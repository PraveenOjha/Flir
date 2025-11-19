//
//  FLIRMeterLinkSensorPoll.h
//  ThermalSDK
//
//  Created by FLIR on 2021-01-19.
//  Copyright © 2021 Teledyne FLIR. All rights reserved.
//

#pragma once

#import <Foundation/Foundation.h>

/** Supported data quantities */
// must match enum ES_QUANTITY
typedef NS_ENUM(NSInteger, ExternalSensorQuantity) {
    ESQ_INVALID = 0,

    ESQ_CURRENT,
    ESQ_VOLTAGE,
    ESQ_RESISTANCE,
    ESQ_REACTANCE,
    ESQ_IMPEDANCE,
    ESQ_CONDUCTANCE,
    ESQ_CAPACITANCE,
    ESQ_INDUCTANCE,
    ESQ_EL_FIELD,
    ESQ_MAG_FIELD,
    ESQ_MAG_FLUX,
    ESQ_CHARGE,
    ESQ_TEMPERATURE,
    ESQ___FREE_SLOT,
    ESQ_TIME,
    ESQ_FREQUENCY,
    ESQ_MASS,
    ESQ_FORCE,
    ESQ_PRESSURE,
    ESQ_MOMENTUM,
    ESQ_TORQUE,
    ESQ_SPEED,
    ESQ_ACCELERATION,
    ESQ_ENERGY,
    ESQ_POWER,
    ESQ_ENTHALPY,
    ESQ_RELHUMIDITY,
    ESQ_ABSHUMIDITY,
    ESQ_RELMOISTURE,
    ESQ_DISTANCE,
    ESQ_ANGLE,
    ESQ_RADIOACTIVITY,
    ESQ_RAD_EXPOSURE,
    ESQ_RAD_ABS_DOSE,
    ESQ_RAD_EQ_DOSE,
    ESQ_MASS_MASS_FRAC,
    ESQ_MASS_VOL_FRAC,
    ESQ_VOL_VOL_FRAC,
    ESQ_RAD_FLUX,
    ESQ_LUM_FLUX,
    ESQ_LUM_INTENSITY,
    ESQ_ILLUMINANCE,
    ESQ_POWER_FACTOR,

    ESQ_NONE,            // New with Meterlink 2.1. Previous, SI units. But with MeterLink 2.1, this are more basic units.
    ESQ_NO_IMPL,         // Not mapped so far.
    ESQ_ADMITTANCE,
    ESQ_RPM,
    ESQ_DUTYCYCLE,
    ESQ_TEST_VOLT,
    ESQ_POLAR_INDEX,
    ESQ_DAR,
    ESQ_EBOND_TEST_VOLT,
    ESQ_EBOND_TEST,
    ESQ_20MA,
    ESQ_PHASE,
    ESQ_HARMONIC_LEV,
    ESQ_HARMONIC_THD,
    ESQ_CREST,
    ESQ_QUALITY_FACT,
    ESQ_DISSIPATION,
    ESQ_ESR,
    ESQ_AC_TESTER_LOAD,
    ESQ_AC_TESTER_UNLOAD_VOLT,
    ESQ_AC_TESTER_LOAD_VOLT,
    ESQ_AC_TESTER_DROP,
    ESQ_RECEPTACLE_TEST,
    ESQ_AFCI,
    ESQ_GFCI,
    ESQ_GFCI_TRIP_CURR,
    ESQ_GFCI_TRIP_TIME,
    ESQ_EDP_TRIP_CURR,
    ESQ_NEUTRAL_GROUND,
    ESQ_HOT_IMP,
    ESQ_NEUTRAL_IMP,
    ESQ_GROUND_IMP,
    ESQ_SHORT_CIRC_CURRENT,
    ESQ_EMISSIVITY,
    ESQ_RESMOISTURE,
    ESQ_GROUP,
    ESQ_CAPMOISTURE,
    ESQ_SOILMOISTURE,
    ESQ_AREA,
    ESQ_VOLUME,
    ESQ_EMF_FREQ,
    ESQ_EMF_STRENGTH,
    ESQ_WEIGHT,
    ESQ_IRRADIANCE,
    ESQ_COMPBEARING,
    ESQ_PITCH,
    ESQ_LATITUDE,
    ESQ_LONGITUDE,
    ESQ_ALTITUDE,
    
    ESQ_LAST
};

