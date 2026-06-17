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

    private static let allowedPrefixes = ["", "www.", "staging.", "beta."]

    private var blockedDomains: [String] = []
    private var passPaths: [String] = []
    private var openUrlParams: [String] = []
    private var blockedHostKeywords: [String] = []
    private var blockedPathSuffixes: [String] = []
    private var appUrl: String = ""

    @objc override public func load() {
        let scheme = bridge?.config.getString("server.scheme") ?? InstanceDescriptorDefaults.scheme
        let hostname = bridge?.config.getString("server.hostname") ?? InstanceDescriptorDefaults.hostname
        appUrl = "\(scheme)://\(hostname)"

        let config = getConfig()
        blockedDomains = configList(config, "blockedDomains")
        passPaths = configList(config, "passPaths")
        openUrlParams = configList(config, "openUrlParams")
        blockedHostKeywords = configList(config, "blockedHostKeywords")
        blockedPathSuffixes = configList(config, "blockedPathSuffixes")

        print("NAIFilterGameUrlPlugin loaded. App URL: \(appUrl), blocked domains: \(blockedDomains)")
    }

    @objc public override func shouldOverrideLoad(_ navigationAction: WKNavigationAction) -> NSNumber? {
        guard let url = navigationAction.request.url, let host = url.host?.lowercased() else {
            return nil // let capacitor policy decide
        }
        let urlString = url.absoluteString.lowercased()

        // 1. Blocked domain: redirect, unless a pass path exempts the URL
        let isBlockedDomain = blockedDomains.contains { domain in
            Self.allowedPrefixes.contains { prefix in host == prefix + domain }
        }
        if isBlockedDomain {
            if passPaths.contains(where: { urlString.contains($0) }) {
                print("NAIFilterGameUrlPlugin: Pass path allowed: \(urlString)")
                return nil
            }
            let scheme = url.scheme?.lowercased() ?? ""
            let redirectUrl = urlString.replacingOccurrences(of: "\(scheme)://\(host)", with: appUrl)
            return redirect(from: urlString, to: redirectUrl)
        }

        // 2. Blocked domain inside an open-url parameter, on any host
        for param in openUrlParams where urlString.contains(param) {
            let paramValue = urlString.components(separatedBy: param).dropFirst().joined(separator: param)
            if blockedDomains.contains(where: { paramValue.contains($0) }) {
                return redirect(from: urlString, to: appUrl)
            }
        }

        // 3. Blocked host keywords, on any host
        if blockedHostKeywords.contains(where: { host.contains($0) }) {
            return redirect(from: urlString, to: appUrl)
        }

        // 4. Blocked path suffixes, on any URL
        if blockedPathSuffixes.contains(where: { urlString.hasSuffix("/\($0)") }) {
            return redirect(from: urlString, to: appUrl)
        }

        return nil // let capacitor policy decide
    }

    private func configList(_ config: PluginConfig, _ key: String) -> [String] {
        return (config.getArray(key) ?? []).compactMap { ($0 as? String)?.lowercased() }
    }

    private func redirect(from urlString: String, to redirectUrl: String) -> NSNumber? {
        guard let url = URL(string: redirectUrl) else {
            return nil // malformed target; let capacitor policy decide
        }
        print("NAIFilterGameUrlPlugin: Redirect from: \(urlString) to: \(redirectUrl)")
        webView?.load(URLRequest(url: url))
        return NSNumber(value: true)
    }
}
