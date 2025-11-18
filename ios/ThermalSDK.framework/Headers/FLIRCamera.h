//
//  FLIRCamera.h
//  FLIR Thermal SDK
//
//  Created on 2018-05-07.
//  Copyright © 2019 Teledyne FLIR. All rights reserved.
//
//  Manages any FLIR camera.
//

#pragma once

#import <Foundation/Foundation.h>
#import <CoreGraphics/CoreGraphics.h>

#import "FLIRIdentity.h"
#import "FLIRRemoteControl.h"
#import "FLIRThermalImage.h"

@class FLIRStream;

/** Streaming options */
typedef NS_OPTIONS(NSUInteger, StreamingOptions) {
    STREAMING_NO_OPENGL = 1
};

/**
 *  Block to accept a thermal image from a live stream.
 */
typedef void (^ FLIRThermalImageBlock)(FLIRThermalImage * _Nonnull);

/**
 ChargingState
 */
typedef NS_ENUM(NSUInteger, FLIRAuthenticationStatus)
{
    pending, //will encapsulate both success upload and waiting to be approved
    approved,
    unknown
};

@class FLIRCamera;

/**
 *  Delegate to return events from a connectad camera.
 */
@protocol FLIRDataReceivedDelegate <NSObject>

#pragma mark - Required event handlers
/**
 *  This event is raised when the camera is disconnected unexpectedly, if this method is
 *  implemented in the app, and if the delegate has been initialised.
 *
 *  @param  camera  The camera.
 *  @param  error   An error code.
 */
-(void)onDisconnected:(FLIRCamera *_Nonnull) camera withError:(nullable NSError *)error;

@optional
#pragma mark - Optional event handlers

@end

@class FLIRCameraImport;

/**
 *  The interface to a FLIR camera of any kind.
 *
 *  Note: This class is not guaranteed to be thread-safe, so calls must be synchronized if a Camera object is accessed from multiple threads.
 *
 */
@interface FLIRCamera : NSObject

+ (NSString* _Nonnull)getFrom:(FLIRIdentity * _Nonnull)identity code:(NSUInteger)code;

/**
 *  A delegate to handle the events in FLIRDataReceivedDelegate.
 */
@property (weak, nullable) id<FLIRDataReceivedDelegate> delegate;

/**
 *  Initialize a camera instance with specified identity.
 *
 *  Important
 *  During development do not call `-[FLIRCamera authenticate:trustedConnectionName:]`
 *  with the same "trustedConnectionName" between two application installs.
 *  During the call to `-[FLIRCamera authenticate:trustedConnectionName:]` authentication files are create and
 *  uploaded with the "trustedConnectionName" as the "key" to the camera,
 *  new authentication files can't be used with the same "trustedConnectionName"
 *  to connect to the camera.
 *  We recommended generate a trustedConnectionName and store it as `Preferences`
 *
 * @param identity the identity of the camera instance to be initialized.
 */
-(FLIRAuthenticationStatus) authenticate:(FLIRIdentity *_Nonnull)identity trustedConnectionName:(NSString *_Nullable) name;
/**
 *  Connect with the camera. This function is blocking.
 *
 *  @param identity the identity of the camera instance to connect to.
 */
-(BOOL)connect:(FLIRIdentity * _Nonnull)identity error:(out NSError * _Nullable * _Nonnull)error;

/**
 *  Connect with the camera. This function is blocking.
 *
 *  @param identity the identity of the camera instance to connect to.
 *  @param code a code that can be present in some cameras when connecting.
 */
- (BOOL)connect:(FLIRIdentity * _Nonnull)identity code: (NSUInteger)code error:(out NSError * _Nullable * _Nonnull)error;

/**
 *  Disconnect from the camera.  This function is blocking.
 *  Disconnecting from a Flir One Edge will also disconnect from the camera's WiFi hotspot
 */
-(void)disconnect;

/**
 *  Get the connection status.
 */
-(BOOL)isConnected;

/**
 *  Get the connected camera identity.
 *
 *  @return The identity of the currently connected camera or null if no camera is connected.
 */
-(nullable FLIRIdentity *)getIdentity;

/**
 *  Get camera remote controller.
 *
 *  @return nullptr returned if the camera isn't in connected state
 */
-(nullable FLIRRemoteControl *)getRemoteControl;

-(nullable FLIRCameraImport *) getImport;

/**
 *  Return all streams availbable on this camera, see @ref FLIRStream.
 *  @note the implementation for thermal streaming for network cameras is still in experimental stage.
 */
- (NSArray<FLIRStream *> * _Nonnull)getStreams;

/**
 *  Check if we're in frame grabbing state.
 */
-(BOOL)isGrabbing;

/**
 *  Get license information for software used in camera, if available
 *  The returned data is utf8 encoded text
 */

- (NSData* _Nullable)getLicenseDataData;

@end
