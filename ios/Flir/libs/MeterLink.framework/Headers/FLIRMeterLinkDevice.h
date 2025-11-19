//
//  FLIRMeterLinkDevice.h
//  MeterLink
//
//  Copyright © 2019 Teledyne FLIR. All rights reserved.
//

#import <Foundation/Foundation.h>


/**
 Describes the type of merLink device

 - MeterType_other: Unknown device
 - MeterType_dmm: Digital Multi Meter
 - MeterType_clamp: Clamp meter
 - MeterType_temperatureMeter: Temperature meter
 - MeterType_rhMeter: Moisture meter
 - MeterType_anemometer: Anemometer
 - MeterType_manometer: Manometer
 - MeterType_moisture: Moisture meter
 - MeterType_gasSensor: Gas sensor
 - MeterType_waterQuality: Water quality meter
 - MeterType_vibrationMeter: Vibration meter
 - MeterType_soundMeter: Sound meter
 - MeterType_weightScale: Weight
 - MeterType_speed: Speedometer
 - MeterType_tachometer: Tachometer
 - MeterType_pressureMeter: Pressure meter
 - MeterType_last: last
 - MeterType_invalid: Invalid or unknown
 */
typedef NS_ENUM(NSUInteger, MLMeterType)
{
    MeterType_other = 0,        /// Unknown device
    MeterType_dmm,
    MeterType_clamp,
    MeterType_temperatureMeter,
    MeterType_rhMeter,
    MeterType_anemometer,
    MeterType_manometer,
    MeterType_moisture,
    MeterType_gasSensor,
    MeterType_waterQuality,
    MeterType_vibrationMeter,
    MeterType_soundMeter,
    MeterType_weightScale,
    MeterType_speed,
    MeterType_tachometer,
    MeterType_pressureMeter,
    MeterType_last,
    MeterType_invalid = 0xFF
};


/**
 Describes the unit

 - SensorQuantity_invalid: Unknown or invalid
 - SensorQuantity_current: electric current	ampere	A
 - SensorQuantity_voltage: electric potential difference V
 - SensorQuantity_resistance: electric resistance	ohm   Ω
 - SensorQuantity_reactance: Electrical reactance	ohm   Ω
 - SensorQuantity_impedance: Electrical impedance	ohm   Ω
 - SensorQuantity_conductance: Conductance 	siemens 	S
 - SensorQuantity_capacitance: Capacitance 	farad 	F
 - SensorQuantity_inductance: Inductance 	henry 	H
 - SensorQuantity_el_field: Electric field  Newton/Coulomb  N/C
 - SensorQuantity_mag_field: Magnetic flux density 	tesla 	T
 - SensorQuantity_mag_flux: Magnetic flux 	weber 	Wb
 - SensorQuantity_charge: Charge 	coulomb 	C
 - SensorQuantity_temperature: Temperature 	kelvin 	K
 - SensorQuantity_free_slot: not used
 - SensorQuantity_time: Time 	second 	s
 - SensorQuantity_frequency: Frequency 	hertz 	Hz
 - SensorQuantity_mass: Mass 	kilogram 	kg
 - SensorQuantity_force: Force 	newton 	N
 - SensorQuantity_pressure: Pressure 	pascal 	Pa
 - SensorQuantity_momentum: Momentum   kilogram meter per second kg · m/s
 - SensorQuantity_torque: Torque   newton metre  N⋅m
 - SensorQuantity_speed: Speed   meter per second m/s
 - SensorQuantity_acceleration: Acceleration   meter per second squared    m/s2
 - SensorQuantity_energy: Energy 	joule 	J
 - SensorQuantity_power: Power 	watt 	W
 - SensorQuantity_enthalpy: Entalphy   joule per kilogram   J/kg
 - SensorQuantity_relhumidity: Relative humidity    percent   %
 - SensorQuantity_abshumidity: Absolute humidity    grams of water vapor per cubic meter volume of air
 - SensorQuantity_relmoisture: Relative moisture %
 - SensorQuantity_distance: Length 	meter 	m
 - SensorQuantity_angle: Radian   rad
 - SensorQuantity_radioactivity: Activity 	becquerel 	Bq
 - SensorQuantity_rad_exposure: Radiation exposure    coulomb per kilogram C/kg
 - SensorQuantity_rad_abs_dose: Absorbed dose 	gray 	Gy
 - SensorQuantity_rad_eq_dose: Dose equivalent 	sievert 	Sv
 - SensorQuantity_mass_mass_frac: Mass fraction kg/kg
 - SensorQuantity_mass_vol_frac: Mass concentration
 - SensorQuantity_vol_vol_frac: Volume fraction
 - SensorQuantity_rad_flux: Radiant flux W
 - SensorQuantity_lum_flux: Luminous flux 	lumen 	lm
 - SensorQuantity_lum_intensity: Luminous intensity   candela   Cd
 - SensorQuantity_illuminance: Illuminance 	lux 	lx
 - SensorQuantity_power_factor: Power factor
 - SensorQuantity_none: New with Meterlink 2.1. Previous, SI units. But with MeterLink 2.1, this are more basic units.
 - SensorQuantity_no_impl: Not mapped so far.
 - SensorQuantity_last: last
 */
