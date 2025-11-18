//
//  MeasurementParameters.h
//  FLIR Thermal SDK
//
//  Copyright © 2019 Teledyne FLIR. All rights reserved.
//

#import <Foundation/Foundation.h>

/**
 Encapsulates the object parameters for a MeasurementShape object
 */
@interface FLIRMeasurementParameters : NSObject
/**
 *  NS_UNAVAILABLE
 *
 *  @return NS_UNAVAILABLE
 */
- (instancetype)init NS_UNAVAILABLE;

/**
 *  Gets or sets the distance to the object.
 */
@property (readwrite) double distance;

/**
 *  Gets or sets the default emissivity for the Thermal Image.
 */
@property (readwrite) double emissivity;

/**
 *  Gets or sets the reflected temperature.
 */
@property (readwrite) double reflectedTemperature;

/**
 *  Gets or sets a value indicating whether to use custom object parameters.
 */
@property (readwrite) bool useCustomObjectParameters;

@end
