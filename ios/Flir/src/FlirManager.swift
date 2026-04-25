//
//  FlirManager.swift
//  Flir
//
//  Simplified FLIR camera manager - matches sample app pattern
//  scan → connect → stream → disconnect
//

import Foundation
import UIKit

#if FLIR_ENABLED
import ThermalSDK
#endif

/// Device info structure for discovered cameras
@objc public class FlirDeviceInfo: NSObject {
    @objc public let deviceId: String
    @objc public let name: String
    @objc public let communicationType: String
    @objc public let isEmulator: Bool
    
    init(deviceId: String, name: String, communicationType: String, isEmulator: Bool) {
        self.deviceId = deviceId
        self.name = name
        self.communicationType = communicationType
        self.isEmulator = isEmulator
    }
    
    @objc public func toDictionary() -> [String: Any] {
        return [
            "id": deviceId,
            "name": name,
            "communicationType": communicationType,
            "isEmulator": isEmulator
        ]
    }
}

/// Callback protocol for FlirManager events
@objc public protocol FlirManagerDelegate: AnyObject {
    func onDevicesFound(_ devices: [FlirDeviceInfo])
    func onDeviceConnected(_ device: FlirDeviceInfo)
    func onDeviceDisconnected()
    func onFrameReceived(_ image: UIImage, width: Int, height: Int)
    @objc optional func onFrameReceivedRaw(_ data: Data, width: Int, height: Int, bytesPerRow: Int, timestamp: Double)
    func onError(_ message: String)
    func onStateChanged(_ state: String, isConnected: Bool, isStreaming: Bool, isEmulator: Bool)
}

/// Main FLIR Manager - Simplified Singleton
@objc public class FlirManager: NSObject {
    @objc public static let shared = FlirManager()
    
    // MARK: - Singleton
    
    // MARK: - Properties
    @objc public weak var delegate: FlirManagerDelegate?
    
    private var _isConnected = false
    private var _isStreaming = false
    private var _isProcessingFrame = false
    private var connectedDeviceId: String?
    private var connectedDeviceName: String?
    
    // Internal synchronization
    private let stateLock = NSObject()
    
    // Palette and Snapshot state
    private var currentPaletteName: String = "WhiteHot"
    private var pendingSnapshotPath: String?
    
    // Dedicated render queue for frame processing (matches sample app pattern)
    private let renderQueue = DispatchQueue(label: "com.flir.render")
    
    // Latest frame
    private var _latestImage: UIImage?
    @objc public var latestImage: UIImage? { return _latestImage }
    
    // Discovered devices
    private var discoveredDevices: [FlirDeviceInfo] = []
    
#if FLIR_ENABLED
    private var discovery: FLIRDiscovery?
    private var camera: FLIRCamera?
    private var stream: FLIRStream?
    private var streamer: FLIRThermalStreamer?
    private var identityMap: [String: FLIRIdentity] = [:]
#endif
    
    private override init() {
        super.init()
        NSLog("[FlirManager] Initialized")
        
        // Generate palette icons on background thread
        DispatchQueue.global().async {
            self.generatePaletteIcons()
        }
    }
    
    // MARK: - Public State
    
    @objc public var isConnected: Bool { return _isConnected }
    @objc public var isStreaming: Bool { return _isStreaming }
    @objc public var isEmulator: Bool {
        return connectedDeviceName?.lowercased().contains("emulator") == true
    }
    
    @objc public func getDiscoveredDevices() -> [FlirDeviceInfo] {
        return discoveredDevices
    }
    
    // MARK: - Discovery
    
    @objc public func startDiscovery() {
        NSLog("[FlirManager] startDiscovery")
        
#if FLIR_ENABLED
        discoveredDevices.removeAll()
        identityMap.removeAll()
        
        if discovery == nil {
            discovery = FLIRDiscovery()
            discovery?.delegate = self
        }
        
        // Match sample app: discover lightning + wireless + emulator + network
        discovery?.start([.lightning, .flirOneWireless, .emulator, .network])
        
        emitStateChange("discovering")
#else
        delegate?.onError("FLIR SDK not available")
#endif
    }
    
