package flir.android

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactContext
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.facebook.react.uimanager.ThemedReactContext
import com.flir.thermalsdk.live.Identity
import com.flir.thermalsdk.image.Palette
import com.flir.thermalsdk.image.PaletteManager
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicLong

/**
 * Simplified FlirManager - bridge between React Native and FlirSdkManager
 * Matches the simplified pattern: scan -> connect -> stream -> disconnect
 */
object FlirManager {
    private const val TAG = "FlirManager"
    
    private var sdkManager: FlirSdkManager? = null
    private var reactContext: ReactContext? = null
    
    // Frame rate limiting for RN events
    private val lastEmitMs = AtomicLong(0)
    private val minEmitIntervalMs = 100L // ~10 fps max for RN events
    
    // Cached palette list to avoid repeated JNI calls (especially if linkage is unstable)
    private var cachedPalettes: List<*>? = null
    private var cachedPaletteNames: List<String>? = null
    
    // Cached reflection for performance
    private var coolField: java.lang.reflect.Field? = null
    
    // State
    private var isInitialized = false
    private var isScanning = false
    private var isConnected = false
    private var isStreaming = false
    private var connectedDeviceId: String? = null
    private var connectedDeviceName: String? = null
    
    // Manual gating
    var manualOnly: Boolean = true

    // Concurrency control
    private val shouldProcessFrames = java.util.concurrent.atomic.AtomicBoolean(false)
    private val isUpdatingTexture = java.util.concurrent.atomic.AtomicBoolean(false)
    
    // Latest bitmap
    private var latestBitmap: Bitmap? = null
    
    // Callbacks
    interface TextureUpdateCallback {
        fun onTextureUpdate(bitmap: Bitmap, textureUnit: Int)
    }
    
    private var textureCallback: TextureUpdateCallback? = null
    
    fun setTextureCallback(callback: TextureUpdateCallback?) {
        textureCallback = callback
    }

    interface TemperatureUpdateCallback {
        fun onTemperatureUpdate(temperature: Double)
    }

    private var temperatureCallback: TemperatureUpdateCallback? = null

    fun setTemperatureCallback(callback: TemperatureUpdateCallback?) {
        temperatureCallback = callback
    }
    
    fun getLatestBitmap(): Bitmap? = latestBitmap

    // Stubs for removed features
    fun setPreferSdkRotation(prefer: Boolean) { /* No-op */ }
    fun isPreferSdkRotation(): Boolean = false
    fun getBatteryLevel(): Int = -1
    fun isBatteryCharging(): Boolean = false
    
    fun setPalette(name: String) {
        sdkManager?.setPalette(name)
    }
    
    fun getAvailablePalettes(): List<String> {
        // Return hardcoded list to avoid PaletteManager class-loading crashes
        // and ensure maximum performance. Matches standard FLIR and shader palettes.
        return listOf("WhiteHot", "Iron", "Rainbow", "Arctic", "Lava", "Coldest", "Hottest", "Wheel")
    }
    
    /**
     * Initialize the FLIR SDK
     */
    fun init(context: Context) {
        if (context is ReactContext) {
            reactContext = context
        }
        
        if (isInitialized) return
        
        sdkManager = FlirSdkManager.getInstance(context)
        sdkManager?.setListener(sdkListener)
        sdkManager?.initialize()
        
        isInitialized = true
        Log.i(TAG, "FlirManager initialized")
    }
    
    /**
     * Start scanning
     */
    @Synchronized
    fun startDiscovery(retry: Boolean = false) {
        if (manualOnly && !retry) {
            Log.w(TAG, "🔭 [FLIR] Discovery blocked: manualOnly is enabled. Use startManualDiscovery() or disable the gate.")
            return
        }

        if (!isInitialized && reactContext != null) {
            init(reactContext!!)
        }
        
        if (isScanning && !retry) return
        
        Log.i(TAG, "🔭 [FLIR] Starting discovery (retry=$retry)...")
        isScanning = true
        emitDeviceState("discovering")
        sdkManager?.scan()
    }

