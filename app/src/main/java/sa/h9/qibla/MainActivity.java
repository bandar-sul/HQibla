package sa.h9.qibla;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.Surface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.IBinder;
import android.os.Parcel;
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

public class MainActivity extends Activity implements LocationListener, SensorEventListener {
    private static final int REQ_LOCATION = 1001;
    private static final float GPS_HEADING_MIN_SPEED_MPS = 0.8f;
    private static final float COURSE_MIN_DISTANCE_M = 3.0f;
    private static final long HEADING_STALE_MS = 7000L;
    private static final long PRAYER_REFRESH_MS = 30L * 60L * 1000L;
    private static final String NOTICE_PREFS = "h9_qibla_notice";
    private static final String KEY_NOTICE_SHOWN = "developer_notice_shown_v2";

    private LocationManager locationManager;
    private ConnectivityManager connectivityManager;
    private SensorManager sensorManager;
    private Sensor rotationVectorSensor;
    private Sensor accelerometerSensor;
    private Sensor magneticSensor;
    private final float[] accelValues = new float[3];
    private final float[] magneticValues = new float[3];
    private boolean haveAccel;
    private boolean haveMagnetic;
    private float lastSpeedMps;
    private long lastGpsHeadingAt;
    private QiblaView qiblaView;
    private TextView middlewareDiagnosticText;
    private TextView binderDiagnosticText;
    private TextView locationDiagnosticText;
    private boolean gwmBinderBound;
    private IBinder gwmMapBinder;
    private android.os.Binder gwmMapCallbackBinder;

