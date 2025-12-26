// BitmapSample.swift
// Simple usage example for FlirManager raw bitmap callback

import UIKit

class BitmapSample: NSObject, FlirPublicDelegate {
    override init() {
        super.init()
        FlirManager.shared.delegate = self
    }

    func start() {
        FlirManager.shared.startDiscovery()
    }

    func onDevicesFound(_ devices: [FlirDeviceInfo]) {
        print("Devices found: \(devices.map { $0.name })")
    }

    func onFrameReceived(_ image: UIImage, width: Int, height: Int) {
        // UI preview
        DispatchQueue.main.async {
            // update preview imageView
        }
    }

    func onFrameReceivedRaw(_ data: Data, width: Int, height: Int, bytesPerRow: Int, timestamp: Double) {
        // Received BGRA bytes. You can wrap into a CGImage like this:
        data.withUnsafeBytes { (ptr: UnsafeRawBufferPointer) in
            guard let base = ptr.baseAddress else { return }
            let colorSpace = CGColorSpaceCreateDeviceRGB()
            let bitmapInfo = CGBitmapInfo.byteOrder32Little.rawValue | CGImageAlphaInfo.premultipliedFirst.rawValue
            if let ctx = CGContext(data: UnsafeMutableRawPointer(mutating: base), width: width, height: height, bitsPerComponent: 8, bytesPerRow: bytesPerRow, space: colorSpace, bitmapInfo: bitmapInfo), let cg = ctx.makeImage() {
                let ui = UIImage(cgImage: cg)
                // process ui (e.g., run ML, save to disk, etc.)
            }
        }
    }

    func onDeviceConnected(_ device: FlirDeviceInfo) { print("Connected: \(device.name)") }
    func onDeviceDisconnected() { print("Disconnected") }
    func onError(_ message: String) { print("Error: \(message)") }
    func onStateChanged(_ state: String, isConnected: Bool, isStreaming: Bool, isEmulator: Bool) { print("State: \(state)") }
}