import { NativeModules, requireNativeComponent, Platform } from 'react-native';

export const FlirModule = NativeModules.FlirModule;

/**
 * ThermalPreview Component
 * 
 * A high-performance native component for rendering the live thermal stream.
 * 
 * Props:
 * - style: Standard React Native view styles (required: set width/height)
 */
export const ThermalPreview = Platform.select({
  ios: requireNativeComponent('FlirPreviewView'),
  android: requireNativeComponent('FLIRCameraView'),
});

export default {
  FlirModule,
  ThermalPreview,
};
