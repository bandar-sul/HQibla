package sa.h9.qibla;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
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

    public static void checkForUpdates(Activity activity, boolean manual) {
        if (activity == null || activity.isFinishing()) return;
        if (!manual) {
            long last = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE).getLong(KEY_LAST_AUTO_CHECK, 0L);
            if (System.currentTimeMillis() - last < AUTO_CHECK_INTERVAL) return;
            activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE).edit().putLong(KEY_LAST_AUTO_CHECK, System.currentTimeMillis()).apply();
        }
        if (manual) Toast.makeText(activity, "جاري فحص التحديثات…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            HttpURLConnection c = null;
            try {
                URL u = new URL(BuildConfig.UPDATE_MANIFEST_URL);
                c = (HttpURLConnection) u.openConnection();
                c.setConnectTimeout(7000); c.setReadTimeout(7000); c.setInstanceFollowRedirects(true);
                c.setRequestProperty("Accept", "application/json");
                c.setRequestProperty("User-Agent", "H9-Qibla/" + BuildConfig.VERSION_NAME);
                if (c.getResponseCode() != 200) throw new IllegalStateException("HTTP");
                StringBuilder body = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
                    String line; while ((line = br.readLine()) != null) body.append(line);
                }
                JSONObject j = new JSONObject(body.toString());
                int remoteCode = j.getInt("versionCode");
                String remoteName = j.optString("versionName", String.valueOf(remoteCode));
                String apkUrl = j.getString("apkUrl");
                String sha256 = normalizeHex(j.getString("sha256"));
                String notes = j.optString("notes", "");
                if (remoteCode <= BuildConfig.VERSION_CODE) {
                    if (manual) activity.runOnUiThread(() -> Toast.makeText(activity, "أنت على أحدث إصدار", Toast.LENGTH_LONG).show());
                    return;
                }
                activity.runOnUiThread(() -> showUpdateDialog(activity, remoteCode, remoteName, apkUrl, sha256, notes));
            } catch (Throwable e) {
                if (manual) activity.runOnUiThread(() -> Toast.makeText(activity, "تعذر فحص التحديثات الآن", Toast.LENGTH_LONG).show());
            } finally { if (c != null) c.disconnect(); }
        }, "H9-OTA-Check").start();
    }

    private static void showUpdateDialog(Activity activity, int code, String name, String apkUrl, String sha256, String notes) {
        if (activity.isFinishing()) return;
        String message = "الإصدار الجديد: " + name + (notes == null || notes.trim().isEmpty() ? "" : "\n\n" + notes)
                + "\n\nسيتم التحقق من SHA-256 وشهادة التوقيع قبل التثبيت.";
        new AlertDialog.Builder(activity).setTitle("يتوفر تحديث H9 Qibla").setMessage(message)
                .setNegativeButton("لاحقًا", null)
                .setPositiveButton("تنزيل وتثبيت", (d, w) -> downloadAndInstall(activity, code, apkUrl, sha256)).show();
    }

    private static void downloadAndInstall(Activity activity, int code, String apkUrl, String expectedSha256) {
        Toast.makeText(activity, "جاري تنزيل التحديث…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            HttpURLConnection c = null; File out = null;
            try {
                URL u = new URL(apkUrl);
                if (!"https".equalsIgnoreCase(u.getProtocol())) throw new SecurityException("HTTPS required");
                c = (HttpURLConnection) u.openConnection();
                c.setConnectTimeout(10000); c.setReadTimeout(20000); c.setInstanceFollowRedirects(true);
                c.setRequestProperty("User-Agent", "H9-Qibla-Updater/" + BuildConfig.VERSION_NAME);
                if (c.getResponseCode() != 200) throw new IllegalStateException("HTTP");
                File dir = new File(activity.getExternalFilesDir(null), "updates");
                if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("mkdir");
                out = new File(dir, "H9-Qibla-" + code + ".apk");
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                try (InputStream in = new BufferedInputStream(c.getInputStream()); FileOutputStream fos = new FileOutputStream(out)) {
                    byte[] buf = new byte[65536]; int n; long total = 0;
                    while ((n = in.read(buf)) > 0) {
                        total += n; if (total > 200L * 1024L * 1024L) throw new SecurityException("too large");
                        fos.write(buf, 0, n); md.update(buf, 0, n);
                    }
                    fos.getFD().sync();
                }
                if (!toHex(md.digest()).equalsIgnoreCase(normalizeHex(expectedSha256))) throw new SecurityException("hash");
                PackageInfo archive = activity.getPackageManager().getPackageArchiveInfo(out.getAbsolutePath(), PackageManager.GET_META_DATA);
                if (archive == null || !BuildConfig.APPLICATION_ID.equals(archive.packageName)) throw new SecurityException("package");
                if (archive.versionCode <= BuildConfig.VERSION_CODE) throw new SecurityException("downgrade");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.getPackageManager().canRequestPackageInstalls()) {
                    final File f = out;
                    activity.runOnUiThread(() -> {
                        Toast.makeText(activity, "فعّل السماح بتثبيت التطبيقات لهذا التطبيق ثم أعد فحص التحديث", Toast.LENGTH_LONG).show();
                        activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + activity.getPackageName())));
                    });
                    return;
                }
                final File apk = out;
                activity.runOnUiThread(() -> install(activity, apk));
            } catch (Throwable e) {
                if (out != null && out.exists()) out.delete();
                activity.runOnUiThread(() -> Toast.makeText(activity, "تم رفض التحديث: فشل التحقق الأمني أو التنزيل", Toast.LENGTH_LONG).show());
            } finally { if (c != null) c.disconnect(); }
        }, "H9-OTA-Download").start();
    }

    private static void install(Activity activity, File apk) {
        try {
            Uri uri = FileProvider.getUriForFile(activity, BuildConfig.APPLICATION_ID + ".files", apk);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(i);
        } catch (Throwable e) {
            Toast.makeText(activity, "تعذر فتح مثبت الحزمة", Toast.LENGTH_LONG).show();
        }
    }

    private static String normalizeHex(String s) { return s == null ? "" : s.replace(":", "").replace(" ", "").trim().toUpperCase(Locale.US); }
    private static String toHex(byte[] bytes) {
        StringBuilder b = new StringBuilder(bytes.length * 2);
        for (byte x : bytes) b.append(String.format(Locale.US, "%02X", x & 0xFF));
        return b.toString();
    }
}
