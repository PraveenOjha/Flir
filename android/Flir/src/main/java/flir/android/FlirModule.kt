package flir.android

import android.util.Log
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.modules.core.DeviceEventManagerModule

class FlirModule(private val reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
    
    companion object {
        private const val TAG = "FlirModule"
    }
    
    override fun getName(): String = "FlirModule"
    
    // Required for RN event emitter support
    private var listenerCount = 0
    
    @ReactMethod
    fun addListener(eventName: String) {
        listenerCount++
        Log.d(TAG, "addListener: $eventName (count: $listenerCount)")
    }
    
    @ReactMethod
    fun removeListeners(count: Int) {
        listenerCount -= count
        if (listenerCount < 0) listenerCount = 0
        Log.d(TAG, "removeListeners: $count (remaining: $listenerCount)")
    }

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
    
    @ReactMethod
    fun isSDKDownloaded(promise: Promise) {
        try {
            val available = FlirSDKLoader.isSDKAvailable(reactContext)
            promise.resolve(available)
        } catch (e: Exception) {
            promise.reject("ERR_FLIR_SDK_CHECK", e)
        }
    }
    
    @ReactMethod
    fun getSDKStatus(promise: Promise) {
        try {
            val available = FlirSDKLoader.isSDKAvailable(reactContext)
            val arch = FlirSDKLoader.getDeviceArch()
            val dexPath = FlirSDKLoader.getDexPath(reactContext)
            val nativeLibDir = FlirSDKLoader.getNativeLibDir(reactContext)
            
            val result = com.facebook.react.bridge.Arguments.createMap()
            result.putBoolean("available", available)
            result.putString("arch", arch)
            result.putString("dexPath", dexPath?.absolutePath ?: "not present (bundled SDK missing)")
            result.putString("nativeLibPath", nativeLibDir?.absolutePath ?: "not present (bundled SDK missing)")
            result.putBoolean("dexExists", dexPath?.exists() == true)
            result.putBoolean("nativeLibsExist", nativeLibDir?.exists() == true)
            
            promise.resolve(result)
        } catch (e: Exception) {
            promise.reject("ERR_FLIR_SDK_STATUS", e)
        }
    }
    
    @ReactMethod
    fun getDiscoveredDevices(promise: Promise) {
        try {
            val devices = FlirManager.getDiscoveredDevices()
            val result = com.facebook.react.bridge.Arguments.createArray()
            
            devices.forEach { identity ->
                val deviceMap = com.facebook.react.bridge.Arguments.createMap()
                deviceMap.putString("id", identity.deviceId)
                deviceMap.putString("name", identity.deviceId)
                deviceMap.putString("communicationType", identity.communicationInterface.name)
                deviceMap.putBoolean("isEmulator", identity.communicationInterface.name == "EMULATOR")
                result.pushMap(deviceMap)
            }
            
            promise.resolve(result)
        } catch (e: Exception) {
            promise.reject("ERR_FLIR_DEVICES", e)
        }
    }

    @ReactMethod
    fun setPreferSdkRotation(prefer: Boolean, promise: Promise) {
        try {
            FlirManager.setPreferSdkRotation(prefer)
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("ERR_FLIR_SET_ROTATION_PREF", e)
        }
    }

    @ReactMethod
    fun isPreferSdkRotation(promise: Promise) {
        try {
            val v = FlirManager.isPreferSdkRotation()
            promise.resolve(v)
        } catch (e: Exception) {
            promise.reject("ERR_FLIR_GET_ROTATION_PREF", e)
        }
    }

    @ReactMethod
    fun getBatteryLevel(promise: Promise) {
        try {
            val level = FlirManager.getBatteryLevel()
            promise.resolve(level)
        } catch (e: Exception) {
            promise.reject("ERR_FLIR_GET_BATTERY", e)
        }
    }

    @ReactMethod
    fun isBatteryCharging(promise: Promise) {
        try {
            val v = FlirManager.isBatteryCharging()
            promise.resolve(v)
        } catch (e: Exception) {
            promise.reject("ERR_FLIR_CHARGING", e)
        }
    }
    
    @ReactMethod
    fun startEmulator(emulatorType: String, promise: Promise) {
        try {
            // Ensure SDK is initialized with context before starting discovery
            FlirManager.init(reactContext)
            // With simplified API, just start discovery - emulators are discovered like any device
            FlirManager.startDiscovery(true)
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("ERR_FLIR_EMULATOR", e)
        }
    }
    
    @ReactMethod
    fun connectToDevice(deviceId: String, promise: Promise) {
        try {
            // Ensure SDK is initialized with context before connecting
            FlirManager.init(reactContext)
            FlirManager.connectToDevice(deviceId)
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("ERR_FLIR_CONNECT", e)
        }
    }
    
    @ReactMethod
    fun startDiscovery(promise: Promise) {
        try {
            // Ensure SDK is initialized with context before starting discovery
            FlirManager.init(reactContext)
            FlirManager.startDiscovery(true)
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("ERR_FLIR_DISCOVERY", e)
        }
    }
    
    @ReactMethod
    fun stopDiscovery(promise: Promise) {
        try {
            FlirManager.stopDiscovery()
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("ERR_FLIR_STOP_DISCOVERY", e)
        }
    }
    
    @ReactMethod
    fun stopFlir(promise: Promise) {
        try {
            FlirManager.stop()
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("ERR_FLIR_STOP", e)
        }
    }
    
    @ReactMethod
    fun initializeSDK(promise: Promise) {
        try {
            FlirManager.init(reactContext)
            
            val result = com.facebook.react.bridge.Arguments.createMap()
            result.putBoolean("initialized", true)
            result.putString("message", "SDK initialized successfully")
            promise.resolve(result)
        } catch (e: Exception) {
            val result = com.facebook.react.bridge.Arguments.createMap()
            result.putBoolean("initialized", false)
            result.putString("error", e.message ?: "Unknown error")
            result.putString("errorType", e.javaClass.simpleName)
            promise.resolve(result)
        }
    }
    
    @ReactMethod
    fun getDebugInfo(promise: Promise) {
        try {
            val result = com.facebook.react.bridge.Arguments.createMap()
            
            // SDK availability
            result.putBoolean("sdkAvailable", FlirSDKLoader.isSDKAvailable(reactContext))
            result.putString("arch", FlirSDKLoader.getDeviceArch())
            
            // Check if FLIR SDK classes are loadable
            val classesLoaded = try {
                Class.forName("com.flir.thermalsdk.androidsdk.ThermalSdkAndroid")
                Class.forName("com.flir.thermalsdk.live.discovery.DiscoveryFactory")
                true
            } catch (e: ClassNotFoundException) {
                false
            }
            result.putBoolean("sdkClassesLoaded", classesLoaded)
            
            // Discovery state
            val devices = FlirManager.getDiscoveredDevices()
            result.putInt("discoveredDeviceCount", devices.size)
            result.putBoolean("isConnected", FlirManager.isConnected())
            result.putBoolean("isStreaming", FlirManager.isStreaming())
            result.putString("connectedDevice", FlirManager.getConnectedDeviceInfo())
            
            promise.resolve(result)
        } catch (e: Exception) {
            promise.reject("ERR_DEBUG_INFO", e)
        }
    }
}
