import Foundation
import WebKit

@objc public class NAIFilterGameUrlPlugin: NSObject {
    private let defaultBlockedDomains = [
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
    ]
    
    private var blockedDomains: [String]
    private var redirectAppUrl: String
    private let appUrl: String
    
    override init() {
        self.blockedDomains = defaultBlockedDomains
        // Default values similar to Android implementation
        let scheme = "capacitor"
        let hostname = "localhost:8100"
        self.redirectAppUrl = "\(scheme)://\(hostname)"
        self.appUrl = self.redirectAppUrl
        super.init()
    }
    
    @objc public func shouldOverrideLoad(_ navigationAction: WKNavigationAction) -> NSNumber? {
        guard let url = navigationAction.request.url,
              let urlString = url.absoluteString,
              let host = url.host else {
            return nil
        }
        
        var isBlocked = false
        
        for blockedDomain in blockedDomains {
            if host.contains(blockedDomain) {
                print("NAIFilterGameUrlPlugin: Matched host: \(host)")
                let scheme = url.scheme ?? ""
                
                if scheme == "https" {
                    redirectAppUrl = urlString
                        .replacingOccurrences(of: "https://staging.\(blockedDomain)", with: appUrl)
                        .replacingOccurrences(of: "https://beta.\(blockedDomain)", with: appUrl)
                        .replacingOccurrences(of: "https://www.\(blockedDomain)", with: appUrl)
                } else if scheme == "http" {
                    redirectAppUrl = urlString
                        .replacingOccurrences(of: "http://staging.\(blockedDomain)", with: appUrl)
                        .replacingOccurrences(of: "http://beta.\(blockedDomain)", with: appUrl)
                        .replacingOccurrences(of: "http://www.\(blockedDomain)", with: appUrl)
                }
                isBlocked = true
                break
            }
            
            let openUrlParam = "OpenURL?url="
            if urlString.contains(openUrlParam),
               let paramValue = urlString.components(separatedBy: openUrlParam).last,
               paramValue.contains(blockedDomain) {
                print("NAIFilterGameUrlPlugin: Matched blocked domain in OpenURL parameter: \(paramValue)")
                redirectAppUrl = appUrl
                isBlocked = true
                break
            }
        }
        
        if isBlocked {
            print("NAIFilterGameUrlPlugin: Redirect from: \(urlString) to: \(redirectAppUrl)")
            if let redirectURL = URL(string: redirectAppUrl) {
                navigationAction.webView?.load(URLRequest(url: redirectURL))
            }
            return NSNumber(value: true)
        }
        
        return nil
    }
}
