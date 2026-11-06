package sa.h9.qibla;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.GeolocationPermissions;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;
import java.util.Locale;

/**
 * Full-screen dedicated Weather Page featuring:
 * 1. Live current weather metrics (temp, apparent, wind speed/direction/gusts, humidity, pressure, UV, sunrise/sunset, rain chance).
 * 2. 24-hour hourly forecast & 7-day daily forecast from Open-Meteo.
 * 3. Live European Weather Radar with GPS centering, OpenStreetMap (100% free, no API key), Esri Satellite, and Windy ECMWF.
 * 4. Full theme synchronization (Royal Emerald, Obsidian Luxury Navy, Desert Amber, Pearl Day).
 */
public final class FullScreenWeatherDialog {

    public static class ThemePalette {
        public int rootBg;
        public int topBarBg;
        public int tabBarBg;
        public int cardBg;
        public int cardStroke;
        public int metricBoxBg;
        public int accentColor;
        public int activeTabBg;
        public int inactiveTabBg;
        public int textPrimary;
        public int textSecondary;
        public int goldHighlight;
        public boolean isLight;
    }

    private FullScreenWeatherDialog() { }

    public static ThemePalette getThemePalette(Context ctx) {
        int themeMode = QiblaView.THEME_AUTO;
        try {
            themeMode = ctx.getSharedPreferences(MainActivity.UI_PREFS, Context.MODE_PRIVATE)
                    .getInt(MainActivity.KEY_THEME_MODE, QiblaView.THEME_AUTO);
        } catch (Exception ignored) { }

        boolean isDaytime = true;
        Calendar now = Calendar.getInstance();
        int hour = now.get(Calendar.HOUR_OF_DAY);
        if (hour < 6 || hour >= 18) {
            isDaytime = false;
        }

        int effectiveTheme = themeMode;
        if (themeMode == QiblaView.THEME_AUTO) {
            effectiveTheme = isDaytime ? QiblaView.THEME_PEARL : QiblaView.THEME_EMERALD;
        }

        ThemePalette pal = new ThemePalette();
        switch (effectiveTheme) {
            case QiblaView.THEME_OBSIDIAN: // 2: Obsidian Luxury Navy
                pal.rootBg = 0xFF050E18;
                pal.topBarBg = 0xEE0A1828;
                pal.tabBarBg = 0xFF071220;
                pal.cardBg = 0xF00D1E32;
                pal.cardStroke = 0xFF3D8BFF;
                pal.metricBoxBg = 0xDD071524;
                pal.accentColor = 0xFF4FA8FF;
                pal.activeTabBg = 0xFF14406E;
                pal.inactiveTabBg = 0xDD0C1928;
                pal.textPrimary = 0xFFFFFFFF;
                pal.textSecondary = 0xBBD4E8F8;
                pal.goldHighlight = 0xFFFFD25A;
                pal.isLight = false;
                break;

            case QiblaView.THEME_AMBER: // 3: Desert Amber & Gold
                pal.rootBg = 0xFF120802;
                pal.topBarBg = 0xEE1E0F05;
                pal.tabBarBg = 0xFF150A03;
                pal.cardBg = 0xF0231307;
                pal.cardStroke = 0xFFFFA834;
                pal.metricBoxBg = 0xDD180D04;
                pal.accentColor = 0xFFFFB74D;
                pal.activeTabBg = 0xFF4A280B;
                pal.inactiveTabBg = 0xDD1F1006;
                pal.textPrimary = 0xFFFFFFFF;
                pal.textSecondary = 0xBBEFE0D2;
                pal.goldHighlight = 0xFFFFBA49;
                pal.isLight = false;
                break;

            case QiblaView.THEME_PEARL: // 4: Pearl Day
                pal.rootBg = 0xFFE9F1F5;
                pal.topBarBg = 0xFFD8E5EC;
                pal.tabBarBg = 0xFFE0ECF2;
                pal.cardBg = 0xF8FFFFFF;
                pal.cardStroke = 0xFF5D8898;
                pal.metricBoxBg = 0xEEEDF4F7;
                pal.accentColor = 0xFF00758A;
                pal.activeTabBg = 0xFF00758A;
                pal.inactiveTabBg = 0xDDD0DFE6;
                pal.textPrimary = 0xFF0C242C;
                pal.textSecondary = 0xFF2B4D58;
                pal.goldHighlight = 0xFF996300;
                pal.isLight = true;
                break;

            case QiblaView.THEME_OLED_NIGHT: // 5: Pure Black OLED Night
                pal.rootBg = 0xFF000000;
                pal.topBarBg = 0xFA080808;
                pal.tabBarBg = 0xFF000000;
                pal.cardBg = 0xF50A0A0A;
                pal.cardStroke = 0xFF443820;
                pal.metricBoxBg = 0xEE080808;
                pal.accentColor = 0xFFFFB74D;
                pal.activeTabBg = 0xFF2A200F;
                pal.inactiveTabBg = 0xDD101010;
                pal.textPrimary = 0xFFEEEEEE;
                pal.textSecondary = 0xFFA0A5A8;
                pal.goldHighlight = 0xFFFFB74D;
                pal.isLight = false;
                break;

            case QiblaView.THEME_EMERALD: // 1: Royal Emerald & Gold
            default:
                pal.rootBg = 0xFF041611;
                pal.topBarBg = 0xEE08221B;
                pal.tabBarBg = 0xFF061B15;
                pal.cardBg = 0xF0082820;
                pal.cardStroke = 0xFFE5B64A;
                pal.metricBoxBg = 0xDD051A14;
                pal.accentColor = 0xFF2FE0C7;
                pal.activeTabBg = 0xFF0F4D3D;
                pal.inactiveTabBg = 0xDD09241C;
                pal.textPrimary = 0xFFFFFFFF;
                pal.textSecondary = 0xBBD3E6ED;
                pal.goldHighlight = 0xFFFFD768;
                pal.isLight = false;
                break;
        }
        return pal;
    }

    public static void show(Activity activity, WeatherManager.WeatherInfo info, Location location, String cityName) {
        if (activity == null || activity.isFinishing()) return;

        final Context ctx = activity;
        final boolean isEn = (activity instanceof MainActivity) && ((MainActivity) activity).isEnglishMode();
        final double lat = (location != null) ? location.getLatitude() : 26.3315;
        final double lon = (location != null) ? location.getLongitude() : 43.9971;
        final String city = (cityName != null && !cityName.isEmpty()) ? cityName : (isEn ? "Saudi Arabia" : "المملكة العربية السعودية");
        final ThemePalette pal = getThemePalette(ctx);

        Dialog dialog = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(pal.rootBg);
        root.setFitsSystemWindows(true);

        // --- TOP BAR ---
        LinearLayout topBar = new LinearLayout(ctx);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(ctx, 20), dp(ctx, 14), dp(ctx, 20), dp(ctx, 14));
        topBar.setBackgroundColor(pal.topBarBg);

