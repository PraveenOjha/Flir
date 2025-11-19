#import <React/RCTEventEmitter.h>

NS_ASSUME_NONNULL_BEGIN

@interface FlirEventEmitter : RCTEventEmitter <RCTBridgeModule>

+ (instancetype)shared;
- (void)sendDeviceEvent:(NSString *)name body:(id)body;

@end

NS_ASSUME_NONNULL_END
