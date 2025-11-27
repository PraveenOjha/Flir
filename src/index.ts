export { FlirDownload } from './FlirDownload';
export type { DownloadProgress, FlirDownloadAPI } from './FlirDownload';

// Re-export existing FlirModule functionality
// Note: FlirModule should be imported from the native module
import { NativeModules } from 'react-native';
export const FlirModule = NativeModules.FlirModule;
