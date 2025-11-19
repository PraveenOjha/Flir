//
//  FLIRImageBase.h
//
//  Copyright © 2019 Teledyne FLIR. All rights reserved.
//

#ifndef FLIRImageBase_h
#define FLIRImageBase_h
#endif

#import <Foundation/Foundation.h>

/**
 *  Indicates the altitude used as the reference altitude in the GPS information/data.
 */
typedef NS_ENUM(NSUInteger, FLIRAltitudeReference) {
    /// Indicates the altitude value is above sea level.
    FLIRSeaLevel,

   /// Indicates the altitude value is below sea level and the altitude is indicated as an absolute value in altitude.
    FLIRBelowSeaLevel,
};

/**
 *  struct provides basic compass information.
 */
typedef struct CompassInfo
{
    /**  Compass direction in degrees, 1-360. */
    int degrees;
    /**  The terminal's rotation in degrees around its own longitudinal axis, -180.0 - +180.0 . */
    int roll;
    /** Sensor pitch in degrees, -90 - +90. */
    int pitch;
    /** Compass tilt in degrees, 0-360. */
    int tilt;
} /** struct provides basic compass information. */ CompassInformation_t;

/**
 * Defines a GPS information like latitude, longitude, etc..
 */
typedef struct GpsInfo
{
    /** A value indicating whether this gps information is valid */
    bool isValid;

    /** Gps DOP (data degree of precision) */
    float dop;

    /** The altitude.
     *
     * If the altitude reference is below sea level, the altitude is indicated as an absolute value in altitude.
     * The unit is meters.
     */
    float altitude;

    /** Altitude reference indicates if the altitude value is below or above see level. */
    FLIRAltitudeReference altitudeRef;

    /** The latitude. */
    double latitude;

    /** Latitude reference
     *
     *  Indicates whether the latitude is north or south latitude.
     *  The value 'N' indicates north latitude, and 'S' is south latitude.
     */
    __unsafe_unretained NSString *latitudeRef;

    /**  The longitude */
    double longitude;

    /**  Longitude reference.
     *
     *   Indicates whether the longitude is east or west longitude.
     *   The value 'E' indicates east longitude, and 'W' is west longitude.
     */
    __unsafe_unretained NSString *longitudeRef;

    /** Map datum
     *
     *   Indicates the geodetic survey data used by the GPS receiver.
     */
    __unsafe_unretained NSString *mapDatum;

    /**  The satellites
     *
     *    Indicates the GPS satellites used for measurements.
     *    This tag can be used to describe the number of satellites,
     *    their ID number, angle of elevation, azimuth, SNR and other information in ASCII notation.
     */
    __unsafe_unretained NSString  *satellites;

    /** The time
     *
     *  Indicates the time as UTC (Coordinated Universal Time).
     */
    __unsafe_unretained NSDate *timeStamp;
} /** Defines a GPS information like latitude, longitude, etc.. */  GpsInformation_t;


/**
 *  Defines a clock-wise rotation angle which can be applied for a ThermalImage.
 */
typedef NS_ENUM(NSUInteger, RotationAngle)
{
    /**  Image should be rotated by 180 degrees. */
    ROTATION_ANGLE_180_DEGREES,

    /**  Image should be rotated by 270 degrees. */
    ROTATION_ANGLE_270_DEGREES,

    /**  Image should be rotated by 90 degrees. */
    ROTATION_ANGLE_90_DEGREES
};


/**
 *  A base definition for all FLIR images.
 *  Provides basic, common interface to handle the images within FLIR Thermal SDK regardless of their source.
 */
@interface FLIRImageBase : NSObject


/**
 *  Gets the width of the image in pixels.
 *  Corresponding image is a UIImage object from FLIRThermalImage#getImage(), which represents an image's colorized pixels data depending on selected FusionMode.
 *
 *  @return The width of the image.
 */
-(int) getWidth;

/**
 *  Gets the height of the image in pixels.
 *  Corresponding image is a UIImage object from FLIRThermalImage#getImage(), which represents an image's colorized pixels data depending on selected FusionMode.
 *
 *  @return The height of the image.
 */
-(int) getHeight;

/**
 *  Gets a CompassInformation object with compass details embedded in the image.
 *
 *  @return a CompassInformation object with compass details embedded in the image.
 */
-(CompassInformation_t) getCompassInformation;

/**
 *  Sets the  compass details embedded in the image.
 *
 *  @param compass a CompassInformation_t object with compass details.
 */
-(void) setCompassInformation:(CompassInformation_t) compass;

/**
 *  Gets a GpsInformation object with GPS details embedded in the image.
 *
 *  @return a GpsInformation object with GPS details embedded in the image.
 */
-(GpsInformation_t) getGpsInformation;

/**
 * Sets the GPS details embedded in the image.
 *
 * @param  gps a  GpsInformation_t object with GPS details.
*/

- (void) setGpsInformation:(GpsInformation_t) gps;
/**
 *  Rotates the image by certain angle. Only rotation by angle defined in RotationAngle is supported.
 *  This is relative rotation, meaning that rotating 0 degrees would be a no-op.
 *  @note The image pixels will be rotated instantly, so this function may incur heavy computation.
 *  @note This image rotation feature is intended to be used for still images (a file-based FLIRhermalImage). For live streaming stream rotation is currently undefined and may give unexpected results.
 *  Especially when used with enabled measurements or other FLIRThermalImage tools.
 *  If you want to rotate a live stream we recommend rotating the colorized pixels (UIImage), which were acquired i.e. by using FLIRThermalImage.getImage() function.
 *  @note Important: When rotating the colorized pixels any existing FLIRThermalImage tools (i.e. measurements) are not rotated.
 *  This means that when using measurements with rotated colorized pixels you are required to handle the difference in the displayed image and the actual FLIRThermalImage
 *  i.e. calculate the correct positions and redraw measurements when the image is rotated/flipped. This can became quite complicated and has to be addresses carefully.
 */
- (void) rotate:(RotationAngle) angle;

/**
 *  Gets a file description from EXIF data.
 *
 * @return description from EXIF data or null, if description could not be obtained.
 */
-(NSString *) getDescription;

/**
 *  Sets file description in EXIF data
 *
 * @param description - the description to be set in the EXIF data.
 */
-(void) setDescription:(NSString *) description;

@end
