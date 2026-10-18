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

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Locale ar = new Locale("ar");
    private Bitmap logoBitmap;

    private float heading;
    private float qiblaBearing;
    private float distanceKm;
    private float gpsAccuracy = -1f;
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
    public static final int THEME_AUTO = 0;
    public static final int THEME_DAY = 1;
    public static final int THEME_NIGHT = 2;
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
        themeMode = (mode == THEME_DAY || mode == THEME_NIGHT) ? mode : THEME_AUTO;
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
            float abs = Math.abs(delta);
            if (abs >= 0.15f) {
                float gain = abs > 35f ? 0.86f : (abs > 12f ? 0.66f : 0.44f);
                smoothedHeading = normalize(smoothedHeading + delta * gain);
            }
        }
        heading = smoothedHeading;
        headingSource = source;
        hasHeading = true;
        invalidate();
    }

    public void clearHeading(String source) {
        hasHeading = false;
        headingSource = source;
        invalidate();
    }

    public void setLocation(Location location) {
        hasLocation = true;
        latitude = location.getLatitude();
        longitude = location.getLongitude();
        qiblaBearing = calculateBearing(latitude, longitude);
        float[] out = new float[1];
        Location.distanceBetween(latitude, longitude, KAABA_LAT, KAABA_LON, out);
        distanceKm = out[0] / 1000f;
        gpsAccuracy = location.hasAccuracy() ? location.getAccuracy() : -1f;
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

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth(), h = getHeight();
        drawBackground(c, w, h);
        drawTopBar(c, w, h);
        if (w >= h) {
            if (panelSwapped) {
                drawPrayerPanel(c, w, h, true);
                drawCompass(c, w * .570f, h * .53f, Math.min(h * .215f, w * .126f));
                drawLeftCards(c, w, h, true);
            } else {
                drawLeftCards(c, w, h, false);
                drawCompass(c, w * .430f, h * .53f, Math.min(h * .215f, w * .126f));
                drawPrayerPanel(c, w, h, false);
            }
        } else {
            drawPortraitLayout(c, w, h);
        }
    }

    private void drawPortraitLayout(Canvas c, float w, float h) {
        float compassR = Math.min(w * .24f, h * .14f);
        drawCompass(c, w * .50f, h * .31f, compassR);

        float pad = w * .04f;
        float gap = w * .025f;
        float cardW = (w - pad * 2f - gap * 2f) / 3f;
        float cardH = h * .072f;
        float cardY = h * .49f;
        drawInfoCard(c, pad, cardY, cardW, cardH,
                englishMode ? "Qibla Angle" : "زاوية القبلة",
                hasLocation ? String.format(Locale.US, "%.0f°", qiblaBearing) : "—",
                "◈", 0xFF2FE0C7);
        drawInfoCard(c, pad + cardW + gap, cardY, cardW, cardH,
                englishMode ? "Heading" : "اتجاه المركبة",
                hasHeading ? String.format(Locale.US, "%.0f°", heading) : "—",
                "↗", hasHeading ? 0xFF2FE0C7 : 0xFFE5B64A);
        drawInfoCard(c, pad + (cardW + gap) * 2f, cardY, cardW, cardH,
                englishMode ? "GPS Accuracy" : "دقة GPS",
                formatAccuracy(), "◎", 0xFF2FE0C7);

        float px = pad, py = h * .578f, pw = w - pad * 2f, ph = h * .235f;
        roundCard(c, new RectF(px, py, px + pw, py + ph));
        p.setTextAlign(englishMode ? Paint.Align.LEFT : Paint.Align.RIGHT);
        p.setColor(goldColor());
        ts(h * .023f);
        p.setFakeBoldText(true);
        String prayerHeader = englishMode
                ? ("Prayer Times (" + localizedLocationName() + ")")
                : ("مواقيت الصلاة (" + localizedLocationName() + ")");
        drawFittedTextAligned(c, prayerHeader, englishMode ? (px + pw * .06f) : (px + pw * .94f), py + ph * .16f, pw * .88f);
        p.setFakeBoldText(false);

        String[] names = activePrayerNames();
        int next = getNextPrayerIndex();
        float rowStart = py + ph * .31f, rowH = ph * .115f;
        for (int i = 0; i < 6; i++) {
            float ry = rowStart + i * rowH;
            if (i == next) {
                p.setColor(0x16E5B64A);
                c.drawRoundRect(new RectF(px + pw * .04f, ry - rowH * .72f, px + pw * .96f, ry + rowH * .25f), 14f, 14f, p);
                stroke.setStrokeWidth(1.2f);
                stroke.setColor(0x88E5B64A);
                c.drawRoundRect(new RectF(px + pw * .04f, ry - rowH * .72f, px + pw * .96f, ry + rowH * .25f), 14f, 14f, stroke);
            }
            ts(h * .019f);
            p.setTextAlign(Paint.Align.LEFT);
            p.setColor(i == next ? goldColor() : prayerPrimaryColor());
            c.drawText(names[i], px + pw * .08f, ry, p);
            p.setTextAlign(Paint.Align.RIGHT);
            c.drawText(format12Hour(prayerTimes[i]), px + pw * .92f, ry, p);
        }

        float ny = h * .825f;
        roundCard(c, new RectF(px, ny, px + pw, ny + h * .055f));
        p.setTextAlign(Paint.Align.CENTER);
        ts(h * .018f);
        p.setColor(goldColor());
        drawFittedText(c, nextPrayerText(), px + pw * .5f, ny + h * .035f, pw * .90f);
    }

    private void drawBackground(Canvas c, float w, float h) {
        p.setStyle(Paint.Style.FILL);
        if (isNightTheme()) {
            p.setShader(new LinearGradient(0, 0, w, h, 0xFF04101B, 0xFF01060B, Shader.TileMode.CLAMP));
        } else {
            p.setShader(new LinearGradient(0, 0, w, h, 0xFFDCEEF2, 0xFFB6D4DC, Shader.TileMode.CLAMP));
        }
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);

        boolean landscape = w >= h;
        float compassCx = landscape ? (panelSwapped ? (w * .570f) : (w * .430f)) : (w * .50f);
        float compassCy = landscape ? (h * .53f) : (h * .31f);
        p.setColor(isNightTheme() ? 0x1500E4D0 : 0x2200D7C2);
        c.drawCircle(compassCx, compassCy, h * .365f, p);

        float secondCx = landscape ? (panelSwapped ? (w * .18f) : (w * .84f)) : (w * .88f);
        p.setColor(isNightTheme() ? 0x0D3E8CFF : 0x1837678E);
        c.drawCircle(secondCx, h * .15f, h * .34f, p);
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

        // 1. Adaptive Day/Night outer halo & rounded-square badge background
        RectF badge = new RectF(cx - r, cy - r, cx + r, cy + r);
        if (night) {
            // Glowing golden ambient halo for night mode
            p.setColor(0x38FFD56B);
            c.drawRoundRect(new RectF(cx - r * 1.08f, cy - r * 1.08f, cx + r * 1.08f, cy + r * 1.08f), r * .28f, r * .28f, p);
            p.setColor(0xFF0F1820);
            c.drawRoundRect(badge, r * .24f, r * .24f, p);
        } else {
            // High-contrast dark shadow & solid card backing for day mode clarity
            p.setColor(0x28000000);
            c.drawRoundRect(new RectF(cx - r * 1.04f, cy - r * 1.02f + 2f, cx + r * 1.04f, cy + r * 1.06f + 2f), r * .28f, r * .28f, p);
            p.setColor(0xF6101B22);
            c.drawRoundRect(badge, r * .24f, r * .24f, p);
        }

        // 2. High-resolution pre-rendered logo bitmap rendering
        if (logoBitmap != null && !logoBitmap.isRecycled()) {
            c.drawBitmap(logoBitmap, null, badge, bitmapPaint);
        } else {
            drawVectorLogo(c, cx, cy, r, night);
        }

        // 3. Crisp golden rounded-square border (clear in both Day and Night)
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Math.max(1.8f, r * .075f));
        stroke.setColor(night ? 0xFFFFD875 : 0xFFE5B23A);
        c.drawRoundRect(badge, r * .24f, r * .24f, stroke);

        // Inner gold highlight for Day mode clarity
        if (!night) {
            stroke.setStrokeWidth(1.2f);
            stroke.setColor(0x66FFF5D6);
            c.drawRoundRect(new RectF(cx - r * .93f, cy - r * .93f, cx + r * .93f, cy + r * .93f), r * .20f, r * .20f, stroke);
        }
    }

    private void drawVectorLogo(Canvas c, float cx, float cy, float r, boolean night) {
        // Compass circle inside badge
        float dialCy = cy + r * .06f;
        float dialR = r * .72f;

        // Emerald green dial background
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(
                cx, dialCy - dialR, cx, dialCy + dialR,
                night ? 0xFF0D6B53 : 0xFF0F7A5F,
                night ? 0xFF05382B : 0xFF074737,
                Shader.TileMode.CLAMP));
        c.drawCircle(cx, dialCy, dialR, p);
        p.setShader(null);

        // Golden compass bezel ring
        stroke.setStrokeWidth(r * .115f);
        stroke.setColor(0xFFE8BC52);
        c.drawCircle(cx, dialCy, dialR, stroke);
        stroke.setStrokeWidth(r * .032f);
        stroke.setColor(0xFFFFF0B8);
        c.drawCircle(cx, dialCy, dialR * 1.03f, stroke);

        // Compass tick marks around the dial
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

        // North-South diamond needle behind Kaaba
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

        // 3D Isometric Kaaba in the center of the dial
        float kw = dialR * .44f;
        float kh = dialR * .34f;
        float topY = dialCy - kh * .95f;
        float midY = dialCy - kh * .38f;
        float botY = dialCy + kh * .78f;

        // Top diamond of Kaaba
        Path topFace = new Path();
        topFace.moveTo(cx, topY);
        topFace.lineTo(cx + kw, midY);
        topFace.lineTo(cx, midY + (midY - topY));
        topFace.lineTo(cx - kw, midY);
        topFace.close();
        p.setColor(0xFF283640);
        c.drawPath(topFace, p);

        float centerMidY = midY + (midY - topY);

        // Left face of Kaaba
        Path leftFace = new Path();
        leftFace.moveTo(cx - kw, midY);
        leftFace.lineTo(cx, centerMidY);
        leftFace.lineTo(cx, botY);
        leftFace.lineTo(cx - kw, botY - (centerMidY - midY));
        leftFace.close();
        p.setColor(0xFF101820);
        c.drawPath(leftFace, p);

        // Right face of Kaaba
        Path rightFace = new Path();
        rightFace.moveTo(cx, centerMidY);
        rightFace.lineTo(cx + kw, midY);
        rightFace.lineTo(cx + kw, botY - (centerMidY - midY));
        rightFace.lineTo(cx, botY);
        rightFace.close();
        p.setColor(0xFF18242E);
        c.drawPath(rightFace, p);

        // Golden Kiswah band across left & right faces
        float bandTop = 0.18f;
        float bandBot = 0.38f;
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

        // Golden door on right face
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

        // Crisp golden outline of 3D Kaaba
        stroke.setStrokeWidth(r * .032f);
        stroke.setColor(0xFFFFDF85);
        c.drawPath(topFace, stroke);
        c.drawPath(leftFace, stroke);
        c.drawPath(rightFace, stroke);

        // Top Crescent Moon Badge at 12 o'clock
        float badgeCy = cy - r * .66f;
        float badgeR = r * .23f;
        p.setColor(0xFF0E1820);
        c.drawCircle(cx, badgeCy, badgeR, p);
        stroke.setStrokeWidth(r * .045f);
        stroke.setColor(0xFFFFD56B);
        c.drawCircle(cx, badgeCy, badgeR, stroke);

        // Golden crescent moon inside top badge
        p.setColor(0xFFFFDF85);
        c.drawCircle(cx, badgeCy, badgeR * .56f, p);
        p.setColor(0xFF0E1820);
        c.drawCircle(cx + badgeR * .22f, badgeCy - badgeR * .16f, badgeR * .48f, p);
    }

    private void drawLeftCards(Canvas c, float w, float h, boolean onRight) {
        float margin = w * .045f;
        float cardW = w * .175f, cardH = h * .120f;
        float x = onRight ? (w - margin - cardW) : margin;
        drawInfoCard(c, x, h * .205f, cardW, cardH,
                englishMode ? "Qibla Angle" : "زاوية القبلة",
                hasLocation ? String.format(Locale.US, "%.0f°", qiblaBearing) : "—",
                "◈", 0xFF2FE0C7);
        drawInfoCard(c, x, h * .342f, cardW, cardH,
                englishMode ? "Heading" : "اتجاه المركبة",
                hasHeading ? String.format(Locale.US, "%.0f°", heading) : "—",
                "↗", hasHeading ? 0xFF2FE0C7 : 0xFFE5B64A);
        drawInfoCard(c, x, h * .479f, cardW, cardH,
                englishMode ? "GPS Accuracy" : "دقة GPS",
                formatAccuracy(), "◎", 0xFF2FE0C7);

        roundCard(c, new RectF(x, h * .616f, x + cardW, h * .685f));
        p.setTextAlign(Paint.Align.CENTER);
        ts(h * .014f);
        p.setColor(0xFF9FB0B7);
        drawFittedText(c, localizedHeadingSource(), x + cardW * .5f, h * .658f, cardW * .90f);
    }

    private void drawInfoCard(Canvas c, float x, float y, float cw, float ch, String label, String value, String icon, int accent) {
        roundCard(c, new RectF(x, y, x + cw, y + ch));
        p.setTextAlign(Paint.Align.LEFT);
        ts(ch * .16f);
        p.setColor(accent);
        c.drawText(icon, x + cw * .09f, y + ch * .53f, p);
        p.setColor(mutedTextColor());
        ts(ch * .12f);
        drawFittedTextAligned(c, label, x + cw * .32f, y + ch * .33f, cw * .65f);
        p.setColor(primaryTextColor());
        ts(ch * .23f);
        p.setFakeBoldText(true);
        drawFittedTextAligned(c, value, x + cw * .32f, y + ch * .72f, cw * .65f);
        p.setFakeBoldText(false);
    }

    private void roundCard(Canvas c, RectF r) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(isNightTheme() ? 0xA80A1822 : 0xE8F8FCFD);
        c.drawRoundRect(r, 24f, 24f, p);
        stroke.setStrokeWidth(1.5f);
        stroke.setColor(isNightTheme() ? 0x334B7180 : 0x554D7783);
        c.drawRoundRect(r, 24f, 24f, stroke);
    }

    private void drawCompass(Canvas c, float cx, float cy, float r) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(isNightTheme() ? 0x1600E8D0 : 0x16006474);
        c.drawCircle(cx, cy, r * 1.42f, p);
        stroke.setStrokeWidth(r * .012f);
        stroke.setColor(isNightTheme() ? 0x5530D9C4 : 0x55466F7B);
        c.drawCircle(cx, cy, r * 1.28f, stroke);
        stroke.setStrokeWidth(r * .006f);
        stroke.setColor(isNightTheme() ? 0x3348A8FF : 0x335D7E86);
        c.drawCircle(cx, cy, r * 1.12f, stroke);
        stroke.setStrokeWidth(r * .010f);
        stroke.setColor(0xAA25D9C4);
        p.setShadowLayer(24f, 0, 0, 0x9925D9C4);
        c.drawCircle(cx, cy, r, stroke);
        p.clearShadowLayer();
        stroke.setStrokeWidth(r * .004f);
        stroke.setColor(0x4430BFAF);
        c.drawCircle(cx, cy, r * .83f, stroke);

        for (int deg = 0; deg < 360; deg += 5) {
            double a = Math.toRadians(deg - 90);
            boolean major = deg % 30 == 0;
            float len = major ? r * .075f : r * .025f;
            stroke.setStrokeWidth(major ? r * .010f : r * .0035f);
            stroke.setColor(major ? 0xCCD9F6F2 : 0x304E7680);
            c.drawLine(cx + (float) Math.cos(a) * (r - len), cy + (float) Math.sin(a) * (r - len), cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r, stroke);
        }

        float rot = hasHeading ? -heading : 0f;
        drawCardinal(c, "N", cx, cy, r * .72f, rot, primaryTextColor());
        drawCardinal(c, "E", cx, cy, r * .72f, 90 + rot, primaryTextColor());
        drawCardinal(c, "S", cx, cy, r * .72f, 180 + rot, primaryTextColor());
        drawCardinal(c, "W", cx, cy, r * .72f, 270 + rot, primaryTextColor());

        if (hasLocation) {
            float arrow = hasHeading ? normalize(qiblaBearing - heading) : qiblaBearing;
            float angle = arrow > 180 ? arrow - 360 : arrow;
            c.save();
            c.rotate(angle, cx, cy);
            stroke.setStrokeWidth(r * .035f);
            stroke.setColor(0xFFE53935);
            c.drawLine(cx, cy + r * .02f, cx, cy - r * .49f, stroke);
            Path head = new Path();
            head.moveTo(cx, cy - r * .71f);
            head.lineTo(cx - r * .115f, cy - r * .47f);
            head.lineTo(cx + r * .115f, cy - r * .47f);
            head.close();
            p.setColor(0xFFE53935);
            p.setShadowLayer(20f, 0, 0, 0x99E53935);
            c.drawPath(head, p);
            p.clearShadowLayer();
            c.restore();
        }

        drawKaaba(c, cx, cy, r);
    }

    private void drawKaaba(Canvas c, float cx, float cy, float r) {
        RectF body = new RectF(cx - r * .18f, cy - r * .18f, cx + r * .18f, cy + r * .21f);
        p.setColor(0xFF10181E);
        c.drawRoundRect(body, r * .04f, r * .04f, p);
        stroke.setStrokeWidth(r * .007f);
        stroke.setColor(0xFFE4B64D);
        c.drawRoundRect(body, r * .04f, r * .04f, stroke);
        p.setColor(0xFFE4B64D);
        c.drawRect(cx - r * .18f, cy - r * .045f, cx + r * .18f, cy + r * .015f, p);
        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(0xFFFFE4A2);
        ts(r * .075f);
        c.drawText(englishMode ? "Kaaba" : "الكعبة", cx, cy + r * .105f, p);
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
        float cw = w * .305f, ch = h * .415f, y = h * .175f;
        float x = onLeft ? margin : (w - margin - cw);
        roundCard(c, new RectF(x, y, x + cw, y + ch));
        p.setTextAlign(englishMode ? Paint.Align.LEFT : Paint.Align.RIGHT);
        p.setColor(goldColor());
        ts(h * .035f);
        p.setFakeBoldText(true);
        c.drawText(englishMode ? "Prayer Times" : "مواقيت الصلاة",
                englishMode ? (x + cw * .08f) : (x + cw * .92f), y + ch * .12f, p);
        p.setFakeBoldText(false);

        String[] names = activePrayerNames();
        int next = getNextPrayerIndex();
        float rowStart = y + ch * .22f, rowH = ch * .115f;
        for (int i = 0; i < 6; i++) {
            float ry = rowStart + i * rowH;
            if (i == next) {
                p.setColor(0x16E5B64A);
                c.drawRoundRect(new RectF(x + cw * .03f, ry - rowH * .72f, x + cw * .97f, ry + rowH * .25f), 14f, 14f, p);
                stroke.setStrokeWidth(1.2f);
                stroke.setColor(0x88E5B64A);
                c.drawRoundRect(new RectF(x + cw * .03f, ry - rowH * .72f, x + cw * .97f, ry + rowH * .25f), 14f, 14f, stroke);
            }
            ts(Math.min(h * .030f, ch * .085f));
            p.setColor(i == next ? goldColor() : prayerPrimaryColor());
            if (englishMode) {
                p.setTextAlign(Paint.Align.LEFT);
                c.drawText(names[i], x + cw * .08f, ry, p);
                p.setTextAlign(Paint.Align.RIGHT);
                c.drawText(format12Hour(prayerTimes[i]), x + cw * .92f, ry, p);
            } else {
                p.setTextAlign(Paint.Align.RIGHT);
                c.drawText(names[i], x + cw * .92f, ry, p);
                p.setTextAlign(Paint.Align.LEFT);
                c.drawText(format12Hour(prayerTimes[i]), x + cw * .08f, ry, p);
            }
        }

        float ny = h * .605f;
        roundCard(c, new RectF(x, ny, x + cw, ny + h * .075f));
        p.setTextAlign(Paint.Align.CENTER);
        ts(h * .022f);
        p.setColor(goldColor());
        drawFittedText(c, nextPrayerText(), x + cw * .5f, ny + h * .046f, cw * .92f);

        float ly = h * .695f;
        roundCard(c, new RectF(x, ly, x + cw, ly + h * .125f));
        p.setTextAlign(englishMode ? Paint.Align.LEFT : Paint.Align.RIGHT);
        float alignX = englishMode ? (x + cw * .09f) : (x + cw * .90f);
        ts(h * .020f);
        p.setColor(prayerAccentColor());
        String placeLine = (englishMode ? "Location: " : "الموقع: ") + localizedLocationName();
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
        int current = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int diff = target - current;
        if (diff <= 0) diff += 24 * 60;
        String[] names = activePrayerNames();
        if (englishMode) {
            return String.format(Locale.US, "Next Prayer: %s in %02d:%02d", names[idx], diff / 60, diff % 60);
        }
        return String.format(Locale.US, "الصلاة القادمة: %s بعد %02d:%02d", names[idx], diff / 60, diff % 60);
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

    private int primaryTextColor() { return isNightTheme() ? 0xFFF4F7F8 : 0xFF102E38; }
    private int secondaryTextColor() { return isNightTheme() ? 0xFFB8C4C9 : 0xFF31545F; }
    private int mutedTextColor() { return isNightTheme() ? 0xFF82949C : 0xFF486A74; }
    private int goldColor() { return isNightTheme() ? 0xFFE5B64A : 0xFF8A5A00; }
    private int prayerPrimaryColor() { return isNightTheme() ? 0xFFEAF2F4 : 0xFF12323C; }
    private int prayerSecondaryColor() { return isNightTheme() ? 0xFFB6C4CA : 0xFF355965; }
    private int prayerAccentColor() { return isNightTheme() ? 0xFF2FE0C7 : 0xFF006B70; }

    private static float signedAngle(float value) {
        value %= 360f;
        if (value > 180f) value -= 360f;
        if (value < -180f) value += 360f;
        return value;
    }

    private boolean isNightTheme() {
        if (themeMode == THEME_NIGHT) return true;
        if (themeMode == THEME_DAY) return false;
        Calendar now = Calendar.getInstance();
        int current = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int sunrise = hasPrayerTimes ? parseMinutes(prayerTimes[1]) : -1;
        int maghrib = hasPrayerTimes ? parseMinutes(prayerTimes[4]) : -1;
        if (sunrise >= 0 && maghrib >= 0) return current < sunrise || current >= maghrib;
        return current < 6 * 60 || current >= 18 * 60;
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
