package sa.h9.qibla;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/**
 * Full-screen luxury settings page tailored for automotive displays and tablets.
 * Synchronized with Day and Night themes.
 * Implements dropdown selection menus (Spinners) for all preferences with instant live preview.
 */
public final class FullScreenSettingsDialog {

    public static final String TAG_SETTINGS_OVERLAY = "h9_overlay_settings";
    public static final String TAG_PICKER_OVERLAY = "h9_settings_picker_overlay";

    private FullScreenSettingsDialog() {}

    public static boolean isOpen(MainActivity activity) {
        if (activity == null) return false;
        FrameLayout rootContainer = activity.getRootContainer();
        return rootContainer != null && rootContainer.findViewWithTag(TAG_SETTINGS_OVERLAY) != null;
    }

    public static void bringToFrontIfOpen(MainActivity activity) {
        if (activity == null) return;
        FrameLayout rootContainer = activity.getRootContainer();
        if (rootContainer == null) return;
        View overlay = rootContainer.findViewWithTag(TAG_SETTINGS_OVERLAY);
        if (overlay != null) {
            overlay.bringToFront();
        }
    }

    public static boolean handleBackPressed(MainActivity activity) {
        if (activity == null) return false;
        FrameLayout rootContainer = activity.getRootContainer();
        if (rootContainer == null) return false;
        View overlay = rootContainer.findViewWithTag(TAG_SETTINGS_OVERLAY);
        if (overlay instanceof FrameLayout) {
            FrameLayout overlayFl = (FrameLayout) overlay;
            View picker = overlayFl.findViewWithTag(TAG_PICKER_OVERLAY);
            if (picker != null) {
                overlayFl.removeView(picker);
                return true;
            }
            dismiss(activity);
            return true;
        }
        return false;
    }

    public static void dismiss(MainActivity activity) {
        if (activity == null) return;
        FrameLayout rootContainer = activity.getRootContainer();
        if (rootContainer == null) return;
        View overlay = rootContainer.findViewWithTag(TAG_SETTINGS_OVERLAY);
        if (overlay != null) {
            rootContainer.removeView(overlay);
        }
        try {
            activity.applyImmersiveMode();
        } catch (Throwable ignored) { }
    }

