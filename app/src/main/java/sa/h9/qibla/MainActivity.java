package sa.h9.qibla;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

public class MainActivity extends Activity implements LocationListener {
    private static final int REQ_LOCATION = 1001;
    private static final float GPS_HEADING_MIN_SPEED_MPS = 0.8f;
    private static final float COURSE_MIN_DISTANCE_M = 3.0f;
    private static final String PREFS = "h9_qibla_live_heading";
    private static final String KEY_HEADING = "last_heading";
    private static final String KEY_HAS_HEADING = "has_heading";

    private LocationManager locationManager;
    private ConnectivityManager connectivityManager;
    private QiblaView qiblaView;
    private Location lastLocation;
    private Location courseAnchor;
    private SharedPreferences prefs;
    private float lastHeading;
    private boolean hasLastHeading;

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

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        lastHeading = prefs.getFloat(KEY_HEADING, 0f);
        hasLastHeading = prefs.getBoolean(KEY_HAS_HEADING, false);
        if (hasLastHeading) {
            qiblaView.setHeading(lastHeading, "آخر اتجاه GPS محفوظ");
        }

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        updateNetworkStatus();
        ensureLocationPermission();
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
        return candidate.getAccuracy() < current.getAccuracy();
    }

    private void updateNetworkStatus() {
        boolean connected = false;
        try {
            NetworkInfo info = connectivityManager != null ? connectivityManager.getActiveNetworkInfo() : null;
            connected = info != null && info.isConnected();
        } catch (Exception ignored) { }
        qiblaView.setNetworkStatus(connected ? "الإنترنت متصل" : "بدون إنترنت");
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateNetworkStatus();
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) startLocation();
    }

    @Override
    protected void onPause() {
        super.onPause();
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
    }

    private void updateLiveCourse(Location location) {
        Float newHeading = null;

        if (location.hasBearing() &&
                (!location.hasSpeed() || location.getSpeed() >= GPS_HEADING_MIN_SPEED_MPS)) {
            newHeading = normalize(location.getBearing());
        }

        if (courseAnchor == null) {
            courseAnchor = new Location(location);
        } else {
            float moved = courseAnchor.distanceTo(location);
            if (moved >= COURSE_MIN_DISTANCE_M) {
                float positionCourse = normalize(courseAnchor.bearingTo(location));
                if (newHeading == null) {
                    newHeading = positionCourse;
                } else {
                    newHeading = circularBlend(newHeading, positionCourse, 0.30f);
                }
                courseAnchor = new Location(location);
            }
        }

        if (newHeading != null) {
            lastHeading = newHeading;
            hasLastHeading = true;
            prefs.edit()
                    .putFloat(KEY_HEADING, lastHeading)
                    .putBoolean(KEY_HAS_HEADING, true)
                    .apply();
            qiblaView.setHeading(lastHeading, "اتجاه حي عبر GPS");
        } else if (hasLastHeading) {
            qiblaView.setHeading(lastHeading, "آخر اتجاه GPS محفوظ");
        } else {
            qiblaView.clearHeading("تحرك عدة أمتار لتفعيل الاتجاه الحي");
        }
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
