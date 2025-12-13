//
//  Flir-Bridging-Header.h
//  Flir
//
//  Bridging header for Swift/Objective-C interoperability
//  Note: This header is used when building the package as part of an app
//

#ifndef Flir_Bridging_Header_h
#define Flir_Bridging_Header_h

// Note: React headers are provided by the app's build system
// These imports are for documentation purposes when building within Xcode
#if __has_include(<React/RCTBridgeModule.h>)
#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>
#import <React/RCTLog.h>
#import <React/RCTViewManager.h>
#endif

// FLIR module headers (local)
#import "FlirEventEmitter.h"
#import "FlirModule.h"
#import "FlirPreviewView.h"
#import "FlirState.h"
#import "FlirViewManager.h"

// ThermalSDK if available
#if __has_include(<ThermalSDK/ThermalSDK.h>)
#import <ThermalSDK/ThermalSDK.h>
#define FLIR_SDK_AVAILABLE 1
#endif

#endif /* Flir_Bridging_Header_h */
