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

export declare const FlirDownload: FlirDownloadAPI;
export declare const FlirModule: any;
