//
//  FLIRFusion.h
//
//  Copyright © 2019 Teledyne FLIR. All rights reserved.
//

#ifndef FLIRFusion_h
#define FLIRFusion_h
#endif 

#import "FLIRFusion.h"
#import "FLIRThermalValue.h"
#import "FLIRFusionTransformation.h"

/**
 *  Specifies the fusion modes of the thermal image.
 */
typedef enum FUSION_MODE
{
    /** Ordinary IR image. */
    IR_MODE,
    
    /** 
     * Visual image is zoomed and fused with IR image.
     * Note: for not aligned DC image use FLIRThermalImage#getPhoto().
     */
    VISUAL_MODE,
    
    /** Picture in picture fusion, where a defined box/rectangle shape with IR image pixels is placed on top of the visual image. */
    FUSION_PIP_MODE,
    
    /** IR image is blended with visual image based on the specified {@link com.flir.thermalsdk.image.ThermalValue} threshold. */
    FUSION_THERMAL_MODE,
    
    /** Thermal MSX (Multi Spectral Dynamic Imaging) mode displays an infrared image where the edges of the objects are enhanced with information from the visual image. */
    FUSION_MSX_MODE,
    
    /** IR image is blended with visual image based on the specified blending factor value. Use Blending mode to display a blended image that uses a mix of infrared pixels and digital photo pixels. */
    FUSION_BLENDING_MODE,
} /**  Specifies the fusion modes of the thermal image. */ FusionMode;

/**
 *  Enumeration of the different fusion color modes.
 */
typedef enum ColorMode
{
				/// <summary>
				/// Fusion image has visual image in color.
				/// </summary>
				Color = 0,

				/// <summary>
				/// Fusion image has visual image in b/w.
				/// </summary>
				BlackAndWhite = 1
} /** Enumeration of the different fusion color modes. */ VisualColorMode;

/**
 *  This class provides functionality for image fusion.
 *
 *  @note Image fusion is a function that will merge the Thermal image with a photo depending on
 *  the selected fusion mode.
 */
@interface FLIRFusion : NSObject

/** 
 *  Set the fusion mode
 *
 *  @param mode	The fusion mode.
 */
-(void) setFusionMode: (FusionMode) mode;

/**
 *  Get the fusion mode.
 *
 *  @return The fusion mode.
 */
-(FusionMode) getFusionMode;

/**
 *  Defines multi-spectral fusion image mode.
 *  Thermal MSX (Multi Spectral Dynamic Imaging) mode displays an infrared image where the edges of the objects are enhanced with information from the visual image.
 *  The higher the alpha level, the more edges will be detected.
 *  Thermal image may appear a bit differently for the same MSX alpha value depending on the camera's resolution.
 *  Thus the value should be based on the expected results and visible effect, but typical and suggested values are:
 *  - FLIR ONE gen 2: 2.0 or 3.0
 *  - FLIR ONE gen 3: 8.0 or higher
 *
 *  @note Does no longer change the fusion mode.
 *  @param alpha The intensity of the MSX, indicate how much the visual image is blended with IR (valid value range: [0.0, 100.0]).
 */
-(void) setMSX: (double) alpha;

/**
 * Get thermal image MSX settings.
 */
-(double) getMSX;

/**
 *  Defines the fusion image mode, where Thermal image is displayed in defined area on top of the Visual image.
 *
 *  @note Does no longer change the fusion mode.
 *  @param rect The rectangle where the Thermal image will be placed inside the photo.
 */
-(void) setPictureInPicture: (CGRect) rect;

/**
 *  Get the picture in picture rectangle.
 *
 *  @return Rectangle that defines the PiP position and size.
 */
- (CGRect) getPictureInPictureRect;

/**
 *  Defines the fusion image mode, where only the visual image is displayed. Settings for FusionMode Visual.
 *  This mode hides the Thermal Image, only the digital photo is displayed.
 *  Note: visual image is zoomed and fused with IR image. For not aligned DC image use FLIRThermalImage#getPhoto().
 *
 *  @note Does no longer change the fusion mode.
 *  @param mode Currently used color scheme the visual image is displayed in.
 */
-(void) setVisualOnly:(VisualColorMode) mode;

 /**
  * Get thermal image visual only settings.
  */
-(VisualColorMode) getVisualOnly;

/**
 *  Defines the image fusion mode, where thermal and visual image is combined for an output image.
 *
 *  @note Does no longer change the fusion mode.
 *  @param min The minimum blending level between thermal and visual image.
 *  @param max The maximum blending level between thermal and visual image.
 */
- (void)setThermalFusionWithMin:(FLIRThermalValue * _Nonnull)min andMax:(FLIRThermalValue * _Nonnull) max;

/**
 *  Defines the image fusion mode, where thermal and visual image is combined for an output image.
 *
 *  @note Does no longer change the fusion mode.
 *  @param min The minimum blending level between thermal and visual image.
 */
- (void)setThermalFusionWithMin:(FLIRThermalValue * _Nonnull)min;

/**
 *  Defines the image fusion mode, where thermal and visual image is combined for an output image.
 *
 *  @note Does no longer change the fusion mode.
 *  @param max The maximum blending level between thermal and visual image.
 */
- (void)setThermalFusionWithMax:(FLIRThermalValue * _Nonnull)max;

/**
 * Get thermal image thermal fusion min level.
 */
- (FLIRThermalValue * _Nonnull)getThermalFusionMin;

/**
 * Get thermal image thermal fusion max level.
 */
- (FLIRThermalValue * _Nonnull)getThermalFusionMax;


/**
 *  Defines the fusion image mode, where Thermal image blends with the Visual image.
 *  Use Blending mode to display a blended image that uses a mix of infrared pixels and digital photo pixels.
 *  The blending level between both images can be adjusted based on the temperatures stored for the Thermal image.
 *
 *  @note Does no longer change the fusion mode.
 *  @param level The blending level between thermal and visual image. (1.0 = only IR, 0.0 = only visual).
 *  @param colorMode The color scheme the visual image is displayed in.
 */
- (void)setBlending:(double)level ColorMode:(VisualColorMode)colorMode;

/**
 * Get thermal image thermal blending level.
 */
- (double)getBlendingLevel;

/**
 * Get thermal image thermal blending colormode.
 */
- (VisualColorMode)getBlendingColorMode;

/**  Set fusion transformation. */
- (void)setTransformation:(FLIRFusionTransformation * _Nonnull)transformation;

/**  Get fusion transformation. */
- (FLIRFusionTransformation * _Nonnull)getTransformation;

@end
