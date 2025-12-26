//
//  FlirModule.h
//  Flir
//
//  React Native bridge module for FLIR thermal camera SDK
//

#if __has_include(<React/RCTBridgeModule.h>)
#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>
#elif __has_include(<ReactCore/RCTBridgeModule.h>)
#import <ReactCore/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>
#elif __has_include("RCTBridgeModule.h")
#import "RCTBridgeModule.h"
#import "RCTEventEmitter.h"
#else
#import <Foundation/Foundation.h>
@interface RCTEventEmitter : NSObject
@end
@protocol RCTBridgeModule <NSObject>
@end
#endif

NS_ASSUME_NONNULL_BEGIN

@interface FlirModule : RCTEventEmitter <RCTBridgeModule>

// Utility for other native code to emit battery updates (level 0-100 or -1 if unknown)
+ (void)emitBatteryUpdateWithLevel:(NSInteger)level charging:(BOOL)charging;

@end

NS_ASSUME_NONNULL_END
