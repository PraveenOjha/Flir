//
//  FLIRBattery.h
//  ThermalSDK
//
//  Created by FLIR on 2020-07-02.
//  Copyright © 2020 Teledyne FLIR. All rights reserved.
//

/**
 ChargingState
 */
typedef NS_ENUM(NSUInteger, FLIRChargingState)
{
    /** Indicates that the battery is not charging because the camera is in "developer mode". */
    DEVELOPER,
    /**  Indicates that the battery is not charging because the camera is not connected to an external power supply. */
    NOCHARGING,
    /**  Indicates that the battery is charging from external power. */
    MANAGEDCHARGING,

    /** Indicates that the battery is charging the iPhone.

      This field indicates that IF there is an iPhone plugged in, it will
      be receiving power from the RBPDevice battery. However, it is still possible
      for the RBPDevice to be in this "mode" EVEN IF THERE IS NO IPHONE ATTACHED.
     */
    CHARGINGSMARTPHONE,

    /**  Indicates that a charging fault occurred (overheat, etc.) */
    FAULT,
    /**  Indicates that a charging heat fault occured */
    FAULTHEAT,
    /**  Indicates that a charging fault occured due to low current from the charging source */
    FAULTBADCHARGER,
    /**  Indicates that a charging fault exists but the iPhone is being charged */
    CHARGINGSMARTPHONEFAULTHEAT,
    /**  Indicates that the device is in charge-only mode */
    MANAGEDCHARGINGONLY,
    /**  Indicates that the device is in phone-charging-only mode */
    CHARGINGSMARTPHONEONLY,
    /**  Indicates that a valid battery charging state was not available. */
    BAD
};

/**
* Camera battery status monitoring interface
*/
@interface FLIRBattery : NSObject
/** Get charging state.
 *
 * In certain cases (e.g. in case of FLIR ONE prior to starting streaming) the battery charging state value may not be available.
 * When the value is not available a "ChargingState.BAD" value is returned.
 * In the same time the "percentage" can return negative value indicating battery percentage is also not available.
 *
 * See Battery::percentage
 */
- (FLIRChargingState) getChargingState;
/** Remaining battery percentage.
 *
 * Valid value range from 0 to 100 where 0 is empty and 100 is full.
 * In certain cases (e.g. in case of FLIR ONE prior to starting streaming) the battery percentage value may not be available.
 * When the value is not available, an invalid (negative) value is returned.
 * In the same time the "chargingState" can return "ChargingState.BAD" indicating state is also not available.
 *
 * See Battery::chargingState
 */
- (int)getPercentage;
/** Subscribes for battery charging state notifications. */
- (BOOL)subscribeChargingState: (out NSError * _Nullable *_Nullable) error;
/** Revokes subscription for battery charging state. */
- (void)unsubscribeChargingState;
/** Subscribes for remaining battery power notifications. */
- (BOOL)subscribePercentage: (out NSError * _Nullable *_Nullable) error;
/** Revokes subscription for remaining battery power. */
- (void)unsubscribePercentage;
@end
