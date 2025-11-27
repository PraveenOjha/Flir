export { FlirDownload } from './FlirDownload';

// Re-export existing FlirModule functionality
// Note: FlirModule should be imported from the native module
import { NativeModules } from 'react-native';
export const FlirModule = NativeModules.FlirModule;

