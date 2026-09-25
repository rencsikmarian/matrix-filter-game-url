package com.matrix.filtergameurl;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * JS-facing side of the plugin. NAIFilterGameUrlPlugin is a WebViewClient and
 * cannot reach JS, so it hands blocked navigations to this plugin, which
 * `npx cap sync` registers under the same "NAIFilterGameUrl" name as iOS.
 */
@CapacitorPlugin(name = "NAIFilterGameUrl")
public class NAIFilterGameUrlEventsPlugin extends Plugin {

    static final String INTERCEPTED_EVENT = "appUrlIntercepted";

    /**
     * Emits appUrlIntercepted to JS.
     *
     * @return false if nothing is listening, so the caller should reload instead
     */
    boolean notifyIntercepted(String url, String appUrl, boolean isMainFrame) {
        if (!hasListeners(INTERCEPTED_EVENT)) {
            return false;
        }
        JSObject data = new JSObject();
        data.put("url", url);
        data.put("appUrl", appUrl);
        data.put("isMainFrame", isMainFrame);
        notifyListeners(INTERCEPTED_EVENT, data);
        return true;
    }
}
