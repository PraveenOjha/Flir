//
//  FLIRStream.h
//  ThermalSDK
//
//  Created by FLIR on 2021-10-11.
//  Copyright © 2021 Teledyne FLIR. All rights reserved.
//

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/**
 *  Delegate for streaming
 */

@protocol FLIRStreamDelegate <NSObject>

/**
 *  Called when an error occurs in streaming
 */
- (void)onError:(NSError *)error;

/**
 *  Called when an image is available on the stream
 */
- (void)onImageReceived;

@end

/**
 *  Camera stream class.
 *  @note the implementation for thermal streaming for network cameras is still in experimental stage.
 */
@interface FLIRStream : NSObject

/**
 *  Delegate with callbacks
 */
@property (nonatomic, strong, nullable) id<FLIRStreamDelegate> delegate;

/**
 *  True if the stream contains both thermal and visual data
 */
@property (nonatomic, readonly) BOOL isDual;

/**
 *  Width and height of the IR data for each frame
 *  (Zero If no IR data is available)
 */
@property (nonatomic, readonly) CGSize irSize;

/**
 *  Width and height of the visual data for each frame
 *  (Zero If no visual data is available)
 */
@property (nonatomic, readonly) CGSize visualSize;

/**
 *  True if the stream contains readiometric data
 */
@property (nonatomic, readonly) BOOL isThermal;

/**
 *  True if the stream is active and streaming
 */
@property (nonatomic, readonly) BOOL isStreaming;

/**
 *  Start frame grabbing on the provided stream.
 *
 *  Starts the frame grabbing. When a new frame is available the delegate method onImageRecieved will be called.
 *  @note the implementation for thermal streaming for network cameras is still in experimental stage.
 *  @param error  The error if start returns false
 *  @return returns false if not able to start stream
 */
- (BOOL)start:(out NSError * _Nullable __autoreleasing * _Nullable)error;

/**
 *  Stop frame grabbing on the stream.
 */
- (void)stop;

/**
 * Set the camera's streamed frame rate.<br />
 * Note: Some cameras will not change frame rate while streaming.
 *
 * @param hz Frames per second, must be in between getMinFrameRate and getMaxFrameRate, inclusive
 */
- (BOOL)setFrameRate:(double)hertz error:(out NSError * _Nullable __autoreleasing * _Nullable)error;

/**
 * Get the camera's streamed frame rate. If the frame rate cannot be fetched, zero is returned.
 */
- (double)getFrameRate;

/**
 * Get the lowest possible frame rate for this camera stream.
 */
- (double)getMinFrameRate;

/**
 * Get the highest possible frame rate for this camera stream. If the frame rate cannot be fetched, zero is returned.
 */
- (double)getMaxFrameRate;

@end

NS_ASSUME_NONNULL_END
