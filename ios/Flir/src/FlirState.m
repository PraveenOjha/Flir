//
//  FlirState.m
//  Flir
//
//  Shared state singleton for FLIR frame and temperature data
//

#import "FlirState.h"

static FlirState *_sharedState = nil;

@implementation FlirState {
  NSArray<NSNumber *> *_temperatureData;
  int _imageWidth;
  int _imageHeight;
  dispatch_queue_t _accessQueue;
}

+ (instancetype)shared {
  static dispatch_once_t onceToken;
  dispatch_once(&onceToken, ^{
    _sharedState = [[FlirState alloc] init];
  });
  return _sharedState;
}

- (instancetype)init {
  if (self = [super init]) {
    _lastTemperature = NAN;
    _latestImage = nil;
    _temperatureData = nil;
    _imageWidth = 0;
    _imageHeight = 0;
    _accessQueue =
        dispatch_queue_create("com.flir.state.access", DISPATCH_QUEUE_SERIAL);
  }
  return self;
}

- (int)imageWidth {
  return _imageWidth;
}

- (int)imageHeight {
  return _imageHeight;
}

- (double)getTemperatureAt:(int)x y:(int)y {
  // First try the temperature data array if available
  double t = [self queryTemperatureAtPoint:x y:y];
  if (!isnan(t)) {
    return t;
  }
  // Fall back to last sampled temperature
  return self.lastTemperature;
}

- (double)queryTemperatureAtPoint:(int)x y:(int)y {
  __block double result = NAN;

  dispatch_sync(_accessQueue, ^{
    if (_temperatureData == nil || _imageWidth == 0 || _imageHeight == 0) {
      return;
    }

    // Bounds check
    if (x < 0 || x >= _imageWidth || y < 0 || y >= _imageHeight) {
      return;
    }

    // Access flattened array: index = y * width + x
    NSInteger index = y * _imageWidth + x;
    if (index < 0 || index >= (NSInteger)[_temperatureData count]) {
      return;
    }

    result = [_temperatureData[index] doubleValue];
  });

  return result;
}

- (void)updateFrame:(UIImage *)image {
  [self updateFrame:image withTemperatureData:nil];
}

- (void)updateFrame:(UIImage *)image
    withTemperatureData:(NSArray<NSNumber *> *)tempData {
  if (!image)
    return;

  dispatch_async(_accessQueue, ^{
    self.latestImage = image;

    if (tempData != nil) {
      self->_temperatureData = [tempData copy];
      self->_imageWidth = (int)image.size.width;
      self->_imageHeight = (int)image.size.height;
    }
  });

  // Invoke texture callback on main thread (for Metal filters, texture unit 7)
  if (self.onTextureUpdate) {
    dispatch_async(dispatch_get_main_queue(), ^{
      if (self.onTextureUpdate) {
        self.onTextureUpdate(image, 7);
      }
    });
  }

  // Sample temperature at center point and invoke callback
  if (self.onTemperatureUpdate) {
    int centerX = (int)(image.size.width / 2);
    int centerY = (int)(image.size.height / 2);
    double temp = [self getTemperatureAt:centerX y:centerY];

    dispatch_async(dispatch_get_main_queue(), ^{
      if (self.onTemperatureUpdate) {
        self.onTemperatureUpdate(temp, centerX, centerY);
      }
    });
  }
}

- (void)reset {
  dispatch_async(_accessQueue, ^{
    self.latestImage = nil;
    self.lastTemperature = NAN;
    self->_temperatureData = nil;
    self->_imageWidth = 0;
    self->_imageHeight = 0;
  });
}

@end
