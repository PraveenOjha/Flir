package flir.android

import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import kotlinx.coroutines.*

class FlirDownloadManager(private val reactContext: ReactApplicationContext) : 
    ReactContextBaseJavaModule(reactContext) {
    
    private var downloadJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    override fun getName() = "FlirDownloadManager"
    
    @ReactMethod
    fun isFlirAvailable(promise: Promise) {
        promise.resolve(FlirSDKLoader.isSDKAvailable(reactContext))
    }
    
    @ReactMethod
    fun getDownloadSize(promise: Promise) {
        promise.resolve(FlirSDKLoader.getDownloadSize(reactContext).toDouble())
    }
    
    @ReactMethod
    fun getDeviceArch(promise: Promise) {
        promise.resolve(FlirSDKLoader.getDeviceArch())
    }
    
    @ReactMethod
    fun downloadFlirSDK(promise: Promise) {
        downloadJob = scope.launch {
            val result = FlirSDKLoader.downloadSDK(reactContext) { downloaded, total ->
                sendEvent("FlirDownloadProgress", Arguments.createMap().apply {
                    putDouble("bytesDownloaded", downloaded.toDouble())
                    putDouble("totalBytes", total.toDouble())
                    putDouble("percent", (downloaded.toDouble() / total) * 100)
                })
            }
            
            result.fold(
                onSuccess = {
                    sendEvent("FlirDownloadComplete", Arguments.createMap())
                    promise.resolve(true)
                },
                onFailure = { error ->
                    sendEvent("FlirDownloadError", Arguments.createMap().apply {
                        putString("error", error.message)
                    })
                    promise.reject("E_DOWNLOAD", error.message, error)
                }
            )
        }
    }
    
    @ReactMethod
    fun cancelDownload() {
        downloadJob?.cancel()
    }
    
    @ReactMethod
    fun deleteSDK(promise: Promise) {
        promise.resolve(FlirSDKLoader.deleteSDK(reactContext))
    }
    
    @ReactMethod
    fun addListener(eventName: String) {}
    
    @ReactMethod
    fun removeListeners(count: Int) {}
    
    private fun sendEvent(name: String, params: WritableMap) {
        reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit(name, params)
    }
}
