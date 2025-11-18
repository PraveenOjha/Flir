#import "FlirModule.h"
#import "FlirEventEmitter.h"
#import "FlirState.h"
#import <React/RCTLog.h>

@implementation FlirModule

RCT_EXPORT_MODULE(FlirIOS);

RCT_EXPORT_METHOD(startDiscovery)
{
  // Hook into ThermalSDK discovery when ready. For now, emit an event to JS.
  dispatch_async(dispatch_get_main_queue(), ^{
    [[FlirEventEmitter shared] sendDeviceEvent:@"FlirDeviceConnected" body:@{ @"msg": @"discovery-started" }];
    RCTLogInfo(@"FLIR discovery started (placeholder)");
  });
}

RCT_EXPORT_METHOD(stopDiscovery)
{
  dispatch_async(dispatch_get_main_queue(), ^{
    [[FlirEventEmitter shared] sendDeviceEvent:@"FlirDeviceDisconnected" body:@{ @"msg": @"discovery-stopped" }];
    RCTLogInfo(@"FLIR discovery stopped (placeholder)");
  });
}

RCT_EXPORT_METHOD(connect:(NSDictionary *)identity resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject)
{
  // TODO: implement real connect using ThermalSDK and identity info
  dispatch_async(dispatch_get_main_queue(), ^{
    RCTLogInfo(@"Flir connect called (placeholder)");
    // emit a connected event
    [[FlirEventEmitter shared] sendDeviceEvent:@"FlirDeviceConnected" body:@{ @"identity": identity ?: @{} }];
    resolve(@(YES));
  });
}

RCT_EXPORT_METHOD(disconnect)
{
  dispatch_async(dispatch_get_main_queue(), ^{
    RCTLogInfo(@"Flir disconnect called (placeholder)");
    [[FlirEventEmitter shared] sendDeviceEvent:@"FlirDeviceDisconnected" body:@{}];
  });
}

RCT_EXPORT_METHOD(getTemperatureAt:(nonnull NSNumber *)x y:(nonnull NSNumber *)y resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject)
{
  // Return the last sampled temperature from the preview/state singleton
  dispatch_async(dispatch_get_main_queue(), ^{
    double t = [FlirState shared].lastTemperature;
    if (isnan(t)) {
      resolve([NSNull null]);
    } else {
      resolve(@(t));
    }
  });
}

@end
