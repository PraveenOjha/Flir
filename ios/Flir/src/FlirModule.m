//
//  FlirModule.m
//  Flir
//
//  React Native bridge module for FLIR thermal camera SDK
//  Delegate to FlirManager (Swift) for all functionality.
//

#import "FlirModule.h"
#import "FlirEventEmitter.h"
#import "FlirState.h"
#import <React/RCTBridge.h>
#import <React/RCTLog.h>
#import <objc/message.h>
#import <objc/runtime.h>

// Import Swift-generated header for FlirManagerDelegate protocol
#if __has_include("Flir-Swift.h")
#import "Flir-Swift.h"
#elif __has_include(<Flir/Flir-Swift.h>)
#import <Flir/Flir-Swift.h>
#endif

// Helper to access FlirManager singleton
static id flir_manager_shared(void) {
  Class cls = NSClassFromString(@"Flir.FlirManager");
  if (!cls) {
    cls = NSClassFromString(@"FlirManager");
  }
  if (!cls)
    return nil;
  SEL sel = sel_registerName("shared");
  if (![cls respondsToSelector:sel])
    return nil;
  id (*msgSend0)(id, SEL) = (id (*)(id, SEL))objc_msgSend;
  return msgSend0((id)cls, sel);
}

// Helper for primitives
static double flir_getTemperatureAtPoint(int x, int y) {
  id inst = flir_manager_shared();
  if (!inst)
    return NAN;
  SEL sel = sel_registerName("getTemperatureAtPoint:y:");
  if (![inst respondsToSelector:sel])
    return NAN;
  double (*msgSend2)(id, SEL, int, int) =
      (double (*)(id, SEL, int, int))objc_msgSend;
  return msgSend2(inst, sel, x, y);
}

static int flir_getBatteryLevel(void) {
  id inst = flir_manager_shared();
  if (!inst)
    return -1;
  SEL sel = sel_registerName("getBatteryLevel");
  if (![inst respondsToSelector:sel])
    return -1;
  int (*msgSend0)(id, SEL) = (int (*)(id, SEL))objc_msgSend;
  return msgSend0(inst, sel);
}

static BOOL flir_isBatteryCharging(void) {
  id inst = flir_manager_shared();
  if (!inst)
    return NO;
  SEL sel = sel_registerName("isBatteryCharging");
  if (![inst respondsToSelector:sel])
    return NO;
  BOOL (*msgSend0)(id, SEL) = (BOOL (*)(id, SEL))objc_msgSend;
  return msgSend0(inst, sel);
}
static void flir_setPreferSdkRotation(BOOL prefer) {
  id inst = flir_manager_shared();
  if (!inst)
    return;
  SEL sel = sel_registerName("setPreferSdkRotation:");
  if (![inst respondsToSelector:sel])
    return;
  void (*msgSend1)(id, SEL, BOOL) = (void (*)(id, SEL, BOOL))objc_msgSend;
  msgSend1(inst, sel, prefer);
}

static BOOL flir_isPreferSdkRotation(void) {
  id inst = flir_manager_shared();
  if (!inst)
    return NO;
  SEL sel = sel_registerName("isPreferSdkRotation");
  if (![inst respondsToSelector:sel])
    return NO;
  BOOL (*msgSend0)(id, SEL) = (BOOL (*)(id, SEL))objc_msgSend;
  return msgSend0(inst, sel);
}

@interface FlirModule () <FlirManagerDelegate>
@property(nonatomic, copy) RCTPromiseResolveBlock connectResolve;
@property(nonatomic, copy) RCTPromiseRejectBlock connectReject;
@end

@implementation FlirModule {
  NSInteger _listenerCount;
}

RCT_EXPORT_MODULE(FlirModule);

+ (BOOL)requiresMainQueueSetup {
  return YES;
}

- (instancetype)init {
  if (self = [super init]) {
    _listenerCount = 0;
    // Wire up delegate
    id manager = flir_manager_shared();
    if (manager) {
      [manager setValue:self forKey:@"delegate"];
    }
  }
  return self;
}

#pragma mark - Event Emitter Support

- (NSArray<NSString *> *)supportedEvents {
  return @[
    @"FlirDeviceConnected", @"FlirDeviceDisconnected", @"FlirDevicesFound",
    @"FlirFrameReceived", @"FlirFrameBitmapAvailable", @"FlirError", @"FlirStateChanged",
    @"FlirBatteryUpdated"
  ];
}

