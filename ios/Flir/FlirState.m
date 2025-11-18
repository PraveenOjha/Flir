#import "FlirState.h"

static FlirState *_sharedState = nil;

@implementation FlirState

+ (instancetype)shared
{
  static dispatch_once_t onceToken;
  dispatch_once(&onceToken, ^{
    _sharedState = [FlirState new];
    _sharedState.lastTemperature = NAN;
  });
  return _sharedState;
}

@end
