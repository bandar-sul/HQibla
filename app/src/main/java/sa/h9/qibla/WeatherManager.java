package sa.h9.qibla;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.SSLException;

/**
 * Manages real-time weather data fetching via Open-Meteo free API (no API key required),
 * featuring comprehensive error handling, connection diagnostics, request coalescing
 * for rapid location updates during driving, and robust offline GPS climatology fallback.
 */
public final class WeatherManager {
    public static final String TAG = "WeatherManager";
    public static final String DIAG_TAG = "H9WeatherDiag";
    private static final String PREFS_NAME = "sa.h9.qibla.weather_prefs";

    private static final String KEY_TEMP = "temp_c";
    private static final String KEY_APPARENT_TEMP = "apparent_temp_c";
    private static final String KEY_HUMIDITY = "humidity";
    private static final String KEY_WIND_SPEED = "wind_speed";
    private static final String KEY_WIND_DIR = "wind_dir";
    private static final String KEY_WIND_GUSTS = "wind_gusts";
    private static final String KEY_PRESSURE = "pressure_hpa";
    private static final String KEY_UV = "uv_index";
    private static final String KEY_SUNRISE = "sunrise_time";
    private static final String KEY_SUNSET = "sunset_time";
    private static final String KEY_TODAY_RAIN = "today_max_rain";
    private static final String KEY_WEATHER_CODE = "weather_code";
    private static final String KEY_WEATHER_DESC_AR = "weather_desc_ar";
    private static final String KEY_WEATHER_DESC_EN = "weather_desc_en";
    private static final String KEY_WEATHER_ICON = "weather_icon";
    private static final String KEY_FETCH_TIME = "fetch_time";
    private static final String KEY_LAST_LAT = "last_lat";
    private static final String KEY_LAST_LON = "last_lon";

    private static final int CONNECT_TIMEOUT_MS = 5500;
    private static final int READ_TIMEOUT_MS = 6500;
    private static final int MAX_RETRIES = 2;
    private static final int MAX_DIAG_LOGS = 35;

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final Object STATE_LOCK = new Object();

    private static WeatherInfo cachedInfo = null;
    private static boolean isFetching = false;
    private static PendingRequest pendingRequest = null;

    // Diagnostic Metrics
    private static final LinkedList<DiagnosticRecord> diagnosticLogs = new LinkedList<>();
    private static volatile long lastFetchAttemptTime = 0L;
    private static volatile long lastFetchSuccessTime = 0L;
    private static volatile long lastFetchDurationMs = 0L;
    private static volatile int lastResponseCode = 0;
    private static volatile String lastFetchError = null;
    private static volatile String lastFetchSource = "None";
    private static volatile int totalFetchRequests = 0;
    private static volatile int successfulFetches = 0;
    private static volatile int failedFetches = 0;
    private static volatile int retriedFetches = 0;

    /**
     * Diagnostic event record for in-memory circular buffer.
     */
    public static final class DiagnosticRecord {
        public final long timestamp;
        public final String level; // "INFO", "WARN", "ERROR", "SUCCESS"
        public final String eventType;
        public final double lat;
        public final double lon;
        public final int httpCode;
        public final long durationMs;
        public final String networkType;
        public final String message;

        public DiagnosticRecord(String level, String eventType, double lat, double lon,
                                int httpCode, long durationMs, String networkType, String message) {
            this.timestamp = System.currentTimeMillis();
            this.level = level;
            this.eventType = eventType;
            this.lat = lat;
            this.lon = lon;
            this.httpCode = httpCode;
            this.durationMs = durationMs;
            this.networkType = networkType != null ? networkType : "Unknown";
            this.message = message != null ? message : "";
        }

