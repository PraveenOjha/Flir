//
//  FlirManager.swift
//  Flir
//
//  Core FLIR camera manager for iOS - handles discovery, connection, and streaming
//  Mirrors the Android FlirManager.kt functionality
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
    func onError(_ message: String)
    func onStateChanged(_ state: String, isConnected: Bool, isStreaming: Bool, isEmulator: Bool)
}

/// Main FLIR Manager - Singleton that manages all FLIR camera operations
@objc public class FlirManager: NSObject {
    @objc public static let shared = FlirManager()
    
    // MARK: - Properties
    @objc public weak var delegate: FlirManagerDelegate?
    
    private var isInitialized = false
    private var isScanning = false
    private var _isConnected = false
    private var _isStreaming = false
    private var connectedDeviceId: String?
    private var connectedDeviceName: String?
    
    // Latest frame for texture updates
    private var _latestImage: UIImage?
    @objc public var latestImage: UIImage? { return _latestImage }
    
    // Temperature data
    private var lastTemperature: Double = Double.nan
    
    // Discovered devices
    private var discoveredDevices: [FlirDeviceInfo] = []
    
#if FLIR_ENABLED
    private var discovery: FLIRDiscovery?
    private var camera: FLIRCamera?
    private var stream: FLIRStream?
    private var streamer: FLIRThermalStreamer?
    private var connectedIdentity: FLIRIdentity?
#endif
    
    private override init() {
        super.init()
        NSLog("[FlirManager] Initialized")
    }
    
    // MARK: - Public State Accessors
    
    @objc public var isConnected: Bool { return _isConnected }
    @objc public var isStreaming: Bool { return _isStreaming }
    @objc public var isEmulator: Bool {
        return connectedDeviceName?.lowercased().contains("emulator") == true ||
               connectedDeviceName?.lowercased().contains("emulat") == true
    }
    
    @objc public func getConnectedDeviceInfo() -> String {
        return connectedDeviceName ?? "Not connected"
    }
    
    @objc public func getDiscoveredDevices() -> [FlirDeviceInfo] {
        return discoveredDevices
    }
    
    // MARK: - SDK Availability
    
    @objc public static var isSDKAvailable: Bool {
#if FLIR_ENABLED
        return true
#else
        return false
#endif
    }
    
    // MARK: - Discovery
    
    @objc public func startDiscovery() {
        NSLog("[FlirManager] Starting discovery...")
        
#if FLIR_ENABLED
        if isScanning {
            NSLog("[FlirManager] Already scanning")
            return
        }
        
        isScanning = true
        discoveredDevices.removeAll()
        
        if discovery == nil {
            discovery = FLIRDiscovery()
            discovery?.delegate = self
        }
        
        // Start discovery on all available interfaces
        let interfaces: FLIRCommunicationInterface = [
            .lightning,
            .network,
            .flirOneWireless,
            .emulator
        ]
        discovery?.start(interfaces)
        
        emitStateChange("discovering")
        NSLog("[FlirManager] Discovery started on interfaces: Lightning, Network, FlirOneWireless, Emulator")
#else
        NSLog("[FlirManager] FLIR SDK not available - discovery disabled")
        delegate?.onError("FLIR SDK not available")
#endif
    }
    
    @objc public func stopDiscovery() {
        NSLog("[FlirManager] Stopping discovery...")
        
#if FLIR_ENABLED
        discovery?.stop()
        isScanning = false
        NSLog("[FlirManager] Discovery stopped")
#endif
    }
    
    // MARK: - Connection
    
    @objc public func connectToDevice(_ deviceId: String) {
        NSLog("[FlirManager] Connecting to device: \(deviceId)")
        
#if FLIR_ENABLED
        // Find the identity for this device
        guard let identity = findIdentity(for: deviceId) else {
            NSLog("[FlirManager] Device not found: \(deviceId)")
            delegate?.onError("Device not found: \(deviceId)")
            return
        }
        
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            self?.performConnection(identity: identity)
        }
#else
        delegate?.onError("FLIR SDK not available")
#endif
    }
    
