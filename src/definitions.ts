/// <reference types="@capacitor/cli" />

import type { PluginListenerHandle } from '@capacitor/core';

declare module '@capacitor/cli' {
  export interface PluginsConfig {
    /**
     * NAIFilterGameUrl filters WebView navigations natively on iOS and
     * Android. Every key is optional; an omitted key disables that check.
     * Without a NAIFilterGameUrl entry the plugin lets every URL through.
     * All values are matched case-insensitively.
     *
     * When a URL is blocked, the WebView is sent back to the most recent
     * page the user visited on the app's own host (see `historyLimit` and
     * `excludedReturnPaths`), falling back to the app URL if none was seen.
     * With `gamePaths` set, a link to the app's own site (see `appDomains`)
     * tapped outside a game opens the same path inside the app instead.
     * Empty strings in any list are ignored.
     */
    NAIFilterGameUrl?: {
      /**
       * Domains whose pages return to the last visited app page (or the app
       * URL). The host must equal the domain or one of its `allowedPrefixes`
       * variants. See `gamePaths` for how links to these domains are treated
       * outside a game.
       *
       * @example ["admiralbet.es", "stargames.de"]
       */
      blockedDomains?: string[];

      /**
       * Subdomain prefixes that also count as a blocked domain: the host
       * must equal a prefix followed by the domain, so include the trailing
       * dot. The bare domain always matches, so `[]` blocks only the exact
       * domains. The same prefixes apply to `appDomains`.
       *
       * @default ["www.", "staging.", "beta."]
       * @example ["www.", "staging.", "beta."]
       */
      allowedPrefixes?: string[];

      /**
       * The blocked domains that are the app's own site. Outside a game (see
       * `gamePaths`) only links to these open in the app; a link to any other
       * blocked domain returns to the last visited app page. Each entry also
       * matches its `allowedPrefixes` variants and must be listed in
       * `blockedDomains` as well. When omitted or empty, every blocked domain
       * counts as the app's own site.
       *
       * @example ["admiralbet.de"]
       */
      appDomains?: string[];

      /**
       * Substrings that exempt a URL on a blocked domain from redirecting.
       * The URL is then left alone: on Android it loads in the WebView, on
       * iOS Capacitor's usual handling applies (a top-level URL outside
       * `server.allowNavigation` opens in the browser). Besides embedded
       * services such as chat, list links that are not app pages here, e.g.
       * files (".pdf"); outside a game they would otherwise be opened as an
       * app route.
       *
       * @example ["/ichatclient/", "novomind", "/chatrest"]
       */
      passPaths?: string[];

      /**
       * Parameter markers checked on every URL. If the text after a marker
       * contains a blocked domain, the WebView returns to the last visited app page (or the app URL).
       *
       * @example ["openurl?url="]
       */
      openUrlParams?: string[];

      /**
       * Checked on every URL: if the host contains an entry, the WebView
       * returns to the last visited app page (or the app URL).
       *
       * @example ["lobbyiframelaunch"]
       */
      blockedHostKeywords?: string[];

      /**
       * Checked on every URL: if the URL ends with "/<entry>", the WebView
       * returns to the last visited app page (or the app URL).
       *
       * @example ["lobbyiframelaunch"]
       */
      blockedPathSuffixes?: string[];

      /**
       * How many of the most recent internal (app-host) pages to remember as
       * return targets, including in-app route changes. When a URL is
       * blocked, the WebView returns to the newest remembered page. Set to 0
       * to disable tracking (always returns to the app URL).
       *
       * @default 3
       * @example 3
       */
      historyLimit?: number;

      /**
       * Substrings that exclude an internal page from being remembered as a
       * return target. A visited app-host URL containing any of these is not
       * recorded, so a later block never sends the user back to it (e.g.
       * 404 pages). Pages matching `gamePaths` are excluded automatically.
       *
       * @example ["/404", "/assets/"]
       */
      excludedReturnPaths?: string[];

      /**
       * Path segments that mark an app-host page as a game page. A page is a
       * game page when its path (query and fragment are ignored) contains an
       * entry that ends at a "/" or at the end of the path: "/cash" matches
       * "/de/games/book-of-ra/cash" but not "/de/cashier". The user counts as
       * "in a game" while the last app-host page shown in the main frame is
       * a game page, and for `gameExitGraceMs` after leaving it. A game that
       * navigates the whole WebView to another host stays "in a game".
       *
       * In a game, a blocked-domain URL returns to the last visited app page.
       * Outside a game, a top-level URL on one of the `appDomains` opens the
       * same path (query and fragment kept) in the app instead, e.g.
       * "https://www.admiralbet.de/de/slots?x=1" becomes
       * "capacitor://localhost/de/slots?x=1". A frame navigating itself to a
       * blocked domain, and any URL matching `openUrlParams`,
       * `blockedHostKeywords` or `blockedPathSuffixes`, always returns to the
       * last app page.
       *
       * Game pages are never remembered as return targets. When omitted or
       * empty, every page counts as a game, so blocked-domain URLs always
       * return to the last app page.
       *
       * @example ["/cash", "/free"]
       */
      gamePaths?: string[];

      /**
       * How long, in milliseconds, the user still counts as "in a game" after
       * the app leaves a game page (see `gamePaths`). A game often redirects
       * to the site just after the app has already routed back to the lobby;
       * within this window that redirect still returns to the last app page
       * instead of being opened as a link. Set to 0 to end the game as soon
       * as the game page is left.
       *
       * @default 3000
       * @example 3000
       */
      gameExitGraceMs?: number;
    };
  }
}

/**
 * Payload of the `appUrlIntercepted` event.
 */
export interface AppUrlIntercepted {
  /**
   * The blocked URL that was cancelled (lowercased).
   */
  url: string;

  /**
   * Where the user should go instead: the last visited app page (or the app
   * URL if none was recorded), or, for a blocked-domain link tapped outside
   * a game (see `gamePaths`), the link's own path, query and fragment on the
   * app URL. Absolute, e.g. "capacitor://localhost/de/slots".
   */
  appUrl: string;

  /**
   * Whether the blocked navigation targeted the main frame (false for
   * iframes, e.g. an embedded game). On Android a frame is recognised by
   * the request it sends for its page, since the WebView does not report
   * frame navigations.
   */
  isMainFrame: boolean;
}

export interface NAIFilterGameUrlPlugin {
  /**
   * Fired when a navigation is blocked. While a listener is attached the
   * navigation is cancelled without reloading the WebView, so the app stays
   * alive and can route to `appUrl` itself. Without a listener the WebView
   * reloads `appUrl` instead.
   */
  addListener(
    eventName: 'appUrlIntercepted',
    listenerFunc: (event: AppUrlIntercepted) => void,
  ): Promise<PluginListenerHandle>;

  /**
   * Removes all listeners for this plugin.
   */
  removeAllListeners(): Promise<void>;
}
