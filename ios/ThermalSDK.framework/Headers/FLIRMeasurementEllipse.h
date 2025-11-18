//
//  FLIRMeasurementEllipse.h
//  FLIR Thermal SDK
//
//  Copyright © 2024 Teledyne FLIR. All rights reserved.
//

#import <CoreGraphics/CoreGraphics.h>
#import "FLIRMeasurementArea.h"

/**
 *  Defines the ellipse measurement tool shape.
 *  Ellipse is described by a center point and two radii, one horizontal (X) and one vertical (Y).
 *  This tool allows to measure temperature in ellipse area.
 *  It gives the possibility to find area's minimum, maximum and average temperature.
 *  There is functionality to find the exact location for minimum and maximum values.
 */
@interface FLIRMeasurementEllipse : FLIRMeasurementArea

/**
 *  Gets x,y positon, the center of the ellipse
 */
- (CGPoint)getPosition;

/**
 *  Gets radii
 */
- (int)getRadiusX;
- (int)getRadiusY;


/**
 * Sets position, the center of the ellipse
 */

- (BOOL)setPosition:(CGPoint)position error:(out NSError * _Nullable *_Nullable)error;

/**
 * Sets radii
 */
- (BOOL)setRadiusX:(int)radiusX radiusY:(int)radiusY error:(out NSError * _Nullable *_Nullable)error;

/**
 *  Sets both position and radii
 */
- (BOOL)setPosition:(CGPoint)position radiusX:(int)radiusX radiusY:(int)radiusY
              error:(out NSError * _Nullable *_Nullable)error;

@end
