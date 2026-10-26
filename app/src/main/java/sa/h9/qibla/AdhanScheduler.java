package sa.h9.qibla;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import java.util.Calendar;

/**
 * Schedules background Adhan alarms using AlarmManager so that Takbeerat and notifications
 * trigger at the exact prayer minute even when the app is closed or the user is using
 * car navigation / radio.
 */
public final class AdhanScheduler {
    private static final String TAG = "AdhanScheduler";
    public static final String PREFS_NAME = "sa.h9.qibla.adhan_prefs";
    public static final String KEY_PRAYER_TIMES = "prayer_times_csv";
    public static final String KEY_PRAYER_NAMES = "prayer_names_csv";
    public static final String KEY_CITY_NAME = "city_name";
    public static final String KEY_NEXT_ALARM_MILLIS = "next_alarm_millis";
    public static final String KEY_NEXT_PRAYER_NAME = "next_prayer_name";
    public static final String KEY_NEXT_PRAYER_TIME = "next_prayer_time";
    public static final String KEY_NEXT_PRAYER_INDEX = "next_prayer_index";

    public static final String ACTION_ADHAN_ALARM = "sa.h9.qibla.ACTION_ADHAN_ALARM";
    public static final String EXTRA_PRAYER_NAME = "extra_prayer_name";
    public static final String EXTRA_PRAYER_TIME = "extra_prayer_time";
    public static final String EXTRA_PRAYER_INDEX = "extra_prayer_index";

    private AdhanScheduler() {}

    /**
     * Updates stored prayer times and reschedules the next upcoming prayer alarm.
     */
    public static void saveAndSchedule(Context context, String[] times, String[] names, String cityName) {
        if (context == null || times == null || names == null || times.length < 6 || names.length < 6) return;

        StringBuilder tb = new StringBuilder();
        StringBuilder nb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            if (i > 0) {
                tb.append(",");
                nb.append(",");
            }
            tb.append(times[i]);
            nb.append(names[i]);
        }

        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        sp.edit()
                .putString(KEY_PRAYER_TIMES, tb.toString())
                .putString(KEY_PRAYER_NAMES, nb.toString())
                .putString(KEY_CITY_NAME, cityName != null ? cityName : "")
                .apply();

        scheduleNextPrayer(context);
    }

    /**
     * Finds the next prayer (excluding Sunrise) and sets an exact alarm.
     */
    public static void scheduleNextPrayer(Context context) {
        if (context == null) return;
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String timesCsv = sp.getString(KEY_PRAYER_TIMES, null);
        String namesCsv = sp.getString(KEY_PRAYER_NAMES, null);
        if (timesCsv == null || namesCsv == null) return;

        String[] times = timesCsv.split(",");
        String[] names = namesCsv.split(",");
        if (times.length < 6 || names.length < 6) return;

        Calendar now = Calendar.getInstance();
        int nowMinute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int nowSecond = now.get(Calendar.SECOND);

        int targetIndex = -1;
        int targetMinute = -1;
        boolean isTomorrow = false;

        // Check remaining prayers today (skip index 1 = Sunrise)
        for (int i = 0; i < 6; i++) {
            if (i == 1) continue; // Sunrise has no Adhan
            int m = parseMinute(times[i]);
            if (m < 0) continue;
            // Target is ahead of current minute (or same minute with seconds < 10)
            if (m > nowMinute || (m == nowMinute && nowSecond < 10)) {
                targetIndex = i;
                targetMinute = m;
                break;
            }
        }

        // If all prayers today passed, schedule Fajr tomorrow (index 0)
        if (targetIndex < 0) {
            targetIndex = 0;
            targetMinute = parseMinute(times[0]);
            isTomorrow = true;
        }

        if (targetMinute < 0) return;

        Calendar targetCal = Calendar.getInstance();
        if (isTomorrow) {
            targetCal.add(Calendar.DAY_OF_YEAR, 1);
        }
        targetCal.set(Calendar.HOUR_OF_DAY, targetMinute / 60);
        targetCal.set(Calendar.MINUTE, targetMinute % 60);
        targetCal.set(Calendar.SECOND, 0);
        targetCal.set(Calendar.MILLISECOND, 0);

        long triggerAtMillis = targetCal.getTimeInMillis();
        if (triggerAtMillis <= System.currentTimeMillis()) {
            triggerAtMillis = System.currentTimeMillis() + 1000L;
        }

        sp.edit()
                .putLong(KEY_NEXT_ALARM_MILLIS, triggerAtMillis)
                .putString(KEY_NEXT_PRAYER_NAME, names[targetIndex])
                .putString(KEY_NEXT_PRAYER_TIME, times[targetIndex])
                .putInt(KEY_NEXT_PRAYER_INDEX, targetIndex)
                .apply();

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, AdhanAlarmReceiver.class);
        intent.setAction(ACTION_ADHAN_ALARM);
        intent.putExtra(EXTRA_PRAYER_NAME, names[targetIndex]);
        intent.putExtra(EXTRA_PRAYER_TIME, times[targetIndex]);
        intent.putExtra(EXTRA_PRAYER_INDEX, targetIndex);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pi = PendingIntent.getBroadcast(context, 7701, intent, flags);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // setAlarmClock gives highest priority and wakes device reliably on automotive headunits
                am.setAlarmClock(new AlarmManager.AlarmClockInfo(triggerAtMillis, pi), pi);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                am.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
            } else {
                am.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
            }
            Log.d(TAG, "Scheduled Adhan for " + names[targetIndex] + " at " + targetCal.getTime());
        } catch (SecurityException se) {
            Log.w(TAG, "Exact alarm permission missing, fallback to setWindow/set", se);
            try {
                am.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
            } catch (Exception ignored) {}
        }
    }

    private static int parseMinute(String s) {
        if (s == null) return -1;
        try {
            String[] parts = s.trim().split(":");
            return Integer.parseInt(parts[0].trim()) * 60 + Integer.parseInt(parts[1].trim());
        } catch (Exception e) {
            return -1;
        }
    }
}
