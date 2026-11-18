import XCTest
@testable import NAIFilterGameUrlPlugin

class UrlFilterTests: XCTestCase {
    private let appUrl = "capacitor://localhost"
    // Fake clock in milliseconds, so tests decide when the grace window after a game ends.
    private var now: Int64 = 1_000_000

    private func config() -> UrlFilterConfig {
        var config = UrlFilterConfig()
        config.blockedDomains = ["admiralbet.de", "stargames.de"]
        config.allowedPrefixes = ["www.", "staging."]
        config.passPaths = ["/ichatclient/"]
        config.openUrlParams = ["openurl?url="]
        config.blockedHostKeywords = ["lobbyiframelaunch"]
        config.blockedPathSuffixes = ["lobbyiframelaunch"]
        config.excludedReturnPaths = ["/404"]
        config.historyLimit = 3
        return config
    }

    private func filter() -> UrlFilter {
        return filter(config())
    }

    private func filter(_ config: UrlFilterConfig) -> UrlFilter {
        return UrlFilter(appUrl: appUrl, config: config, clock: { self.now })
    }

    private func visit(_ filter: UrlFilter, _ url: String) {
        filter.mainFrameUrlChanged(URL(string: url)!)
    }

    /// Where the navigation is sent instead, or nil when the URL is left to load.
    private func target(_ filter: UrlFilter, _ url: String, isMainFrame: Bool = true) -> String? {
        if case .redirect(let target) = filter.decide(for: URL(string: url)!, isMainFrame: isMainFrame) {
            return target
        }
        return nil
    }

    func testBlockedDomainReturnsToLastVisitedAppPage() {
        let filter = filter()
        visit(filter, "capacitor://localhost/de/slots")
        visit(filter, "capacitor://localhost/de/Promotions?tab=New")

        XCTAssertEqual(target(filter, "https://www.admiralbet.de/"), "capacitor://localhost/de/Promotions?tab=New")
    }

    func testBlockedDomainWithoutHistoryReturnsToAppUrl() {
        XCTAssertEqual(target(filter(), "https://admiralbet.de/de/home"), "capacitor://localhost")
    }

    func testBlockedDomainMatchesOnlyConfiguredPrefixes() {
        let filter = filter()

        XCTAssertEqual(target(filter, "https://staging.admiralbet.de/"), "capacitor://localhost")
        XCTAssertNil(target(filter, "https://m.admiralbet.de/"))
        XCTAssertNil(target(filter, "https://notadmiralbet.de/"))
    }

    func testBlockedDomainMatchIgnoresCase() {
        XCTAssertEqual(target(filter(), "https://WWW.AdmiralBet.de/"), "capacitor://localhost")
    }

    func testPassPathOnBlockedDomainLoadsNormally() {
        XCTAssertNil(target(filter(), "https://www.admiralbet.de/iChatClient/index.html"))
    }

    func testOpenUrlParamWithBlockedDomainReturnsToLastAppPage() {
        let filter = filter()
        visit(filter, "capacitor://localhost/de/slots")

        XCTAssertEqual(
            target(filter, "https://games.example.com/openurl?url=https://www.stargames.de/"),
            "capacitor://localhost/de/slots"
        )
        XCTAssertNil(target(filter, "https://games.example.com/openurl?url=https://other.com/"))
    }

    func testBlockedHostKeywordReturnsToLastAppPage() {
        let filter = filter()
        visit(filter, "capacitor://localhost/de/slots")

        XCTAssertEqual(target(filter, "https://lobbyiframelaunch.example.com/x"), "capacitor://localhost/de/slots")
    }

    func testBlockedPathSuffixReturnsToLastAppPage() {
        let filter = filter()
        visit(filter, "capacitor://localhost/de/slots")

        XCTAssertEqual(target(filter, "https://games.example.com/a/LobbyIframeLaunch"), "capacitor://localhost/de/slots")
        XCTAssertNil(target(filter, "https://games.example.com/a/lobbyiframelaunch/more"))
    }

    func testUnrelatedUrlLoadsNormally() {
        XCTAssertNil(target(filter(), "https://games.example.com/play?id=1"))
    }

    func testUrlWithoutHostLoadsNormally() {
        XCTAssertNil(target(filter(), "about:blank"))
    }

    func testExcludedReturnPathIsNeverAReturnTarget() {
        let filter = filter()
        visit(filter, "capacitor://localhost/de/slots")
        visit(filter, "capacitor://localhost/de/404")

        XCTAssertEqual(target(filter, "https://www.admiralbet.de/"), "capacitor://localhost/de/slots")
    }

    func testPagesOffTheAppHostAreNeverAReturnTarget() {
        let filter = filter()
        visit(filter, "capacitor://localhost/de/slots")
        visit(filter, "https://games.example.com/play")

        XCTAssertEqual(target(filter, "https://www.admiralbet.de/"), "capacitor://localhost/de/slots")
    }

