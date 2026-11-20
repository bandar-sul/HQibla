package sa.h9.qibla;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
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
            "اللَّهُمَّ صَلِّ وَسَلِّمْ عَلَى نَبِيِّنَا مُحَمَّدٍ"
    };

    private static final String[] ATHKAR_EN = {
            "Glory to Him Who has subjected this to us, and we could never have it by ourselves.",
            "O Allah, we ask You on this journey of ours for righteousness and piety.",
            "O Allah, make easy for us this journey of ours and shorten its distance.",
            "O Allah, You are our Companion on the journey and Caretaker of our family.",
            "There is no deity except Allah alone; to Him belongs all praise and power.",
            "Glory be to Allah and His is the praise, Glory be to Allah the Supreme.",
            "O Allah, send blessings and peace upon our Prophet Muhammad."
    };

    private static final String[] MORNING_ATHKAR_AR = {
            "🌅 أذكار الصباح: أَصْبَحْنَا وَأَصْبَحَ الْمُلْكُ لِلَّهِ رَبِّ الْعَالَمِينَ",
            "🌅 اللَّهُمَّ بِكَ أَصْبَحْنَا وَبِكَ أَمْسَيْنَا وَبِكَ نَحْيَا وَبِكَ نَمُوتُ وَإِلَيْكَ النُّشُورُ",
            "🌅 رَضِيتُ بِاللَّهِ رَبًّا، وَبِالْإِسْلَامِ دِينًا، وَبِمُحَمَّدٍ ﷺ نَبِيًّا",
            "🌅 اللَّهُمَّ عَافِنِي فِي بَدَنِي، اللَّهُمَّ عَافِنِي فِي سَمْعِي، اللَّهُمَّ عَافِنِي فِي بَصَرِي",
            "🌅 حَسْبِيَ اللَّهُ لَا إِلَهَ إِلَّا هُوَ عَلَيْهِ تَوَكَّلْتُ وَهُوَ رَبُّ الْعَرْشِ الْعَظِيمِ",
            "🚗 سُبْحَانَ الَّذِي سَخَّرَ لَنَا هَذَا وَمَا كُنَّا لَهُ مُقْرِنِينَ وَإِنَّا إِلَى رَبِّنَا لَمُنْقَلِبُونَ"
    };

    private static final String[] MORNING_ATHKAR_EN = {
            "🌅 Morning Athkar: We have reached morning, and all dominion belongs to Allah.",
            "🌅 O Allah, by You we enter morning & evening, by You we live and to You is the return.",
            "🌅 I am pleased with Allah as Lord, Islam as religion, and Muhammad ﷺ as Prophet.",
            "🌅 Sufficient for me is Allah; there is no deity except Him, in Him I put my trust.",
            "🚗 Glory to Him Who subjected this transport to us, and to our Lord we shall return."
    };

    private static final String[] EVENING_ATHKAR_AR = {
            "🌙 أذكار المساء: أَمْسَيْنَا وَأَمْسَى الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ",
            "🌙 اللَّهُمَّ بِكَ أَمْسَيْنَا وَبِكَ أَصْبَحْنَا وَبِكَ نَحْيَا وَبِكَ نَمُوتُ وَإِلَيْكَ الْمَصِيرُ",
            "🌙 بِسْمِ اللَّهِ الَّذِي لَا يَضُرُّ مَعَ اسْمِهِ شَيْءٌ فِي الْأَرْضِ وَلَا فِي السَّمَاءِ وَهُوَ السَّمِيعُ الْعَلِيمُ",
            "🌙 أَعُوذُ بِكَلِمَاتِ اللَّهِ التَّامَّاتِ مِنْ شَرِّ مَا خَلَقَ",
            "🌙 اللَّهُمَّ أَنْتَ رَبِّي لَا إِلَهَ إِلَّا أَنْتَ، خَلَقْتَنِي وَأَنَا عَبْدُكَ، أَسْتَغْفِرُكَ وَأَتُوبُ إِلَيْكَ",
            "🚗 اللَّهُمَّ هَوِّنْ عَلَيْنَا سَفَرَنَا هَذَا وَاطْوِ عَنَّا بُعْدَهُ"
    };

    private static final String[] EVENING_ATHKAR_EN = {
            "🌙 Evening Athkar: We have reached evening, and all dominion belongs to Allah.",
            "🌙 O Allah, by You we enter evening & morning, and to You is our final destination.",
            "🌙 In the Name of Allah with Whose Name nothing in earth or heaven can harm.",
            "🌙 I seek refuge in the perfect words of Allah from the evil of what He created.",
            "🚗 O Allah, make easy this journey of ours and shorten its distance."
    };

    private static final String[] FRIDAY_ATHKAR_AR = {
            "🕌 يوم الجمعة: أَكْثِرُوا مِنَ الصَّلَاةِ عَلَى النَّبِيِّ ﷺ — اللَّهُمَّ صَلِّ وَسَلِّمْ عَلَى مُحَمَّدٍ",
            "📖 سنّة الجمعة: قراءة سورة الكهف نورٌ ما بين الجمعتين",
            "🤲 يوم الجمعة: تحرَّ ساعة الاستجابة خاصةً آخر ساعة قبل صلاة المغرب",
            "🕌 اللَّهُمَّ إِنَّا نَسْأَلُكَ فِي هَذَا الْيَوْمِ الْمُبَارَكِ الْبَرَكَةَ وَالْمَغْفِرَةَ وَالرِّزْقَ الطَّيِّبَ"
    };

    private static final String[] FRIDAY_ATHKAR_EN = {
            "🕌 Blessed Friday: Send abundant Salawat upon Prophet Muhammad ﷺ.",
            "📖 Friday Sunnah: Reading Surah Al-Kahf illuminates between the two Fridays.",
            "🤲 Friday Acceptance Hour: Seek Dua especially in the last hour before Maghrib.",
            "🕌 O Allah, bless us on this blessed Friday with forgiveness, peace & guidance."
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
    public static final int THEME_EMERALD = 1;           // Royal Emerald & Gold (زمردي ملكي)
    public static final int THEME_OBSIDIAN = 2;          // Obsidian Luxury Navy (كحلي فاخر)
    public static final int THEME_AMBER = 3;             // Desert Amber & Gold (عنبر ذهبي)
    public static final int THEME_PEARL = 4;             // Pearl Day (نهاري لؤلؤي)
    public static final int THEME_OLED_NIGHT = 5;        // Pure Black OLED Night (وضع القيادة الليلية الهادئة)
    public static final int THEME_ROYAL_BURGUNDY = 6;    // Royal Burgundy & Rose Gold (عنابي ملكي وذهب وردي)
    public static final int THEME_PLATINUM_TITANIUM = 7; // Royal Platinum & Smoked Titanium (بلاتينيوم وتيتانيوم ملكي)

    // Backward compatibility aliases
    public static final int THEME_DAY = THEME_PEARL;
    public static final int THEME_NIGHT = THEME_EMERALD;

    private int themeMode = THEME_AUTO;
    private boolean autoNightOled = true;
    private int speedLimitKmh = 120;
    private boolean speedMph = false;
    private int athkarTapOffset = 0;
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

    // Swipeable Multi-Face Compass Dial (0 = Qibla Compass, 1 = Digital Clock & Speed, 2 = Sport Compass, 3 = Luxury Analog Watch)
    public static final int DIAL_MODE_COMPASS = 0;
    public static final int DIAL_MODE_CLOCK_SPEED = 1;
    public static final int DIAL_MODE_SPORT_COMPASS = 2;
    public static final int DIAL_MODE_ANALOG_CLOCK = 3;
    private static final int DIAL_FACE_COUNT = 4;

    private int dialMode = DIAL_MODE_COMPASS;
    private float dialSwipeVisualOffset = 0f;
    private boolean isDraggingDial = false;
    private float dialTouchStartX = 0f;
    private float dialTouchStartY = 0f;
    private float lastCompassCx = 0f;
    private float lastCompassCy = 0f;
    private float lastCompassR = 0f;
    private final RectF dialHitRect = new RectF();
    private final RectF dialDotsHitRect = new RectF();

    public QiblaView(Context context) {
        super(context);
        updateTypeface();
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        try {
            dialMode = Math.max(0, Math.min(DIAL_FACE_COUNT - 1,
                    context.getSharedPreferences("h9_qibla_ui", Context.MODE_PRIVATE).getInt("compass_dial_mode", DIAL_MODE_COMPASS)));
        } catch (Exception ignored) { }
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

    public interface OnSettingsClickListener {
        void onSettingsClicked();
    }
    private OnSettingsClickListener onSettingsClickListener;

    public void setOnSettingsClickListener(OnSettingsClickListener listener) {
        this.onSettingsClickListener = listener;
    }

    public interface OnQuickAppsClickListener {
        void onQuickAppsClicked();
    }
    private OnQuickAppsClickListener onQuickAppsClickListener;

    public void setOnQuickAppsClickListener(OnQuickAppsClickListener listener) {
        this.onQuickAppsClickListener = listener;
    }

    private final RectF weatherCardRect = new RectF();
    private final RectF quickAppsBtnRect = new RectF();
    private final RectF weatherPrayerRowRect = new RectF();
    private final RectF prayerCardRect = new RectF();
    private final RectF topBarSettingsRect = new RectF();
    private final RectF athkarBarRect = new RectF();

    private static final int RAIN_COUNT = 65;
    private static final int MIST_COUNT = 45;
    private static final int SUN_MOTE_COUNT = 16;
    private static final int STAR_COUNT = 36;
    private final float[] rainX = new float[RAIN_COUNT];
    private final float[] rainY = new float[RAIN_COUNT];
    private final float[] rainSpeed = new float[RAIN_COUNT];
    private final float[] mistX = new float[MIST_COUNT];
    private final float[] mistY = new float[MIST_COUNT];
    private final float[] mistSpeed = new float[MIST_COUNT];
    private final float[] sunMoteX = new float[SUN_MOTE_COUNT];
    private final float[] sunMoteY = new float[SUN_MOTE_COUNT];
    private final float[] sunMotePhase = new float[SUN_MOTE_COUNT];
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
            rainSpeed[i] = 0.65f + rnd.nextFloat() * 0.55f;
        }
        for (int i = 0; i < MIST_COUNT; i++) {
            mistX[i] = rnd.nextFloat();
            mistY[i] = rnd.nextFloat();
            mistSpeed[i] = 0.35f + rnd.nextFloat() * 0.35f;
        }
        for (int i = 0; i < SUN_MOTE_COUNT; i++) {
            sunMoteX[i] = rnd.nextFloat();
            sunMoteY[i] = rnd.nextFloat() * 0.45f;
            sunMotePhase[i] = rnd.nextFloat() * 6.28f;
        }
        for (int i = 0; i < STAR_COUNT; i++) {
            starX[i] = rnd.nextFloat();
            starY[i] = rnd.nextFloat() * 0.42f; // upper 42% sky
            starAlpha[i] = 0.35f + rnd.nextFloat() * 0.65f;
        }
        weatherParticlesInit = true;
    }

    public static final int WEATHER_MODE_AUTO = 0;
    public static final int WEATHER_MODE_THUNDERSTORM = 1;
    public static final int WEATHER_MODE_RAIN = 2;
    public static final int WEATHER_MODE_CLOUDS = 3;
    public static final int WEATHER_MODE_SUN = 4;
    public static final int WEATHER_MODE_OFF = 5;

    private int weatherEffectMode = WEATHER_MODE_AUTO;

    public void setWeatherEffectMode(int mode) {
        this.weatherEffectMode = mode;
        this.weatherEffectsEnabled = (mode != WEATHER_MODE_OFF);
        postInvalidate();
    }

    public int getWeatherEffectMode() {
        return weatherEffectMode;
    }

    public void setWeatherEffectsEnabled(boolean enabled) {
        this.weatherEffectsEnabled = enabled;
        if (!enabled) this.weatherEffectMode = WEATHER_MODE_OFF;
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
        if (mode >= THEME_AUTO && mode <= THEME_PLATINUM_TITANIUM) {
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
            default: return english ? "Car System (Default)" : "خط السيارة (الافتراضي)";
        }
    }

    public void setAutoNightOled(boolean enabled) {
        this.autoNightOled = enabled;
        invalidate();
    }

    public boolean isAutoNightOled() {
        return autoNightOled;
    }

    public void setSpeedLimitKmh(int limitKmh) {
        this.speedLimitKmh = Math.max(0, limitKmh);
        invalidate();
    }

    public int getSpeedLimitKmh() {
        return speedLimitKmh;
    }

    public void setSpeedMph(boolean mph) {
        this.speedMph = mph;
        invalidate();
    }

    private boolean isOverSpeedLimit() {
        return speedLimitKmh > 0 && speedKmh >= speedLimitKmh;
    }

    private String formatSpeedDisplay() {
        float val = speedMph ? (speedKmh * 0.621371f) : speedKmh;
        return String.format(Locale.US, speedMph ? "%.0f mph" : "%.0f km/h", val);
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
                    currentTypeface = Typeface.DEFAULT;
                    break;
            }
        } catch (Exception e) {
            currentTypeface = Typeface.DEFAULT;
        }
        if (currentTypeface == null) {
            currentTypeface = Typeface.DEFAULT;
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
        invalidate();
    }

    private String adhanBannerText = null;
    private long adhanBannerUntil = 0L;

    public boolean hasPrayerTimes() { return hasPrayerTimes; }
    public String[] getPrayerTimes() { return prayerTimes; }
    public String[] getPrayerNames() { return prayerNames; }

    public void showAdhanBanner(String prayerName, String timeStr) {
        String formatted12h = PrayerTimesCalculator.format12Hour(timeStr, englishMode);
        if (englishMode) {
            adhanBannerText = "🕌 Time for " + prayerName + " Prayer (" + formatted12h + ") • May Allah accept";
        } else {
            adhanBannerText = "🕌 حان الآن موعد أذان " + prayerName + " (" + formatted12h + ") • تقبل الله طاعتكم";
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
            return isDaytime() ? THEME_PEARL : (autoNightOled ? THEME_OLED_NIGHT : THEME_EMERALD);
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
            case THEME_OLED_NIGHT: return 0x00000000;        // Zero glare for OLED night driving
            case THEME_ROYAL_BURGUNDY: return 0x18F7C9B6;    // soft rose champagne lattice
            case THEME_PLATINUM_TITANIUM: return 0x16E2E8F0; // soft sterling silver lattice
            case THEME_EMERALD: return 0x16E5B64A;           // soft gold
            case THEME_OBSIDIAN: return 0x1638B6FF;          // soft celestial blue
            case THEME_AMBER: return 0x16FFA834;             // soft warm amber
            case THEME_PEARL: return 0x16006B78;             // soft petrol teal
            default: return 0x16E5B64A;
        }
    }

    private LinearGradient getBackgroundGradient(float w, float h) {
        switch (getEffectiveTheme()) {
            case THEME_OLED_NIGHT:
                return new LinearGradient(0, 0, w, h,
                        new int[]{0xFF000000, 0xFF000000, 0xFF000000},
                        new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
            case THEME_ROYAL_BURGUNDY:
                return new LinearGradient(0, 0, w, h,
                        new int[]{0xFF120308, 0xFF280713, 0xFF0D0206},
                        new float[]{0f, 0.60f, 1f}, Shader.TileMode.CLAMP);
            case THEME_PLATINUM_TITANIUM:
                return new LinearGradient(0, 0, w, h,
                        new int[]{0xFF0A0D12, 0xFF1A222D, 0xFF080B0F},
                        new float[]{0f, 0.62f, 1f}, Shader.TileMode.CLAMP);
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
            case THEME_ROYAL_BURGUNDY: return 0xCC1E060F;
            case THEME_PLATINUM_TITANIUM: return 0xD8121821;
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
            case THEME_ROYAL_BURGUNDY: return 0x55F3C1A6;
            case THEME_PLATINUM_TITANIUM: return 0x55CBD5E1;
            case THEME_EMERALD: return 0x44E5B64A;
            case THEME_OBSIDIAN: return 0x443D8BFF;
            case THEME_AMBER: return 0x44FFA834;
            case THEME_PEARL: return 0x664B7988;
            default: return 0x44E5B64A;
        }
    }

    private int primaryTextColor() {
        if (getEffectiveTheme() == THEME_OLED_NIGHT) return 0xFFEEEEEE;
        if (getEffectiveTheme() == THEME_ROYAL_BURGUNDY) return 0xFFFFF5F0;
        if (getEffectiveTheme() == THEME_PLATINUM_TITANIUM) return 0xFFF8FAFC;
        return (getEffectiveTheme() == THEME_PEARL) ? 0xFF0C242C : 0xFFF5F8FA;
    }

    private int secondaryTextColor() {
        if (getEffectiveTheme() == THEME_OLED_NIGHT) return 0xFFA0A5A8;
        if (getEffectiveTheme() == THEME_ROYAL_BURGUNDY) return 0xFFE6C8BE;
        if (getEffectiveTheme() == THEME_PLATINUM_TITANIUM) return 0xFFCBD5E1;
        return (getEffectiveTheme() == THEME_PEARL) ? 0xFF2B4D58 : 0xFFB8C8CE;
    }

    private int mutedTextColor() {
        if (getEffectiveTheme() == THEME_OLED_NIGHT) return 0xFF6C7377;
        if (getEffectiveTheme() == THEME_ROYAL_BURGUNDY) return 0xFFB89088;
        if (getEffectiveTheme() == THEME_PLATINUM_TITANIUM) return 0xFF94A3B8;
        return (getEffectiveTheme() == THEME_PEARL) ? 0xFF4A6F7B : 0xFF7D929B;
    }

    private int goldColor() {
        switch (getEffectiveTheme()) {
            case THEME_OLED_NIGHT: return 0xFFFFB74D;
            case THEME_ROYAL_BURGUNDY: return 0xFFF7C9B6;
            case THEME_PLATINUM_TITANIUM: return 0xFFFFE29A;
            case THEME_EMERALD: return 0xFFFFD768;
            case THEME_OBSIDIAN: return 0xFFFFD25A;
            case THEME_AMBER: return 0xFFFFBA49;
            case THEME_PEARL: return 0xFF996300;
            default: return 0xFFFFD768;
        }
    }

    private int prayerPrimaryColor() {
        if (getEffectiveTheme() == THEME_OLED_NIGHT) return 0xFFE0E0E0;
        if (getEffectiveTheme() == THEME_ROYAL_BURGUNDY) return 0xFFFFF3ED;
        if (getEffectiveTheme() == THEME_PLATINUM_TITANIUM) return 0xFFF1F5F9;
        return (getEffectiveTheme() == THEME_PEARL) ? 0xFF0D2832 : 0xFFEBF3F5;
    }

    private int prayerSecondaryColor() {
        if (getEffectiveTheme() == THEME_OLED_NIGHT) return 0xFF8A9499;
        if (getEffectiveTheme() == THEME_ROYAL_BURGUNDY) return 0xFFD9B2A8;
        if (getEffectiveTheme() == THEME_PLATINUM_TITANIUM) return 0xFFCBD5E1;
        return (getEffectiveTheme() == THEME_PEARL) ? 0xFF355864 : 0xFFB4C5CB;
    }

    private int prayerAccentColor() {
        switch (getEffectiveTheme()) {
            case THEME_OLED_NIGHT: return 0xFFFFB74D;
            case THEME_ROYAL_BURGUNDY: return 0xFFFFB4A2;
            case THEME_PLATINUM_TITANIUM: return 0xFFBAE6FD;
            case THEME_EMERALD: return 0xFF00F5C8;
            case THEME_OBSIDIAN: return 0xFF38B6FF;
            case THEME_AMBER: return 0xFFFFB74D;
            case THEME_PEARL: return 0xFF00757D;
            default: return 0xFF00F5C8;
        }
    }

    private String currentThikrText() {
        Calendar now = Calendar.getInstance();
        int dow = now.get(Calendar.DAY_OF_WEEK);
        int curMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int fajrMin = (hasPrayerTimes && parseMinutes(prayerTimes[0]) >= 0) ? parseMinutes(prayerTimes[0]) : 5 * 60;
        int dhuhrMin = (hasPrayerTimes && parseMinutes(prayerTimes[2]) >= 0) ? parseMinutes(prayerTimes[2]) : 12 * 60;
        int asrMin = (hasPrayerTimes && parseMinutes(prayerTimes[3]) >= 0) ? parseMinutes(prayerTimes[3]) : 15 * 60 + 30;
        int maghribMin = (hasPrayerTimes && parseMinutes(prayerTimes[4]) >= 0) ? parseMinutes(prayerTimes[4]) : 18 * 60;

        boolean isFriday = (dow == Calendar.FRIDAY);
        boolean isMorning = (curMin >= fajrMin && curMin < dhuhrMin);
        boolean isEvening = (curMin >= asrMin);
        boolean isFridayAcceptanceHour = isFriday && (curMin >= asrMin && curMin < maghribMin);

        if (isFridayAcceptanceHour && (((System.currentTimeMillis() / 15000L) + athkarTapOffset) % 2 == 0)) {
            return englishMode
                    ? "🤲 Friday Hour of Acceptance (before Maghrib): Make sincere Dua & Salawat ﷺ"
                    : "🤲 ساعة الاستجابة يوم الجمعة (قبل المغرب): أكثر من الدعاء والصلاة على النبي ﷺ";
        }

        String[] primary;
        if (isMorning) {
            primary = englishMode ? MORNING_ATHKAR_EN : MORNING_ATHKAR_AR;
        } else if (isEvening) {
            primary = englishMode ? EVENING_ATHKAR_EN : EVENING_ATHKAR_AR;
        } else {
            primary = englishMode ? ATHKAR_EN : ATHKAR_AR;
        }

        String[] friday = englishMode ? FRIDAY_ATHKAR_EN : FRIDAY_ATHKAR_AR;
        long step = (System.currentTimeMillis() / 15000L) + athkarTapOffset;
        if (isFriday) {
            int combinedLen = friday.length + primary.length;
            int idx = (int) ((step % combinedLen + combinedLen) % combinedLen);
            // Interleave Friday reminders and time-of-day Athkar cleanly
            if (idx % 2 == 0 && (idx / 2) < friday.length) {
                return friday[idx / 2];
            }
            return primary[idx % primary.length];
        }
        int idx = (int) ((step % primary.length + primary.length) % primary.length);
        return primary[idx];
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
        if (currentTypeface != null) {
            p.setTypeface(currentTypeface);
        }
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

        // 1. Enlarged Compass / Interactive Dial in its pane - high visibility, clean pager dot clearance
        float compassR = Math.min(paneW * 0.43f, h * 0.165f);
        float compassCx = leftPaneX + paneW * 0.5f;
        float compassCy = h * 0.305f;
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
        boolean overLimit = showSpeed && isOverSpeedLimit();
        drawInfoCard(c, primaryColX, row1Y, cardW, cardH,
                showSpeed
                        ? (overLimit ? (englishMode ? "⚠️ Speed Limit" : "⚠️ تجاوز السرعة") : (englishMode ? "Speed" : "السرعة"))
                        : (englishMode ? "GPS Accuracy" : "دقة GPS"),
                showSpeed ? ((overLimit ? "🚨 " : "") + formatSpeedDisplay()) : formatAccuracy(),
                showSpeed ? (overLimit ? "🚨" : "🚗") : "◎",
                overLimit ? 0xFFFF5252 : prayerAccentColor());

        drawInfoCard(c, secondaryColX, row1Y, cardW, cardH,
                englishMode ? "Heading Source" : "مصدر الاتجاه",
                localizedHeadingSource(),
                "🧭", goldColor());

        // 3. Prayer Times Card in Prayer pane (bottom aligns with Row 0 cards)
        float px = rightPaneX;
        float py = h * 0.175f;
        float pw = paneW;
        float ph = (row0Y + cardH) - py;
        prayerCardRect.set(px, py, px + pw, py + ph);
        roundCard(c, new RectF(px, py, px + pw, py + ph), 16f, 1.2f);

        float padX = pw * 0.06f;
        p.setTextAlign(rtl ? Paint.Align.RIGHT : Paint.Align.LEFT);
        p.setColor(goldColor());
        ts(ph * 0.078f);
        p.setFakeBoldText(true);
        float headerY = py + ph * 0.112f;
        String splitPrayerTitle = englishMode ? "🕌 Prayer Times  🔄" : "🕌 مواقيت الصلاة  🔄";
        drawFittedTextAligned(c, splitPrayerTitle,
                rtl ? (px + pw - padX) : (px + padX), headerY, pw * 0.85f);
        p.setFakeBoldText(false);

        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(1.1f);
        stroke.setColor(0x40FFD768);
        c.drawLine(px + padX * 0.6f, py + ph * 0.165f, px + pw - padX * 0.6f, py + ph * 0.165f, stroke);

        String[] names = activePrayerNames();
        int next = getNextPrayerIndex();
        float rowsTop = py + ph * 0.195f;
        float rowH = (ph * 0.770f) / 6f;
        float maxColW = (pw - padX * 2.2f) * 0.50f;

        for (int i = 0; i < 6; i++) {
            float slotTop = rowsTop + i * rowH;
            float boxTop = slotTop + rowH * 0.06f;
            float boxBottom = slotTop + rowH * 0.94f;
            float boxCy = (boxTop + boxBottom) * 0.5f;

            if (i == next) {
                p.setColor(0x28FFA726);
                c.drawRoundRect(new RectF(px + pw * .03f, boxTop, px + pw * .97f, boxBottom), 10f, 10f, p);
                stroke.setStyle(Paint.Style.STROKE);
                stroke.setStrokeWidth(1.2f);
                stroke.setColor(0xBBFFD768);
                c.drawRoundRect(new RectF(px + pw * .03f, boxTop, px + pw * .97f, boxBottom), 10f, 10f, stroke);
            }

            ts(Math.min(rowH * 0.54f, ph * 0.070f));
            Paint.FontMetrics rowFm = p.getFontMetrics();
            float ry = boxCy - (rowFm.ascent + rowFm.descent) * 0.5f;
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

            // Subtle divider line between prayer rows
            if (i < 5 && i != next && (i + 1) != next) {
                stroke.setStyle(Paint.Style.STROKE);
                stroke.setStrokeWidth(1.0f);
                stroke.setColor(0x18FFFFFF);
                float lineY = slotTop + rowH;
                c.drawLine(px + padX * 0.8f, lineY, px + pw - padX * 0.8f, lineY, stroke);
            }
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

        // 5. Bottom Athkar Bar (Strictly positioned above bottom settings button)
        float barX = w * .030f;
        float barW = w * .940f;
        float reservedBottom = dp(64);
        float barH = Math.min(h * 0.090f, dp(48));
        float barY = h - reservedBottom - barH - dp(4);
        RectF barRect = new RectF(barX, barY, barX + barW, barY + barH);
        athkarBarRect.set(barRect);
        roundCard(c, barRect, 14f, 1.2f);

        p.setTextAlign(Paint.Align.CENTER);
        float maxTextSize = dp(17f);
        p.setTextSize(Math.min(maxTextSize, barH * 0.46f * fontScale));
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

        // 6. Quick Apps Launcher Button paired cleanly beside bottom Settings button in Split-Screen
        drawPairedBottomQuickAppsButton(c, w, h);
    }

    private void drawPortraitLayout(Canvas c, float w, float h) {
        float pad = dp(12);
        float gap = dp(8);
        boolean rtl = !englishMode;

        // Top bar reservation and bottom settings button reservation
        float topReserved = Math.max(dp(54), h * 0.105f);
        float bottomReserved = dp(64); // clearance for elevated mainSettingsButton
        float usableH = Math.max(dp(300), h - topReserved - bottomReserved);

        // 1. Compass / Interactive Dial - Centered in upper area below top bar
        float compassR = Math.min(w * 0.195f, usableH * 0.135f);
        float compassCx = w * 0.50f;
        float compassCy = topReserved + compassR * 1.05f;
        drawCompass(c, compassCx, compassCy, compassR);

        // 2. Telemetry Cards (2x2 Grid) - starting cleanly below compass pager dots
        float cardW = (w - pad * 2f - gap) / 2f;
        float cardH = Math.min(usableH * 0.068f, dp(40));
        float cardY0 = compassCy + compassR * 1.25f;
        float cardY1 = cardY0 + cardH + dp(5);

        float cLeft = pad;
        float cRight = pad + cardW + gap;
        float primaryColX = rtl ? cRight : cLeft;
        float secondaryColX = rtl ? cLeft : cRight;

        // Row 0 - Weather
        String wIcon = (weatherInfo != null && weatherInfo.icon != null && !weatherInfo.icon.isEmpty()) ? weatherInfo.icon : "🌤️";
        String wVal = (weatherInfo != null) ? String.format(Locale.US, "%.0f°C", weatherInfo.tempC) : "--";
        String wDesc = (weatherInfo != null) ? weatherInfo.getLocalizedDesc(englishMode) : (englishMode ? "Tap for Radar" : "انقر للرادار");
        if (weatherInfo != null && weatherInfo.isOffline) {
            wDesc += englishMode ? " (GPS)" : " (أوفلاين)";
        }
        weatherCardRect.set(primaryColX, cardY0, primaryColX + cardW, cardY0 + cardH);
        drawInfoCard(c, primaryColX, cardY0, cardW, cardH,
                englishMode ? "Weather 🌧️" : "الطقس 🌧️",
                wIcon + " " + wVal,
                "", prayerAccentColor(), wDesc);

        // Row 0 - Vehicle Heading
        drawInfoCard(c, secondaryColX, cardY0, cardW, cardH,
                englishMode ? "Heading" : "اتجاه المركبة",
                hasHeading ? String.format(Locale.US, "%.0f°", heading) : "—",
                "↗", hasHeading ? prayerAccentColor() : 0xFFE5B64A);

        // Row 1 - Speed / Accuracy
        boolean showSpeed = speedKmh > 1.8f;
        boolean overLimit = showSpeed && isOverSpeedLimit();
        drawInfoCard(c, primaryColX, cardY1, cardW, cardH,
                showSpeed
                        ? (overLimit ? (englishMode ? "⚠️ Speed Limit" : "⚠️ تجاوز السرعة") : (englishMode ? "Speed" : "السرعة"))
                        : (englishMode ? "GPS Accuracy" : "دقة GPS"),
                showSpeed ? ((overLimit ? "🚨 " : "") + formatSpeedDisplay()) : formatAccuracy(),
                showSpeed ? (overLimit ? "🚨" : "🚗") : "◎",
                overLimit ? 0xFFFF5252 : prayerAccentColor());

        // Row 1 - Heading Source / Qibla
        drawInfoCard(c, secondaryColX, cardY1, cardW, cardH,
                englishMode ? "Heading Source" : "مصدر الاتجاه",
                localizedHeadingSource(),
                "🧭", goldColor());

        // Proportional distribution for Lower Section:
        float lowerSectionTop = cardY1 + cardH + dp(6);
        float lowerSectionBottom = h - bottomReserved;
        float lowerSpace = Math.max(dp(180), lowerSectionBottom - lowerSectionTop);

        float ph = Math.max(dp(125), lowerSpace * 0.58f);
        float nh = Math.max(dp(26), lowerSpace * 0.16f);
        float athkarH = Math.max(dp(26), lowerSpace * 0.16f);
        float interGap = Math.max(dp(4), lowerSpace * 0.05f);

        // 3. Prayer Times Card
        float px = pad;
        float py = lowerSectionTop;
        float pw = w - pad * 2f;
        prayerCardRect.set(px, py, px + pw, py + ph);
        roundCard(c, new RectF(px, py, px + pw, py + ph), 16f, 1.2f);

        // Header Title with inline sync button
        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(goldColor());
        ts(ph * 0.082f);
        p.setFakeBoldText(true);
        float headerY = py + ph * 0.115f;
        String prayerHeader = englishMode
                ? ("🕌 Prayer Times (" + localizedLocationName() + ")  🔄")
                : ("🕌 مواقيت الصلاة (" + localizedLocationName() + ")  🔄");
        drawFittedText(c, prayerHeader, px + pw * 0.5f, headerY, pw * 0.90f);
        p.setFakeBoldText(false);

        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(1.1f);
        stroke.setColor(0x40FFD768);
        float padX = pw * 0.06f;
        c.drawLine(px + padX * 0.6f, py + ph * 0.168f, px + pw - padX * 0.6f, py + ph * 0.168f, stroke);

        String[] names = activePrayerNames();
        int next = getNextPrayerIndex();
        float rowsTop = py + ph * 0.198f;
        float rowH = (ph * 0.765f) / 6f;

        for (int i = 0; i < 6; i++) {
            float slotTop = rowsTop + i * rowH;
            float boxTop = slotTop + rowH * 0.06f;
            float boxBottom = slotTop + rowH * 0.94f;
            float boxCy = (boxTop + boxBottom) * 0.5f;

            if (i == next) {
                p.setColor(0x28FFA726);
                c.drawRoundRect(new RectF(px + pw * .03f, boxTop, px + pw * .97f, boxBottom), 10f, 10f, p);
                stroke.setStyle(Paint.Style.STROKE);
                stroke.setStrokeWidth(1.2f);
                stroke.setColor(0xBBFFD768);
                c.drawRoundRect(new RectF(px + pw * .03f, boxTop, px + pw * .97f, boxBottom), 10f, 10f, stroke);
            }
            ts(Math.min(rowH * 0.52f, dp(15f) * fontScale));
            Paint.FontMetrics rowFm = p.getFontMetrics();
            float ry = boxCy - (rowFm.ascent + rowFm.descent) * 0.5f;
            String icon = (i < PRAYER_ICONS.length) ? PRAYER_ICONS[i] : "•";
            p.setColor(i == next ? goldColor() : prayerPrimaryColor());
            p.setFakeBoldText(i == next);
            if (englishMode) {
                p.setTextAlign(Paint.Align.LEFT);
                c.drawText(icon + "  " + names[i], px + padX, ry, p);
                p.setTextAlign(Paint.Align.RIGHT);
                c.drawText(format12Hour(prayerTimes[i]), px + pw - padX, ry, p);
            } else {
                p.setTextAlign(Paint.Align.RIGHT);
                c.drawText(icon + "  " + names[i], px + pw - padX, ry, p);
                p.setTextAlign(Paint.Align.LEFT);
                c.drawText(format12Hour(prayerTimes[i]), px + padX, ry, p);
            }
            p.setFakeBoldText(false);

            // Divider lines between prayer rows
            if (i < 5 && i != next && (i + 1) != next) {
                stroke.setStyle(Paint.Style.STROKE);
                stroke.setStrokeWidth(1.0f);
                stroke.setColor(0x18FFFFFF);
                float lineY = slotTop + rowH;
                c.drawLine(px + padX * 0.8f, lineY, px + pw - padX * 0.8f, lineY, stroke);
            }
        }

        // 4. Next Prayer Countdown Card
        float ny = py + ph + interGap;
        roundCard(c, new RectF(px, ny, px + pw, ny + nh), 14f, 1.2f);
        p.setTextAlign(Paint.Align.CENTER);
        ts(nh * 0.44f);
        p.setColor(goldColor());
        p.setFakeBoldText(true);
        drawFittedText(c, nextPrayerText(), px + pw * 0.5f, ny + nh * 0.65f, pw * 0.90f);
        p.setFakeBoldText(false);

        // 5. Portrait Bottom Athkar Bar
        float athkarY = ny + nh + interGap;
        drawPortraitBottomAthkar(c, w, h, athkarY, athkarH);

        // 6. Quick Apps Launcher Button paired cleanly beside bottom Settings button in Portrait
        drawPairedBottomQuickAppsButton(c, w, h);
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
        if (!weatherEffectsEnabled || weatherEffectMode == WEATHER_MODE_OFF) return;
        initWeatherParticles();

        int code = (weatherInfo != null) ? weatherInfo.code : 0;
        float currentRainMm = (weatherInfo != null) ? weatherInfo.currentRainMm : 0f;
        boolean day = isDaytime();
        long now = System.currentTimeMillis();

        boolean isRain = false;
        boolean isThunder = false;

        if (weatherEffectMode == WEATHER_MODE_THUNDERSTORM) {
            isRain = true;
            isThunder = true;
        } else if (weatherEffectMode == WEATHER_MODE_RAIN) {
            isRain = true;
            isThunder = false;
        } else if (weatherEffectMode == WEATHER_MODE_CLOUDS) {
            code = 2;
        } else if (weatherEffectMode == WEATHER_MODE_SUN) {
            day = true;
            code = 0;
        } else {
            // Auto Live Mode: Check rain codes, current rainfall mm, or thunderstorm
            isRain = (code >= 51 && code <= 67) || (code >= 80 && code <= 82) || (code >= 95 && code <= 99) || (currentRainMm > 0.05f);
            isThunder = (code >= 95 && code <= 99);
        }

        if (isRain) {
            // Overcast stormy atmosphere tint (keeps UI 100% legible)
            p.setStyle(Paint.Style.FILL);
            p.setColor(isNightTheme() ? 0x18051218 : 0x1C10222D);
            c.drawRect(0, 0, w, h, p);

            // Volumetric Dark Storm Clouds brooding along the upper sky
            float cloudDrift = (now * 0.000018f * w) % (w * 1.6f) - w * 0.3f;
            int sc1 = isNightTheme() ? 0x24121E25 : 0x2A20303C;
            int sc2 = isNightTheme() ? 0x1A0A1318 : 0x20182630;
            p.setColor(sc1);
            c.drawCircle(cloudDrift, h * 0.08f, h * 0.16f, p);
            c.drawCircle(cloudDrift + h * 0.15f, h * 0.06f, h * 0.19f, p);
            c.drawCircle(cloudDrift + h * 0.32f, h * 0.09f, h * 0.16f, p);
            p.setColor(sc2);
            c.drawCircle(cloudDrift + w * 0.50f, h * 0.09f, h * 0.17f, p);
            c.drawCircle(cloudDrift + w * 0.50f + h * 0.16f, h * 0.07f, h * 0.20f, p);

            // Thunderstorm Lightning & Thunder (واقع افتراضي حقيقي للبرق والرعد)
            if (isThunder) {
                long strikeCycle = now % 6200L;
                if (strikeCycle >= 100L && strikeCycle <= 420L) {
                    // Multi-stage illumination pulse
                    int flashAlpha;
                    if (strikeCycle < 170L) {
                        flashAlpha = (int) (45 * ((strikeCycle - 100L) / 70.0f));
                    } else if (strikeCycle < 210L) {
                        flashAlpha = 20; // Inter-pulse dip
                    } else if (strikeCycle < 330L) {
                        float pVal = 1.0f - ((strikeCycle - 210L) / 120.0f);
                        flashAlpha = (int) (90 * pVal); // Main blinding strike
                    } else {
                        float pVal = 1.0f - ((strikeCycle - 330L) / 90.0f);
                        flashAlpha = (int) (28 * pVal); // Thunder echo afterglow
                    }
                    if (flashAlpha > 0) {
                        p.setStyle(Paint.Style.FILL);
                        p.setColor((flashAlpha << 24) | 0xD8ECFF);
                        c.drawRect(0, 0, w, h, p);
                    }

                    // Branching electric lightning bolt across sky
                    if (strikeCycle >= 160L && strikeCycle <= 340L) {
                        long strikeId = now / 6200L;
                        java.util.Random bRnd = new java.util.Random(strikeId * 31337L);
                        float boltStartX = (0.22f + bRnd.nextFloat() * 0.56f) * w;
                        float boltStartY = h * 0.04f;

                        Path mainBolt = new Path();
                        mainBolt.moveTo(boltStartX, boltStartY);
                        Path branches = new Path();

                        float curX = boltStartX;
                        float curY = boltStartY;
                        int segments = 8;
                        float segH = (h * 0.52f) / segments;

                        for (int s = 0; s < segments; s++) {
                            float nY = curY + segH * (0.8f + bRnd.nextFloat() * 0.4f);
                            float dx = (bRnd.nextFloat() - 0.5f) * dp(26f);
                            float nX = curX + dx;
                            mainBolt.lineTo(nX, nY);

                            if (s == 2 || s == 4 || s == 6) {
                                branches.moveTo(nX, nY);
                                float brX = nX;
                                float brY = nY;
                                float brDir = (dx >= 0) ? 1.0f : -1.0f;
                                for (int b = 0; b < 3; b++) {
                                    brX += brDir * (dp(12f) + bRnd.nextFloat() * dp(14f));
                                    brY += segH * 0.60f * (0.7f + bRnd.nextFloat() * 0.5f);
                                    branches.lineTo(brX, brY);
                                }
                            }
                            curX = nX;
                            curY = nY;
                        }

                        // Outer electric aura
                        stroke.setStyle(Paint.Style.STROKE);
                        stroke.setColor(0x887AE5FF);
                        stroke.setStrokeWidth(dp(4.8f));
                        c.drawPath(mainBolt, stroke);
                        stroke.setStrokeWidth(dp(2.8f));
                        c.drawPath(branches, stroke);

                        // Inner brilliant electric core
                        stroke.setColor(0xFFFFFFFF);
                        stroke.setStrokeWidth(dp(2.0f));
                        c.drawPath(mainBolt, stroke);
                        stroke.setColor(0xEEFFFFFF);
                        stroke.setStrokeWidth(dp(1.2f));
                        c.drawPath(branches, stroke);
                    }
                }
            }

            float windSlant = dp(6.0f);
            stroke.setStyle(Paint.Style.STROKE);

            // Layer 1: Distant fine rain mist
            stroke.setStrokeWidth(dp(0.75f));
            stroke.setColor(isNightTheme() ? 0x2A80D4FF : 0x3855A2DE);
            float mistLen = dp(9f);
            for (int i = 0; i < MIST_COUNT; i++) {
                float progress = ((now * mistSpeed[i] * 0.0007f) + mistY[i]) % 1.0f;
                float px = (mistX[i] * w + (now * 0.00010f * w)) % w;
                float py = progress * h;
                c.drawLine(px, py, px - windSlant * 0.6f, py + mistLen, stroke);
            }

            // Layer 2: Mid-ground raindrops
            stroke.setStrokeWidth(dp(1.2f));
            stroke.setColor(isNightTheme() ? 0x55AEE5FF : 0x6568BAF0);
            float midLen = dp(14f);
            for (int i = 0; i < RAIN_COUNT / 2; i++) {
                float progress = ((now * rainSpeed[i] * 0.0011f) + rainY[i]) % 1.0f;
                float px = (rainX[i] * w + (now * 0.00018f * w)) % w;
                float py = progress * h;
                c.drawLine(px, py, px - windSlant * 0.85f, py + midLen, stroke);
            }

            // Layer 3: Foreground bright raindrops & surface splashes
            stroke.setStrokeWidth(dp(1.5f));
            stroke.setColor(isNightTheme() ? 0x88D6F2FF : 0x9078C8F8);
            float fgLen = dp(19f);
            for (int i = RAIN_COUNT / 2; i < RAIN_COUNT; i++) {
                float progress = ((now * rainSpeed[i] * 0.0015f) + rainY[i]) % 1.0f;
                float px = (rainX[i] * w + (now * 0.00028f * w)) % w;
                float py = progress * h;
                c.drawLine(px, py, px - windSlant, py + fgLen, stroke);

                // Realistic splash ripples on bottom ground/glass
                if (py > h * 0.82f && (i % 2 == 0)) {
                    stroke.setStrokeWidth(dp(0.9f));
                    stroke.setColor(0x388AD4FF);
                    float rippleR = dp(4.0f) * ((py - h * 0.82f) / (h * 0.18f));
                    c.drawOval(new RectF(px - rippleR * 2.2f, py - rippleR * 0.5f, px + rippleR * 2.2f, py + rippleR * 0.5f), stroke);
                    stroke.setStrokeWidth(dp(1.5f));
                }
            }
            postInvalidateDelayed(33L);
            return;
        }

        // 2. Realistic Fog / Dust / Haze (codes 45, 48)
        if (code == 45 || code == 48) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(isNightTheme() ? 0x18FFD54F : 0x20FFE082);
            c.drawRect(0, 0, w, h, p);
            float mistDrift = (now * 0.00004f * w) % (w * 1.5f) - w * 0.25f;
            p.setColor(0x12FFF8E1);
            c.drawCircle(mistDrift, h * 0.25f, h * 0.35f, p);
            c.drawCircle(mistDrift + w * 0.5f, h * 0.30f, h * 0.40f, p);
            postInvalidateDelayed(33L);
            return;
        }

        // 3. Realistic Volumetric Clouds (codes 1, 2, 3) - 3D Puffy Clouds with sun highlights and soft depth
        if (code >= 1 && code <= 3) {
            p.setStyle(Paint.Style.FILL);
            float drift1 = (now * 0.000022f * w) % (w * 1.6f) - w * 0.3f;
            float drift2 = ((now * 0.000016f * w) + w * 0.55f) % (w * 1.6f) - w * 0.3f;

            // Ambient translucent cloud body
            int bodyColor = isNightTheme() ? 0x144A6573 : 0x1C7A9DAE;
            int shadowColor = isNightTheme() ? 0x102E424C : 0x15456272;
            int highlightColor = isNightTheme() ? 0x1E708E9D : 0x30FFF8E7;

            // Cloud Cluster 1
            // Shaded base
            p.setColor(shadowColor);
            c.drawCircle(drift1 + h * 0.14f, h * 0.13f, h * 0.17f, p);
            // Main body
            p.setColor(bodyColor);
            c.drawCircle(drift1, h * 0.11f, h * 0.15f, p);
            c.drawCircle(drift1 + h * 0.13f, h * 0.09f, h * 0.18f, p);
            c.drawCircle(drift1 + h * 0.26f, h * 0.12f, h * 0.14f, p);
            // Sunlit rim highlight on top
            p.setColor(highlightColor);
            c.drawCircle(drift1 + h * 0.13f, h * 0.075f, h * 0.13f, p);

            // Cloud Cluster 2
            p.setColor(shadowColor);
            c.drawCircle(drift2 + h * 0.15f, h * 0.18f, h * 0.19f, p);
            p.setColor(bodyColor);
            c.drawCircle(drift2, h * 0.16f, h * 0.16f, p);
            c.drawCircle(drift2 + h * 0.15f, h * 0.14f, h * 0.19f, p);
            c.drawCircle(drift2 + h * 0.30f, h * 0.17f, h * 0.15f, p);
            p.setColor(highlightColor);
            c.drawCircle(drift2 + h * 0.15f, h * 0.125f, h * 0.14f, p);

            postInvalidateDelayed(33L);
            return;
        }

        // 4. Realistic Clear Sky (Day = Radiant VR Sun & Floating Golden Motes, Night = Twinkling Stars & Shooting Star)
        if (day) {
            // Sun location (discrete corner glow)
            float sunCx = panelSwapped ? (w * 0.90f) : (w * 0.10f);
            float sunCy = h * 0.06f;
            float pulse = (float) Math.sin(now * 0.0016) * dp(6f);

            // Radiant corona glow (smooth multi-layer concentric disks without harsh line stripes)
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x06FFE89E);
            c.drawCircle(sunCx, sunCy, h * 0.45f + pulse, p);
            p.setColor(0x0EFFF0B8);
            c.drawCircle(sunCx, sunCy, h * 0.30f + pulse * 0.6f, p);
            p.setColor(0x1CFFE082);
            c.drawCircle(sunCx, sunCy, h * 0.18f + pulse * 0.3f, p);
            p.setColor(0x35FFF9C4);
            c.drawCircle(sunCx, sunCy, h * 0.09f, p);
            p.setColor(0xEEFFFDF5);
            c.drawCircle(sunCx, sunCy, h * 0.038f, p);

            // Floating Golden Solar Motes (ذرات ضوئية ذهبية تسبح في أشعة الشمس - واقع افتراضي)
            for (int i = 0; i < SUN_MOTE_COUNT; i++) {
                float mx = (sunMoteX[i] * w + (float) Math.cos(now * 0.0006 + sunMotePhase[i]) * dp(14f)) % w;
                float my = sunMoteY[i] * h + (float) Math.sin(now * 0.0012 + sunMotePhase[i]) * dp(12f);
                float mPulse = (float) (0.40 + 0.60 * Math.sin(now * 0.0020 + sunMotePhase[i]));
                int mAlpha = (int) (mPulse * 65);
                p.setColor((mAlpha << 24) | 0xFFE082);
                c.drawCircle(mx, my, dp(1.8f), p);
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
        postInvalidateDelayed(33L);
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

        // Top-left brand/logo touch area opens settings (convenient direct access)
        topBarSettingsRect.set(0, 0, Math.max(textX + minDim * .20f, dp(180)), Math.max(logoCy + logoR * 1.6f, dp(58)));

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
        boolean overLimit = showSpeed && isOverSpeedLimit();
        drawInfoCard(c, x, yTop + 2 * (cardH + gap), cardW, cardH,
                showSpeed
                        ? (overLimit ? (englishMode ? "⚠️ Speed Limit" : "⚠️ تجاوز السرعة") : (englishMode ? "Speed" : "السرعة"))
                        : (englishMode ? "GPS Accuracy" : "دقة GPS"),
                showSpeed ? ((overLimit ? "🚨 " : "") + formatSpeedDisplay()) : formatAccuracy(),
                showSpeed ? (overLimit ? "🚨" : "🚗") : "◎",
                overLimit ? 0xFFFF5252 : prayerAccentColor());

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

        // Card 6 (Below Weather): Quick App Launcher Button
        float appsBtnY = wCardY + cardH + gap;
        float maxAppsH = (h - dp(66)) - appsBtnY;
        float appsBtnH = Math.max(dp(34), Math.min(cardH * 0.78f, maxAppsH));
        quickAppsBtnRect.set(x, appsBtnY, x + cardW, appsBtnY + appsBtnH);
        drawQuickAppsButton(c, x, appsBtnY, cardW, appsBtnH);
    }

    private void drawPairedBottomQuickAppsButton(Canvas c, float w, float h) {
        float btnGap = dp(10);
        float btnW = Math.min(dp(150), (w - dp(34)) * 0.48f);
        float btnH = dp(46);
        float totalW = btnW * 2f + btnGap;
        float startX = (w - totalW) * 0.5f;
        float btnX = startX + btnW + btnGap;
        float btnY = h - dp(12) - btnH;
        quickAppsBtnRect.set(btnX, btnY, btnX + btnW, btnY + btnH);
        drawQuickAppsButton(c, btnX, btnY, btnW, btnH);
    }

    private void drawQuickAppsButton(Canvas c, float x, float y, float bw, float bh) {
        RectF r = new RectF(x, y, x + bw, y + bh);
        float radius = Math.min(dp(18), bh * 0.45f);
        boolean night = isNightTheme();
        int gold = goldColor();
        int accent = prayerAccentColor();

        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(
                x, y, x + bw, y + bh,
                night ? new int[]{0xE80A2622, 0xEE06151C}
                      : new int[]{0xF5FFFFFF, 0xF0DCECF2},
                null, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, radius, radius, p);
        p.setShader(null);

        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(1.4f));
        stroke.setShader(new LinearGradient(
                x, y, x + bw, y,
                new int[]{gold, accent}, null, Shader.TileMode.CLAMP));
        c.drawRoundRect(r, radius, radius, stroke);
        stroke.setShader(null);

        p.setTextAlign(Paint.Align.CENTER);
        ts(Math.min(dp(14.5f), bh * 0.37f));
        p.setFakeBoldText(true);
        p.setColor(night ? gold : 0xFF0A3A44);
        String label = englishMode ? "🚀 Quick Apps" : "🚀 التطبيقات";
        Paint.FontMetrics fm = p.getFontMetrics();
        float textY = r.centerY() - (fm.ascent + fm.descent) * 0.5f;
        drawFittedText(c, label, r.centerX(), textY, bw * 0.88f);
        p.setFakeBoldText(false);
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

    public int getDialMode() {
        return dialMode;
    }

    public void setDialMode(int newMode) {
        saveDialMode(newMode);
        invalidate();
    }

    private void saveDialMode(int newMode) {
        dialMode = ((newMode % DIAL_FACE_COUNT) + DIAL_FACE_COUNT) % DIAL_FACE_COUNT;
        try {
            getContext().getSharedPreferences("h9_qibla_ui", Context.MODE_PRIVATE)
                    .edit()
                    .putInt("compass_dial_mode", dialMode)
                    .apply();
        } catch (Exception ignored) { }
    }

    private void drawCompass(Canvas c, float cx, float cy, float r) {
        lastCompassCx = cx;
        lastCompassCy = cy;
        lastCompassR = r;
        dialHitRect.set(cx - r * 1.22f, cy - r * 1.22f, cx + r * 1.22f, cy + r * 1.32f);

        // Smooth ease-back when snapping after a swipe
        if (!isDraggingDial && Math.abs(dialSwipeVisualOffset) > 0.5f) {
            dialSwipeVisualOffset *= 0.72f;
            if (Math.abs(dialSwipeVisualOffset) <= 0.8f) {
                dialSwipeVisualOffset = 0f;
            } else {
                postInvalidateOnAnimation();
            }
        }

        boolean aligned = isQiblaAligned();

        // Clip circular slide zone while swiping
        float shiftX = dialSwipeVisualOffset;
        if (Math.abs(shiftX) > 0.5f) {
            c.save();
            Path clipCircle = new Path();
            clipCircle.addCircle(cx, cy, r * 1.22f, Path.Direction.CW);
            c.clipPath(clipCircle);
            c.translate(shiftX, 0f);
        }

        if (dialMode == DIAL_MODE_CLOCK_SPEED) {
            drawDigitalClockSpeedDial(c, cx, cy, r, aligned);
            postInvalidateDelayed(500L);
        } else if (dialMode == DIAL_MODE_SPORT_COMPASS) {
            drawSportCompassDial(c, cx, cy, r, aligned);
        } else if (dialMode == DIAL_MODE_ANALOG_CLOCK) {
            drawAnalogClockDial(c, cx, cy, r, aligned);
            postInvalidateDelayed(50L);
        } else {
            drawQiblaCompassFace(c, cx, cy, r, aligned);
        }

        if (Math.abs(shiftX) > 0.5f) {
            c.restore();
        }

        drawDialPagerDots(c, cx, cy, r);
        drawVehicleRelativeBadge(c, cx, cy, r, aligned);
    }

    private void drawDigitalClockSpeedDial(Canvas c, float cx, float cy, float r, boolean aligned) {
        boolean night = isNightTheme();
        int gold = goldColor();
        int accent = aligned ? 0xFF00F5C8 : (night ? 0xFF38BDF8 : 0xFF0284C7);

        // 1. Soft Ambient Aura (RadialGradient glow)
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(
                cx, cy, r * 1.24f,
                new int[]{aligned ? 0x4400F5A0 : (night ? 0x3638BDF8 : 0x280284C7), 0x00000000},
                new float[]{0.55f, 1.0f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r * 1.24f, p);
        p.setShader(null);

        // 2. Luxury Brushed Champagne-Gold & Sapphire Chamfered Outer Bezel
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(r * 0.045f);
        stroke.setShader(new LinearGradient(
                cx - r, cy - r, cx + r, cy + r,
                aligned
                        ? new int[]{0xFF00F5C8, 0xFFFFD768, 0xFF00BFA5}
                        : (night ? new int[]{0xFFFFE599, 0xFF38BDF8, 0xFF6B4E16, 0xFF0EA5E9}
                                 : new int[]{0xFFD4AF37, 0xFF0284C7, 0xFF7A5618}),
                null, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r * 1.115f, stroke);
        stroke.setShader(null);

        // 3. Recessed Deep Obsidian / Pearl Dial Backing
        p.setShader(new RadialGradient(
                cx, cy - r * 0.15f, r * 1.09f,
                night ? new int[]{0xFF102636, 0xFF07131D, 0xFF03080E}
                      : new int[]{0xFFFCFEFF, 0xFFE6F1F6, 0xFFD2E4EC},
                new float[]{0.0f, 0.72f, 1.0f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r * 1.09f, p);
        p.setShader(null);

        // Subtle inner satin glass highlight ring
        stroke.setStrokeWidth(r * 0.008f);
        stroke.setColor(night ? 0x26FFFFFF : 0x55FFFFFF);
        c.drawCircle(cx, cy, r * 1.06f, stroke);

        // Inner fine champagne gold bezel track
        stroke.setStrokeWidth(r * 0.011f);
        stroke.setColor(aligned ? 0xFF00F5C8 : ((0xAA << 24) | (gold & 0x00FFFFFF)));
        c.drawCircle(cx, cy, r * 1.02f, stroke);

        // 4. Precision Chronometer Tick Track & Crimson Sport Redline
        float tickR = r * 0.945f;
        for (int i = 0; i < 60; i++) {
            double deg = 135.0 + (i / 59.0) * 270.0;
            double rad = Math.toRadians(deg);
            boolean cardinal = (i % 15 == 0);
            boolean major = (i % 5 == 0);
            boolean redline = (i >= 48);
            float len = cardinal ? r * 0.10f : (major ? r * 0.075f : r * 0.040f);
            stroke.setStrokeWidth(cardinal ? r * 0.020f : (major ? r * 0.014f : r * 0.007f));
            if (redline) {
                stroke.setColor(major ? 0xFFFF5252 : 0xAAEF4444);
            } else if (cardinal) {
                stroke.setColor(gold);
            } else {
                stroke.setColor(major ? (night ? 0xFFE2E8F0 : 0xFF1E293B) : (night ? 0x5594A3B8 : 0x6664748B));
            }
            c.drawLine(
                    cx + (float) Math.cos(rad) * (tickR - len),
                    cy + (float) Math.sin(rad) * (tickR - len),
                    cx + (float) Math.cos(rad) * tickR,
                    cy + (float) Math.sin(rad) * tickR,
                    stroke);
        }

        // 5. Glowing Gradient Progress Sweep Arc + Luminous Tip Orb
        Calendar cal = Calendar.getInstance();
        int sec = cal.get(Calendar.SECOND);
        float sweepRatio = (speedKmh > 1.5f)
                ? Math.min(1f, speedKmh / 160f)
                : (sec / 60f);
        float sweepDeg = Math.max(2f, 270f * sweepRatio);
        RectF arcOval = new RectF(cx - r * 0.965f, cy - r * 0.965f, cx + r * 0.965f, cy + r * 0.965f);

        // Faint full base arc track
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeWidth(r * 0.024f);
        stroke.setColor(night ? 0x2294A3B8 : 0x22475569);
        c.drawArc(arcOval, 135f, 270f, false, stroke);

        // Luminous active arc
        boolean overSpeed = isOverSpeedLimit();
        stroke.setShader(new SweepGradient(cx, cy,
                overSpeed ? new int[]{0xFF38BDF8, 0xFFFFB74D, 0xFFEF4444, 0xFF38BDF8}
                          : new int[]{0xFF38BDF8, 0xFF00F5C8, 0xFFFFD768, 0xFF38BDF8},
                null));
        stroke.setStrokeWidth(r * 0.030f);
        c.drawArc(arcOval, 135f, sweepDeg, false, stroke);
        stroke.setShader(null);
        stroke.setStrokeCap(Paint.Cap.BUTT);

        // Glowing leading tip orb on the arc
        double tipRad = Math.toRadians(135f + sweepDeg);
        float tipX = cx + (float) Math.cos(tipRad) * (r * 0.965f);
        float tipY = cy + (float) Math.sin(tipRad) * (r * 0.965f);
        p.setColor(overSpeed ? 0xFFFF5252 : accent);
        p.setShadowLayer(10f, 0, 0, overSpeed ? 0xAAFF5252 : 0xBB00F5C8);
        c.drawCircle(tipX, tipY, r * 0.028f, p);
        p.clearShadowLayer();

        // Subtle inner chronograph ring for crisp depth
        stroke.setStrokeWidth(r * 0.005f);
        stroke.setColor(night ? 0x26FFD768 : 0x260284C7);
        c.drawCircle(cx, cy, r * 0.72f, stroke);

        // 6. Luxurious Qibla Rim Chevron Beacon orbiting the gold track
        if (hasLocation) {
            float qAngle = hasHeading ? normalize(qiblaBearing - heading) : qiblaBearing;
            double qr = Math.toRadians(qAngle - 90f);
            float qx = cx + (float) Math.cos(qr) * (r * 1.02f);
            float qy = cy + (float) Math.sin(qr) * (r * 1.02f);
            p.setColor(aligned ? 0xFF00F5C8 : 0xFFFFD768);
            p.setShadowLayer(14f, 0, 0, aligned ? 0xCC00F5C8 : 0xAAFFD768);
            c.drawCircle(qx, qy, r * 0.056f, p);
            p.clearShadowLayer();
            p.setColor(night ? 0xFF07131D : 0xFFFFFFFF);
            c.drawCircle(qx, qy, r * 0.022f, p);
        }

        // 7. Crisp Luxury Digital 12-Hour Time + Champagne Pill Badge
        int h24 = cal.get(Calendar.HOUR_OF_DAY);
        int h12 = h24 % 12;
        if (h12 == 0) h12 = 12;
        int min = cal.get(Calendar.MINUTE);
        String amPm = englishMode ? (h24 >= 12 ? "PM" : "AM") : (h24 >= 12 ? "م" : "ص");
        String timeMain = String.format(Locale.US, "%d:%02d", h12, min);
        String secStr = String.format(Locale.US, ":%02d  •  %s", sec, amPm);

        p.setTextAlign(Paint.Align.CENTER);
        p.setFakeBoldText(true);
        p.setColor(night ? 0xFFFFFFFF : 0xFF0B1F2A);
        p.setShadowLayer(12f, 0, 2f, night ? 0x6638BDF8 : 0x220284C7);
        ts(r * 0.37f);
        c.drawText(timeMain, cx, cy - r * 0.07f, p);
        p.clearShadowLayer();

        // Seconds + AM/PM Pill
        RectF secPill = new RectF(cx - r * 0.34f, cy + r * 0.03f, cx + r * 0.34f, cy + r * 0.21f);
        p.setColor(night ? 0x66122B3B : 0xAAEBF4F8);
        c.drawRoundRect(secPill, secPill.height() * 0.5f, secPill.height() * 0.5f, p);
        stroke.setStrokeWidth(r * 0.006f);
        stroke.setColor((0x66 << 24) | (gold & 0x00FFFFFF));
        c.drawRoundRect(secPill, secPill.height() * 0.5f, secPill.height() * 0.5f, stroke);

        ts(r * 0.105f);
        p.setColor(gold);
        Paint.FontMetrics sFm = p.getFontMetrics();
        c.drawText(secStr, cx, secPill.centerY() - (sFm.ascent + sFm.descent) * 0.5f, p);

        // 8. Recessed Telemetry Instrument Pill (Speed KM/H or Next Prayer)
        String subLine;
        int subColor;
        if (speedKmh > 1.5f) {
            float disp = speedMph ? (speedKmh * 0.621371f) : speedKmh;
            String unitStr = speedMph ? "MPH" : (englishMode ? "KM/H" : "كم/س");
            subLine = String.format(Locale.US, (overSpeed ? "🚨 " : "🚗 ") + "%.0f %s", disp, unitStr);
            subColor = overSpeed ? 0xFFFF5252 : (night ? 0xFF00F5C8 : 0xFF00695C);
        } else if (hasPrayerTimes) {
            int nextIdx = getNextPrayerIndex();
            String[] pNames = activePrayerNames();
            String pLabel = (nextIdx >= 0 && nextIdx < pNames.length) ? pNames[nextIdx] : "";
            String pTime = (nextIdx >= 0 && nextIdx < prayerTimes.length) ? format12Hour(prayerTimes[nextIdx]) : "";
            subLine = "🕌 " + pLabel + "  •  " + pTime;
            subColor = night ? 0xFFFDE68A : 0xFF8A5A00;
        } else {
            subLine = speedMph ? "0 MPH" : (englishMode ? "0 KM/H" : "0 كم/س");
            subColor = secondaryTextColor();
        }
        RectF telPill = new RectF(cx - r * 0.58f, cy + r * 0.29f, cx + r * 0.58f, cy + r * 0.50f);
        p.setColor(overSpeed ? 0xCC3B0D0D : (night ? 0xAA0A1D29 : 0xCCFFFFFF));
        c.drawRoundRect(telPill, telPill.height() * 0.48f, telPill.height() * 0.48f, p);
        stroke.setStrokeWidth(r * (overSpeed ? 0.012f : 0.007f));
        stroke.setColor(overSpeed ? 0xFFFF5252 : (speedKmh > 1.5f ? (night ? 0x8800F5C8 : 0x88007A6C) : (night ? 0x77FFD768 : 0x66B88E3E)));
        c.drawRoundRect(telPill, telPill.height() * 0.48f, telPill.height() * 0.48f, stroke);

        ts(r * 0.105f);
        p.setColor(subColor);
        Paint.FontMetrics tFm = p.getFontMetrics();
        drawFittedText(c, subLine, cx, telPill.centerY() - (tFm.ascent + tFm.descent) * 0.5f, telPill.width() * 0.88f);
        p.setFakeBoldText(false);
    }

    private void drawSportCompassDial(Canvas c, float cx, float cy, float r, boolean aligned) {
        boolean night = isNightTheme();
        int gold = goldColor();

        // 1. Soft Radial Aura
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(
                cx, cy, r * 1.24f,
                new int[]{aligned ? 0x4400F5A0 : (night ? 0x340EA5E9 : 0x240284C7), 0x00000000},
                new float[]{0.55f, 1.0f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r * 1.24f, p);
        p.setShader(null);

        // 2. Brushed Metallic & Gold Outer Bezel matching Face 0 & Face 1
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(r * 0.045f);
        stroke.setShader(new LinearGradient(
                cx - r, cy - r, cx + r, cy + r,
                aligned
                        ? new int[]{0xFF00F5C8, 0xFFFFD768, 0xFF00BFA5}
                        : (night ? new int[]{0xFF7DD3FC, 0xFFFFE599, 0xFF1E3A5F, 0xFF38BDF8}
                                 : new int[]{0xFF0284C7, 0xFFD4AF37, 0xFF0C4A6E}),
                null, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r * 1.115f, stroke);
        stroke.setShader(null);

        // 3. Recessed Radial Instrument Dial Surface
        p.setShader(new RadialGradient(
                cx, cy - r * 0.15f, r * 1.09f,
                night ? new int[]{0xFF0F2433, 0xFF07121B, 0xFF03080D}
                      : new int[]{0xFFFCFEFF, 0xFFE6F1F6, 0xFFD2E4EC},
                new float[]{0.0f, 0.72f, 1.0f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r * 1.09f, p);
        p.setShader(null);

        // Gold chamfer inner track
        stroke.setStrokeWidth(r * 0.010f);
        stroke.setColor(aligned ? 0xFF00F5C8 : ((0x99 << 24) | (gold & 0x00FFFFFF)));
        c.drawCircle(cx, cy, r * 1.02f, stroke);

        float rot = hasHeading ? -heading : 0f;
        c.save();
        c.rotate(rot, cx, cy);

        // 4. Illuminated Rounded Directional Arcs (Sapphire North, Ruby South)
        RectF ringOval = new RectF(cx - r * 0.91f, cy - r * 0.91f, cx + r * 0.91f, cy + r * 0.91f);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeWidth(r * 0.052f);
        stroke.setColor(0xBB0EA5E9);
        stroke.setShadowLayer(12f, 0, 0, 0x8838BDF8);
        c.drawArc(ringOval, -132f, 84f, false, stroke); // North Sapphire Glow Arc
        stroke.setColor(0xBBEF4444);
        stroke.setShadowLayer(12f, 0, 0, 0x88EF4444);
        c.drawArc(ringOval, 48f, 84f, false, stroke);   // South Ruby Glow Arc
        stroke.clearShadowLayer();
        stroke.setStrokeCap(Paint.Cap.BUTT);

        // Fine interior champagne-gold compass rose ring & 4-axis crosshair watermark
        stroke.setStrokeWidth(r * 0.006f);
        stroke.setColor(night ? 0x33FFD768 : 0x33B88E3E);
        c.drawCircle(cx, cy, r * 0.50f, stroke);
        c.drawLine(cx, cy - r * 0.48f, cx, cy + r * 0.48f, stroke);
        c.drawLine(cx - r * 0.48f, cy, cx + r * 0.48f, cy, stroke);

        // Rotating 360° numeric scale & ticks (every 10° tick, every 20° numeric label)
        for (int deg = 0; deg < 360; deg += 5) {
            double a = Math.toRadians(deg - 90);
            boolean major = (deg % 20 == 0);
            boolean med = (deg % 10 == 0);
            float len = major ? r * 0.080f : (med ? r * 0.048f : r * 0.025f);
            stroke.setStrokeWidth(major ? r * 0.012f : r * 0.006f);
            stroke.setColor(major ? (night ? 0xFFF8FAFC : 0xFF0F172A) : (night ? 0x7794A3B8 : 0x7764748B));
            c.drawLine(
                    cx + (float) Math.cos(a) * (r * 1.02f - len),
                    cy + (float) Math.sin(a) * (r * 1.02f - len),
                    cx + (float) Math.cos(a) * (r * 1.02f),
                    cy + (float) Math.sin(a) * (r * 1.02f),
                    stroke);
            if (deg % 40 == 0) {
                float tr = r * 0.80f;
                p.setStyle(Paint.Style.FILL);
                p.setTextAlign(Paint.Align.CENTER);
                ts(r * 0.072f);
                p.setColor(night ? 0xCCCBD5E1 : 0xCC334155);
                Paint.FontMetrics fm = p.getFontMetrics();
                c.drawText(String.valueOf(deg),
                        cx + (float) Math.cos(a) * tr,
                        cy + (float) Math.sin(a) * tr - (fm.ascent + fm.descent) * 0.5f, p);
            }
        }

        drawCardinal(c, "N", cx, cy, r * 0.61f, 0, 0xFF38BDF8);
        drawCardinal(c, "NE", cx, cy, r * 0.61f, 45, secondaryTextColor());
        drawCardinal(c, "E", cx, cy, r * 0.61f, 90, gold);
        drawCardinal(c, "SE", cx, cy, r * 0.61f, 135, secondaryTextColor());
        drawCardinal(c, "S", cx, cy, r * 0.61f, 180, 0xFFEF4444);
        drawCardinal(c, "SW", cx, cy, r * 0.61f, 225, secondaryTextColor());
        drawCardinal(c, "W", cx, cy, r * 0.61f, 270, gold);
        drawCardinal(c, "NW", cx, cy, r * 0.61f, 315, secondaryTextColor());

        c.restore();

        // 5. Golden Qibla Rim Beacon orbiting outer track
        if (hasLocation) {
            float arrow = hasHeading ? normalize(qiblaBearing - heading) : qiblaBearing;
            double qr = Math.toRadians(arrow - 90f);
            float qx = cx + (float) Math.cos(qr) * (r * 1.02f);
            float qy = cy + (float) Math.sin(qr) * (r * 1.02f);
            p.setStyle(Paint.Style.FILL);
            p.setColor(aligned ? 0xFF00F5C8 : 0xFFFFD768);
            p.setShadowLayer(14f, 0, 0, aligned ? 0xCC00F5C8 : 0xAAFFD768);
            c.drawCircle(qx, qy, r * 0.058f, p);
            p.clearShadowLayer();
            p.setColor(night ? 0xFF07131D : 0xFFFFFFFF);
            c.drawCircle(qx, qy, r * 0.022f, p);
        }

        // 6. Sculpted 3D-Beveled Center Metallic Droplet Bearing Hub
        float hubR = r * 0.255f;
        Path dropletLeft = new Path();
        dropletLeft.moveTo(cx, cy - hubR * 1.82f);
        dropletLeft.lineTo(cx - hubR * 0.90f, cy - hubR * 0.22f);
        dropletLeft.lineTo(cx, cy - hubR * 0.22f);
        dropletLeft.close();

        Path dropletRight = new Path();
        dropletRight.moveTo(cx, cy - hubR * 1.82f);
        dropletRight.lineTo(cx + hubR * 0.90f, cy - hubR * 0.22f);
        dropletRight.lineTo(cx, cy - hubR * 0.22f);
        dropletRight.close();

        p.setStyle(Paint.Style.FILL);
        p.setShadowLayer(14f, 0, 4f, night ? 0x99000000 : 0x44000000);
        p.setColor(night ? 0xFFF8FAFC : 0xFF334155);
        c.drawPath(dropletLeft, p);
        p.setColor(night ? 0xFF94A3B8 : 0xFF1E293B);
        c.drawPath(dropletRight, p);

        // Metallic Hub Outer Ring
        p.setShader(new LinearGradient(cx - hubR, cy - hubR, cx + hubR, cy + hubR,
                night ? new int[]{0xFFFFE599, 0xFF64748B, 0xFF1E293B}
                      : new int[]{0xFFD4AF37, 0xFF475569, 0xFF1E293B},
                null, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, hubR, p);
        p.setShader(null);
        p.clearShadowLayer();

        // Recessed Inner Glass Hub
        p.setShader(new RadialGradient(cx, cy - hubR * 0.18f, hubR * 0.84f,
                night ? new int[]{0xFF132A3A, 0xFF07131C}
                      : new int[]{0xFFFFFFFF, 0xFFE2EFF5},
                null, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, hubR * 0.82f, p);
        p.setShader(null);

        stroke.setStrokeWidth(r * 0.008f);
        stroke.setColor((0x88 << 24) | (gold & 0x00FFFFFF));
        c.drawCircle(cx, cy, hubR * 0.82f, stroke);

        String[] cardinals = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        String cardLabel = hasHeading ? cardinals[Math.round(normalize(heading) / 45f) % 8] : "—";
        p.setTextAlign(Paint.Align.CENTER);
        p.setFakeBoldText(true);
        p.setColor(night ? 0xFFFFFFFF : 0xFF0F172A);
        ts(hubR * 0.86f);
        Paint.FontMetrics fm = p.getFontMetrics();
        c.drawText(cardLabel, cx, cy - (fm.ascent + fm.descent) * 0.45f, p);

        if (hasHeading) {
            RectF degPill = new RectF(cx - r * 0.22f, cy + hubR * 1.15f, cx + r * 0.22f, cy + hubR * 1.72f);
            p.setColor(night ? 0xCC0A1D29 : 0xE6FFFFFF);
            c.drawRoundRect(degPill, degPill.height() * 0.5f, degPill.height() * 0.5f, p);
            stroke.setStrokeWidth(r * 0.006f);
            stroke.setColor(aligned ? 0x9900F5C8 : 0x8838BDF8);
            c.drawRoundRect(degPill, degPill.height() * 0.5f, degPill.height() * 0.5f, stroke);

            ts(r * 0.088f);
            p.setColor(aligned ? 0xFF00F5C8 : (night ? 0xFF38BDF8 : 0xFF0284C7));
            Paint.FontMetrics dfm = p.getFontMetrics();
            c.drawText(String.format(Locale.US, "%.0f°", normalize(heading)),
                    cx, degPill.centerY() - (dfm.ascent + dfm.descent) * 0.5f, p);
        }
        p.setFakeBoldText(false);
    }

    private void drawAnalogClockDial(Canvas c, float cx, float cy, float r, boolean aligned) {
        boolean night = isNightTheme();
        int gold = goldColor();

        // 1. Soft Luminous Radial Aura
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(
                cx, cy, r * 1.25f,
                new int[]{aligned ? 0x4E00F5A0 : (night ? 0x34FFD768 : 0x2400757D), 0x00000000},
                new float[]{0.54f, 1.0f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r * 1.25f, p);
        p.setShader(null);

        // 2. Luxury Brushed Champagne-Gold & Platinum Chronometer Bezel
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(r * 0.048f);
        stroke.setShader(new LinearGradient(
                cx - r, cy - r, cx + r, cy + r,
                aligned
                        ? new int[]{0xFF00F5C8, 0xFFFFE599, 0xFF00BFA5, 0xFFFFD768}
                        : (night ? new int[]{0xFFFFE7A1, 0xFFD4AF37, 0xFF6B4E16, 0xFFE8BC52}
                                 : new int[]{0xFFE6C25A, 0xFF8C641C, 0xFFCFA33B, 0xFF5A4010}),
                null, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r * 1.122f, stroke);
        stroke.setShader(null);

        // Outer platinum hairline rim
        stroke.setStrokeWidth(r * 0.008f);
        stroke.setColor(aligned ? 0xAA00F5C8 : (night ? 0x55FFF3C4 : 0x55FFFFFF));
        c.drawCircle(cx, cy, r * 1.148f, stroke);

        // 3. Recessed Sunburst Watch Dial Face
        p.setShader(new RadialGradient(
                cx, cy - r * 0.14f, r * 1.095f,
                dialBackdropGradientColors(),
                new float[]{0.0f, 0.72f, 1.0f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r * 1.095f, p);
        p.setShader(null);

        // Satin glass rim + inner gold bezel track
        stroke.setStrokeWidth(r * 0.009f);
        stroke.setColor(night ? 0x26FFFFFF : 0x66FFFFFF);
        c.drawCircle(cx, cy, r * 1.065f, stroke);

        stroke.setStrokeWidth(r * 0.011f);
        stroke.setColor(aligned ? 0xFF00F5C8 : ((0xBB << 24) | (gold & 0x00FFFFFF)));
        if (aligned) {
            stroke.setShadowLayer(16f, 0, 0, 0xAA00F5C8);
        }
        c.drawCircle(cx, cy, r * 1.015f, stroke);
        stroke.clearShadowLayer();

        // Fine interior horological guilloche rings
        stroke.setStrokeWidth(r * 0.005f);
        stroke.setColor(night ? 0x24FFD768 : 0x22006B78);
        c.drawCircle(cx, cy, r * 0.855f, stroke);
        c.drawCircle(cx, cy, r * 0.565f, stroke);

        // 4. 60-Tick Precision Minute Track & Sculpted Hour Indices
        float outerTickR = r * 0.992f;
        for (int i = 0; i < 60; i++) {
            double rad = Math.toRadians(i * 6.0 - 90.0);
            boolean quarter = (i % 15 == 0);
            boolean hourMark = (i % 5 == 0);
            float tickLen = quarter ? r * 0.105f : (hourMark ? r * 0.082f : r * 0.036f);
            stroke.setStrokeWidth(quarter ? r * 0.024f : (hourMark ? r * 0.016f : r * 0.006f));
            if (quarter) {
                stroke.setColor(aligned ? 0xFF00F5C8 : gold);
            } else if (hourMark) {
                stroke.setColor(night ? 0xFFFFF4D2 : 0xFF1E293B);
            } else {
                stroke.setColor(night ? 0x5594A3B8 : 0x6664748B);
            }
            c.drawLine(
                    cx + (float) Math.cos(rad) * (outerTickR - tickLen),
                    cy + (float) Math.sin(rad) * (outerTickR - tickLen),
                    cx + (float) Math.cos(rad) * outerTickR,
                    cy + (float) Math.sin(rad) * outerTickR,
                    stroke);
        }

        // Hour Numerals (12, 3, 6, 9) + Golden Diamond Pips at intermediate hours
        float numR = r * 0.745f;
        p.setTextAlign(Paint.Align.CENTER);
        p.setFakeBoldText(true);
        for (int h = 1; h <= 12; h++) {
            double rad = Math.toRadians(h * 30.0 - 90.0);
            float hx = cx + (float) Math.cos(rad) * numR;
            float hy = cy + (float) Math.sin(rad) * numR;
            if (h % 3 == 0) {
                ts(r * 0.145f);
                p.setColor(h == 12 ? (aligned ? 0xFF00F5C8 : gold) : (night ? 0xFFF8FAFC : 0xFF0F172A));
                Paint.FontMetrics fm = p.getFontMetrics();
                c.drawText(String.valueOf(h), hx, hy - (fm.ascent + fm.descent) * 0.5f, p);
            } else {
                p.setColor(night ? 0x88FFD768 : 0x888C641C);
                c.drawCircle(hx, hy, r * 0.016f, p);
            }
        }
        p.setFakeBoldText(false);

        // 5. Clean Swiss Watch Time State (No clutter pills inside the analog face)
        Calendar cal = Calendar.getInstance();
        int h12 = cal.get(Calendar.HOUR);
        int min = cal.get(Calendar.MINUTE);
        int sec = cal.get(Calendar.SECOND);
        int ms = cal.get(Calendar.MILLISECOND);

        // 6. Orbiting Qibla Beacon Jewel on Inner Gold Ring
        if (hasLocation) {
            float qAngle = hasHeading ? normalize(qiblaBearing - heading) : qiblaBearing;
            double qr = Math.toRadians(qAngle - 90f);
            float qx = cx + (float) Math.cos(qr) * (r * 1.015f);
            float qy = cy + (float) Math.sin(qr) * (r * 1.015f);
            p.setColor(aligned ? 0xFF00F5C8 : 0xFFFFD768);
            p.setShadowLayer(14f, 0, 0, aligned ? 0xCC00F5C8 : 0xAAFFD768);
            c.drawCircle(qx, qy, r * 0.048f, p);
            p.clearShadowLayer();
            p.setColor(night ? 0xFF061712 : 0xFFFFFFFF);
            c.drawCircle(qx, qy, r * 0.019f, p);
        }

        // 7. Continuous Smooth Analog Watch Hands (3D Two-Tone Dauphine + Sweep Second)
        float secFrac = sec + ms / 1000f;
        float minFrac = min + secFrac / 60f;
        float hourFrac = h12 + minFrac / 60f;

        float hourDeg = hourFrac * 30f;
        float minDeg = minFrac * 6f;
        float secDeg = secFrac * 6f;

        // Hour Hand (Broad Champagne-Gold Dauphine Sword)
        drawDauphineHand(c, cx, cy, hourDeg, r * 0.52f, r * 0.13f, r * 0.045f,
                night ? 0xFFFFE599 : 0xFFD4AF37,
                night ? 0xFFA67C22 : 0xFF7A5618,
                night ? 0xFFFFFFFF : 0xFFFFF8E1);

        // Minute Hand (Longer Sleek Ivory-Gold Dauphine Sword)
        drawDauphineHand(c, cx, cy, minDeg, r * 0.77f, r * 0.15f, r * 0.032f,
                night ? 0xFFFFFFFF : 0xFF1E293B,
                night ? 0xFFCBD5E1 : 0xFF0F172A,
                gold);

        // Sweep Second Hand (Ruby Crimson / Emerald Turquoise when Qibla aligned)
        c.save();
        c.rotate(secDeg, cx, cy);
        int secColor = aligned ? 0xFF00F5C8 : 0xFFFF5252;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeWidth(r * 0.012f);
        stroke.setColor(secColor);
        stroke.setShadowLayer(8f, 0, 0, aligned ? 0x9900F5C8 : 0x88FF5252);
        c.drawLine(cx, cy + r * 0.21f, cx, cy - r * 0.87f, stroke);
        stroke.clearShadowLayer();
        stroke.setStrokeCap(Paint.Cap.BUTT);

        // Counterweight loop on second hand tail
        p.setStyle(Paint.Style.FILL);
        p.setColor(secColor);
        c.drawCircle(cx, cy + r * 0.17f, r * 0.026f, p);
        p.setColor(night ? 0xFF07131D : 0xFFFFFFFF);
        c.drawCircle(cx, cy + r * 0.17f, r * 0.011f, p);
        c.restore();

        // 8. Sculpted Center Horological Crown Cap
        p.setStyle(Paint.Style.FILL);
        p.setShadowLayer(8f, 0, 2f, 0x88000000);
        p.setColor(gold);
        c.drawCircle(cx, cy, r * 0.054f, p);
        p.clearShadowLayer();
        p.setColor(night ? 0xFF091A24 : 0xFF0F172A);
        c.drawCircle(cx, cy, r * 0.026f, p);
        p.setColor(night ? 0xFFFFF3C4 : 0xFFFFFFFF);
        c.drawCircle(cx, cy, r * 0.011f, p);
    }

    private void drawDauphineHand(Canvas c, float cx, float cy, float deg, float tipLen, float tailLen,
                                  float halfWidth, int brightColor, int shadeColor, int lumenColor) {
        c.save();
        c.rotate(deg, cx, cy);

        Path leftFacet = new Path();
        leftFacet.moveTo(cx, cy - tipLen);
        leftFacet.lineTo(cx - halfWidth, cy);
        leftFacet.lineTo(cx, cy + tailLen);
        leftFacet.close();

        Path rightFacet = new Path();
        rightFacet.moveTo(cx, cy - tipLen);
        rightFacet.lineTo(cx + halfWidth, cy);
        rightFacet.lineTo(cx, cy + tailLen);
        rightFacet.close();

        p.setStyle(Paint.Style.FILL);
        p.setShadowLayer(10f, 0, 3f, 0x88000000);
        p.setColor(brightColor);
        c.drawPath(leftFacet, p);
        p.clearShadowLayer();

        p.setColor(shadeColor);
        c.drawPath(rightFacet, p);

        // Luminous Super-LumiNova center inlay strip
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeWidth(halfWidth * 0.38f);
        stroke.setColor(lumenColor);
        c.drawLine(cx, cy - tipLen * 0.24f, cx, cy - tipLen * 0.78f, stroke);
        stroke.setStrokeCap(Paint.Cap.BUTT);

        c.restore();
    }

    private void drawDialPagerDots(Canvas c, float cx, float cy, float r) {
        float dotsY = cy + r * 1.24f;
        float spacing = dp(13f);
        float startX = cx - ((DIAL_FACE_COUNT - 1) * 0.5f) * spacing;
        RectF capsule = new RectF(cx - dp(60f), dotsY - dp(10.5f), cx + dp(60f), dotsY + dp(10.5f));
        dialDotsHitRect.set(capsule.left - dp(10f), capsule.top - dp(8f), capsule.right + dp(10f), capsule.bottom + dp(8f));

        // Frosted Luxury Glass & Champagne-Gold Pill Capsule
        p.setStyle(Paint.Style.FILL);
        p.setColor(isNightTheme() ? 0xB8091B24 : 0xCCF4F9FB);
        c.drawRoundRect(capsule, capsule.height() * 0.5f, capsule.height() * 0.5f, p);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(1.0f));
        stroke.setColor(isNightTheme() ? 0x55FFD768 : 0x55B88E3E);
        c.drawRoundRect(capsule, capsule.height() * 0.5f, capsule.height() * 0.5f, stroke);

        // Subtle left/right chevrons to hint swipe interaction
        p.setTextAlign(Paint.Align.CENTER);
        ts(dp(10.5f));
        p.setColor(goldColor());
        Paint.FontMetrics fm = p.getFontMetrics();
        float chY = dotsY - (fm.ascent + fm.descent) * 0.5f;
        c.drawText("‹", cx - dp(45f), chY, p);
        c.drawText("›", cx + dp(45f), chY, p);

        for (int i = 0; i < DIAL_FACE_COUNT; i++) {
            float dx = startX + i * spacing;
            boolean active = (i == dialMode);
            if (active) {
                p.setColor(goldColor());
                p.setShadowLayer(8f, 0, 0, 0x88FFD768);
                c.drawRoundRect(new RectF(dx - dp(7.0f), dotsY - dp(3.0f), dx + dp(7.0f), dotsY + dp(3.0f)),
                        dp(3.0f), dp(3.0f), p);
                p.clearShadowLayer();
            } else {
                p.setColor(isNightTheme() ? 0x55CBD5E1 : 0x55475569);
                c.drawCircle(dx, dotsY, dp(2.8f), p);
            }
        }
    }

    private int[] dialBackdropGradientColors() {
        switch (getEffectiveTheme()) {
            case THEME_ROYAL_BURGUNDY:
                return new int[]{0xFF2B0915, 0xFF15040A, 0xFF090104};
            case THEME_PLATINUM_TITANIUM:
                return new int[]{0xFF202936, 0xFF10151D, 0xFF07090D};
            case THEME_OBSIDIAN:
                return new int[]{0xFF10283D, 0xFF071624, 0xFF030A12};
            case THEME_AMBER:
                return new int[]{0xFF2B1607, 0xFF160B03, 0xFF0B0501};
            case THEME_OLED_NIGHT:
                return new int[]{0xFF131313, 0xFF070707, 0xFF020202};
            case THEME_PEARL:
                return new int[]{0xFFFCFEFF, 0xFFE6F1F6, 0xFFD0E2EB};
            case THEME_EMERALD:
            default:
                return new int[]{0xFF0D2E25, 0xFF051813, 0xFF020B08};
        }
    }

    private void drawQiblaCompassFace(Canvas c, float cx, float cy, float r, boolean aligned) {
        boolean night = isNightTheme();
        int gold = goldColor();

        // 1. Soft Luminous Ambient Aura
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(
                cx, cy, r * 1.25f,
                new int[]{aligned ? 0x4E00F5A0 : (night ? 0x32FFD768 : 0x2200757D), 0x00000000},
                new float[]{0.54f, 1.0f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r * 1.25f, p);
        p.setShader(null);

        // 2. Luxury Brushed Champagne-Gold & Emerald Outer Chamfered Bezel
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(r * 0.050f);
        stroke.setShader(new LinearGradient(
                cx - r, cy - r, cx + r, cy + r,
                aligned
                        ? new int[]{0xFF00F5C8, 0xFFFFE599, 0xFF00BFA5, 0xFFFFD768}
                        : (night ? new int[]{0xFFFFE7A1, 0xFFD4AF37, 0xFF6B4E16, 0xFFE8BC52}
                                 : new int[]{0xFFE6C25A, 0xFF8C641C, 0xFFCFA33B, 0xFF5A4010}),
                null, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r * 1.125f, stroke);
        stroke.setShader(null);

        // Outer platinum specular rim
        stroke.setStrokeWidth(r * 0.008f);
        stroke.setColor(aligned ? 0xAA00F5C8 : (night ? 0x55FFF3C4 : 0x55FFFFFF));
        c.drawCircle(cx, cy, r * 1.152f, stroke);

        // 3. Recessed Deep Obsidian / Emerald-Velvet / Pearl Dial Backing
        p.setShader(new RadialGradient(
                cx, cy - r * 0.16f, r * 1.095f,
                dialBackdropGradientColors(),
                new float[]{0.0f, 0.72f, 1.0f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r * 1.095f, p);
        p.setShader(null);

        // Subtle satin glass dome reflection arc
        stroke.setStrokeWidth(r * 0.010f);
        stroke.setColor(night ? 0x28FFFFFF : 0x66FFFFFF);
        c.drawCircle(cx, cy, r * 1.065f, stroke);

        // Inner fine champagne-gold bezel track
        stroke.setStrokeWidth(r * 0.012f);
        stroke.setColor(aligned ? 0xFF00F5C8 : ((0xBB << 24) | (gold & 0x00FFFFFF)));
        if (aligned) {
            stroke.setShadowLayer(18f, 0, 0, 0xAA00F5C8);
        }
        c.drawCircle(cx, cy, r * 1.015f, stroke);
        stroke.clearShadowLayer();

        // 4. Sacred Geometric Guilloché Inner Ring & Rosette Watermark
        stroke.setStrokeWidth(r * 0.005f);
        stroke.setColor(aligned ? 0x3300F5C8 : (night ? 0x22FFD768 : 0x22006B78));
        c.drawCircle(cx, cy, r * 0.825f, stroke);
        c.drawCircle(cx, cy, r * 0.54f, stroke);

        float rosetteOuter = r * 0.54f;
        float rosetteInner = rosetteOuter * 0.7071f;
        Path rosette = new Path();
        for (int i = 0; i < 16; i++) {
            double ang = Math.toRadians(i * 22.5);
            float rr = (i % 2 == 0) ? rosetteOuter : rosetteInner;
            float rx = cx + (float) (Math.cos(ang) * rr);
            float ry = cy + (float) (Math.sin(ang) * rr);
            if (i == 0) rosette.moveTo(rx, ry);
            else rosette.lineTo(rx, ry);
        }
        rosette.close();
        stroke.setColor(night ? 0x16FFD768 : 0x14006B78);
        c.drawPath(rosette, stroke);

        // 5. Rotating Precision Degree Scale & Cardinal Diamond Pips
        float rot = hasHeading ? -heading : 0f;
        c.save();
        c.rotate(rot, cx, cy);

        for (int deg = 0; deg < 360; deg += 5) {
            double a = Math.toRadians(deg - 90);
            boolean cardinal = deg % 90 == 0;
            boolean major = deg % 30 == 0;
            boolean medium = deg % 15 == 0;
            float len = cardinal ? r * 0.095f : (major ? r * 0.078f : (medium ? r * 0.050f : r * 0.026f));
            stroke.setStrokeWidth(cardinal ? r * 0.018f : (major ? r * 0.011f : (medium ? r * 0.007f : r * 0.0035f)));
            if (cardinal) {
                stroke.setColor(aligned ? 0xFF00F5C8 : gold);
            } else if (major) {
                stroke.setColor(night ? 0xEEFFF5D6 : 0xEE1E293B);
            } else {
                stroke.setColor(medium ? (night ? 0x88FFD768 : 0x888C641C) : (night ? 0x3E80B0B8 : 0x4564748B));
            }
            c.drawLine(cx + (float) Math.cos(a) * (r * 0.99f - len), cy + (float) Math.sin(a) * (r * 0.99f - len),
                    cx + (float) Math.cos(a) * (r * 0.99f), cy + (float) Math.sin(a) * (r * 0.99f), stroke);

            // Chamfered gold diamond pip at each cardinal 0/90/180/270
            if (cardinal) {
                float px = cx + (float) Math.cos(a) * (r * 1.052f);
                float py = cy + (float) Math.sin(a) * (r * 1.052f);
                p.setStyle(Paint.Style.FILL);
                p.setColor(deg == 0 ? (aligned ? 0xFF00F5C8 : 0xFF38BDF8) : gold);
                c.drawCircle(px, py, r * 0.020f, p);
            }
        }

        drawCardinal(c, "N", cx, cy, r * .72f, 0, aligned ? 0xFF00F5C8 : (night ? 0xFF38BDF8 : 0xFF0284C7));
        drawCardinal(c, "E", cx, cy, r * .72f, 90, gold);
        drawCardinal(c, "S", cx, cy, r * .72f, 180, night ? 0xFFF87171 : 0xFFDC2626);
        drawCardinal(c, "W", cx, cy, r * .72f, 270, gold);
        c.restore();

        // 6. Sculpted Golden Vehicle Heading Crest at 12 o'clock
        Path vehiclePip = new Path();
        vehiclePip.moveTo(cx, cy - r * 1.015f);
        vehiclePip.lineTo(cx - r * 0.042f, cy - r * 1.135f);
        vehiclePip.lineTo(cx, cy - r * 1.105f);
        vehiclePip.lineTo(cx + r * 0.042f, cy - r * 1.135f);
        vehiclePip.close();
        p.setStyle(Paint.Style.FILL);
        p.setColor(aligned ? 0xFF00F5C8 : gold);
        p.setShadowLayer(10f, 0, 0, aligned ? 0xAA00F5C8 : 0x77FFD768);
        c.drawPath(vehiclePip, p);
        p.clearShadowLayer();

        if (hasLocation) {
            float arrow = hasHeading ? normalize(qiblaBearing - heading) : qiblaBearing;
            float angle = arrow > 180 ? arrow - 360 : arrow;

            // Orbiting Qibla beacon jewel on the inner gold track
            double qr = Math.toRadians(arrow - 90f);
            float qx = cx + (float) Math.cos(qr) * (r * 1.015f);
            float qy = cy + (float) Math.sin(qr) * (r * 1.015f);
            p.setStyle(Paint.Style.FILL);
            p.setColor(aligned ? 0xFF00F5C8 : 0xFFFFD768);
            p.setShadowLayer(14f, 0, 0, aligned ? 0xCC00F5C8 : 0xAAFFD768);
            c.drawCircle(qx, qy, r * 0.042f, p);
            p.clearShadowLayer();
            p.setColor(night ? 0xFF061712 : 0xFFFFFFFF);
            c.drawCircle(qx, qy, r * 0.017f, p);

            c.save();
            c.rotate(angle, cx, cy);

            int arrowColor = aligned ? 0xFF00F5C8 : gold;
            int arrowDark = aligned ? 0xFF00A88F : (night ? 0xFF9C7220 : 0xFF7A5618);
            int shadowColor = aligned ? 0xDD00F5C8 : 0x99FFD768;

            if (needleStyle == 1) {
                // Style 1: Classic Precision Navigational Arrow (Two-Tone 3D Beveled)
                Path leftWing = new Path();
                leftWing.moveTo(cx, cy - r * 0.78f);
                leftWing.lineTo(cx - r * 0.115f, cy - r * 0.37f);
                leftWing.lineTo(cx, cy - r * 0.46f);
                leftWing.close();
                p.setStyle(Paint.Style.FILL);
                p.setColor(arrowColor);
                p.setShadowLayer(18f, 0, 0, shadowColor);
                c.drawPath(leftWing, p);

                Path rightWing = new Path();
                rightWing.moveTo(cx, cy - r * 0.78f);
                rightWing.lineTo(cx + r * 0.115f, cy - r * 0.37f);
                rightWing.lineTo(cx, cy - r * 0.46f);
                rightWing.close();
                p.setColor(arrowDark);
                c.drawPath(rightWing, p);
                p.clearShadowLayer();

                stroke.setStyle(Paint.Style.STROKE);
                stroke.setStrokeWidth(r * 0.030f);
                stroke.setColor(arrowColor);
                c.drawLine(cx, cy - r * 0.46f, cx, cy + r * 0.22f, stroke);

                Path tail = new Path();
                tail.moveTo(cx, cy + r * 0.30f);
                tail.lineTo(cx - r * 0.06f, cy + r * 0.20f);
                tail.lineTo(cx + r * 0.06f, cy + r * 0.20f);
                tail.close();
                p.setStyle(Paint.Style.FILL);
                p.setColor(arrowColor);
                c.drawPath(tail, p);

            } else if (needleStyle == 2) {
                // Style 2: Marine Compass Rose Star Needle (Two-Tone Faceted)
                Path mainStarLeft = new Path();
                mainStarLeft.moveTo(cx, cy - r * 0.78f);
                mainStarLeft.lineTo(cx - r * 0.135f, cy);
                mainStarLeft.lineTo(cx, cy);
                mainStarLeft.close();
                p.setStyle(Paint.Style.FILL);
                p.setColor(arrowColor);
                p.setShadowLayer(20f, 0, 0, shadowColor);
                c.drawPath(mainStarLeft, p);

                Path mainStarRight = new Path();
                mainStarRight.moveTo(cx, cy - r * 0.78f);
                mainStarRight.lineTo(cx + r * 0.135f, cy);
                mainStarRight.lineTo(cx, cy);
                mainStarRight.close();
                p.setColor(arrowDark);
                c.drawPath(mainStarRight, p);
                p.clearShadowLayer();

                Path southLeft = new Path();
                southLeft.moveTo(cx, cy + r * 0.36f);
                southLeft.lineTo(cx - r * 0.09f, cy);
                southLeft.lineTo(cx, cy);
                southLeft.close();
                p.setColor(night ? 0xFF476A75 : 0xFF7D9DA6);
                c.drawPath(southLeft, p);

                Path southRight = new Path();
                southRight.moveTo(cx, cy + r * 0.36f);
                southRight.lineTo(cx + r * 0.09f, cy);
                southRight.lineTo(cx, cy);
                southRight.close();
                p.setColor(night ? 0xFF284149 : 0xFF5D7A83);
                c.drawPath(southRight, p);

            } else {
                // Style 0 (Default Flagship Luxury): Sculpted Two-Tone Golden Minaret & Dome
                stroke.setStyle(Paint.Style.STROKE);
                stroke.setStrokeWidth(r * .036f);
                stroke.setShader(new LinearGradient(
                        cx - r * 0.04f, cy - r * 0.48f, cx + r * 0.04f, cy,
                        new int[]{arrowColor, arrowDark}, null, Shader.TileMode.CLAMP));
                c.drawLine(cx, cy + r * .02f, cx, cy - r * .48f, stroke);
                stroke.setShader(null);

                // Ornamented double minaret balcony
                stroke.setStrokeWidth(r * 0.018f);
                stroke.setColor(aligned ? 0xFF00F5C8 : 0xFFFFE599);
                c.drawLine(cx - r * 0.095f, cy - r * 0.48f, cx + r * 0.095f, cy - r * 0.48f, stroke);

                // Two-tone 3D beveled minaret spire
                Path domeLeft = new Path();
                domeLeft.moveTo(cx, cy - r * .78f);
                domeLeft.lineTo(cx - r * .125f, cy - r * .49f);
                domeLeft.lineTo(cx, cy - r * .49f);
                domeLeft.close();

                Path domeRight = new Path();
                domeRight.moveTo(cx, cy - r * .78f);
                domeRight.lineTo(cx + r * .125f, cy - r * .49f);
                domeRight.lineTo(cx, cy - r * .49f);
                domeRight.close();

                p.setStyle(Paint.Style.FILL);
                p.setColor(aligned ? 0xFF00F5C8 : 0xFFFFE7A1);
                p.setShadowLayer(22f, 0, 0, shadowColor);
                c.drawPath(domeLeft, p);
                p.setColor(arrowDark);
                c.drawPath(domeRight, p);

                // Golden crescent finial jewel tip
                p.setColor(aligned ? 0xFFFFFFFF : 0xFFFFF3C4);
                c.drawCircle(cx, cy - r * 0.79f, r * 0.036f, p);
                p.clearShadowLayer();
            }

            c.restore();
        }

        drawKaaba(c, cx, cy, r, aligned);
        drawCompassBottomBadge(c, cx, cy, r, aligned);
    }

    private void drawVehicleRelativeBadge(Canvas c, float cx, float cy, float r, boolean aligned) {
        if (!hasLocation || !hasHeading) return;
        String text = getVehicleRelativeQiblaText();
        if (text == null || text.isEmpty()) return;

        float h = getHeight();
        float w = getWidth();
        boolean landscape = (w / h) >= 1.35f;

        float badgeW = Math.min(w * 0.85f, Math.max(r * 1.35f, dp(150)));
        float badgeH = Math.max(r * 0.145f, dp(24));

        float badgeY;
        if (headingMode == 1 || !landscape) {
            // Standard Classic Mode OR Portrait on mobile: neatly stacked above the dial
            badgeY = Math.max(dp(36), cy - r * 1.36f);
        } else {
            // Elevated Mode (landscape car screens): raised cleanly in the top header zone
            badgeY = h * 0.032f;
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

        // Render Distance to Makkah directly below the Vehicle Qibla Badge
        if (distanceKm > 0) {
            String makkahDist = String.format(Locale.US,
                    englishMode ? "🕋 %.0f km to Makkah" : "🕋 %.0f كم إلى مكة", distanceKm);
            float distW = Math.min(badgeW * 0.84f, Math.max(r * 0.96f, dp(136)));
            float distH = Math.max(dp(18), badgeH * 0.72f);
            float distY = badgeRect.bottom + dp(4);
            RectF distRect = new RectF(cx - distW * 0.5f, distY, cx + distW * 0.5f, distY + distH);

            p.setColor(aligned ? 0xCC002B22 : (isNightTheme() ? 0xCC081820 : 0xDCE8F1F4));
            c.drawRoundRect(distRect, distH * 0.5f, distH * 0.5f, p);

            stroke.setStrokeWidth(0.9f);
            stroke.setColor(aligned ? 0x9900F5C8 : ((0x88 << 24) | (goldColor() & 0x00FFFFFF)));
            c.drawRoundRect(distRect, distH * 0.5f, distH * 0.5f, stroke);

            ts(distH * 0.60f);
            p.setColor(aligned ? 0xFF00F5C8 : goldColor());
            Paint.FontMetrics dfm = p.getFontMetrics();
            drawFittedText(c, makkahDist, cx, distRect.centerY() - (dfm.ascent + dfm.descent) * 0.5f, distW * 0.88f);
        }
        p.setFakeBoldText(false);
    }

    private void drawCompassBottomBadge(Canvas c, float cx, float cy, float r, boolean aligned) {
        if (!aligned) return;
        float textY = cy + r * 0.285f;
        float maxTextW = r * 0.82f;

        p.setTextAlign(Paint.Align.CENTER);
        ts(r * 0.070f);
        p.setFakeBoldText(true);

        String badgeText = englishMode ? "🕋 Aligned with Qibla" : "🕋 باتجاه القبلة المشرفة";
        int glowColor = isNightTheme() ? 0xFF00F5C8 : 0xFF00897B;
        p.setColor(glowColor);
        p.setShadowLayer(14f, 0, 0, isNightTheme() ? 0xDD00F5C8 : 0x88004D40);

        drawFittedText(c, badgeText, cx, textY, maxTextW);
        p.clearShadowLayer();
        p.setFakeBoldText(false);
    }

    private void drawKaaba(Canvas c, float cx, float cy, float r, boolean aligned) {
        boolean night = isNightTheme();

        // Recessed Medallion Pedestal underneath the 3D Kaaba
        float medR = r * 0.275f;
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(cx, cy - r * 0.02f, medR,
                night ? new int[]{0xFF132D26, 0xFF071511}
                      : new int[]{0xFFFFFFFF, 0xFFE2EEF3},
                null, Shader.TileMode.CLAMP));
        p.setShadowLayer(16f, 0, 4f, aligned ? 0x7700F5C8 : (night ? 0xAA000000 : 0x33000000));
        c.drawCircle(cx, cy - r * 0.01f, medR, p);
        p.setShader(null);
        p.clearShadowLayer();

        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Math.max(1.5f, r * 0.013f));
        stroke.setColor(aligned ? 0xCC00F5C8 : 0x99FFD768);
        c.drawCircle(cx, cy - r * 0.01f, medR, stroke);

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
        p.setColor(0xFF283744);
        c.drawPath(topFace, p);

        float centerMidY = midY + (midY - topY);

        Path leftFace = new Path();
        leftFace.moveTo(cx - kw, midY);
        leftFace.lineTo(cx, centerMidY);
        leftFace.lineTo(cx, botY);
        leftFace.lineTo(cx - kw, botY - (centerMidY - midY));
        leftFace.close();
        p.setColor(0xFF0E161D);
        c.drawPath(leftFace, p);

        Path rightFace = new Path();
        rightFace.moveTo(cx, centerMidY);
        rightFace.lineTo(cx + kw, midY);
        rightFace.lineTo(cx + kw, botY - (centerMidY - midY));
        rightFace.lineTo(cx, botY);
        rightFace.close();
        p.setColor(0xFF19242E);
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
        p.setColor(0xFFFFE082);
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

        // Hajar al-Aswad silver bezel corner accent
        p.setColor(0xFFE2E8F0);
        c.drawCircle(cx, botY - sideH * 0.22f, r * 0.015f, p);

        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Math.max(1.5f, r * .018f));
        stroke.setColor(0xFFFFE599);
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

        float yTop = h * .175f;
        float gap = h * .010f;

        float ch1 = h * .350f;
        float ch2 = h * .125f; // Next Prayer Card
        float ch3 = h * .145f; // Location & Status Card

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
        ts(ch1 * .078f);
        p.setFakeBoldText(true);
        float headerY = y1 + ch1 * 0.112f;
        String prayerTitle = englishMode ? "🕌 Prayer Times  🔄" : "🕌 مواقيت الصلاة  🔄";
        drawFittedText(c, prayerTitle, x + cw * 0.5f, headerY, cw * 0.90f);
        p.setFakeBoldText(false);

        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(1.1f);
        stroke.setColor(0x40FFD768);
        c.drawLine(x + padX * 0.6f, y1 + ch1 * 0.165f, x + cw - padX * 0.6f, y1 + ch1 * 0.165f, stroke);

        String[] names = activePrayerNames();
        int next = getNextPrayerIndex();
        float rowsTop = y1 + ch1 * 0.195f;
        float rowH = (ch1 * 0.770f) / 6f;

        for (int i = 0; i < 6; i++) {
            float slotTop = rowsTop + i * rowH;
            float boxTop = slotTop + rowH * 0.06f;
            float boxBottom = slotTop + rowH * 0.94f;
            float boxCy = (boxTop + boxBottom) * 0.5f;

            if (i == next) {
                p.setColor(0x28FFA726);
                c.drawRoundRect(new RectF(x + cw * .03f, boxTop, x + cw * .97f, boxBottom), 10f, 10f, p);
                stroke.setStyle(Paint.Style.STROKE);
                stroke.setStrokeWidth(1.2f);
                stroke.setColor(0xBBFFD768);
                c.drawRoundRect(new RectF(x + cw * .03f, boxTop, x + cw * .97f, boxBottom), 10f, 10f, stroke);
            }

            ts(Math.min(rowH * 0.54f, ch1 * .066f));
            Paint.FontMetrics rowFm = p.getFontMetrics();
            float ry = boxCy - (rowFm.ascent + rowFm.descent) * 0.5f;
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

            // Divider lines between prayer rows (restored with subtle elegance)
            if (i < 5 && i != next && (i + 1) != next) {
                stroke.setStyle(Paint.Style.STROKE);
                stroke.setStrokeWidth(1.0f);
                stroke.setColor(0x18FFFFFF);
                float lineY = slotTop + rowH;
                c.drawLine(x + padX * 0.8f, lineY, x + cw - padX * 0.8f, lineY, stroke);
            }
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
        String statusTag = (prayerStatus != null && !prayerStatus.trim().isEmpty())
                ? prayerStatus.trim()
                : (englishMode ? "Umm al-Qura" : "تقويم أم القرى");
        String placeLine = "📍 " + localizedLocationName() + "  •  " + statusTag;
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
        float startX = w * 0.345f;
        float endX = w * 0.655f;
        RectF barRect = new RectF(startX, barY, endX, barY + barH);
        athkarBarRect.set(barRect);

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

    private void drawPortraitBottomAthkar(Canvas c, float w, float h, float barY, float barH) {
        float pad = dp(12);
        RectF barRect = new RectF(pad, barY, w - pad, barY + barH);
        athkarBarRect.set(barRect);
        roundCard(c, barRect, 14f, 1.2f);

        p.setTextAlign(Paint.Align.CENTER);
        float maxTextSize = barH * 0.46f;
        p.setTextSize(Math.min(maxTextSize, barH * 0.42f * fontScale));
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
        int action = event.getActionMasked();

        if (hudMirrorMode && action == MotionEvent.ACTION_UP && y <= dp(50)) {
            setHudMirrorMode(false);
            return true;
        }

        if (isDraggingDial) {
            if (action == MotionEvent.ACTION_MOVE) {
                float dx = rawX - dialTouchStartX;
                float limit = Math.max(dp(40), lastCompassR * 0.55f);
                dialSwipeVisualOffset = Math.max(-limit, Math.min(limit, dx * 0.48f));
                invalidate();
                return true;
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                float dx = rawX - dialTouchStartX;
                float dy = Math.abs(y - dialTouchStartY);
                isDraggingDial = false;
                if (action == MotionEvent.ACTION_UP && Math.abs(dx) >= dp(28) && Math.abs(dx) > dy * 0.85f) {
                    if (dx < 0) {
                        saveDialMode(dialMode + 1);
                        dialSwipeVisualOffset = Math.max(dp(40), lastCompassR * 0.60f);
                    } else {
                        saveDialMode(dialMode - 1);
                        dialSwipeVisualOffset = -Math.max(dp(40), lastCompassR * 0.60f);
                    }
                } else if (action == MotionEvent.ACTION_UP && dialDotsHitRect.contains(x, y) && Math.abs(dx) < dp(14)) {
                    // Tapping dots/chevrons switches to next/previous dial face
                    if (x < lastCompassCx - dp(12)) {
                        saveDialMode(dialMode - 1);
                        dialSwipeVisualOffset = -Math.max(dp(36), lastCompassR * 0.45f);
                    } else {
                        saveDialMode(dialMode + 1);
                        dialSwipeVisualOffset = Math.max(dp(36), lastCompassR * 0.45f);
                    }
                }
                invalidate();
                return true;
            }
        }

        if (action == MotionEvent.ACTION_DOWN) {
            // 1. Tapping on active Adhan banner dismisses it and silences audio
            if (System.currentTimeMillis() < adhanBannerUntil && adhanBannerText != null && y <= h * 0.15f) {
                dismissAdhanBanner();
                SoundManager.stop();
                return true;
            }

            // 2. Tapping on Top bar logo/settings zone, Weather card, Quick Apps button, or Prayer Times card
            if (topBarSettingsRect.contains(x, y) || weatherCardRect.contains(x, y)
                    || quickAppsBtnRect.contains(x, y)
                    || weatherPrayerRowRect.contains(x, y) || prayerCardRect.contains(x, y)) {
                return true;
            }

            // 3. Tapping bottom Athkar bar stops audio if playing or cycles to next contextual Thikr
            if (athkarBarRect.contains(x, y)) {
                if (SoundManager.isPlaying()) {
                    SoundManager.stop();
                } else {
                    athkarTapOffset++;
                }
                invalidate();
                return true;
            }
            float aspect = getWidth() / (float) h;
            boolean wide = aspect >= 1.35f;
            boolean split = !wide && aspect >= 0.85f;
            float athkarMinY = wide ? (h * 0.92f) : (split ? (h * 0.77f) : (h * 0.81f));
            float athkarMaxY = wide ? (h * 0.995f) : (split ? (h * 0.86f) : (h * 0.88f));
            if (y >= athkarMinY && y <= athkarMaxY) {
                if (SoundManager.isPlaying()) {
                    SoundManager.stop();
                } else {
                    athkarTapOffset++;
                }
                invalidate();
                return true;
            }

            // 4. Touching inside the Compass / Dial area begins horizontal swipe tracking
            if (dialHitRect.contains(x, y) || dialDotsHitRect.contains(x, y)) {
                isDraggingDial = true;
                dialTouchStartX = rawX;
                dialTouchStartY = y;
                return true;
            }
        } else if (action == MotionEvent.ACTION_UP) {
            if (topBarSettingsRect.contains(x, y) && onSettingsClickListener != null) {
                onSettingsClickListener.onSettingsClicked();
                return true;
            }
            if (quickAppsBtnRect.contains(x, y) && onQuickAppsClickListener != null) {
                onQuickAppsClickListener.onQuickAppsClicked();
                return true;
            }
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
