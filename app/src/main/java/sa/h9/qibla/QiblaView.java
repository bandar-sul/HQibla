package sa.h9.qibla;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.location.Location;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class QiblaView extends View {
    private static final double KAABA_LAT = 21.422487;
    private static final double KAABA_LON = 39.826206;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Locale ar = new Locale("ar");

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
    private String locationName = "الموقع الحالي";
    private String regionName = "";
    private String[] prayerNames = {"الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"};
    private String[] prayerTimes = {"--:--", "--:--", "--:--", "--:--", "--:--", "--:--"};
    private boolean hasLocation;
    private boolean hasHeading;
    private boolean hasPrayerTimes;
    private float smoothedHeading = Float.NaN;
    public static final int THEME_AUTO = 0;
    public static final int THEME_DAY = 1;
    public static final int THEME_NIGHT = 2;
    private int themeMode = THEME_AUTO;
    private float fontScale = 1.0f;

    public QiblaView(Context context) {
        super(context);
        p.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL));
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void tick() { invalidate(); }

    public void setThemeMode(int mode) {
        themeMode = (mode == THEME_DAY || mode == THEME_NIGHT) ? mode : THEME_AUTO;
        invalidate();
    }

    public int getThemeMode() { return themeMode; }

    public void setFontScale(float scale) {
        fontScale = Math.max(0.85f, Math.min(1.30f, scale));
        invalidate();
    }

    public float getFontScale() { return fontScale; }

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
        if (names != null && names.length == 6) prayerNames = names;
        if (times != null && times.length == 6) prayerTimes = times;
        hijriDate = hijri == null ? "" : hijri;
        hasPrayerTimes = true;
        prayerStatus = "طريقة الحساب: أم القرى";
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth(), h = getHeight();
        drawBackground(c, w, h);
        drawTopBar(c, w, h);
        drawCompass(c, w * .425f, h * .53f, Math.min(h * .215f, w * .126f));
        drawLeftCards(c, w, h);
        drawPrayerPanel(c, w, h);

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
        p.setColor(isNightTheme() ? 0x1500E4D0 : 0x2200D7C2);
        c.drawCircle(w * .425f, h * .52f, h * .365f, p);
        p.setColor(isNightTheme() ? 0x0D3E8CFF : 0x1837678E);
        c.drawCircle(w * .88f, h * .15f, h * .34f, p);
    }

    private void drawTopBar(Canvas c, float w, float h) {
        drawLogo(c, w * .045f, h * .072f, h * .046f);
        p.setTextAlign(Paint.Align.LEFT);
        p.setColor(primaryTextColor());
        ts(h * .046f);
        p.setFakeBoldText(true);
        p.setColor(primaryTextColor());
        c.drawText("قبلة", w * .083f, h * .082f, p);
        p.setFakeBoldText(false);
        ts(h * .020f);
        p.setColor(goldColor());
        c.drawText("8.0 MANUAL", w * .114f, h * .116f, p);

        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(primaryTextColor());
        ts(h * .031f);
        String qibla = hasLocation ? String.format(Locale.US, "اتجاه القبلة: %.0f° من الشمال", qiblaBearing) : "اتجاه القبلة: بانتظار الموقع";
        c.drawText(qibla, w * .50f, h * .064f, p);
        ts(h * .023f);
        p.setColor(secondaryTextColor());
        String deviation;
        if (hasLocation && hasHeading) {
            float rel = normalize(qiblaBearing - heading);
            float signed = rel > 180 ? rel - 360 : rel;
            deviation = String.format(Locale.US, "الانحراف عن اتجاه المركبة: %.0f° %s", Math.abs(signed), signed >= 0 ? "يمينًا" : "يسارًا");
        } else {
            deviation = hasLocation ? "بانتظار اتجاه السيارة" : "بانتظار تحديد الموقع";
        }
        c.drawText(deviation, w * .50f, h * .105f, p);

        Date now = new Date();
        p.setTextAlign(Paint.Align.RIGHT);
        p.setColor(primaryTextColor());
        ts(h * .048f);
        p.setFakeBoldText(true);
        c.drawText(new SimpleDateFormat("hh:mm a", Locale.US).format(now), w * .955f, h * .070f, p);
        p.setFakeBoldText(false);
        ts(h * .020f);
        p.setColor(secondaryTextColor());
        c.drawText(new SimpleDateFormat("EEEE d MMMM", ar).format(now), w * .955f, h * .105f, p);
        p.setColor(mutedTextColor());
        c.drawText(hijriDate.isEmpty() ? "التاريخ الهجري بعد تحديث الصلاة" : hijriDate, w * .955f, h * .136f, p);
    }

    private void drawLogo(Canvas c, float cx, float cy, float r) {
        stroke.setStrokeWidth(r * .055f);
        stroke.setColor(0xFFE5B64A);
        c.drawCircle(cx, cy, r, stroke);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF11181D);
        c.drawRect(cx-r*.34f, cy-r*.23f, cx+r*.34f, cy+r*.28f, p);
        p.setColor(goldColor());
        c.drawRect(cx-r*.34f, cy-r*.05f, cx+r*.34f, cy+r*.03f, p);
        Path a = new Path();
        a.moveTo(cx+r*.22f, cy+r*.10f);
        a.lineTo(cx+r*.62f, cy+r*.48f);
        a.lineTo(cx+r*.36f, cy+r*.53f);
        a.close();
        p.setColor(0xFF2FE0C7);
        c.drawPath(a, p);
    }

    private void drawLeftCards(Canvas c, float w, float h) {
        float x = w * .018f, cardW = w * .158f, cardH = h * .116f;
        drawInfoCard(c, x, h*.205f, cardW, cardH, "زاوية القبلة", hasLocation ? String.format(Locale.US,"%.0f°", qiblaBearing) : "—", "◈", 0xFF2FE0C7);
        drawInfoCard(c, x, h*.342f, cardW, cardH, "اتجاه المركبة", hasHeading ? String.format(Locale.US,"%.0f°", heading) : "—", "↗", hasHeading ? 0xFF2FE0C7 : 0xFFE5B64A);
        String accuracy = gpsAccuracy < 0 ? "—" : gpsAccuracy <= 12 ? "ممتازة" : gpsAccuracy <= 25 ? "جيدة" : String.format(Locale.US,"%.0f م", gpsAccuracy);
        drawInfoCard(c, x, h*.479f, cardW, cardH, "دقة GPS", accuracy, "◎", 0xFF2FE0C7);

        roundCard(c, new RectF(x, h*.616f, x+cardW, h*.680f));
        p.setTextAlign(Paint.Align.CENTER);
        ts(h*.013f);
        p.setColor(0xFF9FB0B7);
        c.drawText(hasHeading ? headingSource : (hasLocation ? "بانتظار بوصلة السيارة" : "بانتظار GPS"), x+cardW*.5f, h*.657f, p);
    }

    private void drawInfoCard(Canvas c, float x, float y, float cw, float ch, String label, String value, String icon, int accent) {
        roundCard(c, new RectF(x, y, x+cw, y+ch));
        p.setTextAlign(Paint.Align.LEFT);
        ts(ch*.16f);
        p.setColor(accent);
        c.drawText(icon, x+cw*.09f, y+ch*.53f, p);
        p.setColor(mutedTextColor());
        ts(ch*.125f);
        c.drawText(label, x+cw*.45f, y+ch*.31f, p);
        p.setColor(primaryTextColor());
        ts(ch*.245f);
        p.setFakeBoldText(true);
        c.drawText(value, x+cw*.45f, y+ch*.72f, p);
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
        c.drawCircle(cx, cy, r*1.42f, p);
        stroke.setStrokeWidth(r*.012f);
        stroke.setColor(isNightTheme() ? 0x5530D9C4 : 0x55466F7B);
        c.drawCircle(cx, cy, r*1.28f, stroke);
        stroke.setStrokeWidth(r*.006f);
        stroke.setColor(isNightTheme() ? 0x3348A8FF : 0x335D7E86);
        c.drawCircle(cx, cy, r*1.12f, stroke);
        stroke.setStrokeWidth(r*.010f);
        stroke.setColor(0xAA25D9C4);
        p.setShadowLayer(24f, 0, 0, 0x9925D9C4);
        c.drawCircle(cx, cy, r, stroke);
        p.clearShadowLayer();
        stroke.setStrokeWidth(r*.004f);
        stroke.setColor(0x4430BFAF);
        c.drawCircle(cx, cy, r*.83f, stroke);

        for (int deg=0; deg<360; deg+=5) {
            double a = Math.toRadians(deg-90);
            boolean major = deg%30==0;
            float len = major ? r*.075f : r*.025f;
            stroke.setStrokeWidth(major ? r*.010f : r*.0035f);
            stroke.setColor(major ? 0xCCD9F6F2 : 0x304E7680);
            c.drawLine(cx+(float)Math.cos(a)*(r-len), cy+(float)Math.sin(a)*(r-len), cx+(float)Math.cos(a)*r, cy+(float)Math.sin(a)*r, stroke);
        }

        float rot = hasHeading ? -heading : 0f;
        drawCardinal(c,"N",cx,cy,r*.72f,rot,primaryTextColor());
        drawCardinal(c,"E",cx,cy,r*.72f,90+rot,primaryTextColor());
        drawCardinal(c,"S",cx,cy,r*.72f,180+rot,primaryTextColor());
        drawCardinal(c,"W",cx,cy,r*.72f,270+rot,primaryTextColor());

        if (hasLocation) {
            float arrow = hasHeading ? normalize(qiblaBearing-heading) : qiblaBearing;
            float angle = arrow > 180 ? arrow-360 : arrow;
            c.save();
            c.rotate(angle,cx,cy);
            stroke.setStrokeWidth(r*.035f);
            stroke.setColor(0xFFE53935);
            c.drawLine(cx,cy+r*.02f,cx,cy-r*.49f,stroke);
            Path head = new Path();
            head.moveTo(cx,cy-r*.71f);
            head.lineTo(cx-r*.115f,cy-r*.47f);
            head.lineTo(cx+r*.115f,cy-r*.47f);
            head.close();
            p.setColor(0xFFE53935);
            p.setShadowLayer(20f,0,0,0x99E53935);
            c.drawPath(head,p);
            p.clearShadowLayer();
            c.restore();
        }

        drawKaaba(c,cx,cy,r);
    }

    private void drawKaaba(Canvas c, float cx, float cy, float r) {
        RectF body = new RectF(cx-r*.18f,cy-r*.18f,cx+r*.18f,cy+r*.21f);
        p.setColor(0xFF10181E);
        c.drawRoundRect(body,r*.04f,r*.04f,p);
        stroke.setStrokeWidth(r*.007f);
        stroke.setColor(0xFFE4B64D);
        c.drawRoundRect(body,r*.04f,r*.04f,stroke);
        p.setColor(0xFFE4B64D);
        c.drawRect(cx-r*.18f,cy-r*.045f,cx+r*.18f,cy+r*.015f,p);
        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(0xFFFFE4A2);
        ts(r*.075f);
        c.drawText("الكعبة",cx,cy+r*.105f,p);
    }

    private void drawCardinal(Canvas c,String t,float cx,float cy,float rr,float deg,int color) {
        double a=Math.toRadians(deg-90);
        float x=cx+(float)Math.cos(a)*rr, y=cy+(float)Math.sin(a)*rr;
        p.setTextAlign(Paint.Align.CENTER);
        ts(rr*.105f);
        p.setColor(color);
        p.setFakeBoldText(true);
        Paint.FontMetrics fm=p.getFontMetrics();
        c.drawText(t,x,y-(fm.ascent+fm.descent)/2,p);
        p.setFakeBoldText(false);
    }

    private void drawPrayerPanel(Canvas c, float w, float h) {
        float x=w*.715f, y=h*.175f, cw=w*.265f, ch=h*.405f;
        roundCard(c,new RectF(x,y,x+cw,y+ch));
        p.setTextAlign(Paint.Align.RIGHT);
        p.setColor(goldColor());
        ts(h*.038f);
        p.setFakeBoldText(true);
        c.drawText("مواقيت الصلاة",x+cw*.90f,y+ch*.12f,p);
        p.setFakeBoldText(false);

        int next = getNextPrayerIndex();
        float rowStart=y+ch*.22f, rowH=ch*.112f;
        for(int i=0;i<6;i++) {
            float ry=rowStart+i*rowH;
            if(i==next) {
                p.setColor(0x16E5B64A);
                c.drawRoundRect(new RectF(x+cw*.04f,ry-rowH*.72f,x+cw*.96f,ry+rowH*.25f),14f,14f,p);
                stroke.setStrokeWidth(1.2f); stroke.setColor(0x88E5B64A);
                c.drawRoundRect(new RectF(x+cw*.04f,ry-rowH*.72f,x+cw*.96f,ry+rowH*.25f),14f,14f,stroke);
            }
            ts(h*.031f);
            p.setTextAlign(Paint.Align.LEFT);
            p.setColor(i==next ? goldColor() : prayerPrimaryColor());
            c.drawText(prayerNames[i],x+cw*.10f,ry,p);
            p.setTextAlign(Paint.Align.RIGHT);
            c.drawText(format12Hour(prayerTimes[i]),x+cw*.91f,ry,p);
        }

        float ny=h*.600f;
        roundCard(c,new RectF(x,ny,x+cw,ny+h*.075f));
        p.setTextAlign(Paint.Align.CENTER);
        ts(h*.025f);
        p.setColor(goldColor());
        c.drawText(nextPrayerText(),x+cw*.5f,ny+h*.046f,p);

        float ly=h*.690f;
        roundCard(c,new RectF(x,ly,x+cw,ly+h*.125f));
        p.setTextAlign(Paint.Align.RIGHT);
        ts(h*.022f);
        p.setColor(prayerAccentColor());
        String placeLine = "الموقع: " + locationName;
        c.drawText(placeLine,x+cw*.90f,ly+h*.035f,p);
        p.setColor(prayerSecondaryColor());
        ts(h*.016f);
        String coords = hasLocation ? String.format(Locale.US,"%.5f, %.5f",latitude,longitude) : "بانتظار الإحداثيات";
        c.drawText(coords,x+cw*.90f,ly+h*.063f,p);
        p.setColor(isNightTheme() ? 0xFF7ED9EA : 0xFFCBEFF5);
        c.drawText((hasLocation?"GPS + الشبكة":"بانتظار GPS")+"  •  "+networkStatus,x+cw*.90f,ly+h*.090f,p);
        p.setTextAlign(Paint.Align.LEFT);
        p.setColor(prayerSecondaryColor());
        ts(h*.014f);
        c.drawText(prayerStatus,x+cw*.07f,ly+h*.111f,p);
    }

    private int getNextPrayerIndex() {
        if(!hasPrayerTimes) return -1;
        Calendar now=Calendar.getInstance();
        int current=now.get(Calendar.HOUR_OF_DAY)*60+now.get(Calendar.MINUTE);
        for(int i=0;i<prayerTimes.length;i++) {
            int m=parseMinutes(prayerTimes[i]);
            if(m>=0 && m>current) return i;
        }
        return 0;
    }

    private String nextPrayerText() {
        int idx=getNextPrayerIndex();
        if(idx<0) return "الصلاة القادمة: بانتظار التحديث";
        int target=parseMinutes(prayerTimes[idx]);
        Calendar now=Calendar.getInstance();
        int current=now.get(Calendar.HOUR_OF_DAY)*60+now.get(Calendar.MINUTE);
        int diff=target-current;
        if(diff<=0) diff+=24*60;
        return String.format(Locale.US,"الصلاة القادمة: %s بعد %02d:%02d",prayerNames[idx],diff/60,diff%60);
    }

    private int parseMinutes(String s) {
        try {
            String[] parts=s.split(":");
            return Integer.parseInt(parts[0])*60+Integer.parseInt(parts[1]);
        } catch(Exception e) { return -1; }
    }

    private String format12Hour(String value) {
        int minutes = parseMinutes(value);
        if (minutes < 0) return value;
        int hour24 = minutes / 60;
        int minute = minutes % 60;
        String ampm = hour24 >= 12 ? "PM" : "AM";
        int hour12 = hour24 % 12;
        if (hour12 == 0) hour12 = 12;
        return String.format(Locale.US, "%02d:%02d %s", hour12, minute, ampm);
    }

    private void drawFooter(Canvas c,float w,float h) {
        stroke.setStrokeWidth(1f); stroke.setColor(0x334A6B75);
        c.drawLine(w*.025f,h*.91f,w*.975f,h*.91f,stroke);
        p.setTextAlign(Paint.Align.CENTER);
        ts(h*.018f);
        p.setColor(0xFF6F858E);
        }

    private void ts(float size) { p.setTextSize(size * fontScale); }

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

    private static float calculateBearing(double lat,double lon) {
        double phi1=Math.toRadians(lat),phi2=Math.toRadians(KAABA_LAT),dl=Math.toRadians(KAABA_LON-lon);
        double y=Math.sin(dl)*Math.cos(phi2);
        double x=Math.cos(phi1)*Math.sin(phi2)-Math.sin(phi1)*Math.cos(phi2)*Math.cos(dl);
        return normalize((float)Math.toDegrees(Math.atan2(y,x)));
    }

    private static float normalize(float v) {
        v%=360f; if(v<0)v+=360f; return v;
    }
}
