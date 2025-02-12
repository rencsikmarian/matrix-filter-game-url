import Foundation
import Capacitor
import WebKit

/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitorjs.com/docs/plugins/ios
 */
@objc(NAIFilterGameUrlPluginPlugin)
public class NAIFilterGameUrlPluginPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "NAIFilterGameUrlPluginPlugin"
    public let jsName = "NAIFilterGameUrlPlugin"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "shouldOverrideUrlLoading", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getRedirectUrl", returnType: CAPPluginReturnPromise)
    ]
    private let implementation = NAIFilterGameUrlPlugin()

    public override func shouldOverrideLoad(_ navigationAction: WKNavigationAction) -> Bool {
        if let result = implementation.shouldOverrideLoad(navigationAction) {
            return result.boolValue
        }
        return false
    }
}
