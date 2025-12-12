//
//  FlirModule.m
//  Flir
//
//  React Native bridge module for FLIR thermal camera SDK
//  Provides discovery, connection, and streaming functionality
//

#import "FlirModule.h"
#import "FlirEventEmitter.h"
#import "FlirState.h"
#import <React/RCTLog.h>
#import <React/RCTBridge.h>

#if __has_include(<ThermalSDK/ThermalSDK.h>)
#define FLIR_SDK_AVAILABLE 1
#import <ThermalSDK/ThermalSDK.h>
#else
#define FLIR_SDK_AVAILABLE 0
#endif

// Declare minimal FLIRManager interface used by this module
// The real implementation lives in ilabs.libs (FLIRManager.swift). We declare
// the methods we call here to avoid compile-time errors when building this
// bridge from a separate module.
@interface FLIRManager : NSObject
+ (instancetype)shared;
- (double)getTemperatureAtPoint:(int)x y:(int)y;
- (int)getBatteryLevel;
- (BOOL)isBatteryCharging;
- (void)setPreferSdkRotation:(BOOL)prefer;
- (BOOL)isPreferSdkRotation;
@end

@interface FlirModule() 
#if FLIR_SDK_AVAILABLE
<FLIRDiscoveryEventDelegate, FLIRDataReceivedDelegate, FLIRStreamDelegate>
#endif

#if FLIR_SDK_AVAILABLE
@property (nonatomic, strong) FLIRDiscovery *discovery;
@property (nonatomic, strong) FLIRCamera *camera;
@property (nonatomic, strong) FLIRStream *stream;
@property (nonatomic, strong) FLIRThermalStreamer *streamer;
@property (nonatomic, strong) FLIRIdentity *connectedIdentity;
@property (nonatomic, strong) NSMutableDictionary<NSString *, FLIRIdentity *> *identityMap;
#endif

@property (nonatomic, strong) NSMutableArray<NSDictionary *> *discoveredDevices;
@property (nonatomic, assign) BOOL isScanning;
@property (nonatomic, assign) BOOL isConnected;
@property (nonatomic, assign) BOOL isStreaming;
@property (nonatomic, copy) NSString *connectedDeviceId;
@property (nonatomic, copy) NSString *connectedDeviceName;
@property (nonatomic, assign) double lastTemperature;
@end

@implementation FlirModule

RCT_EXPORT_MODULE(FlirModule);

+ (BOOL)requiresMainQueueSetup {
    return YES;
}

- (instancetype)init {
    if (self = [super init]) {
#if FLIR_SDK_AVAILABLE
        _identityMap = [NSMutableDictionary new];
#endif
        _discoveredDevices = [NSMutableArray new];
        _isScanning = NO;
        _isConnected = NO;
        _isStreaming = NO;
        _lastTemperature = NAN;
    }
    return self;
}

#pragma mark - Event Emitter Support

- (NSArray<NSString *> *)supportedEvents {
    return @[
        @"FlirDeviceConnected",
        @"FlirDeviceDisconnected",
        @"FlirDevicesFound",
        @"FlirFrameReceived",
        @"FlirError",
        @"FlirStateChanged"
        , @"FlirBatteryUpdated"
    ];
}

RCT_EXPORT_METHOD(addListener:(NSString *)eventName) {
    // Required for RCTEventEmitter
}

RCT_EXPORT_METHOD(removeListeners:(NSInteger)count) {
    // Required for RCTEventEmitter
}

// Provide a class helper so other native modules can post a battery update
+ (void)emitBatteryUpdateWithLevel:(NSInteger)level charging:(BOOL)charging {
    NSDictionary *payload = @{
        @"level": @(level),
        @"isCharging": @(charging)
    };
    [[FlirEventEmitter shared] sendDeviceEvent:@"FlirBatteryUpdated" body:payload];
}

#pragma mark - Discovery Methods

