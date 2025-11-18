//
//  FLIRQuantification.h
//  ThermalSDK
//
//  Created by Teledyne 2023-04-27.
//  Copyright © 2023 Teledyne. All rights reserved.
//

#import <Foundation/Foundation.h>

///Describes the type of a gas leak
typedef NS_ENUM(NSInteger, FLIRGasLeakType) {
    /// Point-shaped leak
    FLIRGasLeakType_point,
    /// Diffused leak
    FLIRGasLeakType_diffused
};

/// Describes the wind speed conditions
typedef NS_ENUM(NSInteger, FLIRWindSpeed) {
    /// Calm wind.
    FLIRWindSpeed_calm,
    /// Normal wind speed.
    FLIRWindSpeed_normal,
    /// High wind speed.
    FLIRWindSpeed_high
};

/// Gas quantification information
@interface FLIRGasQuantificationInput : NSObject
/// Temperature of surrounding environment
@property (nonatomic, readonly) double ambientTemperature;
/// Name of the gas
@property (nonatomic, readonly, nonnull) NSString* gas;
/// Type of gas leak
@property (nonatomic, readonly) FLIRGasLeakType leakType;
/// Wind speed
@property (nonatomic, readonly) FLIRWindSpeed windSpeed;
/// Distance to object in meters
@property (nonatomic, readonly) NSInteger distance;
/// Detection threshold temperature difference
@property (nonatomic, readonly) double thresholdDeltaTemperature;
/// True if gas is warmer than the surrounding environment
@property (nonatomic, readonly) BOOL emissive;

@end

/// Gas quantification result information
@interface FLIRGasQuantificationResult : NSObject
/// Amount of gas flow
@property (nonatomic, readonly) double flow;
/// Gas concentration
@property (nonatomic, readonly) double concentration;

@end
