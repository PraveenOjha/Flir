#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

@interface FlirPreviewView : UIView

@property (nonatomic, assign) double lastTemperature;

- (void)updateWithFrameImage:(UIImage *)image temperature:(double)temperature;

@end

NS_ASSUME_NONNULL_END
