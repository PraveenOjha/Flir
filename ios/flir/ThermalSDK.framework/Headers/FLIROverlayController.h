//
//  FLIROverlayController.h
//  ThermalSDK
//
//  Created by FLIR on 2020-09-15.
//  Copyright © 2020 Teledyne FLIR. All rights reserved.
//

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/**
 *  Controls the visual overlay with settings and scale on a camera
 */
@interface FLIROverlayController : NSObject

/** Check if camera has overlay */
- (BOOL)getIsAvailable;
/** Check if overlay is hidden */
- (BOOL)getIsHidden;
/** Set overlay hidden */
- (BOOL)setIsHidden: (BOOL)hidden error:(out NSError * _Nullable *_Nullable)error;

@end

NS_ASSUME_NONNULL_END
