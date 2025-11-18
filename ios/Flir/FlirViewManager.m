#import "FlirViewManager.h"
#import "FlirPreviewView.h"
#import <React/RCTBridge.h>
#import <React/RCTUIManager.h>

@implementation FlirViewManager

RCT_EXPORT_MODULE(FlirView)

- (UIView *)view
{
  FlirPreviewView *v = [[FlirPreviewView alloc] initWithFrame:CGRectZero];
  return v;
}

@end
