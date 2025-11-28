//
//  FLIRMeasurementReference.h
//  ThermalSDK
//
//  Created by FLIR on 2021-01-05.
//  Copyright © 2021 Teledyne FLIR. All rights reserved.
//

#import "FLIRMeasurementShape.h"
#import "FLIRThermalValue.h"

/**
 *  Represents a MeasurementReference object.
 */
@interface FLIRMeasurementReference : FLIRMeasurementShape

/**
 *  Get or set the thermal value (i.e. temperature) of the reference measurement.
 */
@property (nonatomic, strong, nonnull) FLIRThermalValue* value;

/**
 *  Get or set a label for the reference measurement.
 */
@property (nonatomic, strong, nonnull) NSString* label;

@end
