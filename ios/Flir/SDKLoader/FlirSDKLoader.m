#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>

@interface RCT_EXTERN_MODULE(FlirDownloadManager, RCTEventEmitter)

RCT_EXTERN_METHOD(isFlirAvailable:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)
RCT_EXTERN_METHOD(getDownloadSize:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)
RCT_EXTERN_METHOD(downloadFlirSDK:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)
RCT_EXTERN_METHOD(cancelDownload)
RCT_EXTERN_METHOD(loadFlirFramework:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)
RCT_EXTERN_METHOD(deleteSDK:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)

@end
