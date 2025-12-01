package com.flir.thermalsdk.live.discovery;

import com.flir.thermalsdk.live.CommunicationInterface;

/**
 * Stub class for FLIR SDK DiscoveryFactory - compile-time only.
 * The real SDK uses instance methods via getInstance().
 */
public class DiscoveryFactory {
    
    private static DiscoveryFactory instance;
    
    private DiscoveryFactory() {}
    
    public static DiscoveryFactory getInstance() {
        if (instance == null) {
            instance = new DiscoveryFactory();
        }
        return instance;
    }
    
    public void scan(DiscoveryEventListener listener, CommunicationInterface... interfaces) {
        throw new UnsupportedOperationException("Stub!");
    }
    
    public void stop(CommunicationInterface... interfaces) {
        throw new UnsupportedOperationException("Stub!");
    }
    
    public void stop() {
        throw new UnsupportedOperationException("Stub!");
    }
}