RCT_EXPORT_METHOD(addListener : (NSString *)eventName) {
  _listenerCount++;
  NSLog(@"[FlirModule] addListener: %@ (count: %ld)", eventName, (long)_listenerCount);
  
  // When FlirDevicesFound listener is added, immediately emit current device list
  // This handles the case where discovery happened before React Native mounted
  if ([eventName isEqualToString:@"FlirDevicesFound"]) {
    dispatch_async(dispatch_get_main_queue(), ^{
      id manager = flir_manager_shared();
      if (manager) {
        NSArray *devices = ((NSArray * (*)(id, SEL))
                           objc_msgSend)(manager, sel_registerName("getDiscoveredDevices"));
        if (devices && devices.count > 0) {
          NSLog(@"[FlirModule] addListener - re-emitting %lu discovered devices", (unsigned long)devices.count);
          [self onDevicesFound:devices];
        }
      }
    });
  }
}

RCT_EXPORT_METHOD(removeListeners : (NSInteger)count) {
  _listenerCount -= count;
  if (_listenerCount < 0) _listenerCount = 0;
  NSLog(@"[FlirModule] removeListeners: %ld (remaining: %ld)", (long)count, (long)_listenerCount);
}

+ (void)emitBatteryUpdateWithLevel:(NSInteger)level charging:(BOOL)charging {
  NSDictionary *payload = @{@"level" : @(level), @"isCharging" : @(charging)};
  NSLog(@"[FlirModule] Emitting battery update - level: %ld, charging: %d", (long)level, charging);
  
  // Note: This is a class method, so we need to get the module instance
  // For now, we'll just log - in production you'd need to get the module instance
  // or convert this to an instance method
  // [[FlirModule sharedInstance] sendEventWithName:@"FlirBatteryUpdated" body:payload];
}

#pragma mark - Methods

RCT_EXPORT_METHOD(setNetworkDiscoveryEnabled : (BOOL)enabled resolver : (
    RCTPromiseResolveBlock)resolve rejecter : (RCTPromiseRejectBlock)reject) {
  // FlirManager uses UserDefaults directly for this too
  id manager = flir_manager_shared();
  if (manager &&
      [manager
          respondsToSelector:sel_registerName("setNetworkDiscoveryEnabled:")]) {
    ((void (*)(id, SEL, BOOL))objc_msgSend)(
        manager, sel_registerName("setNetworkDiscoveryEnabled:"), enabled);
  } else {
    [[NSUserDefaults standardUserDefaults]
        setBool:enabled
         forKey:@"ilabsFlir.networkDiscoveryEnabled"];
  }
  resolve(@(YES));
}

RCT_EXPORT_METHOD(startDiscovery : (RCTPromiseResolveBlock)
                      resolve rejecter : (RCTPromiseRejectBlock)reject) {
  dispatch_async(dispatch_get_main_queue(), ^{
    id manager = flir_manager_shared();
    if (manager &&
        [manager respondsToSelector:sel_registerName("startDiscovery")]) {
      ((void (*)(id, SEL))objc_msgSend)(manager,
                                        sel_registerName("startDiscovery"));
    }
    resolve(@(YES));
  });
}

RCT_EXPORT_METHOD(stopDiscovery : (RCTPromiseResolveBlock)
                      resolve rejecter : (RCTPromiseRejectBlock)reject) {
  dispatch_async(dispatch_get_main_queue(), ^{
    id manager = flir_manager_shared();
    if (manager &&
        [manager respondsToSelector:sel_registerName("stopDiscovery")]) {
      ((void (*)(id, SEL))objc_msgSend)(manager,
                                        sel_registerName("stopDiscovery"));
    }
    resolve(@(YES));
  });
}

RCT_EXPORT_METHOD(getDiscoveredDevices : (RCTPromiseResolveBlock)
                      resolve rejecter : (RCTPromiseRejectBlock)reject) {
  dispatch_async(dispatch_get_main_queue(), ^{
    id manager = flir_manager_shared();
    NSMutableArray *arr = [NSMutableArray new];
    if (manager &&
        [manager respondsToSelector:sel_registerName("getDiscoveredDevices")]) {
      NSArray *devs = ((NSArray * (*)(id, SEL)) objc_msgSend)(
          manager, sel_registerName("getDiscoveredDevices"));
      for (id d in devs) {
        if ([d respondsToSelector:sel_registerName("toDictionary")]) {
          [arr addObject:((NSDictionary * (*)(id, SEL)) objc_msgSend)(
                             d, sel_registerName("toDictionary"))];
        }
      }
    }
    resolve(arr);
  });
}