#if FLIR_ENABLED
    private var identityMap: [String: FLIRIdentity] = [:]
    
    private func findIdentity(for deviceId: String) -> FLIRIdentity? {
        return identityMap[deviceId]
    }
    
    private func performConnection(identity: FLIRIdentity) {
        do {
            if camera == nil {
                camera = FLIRCamera()
                camera?.delegate = self
            }
            
            // Handle authentication for generic cameras
            if identity.cameraType() == .generic {
                let certName = getCertificateName()
                var status = FLIRAuthenticationStatus.pending
                while status == .pending {
                    status = camera!.authenticate(identity, trustedConnectionName: certName)
                    if status == .pending {
                        NSLog("[FlirManager] Waiting for camera authentication approval...")
                        Thread.sleep(forTimeInterval: 1.0)
                    }
                }
            }
            
            // Connect
            try camera?.connect(identity)
            
            connectedIdentity = identity
            connectedDeviceId = identity.deviceId()
            connectedDeviceName = identity.deviceId()
            _isConnected = true
            
            NSLog("[FlirManager] Connected to: \(identity.deviceId())")
            
            // Get streams
            if let streams = camera?.getStreams(), !streams.isEmpty {
                NSLog("[FlirManager] Found \(streams.count) streams")
                
                // Auto-start first thermal stream
                if let firstStream = streams.first {
                    startStreamInternal(firstStream)
                }
            }
            
            DispatchQueue.main.async { [weak self] in
                guard let self = self else { return }
                let deviceInfo = FlirDeviceInfo(
                    deviceId: identity.deviceId(),
                    name: identity.deviceId(),
                    communicationType: self.communicationInterfaceName(identity.communicationInterface()),
                    isEmulator: identity.communicationInterface() == .emulator
                )
                self.delegate?.onDeviceConnected(deviceInfo)
                self.emitStateChange("connected")
            }
            
        } catch {
            NSLog("[FlirManager] Connection failed: \(error)")
            DispatchQueue.main.async { [weak self] in
                self?.delegate?.onError("Connection failed: \(error.localizedDescription)")
            }
        }
    }
    
    private func getCertificateName() -> String {
        let bundleID = Bundle.main.bundleIdentifier ?? "com.flir.app"
        let key = "\(bundleID)-cert-name"
        
        if let existing = UserDefaults.standard.string(forKey: key) {
            return existing
        }
        
        let newName = UUID().uuidString
        UserDefaults.standard.set(newName, forKey: key)
        return newName
    }
    
    private func communicationInterfaceName(_ iface: FLIRCommunicationInterface) -> String {
        if iface.contains(.lightning) { return "LIGHTNING" }
        if iface.contains(.network) { return "NETWORK" }
        if iface.contains(.flirOneWireless) { return "WIRELESS" }
        if iface.contains(.emulator) { return "EMULATOR" }
        if iface.contains(.usb) { return "USB" }
        return "UNKNOWN"
    }
#endif
    
    // MARK: - Streaming
    
    @objc public func startStream() {
#if FLIR_ENABLED
        guard let streams = camera?.getStreams(), !streams.isEmpty else {
            NSLog("[FlirManager] No streams available")
            return
        }
        startStreamInternal(streams[0])
#endif
    }
    
    @objc public func stopStream() {
        NSLog("[FlirManager] Stopping stream...")
        
#if FLIR_ENABLED
        stream?.stop()
        stream = nil
        streamer = nil
        _isStreaming = false
        emitStateChange("connected")
#endif
    }
    
#if FLIR_ENABLED
    private func startStreamInternal(_ newStream: FLIRStream) {
        NSLog("[FlirManager] Starting stream...")
        
        stream?.stop()
        stream = newStream
        
        if newStream.isThermal {
            streamer = FLIRThermalStreamer(stream: newStream)
        }
        
        newStream.delegate = self
        
        do {
            try newStream.start()
            _isStreaming = true
            emitStateChange("streaming")
            NSLog("[FlirManager] Stream started (thermal: \(newStream.isThermal))")
        } catch {
            NSLog("[FlirManager] Stream start failed: \(error)")
            stream = nil
            streamer = nil
            delegate?.onError("Stream start failed: \(error.localizedDescription)")
        }
    }
