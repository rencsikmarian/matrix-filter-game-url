/// <reference types="@capacitor/cli" />

declare module '@capacitor/cli' {
  export interface PluginsConfig {
    /**
     * NAIFilterGameUrl filters WebView navigations natively on iOS and
     * Android. Every key is optional; an omitted key disables that check.
     * Without a NAIFilterGameUrl entry the plugin lets every URL through.
     * All values are matched case-insensitively.
     */
    NAIFilterGameUrl?: {
      /**
       * Domains whose pages redirect back to the app URL. The host must
       * equal the domain or its "www.", "staging." or "beta." variant.
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
       * contains a blocked domain, the WebView redirects to the app URL.
       *
       * @example ["openurl?url="]
       */
      openUrlParams?: string[];

      /**
       * Checked on every URL: if the host contains an entry, the WebView
       * redirects to the app URL.
       *
       * @example ["lobbyiframelaunch"]
       */
      blockedHostKeywords?: string[];

      /**
       * Checked on every URL: if the URL ends with "/<entry>", the WebView
       * redirects to the app URL.
       *
       * @example ["lobbyiframelaunch"]
       */
      blockedPathSuffixes?: string[];
    };
  }
}

// eslint-disable-next-line @typescript-eslint/no-empty-interface
export interface NAIFilterGameUrlPlugin {}
