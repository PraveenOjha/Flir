package flir.android

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod

class FlirModule(private val reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
    override fun getName(): String = "FlirModule"

    // Simple placeholder conversion: converts an ARGB color to a pseudo-temperature value.
    // Replace with SDK call when integrating thermalsdk APIs.
    @ReactMethod
    fun getTemperatureFromColor(color: Int, promise: Promise) {
        try {
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            // Luminance-like value scaled to a plausible temperature range (0°C - 400°C)
            val lum = 0.2126 * r + 0.7152 * g + 0.0722 * b
            val temp = 0.0 + (lum / 255.0) * 400.0
            promise.resolve(temp)
        } catch (e: Exception) {
            promise.reject("ERR_FLIR_CONVERT", e)
        }
    }

    @ReactMethod
    fun getLatestFramePath(promise: Promise) {
        try {
            val path = FlirFrameCache.latestFramePath
            if (path != null) promise.resolve(path) else promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("ERR_FLIR_PATH", e)
        }
    }

    @ReactMethod
    fun getTemperatureAt(x: Int, y: Int, promise: Promise) {
        try {
            val temp = FlirManager.getTemperatureAt(x, y)
            if (temp != null) promise.resolve(temp) else promise.reject("ERR_NO_DATA", "No temperature data available")
        } catch (e: Exception) {
            promise.reject("ERR_FLIR_SAMPLE", e)
        }
    }
    
    @ReactMethod
    fun isEmulator(promise: Promise) {
        try {
            promise.resolve(FlirManager.isEmulator())
        } catch (e: Exception) {
            promise.reject("ERR_FLIR_EMULATOR_CHECK", e)
        }
    }
    
    @ReactMethod
    fun isDeviceConnected(promise: Promise) {
        try {
            promise.resolve(FlirManager.isDeviceConnected())
        } catch (e: Exception) {
            promise.reject("ERR_FLIR_DEVICE_CHECK", e)
        }
    }
    
    @ReactMethod
    fun getConnectedDeviceInfo(promise: Promise) {
        try {
            promise.resolve(FlirManager.getConnectedDeviceInfo())
        } catch (e: Exception) {
            promise.reject("ERR_FLIR_DEVICE_INFO", e)
        }
    }
}