        // Close / Return Button
        Button closeBtn = new Button(ctx);
        closeBtn.setText(isEn ? "✕ Close" : "✕ إغلاق والعودة");
        closeBtn.setTextSize(15f);
        closeBtn.setTextColor(0xFFFFFFFF);
        closeBtn.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setColor(pal.activeTabBg);
        closeBg.setCornerRadius(dp(ctx, 14));
        closeBg.setStroke(dp(ctx, 1.5f), pal.accentColor);
        closeBtn.setBackground(closeBg);
        closeBtn.setPadding(dp(ctx, 18), dp(ctx, 8), dp(ctx, 18), dp(ctx, 8));
        closeBtn.setOnClickListener(v -> dialog.dismiss());
        topBar.addView(closeBtn);

        // Title and Location Header
        LinearLayout titleBox = new LinearLayout(ctx);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setGravity(Gravity.CENTER_VERTICAL);
        titleBox.setPadding(dp(ctx, 18), 0, dp(ctx, 18), 0);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        titleBox.setLayoutParams(titleLp);

        TextView titleView = new TextView(ctx);
        titleView.setText(isEn ? ("🌦️ Weather & Live Radar • " + city) : ("🌦️ طقس " + city + " • الرادار الأوروبي"));
        titleView.setTextSize(17f);
        titleView.setTextColor(pal.goldHighlight);
        titleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleBox.addView(titleView);

        TextView subtitleView = new TextView(ctx);
        subtitleView.setText(isEn ? "Live Open-Meteo Satellite & European ECMWF Radar" : "تحديثات الأرصاد المباشرة والرادار الأوروبي التفاعلي للأمطار مع GPS");
        subtitleView.setTextSize(12f);
        subtitleView.setTextColor(pal.textSecondary);
        titleBox.addView(subtitleView);
        topBar.addView(titleBox);

        if (info != null && info.isOffline) {
            TextView offlineBadge = new TextView(ctx);
            offlineBadge.setText(isEn ? "📡 Offline GPS" : "📡 أوفلاين (GPS)");
            offlineBadge.setTextSize(12f);
            offlineBadge.setTextColor(0xFFFFD768);
            offlineBadge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            offlineBadge.setPadding(dp(ctx, 10), dp(ctx, 4), dp(ctx, 10), dp(ctx, 4));
            GradientDrawable oBg = new GradientDrawable();
            oBg.setColor(0x882A1B0A);
            oBg.setCornerRadius(dp(ctx, 12));
            oBg.setStroke(dp(ctx, 1), 0xAAFFB74D);
            offlineBadge.setBackground(oBg);
            topBar.addView(offlineBadge);
        }

