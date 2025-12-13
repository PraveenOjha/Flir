//
//  FlirEventEmitter.h
//  Flir
//
//  Event emitter for sending FLIR events to React Native
//

#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>

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
