//
//  FLIRThermalDelta.h
//  FLIR Thermal SDK
//
//  Copyright © 2020 Teledyne FLIR. All rights reserved.
//

#pragma once
#import <Foundation/Foundation.h>
#import "FLIRThermalValue.h"

// should match atlas::image::measurements::DeltaMemberValueType

/**
 *  Defines delta member value type.
 */
typedef NS_ENUM(NSUInteger, DeltaMemberValueType) {
    /**  Min marker value for the specified member type.
     *   Valid for member types: rectangle, ellipse, line.
     */
    typeMin,
    /**  Max marker value for the specified member type.
     *   Valid for member types: rectangle, ellipse, line.
     */
    typeMax,
    /**  Average value for the specified member type.
     *   Valid for member types: rectangle, ellipse, line.
     */
    typeAvg,
    /**  Arbitrary value.
     *   Valid for member types: spot, reference temperature.
     */
    typeValue
};

/**
 * Represents an Thermal delta with additional state information. It is used to obtain Thermal data
 * from a ThermalImage or any MeasurementShape added to the image.
 */
@interface FLIRThermalDelta : NSObject

/**
 * Initialize a thermal delta value with specified temperature difference and unit (useful for tests)
 *
 * @param value Temperature difference.
 * @param unit Temperature unit.
 */
- (instancetype)initWithValue:(double) value andUnit:(TemperatureUnit) unit;

/**
 *  Gets the difference/delta value.
 */
@property (readonly, nonatomic) double value;

/**
 *  Gets the temperature unit.
 */
@property (readonly, nonatomic) TemperatureUnit unit;

/**
 *  Gets the state of the value.
 */
@property (readonly, nonatomic) ThermalValueState state;

/**
 *  Gets a formatted string representing the value.
 */
- (NSString *)description;

/**
 *  Convenience function to test if two FLIRThermalDelta have the same temperature difference with imprecise floating-point comparison.
 *  @param other The second (right hand side) value to compare.
 *  @param absError The margin of error in degrees Kelvin.
 */
- (BOOL)areNear:(id)other absError: (double)absError;

@end
