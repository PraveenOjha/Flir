package flir.android

import com.facebook.react.bridge.*

/**
 * FlirDownloadManager - React Native module for SDK status
 * 
 * Since the SDK is now bundled in the AAR, this module just reports
 * that the SDK is always available.
 */
class FlirDownloadManager(private val reactContext: ReactApplicationContext) : 
    ReactContextBaseJavaModule(reactContext) {
    
    override fun getName() = "FlirDownloadManager"
    
    @ReactMethod
    fun isFlirAvailable(promise: Promise) {
        // SDK is bundled - always available
        promise.resolve(true)
    }
    
    @ReactMethod
    fun getDownloadSize(promise: Promise) {
        // No download needed
        promise.resolve(0.0)
    }
    
    @ReactMethod
    fun getDeviceArch(promise: Promise) {
        promise.resolve(FlirSDKLoader.getDeviceArch())
    }
    
    @ReactMethod
    fun downloadFlirSDK(promise: Promise) {
        // SDK is bundled - no download needed
        promise.resolve(true)
    }
    
    @ReactMethod
    fun getSDKStatus(promise: Promise) {
        val status = Arguments.createMap().apply {
            putBoolean("available", true)
            putBoolean("bundled", true)
            putString("arch", FlirSDKLoader.getDeviceArch())
            putString("version", "4.16.0")
        }
        promise.resolve(status)
    }
    
    @ReactMethod
    fun cancelDownload(promise: Promise) {
        // Nothing to cancel
        promise.resolve(true)
    }
}
