export interface DownloadProgress {
    bytesDownloaded: number;
    totalBytes: number;
    percent: number;
}

export interface FlirDownloadAPI {
    isAvailable(): Promise<boolean>;
    getDownloadSize(): Promise<number>;
    getDownloadSizeFormatted(): Promise<string>;
    download(onProgress?: (progress: DownloadProgress) => void): Promise<void>;
    cancel(): void;
    delete(): Promise<boolean>;
}

export interface SDKStatus {
    available: boolean;
    arch: string;
    dexPath: string;
    nativeLibPath: string;
    dexExists: boolean;
    nativeLibsExist: boolean;
}

export interface FlirModuleAPI {
    getTemperatureFromColor(color: number): Promise<number>;
    getLatestFramePath(): Promise<string | null>;
    getTemperatureAt(x: number, y: number): Promise<number>;
    isEmulator(): Promise<boolean>;
    isDeviceConnected(): Promise<boolean>;
    getConnectedDeviceInfo(): Promise<string>;
    isSDKDownloaded(): Promise<boolean>;
    getSDKStatus(): Promise<SDKStatus>;
}

export declare const FlirDownload: FlirDownloadAPI;
export declare const FlirModule: FlirModuleAPI;
