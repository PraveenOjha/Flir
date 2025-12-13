//
//  FlirEventEmitter.m
//  Flir
//
//  Event emitter for sending FLIR events to React Native
//

#import "FlirEventEmitter.h"

static FlirEventEmitter *_sharedEmitter = nil;

@implementation FlirEventEmitter

RCT_EXPORT_MODULE();

+ (BOOL)requiresMainQueueSetup {
  return YES;
}

- (instancetype)init {
  if (self = [super init]) {
    _sharedEmitter = self;
  }
  return self;
}

+ (instancetype)shared {
  return _sharedEmitter;
}

- (NSArray<NSString *> *)supportedEvents {
  return @[
    @"FlirDeviceConnected", @"FlirDeviceDisconnected", @"FlirDevicesFound",
    @"FlirFrameReceived", @"FlirFrame", @"FlirError", @"FlirStateChanged",
    @"FlirTemperatureUpdate", @"FlirBatteryUpdated"
  ];
}

- (void)sendDeviceEvent:(NSString *)name body:(id)body {
  if (!_sharedEmitter) {
    NSLog(@"[FlirEventEmitter] Warning: Emitter not initialized, event "
          @"dropped: %@",
          name);
    return;
  }

  @try {
    [_sharedEmitter sendEventWithName:name body:body];
  } @catch (NSException *exception) {
    NSLog(@"[FlirEventEmitter] Error sending event %@: %@", name, exception);
  }
}

// Required for RCTEventEmitter
RCT_EXPORT_METHOD(addListener : (NSString *)eventName) {
  // Keep track of listeners if needed
}

RCT_EXPORT_METHOD(removeListeners : (NSInteger)count) {
  // Clean up listeners if needed
}

@end