    @objc public func stopDiscovery() {
        objc_sync_enter(stateLock); defer { objc_sync_exit(stateLock) }
        NSLog("[FlirManager] stopDiscovery")
        
#if FLIR_ENABLED
        discovery?.stop()
        emitStateChange("idle")
#endif
    }
    
    // MARK: - Connection
    
    @objc public func connectToDevice(_ deviceId: String) {
        objc_sync_enter(stateLock); defer { objc_sync_exit(stateLock) }
        NSLog("[FlirManager] connectToDevice: \(deviceId)")
        
#if FLIR_ENABLED
        // Find identity
        guard let identity = identityMap[deviceId] else {
            NSLog("[FlirManager] Device not found: \(deviceId)")
            delegate?.onError("Device not found: \(deviceId)")
            return
        }
        
        // Disconnect if already connected
        if _isConnected {
            disconnect()
        }
        
        // Stop discovery before connecting to free up SDK resources
        // This prevents resource contention in the native SDK layer.
        if discovery != nil {
            discovery?.stop()
        }
        
        // Connect on background thread (matches sample app pattern)
        DispatchQueue.global().async { [weak self] in
            guard let self = self else { return }
            
            // Create camera instance
            if self.camera == nil {
                self.camera = FLIRCamera()
                self.camera?.delegate = self
            }
            
            guard let cam = self.camera else {
                self.notifyError("Failed to create camera")
                return
            }
            
            let iface = identity.communicationInterface()
            let camType = identity.cameraType()
            NSLog("[FlirManager] Camera type: \(camType.rawValue), interface: \(iface.rawValue)")
            
            // ── AUTHENTICATE for network cameras ──
            // Official FLIR CameraConnector sample checks .generic camera type,
            // but FLIR One Edge Pro over network may report a different type.
            // Check BOTH: camera type == .generic OR interface contains .network/.flirOneWireless
            let needsAuth = (camType == .generic) || iface.contains(.network) || iface.contains(.flirOneWireless)
            
            if needsAuth {
                NSLog("[FlirManager] Network camera detected — authenticating...")
                
                // Use UUID-based persistent certificate name (matches FLIR sample).
                // The camera has a bug where re-auth with a different name can conflict,
                // so we generate a UUID once and persist it in UserDefaults.
                let certName = self.getPersistentCertificateName()
                NSLog("[FlirManager] Using certificate name: \(certName)")
                
                var status = FLIRAuthenticationStatus.pending
                var attempts = 0
                let maxAttempts = 30 // ~30 seconds timeout
                
                while status == .pending && attempts < maxAttempts {
                    status = cam.authenticate(identity, trustedConnectionName: certName)
                    NSLog("[FlirManager] Auth attempt \(attempts + 1)/\(maxAttempts) status: \(status.rawValue)")
                    
                    if status == .pending {
                        // Camera waiting for user to press "Trust" on its screen
                        Thread.sleep(forTimeInterval: 1.0)
                    }
                    attempts += 1
                }
                
                if status != .approved {
                    NSLog("[FlirManager] Authentication failed/timed out: \(status.rawValue)")
                    self.camera = nil
                    DispatchQueue.main.async {
                        self.emitStateChange("connection_failed")
                        self.delegate?.onError("Camera authentication failed. Check the camera screen for a trust/approve prompt.")
                    }
                    return
                }
                NSLog("[FlirManager] Authentication approved ✅")
            }
            
            // ── PAIR ──
            do {
                try cam.pair(identity, code: 0)
                NSLog("[FlirManager] Pair succeeded")
            } catch {
                NSLog("[FlirManager] Pair failed: \(error)")
                self._isConnected = false
                self.camera = nil
                DispatchQueue.main.async {
                    self.emitStateChange("connection_failed")
                    self.delegate?.onError("Pairing failed: \(error.localizedDescription)")
                }
                return
            }
            
            // ── CONNECT ──
            do {
                try cam.connect()
                
                self._isConnected = true
                self.connectedDeviceId = identity.deviceId()
                self.connectedDeviceName = identity.deviceId()
                
                NSLog("[FlirManager] Connected to: \(identity.deviceId()) ✅")
                
                // Notify on main thread
                let deviceInfo = FlirDeviceInfo(
                    deviceId: identity.deviceId(),
                    name: identity.deviceId(),
                    communicationType: self.interfaceName(Int(identity.communicationInterface().rawValue)),
                    isEmulator: identity.communicationInterface() == .emulator
                )
                
                DispatchQueue.main.async {
                    self.delegate?.onDeviceConnected(deviceInfo)
                    self.emitStateChange("connected")
                }
                
                // Auto-start streaming (matches sample app)
                self.startStreamInternal()
                
            } catch {
                NSLog("[FlirManager] Connect failed: \(error)")
                self._isConnected = false
                self.camera = nil
                DispatchQueue.main.async {
                    self.emitStateChange("connection_failed")
                    self.delegate?.onError("Connection failed: \(error.localizedDescription)")
                }
            }
        }
#else
        delegate?.onError("FLIR SDK not available")
#endif
    }
    
