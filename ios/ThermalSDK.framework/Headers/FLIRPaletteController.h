//
//  FLIRPaletteController.h
//  ThermalSDK
//
//  Created by FLIR on 2020-07-02.
//  Copyright © 2020 Teledyne FLIR. All rights reserved.
//

#import <Foundation/Foundation.h>
#import "FLIRCameraImport.h"

/** camera palette */
@interface FLIRRemotePalette : NSObject

/** name of palette */
@property (nonatomic, retain, nonnull) NSString* name;
/** inverted state of palette */
@property (nonatomic, assign) BOOL isInverted;
/** Holds remote absolute path where palette file is stored on a particular camera, i.e.: "/FLIR/usr/etc/iron.pal". */
@property (nonatomic, retain, nonnull) FLIRFileReference *fileReference;

@end

/**
 *  Controller for the camera palettes
 */
@interface FLIRPaletteController : NSObject

/** get current palette on camera */
- (FLIRRemotePalette* _Nullable)getCurrentPalette;
/** set current palette on camera */
- (BOOL)setCurrentPalette: (FLIRRemotePalette* _Nonnull)currentPalette error: (out NSError * _Nullable *_Nullable)error;
/** list available palettes on camera */
- (NSArray<FLIRRemotePalette*>* _Nullable) getAvailablePalettes;

@end
