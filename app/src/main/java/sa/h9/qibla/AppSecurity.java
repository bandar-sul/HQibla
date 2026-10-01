package sa.h9.qibla;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import java.io.File;
import java.security.MessageDigest;
import java.util.Locale;

public final class AppSecurity {
    private AppSecurity() { }

    public static boolean verifyOwnSignature(Context context) {
        String expected = normalize(BuildConfig.EXPECTED_CERT_SHA256);
        if (expected.isEmpty()) return false;
        try {
            PackageInfo pi = context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES);
            return pi.signatures != null && pi.signatures.length == 1 && expected.equals(certSha256(pi.signatures[0].toByteArray()));
        } catch (Throwable e) { return false; }
    }

    public static boolean verifyArchiveSignature(Context context, File apk) {
        String expected = normalize(BuildConfig.EXPECTED_CERT_SHA256);
        if (expected.isEmpty() || apk == null || !apk.isFile()) return false;
        try {
            PackageInfo pi = context.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), PackageManager.GET_SIGNATURES);
            return pi != null && pi.signatures != null && pi.signatures.length == 1 && expected.equals(certSha256(pi.signatures[0].toByteArray()));
        } catch (Throwable e) { return false; }
    }

    public static void showIntegrityFailureAndExit(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        new AlertDialog.Builder(activity).setTitle("حماية H9 Qibla")
                .setMessage("تم اكتشاف نسخة غير موقعة بالتوقيع الرسمي. لن يتم تشغيل هذه النسخة.")
                .setCancelable(false).setPositiveButton("إغلاق", (d, w) -> activity.finishAndRemoveTask()).show();
    }

    private static String certSha256(byte[] cert) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] out = md.digest(cert);
        StringBuilder b = new StringBuilder(out.length * 2);
        for (byte x : out) b.append(String.format(Locale.US, "%02X", x & 0xFF));
        return b.toString();
    }
    private static String normalize(String s) { return s == null ? "" : s.replace(":", "").replace(" ", "").trim().toUpperCase(Locale.US); }
}
