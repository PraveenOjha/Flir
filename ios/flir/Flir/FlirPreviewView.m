#import "FlirPreviewView.h"
#import "FlirState.h"

@implementation FlirPreviewView

- (instancetype)initWithFrame:(CGRect)frame
{
  if (self = [super initWithFrame:frame]) {
    self.backgroundColor = [UIColor blackColor];
    _lastTemperature = NAN;
  }
  return self;
}

- (void)updateWithFrameImage:(UIImage *)image temperature:(double)temperature
{
  dispatch_async(dispatch_get_main_queue(), ^{
    self.layer.contents = (id)image.CGImage;
    self.lastTemperature = temperature;
    [FlirState shared].lastTemperature = temperature;
  });
}

@end
