package flir.android

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * FlirSDKLoader - Downloads and manages architecture-specific FLIR SDK packages
 * 
 * Each package contains:
 * - classes.dex (combined DEX from thermalsdk + androidsdk)
 * - Native .so libraries in jni/{arch}/ folder
 * 
 * URLs are read from sdk-manifest.json in assets folder
 */
object FlirSDKLoader {
    
    private const val TAG = "FlirSDKLoader"
    
    // Cached manifest data
    private var cachedManifest: SDKManifest? = null
    
    // Manifest data classes
    data class ArchPackage(val downloadUrl: String, val sizeBytes: Long)
    data class SDKManifest(val version: String, val packages: Map<String, ArchPackage>)
    
    private fun getSDKDirectory(context: Context) = File(context.filesDir, "FlirSDK")
    
    /**
     * Load manifest from assets
     */
    private fun loadManifest(context: Context): SDKManifest? {
        if (cachedManifest != null) return cachedManifest
        
        return try {
            val json = context.assets.open("sdk-manifest.json").bufferedReader().readText()
            val root = JSONObject(json)
            val android = root.getJSONObject("android")
            val packagesJson = android.getJSONObject("packages")
            
            val packages = mutableMapOf<String, ArchPackage>()
            packagesJson.keys().forEach { arch ->
                val pkg = packagesJson.getJSONObject(arch)
                packages[arch] = ArchPackage(
                    downloadUrl = pkg.getString("downloadUrl"),
                    sizeBytes = pkg.getLong("sizeBytes")
                )
            }
            
            SDKManifest(
                version = root.getString("version"),
                packages = packages
            ).also { cachedManifest = it }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load manifest: ${e.message}")
            null
        }
    }
    
    /**
     * Get the primary ABI for this device
     */
    fun getDeviceArch(): String {
        val supportedAbis = Build.SUPPORTED_ABIS
        Log.d(TAG, "Device supported ABIs: ${supportedAbis.joinToString()}")
        
        val knownArchs = setOf("arm64-v8a", "armeabi-v7a", "x86_64")
        for (abi in supportedAbis) {
            if (abi in knownArchs) {
                Log.d(TAG, "Selected ABI: $abi")
                return abi
            }
        }
        return "arm64-v8a"
    }
    
    /**
     * Check if SDK is already downloaded
     */
    fun isSDKAvailable(context: Context): Boolean {
        val sdkDir = getSDKDirectory(context)
        val arch = getDeviceArch()
        
        val dexFile = File(sdkDir, "classes.dex")
        val soDir = File(sdkDir, "jni/$arch")
        
        val hasDex = dexFile.exists() && dexFile.length() > 0
        val hasSo = soDir.exists() && soDir.listFiles()?.isNotEmpty() == true
        
        Log.d(TAG, "SDK available: dex=$hasDex, so=$hasSo")
        return hasDex && hasSo
    }
    
    /**
     * Get path to the DEX file
     */
    fun getDexPath(context: Context): File? {
        val dexFile = File(getSDKDirectory(context), "classes.dex")
        return if (dexFile.exists()) dexFile else null
    }
    
    /**
     * Get path to native libraries directory
     */
    fun getNativeLibDir(context: Context): File? {
        val arch = getDeviceArch()
        val libDir = File(getSDKDirectory(context), "jni/$arch")
        return if (libDir.exists()) libDir else null
    }
    
    /**
     * Get estimated download size from manifest
     */
    fun getDownloadSize(context: Context): Long {
        val manifest = loadManifest(context) ?: return 15_000_000L
        val arch = getDeviceArch()
        return manifest.packages[arch]?.sizeBytes ?: 15_000_000L
    }
    
    /**
     * Download the SDK package for this device's architecture
     */
    suspend fun downloadSDK(
        context: Context,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val arch = getDeviceArch()
            val manifest = loadManifest(context) 
                ?: return@withContext Result.failure(Exception("Failed to load manifest"))
            
            val archPackage = manifest.packages[arch]
                ?: return@withContext Result.failure(Exception("No package for architecture: $arch"))
            
            val downloadUrl = archPackage.downloadUrl
            Log.i(TAG, "Downloading SDK for $arch from $downloadUrl")
            
            val sdkDir = getSDKDirectory(context).apply { mkdirs() }
            val zipFile = File(context.cacheDir, "flir-sdk-$arch.zip")
            
            // Download with redirect handling (GitHub uses 302 redirects)
            var connection = URL(downloadUrl).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 30000
            connection.readTimeout = 60000
            
            // Handle redirects manually for cross-protocol redirects
            var redirectCount = 0
            while (connection.responseCode in 301..302 && redirectCount < 5) {
                val redirectUrl = connection.getHeaderField("Location")
                Log.d(TAG, "Redirect to: $redirectUrl")
                connection.disconnect()
                connection = URL(redirectUrl).openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = true
                redirectCount++
            }
            
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(Exception("HTTP error ${connection.responseCode}"))
            }
            
            val totalSize = connection.contentLengthLong.let { 
                if (it > 0) it else archPackage.sizeBytes
            }
            
            connection.inputStream.use { input ->
                FileOutputStream(zipFile).use { output ->
                    val buffer = ByteArray(8192)
                    var totalRead = 0L
                    var bytesRead: Int
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        withContext(Dispatchers.Main) {
                            onProgress(totalRead, totalSize)
                        }
                    }
                }
            }
            connection.disconnect()
            
            Log.i(TAG, "Download complete: ${zipFile.length()} bytes")
            
            // Extract
            unzip(zipFile, sdkDir)
            zipFile.delete()
            
            // Verify
            val dexFile = File(sdkDir, "classes.dex")
            val soDir = File(sdkDir, "jni/$arch")
            
            if (!dexFile.exists()) {
                return@withContext Result.failure(Exception("DEX file not extracted"))
            }
            
            dexFile.setReadOnly()
            Log.i(TAG, "SDK installed: dex=${dexFile.length()} bytes, libs=${soDir.listFiles()?.size ?: 0} files")
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            Result.failure(e)
        }
    }
    
    /**
     * Delete downloaded SDK
     */
    fun deleteSDK(context: Context): Boolean {
        return getSDKDirectory(context).deleteRecursively()
    }
    
    private fun unzip(source: File, destination: File) {
        Log.d(TAG, "Extracting ${source.name} to ${destination.absolutePath}")
        
        ZipInputStream(source.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val file = File(destination, entry.name)
                Log.d(TAG, "  Extracting: ${entry.name}")
                
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    file.outputStream().use { output ->
                        zip.copyTo(output)
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }
}
