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
// Prefer React/ headers, but fall back to ReactCore or local headers for different RN layouts
#if __has_include(<React/RCTBridgeModule.h>)
#import <React/RCTBridgeModule.h>
#if __has_include(<React/RCTEventEmitter.h>)
#import <React/RCTEventEmitter.h>
#endif
#if __has_include(<React/RCTLog.h>)
#import <React/RCTLog.h>
#endif
#if __has_include(<React/RCTViewManager.h>)
#import <React/RCTViewManager.h>
#endif
#elif __has_include(<ReactCore/RCTBridgeModule.h>)
#import <ReactCore/RCTBridgeModule.h>
#if __has_include(<ReactCore/RCTEventEmitter.h>)
#import <ReactCore/RCTEventEmitter.h>
#elif __has_include(<React/RCTEventEmitter.h>)
#import <React/RCTEventEmitter.h>
#endif
#if __has_include(<ReactCore/RCTLog.h>)
#import <ReactCore/RCTLog.h>
#elif __has_include(<React/RCTLog.h>)
#import <React/RCTLog.h>
#endif
#if __has_include(<ReactCore/RCTViewManager.h>)
#import <ReactCore/RCTViewManager.h>
#elif __has_include(<React/RCTViewManager.h>)
#import <React/RCTViewManager.h>
#endif
#elif __has_include("RCTBridgeModule.h")
#import "RCTBridgeModule.h"
#import "RCTEventEmitter.h"
#import "RCTLog.h"
#import "RCTViewManager.h"
#else
#import <Foundation/Foundation.h>
@interface RCTEventEmitter : NSObject
@end
@protocol RCTBridgeModule <NSObject>
@end
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