/** Supported data aux info */
// must match enum ES_AUX_INFO
typedef NS_ENUM(NSInteger, ExternalSensorAuxInfo) {
    ESAI_INVALID = 0,

    ESAI_DC,        //!< Voltage/current is DC
    ESAI_AC,        //!< Voltage/current is AC
    ESAI_INT,       //!< Measurement is internal
    ESAI_EXT,       //!< Measurement is external
    ESAI_MIN__DEPR, //!< This was actually peak min, moved to ES_MEAS_INFO
    ESAI_MAX__DEPR, //!< This was actually peak max, moved to ES_MEAS_INFO
    ESAI_DEW,       //!< Temperature is dew point temp.
    ESAI_AIR,       //!< Temperature is air temp.
    ESAI_IR,        //!< Temperature is IR reflected temp.
    ESAI_K,         //!< Temperature is thermocouple type K temp.
    ESAI_COND,      //!< Temperature is condensation temp.
    ESAI_DB,        //!< Temperature is dry bulb temp.
    ESAI_WB,        //!< Temperature is wet bulb temp.
    ESAI_WME,       //!< Mass fraction is wood moisture equivalent
    ESAI_AC_DC,     //!< Voltage/current is AC+DC (CM83)
    ESAI_VFD,       //!< LowPassFilter is applied before measument, intended for VariableFrequencyDrives (VFD)(CM83)
    ESAI_THD,       //!< THD Total Harmonic Mode (CM83)
    ESAI_PF,        //!< Power Factor (CM83)
    
    // New in MeterLink 2.1
    ESAI_WGBT,      //!< Temperature is wet bulb global temp.
    
    ESAI_LAST
};

/** Measurement info */
// must match enum ES_MEAS_INFO
typedef NS_ENUM(NSInteger, ExternalSensorMeasInfo) {
    ESMI_INVALID = 0,

    ESMI_MIN,         //!< Minimum value
    ESMI_MAX,         //!< Maximum value
    ESMI_PEAKMIN,     //!< Peak min value
    ESMI_PEAKMAX,     //!< Peak max value
    ESMI_PEAKTOPEAK,  //!< Value is peak-to-peak
    ESMI_MEAN,        //!< Mean value
    ESMI_RMS,         //!< Root mean square value
    ESMI_MEDIAN,      //!< Median value
    ESMI_ALARM,       //!< Alarm from measuring device
    ESMI_HOLD,        //!< Measuring device are in HOLD
    ESMI_ERROR,       //!< Measuring device reports error.

    ESMI_LAST
};

/** Type of Meterlink device */
// must match enum class MeterType
typedef NS_ENUM(NSInteger, ExternalSensorMeterType) {
    ESMT_OTHER = 0,
    ESMT_DMM,
    ESMT_CLAMP,
    ESMT_TEMPERATUREMETER,
    ESMT_RHMETER,
    ESMT_ANEMOMETER,
    ESMT_MANOMETER,
    ESMT_MOISTURE,
    ESMT_GASSENSOR,
    ESMT_WATERQUALITY,
    ESMT_VIBRATIONMETER,
    ESMT_SOUNDMETER,
    ESMT_WEIGHTSCALE,
    ESMT_SPEED,
    ESMT_TACHOMETER,
    ESMT_PRESSUREMETER,
    ESMT_LAST,
    ESMT_INVALID = 0XFF
};

/**
 *  Holds information of a MeterLink sensor poll and value contained in an image
 */
@interface FLIRMeterLinkSensorPoll : NSObject

/**  Meterlink protocol version. 0,1 = Meterlink 2.0. 2 = Meterlink 2.1, 3 = Meterlink 3.0 */
@property (nonatomic, readonly) NSInteger protocolVersion;

/**  Model name of Meterlink device, e.g. "CM83" */
@property (nonatomic, readonly, nonnull) NSString *modelName;

/**  Device serial number */
@property (nonatomic, readonly, nonnull) NSString *serialNumber;

/**  Device type */
@property (nonatomic, readonly) ExternalSensorMeterType meterType;

/**  Value quantity (invalid if unknown) */
@property (nonatomic, readonly) ExternalSensorQuantity quantity;

/** Value quantity as string */
@property (nonatomic, readonly, nonnull) NSString *quantityString;

/**  Auxillary information (invalid if unknown) */
@property (nonatomic, readonly) ExternalSensorAuxInfo auxInfo;

/**  Auxillary information as string */
@property (nonatomic, readonly, nonnull) NSString *auxInfoString;

/**  Measure information (invalid if unknown) */
@property (nonatomic, readonly) ExternalSensorMeasInfo measInfo;

/**  Measure information as string */
@property (nonatomic, readonly, nonnull) NSString *measInfoString;

/**  Time when the message was parsed */
@property (nonatomic, readonly, nonnull) NSDate *timeStamp;

/**  Value */
@property (nonatomic, readonly) double value;

/**  Value as string */
@property (nonatomic, readonly, nonnull) NSString *valueString;

/**  Number of significant numbers in value */
@property (nonatomic, readonly) NSInteger precision;

/**  Value is difference */
@property (nonatomic, readonly) BOOL isDifference;

/**  Value is valid  */
@property (nonatomic, readonly) BOOL isValueValid;

/**  -9=nano, -6=u, -3=milli, +3=kilo, etc. Only available for protocolVersion>=2 */
@property (nonatomic, readonly) NSInteger unitPrefix;

/**  Index of value for a multi sensor-reading packet. Only available for protocolVersion>=2 */
@property (nonatomic, readonly) NSInteger index;

@end
