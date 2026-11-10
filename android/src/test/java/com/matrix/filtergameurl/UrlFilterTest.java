package com.matrix.filtergameurl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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
        return filter.redirectTarget(url, URI.create(url).getHost(), true);
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

        assertEquals("https://localhost", filter.redirectTarget("https://WWW.AdmiralBet.de/", "WWW.AdmiralBet.de", true));
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

    private static UrlFilter gameAwareFilter() {
        UrlFilter.Config config = config();
        config.gamePaths = Arrays.asList("/cash", "/free");
        return new UrlFilter(APP_URL, config);
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

        assertEquals(
            "https://localhost/de/slots",
            filter.redirectTarget("https://www.admiralbet.de/de/promotions", "www.admiralbet.de", false)
        );
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
    public void leavingTheGamePageSwitchesBackToOpeningLinksInApp() {
        UrlFilter filter = gameAwareFilter();
        filter.onMainFrameUrl("https://localhost/de/games/book-of-ra/cash");
        filter.onMainFrameUrl("https://localhost/de/lobby");

        assertEquals("https://localhost/de/promotions", target(filter, "https://www.admiralbet.de/de/promotions"));
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
}
