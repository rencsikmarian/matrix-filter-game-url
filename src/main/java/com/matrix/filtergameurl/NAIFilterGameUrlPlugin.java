package com.matrix.filtergameurl;

import java.util.Arrays;
import java.util.List;

import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.util.Log;

import com.getcapacitor.Bridge;
import com.getcapacitor.BridgeWebViewClient;

public class NAIFilterGameUrlPlugin extends BridgeWebViewClient {

    private final List<String> blockedDomains = Arrays.asList(
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

    private final Bridge bridge;

    public NAIFilterGameUrlPlugin(Bridge bridge) {
        super(bridge);
        Log.d("NAIFilterGameUrlPlugin", "NEW WEB VIEW CLIENT CREATED");
        this.bridge = bridge;
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
        String redirectAppUrl = "https://localhost:8100";

        for (String blockedDomain : blockedDomains) {
            if (host.contains(blockedDomain)) {
                Log.d("NAIFilterGameUrlPlugin", "Matched host: " + host);
                String schema = url.getScheme();
                if (schema == null){
                    schema = "";
                }
                if(schema.equals("https")){
                    redirectAppUrl = urlString.replace("https://staging." + blockedDomain, "https://localhost:8100");
                    redirectAppUrl = redirectAppUrl.replace("https://beta." + blockedDomain, "https://localhost:8100");
                    redirectAppUrl = redirectAppUrl.replace("https://www." + blockedDomain, "https://localhost:8100");
                } else if(schema.equals("http")){
                    redirectAppUrl = urlString.replace("http://staging." + blockedDomain, "https://localhost:8100");
                    redirectAppUrl = redirectAppUrl.replace("http://beta." + blockedDomain, "https://localhost:8100");
                    redirectAppUrl = redirectAppUrl.replace("http://www." + blockedDomain, "https://localhost:8100");
                }
                isBlocked = true;
                break;
            }
        }

        // Your custom URL handling logic here
        if (isBlocked) {
            Log.d("NAIFilterGameUrlPlugin", "Redirect from: " + urlString + " to: " + redirectAppUrl);
            view.loadUrl(redirectAppUrl);
            return true;
        }
        
        return bridge.launchIntent(url);
    }
}
