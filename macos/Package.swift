// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "BridgeNotesMac",
    platforms: [.macOS(.v13)],
    products: [
        .executable(name: "BridgeNotesMac", targets: ["BridgeNotesMac"])
    ],
    targets: [
        .executableTarget(
            name: "BridgeNotesMac",
            path: "Sources/BridgeNotesMac"
        )
    ]
)
