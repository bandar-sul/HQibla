package sa.h9.qibla;

import android.icu.util.IslamicCalendar;
import android.os.Build;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Dual-stage high-precision Umm al-Qura prayer-time engine:
 * 1. Primary Stage (GPS Astronomical - Works 100% Offline):
 *    Uses full NOAA / Jean Meeus Julian Day solar ephemeris (Solar Declination & Equation of Time)
 *    combined with KACST / Official Umm al-Qura rounding standards:
 *    - Fajr: -18.5° twilight angle (nearest minute)
 *    - Sunrise: -0.8333° horizon with KACST conservative floor (-0.5 min)
 *    - Dhuhr: Solar transit (Zawal) rounded up to full minute
 *    - Asr: Shafi/Hanbali shadow factor 1 rounded up to full minute
 *    - Maghrib: Sunset (-0.8333°) rounded up to full minute
 *    - Isha: Exactly 90 minutes after Maghrib (120 minutes in Ramadan)
 *
 * 2. Secondary Stage (Online Verification & Sync):
 *    Verifies official Umm al-Qura Hijri date (including Ramadan detection via calendarMethod=UAQ)
 *    and coordinates against online prayer servers, respecting user timezone and KACST rounding.
 */
public final class PrayerTimesCalculator {
    private static final String TAG = "PrayerTimesCalc";
    private static final double FAJR_DEPRESSION_ANGLE = -18.5;
    private static final double SUNRISE_SUNSET_ANGLE = -0.8333;

    public static final int METHOD_MWL = 3;
    public static final int METHOD_UMM_AL_QURA = 4;
    public static final int METHOD_EGYPT = 5;
    public static final int METHOD_KACST = 11; // Mapped internally to Saudi KACST/Umm al-Qura (never Singapore)

    private PrayerTimesCalculator() { }

    public static final class Result {
        public final String[] times24;
        public final String hijriDate;
        public final String hijriDateEn;
        public final boolean fromOnline;
        public final boolean isRamadan;

        public Result(String[] times24, String hijriDate, String hijriDateEn) {
            this(times24, hijriDate, hijriDateEn, false, false);
        }

        public Result(String[] times24, String hijriDate, String hijriDateEn, boolean fromOnline, boolean isRamadan) {
            this.times24 = times24;
            this.hijriDate = hijriDate;
            this.hijriDateEn = hijriDateEn;
            this.fromOnline = fromOnline;
            this.isRamadan = isRamadan;
        }
    }

    public interface OnlineCallback {
        void onSuccess(Result result);
        void onError(Throwable t);
    }

    public static Result calculate(double latitude, double longitude, Calendar date) {
        return calculate(latitude, longitude, date, 0, new int[]{0, 0, 0, 0, 0, 0}, METHOD_UMM_AL_QURA, false);
    }

    public static Result calculate(double latitude, double longitude, Calendar date, int manualOffset) {
        return calculate(latitude, longitude, date, manualOffset, new int[]{0, 0, 0, 0, 0, 0}, METHOD_UMM_AL_QURA, false);
    }

    public static Result calculate(double latitude, double longitude, Calendar date, boolean english) {
        return calculate(latitude, longitude, date, 0, new int[]{0, 0, 0, 0, 0, 0}, METHOD_UMM_AL_QURA, english);
    }

    public static Result calculate(double latitude, double longitude, Calendar date, int manualOffset, boolean english) {
        return calculate(latitude, longitude, date, manualOffset, new int[]{0, 0, 0, 0, 0, 0}, METHOD_UMM_AL_QURA, english);
    }

    public static Result calculate(double latitude, double longitude, Calendar date, int manualOffset,
                                   int fajrOffset, int asrOffset, int method, boolean english) {
        return calculate(latitude, longitude, date, manualOffset, new int[]{fajrOffset, 0, 0, asrOffset, 0, 0}, method, english);
    }

