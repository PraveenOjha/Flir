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
        NSLog("[FlirManager] stopDiscovery")
        
#if FLIR_ENABLED
        discovery?.stop()
        emitStateChange("idle")
#endif
    }
    
    // MARK: - Connection
    
    @objc public func connectToDevice(_ deviceId: String) {
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
        
        // Connect on background thread (matches sample app)
        DispatchQueue.global().async { [weak self] in
            guard let self = self else { return }
            
            do {
                if self.camera == nil {
                    self.camera = FLIRCamera()
                    self.camera?.delegate = self
                }
                
                guard let cam = self.camera else {
                    self.notifyError("Failed to create camera")
                    return
                }
                
                // Authenticate if generic network camera
                if identity.cameraType() == .generic {
                    var status = FLIRAuthenticationStatus.pending
                    let certName = (Bundle.main.bundleIdentifier ?? "ThermalCamera") + "-cert"
                    while status == .pending {
                        status = cam.authenticate(identity, trustedConnectionName: certName)
                        if status == .pending {
                            Thread.sleep(forTimeInterval: 0.2)
                        }
                    }
                }
                
                // Pair and connect (matches sample app pattern)
                try cam.pair(identity, code: 0)
                try cam.connect()
                
                self._isConnected = true
                self.connectedDeviceId = identity.deviceId()
                self.connectedDeviceName = identity.deviceId()
                
                NSLog("[FlirManager] Connected to: \(identity.deviceId())")
                
                // Notify on main thread
                let deviceInfo = FlirDeviceInfo(
                    deviceId: identity.deviceId(),
                    name: identity.deviceId(),
                    communicationType: self.interfaceName(identity.communicationInterface()),
                    isEmulator: identity.communicationInterface() == .emulator
                )
                
                DispatchQueue.main.async {
                    self.delegate?.onDeviceConnected(deviceInfo)
                    self.emitStateChange("connected")
                }
                
                // Auto-start streaming (matches sample app)
                self.startStreamInternal()
                
            } catch {
                NSLog("[FlirManager] Connection failed: \(error)")
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
        NSLog("[FlirManager] disconnect")
        
#if FLIR_ENABLED
        stopStream()
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
        stopStream()
        disconnect()
        stopDiscovery()
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
        NSLog("[FlirManager] stopStream")
        
#if FLIR_ENABLED
        stream?.stop()
        stream = nil
        streamer = nil
        _isStreaming = false
        _latestImage = nil
        
        if _isConnected {
            emitStateChange("connected")
        }
#endif
    }
    
    // MARK: - Temperature
    
    @objc public func getTemperatureAt(x: Int, y: Int) -> Double {
#if FLIR_ENABLED
        guard let streamer = streamer else { return Double.nan }
        
        var result = Double.nan
        streamer.withThermalImage { thermalImage in
            let w = thermalImage.getWidth()
            let h = thermalImage.getHeight()
            let cx = max(0, min(Int(w) - 1, x))
            let cy = max(0, min(Int(h) - 1, y))
            
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
        guard let img = latestImage else { return Double.nan }
        let px = Int(nx * Double(img.size.width))
        let py = Int(y * Double(img.size.height))
        return getTemperatureAt(x: px, y: py)
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
    
    // MARK: - Battery (stub - not needed per user)
    
    @objc public func getBatteryLevel() -> Int { return -1 }
    @objc public func isBatteryCharging() -> Bool { return false }
    
    // MARK: - Shim Compatibility
    
    @objc public static var isSDKAvailable: Bool {
        return true
    }
    
    @objc public func setPalette(_ name: String) {
        // stub
    }
    
    @objc public func setPaletteFromAcol(_ acol: Float) {
        // stub
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
    
#if FLIR_ENABLED
    private func interfaceName(_ iface: FLIRCommunicationInterface) -> String {
        if iface.contains(.lightning) { return "LIGHTNING" }
        if iface.contains(.network) { return "NETWORK" }
        if iface.contains(.flirOneWireless) { return "WIRELESS" }
        if iface.contains(.emulator) { return "EMULATOR" }
        return "UNKNOWN"
    }
#endif
}

// MARK: - Discovery Delegate

#if FLIR_ENABLED
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
            communicationType: interfaceName(identity.communicationInterface()),
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
        NSLog("[FlirManager] Discovery finished: \(iface)")
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
#endif

// MARK: - Camera Delegate

#if FLIR_ENABLED
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
#endif

// MARK: - Stream Delegate

#if FLIR_ENABLED
extension FlirManager: FLIRStreamDelegate {
    public func onError(_ error: Error) {
        NSLog("[FlirManager] Stream error: \(error)")
        delegate?.onError("Stream error: \(error.localizedDescription)")
    }
    
    public func onImageReceived() {
        NSLog("[FLIR-TRACE 1️⃣] onImageReceived called on SDK thread")
        
        // Process frame on dedicated render queue (matches sample app pattern)
        // This prevents blocking the SDK callback thread and main thread
        // Guard to skip frame if already processing (prevents backpressure/latency)
        guard !_isProcessingFrame else {
            NSLog("[FLIR-TRACE ⏩] Skipping frame (already processing)")
            return
        }
        
        _isProcessingFrame = true
        renderQueue.async { [weak self] in
            defer { self?._isProcessingFrame = false }
            guard let self = self, let streamer = self.streamer else {
                NSLog("[FLIR-TRACE ❌] No self or streamer in renderQueue")
                return
            }
            
            NSLog("[FLIR-TRACE 2️⃣] Processing on renderQueue")
            
            do {
                try streamer.update()
                NSLog("[FLIR-TRACE 3️⃣] Streamer updated successfully")
            } catch {
                NSLog("[FLIR-TRACE ❌] Streamer update failed: \(error)")
                return
            }
            
            guard let image = streamer.getImage() else {
                NSLog("[FLIR-TRACE ❌] streamer.getImage() returned nil")
                return
            }
            
            NSLog("[FLIR-TRACE 4️⃣] Got image from streamer: \(image.size.width)x\(image.size.height)")
            
            self._latestImage = image
            let width = Int(image.size.width)
            let height = Int(image.size.height)
            
            DispatchQueue.main.async { [weak self] in
                NSLog("[FLIR-TRACE 5️⃣] Dispatching to delegate.onFrameReceived on main thread")
                self?.delegate?.onFrameReceived(image, width: width, height: height)
            }
        }
    }
}
#endif
