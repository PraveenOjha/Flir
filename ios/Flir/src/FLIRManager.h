// ObjC shim to expose `FLIRManager` from the npm package and forward to `FlirManager` at runtime
#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

@interface FLIRManager : NSObject

+ (instancetype)shared;

- (BOOL)isAvailable;
- (double)getTemperatureAtPoint:(int)x y:(int)y;
- (double)getTemperatureAtNormalized:(double)nx y:(double)ny;
- (int)getBatteryLevel;
- (BOOL)isBatteryCharging;
- (void)setPreferSdkRotation:(BOOL)prefer;
- (BOOL)isPreferSdkRotation;
- (nullable UIImage *)latestFrameImage;
- (void)startDiscovery;
- (void)stopDiscovery;

@end

NS_ASSUME_NONNULL_END