RCT_EXPORT_METHOD(connectToDevice : (NSString *)deviceId resolver : (
    RCTPromiseResolveBlock)resolve rejecter : (RCTPromiseRejectBlock)reject) {
  dispatch_async(dispatch_get_main_queue(), ^{
    self.connectResolve = resolve;
    self.connectReject = reject;

    id manager = flir_manager_shared();
    if (manager &&
        [manager respondsToSelector:sel_registerName("connectToDevice:")]) {
      ((void (*)(id, SEL, id))objc_msgSend)(
          manager, sel_registerName("connectToDevice:"), deviceId);
    } else {
      reject(@"ERR_NO_MANAGER", @"FlirManager not found", nil);
      self.connectResolve = nil;
      self.connectReject = nil;
    }
  });
}

RCT_EXPORT_METHOD(disconnect : (RCTPromiseResolveBlock)
                      resolve rejecter : (RCTPromiseRejectBlock)reject) {
  dispatch_async(dispatch_get_main_queue(), ^{
    id manager = flir_manager_shared();
    if (manager &&
        [manager respondsToSelector:sel_registerName("disconnect")]) {
      ((void (*)(id, SEL))objc_msgSend)(manager,
                                        sel_registerName("disconnect"));
    }
    resolve(@(YES));
  });
}

RCT_EXPORT_METHOD(stopFlir : (RCTPromiseResolveBlock)
                      resolve rejecter : (RCTPromiseRejectBlock)reject) {
  dispatch_async(dispatch_get_main_queue(), ^{
    id manager = flir_manager_shared();
    if (manager && [manager respondsToSelector:sel_registerName("stop")]) {
      ((void (*)(id, SEL))objc_msgSend)(manager, sel_registerName("stop"));
    }
    resolve(@(YES));
  });
}

RCT_EXPORT_METHOD(startEmulator : (NSString *)emulatorType resolver : (
    RCTPromiseResolveBlock)resolve rejecter : (RCTPromiseRejectBlock)reject) {
  dispatch_async(dispatch_get_main_queue(), ^{
    self.connectResolve = resolve;
    self.connectReject = reject;
    id manager = flir_manager_shared();
    if (manager && [manager respondsToSelector:sel_registerName(
                                                   "startEmulatorWithType:")]) {
      // Swift: startEmulator(type: String) -> exposed as startEmulatorWithType:
      // ? Or startEmulatorWith? Swift default naming: startEmulator(type:) ->
      // startEmulatorWithType:
      ((void (*)(id, SEL, id))objc_msgSend)(
          manager, sel_registerName("startEmulatorWithType:"), emulatorType);
    } else {
      // Fallback if selector assumption wrong/mismatch
      reject(@"ERR_NOT_IMPL",
             @"startEmulator not implemented or signature mismatch", nil);
      self.connectResolve = nil;
      self.connectReject = nil;
    }
  });
}

RCT_EXPORT_METHOD(getTemperatureAt : (nonnull NSNumber *)x y : (
    nonnull NSNumber *)y resolver : (RCTPromiseResolveBlock)
                      resolve rejecter : (RCTPromiseRejectBlock)reject) {
  dispatch_async(dispatch_get_main_queue(), ^{
    double temp = flir_getTemperatureAtPoint([x intValue], [y intValue]);
    if (isnan(temp)) {
      resolve([NSNull null]);
    } else {
      resolve(@(temp));
    }
  });
}

RCT_EXPORT_METHOD(getTemperatureFromColor : (NSInteger)color resolver : (
    RCTPromiseResolveBlock)resolve rejecter : (RCTPromiseRejectBlock)reject) {
  int r = (color >> 16) & 0xFF;
  int g = (color >> 8) & 0xFF;
  int b = color & 0xFF;
  double lum = 0.2126 * r + 0.7152 * g + 0.0722 * b;
  double temp = (lum / 255.0) * 400.0;
  resolve(@(temp));
}

