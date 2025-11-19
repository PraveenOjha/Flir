//
//  FLIRColorizer.h
//  ThermalSDK
//
//  Created by FLIR on 2021-10-27.
//  Copyright © 2021 Teledyne FLIR. All rights reserved.
//


#pragma once

#import <UIKit/UIKit.h>

#import "FLIRRenderer.h"

@class FLIRRange;

/**
 *  Protocol for classes colorizing thermal images or streams
 */
@protocol FLIRColorizer<FLIRRenderer>

/**
 *  Automatic scale
 *
 *  If true rendered images will have scale automatically set based on the min and max values in the image.
 *  @note auto-adjusted scale is disabled by default
 *
 *  @note Calling this function does not trigger any render. Use `getImage()` if necessary to "re-render".
 *  @note it is recommended to keep the rendering setting consistent with the FLIRThermalImage information within FLIRScale.
 *  For this purpose code similar to the following could be used:
 *  // auto scale enabled
 *  let colorizer = ... // FLIRColorizer
 *  colorizer.autoScale = true
 *  // calling getImage(), among other internal work, will re-calculate scale range that we can then apply to Scale object
 *  colorizer.getImage()
 *  let scale = ... // obtain FLIRScale instance from FLIRThermalImage or in live stream from FLIRThermalStreamer.withThermalImage()
 *  // set the range calculated by the colorizer
 *  auto tempRange = colorizer.getScaleRange();
 *  scale.setRangeMin(tempRange.min);
 *  scale.setRangeMax(tempRange.max);
 *  //
 *  // auto scale disabled
 *  let colorizer = ... // FLIRColorizer
 *  colorizer.autoScale = false
 *  // first get scale
 *  let scale = ... // obtain FLIRScale instance from FLIRThermalImage or in live stream from FLIRThermalStreamer.withThermalImage()
 *  // then set manual range provided from the user
 *  scale.setRangeMin(userMinScaleValue);
 *  scale.setRangeMax(userMaxScaleValue);
 *  // finally update the colorizer so the rendered image is colorized using user-defined scale range
 *  colorizer.getImage()
 */
@property (nonatomic, assign) BOOL autoScale;

/**
 *  Render scale
 *
 *  If true the scale will be rendered when the image is rendered
 *  @note scale rendering is disabled by default
 */
@property (nonatomic, assign) BOOL renderScale;

/**
 *  Get an image with the scale
 *
 *  @note if scale rendering was disabled during last call to @ref update, the result from this function may be out of sync .
 *  @note For getting rendered image without scale, see @ref getImage
 *  @return Returns an UIImage, or nil if update hasn't been called with scale rendering enabled
 */
- (UIImage * _Nullable)getScaleImage;

/**
 *  Get the range of the scale
 *
 *  If auto scale is on, get the min and max temperature
 *  @return FLIRRange with min and max
 */

- (FLIRRange * _Nullable)getScaleRange;

@end
