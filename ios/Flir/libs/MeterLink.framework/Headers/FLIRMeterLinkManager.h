//
//  FLIRMeterLinkManager.h
//  MeterLink
//
//  Copyright © 2019 Teledyne FLIR. All rights reserved.
//

#import <Foundation/Foundation.h>
#import "FLIRMeterLinkDevice.h"

/**
    Represents the current state of a meterLink connection

 - FLIRMeterLinkStateUnknown: State unknown, update imminent.
 - FLIRMeterLinkStateResetting: The connection with the system service was momentarily lost, update imminent.
 - FLIRMeterLinkStateUnsupported: The platform doesn't support the Bluetooth Low Energy Central/Client role.
 - FLIRMeterLinkStateUnauthorized: The application is not authorized to use the Bluetooth Low Energy Central/Client role.
 - FLIRMeterLinkStatePoweredOff: Bluetooth is currently powered off.
 - FLIRMeterLinkStatePoweredOn: Bluetooth is currently powered on and available to use.
 */
typedef NS_ENUM(NSInteger, FLIRMeterLinkState) {
    FLIRMeterLinkStateUnknown = 0,
    FLIRMeterLinkStateResetting,
    FLIRMeterLinkStateUnsupported,
    FLIRMeterLinkStateUnauthorized,
    FLIRMeterLinkStatePoweredOff,
    FLIRMeterLinkStatePoweredOn,
};

//! File types for progress
typedef NS_ENUM(NSInteger, FLIRMeterLinkFileType)
{
    MLFileTypeLog,    //!< Log file
    MLFileTypeJPEG    //!< JPEG file
};

//! Parser error codes
typedef NS_ENUM(NSInteger, FLIRMeterLinkParseError)
{
    invalidType,         //!< Parsed packet contains an invalid/unknown header
    uninitializedDevice, //!< The device has not yet sent initialization data
    badParameter,        //!< The packet contains some invalid object
    badJpeg              //!< A JPEG transmission has been corrupted
};

/**
 The delegate of a FLIRMeterLinkManager object must adopt the <code>FLIRMeterLinkDelegate</code> protocol. The methods allow for the discovery and
 connection of FLIR meterLink devices.
 */
@protocol FLIRMeterLinkDelegate <NSObject>


/**
 This method is invoked while scanning, upon the discovery of a meterLink device.

 @param device FLIRMeterLinkDevice
 */
- (void)deviceDiscovered:(FLIRMeterLinkDevice *) device;

/**
 This method is invoked when a meterLink device is turned off or goes out of range
 A new startScanning is needed to discover new devices after deviceDisappeared has been called
 @param device FLIRMeterLinkDevice
 */
- (void)deviceDisappeared:(FLIRMeterLinkDevice *) device;

/**
 This method is invoked when a meterLink device has been succesfully connected and is ready to send data

 @param device FLIRMeterLinkDevice
 */
- (void)deviceConnected:(FLIRMeterLinkDevice *) device;

/**
 This method is invoked when the meterLink device is disconnected

 @param device FLIRMeterLinkDevice
 */
- (void)deviceDisconnected:(FLIRMeterLinkDevice *) device;

/**
 This method is invoked when the meterLink is updating its data values or status

 @param device FLIRMeterLinkDevice
 */
- (void)deviceUpdated:(FLIRMeterLinkDevice *) device;

/**
 This method is invoked when the current state of a meterLink connection has been updated

 @param state FLIRMeterLinkState
 */
- (void)mlState:(FLIRMeterLinkState) state;

/** Represents a logged/recorded data reading file
 * @param device FLIRMeterLinkDevice
 * @param data data
 * @param csvString recording in csv format
 */
- (void) dataRecordingReceived:(FLIRMeterLinkDevice *) device data:(id) data CSVString:(NSString *) csvString;

/** JPEG image callback function
 * @param device FLIRMeterLinkDevice
 * @param image Pointer to the JPEG file in memory
 */
- (void) imageReceived:(FLIRMeterLinkDevice *) device image:(NSData *) image;

/** File transfer progress callback function
 * Transfer unit type is unspecified and may be any of bytes, packets, newlines etc.
 * @param type File type being downloaded
 * @param current Number of units downloaded of current file
 * @param total File size in number of units of current file
 */
- (void) transferProgress:(FLIRMeterLinkDevice *) device filetype: (FLIRMeterLinkFileType) type progressCurrent:(int) current progressTotal:(int) total;

/** Parse error callback function
 * @param error Specific error code
 */
- (void) parserError:(FLIRMeterLinkDevice *) device parseError:(FLIRMeterLinkParseError) error;

/** Handle raw MeterLink packet
 */
- (void) receivedPacket:(NSData *)data;
@end


/**
 Entry point to FLIR meterLink. Commands should only be issued when its state is FLIRMeterLinkStatePoweredOn.
 */
@interface FLIRMeterLinkManager : NSObject

/**
 The initialization call. The events of twill be sent to the delegate

 @param delegate The delegate that will receive events
 @return return the FLIRMeterLinkManager instance
 */
- (instancetype)initWithDelegate:(id<FLIRMeterLinkDelegate>)delegate;

/**
 Start scanning for meterLink devices
 */
- (void) startScanning;

/**
 Stop scanning for meterLink devices
 */
- (void) stopScanning;

/**
 Connect to a meterLink device and initiate sending of values and status from the connected device
 The MeterLink device DM93 will disconnect itself after 30 seconds, this is an issue with the device itself.

 @param device FLIRMeterLinkDevice
 */
- (void) connectDevice:(FLIRMeterLinkDevice *) device;

/**
 Disconnect a meterLink device

 @param device FLIRMeterLinkDevice
 */
- (void) disconnectDevice:(FLIRMeterLinkDevice *) device;

/**
 set the decimal separator for a formatted string from a Channel's value

 @param decimalSeparator
 */
- (void) setDecimalSeparator:(NSString *)decimalSeparator;
@end
