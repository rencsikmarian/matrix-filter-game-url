package com.matrix.filtergameurl;

import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import com.getcapacitor.Bridge;
import com.getcapacitor.BridgeWebViewClient;
import com.getcapacitor.Logger;
import com.getcapacitor.PluginConfig;
import com.getcapacitor.PluginHandle;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class NAIFilterGameUrlPlugin extends BridgeWebViewClient {

    private static final String TAG = "NAIFilterGameUrlPlugin";
    private static final String CONFIG_KEY = "NAIFilterGameUrl";
    private static final List<String> DEFAULT_ALLOWED_PREFIXES = Arrays.asList("www.", "staging.", "beta.");

    // Holds the rules and the navigation state (visited pages, in a game or not).
    private final UrlFilter filter;
    private final Bridge bridge;

    public NAIFilterGameUrlPlugin(Bridge bridge) {
        this(bridge, null);
    }

    /**
     * The domains parameter is a programmatic override for the
     * "blockedDomains" config key; all other lists always come from config.
     */
    public NAIFilterGameUrlPlugin(Bridge bridge, List<String> domains) {
        super(bridge);
        this.bridge = bridge;

        PluginConfig config = bridge.getConfig().getPluginConfiguration(CONFIG_KEY);

        UrlFilter.Config rules = new UrlFilter.Config();
        if (domains != null && !domains.isEmpty()) {
            rules.blockedDomains = lowercased(domains);
        } else {
            rules.blockedDomains = configList(config, "blockedDomains");
        }
        // Subdomain prefixes that also count as a blocked domain; the bare domain always matches.
        rules.allowedPrefixes =
            config.getArray("allowedPrefixes") == null ? DEFAULT_ALLOWED_PREFIXES : configList(config, "allowedPrefixes");
        rules.appDomains = configList(config, "appDomains");
        rules.passPaths = configList(config, "passPaths");
        rules.openUrlParams = configList(config, "openUrlParams");
        rules.blockedHostKeywords = configList(config, "blockedHostKeywords");
        rules.blockedPathSuffixes = configList(config, "blockedPathSuffixes");
        rules.excludedReturnPaths = configList(config, "excludedReturnPaths");
        rules.gamePaths = configList(config, "gamePaths");
        rules.historyLimit = Math.max(0, config.getInt("historyLimit", 3));
        rules.gameExitGraceMs = Math.max(0, config.getInt("gameExitGraceMs", 3000));

        // The origin the app is really served from. Unlike the scheme and hostname
        // config keys, it also follows server.url (e.g. live reload).
        String appUrl = bridge.getLocalUrl();
        if (appUrl == null || appUrl.isEmpty()) {
            appUrl = bridge.getScheme() + "://" + bridge.getHost();
        }
        this.filter = new UrlFilter(appUrl, rules);

        Logger.debug(TAG, "Created. App URL: " + appUrl + ", blocked domains: " + rules.blockedDomains);
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        Uri url = request.getUrl();
        UrlFilter.Decision decision = filter.decide(url.toString(), url.getHost(), request.isForMainFrame());
        switch (decision.action) {
            case REDIRECT:
                redirect(view, url.toString(), decision.target, request.isForMainFrame());
                return true;
            case PASS:
                return false; // a pass path always loads in the WebView
            default:
                return super.shouldOverrideUrlLoading(view, request);
        }
    }

    // Android never offers an http(s) page loading inside a frame to shouldOverrideUrlLoading,
    // so those are caught here, where every request passes. This runs on a WebView request
    // thread, not the UI thread.
    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        if (isFramePage(request)) {
            Uri url = request.getUrl();
            UrlFilter.Decision decision = filter.decide(url.toString(), url.getHost(), false);
            if (decision.action == UrlFilter.Decision.Action.REDIRECT) {
                view.post(() -> redirect(view, url.toString(), decision.target, false));
                // The frame gets an empty page instead of the blocked one.
                return new WebResourceResponse("text/html", "UTF-8", new ByteArrayInputStream(new byte[0]));
            }
        }
        return super.shouldInterceptRequest(view, request);
    }

    // SPA route changes (history.pushState) never reach shouldOverrideUrlLoading, and neither
    // does the initial app load, so record the main-frame URL whenever it changes instead.
    @Override
    public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
        super.doUpdateVisitedHistory(view, url, isReload);
        if (url == null) {
            return;
        }
        filter.onMainFrameUrl(url);
        if (Logger.shouldLog()) {
            // Query strings and fragments can carry session tokens, so keep them out of logcat.
            List<String> history = new ArrayList<>();
            for (String page : filter.history()) {
                history.add(UrlFilter.withoutQuery(page));
            }
            Logger.debug(TAG, "Main frame URL: " + UrlFilter.withoutQuery(url) + ", in game: " + filter.inGame() + ", history: " + history);
        }
    }

    /** Whether the request is an http(s) page being loaded into a frame, going by its Accept header. */
    private static boolean isFramePage(WebResourceRequest request) {
        if (request.isForMainFrame() || !"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String scheme = request.getUrl().getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            return false;
        }
        Map<String, String> headers = request.getRequestHeaders();
        if (headers == null) {
            return false;
        }
        for (Map.Entry<String, String> header : headers.entrySet()) {
            if ("Accept".equalsIgnoreCase(header.getKey())) {
                return UrlFilter.isDocumentRequest(header.getValue());
            }
        }
        return false;
    }

    private static List<String> configList(PluginConfig config, String key) {
        String[] values = config.getArray(key);
        if (values == null) {
            return new ArrayList<>();
        }
        return lowercased(Arrays.asList(values));
    }

    private static List<String> lowercased(List<String> values) {
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value != null) {
                result.add(value.toLowerCase(Locale.ROOT));
            }
        }
        return result;
    }

    /**
     * Blocks a navigation. If JS is listening for appUrlIntercepted, only notifies it,
     * so the app stays alive and routes to `to` itself. Otherwise (no listener yet, or
     * the page isn't the app, since Capacitor clears listeners on every page load)
     * reloads the WebView at `to`. Must run on the UI thread.
     */
    private void redirect(WebView view, String from, String to, boolean isMainFrame) {
        // Query strings and fragments can carry session tokens, so keep them out of logcat.
        String route = UrlFilter.withoutQuery(from) + " -> " + UrlFilter.withoutQuery(to);
        NAIFilterGameUrlEventsPlugin events = eventsPlugin();
        if (events != null && events.notifyIntercepted(from.toLowerCase(Locale.ROOT), to, isMainFrame)) {
            Logger.debug(TAG, "Intercepted " + route);
            return; // the navigation is cancelled, the app stays alive
        }
        // A frame blocked on the very page it would return to: reloading that page
        // would only load the frame again.
        if (!isMainFrame && to.equals(view.getUrl())) {
            Logger.debug(TAG, "Blocked frame " + route);
            return;
        }
        Logger.debug(TAG, "Redirect " + route);
        view.loadUrl(to);
    }

    // Null when the events plugin isn't registered (the app hasn't run `npx cap sync`).
    private NAIFilterGameUrlEventsPlugin eventsPlugin() {
        PluginHandle handle = bridge.getPlugin("NAIFilterGameUrl");
        if (handle == null || !(handle.getInstance() instanceof NAIFilterGameUrlEventsPlugin)) {
            return null;
        }
        return (NAIFilterGameUrlEventsPlugin) handle.getInstance();
    }
}
