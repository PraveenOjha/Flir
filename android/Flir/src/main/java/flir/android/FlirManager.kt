package flir.android

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.facebook.react.uimanager.ThemedReactContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicLong

object FlirManager {
    private val TAG = "FlirManager"
    private val FLOW_TAG = "FLIR_FLOW"  // Matching FlirSdkManager flow tag
    private var sdkManager: FlirSdkManager? = null
    private val lastEmitMs = AtomicLong(0)
    private val minEmitIntervalMs = 333L // ~3 fps
    private var discoveryStarted = false
    private var reactContext: ThemedReactContext? = null
    private var appContext: Context? = null
    private var frameCount = 0  // For tracking first frame
    
    // Emulator and device state tracking
    private var isEmulatorMode = false
    private var isPhysicalDeviceConnected = false
    private var connectedDeviceName: String? = null
    private var connectedDeviceId: String? = null
    private var isStreaming = false
    
    // Current emulator type preference
    private var preferredEmulatorType = FlirSdkManager.EmulatorType.FLIR_ONE_EDGE
    
    // Discovered devices cache
    private var discoveredDevices: List<FlirSdkManager.DeviceInfo> = emptyList()
    
    // GL texture callback support for native filters
    interface TextureUpdateCallback {
        fun onTextureUpdate(bitmap: Bitmap, textureUnit: Int)
    }
    
    interface TemperatureCallback {
        fun onTemperatureData(temperature: Double, x: Int, y: Int)
    }
    
    private var textureCallback: TextureUpdateCallback? = null
    private var temperatureCallback: TemperatureCallback? = null
    private var latestBitmap: Bitmap? = null
    
    fun setTextureCallback(callback: TextureUpdateCallback?) {
        textureCallback = callback
    }
    
    fun setTemperatureCallback(callback: TemperatureCallback?) {
        temperatureCallback = callback
    }
    
    fun getLatestBitmap(): Bitmap? = latestBitmap
    
    fun getTemperatureAtPoint(x: Int, y: Int): Double? {
        return try {
            sdkManager?.getTemperatureAtPoint(x, y)?.takeIf { !it.isNaN() }
        } catch (t: Throwable) {
            null
        }
    }
    
    /**
     * Check if currently running in emulator mode (no physical FLIR device)
     */
    fun isEmulator(): Boolean = isEmulatorMode
    
    /**
     * Check if a physical FLIR device is connected
     */
    fun isDeviceConnected(): Boolean = isPhysicalDeviceConnected || isEmulatorMode
    
    /**
     * Check if currently streaming
     */
    fun isStreaming(): Boolean = isStreaming
    
    /**
     * Get list of discovered devices
     */
    fun getDiscoveredDevices(): List<FlirSdkManager.DeviceInfo> = discoveredDevices
    
    /**
     * Get information about the connected device
     */
    fun getConnectedDeviceInfo(): String {
        return when {
            connectedDeviceName == null -> "Not connected"
            isEmulatorMode -> "Emulator ($connectedDeviceName)"
            else -> "Physical device ($connectedDeviceName)"
        }
    }
    
    /**
     * Set preferred emulator type (FLIR_ONE_EDGE or FLIR_ONE)
     */
    fun setPreferredEmulatorType(type: String) {
        preferredEmulatorType = when (type.uppercase()) {
            "FLIR_ONE" -> FlirSdkManager.EmulatorType.FLIR_ONE
            else -> FlirSdkManager.EmulatorType.FLIR_ONE_EDGE
        }
        Log.d(TAG, "Preferred emulator type set to: $preferredEmulatorType")
        sdkManager?.setEmulatorType(preferredEmulatorType)
    }