RCT_EXPORT_METHOD(isEmulator : (RCTPromiseResolveBlock)
                      resolve rejecter : (RCTPromiseRejectBlock)reject) {
  dispatch_async(dispatch_get_main_queue(), ^{
    id manager = flir_manager_shared();
    BOOL isEm = NO;
    if (manager &&
        [manager respondsToSelector:sel_registerName("isEmulator")]) {
      isEm = ((BOOL (*)(id, SEL))objc_msgSend)(manager,
                                               sel_registerName("isEmulator"));
    }
    resolve(@(isEm));
  });
}

RCT_EXPORT_METHOD(getLatestFrameBitmap : (RCTPromiseResolveBlock)resolve rejecter : (RCTPromiseRejectBlock)reject) {
  dispatch_async(dispatch_get_main_queue(), ^{
    id manager = flir_manager_shared();
    if (!manager || ![manager respondsToSelector:sel_registerName("latestFrameBitmapBase64")]) {
      resolve([NSNull null]);
      return;
    }
    NSDictionary *dict = ((NSDictionary * (*)(id, SEL)) objc_msgSend)(manager, sel_registerName("latestFrameBitmapBase64"));
    if (!dict) {
      resolve([NSNull null]);
    } else {
      resolve(dict);
    }
  });
}

RCT_EXPORT_METHOD(isDeviceConnected : (RCTPromiseResolveBlock)
                      resolve rejecter : (RCTPromiseRejectBlock)reject) {
  dispatch_async(dispatch_get_main_queue(), ^{
    id manager = flir_manager_shared();
    BOOL isC = NO;
    if (manager &&
        [manager respondsToSelector:sel_registerName("isConnected")]) {
      isC = ((BOOL (*)(id, SEL))objc_msgSend)(manager,
                                              sel_registerName("isConnected"));
    }
    resolve(@(isC));
  });
}

RCT_EXPORT_METHOD(getConnectedDeviceInfo : (RCTPromiseResolveBlock)
                      resolve rejecter : (RCTPromiseRejectBlock)reject) {
  dispatch_async(dispatch_get_main_queue(), ^{
    id manager = flir_manager_shared();
    NSString *info = @"Not connected";
    if (manager && [manager respondsToSelector:sel_registerName(
                                                   "getConnectedDeviceInfo")]) {
      info = ((NSString * (*)(id, SEL)) objc_msgSend)(
          manager, sel_registerName("getConnectedDeviceInfo"));
    }
    resolve(info);
  });
}

RCT_EXPORT_METHOD(isSDKDownloaded : (RCTPromiseResolveBlock)
                      resolve rejecter : (RCTPromiseRejectBlock)reject) {
  // Assuming integrated SDK
  resolve(@(YES));
}

RCT_EXPORT_METHOD(getSDKStatus : (RCTPromiseResolveBlock)
                      resolve rejecter : (RCTPromiseRejectBlock)reject) {
  resolve(@{@"available" : @(YES), @"arch" : @"arm64", @"platform" : @"iOS"});
}

RCT_EXPORT_METHOD(getBatteryLevel : (RCTPromiseResolveBlock)
                      resolve rejecter : (RCTPromiseRejectBlock)reject) {
  dispatch_async(dispatch_get_main_queue(), ^{
    int level = flir_getBatteryLevel();
    resolve(@(level));
  });
}

RCT_EXPORT_METHOD(isBatteryCharging : (RCTPromiseResolveBlock)
                      resolve rejecter : (RCTPromiseRejectBlock)reject) {
  dispatch_async(dispatch_get_main_queue(), ^{
    BOOL ch = flir_isBatteryCharging();
    resolve(@(ch));
  });
}

RCT_EXPORT_METHOD(setPreferSdkRotation : (BOOL)prefer resolver : (
    RCTPromiseResolveBlock)resolve rejecter : (RCTPromiseRejectBlock)reject) {
  dispatch_async(dispatch_get_main_queue(), ^{
    flir_setPreferSdkRotation(prefer);
    resolve(@(YES));
  });
}

RCT_EXPORT_METHOD(isPreferSdkRotation : (RCTPromiseResolveBlock)
                      resolve rejecter : (RCTPromiseRejectBlock)reject) {
  dispatch_async(dispatch_get_main_queue(), ^{
    BOOL v = flir_isPreferSdkRotation();
    resolve(@(v));
  });
}