RCT_EXPORT_METHOD(startDiscovery:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
#if FLIR_SDK_AVAILABLE
    dispatch_async(dispatch_get_main_queue(), ^{
        if (self.isScanning) {
            RCTLogInfo(@"[FlirModule] Already scanning");
            resolve(@(YES));
            return;
        }
        
        self.isScanning = YES;
        [self.discoveredDevices removeAllObjects];
        [self.identityMap removeAllObjects];
        
        if (!self.discovery) {
            self.discovery = [[FLIRDiscovery alloc] init];
            self.discovery.delegate = self;
        }
        
        // Start discovery on all available interfaces
        FLIRCommunicationInterface interfaces = FLIRCommunicationInterfaceLightning |
                                                 FLIRCommunicationInterfaceNetwork |
                                                 FLIRCommunicationInterfaceFlirOneWireless |
                                                 FLIRCommunicationInterfaceEmulator;
        [self.discovery start:interfaces];
        
        [self emitStateChange:@"discovering"];
        RCTLogInfo(@"[FlirModule] Discovery started");
        resolve(@(YES));
    });
#else
    reject(@"ERR_FLIR_NOT_AVAILABLE", @"FLIR SDK not available", nil);
#endif
}

RCT_EXPORT_METHOD(stopDiscovery:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
#if FLIR_SDK_AVAILABLE
    dispatch_async(dispatch_get_main_queue(), ^{
        [self.discovery stop];
        self.isScanning = NO;
        RCTLogInfo(@"[FlirModule] Discovery stopped");
        resolve(@(YES));
    });
#else
    resolve(@(YES));
#endif
}

RCT_EXPORT_METHOD(getDiscoveredDevices:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    dispatch_async(dispatch_get_main_queue(), ^{
        resolve(self.discoveredDevices);
    });
}

#pragma mark - Connection Methods

RCT_EXPORT_METHOD(connectToDevice:(NSString *)deviceId
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
#if FLIR_SDK_AVAILABLE
    dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^{
        FLIRIdentity *identity = self.identityMap[deviceId];
        if (!identity) {
            dispatch_async(dispatch_get_main_queue(), ^{
                reject(@"ERR_DEVICE_NOT_FOUND", [NSString stringWithFormat:@"Device not found: %@", deviceId], nil);
            });
            return;
        }
        
        [self performConnectionWithIdentity:identity completion:^(BOOL success, NSError *error) {
            dispatch_async(dispatch_get_main_queue(), ^{
                if (success) {
                    resolve(@(YES));
                } else {
                    reject(@"ERR_CONNECTION_FAILED", error.localizedDescription ?: @"Connection failed", error);
                }
            });
        }];
    });
#else
    reject(@"ERR_FLIR_NOT_AVAILABLE", @"FLIR SDK not available", nil);
#endif
}

#if FLIR_SDK_AVAILABLE
- (void)performConnectionWithIdentity:(FLIRIdentity *)identity completion:(void(^)(BOOL success, NSError *error))completion {
    if (!self.camera) {
        self.camera = [[FLIRCamera alloc] init];
        self.camera.delegate = self;
    }
    
    NSError *error = nil;
    
    // Handle authentication for generic cameras
    if ([identity cameraType] == FLIRCameraType_generic) {
        NSString *certName = [self getCertificateName];
        FLIRAuthenticationStatus status = pending;
        while (status == pending) {
            status = [self.camera authenticate:identity trustedConnectionName:certName];
            if (status == pending) {
                RCTLogInfo(@"[FlirModule] Waiting for camera authentication...");
                [NSThread sleepForTimeInterval:1.0];
            }
        }
    }
    
    BOOL connected = [self.camera connect:identity error:&error];
    
    if (connected) {
        self.connectedIdentity = identity;
        self.connectedDeviceId = [identity deviceId];
        self.connectedDeviceName = [identity deviceId];
        self.isConnected = YES;
        
        RCTLogInfo(@"[FlirModule] Connected to: %@", [identity deviceId]);
        
        // Get available streams
        NSArray<FLIRStream *> *streams = [self.camera getStreams];
        if (streams.count > 0) {
            RCTLogInfo(@"[FlirModule] Found %lu streams", (unsigned long)streams.count);
            // Auto-start first stream
            [self startStreamInternal:streams[0]];
        }
        
        dispatch_async(dispatch_get_main_queue(), ^{
            [self emitDeviceConnected];
            [self emitStateChange:@"connected"];
        });
        
        if (completion) completion(YES, nil);
    } else {
        RCTLogError(@"[FlirModule] Connection failed: %@", error.localizedDescription);
        if (completion) completion(NO, error);
    }
}

- (NSString *)getCertificateName {
    NSString *bundleID = [[NSBundle mainBundle] bundleIdentifier] ?: @"com.flir.app";
    NSString *key = [NSString stringWithFormat:@"%@-cert-name", bundleID];
    
    NSString *existing = [[NSUserDefaults standardUserDefaults] stringForKey:key];
    if (existing) {
        return existing;
    }
    
    NSString *newName = [[NSUUID UUID] UUIDString];
    [[NSUserDefaults standardUserDefaults] setObject:newName forKey:key];
    return newName;
}
#endif

