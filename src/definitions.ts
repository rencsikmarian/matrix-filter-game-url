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
     * With `gamePaths` set, a link to a blocked domain tapped outside a game
     * opens the same path inside the app instead.
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
       * domains.
       *
       * @default ["www.", "staging.", "beta."]
       * @example ["www.", "staging.", "beta."]
       */
      allowedPrefixes?: string[];

      /**
       * Substrings that exempt a URL on a blocked domain from redirecting.
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
       * Substrings that mark an app-host page as a game page. The user counts
       * as "in a game" while the last app-host page shown in the main frame
       * contains one of these; any other app-host page ends the game. A game
       * that navigates the whole WebView to another host stays "in a game".
       *
       * In a game, a blocked-domain URL returns to the last visited app page.
       * Outside a game, a top-level blocked-domain URL opens the same path
       * (query and fragment kept) in the app instead, e.g.
       * "https://www.admiralbet.de/de/slots?x=1" becomes
       * "capacitor://localhost/de/slots?x=1". Blocked-domain URLs in iframes
       * and the `openUrlParams`, `blockedHostKeywords` and
       * `blockedPathSuffixes` checks always return to the last app page.
       *
       * Game pages are never remembered as return targets. When omitted or
       * empty, every page counts as a game, so blocked-domain URLs always
       * return to the last app page.
       *
       * @example ["/cash", "/free"]
       */
      gamePaths?: string[];
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
   * iframes, e.g. an embedded game).
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