#pragma mark - FlirManagerDelegate

- (void)onDevicesFound:(NSArray *)devices {
  NSMutableArray *arr = [NSMutableArray new];
  for (id d in devices) {
    if ([d respondsToSelector:sel_registerName("toDictionary")]) {
      [arr addObject:((NSDictionary * (*)(id, SEL))
                          objc_msgSend)(d, sel_registerName("toDictionary"))];
    }
  }
  
  NSLog(@"[FlirModule] onDevicesFound - %lu devices, listenerCount: %ld", (unsigned long)arr.count, (long)_listenerCount);
  
  if (_listenerCount > 0) {
    NSLog(@"[FlirModule] emitting FlirDevicesFound event");
    [self sendEventWithName:@"FlirDevicesFound" 
                       body:@{@"devices" : arr, @"count" : @(arr.count)}];
  } else {
    NSLog(@"[FlirModule] ⚠️ No listeners registered yet - devices will be re-emitted when listener is added");
  }
}

- (void)onDeviceConnected:(id)device {
  if (self.connectResolve) {
    self.connectResolve(@(YES));
    self.connectResolve = nil;
    self.connectReject = nil;
  }

  // device is FlirDeviceInfo
  NSMutableDictionary *body = [NSMutableDictionary new];
  if ([device respondsToSelector:sel_registerName("toDictionary")]) {
    [body
        addEntriesFromDictionary:((NSDictionary * (*)(id, SEL)) objc_msgSend)(
                                     device, sel_registerName("toDictionary"))];
  }
  
  // Add state info to match Android's FlirDeviceConnected event format
  [body setObject:@"connected" forKey:@"state"];
  [body setObject:@(YES) forKey:@"isConnected"];
  [body setObject:@(NO) forKey:@"isStreaming"];  // streaming starts after connection
  // isEmulator info should be in device dictionary already from toDictionary

  NSLog(@"[FlirModule] onDeviceConnected - emitting FlirDeviceConnected event with state info");
  [self sendEventWithName:@"FlirDeviceConnected" body:body];
}

- (void)onDeviceDisconnected {
  NSLog(@"[FlirModule] onDeviceDisconnected - emitting FlirDeviceDisconnected event");
  [self sendEventWithName:@"FlirDeviceDisconnected" body:@{}];
}

- (void)onFrameReceived:(UIImage *)image
                  width:(NSInteger)width
                 height:(NSInteger)height {
  // Also emit event for JS consumers (though slow, some might use it)
  [self sendEventWithName:@"FlirFrameReceived"
                     body:@{
                       @"width" : @(width),
                       @"height" : @(height),
                       @"timestamp" :
                           @([[NSDate date] timeIntervalSince1970] * 1000)
                     }];
}

- (void)onFrameReceivedRaw:(NSData *)data width:(NSInteger)width height:(NSInteger)height bytesPerRow:(NSInteger)bytesPerRow timestamp:(double)timestamp {
  // Emit a lightweight event to notify JS that a raw bitmap is available; raw bytes are available via getLatestFrameBitmap()
  [self sendEventWithName:@"FlirFrameBitmapAvailable" body:@{
    @"width": @(width),
    @"height": @(height),
    @"bytesPerRow": @(bytesPerRow),
    @"timestamp": @(timestamp)
  }];
}

- (void)onError:(NSString *)message {
  if (self.connectReject) {
    self.connectReject(@"ERR_FLIR", message, nil);
    self.connectResolve = nil;
    self.connectReject = nil;
  }
  NSLog(@"[FlirModule] onError - emitting FlirError: %@", message);
  [self sendEventWithName:@"FlirError"
                     body:@{@"error" : message ?: @"Unknown error"}];
}

- (void)onStateChanged:(NSString *)state
           isConnected:(BOOL)isConnected
           isStreaming:(BOOL)isStreaming
            isEmulator:(BOOL)isEmulator {
  NSDictionary *body = @{
    @"state" : state,
    @"isConnected" : @(isConnected),
    @"isStreaming" : @(isStreaming),
    @"isEmulator" : @(isEmulator)
  };
  NSLog(@"[FlirModule] onStateChanged - state: %@, connected: %d, streaming: %d", state, isConnected, isStreaming);
  [self sendEventWithName:@"FlirStateChanged" body:body];
}

@end
