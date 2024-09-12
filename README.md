# matrix-filter-game-url

This plugin will handle the redirect urls when closing external games

## Install

```bash
npm install matrix-filter-game-url
npx cap sync
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