//
//  FLIRRemoteControl.h
//  FLIR Thermal SDK
//
//  Created on 2018-09-05.
//  Copyright © 2019 Teledyne FLIR. All rights reserved.
//
//  Camera remote control. Can read, manipulate and listen for events from a Camera.
//

#pragma once

#import <Foundation/Foundation.h>
#import "FLIRIdentity.h"
#import "FLIRBattery.h"
#import "FLIRCalibration.h"
#import "FLIRFusionController.h"
#import "FLIRCameraImport.h"
#import "FLIRThermalValue.h"

/**
 *  Path to a stored image (or IR+visual pair of images) on a camera.
 */
@interface FLIRStoredImage : NSObject
/** Reference to the thermalimage */
@property (nonatomic, nonnull, readwrite) FLIRFileReference *thermalImage;
/** Reference to the visualimage. */
@property (nonatomic, nullable, readwrite) FLIRFileReference *visualImage;
@end


/**
 *  Describes a state of a FLIR camera.
 */
typedef NS_ENUM(NSUInteger, FLIRCameraState)
{
    /** Camera is not ready */
    NOT_READY,
    /** Camera is cooling down */
    COOLING,
    /** Camera is ready */
    READY
};

/**
 *  Progress status for firmware update. (Internal use only.)
 */
typedef NS_ENUM(NSUInteger, FLIRFirmwareUpdateStatus)
{
    /// no firmware update is started
    US_noUpdate,
    /// information on firmware update progress
    US_info,
    /// the firmware update failed, camera will be rebooted
    US_failure,
    /// the firmware update was successful, camera will be rebooted
    US_success,
    /// the firmware update is ongoing, camera is rebooting
    US_rebooting,
    /// the firmware update starts writing a package
    US_startWritingPackage,
    /// the firmware update has written a package
    US_doneWritingPackage,
    /// there was a failure writing a package
    US_failureWritingPackage,
    /// the firmware update starts
    US_startExecutingUpdate,
    /// the firmware update is done
    US_doneExecutingUpdate,
    /// the firmware update cannot start, since the camera is in normal mode instead of upgrade mode
    US_failureInvalidMode,
    /// the package write failed, retry
    US_retryWritingPackage
};

/// Time format for time display on camera
typedef NS_ENUM(NSUInteger, FLIRTimeDisplayFormat)
{
    /// 12h (e.g. 1.00 pm)
    TF_t12H,
    /// 24h (e.g. 13.00)
    TF_t24H
};

/// Date format for date display on camera
typedef NS_ENUM(NSUInteger, FLIRDateDisplayFormat)
{
    /// YYYY-MM-DD
    DF_ymd,
    /// MM/DD/YYYY
    DF_mdy,
    /// DD/MM/YYYY
    DF_dmy
};

/// Operating modes used by cameras for firefighting
typedef NS_ENUM(NSUInteger, FLIRFireCameraMode)
{
    FLIRFireCameraMode_basic,           ///< Default mode for firefighting. Basic mode on K45, K55. TI Basic NFPA on K65.
    FLIRFireCameraMode_search,         ///< Search mode.
    FLIRFireCameraMode_detection,   ///< Detection mode.
    FLIRFireCameraMode_fire,           ///< Fire mode. Similar to Basic/NFPA mode but with a higher threshold for heat colorization.
    FLIRFireCameraMode_whiteHot,     ///< White hot mode.
};

@interface FLIRFireCameraModeObject : NSObject
@property (nonatomic, assign) FLIRFireCameraMode mode;
@end

@interface FLIRFireCameraTemperatureRange : NSObject
@property (nonatomic, assign) NSInteger minKelvin;
@property (nonatomic, assign) NSInteger maxKelvin;
@property (nonatomic, assign) BOOL isAuto;
@end

