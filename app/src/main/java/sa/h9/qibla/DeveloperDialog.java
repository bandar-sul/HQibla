package sa.h9.qibla;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Royal luxury About Developer dialog featuring:
 * - Developer: بندر بن سليمان
 * - TikTok: h9_haval
 * - Snapchat: h9_haval
 * - Online Store: متجر H9 (https://h9-story.com)
 */
public final class DeveloperDialog {

    private DeveloperDialog() { }

    public static void show(Activity activity, boolean isEn) {
        if (activity == null || activity.isFinishing()) return;
        Context ctx = activity;

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
        }

        ScrollView scroll = new ScrollView(ctx);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(ctx, 24), dp(ctx, 22), dp(ctx, 24), dp(ctx, 22));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF40C1820);
        bg.setCornerRadius(dp(ctx, 22));
        bg.setStroke(dp(ctx, 1.8f), 0xFFE5B64A);
        root.setBackground(bg);

        // Header Title
        TextView title = new TextView(ctx);
        title.setText(isEn ? "👑 About Developer" : "👑 حول المطور");
        title.setTextSize(22f);
        title.setTextColor(0xFFE5B64A);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        // Subtitle / Developer Name
        TextView nameView = new TextView(ctx);
        nameView.setText(isEn ? "Developer: Bandar Bin Sulaiman" : "المطور / بندر بن سليمان");
        nameView.setTextSize(19f);
        nameView.setTextColor(0xFFFFFFFF);
        nameView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        nameView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameLp.setMargins(0, dp(ctx, 8), 0, dp(ctx, 4));
        root.addView(nameView, nameLp);

        TextView roleView = new TextView(ctx);
        roleView.setText(isEn ? "Developer of H9 Automotive & Qibla Systems" : "تطوير تطبيقات وتحديثات أنظمة وشاشات سيارات H9");
        roleView.setTextSize(13f);
        roleView.setTextColor(0xFF2FE0C7);
        roleView.setGravity(Gravity.CENTER);
        root.addView(roleView);

        // Divider
        View div1 = new View(ctx);
        div1.setBackgroundColor(0x33E5B64A);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 1));
        divLp.setMargins(0, dp(ctx, 16), 0, dp(ctx, 16));
        root.addView(div1, divLp);

        // Social Media Header
        TextView socialHeader = new TextView(ctx);
        socialHeader.setText(isEn ? "📱 Follow & Connect on Social Media" : "📱 تابعوني على وسائل التواصل الاجتماعي");
        socialHeader.setTextSize(15f);
        socialHeader.setTextColor(0xFFE5B64A);
        socialHeader.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(socialHeader);

        // TikTok Button
        Button tiktokBtn = createSocialButton(ctx, "🎵  تيك توك | TikTok: @h9_haval", 0xFF000000, 0xFF2FE0C7, () -> {
            openUrl(ctx, "https://www.tiktok.com/@h9_haval", isEn);
        });
        root.addView(tiktokBtn);

        // Snapchat Button
        Button snapBtn = createSocialButton(ctx, "👻  سناب شات | Snapchat: h9_haval", 0xFF2C2802, 0xFFFFFC00, () -> {
            openUrl(ctx, "https://www.snapchat.com/add/h9_haval", isEn);
        });
        root.addView(snapBtn);

        // Divider 2
        View div2 = new View(ctx);
        div2.setBackgroundColor(0x33E5B64A);
        LinearLayout.LayoutParams div2Lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 1));
        div2Lp.setMargins(0, dp(ctx, 16), 0, dp(ctx, 16));
        root.addView(div2, div2Lp);

        // Online Store Section
        TextView storeHeader = new TextView(ctx);
        storeHeader.setText(isEn ? "🛍️ Official Online Store" : "🛍️ متجرنا الإلكتروني الرسمي");
        storeHeader.setTextSize(15f);
        storeHeader.setTextColor(0xFFE5B64A);
        storeHeader.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(storeHeader);

        Button storeBtn = createSocialButton(ctx, "🌐  متجر H9 الإلكتروني (h9-story.com)", 0xFF12434E, 0xFF2FE0C7, () -> {
            openUrl(ctx, "https://h9-story.com", isEn);
        });
        root.addView(storeBtn);

        // Close Button
        Button closeBtn = new Button(ctx);
        closeBtn.setText(isEn ? "✕ Close" : "✕ إغلاق والعودة");
        closeBtn.setTextSize(15f);
        closeBtn.setTextColor(0xFFFFFFFF);
        closeBtn.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setColor(0xDD1B2932);
        closeBg.setCornerRadius(dp(ctx, 14));
        closeBg.setStroke(dp(ctx, 1.2f), 0x88E5B64A);
        closeBtn.setBackground(closeBg);
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 48));
        closeLp.setMargins(0, dp(ctx, 20), 0, 0);
        closeBtn.setOnClickListener(v -> dialog.dismiss());
        root.addView(closeBtn, closeLp);

        scroll.addView(root);
        dialog.setContentView(scroll);

        // Responsive width constraints for automotive landscape
        int screenW = ctx.getResources().getDisplayMetrics().widthPixels;
        int maxDialogW = Math.min(screenW - dp(ctx, 48), dp(ctx, 580));
        if (window != null) {
            window.setLayout(maxDialogW, WindowManager.LayoutParams.WRAP_CONTENT);
        }

        dialog.show();
    }

    private static Button createSocialButton(Context ctx, String text, int bgColor, int strokeColor, Runnable onClick) {
        Button btn = new Button(ctx);
        btn.setText(text);
        btn.setTextSize(14.5f);
        btn.setTextColor(0xFFFFFFFF);
        btn.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        btn.setAllCaps(false);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(dp(ctx, 14));
        bg.setStroke(dp(ctx, 1.4f), strokeColor);
        btn.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 50));
        lp.setMargins(0, dp(ctx, 10), 0, 0);
        btn.setLayoutParams(lp);

        btn.setOnClickListener(v -> onClick.run());
        return btn;
    }

    private static void openUrl(Context ctx, String url, boolean isEn) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            ctx.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(ctx, isEn ? "Cannot open link: " + url : "تعذر فتح الرابط: " + url, Toast.LENGTH_LONG).show();
        }
    }

    private static int dp(Context ctx, float dpVal) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dpVal, ctx.getResources().getDisplayMetrics());
    }
}
