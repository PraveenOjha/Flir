//
//  FLIRIdentity.h
//
//  Copyright © 2019 Teledyne FLIR. All rights reserved.
//

#pragma once

#import <ExternalAccessory/ExternalAccessory.h>

/**
 *  These enums represents the possible camera interfaces.
 *
 *  In this release only the *network*, *emulator*, and *lightning* enums are implemented in FLIR Thermal SDK for iOS.
 */
typedef NS_OPTIONS(NSUInteger, FLIRCommunicationInterface) {
    /// no interface.
    FLIRCommunicationInterfaceNone = 0x00,
    
    /// USB interface. *Note! Not implemented for iOS*
    FLIRCommunicationInterfaceUSB = 0x01,
    
    /// Network interface (wired or wireless).
    FLIRCommunicationInterfaceNetwork = 0x02,
    
    /// FireWire interface. *Note! Not implemented for iOS*
    FLIRCommunicationInterfaceFirewire = 0x04,

    /// Camera emulator interface.
    FLIRCommunicationInterfaceEmulator = 0x08,
    
    /// Bluetooth wireless interface. *Note! Not (yet) implemented for iOS*
    FLIRCommunicationInterfaceBluetooth = 0x10,
    
    /// Ebus interface. *Note! Not implemented for iOS*
    FLIRCommunicationInterfaceEbus = 0x20,
    
    /// USB HSI interface. *Note! Not implemented for iOS*
    FLIRCommunicationInterfaceUsbHsi = 0x40,
    
    /// iOS lightning interface.
    FLIRCommunicationInterfaceLightning = 0x80,
	
    /// Built-in IR camera. *Note! Not implemented for iOS*
    FLIRCommunicationInterfaceIntegrated = 0x100,

    /// FLIR ONE Wireless camera (using BLE and local WiFi).
    FLIRCommunicationInterfaceFlirOneWireless = 0x200,

    /// Custom interface. *Note! Not implemented for iOS*
    FLIRCommunicationInterfaceCustom = 1u << 31,
};

/**
 *  Types of FLIR cameras.
 */
typedef NS_ENUM(NSInteger, FLIRCameraType) {
    /// Any generic FLIR camera.
    FLIRCameraType_generic = 0x01,
    /// FLIR ONE pluggable camera.
    FLIRCameraType_flirOne = 0x02,
    /// FLIR ONE Edge wireless camera.
    FLIRCameraType_flirOneEdge = 0x04,
    /// FLIR ONE Edge Pro wireless camera.
    FLIRCameraType_flirOneEdgePro = 0x08,
	/// FLIR ONE Edge wireless camera.
    FLIRCameraType_unknown1 = 0x10,
    /// FLIR ONE Edge Pro wireless camera.
    FLIRCameraType_unknown2 = 0x20,
    /// FLIR Leia / Scout pro camera
    FLIRCameraType_leia = 0x80,
    /// FLIR luke camera
    FLIRCameraType_luke = 0x100,
    /// Unknown type of camera.
    FLIRCameraType_unknown = 0x200
};

/// Flir one model
typedef NS_ENUM(NSUInteger, FLIROneType)
{
    F1_gen3Pro,
    F1_gen3ProLt,
    F1_gen3Consumer,
    F1_gen2,
    F1_unknown,
    F1_edge,
    F1_edgePro
};

@class FLIRIdentity;

/**
 * Adapter information.
 *
 * Describes the network interface card that discovered a camera.
 */
@interface FLIRAdapterInfo : NSObject

/// The name of the adapter.
/// @return A string with the adapter name.
- (nonnull NSString *) name;

/// Flag telling if adapter is WiFi.
/// @return TRUE if adapter is a WiFi adapter.
- (BOOL) isWireless;

/// The adapter MAC address.
/// @return The MAC address of the adapter.
- (nonnull NSString *) macAddress;

/// The adapter IP address.
/// @return The IP address of the adapter.
- (nonnull NSString *) ipAddress;

