package sa.h9.qibla;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
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

    private FullScreenSettingsDialog() {}

    public static void show(MainActivity activity) {
        if (activity == null || activity.isFinishing()) return;

        final Context ctx = activity;
        final SharedPreferences uiPrefs = activity.getSharedPreferences(MainActivity.UI_PREFS, Context.MODE_PRIVATE);
        final QiblaView qView = activity.getQiblaView();
        final boolean isEn = activity.isEnglishMode();
        final FullScreenWeatherDialog.ThemePalette pal = FullScreenWeatherDialog.getThemePalette(ctx);

        final Dialog dialog = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(false);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawable(new ColorDrawable(pal.rootBg));
            window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(pal.rootBg);

        // Top Navigation Bar
        LinearLayout topBar = new LinearLayout(ctx);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(ctx, 24), dp(ctx, 16), dp(ctx, 24), dp(ctx, 16));
        topBar.setBackgroundColor(pal.topBarBg);

        TextView title = new TextView(ctx);
        title.setText(isEn ? "⚙️ Application Settings" : "⚙️ إعدادات التطبيق");
        title.setTextSize(20f);
        title.setTextColor(pal.goldHighlight);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        TextView verBadge = new TextView(ctx);
        verBadge.setText(isEn ? ("  v" + BuildConfig.VERSION_NAME + " • Automotive") : ("  الإصدار " + BuildConfig.VERSION_NAME + " • لشاشات السيارات"));
        verBadge.setTextSize(13f);
        verBadge.setTextColor(pal.textSecondary);

        LinearLayout titleBox = new LinearLayout(ctx);
        titleBox.setOrientation(LinearLayout.HORIZONTAL);
        titleBox.setGravity(Gravity.CENTER_VERTICAL);
        titleBox.addView(title);
        titleBox.addView(verBadge);

        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        topBar.addView(titleBox, titleLp);

        // Save & Close Button
        Button closeBtn = new Button(ctx);
        closeBtn.setText(isEn ? "💾 Save & Close" : "💾 حفظ وإغلاق");
        closeBtn.setTextSize(14.5f);
        closeBtn.setTextColor(Color.WHITE);
        closeBtn.setAllCaps(false);
        closeBtn.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        closeBtn.setPadding(dp(ctx, 22), dp(ctx, 8), dp(ctx, 22), dp(ctx, 8));

        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setColor(pal.isLight ? 0xFF007A6C : 0xFF00695C);
        closeBg.setCornerRadius(dp(ctx, 16));
        closeBg.setStroke(dp(ctx, 1.8f), pal.goldHighlight);
        closeBtn.setBackground(closeBg);
        closeBtn.setOnClickListener(v -> {
            activity.invalidatePrayerTimes();
            if (qView != null) qView.postInvalidate();
            showCompactSuccessToast(ctx, isEn);
            dialog.dismiss();
        });
        topBar.addView(closeBtn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(ctx, 42)));

        root.addView(topBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Scrollable Body Content
        ScrollView scrollView = new ScrollView(ctx);
        scrollView.setFillViewport(true);
        scrollView.setVerticalScrollBarEnabled(true);

        LinearLayout bodyLayout = new LinearLayout(ctx);
        bodyLayout.setOrientation(LinearLayout.VERTICAL);
        bodyLayout.setPadding(dp(ctx, 24), dp(ctx, 16), dp(ctx, 24), dp(ctx, 28));

        // Wide display grid (Two columns for landscape car screens)
        boolean isWideScreen = ctx.getResources().getDisplayMetrics().widthPixels >= dp(ctx, 600);
        LinearLayout contentRow = new LinearLayout(ctx);
        contentRow.setOrientation(isWideScreen ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);

        // Column 1: Appearance & Display
        LinearLayout col1 = new LinearLayout(ctx);
        col1.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams col1Lp = isWideScreen
                ? new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f)
                : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (isWideScreen) col1Lp.rightMargin = dp(ctx, 16);

        View cardAppearance = createSectionCard(ctx, isEn ? "🎨 Appearance & Screen" : "🎨 المظهر والثيمات", pal);
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
        cardAppearanceContent.addView(createDropdownRow(ctx, isEn ? "Color Theme:" : "المظهر وثيم الألوان:", themeOptions, currentTheme, pal, pos -> {
            uiPrefs.edit().putInt(MainActivity.KEY_THEME_MODE, pos).apply();
            if (qView != null) {
                qView.setThemeMode(pos);
            }
            dialog.dismiss();
            new Handler(Looper.getMainLooper()).postDelayed(() -> show(activity), 80L);
        }));

        // 2. Weather Effects Spinner
        String[] weatherAnimOptions = isEn ? new String[]{
                "🌧️ Weather Effects Enabled",
                "🚫 Weather Effects Disabled"
        } : new String[]{
                "🌧️ تأثيرات الطقس وتفاعلات الرياح والغيوم مفعّلة",
                "🚫 تعطيل تأثيرات الطقس"
        };
        boolean weatherAnimOn = uiPrefs.getBoolean("weather_animation_enabled", true);
        cardAppearanceContent.addView(createDropdownRow(ctx, isEn ? "Weather Effects:" : "تأثيرات الطقس:", weatherAnimOptions, weatherAnimOn ? 0 : 1, pal, pos -> {
            boolean enabled = (pos == 0);
            uiPrefs.edit().putBoolean("weather_animation_enabled", enabled).apply();
            if (qView != null) {
                qView.setWeatherEffectsEnabled(enabled);
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
        cardAppearanceContent.addView(createDropdownRow(ctx, isEn ? "Weather Ambient Sound:" : "صوت الطقس المحيطي:", weatherSoundOptions, weatherSoundOn ? 0 : 1, pal, pos -> {
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
        cardAppearanceContent.addView(createDropdownRow(ctx, isEn ? "Compass Damping:" : "نعومة دوران البوصلة:", smoothOptions, smoothMode, pal, pos -> {
            uiPrefs.edit().putInt("compass_smooth_mode", pos).apply();
            if (qView != null) {
                qView.setSmoothMode(pos);
                qView.postInvalidate();
            }
        }));

        col1.addView(cardAppearance);

        // Display & Vehicle Card
        View cardDisplay = createSectionCard(ctx, isEn ? "🧭 Vehicle Compass & Dial" : "🧭 مؤشرات السيارة وبوصلة القبلة", pal);
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
        cardDisplayContent.addView(createDropdownRow(ctx, isEn ? "Qibla Alignment Badge:" : "شارة توجيه القبلة:", headingBadgeOptions, headingMode, pal, pos -> {
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
        cardDisplayContent.addView(createDropdownRow(ctx, isEn ? "Compass Indicator Style:" : "شكل مؤشر القبلة:", needleOptions, needleStyle, pal, pos -> {
            uiPrefs.edit().putInt("needle_style", pos).apply();
            if (qView != null) {
                qView.setNeedleStyle(pos);
                qView.postInvalidate();
            }
        }));

        // 3. Speedometer Unit Spinner
        String[] speedOptions = isEn ? new String[]{"Kilometers / Hour (km/h)", "Miles / Hour (mph)"} : new String[]{"كيلومتر في الساعة (كم/س)", "ميل في الساعة (mph)"};
        boolean isMph = uiPrefs.getBoolean("speed_mph", false);
        cardDisplayContent.addView(createDropdownRow(ctx, isEn ? "Speedometer Unit:" : "وحدة قياس السرعة GPS:", speedOptions, isMph ? 1 : 0, pal, pos -> {
            uiPrefs.edit().putBoolean("speed_mph", pos == 1).apply();
            if (qView != null) qView.postInvalidate();
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

        // Audio & Alerts Card
        View cardAudio = createSectionCard(ctx, isEn ? "🔊 Prayer Audio & Automotive Alerts" : "🔊 صوتيات الأذان وتنبيهات السيارة", pal);
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
        cardAudioContent.addView(createDropdownRow(ctx, isEn ? "Prayer Audio Alerts:" : "التنبيهات الصوتية للصلوات:", audioToggleOptions, audioOn ? 0 : 1, pal, pos -> {
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
        cardAudioContent.addView(createDropdownRow(ctx, isEn ? "Alert Sound Style:" : "نغمة وصوت دخول وقت الصلاة:", toneOptions, currentTone, pal, pos -> {
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
        cardAudioContent.addView(createDropdownRow(ctx, isEn ? "Radio Ducking:" : "تخفيف صوت ميديا السيارة أثناء الأذان:", duckingOptions, duckingOn ? 0 : 1, pal, pos -> {
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
        cardAudioContent.addView(createDropdownRow(ctx, isEn ? "Background Alerts:" : "تنبيهات الصلاة في الخلفية:", bgAlertOptions, bgActive ? 0 : 1, pal, pos -> {
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
        testAudioBtn.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
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
        LinearLayout.LayoutParams testBtnLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 42));
        testBtnLp.topMargin = dp(ctx, 6);
        testBtnLp.bottomMargin = dp(ctx, 8);
        cardAudioContent.addView(testAudioBtn, testBtnLp);

        col2.addView(cardAudio);

        // Prayer Calibration & Language Card
        View cardPrayer = createSectionCard(ctx, isEn ? "⚖️ Prayer Times & Language" : "⚖️ مواقيت الصلاة واللغة", pal);
        LinearLayout cardPrayerContent = (LinearLayout) ((ViewGroup) cardPrayer).getChildAt(1);

        // 0. City Location & Coordinates (Auto Live GPS or Manual Saudi City)
        String[] cityOptions = CityManager.getCityOptions(isEn);
        int currentCityIdx = CityManager.getSelectedCityIndex(ctx);
        cardPrayerContent.addView(createDropdownRow(ctx, isEn ? "📍 City Location & Coordinates:" : "📍 ربط المواقيت بإحداثيات المدينة:", cityOptions, currentCityIdx, pal, pos -> {
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
        cardPrayerContent.addView(createDropdownRow(ctx, isEn ? "Prayer Times Source:" : "مصدر حساب المواقيت:", sourceOptions, currentMethodIdx, pal, pos -> {
            int newMethod = (pos == 1) ? 11 : ((pos == 2) ? 3 : ((pos == 3) ? 5 : 4));
            uiPrefs.edit().putInt("prayer_calculation_method", newMethod).apply();
            activity.invalidatePrayerTimes();
        }));

        // 2. Individual Prayer Times Calibration (+ or - per prayer, ALL DEFAULT TO 0)
        TextView calibHeader = new TextView(ctx);
        calibHeader.setText(isEn ? "⚖️ Fine-Tune Each Prayer Minutes (Default: 0 min):" : "⚖️ تعديل وتدقيق كل صلاة يدوياً بالدقائق (تبدأ من 0 دقيقة):");
        calibHeader.setTextSize(13.5f);
        calibHeader.setTextColor(pal.goldHighlight);
        calibHeader.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
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
        cardPrayerContent.addView(createDropdownRow(ctx, isEn ? "Language:" : "لغة التطبيق:", langOptions, isEn ? 1 : 0, pal, pos -> {
            boolean englishSelected = (pos == 1);
            if (englishSelected != isEn) {
                activity.applyLanguageMode(englishSelected);
                dialog.dismiss();
                new Handler(Looper.getMainLooper()).postDelayed(() -> show(activity), 120L);
            }
        }));

        LinearLayout.LayoutParams cardPrayerLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardPrayerLp.topMargin = dp(ctx, 16);
        col2.addView(cardPrayer, cardPrayerLp);

        // System & Info Card
        View cardSystem = createSectionCard(ctx, isEn ? "🔄 System & Updates" : "🔄 النظام والتحديثات", pal);
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
        tvSysInfo.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
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
            dialog.dismiss();
            activity.showUserGuideDialog();
        });
        cardSystemContent.addView(guideBtn);

        // Developer Profile Button
        Button devBtn = createActionBtn(ctx, isEn ? "👑 About Developer (Bandar Bin Sulaiman)" : "👑 حول المطور (بندر بن سليمان)",
                pal.isLight ? 0xFFD8E5EC : 0xFF1B3B48, pal.goldHighlight);
        devBtn.setTextColor(pal.isLight ? pal.textPrimary : Color.WHITE);
        devBtn.setOnClickListener(v -> DeveloperDialog.show(activity, isEn));
        cardSystemContent.addView(devBtn);

        // Instant Prayer Times Sync Button
        Button syncPrayerBtn = createActionBtn(ctx, isEn ? "🔄 Force Sync Prayer Times Now" : "🔄 تحديث فوري لمواقيت الصلاة الآن",
                pal.isLight ? 0xFF00758A : 0xFF144D55, pal.accentColor);
        syncPrayerBtn.setOnClickListener(v -> {
            activity.invalidatePrayerTimes();
            Toast.makeText(ctx, isEn ? "Syncing Umm al-Qura prayer times..." : "جاري تحديث مواقيت أم القرى الرسمية...", Toast.LENGTH_SHORT).show();
        });
        cardSystemContent.addView(syncPrayerBtn);

        // Changelog Button
        Button changelogBtn = createActionBtn(ctx, isEn ? "📋 Version Changelog" : "📋 سجل التحديثات والمميزات الجديدة",
                pal.isLight ? 0xFFD8E5EC : 0xFF22323A, pal.cardStroke);
        changelogBtn.setTextColor(pal.isLight ? pal.textPrimary : Color.WHITE);
        changelogBtn.setOnClickListener(v -> {
            dialog.dismiss();
            activity.showChangelogDialog();
        });
        cardSystemContent.addView(changelogBtn);

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
        bottomSaveBtn.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
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
            dialog.dismiss();
        });
        LinearLayout.LayoutParams bSaveLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 54));
        bSaveLp.topMargin = dp(ctx, 20);
        bSaveLp.bottomMargin = dp(ctx, 10);
        bodyLayout.addView(bottomSaveBtn, bSaveLp);

        scrollView.addView(bodyLayout);

        root.addView(scrollView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        dialog.setContentView(root);
        dialog.show();
    }

    private static View createSectionCard(Context ctx, String titleText, FullScreenWeatherDialog.ThemePalette pal) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(ctx, 20), dp(ctx, 16), dp(ctx, 20), dp(ctx, 16));

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(pal.cardBg);
        cardBg.setCornerRadius(dp(ctx, 18));
        cardBg.setStroke(dp(ctx, 1.2f), pal.cardStroke);
        card.setBackground(cardBg);

        TextView header = new TextView(ctx);
        header.setText(titleText);
        header.setTextSize(17f);
        header.setTextColor(pal.goldHighlight);
        header.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.setPadding(0, 0, 0, dp(ctx, 12));
        card.addView(header);

        LinearLayout content = new LinearLayout(ctx);
        content.setOrientation(LinearLayout.VERTICAL);
        card.addView(content);

        return card;
    }

    private interface OnSpinnerIndexListener {
        void onSelected(int index);
    }

    private static View createDropdownRow(final Context ctx, String label, final String[] items, int selectedIdx,
                                          final FullScreenWeatherDialog.ThemePalette pal, final OnSpinnerIndexListener listener) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(ctx, 4), 0, dp(ctx, 10));

        TextView labelTv = new TextView(ctx);
        labelTv.setText(label);
        labelTv.setTextSize(14f);
        labelTv.setTextColor(pal.textPrimary);
        labelTv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        labelTv.setPadding(dp(ctx, 2), 0, dp(ctx, 2), dp(ctx, 4));
        row.addView(labelTv);

        Spinner spinner = new Spinner(ctx, Spinner.MODE_DROPDOWN);
        spinner.setPadding(dp(ctx, 12), dp(ctx, 6), dp(ctx, 12), dp(ctx, 6));

        GradientDrawable spBg = new GradientDrawable();
        spBg.setColor(pal.isLight ? 0xFFFFFFFF : 0xFF1B2A33);
        spBg.setCornerRadius(dp(ctx, 12));
        spBg.setStroke(dp(ctx, 1), pal.cardStroke);
        spinner.setBackground(spBg);

        final int dropdownBg = pal.isLight ? 0xFFFFFFFF : 0xF50D1920;
        final int itemTextColor = pal.isLight ? 0xFF0C242C : 0xFFFFFFFF;
        final int selTextColor = pal.accentColor;

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(ctx, android.R.layout.simple_spinner_item, items) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                tv.setTextColor(itemTextColor);
                tv.setTextSize(14.5f);
                tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                tv.setCompoundDrawablesWithIntrinsicBounds(0, 0, android.R.drawable.arrow_down_float, 0);
                tv.setCompoundDrawablePadding(dp(ctx, 8));
                return tv;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getDropDownView(position, convertView, parent);
                tv.setPadding(dp(ctx, 18), dp(ctx, 14), dp(ctx, 18), dp(ctx, 14));
                tv.setTextSize(15f);
                tv.setBackgroundColor(dropdownBg);
                boolean selected = (position == spinner.getSelectedItemPosition());
                tv.setTextColor(selected ? selTextColor : itemTextColor);
                tv.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
                return tv;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        if (selectedIdx >= 0 && selectedIdx < items.length) {
            spinner.setSelection(selectedIdx, false);
        }

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            private int lastPos = selectedIdx;
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position != lastPos) {
                    lastPos = position;
                    if (listener != null) listener.onSelected(position);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        LinearLayout.LayoutParams spLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 44));
        row.addView(spinner, spLp);

        return row;
    }

    private static Button createActionBtn(Context ctx, String text, int bgColor, int strokeColor) {
        Button btn = new Button(ctx);
        btn.setText(text);
        btn.setTextSize(14.5f);
        btn.setTextColor(Color.WHITE);
        btn.setAllCaps(false);
        btn.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(dp(ctx, 14));
        bg.setStroke(dp(ctx, 1), strokeColor);
        btn.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 42));
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
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(ctx, 4), 0, dp(ctx, 4));

        TextView nameTv = new TextView(ctx);
        nameTv.setText(prayerName);
        nameTv.setTextSize(13.5f);
        nameTv.setTextColor(pal.textPrimary);
        nameTv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        row.addView(nameTv, nameLp);

        final int[] curOff = {uiPrefs.getInt(prefKey, defaultOffset)};

        final TextView badge = new TextView(ctx);
        badge.setText(formatOffsetBadge(curOff[0], isEn));
        badge.setTextSize(13f);
        badge.setTextColor(pal.goldHighlight);
        badge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
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
        if (ctx == null) return;
        try {
            Toast toast = new Toast(ctx);
            toast.setDuration(Toast.LENGTH_SHORT);
            toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, dp(ctx, 48));

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
            tv.setText(isEn ? "✅ Settings Saved" : "✅ تم حفظ الإعدادات");
            tv.setTextColor(0xFFFFFFFF);
            tv.setTextSize(13.5f);
            tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            tv.setGravity(Gravity.CENTER);
            box.addView(tv);

            toast.setView(box);
            toast.show();
        } catch (Throwable t) {
            Toast.makeText(ctx, isEn ? "✅ Settings Saved" : "✅ تم حفظ الإعدادات", Toast.LENGTH_SHORT).show();
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
