package sa.h9.qibla;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Luxury Automotive Quick App Launcher Overlay:
 * - Works seamlessly both inside MainActivity (via showModalCardOverlay, split-screen safe)
 *   and directly over any foreground car app from FloatingSpeedBubbleService (WindowManager overlay).
 * - Uses standard PackageManager MAIN/LAUNCHER queries (Play-policy compliant, no QUERY_ALL_PACKAGES).
 * - Supports Pinning/Unpinning favorite driving apps (long-press or star icon) and Drive/Media filtering.
 */
public final class QuickAppsOverlay {

    public static final String TAG_QUICK_APPS_OVERLAY = "h9_overlay_quick_apps";
    private static final String PREF_PINNED_APPS = "quick_apps_pinned";
    private static final String PREF_PINNED_ORDER = "quick_apps_pinned_order";

    private static final int FILTER_ALL = 0;
    private static final int FILTER_PINNED = 1;
    private static final int FILTER_DRIVE = 2;

    private static View activeFloatingScrim;
    private static WindowManager activeFloatingWm;

    private QuickAppsOverlay() { }

    private static class AppEntry {
        String packageName;
        String label;
        Drawable icon;
        boolean pinned;
        int pinnedOrderIndex = Integer.MAX_VALUE;
        boolean driveRecommended;
        int priorityRank;
    }

    public static void show(final MainActivity activity) {
        if (activity == null || activity.isFinishing()) return;
        final boolean en = activity.isEnglishMode();
        int paneW = activity.getVisiblePaneWidth();
        int paneH = activity.getVisiblePaneHeight();
        int cardW = Math.min(dp(activity, 940), Math.max(dp(activity, 310), (int) (paneW * 0.95f)));
        int cardH = Math.min(dp(activity, 640), Math.max(dp(activity, 350), (int) (paneH * 0.92f)));

        final Runnable dismissAction = () -> activity.dismissOverlayByTag(TAG_QUICK_APPS_OVERLAY);
        View card = buildCard(activity, cardW, en, dismissAction);
        activity.showModalCardOverlay(TAG_QUICK_APPS_OVERLAY, card, cardW, cardH, true, null);
    }

