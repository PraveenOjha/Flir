import Foundation
import UIKit

@objc public class FLIRManager: NSObject {
    @objc public static let shared = FLIRManager()

    @objc public func isAvailable() -> Bool {
        return FlirManager.isSDKAvailable
    }

    @objc public func getTemperatureAtPoint(x: Int, y: Int) -> Double {
        // FlirManager currently doesn't expose a direct getTemperatureAtPoint API.
        // Return NaN for now (consumers should handle NaN) until full parity is implemented.
        return Double.nan
    }

    @objc public func getTemperatureAtNormalized(_ nx: Double, y: Double) -> Double {
        return Double.nan
    }

    @objc public func getBatteryLevel() -> Int {
        return -1
    }

    @objc public func isBatteryCharging() -> Bool {
        return false
    }

    @objc public func setPreferSdkRotation(_ prefer: Bool) {
        // FlirManager doesn't currently support rotation preference; no-op
    }

    @objc public func isPreferSdkRotation() -> Bool {
        return false
    }

    @objc public func latestFrameImage() -> UIImage? {
        return FlirManager.shared.latestImage
    }

    @objc public func startDiscovery() {
        FlirManager.shared.startDiscovery()
    }

    @objc public func stopDiscovery() {
        FlirManager.shared.stopDiscovery()
    }
}