    fun init(context: Context) {
        // Avoid re-initialization if already done
        if (sdkManager != null) {
            Log.i(FLOW_TAG, "[FlirManager] init() called but already initialized, skipping")
            return
        }
        
        appContext = context.applicationContext
        Log.i(FLOW_TAG, "[FlirManager] init() called - initializing SDK Manager")
        try {
            // Initialize SDK manager with listener matching FlirSdkManager.Listener interface
            sdkManager = FlirSdkManager(object : FlirSdkManager.Listener {
                override fun onFrame(bitmap: Bitmap) {
                    // Validate bitmap before processing to prevent GL crashes
                    if (bitmap.isRecycled) {
                        Log.w(TAG, "[FlirManager] onFrame: Received recycled bitmap, skipping")
                        return
                    }
                    if (bitmap.width <= 0 || bitmap.height <= 0) {
                        Log.w(TAG, "[FlirManager] onFrame: Invalid bitmap dimensions ${bitmap.width}x${bitmap.height}, skipping")
                        return
                    }
                    if (bitmap.config == null) {
                        Log.w(TAG, "[FlirManager] onFrame: Bitmap has null config, skipping")
                        return
                    }
                    
                    latestBitmap = bitmap
                    if (frameCount == 0) {
                        Log.i(FLOW_TAG, "[FlirManager] FIRST FRAME received: ${bitmap.width}x${bitmap.height}")
                    }
                    frameCount++
                    textureCallback?.onTextureUpdate(bitmap, 0)
                    emitFrameToReactNative(bitmap)
                }

                override fun onTemperature(temp: Double, x: Int, y: Int) {
                    temperatureCallback?.onTemperatureData(temp, x, y)
                }

                override fun onDeviceFound(deviceId: String, deviceName: String, isEmulator: Boolean) {
                    Log.i(FLOW_TAG, "[FlirManager] Device found: $deviceName (id=$deviceId, emulator=$isEmulator)")
                    Log.d(TAG, "Device found: $deviceName (id=$deviceId, emulator=$isEmulator)")
                    discoveryCallback?.onDeviceFound(deviceName)
                }
                
                override fun onDeviceListUpdated(devices: MutableList<FlirSdkManager.DeviceInfo>) {
                    discoveredDevices = devices.toList()
                    Log.i(FLOW_TAG, "[FlirManager] Device list updated: ${devices.size} devices")
                    Log.i(TAG, "Device list updated: ${devices.size} devices")
                    devices.forEach { 
                        Log.d(TAG, "  - ${it.deviceName} (${it.commInterface}, emu=${it.isEmulator})")
                    }
                    emitDevicesFound(devices)
                    
                    // Auto-connect to first device if available
                    if (devices.isNotEmpty()) {
                        val first = devices[0]
                        connectedDeviceName = first.deviceName
                        connectedDeviceId = first.deviceId
                        isEmulatorMode = first.isEmulator
                        isPhysicalDeviceConnected = !first.isEmulator
                        
                        Log.i(FLOW_TAG, "[FlirManager] Auto-connecting to first device: ${first.deviceName}")
                        // Connect to the device
                        sdkManager?.connectToDevice(first.deviceId)
                    }
                }
                
                override fun onDeviceConnected(deviceId: String, deviceName: String, isEmulator: Boolean) {
                    connectedDeviceId = deviceId
                    connectedDeviceName = deviceName
                    this@FlirManager.isEmulatorMode = isEmulator
                    isPhysicalDeviceConnected = !isEmulator
                    
                    Log.i(TAG, "Device connected: $deviceName (id=$deviceId, emulator=$isEmulator)")
                    emitDeviceState("connected", !isEmulator)
                }
                
                override fun onDeviceDisconnected() {
                    Log.i(TAG, "Device disconnected")
                    isPhysicalDeviceConnected = false
                    isEmulatorMode = false
                    isStreaming = false
                    connectedDeviceId = null
                    connectedDeviceName = null
                    emitDeviceState("disconnected", false)
                }
                
                override fun onDiscoveryStarted() {
                    Log.i(TAG, "Discovery started")
                    emitDeviceState("discovering", false)
                }
                
                override fun onDiscoveryTimeout() {
                    Log.w(TAG, "Discovery timeout - no devices found")
                    discoveryCallback?.onDiscoveryTimeout()
                    emitDeviceState("discovery_timeout", false)
                }
                
                override fun onStreamStarted(streamType: String) {
                    isStreaming = true
                    Log.i(TAG, "Streaming started: $streamType")
                    emitDeviceState("streaming", isPhysicalDeviceConnected)
                }
                
                override fun onError(error: String) {
                    Log.e(TAG, "SDK Error: $error")
                    emitError(error)
                }
            }, context)
            
            // Set emulator type preference
            sdkManager?.setEmulatorType(preferredEmulatorType)
            
            Log.i(TAG, "FlirManager initialized with comprehensive SDK manager")
        } catch (t: Throwable) {
            Log.e(TAG, "FlirManager init failed", t)
        }
    }

