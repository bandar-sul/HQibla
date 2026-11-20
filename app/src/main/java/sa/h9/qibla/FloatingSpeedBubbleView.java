package sa.h9.qibla;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Automotive Overlay Service Floating View:
 * 1. Compact Mode: Floating HUD ball with Qibla glow ring, speedometer, and mini prayer/weather indicator,
 *    plus smart auto-dimming, edge magnetic snapping, and drag-to-hide support.
 * 2. Expanded Mode: Theme-synchronized capsule dock bar with clear Icon + Label tiles:
 *    - [🕌 المواقيت] Prayer countdown & 12h next Adhan
 *    - [🧭 القبلة]   Full 3D Qibla compass
 *    - [🌤️ الطقس]    Live Weather & Rain Radar (with live temperature)
 *    - [⚙️ الإعدادات] App Settings
 *    - [🤖 النظام]   Native Android Settings (Wi-Fi/Bluetooth)
 *    - Dynamic [🔇 إيقاف] Adhan audio stop button when Adhan is playing
 *    - Live speedometer anchor & close button [✕]
 */
public class FloatingSpeedBubbleView extends FrameLayout {

    public interface OnBubbleActionListener {
        void onSyncRequested();
    }

    public interface OnQuickAppsRequestedListener {
        void onQuickAppsRequested();
    }

    public interface OnDragLifecycleListener {
        void onDragStart();
        void onDragMove(float centerRawX, float centerRawY);
        boolean onDragEnd(float centerRawX, float centerRawY); // return true if hidden/dismissed
    }

    private BubbleCanvasView bubbleCanvasView;
    private LinearLayout islandCardLayout;

    // Top Tooltip Pill
    private LinearLayout tooltipBadgeLayout;
    private TextView tvTooltipText;

    // Horizontal Dock Bar
    private LinearLayout dockBarLayout;
    private TextView btnClose;
    private LinearLayout btnMuteAdhan;
    private TextView tvMuteIcon;
    private TextView tvMuteLabel;
    private final LinearLayout[] btnLayouts = new LinearLayout[6];
    private final TextView[] btnIcons = new TextView[6];
    private final TextView[] btnLabels = new TextView[6];
    private int selectedButtonIdx = -1;

    // Dock Speed Circle views
    private LinearLayout dockSpeedCircle;
    private TextView tvDockSpeed;
    private TextView tvDockUnit;

    private boolean isExpanded = false;
    private WindowManager windowManager;
    private WindowManager.LayoutParams layoutParams;
    private OnBubbleActionListener actionListener;
    private OnQuickAppsRequestedListener quickAppsListener;
    private OnDragLifecycleListener dragLifecycleListener;

    private float speedKmh = 0f;
    private int speedLimitKmh = 120;
    private float qiblaRelativeAngle = 0f;
    private boolean hasHeadingSignal = false;
    private boolean isMph = false;
    private boolean isEnglish = false;
    private int bubbleSizeDp = 86;
    private long dockTimeoutMs = 10000L; // 0 = manual close only
    private FullScreenWeatherDialog.ThemePalette themePalette;
    private final List<View> pinnedDockTileViews = new ArrayList<>();
    private int lastOverLimitDockState = -1;

    private String[] prayerNames;
    private String[] prayerTimes;
    private int currentNextIdx = -1;
    private int remainingSeconds = -1;

    private WeatherManager.WeatherInfo currentWeatherInfo;

    // Drag, snap & Touch handling
    private float initialTouchX;
    private float initialTouchY;
    private int initialX;
    private int initialY;
    private long touchDownTime;
    private boolean isDragging = false;
    private final int touchSlop;
    private ValueAnimator edgeSnapAnimator;

    // Auto-collapse timer
    private final Handler autoCollapseHandler = new Handler(Looper.getMainLooper());
    private final Runnable autoCollapseRunnable = () -> {
        if (isExpanded) collapseToBubble();
    };

    // Smart Auto-Dim timer when idle in compact mode
    private final Handler autoDimHandler = new Handler(Looper.getMainLooper());
    private final Runnable autoDimRunnable = () -> {
        if (!isExpanded && !isDragging && !SoundManager.isPlaying()) {
            animate().alpha(0.58f).setDuration(350L).start();
        }
    };

    // Periodic tick for countdown & mute button visibility
    private final Handler countdownHandler = new Handler(Looper.getMainLooper());
    private final Runnable countdownTickRunnable = new Runnable() {
        @Override
        public void run() {
            refreshPrayerCountdown();
            refreshMuteButtonVisibility();
            if (bubbleCanvasView != null && !isExpanded) {
                bubbleCanvasView.postInvalidate();
            }
            countdownHandler.postDelayed(this, isExpanded ? 1000L : 15000L);
        }
    };

    public FloatingSpeedBubbleView(Context context) {
        this(context, null);
    }

    public FloatingSpeedBubbleView(Context context, AttributeSet attrs) {
        super(context, attrs);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        init();
    }

    private void init() {
        themePalette = FullScreenWeatherDialog.getThemePalette(getContext());
        loadUserPreferences();
        initFallbackPrayerTimes();
        buildBubbleCanvasView();
        buildDockLayout();
        applyThemeColors();

        // Default state: compact circular ball
        bubbleCanvasView.setVisibility(View.VISIBLE);
        islandCardLayout.setVisibility(View.GONE);

        countdownHandler.post(countdownTickRunnable);
        scheduleAutoDim();
    }

    public void loadUserPreferences() {
        try {
            SharedPreferences sp = getContext().getSharedPreferences(MainActivity.UI_PREFS, Context.MODE_PRIVATE);
            isMph = sp.getBoolean("speed_mph", false);
            isEnglish = sp.getBoolean(MainActivity.KEY_ENGLISH_MODE, false);
            speedLimitKmh = sp.getInt(MainActivity.KEY_SPEED_LIMIT_KMH, 120);

            int sizePref = sp.getInt("floating_ball_size", 1);
            if (sizePref == 0) bubbleSizeDp = 72;
            else if (sizePref == 2) bubbleSizeDp = 102;
            else bubbleSizeDp = 86;

            int timeoutSec = sp.getInt("floating_dock_timeout_sec", 10);
            dockTimeoutMs = (timeoutSec <= 0) ? 0L : (timeoutSec * 1000L);
        } catch (Throwable ignored) { }
    }

    private boolean isOverSpeedLimit() {
        return speedLimitKmh > 0 && speedKmh >= speedLimitKmh;
    }

    public int getConfiguredBubbleSizePx() {
        return dp(bubbleSizeDp);
    }

