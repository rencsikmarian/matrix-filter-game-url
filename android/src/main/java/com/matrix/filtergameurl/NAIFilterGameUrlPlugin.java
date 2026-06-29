package com.matrix.filtergameurl;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.util.Log;

import com.getcapacitor.Bridge;
import com.getcapacitor.BridgeWebViewClient;
import com.getcapacitor.PluginConfig;

public class NAIFilterGameUrlPlugin extends BridgeWebViewClient {

  private static final String TAG = "NAIFilterGameUrlPlugin";
  private static final String CONFIG_KEY = "NAIFilterGameUrl";
  private static final String DEFAULT_SCHEME = "https";
  private static final String DEFAULT_HOSTNAME = "localhost:8100";
  private static final String[] ALLOWED_PREFIXES = {"", "www.", "staging.", "beta."};

  private final List<String> blockedDomains;
  private final List<String> passPaths;
  private final List<String> openUrlParams;
  private final List<String> blockedHostKeywords;
  private final List<String> blockedPathSuffixes;
  private final List<String> excludedReturnPaths;
  private final int historyLimit;
  private final Deque<String> internalUrlHistory = new ArrayDeque<>();
  private final String appUrl;

  public NAIFilterGameUrlPlugin(Bridge bridge) {
    this(bridge, null);
  }

  /**
   * The domains parameter is a programmatic override for the
   * "blockedDomains" config key; all other lists always come from config.
   */
  public NAIFilterGameUrlPlugin(Bridge bridge, List<String> domains) {
    super(bridge);

    PluginConfig config = bridge.getConfig().getPluginConfiguration(CONFIG_KEY);

    if (domains != null && !domains.isEmpty()) {
      this.blockedDomains = lowercased(domains);
    } else {
      this.blockedDomains = configList(config, "blockedDomains");
    }
    this.passPaths = configList(config, "passPaths");
    this.openUrlParams = configList(config, "openUrlParams");
    this.blockedHostKeywords = configList(config, "blockedHostKeywords");
    this.blockedPathSuffixes = configList(config, "blockedPathSuffixes");
    this.excludedReturnPaths = configList(config, "excludedReturnPaths");
    this.historyLimit = Math.max(0, config.getInt("historyLimit", 3));

    String scheme = bridge.getScheme();
    String hostname = bridge.getHost();
    if (scheme == null || scheme.isEmpty()) {
      scheme = DEFAULT_SCHEME;
    }
    if (hostname == null || hostname.isEmpty()) {
      hostname = DEFAULT_HOSTNAME;
    }
    this.appUrl = scheme + "://" + hostname;

    Log.d(TAG, "Created. App URL: " + this.appUrl + ", blocked domains: " + this.blockedDomains);
  }

  @Override
  public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
    Uri url = request.getUrl();
    String urlString = url.toString().toLowerCase(Locale.ROOT);
    String host = url.getHost();
    host = host == null ? "" : host.toLowerCase(Locale.ROOT);

    // 1. Blocked domain: redirect, unless a pass path exempts the URL
    if (matchesBlockedDomain(host)) {
      if (containsAny(urlString, passPaths)) {
        Log.d(TAG, "Pass path allowed: " + urlString);
        recordIfInternal(request, url.toString());
        return false;
      }
      return redirect(view, urlString, lastInternalUrl());
    }

    // 2. Blocked domain inside an open-url parameter, on any host
    for (String param : openUrlParams) {
      int index = urlString.indexOf(param);
      if (index >= 0) {
        String paramValue = urlString.substring(index + param.length());
        if (containsAny(paramValue, blockedDomains)) {
          return redirect(view, urlString, lastInternalUrl());
        }
      }
    }

    // 3. Blocked host keywords, on any host
    for (String keyword : blockedHostKeywords) {
      if (host.contains(keyword)) {
        return redirect(view, urlString, lastInternalUrl());
      }
    }

    // 4. Blocked path suffixes, on any URL
    for (String suffix : blockedPathSuffixes) {
      if (urlString.endsWith("/" + suffix)) {
        return redirect(view, urlString, lastInternalUrl());
      }
    }

    recordIfInternal(request, url.toString());
    return super.shouldOverrideUrlLoading(view, request);
  }

  private boolean matchesBlockedDomain(String host) {
    for (String domain : blockedDomains) {
      for (String prefix : ALLOWED_PREFIXES) {
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

  private void recordIfInternal(WebResourceRequest request, String originalUrl) {
    if (historyLimit <= 0 || !request.isForMainFrame()) {
      return;
    }
    String lower = originalUrl.toLowerCase(Locale.ROOT);
    if (!lower.startsWith(appUrl.toLowerCase(Locale.ROOT))) {
      return;
    }
    if (containsAny(lower, excludedReturnPaths)) {
      return;
    }
    if (originalUrl.equals(internalUrlHistory.peekFirst())) {
      return; // dedup consecutive
    }
    internalUrlHistory.addFirst(originalUrl);
    while (internalUrlHistory.size() > historyLimit) {
      internalUrlHistory.removeLast();
    }
    Log.d(TAG, "Recorded internal URL. History: " + internalUrlHistory);
  }

  private String lastInternalUrl() {
    String first = internalUrlHistory.peekFirst();
    return first != null ? first : appUrl;
  }

  private boolean redirect(WebView view, String from, String to) {
    Log.d(TAG, "Redirect from: " + from + " to: " + to);
    view.loadUrl(to);
    return true;
  }
}