    public static Result calculate(double latitude, double longitude, Calendar date, int manualOffset,
                                   int[] individualOffsets, int method, boolean english) {
        return calculateInternal(latitude, longitude, date, manualOffset, individualOffsets, method, english, null);
    }

    private static Result calculateInternal(double latitude, double longitude, Calendar date, int manualOffset,
                                            int[] individualOffsets, int method, boolean english, Boolean ramadanOverride) {
        Calendar local = (date != null) ? (Calendar) date.clone() : Calendar.getInstance();

        int year = local.get(Calendar.YEAR);
        int month = local.get(Calendar.MONTH) + 1;
        int day = local.get(Calendar.DAY_OF_MONTH);

        // NOAA / Jean Meeus Julian Day at 0h UTC
        int y = year;
        int m = month;
        if (m <= 2) {
            y -= 1;
            m += 12;
        }
        int a = y / 100;
        int b = 2 - a + (a / 4);
        double jd0 = Math.floor(365.25 * (y + 4716)) + Math.floor(30.6001 * (m + 1)) + day + b - 1524.5;

        // Evaluate solar parameters precisely at local solar noon (~ 12h local solar time in UTC)
        double jd = jd0 + 0.5 - (longitude / 360.0);
        double T = (jd - 2451545.0) / 36525.0;

        double l0 = normalizeDeg(280.46646 + T * (36000.76983 + T * 0.0003032));
        double meanAnomaly = normalizeDeg(357.52911 + T * (35999.05029 - 0.0001537 * T));
        double ecc = 0.016708634 - T * (0.000042037 + 0.0000001267 * T);

        double mRad = Math.toRadians(meanAnomaly);
        double eqCenter = (1.914602 - T * (0.004817 + 0.000014 * T)) * Math.sin(mRad)
                + (0.019993 - 0.000101 * T) * Math.sin(2.0 * mRad)
                + 0.000289 * Math.sin(3.0 * mRad);

        double trueLong = l0 + eqCenter;
        double omega = 125.04 - 1934.136 * T;
        double appLong = trueLong - 0.00569 - 0.00478 * Math.sin(Math.toRadians(omega));

        double eps0 = 23.0 + (26.0 + (21.448 - T * (46.815 + T * (0.00059 - T * 0.001813))) / 60.0) / 60.0;
        double eps = eps0 + 0.00256 * Math.cos(Math.toRadians(omega));

        double decl = Math.asin(Math.sin(Math.toRadians(eps)) * Math.sin(Math.toRadians(appLong)));
        double yObl = Math.tan(Math.toRadians(eps / 2.0));
        yObl = yObl * yObl;
        double l0Rad = Math.toRadians(l0);

        // High-precision Equation of Time in minutes
        double eqTime = 4.0 * Math.toDegrees(
                yObl * Math.sin(2.0 * l0Rad)
                        - 2.0 * ecc * Math.sin(mRad)
                        + 4.0 * ecc * yObl * Math.sin(mRad) * Math.cos(2.0 * l0Rad)
                        - 0.5 * yObl * yObl * Math.sin(4.0 * l0Rad)
                        - 1.25 * ecc * ecc * Math.sin(2.0 * mRad)
        );

        TimeZone zone = local.getTimeZone();
        int offsetMinutes = zone.getOffset(local.getTimeInMillis()) / 60000;
        boolean inSaudi = isInsideSaudiArabia(latitude, longitude);
        if (inSaudi) {
            offsetMinutes = 180; // Official Saudi Time (Asia/Riyadh, GMT+3, zero DST)
        }
        double solarNoon = 720.0 - 4.0 * longitude - eqTime + offsetMinutes;

        double fajrAngle = FAJR_DEPRESSION_ANGLE;
        if (method == METHOD_MWL) {
            fajrAngle = -18.0;
        } else if (method == METHOD_EGYPT) {
            fajrAngle = -19.5;
        }

        double sunriseHourAngle = hourAngleMinutes(latitude, decl, SUNRISE_SUNSET_ANGLE);
        double fajrHourAngle = hourAngleMinutes(latitude, decl, fajrAngle);

        double declDeg = Math.toDegrees(decl);
        double asrAltitude = Math.toDegrees(Math.atan(1.0 /
                (1.0 + Math.tan(Math.toRadians(Math.abs(latitude - declDeg))))));
        double asrHourAngle = hourAngleMinutes(latitude, decl, asrAltitude);

        int offFajr = (individualOffsets != null && individualOffsets.length > 0) ? individualOffsets[0] : 0;
        int offSunrise = (individualOffsets != null && individualOffsets.length > 1) ? individualOffsets[1] : 0;
        int offDhuhr = (individualOffsets != null && individualOffsets.length > 2) ? individualOffsets[2] : 0;
        int offAsr = (individualOffsets != null && individualOffsets.length > 3) ? individualOffsets[3] : 0;
        int offMaghrib = (individualOffsets != null && individualOffsets.length > 4) ? individualOffsets[4] : 0;
        int offIsha = (individualOffsets != null && individualOffsets.length > 5) ? individualOffsets[5] : 0;

        int[] hijri = getUmmAlQuraHijriDate(local);
        boolean ramadan = (ramadanOverride != null) ? ramadanOverride : (hijri[1] == 9);

        boolean useKacstRounding = (method == METHOD_UMM_AL_QURA || method == METHOD_KACST);

        int baseFajr;
        int baseSunrise;
        int baseDhuhr;
        int baseAsr;
        int baseMaghrib;
        int baseIsha;

        if (useKacstRounding) {
            // Exact KACST / Official Umm al-Qura mosque rounding standard
            baseFajr = (int) Math.round(solarNoon - fajrHourAngle) + manualOffset;
            baseSunrise = (int) Math.floor((solarNoon - sunriseHourAngle) - 0.5) + manualOffset;
            baseDhuhr = (int) Math.ceil(solarNoon) + manualOffset;
            baseAsr = (int) Math.ceil(solarNoon + asrHourAngle) + manualOffset;
            baseMaghrib = (int) Math.ceil(solarNoon + sunriseHourAngle) + manualOffset;
            baseIsha = baseMaghrib + (ramadan ? 120 : 90);
        } else {
            baseFajr = (int) Math.round(solarNoon - fajrHourAngle) + manualOffset;
            baseSunrise = (int) Math.round(solarNoon - sunriseHourAngle) + manualOffset;
            baseDhuhr = (int) Math.round(solarNoon) + manualOffset;
            baseAsr = (int) Math.round(solarNoon + asrHourAngle) + manualOffset;
            baseMaghrib = (int) Math.round(solarNoon + sunriseHourAngle) + manualOffset;
            double ishaAngle = (method == METHOD_EGYPT) ? -17.5 : -17.0;
            double ishaHourAngle = hourAngleMinutes(latitude, decl, ishaAngle);
            baseIsha = (int) Math.round(solarNoon + ishaHourAngle) + manualOffset;
        }

        int fajr = normalizeMinute(baseFajr + offFajr);
        int sunrise = normalizeMinute(baseSunrise + offSunrise);
        int dhuhr = normalizeMinute(baseDhuhr + offDhuhr);
        int asr = normalizeMinute(baseAsr + offAsr);
        int maghrib = normalizeMinute(baseMaghrib + offMaghrib);
        int isha = normalizeMinute(baseIsha + offIsha);

        String[] times = {
                format24(fajr),
                format24(sunrise),
                format24(dhuhr),
                format24(asr),
                format24(maghrib),
                format24(isha)
        };

        String[] monthsAr = {
                "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
                "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة"
        };
        String[] monthsEn = {
                "Muharram", "Safar", "Rabi' I", "Rabi' II", "Jumada I", "Jumada II",
                "Rajab", "Sha'ban", "Ramadan", "Shawwal", "Dhu al-Qi'dah", "Dhu al-Hijjah"
        };
        int mIdx = Math.max(0, Math.min(11, hijri[1] - 1));
        String hijriTextAr = hijri[2] + " " + monthsAr[mIdx] + " " + hijri[0];
        String hijriTextEn = hijri[2] + " " + monthsEn[mIdx] + " " + hijri[0] + " AH";
        return new Result(times, english ? hijriTextEn : hijriTextAr, hijriTextEn, false, ramadan);
    }