/// The adapter subnet mask address.
/// @return The subnet mask of the adapter.
- (nonnull NSString *) mask;

@end


/**
 * Stores the IP settings of a discovered network camera.
 */
@interface FLIRIpSettings : NSObject

/// Tthe host (camera) name
- (nullable NSString *) hosttarget;

/// The IP address of the camera.
- (nonnull NSString *) ipAddress;

/// The MAC address of the camera.
- (nonnull NSString *) macAddress;

/// The default gateway for the camera.
- (nonnull NSString *) defaultGateway;

/// The subnet mask for the camera.
- (nonnull NSString *) subnetMask;

/// The camera DHCP setting.
/// - returns: TRUE if DHCP is enabled.
- (BOOL) isDhcpEnabled;

/// Validity of the IP settings.
/// - returns: TRUE if ok.
- (BOOL) isValid;

/// Interface information about the IF that found the camera.
- (nonnull FLIRAdapterInfo *) adapterInfo;

@end

/**
 * Represents camera device information.
 *
 * A FLIRCameraInformation object can be obtained from the FLIRRemoteControl object after a connection to a camera has been made.
 */
@interface FLIRCameraInformation : NSObject

/// The camera name, can be null.
- (nonnull NSString *) modelName;

/// The camera model name.
- (nonnull NSString *) name;

/// The camera display name.
- (nonnull NSString *) displayName;

/// The camera description.
- (nonnull NSString *) cameraDescription;

/// The camera serial number.
- (nonnull NSString *) serialNumber;

/// The camera osImageKitName. Same as osImageKitName.
- (nonnull NSString *) osImagekitName __attribute__((deprecated("Use osImageKitName instead.")));

/// The camera osImagekitName.
- (nonnull NSString *) osImageKitName;

/// The camera swCombinationVersion.
- (nonnull NSString *) swCombinationVersion;

/// The camera confKitName.
- (nonnull NSString *) confKitName;

/// The camera article.
- (nonnull NSString *) article;

/// The camera date
- (nullable NSString *) date;

/// Camera sensor width in pixels.
- (NSInteger) width;

/// Camera height in pixels.
- (NSInteger) height;

/// Camera firmware revision, could be e.g. a commit hash or version number
- (nonnull NSString *) firmwareRevision;

/// get flironetype (only valid for flir one cameras)
- (FLIROneType)getFlirOneType;

@end

/**
 * Represents a Camera identity.
 *
 * Used when discovering and connecting to cameras.
 */
@interface FLIRIdentity : NSObject

/**
 * The communication interface through which the camera will be accessed.
 */
- (FLIRCommunicationInterface) communicationInterface;

/**
 * Type of the camera
 */
- (FLIRCameraType) cameraType;

/**
 * A per session unique Id for the camera
 *
 * This is ONLY unique per session, starting a new scan might make a new Id for the same camera.
 *
 * This may or may not be human-readable. It's recommended to connect to the Camera and query
 * for CameraInformation to get a descriptive camera name. See `FLIRCameraInformation`
 */
- (nonnull NSString *) deviceId;

/**
 * IP settings for a network camera.
 *
 * This field contains value if communicationInterface is CommunicationInterface::network,
 * otherwise it will be empty.
 */
- (nullable FLIRIpSettings *)ipSettings;

/**
* Initializes FLIRIdentity object as a network camera identity.
*
* @param   ipAddr  the IP address of this identity.
*/
-(instancetype _Nullable)initWithIpAddr:(nullable NSString *)ipAddr;

/**
 * Initializes FLIRIdentity object as a FLIR One camera identity.
 *
 * @param   serial  the serial number to be copied into 'deviceId'.
*/
- (instancetype _Nullable)initWithFlirOneSerialNo:(nullable NSString *)serial;

/**
 * Create emulator identity
 */
- (instancetype _Nullable)initWithEmulatorType:(FLIRCameraType)type;

@end

