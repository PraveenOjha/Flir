//
//  FLIRScale.h
//
//  Copyright © 2019 Teledyne FLIR. All rights reserved.
//

#pragma once

#import "FLIRThermalValue.h"
#import "FLIRPalette.h"

/**
 *  Defines an image's linear scale object and basic scale's parameters.
 *  The scale defines the way the Palette is applied to the ThermalImage.
 *  Scale is an interval of the temperature, where colors from a selected Palette is applied.
 *  The available temperature's range is defined by the ThermalImage.
 *  Scale cannot be created directly, it should always be obtained from ThermalImage.
 *  To do so, the ThermalImage.getScale() method should be called.
 */
@interface FLIRScale : NSObject
/**  
 *   Sets the minimum and maximum scale values.
 *
 *   @param min  The minimum scale value.
 *   @param max  The maximum scale value.
 */
- (void)setRangeWithMin:(FLIRThermalValue * _Nonnull)min andMax:(FLIRThermalValue * _Nonnull) max;

/** 
 *   Sets the scale automatically based on the min and max scale values.
 *
 *   @note This call will be removed, use FLIRColorizer.autoScale from FLIRImageColorizer (still images) or FLIRThermalStreamer (live streams) instead.
 */
- (void)setAutoAdjustEnabled:(BOOL)enabled DEPRECATED_MSG_ATTRIBUTE("This call will be removed, use FLIRColorizer.autoScale from FLIRImageColorizer (still images) or FLIRThermalStreamer (live streams) instead");;

/** 
 *  Gets the current minimum scale value.
 *  @return The min scale value.
 */
- (FLIRThermalValue * _Nonnull)getRangeMin;

/** 
 *  Gets the current maximum scale value.
 *  @return The max scale value
 */
- (FLIRThermalValue *  _Nonnull)getRangeMax;

/** 
 *  Gets the thermal scale image.
 *
 *  @return An UIImage representing the thermal scale.
 *
 *  @note This call will be removed, use FLIRColorizer.getScaleImage from FLIRImageColorizer (still images) or FLIRThermalStreamer (live streams) instead.
 */
- (UIImage * _Nullable)getImage DEPRECATED_MSG_ATTRIBUTE("This call will be removed, use FLIRColorizer.getScaleImage from FLIRImageColorizer (still images) or FLIRThermalStreamer (live streams) instead");

/**
 *  Gets the thermal scale image.
 *
 *  @param palette the pallete
 *  
 *  @return An UIImage representing the full thermal range scale.
 *
 *  @note This feature will likely be removed in upcoming release.
 */

+ (UIImage * _Nullable)getFixedFullRangeScaleImage:(FLIRPalette * _Nonnull) palette DEPRECATED_MSG_ATTRIBUTE("This feature will likely be removed in upcoming release.");;

/**
 *  Gets the thermal scale image from current palette.
 *
 *  @return An UIImage representing the full thermal range scale.
 *
 *  @note This feature will likely be removed in upcoming release.
 */

- (UIImage * _Nullable)getFixedFullRangeScaleImage DEPRECATED_MSG_ATTRIBUTE("This feature will likely be removed in upcoming release.");


/**
 *  Gets auto adjust setting.
 *
 *  @note This call will be removed, use FLIRColorizer.autoScale from FLIRImageColorizer (still images) or FLIRThermalStreamer (live streams) instead.
 */
- (BOOL)isAutoAdjustEnabled DEPRECATED_MSG_ATTRIBUTE("This call will be removed, use FLIRColorizer.autoScale from FLIRImageColorizer (still images) or FLIRThermalStreamer (live streams) instead");

/**
 *  Sets the maximum range, where colors defined by the Palette are applied to the ThermalImage.
 *  @Deprecated Setting max value may affect min value, use setRangeWithMin:andMax:
 */
- (void)setRangeMax:(FLIRThermalValue * _Nonnull)max;

/**
 *  Sets the minimum range, where colors defined by the Palette are applied to the ThermalImage.
 *  @Deprecated Setting min value may affect max value, use setRangeWithMin:andMax:
 */
- (void)setRangeMin:(FLIRThermalValue * _Nonnull)min;

@end
