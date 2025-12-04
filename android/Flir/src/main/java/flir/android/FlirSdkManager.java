package flir.android;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.flir.thermalsdk.ErrorCode;
import com.flir.thermalsdk.androidsdk.ThermalSdkAndroid;
import com.flir.thermalsdk.androidsdk.image.BitmapAndroid;
import com.flir.thermalsdk.image.ImageBuffer;
import com.flir.thermalsdk.image.JavaImageBuffer;
import com.flir.thermalsdk.image.Palette;
import com.flir.thermalsdk.image.PaletteManager;
import com.flir.thermalsdk.image.Point;
import com.flir.thermalsdk.image.ThermalImage;
import com.flir.thermalsdk.image.ThermalValue;
import com.flir.thermalsdk.live.Camera;
import com.flir.thermalsdk.live.CommunicationInterface;
import com.flir.thermalsdk.live.ConnectParameters;
import com.flir.thermalsdk.live.Identity;
import com.flir.thermalsdk.live.connectivity.ConnectionStatusListener;
import com.flir.thermalsdk.live.discovery.DiscoveredCamera;
import com.flir.thermalsdk.live.discovery.DiscoveryEventListener;
import com.flir.thermalsdk.live.discovery.DiscoveryFactory;
import com.flir.thermalsdk.live.remote.OnReceived;
import com.flir.thermalsdk.live.remote.OnRemoteError;
import com.flir.thermalsdk.live.streaming.Stream;
import com.flir.thermalsdk.live.streaming.ThermalStreamer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * FLIR SDK Manager - Handles device discovery, connection, and streaming.
 * Uses the official FLIR ThermalSDK directly (bundled in AAR).
 * 
 * Supports USB, NETWORK (FLIR ONE Edge), and EMULATOR interfaces.
 */
public class FlirSdkManager {
    private static final String TAG = "FlirSdkManager";
    private static final String FLOW_TAG = "FLIR_FLOW";
    
    // Discovery timeout in milliseconds
    private static final long DISCOVERY_TIMEOUT_DEVICE_MS = 5000;
    private static final long DISCOVERY_TIMEOUT_EMULATOR_MS = 0;
    
    // Emulator types
    public enum EmulatorType {
        FLIR_ONE_EDGE,  // WiFi emulator
        FLIR_ONE        // USB emulator
    }
    
