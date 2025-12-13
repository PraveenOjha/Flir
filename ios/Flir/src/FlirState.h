//
//  FlirState.h
//  Flir
//
//  Shared state singleton for FLIR frame and temperature data
//

#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// Callback for temperature updates
typedef void (^FlirTemperatureCallback)(double temperature, int x, int y);

/// Callback for texture/image updates
typedef void (^FlirTextureCallback)(UIImage *_Nonnull image, int textureUnit);

@interface FlirState : NSObject

/// Singleton instance
+ (instancetype)shared;

/// Last sampled temperature value
@property(nonatomic, assign) double lastTemperature;

/// Latest thermal image
@property(nonatomic, strong, nullable) UIImage *latestImage;

/// Frame dimensions
@property(nonatomic, assign, readonly) int imageWidth;
@property(nonatomic, assign, readonly) int imageHeight;

/// Callback invoked when temperature is sampled
@property(nonatomic, copy, nullable)
    FlirTemperatureCallback onTemperatureUpdate;

/// Callback invoked when a new frame is available for Metal texture
@property(nonatomic, copy, nullable) FlirTextureCallback onTextureUpdate;

/// Get temperature at pixel coordinates
/// @param x X coordinate
/// @param y Y coordinate
/// @return Temperature in Celsius, or NAN if not available
- (double)getTemperatureAt:(int)x y:(int)y;

/// Query temperature at point using stored temperature data array
/// @param x X coordinate
/// @param y Y coordinate
/// @return Temperature in Celsius, or NAN if not available
- (double)queryTemperatureAtPoint:(int)x y:(int)y;

/// Update frame with new image
/// @param image The thermal image
- (void)updateFrame:(UIImage *_Nonnull)image;

/// Update frame with new image and temperature data array
/// @param image The thermal image
/// @param tempData Flattened array of temperature values (row-major order)
- (void)updateFrame:(UIImage *_Nonnull)image
    withTemperatureData:(NSArray<NSNumber *> *_Nullable)tempData;

/// Clear all stored data
- (void)reset;

@end

NS_ASSUME_NONNULL_END
