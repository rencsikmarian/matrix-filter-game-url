package com.matrix.filtergameurl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.util.Log;

import com.getcapacitor.Bridge;
import com.getcapacitor.BridgeWebViewClient;
import com.getcapacitor.PluginConfig;
import com.getcapacitor.PluginHandle;

public class NAIFilterGameUrlPlugin extends BridgeWebViewClient {

  private static final String TAG = "NAIFilterGameUrlPlugin";
  private static final String CONFIG_KEY = "NAIFilterGameUrl";
  private static final String DEFAULT_SCHEME = "https";
  private static final String DEFAULT_HOSTNAME = "localhost:8100";
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
    rules.allowedPrefixes = config.getArray("allowedPrefixes") == null
      ? DEFAULT_ALLOWED_PREFIXES
      : configList(config, "allowedPrefixes");
    rules.passPaths = configList(config, "passPaths");
    rules.openUrlParams = configList(config, "openUrlParams");
    rules.blockedHostKeywords = configList(config, "blockedHostKeywords");
    rules.blockedPathSuffixes = configList(config, "blockedPathSuffixes");
    rules.excludedReturnPaths = configList(config, "excludedReturnPaths");
    rules.gamePaths = configList(config, "gamePaths");
    rules.historyLimit = Math.max(0, config.getInt("historyLimit", 3));

    String scheme = bridge.getScheme();
    String hostname = bridge.getHost();
    if (scheme == null || scheme.isEmpty()) {
      scheme = DEFAULT_SCHEME;
    }
    if (hostname == null || hostname.isEmpty()) {
      hostname = DEFAULT_HOSTNAME;
    }
    String appUrl = scheme + "://" + hostname;
    this.filter = new UrlFilter(appUrl, rules);

    Log.d(TAG, "Created. App URL: " + appUrl + ", blocked domains: " + rules.blockedDomains);
  }

  @Override
  public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
    Uri url = request.getUrl();
    String target = filter.redirectTarget(url.toString(), url.getHost(), request.isForMainFrame());
    if (target == null) {
      return super.shouldOverrideUrlLoading(view, request);
    }
    return redirect(view, request, url.toString().toLowerCase(Locale.ROOT), target);
  }

  // SPA route changes (history.pushState) never reach shouldOverrideUrlLoading, and neither
  // does the initial app load, so record the main-frame URL whenever it changes instead.
  @Override
  public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
    super.doUpdateVisitedHistory(view, url, isReload);
    if (url != null) {
      filter.onMainFrameUrl(url);
      Log.d(TAG, "Main frame URL: " + url + ", in game: " + filter.inGame() + ", history: " + filter.history());
    }
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
   * Blocks the navigation. If JS is listening for appUrlIntercepted, only notifies it
   * and cancels, so the app stays alive and routes to `to` itself. Otherwise (no
   * listener yet, or the page isn't the app, since Capacitor clears listeners on every
   * page load) reloads the WebView at `to`.
   */
  private boolean redirect(WebView view, WebResourceRequest request, String from, String to) {
    NAIFilterGameUrlEventsPlugin events = eventsPlugin();
    if (events != null && events.notifyIntercepted(from, to, request.isForMainFrame())) {
      Log.d(TAG, "Intercepted " + from + " -> " + to);
      return true; // cancel the navigation, app stays alive
    }
    Log.d(TAG, "Redirect from: " + from + " to: " + to);
    view.loadUrl(to);
    return true;
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
