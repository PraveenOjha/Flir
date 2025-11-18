//
//  FLIRCalibration.h
//  ThermalSDK
//
//  Created by FLIR on 2020-07-02.
//  Copyright © 2020 Teledyne FLIR. All rights reserved.
//

#pragma once

/**
 Shutter state
 */

typedef NS_ENUM(NSUInteger, FLIRShutterState) {
    /// Represents an invalid shutter state. This value should be treated as an error.
    invalid,
    
    /// Represents the "device powered off" shutter state.
    off,
    
    /// Represents the "device powered on" state of the hardware shutter.
    on,
    
    /// Represents the NUC state of the hardware shutter.
    nuc,
    
    /// Indicates that a corrupted/invalid shutter state was detected.
    bad,
    
    /// Indicates a unknown state
    unknown_state
};

/**
 NUC state
 */

typedef NS_ENUM(NSUInteger, FLIRNucState) {
    /**
     * Indicates that the *last* NUC (Non-Uniform Calibration) is invalid.
     */
    INVALID,
    /**
     * Indicates that there is NUC activity in progress.
     */
    PROGRESS,
    /**
     * Indicates that thermal pixel values are radiometrically calibrated.
     */
    VALID_RAD,
    /**
     * Indicates that thermal pixels are calibrated enough to yield an image, but are not yet radiometrically calibrated.
     */
    VALID_IMG,
    /**
     * Indicates that an NUC is desired.
     */
    DESIRED,
    /**
     * Indicated that approximate radiometric values are being given.
     */
    RAD_APPROX,
    /**
     * Indicates that a corrupted/invalid NUC state was detected.
     */
    BAD_STATE,
    /**
     * Indicates a unknown state.
     */
    UNKNOWN
};


/**
* Camera calibration control interface.
*/
@interface FLIRCalibration : NSObject
/** Get calibration state. */
-(BOOL)getActive;
/** Process a one point NUC (Non-uniformity correction) with a black body method. */
-(BOOL)performNUC: (out NSError * _Nullable *_Nullable)error;;
/** Subscribes for camera NUC state notifications. */
-(BOOL)subscribeCalibrationState: (out NSError * _Nullable *_Nullable)error;
/** Revokes subscription for camera NUC state notifications. */
-(void)unsubscribeCalibrationState;
/** Get NUC state
 * @note The state of some cameras (FLIR ONE notably) is `UNKNOWN` until streaming is started.
 */
- (FLIRNucState)getNucState;
/** NUC interval (max interval between each automatic NUC in seconds) */
- (int)getNucInterval;
/** NUC interval (max interval between each automatic NUC in seconds)
 *  For FLIR One setting nucInterval to 0 disables automatic nuc, any other value enables automatic nuc
 */
-(BOOL)setNucInterval: (int)nucInterval error: (out NSError * _Nullable *_Nullable)error;

/** Subscribes for shutter state notifications */
-(BOOL)subscribeShutterState: (out NSError * _Nullable *_Nullable)error;
/** Revokes subscription for shutter state notifications */
-(void)unsubscribeShutterState;

/** Get shutter state
 * @note The state of some cameras (FLIR ONE notably) is `unknown_state` until streaming is started.
 */
- (FLIRShutterState)getShutterState;
@end

