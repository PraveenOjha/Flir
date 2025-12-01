package com.flir.thermalsdk.live;

public class Identity {
    public final CommunicationInterface communicationInterface;
    public final CameraType cameraType;
    public final IpSettings ipSettings;
    public final String deviceId;

    public Identity() {
        this.communicationInterface = null;
        this.cameraType = null;
        this.ipSettings = null;
        this.deviceId = null;
    }

    public Identity(CommunicationInterface communicationInterface, CameraType cameraType, String deviceId,
            IpSettings ipSettings) {
        this.communicationInterface = communicationInterface;
        this.cameraType = cameraType;
        this.deviceId = deviceId;
        this.ipSettings = ipSettings;
    }
}
