import Foundation
import Capacitor
import WebKit

/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitorjs.com/docs/plugins/ios
 */
@objc(NAIFilterGameUrlPlugin)
public class NAIFilterGameUrlPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "NAIFilterGameUrlPlugin"
    public let jsName = "NAIFilterGameUrl"
    public let pluginMethods: [CAPPluginMethod] = []

    private let defaultBlockedDomains = [
        "admiralbet.es",
        "admiralbet.de",
        "stargames.de",
        "starvegas.ch",
        "admiral.ch",
        "admiralcasino.co.uk",
        "loteriesport.lu",
        "admiral.ro",
        "fenikss.lv",
        "feniksscasino.lv"
    ]
    
    private var blockedDomains: [String] = []
    private var redirectAppUrl: String = ""
    private var appUrl: String = ""

    @objc override public func load() {
        print("NAIFilterGameUrlPlugin loaded successfully!")
        
        // Get the scheme and hostname from Capacitor config
        let scheme = bridge?.config.getString("server.scheme") ?? InstanceDescriptorDefaults.scheme
        let hostname = bridge?.config.getString("server.hostname") ?? InstanceDescriptorDefaults.hostname

        self.appUrl = "\(scheme)://\(hostname)"
        self.redirectAppUrl = self.appUrl
        self.blockedDomains = defaultBlockedDomains

         print("🔹 App URL set to: \(self.appUrl)")
    }
    
    @objc public override func shouldOverrideLoad(_ navigationAction: WKNavigationAction) -> NSNumber? {
        print("✅ shouldOverrideLoad called with URL: \(navigationAction.request.url?.absoluteString ?? "Unknown")")
        
        guard let url = navigationAction.request.url else {
            return nil // let capacitor policy decide
        }
        let urlString = url.absoluteString
        guard let host = url.host else {
            return nil // let capacitor policy decide
        }
        
        var isBlocked = false
        
        for blockedDomain in blockedDomains {
            if host.contains(blockedDomain) {
                print("NAIFilterGameUrlPlugin: Matched host: \(host)")
                let scheme = url.scheme ?? ""
                
                if scheme == "https" {
                    redirectAppUrl = urlString
                        .replacingOccurrences(of: "https://staging.\(blockedDomain)", with: appUrl)
                        .replacingOccurrences(of: "https://beta.\(blockedDomain)", with: appUrl)
                        .replacingOccurrences(of: "https://www.\(blockedDomain)", with: appUrl)
                } else if scheme == "http" {
                    redirectAppUrl = urlString
                        .replacingOccurrences(of: "http://staging.\(blockedDomain)", with: appUrl)
                        .replacingOccurrences(of: "http://beta.\(blockedDomain)", with: appUrl)
                        .replacingOccurrences(of: "http://www.\(blockedDomain)", with: appUrl)
                }
                isBlocked = true
                break
            }
            
            let openUrlParam = "OpenURL?url="
            if urlString.contains(openUrlParam),
               let paramValue = urlString.components(separatedBy: openUrlParam).last,
               paramValue.contains(blockedDomain) {
                print("NAIFilterGameUrlPlugin: Matched blocked domain in OpenURL parameter: \(paramValue)")
                redirectAppUrl = appUrl
                isBlocked = true
                break
            }
        }
        
        if isBlocked {
            print("NAIFilterGameUrlPlugin: Redirect from: \(urlString) to: \(redirectAppUrl)")
            self.webView?.load(URLRequest(url: URL(string:redirectAppUrl)!))
            return NSNumber(value: true)
        }
        
        return nil // let capacitor policy decide
    }
}
