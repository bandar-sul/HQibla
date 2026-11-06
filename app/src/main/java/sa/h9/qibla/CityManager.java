package sa.h9.qibla;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;

import java.util.ArrayList;
import java.util.List;

/**
 * High-precision Saudi City database with official coordinates
 * used by Umm al-Qura University and Saudi Ministry of Islamic Affairs.
 */
public final class CityManager {
    public static final String KEY_SELECTED_CITY_INDEX = "selected_city_index";

    public static final class SaudiCity {
        public final String nameAr;
        public final String nameEn;
        public final double lat;
        public final double lon;

        public SaudiCity(String nameAr, String nameEn, double lat, double lon) {
            this.nameAr = nameAr;
            this.nameEn = nameEn;
            this.lat = lat;
            this.lon = lon;
        }
    }

    public static final SaudiCity[] CITIES = new SaudiCity[]{
            new SaudiCity("مكة المكرمة", "Makkah Al-Mukarramah", 21.4225, 39.8262),
            new SaudiCity("المدينة المنورة", "Madinah Al-Munawwarah", 24.4672, 39.6111),
            new SaudiCity("الرياض", "Riyadh", 24.7136, 46.6753),
            new SaudiCity("جدة", "Jeddah", 21.5433, 39.1728),
            new SaudiCity("الدمام", "Dammam", 26.4207, 50.0888),
            new SaudiCity("الخبر والظهران", "Khobar & Dhahran", 26.2818, 50.2083),
            new SaudiCity("الأحساء والهفوف", "Al-Ahsa & Hofuf", 25.3835, 49.5862),
            new SaudiCity("بريدة (القصيم)", "Buraidah (Al-Qassim)", 26.3592, 43.9818),
            new SaudiCity("عنيزة", "Onaizah", 26.0843, 43.9936),
            new SaudiCity("الرس", "Ar-Rass", 25.8694, 43.4973),
            new SaudiCity("الطائف", "Taif", 21.2854, 40.4222),
            new SaudiCity("أبها", "Abha", 18.2164, 42.5053),
            new SaudiCity("خميس مشيط", "Khamis Mushait", 18.3000, 42.7333),
            new SaudiCity("تبوك", "Tabuk", 28.3835, 36.5662),
            new SaudiCity("حائل", "Hail", 27.5219, 41.6907),
            new SaudiCity("جازان", "Jazan", 16.8892, 42.5511),
            new SaudiCity("نجران", "Najran", 17.4924, 44.1277),
            new SaudiCity("الباحة", "Al-Baha", 20.0129, 41.4677),
            new SaudiCity("سكاكا (الجوف)", "Sakaka (Al-Jowf)", 29.9697, 40.2064),
            new SaudiCity("عرعر (الحدود الشمالية)", "Arar (Northern Borders)", 30.9753, 41.0381),
            new SaudiCity("ينبع البحر", "Yanbu Al-Bahr", 24.0891, 38.0637),
            new SaudiCity("الجبيل الصناعية", "Jubail Industrial", 27.0046, 49.6606),
            new SaudiCity("حفر الباطن", "Hafar Al-Batin", 28.4328, 45.9708),
            new SaudiCity("الخرج", "Al-Kharj", 24.1500, 47.3000),
            new SaudiCity("وادي الدواسر", "Wadi Al-Dawasir", 20.4833, 44.8000),
            new SaudiCity("الدوادمي", "Al-Duwadimi", 24.5072, 44.3922),
            new SaudiCity("بيشة", "Bisha", 20.0000, 42.6000),
            new SaudiCity("الزلفي", "Al-Zulfi", 26.2994, 44.7844),
            new SaudiCity("المجمعة", "Al-Majma'ah", 25.9000, 45.3333),
            new SaudiCity("شقراء", "Shaqra", 25.2472, 45.2536),
            new SaudiCity("القريات", "Qurayyat", 31.3317, 37.3428)
    };

    private CityManager() {}

    public static String[] getCityOptions(boolean isEn) {
        String[] options = new String[CITIES.length + 1];
        options[0] = isEn ? "🛰️ Automatic Live GPS (Auto-Detect)" : "🛰️ تحديد تلقائي دقيق عبر GPS (الموقع الحي)";
        for (int i = 0; i < CITIES.length; i++) {
            SaudiCity c = CITIES[i];
            options[i + 1] = (isEn ? "📍 " : "📍 ") + (isEn ? c.nameEn : c.nameAr);
        }
        return options;
    }

    public static int getSelectedCityIndex(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(MainActivity.UI_PREFS, Context.MODE_PRIVATE);
        return sp.getInt(KEY_SELECTED_CITY_INDEX, 0); // Default to Auto GPS
    }

    public static void setSelectedCityIndex(Context ctx, int index) {
        SharedPreferences sp = ctx.getSharedPreferences(MainActivity.UI_PREFS, Context.MODE_PRIVATE);
        sp.edit().putInt(KEY_SELECTED_CITY_INDEX, Math.max(0, Math.min(CITIES.length, index))).apply();
    }

    public static SaudiCity getCityByIndex(int index) {
        if (index <= 0 || index > CITIES.length) return null;
        return CITIES[index - 1];
    }

    public static Location getManualLocation(Context ctx) {
        int idx = getSelectedCityIndex(ctx);
        if (idx <= 0 || idx > CITIES.length) return null;
        SaudiCity city = CITIES[idx - 1];
        Location loc = new Location("manual_city");
        loc.setLatitude(city.lat);
        loc.setLongitude(city.lon);
        loc.setAccuracy(1.0f);
        loc.setTime(System.currentTimeMillis());
        return loc;
    }

    public static String getManualCityName(Context ctx, boolean isEn) {
        int idx = getSelectedCityIndex(ctx);
        if (idx <= 0 || idx > CITIES.length) return null;
        SaudiCity city = CITIES[idx - 1];
        return isEn ? city.nameEn : city.nameAr;
    }

    public static SaudiCity findNearestCity(double lat, double lon) {
        SaudiCity best = CITIES[2]; // Riyadh default
        float minDistance = Float.MAX_VALUE;
        float[] res = new float[1];
        for (SaudiCity city : CITIES) {
            Location.distanceBetween(lat, lon, city.lat, city.lon, res);
            if (res[0] < minDistance) {
                minDistance = res[0];
                best = city;
            }
        }
        return best;
    }

    public static String resolveCityNameFromCoords(double lat, double lon, boolean isEn) {
        SaudiCity nearest = findNearestCity(lat, lon);
        float[] res = new float[1];
        Location.distanceBetween(lat, lon, nearest.lat, nearest.lon, res);
        if (res[0] <= 90_000f) { // Within 90 km of city center
            return isEn ? nearest.nameEn : nearest.nameAr;
        }
        return isEn ? (nearest.nameEn + " Area") : ("منطقة " + nearest.nameAr);
    }
}
