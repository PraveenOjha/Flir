//
//  ThermalSDK.h
//  FLIR Thermal SDK
//
//  Copyright © 2019-2024 Teledyne FLIR. All rights reserved.
//

#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>

//! Project version number for ThermalSDK.
FOUNDATION_EXPORT const double ThermalSDKVersionNumber;

//! Project version string for ThermalSDK.
FOUNDATION_EXPORT const char *ThermalSDKVersionString;

//! Project commit hash string for ThermalSDK.
FOUNDATION_EXPORT const char *ThermalSDKCommitHash;


// In this header, you should import all the public headers of your framework using statements like #import <ThermalSDK/PublicHeader.h>

#import <ThermalSDK/FLIRCamera.h>
#import <ThermalSDK/FLIRCameraDeviceInfo.h>
#import <ThermalSDK/FLIRDiscovery.h>
#import <ThermalSDK/FLIRCameraImport.h>
#import <ThermalSDK/FLIRThermalImage.h>
#import <ThermalSDK/FLIRImageBase.h>
#import <ThermalSDK/FLIRThermalImageFile.h>
#import <ThermalSDK/FLIRScale.h>
#import <ThermalSDK/FLIRFusion.h>
#import <ThermalSDK/FLIRImageStatistics.h>
#import <ThermalSDK/FLIRMeasurementSpot.h>
#import <ThermalSDK/FLIRMeasurementShape.h>
#import <ThermalSDK/FLIRMeasurementArea.h>
#import <ThermalSDK/FLIRMeasurementMarker.h>
#import <ThermalSDK/FLIRMeasurementParameters.h>
#import <ThermalSDK/FLIRThermalValue.h>
#import <ThermalSDK/FLIRThermalDelta.h>
#import <ThermalSDK/FLIRMeasurementCollection.h>
#import <ThermalSDK/FLIRMeasurementRectangle.h>
#import <ThermalSDK/FLIRMeasurementEllipse.h>
#import <ThermalSDK/FLIRMeasurementDimensions.h>
#import <ThermalSDK/FLIRMeasurementLine.h>
#import <ThermalSDK/FLIRMeasurementDelta.h>
#import <ThermalSDK/FLIRPalette.h>
#import <ThermalSDK/FLIRPaletteManager.h>
#import <ThermalSDK/FLIRCameraEvent.h>
#import <ThermalSDK/FLIRDiscoveredCamera.h>
#import <ThermalSDK/FLIRIdentity.h>
#import <ThermalSDK/FLIRRemoteControl.h>
#import <ThermalSDK/FLIRMeasurementsController.h>
#import <ThermalSDK/FLIRPaletteController.h>
#import <ThermalSDK/FLIRSystem.h>
#import <ThermalSDK/FLIRFocus.h>
#import <ThermalSDK/FLIRTemperatureRange.h>
#import <ThermalSDK/FLIRFusionController.h>
#import <ThermalSDK/FLIRCalibration.h>
#import <ThermalSDK/FLIRBattery.h>
#import <ThermalSDK/FLIRScaleController.h>
#import <ThermalSDK/FLIROverlayController.h>
#import <ThermalSDK/FLIRDisplaySettings.h>
#import <ThermalSDK/FLIRMeterLinkSensorPoll.h>
#import <ThermalSDK/FLIRColorDistributionSettings.h>
#import <ThermalSDK/FLIRStream.h>
#import <ThermalSDK/FLIRStreamer.h>
#import <ThermalSDK/FLIRRenderer.h>
#import <ThermalSDK/FLIRColorizer.h>
#import <ThermalSDK/FLIRImageColorizer.h>
#import <ThermalSDK/FLIRRendererImpl.h>
#import <ThermalSDK/FLIRWirelessCameraDetails.h>
#import <ThermalSDK/FLIRQuantification.h>

