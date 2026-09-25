// Run by the Capacitor CLI as this plugin's `capacitor:copy:after` hook (on
// `npx cap copy`, `npx cap sync` and `npx cap run`), with the app's config in
// CAPACITOR_CONFIG. Warns when there is no NAIFilterGameUrl entry, since the
// plugin then allows every URL.
try {
  const plugins = JSON.parse(process.env.CAPACITOR_CONFIG).plugins || {};
  if (!plugins.NAIFilterGameUrl) {
    console.warn(
      `[warn] matrix-filter-game-url (${process.env.CAPACITOR_PLATFORM_NAME}): no NAIFilterGameUrl ` +
        'entry under plugins in the Capacitor config, so every URL will be allowed. ' +
        "See the plugin README's Configuration section.",
    );
  }
} catch {
  // Never fail here: a hook exiting non-zero aborts the whole sync.
}
