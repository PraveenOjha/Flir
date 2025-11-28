//
//  MeasurementSpot.h
//  FLIR Thermal SDK
//
//  Copyright © 2019 Teledyne FLIR. All rights reserved.
//

#import "FLIRMeasurementShape.h"
#import "FLIRThermalValue.h"


/**
 *  Represents a MeasurementSpot object.
 */
@interface FLIRMeasurementSpot : FLIRMeasurementShape

/**
 *  Gets the thermal value of this MeasurementSpot.
 */
- (FLIRThermalValue* _Nonnull)getValue;

/**
 *  Get the position of this MeasurementSpot.
 */
- (CGPoint)getPosition;

/**
 *  Set the position of this MeasurementSpot.
 */
- (BOOL)setPosition:(CGPoint)position error: (out NSError* _Nullable* _Nullable)error;


@end
