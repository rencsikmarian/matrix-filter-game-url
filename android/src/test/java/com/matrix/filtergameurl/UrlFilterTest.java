package com.matrix.filtergameurl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.Arrays;
import org.junit.Test;

public class UrlFilterTest {

    private static final String APP_URL = "https://localhost";

    private static UrlFilter.Config config() {
        UrlFilter.Config config = new UrlFilter.Config();
        config.blockedDomains = Arrays.asList("admiralbet.de", "stargames.de");
        config.allowedPrefixes = Arrays.asList("www.", "staging.");
        config.passPaths = Arrays.asList("/ichatclient/");
        config.openUrlParams = Arrays.asList("openurl?url=");
        config.blockedHostKeywords = Arrays.asList("lobbyiframelaunch");
        config.blockedPathSuffixes = Arrays.asList("lobbyiframelaunch");
        config.excludedReturnPaths = Arrays.asList("/404");
        config.historyLimit = 3;
        return config;
    }

    private static UrlFilter filter() {
        return new UrlFilter(APP_URL, config());
    }

    /** A main-frame navigation, the common case. */
    private static String target(UrlFilter filter, String url) {
        return target(filter, url, URI.create(url).getHost(), true);
    }

    /** Where the navigation is sent instead, or null when the URL is left to load. */
    private static String target(UrlFilter filter, String url, String host, boolean isMainFrame) {
        return filter.decide(url, host, isMainFrame).target;
    }

    private static UrlFilter.Decision.Action action(UrlFilter filter, String url) {
        return filter.decide(url, URI.create(url).getHost(), true).action;
    }

    @Test
    public void blockedDomainReturnsToLastVisitedAppPage() {
        UrlFilter filter = filter();
        filter.onMainFrameUrl("https://localhost/de/slots");
        filter.onMainFrameUrl("https://localhost/de/Promotions?tab=New");

        assertEquals("https://localhost/de/Promotions?tab=New", target(filter, "https://www.admiralbet.de/"));
    }

    @Test
    public void blockedDomainWithoutHistoryReturnsToAppUrl() {
        assertEquals("https://localhost", target(filter(), "https://admiralbet.de/de/home"));
    }

    @Test
    public void blockedDomainMatchesOnlyConfiguredPrefixes() {
        UrlFilter filter = filter();

        assertEquals("https://localhost", target(filter, "https://staging.admiralbet.de/"));
        assertNull(target(filter, "https://m.admiralbet.de/"));
        assertNull(target(filter, "https://notadmiralbet.de/"));
    }

    @Test
    public void blockedDomainMatchIgnoresCase() {
        UrlFilter filter = filter();

        assertEquals("https://localhost", target(filter, "https://WWW.AdmiralBet.de/", "WWW.AdmiralBet.de", true));
    }

    @Test
    public void passPathOnBlockedDomainLoadsNormally() {
        assertNull(target(filter(), "https://www.admiralbet.de/iChatClient/index.html"));
    }

    @Test
    public void openUrlParamWithBlockedDomainReturnsToLastAppPage() {
        UrlFilter filter = filter();
        filter.onMainFrameUrl("https://localhost/de/slots");

        assertEquals("https://localhost/de/slots", target(filter, "https://games.example.com/openurl?url=https://www.stargames.de/"));
        assertNull(target(filter, "https://games.example.com/openurl?url=https://other.com/"));
    }

    @Test
    public void blockedHostKeywordReturnsToLastAppPage() {
        UrlFilter filter = filter();
        filter.onMainFrameUrl("https://localhost/de/slots");

        assertEquals("https://localhost/de/slots", target(filter, "https://lobbyiframelaunch.example.com/x"));
    }

    @Test
    public void blockedPathSuffixReturnsToLastAppPage() {
        UrlFilter filter = filter();
        filter.onMainFrameUrl("https://localhost/de/slots");

        assertEquals("https://localhost/de/slots", target(filter, "https://games.example.com/a/LobbyIframeLaunch"));
        assertNull(target(filter, "https://games.example.com/a/lobbyiframelaunch/more"));
    }

