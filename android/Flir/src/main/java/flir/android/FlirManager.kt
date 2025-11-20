package flir.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Base64
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.facebook.react.uimanager.ThemedReactContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicLong

object FlirManager {
    private val cameraHandler: CameraHandler = CameraHandler()
    private val lastEmitMs = AtomicLong(0)
    private val minEmitIntervalMs = 333L // ~3 fps
    private var discoveryStarted = false
    private var reactContext: ThemedReactContext? = null
    
    // Emulator and device state tracking
    private var isEmulatorMode = false
    private var isPhysicalDeviceConnected = false
    private var connectedIdentity: com.flir.thermalsdk.live.Identity? = null
    
    // Emulator and device state tracking
    private var isEmulatorMode = false
    private var isPhysicalDeviceConnected = false
    private var connectedIdentity: com.flir.thermalsdk.live.Identity? = null
    
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
            cameraHandler.getTemperatureAt(x, y)
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
    fun isDeviceConnected(): Boolean = isPhysicalDeviceConnected
    
    /**
     * Get information about the connected device
     */
    fun getConnectedDeviceInfo(): String {
        return when {
            connectedIdentity == null -> "Not connected"
            isEmulatorMode -> "Emulator (${connectedIdentity?.deviceId})"
            else -> "Physical device (${connectedIdentity?.deviceId})"
        }
    }

    fun init(context: Context) {
        try {
            val cls = Class.forName("com.flir.thermalsdk.live.ThermalSdkAndroid")
            val method = cls.getMethod("init", Context::class.java)
            method.invoke(null, context.applicationContext)
        } catch (ignored: Throwable) {
        }
    }

    fun startDiscoveryAndConnect(context: ThemedReactContext) {
        if (discoveryStarted) return
        discoveryStarted = true
        reactContext = context

        emitDeviceState("discovering", false)

        cameraHandler.startDiscovery(object : com.flir.thermalsdk.live.discovery.DiscoveryEventListener {
            override fun onCameraFound(discoveredCamera: com.flir.thermalsdk.live.discovery.DiscoveredCamera) {
                cameraHandler.add(discoveredCamera.identity)
                
                // Prioritize real device over emulator
                val realDevice = cameraHandler.getFlirOne()
                val emulatorDevice = cameraHandler.getFlirOneEmulator() ?: cameraHandler.getCppEmulator()
                val toConnect = realDevice ?: emulatorDevice
                
                if (toConnect != null) {
                    // Determine if we're connecting to emulator
                    isEmulatorMode = (realDevice == null)
                    isPhysicalDeviceConnected = (realDevice != null)
                    connectedIdentity = toConnect
                    
                    Thread {
                        try {
                            cameraHandler.connect(toConnect, com.flir.thermalsdk.live.connectivity.ConnectionStatusListener { errorCode ->
                                emitDeviceState("disconnected", false)
                                isPhysicalDeviceConnected = false
                                connectedIdentity = null
                            })
                            
                            val deviceType = if (isEmulatorMode) "emulator" else "device"
                            emitDeviceState("connected", true, mapOf("deviceType" to deviceType, "isEmulator" to isEmulatorMode))
                            FlirStatus.flirConnected = true
                            
                            cameraHandler.startStream(object : CameraHandler.StreamDataListener {
                                override fun images(dataHolder: FrameDataHolder) {
                                    handleIncomingFrames(dataHolder.msxBitmap, dataHolder.dcBitmap, context)
                                }

                                override fun images(msxBitmap: Bitmap?, dcBitmap: Bitmap?) {
                                    handleIncomingFrames(msxBitmap, dcBitmap, context)
                                }
                            })
                        } catch (e: Exception) {
                            emitDeviceState("disconnected", false)
                            FlirStatus.flirConnected = false
                            isPhysicalDeviceConnected = false
                            connectedIdentity = null
                        }
                    }.start()
                }
            }

            override fun onDiscoveryError(communicationInterface: com.flir.thermalsdk.live.CommunicationInterface, errorCode: com.flir.thermalsdk.ErrorCode) {
                emitDeviceState("discovery_error", false)
            }
        }, object : CameraHandler.DiscoveryStatus {
            override fun started() {}
            override fun stopped() {}
        })
    }

    fun stop() {
        try {
            cameraHandler.disconnect()
        } catch (ignored: Throwable) {}
        FlirStatus.flirConnected = false
        FlirStatus.flirStreaming = false
        discoveryStarted = false
        reactContext = null
    }

    fun getLatestFramePath(): String? {
        return FlirStatus.latestFramePath
    }

    fun getTemperatureAt(x: Int, y: Int): Double? {
        return try {
            cameraHandler.getTemperatureAt(x, y)
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
    fun isDeviceConnected(): Boolean = isPhysicalDeviceConnected
    
    /**
     * Get information about the connected device
     */
    fun getConnectedDeviceInfo(): String {
        return when {
            connectedIdentity == null -> "Not connected"
            isEmulatorMode -> "Emulator (${connectedIdentity?.deviceId})"
            else -> "Physical device (${connectedIdentity?.deviceId})"
        }
    }

    private fun handleIncomingFrames(msxBitmap: Bitmap?, dcBitmap: Bitmap?, ctx: ThemedReactContext) {
        val now = System.currentTimeMillis()
        if (now - lastEmitMs.get() < minEmitIntervalMs) return
        lastEmitMs.set(now)

        val bmp = msxBitmap ?: dcBitmap ?: return
        latestBitmap = bmp
        
        // Invoke texture callback for native GL/Metal filters (texture unit 7)
        textureCallback?.onTextureUpdate(bmp, 7)
        
        // Get and emit temperature data
        try {
            val temp = cameraHandler.getTemperatureAt(80, 60) // center point
            if (temp != null) {
                temperatureCallback?.onTemperatureData(temp, 80, 60)
            }
        } catch (ignored: Throwable) {}

        try {
            val cacheDir = ctx.cacheDir
            val outFile = File(cacheDir, "flir_latest_frame.png")
            val fos = FileOutputStream(outFile)
            bmp.compress(Bitmap.CompressFormat.PNG, 90, fos)
            fos.flush()
            fos.close()
            FlirStatus.latestFramePath = outFile.absolutePath
            FlirFrameCache.latestFramePath = outFile.absolutePath
            FlirStatus.flirStreaming = true

            val baos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 70, baos)
            val pngBytes = baos.toByteArray()
            val base64 = Base64.encodeToString(pngBytes, Base64.NO_WRAP)

            val params: WritableMap = Arguments.createMap().apply {
                putString("type", "frame")
                putString("path", outFile.absolutePath)
                putString("base64", "data:image/png;base64," + base64)
                putDouble("timestamp", (now / 1000.0))
            }
            try {
                ctx.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                    .emit("FlirFrame", params)
            } catch (e: Exception) {}

        } catch (e: Exception) {
            FlirStatus.flirStreaming = false
        }
    }

    private fun emitDeviceState(state: String, connected: Boolean, extras: Map<String, Any> = emptyMap()) {
        FlirStatus.flirConnected = connected
        val ctx = reactContext ?: return
        val params: WritableMap = Arguments.createMap().apply {
            putString("state", state)
            putBoolean("connected", connected)
            putString("message", if (connected) "FLIR device connected" else state)
            
            // Add any extra parameters
            extras.forEach { (key, value) ->
                when (value) {
                    is String -> putString(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is Double -> putDouble(key, value)
                }
            }
        }
        try {
            ctx.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit("FlirDeviceConnected", params)
        } catch (e: Exception) {}
    }
}
