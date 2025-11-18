//
//  FLIRFocus.h
//  ThermalSDK
//
//  Created by FLIR on 2020-07-02.
//  Copyright © 2020 Teledyne FLIR. All rights reserved.
//

#import <Foundation/Foundation.h>

/**
 * Determines if camera should focus on near or far objects or control focus automatically (depending on the scene).
 */
typedef NS_ENUM(NSUInteger, FLIRDistance) {
    NEAR,
    FAR,
    AUTO
};

/**
 * Autofocus modes.
 */
typedef NS_ENUM(NSUInteger, FLIRAutoFocusMode) {
    /**
     * Allows to specify focus distance parameter.
     */
    NORMAL,
    /**
     * Camera automatically and continuously manages focus.
     */
    CONTINUOUS
};

/**
 * Defines the focus calculation method. I.e. based on maximizing the image contrast or based on laser distance measurement.
 */
typedef NS_ENUM(NSUInteger, FLIRCalculationMethod) {
    /**
     * calculation based on maximizing the image contrast
     */
    CONTRAST,
    /**
     * calculation based on laser distance measurement
     */
    LASER,
    /**
     * calculation method automatically chosen by the camera (if supported)
     */
    AUTOMATIC
};

/** focus settings for the camera */
@interface FLIRFocus : NSObject

/** Triggers a single autofocus / AF event. */
-(BOOL)autofocus: (out NSError * _Nullable *_Nullable)error;
/** get distance selection */
- (FLIRDistance)getDistance;
/** set distance selection */
- (BOOL)setDistance: (FLIRDistance)distance error: (out NSError * _Nullable *_Nullable)error;
/** get autofocus mode */
- (FLIRAutoFocusMode)getMode;
/** set autofocus mode */
- (BOOL)setMode: (FLIRAutoFocusMode)mode error: (out NSError * _Nullable *_Nullable)error;
/** get focus calculation method */
- (FLIRCalculationMethod)getCalculationMethod;
/** set focus calculation method */
- (BOOL)setCalculationMethod: (FLIRCalculationMethod)method error: (out NSError * _Nullable *_Nullable)error;

@end
