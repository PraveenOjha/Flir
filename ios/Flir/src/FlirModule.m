#import \"FlirModule.h\"\n#import \"FlirEventEmitter.h\"\n#import \"FlirState.h\"\n#import <React/RCTLog.h>\n#import <ThermalSDK/ThermalSDK.h>\n\n@interface FlirModule() <FLIRDiscoveryEventDelegate>\n@property (nonatomic, strong) FLIRDiscovery *discovery;\n@property (nonatomic, strong) FLIRCamera *camera;\n@property (nonatomic, strong) FLIRIdentity *connectedIdentity;\n@property (nonatomic, assign) BOOL isEmulatorMode;\n@property (nonatomic, assign) BOOL isPhysicalDeviceConnected;\n@end
#import <ThermalSDK/ThermalSDK.h>

@interface FlirModule()
@property (nonatomic, strong) FLIRDiscovery *discovery;
@property (nonatomic, strong) FLIRCamera *camera;
@property (nonatomic, strong) FLIRIdentity *connectedIdentity;
@property (nonatomic, assign) BOOL isEmulatorMode;
@property (nonatomic, assign) BOOL isPhysicalDeviceConnected;
@end

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

RCT_EXPORT_METHOD(getTemperatureAt:(nonnull NSNumber *)x y:(nonnull NSNumber *)y resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject)\n{\n  // Return the last sampled temperature from the preview/state singleton\n  dispatch_async(dispatch_get_main_queue(), ^{\n    double t = [FlirState shared].lastTemperature;\n    if (isnan(t)) {\n      resolve([NSNull null]);\n    } else {\n      resolve(@(t));\n    }\n  });\n}\n\nRCT_EXPORT_METHOD(isEmulator:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject)\n{\n  dispatch_async(dispatch_get_main_queue(), ^{\n    resolve(@(self.isEmulatorMode));\n  });\n}\n\nRCT_EXPORT_METHOD(isDeviceConnected:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject)\n{\n  dispatch_async(dispatch_get_main_queue(), ^{\n    resolve(@(self.isPhysicalDeviceConnected));\n  });\n}\n\nRCT_EXPORT_METHOD(getConnectedDeviceInfo:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject)\n{\n  dispatch_async(dispatch_get_main_queue(), ^{\n    if (self.connectedIdentity == nil) {\n      resolve(@\"Not connected\");\n    } else if (self.isEmulatorMode) {\n      resolve([NSString stringWithFormat:@\"Emulator (%@)\", self.connectedIdentity.deviceId]);\n    } else {\n      resolve([NSString stringWithFormat:@\"Physical device (%@)\", self.connectedIdentity.deviceId]);\n    }\n  });\n}\n\nRCT_EXPORT_METHOD(startEmulatorMode:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject)\n{\n  dispatch_async(dispatch_get_main_queue(), ^{\n    [self initializeEmulatorMode];\n    resolve(@(YES));\n  });\n}\n\n- (void)initializeEmulatorMode\n{\n  if (!self.discovery) {\n    self.discovery = [[FLIRDiscovery alloc] init];\n    self.discovery.delegate = self;\n  }\n  \n  // Create emulator identity for testing\n  FLIRIdentity *emulatorIdentity = [[FLIRIdentity alloc] initWithEmulatorType:F1_gen3];\n  if (emulatorIdentity) {\n    self.connectedIdentity = emulatorIdentity;\n    self.isEmulatorMode = YES;\n    self.isPhysicalDeviceConnected = NO;\n    \n    // Start discovery for emulator\n    [self.discovery start:FLIRCommunicationInterfaceEmulator cameraType:F1_gen3];\n    \n    // Emit connection event\n    [[FlirEventEmitter shared] sendDeviceEvent:@\"FlirDeviceConnected\" body:@{\n      @\"identity\": @{\n        @\"deviceId\": emulatorIdentity.deviceId ?: @\"Emulator\",\n        @\"isEmulator\": @(YES)\n      },\n      @\"deviceType\": @\"emulator\",\n      @\"isEmulator\": @(YES)\n    }];\n  }\n}

#pragma mark - FLIRDiscoveryEventDelegate

- (void)cameraDiscovered:(FLIRDiscoveredCamera *)discoveredCamera {
  FLIRIdentity *identity = discoveredCamera.identity;
  
  // Check if this is a real device or emulator
  BOOL isRealDevice = (identity.communicationInterface != FLIRCommunicationInterfaceEmulator);
  
  if (isRealDevice && !self.isPhysicalDeviceConnected) {
    // Prioritize real device over emulator
    self.connectedIdentity = identity;
    self.isEmulatorMode = NO;
    self.isPhysicalDeviceConnected = YES;
    
    // Connect to real device
    [self connectToDevice:identity];
  } else if (!isRealDevice && !self.isPhysicalDeviceConnected) {
    // Use emulator if no real device found
    self.connectedIdentity = identity;
    self.isEmulatorMode = YES;
    self.isPhysicalDeviceConnected = NO;
    
    // Connect to emulator
    [self connectToDevice:identity];
  }
}

#pragma mark - Helper Methods

- (void)connectToDevice:(FLIRIdentity *)identity {
  if (!self.camera) {
    self.camera = [[FLIRCamera alloc] init];
  }
  
  NSError *error;
  BOOL connected = [self.camera connect:identity error:&error];
  
  if (connected) {
    NSString *deviceType = self.isEmulatorMode ? @"emulator" : @"device";
    [[FlirEventEmitter shared] sendDeviceEvent:@"FlirDeviceConnected" body:@{
      @"identity": @{
        @"deviceId": identity.deviceId ?: @"Unknown",
        @"isEmulator": @(self.isEmulatorMode)
      },
      @"deviceType": deviceType,
      @"isEmulator": @(self.isEmulatorMode)
    }];
  } else {
    RCTLogError(@"Failed to connect to FLIR device: %@", error.localizedDescription);
  }
}

#pragma mark - FLIRDiscoveryEventDelegate

- (void)cameraFound:(FLIRIdentity *)identity
{
  // Store the found camera identity
  self.connectedIdentity = identity;
  
  // Check if this is an emulator
  self.isEmulatorMode = (identity.communicationInterface == FLIRCommunicationInterfaceEmulator);
  self.isPhysicalDeviceConnected = !self.isEmulatorMode;
  
  // Emit connection event
  [[FlirEventEmitter shared] sendDeviceEvent:@"FlirDeviceConnected" body:@{
    @"identity": @{
      @"deviceId": identity.deviceId ?: @"Unknown",
      @"isEmulator": @(self.isEmulatorMode)
    },
    @"deviceType": self.isEmulatorMode ? @"emulator" : @"physical",
    @"isEmulator": @(self.isEmulatorMode)
  }];
}

- (void)cameraLost:(FLIRIdentity *)identity
{
  // Clear connection state
  self.connectedIdentity = nil;
  self.isEmulatorMode = NO;
  self.isPhysicalDeviceConnected = NO;
  
  // Emit disconnection event
  [[FlirEventEmitter shared] sendDeviceEvent:@"FlirDeviceDisconnected" body:@{
    @"identity": @{
      @"deviceId": identity.deviceId ?: @"Unknown",
      @"isEmulator": @(identity.communicationInterface == FLIRCommunicationInterfaceEmulator)
    },
    @"wasEmulator": @(identity.communicationInterface == FLIRCommunicationInterfaceEmulator)
  }];
}

- (void)discoveryError:(NSString *)error netServiceError:(int)nsnetserviceserror on:(FLIRCommunicationInterface)iface
{
  // Emit error event
  [[FlirEventEmitter shared] sendDeviceEvent:@"FlirError" body:@{
    @"error": error ?: @"Unknown discovery error",
    @"type": @"discovery",
    @"interface": @(iface)
  }];
}

@end