    public static void showFloatingOverlay(final Context serviceContext,
                                           final WindowManager wm,
                                           final Runnable onClosed) {
        if (serviceContext == null || wm == null) return;
        dismissFloatingOverlay();

        SharedPreferences sp = serviceContext.getSharedPreferences(MainActivity.UI_PREFS, Context.MODE_PRIVATE);
        final boolean en = sp.getBoolean(MainActivity.KEY_ENGLISH_MODE, false);

        int screenW = serviceContext.getResources().getDisplayMetrics().widthPixels;
        int screenH = serviceContext.getResources().getDisplayMetrics().heightPixels;
        int cardW = Math.min(dp(serviceContext, 900), Math.max(dp(serviceContext, 310), (int) (screenW * 0.93f)));
        int cardH = Math.min(dp(serviceContext, 600), Math.max(dp(serviceContext, 340), (int) (screenH * 0.88f)));

        final FrameLayout scrim = new FrameLayout(serviceContext) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                if (event != null && event.getKeyCode() == KeyEvent.KEYCODE_BACK
                        && event.getAction() == KeyEvent.ACTION_UP) {
                    dismissFloatingOverlay();
                    if (onClosed != null) onClosed.run();
                    return true;
                }
                return super.dispatchKeyEvent(event);
            }
        };
        scrim.setBackgroundColor(0xB8000000);
        scrim.setLayoutDirection(en ? View.LAYOUT_DIRECTION_LTR : View.LAYOUT_DIRECTION_RTL);
        scrim.setFocusableInTouchMode(true);
        scrim.setOnClickListener(v -> {
            dismissFloatingOverlay();
            if (onClosed != null) onClosed.run();
        });

        final Runnable dismissAction = () -> {
            dismissFloatingOverlay();
            if (onClosed != null) onClosed.run();
        };

        View card = buildCard(serviceContext, cardW, en, dismissAction);
        card.setClickable(true);
        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(cardW, cardH, Gravity.CENTER);
        scrim.addView(card, cardLp);

        int windowType = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                windowType,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        lp.gravity = Gravity.CENTER;

        try {
            wm.addView(scrim, lp);
            activeFloatingScrim = scrim;
            activeFloatingWm = wm;
            scrim.requestFocus();
        } catch (Throwable ignored) {
            activeFloatingScrim = null;
            activeFloatingWm = null;
        }
    }

    public static void dismissFloatingOverlay() {
        if (activeFloatingWm != null && activeFloatingScrim != null) {
            try {
                activeFloatingWm.removeView(activeFloatingScrim);
            } catch (Throwable ignored) { }
        }
        activeFloatingScrim = null;
        activeFloatingWm = null;
    }

    private static View buildCard(final Context ctx,
                                  final int cardW,
                                  final boolean en,
                                  final Runnable dismissAction) {
        final FullScreenWeatherDialog.ThemePalette pal = FullScreenWeatherDialog.getThemePalette(ctx);
        final Typeface tf = FontHelper.getArabicTypeface(ctx);
        final int accentGold = pal.goldHighlight != 0 ? pal.goldHighlight : 0xFFFFD768;

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setLayoutDirection(en ? View.LAYOUT_DIRECTION_LTR : View.LAYOUT_DIRECTION_RTL);
        card.setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 12));

        GradientDrawable cardBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                pal.isLight
                        ? new int[]{0xFAFCFEFF, 0xF5E7F0F5}
                        : new int[]{(pal.topBarBg & 0x00FFFFFF) | 0xF7000000, (pal.rootBg & 0x00FFFFFF) | 0xFA000000});
        cardBg.setCornerRadius(dp(ctx, 22));
        cardBg.setStroke(dp(ctx, 1.8f), accentGold);
        card.setBackground(cardBg);

        // 1. Header Bar
        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvTitle = new TextView(ctx);
        tvTitle.setText(en ? "🚀 Quick App Launcher" : "🚀 الوصول السريع للتطبيقات");
        tvTitle.setTextSize(19f);
        tvTitle.setTypeface(tf, Typeface.BOLD);
        tvTitle.setTextColor(accentGold);
        tvTitle.setSingleLine(true);
        tvTitle.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        header.addView(tvTitle, titleLp);

        final TextView tvCountBadge = new TextView(ctx);
        tvCountBadge.setText("...");
        tvCountBadge.setTextSize(11.5f);
        tvCountBadge.setTypeface(tf, Typeface.BOLD);
        tvCountBadge.setTextColor(pal.accentColor);
        tvCountBadge.setPadding(dp(ctx, 10), dp(ctx, 4), dp(ctx, 10), dp(ctx, 4));
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(pal.isLight ? 0xFFE2EEF4 : 0x55000000);
        badgeBg.setCornerRadius(dp(ctx, 12));
        badgeBg.setStroke(dp(ctx, 1f), pal.cardStroke);
        tvCountBadge.setBackground(badgeBg);
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeLp.setMarginEnd(dp(ctx, 8));
        header.addView(tvCountBadge, badgeLp);

        TextView btnClose = new TextView(ctx);
        btnClose.setText(en ? "✕ Close" : "✕ إغلاق");
        btnClose.setTextSize(13f);
        btnClose.setTypeface(tf, Typeface.BOLD);
        btnClose.setTextColor(pal.isLight ? 0xFF7F1D1D : 0xFFFEE2E2);
        btnClose.setGravity(Gravity.CENTER);
        btnClose.setMinHeight(dp(ctx, 40));
        btnClose.setPadding(dp(ctx, 14), dp(ctx, 6), dp(ctx, 14), dp(ctx, 6));
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setColor(pal.isLight ? 0xFFFEE2E2 : 0xAA7F1D1D);
        closeBg.setCornerRadius(dp(ctx, 18));
        closeBg.setStroke(dp(ctx, 1.2f), 0xFFF87171);
        btnClose.setBackground(closeBg);
        btnClose.setOnClickListener(v -> {
            if (dismissAction != null) dismissAction.run();
        });
        header.addView(btnClose);

        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headerLp.bottomMargin = dp(ctx, 8);
        card.addView(header, headerLp);

        // 2. Quick Filter Tabs + Hint
        LinearLayout filterRow = new LinearLayout(ctx);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);
        filterRow.setGravity(Gravity.CENTER_VERTICAL);

        final TextView[] filterBtns = new TextView[3];
        final String[] filterLabels = en
                ? new String[]{"📱 All Apps", "⭐ Favorites", "🗺️ Drive & Media"}
                : new String[]{"📱 كل التطبيقات", "⭐ المفضلة", "🗺️ الملاحة والصوت"};
        final int[] activeFilter = new int[]{FILTER_ALL};

        for (int i = 0; i < 3; i++) {
            final int fIdx = i;
            TextView tab = new TextView(ctx);
            tab.setText(filterLabels[i]);
            tab.setTextSize(13f);
            tab.setTypeface(tf, Typeface.BOLD);
            tab.setGravity(Gravity.CENTER);
            tab.setSingleLine(true);
            tab.setEllipsize(TextUtils.TruncateAt.END);
            tab.setMinHeight(dp(ctx, 44));
            tab.setPadding(dp(ctx, 8), dp(ctx, 6), dp(ctx, 8), dp(ctx, 6));
            LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i < 2) tLp.setMarginEnd(dp(ctx, 6));
            filterRow.addView(tab, tLp);
            filterBtns[i] = tab;
        }

        LinearLayout.LayoutParams filterLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        filterLp.bottomMargin = dp(ctx, 8);
        card.addView(filterRow, filterLp);

        // 3. Content Area (Loading indicator + Scrollable App Grid)
        final FrameLayout contentContainer = new FrameLayout(ctx);
        LinearLayout.LayoutParams ccLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        card.addView(contentContainer, ccLp);

        final ProgressBar spinner = new ProgressBar(ctx);
        FrameLayout.LayoutParams spLp = new FrameLayout.LayoutParams(dp(ctx, 42), dp(ctx, 42), Gravity.CENTER);
        contentContainer.addView(spinner, spLp);

        ScrollView scrollView = new ScrollView(ctx);
        scrollView.setVerticalScrollBarEnabled(true);
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        final LinearLayout gridHolder = new LinearLayout(ctx);
        gridHolder.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(gridHolder, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        contentContainer.addView(scrollView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 4. Footer Tip
        TextView tvHint = new TextView(ctx);
        tvHint.setText(en
                ? "💡 Tip: Tap ☆ or long-press to pin Favorites (Top 3 appear in Floating Dock • use ▲▼ to reorder)"
                : "💡 تلميح: اضغط ☆ أو مطولاً لتثبيت المفضلة (أول 3 تطبيقات تظهر بالكرة العائمة • استخدم ▲▼ للترتيب)");
        tvHint.setTextSize(11f);
        tvHint.setTypeface(tf);
        tvHint.setTextColor(pal.textSecondary);
        tvHint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintLp.topMargin = dp(ctx, 6);
        card.addView(tvHint, hintLp);

        final List<AppEntry> allApps = new ArrayList<>();
        final int colCount = (cardW >= dp(ctx, 760)) ? 5 : ((cardW >= dp(ctx, 560)) ? 4 : ((cardW >= dp(ctx, 390)) ? 3 : 2));

        final Runnable[] renderRef = new Runnable[1];
        final Runnable updateTabs = () -> {
            for (int i = 0; i < 3; i++) {
                boolean active = (activeFilter[0] == i);
                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(dp(ctx, 14));
                if (active) {
                    bg.setColors(pal.isLight
                            ? new int[]{0xFF00758A, 0xFF005568}
                            : new int[]{pal.activeTabBg, (pal.cardBg & 0x00FFFFFF) | 0xF0000000});
                    bg.setStroke(dp(ctx, 1.5f), accentGold);
                    filterBtns[i].setTextColor(0xFFFFFFFF);
                } else {
                    bg.setColor(pal.isLight ? 0xFFE5EFF4 : (pal.inactiveTabBg & 0x00FFFFFF) | 0xCC000000);
                    bg.setStroke(dp(ctx, 1f), pal.cardStroke);
                    filterBtns[i].setTextColor(pal.textSecondary);
                }
                filterBtns[i].setBackground(bg);
            }
        };

        renderRef[0] = () -> {
            updateTabs.run();
            gridHolder.removeAllViews();

            List<AppEntry> filtered = new ArrayList<>();
            for (AppEntry entry : allApps) {
                if (activeFilter[0] == FILTER_PINNED && !entry.pinned) continue;
                if (activeFilter[0] == FILTER_DRIVE && !entry.driveRecommended && !entry.pinned) continue;
                filtered.add(entry);
            }

            tvCountBadge.setText(en
                    ? (filtered.size() + " apps")
                    : (filtered.size() + " تطبيق"));

            if (filtered.isEmpty()) {
                TextView empty = new TextView(ctx);
                empty.setText(activeFilter[0] == FILTER_PINNED
                        ? (en ? "No favorite apps pinned yet.\nLong-press any app in All Apps to pin it here."
                              : "لا توجد تطبيقات مثبتة في المفضلة بعد.\nاضغط مطولاً على أي تطبيق لتثبيته هنا.")
                        : (en ? "No apps found in this category." : "لا توجد تطبيقات في هذا التصنيف."));
                empty.setTextSize(14f);
                empty.setTypeface(tf, Typeface.BOLD);
                empty.setTextColor(pal.textSecondary);
                empty.setGravity(Gravity.CENTER);
                empty.setPadding(dp(ctx, 16), dp(ctx, 42), dp(ctx, 16), dp(ctx, 42));
                gridHolder.addView(empty);
                return;
            }

            for (int i = 0; i < filtered.size(); i += colCount) {
                LinearLayout row = new LinearLayout(ctx);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setLayoutDirection(en ? View.LAYOUT_DIRECTION_LTR : View.LAYOUT_DIRECTION_RTL);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rowLp.bottomMargin = dp(ctx, 8);

                for (int c = 0; c < colCount; c++) {
                    int idx = i + c;
                    if (idx < filtered.size()) {
                        View tile = buildAppTile(ctx, filtered.get(idx), pal, tf, accentGold, en,
                                dismissAction, () -> {
                                    refreshPinnedOrderIndices(ctx, allApps);
                                    sortApps(allApps);
                                    renderRef[0].run();
                                });
                        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(
                                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                        if (c < colCount - 1) tLp.setMarginEnd(dp(ctx, 8));
                        row.addView(tile, tLp);
                    } else {
                        View spacer = new View(ctx);
                        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(0, dp(ctx, 1), 1f);
                        if (c < colCount - 1) sLp.setMarginEnd(dp(ctx, 8));
                        row.addView(spacer, sLp);
                    }
                }
                gridHolder.addView(row, rowLp);
            }
        };

        for (int i = 0; i < 3; i++) {
            final int fIdx = i;
            filterBtns[i].setOnClickListener(v -> {
                activeFilter[0] = fIdx;
                renderRef[0].run();
            });
        }
        updateTabs.run();

        // Load launchable apps asynchronously
        final Handler mainHandler = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            List<AppEntry> loaded = loadInstalledApps(ctx);
            mainHandler.post(() -> {
                spinner.setVisibility(View.GONE);
                allApps.clear();
                allApps.addAll(loaded);
                renderRef[0].run();
            });
        }, "H9-QuickApps-Loader").start();

        return card;
    }

    private static View buildAppTile(final Context ctx,
                                     final AppEntry entry,
                                     final FullScreenWeatherDialog.ThemePalette pal,
                                     final Typeface tf,
                                     final int accentGold,
                                     final boolean en,
                                     final Runnable dismissAction,
                                     final Runnable onPinChanged) {
        FrameLayout wrapper = new FrameLayout(ctx);
        wrapper.setMinimumHeight(dp(ctx, 102));

        GradientDrawable tileBg = new GradientDrawable();
        tileBg.setCornerRadius(dp(ctx, 16));
        if (entry.pinned) {
            tileBg.setColors(pal.isLight
                    ? new int[]{0xFFFFFFFF, 0xFFEBF6F9}
                    : new int[]{(pal.activeTabBg & 0x00FFFFFF) | 0xDD000000, (pal.cardBg & 0x00FFFFFF) | 0xE8000000});
            tileBg.setStroke(dp(ctx, 1.5f), accentGold);
        } else {
            tileBg.setColor(pal.isLight ? 0xF2FFFFFF : (pal.cardBg & 0x00FFFFFF) | 0xDD000000);
            tileBg.setStroke(dp(ctx, 1.1f), pal.cardStroke);
        }
        wrapper.setBackground(tileBg);

        LinearLayout body = new LinearLayout(ctx);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER);
        body.setPadding(dp(ctx, 10), dp(ctx, 14), dp(ctx, 10), dp(ctx, 12));

        ImageView iconView = new ImageView(ctx);
        iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (entry.icon != null) {
            iconView.setImageDrawable(entry.icon);
        }
        iconView.setContentDescription(entry.label);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(ctx, 48), dp(ctx, 48));
        iconLp.bottomMargin = dp(ctx, 7);
        body.addView(iconView, iconLp);

        TextView labelView = new TextView(ctx);
        labelView.setText(entry.label);
        labelView.setTextSize(13f);
        labelView.setTypeface(tf, Typeface.BOLD);
        labelView.setTextColor(pal.textPrimary);
        labelView.setGravity(Gravity.CENTER);
        labelView.setMaxLines(2);
        labelView.setEllipsize(TextUtils.TruncateAt.END);
        body.addView(labelView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (entry.pinned) {
            LinearLayout orderBar = new LinearLayout(ctx);
            orderBar.setOrientation(LinearLayout.HORIZONTAL);
            orderBar.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            orderBar.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams obLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            obLp.topMargin = dp(ctx, 6);

            TextView upBtn = new TextView(ctx);
            upBtn.setText("▲");
            upBtn.setTextSize(11.5f);
            upBtn.setTextColor(accentGold);
            upBtn.setTypeface(tf, Typeface.BOLD);
            upBtn.setGravity(Gravity.CENTER);
            upBtn.setMinWidth(dp(ctx, 32));
            upBtn.setMinHeight(dp(ctx, 28));
            GradientDrawable moveBtnBg = new GradientDrawable();
            moveBtnBg.setColor(pal.isLight ? 0xFFE2EEF4 : 0x55000000);
            moveBtnBg.setCornerRadius(dp(ctx, 8));
            moveBtnBg.setStroke(dp(ctx, 0.8f), pal.cardStroke);
            upBtn.setBackground(moveBtnBg);
            upBtn.setOnClickListener(v -> {
                movePinnedPackage(ctx, entry.packageName, -1);
                if (onPinChanged != null) onPinChanged.run();
            });

            TextView rankTv = new TextView(ctx);
            int rankNum = entry.pinnedOrderIndex < Integer.MAX_VALUE ? (entry.pinnedOrderIndex + 1) : 1;
            rankTv.setText("#" + rankNum);
            rankTv.setTextSize(10.5f);
            rankTv.setTextColor(pal.accentColor);
            rankTv.setTypeface(tf, Typeface.BOLD);
            rankTv.setGravity(Gravity.CENTER);
            rankTv.setPadding(dp(ctx, 6), 0, dp(ctx, 6), 0);

            TextView downBtn = new TextView(ctx);
            downBtn.setText("▼");
            downBtn.setTextSize(11.5f);
            downBtn.setTextColor(accentGold);
            downBtn.setTypeface(tf, Typeface.BOLD);
            downBtn.setGravity(Gravity.CENTER);
            downBtn.setMinWidth(dp(ctx, 32));
            downBtn.setMinHeight(dp(ctx, 28));
            GradientDrawable moveBtnBg2 = new GradientDrawable();
            moveBtnBg2.setColor(pal.isLight ? 0xFFE2EEF4 : 0x55000000);
            moveBtnBg2.setCornerRadius(dp(ctx, 8));
            moveBtnBg2.setStroke(dp(ctx, 0.8f), pal.cardStroke);
            downBtn.setBackground(moveBtnBg2);
            downBtn.setOnClickListener(v -> {
                movePinnedPackage(ctx, entry.packageName, +1);
                if (onPinChanged != null) onPinChanged.run();
            });

            orderBar.addView(upBtn);
            orderBar.addView(rankTv);
            orderBar.addView(downBtn);
            body.addView(orderBar, obLp);
        }

        wrapper.addView(body, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        // Corner Favorite Star Toggle (36dp touch target)
        TextView starBtn = new TextView(ctx);
        starBtn.setText(entry.pinned ? "★" : "☆");
        starBtn.setTextSize(15f);
        starBtn.setTextColor(entry.pinned ? accentGold : pal.textSecondary);
        starBtn.setGravity(Gravity.CENTER);
        starBtn.setMinimumWidth(dp(ctx, 36));
        starBtn.setMinimumHeight(dp(ctx, 36));
        FrameLayout.LayoutParams starLp = new FrameLayout.LayoutParams(
                dp(ctx, 36), dp(ctx, 36), Gravity.TOP | Gravity.END);
        wrapper.addView(starBtn, starLp);

        View.OnClickListener pinToggle = v -> {
            boolean nowPinned = togglePinnedPackage(ctx, entry.packageName);
            entry.pinned = nowPinned;
            FullScreenSettingsDialog.showCompactToast(ctx, nowPinned
                    ? (en ? ("⭐ Pinned " + entry.label + " to Dock & Favorites") : ("⭐ تم تثبيت " + entry.label + " في المفضلة والكرة"))
                    : (en ? ("Removed " + entry.label + " from Favorites") : ("تمت إزالة " + entry.label + " من المفضلة")));
            if (onPinChanged != null) onPinChanged.run();
        };

        starBtn.setOnClickListener(pinToggle);
        wrapper.setOnLongClickListener(v -> {
            pinToggle.onClick(v);
            return true;
        });

        wrapper.setOnClickListener(v -> launchApp(ctx, entry, en, dismissAction));
        return wrapper;
    }

    private static void launchApp(Context ctx, AppEntry entry, boolean en, Runnable dismissAction) {
        try {
            PackageManager pm = ctx.getPackageManager();
            Intent launch = pm.getLaunchIntentForPackage(entry.packageName);
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                ctx.startActivity(launch);
                if (dismissAction != null) dismissAction.run();
                return;
            }
        } catch (Throwable ignored) { }

        FullScreenSettingsDialog.showCompactToast(ctx, en
                ? ("Unable to open " + entry.label)
                : ("تعذر فتح " + entry.label));
    }

    private static List<AppEntry> loadInstalledApps(Context ctx) {
        List<AppEntry> result = new ArrayList<>();
        try {
            PackageManager pm = ctx.getPackageManager();
            String selfPkg = ctx.getPackageName();
            Set<String> pinnedSet = getPinnedPackages(ctx);

            Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
            mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> resolved = pm.queryIntentActivities(mainIntent, 0);
            Set<String> seenPackages = new HashSet<>();

            for (ResolveInfo ri : resolved) {
                if (ri == null || ri.activityInfo == null) continue;
                String pkg = ri.activityInfo.packageName;
                if (pkg == null || pkg.equals(selfPkg) || seenPackages.contains(pkg)) continue;
                seenPackages.add(pkg);

                CharSequence cs = ri.loadLabel(pm);
                String label = (cs != null && cs.length() > 0) ? cs.toString().trim() : pkg;
                Drawable icon = null;
                try { icon = ri.loadIcon(pm); } catch (Throwable ignored) { }

                AppEntry entry = new AppEntry();
                entry.packageName = pkg;
                entry.label = label;
                entry.icon = icon;
                entry.pinned = pinnedSet.contains(pkg);
                entry.priorityRank = computeAutomotivePriority(pkg, label);
                entry.driveRecommended = entry.priorityRank > 0;
                result.add(entry);
            }

            refreshPinnedOrderIndices(ctx, result);
            sortApps(result);
        } catch (Throwable ignored) { }
        return result;
    }

    private static void refreshPinnedOrderIndices(Context ctx, List<AppEntry> list) {
        List<String> ordered = getOrderedPinnedPackages(ctx);
        for (AppEntry e : list) {
            int idx = ordered.indexOf(e.packageName);
            e.pinned = (idx >= 0);
            e.pinnedOrderIndex = (idx >= 0) ? idx : Integer.MAX_VALUE;
        }
    }

    private static void sortApps(List<AppEntry> list) {
        final Collator collator = Collator.getInstance();
        Collections.sort(list, (a, b) -> {
            if (a.pinned != b.pinned) return a.pinned ? -1 : 1;
            if (a.pinned && b.pinned && a.pinnedOrderIndex != b.pinnedOrderIndex) {
                return Integer.compare(a.pinnedOrderIndex, b.pinnedOrderIndex);
            }
            if (a.priorityRank != b.priorityRank) return Integer.compare(b.priorityRank, a.priorityRank);
            return collator.compare(a.label, b.label);
        });
    }

    private static int computeAutomotivePriority(String pkg, String label) {
        String p = (pkg == null ? "" : pkg).toLowerCase(Locale.US);
        String l = (label == null ? "" : label).toLowerCase(Locale.US);

        if (p.contains("maps") || p.contains("waze") || p.contains("kharita")
                || p.contains("navi") || p.contains("here") || p.contains("sygic")
                || l.contains("خرائط") || l.contains("ملاحة")) {
            return 100;
        }
        if (p.contains("youtube") || p.contains("spotify") || p.contains("anghami")
                || p.contains("music") || p.contains("soundcloud") || p.contains("radio")
                || p.contains("tunein") || p.contains("vlc") || p.contains("audio")) {
            return 85;
        }
        if (p.contains("quran") || p.contains("ayah") || p.contains("athkar") || p.contains("muslim")
                || l.contains("قرآن") || l.contains("القرآن") || l.contains("آية") || l.contains("أذكار")) {
            return 80;
        }
        if (p.contains("dialer") || p.contains("telecom") || p.contains("phone")
                || p.contains("contacts") || p.contains("bluetooth") || p.contains("settings")
                || p.contains("chrome") || p.contains("browser")) {
            return 60;
        }
        return 0;
    }

    public static List<String> getOrderedPinnedPackages(Context ctx) {
        List<String> ordered = new ArrayList<>();
        if (ctx == null) return ordered;
        try {
            SharedPreferences sp = ctx.getSharedPreferences(MainActivity.UI_PREFS, Context.MODE_PRIVATE);
            String csv = sp.getString(PREF_PINNED_ORDER, "");
            Set<String> set = getPinnedPackages(ctx);
            if (csv != null && !csv.trim().isEmpty()) {
                for (String raw : csv.split(",")) {
                    String pkg = raw.trim();
                    if (!pkg.isEmpty() && (set.isEmpty() || set.contains(pkg)) && !ordered.contains(pkg)) {
                        ordered.add(pkg);
                    }
                }
            }
            // Migrate any unordered legacy pinned items seamlessly
            if (!set.isEmpty()) {
                List<String> remaining = new ArrayList<>();
                for (String s : set) {
                    if (!ordered.contains(s)) remaining.add(s);
                }
                Collections.sort(remaining);
                ordered.addAll(remaining);
            }
        } catch (Throwable ignored) { }
        return ordered;
    }

    private static void saveOrderedPinnedPackages(Context ctx, List<String> ordered) {
        if (ctx == null) return;
        Set<String> set = new HashSet<>(ordered);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ordered.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(ordered.get(i));
        }
        ctx.getSharedPreferences(MainActivity.UI_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putStringSet(PREF_PINNED_APPS, set)
                .putString(PREF_PINNED_ORDER, sb.toString())
                .apply();
        try {
            FloatingSpeedBubbleService.reloadPreferences(ctx);
        } catch (Throwable ignored) { }
    }

    private static void movePinnedPackage(Context ctx, String pkg, int delta) {
        List<String> ordered = getOrderedPinnedPackages(ctx);
        int idx = ordered.indexOf(pkg);
        if (idx < 0) return;
        int target = idx + delta;
        if (target < 0 || target >= ordered.size()) return;
        Collections.swap(ordered, idx, target);
        saveOrderedPinnedPackages(ctx, ordered);
    }

    private static Set<String> getPinnedPackages(Context ctx) {
        try {
            SharedPreferences sp = ctx.getSharedPreferences(MainActivity.UI_PREFS, Context.MODE_PRIVATE);
            Set<String> saved = sp.getStringSet(PREF_PINNED_APPS, null);
            return (saved != null) ? new HashSet<>(saved) : new HashSet<>();
        } catch (Throwable ignored) {
            return new HashSet<>();
        }
    }

    private static boolean togglePinnedPackage(Context ctx, String pkg) {
        List<String> ordered = getOrderedPinnedPackages(ctx);
        boolean added;
        if (ordered.contains(pkg)) {
            ordered.remove(pkg);
            added = false;
        } else {
            ordered.add(pkg);
            added = true;
        }
        saveOrderedPinnedPackages(ctx, ordered);
        return added;
    }

    private static int dp(Context ctx, float v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }
}
