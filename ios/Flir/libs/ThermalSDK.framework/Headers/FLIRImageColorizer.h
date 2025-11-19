//
//  FLIRImageColorizer.h
//  ThermalSDK
//
//  Created by FLIR on 2021-10-27.
//  Copyright © 2021 Teledyne FLIR. All rights reserved.
//

#import "FLIRThermalImage.h"
#import "FLIRColorizer.h"
#import "FLIRRendererImpl.h"

/**
 *  Renders still thermal images.
 *
 *  Implements the protocols FLIRColorizer and FLIRRenderer
 */
@interface FLIRImageColorizer : FLIRRendererImpl<FLIRColorizer>

/**
 *  Create a colorizer for the given thermal image
 */
- (instancetype _Nullable)initWithImage:(FLIRThermalImage * _Nonnull)image;
- (BOOL)update:(out NSError * _Nullable * _Nullable)error;

@end
