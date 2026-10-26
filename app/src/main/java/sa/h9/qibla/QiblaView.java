package sa.h9.qibla;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.location.Location;
import android.view.MotionEvent;
import android.view.View;
import androidx.core.content.res.ResourcesCompat;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class QiblaView extends View {
    private static final double KAABA_LAT = 21.422487;
    private static final double KAABA_LON = 39.826206;

    private static final String[] PRAYER_NAMES_AR = {"الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"};
    private static final String[] PRAYER_NAMES_EN = {"Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"};
    private static final String[] PRAYER_ICONS = {"🌅", "☀️", "🔆", "🌤️", "🌇", "🌙"};

    private static final String[] ATHKAR_AR = {
            "سُبْحَانَ الَّذِي سَخَّرَ لَنَا هَذَا وَمَا كُنَّا لَهُ مُقْرِنِينَ وَإِنَّا إِلَى رَبِّنَا لَمُنْقَلِبُونَ",
            "اللَّهُمَّ إِنَّا نَسْأَلُكَ فِي سَفَرِنَا هَذَا الْبِرَّ وَالتَّقْوَى، وَمِنَ الْعَمَلِ مَا تَرْضَى",
            "اللَّهُمَّ هَوِّنْ عَلَيْنَا سَفَرَنَا هَذَا وَاطْوِ عَنَّا بُعْدَهُ",
            "اللَّهُمَّ أَنْتَ الصَّاحِبُ فِي السَّفَرِ، وَالْخَلِيفَةُ فِي الأَهْلِ",
            "لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ",
            "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ ، سُبْحَانَ اللَّهِ الْعَظِيمِ",
            "أَصْبَحْنَا وَأَصْبَحَ الْمُلْكُ لِلَّهِ رَبِّ الْعَالَمِينَ",
            "اللَّهُمَّ صَلِّ وَسَلِّمْ عَلَى نَبِيِّنَا مُحَمَّدٍ"
    };

    private static final String[] ATHKAR_EN = {
            "Glory to Him Who has subjected this to us, and we could never have it by ourselves.",
            "O Allah, we ask You on this journey of ours for righteousness and piety.",
            "O Allah, make easy for us this journey of ours and shorten its distance.",
            "O Allah, You are our Companion on the journey and Caretaker of our family.",
            "There is no deity except Allah alone; to Him belongs all praise and power.",
            "Glory be to Allah and His is the praise, Glory be to Allah the Supreme.",
            "We have reached the morning, and the dominion belongs to Allah, Lord of the worlds.",
            "O Allah, send blessings and peace upon our Prophet Muhammad."
    };

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Locale ar = new Locale("ar");
    private Bitmap logoBitmap;

    private float heading;
    private float qiblaBearing;
    private float distanceKm;
    private float gpsAccuracy = -1f;
    private float speedKmh = -1f;
    private double latitude;
    private double longitude;
    private String status = "بانتظار الموقع…";
    private String networkStatus = "فحص الإنترنت…";
    private String headingSource = "بانتظار اتجاه السيارة";
    private String prayerStatus = "بانتظار مواقيت الصلاة…";
    private String hijriDate = "";
    private String hijriDateEn = "";
    private String locationName = "الموقع الحالي";
    private String regionName = "";
    private String[] prayerNames = PRAYER_NAMES_AR;
    private String[] prayerTimes = {"--:--", "--:--", "--:--", "--:--", "--:--", "--:--"};
    private boolean hasLocation;
    private boolean hasHeading;
    private boolean hasPrayerTimes;
    private boolean englishMode;
    private float smoothedHeading = Float.NaN;

    // Luxury Theme Modes
    public static final int THEME_AUTO = 0;
    public static final int THEME_EMERALD = 1;   // Royal Emerald & Gold (زمردي ملكي)
    public static final int THEME_OBSIDIAN = 2;  // Obsidian Luxury Navy (كحلي فاخر)
    public static final int THEME_AMBER = 3;     // Desert Amber & Gold (عنبر ذهبي)
    public static final int THEME_PEARL = 4;     // Pearl Day (نهاري لؤلؤي)

    // Backward compatibility aliases
    public static final int THEME_DAY = THEME_PEARL;
    public static final int THEME_NIGHT = THEME_EMERALD;

    private int themeMode = THEME_AUTO;
    private float fontScale = 0.90f;

    public static final int FONT_DEFAULT = 0;
    public static final int FONT_CAIRO = 1;
    public static final int FONT_TAJAWAL = 2;
    public static final int FONT_ALMARAI = 3;
    public static final int FONT_READEX = 4;
    public static final int FONT_AMIRI = 5;

    private int fontIndex = FONT_DEFAULT;
    private boolean panelSwapped = false;
    private Typeface currentTypeface;

    public QiblaView(Context context) {
        super(context);
        updateTypeface();
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        try {
            logoBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_qibla_logo);
        } catch (Exception ignored) { }
    }

    public void tick() { invalidate(); }

    public void setEnglishMode(boolean english) {
        englishMode = english;
        invalidate();
    }

    public boolean isEnglishMode() { return englishMode; }

    private String[] activePrayerNames() {
        return englishMode ? PRAYER_NAMES_EN : PRAYER_NAMES_AR;
    }

    public void setThemeMode(int mode) {
        if (mode >= THEME_AUTO && mode <= THEME_PEARL) {
            themeMode = mode;
        } else {
            themeMode = THEME_AUTO;
        }
        invalidate();
    }

    public int getThemeMode() { return themeMode; }

    public void setFontScale(float scale) {
        fontScale = Math.max(0.70f, Math.min(1.50f, scale));
        invalidate();
    }

    public float getFontScale() { return fontScale; }

    public void setFontIndex(int index) {
        fontIndex = (index >= 0 && index <= 5) ? index : FONT_DEFAULT;
        updateTypeface();
        invalidate();
    }

    public int getFontIndex() {
        return fontIndex;
    }

    public void setPanelSwapped(boolean swapped) {
        panelSwapped = swapped;
        invalidate();
    }

    public boolean isPanelSwapped() {
        return panelSwapped;
    }

    public static String getFontName(int index, boolean english) {
        switch (index) {
            case FONT_CAIRO: return english ? "Cairo" : "كايرو";
            case FONT_TAJAWAL: return english ? "Tajawal" : "تجوال";
            case FONT_ALMARAI: return english ? "Almarai" : "المراعي";
            case FONT_READEX: return english ? "Readex Pro" : "ريدكس برو";
            case FONT_AMIRI: return english ? "Amiri" : "أميري";
            default: return english ? "Default" : "الافتراضي";
        }
    }

    public Typeface getCurrentTypeface() {
        return currentTypeface != null ? currentTypeface : Typeface.DEFAULT;
    }

    private void updateTypeface() {
        try {
            switch (fontIndex) {
                case FONT_CAIRO:
                    currentTypeface = ResourcesCompat.getFont(getContext(), R.font.cairo);
                    break;
                case FONT_TAJAWAL:
                    currentTypeface = ResourcesCompat.getFont(getContext(), R.font.tajawal);
                    break;
                case FONT_ALMARAI:
                    currentTypeface = ResourcesCompat.getFont(getContext(), R.font.almarai);
                    break;
                case FONT_READEX:
                    currentTypeface = ResourcesCompat.getFont(getContext(), R.font.readex_pro);
                    break;
                case FONT_AMIRI:
                    currentTypeface = ResourcesCompat.getFont(getContext(), R.font.amiri);
                    break;
                default:
                    currentTypeface = Typeface.create("sans", Typeface.NORMAL);
                    break;
            }
        } catch (Exception e) {
            currentTypeface = Typeface.create("sans", Typeface.NORMAL);
        }
        if (currentTypeface != null) {
            p.setTypeface(currentTypeface);
        }
    }

    public void setHeading(float value, String source) {
        float target = normalize(value);
        if (Float.isNaN(smoothedHeading)) {
            smoothedHeading = target;
        } else {
            float delta = signedAngle(target - smoothedHeading);
            smoothedHeading = normalize(smoothedHeading + delta * 0.22f);
        }
        heading = smoothedHeading;
        hasHeading = true;
        if (source != null && !source.isEmpty()) {
            headingSource = source;
        }
        invalidate();
    }

    public void setLocation(Location location) {
        if (location == null) return;
        hasLocation = true;
        latitude = location.getLatitude();
        longitude = location.getLongitude();
        qiblaBearing = calculateBearing(latitude, longitude);
        float[] out = new float[1];
        Location.distanceBetween(latitude, longitude, KAABA_LAT, KAABA_LON, out);
        distanceKm = out[0] / 1000f;
        gpsAccuracy = location.hasAccuracy() ? location.getAccuracy() : -1f;
        speedKmh = location.hasSpeed() ? (location.getSpeed() * 3.6f) : -1f;
        status = gpsAccuracy > 0 && gpsAccuracy <= 20 ? "GPS دقيق" : "الموقع متصل";
        invalidate();
    }

    public void setStatus(String text) { status = text; invalidate(); }
    public void setNetworkStatus(String text) { networkStatus = text; invalidate(); }
    public void setPrayerStatus(String text) { prayerStatus = text; invalidate(); }
    public void setLocationName(String text) { locationName = text; invalidate(); }
    public String getLocationName() { return locationName; }
    public void setRegionName(String text) { regionName = text == null ? "" : text; invalidate(); }

    public void setPrayerTimes(String[] names, String[] times, String hijri) {
        setPrayerTimes(names, times, hijri, "");
    }

    public void setPrayerTimes(String[] names, String[] times, String hijri, String hijriEn) {
        if (names != null && names.length == 6) prayerNames = names;
        if (times != null && times.length == 6) prayerTimes = times;
        hijriDate = hijri == null ? "" : hijri;
        hijriDateEn = hijriEn == null ? "" : hijriEn;
        hasPrayerTimes = true;
        prayerStatus = "طريقة الحساب: أم القرى";
        invalidate();
    }

    private String adhanBannerText = null;
    private long adhanBannerUntil = 0L;

    public boolean hasPrayerTimes() { return hasPrayerTimes; }
    public String[] getPrayerTimes() { return prayerTimes; }
    public String[] getPrayerNames() { return prayerNames; }

    public void showAdhanBanner(String prayerName, String timeStr) {
        if (englishMode) {
            adhanBannerText = "🕌 Time for " + prayerName + " Prayer (" + timeStr + ") • May Allah accept";
        } else {
            adhanBannerText = "🕌 حان الآن موعد أذان " + prayerName + " (" + timeStr + ") • تقبل الله طاعتكم";
        }
        adhanBannerUntil = System.currentTimeMillis() + 25000L;
        invalidate();
    }

    public void dismissAdhanBanner() {
        adhanBannerText = null;
        adhanBannerUntil = 0L;
        invalidate();
    }

    private String formatAccuracy() {
        if (gpsAccuracy < 0) return "—";
        if (gpsAccuracy <= 12) return englishMode ? "Excellent" : "ممتازة";
        if (gpsAccuracy <= 25) return englishMode ? "Good" : "جيدة";
        return String.format(Locale.US, englishMode ? "%.0f m" : "%.0f م", gpsAccuracy);
    }

    private String localizedHeadingSource() {
        if (!hasHeading) {
            if (!hasLocation) return englishMode ? "Waiting for GPS" : "بانتظار GPS";
            return englishMode ? "Waiting for compass" : "بانتظار بوصلة السيارة";
        }
        if (!englishMode) {
            if (headingSource != null && (headingSource.contains("اتجاه حي") || headingSource.contains("GPS"))) {
                return "GPS مباشر";
            }
            if (headingSource != null && (headingSource.contains("هافال") || headingSource.contains("GWM") || headingSource.contains("السيارة"))) {
                return "بوصلة هافال";
            }
            if (headingSource != null && headingSource.contains("الشاشة")) {
                return "بوصلة الشاشة";
            }
            if (headingSource != null && headingSource.contains("آخر اتجاه")) {
                return "آخر اتجاه";
            }
            return headingSource != null ? headingSource : "بوصلة نشطة";
        }
        if (headingSource == null) return "Compass Active";
        if (headingSource.contains("بوصلة الشاشة")) return "Device Compass";
        if (headingSource.contains("اتجاه حي") || headingSource.contains("GPS")) return "Live GPS";
        if (headingSource.contains("آخر اتجاه")) return "Saved Heading";
        if (headingSource.contains("هافال") || headingSource.contains("GWM") || headingSource.contains("السيارة")) return "Vehicle Compass";
        return "Compass Active";
    }

    private String localizedNetworkStatus() {
        if (!englishMode) return networkStatus;
        if (networkStatus == null) return "Checking network…";
        if (networkStatus.contains("متصل") && !networkStatus.contains("غير")) return "Online";
        if (networkStatus.contains("بدون") || networkStatus.contains("غير متصل")) return "Offline";
        return "Network Ready";
    }

    private String localizedPrayerStatus() {
        if (!englishMode) return prayerStatus;
        if (!hasPrayerTimes) return "Calculating prayer times…";
        return "Umm al-Qura • Offline Calculation";
    }

    private String localizedLocationName() {
        if (locationName == null || locationName.isEmpty() || "الموقع الحالي".equals(locationName)) {
            return englishMode ? "Current Location" : "الموقع الحالي";
        }
        if (!englishMode) return locationName;
        if (locationName.contains("الرياض")) return "Riyadh";
        if (locationName.contains("مكة المكرمة") || locationName.contains("مكة")) return "Makkah";
        if (locationName.contains("المدينة المنورة") || locationName.contains("المدينة")) return "Madinah";
        if (locationName.contains("بريدة")) return "Buraydah";
        if (locationName.contains("عنيزة")) return "Unaizah";
        if (locationName.contains("الرس")) return "Ar Rass";
        if (locationName.contains("جدة")) return "Jeddah";
        if (locationName.contains("الدمام")) return "Dammam";
        return locationName;
    }

    private int getEffectiveTheme() {
        if (themeMode == THEME_AUTO) {
            return isDaytime() ? THEME_PEARL : THEME_EMERALD;
        }
        return themeMode;
    }

    private boolean isDaytime() {
        Calendar now = Calendar.getInstance();
        int current = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int sunrise = hasPrayerTimes ? parseMinutes(prayerTimes[1]) : -1;
        int maghrib = hasPrayerTimes ? parseMinutes(prayerTimes[4]) : -1;
        if (sunrise >= 0 && maghrib >= 0) return current >= sunrise && current < maghrib;
        return current >= 6 * 60 && current < 18 * 60;
    }

    public boolean isNightTheme() {
        return getEffectiveTheme() != THEME_PEARL;
    }

    private int getArabesqueColor() {
        switch (getEffectiveTheme()) {
            case THEME_EMERALD: return 0x16E5B64A; // soft gold
            case THEME_OBSIDIAN: return 0x1638B6FF; // soft celestial blue
            case THEME_AMBER: return 0x16FFA834;    // soft warm amber
            case THEME_PEARL: return 0x16006B78;    // soft petrol teal
            default: return 0x16E5B64A;
        }
    }

    private LinearGradient getBackgroundGradient(float w, float h) {
        switch (getEffectiveTheme()) {
            case THEME_EMERALD:
                return new LinearGradient(0, 0, w, h,
                        new int[]{0xFF02120D, 0xFF05271F, 0xFF020D0A},
                        new float[]{0f, 0.65f, 1f}, Shader.TileMode.CLAMP);
            case THEME_OBSIDIAN:
                return new LinearGradient(0, 0, w, h,
                        new int[]{0xFF020810, 0xFF081C2E, 0xFF030913},
                        new float[]{0f, 0.65f, 1f}, Shader.TileMode.CLAMP);
            case THEME_AMBER:
                return new LinearGradient(0, 0, w, h,
                        new int[]{0xFF130903, 0xFF271507, 0xFF0F0602},
                        new float[]{0f, 0.65f, 1f}, Shader.TileMode.CLAMP);
            case THEME_PEARL:
            default:
                return new LinearGradient(0, 0, w, h,
                        new int[]{0xFFEFF5F7, 0xFFD4E6EB, 0xFFBED8E0},
                        new float[]{0f, 0.65f, 1f}, Shader.TileMode.CLAMP);
        }
    }

    private int cardBgFill() {
        switch (getEffectiveTheme()) {
            case THEME_EMERALD: return 0xC8041812;
            case THEME_OBSIDIAN: return 0xC8061422;
            case THEME_AMBER: return 0xC81E1006;
            case THEME_PEARL: return 0xEFF8FCFD;
            default: return 0xC8041812;
        }
    }

    private int cardStrokeColor() {
        switch (getEffectiveTheme()) {
            case THEME_EMERALD: return 0x44E5B64A;
            case THEME_OBSIDIAN: return 0x443D8BFF;
            case THEME_AMBER: return 0x44FFA834;
            case THEME_PEARL: return 0x664B7988;
            default: return 0x44E5B64A;
        }
    }

    private int primaryTextColor() {
        return (getEffectiveTheme() == THEME_PEARL) ? 0xFF0C242C : 0xFFF5F8FA;
    }

    private int secondaryTextColor() {
        return (getEffectiveTheme() == THEME_PEARL) ? 0xFF2B4D58 : 0xFFB8C8CE;
    }

    private int mutedTextColor() {
        return (getEffectiveTheme() == THEME_PEARL) ? 0xFF4A6F7B : 0xFF7D929B;
    }

    private int goldColor() {
        switch (getEffectiveTheme()) {
            case THEME_EMERALD: return 0xFFFFD768;
            case THEME_OBSIDIAN: return 0xFFFFD25A;
            case THEME_AMBER: return 0xFFFFBA49;
            case THEME_PEARL: return 0xFF996300;
            default: return 0xFFFFD768;
        }
    }

    private int prayerPrimaryColor() {
        return (getEffectiveTheme() == THEME_PEARL) ? 0xFF0D2832 : 0xFFEBF3F5;
    }

    private int prayerSecondaryColor() {
        return (getEffectiveTheme() == THEME_PEARL) ? 0xFF355864 : 0xFFB4C5CB;
    }

    private int prayerAccentColor() {
        switch (getEffectiveTheme()) {
            case THEME_EMERALD: return 0xFF00F5C8;
            case THEME_OBSIDIAN: return 0xFF38B6FF;
            case THEME_AMBER: return 0xFFFFB74D;
            case THEME_PEARL: return 0xFF00757D;
            default: return 0xFF00F5C8;
        }
    }

    private String currentThikrText() {
        long sec = System.currentTimeMillis() / 15000L;
        int idx = (int) (sec % ATHKAR_AR.length);
        if (idx < 0) idx = 0;
        return englishMode ? ATHKAR_EN[idx] : ATHKAR_AR[idx];
    }

    public boolean isQiblaAligned() {
        if (!hasLocation || !hasHeading) return false;
        float arrow = normalize(qiblaBearing - heading);
        float signed = arrow > 180 ? arrow - 360 : arrow;
        return Math.abs(signed) <= 5.0f;
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth(), h = getHeight();
        drawBackground(c, w, h);
        drawTopBar(c, w, h);
        boolean wideLandscape = (w / h) >= 1.35f;
        boolean splitScreen = !wideLandscape && (w / h) >= 0.85f;
        if (wideLandscape) {
            float compassR = Math.min(h * .225f, w * .138f);
            if (panelSwapped) {
                drawPrayerPanel(c, w, h, true);
                drawCompass(c, w * .565f, h * .525f, compassR);
                drawLeftCards(c, w, h, true);
            } else {
                drawLeftCards(c, w, h, false);
                drawCompass(c, w * .435f, h * .525f, compassR);
                drawPrayerPanel(c, w, h, false);
            }
            drawBottomAthkarBar(c, w, h);
        } else if (splitScreen) {
            drawSplitScreenTwoPane(c, w, h);
        } else {
            drawPortraitLayout(c, w, h);
        }
        drawAdhanBanner(c, w, h);
    }

    private void drawSplitScreenTwoPane(Canvas c, float w, float h) {
        float leftPaneX = panelSwapped ? (w * .515f) : (w * .030f);
        float rightPaneX = panelSwapped ? (w * .030f) : (w * .515f);
        float paneW = w * .455f;
        boolean rtl = !englishMode;

        // 1. Enlarged Compass in its pane - high visibility, perfectly balanced
        float compassR = Math.min(paneW * 0.44f, h * 0.170f);
        float compassCx = leftPaneX + paneW * 0.5f;
        float compassCy = h * 0.315f;
        drawCompass(c, compassCx, compassCy, compassR);

        // 2. Telemetry Cards pushed downward, with RTL-aware row/col arrangement
        float colGap = w * .015f;
        float cardW = (paneW - colGap) / 2f;
        float cardH = h * 0.105f;
        float row0Y = h * 0.540f;
        float row1Y = row0Y + cardH + h * 0.016f;

        float rightColX = leftPaneX + cardW + colGap;
        float leftColX = leftPaneX;
        float primaryColX = rtl ? rightColX : leftColX;
        float secondaryColX = rtl ? leftColX : rightColX;

        // Row 0 - Primary (Right in Arabic): Qibla Angle | Secondary (Left in Arabic): Vehicle Heading
        drawInfoCard(c, primaryColX, row0Y, cardW, cardH,
                englishMode ? "Qibla Angle" : "زاوية القبلة",
                hasLocation ? String.format(Locale.US, "%.0f°", qiblaBearing) : "—",
                "◈", prayerAccentColor());

        drawInfoCard(c, secondaryColX, row0Y, cardW, cardH,
                englishMode ? "Vehicle Heading" : "اتجاه المركبة",
                hasHeading ? String.format(Locale.US, "%.0f°", heading) : "—",
                "↗", hasHeading ? prayerAccentColor() : 0xFFE5B64A);

        // Row 1 - Primary (Right in Arabic): Speed | Secondary (Left in Arabic): Heading Source
        boolean showSpeed = speedKmh > 1f;
        drawInfoCard(c, primaryColX, row1Y, cardW, cardH,
                showSpeed ? (englishMode ? "Speed" : "السرعة") : (englishMode ? "GPS Accuracy" : "دقة GPS"),
                showSpeed ? String.format(Locale.US, "%.0f km/h", speedKmh) : formatAccuracy(),
                showSpeed ? "🚗" : "◎", prayerAccentColor());

        drawInfoCard(c, secondaryColX, row1Y, cardW, cardH,
                englishMode ? "Heading Source" : "مصدر الاتجاه",
                localizedHeadingSource(),
                "🧭", goldColor());

        // 3. Prayer Times Card in Prayer pane (bottom aligns with Row 0 cards)
        float px = rightPaneX;
        float py = h * 0.130f;
        float pw = paneW;
        float ph = (row0Y + cardH) - py;
        roundCard(c, new RectF(px, py, px + pw, py + ph), 16f, 1.2f);

        float padX = pw * 0.06f;
        p.setTextAlign(rtl ? Paint.Align.RIGHT : Paint.Align.LEFT);
        p.setColor(goldColor());
        ts(ph * 0.082f);
        p.setFakeBoldText(true);
        float headerY = py + ph * 0.112f;
        // Clean single-line header without overlapping Hijri date
        drawFittedTextAligned(c, englishMode ? "🕌 Prayer Times" : "🕌 مواقيت الصلاة",
                rtl ? (px + pw - padX) : (px + padX), headerY, pw * 0.88f);
        p.setFakeBoldText(false);

        String[] names = activePrayerNames();
        int next = getNextPrayerIndex();
        float rowStart = py + ph * 0.230f;
        float rowH = ph * 0.122f;
        float maxColW = (pw - padX * 2.2f) * 0.50f;

        for (int i = 0; i < 6; i++) {
            float ry = rowStart + i * rowH;
            if (i == next) {
                p.setColor(0x28FFA726);
                c.drawRoundRect(new RectF(px + pw * .03f, ry - rowH * .70f, px + pw * .97f, ry + rowH * .26f), 10f, 10f, p);
                stroke.setStyle(Paint.Style.STROKE);
                stroke.setStrokeWidth(1.2f);
                stroke.setColor(0xBBFFD768);
                c.drawRoundRect(new RectF(px + pw * .03f, ry - rowH * .70f, px + pw * .97f, ry + rowH * .26f), 10f, 10f, stroke);
            }

            ts(ph * 0.072f);
            String icon = (i < PRAYER_ICONS.length) ? PRAYER_ICONS[i] : "•";
            p.setColor(i == next ? goldColor() : prayerPrimaryColor());
            p.setFakeBoldText(i == next);

            if (rtl) {
                p.setTextAlign(Paint.Align.RIGHT);
                drawFittedTextAligned(c, icon + "  " + names[i], px + pw - padX, ry, maxColW);
                p.setTextAlign(Paint.Align.LEFT);
                drawFittedTextAligned(c, format12Hour(prayerTimes[i]), px + padX, ry, maxColW);
            } else {
                p.setTextAlign(Paint.Align.LEFT);
                drawFittedTextAligned(c, icon + "  " + names[i], px + padX, ry, maxColW);
                p.setTextAlign(Paint.Align.RIGHT);
                drawFittedTextAligned(c, format12Hour(prayerTimes[i]), px + pw - padX, ry, maxColW);
            }
            p.setFakeBoldText(false);
        }

        // 4. Next Prayer Card in Prayer pane (aligned precisely with Row 1 cards)
        float ny = row1Y;
        float nh = cardH;
        roundCard(c, new RectF(px, ny, px + pw, ny + nh), 16f, 1.4f);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(1.4f);
        stroke.setColor(0xAAFFD768);
        c.drawRoundRect(new RectF(px, ny, px + pw, ny + nh), 16f, 16f, stroke);

        p.setTextAlign(Paint.Align.CENTER);
        ts(nh * 0.28f);
        p.setColor(goldColor());
        p.setFakeBoldText(true);
        drawFittedText(c, formatNextPrayerTitle(), px + pw * 0.5f, ny + nh * 0.38f, pw * 0.90f);

        ts(nh * 0.38f);
        p.setColor(primaryTextColor());
        drawFittedText(c, formatNextPrayerCountdown(), px + pw * 0.5f, ny + nh * 0.78f, pw * 0.92f);
        p.setFakeBoldText(false);

        // 5. Bottom Athkar Bar (Removed red symbol)
        float barX = w * .030f;
        float barY = h * 0.785f;
        float barW = w * .940f;
        float barH = h * 0.065f;
        RectF barRect = new RectF(barX, barY, barX + barW, barY + barH);
        roundCard(c, barRect, 14f, 1.2f);

        p.setTextAlign(Paint.Align.CENTER);
        ts(barH * 0.52f);
        p.setFakeBoldText(true);
        boolean playing = SoundManager.isPlaying();
        p.setColor(playing ? 0xFF00F5C8 : goldColor());
        String thikr = playing
                ? (englishMode ? "🔊 Playing Takbeerat... (Tap to stop)" : "🔊 جاري تشغيل تكبيرات الأذان... (اضغط للإيقاف)")
                : currentThikrText();
        drawFittedText(c, thikr, barX + barW * 0.5f, barY + barH * 0.68f, barW * 0.94f);
        p.setFakeBoldText(false);
    }

    private void drawPortraitLayout(Canvas c, float w, float h) {
        float compassR = Math.min(w * .19f, h * .105f);
        drawCompass(c, w * .50f, h * .215f, compassR);

        float pad = w * .025f;
        float gap = w * .015f;
        float cardW = (w - pad * 2f - gap * 3f) / 4f;
        float cardH = h * .085f;
        float cardY = h * .360f;

        drawInfoCard(c, pad + 0 * (cardW + gap), cardY, cardW, cardH,
                englishMode ? "Qibla Angle" : "زاوية القبلة",
                hasLocation ? String.format(Locale.US, "%.0f°", qiblaBearing) : "—",
                "◈", prayerAccentColor());
        drawInfoCard(c, pad + 1 * (cardW + gap), cardY, cardW, cardH,
                englishMode ? "Heading" : "اتجاه المركبة",
                hasHeading ? String.format(Locale.US, "%.0f°", heading) : "—",
                "↗", hasHeading ? prayerAccentColor() : 0xFFE5B64A);
        boolean showSpeed = speedKmh > 1f;
        drawInfoCard(c, pad + 2 * (cardW + gap), cardY, cardW, cardH,
                showSpeed ? (englishMode ? "Speed" : "السرعة") : (englishMode ? "GPS Accuracy" : "دقة GPS"),
                showSpeed ? String.format(Locale.US, "%.0f km/h", speedKmh) : formatAccuracy(),
                showSpeed ? "🚗" : "◎", prayerAccentColor());
        drawInfoCard(c, pad + 3 * (cardW + gap), cardY, cardW, cardH,
                englishMode ? "Heading Source" : "مصدر الاتجاه",
                localizedHeadingSource(),
                "🧭", goldColor());

        float px = pad, py = h * .465f, pw = w - pad * 2f, ph = h * .275f;
        roundCard(c, new RectF(px, py, px + pw, py + ph));
        p.setTextAlign(englishMode ? Paint.Align.LEFT : Paint.Align.RIGHT);
        p.setColor(goldColor());
        ts(ph * .115f);
        p.setFakeBoldText(true);
        String prayerHeader = englishMode
                ? ("Prayer Times (" + localizedLocationName() + ")")
                : ("مواقيت الصلاة (" + localizedLocationName() + ")");
        drawFittedTextAligned(c, prayerHeader, englishMode ? (px + pw * .05f) : (px + pw * .95f), py + ph * .14f, pw * .88f);
        p.setFakeBoldText(false);

        String[] names = activePrayerNames();
        int next = getNextPrayerIndex();
        float rowStart = py + ph * .27f, rowH = ph * .118f;
        for (int i = 0; i < 6; i++) {
            float ry = rowStart + i * rowH;
            if (i == next) {
                p.setColor(0x28FFA726);
                c.drawRoundRect(new RectF(px + pw * .03f, ry - rowH * .70f, px + pw * .97f, ry + rowH * .26f), 10f, 10f, p);
                stroke.setStyle(Paint.Style.STROKE);
                stroke.setStrokeWidth(1.2f);
                stroke.setColor(0xBBFFD768);
                c.drawRoundRect(new RectF(px + pw * .03f, ry - rowH * .70f, px + pw * .97f, ry + rowH * .26f), 10f, 10f, stroke);
            }
            ts(ph * .095f);
            String icon = (i < PRAYER_ICONS.length) ? PRAYER_ICONS[i] : "•";
            p.setColor(i == next ? goldColor() : prayerPrimaryColor());
            if (englishMode) {
                p.setTextAlign(Paint.Align.LEFT);
                c.drawText(icon + " " + names[i], px + pw * .06f, ry, p);
                p.setTextAlign(Paint.Align.RIGHT);
                c.drawText(format12Hour(prayerTimes[i]), px + pw * .94f, ry, p);
            } else {
                p.setTextAlign(Paint.Align.RIGHT);
                c.drawText(icon + " " + names[i], px + pw * .94f, ry, p);
                p.setTextAlign(Paint.Align.LEFT);
                c.drawText(format12Hour(prayerTimes[i]), px + pw * .06f, ry, p);
            }
        }

        // Next Prayer Card
        float ny = h * .755f;
        float nh = h * .065f;
        roundCard(c, new RectF(px, ny, px + pw, ny + nh));
        p.setTextAlign(Paint.Align.CENTER);
        ts(nh * .36f);
        p.setColor(goldColor());
        p.setFakeBoldText(true);
        drawFittedText(c, nextPrayerText(), px + pw * .5f, ny + nh * 0.65f, pw * .90f);
        p.setFakeBoldText(false);

        // Portrait Bottom Athkar Bar
        drawPortraitBottomAthkar(c, w, h);
    }

    private void drawBackground(Canvas c, float w, float h) {
        p.setStyle(Paint.Style.FILL);
        p.setShader(getBackgroundGradient(w, h));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);

        boolean wideLandscape = (w / h) >= 1.35f;
        boolean splitScreen = !wideLandscape && (w / h) >= 0.85f;

        float compassCx;
        float compassCy;
        float ambR;

        if (wideLandscape) {
            compassCx = panelSwapped ? (w * .565f) : (w * .435f);
            compassCy = h * .525f;
            ambR = h * .320f;
        } else if (splitScreen) {
            float leftPaneX = panelSwapped ? (w * .515f) : (w * .030f);
            float paneW = w * .455f;
            compassCx = leftPaneX + paneW * 0.5f;
            compassCy = h * 0.315f;
            ambR = Math.min(paneW * 0.44f, h * 0.170f) * 1.12f;
        } else {
            compassCx = w * .50f;
            compassCy = h * .215f;
            ambR = h * .160f;
        }

        // Ambient radial circle directly centered under the compass!
        p.setStyle(Paint.Style.FILL);
        p.setColor(isNightTheme() ? 0x1600F5C8 : 0x1E00D7C2);
        c.drawCircle(compassCx, compassCy, ambR, p);

        if (wideLandscape) {
            float secondCx = panelSwapped ? (w * .18f) : (w * .84f);
            p.setColor(isNightTheme() ? 0x103E8CFF : 0x1837678E);
            c.drawCircle(secondCx, h * .15f, h * .30f, p);
        }

        // Draw Islamic Arabesque Geometry centered at the EXACT compass location!
        drawArabesqueGeometry(c, w, h, compassCx, compassCy, wideLandscape, splitScreen);
    }

    private void drawArabesqueGeometry(Canvas c, float w, float h, float cx, float cy, boolean wide, boolean split) {
        float maxR;
        if (wide) {
            maxR = Math.min(w, h) * 0.32f;
        } else if (split) {
            maxR = Math.min(w * 0.455f * 0.44f, h * 0.170f) * 1.05f;
        } else {
            maxR = Math.min(w, h) * 0.20f;
        }

        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(1.2f);
        int patternColor = getArabesqueColor();
        stroke.setColor(patternColor);

        // Concentric geometric circles exactly around the compass
        c.drawCircle(cx, cy, maxR * 0.88f, stroke);
        c.drawCircle(cx, cy, maxR * 1.10f, stroke);

        // 8-Pointed Star Lattice around compass
        float starR = maxR * 1.18f;
        float innerR = starR * 0.7071f;
        Path starPath = new Path();
        for (int i = 0; i < 16; i++) {
            double angle = Math.toRadians(i * 22.5);
            float r = (i % 2 == 0) ? starR : innerR;
            float px = cx + (float) (Math.cos(angle) * r);
            float py = cy + (float) (Math.sin(angle) * r);
            if (i == 0) starPath.moveTo(px, py);
            else starPath.lineTo(px, py);
        }
        starPath.close();
        c.drawPath(starPath, stroke);

        // Corner Arabesque motifs
        drawCornerArabesque(c, 0, 0, 1, 1, patternColor);
        drawCornerArabesque(c, w, 0, -1, 1, patternColor);
        drawCornerArabesque(c, 0, h, 1, -1, patternColor);
        drawCornerArabesque(c, w, h, -1, -1, patternColor);
    }

    private void drawCornerArabesque(Canvas c, float x, float y, float sx, float sy, int color) {
        stroke.setColor(color);
        stroke.setStrokeWidth(1.2f);
        Path path = new Path();
        path.moveTo(x + sx * 10f, y + sy * 46f);
        path.lineTo(x + sx * 10f, y + sy * 10f);
        path.lineTo(x + sx * 46f, y + sy * 10f);
        c.drawPath(path, stroke);

        Path path2 = new Path();
        path2.moveTo(x + sx * 18f, y + sy * 36f);
        path2.lineTo(x + sx * 18f, y + sy * 18f);
        path2.lineTo(x + sx * 36f, y + sy * 18f);
        c.drawPath(path2, stroke);

        c.drawLine(x + sx * 8f, y + sy * 30f, x + sx * 30f, y + sy * 8f, stroke);
    }

    private void drawTopBar(Canvas c, float w, float h) {
        float minDim = Math.min(w, h);
        boolean landscape = (w / h) >= 1.55f;
        float logoR = minDim * (landscape ? .038f : .042f);
        float logoCx = landscape ? (w * .022f + logoR) : (logoR + w * .028f);
        float logoCy = h * (landscape ? .066f : .048f);
        drawLogo(c, logoCx, logoCy, logoR);

        float textX = logoCx + logoR + (landscape ? w * .012f : w * .016f);
        p.setTextAlign(Paint.Align.LEFT);
        p.setColor(primaryTextColor());
        ts(minDim * (landscape ? .036f : .030f));
        p.setFakeBoldText(true);
        c.drawText(englishMode ? "Qibla" : "قبلة", textX, logoCy + logoR * .05f, p);
        p.setFakeBoldText(false);
        ts(minDim * (landscape ? .018f : .016f));
        p.setColor(goldColor());
        c.drawText(BuildConfig.VERSION_NAME, textX + minDim * .006f, logoCy + logoR * .72f, p);

        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(primaryTextColor());
        ts(minDim * (landscape ? .028f : .020f));
        String qibla;
        if (englishMode) {
            qibla = hasLocation
                    ? String.format(Locale.US, "Qibla Bearing: %.0f° from North", qiblaBearing)
                    : "Qibla Bearing: Waiting for GPS";
        } else {
            qibla = hasLocation
                    ? String.format(Locale.US, "اتجاه القبلة: %.0f° من الشمال", qiblaBearing)
                    : "اتجاه القبلة: بانتظار الموقع";
        }
        float centerMaxW = landscape ? (w * .44f) : (w * .42f);
        float qiblaY = landscape ? (h * .062f) : (h * .042f);
        drawFittedText(c, qibla, w * .50f, qiblaY, centerMaxW);

        ts(minDim * (landscape ? .020f : .015f));
        p.setColor(secondaryTextColor());
        String deviation;
        if (hasLocation && hasHeading) {
            float rel = normalize(qiblaBearing - heading);
            float signed = rel > 180 ? rel - 360 : rel;
            if (englishMode) {
                deviation = String.format(Locale.US, "Deviation from Heading: %.0f° %s",
                        Math.abs(signed), signed >= 0 ? "Right" : "Left");
            } else {
                deviation = String.format(Locale.US, "الانحراف عن اتجاه المركبة: %.0f° %s",
                        Math.abs(signed), signed >= 0 ? "يمينًا" : "يسارًا");
            }
        } else {
            if (englishMode) {
                deviation = hasLocation ? "Waiting for compass heading" : "Waiting for GPS location";
            } else {
                deviation = hasLocation ? "بانتظار اتجاه السيارة" : "بانتظار تحديد الموقع";
            }
        }
        float devY = landscape ? (h * .102f) : (h * .068f);
        drawFittedText(c, deviation, w * .50f, devY, centerMaxW);

        Date now = new Date();
        float rightX = landscape ? (w * .978f) : (w * .965f);
        p.setTextAlign(Paint.Align.RIGHT);
        p.setColor(primaryTextColor());
        ts(minDim * (landscape ? .040f : .032f));
        p.setFakeBoldText(true);
        c.drawText(new SimpleDateFormat("hh:mm a", Locale.US).format(now), rightX, h * (landscape ? .068f : .046f), p);
        p.setFakeBoldText(false);
        ts(minDim * (landscape ? .018f : .015f));
        p.setColor(secondaryTextColor());
        float rightMaxW = landscape ? (w * .24f) : (w * .38f);
        drawFittedTextAligned(c, new SimpleDateFormat("EEEE d MMMM", englishMode ? Locale.US : ar).format(now), rightX, h * (landscape ? .096f : .068f), rightMaxW);
        p.setColor(mutedTextColor());
        ts(minDim * .015f);
        String activeHijri = (englishMode && !hijriDateEn.isEmpty()) ? hijriDateEn : hijriDate;
        String emptyHijri = englishMode ? "Hijri date after prayer sync" : "التاريخ الهجري بعد تحديث الصلاة";
        drawFittedTextAligned(c, activeHijri.isEmpty() ? emptyHijri : activeHijri, w * .955f, h * .118f, rightMaxW);
    }

    private void drawLogo(Canvas c, float cx, float cy, float r) {
        boolean night = isNightTheme();
        p.setStyle(Paint.Style.FILL);
        p.setColor(night ? 0xFF0F1820 : 0xF6101B22);
        c.drawCircle(cx, cy, r, p);

        RectF badge = new RectF(cx - r * 0.82f, cy - r * 0.82f, cx + r * 0.82f, cy + r * 0.82f);
        if (logoBitmap != null && !logoBitmap.isRecycled()) {
            c.drawBitmap(logoBitmap, null, badge, bitmapPaint);
        } else {
            drawVectorLogo(c, cx, cy, r * 0.82f, night);
        }

        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(1.2f);
        stroke.setColor(night ? 0xBBFFD875 : 0xBBE5B23A);
        c.drawCircle(cx, cy, r, stroke);
    }

    private void drawVectorLogo(Canvas c, float cx, float cy, float r, boolean night) {
        float dialCy = cy + r * .06f;
        float dialR = r * .72f;

        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(
                cx, dialCy - dialR, cx, dialCy + dialR,
                night ? 0xFF0D6B53 : 0xFF0F7A5F,
                night ? 0xFF05382B : 0xFF074737,
                Shader.TileMode.CLAMP));
        c.drawCircle(cx, dialCy, dialR, p);
        p.setShader(null);

        stroke.setStrokeWidth(r * .115f);
        stroke.setColor(0xFFE8BC52);
        c.drawCircle(cx, dialCy, dialR, stroke);
        stroke.setStrokeWidth(r * .032f);
        stroke.setColor(0xFFFFF0B8);
        c.drawCircle(cx, dialCy, dialR * 1.03f, stroke);

        stroke.setStrokeWidth(r * .042f);
        stroke.setColor(0xEEFFF5D6);
        for (int deg = 0; deg < 360; deg += 30) {
            if (deg == 0) continue;
            double rad = Math.toRadians(deg - 90);
            float r1 = (deg % 90 == 0) ? dialR * .72f : dialR * .78f;
            float r2 = dialR * .89f;
            c.drawLine(
                    cx + (float) Math.cos(rad) * r1,
                    dialCy + (float) Math.sin(rad) * r1,
                    cx + (float) Math.cos(rad) * r2,
                    dialCy + (float) Math.sin(rad) * r2,
                    stroke);
        }

        Path northNeedle = new Path();
        northNeedle.moveTo(cx, dialCy - dialR * .78f);
        northNeedle.lineTo(cx - dialR * .20f, dialCy);
        northNeedle.lineTo(cx + dialR * .20f, dialCy);
        northNeedle.close();
        p.setColor(0xFF00F5C8);
        c.drawPath(northNeedle, p);

        Path southNeedle = new Path();
        southNeedle.moveTo(cx, dialCy + dialR * .78f);
        southNeedle.lineTo(cx - dialR * .20f, dialCy);
        southNeedle.lineTo(cx + dialR * .20f, dialCy);
        southNeedle.close();
        p.setColor(0xFFE6FAF6);
        c.drawPath(southNeedle, p);

        float kw = dialR * .44f;
        float kh = dialR * .34f;
        float topY = dialCy - kh * .95f;
        float midY = dialCy - kh * .38f;
        float botY = dialCy + kh * .78f;

        Path topFace = new Path();
        topFace.moveTo(cx, topY);
        topFace.lineTo(cx + kw, midY);
        topFace.lineTo(cx, midY + (midY - topY));
        topFace.lineTo(cx - kw, midY);
        topFace.close();
        p.setColor(0xFF283640);
        c.drawPath(topFace, p);

        float centerMidY = midY + (midY - topY);

        Path leftFace = new Path();
        leftFace.moveTo(cx - kw, midY);
        leftFace.lineTo(cx, centerMidY);
        leftFace.lineTo(cx, botY);
        leftFace.lineTo(cx - kw, botY - (centerMidY - midY));
        leftFace.close();
        p.setColor(0xFF101820);
        c.drawPath(leftFace, p);

        Path rightFace = new Path();
        rightFace.moveTo(cx, centerMidY);
        rightFace.lineTo(cx + kw, midY);
        rightFace.lineTo(cx + kw, botY - (centerMidY - midY));
        rightFace.lineTo(cx, botY);
        rightFace.close();
        p.setColor(0xFF18222A);
        c.drawPath(rightFace, p);

        float bandTop = .24f, bandBot = .38f;
        float sideH = botY - centerMidY;
        Path kiswah = new Path();
        kiswah.moveTo(cx - kw, midY + sideH * bandTop);
        kiswah.lineTo(cx, centerMidY + sideH * bandTop);
        kiswah.lineTo(cx + kw, midY + sideH * bandTop);
        kiswah.lineTo(cx + kw, midY + sideH * bandBot);
        kiswah.lineTo(cx, centerMidY + sideH * bandBot);
        kiswah.lineTo(cx - kw, midY + sideH * bandBot);
        kiswah.close();
        p.setColor(0xFFFFD56B);
        c.drawPath(kiswah, p);

        Path door = new Path();
        float dx1 = cx + kw * .22f;
        float dx2 = cx + kw * .52f;
        float dyShift1 = (centerMidY - midY) * .22f;
        float dyShift2 = (centerMidY - midY) * .52f;
        door.moveTo(dx1, centerMidY - dyShift1 + sideH * .50f);
        door.lineTo(dx2, centerMidY - dyShift2 + sideH * .50f);
        door.lineTo(dx2, centerMidY - dyShift2 + sideH * .88f);
        door.lineTo(dx1, centerMidY - dyShift1 + sideH * .88f);
        door.close();
        p.setColor(0xFFE8BC52);
        c.drawPath(door, p);

        stroke.setStrokeWidth(r * .032f);
        stroke.setColor(0xFFFFDF85);
        c.drawPath(topFace, stroke);
        c.drawPath(leftFace, stroke);
        c.drawPath(rightFace, stroke);
    }

    private void drawLeftCards(Canvas c, float w, float h, boolean onRight) {
        float margin = w * .022f;
        float cardW = w * .118f;
        float x = onRight ? (w - margin - cardW) : margin;

        float yTop = h * .145f;
        float gap = h * .010f;
        float cardH = h * .112f;

        drawInfoCard(c, x, yTop + 0 * (cardH + gap), cardW, cardH,
                englishMode ? "Qibla Angle" : "زاوية القبلة",
                hasLocation ? String.format(Locale.US, "%.0f°", qiblaBearing) : "—",
                "◈", prayerAccentColor());

        drawInfoCard(c, x, yTop + 1 * (cardH + gap), cardW, cardH,
                englishMode ? "Vehicle Heading" : "اتجاه المركبة",
                hasHeading ? String.format(Locale.US, "%.0f°", heading) : "—",
                "↗", hasHeading ? prayerAccentColor() : 0xFFE5B64A);

        boolean showSpeed = speedKmh > 1f;
        drawInfoCard(c, x, yTop + 2 * (cardH + gap), cardW, cardH,
                showSpeed ? (englishMode ? "Speed" : "السرعة") : (englishMode ? "GPS Accuracy" : "دقة GPS"),
                showSpeed ? String.format(Locale.US, "%.0f km/h", speedKmh) : formatAccuracy(),
                showSpeed ? "🚗" : "◎", prayerAccentColor());

        drawInfoCard(c, x, yTop + 3 * (cardH + gap), cardW, cardH,
                englishMode ? "Heading Source" : "مصدر الاتجاه",
                localizedHeadingSource(),
                "🧭", goldColor());
    }

    private void drawInfoCard(Canvas c, float x, float y, float cw, float ch, String label, String value, String icon, int accent) {
        drawInfoCard(c, x, y, cw, ch, label, value, icon, accent, null);
    }

    private void drawInfoCard(Canvas c, float x, float y, float cw, float ch, String label, String value, String icon, int accent, String sub) {
        roundCard(c, new RectF(x, y, x + cw, y + ch), 14f, 1.0f);

        float cx = x + cw * 0.5f;

        p.setTextAlign(Paint.Align.CENTER);
        ts(ch * .20f);
        p.setColor(accent);
        String header = (icon != null && !icon.isEmpty()) ? (icon + " " + label) : label;
        drawFittedText(c, header, cx, y + ch * 0.38f, cw * 0.90f);

        p.setColor(primaryTextColor());
        ts(ch * .32f);
        p.setFakeBoldText(true);
        drawFittedText(c, value, cx, y + ch * 0.78f, cw * 0.90f);
        p.setFakeBoldText(false);
    }

    private void roundCard(Canvas c, RectF r) {
        roundCard(c, r, 16f, 1.2f);
    }

    private void roundCard(Canvas c, RectF r, float radius, float strokeW) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(cardBgFill());
        c.drawRoundRect(r, radius, radius, p);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(strokeW);
        stroke.setColor(cardStrokeColor());
        c.drawRoundRect(r, radius, radius, stroke);
    }

    private void drawCompass(Canvas c, float cx, float cy, float r) {
        boolean aligned = isQiblaAligned();

        p.setStyle(Paint.Style.FILL);
        if (aligned) {
            p.setColor(0x2E00F5A0);
            c.drawCircle(cx, cy, r * 1.20f, p);
            p.setColor(0x3D00F5A0);
            c.drawCircle(cx, cy, r * 1.12f, p);
        } else {
            p.setColor(isNightTheme() ? 0x1600E8D0 : 0x16006474);
            c.drawCircle(cx, cy, r * 1.16f, p);
        }

        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(r * .035f);
        stroke.setColor(aligned ? 0xFFFFD768 : (isNightTheme() ? 0xFF8A6726 : 0xFFB88E3E));
        c.drawCircle(cx, cy, r * 1.14f, stroke);

        stroke.setStrokeWidth(r * .012f);
        stroke.setColor(aligned ? 0xFF00F5C8 : (isNightTheme() ? 0x88FFDF85 : 0x884D7783));
        c.drawCircle(cx, cy, r * 1.08f, stroke);

        stroke.setStrokeWidth(r * .008f);
        stroke.setColor(isNightTheme() ? 0x44FFD768 : 0x445D7E86);
        c.drawCircle(cx, cy, r * 1.12f, stroke);

        stroke.setStrokeWidth(r * .012f);
        stroke.setColor(aligned ? 0xFF00F5C8 : goldColor());
        if (aligned) {
            p.setShadowLayer(24f, 0, 0, 0xAA00F5C8);
        }
        c.drawCircle(cx, cy, r, stroke);
        p.clearShadowLayer();

        stroke.setStrokeWidth(r * .005f);
        stroke.setColor(isNightTheme() ? 0x4430BFAF : 0x33006474);
        c.drawCircle(cx, cy, r * .83f, stroke);

        for (int deg = 0; deg < 360; deg += 5) {
            double a = Math.toRadians(deg - 90);
            boolean major = deg % 30 == 0;
            boolean medium = deg % 15 == 0;
            float len = major ? r * .085f : (medium ? r * .055f : r * .028f);
            stroke.setStrokeWidth(major ? r * .012f : (medium ? r * .007f : r * .0035f));
            stroke.setColor(major ? (aligned ? 0xFFFFD768 : 0xEEFFF5D6) : (medium ? 0x88FFD768 : 0x4080B0B8));
            c.drawLine(cx + (float) Math.cos(a) * (r - len), cy + (float) Math.sin(a) * (r - len),
                    cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r, stroke);
        }

        float rot = hasHeading ? -heading : 0f;
        drawCardinal(c, "N", cx, cy, r * .72f, rot, aligned ? 0xFFFFD768 : primaryTextColor());
        drawCardinal(c, "E", cx, cy, r * .72f, 90 + rot, primaryTextColor());
        drawCardinal(c, "S", cx, cy, r * .72f, 180 + rot, primaryTextColor());
        drawCardinal(c, "W", cx, cy, r * .72f, 270 + rot, primaryTextColor());

        if (hasLocation) {
            float arrow = hasHeading ? normalize(qiblaBearing - heading) : qiblaBearing;
            float angle = arrow > 180 ? arrow - 360 : arrow;
            c.save();
            c.rotate(angle, cx, cy);
            stroke.setStrokeWidth(r * .035f);
            stroke.setColor(aligned ? 0xFF00F5C8 : 0xFFE53935);
            c.drawLine(cx, cy + r * .02f, cx, cy - r * .49f, stroke);
            Path head = new Path();
            head.moveTo(cx, cy - r * .73f);
            head.lineTo(cx - r * .115f, cy - r * .47f);
            head.lineTo(cx + r * .115f, cy - r * .47f);
            head.close();
            p.setStyle(Paint.Style.FILL);
            p.setColor(aligned ? 0xFF00F5C8 : 0xFFE53935);
            p.setShadowLayer(22f, 0, 0, aligned ? 0xDD00F5C8 : 0x99E53935);
            c.drawPath(head, p);
            p.clearShadowLayer();
            c.restore();
        }

        drawKaaba(c, cx, cy, r);
        drawCompassBottomBadge(c, cx, cy, r, aligned);
    }

    private void drawCompassBottomBadge(Canvas c, float cx, float cy, float r, boolean aligned) {
        // Raised higher up inside the compass dial below Kaaba (no box/rectangle)
        float textY = cy + r * 0.285f;
        float maxTextW = r * 0.82f;

        p.setTextAlign(Paint.Align.CENTER);
        ts(r * 0.070f);
        p.setFakeBoldText(true);

        String makkahDist = (distanceKm > 0)
                ? String.format(Locale.US, englishMode ? "%.0f km to Makkah" : "%.0f كم إلى مكة", distanceKm)
                : (englishMode ? "Makkah: 21.42° N" : "مكة المكرمة");

        String badgeText = aligned
                ? (englishMode ? "🕋 Aligned with Qibla" : "🕋 باتجاه القبلة المشرفة")
                : ("🕋 " + makkahDist);

        if (aligned) {
            int glowColor = isNightTheme() ? 0xFF00F5C8 : 0xFF00897B;
            p.setColor(glowColor);
            p.setShadowLayer(14f, 0, 0, isNightTheme() ? 0xDD00F5C8 : 0x88004D40);
        } else {
            p.setColor(goldColor());
            p.setShadowLayer(8f, 0, 0, isNightTheme() ? 0xAA000000 : 0x44000000);
        }

        drawFittedText(c, badgeText, cx, textY, maxTextW);
        p.clearShadowLayer();
        p.setFakeBoldText(false);
    }

    private void drawKaaba(Canvas c, float cx, float cy, float r) {
        float kw = r * .19f;
        float kh = r * .15f;
        float topY = cy - kh * 1.0f;
        float midY = cy - kh * 0.35f;
        float botY = cy + kh * 0.85f;

        Path topFace = new Path();
        topFace.moveTo(cx, topY);
        topFace.lineTo(cx + kw, midY);
        topFace.lineTo(cx, midY + (midY - topY));
        topFace.lineTo(cx - kw, midY);
        topFace.close();
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF283640);
        c.drawPath(topFace, p);

        float centerMidY = midY + (midY - topY);

        Path leftFace = new Path();
        leftFace.moveTo(cx - kw, midY);
        leftFace.lineTo(cx, centerMidY);
        leftFace.lineTo(cx, botY);
        leftFace.lineTo(cx - kw, botY - (centerMidY - midY));
        leftFace.close();
        p.setColor(0xFF101820);
        c.drawPath(leftFace, p);

        Path rightFace = new Path();
        rightFace.moveTo(cx, centerMidY);
        rightFace.lineTo(cx + kw, midY);
        rightFace.lineTo(cx + kw, botY - (centerMidY - midY));
        rightFace.lineTo(cx, botY);
        rightFace.close();
        p.setColor(0xFF18222A);
        c.drawPath(rightFace, p);

        float sideH = botY - centerMidY;
        Path kiswah = new Path();
        kiswah.moveTo(cx - kw, midY + sideH * 0.22f);
        kiswah.lineTo(cx, centerMidY + sideH * 0.22f);
        kiswah.lineTo(cx + kw, midY + sideH * 0.22f);
        kiswah.lineTo(cx + kw, midY + sideH * 0.38f);
        kiswah.lineTo(cx, centerMidY + sideH * 0.38f);
        kiswah.lineTo(cx - kw, midY + sideH * 0.38f);
        kiswah.close();
        p.setColor(0xFFFFD56B);
        c.drawPath(kiswah, p);

        Path door = new Path();
        float dx1 = cx + kw * .22f;
        float dx2 = cx + kw * .55f;
        float dyShift1 = (centerMidY - midY) * .22f;
        float dyShift2 = (centerMidY - midY) * .55f;
        door.moveTo(dx1, centerMidY - dyShift1 + sideH * .48f);
        door.lineTo(dx2, centerMidY - dyShift2 + sideH * .48f);
        door.lineTo(dx2, centerMidY - dyShift2 + sideH * .88f);
        door.lineTo(dx1, centerMidY - dyShift1 + sideH * .88f);
        door.close();
        p.setColor(0xFFE8BC52);
        c.drawPath(door, p);

        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Math.max(1.5f, r * .018f));
        stroke.setColor(0xFFFFDF85);
        c.drawPath(topFace, stroke);
        c.drawPath(leftFace, stroke);
        c.drawPath(rightFace, stroke);
    }

    private void drawCardinal(Canvas c, String t, float cx, float cy, float rr, float deg, int color) {
        double a = Math.toRadians(deg - 90);
        float x = cx + (float) Math.cos(a) * rr, y = cy + (float) Math.sin(a) * rr;
        p.setTextAlign(Paint.Align.CENTER);
        ts(rr * .105f);
        p.setColor(color);
        p.setFakeBoldText(true);
        Paint.FontMetrics fm = p.getFontMetrics();
        c.drawText(t, x, y - (fm.ascent + fm.descent) / 2, p);
        p.setFakeBoldText(false);
    }

    private void drawPrayerPanel(Canvas c, float w, float h, boolean onLeft) {
        float margin = w * .022f;
        float cw = w * .315f;
        float x = onLeft ? margin : (w - margin - cw);

        float yTop = h * .145f;
        float gap = h * .014f;

        float ch1 = h * .415f;
        float ch2 = h * .140f; // Next Prayer Card
        float ch3 = h * .175f; // Location & Status Card

        float y1 = yTop;
        float y2 = y1 + ch1 + gap;
        float y3 = y2 + ch2 + gap;

        // Card 1: مواقيت الصلاة (Prayer Times Card)
        roundCard(c, new RectF(x, y1, x + cw, y1 + ch1), 16f, 1.2f);
        boolean rtl = !englishMode;
        float padX = cw * 0.08f;

        // Header Title
        p.setTextAlign(rtl ? Paint.Align.RIGHT : Paint.Align.LEFT);
        p.setColor(goldColor());
        ts(ch1 * .078f);
        p.setFakeBoldText(true);
        float headerY = y1 + ch1 * 0.12f;
        c.drawText(englishMode ? "🕌 Prayer Times" : "🕌 مواقيت الصلاة",
                rtl ? (x + cw - padX) : (x + padX), headerY, p);
        p.setFakeBoldText(false);

        // Hijri date subtitle in header
        String hijri = englishMode ? (hijriDateEn.isEmpty() ? hijriDate : hijriDateEn) : hijriDate;
        if (hijri != null && !hijri.isEmpty()) {
            p.setTextAlign(rtl ? Paint.Align.LEFT : Paint.Align.RIGHT);
            p.setColor(prayerSecondaryColor());
            ts(ch1 * .058f);
            drawFittedTextAligned(c, hijri, rtl ? (x + padX) : (x + cw - padX), headerY, cw * 0.44f);
        }

        String[] names = activePrayerNames();
        int next = getNextPrayerIndex();
        float rowStart = y1 + ch1 * 0.235f;
        float rowH = ch1 * 0.120f;

        for (int i = 0; i < 6; i++) {
            float ry = rowStart + i * rowH;
            if (i == next) {
                p.setColor(0x28FFA726);
                c.drawRoundRect(new RectF(x + cw * .03f, ry - rowH * .70f, x + cw * .97f, ry + rowH * .26f), 10f, 10f, p);
                stroke.setStyle(Paint.Style.STROKE);
                stroke.setStrokeWidth(1.2f);
                stroke.setColor(0xBBFFD768);
                c.drawRoundRect(new RectF(x + cw * .03f, ry - rowH * .70f, x + cw * .97f, ry + rowH * .26f), 10f, 10f, stroke);
            }

            ts(ch1 * .074f);
            String icon = (i < PRAYER_ICONS.length) ? PRAYER_ICONS[i] : "•";
            p.setColor(i == next ? goldColor() : prayerPrimaryColor());
            p.setFakeBoldText(i == next);

            if (rtl) {
                p.setTextAlign(Paint.Align.RIGHT);
                c.drawText(icon + "  " + names[i], x + cw - padX, ry, p);
                p.setTextAlign(Paint.Align.LEFT);
                c.drawText(format12Hour(prayerTimes[i]), x + padX, ry, p);
            } else {
                p.setTextAlign(Paint.Align.LEFT);
                c.drawText(icon + "  " + names[i], x + padX, ry, p);
                p.setTextAlign(Paint.Align.RIGHT);
                c.drawText(format12Hour(prayerTimes[i]), x + cw - padX, ry, p);
            }
            p.setFakeBoldText(false);
        }

        // Card 2: الصلاة القادمة (Next Prayer Card - ENLARGED!)
        roundCard(c, new RectF(x, y2, x + cw, y2 + ch2), 16f, 1.4f);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(1.5f);
        stroke.setColor(0xAAFFD768);
        c.drawRoundRect(new RectF(x, y2, x + cw, y2 + ch2), 16f, 16f, stroke);

        p.setTextAlign(Paint.Align.CENTER);
        ts(ch2 * 0.22f);
        p.setColor(goldColor());
        p.setFakeBoldText(true);
        drawFittedText(c, formatNextPrayerTitle(), x + cw * 0.5f, y2 + ch2 * 0.32f, cw * 0.88f);

        // Main prominent countdown line in large bold font
        ts(ch2 * 0.38f);
        p.setColor(primaryTextColor());
        drawFittedText(c, formatNextPrayerCountdown(), x + cw * 0.5f, y2 + ch2 * 0.76f, cw * 0.92f);
        p.setFakeBoldText(false);

        // Card 3: الموقع وحالة النظام (Location & Status Card)
        roundCard(c, new RectF(x, y3, x + cw, y3 + ch3), 16f, 1.2f);
        p.setTextAlign(rtl ? Paint.Align.RIGHT : Paint.Align.LEFT);
        float alignX = rtl ? (x + cw - padX) : (x + padX);

        ts(ch3 * 0.18f);
        p.setColor(prayerAccentColor());
        String placeLine = "📍 " + localizedLocationName();
        drawFittedTextAligned(c, placeLine, alignX, y3 + ch3 * 0.30f, cw * 0.84f);

        p.setColor(prayerSecondaryColor());
        ts(ch3 * 0.14f);
        String coords = hasLocation
                ? String.format(Locale.US, "🛰️ %.4f° N, %.4f° E  (±%s)", latitude, longitude, formatAccuracy())
                : (englishMode ? "🛰️ Waiting for GPS lock" : "🛰️ بانتظار إشارة GPS");
        drawFittedTextAligned(c, coords, alignX, y3 + ch3 * 0.58f, cw * 0.84f);

        p.setColor(isNightTheme() ? 0xFF7ED9EA : 0xFF006B70);
        ts(ch3 * 0.13f);
        String calMethod = englishMode ? "⚖️ Umm al-Qura Calendar • Live Sync" : "⚖️ تقويم أم القرى الرسمي • تحديث مباشر";
        drawFittedTextAligned(c, calMethod, alignX, y3 + ch3 * 0.84f, cw * 0.84f);
    }

    private void drawBottomAthkarBar(Canvas c, float w, float h) {
        float barH = h * 0.048f;
        float barY = h * 0.940f;
        float startX = panelSwapped ? (w * 0.035f) : (w * 0.350f);
        float endX = panelSwapped ? (w * 0.635f) : (w * 0.965f);
        RectF barRect = new RectF(startX, barY, endX, barY + barH);

        roundCard(c, barRect, 14f, 1.2f);

        p.setTextAlign(Paint.Align.CENTER);
        ts(barH * 0.56f);
        p.setFakeBoldText(true);
        boolean playing = SoundManager.isPlaying();
        p.setColor(playing ? 0xFF00F5C8 : goldColor());
        String thikr = playing
                ? (englishMode ? "🔊 Playing Takbeerat... (Tap to stop)" : "🔊 جاري تشغيل تكبيرات الأذان... (اضغط للإيقاف)")
                : currentThikrText();
        drawFittedText(c, thikr, (startX + endX) * 0.5f, barY + barH * 0.68f, (endX - startX) * 0.94f);
        p.setFakeBoldText(false);
    }

    private void drawPortraitBottomAthkar(Canvas c, float w, float h) {
        float pad = w * .025f;
        float barH = h * 0.045f;
        float barY = h * 0.832f;
        RectF barRect = new RectF(pad, barY, w - pad, barY + barH);
        roundCard(c, barRect, 14f, 1.2f);
        p.setTextAlign(Paint.Align.CENTER);
        ts(barH * 0.52f);
        p.setFakeBoldText(true);
        boolean playing = SoundManager.isPlaying();
        p.setColor(playing ? 0xFF00F5C8 : goldColor());
        String thikr = playing
                ? (englishMode ? "🔊 Playing Takbeerat... (Tap to stop)" : "🔊 جاري تشغيل تكبيرات الأذان... (اضغط للإيقاف)")
                : currentThikrText();
        drawFittedText(c, thikr, w * 0.5f, barY + barH * 0.68f, (w - pad * 2f) * 0.94f);
        p.setFakeBoldText(false);
    }

    private void drawAdhanBanner(Canvas c, float w, float h) {
        if (adhanBannerText == null || System.currentTimeMillis() >= adhanBannerUntil) return;
        float bw = Math.min(w * 0.74f, 750f);
        float bh = h * 0.078f;
        float bx = (w - bw) * 0.5f;
        float by = h * 0.020f;
        RectF r = new RectF(bx, by, bx + bw, by + bh);

        // Rich emerald fill with soft glow
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFA052C22);
        p.setShadowLayer(26f, 0, 4f, 0xDD00F5C8);
        c.drawRoundRect(r, bh * 0.42f, bh * 0.42f, p);
        p.clearShadowLayer();

        // Stroke gold border
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2.0f);
        stroke.setColor(0xFFFFD768);
        c.drawRoundRect(r, bh * 0.42f, bh * 0.42f, stroke);

        p.setTextAlign(Paint.Align.CENTER);
        ts(bh * 0.40f);
        p.setColor(0xFFFFFFFF);
        p.setFakeBoldText(true);
        drawFittedText(c, adhanBannerText, bx + bw * 0.5f, by + bh * 0.64f, bw * 0.90f);
        p.setFakeBoldText(false);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            float y = event.getY();
            float h = getHeight();

            // 1. Tapping on active Adhan banner dismisses it and silences audio
            if (System.currentTimeMillis() < adhanBannerUntil && adhanBannerText != null && y <= h * 0.15f) {
                dismissAdhanBanner();
                SoundManager.stop();
                return true;
            }

            // 2. Tapping bottom Athkar bar stops audio if playing
            float aspect = getWidth() / (float) h;
            boolean wide = aspect >= 1.35f;
            boolean split = !wide && aspect >= 0.85f;
            float athkarMinY = wide ? (h * 0.92f) : (split ? (h * 0.77f) : (h * 0.81f));
            float athkarMaxY = wide ? (h * 0.995f) : (split ? (h * 0.86f) : (h * 0.88f));
            if (y >= athkarMinY && y <= athkarMaxY) {
                if (SoundManager.isPlaying()) {
                    SoundManager.stop();
                    invalidate();
                    return true;
                }
            }
        }
        return super.onTouchEvent(event);
    }

    private int getNextPrayerIndex() {
        if (!hasPrayerTimes) return -1;
        Calendar now = Calendar.getInstance();
        int current = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        for (int i = 0; i < prayerTimes.length; i++) {
            int m = parseMinutes(prayerTimes[i]);
            if (m >= 0 && m > current) return i;
        }
        return 0;
    }

    private String nextPrayerText() {
        int idx = getNextPrayerIndex();
        if (idx < 0) return englishMode ? "Next Prayer: Waiting for sync" : "الصلاة القادمة: بانتظار التحديث";
        int target = parseMinutes(prayerTimes[idx]);
        Calendar now = Calendar.getInstance();
        int currentSec = now.get(Calendar.HOUR_OF_DAY) * 3600 + now.get(Calendar.MINUTE) * 60 + now.get(Calendar.SECOND);
        int targetSec = target * 60;
        int diff = targetSec - currentSec;
        if (diff <= 0) diff += 24 * 3600;
        int dh = diff / 3600;
        int dm = (diff % 3600) / 60;
        int ds = diff % 60;
        String[] names = activePrayerNames();
        if (englishMode) {
            return String.format(Locale.US, "Next: %s in %02d:%02d:%02d", names[idx], dh, dm, ds);
        }
        return String.format(Locale.US, "الصلاة القادمة: %s متبقي %02d:%02d:%02d", names[idx], dh, dm, ds);
    }

    private String formatNextPrayerTitle() {
        int idx = getNextPrayerIndex();
        if (idx < 0) return englishMode ? "NEXT PRAYER" : "الصلاة القادمة";
        String[] names = activePrayerNames();
        return englishMode ? ("🕌 Next Prayer: " + names[idx]) : ("🕌 الصلاة القادمة: " + names[idx]);
    }

    private String formatNextPrayerCountdown() {
        int idx = getNextPrayerIndex();
        if (idx < 0) return englishMode ? "Waiting for sync" : "بانتظار التحديث";
        int target = parseMinutes(prayerTimes[idx]);
        Calendar now = Calendar.getInstance();
        int currentSec = now.get(Calendar.HOUR_OF_DAY) * 3600 + now.get(Calendar.MINUTE) * 60 + now.get(Calendar.SECOND);
        int targetSec = target * 60;
        int diff = targetSec - currentSec;
        if (diff <= 0) diff += 24 * 3600;
        int dh = diff / 3600;
        int dm = (diff % 3600) / 60;
        int ds = diff % 60;
        if (englishMode) {
            return String.format(Locale.US, "%02d:%02d:%02d  Remaining", dh, dm, ds);
        }
        return String.format(Locale.US, "متبقي  \u200E%02d:%02d:%02d", dh, dm, ds);
    }

    private int parseMinutes(String s) {
        try {
            String[] parts = s.split(":");
            return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
        } catch (Exception e) { return -1; }
    }

    private String format12Hour(String value) {
        int minutes = parseMinutes(value);
        if (minutes < 0) return value;
        int hour24 = minutes / 60;
        int minute = minutes % 60;
        String ampm = englishMode ? (hour24 >= 12 ? "PM" : "AM") : (hour24 >= 12 ? "م" : "ص");
        int hour12 = hour24 % 12;
        if (hour12 == 0) hour12 = 12;
        return String.format(Locale.US, "%02d:%02d %s", hour12, minute, ampm);
    }

    private void ts(float size) { p.setTextSize(size * fontScale); }

    private void drawFittedText(Canvas c, String text, float cx, float cy, float maxW) {
        if (text == null || text.isEmpty()) return;
        float originalSize = p.getTextSize();
        float textW = p.measureText(text);
        if (textW > maxW && textW > 0) {
            p.setTextSize(Math.max(originalSize * 0.45f, originalSize * (maxW / textW)));
        }
        c.drawText(text, cx, cy, p);
        p.setTextSize(originalSize);
    }

    private void drawFittedTextAligned(Canvas c, String text, float x, float y, float maxW) {
        if (text == null || text.isEmpty()) return;
        float originalSize = p.getTextSize();
        float textW = p.measureText(text);
        if (textW > maxW && textW > 0) {
            p.setTextSize(Math.max(originalSize * 0.45f, originalSize * (maxW / textW)));
        }
        c.drawText(text, x, y, p);
        p.setTextSize(originalSize);
    }

    private static float signedAngle(float value) {
        value %= 360f;
        if (value > 180f) value -= 360f;
        if (value < -180f) value += 360f;
        return value;
    }

    private static float calculateBearing(double lat, double lon) {
        double phi1 = Math.toRadians(lat), phi2 = Math.toRadians(KAABA_LAT), dl = Math.toRadians(KAABA_LON - lon);
        double y = Math.sin(dl) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2) - Math.sin(phi1) * Math.cos(phi2) * Math.cos(dl);
        return normalize((float) Math.toDegrees(Math.atan2(y, x)));
    }

    private static float normalize(float v) {
        v %= 360f; if (v < 0) v += 360f; return v;
    }
}
