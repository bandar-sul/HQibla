package sa.h9.qibla;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import org.json.JSONObject;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

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
                    String sha256 = normalizeHex(best.optString("sha256", ""));
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
        final String overlayTag = "h9_overlay_update";
        final boolean embedded = (activity instanceof MainActivity) && ((MainActivity) activity).getRootContainer() != null;
        final Dialog dialog = embedded ? null : new Dialog(activity);
        if (dialog != null) {
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            dialog.setCancelable(true);
        }

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = dp(activity, 24);
        card.setPadding(pad, dp(activity, 20), pad, dp(activity, 20));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF5171D23);
        bg.setCornerRadius(dp(activity, 26));
        bg.setStroke(dp(activity, 2), 0xFFE5B64A);
        card.setBackground(bg);

        TextView title = new TextView(activity);
        title.setText(en ? "New Official Update" : "تحديث رسمي جديد");
        title.setTextColor(0xFFFFC857);
        title.setTextSize(24f);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(FontHelper.getArabicTypeface(activity), android.graphics.Typeface.BOLD);
        card.addView(title, new LinearLayout.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView version = new TextView(activity);
        version.setText((en ? "Version " : "الإصدار ") + name + " (Build " + code + ")");
        version.setTextColor(0xFF2FE0C7);
        version.setTextSize(17f);
        version.setGravity(Gravity.CENTER);
        version.setTypeface(FontHelper.getArabicTypeface(activity), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams versionLp = new LinearLayout.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        versionLp.topMargin = dp(activity, 4);
        versionLp.bottomMargin = dp(activity, 10);
        card.addView(version, versionLp);

        ScrollView scroll = new ScrollView(activity);
        TextView body = new TextView(activity);
        String bodyText = (notes == null || notes.trim().isEmpty())
                ? (en ? "A newer version of Qibla is available." : "يتوفر إصدار أحدث للتطبيق.")
                : notes.trim();
        body.setText(bodyText + (en
                ? "\n\nThe APK will download inside the app, verify SHA-256 integrity, and install directly."
                : "\n\nسيتم تحميل ملف التحديث مباشرة داخل التطبيق والتحقق من بصمة الأمان SHA-256 قبل التثبيت."));
        body.setTextColor(0xFFF1F5F6);
        body.setTextSize(15f);
        body.setGravity(en ? Gravity.LEFT : Gravity.RIGHT);
        body.setTextDirection(en ? View.TEXT_DIRECTION_LTR : View.TEXT_DIRECTION_RTL);
        body.setTypeface(FontHelper.getArabicTypeface(activity));
        body.setLineSpacing(dp(activity, 2), 1.12f);
        scroll.addView(body, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        int paneH = embedded ? ((MainActivity) activity).getVisiblePaneHeight() : activity.getResources().getDisplayMetrics().heightPixels;
        int scrollMaxH = Math.min(dp(activity, 240), Math.max(dp(activity, 120), (int) (paneH * 0.42f)));
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, scrollMaxH);
        bodyLp.bottomMargin = dp(activity, 16);
        card.addView(scroll, bodyLp);

        LinearLayout buttons = new LinearLayout(activity);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);

        Button later = new Button(activity);
        later.setText(en ? "Later" : "لاحقًا");
        later.setTextSize(16f);
        later.setTextColor(0xFFFFC857);
        later.setTypeface(FontHelper.getArabicTypeface(activity), android.graphics.Typeface.BOLD);
        later.setAllCaps(false);
        GradientDrawable laterBg = new GradientDrawable();
        laterBg.setColor(0xFF25323A);
        laterBg.setCornerRadius(dp(activity, 18));
        laterBg.setStroke(dp(activity, 1), 0xFFE5B64A);
        later.setBackground(laterBg);
        later.setOnClickListener(v -> {
            if (embedded) {
                ((MainActivity) activity).dismissOverlayByTag(overlayTag);
            } else if (dialog != null) {
                dialog.dismiss();
            }
        });

        Button install = new Button(activity);
        install.setText(en ? "Download & Install" : "تنزيل وتثبيت مباشر");
        install.setTextSize(16f);
        install.setTextColor(Color.WHITE);
        install.setTypeface(FontHelper.getArabicTypeface(activity), android.graphics.Typeface.BOLD);
        install.setAllCaps(false);
        GradientDrawable installBg = new GradientDrawable();
        installBg.setColor(0xFF117A7C);
        installBg.setCornerRadius(dp(activity, 18));
        installBg.setStroke(dp(activity, 2), 0xFF2FE0C7);
        install.setBackground(installBg);
        install.setOnClickListener(v -> {
            if (embedded) {
                ((MainActivity) activity).dismissOverlayByTag(overlayTag);
            } else if (dialog != null) {
                dialog.dismiss();
            }
            downloadAndInstall(activity, code, apkUrl, sha256);
        });

        LinearLayout.LayoutParams btnLp1 = new LinearLayout.LayoutParams(0, dp(activity, 50), 0.9f);
        btnLp1.rightMargin = dp(activity, 10);
        buttons.addView(later, btnLp1);
        buttons.addView(install, new LinearLayout.LayoutParams(0, dp(activity, 50), 1.4f));
        card.addView(buttons, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        int paneW = embedded ? ((MainActivity) activity).getVisiblePaneWidth() : activity.getResources().getDisplayMetrics().widthPixels;
        int maxW = Math.min(Math.max(dp(activity, 260), paneW - dp(activity, 32)), dp(activity, 640));
        if (embedded) {
            ((MainActivity) activity).showModalCardOverlay(overlayTag, card, maxW, ViewGroup.LayoutParams.WRAP_CONTENT, true, null);
        } else if (dialog != null) {
            dialog.setContentView(card);
            dialog.show();
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                WindowManager.LayoutParams attrs = window.getAttributes();
                attrs.dimAmount = 0.72f;
                window.setAttributes(attrs);
                window.setLayout(maxW, WindowManager.LayoutParams.WRAP_CONTENT);
                window.setGravity(Gravity.CENTER);
            }
        }
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static void downloadAndInstall(Activity activity, int code, String apkUrl, String expectedSha256) {
        if (activity == null || activity.isFinishing()) return;
        final boolean en = isEn(activity);
        final String progressTag = "h9_overlay_update_progress";
        final boolean embedded = (activity instanceof MainActivity) && ((MainActivity) activity).getRootContainer() != null;
        final AtomicBoolean cancelled = new AtomicBoolean(false);

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(activity, 24), dp(activity, 22), dp(activity, 24), dp(activity, 20));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF6171D23);
        bg.setCornerRadius(dp(activity, 24));
        bg.setStroke(dp(activity, 2), 0xFFE5B64A);
        card.setBackground(bg);

        TextView title = new TextView(activity);
        title.setText(en ? "⬇️ Downloading Official Update" : "⬇️ جاري تنزيل التحديث الرسمي");
        title.setTextColor(0xFFFFC857);
        title.setTextSize(19f);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(FontHelper.getArabicTypeface(activity), android.graphics.Typeface.BOLD);
        card.addView(title);

        TextView status = new TextView(activity);
        status.setText(en ? "Connecting securely..." : "جاري الاتصال بالخادم...");
        status.setTextColor(0xFF2FE0C7);
        status.setTextSize(14.5f);
        status.setGravity(Gravity.CENTER);
        status.setTypeface(FontHelper.getArabicTypeface(activity));
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusLp.topMargin = dp(activity, 10);
        statusLp.bottomMargin = dp(activity, 12);
        card.addView(status, statusLp);

        ProgressBar progressBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(true);
        progressBar.setMax(100);
        LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, dp(activity, 12));
        pbLp.bottomMargin = dp(activity, 16);
        card.addView(progressBar, pbLp);

        Button cancelBtn = new Button(activity);
        cancelBtn.setText(en ? "Cancel" : "إلغاء التنزيل");
        cancelBtn.setTextSize(14f);
        cancelBtn.setTextColor(0xFFFFC857);
        cancelBtn.setTypeface(FontHelper.getArabicTypeface(activity), android.graphics.Typeface.BOLD);
        GradientDrawable cancelBg = new GradientDrawable();
        cancelBg.setColor(0xFF25323A);
        cancelBg.setCornerRadius(dp(activity, 14));
        cancelBg.setStroke(dp(activity, 1), 0xFFE5B64A);
        cancelBtn.setBackground(cancelBg);
        card.addView(cancelBtn, new LinearLayout.LayoutParams(dp(activity, 160), dp(activity, 44)));

        final Dialog fallbackDialog = embedded ? null : new Dialog(activity);
        if (fallbackDialog != null) {
            fallbackDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            fallbackDialog.setCancelable(false);
            fallbackDialog.setContentView(card);
            fallbackDialog.show();
        } else {
            int paneW = ((MainActivity) activity).getVisiblePaneWidth();
            int maxW = Math.min(Math.max(dp(activity, 250), paneW - dp(activity, 32)), dp(activity, 520));
            ((MainActivity) activity).showModalCardOverlay(progressTag, card, maxW, ViewGroup.LayoutParams.WRAP_CONTENT, false, null);
        }

        Runnable closeProgress = () -> {
            if (embedded) {
                ((MainActivity) activity).dismissOverlayByTag(progressTag);
            } else if (fallbackDialog != null && fallbackDialog.isShowing()) {
                fallbackDialog.dismiss();
            }
        };

        cancelBtn.setOnClickListener(v -> {
            cancelled.set(true);
            closeProgress.run();
        });

        new Thread(() -> {
            HttpURLConnection conn = null;
            File outFile = null;
            try {
                File dir = new File(activity.getExternalFilesDir(null), "updates");
                if (!dir.exists()) dir.mkdirs();
                outFile = new File(dir, "Qibla-v" + code + ".apk");
                if (outFile.exists()) outFile.delete();

                String currentUrl = apkUrl;
                int redirects = 0;
                while (redirects < 5) {
                    URL u = new URL(currentUrl);
                    conn = (HttpURLConnection) u.openConnection();
                    conn.setConnectTimeout(12000);
                    conn.setReadTimeout(20000);
                    conn.setInstanceFollowRedirects(true);
                    conn.setRequestProperty("User-Agent", "H9-Qibla/" + BuildConfig.VERSION_NAME);
                    int codeResp = conn.getResponseCode();
                    if (codeResp == HttpURLConnection.HTTP_MOVED_PERM
                            || codeResp == HttpURLConnection.HTTP_MOVED_TEMP
                            || codeResp == 307 || codeResp == 308) {
                        String loc = conn.getHeaderField("Location");
                        conn.disconnect();
                        if (loc == null || loc.isEmpty()) break;
                        currentUrl = loc;
                        redirects++;
                        continue;
                    }
                    break;
                }

                if (conn == null || conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    throw new IllegalStateException("HTTP " + (conn == null ? -1 : conn.getResponseCode()));
                }

                final int totalBytes = conn.getContentLength();
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] buf = new byte[16384];
                long downloaded = 0L;
                long lastUiUpdate = 0L;

                try (InputStream in = new BufferedInputStream(conn.getInputStream());
                     FileOutputStream fos = new FileOutputStream(outFile)) {
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        if (cancelled.get()) {
                            outFile.delete();
                            return;
                        }
                        fos.write(buf, 0, n);
                        md.update(buf, 0, n);
                        downloaded += n;
                        long now = System.currentTimeMillis();
                        if (now - lastUiUpdate >= 120L) {
                            lastUiUpdate = now;
                            final long dl = downloaded;
                            activity.runOnUiThread(() -> {
                                if (totalBytes > 0) {
                                    int pct = (int) Math.min(100, (dl * 100L) / totalBytes);
                                    progressBar.setIndeterminate(false);
                                    progressBar.setProgress(pct);
                                    status.setText(String.format(Locale.US,
                                            en ? "Downloaded %d%% (%.1f / %.1f MB)" : "تم التحميل %d%% (%.1f / %.1f م.ب)",
                                            pct, dl / 1048576f, totalBytes / 1048576f));
                                } else {
                                    status.setText(String.format(Locale.US,
                                            en ? "Downloaded %.1f MB..." : "جاري التحميل (%.1f م.ب)...",
                                            dl / 1048576f));
                                }
                            });
                        }
                    }
                    fos.getFD().sync();
                }

                String actualSha = toHex(md.digest());
                if (expectedSha256 != null && !expectedSha256.isEmpty() && !expectedSha256.equals(actualSha)) {
                    outFile.delete();
                    activity.runOnUiThread(() -> {
                        closeProgress.run();
                        Toast.makeText(activity,
                                en ? "Downloaded update failed SHA-256 security check"
                                   : "فشل التحقق من بصمة الأمان SHA-256 لملف التحديث",
                                Toast.LENGTH_LONG).show();
                        showDownloadFallbackDialog(activity, apkUrl);
                    });
                    return;
                }

                final File verifiedApk = outFile;
                activity.runOnUiThread(() -> {
                    closeProgress.run();
                    launchApkInstaller(activity, verifiedApk, apkUrl);
                });
            } catch (Throwable t) {
                if (outFile != null && outFile.exists()) outFile.delete();
                if (!cancelled.get()) {
                    activity.runOnUiThread(() -> {
                        closeProgress.run();
                        Toast.makeText(activity,
                                en ? "Direct download unavailable — opening alternative options"
                                   : "تعذر التنزيل المباشر — تم فتح خيارات التنزيل البديلة",
                                Toast.LENGTH_SHORT).show();
                        showDownloadFallbackDialog(activity, apkUrl);
                    });
                }
            } finally {
                if (conn != null) conn.disconnect();
            }
        }, "H9-OTA-Download").start();
    }

    private static void launchApkInstaller(Activity activity, File apkFile, String fallbackUrl) {
        if (activity == null || activity.isFinishing() || apkFile == null || !apkFile.isFile()) return;
        final boolean en = isEn(activity);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.getPackageManager().canRequestPackageInstalls()) {
            showInstallPermissionPrompt(activity, apkFile, fallbackUrl);
            return;
        }

        try {
            Uri apkUri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".files", apkFile);
            Intent installIntent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
            installIntent.setData(apkUri);
            installIntent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
            installIntent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
            installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(installIntent);
        } catch (Throwable firstErr) {
            try {
                Uri apkUri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".files", apkFile);
                Intent viewIntent = new Intent(Intent.ACTION_VIEW);
                viewIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(viewIntent);
            } catch (Throwable secondErr) {
                Toast.makeText(activity,
                        en ? "Unable to open installer directly" : "تعذر فتح مُثبّت النظام تلقائياً",
                        Toast.LENGTH_LONG).show();
                showDownloadFallbackDialog(activity, fallbackUrl);
            }
        }
    }

    private static void showInstallPermissionPrompt(Activity activity, File apkFile, String fallbackUrl) {
        final boolean en = isEn(activity);
        final String tag = "h9_overlay_install_perm";
        final boolean embedded = (activity instanceof MainActivity) && ((MainActivity) activity).getRootContainer() != null;

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(activity, 24), dp(activity, 20), dp(activity, 24), dp(activity, 20));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF6171D23);
        bg.setCornerRadius(dp(activity, 24));
        bg.setStroke(dp(activity, 2), 0xFFE5B64A);
        card.setBackground(bg);

        TextView title = new TextView(activity);
        title.setText(en ? "Allow App Updates" : "السماح بتثبيت التحديثات");
        title.setTextColor(0xFFFFC857);
        title.setTextSize(20f);
        title.setTypeface(FontHelper.getArabicTypeface(activity), android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        card.addView(title);

        TextView msg = new TextView(activity);
        msg.setText(en
                ? "The update APK has been downloaded and verified! Please enable \"Allow from this source\" for H9 Qibla, then tap Install."
                : "تم تنزيل ملف التحديث والتحقق منه بنجاح! يرجى تفعيل «السماح من هذا المصدر» لتطبيق قبلة حتى يكتمل التثبيت مباشرة.");
        msg.setTextColor(0xFFF1F5F6);
        msg.setTextSize(15f);
        msg.setTypeface(FontHelper.getArabicTypeface(activity));
        msg.setGravity(Gravity.CENTER);
        msg.setTextDirection(en ? View.TEXT_DIRECTION_LTR : View.TEXT_DIRECTION_RTL);
        LinearLayout.LayoutParams msgLp = new LinearLayout.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        msgLp.topMargin = dp(activity, 10);
        msgLp.bottomMargin = dp(activity, 16);
        card.addView(msg, msgLp);

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);

        Button settingsBtn = new Button(activity);
        settingsBtn.setText(en ? "Open Permission" : "فتح الإذن");
        settingsBtn.setTextSize(14f);
        settingsBtn.setTextColor(0xFFFFC857);
        settingsBtn.setTypeface(FontHelper.getArabicTypeface(activity), android.graphics.Typeface.BOLD);
        GradientDrawable sBg = new GradientDrawable();
        sBg.setColor(0xFF25323A);
        sBg.setCornerRadius(dp(activity, 16));
        sBg.setStroke(dp(activity, 1), 0xFFE5B64A);
        settingsBtn.setBackground(sBg);
        settingsBtn.setOnClickListener(v -> {
            try {
                Intent permIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + activity.getPackageName()));
                permIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(permIntent);
            } catch (Throwable ignored) { }
        });

        Button retryBtn = new Button(activity);
        retryBtn.setText(en ? "Install Now" : "تثبيت الآن");
        retryBtn.setTextSize(14f);
        retryBtn.setTextColor(Color.WHITE);
        retryBtn.setTypeface(FontHelper.getArabicTypeface(activity), android.graphics.Typeface.BOLD);
        GradientDrawable rBg = new GradientDrawable();
        rBg.setColor(0xFF117A7C);
        rBg.setCornerRadius(dp(activity, 16));
        rBg.setStroke(dp(activity, 1), 0xFF2FE0C7);
        retryBtn.setBackground(rBg);
        retryBtn.setOnClickListener(v -> {
            if (embedded) ((MainActivity) activity).dismissOverlayByTag(tag);
            launchApkInstaller(activity, apkFile, fallbackUrl);
        });

        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, dp(activity, 48), 1f);
        lp1.rightMargin = dp(activity, 10);
        row.addView(settingsBtn, lp1);
        row.addView(retryBtn, new LinearLayout.LayoutParams(0, dp(activity, 48), 1f));
        card.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (embedded) {
            int paneW = ((MainActivity) activity).getVisiblePaneWidth();
            int maxW = Math.min(Math.max(dp(activity, 250), paneW - dp(activity, 32)), dp(activity, 560));
            ((MainActivity) activity).showModalCardOverlay(tag, card, maxW, ViewGroup.LayoutParams.WRAP_CONTENT, true, null);
        } else {
            Dialog d = new Dialog(activity);
            d.requestWindowFeature(Window.FEATURE_NO_TITLE);
            d.setContentView(card);
            d.show();
        }
    }

    public static void showDownloadFallbackDialog(Activity activity, String apkUrl) {
        if (activity == null || activity.isFinishing()) return;
        final boolean en = isEn(activity);
        final String overlayTag = "h9_overlay_update_fallback";
        final boolean embedded = (activity instanceof MainActivity) && ((MainActivity) activity).getRootContainer() != null;
        final Dialog d = embedded ? null : new Dialog(activity);
        if (d != null) {
            d.requestWindowFeature(Window.FEATURE_NO_TITLE);
            d.setCancelable(true);
        }

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
        t.setTypeface(FontHelper.getArabicTypeface(activity), android.graphics.Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        card.addView(t, new LinearLayout.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, dp(activity, 46)));

        TextView msg = new TextView(activity);
        msg.setText(en
                ? "You can download the APK from H9 Store (h9-story.com) or copy the direct link below:"
                : "يمكنك تنزيل ملف التحديث عبر متجر هافال (h9-story.com) أو نسخ الرابط المباشر أدناه:");
        msg.setTextColor(0xFFF1F5F6);
        msg.setTextSize(15f);
        msg.setGravity(Gravity.CENTER);
        msg.setTypeface(FontHelper.getArabicTypeface(activity));
        msg.setTextDirection(en ? View.TEXT_DIRECTION_LTR : View.TEXT_DIRECTION_RTL);
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
        copyBtn.setTypeface(FontHelper.getArabicTypeface(activity), android.graphics.Typeface.BOLD);
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
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(0, dp(activity, 46), 1.0f);
        copyLp.rightMargin = dp(activity, 10);
        row.addView(copyBtn, copyLp);

        Button storeQrBtn = new Button(activity);
        storeQrBtn.setText(en ? "📱 Store QR" : "📱 باركود المتجر");
        storeQrBtn.setTextSize(14f);
        storeQrBtn.setTextColor(Color.WHITE);
        storeQrBtn.setTypeface(FontHelper.getArabicTypeface(activity), android.graphics.Typeface.BOLD);
        storeQrBtn.setAllCaps(false);
        GradientDrawable qrBg = new GradientDrawable();
        qrBg.setColor(0xFF117A7C);
        qrBg.setCornerRadius(dp(activity, 16));
        qrBg.setStroke(dp(activity, 1), 0xFF2FE0C7);
        storeQrBtn.setBackground(qrBg);
        storeQrBtn.setOnClickListener(v -> {
            if (embedded) {
                ((MainActivity) activity).dismissOverlayByTag(overlayTag);
            } else if (d != null) {
                d.dismiss();
            }
            HavalStoreQrDialog.show(activity);
        });
        row.addView(storeQrBtn, new LinearLayout.LayoutParams(0, dp(activity, 46), 1.0f));
        card.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        int paneW = embedded ? ((MainActivity) activity).getVisiblePaneWidth() : activity.getResources().getDisplayMetrics().widthPixels;
        int maxW = Math.min(Math.max(dp(activity, 250), paneW - dp(activity, 32)), dp(activity, 560));
        if (embedded) {
            ((MainActivity) activity).showModalCardOverlay(overlayTag, card, maxW, ViewGroup.LayoutParams.WRAP_CONTENT, true, null);
        } else if (d != null) {
            d.setContentView(card);
            d.show();
            Window w = d.getWindow();
            if (w != null) {
                w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
                w.setLayout(maxW, WindowManager.LayoutParams.WRAP_CONTENT);
                w.setGravity(Gravity.CENTER);
            }
        }
    }

    private static String normalizeHex(String s) { return s == null ? "" : s.replace(":", "").replace(" ", "").trim().toUpperCase(Locale.US); }
    private static String toHex(byte[] bytes) {
        StringBuilder b = new StringBuilder(bytes.length * 2);
        for (byte x : bytes) b.append(String.format(Locale.US, "%02X", x & 0xFF));
        return b.toString();
    }
}
