// swift-tools-version: 6.0
import PackageDescription

// FlorsentCore: Foundation only (breathing timeline, cue gate, sound tuning, App Group state). The
// Screen Time extensions link only this, which keeps the DeviceActivityMonitor under its ~6 MB limit.
// FlorsentKit: SwiftUI on top (palette, contrast audit, the breathing flower).
let package = Package(
    name: "FlorsentKit",
    defaultLocalization: "en",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "FlorsentCore", targets: ["FlorsentCore"]),
        .library(name: "FlorsentKit", targets: ["FlorsentKit"]),
    ],
    targets: [
        .target(name: "FlorsentCore"),
        .target(name: "FlorsentKit", dependencies: ["FlorsentCore"]),
        .testTarget(name: "FlorsentCoreTests", dependencies: ["FlorsentCore"]),
        .testTarget(name: "FlorsentKitTests", dependencies: ["FlorsentKit"]),
    ]
)
