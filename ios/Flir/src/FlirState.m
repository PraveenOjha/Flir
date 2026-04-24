//
//  FlirState.m
//  Flir
//
//  Shared state singleton for FLIR frame and temperature data
//

#import "FlirState.h"

#import <stdatomic.h>

static FlirState *_sharedState = nil;

@implementation FlirState {
  NSArray<NSNumber *> *_temperatureData;
  int _imageWidth;
  int _imageHeight;
  dispatch_queue_t _accessQueue;
  atomic_bool _isTextureBusy;
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
    atomic_store(&_isTextureBusy, false);
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
  // Non-blocking read: use dispatch_async with cached value for responsiveness
  // If data is being updated, return NAN rather than blocking main thread

  // Quick nil check without locking
  if (_temperatureData == nil || _imageWidth == 0 || _imageHeight == 0) {
    return NAN;
  }

  // Bounds check
  if (x < 0 || x >= _imageWidth || y < 0 || y >= _imageHeight) {
    return NAN;
  }

  // Access flattened array: index = y * width + x
  NSInteger index = y * _imageWidth + x;
  NSArray<NSNumber *> *tempData = _temperatureData; // Capture pointer
  if (tempData == nil || index < 0 || index >= (NSInteger)[tempData count]) {
    return NAN;
  }

  return [tempData[index] doubleValue];
}

- (void)updateFrame:(UIImage *)image {
  [self updateFrame:image withTemperatureData:nil];
}

- (void)updateFrame:(UIImage *)image
    withTemperatureData:(NSArray<NSNumber *> *)tempData {
  dispatch_async(_accessQueue, ^{
    self.latestImage = image;

    if (tempData != nil) {
      self->_temperatureData = [tempData copy];
      self->_imageWidth = (int)image.size.width;
      self->_imageHeight = (int)image.size.height;
    }
  });

  // Invoke texture callback on main thread (for Metal filters, texture unit 7)
  NSLog(@"[FLIR-TRACE 9️⃣] FlirState checking onTextureUpdate callback - "
        @"hasCallback=%d",
        self.onTextureUpdate != nil);

  if (self.onTextureUpdate) {
    bool expected = false;
    if (atomic_compare_exchange_strong(&_isTextureBusy, &expected, true)) {
      NSLog(@"[FLIR-TRACE 🔟] Dispatching onTextureUpdate callback to main "
            @"queue");
      dispatch_async(dispatch_get_main_queue(), ^{
        @try {
          if (self.onTextureUpdate) {
            NSLog(@"[FLIR-TRACE 1️⃣1️⃣] Invoking onTextureUpdate callback with "
                  @"image %@",
                  image);
            self.onTextureUpdate(image, 7);
            NSLog(@"[FLIR-TRACE 1️⃣2️⃣] onTextureUpdate callback completed");
          } else {
            NSLog(@"[FLIR-TRACE ❌] onTextureUpdate became nil before invoke");
          }
        } @finally {
          atomic_store(&_isTextureBusy, false);
        }
      });
    } else {
      NSLog(@"[FLIR-TRACE ⚠️] isTextureBusy was true - skipping callback (frame "
            @"drop)");
    }
  } else {
    NSLog(@"[FLIR-TRACE ❌] onTextureUpdate callback is nil - NO CALLBACK "
          @"REGISTERED!");
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
    
    // Notify view to clear instantly
    if (self.onTextureUpdate) {
      dispatch_async(dispatch_get_main_queue(), ^{
        if (self.onTextureUpdate) {
          self.onTextureUpdate(nil, 7);
        }
      });
    }
  });
}

@end