typedef NS_ENUM(NSUInteger, MLSensorQuantity)
{
    SensorQuantity_invalid = 0,
    SensorQuantity_current,
    SensorQuantity_voltage,
    SensorQuantity_resistance,
    SensorQuantity_reactance,
    SensorQuantity_impedance,
    SensorQuantity_conductance,
    SensorQuantity_capacitance,
    SensorQuantity_inductance,
    SensorQuantity_el_field,
    SensorQuantity_mag_field,
    SensorQuantity_mag_flux,
    SensorQuantity_charge,
    SensorQuantity_temperature,
    SensorQuantity_free_slot,
    SensorQuantity_time,
    SensorQuantity_frequency,
    SensorQuantity_mass,
    SensorQuantity_force,
    SensorQuantity_pressure,
    SensorQuantity_momentum,
    SensorQuantity_torque,
    SensorQuantity_speed,
    SensorQuantity_acceleration,
    SensorQuantity_energy,
    SensorQuantity_power,
    SensorQuantity_enthalpy,
    SensorQuantity_relhumidity,
    SensorQuantity_abshumidity,
    SensorQuantity_relmoisture,
    SensorQuantity_distance,
    SensorQuantity_angle,
    SensorQuantity_radioactivity,
    SensorQuantity_rad_exposure,
    SensorQuantity_rad_abs_dose,
    SensorQuantity_rad_eq_dose,
    SensorQuantity_mass_mass_frac,
    SensorQuantity_mass_vol_frac,
    SensorQuantity_vol_vol_frac,
    SensorQuantity_rad_flux,
    SensorQuantity_lum_flux,
    SensorQuantity_lum_intensity,
    SensorQuantity_illuminance,
    SensorQuantity_power_factor,
    SensorQuantity_none,
    SensorQuantity_no_impl,
    SensorQuantity_admittance,
    SensorQuantity_rpm,
    SensorQuantity_dutycycle,
    SensorQuantity_test_volt,
    SensorQuantity_polar_index,
    SensorQuantity_dar,
    SensorQuantity_ebond_test_volt,
    SensorQuantity_ebond_test,
    SensorQuantity_20ma,
    SensorQuantity_phase,
    SensorQuantity_harmonic_lev,
    SensorQuantity_harmonic_thd,
    SensorQuantity_crest,
    SensorQuantity_quality_fact,
    SensorQuantity_dissipation,
    SensorQuantity_esr,
    SensorQuantity_ac_tester_load,
    SensorQuantity_ac_tester_unload_volt,
    SensorQuantity_ac_tester_load_volt,
    SensorQuantity_ac_tester_drop,
    SensorQuantity_receptacle_test,
    SensorQuantity_afci,
    SensorQuantity_gfci,
    SensorQuantity_gfci_trip_curr,
    SensorQuantity_gfci_trip_time,
    SensorQuantity_edp_trip_curr,
    SensorQuantity_neutral_ground,
    SensorQuantity_hot_imp,
    SensorQuantity_neutral_imp,
    SensorQuantity_ground_imp,
    SensorQuantity_short_circ_current,
    SensorQuantity_emissivity,
    SensorQuantity_resmoisture,
    SensorQuantity_group,
    SensorQuantity_capmoisture,
    SensorQuantity_soilmoisture,
    SensorQuantity_area,
    SensorQuantity_volume,
    SensorQuantity_emf_freq,
    SensorQuantity_emf_strength,
    SensorQuantity_weight,
    SensorQuantity_irradiance,
    SensorQuantity_compbearing,
    SensorQuantity_pitch,
    SensorQuantity_latitude,
    SensorQuantity_longitude,
    SensorQuantity_altitude,
    SensorQuantity_last
};