    /**
     * Explicitly starts discovery regardless of manualOnly flag.
     * Use this for JS-triggered scans.
     */
    @Synchronized
    fun startManualDiscovery() {
        Log.i(TAG, "🔭 [FLIR] Manual discovery requested - opening gate.")
        manualOnly = false
        startDiscovery(true)
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
    @Synchronized
    fun stopDiscovery() {
        Log.i(TAG, "Stopping FlirManager discovery...")
        sdkManager?.stopScan()
        isScanning = false
    }
    
    /**
     * Connect to a device
     */
    @Synchronized
    fun connectToDevice(deviceId: String?) {
        if (deviceId == null) {
            Log.e(TAG, "connectToDevice: deviceId is null")
            return
        }
        Log.i(TAG, "connectToDevice: $deviceId")
        
        val devices = sdkManager?.discoveredDevices ?: emptyList()
        val identity = devices.find { it.deviceId == deviceId }
        
        if (identity != null) {
            shouldProcessFrames.set(true)
            sdkManager?.connect(identity)
        } else {
            Log.e(TAG, "Device not found: $deviceId")
            emitError("Device not found: $deviceId")
        }
    }

    fun switchToDevice(deviceId: String) {
        connectToDevice(deviceId)
    }
    
    /**
     * Disconnect
     */
    @Synchronized
    fun disconnect() {
        Log.i(TAG, "Disconnecting FlirManager...")
        shouldProcessFrames.set(false)
        sdkManager?.disconnect()
        isConnected = false
        isStreaming = false
        connectedDeviceId = null
        connectedDeviceName = null
    }
    
    /**
     * Stop everything
     */
    @Synchronized
    fun stop() {
        Log.i(TAG, "Stopping FlirManager completely...")
        shouldProcessFrames.set(false)
        
        // Clear callbacks first to prevent any more frames/updates from hitting Java/RN
        textureCallback = null
        temperatureCallback = null
        
        disconnect()
        stopDiscovery()
        latestBitmap = null
        Log.i(TAG, "FlirManager stopped")
    }

    @Synchronized
    fun simulateContextLoss() {
        latestBitmap = null
        emitDeviceState(if (isStreaming) "streaming" else "connected")
    }
    
    // Stub legacy methods
    fun startStream() { /* handled automatically by connect */ }
    fun stopStream() { sdkManager?.stopStream() }
    
    /**
     * Get temperature
     */
    fun getTemperatureAt(x: Int, y: Int): Double? {
        val temp = sdkManager?.getTemperatureAt(x, y)
        return if (temp != null && !temp.isNaN()) temp else null
    }
    
    fun getTemperatureAtNormalized(nx: Double, ny: Double, rotation: Int = -90): Double? {
        val bitmap = latestBitmap ?: return null
        
        // Map UI normalized (0..1) to Raw sensor normalized (0..1) based on display rotation
        // Using generic trigonometric rotation formula for total precision
        val angle = -rotation.toDouble() // Inverse the display rotation
        val rad = Math.toRadians(angle)
        val cosA = Math.cos(rad)
        val sinA = Math.sin(rad)
        
        // Rotate around center (0.5, 0.5)
        val dx = nx - 0.5
        val dy = ny - 0.5
        val rawX = dx * cosA - dy * sinA + 0.5
        val rawY = dx * sinA + dy * cosA + 0.5
        
        return FlirSdkManager.getInstance(reactContext).getTemperatureAtNormalized(rawX, rawY)
    }
    
    fun getTemperatureAtPoint(x: Int, y: Int): Double? = getTemperatureAt(x, y)
    
    /**
     * Get discovered devices
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
    
    fun getConnectedDeviceInfo(): String {
        return connectedDeviceName ?: "Not connected"
    }
    
     /**
     * Capture a high-fidelity radiometric snapshot (saves thermal data)
     */
    fun captureRadiometricSnapshot(path: String, callback: FlirSdkManager.SnapshotCallback? = null) {
        sdkManager?.captureRadiometricSnapshot(path, callback)
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
            null
        }
    }
    
