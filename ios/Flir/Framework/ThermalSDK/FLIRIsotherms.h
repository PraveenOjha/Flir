//
//  FLIRIsotherms.h
//  ThermalSDK
//
//  Created by FLIR on 2021-04-28.
//  Copyright © 2021 Teledyne FLIR. All rights reserved.
//

#pragma once

#import <UIKit/UIKit.h>

#import "FLIRThermalValue.h"

/**  Blending mode to for isotherm with single color */
typedef NS_ENUM(NSInteger, FLIRIsothermBlendingMode) {
    /**  Use a single color for the isotherm. */
    BM_solid,

    /**  Use a transparent isotherm. */
    BM_transparent,

    /**  Use the Y (in YCbCr) value from the palette underneath the isotherm. */
    BM_followY,

    /**  Link several isotherms by their Y value (in YCbCr). */
    BM_linkedY,

};

@class FLIRIsotherm;
@class FLIRAboveIsotherm;
@class FLIRBelowIsotherm;
@class FLIRIntervalIsotherm;
@class FLIRHumidityIsotherm;
@class FLIRInsulationIsotherm;

/**
 * fillmode properties of an isotherm
 * @note to change an isotherm, @ref setFillmode must be called after changing properties here
 * */
@interface FLIRIsothermFillmode : NSObject

/**  palette, a list of colors used for the isotherm, if this is nil, the preset color or color with blending mode is used instead */
@property (nonatomic, retain, nullable) NSArray<UIColor*>* palette;
/**  color, not used if palette is set */
@property (nonatomic, retain, nullable) UIColor* color;
/**  blending mode, note used if palette is set */
@property (nonatomic, assign) FLIRIsothermBlendingMode blendingMode;

@end

/**  an interface to the isotherms defined for the image */
@interface FLIRIsotherms : NSObject

/**  add an isotherm with below cutoff */
- (FLIRBelowIsotherm * _Nonnull)addBelowWithCutoff:(FLIRThermalValue * _Nonnull)cutoff
                                          fillMode:(FLIRIsothermFillmode * _Nonnull)fillMode;
/**  add an isotherm with above cutoff */
- (FLIRAboveIsotherm * _Nonnull)addAboveWithCutoff:(FLIRThermalValue * _Nonnull)cutoff
                                          fillMode:(FLIRIsothermFillmode * _Nonnull)fillMode;
/**  add an isotherm with interval */
- (FLIRIntervalIsotherm * _Nonnull)addIntervalWithMin:(FLIRThermalValue * _Nonnull)min
                                                  max:(FLIRThermalValue * _Nonnull)max
                                             fillMode:(FLIRIsothermFillmode * _Nonnull)fillMode;
/**  add a humidity point isotherm
 */
- (FLIRHumidityIsotherm * _Nonnull)addWithAirHumidity:(float)airHumidity
                                airHumidityAlarmLevel:(float)airHumidityAlarmLevel
                               atmosphericTemperature:(FLIRThermalValue * _Nonnull)atmosphericTemperature
                                             fillMode:(FLIRIsothermFillmode * _Nonnull)fillMode;

/**  add an insulation isotherm
 */

- (FLIRInsulationIsotherm * _Nonnull)addInsulationWithIndoorAirTemperature:(FLIRThermalValue * _Nonnull)indoorAirTemperature
                                                     outdoorAirTemperature:(FLIRThermalValue * _Nonnull)outdoorAirTemperature
                                                          insulationFactor:(float)insulationFactor
                                                                  fillMode:(FLIRIsothermFillmode * _Nonnull)fillMode;

/**  remove an isotherm
 */
- (void)remove:(FLIRIsotherm * _Nonnull)isotherm;

/**  remove all isotherms
 */
- (void)removeAll;

/**  all isotherms for the image
 */
- (NSArray<FLIRIsotherm *> * _Nonnull)getAll;

/**  number of isotherms
 */
@property (nonatomic, readonly) NSUInteger count;

@end

/**  an object with isotherm properties
 */
@interface FLIRIsotherm : NSObject

/**  the fillmode of the isotherm
 */
@property (nonatomic, nonnull) FLIRIsothermFillmode* fillMode;

@end

@interface FLIRCutoffIsotherm : FLIRIsotherm

/**  cutoff temperature
 */
@property (nonatomic, nonnull) FLIRThermalValue* cutoff;

@end

/**
 *  An isotherm that is covering an area starting at the cutoff and above that temperature.
 */
@interface FLIRAboveIsotherm : FLIRCutoffIsotherm
@end

/**
 *  An isotherm that is covering an area starting at the cutoff and below that temperature.
 */
@interface FLIRBelowIsotherm : FLIRCutoffIsotherm
@end

/**
 *  An isotherm that is covering an area between the defined min and max temperature.
 */
@interface FLIRIntervalIsotherm : FLIRIsotherm

/**  min temperature
 */
@property (nonatomic, retain, nonnull) FLIRThermalValue* min;

/**  max temperature
 */
@property (nonatomic, retain, nonnull) FLIRThermalValue* max;

@end

/**
 *  The humidity isotherm can detect areas where there is a risk of mold growing, or where there is a
 *  risk of the humidity falling out as liquid water (i.e., the dew point).
 */
@interface FLIRHumidityIsotherm : FLIRIsotherm

/**  air humidity in percent (0.0 - 100.0).
 */
@property (nonatomic, assign) float airHumidity;

/**  air humidity alarm level in percent (0.0 - 100.0)
 */
@property (nonatomic, assign) float airHumidityAlarmLevel;

/**  atmospheric temperature
 */
@property (nonatomic, assign, nonnull) FLIRThermalValue *atmosphericTemperature;


/**  Get the calculated dew point temperature.
 */
@property (nonatomic, readonly, nonnull) FLIRThermalValue *dewPointTemperature;

/**  Get the calculated dew point temperature threshold.
 */
@property (nonatomic, readonly, nonnull) FLIRThermalValue *thresholdTemperature;

@end

/**
 *  The insulation isotherm can detect areas where there may be an insulation deficiency in the building.
 *  * It will trigger when the insulation level falls below a preset value of the energy leakage through the
 *  * building structure - the so-called thermal index/insulation factor. Different building codes recommend different values
 *  * for the thermal index/insulation factor, but typical values are 60 - 80% for new buildings.
 *  * Refer to your national building code for recommendations.
 */
@interface FLIRInsulationIsotherm : FLIRIsotherm

/**  indoor air temperature
 */
@property (nonatomic, retain, nonnull) FLIRThermalValue *indoorAirTemperature;
/**  outdoor air temperature
 */
@property (nonatomic, retain, nonnull) FLIRThermalValue *outdoorAirTemperature;

/**  insulation factor in percent (0.0 - 100.0).
 */
@property (nonatomic, assign) float insulationFactor;

/**  Get the calculated insulation temperature.
 */
@property (nonatomic, readonly, nonnull) FLIRThermalValue *insulationTemperature;

/**
 *  Calculate the insulation temperature.
 *  @param indoorAirTemp    Indoor air temperature in Kelvin.
 *  @param outdoorAirTemp    Outdoor air temperature in Kelvin.
 *  @param insulationFactor    Insulation factor (0.0 - 1.0).
 *  @return Insulation temperature in Kelvin
 */
+ (float)calculateInsulationTempIndoorAirTemp:(float)indoorAirTemp
                               outdoorAirTemp:(float)outdoorAirTemp
                             insulationFactor:(float)insulationFactor;

@end
