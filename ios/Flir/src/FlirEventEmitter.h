//
//  FlirEventEmitter.h
//  Flir
//
//  Event emitter for sending FLIR events to React Native
//

// Prefer React/ headers, but fall back to ReactCore or local headers for different RN layouts
#if __has_include(<React/RCTBridgeModule.h>)
#import <React/RCTBridgeModule.h>
#if __has_include(<React/RCTEventEmitter.h>)
#import <React/RCTEventEmitter.h>
#endif
#elif __has_include(<ReactCore/RCTBridgeModule.h>)
#import <ReactCore/RCTBridgeModule.h>
#if __has_include(<ReactCore/RCTEventEmitter.h>)
#import <ReactCore/RCTEventEmitter.h>
#elif __has_include(<React/RCTEventEmitter.h>)
#import <React/RCTEventEmitter.h>
#endif
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

@interface FlirEventEmitter : RCTEventEmitter <RCTBridgeModule>

/// Shared singleton instance (set after module initialization)
+ (nullable instancetype)shared;

/// Send an event to JavaScript
/// @param name Event name
/// @param body Event payload
- (void)sendDeviceEvent:(NSString *)name body:(id)body;

@end

NS_ASSUME_NONNULL_END
