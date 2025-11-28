//
//  FLIRPalette.h
//  FLIR Thermal SDK
//
//  Copyright © 2019 Teledyne FLIR. All rights reserved.
//

#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>

/**
 *  Defines a palette representation, which can be applied to the ThermalImage in order to display it in specified colors.
 *  The palette is a set of colors applied to the temperature or signal data stored in the ThermalImage.
 *  Thanks to that each temperature / signal value can be displayed in specific color.
 *  This creates a visual representation of the ThermalImage.
 *  A Palette's object cannot be created directly.
 *  It can be obtained from ThermalImage by calling ThermalImage.getPalette() method.
 *  This method will always return an Palette instance.
 *  A ThermalImage has to have palette set in order to be presented to the User.
 *
 *  The Palette for ThermalImage can be changed. In order to get the other palettes a PaletteManager class should be used.
 *  It can provide the list of predefined palettes and allows to load more palettes as well from a user-defined location.
 */
@interface FLIRPalette : NSObject

/**
 *  Gets and sets the color of the above span.
 */
@property (readwrite) UIColor * _Nonnull aboveSpanColor;

/**
 *  Gets and sets the color of the below span.
 */
@property (readwrite) UIColor * _Nonnull belowSpanColor;

/**
 *  Gets and sets the color of the overflow.
 */
@property (readwrite) UIColor * _Nonnull overflowColor;

/**
 *  Gets and sets the color of the underflow.
 */
@property (readwrite) UIColor * _Nonnull underflowColor;

/**
 *  Gets or sets list of all the colors defining the palette.
 */
@property (readwrite) NSArray<UIColor *> * _Nonnull paletteColors;

/**
 *  Inverts the colors i.e. warm colors are shown as cold.
 */
@property (readwrite) BOOL isInverted;

/**
 *  Name of the Palette
 */
@property (readwrite) NSString * _Nonnull name;
@end