    @objc public func startEmulator() {
        NSLog("[FlirManager] startEmulator")
        startDiscovery()
    }
    
    @objc public func disconnect() {
        objc_sync_enter(stateLock); defer { objc_sync_exit(stateLock) }
        NSLog("[FlirManager] disconnect")
        
#if FLIR_ENABLED
        stopStreamInternalSync()
        camera?.disconnect()
        camera = nil
        _isConnected = false
        connectedDeviceId = nil
        connectedDeviceName = nil
        _latestImage = nil
        
        DispatchQueue.main.async { [weak self] in
            self?.delegate?.onDeviceDisconnected()
            self?.emitStateChange("disconnected")
        }
#endif
    }
    
    @objc public func stop() {
        objc_sync_enter(stateLock); defer { objc_sync_exit(stateLock) }
        stopStreamInternalSync()
        disconnectInternalSync()
        stopDiscoveryInternalSync()
    }
    
    private func stopDiscoveryInternalSync() {
        NSLog("[FlirManager] stopDiscovery")
#if FLIR_ENABLED
        discovery?.stop()
        emitStateChange("idle")
#endif
    }
    
    private func disconnectInternalSync() {
        NSLog("[FlirManager] disconnect")
#if FLIR_ENABLED
        stopStreamInternalSync()
        camera?.disconnect()
        camera = nil
        _isConnected = false
        connectedDeviceId = nil
        connectedDeviceName = nil
        _latestImage = nil
        
        DispatchQueue.main.async { [weak self] in
            self?.delegate?.onDeviceDisconnected()
            self?.emitStateChange("disconnected")
        }
#endif
    }
    
    // MARK: - Streaming
    
    @objc public func startStream() {
#if FLIR_ENABLED
        guard _isConnected else {
            delegate?.onError("Not connected")
            return
        }
        
        DispatchQueue.global().async { [weak self] in
            self?.startStreamInternal()
        }
#endif
    }
    
#if FLIR_ENABLED
    private func startStreamInternal() {
        guard let cam = camera else { return }
        
        let streams = cam.getStreams()
        guard !streams.isEmpty else {
            NSLog("[FlirManager] No streams available")
            return
        }
        
        // Find thermal stream or use first
        let thermalStream = streams.first { $0.isThermal } ?? streams.first!
        
        stream = thermalStream
        streamer = FLIRThermalStreamer(stream: thermalStream)
        streamer?.autoScale = true
        streamer?.renderScale = true
        thermalStream.delegate = self
        
        do {
            try thermalStream.start()
            _isStreaming = true
            NSLog("[FlirManager] Streaming started")
            DispatchQueue.main.async { [weak self] in
                self?.emitStateChange("streaming")
            }
        } catch {
            NSLog("[FlirManager] Stream start failed: \(error)")
            stream = nil
            streamer = nil
            delegate?.onError("Stream failed: \(error.localizedDescription)")
        }
    }
#endif
    
    @objc public func stopStream() {
        objc_sync_enter(stateLock); defer { objc_sync_exit(stateLock) }
        stopStreamInternalSync()
    }