        // Online Sync / Refresh Button (Calls Open-Meteo Open Data Live)
        Button refreshBtn = new Button(ctx);
        refreshBtn.setText(isEn ? "🔄 Refresh" : "🔄 تحديث الآن");
        refreshBtn.setTextSize(13f);
        refreshBtn.setTextColor(0xFFFFFFFF);
        refreshBtn.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        GradientDrawable rBg = new GradientDrawable();
        rBg.setColor(0xAA0277BD);
        rBg.setCornerRadius(dp(ctx, 12));
        rBg.setStroke(dp(ctx, 1f), 0xFF81D4FA);
        refreshBtn.setBackground(rBg);
        refreshBtn.setPadding(dp(ctx, 12), dp(ctx, 6), dp(ctx, 12), dp(ctx, 6));
        LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(ctx, 38));
        rLp.setMargins(dp(ctx, 8), 0, 0, 0);
        refreshBtn.setLayoutParams(rLp);
        refreshBtn.setOnClickListener(v -> {
            Toast.makeText(ctx, isEn ? "🌐 Refreshing open-source weather data (Open-Meteo)..." : "🌐 جاري تحديث بيانات الأرصاد المفتوحة (Open-Meteo)...", Toast.LENGTH_SHORT).show();
            WeatherManager.fetchWeather(ctx, lat, lon, true, updatedInfo -> {
                dialog.dismiss();
                show(activity, updatedInfo, location, cityName);
            });
        });
        topBar.addView(refreshBtn);

        root.addView(topBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // --- TAB SWITCHER BAR ---
        LinearLayout tabBar = new LinearLayout(ctx);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setGravity(Gravity.CENTER);
        tabBar.setPadding(dp(ctx, 16), dp(ctx, 10), dp(ctx, 16), dp(ctx, 10));
        tabBar.setBackgroundColor(pal.tabBarBg);

        Button tabForecast = new Button(ctx);
        tabForecast.setText(isEn ? "📊 Forecast & Conditions" : "📊 الأحوال وتوقعات الأيام");
        tabForecast.setTextSize(14f);
        tabForecast.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        Button tabRadar = new Button(ctx);
        tabRadar.setText(isEn ? "🛰️ Live European Radar" : "🛰️ الرادار الأوروبي المباشر (GPS)");
        tabRadar.setTextSize(14f);
        tabRadar.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        // Content Containers
        ScrollView forecastScroll = new ScrollView(ctx);
        forecastScroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        LinearLayout forecastContent = new LinearLayout(ctx);
        forecastContent.setOrientation(LinearLayout.VERTICAL);
        forecastContent.setPadding(dp(ctx, 22), dp(ctx, 16), dp(ctx, 22), dp(ctx, 24));
        populateForecastContent(ctx, forecastContent, info, isEn, city, pal);
        forecastScroll.addView(forecastContent);

        FrameLayout radarContainer = new FrameLayout(ctx);
        radarContainer.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
        radarContainer.setVisibility(View.GONE);
        populateRadarContent(ctx, radarContainer, lat, lon, isEn, pal, city);

        // Tab styling & click listeners
        updateTabStyles(ctx, tabForecast, tabRadar, true, pal);

        tabForecast.setOnClickListener(v -> {
            updateTabStyles(ctx, tabForecast, tabRadar, true, pal);
            forecastScroll.setVisibility(View.VISIBLE);
            radarContainer.setVisibility(View.GONE);
        });

        tabRadar.setOnClickListener(v -> {
            updateTabStyles(ctx, tabForecast, tabRadar, false, pal);
            forecastScroll.setVisibility(View.GONE);
            radarContainer.setVisibility(View.VISIBLE);
        });

        LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(0, dp(ctx, 42), 1.0f);
        tabLp.setMargins(dp(ctx, 8), 0, dp(ctx, 8), 0);
        tabBar.addView(tabForecast, tabLp);
        tabBar.addView(tabRadar, tabLp);

        root.addView(tabBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(forecastScroll);
        root.addView(radarContainer);

        dialog.setContentView(root);
        dialog.show();
    }

    private static void updateTabStyles(Context ctx, Button activeTab, Button inactiveTab, boolean firstActive, ThemePalette pal) {
        Button b1 = firstActive ? activeTab : inactiveTab;
        Button b2 = firstActive ? inactiveTab : activeTab;

        GradientDrawable actBg = new GradientDrawable();
        actBg.setColor(pal.activeTabBg);
        actBg.setCornerRadius(dp(ctx, 12));
        actBg.setStroke(dp(ctx, 1.5f), pal.accentColor);
        b1.setBackground(actBg);
        b1.setTextColor(0xFFFFFFFF);

        GradientDrawable inactBg = new GradientDrawable();
        inactBg.setColor(pal.inactiveTabBg);
        inactBg.setCornerRadius(dp(ctx, 12));
        inactBg.setStroke(dp(ctx, 1f), (pal.cardStroke & 0x55FFFFFF) | 0x44000000);
        b2.setBackground(inactBg);
        b2.setTextColor(pal.textSecondary);
    }

    private static void populateForecastContent(Context ctx, LinearLayout parent, WeatherManager.WeatherInfo info,
                                                boolean isEn, String city, ThemePalette pal) {
        // 1. Current Hero Overview Card
        LinearLayout heroCard = new LinearLayout(ctx);
        heroCard.setOrientation(LinearLayout.VERTICAL);
        heroCard.setPadding(dp(ctx, 22), dp(ctx, 18), dp(ctx, 22), dp(ctx, 18));
        GradientDrawable heroBg = new GradientDrawable();
        heroBg.setColor(pal.cardBg);
        heroBg.setCornerRadius(dp(ctx, 20));
        heroBg.setStroke(dp(ctx, 1.5f), pal.cardStroke);
        heroCard.setBackground(heroBg);

        LinearLayout rowMain = new LinearLayout(ctx);
        rowMain.setOrientation(LinearLayout.HORIZONTAL);
        rowMain.setGravity(Gravity.CENTER_VERTICAL);

        TextView iconView = new TextView(ctx);
        iconView.setText((info != null) ? info.icon : "🌤️");
        iconView.setTextSize(54f);
        rowMain.addView(iconView);

        LinearLayout tempBox = new LinearLayout(ctx);
        tempBox.setOrientation(LinearLayout.VERTICAL);
        tempBox.setPadding(dp(ctx, 18), 0, dp(ctx, 18), 0);

        TextView tempView = new TextView(ctx);
        tempView.setText((info != null) ? String.format(Locale.US, "%.0f°C", info.tempC) : "--°C");
        tempView.setTextSize(42f);
        tempView.setTextColor(pal.textPrimary);
        tempView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        tempBox.addView(tempView);

        TextView descView = new TextView(ctx);
        descView.setText((info != null) ? info.getLocalizedDesc(isEn) : (isEn ? "Fair Weather" : "صحو بوجه عام"));
        descView.setTextSize(17f);
        descView.setTextColor(pal.accentColor);
        descView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        tempBox.addView(descView);

        TextView feelsView = new TextView(ctx);
        float apparent = (info != null) ? info.apparentTempC : ((info != null) ? info.tempC : 28f);
        feelsView.setText(isEn ? String.format(Locale.US, "Feels like: %.0f°C", apparent)
                : String.format(Locale.US, "الإحساس الفعلي: %.0f°م", apparent));
        feelsView.setTextSize(13f);
        feelsView.setTextColor(pal.textSecondary);
        tempBox.addView(feelsView);

        rowMain.addView(tempBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        heroCard.addView(rowMain);

        // Telemetry Metrics Grid - Row 1 (Wind, Gusts, Humidity, Rain Chance)
        LinearLayout grid1 = new LinearLayout(ctx);
        grid1.setOrientation(LinearLayout.HORIZONTAL);
        grid1.setPadding(0, dp(ctx, 16), 0, 0);

        float wind = (info != null) ? info.windSpeedKmh : 10f;
        String windDir = (info != null) ? info.getWindDirectionLabel(isEn) : (isEn ? "North" : "شمالية");
        float gusts = (info != null && info.windGustsKmh > 0) ? info.windGustsKmh : wind * 1.3f;
        int hum = (info != null) ? info.humidity : 20;
        int rainChance = (info != null) ? info.todayMaxRainProb : 0;

        grid1.addView(createMetricBox(ctx, isEn ? "💨 Wind" : "💨 الرياح والاتجاه",
                String.format(Locale.US, "%.0f km/h (%s)", wind, windDir), pal));
        grid1.addView(createMetricBox(ctx, isEn ? "🌪️ Gusts" : "🌪️ هبات الرياح",
                String.format(Locale.US, "%.0f km/h", gusts), pal));
        grid1.addView(createMetricBox(ctx, isEn ? "💧 Humidity" : "💧 الرطوبة",
                String.format(Locale.US, "%d%%", hum), pal));
        grid1.addView(createMetricBox(ctx, isEn ? "🌧️ Rain Chance" : "🌧️ فرصة الأمطار",
                rainChance + "%", pal));
        heroCard.addView(grid1);

        // Telemetry Metrics Grid - Row 2 (Sunrise, Sunset, Pressure, UV)
        LinearLayout grid2 = new LinearLayout(ctx);
        grid2.setOrientation(LinearLayout.HORIZONTAL);
        grid2.setPadding(0, dp(ctx, 8), 0, 0);

        String sunrise = (info != null && !info.sunriseTime.isEmpty()) ? info.sunriseTime : "06:12 ص";
        String sunset = (info != null && !info.sunsetTime.isEmpty()) ? info.sunsetTime : "05:28 م";
        float pressure = (info != null && info.pressureHpa > 0) ? info.pressureHpa : 1014f;
        float uv = (info != null && info.uvIndex > 0) ? info.uvIndex : 5.0f;

        grid2.addView(createMetricBox(ctx, isEn ? "🌅 Sunrise" : "🌅 شروق الشمس", sunrise, pal));
        grid2.addView(createMetricBox(ctx, isEn ? "🌇 Sunset" : "🌇 غروب الشمس", sunset, pal));
        grid2.addView(createMetricBox(ctx, isEn ? "⏱️ Pressure" : "⏱️ الضغط الجوي",
                String.format(Locale.US, "%.0f hPa", pressure), pal));
        grid2.addView(createMetricBox(ctx, isEn ? "☀️ UV Index" : "☀️ مؤشر الأشعة",
                String.format(Locale.US, "%.1f", uv), pal));
        heroCard.addView(grid2);

        parent.addView(heroCard, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 2. Hourly Forecast Section
        if (info != null && info.hourly != null && !info.hourly.isEmpty()) {
            TextView hTitle = new TextView(ctx);
            hTitle.setText(isEn ? "⏱️ Hourly Forecast (Next 24 Hours)" : "⏱️ توقعات درجات الحرارة بالساعات القادمة");
            hTitle.setTextSize(16f);
            hTitle.setTextColor(pal.goldHighlight);
            hTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            LinearLayout.LayoutParams hTitleLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hTitleLp.setMargins(0, dp(ctx, 22), 0, dp(ctx, 10));
            parent.addView(hTitle, hTitleLp);

            HorizontalScrollView hScroll = new HorizontalScrollView(ctx);
            hScroll.setHorizontalScrollBarEnabled(false);
            LinearLayout hLayout = new LinearLayout(ctx);
            hLayout.setOrientation(LinearLayout.HORIZONTAL);

            for (WeatherManager.HourForecast hf : info.hourly) {
                LinearLayout card = new LinearLayout(ctx);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setGravity(Gravity.CENTER);
                card.setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 12));
                GradientDrawable cBg = new GradientDrawable();
                cBg.setColor(pal.cardBg);
                cBg.setCornerRadius(dp(ctx, 14));
                cBg.setStroke(dp(ctx, 1f), (pal.accentColor & 0x44FFFFFF) | 0x33000000);
                card.setBackground(cBg);

                TextView tvHour = new TextView(ctx);
                tvHour.setText(hf.hourStr);
                tvHour.setTextSize(12f);
                tvHour.setTextColor(pal.textSecondary);
                card.addView(tvHour);

                TextView tvIcon = new TextView(ctx);
                tvIcon.setText(hf.icon);
                tvIcon.setTextSize(26f);
                card.addView(tvIcon);

                TextView tvTemp = new TextView(ctx);
                tvTemp.setText(String.format(Locale.US, "%.0f°", hf.tempC));
                tvTemp.setTextSize(15f);
                tvTemp.setTextColor(pal.textPrimary);
                tvTemp.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                card.addView(tvTemp);

                if (hf.rainProb > 0) {
                    TextView tvRain = new TextView(ctx);
                    tvRain.setText("💧 " + hf.rainProb + "%");
                    tvRain.setTextSize(11f);
                    tvRain.setTextColor(pal.accentColor);
                    card.addView(tvRain);
                }

                LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(dp(ctx, 80), ViewGroup.LayoutParams.WRAP_CONTENT);
                cLp.setMargins(0, 0, dp(ctx, 10), 0);
                hLayout.addView(card, cLp);
            }
            hScroll.addView(hLayout);
            parent.addView(hScroll);
        }

        // 3. 7-Day Forecast Section
        if (info != null && info.daily != null && !info.daily.isEmpty()) {
            TextView dTitle = new TextView(ctx);
            dTitle.setText(isEn ? "📅 7-Day Daily Weather Forecast" : "📅 توقعات الطقس للأيام السبعة القادمة");
            dTitle.setTextSize(16f);
            dTitle.setTextColor(pal.goldHighlight);
            dTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            LinearLayout.LayoutParams dTitleLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dTitleLp.setMargins(0, dp(ctx, 22), 0, dp(ctx, 10));
            parent.addView(dTitle, dTitleLp);

            LinearLayout dailyContainer = new LinearLayout(ctx);
            dailyContainer.setOrientation(LinearLayout.VERTICAL);

            for (WeatherManager.DayForecast df : info.daily) {
                LinearLayout dayRow = new LinearLayout(ctx);
                dayRow.setOrientation(LinearLayout.HORIZONTAL);
                dayRow.setGravity(Gravity.CENTER_VERTICAL);
                dayRow.setPadding(dp(ctx, 16), dp(ctx, 12), dp(ctx, 16), dp(ctx, 12));
                GradientDrawable rowBg = new GradientDrawable();
                rowBg.setColor(pal.cardBg);
                rowBg.setCornerRadius(dp(ctx, 14));
                rowBg.setStroke(dp(ctx, 1f), (pal.cardStroke & 0x44FFFFFF) | 0x33000000);
                dayRow.setBackground(rowBg);

                TextView tvDay = new TextView(ctx);
                tvDay.setText(df.dayName.isEmpty() ? df.dateStr : df.dayName);
                tvDay.setTextSize(15f);
                tvDay.setTextColor(pal.textPrimary);
                tvDay.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                dayRow.addView(tvDay, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.3f));

                TextView tvDayIcon = new TextView(ctx);
                tvDayIcon.setText(df.icon + " " + (isEn ? df.descEn : df.descAr));
                tvDayIcon.setTextSize(14f);
                tvDayIcon.setTextColor(pal.accentColor);
                dayRow.addView(tvDayIcon, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2.0f));

                if (df.rainProb > 0) {
                    TextView tvRain = new TextView(ctx);
                    tvRain.setText("💧 " + df.rainProb + "%");
                    tvRain.setTextSize(12f);
                    tvRain.setTextColor(pal.isLight ? 0xFF00758A : 0xFF7DE3F4);
                    dayRow.addView(tvRain, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.9f));
                }

                TextView tvTemps = new TextView(ctx);
                tvTemps.setText(String.format(Locale.US, "%.0f° / %.0f°", df.maxTemp, df.minTemp));
                tvTemps.setTextSize(15f);
                tvTemps.setTextColor(pal.goldHighlight);
                tvTemps.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                tvTemps.setGravity(Gravity.END);
                dayRow.addView(tvTemps, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rowLp.setMargins(0, 0, 0, dp(ctx, 8));
                dailyContainer.addView(dayRow, rowLp);
            }
            parent.addView(dailyContainer);
        }
    }

    private static View createMetricBox(Context ctx, String label, String value, ThemePalette pal) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(ctx, 8), dp(ctx, 8), dp(ctx, 8), dp(ctx, 8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(pal.metricBoxBg);
        bg.setCornerRadius(dp(ctx, 12));
        box.setBackground(bg);

        TextView lbl = new TextView(ctx);
        lbl.setText(label);
        lbl.setTextSize(11f);
        lbl.setTextColor(pal.textSecondary);
        lbl.setGravity(Gravity.CENTER);
        box.addView(lbl);

        TextView val = new TextView(ctx);
        val.setText(value);
        val.setTextSize(13f);
        val.setTextColor(pal.textPrimary);
        val.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        val.setGravity(Gravity.CENTER);
        box.addView(val);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        lp.setMargins(dp(ctx, 3), 0, dp(ctx, 3), 0);
        box.setLayoutParams(lp);
        return box;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private static void populateRadarContent(Context ctx, FrameLayout parent, double lat, double lon,
                                              boolean isEn, ThemePalette pal, String city) {
        LinearLayout container = new LinearLayout(ctx);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundColor(pal.rootBg);

        // Radar controls bar
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(ctx, 16), dp(ctx, 8), dp(ctx, 16), dp(ctx, 8));
        bar.setBackgroundColor(pal.topBarBg);

        TextView radarLabel = new TextView(ctx);
        radarLabel.setText(isEn ? "🛰️ European ECMWF & GPS Rain Radar" : "🛰️ الرادار الأوروبي ورادار الأمطار المباشر (GPS)");
        radarLabel.setTextSize(14f);
        radarLabel.setTextColor(pal.goldHighlight);
        radarLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        bar.addView(radarLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        // URLs:
        // 1. Windy European ECMWF live radar embed (with closer default zoom = 9 centered on coordinates)
        final int[] windyZoom = {9};
        final String[] activeMode = {"leaflet"}; // "leaflet", "ventusky", "windy"

        // 2. Ventusky live radar alternative (smooth interactive precipitation & clouds)
        final String ventuskyUrl = String.format(Locale.US,
                "https://www.ventusky.com/?p=%.4f;%%20%.4f;8&l=rain-3h",
                lat, lon);

        // 3. Leaflet RainViewer direct tile HTML (100% free OpenStreetMap & Esri imagery, ZERO API key watermarks, with GPS locate button)
        final String leafletRainHtml = buildLeafletRainViewerHtml(lat, lon, isEn, pal, city);

        // WebView setup with full automotive hardware & DOM storage options
        WebView webView = new WebView(ctx);
        webView.setBackgroundColor(pal.rootBg);
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        ws.setBuiltInZoomControls(true);
        ws.setDisplayZoomControls(false);
        ws.setSupportZoom(true);
        ws.setCacheMode(WebSettings.LOAD_DEFAULT);
        try {
            ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        } catch (Exception ignored) { }
        ws.setUserAgentString("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }
        });
        webView.setWebViewClient(new WebViewClient());

        // Default to Leaflet RainViewer Direct Radar with OpenStreetMap tiles & GPS centering!
        webView.loadDataWithBaseURL("https://api.rainviewer.com/", leafletRainHtml, "text/html", "UTF-8", null);

        Button btnTileRain = createRadarSmallBtn(ctx, isEn ? "🌧️ Open Rain Radar (GPS)" : "🌧️ رادار الأمطار المفتوح (سريع)", true, pal);
        Button btnVentusky = createRadarSmallBtn(ctx, isEn ? "🌪️ Ventusky Radar" : "🌪️ رادار فينتوسكي الحي (أمطار وسحب)", false, pal);
        Button btnWindy = createRadarSmallBtn(ctx, isEn ? "🇪🇺 ECMWF Radar" : "🇪🇺 رادار ويندي الأوروبي", false, pal);
        Button btnGpsCenter = createRadarSmallBtn(ctx, isEn ? "🎯 Street GPS Zoom" : "🎯 تقريب موقعي GPS", false, pal);
        Button btnZoomIn = createRadarSmallBtn(ctx, isEn ? "➕ Zoom In" : "➕ تكبير", false, pal);
        Button btnZoomOut = createRadarSmallBtn(ctx, isEn ? "➖ Zoom Out" : "➖ تصغير", false, pal);

        btnTileRain.setOnClickListener(v -> {
            activeMode[0] = "leaflet";
            updateRadarSmallBtn(ctx, btnTileRain, true, pal);
            updateRadarSmallBtn(ctx, btnVentusky, false, pal);
            updateRadarSmallBtn(ctx, btnWindy, false, pal);
            webView.loadDataWithBaseURL("https://api.rainviewer.com/", leafletRainHtml, "text/html", "UTF-8", null);
        });

        btnVentusky.setOnClickListener(v -> {
            activeMode[0] = "ventusky";
            updateRadarSmallBtn(ctx, btnTileRain, false, pal);
            updateRadarSmallBtn(ctx, btnVentusky, true, pal);
            updateRadarSmallBtn(ctx, btnWindy, false, pal);
            webView.loadUrl(ventuskyUrl);
            Toast.makeText(ctx, isEn ? "Loading Ventusky live rain & cloud radar..." : "جاري تشغيل رادار فينتوسكي الحي للأمطار والسحب...", Toast.LENGTH_SHORT).show();
        });

        btnWindy.setOnClickListener(v -> {
            activeMode[0] = "windy";
            updateRadarSmallBtn(ctx, btnTileRain, false, pal);
            updateRadarSmallBtn(ctx, btnVentusky, false, pal);
            updateRadarSmallBtn(ctx, btnWindy, true, pal);
            String url = String.format(Locale.US,
                    "https://embed.windy.com/embed.html?type=map&location=coordinates&metricRain=mm&metricTemp=%%C2%%B0C&metricWind=km%%2Fh&zoom=%d&overlay=radar&product=ecmwf&level=surface&lat=%.4f&lon=%.4f&message=true",
                    windyZoom[0], lat, lon);
            webView.loadUrl(url);
            Toast.makeText(ctx, isEn ? "Loading Windy European Radar..." : "جاري تشغيل رادار ويندي الأوروبي...", Toast.LENGTH_SHORT).show();
        });

        btnGpsCenter.setOnClickListener(v -> {
            if (!"leaflet".equals(activeMode[0])) {
                activeMode[0] = "leaflet";
                updateRadarSmallBtn(ctx, btnTileRain, true, pal);
                updateRadarSmallBtn(ctx, btnVentusky, false, pal);
                updateRadarSmallBtn(ctx, btnWindy, false, pal);
                webView.loadDataWithBaseURL("https://api.rainviewer.com/", leafletRainHtml, "text/html", "UTF-8", null);
            }

            Location freshLoc = null;
            if (ctx instanceof MainActivity) {
                freshLoc = ((MainActivity) ctx).getLastLocation();
            }
            if (freshLoc == null) {
                try {
                    android.location.LocationManager lm = (android.location.LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
                    if (lm != null) {
                        freshLoc = lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER);
                        if (freshLoc == null) freshLoc = lm.getLastKnownLocation(android.location.LocationManager.PASSIVE_PROVIDER);
                        if (freshLoc == null) freshLoc = lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER);
                    }
                } catch (Exception ignored) { }
            }

            if (freshLoc != null) {
                String js = String.format(Locale.US,
                        "if (typeof centerOnGPS === 'function') { centerOnGPS(%.6f, %.6f, %.1f); }",
                        freshLoc.getLatitude(), freshLoc.getLongitude(), freshLoc.getAccuracy());
                webView.evaluateJavascript(js, null);
                Toast.makeText(ctx, isEn ? "🎯 Zooming in to exact vehicle street location" : "🎯 تقريب وتحديد موقع المركبة بالشارع والحي", Toast.LENGTH_SHORT).show();
            } else {
                webView.evaluateJavascript("if (typeof centerOnGPS === 'function') { centerOnGPS(); }", null);
                Toast.makeText(ctx, isEn ? "Locating vehicle via GPS..." : "جاري تحديد موقع المركبة بالـ GPS...", Toast.LENGTH_SHORT).show();
            }
        });

        btnZoomIn.setOnClickListener(v -> {
            if ("leaflet".equals(activeMode[0])) {
                webView.evaluateJavascript("if (typeof map !== 'undefined') { map.zoomIn(); }", null);
            } else if ("windy".equals(activeMode[0])) {
                if (windyZoom[0] < 14) {
                    windyZoom[0]++;
                    String url = String.format(Locale.US,
                            "https://embed.windy.com/embed.html?type=map&location=coordinates&metricRain=mm&metricTemp=%%C2%%B0C&metricWind=km%%2Fh&zoom=%d&overlay=radar&product=ecmwf&level=surface&lat=%.4f&lon=%.4f&message=true",
                            windyZoom[0], lat, lon);
                    webView.loadUrl(url);
                }
            } else {
                webView.zoomIn();
            }
        });

        btnZoomOut.setOnClickListener(v -> {
            if ("leaflet".equals(activeMode[0])) {
                webView.evaluateJavascript("if (typeof map !== 'undefined') { map.zoomOut(); }", null);
            } else if ("windy".equals(activeMode[0])) {
                if (windyZoom[0] > 4) {
                    windyZoom[0]--;
                    String url = String.format(Locale.US,
                            "https://embed.windy.com/embed.html?type=map&location=coordinates&metricRain=mm&metricTemp=%%C2%%B0C&metricWind=km%%2Fh&zoom=%d&overlay=radar&product=ecmwf&level=surface&lat=%.4f&lon=%.4f&message=true",
                            windyZoom[0], lat, lon);
                    webView.loadUrl(url);
                }
            } else {
                webView.zoomOut();
            }
        });

        HorizontalScrollView barScroll = new HorizontalScrollView(ctx);
        barScroll.setHorizontalScrollBarEnabled(false);
        bar.addView(btnTileRain);
        bar.addView(btnVentusky);
        bar.addView(btnWindy);
        bar.addView(btnGpsCenter);
        bar.addView(btnZoomIn);
        bar.addView(btnZoomOut);
        barScroll.addView(bar);
        container.addView(barScroll);

        LinearLayout.LayoutParams webLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        container.addView(webView, webLp);

        parent.addView(container, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private static String buildLeafletRainViewerHtml(double lat, double lon, boolean isEn, ThemePalette pal, String city) {
        String statusConnecting = isEn ? "📡 Connecting to High-Speed Radar..." : "📡 جاري الاتصال برادار الطقس السريع...";
        String statusActiveRain = isEn ? "🟢 Live Rain Radar Active (GPS Street Zoom)" : "🟢 رادار الأمطار مباشر ونشط (تقريب الأحياء والشوارع)";
        String statusActiveSat = isEn ? "🛰️ Infrared Clouds & Satellite Radar Active" : "🛰️ رادار السحب والأقمار الصناعية نشط ومباشر";
        String statusEmpty = isEn ? "☀️ Clear skies (No rain clouds detected over region)" : "☀️ الأجواء صحوة ولا توجد خلايا ماطرة حالياً فوق المنطقة";
        String statusError = isEn ? "⚠️ Could not connect to radar" : "⚠️ تعذر الاتصال ببيانات الرادار";
        String myLocation = isEn ? ("📍 Vehicle GPS: " + city) : ("📍 موقع المركبة بالـ GPS: " + city);
        String gpsBtnText = isEn ? "🎯 GPS Street Zoom" : "🎯 تقريب موقعي GPS";

        String bgHex = String.format("#%06X", (0xFFFFFF & pal.rootBg));
        String accentHex = String.format("#%06X", (0xFFFFFF & pal.accentColor));
        String goldHex = String.format("#%06X", (0xFFFFFF & pal.goldHighlight));

        return "<!DOCTYPE html>\n" +
                "<html lang=\"ar\" dir=\"rtl\">\n" +
                "<head>\n" +
                "  <meta charset=\"utf-8\">\n" +
                "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=2.0, user-scalable=yes\">\n" +
                "  <link rel=\"stylesheet\" href=\"https://unpkg.com/leaflet@1.9.4/dist/leaflet.css\" />\n" +
                "  <script src=\"https://unpkg.com/leaflet@1.9.4/dist/leaflet.js\"></script>\n" +
                "  <style>\n" +
                "    body, html { margin: 0; padding: 0; width: 100%; height: 100%; background: " + bgHex + "; font-family: sans-serif; overflow: hidden; }\n" +
                "    #map { width: 100%; height: 100%; background: " + bgHex + "; }\n" +
                "    .status-badge {\n" +
                "      position: absolute; top: 12px; right: 12px; z-index: 1000;\n" +
                "      background: rgba(8, 22, 28, 0.94); border: 1.5px solid " + accentHex + ";\n" +
                "      color: " + accentHex + "; padding: 8px 16px; border-radius: 14px;\n" +
                "      font-size: 13px; font-weight: bold; box-shadow: 0 4px 12px rgba(0,0,0,0.5);\n" +
                "    }\n" +
                "    .gps-btn {\n" +
                "      position: absolute; top: 12px; left: 12px; z-index: 1000;\n" +
                "      background: " + accentHex + "; color: #041E18; border: 2px solid " + goldHex + ";\n" +
                "      border-radius: 20px; padding: 8px 16px; font-size: 13px; font-weight: bold;\n" +
                "      cursor: pointer; box-shadow: 0 4px 14px rgba(0,0,0,0.6); display: flex; align-items: center; gap: 6px;\n" +
                "    }\n" +
                "    .gps-btn:active { transform: scale(0.96); }\n" +
                "    .controls-bar {\n" +
                "      position: absolute; bottom: 20px; left: 50%; transform: translateX(-50%); z-index: 1000;\n" +
                "      background: rgba(10, 24, 32, 0.94); border: 1.5px solid " + goldHex + ";\n" +
                "      border-radius: 26px; padding: 8px 20px; color: #fff;\n" +
                "      display: flex; align-items: center; gap: 14px; font-weight: bold; font-size: 14px;\n" +
                "      box-shadow: 0 6px 20px rgba(0,0,0,0.6);\n" +
                "    }\n" +
                "    .play-btn {\n" +
                "      background: " + accentHex + "; color: #041B17; border: none;\n" +
                "      border-radius: 50%; width: 38px; height: 38px; font-size: 16px;\n" +
                "      cursor: pointer; display: flex; align-items: center; justify-content: center;\n" +
                "    }\n" +
                "    .time-badge { color: " + goldHex + "; min-width: 80px; text-align: center; font-size: 14px; }\n" +
                "    .radar-mode-tag { font-size: 12px; color: #CADFE6; font-weight: bold; }\n" +
                "  </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "  <div id=\"map\"></div>\n" +
                "  <div class=\"status-badge\" id=\"status\">" + statusConnecting + "</div>\n" +
                "  <button class=\"gps-btn\" onclick=\"centerOnGPS()\">" + gpsBtnText + "</button>\n" +
                "  <div class=\"controls-bar\" id=\"controls\" style=\"display:none;\">\n" +
                "    <button class=\"play-btn\" id=\"playBtn\" onclick=\"togglePlay()\">⏸</button>\n" +
                "    <div class=\"time-badge\" id=\"timeLabel\">مباشر</div>\n" +
                "    <div class=\"radar-mode-tag\" id=\"modeLabel\">🌧️ رادار الأمطار</div>\n" +
                "  </div>\n" +
                "  <script>\n" +
                "    var lat = " + String.format(Locale.US, "%.5f", lat) + ";\n" +
                "    var lon = " + String.format(Locale.US, "%.5f", lon) + ";\n" +
                "    var currentMode = 'rain'; // 'rain' or 'clouds'\n" +
                "    var map = L.map('map', { zoomControl: true, attributionControl: false }).setView([lat, lon], 12);\n" +
                "\n" +
                "    // 100% Free OpenStreetMap Global Street Tiles\n" +
                "    var osmStreets = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {\n" +
                "      maxZoom: 18,\n" +
                "      subdomains: ['a','b','c']\n" +
                "    }).addTo(map);\n" +
                "\n" +
                "    // High-Resolution Esri World Imagery as alternative satellite base\n" +
                "    var esriSat = L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}', {\n" +
                "      maxZoom: 18\n" +
                "    });\n" +
                "\n" +
                "    var baseMaps = {\n" +
                "      '🗺️ خريطة الشوارع': osmStreets,\n" +
                "      '🛰️ قمر اصطناعي أرضي': esriSat\n" +
                "    };\n" +
                "    L.control.layers(baseMaps).addTo(map);\n" +
                "\n" +
                "    // Vehicle GPS marker & accuracy circle\n" +
                "    var vehicleMarker = null;\n" +
                "    var accuracyCircle = null;\n" +
                "\n" +
                "    function updateVehicleMarker(cLat, cLon, accuracy) {\n" +
                "      lat = cLat;\n" +
                "      lon = cLon;\n" +
                "      if (!vehicleMarker) {\n" +
                "        vehicleMarker = L.circleMarker([lat, lon], {\n" +
                "          radius: 12, fillColor: '" + goldHex + "', color: '#FFFFFF', weight: 4, opacity: 1, fillOpacity: 0.95\n" +
                "        }).addTo(map);\n" +
                "      } else {\n" +
                "        vehicleMarker.setLatLng([lat, lon]);\n" +
                "      }\n" +
                "      if (accuracy && accuracy > 0) {\n" +
                "        if (!accuracyCircle) {\n" +
                "          accuracyCircle = L.circle([lat, lon], {\n" +
                "            radius: accuracy, color: '" + accentHex + "', fillColor: '" + accentHex + "', fillOpacity: 0.18, weight: 1.5\n" +
                "          }).addTo(map);\n" +
                "        } else {\n" +
                "          accuracyCircle.setLatLng([lat, lon]);\n" +
                "          accuracyCircle.setRadius(accuracy);\n" +
                "        }\n" +
                "      }\n" +
                "      var popupText = \"<div style='color:#05141A; font-family:sans-serif; text-align:right;' dir='rtl'>\" +\n" +
                "        \"<b style='color:#083B2E; font-size:14px;'>" + myLocation + "</b><br/>\" +\n" +
                "        \"<small style='color:#3B6370;'>الإحداثيات: \" + lat.toFixed(5) + \"°, \" + lon.toFixed(5) + \"°</small>\" +\n" +
                "        (accuracy ? (\"<br/><small style='color:#0B5848; font-weight:bold;'>دقة GPS: ±\" + Math.round(accuracy) + \" متر</small>\") : \"\") +\n" +
                "        \"<br/><small style='color:#825300; font-weight:bold;'>مستوى التقريب: الشوارع والأحياء (Zoom 14)</small>\" +\n" +
                "        \"</div>\";\n" +
                "      vehicleMarker.bindPopup(popupText).openPopup();\n" +
                "    }\n" +
                "\n" +
                "    updateVehicleMarker(lat, lon, 25);\n" +
                "\n" +
                "    function centerOnGPS(newLat, newLon, acc) {\n" +
                "      if (newLat && newLon && newLat !== 0) {\n" +
                "        updateVehicleMarker(newLat, newLon, acc);\n" +
                "        map.flyTo([newLat, newLon], 14, { duration: 1.4 });\n" +
                "        document.getElementById('status').innerText = '🟢 تم تحديد موقع المركبة بدقة عالية';\n" +
                "        return;\n" +
                "      }\n" +
                "      if (navigator.geolocation) {\n" +
                "        document.getElementById('status').innerText = '📡 جاري تحديد موقعك بالأقمار الصناعية...';\n" +
                "        navigator.geolocation.getCurrentPosition(function(pos) {\n" +
                "          updateVehicleMarker(pos.coords.latitude, pos.coords.longitude, pos.coords.accuracy);\n" +
                "          map.flyTo([pos.coords.latitude, pos.coords.longitude], 14, { duration: 1.4 });\n" +
                "          document.getElementById('status').innerText = '🟢 تم تحديد موقع المركبة بدقة';\n" +
                "        }, function(err) {\n" +
                "          map.flyTo([lat, lon], 14, { duration: 1.2 });\n" +
                "          if (vehicleMarker) vehicleMarker.openPopup();\n" +
                "        }, { enableHighAccuracy: true, timeout: 8000, maximumAge: 30000 });\n" +
                "      } else {\n" +
                "        map.flyTo([lat, lon], 14, { duration: 1.2 });\n" +
                "        if (vehicleMarker) vehicleMarker.openPopup();\n" +
                "      }\n" +
                "    }\n" +
                "\n" +
                "    var rainLayers = [];\n" +
                "    var rainTimestamps = [];\n" +
                "    var satLayers = [];\n" +
                "    var satTimestamps = [];\n" +
                "    var currentIdx = 0;\n" +
                "    var intervalId = null;\n" +
                "\n" +
                "    fetch('https://api.rainviewer.com/public/weather-maps.json')\n" +
                "      .then(function(res){ return res.json(); })\n" +
                "      .then(function(data){\n" +
                "        // 1. Rain Radar Layers (CRITICAL: maxNativeZoom: 7 prevents 'Zoom Level Not Supported' error!)\n" +
                "        var rList = (data.radar && data.radar.past) ? data.radar.past : [];\n" +
                "        if (data.radar && data.radar.nowcast) rList = rList.concat(data.radar.nowcast);\n" +
                "        rList.forEach(function(item){\n" +
                "          var layer = L.tileLayer('https://tilecache.rainviewer.com' + item.path + '/256/{z}/{x}/{y}/2/1_1.png', {\n" +
                "            tileSize: 256,\n" +
                "            maxNativeZoom: 7,\n" +
                "            maxZoom: 18,\n" +
                "            opacity: 0,\n" +
                "            zIndex: 100\n" +
                "          });\n" +
                "          layer.addTo(map);\n" +
                "          rainLayers.push(layer);\n" +
                "          rainTimestamps.push(item.time);\n" +
                "        });\n" +
                "\n" +
                "        // 2. High-Speed 2D Satellite Clouds Layers (Smooth, zero WebGL lag on car Android!)\n" +
                "        var sList = (data.satellite && data.satellite.infrared) ? data.satellite.infrared : [];\n" +
                "        sList.forEach(function(item){\n" +
                "          var layer = L.tileLayer('https://tilecache.rainviewer.com' + item.path + '/256/{z}/{x}/{y}/0/1_0.png', {\n" +
                "            tileSize: 256,\n" +
                "            maxNativeZoom: 5,\n" +
                "            maxZoom: 18,\n" +
                "            opacity: 0,\n" +
                "            zIndex: 90\n" +
                "          });\n" +
                "          layer.addTo(map);\n" +
                "          satLayers.push(layer);\n" +
                "          satTimestamps.push(item.time);\n" +
                "        });\n" +
                "\n" +
                "        if (rainLayers.length === 0 && satLayers.length === 0) {\n" +
                "          document.getElementById('status').innerText = '" + statusEmpty + "';\n" +
                "          return;\n" +
                "        }\n" +
                "        document.getElementById('status').innerText = '" + statusActiveRain + "';\n" +
                "        document.getElementById('controls').style.display = 'flex';\n" +
                "        showFrame(getActiveLayers().length - 1);\n" +
                "        startAnimation();\n" +
                "      })\n" +
                "      .catch(function(err){\n" +
                "        document.getElementById('status').innerText = '" + statusError + "';\n" +
                "      });\n" +
                "\n" +
                "    function getActiveLayers() {\n" +
                "      return (currentMode === 'clouds') ? satLayers : rainLayers;\n" +
                "    }\n" +
                "\n" +
                "    function getActiveTimestamps() {\n" +
                "      return (currentMode === 'clouds') ? satTimestamps : rainTimestamps;\n" +
                "    }\n" +
                "\n" +
                "    function setRadarMode(mode) {\n" +
                "      currentMode = mode;\n" +
                "      // Hide all layers from both\n" +
                "      rainLayers.forEach(function(l){ l.setOpacity(0); });\n" +
                "      satLayers.forEach(function(l){ l.setOpacity(0); });\n" +
                "      if (mode === 'clouds') {\n" +
                "        document.getElementById('status').innerText = '" + statusActiveSat + "';\n" +
                "        document.getElementById('modeLabel').innerText = '🛰️ الأقمار والسحب';\n" +
                "      } else {\n" +
                "        document.getElementById('status').innerText = '" + statusActiveRain + "';\n" +
                "        document.getElementById('modeLabel').innerText = '🌧️ رادار الأمطار';\n" +
                "      }\n" +
                "      var layers = getActiveLayers();\n" +
                "      if (layers.length > 0) {\n" +
                "        showFrame(layers.length - 1);\n" +
                "      }\n" +
                "    }\n" +
                "\n" +
                "    function showFrame(idx){\n" +
                "      var layers = getActiveLayers();\n" +
                "      var times = getActiveTimestamps();\n" +
                "      if (layers.length === 0) return;\n" +
                "      layers.forEach(function(l, i){ l.setOpacity(i === idx ? 0.78 : 0); });\n" +
                "      currentIdx = idx;\n" +
                "      if (times[idx]) {\n" +
                "        var d = new Date(times[idx] * 1000);\n" +
                "        document.getElementById('timeLabel').innerText = d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });\n" +
                "      }\n" +
                "    }\n" +
                "\n" +
                "    function togglePlay(){\n" +
                "      if (intervalId) {\n" +
                "        clearInterval(intervalId);\n" +
                "        intervalId = null;\n" +
                "        document.getElementById('playBtn').innerText = '▶';\n" +
                "      } else {\n" +
                "        startAnimation();\n" +
                "        document.getElementById('playBtn').innerText = '⏸';\n" +
                "      }\n" +
                "    }\n" +
                "\n" +
                "    function startAnimation(){\n" +
                "      if (intervalId) clearInterval(intervalId);\n" +
                "      intervalId = setInterval(function(){\n" +
                "        var layers = getActiveLayers();\n" +
                "        if (layers.length === 0) return;\n" +
                "        var next = (currentIdx + 1) % layers.length;\n" +
                "        showFrame(next);\n" +
                "      }, 850);\n" +
                "    }\n" +
                "  </script>\n" +
                "</body>\n" +
                "</html>";
    }

    private static Button createRadarSmallBtn(Context ctx, String text, boolean active, ThemePalette pal) {
        Button btn = new Button(ctx);
        btn.setText(text);
        btn.setTextSize(12f);
        btn.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        updateRadarSmallBtn(ctx, btn, active, pal);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(ctx, 36));
        lp.setMargins(dp(ctx, 4), 0, dp(ctx, 4), 0);
        btn.setLayoutParams(lp);
        btn.setPadding(dp(ctx, 12), 0, dp(ctx, 12), 0);
        return btn;
    }

    private static void updateRadarSmallBtn(Context ctx, Button btn, boolean active, ThemePalette pal) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(active ? pal.activeTabBg : pal.inactiveTabBg);
        bg.setCornerRadius(dp(ctx, 10));
        bg.setStroke(dp(ctx, 1f), active ? pal.accentColor : ((pal.cardStroke & 0x55FFFFFF) | 0x44000000));
        btn.setBackground(bg);
        btn.setTextColor(active ? 0xFFFFFFFF : pal.textSecondary);
    }

    private static int dp(Context ctx, float dpVal) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dpVal, ctx.getResources().getDisplayMetrics());
    }
}