    @Test
    public void unrelatedUrlLoadsNormally() {
        assertNull(target(filter(), "https://games.example.com/play?id=1"));
    }

    @Test
    public void excludedReturnPathIsNeverAReturnTarget() {
        UrlFilter filter = filter();
        filter.onMainFrameUrl("https://localhost/de/slots");
        filter.onMainFrameUrl("https://localhost/de/404");

        assertEquals("https://localhost/de/slots", target(filter, "https://www.admiralbet.de/"));
    }

    @Test
    public void pagesOffTheAppHostAreNeverAReturnTarget() {
        UrlFilter filter = filter();
        filter.onMainFrameUrl("https://localhost/de/slots");
        filter.onMainFrameUrl("https://games.example.com/play");

        assertEquals("https://localhost/de/slots", target(filter, "https://www.admiralbet.de/"));
    }

    @Test
    public void historyLimitZeroAlwaysReturnsToAppUrl() {
        UrlFilter.Config config = config();
        config.historyLimit = 0;
        UrlFilter filter = new UrlFilter(APP_URL, config);
        filter.onMainFrameUrl("https://localhost/de/slots");

        assertEquals("https://localhost", target(filter, "https://www.admiralbet.de/"));
    }

    // --- gamePaths: blocked-domain links open in the app unless a game is running ---

    // Fake clock in milliseconds, so tests decide when the grace window after a game ends.
    private long now = 1_000_000;

    private static UrlFilter.Config gameConfig() {
        UrlFilter.Config config = config();
        config.gamePaths = Arrays.asList("/cash", "/free");
        return config;
    }

    private UrlFilter gameAwareFilter() {
        return gameAwareFilter(gameConfig());
    }

    private UrlFilter gameAwareFilter(UrlFilter.Config config) {
        return new UrlFilter(APP_URL, config, () -> now);
    }

    @Test
    public void outsideGameBlockedDomainLinkOpensSamePathInApp() {
        UrlFilter filter = gameAwareFilter();
        filter.onMainFrameUrl("https://localhost/de/slots");

        assertEquals(
            "https://localhost/De/Promotions?utm=X&id=7#Top",
            target(filter, "https://www.admiralbet.de/De/Promotions?utm=X&id=7#Top")
        );
    }

    @Test
    public void outsideGameBlockedDomainRootOpensAppUrl() {
        UrlFilter filter = gameAwareFilter();
        filter.onMainFrameUrl("https://localhost/de/slots");

        assertEquals("https://localhost", target(filter, "https://www.admiralbet.de"));
        assertEquals("https://localhost?utm=x", target(filter, "https://www.admiralbet.de?utm=x"));
    }

    @Test
    public void beforeAnyPageIsSeenUserCountsAsOutsideGame() {
        assertEquals("https://localhost/de/slots", target(gameAwareFilter(), "https://www.admiralbet.de/de/slots"));
    }

    @Test
    public void outsideGameIframeOnBlockedDomainReturnsToLastAppPage() {
        UrlFilter filter = gameAwareFilter();
        filter.onMainFrameUrl("https://localhost/de/slots");

        assertEquals("https://localhost/de/slots", target(filter, "https://www.admiralbet.de/de/promotions", "www.admiralbet.de", false));
    }

    @Test
    public void outsideGamePassPathStillLoadsNormally() {
        assertNull(target(gameAwareFilter(), "https://www.admiralbet.de/ichatclient/index.html"));
    }

    @Test
    public void outsideGameLauncherRulesStillReturnToLastAppPage() {
        UrlFilter filter = gameAwareFilter();
        filter.onMainFrameUrl("https://localhost/de/slots");

        assertEquals("https://localhost/de/slots", target(filter, "https://lobbyiframelaunch.example.com/x"));
        assertEquals("https://localhost/de/slots", target(filter, "https://games.example.com/a/lobbyiframelaunch"));
        assertEquals("https://localhost/de/slots", target(filter, "https://games.example.com/openurl?url=https://www.stargames.de/de/x"));
    }

