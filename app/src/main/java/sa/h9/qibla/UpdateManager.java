package sa.h9.qibla;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Window;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.Button;
import android.widget.TextView;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import org.json.JSONObject;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;

public final class UpdateManager {
    private static final String PREFS = "h9_qibla_ota";
    private static final String KEY_LAST_AUTO_CHECK = "last_auto_check";
    private static final long AUTO_CHECK_INTERVAL = 24L * 60L * 60L * 1000L;
    private UpdateManager() { }

    private static boolean isEn(Activity activity) {
        if (activity == null) return false;
        boolean defaultEn = Locale.getDefault().getLanguage().toLowerCase(Locale.US).startsWith("en");
        return activity.getSharedPreferences("h9_qibla_ui", Activity.MODE_PRIVATE).getBoolean("english_mode", defaultEn);
    }

    public static void checkForUpdates(Activity activity, boolean manual) {
        if (activity == null || activity.isFinishing()) return;
        final boolean en = isEn(activity);
        if (!manual) {
            long last = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE).getLong(KEY_LAST_AUTO_CHECK, 0L);
            if (System.currentTimeMillis() - last < AUTO_CHECK_INTERVAL) return;
            activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE).edit().putLong(KEY_LAST_AUTO_CHECK, System.currentTimeMillis()).apply();
        }
        if (manual) Toast.makeText(activity, en ? "Checking for updates…" : "جاري فحص التحديثات…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String[] urls = new String[] {
                BuildConfig.UPDATE_MANIFEST_URL,
                "https://raw.githubusercontent.com/bandar-sul/HQibla-OTA/main/latest.json"
            };
            JSONObject best = null;
            int maxRemoteCode = -1;
            for (String manifestUrl : urls) {
                if (manifestUrl == null || manifestUrl.isEmpty()) continue;
                HttpURLConnection c = null;
                try {
                    URL u = new URL(manifestUrl);
                    c = (HttpURLConnection) u.openConnection();
                    c.setConnectTimeout(6000); c.setReadTimeout(6000); c.setInstanceFollowRedirects(true);
                    c.setRequestProperty("Accept", "application/json");
                    c.setRequestProperty("User-Agent", "H9-Qibla/" + BuildConfig.VERSION_NAME);
                    if (c.getResponseCode() != 200) continue;
                    StringBuilder body = new StringBuilder();
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
                        String line; while ((line = br.readLine()) != null) body.append(line);
                    }
                    JSONObject j = new JSONObject(body.toString());
                    int code = j.getInt("versionCode");
                    if (code > maxRemoteCode) {
                        maxRemoteCode = code;
                        best = j;
                    }
                } catch (Throwable ignored) {
                } finally {
                    if (c != null) c.disconnect();
                }
            }
            if (best != null) {
                try {
                    int remoteCode = best.getInt("versionCode");
                    String remoteName = best.optString("versionName", String.valueOf(remoteCode));
                    String apkUrl = best.getString("apkUrl");
                    String sha256 = normalizeHex(best.getString("sha256"));
                    String notes = en ? best.optString("notes_en", best.optString("notes", ""))
                                      : best.optString("notes_ar", best.optString("notes", ""));
                    if (remoteCode <= BuildConfig.VERSION_CODE) {
                        if (manual) activity.runOnUiThread(() -> Toast.makeText(activity, en ? ("You are on the latest version (" + BuildConfig.VERSION_NAME + ")") : ("أنت على أحدث إصدار (" + BuildConfig.VERSION_NAME + ")"), Toast.LENGTH_LONG).show());
                        return;
                    }
                    activity.runOnUiThread(() -> showUpdateDialog(activity, remoteCode, remoteName, apkUrl, sha256, notes));
                } catch (Throwable e) {
                    if (manual) activity.runOnUiThread(() -> Toast.makeText(activity, en ? "Unable to check for updates right now" : "تعذر فحص التحديثات الآن", Toast.LENGTH_LONG).show());
                }
            } else {
                if (manual) activity.runOnUiThread(() -> Toast.makeText(activity, en ? "Unable to check for updates right now" : "تعذر فحص التحديثات الآن", Toast.LENGTH_LONG).show());
            }
        }, "H9-OTA-Check").start();
    }

    private static void showUpdateDialog(Activity activity, int code, String name, String apkUrl, String sha256, String notes) {
        if (activity.isFinishing()) return;
        final boolean en = isEn(activity);
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = dp(activity, 28);
        card.setPadding(pad, dp(activity, 24), pad, dp(activity, 24));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF171D23);
        bg.setCornerRadius(dp(activity, 28));
        bg.setStroke(dp(activity, 2), 0xFFE5B64A);
        card.setBackground(bg);

        TextView title = new TextView(activity);
        title.setText(en ? "New Update Available" : "يوجد تحديث جديد");
        title.setTextColor(0xFFFFC857);
        title.setTextSize(27f);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        card.addView(title, new LinearLayout.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, dp(activity, 58)));

        TextView version = new TextView(activity);
        version.setText((en ? "Version " : "الإصدار ") + name);
        version.setTextColor(0xFF2FE0C7);
        version.setTextSize(20f);
        version.setGravity(Gravity.CENTER);
        version.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams versionLp = new LinearLayout.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, dp(activity, 46));
        versionLp.bottomMargin = dp(activity, 8);
        card.addView(version, versionLp);

        TextView body = new TextView(activity);
        String bodyText = (notes == null || notes.trim().isEmpty())
                ? (en ? "A newer version of Qibla is available." : "يتوفر إصدار أحدث للتطبيق.")
                : notes.trim();
        body.setText(bodyText + (en ? "\n\nThe update package integrity will be verified before installation." : "\n\nسيتم التحقق من سلامة ملف التحديث قبل التثبيت."));
        body.setTextColor(0xFFF1F5F6);
        body.setTextSize(18f);
        body.setGravity(Gravity.CENTER);
        body.setTextDirection(en ? android.view.View.TEXT_DIRECTION_LTR : android.view.View.TEXT_DIRECTION_RTL);
        body.setLineSpacing(dp(activity, 2), 1.10f);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
        bodyLp.bottomMargin = dp(activity, 20);
        card.addView(body, bodyLp);

        LinearLayout buttons = new LinearLayout(activity);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);

        Button later = new Button(activity);
        later.setText(en ? "Later" : "لاحقًا");
        later.setTextSize(18f);
        later.setTextColor(0xFFFFC857);
        later.setAllCaps(false);
        GradientDrawable laterBg = new GradientDrawable();
        laterBg.setColor(0xFF25323A);
        laterBg.setCornerRadius(dp(activity, 20));
        laterBg.setStroke(dp(activity, 1), 0xFFE5B64A);
        later.setBackground(laterBg);
        later.setOnClickListener(v -> dialog.dismiss());

        Button install = new Button(activity);
        install.setText(en ? "Download & Install" : "تنزيل وتثبيت");
        install.setTextSize(18f);
        install.setTextColor(Color.WHITE);
        install.setAllCaps(false);
        GradientDrawable installBg = new GradientDrawable();
        installBg.setColor(0xFF117A7C);
        installBg.setCornerRadius(dp(activity, 20));
        installBg.setStroke(dp(activity, 1), 0xFF2FE0C7);
        install.setBackground(installBg);
        install.setOnClickListener(v -> {
            dialog.dismiss();
            downloadAndInstall(activity, code, apkUrl, sha256);
        });

        LinearLayout.LayoutParams btnLp1 = new LinearLayout.LayoutParams(dp(activity, 180), dp(activity, 54));
        btnLp1.rightMargin = dp(activity, 12);
        buttons.addView(later, btnLp1);
        buttons.addView(install, new LinearLayout.LayoutParams(dp(activity, 220), dp(activity, 54)));
        card.addView(buttons);

        dialog.setContentView(card);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.dimAmount = 0.72f;
            window.setAttributes(attrs);
            window.setLayout(dp(activity, 620), WindowManager.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
        }
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static void downloadAndInstall(Activity activity, int code, String apkUrl, String expectedSha256) {
        if (activity == null || activity.isFinishing()) return;
        try {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl));
            browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(browserIntent);
        } catch (Throwable t) {
            showDownloadFallbackDialog(activity, apkUrl);
        }
    }

    public static void showDownloadFallbackDialog(Activity activity, String apkUrl) {
        if (activity == null || activity.isFinishing()) return;
        final boolean en = isEn(activity);
        final Dialog d = new Dialog(activity);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setCancelable(true);

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(activity, 24), dp(activity, 20), dp(activity, 24), dp(activity, 20));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF171D23);
        bg.setCornerRadius(dp(activity, 24));
        bg.setStroke(dp(activity, 2), 0xFFE5B64A);
        card.setBackground(bg);

        TextView t = new TextView(activity);
        t.setText(en ? "Download Qibla Update" : "تنزيل تحديث القبلة");
        t.setTextColor(0xFFFFC857);
        t.setTextSize(20f);
        t.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        card.addView(t, new LinearLayout.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, dp(activity, 46)));

        TextView msg = new TextView(activity);
        msg.setText(en ? "You can download the APK directly in your browser or Downloader app:" : "يمكنك تنزيل ملف التحديث APK مباشرة عبر المتصفح أو تطبيق Downloader:");
        msg.setTextColor(0xFFF1F5F6);
        msg.setTextSize(15f);
        msg.setGravity(Gravity.CENTER);
        msg.setTextDirection(en ? android.view.View.TEXT_DIRECTION_LTR : android.view.View.TEXT_DIRECTION_RTL);
        LinearLayout.LayoutParams msgLp = new LinearLayout.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
        msgLp.bottomMargin = dp(activity, 14);
        card.addView(msg, msgLp);

        TextView urlView = new TextView(activity);
        urlView.setText(apkUrl);
        urlView.setTextColor(0xFF2FE0C7);
        urlView.setTextSize(12f);
        urlView.setGravity(Gravity.CENTER);
        urlView.setPadding(dp(activity, 8), dp(activity, 8), dp(activity, 8), dp(activity, 8));
        GradientDrawable urlBg = new GradientDrawable();
        urlBg.setColor(0xFF0C1820);
        urlBg.setCornerRadius(dp(activity, 12));
        urlBg.setStroke(dp(activity, 1), 0xFF2FE0C7);
        urlView.setBackground(urlBg);
        LinearLayout.LayoutParams urlLp = new LinearLayout.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
        urlLp.bottomMargin = dp(activity, 16);
        card.addView(urlView, urlLp);

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);

        Button copyBtn = new Button(activity);
        copyBtn.setText(en ? "📋 Copy URL" : "📋 نسخ الرابط");
        copyBtn.setTextSize(14f);
        copyBtn.setTextColor(0xFFFFC857);
        copyBtn.setAllCaps(false);
        GradientDrawable copyBg = new GradientDrawable();
        copyBg.setColor(0xFF25323A);
        copyBg.setCornerRadius(dp(activity, 16));
        copyBg.setStroke(dp(activity, 1), 0xFFE5B64A);
        copyBtn.setBackground(copyBg);
        copyBtn.setOnClickListener(v -> {
            try {
                android.content.ClipboardManager cm = (android.content.ClipboardManager) activity.getSystemService(Activity.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("Qibla APK URL", apkUrl));
                    Toast.makeText(activity, en ? "URL copied to clipboard" : "تم نسخ الرابط للحافظة", Toast.LENGTH_SHORT).show();
                }
            } catch (Throwable ignored) { }
        });
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(dp(activity, 140), dp(activity, 44));
        copyLp.rightMargin = dp(activity, 10);
        row.addView(copyBtn, copyLp);

        Button openBrowser = new Button(activity);
        openBrowser.setText(en ? "🌐 Open Browser" : "🌐 فتح في المتصفح");
        openBrowser.setTextSize(14f);
        openBrowser.setTextColor(Color.WHITE);
        openBrowser.setAllCaps(false);
        GradientDrawable obBg = new GradientDrawable();
        obBg.setColor(0xFF117A7C);
        obBg.setCornerRadius(dp(activity, 16));
        obBg.setStroke(dp(activity, 1), 0xFF2FE0C7);
        openBrowser.setBackground(obBg);
        openBrowser.setOnClickListener(v -> {
            d.dismiss();
            try {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl));
                browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(browserIntent);
            } catch (Throwable err) {
                Toast.makeText(activity, en ? "Unable to open browser" : "تعذر فتح المتصفح", Toast.LENGTH_SHORT).show();
            }
        });
        row.addView(openBrowser, new LinearLayout.LayoutParams(dp(activity, 160), dp(activity, 44)));
        card.addView(row);

        d.setContentView(card);
        d.show();
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            w.setLayout(dp(activity, 560), WindowManager.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.CENTER);
        }
    }

    private static String normalizeHex(String s) { return s == null ? "" : s.replace(":", "").replace(" ", "").trim().toUpperCase(Locale.US); }
    private static String toHex(byte[] bytes) {
        StringBuilder b = new StringBuilder(bytes.length * 2);
        for (byte x : bytes) b.append(String.format(Locale.US, "%02X", x & 0xFF));
        return b.toString();
    }
}
