package flir.android

import android.content.Context
import android.os.Build
import android.util.Log

/**
 * FlirSDKLoader - SDK availability checker
 * 
 * Since the SDK is now bundled via AAR files, this class simply reports
 * that the SDK is always available. The AAR files include native .so libraries
 * for all supported architectures, which Android handles automatically.
 */
object FlirSDKLoader {
    
    private const val TAG = "FlirSDKLoader"
    
    /**
     * Get the primary ABI for this device
     */
    fun getDeviceArch(): String {
        val supportedAbis = Build.SUPPORTED_ABIS
        Log.d(TAG, "Device supported ABIs: ${supportedAbis.joinToString()}")
        
        val knownArchs = setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        for (abi in supportedAbis) {
            if (abi in knownArchs) {
                Log.d(TAG, "Selected ABI: $abi")
                return abi
            }
        }
        return "arm64-v8a"
    }
    
    /**
     * Check if SDK is available - always true since bundled in AAR
     */
    fun isSDKAvailable(context: Context): Boolean {
        Log.d(TAG, "SDK is bundled in AAR - always available")
        return true
    }
    
    // downloadSDK removed. Runtime downloads are not supported; SDK is bundled at compile-time.
    
    /**
     * Get SDK status
     */
    fun getSDKStatus(context: Context): Map<String, Any> {
        return mapOf(
            "available" to true,
            "bundled" to true,
            "arch" to getDeviceArch(),
            "version" to "4.16.0" // SDK version from AAR
        )
    }
    
    /**
     * Get DEX path - not applicable when bundled via AAR
     * Returns null since SDK is bundled in AAR, no separate DEX needed
     */
    fun getDexPath(context: Context): java.io.File? {
        // SDK is bundled in AAR - no separate DEX file
        return null
    }
    
    /**
     * Get native library directory - not applicable when bundled via AAR
     * Returns null since native libs are included in AAR and handled by Android
     */
    fun getNativeLibDir(context: Context): java.io.File? {
        // SDK native libs are bundled in AAR and extracted automatically by Android
        return null
    }
}
