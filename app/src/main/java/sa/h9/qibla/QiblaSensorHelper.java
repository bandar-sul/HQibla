package sa.h9.qibla;

import android.content.Context;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

/**
 * Helper class that uses Android's SensorManager to monitor rotation vector sensors
 * (with fallback to accelerometer + magnetic sensors) and computes real-time device heading,
 * Kaaba (Qibla) geographic bearing, and the relative angle from the device/vehicle to the Kaaba.
 */
public class QiblaSensorHelper implements SensorEventListener {

    public static final double KAABA_LATITUDE = 21.422487;
    public static final double KAABA_LONGITUDE = 39.826206;

    private static final float SMOOTHING_FACTOR = 0.25f; // Exponential circular smoothing
    private static final int DEFAULT_SAMPLING_PERIOD_US = 20000; // 50 Hz

    public interface OnQiblaCompassListener {
        /**
         * Called when sensor or location updates compute a new Qibla state.
         *
         * @param deviceAzimuth      Device heading relative to True North (0..360 degrees).
         * @param qiblaBearing       Direction to the Kaaba relative to True North (0..360 degrees).
         * @param relativeQiblaAngle Needle deviation angle relative to the device/vehicle (0..360 degrees;
         *                           0 means the device is pointing directly towards the Kaaba).
         * @param distanceMeters     Distance to the Kaaba in meters, or -1 if location is not set.
         * @param sensorAccuracy     Accuracy report from the sensor (e.g. SensorManager.SENSOR_STATUS_ACCURACY_HIGH).
         */
        void onQiblaUpdated(float deviceAzimuth, float qiblaBearing, float relativeQiblaAngle, float distanceMeters, int sensorAccuracy);
    }

    private final Context context;
    private final SensorManager sensorManager;
    private final WindowManager windowManager;
    private OnQiblaCompassListener listener;

    private Sensor rotationVectorSensor;
    private Sensor accelerometerSensor;
    private Sensor magneticSensor;

    private boolean isListening = false;
    private int currentAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE;

    // Location & calculation state
    private Location currentLocation;
    private float qiblaBearing = Float.NaN;
    private float distanceToKaaba = -1f;

    // Sensor buffers
    private final float[] rotationMatrix = new float[9];
    private final float[] remappedMatrix = new float[9];
    private final float[] orientationAngles = new float[3];
    private final float[] accelValues = new float[3];
    private final float[] magneticValues = new float[3];
    private boolean hasAccel = false;
    private boolean hasMagnetic = false;

    // Smoothing state
    private float lastRawAzimuth = Float.NaN;
    private float smoothedAzimuth = Float.NaN;

    public QiblaSensorHelper(Context context) {
        this(context, null);
    }

    public QiblaSensorHelper(Context context, OnQiblaCompassListener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.sensorManager = (SensorManager) this.context.getSystemService(Context.SENSOR_SERVICE);
        this.windowManager = (WindowManager) this.context.getSystemService(Context.WINDOW_SERVICE);
        initializeSensors();
    }

    private void initializeSensors() {
        if (sensorManager == null) return;

        // Primary: Hardware rotation vector sensor (fuses gyroscope + accelerometer + magnetometer)
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

        // Secondary fallback: Geomagnetic rotation vector
        if (rotationVectorSensor == null) {
            rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR);
        }