    public static void show(MainActivity activity) {
        if (activity == null || activity.isFinishing()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed()) {
            return;
        }
        FrameLayout hostContainer = activity.getRootContainer();
        if (hostContainer == null) return;

        final Context ctx = activity;
        final SharedPreferences uiPrefs = activity.getSharedPreferences(MainActivity.UI_PREFS, Context.MODE_PRIVATE);
        final QiblaView qView = activity.getQiblaView();
        final boolean isEn = activity.isEnglishMode();
        final FullScreenWeatherDialog.ThemePalette pal = FullScreenWeatherDialog.getThemePalette(ctx);

        int screenW = activity.getVisiblePaneWidth();
        int screenH = activity.getVisiblePaneHeight();
        int displayW = ctx.getResources().getDisplayMetrics().widthPixels;
        if (screenW <= 0) screenW = displayW;

        boolean inMultiWindow = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                inMultiWindow = activity.isInMultiWindowMode();
            } catch (Throwable ignored) { }
        }

        // Physical split-screen check: use Activity container's actual visible pane width
        boolean isSplitMode = inMultiWindow || (displayW > 0 && screenW < (int) (displayW * 0.85f));
        float aspect = (float) screenW / (float) Math.max(1, screenH);
        boolean isWideScreen = !isSplitMode && screenW >= dp(ctx, 960) && aspect >= 1.60f;

        // Embedded overlay container locked strictly inside MainActivity's split/full pane
        View existing = hostContainer.findViewWithTag(TAG_SETTINGS_OVERLAY);
        if (existing != null) {
            hostContainer.removeView(existing);
        }

        final FrameLayout overlayContainer = new FrameLayout(ctx);
        overlayContainer.setTag(TAG_SETTINGS_OVERLAY);
        overlayContainer.setBackgroundColor(pal.rootBg);
        overlayContainer.setClickable(true);
        overlayContainer.setFocusable(true);
        overlayContainer.setLayoutDirection(isEn ? View.LAYOUT_DIRECTION_LTR : View.LAYOUT_DIRECTION_RTL);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(pal.rootBg);
        root.setLayoutDirection(isEn ? View.LAYOUT_DIRECTION_LTR : View.LAYOUT_DIRECTION_RTL);

        int padH = isWideScreen ? dp(ctx, 24) : dp(ctx, 12);
        int padV = isWideScreen ? dp(ctx, 16) : dp(ctx, 10);

        // Top Navigation Bar
        LinearLayout topBar = new LinearLayout(ctx);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(padH, padV, padH, padV);
        topBar.setBackgroundColor(pal.topBarBg);
        topBar.setLayoutDirection(isEn ? View.LAYOUT_DIRECTION_LTR : View.LAYOUT_DIRECTION_RTL);

        TextView title = new TextView(ctx);
        title.setText(isEn ? "⚙️ Application Settings" : "⚙️ إعدادات التطبيق");
        title.setTextSize(isWideScreen ? 20f : 16.5f);
        title.setTextColor(pal.goldHighlight);
        title.setTypeface(FontHelper.getArabicTypeface(ctx), Typeface.BOLD);
        title.setTextDirection(isEn ? View.TEXT_DIRECTION_LTR : View.TEXT_DIRECTION_RTL);
        title.setGravity(Gravity.CENTER_VERTICAL | (isEn ? Gravity.LEFT : Gravity.RIGHT));
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);

        TextView verBadge = new TextView(ctx);
        verBadge.setText(isWideScreen
                ? (isEn ? ("  v" + BuildConfig.VERSION_NAME + " • Automotive") : ("  الإصدار " + BuildConfig.VERSION_NAME + " • لشاشات السيارات"))
                : (isEn ? ("v" + BuildConfig.VERSION_NAME + " • Automotive") : ("الإصدار " + BuildConfig.VERSION_NAME + " • لشاشات السيارات")));
        verBadge.setTextSize(isWideScreen ? 13f : 11.5f);
        verBadge.setTextColor(pal.textSecondary);
        verBadge.setTypeface(FontHelper.getArabicTypeface(ctx), Typeface.NORMAL);
        verBadge.setTextDirection(isEn ? View.TEXT_DIRECTION_LTR : View.TEXT_DIRECTION_RTL);
        verBadge.setGravity(Gravity.CENTER_VERTICAL | (isEn ? Gravity.LEFT : Gravity.RIGHT));
        verBadge.setMaxLines(1);
        verBadge.setEllipsize(android.text.TextUtils.TruncateAt.END);

        LinearLayout titleBox = new LinearLayout(ctx);
        titleBox.setOrientation(isWideScreen ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        titleBox.setGravity(Gravity.CENTER_VERTICAL | (isEn ? Gravity.LEFT : Gravity.RIGHT));
        titleBox.addView(title);
        titleBox.addView(verBadge);

        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        if (isEn) titleLp.rightMargin = dp(ctx, 8);
        else titleLp.leftMargin = dp(ctx, 8);
        topBar.addView(titleBox, titleLp);

        // Save & Close Button
        Button closeBtn = new Button(ctx);
        closeBtn.setText(isEn ? "💾 Save & Close" : "💾 حفظ وإغلاق");
        closeBtn.setTextSize(isWideScreen ? 14.5f : 13.5f);
        closeBtn.setTextColor(Color.WHITE);
        closeBtn.setAllCaps(false);
        closeBtn.setTypeface(FontHelper.getArabicTypeface(ctx), Typeface.BOLD);
        closeBtn.setPadding(isWideScreen ? dp(ctx, 22) : dp(ctx, 12), dp(ctx, 6), isWideScreen ? dp(ctx, 22) : dp(ctx, 12), dp(ctx, 6));

        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setColor(pal.isLight ? 0xFF007A6C : 0xFF00695C);
        closeBg.setCornerRadius(dp(ctx, 16));
        closeBg.setStroke(dp(ctx, 1.8f), pal.goldHighlight);
        closeBtn.setBackground(closeBg);
        closeBtn.setOnClickListener(v -> {
            activity.invalidatePrayerTimes();
            if (qView != null) qView.postInvalidate();
            showCompactSuccessToast(ctx, isEn);
            dismiss(activity);
        });
        topBar.addView(closeBtn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(ctx, 42)));

        root.addView(topBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Scrollable Body Content
        ScrollView scrollView = new ScrollView(ctx);
        scrollView.setFillViewport(true);
        scrollView.setVerticalScrollBarEnabled(true);

        LinearLayout bodyLayout = new LinearLayout(ctx);
        bodyLayout.setOrientation(LinearLayout.VERTICAL);
        bodyLayout.setPadding(padH, padV, padH, dp(ctx, 20));

        // Wide display grid (Two columns for landscape car screens)
        LinearLayout contentRow = new LinearLayout(ctx);
        contentRow.setOrientation(isWideScreen ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);

        // Column 1: Appearance & Display
        LinearLayout col1 = new LinearLayout(ctx);
        col1.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams col1Lp = isWideScreen
                ? new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f)
                : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (isWideScreen) col1Lp.rightMargin = dp(ctx, 16);

        View cardAppearance = createSectionCard(ctx, isEn ? "🎨 Appearance & Screen" : "🎨 المظهر والثيمات", pal, isWideScreen, isEn);
        LinearLayout cardAppearanceContent = (LinearLayout) ((ViewGroup) cardAppearance).getChildAt(1);

        // 1. Theme Spinner
        String[] themeOptions = isEn ? new String[]{
                "🌓 Automatic (Day / Night)",
                "💎 Royal Emerald & Gold",
                "🌌 Obsidian Luxury Navy",
                "🏜️ Desert Amber & Gold",
                "⚪ Pearl Day",
                "🌙 Pure Black OLED Night (Glare-free)"
        } : new String[]{
                "🌓 تلقائي (نهاري / ليلي ذكي)",
                "💎 زمردي ملكي وذهبي",
                "🌌 كحلي فاخر (أوبسيديان)",
                "🏜️ عنبر ذهبي دافئ",
                "⚪ نهاري لؤلؤي أنيق",
                "🌙 أسود OLED نقي (للقيادة الليلية الهادئة)"
        };
        int currentTheme = uiPrefs.getInt(MainActivity.KEY_THEME_MODE, QiblaView.THEME_AUTO);
        cardAppearanceContent.addView(createDropdownRow(ctx, overlayContainer, isEn ? "Color Theme:" : "المظهر وثيم الألوان:", themeOptions, currentTheme, pal, isEn, pos -> {
            uiPrefs.edit().putInt(MainActivity.KEY_THEME_MODE, pos).apply();
            if (qView != null) {
                qView.setThemeMode(pos);
            }
            FloatingSpeedBubbleService.reloadPreferences(ctx);
            new Handler(Looper.getMainLooper()).post(() -> show(activity));
        }));

        // 2. Fullscreen Mode Spinner (استرجاع خيار تكبير وملء الشاشة)
        String[] fullscreenOptions = isEn ? new String[]{
                "📺 Fullscreen Enabled (Hide system bars)",
                "📱 Standard Windowed (Show system bars)"
        } : new String[]{
                "📺 ملء وتكبير الشاشة بالكامل (إخفاء أشرطة النظام)",
                "📱 وضع النوافذ العادي (إظهار أشرطة النظام)"
        };
        boolean fullScreenOn = uiPrefs.getBoolean(MainActivity.KEY_FULLSCREEN_ENABLED, true);
        cardAppearanceContent.addView(createDropdownRow(ctx, overlayContainer, isEn ? "Fullscreen Mode:" : "ملء وتكبير الشاشة:", fullscreenOptions, fullScreenOn ? 0 : 1, pal, isEn, pos -> {
            boolean enabled = (pos == 0);
            uiPrefs.edit().putBoolean(MainActivity.KEY_FULLSCREEN_ENABLED, enabled).apply();
            if (activity instanceof MainActivity) {
                activity.runOnUiThread(() -> {
                    try {
                        activity.applyImmersiveMode();
                    } catch (Exception ignored) {}
                });
            }
        }));

        // 3. Font Scale / Size Spinner (استرجاع تكبير حجم الخط والنصوص)
        String[] fontScaleOptions = isEn ? new String[]{
                "🔤 Normal Size (100% - Balanced)",
                "🔍 Large Font (115% - High Visibility)",
                "🔎 Extra Large (130% - Distant Viewing)",
                "📐 Compact Font (85% - Small / Split Screens)"
        } : new String[]{
                "🔤 عادي (100% - الحجم القياسي المتوازن)",
                "🔍 كبير (115% - أوضح للرؤية أثناء القيادة)",
                "🔎 كبير جداً (130% - وضوح فائق للشاشات البعيدة)",
                "📐 مدمج (85% - مناسب للتقسيم والشاشات الصغيرة)"
        };
        float curScale = uiPrefs.getFloat(MainActivity.KEY_FONT_SCALE, 0.90f);
        int scaleIdx = 0;
        if (curScale >= 1.18f) scaleIdx = 2;
        else if (curScale >= 1.00f) scaleIdx = 1;
        else if (curScale <= 0.84f) scaleIdx = 3;
        else scaleIdx = 0;

        cardAppearanceContent.addView(createDropdownRow(ctx, overlayContainer, isEn ? "Font Size Scaling:" : "تكبير حجم الخط والنصوص:", fontScaleOptions, scaleIdx, pal, isEn, pos -> {
            float targetScale = 0.90f;
            if (pos == 1) targetScale = 1.05f;
            else if (pos == 2) targetScale = 1.25f;
            else if (pos == 3) targetScale = 0.78f;
            uiPrefs.edit().putFloat(MainActivity.KEY_FONT_SCALE, targetScale).apply();
            if (qView != null) {
                qView.setFontScale(targetScale);
                qView.postInvalidate();
            }
        }));

        // 4. Arabic Font Family Spinner (اختيار نوع وشكل الخط)
        String[] fontTypeOptions = isEn ? new String[]{
                "✨ Default System Font",
                "✍️ Cairo Font (Clear & Modern)",
                "🚘 Tajawal Font (Automotive Optimized)",
                "📖 Almarai Font (Soft & Elegant)",
                "📱 Readex Pro Font (Contemporary Tech)",
                "🕌 Amiri Font (Classic Quranic Calligraphy)"
        } : new String[]{
                "✨ الخط الافتراضي المتناسق",
                "✍️ خط كايرو (Cairo - عريض وأنيق)",
                "🚘 خط تجوال (Tajawal - ممتاز لشاشات السيارات)",
                "📖 خط المراعي (Almarai - فخم ومريح للعين)",
                "📱 خط ريدكس برو (Readex Pro - رقمي وعصري)",
                "🕌 خط أميري (Amiri - خط عربي قرآني)"
        };
        int currentFont = uiPrefs.getInt(MainActivity.KEY_FONT_INDEX, QiblaView.FONT_DEFAULT);
        if (currentFont < 0 || currentFont > 5) currentFont = 0;
        cardAppearanceContent.addView(createDropdownRow(ctx, overlayContainer, isEn ? "Arabic Font Style:" : "نوع ونمط الخط العربي:", fontTypeOptions, currentFont, pal, isEn, pos -> {
            uiPrefs.edit().putInt(MainActivity.KEY_FONT_INDEX, pos).apply();
            if (qView != null) {
                qView.setFontIndex(pos);
                qView.postInvalidate();
            }
        }));

        // 5. VR Weather Engine Mode (واقع افتراضي متكامل للطقس)
        String[] weatherVrOptions = isEn ? new String[]{
                "🌐 Auto Live Weather (City Radar & Rain)",
                "⚡ VR Thunderstorm & Flashing Lightning",
                "🌧️ VR Heavy Rain & Glass Splash",
                "☁️ VR Realistic 3D Drifting Clouds",
                "☀️ VR Radiant Sunlight & Solar Flares",
                "🚫 Weather Effects Disabled"
        } : new String[]{
                "🌐 تلقائي حي (حسب طقس ورادار المدينة الحالي)",
                "⚡ محاكاة واقعية: عواصف رعدية وبرق متوهج",
                "🌧️ محاكاة واقعية: أمطار غزيرة وقطرات على الشاشة",
                "☁️ محاكاة واقعية: سحب ثلاثية الأبعاد متحركة",
                "☀️ محاكاة واقعية: أشعة شمس ساطعة وهواء نقي",
                "🚫 إيقاف تأثيرات الطقس"
        };
        int currentWMode = uiPrefs.getInt("weather_effect_mode", QiblaView.WEATHER_MODE_AUTO);
        if (currentWMode < 0 || currentWMode > 5) currentWMode = QiblaView.WEATHER_MODE_AUTO;
        cardAppearanceContent.addView(createDropdownRow(ctx, overlayContainer, isEn ? "VR Weather Engine:" : "محرك الطقس الافتراضي (VR):", weatherVrOptions, currentWMode, pal, isEn, pos -> {
            uiPrefs.edit().putInt("weather_effect_mode", pos)
                    .putBoolean("weather_effects_enabled", pos != QiblaView.WEATHER_MODE_OFF)
                    .putBoolean("weather_animation_enabled", pos != QiblaView.WEATHER_MODE_OFF).apply();
            if (qView != null) {
                qView.setWeatherEffectMode(pos);
                qView.postInvalidate();
            }
        }));

        // 3. Ambient Weather Sound Spinner
        String[] weatherSoundOptions = isEn ? new String[]{
                "🔉 Gentle Ambient Weather Sound",
                "🔇 Muted Ambient"
        } : new String[]{
                "🔉 صوت الطقس المحيطي الهادئ مفعّل",
                "🔇 كتم صوت الطقس المحيطي"
        };
        boolean weatherSoundOn = uiPrefs.getBoolean("weather_sound_enabled", false);
        cardAppearanceContent.addView(createDropdownRow(ctx, overlayContainer, isEn ? "Weather Ambient Sound:" : "صوت الطقس المحيطي:", weatherSoundOptions, weatherSoundOn ? 0 : 1, pal, isEn, pos -> {
            uiPrefs.edit().putBoolean("weather_sound_enabled", pos == 0).apply();
        }));

        // 4. Compass Rotation Smoothing Spinner
        String[] smoothOptions = isEn ? new String[]{
                "⚡ Dynamic Automotive Response (Fast)",
                "🌿 Ultra Smooth Dampened (Luxury)",
                "🎯 Direct Instant Tracking"
        } : new String[]{
                "⚡ استجابة ديناميكية سلسة للسيارة (موصى به)",
                "🌿 تهدئة فائقة النعومة وفخمة للبوصلة",
                "🎯 تتبع فوري مباشر بدون تأخير"
        };
        int smoothMode = uiPrefs.getInt("compass_smooth_mode", 0);
        cardAppearanceContent.addView(createDropdownRow(ctx, overlayContainer, isEn ? "Compass Damping:" : "نعومة دوران البوصلة:", smoothOptions, smoothMode, pal, isEn, pos -> {
            uiPrefs.edit().putInt("compass_smooth_mode", pos).apply();
            if (qView != null) {
                qView.setSmoothMode(pos);
                qView.postInvalidate();
            }
        }));

        col1.addView(cardAppearance);

        // Display & Vehicle Card
        View cardDisplay = createSectionCard(ctx, isEn ? "🧭 Vehicle Compass & Dial" : "🧭 مؤشرات السيارة وبوصلة القبلة", pal, isWideScreen, isEn);
        LinearLayout cardDisplayContent = (LinearLayout) ((ViewGroup) cardDisplay).getChildAt(1);

        // 1. Vehicle Heading Indicator Spinner
        String[] headingBadgeOptions = isEn ? new String[]{
                "⬆️ Elevated Qibla Badge (Recommended)",
                "📐 Standard Heading Mode"
        } : new String[]{
                "⬆️ شارة القبلة المرفوعة بالأعلى (المعتمدة)",
                "📐 النمط الكلاسيكي للبوصلة"
        };
        int headingMode = uiPrefs.getInt("vehicle_heading_mode", 0);
        cardDisplayContent.addView(createDropdownRow(ctx, overlayContainer, isEn ? "Qibla Alignment Badge:" : "شارة توجيه القبلة:", headingBadgeOptions, headingMode, pal, isEn, pos -> {
            uiPrefs.edit().putInt("vehicle_heading_mode", pos).apply();
            if (qView != null) {
                qView.setVehicleHeadingMode(pos);
                qView.postInvalidate();
            }
        }));

        // 2. Compass Needle Style Spinner
        String[] needleOptions = isEn ? new String[]{
                "✨ Golden Kaaba Minaret & Dome",
                "🏹 Classic Precision Arrow",
                "🧭 Marine Compass Rose"
        } : new String[]{
                "✨ منارة وقبة الكعبة الذهبية (فاخر)",
                "🏹 سهم الاتجاه الملاحي الدقيق",
                "🧭 وردة البوصلة البحرية الدائرية"
        };
        int needleStyle = uiPrefs.getInt("needle_style", 0);
        cardDisplayContent.addView(createDropdownRow(ctx, overlayContainer, isEn ? "Compass Indicator Style:" : "شكل مؤشر القبلة:", needleOptions, needleStyle, pal, isEn, pos -> {
            uiPrefs.edit().putInt("needle_style", pos).apply();
            if (qView != null) {
                qView.setNeedleStyle(pos);
                qView.postInvalidate();
            }
        }));

        // 3. Speedometer Unit Spinner
        String[] speedOptions = isEn ? new String[]{"Kilometers / Hour (km/h)", "Miles / Hour (mph)"} : new String[]{"كيلومتر في الساعة (كم/س)", "ميل في الساعة (mph)"};
        boolean isMph = uiPrefs.getBoolean("speed_mph", false);
        cardDisplayContent.addView(createDropdownRow(ctx, overlayContainer, isEn ? "Speedometer Unit:" : "وحدة قياس السرعة GPS:", speedOptions, isMph ? 1 : 0, pal, isEn, pos -> {
            uiPrefs.edit().putBoolean("speed_mph", pos == 1).apply();
            if (qView != null) qView.postInvalidate();
            FloatingSpeedBubbleService.reloadPreferences(ctx);
        }));

        // 4. Dynamic Floating Ball (HUD)
        String[] bubbleOptions = isEn ? new String[]{
                "🚫 Disabled",
                "🟢 Floating Ball & Island (Tap to expand prayers over any app)"
        } : new String[]{
                "🚫 معطلة",
                "🟢 تشغيل الكرة والجزيرة العائمة (اضغط الكرة لتتحول لجزيرة تفاعلية بالمواقيت)"
        };
        boolean bubbleOn = uiPrefs.getBoolean("floating_bubble_enabled", false);
        cardDisplayContent.addView(createDropdownRow(ctx, overlayContainer, isEn ? "Floating Ball & Island (HUD):" : "الكرة والجزيرة التفاعلية العائمة (HUD):", bubbleOptions, bubbleOn ? 1 : 0, pal, isEn, pos -> {
            boolean enabled = (pos == 1);
            if (enabled) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(ctx)) {
                    showCompactToast(ctx, isEn ? "⚠️ Please enable 'Display over other apps' permission" : "⚠️ يرجى تفعيل إذن 'الظهور فوق التطبيقات الأخرى'");
                    try {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + ctx.getPackageName()));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        ctx.startActivity(intent);
                    } catch (Throwable ignored) { }
                    return;
                }
                uiPrefs.edit().putBoolean("floating_bubble_enabled", true)
                        .putBoolean("floating_bubble_autostart_car", true).apply();
                FloatingSpeedBubbleService.start(ctx);
                showCompactToast(ctx, isEn ? "🟢 Floating Ball & Island Activated (Tap ball to expand)" : "🟢 تم تفعيل الكرة والجزيرة العائمة (المس الكرة لفتح المواقيت)");
            } else {
                uiPrefs.edit().putBoolean("floating_bubble_enabled", false).apply();
                FloatingSpeedBubbleService.stop(ctx);
                showCompactToast(ctx, isEn ? "⚪ Floating Ball Stopped" : "⚪ تم إيقاف الكرة والجزيرة العائمة");
            }
        }));

        // Option to auto-start floating ball when car turns on
        String[] carBootOptions = isEn ? new String[]{
                "🚗 Auto-start on car startup (Active)",
                "🚫 Manual start only"
        } : new String[]{
                "🚗 تشغيل تلقائي عند تشغيل السيارة (مفعل)",
                "🚫 تشغيل يدوي فقط"
        };
        boolean autoStartCar = uiPrefs.getBoolean("floating_bubble_autostart_car", true);
        cardDisplayContent.addView(createDropdownRow(ctx, overlayContainer, isEn ? "Auto-start ball when car turns on:" : "تشغيل الكرة عند تشغيل السيارة:", carBootOptions, autoStartCar ? 0 : 1, pal, isEn, pos -> {
            boolean enabled = (pos == 0);
            uiPrefs.edit().putBoolean("floating_bubble_autostart_car", enabled).apply();
            showCompactToast(ctx, enabled
                    ? (isEn ? "🚗 Auto-start on car startup enabled" : "🚗 تم تفعيل التشغيل التلقائي مع تشغيل السيارة")
                    : (isEn ? "🚫 Auto-start on car startup disabled" : "🚫 تم تعطيل التشغيل التلقائي مع تشغيل السيارة"));
        }));

        // 5. Floating Ball Size
        String[] ballSizeOptions = isEn ? new String[]{
                "🟢 Compact (72 dp)",
                "🔵 Medium Balanced (86 dp - Recommended)",
                "🟣 Large Driver View (102 dp)"
        } : new String[]{
                "🟢 مدمجة صغيرة (72 dp)",
                "🔵 متوسطة متوازنة (86 dp - موصى به)",
                "🟣 كبيرة لوضوح القيادة (102 dp)"
        };
        int curBallSize = uiPrefs.getInt("floating_ball_size", 1);
        if (curBallSize < 0 || curBallSize > 2) curBallSize = 1;
        cardDisplayContent.addView(createDropdownRow(ctx, overlayContainer, isEn ? "Floating Ball Size:" : "حجم الكرة العائمة:", ballSizeOptions, curBallSize, pal, isEn, pos -> {
            uiPrefs.edit().putInt("floating_ball_size", pos).apply();
            FloatingSpeedBubbleService.reloadPreferences(ctx);
        }));

        // 6. Floating Dock Auto-Collapse Timeout
        String[] dockTimeoutOptions = isEn ? new String[]{
                "⚡ 5 Seconds",
                "⏱️ 10 Seconds (Recommended)",
                "🕒 25 Seconds",
                "📌 Manual Only (No auto-close)"
        } : new String[]{
                "⚡ 5 ثوانٍ",
                "⏱️ 10 ثوانٍ (موصى به)",
                "🕒 25 ثانية",
                "📌 يدوي فقط (لا يُغلق تلقائياً)"
        };
        final int[] timeoutValues = new int[]{5, 10, 25, 0};
        int curTimeoutSec = uiPrefs.getInt("floating_dock_timeout_sec", 10);
        int curTimeoutIdx = 1;
        for (int i = 0; i < timeoutValues.length; i++) {
            if (timeoutValues[i] == curTimeoutSec) {
                curTimeoutIdx = i;
                break;
            }
        }
        cardDisplayContent.addView(createDropdownRow(ctx, overlayContainer, isEn ? "Floating Dock Auto-Close:" : "مدة بقاء شريط الاختصارات العائم:", dockTimeoutOptions, curTimeoutIdx, pal, isEn, pos -> {
            int chosenSec = (pos >= 0 && pos < timeoutValues.length) ? timeoutValues[pos] : 10;
            uiPrefs.edit().putInt("floating_dock_timeout_sec", chosenSec).apply();
            FloatingSpeedBubbleService.reloadPreferences(ctx);
        }));

        LinearLayout.LayoutParams cardDisplayLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardDisplayLp.topMargin = dp(ctx, 16);
        col1.addView(cardDisplay, cardDisplayLp);

        contentRow.addView(col1, col1Lp);

        // Column 2: Audio, Prayers & System
        LinearLayout col2 = new LinearLayout(ctx);
        col2.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams col2Lp = isWideScreen
                ? new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f)
                : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (!isWideScreen) {
            col2Lp.topMargin = dp(ctx, 16);
        }

        // Audio & Alerts Card
        View cardAudio = createSectionCard(ctx, isEn ? "🔊 Prayer Audio & Automotive Alerts" : "🔊 صوتيات الأذان وتنبيهات السيارة", pal, isWideScreen, isEn);
        LinearLayout cardAudioContent = (LinearLayout) ((ViewGroup) cardAudio).getChildAt(1);

        // 1. Audio Alerts Toggle Spinner
        String[] audioToggleOptions = isEn ? new String[]{
                "🔊 Audio Alerts Active",
                "🔇 Mute Audio Alerts"
        } : new String[]{
                "🔊 تنبيهات الأذان الصوتية مفعّلة",
                "🔇 كتم الصوت تماماً"
        };
        boolean audioOn = uiPrefs.getBoolean(MainActivity.KEY_ADHAN_SOUND_ENABLED, true);
        cardAudioContent.addView(createDropdownRow(ctx, overlayContainer, isEn ? "Prayer Audio Alerts:" : "التنبيهات الصوتية للصلوات:", audioToggleOptions, audioOn ? 0 : 1, pal, isEn, pos -> {
            uiPrefs.edit().putBoolean(MainActivity.KEY_ADHAN_SOUND_ENABLED, pos == 0).apply();
        }));

        // 2. Alert Tone Selection Spinner
        String[] toneOptions = isEn ? new String[]{
                "🎙️ Male Voice: 'Prayer time has begun' + Takbeerat",
                "🗣️ Male Voice Only: 'Prayer time has begun'",
                "🕋 Makkah Haram Takbeerat Only",
                "🔔 Calm Dignified Islamic Chime (Automotive)",
                "🔇 Visual Notification Only (Muted)"
        } : new String[]{
                "🎙️ صوت رجل وقور (دخل الآن وقت الصلاة) + تكبيرات",
                "🗣️ صوت رجل فقط (دخل الآن وقت صلاة...)",
                "🕋 تكبيرات الحرم المكي الشريف فقط",
                "🔔 نغمة إسلامية وقورة هادئة (للسيارة)",
                "🔇 إشعار مرئي بدون صوت"
        };
        int currentTone = uiPrefs.getInt("adhan_sound_mode", SoundManager.SOUND_MODE_COMBINED);
        cardAudioContent.addView(createDropdownRow(ctx, overlayContainer, isEn ? "Alert Sound Style:" : "نغمة وصوت دخول وقت الصلاة:", toneOptions, currentTone, pal, isEn, pos -> {
            uiPrefs.edit().putInt("adhan_sound_mode", pos).apply();
        }));

        // 3. Audio Ducking Car Radio Spinner
        String[] duckingOptions = isEn ? new String[]{
                "🚗 Auto Lower Car Radio 70% During Adhan (Recommended)",
                "📻 Keep Radio Volume Unchanged"
        } : new String[]{
                "🚗 خفض صوت راديو وميديا السيارة تلقائياً 70% أثناء الأذان",
                "📻 عدم خفض صوت ميديا السيارة"
        };
        boolean duckingOn = uiPrefs.getBoolean("audio_ducking_enabled", true);
        cardAudioContent.addView(createDropdownRow(ctx, overlayContainer, isEn ? "Radio Ducking:" : "تخفيف صوت ميديا السيارة أثناء الأذان:", duckingOptions, duckingOn ? 0 : 1, pal, isEn, pos -> {
            uiPrefs.edit().putBoolean("audio_ducking_enabled", pos == 0).apply();
        }));

        // 4. Background Alerts Spinner
        String[] bgAlertOptions = isEn ? new String[]{
                "🔔 Always Active in Background (AlarmClock Priority)",
                "🔕 Active in Foreground Only"
        } : new String[]{
                "🔔 تعمل دائماً بالخلفية (حتى لو التطبيق مغلق)",
                "🔕 التنبيه عند فتح التطبيق فقط"
        };
        boolean bgActive = uiPrefs.getBoolean("background_alerts_enabled", true);
        cardAudioContent.addView(createDropdownRow(ctx, overlayContainer, isEn ? "Background Alerts:" : "تنبيهات الصلاة في الخلفية:", bgAlertOptions, bgActive ? 0 : 1, pal, isEn, pos -> {
            boolean active = (pos == 0);
            uiPrefs.edit().putBoolean("background_alerts_enabled", active).apply();
            if (active) {
                AdhanScheduler.scheduleNextPrayer(ctx);
            }
        }));

        // 5. Test Audio & Notification Button
        final Button testAudioBtn = new Button(ctx);
        testAudioBtn.setText(SoundManager.isPlaying() ? (isEn ? "⏹ Stop Alert Audio" : "⏹ إيقاف الصوت") : (isEn ? "🔔 Test Sound & Notification Now" : "🔔 تجربة صوت وتنبيه الأذان الآن (إشعار وصوت)"));
        testAudioBtn.setTextSize(14f);
        testAudioBtn.setTextColor(Color.WHITE);
        testAudioBtn.setAllCaps(false);
        testAudioBtn.setTypeface(FontHelper.getArabicTypeface(ctx), Typeface.BOLD);
        GradientDrawable testBg = new GradientDrawable();
        testBg.setColor(pal.isLight ? 0xFF00758A : 0xFF165B63);
        testBg.setCornerRadius(dp(ctx, 16));
        testBg.setStroke(dp(ctx, 1), pal.accentColor);
        testAudioBtn.setBackground(testBg);
        testAudioBtn.setOnClickListener(v -> {
            if (SoundManager.isPlaying()) {
                SoundManager.stop();
                testAudioBtn.setText(isEn ? "🔔 Test Sound & Notification Now" : "🔔 تجربة صوت وتنبيه الأذان الآن (إشعار وصوت)");
            } else {
                testAudioBtn.setText(isEn ? "⏹ Stop Alert Audio" : "⏹ إيقاف الصوت");
                int mode = uiPrefs.getInt("adhan_sound_mode", SoundManager.SOUND_MODE_COMBINED);
                try {
                    AdhanAlarmReceiver.showNotification(ctx, isEn ? "Dhuhr (Test)" : "الظهر (اختبار التنبيه)", "12:00", isEn ? "Riyadh" : "الرياض");
                } catch (Throwable ignored) { }
                SoundManager.playPrayerAlert(ctx, mode, true, () -> {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        testAudioBtn.setText(isEn ? "🔔 Test Sound & Notification Now" : "🔔 تجربة صوت وتنبيه الأذان الآن (إشعار وصوت)");
                    });
                });
            }
        });
        testAudioBtn.setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 10));
        testAudioBtn.setMinHeight(dp(ctx, 44));
        LinearLayout.LayoutParams testBtnLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        testBtnLp.topMargin = dp(ctx, 6);
        testBtnLp.bottomMargin = dp(ctx, 8);
        cardAudioContent.addView(testAudioBtn, testBtnLp);

        col2.addView(cardAudio);

        // Prayer Calibration & Language Card
        View cardPrayer = createSectionCard(ctx, isEn ? "⚖️ Prayer Times & Language" : "⚖️ مواقيت الصلاة واللغة", pal, isWideScreen, isEn);
        LinearLayout cardPrayerContent = (LinearLayout) ((ViewGroup) cardPrayer).getChildAt(1);

        // 0. City Location & Coordinates (Auto Live GPS or Manual Saudi City)
        String[] cityOptions = CityManager.getCityOptions(isEn);
        int currentCityIdx = CityManager.getSelectedCityIndex(ctx);
        cardPrayerContent.addView(createDropdownRow(ctx, overlayContainer, isEn ? "📍 City Location & Coordinates:" : "📍 ربط المواقيت بإحداثيات المدينة:", cityOptions, currentCityIdx, pal, isEn, pos -> {
            CityManager.setSelectedCityIndex(ctx, pos);
            activity.onCitySelectionChanged();
            String chosen = cityOptions[pos];
            Toast.makeText(ctx, isEn ? ("Location linked: " + chosen) : ("تم ربط المواقيت: " + chosen), Toast.LENGTH_SHORT).show();
        }));

        // 1. Prayer Calculation Source Spinner
        String[] sourceOptions = isEn ? new String[]{
                "🇸🇦 Umm al-Qura Official (Saudi Mosque Standard)",
                "🏛️ Geometrical Survey & KACST Authority",
                "🌍 Muslim World League (MWL)",
                "📐 Egyptian Survey Authority"
        } : new String[]{
                "🇸🇦 تقويم أم القرى الرسمي (معتمد مساجد المملكة)",
                "🏛️ الهيئة العامة للمساحة والمعلومات الجيومكانية (KACST)",
                "🌍 رابطة العالم الإسلامي (Muslim World League)",
                "📐 الهيئة المصرية العامة للمساحة"
        };
        int currentMethod = uiPrefs.getInt("prayer_calculation_method", 4);
        int currentMethodIdx = (currentMethod == 11) ? 1 : ((currentMethod == 3) ? 2 : ((currentMethod == 5) ? 3 : 0));
        cardPrayerContent.addView(createDropdownRow(ctx, overlayContainer, isEn ? "Prayer Times Source:" : "مصدر حساب المواقيت:", sourceOptions, currentMethodIdx, pal, isEn, pos -> {
            int newMethod = (pos == 1) ? 11 : ((pos == 2) ? 3 : ((pos == 3) ? 5 : 4));
            uiPrefs.edit().putInt("prayer_calculation_method", newMethod).apply();
            activity.invalidatePrayerTimes();
        }));

        // 2. Individual Prayer Times Calibration (+ or - per prayer, ALL DEFAULT TO 0)
        TextView calibHeader = new TextView(ctx);
        calibHeader.setText(isEn ? "⚖️ Fine-Tune Each Prayer Minutes (Default: 0 min):" : "⚖️ تعديل وتدقيق كل صلاة يدوياً بالدقائق (تبدأ من 0 دقيقة):");
        calibHeader.setTextSize(13.5f);
        calibHeader.setTextColor(pal.goldHighlight);
        calibHeader.setTypeface(FontHelper.getArabicTypeface(ctx), Typeface.BOLD);
        calibHeader.setTextDirection(isEn ? View.TEXT_DIRECTION_LTR : View.TEXT_DIRECTION_RTL);
        calibHeader.setGravity(Gravity.CENTER_VERTICAL | (isEn ? Gravity.LEFT : Gravity.RIGHT));
        calibHeader.setPadding(dp(ctx, 2), dp(ctx, 8), dp(ctx, 2), dp(ctx, 6));
        cardPrayerContent.addView(calibHeader);

        Runnable onPrayerAdjusted = () -> activity.invalidatePrayerTimes();

        cardPrayerContent.addView(createPrayerFineTuneRow(ctx, uiPrefs, isEn ? "🕌 Fajr (Dawn):" : "🕌 صلاة الفجر:", "prayer_offset_fajr", 0, isEn, pal, onPrayerAdjusted));
        cardPrayerContent.addView(createPrayerFineTuneRow(ctx, uiPrefs, isEn ? "🌅 Sunrise:" : "🌅 الشروق:", "prayer_offset_sunrise", 0, isEn, pal, onPrayerAdjusted));
        cardPrayerContent.addView(createPrayerFineTuneRow(ctx, uiPrefs, isEn ? "☀️ Dhuhr (Noon):" : "☀️ صلاة الظهر:", "prayer_offset_dhuhr", 0, isEn, pal, onPrayerAdjusted));
        cardPrayerContent.addView(createPrayerFineTuneRow(ctx, uiPrefs, isEn ? "🌤️ Asr (Afternoon):" : "🌤️ صلاة العصر:", "prayer_offset_asr", 0, isEn, pal, onPrayerAdjusted));
        cardPrayerContent.addView(createPrayerFineTuneRow(ctx, uiPrefs, isEn ? "🌇 Maghrib (Sunset):" : "🌇 صلاة المغرب:", "prayer_offset_maghrib", 0, isEn, pal, onPrayerAdjusted));
        cardPrayerContent.addView(createPrayerFineTuneRow(ctx, uiPrefs, isEn ? "🌙 Isha (Night):" : "🌙 صلاة العشاء:", "prayer_offset_isha", 0, isEn, pal, onPrayerAdjusted));


        // 4. Language Spinner
        String[] langOptions = new String[]{"🇸🇦 العربية (Arabic)", "🇬🇧 English"};
        cardPrayerContent.addView(createDropdownRow(ctx, overlayContainer, isEn ? "Language:" : "لغة التطبيق:", langOptions, isEn ? 1 : 0, pal, isEn, pos -> {
            boolean englishSelected = (pos == 1);
            if (englishSelected != isEn) {
                activity.applyLanguageMode(englishSelected);
                FloatingSpeedBubbleService.reloadPreferences(ctx);
                new Handler(Looper.getMainLooper()).post(() -> show(activity));
            }
        }));

        LinearLayout.LayoutParams cardPrayerLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardPrayerLp.topMargin = dp(ctx, 16);
        col2.addView(cardPrayer, cardPrayerLp);

        // System & Info Card
        View cardSystem = createSectionCard(ctx, isEn ? "🔄 System & Updates" : "🔄 النظام والتحديثات", pal, isWideScreen, isEn);
        LinearLayout cardSystemContent = (LinearLayout) ((ViewGroup) cardSystem).getChildAt(1);

        // Vehicle & Display System Info Badge
        TextView tvSysInfo = new TextView(ctx);
        String carModel = "🚗 سيارة ليوبارد 5 • BYD DiLink 5.1 (أندرويد 13 • 320 DPI)";
        try {
            String m = (android.os.Build.BRAND + " " + android.os.Build.MODEL).trim();
            if (m.toLowerCase().contains("byd") || m.toLowerCase().contains("leopard") || m.toLowerCase().contains("dilink")) {
                carModel = "🚗 سيارة ليوبارد (BYD DiLink 5.1 • أندرويد 13 • 3 شاشات)";
            } else if (!m.isEmpty() && !m.equalsIgnoreCase("unknown")) {
                carModel = "🚗 نظام المركبة: " + m + " (Android " + android.os.Build.VERSION.RELEASE + ")";
            }
        } catch (Exception ignored) { }
        tvSysInfo.setText(isEn ? "🚗 Vehicle: Leopard 5 • BYD DiLink 5.1 (Universal Android 7.0 - 15)" : carModel);
        tvSysInfo.setTextSize(12.5f);
        tvSysInfo.setTextColor(pal.accentColor);
        tvSysInfo.setTypeface(FontHelper.getArabicTypeface(ctx), Typeface.BOLD);
        tvSysInfo.setGravity(Gravity.CENTER);
        tvSysInfo.setPadding(dp(ctx, 10), dp(ctx, 6), dp(ctx, 10), dp(ctx, 8));
        cardSystemContent.addView(tvSysInfo);

        // Check for Updates Button
        Button updateBtn = createActionBtn(ctx, isEn ? "🔄 Check for OTA Updates" : "🔄 فحص التحديثات عن بعد (OTA)",
                pal.isLight ? 0xFF00758A : 0xFF165B63, pal.accentColor);
        updateBtn.setOnClickListener(v -> UpdateManager.checkForUpdates(activity, true));
        cardSystemContent.addView(updateBtn);

        // Guide Button
        Button guideBtn = createActionBtn(ctx, isEn ? "📖 Compass & Car Screen Guide" : "📖 طريقة الاستخدام ودليل البوصلة",
                pal.isLight ? 0xFFD8E5EC : 0xFF22323A, pal.cardStroke);
        guideBtn.setTextColor(pal.isLight ? pal.textPrimary : Color.WHITE);
        guideBtn.setOnClickListener(v -> {
            dismiss(activity);
            activity.showUserGuideDialog();
        });
        cardSystemContent.addView(guideBtn);

        // Developer Profile Button
        Button devBtn = createActionBtn(ctx, isEn ? "👑 About Developer (Bandar Bin Sulaiman)" : "👑 حول المطور (بندر بن سليمان)",
                pal.isLight ? 0xFFD8E5EC : 0xFF1B3B48, pal.goldHighlight);
        devBtn.setTextColor(pal.isLight ? pal.textPrimary : Color.WHITE);
        devBtn.setOnClickListener(v -> DeveloperDialog.show(activity, isEn));
        cardSystemContent.addView(devBtn);

        // Instant Prayer Times Sync Button (Stage 1 GPS + Stage 2 Online)
        Button syncPrayerBtn = createActionBtn(ctx, isEn ? "🔄 Force Sync Prayer Times Now" : "🔄 تحديث فوري لمواقيت الصلاة (GPS + إنترنت)",
                pal.isLight ? 0xFF00758A : 0xFF144D55, pal.accentColor);
        syncPrayerBtn.setOnClickListener(v -> {
            activity.forceSyncOnlinePrayerTimes(true);
        });
        cardSystemContent.addView(syncPrayerBtn);

        // Changelog Button
        Button changelogBtn = createActionBtn(ctx, isEn ? "📋 Version Changelog" : "📋 سجل التحديثات والمميزات الجديدة",
                pal.isLight ? 0xFFD8E5EC : 0xFF22323A, pal.cardStroke);
        changelogBtn.setTextColor(pal.isLight ? pal.textPrimary : Color.WHITE);
        changelogBtn.setOnClickListener(v -> {
            dismiss(activity);
            activity.showChangelogDialog();
        });
        cardSystemContent.addView(changelogBtn);

        // Haval H9 App Store QR Barcode Button
        Button qrBtn = createActionBtn(ctx, isEn ? "📱 Haval H9 Store QR Code (Scan with Phone)" : "📱 باركود متجر هافال H9 للسيارات (امسح بكاميرا الجوال)",
                0xFF0D47A1, 0xFF42A5F5);
        qrBtn.setTextColor(Color.WHITE);
        qrBtn.setOnClickListener(v -> HavalStoreQrDialog.show(ctx));
        cardSystemContent.addView(qrBtn);

        LinearLayout.LayoutParams cardSystemLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardSystemLp.topMargin = dp(ctx, 16);
        col2.addView(cardSystem, cardSystemLp);

        contentRow.addView(col2, col2Lp);
        bodyLayout.addView(contentRow);

        // Prominent Full-Width Bottom Save & Close Action Button
        Button bottomSaveBtn = new Button(ctx);
        bottomSaveBtn.setText(isEn ? "💾 Save & Apply All Settings" : "💾 حفظ وتطبيق جميع الإعدادات وإغلاق");
        bottomSaveBtn.setTextSize(16.5f);
        bottomSaveBtn.setTextColor(Color.WHITE);
        bottomSaveBtn.setTypeface(FontHelper.getArabicTypeface(ctx), Typeface.BOLD);
        bottomSaveBtn.setAllCaps(false);
        bottomSaveBtn.setPadding(dp(ctx, 24), dp(ctx, 14), dp(ctx, 24), dp(ctx, 14));
        GradientDrawable bottomSaveBg = new GradientDrawable();
        bottomSaveBg.setColor(pal.isLight ? 0xFF007A6C : 0xFF00695C);
        bottomSaveBg.setCornerRadius(dp(ctx, 20));
        bottomSaveBg.setStroke(dp(ctx, 2), pal.goldHighlight);
        bottomSaveBtn.setBackground(bottomSaveBg);
        bottomSaveBtn.setOnClickListener(v -> {
            activity.invalidatePrayerTimes();
            if (qView != null) qView.postInvalidate();
            showCompactSuccessToast(ctx, isEn);
            dismiss(activity);
        });
        bottomSaveBtn.setMinHeight(dp(ctx, 52));
        LinearLayout.LayoutParams bSaveLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bSaveLp.topMargin = dp(ctx, 20);
        bSaveLp.bottomMargin = dp(ctx, 10);
        bodyLayout.addView(bottomSaveBtn, bSaveLp);

        scrollView.addView(bodyLayout);

        root.addView(scrollView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
        overlayContainer.addView(root, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        hostContainer.addView(overlayContainer, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        overlayContainer.bringToFront();
    }

    private static View createSectionCard(Context ctx, String titleText, FullScreenWeatherDialog.ThemePalette pal,
                                        boolean isWide, boolean isEn) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setLayoutDirection(isEn ? View.LAYOUT_DIRECTION_LTR : View.LAYOUT_DIRECTION_RTL);
        int cardPadH = isWide ? dp(ctx, 20) : dp(ctx, 12);
        int cardPadV = isWide ? dp(ctx, 16) : dp(ctx, 12);
        card.setPadding(cardPadH, cardPadV, cardPadH, cardPadV);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(pal.cardBg);
        cardBg.setCornerRadius(dp(ctx, 18));
        cardBg.setStroke(dp(ctx, 1.2f), pal.cardStroke);
        card.setBackground(cardBg);

        TextView header = new TextView(ctx);
        header.setText(titleText);
        header.setTextSize(17f);
        header.setTextColor(pal.goldHighlight != 0 ? pal.goldHighlight : 0xFFFFD768);
        header.setTypeface(FontHelper.getArabicTypeface(ctx), Typeface.BOLD);
        header.setTextDirection(isEn ? View.TEXT_DIRECTION_LTR : View.TEXT_DIRECTION_RTL);
        header.setGravity(Gravity.CENTER_VERTICAL | (isEn ? Gravity.LEFT : Gravity.RIGHT));
        header.setPadding(0, 0, 0, dp(ctx, 12));
        card.addView(header);

        LinearLayout content = new LinearLayout(ctx);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setLayoutDirection(isEn ? View.LAYOUT_DIRECTION_LTR : View.LAYOUT_DIRECTION_RTL);
        card.addView(content);

        return card;
    }

    private interface OnSpinnerIndexListener {
        void onSelected(int index);
    }

    private static View createDropdownRow(final Context ctx, final FrameLayout overlayContainer, String label,
                                          final String[] items, int selectedIdx,
                                          final FullScreenWeatherDialog.ThemePalette pal, final boolean isEn,
                                          final OnSpinnerIndexListener listener) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setLayoutDirection(isEn ? View.LAYOUT_DIRECTION_LTR : View.LAYOUT_DIRECTION_RTL);
        row.setPadding(0, dp(ctx, 6), 0, dp(ctx, 10));

        final Typeface arabicTf = FontHelper.getArabicTypeface(ctx);

        TextView labelTv = new TextView(ctx);
        labelTv.setText(label);
        labelTv.setTextSize(14.5f);
        labelTv.setTextColor(pal.goldHighlight != 0 ? pal.goldHighlight : 0xFFFFD768);
        labelTv.setTypeface(arabicTf, Typeface.BOLD);
        labelTv.setTextDirection(isEn ? View.TEXT_DIRECTION_LTR : View.TEXT_DIRECTION_RTL);
        labelTv.setGravity(Gravity.CENTER_VERTICAL | (isEn ? Gravity.LEFT : Gravity.RIGHT));
        labelTv.setPadding(dp(ctx, 4), 0, dp(ctx, 4), dp(ctx, 6));
        row.addView(labelTv);

        final int[] currentPos = new int[]{ (selectedIdx >= 0 && selectedIdx < items.length) ? selectedIdx : 0 };

        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setLayoutDirection(isEn ? View.LAYOUT_DIRECTION_LTR : View.LAYOUT_DIRECTION_RTL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(ctx, 14), dp(ctx, 10), dp(ctx, 14), dp(ctx, 10));
        box.setMinimumHeight(dp(ctx, 50));

        GradientDrawable spBg = new GradientDrawable();
        spBg.setColor(pal.isLight ? 0xFFFFFFFF : 0xEE14222D);
        spBg.setCornerRadius(dp(ctx, 12));
        int strokeColor = pal.isLight ? 0xFF90A4AE : (pal.goldHighlight != 0 ? pal.goldHighlight : 0xFFE5B64A);
        spBg.setStroke(dp(ctx, 1.4f), strokeColor);
        box.setBackground(spBg);

        final TextView valTv = new TextView(ctx);
        valTv.setText(items[currentPos[0]]);
        valTv.setTextColor(pal.isLight ? 0xFF0C242C : 0xFFFFFFFF);
        valTv.setTextSize(15f);
        valTv.setTypeface(arabicTf, Typeface.BOLD);
        valTv.setMaxLines(2);
        valTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        valTv.setTextDirection(isEn ? View.TEXT_DIRECTION_LTR : View.TEXT_DIRECTION_RTL);
        valTv.setGravity(Gravity.CENTER_VERTICAL | (isEn ? Gravity.LEFT : Gravity.RIGHT));

        LinearLayout.LayoutParams valLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        valLp.gravity = Gravity.CENTER_VERTICAL;
        box.addView(valTv, valLp);

        TextView arrow = new TextView(ctx);
        arrow.setText(" ▼ ");
        arrow.setTextColor(pal.goldHighlight != 0 ? pal.goldHighlight : 0xFFFFD768);
        arrow.setTextSize(13f);
        arrow.setTypeface(arabicTf, Typeface.BOLD);
        arrow.setPadding(dp(ctx, 4), 0, dp(ctx, 4), 0);
        box.addView(arrow);

        box.setOnClickListener(v -> {
            showThemedOptionPicker(ctx, overlayContainer, label, items, currentPos[0], pal, isEn, newPos -> {
                currentPos[0] = newPos;
                valTv.setText(items[newPos]);
                if (listener != null) listener.onSelected(newPos);
            });
        });

        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        row.addView(box, bLp);

        return row;
    }

    private static void showThemedOptionPicker(Context ctx, final FrameLayout overlayContainer, String titleText,
                                               final String[] items, int selectedPos,
                                               FullScreenWeatherDialog.ThemePalette pal, final boolean isEn,
                                               final OnSpinnerIndexListener listener) {
        if (ctx == null || overlayContainer == null || items == null) return;

        View oldPicker = overlayContainer.findViewWithTag(TAG_PICKER_OVERLAY);
        if (oldPicker != null) {
            overlayContainer.removeView(oldPicker);
        }

        final FrameLayout pickerScrim = new FrameLayout(ctx);
        pickerScrim.setTag(TAG_PICKER_OVERLAY);
        pickerScrim.setBackgroundColor(0xB8000000);
        pickerScrim.setClickable(true);
        pickerScrim.setFocusable(true);
        pickerScrim.setLayoutDirection(isEn ? View.LAYOUT_DIRECTION_LTR : View.LAYOUT_DIRECTION_RTL);
        pickerScrim.setOnClickListener(v -> overlayContainer.removeView(pickerScrim));

        final Typeface arabicTf = FontHelper.getArabicTypeface(ctx);
        int dpVal = (int) Math.max(1f, ctx.getResources().getDisplayMetrics().density);
        LinearLayout pRoot = new LinearLayout(ctx);
        pRoot.setOrientation(LinearLayout.VERTICAL);
        pRoot.setLayoutDirection(isEn ? View.LAYOUT_DIRECTION_LTR : View.LAYOUT_DIRECTION_RTL);
        pRoot.setClickable(true);
        pRoot.setPadding(16 * dpVal, 16 * dpVal, 16 * dpVal, 16 * dpVal);

        GradientDrawable pBg = new GradientDrawable();
        pBg.setColor(pal.isLight ? 0xFFFFFFFF : 0xF80B1C26);
        pBg.setCornerRadius(18 * dpVal);
        pBg.setStroke(2 * dpVal, pal.cardStroke);
        pRoot.setBackground(pBg);

        TextView pTitle = new TextView(ctx);
        pTitle.setText(titleText);
        pTitle.setTextSize(17f);
        pTitle.setTextColor(pal.goldHighlight != 0 ? pal.goldHighlight : 0xFFFFD768);
        pTitle.setTypeface(arabicTf, Typeface.BOLD);
        pTitle.setTextDirection(isEn ? View.TEXT_DIRECTION_LTR : View.TEXT_DIRECTION_RTL);
        pTitle.setGravity(Gravity.CENTER_VERTICAL | (isEn ? Gravity.LEFT : Gravity.RIGHT));
        pTitle.setPadding(4 * dpVal, 0, 4 * dpVal, 12 * dpVal);
        pRoot.addView(pTitle);

        ScrollView sv = new ScrollView(ctx);
        sv.setVerticalScrollBarEnabled(true);
        LinearLayout itemsList = new LinearLayout(ctx);
        itemsList.setOrientation(LinearLayout.VERTICAL);
        itemsList.setLayoutDirection(isEn ? View.LAYOUT_DIRECTION_LTR : View.LAYOUT_DIRECTION_RTL);

        for (int i = 0; i < items.length; i++) {
            final int pos = i;
            boolean isSel = (i == selectedPos);

            LinearLayout itemRow = new LinearLayout(ctx);
            itemRow.setOrientation(LinearLayout.HORIZONTAL);
            itemRow.setLayoutDirection(isEn ? View.LAYOUT_DIRECTION_LTR : View.LAYOUT_DIRECTION_RTL);
            itemRow.setGravity(Gravity.CENTER_VERTICAL);
            itemRow.setPadding(14 * dpVal, 13 * dpVal, 14 * dpVal, 13 * dpVal);

            GradientDrawable itemBg = new GradientDrawable();
            itemBg.setColor(isSel ? (pal.isLight ? 0xFFE0F2F1 : 0xFF144036) : (pal.isLight ? 0xFFF1F5F9 : 0x44081620));
            itemBg.setCornerRadius(12 * dpVal);
            if (isSel) {
                itemBg.setStroke(2 * dpVal, pal.accentColor);
            }
            itemRow.setBackground(itemBg);

            TextView itemText = new TextView(ctx);
            itemText.setText(items[i]);
            itemText.setTextColor(isSel ? pal.accentColor : (pal.isLight ? 0xFF0C242C : 0xFFFFFFFF));
            itemText.setTextSize(15f);
            itemText.setTypeface(arabicTf, isSel ? Typeface.BOLD : Typeface.NORMAL);
            itemText.setTextDirection(isEn ? View.TEXT_DIRECTION_LTR : View.TEXT_DIRECTION_RTL);
            itemText.setGravity(Gravity.CENTER_VERTICAL | (isEn ? Gravity.LEFT : Gravity.RIGHT));
            LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            itemRow.addView(itemText, tLp);

            if (isSel) {
                TextView check = new TextView(ctx);
                check.setText(" ✓ ");
                check.setTextColor(pal.accentColor);
                check.setTextSize(16f);
                check.setTypeface(arabicTf, Typeface.BOLD);
                itemRow.addView(check);
            }

            itemRow.setOnClickListener(v -> {
                overlayContainer.removeView(pickerScrim);
                if (listener != null) listener.onSelected(pos);
            });

            LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rLp.bottomMargin = 8 * dpVal;
            itemsList.addView(itemRow, rLp);
        }

        sv.addView(itemsList);

        int availW = overlayContainer.getWidth();
        int availH = overlayContainer.getHeight();
        if (availW <= 0 && ctx instanceof MainActivity) availW = ((MainActivity) ctx).getVisiblePaneWidth();
        if (availH <= 0 && ctx instanceof MainActivity) availH = ((MainActivity) ctx).getVisiblePaneHeight();
        if (availW <= 0) availW = ctx.getResources().getDisplayMetrics().widthPixels;
        if (availH <= 0) availH = ctx.getResources().getDisplayMetrics().heightPixels;

        int maxW = Math.min(Math.max(240 * dpVal, availW - 24 * dpVal), 480 * dpVal);
        int maxListH = Math.max(160 * dpVal, availH - 180 * dpVal);
        int svH = (items.length > 6) ? maxListH : ViewGroup.LayoutParams.WRAP_CONTENT;
        pRoot.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, svH));

        Button cancelBtn = new Button(ctx);
        cancelBtn.setText(isEn ? "✕ Close" : "✕ إغلاق");
        cancelBtn.setTextSize(13.5f);
        cancelBtn.setTextColor(pal.textSecondary);
        cancelBtn.setTypeface(arabicTf, Typeface.BOLD);
        cancelBtn.setAllCaps(false);
        GradientDrawable cBg = new GradientDrawable();
        cBg.setColor(0x33000000);
        cBg.setCornerRadius(10 * dpVal);
        cancelBtn.setBackground(cBg);
        cancelBtn.setOnClickListener(v -> overlayContainer.removeView(pickerScrim));
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 40 * dpVal);
        cLp.topMargin = 10 * dpVal;
        pRoot.addView(cancelBtn, cLp);

        FrameLayout.LayoutParams pLp = new FrameLayout.LayoutParams(maxW, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        pLp.setMargins(12 * dpVal, 12 * dpVal, 12 * dpVal, 12 * dpVal);
        pickerScrim.addView(pRoot, pLp);

        overlayContainer.addView(pickerScrim, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        pickerScrim.bringToFront();
    }

    private static Button createActionBtn(Context ctx, String text, int bgColor, int strokeColor) {
        Button btn = new Button(ctx);
        btn.setText(text);
        btn.setTextSize(14.5f);
        btn.setTextColor(Color.WHITE);
        btn.setAllCaps(false);
        btn.setTypeface(FontHelper.getArabicTypeface(ctx), Typeface.BOLD);
        btn.setPadding(dp(ctx, 12), dp(ctx, 9), dp(ctx, 12), dp(ctx, 9));
        btn.setMinHeight(dp(ctx, 44));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(dp(ctx, 14));
        bg.setStroke(dp(ctx, 1), strokeColor);
        btn.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(ctx, 4);
        lp.bottomMargin = dp(ctx, 6);
        btn.setLayoutParams(lp);

        return btn;
    }

    private static View createPrayerFineTuneRow(final Context ctx, final SharedPreferences uiPrefs,
                                                final String prayerName, final String prefKey,
                                                final int defaultOffset, final boolean isEn,
                                                final FullScreenWeatherDialog.ThemePalette pal,
                                                final Runnable onUpdated) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutDirection(isEn ? View.LAYOUT_DIRECTION_LTR : View.LAYOUT_DIRECTION_RTL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(ctx, 4), 0, dp(ctx, 4));

        final Typeface arabicTf = FontHelper.getArabicTypeface(ctx);

        TextView nameTv = new TextView(ctx);
        nameTv.setText(prayerName);
        nameTv.setTextSize(14f);
        nameTv.setTextColor(pal.textPrimary);
        nameTv.setTypeface(arabicTf, Typeface.BOLD);
        nameTv.setTextDirection(isEn ? View.TEXT_DIRECTION_LTR : View.TEXT_DIRECTION_RTL);
        nameTv.setGravity(Gravity.CENTER_VERTICAL | (isEn ? Gravity.LEFT : Gravity.RIGHT));
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        row.addView(nameTv, nameLp);

        final int[] curOff = {uiPrefs.getInt(prefKey, defaultOffset)};

        final TextView badge = new TextView(ctx);
        badge.setText(formatOffsetBadge(curOff[0], isEn));
        badge.setTextSize(13f);
        badge.setTextColor(pal.goldHighlight != 0 ? pal.goldHighlight : 0xFFFFD768);
        badge.setTypeface(arabicTf, Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(ctx, 6), dp(ctx, 4), dp(ctx, 6), dp(ctx, 4));
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(pal.isLight ? 0xFFE0ECF2 : 0xFF1B2A33);
        badgeBg.setCornerRadius(dp(ctx, 10));
        badgeBg.setStroke(dp(ctx, 1), pal.cardStroke);
        badge.setBackground(badgeBg);

        Button minusBtn = new Button(ctx);
        minusBtn.setText("➖");
        minusBtn.setTextSize(12f);
        minusBtn.setTextColor(pal.isLight ? pal.textPrimary : Color.WHITE);
        minusBtn.setPadding(0, 0, 0, 0);
        GradientDrawable minusBg = new GradientDrawable();
        minusBg.setColor(pal.isLight ? 0xFFD8E5EC : 0xFF223742);
        minusBg.setCornerRadius(dp(ctx, 10));
        minusBg.setStroke(dp(ctx, 1), pal.cardStroke);
        minusBtn.setBackground(minusBg);

        Button plusBtn = new Button(ctx);
        plusBtn.setText("➕");
        plusBtn.setTextSize(12f);
        plusBtn.setTextColor(pal.isLight ? pal.textPrimary : Color.WHITE);
        plusBtn.setPadding(0, 0, 0, 0);
        GradientDrawable plusBg = new GradientDrawable();
        plusBg.setColor(pal.isLight ? 0xFFD8E5EC : 0xFF223742);
        plusBg.setCornerRadius(dp(ctx, 10));
        plusBg.setStroke(dp(ctx, 1), pal.cardStroke);
        plusBtn.setBackground(plusBg);

        minusBtn.setOnClickListener(v -> {
            if (curOff[0] > -30) {
                curOff[0]--;
                uiPrefs.edit().putInt(prefKey, curOff[0]).apply();
                badge.setText(formatOffsetBadge(curOff[0], isEn));
                if (onUpdated != null) onUpdated.run();
            }
        });

        plusBtn.setOnClickListener(v -> {
            if (curOff[0] < 30) {
                curOff[0]++;
                uiPrefs.edit().putInt(prefKey, curOff[0]).apply();
                badge.setText(formatOffsetBadge(curOff[0], isEn));
                if (onUpdated != null) onUpdated.run();
            }
        });

        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(dp(ctx, 36), dp(ctx, 36));
        row.addView(minusBtn, btnLp);

        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(dp(ctx, 64), dp(ctx, 36));
        badgeLp.leftMargin = dp(ctx, 6);
        badgeLp.rightMargin = dp(ctx, 6);
        row.addView(badge, badgeLp);

        row.addView(plusBtn, btnLp);

        return row;
    }

    public static void showCompactSuccessToast(Context ctx, boolean isEn) {
        showCompactToast(ctx, isEn ? "✅ Settings Saved" : "✅ تم حفظ الإعدادات");
    }

    public static void showCompactToast(Context ctx, String message) {
        if (ctx == null || message == null || message.isEmpty()) return;
        try {
            Toast toast = new Toast(ctx.getApplicationContext());
            toast.setDuration(Toast.LENGTH_SHORT);
            toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, dp(ctx, 52));

            LinearLayout box = new LinearLayout(ctx);
            box.setOrientation(LinearLayout.HORIZONTAL);
            box.setGravity(Gravity.CENTER_VERTICAL);
            box.setPadding(dp(ctx, 16), dp(ctx, 8), dp(ctx, 16), dp(ctx, 8));

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xF008241D); // Rich dark emerald
            bg.setCornerRadius(dp(ctx, 22));
            bg.setStroke(dp(ctx, 1.2f), 0xFF00F5C8); // Subtle glowing accent border
            box.setBackground(bg);

            TextView tv = new TextView(ctx);
            tv.setText(message);
            tv.setTextColor(0xFFFFFFFF);
            tv.setTextSize(13.5f);
            tv.setTypeface(FontHelper.getArabicTypeface(ctx), Typeface.BOLD);
            tv.setGravity(Gravity.CENTER);
            box.addView(tv);

            toast.setView(box);
            toast.show();
        } catch (Throwable t) {
            Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show();
        }
    }

    private static String formatOffsetBadge(int off, boolean isEn) {
        if (off == 0) return isEn ? "0 min" : "0 د";
        return (off > 0 ? "+" : "") + off + (isEn ? " m" : " د");
    }

    private static int dp(Context ctx, float v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density + 0.5f);
    }
}
