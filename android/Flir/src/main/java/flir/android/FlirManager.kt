package flir.android

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContext
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.facebook.react.uimanager.ThemedReactContext
import com.flir.thermalsdk.live.Identity
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicLong

/**
 * Simplified FlirManager - bridge between React Native and FlirSdkManager
 * No filtering - returns ALL discovered devices (USB, Network, Emulator)
 * Let React Native handle any filtering logic
 */
object FlirManager {
    private const val TAG = "FlirManager"
    
    private var sdkManager: FlirSdkManager? = null
    private var reactContext: ReactContext? = null
    private var appContext: Context? = null
    
    // Frame rate limiting
    private val lastEmitMs = AtomicLong(0)
    private val minEmitIntervalMs = 100L // ~10 fps max for RN events
    
    // State
    private var isInitialized = false
    private var isScanning = false
    private var isConnected = false
    private var isStreaming = false
    private var connectedDeviceId: String? = null
    private var connectedDeviceName: String? = null
    
    // Latest bitmap for texture updates
    private var latestBitmap: Bitmap? = null
    
    // Callbacks
    interface TextureUpdateCallback {
        fun onTextureUpdate(bitmap: Bitmap, textureUnit: Int)
    }
    
    interface TemperatureCallback {
        fun onTemperatureData(temperature: Double, x: Int, y: Int)
    }
    
    private var textureCallback: TextureUpdateCallback? = null
    private var temperatureCallback: TemperatureCallback? = null
    
    fun setTextureCallback(callback: TextureUpdateCallback?) {
        textureCallback = callback
    }
    
    fun setTemperatureCallback(callback: TemperatureCallback?) {
        temperatureCallback = callback
    }
    
    fun getLatestBitmap(): Bitmap? = latestBitmap

    // Preference: ask SDK to deliver oriented/rotated frames (if SDK supports it)
    fun setPreferSdkRotation(prefer: Boolean) {
        sdkManager?.setPreferSdkRotation(prefer)
    }

    fun isPreferSdkRotation(): Boolean {
        return sdkManager?.isPreferSdkRotation() ?: false
    }

    fun getBatteryLevel(): Int {
        return sdkManager?.getBatteryLevel() ?: -1
    }

    fun isBatteryCharging(): Boolean {
        return sdkManager?.isBatteryCharging() ?: false
    }
    
    /**
     * Initialize the FLIR SDK
     */
    fun init(context: Context) {
        // Store react context for event emission if it's a React context
        // Always update if we get a valid ReactContext (in case previous was stale)
        if (context is ReactContext) {
            Log.d(TAG, "Storing ReactContext for event emission: ${context.javaClass.simpleName}")
            reactContext = context
        } else {
            Log.d(TAG, "Context is not ReactContext: ${context.javaClass.simpleName}")
        }
        
        if (isInitialized) {
            Log.d(TAG, "Already initialized")
            return
        }
        
        appContext = context.applicationContext
        
        sdkManager = FlirSdkManager.getInstance(context)
        sdkManager?.setListener(sdkListener)
        sdkManager?.initialize()
        
        isInitialized = true
        Log.i(TAG, "FlirManager initialized")
    }
    
    /**
     * Start scanning for devices (USB, Network, Emulator - ALL types)
     */
    fun startDiscovery(retry: Boolean = false) {
        Log.i(TAG, "startDiscovery(retry=$retry)")
        
        if (!isInitialized && appContext != null) {
            init(appContext!!)
        }
        
        if (isScanning && !retry) {
            Log.d(TAG, "Already scanning")
            return
        }
        
        isScanning = true
        emitDeviceState("discovering")
        sdkManager?.scan()
    }
    
    /**
     * Start discovery with React context
     */
    fun startDiscoveryAndConnect(context: ThemedReactContext, isEmuMode: Boolean = false) {
        reactContext = context
        startDiscovery(retry = false)
    }
    
    /**
     * Stop scanning
     */
    fun stopDiscovery() {
        Log.i(TAG, "stopDiscovery")
        sdkManager?.stop()
        isScanning = false
    }
    
    /**
     * Connect to a device by ID
     */
    fun connectToDevice(deviceId: String) {
        Log.i(TAG, "connectToDevice: $deviceId")
        
        val devices = sdkManager?.discoveredDevices ?: emptyList()
        val identity = devices.find { it.deviceId == deviceId }
        
        if (identity != null) {
            sdkManager?.connect(identity)
        } else {
            Log.e(TAG, "Device not found: $deviceId")
            emitError("Device not found: $deviceId")
        }
    }
    
