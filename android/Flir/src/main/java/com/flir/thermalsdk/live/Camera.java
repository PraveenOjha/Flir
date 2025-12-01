package com.flir.thermalsdk.live;

import com.flir.thermalsdk.live.connectivity.ConnectionStatusListener;
import com.flir.thermalsdk.live.streaming.Stream;
import java.util.List;

/**
 * Stub class for FLIR SDK Camera - compile-time only.
 */
public class Camera {
    public void connect(ConnectParameters params, ConnectionStatusListener listener) {
        throw new UnsupportedOperationException("Stub!");
    }
    
    public void disconnect() {
        throw new UnsupportedOperationException("Stub!");
    }
    
    public List<Stream> getStreams() {
        throw new UnsupportedOperationException("Stub!");
    }
    
    public RemoteControl getRemoteControl() {
        throw new UnsupportedOperationException("Stub!");
    }
}
