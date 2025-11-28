//
//  FLIRThermalImage.h
//
//  Copyright © 2019 Teledyne FLIR. All rights reserved.
//

#pragma once

#import "FLIRImageBase.h"
#import "FLIRMeasurementCollection.h"
#import "FLIRPaletteManager.h"
#import "FLIRThermalParameters.h"
#import "FLIRImageStatistics.h"
#import "FLIRFusion.h"
#import "FLIRScale.h"
#import "FLIRThermalValue.h"
#import "FLIRDisplaySettings.h"
#import "FLIRMeterLinkSensorPoll.h"
#import "FLIRIsotherms.h"
#import "FLIRColorDistributionSettings.h"
#import "FLIRIdentity.h"
#import "FLIRQuantification.h"

/**
 *  Specifies the color distribution of the thermal image.
 */
typedef enum ColorDistribution_type
{
    /**
     *  This is an image-displaying method that distributes the colors according to temperature.
     */
    TemperatureLinear,
    /**
     *  This is an image-displaying method that evenly distributes the color information over 
     *  the existing temperatures of the image. This method to distribute the information can be particularly
     *  successful when the image contains few peaks of very high temperature values.
     */
    HistogramEqualization

} /** Specifies the color distribution of the thermal image. */ ColorDistribution;

/**
 *  Holds information about the camera used to create the Thermal image.
 *  Provides an information about a camera device, like serial number, model, lens type/name.
 *  All information given is as written by the camera. For extra information, refer to the user manual of the camera.
 */
@interface CameraInfo : NSObject
/**  The camera filter information. */
@property (nonatomic, readonly, nonnull) NSString *filter;

/**  The camera lens information. */
@property (nonatomic, readonly, nonnull) NSString *lens;

/**  The camera model information. */
@property (nonatomic, readonly, nonnull) NSString *modelName;

/**  The camera serial number information. */
@property (nonatomic, readonly, nonnull) NSString *serialNumber;

/// The program version
@property (nonatomic, readonly, nonnull) NSString* progVer;

/// The camera article number
@property (nonatomic, readonly, nonnull) NSString* articleNumber;

/// flir one type (only valid for flir one cameras)
@property (nonatomic, readonly) FLIROneType flirOneType;

/**
 *  The lower limit of the camera's measurement range.
 *  Note that there are some preconditions/limits related to camera's measurement range, when using remote API 'TemperatureRange.selectedIndex()'.
 */
@property (nonatomic, readonly, nonnull) FLIRThermalValue *rangeMin;

/**  
 *  The upper limit of the camera's measurement range.
 *  Note that there are some preconditions/limits related to camera's measurement range, when using remote API 'TemperatureRange.selectedIndex()'.
 */
@property (nonatomic, readonly, nonnull) FLIRThermalValue *rangeMax;

/// Calibration information
@property (nonatomic, readonly, nonnull) NSString* calibrationTitle;
@property (nonatomic, readonly, nonnull) NSString* lensSerialNumber;
@property (nonatomic, readonly, nonnull) NSString* arcVersion;
@property (nonatomic, readonly, nonnull) NSString* arcDate;
@property (nonatomic, readonly, nonnull) NSString* arcSignature;
@property (nonatomic, readonly, nonnull) NSString* countryCode;
@property (nonatomic, readonly) NSInteger horizontalFoV;
@property (nonatomic, readonly) float focalLength;

@end



/** Specifies the supported distance units. */
typedef NS_ENUM(NSUInteger, DistanceUnit)
{
    /** The metre or meter is the fundamental unit of length in the International System of Units. */
    METER,
    /** International foot (equal to 0.3048 meters). */
    FEET
};

/**
 * Defines text annotations that can be stored with the Thermal image
 */
@interface FLIRTextAnnotation : NSObject
@property (nonatomic, nonnull) NSString* first DEPRECATED_MSG_ATTRIBUTE("Use label instead, this property will be removed.");
@property (nonatomic, nonnull) NSString* second DEPRECATED_MSG_ATTRIBUTE("Use value instead, this property will be removed.");

