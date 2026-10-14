package sa.h9.qibla;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Offline prayer-time calculator tuned for the Umm al-Qura convention:
 * Fajr = 18.5 degrees following the Umm al-Qura convention with a +2 minute calibration,
 * Sunrise = standard -0.833 degree horizon without a blanket offset,
 * Isha = 90 minutes after Maghrib (120 in Ramadan),
 * Asr = standard Shafi/Maliki/Hanbali shadow factor 1, with a one-minute
 * rounding calibration against the published Buraydah Umm al-Qura reference.
 */
public final class PrayerTimesCalculator {
    private static final int FAJR_UMM_AL_QURA_CORRECTION_MINUTES = 2;
    private static final int SUNRISE_CORRECTION_MINUTES = 0;
    private static final int NOON_CORRECTION_MINUTES = 1;
    private static final int ASR_UMM_AL_QURA_CORRECTION_MINUTES = 1;
    private PrayerTimesCalculator() { }

    public static final class Result {
        public final String[] times24;
        public final String hijriDate;

        Result(String[] times24, String hijriDate) {
            this.times24 = times24;
            this.hijriDate = hijriDate;
        }
    }

    public static Result calculate(double latitude, double longitude, Calendar date) {
        Calendar local = (Calendar) date.clone();
        int dayOfYear = local.get(Calendar.DAY_OF_YEAR);
        double gamma = 2.0 * Math.PI / 365.0 * (dayOfYear - 1);

        double eqTime = 229.18 * (0.000075
                + 0.001868 * Math.cos(gamma)
                - 0.032077 * Math.sin(gamma)
                - 0.014615 * Math.cos(2 * gamma)
                - 0.040849 * Math.sin(2 * gamma));

        double decl = 0.006918
                - 0.399912 * Math.cos(gamma)
                + 0.070257 * Math.sin(gamma)
                - 0.006758 * Math.cos(2 * gamma)
                + 0.000907 * Math.sin(2 * gamma)
                - 0.002697 * Math.cos(3 * gamma)
                + 0.00148 * Math.sin(3 * gamma);

        TimeZone zone = local.getTimeZone();
        int offsetMinutes = zone.getOffset(local.getTimeInMillis()) / 60000;
        double solarNoon = 720.0 - 4.0 * longitude - eqTime + offsetMinutes;

        double sunriseHourAngle = hourAngleMinutes(latitude, decl, -0.833);
        double fajrHourAngle = hourAngleMinutes(latitude, decl, -18.5);

        double declDeg = Math.toDegrees(decl);
        double asrAltitude = Math.toDegrees(Math.atan(1.0 /
                (1.0 + Math.tan(Math.toRadians(Math.abs(latitude - declDeg))))));
        double asrHourAngle = hourAngleMinutes(latitude, decl, asrAltitude);

        int fajr = normalizeMinute((int) Math.round(solarNoon - fajrHourAngle)
                + FAJR_UMM_AL_QURA_CORRECTION_MINUTES);
        int sunrise = normalizeMinute((int) Math.round(solarNoon - sunriseHourAngle)
                + SUNRISE_CORRECTION_MINUTES);
        int dhuhr = normalizeMinute((int) Math.round(solarNoon) + NOON_CORRECTION_MINUTES);
        int asr = normalizeMinute((int) Math.round(solarNoon + asrHourAngle)
                + ASR_UMM_AL_QURA_CORRECTION_MINUTES);
        int maghrib = normalizeMinute((int) Math.round(solarNoon + sunriseHourAngle));

        int[] hijri = gregorianToHijri(
                local.get(Calendar.YEAR),
                local.get(Calendar.MONTH) + 1,
                local.get(Calendar.DAY_OF_MONTH));
        boolean ramadan = hijri[1] == 9;
        int isha = normalizeMinute(maghrib + (ramadan ? 120 : 90));

        String[] times = {
                format24(fajr),
                format24(sunrise),
                format24(dhuhr),
                format24(asr),
                format24(maghrib),
                format24(isha)
        };

        String[] months = {
                "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
                "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة"
        };
        String hijriText = hijri[2] + " " + months[Math.max(0, Math.min(11, hijri[1] - 1))] + " " + hijri[0];
        return new Result(times, hijriText);
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