RCT_EXPORT_METHOD(disconnect:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
#if FLIR_SDK_AVAILABLE
    dispatch_async(dispatch_get_main_queue(), ^{
        [self stopStreamInternal];
        [self.camera disconnect];
        self.camera = nil;
        self.connectedIdentity = nil;
        self.connectedDeviceId = nil;
        self.connectedDeviceName = nil;
        self.isConnected = NO;
        self.isStreaming = NO;
        
        [[FlirEventEmitter shared] sendDeviceEvent:@"FlirDeviceDisconnected" body:@{}];
        [self emitStateChange:@"disconnected"];
        
        RCTLogInfo(@"[FlirModule] Disconnected");
        resolve(@(YES));
    });
#else
    resolve(@(YES));
#endif
}

RCT_EXPORT_METHOD(stopFlir:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
#if FLIR_SDK_AVAILABLE
    dispatch_async(dispatch_get_main_queue(), ^{
        [self stopStreamInternal];
        [self.camera disconnect];
        [self.discovery stop];
        
        self.camera = nil;
        self.connectedIdentity = nil;
        self.connectedDeviceId = nil;
        self.connectedDeviceName = nil;
        self.isConnected = NO;
        self.isStreaming = NO;
        self.isScanning = NO;
        
        [self emitStateChange:@"stopped"];
        RCTLogInfo(@"[FlirModule] Stopped");
        resolve(@(YES));
    });
#else
    resolve(@(YES));
#endif
}

#pragma mark - Streaming

#if FLIR_SDK_AVAILABLE
- (void)startStreamInternal:(FLIRStream *)newStream {
    [self stopStreamInternal];
    
    self.stream = newStream;
    
    if (newStream.isThermal) {
        self.streamer = [[FLIRThermalStreamer alloc] initWithStream:newStream];
    }
    
    newStream.delegate = self;
    
    NSError *error = nil;
    if ([newStream start:&error]) {
        self.isStreaming = YES;
        [self emitStateChange:@"streaming"];
        RCTLogInfo(@"[FlirModule] Stream started (thermal: %@)", newStream.isThermal ? @"YES" : @"NO");
    } else {
        RCTLogError(@"[FlirModule] Stream start failed: %@", error.localizedDescription);
        self.stream = nil;
        self.streamer = nil;
        [[FlirEventEmitter shared] sendDeviceEvent:@"FlirError" body:@{@"error": error.localizedDescription ?: @"Stream start failed"}];
    }
}

- (void)stopStreamInternal {
    [self.stream stop];
    self.stream = nil;
    self.streamer = nil;
    self.isStreaming = NO;
}
#endif

#pragma mark - Temperature Methods

RCT_EXPORT_METHOD(getTemperatureAt:(nonnull NSNumber *)x
                  y:(nonnull NSNumber *)y
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    dispatch_async(dispatch_get_main_queue(), ^{
        // Call into native FLIRManager to query temperature at point
        double temp = [[FLIRManager shared] getTemperatureAtPoint:[x intValue] y:[y intValue]];
        if (isnan(temp)) {
            resolve([NSNull null]);
        } else {
            resolve(@(temp));
        }
    });
}

RCT_EXPORT_METHOD(getTemperatureFromColor:(NSInteger)color
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    // Placeholder: Convert ARGB color to pseudo-temperature
    int r = (color >> 16) & 0xFF;
    int g = (color >> 8) & 0xFF;
    int b = color & 0xFF;
    double lum = 0.2126 * r + 0.7152 * g + 0.0722 * b;
    double temp = (lum / 255.0) * 400.0;
    resolve(@(temp));
}

#pragma mark - Status Methods

RCT_EXPORT_METHOD(isEmulator:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    dispatch_async(dispatch_get_main_queue(), ^{
        BOOL isEmu = [self.connectedDeviceName.lowercaseString containsString:@"emulator"] ||
                     [self.connectedDeviceName.lowercaseString containsString:@"emulat"];
        resolve(@(isEmu));
    });
}

RCT_EXPORT_METHOD(isDeviceConnected:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    dispatch_async(dispatch_get_main_queue(), ^{
        resolve(@(self.isConnected));
    });
}

