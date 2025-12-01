package com.flir.thermalsdk.live.streaming;

import com.flir.thermalsdk.image.ThermalImage;

/**
 * Stub class for FLIR SDK ThermalStreamer - compile-time only.
 */
public class ThermalStreamer implements Stream {
    public ThermalStreamer(Stream stream) {
        throw new UnsupportedOperationException("Stub!");
    }
    
    public void update() {
        throw new UnsupportedOperationException("Stub!");
    }
    
    public Object getImage() {
        throw new UnsupportedOperationException("Stub!");
    }
    
    public void withThermalImage(ThermalImageCallback callback) {
        throw new UnsupportedOperationException("Stub!");
    }
    
    public interface ThermalImageCallback {
        void run(ThermalImage thermalImage);
    }
}
