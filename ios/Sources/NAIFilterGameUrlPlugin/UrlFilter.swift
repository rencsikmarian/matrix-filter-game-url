import Foundation

/// Rule lists, all lowercased by the caller. See the README for each key.
struct UrlFilterConfig {
    var blockedDomains: [String] = []
    // Subdomain prefixes that also count as a blocked domain; the bare domain always matches.
    var allowedPrefixes: [String] = []
    // Blocked domains that are the app's own site; empty means all of them.
    var appDomains: [String] = []
    var passPaths: [String] = []
    var openUrlParams: [String] = []
    var blockedHostKeywords: [String] = []
    var blockedPathSuffixes: [String] = []
    var excludedReturnPaths: [String] = []
    // App pages whose path holds one of these as whole segments are game pages; empty disables game detection.
    var gamePaths: [String] = []
    // How many recent internal (app-host) pages to remember; 0 disables tracking.
    var historyLimit = 3
    // How long after a game page is left a blocked navigation still counts as the game exiting.
    var gameExitGraceMs = 3000

    /// A copy without empty entries, which would otherwise match every URL.
    func withoutEmptyEntries() -> UrlFilterConfig {
        func kept(_ values: [String]) -> [String] {
            return values.filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }
        }
        var result = self
        result.blockedDomains = kept(blockedDomains)
        result.allowedPrefixes = kept(allowedPrefixes)
        result.appDomains = kept(appDomains)
        result.passPaths = kept(passPaths)
        result.openUrlParams = kept(openUrlParams)
        result.blockedHostKeywords = kept(blockedHostKeywords)
        result.blockedPathSuffixes = kept(blockedPathSuffixes)
        result.excludedReturnPaths = kept(excludedReturnPaths)
        result.gamePaths = kept(gamePaths)
        return result
    }
}

/// What to do with a navigation.
enum UrlDecision: Equatable {
    /// No rule matched: the usual Capacitor handling applies.
    case noMatch
    /// A pass path on a blocked domain: the URL loads.
    case pass
    /// Blocked: go to this app URL instead.
    case redirect(String)
}

/// The navigation rules, free of WebKit and Capacitor types so they run in plain
/// unit tests. NAIFilterGameUrlPlugin feeds it main-frame URL changes and asks it
/// what to do with each navigation.
final class UrlFilter {
    private let appUrl: String
    private let appUrlLower: String
    private let config: UrlFilterConfig
    /// Milliseconds from a clock that only moves forward.
    private let clock: () -> Int64
    /// Most-recent-first app-host pages the user actually visited.
    private(set) var history: [String] = []
    /// Whether the last app-host page the main frame showed was a game page. Without
    /// gamePaths there is nothing to tell games apart, so every page counts as one.
    private var onGamePage: Bool
    /// Until when a game page that was just left still counts as a running game. A game
    /// often redirects to the site right after the app has already routed away from it.
    private var graceUntil = Int64.min

    init(
        appUrl: String,
        config: UrlFilterConfig,
        clock: @escaping () -> Int64 = { Int64(ProcessInfo.processInfo.systemUptime * 1000) }
    ) {
        self.appUrl = appUrl
        self.appUrlLower = appUrl.lowercased()
        self.config = config.withoutEmptyEntries()
        self.clock = clock
        self.onGamePage = self.config.gamePaths.isEmpty
    }