RCT_EXPORT_METHOD(getConnectedDeviceInfo:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    dispatch_async(dispatch_get_main_queue(), ^{
        resolve(self.connectedDeviceName ?: @"Not connected");
    });
}

RCT_EXPORT_METHOD(isSDKDownloaded:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
#if FLIR_SDK_AVAILABLE
    resolve(@(YES));
#else
    resolve(@(NO));
#endif
}

RCT_EXPORT_METHOD(getSDKStatus:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    NSDictionary *status = @{
#if FLIR_SDK_AVAILABLE
        @"available": @(YES),
#else
        @"available": @(NO),
#endif
        @"arch": @"arm64",
        @"platform": @"iOS"
    };
    resolve(status);
}

#pragma mark - Emulator

RCT_EXPORT_METHOD(startEmulator:(NSString *)emulatorType
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
#if FLIR_SDK_AVAILABLE
    dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^{
        FLIRCameraType cameraType = FLIRCameraType_flirOne;
        if ([emulatorType.lowercaseString containsString:@"edge"]) {
            cameraType = FLIRCameraType_flirOneEdge;
        } else if ([emulatorType.lowercaseString containsString:@"pro"]) {
            cameraType = FLIRCameraType_flirOneEdgePro;
        }
        
        FLIRIdentity *emulatorIdentity = [[FLIRIdentity alloc] initWithEmulatorType:cameraType];
        if (emulatorIdentity) {
            self.identityMap[[emulatorIdentity deviceId]] = emulatorIdentity;
            
            [self performConnectionWithIdentity:emulatorIdentity completion:^(BOOL success, NSError *error) {
                dispatch_async(dispatch_get_main_queue(), ^{
                    if (success) {
                        resolve(@(YES));
                    } else {
                        reject(@"ERR_EMULATOR_FAILED", error.localizedDescription ?: @"Emulator start failed", error);
                    }
                });
            }];
        } else {
            dispatch_async(dispatch_get_main_queue(), ^{
                reject(@"ERR_EMULATOR_INIT", @"Failed to create emulator identity", nil);
            });
        }
    });
#else
    reject(@"ERR_FLIR_NOT_AVAILABLE", @"FLIR SDK not available", nil);
#endif
}

#pragma mark - Debug

RCT_EXPORT_METHOD(initializeSDK:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    NSDictionary *result = @{
#if FLIR_SDK_AVAILABLE
        @"initialized": @(YES),
        @"message": @"SDK initialized successfully"
#else
        @"initialized": @(NO),
        @"message": @"SDK not available - built without FLIR"
#endif
    };
    resolve(result);
}

RCT_EXPORT_METHOD(getDebugInfo:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    dispatch_async(dispatch_get_main_queue(), ^{
        NSDictionary *info = @{
#if FLIR_SDK_AVAILABLE
            @"sdkAvailable": @(YES),
#else
            @"sdkAvailable": @(NO),
#endif
            @"arch": @"arm64",
            @"discoveredDeviceCount": @(self.discoveredDevices.count),
            @"isConnected": @(self.isConnected),
            @"isStreaming": @(self.isStreaming),
            @"connectedDevice": self.connectedDeviceName ?: @"None"
        };
        resolve(info);
    });
}

RCT_EXPORT_METHOD(getLatestFramePath:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    dispatch_async(dispatch_get_main_queue(), ^{
        UIImage *image = [FlirState shared].latestImage;
        if (!image) {
            resolve([NSNull null]);
            return;
        }
        
        NSData *jpegData = UIImageJPEGRepresentation(image, 0.9);
        if (!jpegData) {
            resolve([NSNull null]);
            return;
        }
        
        NSString *tempPath = [NSTemporaryDirectory() stringByAppendingPathComponent:
                              [NSString stringWithFormat:@"flir_frame_%lld.jpg", (long long)[[NSDate date] timeIntervalSince1970] * 1000]];
        [jpegData writeToFile:tempPath atomically:YES];
        resolve(tempPath);
    });
}

RCT_EXPORT_METHOD(getBatteryLevel:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    dispatch_async(dispatch_get_main_queue(), ^{
        #if FLIR_SDK_AVAILABLE
        int level = [[FLIRManager shared] getBatteryLevel];
        resolve(@(level));
        #else
        resolve(@(-1));
        #endif
    });
}