    func testHistoryLimitZeroAlwaysReturnsToAppUrl() {
        var config = config()
        config.historyLimit = 0
        let filter = filter(config)
        visit(filter, "capacitor://localhost/de/slots")

        XCTAssertEqual(target(filter, "https://www.admiralbet.de/"), "capacitor://localhost")
    }

    // MARK: - gamePaths: blocked-domain links open in the app unless a game is running

    private func gameConfig() -> UrlFilterConfig {
        var config = config()
        config.gamePaths = ["/cash", "/free"]
        return config
    }

    private func gameAwareFilter() -> UrlFilter {
        return filter(gameConfig())
    }

    func testOutsideGameBlockedDomainLinkOpensSamePathInApp() {
        let filter = gameAwareFilter()
        visit(filter, "capacitor://localhost/de/slots")

        XCTAssertEqual(
            target(filter, "https://www.admiralbet.de/De/Promotions?utm=X&id=7#Top"),
            "capacitor://localhost/De/Promotions?utm=X&id=7#Top"
        )
    }

    func testOutsideGameBlockedDomainRootOpensAppUrl() {
        let filter = gameAwareFilter()
        visit(filter, "capacitor://localhost/de/slots")

        XCTAssertEqual(target(filter, "https://www.admiralbet.de"), "capacitor://localhost")
        XCTAssertEqual(target(filter, "https://www.admiralbet.de?utm=x"), "capacitor://localhost?utm=x")
    }

    func testBeforeAnyPageIsSeenUserCountsAsOutsideGame() {
        XCTAssertEqual(
            target(gameAwareFilter(), "https://www.admiralbet.de/de/slots"),
            "capacitor://localhost/de/slots"
        )
    }

    func testOutsideGameIframeOnBlockedDomainReturnsToLastAppPage() {
        let filter = gameAwareFilter()
        visit(filter, "capacitor://localhost/de/slots")

        XCTAssertEqual(
            target(filter, "https://www.admiralbet.de/de/promotions", isMainFrame: false),
            "capacitor://localhost/de/slots"
        )
    }

    func testOutsideGamePassPathStillLoadsNormally() {
        XCTAssertNil(target(gameAwareFilter(), "https://www.admiralbet.de/ichatclient/index.html"))
    }

    func testOutsideGameLauncherRulesStillReturnToLastAppPage() {
        let filter = gameAwareFilter()
        visit(filter, "capacitor://localhost/de/slots")

        XCTAssertEqual(target(filter, "https://lobbyiframelaunch.example.com/x"), "capacitor://localhost/de/slots")
        XCTAssertEqual(target(filter, "https://games.example.com/a/lobbyiframelaunch"), "capacitor://localhost/de/slots")
        XCTAssertEqual(
            target(filter, "https://games.example.com/openurl?url=https://www.stargames.de/de/x"),
            "capacitor://localhost/de/slots"
        )
    }

    func testInGameBlockedDomainReturnsToLastPageBeforeTheGame() {
        let filter = gameAwareFilter()
        visit(filter, "capacitor://localhost/de/slots")
        visit(filter, "capacitor://localhost/de/games/Book-Of-Ra/Cash")

        XCTAssertEqual(target(filter, "https://www.admiralbet.de/de/home?x=1"), "capacitor://localhost/de/slots")
    }

    func testInGameIsDetectedForEveryGamePath() {
        let filter = gameAwareFilter()
        visit(filter, "capacitor://localhost/de/slots")
        visit(filter, "capacitor://localhost/de/games/book-of-ra/free")

        XCTAssertEqual(target(filter, "https://www.admiralbet.de/de/home"), "capacitor://localhost/de/slots")
    }

    func testLeavingTheGamePageSwitchesBackToOpeningLinksInAppAfterTheGraceWindow() {
        let filter = gameAwareFilter()
        visit(filter, "capacitor://localhost/de/games/book-of-ra/cash")
        visit(filter, "capacitor://localhost/de/lobby")
        now += 3000

        XCTAssertEqual(
            target(filter, "https://www.admiralbet.de/de/promotions"),
            "capacitor://localhost/de/promotions"
        )
    }

    // A game often redirects to the site just after the app has already routed off the game page.
    func testGameExitArrivingJustAfterTheGamePageIsLeftReturnsToLastAppPage() {
        let filter = gameAwareFilter()
        visit(filter, "capacitor://localhost/de/slots")
        visit(filter, "capacitor://localhost/de/games/book-of-ra/cash")
        visit(filter, "capacitor://localhost/de/slots")
        now += 2999

        XCTAssertEqual(target(filter, "https://www.admiralbet.de/de/home"), "capacitor://localhost/de/slots")
    }

