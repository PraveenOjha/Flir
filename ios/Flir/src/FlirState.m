#import "FlirState.h"

static FlirState *_sharedState = nil;

@implementation FlirState {
    NSArray<NSNumber *> *_temperatureData; // Flattened array of temperature values
    int _imageWidth;
    int _imageHeight;
}

+ (instancetype)shared
{
  static dispatch_once_t onceToken;
  dispatch_once(&onceToken, ^{
    _sharedState = [FlirState new];
    _sharedState.lastTemperature = NAN;
    _sharedState.latestImage = nil;
    _sharedState->_temperatureData = nil;
    _sharedState->_imageWidth = 0;
    _sharedState->_imageHeight = 0;
  });
  return _sharedState;
}

- (double)getTemperatureAt:(int)x y:(int)y
{
  // Return cached temperature (will be updated by thermal SDK callbacks)
  // In production, this would query the actual thermal image data at (x,y)
  return self.lastTemperature;
}

- (void)updateFrame:(UIImage *)image
{
  [self updateFrame:image withTemperatureData:nil];
}

- (void)updateFrame:(UIImage *)image withTemperatureData:(NSArray<NSNumber *> *)tempData
{
  self.latestImage = image;
  
  if (tempData != nil) {
    _temperatureData = tempData;
    _imageWidth = (int)image.size.width;
    _imageHeight = (int)image.size.height;
  }
  
  // Invoke texture callback for native Metal filters (texture unit 7)
  if (self.onTextureUpdate) {
    self.onTextureUpdate(image, 7);
  }
  
  // Sample temperature at center point and invoke callback
  if (self.onTemperatureUpdate) {
    double temp = [self getTemperatureAt:80 y:60];
    self.onTemperatureUpdate(temp, 80, 60);
  }
}

- (double)queryTemperatureAtPoint:(int)x y:(int)y
{
  if (_temperatureData == nil || _imageWidth == 0 || _imageHeight == 0) {
    return NAN;
  }
  
  // Bounds check
  if (x < 0 || x >= _imageWidth || y < 0 || y >= _imageHeight) {
    return NAN;
  }
  
  // Access flattened array: index = y * width + x
  NSInteger index = y * _imageWidth + x;
  if (index < 0 || index >= (NSInteger)[_temperatureData count]) {
    return NAN;
  }
  
  return [_temperatureData[index] doubleValue];
}

@end