    public void reloadPreferencesAndTheme() {
        loadUserPreferences();
        themePalette = FullScreenWeatherDialog.getThemePalette(getContext());
        refreshDockFonts();
        refreshPinnedAppsInDock();
        applyThemeColors();
        refreshDockLabels();
        refreshPrayerCountdown();
        refreshWeatherDisplay();
        refreshMuteButtonVisibility();

        if (!isExpanded && windowManager != null && layoutParams != null) {
            int sizePx = getConfiguredBubbleSizePx();
            layoutParams.width = sizePx;
            layoutParams.height = sizePx;

            if (bubbleCanvasView != null) {
                ViewGroup.LayoutParams lp = bubbleCanvasView.getLayoutParams();
                if (lp != null) {
                    lp.width = sizePx;
                    lp.height = sizePx;
                    bubbleCanvasView.setLayoutParams(lp);
                }
                bubbleCanvasView.postInvalidate();
            }

            int screenW = getResources().getDisplayMetrics().widthPixels;
            int screenH = getResources().getDisplayMetrics().heightPixels;
            layoutParams.x = Math.max(dp(6), Math.min(screenW - sizePx - dp(6), layoutParams.x));
            layoutParams.y = Math.max(dp(6), Math.min(screenH - sizePx - dp(12), layoutParams.y));
            try {
                windowManager.updateViewLayout(this, layoutParams);
            } catch (Throwable ignored) { }
        } else if (isExpanded) {
            resetAutoCollapseTimer();
        }
    }

    public void setWindowManager(WindowManager wm, WindowManager.LayoutParams lp) {
        this.windowManager = wm;
        this.layoutParams = lp;
    }

    public void setActionListener(OnBubbleActionListener listener) {
        this.actionListener = listener;
    }

    public void setQuickAppsListener(OnQuickAppsRequestedListener listener) {
        this.quickAppsListener = listener;
    }

    public void setDragLifecycleListener(OnDragLifecycleListener listener) {
        this.dragLifecycleListener = listener;
    }

    private void buildBubbleCanvasView() {
        bubbleCanvasView = new BubbleCanvasView(getContext());
        int sizePx = getConfiguredBubbleSizePx();
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(sizePx, sizePx);
        lp.gravity = Gravity.CENTER;
        addView(bubbleCanvasView, lp);
    }