    func testGraceWindowOfZeroEndsTheGameAtOnce() {
        var config = gameConfig()
        config.gameExitGraceMs = 0
        let filter = filter(config)
        visit(filter, "capacitor://localhost/de/games/book-of-ra/cash")
        visit(filter, "capacitor://localhost/de/lobby")

        XCTAssertEqual(
            target(filter, "https://www.admiralbet.de/de/promotions"),
            "capacitor://localhost/de/promotions"
        )
    }

    func testGameThatTakesOverTheMainFrameStaysInGame() {
        let filter = gameAwareFilter()
        visit(filter, "capacitor://localhost/de/slots")
        visit(filter, "capacitor://localhost/de/games/book-of-ra/cash")
        visit(filter, "https://games.example.com/play?session=1")

        XCTAssertEqual(target(filter, "https://www.admiralbet.de/de/home"), "capacitor://localhost/de/slots")
    }

    func testInGameIsTrackedEvenWhenHistoryIsDisabled() {
        var config = config()
        config.gamePaths = ["/cash"]
        config.historyLimit = 0
        let filter = filter(config)
        visit(filter, "capacitor://localhost/de/games/book-of-ra/cash")

        XCTAssertEqual(target(filter, "https://www.admiralbet.de/de/home"), "capacitor://localhost")
    }

    func testOutsideGameLinkPathCannotBecomeProtocolRelative() {
        let filter = gameAwareFilter()

        XCTAssertEqual(
            target(filter, "https://www.admiralbet.de//evil.example.com/x?a=1"),
            "capacitor://localhost/evil.example.com/x?a=1"
        )
        XCTAssertEqual(target(filter, "https://www.admiralbet.de///evil.example.com"), "capacitor://localhost/evil.example.com")
    }

    // MARK: - gamePaths match whole path segments

    func testGamePathDoesNotMatchInsideALongerSegment() {
        let filter = gameAwareFilter()

        visit(filter, "capacitor://localhost/de/cashier")
        XCTAssertEqual(target(filter, "https://www.admiralbet.de/de/terms"), "capacitor://localhost/de/terms")

        visit(filter, "capacitor://localhost/de/promotions/free-spins")
        XCTAssertEqual(target(filter, "https://www.admiralbet.de/de/terms"), "capacitor://localhost/de/terms")
    }

    func testGamePathIgnoresQueryAndFragment() {
        let filter = gameAwareFilter()

        visit(filter, "capacitor://localhost/de/login?returnUrl=/de/games/book-of-ra/cash")
        XCTAssertEqual(target(filter, "https://www.admiralbet.de/de/terms"), "capacitor://localhost/de/terms")

        visit(filter, "capacitor://localhost/de/lobby#/free")
        XCTAssertEqual(target(filter, "https://www.admiralbet.de/de/terms"), "capacitor://localhost/de/terms")
    }

    func testGamePathMatchesAheadOfFurtherSegments() {
        let filter = gameAwareFilter()
        visit(filter, "capacitor://localhost/de/slots")

        visit(filter, "capacitor://localhost/de/games/book-of-ra/cash/demo")
        XCTAssertEqual(target(filter, "https://www.admiralbet.de/de/home"), "capacitor://localhost/de/slots")

        visit(filter, "capacitor://localhost/de/games/book-of-ra/free/")
        XCTAssertEqual(target(filter, "https://www.admiralbet.de/de/home"), "capacitor://localhost/de/slots")
    }

    func testPageThatOnlyResemblesAGamePathIsAReturnTarget() {
        let filter = gameAwareFilter()
        visit(filter, "capacitor://localhost/de/slots")
        visit(filter, "capacitor://localhost/de/cashier")
        visit(filter, "capacitor://localhost/de/games/book-of-ra/cash")

        XCTAssertEqual(target(filter, "https://www.admiralbet.de/de/home"), "capacitor://localhost/de/cashier")
    }

    // MARK: - appDomains: only the app's own site opens in the app

    func testOnlyAppDomainLinksOpenInTheApp() {
        var config = gameConfig()
        config.appDomains = ["admiralbet.de"]
        let filter = filter(config)
        visit(filter, "capacitor://localhost/de/slots")

        XCTAssertEqual(
            target(filter, "https://www.admiralbet.de/de/promotions"),
            "capacitor://localhost/de/promotions"
        )
        XCTAssertEqual(target(filter, "https://www.stargames.de/slots/book-of-ra"), "capacitor://localhost/de/slots")
    }

    func testAppDomainsMatchTheSamePrefixesAsBlockedDomains() {
        var config = gameConfig()
        config.appDomains = ["admiralbet.de"]
        let filter = filter(config)
        visit(filter, "capacitor://localhost/de/slots")

        XCTAssertEqual(target(filter, "https://admiralbet.de/de/x"), "capacitor://localhost/de/x")
        XCTAssertEqual(target(filter, "https://www.admiralbet.de/de/x"), "capacitor://localhost/de/x")
        XCTAssertEqual(target(filter, "https://staging.admiralbet.de/de/x"), "capacitor://localhost/de/x")
    }

