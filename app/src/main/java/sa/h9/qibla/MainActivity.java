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
import android.hardware.GeomagneticField;
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
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements LocationListener, SensorEventListener {
    private static final int REQ_LOCATION = 1001;
    private static final float GPS_HEADING_MIN_SPEED_MPS = 0.8f;
    private static final float COURSE_MIN_DISTANCE_M = 2.0f;
    private static final long HEADING_STALE_MS = 7000L;
    private static final long PRAYER_REFRESH_MS = 30L * 60L * 1000L;
    private static final String NOTICE_PREFS = "h9_qibla_notice";
    private static final String KEY_NOTICE_SHOWN = "developer_notice_shown_v3";
    private static final String HEADING_PREFS = "h9_qibla_heading";
    private static final String KEY_LAST_HEADING = "last_heading";
    private static final String KEY_LAST_HEADING_SOURCE = "last_heading_source";
    private static final String UI_PREFS = "h9_qibla_ui";
    private static final String KEY_THEME_MODE = "theme_mode";
    private static final String KEY_FONT_SCALE = "font_scale";
    private static final String KEY_ENGLISH_MODE = "english_mode";
    private static final String KEY_FONT_INDEX = "font_index";
    private static final String KEY_PANEL_SWAPPED = "panel_swapped";

    private Button mainSettingsButton;
    private Button mainSwapButton;
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
    private volatile long lastOemHeadingAt;
    private volatile boolean oemDrRunning;
    private volatile Object oemLocationService;
    private volatile boolean gwmNaviHeadingStarted;
    private volatile Object gwmNaviControl;
    private volatile Object gwmNaviDataListener;
    private volatile boolean microGLocationRunning;
    private volatile Object microGFusedClient;
    private QiblaView qiblaView;
    private QiblaSensorHelper qiblaSensorHelper;
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
            startOemDrIntegration();
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
    private long lastPlaceResolveAt;
    private Location lastPlaceResolveLocation;
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

    private boolean isEn() {
        return qiblaView != null && qiblaView.isEnglishMode();
    }

    private void applyLanguageMode(boolean english) {
        if (qiblaView != null) {
            qiblaView.setEnglishMode(english);
        }
        getSharedPreferences(UI_PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ENGLISH_MODE, english).apply();
        if (mainSettingsButton != null) {
            mainSettingsButton.setText(english ? "Settings" : "الإعدادات");
        }
        if (mainSwapButton != null) {
            mainSwapButton.setText(swapButtonLabel(qiblaView != null && qiblaView.isPanelSwapped(), english));
        }
        if (lastLocation != null) {
            lastPlaceResolveAt = 0L;
            maybeResolveLocationName(lastLocation);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        applyImmersiveMode();

        SharedPreferences uiPrefs = getSharedPreferences(UI_PREFS, MODE_PRIVATE);
        boolean defaultEnglish = Locale.getDefault().getLanguage().toLowerCase(Locale.US).startsWith("en");
        boolean englishMode = uiPrefs.getBoolean(KEY_ENGLISH_MODE, defaultEnglish);

        FrameLayout root = new FrameLayout(this);
        qiblaView = new QiblaView(this);
        qiblaView.setThemeMode(uiPrefs.getInt(KEY_THEME_MODE, QiblaView.THEME_AUTO));
        qiblaView.setFontScale(uiPrefs.getFloat(KEY_FONT_SCALE, 1.0f));
        qiblaView.setFontIndex(uiPrefs.getInt(KEY_FONT_INDEX, QiblaView.FONT_DEFAULT));
        qiblaView.setPanelSwapped(uiPrefs.getBoolean(KEY_PANEL_SWAPPED, false));
        qiblaView.setEnglishMode(englishMode);
        root.addView(qiblaView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        restoreLastHeading();

        middlewareDiagnosticText = null;
        binderDiagnosticText = null;
        locationDiagnosticText = null;

        mainSettingsButton = new Button(this);
        mainSettingsButton.setText(englishMode ? "Settings" : "الإعدادات");
        mainSettingsButton.setTextSize(14.5f);
        mainSettingsButton.setTextColor(0xFFF1F7FA);
        mainSettingsButton.setAllCaps(false);
        mainSettingsButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        GradientDrawable aboutBg = new GradientDrawable();
        aboutBg.setColor(0xCC0B1B24);
        aboutBg.setCornerRadius(dp(14));
        aboutBg.setStroke(dp(1), 0xFF2FE0C7);
        mainSettingsButton.setBackground(aboutBg);
        mainSettingsButton.setOnClickListener(v -> showSettingsDialog());
        FrameLayout.LayoutParams aboutLp = new FrameLayout.LayoutParams(dp(105), dp(38));
        aboutLp.gravity = Gravity.BOTTOM | Gravity.LEFT;
        aboutLp.leftMargin = dp(36);
        aboutLp.bottomMargin = dp(18);
        root.addView(mainSettingsButton, aboutLp);

        mainSwapButton = new Button(this);
        mainSwapButton.setText(swapButtonLabel(qiblaView.isPanelSwapped(), englishMode));
        mainSwapButton.setTextSize(13.5f);
        mainSwapButton.setTextColor(0xFFF1F7FA);
        mainSwapButton.setAllCaps(false);
        mainSwapButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        GradientDrawable swapBg = new GradientDrawable();
        swapBg.setColor(0xCC0B1B24);
        swapBg.setCornerRadius(dp(14));
        swapBg.setStroke(dp(1), 0xFFE5B64A);
        mainSwapButton.setBackground(swapBg);
        mainSwapButton.setOnClickListener(v -> {
            boolean next = !qiblaView.isPanelSwapped();
            qiblaView.setPanelSwapped(next);
            getSharedPreferences(UI_PREFS, MODE_PRIVATE).edit().putBoolean(KEY_PANEL_SWAPPED, next).apply();
            mainSwapButton.setText(swapButtonLabel(next, isEn()));
        });
        FrameLayout.LayoutParams swapLp = new FrameLayout.LayoutParams(dp(135), dp(38));
        swapLp.gravity = Gravity.BOTTOM | Gravity.LEFT;
        swapLp.leftMargin = dp(36) + dp(105) + dp(12);
        swapLp.bottomMargin = dp(18);
        root.addView(mainSwapButton, swapLp);

        setContentView(root);

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            magneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        }
        qiblaSensorHelper = new QiblaSensorHelper(this, (deviceAzimuth, qiblaBearing, relativeAngle, distanceMeters, accuracy) -> {
            applyTrueSensorHeading(deviceAzimuth);
        });
        updateNetworkStatus();
        showDeveloperNoticeOnce();
        ensureLocationPermission();
        testGwmMapBinder();
        startGwmNaviHeadingIntegration();
        startMicroGLocationSupport();

        handler.postDelayed(() -> {
            if (lastLocation == null) {
                Location fallback = new Location("default");
                fallback.setLatitude(24.7136);
                fallback.setLongitude(46.6753);
                fallback.setAccuracy(15f);
                fallback.setTime(System.currentTimeMillis());
                updateLocation(fallback);
            }
        }, 2000L);

        handler.postDelayed(() -> UpdateManager.checkForUpdates(this, false), 3500L);
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
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        View decor = getWindow().getDecorView();
        if (decor != null) {
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersiveMode();
    }

    private void showSettingsDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);

        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int targetWidth = Math.min(dp(650), (int) (screenWidth * 0.90f));
        int targetHeight = Math.min(dp(460), (int) (screenHeight * 0.86f));
        int scrollHeight = Math.max(dp(140), targetHeight - dp(165));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(28), dp(14), dp(28), dp(14));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(0xF2171D23);
        cardBg.setCornerRadius(dp(26));
        cardBg.setStroke(dp(2), 0xFFE5B64A);
        card.setBackground(cardBg);

        final TextView appTitle = noteText(isEn() ? ("Qibla • Version " + BuildConfig.VERSION_NAME) : ("قبلة • الإصدار " + BuildConfig.VERSION_NAME), 18f, 0xFFE5B64A, true);
        card.addView(appTitle, matchWrap(WindowManager.LayoutParams.WRAP_CONTENT));
        final TextView title = noteText(isEn() ? "Settings" : "الإعدادات", 24f, 0xFFFFC857, true);
        title.setPadding(0, dp(2), 0, dp(8));
        card.addView(title, matchWrap(WindowManager.LayoutParams.WRAP_CONTENT));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setVerticalScrollBarEnabled(true);
        scrollView.setScrollbarFadingEnabled(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);

        final Button update = new Button(this);
        update.setText(isEn() ? "Check for Updates" : "فحص التحديثات");
        update.setTextSize(15f);
        update.setTextColor(Color.WHITE);
        update.setAllCaps(false);
        GradientDrawable updateBg = new GradientDrawable();
        updateBg.setColor(0xFF165B63);
        updateBg.setCornerRadius(dp(16));
        updateBg.setStroke(dp(1), 0xFF2FE0C7);
        update.setBackground(updateBg);
        update.setOnClickListener(v -> UpdateManager.checkForUpdates(this, true));
        LinearLayout.LayoutParams updateLp = new LinearLayout.LayoutParams(dp(220), dp(40));
        updateLp.bottomMargin = dp(8);
        content.addView(update, updateLp);

        final TextView appearanceTitle = noteText(isEn() ? "Appearance & Language" : "المظهر واللغة", 16f, 0xFFFFC857, true);
        content.addView(appearanceTitle, matchWrap(dp(26)));

        final Button langBtn = new Button(this);
        langBtn.setText(isEn() ? "Language: EN  ⇄  عربي" : "اللغة: العربية  ⇄  EN");
        langBtn.setTextSize(15f);
        langBtn.setTextColor(0xFFFFC857);
        langBtn.setAllCaps(false);
        langBtn.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        GradientDrawable langBtnBg = new GradientDrawable();
        langBtnBg.setColor(0xFF22323A);
        langBtnBg.setCornerRadius(dp(16));
        langBtnBg.setStroke(dp(1), 0xFFE5B64A);
        langBtn.setBackground(langBtnBg);
        LinearLayout.LayoutParams langBtnLp = new LinearLayout.LayoutParams(dp(230), dp(40));
        langBtnLp.bottomMargin = dp(8);
        content.addView(langBtn, langBtnLp);

        final Button theme = new Button(this);
        theme.setText((isEn() ? "Theme: " : "المظهر: ") + themeModeLabel(qiblaView.getThemeMode()));
        theme.setTextSize(15f);
        theme.setTextColor(Color.WHITE);
        theme.setAllCaps(false);
        GradientDrawable themeBg = new GradientDrawable();
        themeBg.setColor(0xFF22323A);
        themeBg.setCornerRadius(dp(16));
        themeBg.setStroke(dp(1), 0xFFE5B64A);
        theme.setBackground(themeBg);
        theme.setOnClickListener(v -> {
            int next = (qiblaView.getThemeMode() + 1) % 5;
            qiblaView.setThemeMode(next);
            getSharedPreferences(UI_PREFS, MODE_PRIVATE).edit().putInt(KEY_THEME_MODE, next).apply();
            theme.setText((isEn() ? "Theme: " : "المظهر: ") + themeModeLabel(next));
        });
        LinearLayout.LayoutParams themeLp = new LinearLayout.LayoutParams(dp(230), dp(40));
        themeLp.bottomMargin = dp(8);
        content.addView(theme, themeLp);

        final Button fontSize = new Button(this);
        fontSize.setText((isEn() ? "Font Size: " : "حجم الخط: ") + fontScaleLabel(qiblaView.getFontScale()));
        fontSize.setTextSize(15f);
        fontSize.setTextColor(Color.WHITE);
        fontSize.setAllCaps(false);
        GradientDrawable fontBg = new GradientDrawable();
        fontBg.setColor(0xFF22323A);
        fontBg.setCornerRadius(dp(16));
        fontBg.setStroke(dp(1), 0xFFE5B64A);
        fontSize.setBackground(fontBg);
        fontSize.setOnClickListener(v -> {
            float current = qiblaView.getFontScale();
            float next;
            if (current < 0.95f) {
                next = 1.0f;
            } else if (current < 1.08f) {
                next = 1.16f;
            } else if (current < 1.25f) {
                next = 1.34f;
            } else {
                next = 0.88f;
            }
            qiblaView.setFontScale(next);
            getSharedPreferences(UI_PREFS, MODE_PRIVATE).edit().putFloat(KEY_FONT_SCALE, next).apply();
            fontSize.setText((isEn() ? "Font Size: " : "حجم الخط: ") + fontScaleLabel(next));
        });
        LinearLayout.LayoutParams fontLp = new LinearLayout.LayoutParams(dp(230), dp(40));
        fontLp.bottomMargin = dp(8);
        content.addView(fontSize, fontLp);

        final Button fontBtn = new Button(this);
        fontBtn.setText((isEn() ? "Font: " : "الخط: ") + QiblaView.getFontName(qiblaView.getFontIndex(), isEn()));
        fontBtn.setTextSize(15f);
        fontBtn.setTextColor(Color.WHITE);
        fontBtn.setAllCaps(false);
        GradientDrawable fontBtnBg = new GradientDrawable();
        fontBtnBg.setColor(0xFF22323A);
        fontBtnBg.setCornerRadius(dp(16));
        fontBtnBg.setStroke(dp(1), 0xFFE5B64A);
        fontBtn.setBackground(fontBtnBg);
        fontBtn.setOnClickListener(v -> {
            int next = (qiblaView.getFontIndex() + 1) % 6;
            qiblaView.setFontIndex(next);
            getSharedPreferences(UI_PREFS, MODE_PRIVATE).edit().putInt(KEY_FONT_INDEX, next).apply();
            fontBtn.setText((isEn() ? "Font: " : "الخط: ") + QiblaView.getFontName(next, isEn()));
        });
        LinearLayout.LayoutParams fontBtnLp = new LinearLayout.LayoutParams(dp(230), dp(40));
        fontBtnLp.bottomMargin = dp(8);
        content.addView(fontBtn, fontBtnLp);

        final Button swapBtn = new Button(this);
        swapBtn.setText((isEn() ? "Prayer Times: " : "موضع المواقيت: ") + (qiblaView.isPanelSwapped() ? (isEn() ? "Left ⇄" : "يسار ⇄") : (isEn() ? "Right ⇄" : "يمين ⇄")));
        swapBtn.setTextSize(15f);
        swapBtn.setTextColor(Color.WHITE);
        swapBtn.setAllCaps(false);
        GradientDrawable swapBtnBg = new GradientDrawable();
        swapBtnBg.setColor(0xFF22323A);
        swapBtnBg.setCornerRadius(dp(16));
        swapBtnBg.setStroke(dp(1), 0xFFE5B64A);
        swapBtn.setBackground(swapBtnBg);
        swapBtn.setOnClickListener(v -> {
            boolean next = !qiblaView.isPanelSwapped();
            qiblaView.setPanelSwapped(next);
            getSharedPreferences(UI_PREFS, MODE_PRIVATE).edit().putBoolean(KEY_PANEL_SWAPPED, next).apply();
            swapBtn.setText((isEn() ? "Prayer Times: " : "موضع المواقيت: ") + (next ? (isEn() ? "Left ⇄" : "يسار ⇄") : (isEn() ? "Right ⇄" : "يمين ⇄")));
            if (mainSwapButton != null) {
                mainSwapButton.setText(swapButtonLabel(next, isEn()));
            }
        });
        LinearLayout.LayoutParams swapBtnLp = new LinearLayout.LayoutParams(dp(230), dp(40));
        swapBtnLp.bottomMargin = dp(8);
        content.addView(swapBtn, swapBtnLp);

        final Button guide = new Button(this);
        guide.setText(isEn() ? "How to Use" : "طريقة استخدام البرنامج");
        guide.setTextSize(15f);
        guide.setTextColor(Color.WHITE);
        guide.setAllCaps(false);
        GradientDrawable guideBg = new GradientDrawable();
        guideBg.setColor(0xFF165B63);
        guideBg.setCornerRadius(dp(16));
        guideBg.setStroke(dp(1), 0xFF2FE0C7);
        guide.setBackground(guideBg);
        guide.setOnClickListener(v -> {
            dialog.dismiss();
            handler.postDelayed(this::showUserGuideDialog, 120L);
        });
        LinearLayout.LayoutParams guideLp = new LinearLayout.LayoutParams(dp(230), dp(40));
        guideLp.bottomMargin = dp(8);
        content.addView(guide, guideLp);

        final Button about = new Button(this);
        about.setText(isEn() ? "About" : "حول");
        about.setTextSize(15f);
        about.setTextColor(Color.WHITE);
        about.setAllCaps(false);
        GradientDrawable aboutSectionBg = new GradientDrawable();
        aboutSectionBg.setColor(0xFF22323A);
        aboutSectionBg.setCornerRadius(dp(16));
        aboutSectionBg.setStroke(dp(1), 0xFFE5B64A);
        about.setBackground(aboutSectionBg);
        about.setOnClickListener(v -> {
            dialog.dismiss();
            handler.postDelayed(this::showAboutDetailsDialog, 120L);
        });
        LinearLayout.LayoutParams aboutSectionLp = new LinearLayout.LayoutParams(dp(210), dp(40));
        aboutSectionLp.bottomMargin = dp(8);
        content.addView(about, aboutSectionLp);

        scrollView.addView(content, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        card.addView(scrollView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, scrollHeight));

        final Button close = new Button(this);
        close.setText(isEn() ? "Close" : "إغلاق");
        close.setTextSize(15f);
        close.setTextColor(Color.WHITE);
        close.setAllCaps(false);
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setColor(0xFF117A7C);
        closeBg.setCornerRadius(dp(16));
        closeBg.setStroke(dp(1), 0xFF2FE0C7);
        close.setBackground(closeBg);
        close.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(dp(160), dp(38));
        closeLp.topMargin = dp(8);
        card.addView(close, closeLp);

        langBtn.setOnClickListener(v -> {
            boolean nextEn = !isEn();
            applyLanguageMode(nextEn);
            appTitle.setText(nextEn ? ("Qibla • Version " + BuildConfig.VERSION_NAME) : ("قبلة • الإصدار " + BuildConfig.VERSION_NAME));
            title.setText(nextEn ? "Settings" : "الإعدادات");
            update.setText(nextEn ? "Check for Updates" : "فحص التحديثات");
            appearanceTitle.setText(nextEn ? "Appearance & Language" : "المظهر واللغة");
            langBtn.setText(nextEn ? "Language: EN  ⇄  عربي" : "اللغة: العربية  ⇄  EN");
            theme.setText((nextEn ? "Theme: " : "المظهر: ") + themeModeLabel(qiblaView.getThemeMode()));
            fontSize.setText((nextEn ? "Font Size: " : "حجم الخط: ") + fontScaleLabel(qiblaView.getFontScale()));
            fontBtn.setText((nextEn ? "Font: " : "الخط: ") + QiblaView.getFontName(qiblaView.getFontIndex(), nextEn));
            swapBtn.setText((nextEn ? "Prayer Times: " : "موضع المواقيت: ") + (qiblaView.isPanelSwapped() ? (nextEn ? "Left ⇄" : "يسار ⇄") : (nextEn ? "Right ⇄" : "يمين ⇄")));
            guide.setText(nextEn ? "How to Use" : "طريقة استخدام البرنامج");
            about.setText(nextEn ? "About" : "حول");
            close.setText(nextEn ? "Close" : "إغلاق");
        });

        dialog.setContentView(card, new ViewGroup.LayoutParams(targetWidth, targetHeight));
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.dimAmount = 0.72f;
            attrs.width = targetWidth;
            attrs.height = targetHeight;
            attrs.gravity = Gravity.CENTER;
            window.setAttributes(attrs);
            window.setLayout(targetWidth, targetHeight);
        }
        dialog.setOnDismissListener(d -> handler.postDelayed(this::applyImmersiveMode, 120L));
        dialog.show();
    }

    private void showAboutDetailsDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);

        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int targetWidth = Math.min(dp(650), (int) (screenWidth * 0.90f));
        int targetHeight = Math.min(dp(440), (int) (screenHeight * 0.86f));
        int scrollHeight = Math.max(dp(130), targetHeight - dp(165));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(28), dp(14), dp(28), dp(14));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(0xF2171D23);
        cardBg.setCornerRadius(dp(26));
        cardBg.setStroke(dp(2), 0xFFE5B64A);
        card.setBackground(cardBg);

        TextView appTitle = noteText(isEn() ? ("Qibla • Version " + BuildConfig.VERSION_NAME) : ("قبلة • الإصدار " + BuildConfig.VERSION_NAME), 18f, 0xFFE5B64A, true);
        card.addView(appTitle, matchWrap(WindowManager.LayoutParams.WRAP_CONTENT));

        TextView title = noteText(isEn() ? "About" : "حول", 24f, 0xFFFFC857, true);
        title.setPadding(0, dp(2), 0, dp(8));
        card.addView(title, matchWrap(WindowManager.LayoutParams.WRAP_CONTENT));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setVerticalScrollBarEnabled(true);
        scrollView.setScrollbarFadingEnabled(false);
        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setGravity(Gravity.CENTER_HORIZONTAL);

        String aboutMsg = isEn()
                ? "Qibla was developed by Bandar bin Sulaiman for Haval and Tank users.\n\n" +
                  "The app displays the Qibla direction, vehicle/device heading, location, and Umm al-Qura prayer times, with online update support.\n\n" +
                  "This work is an ongoing charity (Sadaqah Jariyah) for me, you, and every Muslim.\n\n" +
                  "Developed by / Bandar bin Sulaiman\n" +
                  "TikTok & Snapchat / h9_haval\n" +
                  "Visit our website:\n" +
                  "H9 Store"
                : "تم تطوير تطبيق قبلة بواسطة بندر بن سليمان لخدمة مستخدمي هافال وتانك.\n\n" +
                  "التطبيق يعرض اتجاه القبلة، اتجاه المركبة، الموقع، ومواقيت الصلاة، مع دعم التحديثات عبر الإنترنت.\n\n" +
                  "هذا العمل صدقة جارية عني وعنك وعن كل مسلم.\n\n" +
                  "تم تطوير بواسطة / بندر بن سليمان\n" +
                  "حساب تيك توك و سناب شات / h9_haval\n" +
                  "زورونا عبر موقعنا الالكتروني\n" +
                  "متجر H9";
        TextView body = noteText(aboutMsg, 16f, 0xFFF1F5F6, false);
        body.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        body.setLineSpacing(dp(2), 1.08f);
        inner.addView(body, matchWrap(WindowManager.LayoutParams.WRAP_CONTENT));

        TextView store = noteText("https://h9-story.com", 17f, 0xFFFFC857, true);
        store.setPadding(dp(8), dp(10), dp(8), dp(10));
        store.setOnClickListener(v -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://h9-story.com"))); } catch (Exception ignored) { }
        });
        inner.addView(store, matchWrap(WindowManager.LayoutParams.WRAP_CONTENT));

        scrollView.addView(inner, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        card.addView(scrollView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, scrollHeight));

        Button close = new Button(this);
        close.setText(isEn() ? "Close" : "إغلاق");
        close.setTextSize(15f);
        close.setTextColor(Color.WHITE);
        close.setAllCaps(false);
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setColor(0xFF117A7C);
        closeBg.setCornerRadius(dp(16));
        closeBg.setStroke(dp(1), 0xFF2FE0C7);
        close.setBackground(closeBg);
        close.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(dp(160), dp(38));
        closeLp.topMargin = dp(8);
        card.addView(close, closeLp);

        dialog.setContentView(card, new ViewGroup.LayoutParams(targetWidth, targetHeight));
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.dimAmount = 0.72f;
            attrs.width = targetWidth;
            attrs.height = targetHeight;
            attrs.gravity = Gravity.CENTER;
            window.setAttributes(attrs);
            window.setLayout(targetWidth, targetHeight);
        }
        dialog.setOnDismissListener(d -> handler.postDelayed(this::applyImmersiveMode, 120L));
        dialog.show();
    }

    private void showDeveloperNoticeOnce() {
        final SharedPreferences prefs = getSharedPreferences(NOTICE_PREFS, MODE_PRIVATE);
        if (prefs.getBoolean(KEY_NOTICE_SHOWN, false)) return;
        showUserGuideDialog(() -> prefs.edit().putBoolean(KEY_NOTICE_SHOWN, true).apply(), true);
    }

    private void showUserGuideDialog() {
        showUserGuideDialog(null, false);
    }

    private void showUserGuideDialog(final Runnable onContinue, boolean firstRun) {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);

        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int targetWidth = Math.min(dp(680), (int) (screenWidth * 0.90f));
        int targetHeight = Math.min(dp(420), (int) (screenHeight * 0.85f));
        int scrollHeight = Math.max(dp(120), targetHeight - dp(136));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(22), dp(12), dp(22), dp(14));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(0xF4141A20);
        cardBg.setCornerRadius(dp(24));
        cardBg.setStroke(dp(2), 0xFFE5B64A);
        card.setBackground(cardBg);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        String headerTitleText;
        if (isEn()) {
            headerTitleText = firstRun ? ("Welcome to Qibla (" + BuildConfig.VERSION_NAME + ")") : "How to Use Qibla";
        } else {
            headerTitleText = firstRun ? ("مرحبًا بك في قبلة (" + BuildConfig.VERSION_NAME + ")") : "طريقة استخدام البرنامج";
        }
        TextView title = noteText(headerTitleText, 21f, 0xFFFFC857, true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        header.addView(title, titleLp);

        Button topClose = new Button(this);
        topClose.setText("✕");
        topClose.setTextSize(14f);
        topClose.setTextColor(0xFFFFC857);
        topClose.setPadding(0, 0, 0, 0);
        GradientDrawable topCloseBg = new GradientDrawable();
        topCloseBg.setColor(0x2EFFFFFF);
        topCloseBg.setCornerRadius(dp(16));
        topClose.setBackground(topCloseBg);
        topClose.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams topCloseLp = new LinearLayout.LayoutParams(dp(32), dp(32));
        header.addView(topClose, topCloseLp);

        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(38));
        headerLp.bottomMargin = dp(6);
        card.addView(header, headerLp);

        final ScrollView scrollView = new ScrollView(this);
        scrollView.setVerticalScrollBarEnabled(true);
        scrollView.setScrollbarFadingEnabled(false);

        LinearLayout scrollContent = new LinearLayout(this);
        scrollContent.setOrientation(LinearLayout.VERTICAL);
        scrollContent.setGravity(Gravity.CENTER_HORIZONTAL);

        String devCredit = isEn()
                ? "Developed by / Bandar Bin Sulaiman\n" +
                  "TikTok & Snapchat / h9_haval\n" +
                  "Visit our website: H9 Store"
                : "تم تطوير بواسطة / بندر بن سليمان\n" +
                  "حساب تيك توك و سناب شات / h9_haval\n" +
                  "زورونا عبر موقعنا الالكتروني / متجر H9";

        TextView devText = noteText(devCredit, 15f, 0xFFFFC857, true);
        devText.setGravity(Gravity.CENTER);
        devText.setLineSpacing(dp(2), 1.08f);
        devText.setPadding(dp(8), dp(4), dp(8), dp(4));
        scrollContent.addView(devText, matchWrap(WindowManager.LayoutParams.WRAP_CONTENT));

        TextView storeLink = noteText("https://h9-story.com", 16f, 0xFF2FE0C7, true);
        storeLink.setGravity(Gravity.CENTER);
        storeLink.setPadding(dp(8), dp(2), dp(8), dp(8));
        storeLink.setOnClickListener(v -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://h9-story.com"))); } catch (Exception ignored) { }
        });
        scrollContent.addView(storeLink, matchWrap(WindowManager.LayoutParams.WRAP_CONTENT));

        View divider = new View(this);
        divider.setBackgroundColor(0x44E5B64A);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(dp(220), dp(1));
        divLp.bottomMargin = dp(8);
        scrollContent.addView(divider, divLp);

        String guideBody = isEn()
                ? "Overview\n" +
                  "Qibla for Haval, Tank, and Android devices displays the Qibla direction relative to your vehicle or device heading, along with your location and Umm al-Qura prayer times.\n\n" +
                  "How to Use\n" +
                  "Enable GPS and wait a few seconds for location and compass heading stabilization. The arrow inside the compass indicates the Qibla direction relative to your heading.\n\n" +
                  "Location & Region\n" +
                  "The app automatically displays your city and province as soon as location data is available.\n\n" +
                  "Updates\n" +
                  "Open Settings and tap 'Check for Updates' to download and install new releases.\n\n" +
                  "Tip\n" +
                  "For optimal compass accuracy, allow the app a few seconds after launch to stabilize heading sensors."
                : "وصف البرنامج\n" +
                  "تطبيق قبلة لمستخدمي هافال وتانك، يعرض اتجاه القبلة بالنسبة لاتجاه المركبة، الموقع، المنطقة، ومواقيت الصلاة.\n\n" +
                  "طريقة الاستخدام\n" +
                  "فعّل GPS وانتظر عدة ثوانٍ حتى يتم تحديد الموقع واستقرار اتجاه المركبة. السهم داخل البوصلة يوضح اتجاه القبلة بالنسبة لاتجاه السيارة.\n\n" +
                  "الموقع والمنطقة\n" +
                  "يعرض التطبيق اسم المدينة والمنطقة عند توفر بيانات الموقع.\n\n" +
                  "التحديث\n" +
                  "من الإعدادات اختر «فحص التحديثات». إذا توفر إصدار جديد اختر «تنزيل وتثبيت».\n\n" +
                  "نصيحة\n" +
                  "لأفضل دقة للبوصلة اترك التطبيق عدة ثوانٍ بعد التشغيل حتى يستقر الاتجاه.";

        TextView body = noteText(guideBody, 15f, 0xFFF1F5F6, false);
        body.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        body.setLineSpacing(dp(2), 1.08f);
        body.setPadding(dp(8), dp(4), dp(8), dp(12));
        scrollContent.addView(body, matchWrap(WindowManager.LayoutParams.WRAP_CONTENT));

        scrollView.addView(scrollContent, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, scrollHeight);
        scrollLp.bottomMargin = dp(10);
        card.addView(scrollView, scrollLp);

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER);

        Button scrollDownButton = new Button(this);
        scrollDownButton.setText(isEn() ? "⬇ Scroll Down" : "⬇ النزول للأسفل");
        scrollDownButton.setTextSize(14f);
        scrollDownButton.setTextColor(0xFFFFC857);
        scrollDownButton.setAllCaps(false);
        GradientDrawable scrollDownBg = new GradientDrawable();
        scrollDownBg.setColor(0xFF22323A);
        scrollDownBg.setCornerRadius(dp(16));
        scrollDownBg.setStroke(dp(1), 0xFFE5B64A);
        scrollDownButton.setBackground(scrollDownBg);
        scrollDownButton.setOnClickListener(v -> scrollView.smoothScrollBy(0, dp(140)));
        LinearLayout.LayoutParams scrollBtnLp = new LinearLayout.LayoutParams(dp(135), dp(38));
        scrollBtnLp.rightMargin = dp(10);
        footer.addView(scrollDownButton, scrollBtnLp);

        Button continueButton = new Button(this);
        continueButton.setText(isEn() ? (firstRun ? "Get Started" : "Close") : (firstRun ? "بدء الاستخدام" : "إغلاق"));
        continueButton.setTextSize(15f);
        continueButton.setTextColor(Color.WHITE);
        continueButton.setAllCaps(false);
        GradientDrawable buttonBg = new GradientDrawable();
        buttonBg.setColor(0xFF117A7C);
        buttonBg.setCornerRadius(dp(16));
        buttonBg.setStroke(dp(1), 0xFF2FE0C7);
        continueButton.setBackground(buttonBg);
        continueButton.setOnClickListener(v -> {
            if (onContinue != null) onContinue.run();
            dialog.dismiss();
        });
        LinearLayout.LayoutParams buttonLp = new LinearLayout.LayoutParams(dp(155), dp(38));
        footer.addView(continueButton, buttonLp);

        card.addView(footer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(42)));

        dialog.setContentView(card, new ViewGroup.LayoutParams(targetWidth, targetHeight));
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.dimAmount = 0.72f;
            attrs.width = targetWidth;
            attrs.height = targetHeight;
            attrs.gravity = Gravity.CENTER;
            window.setAttributes(attrs);
            window.setLayout(targetWidth, targetHeight);
        }
        dialog.setOnDismissListener(d -> {
            if (onContinue != null) onContinue.run();
            handler.postDelayed(this::applyImmersiveMode, 120L);
        });
        dialog.show();
    }

    private String fontScaleLabel(float scale) {
        if (isEn()) {
            if (scale >= 1.25f) return "Extra Large";
            if (scale >= 1.10f) return "Large";
            if (scale <= 0.92f) return "Small";
            return "Medium";
        }
        if (scale >= 1.25f) return "كبير جداً";
        if (scale >= 1.10f) return "كبير";
        if (scale <= 0.92f) return "صغير";
        return "متوسط";
    }

    private String swapButtonLabel(boolean swapped, boolean english) {
        if (english) {
            return swapped ? "Prayers: Left ⇄" : "Prayers: Right ⇄";
        } else {
            return swapped ? "المواقيت: يسار ⇄" : "المواقيت: يمين ⇄";
        }
    }

    private String themeModeLabel(int mode) {
        if (isEn()) {
            switch (mode) {
                case QiblaView.THEME_EMERALD: return "Royal Emerald";
                case QiblaView.THEME_OBSIDIAN: return "Obsidian Navy";
                case QiblaView.THEME_AMBER: return "Desert Amber";
                case QiblaView.THEME_PEARL: return "Pearl Day";
                default: return "Auto (Day/Night)";
            }
        }
        switch (mode) {
            case QiblaView.THEME_EMERALD: return "زمردي ملكي";
            case QiblaView.THEME_OBSIDIAN: return "كحلي فاخر";
            case QiblaView.THEME_AMBER: return "عنبر ذهبي";
            case QiblaView.THEME_PEARL: return "نهاري لؤلؤي";
            default: return "تلقائي (حسب الوقت)";
        }
    }

    private TextView noteText(String text, float sizeSp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        view.setTextDirection(isEn() ? View.TEXT_DIRECTION_LTR : View.TEXT_DIRECTION_RTL);
        view.setTypeface(Typeface.create("sans", bold ? Typeface.BOLD : Typeface.NORMAL));
        return view;
    }

    private LinearLayout.LayoutParams matchWrap(int height) {
        return new LinearLayout.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, height);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void startMicroGLocationSupport() {
        if (microGLocationRunning) return;
        microGLocationRunning = true;
        handler.post(microGPollRunnable);
    }

    private final Runnable microGPollRunnable = new Runnable() {
        @Override public void run() {
            if (!microGLocationRunning) return;
            requestMicroGLastLocation();
            handler.postDelayed(this, 1500L);
        }
    };

    private void requestMicroGLastLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        try {
            Context gmsContext = createPackageContext("com.google.android.gms",
                    Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
            ClassLoader cl = gmsContext.getClassLoader();

            if (microGFusedClient == null) {
                Class<?> locationServices = Class.forName("com.google.android.gms.location.LocationServices", true, cl);
                java.lang.reflect.Method getter = locationServices.getMethod("getFusedLocationProviderClient", Context.class);
                microGFusedClient = getter.invoke(null, this);
            }
            if (microGFusedClient == null) return;

            java.lang.reflect.Method getLastLocation = microGFusedClient.getClass().getMethod("getLastLocation");
            Object task = getLastLocation.invoke(microGFusedClient);
            if (task == null) return;

            Class<?> listenerClass = Class.forName("com.google.android.gms.tasks.OnSuccessListener", true, cl);
            Object listener = java.lang.reflect.Proxy.newProxyInstance(cl, new Class[]{listenerClass},
                    (proxy, method, args) -> {
                        if ("onSuccess".equals(method.getName()) && args != null && args.length > 0 && args[0] instanceof Location) {
                            Location loc = (Location) args[0];
                            runOnUiThread(() -> {
                                if (isBetterLocation(loc, lastLocation)) updateLocation(loc);
                                updateLocationDiagnosticText("microG fused متصل");
                            });
                        }
                        return null;
                    });

            java.lang.reflect.Method addSuccess = null;
            for (java.lang.reflect.Method method : task.getClass().getMethods()) {
                if ("addOnSuccessListener".equals(method.getName()) && method.getParameterTypes().length == 1
                        && method.getParameterTypes()[0].getName().equals("com.google.android.gms.tasks.OnSuccessListener")) {
                    addSuccess = method;
                    break;
                }
            }
            if (addSuccess != null) addSuccess.invoke(task, listener);
        } catch (PackageManager.NameNotFoundException ignored) {
            // microG / Google Play Services not installed: normal Android GPS/network path remains active.
        } catch (Throwable ignored) {
            // Optional integration must never break the native location path.
        }
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
        if ("default".equals(current.getProvider()) && !"default".equals(candidate.getProvider())) return true;
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
        if (qiblaSensorHelper != null) qiblaSensorHelper.startListening();
        if (!gwmNaviHeadingStarted) startGwmNaviHeadingIntegration();
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) startLocation();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(uiTick);
        if (qiblaSensorHelper != null) qiblaSensorHelper.stopListening();
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
        if (qiblaSensorHelper != null) qiblaSensorHelper.setLocation(location);
        updateNetworkStatus();

        if (isFused) {
            updateFusedCourse(location);
        } else if (LocationManager.GPS_PROVIDER.equals(provider)) {
            updateLiveCourse(location);
        }
        maybeRefreshPrayerTimes(location);
        maybeResolveLocationName(location);
    }

    private void updateFusedCourse(Location location) {
        if (System.currentTimeMillis() - lastOemHeadingAt < 2500L) return;
        float speed = location.hasSpeed() ? location.getSpeed() : -1f;
        String bearingText = location.hasBearing() ? String.format(Locale.US, "%.1f°", location.getBearing()) : "--";
        String speedText = speed >= 0f ? String.format(Locale.US, "%.2f m/s", speed) : "--";
        String accuracyText = location.hasAccuracy() ? String.format(Locale.US, "%.1f m", location.getAccuracy()) : "--";
        updateLocationDiagnosticText("Provider: fused   Bearing: " + bearingText + "   Speed: " + speedText + "   Accuracy: " + accuracyText);
        android.util.Log.i("H9QiblaDR", "FUSED bearing=" + bearingText + " speed=" + speedText + " accuracy=" + accuracyText);

        // For this test build we intentionally accept a fused bearing even at standstill.
        // This will tell us whether GWM's DR service preserves a real vehicle heading.
        if (location.hasBearing() && !Float.isNaN(location.getBearing())) {
            float fusedHeading = normalize(location.getBearing());
            lastHeadingAt = System.currentTimeMillis();
            saveHeading(fusedHeading, "اتجاه fused من نظام هافال");
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


    private void restoreLastHeading() {
        try {
            SharedPreferences p = getSharedPreferences(HEADING_PREFS, MODE_PRIVATE);
            if (!p.contains(KEY_LAST_HEADING)) return;
            float h = normalize(p.getFloat(KEY_LAST_HEADING, 0f));
            String source = p.getString(KEY_LAST_HEADING_SOURCE, "آخر اتجاه محفوظ");
            lastHeadingAt = System.currentTimeMillis();
            if (qiblaView != null) qiblaView.setHeading(h, source == null ? "آخر اتجاه محفوظ" : source);
        } catch (Throwable ignored) { }
    }

    private void saveHeading(float heading, String source) {
        try {
            getSharedPreferences(HEADING_PREFS, MODE_PRIVATE).edit()
                    .putFloat(KEY_LAST_HEADING, normalize(heading))
                    .putString(KEY_LAST_HEADING_SOURCE, source)
                    .apply();
        } catch (Throwable ignored) { }
    }

    private void startGwmNaviHeadingIntegration() {
        if (gwmNaviHeadingStarted) return;
        gwmNaviHeadingStarted = true;

        new Thread(() -> {
            final String TAG = "H9QiblaNavi57";
            try {
                Context[] contexts = new Context[2];
                try {
                    contexts[0] = createPackageContext("com.gwm.app.offroadovs",
                            Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
                } catch (Throwable ignored) { }
                try {
                    contexts[1] = createPackageContext("com.gwm.navi.app",
                            Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
                } catch (Throwable ignored) { }

                Class<?> controlClass = null;
                Class<?> listenerClass = null;
                Context codeContext = null;
                for (Context c : contexts) {
                    if (c == null) continue;
                    try {
                        ClassLoader cl = c.getClassLoader();
                        controlClass = Class.forName("com.gwm.navi.api.NaviControl", false, cl);
                        listenerClass = Class.forName("com.gwm.navi.api.NaviDataListener", false, cl);
                        codeContext = c;
                        break;
                    } catch (Throwable ignored) { }
                }

                if (controlClass == null || listenerClass == null || codeContext == null) {
                    android.util.Log.w(TAG, "Navi API classes unavailable");
                    gwmNaviHeadingStarted = false;
                    return;
                }

                final Class<?> finalListenerClass = listenerClass;
                Object listener = java.lang.reflect.Proxy.newProxyInstance(
                        codeContext.getClassLoader(),
                        new Class<?>[]{listenerClass},
                        (proxy, method, args) -> {
                            if (args != null) {
                                for (Object arg : args) {
                                    Double h = extractHeadingDeep(arg, 0);
                                    if (h != null && !h.isNaN()) {
                                        applyGwmNaviHeading(h.floatValue());
                                        break;
                                    }
                                }
                            }
                            return defaultValue(method.getReturnType());
                        });
                gwmNaviDataListener = listener;

                Object control = createNaviControl(controlClass, codeContext);
                if (control == null) {
                    android.util.Log.w(TAG, "NaviControl instance unavailable");
                    gwmNaviHeadingStarted = false;
                    return;
                }
                gwmNaviControl = control;

                // Register the same NaviDataListener family used by GWM OffRoad.
                for (java.lang.reflect.Method m : controlClass.getMethods()) {
                    String n = m.getName().toLowerCase(Locale.US);
                    Class<?>[] pts = m.getParameterTypes();
                    boolean takesDataListener = false;
                    for (Class<?> pt : pts) {
                        if (pt.getName().equals(finalListenerClass.getName())) {
                            takesDataListener = true;
                            break;
                        }
                    }
                    if (!takesDataListener) continue;
                    if (!(n.contains("register") || n.contains("listener") || n.contains("location"))) continue;
                    try {
                        m.setAccessible(true);
                        m.invoke(control, buildNaviArgs(pts, codeContext, finalListenerClass, listener));
                        android.util.Log.i(TAG, "listener attached via " + m.getName());
                    } catch (Throwable ignored) { }
                }

                // Connect/init only on clearly non-destructive Navi API methods.
                for (java.lang.reflect.Method m : controlClass.getMethods()) {
                    String n = m.getName().toLowerCase(Locale.US);
                    if (!(n.equals("connect") || n.equals("init") || n.equals("initialize"))) continue;
                    try {
                        m.setAccessible(true);
                        m.invoke(control, buildNaviArgs(m.getParameterTypes(), codeContext, finalListenerClass, listener));
                    } catch (Throwable ignored) { }
                }

                // Start the location stream if the API exposes the same startLocation call seen in OffRoad.
                for (java.lang.reflect.Method m : controlClass.getMethods()) {
                    String n = m.getName().toLowerCase(Locale.US);
                    if (!(n.equals("startlocation") || n.equals("start_location"))) continue;
                    try {
                        m.setAccessible(true);
                        Object out = m.invoke(control, buildNaviArgs(m.getParameterTypes(), codeContext, finalListenerClass, listener));
                        Double h = extractHeadingDeep(out, 0);
                        if (h != null && !h.isNaN()) applyGwmNaviHeading(h.floatValue());
                        android.util.Log.i(TAG, "startLocation invoked");
                    } catch (Throwable e) {
                        android.util.Log.w(TAG, "startLocation failed: " + rootCauseName(e));
                    }
                }

                // Poll getLocationData as a second path. OffRoad contains this exact API.
                while (gwmNaviHeadingStarted && gwmNaviControl == control) {
                    boolean invoked = false;
                    for (java.lang.reflect.Method m : controlClass.getMethods()) {
                        String n = m.getName().toLowerCase(Locale.US);
                        if (!n.equals("getlocationdata")) continue;
                        try {
                            m.setAccessible(true);
                            Object out = m.invoke(control, buildNaviArgs(m.getParameterTypes(), codeContext, finalListenerClass, listener));
                            invoked = true;
                            Double h = extractHeadingDeep(out, 0);
                            if (h != null && !h.isNaN()) applyGwmNaviHeading(h.floatValue());
                        } catch (Throwable ignored) { }
                    }
                    try { Thread.sleep(invoked ? 250L : 1000L); } catch (InterruptedException ignored) { }
                }
            } catch (Throwable e) {
                android.util.Log.w(TAG, "Navi heading integration stopped: " + rootCauseName(e));
                gwmNaviHeadingStarted = false;
            }
        }, "H9-Navi-Heading-57").start();
    }

    private Object createNaviControl(Class<?> controlClass, Context context) {
        String[] names = new String[]{"getInstance", "instance", "getDefault"};
        for (String name : names) {
            for (java.lang.reflect.Method m : controlClass.getMethods()) {
                if (!m.getName().equals(name) || !java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
                try {
                    m.setAccessible(true);
                    Object v = m.invoke(null, buildNaviArgs(m.getParameterTypes(), context, null, null));
                    if (v != null) return v;
                } catch (Throwable ignored) { }
            }
        }
        for (java.lang.reflect.Constructor<?> c : controlClass.getDeclaredConstructors()) {
            try {
                c.setAccessible(true);
                Object v = c.newInstance(buildNaviArgs(c.getParameterTypes(), context, null, null));
                if (v != null) return v;
            } catch (Throwable ignored) { }
        }
        return null;
    }

    private Object[] buildNaviArgs(Class<?>[] types, Context context, Class<?> listenerType, Object listener) {
        Object[] args = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            Class<?> t = types[i];
            if (Context.class.isAssignableFrom(t)) {
                args[i] = context;
            } else if (listenerType != null && t.getName().equals(listenerType.getName())) {
                args[i] = listener;
            } else if (t.isInterface()) {
                try {
                    args[i] = java.lang.reflect.Proxy.newProxyInstance(
                            context.getClassLoader(), new Class<?>[]{t},
                            (proxy, method, methodArgs) -> {
                                if (methodArgs != null) {
                                    for (Object a : methodArgs) {
                                        Double h = extractHeadingDeep(a, 0);
                                        if (h != null && !h.isNaN()) {
                                            applyGwmNaviHeading(h.floatValue());
                                            break;
                                        }
                                    }
                                }
                                return defaultValue(method.getReturnType());
                            });
                } catch (Throwable ignored) {
                    args[i] = null;
                }
            } else if (t == String.class) {
                args[i] = getPackageName();
            } else {
                args[i] = defaultValue(t);
            }
        }
        return args;
    }

    private Object defaultValue(Class<?> t) {
        if (t == null || t == Void.TYPE || !t.isPrimitive()) return null;
        if (t == Boolean.TYPE) return false;
        if (t == Byte.TYPE) return (byte) 0;
        if (t == Short.TYPE) return (short) 0;
        if (t == Integer.TYPE) return 0;
        if (t == Long.TYPE) return 0L;
        if (t == Float.TYPE) return 0f;
        if (t == Double.TYPE) return 0d;
        if (t == Character.TYPE) return (char) 0;
        return null;
    }

    private Double extractHeadingDeep(Object value, int depth) {
        if (value == null || depth > 3) return null;

        Double direct = extractHeading(value);
        if (direct != null && !direct.isNaN()) return direct;

        if (value instanceof Bundle) {
            Bundle b = (Bundle) value;
            String[] keys = new String[]{"heading", "direction", "bearing", "azimuth"};
            for (String k : keys) {
                try {
                    Object v = b.get(k);
                    if (v instanceof Number) return ((Number) v).doubleValue();
                } catch (Throwable ignored) { }
            }
        }

        if (value instanceof java.util.Map) {
            java.util.Map<?, ?> map = (java.util.Map<?, ?>) value;
            for (Object k : map.keySet()) {
                if (k == null) continue;
                String n = String.valueOf(k).toLowerCase(Locale.US);
                if (n.contains("heading") || n.contains("direction") || n.contains("bearing") || n.contains("azimuth")) {
                    Object v = map.get(k);
                    if (v instanceof Number) return ((Number) v).doubleValue();
                }
            }
        }

        Class<?> c = value.getClass();
        if (c.isArray()) {
            int n = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < Math.min(n, 8); i++) {
                Double h = extractHeadingDeep(java.lang.reflect.Array.get(value, i), depth + 1);
                if (h != null) return h;
            }
        }
        return null;
    }

    private void applyGwmNaviHeading(float rawHeading) {
        if (Float.isNaN(rawHeading) || Float.isInfinite(rawHeading)) return;
        final float h = normalize(rawHeading);
        lastOemHeadingAt = System.currentTimeMillis();
        lastHeadingAt = lastOemHeadingAt;
        saveHeading(h, "اتجاه السيارة من بوصلة هافال");
        runOnUiThread(() -> {
            if (qiblaView != null) qiblaView.setHeading(h, "اتجاه السيارة من بوصلة هافال");
        });
    }

    private void startOemDrIntegration() {
        if (oemDrRunning) return;
        oemDrRunning = true;
        new Thread(() -> {
            final String TAG = "H9QiblaOEM";
            try {
                Context navCtx = createPackageContext("com.gwm.navi.app",
                        Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
                ClassLoader cl = navCtx.getClassLoader();

                // Initialize the same dependency layer used by the OEM navigation process.
                try {
                    Class<?> mgr = Class.forName("com.gwm.navi.dependcy.NaviDependcyManager", false, cl);
                    java.lang.reflect.Method instanceM = mgr.getDeclaredMethod("instance");
                    instanceM.setAccessible(true);
                    Object manager = instanceM.invoke(null);
                    if (manager != null) {
                        try {
                            java.lang.reflect.Method init = mgr.getDeclaredMethod("init", Context.class);
                            init.setAccessible(true);
                            init.invoke(manager, navCtx);
                        } catch (Throwable ignored) { }
                        try {
                            java.lang.reflect.Method initService = mgr.getDeclaredMethod("initService", Context.class);
                            initService.setAccessible(true);
                            initService.invoke(manager, navCtx);
                        } catch (Throwable ignored) { }
                    }
                } catch (Throwable e) {
                    android.util.Log.w(TAG, "NaviDependcyManager init: " + rootCauseName(e));
                }

                try {
                    Class<?> sm = Class.forName("com.gwm.navi.dependcy.manager.ServiceManager", false, cl);
                    java.lang.reflect.Method getInstance = sm.getDeclaredMethod("getInstance");
                    getInstance.setAccessible(true);
                    Object smi = getInstance.invoke(null);
                    if (smi != null) {
                        try {
                            java.lang.reflect.Method initManager = sm.getDeclaredMethod("initManager", Context.class);
                            initManager.setAccessible(true);
                            initManager.invoke(smi, navCtx);
                        } catch (Throwable ignored) { }
                    }
                } catch (Throwable e) {
                    android.util.Log.w(TAG, "ServiceManager init: " + rootCauseName(e));
                }

                Class<?> factory = Class.forName("com.gwm.navi.service.manager.ServiceManagerFactory", false, cl);
                java.lang.reflect.Method getLocationService = factory.getDeclaredMethod("getLocationService");
                getLocationService.setAccessible(true);

                // The OEM dependency bind may complete asynchronously; retry for up to 10 seconds.
                for (int i = 0; i < 40 && oemDrRunning && oemLocationService == null; i++) {
                    try { oemLocationService = getLocationService.invoke(null); } catch (Throwable ignored) { }
                    if (oemLocationService == null) {
                        try { Thread.sleep(250L); } catch (InterruptedException ignored) { }
                    }
                }

                if (oemLocationService == null) {
                    android.util.Log.w(TAG, "OEM location service unavailable; keeping normal heading fallback.");
                    return;
                }

                java.lang.reflect.Method getDr = null;
                try { getDr = oemLocationService.getClass().getMethod("getDrLocInfo"); }
                catch (NoSuchMethodException e) {
                    try { getDr = oemLocationService.getClass().getDeclaredMethod("getDrLocInfo"); }
                    catch (NoSuchMethodException ignored) { }
                }
                if (getDr == null) {
                    android.util.Log.w(TAG, "getDrLocInfo() not exposed by OEM location service.");
                    return;
                }
                getDr.setAccessible(true);

                android.util.Log.i(TAG, "OEM DR heading active.");
                while (oemDrRunning) {
                    try {
                        Object dr = getDr.invoke(oemLocationService);
                        Double heading = extractHeading(dr);
                        if (heading != null && !heading.isNaN() && !heading.isInfinite()) {
                            final float h = normalize(heading.floatValue());
                            lastOemHeadingAt = System.currentTimeMillis();
                            lastHeadingAt = lastOemHeadingAt;
                            saveHeading(h, "اتجاه السيارة من نظام هافال");
                            runOnUiThread(() -> qiblaView.setHeading(h, "اتجاه السيارة من نظام هافال"));
                        }
                    } catch (Throwable e) {
                        android.util.Log.w(TAG, "DR read: " + rootCauseName(e));
                    }
                    try { Thread.sleep(250L); } catch (InterruptedException ignored) { }
                }
            } catch (Throwable e) {
                android.util.Log.e("H9QiblaOEM", "OEM integration failed: " + rootCauseName(e));
            } finally {
                oemDrRunning = false;
            }
        }, "H9-OEM-DR").start();
    }

    private void probeDrDependencyInitV54() {
        new Thread(() -> {
            final String TAG = "H9QiblaDep54";
            android.util.Log.i(TAG, "BEGIN dependency init probe");
            try {
                Context navCtx = createPackageContext("com.gwm.navi.app",
                        Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
                ClassLoader cl = navCtx.getClassLoader();

                Class<?> mgr = Class.forName("com.gwm.navi.dependcy.NaviDependcyManager", false, cl);
                java.lang.reflect.Method instanceM = mgr.getDeclaredMethod("instance");
                instanceM.setAccessible(true);
                Object manager = instanceM.invoke(null);
                android.util.Log.w(TAG, "MANAGER instance=" + (manager == null ? "NULL" : manager.getClass().getName()));

                if (manager != null) {
                    try {
                        java.lang.reflect.Method init = mgr.getDeclaredMethod("init", Context.class);
                        init.setAccessible(true);
                        init.invoke(manager, navCtx);
                        android.util.Log.w(TAG, "MANAGER init(navCtx)=OK");
                    } catch (Throwable e) {
                        android.util.Log.e(TAG, "MANAGER init ERR=" + rootCauseName(e) + ":" + shortMessage(e));
                    }

                    try {
                        java.lang.reflect.Method initService = mgr.getDeclaredMethod("initService", Context.class);
                        initService.setAccessible(true);
                        initService.invoke(manager, navCtx);
                        android.util.Log.w(TAG, "MANAGER initService(navCtx)=OK");
                    } catch (Throwable e) {
                        android.util.Log.i(TAG, "MANAGER initService ERR=" + rootCauseName(e) + ":" + shortMessage(e));
                    }

                    try {
                        Class<?> sysAbility = Class.forName("com.gwm.navi.service.dependcy.system.INaviSystemAbility", false, cl);
                        java.lang.reflect.Method getAbility = mgr.getDeclaredMethod("getAbility", Class.class);
                        getAbility.setAccessible(true);
                        Object ability = getAbility.invoke(manager, sysAbility);
                        android.util.Log.w(TAG, "MANAGER systemAbility=" + (ability == null ? "NULL" : ability.getClass().getName()));
                        if (ability != null) {
                            String[] methods = new String[]{
                                    "getNaviAppType","getDriveType","getDriveHandleType","getGNSSType",
                                    "getProjectCode","getMapWrapper","getFlavor","getSalesRegion",
                                    "getBrand","getAutoType"
                            };
                            for (String n : methods) {
                                try {
                                    java.lang.reflect.Method m = ability.getClass().getMethod(n);
                                    m.setAccessible(true);
                                    Object v = m.invoke(ability);
                                    android.util.Log.w(TAG, "ABILITY " + n + "=" + String.valueOf(v));
                                } catch (Throwable e) {
                                    android.util.Log.i(TAG, "ABILITY_ERR " + n + " " + rootCauseName(e));
                                }
                            }
                        }
                    } catch (Throwable e) {
                        android.util.Log.e(TAG, "GET_ABILITY ERR=" + rootCauseName(e) + ":" + shortMessage(e));
                    }
                }

                try {
                    Class<?> sm = Class.forName("com.gwm.navi.dependcy.manager.ServiceManager", false, cl);
                    java.lang.reflect.Method getInstance = sm.getDeclaredMethod("getInstance");
                    getInstance.setAccessible(true);
                    Object smi = getInstance.invoke(null);
                    android.util.Log.w(TAG, "SERVICE_MANAGER=" + (smi == null ? "NULL" : smi.getClass().getName()));
                    if (smi != null) {
                        try {
                            java.lang.reflect.Method initManager = sm.getDeclaredMethod("initManager", Context.class);
                            initManager.setAccessible(true);
                            initManager.invoke(smi, navCtx);
                            android.util.Log.w(TAG, "SERVICE_MANAGER initManager(navCtx)=OK");
                        } catch (Throwable e) {
                            android.util.Log.i(TAG, "SERVICE_MANAGER init ERR=" + rootCauseName(e) + ":" + shortMessage(e));
                        }
                        try {
                            java.lang.reflect.Method getDep = sm.getDeclaredMethod("getDependcyService");
                            getDep.setAccessible(true);
                            Object dep = getDep.invoke(smi);
                            android.util.Log.w(TAG, "SERVICE_MANAGER getDependcyService=" +
                                    (dep == null ? "NULL" : dep.getClass().getName()));
                            if (dep != null) {
                                Class<?> sysAbility = Class.forName("com.gwm.navi.service.dependcy.system.INaviSystemAbility", false, cl);
                                java.lang.reflect.Method getAbility = dep.getClass().getMethod("getAbility", Class.class);
                                getAbility.setAccessible(true);
                                Object ability = getAbility.invoke(dep, sysAbility);
                                android.util.Log.w(TAG, "SERVICE_MANAGER systemAbility=" +
                                        (ability == null ? "NULL" : ability.getClass().getName()));
                            }
                        } catch (Throwable e) {
                            android.util.Log.e(TAG, "SERVICE_MANAGER getDependcyService ERR=" +
                                    rootCauseName(e) + ":" + shortMessage(e));
                        }
                    }
                } catch (Throwable e) {
                    android.util.Log.e(TAG, "SERVICE_MANAGER FATAL=" + rootCauseName(e) + ":" + shortMessage(e));
                }

                // Re-check the factory accessor after initializing the dependency layer.
                try {
                    Class<?> factory = Class.forName("com.gwm.navi.service.manager.ServiceManagerFactory", false, cl);
                    java.lang.reflect.Method m = factory.getDeclaredMethod("getDependcyService");
                    m.setAccessible(true);
                    Object dep = m.invoke(null);
                    android.util.Log.w(TAG, "FACTORY_AFTER_INIT getDependcyService=" +
                            (dep == null ? "NULL" : dep.getClass().getName()));
                } catch (Throwable e) {
                    android.util.Log.e(TAG, "FACTORY_AFTER_INIT ERR=" + rootCauseName(e) + ":" + shortMessage(e));
                }

                android.util.Log.i(TAG, "END");
            } catch (Throwable e) {
                android.util.Log.e(TAG, "FATAL " + rootCauseName(e) + ":" + shortMessage(e));
            }
        }, "H9-DR-Dep54").start();
    }

    private void probeDrDependencyV53() {
        new Thread(() -> {
            final String TAG = "H9QiblaDep53";
            android.util.Log.i(TAG, "BEGIN navigation dependency probe");
            try {
                Context navCtx = createPackageContext("com.gwm.navi.app",
                        Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
                ClassLoader cl = navCtx.getClassLoader();

                Class<?> factory = Class.forName("com.gwm.navi.service.manager.ServiceManagerFactory", false, cl);
                android.util.Log.i(TAG, "FACTORY " + factory.getName());

                for (java.lang.reflect.Method m : factory.getDeclaredMethods()) {
                    String n = m.getName().toLowerCase(Locale.US);
                    if (n.contains("depend") || n.contains("system") || n.contains("ability") || n.contains("init")) {
                        android.util.Log.w(TAG, "FACTORY_METHOD " + methodSig(m));
                    }
                }

                Object dependency = null;
                String[] depNames = new String[]{"getDependencyService","getDependcyService"};
                for (String name : depNames) {
                    try {
                        java.lang.reflect.Method m = factory.getDeclaredMethod(name);
                        m.setAccessible(true);
                        dependency = m.invoke(null);
                        android.util.Log.w(TAG, name + "=" + (dependency == null ? "NULL" : dependency.getClass().getName()));
                        if (dependency != null) break;
                    } catch (Throwable e) {
                        android.util.Log.i(TAG, name + "_ERR " + rootCauseName(e));
                    }
                }

                if (dependency != null) {
                    Class<?> depClass = dependency.getClass();
                    android.util.Log.i(TAG, "DEPENDENCY_CLASS " + depClass.getName());

                    for (java.lang.reflect.Method m : depClass.getMethods()) {
                        String n = m.getName().toLowerCase(Locale.US);
                        if (n.contains("ability") || n.contains("system") || n.contains("init") ||
                                n.contains("service") || n.contains("depend")) {
                            android.util.Log.w(TAG, "DEP_METHOD " + methodSig(m));
                        }
                    }

                    try {
                        Class<?> sysAbility = Class.forName("com.gwm.navi.service.dependcy.system.INaviSystemAbility", false, cl);
                        java.lang.reflect.Method getAbility = null;
                        for (java.lang.reflect.Method m : depClass.getMethods()) {
                            if (m.getName().equals("getAbility") && m.getParameterTypes().length == 1 &&
                                    m.getParameterTypes()[0] == Class.class) {
                                getAbility = m; break;
                            }
                        }
                        if (getAbility != null) {
                            getAbility.setAccessible(true);
                            Object ability = getAbility.invoke(dependency, sysAbility);
                            android.util.Log.w(TAG, "SYSTEM_ABILITY=" + (ability == null ? "NULL" : ability.getClass().getName()));
                            if (ability != null) {
                                String[] getters = new String[]{
                                        "getNaviAppType","getDriveType","getDriveHandleType","getGNSSType",
                                        "getProjectCode","getMapWrapper","getFlavor","getSalesRegion"
                                };
                                for (String gn : getters) {
                                    try {
                                        java.lang.reflect.Method gm = ability.getClass().getMethod(gn);
                                        gm.setAccessible(true);
                                        Object v = gm.invoke(ability);
                                        android.util.Log.w(TAG, "ABILITY " + gn + "=" + String.valueOf(v));
                                    } catch (Throwable e) {
                                        android.util.Log.i(TAG, "ABILITY_ERR " + gn + " " + rootCauseName(e));
                                    }
                                }
                            }
                        } else {
                            android.util.Log.w(TAG, "NO getAbility(Class) on dependency implementation");
                        }
                    } catch (Throwable e) {
                        android.util.Log.e(TAG, "SYSTEM_ABILITY_ERR " + rootCauseName(e));
                    }
                }

                // Also scan for dependency implementation/singletons if the factory accessor is null.
                android.content.pm.ApplicationInfo ai = getPackageManager().getApplicationInfo("com.gwm.navi.app", 0);
                dalvik.system.DexFile dex = new dalvik.system.DexFile(ai.sourceDir);
                java.util.Enumeration<String> en = dex.entries();
                int found = 0;
                while (en.hasMoreElements() && found < 40) {
                    String cn = en.nextElement();
                    String l = cn.toLowerCase(Locale.US);
                    if (!(l.contains("dependcy") || l.contains("dependency"))) continue;
                    if (!(l.contains("service") || l.contains("manager") || l.contains("factory"))) continue;
                    try {
                        Class<?> c = Class.forName(cn, false, cl);
                        android.util.Log.i(TAG, "CANDIDATE " + cn);
                        found++;
                        for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                            String n = m.getName().toLowerCase(Locale.US);
                            if (n.contains("instance") || n.contains("init") || n.contains("ability") ||
                                    n.contains("service") || n.startsWith("get")) {
                                android.util.Log.w(TAG, "  METHOD " + methodSig(m));
                            }
                        }
                        for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                            if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                            String n = f.getName().toLowerCase(Locale.US);
                            if (n.contains("instance") || n.contains("service") || n.contains("depend")) {
                                try {
                                    f.setAccessible(true);
                                    Object v = f.get(null);
                                    android.util.Log.w(TAG, "  STATIC " + f.getName() + "=" +
                                            (v == null ? "NULL" : v.getClass().getName()));
                                } catch (Throwable ignored) { }
                            }
                        }
                    } catch (Throwable ignored) { }
                }
                try { dex.close(); } catch (Throwable ignored) { }

                android.util.Log.i(TAG, "END");
            } catch (Throwable e) {
                android.util.Log.e(TAG, "FATAL " + rootCauseName(e));
            }
        }, "H9-DR-Dep53").start();
    }

    private void probeDrInterceptorV52() {
        new Thread(() -> {
            final String TAG = "H9QiblaGate52";
            android.util.Log.i(TAG, "BEGIN interceptor decision probe");
            try {
                Context navCtx = createPackageContext("com.gwm.navi.app",
                        Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
                ClassLoader cl = navCtx.getClassLoader();

                Class<?> apiContextClass = Class.forName("com.gwm.navi.service.external.api.ApiContext", false, cl);
                Class<?> appModeClass = Class.forName("com.gwm.navi.service.external.interceptor.AppModeInterceptor", false, cl);
                Class<?> permClass = Class.forName("com.gwm.navi.service.external.interceptor.PermissionInterceptor", false, cl);

                java.lang.reflect.Constructor<?> apiCtor = apiContextClass.getDeclaredConstructor(String.class);
                apiCtor.setAccessible(true);

                String[] payloads = new String[]{
                        "{\\\"moduleType\\\":14000,\\\"actionType\\\":0,\\\"operateType\\\":-9999,\\\"source\\\":\\\"sa.h9.qibla.stable\\\"}",
                        "{\\\"moduleType\\\":14000,\\\"actionType\\\":21,\\\"operateType\\\":-9999,\\\"source\\\":\\\"sa.h9.qibla.stable\\\"}",
                        "{\\\"moduleType\\\":14000,\\\"actionType\\\":32,\\\"operateType\\\":-9999,\\\"source\\\":\\\"sa.h9.qibla.stable\\\"}",
                        "{\\\"pkgName\\\":\\\"sa.h9.qibla.stable\\\",\\\"protocolId\\\":\\\"14021\\\",\\\"verName\\\":\\\"1.0\\\",\\\"data\\\":{}}"
                };

                java.lang.reflect.Method appDeal = appModeClass.getDeclaredMethod("dealBefore", apiContextClass);
                java.lang.reflect.Method permDeal = permClass.getDeclaredMethod("dealBefore", apiContextClass);
                appDeal.setAccessible(true);
                permDeal.setAccessible(true);

                java.lang.reflect.Field protocolTypeField = apiContextClass.getDeclaredField("protocolType");
                protocolTypeField.setAccessible(true);

                for (String p : payloads) {
                    for (int protoType : new int[]{1,2}) {
                        try {
                            Object ctx = apiCtor.newInstance(p);
                            protocolTypeField.setInt(ctx, protoType);

                            Object appMode = appModeClass.getDeclaredConstructor().newInstance();
                            Object perm = permClass.getDeclaredConstructor().newInstance();

                            boolean appResult = (Boolean) appDeal.invoke(appMode, ctx);
                            boolean permResult = (Boolean) permDeal.invoke(perm, ctx);
                            android.util.Log.w(TAG, "DECISION protoType=" + protoType +
                                    " appMode=" + appResult + " permission=" + permResult +
                                    " payload=" + p);

                            for (java.lang.reflect.Field f : apiContextClass.getDeclaredFields()) {
                                try {
                                    f.setAccessible(true);
                                    Object v = f.get(ctx);
                                    if (v != null) {
                                        android.util.Log.i(TAG, "  CTX " + f.getName() + "=" + String.valueOf(v));
                                    }
                                } catch (Throwable ignored) { }
                            }
                        } catch (Throwable e) {
                            android.util.Log.e(TAG, "DECISION_ERR protoType=" + protoType +
                                    " payload=" + p + " err=" + rootCauseName(e));
                        }
                    }
                }

                // Discover the exact dependency/system-ability API used by AppModeInterceptor.
                String[] names = new String[]{
                        "com.gwm.navi.service.dependcy.IDependcyService",
                        "com.gwm.navi.service.dependency.IDependcyService",
                        "com.gwm.navi.service.dependcy.INaviSystemAbility",
                        "com.gwm.navi.service.dependency.INaviSystemAbility"
                };
                for (String cn : names) {
                    try {
                        Class<?> c = Class.forName(cn, false, cl);
                        android.util.Log.i(TAG, "CLASS " + cn);
                        for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                            String n = m.getName().toLowerCase(Locale.US);
                            if (n.contains("dr") || n.contains("mode") || n.contains("support") ||
                                    n.contains("system") || n.contains("ability") || n.contains("app")) {
                                android.util.Log.w(TAG, "METHOD " + cn + "#" + methodSig(m));
                            }
                        }
                    } catch (Throwable ignored) { }
                }

                // Search loaded dex for INaviSystemAbility / IDependcyService classes even if package spelling differs.
                android.content.pm.ApplicationInfo ai = getPackageManager().getApplicationInfo("com.gwm.navi.app", 0);
                dalvik.system.DexFile dex = new dalvik.system.DexFile(ai.sourceDir);
                java.util.Enumeration<String> en = dex.entries();
                while (en.hasMoreElements()) {
                    String cn = en.nextElement();
                    String l = cn.toLowerCase(Locale.US);
                    if (!(l.contains("navisystemability") || l.contains("idependcyservice") || l.contains("dependencyservice"))) continue;
                    try {
                        Class<?> c = Class.forName(cn, false, cl);
                        android.util.Log.i(TAG, "FOUND_CLASS " + cn);
                        for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                            android.util.Log.w(TAG, "  FOUND_METHOD " + methodSig(m));
                        }
                    } catch (Throwable ignored) { }
                }
                try { dex.close(); } catch (Throwable ignored) { }

                android.util.Log.i(TAG, "END");
            } catch (Throwable e) {
                android.util.Log.e(TAG, "FATAL " + rootCauseName(e));
            }
        }, "H9-DR-Gate52").start();
    }

    private void probeDrObjectInheritanceV51() {
        new Thread(() -> {
            final String TAG = "H9QiblaGate51";
            android.util.Log.i(TAG, "BEGIN inherited object decode");
            try {
                Context navCtx = createPackageContext("com.gwm.navi.app",
                        Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
                ClassLoader cl = navCtx.getClassLoader();

                String[] targets = new String[]{
                        "com.gwm.navi.service.external.interceptor.AppModeInterceptor",
                        "com.gwm.navi.service.external.interceptor.PermissionInterceptor"
                };

                for (String cn : targets) {
                    Class<?> c = Class.forName(cn, false, cl);
                    Object owner = c.getDeclaredConstructor().newInstance();
                    android.util.Log.i(TAG, "TARGET " + cn);

                    for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                        f.setAccessible(true);
                        Object v = null;
                        try { v = f.get(owner); } catch (Throwable ignored) { }
                        if (!(v instanceof java.util.List)) continue;

                        java.util.List<?> list = (java.util.List<?>) v;
                        android.util.Log.w(TAG, "LIST " + f.getName() + " size=" + list.size());

                        for (int i = 0; i < list.size(); i++) {
                            Object item = list.get(i);
                            if (item == null) continue;
                            android.util.Log.w(TAG, "ITEM " + f.getName() + "[" + i + "] class=" + item.getClass().getName());

                            // Traverse the whole inheritance chain. Previous build only inspected
                            // fields declared directly on NaviApiDataOvs and therefore missed
                            // protocol/action/opera fields inherited from its base bean.
                            for (Class<?> k = item.getClass(); k != null && k != Object.class; k = k.getSuperclass()) {
                                android.util.Log.i(TAG, "  SUPER " + k.getName());
                                for (java.lang.reflect.Field x : k.getDeclaredFields()) {
                                    try {
                                        x.setAccessible(true);
                                        Object xv = java.lang.reflect.Modifier.isStatic(x.getModifiers()) ? x.get(null) : x.get(item);
                                        android.util.Log.w(TAG, "  FIELD " + k.getSimpleName() + "." + x.getName() +
                                                ":" + x.getType().getSimpleName() + "=" + String.valueOf(xv));
                                    } catch (Throwable e) {
                                        android.util.Log.i(TAG, "  FIELD_ERR " + x.getName() + " " + rootCauseName(e));
                                    }
                                }

                                for (java.lang.reflect.Method m : k.getDeclaredMethods()) {
                                    if (m.getParameterTypes().length != 0) continue;
                                    String mn = m.getName().toLowerCase(Locale.US);
                                    boolean interesting = mn.startsWith("get") || mn.startsWith("is") ||
                                            mn.contains("protocol") || mn.contains("action") ||
                                            mn.contains("opera") || mn.contains("package") ||
                                            mn.contains("version") || mn.contains("type") ||
                                            mn.contains("valid");
                                    if (!interesting) continue;
                                    if (m.getReturnType() == Void.TYPE) continue;
                                    try {
                                        m.setAccessible(true);
                                        Object mv = m.invoke(item);
                                        android.util.Log.w(TAG, "  CALL " + k.getSimpleName() + "." +
                                                m.getName() + "()=" + String.valueOf(mv));
                                    } catch (Throwable e) {
                                        android.util.Log.i(TAG, "  CALL_ERR " + m.getName() + " " + rootCauseName(e));
                                    }
                                }
                            }
                        }
                    }
                }

                // Inspect base bean constructors and field names once, so we know how list entries
                // are encoded even if getters are absent.
                String[] beans = new String[]{
                        "com.gwm.navi.service.external.ovs.NaviApiDataOvs",
                        "com.gwm.navi.service.external.bean.INaviApiData"
                };
                for (String cn : beans) {
                    try {
                        Class<?> c = Class.forName(cn, false, cl);
                        android.util.Log.i(TAG, "BEAN " + cn + " superclass=" +
                                (c.getSuperclass() == null ? "null" : c.getSuperclass().getName()));
                        for (java.lang.reflect.Constructor<?> ctor : c.getDeclaredConstructors()) {
                            android.util.Log.i(TAG, "BEAN_CTOR " + ctor.toString());
                        }
                        for (java.lang.reflect.Method m : c.getMethods()) {
                            String n = m.getName().toLowerCase(Locale.US);
                            if (n.contains("action") || n.contains("opera") || n.contains("protocol") ||
                                    n.contains("package") || n.contains("version") || n.contains("valid")) {
                                android.util.Log.i(TAG, "BEAN_METHOD " + methodSig(m));
                            }
                        }
                    } catch (Throwable e) {
                        android.util.Log.e(TAG, "BEAN_ERR " + cn + " " + rootCauseName(e));
                    }
                }

                android.util.Log.i(TAG, "END");
            } catch (Throwable e) {
                android.util.Log.e(TAG, "FATAL " + rootCauseName(e));
            }
        }, "H9-DR-Gate51").start();
    }

    private void probeDrGateObjectsV50() {
        new Thread(() -> {
            final String TAG = "H9QiblaGate50";
            android.util.Log.i(TAG, "BEGIN decode DR gate objects");
            try {
                Context navCtx = createPackageContext("com.gwm.navi.app",
                        Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
                ClassLoader cl = navCtx.getClassLoader();

                String[] targets = new String[]{
                        "com.gwm.navi.service.external.interceptor.AppModeInterceptor",
                        "com.gwm.navi.service.external.interceptor.PermissionInterceptor"
                };

                for (String cn : targets) {
                    Class<?> c = Class.forName(cn, false, cl);
                    java.lang.reflect.Constructor<?> ctor = c.getDeclaredConstructor();
                    ctor.setAccessible(true);
                    Object instance = ctor.newInstance();
                    android.util.Log.i(TAG, "TARGET " + cn);

                    for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                        String fn = f.getName().toLowerCase(Locale.US);
                        if (!(fn.contains("dr") || fn.contains("api") || fn.contains("permission") ||
                                fn.contains("check") || fn.contains("support") || fn.contains("location"))) continue;
                        f.setAccessible(true);
                        Object v = f.get(instance);
                        android.util.Log.w(TAG, "LIST_FIELD " + f.getName() + " class=" +
                                (v == null ? "null" : v.getClass().getName()) + " value=" + String.valueOf(v));

                        if (v instanceof java.util.List) {
                            java.util.List<?> list = (java.util.List<?>) v;
                            for (int i = 0; i < list.size(); i++) {
                                Object item = list.get(i);
                                if (item == null) {
                                    android.util.Log.w(TAG, f.getName() + "[" + i + "]=null");
                                    continue;
                                }
                                Class<?> ic = item.getClass();
                                android.util.Log.w(TAG, "ITEM " + f.getName() + "[" + i + "] class=" + ic.getName());
                                for (java.lang.reflect.Field x : ic.getDeclaredFields()) {
                                    try {
                                        x.setAccessible(true);
                                        Object xv = x.get(item);
                                        android.util.Log.w(TAG, "  FIELD " + x.getName() + ":" +
                                                x.getType().getSimpleName() + "=" + String.valueOf(xv));
                                    } catch (Throwable ignored) { }
                                }
                                for (java.lang.reflect.Method m : ic.getDeclaredMethods()) {
                                    if (m.getParameterTypes().length != 0) continue;
                                    String mn = m.getName().toLowerCase(Locale.US);
                                    if (!(mn.startsWith("get") || mn.startsWith("is"))) continue;
                                    Class<?> rt = m.getReturnType();
                                    if (!(rt.isPrimitive() || rt == String.class || Number.class.isAssignableFrom(rt))) continue;
                                    try {
                                        m.setAccessible(true);
                                        Object mv = m.invoke(item);
                                        android.util.Log.w(TAG, "  GET " + m.getName() + "()=" + String.valueOf(mv));
                                    } catch (Throwable ignored) { }
                                }
                            }
                        }
                    }
                }

                try {
                    Class<?> ac = Class.forName("com.gwm.navi.service.external.api.ApiContext", false, cl);
                    android.util.Log.i(TAG, "APICONTEXT " + ac.getName());
                    for (java.lang.reflect.Constructor<?> ctor : ac.getDeclaredConstructors()) {
                        android.util.Log.i(TAG, "APICONTEXT_CTOR " + ctor.toString());
                    }
                    for (java.lang.reflect.Field f : ac.getDeclaredFields()) {
                        try {
                            android.util.Log.i(TAG, "APICONTEXT_FIELD " + f.getName() + ":" + f.getType().getName());
                        } catch (Throwable ignored) { }
                    }
                    for (java.lang.reflect.Method m : ac.getDeclaredMethods()) {
                        String mn = m.getName().toLowerCase(Locale.US);
                        if (mn.contains("protocol") || mn.contains("package") || mn.contains("pkg") ||
                                mn.contains("type") || mn.contains("version") || mn.startsWith("get") || mn.startsWith("set")) {
                            android.util.Log.i(TAG, "APICONTEXT_METHOD " + methodSig(m));
                        }
                    }
                } catch (Throwable e) {
                    android.util.Log.e(TAG, "APICONTEXT_ERR " + rootCauseName(e));
                }

                android.util.Log.i(TAG, "END");
            } catch (Throwable e) {
                android.util.Log.e(TAG, "FATAL " + rootCauseName(e));
            }
        }, "H9-DR-Gate50").start();
    }

    private void probeDrGateV49() {
        new Thread(() -> {
            final String TAG = "H9QiblaGate49";
            dalvik.system.DexFile dex = null;
            try {
                Context navCtx = createPackageContext("com.gwm.navi.app",
                        Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
                ClassLoader cl = navCtx.getClassLoader();
                android.content.pm.ApplicationInfo ai = getPackageManager().getApplicationInfo("com.gwm.navi.app", 0);
                dex = new dalvik.system.DexFile(ai.sourceDir);

                java.util.Enumeration<String> en = dex.entries();
                int codeHits = 0;
                while (en.hasMoreElements()) {
                    String cn = en.nextElement();
                    if (!cn.startsWith("com.gwm.navi.service.external")) continue;
                    try {
                        Class<?> c = Class.forName(cn, false, cl);
                        for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                            if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                            Class<?> t = f.getType();
                            if (!(t == int.class || t == Integer.class || t == long.class || t == Long.class || t == String.class)) continue;
                            try {
                                f.setAccessible(true);
                                Object v = f.get(null);
                                String sv = String.valueOf(v);
                                if ("10000".equals(sv) || "10001".equals(sv) || "10002".equals(sv) ||
                                        "10003".equals(sv) || "10004".equals(sv) || "10005".equals(sv)) {
                                    android.util.Log.w(TAG, "STATUS_CONST " + cn + "#" + f.getName() + "=" + sv);
                                    codeHits++;
                                }
                            } catch (Throwable ignored) { }
                        }
                    } catch (Throwable ignored) { }
                }
                android.util.Log.i(TAG, "STATUS_CONST_HITS=" + codeHits);

                String[] targets = new String[]{
                        "com.gwm.navi.service.external.interceptor.AppModeInterceptor",
                        "com.gwm.navi.service.external.interceptor.PermissionInterceptor"
                };
                for (String cn : targets) {
                    try {
                        Class<?> c = Class.forName(cn, false, cl);
                        android.util.Log.i(TAG, "TARGET " + cn);

                        for (java.lang.reflect.Constructor<?> ctor : c.getDeclaredConstructors()) {
                            android.util.Log.i(TAG, "CTOR " + ctor.toString());
                        }
                        for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                            android.util.Log.i(TAG, "METHOD " + methodSig(m));
                        }

                        Object instance = null;
                        for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                            if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                            if (!c.isAssignableFrom(f.getType())) continue;
                            try {
                                f.setAccessible(true);
                                instance = f.get(null);
                                if (instance != null) {
                                    android.util.Log.w(TAG, "INSTANCE_FROM_FIELD " + f.getName());
                                    break;
                                }
                            } catch (Throwable ignored) { }
                        }

                        if (instance == null) {
                            for (java.lang.reflect.Constructor<?> ctor : c.getDeclaredConstructors()) {
                                try {
                                    Class<?>[] ps = ctor.getParameterTypes();
                                    Object[] args = new Object[ps.length];
                                    boolean ok = true;
                                    for (int i = 0; i < ps.length; i++) {
                                        Class<?> p = ps[i];
                                        if (p == Context.class || p.isAssignableFrom(navCtx.getClass())) args[i] = navCtx;
                                        else if (p == String.class) args[i] = getPackageName();
                                        else if (p == int.class || p == Integer.class) args[i] = 0;
                                        else if (p == boolean.class || p == Boolean.class) args[i] = false;
                                        else { ok = false; break; }
                                    }
                                    if (!ok) continue;
                                    ctor.setAccessible(true);
                                    instance = ctor.newInstance(args);
                                    android.util.Log.w(TAG, "INSTANCE_FROM_CTOR " + ctor.toString());
                                    break;
                                } catch (Throwable ignored) { }
                            }
                        }

                        if (instance != null) {
                            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                                String n = f.getName().toLowerCase(Locale.US);
                                if (!(n.contains("dr") || n.contains("api") || n.contains("permission") ||
                                        n.contains("check") || n.contains("support") || n.contains("location"))) continue;
                                try {
                                    f.setAccessible(true);
                                    Object v = f.get(instance);
                                    android.util.Log.w(TAG, "INSTANCE_FIELD " + cn + "#" + f.getName() + "=" + String.valueOf(v));
                                } catch (Throwable e) {
                                    android.util.Log.i(TAG, "INSTANCE_FIELD_ERR " + f.getName() + " " + rootCauseName(e));
                                }
                            }
                        } else {
                            android.util.Log.w(TAG, "NO_INSTANCE " + cn);
                        }
                    } catch (Throwable e) {
                        android.util.Log.e(TAG, "TARGET_ERR " + cn + " " + rootCauseName(e));
                    }
                }

                android.util.Log.i(TAG, "END");
            } catch (Throwable e) {
                android.util.Log.e(TAG, "FATAL " + rootCauseName(e));
            } finally {
                if (dex != null) try { dex.close(); } catch (Throwable ignored) { }
            }
        }, "H9-DR-Gate49").start();
    }

    private void probeDrAuthorizationV48() {
        new Thread(() -> {
            final String TAG = "H9QiblaAuth48";
            try {
                Context navCtx = createPackageContext("com.gwm.navi.app",
                        Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
                ClassLoader cl = navCtx.getClassLoader();

                String[] classes = new String[]{
                        "com.gwm.navi.service.external.ExternalServiceResult",
                        "com.gwm.navi.service.external.ApiConstV4",
                        "com.gwm.navi.service.external.api.ApiConst",
                        "com.gwm.navi.service.external.interceptor.PermissionInterceptor",
                        "com.gwm.navi.service.external.interceptor.AppModeInterceptor",
                        "com.gwm.navi.service.external.v4.NaviExternalMessageManagerV4"
                };
                for (String cn : classes) {
                    try {
                        Class<?> c = Class.forName(cn, false, cl);
                        android.util.Log.i(TAG, "CLASS " + cn);
                        for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                            int mod = f.getModifiers();
                            if (!java.lang.reflect.Modifier.isStatic(mod)) continue;
                            Class<?> t = f.getType();
                            if (!(t.isPrimitive() || t == String.class || Number.class.isAssignableFrom(t))) continue;
                            try {
                                f.setAccessible(true);
                                Object v = f.get(null);
                                android.util.Log.w(TAG, "STATIC " + cn + "#" + f.getName() + "=" + String.valueOf(v));
                            } catch (Throwable e) {
                                android.util.Log.i(TAG, "STATIC_ERR " + cn + "#" + f.getName() + " " + rootCauseName(e));
                            }
                        }
                    } catch (Throwable e) {
                        android.util.Log.e(TAG, "CLASS_ERR " + cn + " " + rootCauseName(e));
                    }
                }

                android.content.pm.PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(),
                        android.content.pm.PackageManager.GET_PERMISSIONS);
                if (pi.requestedPermissions != null) {
                    for (int i = 0; i < pi.requestedPermissions.length; i++) {
                        String p = pi.requestedPermissions[i];
                        int granted = getPackageManager().checkPermission(p, getPackageName());
                        android.util.Log.i(TAG, "PERM " + p + "=" +
                                (granted == android.content.pm.PackageManager.PERMISSION_GRANTED ? "GRANTED" : "DENIED"));
                    }
                }

                android.content.pm.PackageInfo navPi = getPackageManager().getPackageInfo("com.gwm.navi.app",
                        android.content.pm.PackageManager.GET_PROVIDERS);
                if (navPi.providers != null) {
                    for (android.content.pm.ProviderInfo p : navPi.providers) {
                        android.util.Log.i(TAG, "PROVIDER name=" + p.name + " authority=" + p.authority +
                                " exported=" + p.exported + " readPerm=" + p.readPermission + " writePerm=" + p.writePermission);
                    }
                }

                // Inspect instance field names that govern DR/location authorization.
                String[] inspect = new String[]{
                        "com.gwm.navi.service.external.interceptor.PermissionInterceptor",
                        "com.gwm.navi.service.external.interceptor.AppModeInterceptor"
                };
                for (String cn : inspect) {
                    try {
                        Class<?> c = Class.forName(cn, false, cl);
                        for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                            String n = f.getName().toLowerCase(Locale.US);
                            if (n.contains("location") || n.contains("permission") || n.contains("dr") ||
                                    n.contains("support") || n.contains("api")) {
                                android.util.Log.w(TAG, "FIELD " + cn + "#" + f.getName() +
                                        ":" + f.getType().getName());
                            }
                        }
                    } catch (Throwable ignored) { }
                }

                android.util.Log.i(TAG, "END");
            } catch (Throwable e) {
                android.util.Log.e(TAG, "FATAL " + rootCauseName(e));
            }
        }, "H9-DR-Auth48").start();
    }

    private void probeExactDrV47(IBinder service) {
        new Thread(() -> {
            final String TAG = "H9QiblaDR47";
            final String mapIface = "com.gwm.map.sdk.MapControl";
            final String cbIface = "com.gwm.map.sdk.MapCallback";
            final String pkg = getPackageName();
            final String ACTION_DR = "14021";
            final String ACTION_NOTIFY_DR = "14032";
            android.util.Log.i(TAG, "BEGIN ACTION_DR=" + ACTION_DR + " ACTION_NOTIFY_DR=" + ACTION_NOTIFY_DR);

            try {
                gwmMapCallbackBinder = new android.os.Binder() {
                    { attachInterface(null, cbIface); }

                    @Override
                    protected boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags)
                            throws android.os.RemoteException {
                        if (code == INTERFACE_TRANSACTION) {
                            if (reply != null) reply.writeString(cbIface);
                            return true;
                        }
                        try {
                            data.enforceInterface(cbIface);
                            if (code == 1) {
                                String response = data.readString();
                                android.util.Log.w(TAG, "CALLBACK_RESPONSE " + String.valueOf(response));
                                inspectDrPayload("callback_response", response);
                                if (reply != null) reply.writeNoException();
                                return true;
                            }
                            if (code == 2) {
                                String key = data.readString();
                                android.os.Bundle bundle = null;
                                if (data.readInt() != 0) bundle = android.os.Bundle.CREATOR.createFromParcel(data);
                                String payload = bundle == null ? "null" : bundle.toString();
                                android.util.Log.w(TAG, "CALLBACK_DATA key=" + key + " bundle=" + payload);
                                inspectDrPayload(key, payload);
                                if (reply != null) reply.writeNoException();
                                return true;
                            }
                            android.util.Log.i(TAG, "CALLBACK_TX code=" + code + " flags=" + flags + " avail=" + data.dataAvail());
                        } catch (Throwable e) {
                            android.util.Log.e(TAG, "CALLBACK_ERROR code=" + code + " " + rootCauseName(e));
                        }
                        return super.onTransact(code, data, reply, flags);
                    }
                };

                Parcel rd = Parcel.obtain();
                Parcel rr = Parcel.obtain();
                try {
                    rd.writeInterfaceToken(mapIface);
                    rd.writeString(pkg);
                    rd.writeStrongBinder(gwmMapCallbackBinder);
                    boolean ok = service.transact(1, rd, rr, 0);
                    rr.readException();
                    android.util.Log.i(TAG, "REGISTER_CALLBACK=" + ok);
                } finally { rd.recycle(); rr.recycle(); }

                Parcel sd = Parcel.obtain();
                Parcel sr = Parcel.obtain();
                try {
                    sd.writeInterfaceToken(mapIface);
                    sd.writeString(pkg);
                    sd.writeStringArray(new String[]{ACTION_NOTIFY_DR, ACTION_DR});
                    boolean ok = service.transact(5, sd, sr, 0);
                    sr.readException();
                    android.util.Log.i(TAG, "SUBSCRIBE_DR=" + ok + " ids=[" + ACTION_NOTIFY_DR + "," + ACTION_DR + "]");
                } finally { sd.recycle(); sr.recycle(); }

                for (String protocolId : new String[]{ACTION_DR, ACTION_NOTIFY_DR}) {
                    Parcel d = Parcel.obtain();
                    Parcel r = Parcel.obtain();
                    try {
                        org.json.JSONObject req = new org.json.JSONObject();
                        req.put("pkgName", pkg);
                        req.put("protocolId", protocolId);
                        req.put("reqId", String.valueOf(System.currentTimeMillis()));
                        req.put("verName", "1.0");
                        req.put("data", new org.json.JSONObject());
                        d.writeInterfaceToken(mapIface);
                        d.writeString(req.toString());
                        boolean sent = service.transact(4, d, r, 0);
                        r.readException();
                        String response = r.readString();
                        android.util.Log.w(TAG, "SYNC_DR protocol=" + protocolId + " sent=" + sent + " response=" + String.valueOf(response));
                        inspectDrPayload(protocolId, response);
                    } catch (Throwable e) {
                        android.util.Log.e(TAG, "SYNC_DR protocol=" + protocolId + " ERROR=" + rootCauseName(e));
                    } finally { d.recycle(); r.recycle(); }
                }

                // Direct Neusoft binder discovery. The installed navigation APK exposes:
                // ILocationAidlInterface.setDRListener transaction 5 and listener callback tx1.
                try {
                    Class<?> sm = Class.forName("android.os.ServiceManager");
                    java.lang.reflect.Method list = sm.getDeclaredMethod("listServices");
                    java.lang.reflect.Method get = sm.getDeclaredMethod("getService", String.class);
                    list.setAccessible(true); get.setAccessible(true);
                    String[] services = (String[]) list.invoke(null);
                    android.util.Log.i(TAG, "SERVICE_COUNT=" + (services == null ? -1 : services.length));
                    if (services != null) {
                        for (String sn : services) {
                            IBinder b = null;
                            try { b = (IBinder) get.invoke(null, sn); } catch (Throwable ignored) { }
                            if (b == null) continue;
                            String desc = "";
                            try { desc = b.getInterfaceDescriptor(); } catch (Throwable ignored) { }
                            if (desc != null && desc.toLowerCase(Locale.US).contains("location")) {
                                android.util.Log.i(TAG, "SERVICE name=" + sn + " desc=" + desc);
                            }
                            if (!"com.neusoft.nmasdk.ILocationAidlInterface".equals(desc)) continue;

                            android.util.Log.w(TAG, "FOUND_NEUSOFT_LOCATION_SERVICE name=" + sn);
                            final String listenerDesc = "com.neusoft.nmasdk.ILocationAidlListener";
                            android.os.Binder listener = new android.os.Binder() {
                                { attachInterface(null, listenerDesc); }
                                @Override
                                protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                                        throws android.os.RemoteException {
                                    if (code == INTERFACE_TRANSACTION) {
                                        if (reply != null) reply.writeString(listenerDesc);
                                        return true;
                                    }
                                    try {
                                        data.enforceInterface(listenerDesc);
                                        if (code == 1) {
                                            double a = data.readDouble();
                                            double b = data.readDouble();
                                            double c = data.readDouble();
                                            double d = data.readDouble();
                                            double e = data.readDouble();
                                            double f = data.readDouble();
                                            android.util.Log.w(TAG, String.format(Locale.US,
                                                    "NEUSOFT_DR a=%.6f b=%.6f c=%.6f d=%.6f e=%.6f f=%.6f",
                                                    a,b,c,d,e,f));
                                            if (reply != null) reply.writeNoException();
                                            return true;
                                        }
                                    } catch (Throwable x) {
                                        android.util.Log.e(TAG, "NEUSOFT_CALLBACK_ERROR " + rootCauseName(x));
                                    }
                                    return super.onTransact(code, data, reply, flags);
                                }
                            };

                            Parcel ld = Parcel.obtain();
                            Parcel lr = Parcel.obtain();
                            try {
                                ld.writeInterfaceToken(desc);
                                ld.writeStrongBinder(listener);
                                boolean ok = b.transact(5, ld, lr, 0);
                                lr.readException();
                                android.util.Log.w(TAG, "SET_DR_LISTENER tx5=" + ok);
                            } catch (Throwable e) {
                                android.util.Log.e(TAG, "SET_DR_LISTENER_ERROR " + rootCauseName(e));
                            } finally { ld.recycle(); lr.recycle(); }
                        }
                    }
                } catch (Throwable e) {
                    android.util.Log.e(TAG, "SERVICE_DISCOVERY_ERROR " + rootCauseName(e));
                }

                android.util.Log.i(TAG, "READY waiting live DR");
            } catch (Throwable e) {
                android.util.Log.e(TAG, "FATAL " + rootCauseName(e));
            }
        }, "H9-Exact-DR47").start();
    }

    private void probeInstalledGwmNaviProtocol() {
        new Thread(() -> {
            final String TAG = "H9QiblaProto";
            android.util.Log.i(TAG, "BEGIN runtime scan com.gwm.navi.app");
            dalvik.system.DexFile dex = null;
            try {
                android.content.pm.ApplicationInfo ai = getPackageManager().getApplicationInfo("com.gwm.navi.app", 0);
                android.util.Log.i(TAG, "sourceDir=" + ai.sourceDir);
                Context navCtx = createPackageContext("com.gwm.navi.app",
                        Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
                ClassLoader cl = navCtx.getClassLoader();

                dex = new dalvik.system.DexFile(ai.sourceDir);
                java.util.Enumeration<String> entries = dex.entries();
                int classCount = 0;
                int interestingClasses = 0;
                int emittedMethods = 0;
                while (entries.hasMoreElements()) {
                    String cn = entries.nextElement();
                    classCount++;
                    String lcn = cn.toLowerCase(Locale.US);
                    boolean classHit = lcn.contains("heading") || lcn.contains("deadreck") ||
                            lcn.contains(".dr") || lcn.contains("dr.") || lcn.contains("location") ||
                            lcn.contains("position") || lcn.contains("mapcontrol") ||
                            lcn.contains("mapcallback") || lcn.contains("navi.service");
                    if (!classHit) continue;

                    Class<?> c;
                    try {
                        c = Class.forName(cn, false, cl);
                    } catch (Throwable loadErr) {
                        continue;
                    }

                    java.util.ArrayList<String> interesting = new java.util.ArrayList<>();
                    try {
                        for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                            String mn = m.getName().toLowerCase(Locale.US);
                            if (mn.contains("heading") || mn.contains("bearing") || mn.contains("azimuth") ||
                                    mn.contains("yaw") || mn.contains("course") || mn.contains("direction") ||
                                    mn.contains("deadreck") || mn.equals("getdrlocinfo") ||
                                    mn.contains("drheading") || mn.contains("drinfo") ||
                                    mn.contains("locationinfo") || mn.contains("subscribe")) {
                                interesting.add(methodSig(m));
                            }
                        }
                    } catch (Throwable ignored) { }

                    java.util.ArrayList<String> fields = new java.util.ArrayList<>();
                    try {
                        for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                            String fn = f.getName().toLowerCase(Locale.US);
                            boolean nameHit = fn.contains("heading") || fn.contains("bearing") ||
                                    fn.contains("azimuth") || fn.contains("yaw") || fn.contains("course") ||
                                    fn.contains("direction") || fn.contains("deadreck") || fn.contains("dr") ||
                                    fn.contains("protocol") || fn.contains("location");
                            if (!nameHit) continue;
                            String value = "";
                            if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) &&
                                    (f.getType().isPrimitive() || f.getType() == String.class ||
                                     Number.class.isAssignableFrom(f.getType()))) {
                                try {
                                    f.setAccessible(true);
                                    Object fv = f.get(null);
                                    value = "=" + String.valueOf(fv);
                                } catch (Throwable ignored) { }
                            }
                            fields.add(f.getName() + ":" + f.getType().getSimpleName() + value);
                        }
                    } catch (Throwable ignored) { }

                    if (!interesting.isEmpty() || !fields.isEmpty()) {
                        interestingClasses++;
                        android.util.Log.w(TAG, "CLASS " + cn);
                        for (String s : interesting) {
                            android.util.Log.w(TAG, "METHOD " + cn + "#" + s);
                            emittedMethods++;
                            if (emittedMethods >= 220) break;
                        }
                        for (String s : fields) {
                            android.util.Log.w(TAG, "FIELD " + cn + "#" + s);
                        }
                        if (emittedMethods >= 220) {
                            android.util.Log.w(TAG, "METHOD_LIMIT reached");
                            break;
                        }
                    }
                }

                android.util.Log.i(TAG, "END classes=" + classCount + " interestingClasses=" +
                        interestingClasses + " methods=" + emittedMethods);

                // Known anchor from prior GWM navigation analysis: try exact symbolic lookup
                // so the declaring class is surfaced immediately if present.
                dex = dex == null ? new dalvik.system.DexFile(ai.sourceDir) : dex;
                java.util.Enumeration<String> e2 = dex.entries();
                while (e2.hasMoreElements()) {
                    String cn = e2.nextElement();
                    try {
                        Class<?> c = Class.forName(cn, false, cl);
                        for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                            String n = m.getName();
                            if ("setDrHeading".equals(n) || "getDrHeading".equals(n) ||
                                    "getDrLocInfo".equals(n)) {
                                android.util.Log.e(TAG, "ANCHOR " + cn + "#" + methodSig(m));
                            }
                        }
                    } catch (Throwable ignored) { }
                }
            } catch (Throwable e) {
                android.util.Log.e(TAG, "ERROR " + rootCauseName(e));
            } finally {
                if (dex != null) {
                    try { dex.close(); } catch (Throwable ignored) { }
                }
            }
        }, "H9-Navi-Protocol46").start();
    }

    private void probeGwmBinderDrCallback(IBinder service) {
        new Thread(() -> {
            final String mapIface = "com.gwm.map.sdk.MapControl";
            final String cbIface = "com.gwm.map.sdk.MapCallback";
            final String pkg = getPackageName();
            final String[] protocolIds = new String[]{
                    "10011000","10022000","10031000","10042000","10051000",
                    "10062000","10071000","10082000","10091000"
            };
            android.util.Log.i("H9QiblaBinderDR", "BEGIN descriptor probe");

            try {
                gwmMapCallbackBinder = new android.os.Binder() {
                    { attachInterface(null, cbIface); }

                    @Override
                    protected boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags)
                            throws android.os.RemoteException {
                        if (code == INTERFACE_TRANSACTION) {
                            if (reply != null) reply.writeString(cbIface);
                            return true;
                        }
                        try {
                            data.enforceInterface(cbIface);
                            if (code == 1) {
                                String response = data.readString();
                                android.util.Log.w("H9QiblaBinderDR", "CALLBACK_RESPONSE " + String.valueOf(response));
                                inspectDrPayload("response", response);
                                if (reply != null) reply.writeNoException();
                                return true;
                            }
                            if (code == 2) {
                                String key = data.readString();
                                android.os.Bundle bundle = null;
                                if (data.readInt() != 0) bundle = android.os.Bundle.CREATOR.createFromParcel(data);
                                String payload = bundle == null ? "null" : bundle.toString();
                                android.util.Log.w("H9QiblaBinderDR", "CALLBACK_DATA key=" + key + " bundle=" + payload);
                                inspectDrPayload(key, payload);
                                if (reply != null) reply.writeNoException();
                                return true;
                            }
                            android.util.Log.i("H9QiblaBinderDR", "CALLBACK_TX code=" + code + " flags=" + flags + " size=" + data.dataAvail());
                        } catch (Throwable e) {
                            android.util.Log.e("H9QiblaBinderDR", "CALLBACK_ERROR code=" + code + " " + rootCauseName(e));
                        }
                        return super.onTransact(code, data, reply, flags);
                    }
                };

                Parcel regData = Parcel.obtain();
                Parcel regReply = Parcel.obtain();
                try {
                    regData.writeInterfaceToken(mapIface);
                    regData.writeString(pkg);
                    regData.writeStrongBinder(gwmMapCallbackBinder);
                    boolean sent = service.transact(1, regData, regReply, 0);
                    regReply.readException();
                    android.util.Log.i("H9QiblaBinderDR", "REGISTER_CALLBACK sent=" + sent);
                } finally {
                    regData.recycle();
                    regReply.recycle();
                }

                Parcel subData = Parcel.obtain();
                Parcel subReply = Parcel.obtain();
                try {
                    subData.writeInterfaceToken(mapIface);
                    subData.writeString(pkg);
                    subData.writeStringArray(protocolIds);
                    boolean sent = service.transact(5, subData, subReply, 0);
                    subReply.readException();
                    android.util.Log.i("H9QiblaBinderDR", "SUBSCRIBE sent=" + sent + " ids=" + java.util.Arrays.toString(protocolIds));
                } finally {
                    subData.recycle();
                    subReply.recycle();
                }

                // Exact OEM read-only location / dead-reckoning requests discovered earlier.
                final int[] actions = new int[]{14001, 14002};
                final String[] actionNames = new String[]{"GET_LOCATION_INFO", "GET_DR_INFO"};
                final String version = "v1.1.10-SNAPSHOT_t20250217";
                for (int i = 0; i < actions.length; i++) {
                    Parcel d = Parcel.obtain();
                    Parcel r = Parcel.obtain();
                    try {
                        org.json.JSONObject req = new org.json.JSONObject();
                        req.put("actionType", actions[i]);
                        req.put("version", version);
                        d.writeInterfaceToken(mapIface);
                        d.writeString(req.toString());
                        boolean sent = service.transact(4, d, r, 0);
                        r.readException();
                        String response = r.readString();
                        android.util.Log.w("H9QiblaBinderDR", actionNames[i] + " action=" + actions[i] + " sent=" + sent + " response=" + String.valueOf(response));
                        inspectDrPayload(actionNames[i], response);
                    } catch (Throwable e) {
                        android.util.Log.e("H9QiblaBinderDR", actionNames[i] + " ERROR=" + rootCauseName(e));
                    } finally {
                        d.recycle();
                        r.recycle();
                    }
                }

                // Also request the known V4 envelopes once so a callback can reveal any live
                // navigation payloads exposed to this package.
                for (String protocolId : protocolIds) {
                    Parcel d = Parcel.obtain();
                    Parcel r = Parcel.obtain();
                    try {
                        org.json.JSONObject req = new org.json.JSONObject();
                        req.put("pkgName", pkg);
                        req.put("protocolId", protocolId);
                        req.put("reqId", String.valueOf(System.currentTimeMillis()));
                        req.put("verName", "1.0");
                        req.put("data", new org.json.JSONObject());
                        d.writeInterfaceToken(mapIface);
                        d.writeString(req.toString());
                        boolean sent = service.transact(4, d, r, 0);
                        r.readException();
                        String response = r.readString();
                        android.util.Log.i("H9QiblaBinderDR", "SYNC protocol=" + protocolId + " sent=" + sent + " response=" + String.valueOf(response));
                        inspectDrPayload(protocolId, response);
                    } catch (Throwable e) {
                        android.util.Log.w("H9QiblaBinderDR", "SYNC protocol=" + protocolId + " ERROR=" + rootCauseName(e));
                    } finally {
                        d.recycle();
                        r.recycle();
                    }
                }

                android.util.Log.i("H9QiblaBinderDR", "READY waiting for callbacks");
            } catch (Throwable e) {
                android.util.Log.e("H9QiblaBinderDR", "FATAL " + rootCauseName(e));
                runOnUiThread(() -> updateBinderDiagnosticText("Binder DR ERROR: " + rootCauseName(e)));
            }
        }, "H9-Binder-DR45").start();
    }

    private void inspectDrPayload(String source, String payload) {
        if (payload == null) return;
        String low = payload.toLowerCase(Locale.US);
        if (low.contains("heading") || low.contains("bearing") || low.contains("azimuth") ||
                low.contains("yaw") || low.contains("direction") || low.contains("course") ||
                low.contains("dr") || low.contains("angle") || low.contains("speed")) {
            android.util.Log.w("H9QiblaBinderDR", "CANDIDATE source=" + source + " payload=" + payload);
        }
        try {
            org.json.JSONObject obj = new org.json.JSONObject(payload);
            String[] keys = new String[]{"heading","bearing","azimuth","yaw","direction","course","angle"};
            for (String k : keys) {
                if (!obj.has(k)) continue;
                Object v = obj.opt(k);
                if (v instanceof Number) {
                    float h = normalize(((Number) v).floatValue());
                    android.util.Log.w("H9QiblaBinderDR", "SUCCESS_HEADING key=" + k + " value=" + h + " source=" + source);
                    runOnUiThread(() -> qiblaView.setHeading(h, "اتجاه السيارة من نظام هافال"));
                    break;
                }
            }
        } catch (Throwable ignored) { }
    }

    private void probeGwmEngineerAdapter() {
        synchronized (oemLocLog) {
            oemLocLog.setLength(0);
            oemLocLog.append("FINAL PROPERTY SCAN • GwmCarSettingManager 4.4\\n");
        }
        new Thread(() -> {
            try {
                Context engCtx = createPackageContext("com.gwm.naviengineer",
                        Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
                ClassLoader cl = engCtx.getClassLoader();
                Class<?> carClass = Class.forName("android.car.Car", false, cl);

                android.content.ServiceConnection safeConn = new android.content.ServiceConnection() {
                    @Override public void onServiceConnected(android.content.ComponentName name, android.os.IBinder service) {
                        appendOemLoc("CONNECTED");
                    }
                    @Override public void onServiceDisconnected(android.content.ComponentName name) {
                        appendOemLoc("DISCONNECTED");
                    }
                };

                Object car = null;
                for (java.lang.reflect.Method mm : carClass.getMethods()) {
                    if (!"createCar".equals(mm.getName()) || !java.lang.reflect.Modifier.isStatic(mm.getModifiers())) continue;
                    Class<?>[] ps = mm.getParameterTypes();
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
                        mm.setAccessible(true);
                        Object v = mm.invoke(null, args);
                        if (v != null) { car = v; break; }
                    } catch (Throwable ignored) { }
                }

                if (car == null) { appendOemLoc("RESULT Car=NULL"); return; }
                try { carClass.getMethod("connect").invoke(car); } catch (Throwable ignored) { }

                java.lang.reflect.Method isConnected = carClass.getMethod("isConnected");
                long deadline = System.currentTimeMillis() + 2500L;
                while (System.currentTimeMillis() < deadline) {
                    try { if (Boolean.TRUE.equals(isConnected.invoke(car))) break; } catch (Throwable ignored) { }
                    try { Thread.sleep(100); } catch (InterruptedException ignored) { }
                }

                Object manager = carClass.getMethod("getCarManager", String.class).invoke(car, "gwm_car_setting");
                appendOemLoc("manager=" + (manager == null ? "NULL" : manager.getClass().getName()));
                if (manager == null) return;

                Class<?> mc = manager.getClass();

                // 4.4 focused probe: resolve the confirmed Haval vehicle-speed FID with exact
                // reader signatures and common area IDs. Every attempt is logged with its
                // real exception instead of being swallowed.
                final int VEHSPD_FID = 560010980;
                int[] speedAreas = new int[]{0, 1, 2, 4, 8, 16, 32, 64, 16777216};
                android.util.Log.i("H9QiblaSpeed", "BEGIN FID=" + VEHSPD_FID + " NAME=ID_GWM_VEHSPD");
                for (java.lang.reflect.Method sm : mc.getMethods()) {
                    String sn = sm.getName();
                    if (!("getIntProperty".equals(sn) || "getFloatProperty".equals(sn) || "isPropertyAvailable".equals(sn))) continue;
                    Class<?>[] sps = sm.getParameterTypes();
                    if (sps.length < 1 || !(sps[0] == int.class || sps[0] == Integer.class)) continue;
                    android.util.Log.i("H9QiblaSpeed", "SIG " + methodSig(sm));
                    for (int area : speedAreas) {
                        try {
                            Object[] args = new Object[sps.length];
                            boolean ok = true;
                            for (int ai = 0; ai < sps.length; ai++) {
                                Class<?> p = sps[ai];
                                if (p == int.class || p == Integer.class) args[ai] = (ai == 0) ? VEHSPD_FID : area;
                                else if (p == long.class || p == Long.class) args[ai] = 0L;
                                else if (p == float.class || p == Float.class) args[ai] = 0f;
                                else if (p == double.class || p == Double.class) args[ai] = 0d;
                                else if (p == boolean.class || p == Boolean.class) args[ai] = false;
                                else { ok = false; break; }
                            }
                            if (!ok) continue;
                            sm.setAccessible(true);
                            Object sv = sm.invoke(manager, args);
                            android.util.Log.i("H9QiblaSpeed", "READ " + sn + " area=" + area + " => " + String.valueOf(sv));
                            if (sv instanceof Number && !("isPropertyAvailable".equals(sn))) {
                                float raw = ((Number) sv).floatValue();
                                if (!Float.isNaN(raw)) {
                                    // Keep the raw value visible for calibration; if it already looks like km/h,
                                    // preserve it. If OEM returns m/s the subsequent drive sample will reveal it.
                                    final float liveSpeed = raw;
                                    lastSpeedMps = raw;
                                    android.util.Log.w("H9QiblaSpeed", "SUCCESS RAW_SPEED=" + liveSpeed + " area=" + area + " via=" + sn);
                                }
                            }
                        } catch (Throwable se) {
                            android.util.Log.w("H9QiblaSpeed", "ERR " + sn + " area=" + area + " => " + rootCauseName(se));
                        }
                    }
                }
                android.util.Log.i("H9QiblaSpeed", "END");

                // 4.4 focused DR path. Prior broad probes are no longer repeated; this calls the
                // exact navigation factory accessor and getDrLocInfo() path.
                probeDirectGetLocationService();

                java.lang.reflect.Method getList = null;
                java.util.ArrayList<java.lang.reflect.Method> intReaders = new java.util.ArrayList<>();
                java.util.ArrayList<java.lang.reflect.Method> floatReaders = new java.util.ArrayList<>();
                java.util.ArrayList<java.lang.reflect.Method> boolReaders = new java.util.ArrayList<>();
                java.util.ArrayList<java.lang.reflect.Method> availReaders = new java.util.ArrayList<>();

                for (java.lang.reflect.Method mm : mc.getMethods()) {
                    String n = mm.getName();
                    if ("getPropertyList".equals(n) && mm.getParameterTypes().length == 0) getList = mm;
                    else if ("getIntProperty".equals(n)) intReaders.add(mm);
                    else if ("getFloatProperty".equals(n)) floatReaders.add(mm);
                    else if ("getBooleanProperty".equals(n)) boolReaders.add(mm);
                    else if ("isPropertyAvailable".equals(n)) availReaders.add(mm);
                }

                appendOemLoc("SIG int=" + intReaders.size() + " float=" + floatReaders.size() +
                        " bool=" + boolReaders.size() + " avail=" + availReaders.size());

                for (java.lang.reflect.Method mm : intReaders) appendOemLoc("I " + methodSig(mm));
                for (java.lang.reflect.Method mm : floatReaders) appendOemLoc("F " + methodSig(mm));
                for (java.lang.reflect.Method mm : boolReaders) appendOemLoc("B " + methodSig(mm));

                if (getList == null) {
                    appendOemLoc("RESULT getPropertyList MISSING");
                    return;
                }

                java.util.HashMap<Integer,String> idNames = new java.util.HashMap<>();
                for (java.lang.reflect.Field f : mc.getFields()) {
                    try {
                        if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                        if (!(f.getType() == int.class || f.getType() == Integer.class)) continue;
                        f.setAccessible(true);
                        Object v = f.get(null);
                        if (!(v instanceof Integer)) continue;
                        int id = (Integer)v;
                        String old = idNames.get(id);
                        idNames.put(id, old == null ? f.getName() : old + "|" + f.getName());
                    } catch (Throwable ignored) { }
                }

                Object listObj = getList.invoke(manager);
                if (!(listObj instanceof java.util.List)) {
                    appendOemLoc("RESULT getPropertyList=" + String.valueOf(listObj));
                    return;
                }

                java.util.List<?> props = (java.util.List<?>) listObj;
                appendOemLoc("PROPERTY_LIST size=" + props.size());

                android.util.Log.i("H9QiblaProperty", "PROPERTY_LIST_BEGIN size=" + props.size());
                int shown = 0;
                int scanned = 0;
                for (Object item : props) {
                    Integer id = null;
                    if (item instanceof Number) {
                        id = ((Number)item).intValue();
                    } else if (item != null) {
                        String[] idMethods = new String[]{"getPropertyId","getId","getPropId","getProperty"};
                        for (String mn : idMethods) {
                            try {
                                java.lang.reflect.Method im = item.getClass().getMethod(mn);
                                Object iv = im.invoke(item);
                                if (iv instanceof Number) { id = ((Number)iv).intValue(); break; }
                            } catch (Throwable ignored) { }
                        }
                    }

                    if (id == null) {
                        String line = "P[" + scanned + "] object=" + String.valueOf(item);
                        android.util.Log.i("H9QiblaProperty", line);
                        if (shown < 18) { appendOemLoc(line); shown++; }
                        scanned++;
                        continue;
                    }

                    String name = idNames.get(id);
                    if (name == null) name = "?";

                    String aVal = invokeAdaptiveReaders(manager, availReaders, id);
                    String iVal = invokeAdaptiveReaders(manager, intReaders, id);
                    String fVal = invokeAdaptiveReaders(manager, floatReaders, id);
                    String bVal = invokeAdaptiveReaders(manager, boolReaders, id);

                    String line = "P[" + scanned + "] FID=" + id + " NAME=" + name + " A=" + aVal + " I=" + iVal + " F=" + fVal + " B=" + bVal;
                    android.util.Log.i("H9QiblaProperty", line);
                    String lowName = name.toLowerCase(Locale.US);
                    if (lowName.contains("speed") || lowName.contains("gear") || lowName.contains("heading") ||
                            lowName.contains("yaw") || lowName.contains("angle") || lowName.contains("direction") ||
                            lowName.contains("steer")) {
                        android.util.Log.w("H9QiblaProperty", "CANDIDATE " + line);
                    }
                    if (shown < 18) { appendOemLoc(line); shown++; }
                    scanned++;
                }

                android.util.Log.i("H9QiblaProperty", "PROPERTY_LIST_END scanned=" + scanned + " total=" + props.size());
                appendOemLoc("LOGCAT READY • scanned=" + scanned + " total=" + props.size() + " • tag=H9QiblaProperty");
            } catch (Throwable e) {
                appendOemLoc("5.4 ERROR=" + e.getClass().getSimpleName() + ":" + shortMessage(e));
            }
        }, "H9-FinalPropertyScan44").start();
    }

    private String methodSig(java.lang.reflect.Method mm) {
        StringBuilder sb = new StringBuilder(mm.getName()).append('(');
        Class<?>[] ps = mm.getParameterTypes();
        for (int i=0;i<ps.length;i++) {
            if (i>0) sb.append(',');
            sb.append(ps[i].getSimpleName());
        }
        return sb.append(")->").append(mm.getReturnType().getSimpleName()).toString();
    }

    private String invokeAdaptiveReaders(Object target, java.util.List<java.lang.reflect.Method> methods, int propertyId) {
        for (java.lang.reflect.Method mm : methods) {
            try {
                Class<?>[] ps = mm.getParameterTypes();
                Object[] args = new Object[ps.length];
                boolean supported = true;
                for (int i=0;i<ps.length;i++) {
                    Class<?> p = ps[i];
                    if (p == int.class || p == Integer.class) {
                        args[i] = (i == 0) ? propertyId : 0;
                    } else if (p == boolean.class || p == Boolean.class) {
                        args[i] = false;
                    } else if (p == long.class || p == Long.class) {
                        args[i] = 0L;
                    } else if (p == float.class || p == Float.class) {
                        args[i] = 0f;
                    } else if (p == double.class || p == Double.class) {
                        args[i] = 0d;
                    } else {
                        supported = false;
                        break;
                    }
                }
                if (!supported) continue;
                mm.setAccessible(true);
                Object v = mm.invoke(target, args);
                return String.valueOf(v);
            } catch (Throwable ignored) { }
        }
        return "-";
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
        android.util.Log.i("H9QiblaDR", "BEGIN direct getLocationService -> getDrLocInfo");
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
                android.util.Log.i("H9QiblaDR", "getLocationService=" + (service == null ? "NULL" : service.getClass().getName()));
                appendOemLoc("getLocationService=" + (service == null ? "NULL" : service.getClass().getName()));
                if (service == null) {
                    android.util.Log.w("H9QiblaDR", "RESULT NULL service instance");
                    appendOemLoc("RESULT: NULL service instance");
                    return;
                }

                appendInterestingMethods(service.getClass(), "service");

                try {
                    java.lang.reflect.Method drMethod = service.getClass().getMethod("getDrLocInfo");
                    drMethod.setAccessible(true);
                    Object dr = drMethod.invoke(service);
                    android.util.Log.i("H9QiblaDR", "getDrLocInfo=" + String.valueOf(dr));
                    appendOemLoc("getDrLocInfo=" + String.valueOf(dr));
                    if (dr != null) {
                        appendOemLoc("DrLocInfo class=" + dr.getClass().getName());
                        Double heading = extractHeading(dr);
                        if (heading != null && !heading.isNaN()) {
                            final float h = normalize(heading.floatValue());
                            android.util.Log.w("H9QiblaDR", "SUCCESS DR_HEADING=" + String.format(Locale.US, "%.2f", h));
                            appendOemLoc("SUCCESS DR_HEADING=" + String.format(Locale.US, "%.2f°", h));
                            lastOemHeadingAt = System.currentTimeMillis();
                            lastHeadingAt = lastOemHeadingAt;
                            runOnUiThread(() -> qiblaView.setHeading(h, "اتجاه السيارة من نظام هافال"));
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
                        android.util.Log.w("H9QiblaDR", "getDrLocInfo returned NULL");
                        appendOemLoc("RESULT: getDrLocInfo returned NULL");
                    }
                } catch (Throwable e) {
                    appendOemLoc("getDrLocInfo ERROR=" + rootCauseName(e));
                    invokeDrInfo(service, "directService");
                }
            } catch (Throwable e) {
                android.util.Log.e("H9QiblaDR", "ERROR " + rootCauseName(e));
                appendOemLoc("DIRECT ERROR=" + e.getClass().getSimpleName() + ":" + shortMessage(e));
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
                        lastOemHeadingAt = System.currentTimeMillis();
                        lastHeadingAt = lastOemHeadingAt;
                        runOnUiThread(() -> qiblaView.setHeading(h, "اتجاه السيارة من نظام هافال"));
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
        if (binderDiagnosticText != null) binderDiagnosticText.setText("" + text);
    }

    private void updateMiddlewareDiagnosticText(String text) {
        if (middlewareDiagnosticText != null) middlewareDiagnosticText.setText("" + text);
    }

    private void updateLocationDiagnosticText(String text) {
        if (locationDiagnosticText != null) locationDiagnosticText.setText("" + text);
    }

    private void updateLiveCourse(Location location) {
        if (System.currentTimeMillis() - lastOemHeadingAt < 2500L) {
            if (location.hasSpeed()) lastSpeedMps = location.getSpeed();
            return;
        }
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
            saveHeading(newHeading, "آخر اتجاه GPS محفوظ");
            qiblaView.setHeading(newHeading, "اتجاه حي عبر GPS");
        }
    }

    private void maybeRefreshPrayerTimes(Location location) {
        long now = System.currentTimeMillis();
        boolean movedFar = lastPrayerLocation == null || lastPrayerLocation.distanceTo(location) > 3000f;
        if (!movedFar && now - lastPrayerCalcAt < PRAYER_REFRESH_MS) return;

        lastPrayerCalcAt = now;
        lastPrayerLocation = new Location(location);
        qiblaView.setPrayerStatus("حساب مواقيت الصلاة محليًا…");

        Calendar calendar = Calendar.getInstance();
        PrayerTimesCalculator.Result result = PrayerTimesCalculator.calculate(
                location.getLatitude(), location.getLongitude(), calendar);
        String[] names = {"الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"};
        qiblaView.setPrayerTimes(names, result.times24, result.hijriDate, result.hijriDateEn);
        qiblaView.setPrayerStatus("أم القرى • حساب محلي بدون إنترنت");
    }

    private void maybeResolveLocationName(Location location) {
        final double lat = location.getLatitude();
        final double lon = location.getLongitude();

        // Province is available offline from coordinates and must not wait for Geocoder/network.
        String offlineRegion = regionFromSaudiCoordinates(lat, lon);
        if (offlineRegion != null && !offlineRegion.isEmpty()) qiblaView.setRegionName(offlineRegion);

        long now = System.currentTimeMillis();
        boolean movedEnough = lastPlaceResolveLocation == null || location.distanceTo(lastPlaceResolveLocation) >= 1500f;
        if (!movedEnough && now - lastPlaceResolveAt < 10L * 60L * 1000L) return;
        lastPlaceResolveAt = now;
        lastPlaceResolveLocation = new Location(location);
        new Thread(() -> resolveLocationName(lat, lon), "H9-Place-Resolve").start();
    }

    private void resolveLocationName(double lat, double lon) {
        String city = municipalityFromCoordinates(lat, lon);
        String region = regionFromSaudiCoordinates(lat, lon);
        if (city == null && region != null) city = region;
        try {
            if (Geocoder.isPresent()) {
                Geocoder geocoder = new Geocoder(this, isEn() ? Locale.US : new Locale("ar"));
                List<android.location.Address> results = geocoder.getFromLocation(lat, lon, 1);
                if (results != null && !results.isEmpty()) {
                    android.location.Address a = results.get(0);
                    String geocoderCity = firstNonEmpty(a.getLocality(), a.getSubLocality(), a.getSubAdminArea());
                    if (geocoderCity != null && !geocoderCity.trim().isEmpty()) city = geocoderCity.trim();
                    String geocoderRegion = normalizeSaudiRegionName(a.getAdminArea());
                    if (geocoderRegion == null) geocoderRegion = extractSaudiRegionFromAddress(a);
                    if (geocoderRegion != null) region = geocoderRegion;
                }
            }
        } catch (Exception ignored) { }

        final String finalCity = city == null || city.trim().isEmpty() ? "الموقع الحالي" : city.trim();
        runOnUiThread(() -> {
            qiblaView.setLocationName(finalCity);
            qiblaView.setRegionName("");
        });
    }

    private String firstNonEmpty(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) return v.trim();
        }
        return null;
    }

    private String extractSaudiRegionFromAddress(android.location.Address a) {
        StringBuilder all = new StringBuilder();
        for (int i = 0; i <= a.getMaxAddressLineIndex(); i++) {
            String line = a.getAddressLine(i);
            if (line != null) all.append(' ').append(line);
        }
        all.append(' ').append(String.valueOf(a.getAdminArea()));
        all.append(' ').append(String.valueOf(a.getSubAdminArea()));
        return normalizeSaudiRegionName(all.toString());
    }

    private String normalizeSaudiRegionName(String value) {
        if (value == null) return null;
        String s = value.trim();
        String low = s.toLowerCase(Locale.US);
        if (s.contains("القصيم") || low.contains("qassim") || low.contains("qasim")) return "القصيم";
        if (s.contains("الرياض") || low.contains("riyadh")) return "الرياض";
        if (s.contains("مكة") || low.contains("makkah") || low.contains("mecca")) return "مكة المكرمة";
        if (s.contains("المدينة") || low.contains("madinah") || low.contains("medina")) return "المدينة المنورة";
        if (s.contains("الشرقية") || low.contains("eastern province") || low.contains("ash sharqiyah") || low.contains("sharqiyah")) return "المنطقة الشرقية";
        if (s.contains("عسير") || low.contains("asir")) return "عسير";
        if (s.contains("تبوك") || low.contains("tabuk")) return "تبوك";
        if (s.contains("حائل") || low.contains("hail") || low.contains("ha'il")) return "حائل";
        if (s.contains("الحدود الشمالية") || low.contains("northern borders")) return "الحدود الشمالية";
        if (s.contains("جازان") || low.contains("jazan") || low.contains("jizan")) return "جازان";
        if (s.contains("نجران") || low.contains("najran")) return "نجران";
        if (s.contains("الباحة") || low.contains("al baha") || low.contains("bahah")) return "الباحة";
        if (s.contains("الجوف") || low.contains("al jawf") || low.contains("jawf")) return "الجوف";
        return null;
    }

    private String municipalityFromCoordinates(double lat, double lon) {
        String[] names = {"بريدة","عنيزة","الرس","البكيرية","البدائع","المذنب","رياض الخبراء","عيون الجواء","الأسياح","النبهانية","عقلة الصقور","الشماسية"};
        double[][] pts = {{26.3592,43.9818},{26.0843,43.9936},{25.8694,43.4973},{26.1392,43.6578},{25.9946,43.7338},{25.8601,44.2223},{26.0560,43.5480},{26.4995,43.6290},{26.6084,44.2226},{25.8567,43.5861},{25.8344,42.8807},{26.3198,44.2552}};
        float[] out = new float[1];
        String best = null;
        float bestM = Float.MAX_VALUE;
        for (int i = 0; i < names.length; i++) {
            Location.distanceBetween(lat, lon, pts[i][0], pts[i][1], out);
            if (out[0] < bestM) { bestM = out[0]; best = names[i]; }
        }
        return bestM <= 45000f ? best : null;
    }

    private String regionFromSaudiCoordinates(double lat, double lon) {
        // Fallback only when Android Geocoder omits the province.
        if (lat >= 24.0 && lat <= 28.6 && lon >= 41.0 && lon <= 45.8) return "القصيم";
        if (lat >= 22.0 && lat <= 27.8 && lon >= 44.0 && lon <= 48.8) return "الرياض";
        if (lat >= 19.0 && lat <= 24.8 && lon >= 38.0 && lon <= 43.8) return "مكة المكرمة";
        if (lat >= 22.0 && lat <= 27.5 && lon >= 37.0 && lon <= 42.5) return "المدينة المنورة";
        if (lat >= 23.0 && lat <= 29.9 && lon >= 47.0 && lon <= 55.7) return "المنطقة الشرقية";
        if (lat >= 17.0 && lat <= 21.8 && lon >= 40.5 && lon <= 44.5) return "عسير";
        if (lat >= 26.0 && lat <= 30.5 && lon >= 34.5 && lon <= 39.8) return "تبوك";
        if (lat >= 25.5 && lat <= 29.0 && lon >= 39.0 && lon <= 43.5) return "حائل";
        if (lat >= 28.0 && lat <= 32.2 && lon >= 39.0 && lon <= 46.0) return "الحدود الشمالية";
        if (lat >= 16.0 && lat <= 18.3 && lon >= 41.5 && lon <= 43.7) return "جازان";
        if (lat >= 16.5 && lat <= 20.3 && lon >= 43.5 && lon <= 47.0) return "نجران";
        if (lat >= 19.0 && lat <= 21.5 && lon >= 40.5 && lon <= 42.8) return "الباحة";
        if (lat >= 28.5 && lat <= 32.5 && lon >= 35.5 && lon <= 41.5) return "الجوف";
        return null;
    }

    private void registerHeadingSensors() {
        if (qiblaSensorHelper != null) return;
        if (sensorManager == null) return;
        sensorManager.unregisterListener(this);
        if (rotationVectorSensor != null) {
            sensorManager.registerListener(this, rotationVectorSensor, 20000);
        } else {
            if (accelerometerSensor != null) sensorManager.registerListener(this, accelerometerSensor, 20000);
            if (magneticSensor != null) sensorManager.registerListener(this, magneticSensor, 20000);
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

    private void applyTrueSensorHeading(float trueHeading) {
        if (Float.isNaN(trueHeading)) return;
        long now = System.currentTimeMillis();
        if (now - lastOemHeadingAt < 1500L) return;
        boolean gpsFreshAndMoving = lastSpeedMps >= GPS_HEADING_MIN_SPEED_MPS && now - lastGpsHeadingAt < 3000L;
        if (!gpsFreshAndMoving) {
            lastHeadingAt = now;
            saveHeading(trueHeading, "بوصلة الشاشة • شمال حقيقي");
            qiblaView.setHeading(trueHeading, "بوصلة الشاشة • شمال حقيقي");
        }
    }

    private void applySensorHeading(float headingValue) {
        if (Float.isNaN(headingValue)) return;
        long now = System.currentTimeMillis();
        if (now - lastOemHeadingAt < 1500L) return;
        boolean gpsFreshAndMoving = lastSpeedMps >= GPS_HEADING_MIN_SPEED_MPS && now - lastGpsHeadingAt < 3000L;
        if (!gpsFreshAndMoving) {
            float trueHeading = headingValue;
            if (lastLocation != null) {
                try {
                    GeomagneticField field = new GeomagneticField(
                            (float) lastLocation.getLatitude(),
                            (float) lastLocation.getLongitude(),
                            lastLocation.hasAltitude() ? (float) lastLocation.getAltitude() : 0f,
                            now);
                    trueHeading = normalize(headingValue + field.getDeclination());
                } catch (Throwable ignored) { }
            }
            lastHeadingAt = now;
            saveHeading(trueHeading, "بوصلة الشاشة • شمال حقيقي");
            qiblaView.setHeading(trueHeading, "بوصلة الشاشة • شمال حقيقي");
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
        if (qiblaSensorHelper != null) {
            qiblaSensorHelper.stopListening();
            qiblaSensorHelper = null;
        }
        microGLocationRunning = false;
        handler.removeCallbacks(microGPollRunnable);
        oemDrRunning = false;
        oemLocationService = null;
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
