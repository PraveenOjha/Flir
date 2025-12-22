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
#import <objc/message.h>
#import <objc/runtime.h>

#if __has_include(<ThermalSDK/ThermalSDK.h>)
#define FLIR_SDK_AVAILABLE 1
#import <ThermalSDK/ThermalSDK.h>
#else
#define FLIR_SDK_AVAILABLE 0
#endif

// Use runtime lookup to avoid a hard link-time dependency on `FLIRManager`.
// This prevents duplicate-definition and missing-symbol build failures when
// the Swift `FLIRManager` may or may not be available at build/link time.
static id flir_manager_shared(void) {
    Class cls = NSClassFromString(@"FLIRManager");
    if (!cls) return nil;
    SEL sel = sel_registerName("shared");
    if (![cls respondsToSelector:sel]) return nil;
    id (*msgSend0)(id, SEL) = (id (*)(id, SEL))objc_msgSend;
    return msgSend0((id)cls, sel);
}

static double flir_getTemperatureAtPoint(int x, int y) {
    id inst = flir_manager_shared();
    if (!inst) return NAN;
    SEL sel = sel_registerName("getTemperatureAtPoint:y:");
    if (![inst respondsToSelector:sel]) return NAN;
    double (*msgSend2)(id, SEL, int, int) = (double (*)(id, SEL, int, int))objc_msgSend;
    return msgSend2(inst, sel, x, y);
}

static int flir_getBatteryLevel(void) {
    id inst = flir_manager_shared();
    if (!inst) return -1;
    SEL sel = sel_registerName("getBatteryLevel");
    if (![inst respondsToSelector:sel]) return -1;
    int (*msgSend0)(id, SEL) = (int (*)(id, SEL))objc_msgSend;
    return msgSend0(inst, sel);
}

static BOOL flir_isBatteryCharging(void) {
    id inst = flir_manager_shared();
    if (!inst) return NO;
    SEL sel = sel_registerName("isBatteryCharging");
    if (![inst respondsToSelector:sel]) return NO;
    BOOL (*msgSend0)(id, SEL) = (BOOL (*)(id, SEL))objc_msgSend;
    return msgSend0(inst, sel);
}

static void flir_setPreferSdkRotation(BOOL prefer) {
    id inst = flir_manager_shared();
    if (!inst) return;
    SEL sel = sel_registerName("setPreferSdkRotation:");
    if (![inst respondsToSelector:sel]) return;
    void (*msgSend1)(id, SEL, BOOL) = (void (*)(id, SEL, BOOL))objc_msgSend;
    msgSend1(inst, sel, prefer);
}

static BOOL flir_isPreferSdkRotation(void) {
    id inst = flir_manager_shared();
    if (!inst) return NO;
    SEL sel = sel_registerName("isPreferSdkRotation");
    if (![inst respondsToSelector:sel]) return NO;
    BOOL (*msgSend0)(id, SEL) = (BOOL (*)(id, SEL))objc_msgSend;
    return msgSend0(inst, sel);
}

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

- (void)emitStateChange:(NSString *)state;
- (void)emitDeviceConnected;
- (void)stopStreamInternal;

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

// Network discovery on iOS 14+ requires Local Network privacy keys.
// In USB/Bluetooth-only builds (or when the user denied permission), attempting
// Bonjour discovery can fail noisily or crash depending on SDK internals.
// We default to enabling network discovery only when the host app declares
// NSLocalNetworkUsageDescription, and allow an explicit override via
// setNetworkDiscoveryEnabled.
- (BOOL)shouldEnableNetworkDiscovery {
    // Explicit override if app sets it.
    NSString *key = @"ilabsFlir.networkDiscoveryEnabled";
    NSUserDefaults *defaults = [NSUserDefaults standardUserDefaults];
    if ([defaults objectForKey:key] != nil) {
        return [defaults boolForKey:key];
    }

    // Safe default: require Local Network usage description to be present.
    id desc = [[NSBundle mainBundle] objectForInfoDictionaryKey:@"NSLocalNetworkUsageDescription"];
    if ([desc isKindOfClass:[NSString class]] && ((NSString *)desc).length > 0) {
        return YES;
    }
    return NO;
}

