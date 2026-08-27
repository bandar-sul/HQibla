package sa.h9.qibla;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

public class MainActivity extends Activity implements LocationListener {
    private static final int REQ_LOCATION = 1001;
    private static final float MIN_GPS_SPEED_MPS = 1.0f;
    private static final float MIN_COURSE_DISTANCE_M = 6.0f;
    private static final long MAX_COURSE_AGE_MS = 60000L;

    private LocationManager locationManager;
    private QiblaView qiblaView;
    private Location lastLocation;
    private Location courseAnchor;
    private Float lastGpsHeading = null;

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
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 700L, 0.5f, this);
                Location l = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (l != null) updateLocation(l);
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2500L, 5f, this);
                if (lastLocation == null) {
                    Location l = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                    if (l != null) updateLocation(l);
                }
            }
        } catch (SecurityException ignored) { }
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

    @Override
    protected void onResume() {
        super.onResume();
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) startLocation();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { locationManager.removeUpdates(this); } catch (SecurityException ignored) { }
    }

    private void updateLocation(Location location) {
        lastLocation = location;
        qiblaView.setLocation(location);

        if (LocationManager.GPS_PROVIDER.equals(location.getProvider())) {
            updateGpsCourse(location);
        }

        if (lastGpsHeading != null) {
            String source = location.hasSpeed() && location.getSpeed() >= MIN_GPS_SPEED_MPS
                    ? "اتجاه حركة السيارة عبر GPS"
                    : "آخر اتجاه حركة GPS";
            qiblaView.setHeading(lastGpsHeading, source);
        } else {
            qiblaView.clearHeading("تحرك بالسيارة لتحديد الاتجاه");
        }
    }

    private void updateGpsCourse(Location location) {
        if (location.hasBearing() && location.hasSpeed() && location.getSpeed() >= MIN_GPS_SPEED_MPS) {
            lastGpsHeading = normalize(location.getBearing());
            courseAnchor = new Location(location);
            return;
        }

        if (courseAnchor == null) {
            courseAnchor = new Location(location);
            return;
        }

        long age = Math.abs(location.getTime() - courseAnchor.getTime());
        if (age > MAX_COURSE_AGE_MS) {
            courseAnchor = new Location(location);
            return;
        }

        float distance = courseAnchor.distanceTo(location);
        if (distance >= MIN_COURSE_DISTANCE_M) {
            lastGpsHeading = normalize(courseAnchor.bearingTo(location));
            courseAnchor = new Location(location);
        }
    }

    private static float normalize(float value) {
        value %= 360f;
        if (value < 0) value += 360f;
        return value;
    }

    @Override public void onLocationChanged(Location location) { updateLocation(location); }
    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
    @Override public void onProviderEnabled(String provider) { qiblaView.setStatus("GPS متصل"); }
    @Override public void onProviderDisabled(String provider) { qiblaView.setStatus("GPS غير متاح"); }
}
