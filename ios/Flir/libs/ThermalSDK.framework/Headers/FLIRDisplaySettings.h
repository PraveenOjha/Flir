//
//  FLIRDisplaySettings.h
//
//  Copyright © 2020 Teledyne FLIR. All rights reserved.
//

#import <Foundation/Foundation.h>

/**
 *  The FLIRDisplaySettings exposes a range of visual attributes which specify how the image is
 *  displayed. They also specify how image was displayed when it was initially taken by a camera.
 *  FLIRDisplaySettings allows user to specify a "subsection" of interest, while still keeping the
 *  entire image available for context.
 *  For example: taking an image of a car engine, but "zoom-in" to a faulty spark-plug, while keeping
 *  the entire image of the photographed engine for a context.
 *  The FLIRDisplaySettings effects both the IR and visual image.
 */
@interface FLIRDisplaySettings : NSObject

- (instancetype)initWithZoomFactor:(float)zoomFactor zoomPanX:(int)zoomPanX zoomPanY:(int)zoomPanY;

/** Defines the image zoom factor, where value 1.0 means no zoom. */
@property (nonatomic, readonly) float zoomFactor;
/** Defines the image zoom pan/offset in X axis, where value 0 means centered. */
@property (nonatomic, readonly) int zoomPanX;
/** Defines the image zoom pan/offset in Y axis, where value 0 means centered. */
@property (nonatomic, readonly) int zoomPanY;

@end
