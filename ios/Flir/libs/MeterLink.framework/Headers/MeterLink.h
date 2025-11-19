//
//  MeterLink.h
//  MeterLink
//
//  Copyright © 2019 Teledyne FLIR. All rights reserved.
//
#include <TargetConditionals.h>

#if TARGET_OS_IPHONE
#import <UIKit/UIKit.h>
#else
#import <Cocoa/Cocoa.h>
#endif

//! meterLink framework on iOS only support build/debug for device only, Bluetooth is not supported in simulator

//! Project version number for MeterLink.
FOUNDATION_EXPORT double MeterLinkVersionNumber;

//! Project version string for MeterLink.
FOUNDATION_EXPORT const unsigned char MeterLinkVersionString[];

// In this header, you should import all the public headers of your framework using statements like #import <MeterLink/PublicHeader.h>

#import <MeterLink/FLIRMeterLinkManager.h>
#import <MeterLink/FLIRMeterLinkDevice.h>