        // Tertiary fallback: Discrete accelerometer & magnetometer
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
    }

    public void setListener(OnQiblaCompassListener listener) {
        this.listener = listener;
    }

    /**
     * Updates the user's location, re-calculating the Qibla bearing and distance to the Kaaba.
     */
    public synchronized void setLocation(Location location) {
        if (location == null) return;
        this.currentLocation = location;
        recalculateQiblaBearing();
        dispatchUpdate();
    }

    /**
     * Updates the user's location with latitude and longitude.
     */
    public synchronized void setLocation(double latitude, double longitude) {
        Location loc = new Location("manual");
        loc.setLatitude(latitude);
        loc.setLongitude(longitude);
        loc.setTime(System.currentTimeMillis());
        setLocation(loc);
    }

    /**
     * Starts listening to rotation sensors.
     *
     * @return true if at least one sensor was successfully registered.
     */
    public synchronized boolean startListening() {
        return startListening(DEFAULT_SAMPLING_PERIOD_US);
    }

    /**
     * Starts listening with a specified sensor sampling rate in microseconds.
     */
    public synchronized boolean startListening(int samplingPeriodUs) {
        if (isListening || sensorManager == null) return isListening;

        boolean registered = false;
        if (rotationVectorSensor != null) {
            registered = sensorManager.registerListener(this, rotationVectorSensor, samplingPeriodUs);
        }

        if (!registered && accelerometerSensor != null && magneticSensor != null) {
            boolean aReg = sensorManager.registerListener(this, accelerometerSensor, samplingPeriodUs);
            boolean mReg = sensorManager.registerListener(this, magneticSensor, samplingPeriodUs);
            registered = aReg && mReg;
        }

        isListening = registered;
        return isListening;
    }

    /**
     * Unregisters sensor listeners and pauses updates.
     */
    public synchronized void stopListening() {
        if (!isListening || sensorManager == null) return;
        sensorManager.unregisterListener(this);
        isListening = false;
        hasAccel = false;
        hasMagnetic = false;
    }

    public boolean isListening() {
        return isListening;
    }

    public boolean isSensorAvailable() {
        return rotationVectorSensor != null || (accelerometerSensor != null && magneticSensor != null);
    }

    public float getDeviceAzimuth() {
        return smoothedAzimuth;
    }

    public float getQiblaBearing() {
        return qiblaBearing;
    }

    public float getRelativeQiblaAngle() {
        if (Float.isNaN(qiblaBearing) || Float.isNaN(smoothedAzimuth)) return Float.NaN;
        return normalize(qiblaBearing - smoothedAzimuth);
    }

    public float getDistanceToKaabaMeters() {
        return distanceToKaaba;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float computedAzimuth = Float.NaN;

        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR ||
            event.sensor.getType() == Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR) {

            if (event.values.length > 4) {
                float[] truncatedVector = new float[4];
                System.arraycopy(event.values, 0, truncatedVector, 0, 4);
                SensorManager.getRotationMatrixFromVector(rotationMatrix, truncatedVector);
            } else {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            }
            computedAzimuth = calculateAzimuthFromMatrix(rotationMatrix);
            currentAccuracy = event.accuracy;

        } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, accelValues, 0, 3);
            hasAccel = true;
            currentAccuracy = event.accuracy;
            if (hasMagnetic) {
                if (SensorManager.getRotationMatrix(rotationMatrix, null, accelValues, magneticValues)) {
                    computedAzimuth = calculateAzimuthFromMatrix(rotationMatrix);
                }
            }
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, magneticValues, 0, 3);
            hasMagnetic = true;
            currentAccuracy = event.accuracy;
            if (hasAccel) {
                if (SensorManager.getRotationMatrix(rotationMatrix, null, accelValues, magneticValues)) {
                    computedAzimuth = calculateAzimuthFromMatrix(rotationMatrix);
                }
            }
        }

        if (!Float.isNaN(computedAzimuth)) {
            // Apply Geomagnetic declination to obtain True North heading
            float trueNorthAzimuth = applyMagneticDeclination(computedAzimuth);
            lastRawAzimuth = trueNorthAzimuth;

            // Apply smooth circular blending to minimize sensor jitter
            if (Float.isNaN(smoothedAzimuth)) {
                smoothedAzimuth = trueNorthAzimuth;
            } else {
                smoothedAzimuth = circularBlend(smoothedAzimuth, trueNorthAzimuth, SMOOTHING_FACTOR);
            }

            dispatchUpdate();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        this.currentAccuracy = accuracy;
    }

    /**
     * Remaps the sensor rotation matrix according to display orientation (Landscape, Portrait, etc.)
     * and computes the azimuth angle.
     */
    private float calculateAzimuthFromMatrix(float[] matrix) {
        int rotation = Surface.ROTATION_0;
        if (windowManager != null) {
            Display display = windowManager.getDefaultDisplay();
            if (display != null) rotation = display.getRotation();
        }

        int axisX = SensorManager.AXIS_X;
        int axisY = SensorManager.AXIS_Y;

        switch (rotation) {
            case Surface.ROTATION_90:
                axisX = SensorManager.AXIS_Y;
                axisY = SensorManager.AXIS_MINUS_X;
                break;
            case Surface.ROTATION_180:
                axisX = SensorManager.AXIS_MINUS_X;
                axisY = SensorManager.AXIS_MINUS_Y;
                break;
            case Surface.ROTATION_270:
                axisX = SensorManager.AXIS_MINUS_Y;
                axisY = SensorManager.AXIS_X;
                break;
            case Surface.ROTATION_0:
            default:
                break;
        }

        if (!SensorManager.remapCoordinateSystem(matrix, axisX, axisY, remappedMatrix)) {
            return Float.NaN;
        }

        SensorManager.getOrientation(remappedMatrix, orientationAngles);
        return normalize((float) Math.toDegrees(orientationAngles[0]));
    }

    /**
     * Uses GeomagneticField when current location is known to convert magnetic azimuth to True North azimuth.
     */
    private float applyMagneticDeclination(float magneticAzimuth) {
        if (currentLocation == null) return magneticAzimuth;
        try {
            GeomagneticField field = new GeomagneticField(
                    (float) currentLocation.getLatitude(),
                    (float) currentLocation.getLongitude(),
                    currentLocation.hasAltitude() ? (float) currentLocation.getAltitude() : 0f,
                    System.currentTimeMillis()
            );
            return normalize(magneticAzimuth + field.getDeclination());
        } catch (Throwable ignored) {
            return magneticAzimuth;
        }
    }

    private void recalculateQiblaBearing() {
        if (currentLocation == null) {
            qiblaBearing = Float.NaN;
            distanceToKaaba = -1f;
            return;
        }

        qiblaBearing = calculateQiblaBearing(currentLocation.getLatitude(), currentLocation.getLongitude());
        distanceToKaaba = calculateDistanceToKaaba(currentLocation.getLatitude(), currentLocation.getLongitude());
    }

    private void dispatchUpdate() {
        if (listener == null) return;
        float relativeAngle = getRelativeQiblaAngle();
        listener.onQiblaUpdated(smoothedAzimuth, qiblaBearing, relativeAngle, distanceToKaaba, currentAccuracy);
    }

    // =========================================================================
    // Static Qibla & Math Helpers
    // =========================================================================

    /**
     * Computes the great-circle initial bearing (forward azimuth) from observer coordinates
     * to the Kaaba in Mecca (21.422487° N, 39.826206° E).
     *
     * @param lat Observer latitude in degrees.
     * @param lon Observer longitude in degrees.
     * @return Bearing in degrees from True North (0..360).
     */
    public static float calculateQiblaBearing(double lat, double lon) {
        double phi1 = Math.toRadians(lat);
        double phi2 = Math.toRadians(KAABA_LATITUDE);
        double deltaLambda = Math.toRadians(KAABA_LONGITUDE - lon);

        double y = Math.sin(deltaLambda) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2) - Math.sin(phi1) * Math.cos(phi2) * Math.cos(deltaLambda);

        return normalize((float) Math.toDegrees(Math.atan2(y, x)));
    }

    /**
     * Calculates the distance from the given coordinate to the Kaaba in meters.
     */
    public static float calculateDistanceToKaaba(double lat, double lon) {
        float[] results = new float[1];
        Location.distanceBetween(lat, lon, KAABA_LATITUDE, KAABA_LONGITUDE, results);
        return results[0];
    }

    /**
     * Computes the relative Qibla angle from the vehicle/device heading.
     * 0° indicates facing Mecca directly, 90° indicates 90° to the right, etc.
     */
    public static float calculateRelativeAngle(float qiblaBearing, float deviceHeading) {
        return normalize(qiblaBearing - deviceHeading);
    }

    /**
     * Normalizes an angle into the [0, 360) degree range.
     */
    public static float normalize(float degrees) {
        degrees %= 360f;
        if (degrees < 0) degrees += 360f;
        return degrees;
    }

    /**
     * Computes shortest circular angular interpolation between two angles in degrees.
     */
    public static float circularBlend(float currentAngle, float targetAngle, float weightTarget) {
        float delta = targetAngle - currentAngle;
        delta %= 360f;
        if (delta > 180f) delta -= 360f;
        if (delta < -180f) delta += 360f;
        return normalize(currentAngle + delta * weightTarget);
    }
}
