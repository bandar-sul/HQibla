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
 * High-reliability background Adhan alarm scheduler.
 * Schedules exact alarms for all upcoming daily prayers with AlarmClock/ExactAndAllowWhileIdle,
 * ensuring Takbeerat and notifications trigger even when the car headunit is locked, sleeping,
 * running navigation, or when the app is in the background.
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
     * Updates stored prayer times and reschedules all upcoming prayers.
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

        scheduleAllPrayers(context);
    }

    /**
     * Backward-compatible alias for scheduling.
     */
    public static void scheduleNextPrayer(Context context) {
        scheduleAllPrayers(context);
    }

    /**
     * Schedules alarms for ALL remaining prayers today, plus tomorrow's Fajr,
     * so that each prayer has its own dedicated system alarm.
     */
    public static void scheduleAllPrayers(Context context) {
        if (context == null) return;
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String timesCsv = sp.getString(KEY_PRAYER_TIMES, null);
        String namesCsv = sp.getString(KEY_PRAYER_NAMES, null);
        if (timesCsv == null || namesCsv == null) return;

        String[] times = timesCsv.split(",");
        String[] names = namesCsv.split(",");
        if (times.length < 6 || names.length < 6) return;

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Calendar now = Calendar.getInstance();
        int nowMinute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        long nowMillis = System.currentTimeMillis();

        long firstUpcomingMillis = Long.MAX_VALUE;
        String firstUpcomingName = "";
        String firstUpcomingTime = "";
        int firstUpcomingIndex = -1;

        // Schedule remaining prayers for today (skip index 1 = Sunrise)
        for (int i = 0; i < 6; i++) {
            if (i == 1) continue; // Sunrise has no Adhan
            int m = parseMinute(times[i]);
            if (m < 0) continue;

            Calendar targetCal = Calendar.getInstance();
            targetCal.set(Calendar.HOUR_OF_DAY, m / 60);
            targetCal.set(Calendar.MINUTE, m % 60);
            targetCal.set(Calendar.SECOND, 0);
            targetCal.set(Calendar.MILLISECOND, 0);

            long triggerAtMillis = targetCal.getTimeInMillis();

            // If this prayer is still in the future today
            if (triggerAtMillis > nowMillis - 30000L) {
                PendingIntent pi = buildPendingIntent(context, i, names[i], times[i]);
                scheduleAlarm(am, triggerAtMillis, pi);
                Log.d(TAG, "Scheduled alarm for " + names[i] + " at " + targetCal.getTime());

                if (triggerAtMillis < firstUpcomingMillis) {
                    firstUpcomingMillis = triggerAtMillis;
                    firstUpcomingName = names[i];
                    firstUpcomingTime = times[i];
                    firstUpcomingIndex = i;
                }
            }
        }

        // Always schedule tomorrow's Fajr (index 0) as guaranteed future anchor
        int fajrM = parseMinute(times[0]);
        if (fajrM >= 0) {
            Calendar tomorrowFajr = Calendar.getInstance();
            tomorrowFajr.add(Calendar.DAY_OF_YEAR, 1);
            tomorrowFajr.set(Calendar.HOUR_OF_DAY, fajrM / 60);
            tomorrowFajr.set(Calendar.MINUTE, fajrM % 60);
            tomorrowFajr.set(Calendar.SECOND, 0);
            tomorrowFajr.set(Calendar.MILLISECOND, 0);

            long fajrMillis = tomorrowFajr.getTimeInMillis();
            PendingIntent piFajr = buildPendingIntent(context, 10, names[0], times[0]);
            scheduleAlarm(am, fajrMillis, piFajr);
            Log.d(TAG, "Scheduled tomorrow's Fajr at " + tomorrowFajr.getTime());

            if (firstUpcomingIndex < 0) {
                firstUpcomingMillis = fajrMillis;
                firstUpcomingName = names[0];
                firstUpcomingTime = times[0];
                firstUpcomingIndex = 0;
            }
        }

        if (firstUpcomingIndex >= 0) {
            sp.edit()
                    .putLong(KEY_NEXT_ALARM_MILLIS, firstUpcomingMillis)
                    .putString(KEY_NEXT_PRAYER_NAME, firstUpcomingName)
                    .putString(KEY_NEXT_PRAYER_TIME, firstUpcomingTime)
                    .putInt(KEY_NEXT_PRAYER_INDEX, firstUpcomingIndex)
                    .apply();
        }
    }

    private static PendingIntent buildPendingIntent(Context context, int requestCodeOffset, String prayerName, String prayerTime) {
        Intent intent = new Intent(context, AdhanAlarmReceiver.class);
        intent.setAction(ACTION_ADHAN_ALARM);
        intent.putExtra(EXTRA_PRAYER_NAME, prayerName);
        intent.putExtra(EXTRA_PRAYER_TIME, prayerTime);
        intent.putExtra(EXTRA_PRAYER_INDEX, requestCodeOffset % 6);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(context, 7700 + requestCodeOffset, intent, flags);
    }

    private static void scheduleAlarm(AlarmManager am, long triggerAtMillis, PendingIntent pi) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (am.canScheduleExactAlarms()) {
                    am.setAlarmClock(new AlarmManager.AlarmClockInfo(triggerAtMillis, pi), pi);
                } else {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                am.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
            } else {
                am.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
            }
        } catch (SecurityException se) {
            Log.w(TAG, "Exact alarm permission restricted, falling back to setAndAllowWhileIdle", se);
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
                } else {
                    am.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
                }
            } catch (Throwable ignored) {}
        } catch (Throwable t) {
            Log.e(TAG, "Error scheduling alarm", t);
        }
    }

    public static int parseMinute(String s) {
        if (s == null) return -1;
        try {
            StringBuilder sb = new StringBuilder();
            for (char c : s.toCharArray()) {
                if (c >= '٠' && c <= '٩') {
                    sb.append((char) ('0' + (c - '٠')));
                } else {
                    sb.append(c);
                }
            }
            String clean = sb.toString().trim();
            int space = clean.indexOf(' ');
            if (space > 0) clean = clean.substring(0, space).trim();
            String[] parts = clean.split(":");
            if (parts.length < 2) return -1;
            return Integer.parseInt(parts[0].trim()) * 60 + Integer.parseInt(parts[1].trim());
        } catch (Exception e) {
            return -1;
        }
    }
}