RCT_EXPORT_METHOD(isBatteryCharging:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    dispatch_async(dispatch_get_main_queue(), ^{
        #if FLIR_SDK_AVAILABLE
        BOOL ch = [[FLIRManager shared] isBatteryCharging];
        resolve(@(ch));
        #else
        resolve(@(NO));
        #endif
    });
}

RCT_EXPORT_METHOD(setPreferSdkRotation:(BOOL)prefer
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    dispatch_async(dispatch_get_main_queue(), ^{
        @try {
            [[FLIRManager shared] setPreferSdkRotation:prefer];
            resolve(@(YES));
        } @catch (NSException *ex) {
            reject(@"ERR_FLIR_SET_ROTATION_PREF", ex.reason, nil);
        }
    });
}

RCT_EXPORT_METHOD(isPreferSdkRotation:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    dispatch_async(dispatch_get_main_queue(), ^{
        BOOL v = [[FLIRManager shared] isPreferSdkRotation];
        resolve(@(v));
    });
}

#pragma mark - Helper Methods

- (void)emitDeviceConnected {
    BOOL isEmu = [self.connectedDeviceName.lowercaseString containsString:@"emulator"];
    
    NSDictionary *body = @{
        @"identity": @{
            @"deviceId": self.connectedDeviceId ?: @"Unknown",
            @"isEmulator": @(isEmu)
        },
        @"deviceType": isEmu ? @"emulator" : @"device",
        @"isEmulator": @(isEmu),
        @"state": @"connected"
    };
    
    [[FlirEventEmitter shared] sendDeviceEvent:@"FlirDeviceConnected" body:body];
}

- (void)emitStateChange:(NSString *)state {
    BOOL isEmu = [self.connectedDeviceName.lowercaseString containsString:@"emulator"];
    
    NSDictionary *body = @{
        @"state": state,
        @"isConnected": @(self.isConnected),
        @"isStreaming": @(self.isStreaming),
        @"isEmulator": @(isEmu),
        @"deviceName": self.connectedDeviceName ?: @"",
        @"deviceId": self.connectedDeviceId ?: @""
    };
    
    [[FlirEventEmitter shared] sendDeviceEvent:@"FlirStateChanged" body:body];
}

#if FLIR_SDK_AVAILABLE
- (NSString *)communicationInterfaceName:(FLIRCommunicationInterface)iface {
    if (iface & FLIRCommunicationInterfaceLightning) return @"LIGHTNING";
    if (iface & FLIRCommunicationInterfaceNetwork) return @"NETWORK";
    if (iface & FLIRCommunicationInterfaceFlirOneWireless) return @"WIRELESS";
    if (iface & FLIRCommunicationInterfaceEmulator) return @"EMULATOR";
    if (iface & FLIRCommunicationInterfaceUSB) return @"USB";
    return @"UNKNOWN";
}
#endif

#pragma mark - FLIRDiscoveryEventDelegate

#if FLIR_SDK_AVAILABLE
- (void)cameraDiscovered:(FLIRDiscoveredCamera *)discoveredCamera {
    FLIRIdentity *identity = discoveredCamera.identity;
    NSString *deviceId = [identity deviceId];
    
    RCTLogInfo(@"[FlirModule] Camera discovered: %@", deviceId);
    
    // Store identity
    self.identityMap[deviceId] = identity;
    
    // Create device info
    NSDictionary *deviceInfo = @{
        @"id": deviceId,
        @"name": discoveredCamera.displayName ?: deviceId,
        @"communicationType": [self communicationInterfaceName:[identity communicationInterface]],
        @"isEmulator": @([identity communicationInterface] == FLIRCommunicationInterfaceEmulator)
    };
    
    // Add if not already present
    BOOL found = NO;
    for (NSDictionary *existing in self.discoveredDevices) {
        if ([existing[@"id"] isEqualToString:deviceId]) {
            found = YES;
            break;
        }
    }
    
    if (!found) {
        [self.discoveredDevices addObject:deviceInfo];
    }
    
    // Emit devices found event
    dispatch_async(dispatch_get_main_queue(), ^{
        NSDictionary *body = @{
            @"devices": self.discoveredDevices,
            @"count": @(self.discoveredDevices.count)
        };
        [[FlirEventEmitter shared] sendDeviceEvent:@"FlirDevicesFound" body:body];
    });
}

