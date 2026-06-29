/// <reference types="@capacitor/cli" />

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
     */
    NAIFilterGameUrl?: {
      /**
       * Domains whose pages return to the last visited app page (or the app
       * URL). The host must equal the domain or its "www.", "staging." or
       * "beta." variant.
       *
       * @example ["admiralbet.es", "stargames.de"]
       */
      blockedDomains?: string[];

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
       * return targets. When a URL is blocked, the WebView reloads the newest
       * remembered page. Set to 0 to disable tracking (always returns to the
       * app URL).
       *
       * @default 3
       * @example 3
       */
      historyLimit?: number;

      /**
       * Substrings that exclude an internal page from being remembered as a
       * return target. A visited app-host URL containing any of these is not
       * recorded, so a later block never sends the user back to it (e.g.
       * cashier or free-play pages).
       *
       * @example ["/cash", "/free"]
       */
      excludedReturnPaths?: string[];
    };
  }
}

// eslint-disable-next-line @typescript-eslint/no-empty-interface
export interface NAIFilterGameUrlPlugin {}
