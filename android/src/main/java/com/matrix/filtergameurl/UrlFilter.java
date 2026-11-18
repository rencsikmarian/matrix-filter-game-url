package com.matrix.filtergameurl;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.function.LongSupplier;

/**
 * The navigation rules, free of Android types so they run in plain JVM unit
 * tests. NAIFilterGameUrlPlugin feeds it main-frame URL changes and asks it
 * what to do with each navigation. Its methods are synchronized because the
 * plugin asks from the WebView's request thread as well as the UI thread.
 */
final class UrlFilter {

    /** Rule lists, all lowercased by the caller. See the README for each key. */
    static final class Config {

        List<String> blockedDomains = new ArrayList<>();
        List<String> allowedPrefixes = new ArrayList<>();
        // Blocked domains that are the app's own site; empty means all of them.
        List<String> appDomains = new ArrayList<>();
        List<String> passPaths = new ArrayList<>();
        List<String> openUrlParams = new ArrayList<>();
        List<String> blockedHostKeywords = new ArrayList<>();
        List<String> blockedPathSuffixes = new ArrayList<>();
        List<String> excludedReturnPaths = new ArrayList<>();
        List<String> gamePaths = new ArrayList<>();
        int historyLimit = 3;
        // How long after a game page is left a blocked navigation still counts as the game exiting.
        int gameExitGraceMs = 3000;
    }

    /** What to do with a navigation. */
    static final class Decision {

        enum Action {
            /** No rule matched: the usual Capacitor handling applies. */
            NONE,
            /** A pass path on a blocked domain: the URL loads. */
            PASS,
            /** Blocked: go to {@link #target} instead. */
            REDIRECT
        }

        final Action action;
        /** The app URL to go to instead; null unless the action is REDIRECT. */
        final String target;

        private Decision(Action action, String target) {
            this.action = action;
            this.target = target;
        }
    }

    private final String appUrl;
    private final String appUrlLower;
    private final Config config;
    // Milliseconds from a clock that only moves forward.
    private final LongSupplier clock;
    // Most-recent-first app-host pages the user actually visited.
    private final Deque<String> internalUrlHistory = new ArrayDeque<>();
    // Whether the last app-host page the main frame showed was a game page. Without
    // gamePaths there is nothing to tell games apart, so every page counts as one.
    private boolean onGamePage;
    // Until when a game page that was just left still counts as a running game. A game
    // often redirects to the site right after the app has already routed away from it.
    private long graceUntil = Long.MIN_VALUE;

    UrlFilter(String appUrl, Config config) {
        this(appUrl, config, () -> System.nanoTime() / 1_000_000L);
    }

    UrlFilter(String appUrl, Config config, LongSupplier clock) {
        this.appUrl = appUrl;
        this.appUrlLower = appUrl.toLowerCase(Locale.ROOT);
        this.config = withoutEmptyEntries(config);
        this.clock = clock;
        this.onGamePage = this.config.gamePaths.isEmpty();
    }