- (void)cameraLost:(FLIRIdentity *)cameraIdentity {
    NSString *deviceId = [cameraIdentity deviceId];
    RCTLogInfo(@"[FlirModule] Camera lost: %@", deviceId);
    
    [self.identityMap removeObjectForKey:deviceId];
    
    NSMutableArray *toRemove = [NSMutableArray new];
    for (NSDictionary *device in self.discoveredDevices) {
        if ([device[@"id"] isEqualToString:deviceId]) {
            [toRemove addObject:device];
        }
    }
    [self.discoveredDevices removeObjectsInArray:toRemove];
    
    // If this was our connected device, handle disconnect
    if ([self.connectedDeviceId isEqualToString:deviceId]) {
        dispatch_async(dispatch_get_main_queue(), ^{
            [self stopStreamInternal];
            self.camera = nil;
            self.connectedIdentity = nil;
            self.connectedDeviceId = nil;
            self.connectedDeviceName = nil;
            self.isConnected = NO;
            self.isStreaming = NO;
            
            [[FlirEventEmitter shared] sendDeviceEvent:@"FlirDeviceDisconnected" body:@{}];
            [self emitStateChange:@"disconnected"];
        });
    }
    
    dispatch_async(dispatch_get_main_queue(), ^{
        NSDictionary *body = @{
            @"devices": self.discoveredDevices,
            @"count": @(self.discoveredDevices.count)
        };
        [[FlirEventEmitter shared] sendDeviceEvent:@"FlirDevicesFound" body:body];
    });
}

- (void)discoveryError:(NSString *)error netServiceError:(int)nsnetserviceserror on:(FLIRCommunicationInterface)iface {
    RCTLogError(@"[FlirModule] Discovery error: %@ (%d)", error, nsnetserviceserror);
    
    dispatch_async(dispatch_get_main_queue(), ^{
        [[FlirEventEmitter shared] sendDeviceEvent:@"FlirError" body:@{
            @"error": error ?: @"Unknown discovery error",
            @"type": @"discovery"
        }];
    });
}

- (void)discoveryFinished:(FLIRCommunicationInterface)iface {
    RCTLogInfo(@"[FlirModule] Discovery finished");
    self.isScanning = NO;
}
#endif

#pragma mark - FLIRDataReceivedDelegate

#if FLIR_SDK_AVAILABLE
- (void)onDisconnected:(FLIRCamera *)camera withError:(NSError *)error {
    RCTLogInfo(@"[FlirModule] Camera disconnected: %@", error.localizedDescription);
    
    dispatch_async(dispatch_get_main_queue(), ^{
        self.isConnected = NO;
        self.isStreaming = NO;
        self.connectedDeviceId = nil;
        self.connectedDeviceName = nil;
        
        [[FlirEventEmitter shared] sendDeviceEvent:@"FlirDeviceDisconnected" body:@{}];
        [self emitStateChange:@"disconnected"];
    });
}
#endif

#pragma mark - FLIRStreamDelegate

#if FLIR_SDK_AVAILABLE
- (void)onError:(NSError *)error {
    RCTLogError(@"[FlirModule] Stream error: %@", error.localizedDescription);
    
    dispatch_async(dispatch_get_main_queue(), ^{
        [[FlirEventEmitter shared] sendDeviceEvent:@"FlirError" body:@{
            @"error": error.localizedDescription ?: @"Stream error",
            @"type": @"stream"
        }];
    });
}

- (void)onImageReceived {
    if (!self.streamer) return;
    
    NSError *error = nil;
    if ([self.streamer update:&error]) {
        UIImage *image = [self.streamer getImage];
        if (image) {
            // Update shared state
            [[FlirState shared] updateFrame:image];
            
            // Get temperature from thermal image if available
            [self.streamer withThermalImage:^(FLIRThermalImage *thermalImage) {
                FLIRImageStatistics *stats = [thermalImage getStatistics];
                if (stats) {
                    self.lastTemperature = [[stats getMax] value];
                    [FlirState shared].lastTemperature = self.lastTemperature;
                }
            }];
            
            // Emit frame received event (rate-limited by RN event queue)
            dispatch_async(dispatch_get_main_queue(), ^{
                [[FlirEventEmitter shared] sendDeviceEvent:@"FlirFrameReceived" body:@{
                    @"width": @(image.size.width),
                    @"height": @(image.size.height),
                    @"timestamp": @([[NSDate date] timeIntervalSince1970] * 1000)
                }];
            });
        }
    } else {
        RCTLogError(@"[FlirModule] Streamer update error: %@", error.localizedDescription);
    }
}
#endif

@end
