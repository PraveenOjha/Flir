package flir.android;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import com.flir.thermalsdk.ErrorCode;
import com.flir.thermalsdk.androidsdk.ThermalSdkAndroid;
import com.flir.thermalsdk.androidsdk.image.BitmapAndroid;
import com.flir.thermalsdk.image.ImageBuffer;
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
import com.flir.thermalsdk.live.streaming.Stream;
import com.flir.thermalsdk.live.streaming.ThermalStreamer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Simplified FLIR SDK Manager - handles discovery, connection, and streaming
 * No filtering - returns all discovered devices (USB, Network, Emulator)
 */
public class FlirSdkManager {
    private static final String TAG = "FlirSdkManager";

    // Singleton instance
    private static FlirSdkManager instance;

    // Core components
    private final Context context;
    // Use bounded thread pool to prevent thread explosion during rapid frame
    // processing
    private final Executor executor = Executors.newFixedThreadPool(2);
    // Single-threaded executor for frame processing to ensure ordered processing
    private final Executor frameExecutor = Executors.newSingleThreadExecutor();
    // Battery poller scheduler - polls battery level & charging state periodically
    // if supported
    private final java.util.concurrent.ScheduledExecutorService batteryPoller = java.util.concurrent.Executors
            .newSingleThreadScheduledExecutor();
    private volatile int lastPolledBatteryLevel = -1;
    private volatile boolean lastPolledCharging = false;
    // Frame processing guard - skip frames if still processing previous one
    private volatile boolean isProcessingFrame = false;
    private long lastFrameProcessedMs = 0;
    private static final long MIN_FRAME_INTERVAL_MS = 50; // Max ~20 FPS frame processing

    // State
    private boolean isInitialized = false;
    private boolean isScanning = false;
    private Camera camera;
    private ThermalStreamer streamer;
    private Stream activeStream;
    private final List<Identity> discoveredDevices = Collections.synchronizedList(new ArrayList<>());
    // When true, prefer getting SDK-provided rotated frames instead of rotating
    // ourselves
    private volatile boolean preferSdkRotation = false;

    // Listener
    private Listener listener;

    /**
     * Listener interface for SDK events
     */
    public interface Listener {
        void onDeviceFound(Identity identity);

        void onDeviceListUpdated(List<Identity> devices);

        void onConnected(Identity identity);

        void onDisconnected();

        void onFrame(Bitmap bitmap);

        void onError(String message);

        void onBatteryUpdated(int level, boolean isCharging);
    }

