//
//  FLIRPaletteManager.h
//  FLIR Thermal SDK
//
//  Copyright © 2019 Teledyne FLIR. All rights reserved.
//

#import <Foundation/Foundation.h>
#import "FLIRPalette.h"

/**
 *  The palette manager is a way to get the default palettes. These palettes can be applied to a thermal image, to change its look.
 *  Every palette has a different color for a different temperature.
 *  Some Palettes provide better contrast, while other expose the important details.
 */
@interface FLIRPaletteManager : NSObject

/**
 *  Gets the Arctic Palette.
 */
@property (readonly) FLIRPalette *arctic;

/**
 *  Gets the blackhot Palette.
 */
@property (readonly) FLIRPalette *blackhot;

/**
 *  Gets the gray / "black and white" Palette
 */
@property (readonly) FLIRPalette *gray;

/**
 *  Gets the iron Palette.
 */
@property (readonly) FLIRPalette *iron;

/**
 *  Gets the lava Palette.
 */
@property (readonly) FLIRPalette *lava;

/**
 *  Gets the rainbow Palette.
 */
@property (readonly) FLIRPalette *rainbow;

/**
 *  Gets the rainbow HighContrast Palette.
 */
@property (readonly) FLIRPalette *rainHC;

/**
 *  Gets the coldest Palette.
 */
@property (readonly) FLIRPalette *coldest;

/**
 *  Gets the hottest Palette.
 */
@property (readonly) FLIRPalette *hottest;

/**
 *  Gets the colorWheel6 Palette.
 */
@property (readonly) FLIRPalette *colorWheel6;

/**
 *  Gets the doubleRainbow Palette.
 */
@property (readonly) FLIRPalette *doubleRainbow;

/**
 *  Gets the whitehot Palette.
 */
@property (readonly) FLIRPalette *whitehot;

/**
 *  Gets a list of default Palettes.
 *
 *  @return an array with the default palettes
 */
- (NSArray<FLIRPalette*>*)getDefaultPalettes;

/**
 *  Open a palette from file.
 *
 *  @return a palette object based on palette file
 */
- (FLIRPalette *) openPalette:(NSURL *) paletteFile;

/**
 * Get the default manager
 */
+ (instancetype)default;

@end
