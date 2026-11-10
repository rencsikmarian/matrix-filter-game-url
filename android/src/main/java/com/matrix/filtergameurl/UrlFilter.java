package com.matrix.filtergameurl;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * The navigation rules, free of Android types so they run in plain JVM unit
 * tests. NAIFilterGameUrlPlugin feeds it main-frame URL changes and asks it
 * where each navigation should go.
 */
final class UrlFilter {

    /** Rule lists, all lowercased by the caller. See the README for each key. */
    static final class Config {

        List<String> blockedDomains = new ArrayList<>();
        List<String> allowedPrefixes = new ArrayList<>();
        List<String> passPaths = new ArrayList<>();
        List<String> openUrlParams = new ArrayList<>();
        List<String> blockedHostKeywords = new ArrayList<>();
        List<String> blockedPathSuffixes = new ArrayList<>();
        List<String> excludedReturnPaths = new ArrayList<>();
        List<String> gamePaths = new ArrayList<>();
        int historyLimit = 3;
    }

    private final String appUrl;
    private final Config config;
    // Most-recent-first app-host pages the user actually visited.
    private final Deque<String> internalUrlHistory = new ArrayDeque<>();
    // Whether the last app-host page the main frame showed was a game page. Without
    // gamePaths there is nothing to tell games apart, so every page counts as one.
    private boolean inGame;

    UrlFilter(String appUrl, Config config) {
        this.appUrl = appUrl;
        this.config = config;
        this.inGame = config.gamePaths.isEmpty();
    }

    /**
     * Call on every main-frame URL change, including SPA route changes. Tracks
     * whether the user is in a game and remembers the last page they were on,
     * so a later blocked navigation can return to it.
     */
    void onMainFrameUrl(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        // Pages off the app host (e.g. a game that took over the main frame) change
        // nothing: the user stays in the game until an app page says otherwise.
        if (!lower.startsWith(appUrl.toLowerCase(Locale.ROOT))) {
            return;
        }
        boolean isGamePage = containsAny(lower, config.gamePaths);
        if (!config.gamePaths.isEmpty()) {
            inGame = isGamePage;
        }
        // A game page is never a return target: returning to it would reopen the game.
        if (config.historyLimit <= 0 || isGamePage || containsAny(lower, config.excludedReturnPaths)) {
            return;
        }
        if (url.equals(internalUrlHistory.peekFirst())) {
            return; // dedup consecutive
        }
        internalUrlHistory.addFirst(url);
        while (internalUrlHistory.size() > config.historyLimit) {
            internalUrlHistory.removeLast();
        }
    }

    /**
     * Decides a navigation.
     *
     * @return null to let the URL load, otherwise the app URL to go to instead
     */
    String redirectTarget(String url, String host, boolean isMainFrame) {
        String lower = url.toLowerCase(Locale.ROOT);
        String lowerHost = host == null ? "" : host.toLowerCase(Locale.ROOT);

        // 1. Blocked domain: redirect, unless a pass path exempts the URL
        if (matchesBlockedDomain(lowerHost)) {
            if (containsAny(lower, config.passPaths)) {
                return null;
            }
            // Outside a game a top-level link to the site is a real link: open the
            // same page in the app. In a game it is the game exiting: go back.
            // Iframes always go back, so an embedded frame cannot reroute the app.
            return !inGame && isMainFrame ? appUrl + pathAndAfter(url) : lastInternalUrl();
        }

        // 2. Blocked domain inside an open-url parameter, on any host
        for (String param : config.openUrlParams) {
            int index = lower.indexOf(param);
            if (index >= 0 && containsAny(lower.substring(index + param.length()), config.blockedDomains)) {
                return lastInternalUrl();
            }
        }

        // 3. Blocked host keywords, on any host
        if (containsAny(lowerHost, config.blockedHostKeywords)) {
            return lastInternalUrl();
        }

        // 4. Blocked path suffixes, on any URL
        for (String suffix : config.blockedPathSuffixes) {
            if (lower.endsWith("/" + suffix)) {
                return lastInternalUrl();
            }
        }

        return null;
    }

    /** Most-recent-first return targets, for logging. */
    List<String> history() {
        return new ArrayList<>(internalUrlHistory);
    }

    boolean inGame() {
        return inGame;
    }

    /** The URL up to its path, for logging. */
    static String withoutQuery(String url) {
        for (int i = 0; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c == '?' || c == '#') {
                return url.substring(0, i);
            }
        }
        return url;
    }

    /**
     * Everything after the host: path, query and fragment, in their original casing.
     * Leading slashes and backslashes collapse to one slash, so an app that navigates
     * to the result can never read it as a protocol-relative URL ("//other.host/x").
     */
    private static String pathAndAfter(String url) {
        int schemeEnd = url.indexOf("://");
        int i = schemeEnd < 0 ? 0 : schemeEnd + 3;
        while (i < url.length() && "/\\?#".indexOf(url.charAt(i)) < 0) {
            i++;
        }
        int pathStart = i;
        while (i < url.length() && (url.charAt(i) == '/' || url.charAt(i) == '\\')) {
            i++;
        }
        return (i > pathStart ? "/" : "") + url.substring(i);
    }

    private String lastInternalUrl() {
        String first = internalUrlHistory.peekFirst();
        return first != null ? first : appUrl;
    }

    private boolean matchesBlockedDomain(String host) {
        for (String domain : config.blockedDomains) {
            if (host.equals(domain)) {
                return true;
            }
            for (String prefix : config.allowedPrefixes) {
                if (host.equals(prefix + domain)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsAny(String value, List<String> needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