RCT_EXPORT_METHOD(setNetworkDiscoveryEnabled:(BOOL)enabled
                  resolver:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
#if FLIR_SDK_AVAILABLE
    [[NSUserDefaults standardUserDefaults] setBool:enabled forKey:@"ilabsFlir.networkDiscoveryEnabled"];
    resolve(@(YES));
#else
    resolve(@(YES));
#endif
}

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

        // Always expose emulator options to JS/UI so the user can connect even when
        // physical devices are not present.
        NSDictionary *emuOne = @{
            @"id": @"emu:FLIR_ONE",
            @"name": @"FLIR One Emulator",
            @"communicationType": @"EMULATOR",
            @"isEmulator": @(YES)
        };
        NSDictionary *emuEdge = @{
            @"id": @"emu:FLIR_ONE_EDGE",
            @"name": @"FLIR One Edge Emulator",
            @"communicationType": @"EMULATOR",
            @"isEmulator": @(YES)
        };
        [self.discoveredDevices addObjectsFromArray:@[ emuOne, emuEdge ]];

        NSDictionary *initialDevicesBody = @{
            @"devices": self.discoveredDevices,
            @"count": @(self.discoveredDevices.count)
        };
        [[FlirEventEmitter shared] sendDeviceEvent:@"FlirDevicesFound" body:initialDevicesBody];
        
        if (!self.discovery) {
            self.discovery = [[FLIRDiscovery alloc] init];
            self.discovery.delegate = self;
        }
        
        // Start discovery on allowed interfaces.
        // Always include wired/BLE/emulator. Only include network when the app has
        // Local Network usage description (or the app explicitly enabled it).
        FLIRCommunicationInterface interfaces = FLIRCommunicationInterfaceLightning |
                                                 FLIRCommunicationInterfaceFlirOneWireless |
                                                 FLIRCommunicationInterfaceEmulator |
                                                 FLIRCommunicationInterfaceUSB;
        if ([self shouldEnableNetworkDiscovery]) {
            interfaces |= FLIRCommunicationInterfaceNetwork;
        } else {
            RCTLogInfo(@"[FlirModule] Network discovery disabled (missing NSLocalNetworkUsageDescription or overridden)");
        }
        [self.discovery start:interfaces];
        
        [self emitStateChange:@"discovering"];
        RCTLogInfo(@"[FlirModule] Discovery started");

        __weak typeof(self) weakSelf = self;
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(6 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
            __strong typeof(self) strongSelf = weakSelf;
            if (!strongSelf) return;
            if (!strongSelf.isScanning || strongSelf.isConnected) return;

            BOOL hasRealDevice = NO;
            for (NSDictionary *dev in strongSelf.discoveredDevices) {
                NSString *did = dev[@"id"];
                if (did.length > 0 && ![did hasPrefix:@"emu:"]) {
                    hasRealDevice = YES;
                    break;
                }
            }
            if (!hasRealDevice) {
                [strongSelf emitStateChange:@"no_device_found"];
            }
        });

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
        // Synthetic emulator ids exposed during discovery.
        if ([deviceId hasPrefix:@"emu:"]) {
            NSString *typePart = [deviceId substringFromIndex:4];
            FLIRCameraType cameraType = FLIRCameraType_flirOne;
            if ([typePart.lowercaseString containsString:@"edge"]) {
                cameraType = FLIRCameraType_flirOneEdge;
            }

            FLIRIdentity *emulatorIdentity = [[FLIRIdentity alloc] initWithEmulatorType:cameraType];
            if (!emulatorIdentity) {
                dispatch_async(dispatch_get_main_queue(), ^{
                    reject(@"ERR_EMULATOR_INIT", @"Failed to create emulator identity", nil);
                });
                return;
            }

            self.identityMap[[emulatorIdentity deviceId]] = emulatorIdentity;

            [self performConnectionWithIdentity:emulatorIdentity completion:^(BOOL success, NSError *error) {
                dispatch_async(dispatch_get_main_queue(), ^{
                    if (success) {
                        resolve(@(YES));
                    } else {
                        reject(@"ERR_CONNECTION_FAILED", error.localizedDescription ?: @"Connection failed", error);
                    }
                });
            }];
            return;
        }

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
    
    // Handle authentication for generic cameras (network cameras)
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
        RCTLogInfo(@"[FlirModule] Authentication status: %d", (int)status);
    }
    
    // Step 1: Pair with camera (required for FLIR One devices)
    @try {
        if (![self.camera pair:identity code:0 error:&error]) {
            RCTLogError(@"[FlirModule] Pair failed: %@", error.localizedDescription);
            if (completion) completion(NO, error);
            return;
        }
        RCTLogInfo(@"[FlirModule] Paired with: %@", [identity deviceId]);
    } @catch (NSException *exception) {
        RCTLogError(@"[FlirModule] Pair exception: %@", exception.reason);
        NSError *pairError = [NSError errorWithDomain:@"FlirModule" code:1001 userInfo:@{NSLocalizedDescriptionKey: exception.reason ?: @"Pair failed"}];
        if (completion) completion(NO, pairError);
        return;
    }
    
    // Step 2: Connect to camera
    BOOL connected = NO;
    @try {
        if (![self.camera connect:&error]) {
            RCTLogError(@"[FlirModule] Connect failed: %@", error.localizedDescription);
            if (completion) completion(NO, error);
            return;
        }
        connected = YES;
        RCTLogInfo(@"[FlirModule] Connected to: %@", [identity deviceId]);
    } @catch (NSException *exception) {
        RCTLogError(@"[FlirModule] Connect exception: %@", exception.reason);
        error = [NSError errorWithDomain:@"FlirModule" code:1002 userInfo:@{NSLocalizedDescriptionKey: exception.reason ?: @"Connect failed"}];
        connected = NO;
    }
    
    if (connected) {
        self.connectedIdentity = identity;
        self.connectedDeviceId = [identity deviceId];
        NSString *displayName = [identity deviceId];
        if ([identity communicationInterface] == FLIRCommunicationInterfaceEmulator) {
            if ([identity cameraType] == FLIRCameraType_flirOneEdge || [identity cameraType] == FLIRCameraType_flirOneEdgePro) {
                displayName = @"FLIR One Edge Emulator";
            } else {
                displayName = @"FLIR One Emulator";
            }
        }
        self.connectedDeviceName = displayName;
        self.isConnected = YES;
        
        RCTLogInfo(@"[FlirModule] Successfully connected to: %@", displayName);
        
        // Get available streams and prefer thermal stream
        NSArray<FLIRStream *> *streams = [self.camera getStreams];
        if (streams.count > 0) {
            RCTLogInfo(@"[FlirModule] Found %lu stream(s)", (unsigned long)streams.count);
            
            // Find thermal stream (preferred) or use first stream
            FLIRStream *streamToStart = nil;
            for (FLIRStream *stream in streams) {
                if (stream.isThermal) {
                    streamToStart = stream;
                    break;
                }
            }
            if (!streamToStart) {
                streamToStart = streams[0];
            }
            [self startStreamInternal:streamToStart];
        }
        
        dispatch_async(dispatch_get_main_queue(), ^{
            [self emitDeviceConnected];
        });
        
        if (completion) completion(YES, nil);
    } else {
        RCTLogError(@"[FlirModule] Connection failed: %@", error.localizedDescription);
        self.camera = nil;
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
    // Use try-catch pattern from samples
    @try {
        if (![newStream start:&error]) {
            RCTLogError(@"[FlirModule] Stream start failed: %@", error.localizedDescription);
            self.stream = nil;
            self.streamer = nil;
            [[FlirEventEmitter shared] sendDeviceEvent:@"FlirError" body:@{@"error": error.localizedDescription ?: @"Stream start failed"}];
            return;
        }
    } @catch (NSException *exception) {
        RCTLogError(@"[FlirModule] Stream start exception: %@", exception.reason);
        self.stream = nil;
        self.streamer = nil;
        [[FlirEventEmitter shared] sendDeviceEvent:@"FlirError" body:@{@"error": exception.reason ?: @"Stream start exception"}];
        return;
    }
    
    self.isStreaming = YES;
    [self emitStateChange:@"streaming"];
    RCTLogInfo(@"[FlirModule] Stream started (thermal: %@)", newStream.isThermal ? @"YES" : @"NO");
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
        // Call into native FLIRManager to query temperature at point (runtime lookup)
        double temp = flir_getTemperatureAtPoint([x intValue], [y intValue]);
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
        int level = flir_getBatteryLevel();
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
        BOOL ch = flir_isBatteryCharging();
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
            flir_setPreferSdkRotation(prefer);
            resolve(@(YES));
        } @catch (NSException *ex) {
            reject(@"ERR_FLIR_SET_ROTATION_PREF", ex.reason, nil);
        }
    });
}

