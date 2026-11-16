# CLAUDE.md

Android exam client for CBT Teladan (server: `setiawansopan/cbtteladan`, desktop
client: `setiawansopan/cbtteladan-frontend`). Single-activity Kotlin app, a
locked-down WebView pointed at `BuildConfig.EXAM_URL`. No AndroidX besides
`androidx.webkit`; `MainActivity` extends the framework `Activity`.

## Commands

```bash
./gradlew testDebugUnitTest   # JVM unit tests (signature test vector)
./gradlew assembleDebug       # debug APK, never contains the signing key
CBT_APP_SECRET=... CBT_KEYSTORE_FILE=... CBT_KEYSTORE_PASSWORD=... \
CBT_KEY_ALIAS=... CBT_KEY_PASSWORD=... ./gradlew assembleRelease
```

CI (`.github/workflows/build.yml`) runs tests + debug build on every push/PR and
builds/signs/publishes the release APK on `v*` tags (tag must match `appVersionName`).

## How the server recognises the app

The server (`StudentAccessPolicy`) treats a student request as the official app when:

1. User-Agent contains `CBTTeladan/<version>` and `cbt-teladan/<version>`
   (`ExamClient.userAgent`), version >= the admin's minimum (default 5.0.0);
2. headers `X-CBT-Client` / `X-CBT-Version`;
3. when the server has `CBT_APP_SECRET`: `X-CBT-Timestamp` (epoch seconds) and
   `X-CBT-Signature` = hex HMAC-SHA256(secret, timestamp), ±24h. Test vector:
   secret `rahasia-uji`, timestamp `1763290000` →
   `9c1344be7afacfec0610dc3fb68daa5e7917129e124f27a2743132f0088caa57`.
   Keep this identical to the desktop `signature.js` and the server.

The signature deliberately does not cover method/path: WebView XHRs keep their
custom headers across redirects, so a per-path signature would break after
login or exam start.

Headers reach the server through two paths, both needed:
- main-frame navigations: `loadUrl(url, ExamClient.headers())`, including
  redirects re-issued from `shouldOverrideUrlLoading`;
- XHR/fetch (Inertia visits, answer autosave, heartbeat, violations):
  `assets/cbt-bridge.js`, injected at document start (`addDocumentStartJavaScript`,
  fallback `evaluateJavascript`), asks `CBTNative.headers()` for fresh headers.

## Secrets

`CBT_APP_SECRET` is only baked into release builds, XOR-masked in `BuildConfig`
(`SECRET_DATA`/`SECRET_MASK`). Never commit it or a keystore (`*.jks` is ignored).
A determined student can still extract it from the APK; the goal is to stop
casual User-Agent spoofing from a normal browser.

## Exam lockdown

- Screen pinning (`startLockTask`) is required before the exam loads; a 1s monitor
  shows `lockOverlay` and pauses the WebView when unpinned or in multi-window,
  which makes the web app's `visibilitychange` handler log a violation.
- `FLAG_SECURE`, `setHideOverlayWindows`, network security config (HTTPS only,
  system CAs only), navigation limited to the exam origin, copy/paste blocked.