@interface FLIRFireCameraModeController : NSObject
- (BOOL)getAvailable;
- (BOOL)setAvailable:(BOOL)visible error:(out NSError * _Nullable * _Nullable)error;
- (BOOL)getReadOnly;
- (FLIRFireCameraTemperatureRange * _Nullable)getTemperatureRange:(out NSError * _Nullable * _Nullable)error;
@end

/// GUI HotSpot shapes used by cameras for firefighting.
typedef NS_ENUM(NSUInteger, FLIRHotSpotShape)
{
    FLIRHotSpotShape_disabled,       ///< Disabled.
    FLIRHotSpotShape_nfpa,           ///< NFPA/basic mode style.
    FLIRHotSpotShape_crosshair       ///< Crosshair style.
};

typedef NS_ENUM(NSUInteger, FLIRSingleTriggerBehaviour)
{
    FLIRSingleTriggerBehaviour_save,           ///< save a still image
    FLIRSingleTriggerBehaviour_freeze,         ///< freeze image
    FLIRSingleTriggerBehaviour_none,           ///< no effect
    FLIRSingleTriggerBehaviour_record           ///< record video
};

@interface FLIRDualTriggerBehaviour : NSObject
@property (nonatomic, assign) FLIRSingleTriggerBehaviour shortPress;
@property (nonatomic, assign) FLIRSingleTriggerBehaviour longPress;
@end

/**
 *  Internal interface for firmware update of FLIR One
 */
@interface FLIRFirmwareUpdate : NSObject
@end

/**
 *  Video recorder for network cameras
 */
@interface FLIRVideoRecorder : NSObject
- (BOOL)start:(out NSError * _Nullable *_Nullable)error;
- (BOOL)stop:(out NSError * _Nullable *_Nullable)error;

@property (nonatomic, readonly) BOOL isRecording;

- (BOOL)subscribeVideoRecorder:(out NSError * _Nullable *_Nullable)error;
- (void)unsubscribeVideoRecorder;

@end

/**
 * Controller for general camera settings
 */
@interface FLIRCameraUISettingsController : NSObject

- (TemperatureUnit)getTemperatureUnit;
- (BOOL)setTemperatureUnit:(TemperatureUnit)unit error:(out NSError * _Nullable * _Nullable)error;
- (FLIRDateDisplayFormat)getDateDisplayFormat;
- (BOOL)setDateDisplayFormat:(FLIRDateDisplayFormat)format error:(out NSError * _Nullable * _Nullable)error;
- (FLIRTimeDisplayFormat)getTimeDisplayFormat;
- (BOOL)setTimeDisplayFormat:(FLIRTimeDisplayFormat)format error:(out NSError * _Nullable * _Nullable)error;

@end

@interface FLIRFireCameraController : NSObject

- (FLIRFireCameraMode)getCurrentMode;
- (BOOL)setCurrentMode:(FLIRFireCameraMode)mode error:(out NSError * _Nullable * _Nullable)error;
- (NSArray<FLIRFireCameraModeObject *> *_Nullable)getAllModes:(out NSError * _Nullable * _Nullable)error;
- (NSArray<FLIRFireCameraModeObject *> *_Nullable)getAvailableModes:(out NSError * _Nullable * _Nullable)error;

- (FLIRHotSpotShape)getHotSpotShape;
- (BOOL)setHotSpotShape:(FLIRHotSpotShape)shape error:(out NSError * _Nullable * _Nullable)error;

- (BOOL)getShowTemperatureBar;
- (BOOL)setShowTemperatureBar:(BOOL)show error:(out NSError * _Nullable * _Nullable)error;
- (BOOL)getShowReferenceBar;
- (BOOL)setShowReferenceBar:(BOOL)show error:(out NSError * _Nullable * _Nullable)error;
- (BOOL)getShowDigitalReadout;
- (BOOL)setShowDigitalReadout:(BOOL)show error:(out NSError * _Nullable * _Nullable)error;

