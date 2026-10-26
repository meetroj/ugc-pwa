# ugc-pwa: Android wrapper for the UGCad.io installed app (PWA)

A **test wrapper**, not the store build. It is a tiny native Android shell (`io.ugcad.pwa`, plain `WebView`, no
libraries) that serves a **bundled copy of the built UGCad website** from the private origin
`https://appassets.ugcad.io` and opens it in "app mode" (`?app=1`), so the phone screens from the Frontend repo
(`src/app/`, branch `new-ui`) run exactly as an installed PWA would. All API calls go to the live backend
(`https://app.ugcad.io`); the backend CORS rule already accepts any `https://*.ugcad.io` origin.

## Why this exists, and what it is not
- Used to test the PWA on a real Android phone as an installable APK.
- **Not store-ready:** debug-style package name, signed with a throw-away test key, built as `.apk` (Google Play needs
  an `.aab`), and the site is **copied inside** the APK, so every website change needs a rebuild.
- Google sign-in does **not** work inside it (Google blocks OAuth in embedded WebViews and the origin is not
  registered); email + password works. No push notifications.
- For the Play Store the recommended route for a PWA is a **Trusted Web Activity** (Bubblewrap / PWABuilder) that
  opens the deployed HTTPS site full-screen, verified with `/.well-known/assetlinks.json` on the domain; website
  updates then reach users without a store release. Apple does not list PWAs: on iOS keep the native app.
  See `docs/HANDOVER.md` in the Frontend repo.

## How it works
- `MainActivity.java` loads `https://appassets.ugcad.io/?app=1&source=pwa`. `shouldInterceptRequest` answers every
  request for that host from `app/src/main/assets/web/` (single-page-app fallback to `index.html`); `/sw.js` is
  blocked on purpose (a service worker would keep serving an old bundle after an update).
- Links to other hosts, `mailto:`, `tel:` open outside the app. File uploads use the system picker. Camera, mic and
  location prompts from web content are denied.
- Status/navigation bar colours are set in `app/src/main/res/values/styles.xml`.

## Build
1. Build the website with the live API address (in the Frontend repo):
   `REACT_APP_BACKEND_URL=https://app.ugcad.io npm run build`
   (a stale `.env.local` pointing at `http://localhost:8000` overrides `.env`; set the variable in the shell).
2. Copy the contents of `build/` into `app/src/main/assets/web/`, **leaving out the landing-page videos**
   (`*.mp4`, `*.mov`), which the app does not use. (`assets/web/` is git-ignored.)
3. Create a signing key (once) and `keystore.properties` next to `settings.gradle` (both git-ignored):
   ```
   storeFile=ugcad-pwa-release.jks
   storePassword=...
   keyAlias=ugcadpwa
   keyPassword=...
   ```
   `keytool -genkeypair -v -keystore ugcad-pwa-release.jks -alias ugcadpwa -keyalg RSA -keysize 2048 -validity 10000`
4. `local.properties` with `sdk.dir=C:/Users/<you>/AppData/Local/Android/Sdk` (git-ignored).
5. `./gradlew assembleRelease` -> `app/build/outputs/apk/release/app-release.apk` (JDK 17, Android SDK 35,
   Gradle 8.9 via the wrapper). Bump `versionCode` in `app/build.gradle` for every build you reinstall.

Debug builds (`./gradlew assembleDebug`) enable WebView devtools (`chrome://inspect`); release builds do not.

## Never commit
`*.jks`, `keystore.properties`, `local.properties`, `app/src/main/assets/web/`, `build/` (all in `.gitignore`).
