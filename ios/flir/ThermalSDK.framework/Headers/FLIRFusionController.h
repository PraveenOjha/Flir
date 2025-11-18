//
//  FLIRFusionController.h
//  ThermalSDK
//
//  Created by FLIR on 2020-07-02.
//  Copyright © 2020 Teledyne FLIR. All rights reserved.
//

#import <Foundation/Foundation.h>
#import <CoreGraphics/CoreGraphics.h>

NS_ASSUME_NONNULL_BEGIN

/**
 * Defines how fusion span level is displayed.
 */
typedef NS_ENUM(NSUInteger, FLIRFusionSpanLevel) {
    DC = 0,
    IR = 1,
    THERMAL_FUSION = 7
};

/**
 * Channel mode/type used to display live view on the camera.
 */
typedef NS_ENUM(NSUInteger, FLIRChannelType) {
    IR_TYPE,
    VISUAL,
    FUSION
};

/**
 * Definition of image's display mode. Depending on selected mode thermal or visual data is used to
 * render the image. Provides detailed information for fusion.
 */
typedef NS_ENUM(NSUInteger, FLIRDisplayMode) {
    /**
     * The camera sends video in basic mode: {@link ChannelType#IR} or {@link ChannelType#VISUAL}
     */
    NONE,
    /**
     * The camera video in fusion blending mode.
     */
    FUSION_MODE,
    /**
     * The camera video in picture in picture mode.
     */
    PIP,
    /**
     * The camera video in multi-spectral image mode.
     */
    MSX,
    /**
     * Image displayed a digital.
     */
    DIGITAL,
    /**
     * Image displayed as difference.
     */
    DIFF,
    /**
     * Image displayed as blending between thermal and digital images.
     */
    BLENDING
};

/** a wrapper for the display mode */
@interface FLIRMode : NSObject
/** display mode */
@property (nonatomic, assign) FLIRDisplayMode mode;
@end

/** fusion options for the camera */
@interface FLIRFusionController : NSObject

/** 
 * Defines the active channel on displaying image on live view on the camera.
 * This property may change dynamically after displayMode property is changed. Emulated cameras are NOT affected.
 */
- (FLIRChannelType)getActiveChannel;
/** 
 * Defines the active channel on displaying image on live view on the camera.
 * This property may change dynamically after displayMode property is changed. Emulated cameras are NOT affected.
 * Writing this value should be done with caution, because in order to camera physically change the mode it is also required to apply proper values to displayMode and fusionSpanLevel.
 * This is highly recommended to use displayMode only, which automatically makes sure both fusionSpanLevel and this property are set accordingly.
 * Avoid setting this property while streaming.
 */
- (BOOL)setActiveChannel: (FLIRChannelType)activeChannel  error: (out NSError * _Nullable *_Nullable)error;
/** subscribe to changes of the active channel (delegate method will be called when channel changes) */
- (BOOL)subscribeActiveChannel: (out NSError * _Nullable *_Nullable)error;
/** unsubscribe to changes of the active channel */
- (void)unsubscribeActiveChannel;

/** list valid display modes for the camera */
- (NSArray<FLIRMode*>* _Nullable)getValidModes;
/** get true if msx is supported */
- (BOOL)getMsxSupported;
/** get a rectangle for picture in picture display */
- (CGRect)getPIPWindow;
/** set a rectangle for picture in picture display */
- (BOOL)setPIPWindow: (CGRect)pipWindow error: (out NSError * _Nullable *_Nullable)error;
/** get true if fusion is always on */
- (BOOL)getFusionAlwaysOn;
/** get or set the display mode used */
- (FLIRDisplayMode)getDisplayMode;
/** get or set the display mode used */
- (BOOL)setDisplayMode: (FLIRDisplayMode)displayMode error: (out NSError * _Nullable *_Nullable)error;
/** 
 * Defines how fusion span level is displayed.
 * This property may change dynamically after displayMode property is changed. Emulated cameras are NOT affected.
 */
- (FLIRFusionSpanLevel)getFusionSpanLevel;
/** 
 * Defines how fusion span level is displayed.
 * This property may change dynamically after displayMode property is changed. Emulated cameras are NOT affected.
 * Writing this value should be done with caution, because in order to camera physically change the mode it is also required to apply proper values to displayMode and activeChannel.
 * This is highly recommended to use displayMode only, which automatically makes sure both activeChannel and this property are set accordingly.
 */
- (BOOL)setFusionSpanLevel: (FLIRFusionSpanLevel)fusionSpanLevel error: (out NSError * _Nullable *_Nullable)error;

/**
 * Set the fusion distance in meters.
 * This is only to be used to modify the fusion distance on a live stream.
 * @param distance distance to the object in meters. Range 0.1 to 10.0
 *  @param error reason for error
*/
-(BOOL) setFusionDistance:(double) distance error:(out NSError * _Nullable * _Nullable)error;

/**
 *  Gets the fusion distance in meters.
 */
-(double) getFusionDistance;

@end

NS_ASSUME_NONNULL_END
