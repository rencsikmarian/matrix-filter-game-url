// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "MatrixFilterGameUrl",
    platforms: [.iOS(.v14)],
    products: [
        .library(
            name: "MatrixFilterGameUrl",
            targets: ["NAIFilterGameUrlPluginPlugin"])
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", from: "7.0.0")
    ],
    targets: [
        .target(
            name: "NAIFilterGameUrlPluginPlugin",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                .product(name: "Cordova", package: "capacitor-swift-pm")
            ],
            path: "ios/Sources/NAIFilterGameUrlPluginPlugin"),
        .testTarget(
            name: "NAIFilterGameUrlPluginPluginTests",
            dependencies: ["NAIFilterGameUrlPluginPlugin"],
            path: "ios/Tests/NAIFilterGameUrlPluginPluginTests")
    ]
)