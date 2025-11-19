//
//  FLIRRenderer.h
//  ThermalSDK
//
//  Created by FLIR on 2021-10-12.
//  Copyright © 2021 Teledyne FLIR. All rights reserved.
//

#pragma once

#import <UIKit/UIKit.h>

/**
 *  Class for rendering images.
 */
@protocol FLIRRenderer

/**
 *  Get "rendered result"
 *
 *  Fetch the rendered image from the renderer. This returns a deep copy of the image.
 *  @return Returns an UIImage, or nil if update has not been called previously or no source image was available yet.
 */
- (UIImage * _Nullable)getImage;

/**
 *  Execute renderer
 *
 *  Polls image data from the source (e.g. a @ref FLIRStream or a @ref FLIRThermalImage), then performs image processing
 *  Result could be stored in a UIImage or rendered directly to screen, depending on configuration.
 *  @return true if update is succesfull, otherwise the error is set
 */
- (BOOL)update:(out NSError * _Nullable __autoreleasing * _Nullable)error;

@end
