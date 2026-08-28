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

import java.util.Locale;

public class QiblaView extends View {
    private static final double KAABA_LAT = 21.422487;
    private static final double KAABA_LON = 39.826206;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float heading = 0f;
    private float qiblaBearing = 0f;
    private float distanceKm = 0f;
    private String status = "بانتظار الموقع…";
    private String networkStatus = "فحص الإنترنت…";
    private String headingSource = "تحرك عدة أمتار لتفعيل الاتجاه الحي";
    private boolean hasLocation = false;
    private boolean hasHeading = false;

    public QiblaView(Context context) {
        super(context);
        p.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL));
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setHeading(float heading, String source) {
        this.heading = normalize(heading);
        this.headingSource = source;
        this.hasHeading = true;
        invalidate();
    }

    public void clearHeading(String source) {
        this.hasHeading = false;
        this.headingSource = source;
        invalidate();
    }

    public void setLocation(Location location) {
        hasLocation = true;
        qiblaBearing = calculateBearing(location.getLatitude(), location.getLongitude());
        float[] out = new float[1];
        Location.distanceBetween(location.getLatitude(), location.getLongitude(), KAABA_LAT, KAABA_LON, out);
        distanceKm = out[0] / 1000f;
        status = location.hasAccuracy() && location.getAccuracy() <= 20 ? "GPS دقيق" : "الموقع متصل";
        invalidate();
    }

    public void setStatus(String text) { status = text; invalidate(); }
    public void setNetworkStatus(String text) { networkStatus = text; invalidate(); }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth(), h = getHeight();
        drawBackground(c, w, h);
        drawHeader(c, w, h);
        drawCompass(c, w * 0.52f, h * 0.50f, Math.min(h * 0.36f, w * 0.22f));
        drawLeftInfo(c, w, h);
        drawRightInfo(c, w, h);
        drawFooter(c, w, h);
    }

    private void drawBackground(Canvas c, float w, float h) {
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(0, 0, w, h, 0xFF071019, 0xFF020508, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        p.setColor(0x1100D9C6);
        c.drawCircle(w * .50f, h * .50f, h * .40f, p);
        p.setColor(0x0C58A6FF);
        c.drawCircle(w * .86f, h * .15f, h * .32f, p);
    }

    private void drawHeader(Canvas c, float w, float h) {
        p.setTextAlign(Paint.Align.LEFT);
        p.setColor(0xFFF1F7FA);
        p.setTextSize(h * .044f);
        p.setFakeBoldText(true);
        c.drawText("H9 QIBLA", w * .045f, h * .075f, p);
        p.setFakeBoldText(false);

        p.setTextAlign(Paint.Align.RIGHT);
        p.setTextSize(h * .024f);
        p.setColor(hasLocation ? 0xFF5CE1C6 : 0xFFFFC857);
        c.drawText(status, w * .955f, h * .058f, p);
        p.setTextSize(h * .019f);
        p.setColor(networkStatus.contains("متصل") ? 0xFF5CE1C6 : 0xFFFFC857);
        c.drawText(networkStatus, w * .955f, h * .092f, p);
    }

    private void drawCompass(Canvas c, float cx, float cy, float r) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x0F00E8D0);
        c.drawCircle(cx, cy, r * 1.12f, p);
        stroke.setStrokeWidth(r * .006f);
        stroke.setColor(0x5538F0D1);
        c.drawCircle(cx, cy, r, stroke);
        stroke.setColor(0x2238F0D1);
        c.drawCircle(cx, cy, r * .84f, stroke);

        for (int deg = 0; deg < 360; deg += 5) {
            double a = Math.toRadians(deg - 90);
            boolean major = deg % 30 == 0;
            float len = major ? r * .075f : r * .035f;
            stroke.setStrokeWidth(major ? r * .009f : r * .004f);
            stroke.setColor(major ? 0x99D8F4EF : 0x335F7E86);
            float x1 = cx + (float)Math.cos(a) * (r - len);
            float y1 = cy + (float)Math.sin(a) * (r - len);
            float x2 = cx + (float)Math.cos(a) * r;
            float y2 = cy + (float)Math.sin(a) * r;
            c.drawLine(x1, y1, x2, y2, stroke);
        }

        float compassRotation = hasHeading ? -heading : 0f;
        drawCardinal(c, "N", cx, cy, r * .70f, compassRotation, 0xFFFFFFFF);
        drawCardinal(c, "E", cx, cy, r * .70f, 90f + compassRotation, 0xFF8BA0A8);
        drawCardinal(c, "S", cx, cy, r * .70f, 180f + compassRotation, 0xFF8BA0A8);
        drawCardinal(c, "W", cx, cy, r * .70f, 270f + compassRotation, 0xFF8BA0A8);

        if (hasLocation) {
            float arrowBearing = hasHeading ? normalize(qiblaBearing - heading) : qiblaBearing;
            float angle = arrowBearing > 180 ? arrowBearing - 360 : arrowBearing;
            c.save();
            c.rotate(angle, cx, cy);
            stroke.setStrokeWidth(r * .034f);
            stroke.setColor(0xFF35E6C7);
            c.drawLine(cx, cy + r * .03f, cx, cy - r * .47f, stroke);

            Path arrow = new Path();
            arrow.moveTo(cx, cy - r * .69f);
            arrow.lineTo(cx - r * .105f, cy - r * .47f);
            arrow.lineTo(cx + r * .105f, cy - r * .47f);
            arrow.close();
            p.setColor(0xFF35E6C7);
            p.setShadowLayer(18f, 0, 0, 0xAA35E6C7);
            c.drawPath(arrow, p);
            p.clearShadowLayer();
            c.restore();
        }

        p.setColor(0xFF0B151B);
        c.drawRoundRect(new RectF(cx-r*.18f, cy-r*.18f, cx+r*.18f, cy+r*.18f), r*.055f, r*.055f, p);
        stroke.setStrokeWidth(r*.008f);
        stroke.setColor(0xFFCCA84A);
        c.drawRoundRect(new RectF(cx-r*.18f, cy-r*.18f, cx+r*.18f, cy+r*.18f), r*.055f, r*.055f, stroke);
        p.setColor(0xFFCCA84A);
        c.drawRect(cx-r*.18f, cy-r*.035f, cx+r*.18f, cy+r*.01f, p);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(r*.09f);
        p.setColor(0xFFE9D18B);
        c.drawText("الكعبة", cx, cy+r*.085f, p);

        p.setColor(0xFF10222B);
        c.drawRoundRect(new RectF(cx-r*.50f, cy+r*.76f, cx+r*.50f, cy+r*.94f), r*.075f, r*.075f, p);
        p.setColor(0xFFEAF5F4);
        p.setTextSize(r*.061f);
        p.setTextAlign(Paint.Align.CENTER);
        String msg;
        if (!hasLocation) {
            msg = "بانتظار تحديد الموقع";
        } else if (hasHeading) {
            float relative = normalize(qiblaBearing - heading);
            float signed = relative > 180 ? relative - 360 : relative;
            String side = signed >= 0 ? "يمين" : "يسار";
            msg = String.format(Locale.US, "القبلة %.0f° %s • %.0f° من الشمال", Math.abs(signed), side, qiblaBearing);
        } else {
            msg = String.format(Locale.US, "القبلة %.0f° من الشمال • بانتظار الاتجاه الحي", qiblaBearing);
        }
        c.drawText(msg, cx, cy+r*.875f, p);
    }

    private void drawCardinal(Canvas c, String text, float cx, float cy, float rr, float deg, int color) {
        double a = Math.toRadians(deg - 90);
        float x = cx + (float)Math.cos(a) * rr;
        float y = cy + (float)Math.sin(a) * rr;
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(rr*.11f);
        p.setColor(color);
        p.setFakeBoldText(text.equals("N"));
        Paint.FontMetrics fm = p.getFontMetrics();
        c.drawText(text, x, y - (fm.ascent+fm.descent)/2, p);
        p.setFakeBoldText(false);
    }

    private void drawLeftInfo(Canvas c, float w, float h) {
        float x = w*.055f;
        p.setTextAlign(Paint.Align.LEFT);
        p.setColor(0xFF6F8992); p.setTextSize(h*.025f);
        c.drawText("زاوية القبلة من الشمال", x, h*.29f, p);
        p.setColor(0xFFF4FAFA); p.setTextSize(h*.070f); p.setFakeBoldText(true);
        c.drawText(hasLocation ? String.format(Locale.US, "%.0f°", qiblaBearing) : "—", x, h*.37f, p);
        p.setFakeBoldText(false);

        p.setColor(0xFF6F8992); p.setTextSize(h*.025f);
        c.drawText("اتجاه الحركة", x, h*.52f, p);
        p.setColor(0xFFF4FAFA); p.setTextSize(h*.052f);
        c.drawText(hasHeading ? String.format(Locale.US, "%.0f°", heading) : "—", x, h*.59f, p);
        p.setColor(hasHeading ? 0xFF35E6C7 : 0xFFFFC857); p.setTextSize(h*.020f);
        c.drawText(headingSource, x, h*.635f, p);
    }

    private void drawRightInfo(Canvas c, float w, float h) {
        float x = w*.945f;
        p.setTextAlign(Paint.Align.RIGHT);
        p.setColor(0xFF6F8992); p.setTextSize(h*.025f);
        c.drawText("المسافة إلى مكة", x, h*.29f, p);
        p.setColor(0xFFF4FAFA); p.setTextSize(h*.064f); p.setFakeBoldText(true);
        c.drawText(hasLocation ? String.format(Locale.US, "%,.0f كم", distanceKm) : "—", x, h*.37f, p);
        p.setFakeBoldText(false);
        p.setColor(0xFF6F8992); p.setTextSize(h*.025f);
        c.drawText("طريقة التحديد", x, h*.52f, p);
        p.setColor(0xFFE8CE81); p.setTextSize(h*.038f);
        c.drawText("GPS حي + الشبكة", x, h*.585f, p);
        p.setColor(0xFF6F8992); p.setTextSize(h*.020f);
        c.drawText("السهم يتغير مع اتجاه الحركة", x, h*.63f, p);
    }

    private void drawFooter(Canvas c, float w, float h) {
        stroke.setStrokeWidth(1f);
        stroke.setColor(0x224F6972);
        c.drawLine(w*.045f, h*.91f, w*.955f, h*.91f, stroke);
        p.setTextSize(h*.019f); p.setColor(0xFF50666E); p.setTextAlign(Paint.Align.CENTER);
        c.drawText("H9 Qibla 0.8 Stable   •   سهم حي عبر GPS", w*.5f, h*.958f, p);
    }

    private static float calculateBearing(double lat, double lon) {
        double phi1 = Math.toRadians(lat), phi2 = Math.toRadians(KAABA_LAT);
        double dl = Math.toRadians(KAABA_LON - lon);
        double y = Math.sin(dl) * Math.cos(phi2);
        double x = Math.cos(phi1)*Math.sin(phi2) - Math.sin(phi1)*Math.cos(phi2)*Math.cos(dl);
        return normalize((float)Math.toDegrees(Math.atan2(y, x)));
    }

    private static float normalize(float v) {
        v %= 360f;
        if (v < 0) v += 360f;
        return v;
    }
}
