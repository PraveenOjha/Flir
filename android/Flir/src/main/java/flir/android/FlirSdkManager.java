package flir.android;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import dalvik.system.DexClassLoader;

/**
 * FLIR SDK Manager - Handles device discovery, connection, and streaming using reflection.
 * Supports USB, NETWORK (FLIR ONE Edge), and EMULATOR interfaces.
 * All SDK calls use reflection for loose binding - no compile-time dependency on FLIR SDK.
 * 
 * Flow:
 * isFlir=true → startDiscovery()
 *   ↓ disconnect current device (if any)
 *   ↓ (timeout: 0s if isEmu=true, 5s otherwise)
 *   ├─ Devices found → emit to RN → stream from device[0]
 *   │                    ↓ RN sends deviceId
 *   │                    └─ disconnect → switch to deviceId
 *   ├─ No device OR isEmu=true → emulatorDiscovery()
 *   │                            ↓ Check emulator type setting
 *   │                            ├─ FLIR_ONE_EDGE (default)
 *   │                            └─ FLIR_ONE
 *   │                            └─ stream from emulator
 *   └─ Streaming: connect → start stream → upload to texture
 *                 ↓ acol changes → setPalette()
 *                 ↓ touch point → getTemperatureAt(x,y)
 */
public class FlirSdkManager {
    private static final String TAG = "FlirSdkManager";
    private static final String FLOW_TAG = "FLIR_FLOW"; // For step-by-step flow logging
    
    // Step counter for tracking flow
    private int stepCounter = 0;
    private long flowStartTime = 0;
    
    private void logStep(String step, String details) {
        stepCounter++;
        long elapsed = flowStartTime > 0 ? System.currentTimeMillis() - flowStartTime : 0;
        String msg = String.format("[Step %d] [+%dms] %s: %s", stepCounter, elapsed, step, details);
        Log.i(FLOW_TAG, msg);
    }
    
    private void resetFlowTracking() {
        stepCounter = 0;
        flowStartTime = System.currentTimeMillis();
        Log.i(FLOW_TAG, "========== FLIR FLOW STARTED ==========");
    }
    
    // Discovery timeout in milliseconds
    private static final long DISCOVERY_TIMEOUT_DEVICE_MS = 5000; // 5 seconds for real devices
    private static final long DISCOVERY_TIMEOUT_EMULATOR_MS = 0;   // Immediate for emulator mode
    
    // Emulator types
    public enum EmulatorType {
        FLIR_ONE_EDGE,  // Default - WiFi emulator
        FLIR_ONE        // USB emulator
    }
    
    // Communication interfaces (mirrors SDK enum)
    public enum CommInterface {
        USB,
        NETWORK,
        EMULATOR
    }

    // Listener interface for callbacks
    public interface Listener {
        void onFrame(Bitmap bitmap);
        void onTemperature(double temp, int x, int y);
        void onDeviceFound(String deviceId, String deviceName, boolean isEmulator);
        void onDeviceListUpdated(List<DeviceInfo> devices);
        void onDeviceConnected(String deviceId, String deviceName, boolean isEmulator);
        void onDeviceDisconnected();
        void onDiscoveryStarted();
        void onDiscoveryTimeout();
        void onStreamStarted(String streamType);
        void onError(String error);
    }
    
    // Device info class for discovered devices
    public static class DeviceInfo {
        public final String deviceId;
        public final String deviceName;
        public final boolean isEmulator;
        public final CommInterface commInterface;
        public final Object identity; // SDK Identity object (kept for connection)
        
        DeviceInfo(String id, String name, boolean emu, CommInterface iface, Object identity) {
            this.deviceId = id;
            this.deviceName = name;
            this.isEmulator = emu;
            this.commInterface = iface;
            this.identity = identity;
        }
    }

    private final Listener listener;
    private final android.content.Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    
    // SDK objects (all via reflection)
    private ClassLoader sdkClassLoader = null;
    private Object discoveryFactory = null;
    private Object discoveryListener = null;
    private Object cameraObj = null;
    private Object streamerObj = null;
    private Object currentStream = null;
    private Object currentPalette = null;
    
    // State tracking
    private final AtomicBoolean isDiscovering = new AtomicBoolean(false);
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private final AtomicBoolean isStreaming = new AtomicBoolean(false);
    private final AtomicBoolean isEmulatorMode = new AtomicBoolean(false);
    private final CopyOnWriteArrayList<DeviceInfo> discoveredDevices = new CopyOnWriteArrayList<>();
    private ScheduledFuture<?> discoveryTimeoutFuture = null;
    private DeviceInfo connectedDevice = null;
    private EmulatorType emulatorType = EmulatorType.FLIR_ONE_EDGE;
    
    // Frame state
    private volatile Bitmap latestFrame = null;
    private String sdkJarPath = null;
    private String currentStreamKind = null;
    
    // Frame counting for debug logging
    private int frameCount = 0;
    private long lastFrameLogTime = 0;
    private int successfulBitmapCount = 0;

    FlirSdkManager(Listener listener, android.content.Context context) {
        this.listener = listener;
        this.appContext = context != null ? context.getApplicationContext() : null;
    }
    
    // ==================== PUBLIC API ====================
    
    /**
     * Set the emulator type to use when no physical device is found
     */
    public void setEmulatorType(EmulatorType type) {
        this.emulatorType = type;
        Log.i(TAG, "[FLIR] Emulator type set to: " + type);
        logStep("SET_EMULATOR_TYPE", "type=" + type + " (FLIR_ONE_EDGE=WiFi, FLIR_ONE=USB)");
    }
    
    /**
     * Start device discovery. Will disconnect current device first.
     * @param forceEmulator If true, skip device discovery and connect to emulator immediately
     */
    public void startDiscovery(boolean forceEmulator) {
        resetFlowTracking();
        logStep("START_DISCOVERY", "forceEmulator=" + forceEmulator + ", emulatorType=" + emulatorType);
        Log.i(TAG, "[FLIR] startDiscovery(forceEmulator=" + forceEmulator + ")");
        
        // Always disconnect first
        if (isConnected.get()) {
            logStep("DISCONNECT_PREVIOUS", "Disconnecting current device before discovery");
            disconnect();
        }
        
        // Clear discovered devices
        discoveredDevices.clear();
        logStep("CLEAR_DEVICES", "Cleared discovered devices list");
        
        if (forceEmulator) {
            // Immediate emulator mode
            logStep("MODE_EMULATOR", "Forcing emulator mode - skipping device discovery");
            isEmulatorMode.set(true);
            startEmulatorDiscovery();
        } else {
            // Normal discovery with timeout
            logStep("MODE_FULL_DISCOVERY", "Starting full discovery (USB+NETWORK+EMULATOR), timeout=" + DISCOVERY_TIMEOUT_DEVICE_MS + "ms");
            isEmulatorMode.set(false);
            startFullDiscovery();
        }
    }
    
