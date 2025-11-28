//
//  FLIRScaleController.h
//  ThermalSDK
//
//  Created by FLIR on 2020-09-14.
//  Copyright © 2020 Teledyne FLIR. All rights reserved.
//

#pragma once

@class FLIRRange;

/**
* Camera scale controller interface.
*/

@interface FLIRScaleController : NSObject

/** check if auto adjust is available */
- (BOOL)autoAdjustAvailable;
/**
 * Get auto adjust mode. Defines the adjust mode of the temperature scale on the live screen on the camera.
 * Auto mode (true value): In this mode, the camera is continuously auto-adjusted for the best image brightness and contrast.
 * Manual mode (false value): This mode allows manual adjustments of the temperature span and the temperature level.
 */
- (BOOL)getAutoAdjust;
/** Set auto adjust mode */
- (BOOL)setAutoAdjust:(BOOL)autoAdjust error: (out NSError * _Nullable * _Nullable)error;
/** subscribe to changes of auto adjust mode */
- (BOOL)subscribeAutoAdjust:(out NSError * _Nullable * _Nullable)error;
/** unsubscribe to changes of auto adjust mode */
- (void)unsubscribeAutoAdjust;
/** check if scale active mode is available */
- (BOOL)activeAvailable;
/** get active mode */
- (BOOL)getActive;
/** Enables/disables the scale active mode.*/
- (BOOL)setActive:(BOOL)active error: (out NSError * _Nullable * _Nullable)error;
/** get range for scale */
- (FLIRRange *_Nullable)getRange;
/** set range for scale */
- (BOOL)setRange:(FLIRRange *_Nonnull)range error:(out NSError * _Nullable * _Nullable)error;

@end
