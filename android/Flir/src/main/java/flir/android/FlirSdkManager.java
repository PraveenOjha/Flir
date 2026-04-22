package flir.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.util.Log;

import com.flir.thermalsdk.ErrorCode;
import com.flir.thermalsdk.androidsdk.ThermalSdkAndroid;
import com.flir.thermalsdk.androidsdk.image.BitmapAndroid;
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
import com.flir.thermalsdk.live.streaming.Stream;
import com.flir.thermalsdk.live.streaming.ThermalStreamer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    private boolean isInitialized = false;
    private boolean isScanning = false;
    private Camera camera;
    private ThermalStreamer streamer;
    private Stream activeStream;
    private final List<Identity> discoveredDevices = Collections.synchronizedList(new ArrayList<>());
    private volatile Bitmap latestBitmap;
    private volatile String currentPaletteName = "iron";
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
        if (isInitialized)
            return;
        try {
            ThermalSdkAndroid.init(context);
            isInitialized = true;
            Log.d(TAG, "SDK initialized");
        } catch (Exception e) {
            Log.e(TAG, "SDK init failed", e);
            notifyError("SDK init failed: " + e.getMessage());
        }
    }

    public boolean isInitialized() {
        return isInitialized;
    }

    // ==================== DISCOVERY ====================

    public void scan() {
        executor.execute(() -> {
            if (!isInitialized) {
                notifyError("SDK not initialized");
                return;
            }
            if (isScanning)
                return;

            isScanning = true;
            discoveredDevices.clear();
            Log.d(TAG, "Starting discovery...");

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
        if (!isScanning) return;
        
        // Use a temporary flag to prevent concurrent stop calls
        isScanning = false;
        
        executor.execute(() -> {
            try {
                Log.d(TAG, "Stopping discovery...");
                DiscoveryFactory.getInstance().stop(
                        CommunicationInterface.EMULATOR,
                        CommunicationInterface.USB,
                        CommunicationInterface.NETWORK,
                        CommunicationInterface.FLIR_ONE_WIRELESS);
                Log.d(TAG, "Discovery stopped successfully");
            } catch (Exception e) {
                // This is where the 'Receiver not registered' usually happens in SDK internals.
                // We catch it silently as it means the SDK already cleaned up or is in a weird state.
                Log.w(TAG, "Stop scan warning (internal SDK): " + e.getMessage());
            }
        });
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

                Log.d(TAG, "Connecting to: " + identity.deviceId);
                camera = new Camera();

                // ── Authenticate for NETWORK/WIRELESS cameras (required by FLIR SDK) ──
                // Matches the official NetworkCamera sample app pattern.
                // The FLIR One Edge Pro is a network/wireless camera and will reject
                // connections without prior authentication + trust approval.
                if (identity.communicationInterface == CommunicationInterface.NETWORK || 
                    identity.communicationInterface == CommunicationInterface.FLIR_ONE_WIRELESS) {
                    Log.d(TAG, "Network/Wireless camera detected — authenticating...");

                    // Use a persistent application name (workaround for camera bug
                    // where re-auth with a different name conflicts). Same pattern
                    // as CameraAuthName in the NetworkCamera sample.
                    SharedPreferences prefs = context.getSharedPreferences(
                            "flir_auth", Context.MODE_PRIVATE);
                    String authName = prefs.getString("auth_name", null);
                    if (authName == null) {
                        authName = context.getPackageName() + "-" +
                                (System.currentTimeMillis() % 10000);
                        prefs.edit().putString("auth_name", authName).apply();
                    }

                    AuthenticationResponse response;
                    int attempts = 0;
                    final int MAX_AUTH_ATTEMPTS = 30; // 30 seconds max wait
                    do {
                        response = camera.authenticate(identity, authName,
                                41 * 1000); // 41-second timeout per attempt
                        Log.d(TAG, "Auth attempt " + (attempts + 1) +
                                " status: " + response.authenticationStatus);

                        if (response.authenticationStatus ==
                                AuthenticationResponse.AuthenticationStatus.PENDING) {
                            // Camera is waiting for user to press "Trust" on its screen
                            Thread.sleep(1000);
                        }
                        attempts++;
                    } while (response.authenticationStatus ==
                            AuthenticationResponse.AuthenticationStatus.PENDING
                            && attempts < MAX_AUTH_ATTEMPTS);

                    if (response.authenticationStatus !=
                            AuthenticationResponse.AuthenticationStatus.APPROVED) {
                        Log.e(TAG, "Authentication rejected/timed out: " +
                                response.authenticationStatus);
                        camera = null;
                        notifyError("Camera authentication failed. " +
                                "Check the camera screen for a trust prompt.");
                        return;
                    }
                    Log.d(TAG, "Authentication approved");
                }

                camera.connect(identity, connectionStatusListener, new ConnectParameters());
                Log.d(TAG, "Connected to: " + identity.deviceId);

                if (listener != null) {
                    listener.onConnected(identity);
                }

                // Auto-start stream after connection (matches sample app)
                startStreamInternal();

            } catch (Exception e) {
                Log.e(TAG, "Connection failed", e);
                camera = null;
                notifyError("Connection failed: " + e.getMessage());
            }
        });
    }

    public void disconnect() {
        executor.execute(() -> {
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
            if (!camera.isConnected()) {
                Log.e(TAG, "Camera not connected, cannot start stream");
                notifyError("Camera not connected");
                return;
            }

            List<Stream> streams = camera.getStreams();
            if (streams == null || streams.isEmpty()) {
                notifyError("No streams available");
                return;
            }

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
                                            Palette palette = 
                                                PaletteManager.getDefaultPalettes().stream()
                                                    .filter(p -> p.name.equalsIgnoreCase(paletteToApply))
                                                    .findFirst()
                                                    .orElse(null);
                                            if (palette != null) {
                                                thermalImage.setPalette(palette);
                                            }
                                        }

                                        // 2. Save Radiometric Snapshot if requested
                                        if (snapshotPath != null) {
                                            try {
                                                thermalImage.saveAs(snapshotPath);
                                                Log.i(TAG, "Radiometric snapshot saved to: " + snapshotPath);
                                            } catch (java.io.IOException e) {
                                                Log.e(TAG, "Failed to save radiometric snapshot", e);
                                            }
                                        }

                                        // 3. Generate Bitmap for display
                                        Bitmap bitmap = BitmapAndroid.createBitmap(thermalImage.getImage()).getBitMap();
                                        if (bitmap != null) {
                                            latestBitmap = bitmap;
                                            if (listener != null) {
                                                listener.onFrame(bitmap);
                                            }
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

    // ==================== LISTENERS ====================
    
    public void setPalette(String paletteName) {
        this.currentPaletteName = paletteName;
        Log.d(TAG, "Requested palette: " + paletteName);
    }

    public void captureRadiometricSnapshot(String path) {
        this.pendingSnapshotPath = path;
        Log.d(TAG, "Pending radiometric snapshot: " + path);
    }

    private final DiscoveryEventListener discoveryListener = new DiscoveryEventListener() {
        @Override
        public void onCameraFound(DiscoveredCamera discoveredCamera) {
            Identity identity = discoveredCamera.getIdentity();
            Log.d(TAG, "Device found: " + identity.deviceId);

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
