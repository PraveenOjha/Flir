package flir.android;

import android.graphics.Bitmap;
import android.util.Log;

import com.flir.thermalsdk.androidsdk.image.BitmapAndroid;
import com.flir.thermalsdk.image.ThermalImage;
import com.flir.thermalsdk.live.Camera;
import com.flir.thermalsdk.live.CommunicationInterface;
import com.flir.thermalsdk.live.ConnectParameters;
import com.flir.thermalsdk.live.Identity;
import com.flir.thermalsdk.live.connectivity.ConnectionStatusListener;
import com.flir.thermalsdk.live.discovery.DiscoveryEventListener;
import com.flir.thermalsdk.live.discovery.DiscoveryFactory;
import com.flir.thermalsdk.live.streaming.Stream;
import com.flir.thermalsdk.live.streaming.ThermalStreamer;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Objects;

public class CameraHandler {
    
    private static final String TAG = "CameraHandler";

    private StreamDataListener streamDataListener;

    public interface StreamDataListener {
        void images(FrameDataHolder dataHolder);
        void images(Bitmap msxBitmap, Bitmap dcBitmap);
    }

    LinkedList<Identity> foundCameraIdentities = new LinkedList<>();

    private Camera camera;

    private Stream connectedStream;
    private ThermalStreamer streamer;
    // Cache the latest ThermalImage delivered by the streamer
    private ThermalImage latestThermalImage;

    public CameraHandler() {
        Log.d(TAG, "CameraHandler constr");
    }

    public void startDiscovery(DiscoveryEventListener cameraDiscoveryListener, DiscoveryStatus discoveryStatus) {
        DiscoveryFactory.getInstance().scan(cameraDiscoveryListener, CommunicationInterface.EMULATOR, CommunicationInterface.USB);
        discoveryStatus.started();
    }

    public void stopDiscovery(DiscoveryStatus discoveryStatus) {
        DiscoveryFactory.getInstance().stop(CommunicationInterface.EMULATOR, CommunicationInterface.USB);
        discoveryStatus.stopped();
    }

    public synchronized void connect(Identity identity, ConnectionStatusListener connectionStatusListener) throws IOException {
        Log.d(TAG, "connect identity: " + identity);
        camera = new Camera();
        camera.connect(identity, connectionStatusListener, new ConnectParameters());
    }

    public synchronized void disconnect() {
        Log.d(TAG, "disconnect");
        if (camera == null) {
            return;
        }
        if (connectedStream == null) {
            return;
        }

        if (connectedStream.isStreaming()) {
            connectedStream.stop();
        }
        camera.disconnect();
        camera = null;
    }

    public synchronized void startStream(StreamDataListener listener) {
        this.streamDataListener = listener;
        if (camera == null || !camera.isConnected()) {
            Log.e(TAG, "startStream, failed, camera was null or not connected");
            return;
        }
        connectedStream = camera.getStreams().get(0);
        if (connectedStream.isThermal()) {
            streamer = new ThermalStreamer(connectedStream);
        } else {
            Log.e(TAG, "startStream, failed, no thermal stream available for the camera");
            return;
        }
        connectedStream.start(
                unused -> {
                    streamer.update();
                    final Bitmap[] dcBitmap = new Bitmap[1];
                    streamer.withThermalImage(thermalImage -> {
                        try {
                            // Cache the latest ThermalImage for sampling
                            latestThermalImage = thermalImage;
                            if (thermalImage.getFusion() != null && thermalImage.getFusion().getPhoto() != null) {
                                dcBitmap[0] = BitmapAndroid.createBitmap(thermalImage.getFusion().getPhoto()).getBitMap();
                            }
                            // The streamer.getImage() returns the ImageBuffer expected by BitmapAndroid
                            final Bitmap thermalPixels = BitmapAndroid.createBitmap(streamer.getImage()).getBitMap();
                            if (streamDataListener != null) streamDataListener.images(thermalPixels, dcBitmap[0]);
                        } catch (Exception e) {
                            Log.e(TAG, "thermal bitmap creation error", e);
                        }
                    });
                },
                error -> Log.e(TAG, "Streaming error: " + error));
    }

    public synchronized Double getTemperatureAt(int x, int y) {
        try {
            if (streamer == null) return null;
            ThermalImage img = latestThermalImage;
            if (img == null) return null;

            java.lang.reflect.Method[] methods = img.getClass().getMethods();
            for (java.lang.reflect.Method m : methods) {
                String name = m.getName().toLowerCase();
                Class<?>[] params = m.getParameterTypes();

                if ((name.contains("get") || name.contains("value") || name.contains("temperature")) && params.length == 2
                        && (params[0] == int.class || params[0] == Integer.class || params[0] == double.class || params[0] == float.class)
                        && (params[1] == int.class || params[1] == Integer.class || params[1] == double.class || params[1] == float.class)) {
                    try {
                        Object res = null;
                        if (params[0] == int.class && params[1] == int.class) {
                            res = m.invoke(img, x, y);
                        } else if (params[0] == double.class && params[1] == double.class) {
                            res = m.invoke(img, (double) x, (double) y);
                        } else if (params[0] == float.class && params[1] == float.class) {
                            res = m.invoke(img, (float) x, (float) y);
                        } else {
                            res = m.invoke(img, Integer.valueOf(x), Integer.valueOf(y));
                        }
                        if (res instanceof Number) {
                            return ((Number) res).doubleValue();
                        }
                    } catch (Exception e) {
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "getTemperatureAt error", e);
        }
        return null;
    }

    public void add(Identity identity) {
        foundCameraIdentities.add(identity);
    }

    public Identity getCppEmulator() {
        for (Identity foundCameraIdentity : foundCameraIdentities) {
            if (foundCameraIdentity.deviceId.contains("C++ Emulator")) {
                return foundCameraIdentity;
            }
        }
        return null;
    }

    public Identity getFlirOneEmulator() {
        for (Identity foundCameraIdentity : foundCameraIdentities) {
            if (foundCameraIdentity.deviceId.contains("EMULATED FLIR ONE")) {
                return foundCameraIdentity;
            }
        }
        return null;
    }

    public Identity getFlirOne() {
        for (Identity foundCameraIdentity : foundCameraIdentities) {
            if (foundCameraIdentity.communicationInterface == CommunicationInterface.USB) return foundCameraIdentity;
        }
        return null;
    }

    public String getDeviceInfo() {
        if (camera == null) return "N/A";
        try {
            if (camera.getRemoteControl() == null) return "N/A";
            return camera.getRemoteControl().cameraInformation().getSync().displayName;
        } catch (Exception e) {
            return "N/A";
        }
    }

    public interface DiscoveryStatus {
        void started();
        void stopped();
    }
}
