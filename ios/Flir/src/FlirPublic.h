// FlirPublic.h
// Public C/ObjC API for the Flir library
//
// NOTE: FlirDeviceInfo and FlirManager are defined in FlirManager.swift.
// The Swift compiler generates Flir-Swift.h which exports these classes to Objective-C.
// Do NOT define duplicate @interface declarations here to avoid "Duplicate interface" errors.

#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

// Forward declarations for Swift classes (defined in FlirManager.swift)
// Use the Flir-Swift.h import in implementation files to access these.
@class FlirDeviceInfo;
@class FlirManager;

/// Protocol for receiving FLIR events from FlirManager
/// Implemented by FlirModule.m to bridge events to React Native
@protocol FlirPublicDelegate <NSObject>
- (void)onDevicesFound:(NSArray<FlirDeviceInfo *> *)devices;
- (void)onDeviceConnected:(FlirDeviceInfo *)device;
- (void)onDeviceDisconnected;
- (void)onFrameReceived:(UIImage *)image width:(NSInteger)width height:(NSInteger)height;
@optional
- (void)onFrameReceivedRaw:(NSData *)data width:(NSInteger)width height:(NSInteger)height bytesPerRow:(NSInteger)bytesPerRow timestamp:(double)timestamp;
- (void)onError:(NSString *)message;
- (void)onStateChanged:(NSString *)state isConnected:(BOOL)isConnected isStreaming:(BOOL)isStreaming isEmulator:(BOOL)isEmulator;
@end

NS_ASSUME_NONNULL_END