- (FLIRDualTriggerBehaviour *_Nullable)getDualTriggerBehaviour:(out NSError * _Nullable *_Nullable)error;
- (BOOL)setDualTriggerBehaviour:(FLIRDualTriggerBehaviour *_Nonnull)behaviour error:(out NSError * _Nullable *_Nullable)error;
- (NSArray<FLIRDualTriggerBehaviour *> *_Nullable)getAvailableDualTriggerBehaviours:(out NSError * _Nullable *_Nullable)error;

- (BOOL)getBlackBox;
- (BOOL)setBlackBox:(BOOL)blackBox error:(out NSError * _Nullable * _Nullable)error;

- (FLIRFireCameraModeController *_Nullable)getFireCameraModeController:(FLIRFireCameraMode)mode error:(out NSError * _Nullable * _Nullable)error;

- (BOOL)getAllowChanges;
- (BOOL)subscribeAllowChanges:(out NSError * _Nullable * _Nullable)error;
- (void)unsubscribeAllowChanges;
- (BOOL)resetParameters:(out NSError * _Nullable * _Nullable)error;

@end

/**
 *  Camera image storage control interface.
 */
@interface FLIRStorage : NSObject

/**
* Get the latest image that was stored in the camera.
* This property will be updated when the file is completely written to storage,
* so there may be some delay from when the picture is taken till the property is updated.
* Note: this call is blocking.
*/
-(nullable FLIRStoredImage *) getLastStoredImage:(out NSError * _Nullable *_Nullable)error;

/** Subscribes for camera last stored image notifications. */
-(BOOL) subscribeLastStoredImage: (out NSError * _Nullable *_Nullable)error;
/** Revokes subscription for last stored image. */
-(void) unsubscribeLastStoredImage;


/** Check if camera has snapshot capability */
- (BOOL)snapshotIsAvailable;

/**  Request camera to store an image into the  image storage and return it.
 */
- (FLIRStoredImage * _Nullable)snapshot:(out NSError * _Nullable * _Nullable)error;

@end

/**
 *  Delegate to return events from a connectad camera.
 */
@protocol FLIRRemoteDelegate <NSObject>

@optional

#pragma mark - Battery
/** Called when battery charging state changes. */
-(void)ChargingStateChanged:(FLIRChargingState) state;
/** Called when remaining battery power changes. */
-(void)PercentageChanged:(int) percent;

#pragma mark - Calibration
/** Called when a NUC state changes. */
-(void)CalibrationStateChanged:(BOOL) isActive;
/** Called when a NUC state changes, with the NUC state as parameter */
- (void)nucStateChanged: (FLIRNucState)state;
/** Called when   shutter state changes */
- (void)shutterStateChanged: (FLIRShutterState)state;

#pragma mark - Storage
/** Called when new image was taken on the camera. */
-(void)lastStoredImageChanged:(FLIRStoredImage *_Nonnull) laststore;

/** Called when state is changing, if subscribing */
- (void)cameraStateChanged: (FLIRCameraState)newState;

/** Called when cameraInformation is changing, if subscribing */
- (void)cameraInformationChanged: (FLIRCameraInformation*_Nonnull)newInformation;

/** Called when laserOn is changing, if subscribing */
- (void)cameraIsLaserOnChanged: (BOOL)newIsLaserOn;

#pragma mark - TemperatureRange
/** Called when selected temperature range changes, if subscribing */
- (void)selectedTemperatureRangeIndexChanged: (int)index;

#pragma mark - FusionController
/** Called when fusion activeChannel is changing, if subscribing */
- (void)activeChannelChanged: (FLIRChannelType)newActiveChannel;

#pragma mark - ScaleController
/** Called when scale controller auto adjust is chaning, if subscribing */
- (void)autoAdjustChanged: (BOOL)autoAdjust;

/** Called when status changes in an ongoing firmware update */
-(void)updateStatusChanged:(FLIRFirmwareUpdateStatus) status;

#pragma mark - VideoRecorder
/** Called when recording has started or stopped */
- (void)videoRecorderChanged:(BOOL)isRecording;

