import Foundation
import UIKit

/// ObjC-compatible shim that forwards to FlirManager
/// This provides a clean API for native consumers (FilterDataProvider, etc.)
@objc public class FLIRManager: NSObject {
    @objc public static let shared = FLIRManager()
    
    private override init() {
        super.init()
    }

    // MARK: - SDK Availability
    
    @objc public func isAvailable() -> Bool {
        return FlirManager.isSDKAvailable
    }
    
    @objc public static var isSDKAvailable: Bool {
        return FlirManager.isSDKAvailable
    }

    // MARK: - Temperature APIs
    
    @objc public func getTemperatureAtPoint(x: Int, y: Int) -> Double {
        return FlirManager.shared.getTemperatureAtPoint(x, y: y)
    }

    @objc public func getTemperatureAtNormalized(_ nx: Double, y: Double) -> Double {
        return FlirManager.shared.getTemperatureAtNormalized(nx, y: y)
    }

    // MARK: - Battery APIs
    
    @objc public func getBatteryLevel() -> Int {
        return FlirManager.shared.getBatteryLevel()
    }

    @objc public func isBatteryCharging() -> Bool {
        return FlirManager.shared.isBatteryCharging()
    }

    // MARK: - Rotation Preference
    
    @objc public func setPreferSdkRotation(_ prefer: Bool) {
        FlirManager.shared.setPreferSdkRotation(prefer)
    }

    @objc public func isPreferSdkRotation() -> Bool {
        return FlirManager.shared.isPreferSdkRotation()
    }

    // MARK: - Frame Access
    
    @objc public func latestFrameImage() -> UIImage? {
        return FlirManager.shared.latestImage
    }
    
    @objc public var latestImage: UIImage? {
        return FlirManager.shared.latestImage
    }

    // MARK: - Discovery & Connection
    
    @objc public func startDiscovery() {
        FlirManager.shared.startDiscovery()
    }

    @objc public func stopDiscovery() {
        FlirManager.shared.stopDiscovery()
    }
    
    @objc public var isConnected: Bool {
        return FlirManager.shared.isConnected
    }
    
    @objc public var isStreaming: Bool {
        return FlirManager.shared.isStreaming
    }
    
    @objc public var isEmulator: Bool {
        return FlirManager.shared.isEmulator
    }

    // MARK: - Palette Control
    
    @objc public func setPalette(_ name: String) {
        FlirManager.shared.setPalette(name)
    }
    
    @objc public func setPaletteFromAcol(_ acol: Float) {
        FlirManager.shared.setPaletteFromAcol(acol)
    }
    
    // MARK: - Client Lifecycle
    
    @objc public func retainClient(_ clientId: String) {
        FlirManager.shared.retainClient(clientId)
    }
    
    @objc public func releaseClient(_ clientId: String) {
        FlirManager.shared.releaseClient(clientId)
    }
}