RCT_EXPORT_METHOD(isPreferSdkRotation:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject) {
    dispatch_async(dispatch_get_main_queue(), ^{
        BOOL v = flir_isPreferSdkRotation();
        resolve(@(v));
    });
}

#pragma mark - Helper Methods

- (void)emitDeviceConnected {
    [self emitStateChange:@"connected"];
}

- (void)emitStateChange:(NSString *)state {
    BOOL isEmu = NO;
#if FLIR_SDK_AVAILABLE
    if (self.connectedIdentity) {
        isEmu = ([self.connectedIdentity communicationInterface] == FLIRCommunicationInterfaceEmulator);
    } else {
        isEmu = [self.connectedDeviceName.lowercaseString containsString:@"emulator"];
    }
#else
    isEmu = [self.connectedDeviceName.lowercaseString containsString:@"emulator"];
#endif
    
    NSDictionary *body = @{
        @"state": state,
        @"isConnected": @(self.isConnected),
        @"isStreaming": @(self.isStreaming),
        @"isEmulator": @(isEmu),
        @"deviceName": self.connectedDeviceName ?: @"",
        @"deviceId": self.connectedDeviceId ?: @"",
        @"identity": @{
            @"deviceId": self.connectedDeviceId ?: @"",
            @"isEmulator": @(isEmu)
        }
    };

    // App JS listens for FlirDeviceConnected state transitions.
    [[FlirEventEmitter shared] sendDeviceEvent:@"FlirDeviceConnected" body:body];

    // Keep legacy event for backwards compatibility.
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
    // Network discovery failures are expected when Local Network permission is missing/denied.
    // Do not surface those as fatal errors; keep USB/BLE discovery running.
    if ((iface & FLIRCommunicationInterfaceNetwork) == FLIRCommunicationInterfaceNetwork) {
        RCTLogInfo(@"[FlirModule] Network discovery error (suppressed): %@ (%d)", error, nsnetserviceserror);
        return;
    }

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
                // Some SDK versions call getImageStatistics(), try both selectors
                FLIRImageStatistics *stats = nil;
                if ([thermalImage respondsToSelector:sel_registerName("getImageStatistics")]) {
                    stats = ((id (*)(id, SEL))objc_msgSend)((id)thermalImage, sel_registerName("getImageStatistics"));
                } else if ([thermalImage respondsToSelector:sel_registerName("getStatistics")]) {
                    stats = ((id (*)(id, SEL))objc_msgSend)((id)thermalImage, sel_registerName("getStatistics"));
                }
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