#endif
    
    // MARK: - Disconnect
    
    @objc public func disconnect() {
        NSLog("[FlirManager] Disconnecting...")
        
#if FLIR_ENABLED
        stopStream()
        camera?.disconnect()
        camera = nil
        connectedIdentity = nil
        connectedDeviceId = nil
        connectedDeviceName = nil
        _isConnected = false
        _isStreaming = false
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
        _latestImage = nil
    }
    
    // MARK: - Temperature
    
    @objc public func getTemperatureAt(x: Int, y: Int) -> Double {
#if FLIR_ENABLED
        // Get temperature from thermal image at point
        if let thermalStreamer = streamer {
            var temp: Double = Double.nan
            thermalStreamer.withThermalImage { thermalImage in
                if let measurements = thermalImage.measurements {
                    // Try to get temperature at point
                    // For now, return the last known temperature
                    temp = self.lastTemperature
                }
            }
            return temp
        }
#endif
        return lastTemperature
    }
    
    @objc public func getLastTemperature() -> Double {
        return lastTemperature
    }
    
    // MARK: - Emulator
    
    @objc public func startEmulator(type: String) {
        NSLog("[FlirManager] Starting emulator: \(type)")
        
#if FLIR_ENABLED
        // Create emulator identity
        var cameraType: FLIRCameraType = .flirOne
        if type.lowercased().contains("edge") {
            cameraType = .flirOneEdge
        } else if type.lowercased().contains("pro") {
            cameraType = .flirOneEdgePro
        }
        
        if let emulatorIdentity = FLIRIdentity(emulatorType: cameraType) {
            discoveredDevices.append(FlirDeviceInfo(
                deviceId: emulatorIdentity.deviceId(),
                name: "FLIR Emulator",
                communicationType: "EMULATOR",
                isEmulator: true
            ))
            identityMap[emulatorIdentity.deviceId()] = emulatorIdentity
            
            // Auto-connect to emulator
            performConnection(identity: emulatorIdentity)
        }
#else
        delegate?.onError("FLIR SDK not available - emulator disabled")
#endif
    }
    
    // MARK: - State Emission
    
    private func emitStateChange(_ state: String) {
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            self.delegate?.onStateChanged(
                state,
                isConnected: self._isConnected,
                isStreaming: self._isStreaming,
                isEmulator: self.isEmulator
            )
        }
    }
    
    // MARK: - Fallback Frame
    
    /// Generate a fallback gradient image when SDK is not available
    @objc public static func generateFallbackFrame(width: Int, height: Int) -> UIImage {
        let size = CGSize(width: width, height: height)
        UIGraphicsBeginImageContextWithOptions(size, true, 1.0)
        defer { UIGraphicsEndImageContext() }
        
        guard let context = UIGraphicsGetCurrentContext() else {
            return UIImage()
        }
        
        // Create a thermal-looking gradient
        let colors: [CGColor] = [
            UIColor(red: 0.0, green: 0.0, blue: 0.5, alpha: 1.0).cgColor,   // Dark blue (cold)
            UIColor(red: 0.0, green: 0.5, blue: 0.5, alpha: 1.0).cgColor,   // Cyan
            UIColor(red: 0.0, green: 0.8, blue: 0.0, alpha: 1.0).cgColor,   // Green
            UIColor(red: 1.0, green: 1.0, blue: 0.0, alpha: 1.0).cgColor,   // Yellow
            UIColor(red: 1.0, green: 0.5, blue: 0.0, alpha: 1.0).cgColor,   // Orange
            UIColor(red: 1.0, green: 0.0, blue: 0.0, alpha: 1.0).cgColor,   // Red (hot)
            UIColor(red: 1.0, green: 1.0, blue: 1.0, alpha: 1.0).cgColor    // White (hottest)
        ]
        
        let colorSpace = CGColorSpaceCreateDeviceRGB()
        let locations: [CGFloat] = [0.0, 0.15, 0.3, 0.5, 0.7, 0.85, 1.0]
        
        if let gradient = CGGradient(colorsSpace: colorSpace, colors: colors as CFArray, locations: locations) {
            context.drawLinearGradient(
                gradient,
                start: CGPoint(x: 0, y: size.height),
                end: CGPoint(x: size.width, y: 0),
                options: []
            )
        }
        
        // Add "FALLBACK" text
        let text = "FLIR FALLBACK"
        let attributes: [NSAttributedString.Key: Any] = [
            .font: UIFont.boldSystemFont(ofSize: 14),
            .foregroundColor: UIColor.white
        ]
        let textSize = text.size(withAttributes: attributes)
        let textRect = CGRect(
            x: (size.width - textSize.width) / 2,
            y: (size.height - textSize.height) / 2,
            width: textSize.width,
            height: textSize.height
        )
        text.draw(in: textRect, withAttributes: attributes)
        
        return UIGraphicsGetImageFromCurrentImageContext() ?? UIImage()
    }
}