    private func stopStreamInternalSync() {
        NSLog("[FlirManager] stopStream")
        
#if FLIR_ENABLED
        _isStreaming = false
        stream?.stop()
        stream = nil
        streamer = nil
        _latestImage = nil
        
        if _isConnected {
            emitStateChange("connected")
        }
#endif
    }
    
    // MARK: - Temperature
    
    @objc public func getTemperatureAt(x: Int, y: Int) -> Double {
#if FLIR_ENABLED
        guard let streamer = streamer, _isStreaming else { return Double.nan }
        
        var result = Double.nan
        streamer.withThermalImage { thermalImage in
            let w = Double(thermalImage.getWidth())
            let h = Double(thermalImage.getHeight())
            
            // Map incoming integer coordinates (from latestImage) to normalized, then to sensor
            // This assumes x,y are in latestImage coordinate space.
            guard let img = self.latestImage, img.size.width > 0, img.size.height > 0 else { return }
            
            let nx = Double(x) / Double(img.size.width)
            let ny = Double(y) / Double(img.size.height)
            
            let cx = max(0, min(Int(w) - 1, Int(nx * w)))
            let cy = max(0, min(Int(h) - 1, Int(ny * h)))
            
            if let measurements = thermalImage.measurements,
               let spot = try? measurements.addSpot(CGPoint(x: cx, y: cy)) {
                
                // getValue() returns non-optional in some SDK versions, or optional in others.
                // Compiler says it is NOT optional here, so direct assignment.
                let value = spot.getValue()
                result = value.value
                
                try? measurements.remove(spot)
            }
        }
        return result
#else
        return Double.nan
#endif
    }
    
    @objc public func getTemperatureAtNormalized(_ nx: Double, y: Double) -> Double {
#if FLIR_ENABLED
        guard let streamer = streamer, _isStreaming else { return Double.nan }
        
        var result = Double.nan
        streamer.withThermalImage { thermalImage in
            let w = Double(thermalImage.getWidth())
            let h = Double(thermalImage.getHeight())
            
            // Map normalized (0.0 - 1.0) to actual sensor pixels
            let cx = max(0, min(Int(w) - 1, Int(nx * w)))
            let cy_fixed = max(0, min(Int(h) - 1, Int(y * h)))
            
            if let measurements = thermalImage.measurements,
               let spot = try? measurements.addSpot(CGPoint(x: cx, y: cy_fixed)) {
                result = spot.getValue().value
                try? measurements.remove(spot)
            }
        }
        return result
#else
        return Double.nan
#endif
    }
    
    // MARK: - Legacy / Compatibility Methods
    
    @objc public func setPreferSdkRotation(_ prefer: Bool) {
        // No-op in simplified version
    }
    
    @objc public func isPreferSdkRotation() -> Bool {
        return false
    }
    
    @objc public func setNetworkDiscoveryEnabled(_ enabled: Bool) {
        // No-op - simple discovery always scans all supported types
    }
    
    @objc public func startEmulator(withType type: String) {
        NSLog("[FlirManager] startEmulator(withType: \(type))")
        startDiscovery()
    }
    
    @objc public func latestFrameBitmapBase64() -> [String: Any]? {
        // Legacy method for base64 frame data - simplified version uses onFrameReceived
        // If absolutely needed, we could implement jpeg compression here
        return nil
    }
    
    @objc public func getConnectedDeviceInfo() -> String {
        return connectedDeviceName ?? "Not connected"
    }

    // MARK: - Battery (stub - not needed per user)
    
    @objc public func getBatteryLevel() -> Int { return -1 }
    @objc public func isBatteryCharging() -> Bool { return false }
    
    // MARK: - Shim Compatibility
    
    @objc public static var isSDKAvailable: Bool {
        return true
    }
    
    @objc public func setPalette(_ name: String) {
        self.currentPaletteName = name
        NSLog("[FlirManager] Requested palette: \(name)")
    }
    
    @objc public func setPaletteFromAcol(_ acol: Float) {
        let palettes = getAvailablePalettes()
        let idx = Int(acol)
        let maxEff = palettes.count
        if maxEff > 0 {
            let paletteIdx = idx % maxEff
            let safeIdx = paletteIdx < 0 ? paletteIdx + maxEff : paletteIdx
            setPalette(palettes[safeIdx])
        }
    }

