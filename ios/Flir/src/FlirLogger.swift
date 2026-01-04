//
//  FlirLogger.swift
//  Flir
//
//  Comprehensive lifecycle logging for FLIR camera operations
//  Helps debug: load → discovery → connection → streaming → frame → battery
//

import Foundation

/// Lifecycle stages for FLIR operations
@objc public enum FlirLifecycleStage: Int {
    case load       // SDK initialization
    case discovery  // Device scanning
    case connection // Device pairing/connecting
    case streaming  // Stream start/stop
    case frame      // Frame receiving
    case battery    // Battery polling
    case error      // Error states
    case disconnect // Disconnection events
    
    var tag: String {
        switch self {
        case .load: return "LOAD"
        case .discovery: return "DISCOVERY"
        case .connection: return "CONNECTION"
        case .streaming: return "STREAMING"
        case .frame: return "FRAME"
        case .battery: return "BATTERY"
        case .error: return "ERROR"
        case .disconnect: return "DISCONNECT"
        }
    }
}

/// Centralized logger for FLIR operations with consistent formatting
@objc public class FlirLogger: NSObject {
    
    /// Frame counter for periodic frame logging
    private static var frameCount: Int = 0
    private static let frameLogInterval: Int = 100  // Log every 100 frames
    
    /// Log a message at a specific lifecycle stage
    @objc public static func log(_ stage: FlirLifecycleStage, _ message: String) {
        let timestamp = Date()
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm:ss.SSS"
        let timeStr = formatter.string(from: timestamp)
        NSLog("[Flir-\(stage.tag)] [\(timeStr)] \(message)")
    }
    
    /// Log an error with optional Error object
    @objc public static func logError(_ stage: FlirLifecycleStage, _ message: String, error: Error? = nil) {
        let errorDesc = error?.localizedDescription ?? "no error details"
        log(stage, "❌ \(message) - \(errorDesc)")
    }
    
    /// Log frame received (rate-limited to avoid log spam)
    @objc public static func logFrame(width: Int, height: Int, temperature: Double? = nil) {
        frameCount += 1
        if frameCount % frameLogInterval == 0 {
            var msg = "Frame #\(frameCount) received (\(width)x\(height))"
            if let temp = temperature, !temp.isNaN {
                msg += " temp=\(String(format: "%.1f", temp))°C"
            }
            log(.frame, msg)
        }
    }
    
    /// Reset frame counter (call on disconnect)
    @objc public static func resetFrameCounter() {
        frameCount = 0
    }
    
    /// Log discovery interface status (important for debugging network vs lightning)
    @objc public static func logDiscoveryInterfaces(lightning: Bool, network: Bool, wireless: Bool, emulator: Bool) {
        var interfaces: [String] = []
        if lightning { interfaces.append("Lightning") }
        if network { interfaces.append("Network") }
        if wireless { interfaces.append("FlirOneWireless") }
        if emulator { interfaces.append("Emulator") }
        
        log(.discovery, "Starting discovery on interfaces: \(interfaces.joined(separator: ", "))")
        
        if !network {
            log(.discovery, "⚠️ Network discovery DISABLED - requires paid iOS Developer License with NSLocalNetworkUsageDescription")
        }
    }
    
    /// Log device found with details
    @objc public static func logDeviceFound(deviceId: String, name: String, type: String, isEmulator: Bool) {
        var msg = "Device found: '\(name)' (id=\(deviceId), type=\(type))"
        if isEmulator {
            msg += " [EMULATOR]"
        }
        log(.discovery, msg)
    }
    
    /// Log connection attempt
    @objc public static func logConnectionAttempt(deviceId: String) {
        log(.connection, "Attempting connection to: \(deviceId)")
    }
    
    /// Log connection success with stream info
    @objc public static func logConnectionSuccess(deviceId: String, streamCount: Int, hasThermal: Bool) {
        log(.connection, "✅ Connected to: \(deviceId) - \(streamCount) stream(s), thermal=\(hasThermal)")
    }
    
    /// Log battery state
    @objc public static func logBattery(level: Int, isCharging: Bool) {
        if level >= 0 {
            let chargingStr = isCharging ? "charging" : "not charging"
            log(.battery, "Level: \(level)%, \(chargingStr)")
        } else {
            log(.battery, "Battery info unavailable")
        }
    }
}
