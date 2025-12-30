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
    @objc optional func onFrameReceivedRaw(_ data: Data, width: Int, height: Int, bytesPerRow: Int, timestamp: Double)
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
    // Client lifecycle for discovery/connection ownership
    private var activeClients: Set<String> = []
    private var shutdownWorkItem: DispatchWorkItem? = nil
    
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
    
    // Preference: ask SDK to deliver oriented/rotated frames (if SDK supports it)
    private var _preferSdkRotation: Bool = false
    
    @objc public func setPreferSdkRotation(_ prefer: Bool) {
        _preferSdkRotation = prefer
    }
    
    @objc public func isPreferSdkRotation() -> Bool {
        return _preferSdkRotation
    }
    
    @objc public func getConnectedDeviceInfo() -> String {
        return connectedDeviceName ?? "Not connected"
    }
    
    @objc public func getDiscoveredDevices() -> [FlirDeviceInfo] {
        return discoveredDevices
    }

    // MARK: - Temperature & Battery Access

    /// Returns a temperature data dictionary for the given pixel, or nil if unavailable.
    @objc public func getTemperatureData(x: Int = -1, y: Int = -1) -> [String: Any]? {
#if FLIR_ENABLED
        guard let streamer = streamer else { return nil }
        var result: [String: Any]? = nil
        streamer.withThermalImage { [weak self] thermalImage in
            // Attempt to extract per-pixel measurements if available
            if let measurements = thermalImage.measurements as? [NSNumber],
               measurements.count > 0,
               let img = streamer.getImage() {
                let width = Int(img.size.width)
                let height = Int(img.size.height)
                if width > 0 && height > 0 && x >= 0 && y >= 0 && x < width && y < height {
                    let idx = y * width + x
                    if idx < measurements.count {
                        let temp = measurements[idx].doubleValue
                        result = ["temperature": temp]
                    }
                }
            }
            // Fallback: use lastTemperature if set
            if result == nil, let s = self, !s.lastTemperature.isNaN {
                result = ["temperature": s.lastTemperature]
            }
        }
        return result
#else
        return nil
#endif
    }

    @objc public func getTemperatureAtPoint(_ x: Int, y: Int) -> Double {
        if let data = getTemperatureData(x: x, y: y), let t = data["temperature"] as? Double {
            return t
        }
        return Double.nan
    }

    @objc public func getTemperatureAtNormalized(_ nx: Double, y: Double) -> Double {
        guard let img = latestImage else { return Double.nan }
        let px = Int(nx * Double(img.size.width))
        let py = Int(y * Double(img.size.height))
        return getTemperatureAtPoint(px, y: py)
    }

    @objc public func getBatteryLevel() -> Int {
#if FLIR_ENABLED
        if let cam = camera {
            if let val = cam.value(forKey: "batteryLevel") as? Int { return val }
            if let batt = cam.value(forKey: "battery") as? NSObject,
               let lv = batt.value(forKey: "level") as? Int { return lv }
        }
#endif
        return -1
    }

    @objc public func isBatteryCharging() -> Bool {
#if FLIR_ENABLED
        if let cam = camera {
            if let ch = cam.value(forKey: "isCharging") as? Bool { return ch }
            if let batt = cam.value(forKey: "battery") as? NSObject,
               let ch = batt.value(forKey: "charging") as? Bool { return ch }
        }
#endif
        return false
    }

    @objc public func latestFrameImage() -> UIImage? {
        return latestImage
    }

    @objc public func latestFrameBase64() -> String? {
        guard let img = latestImage else { return nil }
        if let data = img.jpegData(compressionQuality: 0.7) {
            return data.base64EncodedString()
        }
        if let data = img.pngData() {
            return data.base64EncodedString()
        }
        return nil
    }

    // Returns a NSDictionary with BGRA base64 data for the latest frame.
    // Keys: width (Int), height (Int), bytesPerRow (Int), dataBase64 (String)
    @objc public func latestFrameBitmapBase64() -> NSDictionary? {
        guard let img = latestImage else { return nil }
        guard let bmp = convertUIImageToBGRA(img) else { return nil }
        let b64 = bmp.data.base64EncodedString()
        return ["width": bmp.width, "height": bmp.height, "bytesPerRow": bmp.bytesPerRow, "dataBase64": b64]
    }

    // Convert a UIImage to BGRA (kCGBitmapByteOrder32Little | kCGImageAlphaPremultipliedFirst).
    private func convertUIImageToBGRA(_ image: UIImage) -> (data: Data, width: Int, height: Int, bytesPerRow: Int)? {
        guard let cg = image.cgImage else { return nil }
        let width = cg.width
        let height = cg.height
        let bytesPerRow = width * 4
        let size = height * bytesPerRow
        var data = Data(count: size)
        let colorSpace = CGColorSpaceCreateDeviceRGB()
        let bitmapInfo = CGBitmapInfo.byteOrder32Little.rawValue | CGImageAlphaInfo.premultipliedFirst.rawValue
        let success = data.withUnsafeMutableBytes { (ptr: UnsafeMutableRawBufferPointer) -> Bool in
            guard let base = ptr.baseAddress else { return false }
            guard let ctx = CGContext(data: base, width: width, height: height, bitsPerComponent: 8, bytesPerRow: bytesPerRow, space: colorSpace, bitmapInfo: bitmapInfo) else { return false }
            let rect = CGRect(x: 0, y: 0, width: width, height: height)
            ctx.draw(cg, in: rect)
            return true
        }
        return success ? (data, width, height, bytesPerRow) : nil
    }

    // Client lifecycle helpers: callers (UI/filters) can retain/release to ensure
    // discovery runs while any client is active.
    @objc public func retainClient(_ clientId: String) {
        DispatchQueue.main.async {
            self.activeClients.insert(clientId)
            self.shutdownWorkItem?.cancel()
            self.shutdownWorkItem = nil
            if self.activeClients.count == 1 {
                self.startDiscovery()
            }
        }
    }

    @objc public func releaseClient(_ clientId: String) {
        DispatchQueue.main.async {
            self.activeClients.remove(clientId)
            self.shutdownWorkItem?.cancel()
            let work = DispatchWorkItem { [weak self] in
                guard let self = self else { return }
                if self.activeClients.isEmpty {
                    self.stopDiscovery()
                }
            }
            self.shutdownWorkItem = work
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5, execute: work)
        }
    }

    // MARK: - Palette Control

    /// Set palette by name (case-insensitive). If the SDK isn't available or the
    /// palette cannot be found, this is a no-op.
    @objc public func setPalette(_ paletteName: String) {
#if FLIR_ENABLED
        guard let streamer = streamer, let thermalImage = streamer.getImage() else {
            NSLog("[FlirManager] Cannot set palette - no active streamer")
            return
        }

        // Use runtime-safe APIs to find and set palette to avoid compile-time
        // coupling to specific SDK versions. Try FLIRPaletteManager.defaultPalettes
        // via ObjC runtime, then attempt a KVC set on the returned image if possible.
        if let pmClass = NSClassFromString("FLIRPaletteManager") as AnyObject?, pmClass.responds(to: Selector(("default"))) {
            if let pmInstance = pmClass.perform(Selector(("default")))?.takeUnretainedValue() as? NSObject,
               pmInstance.responds(to: Selector(("getDefaultPalettes"))) {
            if let arr = pmInstance.perform(Selector(("getDefaultPalettes")))?.takeUnretainedValue() as? NSArray {
                for palette in arr {
                    if let p = palette as? NSObject,
                       let name = p.value(forKey: "name") as? String,
                       name.lowercased() == paletteName.lowercased() {
                        if let imgObj = thermalImage as? NSObject {
                            // Try both 'palette' and 'Palette' keys depending on SDK
                            if imgObj.responds(to: Selector(("setPalette:"))) {
                                imgObj.perform(Selector(("setPalette:")), with: p)
                            } else {
                                imgObj.setValue(p, forKey: "Palette")
                            }
                            NSLog("[FlirManager] ✅ Palette set to: \(paletteName)")
                            return
                        }
                    }
                }
                NSLog("[FlirManager] Palette not found: \(paletteName)")
            } else {
                NSLog("[FlirManager] Palette manager returned unexpected type")
            }
        }
        } else {
            NSLog("[FlirManager] SDK palette APIs not available - cannot set palette")
        }
#else
        NSLog("[FlirManager] SDK not available - cannot set palette")
#endif
    }

    /// Map a normalized acol value (0..1) to a palette name.
    @objc public static func getPaletteNameFromAcol(_ acol: Float) -> String {
        if acol < 0.125 { return "WhiteHot" }
        else if acol < 0.25 { return "BlackHot" }
        else if acol < 0.375 { return "Iron" }
        else if acol < 0.5 { return "Rainbow" }
        else if acol < 0.625 { return "Lava" }
        else if acol < 0.75 { return "Arctic" }
        else if acol < 0.875 { return "Coldest" }
        else { return "Hottest" }
    }

    @objc public func setPaletteFromAcol(_ acol: Float) {
        let paletteName = FlirManager.getPaletteNameFromAcol(acol)
        NSLog("[FlirManager] Setting palette from acol=\(acol) -> \(paletteName)")
        setPalette(paletteName)
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
        
        // Build interfaces based on available permissions
        // Always include Lightning, USB, Wireless BLE, and Emulator
        var interfaces: FLIRCommunicationInterface = [
            .lightning,
            .flirOneWireless,
            .emulator
        ]
        
        // Only add network discovery if NSLocalNetworkUsageDescription is present
        // This prevents crashes/errors when user doesn't have iOS developer registration
        // or hasn't declared network permission
        if shouldEnableNetworkDiscovery() {
            interfaces.insert(.network)
            NSLog("[FlirManager] Network discovery enabled (NSLocalNetworkUsageDescription present)")
        } else {
            NSLog("[FlirManager] Network discovery disabled (no NSLocalNetworkUsageDescription)")
        }
        
        discovery?.start(interfaces)
        
        emitStateChange("discovering")
        NSLog("[FlirManager] Discovery started on interfaces: Lightning, \(interfaces.contains(.network) ? "Network, " : "")FlirOneWireless, Emulator")
#else
        NSLog("[FlirManager] FLIR SDK not available - discovery disabled")
        delegate?.onError("FLIR SDK not available")
#endif
    }
    
    /// Check if network discovery should be enabled based on Info.plist permission
    private func shouldEnableNetworkDiscovery() -> Bool {
        // Check for explicit override first
        let key = "ilabsFlir.networkDiscoveryEnabled"
        if UserDefaults.standard.object(forKey: key) != nil {
            return UserDefaults.standard.bool(forKey: key)
        }
        
        // Safe default: require Local Network usage description to be present
        if let desc = Bundle.main.object(forInfoDictionaryKey: "NSLocalNetworkUsageDescription") as? String,
           !desc.isEmpty {
            return true
        }
        
        return false
    }
    
    /// Allow explicit override of network discovery (called from React Native)
    @objc public func setNetworkDiscoveryEnabled(_ enabled: Bool) {
        UserDefaults.standard.set(enabled, forKey: "ilabsFlir.networkDiscoveryEnabled")
        NSLog("[FlirManager] Network discovery override set to: \(enabled)")
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
        // Use the proven connection pattern from FLIR SDK samples:
        // FLIROneCameraSwift uses: pair(identity, code:) then connect()
        
        if camera == nil {
            camera = FLIRCamera()
            camera?.delegate = self
        }
        
        guard let cam = camera else {
            NSLog("[FlirManager] Failed to create FLIRCamera")
            DispatchQueue.main.async { [weak self] in
                self?.delegate?.onError("Failed to create camera instance")
            }
            return
        }
        
        // Handle authentication for generic cameras (network cameras)
        if identity.cameraType() == .generic {
            let certName = getCertificateName()
            var status = FLIRAuthenticationStatus.pending
            while status == .pending {
                status = cam.authenticate(identity, trustedConnectionName: certName)
                if status == .pending {
                    NSLog("[FlirManager] Waiting for camera authentication approval...")
                    Thread.sleep(forTimeInterval: 1.0)
                }
            }
            NSLog("[FlirManager] Authentication status: \(status.rawValue)")
        }
        
        do {
            // Step 1: Pair with identity (required for FLIR One devices)
            // The code parameter is for BLE pairing, 0 for direct connection
            try cam.pair(identity, code: 0)
            NSLog("[FlirManager] Paired with: \(identity.deviceId())")
            
            // Step 2: Connect (no identity parameter - uses paired identity)
            try cam.connect()
            NSLog("[FlirManager] Connected to: \(identity.deviceId())")
            
            // Update state
            connectedIdentity = identity
            connectedDeviceId = identity.deviceId()
            connectedDeviceName = identity.deviceId()
            _isConnected = true
            
            // Get camera info if available
            if let remoteControl = cam.getRemoteControl(),
               let cameraInfo = try? remoteControl.getCameraInformation() {
                NSLog("[FlirManager] Camera info: \(cameraInfo)")
            }
            
            // Get streams
            let streams = cam.getStreams()
            if !streams.isEmpty {
                NSLog("[FlirManager] Found \(streams.count) streams")

                // Find and start the first thermal stream (preferred) or any stream
                let thermalStream = streams.first { $0.isThermal } ?? streams.first
                if let streamToStart = thermalStream {
                    startStreamInternal(streamToStart)
                }
            } else {
                NSLog("[FlirManager] No streams available on camera")
            }
            
            // Notify delegate on main thread
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
            NSLog("[FlirManager] Connection failed: \(error.localizedDescription)")
            _isConnected = false
            camera = nil
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
        if String(describing: iface).lowercased().contains("usb") { return "USB" }
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
                
                // Update shared state for native preview
                FlirState.shared().updateFrame(image)

                // Get temperature from thermal image (use runtime selectors to be resilient across SDK versions)
                streamer.withThermalImage { [weak self] thermalImage in
                    var tempVal: Double = Double.nan
                    // Try getImageStatistics then fallback to getStatistics (different SDK versions)
                    if let statsObj = (thermalImage.perform(Selector(("getImageStatistics")))?.takeUnretainedValue() as? NSObject) ?? (thermalImage.perform(Selector(("getStatistics")))?.takeUnretainedValue() as? NSObject) {
                        if statsObj.responds(to: Selector(("getMax"))) {
                            if let maxObj = statsObj.perform(Selector(("getMax")))?.takeUnretainedValue() as? NSObject,
                               let val = maxObj.value(forKey: "value") as? Double {
                                tempVal = val
                            }
                        } else if let maxVal = statsObj.value(forKey: "max") as? NSObject,
                                  let val = maxVal.value(forKey: "value") as? Double {
                            tempVal = val
                        }
                    }
                    if !tempVal.isNaN {
                        self?.lastTemperature = tempVal
                        FlirState.shared().lastTemperature = tempVal
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

                // Also provide a raw BGRA bitmap callback (optional) to delegates and a
                // queryable base64 bitmap dict for RN consumers. Conversion is done
                // off the main thread to avoid blocking UI.
                DispatchQueue.global(qos: .utility).async { [weak self, weak image] in
                    guard let self = self, let image = image else { return }
                    if let bmp = self.convertUIImageToBGRA(image) {
                        let ts = Date().timeIntervalSince1970 * 1000.0
                        DispatchQueue.main.async {
                            // Notify the delegate if set (may be FlirModule or another consumer)
                            self.delegate?.onFrameReceivedRaw?(bmp.data, width: bmp.width, height: bmp.height, bytesPerRow: bmp.bytesPerRow, timestamp: ts)

                            // Post a system notification so multiple native observers can react
                            NotificationCenter.default.post(name: Notification.Name("FlirFrameBitmapAvailableNative"), object: nil, userInfo: ["width": bmp.width, "height": bmp.height, "bytesPerRow": bmp.bytesPerRow, "timestamp": ts])
                        }
                    }
                }
            }
        } catch {
            NSLog("[FlirManager] Streamer update error: \(error)")
        }
    }
}
#endif
