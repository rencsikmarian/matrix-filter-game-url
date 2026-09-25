# matrix-filter-game-url

This plugin will handle the redirect urls when closing external games

## Install

The plugin is installed from git, not npm:

```bash
npm install git+https://github.com/rencsikmarian/matrix-filter-game-url.git#main
npx cap sync
```

Your lock file pins the installed commit, so merging into `main` does not
update your app; rerun the install to pick up changes. Use a tag or commit
instead of `#main` to pin a specific version.

## Configuration

All filtering rules live in your `capacitor.config.ts` (or
`capacitor.config.json`). Every key is optional — an omitted key disables that
check, and **without a `NAIFilterGameUrl` entry the plugin lets every URL
through** (the previously hardcoded domain list was removed in this version).
In that case `npx cap sync` prints a warning. Values are matched
case-insensitively.

The config below is the default setup: it reproduces the rules earlier
versions had built in (all previously hardcoded domains, chat pass paths and
lobby checks), so copy it as-is to keep the old filtering after upgrading.

When a URL is blocked, the WebView is sent back to the **most recent page the
user visited on the app's own host**, falling back to the app URL if none was
seen. Every page is remembered, including in-app route changes (e.g. Angular
router navigations). Use `historyLimit` and `excludedReturnPaths` to tune
which pages are remembered.

```typescript
import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  plugins: {
    NAIFilterGameUrl: {
      // Pages on these domains (incl. www./staging./beta. variants) send the
      // WebView back to the last visited app page.
      blockedDomains: [
        'admiralbet.es',
        'admiralbet.de',
        'stargames.de',
        'starvegas.ch',
        'admiral.ch',
        'admiralcasino.co.uk',
        'loteriesport.lu',
        'admiral.ro',
        'fenikss.lv',
        'feniksscasino.lv',
        'starvegas.es',
      ],
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
      // Example values; there is no built-in default.
      excludedReturnPaths: ['/cash', '/free', '/assets/'],
    },
  },
};

export default config;
```

## Handling blocked URLs in the app

By default a blocked URL reloads the WebView at the return page, which restarts
the web app. To avoid the reload, attach an `appUrlIntercepted` listener:
while one is attached, the plugin only cancels the navigation and emits the
event, and your router navigates to `appUrl` itself. If no listener is
attached (or the WebView's page is not your app), it falls back to the reload.

```typescript
import { Capacitor } from '@capacitor/core';
import { NAIFilterGameUrl } from 'matrix-filter-game-url';

if (Capacitor.isNativePlatform()) {
  NAIFilterGameUrl.addListener('appUrlIntercepted', ({ appUrl }) => {
    const { pathname, search } = new URL(appUrl); // "/de/slots"
    this.ngZone.run(() => this.router.navigateByUrl(pathname + search));
  });
}
```

The event works on iOS and Android (Android still needs the `MainActivity`
setup below, and `npx cap sync` after updating the plugin). There is no web
implementation, so `addListener` rejects on web, hence the platform check.

## API

<docgen-index>

* [`addListener('appUrlIntercepted', ...)`](#addlistenerappurlintercepted-)
* [`removeAllListeners()`](#removealllisteners)
* [Interfaces](#interfaces)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### addListener('appUrlIntercepted', ...)

```typescript
addListener(eventName: 'appUrlIntercepted', listenerFunc: (event: AppUrlIntercepted) => void) => Promise<PluginListenerHandle>
```

Fired when a navigation is blocked. While a listener is attached the
navigation is cancelled without reloading the WebView, so the app stays
alive and can route to `appUrl` itself. Without a listener the WebView
reloads `appUrl` instead.

| Param              | Type                                                                                |
| ------------------ | ----------------------------------------------------------------------------------- |
| **`eventName`**    | <code>'appUrlIntercepted'</code>                                                    |
| **`listenerFunc`** | <code>(event: <a href="#appurlintercepted">AppUrlIntercepted</a>) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### removeAllListeners()

```typescript
removeAllListeners() => Promise<void>
```

Removes all listeners for this plugin.

--------------------


### Interfaces


#### PluginListenerHandle

| Prop         | Type                                      |
| ------------ | ----------------------------------------- |
| **`remove`** | <code>() =&gt; Promise&lt;void&gt;</code> |


#### AppUrlIntercepted

Payload of the `appUrlIntercepted` event.

| Prop              | Type                 | Description                                                                                                                                        |
| ----------------- | -------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| **`url`**         | <code>string</code>  | The blocked URL that was cancelled (lowercased).                                                                                                   |
| **`appUrl`**      | <code>string</code>  | Where the user should go instead: the last visited app page, or the app URL if none was recorded. Absolute, e.g. "capacitor://localhost/de/slots". |
| **`isMainFrame`** | <code>boolean</code> | Whether the blocked navigation targeted the main frame (false for iframes, e.g. an embedded game).                                                 |

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

# How to use the plugin on iOS

No code is needed: `npx cap sync` registers the plugin automatically.
