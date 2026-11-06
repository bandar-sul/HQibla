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
    public static final int THEME_OLED_NIGHT = 5; // Pure Black OLED Night (وضع القيادة الليلية الهادئة)

    // Backward compatibility aliases
    public static final int THEME_DAY = THEME_PEARL;
    public static final int THEME_NIGHT = THEME_EMERALD;

    private int themeMode = THEME_AUTO;
    private float fontScale = 0.90f;
    private boolean hudMirrorMode = false;

    public void setHudMirrorMode(boolean enabled) {
        this.hudMirrorMode = enabled;
        invalidate();
    }

    public boolean isHudMirrorMode() {
        return hudMirrorMode;
    }

    public void toggleHudMirrorMode() {
        setHudMirrorMode(!hudMirrorMode);
    }

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

    private WeatherManager.WeatherInfo weatherInfo = null;
    private boolean weatherEffectsEnabled = true;

    public interface OnWeatherClickListener {
        void onWeatherClicked();
    }
    private OnWeatherClickListener onWeatherClickListener;

    public void setOnWeatherClickListener(OnWeatherClickListener listener) {
        this.onWeatherClickListener = listener;
    }

    public interface OnPrayerClickListener {
        void onPrayerClicked();
    }
    private OnPrayerClickListener onPrayerClickListener;

    public void setOnPrayerClickListener(OnPrayerClickListener listener) {
        this.onPrayerClickListener = listener;
    }

    private final RectF weatherCardRect = new RectF();
    private final RectF weatherPrayerRowRect = new RectF();
    private final RectF prayerCardRect = new RectF();

    private static final int RAIN_COUNT = 45;
    private static final int STAR_COUNT = 36;
    private final float[] rainX = new float[RAIN_COUNT];
    private final float[] rainY = new float[RAIN_COUNT];
    private final float[] rainSpeed = new float[RAIN_COUNT];
    private final float[] starX = new float[STAR_COUNT];
    private final float[] starY = new float[STAR_COUNT];
    private final float[] starAlpha = new float[STAR_COUNT];
    private boolean weatherParticlesInit = false;

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private void initWeatherParticles() {
        if (weatherParticlesInit) return;
        java.util.Random rnd = new java.util.Random(1048576L);
        for (int i = 0; i < RAIN_COUNT; i++) {
            rainX[i] = rnd.nextFloat();
            rainY[i] = rnd.nextFloat();
            rainSpeed[i] = 0.55f + rnd.nextFloat() * 0.45f;
        }
        for (int i = 0; i < STAR_COUNT; i++) {
            starX[i] = rnd.nextFloat();
            starY[i] = rnd.nextFloat() * 0.42f; // upper 42% sky
            starAlpha[i] = 0.35f + rnd.nextFloat() * 0.65f;
        }
        weatherParticlesInit = true;
    }

    public void setWeatherEffectsEnabled(boolean enabled) {
        this.weatherEffectsEnabled = enabled;
        invalidate();
    }

    public boolean isWeatherEffectsEnabled() {
        return weatherEffectsEnabled;
    }

    public void setWeatherInfo(WeatherManager.WeatherInfo info) {
        this.weatherInfo = info;
        invalidate();
    }

    public void setEnglishMode(boolean english) {
        englishMode = english;
        invalidate();
    }

    public boolean isEnglishMode() { return englishMode; }

    private String[] activePrayerNames() {
        return englishMode ? PRAYER_NAMES_EN : PRAYER_NAMES_AR;
    }

    public void setThemeMode(int mode) {
        if (mode >= THEME_AUTO && mode <= THEME_OLED_NIGHT) {
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

    private int headingMode = 0;   // 0: Elevated Qibla Badge, 1: Standard Heading Mode
    private int needleStyle = 0;   // 0: Golden Kaaba Minaret, 1: Classic Precision Arrow, 2: Marine Compass Rose
    private int smoothMode = 0;    // 0: Fast Dynamic, 1: Ultra Smooth Damped, 2: Direct Instant
    private float targetHeading = Float.NaN;

    public void setVehicleHeadingMode(int mode) {
        this.headingMode = mode;
        invalidate();
    }

    public int getVehicleHeadingMode() {
        return headingMode;
    }

    public void setNeedleStyle(int style) {
        this.needleStyle = style;
        invalidate();
    }

    public int getNeedleStyle() {
        return needleStyle;
    }

    public void setSmoothMode(int mode) {
        this.smoothMode = mode;
        invalidate();
    }

    public int getSmoothMode() {
        return smoothMode;
    }

    public void setHeading(float value, String source) {
        float rawTarget = normalize(value);
        if (source != null && !source.isEmpty()) {
            headingSource = source;
        }

        if (!hasHeading || Float.isNaN(smoothedHeading)) {
            hasHeading = true;
            this.targetHeading = rawTarget;
            this.smoothedHeading = rawTarget;
            this.heading = rawTarget;
            invalidate();
            return;
        }

        float delta = signedAngle(rawTarget - targetHeading);
        // Hysteresis deadband: ignore electrical micro-tremors and sensor noise < 0.35 degrees
        if (Math.abs(delta) < 0.35f) {
            return;
        }

        this.targetHeading = rawTarget;
        hasHeading = true;

        if (smoothMode == 2) {
            smoothedHeading = rawTarget;
            heading = rawTarget;
            invalidate();
        } else {
            postInvalidateOnAnimation();
        }
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

    public void setSpeed(float speedKmh) {
        this.speedKmh = speedKmh;
        invalidate();
    }

    public void setGpsAccuracy(float accuracy) {
        this.gpsAccuracy = accuracy;
        if (accuracy > 0 && accuracy <= 20) {
            status = "GPS دقيق";
        }
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
            case THEME_OLED_NIGHT: return 0x00000000; // Zero glare for OLED night driving
            case THEME_EMERALD: return 0x16E5B64A; // soft gold
            case THEME_OBSIDIAN: return 0x1638B6FF; // soft celestial blue
            case THEME_AMBER: return 0x16FFA834;    // soft warm amber
            case THEME_PEARL: return 0x16006B78;    // soft petrol teal
            default: return 0x16E5B64A;
        }
    }

    private LinearGradient getBackgroundGradient(float w, float h) {
        switch (getEffectiveTheme()) {
            case THEME_OLED_NIGHT:
                return new LinearGradient(0, 0, w, h,
                        new int[]{0xFF000000, 0xFF000000, 0xFF000000},
                        new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
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
            case THEME_OLED_NIGHT: return 0xF2080808;
            case THEME_EMERALD: return 0xC8041812;
            case THEME_OBSIDIAN: return 0xC8061422;
            case THEME_AMBER: return 0xC81E1006;
            case THEME_PEARL: return 0xEFF8FCFD;
            default: return 0xC8041812;
        }
    }

    private int cardStrokeColor() {
        switch (getEffectiveTheme()) {
            case THEME_OLED_NIGHT: return 0x33FFA834;
            case THEME_EMERALD: return 0x44E5B64A;
            case THEME_OBSIDIAN: return 0x443D8BFF;
            case THEME_AMBER: return 0x44FFA834;
            case THEME_PEARL: return 0x664B7988;
            default: return 0x44E5B64A;
        }
    }

    private int primaryTextColor() {
        if (getEffectiveTheme() == THEME_OLED_NIGHT) return 0xFFEEEEEE;
        return (getEffectiveTheme() == THEME_PEARL) ? 0xFF0C242C : 0xFFF5F8FA;
    }

    private int secondaryTextColor() {
        if (getEffectiveTheme() == THEME_OLED_NIGHT) return 0xFFA0A5A8;
        return (getEffectiveTheme() == THEME_PEARL) ? 0xFF2B4D58 : 0xFFB8C8CE;
    }

    private int mutedTextColor() {
        if (getEffectiveTheme() == THEME_OLED_NIGHT) return 0xFF6C7377;
        return (getEffectiveTheme() == THEME_PEARL) ? 0xFF4A6F7B : 0xFF7D929B;
    }

    private int goldColor() {
        switch (getEffectiveTheme()) {
            case THEME_OLED_NIGHT: return 0xFFFFB74D;
            case THEME_EMERALD: return 0xFFFFD768;
            case THEME_OBSIDIAN: return 0xFFFFD25A;
            case THEME_AMBER: return 0xFFFFBA49;
            case THEME_PEARL: return 0xFF996300;
            default: return 0xFFFFD768;
        }
    }

    private int prayerPrimaryColor() {
        if (getEffectiveTheme() == THEME_OLED_NIGHT) return 0xFFE0E0E0;
        return (getEffectiveTheme() == THEME_PEARL) ? 0xFF0D2832 : 0xFFEBF3F5;
    }

    private int prayerSecondaryColor() {
        if (getEffectiveTheme() == THEME_OLED_NIGHT) return 0xFF8A9499;
        return (getEffectiveTheme() == THEME_PEARL) ? 0xFF355864 : 0xFFB4C5CB;
    }

    private int prayerAccentColor() {
        switch (getEffectiveTheme()) {
            case THEME_OLED_NIGHT: return 0xFFFFB74D;
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

    public void tick() { invalidate(); }

    public boolean isQiblaAligned() {
        if (!hasLocation || !hasHeading) return false;
        float arrow = normalize(qiblaBearing - heading);
        float signed = arrow > 180 ? arrow - 360 : arrow;
        return Math.abs(signed) <= 5.0f;
    }

    public String getVehicleRelativeQiblaText() {
        if (!hasLocation || !hasHeading) return "";
        float arrow = normalize(qiblaBearing - heading);
        float angle = arrow > 180 ? arrow - 360 : arrow;
        float absAngle = Math.abs(angle);
        if (englishMode) {
            if (absAngle <= 8.0f) return "🕋 Qibla Straight Ahead ⬆️";
            if (angle > 8 && angle <= 45) return String.format(Locale.US, "🕋 Qibla Front-Right (%.0f°) ↗️", absAngle);
            if (angle > 45 && angle <= 80) return String.format(Locale.US, "🕋 Qibla to Right (%.0f°) ↗️", absAngle);
            if (angle > 80 && angle <= 105) return "🕋 Qibla Directly on Right ➡️";
            if (angle > 105 && angle <= 150) return String.format(Locale.US, "🕋 Qibla Behind-Right (%.0f°) ↘️", absAngle);
            if (absAngle >= 150) return "🕋 Qibla Directly Behind ⬇️";
            if (angle < -8 && angle >= -45) return String.format(Locale.US, "🕋 Qibla Front-Left (%.0f°) ↖️", absAngle);
            if (angle < -45 && angle >= -80) return String.format(Locale.US, "🕋 Qibla to Left (%.0f°) ↖️", absAngle);
            if (angle < -80 && angle >= -105) return "🕋 Qibla Directly on Left ⬅️";
            return String.format(Locale.US, "🕋 Qibla Behind-Left (%.0f°) ↙️", absAngle);
        } else {
            if (absAngle <= 8.0f) return "🕋 القبلة أمام المركبة مباشرة ⬆️";
            if (angle > 8 && angle <= 45) return String.format(Locale.US, "🕋 القبلة أمامك لليمين (%.0f°) ↗️", absAngle);
            if (angle > 45 && angle <= 80) return String.format(Locale.US, "🕋 القبلة إلى يمين المركبة (%.0f°) ↗️", absAngle);
            if (angle > 80 && angle <= 105) return "🕋 القبلة على يمينك تماماً ➡️";
            if (angle > 105 && angle <= 150) return String.format(Locale.US, "🕋 القبلة خلفك إلى اليمين (%.0f°) ↘️", absAngle);
            if (absAngle >= 150) return "🕋 القبلة خلف المركبة مباشرة ⬇️";
            if (angle < -8 && angle >= -45) return String.format(Locale.US, "🕋 القبلة أمامك لليسار (%.0f°) ↖️", absAngle);
            if (angle < -45 && angle >= -80) return String.format(Locale.US, "🕋 القبلة إلى يسار المركبة (%.0f°) ↖️", absAngle);
            if (angle < -80 && angle >= -105) return "🕋 القبلة على يسارك تماماً ⬅️";
            return String.format(Locale.US, "🕋 القبلة خلفك إلى اليسار (%.0f°) ↙️", absAngle);
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth(), h = getHeight();
        if (hudMirrorMode) {
            c.save();
            c.scale(-1f, 1f, w / 2f, h / 2f);
        }
        drawBackground(c, w, h);
        drawTopBar(c, w, h);

        // Continuous smooth rotation animation glide (60fps/120fps fluid luxury movement)
        if (smoothMode != 2 && !Float.isNaN(targetHeading) && !Float.isNaN(smoothedHeading)) {
            float diff = signedAngle(targetHeading - smoothedHeading);
            if (Math.abs(diff) >= 0.04f) {
                float glideStep = (smoothMode == 1) ? 0.09f : 0.18f;
                smoothedHeading = normalize(smoothedHeading + diff * glideStep);
                heading = smoothedHeading;
                postInvalidateOnAnimation();
            } else {
                smoothedHeading = targetHeading;
                heading = targetHeading;
            }
        }

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

        if (hudMirrorMode) {
            c.restore();
            drawHudExitPill(c, w, h);
        }
    }

    private void drawHudExitPill(Canvas c, float w, float h) {
        float pillW = Math.min(w * 0.55f, dp(460));
        float pillH = dp(34);
        float px = (w - pillW) * 0.5f;
        float py = dp(8);
        RectF pillRect = new RectF(px, py, px + pillW, py + pillH);

        p.setStyle(Paint.Style.FILL);
        p.setColor(0xEE050A0E);
        c.drawRoundRect(pillRect, pillH * 0.5f, pillH * 0.5f, p);

        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(1.2f));
        stroke.setColor(0xFFFFB74D);
        c.drawRoundRect(pillRect, pillH * 0.5f, pillH * 0.5f, stroke);

        p.setTextAlign(Paint.Align.CENTER);
        ts(dp(13.0f));
        p.setColor(0xFFFFD56B);
        p.setFakeBoldText(true);
        String label = englishMode
                ? "🪞 Windshield HUD Mode Active • Tap to Exit"
                : "🪞 وضع الانعكاس على الزجاج HUD نشط • انقر للخروج";
        c.drawText(label, w * 0.5f, py + pillH * 0.68f, p);
        p.setFakeBoldText(false);
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

        // Row 0 - Primary (Right in Arabic): Weather & Rain (Tap to open Weather Dialog)
        String wIcon = (weatherInfo != null && weatherInfo.icon != null && !weatherInfo.icon.isEmpty()) ? weatherInfo.icon : "🌤️";
        String wVal = (weatherInfo != null) ? String.format(Locale.US, "%.0f°C", weatherInfo.tempC) : "--";
        String wDesc = (weatherInfo != null) ? weatherInfo.getLocalizedDesc(englishMode) : (englishMode ? "Tap for Radar" : "انقر للرادار");
        if (weatherInfo != null && weatherInfo.isOffline) {
            wDesc += englishMode ? " (GPS)" : " (أوفلاين)";
        }
        weatherCardRect.set(primaryColX, row0Y, primaryColX + cardW, row0Y + cardH);
        drawInfoCard(c, primaryColX, row0Y, cardW, cardH,
                englishMode ? "Weather & Rain 🌧️" : "الطقس والأمطار 🌧️",
                wIcon + " " + wVal,
                "", prayerAccentColor(), wDesc);

        // Row 0 - Secondary: Vehicle Heading
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
        prayerCardRect.set(px, py, px + pw, py + ph);
        roundCard(c, new RectF(px, py, px + pw, py + ph), 16f, 1.2f);

        float padX = pw * 0.06f;
        p.setTextAlign(rtl ? Paint.Align.RIGHT : Paint.Align.LEFT);
        p.setColor(goldColor());
        ts(ph * 0.082f);
        p.setFakeBoldText(true);
        float headerY = py + ph * 0.115f;
        String splitPrayerTitle = englishMode ? "🕌 Prayer Times  🔄" : "🕌 مواقيت الصلاة  🔄";
        drawFittedTextAligned(c, splitPrayerTitle,
                rtl ? (px + pw - padX) : (px + padX), headerY, pw * 0.85f);
        p.setFakeBoldText(false);

        String[] names = activePrayerNames();
        int next = getNextPrayerIndex();
        float rowStart = py + ph * 0.225f;
        float rowH = ph * 0.124f;
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

        // 5. Bottom Athkar Bar
        float barX = w * .030f;
        float barW = w * .940f;
        float barH = Math.max(h * 0.068f, h * 0.055f * fontScale);
        float barY = Math.min(h * 0.785f, h * 0.860f - barH);
        RectF barRect = new RectF(barX, barY, barX + barW, barY + barH);
        roundCard(c, barRect, 14f, 1.2f);

        p.setTextAlign(Paint.Align.CENTER);
        float maxTextSize = barH * 0.45f;
        p.setTextSize(Math.min(maxTextSize, barH * 0.40f * fontScale));
        p.setFakeBoldText(true);
        boolean playing = SoundManager.isPlaying();
        p.setColor(playing ? 0xFF00F5C8 : goldColor());
        String thikr = playing
                ? (englishMode ? "🔊 Playing Takbeerat... (Tap to stop)" : "🔊 جاري تشغيل تكبيرات الأذان... (اضغط للإيقاف)")
                : currentThikrText();

        Paint.FontMetrics fm = p.getFontMetrics();
        float textY = barRect.centerY() - (fm.ascent + fm.descent) / 2f;
        drawFittedText(c, thikr, barX + barW * 0.5f, textY, barW * 0.92f);
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

        String wIcon = (weatherInfo != null && weatherInfo.icon != null && !weatherInfo.icon.isEmpty()) ? weatherInfo.icon : "🌤️";
        String wVal = (weatherInfo != null) ? String.format(Locale.US, "%.0f°C", weatherInfo.tempC) : "--";
        String wDesc = (weatherInfo != null) ? weatherInfo.getLocalizedDesc(englishMode) : (englishMode ? "Tap for Radar" : "انقر للرادار");
        if (weatherInfo != null && weatherInfo.isOffline) {
            wDesc += englishMode ? " (GPS)" : " (أوفلاين)";
        }
        weatherCardRect.set(pad, cardY, pad + cardW, cardY + cardH);
        drawInfoCard(c, pad + 0 * (cardW + gap), cardY, cardW, cardH,
                englishMode ? "Weather 🌧️" : "الطقس 🌧️",
                wIcon + " " + wVal,
                "", prayerAccentColor(), wDesc);
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

        // Draw live atmospheric weather effects (rain, clouds, sun rays, sparkling night stars)
        drawWeatherEffects(c, w, h);
    }

    private void drawWeatherEffects(Canvas c, float w, float h) {
        if (!weatherEffectsEnabled) return;
        initWeatherParticles();

        int code = (weatherInfo != null) ? weatherInfo.code : 0;
        boolean day = isDaytime();
        long now = System.currentTimeMillis();

        // 1. Realistic Rain / Drizzle / Thunderstorm (codes 51..67, 80..82, 95..99)
        boolean isRain = (code >= 51 && code <= 67) || (code >= 80 && code <= 82) || (code >= 95 && code <= 99);
        if (isRain) {
            // Overcast rainy atmosphere tint
            p.setStyle(Paint.Style.FILL);
            p.setColor(isNightTheme() ? 0x1A051218 : 0x22102530);
            c.drawRect(0, 0, w, h, p);

            float windSlant = dp(5.5f);
            stroke.setStyle(Paint.Style.STROKE);

            // Layer 1: Background distant fine rain
            stroke.setStrokeWidth(dp(0.9f));
            stroke.setColor(isNightTheme() ? 0x3380D4FF : 0x4455A2DE);
            float bgLen = dp(11f);
            for (int i = 0; i < RAIN_COUNT / 2; i++) {
                float progress = ((now * rainSpeed[i] * 0.0009f) + rainY[i]) % 1.0f;
                float px = (rainX[i] * w + (now * 0.00015f * w)) % w;
                float py = progress * h;
                c.drawLine(px, py, px - windSlant * 0.7f, py + bgLen, stroke);
            }

            // Layer 2: Foreground bright raindrops
            stroke.setStrokeWidth(dp(1.4f));
            stroke.setColor(isNightTheme() ? 0x77AEE5FF : 0x8868BAF0);
            float fgLen = dp(18f);
            for (int i = RAIN_COUNT / 2; i < RAIN_COUNT; i++) {
                float progress = ((now * rainSpeed[i] * 0.0014f) + rainY[i]) % 1.0f;
                float px = (rainX[i] * w + (now * 0.00025f * w)) % w;
                float py = progress * h;
                c.drawLine(px, py, px - windSlant, py + fgLen, stroke);

                // Small realistic droplet splash near bottom
                if (py > h * 0.85f && (i % 3 == 0)) {
                    stroke.setStrokeWidth(dp(0.8f));
                    stroke.setColor(0x338AD4FF);
                    float rippleR = dp(3.5f) * ((py - h * 0.85f) / (h * 0.15f));
                    c.drawOval(new RectF(px - rippleR * 2f, py - rippleR * 0.5f, px + rippleR * 2f, py + rippleR * 0.5f), stroke);
                    stroke.setStrokeWidth(dp(1.4f));
                }
            }
            return;
        }

        // 2. Realistic Fog / Dust / Haze (codes 45, 48)
        if (code == 45 || code == 48) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(isNightTheme() ? 0x22FFD54F : 0x2CFFE082);
            c.drawRect(0, 0, w, h, p);
            float mistDrift = (now * 0.00004f * w) % (w * 1.5f) - w * 0.25f;
            p.setColor(0x18FFF8E1);
            c.drawCircle(mistDrift, h * 0.25f, h * 0.35f, p);
            c.drawCircle(mistDrift + w * 0.5f, h * 0.30f, h * 0.40f, p);
            return;
        }

        // 3. Realistic Volumetric Clouds (codes 2, 3)
        if (code == 2 || code == 3) {
            p.setStyle(Paint.Style.FILL);
            float drift1 = (now * 0.000025f * w) % (w * 1.5f) - w * 0.25f;
            float drift2 = ((now * 0.000018f * w) + w * 0.6f) % (w * 1.5f) - w * 0.25f;

            // Soft layered cloud lobes
            int cloudColor1 = isNightTheme() ? 0x184A6573 : 0x247A9DAE;
            int cloudColor2 = isNightTheme() ? 0x103A525E : 0x1C6A8D9E;

            p.setColor(cloudColor1);
            c.drawCircle(drift1, h * 0.14f, h * 0.15f, p);
            c.drawCircle(drift1 + h * 0.12f, h * 0.12f, h * 0.18f, p);
            c.drawCircle(drift1 + h * 0.24f, h * 0.15f, h * 0.13f, p);

            p.setColor(cloudColor2);
            c.drawCircle(drift2, h * 0.20f, h * 0.17f, p);
            c.drawCircle(drift2 + h * 0.15f, h * 0.18f, h * 0.21f, p);
            c.drawCircle(drift2 + h * 0.30f, h * 0.21f, h * 0.15f, p);
            return;
        }

        // 4. Realistic Clear Sky (Day = Volumetric Sunbeams & Warm Corona, Night = Twinkling Stars & Shooting Star)
        if (day) {
            // Sun location
            float sunCx = panelSwapped ? (w * 0.88f) : (w * 0.12f);
            float sunCy = h * 0.07f;
            float pulse = (float) Math.sin(now * 0.0018) * dp(5f);

            // Radiant corona glow
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x0EFFF0B8);
            c.drawCircle(sunCx, sunCy, h * 0.38f + pulse, p);
            p.setColor(0x1CFFE082);
            c.drawCircle(sunCx, sunCy, h * 0.22f + pulse * 0.5f, p);
            p.setColor(0x35FFF9C4);
            c.drawCircle(sunCx, sunCy, h * 0.11f, p);
            p.setColor(0x70FFFFFF);
            c.drawCircle(sunCx, sunCy, h * 0.045f, p);

            // Volumetric soft God Rays radiating diagonally across screen
            stroke.setStyle(Paint.Style.STROKE);
            float dir = panelSwapped ? -1.0f : 1.0f;
            for (int r = 0; r < 4; r++) {
                double angleDeg = (r * 18.0) + (Math.sin(now * 0.0008 + r) * 3.0);
                double angleRad = Math.toRadians(35.0 + angleDeg);
                float rayLen = Math.min(w, h) * 0.85f;
                float ex = sunCx + (float) Math.cos(angleRad) * rayLen * dir;
                float ey = sunCy + (float) Math.sin(angleRad) * rayLen;

                int rayAlpha = (int) (12 + 8 * Math.sin(now * 0.0012 + r * 1.5));
                stroke.setColor((rayAlpha << 24) | 0xFFEAA6);
                stroke.setStrokeWidth(dp(18f + r * 6f));
                c.drawLine(sunCx, sunCy, ex, ey, stroke);
            }
        } else {
            // Realistic Night Sky
            p.setStyle(Paint.Style.FILL);

            // Multi-depth twinkling stars
            for (int i = 0; i < STAR_COUNT; i++) {
                float sx = starX[i] * w;
                float sy = starY[i] * h;
                float twinkle = (float) (0.35 + 0.65 * Math.sin(now * 0.0025 + i * 2.3));
                int alpha = (int) (starAlpha[i] * twinkle * 215);

                // Major star with 4-point subtle cross-flare
                if (i % 6 == 0) {
                    p.setColor((alpha << 24) | 0xFFFBEA);
                    c.drawCircle(sx, sy, dp(2.0f), p);
                    stroke.setStyle(Paint.Style.STROKE);
                    stroke.setColor(((alpha / 2) << 24) | 0xFFFBEA);
                    stroke.setStrokeWidth(dp(0.9f));
                    float flareLen = dp(5.0f);
                    c.drawLine(sx - flareLen, sy, sx + flareLen, sy, stroke);
                    c.drawLine(sx, sy - flareLen, sx, sy + flareLen, stroke);
                    p.setStyle(Paint.Style.FILL);
                } else {
                    p.setColor((alpha << 24) | 0xFFF8E7);
                    float starR = (i % 3 == 0) ? dp(1.8f) : dp(1.1f);
                    c.drawCircle(sx, sy, starR, p);
                }
            }

            // Realistic Graceful Shooting Star (Meteor / شهاب عابر) every 14 seconds
            long meteorCycle = now % 14000L;
            if (meteorCycle < 900L) { // 900ms streak
                float mProg = meteorCycle / 900.0f;
                float startMx = w * 0.75f;
                float startMy = h * 0.06f;
                float streakDist = Math.min(w, h) * 0.35f;
                float curMx = startMx - mProg * streakDist * 1.2f;
                float curMy = startMy + mProg * streakDist * 0.7f;
                float tailMx = curMx + dp(38f);
                float tailMy = curMy - dp(22f);

                int mAlpha = (int) (Math.sin(mProg * Math.PI) * 230);
                stroke.setStyle(Paint.Style.STROKE);
                stroke.setStrokeWidth(dp(1.6f));
                stroke.setColor((mAlpha << 24) | 0xFFFFFF);
                c.drawLine(curMx, curMy, tailMx, tailMy, stroke);

                p.setStyle(Paint.Style.FILL);
                p.setColor((mAlpha << 24) | 0xFFFFFF);
                c.drawCircle(curMx, curMy, dp(2.2f), p);
            }
        }
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

        // Top center is kept clean with the guidance badge placed at the very top

        Date now = new Date();
        float rightX = landscape ? (w * .978f) : (w * .965f);
        p.setTextAlign(Paint.Align.RIGHT);
        p.setColor(primaryTextColor());
        ts(minDim * (landscape ? .040f : .032f));
        p.setFakeBoldText(true);
        c.drawText(new SimpleDateFormat("hh:mm a", Locale.US).format(now), rightX, h * (landscape ? .065f : .046f), p);
        p.setFakeBoldText(false);

        // Gregorian Date - Prominent and clearly readable
        ts(minDim * (landscape ? .026f : .020f));
        p.setColor(secondaryTextColor());
        p.setFakeBoldText(true);
        float rightMaxW = landscape ? (w * .32f) : (w * .42f);
        drawFittedTextAligned(c, new SimpleDateFormat("EEEE d MMMM", englishMode ? Locale.US : ar).format(now), rightX, h * (landscape ? .096f : .068f), rightMaxW);

        // Hijri Date - Large Royal Gold typography for high visibility from driver seat
        p.setColor(goldColor());
        ts(minDim * (landscape ? .032f : .025f));
        p.setFakeBoldText(true);
        String activeHijri = (englishMode && !hijriDateEn.isEmpty()) ? hijriDateEn : hijriDate;
        String emptyHijri = englishMode ? "Hijri date after prayer sync" : "التاريخ الهجري بعد تحديث الصلاة";
        drawFittedTextAligned(c, activeHijri.isEmpty() ? emptyHijri : activeHijri, rightX, h * (landscape ? .134f : .095f), rightMaxW);
        p.setFakeBoldText(false);
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

        float yTop = h * .142f;
        float gap = h * .009f;
        float cardH = h * .108f;

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

        // Card 5: Weather Telemetry Card
        float wCardY = yTop + 4 * (cardH + gap);
        weatherCardRect.set(x, wCardY, x + cardW, wCardY + cardH);
        String wIcon = (weatherInfo != null) ? weatherInfo.icon : "🌤️";
        String wVal = (weatherInfo != null) ? String.format(Locale.US, "%.0f°C", weatherInfo.tempC) : "--";
        String wDesc = (weatherInfo != null) ? weatherInfo.getLocalizedDesc(englishMode) : (englishMode ? "Weather" : "الطقس");
        if (weatherInfo != null && weatherInfo.isOffline) {
            wDesc += englishMode ? " (GPS)" : " (أوفلاين)";
        }
        drawInfoCard(c, x, wCardY, cardW, cardH,
                englishMode ? "Weather" : "الطقس",
                wIcon + " " + wVal,
                "", prayerAccentColor(), wDesc);
    }

    private void drawInfoCard(Canvas c, float x, float y, float cw, float ch, String label, String value, String icon, int accent) {
        drawInfoCard(c, x, y, cw, ch, label, value, icon, accent, null);
    }

    private void drawInfoCard(Canvas c, float x, float y, float cw, float ch, String label, String value, String icon, int accent, String sub) {
        roundCard(c, new RectF(x, y, x + cw, y + ch), 14f, 1.0f);

        float cx = x + cw * 0.5f;

        p.setTextAlign(Paint.Align.CENTER);
        if (sub != null && !sub.isEmpty()) {
            ts(ch * .18f);
            p.setColor(accent);
            String header = (icon != null && !icon.isEmpty()) ? (icon + " " + label) : label;
            drawFittedText(c, header, cx, y + ch * 0.28f, cw * 0.90f);

            p.setColor(primaryTextColor());
            ts(ch * .27f);
            p.setFakeBoldText(true);
            drawFittedText(c, value, cx, y + ch * 0.60f, cw * 0.90f);
            p.setFakeBoldText(false);

            p.setColor(prayerSecondaryColor());
            ts(ch * .18f);
            drawFittedText(c, sub, cx, y + ch * 0.86f, cw * 0.90f);
        } else {
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

        float rot = hasHeading ? -heading : 0f;
        c.save();
        c.rotate(rot, cx, cy);

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

        drawCardinal(c, "N", cx, cy, r * .72f, 0, aligned ? 0xFFFFD768 : primaryTextColor());
        drawCardinal(c, "E", cx, cy, r * .72f, 90, primaryTextColor());
        drawCardinal(c, "S", cx, cy, r * .72f, 180, primaryTextColor());
        drawCardinal(c, "W", cx, cy, r * .72f, 270, primaryTextColor());
        c.restore();

        // Sleek golden vehicle direction pip at 12 o'clock
        Path vehiclePip = new Path();
        vehiclePip.moveTo(cx, cy - r * 1.02f);
        vehiclePip.lineTo(cx - r * 0.035f, cy - r * 1.12f);
        vehiclePip.lineTo(cx + r * 0.035f, cy - r * 1.12f);
        vehiclePip.close();
        p.setStyle(Paint.Style.FILL);
        p.setColor(aligned ? 0xFF00F5C8 : goldColor());
        c.drawPath(vehiclePip, p);

        if (hasLocation) {
            float arrow = hasHeading ? normalize(qiblaBearing - heading) : qiblaBearing;
            float angle = arrow > 180 ? arrow - 360 : arrow;
            c.save();
            c.rotate(angle, cx, cy);

            int arrowColor = aligned ? 0xFF00F5C8 : goldColor();
            int shadowColor = aligned ? 0xDD00F5C8 : 0x88FFD768;

            if (needleStyle == 1) {
                // Style 1: Classic Precision Navigational Arrow (سهم الاتجاه الملاحي الدقيق)
                Path leftWing = new Path();
                leftWing.moveTo(cx, cy - r * 0.76f);
                leftWing.lineTo(cx - r * 0.11f, cy - r * 0.38f);
                leftWing.lineTo(cx, cy - r * 0.46f);
                leftWing.close();
                p.setStyle(Paint.Style.FILL);
                p.setColor(arrowColor);
                p.setShadowLayer(18f, 0, 0, shadowColor);
                c.drawPath(leftWing, p);

                Path rightWing = new Path();
                rightWing.moveTo(cx, cy - r * 0.76f);
                rightWing.lineTo(cx + r * 0.11f, cy - r * 0.38f);
                rightWing.lineTo(cx, cy - r * 0.46f);
                rightWing.close();
                p.setColor(aligned ? 0xFF00BFA5 : (isNightTheme() ? 0xFF8A6726 : 0xFFC9983E));
                c.drawPath(rightWing, p);
                p.clearShadowLayer();

                // Counterweight tail
                stroke.setStyle(Paint.Style.STROKE);
                stroke.setStrokeWidth(r * 0.032f);
                stroke.setColor(aligned ? 0xFF00F5C8 : goldColor());
                c.drawLine(cx, cy - r * 0.46f, cx, cy + r * 0.18f, stroke);

                // Small counterweight diamond
                Path tail = new Path();
                tail.moveTo(cx, cy + r * 0.28f);
                tail.lineTo(cx - r * 0.06f, cy + r * 0.18f);
                tail.lineTo(cx + r * 0.06f, cy + r * 0.18f);
                tail.close();
                p.setStyle(Paint.Style.FILL);
                p.setColor(aligned ? 0xFF00F5C8 : goldColor());
                c.drawPath(tail, p);

            } else if (needleStyle == 2) {
                // Style 2: Marine Compass Rose Star Needle (وردة البوصلة البحرية الدائرية)
                Path mainStarLeft = new Path();
                mainStarLeft.moveTo(cx, cy - r * 0.75f);
                mainStarLeft.lineTo(cx - r * 0.13f, cy);
                mainStarLeft.lineTo(cx, cy);
                mainStarLeft.close();
                p.setStyle(Paint.Style.FILL);
                p.setColor(arrowColor);
                p.setShadowLayer(20f, 0, 0, shadowColor);
                c.drawPath(mainStarLeft, p);

                Path mainStarRight = new Path();
                mainStarRight.moveTo(cx, cy - r * 0.75f);
                mainStarRight.lineTo(cx + r * 0.13f, cy);
                mainStarRight.lineTo(cx, cy);
                mainStarRight.close();
                p.setColor(aligned ? 0xFF00BFA5 : (isNightTheme() ? 0xFF9E7832 : 0xFFB88E3E));
                c.drawPath(mainStarRight, p);
                p.clearShadowLayer();

                // Marine southern counter-pointer
                Path southLeft = new Path();
                southLeft.moveTo(cx, cy + r * 0.35f);
                southLeft.lineTo(cx - r * 0.09f, cy);
                southLeft.lineTo(cx, cy);
                southLeft.close();
                p.setColor(isNightTheme() ? 0xFF35525B : 0xFF7D9DA6);
                c.drawPath(southLeft, p);

                Path southRight = new Path();
                southRight.moveTo(cx, cy + r * 0.35f);
                southRight.lineTo(cx + r * 0.09f, cy);
                southRight.lineTo(cx, cy);
                southRight.close();
                p.setColor(isNightTheme() ? 0xFF243B42 : 0xFF65848D);
                c.drawPath(southRight, p);

            } else {
                // Style 0 (Default Luxury): Golden Kaaba Minaret & Dome (منارة وقبة الكعبة الذهبية)
                stroke.setStyle(Paint.Style.STROKE);
                stroke.setStrokeWidth(r * .038f);
                stroke.setColor(arrowColor);
                c.drawLine(cx, cy + r * .02f, cx, cy - r * .48f, stroke);

                // Minaret balcony
                stroke.setStrokeWidth(r * 0.015f);
                stroke.setColor(aligned ? 0xFF00F5C8 : 0xFFFFE082);
                c.drawLine(cx - r * 0.08f, cy - r * 0.48f, cx + r * 0.08f, cy - r * 0.48f, stroke);

                // Minaret dome & spire
                Path minaret = new Path();
                minaret.moveTo(cx, cy - r * .76f);
                minaret.lineTo(cx - r * .115f, cy - r * .49f);
                minaret.lineTo(cx + r * .115f, cy - r * .49f);
                minaret.close();
                p.setStyle(Paint.Style.FILL);
                p.setColor(arrowColor);
                p.setShadowLayer(22f, 0, 0, shadowColor);
                c.drawPath(minaret, p);

                // Golden dome crescent tip
                p.setColor(0xFFFFE082);
                c.drawCircle(cx, cy - r * 0.77f, r * 0.035f, p);
                p.clearShadowLayer();
            }

            c.restore();
        }

        drawKaaba(c, cx, cy, r);
        drawCompassBottomBadge(c, cx, cy, r, aligned);
        drawVehicleRelativeBadge(c, cx, cy, r, aligned);
    }

    private void drawVehicleRelativeBadge(Canvas c, float cx, float cy, float r, boolean aligned) {
        if (!hasLocation || !hasHeading) return;
        String text = getVehicleRelativeQiblaText();
        if (text == null || text.isEmpty()) return;

        float h = getHeight();
        float w = getWidth();
        boolean landscape = (w / h) >= 1.35f;

        float badgeW = Math.max(r * 1.25f, 210f);
        float badgeH = Math.max(r * 0.135f, 26f);

        float badgeY;
        if (headingMode == 1) {
            // Standard Classic Mode: directly on top of the compass dial
            badgeY = Math.max(dp(6), cy - r * 1.28f);
        } else {
            // Elevated Mode: raised to the very top bar
            badgeY = landscape ? (h * 0.038f) : (h * 0.024f);
        }

        RectF badgeRect = new RectF(cx - badgeW * 0.5f, badgeY, cx + badgeW * 0.5f, badgeY + badgeH);
        p.setStyle(Paint.Style.FILL);
        p.setColor(aligned ? 0xDD003B2E : (isNightTheme() ? 0xDD0C242C : 0xDDDEEAE9));
        c.drawRoundRect(badgeRect, badgeH * 0.5f, badgeH * 0.5f, p);

        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(aligned ? 1.6f : 1.0f);
        stroke.setColor(aligned ? 0xFF00F5C8 : goldColor());
        c.drawRoundRect(badgeRect, badgeH * 0.5f, badgeH * 0.5f, stroke);

        p.setTextAlign(Paint.Align.CENTER);
        ts(badgeH * 0.54f);
        p.setFakeBoldText(true);
        p.setColor(aligned ? 0xFF00F5C8 : (isNightTheme() ? goldColor() : 0xFF00585E));
        Paint.FontMetrics fm = p.getFontMetrics();
        float textY = badgeRect.centerY() - (fm.ascent + fm.descent) / 2f;
        drawFittedText(c, text, cx, textY, badgeW * 0.92f);
        p.setFakeBoldText(false);
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

        // Card 1: مواقيت الصلاة (Prayer Times Card - Tap to Sync Online)
        prayerCardRect.set(x, y1, x + cw, y1 + ch1);
        roundCard(c, new RectF(x, y1, x + cw, y1 + ch1), 16f, 1.2f);
        boolean rtl = !englishMode;
        float padX = cw * 0.08f;

        // Header Title with inline sync button (حذف التاريخ من داخل البطاقة ووضع زر التحديث بجانب مواقيت الصلاة)
        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(goldColor());
        ts(ch1 * .085f);
        p.setFakeBoldText(true);
        float headerY = y1 + ch1 * 0.125f;
        String prayerTitle = englishMode ? "🕌 Prayer Times  🔄" : "🕌 مواقيت الصلاة  🔄";
        drawFittedText(c, prayerTitle, x + cw * 0.5f, headerY, cw * 0.90f);
        p.setFakeBoldText(false);

        String[] names = activePrayerNames();
        int next = getNextPrayerIndex();
        float rowStart = y1 + ch1 * 0.225f;
        float rowH = ch1 * 0.122f;

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

        // Card 3: الموقع والطقس وحالة النظام (Location & Weather Card)
        roundCard(c, new RectF(x, y3, x + cw, y3 + ch3), 16f, 1.2f);
        p.setTextAlign(rtl ? Paint.Align.RIGHT : Paint.Align.LEFT);
        float alignX = rtl ? (x + cw - padX) : (x + padX);

        ts(ch3 * 0.17f);
        p.setColor(prayerAccentColor());
        String placeLine = "📍 " + localizedLocationName() + "  •  " + (englishMode ? "Umm al-Qura" : "تقويم أم القرى");
        drawFittedTextAligned(c, placeLine, alignX, y3 + ch3 * 0.26f, cw * 0.86f);

        p.setColor(prayerSecondaryColor());
        ts(ch3 * 0.135f);
        String coords = hasLocation
                ? String.format(Locale.US, "🛰️ %.4f° N, %.4f° E  (±%s)", latitude, longitude, formatAccuracy())
                : (englishMode ? "🛰️ Waiting for GPS lock" : "🛰️ بانتظار إشارة GPS");
        drawFittedTextAligned(c, coords, alignX, y3 + ch3 * 0.52f, cw * 0.86f);

        // Prominent Weather row directly below GPS coordinates line
        weatherPrayerRowRect.set(x, y3 + ch3 * 0.65f, x + cw, y3 + ch3);
        p.setColor(goldColor());
        ts(ch3 * 0.155f);
        p.setFakeBoldText(true);
        String weatherText = (weatherInfo != null)
                ? weatherInfo.getSummaryLine(englishMode)
                : (englishMode ? "🌤️ Weather: Fetching..." : "🌤️ الطقس: جاري التحديث...");
        drawFittedTextAligned(c, weatherText, alignX, y3 + ch3 * 0.80f, cw * 0.86f);
        p.setFakeBoldText(false);
    }

    private void drawBottomAthkarBar(Canvas c, float w, float h) {
        float barH = Math.max(h * 0.064f, h * 0.054f * fontScale);
        float barY = Math.min(h * 0.925f, h * 0.986f - barH);
        float startX = panelSwapped ? (w * 0.035f) : (w * 0.350f);
        float endX = panelSwapped ? (w * 0.635f) : (w * 0.965f);
        RectF barRect = new RectF(startX, barY, endX, barY + barH);

        roundCard(c, barRect, 14f, 1.2f);

        p.setTextAlign(Paint.Align.CENTER);
        // Strict clamp to ensure text always stays inside the pill bounds regardless of font scale
        float maxTextSize = barH * 0.45f;
        p.setTextSize(Math.min(maxTextSize, barH * 0.40f * fontScale));
        p.setFakeBoldText(true);
        boolean playing = SoundManager.isPlaying();
        p.setColor(playing ? 0xFF00F5C8 : goldColor());
        String thikr = playing
                ? (englishMode ? "🔊 Playing Takbeerat... (Tap to stop)" : "🔊 جاري تشغيل تكبيرات الأذان... (اضغط للإيقاف)")
                : currentThikrText();

        Paint.FontMetrics fm = p.getFontMetrics();
        float textY = barRect.centerY() - (fm.ascent + fm.descent) / 2f;
        drawFittedText(c, thikr, (startX + endX) * 0.5f, textY, (endX - startX) * 0.92f);
        p.setFakeBoldText(false);
    }

    private void drawPortraitBottomAthkar(Canvas c, float w, float h) {
        float pad = w * .025f;
        float barH = Math.max(h * 0.060f, h * 0.050f * fontScale);
        float barY = Math.min(h * 0.830f, h * 0.890f - barH);
        RectF barRect = new RectF(pad, barY, w - pad, barY + barH);
        roundCard(c, barRect, 14f, 1.2f);

        p.setTextAlign(Paint.Align.CENTER);
        float maxTextSize = barH * 0.45f;
        p.setTextSize(Math.min(maxTextSize, barH * 0.40f * fontScale));
        p.setFakeBoldText(true);
        boolean playing = SoundManager.isPlaying();
        p.setColor(playing ? 0xFF00F5C8 : goldColor());
        String thikr = playing
                ? (englishMode ? "🔊 Playing Takbeerat... (Tap to stop)" : "🔊 جاري تشغيل تكبيرات الأذان... (اضغط للإيقاف)")
                : currentThikrText();

        Paint.FontMetrics fm = p.getFontMetrics();
        float textY = barRect.centerY() - (fm.ascent + fm.descent) / 2f;
        drawFittedText(c, thikr, w * 0.5f, textY, (w - pad * 2f) * 0.92f);
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
        float rawX = event.getX();
        float x = hudMirrorMode ? (getWidth() - rawX) : rawX;
        float y = event.getY();
        float h = getHeight();

        if (hudMirrorMode && event.getAction() == MotionEvent.ACTION_UP && y <= dp(50)) {
            setHudMirrorMode(false);
            return true;
        }

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            // 1. Tapping on active Adhan banner dismisses it and silences audio
            if (System.currentTimeMillis() < adhanBannerUntil && adhanBannerText != null && y <= h * 0.15f) {
                dismissAdhanBanner();
                SoundManager.stop();
                return true;
            }

            // 2. Tapping on Weather card or Weather telemetry row or Prayer Times card
            if (weatherCardRect.contains(x, y) || weatherPrayerRowRect.contains(x, y) || prayerCardRect.contains(x, y)) {
                return true;
            }

            // 3. Tapping bottom Athkar bar stops audio if playing
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
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            if ((weatherCardRect.contains(x, y) || weatherPrayerRowRect.contains(x, y)) && onWeatherClickListener != null) {
                onWeatherClickListener.onWeatherClicked();
                return true;
            }
            if (prayerCardRect.contains(x, y) && onPrayerClickListener != null) {
                onPrayerClickListener.onPrayerClicked();
                return true;
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
