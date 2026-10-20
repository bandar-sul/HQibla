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
    private float fontScale = 1.0f;

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
        fontScale = Math.max(0.80f, Math.min(1.50f, scale));
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
        if (!englishMode) return headingSource;
        if (headingSource == null) return "Compass Active";
        if (headingSource.contains("بوصلة الشاشة")) return "Device Compass • True North";
        if (headingSource.contains("اتجاه حي عبر GPS")) return "Live GPS Heading";
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
        if (w >= h) {
            if (panelSwapped) {
                drawPrayerPanel(c, w, h, true);
                drawCompass(c, w * .570f, h * .52f, Math.min(h * .215f, w * .126f));
                drawLeftCards(c, w, h, true);
            } else {
                drawLeftCards(c, w, h, false);
                drawCompass(c, w * .430f, h * .52f, Math.min(h * .215f, w * .126f));
                drawPrayerPanel(c, w, h, false);
            }
            drawBottomAthkarBar(c, w, h);
        } else {
            drawPortraitLayout(c, w, h);
        }
    }

    private void drawPortraitLayout(Canvas c, float w, float h) {
        float compassR = Math.min(w * .24f, h * .135f);
        drawCompass(c, w * .50f, h * .29f, compassR);

        float pad = w * .04f;
        float gap = w * .025f;
        float cardW = (w - pad * 2f - gap * 2f) / 3f;
        float cardH = h * .070f;
        float cardY = h * .465f;
        drawInfoCard(c, pad, cardY, cardW, cardH,
                englishMode ? "Qibla Angle" : "زاوية القبلة",
                hasLocation ? String.format(Locale.US, "%.0f°", qiblaBearing) : "—",
                "◈", prayerAccentColor());
        drawInfoCard(c, pad + cardW + gap, cardY, cardW, cardH,
                englishMode ? "Heading" : "اتجاه المركبة",
                hasHeading ? String.format(Locale.US, "%.0f°", heading) : "—",
                "↗", hasHeading ? prayerAccentColor() : 0xFFE5B64A);
        drawInfoCard(c, pad + (cardW + gap) * 2f, cardY, cardW, cardH,
                speedKmh > 1f ? (englishMode ? "Speed" : "السرعة") : (englishMode ? "GPS Accuracy" : "دقة GPS"),
                speedKmh > 1f ? String.format(Locale.US, "%.0f km/h", speedKmh) : formatAccuracy(),
                speedKmh > 1f ? "🚗" : "◎", prayerAccentColor());

        float px = pad, py = h * .550f, pw = w - pad * 2f, ph = h * .240f;
        roundCard(c, new RectF(px, py, px + pw, py + ph));
        p.setTextAlign(englishMode ? Paint.Align.LEFT : Paint.Align.RIGHT);
        p.setColor(goldColor());
        ts(h * .023f);
        p.setFakeBoldText(true);
        String prayerHeader = englishMode
                ? ("Prayer Times (" + localizedLocationName() + ")")
                : ("مواقيت الصلاة (" + localizedLocationName() + ")");
        drawFittedTextAligned(c, prayerHeader, englishMode ? (px + pw * .06f) : (px + pw * .94f), py + ph * .15f, pw * .88f);
        p.setFakeBoldText(false);

        String[] names = activePrayerNames();
        int next = getNextPrayerIndex();
        float rowStart = py + ph * .29f, rowH = ph * .115f;
        for (int i = 0; i < 6; i++) {
            float ry = rowStart + i * rowH;
            if (i == next) {
                p.setColor(0x28FFA726);
                c.drawRoundRect(new RectF(px + pw * .04f, ry - rowH * .72f, px + pw * .96f, ry + rowH * .25f), 14f, 14f, p);
                stroke.setStyle(Paint.Style.STROKE);
                stroke.setStrokeWidth(1.2f);
                stroke.setColor(0xBBFFD768);
                c.drawRoundRect(new RectF(px + pw * .04f, ry - rowH * .72f, px + pw * .96f, ry + rowH * .25f), 14f, 14f, stroke);
            }
            ts(h * .019f);
            String icon = (i < PRAYER_ICONS.length) ? PRAYER_ICONS[i] : "•";
            p.setColor(i == next ? goldColor() : prayerPrimaryColor());
            if (englishMode) {
                p.setTextAlign(Paint.Align.LEFT);
                c.drawText(icon + " " + names[i], px + pw * .07f, ry, p);
                p.setTextAlign(Paint.Align.RIGHT);
                c.drawText(format12Hour(prayerTimes[i]), px + pw * .93f, ry, p);
            } else {
                p.setTextAlign(Paint.Align.RIGHT);
                c.drawText(icon + " " + names[i], px + pw * .93f, ry, p);
                p.setTextAlign(Paint.Align.LEFT);
                c.drawText(format12Hour(prayerTimes[i]), px + pw * .07f, ry, p);
            }
        }

        float ny = h * .802f;
        roundCard(c, new RectF(px, ny, px + pw, ny + h * .055f));
        p.setTextAlign(Paint.Align.CENTER);
        ts(h * .019f);
        p.setColor(goldColor());
        p.setFakeBoldText(true);
        drawFittedText(c, nextPrayerText(), px + pw * .5f, ny + h * .036f, pw * .90f);
        p.setFakeBoldText(false);

        // Portrait Bottom Athkar Bar
        drawPortraitBottomAthkar(c, w, h);
    }

    private void drawBackground(Canvas c, float w, float h) {
        p.setStyle(Paint.Style.FILL);
        p.setShader(getBackgroundGradient(w, h));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);

        boolean landscape = w >= h;
        float compassCx = landscape ? (panelSwapped ? (w * .570f) : (w * .430f)) : (w * .50f);
        float compassCy = landscape ? (h * .52f) : (h * .29f);

        // Ambient radial circles
        p.setStyle(Paint.Style.FILL);
        p.setColor(isNightTheme() ? 0x1600F5C8 : 0x1E00D7C2);
        c.drawCircle(compassCx, compassCy, h * .365f, p);

        float secondCx = landscape ? (panelSwapped ? (w * .18f) : (w * .84f)) : (w * .88f);
        p.setColor(isNightTheme() ? 0x103E8CFF : 0x1837678E);
        c.drawCircle(secondCx, h * .15f, h * .34f, p);

        // Draw Islamic Arabesque Geometry
        drawArabesqueGeometry(c, w, h);
    }

    private void drawArabesqueGeometry(Canvas c, float w, float h) {
        boolean landscape = w >= h;
        float cx = landscape ? (panelSwapped ? (w * .570f) : (w * .430f)) : (w * .50f);
        float cy = landscape ? (h * .52f) : (h * .29f);
        float maxR = Math.min(w, h) * (landscape ? 0.36f : 0.26f);

        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(1.2f);
        int patternColor = getArabesqueColor();
        stroke.setColor(patternColor);

        // Concentric geometric circles
        c.drawCircle(cx, cy, maxR * 0.88f, stroke);
        c.drawCircle(cx, cy, maxR * 1.15f, stroke);
        c.drawCircle(cx, cy, maxR * 1.35f, stroke);

        // 8-Pointed Star Lattice around compass
        float starR = maxR * 1.25f;
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

        // Second interleaved 8-Pointed Star rotated by 11.25 degrees
        Path starPath2 = new Path();
        for (int i = 0; i < 16; i++) {
            double angle = Math.toRadians(i * 22.5 + 11.25);
            float r = (i % 2 == 0) ? (starR * 1.08f) : (innerR * 1.08f);
            float px = cx + (float) (Math.cos(angle) * r);
            float py = cy + (float) (Math.sin(angle) * r);
            if (i == 0) starPath2.moveTo(px, py);
            else starPath2.lineTo(px, py);
        }
        starPath2.close();
        stroke.setColor(patternColor & 0x66FFFFFF);
        c.drawPath(starPath2, stroke);

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
        boolean landscape = w >= h;
        float logoR = minDim * (landscape ? .062f : .058f);
        float logoCx = landscape ? Math.max(w * .060f, logoR + w * .035f) : (logoR + w * .042f);
        float logoCy = h * (landscape ? .074f : .068f);
        drawLogo(c, logoCx, logoCy, logoR);

        float textX = logoCx + logoR + (landscape ? w * .016f : w * .024f);
        p.setTextAlign(Paint.Align.LEFT);
        p.setColor(primaryTextColor());
        ts(minDim * .042f);
        p.setFakeBoldText(true);
        c.drawText(englishMode ? "Qibla" : "قبلة", textX, logoCy + logoR * .10f, p);
        p.setFakeBoldText(false);
        ts(minDim * .020f);
        p.setColor(goldColor());
        c.drawText(BuildConfig.VERSION_NAME, textX + (englishMode ? minDim * .006f : minDim * .014f), logoCy + logoR * .68f, p);

        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(primaryTextColor());
        ts(minDim * .029f);
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
        float centerMaxW = landscape ? (w * .44f) : (w * .88f);
        drawFittedText(c, qibla, w * .50f, h * (landscape ? .064f : .138f), centerMaxW);

        ts(minDim * .021f);
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
        drawFittedText(c, deviation, w * .50f, h * (landscape ? .105f : .165f), centerMaxW);

        Date now = new Date();
        p.setTextAlign(Paint.Align.RIGHT);
        p.setColor(primaryTextColor());
        ts(minDim * .042f);
        p.setFakeBoldText(true);
        c.drawText(new SimpleDateFormat("hh:mm a", Locale.US).format(now), w * .955f, h * .070f, p);
        p.setFakeBoldText(false);
        ts(minDim * .019f);
        p.setColor(secondaryTextColor());
        float rightMaxW = landscape ? (w * .24f) : (w * .44f);
        drawFittedTextAligned(c, new SimpleDateFormat("EEEE d MMMM", englishMode ? Locale.US : ar).format(now), w * .955f, h * .098f, rightMaxW);
        p.setColor(mutedTextColor());
        ts(minDim * .016f);
        String activeHijri = (englishMode && !hijriDateEn.isEmpty()) ? hijriDateEn : hijriDate;
        String emptyHijri = englishMode ? "Hijri date after prayer sync" : "التاريخ الهجري بعد تحديث الصلاة";
        drawFittedTextAligned(c, activeHijri.isEmpty() ? emptyHijri : activeHijri, w * .955f, h * .120f, rightMaxW);
    }

    private void drawLogo(Canvas c, float cx, float cy, float r) {
        boolean night = isNightTheme();
        p.setStyle(Paint.Style.FILL);

        RectF badge = new RectF(cx - r, cy - r, cx + r, cy + r);
        if (night) {
            p.setColor(0x38FFD56B);
            c.drawRoundRect(new RectF(cx - r * 1.08f, cy - r * 1.08f, cx + r * 1.08f, cy + r * 1.08f), r * .28f, r * .28f, p);
            p.setColor(0xFF0F1820);
            c.drawRoundRect(badge, r * .24f, r * .24f, p);
        } else {
            p.setColor(0x28000000);
            c.drawRoundRect(new RectF(cx - r * 1.04f, cy - r * 1.02f + 2f, cx + r * 1.04f, cy + r * 1.06f + 2f), r * .28f, r * .28f, p);
            p.setColor(0xF6101B22);
            c.drawRoundRect(badge, r * .24f, r * .24f, p);
        }

        if (logoBitmap != null && !logoBitmap.isRecycled()) {
            c.drawBitmap(logoBitmap, null, badge, bitmapPaint);
        } else {
            drawVectorLogo(c, cx, cy, r, night);
        }

        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Math.max(1.8f, r * .075f));
        stroke.setColor(night ? 0xFFFFD875 : 0xFFE5B23A);
        c.drawRoundRect(badge, r * .24f, r * .24f, stroke);

        if (!night) {
            stroke.setStrokeWidth(1.2f);
            stroke.setColor(0x66FFF5D6);
            c.drawRoundRect(new RectF(cx - r * .93f, cy - r * .93f, cx + r * .93f, cy + r * .93f), r * .20f, r * .20f, stroke);
        }
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
        float margin = w * .045f;
        float cardW = w * .175f, cardH = h * .120f;
        float x = onRight ? (w - margin - cardW) : margin;
        drawInfoCard(c, x, h * .185f, cardW, cardH,
                englishMode ? "Qibla Angle" : "زاوية القبلة",
                hasLocation ? String.format(Locale.US, "%.0f°", qiblaBearing) : "—",
                "◈", prayerAccentColor());
        drawInfoCard(c, x, h * .322f, cardW, cardH,
                englishMode ? "Heading" : "اتجاه المركبة",
                hasHeading ? String.format(Locale.US, "%.0f°", heading) : "—",
                "↗", hasHeading ? prayerAccentColor() : 0xFFE5B64A);

        boolean showSpeed = speedKmh > 1f;
        drawInfoCard(c, x, h * .459f, cardW, cardH,
                showSpeed ? (englishMode ? "Vehicle Speed" : "سرعة المركبة") : (englishMode ? "GPS Accuracy" : "دقة GPS"),
                showSpeed ? String.format(Locale.US, "%.0f km/h", speedKmh) : formatAccuracy(),
                showSpeed ? "🚗" : "◎", prayerAccentColor());

        drawInfoCard(c, x, h * .596f, cardW, cardH * 0.72f,
                englishMode ? "Compass Source" : "مصدر الاتجاه",
                localizedHeadingSource(), "🧭", goldColor());
    }

    private void drawInfoCard(Canvas c, float x, float y, float cw, float ch, String label, String value, String icon, int accent) {
        roundCard(c, new RectF(x, y, x + cw, y + ch));
        p.setTextAlign(Paint.Align.LEFT);
        ts(ch * .20f);
        p.setColor(accent);
        c.drawText(icon, x + cw * .08f, y + ch * .53f, p);
        p.setColor(mutedTextColor());
        ts(ch * .14f);
        drawFittedTextAligned(c, label, x + cw * .30f, y + ch * .34f, cw * .66f);
        p.setColor(primaryTextColor());
        ts(ch * .25f);
        p.setFakeBoldText(true);
        drawFittedTextAligned(c, value, x + cw * .30f, y + ch * .74f, cw * .66f);
        p.setFakeBoldText(false);
    }

    private void roundCard(Canvas c, RectF r) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(cardBgFill());
        c.drawRoundRect(r, 22f, 22f, p);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(1.4f);
        stroke.setColor(cardStrokeColor());
        c.drawRoundRect(r, 22f, 22f, stroke);
    }

    private void drawCompass(Canvas c, float cx, float cy, float r) {
        boolean aligned = isQiblaAligned();

        p.setStyle(Paint.Style.FILL);
        if (aligned) {
            p.setColor(0x2E00F5A0);
            c.drawCircle(cx, cy, r * 1.48f, p);
            p.setColor(0x3D00F5A0);
            c.drawCircle(cx, cy, r * 1.34f, p);
        } else {
            p.setColor(isNightTheme() ? 0x1600E8D0 : 0x16006474);
            c.drawCircle(cx, cy, r * 1.42f, p);
        }

        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(r * .035f);
        stroke.setColor(aligned ? 0xFFFFD768 : (isNightTheme() ? 0xFF8A6726 : 0xFFB88E3E));
        c.drawCircle(cx, cy, r * 1.25f, stroke);

        stroke.setStrokeWidth(r * .012f);
        stroke.setColor(aligned ? 0xFF00F5C8 : (isNightTheme() ? 0x88FFDF85 : 0x884D7783));
        c.drawCircle(cx, cy, r * 1.28f, stroke);

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
        float badgeY = cy + r * 1.05f;
        float badgeW = r * 1.55f;
        float badgeH = Math.max(26f, r * 0.22f);
        RectF badgeRect = new RectF(cx - badgeW * 0.5f, badgeY, cx + badgeW * 0.5f, badgeY + badgeH);

        p.setStyle(Paint.Style.FILL);
        p.setColor(aligned ? 0xE005382B : cardBgFill());
        c.drawRoundRect(badgeRect, 18f, 18f, p);

        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(1.2f);
        stroke.setColor(aligned ? 0xFFFFD768 : cardStrokeColor());
        c.drawRoundRect(badgeRect, 18f, 18f, stroke);

        p.setTextAlign(Paint.Align.CENTER);
        ts(badgeH * 0.50f);
        p.setColor(aligned ? 0xFFFFFFFF : goldColor());
        p.setFakeBoldText(true);

        String makkahDist = (distanceKm > 0)
                ? String.format(Locale.US, englishMode ? "%.0f km to Makkah" : "%.0f كم إلى مكة", distanceKm)
                : (englishMode ? "Makkah: 21.42° N" : "مكة المكرمة");

        String badgeText = aligned
                ? (englishMode ? "🕋 Aligned with Qibla" : "🕋 باتجاه القبلة المشرفة")
                : ("🕋 " + makkahDist);

        drawFittedText(c, badgeText, cx, badgeY + badgeH * 0.68f, badgeW * 0.90f);
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
        float margin = w * .045f;
        float cw = w * .305f, ch = h * .425f, y = h * .175f;
        float x = onLeft ? margin : (w - margin - cw);
        roundCard(c, new RectF(x, y, x + cw, y + ch));
        p.setTextAlign(englishMode ? Paint.Align.LEFT : Paint.Align.RIGHT);
        p.setColor(goldColor());
        ts(h * .033f);
        p.setFakeBoldText(true);
        c.drawText(englishMode ? "🕌 Prayer Times" : "🕌 مواقيت الصلاة",
                englishMode ? (x + cw * .08f) : (x + cw * .92f), y + ch * .12f, p);
        p.setFakeBoldText(false);

        String[] names = activePrayerNames();
        int next = getNextPrayerIndex();
        float rowStart = y + ch * .22f, rowH = ch * .115f;
        for (int i = 0; i < 6; i++) {
            float ry = rowStart + i * rowH;
            if (i == next) {
                p.setColor(0x28FFA726);
                c.drawRoundRect(new RectF(x + cw * .03f, ry - rowH * .72f, x + cw * .97f, ry + rowH * .25f), 14f, 14f, p);
                stroke.setStyle(Paint.Style.STROKE);
                stroke.setStrokeWidth(1.2f);
                stroke.setColor(0xBBFFD768);
                c.drawRoundRect(new RectF(x + cw * .03f, ry - rowH * .72f, x + cw * .97f, ry + rowH * .25f), 14f, 14f, stroke);
            }
            ts(Math.min(h * .029f, ch * .085f));
            String icon = (i < PRAYER_ICONS.length) ? PRAYER_ICONS[i] : "•";
            p.setColor(i == next ? goldColor() : prayerPrimaryColor());
            if (englishMode) {
                p.setTextAlign(Paint.Align.LEFT);
                c.drawText(icon + " " + names[i], x + cw * .08f, ry, p);
                p.setTextAlign(Paint.Align.RIGHT);
                c.drawText(format12Hour(prayerTimes[i]), x + cw * .92f, ry, p);
            } else {
                p.setTextAlign(Paint.Align.RIGHT);
                c.drawText(icon + " " + names[i], x + cw * .92f, ry, p);
                p.setTextAlign(Paint.Align.LEFT);
                c.drawText(format12Hour(prayerTimes[i]), x + cw * .08f, ry, p);
            }
        }

        float ny = h * .615f;
        roundCard(c, new RectF(x, ny, x + cw, ny + h * .075f));
        p.setTextAlign(Paint.Align.CENTER);
        ts(h * .022f);
        p.setColor(goldColor());
        p.setFakeBoldText(true);
        drawFittedText(c, nextPrayerText(), x + cw * .5f, ny + h * .046f, cw * .92f);
        p.setFakeBoldText(false);

        float ly = h * .705f;
        roundCard(c, new RectF(x, ly, x + cw, ly + h * .125f));
        p.setTextAlign(englishMode ? Paint.Align.LEFT : Paint.Align.RIGHT);
        float alignX = englishMode ? (x + cw * .09f) : (x + cw * .90f);
        ts(h * .020f);
        p.setColor(prayerAccentColor());
        String placeLine = (englishMode ? "📍 Location: " : "📍 الموقع: ") + localizedLocationName();
        drawFittedTextAligned(c, placeLine, alignX, ly + h * .035f, cw * .84f);
        p.setColor(prayerSecondaryColor());
        ts(h * .015f);
        String coords = hasLocation
                ? String.format(Locale.US, "%.5f, %.5f", latitude, longitude)
                : (englishMode ? "Waiting for coordinates" : "بانتظار الإحداثيات");
        drawFittedTextAligned(c, coords, alignX, ly + h * .063f, cw * .84f);
        p.setColor(isNightTheme() ? 0xFF7ED9EA : 0xFF006B70);
        String gpsLabel = hasLocation
                ? (englishMode ? "GPS + Network" : "GPS + الشبكة")
                : (englishMode ? "Waiting for GPS" : "بانتظار GPS");
        drawFittedTextAligned(c, gpsLabel + "  •  " + localizedNetworkStatus(), alignX, ly + h * .090f, cw * .84f);
        p.setTextAlign(englishMode ? Paint.Align.LEFT : Paint.Align.RIGHT);
        p.setColor(prayerSecondaryColor());
        ts(h * .014f);
        drawFittedTextAligned(c, localizedPrayerStatus(), alignX, ly + h * .111f, cw * .84f);
    }

    private void drawBottomAthkarBar(Canvas c, float w, float h) {
        float barH = h * 0.052f;
        float barY = h * 0.932f;
        float startX = w * 0.38f;
        float endX = w * 0.96f;
        RectF barRect = new RectF(startX, barY, endX, barY + barH);

        roundCard(c, barRect);

        p.setTextAlign(Paint.Align.CENTER);
        ts(barH * 0.44f);
        p.setColor(goldColor());
        String thikr = "📿 " + currentThikrText();
        drawFittedText(c, thikr, (startX + endX) * 0.5f, barY + barH * 0.68f, (endX - startX) * 0.94f);
    }

    private void drawPortraitBottomAthkar(Canvas c, float w, float h) {
        float pad = w * .04f;
        float barH = h * 0.048f;
        float barY = h * 0.940f;
        RectF barRect = new RectF(pad, barY, w - pad, barY + barH);
        roundCard(c, barRect);
        p.setTextAlign(Paint.Align.CENTER);
        ts(barH * 0.44f);
        p.setColor(goldColor());
        String thikr = "📿 " + currentThikrText();
        drawFittedText(c, thikr, w * 0.5f, barY + barH * 0.68f, (w - pad * 2f) * 0.94f);
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
            p.setTextSize(Math.max(originalSize * 0.65f, originalSize * (maxW / textW)));
        }
        c.drawText(text, cx, cy, p);
        p.setTextSize(originalSize);
    }

    private void drawFittedTextAligned(Canvas c, String text, float x, float y, float maxW) {
        if (text == null || text.isEmpty()) return;
        float originalSize = p.getTextSize();
        float textW = p.measureText(text);
        if (textW > maxW && textW > 0) {
            p.setTextSize(Math.max(originalSize * 0.65f, originalSize * (maxW / textW)));
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
