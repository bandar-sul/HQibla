package sa.h9.qibla;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements LocationListener {
    private static final int REQ_LOCATION = 1001;
    private static final float GPS_HEADING_MIN_SPEED_MPS = 0.8f;
    private static final float COURSE_MIN_DISTANCE_M = 3.0f;
    private static final long HEADING_STALE_MS = 7000L;
    private static final long PRAYER_REFRESH_MS = 30L * 60L * 1000L;
    private static final String NOTICE_PREFS = "h9_qibla_notice";
    private static final String KEY_NOTICE_SHOWN = "developer_notice_shown_v2";

    private LocationManager locationManager;
    private ConnectivityManager connectivityManager;
    private QiblaView qiblaView;
    private Location lastLocation;
    private Location courseAnchor;
    private long lastHeadingAt;
    private long lastPrayerCalcAt;
    private Location lastPrayerLocation;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable uiTick = new Runnable() {
        @Override public void run() {
            qiblaView.tick();
            if (lastHeadingAt > 0 && System.currentTimeMillis() - lastHeadingAt > HEADING_STALE_MS) {
                qiblaView.clearHeading("بانتظار اتجاه GPS حي");
                lastHeadingAt = 0;
                courseAnchor = null;
            }
            handler.postDelayed(this, 1000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        FrameLayout root = new FrameLayout(this);
        qiblaView = new QiblaView(this);
        root.addView(qiblaView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        Button aboutButton = new Button(this);
        aboutButton.setText("حول");
        aboutButton.setTextSize(18f);
        aboutButton.setTextColor(0xFFF1F7FA);
        aboutButton.setAllCaps(false);
        aboutButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        GradientDrawable aboutBg = new GradientDrawable();
        aboutBg.setColor(0xCC0B1B24);
        aboutBg.setCornerRadius(dp(18));
        aboutBg.setStroke(dp(1), 0xFF2FE0C7);
        aboutButton.setBackground(aboutBg);
        aboutButton.setOnClickListener(v -> showAboutDialog());
        FrameLayout.LayoutParams aboutLp = new FrameLayout.LayoutParams(dp(130), dp(52));
        aboutLp.gravity = Gravity.BOTTOM | Gravity.LEFT;
        aboutLp.leftMargin = dp(24);
        aboutLp.bottomMargin = dp(24);
        root.addView(aboutButton, aboutLp);
        setContentView(root);

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        updateNetworkStatus();
        showDeveloperNoticeOnce();
        ensureLocationPermission();
    }

    private void showAboutDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(38), dp(24), dp(38), dp(28));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(0xF2171D23);
        cardBg.setCornerRadius(dp(28));
        cardBg.setStroke(dp(2), 0xFFE5B64A);
        card.setBackground(cardBg);

        TextView appTitle = noteText("H9 Qibla", 20f, 0xFFE5B64A, true);
        card.addView(appTitle, matchWrap(dp(44)));
        TextView title = noteText("حول", 30f, 0xFFFFC857, true);
        card.addView(title, matchWrap(dp(58)));
        TextView body = noteText("تم تطوير البرنامج بواسطة هافال H9 صدقة جارية\n\nللتواصل عبر الواتساب\n0539100194\n\nمتجر H9", 21f, 0xFFF1F5F6, false);
        body.setLineSpacing(dp(2), 1.08f);
        card.addView(body, matchWrap(WindowManager.LayoutParams.WRAP_CONTENT));

        TextView store = noteText("https://h9-story.com", 22f, 0xFFFFC857, true);
        store.setPadding(dp(8), dp(12), dp(8), dp(12));
        store.setOnClickListener(v -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://h9-story.com"))); } catch (Exception ignored) { }
        });
        card.addView(store, matchWrap(dp(58)));

        Button close = new Button(this);
        close.setText("إغلاق");
        close.setTextSize(20f);
        close.setTextColor(Color.WHITE);
        close.setAllCaps(false);
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setColor(0xFF117A7C);
        closeBg.setCornerRadius(dp(24));
        closeBg.setStroke(dp(1), 0xFF2FE0C7);
        close.setBackground(closeBg);
        close.setOnClickListener(v -> dialog.dismiss());
        card.addView(close, new LinearLayout.LayoutParams(dp(220), dp(56)));

        dialog.setContentView(card);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.dimAmount = 0.72f;
            window.setAttributes(attrs);
        }
        dialog.show();
        if (window != null) { window.setLayout(dp(650), WindowManager.LayoutParams.WRAP_CONTENT); window.setGravity(Gravity.CENTER); }
    }

    private void showDeveloperNoticeOnce() {
        final SharedPreferences prefs = getSharedPreferences(NOTICE_PREFS, MODE_PRIVATE);
        if (prefs.getBoolean(KEY_NOTICE_SHOWN, false)) return;

        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(false);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(38), dp(24), dp(38), dp(28));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(0xF2171D23);
        cardBg.setCornerRadius(dp(28));
        cardBg.setStroke(dp(2), 0xFFE5B64A);
        card.setBackground(cardBg);

        TextView appTitle = noteText("H9 Qibla", 20f, 0xFFE5B64A, true);
        card.addView(appTitle, matchWrap(dp(46)));

        View divider = new View(this);
        GradientDrawable dividerBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0x001E4F54, 0xFFE5B64A, 0x001E4F54});
        divider.setBackground(dividerBg);
        LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(dp(320), dp(1));
        dividerLp.bottomMargin = dp(14);
        card.addView(divider, dividerLp);

        TextView title = noteText("ملاحظة", 30f, 0xFFFFC857, true);
        LinearLayout.LayoutParams titleLp = matchWrap(dp(58));
        titleLp.bottomMargin = dp(8);
        card.addView(title, titleLp);

        TextView body = noteText(
                "تم تطوير هذا البرنامج بواسطة / بندر بن سليمان\n\n" +
                "هذا العمل أجر عني وعنك وعن كل مسلم\n\n" +
                "حساب تيك توك و سناب شات : h9_haval\n\n" +
                "لا تنسون زيارة متجرنا",
                20f, 0xFFF1F5F6, false);
        body.setLineSpacing(dp(2), 1.08f);
        LinearLayout.LayoutParams bodyLp = matchWrap(WindowManager.LayoutParams.WRAP_CONTENT);
        bodyLp.bottomMargin = dp(10);
        card.addView(body, bodyLp);

        TextView store = noteText("https://h9-story.com", 22f, 0xFFFFC857, true);
        store.setPadding(dp(12), dp(8), dp(12), dp(12));
        store.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://h9-story.com")));
            } catch (Exception ignored) { }
        });
        LinearLayout.LayoutParams storeLp = matchWrap(dp(52));
        storeLp.bottomMargin = dp(14);
        card.addView(store, storeLp);

        Button continueButton = new Button(this);
        continueButton.setText("متابعة");
        continueButton.setTextSize(21f);
        continueButton.setTextColor(Color.WHITE);
        continueButton.setAllCaps(false);
        continueButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        continueButton.setGravity(Gravity.CENTER);
        continueButton.setPadding(0, 0, 0, 0);
        GradientDrawable buttonBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF0B5960, 0xFF117A7C, 0xFF0B5960});
        buttonBg.setCornerRadius(dp(24));
        buttonBg.setStroke(dp(1), 0xFF2FE0C7);
        continueButton.setBackground(buttonBg);
        continueButton.setOnClickListener(v -> {
            prefs.edit().putBoolean(KEY_NOTICE_SHOWN, true).apply();
            dialog.dismiss();
        });
        LinearLayout.LayoutParams buttonLp = new LinearLayout.LayoutParams(dp(230), dp(58));
        card.addView(continueButton, buttonLp);

        dialog.setContentView(card);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.dimAmount = 0.72f;
            window.setAttributes(attrs);
        }
        dialog.show();
        if (window != null) {
            window.setLayout(dp(650), WindowManager.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
        }
    }

    private TextView noteText(String text, float sizeSp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        view.setTextDirection(View.TEXT_DIRECTION_RTL);
        view.setTypeface(Typeface.create("sans", bold ? Typeface.BOLD : Typeface.NORMAL));
        return view;
    }

    private LinearLayout.LayoutParams matchWrap(int height) {
        return new LinearLayout.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, height);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void ensureLocationPermission() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
        } else {
            startLocation();
        }
    }

    private void startLocation() {
        updateNetworkStatus();
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 400L, 0.2f, this);
                Location gps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (gps != null) updateLocation(gps);
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1200L, 1f, this);
                Location network = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (network != null && isBetterLocation(network, lastLocation)) updateLocation(network);
            }
        } catch (SecurityException ignored) {
            qiblaView.setStatus("تعذر الوصول إلى الموقع");
        }
    }

    private boolean isBetterLocation(Location candidate, Location current) {
        if (current == null) return true;
        long dt = candidate.getTime() - current.getTime();
        if (dt > 30000L) return true;
        if (dt < -30000L) return false;
        if (!candidate.hasAccuracy()) return false;
        return !current.hasAccuracy() || candidate.getAccuracy() < current.getAccuracy();
    }

    private boolean isNetworkConnected() {
        try {
            NetworkInfo info = connectivityManager != null ? connectivityManager.getActiveNetworkInfo() : null;
            return info != null && info.isConnected();
        } catch (Exception ignored) {
            return false;
        }
    }

    private void updateNetworkStatus() {
        qiblaView.setNetworkStatus(isNetworkConnected() ? "الإنترنت متصل" : "بدون إنترنت");
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateNetworkStatus();
        handler.removeCallbacks(uiTick);
        handler.post(uiTick);
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) startLocation();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(uiTick);
        try { locationManager.removeUpdates(this); } catch (SecurityException ignored) { }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocation();
        } else {
            qiblaView.setStatus("يلزم السماح بالموقع لحساب القبلة");
        }
    }

    private void updateLocation(Location location) {
        if (!isBetterLocation(location, lastLocation) && lastLocation != null &&
                !LocationManager.GPS_PROVIDER.equals(location.getProvider())) return;

        lastLocation = location;
        qiblaView.setLocation(location);
        updateNetworkStatus();

        if (LocationManager.GPS_PROVIDER.equals(location.getProvider())) {
            updateLiveCourse(location);
        }
        maybeRefreshPrayerTimes(location);
        if (isNetworkConnected()) maybeResolveLocationName(location);
    }

    private void updateLiveCourse(Location location) {
        Float newHeading = null;

        if (location.hasBearing() && (!location.hasSpeed() || location.getSpeed() >= GPS_HEADING_MIN_SPEED_MPS)) {
            newHeading = normalize(location.getBearing());
        }

        if (courseAnchor == null) {
            courseAnchor = new Location(location);
        } else {
            float moved = courseAnchor.distanceTo(location);
            if (moved >= COURSE_MIN_DISTANCE_M) {
                float positionCourse = normalize(courseAnchor.bearingTo(location));
                newHeading = newHeading == null ? positionCourse : circularBlend(newHeading, positionCourse, 0.30f);
                courseAnchor = new Location(location);
            }
        }

        if (newHeading != null) {
            lastHeadingAt = System.currentTimeMillis();
            qiblaView.setHeading(newHeading, "اتجاه حي عبر GPS");
        }
    }

    private void maybeRefreshPrayerTimes(Location location) {
        long now = System.currentTimeMillis();
        boolean movedFar = lastPrayerLocation == null || lastPrayerLocation.distanceTo(location) > 10000f;
        if (!movedFar && now - lastPrayerCalcAt < PRAYER_REFRESH_MS) return;

        lastPrayerCalcAt = now;
        lastPrayerLocation = new Location(location);
        qiblaView.setPrayerStatus("حساب مواقيت الصلاة محليًا…");

        Calendar calendar = Calendar.getInstance();
        PrayerTimesCalculator.Result result = PrayerTimesCalculator.calculate(
                location.getLatitude(), location.getLongitude(), calendar);
        String[] names = {"الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"};
        qiblaView.setPrayerTimes(names, result.times24, result.hijriDate);
        qiblaView.setPrayerStatus("أم القرى • حساب محلي بدون إنترنت");
    }

    private void maybeResolveLocationName(Location location) {
        final double lat = location.getLatitude();
        final double lon = location.getLongitude();
        new Thread(() -> resolveLocationName(lat, lon)).start();
    }

    private void resolveLocationName(double lat, double lon) {
        try {
            Geocoder geocoder = new Geocoder(this, new Locale("ar"));
            List<android.location.Address> results = geocoder.getFromLocation(lat, lon, 1);
            if (results != null && !results.isEmpty()) {
                android.location.Address a = results.get(0);
                String name = a.getLocality();
                if (name == null || name.trim().isEmpty()) name = a.getSubAdminArea();
                if (name == null || name.trim().isEmpty()) name = a.getAdminArea();
                if (name != null && !name.trim().isEmpty()) {
                    final String finalName = name;
                    runOnUiThread(() -> qiblaView.setLocationName(finalName));
                }
            }
        } catch (Exception ignored) { }
    }

    private static float circularBlend(float a, float b, float weightB) {
        float delta = signedAngle(b - a);
        return normalize(a + delta * weightB);
    }

    private static float signedAngle(float value) {
        value %= 360f;
        if (value > 180f) value -= 360f;
        if (value < -180f) value += 360f;
        return value;
    }

    private static float normalize(float value) {
        value %= 360f;
        if (value < 0) value += 360f;
        return value;
    }

    @Override public void onLocationChanged(Location location) { updateLocation(location); }
    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
    @Override public void onProviderEnabled(String provider) { qiblaView.setStatus("الموقع متصل"); }
    @Override public void onProviderDisabled(String provider) {
        if (LocationManager.GPS_PROVIDER.equals(provider)) qiblaView.setStatus("GPS غير متاح");
    }
}