    /**
     * Switch to a different device
     */
    fun switchToDevice(deviceId: String) {
        if (deviceId == connectedDeviceId) {
            Log.d(TAG, "Already connected to: $deviceId")
            return
        }
        
        // Disconnect current and connect new
        if (isConnected) {
            sdkManager?.disconnect()
        }
        connectToDevice(deviceId)
    }
    
    /**
     * Start streaming from connected device
     */
    fun startStream() {
        Log.i(TAG, "startStream")
        sdkManager?.startStream()
    }
    
    /**
     * Stop streaming
     */
    fun stopStream() {
        Log.i(TAG, "stopStream")
        sdkManager?.stopStream()
        isStreaming = false
    }
    
    /**
     * Disconnect from current device
     */
    fun disconnect() {
        Log.i(TAG, "disconnect")
        sdkManager?.disconnect()
        isConnected = false
        isStreaming = false
        connectedDeviceId = null
        connectedDeviceName = null
    }
    
    /**
     * Stop everything
     */
    fun stop() {
        Log.i(TAG, "stop")
        stopStream()
        disconnect()
        stopDiscovery()
        latestBitmap = null
    }
    
    /**
     * Get temperature at point in image coordinates
     */
    fun getTemperatureAt(x: Int, y: Int): Double? {
        return sdkManager?.getTemperatureAt(x, y)?.takeIf { !it.isNaN() }
    }
    
    /**
     * Get temperature at normalized coordinates (0.0 to 1.0)
     */
    fun getTemperatureAtNormalized(normalizedX: Double, normalizedY: Double): Double? {
        return sdkManager?.getTemperatureAtNormalized(normalizedX, normalizedY)?.takeIf { !it.isNaN() }
    }
    
    /**
     * Alias for getTemperatureAt
     */
    fun getTemperatureAtPoint(x: Int, y: Int): Double? = getTemperatureAt(x, y)
    
    /**
     * Set palette
     */
    fun setPalette(name: String) {
        Log.d(TAG, "setPalette: $name")
        sdkManager?.setPalette(name)
    }
    
    /**
     * Get available palettes
     */
    fun getAvailablePalettes(): List<String> {
        return sdkManager?.availablePalettes ?: emptyList()
    }
    
    /**
     * Get list of discovered devices
     */
    fun getDiscoveredDevices(): List<Identity> {
        return sdkManager?.discoveredDevices ?: emptyList()
    }
    
    /**
     * Check states
     */
    fun isConnected(): Boolean = isConnected
    fun isStreaming(): Boolean = isStreaming
    fun isEmulator(): Boolean = connectedDeviceName?.contains("EMULAT", ignoreCase = true) == true
    fun isDeviceConnected(): Boolean = isConnected
    
    /**
     * Get connected device info
     */
    fun getConnectedDeviceInfo(): String {
        return connectedDeviceName ?: "Not connected"
    }
    
    /**
     * Get latest frame as file path (for RN)
     */
    fun getLatestFramePath(): String? {
        val bitmap = latestBitmap ?: return null
        return try {
            val file = File.createTempFile("flir_frame_", ".jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            file.absolutePath
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to save frame", t)
            null
        }
    }
    
    // SDK Listener
    private val sdkListener = object : FlirSdkManager.Listener {
        override fun onDeviceFound(identity: Identity) {
            Log.i(TAG, "Device found: ${identity.deviceId}")
        }
        
        override fun onDeviceListUpdated(devices: List<Identity>) {
            Log.i(TAG, "Devices updated: ${devices.size} found")
            devices.forEach { 
                Log.d(TAG, "  - ${it.deviceId} (${it.communicationInterface})")
            }
            emitDevicesFound(devices)
        }
        
        override fun onConnected(identity: Identity?) {
            Log.i(TAG, "Connected to: ${identity?.deviceId}")
            isConnected = true
            connectedDeviceId = identity?.deviceId
            connectedDeviceName = identity?.deviceId
            emitDeviceState("connected")
            
            // Auto-start streaming when connected
            startStream()
        }
        
        override fun onDisconnected() {
            Log.i(TAG, "Disconnected")
            isConnected = false
            isStreaming = false
            connectedDeviceId = null
            connectedDeviceName = null
            emitDeviceState("disconnected")
        }
        
        override fun onFrame(bitmap: Bitmap) {
            if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
                return
            }
            
            latestBitmap = bitmap
            isStreaming = true
            
            // Notify texture callback (for GL rendering)
            if (textureCallback != null) {
                textureCallback?.onTextureUpdate(bitmap, 0)
            } else {
                // Log only occasionally to avoid spam
                if (System.currentTimeMillis() % 5000 < 100) {
                    Log.w(TAG, "⚠️ Frame received but textureCallback is null - texture won't update!")
                }
            }
            
            // Rate-limited RN event
            emitFrameToReactNative(bitmap)
        }
        
        override fun onError(message: String) {
            Log.e(TAG, "Error: $message")
            emitError(message)
        }

        override fun onBatteryUpdated(level: Int, isCharging: Boolean) {
            Log.d(TAG, "onBatteryUpdated: level=$level charging=$isCharging")
            emitBatteryState(level, isCharging)
        }
    }
    
