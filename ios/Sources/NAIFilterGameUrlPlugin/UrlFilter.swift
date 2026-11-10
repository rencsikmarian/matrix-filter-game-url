import Foundation

/// Rule lists, all lowercased by the caller. See the README for each key.
struct UrlFilterConfig {
    var blockedDomains: [String] = []
    // Subdomain prefixes that also count as a blocked domain; the bare domain always matches.
    var allowedPrefixes: [String] = []
    var passPaths: [String] = []
    var openUrlParams: [String] = []
    var blockedHostKeywords: [String] = []
    var blockedPathSuffixes: [String] = []
    var excludedReturnPaths: [String] = []
    // App pages whose URL contains one of these are game pages; empty disables game detection.
    var gamePaths: [String] = []
    // How many recent internal (app-host) pages to remember; 0 disables tracking.
    var historyLimit = 3
}

/// The navigation rules, free of WebKit and Capacitor types so they run in plain
/// unit tests. NAIFilterGameUrlPlugin feeds it main-frame URL changes and asks it
/// where each navigation should go.
final class UrlFilter {
    private let appUrl: String
    private let config: UrlFilterConfig
    /// Most-recent-first app-host pages the user actually visited.
    private(set) var history: [String] = []
    /// Whether the last app-host page the main frame showed was a game page. Without
    /// gamePaths there is nothing to tell games apart, so every page counts as one.
    private(set) var inGame: Bool

    init(appUrl: String, config: UrlFilterConfig) {
        self.appUrl = appUrl
        self.config = config
        self.inGame = config.gamePaths.isEmpty
    }

    /// Where a blocked navigation returns to: the newest internal page, else the app URL.
    private var lastInternalUrl: String { history.first ?? appUrl }

    /// Call on every main-frame URL change, including SPA route changes. Tracks
    /// whether the user is in a game and remembers the last page they were on,
    /// so a later blocked navigation can return to it.
    func mainFrameUrlChanged(_ url: URL) {
        let original = url.absoluteString // keep original casing for the reload
        let lower = original.lowercased()
        // Pages off the app host (e.g. a game that took over the main frame) change
        // nothing: the user stays in the game until an app page says otherwise.
        guard lower.hasPrefix(appUrl.lowercased()) else { return }
        let isGamePage = config.gamePaths.contains(where: { lower.contains($0) })
        if !config.gamePaths.isEmpty {
            inGame = isGamePage
        }
        // A game page is never a return target: returning to it would reopen the game.
        guard config.historyLimit > 0, !isGamePage else { return }
        guard !config.excludedReturnPaths.contains(where: { lower.contains($0) }) else { return }
        guard history.first != original else { return } // dedup consecutive
        history.insert(original, at: 0)
        if history.count > config.historyLimit {
            history.removeLast()
        }
    }

    /// Decides a navigation: nil lets the URL load, otherwise the app URL to go to instead.
    func redirectTarget(for url: URL, isMainFrame: Bool) -> String? {
        guard let host = url.host?.lowercased() else { return nil }
        let lower = url.absoluteString.lowercased()

        // 1. Blocked domain: redirect, unless a pass path exempts the URL
        let isBlockedDomain = config.blockedDomains.contains { domain in
            host == domain || config.allowedPrefixes.contains { prefix in host == prefix + domain }
        }
        if isBlockedDomain {
            if config.passPaths.contains(where: { lower.contains($0) }) {
                return nil
            }
            // Outside a game a top-level link to the site is a real link: open the
            // same page in the app. In a game it is the game exiting: go back.
            // Iframes always go back, so an embedded frame cannot reroute the app.
            return !inGame && isMainFrame ? appUrl + Self.pathAndAfter(url.absoluteString) : lastInternalUrl
        }

        // 2. Blocked domain inside an open-url parameter, on any host
        for param in config.openUrlParams where lower.contains(param) {
            let paramValue = lower.components(separatedBy: param).dropFirst().joined(separator: param)
            if config.blockedDomains.contains(where: { paramValue.contains($0) }) {
                return lastInternalUrl
            }
        }

        // 3. Blocked host keywords, on any host
        if config.blockedHostKeywords.contains(where: { host.contains($0) }) {
            return lastInternalUrl
        }

        // 4. Blocked path suffixes, on any URL
        if config.blockedPathSuffixes.contains(where: { lower.hasSuffix("/\($0)") }) {
            return lastInternalUrl
        }

        return nil
    }

    /// Everything after the host: path, query and fragment, in their original casing.
    private static func pathAndAfter(_ url: String) -> String {
        let afterScheme = url.range(of: "://")?.upperBound ?? url.startIndex
        guard let start = url[afterScheme...].firstIndex(where: { "/?#".contains($0) }) else { return "" }
        return String(url[start...])
    }
}
