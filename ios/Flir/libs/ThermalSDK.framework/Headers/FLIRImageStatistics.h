//
//  FLIRImageStatistics.h
//  Spartacus
//
//  Copyright © 2019 Teledyne FLIR. All rights reserved.
//

#import <Foundation/Foundation.h>
#import <CoreGraphics/CGGeometry.h>
#import "FLIRThermalValue.h"
#import "FLIRThermalDelta.h"

/**
 *  Provides statistics for Thermal data for the ThermalImage.
 *  Defines such values as minimum, maximum and position, where they were measured, average and standard deviation.
 */
@interface FLIRImageStatistics : NSObject

/**
 *  Gets calculated Minimum temperature value.
 *
 *  @return The minimum value.
 */
- (FLIRThermalValue * _Nonnull)getMin;

/** 
 *  Gets calculated Maximum temperature value.
 *
 *  @return The maximum value.
 */
- (FLIRThermalValue * _Nonnull)getMax;

/**
 *  Gets calculated Average value of the temperature data associated with the ThermalImage.
 *
 *  @return The average value.
 */
- (FLIRThermalValue * _Nonnull)getAverage;

/**
 *  Gets calculated Standard Deviation value of the temperature data associated with the ThermalImage.
 *
 *  @return The standard deviation value.
 */
- (FLIRThermalDelta * _Nonnull)getStandardDeviation;

/**
 *  Get the position (x,y-coordinate) to the pixel with the highest temperature.
 *
 *  @return A (x,y) point.
 */
- (CGPoint)getHotSpot;

/**
 *  Get the position (x,y-coordinate) to the pixel with the lowest temperature.
 *
 *  @return A (x,y) point.
 */
- (CGPoint)getColdSpot;

@end
