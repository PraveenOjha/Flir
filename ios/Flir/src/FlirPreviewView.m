//
//  FlirPreviewView.m
//  Flir
//
//  Native preview view for displaying FLIR thermal camera frames
//

#import "FlirPreviewView.h"
#import "FlirState.h"

@interface FlirPreviewView ()
@property(nonatomic, strong) UIImageView *imageView;
@property(nonatomic, strong) UILabel *temperatureLabel;
@property(nonatomic, strong) CADisplayLink *displayLink;
@property(nonatomic, assign) BOOL isActive;
@end

@implementation FlirPreviewView

- (instancetype)initWithFrame:(CGRect)frame {
  if (self = [super initWithFrame:frame]) {
    [self setupView];
  }
  return self;
}

- (void)setupView {
  self.backgroundColor = [UIColor blackColor];
  self.clipsToBounds = YES;
  _lastTemperature = NAN;
  _isActive = NO;

  // Image view for thermal frames
  _imageView = [[UIImageView alloc] initWithFrame:self.bounds];
  _imageView.contentMode = UIViewContentModeScaleAspectFit;
  _imageView.autoresizingMask =
      UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
  _imageView.backgroundColor = [UIColor blackColor];
  [self addSubview:_imageView];

  // Temperature label overlay (optional)
  _temperatureLabel = [[UILabel alloc] init];
  _temperatureLabel.textColor = [UIColor whiteColor];
  _temperatureLabel.font = [UIFont boldSystemFontOfSize:14];
  _temperatureLabel.backgroundColor =
      [[UIColor blackColor] colorWithAlphaComponent:0.5];
  _temperatureLabel.textAlignment = NSTextAlignmentCenter;
  _temperatureLabel.layer.cornerRadius = 4;
  _temperatureLabel.clipsToBounds = YES;
  _temperatureLabel.hidden = YES; // Hidden by default
  [self addSubview:_temperatureLabel];

  // Register for frame updates from FlirState
  __weak FlirPreviewView *weakSelf = self;
  [FlirState shared].onTextureUpdate = ^(UIImage *image, int textureUnit) {
    [weakSelf updateWithImage:image];
  };
}

- (void)layoutSubviews {
  [super layoutSubviews];

  _imageView.frame = self.bounds;

  // Position temperature label in top-right corner
  CGFloat labelWidth = 100;
  CGFloat labelHeight = 30;
  CGFloat padding = 10;
  _temperatureLabel.frame =
      CGRectMake(self.bounds.size.width - labelWidth - padding, padding,
                 labelWidth, labelHeight);
}

- (void)updateWithImage:(UIImage *)image {
  if (!image)
    return;

  dispatch_async(dispatch_get_main_queue(), ^{
    self.imageView.image = image;
  });
}

- (void)updateWithFrameImage:(UIImage *)image temperature:(double)temperature {
  dispatch_async(dispatch_get_main_queue(), ^{
    self.imageView.image = image;
    self.lastTemperature = temperature;
    [FlirState shared].lastTemperature = temperature;

    if (!isnan(temperature)) {
      self.temperatureLabel.text =
          [NSString stringWithFormat:@"%.1f°C", temperature];
      self.temperatureLabel.hidden = NO;
    }
  });
}

- (void)showFallbackFrame {
  // Generate a fallback gradient when no FLIR data is available
  UIGraphicsBeginImageContextWithOptions(CGSizeMake(320, 240), YES, 1.0);
  CGContextRef context = UIGraphicsGetCurrentContext();

  if (context) {
    // Create thermal-looking gradient
    CGColorSpaceRef colorSpace = CGColorSpaceCreateDeviceRGB();
    NSArray *colors = @[
      (id)[UIColor colorWithRed:0 green:0 blue:0.5 alpha:1].CGColor,
      (id)[UIColor colorWithRed:0 green:0.5 blue:0.5 alpha:1].CGColor,
      (id)[UIColor colorWithRed:0 green:0.8 blue:0 alpha:1].CGColor,
      (id)[UIColor colorWithRed:1 green:1 blue:0 alpha:1].CGColor,
      (id)[UIColor colorWithRed:1 green:0.5 blue:0 alpha:1].CGColor,
      (id)[UIColor colorWithRed:1 green:0 blue:0 alpha:1].CGColor,
    ];
    CGFloat locations[] = {0.0, 0.2, 0.4, 0.6, 0.8, 1.0};
    CGGradientRef gradient = CGGradientCreateWithColors(
        colorSpace, (__bridge CFArrayRef)colors, locations);

    CGContextDrawLinearGradient(context, gradient, CGPointMake(0, 240),
                                CGPointMake(320, 0), 0);

    CGGradientRelease(gradient);
    CGColorSpaceRelease(colorSpace);

    // Add "FALLBACK" text
    NSDictionary *attrs = @{
      NSFontAttributeName : [UIFont boldSystemFontOfSize:20],
      NSForegroundColorAttributeName : [UIColor whiteColor]
    };
    NSString *text = @"FLIR FALLBACK";
    CGSize textSize = [text sizeWithAttributes:attrs];
    CGRect textRect =
        CGRectMake((320 - textSize.width) / 2, (240 - textSize.height) / 2,
                   textSize.width, textSize.height);
    [text drawInRect:textRect withAttributes:attrs];
  }

  UIImage *fallbackImage = UIGraphicsGetImageFromCurrentImageContext();
  UIGraphicsEndImageContext();

  dispatch_async(dispatch_get_main_queue(), ^{
    self.imageView.image = fallbackImage;
  });
}

#pragma mark - Lifecycle

- (void)willMoveToWindow:(UIWindow *)newWindow {
  [super willMoveToWindow:newWindow];

  if (newWindow) {
    // View is being added to window - start updates
    _isActive = YES;

    // Check if we have a frame, otherwise show fallback
    UIImage *currentFrame = [FlirState shared].latestImage;
    if (currentFrame) {
      [self updateWithImage:currentFrame];
    } else {
      [self showFallbackFrame];
    }
  } else {
    // View is being removed from window - stop updates
    _isActive = NO;
  }
}

- (void)dealloc {
  // Clean up
  [FlirState shared].onTextureUpdate = nil;
}

@end
