//
//  FLIRMeasurementDimensions.h
//  ThermalSDK
//
//  Created by FLIR on 2020-11-05.
//  Copyright © 2020 Teledyne FLIR. All rights reserved.
//

#import <Foundation/Foundation.h>

/**
 * Describes area calculation for specified measurement tool.
 * Note: Some fields are only applicable for specific measurement tools. See description for each field for details.
 * Note: When field is unapplicable for a specified measurement type, field's value will evaluate to default value of 0.0.
 */
@interface FLIRMeasurementDimensions : NSObject

/** Area dimension value - only applicable for rectangle and ellipse (evaluates to 0.0 for other types) */
@property (nonatomic, readonly) double area;
/** Height dimension value - only applicable for rectangle (evaluates to 0.0 for other types) */
@property (nonatomic, readonly) double height;
/** Width dimension value - only applicable for rectangle (evaluates to 0.0 for other types) */
@property (nonatomic, readonly) double width;
/** Length dimension value - only applicable for line (evaluates to 0.0 for other types) */
@property (nonatomic, readonly) double length;
/** Horizontal radius dimension value - only applicable for ellipse (evaluates to 0.0 for other types) */
@property (nonatomic, readonly) double radiusX;
/** Vertical radius dimension value - only applicable for ellipse (evaluates to 0.0 for other types) */
@property (nonatomic, readonly) double radiusY;
/** Dimension status - true when calculation is valid and accurate, otherwise false */
@property (nonatomic, readonly) BOOL valid;

@end
