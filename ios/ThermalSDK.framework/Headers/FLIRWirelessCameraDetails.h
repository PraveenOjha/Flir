//
//  FLIRWirelessCameraDetails.h
//  ThermalSDK
//
//  Created by Teledyne on 2022-08-24.
//  Copyright © 2022 Teledyne FLIR. All rights reserved.
//

#import <Foundation/Foundation.h>

#import "FLIRIdentity.h"

NS_ASSUME_NONNULL_BEGIN
@class FLIRWirelessCameraDetails;

@protocol FLIRWirelessCameraDetailsEventDelegate <NSObject>
-(void) detailsUpdated:(FLIRWirelessCameraDetails *) wcd;
@end

typedef NS_ENUM(NSInteger, FLIRSignalStrength) {
    /**
     * @brief State in which it is possible to discover the camera.
     * @note after discovery phrase camera can be moved further away from the phone and still it will be possible to connect and stream.
     */
    discoverable,
    /** @brief Good signal, allows to live stream and interact with the camera. */
    excellent,
    /** @brief Signal strength is fair enough for live streaming and interaction with the camera. Minor frame drops or lag/delay may occur. */
    fair,
    /** @brief Weak signal may result in significant frame drops and delays in communication with the camera. */
    weak,
    /** @brief Bad signal will eventually result in a connection break or timeout and it will most likely be impossible to get a live stream or interact with the camera. */
    bad_distance
};

@interface FLIRWirelessCameraDetails : NSObject

/// SSID/name of the FLIR ONE WiFi network.
@property (nonatomic, readonly) NSString* ssid;

/// Password for the FLIR ONE WiFi network.
@property (nonatomic, readonly) NSString* password;

/// The IP address of the F1 wireless camera i.e. 192.168.1.1.
@property (nonatomic, readonly) NSString* ipAddress;

/// Camera's firmware version.
@property (nonatomic, readonly) NSString* firmwareVersion;

/// Camera's serial number.
@property (nonatomic, readonly) NSString* serialNumber;

/// Flag determining if this is the first time setup/connection.
@property (nonatomic, readonly) BOOL firstTimeSetupFlag;

/// The transmit power in dBm. Valid range is [-127, 126]. A value of 127 (0x7F) indicates that the TX power is not present.
@property (nonatomic, readonly) NSInteger txPowerLevel;

/// Returns the received signal strength in dBm. The valid range is [-127, 126].
@property (nonatomic, readonly) NSInteger rssi;

/// Returns the distance.
@property (nonatomic, readonly) FLIRSignalStrength signal;

/// Flag determining if the default password has changed.
@property (nonatomic, readonly) BOOL passwordChangedFlag;

/// Camera's current battery level.
@property (nonatomic, readonly) NSInteger batteryLevel;

/// Camera's current battery state - charging or not.
@property (nonatomic, readonly) NSInteger batteryCharging;

/// Determines if there are active connections to the camera's WiFi network.
@property (nonatomic, readonly) NSInteger ConnectedFlag;

/// Delegate that will receive changes, useful to check distance between device and camera
@property (nonatomic, retain, nullable) id<FLIRWirelessCameraDetailsEventDelegate> delegate;

@property (nonatomic, readonly) NSInteger wifiMode;

@property (nonatomic, readonly) FLIRCameraType cameraType;

@end



NS_ASSUME_NONNULL_END
