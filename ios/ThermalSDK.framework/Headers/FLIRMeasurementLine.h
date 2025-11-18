//
//  FLIRMeasurementLine.h
//  FLIR Thermal SDK
//
//  Copyright © 2019 Teledyne FLIR. All rights reserved.
//

#import "FLIRMeasurementArea.h"
#import "FLIRThermalValue.h"

NS_ASSUME_NONNULL_BEGIN
/**
 *  Defines a line measurement tool shape.
 *  The SDK supports only horizontal or vertical lines.
 *  This tool allows to measure temperature in line area.
 *  It gives the possibility to find area's minimum, maximum and average temperature.
 *  There is functionality to find the exact location for minimum and maximum values.
 */
@interface FLIRMeasurementLine : FLIRMeasurementArea

/**  Set the location of a vertical line.
 *
 *  @param x    The x-coordinate where the line is placed.
 *
 *   X-coordinates should be positive values.
 */
- (BOOL)setVerticalLine:(int)x
                  error:(out NSError* _Nullable * _Nullable)error;

/**  Set the location of a horizontal line.
 *
 *  @param y    The y-coordinate where the line is placed.
 *
 *   Y-coordinates should be positive values.
 */
- (BOOL)setHorizontalLine:(int)y
                    error:(out NSError* _Nullable * _Nullable)error;

/**  Set line location
*
 *  @param from starting point
 *  @param to ending point
 */

- (BOOL)setLineFrom:(CGPoint)start
                 to:(CGPoint)end
              error:(out NSError * _Nullable * _Nullable)error;

/** The start position of the line.
 *  A Point with (x,y)-coordinates. Error if point is (-1,-1).
 */
- (CGPoint)getStartPosition;

/** The end position of the line.
 *  A Point with (x,y)-coordinates. Error if point is (-1,-1).
 */
- (CGPoint)getEndPosition;

/**
 *  Return true  if the line measurement tool is horizontal.
 */
- (BOOL)isHorizontal;

@end

NS_ASSUME_NONNULL_END
