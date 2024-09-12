package com.matrix.filtergameurl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.util.Log;

import com.getcapacitor.Bridge;
import com.getcapacitor.BridgeWebViewClient;

public class NAIFilterGameUrlPlugin extends BridgeWebViewClient {

  private static final String DEFAULT_SCHEME = "https";
  private static final String DEFAULT_HOSTNAME = "localhost:8100";

  private final List<String> defaultBlockedDomains = Arrays.asList(
    "admiralbet.es",
    "admiralbet.de",
    "stargames.de",
    "starvegas.ch",
    "admiral.ch",
    "admiralcasino.co.uk",
    "loteriesport.lu",
    "admiral.ro",
    "fenikss.lv",
    "feniksscasino.lv"
    // Add more domains as needed
  );

  private List<String> blockedDomains;
  private final Bridge bridge;
  private String redirectAppUrl;
  private final String appUrl;

  public NAIFilterGameUrlPlugin(Bridge bridge) {
    this(bridge, null);
  }

  public NAIFilterGameUrlPlugin(Bridge bridge, List<String> domains) {
    super(bridge);
    this.bridge = bridge;

    if (domains != null && !domains.isEmpty()) {
      this.blockedDomains = new ArrayList<>(domains);
    } else {
      this.blockedDomains = new ArrayList<>(defaultBlockedDomains);
    }

    String scheme = bridge.getScheme();
    String hostname = bridge.getHost();

    if (scheme == null || scheme.isEmpty()) {
      scheme = DEFAULT_SCHEME;
    }
    if (hostname == null || hostname.isEmpty()) {
      hostname = DEFAULT_HOSTNAME;
    }

    this.redirectAppUrl = scheme + "://" + hostname;
    this.appUrl = this.redirectAppUrl;
    Log.d("NAIFilterGameUrlPlugin", "NEW WEB VIEW CLIENT CREATED");
    Log.d("NAIFilterGameUrlPlugin", "Blocked domains: " + this.blockedDomains);
  }

  @Override
  public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
    Uri url = request.getUrl();
    String host = url.getHost();
    String urlString = url.toString();
    Log.d("NAIFilterGameUrlPlugin", "Attempting to load URL: " + urlString);

    if (host == null) {
      host = "";
    }

    boolean isBlocked = false;

    for (String blockedDomain : blockedDomains) {
      if (host.contains(blockedDomain)) {
        Log.d("NAIFilterGameUrlPlugin", "Matched host: " + host);
        String schema = url.getScheme();
        if (schema == null){
          schema = "";
        }
        if(schema.equals("https")){
          this.redirectAppUrl = urlString.replace("https://staging." + blockedDomain, this.appUrl);
          this.redirectAppUrl = this.redirectAppUrl.replace("https://beta." + blockedDomain, this.appUrl);
          this.redirectAppUrl = this.redirectAppUrl.replace("https://www." + blockedDomain, this.appUrl);
        } else if(schema.equals("http")){
          this.redirectAppUrl = urlString.replace("http://staging." + blockedDomain, this.appUrl);
          this.redirectAppUrl = this.redirectAppUrl.replace("http://beta." + blockedDomain, this.appUrl);
          this.redirectAppUrl = this.redirectAppUrl.replace("http://www." + blockedDomain, this.appUrl);
        }
        isBlocked = true;
        break;
      }
    }

    // Your custom URL handling logic here
    if (isBlocked) {
      Log.d("NAIFilterGameUrlPlugin", "Redirect from: " + urlString + " to: " + this.redirectAppUrl);
      view.loadUrl(this.redirectAppUrl);
      return true;
    }

    return super.shouldOverrideUrlLoading(view, request);
  }
}
