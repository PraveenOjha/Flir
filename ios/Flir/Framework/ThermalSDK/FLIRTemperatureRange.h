//
//  FLIRTemperatureRange.h
//  ThermalSDK
//
//  Created by FLIR on 2020-07-02.
//  Copyright © 2020 Teledyne FLIR. All rights reserved.
//

@class FLIRThermalValue;

/** a temperature range for a camera */
@interface FLIRRange : NSObject

/** lowest thermal value */
@property (nonatomic, retain) FLIRThermalValue* _Nonnull min;
/** highest thermal value */
@property (nonatomic, retain) FLIRThermalValue* _Nonnull max;

@end


@interface FLIRTemperatureRange : NSObject

/** get all available ranges */
- (NSArray<FLIRRange*>* _Nullable) getRanges;
/** get the range used */
- (int)getSelectedIndex;
/** 
 * Set the range used.
 * When 'selectedIndex' is being set on the network camera, the camera responds with a success, but it asynchronously sets itself up.
 * In consequence, the success response doesn't necessarily mean the camera is done with changing the setting.
 * This means that reading the 'selectedIndex' will return the old value for a short while, until the camera sets everything up internally.
 * It is up to the developer to subscribe if it is required.
 * When 'selectedIndex' is being set on the FLIR ONE camera, in order to properly reflect new value in 'image.CameraInformation'
 * the streaming must be started prior to setting the value and also 'FLIRThermalImage.getImage()' needs to be called.
 */
- (BOOL)setSelectedIndex: (int)selectedIndex error: (out NSError * _Nullable *_Nullable)error;
/** Subscribes for selected temperature range notifications. */
- (BOOL)subscribeSelectedIndex: (out NSError * _Nullable *_Nullable) error;
/** Revokes subscription for selected temperature range. */
- (void)unsubscribeSelectedIndex;

@end