    // Communication interfaces
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
        void onNoDeviceFound(); // Called when discovery times out with no real device - RN should enable EMU button
        void onStreamStarted(String streamType);
        void onError(String error);
    }
    
    // Correct emulator device IDs from FLIR SDK - ONLY these two are allowed
    private static final String EMULATOR_DEVICE_ID_FLIR_ONE = "EMULATED FLIR ONE";
    private static final String EMULATOR_DEVICE_ID_FLIR_ONE_EDGE = "EMULATED F1 EDGE PRO";
    
    // Known C++ emulator device IDs to block
    private static final String CPP_EMULATOR_ID = "C++ Emulator";
    
    /**
     * Check if a device ID is a VALID emulator (FLIR ONE or FLIR ONE Edge only).
     * Returns true ONLY for "EMULATED FLIR ONE" or "EMULATED F1 EDGE PRO".
     */
    private static boolean isValidEmulator(String deviceId) {
        if (deviceId == null) return false;
        return EMULATOR_DEVICE_ID_FLIR_ONE.equals(deviceId) || 
               EMULATOR_DEVICE_ID_FLIR_ONE_EDGE.equals(deviceId);
    }
    
    /**
     * Check if a device is the C++ emulator or any invalid/unwanted emulator.
     * Returns true for ANY emulator that is NOT our two valid ones.
     * Uses contains() checks for maximum detection coverage.
     */
    private static boolean isCppEmulator(String deviceId) {
        if (deviceId == null) return false;
        
        // If it's a valid emulator (EMULATED FLIR ONE or EMULATED F1 EDGE PRO), allow it
        if (isValidEmulator(deviceId)) {
            return false;
        }
        
        String idLower = deviceId.toLowerCase();
        
        // Block known C++ emulator ID "65" (contains check)
        if (idLower.contains("65") || deviceId.equals(CPP_EMULATOR_ID)) {
            return true;
        }
        
        // Block pure numeric device IDs (likely internal/test emulators)
        if (deviceId.matches("^\\d+$")) {
            return true;
        }
        
        // Block anything containing c++ or cpp
        if (idLower.contains("c++") || idLower.contains("cpp")) {
            return true;
        }
        
        // Block anything containing "emulate" or "emulator" that isn't our valid emulators
        if (idLower.contains("emulate") || idLower.contains("emulator")) {
            return true;
        }
        
        // Block any short numeric-looking IDs (1-3 digits often indicate internal test devices)
        if (deviceId.matches("^\\d{1,3}$")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if a device is an emulator by checking device ID or name.
     * Returns true if deviceId or deviceName contains "emulate" or "emulator" (case-insensitive).
     */
    private static boolean isEmulatorDevice(String deviceId, String deviceName) {
        if (deviceId != null) {
            String idLower = deviceId.toLowerCase();
            if (idLower.contains("emulate") || idLower.contains("emulator")) {
                return true;
            }
        }
        if (deviceName != null) {
            String nameLower = deviceName.toLowerCase();
            if (nameLower.contains("emulate") || nameLower.contains("emulator")) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Count real (non-emulator) devices in the discovered list.
     */
    private int countRealDevices() {
        int count = 0;
        for (DeviceInfo d : discoveredDevices) {
            if (!isEmulatorDevice(d.deviceId, d.deviceName) && !d.isEmulator) {
                count++;
            }
        }
        return count;
    }
    
    // Device info class
    public static class DeviceInfo {
        public final String deviceId;
        public final String deviceName;
        public final boolean isEmulator;
        public final CommInterface commInterface;
        public final Identity identity;
        
        DeviceInfo(String id, String name, boolean emu, CommInterface iface, Identity identity) {
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
    
    // SDK objects
    private Camera camera = null;
    private Stream currentStream = null;
    private ThermalStreamer thermalStreamer = null;
    private Palette currentPalette = null;
    
    // State tracking
    private final AtomicBoolean isDiscovering = new AtomicBoolean(false);
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private final AtomicBoolean isConnecting = new AtomicBoolean(false); // Prevent duplicate connection attempts
    private final AtomicBoolean isStreaming = new AtomicBoolean(false);
    private final AtomicBoolean isEmulatorMode = new AtomicBoolean(false);
    private final CopyOnWriteArrayList<DeviceInfo> discoveredDevices = new CopyOnWriteArrayList<>();
    private ScheduledFuture<?> discoveryTimeoutFuture = null;
    private DeviceInfo connectedDevice = null;
    private EmulatorType emulatorType = EmulatorType.FLIR_ONE_EDGE;
    
    // Frame state
    private volatile Bitmap latestFrame = null;
    private volatile ThermalImage currentThermalImage = null;
    private String currentStreamKind = null;
    
    // Step tracking for debugging
    private int stepCounter = 0;
    private long flowStartTime = 0;
    
    // SDK initialization state
    private static boolean sdkInitialized = false;

    FlirSdkManager(Listener listener, android.content.Context context) {
        this.listener = listener;
        this.appContext = context != null ? context.getApplicationContext() : null;
    }
    
    private void logStep(String step, String details) {
        stepCounter++;
        long elapsed = flowStartTime > 0 ? System.currentTimeMillis() - flowStartTime : 0;
        Log.i(FLOW_TAG, String.format("[Step %d] [+%dms] %s: %s", stepCounter, elapsed, step, details));
    }
    
    private void resetFlowTracking() {
        stepCounter = 0;
        flowStartTime = System.currentTimeMillis();
        Log.i(FLOW_TAG, "========== FLIR FLOW STARTED ==========");
    }
    
    // ==================== SDK INITIALIZATION ====================
    
    private boolean initializeSdk() {
        if (sdkInitialized) {
            Log.d(TAG, "[FLIR SDK] Already initialized");
            return true;
        }
        
        if (appContext == null) {
            Log.e(TAG, "[FLIR SDK] No context available");
            return false;
        }
        
        try {
            Log.i(TAG, "[FLIR SDK] Initializing ThermalSdkAndroid...");
            ThermalSdkAndroid.init(appContext);
            sdkInitialized = true;
            Log.i(TAG, "[FLIR SDK] SDK Version: " + ThermalSdkAndroid.getVersion());
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "[FLIR SDK] Initialization failed: " + t.getMessage(), t);
            notifyError("SDK initialization failed: " + t.getMessage());
            return false;
        }
    }
    
    // ==================== PUBLIC API ====================
    
    public void setEmulatorType(EmulatorType type) {
        this.emulatorType = type;
        Log.i(TAG, "[FLIR] Emulator type set to: " + type);
        logStep("SET_EMULATOR_TYPE", "type=" + type);
    }
    
    public void startDiscovery(boolean forceEmulator) {
        resetFlowTracking();
        logStep("START_DISCOVERY", "forceEmulator=" + forceEmulator + ", emulatorType=" + emulatorType);
        Log.i(TAG, "[FLIR] startDiscovery(forceEmulator=" + forceEmulator + ")");
        
        // Disconnect current device first
        if (isConnected.get()) {
            logStep("DISCONNECT_PREVIOUS", "Disconnecting current device");
            disconnect();
        }
        
        discoveredDevices.clear();
        logStep("CLEAR_DEVICES", "Cleared discovered devices list");
        
        if (forceEmulator) {
            logStep("MODE_EMULATOR", "Forcing emulator mode");
            isEmulatorMode.set(true);
            startEmulatorDiscovery();
        } else {
            logStep("MODE_FULL_DISCOVERY", "Starting full discovery, timeout=" + DISCOVERY_TIMEOUT_DEVICE_MS + "ms");
            isEmulatorMode.set(false);
            startFullDiscovery();
        }
    }
    
    public void stopDiscovery() {
        Log.i(TAG, "[FLIR] stopDiscovery()");
        cancelDiscoveryTimeout();
        isDiscovering.set(false);
        
        try {
            DiscoveryFactory.getInstance().stop();
        } catch (Throwable t) {
            Log.w(TAG, "[FLIR] stopDiscovery failed: " + t.getMessage());
        }
    }
    
    public void connectToDevice(String deviceId) {
        Log.i(TAG, "[FLIR] connectToDevice: " + deviceId);
        
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
        
        if (isConnected.get()) {
            disconnect();
        }
        
        connectToIdentity(target);
    }
    
    public void disconnect() {
        Log.i(TAG, "[FLIR] 🔌 disconnect()");
        
        stopStreaming();
        
        if (camera != null) {
            try {
                camera.disconnect();
            } catch (Throwable t) {
                Log.w(TAG, "[FLIR] Camera disconnect failed: " + t.getMessage());
            }
            camera = null;
        }
        
        isConnected.set(false);
        isConnecting.set(false); // Reset connecting flag on disconnect
        connectedDevice = null;
        
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onDeviceDisconnected();
            }
        });
    }
    
    public void setStreamType(String streamType) {
        Log.i(TAG, "[FLIR] setStreamType: " + streamType);
        currentStreamKind = streamType;
        
        if (isConnected.get() && camera != null) {
            stopStreaming();
            startStreaming();
        }
    }
    
    public void setPalette(String paletteName) {
        Log.i(TAG, "[FLIR] setPalette: " + paletteName);
        
        try {
            List<Palette> palettes = PaletteManager.getDefaultPalettes();
            for (Palette p : palettes) {
                if (p.name.equalsIgnoreCase(paletteName)) {
                    currentPalette = p;
                    Log.i(TAG, "[FLIR] Palette set to: " + p.name);
                    return;
                }
            }
            Log.w(TAG, "[FLIR] Palette not found: " + paletteName);
        } catch (Throwable t) {
            Log.w(TAG, "[FLIR] setPalette failed: " + t.getMessage());
        }
    }
    
    public void getTemperatureAt(int x, int y, Bitmap source) {
        Log.d(TAG, "[FLIR] getTemperatureAt(" + x + ", " + y + ")");
        double temp = getTemperatureAtPoint(x, y);
        if (!Double.isNaN(temp) && listener != null) {
            mainHandler.post(() -> listener.onTemperature(temp, x, y));
        }
    }
    
    /**
     * Get temperature at a specific point from the current thermal image
     */
    public double getTemperatureAtPoint(int x, int y) {
        if (currentThermalImage == null) {
            return Double.NaN;
        }
        
        try {
            // Clamp coordinates to image bounds
            int imgWidth = currentThermalImage.getWidth();
            int imgHeight = currentThermalImage.getHeight();
            int clampedX = Math.max(0, Math.min(x, imgWidth - 1));
            int clampedY = Math.max(0, Math.min(y, imgHeight - 1));
            
            ThermalValue value = currentThermalImage.getValueAt(new Point(clampedX, clampedY));
            if (value != null) {
                return value.asCelsius().value;
            }
        } catch (Throwable t) {
            Log.w(TAG, "[FLIR] getTemperatureAtPoint failed: " + t.getMessage());
        }
        return Double.NaN;
    }
    
    public Bitmap getLatestFrame() {
        return latestFrame;
    }
    
    public List<DeviceInfo> getDiscoveredDevices() {
        return new ArrayList<>(discoveredDevices);
    }
    
    public boolean isConnected() {
        return isConnected.get();
    }
    
    public boolean isStreaming() {
        return isStreaming.get();
    }
    
    public void destroy() {
        Log.i(TAG, "[FLIR] destroy()");
        stopDiscovery();
        disconnect();
        scheduler.shutdown();
    }
    
    // ==================== DISCOVERY ====================
    
    private void startFullDiscovery() {
        Log.i(TAG, "[FLIR] Starting full discovery (USB, NETWORK, EMULATOR, FLIR_ONE_WIRELESS)");
        
        if (!initializeSdk()) {
            Log.w(TAG, "[FLIR] SDK not available, falling back to emulator");
            startEmulatorDiscovery();
            return;
        }
        
        isDiscovering.set(true);
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onDiscoveryStarted();
            }
        });
        
        try {
            CommunicationInterface[] interfaces = {
                CommunicationInterface.USB,
                CommunicationInterface.NETWORK,
                CommunicationInterface.EMULATOR,
                CommunicationInterface.FLIR_ONE_WIRELESS
            };
            
            DiscoveryFactory.getInstance().scan(new DiscoveryEventListener() {
                @Override
                public void onCameraFound(DiscoveredCamera discoveredCamera) {
                    handleCameraFound(discoveredCamera);
                }
                
                @Override
                public void onDiscoveryError(CommunicationInterface iface, ErrorCode errorCode) {
                    Log.w(TAG, "[FLIR] Discovery error on " + iface + ": " + errorCode);
                }
            }, interfaces);
            
            // Set timeout for device discovery
            scheduleDiscoveryTimeout(DISCOVERY_TIMEOUT_DEVICE_MS);
            
        } catch (Throwable t) {
            Log.e(TAG, "[FLIR] startFullDiscovery failed: " + t.getMessage(), t);
            notifyError("Discovery failed: " + t.getMessage());
            startEmulatorDiscovery();
        }
    }
    
    private void startEmulatorDiscovery() {
        logStep("EMULATOR_DISCOVERY_START", "type=" + emulatorType);
        Log.i(TAG, "[FLIR] Starting emulator discovery (type=" + emulatorType + ")");
        
        if (!initializeSdk()) {
            notifyError("SDK initialization failed");
            return;
        }
        
        isDiscovering.set(true);
        isEmulatorMode.set(true);
        
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onDiscoveryStarted();
            }
        });
        
        try {
            DiscoveryFactory.getInstance().scan(new DiscoveryEventListener() {
                @Override
                public void onCameraFound(DiscoveredCamera discoveredCamera) {
                    handleCameraFound(discoveredCamera);
                }
                
                @Override
                public void onDiscoveryError(CommunicationInterface iface, ErrorCode errorCode) {
                    Log.w(TAG, "[FLIR] Emulator discovery error: " + errorCode);
                }
            }, CommunicationInterface.EMULATOR);
            
            // Short timeout for emulator
            scheduleDiscoveryTimeout(2000);
            
        } catch (Throwable t) {
            Log.e(TAG, "[FLIR] startEmulatorDiscovery failed: " + t.getMessage(), t);
            notifyError("Emulator discovery failed: " + t.getMessage());
        }
    }
    
    private void handleCameraFound(DiscoveredCamera discoveredCamera) {
        Identity identity = discoveredCamera.getIdentity();
        String deviceId = identity.deviceId;
        CommunicationInterface iface = identity.communicationInterface;
        boolean isEmulatorInterface = (iface == CommunicationInterface.EMULATOR);
        
        // Also check device ID/name for "emulate" or "emulator" keywords
        boolean isEmulatorByName = isEmulatorDevice(deviceId, null);
        boolean isEmulator = isEmulatorInterface || isEmulatorByName;
        
        // Create a friendly device name instead of using identity.toString()
        String deviceName;
        if (isEmulator) {
            // For emulators, use the device ID as name for clarity
            deviceName = deviceId; // e.g., "EMULATED FLIR ONE" or "EMULATED F1 EDGE PRO"
        } else if (iface == CommunicationInterface.USB) {
            deviceName = "FLIR ONE (USB)";
        } else if (iface == CommunicationInterface.NETWORK || iface == CommunicationInterface.FLIR_ONE_WIRELESS) {
            deviceName = "FLIR ONE Edge (WiFi)";
        } else {
            deviceName = "FLIR Camera";
        }
        
        // Enhanced logging for all discovered FLIR devices
        Log.i(TAG, "==================== FLIR DEVICE DISCOVERED ====================");
        Log.i(TAG, "[FLIR] Device ID: " + deviceId);
        Log.i(TAG, "[FLIR] Device Name: " + deviceName);
        Log.i(TAG, "[FLIR] Interface: " + iface);
        Log.i(TAG, "[FLIR] Is Emulator (interface): " + isEmulatorInterface);
        Log.i(TAG, "[FLIR] Is Emulator (by name/id): " + isEmulatorByName);
        Log.i(TAG, "[FLIR] Is Emulator (final): " + isEmulator);
        Log.i(TAG, "[FLIR] Total discovered so far: " + (discoveredDevices.size() + 1));
        Log.i(TAG, "================================================================");
        logStep("DEVICE_FOUND", "id=" + deviceId + ", name=" + deviceName + ", interface=" + iface + ", isEmulator=" + isEmulator);
        
        CommInterface commIface;
        switch (iface) {
            case USB:
                commIface = CommInterface.USB;
                break;
            case NETWORK:
            case FLIR_ONE_WIRELESS:
                commIface = CommInterface.NETWORK;
                break;
            default:
                commIface = CommInterface.EMULATOR;
        }
        
        DeviceInfo deviceInfo = new DeviceInfo(deviceId, deviceName, isEmulator, commIface, identity);
        
        // Avoid duplicates
        boolean exists = false;
        for (DeviceInfo d : discoveredDevices) {
            if (d.deviceId.equals(deviceId)) {
                exists = true;
                break;
            }
        }
        
        if (!exists) {
            discoveredDevices.add(deviceInfo);
            
            // Log all discovered devices so far
            Log.i(TAG, "[FLIR] 📋 All discovered devices (" + discoveredDevices.size() + " total):");
            int idx = 0;
            for (DeviceInfo d : discoveredDevices) {
                idx++;
                Log.i(TAG, "[FLIR]   " + idx + ". " + d.deviceName + " (id=" + d.deviceId + ", emu=" + d.isEmulator + ", iface=" + d.commInterface + ")");
            }
            
            mainHandler.post(() -> {
                if (listener != null) {
                    listener.onDeviceFound(deviceId, deviceName, isEmulator);
                    listener.onDeviceListUpdated(new ArrayList<>(discoveredDevices));
                }
            });
            
            // === AUTO-CONNECT LOGIC ===
            // Rule 0: NEVER connect to C++ emulator or any invalid emulator
            // Rule 1: During NORMAL discovery (isEmulatorMode=false): 
            //         - Auto-connect to REAL devices only
            //         - NEVER auto-connect to any emulator devices
            // Rule 2: During EXPLICIT emulator mode (isEmulatorMode=true via user EMU button):
            //         - Auto-connect ONLY to valid emulators: "EMULATED FLIR ONE" or "EMULATED F1 EDGE PRO"
            //         - NEVER connect to C++ emulator
            
            // BLOCK C++ emulator and any invalid emulator - NEVER connect to these
            if (isCppEmulator(deviceId) || (isEmulator && !isValidEmulator(deviceId))) {
                Log.w(TAG, "[FLIR] 🚫 BLOCKING invalid/C++ emulator: '" + deviceId + "' - only FLIR ONE and FLIR ONE Edge emulators are supported");
                return; // Exit early - do not process this device at all
            }
            
            // === AUTO-CONNECT COMPLETELY DISABLED ===
            // ALL devices (real and emulator) must be manually selected from the Settings modal.
            // Discovery only populates the device list, no automatic connections ever.
            // User must go to Settings > FLIR > Device Selection to choose and connect.
            
            if (isEmulator) {
                Log.i(TAG, "[FLIR] 🎮 Emulator discovered (NO auto-connect): deviceId='" + deviceId + "' - user must select from Settings");
            } else {
                Log.i(TAG, "[FLIR] 📱 Real device discovered (NO auto-connect): deviceId='" + deviceId + "' - user must select from Settings");
            }
            // NO connectToIdentity() call - user must manually select from Settings
        }
    }
    
    private void scheduleDiscoveryTimeout(long timeoutMs) {
        cancelDiscoveryTimeout();
        
        discoveryTimeoutFuture = scheduler.schedule(() -> {
            Log.i(TAG, "[FLIR] Discovery timeout after " + timeoutMs + "ms");
            
            // Count real (non-emulator) devices
            int realDeviceCount = countRealDevices();
            int totalDevices = discoveredDevices.size();
            
            Log.i(TAG, "[FLIR] 📊 Discovery stats: total=" + totalDevices + ", realDevices=" + realDeviceCount + ", emulators=" + (totalDevices - realDeviceCount));
            logStep("DISCOVERY_TIMEOUT", "timeout=" + timeoutMs + "ms, totalDevices=" + totalDevices + ", realDevices=" + realDeviceCount);
            
            isDiscovering.set(false);
            stopDiscovery();
            
            mainHandler.post(() -> {
                if (listener != null) {
                    listener.onDiscoveryTimeout();
                }
            });
            
            // If NO REAL devices found (only emulators or empty) and NOT in emulator mode, notify RN to enable EMU button
            // This treats "only emulator devices found" as "no device found"
            // Do NOT auto-start emulator - let user enable it manually
            if (realDeviceCount == 0 && !isEmulatorMode.get()) {
                Log.i(TAG, "[FLIR] ⚠️ No REAL FLIR device found (emulators don't count) - notifying RN to enable EMU button");
                logStep("NO_DEVICE_FOUND", "realDevices=0, notifying RN to show EMU option");
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onNoDeviceFound();
                    }
                });
            }
        }, timeoutMs, TimeUnit.MILLISECONDS);
    }
    
    private void cancelDiscoveryTimeout() {
        if (discoveryTimeoutFuture != null && !discoveryTimeoutFuture.isDone()) {
            discoveryTimeoutFuture.cancel(false);
            discoveryTimeoutFuture = null;
        }
    }
    
    // ==================== CONNECTION ====================
    
    /**
     * Find a device by its ID from the discovered devices list.
     * @param deviceId The device ID to search for
     * @return The DeviceInfo if found, null otherwise
     */
    public DeviceInfo findDeviceById(String deviceId) {
        if (deviceId == null) return null;
        for (DeviceInfo d : discoveredDevices) {
            if (deviceId.equals(d.deviceId)) {
                return d;
            }
        }
        return null;
    }
    
    /**
     * Connect to a specific device by its ID.
     * @param deviceId The device ID to connect to
     * @return true if connection was initiated, false if device not found
     */
    public boolean connectToDeviceById(String deviceId) {
        DeviceInfo device = findDeviceById(deviceId);
        if (device == null) {
            Log.e(TAG, "[FLIR] ❌ Cannot connect: Device not found with ID: " + deviceId);
            return false;
        }
        Log.i(TAG, "[FLIR] 🎯 Connecting to device by ID: " + deviceId + " (name=" + device.deviceName + ")");
        connectToIdentity(device);
        return true;
    }
    
    private void connectToIdentity(DeviceInfo device) {
        // Prevent duplicate connection attempts
        if (isConnecting.get()) {
            Log.w(TAG, "[FLIR] ⚠️ Connection already in progress, ignoring duplicate request for: " + device.deviceName);
            return;
        }
        if (isConnected.get()) {
            Log.w(TAG, "[FLIR] ⚠️ Already connected to a device, ignoring request for: " + device.deviceName);
            return;
        }
        
        isConnecting.set(true);
        logStep("CONNECT_START", "device=" + device.deviceName);
        Log.i(TAG, "[FLIR] 🔌 Connecting to: " + device.deviceName);
        
        scheduler.execute(() -> {
            try {
                camera = new Camera();
                
                // Connect using Identity, error callback, and ConnectParameters
                // Note: connect is blocking, so we're on a background thread
                camera.connect(
                    device.identity,
                    new ConnectionStatusListener() {
                        @Override
                        public void onDisconnected(ErrorCode errorCode) {
                            Log.i(TAG, "[FLIR] Disconnected: " + errorCode);
                            logStep("DISCONNECTED", "reason=" + errorCode);
                            
                            isConnected.set(false);
                            connectedDevice = null;
                            
                            mainHandler.post(() -> {
                                if (listener != null) {
                                    listener.onDeviceDisconnected();
                                }
                            });
                        }
                    },
                    new ConnectParameters()
                );
                
                // If we get here, connection succeeded
                Log.i(TAG, "[FLIR] ✅ Connected to: " + device.deviceName);
                logStep("CONNECTED", "device=" + device.deviceName);
                
                isConnected.set(true);
                isConnecting.set(false); // Connection complete
                connectedDevice = device;
                
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onDeviceConnected(device.deviceId, device.deviceName, device.isEmulator);
                    }
                });
                
                // Start streaming automatically
                startStreaming();
                
            } catch (Throwable t) {
                Log.e(TAG, "[FLIR] ❌ Connect error: " + t.getMessage(), t);
                isConnected.set(false);
                isConnecting.set(false); // Connection attempt finished
                camera = null;
                notifyError("Connect error: " + t.getMessage());
            }
        });
    }
    
    // ==================== STREAMING ====================
    
    private void startStreaming() {
        if (camera == null || !isConnected.get()) {
            Log.w(TAG, "[FLIR] Cannot start streaming - not connected");
            return;
        }
        
        logStep("STREAM_START", "streamType=" + currentStreamKind);
        Log.i(TAG, "[FLIR] Starting streaming...");
        
        scheduler.execute(() -> {
            try {
                // Get available streams
                List<Stream> streams = camera.getStreams();
                if (streams == null || streams.isEmpty()) {
                    Log.e(TAG, "[FLIR] No streams available");
                    notifyError("No streams available");
                    return;
                }
                
                Log.i(TAG, "[FLIR] Available streams: " + streams.size());
                
                // Select thermal stream (prefer thermal, fallback to first)
                Stream thermalStream = null;
                for (Stream s : streams) {
                    if (s.isThermal()) {
                        thermalStream = s;
                        break;
                    }
                }
                currentStream = thermalStream != null ? thermalStream : streams.get(0);
                
                // Create ThermalStreamer for rendering
                thermalStreamer = new ThermalStreamer(currentStream);
                
                // Set default palette if available
                if (currentPalette == null) {
                    try {
                        List<Palette> palettes = PaletteManager.getDefaultPalettes();
                        for (Palette p : palettes) {
                            if (p.name.toLowerCase().contains("iron")) {
                                currentPalette = p;
                                break;
                            }
                        }
                    } catch (Throwable t) {
                        Log.w(TAG, "[FLIR] Failed to get default palette: " + t.getMessage());
                    }
                }
                
                // Start the stream with OnReceived and OnRemoteError callbacks
                currentStream.start(
                    new OnReceived<Void>() {
                        @Override
                        public void onReceived(Void result) {
                            // Process received frame on background thread
                            scheduler.execute(() -> refreshThermalFrame());
                        }
                    },
                    new OnRemoteError() {
                        @Override
                        public void onRemoteError(ErrorCode errorCode) {
                            Log.e(TAG, "[FLIR] Stream error: " + errorCode);
                            notifyError("Stream error: " + errorCode);
                        }
                    }
                );
                
                isStreaming.set(true);
                Log.i(TAG, "[FLIR] Stream started");
                logStep("STREAM_STARTED", "stream=" + currentStream);
                
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onStreamStarted("thermal");
                    }
                });
                
            } catch (Throwable t) {
                Log.e(TAG, "[FLIR] Start stream error: " + t.getMessage(), t);
                notifyError("Stream error: " + t.getMessage());
            }
        });
    }
    
    private void stopStreaming() {
        if (currentStream != null) {
            try {
                currentStream.stop();
            } catch (Throwable t) {
                Log.w(TAG, "[FLIR] Stop stream error: " + t.getMessage());
            }
            currentStream = null;
        }
        thermalStreamer = null;
        isStreaming.set(false);
    }
    
    /**
     * Refresh thermal frame using ThermalStreamer pattern.
     * Called when a new frame is received.
     */
    private synchronized void refreshThermalFrame() {
        if (thermalStreamer == null) {
            return;
        }
        
        try {
            // Update streamer to get latest frame
            thermalStreamer.update();
            
            // Get the image buffer from streamer
            ImageBuffer imageBuffer = thermalStreamer.getImage();
            if (imageBuffer == null) {
                return;
            }
            
            // Access thermal image safely for temperature queries and palette
            thermalStreamer.withThermalImage(thermalImage -> {
                // Store for temperature queries
                currentThermalImage = thermalImage;
                
                // Apply palette if set
                if (currentPalette != null) {
                    thermalImage.setPalette(currentPalette);
                }
            });
            
            // Convert to Android Bitmap
            Bitmap bitmap = BitmapAndroid.createBitmap(imageBuffer).getBitMap();
            
            if (bitmap != null) {
                latestFrame = bitmap;
                
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onFrame(bitmap);
                    }
                });
            }
            
        } catch (Throwable t) {
            Log.w(TAG, "[FLIR] refreshThermalFrame error: " + t.getMessage());
        }
    }
    
    // ==================== PUBLIC STOP ====================
    
    /**
     * Stop the manager - disconnect and cleanup all resources.
     */
    public void stop() {
        Log.i(TAG, "[FLIR] Stopping FlirSdkManager");
        
        // Stop streaming
        stopStreaming();
        
        // Disconnect camera
        disconnect();
        
        // Stop discovery
        stopDiscovery();
        
        // Clear state
        discoveredDevices.clear();
        currentThermalImage = null;
        latestFrame = null;
        
        Log.i(TAG, "[FLIR] FlirSdkManager stopped");
    }
    
    // ==================== HELPERS ====================
    
    private void notifyError(String error) {
        Log.e(TAG, "[FLIR] Error: " + error);
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onError(error);
            }
        });
    }
}
