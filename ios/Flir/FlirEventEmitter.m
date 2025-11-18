#import "FlirEventEmitter.h"

static FlirEventEmitter *_sharedEmitter = nil;

@implementation FlirEventEmitter

RCT_EXPORT_MODULE();

- (instancetype)init
{
  if (self = [super init]) {
    _sharedEmitter = self;
  }
  return self;
}

+ (instancetype)shared
{
  return _sharedEmitter;
}

- (NSArray<NSString *> *)supportedEvents
{
  return @[@"FlirDeviceConnected", @"FlirDeviceDisconnected", @"FlirFrame"];
}

- (void)sendDeviceEvent:(NSString *)name body:(id)body
{
  if (!_sharedEmitter) return;
  [_sharedEmitter sendEventWithName:name body:body];
}

@end
