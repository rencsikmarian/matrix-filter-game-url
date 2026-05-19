// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "MatrixFilterGameUrl",
    platforms: [.iOS(.v15)],
    products: [
        .library(
            name: "MatrixFilterGameUrl",
            targets: ["NAIFilterGameUrlPlugin"])
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", from: "8.0.0")
    ],
    targets: [
        .target(
            name: "NAIFilterGameUrlPlugin",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                .product(name: "Cordova", package: "capacitor-swift-pm")
            ],
            path: "ios/Sources/NAIFilterGameUrlPlugin"),
        .testTarget(
            name: "NAIFilterGameUrlPluginTests",
            dependencies: ["NAIFilterGameUrlPlugin"],
            path: "ios/Tests/NAIFilterGameUrlPluginTests")
    ]
)