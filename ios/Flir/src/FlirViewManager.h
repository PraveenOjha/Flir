//
//  FlirViewManager.h
//  Flir
//
//  React Native view manager for FLIR preview
//

// Prefer React/ headers, but support multiple RN header layouts
#if __has_include(<React/RCTViewManager.h>)
#import <React/RCTViewManager.h>
#elif __has_include(<ReactCore/RCTViewManager.h>)
#import <ReactCore/RCTViewManager.h>
#elif __has_include("RCTViewManager.h")
#import "RCTViewManager.h"
#elif __has_include(<React/RCTUIManager.h>)
#import <React/RCTUIManager.h>
#else
#import <Foundation/Foundation.h>
@interface RCTViewManager : NSObject
@end
#endif

NS_ASSUME_NONNULL_BEGIN

@interface FlirViewManager : RCTViewManager

@end

NS_ASSUME_NONNULL_END
