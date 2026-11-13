package sa.h9.qibla;

import android.content.Context;
import android.graphics.Typeface;
import androidx.core.content.res.ResourcesCompat;

/**
 * Centralized Font Helper for Automotive & Android Displays:
 * Solves the critical issue on Chinese automotive head units (e.g. Haval H9, Tank 300/500/700)
 * where the system font (Typeface.DEFAULT) lacks Arabic glyphs, causing all Arabic text in
 * Dialogs, TextViews, and Spinners to render as invisible, empty boxes or zero-width characters.
 *
 * Guarantees that bundled high-quality Arabic fonts (Cairo, Tajawal, etc.) are always loaded
 * and applied to all UI elements.
 */
public final class FontHelper {
    private static Typeface cachedCairo;
    private static Typeface cachedTajawal;

    private FontHelper() { }

    /**
     * Returns the primary bold Arabic typeface (Cairo) for headers, labels, and buttons.
     */
    public static Typeface getArabicTypeface(Context ctx) {
        if (cachedCairo != null) return cachedCairo;
        if (ctx == null) return Typeface.DEFAULT;
        try {
            cachedCairo = ResourcesCompat.getFont(ctx.getApplicationContext(), R.font.cairo);
            if (cachedCairo != null) return cachedCairo;
        } catch (Throwable ignored) { }
        try {
            cachedTajawal = ResourcesCompat.getFont(ctx.getApplicationContext(), R.font.tajawal);
            if (cachedTajawal != null) return cachedTajawal;
        } catch (Throwable ignored) { }
        return Typeface.SANS_SERIF;
    }

    /**
     * Returns the user's currently selected Arabic font, falling back to Cairo.
     */
    public static Typeface getSelectedTypeface(Context ctx) {
        if (ctx == null) return getArabicTypeface(ctx);
        try {
            int fontIdx = ctx.getSharedPreferences(MainActivity.UI_PREFS, Context.MODE_PRIVATE)
                    .getInt(MainActivity.KEY_FONT_INDEX, 1);
            switch (fontIdx) {
                case 2:
                    Typeface t2 = ResourcesCompat.getFont(ctx, R.font.tajawal);
                    if (t2 != null) return t2;
                    break;
                case 3:
                    Typeface t3 = ResourcesCompat.getFont(ctx, R.font.almarai);
                    if (t3 != null) return t3;
                    break;
                case 4:
                    Typeface t4 = ResourcesCompat.getFont(ctx, R.font.readex_pro);
                    if (t4 != null) return t4;
                    break;
                case 5:
                    Typeface t5 = ResourcesCompat.getFont(ctx, R.font.amiri);
                    if (t5 != null) return t5;
                    break;
                case 1:
                default:
                    Typeface t1 = ResourcesCompat.getFont(ctx, R.font.cairo);
                    if (t1 != null) return t1;
                    break;
            }
        } catch (Throwable ignored) { }
        return getArabicTypeface(ctx);
    }
}
