package sa.h9.qibla;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Locale;

/**
 * Dynamic Island Notification Capsule (الجزيرة التفاعلية لمواقيت الصلاة):
 * Floats at the top center of the screen, showing live countdown to the next prayer
 * in a sleek, luxury obsidian capsule that smoothly expands on tap with full prayer details.
 */
public class DynamicIslandView extends FrameLayout {

    public interface OnIslandActionListener {
        void onSyncRequested();
        void onSettingsRequested();
    }

    private static final int STATE_NORMAL = 0;
    private static final int STATE_APPROACHING = 1;
    private static final int STATE_ADHAN = 2;

    private boolean isExpanded = false;
    private boolean isAnimating = false;
    private boolean isEnglish = false;

    private GradientDrawable bgDrawable;
    private LinearLayout compactLayout;
    private LinearLayout expandedLayout;

    // Compact UI Elements
    private TextView tvCompactIcon;
    private TextView tvCompactTitle;
    private TextView tvCompactDot;
    private TextView tvCompactCountdown;
    private View compactPulseDot;

    // Expanded UI Elements
    private TextView tvExpTitle;
    private TextView tvExpAdhanTime;
    private TextView tvExpCountdown;
    private TextView tvExpSub;
    private ProgressBar expProgressBar;
    private LinearLayout expChipsContainer;
    private TextView btnExpSync;
    private TextView btnExpClose;

    private String[] prayerNames;
    private String[] prayerTimes;
    private int currentNextIdx = -1;
    private int remainingSeconds = -1;
    private int currentState = STATE_NORMAL;

    private final Handler autoCollapseHandler = new Handler(Looper.getMainLooper());
    private final Runnable autoCollapseRunnable = () -> {
        if (isExpanded) collapse();
    };

    private OnIslandActionListener actionListener;

    public DynamicIslandView(Context context) {
        this(context, null);
    }

    public DynamicIslandView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setClipChildren(true);
        setClipToPadding(true);

        // Background capsule styling: luxury dark obsidian with glowing rim
        bgDrawable = new GradientDrawable();
        bgDrawable.setColor(0xF4090F1B); // 95% opacity obsidian
        bgDrawable.setCornerRadius(dp(20));
        bgDrawable.setStroke(dp(1.6f), 0xFF10B981); // Emerald glow
        setBackground(bgDrawable);

        buildCompactLayout();
        buildExpandedLayout();

        // Tapping compact capsule triggers smooth spring expansion
        setOnClickListener(v -> {
            if (!isAnimating) {
                if (!isExpanded) expand();
                else collapse();
            }
        });
        compactLayout.setOnClickListener(v -> {
            if (!isAnimating) {
                if (!isExpanded) expand();
                else collapse();
            }
        });

