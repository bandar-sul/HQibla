package sa.h9.qibla;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;

import androidx.core.app.NotificationCompat;

/**
 * Automotive Overlay Service:
 * Manages the floating speed & Qibla bubble and rectangular Floating Island across any Android car screen.
 * Implements strict standstill speed deadband (0 km/h) and stabilized stationary Qibla bearing.
 */
public class FloatingSpeedBubbleService extends Service implements LocationListener {

    private static final String CHANNEL_ID = "qibla_floating_bubble_ch";
    private static final int NOTIF_ID = 9081;

    private static volatile FloatingSpeedBubbleService instance;

    public static void updateLivePrayers(String[] names, String[] times, boolean english) {
        FloatingSpeedBubbleService s = instance;
        if (s != null && s.bubbleView != null) {
            s.lastBubblePrayerDay = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR);
            s.bubbleView.updatePrayerTimes(names, times, english);
        }
    }

    public static void updateLiveHeading(float heading) {
        FloatingSpeedBubbleService s = instance;
        if (s != null) {
            s.hasHeadingSignal = true;
            // Apply sensor heading whenever stopped or as primary orientation
            if (s.lastSpeedKmh < 2.5f || Float.isNaN(s.lastHeading)) {
                s.lastHeading = heading;
                s.refreshBubbleData();
            }
        }
    }

    public static void updateLiveWeather(WeatherManager.WeatherInfo info) {
        FloatingSpeedBubbleService s = instance;
        if (s != null && s.bubbleView != null && info != null) {
            s.bubbleView.updateWeather(info);
        }
    }

    public static void reloadPreferences(Context context) {
        FloatingSpeedBubbleService s = instance;
        if (s != null && s.bubbleView != null) {
            s.bubbleView.post(() -> {
                if (s.bubbleView != null) {
                    s.lastBubblePrayerDay = -1;
                    s.lastBubblePrayerLoc = null;
                    s.bubbleView.reloadPreferencesAndTheme();
                    s.refreshBubbleData();
                }
            });
        }
    }

    public static void setAppForeground(boolean isForeground) {
        FloatingSpeedBubbleService s = instance;
        if (s != null && s.bubbleView != null) {
            s.bubbleView.post(() -> {
                // Hide floating ball when Qibla app is open, show when in background
                s.bubbleView.setVisibility(isForeground ? android.view.View.GONE : android.view.View.VISIBLE);
                if (isForeground) {
                    s.hideDropTarget();
                    QuickAppsOverlay.dismissFloatingOverlay();
                }
            });
        }
    }

    private WindowManager windowManager;
    private FloatingSpeedBubbleView bubbleView;
    private WindowManager.LayoutParams layoutParams;
    private LocationManager locationManager;

    private LinearLayout dropTargetView;
    private WindowManager.LayoutParams dropTargetParams;
    private TextView tvDropLabel;
    private boolean dropZoneHovered = false;

    private float lastSpeedKmh = 0f;
    private float lastHeading = 0f;
    private boolean hasHeadingSignal = false;
    private Location lastLocation;
    private Location lastBubblePrayerLoc;
    private int lastBubblePrayerDay = -1;

    public static void start(Context context) {
        if (context == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            return;
        }
        try {
            Intent intent = new Intent(context, FloatingSpeedBubbleService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Throwable ignored) { }
    }

    public static void stop(Context context) {
        if (context == null) return;
        try {
            Intent intent = new Intent(context, FloatingSpeedBubbleService.class);
            context.stopService(intent);
        } catch (Throwable ignored) { }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }

        startForegroundNotification();
        initOverlayWindow();
        initLocationUpdates();
        initCachedWeather();
        initCachedPrayers();
        registerAdhanReceiver();
    }

    private void registerAdhanReceiver() {
        try {
            android.content.IntentFilter filter = new android.content.IntentFilter(AdhanAlarmReceiver.ACTION_ADHAN_TRIGGERED);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(adhanReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(adhanReceiver, filter);
            }
        } catch (Throwable ignored) { }
    }

    private final android.content.BroadcastReceiver adhanReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || bubbleView == null) return;
            bubbleView.post(() -> {
                try {
                    bubbleView.refreshMuteButtonVisibility();
                    bubbleView.expandToIsland();
                } catch (Throwable ignored) { }
            });
        }
    };

    private void initCachedPrayers() {
        try {
            SharedPreferences sp = getSharedPreferences(AdhanScheduler.PREFS_NAME, MODE_PRIVATE);
            String timesCsv = sp.getString(AdhanScheduler.KEY_PRAYER_TIMES, null);
            String namesCsv = sp.getString(AdhanScheduler.KEY_PRAYER_NAMES, null);
            if (timesCsv != null && namesCsv != null && bubbleView != null) {
                String[] times = timesCsv.split(",");
                String[] names = namesCsv.split(",");
                if (times.length == 6 && names.length == 6) {
                    SharedPreferences uiPrefs = getSharedPreferences("h9_qibla_ui", MODE_PRIVATE);
                    boolean isEn = uiPrefs.getBoolean("english_mode", false);
                    bubbleView.updatePrayerTimes(names, times, isEn);
                }
            }
        } catch (Throwable ignored) { }
    }

    private void initCachedWeather() {
        try {
            WeatherManager.WeatherInfo cached = WeatherManager.getCachedWeather(this);
            if (cached != null && bubbleView != null) {
                bubbleView.updateWeather(cached);
            }
        } catch (Throwable ignored) { }
    }

    private void startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "الكرة والجزيرة العائمة للسيارة",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("تشغيل الكرة والجزيرة التفاعلية فوق شاشة السيارة");
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }

        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, openIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0
        );

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("الكرة والجزيرة التفاعلية العائمة للسيارة")
                .setContentText("عرض السرعة ومواقيت الصلاة والطقس عائماً فوق التطبيقات")
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIF_ID, notification);
        }
    }

    private void initOverlayWindow() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager == null) return;

        bubbleView = new FloatingSpeedBubbleView(this);
        int sizePx = bubbleView.getConfiguredBubbleSizePx();

        int windowType = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        layoutParams = new WindowManager.LayoutParams(
                sizePx, sizePx,
                windowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        SharedPreferences sp = getSharedPreferences(MainActivity.UI_PREFS, MODE_PRIVATE);
        layoutParams.gravity = Gravity.TOP | Gravity.LEFT;

        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        int savedX = sp.getInt("floating_ball_pos_x", dp(16));
        int savedY = sp.getInt("floating_ball_pos_y", dp(140));
        layoutParams.x = Math.max(dp(6), Math.min(screenW - sizePx - dp(6), savedX));
        layoutParams.y = Math.max(dp(6), Math.min(screenH - sizePx - dp(12), savedY));

        bubbleView.setWindowManager(windowManager, layoutParams);
        bubbleView.setActionListener(this::syncOnlinePrayerTimes);
        bubbleView.setQuickAppsListener(() -> {
            if (windowManager != null) {
                QuickAppsOverlay.showFloatingOverlay(FloatingSpeedBubbleService.this, windowManager, null);
            }
        });
        bubbleView.setDragLifecycleListener(new FloatingSpeedBubbleView.OnDragLifecycleListener() {
            @Override
            public void onDragStart() {
                showDropTarget();
            }

            @Override
            public void onDragMove(float centerRawX, float centerRawY) {
                updateDropTargetHover(centerRawX, centerRawY);
            }

            @Override
            public boolean onDragEnd(float centerRawX, float centerRawY) {
                boolean shouldHide = isInsideDropZone(centerRawX, centerRawY);
                hideDropTarget();
                if (shouldHide) {
                    boolean isEn = getSharedPreferences(MainActivity.UI_PREFS, MODE_PRIVATE)
                            .getBoolean(MainActivity.KEY_ENGLISH_MODE, false);
                    getSharedPreferences(MainActivity.UI_PREFS, MODE_PRIVATE)
                            .edit()
                            .putBoolean("floating_bubble_enabled", false)
                            .apply();
                    FullScreenSettingsDialog.showCompactToast(
                            FloatingSpeedBubbleService.this,
                            isEn ? "🗑 Floating Ball Hidden (Re-enable in Settings)"
                                 : "🗑 تم إخفاء الكرة العائمة (يمكنك تفعيلها من الإعدادات)"
                    );
                    stopSelf();
                    return true;
                }
                return false;
            }
        });

        try {
            windowManager.addView(bubbleView, layoutParams);
        } catch (Throwable ignored) { }
    }

    private void showDropTarget() {
        if (windowManager == null || dropTargetView != null) return;

        boolean isEn = getSharedPreferences(MainActivity.UI_PREFS, MODE_PRIVATE)
                .getBoolean(MainActivity.KEY_ENGLISH_MODE, false);

        dropTargetView = new LinearLayout(this);
        dropTargetView.setOrientation(LinearLayout.HORIZONTAL);
        dropTargetView.setGravity(Gravity.CENTER);
        dropTargetView.setPadding(dp(16), dp(8), dp(16), dp(8));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xDD1E293B);
        bg.setCornerRadius(dp(29));
        bg.setStroke(dp(2f), 0xFFF87171);
        dropTargetView.setBackground(bg);

        tvDropLabel = new TextView(this);
        tvDropLabel.setText(isEn ? "🗑 Hide" : "🗑 إخفاء");
        tvDropLabel.setTextSize(14f);
        tvDropLabel.setTextColor(0xFFFFFFFF);
        tvDropLabel.setTypeface(FontHelper.getArabicTypeface(this), Typeface.BOLD);
        tvDropLabel.setGravity(Gravity.CENTER);
        dropTargetView.addView(tvDropLabel);

        int windowType = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        int dropW = dp(124);
        int dropH = dp(56);
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;

        dropTargetParams = new WindowManager.LayoutParams(
                dropW, dropH,
                windowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
        );
        dropTargetParams.gravity = Gravity.TOP | Gravity.LEFT;
        dropTargetParams.x = Math.max(0, (screenW - dropW) / 2);
        dropTargetParams.y = Math.max(dp(16), screenH - dropH - dp(26));

        dropZoneHovered = false;
        dropTargetView.setAlpha(0f);
        dropTargetView.setScaleX(0.85f);
        dropTargetView.setScaleY(0.85f);

        try {
            windowManager.addView(dropTargetView, dropTargetParams);
            dropTargetView.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(150L).start();
        } catch (Throwable ignored) {
            dropTargetView = null;
        }
    }

    private void updateDropTargetHover(float centerRawX, float centerRawY) {
        if (dropTargetView == null) return;
        boolean hovered = isInsideDropZone(centerRawX, centerRawY);
        if (hovered != dropZoneHovered) {
            dropZoneHovered = hovered;
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(hovered ? 0xF5B91C1C : 0xDD1E293B);
            bg.setCornerRadius(dp(29));
            bg.setStroke(dp(hovered ? 2.6f : 2f), hovered ? Color.WHITE : 0xFFF87171);
            dropTargetView.setBackground(bg);
            dropTargetView.animate()
                    .scaleX(hovered ? 1.15f : 1.0f)
                    .scaleY(hovered ? 1.15f : 1.0f)
                    .setDuration(120L)
                    .start();
        }
    }

    private boolean isInsideDropZone(float centerRawX, float centerRawY) {
        if (dropTargetView == null || dropTargetParams == null) return false;
        int[] loc = new int[2];
        dropTargetView.getLocationOnScreen(loc);
        float targetX = (loc[0] > 0 || loc[1] > 0) ? loc[0] : dropTargetParams.x;
        float targetY = (loc[0] > 0 || loc[1] > 0) ? loc[1] : dropTargetParams.y;
        float cx = targetX + dropTargetParams.width * 0.5f;
        float cy = targetY + dropTargetParams.height * 0.5f;
        float dx = centerRawX - cx;
        float dy = centerRawY - cy;
        float threshold = dp(66);
        return (dx * dx + dy * dy) <= (threshold * threshold);
    }

    private void hideDropTarget() {
        dropZoneHovered = false;
        if (windowManager != null && dropTargetView != null) {
            try {
                windowManager.removeView(dropTargetView);
            } catch (Throwable ignored) { }
            dropTargetView = null;
            dropTargetParams = null;
            tvDropLabel = null;
        }
    }

    private void initLocationUpdates() {
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (locationManager == null) return;

        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500L, 0.5f, this);
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1500L, 2.0f, this);
            }
        } catch (SecurityException ignored) { }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        refreshBubbleData();
        return START_STICKY;
    }

    private int[] getSavedPrayerOffsets(SharedPreferences uiPrefs) {
        return new int[]{
                uiPrefs.getInt("prayer_offset_fajr", 0),
                uiPrefs.getInt("prayer_offset_sunrise", 0),
                uiPrefs.getInt("prayer_offset_dhuhr", 0),
                uiPrefs.getInt("prayer_offset_asr", 0),
                uiPrefs.getInt("prayer_offset_maghrib", 0),
                uiPrefs.getInt("prayer_offset_isha", 0)
        };
    }

    private Location getEffectivePrayerLocation() {
        return lastLocation;
    }

    private void refreshBubbleData() {
        if (bubbleView == null) return;
        SharedPreferences uiPrefs = getSharedPreferences(MainActivity.UI_PREFS, MODE_PRIVATE);
        boolean isMph = uiPrefs.getBoolean("speed_mph", false);
        boolean isEn = uiPrefs.getBoolean(MainActivity.KEY_ENGLISH_MODE, false);

        Location effLoc = getEffectivePrayerLocation();
        double lat = (effLoc != null) ? effLoc.getLatitude() : 24.7136;
        double lon = (effLoc != null) ? effLoc.getLongitude() : 46.6753;

        float relativeQiblaAngle = 0f;
        if (effLoc != null) {
            float qiblaBearing = (float) QiblaSensorHelper.calculateQiblaBearing(lat, lon);
            relativeQiblaAngle = (qiblaBearing - lastHeading + 360f) % 360f;
        }

        bubbleView.updateSpeedAndHeading(lastSpeedKmh, relativeQiblaAngle, isMph, isEn, hasHeadingSignal);

        java.util.Calendar cal = java.util.Calendar.getInstance();
        int today = cal.get(java.util.Calendar.DAY_OF_YEAR);
        boolean movedFar = (effLoc != null) && (lastBubblePrayerLoc == null || lastBubblePrayerLoc.distanceTo(effLoc) > 1000f);
        if (lastBubblePrayerDay == today && !movedFar) {
            return;
        }

        lastBubblePrayerDay = today;
        if (effLoc != null) {
            lastBubblePrayerLoc = new Location(effLoc);
        }

        // Primary GPS astronomical calculation honoring user's method & offsets
        try {
            int[] offsets = getSavedPrayerOffsets(uiPrefs);
            int method = uiPrefs.getInt("prayer_calculation_method", PrayerTimesCalculator.METHOD_UMM_AL_QURA);
            PrayerTimesCalculator.Result pr = PrayerTimesCalculator.calculate(
                    lat, lon, cal, 0, offsets, method, isEn);
            String[] names = isEn
                    ? new String[]{"Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"}
                    : new String[]{"الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"};
            bubbleView.updatePrayerTimes(names, pr.times24, isEn);
        } catch (Throwable ignored) { }
    }

    private void syncOnlinePrayerTimes() {
        Location effLoc = getEffectivePrayerLocation();
        double lat = (effLoc != null) ? effLoc.getLatitude() : 24.7136;
        double lon = (effLoc != null) ? effLoc.getLongitude() : 46.6753;
        SharedPreferences uiPrefs = getSharedPreferences(MainActivity.UI_PREFS, MODE_PRIVATE);
        boolean isEn = uiPrefs.getBoolean(MainActivity.KEY_ENGLISH_MODE, false);
        int[] offsets = getSavedPrayerOffsets(uiPrefs);
        int method = uiPrefs.getInt("prayer_calculation_method", PrayerTimesCalculator.METHOD_UMM_AL_QURA);

        // Fetch Weather
        WeatherManager.fetchWeather(this, lat, lon, true, info -> {
            if (info != null && bubbleView != null) {
                bubbleView.updateWeather(info);
            }
        });

        // Additional Online verification & Hijri Ramadan sync
        PrayerTimesCalculator.fetchOnlineUmmAlQura(
                lat, lon, java.util.Calendar.getInstance(), 0, offsets, method, isEn,
                new PrayerTimesCalculator.OnlineCallback() {
                    @Override
                    public void onSuccess(PrayerTimesCalculator.Result onlineResult) {
                        if (bubbleView != null && onlineResult != null) {
                            String[] names = isEn
                                    ? new String[]{"Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"}
                                    : new String[]{"الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"};
                            lastBubblePrayerDay = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR);
                            bubbleView.updatePrayerTimes(names, onlineResult.times24, isEn);
                        }
                    }

                    @Override
                    public void onError(Throwable t) { }
                }
        );
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location == null) return;
        lastLocation = location;

        // 1. Enforce strict standstill deadband: below 2.0 km/h (~0.55 m/s) speed is strictly 0 km/h
        float rawSpeedKmh = location.hasSpeed() ? (location.getSpeed() * 3.6f) : 0f;
        if (rawSpeedKmh < 2.0f) {
            lastSpeedKmh = 0f;
        } else {
            lastSpeedKmh = rawSpeedKmh;
        }

        // 2. Only accept GPS bearing when vehicle is genuinely moving (>= 2.5 km/h)
        // At standstill, GPS bearing wanders randomly due to noise and multipath
        if (rawSpeedKmh >= 2.5f && location.hasBearing()) {
            lastHeading = location.getBearing();
            hasHeadingSignal = true;
        }

        // 3. Update weather and calculate prayer times primarily from GPS
        refreshBubbleData();

        WeatherManager.fetchWeather(this, location.getLatitude(), location.getLongitude(), false, info -> {
            if (info != null && bubbleView != null) {
                bubbleView.updateWeather(info);
            }
        });
    }

    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
    @Override public void onProviderEnabled(String provider) { }
    @Override public void onProviderDisabled(String provider) { }

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        try { unregisterReceiver(adhanReceiver); } catch (Throwable ignored) { }
        if (locationManager != null) {
            try { locationManager.removeUpdates(this); } catch (SecurityException ignored) { }
        }
        hideDropTarget();
        QuickAppsOverlay.dismissFloatingOverlay();
        if (windowManager != null && bubbleView != null) {
            try { windowManager.removeView(bubbleView); } catch (Throwable ignored) { }
            bubbleView = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private int dp(float val) {
        return Math.round(val * getResources().getDisplayMetrics().density);
    }

    private int dp(int val) {
        return Math.round(val * getResources().getDisplayMetrics().density);
    }
}
