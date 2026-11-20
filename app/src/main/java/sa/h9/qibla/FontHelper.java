package sa.h9.qibla;

import android.content.Context;
import android.graphics.Typeface;
import androidx.core.content.res.ResourcesCompat;

/**
 * Centralized Font Helper for Automotive & Android Displays.
 * Uses the vehicle's native system font (Typeface.DEFAULT) by default,
 * while allowing users to switch to any bundled Arabic typeface from Settings.
 */
public final class FontHelper {
    private static Typeface cachedCairo;
    private static Typeface cachedTajawal;
    private static Typeface cachedAlmarai;
    private static Typeface cachedReadexPro;
    private static Typeface cachedAmiri;

    private FontHelper() { }

    /**
     * Returns the user's selected typeface, or the vehicle's native system font by default.
     */
    public static Typeface getArabicTypeface(Context ctx) {
        return getSelectedTypeface(ctx);
    }

    /**
     * Returns the user's selected font (0 = car system default, 1..5 = bundled Arabic fonts).
     */
    public static Typeface getSelectedTypeface(Context ctx) {
        if (ctx == null) return Typeface.DEFAULT;
        Context appCtx = ctx.getApplicationContext() != null ? ctx.getApplicationContext() : ctx;
        try {
            int fontIdx = appCtx.getSharedPreferences(MainActivity.UI_PREFS, Context.MODE_PRIVATE)
                    .getInt(MainActivity.KEY_FONT_INDEX, QiblaView.FONT_DEFAULT);
            switch (fontIdx) {
                case QiblaView.FONT_CAIRO:
                    if (cachedCairo == null) cachedCairo = ResourcesCompat.getFont(appCtx, R.font.cairo);
                    if (cachedCairo != null) return cachedCairo;
                    break;
                case QiblaView.FONT_TAJAWAL:
                    if (cachedTajawal == null) cachedTajawal = ResourcesCompat.getFont(appCtx, R.font.tajawal);
                    if (cachedTajawal != null) return cachedTajawal;
                    break;
                case QiblaView.FONT_ALMARAI:
                    if (cachedAlmarai == null) cachedAlmarai = ResourcesCompat.getFont(appCtx, R.font.almarai);
                    if (cachedAlmarai != null) return cachedAlmarai;
                    break;
                case QiblaView.FONT_READEX:
                    if (cachedReadexPro == null) cachedReadexPro = ResourcesCompat.getFont(appCtx, R.font.readex_pro);
                    if (cachedReadexPro != null) return cachedReadexPro;
                    break;
                case QiblaView.FONT_AMIRI:
                    if (cachedAmiri == null) cachedAmiri = ResourcesCompat.getFont(appCtx, R.font.amiri);
                    if (cachedAmiri != null) return cachedAmiri;
                    break;
                case QiblaView.FONT_DEFAULT:
                default:
                    return Typeface.DEFAULT;
            }
        } catch (Throwable ignored) { }
        return Typeface.DEFAULT;
    }
}
