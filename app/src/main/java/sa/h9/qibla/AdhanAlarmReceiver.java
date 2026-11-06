package sa.h9.qibla;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;

import android.net.Uri;
import androidx.core.app.NotificationCompat;

/**
 * Handles background Adhan alarm events: displays high-priority notification with prayer info
 * and plays male voice alert / Takbeerat audio safely.
 */
public class AdhanAlarmReceiver extends BroadcastReceiver {
    private static final String TAG = "AdhanAlarmReceiver";
    public static final String ACTION_DISMISS_ADHAN = "sa.h9.qibla.ACTION_DISMISS_ADHAN";
    public static final String CHANNEL_ID = "qibla_adhan_v8";
    public static final int NOTIFICATION_ID = 9910;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        final PendingResult pendingResult = goAsync();
        try {
            String action = intent.getAction();

            if (ACTION_DISMISS_ADHAN.equals(action)) {
                try {
                    NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                    if (nm != null) nm.cancel(NOTIFICATION_ID);
                } catch (Throwable ignored) { }
                SoundManager.stop();
                if (pendingResult != null) pendingResult.finish();
                return;
            }

            if (AdhanScheduler.ACTION_ADHAN_ALARM.equals(action)) {
                String prayerName = intent.getStringExtra(AdhanScheduler.EXTRA_PRAYER_NAME);
                String prayerTime = intent.getStringExtra(AdhanScheduler.EXTRA_PRAYER_TIME);
                if (prayerName == null) prayerName = "الصلاة";

                SharedPreferences sp = context.getSharedPreferences(AdhanScheduler.PREFS_NAME, Context.MODE_PRIVATE);
                java.util.Calendar now = java.util.Calendar.getInstance();
                String todayPrayerKey = String.format(java.util.Locale.US, "%04d-%02d-%02d_%s",
                        now.get(java.util.Calendar.YEAR), now.get(java.util.Calendar.DAY_OF_YEAR), prayerName);
                String lastKey = sp.getString("last_triggered_prayer_key", "");
                if (todayPrayerKey.equals(lastKey)) {
                    Log.w(TAG, "Adhan already triggered for " + todayPrayerKey + ", skipping duplicate receiver execution.");
                    AdhanScheduler.scheduleNextPrayer(context);
                    if (pendingResult != null) pendingResult.finish();
                    return;
                }
                sp.edit().putString("last_triggered_prayer_key", todayPrayerKey).apply();

                String cityName = sp.getString(AdhanScheduler.KEY_CITY_NAME, "");

                SharedPreferences uiPrefs = context.getSharedPreferences("h9_qibla_ui", Context.MODE_PRIVATE);
                boolean soundEnabled = uiPrefs.getBoolean("adhan_sound_enabled", true);
                int soundMode = uiPrefs.getInt("adhan_sound_mode", SoundManager.SOUND_MODE_COMBINED);

                try {
                    showNotification(context, prayerName, prayerTime, cityName);
                } catch (Throwable t) {
                    Log.e(TAG, "Failed to show Adhan notification", t);
                }

                if (soundEnabled && soundMode != SoundManager.SOUND_MODE_MUTED) {
                    android.os.PowerManager pm = null;
                    try {
                        pm = (android.os.PowerManager) context.getSystemService(Context.POWER_SERVICE);
                    } catch (Throwable ignored) { }
                    android.os.PowerManager.WakeLock wl = null;
                    if (pm != null) {
                        try {
                            int flags = android.os.PowerManager.PARTIAL_WAKE_LOCK;
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                flags |= android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP;
                            }
                            wl = pm.newWakeLock(flags, "sa.h9.qibla:AdhanWakeLock");
                            wl.acquire(50000L); // 50 seconds for full audio
                        } catch (Throwable ignored) { }
                    }
                    final android.os.PowerManager.WakeLock finalWl = wl;
                    SoundManager.playPrayerAlert(context, soundMode, true, () -> {
                        if (finalWl != null) {
                            try {
                                if (finalWl.isHeld()) finalWl.release();
                            } catch (Throwable ignored) { }
                        }
                        try {
                            if (pendingResult != null) pendingResult.finish();
                        } catch (Throwable ignored) { }
                    });
                } else {
                    if (pendingResult != null) pendingResult.finish();
                }

                // Immediately schedule the subsequent prayer
                AdhanScheduler.scheduleNextPrayer(context);
            } else {
                if (pendingResult != null) pendingResult.finish();
            }
        } catch (Throwable t) {
            Log.e(TAG, "Fatal error prevented in onReceive", t);
            if (pendingResult != null) {
                try { pendingResult.finish(); } catch (Throwable ignored) {}
            }
        }
    }

    public static void showNotification(Context context, String prayerName, String prayerTime, String cityName) {
        if (context == null) return;
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        final String channelId = CHANNEL_ID;
        Uri soundUri = null;
        try {
            soundUri = Uri.parse("android.resource://" + context.getPackageName() + "/" + R.raw.prayer_chime);
        } catch (Throwable ignored) { }
        if (soundUri == null) {
            soundUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                nm.deleteNotificationChannel("qibla_adhan_v6");
                nm.deleteNotificationChannel("qibla_adhan_v7");
            } catch (Throwable ignored) { }

            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "تنبيهات دخول أوقات الصلاة والأذان (معتمد)",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("تنبيهات صوتية فورية لدخول وقت كل صلاة والأذان المكي");
            channel.enableLights(true);
            channel.setLightColor(Color.YELLOW);
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 500, 250, 500});
            channel.setShowBadge(true);
            channel.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);

            if (soundUri != null) {
                android.media.AudioAttributes audioAttrs = new android.media.AudioAttributes.Builder()
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                        .build();
                channel.setSound(soundUri, audioAttrs);
            }
            nm.createNotificationChannel(channel);
        }

        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            piFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingOpen = PendingIntent.getActivity(context, 7702, openIntent, piFlags);

        Intent dismissIntent = new Intent(context, AdhanAlarmReceiver.class);
        dismissIntent.setAction(ACTION_DISMISS_ADHAN);
        PendingIntent pendingDismiss = PendingIntent.getBroadcast(context, 7703, dismissIntent, piFlags);

        String title = "🕌 دخل الآن وقت صلاة " + prayerName;
        String locationDesc = (cityName != null && !cityName.trim().isEmpty())
                ? ("وفق توقيت " + cityName + " • تقويم أم القرى")
                : "وفق تقويم أم القرى الرسمي";
        if (prayerTime != null && !prayerTime.isEmpty()) {
            locationDesc = locationDesc + " (" + prayerTime + ")";
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(locationDesc)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setSound(soundUri)
                .setDefaults(NotificationCompat.DEFAULT_VIBRATE | NotificationCompat.DEFAULT_LIGHTS)
                .setContentIntent(pendingOpen)
                .setFullScreenIntent(pendingOpen, true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "إيقاف الصوت", pendingDismiss);

        try {
            nm.notify(NOTIFICATION_ID, builder.build());
        } catch (Exception e) {
            Log.e(TAG, "Failed to display notification", e);
        }
    }
}
