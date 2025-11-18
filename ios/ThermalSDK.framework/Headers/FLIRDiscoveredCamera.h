//
//  FLIRDiscoveredCamera.h
//  ThermalSDK
//
//  Created by FLIR on 2022-02-03.
//  Copyright © 2022 Teledyne FLIR. All rights reserved.
//

#import <Foundation/Foundation.h>

@class FLIRIdentity;
@class FLIRWirelessCameraDetails;

NS_ASSUME_NONNULL_BEGIN

/// Describes how connect may be called
typedef NS_OPTIONS(NSUInteger, FLIRConnectMethod) {
    /// Allows normal usage of connect
    CM_normal = 0x1,
    /// Allows connect "connecting" over SSL
    CM_secure = 0x2,
};

/// Plain information of discovered camera, including connection settings.
@interface FLIRDiscoveredCamera : NSObject

/// The Identity to use when connecting to a camera
@property (nonatomic, nonnull, readonly, strong) FLIRIdentity *identity;

/// Human readable name of the camera
@property (nonatomic, nonnull, readonly) NSString *displayName;

/// Describes how connect may be called
@property (nonatomic, readonly) FLIRConnectMethod  supportedConnectMethods;

/// Additional custom data received during discovery
@property (nonatomic, nonnull, readonly) NSDictionary<NSString *, NSString *> *records;

/// Optional wireless details for F1 wireless
@property (nonatomic, nullable, readonly, strong) FLIRWirelessCameraDetails* wirelessCameraDetails;

@end

NS_ASSUME_NONNULL_END