    // Private constructor for singleton
    private FlirSdkManager(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Get singleton instance
     */
    public static synchronized FlirSdkManager getInstance(Context context) {
        if (instance == null) {
            instance = new FlirSdkManager(context);
        }
        return instance;
    }

    /**
     * Set listener for SDK events
     */
    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setPreferSdkRotation(boolean prefer) {
        this.preferSdkRotation = prefer;
        // Try to ask SDK streamer to provide rotated images if possible
        if (streamer != null) {
            try {
                // Try common method names via reflection to avoid hard dependency on exact API
                // signature
                Object obj = streamer;
                java.lang.reflect.Method m = null;
                try {
                    m = obj.getClass().getMethod("setImageRotation", int.class);
                } catch (Throwable ignored) {
                }
                if (m == null) {
                    try {
                        m = obj.getClass().getMethod("setRotation", int.class);
                    } catch (Throwable ignored) {
                    }
                }
                if (m != null) {
                    // If caller asked SDK to rotate, choose 0 = 'auto' or prefer flag; here we
                    // request SDK to respect device orientation
                    int degrees = prefer ? 0 : 0; // SDK-specific - for now, 0 requests orientation-respected frames if
                                                  // method interprets so
                    m.invoke(obj, degrees);
                    Log.d(TAG, "setPreferSdkRotation: requested SDK rotation via reflection");
                } else {
                    Log.w(TAG, "setPreferSdkRotation: SDK does not expose rotation API (reflection check)");
                }
            } catch (Throwable t) {
                Log.w(TAG, "setPreferSdkRotation failed (reflection)", t);
            }
        }
    }

    public boolean isPreferSdkRotation() {
        return preferSdkRotation;
    }

    /**
     * Initialize the FLIR Thermal SDK
     */
    public void initialize() {
        if (isInitialized) {
            Log.d(TAG, "Already initialized");
            return;
        }

        try {
            // Explicitly load native library to ensure it's available and initialized
            // This can help resolve issues where the automatic loading fails or happens out
            // of order
            try {
                System.loadLibrary("atlas_native");
                Log.d(TAG, "Manually loaded atlas_native library");
            } catch (Throwable t) {
                Log.w(TAG, "Manual load of atlas_native failed: " + t.getMessage());
            }

            ThermalSdkAndroid.init(context);
            isInitialized = true;
            Log.d(TAG, "[Flir-LOAD] SDK initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "[Flir-ERROR] Failed to initialize SDK", e);
            notifyError("SDK initialization failed: " + e.getMessage());
        }
    }

    /**
     * Check if SDK is initialized
     */
    public boolean isInitialized() {
        return isInitialized;
    }

    /**
     * Start scanning for all device types (USB, Network, Emulator)
     * Returns ALL devices - no filtering
     */
    public void scan() {
        if (!isInitialized) {
            Log.e(TAG, "SDK not initialized");
            notifyError("SDK not initialized");
            return;
        }

        if (isScanning) {
            Log.d(TAG, "Already scanning");
            return;
        }

        isScanning = true;
        discoveredDevices.clear();

        Log.d(TAG, "[Flir-DISCOVERY] Starting discovery for EMULATOR, NETWORK, USB...");

        try {
            DiscoveryFactory.getInstance().scan(
                    discoveryListener,
                    CommunicationInterface.EMULATOR,
                    CommunicationInterface.NETWORK,
                    CommunicationInterface.USB);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start scan", e);
            isScanning = false;
            notifyError("Scan failed: " + e.getMessage());
        }
    }

    /**
     * Stop scanning for devices
     */
    public void stop() {
        if (!isScanning) {
            return;
        }

        try {
            DiscoveryFactory.getInstance().stop(
                    CommunicationInterface.EMULATOR,
                    CommunicationInterface.NETWORK,
                    CommunicationInterface.USB);
        } catch (Exception e) {
            Log.e(TAG, "Failed to stop scan", e);
        }

        isScanning = false;
        Log.d(TAG, "[Flir-DISCOVERY] Discovery stopped");
    }

    /**
     * Get list of discovered devices
     */
    public List<Identity> getDiscoveredDevices() {
        return new ArrayList<>(discoveredDevices);
    }

    /**
     * Connect to a device
     */
    public void connect(Identity identity) {
        if (identity == null) {
            notifyError("Invalid identity");
            return;
        }

        // Disconnect if already connected
        if (camera != null) {
            disconnect();
        }

        Log.d(TAG, "Connecting to: " + identity.deviceId);

        // Run connection on background thread since it's blocking
        executor.execute(() -> {
            try {
                camera = new Camera();
                camera.connect(identity, connectionStatusListener, new ConnectParameters());

                Log.d(TAG, "[Flir-CONNECTION] Connected to camera: " + identity.deviceId);

                if (listener != null) {
                    listener.onConnected(identity);
                }
                // Start battery poller for continuous updates
                startBatteryPoller();
            } catch (Exception e) {
                Log.e(TAG, "Connection failed", e);
                camera = null;
                notifyError("Connection failed: " + e.getMessage());
            }
        });
    }

    /**
     * Disconnect from current device
     */
    public void disconnect() {
        stopStream();

        if (camera != null) {
            try {
                camera.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Error disconnecting", e);
            }
            camera = null;
        }
        // stop battery poller
        stopBatteryPoller();

        if (listener != null) {
            listener.onDisconnected();
        }

        Log.d(TAG, "[Flir-DISCONNECT] Disconnected");
    }

    /**
     * Check if connected
     */
    public boolean isConnected() {
        return camera != null;
    }

    /**
     * Start streaming from connected device
     */
    public void startStream() {
        if (camera == null) {
            notifyError("Not connected");
            return;
        }

        // CRITICAL FIX: Prevent starting stream if previous stream is still active
        // This prevents race conditions and resource conflicts
        if (streamer != null || activeStream != null) {
            Log.w(TAG, "[Flir-STREAMING] Stream already active, stopping first");
            stopStream();
            
            // Wait for cleanup to complete
            try {
                Thread.sleep(200);
            } catch (InterruptedException ignored) {
            }
        }

        executor.execute(() -> {
            try {
                // Get available streams
                List<Stream> streams = camera.getStreams();
                if (streams == null || streams.isEmpty()) {
                    notifyError("No streams available");
                    return;
                }

                // Find thermal stream
                Stream thermalStream = null;
                for (Stream stream : streams) {
                    if (stream.isThermal()) {
                        thermalStream = stream;
                        break;
                    }
                }

                if (thermalStream == null) {
                    thermalStream = streams.get(0);
                }

                activeStream = thermalStream;
                
                // CRITICAL FIX: Validate stream before creating ThermalStreamer
                // The FLIR SDK native library can crash if stream is in invalid state
                if (!thermalStream.isAvailable()) {
                    notifyError("Thermal stream not available. Please reconnect device.");
                    return;
                }
                
                // CRITICAL FIX: Wrap ThermalStreamer creation in try-catch
                // While native crashes usually bypass Java exception handling,
                // we can catch:
                // 1. JNI errors (UnsatisfiedLinkError, Error subclasses)
                // 2. SDK errors before they reach native code
                // 3. Resource initialization failures
                // This won't prevent SIGSEGV/SIGABRT but reduces crash frequency
                try {
                    // Small delay to ensure stream and resources are fully initialized
                    // This prevents race conditions in the native filter chain setup
                    Thread.sleep(150);
                    
                    streamer = new ThermalStreamer(thermalStream);
                    Log.d(TAG, "[Flir-STREAMING] ThermalStreamer created successfully");
                } catch (UnsatisfiedLinkError e) {
                    // JNI library loading error
                    Log.e(TAG, "[Flir-STREAMING] JNI library error creating ThermalStreamer", e);
                    notifyError("FLIR_NATIVE_ERROR", "Failed to load native library. Please restart app.");
                    return;
                } catch (Exception e) {
                    // Java exception during initialization
                    Log.e(TAG, "[Flir-STREAMING] Failed to create ThermalStreamer", e);
                    notifyError("FLIR_INIT_ERROR", "Failed to initialize thermal camera: " + e.getMessage());
                    return;
                } catch (Error e) {
                    // Catch native errors/crashes from FLIR SDK
                    // Note: True SIGSEGV crashes will still kill the process,
                    // but some JNI errors can be caught here
                    Log.e(TAG, "[Flir-STREAMING] Native error creating ThermalStreamer", e);
                    notifyError("FLIR_NATIVE_ERROR", "Native error from FLIR device. Please reconnect and retry.");
                    return;
                }

                // Start receiving frames using OnReceived and OnRemoteError
                thermalStream.start(
                        (OnReceived<Void>) v -> {
                            // FRAME DROP GUARD: Skip frame if still processing previous one
                            // This prevents thread buildup and ensures smooth frame flow
                            long now = System.currentTimeMillis();
                            if (isProcessingFrame || (now - lastFrameProcessedMs < MIN_FRAME_INTERVAL_MS)) {
                                // Drop frame - processing is behind or too soon since last frame
                                return;
                            }

                            // Mark processing start before queuing task
                            isProcessingFrame = true;

                            // Use single-threaded frameExecutor to ensure ordered frame processing
                            frameExecutor.execute(() -> {
                                try {
                                    if (streamer != null) {
                                        streamer.update();

                                        // Get ImageBuffer and convert to Bitmap
                                        ImageBuffer imageBuffer = streamer.getImage();
                                        if (imageBuffer != null && listener != null) {
                                            BitmapAndroid bitmapAndroid = BitmapAndroid.createBitmap(imageBuffer);
                                            Bitmap bitmap = bitmapAndroid.getBitMap();
                                            if (bitmap != null) {
                                                listener.onFrame(bitmap);
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "Error processing frame", e);
                                } finally {
                                    // Reset frame processing guard to allow next frame
                                    lastFrameProcessedMs = System.currentTimeMillis();
                                    isProcessingFrame = false;
                                }
                            });
                        },
                        error -> {
                            Log.e(TAG, "Stream error: " + error);
                            notifyError("Stream error: " + error);
                        });

                Log.d(TAG, "[Flir-STREAMING] Streaming started");

            } catch (Exception e) {
                Log.e(TAG, "[Flir-ERROR] Failed to start stream", e);
                notifyError("Stream failed: " + e.getMessage());
            }
        });
    }

    /**
     * Stop streaming
     */
    public void stopStream() {
        if (activeStream != null) {
            try {
                activeStream.stop();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping stream", e);
            }
            activeStream = null;
        }

        // CRITICAL FIX: Properly cleanup streamer to prevent resource leaks
        if (streamer != null) {
            try {
                // Give streamer time to cleanup before nulling
                Thread.sleep(50);
            } catch (InterruptedException ignored) {
            }
            streamer = null;
        }

        // Reset frame processing state
        isProcessingFrame = false;
        lastFrameProcessedMs = 0;

        Log.d(TAG, "[Flir-STREAMING] Streaming stopped");
    }

    /**
     * Get temperature at a specific point in the image
     * Queries the SDK directly - simple and no locks needed
     * 
     * @param x X coordinate (0 to image width-1)
     * @param y Y coordinate (0 to image height-1)
     * @return Temperature in Celsius, or Double.NaN if not available
     */
    public double getTemperatureAt(int x, int y) {
        if (streamer == null) {
            return Double.NaN;
        }

        final double[] result = { Double.NaN };
        try {
            streamer.withThermalImage(thermalImage -> {
                try {
                    int imgWidth = thermalImage.getWidth();
                    int imgHeight = thermalImage.getHeight();

                    int clampedX = Math.max(0, Math.min(imgWidth - 1, x));
                    int clampedY = Math.max(0, Math.min(imgHeight - 1, y));

                    ThermalValue value = thermalImage.getValueAt(new Point(clampedX, clampedY));
                    if (value != null) {
                        result[0] = value.asCelsius().value;
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Error querying temperature", e);
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "Temperature query failed", e);
        }

        return result[0];
    }

    /**
     * Get temperature at normalized coordinates (0.0 to 1.0)
     * 
     * @param normalizedX X coordinate (0.0 to 1.0)
     * @param normalizedY Y coordinate (0.0 to 1.0)
     * @return Temperature in Celsius, or Double.NaN if not available
     */
    public double getTemperatureAtNormalized(double normalizedX, double normalizedY) {
        if (streamer == null) {
            return Double.NaN;
        }

        final double[] result = { Double.NaN };
        try {
            streamer.withThermalImage(thermalImage -> {
                try {
                    int width = thermalImage.getWidth();
                    int height = thermalImage.getHeight();

                    int x = (int) (normalizedX * (width - 1));
                    int y = (int) (normalizedY * (height - 1));

                    x = Math.max(0, Math.min(width - 1, x));
                    y = Math.max(0, Math.min(height - 1, y));

                    ThermalValue value = thermalImage.getValueAt(new Point(x, y));
                    if (value != null) {
                        result[0] = value.asCelsius().value;
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Error querying temperature (normalized)", e);
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "Temperature query failed", e);
        }

        return result[0];
    }

    /**
     * Set palette for thermal image rendering
     */
    public void setPalette(String paletteName) {
        if (streamer == null) {
            Log.w(TAG, "No active streamer");
            return;
        }

        executor.execute(() -> {
            try {
                Palette palette = findPalette(paletteName);
                if (palette != null) {
                    streamer.withThermalImage(thermalImage -> {
                        thermalImage.setPalette(palette);
                    });
                    Log.d(TAG, "[Flir-STREAMING] Palette set to: " + paletteName);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error setting palette", e);
            }
        });

    }

    /**
     * Get list of available palettes
     */
    public List<String> getAvailablePalettes() {
        List<String> names = new ArrayList<>();
        try {
            List<Palette> palettes = PaletteManager.getDefaultPalettes();
            for (Palette p : palettes) {
                names.add(p.name);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting palettes", e);
        }
        return names;
    }

    /**
     * Best-effort: Fetch battery level from connected camera if SDK exposes battery
     * APIs
     * Returns -1 if unavailable
     */
    public int getBatteryLevel() {
        if (camera == null)
            return -1;
        try {
            // Common SDK methods to try
            try {
                java.lang.reflect.Method m = camera.getClass().getMethod("getBatteryLevel");
                Object r = m.invoke(camera);
                if (r instanceof Number)
                    return ((Number) r).intValue();
            } catch (Throwable ignored) {
            }

            try {
                java.lang.reflect.Method m = camera.getClass().getMethod("getBattery");
                Object batt = m.invoke(camera);
                if (batt != null) {
                    try {
                        java.lang.reflect.Method levelMethod = batt.getClass().getMethod("getLevel");
                        Object lv = levelMethod.invoke(batt);
                        if (lv instanceof Number)
                            return ((Number) lv).intValue();
                    } catch (Throwable ignored) {
                    }
                }
            } catch (Throwable ignored) {
            }
        } catch (Throwable t) {
            Log.w(TAG, "Error querying battery level", t);
        }
        return -1;
    }

    /**
     * Best-effort: Check if the camera is charging
     * Returns false if unknown
     */
    public boolean isBatteryCharging() {
        if (camera == null)
            return false;
        try {
            try {
                java.lang.reflect.Method m = camera.getClass().getMethod("isCharging");
                Object r = m.invoke(camera);
                if (r instanceof Boolean)
                    return (Boolean) r;
            } catch (Throwable ignored) {
            }

            try {
                java.lang.reflect.Method m = camera.getClass().getMethod("getBattery");
                Object batt = m.invoke(camera);
                if (batt != null) {
                    try {
                        java.lang.reflect.Method isCh = batt.getClass().getMethod("isCharging");
                        Object cv = isCh.invoke(batt);
                        if (cv instanceof Boolean)
                            return (Boolean) cv;
                    } catch (Throwable ignored) {
                    }
                }
            } catch (Throwable ignored) {
            }
        } catch (Throwable t) {
            Log.w(TAG, "Error querying battery charging state", t);
        }
        return false;
    }

    // Find palette by name
    private Palette findPalette(String name) {
        try {
            List<Palette> palettes = PaletteManager.getDefaultPalettes();
            for (Palette p : palettes) {
                if (p.name.equalsIgnoreCase(name)) {
                    return p;
                }
            }
            // Return first if not found
            if (!palettes.isEmpty()) {
                return palettes.get(0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error finding palette", e);
        }
        return null;
    }

    // Discovery listener - no filtering, returns all devices
    private final DiscoveryEventListener discoveryListener = new DiscoveryEventListener() {
        @Override
        public void onCameraFound(DiscoveredCamera discoveredCamera) {
            Identity identity = discoveredCamera.getIdentity();
            Log.d(TAG,
                    "[Flir-DISCOVERY] Device found: " + identity.deviceId + " type=" + identity.communicationInterface);

            // Add to list if not already present
            synchronized (discoveredDevices) {
                boolean exists = false;
                for (Identity d : discoveredDevices) {
                    if (d.deviceId.equals(identity.deviceId)) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    discoveredDevices.add(identity);
                }
            }

            if (listener != null) {
                listener.onDeviceFound(identity);
                listener.onDeviceListUpdated(new ArrayList<>(discoveredDevices));
            }
        }

        @Override
        public void onCameraLost(Identity identity) {
            Log.d(TAG, "[Flir-DISCOVERY] Device lost: " + identity.deviceId);

            synchronized (discoveredDevices) {
                discoveredDevices.removeIf(d -> d.deviceId.equals(identity.deviceId));
            }

            if (listener != null) {
                listener.onDeviceListUpdated(new ArrayList<>(discoveredDevices));
            }
        }

        @Override
        public void onDiscoveryError(CommunicationInterface iface, ErrorCode error) {
            Log.e(TAG, "[Flir-ERROR] Discovery error: " + iface + " - " + error);
            notifyError("Discovery error: " + error);
        }

        @Override
        public void onDiscoveryFinished(CommunicationInterface iface) {
            Log.d(TAG, "[Flir-DISCOVERY] Discovery finished for: " + iface);
        }
    };

    // Connection status listener
    private final ConnectionStatusListener connectionStatusListener = new ConnectionStatusListener() {
        @Override
        public void onDisconnected(ErrorCode error) {
            Log.d(TAG, "Disconnected: " + (error != null ? error : "clean"));
            camera = null;

            if (listener != null) {
                listener.onDisconnected();
            }
        }
    };

    // Helper to notify errors
    private void notifyError(String message) {
        if (listener != null) {
            listener.onError(message);
        }
    }
    
    /**
     * Notify error with error code for better handling
     */
    private void notifyError(String errorCode, String message) {
        Log.e(TAG, "[" + errorCode + "] " + message);
        if (listener != null) {
            // Send both code and message - listener can parse it
            listener.onError(errorCode + ": " + message);
        }
    }

    /**
     * Cleanup resources
     */
    public void destroy() {
        stop();
        disconnect();
        discoveredDevices.clear();
        listener = null;
        instance = null;
        Log.d(TAG, "Destroyed");
    }

    /**
     * Start a background poller to periodically check battery state and notify
     * listener
     */
    private void startBatteryPoller() {
        try {
            batteryPoller.scheduleAtFixedRate(() -> {
                if (camera == null)
                    return;
                try {
                    int level = getBatteryLevel();
                    boolean charging = isBatteryCharging();
                    if (level != lastPolledBatteryLevel || charging != lastPolledCharging) {
                        lastPolledBatteryLevel = level;
                        lastPolledCharging = charging;

                        Log.d(TAG, String.format("[Flir-BATTERY] Level: %d%%, Charging: %b", level, charging));

                        if (listener != null) {
                            try {
                                listener.onBatteryUpdated(level, charging);
                            } catch (Throwable t) {
                            }
                        }
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "Battery poller error", t);
                }
            }, 0, 5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Throwable t) {
            Log.w(TAG, "Failed to start battery poller", t);
        }
    }

    /**
     * Stop the battery poller.
     */
    private void stopBatteryPoller() {
        try {
            batteryPoller.shutdownNow();
        } catch (Throwable ignored) {
        }
    }
}
