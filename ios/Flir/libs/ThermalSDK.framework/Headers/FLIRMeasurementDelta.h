//
//  FLIRMeasurementDelta.h
//  ThermalSDK
//
//  Created by FLIR on 2020-12-08.
//  Copyright © 2020 Teledyne FLIR. All rights reserved.
//

#pragma once

#import "FLIRMeasurementShape.h"
#import "FLIRThermalDelta.h"

NS_ASSUME_NONNULL_BEGIN

/**
 *  Class representing a delta measurement type in the image.
 */
@interface FLIRMeasurementDelta : FLIRMeasurementShape

/**
 *  Thermal difference/delta value.
 */
- (FLIRThermalDelta * _Nullable)getDeltaValue:(out NSError * _Nullable * _Nullable)error;

/**
 *  Gets the first shape.
 */
- (FLIRMeasurementShape *)getMember1;

/**
 *  Gets the second shape.
 */
- (FLIRMeasurementShape *)getMember2;

/**
 *  Gets the first delta member type.
 */
- (DeltaMemberValueType)getMember1ValueType;

/**
 *  Gets the second delta member type.
 */
- (DeltaMemberValueType)getMember2ValueType;

/**
 *  Sets the first delta member.
 */
- (BOOL)setMember1:(FLIRMeasurementShape *)shape
              type:(DeltaMemberValueType)type
             error:(out NSError * _Nullable * _Nullable)error;

/**
 *  Sets the second delta member.
 */
- (BOOL)setMember2:(FLIRMeasurementShape *)shape
              type:(DeltaMemberValueType)type
             error:(out NSError * _Nullable * _Nullable)error;

@end

NS_ASSUME_NONNULL_END