    @Test
    public void inGameBlockedDomainReturnsToLastPageBeforeTheGame() {
        UrlFilter filter = gameAwareFilter();
        filter.onMainFrameUrl("https://localhost/de/slots");
        filter.onMainFrameUrl("https://localhost/de/games/Book-Of-Ra/Cash");

        assertEquals("https://localhost/de/slots", target(filter, "https://www.admiralbet.de/de/home?x=1"));
    }

    @Test
    public void inGameIsDetectedForEveryGamePath() {
        UrlFilter filter = gameAwareFilter();
        filter.onMainFrameUrl("https://localhost/de/slots");
        filter.onMainFrameUrl("https://localhost/de/games/book-of-ra/free");

        assertEquals("https://localhost/de/slots", target(filter, "https://www.admiralbet.de/de/home"));
    }

    @Test
    public void leavingTheGamePageSwitchesBackToOpeningLinksInAppAfterTheGraceWindow() {
        UrlFilter filter = gameAwareFilter();
        filter.onMainFrameUrl("https://localhost/de/games/book-of-ra/cash");
        filter.onMainFrameUrl("https://localhost/de/lobby");
        now += 3000;

        assertEquals("https://localhost/de/promotions", target(filter, "https://www.admiralbet.de/de/promotions"));
    }

    // A game often redirects to the site just after the app has already routed off the game page.
    @Test
    public void gameExitArrivingJustAfterTheGamePageIsLeftReturnsToLastAppPage() {
        UrlFilter filter = gameAwareFilter();
        filter.onMainFrameUrl("https://localhost/de/slots");
        filter.onMainFrameUrl("https://localhost/de/games/book-of-ra/cash");
        filter.onMainFrameUrl("https://localhost/de/slots");
        now += 2999;

        assertEquals("https://localhost/de/slots", target(filter, "https://www.admiralbet.de/de/home"));
    }

    @Test
    public void graceWindowOfZeroEndsTheGameAtOnce() {
        UrlFilter.Config config = gameConfig();
        config.gameExitGraceMs = 0;
        UrlFilter filter = gameAwareFilter(config);
        filter.onMainFrameUrl("https://localhost/de/games/book-of-ra/cash");
        filter.onMainFrameUrl("https://localhost/de/lobby");

        assertEquals("https://localhost/de/promotions", target(filter, "https://www.admiralbet.de/de/promotions"));
    }

    // --- appDomains: only the app's own site opens in the app ---

    @Test
    public void onlyAppDomainLinksOpenInTheApp() {
        UrlFilter.Config config = gameConfig();
        config.appDomains = Arrays.asList("admiralbet.de");
        UrlFilter filter = gameAwareFilter(config);
        filter.onMainFrameUrl("https://localhost/de/slots");

        assertEquals("https://localhost/de/promotions", target(filter, "https://www.admiralbet.de/de/promotions"));
        assertEquals("https://localhost/de/slots", target(filter, "https://www.stargames.de/slots/book-of-ra"));
    }

    @Test
    public void appDomainsMatchTheSamePrefixesAsBlockedDomains() {
        UrlFilter.Config config = gameConfig();
        config.appDomains = Arrays.asList("admiralbet.de");
        UrlFilter filter = gameAwareFilter(config);
        filter.onMainFrameUrl("https://localhost/de/slots");

        assertEquals("https://localhost/de/x", target(filter, "https://admiralbet.de/de/x"));
        assertEquals("https://localhost/de/x", target(filter, "https://www.admiralbet.de/de/x"));
        assertEquals("https://localhost/de/x", target(filter, "https://staging.admiralbet.de/de/x"));
    }

    @Test
    public void withoutAppDomainsEveryBlockedDomainOpensInTheApp() {
        UrlFilter filter = gameAwareFilter();
        filter.onMainFrameUrl("https://localhost/de/slots");

        assertEquals("https://localhost/slots/book-of-ra", target(filter, "https://www.stargames.de/slots/book-of-ra"));
    }

    // --- a pass path is told apart from a URL no rule matched ---

    @Test
    public void passPathIsReportedApartFromANonMatch() {
        UrlFilter filter = filter();

        assertEquals(UrlFilter.Decision.Action.PASS, action(filter, "https://www.admiralbet.de/iChatClient/index.html"));
        assertEquals(UrlFilter.Decision.Action.NONE, action(filter, "https://games.example.com/play?id=1"));
        assertEquals(UrlFilter.Decision.Action.REDIRECT, action(filter, "https://www.admiralbet.de/de/home"));
    }

