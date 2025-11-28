//
//  FLIRFusionTransformation.h
//  ThermalSDK
//
//  Created by FLIR on 2021-03-16.
//  Copyright © 2021 Teledyne FLIR. All rights reserved.
//

#pragma once

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/**
 *  Fusion transformation settings.
 *
 *  @note setTransformation should be called after changing the fields below
 */
@interface FLIRFusionTransformation : NSObject

- (instancetype)initWithPanX:(int)panX panY:(int)panY scale:(float)scale rotation:(float)rotation;

/**  Fusion horizontal panning. */
@property (nonatomic, assign) int panX;
/**  Fusion vertical panning. */
@property (nonatomic, assign) int panY;
/**  Fusion zoom factor. */
@property (nonatomic, assign) float scale;
/**  Fusion rotation factor clockwise in degrees. */
@property (nonatomic, assign) float rotation;

@end

NS_ASSUME_NONNULL_END
