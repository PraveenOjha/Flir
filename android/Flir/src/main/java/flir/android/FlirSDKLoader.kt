package flir.android

import android.content.Context
import com.google.android.play.core.splitinstall.*
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream

object FlirSDKLoader {
    
    private const val FEATURE_MODULE = "flir_sdk"
    private var splitInstallManager: SplitInstallManager? = null
    
    fun init(context: Context) {
        splitInstallManager = SplitInstallManagerFactory.create(context)
    }
    
    private fun getSDKDirectory(context: Context) = File(context.filesDir, "FlirSDK")
    
    fun isSDKAvailable(context: Context): Boolean {
        // Check Play Feature module
        splitInstallManager?.installedModules?.let {
            if (FEATURE_MODULE in it) return true
        }
        // Check direct download
        return File(getSDKDirectory(context), "thermalsdk.aar").exists()
    }
    
    fun getDownloadSize(context: Context): Long {
        return loadManifest(context)?.android?.directDownload?.sizeBytes ?: 52_428_800L
    }
    
    fun downloadViaPlayStore(
        onProgress: (Float) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        val manager = splitInstallManager ?: run {
            onError("SplitInstallManager not initialized")
            return
        }
        
        val request = SplitInstallRequest.newBuilder()
            .addModule(FEATURE_MODULE)
            .build()
        
        manager.registerListener { state ->
            when (state.status()) {
                SplitInstallSessionStatus.DOWNLOADING -> {
                    val progress = state.bytesDownloaded().toFloat() / state.totalBytesToDownload()
                    onProgress(progress)
                }
                SplitInstallSessionStatus.INSTALLED -> onComplete()
                SplitInstallSessionStatus.FAILED -> onError("Install failed: ${state.errorCode()}")
                else -> {}
            }
        }
        
        manager.startInstall(request)
    }
    
    suspend fun downloadDirect(
        context: Context,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val manifest = loadManifest(context) ?: return@withContext Result.failure(
                Exception("Failed to load manifest"))
            
            val downloadUrl = manifest.android.directDownload.downloadUrl
            val expectedHash = manifest.android.directDownload.sha256
            val totalSize = manifest.android.directDownload.sizeBytes
            
            val sdkDir = getSDKDirectory(context).apply { mkdirs() }
            val zipFile = File(context.cacheDir, "flir-sdk.zip")
            
            // Download
            URL(downloadUrl).openStream().use { input ->
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
            
            // Verify checksum
            val actualHash = sha256(zipFile)
            if (actualHash != expectedHash) {
                zipFile.delete()
                return@withContext Result.failure(SecurityException("Checksum mismatch"))
            }
            
            // Extract
            unzip(zipFile, sdkDir)
            zipFile.delete()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun deleteSDK(context: Context): Boolean {
        splitInstallManager?.deferredUninstall(listOf(FEATURE_MODULE))
        return getSDKDirectory(context).deleteRecursively()
    }
    
    private fun loadManifest(context: Context): SDKManifest? {
        return try {
            val json = context.assets.open("sdk-manifest.json").bufferedReader().readText()
            SDKManifest.fromJson(json)
        } catch (e: Exception) { null }
    }
    
    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    
    private fun unzip(source: File, destination: File) {
        ZipInputStream(source.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val file = File(destination, entry.name)
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    file.outputStream().use { zip.copyTo(it) }
                }
                entry = zip.nextEntry
            }
        }
    }
}

data class SDKManifest(
    val version: String,
    val android: AndroidManifest
) {
    data class AndroidManifest(
        val playFeatureModule: String,
        val directDownload: DirectDownload
    )
    
    data class DirectDownload(
        val downloadUrl: String,
        val sha256: String,
        val sizeBytes: Long
    )
    
    companion object {
        fun fromJson(json: String): SDKManifest {
            val root = JSONObject(json)
            val android = root.getJSONObject("android")
            val direct = android.getJSONObject("directDownload")
            
            return SDKManifest(
                version = root.getString("version"),
                android = AndroidManifest(
                    playFeatureModule = android.getString("playFeatureModule"),
                    directDownload = DirectDownload(
                        downloadUrl = direct.getString("downloadUrl"),
                        sha256 = direct.getString("sha256"),
                        sizeBytes = direct.getLong("sizeBytes")
                    )
                )
            )
        }
    }
}