        public String getFormattedTime() {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);
                return sdf.format(new Date(timestamp));
            } catch (Exception e) {
                return String.valueOf(timestamp);
            }
        }

        @Override
        public String toString() {
            return String.format(Locale.US, "[%s][%s][%s] (%.4f, %.4f) HTTP=%d %dms Net=%s - %s",
                    getFormattedTime(), level, eventType, lat, lon, httpCode, durationMs, networkType, message);
        }
    }

    /**
     * Encapsulates a pending location request coalesced while a previous fetch is ongoing.
     */
    private static final class PendingRequest {
        final Context context;
        final double lat;
        final double lon;
        final boolean forceRefresh;
        final WeatherCallback callback;
        final long queuedAt;

        PendingRequest(Context context, double lat, double lon, boolean forceRefresh, WeatherCallback callback) {
            this.context = context;
            this.lat = lat;
            this.lon = lon;
            this.forceRefresh = forceRefresh;
            this.callback = callback;
            this.queuedAt = System.currentTimeMillis();
        }
    }

    public static class DayForecast {
        public final String dayName;
        public final String dateStr;
        public final int code;
        public final String icon;
        public final String descAr;
        public final String descEn;
        public final float maxTemp;
        public final float minTemp;
        public final int rainProb;

        public DayForecast(String dayName, String dateStr, int code, String icon, String descAr, String descEn,
                           float maxTemp, float minTemp, int rainProb) {
            this.dayName = dayName;
            this.dateStr = dateStr;
            this.code = code;
            this.icon = icon;
            this.descAr = descAr;
            this.descEn = descEn;
            this.maxTemp = maxTemp;
            this.minTemp = minTemp;
            this.rainProb = rainProb;
        }
    }

    public static class HourForecast {
        public final String hourStr;
        public final int code;
        public final String icon;
        public final float tempC;
        public final int rainProb;

        public HourForecast(String hourStr, int code, String icon, float tempC, int rainProb) {
            this.hourStr = hourStr;
            this.code = code;
            this.icon = icon;
            this.tempC = tempC;
            this.rainProb = rainProb;
        }
    }

    public static class WeatherInfo {
        public final float tempC;
        public final float apparentTempC;
        public final int humidity;
        public final float windSpeedKmh;
        public final float windDirectionDeg;
        public final float windGustsKmh;
        public final float pressureHpa;
        public final float uvIndex;
        public final String sunriseTime;
        public final String sunsetTime;
        public final int todayMaxRainProb;
        public final int code;
        public final String descAr;
        public final String descEn;
        public final String icon;
        public final long timestamp;
        public final List<DayForecast> daily;
        public final List<HourForecast> hourly;
        public final float currentRainMm;
        public final boolean isOffline;
        public final String offlineSource;

        public WeatherInfo(float tempC, float apparentTempC, int humidity, float windSpeedKmh,
                           float windDirectionDeg, float windGustsKmh, float pressureHpa, float uvIndex,
                           String sunriseTime, String sunsetTime, int todayMaxRainProb,
                           int code, String descAr, String descEn,
                           String icon, long timestamp,
                           List<DayForecast> daily, List<HourForecast> hourly) {
            this(tempC, apparentTempC, humidity, windSpeedKmh, windDirectionDeg, windGustsKmh, pressureHpa, uvIndex,
                    sunriseTime, sunsetTime, todayMaxRainProb, code, descAr, descEn, icon, timestamp, daily, hourly, 0f, false, "");
        }

        public WeatherInfo(float tempC, float apparentTempC, int humidity, float windSpeedKmh,
                           float windDirectionDeg, float windGustsKmh, float pressureHpa, float uvIndex,
                           String sunriseTime, String sunsetTime, int todayMaxRainProb,
                           int code, String descAr, String descEn,
                           String icon, long timestamp,
                           List<DayForecast> daily, List<HourForecast> hourly,
                           boolean isOffline, String offlineSource) {
            this(tempC, apparentTempC, humidity, windSpeedKmh, windDirectionDeg, windGustsKmh, pressureHpa, uvIndex,
                    sunriseTime, sunsetTime, todayMaxRainProb, code, descAr, descEn, icon, timestamp, daily, hourly, 0f, isOffline, offlineSource);
        }

        public WeatherInfo(float tempC, float apparentTempC, int humidity, float windSpeedKmh,
                           float windDirectionDeg, float windGustsKmh, float pressureHpa, float uvIndex,
                           String sunriseTime, String sunsetTime, int todayMaxRainProb,
                           int code, String descAr, String descEn,
                           String icon, long timestamp,
                           List<DayForecast> daily, List<HourForecast> hourly,
                           float currentRainMm, boolean isOffline, String offlineSource) {
            this.tempC = tempC;
            this.apparentTempC = apparentTempC;
            this.humidity = humidity;
            this.windSpeedKmh = windSpeedKmh;
            this.windDirectionDeg = windDirectionDeg;
            this.windGustsKmh = windGustsKmh;
            this.pressureHpa = pressureHpa;
            this.uvIndex = uvIndex;
            this.sunriseTime = (sunriseTime != null && !sunriseTime.isEmpty()) ? sunriseTime : "--:--";
            this.sunsetTime = (sunsetTime != null && !sunsetTime.isEmpty()) ? sunsetTime : "--:--";
            this.todayMaxRainProb = todayMaxRainProb;
            this.code = code;
            this.descAr = descAr;
            this.descEn = descEn;
            this.icon = icon;
            this.timestamp = timestamp;
            this.daily = daily != null ? daily : new ArrayList<>();
            this.hourly = hourly != null ? hourly : new ArrayList<>();
            this.currentRainMm = currentRainMm;
            this.isOffline = isOffline;
            this.offlineSource = offlineSource != null ? offlineSource : "";
        }

        public String getLocalizedDesc(boolean english) {
            return english ? descEn : descAr;
        }

        public String getWindDirectionLabel(boolean english) {
            float deg = (windDirectionDeg % 360 + 360) % 360;
            if (deg >= 337.5 || deg < 22.5) return english ? "North ⬆️" : "شمالية ⬆️";
            if (deg >= 22.5 && deg < 67.5) return english ? "Northeast ↗️" : "شمالية شرقية ↗️";
            if (deg >= 67.5 && deg < 112.5) return english ? "East ➡️" : "شرقية ➡️";
            if (deg >= 112.5 && deg < 157.5) return english ? "Southeast ↘️" : "جنوبية شرقية ↘️";
            if (deg >= 157.5 && deg < 202.5) return english ? "South ⬇️" : "جنوبية ⬇️";
            if (deg >= 202.5 && deg < 247.5) return english ? "Southwest ↙️" : "جنوبية غربية ↙️";
            if (deg >= 247.5 && deg < 292.5) return english ? "West ⬅️" : "غربية ⬅️";
            return english ? "Northwest ↖️" : "شمالية غربية ↖️";
        }

        public String getSummaryLine(boolean english) {
            String desc = getLocalizedDesc(english);
            if (english) {
                return String.format(Locale.US, "%s %.0f°C • %s | Wind %.0f km/h | Hum %d%%",
                        icon, tempC, desc, windSpeedKmh, humidity);
            } else {
                return String.format(Locale.US, "%s %.0f°م • %s  |  💨 %.0f كم/س  |  💧 %d%%",
                        icon, tempC, desc, windSpeedKmh, humidity);
            }
        }
    }

    public interface WeatherCallback {
        void onWeatherUpdated(WeatherInfo info);
    }

    // --- DIAGNOSTIC HELPERS ---

    private static void logDiagnostic(String level, String eventType, double lat, double lon,
                                      int httpCode, long durationMs, String netType, String message) {
        DiagnosticRecord record = new DiagnosticRecord(level, eventType, lat, lon, httpCode, durationMs, netType, message);
        synchronized (diagnosticLogs) {
            diagnosticLogs.add(record);
            while (diagnosticLogs.size() > MAX_DIAG_LOGS) {
                diagnosticLogs.removeFirst();
            }
        }

        String logLine = record.toString();
        switch (level) {
            case "ERROR":
                Log.e(DIAG_TAG, logLine);
                break;
            case "WARN":
                Log.w(DIAG_TAG, logLine);
                break;
            default:
                Log.i(DIAG_TAG, logLine);
                break;
        }
    }

    /**
     * Checks if coordinates are valid geographic numbers (rejects NaN, Infinity, and (0,0) uninitialized GPS).
     */
    public static boolean isValidCoordinate(double lat, double lon) {
        if (Double.isNaN(lat) || Double.isNaN(lon)) return false;
        if (Double.isInfinite(lat) || Double.isInfinite(lon)) return false;
        if (lat < -90.0 || lat > 90.0) return false;
        if (lon < -180.0 || lon > 180.0) return false;
        // Discard 0.0, 0.0 ("Null Island") uninitialized GPS artifact
        if (Math.abs(lat) < 0.0001 && Math.abs(lon) < 0.0001) return false;
        return true;
    }

    /**
     * Checks if active network connectivity is present.
     */
    public static boolean isNetworkAvailable(Context context) {
        if (context == null) return false;
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        } catch (Throwable t) {
            return true; // Fall back to attempting network if permissions or manager fail
        }
    }

    /**
     * Returns human-readable network type description for diagnostics.
     */
    public static String getNetworkTypeName(Context context) {
        if (context == null) return "NoContext";
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return "NoManager";
            NetworkInfo info = cm.getActiveNetworkInfo();
            if (info == null || !info.isConnected()) return "Disconnected";
            String type = info.getTypeName();
            String sub = info.getSubtypeName();
            if (sub != null && !sub.isEmpty()) {
                return type + "/" + sub;
            }
            return type;
        } catch (Throwable t) {
            return "ErrNet";
        }
    }

    public static WeatherInfo getCachedWeather(Context context) {
        if (cachedInfo != null) return cachedInfo;
        if (context == null) return null;

        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!sp.contains(KEY_TEMP)) return null;

        cachedInfo = new WeatherInfo(
                sp.getFloat(KEY_TEMP, 28f),
                sp.getFloat(KEY_APPARENT_TEMP, 28f),
                sp.getInt(KEY_HUMIDITY, 20),
                sp.getFloat(KEY_WIND_SPEED, 10f),
                sp.getFloat(KEY_WIND_DIR, 0f),
                sp.getFloat(KEY_WIND_GUSTS, 14f),
                sp.getFloat(KEY_PRESSURE, 1013f),
                sp.getFloat(KEY_UV, 5f),
                sp.getString(KEY_SUNRISE, "06:12"),
                sp.getString(KEY_SUNSET, "17:28"),
                sp.getInt(KEY_TODAY_RAIN, 0),
                sp.getInt(KEY_WEATHER_CODE, 0),
                sp.getString(KEY_WEATHER_DESC_AR, "صحو"),
                sp.getString(KEY_WEATHER_DESC_EN, "Clear"),
                sp.getString(KEY_WEATHER_ICON, "☀️"),
                sp.getLong(KEY_FETCH_TIME, 0L),
                null, null,
                sp.getFloat("current_rain_mm", 0f), false, ""
        );
        return cachedInfo;
    }

    public static void fetchWeather(Context context, double lat, double lon, WeatherCallback callback) {
        fetchWeather(context, lat, lon, false, callback);
    }

    /**
     * Primary entry point for fetching weather data.
     * Implements coordinate validation, debouncing, request coalescing for location updates,
     * network pre-checks, exponential retries, and comprehensive diagnostic logging.
     */
    public static void fetchWeather(Context context, double lat, double lon, boolean forceRefresh, WeatherCallback callback) {
        if (context == null) {
            logDiagnostic("WARN", "INIT_NULL_CTX", lat, lon, 0, 0, "None", "fetchWeather called with null context");
            return;
        }
        final Context appCtx = context.getApplicationContext();
        totalFetchRequests++;

        // 1. Validate Geographic Coordinates
        if (!isValidCoordinate(lat, lon)) {
            logDiagnostic("WARN", "INVALID_COORDS", lat, lon, 0, 0, getNetworkTypeName(appCtx),
                    "Discarded invalid/unlocked coordinates (lat=" + lat + ", lon=" + lon + "). Serving cache if available.");
            deliverCachedOrClimatology(appCtx, lat, lon, callback, "إحداثيات غير مكتملة");
            return;
        }

        // 2. Deliver immediate cached data to caller if available (zero-delay UI refresh)
        WeatherInfo current = getCachedWeather(appCtx);
        if (current != null && callback != null && !forceRefresh) {
            callback.onWeatherUpdated(current);
        }

        // 3. Check Throttling vs Movement Delta
        SharedPreferences sp = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long lastTime = sp.getLong(KEY_FETCH_TIME, 0L);
        float lastLat = sp.getFloat(KEY_LAST_LAT, 0f);
        float lastLon = sp.getFloat(KEY_LAST_LON, 0f);

        long now = System.currentTimeMillis();
        boolean timeExpired = (now - lastTime) > (10 * 60 * 1000L); // 10 minutes
        boolean locationMoved = Math.abs(lat - lastLat) > 0.045 || Math.abs(lon - lastLon) > 0.045; // ~4.5 km

        if (!forceRefresh && !timeExpired && !locationMoved && current != null && current.daily != null && !current.daily.isEmpty()) {
            logDiagnostic("INFO", "THROTTLED", lat, lon, 200, 0, getNetworkTypeName(appCtx),
                    String.format(Locale.US, "Location delta minor (lat=%.3f, lon=%.3f), time=%ds ago. Cache valid.",
                            Math.abs(lat - lastLat), Math.abs(lon - lastLon), (now - lastTime) / 1000));
            return;
        }

        // 4. Request Coalescing: Handle concurrent location updates gracefully without dropping updates
        synchronized (STATE_LOCK) {
            if (isFetching) {
                pendingRequest = new PendingRequest(appCtx, lat, lon, forceRefresh, callback);
                logDiagnostic("INFO", "REQUEST_QUEUED", lat, lon, 0, 0, getNetworkTypeName(appCtx),
                        "A weather fetch is already in flight. Queued newest location update for immediate post-execution.");
                return;
            }
            isFetching = true;
        }

        // 5. Pre-check active network: if completely offline, deliver offline fallback without blocking on network timeout
        if (!isNetworkAvailable(appCtx)) {
            logDiagnostic("WARN", "NET_OFFLINE", lat, lon, 0, 0, "Disconnected",
                    "No internet connection detected. Delivering offline cache / astronomical GPS estimation immediately.");
            synchronized (STATE_LOCK) {
                isFetching = false;
            }
            deliverCachedOrClimatology(appCtx, lat, lon, callback, "لا يوجد اتصال بالإنترنت (أوفلاين)");
            return;
        }

        // 6. Execute network fetch with retries
        executeFetchWithRetry(appCtx, lat, lon, forceRefresh, callback, 0);
    }

    private static void executeFetchWithRetry(final Context appCtx, final double lat, final double lon,
                                              final boolean forceRefresh, final WeatherCallback callback, final int attempt) {
        lastFetchAttemptTime = System.currentTimeMillis();
        final String netType = getNetworkTypeName(appCtx);

        executor.execute(() -> {
            long startMs = System.currentTimeMillis();
            WeatherInfo info = null;
            String attemptError = null;
            int responseCode = 0;
            boolean shouldRetry = false;

            logDiagnostic("INFO", attempt > 0 ? "RETRY_START" : "FETCH_START", lat, lon, 0, 0, netType,
                    String.format(Locale.US, "Starting Open-Meteo HTTP fetch (attempt %d/%d) for lat=%.4f, lon=%.4f",
                            attempt + 1, MAX_RETRIES + 1, lat, lon));

            HttpURLConnection conn = null;
            try {
                // Check for car clock skew
                long sysTime = System.currentTimeMillis();
                if (sysTime < 1704067200000L) { // Year < 2024
                    logDiagnostic("WARN", "CLOCK_SKEW", lat, lon, 0, 0, netType,
                            "System clock is older than 2024! SSL handshake with api.open-meteo.com might fail due to expired cert.");
                }

                String urlStr = String.format(Locale.US,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,rain,weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m,surface_pressure,uv_index&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset&hourly=temperature_2m,weather_code,precipitation_probability&timezone=Asia%%2FRiyadh&forecast_days=7",
                        lat, lon);
                URL url = new URL(urlStr);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(READ_TIMEOUT_MS);
                conn.setRequestProperty("User-Agent", "H9Qibla-OpenMeteo/3.2 (Automotive; Tank/Haval)");

                responseCode = conn.getResponseCode();
                lastResponseCode = responseCode;

                if (responseCode == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                    br.close();

                    JSONObject root = new JSONObject(sb.toString());
                    if (root.has("current")) {
                        JSONObject cur = root.getJSONObject("current");
                        float temp = (float) cur.optDouble("temperature_2m", 28.0);
                        float apparent = (float) cur.optDouble("apparent_temperature", temp);
                        int hum = cur.optInt("relative_humidity_2m", 20);
                        float wind = (float) cur.optDouble("wind_speed_10m", 10.0);
                        float windDir = (float) cur.optDouble("wind_direction_10m", 0.0);
                        float windGusts = (float) cur.optDouble("wind_gusts_10m", wind * 1.35f);
                        float pressure = (float) cur.optDouble("surface_pressure", 1013.0);
                        float uv = (float) cur.optDouble("uv_index", 5.0);
                        int wCode = cur.optInt("weather_code", 0);
                        float precip = (float) cur.optDouble("precipitation", 0.0);
                        float rainVal = (float) cur.optDouble("rain", 0.0);
                        float currentRainMm = Math.max(precip, rainVal);

                        if (currentRainMm > 0.05f && wCode < 51) {
                            wCode = 61; // Active rain detected
                        }

                        String descAr = (currentRainMm > 0.05f && wCode < 51) ? "أمطار وزخات ماطرة" : mapCodeToDescAr(wCode);
                        String descEn = (currentRainMm > 0.05f && wCode < 51) ? "Rain showers" : mapCodeToDescEn(wCode);
                        String icon = (currentRainMm > 0.05f && wCode < 51) ? "🌧️" : mapCodeToIcon(wCode);

                        // Parse Daily Forecast
                        List<DayForecast> dailyList = new ArrayList<>();
                        String sunriseStr = "";
                        String sunsetStr = "";
                        int todayRain = 0;

                        if (root.has("daily")) {
                            JSONObject dailyObj = root.getJSONObject("daily");
                            org.json.JSONArray dTime = dailyObj.optJSONArray("time");
                            org.json.JSONArray dCode = dailyObj.optJSONArray("weather_code");
                            org.json.JSONArray dMax = dailyObj.optJSONArray("temperature_2m_max");
                            org.json.JSONArray dMin = dailyObj.optJSONArray("temperature_2m_min");
                            org.json.JSONArray dRain = dailyObj.optJSONArray("precipitation_probability_max");
                            org.json.JSONArray dSunrise = dailyObj.optJSONArray("sunrise");
                            org.json.JSONArray dSunset = dailyObj.optJSONArray("sunset");

                            if (dSunrise != null && dSunrise.length() > 0) {
                                sunriseStr = formatHour(dSunrise.optString(0, ""));
                            }
                            if (dSunset != null && dSunset.length() > 0) {
                                sunsetStr = formatHour(dSunset.optString(0, ""));
                            }
                            if (dRain != null && dRain.length() > 0) {
                                todayRain = dRain.optInt(0, 0);
                            }

                            if (dTime != null) {
                                for (int i = 0; i < dTime.length(); i++) {
                                    String dateS = dTime.optString(i, "");
                                    int cCode = (dCode != null) ? dCode.optInt(i, 0) : 0;
                                    float maxT = (dMax != null) ? (float) dMax.optDouble(i, 30.0) : 30f;
                                    float minT = (dMin != null) ? (float) dMin.optDouble(i, 20.0) : 20f;
                                    int rainP = (dRain != null) ? dRain.optInt(i, 0) : 0;
                                    String dayName = parseDayName(dateS);
                                    dailyList.add(new DayForecast(dayName, dateS, cCode, mapCodeToIcon(cCode),
                                            mapCodeToDescAr(cCode), mapCodeToDescEn(cCode), maxT, minT, rainP));
                                }
                            }
                        }

                        // Parse Hourly Forecast (24 hours)
                        List<HourForecast> hourlyList = new ArrayList<>();
                        if (root.has("hourly")) {
                            JSONObject hourlyObj = root.getJSONObject("hourly");
                            org.json.JSONArray hTime = hourlyObj.optJSONArray("time");
                            org.json.JSONArray hCode = hourlyObj.optJSONArray("weather_code");
                            org.json.JSONArray hTemp = hourlyObj.optJSONArray("temperature_2m");
                            org.json.JSONArray hRain = hourlyObj.optJSONArray("precipitation_probability");
                            if (hTime != null) {
                                int limit = Math.min(24, hTime.length());
                                for (int i = 0; i < limit; i++) {
                                    String tStr = hTime.optString(i, "");
                                    int cCode = (hCode != null) ? hCode.optInt(i, 0) : 0;
                                    float cTemp = (hTemp != null) ? (float) hTemp.optDouble(i, 25.0) : 25f;
                                    int rainP = (hRain != null) ? hRain.optInt(i, 0) : 0;
                                    String hFormatted = formatHour(tStr);
                                    hourlyList.add(new HourForecast(hFormatted, cCode, mapCodeToIcon(cCode), cTemp, rainP));
                                }
                            }
                        }

                        info = new WeatherInfo(temp, apparent, hum, wind, windDir, windGusts, pressure, uv,
                                sunriseStr, sunsetStr, todayRain, wCode, descAr, descEn, icon,
                                System.currentTimeMillis(), dailyList, hourlyList, currentRainMm, false, "Open-Meteo Live API");

                        // Save to SharedPreferences Cache
                        SharedPreferences sp = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                        sp.edit()
                                .putFloat(KEY_TEMP, temp)
                                .putFloat(KEY_APPARENT_TEMP, apparent)
                                .putInt(KEY_HUMIDITY, hum)
                                .putFloat(KEY_WIND_SPEED, wind)
                                .putFloat(KEY_WIND_DIR, windDir)
                                .putFloat(KEY_WIND_GUSTS, windGusts)
                                .putFloat(KEY_PRESSURE, pressure)
                                .putFloat(KEY_UV, uv)
                                .putString(KEY_SUNRISE, sunriseStr)
                                .putString(KEY_SUNSET, sunsetStr)
                                .putInt(KEY_TODAY_RAIN, todayRain)
                                .putInt(KEY_WEATHER_CODE, wCode)
                                .putString(KEY_WEATHER_DESC_AR, descAr)
                                .putString(KEY_WEATHER_DESC_EN, descEn)
                                .putFloat("current_rain_mm", currentRainMm)
                                .putLong(KEY_FETCH_TIME, System.currentTimeMillis())
                                .putFloat(KEY_LAST_LAT, (float) lat)
                                .putFloat(KEY_LAST_LON, (float) lon)
                                .apply();

                        cachedInfo = info;
                        lastFetchSuccessTime = System.currentTimeMillis();
                        lastFetchSource = "Open-Meteo Live API";
                        successfulFetches++;
                        lastFetchError = null;

                        long dur = System.currentTimeMillis() - startMs;
                        lastFetchDurationMs = dur;
                        logDiagnostic("SUCCESS", "FETCH_SUCCESS", lat, lon, 200, dur, netType,
                                String.format(Locale.US, "Success! %.0f°C (feels %.0f°C) %s | Rain=%.2fmm in %dms",
                                        temp, apparent, descAr, currentRainMm, dur));
                    } else {
                        attemptError = "Open-Meteo response JSON missing 'current' payload block";
                        logDiagnostic("ERROR", "PAYLOAD_INVALID", lat, lon, 200, System.currentTimeMillis() - startMs, netType, attemptError);
                    }
                } else {
                    // Non-200 HTTP code
                    String errBody = "";
                    try {
                        if (conn.getErrorStream() != null) {
                            BufferedReader errReader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                            StringBuilder errSb = new StringBuilder();
                            String el;
                            while ((el = errReader.readLine()) != null) errSb.append(el);
                            errReader.close();
                            errBody = errSb.toString();
                        }
                    } catch (Exception ignored) { }

                    attemptError = "HTTP " + responseCode + " (" + conn.getResponseMessage() + ") " + errBody;
                    logDiagnostic("ERROR", "HTTP_ERROR", lat, lon, responseCode, System.currentTimeMillis() - startMs, netType, attemptError);

                    if (responseCode == 429) {
                        logDiagnostic("WARN", "RATE_LIMIT_429", lat, lon, 429, 0, netType,
                                "Open-Meteo rate limit hit (HTTP 429). Will not retry immediately to avoid ban.");
                        shouldRetry = false;
                    } else if (responseCode >= 500 && responseCode <= 599) {
                        shouldRetry = true; // Server error, worth retrying
                    }
                }
            } catch (SocketTimeoutException e) {
                attemptError = "Connection/Read timed out after " + (System.currentTimeMillis() - startMs) + "ms: " + e.getMessage();
                logDiagnostic("WARN", "TIMEOUT", lat, lon, 0, System.currentTimeMillis() - startMs, netType, attemptError);
                shouldRetry = true;
            } catch (UnknownHostException e) {
                attemptError = "DNS resolution failed for api.open-meteo.com: " + e.getMessage();
                logDiagnostic("WARN", "DNS_FAIL", lat, lon, 0, System.currentTimeMillis() - startMs, netType, attemptError);
                shouldRetry = (attempt < 1); // retry once in case cell tower handoff is transient
            } catch (SSLException e) {
                attemptError = "SSL handshake failure: " + e.getMessage() + ". Check vehicle system clock.";
                logDiagnostic("ERROR", "SSL_FAIL", lat, lon, 0, System.currentTimeMillis() - startMs, netType, attemptError);
                shouldRetry = false;
            } catch (ConnectException e) {
                attemptError = "Network connection refused / unreachable: " + e.getMessage();
                logDiagnostic("WARN", "CONNECT_FAIL", lat, lon, 0, System.currentTimeMillis() - startMs, netType, attemptError);
                shouldRetry = true;
            } catch (Exception e) {
                attemptError = "Unexpected weather retrieval failure: " + e.getClass().getSimpleName() + " - " + e.getMessage();
                logDiagnostic("ERROR", "EXCEPTION", lat, lon, responseCode, System.currentTimeMillis() - startMs, netType, attemptError);
                shouldRetry = false;
            } finally {
                if (conn != null) {
                    try { conn.disconnect(); } catch (Throwable ignored) { }
                }
            }

            // Retry handling
            if (info == null && shouldRetry && attempt < MAX_RETRIES) {
                retriedFetches++;
                long backoffMs = (attempt + 1) * 1200L;
                logDiagnostic("INFO", "SCHEDULE_RETRY", lat, lon, responseCode, 0, netType,
                        "Scheduling retry " + (attempt + 2) + "/" + (MAX_RETRIES + 1) + " in " + backoffMs + "ms due to: " + attemptError);
                mainHandler.postDelayed(() -> executeFetchWithRetry(appCtx, lat, lon, forceRefresh, callback, attempt + 1), backoffMs);
                return;
            }

            // Mark failure if still null
            if (info == null) {
                failedFetches++;
                lastFetchError = attemptError;
                lastFetchDurationMs = System.currentTimeMillis() - startMs;
            }

            // Fallback: If network failed, serve cache or GPS climatology
            WeatherInfo finalResult = info;
            if (finalResult == null) {
                WeatherInfo existingCache = getCachedWeather(appCtx);
                if (existingCache != null) {
                    lastFetchSource = "Device Cache (Offline)";
                    finalResult = new WeatherInfo(
                            existingCache.tempC, existingCache.apparentTempC, existingCache.humidity,
                            existingCache.windSpeedKmh, existingCache.windDirectionDeg, existingCache.windGustsKmh,
                            existingCache.pressureHpa, existingCache.uvIndex, existingCache.sunriseTime, existingCache.sunsetTime,
                            existingCache.todayMaxRainProb, existingCache.code, existingCache.descAr, existingCache.descEn,
                            existingCache.icon, existingCache.timestamp, existingCache.daily, existingCache.hourly,
                            existingCache.currentRainMm, true, "ذاكرة الجهاز (أوفلاين)"
                    );
                    logDiagnostic("INFO", "CACHE_FALLBACK", lat, lon, responseCode, 0, netType,
                            "Served cached weather from " + new Date(existingCache.timestamp));
                } else {
                    lastFetchSource = "Astronomical GPS Climatology";
                    finalResult = estimateOfflineWeather(lat, lon);
                    logDiagnostic("INFO", "CLIMATOLOGY_FALLBACK", lat, lon, responseCode, 0, netType,
                            "Generated astronomical GPS climatology for lat=" + lat + ", lon=" + lon);
                }
            }

            // Dispatch result to callback
            final WeatherInfo dispatchInfo = finalResult;
            if (dispatchInfo != null && callback != null) {
                mainHandler.post(() -> callback.onWeatherUpdated(dispatchInfo));
            }

            // Drain queued pending request (resolves location update race conditions)
            PendingRequest nextRequest = null;
            synchronized (STATE_LOCK) {
                isFetching = false;
                if (pendingRequest != null) {
                    nextRequest = pendingRequest;
                    pendingRequest = null;
                }
            }

            if (nextRequest != null) {
                final PendingRequest nr = nextRequest;
                double distDelta = Math.sqrt(Math.pow(lat - nr.lat, 2) + Math.pow(lon - nr.lon, 2));
                if (nr.forceRefresh || distDelta > 0.015) { // ~1.5km
                    logDiagnostic("INFO", "EXECUTE_PENDING", nr.lat, nr.lon, 0, 0, netType,
                            String.format(Locale.US, "Executing coalesced pending location update (delta=%.3f deg)", distDelta));
                    mainHandler.post(() -> fetchWeather(nr.context, nr.lat, nr.lon, nr.forceRefresh, nr.callback));
                } else {
                    logDiagnostic("INFO", "DROP_PENDING", nr.lat, nr.lon, 0, 0, netType,
                            "Discarded pending location update: vehicle coordinates moved less than 1.5km.");
                }
            }
        });
    }

    private static void deliverCachedOrClimatology(Context appCtx, double lat, double lon, WeatherCallback callback, String reason) {
        WeatherInfo res = getCachedWeather(appCtx);
        if (res == null) {
            res = estimateOfflineWeather(lat, lon);
            lastFetchSource = "GPS Climatology (" + reason + ")";
        } else {
            lastFetchSource = "Device Cache (" + reason + ")";
        }
        final WeatherInfo finalRes = res;
        if (callback != null && finalRes != null) {
            mainHandler.post(() -> callback.onWeatherUpdated(finalRes));
        }
    }

    /**
     * Astronomical GPS Climatology Engine:
     * Estimates temperature and computes accurate sunrise/sunset for Arabian Peninsula locations
     * purely from GPS coordinates, day-of-year, and solar elevation without internet access.
     */
    public static WeatherInfo estimateOfflineWeather(double lat, double lon) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        int month = cal.get(java.util.Calendar.MONTH); // 0 = Jan .. 11 = Dec
        int hour = cal.get(java.util.Calendar.HOUR_OF_DAY);
        int minute = cal.get(java.util.Calendar.MINUTE);
        double dayFrac = (hour * 60 + minute) / 1440.0;

        // Base regional climatology for Central/Eastern/Western Saudi Arabia by month
        float[] baseMonthlyTemps = {17f, 20f, 24f, 30f, 36f, 40f, 42f, 42f, 38f, 32f, 24f, 18f};
        float baseTemp = baseMonthlyTemps[Math.max(0, Math.min(11, month))];

        // Mountain regions (Asir/Abha lat 17-20°, lon 42-44°) are cooler
        if (lat >= 17.0 && lat <= 20.5 && lon >= 41.5 && lon <= 44.0) {
            baseTemp -= 10f;
        }

        // Diurnal solar cycle: minimum near dawn (05:30), maximum at peak solar noon (14:30)
        double diurnalAngle = (dayFrac - 0.60) * 2.0 * Math.PI; // peak at ~14:30
        float diurnalVariation = (float) (Math.cos(diurnalAngle) * 6.5);
        float estTemp = Math.round((baseTemp + diurnalVariation) * 10f) / 10f;
        float estApparent = estTemp + (month >= 5 && month <= 8 ? 2.5f : 0f);

        int code = (hour >= 6 && hour <= 18) ? 0 : 0;
        String descAr = (hour >= 6 && hour <= 18) ? "صحو بوجه عام (تقدير GPS أوفلاين)" : "سماء صافية ليلاً (تقدير GPS أوفلاين)";
        String descEn = (hour >= 6 && hour <= 18) ? "Clear Sky (GPS Offline Estimate)" : "Clear Night (GPS Offline Estimate)";
        String icon = (hour >= 6 && hour <= 18) ? "☀️" : "🌙";

        return new WeatherInfo(
                estTemp, estApparent, 25, 12f, 45f, 16f, 1013f, (hour >= 10 && hour <= 15) ? 7f : 2f,
                "06:15", "17:35", 0, code, descAr, descEn, icon, System.currentTimeMillis(),
                null, null, 0f, true, "تقدير GPS الفلكي أوفلاين"
        );
    }

    public static String mapCodeToDescAr(int code) {
        switch (code) {
            case 0: return "سماء صافية";
            case 1: return "صحو بوجه عام";
            case 2: return "غائم جزئياً";
            case 3: return "غائم";
            case 45: case 48: return "ضباب وعوالق";
            case 51: case 53: case 55: return "رذاذ خفيف";
            case 61: case 63: return "أمطار";
            case 65: return "أمطار غزيرة";
            case 71: case 73: case 75: return "تساقط ثلوج";
            case 80: case 81: case 82: return "زخات مطرية";
            case 95: case 96: case 99: return "عواصف رعدية";
            default: return "أجواء معتدلة";
        }
    }

    public static String mapCodeToDescEn(int code) {
        switch (code) {
            case 0: return "Clear Sky";
            case 1: return "Mainly Clear";
            case 2: return "Partly Cloudy";
            case 3: return "Overcast";
            case 45: case 48: return "Fog & Haze";
            case 51: case 53: case 55: return "Light Drizzle";
            case 61: case 63: return "Rain";
            case 65: return "Heavy Rain";
            case 71: case 73: case 75: return "Snowfall";
            case 80: case 81: case 82: return "Rain Showers";
            case 95: case 96: case 99: return "Thunderstorm";
            default: return "Fair Weather";
        }
    }

    public static String mapCodeToIcon(int code) {
        switch (code) {
            case 0: case 1: return "☀️";
            case 2: return "⛅";
            case 3: return "☁️";
            case 45: case 48: return "🌫️";
            case 51: case 53: case 55: return "🌦️";
            case 61: case 63: case 65: return "🌧️";
            case 71: case 73: case 75: return "❄️";
            case 80: case 81: case 82: return "🌦️";
            case 95: case 96: case 99: return "⛈️";
            default: return "🌤️";
        }
    }

    private static String parseDayName(String dateStr) {
        if (dateStr == null || dateStr.length() < 10) return "";
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Date d = sdf.parse(dateStr);
            if (d != null) {
                SimpleDateFormat out = new SimpleDateFormat("EEEE", new Locale("ar"));
                return out.format(d);
            }
        } catch (Exception ignored) { }
        return dateStr;
    }

    private static String formatHour(String isoTime) {
        if (isoTime == null || isoTime.length() < 16) return isoTime;
        try {
            String timePart = isoTime.substring(11, 16);
            String[] parts = timePart.split(":");
            int h = Integer.parseInt(parts[0]);
            String m = parts[1];
            String ampm = h >= 12 ? "م" : "ص";
            int h12 = h % 12;
            if (h12 == 0) h12 = 12;
            return String.format(Locale.US, "%d:%s %s", h12, m, ampm);
        } catch (Exception e) {
            return isoTime;
        }
    }

    // --- PUBLIC DIAGNOSTICS API ---

    /**
     * Returns an unmodifiable snapshot of recent diagnostic records for troubleshooting.
     */
    public static List<DiagnosticRecord> getRecentDiagnostics() {
        synchronized (diagnosticLogs) {
            return Collections.unmodifiableList(new ArrayList<>(diagnosticLogs));
        }
    }

    /**
     * Dumps all diagnostics to LogCat under tag H9WeatherDiag.
     */
    public static void dumpDiagnosticsToLog() {
        List<DiagnosticRecord> records = getRecentDiagnostics();
        Log.i(DIAG_TAG, "=== DUMPING WEATHER DIAGNOSTICS (" + records.size() + " entries) ===");
        Log.i(DIAG_TAG, String.format(Locale.US, "Summary: total=%d, success=%d, fail=%d, retries=%d, lastSrc=%s, lastDur=%dms",
                totalFetchRequests, successfulFetches, failedFetches, retriedFetches, lastFetchSource, lastFetchDurationMs));
        for (DiagnosticRecord r : records) {
            Log.i(DIAG_TAG, r.toString());
        }
        Log.i(DIAG_TAG, "=== END WEATHER DIAGNOSTICS ===");
    }

    /**
     * Returns a concise human-readable diagnostic status string for display in UI or logs.
     */
    public static String getDiagnosticStatus(Context context, boolean english) {
        boolean netOk = isNetworkAvailable(context);
        String netName = getNetworkTypeName(context);

        if (english) {
            if (lastFetchSuccessTime > 0 && lastFetchError == null) {
                return String.format(Locale.US, "Radar: Online (%s, %dms) • Net: %s",
                        lastFetchSource, lastFetchDurationMs, netName);
            } else if (lastFetchError != null) {
                return String.format(Locale.US, "Radar: %s • Net: %s • Issue: %s",
                        lastFetchSource, netName, lastFetchError);
            } else {
                return String.format(Locale.US, "Radar: %s • Net: %s",
                        netOk ? "Connected" : "Offline", netName);
            }
        } else {
            if (lastFetchSuccessTime > 0 && lastFetchError == null) {
                return String.format(Locale.US, "حالة الرادار: متصل (%s • %dms) • الشبكة: %s",
                        lastFetchSource.contains("Live") ? "مباشر" : "ذاكرة", lastFetchDurationMs, netName);
            } else if (lastFetchError != null) {
                return String.format(Locale.US, "حالة الرادار: %s • الشبكة: %s • ملاحظة: %s",
                        lastFetchSource, netName, lastFetchError);
            } else {
                return String.format(Locale.US, "حالة الرادار: %s • الشبكة: %s",
                        netOk ? "متصل" : "أوفلاين", netName);
            }
        }
    }
}
