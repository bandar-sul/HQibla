package sa.h9.qibla;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Offline prayer-time calculator calibrated precisely for the official Umm al-Qura standard:
 * - Fajr = 18.5 degrees solar depression angle
 * - Sunrise = -0.8333 degrees (accounting for solar radius and atmospheric refraction)
 * - Dhuhr = Solar transit + 1 minute (ensuring the sun has passed the meridian)
 * - Asr = Standard Shafi/Hanbali shadow factor 1
 * - Maghrib = Sunset + 1 minute (ensuring sun disk is fully below the horizon)
 * - Isha = Exactly 90 minutes after Maghrib (120 minutes in Ramadan)
 *
 * Supports an optional user-calibration offset (+/- minutes) for local mosque synchronization.
 */
public final class PrayerTimesCalculator {
    private static final double FAJR_DEPRESSION_ANGLE = -18.5;
    private static final double SUNRISE_SUNSET_ANGLE = -0.8333;

    private PrayerTimesCalculator() { }

    public static final class Result {
        public final String[] times24;
        public final String hijriDate;
        public final String hijriDateEn;

        Result(String[] times24, String hijriDate, String hijriDateEn) {
            this.times24 = times24;
            this.hijriDate = hijriDate;
            this.hijriDateEn = hijriDateEn;
        }
    }

    public static Result calculate(double latitude, double longitude, Calendar date) {
        return calculate(latitude, longitude, date, 0, false);
    }

    public static Result calculate(double latitude, double longitude, Calendar date, int manualOffset) {
        return calculate(latitude, longitude, date, manualOffset, false);
    }

    public static Result calculate(double latitude, double longitude, Calendar date, boolean english) {
        return calculate(latitude, longitude, date, 0, english);
    }

    public static Result calculate(double latitude, double longitude, Calendar date, int manualOffset, boolean english) {
        Calendar local = (Calendar) date.clone();
        int dayOfYear = local.get(Calendar.DAY_OF_YEAR);

        // Compute solar parameters at local noon for high precision
        double approxNoonFraction = (dayOfYear - 1) + (12.0 - longitude / 15.0) / 24.0;
        double gamma = 2.0 * Math.PI / 365.0 * approxNoonFraction;

        // Equation of time in minutes
        double eqTime = 229.18 * (0.000075
                + 0.001868 * Math.cos(gamma)
                - 0.032077 * Math.sin(gamma)
                - 0.014615 * Math.cos(2 * gamma)
                - 0.040849 * Math.sin(2 * gamma));

        // Solar declination in radians
        double decl = 0.006918
                - 0.399912 * Math.cos(gamma)
                + 0.070257 * Math.sin(gamma)
                - 0.006758 * Math.cos(2 * gamma)
                + 0.000907 * Math.sin(2 * gamma)
                - 0.002697 * Math.cos(3 * gamma)
                + 0.001480 * Math.sin(3 * gamma);

        TimeZone zone = local.getTimeZone();
        int offsetMinutes = zone.getOffset(local.getTimeInMillis()) / 60000;
        double solarNoon = 720.0 - 4.0 * longitude - eqTime + offsetMinutes;

        double sunriseHourAngle = hourAngleMinutes(latitude, decl, SUNRISE_SUNSET_ANGLE);
        double fajrHourAngle = hourAngleMinutes(latitude, decl, FAJR_DEPRESSION_ANGLE);

        double declDeg = Math.toDegrees(decl);
        double asrAltitude = Math.toDegrees(Math.atan(1.0 /
                (1.0 + Math.tan(Math.toRadians(Math.abs(latitude - declDeg))))));
        double asrHourAngle = hourAngleMinutes(latitude, decl, asrAltitude);

        // Umm al-Qura standard alignments:
        // Fajr: +1.2 min calibration to match printed Umm al-Qura schedule
        int fajr = normalizeMinute((int) Math.round(solarNoon - fajrHourAngle + 1.2) + manualOffset);
        int sunrise = normalizeMinute((int) Math.round(solarNoon - sunriseHourAngle) + manualOffset);
        // Dhuhr: +1.0 min to ensure sun is strictly past zenith (Zawāl)
        int dhuhr = normalizeMinute((int) Math.round(solarNoon + 1.0) + manualOffset);
        // Asr: Standard shadow factor 1
        int asr = normalizeMinute((int) Math.round(solarNoon + asrHourAngle) + manualOffset);
        // Maghrib: Sunset + 0.8 min safe margin ensuring sun disk is entirely down
        int maghrib = normalizeMinute((int) Math.round(solarNoon + sunriseHourAngle + 0.8) + manualOffset);

        int[] hijri = gregorianToHijri(
                local.get(Calendar.YEAR),
                local.get(Calendar.MONTH) + 1,
                local.get(Calendar.DAY_OF_MONTH));
        boolean ramadan = hijri[1] == 9;
        // Isha is exactly 90 min after Maghrib (120 min in Ramadan) per Umm al-Qura standard
        int isha = normalizeMinute(maghrib + (ramadan ? 120 : 90));

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
        return new Result(times, english ? hijriTextEn : hijriTextAr, hijriTextEn);
    }

    private static double hourAngleMinutes(double latitude, double declinationRad, double altitudeDeg) {
        double lat = Math.toRadians(latitude);
        double alt = Math.toRadians(altitudeDeg);
        double numerator = Math.sin(alt) - Math.sin(lat) * Math.sin(declinationRad);
        double denominator = Math.cos(lat) * Math.cos(declinationRad);
        double cosH = numerator / denominator;
        cosH = Math.max(-1.0, min1(cosH));
        return Math.toDegrees(Math.acos(cosH)) * 4.0;
    }

    private static double min1(double v) {
        return v > 1.0 ? 1.0 : v;
    }

    private static int normalizeMinute(int minute) {
        minute %= 1440;
        if (minute < 0) minute += 1440;
        return minute;
    }

    private static String format24(int minute) {
        return String.format(Locale.US, "%02d:%02d", minute / 60, minute % 60);
    }

    // Civil/tabular Hijri conversion, used locally for the displayed date and Ramadan Isha offset.
    private static int[] gregorianToHijri(int year, int month, int day) {
        long jd = gregorianToJulianDay(year, month, day);
        long l = jd - 1948440L + 10632L;
        long n = (l - 1L) / 10631L;
        l = l - 10631L * n + 354L;
        long j = ((10985L - l) / 5316L) * ((50L * l) / 17719L)
                + (l / 5670L) * ((43L * l) / 15238L);
        l = l - ((30L - j) / 15L) * ((17719L * j) / 50L)
                - (j / 16L) * ((15238L * j) / 43L) + 29L;
        long m = (24L * l) / 709L;
        long d = l - (709L * m) / 24L;
        long y = 30L * n + j - 30L;
        return new int[]{(int) y, (int) m, (int) d};
    }

    private static long gregorianToJulianDay(int year, int month, int day) {
        int a = (14 - month) / 12;
        int y = year + 4800 - a;
        int m = month + 12 * a - 3;
        return day + (153L * m + 2) / 5 + 365L * y + y / 4 - y / 100 + y / 400 - 32045L;
    }
}
