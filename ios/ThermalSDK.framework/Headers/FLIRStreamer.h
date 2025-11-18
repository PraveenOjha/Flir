//
//  FLIRStreamer.h
//  ThermalSDK
//
//  Created by FLIR on 2021-10-12.
//  Copyright © 2021 Teledyne FLIR. All rights reserved.
//

#import <Foundation/Foundation.h>

#import "FLIRRendererImpl.h"
#import "FLIRColorizer.h"
#import "FLIRCamera.h"

@class FLIRStream;
@class FLIRThermalImage;

NS_ASSUME_NONNULL_BEGIN

@class FLIRVividIRParameters;

/**
 * Vivid IR upscale methods
 * chaning this will give faster response, but lower quality
 */
typedef NS_ENUM(NSInteger, FLIRVividIRUpscale) {
    /// Use superResolution for scaling (only available on FLIR One Pro models)
    /// this is the default setting for FLIR One Pro
    FLIRSuperResolution,
    /// use trilateral scaling
    /// this is default setting for FLIR One
    FLIRTrilateral
};

/**
 * Vivid IR latency mode
 * chaning this will give faster response, but lower quality
 */
typedef NS_ENUM(NSInteger, FLIRVividIRLatency) {
    /// Use a latency of 2 frames for denoise
    /// this is the default setting for FLIR One
    FLIRLatencyHigh,
    /// Use a latency of 0 frames for denoise (fastest)
    FLIRLatencyLow
};

/**
 *  Streamer class
 */
@interface FLIRStreamer : FLIRRendererImpl

/**
 *  Get parameters for the VividIR filter
 *  Returns nil if this streamer doesn't use VividIR
 */
@property (nonatomic, nullable, readonly) FLIRVividIRParameters* vividIRParameters;

@end

/**
 *  Streamer class for thermal data
 */
@interface FLIRThermalStreamer : FLIRStreamer<FLIRColorizer>

/**
 *  Create a thermal streamer on a stream
 *
 *  @param stream the stream to be streamed
 *
 *  The stream should contain thermal data. This will also contain a colorizer to provide a visual image
 *  from the thermal stream.
 */
- (instancetype)initWithStream:(FLIRStream *)stream;

/**
 *  Create a thermal streamer on a stream
 *
 *  @param stream the stream to be streamed
 *  @param streamingOptions options for streaming, currently the only option is STREAMING_NO_OPENGL
 *     which creates a pipeline using only CPU filters, avoiding openGL rendering
 *
 *  The stream should contain thermal data. This will also contain a colorizer to provide a visual image
 *  from the thermal stream.
 */
- (instancetype)initWithStream:(FLIRStream *)stream options: (StreamingOptions)streamingOptions;

/**
 *  call a function with a @ref FLIRThermalImage from the stream
 *
 *  @param imageBlock a block with an image parameter
 */
- (void)withThermalImage:(void (^)(FLIRThermalImage *))imageBlock;


@end

@interface FLIRVividIRParameters : NSObject


/// Upscale method used, superresolution is only available on FLIR One Pro models
@property (nonatomic, assign) FLIRVividIRUpscale upscale;
/// Latency for denoise
@property (nonatomic, assign) FLIRVividIRLatency latency;
/// Denoise filters enabled
@property (nonatomic, assign) BOOL useDenoise;

@end

/**
 *  Streamer class for visual data
 */
@interface FLIRVisualStreamer : FLIRStreamer
/**
 *  Create a visual streamer on a stream
 *
 *  @param stream the stream to be streamed
 *
 *  The stream should contain visual data.
 */
- (instancetype)initWithStream:(FLIRStream *)stream;

@end

NS_ASSUME_NONNULL_END
