//
//  MeasurementShape.h
//  FLIR Thermal SDK
//
//  Copyright © 2019 Teledyne FLIR. All rights reserved.
//

#import <Foundation/Foundation.h>
#import <CoreGraphics/CoreGraphics.h>
#import "FLIRMeasurementParameters.h"

/**
 *  The class is a base class for other measurement shapes. The class has the basic properties
 *  and methods for a measurement shape.
 */
@interface FLIRMeasurementShape : NSObject

/**
 *  Gets the identity on the measurement shape.
 */
@property (readonly) uint32_t identity;

/**
 *  Gets or sets the name of the measurement shape.
 */
@property (nonatomic,readwrite) NSString * _Nonnull name;

/**
 *  Gets the object parameters for this measurement shape.
 */
@property (nonatomic,readonly) FLIRMeasurementParameters * _Nonnull thermalParameters;

/**
 *  Adjusts the location of this measurement shape by the specified amount.
 *
 *  @param offset Amount to offset the location.
 */
- (BOOL)offset:(CGPoint)offset error: (out NSError * _Nullable *_Nullable)error;

/**
 *  Move the location of this measurement shape to the specified position.
 *
 *  @param point Position
 */
- (BOOL)moveTo:(CGPoint)point error: (out NSError * _Nullable *_Nullable)error;
@end