    private void buildDockLayout() {
        Typeface arabicTf = FontHelper.getArabicTypeface(getContext());

        islandCardLayout = new LinearLayout(getContext());
        islandCardLayout.setOrientation(LinearLayout.VERTICAL);
        islandCardLayout.setGravity(Gravity.CENTER_HORIZONTAL);
        islandCardLayout.setPadding(dp(2), dp(2), dp(2), dp(2));

        // 1. Floating Tooltip Pill (displays 12h countdown / status above the dock bar)
        tooltipBadgeLayout = new LinearLayout(getContext());
        tooltipBadgeLayout.setOrientation(LinearLayout.HORIZONTAL);
        tooltipBadgeLayout.setGravity(Gravity.CENTER);
        tooltipBadgeLayout.setPadding(dp(16), dp(6), dp(16), dp(6));
        tooltipBadgeLayout.setOnClickListener(v -> showPrayerRemainingMessage());

        tvTooltipText = new TextView(getContext());
        tvTooltipText.setText(isEnglish ? "🕌 Next Prayer: --:--" : "🕌 جاري حساب متبقي الصلاة...");
        tvTooltipText.setTextSize(14.5f);
        tvTooltipText.setTypeface(arabicTf, Typeface.BOLD);
        tvTooltipText.setSingleLine(true);
        tvTooltipText.setEllipsize(TextUtils.TruncateAt.END);
        tooltipBadgeLayout.addView(tvTooltipText);

        LinearLayout.LayoutParams tipLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tipLp.bottomMargin = dp(6);
        islandCardLayout.addView(tooltipBadgeLayout, tipLp);

        // 2. Horizontal Dock Bar
        dockBarLayout = new LinearLayout(getContext());
        dockBarLayout.setOrientation(LinearLayout.HORIZONTAL);
        dockBarLayout.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        dockBarLayout.setGravity(Gravity.CENTER_VERTICAL);
        dockBarLayout.setPadding(dp(8), dp(6), dp(8), dp(6));

        // A. Close button [ ✕ ] on the left
        btnClose = new TextView(getContext());
        btnClose.setText("✕");
        btnClose.setTextSize(16f);
        btnClose.setTypeface(arabicTf, Typeface.BOLD);
        btnClose.setGravity(Gravity.CENTER);
        btnClose.setOnClickListener(v -> collapseToBubble());

        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(dp(42), dp(46));
        closeLp.rightMargin = dp(5);
        closeLp.leftMargin = dp(2);
        dockBarLayout.addView(btnClose, closeLp);

        // B. Dynamic Adhan Mute Button (Visible automatically whenever Adhan sound is playing)
        btnMuteAdhan = new LinearLayout(getContext());
        btnMuteAdhan.setOrientation(LinearLayout.VERTICAL);
        btnMuteAdhan.setGravity(Gravity.CENTER);
        GradientDrawable muteBg = new GradientDrawable();
        muteBg.setColor(0xFF991B1B);
        muteBg.setCornerRadius(dp(14));
        muteBg.setStroke(dp(1.8f), 0xFFFCA5A5);
        btnMuteAdhan.setBackground(muteBg);

        tvMuteIcon = new TextView(getContext());
        tvMuteIcon.setText("🔇");
        tvMuteIcon.setTextSize(16f);
        tvMuteIcon.setGravity(Gravity.CENTER);
        btnMuteAdhan.addView(tvMuteIcon);

        tvMuteLabel = new TextView(getContext());
        tvMuteLabel.setText(isEnglish ? "Stop" : "إيقاف");
        tvMuteLabel.setTextSize(9.5f);
        tvMuteLabel.setTextColor(0xFFFFFFFF);
        tvMuteLabel.setTypeface(arabicTf, Typeface.BOLD);
        tvMuteLabel.setGravity(Gravity.CENTER);
        tvMuteLabel.setSingleLine(true);
        btnMuteAdhan.addView(tvMuteLabel);

        btnMuteAdhan.setOnClickListener(v -> {
            SoundManager.stop();
            refreshMuteButtonVisibility();
            FullScreenSettingsDialog.showCompactToast(getContext(),
                    isEnglish ? "🔇 Prayer Audio Stopped" : "🔇 تم إيقاف صوت الأذان");
        });
        btnMuteAdhan.setVisibility(SoundManager.isPlaying() ? View.VISIBLE : View.GONE);

        LinearLayout.LayoutParams muteLp = new LinearLayout.LayoutParams(dp(54), dp(54));
        muteLp.rightMargin = dp(4);
        muteLp.leftMargin = dp(2);
        dockBarLayout.addView(btnMuteAdhan, muteLp);

        // C. Six Clear Icon + Label Quick-Action Tiles (including Quick Apps Launcher)
        String[] icons = new String[]{"🕌", "🧭", "🌤️", "🚀", "⚙️", "🤖"};
        for (int i = 0; i < 6; i++) {
            final int index = i;
            LinearLayout btnLayout = new LinearLayout(getContext());
            btnLayout.setOrientation(LinearLayout.VERTICAL);
            btnLayout.setGravity(Gravity.CENTER);
            btnLayout.setPadding(dp(2), dp(3), dp(2), dp(3));

            TextView tvIcon = new TextView(getContext());
            tvIcon.setText(icons[i]);
            tvIcon.setTextSize(17.5f);
            tvIcon.setGravity(Gravity.CENTER);
            tvIcon.setIncludeFontPadding(false);
            btnLayout.addView(tvIcon);

            TextView tvLabel = new TextView(getContext());
            tvLabel.setTextSize(9.5f);
            tvLabel.setTypeface(arabicTf, Typeface.BOLD);
            tvLabel.setGravity(Gravity.CENTER);
            tvLabel.setSingleLine(true);
            tvLabel.setEllipsize(TextUtils.TruncateAt.END);
            btnLayout.addView(tvLabel);

            btnLayout.setOnClickListener(v -> onDockButtonClicked(index));

            btnLayouts[i] = btnLayout;
            btnIcons[i] = tvIcon;
            btnLabels[i] = tvLabel;

            LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(dp(55), dp(54));
            btnLp.leftMargin = dp(2.5f);
            btnLp.rightMargin = dp(2.5f);
            dockBarLayout.addView(btnLayout, btnLp);
        }
        refreshDockLabels();
        refreshPinnedAppsInDock();

        // D. Speedometer Circle anchor on the right
        dockSpeedCircle = new LinearLayout(getContext());
        dockSpeedCircle.setOrientation(LinearLayout.VERTICAL);
        dockSpeedCircle.setGravity(Gravity.CENTER);
        dockSpeedCircle.setOnClickListener(v -> collapseToBubble());

        tvDockSpeed = new TextView(getContext());
        tvDockSpeed.setText("0");
        tvDockSpeed.setTextSize(17.5f);
        tvDockSpeed.setTypeface(arabicTf, Typeface.BOLD);
        tvDockSpeed.setGravity(Gravity.CENTER);
        tvDockSpeed.setIncludeFontPadding(false);
        dockSpeedCircle.addView(tvDockSpeed);

        tvDockUnit = new TextView(getContext());
        tvDockUnit.setText(isEnglish ? "km/h" : "كم/س");
        tvDockUnit.setTextSize(9f);
        tvDockUnit.setTypeface(arabicTf, Typeface.BOLD);
        tvDockUnit.setGravity(Gravity.CENTER);
        dockSpeedCircle.addView(tvDockUnit);

        LinearLayout.LayoutParams speedLp = new LinearLayout.LayoutParams(dp(54), dp(54));
        speedLp.leftMargin = dp(5);
        speedLp.rightMargin = dp(2);
        dockBarLayout.addView(dockSpeedCircle, speedLp);

        // Wrap dock bar in HorizontalScrollView so it never clips on split screens
        HorizontalScrollView dockScrollView = new HorizontalScrollView(getContext());
        dockScrollView.setHorizontalScrollBarEnabled(false);
        dockScrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        dockScrollView.addView(dockBarLayout, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        islandCardLayout.addView(dockScrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams islandLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        islandLp.gravity = Gravity.CENTER;
        addView(islandCardLayout, islandLp);
    }

    private void refreshDockFonts() {
        Typeface arabicTf = FontHelper.getSelectedTypeface(getContext());
        if (tvTooltipText != null) tvTooltipText.setTypeface(arabicTf, Typeface.BOLD);
        if (btnClose != null) btnClose.setTypeface(arabicTf, Typeface.BOLD);
        if (tvMuteLabel != null) tvMuteLabel.setTypeface(arabicTf, Typeface.BOLD);
        for (int i = 0; i < 6; i++) {
            if (btnLabels[i] != null) btnLabels[i].setTypeface(arabicTf, Typeface.BOLD);
        }
        if (tvDockSpeed != null) tvDockSpeed.setTypeface(arabicTf, Typeface.BOLD);
        if (tvDockUnit != null) tvDockUnit.setTypeface(arabicTf, Typeface.BOLD);
    }

    private void refreshPinnedAppsInDock() {
        if (dockBarLayout == null) return;
        for (View v : pinnedDockTileViews) {
            dockBarLayout.removeView(v);
        }
        pinnedDockTileViews.clear();

        List<String> pinned = QuickAppsOverlay.getOrderedPinnedPackages(getContext());
        if (pinned.isEmpty()) return;

        Context ctx = getContext();
        PackageManager pm = ctx.getPackageManager();
        Typeface arabicTf = FontHelper.getSelectedTypeface(ctx);
        FullScreenWeatherDialog.ThemePalette pal = (themePalette != null)
                ? themePalette : FullScreenWeatherDialog.getThemePalette(ctx);
        int tileFill = pal.isLight ? 0xFFF1F5F9 : ((pal.cardBg & 0x00FFFFFF) | 0xF0000000);
        int borderGold = pal.goldHighlight != 0 ? pal.goldHighlight : pal.accentColor;

        // Insert right after btnLayouts[3] (Quick Apps button)
        int insertIndex = (btnLayouts[3] != null) ? (dockBarLayout.indexOfChild(btnLayouts[3]) + 1) : dockBarLayout.getChildCount();
        if (insertIndex < 0) insertIndex = dockBarLayout.getChildCount();

        int added = 0;
        for (final String pkg : pinned) {
            if (added >= 3) break;
            try {
                final Intent launch = pm.getLaunchIntentForPackage(pkg);
                if (launch == null) continue;
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                CharSequence cs = pm.getApplicationLabel(ai);
                final String appLabel = (cs != null && cs.length() > 0) ? cs.toString().trim() : pkg;
                Drawable icon = pm.getApplicationIcon(ai);

                LinearLayout pinTile = new LinearLayout(ctx);
                pinTile.setOrientation(LinearLayout.VERTICAL);
                pinTile.setGravity(Gravity.CENTER);
                pinTile.setPadding(dp(3), dp(3), dp(3), dp(3));

                GradientDrawable tileBg = new GradientDrawable();
                tileBg.setColor(tileFill);
                tileBg.setCornerRadius(dp(13));
                tileBg.setStroke(dp(1.4f), borderGold);
                pinTile.setBackground(tileBg);

                ImageView iv = new ImageView(ctx);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                if (icon != null) iv.setImageDrawable(icon);
                iv.setContentDescription(appLabel);
                LinearLayout.LayoutParams ivLp = new LinearLayout.LayoutParams(dp(23), dp(23));
                ivLp.bottomMargin = dp(2);
                pinTile.addView(iv, ivLp);

                TextView tv = new TextView(ctx);
                tv.setText(appLabel);
                tv.setTextSize(8.8f);
                tv.setTypeface(arabicTf, Typeface.BOLD);
                tv.setTextColor(pal.textPrimary);
                tv.setGravity(Gravity.CENTER);
                tv.setSingleLine(true);
                tv.setEllipsize(TextUtils.TruncateAt.END);
                pinTile.addView(tv, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                pinTile.setOnClickListener(v -> {
                    try {
                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                        ctx.startActivity(launch);
                    } catch (Throwable ignored) { }
                    collapseToBubble();
                });

                LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(dp(55), dp(54));
                pLp.leftMargin = dp(2.5f);
                pLp.rightMargin = dp(2.5f);
                dockBarLayout.addView(pinTile, insertIndex + added, pLp);
                pinnedDockTileViews.add(pinTile);
                added++;
            } catch (Throwable ignored) { }
        }
    }

    private void refreshDockLabels() {
        if (btnLabels[0] == null) return;
        String[] labels = isEnglish
                ? new String[]{"Prayers", "Qibla", "Weather", "Apps", "Settings", "System"}
                : new String[]{"المواقيت", "القبلة", "الطقس", "التطبيقات", "الإعدادات", "النظام"};
        for (int i = 0; i < 6; i++) {
            if (btnLabels[i] != null) {
                btnLabels[i].setText(labels[i]);
            }
        }
        if (tvMuteLabel != null) {
            tvMuteLabel.setText(isEnglish ? "Stop" : "إيقاف");
        }
        if (tvDockUnit != null) {
            tvDockUnit.setText(isMph ? "mph" : (isEnglish ? "km/h" : "كم/س"));
        }
    }

    private void applyThemeColors() {
        FullScreenWeatherDialog.ThemePalette pal = (themePalette != null)
                ? themePalette : FullScreenWeatherDialog.getThemePalette(getContext());

        int glassBg = (pal.rootBg & 0x00FFFFFF) | 0xF2000000;
        int tileBg = pal.isLight ? 0xFFF1F5F9 : ((pal.cardBg & 0x00FFFFFF) | 0xF0000000);

        if (tooltipBadgeLayout != null) {
            GradientDrawable tipBg = new GradientDrawable();
            tipBg.setColor(glassBg);
            tipBg.setCornerRadius(dp(16));
            tipBg.setStroke(dp(1.5f), pal.accentColor);
            tooltipBadgeLayout.setBackground(tipBg);
        }
        if (tvTooltipText != null) {
            tvTooltipText.setTextColor(pal.isLight ? pal.accentColor : pal.goldHighlight);
        }
        if (dockBarLayout != null) {
            GradientDrawable barBg = new GradientDrawable();
            barBg.setColor(glassBg);
            barBg.setCornerRadius(dp(30));
            barBg.setStroke(dp(1.6f), pal.cardStroke);
            dockBarLayout.setBackground(barBg);
        }
        if (btnClose != null) {
            GradientDrawable closeBg = new GradientDrawable();
            closeBg.setColor(pal.isLight ? 0xFFE2E8F0 : 0x44000000);
            closeBg.setCornerRadius(dp(18));
            closeBg.setStroke(dp(1.2f), pal.cardStroke);
            btnClose.setBackground(closeBg);
            btnClose.setTextColor(pal.textSecondary);
        }
        applySpeedLimitDockStyle(pal);

        highlightDockButtons(selectedButtonIdx, pal.goldHighlight != 0 ? pal.goldHighlight : pal.accentColor);
        if (bubbleCanvasView != null) {
            bubbleCanvasView.postInvalidate();
        }
    }

    private void applySpeedLimitDockStyle(FullScreenWeatherDialog.ThemePalette pal) {
        if (pal == null) pal = FullScreenWeatherDialog.getThemePalette(getContext());
        boolean over = isOverSpeedLimit();
        lastOverLimitDockState = over ? 1 : 0;
        int tileBg = pal.isLight ? 0xFFF1F5F9 : ((pal.cardBg & 0x00FFFFFF) | 0xF0000000);
        if (dockSpeedCircle != null) {
            GradientDrawable speedBg = new GradientDrawable();
            speedBg.setColor(over ? (pal.isLight ? 0xFFFEE2E2 : 0xEE3B0D0D) : tileBg);
            speedBg.setCornerRadius(dp(27));
            speedBg.setStroke(dp(over ? 2.4f : 2.0f), over ? 0xFFFF5252 : pal.accentColor);
            dockSpeedCircle.setBackground(speedBg);
        }
        if (tvDockSpeed != null) {
            tvDockSpeed.setTextColor(over ? 0xFFFF6B6B : pal.textPrimary);
        }
        if (tvDockUnit != null) {
            tvDockUnit.setTextColor(over ? 0xFFFF8A80 : pal.accentColor);
        }
    }

    public void refreshMuteButtonVisibility() {
        if (btnMuteAdhan == null) return;
        boolean playing = SoundManager.isPlaying();
        int desired = playing ? View.VISIBLE : View.GONE;
        if (btnMuteAdhan.getVisibility() != desired) {
            btnMuteAdhan.setVisibility(desired);
            if (playing) {
                cancelAutoDim();
            }
        }
    }

    private void onDockButtonClicked(int index) {
        selectedButtonIdx = index;
        FullScreenWeatherDialog.ThemePalette pal = (themePalette != null)
                ? themePalette : FullScreenWeatherDialog.getThemePalette(getContext());

        int[] activeColors = new int[]{
                pal.goldHighlight != 0 ? pal.goldHighlight : 0xFFF59E0B, // Prayers
                0xFF10B981, // Qibla
                0xFFF59E0B, // Weather
                0xFFFFD768, // Quick Apps
                pal.accentColor, // App Settings
                0xFF38BDF8  // Android System
        };

        highlightDockButtons(index, activeColors[index]);

        switch (index) {
            case 0: // 🕌 Prayer Times & 12h Countdown
                String pName = (prayerNames != null && prayerNames.length > currentNextIdx && currentNextIdx >= 0)
                        ? prayerNames[currentNextIdx] : (isEnglish ? "Prayer" : "الصلاة");
                String cd = formatCountdown();
                String timeStr = (prayerTimes != null && currentNextIdx >= 0 && prayerTimes.length > currentNextIdx)
                        ? format12Hour(prayerTimes[currentNextIdx]) : "--:--";
                tvTooltipText.setText(isEnglish
                        ? ("🕌 " + pName + " in " + cd + " • " + timeStr)
                        : ("🕌 متبقي " + pName + ": " + cd + " • " + timeStr));
                showPrayerRemainingMessage();
                if (actionListener != null) actionListener.onSyncRequested();
                break;

            case 1: // 🧭 Open Full 3D Qibla Compass
                tvTooltipText.setText(isEnglish
                        ? "🧭 Opening Qibla Compass..."
                        : "🧭 جاري فتح بوصلة القبلة...");
                openMainActivity();
                collapseToBubble();
                break;

            case 2: // 🌤️ Live Weather & Rain Radar
                String weatherDesc = (currentWeatherInfo != null)
                        ? (currentWeatherInfo.getLocalizedDesc(isEnglish) + " " + String.format(Locale.US, "%.0f°", currentWeatherInfo.tempC))
                        : (isEnglish ? "Live Rain Radar" : "رادار الأمطار الحي");
                tvTooltipText.setText("🌤️ " + weatherDesc);
                openMainActivityWithExtra(MainActivity.EXTRA_OPEN_WEATHER);
                collapseToBubble();
                break;

            case 3: // 🚀 Quick Apps Launcher Overlay
                tvTooltipText.setText(isEnglish
                        ? "🚀 Opening Quick Apps..."
                        : "🚀 جاري فتح التطبيقات السريعة...");
                collapseToBubble();
                if (quickAppsListener != null) {
                    quickAppsListener.onQuickAppsRequested();
                } else {
                    openMainActivity();
                }
                break;

            case 4: // ⚙️ App Settings
                tvTooltipText.setText(isEnglish
                        ? "⚙️ Opening App Settings..."
                        : "⚙️ جاري فتح إعدادات التطبيق...");
                openMainActivityWithExtra(MainActivity.EXTRA_OPEN_SETTINGS);
                collapseToBubble();
                break;

            case 5: // 🤖 Native Android OS Settings
                tvTooltipText.setText(isEnglish
                        ? "🤖 Opening Android Settings..."
                        : "🤖 فتح إعدادات أندرويد...");
                openAndroidSettings();
                collapseToBubble();
                break;
        }

        resetAutoCollapseTimer();
    }

    private void highlightDockButtons(int activeIndex, int activeColor) {
        FullScreenWeatherDialog.ThemePalette pal = (themePalette != null)
                ? themePalette : FullScreenWeatherDialog.getThemePalette(getContext());
        int tileFill = pal.isLight ? 0xFFF1F5F9 : ((pal.cardBg & 0x00FFFFFF) | 0xF0000000);
        int activeFill = pal.isLight ? 0xFFE0F2FE : pal.activeTabBg;

        for (int i = 0; i < 6; i++) {
            if (btnLayouts[i] == null) continue;
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(13));
            if (i == activeIndex) {
                bg.setColor(activeFill);
                bg.setStroke(dp(2.2f), activeColor);
            } else {
                bg.setColor(tileFill);
                bg.setStroke(dp(1.4f), pal.cardStroke);
            }
            btnLayouts[i].setBackground(bg);
            if (btnLabels[i] != null) {
                btnLabels[i].setTextColor(i == activeIndex && !pal.isLight ? 0xFFFFFFFF : pal.textPrimary);
            }
        }
    }

    public void updateSpeedAndHeading(float speedKmh, float relativeQiblaAngle, boolean mph, boolean english) {
        updateSpeedAndHeading(speedKmh, relativeQiblaAngle, mph, english, true);
    }

    public void updateSpeedAndHeading(float speedKmh, float relativeQiblaAngle, boolean mph, boolean english, boolean hasHeading) {
        this.speedKmh = (speedKmh < 2.0f) ? 0f : speedKmh;
        this.qiblaRelativeAngle = relativeQiblaAngle;
        this.hasHeadingSignal = hasHeading;
        this.isMph = mph;
        this.isEnglish = english;

        float displaySpeed = isMph ? (this.speedKmh * 0.621371f) : this.speedKmh;
        int speedInt = (displaySpeed < 1.8f) ? 0 : Math.round(displaySpeed);
        String speedStr = String.valueOf(speedInt);

        if (tvDockSpeed != null) {
            tvDockSpeed.setText(speedStr);
        }
        if (tvDockUnit != null) {
            tvDockUnit.setText(isMph ? "mph" : (isEnglish ? "km/h" : "كم/س"));
        }
        int overState = isOverSpeedLimit() ? 1 : 0;
        if (overState != lastOverLimitDockState) {
            applySpeedLimitDockStyle(themePalette);
        }
        if (bubbleCanvasView != null) {
            bubbleCanvasView.postInvalidate();
        }
    }

    public void updateWeather(WeatherManager.WeatherInfo info) {
        this.currentWeatherInfo = info;
        post(this::refreshWeatherDisplay);
    }

    private void refreshWeatherDisplay() {
        if (currentWeatherInfo == null) return;
        if (btnIcons[2] != null) {
            String icon = (currentWeatherInfo.icon != null && !currentWeatherInfo.icon.isEmpty())
                    ? currentWeatherInfo.icon : "🌤️";
            btnIcons[2].setText(icon);
        }
        if (btnLabels[2] != null) {
            btnLabels[2].setText(String.format(Locale.US, "%.0f° %s",
                    currentWeatherInfo.tempC, isEnglish ? "Weather" : "الطقس"));
        }
        if (selectedButtonIdx == 2 && tvTooltipText != null) {
            String desc = currentWeatherInfo.getLocalizedDesc(isEnglish);
            String temp = String.format(Locale.US, "%.0f°%s", currentWeatherInfo.tempC, isEnglish ? "C" : "م");
            tvTooltipText.setText("🌤️ " + desc + " " + temp);
        }
        if (bubbleCanvasView != null) {
            bubbleCanvasView.postInvalidate();
        }
    }

    public void updatePrayerTimes(String[] names, String[] times, boolean english) {
        if (names != null && times != null) {
            this.prayerNames = names;
            this.prayerTimes = times;
            this.isEnglish = english;
            post(() -> {
                refreshDockLabels();
                refreshPrayerCountdown();
                if (bubbleCanvasView != null) bubbleCanvasView.postInvalidate();
            });
        }
    }

    private void initFallbackPrayerTimes() {
        try {
            Calendar now = Calendar.getInstance();
            PrayerTimesCalculator.Result fallback = PrayerTimesCalculator.calculate(
                    24.7136, 46.6753, now, 0, new int[]{0, 0, 0, 0, 0, 0}, 4, isEnglish);
            String[] defaultNames = isEnglish
                    ? new String[]{"Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"}
                    : new String[]{"الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"};
            this.prayerNames = defaultNames;
            this.prayerTimes = fallback.times24;
            refreshPrayerCountdown();
        } catch (Throwable ignored) { }
    }

    public void expandToIsland() {
        if (isExpanded || windowManager == null || layoutParams == null) return;
        isExpanded = true;
        cancelEdgeSnap();
        cancelAutoDim();

        themePalette = FullScreenWeatherDialog.getThemePalette(getContext());
        refreshDockFonts();
        refreshPinnedAppsInDock();
        applyThemeColors();
        refreshDockLabels();
        refreshPrayerCountdown();
        refreshWeatherDisplay();
        refreshMuteButtonVisibility();

        countdownHandler.removeCallbacks(countdownTickRunnable);
        countdownHandler.post(countdownTickRunnable);
        resetAutoCollapseTimer();

        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;

        layoutParams.gravity = Gravity.TOP | Gravity.LEFT;

        int pinnedExtraDp = pinnedDockTileViews.size() * 60;
        int targetW = Math.min(screenW - dp(16), (SoundManager.isPlaying() ? dp(572) : dp(518)) + dp(pinnedExtraDp));
        int targetH = dp(118);

        int bubbleCenterX = layoutParams.x + layoutParams.width / 2;
        int targetX = bubbleCenterX - targetW / 2;

        int minX = dp(6);
        int maxX = Math.max(minX, screenW - targetW - dp(6));
        int minY = dp(6);
        int maxY = Math.max(minY, screenH - targetH - dp(12));

        layoutParams.x = Math.max(minX, Math.min(maxX, targetX));
        layoutParams.y = Math.max(minY, Math.min(maxY, layoutParams.y));
        layoutParams.width = targetW;
        layoutParams.height = targetH;

        try {
            windowManager.updateViewLayout(this, layoutParams);
        } catch (Throwable ignored) { }

        bubbleCanvasView.setVisibility(View.GONE);
        islandCardLayout.setVisibility(View.VISIBLE);
    }

    public void collapseToBubble() {
        if (!isExpanded || windowManager == null || layoutParams == null) return;
        isExpanded = false;

        autoCollapseHandler.removeCallbacks(autoCollapseRunnable);

        int sizePx = getConfiguredBubbleSizePx();

        int islandCenterX = layoutParams.x + layoutParams.width / 2;
        int targetX = islandCenterX - sizePx / 2;

        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;

        int minX = dp(8);
        int maxX = Math.max(minX, screenW - sizePx - dp(8));
        int minY = dp(6);
        int maxY = Math.max(minY, screenH - sizePx - dp(12));

        // Snap collapsed ball nicely toward nearest horizontal edge so map center stays clear
        int snappedX = (targetX + sizePx / 2 < screenW / 2) ? minX : maxX;

        layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
        layoutParams.x = Math.max(minX, Math.min(maxX, snappedX));
        layoutParams.y = Math.max(minY, Math.min(maxY, layoutParams.y));
        layoutParams.width = sizePx;
        layoutParams.height = sizePx;

        try {
            windowManager.updateViewLayout(this, layoutParams);
        } catch (Throwable ignored) { }

        islandCardLayout.setVisibility(View.GONE);
        bubbleCanvasView.setVisibility(View.VISIBLE);
        saveCustomPosition(layoutParams.x, layoutParams.y);
        scheduleAutoDim();
    }

    private void resetAutoCollapseTimer() {
        autoCollapseHandler.removeCallbacks(autoCollapseRunnable);
        if (dockTimeoutMs > 0L) {
            autoCollapseHandler.postDelayed(autoCollapseRunnable, dockTimeoutMs);
        }
    }

    private void cancelAutoDim() {
        autoDimHandler.removeCallbacks(autoDimRunnable);
        animate().alpha(1.0f).setDuration(140L).start();
    }

    private void scheduleAutoDim() {
        autoDimHandler.removeCallbacks(autoDimRunnable);
        if (!isExpanded && !isDragging && !SoundManager.isPlaying()) {
            autoDimHandler.postDelayed(autoDimRunnable, 6500L);
        }
    }

    private void cancelEdgeSnap() {
        if (edgeSnapAnimator != null) {
            edgeSnapAnimator.cancel();
            edgeSnapAnimator = null;
        }
    }

    private void snapToNearestEdge() {
        if (isExpanded || windowManager == null || layoutParams == null) return;
        cancelEdgeSnap();

        int screenW = getResources().getDisplayMetrics().widthPixels;
        int w = layoutParams.width;
        int minX = dp(8);
        int maxX = Math.max(minX, screenW - w - dp(8));
        final int startX = layoutParams.x;
        final int targetX = (startX + w / 2 < screenW / 2) ? minX : maxX;

        if (startX == targetX) {
            saveCustomPosition(targetX, layoutParams.y);
            return;
        }

        edgeSnapAnimator = ValueAnimator.ofInt(startX, targetX);
        edgeSnapAnimator.setDuration(190L);
        edgeSnapAnimator.setInterpolator(new DecelerateInterpolator());
        edgeSnapAnimator.addUpdateListener(anim -> {
            if (isExpanded || windowManager == null || layoutParams == null) return;
            layoutParams.x = (Integer) anim.getAnimatedValue();
            try {
                windowManager.updateViewLayout(FloatingSpeedBubbleView.this, layoutParams);
            } catch (Throwable ignored) { }
        });
        edgeSnapAnimator.start();
        saveCustomPosition(targetX, layoutParams.y);
    }

    private void showPrayerRemainingMessage() {
        if (prayerTimes == null || prayerNames == null || currentNextIdx < 0) return;
        String pName = (prayerNames.length > currentNextIdx) ? prayerNames[currentNextIdx] : "";
        int diff = remainingSeconds;
        int dh = Math.max(0, diff / 3600);
        int dm = Math.max(0, (diff % 3600) / 60);
        int ds = Math.max(0, diff % 60);

        String msg;
        if (isEnglish) {
            msg = String.format(Locale.US, "🕌 %s in %02d:%02d:%02d (Adhan %s)",
                    pName, dh, dm, ds, format12Hour(prayerTimes[currentNextIdx]));
        } else {
            msg = String.format(Locale.US, "🕌 المتبقي على %s: %d س %d د (الأذان %s)",
                    pName, dh, dm, format12Hour(prayerTimes[currentNextIdx]));
        }
        FullScreenSettingsDialog.showCompactToast(getContext(), msg);
    }

    private String formatCountdown() {
        if (remainingSeconds < 0) return "00:00:00";
        int diff = remainingSeconds;
        int dh = diff / 3600;
        int dm = (diff % 3600) / 60;
        int ds = diff % 60;
        return String.format(Locale.US, "%02d:%02d:%02d", dh, dm, ds);
    }

    private String formatCompactPrayerMini() {
        if (prayerNames == null || currentNextIdx < 0 || remainingSeconds < 0) return "";
        String pName = (prayerNames.length > currentNextIdx) ? prayerNames[currentNextIdx] : "";
        int totalMin = Math.max(1, remainingSeconds / 60);
        String dur;
        if (totalMin >= 60) {
            int h = totalMin / 60;
            int m = totalMin % 60;
            dur = isEnglish ? (h + "h" + m + "m") : (h + "س" + m + "د");
        } else {
            dur = isEnglish ? (totalMin + "m") : (totalMin + "د");
        }
        if (currentWeatherInfo != null) {
            return String.format(Locale.US, "%s • %.0f°", dur, currentWeatherInfo.tempC);
        }
        return pName + " " + dur;
    }

    private void refreshPrayerCountdown() {
        if (prayerTimes == null || prayerTimes.length < 6) return;

        Calendar now = Calendar.getInstance();
        int currentSec = now.get(Calendar.HOUR_OF_DAY) * 3600 + now.get(Calendar.MINUTE) * 60 + now.get(Calendar.SECOND);

        int nextIdx = -1;
        int nextTargetSec = -1;

        for (int i = 0; i < prayerTimes.length; i++) {
            int m = parseMinutes(prayerTimes[i]);
            if (m >= 0) {
                int sec = m * 60;
                if (sec > currentSec) {
                    nextIdx = i;
                    nextTargetSec = sec;
                    break;
                }
            }
        }

        if (nextIdx < 0) {
            nextIdx = 0;
            int fajrSec = parseMinutes(prayerTimes[0]) * 60;
            nextTargetSec = fajrSec + 24 * 3600;
        }

        this.currentNextIdx = nextIdx;
        this.remainingSeconds = nextTargetSec - currentSec;

        String pName = (prayerNames != null && prayerNames.length > nextIdx) ? prayerNames[nextIdx] : "";
        String countdownStr = formatCountdown();
        String timeStr = (prayerTimes.length > nextIdx) ? format12Hour(prayerTimes[nextIdx]) : "--:--";

        if (tvTooltipText != null && (selectedButtonIdx == 0 || selectedButtonIdx == -1)) {
            tvTooltipText.setText(isEnglish
                    ? ("🕌 " + pName + " in " + countdownStr + " • " + timeStr)
                    : ("🕌 متبقي " + pName + ": " + countdownStr + " • " + timeStr));
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (layoutParams == null || windowManager == null) return super.onInterceptTouchEvent(ev);

        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                cancelAutoDim();
                cancelEdgeSnap();
                initialX = layoutParams.x;
                initialY = layoutParams.y;
                initialTouchX = ev.getRawX();
                initialTouchY = ev.getRawY();
                touchDownTime = System.currentTimeMillis();
                isDragging = false;
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = Math.abs(ev.getRawX() - initialTouchX);
                float dy = Math.abs(ev.getRawY() - initialTouchY);
                if (dx > touchSlop || dy > touchSlop) {
                    isDragging = true;
                    if (!isExpanded && dragLifecycleListener != null) {
                        dragLifecycleListener.onDragStart();
                    }
                    return true;
                }
                break;
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (layoutParams == null || windowManager == null) return super.onTouchEvent(event);

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                cancelAutoDim();
                cancelEdgeSnap();
                initialX = layoutParams.x;
                initialY = layoutParams.y;
                initialTouchX = event.getRawX();
                initialTouchY = event.getRawY();
                touchDownTime = System.currentTimeMillis();
                isDragging = false;
                if (isExpanded) {
                    resetAutoCollapseTimer();
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                float deltaX = event.getRawX() - initialTouchX;
                float deltaY = event.getRawY() - initialTouchY;

                if (!isDragging && (Math.abs(deltaX) > touchSlop || Math.abs(deltaY) > touchSlop)) {
                    isDragging = true;
                    if (!isExpanded && dragLifecycleListener != null) {
                        dragLifecycleListener.onDragStart();
                    }
                }

                if (isDragging) {
                    int screenW = getResources().getDisplayMetrics().widthPixels;
                    int screenH = getResources().getDisplayMetrics().heightPixels;
                    int w = layoutParams.width;
                    int h = layoutParams.height;

                    int minX = dp(6);
                    int maxX = Math.max(minX, screenW - w - dp(6));
                    int minY = dp(6);
                    int maxY = Math.max(minY, screenH - h - dp(12));

                    int newX = initialX + (int) deltaX;
                    int newY = initialY + (int) deltaY;

                    layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
                    layoutParams.x = Math.max(minX, Math.min(maxX, newX));
                    layoutParams.y = Math.max(minY, Math.min(maxY, newY));

                    try {
                        windowManager.updateViewLayout(this, layoutParams);
                    } catch (Throwable ignored) { }

                    if (!isExpanded && dragLifecycleListener != null) {
                        int[] loc = new int[2];
                        getLocationOnScreen(loc);
                        dragLifecycleListener.onDragMove(loc[0] + w * 0.5f, loc[1] + h * 0.5f);
                    }
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                long duration = System.currentTimeMillis() - touchDownTime;
                if (!isDragging && event.getAction() == MotionEvent.ACTION_UP && duration < 300) {
                    if (!isExpanded) {
                        expandToIsland();
                    } else {
                        collapseToBubble();
                    }
                } else if (isDragging) {
                    boolean dismissed = false;
                    if (!isExpanded && dragLifecycleListener != null) {
                        int[] loc = new int[2];
                        getLocationOnScreen(loc);
                        dismissed = dragLifecycleListener.onDragEnd(
                                loc[0] + layoutParams.width * 0.5f,
                                loc[1] + layoutParams.height * 0.5f);
                    }
                    if (!dismissed && !isExpanded) {
                        snapToNearestEdge();
                    } else if (!dismissed) {
                        saveCustomPosition(layoutParams.x, layoutParams.y);
                    }
                }
                isDragging = false;
                if (!isExpanded) {
                    scheduleAutoDim();
                }
                return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onDetachedFromWindow() {
        countdownHandler.removeCallbacks(countdownTickRunnable);
        autoCollapseHandler.removeCallbacks(autoCollapseRunnable);
        autoDimHandler.removeCallbacks(autoDimRunnable);
        cancelEdgeSnap();
        super.onDetachedFromWindow();
    }

    private void saveCustomPosition(int x, int y) {
        try {
            getContext().getSharedPreferences(MainActivity.UI_PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putInt("floating_ball_pos_x", x)
                    .putInt("floating_ball_pos_y", y)
                    .apply();
        } catch (Throwable ignored) { }
    }

    private void openMainActivity() {
        try {
            Context ctx = getContext();
            Intent intent = new Intent(ctx, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            ctx.startActivity(intent);
        } catch (Throwable ignored) { }
    }

    private void openMainActivityWithExtra(String extraKey) {
        try {
            Context ctx = getContext();
            Intent intent = new Intent(ctx, MainActivity.class);
            if (extraKey != null) {
                intent.putExtra(extraKey, true);
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            ctx.startActivity(intent);
        } catch (Throwable ignored) { }
    }

    private void openAndroidSettings() {
        Context ctx = getContext();
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setClassName("com.android.settings", "com.android.settings.Settings");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
            return;
        } catch (Throwable ignored) { }

        try {
            Intent intent = new Intent();
            intent.setClassName("com.android.settings", "com.android.settings.Settings");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
            return;
        } catch (Throwable ignored) { }

        try {
            Intent sysIntent = new Intent(Settings.ACTION_SETTINGS);
            sysIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(sysIntent);
        } catch (Throwable ignored) { }
    }

    private int parseMinutes(String s) {
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
            return Integer.parseInt(parts[0].trim()) * 60 + Integer.parseInt(parts[1].trim());
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private String format12Hour(String value) {
        int minutes = parseMinutes(value);
        if (minutes < 0) return value == null ? "--:--" : value;
        int hour24 = minutes / 60;
        int minute = minutes % 60;
        String ampm = isEnglish ? (hour24 >= 12 ? "PM" : "AM") : (hour24 >= 12 ? "م" : "ص");
        int hour12 = hour24 % 12;
        if (hour12 == 0) hour12 = 12;
        return String.format(Locale.US, "%d:%02d %s", hour12, minute, ampm);
    }

    private int dp(float val) {
        return Math.round(val * getResources().getDisplayMetrics().density);
    }

    private int dp(int val) {
        return Math.round(val * getResources().getDisplayMetrics().density);
    }

    /**
     * Inner custom View rendering the circular HUD ball with:
     * - Theme-synchronized dark/OLED/Pearl styling
     * - Luminous emerald glow when vehicle aligns with Qibla
     * - Rotating Qibla triangle pointer
     * - Standstill deadband speed + unit
     * - Compact bottom telemetry (Next Prayer min + Weather Temp)
     */
    private class BubbleCanvasView extends View {
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glowRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint speedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint unitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint miniInfoPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path arrowPath = new Path();

        private int lastFontIdx = -1;

        public BubbleCanvasView(Context context) {
            super(context);
            bgPaint.setStyle(Paint.Style.FILL);

            glowRingPaint.setStyle(Paint.Style.STROKE);
            glowRingPaint.setStrokeWidth(dp(5.0f));
            glowRingPaint.setColor(0x5500F5C8);

            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeWidth(dp(2.3f));

            arrowPaint.setStyle(Paint.Style.FILL);

            speedPaint.setTextAlign(Paint.Align.CENTER);
            unitPaint.setTextAlign(Paint.Align.CENTER);
            miniInfoPaint.setTextAlign(Paint.Align.CENTER);
            syncPaintTypefaces(context);
        }

        private void syncPaintTypefaces(Context context) {
            int fontIdx = QiblaView.FONT_DEFAULT;
            try {
                fontIdx = context.getSharedPreferences(MainActivity.UI_PREFS, Context.MODE_PRIVATE)
                        .getInt(MainActivity.KEY_FONT_INDEX, QiblaView.FONT_DEFAULT);
            } catch (Throwable ignored) { }
            if (fontIdx != lastFontIdx) {
                lastFontIdx = fontIdx;
                Typeface arabicTf = FontHelper.getSelectedTypeface(context);
                speedPaint.setTypeface(Typeface.create(arabicTf, Typeface.BOLD));
                unitPaint.setTypeface(arabicTf);
                miniInfoPaint.setTypeface(Typeface.create(arabicTf, Typeface.BOLD));
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            syncPaintTypefaces(getContext());

            FullScreenWeatherDialog.ThemePalette pal = (themePalette != null)
                    ? themePalette : FullScreenWeatherDialog.getThemePalette(getContext());

            float cx = w * 0.5f;
            float cy = h * 0.5f;
            float radius = Math.min(cx, cy) - dp(3.5f);

            int innerColor = pal.isLight ? 0xFAFFFFFF : ((pal.cardBg & 0x00FFFFFF) | 0xF0000000);
            int outerColor = pal.isLight ? 0xF5DDE8EE : ((pal.rootBg & 0x00FFFFFF) | 0xF5000000);

            RadialGradient grad = new RadialGradient(cx, cy, radius,
                    new int[]{innerColor, outerColor}, null, Shader.TileMode.CLAMP);
            bgPaint.setShader(grad);
            canvas.drawCircle(cx, cy, radius, bgPaint);

            // Check Qibla alignment (within +/- 12 degrees) and GPS Speed Limit Alert
            float signedAngle = ((qiblaRelativeAngle + 540f) % 360f) - 180f;
            boolean qiblaAligned = hasHeadingSignal && Math.abs(signedAngle) <= 12f;
            boolean overLimit = isOverSpeedLimit();

            if (overLimit) {
                glowRingPaint.setColor(0x66FF5252);
                canvas.drawCircle(cx, cy, radius - dp(1.0f), glowRingPaint);
                ringPaint.setColor(0xFFFF5252);
                ringPaint.setStrokeWidth(dp(3.0f));
                arrowPaint.setColor(qiblaAligned ? 0xFF00F5C8 : (pal.goldHighlight != 0 ? pal.goldHighlight : 0xFFF59E0B));
            } else if (qiblaAligned) {
                glowRingPaint.setColor(0x5500F5C8);
                canvas.drawCircle(cx, cy, radius - dp(1.0f), glowRingPaint);
                ringPaint.setColor(0xFF00F5C8);
                ringPaint.setStrokeWidth(dp(2.8f));
                arrowPaint.setColor(0xFF00F5C8);
            } else {
                ringPaint.setColor(speedKmh > 1.8f ? pal.accentColor : pal.cardStroke);
                ringPaint.setStrokeWidth(dp(2.2f));
                arrowPaint.setColor(pal.goldHighlight != 0 ? pal.goldHighlight : 0xFFF59E0B);
            }
            canvas.drawCircle(cx, cy, radius - dp(1.2f), ringPaint);

            // Rotating Qibla indicator triangle on outer ring
            canvas.save();
            canvas.rotate(qiblaRelativeAngle, cx, cy);
            float arrowTop = cy - radius + dp(2.4f);
            float arrowBottom = arrowTop + dp(7.8f);
            float arrowWidth = dp(7.2f);
            arrowPath.reset();
            arrowPath.moveTo(cx, arrowTop);
            arrowPath.lineTo(cx - arrowWidth * 0.5f, arrowBottom);
            arrowPath.lineTo(cx + arrowWidth * 0.5f, arrowBottom);
            arrowPath.close();
            canvas.drawPath(arrowPath, arrowPaint);
            canvas.restore();

            // Speed calculation with standstill deadband
            float displaySpeed = isMph ? (speedKmh * 0.621371f) : speedKmh;
            int speedInt = (displaySpeed < 1.8f) ? 0 : Math.round(displaySpeed);
            String speedStr = String.valueOf(speedInt);

            speedPaint.setColor(overLimit ? 0xFFFF6B6B : pal.textPrimary);
            speedPaint.setTextSize(radius * 0.56f);
            float speedY = cy + (speedPaint.getTextSize() * 0.06f);
            canvas.drawText(speedStr, cx, speedY, speedPaint);

            // Unit text (كم/س or mph)
            String unitStr = isMph ? "mph" : (isEnglish ? "km/h" : "كم/س");
            unitPaint.setColor(overLimit ? 0xFFFF8A80 : pal.accentColor);
            unitPaint.setTextSize(radius * 0.21f);
            float unitY = speedY + unitPaint.getTextSize() * 1.18f;
            canvas.drawText(unitStr, cx, unitY, unitPaint);

            // Mini Prayer Countdown + Temp line safely fitted inside bottom chord
            String miniInfo = formatCompactPrayerMini();
            if (!miniInfo.isEmpty()) {
                miniInfoPaint.setColor(pal.goldHighlight != 0 ? pal.goldHighlight : pal.accentColor);
                float baseTextSize = radius * 0.20f;
                miniInfoPaint.setTextSize(baseTextSize);
                float infoY = cy + radius * 0.65f;
                float dy = infoY - cy;
                float maxChordW = (float) Math.sqrt(Math.max(1f, radius * radius - dy * dy)) * 1.65f;
                float measured = miniInfoPaint.measureText(miniInfo);
                if (measured > maxChordW && measured > 0) {
                    miniInfoPaint.setTextSize(baseTextSize * (maxChordW / measured));
                }
                canvas.drawText(miniInfo, cx, infoY, miniInfoPaint);
            }
        }
    }
}