    /**
     * Stop discovery scan
     */
    public void stopDiscovery() {
        Log.i(TAG, "[FLIR] stopDiscovery()");
        cancelDiscoveryTimeout();
        isDiscovering.set(false);
        
        try {
            if (discoveryFactory != null) {
                Method stopMethod = discoveryFactory.getClass().getMethod("stop");
                stopMethod.invoke(discoveryFactory);
            }
        } catch (Throwable t) {
            Log.w(TAG, "[FLIR] stopDiscovery failed: " + t.getMessage());
        }
    }
    
    /**
     * Connect to a specific device by ID
     */
    public void connectToDevice(String deviceId) {
        Log.i(TAG, "[FLIR] connectToDevice: " + deviceId);
        
        // Find device in discovered list
        DeviceInfo target = null;
        for (DeviceInfo d : discoveredDevices) {
            if (d.deviceId.equals(deviceId)) {
                target = d;
                break;
            }
        }
        
        if (target == null) {
            notifyError("Device not found: " + deviceId);
            return;
        }
        
        // Disconnect current if needed
        if (isConnected.get()) {
            disconnect();
        }
        
        // Connect to target
        connectToIdentity(target);
    }
    
    /**
     * Disconnect current device/emulator
     */
    public void disconnect() {
        Log.i(TAG, "[FLIR] disconnect()");
        
        // Stop streaming first
        stopStreaming();
        
        // Disconnect camera
        if (cameraObj != null) {
            try {
                Method disconnectMethod = cameraObj.getClass().getMethod("disconnect");
                disconnectMethod.invoke(cameraObj);
                Log.i(TAG, "[FLIR] Camera disconnected");
            } catch (Throwable t) {
                Log.w(TAG, "[FLIR] disconnect failed: " + t.getMessage());
            }
        }
        
        cameraObj = null;
        streamerObj = null;
        currentStream = null;
        connectedDevice = null;
        isConnected.set(false);
        isStreaming.set(false);
        
        if (listener != null) {
            mainHandler.post(() -> listener.onDeviceDisconnected());
        }
    }
    
    /**
     * Set palette by name (iron, rainbow, etc.)
     */
    public void setPalette(String paletteName) {
        Log.d(TAG, "[FLIR] setPalette: " + paletteName);
        
        if (streamerObj == null) {
            Log.w(TAG, "[FLIR] Cannot set palette - no active streamer");
            return;
        }
        
        scheduler.submit(() -> {
            try {
                // Get PaletteManager.getDefaultPalettes()
                Class<?> paletteManagerClass = findSdkClass("com.flir.thermalsdk.image.PaletteManager");
                Method getDefaultPalettes = paletteManagerClass.getMethod("getDefaultPalettes");
                Object palettes = getDefaultPalettes.invoke(null);
                
                // Find matching palette
                Object targetPalette = null;
                if (palettes instanceof List) {
                    for (Object p : (List<?>) palettes) {
                        Method getName = p.getClass().getMethod("getName");
                        String name = (String) getName.invoke(p);
                        if (name != null && name.equalsIgnoreCase(paletteName)) {
                            targetPalette = p;
                            break;
                        }
                    }
                }
                
                if (targetPalette != null) {
                    currentPalette = targetPalette;
                    Log.i(TAG, "[FLIR] Palette set to: " + paletteName);
                } else {
                    Log.w(TAG, "[FLIR] Palette not found: " + paletteName);
                }
            } catch (Throwable t) {
                Log.w(TAG, "[FLIR] setPalette failed: " + t.getMessage());
            }
        });
    }
    
