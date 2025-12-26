// FlirPublic.h
// Public C/ObjC API for the Flir library

#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

@interface FlirDeviceInfo : NSObject
@property (nonatomic, readonly) NSString *deviceId;
@property (nonatomic, readonly) NSString *name;
@property (nonatomic, readonly) NSString *communicationType;
@property (nonatomic, readonly) BOOL isEmulator;
- (NSDictionary *)toDictionary;
@end

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

@interface FlirManager : NSObject
+ (instancetype)shared NS_SWIFT_NAME(shared);

@property (nonatomic, weak, nullable) id<FlirPublicDelegate> delegate;

// Lifecycle
- (void)startDiscovery;
- (void)stopDiscovery;
- (void)connectToDevice:(NSString *)deviceId;
- (void)disconnect;
- (void)stop;

// Frame accessors
- (nullable NSDictionary *)latestFrameBitmapBase64; // { width, height, bytesPerRow, dataBase64 }
- (nullable NSString *)latestFrameBase64;

// Utilities
- (NSInteger)getBatteryLevel;
- (BOOL)isBatteryCharging;

@end

NS_ASSUME_NONNULL_END