    /**
     * Start discovery with the specified parameters
     * @param isEmuMode If true, immediately start emulator without physical device discovery
     */
    fun startDiscoveryAndConnect(context: ThemedReactContext, isEmuMode: Boolean = false) {
        Log.i(FLOW_TAG, "[FlirManager] startDiscoveryAndConnect called: isEmuMode=$isEmuMode, discoveryStarted=$discoveryStarted, preferredEmulatorType=$preferredEmulatorType")
        
        if (discoveryStarted && !isEmuMode) {
            Log.i(FLOW_TAG, "[FlirManager] Discovery already started, skipping")
            return
        }
        discoveryStarted = true
        reactContext = context
        frameCount = 0  // Reset frame counter

        emitDeviceState("discovering", false)

        try {
            // Set emulator type preference before discovery
            Log.i(FLOW_TAG, "[FlirManager] Setting emulator type: $preferredEmulatorType")
            sdkManager?.setEmulatorType(preferredEmulatorType)
            
            // Start discovery (forceEmulator=isEmuMode)
            Log.i(FLOW_TAG, "[FlirManager] Calling sdkManager.startDiscovery(forceEmulator=$isEmuMode)")
            Log.i(TAG, "Starting discovery (forceEmulator=$isEmuMode)")
            sdkManager?.startDiscovery(isEmuMode)
        } catch (t: Throwable) {
            Log.e(FLOW_TAG, "[FlirManager] startDiscoveryAndConnect failed: ${t.message}")
            Log.e(TAG, "startDiscoveryAndConnect failed", t)
            emitDeviceState("error", false)
        }
    }
    
    /**
     * Legacy overload for backward compatibility
     */
    fun startDiscoveryAndConnect(context: ThemedReactContext) {
        startDiscoveryAndConnect(context, isEmuMode = false)
    }

    /**
     * Switch to a specific device by ID
     */
    fun switchToDevice(deviceId: String) {
        if (deviceId == connectedDeviceId) {
            Log.d(TAG, "Already connected to device: $deviceId")
            return
        }
        
        Log.i(TAG, "Switching to device: $deviceId")
        sdkManager?.connectToDevice(deviceId)
    }
    
    /**
     * Start emulator mode
     */
    fun startEmulator(type: FlirSdkManager.EmulatorType = preferredEmulatorType) {
        Log.i(TAG, "Starting emulator: $type")
        sdkManager?.setEmulatorType(type)
        sdkManager?.startDiscovery(true) // forceEmulator = true
    }

    fun stopDiscovery() {
        Log.i(TAG, "Stopping discovery")
        discoveryStarted = false
        sdkManager?.stopDiscovery()
    }

    fun stop() {
        Log.i(TAG, "Stopping FlirManager")
        
        // Stop the SDK manager
        sdkManager?.stop()
        
        // Reset state
        discoveryStarted = false
        isPhysicalDeviceConnected = false
        isEmulatorMode = false
        isStreaming = false
        connectedDeviceName = null
        connectedDeviceId = null
        latestBitmap = null
        discoveredDevices = emptyList()
    }