    /**
     * Initialize default "iron" palette for thermal streaming.
     * Called automatically when thermal streaming starts if no palette is set.
     */
    private void initializeDefaultPalette() {
        try {
            Class<?> paletteManagerClass = findSdkClass("com.flir.thermalsdk.image.PaletteManager");
            Method getDefaultPalettes = paletteManagerClass.getMethod("getDefaultPalettes");
            Object palettes = getDefaultPalettes.invoke(null);
            
            if (palettes instanceof List) {
                // Try to find "iron" palette first, then fall back to first available
                Object ironPalette = null;
                Object firstPalette = null;
                
                for (Object p : (List<?>) palettes) {
                    if (firstPalette == null) firstPalette = p;
                    
                    try {
                        Method getName = p.getClass().getMethod("getName");
                        String name = (String) getName.invoke(p);
                        if (name != null && name.toLowerCase().contains("iron")) {
                            ironPalette = p;
                            break;
                        }
                    } catch (Throwable ignored) {}
                }
                
                currentPalette = ironPalette != null ? ironPalette : firstPalette;
                
                if (currentPalette != null) {
                    try {
                        Method getName = currentPalette.getClass().getMethod("getName");
                        String name = (String) getName.invoke(currentPalette);
                        Log.i(TAG, "[FLIR] Default palette initialized: " + name);
                    } catch (Throwable ignored) {
                        Log.i(TAG, "[FLIR] Default palette initialized");
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "[FLIR] initializeDefaultPalette failed: " + t.getMessage());
        }
    }
    
    /**
     * Get temperature at a specific point
     */
    public double getTemperatureAtPoint(int x, int y) {
        if (streamerObj == null) return Double.NaN;
        
        try {
            // Get the thermal image from streamer
            Method getImage = streamerObj.getClass().getMethod("getImage");
            Object thermalImage = getImage.invoke(streamerObj);
            
            if (thermalImage != null) {
                // Try getValueAt(Point)
                try {
                    Method getValueAt = thermalImage.getClass().getMethod("getValueAt", android.graphics.Point.class);
                    android.graphics.Point p = new android.graphics.Point(x, y);
                    Object temp = getValueAt.invoke(thermalImage, p);
                    if (temp instanceof Double) return (Double) temp;
                    if (temp instanceof Float) return ((Float) temp).doubleValue();
                } catch (NoSuchMethodException ignored) {}
                
                // Try getValues().getValueAt(x, y)
                try {
                    Method getValues = thermalImage.getClass().getMethod("getValues");
                    Object values = getValues.invoke(thermalImage);
                    if (values != null) {
                        Method valGetAt = values.getClass().getMethod("getValueAt", int.class, int.class);
                        Object temp = valGetAt.invoke(values, x, y);
                        if (temp instanceof Double) return (Double) temp;
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            Log.d(TAG, "[FLIR] getTemperatureAtPoint failed: " + t.getMessage());
        }
        
        return Double.NaN;
    }
    
    /**
     * Get latest frame bitmap
     */
    public Bitmap getLatestFrame() {
        return latestFrame;
    }
    
    /**
     * Check if streaming is active
     */
    public boolean isStreamingActive() {
        return isStreaming.get();
    }
    
    /**
     * Check if connected
     */
    public boolean isConnected() {
        return isConnected.get();
    }
    
    /**
     * Get current stream type
     */
    public String getCurrentStreamKind() {
        return currentStreamKind;
    }
    
    /**
     * Get list of discovered devices
     */
    public List<DeviceInfo> getDiscoveredDevices() {
        return new ArrayList<>(discoveredDevices);
    }
    
    /**
     * Cleanup resources
     */
    public void stop() {
        Log.i(TAG, "[FLIR] stop()");
        stopDiscovery();
        disconnect();
        scheduler.shutdownNow();
    }
    
    // ==================== DISCOVERY IMPLEMENTATION ====================
    
    private void startFullDiscovery() {
        Log.i(TAG, "[FLIR] Starting full discovery (USB, NETWORK, EMULATOR)");
        
        if (!initializeSdk()) {
            Log.w(TAG, "[FLIR] SDK not available, falling back to emulator");
            startEmulatorDiscovery();
            return;
        }
        
        isDiscovering.set(true);
        if (listener != null) {
            mainHandler.post(() -> listener.onDiscoveryStarted());
        }
        
        try {
            // Get DiscoveryFactory.getInstance()
            Class<?> discoveryFactoryClass = findSdkClass("com.flir.thermalsdk.live.discovery.DiscoveryFactory");
            Method getInstance = discoveryFactoryClass.getMethod("getInstance");
            discoveryFactory = getInstance.invoke(null);
            
            // Get CommunicationInterface enum values
            Class<?> commIfaceClass = findSdkClass("com.flir.thermalsdk.live.CommunicationInterface");
            Object usbInterface = Enum.valueOf((Class<Enum>) commIfaceClass, "USB");
            Object networkInterface = Enum.valueOf((Class<Enum>) commIfaceClass, "NETWORK");
            Object emulatorInterface = Enum.valueOf((Class<Enum>) commIfaceClass, "EMULATOR");
            
            // Create discovery listener proxy
            Class<?> listenerClass = findSdkClass("com.flir.thermalsdk.live.discovery.DiscoveryEventListener");
            discoveryListener = Proxy.newProxyInstance(
                getEffectiveClassLoader(),
                new Class<?>[] { listenerClass },
                (proxy, method, args) -> handleDiscoveryCallback(method.getName(), args)
            );
            
            // Create interface array [USB, NETWORK, EMULATOR]
            Object ifaceArray = java.lang.reflect.Array.newInstance(commIfaceClass, 3);
            java.lang.reflect.Array.set(ifaceArray, 0, usbInterface);
            java.lang.reflect.Array.set(ifaceArray, 1, networkInterface);
            java.lang.reflect.Array.set(ifaceArray, 2, emulatorInterface);
            
            // Start discovery scan
            Method scanMethod = discoveryFactoryClass.getMethod("scan", listenerClass, ifaceArray.getClass());
            scanMethod.invoke(discoveryFactory, discoveryListener, ifaceArray);
            
            Log.i(TAG, "[FLIR] Discovery scan started for USB, NETWORK, EMULATOR");
            
            // Set discovery timeout
            startDiscoveryTimeout(DISCOVERY_TIMEOUT_DEVICE_MS);
            
        } catch (Throwable t) {
            Log.e(TAG, "[FLIR] startFullDiscovery failed: " + t.getMessage(), t);
            notifyError("Discovery failed: " + t.getMessage());
            // Fallback to emulator
            startEmulatorDiscovery();
        }
    }
    
    private void startEmulatorDiscovery() {
        logStep("EMULATOR_DISCOVERY_START", "type=" + emulatorType);
        Log.i(TAG, "[FLIR] Starting emulator discovery (type=" + emulatorType + ")");
        
        if (!initializeSdk()) {
            logStep("SDK_INIT_FAILED", "FLIR SDK not available - check if SDK JAR is loaded");
            notifyError("FLIR SDK not available");
            return;
        }
        logStep("SDK_INITIALIZED", "FLIR SDK successfully initialized");
        
        isDiscovering.set(true);
        isEmulatorMode.set(true);
        
        try {
            // Get DiscoveryFactory
            Class<?> discoveryFactoryClass = findSdkClass("com.flir.thermalsdk.live.discovery.DiscoveryFactory");
            Method getInstance = discoveryFactoryClass.getMethod("getInstance");
            discoveryFactory = getInstance.invoke(null);
            
            // Get EMULATOR interface
            Class<?> commIfaceClass = findSdkClass("com.flir.thermalsdk.live.CommunicationInterface");
            Object emulatorInterface = Enum.valueOf((Class<Enum>) commIfaceClass, "EMULATOR");
            
            // Create discovery listener
            Class<?> listenerClass = findSdkClass("com.flir.thermalsdk.live.discovery.DiscoveryEventListener");
            discoveryListener = Proxy.newProxyInstance(
                getEffectiveClassLoader(),
                new Class<?>[] { listenerClass },
                (proxy, method, args) -> handleDiscoveryCallback(method.getName(), args)
            );
            
            // Create interface array [EMULATOR]
            Object ifaceArray = java.lang.reflect.Array.newInstance(commIfaceClass, 1);
            java.lang.reflect.Array.set(ifaceArray, 0, emulatorInterface);
            
            // Start discovery
            Method scanMethod = discoveryFactoryClass.getMethod("scan", listenerClass, ifaceArray.getClass());
            scanMethod.invoke(discoveryFactory, discoveryListener, ifaceArray);
            
            logStep("EMULATOR_SCAN_STARTED", "Scanning for emulator devices...");
            Log.i(TAG, "[FLIR] Emulator discovery started");
            
        } catch (Throwable t) {
            logStep("EMULATOR_DISCOVERY_ERROR", "Failed: " + t.getMessage());
            Log.e(TAG, "[FLIR] startEmulatorDiscovery failed: " + t.getMessage(), t);
            notifyError("Emulator discovery failed: " + t.getMessage());
        }
    }
    
    private Object handleDiscoveryCallback(String methodName, Object[] args) {
        switch (methodName) {
            case "onCameraFound":
                if (args != null && args.length > 0) {
                    handleCameraFound(args[0]);
                }
                break;
                
            case "onCameraLost":
                if (args != null && args.length > 0) {
                    handleCameraLost(args[0]);
                }
                break;
                
            case "onDiscoveryError":
                if (args != null && args.length > 1) {
                    Log.w(TAG, "[FLIR] Discovery error: " + args[1]);
                }
                break;
        }
        return null;
    }
    
    private void handleCameraFound(Object discoveredCamera) {
        try {
            // Get identity from DiscoveredCamera
            Method getIdentity = discoveredCamera.getClass().getMethod("getIdentity");
            Object identity = getIdentity.invoke(discoveredCamera);
            
            // Extract device info from identity
            String deviceId = extractDeviceId(identity);
            String deviceName = extractDeviceName(identity);
            CommInterface commInterface = extractCommInterface(identity);
            boolean isEmulator = commInterface == CommInterface.EMULATOR;
            
            logStep("DEVICE_FOUND", "name=" + deviceName + ", id=" + deviceId + ", interface=" + commInterface + ", isEmulator=" + isEmulator);
            Log.i(TAG, "[FLIR] Camera found: " + deviceName + " (" + commInterface + ")");
            
            // Create device info
            DeviceInfo deviceInfo = new DeviceInfo(deviceId, deviceName, isEmulator, commInterface, identity);
            
            // Add to list if not already present
            boolean exists = false;
            for (DeviceInfo d : discoveredDevices) {
                if (d.deviceId.equals(deviceId)) {
                    exists = true;
                    break;
                }
            }
            
            if (!exists) {
                discoveredDevices.add(deviceInfo);
                
                // Notify listener
                if (listener != null) {
                    final List<DeviceInfo> devices = new ArrayList<>(discoveredDevices);
                    mainHandler.post(() -> {
                        listener.onDeviceFound(deviceId, deviceName, isEmulator);
                        listener.onDeviceListUpdated(devices);
                    });
                }
                
                // If this is first device found (not emulator in normal mode), connect to it
                if (!isEmulator && discoveredDevices.size() == 1 && !isEmulatorMode.get()) {
                    cancelDiscoveryTimeout();
                    stopDiscovery();
                    connectToIdentity(deviceInfo);
                } else if (isEmulator && isEmulatorMode.get()) {
                    // In emulator mode, connect to first emulator found
                    stopDiscovery();
                    connectToIdentity(deviceInfo);
                }
            }
            
        } catch (Throwable t) {
            Log.e(TAG, "[FLIR] handleCameraFound failed: " + t.getMessage(), t);
        }
    }
    
    private void handleCameraLost(Object discoveredCamera) {
        try {
            Method getIdentity = discoveredCamera.getClass().getMethod("getIdentity");
            Object identity = getIdentity.invoke(discoveredCamera);
            String deviceId = extractDeviceId(identity);
            
            Log.i(TAG, "[FLIR] Camera lost: " + deviceId);
            
            // Remove from list
            discoveredDevices.removeIf(d -> d.deviceId.equals(deviceId));
            
            // If this was our connected device, disconnect
            if (connectedDevice != null && connectedDevice.deviceId.equals(deviceId)) {
                disconnect();
            }
            
        } catch (Throwable t) {
            Log.w(TAG, "[FLIR] handleCameraLost failed: " + t.getMessage());
        }
    }
    
    private void startDiscoveryTimeout(long timeoutMs) {
        cancelDiscoveryTimeout();
        
        if (timeoutMs <= 0) return;
        
        discoveryTimeoutFuture = scheduler.schedule(() -> {
            Log.i(TAG, "[FLIR] Discovery timeout");
            isDiscovering.set(false);
            
            if (listener != null) {
                mainHandler.post(() -> listener.onDiscoveryTimeout());
            }
            
            // If no physical devices found, try emulator
            boolean hasPhysicalDevice = false;
            for (DeviceInfo d : discoveredDevices) {
                if (!d.isEmulator) {
                    hasPhysicalDevice = true;
                    break;
                }
            }
            
            if (!hasPhysicalDevice) {
                Log.i(TAG, "[FLIR] No physical devices found, starting emulator");
                startEmulatorDiscovery();
            } else if (!discoveredDevices.isEmpty()) {
                // Connect to first device
                connectToIdentity(discoveredDevices.get(0));
            }
            
        }, timeoutMs, TimeUnit.MILLISECONDS);
    }
    
    private void cancelDiscoveryTimeout() {
        if (discoveryTimeoutFuture != null) {
            discoveryTimeoutFuture.cancel(false);
            discoveryTimeoutFuture = null;
        }
    }
    
    // ==================== CONNECTION IMPLEMENTATION ====================
    
    private void connectToIdentity(DeviceInfo deviceInfo) {
        logStep("CONNECT_START", "Connecting to: " + deviceInfo.deviceName + " (" + deviceInfo.commInterface + ")");
        Log.i(TAG, "[FLIR] Connecting to: " + deviceInfo.deviceName);
        
        scheduler.submit(() -> {
            try {
                // Create Camera instance
                logStep("CREATE_CAMERA", "Creating Camera instance");
                Class<?> cameraClass = findSdkClass("com.flir.thermalsdk.live.Camera");
                cameraObj = cameraClass.newInstance();
                
                // Create connection status listener
                Class<?> connStatusClass = findSdkClass("com.flir.thermalsdk.live.connectivity.ConnectionStatusListener");
                Object connListener = Proxy.newProxyInstance(
                    getEffectiveClassLoader(),
                    new Class<?>[] { connStatusClass },
                    (proxy, method, args) -> {
                        if ("onDisconnected".equals(method.getName())) {
                            Log.w(TAG, "[FLIR] Camera disconnected (callback)");
                            handleDisconnected();
                        }
                        return null;
                    }
                );
                
                // Try to connect with different method signatures
                boolean connected = false;
                
                // Try connect(Identity, ConnectionStatusListener, ConnectParameters)
                try {
                    Class<?> connectParamsClass = findSdkClass("com.flir.thermalsdk.live.ConnectParameters");
                    Object connectParams = connectParamsClass.newInstance();
                    Class<?> identityClass = findSdkClass("com.flir.thermalsdk.live.Identity");
                    Method connectMethod = cameraClass.getMethod("connect", 
                        identityClass, connStatusClass, connectParamsClass);
                    connectMethod.invoke(cameraObj, deviceInfo.identity, connListener, connectParams);
                    connected = true;
                } catch (NoSuchMethodException ignored) {}
                
                // Try connect(Identity, ConnectionStatusListener)
                if (!connected) {
                    try {
                        Class<?> identityClass = findSdkClass("com.flir.thermalsdk.live.Identity");
                        Method connectMethod = cameraClass.getMethod("connect",
                            identityClass, connStatusClass);
                        connectMethod.invoke(cameraObj, deviceInfo.identity, connListener);
                        connected = true;
                    } catch (NoSuchMethodException ignored) {}
                }
                
                // Try connect(Identity)
                if (!connected) {
                    try {
                        Class<?> identityClass = findSdkClass("com.flir.thermalsdk.live.Identity");
                        Method connectMethod = cameraClass.getMethod("connect", identityClass);
                        connectMethod.invoke(cameraObj, deviceInfo.identity);
                        connected = true;
                    } catch (NoSuchMethodException ignored) {}
                }
                
                if (!connected) {
                    throw new Exception("No suitable connect method found");
                }
                
                connectedDevice = deviceInfo;
                isConnected.set(true);
                
                logStep("CONNECTED", "Successfully connected to: " + deviceInfo.deviceName);
                Log.i(TAG, "[FLIR] Connected to: " + deviceInfo.deviceName);
                
                // Notify listener
                if (listener != null) {
                    mainHandler.post(() -> listener.onDeviceConnected(
                        deviceInfo.deviceId, deviceInfo.deviceName, deviceInfo.isEmulator));
                }
                
                // Start streaming after brief delay
                logStep("SCHEDULE_STREAMING", "Scheduling stream start in 500ms");
                scheduler.schedule(this::startStreaming, 500, TimeUnit.MILLISECONDS);
                
            } catch (Throwable t) {
                logStep("CONNECT_FAILED", "Connection failed: " + t.getMessage());
                Log.e(TAG, "[FLIR] Connection failed: " + t.getMessage(), t);
                notifyError("Connection failed: " + t.getMessage());
            }
        });
    }
    
    private void handleDisconnected() {
        cameraObj = null;
        streamerObj = null;
        currentStream = null;
        connectedDevice = null;
        isConnected.set(false);
        isStreaming.set(false);
        
        if (listener != null) {
            mainHandler.post(() -> listener.onDeviceDisconnected());
        }
    }
    
    // ==================== STREAMING IMPLEMENTATION ====================
    
    private void startStreaming() {
        if (cameraObj == null) {
            logStep("STREAM_SKIP", "Cannot start streaming - no camera object");
            Log.w(TAG, "[FLIR] Cannot start streaming - no camera");
            return;
        }
        
        logStep("STREAM_START", "Starting streaming process...");
        Log.i(TAG, "[FLIR] Starting streaming...");
        
        try {
            // Get camera streams
            Method getStreams = cameraObj.getClass().getMethod("getStreams");
            Object streams = getStreams.invoke(cameraObj);
            
            if (streams == null || !(streams instanceof List) || ((List<?>) streams).isEmpty()) {
                logStep("NO_STREAMS", "No streams available from camera");
                Log.w(TAG, "[FLIR] No streams available");
                return;
            }
            
            List<?> streamList = (List<?>) streams;
            logStep("STREAMS_FOUND", "Found " + streamList.size() + " stream(s)");
            Log.i(TAG, "[FLIR] Found " + streamList.size() + " stream(s)");
            
            // Prefer thermal stream, fallback to first available
            Object chosenStream = null;
            String streamType = "unknown";
            
            for (Object s : streamList) {
                if (s == null) continue;
                
                try {
                    Method isThermal = s.getClass().getMethod("isThermal");
                    Boolean thermal = (Boolean) isThermal.invoke(s);
                    
                    Method getName = null;
                    try { getName = s.getClass().getMethod("getName"); } catch (Throwable ignored) {}
                    String name = getName != null ? String.valueOf(getName.invoke(s)) : "unknown";
                    
                    Log.d(TAG, "[FLIR] Stream: " + name + ", thermal=" + thermal);
                    
                    if (thermal != null && thermal) {
                        chosenStream = s;
                        streamType = "thermal";
                        break;
                    } else if (chosenStream == null) {
                        chosenStream = s;
                        streamType = "visual";
                    }
                } catch (Throwable ignored) {}
            }
            
            if (chosenStream == null) {
                chosenStream = streamList.get(0);
                streamType = "default";
            }
            
            currentStream = chosenStream;
            currentStreamKind = streamType;
            
            logStep("STREAM_SELECTED", "Using " + streamType + " stream");
            Log.i(TAG, "[FLIR] Using stream type: " + streamType);
            
            // Create appropriate streamer
            if ("thermal".equals(streamType)) {
                logStep("CREATE_THERMAL_STREAMER", "Creating ThermalStreamer");
                Class<?> thermalStreamerClass = findSdkClass("com.flir.thermalsdk.live.streaming.ThermalStreamer");
                Class<?> streamClass = findSdkClass("com.flir.thermalsdk.live.streaming.Stream");
                streamerObj = thermalStreamerClass.getConstructor(streamClass).newInstance(chosenStream);
                
                // Initialize default palette if not already set
                if (currentPalette == null) {
                    logStep("INIT_PALETTE", "Initializing default 'iron' palette");
                    initializeDefaultPalette();
                } else {
                    logStep("PALETTE_EXISTS", "Palette already set, skipping init");
                }
            } else {
                logStep("CREATE_VISUAL_STREAMER", "Creating VisualStreamer");
                Class<?> visualStreamerClass = findSdkClass("com.flir.thermalsdk.live.streaming.VisualStreamer");
                Class<?> streamClass = findSdkClass("com.flir.thermalsdk.live.streaming.Stream");
                streamerObj = visualStreamerClass.getConstructor(streamClass).newInstance(chosenStream);
            }
            
            // Create OnReceived callback
            Class<?> onReceivedClass = findSdkClass("com.flir.thermalsdk.live.remote.OnReceived");
            Object onReceivedCallback = Proxy.newProxyInstance(
                getEffectiveClassLoader(),
                new Class<?>[] { onReceivedClass },
                (proxy, method, args) -> {
                    if ("run".equals(method.getName())) {
                        scheduler.submit(this::processFrame);
                    }
                    return null;
                }
            );
            
            // Create OnRemoteError callback
            Class<?> onErrorClass = findSdkClass("com.flir.thermalsdk.live.remote.OnRemoteError");
            Object onErrorCallback = Proxy.newProxyInstance(
                getEffectiveClassLoader(),
                new Class<?>[] { onErrorClass },
                (proxy, method, args) -> {
                    if ("run".equals(method.getName()) && args != null && args.length > 0) {
                        Log.e(TAG, "[FLIR] Stream error: " + args[0]);
                    }
                    return null;
                }
            );
            
            // Start the stream
            logStep("STREAM_STARTING", "Invoking stream.start()");
            Method startMethod = chosenStream.getClass().getMethod("start", onReceivedClass, onErrorClass);
            startMethod.invoke(chosenStream, onReceivedCallback, onErrorCallback);
            
            isStreaming.set(true);
            
            logStep("STREAM_STARTED", "Stream started successfully - type=" + streamType + ", waiting for frames...");
            Log.i(TAG, "[FLIR] Streaming started (" + streamType + ")");
            
            if (listener != null) {
                final String type = streamType;
                mainHandler.post(() -> listener.onStreamStarted(type));
            }
            
        } catch (Throwable t) {
            logStep("STREAM_ERROR", "Streaming failed: " + t.getMessage());
            Log.e(TAG, "[FLIR] startStreaming failed: " + t.getMessage(), t);
            notifyError("Streaming failed: " + t.getMessage());
        }
    }
    
    private void processFrame() {
        if (streamerObj == null) return;
        
        frameCount++;
        long now = System.currentTimeMillis();
        
        // Log first frame and then every 30 frames or every 5 seconds
        boolean shouldLog = frameCount == 1 || frameCount % 30 == 0 || (now - lastFrameLogTime > 5000);
        
        if (shouldLog) {
            logStep("PROCESS_FRAME", "frame=" + frameCount + ", streamType=" + currentStreamKind + ", bitmapsSuccess=" + successfulBitmapCount);
            lastFrameLogTime = now;
        }
        
        try {
            // Call streamer.update() first - this refreshes the streamer content
            Method updateMethod = streamerObj.getClass().getMethod("update");
            updateMethod.invoke(streamerObj);
            
            // Get image buffer from streamer.getImage()
            Method getImage = streamerObj.getClass().getMethod("getImage");
            Object imageBuffer = getImage.invoke(streamerObj);
            
            if (imageBuffer == null) {
                if (shouldLog) logStep("FRAME_NULL_BUFFER", "imageBuffer is null at frame " + frameCount);
                Log.d(TAG, "[FLIR] imageBuffer is null");
                return;
            }
            
            if (frameCount == 1) {
                logStep("FIRST_BUFFER", "Got first imageBuffer, type=" + imageBuffer.getClass().getSimpleName());
            }
            
            // For thermal streamer, we MUST create the bitmap INSIDE withThermalImage callback
            // This is critical - the palette affects the imageBuffer rendering only while inside the callback
            if ("thermal".equals(currentStreamKind)) {
                processThermalFrameWithCallback(imageBuffer);
            } else {
                // For visual stream, just convert to bitmap directly
                Bitmap bitmap = convertToBitmap(imageBuffer);
                if (bitmap != null) {
                    successfulBitmapCount++;
                    latestFrame = bitmap;
                    if (frameCount == 1) {
                        logStep("FIRST_BITMAP", "Visual bitmap created: " + bitmap.getWidth() + "x" + bitmap.getHeight());
                    }
                    if (listener != null) {
                        listener.onFrame(bitmap);
                    }
                } else if (frameCount <= 5) {
                    logStep("BITMAP_NULL", "Visual convertToBitmap returned null at frame " + frameCount);
                }
            }
            
        } catch (Throwable t) {
            // Log errors periodically to avoid spam
            if (System.currentTimeMillis() % 5000 < 100) {
                Log.d(TAG, "[FLIR] Frame processing error: " + t.getMessage());
            }
        }
    }
    
    /**
     * Process thermal frame using withThermalImage callback pattern.
     * CRITICAL: Both palette setting AND bitmap conversion must happen INSIDE the callback!
     * This matches the LiveStreamingKotlin example exactly.
     */
    private void processThermalFrameWithCallback(Object imageBuffer) {
        if (streamerObj == null) return;
        
        try {
            Class<?> thermalStreamerClass = findSdkClass("com.flir.thermalsdk.live.streaming.ThermalStreamer");
            if (!thermalStreamerClass.isInstance(streamerObj)) {
                // Not a ThermalStreamer, use direct conversion
                Bitmap bitmap = convertToBitmap(imageBuffer);
                if (bitmap != null) {
                    latestFrame = bitmap;
                    if (listener != null) listener.onFrame(bitmap);
                }
                return;
            }
            
            // Find withThermalImage method
            Method withThermalImageMethod = null;
            for (Method m : thermalStreamerClass.getMethods()) {
                if ("withThermalImage".equals(m.getName()) && m.getParameterCount() == 1) {
                    withThermalImageMethod = m;
                    break;
                }
            }
            
            if (withThermalImageMethod == null) {
                Log.w(TAG, "[FLIR] withThermalImage method not found, using fallback");
                // Fallback: try to set palette via getThermalImage, then convert
                try {
                    Method getThermalImage = streamerObj.getClass().getMethod("getThermalImage");
                    Object thermalImage = getThermalImage.invoke(streamerObj);
                    if (thermalImage != null && currentPalette != null) {
                        setPaletteOnThermalImage(thermalImage);
                    }
                } catch (Throwable ignored) {}
                
                Bitmap bitmap = convertToBitmap(imageBuffer);
                if (bitmap != null) {
                    latestFrame = bitmap;
                    if (listener != null) listener.onFrame(bitmap);
                }
                return;
            }
            
            // Create Consumer proxy that does BOTH: set palette AND create bitmap
            // This is the key insight from LiveStreamingKotlin!
            final Object imgBuffer = imageBuffer;
            Class<?> consumerClass = withThermalImageMethod.getParameterTypes()[0];
            Object consumer = Proxy.newProxyInstance(
                getEffectiveClassLoader(),
                new Class<?>[] { consumerClass },
                (proxy, method, args) -> {
                    if ("accept".equals(method.getName()) && args != null && args.length > 0) {
                        Object thermalImage = args[0];
                        if (thermalImage != null) {
                            // Step 1: Set palette on thermal image
                            if (currentPalette != null) {
                                setPaletteOnThermalImage(thermalImage);
                                if (frameCount == 1) {
                                    logStep("PALETTE_APPLIED", "Palette applied to ThermalImage inside callback");
                                }
                            }
                            
                            // Step 2: Convert to bitmap INSIDE the callback
                            // This is critical - must happen while we have access to thermal image context
                            try {
                                Bitmap bitmap = convertToBitmap(imgBuffer);
                                if (bitmap != null) {
                                    successfulBitmapCount++;
                                    latestFrame = bitmap;
                                    if (frameCount == 1) {
                                        logStep("FIRST_THERMAL_BITMAP", "Thermal bitmap created INSIDE callback: " + bitmap.getWidth() + "x" + bitmap.getHeight());
                                    }
                                    if (listener != null) {
                                        listener.onFrame(bitmap);
                                    }
                                } else if (frameCount <= 5) {
                                    logStep("THERMAL_BITMAP_NULL", "Thermal convertToBitmap returned null at frame " + frameCount);
                                }
                            } catch (Throwable t) {
                                if (frameCount <= 5) {
                                    logStep("THERMAL_BITMAP_ERROR", "Bitmap conversion failed: " + t.getMessage());
                                }
                                Log.d(TAG, "[FLIR] Bitmap conversion in callback failed: " + t.getMessage());
                            }
                        } else if (frameCount <= 5) {
                            logStep("THERMAL_IMAGE_NULL", "ThermalImage is null in callback");
                        }
                    }
                    return null;
                }
            );
            
            withThermalImageMethod.invoke(streamerObj, consumer);
            
        } catch (Throwable t) {
            Log.d(TAG, "[FLIR] processThermalFrameWithCallback failed: " + t.getMessage());
            // Fallback to direct conversion
            Bitmap bitmap = convertToBitmap(imageBuffer);
            if (bitmap != null) {
                latestFrame = bitmap;
                if (listener != null) listener.onFrame(bitmap);
            }
        }
    }
    
    /**
     * Apply palette via ThermalStreamer.withThermalImage() callback pattern.
     * In the SDK: thermalStreamer.withThermalImage { it.palette = selectedPalette }
     * NOTE: This is now mostly unused - processThermalFrameWithCallback handles both palette and bitmap
     */
    private void applyPaletteViaThermalImage() {
        if (streamerObj == null || currentPalette == null) return;
        
        try {
            Class<?> thermalStreamerClass = findSdkClass("com.flir.thermalsdk.live.streaming.ThermalStreamer");
            if (!thermalStreamerClass.isInstance(streamerObj)) return;
            
            // Find withThermalImage method - it takes a Consumer<ThermalImage> callback
            // We'll use reflection to create a proxy for the Consumer interface
            Method withThermalImageMethod = null;
            for (Method m : thermalStreamerClass.getMethods()) {
                if ("withThermalImage".equals(m.getName()) && m.getParameterCount() == 1) {
                    withThermalImageMethod = m;
                    break;
                }
            }
            
            if (withThermalImageMethod == null) {
                // Fallback: Try to get ThermalImage directly
                try {
                    Method getThermalImage = streamerObj.getClass().getMethod("getThermalImage");
                    Object thermalImage = getThermalImage.invoke(streamerObj);
                    if (thermalImage != null) {
                        setPaletteOnThermalImage(thermalImage);
                    }
                } catch (Throwable ignored) {}
                return;
            }
            
            // Create Consumer proxy to call setPalette on ThermalImage
            Class<?> consumerClass = withThermalImageMethod.getParameterTypes()[0];
            Object consumer = Proxy.newProxyInstance(
                getEffectiveClassLoader(),
                new Class<?>[] { consumerClass },
                (proxy, method, args) -> {
                    if ("accept".equals(method.getName()) && args != null && args.length > 0) {
                        Object thermalImage = args[0];
                        if (thermalImage != null) {
                            setPaletteOnThermalImage(thermalImage);
                        }
                    }
                    return null;
                }
            );
            
            withThermalImageMethod.invoke(streamerObj, consumer);
            
        } catch (Throwable t) {
            Log.d(TAG, "[FLIR] applyPaletteViaThermalImage failed: " + t.getMessage());
        }
    }
    
    /**
     * Set palette on a ThermalImage object
     */
    private void setPaletteOnThermalImage(Object thermalImage) {
        if (thermalImage == null || currentPalette == null) return;
        
        try {
            Class<?> paletteClass = findSdkClass("com.flir.thermalsdk.image.Palette");
            Method setPalette = thermalImage.getClass().getMethod("setPalette", paletteClass);
            setPalette.invoke(thermalImage, currentPalette);
        } catch (Throwable t) {
            // Try with direct class
            try {
                Method setPalette = thermalImage.getClass().getMethod("setPalette", currentPalette.getClass());
                setPalette.invoke(thermalImage, currentPalette);
            } catch (Throwable ignored) {}
        }
    }
    
    private Bitmap convertToBitmap(Object imageBuffer) {
        boolean isFirstConvert = frameCount == 1;
        
        try {
            // Try BitmapAndroid.createBitmap(imageBuffer).bitMap (or getBitMap)
            Class<?> bitmapAndroidClass = findSdkClass("com.flir.thermalsdk.androidsdk.image.BitmapAndroid");
            
            if (isFirstConvert) {
                logStep("BITMAP_CONVERT_START", "imageBuffer type=" + imageBuffer.getClass().getName());
            }
            
            // Find a createBitmap method that works with our imageBuffer
            for (Method m : bitmapAndroidClass.getMethods()) {
                if ("createBitmap".equals(m.getName()) && m.getParameterCount() == 1) {
                    Class<?> paramType = m.getParameterTypes()[0];
                    if (paramType.isInstance(imageBuffer)) {
                        Object wrapper = m.invoke(null, imageBuffer);
                        if (wrapper != null) {
                            if (isFirstConvert) {
                                logStep("BITMAP_WRAPPER", "BitmapAndroid wrapper created: " + wrapper.getClass().getSimpleName());
                            }
                            
                            // Try different method names: bitMap, getBitMap, getBitmap
                            String[] methodNames = {"getBitMap", "bitMap", "getBitmap"};
                            for (String methodName : methodNames) {
                                try {
                                    Method getBitMap = wrapper.getClass().getMethod(methodName);
                                    Object bmp = getBitMap.invoke(wrapper);
                                    if (bmp instanceof Bitmap) {
                                        Bitmap bitmap = (Bitmap) bmp;
                                        if (bitmap.getWidth() > 0 && bitmap.getHeight() > 0) {
                                            if (isFirstConvert) {
                                                logStep("BITMAP_SUCCESS", "Got bitmap via " + methodName + "(): " + bitmap.getWidth() + "x" + bitmap.getHeight());
                                            }
                                            return bitmap;
                                        } else if (isFirstConvert) {
                                            logStep("BITMAP_EMPTY", "Bitmap has zero dimensions via " + methodName);
                                        }
                                    }
                                } catch (NoSuchMethodException ignored) {}
                            }
                            
                            // Also try as a field access (Kotlin property)
                            try {
                                java.lang.reflect.Field field = wrapper.getClass().getField("bitMap");
                                Object bmp = field.get(wrapper);
                                if (bmp instanceof Bitmap) {
                                    return (Bitmap) bmp;
                                }
                            } catch (Throwable ignored) {}
                        }
                    }
                }
            }
            
            Log.d(TAG, "[FLIR] BitmapAndroid.createBitmap method not found for imageBuffer type: " + imageBuffer.getClass().getName());
            
        } catch (Throwable t) {
            Log.d(TAG, "[FLIR] convertToBitmap primary method failed: " + t.getMessage());
        }
        
        // Try alternative: direct getBitmap() on imageBuffer
        try {
            Method getBitmap = imageBuffer.getClass().getMethod("getBitmap");
            Object bmp = getBitmap.invoke(imageBuffer);
            if (bmp instanceof Bitmap) {
                return (Bitmap) bmp;
            }
        } catch (Throwable ignored) {}
        
        return null;
    }
    
    private void stopStreaming() {
        if (currentStream != null) {
            try {
                Method stopMethod = currentStream.getClass().getMethod("stop");
                stopMethod.invoke(currentStream);
                Log.i(TAG, "[FLIR] Stream stopped");
            } catch (Throwable t) {
                Log.w(TAG, "[FLIR] stopStreaming failed: " + t.getMessage());
            }
        }
        
        streamerObj = null;
        currentStream = null;
        isStreaming.set(false);
    }
    
    // ==================== SDK LOADING ====================
    
    private boolean initializeSdk() {
        // Try direct class loading first
        try {
            Class.forName("com.flir.thermalsdk.live.CommunicationInterface");
            Log.i(TAG, "[FLIR SDK] Classes available on classpath");
            
            // Initialize SDK
            initializeThermalSdk();
            return true;
        } catch (ClassNotFoundException e) {
            Log.w(TAG, "[FLIR SDK] Classes not on classpath, trying AAR load");
        }
        
        // Try loading from downloaded AAR
        if (attemptLoadSdkFromAar()) {
            initializeThermalSdk();
            return true;
        }
        
        return false;
    }
    
    private void initializeThermalSdk() {
        try {
            Class<?> sdkClass = findSdkClass("com.flir.thermalsdk.live.ThermalSdkAndroid");
            Method initMethod = sdkClass.getMethod("init", android.content.Context.class);
            initMethod.invoke(null, appContext);
            Log.i(TAG, "[FLIR SDK] ThermalSdkAndroid.init() completed");
        } catch (Throwable t) {
            Log.w(TAG, "[FLIR SDK] ThermalSdkAndroid.init() failed: " + t.getMessage());
        }
    }
    
    private boolean attemptLoadSdkFromAar() {
        android.content.Context ctx = appContext;
        if (ctx == null) {
            Log.w(TAG, "[FLIR SDK] No application context available for SDK load");
            return false;
        }
        
        try {
            // First try: Load from architecture-specific DEX (new format from FlirSDKLoader)
            File dexFile = FlirSDKLoader.INSTANCE.getDexPath(ctx);
            if (dexFile != null && dexFile.exists()) {
                Log.i(TAG, "[FLIR SDK] Found DEX file: " + dexFile.getAbsolutePath() + " (size=" + dexFile.length() + ")");
                
                // Get native library directory path for DexClassLoader
                File nativeLibDir = FlirSDKLoader.INSTANCE.getNativeLibDir(ctx);
                String nativeLibPath = nativeLibDir != null ? nativeLibDir.getAbsolutePath() : null;
                Log.i(TAG, "[FLIR SDK] Native lib path: " + nativeLibPath);
                
                // Create DexClassLoader with native lib path
                File dexOutDir = ctx.getDir("dex", android.content.Context.MODE_PRIVATE);
                DexClassLoader dcl = new DexClassLoader(
                    dexFile.getAbsolutePath(),
                    dexOutDir.getAbsolutePath(),
                    nativeLibPath,  // This allows SDK to find .so files
                    ctx.getClassLoader()
                );
                
                // Verify class loading
                Class<?> test = Class.forName("com.flir.thermalsdk.live.CommunicationInterface", true, dcl);
                if (test != null) {
                    sdkClassLoader = dcl;
                    sdkJarPath = dexFile.getAbsolutePath();
                    Log.i(TAG, "[FLIR SDK] DexClassLoader created from DEX: " + dexFile.getAbsolutePath());
                    return true;
                }
            }
            
            // Fallback: Legacy AAR loading
            Log.i(TAG, "[FLIR SDK] No DEX found, trying legacy AAR locations...");
            
            File filesDir = ctx.getFilesDir();
            
            // Candidate search locations (ordered by preference)
            List<File> candidates = new ArrayList<>();
            
            // Primary: FlirSDKLoader download directory
            candidates.add(new File(filesDir, "FlirSDK/thermalsdk-release.aar"));
            candidates.add(new File(filesDir, "FlirSDK/androidsdk-release.aar"));
            candidates.add(new File(filesDir, "FlirSDK/thermalsdk.aar"));
            
            // Legacy locations
            candidates.add(new File(filesDir, "flir-sdk/thermalsdk-release.aar"));
            candidates.add(new File(filesDir, "thermalsdk-release.aar"));
            candidates.add(new File(filesDir, "thermalsdk.aar"));
            
            // External storage
            File extDir = ctx.getExternalFilesDir(null);
            if (extDir != null) {
                candidates.add(new File(extDir, "FlirSDK/thermalsdk-release.aar"));
                candidates.add(new File(extDir, "thermalsdk-release.aar"));
            }
            
            // Find first existing AAR
            File aarFile = null;
            StringBuilder tried = new StringBuilder();
            for (File f : candidates) {
                tried.append(f.getAbsolutePath()).append(f.exists() ? "(✓)," : "(✗),");
                if (f.exists()) {
                    aarFile = f;
                    break;
                }
            }
            
            if (aarFile == null) {
                Log.w(TAG, "[FLIR SDK] No AAR found. Tried: " + tried);
                return false;
            }
            
            Log.i(TAG, "[FLIR SDK] Found AAR: " + aarFile.getAbsolutePath());
            
            // Extract classes.jar from AAR to a private directory
            ZipFile zf = new ZipFile(aarFile);
            ZipEntry classesEntry = zf.getEntry("classes.jar");
            if (classesEntry == null) {
                Log.w(TAG, "[FLIR SDK] classes.jar not found in AAR");
                zf.close();
                return false;
            }
            
            // Use getDir() for a MODE_PRIVATE directory - required for DexClassLoader security
            File privateDir = ctx.getDir("flir_sdk", android.content.Context.MODE_PRIVATE);
            File outJar = new File(privateDir, "flir-classes.jar");
            
            // Delete old file if exists to ensure clean extraction
            if (outJar.exists()) {
                outJar.delete();
            }
            
            FileOutputStream fos = new FileOutputStream(outJar);
            java.io.InputStream is = zf.getInputStream(classesEntry);
            byte[] buf = new byte[8192];
            int r;
            while ((r = is.read(buf)) != -1) fos.write(buf, 0, r);
            is.close();
            fos.close();
            zf.close();
            
            // Set file to read-only (required for Android security)
            outJar.setReadOnly();
            
            Log.i(TAG, "[FLIR SDK] Extracted classes.jar to: " + outJar.getAbsolutePath() + " (size=" + outJar.length() + ")");
            
            // Create DexClassLoader with private dex output directory
            File dexOutDir = ctx.getDir("dex", android.content.Context.MODE_PRIVATE);
            DexClassLoader dcl = new DexClassLoader(
                outJar.getAbsolutePath(),
                dexOutDir.getAbsolutePath(),
                null,
                ctx.getClassLoader()
            );
            
            // Verify class loading
            Class<?> test = Class.forName("com.flir.thermalsdk.live.CommunicationInterface", true, dcl);
            if (test != null) {
                sdkClassLoader = dcl;
                sdkJarPath = outJar.getAbsolutePath();
                Log.i(TAG, "[FLIR SDK] DexClassLoader created from: " + outJar.getAbsolutePath());
                return true;
            }
            
        } catch (Throwable t) {
            Log.e(TAG, "[FLIR SDK] attemptLoadSdkFromAar failed: " + t.getMessage(), t);
        }
        
        return false;
    }
    
    private Class<?> findSdkClass(String name) throws ClassNotFoundException {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            if (sdkClassLoader != null) {
                return Class.forName(name, true, sdkClassLoader);
            }
            throw e;
        }
    }
    
    private ClassLoader getEffectiveClassLoader() {
        return sdkClassLoader != null ? sdkClassLoader : getClass().getClassLoader();
    }
    
    // ==================== HELPERS ====================
    
    private String extractDeviceId(Object identity) {
        try {
            Method getDeviceId = identity.getClass().getMethod("getDeviceId");
            Object result = getDeviceId.invoke(identity);
            return result != null ? result.toString() : "unknown";
        } catch (Throwable t) {
            return "device_" + System.currentTimeMillis();
        }
    }
    
    private String extractDeviceName(Object identity) {
        try {
            // Try getName() first
            try {
                Method getName = identity.getClass().getMethod("getName");
                Object result = getName.invoke(identity);
                if (result != null && !result.toString().isEmpty()) {
                    return result.toString();
                }
            } catch (Throwable ignored) {}
            
            // Try getDeviceId() as fallback
            Method getDeviceId = identity.getClass().getMethod("getDeviceId");
            Object result = getDeviceId.invoke(identity);
            return result != null ? result.toString() : "FLIR Camera";
        } catch (Throwable t) {
            return "FLIR Camera";
        }
    }
    
    private CommInterface extractCommInterface(Object identity) {
        try {
            Method getCommInterface = identity.getClass().getMethod("getCommunicationInterface");
            Object result = getCommInterface.invoke(identity);
            if (result != null) {
                String name = result.toString();
                if (name.contains("USB")) return CommInterface.USB;
                if (name.contains("NETWORK")) return CommInterface.NETWORK;
                if (name.contains("EMULATOR")) return CommInterface.EMULATOR;
            }
        } catch (Throwable ignored) {}
        return CommInterface.EMULATOR;
    }
    
    private void notifyError(String error) {
        Log.e(TAG, "[FLIR] Error: " + error);
        if (listener != null) {
            mainHandler.post(() -> listener.onError(error));
        }
    }
}
