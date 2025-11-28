//
//  FLIRSystem.h
//  ThermalSDK
//
//  Created by FLIR on 2020-07-02.
//  Copyright © 2020 Teledyne FLIR. All rights reserved.
//

#import <Foundation/Foundation.h>

/** system parameters for the camera */
@interface FLIRSystem : NSObject

/** get the a flag indicating the system is running */
- (BOOL) getSystemUp;
/** get the date and time for the camera */
- (NSDateComponents* _Nullable)getTime;
/** get the time zone name for the camera */
- (NSString* _Nullable)getTimeZoneName;
/// Set the date and time for the camera.
/// - Parameter dateComponents: Required components: year, month, day, hour, minute, second and calendar.
///
/// The given components are used to set the camera's date and time.
/// > Note: Timezone is ignored; it's set by ``setTimeZoneName:error:``.  Some cameras set seconds to 0.
- (BOOL)setTime:(NSDateComponents* _Nonnull)dateComponents error:(out NSError * _Nullable *_Nullable)error;
/// Set the time zone name for the camera.
/// > Note: not all cameras support this.
- (BOOL)setTimeZoneName:(NSString* _Nonnull)timeZoneName error:(out NSError * _Nullable *_Nullable)error;
/// Get a list of available time zones
- (NSArray<NSString *>* _Nullable)getAvailableTimeZones:(out NSError * _Nullable *_Nullable)error;

- (BOOL)setWiFiSSID:(NSString * _Nonnull)ssid;

@property (nonatomic, readonly) NSUInteger capacityKBytes;
@property (nonatomic, readonly) NSUInteger freeKBytes;
@property (nonatomic) BOOL isTorchOn;

- (void) reboot;

@end
