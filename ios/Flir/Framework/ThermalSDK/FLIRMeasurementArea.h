//
//  FLIRMeasurementArea.h
//  FLIR Thermal SDK
//
//  Copyright © 2019 Teledyne FLIR. All rights reserved.
//

#import "FLIRMeasurementMarker.h"

@class FLIRMeasurementDimensions;

/**
 *  Represents a measurement area object.
 */
@interface FLIRMeasurementArea : FLIRMeasurementMarker

/**
 *  Gets the width of this measurement object.
 */
@property (nonatomic, readonly) int width;

/**
 *  Gets the height of this measurement object.
 */
@property (nonatomic, readonly) int height;

/**
 *  Gets the measurement area dimensions
 */
- (FLIRMeasurementDimensions * _Nullable)getMeasurementDimensions;

/**
 *  Set or get the flag for area calculations
 */

@property (nonatomic, assign) BOOL areaCalc;

@end
