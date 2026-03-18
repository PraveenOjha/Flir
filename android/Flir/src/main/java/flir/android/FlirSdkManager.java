package flir.android;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import com.flir.thermalsdk.ErrorCode;
import com.flir.thermalsdk.androidsdk.ThermalSdkAndroid;
import com.flir.thermalsdk.androidsdk.image.BitmapAndroid;
import com.flir.thermalsdk.image.Point;
import com.flir.thermalsdk.image.ThermalValue;
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

/**
 * Simplified FLIR SDK Manager - matches sample app pattern
 * Simple: scan → connect → stream → disconnect
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
    }

    public void stopScan() {
        if (!isScanning)
            return;
        try {
            DiscoveryFactory.getInstance().stop(
                    CommunicationInterface.EMULATOR,
                    CommunicationInterface.USB,
                    CommunicationInterface.NETWORK,
                    CommunicationInterface.FLIR_ONE_WIRELESS);
        } catch (Exception e) {
            Log.e(TAG, "Stop scan failed", e);
        }
        isScanning = false;
        Log.d(TAG, "Discovery stopped");
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

        // Disconnect if already connected
        if (camera != null) {
            disconnect();
        }

        Log.d(TAG, "Connecting to: " + identity.deviceId);

        // Run on background thread (matches sample app pattern)
        executor.execute(() -> {
            try {
                camera = new Camera();
                camera.connect(identity, connectionStatusListener, new ConnectParameters());
                Log.d(TAG, "Connected to: " + identity.deviceId);

                if (listener != null) {
                    listener.onConnected(identity);
                }

                // Auto-start stream after connection (matches sample app)
                startStream();

            } catch (Exception e) {
                Log.e(TAG, "Connection failed", e);
                camera = null;
                notifyError("Connection failed: " + e.getMessage());
            }
        });
    }

    public void disconnect() {
        stopStream();

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
    }

    public boolean isConnected() {
        return camera != null;
    }

    // ==================== STREAMING ====================

    public void startStream() {
        if (camera == null) {
            notifyError("Not connected");
            return;
        }

        executor.execute(() -> {
            try {
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
                            try {
                                if (streamer != null) {
                                    streamer.update();
                                    Bitmap bitmap = BitmapAndroid.createBitmap(streamer.getImage()).getBitMap();
                                    if (bitmap != null) {
                                        latestBitmap = bitmap;
                                        if (listener != null) {
                                            listener.onFrame(bitmap);
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Frame error", e);
                            }
                        },
                        error -> {
                            Log.e(TAG, "Stream error: " + error);
                            notifyError("Stream error: " + error);
                        });

                Log.d(TAG, "Streaming started");

            } catch (Exception e) {
                Log.e(TAG, "Start stream failed", e);
                notifyError("Stream failed: " + e.getMessage());
            }
        });
    }

    public void stopStream() {
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
        if (streamer == null)
            return Double.NaN;

        final double[] result = { Double.NaN };
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
        return result[0];
    }

    // ==================== LISTENERS ====================

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
        Log.d(TAG, "Disconnected: " + (errorCode != null ? errorCode : "clean"));
        camera = null;
        if (listener != null) {
            listener.onDisconnected();
        }
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
        discoveredDevices.clear();
        listener = null;
        instance = null;
        Log.d(TAG, "Destroyed");
    }
}
