// FlirDownload API removed: the SDK is bundled at compile time and runtime downloading is not supported.

export interface SDKStatus {
    available: boolean;
    arch: string;
    dexPath: string;
    nativeLibPath: string;
    dexExists: boolean;
    nativeLibsExist: boolean;
}

export interface SDKInitResult {
    initialized: boolean;
    message?: string;
    error?: string;
    errorType?: string;
}

export interface FlirDebugInfo {
    sdkAvailable: boolean;
    arch: string;
    sdkClassesLoaded: boolean;
    discoveredDeviceCount: number;
    isConnected: boolean;
    isStreaming: boolean;
    connectedDevice: string;
}

export interface FlirDevice {
    id: string;
    name: string;
    communicationType: 'USB' | 'NETWORK' | 'EMULATOR';
    isEmulator: boolean;
}

export interface FlirModuleAPI {
    // Temperature APIs
    getTemperatureFromColor(color: number): Promise<number>;
    getLatestFramePath(): Promise<string | null>;
    getTemperatureAt(x: number, y: number): Promise<number>;
    
    // Status APIs
    isEmulator(): Promise<boolean>;
    isDeviceConnected(): Promise<boolean>;
    getConnectedDeviceInfo(): Promise<string>;
    isSDKDownloaded(): Promise<boolean>;
    getSDKStatus(): Promise<SDKStatus>;
    
    // Discovery & Connection APIs
    startDiscovery(): Promise<boolean>;
    stopDiscovery(): Promise<boolean>;
    startEmulator(emulatorType: string): Promise<boolean>;
    connectToDevice(deviceId: string): Promise<boolean>;
    stopFlir(): Promise<boolean>;
    getDiscoveredDevices(): Promise<FlirDevice[]>;
    
    // Debug APIs
    initializeSDK(): Promise<SDKInitResult>;
    getDebugInfo(): Promise<FlirDebugInfo>;
}

// FlirDownload removed. Use `FlirModule` APIs instead.
export declare const FlirModule: FlirModuleAPI;