    // --- Android iframe documents are recognised by the browser's navigation Accept header ---

    @Test
    public void browserNavigationAcceptHeaderIsADocumentRequest() {
        assertTrue(
            UrlFilter.isDocumentRequest("text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
        );
        assertTrue(UrlFilter.isDocumentRequest("Text/HTML,Application/XHTML+XML,*/*;q=0.8"));
    }

    @Test
    public void subresourceAcceptHeadersAreNotDocumentRequests() {
        assertFalse(UrlFilter.isDocumentRequest("*/*"));
        assertFalse(UrlFilter.isDocumentRequest("image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8"));
        assertFalse(UrlFilter.isDocumentRequest("application/json, text/plain, */*"));
        // An XHR that merely asks for HTML (e.g. jQuery's dataType "html") is not a navigation.
        assertFalse(UrlFilter.isDocumentRequest("text/html, */*; q=0.01"));
        assertFalse(UrlFilter.isDocumentRequest(null));
    }

    @Test
    public void gameThatTakesOverTheMainFrameStaysInGame() {
        UrlFilter filter = gameAwareFilter();
        filter.onMainFrameUrl("https://localhost/de/slots");
        filter.onMainFrameUrl("https://localhost/de/games/book-of-ra/cash");
        filter.onMainFrameUrl("https://games.example.com/play?session=1");

        assertEquals("https://localhost/de/slots", target(filter, "https://www.admiralbet.de/de/home"));
    }

    @Test
    public void inGameIsTrackedEvenWhenHistoryIsDisabled() {
        UrlFilter.Config config = config();
        config.gamePaths = Arrays.asList("/cash");
        config.historyLimit = 0;
        UrlFilter filter = new UrlFilter(APP_URL, config);
        filter.onMainFrameUrl("https://localhost/de/games/book-of-ra/cash");

        assertEquals("https://localhost", target(filter, "https://www.admiralbet.de/de/home"));
    }

    @Test
    public void outsideGameLinkPathCannotBecomeProtocolRelative() {
        UrlFilter filter = gameAwareFilter();

        assertEquals("https://localhost/evil.example.com/x?a=1", target(filter, "https://www.admiralbet.de//evil.example.com/x?a=1"));
        assertEquals(
            "https://localhost/evil.example.com",
            target(filter, "https://www.admiralbet.de/\\/evil.example.com", "www.admiralbet.de", true)
        );
        assertEquals(
            "https://localhost/evil.example.com",
            target(filter, "https://www.admiralbet.de\\evil.example.com", "www.admiralbet.de", true)
        );
    }

    // --- app pages are recognised by origin, not by string prefix ---

    @Test
    public void lookalikeHostIsNeverAReturnTarget() {
        UrlFilter filter = filter();
        filter.onMainFrameUrl("https://localhost/de/slots");
        filter.onMainFrameUrl("https://localhost.evil.example/phish");
        filter.onMainFrameUrl("https://localhost:8443/other");

        assertEquals("https://localhost/de/slots", target(filter, "https://www.admiralbet.de/"));
    }

    // --- gamePaths match whole path segments ---

    @Test
    public void gamePathDoesNotMatchInsideALongerSegment() {
        UrlFilter filter = gameAwareFilter();

        filter.onMainFrameUrl("https://localhost/de/cashier");
        assertEquals("https://localhost/de/terms", target(filter, "https://www.admiralbet.de/de/terms"));

        filter.onMainFrameUrl("https://localhost/de/promotions/free-spins");
        assertEquals("https://localhost/de/terms", target(filter, "https://www.admiralbet.de/de/terms"));
    }

    @Test
    public void gamePathIgnoresQueryAndFragment() {
        UrlFilter filter = gameAwareFilter();

        filter.onMainFrameUrl("https://localhost/de/login?returnUrl=/de/games/book-of-ra/cash");
        assertEquals("https://localhost/de/terms", target(filter, "https://www.admiralbet.de/de/terms"));

        filter.onMainFrameUrl("https://localhost/de/lobby#/free");
        assertEquals("https://localhost/de/terms", target(filter, "https://www.admiralbet.de/de/terms"));
    }

    @Test
    public void gamePathMatchesAheadOfFurtherSegments() {
        UrlFilter filter = gameAwareFilter();
        filter.onMainFrameUrl("https://localhost/de/slots");

        filter.onMainFrameUrl("https://localhost/de/games/book-of-ra/cash/demo");
        assertEquals("https://localhost/de/slots", target(filter, "https://www.admiralbet.de/de/home"));

        filter.onMainFrameUrl("https://localhost/de/games/book-of-ra/free/");
        assertEquals("https://localhost/de/slots", target(filter, "https://www.admiralbet.de/de/home"));
    }

    @Test
    public void pageThatOnlyResemblesAGamePathIsAReturnTarget() {
        UrlFilter filter = gameAwareFilter();
        filter.onMainFrameUrl("https://localhost/de/slots");
        filter.onMainFrameUrl("https://localhost/de/cashier");
        filter.onMainFrameUrl("https://localhost/de/games/book-of-ra/cash");

        assertEquals("https://localhost/de/cashier", target(filter, "https://www.admiralbet.de/de/home"));
    }

    // --- empty config entries are ignored ---

    @Test
    public void emptyBlockedHostKeywordBlocksNothing() {
        UrlFilter.Config config = config();
        config.blockedHostKeywords = Arrays.asList("");

        assertNull(target(new UrlFilter(APP_URL, config), "https://games.example.com/play"));
    }

    @Test
    public void emptyBlockedPathSuffixBlocksNothing() {
        UrlFilter.Config config = config();
        config.blockedPathSuffixes = Arrays.asList("");

        assertNull(target(new UrlFilter(APP_URL, config), "https://games.example.com/"));
    }

    @Test
    public void emptyPassPathExemptsNothing() {
        UrlFilter.Config config = config();
        config.passPaths = Arrays.asList("");

        assertEquals("https://localhost", target(new UrlFilter(APP_URL, config), "https://www.admiralbet.de/de/home"));
    }

    @Test
    public void emptyGamePathCountsAsNoGamePaths() {
        UrlFilter.Config config = config();
        config.gamePaths = Arrays.asList("");
        UrlFilter filter = new UrlFilter(APP_URL, config);
        filter.onMainFrameUrl("https://localhost/de/slots");

        // Without game paths every page counts as a game: return to the last app page.
        assertEquals("https://localhost/de/slots", target(filter, "https://www.admiralbet.de/de/home"));
    }

    // --- launcher rules win over opening a link in the app ---

    @Test
    public void outsideGameLauncherUrlOnBlockedDomainReturnsToLastAppPage() {
        UrlFilter filter = gameAwareFilter();
        filter.onMainFrameUrl("https://localhost/de/slots");

        assertEquals("https://localhost/de/slots", target(filter, "https://www.admiralbet.de/de/lobbyiframelaunch"));
        assertEquals("https://localhost/de/slots", target(filter, "https://www.admiralbet.de/openurl?url=https://www.stargames.de/"));
    }

    // Reloading the return page goes through the filter again on iOS; it must not bounce forever.
    @Test
    public void navigationToItsOwnReturnTargetLoadsNormally() {
        UrlFilter filter = filter();
        filter.onMainFrameUrl("https://localhost/de/lobbyiframelaunch");

        assertNull(target(filter, "https://localhost/de/lobbyiframelaunch"));
    }

    @Test
    public void urlWithoutHostLoadsNormally() {
        assertNull(target(filter(), "about:blank#x/lobbyiframelaunch", null, true));
    }

    @Test
    public void withoutQueryDropsQueryAndFragment() {
        assertEquals("https://games.example.com/play", UrlFilter.withoutQuery("https://games.example.com/play?token=s3cret#t"));
        assertEquals("https://localhost/de/slots", UrlFilter.withoutQuery("https://localhost/de/slots#token=s3cret?x"));
        assertEquals("https://localhost/de/slots", UrlFilter.withoutQuery("https://localhost/de/slots"));
    }
}