    // SDK Listener
    private val sdkListener = object : FlirSdkManager.Listener {
        override fun onDeviceFound(identity: Identity) {
            // Devices updated event handles the list, but we can log unique finds
        }
        
        override fun onDeviceListUpdated(devices: List<Identity>) {
            Log.d(TAG, "Devices found: ${devices.size}")
            emitDevicesFound(devices)
        }
        
        override fun onConnected(identity: Identity) {
            Log.i(TAG, "Connected to: ${identity.deviceId}")
            isConnected = true
            connectedDeviceId = identity.deviceId
            connectedDeviceName = identity.deviceId
            emitDeviceState("connected")
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
            // IMMEDIATE STOP CHECK
            if (!shouldProcessFrames.get()) {
                return
            }

            // THROTTLE: Limit to ~30 FPS for smoother streaming
            val now = System.currentTimeMillis()
            if (now - lastEmitMs.get() < 33) { // 33ms ~= 30 FPS
                return
            }
            lastEmitMs.set(now)

            latestBitmap = bitmap
            
            // If this is the first frame, notify JS that we are now streaming
            if (!isStreaming) {
                isStreaming = true
                emitDeviceState("streaming")
            }
            
            // NON-BLOCKING TEXTURE UPDATE
            if (textureCallback != null) {
                // We use try-lock to ensure we don't pile up parallel calls, 
                // though usually onFrame is serial. 
                if (isUpdatingTexture.compareAndSet(false, true)) {
                    try {
                        textureCallback?.onTextureUpdate(bitmap, 0)
                    } catch (e: Exception) {
                        Log.e(TAG, "Texture update failed", e)
                    } finally {
                        isUpdatingTexture.set(false)
                    }
                }
            }
        }
        
        override fun onError(message: String) {
            emitError(message)
        }
    }
    
    // React Native Emitters
    
    private fun emitDeviceState(state: String) {
        val ctx = reactContext ?: return
        try {
            val params = Arguments.createMap().apply {
                putString("state", state)
                putBoolean("isConnected", isConnected)
                putBoolean("isStreaming", isStreaming)
                putBoolean("isEmulator", isEmulator())
                connectedDeviceName?.let { putString("deviceName", it) }
            }
            ctx.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit("FlirDeviceConnected", params)
            ctx.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit("FlirStateChanged", params)
        } catch (e: Exception) { }
    }
    
    private fun emitDevicesFound(devices: List<Identity>) {
        val ctx = reactContext ?: return
        try {
            val params = Arguments.createMap()
            val devicesArray: WritableArray = Arguments.createArray()
            
            devices.forEach { identity ->
                val deviceMap = Arguments.createMap().apply {
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
        } catch (e: Exception) { }
    }
    
    private fun emitError(message: String) {
        val ctx = reactContext ?: return
        try {
            val params = Arguments.createMap().apply {
                putString("error", message)
                putString("message", message)
            }
            ctx.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit("FlirError", params)
        } catch (e: Exception) { }
    }
    
    // Legacy methods placeholders
    @JvmStatic fun getInstance(): FlirManager = this
    
    interface DiscoveryCallback {
        fun onDeviceFound(deviceName: String)
        fun onDiscoveryTimeout()
        fun onEmulatorEnabled()
    }
    fun setDiscoveryCallback(callback: DiscoveryCallback?) { /* No-op */ }
    fun setEmulatorMode(enabled: Boolean) { startDiscovery() }
    fun enableEmulatorMode() = startDiscovery()
    fun forceEmulatorMode(type: String = "FLIR_ONE_EDGE") { startDiscovery() }
    fun setPreferredEmulatorType(type: String) { }
    fun updateAcol(value: Float) {
        try {
            if (coolField == null) {
                val varClass = Class.forName("ilabs.libs.io.data.Var")
                coolField = varClass.getField("cool")
            }
            
            val rawIdx = value.toInt()
            var shaderIdx = rawIdx
            if (shaderIdx > 16) {
                shaderIdx = shaderIdx % 16 // Shader loop
            }
            
            coolField?.set(null, shaderIdx)
            
        } catch (e: Throwable) {
            Log.w(TAG, "updateAcol reflection failed: ${e.message}")
        }
    }

    /**
     * Generate icons for all default palettes and save to cache.
     */
    fun generatePaletteIcons(context: Context): List<Map<String, String>> {
        val results = mutableListOf<Map<String, String>>()
        val paletteNames = getAvailablePalettes()
        for (name in paletteNames) {
            results.add(mapOf(
                "name" to name,
                "uri" to "" // No URI - rely on local assets if any
            ))
        }
        return results
    }

    fun getPalettesWithIcons(context: Context? = null): List<Map<String, String>> {
        val ctx = context ?: reactContext ?: return emptyList()
        return generatePaletteIcons(ctx)
    }

    fun destroy() { stop() }
}
