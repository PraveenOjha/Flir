//
//  FlirViewManager.m
//  Flir
//
//  React Native view manager for FLIR preview
//

#import "FlirViewManager.h"
#import "FlirPreviewView.h"
#import <React/RCTUIManager.h>

@implementation FlirViewManager

RCT_EXPORT_MODULE(FlirPreviewView)

+ (BOOL)requiresMainQueueSetup {
  return YES;
}

- (UIView *)view {
  return [[FlirPreviewView alloc] init];
}

RCT_EXPORT_VIEW_PROPERTY(showTemperature, BOOL)
RCT_EXPORT_VIEW_PROPERTY(showFallback, BOOL)

@end
