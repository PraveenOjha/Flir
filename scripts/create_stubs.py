import os
import subprocess

def create_file(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        f.write(content)

# Define stub classes
stubs = {
    "com/flir/thermalsdk/androidsdk/image/BitmapAndroid.java": """
package com.flir.thermalsdk.androidsdk.image;
import android.graphics.Bitmap;
import com.flir.thermalsdk.image.JavaImageBuffer;
public class BitmapAndroid {
    public static BitmapAndroid createBitmap(JavaImageBuffer buffer) { return null; }
    public Bitmap getBitMap() { return null; }
}
""",
    "com/flir/thermalsdk/image/JavaImageBuffer.java": """
package com.flir.thermalsdk.image;
public interface JavaImageBuffer {}
""",
    "com/flir/thermalsdk/image/ThermalImage.java": """
package com.flir.thermalsdk.image;
public class ThermalImage {
    public Fusion getFusion() { return null; }
}
""",
    "com/flir/thermalsdk/image/Fusion.java": """
package com.flir.thermalsdk.image;
public class Fusion {
    public JavaImageBuffer getPhoto() { return null; }
}
""",
    "com/flir/thermalsdk/live/Camera.java": """
package com.flir.thermalsdk.live;
import java.util.List;
import com.flir.thermalsdk.live.connectivity.ConnectionStatusListener;
import com.flir.thermalsdk.live.streaming.Stream;
public class Camera {
    public void connect(Identity id, ConnectionStatusListener listener, ConnectParameters params) {}
    public void disconnect() {}
    public boolean isConnected() { return false; }
    public List<Stream> getStreams() { return null; }
    public RemoteControl getRemoteControl() { return null; }
}
""",
    "com/flir/thermalsdk/live/RemoteControl.java": """
package com.flir.thermalsdk.live;
public class RemoteControl {
    public CameraInformation cameraInformation() { return null; }
}
""",
    "com/flir/thermalsdk/live/CameraInformation.java": """
package com.flir.thermalsdk.live;
public class CameraInformation {
    public CameraInformationSync getSync() { return null; }
}
""",
    "com/flir/thermalsdk/live/CameraInformationSync.java": """
package com.flir.thermalsdk.live;
public class CameraInformationSync {
    public String displayName;
}
""",
    "com/flir/thermalsdk/live/CommunicationInterface.java": """
package com.flir.thermalsdk.live;
public enum CommunicationInterface {
    USB, EMULATOR
}
""",
    "com/flir/thermalsdk/live/ConnectParameters.java": """
package com.flir.thermalsdk.live;
public class ConnectParameters {}
""",
    "com/flir/thermalsdk/live/Identity.java": """
package com.flir.thermalsdk.live;
public class Identity {
    public String deviceId;
    public CommunicationInterface communicationInterface;
}
""",
    "com/flir/thermalsdk/ErrorCode.java": """
package com.flir.thermalsdk;
public class ErrorCode {}
""",
    "com/flir/thermalsdk/live/discovery/DiscoveredCamera.java": """
package com.flir.thermalsdk.live.discovery;
import com.flir.thermalsdk.live.Identity;
public class DiscoveredCamera {
    public Identity identity;
}
""",
    "com/flir/thermalsdk/live/connectivity/ConnectionStatusListener.java": """
package com.flir.thermalsdk.live.connectivity;
import com.flir.thermalsdk.ErrorCode;
public interface ConnectionStatusListener {
    void onDisconnected(ErrorCode errorCode);
}
""",
    "com/flir/thermalsdk/live/discovery/DiscoveryEventListener.java": """
package com.flir.thermalsdk.live.discovery;
import com.flir.thermalsdk.live.CommunicationInterface;
import com.flir.thermalsdk.ErrorCode;
public interface DiscoveryEventListener {
    void onCameraFound(DiscoveredCamera camera);
    void onDiscoveryError(CommunicationInterface communicationInterface, ErrorCode errorCode);
}
""",
    "com/flir/thermalsdk/live/discovery/DiscoveryFactory.java": """
package com.flir.thermalsdk.live.discovery;
import com.flir.thermalsdk.live.CommunicationInterface;
public class DiscoveryFactory {
    public static DiscoveryFactory getInstance() { return new DiscoveryFactory(); }
    public void scan(DiscoveryEventListener listener, CommunicationInterface... ifaces) {}
    public void stop(CommunicationInterface... ifaces) {}
}
""",
    "com/flir/thermalsdk/live/streaming/Stream.java": """
package com.flir.thermalsdk.live.streaming;
import com.flir.thermalsdk.image.JavaImageBuffer;
public interface Stream {
    boolean isStreaming();
    void stop();
    boolean isThermal();
    void start(OnImageReceivedListener listener, OnErrorListener errorListener);
    interface OnImageReceivedListener { void onImageReceived(Void v); }
    interface OnErrorListener { void onError(Object error); }
}
""",
    "com/flir/thermalsdk/live/streaming/ThermalStreamer.java": """
package com.flir.thermalsdk.live.streaming;
import com.flir.thermalsdk.image.ThermalImage;
import com.flir.thermalsdk.image.JavaImageBuffer;
public class ThermalStreamer {
    public ThermalStreamer(Stream stream) {}
    public void update() {}
    public void withThermalImage(OnThermalImageReceivedListener listener) {}
    public JavaImageBuffer getImage() { return null; }
    public interface OnThermalImageReceivedListener { void onThermalImageReceived(ThermalImage image); }
}
"""
}

# Create source files
base_dir = "stubs_src"
for path, content in stubs.items():
    create_file(os.path.join(base_dir, path), content)

# Mock android.graphics.Bitmap
create_file(os.path.join(base_dir, "android/graphics/Bitmap.java"), "package android.graphics; public class Bitmap {}")

# Compile
sources_file = "sources.txt"
with open(sources_file, 'w') as f:
    for root, dirs, files in os.walk(base_dir):
        for file in files:
            if file.endswith(".java"):
                f.write(os.path.join(root, file) + "\n")

subprocess.run(["javac", "@" + sources_file], check=True)

# Jar
subprocess.run(["jar", "cf", "flir-stubs.jar", "-C", base_dir, "."], check=True)

# Move to libs
os.rename("flir-stubs.jar", "android/Flir/libs/flir-stubs.jar")

# Cleanup
import shutil
shutil.rmtree(base_dir)
os.remove(sources_file)
print("Done!")