        initFallbackTimes();
    }

    public void setActionListener(OnIslandActionListener listener) {
        this.actionListener = listener;
    }

    private void buildCompactLayout() {
        compactLayout = new LinearLayout(getContext());
        compactLayout.setOrientation(LinearLayout.HORIZONTAL);
        compactLayout.setGravity(Gravity.CENTER_VERTICAL);
        compactLayout.setPadding(dp(12), dp(4), dp(12), dp(4));

        tvCompactIcon = new TextView(getContext());
        tvCompactIcon.setText("🕌");
        tvCompactIcon.setTextSize(14f);
        compactLayout.addView(tvCompactIcon);

        tvCompactTitle = new TextView(getContext());
        tvCompactTitle.setText(isEnglish ? "Next Prayer" : "الصلاة القادمة");
        tvCompactTitle.setTextSize(13.5f);
        tvCompactTitle.setTextColor(0xFFE2E8F0);
        tvCompactTitle.setTypeface(FontHelper.getArabicTypeface(getContext()), Typeface.BOLD);
        tvCompactTitle.setSingleLine(true);
        tvCompactTitle.setPadding(dp(6), 0, dp(4), 0);
        compactLayout.addView(tvCompactTitle);

        tvCompactDot = new TextView(getContext());
        tvCompactDot.setText("•");
        tvCompactDot.setTextSize(12f);
        tvCompactDot.setTextColor(0xFF64748B);
        tvCompactDot.setPadding(dp(2), 0, dp(6), 0);
        compactLayout.addView(tvCompactDot);

        tvCompactCountdown = new TextView(getContext());
        tvCompactCountdown.setText("--:--:--");
        tvCompactCountdown.setTextSize(13.5f);
        tvCompactCountdown.setTextColor(0xFF34D399); // Crisp Emerald
        tvCompactCountdown.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tvCompactCountdown.setSingleLine(true);
        compactLayout.addView(tvCompactCountdown);

        compactPulseDot = new View(getContext());
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(0xFF10B981);
        compactPulseDot.setBackground(dotBg);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(6), dp(6));
        dotLp.leftMargin = dp(8);
        dotLp.rightMargin = dp(2);
        compactLayout.addView(compactPulseDot, dotLp);

        addView(compactLayout, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void buildExpandedLayout() {
        expandedLayout = new LinearLayout(getContext());
        expandedLayout.setOrientation(LinearLayout.VERTICAL);
        expandedLayout.setPadding(dp(16), dp(12), dp(16), dp(12));
        expandedLayout.setVisibility(View.GONE);
        expandedLayout.setAlpha(0f);

        // 1. Header row
        LinearLayout headerRow = new LinearLayout(getContext());
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView icHeader = new TextView(getContext());
        icHeader.setText("🕌 ");
        icHeader.setTextSize(15f);
        headerRow.addView(icHeader);

        tvExpTitle = new TextView(getContext());
        tvExpTitle.setText(isEnglish ? "Next Prayer: Loading..." : "الصلاة القادمة: جاري التحديث...");
        tvExpTitle.setTextSize(15f);
        tvExpTitle.setTextColor(0xFFF59E0B); // Amber / Gold
        tvExpTitle.setTypeface(FontHelper.getArabicTypeface(getContext()), Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        headerRow.addView(tvExpTitle, titleLp);

        tvExpAdhanTime = new TextView(getContext());
        tvExpAdhanTime.setText("🕒 --:--");
        tvExpAdhanTime.setTextSize(13.5f);
        tvExpAdhanTime.setTextColor(0xFFE2E8F0);
        tvExpAdhanTime.setTypeface(FontHelper.getArabicTypeface(getContext()), Typeface.BOLD);
        tvExpAdhanTime.setPadding(dp(8), 0, dp(10), 0);
        headerRow.addView(tvExpAdhanTime);

        btnExpClose = new TextView(getContext());
        btnExpClose.setText(" ✕ ");
        btnExpClose.setTextSize(14f);
        btnExpClose.setTextColor(0xFF94A3B8);
        btnExpClose.setPadding(dp(6), dp(2), dp(6), dp(2));
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setColor(0x33334155);
        closeBg.setCornerRadius(dp(10));
        btnExpClose.setBackground(closeBg);
        btnExpClose.setOnClickListener(v -> collapse());
        headerRow.addView(btnExpClose);

        expandedLayout.addView(headerRow);

        // 2. Hero Countdown Center
        LinearLayout heroBox = new LinearLayout(getContext());
        heroBox.setOrientation(LinearLayout.HORIZONTAL);
        heroBox.setGravity(Gravity.CENTER_VERTICAL);
        heroBox.setPadding(0, dp(8), 0, dp(4));

        tvExpCountdown = new TextView(getContext());
        tvExpCountdown.setText("00:00:00");
        tvExpCountdown.setTextSize(26f);
        tvExpCountdown.setTextColor(0xFFFFFFFF);
        tvExpCountdown.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        heroBox.addView(tvExpCountdown);

        tvExpSub = new TextView(getContext());
        tvExpSub.setText(isEnglish ? "  time remaining until Adhan" : "  الوقت المتبقي لرفع الأذان");
        tvExpSub.setTextSize(12.5f);
        tvExpSub.setTextColor(0xFF94A3B8);
        heroBox.addView(tvExpSub);

        expandedLayout.addView(heroBox);

        // 3. Progress Bar (Elapsed time between prayers)
        expProgressBar = new ProgressBar(getContext(), null, android.R.attr.progressBarStyleHorizontal);
        expProgressBar.setMax(100);
        expProgressBar.setProgress(45);
        expProgressBar.setProgressDrawable(createProgressBarDrawable());
        LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4));
        pbLp.topMargin = dp(2);
        pbLp.bottomMargin = dp(8);
        expandedLayout.addView(expProgressBar, pbLp);

        // 4. Prayer Timeline Chips (Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha)
        HorizontalScrollView scrollChips = new HorizontalScrollView(getContext());
        scrollChips.setHorizontalScrollBarEnabled(false);
        scrollChips.setOverScrollMode(OVER_SCROLL_NEVER);

        expChipsContainer = new LinearLayout(getContext());
        expChipsContainer.setOrientation(LinearLayout.HORIZONTAL);
        scrollChips.addView(expChipsContainer);

        expandedLayout.addView(scrollChips);

        // 5. Action Footer
        LinearLayout footerRow = new LinearLayout(getContext());
        footerRow.setOrientation(LinearLayout.HORIZONTAL);
        footerRow.setGravity(Gravity.CENTER_VERTICAL);
        footerRow.setPadding(0, dp(6), 0, 0);

        btnExpSync = new TextView(getContext());
        btnExpSync.setText(isEnglish ? "🔄 Sync Online" : "🔄 تحديث فوري أونلاين");
        btnExpSync.setTextSize(12.5f);
        btnExpSync.setTextColor(0xFF38BDF8);
        btnExpSync.setPadding(dp(10), dp(4), dp(10), dp(4));
        GradientDrawable syncBg = new GradientDrawable();
        syncBg.setColor(0x220284C7);
        syncBg.setCornerRadius(dp(12));
        syncBg.setStroke(dp(1), 0x5538BDF8);
        btnExpSync.setBackground(syncBg);
        btnExpSync.setOnClickListener(v -> {
            if (actionListener != null) actionListener.onSyncRequested();
            collapse();
        });
        footerRow.addView(btnExpSync);

        TextView space = new TextView(getContext());
        LinearLayout.LayoutParams spLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        footerRow.addView(space, spLp);

        TextView btnDetails = new TextView(getContext());
        btnDetails.setText(isEnglish ? "⚙ Settings & Offsets" : "⚙ تعديل ومواقيت أم القرى");
        btnDetails.setTextSize(12.5f);
        btnDetails.setTextColor(0xFFF1F5F9);
        btnDetails.setPadding(dp(10), dp(4), dp(10), dp(4));
        GradientDrawable detBg = new GradientDrawable();
        detBg.setColor(0x22334155);
        detBg.setCornerRadius(dp(12));
        btnDetails.setBackground(detBg);
        btnDetails.setOnClickListener(v -> {
            if (actionListener != null) actionListener.onSettingsRequested();
            collapse();
        });
        footerRow.addView(btnDetails);

        expandedLayout.addView(footerRow);

        addView(expandedLayout, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private GradientDrawable createProgressBarDrawable() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(0xFF10B981);
        d.setCornerRadius(dp(2));
        return d;
    }

    public void updatePrayerTimes(String[] names, String[] times, boolean english) {
        this.prayerNames = names;
        this.prayerTimes = times;
        this.isEnglish = english;
        post(this::refreshData);
    }

    public void tick() {
        refreshData();
    }

    private void refreshData() {
        if (prayerTimes == null || prayerTimes.length < 6) return;

        Calendar now = Calendar.getInstance();
        int currentSec = now.get(Calendar.HOUR_OF_DAY) * 3600 + now.get(Calendar.MINUTE) * 60 + now.get(Calendar.SECOND);

        int nextIdx = -1;
        int nextTargetSec = -1;
        int prevTargetSec = -1;

        for (int i = 0; i < prayerTimes.length; i++) {
            int m = parseMinutes(prayerTimes[i]);
            if (m >= 0) {
                int sec = m * 60;
                if (sec > currentSec) {
                    nextIdx = i;
                    nextTargetSec = sec;
                    prevTargetSec = (i > 0) ? (parseMinutes(prayerTimes[i - 1]) * 60) : (sec - 4 * 3600);
                    break;
                }
            }
        }

        // If after Isha, next is Fajr tomorrow
        if (nextIdx < 0) {
            nextIdx = 0;
            int fajrSec = parseMinutes(prayerTimes[0]) * 60;
            nextTargetSec = fajrSec + 24 * 3600;
            prevTargetSec = parseMinutes(prayerTimes[5]) * 60;
        }

        this.currentNextIdx = nextIdx;
        this.remainingSeconds = nextTargetSec - currentSec;

        int diff = remainingSeconds;
        int dh = diff / 3600;
        int dm = (diff % 3600) / 60;
        int ds = diff % 60;

        String pName = (prayerNames != null && prayerNames.length > nextIdx) ? prayerNames[nextIdx] : "";
        String countdownStr = String.format(Locale.US, "%02d:%02d:%02d", dh, dm, ds);

        // State detection
        int newState = STATE_NORMAL;
        if (diff <= 900 && diff > 0) {
            newState = STATE_APPROACHING; // Within 15 minutes
        } else if (diff <= 0 && diff > -900) {
            newState = STATE_ADHAN; // Adhan time active
        }

        if (newState != currentState) {
            currentState = newState;
            updateStylingForState(currentState);
        }

        // Update Compact UI
        if (currentState == STATE_APPROACHING) {
            tvCompactIcon.setText("⚠️");
            tvCompactTitle.setText(isEnglish ? ("Approaching: " + pName) : ("اقتربت: " + pName));
            tvCompactCountdown.setText(countdownStr);
            tvCompactCountdown.setTextColor(0xFFFBBF24); // Amber
        } else if (currentState == STATE_ADHAN) {
            tvCompactIcon.setText("📢");
            tvCompactTitle.setText(isEnglish ? ("Adhan: " + pName) : ("حان أذان " + pName));
            tvCompactCountdown.setText(isEnglish ? "Now" : "الآن");
            tvCompactCountdown.setTextColor(0xFF34D399); // Green
        } else {
            tvCompactIcon.setText("🕌");
            tvCompactTitle.setText(pName);
            tvCompactCountdown.setText(countdownStr);
            tvCompactCountdown.setTextColor(0xFF34D399);
        }

        // Update Expanded UI
        if (tvExpTitle != null) {
            tvExpTitle.setText(isEnglish ? ("Next Prayer: " + pName) : ("الصلاة القادمة: " + pName));
            tvExpAdhanTime.setText("🕒 " + format12Hour(prayerTimes[nextIdx]));
            tvExpCountdown.setText(countdownStr);
            tvExpSub.setText(isEnglish ? "  time remaining until Adhan" : "  الوقت المتبقي لرفع الأذان");

            // Calculate progress between prayers
            int totalSpan = Math.max(60, nextTargetSec - prevTargetSec);
            int elapsed = Math.max(0, currentSec - prevTargetSec);
            int progress = Math.min(100, Math.max(0, (elapsed * 100) / totalSpan));
            expProgressBar.setProgress(progress);

            rebuildPrayerChips(nextIdx);
        }
    }

    private void updateStylingForState(int state) {
        if (state == STATE_APPROACHING) {
            bgDrawable.setStroke(dp(1.8f), 0xFFF59E0B); // Amber rim
        } else if (state == STATE_ADHAN) {
            bgDrawable.setStroke(dp(2.2f), 0xFF34D399); // Glowing green rim
        } else {
            bgDrawable.setStroke(dp(1.6f), 0xFF10B981); // Emerald rim
        }
    }

    private void rebuildPrayerChips(int activeNextIdx) {
        if (expChipsContainer == null || prayerTimes == null || prayerNames == null) return;
        expChipsContainer.removeAllViews();

        for (int i = 0; i < Math.min(prayerNames.length, prayerTimes.length); i++) {
            boolean isNext = (i == activeNextIdx);
            LinearLayout chip = new LinearLayout(getContext());
            chip.setOrientation(LinearLayout.VERTICAL);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(10), dp(5), dp(10), dp(5));

            GradientDrawable chipBg = new GradientDrawable();
            if (isNext) {
                chipBg.setColor(0x44059669); // Glowing active emerald
                chipBg.setStroke(dp(1.5f), 0xFF34D399);
            } else {
                chipBg.setColor(0x221E293B);
                chipBg.setStroke(dp(0.8f), 0x33475569);
            }
            chipBg.setCornerRadius(dp(10));
            chip.setBackground(chipBg);

            TextView tvName = new TextView(getContext());
            tvName.setText(isNext ? ("★ " + prayerNames[i]) : prayerNames[i]);
            tvName.setTextSize(11.5f);
            tvName.setTextColor(isNext ? 0xFFFFFFFF : 0xFF94A3B8);
            tvName.setTypeface(FontHelper.getArabicTypeface(getContext()), isNext ? Typeface.BOLD : Typeface.NORMAL);
            chip.addView(tvName);

            TextView tvTime = new TextView(getContext());
            tvTime.setText(format12Hour(prayerTimes[i]));
            tvTime.setTextSize(11f);
            tvTime.setTextColor(isNext ? 0xFF34D399 : 0xFFCBD5E1);
            tvTime.setTypeface(Typeface.MONOSPACE, isNext ? Typeface.BOLD : Typeface.NORMAL);
            chip.addView(tvTime);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(6);
            expChipsContainer.addView(chip, lp);
        }
    }

    public void expand() {
        if (isExpanded || isAnimating) return;
        isAnimating = true;
        bringToFront();

        autoCollapseHandler.removeCallbacks(autoCollapseRunnable);
        autoCollapseHandler.postDelayed(autoCollapseRunnable, 14000L); // Auto-collapse after 14s

        int startWidth = getWidth() > 0 ? getWidth() : dp(280);
        int startHeight = getHeight() > 0 ? getHeight() : dp(42);

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int targetWidth = Math.min(screenWidth - dp(32), dp(470));
        int targetHeight = dp(154);

        expandedLayout.setVisibility(View.VISIBLE);
        expandedLayout.setAlpha(0f);

        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(280);
        anim.setInterpolator(new DecelerateInterpolator(1.8f));
        anim.addUpdateListener(animation -> {
            float frac = animation.getAnimatedFraction();
            int currentW = (int) (startWidth + (targetWidth - startWidth) * frac);
            int currentH = (int) (startHeight + (targetHeight - startHeight) * frac);

            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
            if (lp != null) {
                lp.width = currentW;
                lp.height = currentH;
                setLayoutParams(lp);
            }

            bgDrawable.setCornerRadius(dp(20) + (dp(4) * frac));
            compactLayout.setAlpha(1f - frac);
            expandedLayout.setAlpha(frac);
        });

        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                isExpanded = true;
                isAnimating = false;
                compactLayout.setVisibility(View.GONE);
                expandedLayout.setAlpha(1f);
            }
        });
        anim.start();
    }

    public void collapse() {
        if (!isExpanded || isAnimating) return;
        isAnimating = true;
        autoCollapseHandler.removeCallbacks(autoCollapseRunnable);

        int startWidth = getWidth();
        int startHeight = getHeight();
        int targetWidth = dp(280);
        int targetHeight = dp(42);

        compactLayout.setVisibility(View.VISIBLE);
        compactLayout.setAlpha(0f);

        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(240);
        anim.setInterpolator(new DecelerateInterpolator(1.8f));
        anim.addUpdateListener(animation -> {
            float frac = animation.getAnimatedFraction();
            int currentW = (int) (startWidth + (targetWidth - startWidth) * frac);
            int currentH = (int) (startHeight + (targetHeight - startHeight) * frac);

            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
            if (lp != null) {
                lp.width = currentW;
                lp.height = currentH;
                setLayoutParams(lp);
            }

            bgDrawable.setCornerRadius(dp(24) - (dp(4) * frac));
            expandedLayout.setAlpha(1f - frac);
            compactLayout.setAlpha(frac);
        });

        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                isExpanded = false;
                isAnimating = false;
                expandedLayout.setVisibility(View.GONE);
                compactLayout.setAlpha(1f);
            }
        });
        anim.start();
    }

    public boolean isExpanded() {
        return isExpanded;
    }

    private void initFallbackTimes() {
        try {
            Calendar now = Calendar.getInstance();
            PrayerTimesCalculator.Result fallback = PrayerTimesCalculator.calculate(
                    24.7136, 46.6753, now, 0, new int[]{0, 0, 0, 0, 0, 0}, 4, isEnglish);
            String[] defaultNames = isEnglish
                    ? new String[]{"Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"}
                    : new String[]{"الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"};
            this.prayerNames = defaultNames;
            this.prayerTimes = fallback.times24;
            refreshData();
        } catch (Throwable ignored) { }
    }

    private float touchStartX, touchStartY;
    private int initialTouchLeft, initialTouchTop;
    private boolean isDragging = false;

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (isExpanded) return super.onInterceptTouchEvent(ev);
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchStartX = ev.getRawX();
                touchStartY = ev.getRawY();
                initialTouchLeft = (int) getX();
                initialTouchTop = (int) getY();
                isDragging = false;
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = Math.abs(ev.getRawX() - touchStartX);
                float dy = Math.abs(ev.getRawY() - touchStartY);
                if (dx > 12 || dy > 12) {
                    isDragging = true;
                    return true;
                }
                break;
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (isExpanded) return super.onTouchEvent(event);
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchStartX = event.getRawX();
                touchStartY = event.getRawY();
                initialTouchLeft = (int) getX();
                initialTouchTop = (int) getY();
                isDragging = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - touchStartX;
                float dy = event.getRawY() - touchStartY;
                if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                    isDragging = true;
                    FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
                    if (lp != null) {
                        lp.gravity = Gravity.TOP | Gravity.START;
                        int screenW = getResources().getDisplayMetrics().widthPixels;
                        int screenH = getResources().getDisplayMetrics().heightPixels;
                        int targetX = Math.max(0, Math.min(screenW - getWidth(), (int) (initialTouchLeft + dx)));
                        int targetY = Math.max(0, Math.min(screenH - getHeight(), (int) (initialTouchTop + dy)));
                        lp.leftMargin = targetX;
                        lp.topMargin = targetY;
                        setLayoutParams(lp);
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (!isDragging) {
                    if (!isAnimating) {
                        if (!isExpanded) expand();
                        else collapse();
                    }
                }
                isDragging = false;
                return true;
        }
        return super.onTouchEvent(event);
    }

    private int parseMinutes(String s) {
        if (s == null) return -1;
        try {
            String[] parts = s.split(":");
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
        return String.format(Locale.US, "%02d:%02d %s", hour12, minute, ampm);
    }

    private int dp(float val) {
        return Math.round(val * getResources().getDisplayMetrics().density);
    }

    private int dp(int val) {
        return Math.round(val * getResources().getDisplayMetrics().density);
    }
}
