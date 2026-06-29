# matrix-filter-game-url

This plugin will handle the redirect urls when closing external games

## Install

```bash
npm install matrix-filter-game-url
npx cap sync
```

## Configuration

All filtering rules live in your `capacitor.config.ts` (or
`capacitor.config.json`). Every key is optional — an omitted key disables that
check, and **without a `NAIFilterGameUrl` entry the plugin lets every URL
through** (the previously hardcoded domain list was removed in this version).
Values are matched case-insensitively.

When a URL is blocked, the WebView is sent back to the **most recent page the
user visited on the app's own host** (the last internal link), falling back to
the app URL if none was seen. Use `historyLimit` and `excludedReturnPaths` to
tune which pages are remembered.

```typescript
import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  plugins: {
    NAIFilterGameUrl: {
      // Pages on these domains (incl. www./staging./beta. variants) send the
      // WebView back to the last visited app page.
      blockedDomains: ['admiralbet.es', 'admiralbet.de', 'stargames.de'],
      // URLs on a blocked domain containing one of these load normally.
      passPaths: ['/ichatclient/', 'novomind', '/chatrest'],
      // Checked on every URL: if the text after the marker contains a
      // blocked domain, return to the last visited app page.
      openUrlParams: ['openurl?url='],
      // Checked on every URL: host contains the entry -> return to last app page.
      blockedHostKeywords: ['lobbyiframelaunch'],
      // Checked on every URL: URL ends with "/<entry>" -> return to last app page.
      blockedPathSuffixes: ['lobbyiframelaunch'],
      // How many recent app-host pages to remember; the newest is reloaded
      // when a URL is blocked. 0 disables tracking. Defaults to 3.
      historyLimit: 3,
      // App-host pages whose URL contains one of these are never remembered as
      // a return target (so a block won't send the user back to them).
      excludedReturnPaths: ['/cash', '/free'],
    },
  },
};

export default config;
```

## API

<docgen-index>



</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->



</docgen-api>

# How to use the plugin on Android
## Add this code in MainActivity in onCreate function

```java

import com.matrix.filtergameurl.NAIFilterGameUrlPlugin;

public class MainActivity extends BridgeActivity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    this.bridge.setWebViewClient(new NAIFilterGameUrlPlugin(this.bridge));
  }
}
```

The optional second constructor argument (`new NAIFilterGameUrlPlugin(this.bridge, domains)`) overrides the `blockedDomains` config key; all other lists always come from the Capacitor config.

## TODO
- [ ] Add same function for iOS

## Code to test for iOS

```swift
import Foundation
import Capacitor
import WebKit

@objc(NAIFilterGameUrlPlugin)
public class NAIFilterGameUrlPlugin: NSObject, CAPPlugin {
    private let defaultBlockedDomains = [
        // Add your blocked domains here
    ]
    private var blockedDomains: [String]
    private var redirectAppUrl: String
    private let appUrl: String

    override public init() {
        self.blockedDomains = defaultBlockedDomains
        self.appUrl = "https://localhost:8100" // Default, update as needed
        self.redirectAppUrl = self.appUrl
        super.init()
    }

    @objc public func shouldOverrideLoad(_ navigationAction: WKNavigationAction) -> NSNumber? {
        guard let url = navigationAction.request.url, let host = url.host else {
            return NSNumber(value: false)
        }

        let urlString = url.absoluteString
        print("Attempting to load URL: \(urlString)")

        for blockedDomain in blockedDomains {
            if host.contains(blockedDomain) {
                print("Matched host: \(host)")
                let scheme = url.scheme ?? ""
                if scheme == "https" {
                    self.redirectAppUrl = urlString
                        .replacingOccurrences(of: "https://staging.\(blockedDomain)", with: self.appUrl)
                        .replacingOccurrences(of: "https://beta.\(blockedDomain)", with: self.appUrl)
                        .replacingOccurrences(of: "https://www.\(blockedDomain)", with: self.appUrl)
                } else if scheme == "http" {
                    self.redirectAppUrl = urlString
                        .replacingOccurrences(of: "http://staging.\(blockedDomain)", with: self.appUrl)
                        .replacingOccurrences(of: "http://beta.\(blockedDomain)", with: self.appUrl)
                        .replacingOccurrences(of: "http://www.\(blockedDomain)", with: self.appUrl)
                }
                
                print("Redirect from: \(urlString) to: \(self.redirectAppUrl)")
                if let redirectURL = URL(string: self.redirectAppUrl) {
                    DispatchQueue.main.async {
                        self.bridge?.webView?.load(URLRequest(url: redirectURL))
                    }
                }
                return NSNumber(value: true)
            }
        }

        return NSNumber(value: false)
    }
}
```
  
# How to use the plugin on iOS
## Add this code in AppDelegate in application function

```swift
import Capacitor

// ... existing imports and class declaration ...

func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
    // Override point for customization after application launch.
    
    // Add this line to register your plugin
    CAPBridge.registerPlugin(NAIFilterGameUrlPlugin.self)
    
    return true
}
```