    @objc public func getAvailablePalettes() -> [String] {
        return ["WhiteHot", "Iron", "Rainbow", "Arctic", "Lava", "Coldest", "Hottest", "Wheel"]
    }

    @objc public func generatePaletteIcons() -> [[String: String]] {
        let paletteNames = getAvailablePalettes()
        var results: [[String: String]] = []
        for name in paletteNames {
            results.append([
                "name": name,
                "uri": "" // No URI - rely on local assets if any
            ])
        }
        return results
    }

    @objc public func getPalettesWithIcons() -> [[String: String]] {
        return generatePaletteIcons()
    }

    @objc public func captureRadiometricSnapshot(_ path: String) {
        self.pendingSnapshotPath = path
        NSLog("[FlirManager] Pending radiometric snapshot: \(path)")
    }
    
    @objc public func retainClient(_ clientId: String) {
        // Only start discovery if not already connected
        // Starting discovery while connected can interfere with active stream
        if !_isConnected {
            startDiscovery()
        }
    }
    
    @objc public func releaseClient(_ clientId: String) {
        // simplified manager doesn't track retain counts per client yet
        // stopDiscovery() // Optional: could stop if count == 0
    }
    
    // MARK: - Helpers
    
    private func emitStateChange(_ state: String) {
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            self.delegate?.onStateChanged(state, isConnected: self._isConnected, isStreaming: self._isStreaming, isEmulator: self.isEmulator)
        }
    }
    
    private func notifyError(_ message: String) {
        DispatchQueue.main.async { [weak self] in
            self?.delegate?.onError(message)
        }
    }
    
    /// Persistent UUID-based certificate name for camera authentication.
    /// Matches the pattern from FLIR's official CameraConnector sample.
    /// The camera has a bug where re-auth with a different name can conflict,
    /// so we generate a UUID once and persist it in UserDefaults.
    private func getPersistentCertificateName() -> String {
        guard let bundleID = Bundle.main.bundleIdentifier else { return "flir-cert-fallback" }
        let key = "\(bundleID)-flir-cert-name"
        let defaults = UserDefaults.standard
        if let existing = defaults.string(forKey: key) {
            return existing
        }
        let newName = UUID().uuidString
        defaults.set(newName, forKey: key)
        return newName
    }
    
    private func interfaceName(_ iface: Int) -> String {
        // Placeholder for interface name mapping
        return "UNKNOWN"
    }
    @objc public func simulateContextLoss() {
        _latestImage = nil
        DispatchQueue.main.async { [weak self] in
            // Re-emit current state to trigger UI refresh
            self?.emitStateChange(self?._isStreaming == true ? "streaming" : "connected")
        }
    }
}

#if FLIR_ENABLED
// MARK: - Discovery Delegate

extension FlirManager: FLIRDiscoveryEventDelegate {
    public func cameraDiscovered(_ camera: FLIRDiscoveredCamera) {
        let identity = camera.identity
        let deviceId = identity.deviceId()
        
        NSLog("[FlirManager] Device found: \(deviceId)")
        
        // Store identity
        identityMap[deviceId] = identity
        
        // Create device info
        let deviceInfo = FlirDeviceInfo(
            deviceId: deviceId,
            name: camera.displayName ?? deviceId,
            communicationType: interfaceName(Int(identity.communicationInterface().rawValue)),
            isEmulator: identity.communicationInterface() == .emulator
        )
        
        // Add if not exists
        if !discoveredDevices.contains(where: { $0.deviceId == deviceId }) {
            discoveredDevices.append(deviceInfo)
        }
        
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            self.delegate?.onDevicesFound(self.discoveredDevices)
        }
    }
    
    public func discoveryError(_ error: String, netServiceError: Int32, on iface: FLIRCommunicationInterface) {
        NSLog("[FlirManager] Discovery error: \(error)")
        delegate?.onError("Discovery error: \(error)")
    }
    
    public func discoveryFinished(_ iface: FLIRCommunicationInterface) {
        NSLog("[FlirManager] Discovery finished: \(iface.rawValue)")
    }
    
    public func cameraLost(_ cameraIdentity: FLIRIdentity) {
        let deviceId = cameraIdentity.deviceId()
        NSLog("[FlirManager] Device lost: \(deviceId)")
        
        identityMap.removeValue(forKey: deviceId)
        discoveredDevices.removeAll { $0.deviceId == deviceId }
        
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            self.delegate?.onDevicesFound(self.discoveredDevices)
        }
    }
}