/**
 Auxillary information

 - SensorAuxInfo_invalid: Unknown or invalid
 - SensorAuxInfo_dc: Voltage/current is DC
 - SensorAuxInfo_ac: Voltage/current is AC
 - SensorAuxInfo_internal: Measurement is internal
 - SensorAuxInfo_external: Measurement is external
 - SensorAuxInfo_min_depr: (Deprecated) This was actually peak min, moved to SensorMeasureInfo_peakmin
 - SensorAuxInfo_max_depr: (Deprecated) This was actually peak max, moved to SensorMeasureInfo_peakmax
 - SensorAuxInfo_dew: Temperature is dew point temp.
 - SensorAuxInfo_air: Temperature is air temp.
 - SensorAuxInfo_ir: Temperature is IR reflected temp.
 - SensorAuxInfo_k: Temperature is thermocouple type K temp.
 - SensorAuxInfo_cond: Temperature is condensation temp.
 - SensorAuxInfo_db: Temperature is dry bulb temp.
 - SensorAuxInfo_wb: Temperature is wet bulb temp.
 - SensorAuxInfo_wme: Mass fraction is wood moisture equivalent
 - SensorAuxInfo_ac_dc: Voltage/current is AC+DC (CM83)
 - SensorAuxInfo_vfd: LowPassFilter is applied before measument, intended for VariableFrequencyDrives (VFD)(CM83)
 - SensorAuxInfo_thd: THD Total Harmonic Mode (CM83)
 - SensorAuxInfo_pf: Power Factor (CM83)
 - SensorAuxInfo_last: last enum
 */
typedef NS_ENUM(NSUInteger, MLSensorAuxInfo)
{
    SensorAuxInfo_invalid = 0,  /** Unknown or invalid */
    SensorAuxInfo_dc,           /** Voltage/current is DC */
    SensorAuxInfo_ac,           /** Voltage/current is AC */
    SensorAuxInfo_internal,     /** Measurement is internal */
    SensorAuxInfo_external,     /** Measurement is external */
    SensorAuxInfo_min_depr,     /** (Deprecated) This was actually peak min, moved to SensorMeasureInfo_peakmin */
    SensorAuxInfo_max_depr,     /** (Deprecated) This was actually peak max, moved to SensorMeasureInfo_peakmax */
    SensorAuxInfo_dew,          /** Temperature is dew point temp. */
    SensorAuxInfo_air,          /** Temperature is air temp. */
    SensorAuxInfo_ir,           /** Temperature is IR reflected temp. */
    SensorAuxInfo_k,            /** Temperature is thermocouple type K temp. */
    SensorAuxInfo_cond,         /** Temperature is condensation temp. */
    SensorAuxInfo_db,           /** Temperature is dry bulb temp. */
    SensorAuxInfo_wb,           /** Temperature is wet bulb temp. */
    SensorAuxInfo_wme,          /** Mass fraction is wood moisture equivalent */
    SensorAuxInfo_ac_dc,        /** Voltage/current is AC+DC (CM83) */
    SensorAuxInfo_vfd,          /** LowPassFilter is applied before measument, intended for VariableFrequencyDrives (VFD)(CM83) */
    SensorAuxInfo_thd,          /** THD Total Harmonic Mode (CM83) */
    SensorAuxInfo_pf,           /** Power Factor (CM83) */
    SensorAuxInfo_last          /** last enum */
};

/**
 Information about the value

 - SensorMeasureInfo_invalid: Unknown or invalid
 - SensorMeasureInfo_min: Minimum value
 - SensorMeasureInfo_max: Maximum value
 - SensorMeasureInfo_peakmin: Peak min value
 - SensorMeasureInfo_peakmax: Peak max value
 - SensorMeasureInfo_peaktopeak: Value is peak-to-peak
 - SensorMeasureInfo_mean: Mean value
 - SensorMeasureInfo_rms: Root mean square value
 - SensorMeasureInfo_median: Median value
 - SensorMeasureInfo_alarm: Alarm from measuring device
 - SensorMeasureInfo_hold: Measuring device are in HOLD
 - SensorMeasureInfo_error: Measuring device reports error.
 - SensorMeasureInfo_last: last enum
 */
typedef NS_ENUM(NSUInteger, MLSensorMeasureInfo)
{
    SensorMeasureInfo_invalid = 0,  ///
    SensorMeasureInfo_min,          ///
    SensorMeasureInfo_max,          ///
    SensorMeasureInfo_peakmin,      ///
    SensorMeasureInfo_peakmax,      ///
    SensorMeasureInfo_peaktopeak,   ///
    SensorMeasureInfo_mean,         ///
    SensorMeasureInfo_rms,          ///
    SensorMeasureInfo_median,       ///
    SensorMeasureInfo_alarm,        ///
    SensorMeasureInfo_hold,         ///
    SensorMeasureInfo_error,        ///
    SensorMeasureInfo_last          ///
};


