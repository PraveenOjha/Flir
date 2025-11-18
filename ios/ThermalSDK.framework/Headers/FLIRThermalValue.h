//
//  ThermalValue.h
//  FLIR Thermal SDK
//
//  Copyright © 2019 Teledyne FLIR. All rights reserved.
//

#import <Foundation/Foundation.h>

/**  Specifies the supported temperature units. */
typedef NS_ENUM(NSUInteger, TemperatureUnit)
{
    /** The Celsius temperature scale was previously known as the centigrade scale. */
    CELSIUS,
    /** Units in Fahrenheit. */
    FAHRENHEIT,
    /** The Kelvin scale is a thermodynamic (absolute) temperature scale where absolute zero, the theoretical absence of all thermal energy, is zero (0 K). */
    KELVIN
};

/**
 *  The possible ThermalValueStates.
 */
typedef NS_ENUM(NSUInteger, ThermalValueState) {
    /** Value is invalid. Usually happens when calculation mask for the particular measure type (i.e. min, max, average) is not enabled. */
    Invalid,
    /** Value is OK. */
    Ok,
    /** Value is too high. */
    Overflow,
    /** Value is too low. */
    Underflow,
    /** Value is outside image. */
    Outside,
    /** Value is unreliable. */
    Warning,
    /** Value is not yet calculated, unstable image after restart/case change. */
    Unstable,
    /** Value is OK + compensated with a reference temperature delta. */
    Delta,
}; // ThermalValueState;

/**
 *  This class represents an IR value with additional information.
 */
@interface FLIRThermalValue : NSObject

/**
 * Initialize an "OK" thermal value with specified temperature value and unit
 *
 * @param value Temperature value.
 * @param unit Temperature unit.
 */
- (instancetype)initWithValue:(double) value andUnit:(TemperatureUnit) unit;

/**
 *  Gets the value.
 */
@property (readonly) double value;

/**
 *  Gets the temperature unit of the value.
 */
@property (readonly) TemperatureUnit unit;

/**
 *  Gets the state of the value.
 */
@property (readonly) ThermalValueState state;

/**
 *  Gets a nicely formatted string representing the value.
 */
- (NSString *)description;

/** @brief Convenience function to test if two @ref FLIRThermalValue "ThermalValues" practically the same temperature with imprecise floating-point comparison.
 *  @param other The second (right hand side) value to compare.
 *  @param absError The margin of error in degrees Kelvin.
 */
- (BOOL)areNear:(id)other absError: (double)absError;

/** @brief Return the corresponding thermal value in another unit */
- (FLIRThermalValue*)asCelsius;

/** @brief Return the corresponding thermal value in another unit */
- (FLIRThermalValue*)asKelvin;

/** @brief Return the corresponding thermal value in another unit */
- (FLIRThermalValue*)asFahrenheit;


@end
