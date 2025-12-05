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
import com.flir.thermalsdk.live.remote.OnRemoteError;
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
    private final Executor executor = Executors.newCachedThreadPool();
    
    // State
    private boolean isInitialized = false;
    private boolean isScanning = false;
    private Camera camera;
    private ThermalStreamer streamer;
    private Stream activeStream;
    private ThermalImage lastThermalImage;
    private final List<Identity> discoveredDevices = Collections.synchronizedList(new ArrayList<>());
    
    // Temperature data storage - store actual values since ThermalImage reference may not persist
    private double[][] temperatureGrid = null;
    private int thermalWidth = 0;
    private int thermalHeight = 0;
    private final Object temperatureLock = new Object();
    
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
    
    /**
     * Initialize the FLIR Thermal SDK
     */
    public void initialize() {
        if (isInitialized) {
            Log.d(TAG, "Already initialized");
            return;
        }
        
        try {
            ThermalSdkAndroid.init(context);
            isInitialized = true;
            Log.d(TAG, "SDK initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize SDK", e);
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
        
        Log.d(TAG, "Starting discovery for EMULATOR, NETWORK, USB...");
        
        try {
            DiscoveryFactory.getInstance().scan(
                discoveryListener,
                CommunicationInterface.EMULATOR,
                CommunicationInterface.NETWORK,
                CommunicationInterface.USB
            );
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
                CommunicationInterface.USB
            );
        } catch (Exception e) {
            Log.e(TAG, "Failed to stop scan", e);
        }
        
        isScanning = false;
        Log.d(TAG, "Discovery stopped");
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
                
                Log.d(TAG, "Connected to camera");
                
                if (listener != null) {
                    listener.onConnected(identity);
                }
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
        
        if (listener != null) {
            listener.onDisconnected();
        }
        
        Log.d(TAG, "Disconnected");
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
                streamer = new ThermalStreamer(thermalStream);
                
                // Start receiving frames using OnReceived and OnRemoteError
                thermalStream.start(
                    (OnReceived<Void>) v -> {
                        executor.execute(() -> {
                            try {
                                if (streamer != null) {
                                    streamer.update();
                                    
                                    // Extract temperature data inside the callback where ThermalImage is valid
                                    streamer.withThermalImage(thermalImage -> {
                                        lastThermalImage = thermalImage;
                                        
                                        // Cache temperature grid for queries outside callback
                                        try {
                                            int width = thermalImage.getWidth();
                                            int height = thermalImage.getHeight();
                                            
                                            synchronized (temperatureLock) {
                                                // Only reallocate if size changed
                                                if (temperatureGrid == null || thermalWidth != width || thermalHeight != height) {
                                                    temperatureGrid = new double[height][width];
                                                    thermalWidth = width;
                                                    thermalHeight = height;
                                                    Log.d(TAG, "Temperature grid allocated: " + width + "x" + height);
                                                }
                                                
                                                // Sample key points for temperature (full grid is expensive)
                                                // Store center point and last queried region
                                                int cx = width / 2;
                                                int cy = height / 2;
                                                
                                                // Update center region (3x3 around center)
                                                for (int dy = -1; dy <= 1; dy++) {
                                                    for (int dx = -1; dx <= 1; dx++) {
                                                        int px = Math.max(0, Math.min(width - 1, cx + dx));
                                                        int py = Math.max(0, Math.min(height - 1, cy + dy));
                                                        try {
                                                            ThermalValue val = thermalImage.getValueAt(new Point(px, py));
                                                            if (val != null) {
                                                                temperatureGrid[py][px] = val.asCelsius().value;
                                                            }
                                                        } catch (Exception ignored) {}
                                                    }
                                                }
                                            }
                                        } catch (Exception e) {
                                            Log.w(TAG, "Error caching temperature grid", e);
                                        }
                                    });
                                    
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
                            }
                        });
                    },
                    error -> {
                        Log.e(TAG, "Stream error: " + error);
                        notifyError("Stream error: " + error);
                    }
                );
                
                Log.d(TAG, "Streaming started");
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to start stream", e);
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
        
        streamer = null;
        lastThermalImage = null;
        
        Log.d(TAG, "Streaming stopped");
        
        // Clear temperature cache
        synchronized (temperatureLock) {
            temperatureGrid = null;
            thermalWidth = 0;
            thermalHeight = 0;
        }
    }
    
    /**
     * Get temperature at a specific point in the image
     * Uses cached temperature grid that's populated during frame callbacks
     * @param x X coordinate (0 to image width-1)
     * @param y Y coordinate (0 to image height-1)
     * @return Temperature in Celsius, or Double.NaN if not available
     */
    public double getTemperatureAt(int x, int y) {
        // First try to query via streamer callback (most accurate)
        if (streamer != null) {
            final double[] result = {Double.NaN};
            try {
                streamer.withThermalImage(thermalImage -> {
                    try {
                        // Clamp coordinates to thermal image bounds
                        int imgWidth = thermalImage.getWidth();
                        int imgHeight = thermalImage.getHeight();
                        
                        int clampedX = Math.max(0, Math.min(imgWidth - 1, x));
                        int clampedY = Math.max(0, Math.min(imgHeight - 1, y));
                        
                        Point point = new Point(clampedX, clampedY);
                        ThermalValue value = thermalImage.getValueAt(point);
                        
                        if (value != null) {
                            result[0] = value.asCelsius().value;
                            
                            // Also cache in grid for future use
                            synchronized (temperatureLock) {
                                if (temperatureGrid != null && clampedY < thermalHeight && clampedX < thermalWidth) {
                                    temperatureGrid[clampedY][clampedX] = result[0];
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Error in withThermalImage temperature query", e);
                    }
                });
                
                if (!Double.isNaN(result[0])) {
                    return result[0];
                }
            } catch (Exception e) {
                Log.w(TAG, "Streamer temperature query failed, using cache", e);
            }
        }
        
        // Fallback: check cached grid
        synchronized (temperatureLock) {
            if (temperatureGrid != null && thermalWidth > 0 && thermalHeight > 0) {
                int clampedX = Math.max(0, Math.min(thermalWidth - 1, x));
                int clampedY = Math.max(0, Math.min(thermalHeight - 1, y));
                double cachedTemp = temperatureGrid[clampedY][clampedX];
                if (cachedTemp != 0.0) {
                    return cachedTemp;
                }
            }
        }
        
        Log.w(TAG, "No temperature data available for point (" + x + ", " + y + ")");
        return Double.NaN;
    }
    
    /**
     * Get thermal image dimensions
     */
    public int getThermalWidth() {
        synchronized (temperatureLock) {
            return thermalWidth;
        }
    }
    
    public int getThermalHeight() {
        synchronized (temperatureLock) {
            return thermalHeight;
        }
    }
    
    /**
     * Get temperature at normalized coordinates (0.0 to 1.0)
     * @param normalizedX X coordinate (0.0 to 1.0)
     * @param normalizedY Y coordinate (0.0 to 1.0)
     * @return Temperature in Celsius, or Double.NaN if not available
     */
    public double getTemperatureAtNormalized(double normalizedX, double normalizedY) {
        int width, height;
        
        // Get dimensions from cache (thread-safe)
        synchronized (temperatureLock) {
            width = thermalWidth;
            height = thermalHeight;
        }
        
        if (width <= 0 || height <= 0) {
            // Try to get from streamer
            if (streamer != null) {
                final int[] dims = {0, 0};
                try {
                    streamer.withThermalImage(thermalImage -> {
                        dims[0] = thermalImage.getWidth();
                        dims[1] = thermalImage.getHeight();
                    });
                    width = dims[0];
                    height = dims[1];
                } catch (Exception ignored) {}
            }
            
            if (width <= 0 || height <= 0) {
                return Double.NaN;
            }
        }
        
        int x = (int) (normalizedX * (width - 1));
        int y = (int) (normalizedY * (height - 1));
        
        return getTemperatureAt(x, y);
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
                    Log.d(TAG, "Palette set to: " + paletteName);
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
            Log.d(TAG, "Device found: " + identity.deviceId + 
                       " type=" + identity.communicationInterface);
            
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
            Log.d(TAG, "Discovery finished for: " + iface);
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
}