// MARK: - Camera Delegate

extension FlirManager: FLIRDataReceivedDelegate {
    public func onDisconnected(_ camera: FLIRCamera, withError error: Error?) {
        NSLog("[FlirManager] Camera disconnected: \(error?.localizedDescription ?? "clean")")
        
        _isConnected = false
        _isStreaming = false
        self.camera = nil
        stream = nil
        streamer = nil
        
        DispatchQueue.main.async { [weak self] in
            self?.delegate?.onDeviceDisconnected()
            self?.emitStateChange("disconnected")
        }
    }
}

// MARK: - Stream Delegate

extension FlirManager: FLIRStreamDelegate {
    public func onError(_ error: Error) {
        NSLog("[FlirManager] Stream error: \(error)")
        delegate?.onError("Stream error: \(error.localizedDescription)")
    }
    
    public func onImageReceived() {
        // Process frame on dedicated render queue (matches sample app pattern)
        // This prevents blocking the SDK callback thread and main thread
        // Guard to skip frame if already processing (prevents backpressure/latency)
        guard !_isProcessingFrame else {
            return
        }
        
        _isProcessingFrame = true
        renderQueue.async { [weak self] in
            defer { self?._isProcessingFrame = false }
            guard let self = self, self._isStreaming, let streamer = self.streamer else {
                return
            }
            
            objc_sync_enter(self.stateLock)
            let currentStreamer = self.streamer
            let streaming = self._isStreaming
            objc_sync_exit(self.stateLock)
            
            guard streaming, let streamer = currentStreamer else {
                return
            }

            let paletteToApply = self.currentPaletteName
            let snapshotPath = self.pendingSnapshotPath
            self.pendingSnapshotPath = nil

            do {
                try streamer.update()
                
                streamer.withThermalImage { thermalImage in
                    // 1. Apply Palette
                    guard let paletteManager = thermalImage.paletteManager,
                          let sdkPalettes = paletteManager.getDefaultPalettes() else { return }
                    var targetPalette: FLIRPalette? = nil
                    
                    if paletteToApply.lowercased() == "gray" || paletteToApply.lowercased() == "grayscale" {
                        // Map Gray to WhiteHot (standard SDK name)
                        targetPalette = sdkPalettes.first(where: { 
                            $0.name.lowercased() == "whitehot" || $0.name.lowercased() == "white hot" 
                        })
                    } else {
                        targetPalette = sdkPalettes.first(where: { $0.name.lowercased() == paletteToApply.lowercased() })
                        
                        // Fallback for Wheel
                        if targetPalette == nil && paletteToApply.lowercased() == "wheel" {
                            targetPalette = sdkPalettes.first(where: {
                                $0.name.contains("Wheel") || $0.name.contains("ColorWheel") || $0.name.contains("Rainbow")
                            })
                        }
                    }
                    
                    if let palette = targetPalette {
                        thermalImage.palette = palette
                    }

                    // 2. Save Radiometric Snapshot if requested
                    if let path = snapshotPath {
                        do {
                            try thermalImage.save(as: path)
                            NSLog("[FlirManager] Radiometric snapshot saved to: \(path)")
                        } catch {
                            NSLog("[FlirManager] Failed to save radiometric snapshot: \(error)")
                        }
                    }

                    // 3. Generate UIImage for display
                    // Grab the image while the thermal image is locked to ensure settings are applied
                    if let image = streamer.getImage() {
                        self._latestImage = image
                        let width = Int(image.size.width)
                        let height = Int(image.size.height)
                        
                        DispatchQueue.main.async { [weak self] in
                            self?.delegate?.onFrameReceived(image, width: width, height: height)
                        }
                    }
                }
            } catch {
                NSLog("[FlirManager] Streamer update failed: \(error)")
                return
            }
        }
    }
}
#endif
