// FlirDownload removed: SDK is now bundled at compile time; runtime downloads are no longer supported.

// Re-export existing FlirModule functionality
// Note: FlirModule should be imported from the native module
import { NativeModules } from 'react-native';
export const FlirModule = NativeModules.FlirModule;
