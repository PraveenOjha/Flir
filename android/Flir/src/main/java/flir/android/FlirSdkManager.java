package flir.android;

import android.content.Context;
import android.content.SharedPreferences;
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
import com.flir.thermalsdk.live.AuthenticationResponse;
import com.flir.thermalsdk.live.Camera;
import com.flir.thermalsdk.live.CommunicationInterface;
import com.flir.thermalsdk.live.ConnectParameters;
import com.flir.thermalsdk.live.Identity;
import com.flir.thermalsdk.live.connectivity.ConnectionStatusListener;
import com.flir.thermalsdk.live.discovery.DiscoveredCamera;
import com.flir.thermalsdk.live.discovery.DiscoveryEventListener;
import com.flir.thermalsdk.live.discovery.DiscoveryFactory;
import com.flir.thermalsdk.androidsdk.live.connectivity.SdkWifiConnectionHelper;
import com.flir.thermalsdk.live.streaming.Stream;
import com.flir.thermalsdk.live.streaming.ThermalStreamer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Simplified FLIR SDK Manager - matches sample app pattern
 * Thread-safe: All lifecycle methods run on a single background executor to prevent native race conditions.
 */
public class FlirSdkManager {
    private static final String TAG = "FlirSdkManager";

    private static FlirSdkManager instance;
    private final Context context;
    private final Executor executor = Executors.newSingleThreadExecutor();

