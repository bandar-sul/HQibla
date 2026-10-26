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

import androidx.core.app.NotificationCompat;

/**
 * Handles background Adhan alarm events: displays high-priority notification with prayer info
 * and plays Takbeerat audio safely (unless the driver is in a phone call).
 */
public class AdhanAlarmReceiver extends BroadcastReceiver {
    private static final String TAG = "AdhanAlarmReceiver";
    public static final String ACTION_DISMISS_ADHAN = "sa.h9.qibla.ACTION_DISMISS_ADHAN";
    public static final String CHANNEL_ID = "qibla_adhan_channel";
    public static final int NOTIFICATION_ID = 9910;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        String action = intent.getAction();

        if (ACTION_DISMISS_ADHAN.equals(action)) {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(NOTIFICATION_ID);
            SoundManager.stop();
            return;
        }

        if (AdhanScheduler.ACTION_ADHAN_ALARM.equals(action)) {
            String prayerName = intent.getStringExtra(AdhanScheduler.EXTRA_PRAYER_NAME);
            String prayerTime = intent.getStringExtra(AdhanScheduler.EXTRA_PRAYER_TIME);
            if (prayerName == null) prayerName = "الصلاة";

            SharedPreferences sp = context.getSharedPreferences(AdhanScheduler.PREFS_NAME, Context.MODE_PRIVATE);
            String cityName = sp.getString(AdhanScheduler.KEY_CITY_NAME, "");

            SharedPreferences uiPrefs = context.getSharedPreferences("h9_qibla_ui", Context.MODE_PRIVATE);
            boolean soundEnabled = uiPrefs.getBoolean("adhan_sound_enabled", true);

            showNotification(context, prayerName, prayerTime, cityName);

            if (soundEnabled) {
                SoundManager.playAdhan(context, null);
            }

            // Immediately schedule the subsequent prayer
            AdhanScheduler.scheduleNextPrayer(context);
        }
    }

    private void showNotification(Context context, String prayerName, String prayerTime, String cityName) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "تنبيهات الأذان والصلوات",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("تنبيهات دخول أوقات الصلوات وأذان الحرم المكي الشريف");
            channel.enableLights(true);
            channel.setLightColor(Color.YELLOW);
            channel.enableVibration(true);
            channel.setShowBadge(true);
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

        String title = "🕌 حان الآن وقت صلاة " + prayerName;
        String locationDesc = (cityName != null && !cityName.trim().isEmpty())
                ? ("وفق توقيت " + cityName + " • تقويم أم القرى")
                : "وفق تقويم أم القرى الرسمي";
        if (prayerTime != null && !prayerTime.isEmpty()) {
            locationDesc = locationDesc + " (" + prayerTime + ")";
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(locationDesc)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(pendingOpen)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "إيقاف الأذان", pendingDismiss);

        try {
            nm.notify(NOTIFICATION_ID, builder.build());
        } catch (Exception e) {
            Log.e(TAG, "Failed to display notification", e);
        }
    }
}
