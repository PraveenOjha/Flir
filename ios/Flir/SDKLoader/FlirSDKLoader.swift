import Foundation
import CommonCrypto

@objc(FlirSDKLoader)
public class FlirSDKLoader: RCTEventEmitter {
    
    private var downloadTask: URLSessionDownloadTask?
    private var progressObservation: NSKeyValueObservation?
    
    // MARK: - SDK Paths
    
    private static var sdkDirectory: URL {
        FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
            .appendingPathComponent("FlirSDK", isDirectory: true)
    }
    
    private static var thermalSDKPath: URL {
        sdkDirectory.appendingPathComponent("ThermalSDK.framework")
    }
    
    // MARK: - RCTEventEmitter
    
    override public static func moduleName() -> String! { "FlirDownloadManager" }
    override public static func requiresMainQueueSetup() -> Bool { false }
    override public func supportedEvents() -> [String]! {
        ["FlirDownloadProgress", "FlirDownloadComplete", "FlirDownloadError"]
    }
    
    // MARK: - Public API
    
    @objc func isFlirAvailable(_ resolve: @escaping RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {
        resolve(FileManager.default.fileExists(atPath: Self.thermalSDKPath.path))
    }
    
    @objc func getDownloadSize(_ resolve: @escaping RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {
        guard let manifest = loadManifest() else {
            resolve(104_857_600)
            return
        }
        resolve(manifest.ios.sizeBytes)
    }
    
    @objc func downloadFlirSDK(_ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {
        guard let manifest = loadManifest() else {
            reject("E_MANIFEST", "Failed to load SDK manifest", nil)
            return
        }
        
        guard let url = URL(string: manifest.ios.downloadUrl) else {
            reject("E_URL", "Invalid download URL", nil)
            return
        }
        
        let session = URLSession(configuration: .default)
        downloadTask = session.downloadTask(with: url) { [weak self] tempURL, response, error in
            guard let self = self else { return }
            
            if let error = error {
                self.sendEvent(withName: "FlirDownloadError", body: ["error": error.localizedDescription])
                reject("E_DOWNLOAD", error.localizedDescription, error)
                return
            }
            
            guard let tempURL = tempURL else {
                self.sendEvent(withName: "FlirDownloadError", body: ["error": "No data received"])
                reject("E_NODATA", "No data received", nil)
                return
            }
            
            do {
                // Verify checksum
                let data = try Data(contentsOf: tempURL)
                let hash = self.sha256(data)
                guard hash == manifest.ios.sha256 else {
                    throw NSError(domain: "FlirSDK", code: -1, 
                        userInfo: [NSLocalizedDescriptionKey: "Checksum verification failed"])
                }
                
                // Create SDK directory
                try FileManager.default.createDirectory(at: Self.sdkDirectory, 
                    withIntermediateDirectories: true)
                
                // Unzip
                try self.unzip(tempURL, to: Self.sdkDirectory)
                
                self.sendEvent(withName: "FlirDownloadComplete", body: [:])
                resolve(true)
            } catch {
                self.sendEvent(withName: "FlirDownloadError", body: ["error": error.localizedDescription])
                reject("E_INSTALL", error.localizedDescription, error)
            }
        }
        
        // Observe progress
        progressObservation = downloadTask?.progress.observe(\.fractionCompleted) { [weak self] progress, _ in
            let totalBytes = Int64(104_857_600)
            let downloaded = Int64(Double(totalBytes) * progress.fractionCompleted)
            self?.sendEvent(withName: "FlirDownloadProgress", body: [
                "bytesDownloaded": downloaded,
                "totalBytes": totalBytes,
                "percent": progress.fractionCompleted * 100
            ])
        }
        
        downloadTask?.resume()
    }
    
    @objc func cancelDownload() {
        downloadTask?.cancel()
        progressObservation?.invalidate()
    }
    
    @objc func loadFlirFramework(_ resolve: @escaping RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {
        let binaryPath = Self.thermalSDKPath.appendingPathComponent("ThermalSDK").path
        
        guard let handle = dlopen(binaryPath, RTLD_NOW | RTLD_GLOBAL) else {
            let error = String(cString: dlerror())
            reject("E_LOAD", "Failed to load SDK: \(error)", nil)
            return
        }
        
        resolve(true)
    }
    
    @objc func deleteSDK(_ resolve: @escaping RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {
        do {
            try FileManager.default.removeItem(at: Self.sdkDirectory)
            resolve(true)
        } catch {
            reject("E_DELETE", error.localizedDescription, error)
        }
    }
    
    // MARK: - Helpers
    
    private func loadManifest() -> SDKManifest? {
        guard let bundle = Bundle(identifier: "org.cocoapods.Flir"),
              let url = bundle.url(forResource: "sdk-manifest", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let manifest = try? JSONDecoder().decode(SDKManifest.self, from: data)
        else { return nil }
        return manifest
    }
    
    private func sha256(_ data: Data) -> String {
        var hash = [UInt8](repeating: 0, count: Int(CC_SHA256_DIGEST_LENGTH))
        data.withUnsafeBytes { _ = CC_SHA256($0.baseAddress, CC_LONG(data.count), &hash) }
        return hash.map { String(format: "%02x", $0) }.joined()
    }
    
    private func unzip(_ source: URL, to destination: URL) throws {
        let process = Process()
        process.executableURL = URL(fileURLWithPath: "/usr/bin/unzip")
        process.arguments = ["-o", "-q", source.path, "-d", destination.path]
        try process.run()
        process.waitUntilExit()
        guard process.terminationStatus == 0 else {
            throw NSError(domain: "FlirSDK", code: Int(process.terminationStatus),
                userInfo: [NSLocalizedDescriptionKey: "Unzip failed"])
        }
    }
}

// MARK: - Models

private struct SDKManifest: Codable {
    let version: String
    let ios: IOSManifest
    
    struct IOSManifest: Codable {
        let downloadUrl: String
        let sha256: String
        let sizeBytes: Int64
    }
}