    // React Native event emitters
    private fun emitFrameToReactNative(bitmap: Bitmap) {
        val now = System.currentTimeMillis()
        if (now - lastEmitMs.get() < minEmitIntervalMs) return
        lastEmitMs.set(now)
        
        val ctx = reactContext ?: return
        try {
            val params = Arguments.createMap().apply {
                putInt("width", bitmap.width)
                putInt("height", bitmap.height)
                putDouble("timestamp", now.toDouble())
            }
            ctx.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit("FlirFrameReceived", params)
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    private fun emitDeviceState(state: String) {
        val ctx = reactContext
        if (ctx == null) {
            Log.e(TAG, "Cannot emit FlirDeviceConnected($state) - reactContext is null!")
            return
        }
        Log.d(TAG, "Emitting FlirDeviceConnected: $state")
        try {
            val params = Arguments.createMap().apply {
                putString("state", state)
                putBoolean("isConnected", isConnected)
                putBoolean("isStreaming", isStreaming)
                putBoolean("isEmulator", isEmulator())
                connectedDeviceName?.let { putString("deviceName", it) }
                connectedDeviceId?.let { putString("deviceId", it) }
            }
            ctx.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit("FlirDeviceConnected", params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to emit device state", e)
        }
    }
    
    private fun emitDevicesFound(devices: List<Identity>) {
        val ctx = reactContext
        if (ctx == null) {
            Log.e(TAG, "Cannot emit FlirDevicesFound - reactContext is null!")
            return
        }
        Log.d(TAG, "Emitting FlirDevicesFound with ${devices.size} devices")
        try {
            val params = Arguments.createMap()
            val devicesArray: WritableArray = Arguments.createArray()
            
            devices.forEach { identity ->
                val deviceMap: WritableMap = Arguments.createMap().apply {
                    putString("id", identity.deviceId)
                    putString("name", identity.deviceId)
                    putString("communicationType", identity.communicationInterface.name)
                    putBoolean("isEmulator", identity.communicationInterface.name == "EMULATOR")
                }
                devicesArray.pushMap(deviceMap)
            }
            
            params.putArray("devices", devicesArray)
            params.putInt("count", devices.size)
            
            ctx.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit("FlirDevicesFound", params)
            Log.d(TAG, "Successfully emitted FlirDevicesFound")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to emit devices found", e)
        }
    }

    private fun emitBatteryState(level: Int, isCharging: Boolean) {
        val ctx = reactContext
        if (ctx == null) {
            Log.w(TAG, "Cannot emit FlirBatteryUpdated - reactContext is null!")
            return
        }
        try {
            val params = Arguments.createMap().apply {
                putInt("level", level)
                putBoolean("isCharging", isCharging)
            }
            ctx.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit("FlirBatteryUpdated", params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to emit battery state", e)
        }
    }
    
    private fun emitError(message: String) {
        val ctx = reactContext ?: return
        try {
            val params = Arguments.createMap().apply {
                putString("error", message)
            }
            ctx.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit("FlirError", params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to emit error", e)
        }
    }
    
    // Legacy compatibility
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
    
    // Legacy methods - no-ops or simple forwards
    fun setEmulatorMode(enabled: Boolean) {
        Log.d(TAG, "setEmulatorMode($enabled) - legacy, use startDiscovery() instead")
        if (enabled) {
            startDiscovery(retry = true)
        }
    }
    
    fun enableEmulatorMode() = setEmulatorMode(true)
    
    fun forceEmulatorMode(type: String = "FLIR_ONE_EDGE") {
        Log.d(TAG, "forceEmulatorMode($type) - legacy, use startDiscovery() instead")
        startDiscovery(retry = true)
    }
    
    fun setPreferredEmulatorType(type: String) {
        Log.d(TAG, "setPreferredEmulatorType($type) - legacy, no longer used")
    }
    
    fun updateAcol(value: Float) {
        // No-op - not used in simplified version
    }
    
    /**
     * Cleanup
     */
    fun destroy() {
        stop()
        sdkManager?.destroy()
        sdkManager = null
        isInitialized = false
    }
}