    func testWithoutAppDomainsEveryBlockedDomainOpensInTheApp() {
        let filter = gameAwareFilter()
        visit(filter, "capacitor://localhost/de/slots")

        XCTAssertEqual(
            target(filter, "https://www.stargames.de/slots/book-of-ra"),
            "capacitor://localhost/slots/book-of-ra"
        )
    }

    // MARK: - app pages are recognised by origin, not by string prefix

    func testLookalikeHostIsNeverAReturnTarget() {
        let filter = filter()
        visit(filter, "capacitor://localhost/de/slots")
        visit(filter, "capacitor://localhost.evil.example/phish")
        visit(filter, "capacitor://localhost:8443/other")

        XCTAssertEqual(target(filter, "https://www.admiralbet.de/"), "capacitor://localhost/de/slots")
    }

    // MARK: - empty config entries are ignored

    func testEmptyBlockedHostKeywordBlocksNothing() {
        var config = config()
        config.blockedHostKeywords = [""]

        XCTAssertNil(target(filter(config), "https://games.example.com/play"))
    }

    func testEmptyBlockedPathSuffixBlocksNothing() {
        var config = config()
        config.blockedPathSuffixes = [""]

        XCTAssertNil(target(filter(config), "https://games.example.com/"))
    }

    func testEmptyPassPathExemptsNothing() {
        var config = config()
        config.passPaths = [""]

        XCTAssertEqual(target(filter(config), "https://www.admiralbet.de/de/home"), "capacitor://localhost")
    }

    func testEmptyGamePathCountsAsNoGamePaths() {
        var config = config()
        config.gamePaths = [""]
        let filter = filter(config)
        visit(filter, "capacitor://localhost/de/slots")

        // Without game paths every page counts as a game: return to the last app page.
        XCTAssertEqual(target(filter, "https://www.admiralbet.de/de/home"), "capacitor://localhost/de/slots")
    }

    // MARK: - launcher rules win over opening a link in the app

    func testOutsideGameLauncherUrlOnBlockedDomainReturnsToLastAppPage() {
        let filter = gameAwareFilter()
        visit(filter, "capacitor://localhost/de/slots")

        XCTAssertEqual(target(filter, "https://www.admiralbet.de/de/lobbyiframelaunch"), "capacitor://localhost/de/slots")
        XCTAssertEqual(
            target(filter, "https://www.admiralbet.de/openurl?url=https://www.stargames.de/"),
            "capacitor://localhost/de/slots"
        )
    }

    // Reloading the return page goes through the filter again; it must not bounce forever.
    func testNavigationToItsOwnReturnTargetLoadsNormally() {
        let filter = filter()
        visit(filter, "capacitor://localhost/de/lobbyiframelaunch")

        XCTAssertNil(target(filter, "capacitor://localhost/de/lobbyiframelaunch"))
    }

    // MARK: - a pass path is told apart from a URL no rule matched

    func testPassPathIsReportedApartFromANonMatch() {
        let filter = filter()

        XCTAssertEqual(
            filter.decide(for: URL(string: "https://www.admiralbet.de/iChatClient/index.html")!, isMainFrame: true),
            .pass
        )
        XCTAssertEqual(
            filter.decide(for: URL(string: "https://games.example.com/play?id=1")!, isMainFrame: true),
            .noMatch
        )
    }

    // MARK: - helpers

    func testWithoutQueryDropsQueryAndFragment() {
        XCTAssertEqual(
            UrlFilter.withoutQuery("https://games.example.com/play?token=s3cret#t"),
            "https://games.example.com/play"
        )
        XCTAssertEqual(
            UrlFilter.withoutQuery("capacitor://localhost/de/slots#token=s3cret?x"),
            "capacitor://localhost/de/slots"
        )
        XCTAssertEqual(UrlFilter.withoutQuery("capacitor://localhost/de/slots"), "capacitor://localhost/de/slots")
    }

    // The app URL is the origin the WebView really serves the app from.
    func testOriginKeepsSchemeHostAndPortOnly() {
        XCTAssertEqual(UrlFilter.origin(of: URL(string: "capacitor://localhost")!), "capacitor://localhost")
        XCTAssertEqual(UrlFilter.origin(of: URL(string: "ionic://localhost/")!), "ionic://localhost")
        XCTAssertEqual(
            UrlFilter.origin(of: URL(string: "http://192.168.0.5:8100/index.html?x=1")!),
            "http://192.168.0.5:8100"
        )
        XCTAssertNil(UrlFilter.origin(of: URL(string: "about:blank")!))
    }
}
