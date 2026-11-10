import XCTest
@testable import NAIFilterGameUrlPlugin

class UrlFilterTests: XCTestCase {
    private let appUrl = "capacitor://localhost"

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
        return UrlFilter(appUrl: appUrl, config: config())
    }

    private func visit(_ filter: UrlFilter, _ url: String) {
        filter.mainFrameUrlChanged(URL(string: url)!)
    }

    /// A main-frame navigation, the common case.
    private func target(_ filter: UrlFilter, _ url: String, isMainFrame: Bool = true) -> String? {
        return filter.redirectTarget(for: URL(string: url)!, isMainFrame: isMainFrame)
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
        let filter = UrlFilter(appUrl: appUrl, config: config)
        visit(filter, "capacitor://localhost/de/slots")

        XCTAssertEqual(target(filter, "https://www.admiralbet.de/"), "capacitor://localhost")
    }

    // MARK: - gamePaths: blocked-domain links open in the app unless a game is running

    private func gameAwareFilter() -> UrlFilter {
        var config = config()
        config.gamePaths = ["/cash", "/free"]
        return UrlFilter(appUrl: appUrl, config: config)
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

    func testLeavingTheGamePageSwitchesBackToOpeningLinksInApp() {
        let filter = gameAwareFilter()
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
        let filter = UrlFilter(appUrl: appUrl, config: config)
        visit(filter, "capacitor://localhost/de/games/book-of-ra/cash")

        XCTAssertEqual(target(filter, "https://www.admiralbet.de/de/home"), "capacitor://localhost")
    }
}