    private final ServiceConnection gwmMapConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            gwmBinderBound = true;
            gwmMapBinder = service;
            String descriptor = "?";
            try { descriptor = service.getInterfaceDescriptor(); } catch (Exception ignored) { }
            updateBinderDiagnosticText("CONNECTED • MapControl=" + descriptor + " • probing GWMNaviEngineer Adapter...");
            probeGwmEngineerAdapter();
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            gwmBinderBound = false;
            gwmMapBinder = null;
            updateBinderDiagnosticText("DISCONNECTED • " + name.flattenToShortString());
        }

        @Override public void onBindingDied(ComponentName name) {
            gwmBinderBound = false;
            gwmMapBinder = null;
            updateBinderDiagnosticText("BINDING DIED • " + name.flattenToShortString());
        }

        @Override public void onNullBinding(ComponentName name) {
            gwmBinderBound = false;
            gwmMapBinder = null;
            updateBinderDiagnosticText("NULL BINDING • " + name.flattenToShortString());
        }
    };
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
        applyImmersiveMode();

        FrameLayout root = new FrameLayout(this);
        qiblaView = new QiblaView(this);
        root.addView(qiblaView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        middlewareDiagnosticText = createDiagnosticText("3.8 TEST • vehicleSignal: آخر فحص");
        FrameLayout.LayoutParams middlewareLp = new FrameLayout.LayoutParams(dp(900), dp(92));
        middlewareLp.gravity = Gravity.TOP | Gravity.LEFT;
        middlewareLp.leftMargin = dp(24);
        middlewareLp.topMargin = dp(132);
        root.addView(middlewareDiagnosticText, middlewareLp);

        binderDiagnosticText = createDiagnosticText("3.8 TEST • Binder: جاري البحث عن GWMMapService...");
        FrameLayout.LayoutParams binderLp = new FrameLayout.LayoutParams(dp(900), dp(170));
        binderLp.gravity = Gravity.TOP | Gravity.LEFT;
        binderLp.leftMargin = dp(24);
        binderLp.topMargin = dp(230);
        root.addView(binderDiagnosticText, binderLp);

        locationDiagnosticText = createDiagnosticText("3.8 TEST • الموقع: جاري فحص fused/GPS");
        FrameLayout.LayoutParams locationLp = new FrameLayout.LayoutParams(dp(700), dp(58));
        locationLp.gravity = Gravity.TOP | Gravity.LEFT;
        locationLp.leftMargin = dp(24);
        locationLp.topMargin = dp(405);
        root.addView(locationDiagnosticText, locationLp);

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
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            magneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        }
        updateNetworkStatus();
        showDeveloperNoticeOnce();
        ensureLocationPermission();
        testGwmMiddleware();
        testGwmMapBinder();
    }

    private TextView createDiagnosticText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(12f);
        view.setTextColor(0xFFF1F7FA);
        view.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        view.setPadding(dp(14), 0, dp(14), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xCC07151C);
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), 0xFF2FE0C7);
        view.setBackground(bg);
        return view;
    }

    private void applyImmersiveMode() {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersiveMode();
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
        TextView body = noteText("تم تطوير البرنامج بواسطة بندر بن سليمان حساب هافال H9 صدقة جارية عني وعنك وعن كل مسلم\n\nللتواصل عبر الواتساب\n0539100194\n\nمتجر H9", 21f, 0xFFF1F5F6, false);
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
        dialog.setOnDismissListener(d -> handler.postDelayed(this::applyImmersiveMode, 120L));
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
            handler.postDelayed(this::applyImmersiveMode, 120L);
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
            // HAVAL/GWM exposes its own fused provider through com.gwm.navi.app.
            // Request it first so we can test the factory Dead-Reckoning output directly.
            if (locationManager.getProvider("fused") != null && locationManager.isProviderEnabled("fused")) {
                locationManager.requestLocationUpdates("fused", 300L, 0f, this);
                Location fused = locationManager.getLastKnownLocation("fused");
                if (fused != null) updateLocation(fused);
                else updateLocationDiagnosticText("fused متاح • بانتظار أول موقع");
            } else {
                updateLocationDiagnosticText("fused غير متاح للنظام");
            }

            // With internet, network location can give a fast position without waiting for GNSS.
            if (isNetworkConnected() && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 0f, this);
                Location network = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (network != null && isBetterLocation(network, lastLocation)) updateLocation(network);
            }

            // GNSS remains the fallback and the reference source while moving.
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 400L, 0.2f, this);
                Location gps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (gps != null && lastLocation == null) updateLocation(gps);
            }
        } catch (SecurityException ignored) {
            qiblaView.setStatus("تعذر الوصول إلى الموقع");
            updateLocationDiagnosticText("لا توجد صلاحية موقع");
        } catch (IllegalArgumentException ignored) {
            updateLocationDiagnosticText("fused رفض الطلب • GPS احتياط");
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
        applyImmersiveMode();
        updateNetworkStatus();
        handler.removeCallbacks(uiTick);
        handler.post(uiTick);
        registerHeadingSensors();
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) startLocation();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(uiTick);
        if (sensorManager != null) sensorManager.unregisterListener(this);
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
        String provider = location.getProvider() == null ? "unknown" : location.getProvider();
        boolean isFused = "fused".equals(provider);

        // Never discard factory fused samples merely because the previous GPS fix is more accurate.
        if (!isFused && !isBetterLocation(location, lastLocation) && lastLocation != null &&
                !LocationManager.GPS_PROVIDER.equals(provider)) return;

        lastLocation = location;
        qiblaView.setLocation(location);
        updateNetworkStatus();

        if (isFused) {
            updateFusedCourse(location);
        } else if (LocationManager.GPS_PROVIDER.equals(provider)) {
            updateLiveCourse(location);
        }
        maybeRefreshPrayerTimes(location);
        if (isNetworkConnected()) maybeResolveLocationName(location);
    }

    private void updateFusedCourse(Location location) {
        float speed = location.hasSpeed() ? location.getSpeed() : -1f;
        String bearingText = location.hasBearing() ? String.format(Locale.US, "%.1f°", location.getBearing()) : "--";
        String speedText = speed >= 0f ? String.format(Locale.US, "%.2f m/s", speed) : "--";
        String accuracyText = location.hasAccuracy() ? String.format(Locale.US, "%.1f m", location.getAccuracy()) : "--";
        updateLocationDiagnosticText("Provider: fused   Bearing: " + bearingText + "   Speed: " + speedText + "   Accuracy: " + accuracyText);

        // For this test build we intentionally accept a fused bearing even at standstill.
        // This will tell us whether GWM's DR service preserves a real vehicle heading.
        if (location.hasBearing() && !Float.isNaN(location.getBearing())) {
            float fusedHeading = normalize(location.getBearing());
            lastHeadingAt = System.currentTimeMillis();
            qiblaView.setHeading(fusedHeading, "اتجاه fused من نظام هافال");
        }
    }

    private void testGwmMiddleware() {
        updateMiddlewareDiagnosticText("vehicleSignal: قراءة جميع الأعمدة...");
        new Thread(() -> {
            if (checkSelfPermission("com.gwm.navi.permission.READ_CONTENT") != PackageManager.PERMISSION_GRANTED) {
                runOnUiThread(() -> updateMiddlewareDiagnosticText("READ_CONTENT غير ممنوحة للتطبيق"));
                return;
            }
            Cursor c = null;
            try {
                Uri uri = Uri.parse("content://com.gwm.navi.middleware/vehicleSignal");
                c = getContentResolver().query(uri, null, null, null, null);
                if (c == null) {
                    runOnUiThread(() -> updateMiddlewareDiagnosticText("vehicleSignal=null"));
                    return;
                }
                String[] cols = c.getColumnNames();
                StringBuilder matches = new StringBuilder();
                StringBuilder all = new StringBuilder();
                String[] keys = new String[]{"heading","bearing","yaw","angle","direction","speed","steering","gyro","course","azimuth","orientation","wheel"};
                if (c.moveToFirst()) {
                    for (int i = 0; i < cols.length; i++) {
                        String name = cols[i];
                        String lower = name == null ? "" : name.toLowerCase(Locale.US);
                        String value;
                        try { value = c.isNull(i) ? "null" : c.getString(i); }
                        catch (Exception e) { value = "?"; }
                        if (value != null && value.length() > 24) value = value.substring(0, 24) + "…";
                        if (all.length() > 0) all.append(" | ");
                        all.append(name).append('=').append(value);
                        for (String key : keys) {
                            if (lower.contains(key)) {
                                if (matches.length() > 0) matches.append(" • ");
                                matches.append(name).append('=').append(value);
                                break;
                            }
                        }
                    }
                }
                String out = matches.length() > 0
                        ? "MATCH: " + matches + "\nALL(" + cols.length + "): " + all
                        : "لا توجد أسماء heading/yaw/speed مباشرة • ALL(" + cols.length + "): " + all;
                if (out.length() > 900) out = out.substring(0, 900) + "…";
                final String finalOut = out;
                runOnUiThread(() -> updateMiddlewareDiagnosticText(finalOut));
            } catch (SecurityException e) {
                runOnUiThread(() -> updateMiddlewareDiagnosticText("vehicleSignal SecurityException"));
            } catch (Exception e) {
                final String err = e.getClass().getSimpleName() + ": " + e.getMessage();
                runOnUiThread(() -> updateMiddlewareDiagnosticText(err));
            } finally {
                if (c != null) c.close();
            }
        }, "H9-VehicleSignal-FullProbe").start();
    }

    private void testGwmMapBinder() {
        try {
            Intent discover = new Intent("com.gwmmapsdk.action.GWMMapService");
            discover.setPackage("com.gwm.navi.app");
            List<ResolveInfo> services = getPackageManager().queryIntentServices(discover, 0);
            String found = services == null ? "0" : String.valueOf(services.size());

            Intent explicit = new Intent();
            explicit.setComponent(new ComponentName(
                    "com.gwm.navi.app",
                    "com.gwm.navi.service.external.NaviExternalServiceV4"));

            boolean bound = bindService(explicit, gwmMapConnection, Context.BIND_AUTO_CREATE);
            updateBinderDiagnosticText("serviceMatches=" + found + " • bindService=" + bound + " • waiting callback...");
        } catch (SecurityException e) {
            updateBinderDiagnosticText("SecurityException • " + shortMessage(e));
        } catch (Exception e) {
            updateBinderDiagnosticText(e.getClass().getSimpleName() + " • " + shortMessage(e));
        }
    }

    private void probeGwmEngineerAdapter() {
        synchronized (oemLocLog) {
            oemLocLog.setLength(0);
            oemLocLog.append("GWM CarService • gwm_car_setting PROBE\n");
        }
        new Thread(() -> {
            try {
                Context engCtx = createPackageContext("com.gwm.naviengineer",
                        Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
                ClassLoader cl = engCtx.getClassLoader();

                Class<?> carClass = Class.forName("android.car.Car", false, cl);
                Class<?> mgrClass = Class.forName("android.car.hardware.setting.GwmCarSettingManager", false, cl);

                android.content.ServiceConnection safeConn = new android.content.ServiceConnection() {
                    @Override
                    public void onServiceConnected(android.content.ComponentName name, android.os.IBinder service) {
                        appendOemLoc("CONNECTED=" + String.valueOf(name));
                    }

                    @Override
                    public void onServiceDisconnected(android.content.ComponentName name) {
                        appendOemLoc("DISCONNECTED=" + String.valueOf(name));
                    }
                };

                Object car = null;
                for (java.lang.reflect.Method m : carClass.getMethods()) {
                    if (!"createCar".equals(m.getName()) || !java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
                    Class<?>[] ps = m.getParameterTypes();

                    boolean hasConn = false;
                    for (Class<?> p : ps) if (android.content.ServiceConnection.class.isAssignableFrom(p)) hasConn = true;
                    if (!hasConn) continue;

                    try {
                        Object[] args = new Object[ps.length];
                        for (int i=0;i<ps.length;i++) {
                            Class<?> p = ps[i];
                            if (Context.class.isAssignableFrom(p)) args[i] = this;
                            else if (android.content.ServiceConnection.class.isAssignableFrom(p)) args[i] = safeConn;
                            else if (android.os.Handler.class.isAssignableFrom(p)) args[i] = new android.os.Handler(android.os.Looper.getMainLooper());
                            else if (p == long.class || p == Long.class) args[i] = 0L;
                            else if (p == int.class || p == Integer.class) args[i] = 0;
                            else if (p == boolean.class || p == Boolean.class) args[i] = false;
                            else args[i] = null;
                        }
                        m.setAccessible(true);
                        Object v = m.invoke(null, args);
                        if (v != null) { car = v; break; }
                    } catch (Throwable ignored) { }
                }

                if (car == null) {
                    appendOemLoc("RESULT: Car=NULL");
                    return;
                }

                try {
                    java.lang.reflect.Method connect = carClass.getMethod("connect");
                    connect.invoke(car);
                } catch (Throwable e) {
                    appendOemLoc("connect ERROR=" + rootCauseName(e));
                }

                long deadline = System.currentTimeMillis() + 2500L;
                boolean connected = false;
                try {
                    java.lang.reflect.Method isConnected = carClass.getMethod("isConnected");
                    while (System.currentTimeMillis() < deadline) {
                        try {
                            Object v = isConnected.invoke(car);
                            connected = Boolean.TRUE.equals(v);
                            if (connected) break;
                        } catch (Throwable ignored) { }
                        try { Thread.sleep(100); } catch (InterruptedException ignored) { }
                    }
                } catch (Throwable ignored) { }

                appendOemLoc("isConnected=" + connected);

                java.lang.reflect.Method getMgr = carClass.getMethod("getCarManager", String.class);
                Object manager = null;
                try {
                    manager = getMgr.invoke(car, "gwm_car_setting");
                    appendOemLoc("getCarManager(gwm_car_setting)=" + (manager == null ? "NULL" : manager.getClass().getName()));
                } catch (Throwable e) {
                    appendOemLoc("getCarManager ERROR=" + rootCauseName(e));
                }

                if (manager == null) {
                    appendOemLoc("RESULT: manager=NULL");
                    return;
                }

                appendOemLoc("isGwmManager=" + mgrClass.isInstance(manager));

                int shown = 0;
                int useful = 0;
                for (java.lang.reflect.Method m : manager.getClass().getMethods()) {
                    String n = m.getName().toLowerCase(Locale.US);
                    if (!(n.contains("speed") || n.contains("gear") || n.contains("yaw") ||
                            n.contains("heading") || n.contains("azimuth") || n.contains("bearing") ||
                            n.contains("angle"))) continue;

                    Class<?>[] ps = m.getParameterTypes();
                    StringBuilder sig = new StringBuilder();
                    sig.append(m.getName()).append('(');
                    for (int i=0;i<ps.length;i++) {
                        if (i>0) sig.append(',');
                        sig.append(ps[i].getSimpleName());
                    }
                    sig.append(")->").append(m.getReturnType().getSimpleName());
                    appendOemLoc("M " + sig.toString());
                    shown++;

                    if (ps.length == 0) {
                        try {
                            m.setAccessible(true);
                            Object v = m.invoke(manager);
                            appendOemLoc("V " + m.getName() + "=" + String.valueOf(v));
                            if (v != null) useful++;
                        } catch (Throwable e) {
                            appendOemLoc("V " + m.getName() + " ERROR=" + rootCauseName(e));
                        }
                    }
                }

                appendOemLoc("RESULT methods=" + shown + " useful=" + useful);
            } catch (Throwable e) {
                appendOemLoc("3.8 ERROR=" + e.getClass().getSimpleName() + ":" + shortMessage(e));
            }
        }, "H9-GwmCarSetting38").start();
    }

    private void probeServiceManagerInitAndLocation() {
        synchronized (oemLocLog) {
            oemLocLog.setLength(0);
            oemLocLog.append("ServiceManagerFactory INIT/DEPENDENCY PROBE\n");
        }
        new Thread(() -> {
            try {
                Context navCtx = createPackageContext("com.gwm.navi.app",
                        Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
                ClassLoader cl = navCtx.getClassLoader();
                Class<?> factory = Class.forName("com.gwm.navi.service.manager.ServiceManagerFactory", false, cl);
                Class<?> contract = Class.forName("com.gwm.navi.service.location.ILocationService", false, cl);
                appendOemLoc("factory=" + factory.getName());
                appendOemLoc("contract=" + contract.getName());

                // Show factory state and any init/register/set entry points.
                for (java.lang.reflect.Method m : factory.getDeclaredMethods()) {
                    String n = m.getName().toLowerCase(Locale.US);
                    if (n.contains("init") || n.contains("depend") || n.contains("register") ||
                            n.startsWith("set") || n.contains("location")) {
                        appendOemLoc("factoryMethod=" + java.lang.reflect.Modifier.toString(m.getModifiers()) + " " +
                                m.getName() + "(" + m.getParameterTypes().length + ")->" + m.getReturnType().getName());
                    }
                }
                for (java.lang.reflect.Field f : factory.getDeclaredFields()) {
                    if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    try {
                        f.setAccessible(true);
                        Object v = f.get(null);
                        appendOemLoc("factoryField=" + f.getName() + ":" + f.getType().getName() + "=" +
                                (v == null ? "null" : v.getClass().getName()));
                    } catch (Throwable e) {
                        appendOemLoc("factoryField " + f.getName() + " ERROR=" + rootCauseName(e));
                    }
                }

                Object dependency = null;
                try {
                    java.lang.reflect.Method m = factory.getDeclaredMethod("getDependencyService");
                    m.setAccessible(true);
                    dependency = m.invoke(null);
                    appendOemLoc("getDependencyService=" + (dependency == null ? "NULL" : dependency.getClass().getName()));
                } catch (Throwable e) {
                    appendOemLoc("getDependencyService ERROR=" + rootCauseName(e));
                }

                if (dependency != null) {
                    Class<?> dc = dependency.getClass();
                    int shown = 0;
                    for (java.lang.reflect.Method m : dc.getMethods()) {
                        String n = m.getName().toLowerCase(Locale.US);
                        String rt = m.getReturnType().getName().toLowerCase(Locale.US);
                        if (n.contains("init") || n.contains("register") || n.contains("service") ||
                                n.contains("location") || n.startsWith("get") || rt.contains("locationservice")) {
                            appendOemLoc("depMethod=" + m.getName() + "(" + m.getParameterTypes().length + ")->" +
                                    m.getReturnType().getName());
                            if (++shown >= 18) break;
                        }
                    }

                    // Read cached service fields from the dependency object.
                    for (java.lang.reflect.Field f : dc.getDeclaredFields()) {
                        String ft = f.getType().getName().toLowerCase(Locale.US);
                        String fn = f.getName().toLowerCase(Locale.US);
                        if (!(ft.contains("location") || ft.contains("service") || fn.contains("location") || fn.contains("service"))) continue;
                        try {
                            f.setAccessible(true);
                            Object v = f.get(dependency);
                            appendOemLoc("depField=" + f.getName() + ":" + f.getType().getName() + "=" +
                                    (v == null ? "null" : v.getClass().getName()));
                            if (v != null && invokeDrInfo(v, "depField/" + f.getName())) return;
                        } catch (Throwable e) {
                            appendOemLoc("depField " + f.getName() + " ERROR=" + rootCauseName(e));
                        }
                    }

                    // Only invoke no-arg accessors that look like getters. Avoid mutating init/register calls.
                    for (java.lang.reflect.Method m : dc.getMethods()) {
                        if (m.getParameterTypes().length != 0) continue;
                        String n = m.getName().toLowerCase(Locale.US);
                        String rt = m.getReturnType().getName().toLowerCase(Locale.US);
                        if (!(n.equals("getlocationservice") || n.equals("getlocservice") ||
                                rt.contains("ilocationservice") || rt.endsWith("locationservice"))) continue;
                        try {
                            m.setAccessible(true);
                            Object v = m.invoke(dependency);
                            appendOemLoc("depCall=" + m.getName() + " -> " +
                                    (v == null ? "NULL" : v.getClass().getName()));
                            if (v != null && invokeDrInfo(v, "dependency/" + m.getName())) return;
                        } catch (Throwable e) {
                            appendOemLoc("depCall " + m.getName() + " ERROR=" + rootCauseName(e));
                        }
                    }
                }

                // Retry the exact factory accessor after dependency inspection.
                try {
                    java.lang.reflect.Method getLocationService = factory.getDeclaredMethod("getLocationService");
                    getLocationService.setAccessible(true);
                    Object service = getLocationService.invoke(null);
                    appendOemLoc("RETRY getLocationService=" + (service == null ? "NULL" : service.getClass().getName()));
                    if (service != null) {
                        if (invokeDrInfo(service, "factoryRetry")) return;
                        appendInterestingMethods(service.getClass(), "factoryRetry");
                    }
                } catch (Throwable e) {
                    appendOemLoc("RETRY getLocationService ERROR=" + rootCauseName(e));
                }

                appendOemLoc("RESULT: factory/dependency not initialized in our process");
            } catch (Throwable e) {
                appendOemLoc("DIRECT 3.3 ERROR=" + e.getClass().getSimpleName() + ":" + shortMessage(e));
            }
        }, "H9-ServiceFactory-Init").start();
    }

    private void probeDirectGetLocationService() {
        synchronized (oemLocLog) {
            oemLocLog.setLength(0);
            oemLocLog.append("DIRECT getLocationService -> getDrLocInfo PROBE\n");
        }
        new Thread(() -> {
            try {
                Context navCtx = createPackageContext("com.gwm.navi.app",
                        Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
                ClassLoader cl = navCtx.getClassLoader();
                Class<?> factory = Class.forName("com.gwm.navi.service.manager.ServiceManagerFactory", false, cl);
                Class<?> contract = Class.forName("com.gwm.navi.service.location.ILocationService", false, cl);
                appendOemLoc("factory=" + factory.getName());
                appendOemLoc("contract=" + contract.getName());

                java.lang.reflect.Method getLocationService = factory.getDeclaredMethod("getLocationService");
                getLocationService.setAccessible(true);
                appendOemLoc("method=getLocationService()->" + getLocationService.getReturnType().getName());

                Object service = getLocationService.invoke(null);
                appendOemLoc("getLocationService=" + (service == null ? "NULL" : service.getClass().getName()));
                if (service == null) {
                    appendOemLoc("RESULT: NULL service instance");
                    return;
                }

                appendInterestingMethods(service.getClass(), "service");

                try {
                    java.lang.reflect.Method drMethod = service.getClass().getMethod("getDrLocInfo");
                    drMethod.setAccessible(true);
                    Object dr = drMethod.invoke(service);
                    appendOemLoc("getDrLocInfo=" + String.valueOf(dr));
                    if (dr != null) {
                        appendOemLoc("DrLocInfo class=" + dr.getClass().getName());
                        Double heading = extractHeading(dr);
                        if (heading != null && !heading.isNaN()) {
                            final float h = normalize(heading.floatValue());
                            appendOemLoc("SUCCESS DR_HEADING=" + String.format(Locale.US, "%.2f°", h));
                            runOnUiThread(() -> qiblaView.setHeading(h, "اتجاه DR من نظام هافال"));
                        } else {
                            appendOemLoc("DR object found but heading field/getter not resolved");
                            for (java.lang.reflect.Method m : dr.getClass().getMethods()) {
                                String n = m.getName().toLowerCase(Locale.US);
                                if (n.contains("heading") || n.contains("bearing") || n.contains("direction") || n.contains("azimuth")) {
                                    appendOemLoc("drMethod=" + m.getName() + "()->" + m.getReturnType().getName());
                                }
                            }
                            for (java.lang.reflect.Field f : dr.getClass().getDeclaredFields()) {
                                String n = f.getName().toLowerCase(Locale.US);
                                if (n.contains("heading") || n.contains("bearing") || n.contains("direction") || n.contains("azimuth")) {
                                    try {
                                        f.setAccessible(true);
                                        appendOemLoc("drField=" + f.getName() + "=" + String.valueOf(f.get(dr)));
                                    } catch (Throwable ignored) { }
                                }
                            }
                        }
                    } else {
                        appendOemLoc("RESULT: getDrLocInfo returned NULL");
                    }
                } catch (Throwable e) {
                    appendOemLoc("getDrLocInfo ERROR=" + rootCauseName(e));
                    invokeDrInfo(service, "directService");
                }
            } catch (Throwable e) {
                appendOemLoc("DIRECT 3.2 ERROR=" + e.getClass().getSimpleName() + ":" + shortMessage(e));
            }
        }, "H9-Direct-getLocationService").start();
    }

    private void probeServiceManagerFactoryDirect() {
        synchronized (oemLocLog) {
            oemLocLog.setLength(0);
            oemLocLog.append("DIRECT ServiceManagerFactory PROBE\n");
        }
        new Thread(() -> {
            try {
                Context navCtx = createPackageContext("com.gwm.navi.app",
                        Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
                ClassLoader cl = navCtx.getClassLoader();
                Class<?> contract = Class.forName("com.gwm.navi.service.location.ILocationService", false, cl);
                String targetName = "com.gwm.navi.service.manager.ServiceManagerFactory";
                Class<?> target = Class.forName(targetName, false, cl);
                appendOemLoc("target=" + target.getName());
                appendOemLoc("contract=" + contract.getName());

                int shown = 0;
                for (java.lang.reflect.Method m : target.getDeclaredMethods()) {
                    StringBuilder sig = new StringBuilder();
                    sig.append("method=").append(java.lang.reflect.Modifier.toString(m.getModifiers())).append(' ')
                            .append(m.getName()).append('(');
                    Class<?>[] ps = m.getParameterTypes();
                    for (int i = 0; i < ps.length; i++) {
                        if (i > 0) sig.append(',');
                        sig.append(ps[i].getName());
                    }
                    sig.append(")->").append(m.getReturnType().getName());
                    appendOemLoc(sig.toString());
                    if (++shown >= 22) break;
                }

                // Static cached objects/singletons.
                for (java.lang.reflect.Field f : target.getDeclaredFields()) {
                    if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    try {
                        f.setAccessible(true);
                        Object v = f.get(null);
                        appendOemLoc("static=" + f.getName() + ":" + f.getType().getName() + " -> " +
                                (v == null ? "null" : v.getClass().getName()));
                        if (v != null && probeLocationCarrier(v, targetName + "." + f.getName())) return;
                    } catch (Throwable e) {
                        appendOemLoc("static " + f.getName() + " ERROR=" + rootCauseName(e));
                    }
                }

                // Invoke safe factory forms. Support no-arg, Class, and enum selectors.
                for (java.lang.reflect.Method m : target.getDeclaredMethods()) {
                    if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
                    Class<?>[] ps = m.getParameterTypes();
                    Object[] args = null;
                    String selector = "";
                    if (ps.length == 0) {
                        args = new Object[0];
                    } else if (ps.length == 1 && ps[0] == Class.class) {
                        args = new Object[]{contract};
                        selector = "[ILocationService.class]";
                    } else if (ps.length == 1 && ps[0].isEnum()) {
                        Object[] constants = ps[0].getEnumConstants();
                        if (constants != null) {
                            for (Object c : constants) {
                                String n = String.valueOf(c).toLowerCase(Locale.US);
                                if (n.contains("loc") || n.contains("position") || n.contains("navi")) {
                                    try {
                                        m.setAccessible(true);
                                        Object v = m.invoke(null, c);
                                        appendOemLoc("call=" + m.getName() + "[" + c + "] -> " +
                                                (v == null ? "null" : v.getClass().getName()));
                                        if (v != null && probeLocationCarrier(v, targetName + "." + m.getName() + "[" + c + "]")) return;
                                    } catch (Throwable e) {
                                        appendOemLoc("call " + m.getName() + "[" + c + "] ERROR=" + rootCauseName(e));
                                    }
                                }
                            }
                        }
                        continue;
                    } else {
                        continue;
                    }
                    try {
                        m.setAccessible(true);
                        Object v = m.invoke(null, args);
                        appendOemLoc("call=" + m.getName() + selector + " -> " +
                                (v == null ? "null" : v.getClass().getName()));
                        if (v != null && probeLocationCarrier(v, targetName + "." + m.getName() + selector)) return;
                    } catch (Throwable e) {
                        appendOemLoc("call " + m.getName() + selector + " ERROR=" + rootCauseName(e));
                    }
                }

                // Constructors are diagnostic only; instantiate a no-arg one if present.
                for (java.lang.reflect.Constructor<?> ctor : target.getDeclaredConstructors()) {
                    appendOemLoc("ctor params=" + ctor.getParameterTypes().length + " mods=" +
                            java.lang.reflect.Modifier.toString(ctor.getModifiers()));
                    if (ctor.getParameterTypes().length == 0) {
                        try {
                            ctor.setAccessible(true);
                            Object v = ctor.newInstance();
                            appendOemLoc("ctorInstance=" + (v == null ? "null" : v.getClass().getName()));
                            if (v != null && probeLocationCarrier(v, targetName + ".<init>")) return;
                        } catch (Throwable e) {
                            appendOemLoc("ctor ERROR=" + rootCauseName(e));
                        }
                    }
                }

                appendOemLoc("NO ILocationService • ServiceManagerFactory probe complete");
            } catch (Throwable e) {
                appendOemLoc("FACTORY PROBE ERROR=" + e.getClass().getSimpleName() + ":" + shortMessage(e));
            }
        }, "H9-ServiceManagerFactory").start();
    }

    private void probeLocationServiceImplDirect() {
        synchronized (oemLocLog) {
            oemLocLog.setLength(0);
            oemLocLog.append("DIRECT LocationServiceImpl PROBE\n");
        }
        new Thread(() -> {
            try {
                Context navCtx = createPackageContext("com.gwm.navi.app",
                        Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
                ClassLoader cl = navCtx.getClassLoader();
                String targetName = "com.gwm.navi.wrapper.neusoft.location.LocationServiceImpl";
                Class<?> target = Class.forName(targetName, false, cl);
                appendOemLoc("target=" + target.getName());

                try {
                    Class<?> contract = Class.forName("com.gwm.navi.service.location.ILocationService", false, cl);
                    appendOemLoc("contract=" + contract.getName());
                } catch (Throwable e) {
                    appendOemLoc("contract ERROR=" + rootCauseName(e));
                }

                int shown = 0;
                for (java.lang.reflect.Method m : target.getDeclaredMethods()) {
                    String n = m.getName().toLowerCase(Locale.US);
                    String rt = m.getReturnType().getName().toLowerCase(Locale.US);
                    if (n.contains("instance") || n.contains("location") || n.contains("service") || n.contains("dr") ||
                            rt.contains("location") || rt.contains("service")) {
                        appendOemLoc("method=" + java.lang.reflect.Modifier.toString(m.getModifiers()) + " " +
                                m.getName() + "(" + m.getParameterTypes().length + ")->" + m.getReturnType().getName());
                        if (++shown >= 14) break;
                    }
                }

                for (java.lang.reflect.Field f : target.getDeclaredFields()) {
                    if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    try {
                        f.setAccessible(true);
                        Object v = f.get(null);
                        appendOemLoc("static=" + f.getName() + ":" + f.getType().getName() + " -> " +
                                (v == null ? "null" : v.getClass().getName()));
                        if (v != null && probeLocationCarrier(v, targetName + "." + f.getName())) return;
                    } catch (Throwable e) {
                        appendOemLoc("static " + f.getName() + " ERROR=" + rootCauseName(e));
                    }
                }

                for (java.lang.reflect.Method m : target.getDeclaredMethods()) {
                    if (!java.lang.reflect.Modifier.isStatic(m.getModifiers()) || m.getParameterTypes().length != 0) continue;
                    String n = m.getName().toLowerCase(Locale.US);
                    String rt = m.getReturnType().getName().toLowerCase(Locale.US);
                    if (!(n.contains("instance") || n.contains("service") || n.contains("location") || n.startsWith("get") ||
                            rt.contains("location") || rt.contains("service") || rt.contains("wrapper"))) continue;
                    try {
                        m.setAccessible(true);
                        Object v = m.invoke(null);
                        appendOemLoc("staticCall=" + m.getName() + " -> " + (v == null ? "null" : v.getClass().getName()));
                        if (v != null && probeLocationCarrier(v, targetName + "." + m.getName() + "()")) return;
                    } catch (Throwable e) {
                        appendOemLoc("staticCall " + m.getName() + " ERROR=" + rootCauseName(e));
                    }
                }

                boolean ctorTried = false;
                for (java.lang.reflect.Constructor<?> ctor : target.getDeclaredConstructors()) {
                    appendOemLoc("ctor params=" + ctor.getParameterTypes().length + " mods=" +
                            java.lang.reflect.Modifier.toString(ctor.getModifiers()));
                    if (ctor.getParameterTypes().length != 0 || ctorTried) continue;
                    ctorTried = true;
                    try {
                        ctor.setAccessible(true);
                        Object obj = ctor.newInstance();
                        appendOemLoc("ctorInstance=" + (obj == null ? "null" : obj.getClass().getName()));
                        if (obj != null && probeLocationCarrier(obj, targetName + ".<init>")) return;
                    } catch (Throwable e) {
                        appendOemLoc("ctor ERROR=" + rootCauseName(e));
                    }
                }

                String[] extra = new String[]{
                        "com.gwm.navi.service.manager.ServiceManagerFactory",
                        "com.gwm.navi.wrapper.neusoft.eVServiceImpl",
                        "com.gwm.navi.wrapper.neusoft.guide.GuideServiceImpl"
                };
                for (String cn : extra) {
                    try {
                        Class<?> c = Class.forName(cn, false, cl);
                        appendOemLoc("secondary=" + cn);
                        for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                            if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                            try {
                                f.setAccessible(true);
                                Object v = f.get(null);
                                if (v != null && probeLocationCarrier(v, cn + "." + f.getName())) return;
                            } catch (Throwable ignored) { }
                        }
                        for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                            if (!java.lang.reflect.Modifier.isStatic(m.getModifiers()) || m.getParameterTypes().length != 0) continue;
                            String n = m.getName().toLowerCase(Locale.US);
                            if (!(n.contains("instance") || n.contains("location") || n.contains("service") || n.startsWith("get"))) continue;
                            try {
                                m.setAccessible(true);
                                Object v = m.invoke(null);
                                if (v != null && probeLocationCarrier(v, cn + "." + m.getName() + "()")) return;
                            } catch (Throwable ignored) { }
                        }
                    } catch (Throwable e) {
                        appendOemLoc("secondary " + cn + " ERROR=" + rootCauseName(e));
                    }
                }

                appendOemLoc("NO DR INSTANCE • focused probe complete");
            } catch (Throwable e) {
                appendOemLoc("DIRECT PROBE ERROR=" + e.getClass().getSimpleName() + ":" + shortMessage(e));
            }
        }, "H9-LocationServiceImpl-Direct").start();
    }

    private void probeInternalGwmLocationService() {
        synchronized (oemLocLog) {
            oemLocLog.setLength(0);
            oemLocLog.append("INTERNAL ILocationService PROVIDER PROBE\n");
        }
        new Thread(() -> {
            dalvik.system.DexFile dex = null;
            try {
                Context navCtx = createPackageContext("com.gwm.navi.app",
                        Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
                ClassLoader cl = navCtx.getClassLoader();
                android.content.pm.ApplicationInfo ai = getPackageManager().getApplicationInfo("com.gwm.navi.app", 0);
                appendOemLoc("apk=" + ai.sourceDir);

                Class<?> contract = null;
                try {
                    contract = Class.forName("com.gwm.navi.service.location.ILocationService", false, cl);
                    appendOemLoc("contract=" + contract.getName() + " • getDrLocInfo confirmed");
                } catch (Throwable e) {
                    appendOemLoc("contract load ERROR=" + e.getClass().getSimpleName());
                }

                dex = new dalvik.system.DexFile(ai.sourceDir);
                java.util.Enumeration<String> entries = dex.entries();
                int scanned = 0;
                int candidateClasses = 0;
                while (entries.hasMoreElements()) {
                    String cn = entries.nextElement();
                    if (!cn.startsWith("com.gwm.navi.")) continue;
                    String low = cn.toLowerCase(Locale.US);
                    if (!(low.contains("location") || low.contains("service") || low.contains("manager") ||
                            low.contains("wrapper") || low.contains("factory") || low.contains("sdk"))) continue;
                    if (++scanned > 4200) break;
                    try {
                        Class<?> c = Class.forName(cn, false, cl);
                        boolean interesting = false;
                        for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                            String n = m.getName().toLowerCase(Locale.US);
                            String rt = m.getReturnType().getName().toLowerCase(Locale.US);
                            if (n.contains("locationservice") || n.equals("getlocationservice") ||
                                    rt.contains("ilocationservice") || rt.endsWith("locationservice")) {
                                interesting = true;
                                break;
                            }
                        }
                        if (!interesting) {
                            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                                String ft = f.getType().getName().toLowerCase(Locale.US);
                                if (ft.contains("ilocationservice") || ft.endsWith("locationservice")) {
                                    interesting = true;
                                    break;
                                }
                            }
                        }
                        if (!interesting) continue;
                        candidateClasses++;
                        if (candidateClasses <= 18) appendOemLoc("candidate=" + cn);

                        // 1) Static fields that already hold ILocationService or a provider object.
                        for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                            if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                            String ft = f.getType().getName().toLowerCase(Locale.US);
                            if (!(ft.contains("locationservice") || ft.contains("locationmanager") ||
                                    ft.contains("locationprovider"))) continue;
                            try {
                                f.setAccessible(true);
                                Object v = f.get(null);
                                if (v != null) {
                                    appendOemLoc("staticField=" + cn + "." + f.getName() + " -> " + v.getClass().getName());
                                    if (probeLocationCarrier(v, cn + "." + f.getName())) return;
                                }
                            } catch (Throwable e) {
                                if (candidateClasses <= 8) appendOemLoc("field " + f.getName() + " ERROR=" + e.getClass().getSimpleName());
                            }
                        }

                        // 2) Static no-arg factories/singletons.
                        for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                            if (!java.lang.reflect.Modifier.isStatic(m.getModifiers()) || m.getParameterTypes().length != 0) continue;
                            String n = m.getName().toLowerCase(Locale.US);
                            String rt = m.getReturnType().getName().toLowerCase(Locale.US);
                            if (!(n.equals("getlocationservice") || n.equals("getinstance") || n.equals("getlocservice") ||
                                    rt.contains("ilocationservice") || rt.endsWith("locationservice"))) continue;
                            try {
                                m.setAccessible(true);
                                Object v = m.invoke(null);
                                if (v != null) {
                                    appendOemLoc("factory=" + cn + "." + m.getName() + " -> " + v.getClass().getName());
                                    if (probeLocationCarrier(v, cn + "." + m.getName() + "()")) return;
                                }
                            } catch (Throwable e) {
                                if (candidateClasses <= 8) appendOemLoc("factory " + m.getName() + " ERROR=" + rootCauseName(e));
                            }
                        }
                    } catch (Throwable ignored) { }
                }
                appendOemLoc("NO LIVE ILocationService • scanned=" + scanned + " candidates=" + candidateClasses);
            } catch (Throwable e) {
                appendOemLoc("SCAN ERROR=" + e.getClass().getSimpleName() + ":" + shortMessage(e));
            } finally {
                if (dex != null) try { dex.close(); } catch (Throwable ignored) { }
            }
        }, "H9-Internal-ILocationService").start();
    }

    private boolean probeLocationCarrier(Object carrier, String source) {
        if (carrier == null) return false;
        if (invokeDrInfo(carrier, source)) return true;

        // Look for an instance no-arg accessor returning the actual ILocationService.
        Class<?> c = carrier.getClass();
        for (java.lang.reflect.Method m : c.getMethods()) {
            if (m.getParameterTypes().length != 0 || java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
            String n = m.getName().toLowerCase(Locale.US);
            String rt = m.getReturnType().getName().toLowerCase(Locale.US);
            if (!(n.equals("getlocationservice") || n.equals("getlocservice") ||
                    rt.contains("ilocationservice") || rt.endsWith("locationservice"))) continue;
            try {
                m.setAccessible(true);
                Object v = m.invoke(carrier);
                if (v != null) {
                    appendOemLoc("accessor=" + source + "." + m.getName() + " -> " + v.getClass().getName());
                    if (invokeDrInfo(v, source + "." + m.getName())) return true;
                }
            } catch (Throwable e) {
                appendOemLoc("accessor " + m.getName() + " ERROR=" + rootCauseName(e));
            }
        }

        // Inspect one level of instance fields for a cached location-service proxy.
        for (java.lang.reflect.Field f : c.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
            String ft = f.getType().getName().toLowerCase(Locale.US);
            if (!(ft.contains("ilocationservice") || ft.endsWith("locationservice") || ft.contains("locationprovider"))) continue;
            try {
                f.setAccessible(true);
                Object v = f.get(carrier);
                if (v != null) {
                    appendOemLoc("instanceField=" + source + "." + f.getName() + " -> " + v.getClass().getName());
                    if (invokeDrInfo(v, source + "." + f.getName())) return true;
                }
            } catch (Throwable e) {
                appendOemLoc("instanceField " + f.getName() + " ERROR=" + rootCauseName(e));
            }
        }
        return false;
    }

    private String rootCauseName(Throwable e) {
        Throwable r = e;
        while (r.getCause() != null && r.getCause() != r) r = r.getCause();
        String msg = r.getMessage();
        if (msg == null) msg = "";
        if (msg.length() > 80) msg = msg.substring(0, 80) + "…";
        return r.getClass().getSimpleName() + (msg.isEmpty() ? "" : ":" + msg);
    }

    private final java.util.concurrent.atomic.AtomicInteger oemLocReplies = new java.util.concurrent.atomic.AtomicInteger(0);
    private final StringBuilder oemLocLog = new StringBuilder();

    private void probeOemLocationServices() {
        synchronized (oemLocLog) {
            oemLocLog.setLength(0);
            oemLocLog.append("OEM LOCATION SERVICE BIND PROBE\n");
        }
        bindOneOemLocationService("SensService",
                "com.neusoft.nmasdk.location.service.SensService");
        bindOneOemLocationService("SystemService",
                "com.neusoft.nmasdk.system.service.SystemService");
    }

    private void bindOneOemLocationService(final String label, final String className) {
        try {
            Intent i = new Intent();
            i.setComponent(new ComponentName("com.gwm.navi.app", className));
            ServiceConnection c = new ServiceConnection() {
                @Override public void onServiceConnected(ComponentName name, IBinder service) {
                    inspectOemLocationBinder(label, name, service);
                }
                @Override public void onServiceDisconnected(ComponentName name) {
                    appendOemLoc(label + " disconnected");
                }
                @Override public void onBindingDied(ComponentName name) {
                    appendOemLoc(label + " bindingDied");
                }
                @Override public void onNullBinding(ComponentName name) {
                    appendOemLoc(label + " nullBinding");
                }
            };
            boolean ok = bindService(i, c, Context.BIND_AUTO_CREATE);
            appendOemLoc(label + " bindService=" + ok + " • " + className);
        } catch (Throwable e) {
            appendOemLoc(label + " bind ERROR=" + e.getClass().getSimpleName() + ":" + shortMessage(e));
        }
    }

    private void inspectOemLocationBinder(final String label, final ComponentName name, final IBinder binder) {
        new Thread(() -> {
            String descriptor = "?";
            try { descriptor = binder.getInterfaceDescriptor(); } catch (Throwable ignored) { }
            appendOemLoc(label + " CONNECTED • desc=" + descriptor);
            appendOemLoc(label + " component=" + name.flattenToShortString());
            try {
                Context navCtx = createPackageContext("com.gwm.navi.app",
                        Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
                ClassLoader cl = navCtx.getClassLoader();

                String[] ifaceCandidates = new String[]{
                        "com.gwm.navi.service.location.ILocationService",
                        "com.neusoft.nmasdk.location.service.ILocationService"
                };
                boolean contractFound = false;
                for (String ifaceName : ifaceCandidates) {
                    try {
                        Class<?> iface = Class.forName(ifaceName, false, cl);
                        contractFound = true;
                        appendOemLoc("contract=" + ifaceName);
                        appendInterestingMethods(iface, "iface");

                        // If this is AIDL, use its real Stub.asInterface(IBinder).
                        try {
                            Class<?> stub = Class.forName(ifaceName + "$Stub", false, cl);
                            java.lang.reflect.Method asInterface = stub.getDeclaredMethod("asInterface", IBinder.class);
                            asInterface.setAccessible(true);
                            Object proxy = asInterface.invoke(null, binder);
                            appendOemLoc("asInterface=" + (proxy == null ? "null" : proxy.getClass().getName()));
                            if (proxy != null && invokeDrInfo(proxy, label + "/AIDL")) return;
                        } catch (ClassNotFoundException e) {
                            appendOemLoc("Stub absent for " + ifaceName);
                        } catch (Throwable e) {
                            appendOemLoc("asInterface ERROR=" + e.getClass().getSimpleName() + ":" + shortMessage(e));
                        }
                    } catch (ClassNotFoundException ignored) { }
                }
                if (!contractFound) appendOemLoc("ILocationService class not found in candidate packages");

                // Inspect service binder itself for OEM methods exposed directly.
                appendInterestingMethods(binder.getClass(), label + "/binder");
                if (invokeDrInfo(binder, label + "/binder")) return;

                // Inspect local interface if available.
                if (descriptor != null && !"?".equals(descriptor)) {
                    try {
                        android.os.IInterface local = binder.queryLocalInterface(descriptor);
                        appendOemLoc("queryLocalInterface=" + (local == null ? "null" : local.getClass().getName()));
                        if (local != null) {
                            appendInterestingMethods(local.getClass(), label + "/local");
                            if (invokeDrInfo(local, label + "/local")) return;
                        }
                    } catch (Throwable e) {
                        appendOemLoc("localInterface ERROR=" + e.getClass().getSimpleName());
                    }
                }
            } catch (Throwable e) {
                appendOemLoc(label + " inspect ERROR=" + e.getClass().getName() + ":" + shortMessage(e));
            }
            oemLocReplies.incrementAndGet();
        }, "H9-OEM-Loc-" + label).start();
    }

    private void appendInterestingMethods(Class<?> c, String prefix) {
        try {
            StringBuilder b = new StringBuilder(prefix).append(" methods:");
            int count = 0;
            for (java.lang.reflect.Method m : c.getMethods()) {
                String n = m.getName().toLowerCase(Locale.US);
                if (n.contains("dr") || n.contains("loc") || n.contains("heading") || n.contains("bearing") || n.contains("position")) {
                    b.append(' ').append(m.getName()).append('(').append(m.getParameterTypes().length).append(')');
                    if (++count >= 18) break;
                }
            }
            appendOemLoc(b.toString());
        } catch (Throwable ignored) { }
    }

    private boolean invokeDrInfo(Object target, String source) {
        if (target == null) return false;
        String[] names = new String[]{"getDrLocInfo", "getLocInfo", "getLastLocation"};
        for (String n : names) {
            try {
                java.lang.reflect.Method m;
                try { m = target.getClass().getMethod(n); }
                catch (NoSuchMethodException e) { m = target.getClass().getDeclaredMethod(n); }
                m.setAccessible(true);
                Object value = m.invoke(target);
                appendOemLoc(source + " " + n + "=" + String.valueOf(value));
                if ("getDrLocInfo".equals(n) && value != null) {
                    Double heading = extractHeading(value);
                    if (heading != null && !heading.isNaN()) {
                        final float h = normalize(heading.floatValue());
                        appendOemLoc("SUCCESS DR_HEADING=" + String.format(Locale.US, "%.2f°", h));
                        runOnUiThread(() -> qiblaView.setHeading(h, "اتجاه DR من نظام هافال"));
                        return true;
                    }
                }
            } catch (NoSuchMethodException ignored) {
            } catch (Throwable e) {
                appendOemLoc(source + " " + n + " ERROR=" + e.getClass().getSimpleName() + ":" + shortMessage(e));
            }
        }
        return false;
    }

    private Double extractHeading(Object dr) {
        if (dr == null) return null;
        String[] getterNames = new String[]{"getHeading", "heading", "getDirection", "getBearing", "getAzimuth"};
        for (String name : getterNames) {
            try {
                java.lang.reflect.Method m;
                try { m = dr.getClass().getMethod(name); }
                catch (NoSuchMethodException e) { m = dr.getClass().getDeclaredMethod(name); }
                m.setAccessible(true);
                Object v = m.invoke(dr);
                if (v instanceof Number) return ((Number) v).doubleValue();
            } catch (Throwable ignored) { }
        }
        for (java.lang.reflect.Field f : dr.getClass().getDeclaredFields()) {
            String n = f.getName().toLowerCase(Locale.US);
            if (!(n.contains("heading") || n.contains("bearing") || n.contains("azimuth") || n.equals("direction"))) continue;
            try {
                f.setAccessible(true);
                Object v = f.get(dr);
                if (v instanceof Number) return ((Number) v).doubleValue();
            } catch (Throwable ignored) { }
        }
        return null;
    }

    private void appendOemLoc(String line) {
        final String out;
        synchronized (oemLocLog) {
            if (oemLocLog.length() > 0) oemLocLog.append('\n');
            oemLocLog.append(line);
            out = trimDiag(oemLocLog.toString());
        }
        runOnUiThread(() -> updateBinderDiagnosticText(out));
    }

    private String trimDiag(String value) {
        if (value == null) return "null";
        return value.length() > 1900 ? value.substring(0, 1900) + "…" : value;
    }

    private String shortMessage(Throwable t) {
        String s = t.getMessage();
        if (s == null) return "no message";
        return s.length() > 120 ? s.substring(0, 120) + "…" : s;
    }

    private void updateBinderDiagnosticText(String text) {
        if (binderDiagnosticText != null) binderDiagnosticText.setText("3.8 TEST • BINDER: " + text);
    }

    private void updateMiddlewareDiagnosticText(String text) {
        if (middlewareDiagnosticText != null) middlewareDiagnosticText.setText("3.8 TEST • GWM: " + text);
    }

    private void updateLocationDiagnosticText(String text) {
        if (locationDiagnosticText != null) locationDiagnosticText.setText("3.8 TEST • LOC: " + text);
    }

    private void updateLiveCourse(Location location) {
        if (location.hasSpeed()) lastSpeedMps = location.getSpeed();
        if (location.hasSpeed() && location.getSpeed() < GPS_HEADING_MIN_SPEED_MPS) {
            courseAnchor = null;
            return;
        }

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
            lastGpsHeadingAt = lastHeadingAt;
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

    private void registerHeadingSensors() {
        if (sensorManager == null) return;
        sensorManager.unregisterListener(this);
        if (rotationVectorSensor != null) {
            sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI);
        } else {
            if (accelerometerSensor != null) sensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_UI);
            if (magneticSensor != null) sensorManager.registerListener(this, magneticSensor, SensorManager.SENSOR_DELAY_UI);
        }
    }

    private float azimuthForDisplay(float[] matrix) {
        float[] remapped = new float[9];
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int x = SensorManager.AXIS_X, y = SensorManager.AXIS_Y;
        if (rotation == Surface.ROTATION_90) { x = SensorManager.AXIS_Y; y = SensorManager.AXIS_MINUS_X; }
        else if (rotation == Surface.ROTATION_180) { x = SensorManager.AXIS_MINUS_X; y = SensorManager.AXIS_MINUS_Y; }
        else if (rotation == Surface.ROTATION_270) { x = SensorManager.AXIS_MINUS_Y; y = SensorManager.AXIS_X; }
        if (!SensorManager.remapCoordinateSystem(matrix, x, y, remapped)) return Float.NaN;
        float[] orientation = new float[3];
        SensorManager.getOrientation(remapped, orientation);
        return normalize((float) Math.toDegrees(orientation[0]));
    }

    private void applySensorHeading(float headingValue) {
        if (Float.isNaN(headingValue)) return;
        long now = System.currentTimeMillis();
        boolean gpsFreshAndMoving = lastSpeedMps >= GPS_HEADING_MIN_SPEED_MPS && now - lastGpsHeadingAt < 3000L;
        if (!gpsFreshAndMoving) {
            lastHeadingAt = now;
            qiblaView.setHeading(headingValue, "بوصلة الشاشة / الحساس");
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            float[] matrix = new float[9];
            SensorManager.getRotationMatrixFromVector(matrix, event.values);
            applySensorHeading(azimuthForDisplay(matrix));
            return;
        }
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, accelValues, 0, 3);
            haveAccel = true;
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, magneticValues, 0, 3);
            haveMagnetic = true;
        }
        if (haveAccel && haveMagnetic) {
            float[] matrix = new float[9];
            float[] inclination = new float[9];
            if (SensorManager.getRotationMatrix(matrix, inclination, accelValues, magneticValues)) {
                applySensorHeading(azimuthForDisplay(matrix));
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (gwmBinderBound) {
            try { unbindService(gwmMapConnection); } catch (Exception ignored) { }
            gwmBinderBound = false;
            gwmMapBinder = null;
        }
        super.onDestroy();
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }

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