    public static void fetchOnlineUmmAlQura(double lat, double lon, Calendar date, int manualOffset, boolean english, OnlineCallback callback) {
        fetchOnlineUmmAlQura(lat, lon, date, manualOffset, 0, 0, METHOD_UMM_AL_QURA, english, callback);
    }

    public static void fetchOnlineUmmAlQura(double lat, double lon, Calendar date, int manualOffset,
                                            int fajrOffset, int asrOffset, int method, boolean english, OnlineCallback callback) {
        fetchOnlineUmmAlQura(lat, lon, date, manualOffset, new int[]{fajrOffset, 0, 0, asrOffset, 0, 0}, method, english, callback);
    }

    public static void fetchOnlineUmmAlQura(double lat, double lon, Calendar date, int manualOffset,
                                            int[] individualOffsets, int method, boolean english, OnlineCallback callback) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                Calendar targetDate = (date != null) ? (Calendar) date.clone() : Calendar.getInstance();
                int day = targetDate.get(Calendar.DAY_OF_MONTH);
                int month = targetDate.get(Calendar.MONTH) + 1;
                int year = targetDate.get(Calendar.YEAR);
                String dateStr = String.format(Locale.US, "%02d-%02d-%04d", day, month, year);

                // Never pass 11 (Singapore in AlAdhan!) for KACST — map KACST to Umm al-Qura (4)
                int apiMethod = (method == METHOD_KACST) ? METHOD_UMM_AL_QURA : method;
                boolean inSaudi = isInsideSaudiArabia(lat, lon);
                String tzId = inSaudi ? "Asia/Riyadh" : TimeZone.getDefault().getID();

