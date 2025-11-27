import { NativeModules, NativeEventEmitter, Platform } from 'react-native';

const FlirDownloadNative = NativeModules.FlirDownloadManager;
const FlirEmitter = FlirDownloadNative ? new NativeEventEmitter(FlirDownloadNative) : null;

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

class FlirDownloadManager implements FlirDownloadAPI {
    private subscriptions: any[] = [];

    async isAvailable(): Promise<boolean> {
        if (!FlirDownloadNative) return false;
        return FlirDownloadNative.isFlirAvailable();
    }

    async getDownloadSize(): Promise<number> {
        if (!FlirDownloadNative) return 0;
        return FlirDownloadNative.getDownloadSize();
    }

    async getDownloadSizeFormatted(): Promise<string> {
        const bytes = await this.getDownloadSize();
        const mb = bytes / (1024 * 1024);
        return mb >= 1000 ? `${(mb / 1024).toFixed(1)} GB` : `${mb.toFixed(0)} MB`;
    }

    async download(onProgress?: (progress: DownloadProgress) => void): Promise<void> {
        if (!FlirDownloadNative || !FlirEmitter) {
            throw new Error('FlirDownloadManager not available');
        }

        if (await this.isAvailable()) return;

        return new Promise((resolve, reject) => {
            this.cleanup();

            this.subscriptions.push(
                FlirEmitter!.addListener('FlirDownloadProgress', (e: DownloadProgress) => onProgress?.(e)),
                FlirEmitter!.addListener('FlirDownloadComplete', () => { this.cleanup(); resolve(); }),
                FlirEmitter!.addListener('FlirDownloadError', (e: { error: string }) => { this.cleanup(); reject(new Error(e.error)); })
            );

            FlirDownloadNative.downloadFlirSDK()
                .then(() => { this.cleanup(); resolve(); })
                .catch((e: Error) => { this.cleanup(); reject(e); });
        });
    }

    cancel(): void {
        FlirDownloadNative?.cancelDownload();
        this.cleanup();
    }

    async delete(): Promise<boolean> {
        if (!FlirDownloadNative) return false;
        return FlirDownloadNative.deleteSDK();
    }

    private cleanup(): void {
        this.subscriptions.forEach(s => s.remove());
        this.subscriptions = [];
    }
}

export const FlirDownload = new FlirDownloadManager();