    // State
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);
    private boolean isScanning = false;
    private Camera camera;
    private ThermalStreamer streamer;
    private Stream activeStream;
    private final List<Identity> discoveredDevices = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, DiscoveredCamera> discoveredCameras = Collections.synchronizedMap(new HashMap<>());
    private volatile Bitmap latestBitmap;
    private volatile String currentPaletteName = "WhiteHot";
    private final AtomicBoolean isProcessingFrame = new AtomicBoolean(false);
    private boolean useHalfScale = false;
    private String pendingSnapshotPath = null;

    // Listener
    private Listener listener;

    public interface Listener {
        void onDeviceFound(Identity identity);

        void onDeviceListUpdated(List<Identity> devices);

        void onConnected(Identity identity);

        void onDisconnected();

        void onFrame(Bitmap bitmap);

        void onError(String message);
    }

    public interface SnapshotCallback {
        void onSnapshotSaved(String path);
        void onSnapshotError(String message);
    }

    private volatile SnapshotCallback snapshotCallback;

    private FlirSdkManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized FlirSdkManager getInstance(Context context) {
        if (instance == null) {
            instance = new FlirSdkManager(context);
        }
        return instance;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    // ==================== INITIALIZE ====================

    public void initialize() {
        if (isInitialized.compareAndSet(false, true)) {
            Log.d(TAG, "Initializing FLIR SDK (async)...");
            
            executor.execute(() -> {
                try {
                    ThermalSdkAndroid.init(context);
                    
                    // Small delay to ensure JNI linkage is stable
                    try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                    
                    Log.d(TAG, "FLIR SDK initialized successfully on background thread. Arch: " + System.getProperty("os.arch"));
                } catch (Throwable t) {
                    Log.e(TAG, "Failed to initialize FLIR SDK", t);
                    isInitialized.set(false);
                }
            });
        }
    }

    public boolean isInitialized() {
        return isInitialized.get();
    }

    // ==================== DISCOVERY ====================

    public void scan() {
        executor.execute(() -> {
            if (!isInitialized.get()) {
                notifyError("SDK not initialized");
                return;
            }
            if (isScanning) {
                Log.d(TAG, "Discovery already running, ensuring clean state...");
                try {
                    DiscoveryFactory.getInstance().stop();
                } catch (Throwable ignored) {}
                isScanning = false;
            }

            isScanning = true;
            discoveredDevices.clear();
            Log.d(TAG, "Starting discovery for all interfaces...");

            try {
                DiscoveryFactory.getInstance().scan(
                        discoveryListener,
                        CommunicationInterface.EMULATOR,
                        CommunicationInterface.USB,
                        CommunicationInterface.NETWORK,
                        CommunicationInterface.FLIR_ONE_WIRELESS);
            } catch (Exception e) {
                Log.e(TAG, "Scan failed", e);
                isScanning = false;
                notifyError("Scan failed: " + e.getMessage());
            }
        });
    }

    public void stopScan() {
        executor.execute(() -> {
            if (!isScanning) return;
            isScanning = false;
            stopScanInternal();
        });
    }

    private void stopScanInternal() {
        try {
            Log.d(TAG, "Stopping all discovery scanners...");
            // Use zero-arg stop() as seen in official samples to stop all scanners
            DiscoveryFactory.getInstance().stop();
            Log.d(TAG, "Discovery stopped successfully");
        } catch (Throwable t) {
            // This catches the notorious 'Receiver not registered' IllegalArgumentException
            // and any other JNI-bubbled exceptions during teardown.
            Log.w(TAG, "Stop scan suppressed (SDK internal race): " + t.getMessage());
        }
    }

    public List<Identity> getDiscoveredDevices() {
        return new ArrayList<>(discoveredDevices);
    }

    // ==================== CONNECTION ====================

    public void connect(Identity identity) {
        if (identity == null) {
            notifyError("Invalid identity");
            return;
        }

        // Run on background thread (matches sample app pattern)
        executor.execute(() -> {
            try {
                // Disconnect if already connected
                stopStreamInternal();
                if (camera != null) {
                    try {
                        camera.disconnect();
                    } catch (Exception e) {
                        Log.e(TAG, "Disconnect error", e);
                    }
                    camera = null;
                }

                // ── FLIR ONE WIRELESS (WiFi) Connection ──
                // Matches the official FlirOneWireless sample. We must connect to the
                // camera's WiFi Access Point before calling camera.connect().
                if (identity.communicationInterface == CommunicationInterface.FLIR_ONE_WIRELESS) {
                    DiscoveredCamera dc = getDiscoveredCamera(identity.deviceId);
                    if (dc != null && dc.getCameraDetails() != null) {
                        String ssid = dc.getCameraDetails().ssid;
                        Log.d(TAG, "Establishing WiFi connection to: " + ssid);
                        
                        if (!SdkWifiConnectionHelper.isConnectedToNetwork(context, ssid)) {
                            // This is a blocking-style wrapper for simplicity in the executor thread
                            final AtomicBoolean wifiDone = new AtomicBoolean(false);
                            final AtomicBoolean wifiSuccess = new AtomicBoolean(false);
                            
                            SdkWifiConnectionHelper.connectToWifiWithoutCode(context, dc.getCameraDetails(), status -> {
                                if (status.status == SdkWifiConnectionHelper.ConInfo.CONNECTED) {
                                    wifiSuccess.set(true);
                                    wifiDone.set(true);
                                } else if (status.status == SdkWifiConnectionHelper.ConInfo.ERROR) {
                                    wifiSuccess.set(false);
                                    wifiDone.set(true);
                                }
                            });
                            
                            // Wait for WiFi connection (max 15 seconds)
                            int waitLoops = 0;
                            while (!wifiDone.get() && waitLoops < 30) {
                                try { Thread.sleep(500); } catch (Exception ignored) {}
                                waitLoops++;
                            }
                            
                            if (!wifiSuccess.get()) {
                                notifyError("Failed to connect to camera WiFi: " + ssid);
                                return;
                            }
                        }
                    }
                }

                // ── NETWORK Authentication (Required for A/T-series, NOT Wireless) ──
                if (identity.communicationInterface == CommunicationInterface.NETWORK) {
                    Log.d(TAG, "Authenticating with network camera: " + identity.deviceId);
                    
                    if (isScanning) {
                        isScanning = false;
                        stopScanInternal();
                        try { Thread.sleep(500); } catch (Exception ignored) {}
                    }
                    
                    camera = new Camera();
                    String authName = "ThermalCameraFx";
                    AuthenticationResponse response;
                    int attempts = 0;
                    final int MAX_AUTH_ATTEMPTS = 3;
                    
                    do {
                        attempts++;
                        response = camera.authenticate(identity, authName, 41 * 1000);
                        if (response.authenticationStatus == AuthenticationResponse.AuthenticationStatus.APPROVED) {
                            break;
                        }
                    } while (response.authenticationStatus == AuthenticationResponse.AuthenticationStatus.PENDING && attempts < MAX_AUTH_ATTEMPTS);

                    if (response.authenticationStatus != AuthenticationResponse.AuthenticationStatus.APPROVED) {
                        notifyError("Authentication failed: " + response.authenticationStatus.name());
                        return;
                    }
                } else {
                    // For all other types, stop scan if still running
                    if (isScanning) {
                        isScanning = false;
                        stopScanInternal();
                        try { Thread.sleep(300); } catch (Exception ignored) {}
                    }
                    camera = new Camera();
                }

                Log.d(TAG, "Calling camera.connect() for " + identity.deviceId);
                camera.connect(identity, connectionStatusListener, new ConnectParameters());
                Log.d(TAG, "camera.connect() returned for " + identity.deviceId + ". isConnected=" + camera.isConnected());

                if (listener != null) {
                    listener.onConnected(identity);
                }

                // Auto-start stream after connection (matches sample app)
                startStreamInternal();

            } catch (Exception e) {
                Log.e(TAG, "Connection failed for " + identity.deviceId, e);
                camera = null;
                notifyError("Connection failed: " + e.getMessage());
            }
        });
    }

    public void disconnect() {
        executor.execute(() -> {
            if (isScanning) {
                isScanning = false;
                stopScanInternal();
            }
            stopStreamInternal();

            if (camera != null) {
                try {
                    camera.disconnect();
                } catch (Exception e) {
                    Log.e(TAG, "Disconnect error", e);
                }
                camera = null;
            }

            if (listener != null) {
                listener.onDisconnected();
            }
            Log.d(TAG, "Disconnected");
        });
    }

    public boolean isConnected() {
        return camera != null;
    }

    private DiscoveredCamera getDiscoveredCamera(String deviceId) {
        return discoveredCameras.get(deviceId);
    }

    // ==================== STREAMING ====================

    public void startStream() {
        executor.execute(this::startStreamInternal);
    }

    public void setUseHalfScale(boolean useHalfScale) {
        this.useHalfScale = useHalfScale;
        executor.execute(() -> {
            if (streamer != null) {
                // We'll apply this when the streamer is created or updated
            }
        });
    }

    private void startStreamInternal() {
        if (camera == null) {
            notifyError("Not connected");
            return;
        }

        try {
            // RETRY MECHANISM: For Network/Wireless cameras, the connection might take 
            // a few hundred milliseconds to stabilize at the native level even after 
            // camera.connect() returns. We retry for up to 10 seconds.
            int retries = 0;
            final int MAX_RETRIES = 50; // 50 * 200ms = 10 seconds
            while (!camera.isConnected() && retries < MAX_RETRIES) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ignored) {}
                retries++;
                if (retries % 5 == 0) {
                    Log.d(TAG, "Waiting for camera connection state to stabilize... (attempt " + retries + ")");
                }
            }

            if (!camera.isConnected()) {
                Log.w(TAG, "Camera still reports disconnected after timeout, but attempting to check streams as fallback...");
                List<Stream> fallbackStreams = camera.getStreams();
                if (fallbackStreams == null || fallbackStreams.isEmpty()) {
                    Log.e(TAG, "No streams available and camera is disconnected. Giving up.");
                    notifyError("Camera not connected and no streams found");
                    return;
                }
                Log.i(TAG, "Found " + fallbackStreams.size() + " streams despite disconnected status. Proceeding...");
            }

            Log.d(TAG, "Camera connected state confirmed after " + (retries * 200) + "ms");

            // RETRY MECHANISM for getStreams: Sometimes streams are not immediately available
            List<Stream> streams = null;
            retries = 0;
            while (retries < 10) { // 10 * 200ms = 2 seconds
                streams = camera.getStreams();
                if (streams != null && !streams.isEmpty()) {
                    break;
                }
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ignored) {}
                retries++;
            }

            if (streams == null || streams.isEmpty()) {
                notifyError("No streams available");
                return;
            }

            Log.d(TAG, "Streams found: " + streams.size() + " (after " + (retries * 200) + "ms)");

            // Find thermal stream or use first
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
            streamer = new ThermalStreamer(thermalStream);

            // Start stream with simple callback (matches sample app)
            thermalStream.start(
                    unused -> {
                        // CRITICAL: Frame processing must happen in a controlled way.
                        // For Android, we perform streamer.update() on the callback thread 
                        // to ensure the native frame reference remains valid.
                        if (isProcessingFrame.compareAndSet(false, true)) {
                            try {
                                if (streamer != null && activeStream != null) {
                                    streamer.update();
                                    
                                    final String paletteToApply = currentPaletteName;
                                    final String snapshotPath = pendingSnapshotPath;
                                    pendingSnapshotPath = null;

                                    streamer.withThermalImage(thermalImage -> {
                                        // 1. Apply Palette
                                        if (paletteToApply != null) {
                                            try {
                                                List<Palette> sdkPalettes = PaletteManager.getDefaultPalettes();
                                                
                                                if (paletteToApply.equalsIgnoreCase("Gray") || paletteToApply.equalsIgnoreCase("grayscale")) {
                                                    // User wants Gray - map to WhiteHot which is the SDK's standard grayscale
                                                    for (Palette p : sdkPalettes) {
                                                        if (p.name.equalsIgnoreCase("WhiteHot") || p.name.equalsIgnoreCase("White hot")) {
                                                            thermalImage.setPalette(p);
                                                            break;
                                                        }
                                                    }
                                                } else {
                                                    Palette palette = null;
                                                    for (Palette p : sdkPalettes) {
                                                        if (p.name.equalsIgnoreCase(paletteToApply)) {
                                                            palette = p;
                                                            break;
                                                        }
                                                    }
                                                    
                                                    if (palette != null) {
                                                        thermalImage.setPalette(palette);
                                                    } else if (paletteToApply.equalsIgnoreCase("Wheel")) {
                                                        // Fallback for Wheel if not found - some SDKs use different names
                                                        for (Palette p : sdkPalettes) {
                                                            if (p.name.contains("Wheel") || p.name.contains("ColorWheel") || p.name.contains("Rainbow")) {
                                                                thermalImage.setPalette(p);
                                                                break;
                                                            }
                                                        }
                                                    }
                                                }
                                            } catch (Throwable t) {
                                                Log.e(TAG, "Failed to apply palette: " + paletteToApply, t);
                                            }
                                        }

                                        // 2. Save Radiometric Snapshot if requested
                                        if (snapshotPath != null) {
                                            try {
                                                Log.i(TAG, "[SNAPSHOT] Attempting to save radiometric snapshot: " + snapshotPath);
                                                thermalImage.saveAs(snapshotPath);
                                                Log.i(TAG, "[SNAPSHOT] ✅ Success: Radiometric snapshot saved");
                                                if (snapshotCallback != null) {
                                                    snapshotCallback.onSnapshotSaved(snapshotPath);
                                                    snapshotCallback = null;
                                                }
                                            } catch (java.io.IOException e) {
                                                Log.e(TAG, "Failed to save radiometric snapshot", e);
                                                if (snapshotCallback != null) {
                                                    snapshotCallback.onSnapshotError(e.getMessage());
                                                    snapshotCallback = null;
                                                }
                                            }
                                        }

                                        // 3. Generate Bitmap for display
                                        // We use streamer.getImage() to get the rendered image with palette applied.
                                        try {
                                            Bitmap newBitmap = BitmapAndroid.createBitmap(streamer.getImage()).getBitMap();
                                            if (newBitmap != null) {
                                                Bitmap oldBitmap = latestBitmap;
                                                latestBitmap = newBitmap;
                                                if (listener != null) {
                                                    listener.onFrame(newBitmap);
                                                }
                                                // Recycle old bitmap to prevent memory leak
                                                if (oldBitmap != null && oldBitmap != newBitmap) {
                                                    oldBitmap.recycle();
                                                }
                                            }
                                        } catch (Exception e) {
                                            Log.e(TAG, "Bitmap creation failed", e);
                                        }
                                    });
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Frame processing error", e);
                            } finally {
                                isProcessingFrame.set(false);
                            }
                        }
                    },
                    error -> {
                        executor.execute(() -> {
                            Log.e(TAG, "Stream error: " + error);
                            notifyError("Stream error: " + error);
                        });
                    });

            Log.d(TAG, "Streaming started");

        } catch (Exception e) {
            Log.e(TAG, "Start stream failed", e);
            notifyError("Stream failed: " + e.getMessage());
        }
    }

    public void stopStream() {
        executor.execute(this::stopStreamInternal);
    }

    private void stopStreamInternal() {
        if (activeStream != null) {
            try {
                activeStream.stop();
            } catch (Exception e) {
                Log.e(TAG, "Stop stream error", e);
            }
            activeStream = null;
        }
        streamer = null;
        latestBitmap = null;
        Log.d(TAG, "Streaming stopped");
    }

    public Bitmap getLatestBitmap() {
        return latestBitmap;
    }

    // ==================== TEMPERATURE ====================

    public double getTemperatureAt(int x, int y) {
        // Run on the same thread to avoid concurrent access to 'streamer'
        final double[] result = { Double.NaN };
        // This query remains synchronous for the caller but synchronized with the stream updates
        synchronized (this) {
            if (streamer == null)
                return Double.NaN;

            try {
                streamer.withThermalImage(thermalImage -> {
                    try {
                        int w = thermalImage.getWidth();
                        int h = thermalImage.getHeight();
                        int cx = Math.max(0, Math.min(w - 1, x));
                        int cy = Math.max(0, Math.min(h - 1, y));
                        ThermalValue value = thermalImage.getValueAt(new Point(cx, cy));
                        if (value != null) {
                            result[0] = value.asCelsius().value;
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Temp query error", e);
                    }
                });
            } catch (Exception e) {
                Log.w(TAG, "Temp query failed", e);
            }
        }
        return result[0];
    }

    /**
     * Samples temperature using normalized coordinates (0.0 to 1.0)
     * This avoids clamping bugs when UI dimensions differ from sensor dimensions.
     */
    public double getTemperatureAtNormalized(double nx, double ny) {
        final double[] result = { Double.NaN };
        synchronized (this) {
            if (streamer == null) return Double.NaN;
            try {
                streamer.withThermalImage(thermalImage -> {
                    try {
                        int w = thermalImage.getWidth();
                        int h = thermalImage.getHeight();
                        // Map normalized 0..1 to sensor pixels 0..w-1
                        int cx = (int) Math.max(0, Math.min(w - 1, nx * w));
                        int cy = (int) Math.max(0, Math.min(h - 1, ny * h));
                        ThermalValue value = thermalImage.getValueAt(new Point(cx, cy));
                        if (value != null) {
                            result[0] = value.asCelsius().value;
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Normalized temp query error", e);
                    }
                });
            } catch (Exception e) {
                Log.w(TAG, "Normalized temp query failed", e);
            }
        }
        return result[0];
    }

    // ==================== LISTENERS ====================
    
    public void setPalette(String paletteName) {
        this.currentPaletteName = paletteName;
        Log.d(TAG, "Requested palette: " + paletteName);
    }

    public void captureRadiometricSnapshot(String path, SnapshotCallback callback) {
        this.pendingSnapshotPath = path;
        this.snapshotCallback = callback;
        Log.d(TAG, "Pending radiometric snapshot: " + path);
    }

    private final DiscoveryEventListener discoveryListener = new DiscoveryEventListener() {
        @Override
        public void onCameraFound(DiscoveredCamera discoveredCamera) {
            Identity identity = discoveredCamera.getIdentity();
            Log.d(TAG, "Device found: " + identity.deviceId);

            discoveredCameras.put(identity.deviceId, discoveredCamera);

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
            Log.d(TAG, "Device lost: " + identity.deviceId);
            discoveredCameras.remove(identity.deviceId);
            synchronized (discoveredDevices) {
                discoveredDevices.removeIf(d -> d.deviceId.equals(identity.deviceId));
            }
            if (listener != null) {
                listener.onDeviceListUpdated(new ArrayList<>(discoveredDevices));
            }
        }

        @Override
        public void onDiscoveryError(CommunicationInterface iface, ErrorCode error) {
            Log.e(TAG, "Discovery error: " + iface + " - " + error);
            notifyError("Discovery error: " + error);
        }

        @Override
        public void onDiscoveryFinished(CommunicationInterface iface) {
            Log.d(TAG, "Discovery finished: " + iface);
        }
    };

    private final ConnectionStatusListener connectionStatusListener = errorCode -> {
        executor.execute(() -> {
            Log.d(TAG, "Disconnected callback: " + (errorCode != null ? errorCode : "clean"));
            camera = null;
            if (listener != null) {
                listener.onDisconnected();
            }
        });
    };

    private void notifyError(String message) {
        if (listener != null) {
            listener.onError(message);
        }
    }

    // ==================== CLEANUP ====================

    public void destroy() {
        stopScan();
        disconnect();
        executor.execute(() -> {
            discoveredDevices.clear();
            listener = null;
            instance = null;
            Log.d(TAG, "Destroyed");
        });
    }
}