#pragma mark - FireCameraController
/// Called when allowChanges is changed.
- (void)allowChangesChanged:(BOOL)allowChanges;


@end

@class FLIRTemperatureRange;
@class FLIRSystem;
@class FLIRFocus;
@class FLIRPaletteController;
@class FLIRFusionController;
@class FLIRMeasurementsController;
@class FLIRScaleController;
@class FLIROverlayController;

/**
 *  Camera remote control. Can read, manipulate and listen on events from a Camera.
 */
@interface FLIRRemoteControl : NSObject

/**
 *  A delegate to handle the events.
 */
@property (nonatomic, assign, nullable) id<FLIRRemoteDelegate> delegate;

/**
 *  Fetch information about that device.
 *
 *  @note This is a synchronous call to the camera, which blocks until the result is retrieved or an error occurs.
 *  @param error Out parameter filled with an error object on failure, or nil on success.
 *  @return The result object on success, or nil on failure.
 */
-(nullable FLIRCameraInformation*)getCameraInformation:(out NSError * _Nullable * _Nullable)error;

/** subscribe to changes on camera information, delegate will be called when camera information changes */
- (BOOL)subscribeCameraInformation: (out NSError * _Nullable *_Nullable)error;

/**
 * Get the Battery interface to monitor camera battery status.
 *
 * @return Battery object if it's available, or nil if the Camera model has no battery support.
 */
-(nullable FLIRBattery *) getBattery;

/**
 * Get the camera calibration control interface.
 *
 * @return Calibration object if it's available, or nil if the Camera model has no calibration support.
 */
-(FLIRCalibration* _Nullable) getCalibration;

/**
 * Get the Storage interface to interact with camera storage (i.e. internal storage or SD card).
 *
 * @return Storage object if it's available, or nil if the Camera model has no storage support.
 */
-(FLIRStorage* _Nullable) getStorage;

/**
 * Check if the camera is ready for connecting a stream.
 */
- (FLIRCameraState)getCameraReady;

/** subscribe to changes on camera state, delegate will be called when camera state changes */
- (BOOL)subscribeCameraState: (out NSError * _Nullable *_Nullable)error;

/** Check if the laser is on */
- (BOOL)getLaserOn;
/** turn on or of the laser */
- (BOOL)setLaserOn: (BOOL)laserOn error: (out NSError * _Nullable *_Nullable)error;

/** subscribe to changes on laser on, delegate will be called when laser on changes */
- (BOOL)subscribeIsLaserOn: (out NSError * _Nullable *_Nullable)error;
/** unsubscribe to changes on laser on */
- (void)unsubscribeIsLaserOn;

/** get the temperature range interface */
- (FLIRTemperatureRange * _Nullable)getTemperatureRange;
/** get remote system parameters interface */
- (FLIRSystem* _Nullable)getSystem;
/** get remote focus parameters interface */
- (FLIRFocus* _Nullable)getFocus;
/** get remote palette controller interface */
- (FLIRPaletteController * _Nullable)getPaletteController;
/** get remote fusion controller interface */
- (FLIRFusionController * _Nullable)getFusionController;
/** get remote measurements controller interface */
- (FLIRMeasurementsController * _Nullable)getMeasurementsController;
/** get the scale controller interface */
- (FLIRScaleController * _Nullable)getScaleController;
/** get the  overlay controller interface */
- (FLIROverlayController * _Nullable)getOverlayController;
/** get firmware update (only for internal use) */
- (FLIRFirmwareUpdate * _Nullable)getFirmwareUpdate;
/** get video recorder */
- (FLIRVideoRecorder * _Nullable)getVideoRecorder;
/** get camera settings controller */
- (FLIRCameraUISettingsController * _Nullable)getCameraUISettingsController;
/// get fire camera controller
- (FLIRFireCameraController * _Nullable)getFireCameraController;
@end
