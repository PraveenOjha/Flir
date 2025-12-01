package com.flir.thermalsdk.live.discovery;

import com.flir.thermalsdk.live.CommunicationInterface;
import com.flir.thermalsdk.live.Identity;

/**
 * Stub interface for FLIR SDK DiscoveryEventListener - compile-time only.
 */
public interface DiscoveryEventListener {
    void onCameraFound(Identity identity);
    void onCameraLost(Identity identity);
    void onDiscoveryError(CommunicationInterface iface, String errorMessage);
    void onDiscoveryFinished(CommunicationInterface iface);
}
