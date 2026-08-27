package sa.h9.qibla;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.Surface;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

public class MainActivity extends Activity implements LocationListener, SensorEventListener {
    private static final int REQ_LOCATION = 1001;
    private static final float AUTO_CAL_MIN_SPEED_MPS = 2.5f;
    private static final float AUTO_CAL_MAX_ACCURACY_M = 25f;
    private static final String PREFS = "h9_qibla_calibration";
    private static final String KEY_OFFSET = "sensor_offset_deg";
    private static final String KEY_CALIBRATED = "sensor_calibrated";

    private LocationManager locationManager;
    private SensorManager sensorManager;
    private Sensor rotationSensor;
    private Sensor accelerometer;
    private Sensor magnetometer;
    private final float[] accel = new float[3];
    private final float[] mag = new float[3];
    private boolean haveAccel;
    private boolean haveMag;
    private boolean sensorHeadingAvailable;

    private QiblaView qiblaView;
    private Location lastLocation;
    private float magneticAzimuth;
    private float calibrationOffset;
    private boolean calibrated;
    private SharedPreferences prefs;

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
        calibrationOffset = prefs.getFloat(KEY_OFFSET, 0f);
        calibrated = prefs.getBoolean(KEY_CALIBRATED, false);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        sensorHeadingAvailable = rotationSensor != null || (accelerometer != null && magnetometer != null);

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
    protected void onResume() {
        super.onResume();
        if (rotationSensor != null) {
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME);
        } else {
            if (accelerometer != null) sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
            if (magnetometer != null) sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_GAME);
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) startLocation();
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
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

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            float[] raw = new float[9];
            SensorManager.getRotationMatrixFromVector(raw, event.values);
            magneticAzimuth = azimuthForDisplay(raw);
            publishSensorHeading();
        } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, accel, 0, 3);
            haveAccel = true;
            computeFallbackOrientation();
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, mag, 0, 3);
            haveMag = true;
            computeFallbackOrientation();
        }
    }

    private void computeFallbackOrientation() {
        if (!haveAccel || !haveMag) return;
        float[] raw = new float[9];
        float[] inclination = new float[9];
        if (SensorManager.getRotationMatrix(raw, inclination, accel, mag)) {
            magneticAzimuth = azimuthForDisplay(raw);
            publishSensorHeading();
        }
    }

    private float azimuthForDisplay(float[] raw) {
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int axisX = SensorManager.AXIS_X;
        int axisY = SensorManager.AXIS_Y;
        if (rotation == Surface.ROTATION_90) {
            axisX = SensorManager.AXIS_Y;
            axisY = SensorManager.AXIS_MINUS_X;
        } else if (rotation == Surface.ROTATION_180) {
            axisX = SensorManager.AXIS_MINUS_X;
            axisY = SensorManager.AXIS_MINUS_Y;
        } else if (rotation == Surface.ROTATION_270) {
            axisX = SensorManager.AXIS_MINUS_Y;
            axisY = SensorManager.AXIS_X;
        }
        float[] remapped = new float[9];
        if (!SensorManager.remapCoordinateSystem(raw, axisX, axisY, remapped)) remapped = raw;
        float[] orientation = new float[3];
        SensorManager.getOrientation(remapped, orientation);
        return normalize((float) Math.toDegrees(orientation[0]));
    }

    private float trueSensorHeading() {
        float heading = magneticAzimuth;
        if (lastLocation != null) {
            GeomagneticField field = new GeomagneticField(
                    (float) lastLocation.getLatitude(),
                    (float) lastLocation.getLongitude(),
                    (float) lastLocation.getAltitude(),
                    System.currentTimeMillis());
            heading = normalize(heading + field.getDeclination());
        }
        return heading;
    }

    private void publishSensorHeading() {
        if (!sensorHeadingAvailable) {
            qiblaView.clearHeading("لا توجد بوصلة في الشاشة");
            return;
        }
        float corrected = normalize(trueSensorHeading() + calibrationOffset);
        String source = calibrated ? "بوصلة H9 المعايرة" : "بوصلة H9 • معايرة تلقائية";
        qiblaView.setHeading(corrected, source);
    }

    private void updateLocation(Location location) {
        lastLocation = location;
        qiblaView.setLocation(location);

        if (LocationManager.GPS_PROVIDER.equals(location.getProvider()) &&
                location.hasBearing() && location.hasSpeed() &&
                location.getSpeed() >= AUTO_CAL_MIN_SPEED_MPS &&
                (!location.hasAccuracy() || location.getAccuracy() <= AUTO_CAL_MAX_ACCURACY_M) &&
                sensorHeadingAvailable) {
            autoCalibrate(location.getBearing());
        }

        publishSensorHeading();
    }

    private void autoCalibrate(float gpsBearing) {
        float sensorTrue = trueSensorHeading();
        float candidate = signedAngle(normalize(gpsBearing) - sensorTrue);

        if (!calibrated) {
            calibrationOffset = candidate;
            calibrated = true;
        } else {
            float error = signedAngle(candidate - calibrationOffset);
            calibrationOffset = signedAngle(calibrationOffset + error * 0.18f);
        }

        prefs.edit()
                .putFloat(KEY_OFFSET, calibrationOffset)
                .putBoolean(KEY_CALIBRATED, true)
                .apply();
    }

    private static float normalize(float value) {
        value %= 360f;
        if (value < 0) value += 360f;
        return value;
    }

    private static float signedAngle(float value) {
        value %= 360f;
        if (value > 180f) value -= 360f;
        if (value < -180f) value += 360f;
        return value;
    }

    @Override public void onLocationChanged(Location location) { updateLocation(location); }
    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
    @Override public void onProviderEnabled(String provider) { qiblaView.setStatus("GPS متصل"); }
    @Override public void onProviderDisabled(String provider) { qiblaView.setStatus("GPS غير متاح"); }
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
}
