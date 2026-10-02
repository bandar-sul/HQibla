package sa.h9.qibla;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import java.io.File;
import java.security.MessageDigest;
import java.util.Locale;

public final class AppSecurity {
    private AppSecurity() { }

    public static boolean verifyOwnSignature(Context context) {
        String expected = normalize(BuildConfig.EXPECTED_CERT_SHA256);
        if (expected.isEmpty()) return false;
        try {
            if (matchesInstalledPackage(context, expected)) return true;
        } catch (Throwable ignored) { }

        // Some Android 9 OEM builds return incomplete signing data for an installed package.
        // Verify the actual APK on disk as a second authoritative path.
        try {
            String source = context.getApplicationInfo().sourceDir;
            if (source != null && matchesArchive(context, new File(source), expected)) return true;
        } catch (Throwable ignored) { }
        return false;
    }

    public static boolean verifyArchiveSignature(Context context, File apk) {
        String expected = normalize(BuildConfig.EXPECTED_CERT_SHA256);
        if (expected.isEmpty() || apk == null || !apk.isFile()) return false;
        return matchesArchive(context, apk, expected);
    }

    private static boolean matchesInstalledPackage(Context context, String expected) throws Exception {
        PackageManager pm = context.getPackageManager();
        if (Build.VERSION.SDK_INT >= 28) {
            PackageInfo pi = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
            if (pi.signingInfo != null) {
                Signature[] signers = pi.signingInfo.hasMultipleSigners()
                        ? pi.signingInfo.getApkContentsSigners()
                        : pi.signingInfo.getSigningCertificateHistory();
                if (matchesAny(signers, expected)) return true;
            }
        }
        PackageInfo legacy = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES);
        return matchesAny(legacy.signatures, expected);
    }

    private static boolean matchesArchive(Context context, File apk, String expected) {
        try {
            PackageManager pm = context.getPackageManager();
            if (Build.VERSION.SDK_INT >= 28) {
                PackageInfo pi = pm.getPackageArchiveInfo(apk.getAbsolutePath(), PackageManager.GET_SIGNING_CERTIFICATES);
                if (pi != null && pi.signingInfo != null) {
                    Signature[] signers = pi.signingInfo.hasMultipleSigners()
                            ? pi.signingInfo.getApkContentsSigners()
                            : pi.signingInfo.getSigningCertificateHistory();
                    if (matchesAny(signers, expected)) return true;
                }
            }
            PackageInfo legacy = pm.getPackageArchiveInfo(apk.getAbsolutePath(), PackageManager.GET_SIGNATURES);
            return legacy != null && matchesAny(legacy.signatures, expected);
        } catch (Throwable e) {
            return false;
        }
    }

    private static boolean matchesAny(Signature[] signatures, String expected) throws Exception {
        if (signatures == null || signatures.length == 0) return false;
        for (Signature s : signatures) {
            if (s != null && expected.equals(certSha256(s.toByteArray()))) return true;
        }
        return false;
    }

    public static void showIntegrityFailureAndExit(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        new AlertDialog.Builder(activity)
                .setTitle("حماية H9 Qibla")
                .setMessage("تعذر التحقق من التوقيع على هذه الشاشة. سيتم تشغيل التطبيق، لكن التحديثات ستظل مقيدة بالتوقيع الرسمي.")
                .setCancelable(false)
                .setPositiveButton("متابعة", (d, w) -> d.dismiss())
                .show();
    }

    private static String certSha256(byte[] cert) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] out = md.digest(cert);
        StringBuilder b = new StringBuilder(out.length * 2);
        for (byte x : out) b.append(String.format(Locale.US, "%02X", x & 0xFF));
        return b.toString();
    }

    private static String normalize(String s) {
        return s == null ? "" : s.replace(":", "").replace(" ", "").trim().toUpperCase(Locale.US);
    }
}
