//
//  FLIRThermalParameters.h
//  FLIR Thermal SDK
//
//  Copyright © 2019 Teledyne FLIR. All rights reserved.
//

#import <Foundation/Foundation.h>
#import "FLIRThermalValue.h"

/** FLIRThermalParameters */
@interface FLIRThermalParameters : NSObject

/** Gets or sets the default emissivity for the IR Image. */
@property (nonatomic, readwrite) float objectEmissivity;
/** Gets or sets the distance from camera to focused object. */
@property (nonatomic, readwrite) float objectDistance;
/** Gets or sets the reflected temperature. */
@property (nonatomic, readwrite, nonnull) FLIRThermalValue* objectReflectedTemperature;
/** Gets or sets the atmospheric temperature. */
@property (nonatomic, readwrite, nonnull) FLIRThermalValue* atmosphericTemperature;
/** Gets or sets the external optics temperature. */
@property (nonatomic, readwrite, nonnull) FLIRThermalValue* externalOpticsTemperature;
/** Gets or sets the external optics transmission. */
@property (nonatomic, readwrite) float externalOpticsTransmission;
/** Gets or sets the atmospheric transmission. */
@property (nonatomic, readwrite) float atmosphericTransmission;
/** Gets or sets the relative humidity (0.0 - 100.0). */
@property (nonatomic, readwrite) float relativeHumidity;

@end
