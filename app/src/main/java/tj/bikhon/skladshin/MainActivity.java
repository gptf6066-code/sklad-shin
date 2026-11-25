package tj.bikhon.skladshin;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Барномаи «Чарх»: саҳифаи index.html дар WebView.
 * - Тугмаи «Назад» ҳамеша ба худи барнома дода мешавад (Android 6 … 16).
 * - Сатрҳои системавӣ (боло/поён) ва клавиатура ҳеҷ гоҳ тугмаҳоро намепӯшонанд.
 */
public class MainActivity extends Activity {
    private WebView web;
    private FrameLayout root;
    private View topBar, bottomBar;
    private int barColor = Color.parseColor("#2D9CD8");
    private int bgColor = Color.parseColor("#F4F5F9");
    private long lastBack = 0;
    private OnBackInvokedCallback backCallback;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window w = getWindow();

        // Colored strip under the status bar, the page, colored strip under the navigation bar.
        root = new FrameLayout(this);
        root.setBackgroundColor(bgColor);
        topBar = new View(this);
        topBar.setBackgroundColor(barColor);
        bottomBar = new View(this);
        bottomBar.setBackgroundColor(bgColor);

        web = new WebView(this);
        web.setBackgroundColor(bgColor);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setTextZoom(100);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new Bridge(), "AndroidApp");

        root.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(topBar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, Gravity.TOP));
        root.addView(bottomBar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, Gravity.BOTTOM));
        setContentView(root);

        // Keep the page inside the visible area on every Android version (edge-to-edge on 15+, keyboard, notches).
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top, bottom, left, right;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets b = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                android.graphics.Insets ime = insets.getInsets(WindowInsets.Type.ime());
                top = b.top; left = b.left; right = b.right;
                bottom = Math.max(b.bottom, ime.bottom);
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
                left = insets.getSystemWindowInsetLeft();
                right = insets.getSystemWindowInsetRight();
            }
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) web.getLayoutParams();
            if (lp.topMargin != top || lp.bottomMargin != bottom || lp.leftMargin != left || lp.rightMargin != right) {
                lp.setMargins(left, top, right, bottom);
                web.setLayoutParams(lp);
            }
            setHeight(topBar, top);
            setHeight(bottomBar, bottom);
            return insets;
        });
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
        } else {
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) {
            w.setStatusBarContrastEnforced(false);
            w.setNavigationBarContrastEnforced(false);
        }
        applyBarIcons();

        // Back button / back gesture: always handled by the app (Android 13+ predictive back included).
        if (Build.VERSION.SDK_INT >= 33) {
            backCallback = this::handleBack;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);
        }

        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl("file:///android_asset/index.html");
    }

    private static void setHeight(View v, int h) {
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp.height != h) { lp.height = h; v.setLayoutParams(lp); }
    }

    /** Light or dark status/navigation icons depending on the colors behind them. */
    @SuppressWarnings("deprecation")
    private void applyBarIcons() {
        boolean lightTop = isLight(barColor), lightBottom = isLight(bgColor);
        View d = getWindow().getDecorView();
        if (Build.VERSION.SDK_INT >= 30) {
            android.view.WindowInsetsController c = d.getWindowInsetsController();
            if (c == null) return;
            int mask = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            int val = (lightTop ? android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS : 0)
                    | (lightBottom ? android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS : 0);
            c.setSystemBarsAppearance(val, mask);
        } else {
            int f = d.getSystemUiVisibility();
            f = lightTop ? (f | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR) : (f & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            if (Build.VERSION.SDK_INT >= 26)
                f = lightBottom ? (f | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR) : (f & ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
            d.setSystemUiVisibility(f);
        }
    }

    private static boolean isLight(int c) {
        return (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) / 255.0 > 0.62;
    }

    /** Ask the page to go back one step; on the main screen, exit only after a second press. */
    private void handleBack() {
        if (web == null) { finish(); return; }
        web.evaluateJavascript(
                "(function(){try{return window.charkhBack?String(window.charkhBack()):'false'}catch(e){return 'false'}})()",
                value -> {
                    if (value != null && value.contains("true")) return;
                    long now = SystemClock.elapsedRealtime();
                    if (now - lastBack < 2000) {
                        finish();
                    } else {
                        lastBack = now;
                        Toast.makeText(MainActivity.this, "Нажмите «Назад» ещё раз, чтобы выйти", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        // Android 12 and older (13+ uses the callback registered in onCreate).
        handleBack();
    }

    /** Bridge between the page and the phone. */
    private class Bridge {
        @JavascriptInterface
        public void saveFile(final String name, final String text) {
            runOnUiThread(() -> {
                try {
                    byte[] data = text.getBytes(StandardCharsets.UTF_8);
                    if (Build.VERSION.SDK_INT >= 29) {
                        ContentValues v = new ContentValues();
                        v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                        v.put(MediaStore.MediaColumns.MIME_TYPE, "text/csv");
                        v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                        Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                        if (uri == null) throw new Exception("no uri");
                        try (OutputStream o = getContentResolver().openOutputStream(uri)) { o.write(data); }
                        toast("Файл сохранён в «Загрузки»: " + name);
                    } else {
                        File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                        File f = new File(dir, name);
                        try (FileOutputStream o = new FileOutputStream(f)) { o.write(data); }
                        toast("Файл сохранён: " + f.getAbsolutePath());
                    }
                } catch (Exception e) {
                    toast("Не удалось сохранить файл");
                }
            });
        }

        @JavascriptInterface
        public void setBars(final String bar, final String bg) {
            runOnUiThread(() -> {
                try {
                    barColor = parseCss(bar);
                    bgColor = parseCss(bg);
                    topBar.setBackgroundColor(barColor);
                    bottomBar.setBackgroundColor(bgColor);
                    root.setBackgroundColor(bgColor);
                    web.setBackgroundColor(bgColor);
                    applyBarIcons();
                } catch (Exception ignored) { }
            });
        }

        @JavascriptInterface
        public void print() {
            runOnUiThread(() -> {
                PrintManager pm = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                if (pm == null) { toast("Печать недоступна"); return; }
                pm.print("Чарх", web.createPrintDocumentAdapter("Чарх"), new PrintAttributes.Builder().build());
            });
        }
    }

    /** "#RRGGBB" or "rgb(r, g, b)" -> color */
    private static int parseCss(String c) {
        c = c.trim();
        if (c.startsWith("rgb")) {
            String[] p = c.substring(c.indexOf('(') + 1, c.indexOf(')')).split(",");
            return Color.rgb(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim()));
        }
        return Color.parseColor(c);
    }

    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_LONG).show(); }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onPause() { web.onPause(); super.onPause(); }

    @Override
    protected void onResume() { super.onResume(); web.onResume(); }

    @Override
    protected void onDestroy() {
        if (Build.VERSION.SDK_INT >= 33 && backCallback != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
        }
        web.destroy();
        super.onDestroy();
    }
}