// MARK: - FLIRDiscoveryEventDelegate

#if FLIR_ENABLED
extension FlirManager: FLIRDiscoveryEventDelegate {
    public func cameraDiscovered(_ discoveredCamera: FLIRDiscoveredCamera) {
        let identity = discoveredCamera.identity
        let deviceId = identity.deviceId()
        
        NSLog("[FlirManager] Camera discovered: \(deviceId)")
        
        // Store identity for later connection
        identityMap[deviceId] = identity
        
        // Create device info
        let deviceInfo = FlirDeviceInfo(
            deviceId: deviceId,
            name: discoveredCamera.displayName ?? deviceId,
            communicationType: communicationInterfaceName(identity.communicationInterface()),
            isEmulator: identity.communicationInterface() == .emulator
        )
        
        // Add to discovered list if not already present
        if !discoveredDevices.contains(where: { $0.deviceId == deviceId }) {
            discoveredDevices.append(deviceInfo)
        }
        
        // Notify delegate
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            self.delegate?.onDevicesFound(self.discoveredDevices)
        }
    }
    
    public func cameraLost(_ cameraIdentity: FLIRIdentity) {
        let deviceId = cameraIdentity.deviceId()
        NSLog("[FlirManager] Camera lost: \(deviceId)")
        
        identityMap.removeValue(forKey: deviceId)
        discoveredDevices.removeAll { $0.deviceId == deviceId }
        
        // If this was our connected device, handle disconnect
        if connectedDeviceId == deviceId {
            disconnect()
        }
        
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            self.delegate?.onDevicesFound(self.discoveredDevices)
        }
    }
    
    public func discoveryError(_ error: String, netServiceError nsnetserviceserror: Int32, on iface: FLIRCommunicationInterface) {
        NSLog("[FlirManager] Discovery error: \(error) (\(nsnetserviceserror)) on interface: \(iface)")
        
        DispatchQueue.main.async { [weak self] in
            self?.delegate?.onError("Discovery error: \(error)")
        }
    }
    
    public func discoveryFinished(_ iface: FLIRCommunicationInterface) {
        NSLog("[FlirManager] Discovery finished on interface: \(iface)")
        isScanning = false
    }
}

// MARK: - FLIRDataReceivedDelegate

extension FlirManager: FLIRDataReceivedDelegate {
    public func onDisconnected(_ camera: FLIRCamera, withError error: Error?) {
        NSLog("[FlirManager] Camera disconnected: \(error?.localizedDescription ?? "no error")")
        
        _isConnected = false
        _isStreaming = false
        connectedDeviceId = nil
        connectedDeviceName = nil
        
        DispatchQueue.main.async { [weak self] in
            self?.delegate?.onDeviceDisconnected()
            self?.emitStateChange("disconnected")
        }
    }
}

// MARK: - FLIRStreamDelegate

extension FlirManager: FLIRStreamDelegate {
    public func onError(_ error: Error) {
        NSLog("[FlirManager] Stream error: \(error)")
        
        DispatchQueue.main.async { [weak self] in
            self?.delegate?.onError("Stream error: \(error.localizedDescription)")
        }
    }
    
    public func onImageReceived() {
        guard let streamer = streamer else { return }
        
        do {
            try streamer.update()
            
            if let image = streamer.getImage() {
                _latestImage = image
                
                // Get temperature from thermal image
                streamer.withThermalImage { [weak self] thermalImage in
                    if let stats = thermalImage.getStatistics() {
                        self?.lastTemperature = stats.getMax().value
                    }
                }
                
                DispatchQueue.main.async { [weak self] in
                    guard let self = self else { return }
                    self.delegate?.onFrameReceived(
                        image,
                        width: Int(image.size.width),
                        height: Int(image.size.height)
                    )
                }
            }
        } catch {
            NSLog("[FlirManager] Streamer update error: \(error)")
        }
    }
}
#endif