@property (nonatomic, nonnull) NSString* label;
@property (nonatomic, nonnull) NSString* value;

- (instancetype _Nonnull)initWithLabel:(NSString * _Nonnull)label value:(NSString * _Nonnull)value;

@end

/**
 *  Defines features of a Thermal image created from file or stream with temperature (radiometric) data.
 *  The object of this class can be obtained from Thermal cameras supporting such streaming mode.
 *  There is also a specialized subclass for handling Thermal data stored in files. The ThermalImageFile should be used for them.
 */
@interface FLIRThermalImage : FLIRImageBase

/** The measurements collection. */
@property (nonatomic, readonly, nullable) FLIRMeasurementCollection *Measurements;

/** The palette manager. */
@property (nonatomic, readonly, nullable) FLIRPaletteManager *PaletteManager;

/** Gets a Palette object set for this image. */
@property (nonatomic, readwrite, nullable) FLIRPalette *Palette;

/**
 *  Gets an ThermalParameters object for this instance.
 */
- (FLIRThermalParameters * _Nullable)getThermalParameters;

/**
 *  A list of external sensor poll values
 *
 *  @return  arrayf of @ref FLIRMeterLinkSensorPoll FLIRMeterLinkSensorPoll
 */
- (NSArray<FLIRMeterLinkSensorPoll *> * _Nonnull)getExternalSensorPolls;

/**
 *  Gets a UIImage object, which represents an image's colorized pixels data depending on selected FusionMode.
 *  Rendered/colorized image, which size, appearance etc. heavily depends on the FusionMode.
 *  Note: raw IR and DC pixels are used to generate this image (depending on fusion mode), and DC image will be aligned even in FusionMode#VISUAL_MODE mode.
 *
 *  @return An UIImage representing the thermal image.
 */
- (UIImage * _Nullable)getImage DEPRECATED_MSG_ATTRIBUTE("This call will be removed, use FLIRImageColorizer (still images) or FLIRThermalStreamer (live streams) instead");

/**
 *  Gets an ImageStatistics object providing the statistics for Thermal data.
 *
 *  @return The image statistics in a FLIRImageStatistics object
 */
- (FLIRImageStatistics * _Nullable)getImageStatistics;

/**
 *  Gets the scale object.
 *
 *  @return The image scale as FLIRScale object
 */
- (FLIRScale * _Nullable)getScale;

/**
 *  Gets a Fusion object set for this image.
 *
 *  @return The image fusion as a FLIRFusion object
 */
-(FLIRFusion * _Nullable)getFusion;

/**
 *  Gets the thermal image color distribution.
 *
 *  @return The color distribution.
 */
- (ColorDistribution)getColorDistribution DEPRECATED_MSG_ATTRIBUTE("This call will be removed, use getColorDistributionSettings instead");

/**
 *  Sets the thermal image color distribution.
 *
 *  @param value The color distribution.
 */
- (void)setColorDistribution:(ColorDistribution)value;

/**
 *  Gets a {@link FLIRColorDistributionSettings} for the image.
 */
- (FLIRColorDistributionSettings * _Nonnull)getColorDistributionSettings;

/**
 *  Sets the {@link FLIRColorDistributionSettings} for the image.
 */
- (void)setColorDistributionSettings: (FLIRColorDistributionSettings * _Nonnull)settings;

/**
 * Gets the CameraInformation describing the device the image was taken with.
 *
 * @return CameraInformation_t
 */
-(CameraInfo * _Nullable)getCameraInformation;

/**
 * Return display settings, with information on pan and zoom
 */
- (FLIRDisplaySettings * _Nullable)getDisplaySettings;

/**
 * Set display settings, with information on pan and zoom
 */
- (BOOL)setDisplaySettings:(FLIRDisplaySettings * _Nonnull)displaySettings error:(out NSError * _Nullable * _Nullable)error;

/**
 *  Gets a DistanceUnit used to define any distance parameter related with image.
 *
 *  @return DistanceUnit
 */
-(DistanceUnit) getDistanceUnit;


