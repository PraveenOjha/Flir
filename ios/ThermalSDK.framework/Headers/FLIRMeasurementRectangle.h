//
//  FLIRMeasurementRectangle.h
//  FLIR Thermal SDK
//
//  Copyright © 2019 Teledyne FLIR. All rights reserved.
//

#import "FLIRMeasurementArea.h"

/**
 *  Represents a MeasurementRectangle object.
 */
@interface FLIRMeasurementRectangle : FLIRMeasurementArea

/**
 *  Gets x,y positon
 */
- (CGPoint)getPosition;

/**
 * Sets position
 */

- (BOOL)setPosition:(CGPoint)position error:(out NSError * _Nullable *_Nullable)error;

/**
 *  Gets the measurement rectangle, position and size
 */
- (CGRect)getRectangle;

/**
 *  Sets the measurement rectangle, position and size
 */
- (BOOL)setRectangle:(CGRect)rectangle error:(out NSError * _Nullable *_Nullable)error;

@end
