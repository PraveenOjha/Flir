#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@interface FlirState : NSObject

@property (nonatomic, assign) double lastTemperature;
@property (nonatomic, copy, nullable) void (^onTemperatureUpdate)(double temperature, int x, int y);
@property (nonatomic, copy, nullable) void (^onTextureUpdate)(UIImage *_Nonnull image, int textureUnit);
@property (nonatomic, strong, nullable) UIImage *latestImage;

+ (instancetype)shared;
- (double)getTemperatureAt:(int)x y:(int)y;
- (void)updateFrame:(UIImage *_Nonnull)image;
- (void)updateFrame:(UIImage *_Nonnull)image withTemperatureData:(NSArray<NSNumber *> *_Nullable)tempData;
- (double)queryTemperatureAtPoint:(int)x y:(int)y;

@end

NS_ASSUME_NONNULL_END