    /**
     * Call on every main-frame URL change, including SPA route changes. Tracks
     * whether the user is in a game and remembers the last page they were on,
     * so a later blocked navigation can return to it.
     */
    synchronized void onMainFrameUrl(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        // Pages off the app host (e.g. a game that took over the main frame) change
        // nothing: the user stays in the game until an app page says otherwise.
        if (!isAppUrl(lower)) {
            return;
        }
        boolean isGamePage = isGamePage(lower);
        if (!config.gamePaths.isEmpty()) {
            if (onGamePage && !isGamePage) {
                graceUntil = clock.getAsLong() + config.gameExitGraceMs;
            }
            onGamePage = isGamePage;
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

    /** Decides a navigation. */
    synchronized Decision decide(String url, String host, boolean isMainFrame) {
        // URLs without a host (about:, data:, mailto: ...) are never filtered.
        if (host == null || host.isEmpty()) {
            return new Decision(Decision.Action.NONE, null);
        }
        String lower = url.toLowerCase(Locale.ROOT);
        String lowerHost = host.toLowerCase(Locale.ROOT);
        // Launcher rules (open-url parameter, host keyword, path suffix) apply on any
        // host and always return to the last app page.
        boolean isLauncher = matchesLauncherRule(lower, lowerHost);

        String target;
        if (matchesDomain(lowerHost, config.blockedDomains)) {
            if (containsAny(lower, config.passPaths)) {
                return new Decision(Decision.Action.PASS, null);
            }
            // Outside a game a top-level link to the app's own site is a real link: open
            // the same page in the app. In a game it is the game exiting: go back. A
            // frame navigating itself always goes back.
            boolean opensInApp = isMainFrame && !isLauncher && !inGame() && isAppDomain(lowerHost);
            target = opensInApp ? appUrl + pathAndAfter(url) : lastInternalUrl();
        } else if (isLauncher) {
            target = lastInternalUrl();
        } else {
            return new Decision(Decision.Action.NONE, null);
        }
        // A URL is never sent to itself: reloading the return page passes through the
        // filter again on iOS, and would otherwise bounce forever.
        if (target.equalsIgnoreCase(url)) {
            return new Decision(Decision.Action.NONE, null);
        }
        return new Decision(Decision.Action.REDIRECT, target);
    }

    /** Most-recent-first return targets, for logging. */
    synchronized List<String> history() {
        return new ArrayList<>(internalUrlHistory);
    }

    /** Whether a blocked navigation currently counts as a game exiting. */
    synchronized boolean inGame() {
        return onGamePage || clock.getAsLong() < graceUntil;
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
     * Whether an Accept header is the one a browser sends when it loads a page or a
     * frame, rather than an image, a script or an XHR. Android only reports frame
     * loads as plain resource requests, and this header is what tells them apart.
     */
    static boolean isDocumentRequest(String accept) {
        if (accept == null) {
            return false;
        }
        String lower = accept.toLowerCase(Locale.ROOT);
        return lower.contains("text/html") && lower.contains("application/xhtml+xml");
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

    /** Whether the URL is on the app's own origin: the app URL, then "/", "?", "#" or nothing. */
    private boolean isAppUrl(String lower) {
        if (!lower.startsWith(appUrlLower)) {
            return false;
        }
        return lower.length() == appUrlLower.length() || "/?#".indexOf(lower.charAt(appUrlLower.length())) >= 0;
    }

    /** Whether the path of an app URL (query and fragment ignored) holds a game path as whole segments. */
    private boolean isGamePage(String lower) {
        String path = withoutQuery(lower.substring(appUrlLower.length()));
        for (String gamePath : config.gamePaths) {
            if (containsSegments(path, gamePath)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the path contains the entry ending at a "/" or at the end of the path. */
    private static boolean containsSegments(String path, String entry) {
        for (int index = path.indexOf(entry); index >= 0; index = path.indexOf(entry, index + 1)) {
            int end = index + entry.length();
            if (entry.endsWith("/") || end == path.length() || path.charAt(end) == '/') {
                return true;
            }
        }
        return false;
    }

    private String lastInternalUrl() {
        String first = internalUrlHistory.peekFirst();
        return first != null ? first : appUrl;
    }

    private boolean matchesLauncherRule(String lower, String lowerHost) {
        // Blocked domain inside an open-url parameter
        for (String param : config.openUrlParams) {
            int index = lower.indexOf(param);
            if (index >= 0 && containsAny(lower.substring(index + param.length()), config.blockedDomains)) {
                return true;
            }
        }
        // Blocked host keywords
        if (containsAny(lowerHost, config.blockedHostKeywords)) {
            return true;
        }
        // Blocked path suffixes
        for (String suffix : config.blockedPathSuffixes) {
            if (lower.endsWith("/" + suffix)) {
                return true;
            }
        }
        return false;
    }

    /** Whether links to this blocked host belong in the app: every blocked domain unless appDomains narrows it. */
    private boolean isAppDomain(String host) {
        return config.appDomains.isEmpty() || matchesDomain(host, config.appDomains);
    }

    /** Whether the host is one of the domains, bare or behind one of the allowed prefixes. */
    private boolean matchesDomain(String host, List<String> domains) {
        for (String domain : domains) {
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

    /** A copy of the config without empty entries, which would otherwise match every URL. */
    private static Config withoutEmptyEntries(Config config) {
        Config result = new Config();
        result.blockedDomains = withoutEmpty(config.blockedDomains);
        result.allowedPrefixes = withoutEmpty(config.allowedPrefixes);
        result.appDomains = withoutEmpty(config.appDomains);
        result.passPaths = withoutEmpty(config.passPaths);
        result.openUrlParams = withoutEmpty(config.openUrlParams);
        result.blockedHostKeywords = withoutEmpty(config.blockedHostKeywords);
        result.blockedPathSuffixes = withoutEmpty(config.blockedPathSuffixes);
        result.excludedReturnPaths = withoutEmpty(config.excludedReturnPaths);
        result.gamePaths = withoutEmpty(config.gamePaths);
        result.historyLimit = config.historyLimit;
        result.gameExitGraceMs = config.gameExitGraceMs;
        return result;
    }

    private static List<String> withoutEmpty(List<String> values) {
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                result.add(value);
            }
        }
        return result;
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
