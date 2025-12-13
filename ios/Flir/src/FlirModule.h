//
//  FlirModule.h
//  Flir
//
//  React Native bridge module for FLIR thermal camera SDK
//

#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>

NS_ASSUME_NONNULL_BEGIN

@interface FlirModule : RCTEventEmitter <RCTBridgeModule>

// Utility for other native code to emit battery updates (level 0-100 or -1 if unknown)
+ (void)emitBatteryUpdateWithLevel:(NSInteger)level charging:(BOOL)charging;

@end

NS_ASSUME_NONNULL_END
