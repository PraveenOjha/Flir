//
//  FLIRMeasurementsController.h
//  ThermalSDK
//
//  Created by FLIR on 2020-07-02.
//  Copyright © 2020 Teledyne FLIR. All rights reserved.
//

#import <Foundation/Foundation.h>

@class FLIRThermalValue;

/** abstract class for remote measurement shape, either a point (spot), a circle, a rectangle or a line */
@interface FLIRRemoteShape : NSObject

@end

/** Flags for calculations done on measurement areas. */
typedef NS_OPTIONS(NSUInteger, FLIRCalcMask) {
    ///  Single value temperature/signal, e.g. a spot value
    CM_temp = 1 << 1,
    ///  Maximum temperature/signal, e.g a area max value
    CM_max = 1 << 2,
    ///  Position of maximum temperature
    CM_maxpos = 1 << 3,
    ///  Minimum temperature/signal
    CM_min = 1 << 4,
    ///  Position of minimum temperature
    CM_minpos = 1 << 5,
    ///  Average temperature/signal
    CM_avg = 1 << 6,
    ///  Standard deviation temperature/signal
    CM_sdev = 1 << 7,
    ///  Median temperature/signal
    CM_median = 1 << 8,
    ///  Isotherm coloring or coverage depending on function
    CM_iso = 1 << 10,
    ///  Dimension of bounded object, e.g area of box
    CM_dimension = 1 << 13,
};

/** abstract class for remote measurement marker, either a rectangle, a circle or a line */
@interface FLIRRemoteMarker : FLIRRemoteShape

/** get the position of the hottest spot */
- (CGPoint)getHotSpotPosition;
/** get the position of the coldest spot */
- (CGPoint)getColdSpotPosition;
/** get the thermal value of the hottest spot */
- (FLIRThermalValue* _Nullable)getHotSpotTemperature;
/** get the thermal value of the coldest spot */
- (FLIRThermalValue* _Nullable)getColdSpotTemperature;
/** get the average thermal value  */
- (FLIRThermalValue* _Nullable)getAverageTemperature;
/** is the markers active */
- (BOOL)isMarkersActive;
/** set marker active (YES) or inactive (NO) */
- (BOOL)setIsMarkersActive: (BOOL)active error:(out NSError * _Nullable *_Nullable)error;

/** get the calcmask for the marker */
- (FLIRCalcMask)getCalcMask;
/** set the calcmask for the marker */
- (BOOL)setCalcMask:(FLIRCalcMask)calcMask error:(out NSError * _Nullable * _Nullable)error;
@end

/** a circular measurement area */
@interface FLIRRemoteCircle : FLIRRemoteMarker

/** get the center of the circle */
- (CGPoint)getPosition;
/** get radius of the circle */
- (int)getRadius;
/** set the center and radius of the circle */
- (BOOL)setPosition: (CGPoint)center radius: (int)radius error: (out NSError * _Nullable *_Nullable)error;

@end

/** a rectangular measurement area */
@interface FLIRRemoteRect : FLIRRemoteMarker

/** get the measurement rectangle */
- (CGRect)getRect;
/** set the measurement rectangle */
- (BOOL)setRect: (CGRect)rect error: (out NSError * _Nullable *_Nullable)error;

@end

/** a horizontal or vertical measurement line */
@interface FLIRRemoteLine : FLIRRemoteMarker

/** get the x-coordinate (if vertical) or y-coordinate (if horizontal) of the line */
- (int)getCoordinate;
/** get whether the line is horizontal (true) or vertical (false) */
- (BOOL)getHorizontal;
/** set the x-coordinate (if horizontal) or y-coordinate (if vertical) of the line and orientation */
- (BOOL)setCoordinate: (int)coordinate horizontal: (BOOL)horizontal error: (out NSError * _Nullable *_Nullable)error;

@end

/** a measurement point */
@interface FLIRRemoteSpot : FLIRRemoteShape

/** get the position of the measurement point */
- (CGPoint)getPosition;
/** set the position of the measurement point */
- (BOOL)setPosition: (CGPoint)position error: (out NSError * _Nullable *_Nullable)error;
/** the thermal value at the measurement point */
- (FLIRThermalValue* _Nullable) getValue;

@end

/** remote measurements */
@interface FLIRMeasurementsController : NSObject

/** add a measurement point */
- (FLIRRemoteSpot * _Nullable)addSpot:(CGPoint)point
                                error:(out NSError * _Nullable * _Nullable)error;
/** add a circular measurement area */
- (FLIRRemoteCircle  * _Nullable)addCircle: (CGPoint)position
                                    radius: (int)radius
                                     error:(out NSError * _Nullable * _Nullable)error;

/** add a rectangular measurement area */
- (FLIRRemoteRect * _Nullable)addRectangle: (CGRect)rect
                                     error:(out NSError * _Nullable * _Nullable)error;

/** add a measurement line
 *
 *  @param y y position of the line
 *  @return FLIRRemoteLine
 */
- (FLIRRemoteLine * _Nullable)addHorizontalLine:(int)y
                                          error:(out NSError * _Nullable * _Nullable)error;

/** add a measurement line
 *
 *  @param x x position of the line
 *  @return FLIRRemoteLine
 */
- (FLIRRemoteLine * _Nullable)addVerticalLine:(int)x
                                        error:(out NSError * _Nullable * _Nullable)error;

/** remove a previously added measurement shape
 *
 *  @param shape The measurement shape (line, spot, rectangle or circle)
 *  @return true if shape removed (i.e. the shape was in the collection)
 */
- (BOOL)remove:(FLIRRemoteShape * _Nonnull)shape
         error:(out NSError * _Nullable * _Nullable)error;

/** get all measument points */
- (NSArray<FLIRRemoteSpot *> * _Nullable) getSpots;
/** get all measument rectangles */
- (NSArray<FLIRRemoteRect *> * _Nullable) getRects;
/** get all measument circles */
- (NSArray<FLIRRemoteCircle *> * _Nullable) getCircles;
/** get all measument lines */
- (NSArray<FLIRRemoteLine *> * _Nullable) getLines;

@end
