package sa.h9.qibla;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.HashMap;
import java.util.Map;

/**
 * Dedicated QR Code dialog for Haval H9 car screens.
 * Allows drivers and passengers to scan the barcode with their phone camera
 * to directly download, update, or share the Haval H9 Qibla app.
 */
public final class HavalStoreQrDialog {

    public static final String HAVAL_STORE_URL = "https://h9-story.com/";

    private HavalStoreQrDialog() {}

    public static void show(Context context) {
        if (context == null) return;

        final String overlayTag = "h9_overlay_qr";
        final boolean embedded = (context instanceof MainActivity) && ((MainActivity) context).getRootContainer() != null;
        final Dialog dialog = embedded ? null : new Dialog(context);
        if (dialog != null) {
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            dialog.setCancelable(true);
            dialog.setCanceledOnTouchOutside(true);

            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }
        }

        int dpVal = (int) (context.getResources().getDisplayMetrics().density);
        int dp8 = 8 * dpVal;
        int dp12 = 12 * dpVal;
        int dp16 = 16 * dpVal;
        int dp20 = 20 * dpVal;
        int dp24 = 24 * dpVal;

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp24, dp20, dp24, dp20);

        GradientDrawable rootBg = new GradientDrawable();
        rootBg.setColor(0xF8051712); // Deep obsidian emerald
        rootBg.setCornerRadius(22 * dpVal);
        rootBg.setStroke(2 * dpVal, 0xFFFFD768); // Gold border
        root.setBackground(rootBg);

        // Header Title
        TextView title = new TextView(context);
        title.setText("🚗 متجر هافال H9 للسيارات الذكية");
        title.setTextSize(19f);
        title.setTextColor(0xFFFFD768);
        title.setTypeface(FontHelper.getArabicTypeface(context), Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView subTitle = new TextView(context);
        subTitle.setText("امسح الباركود بكاميرا الجوال للتحميل والتحديثات المباشرة");
        subTitle.setTextSize(13.5f);
        subTitle.setTextColor(0xFFD4E8E2);
        subTitle.setTypeface(FontHelper.getArabicTypeface(context), Typeface.NORMAL);
        subTitle.setGravity(Gravity.CENTER);
        subTitle.setPadding(0, dp8, 0, dp16);
        root.addView(subTitle);

        // QR Code Container
        LinearLayout qrCard = new LinearLayout(context);
        qrCard.setOrientation(LinearLayout.VERTICAL);
        qrCard.setGravity(Gravity.CENTER);
        qrCard.setPadding(dp12, dp12, dp12, dp12);

        GradientDrawable qrCardBg = new GradientDrawable();
        qrCardBg.setColor(0xFFFFFFFF);
        qrCardBg.setCornerRadius(16 * dpVal);
        qrCardBg.setStroke(2 * dpVal, 0xFF00F5C8);
        qrCard.setBackground(qrCardBg);

        ImageView ivQr = new ImageView(context);
        int paneH = embedded ? ((MainActivity) context).getVisiblePaneHeight() : context.getResources().getDisplayMetrics().heightPixels;
        int qrSize = Math.min(220 * dpVal, Math.max(130 * dpVal, paneH / 3));
        Bitmap qrBitmap = generateQrBitmap(HAVAL_STORE_URL, qrSize);
        if (qrBitmap != null) {
            ivQr.setImageBitmap(qrBitmap);
        }
        qrCard.addView(ivQr, new LinearLayout.LayoutParams(qrSize, qrSize));
        root.addView(qrCard);

        // App Link URL
        TextView tvUrl = new TextView(context);
        tvUrl.setText("🔗 h9-story.com");
        tvUrl.setTextSize(14f);
        tvUrl.setTextColor(0xFF00F5C8);
        tvUrl.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        tvUrl.setGravity(Gravity.CENTER);
        tvUrl.setPadding(0, dp12, 0, dp16);
        root.addView(tvUrl);

        // Action Buttons Row (Copy Link & Open Browser)
        LinearLayout btnRow = new LinearLayout(context);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);

        Button btnCopy = new Button(context);
        btnCopy.setText("📋 نسخ الرابط");
        btnCopy.setTextSize(13.5f);
        btnCopy.setTextColor(Color.WHITE);
        btnCopy.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        GradientDrawable copyBg = new GradientDrawable();
        copyBg.setColor(0xFF144D3E);
        copyBg.setCornerRadius(12 * dpVal);
        copyBg.setStroke(dpVal, 0xFF00F5C8);
        btnCopy.setBackground(copyBg);
        btnCopy.setOnClickListener(v -> {
            try {
                ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("Haval H9 Store", HAVAL_STORE_URL));
                    Toast.makeText(context, "✅ تم نسخ رابط متجر هافال H9 (h9-story.com) للحافظة", Toast.LENGTH_SHORT).show();
                }
            } catch (Throwable ignored) { }
        });

        LinearLayout.LayoutParams btnCopyLp = new LinearLayout.LayoutParams(0, 42 * dpVal, 1.0f);
        btnCopyLp.rightMargin = dp8;
        btnRow.addView(btnCopy, btnCopyLp);

        Button btnOpen = new Button(context);
        btnOpen.setText("🌐 فتح بالمتصفح");
        btnOpen.setTextSize(13.5f);
        btnOpen.setTextColor(Color.WHITE);
        btnOpen.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        GradientDrawable openBg = new GradientDrawable();
        openBg.setColor(0xFF00695C);
        openBg.setCornerRadius(12 * dpVal);
        openBg.setStroke(dpVal, 0xFFFFD768);
        btnOpen.setBackground(openBg);
        btnOpen.setOnClickListener(v -> {
            try {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(HAVAL_STORE_URL));
                browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(browserIntent);
            } catch (Throwable t) {
                Toast.makeText(context, "⚠️ تعذر فتح المتصفح مباشرة", Toast.LENGTH_SHORT).show();
            }
        });
        btnRow.addView(btnOpen, new LinearLayout.LayoutParams(0, 42 * dpVal, 1.0f));

        root.addView(btnRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Close Button
        Button btnClose = new Button(context);
        btnClose.setText("✕ إغلاق");
        btnClose.setTextSize(13f);
        btnClose.setTextColor(0xFF94A3B8);
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setColor(0x33101E28);
        closeBg.setCornerRadius(12 * dpVal);
        btnClose.setBackground(closeBg);
        btnClose.setOnClickListener(v -> {
            if (embedded) {
                ((MainActivity) context).dismissOverlayByTag(overlayTag);
            } else if (dialog != null) {
                dialog.dismiss();
            }
        });

        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 38 * dpVal);
        closeLp.topMargin = dp8;
        root.addView(btnClose, closeLp);

        int screenW = embedded ? ((MainActivity) context).getVisiblePaneWidth() : context.getResources().getDisplayMetrics().widthPixels;
        int maxW = Math.min(Math.max(240 * dpVal, screenW - 32 * dpVal), 380 * dpVal);
        if (embedded) {
            ((MainActivity) context).showModalCardOverlay(overlayTag, root, maxW, ViewGroup.LayoutParams.WRAP_CONTENT, true, null);
        } else if (dialog != null) {
            dialog.setContentView(root, new ViewGroup.LayoutParams(maxW, ViewGroup.LayoutParams.WRAP_CONTENT));
            dialog.show();
        }
    }

    public static Bitmap generateQrBitmap(String content, int sizePx) {
        if (content == null || content.isEmpty()) return null;
        try {
            QRCodeWriter writer = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.MARGIN, 1);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

            BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints);
            int width = matrix.getWidth();
            int height = matrix.getHeight();
            Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

            int colorDark = 0xFF051712; // High contrast dark emerald
            int colorLight = 0xFFFFFFFF; // Pure white

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    bmp.setPixel(x, y, matrix.get(x, y) ? colorDark : colorLight);
                }
            }
            return bmp;
        } catch (Throwable t) {
            return null;
        }
    }
}