/**
 *  Gets a visual image (photo), which is not fusion aligned, as a UIImage extracted from the file.
 *  Raw visual pixels/"photo"/"DC", usually in YCbCr format.
 *  These are only available if the thermal image was created with fusion data and visual pixel data.
 *  These pixels are not aligned to the IR pixels.
 */
- (UIImage * _Nullable)getPhoto;


/**
 *  Gets a TemperatureUnit the ThermalValues are in.
 */
-(TemperatureUnit) getTemperatureUnit;


/**
 *  Gets a text annotations associated with the file.

 @return array with annotations
 */
- (NSArray<FLIRTextAnnotation *> * _Nullable)getTextAnnotations:(out NSError * _Nullable * _Nullable)error;

/**
 *  Sets a text annotations associated with the file.

 @param annotations array with annotations
 */
- (BOOL)setTextAnnotations:(NSArray<FLIRTextAnnotation *> * _Nonnull)annotations error:(out NSError * _Nullable * _Nullable)error;

/**
 *  Gets a temperature value from a point(x,y) in the Thermal Image.
 
 *  Due to Lepton thermal camera core specifics, reading a value from a single point may be inaccurate.
 *  Thus this is recommended to use a measurement spot instead, where value calculation is internally compensated based on a several adjacent points and calibration data.
 *
 *  @param point the pixel coordinate
 *  @return temperature value
 */
-(FLIRThermalValue * _Nullable) getValueAt:(CGPoint) point error:(out NSError * _Nullable * _Nullable)error;

/**
 *  Gets temperature values from a list of points(x,y) in the Thermal Image.
 *
 *  @param points array with pixel coordinates, given as NSValue wrapping CGPoint
 *
 *  @return array with temperature values, given as NSNumber wrapping double with Kelvin value
 */
-(NSArray<NSNumber *> * _Nullable)getValues:(NSArray<NSValue *> * _Nonnull)points error:(out NSError * _Nullable * _Nullable)error;

/**
 *  Gets temperature values from a list of points(x,y) in the Thermal Image.
 *
 *  @param rectangle a rectangular area within the thermal image
 *
 *  @return array with temperature values, given as NSNumber wrapping double with Kelvin value
 */
-(NSArray<NSNumber *> * _Nullable)getValuesFromRectangle:(CGRect)rectangle error:(out NSError * _Nullable * _Nullable)error;

/**
 *  Sets a DistanceUnit used to define any distance parameter related with image.
 *
 */
-(void) setDistanceUnit:(DistanceUnit) value;


/**
 *  Sets a TemperatureUnit the ThermalValues are in.
 */
-(void) setTemperatureUnit:(TemperatureUnit) value;

/**
 *  Saves a thermal image to file.
 *
 *  @param fileName  Path to file.
 *  @param overlay   The UIImageView with ovelay and the thermal image.
 */
- (BOOL)saveAs:(NSString * _Nonnull)fileName imageViewWithOverlay:(UIImageView * _Nonnull)overlay error:(out NSError * _Nullable * _Nullable)error;

/**
 *  Saves a thermal image to file.
 *
 *  @param fileName  Path to file.
 *  @param imageWithOverlay   The UIImage with ovelay and the thermal image.
 */
- (BOOL)saveAs:(NSString * _Nonnull)fileName imageWithOverlay:(UIImage * _Nonnull)imageWithOverlay error:(out NSError * _Nullable * _Nullable)error;

/**
 *  Saves a thermal image to file.
 *
 *  @param fileName  Path to file.
 */
- (BOOL)saveAs:(NSString * _Nonnull)fileName error:(out NSError * _Nullable * _Nullable)error;

/**
 *  Gets a date when the image was taken.
 */
- (NSDate * _Nullable)getDateTaken;

/** @brief Gets the isotherms collection. */
- (FLIRIsotherms * _Nonnull)getIsotherms;

/// gas quantification input, nil if image does not contain gas quantification input data
@property (nonatomic, nullable, readonly) FLIRGasQuantificationInput* gasQuantificationInput;
/// gas quantification result
@property (nonatomic, nullable, readonly) FLIRGasQuantificationResult* gasQuantificationResult;

@end
