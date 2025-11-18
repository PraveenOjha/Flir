//
//  FLIRCameraEvent.h
//  FLIR Thermal SDK
//
//  Copyright © 2019 Teledyne FLIR. All rights reserved.
//

#import <Foundation/Foundation.h>
#import "FLIRCameraDeviceInfo.h"
#import "FLIRIdentity.h"

/**
 *
 * Camera event.
 *
 * Handles events from a remotely connected camera
 *
 */

@interface FLIRCameraEvent : NSObject

/**
 Event

 - ImageSavedInCamera: the event
 */
typedef NS_ENUM(NSInteger, CameraEvent) {
    /** Image was saved in the camera */
    ImageSavedInCamera = 0,
    /** Connection was lost */
    CameraConnectionLost = 1, // Always on
};

/**
 * Event
 * CameraConnectionLost
 *
 * CameraConnectionLost event will be posted to NSNotificationCenter defaultCenter when camera is lost
 * This event is always enabled when listening for ImageSavedInCamera
 * event is separated from the discovery events
 * object param always nil
 *
 *    [[NSNotificationCenter defaultCenter] addObserver:self
 *                                             selector:@selector(CameraConnectionLost:)
 *                                                 name:@"CameraConnectionLost"
 *                                               object:nil];
 *
 */

/**
 *  Create a camera event object
 *
 *  @param cameraDeviceInfo the camera device to listen for events from
 *
 *  @return FLIRCameraEvent object
 */
- (instancetype) initWithCameraDeviceInfo:(FLIRCameraDeviceInfo *) cameraDeviceInfo;

/**
 *  Create a camera event object
 *
 *  @param ipAddress the camera ipAddress to listen for events from
 *
 *  @return FLIRCameraEvent object
 */
- (instancetype) initWithCameraIpAddress:(NSString *) ipAddress;

/**
 *  Create a camera event object
 *
 *  @param identity the camera identity to listen for events from
 *
 *  @return FLIRCameraEvent object
 */
- (instancetype) initWithIdentity:(FLIRIdentity *) identity;

/**
 * start listen for events
 *
 * posts the event to NSNotificationCenter defaultCenter
 *
 * event with id ImageSavedInCamera will post a named event "ImageSavedInCamera" to the notification center
 *
 * the notification object is a `FLIRFileReference` instance containing the full path to the image that was saved
 *
 * this image can be downloaded from the camera via FTP
 *
 *
 * typedef NS_ENUM(NSInteger, CameraEvent) {
 *
 * - ImageSavedInCamera = 0,
 * - CameraConnectionLost = 1, // Always on
 *
 *};
 *
 *	[[NSNotificationCenter defaultCenter] addObserver:self
 *											 selector:@selector(imageSavedInCamera:)
 *												 name:@"ImageSavedInCamera"
 *											   object:nil];
 *
 *	self.cameraEvent = [[FLIRCameraEvent alloc] initWithCameraDeviceInfo:self.cameraDeviceInfo];
 *	[self.cameraEvent listenForEvent:ImageSavedInCamera];
 *
 * Event
 * CameraConnectionLost Always on when listening for ImageSavedInCamera
 *
 * CameraConnectionLost event will be posted to NSNotificationCenter defaultCenter when camera is lost
 *
 * This event is always enabled when listening for ImageSavedInCamera
 *
 * event is separated from the discovery events
 *
 * object param always nil
 *
 *  [[NSNotificationCenter defaultCenter] addObserver:self
 *                                           selector:@selector(CameraConnectionLost:)
 *                                               name:@"CameraConnectionLost"
 *                                             object:nil];
 *
 *  @param eventID the event to listen for
 *
 *  @return true if succesful
 */

- (BOOL) listenForEvent:(CameraEvent) eventID;

/**
 *  stop listen for events
 */
- (void) stopListening;

@end
