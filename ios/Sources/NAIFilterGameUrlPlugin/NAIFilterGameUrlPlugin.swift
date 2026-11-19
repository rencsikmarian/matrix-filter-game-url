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

    private static let defaultAllowedPrefixes = ["www.", "staging.", "beta."]
    private static let interceptedEvent = "appUrlIntercepted"

    // Holds the rules and the navigation state (visited pages, in a game or not).
    private var filter = UrlFilter(appUrl: "", config: UrlFilterConfig())
    private var urlObservation: NSKeyValueObservation?

    @objc override public func load() {
        // The origin the app is really served from. It follows server.iosScheme,
        // server.hostname and server.url (e.g. live reload).
        let defaultAppUrl = "\(InstanceDescriptorDefaults.scheme)://\(InstanceDescriptorDefaults.hostname)"
        let appUrl = (bridge?.config.serverURL).flatMap(UrlFilter.origin(of:)) ?? defaultAppUrl

        let config = getConfig()
        var rules = UrlFilterConfig()
        rules.blockedDomains = configList(config, "blockedDomains")
        rules.allowedPrefixes = config.getArray("allowedPrefixes") == nil
            ? Self.defaultAllowedPrefixes
            : configList(config, "allowedPrefixes")
        rules.appDomains = configList(config, "appDomains")
        rules.passPaths = configList(config, "passPaths")
        rules.openUrlParams = configList(config, "openUrlParams")
        rules.blockedHostKeywords = configList(config, "blockedHostKeywords")
        rules.blockedPathSuffixes = configList(config, "blockedPathSuffixes")
        rules.excludedReturnPaths = configList(config, "excludedReturnPaths")
        rules.gamePaths = configList(config, "gamePaths")
        rules.historyLimit = max(0, config.getInt("historyLimit", 3))
        rules.gameExitGraceMs = max(0, config.getInt("gameExitGraceMs", 3000))
        filter = UrlFilter(appUrl: appUrl, config: rules)

        // SPA route changes (history.pushState) never reach shouldOverrideLoad, so record
        // the main-frame URL whenever it changes instead; this also covers full page loads.
        urlObservation = webView?.observe(\.url, options: [.new]) { [weak self] webView, _ in
            guard let self = self, let url = webView.url else { return }
            self.filter.mainFrameUrlChanged(url)
            guard CAPLog.enableLogging else { return }
            // Query strings and fragments can carry session tokens, so keep them out of the log.
            let page = UrlFilter.withoutQuery(url.absoluteString)
            let history = self.filter.history.map(UrlFilter.withoutQuery)
            CAPLog.print("NAIFilterGameUrlPlugin: Main frame URL: \(page), in game: \(self.filter.inGame), history: \(history)")
        }

        CAPLog.print("NAIFilterGameUrlPlugin loaded. App URL: \(appUrl), blocked domains: \(rules.blockedDomains)")
    }

    @objc public override func shouldOverrideLoad(_ navigationAction: WKNavigationAction) -> NSNumber? {
        guard let url = navigationAction.request.url else {
            return nil // let capacitor policy decide
        }
        // A navigation without a target frame (e.g. target="_blank") counts as top-level.
        let isMainFrame = navigationAction.targetFrame?.isMainFrame ?? true
        guard case .redirect(let target) = filter.decide(for: url, isMainFrame: isMainFrame) else {
            return nil // no rule matched, or a pass path: let capacitor policy decide
        }
        return redirect(from: url.absoluteString, to: target, isMainFrame: isMainFrame)
    }

    private func configList(_ config: PluginConfig, _ key: String) -> [String] {
        return (config.getArray(key) ?? []).compactMap { ($0 as? String)?.lowercased() }
    }

    /// Blocks the navigation. If JS is listening for `appUrlIntercepted`, only notifies
    /// it and cancels, so the app stays alive and routes to `redirectAppUrl` itself.
    /// Otherwise (no listener yet, or the main frame is not the app page, since Capacitor
    /// clears listeners on every main-frame navigation) reloads the WebView at `redirectAppUrl`.
    private func redirect(from urlString: String, to redirectAppUrl: String, isMainFrame: Bool) -> NSNumber? {
        // Query strings and fragments can carry session tokens, so keep them out of the log.
        let route = "\(UrlFilter.withoutQuery(urlString)) -> \(UrlFilter.withoutQuery(redirectAppUrl))"
        if hasListeners(Self.interceptedEvent) {
            CAPLog.print("NAIFilterGameUrlPlugin: Intercepted \(route)")
            notifyListeners(Self.interceptedEvent, data: [
                "url": urlString.lowercased(),
                "appUrl": redirectAppUrl,
                "isMainFrame": isMainFrame
            ])
            return NSNumber(value: true) // cancel the navigation, app stays alive
        }
        guard let url = URL(string: redirectAppUrl) else {
            return nil // malformed target; let capacitor policy decide
        }
        CAPLog.print("NAIFilterGameUrlPlugin: Redirect \(route)")
        webView?.load(URLRequest(url: url))
        return NSNumber(value: true)
    }
}