                String urlStr = String.format(Locale.US,
                        "https://api.aladhan.com/v1/timings/%s?latitude=%.5f&longitude=%.5f&method=%d&calendarMethod=UAQ&timezonestring=%s",
                        dateStr, lat, lon, apiMethod, URLEncoder.encode(tzId, "UTF-8"));

                URL url = new URL(urlStr);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(7000);
                conn.setReadTimeout(9000);
                conn.setRequestProperty("User-Agent", "H9-Qibla-UmmAlQura/9.9.9");
                conn.setRequestProperty("Accept", "application/json");

                int code = conn.getResponseCode();
                if (code == 200) {
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) sb.append(line);
                    }
                    JSONObject root = new JSONObject(sb.toString());
                    JSONObject data = root.getJSONObject("data");
                    JSONObject timings = data.getJSONObject("timings");

                    int[] serverMins = new int[6];
                    serverMins[0] = parse24ToMinutes(cleanTime(timings.getString("Fajr")));
                    serverMins[1] = parse24ToMinutes(cleanTime(timings.getString("Sunrise")));
                    serverMins[2] = parse24ToMinutes(cleanTime(timings.getString("Dhuhr")));
                    serverMins[3] = parse24ToMinutes(cleanTime(timings.getString("Asr")));
                    serverMins[4] = parse24ToMinutes(cleanTime(timings.getString("Maghrib")));
                    serverMins[5] = parse24ToMinutes(cleanTime(timings.getString("Isha")));

                    JSONObject hijriObj = data.getJSONObject("date").getJSONObject("hijri");
                    String hDay = hijriObj.getString("day");
                    String hYear = hijriObj.getString("year");
                    JSONObject monthObj = hijriObj.getJSONObject("month");
                    int hMonthNum = monthObj.optInt("number", 0);
                    boolean onlineRamadan = (hMonthNum == 9);
                    String hMonthAr = monthObj.getString("ar");
                    String hMonthEn = monthObj.getString("en");
                    String hijriAr = hDay + " " + hMonthAr + " " + hYear;
                    String hijriEn = hDay + " " + hMonthEn + " " + hYear + " AH";

                    int[] prayerOffs = new int[6];
                    for (int i = 0; i < 6; i++) {
                        prayerOffs[i] = (individualOffsets != null && individualOffsets.length > i) ? individualOffsets[i] : 0;
                    }

                    // Reconcile online timings with KACST GPS model inside Saudi Arabia for Umm al-Qura / KACST
                    String[] finalTimes = new String[6];
                    if (inSaudi && (method == METHOD_UMM_AL_QURA || method == METHOD_KACST)) {
                        Result kacstBase = calculateInternal(lat, lon, targetDate, 0, new int[]{0, 0, 0, 0, 0, 0},
                                method, english, onlineRamadan);
                        int[] kacstBaseMins = new int[6];
                        for (int i = 0; i < 6; i++) {
                            kacstBaseMins[i] = parse24ToMinutes(kacstBase.times24[i]);
                        }
                        for (int i = 0; i < 5; i++) {
                            int chosenBase = (Math.abs(serverMins[i] - kacstBaseMins[i]) <= 1)
                                    ? kacstBaseMins[i]
                                    : serverMins[i];
                            finalTimes[i] = format24(normalizeMinute(chosenBase + manualOffset + prayerOffs[i]));
                        }
                        // Official Saudi Isha is strictly Maghrib + 90 min (120 min in Ramadan)
                        int baseMaghrib = (Math.abs(serverMins[4] - kacstBaseMins[4]) <= 1)
                                ? kacstBaseMins[4]
                                : serverMins[4];
                        int ishaBase = baseMaghrib + (onlineRamadan ? 120 : 90);
                        finalTimes[5] = format24(normalizeMinute(ishaBase + manualOffset + prayerOffs[5]));
                    } else {
                        for (int i = 0; i < 6; i++) {
                            finalTimes[i] = format24(normalizeMinute(serverMins[i] + manualOffset + prayerOffs[i]));
                        }
                    }

                    Result res = new Result(finalTimes, english ? hijriEn : hijriAr, hijriEn, true, onlineRamadan);
                    if (callback != null) callback.onSuccess(res);
                } else {
                    Log.w(TAG, "Online prayer sync failed HTTP " + code);
                    if (callback != null) callback.onError(new IllegalStateException("HTTP " + code));
                }
            } catch (Throwable t) {
                Log.w(TAG, "Online prayer sync error: " + t.getMessage());
                if (callback != null) callback.onError(t);
            } finally {
                if (conn != null) {
                    try { conn.disconnect(); } catch (Throwable ignored) { }
                }
            }
        }, "UmmAlQura-OnlineSync").start();
    }

    public static boolean isInsideSaudiArabia(double latitude, double longitude) {
        return latitude >= 16.0 && latitude <= 32.5 && longitude >= 34.0 && longitude <= 56.0;
    }

    private static double normalizeDeg(double deg) {
        double r = deg % 360.0;
        return r < 0 ? r + 360.0 : r;
    }

    private static String cleanTime(String timeWithZone) {
        if (timeWithZone == null) return "--:--";
        String s = timeWithZone.trim();
        int space = s.indexOf(' ');
        if (space > 0) s = s.substring(0, space);
        return s;
    }

    private static int parse24ToMinutes(String time24) {
        if (time24 == null) return 0;
        try {
            String[] parts = time24.split(":");
            return Integer.parseInt(parts[0].trim()) * 60 + Integer.parseInt(parts[1].trim());
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static double hourAngleMinutes(double latitude, double declinationRad, double altitudeDeg) {
        double lat = Math.toRadians(latitude);
        double alt = Math.toRadians(altitudeDeg);
        double numerator = Math.sin(alt) - Math.sin(lat) * Math.sin(declinationRad);
        double denominator = Math.cos(lat) * Math.cos(declinationRad);
        double cosH = numerator / denominator;
        cosH = Math.max(-1.0, Math.min(1.0, cosH));
        return Math.toDegrees(Math.acos(cosH)) * 4.0;
    }

    private static int normalizeMinute(int minute) {
        int m = minute % 1440;
        return m < 0 ? m + 1440 : m;
    }

    private static String format24(int minute) {
        int h = (minute / 60) % 24;
        int m = minute % 60;
        return String.format(Locale.US, "%02d:%02d", h, m);
    }

    /**
     * Converts a Gregorian date into a Hijri date using the official Umm al-Qura standard.
     * Uses Android's native ICU IslamicCalendar (UMALQURA) on API 24+ for exact calendar alignment.
     * Returns an array of ints: [year, month, day].
     */
    public static int[] getUmmAlQuraHijriDate(Calendar local) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && local != null) {
            try {
                IslamicCalendar icuCal = new IslamicCalendar();
                icuCal.setCalculationType(IslamicCalendar.CalculationType.ISLAMIC_UMALQURA);
                icuCal.setTime(local.getTime());
                int hYear = icuCal.get(android.icu.util.Calendar.YEAR);
                int hMonth = icuCal.get(android.icu.util.Calendar.MONTH) + 1; // 1-based (1..12)
                int hDay = icuCal.get(android.icu.util.Calendar.DAY_OF_MONTH);
                return new int[]{hYear, hMonth, hDay};
            } catch (Throwable ignored) { }
        }
        if (local == null) local = Calendar.getInstance();
        return gregorianToHijri(
                local.get(Calendar.YEAR),
                local.get(Calendar.MONTH) + 1,
                local.get(Calendar.DAY_OF_MONTH));
    }

    /**
     * Fallback algorithmic calculation if ICU is unavailable.
     * Returns an array of ints: [year, month, day].
     */
    private static int[] gregorianToHijri(int year, int month, int day) {
        int m = month;
        int y = year;
        if (m < 3) {
            y -= 1;
            m += 12;
        }
        int a = y / 100;
        int b = 2 - a + a / 4;
        long jd = (long) (365.25 * (y + 4716)) + (long) (30.6001 * (m + 1)) + day + b - 1524;

        long l = jd - 1948440 + 10632;
        long n = (l - 1) / 10631;
        l = l - 10631 * n + 354;
        long j = ((10985 - l) / 5316) * ((50 * l) / 17719) + (l / 5670) * ((43 * l) / 15238);
        l = l - ((30 - j) / 15) * ((17719 * j) / 50) - (j / 16) * ((15238 * j) / 43) + 29;
        int hMonth = (int) ((24 * l) / 709);
        int hDay = (int) (l - (709 * hMonth) / 24);
        int hYear = (int) (30 * n + j - 30);
        return new int[]{hYear, hMonth, hDay};
    }

    /**
     * Converts a 24-hour time string ("17:25", "05:15", etc.) to 12-hour format with AM/PM or ص/م.
     */
    public static String format12Hour(String time24, boolean isEn) {
        if (time24 == null || time24.trim().isEmpty()) return "";
        try {
            String clean = time24.trim();
            String[] parts = clean.split(":");
            if (parts.length >= 2) {
                int h = Integer.parseInt(parts[0].replaceAll("[^0-9]", ""));
                int m = Integer.parseInt(parts[1].substring(0, Math.min(2, parts[1].length())).replaceAll("[^0-9]", ""));
                int h12 = h % 12;
                if (h12 == 0) h12 = 12;
                boolean isPm = (h >= 12);
                String suffix = isEn ? (isPm ? "PM" : "AM") : (isPm ? "م" : "ص");
                return String.format(Locale.US, "%d:%02d %s", h12, m, suffix);
            }
        } catch (Throwable ignored) { }
        return time24;
    }
}