/**
  A FLIRMeterLinkDeviceInfo object with information about the meterLink device
 */
@interface FLIRMeterLinkDeviceInfo : NSObject

/**
 TRUE if the device will turn off automatically after a peroid of inactivity
 */
@property (nonatomic) bool isAutoPowerOff;          //!< Auto power off
/**
 The primary trigger button was pressed
 */
@property (nonatomic) bool isTriggerPressed;        //!< Trigger pressed
/**
 The second trigger button was pressed
 */
@property (nonatomic) bool isSecondTriggerPressed;  //!< Second button (if exists)
/**
 TRUE if battery is low
 */
@property (nonatomic) bool isBatteryLow;            //!< Battery in device is low
/**
 Type of device
 */
@property (nonatomic) MLMeterType meterType;
/**
 The number of active measurement channels
 */
@property (nonatomic) NSUInteger channelCount;
/**
 The meterLink device modelname
 */
@property (nonatomic) NSString * _Nonnull modelName;
/**
 The meterLink device serial number
 */
@property (nonatomic) NSString * _Nonnull serialNumber;

/**
 Convert the MLMeterType enum to a readable string

 @return Returns MLMeterType as a readable string
 */
- (NSString *_Nonnull) MLMeterTypeString;

@end


/**
 Describes the characteristics of the measurement value
 */
@interface FLIRMeterLinkDeviceChannel : NSObject

/**
 Value quantity (SensorQuantity_invalid if unknown)
 */
@property (nonatomic) MLSensorQuantity    quantity;
/**
 Auxillary information (SensorAuxInfo_invalid if unknown)
 */
@property (nonatomic) MLSensorAuxInfo     auxInfo;
/**
 Measure information (SensorMeasureInfo_invalid if unknown)
 */
@property (nonatomic) MLSensorMeasureInfo measureInfo;
/**
 Value in SI units
 */
@property (nonatomic) double value;
/**
 Timestamp of value when it was parsed
 */
@property (nonatomic) NSDate * _Nonnull timeStamp;
/**
 Number of significant numbers in value
 */
@property (nonatomic) NSUInteger precision;
/**
 Value is difference
 */
@property (nonatomic) bool isDifference;
/**
 TRUE if value is valid
 */
@property (nonatomic) bool isValueValid;
/**
 TRUE if channel is active
 */
@property (nonatomic) bool isActive;
/**
 Raw quantity string (if returned by meter) Only valid if quantity == SensorQuantity_invalid
 */
@property (nonatomic) NSString * _Nonnull rawQuantity;        //!<
/**
 Raw unit string (if returned by meter) Only valid if quantity == SensorQuantity_invalid
 */
@property (nonatomic) NSString * _Nonnull rawUnit;
/**
 Index of value for a multi sensor-reading packet. Only available for protocolVersion>=2
 */
@property (nonatomic, nullable) NSNumber *index;
/**
 -9=nano, -6=u, -3=milli, +3=kilo, etc. Only available for protocolVersion>=2
 */
@property (nonatomic) NSNumber * _Nonnull unitPrefix;


/**
 Convert the MLSensorMeasureInfo enum to a readable string

 @return Returns MLSensorMeasureInfo as a readable string
 */
- (NSString *_Nonnull) MLSensorMeasureInfoString;

/**
 Convert the MLSensorAuxInfo enum to a readable string

 @return Returns MLSensorAuxInfo as a readable string
 */
- (NSString *_Nonnull) MLSensorAuxInfoString;

/**
 Convert the MLSensorQuantity enum to a readable string

 @return Returns MLSensorQuantity as a readable string
 */
- (NSString *_Nonnull) MLSensorQuantityString;

/** @brief Return a formatted string from a Channel's value
 */
- (NSString *_Nonnull) MLSensorValueString;

@end

@class FLIRMeterLinkDeviceChannel;

/**
 Represents a meterLink device
 */
@interface FLIRMeterLinkDevice : NSObject


/**
 The last updated channel
 */
@property (nonatomic) int channel;

/*!
 *  @property identifier
 *
 *  @discussion The unique, persistent identifier associated with the peer.
 */
@property(nonatomic, nullable) NSUUID * identifier NS_AVAILABLE(10_10, 7_0);

/**
 A FLIRMeterLinkDeviceInfo object with information about the meterLink device
 */
@property (nonatomic, nullable) FLIRMeterLinkDeviceInfo *  deviceInfo;

/**
 A list of FLIRMeterLinkDeviceChannel objects representing meterLink values.
 */
@property (nonatomic, nullable) NSMutableArray<FLIRMeterLinkDeviceChannel *> * channels;


@end
