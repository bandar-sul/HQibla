package sa.h9.qibla;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
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
    private static final String KEY_NOTICE_SHOWN = "developer_notice_shown_v1";

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

        qiblaView = new QiblaView(this);
        setContentView(qiblaView);

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        updateNetworkStatus();
        showDeveloperNoticeOnce();
        ensureLocationPermission();
    }

    private void showDeveloperNoticeOnce() {
        SharedPreferences prefs = getSharedPreferences(NOTICE_PREFS, MODE_PRIVATE);
        if (prefs.getBoolean(KEY_NOTICE_SHOWN, false)) return;

        final String text = "ملاحظة :\n\n" +
                "تم تطوير هذا البرنامج بواسطة / بندر بن سليمان\n" +
                "هذا العمل أجر عني وعنك وعن كل مسلم\n\n" +
                "حساب تيك توك و سناب شات : h9_haval\n\n" +
                "لا تنسون زيارة متجرنا\nhttps://h9-story.com";

        TextView message = new TextView(this);
        message.setText(text);
        message.setTextSize(20f);
        message.setTextColor(0xFFF1F7FA);
        message.setPadding(42, 30, 42, 12);
        message.setTextDirection(View.TEXT_DIRECTION_RTL);
        message.setMovementMethod(LinkMovementMethod.getInstance());
        message.setAutoLinkMask(android.text.util.Linkify.WEB_URLS);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("H9 Qibla")
                .setView(message)
                .setCancelable(false)
                .setPositiveButton("متابعة", (d, which) ->
                        prefs.edit().putBoolean(KEY_NOTICE_SHOWN, true).apply())
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(0xFF2FE0C7);
        });
        dialog.show();
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
