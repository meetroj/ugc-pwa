package io.ugcad.pwa;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.MimeTypeMap;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * UGCad installed-app shell. The built website (src/main/assets/web) is served from a private
 * https origin that the backend's CORS rule (https://*.ugcad.io) already allows, and the web
 * app switches to its phone screens through the ?app=1 flag. Nothing here talks to the
 * network: all API calls are made by the web app itself, to the live backend.
 */
public class MainActivity extends Activity {
    private static final String HOST = "appassets.ugcad.io";
    private static final String START_URL = "https://" + HOST + "/?app=1&source=pwa";
    private static final int FILE_REQUEST = 7001;

    private WebView web;
    private ValueCallback<Uri[]> filePicker;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        // Devtools only in debuggable builds (never in the signed release APK).
        if ((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
        web = new WebView(this);
        web.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportMultipleWindows(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setUserAgentString(s.getUserAgentString() + " UGCadApp/1");

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(web, true); // payment checkout runs in an iframe

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (!HOST.equals(u.getHost())) return null;
                return serveAsset(u.getPath());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (HOST.equals(u.getHost())) return false;
                if (!req.isForMainFrame()) return false;
                openExternal(u); // anything outside the bundled app opens in the browser / dialer / mail app
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (filePicker != null) filePicker.onReceiveValue(null);
                filePicker = cb;
                try {
                    startActivityForResult(params.createIntent(), FILE_REQUEST);
                } catch (ActivityNotFoundException e) {
                    filePicker = null;
                    return false;
                }
                return true;
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                request.deny(); // no camera / mic / location prompts from web content
            }
        });

        web.setDownloadListener((url, ua, cd, mime, len) -> openExternal(Uri.parse(url)));

        if (state != null) web.restoreState(state);
        else web.loadUrl(START_URL);
    }

    /** Serves /path from assets/web; unknown extension-less paths fall back to index.html (SPA routing). */
    private WebResourceResponse serveAsset(String rawPath) {
        String path = rawPath == null ? "" : rawPath.replaceFirst("^/+", "");
        if (path.isEmpty()) path = "index.html";
        // The service worker would keep serving an old bundle after an app update; the shell doesn't need one.
        if (path.equals("sw.js")) return notFound();
        if (path.contains("..")) return notFound();
        try {
            return asset(path);
        } catch (IOException e) {
            String last = path.substring(path.lastIndexOf('/') + 1);
            if (last.contains(".")) return notFound();
            try {
                return asset("index.html");
            } catch (IOException e2) {
                return notFound();
            }
        }
    }

    private WebResourceResponse asset(String path) throws IOException {
        InputStream in = getAssets().open("web/" + path);
        Map<String, String> headers = new HashMap<>();
        headers.put("Cache-Control", "no-cache");
        return new WebResourceResponse(mimeFor(path), "utf-8", 200, "OK", headers, in);
    }

    private static WebResourceResponse notFound() {
        return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", new HashMap<>(), new ByteArrayInputStream(new byte[0]));
    }

    private static String mimeFor(String path) {
        String ext = MimeTypeMap.getFileExtensionFromUrl(path).toLowerCase();
        switch (ext) {
            case "js": case "mjs": return "application/javascript";
            case "css": return "text/css";
            case "html": return "text/html";
            case "json": case "map": return "application/json";
            case "webmanifest": return "application/manifest+json";
            case "svg": return "image/svg+xml";
            case "woff2": return "font/woff2";
            case "woff": return "font/woff";
            case "webp": return "image/webp";
            default:
                String m = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
                return m != null ? m : "application/octet-stream";
        }
    }

    private void openExternal(Uri u) {
        String scheme = u.getScheme();
        if (scheme == null) return;
        if (!(scheme.equals("https") || scheme.equals("http") || scheme.equals("mailto") || scheme.equals("tel") || scheme.equals("whatsapp"))) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, u));
        } catch (ActivityNotFoundException ignored) {
            // no app can handle it: nothing to do
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == FILE_REQUEST && filePicker != null) {
            filePicker.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
            filePicker = null;
            return;
        }
        super.onActivityResult(req, res, data);
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_BACK && web.canGoBack()) {
            web.goBack();
            return true;
        }
        return super.onKeyDown(code, e);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onPause() {
        CookieManager.getInstance().flush();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