    fun getLatestFramePath(): String? {
        val bitmap = latestBitmap ?: return null
        return try {
            val file = File.createTempFile("flir_frame_", ".jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            file.absolutePath
        } catch (t: Throwable) {
            null
        }
    }

    fun getTemperatureAt(x: Int, y: Int): Double? {
        return getTemperatureAtPoint(x, y)
    }

    private fun emitFrameToReactNative(bitmap: Bitmap) {
        val now = System.currentTimeMillis()
        if (now - lastEmitMs.get() < minEmitIntervalMs) return
        lastEmitMs.set(now)

        val ctx = reactContext ?: return
        try {
            val params = Arguments.createMap()
            params.putInt("width", bitmap.width)
            params.putInt("height", bitmap.height)
            params.putDouble("timestamp", now.toDouble())

            ctx.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit("FlirFrameReceived", params)
        } catch (e: Exception) {
            // Silently ignore
        }
    }

    private fun emitDeviceState(state: String, isPhysical: Boolean) {
        val ctx = reactContext ?: return
        try {
            val params = Arguments.createMap()
            params.putString("state", state)
            params.putBoolean("isPhysical", isPhysical)
            params.putBoolean("isEmulator", isEmulatorMode)
            params.putBoolean("isConnected", isPhysicalDeviceConnected || isEmulatorMode)
            params.putBoolean("isStreaming", isStreaming)
            
            connectedDeviceName?.let {
                params.putString("deviceName", it)
            }
            connectedDeviceId?.let {
                params.putString("deviceId", it)
            }

            ctx.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit("FlirDeviceConnected", params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to emit device state", e)
        }
    }
    
    private fun emitDevicesFound(devices: List<FlirSdkManager.DeviceInfo>) {
        val ctx = reactContext ?: return
        try {
            val params = Arguments.createMap()
            val devicesArray: WritableArray = Arguments.createArray()
            
            devices.forEach { device ->
                val deviceMap: WritableMap = Arguments.createMap()
                deviceMap.putString("id", device.deviceId)
                deviceMap.putString("name", device.deviceName)
                deviceMap.putString("communicationType", device.commInterface.name)
                deviceMap.putBoolean("isEmulator", device.isEmulator)
                devicesArray.pushMap(deviceMap)
            }
            
            params.putArray("devices", devicesArray)
            params.putInt("count", devices.size)

            ctx.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit("FlirDevicesFound", params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to emit devices found", e)
        }
    }
    
    private fun emitError(message: String) {
        val ctx = reactContext ?: return
        try {
            val params = Arguments.createMap()
            params.putString("error", message)

            ctx.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit("FlirError", params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to emit error", e)
        }
    }

    // Compatibility for Java / FlirHelper
    @JvmStatic
    fun getInstance(): FlirManager = this

    interface DiscoveryCallback {
        fun onDeviceFound(deviceName: String)
        fun onDiscoveryTimeout()
        fun onEmulatorEnabled()
    }

    private var discoveryCallback: DiscoveryCallback? = null

    fun setDiscoveryCallback(callback: DiscoveryCallback?) {
        discoveryCallback = callback
    }

    fun setPalette(name: String) {
        sdkManager?.setPalette(name)
    }

    fun setEmulatorMode(enabled: Boolean) {
        Log.i(FLOW_TAG, "[FlirManager] setEmulatorMode($enabled) called")
        if (enabled) {
            isEmulatorMode = true
            // Reset discovery state to allow fresh discovery
            discoveryStarted = false
            discoveryCallback?.onEmulatorEnabled()
            startEmulator(preferredEmulatorType)
        } else {
            // Disable emulator - just stop, don't start new discovery
            Log.i(FLOW_TAG, "[FlirManager] Disabling emulator mode, stopping...")
            stop()
        }
    }

    fun updateAcol(value: Float) {
        // No-op for now - palette changes handled by setPalette
    }

    fun startDiscovery(retry: Boolean) {
        Log.i(FLOW_TAG, "[FlirManager] startDiscovery($retry) called, sdkManager=${if (sdkManager != null) "present" else "NULL"}, appContext=${if (appContext != null) "present" else "NULL"}")
        
        // Auto-init if not initialized
        if (sdkManager == null && appContext != null) {
            Log.i(FLOW_TAG, "[FlirManager] Auto-initializing from startDiscovery()")
            init(appContext!!)
        }
        
        val ctx = reactContext
        if (ctx != null) {
            // Reset discovery state if retrying
            if (retry) {
                discoveryStarted = false
            }
            startDiscoveryAndConnect(ctx, isEmuMode = false)
        } else if (appContext != null) {
            // Fallback: try to start discovery without React context
            try {
                Log.w(TAG, "Starting discovery without React context")
                Log.i(FLOW_TAG, "[FlirManager] Calling sdkManager.startDiscovery(false) without React context")
                sdkManager?.startDiscovery(false)
            } catch (t: Throwable) {
                Log.e(TAG, "startDiscovery failed", t)
                Log.e(FLOW_TAG, "[FlirManager] startDiscovery failed: ${t.message}")
            }
        } else {
            Log.e(FLOW_TAG, "[FlirManager] Cannot startDiscovery - no context available! Call init(context) first.")
        }
    }
    
    fun enableEmulatorMode() {
        setEmulatorMode(true)
    }
    
    /**
     * Force start with emulator (useful for testing)
     */
    fun forceEmulatorMode(type: String = "FLIR_ONE_EDGE") {
        setPreferredEmulatorType(type)
        val ctx = reactContext
        if (ctx != null) {
            discoveryStarted = false
            startDiscoveryAndConnect(ctx, isEmuMode = true)
        } else {
            startEmulator(preferredEmulatorType)
        }
    }
}