    /// Whether a blocked navigation currently counts as a game exiting.
    var inGame: Bool { onGamePage || clock() < graceUntil }

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
        guard isAppUrl(lower) else { return }
        let isGamePage = isGamePage(lower)
        if !config.gamePaths.isEmpty {
            if onGamePage && !isGamePage {
                graceUntil = clock() + Int64(config.gameExitGraceMs)
            }
            onGamePage = isGamePage
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

    /// Decides a navigation.
    func decide(for url: URL, isMainFrame: Bool) -> UrlDecision {
        // URLs without a host (about:, data:, mailto: ...) are never filtered.
        guard let host = url.host?.lowercased(), !host.isEmpty else { return .noMatch }
        let original = url.absoluteString
        let lower = original.lowercased()
        // Launcher rules (open-url parameter, host keyword, path suffix) apply on any
        // host and always return to the last app page.
        let isLauncher = matchesLauncherRule(lower, host: host)

        let target: String
        if matchesDomain(host, config.blockedDomains) {
            if config.passPaths.contains(where: { lower.contains($0) }) {
                return .pass
            }
            // Outside a game a top-level link to the app's own site is a real link: open
            // the same page in the app. In a game it is the game exiting: go back. A
            // frame navigating itself always goes back.
            let opensInApp = isMainFrame && !isLauncher && !inGame && isAppDomain(host)
            target = opensInApp ? appUrl + Self.pathAndAfter(original) : lastInternalUrl
        } else if isLauncher {
            target = lastInternalUrl
        } else {
            return .noMatch
        }
        // A URL is never sent to itself: reloading the return page passes through the
        // filter again, and would otherwise bounce forever.
        if target.caseInsensitiveCompare(original) == .orderedSame {
            return .noMatch
        }
        return .redirect(target)
    }

    /// The URL up to its path, for logging.
    static func withoutQuery(_ url: String) -> String {
        return String(url.prefix(while: { $0 != "?" && $0 != "#" }))
    }

    /// The scheme, host and port of a URL: the prefix shared by every page served from it.
    static func origin(of url: URL) -> String? {
        guard let scheme = url.scheme, let host = url.host, !host.isEmpty else { return nil }
        let port = url.port.map { ":\($0)" } ?? ""
        return "\(scheme)://\(host)\(port)"
    }

    /// Everything after the host: path, query and fragment, in their original casing.
    /// Leading slashes and backslashes collapse to one slash, so an app that navigates
    /// to the result can never read it as a protocol-relative URL ("//other.host/x").
    private static func pathAndAfter(_ url: String) -> String {
        let afterScheme = url.range(of: "://")?.upperBound ?? url.startIndex
        let afterHost = url[afterScheme...].drop(while: { !"/\\?#".contains($0) })
        let rest = afterHost.drop(while: { $0 == "/" || $0 == "\\" })
        return (rest.count < afterHost.count ? "/" : "") + rest
    }

    /// Whether the URL is on the app's own origin: the app URL, then "/", "?", "#" or nothing.
    private func isAppUrl(_ lower: String) -> Bool {
        guard lower.hasPrefix(appUrlLower) else { return false }
        guard let next = lower.dropFirst(appUrlLower.count).first else { return true }
        return "/?#".contains(next)
    }

    /// Whether the path of an app URL (query and fragment ignored) holds a game path as whole segments.
    private func isGamePage(_ lower: String) -> Bool {
        let path = Self.withoutQuery(String(lower.dropFirst(appUrlLower.count)))
        return config.gamePaths.contains { Self.containsSegments(path, $0) }
    }

    /// Whether the path contains the entry ending at a "/" or at the end of the path.
    private static func containsSegments(_ path: String, _ entry: String) -> Bool {
        var searchStart = path.startIndex
        while let found = path.range(of: entry, range: searchStart..<path.endIndex) {
            if entry.hasSuffix("/") || found.upperBound == path.endIndex || path[found.upperBound] == "/" {
                return true
            }
            searchStart = path.index(after: found.lowerBound)
        }
        return false
    }

    private func matchesLauncherRule(_ lower: String, host: String) -> Bool {
        // Blocked domain inside an open-url parameter
        for param in config.openUrlParams where lower.contains(param) {
            let paramValue = lower.components(separatedBy: param).dropFirst().joined(separator: param)
            if config.blockedDomains.contains(where: { paramValue.contains($0) }) {
                return true
            }
        }
        // Blocked host keywords
        if config.blockedHostKeywords.contains(where: { host.contains($0) }) {
            return true
        }
        // Blocked path suffixes
        return config.blockedPathSuffixes.contains(where: { lower.hasSuffix("/\($0)") })
    }

    /// Whether links to this blocked host belong in the app: every blocked domain unless appDomains narrows it.
    private func isAppDomain(_ host: String) -> Bool {
        return config.appDomains.isEmpty || matchesDomain(host, config.appDomains)
    }

    /// Whether the host is one of the domains, bare or behind one of the allowed prefixes.
    private func matchesDomain(_ host: String, _ domains: [String]) -> Bool {
        return domains.contains { domain in
            host == domain || config.allowedPrefixes.contains { prefix in host == prefix + domain }
        }
    }
}
