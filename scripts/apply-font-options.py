from pathlib import Path

view_path = Path("app/src/main/java/sa/h9/qibla/QiblaView.java")
view = view_path.read_text()

def replace_once(text, old, new, description):
    if old in text:
        return text.replace(old, new, 1)
    if new in text:
        return text
    raise SystemExit("Expected anchor missing: " + description)

view = replace_once(view,
    'drawCompass(c, w * .427f, h * .53f, Math.min(h * .236f, w * .132f));',
    'drawCompass(c, w * .50f, h * .53f, Math.min(h * .236f, w * .132f));',
    'compass center')
view = replace_once(view,
    'c.drawText("8.1 MANUAL", w * .114f, h * .116f, p);',
    '// Removed legacy build label as requested.',
    'legacy label')
view = replace_once(view,
    'float x = w * .804f, cardW = w * .178f, cardH = h * .116f;',
    'float cardW = w * .178f, cardH = h * .116f;\n        float x = w * (swapPanels ? .018f : .804f);',
    'information panel position')
view = replace_once(view,
    'float x=w*.018f, y=h*.175f, cw=w*.265f, ch=h*.405f;',
    'float x=w*(swapPanels ? .715f : .018f), y=h*.175f, cw=w*.265f, ch=h*.405f;',
    'prayer panel position')
view = replace_once(view,
    'private float fontScale = 1.15f;',
    'private float fontScale = 1.15f;\n    private boolean swapPanels = false;',
    'swap panels state')
view = replace_once(view,
    'public float getFontScale() { return fontScale; }',
    'public float getFontScale() { return fontScale; }\n\n    public void setSwapPanels(boolean swap) { swapPanels = swap; invalidate(); }\n    public boolean getSwapPanels() { return swapPanels; }',
    'swap panel methods')
view = replace_once(view,
    'c.drawCircle(cx, cy, r*1.49f, p);',
    'c.drawCircle(cx, cy, r*1.28f, p);',
    'compass halo size')
# Adjust both ring radii as a single block to avoid ambiguous repeated anchors.
old_rings = '''stroke.setStrokeWidth(r*.012f);
        stroke.setColor(isNightTheme() ? 0x5530D9C4 : 0x55466F7B);
        c.drawCircle(cx, cy, r*1.36f, stroke);
        stroke.setStrokeWidth(r*.006f);
        stroke.setColor(isNightTheme() ? 0x3348A8FF : 0x335D7E86);
        c.drawCircle(cx, cy, r*1.18f, stroke);'''
new_rings = '''stroke.setStrokeWidth(r*.012f);
        stroke.setColor(isNightTheme() ? 0x5530D9C4 : 0x55466F7B);
        c.drawCircle(cx, cy, r*1.18f, stroke);
        stroke.setStrokeWidth(r*.006f);
        stroke.setColor(isNightTheme() ? 0x3348A8FF : 0x335D7E86);
        c.drawCircle(cx, cy, r*1.08f, stroke);'''
view = replace_once(view, old_rings, new_rings, 'compass ring block')
view = replace_once(view,
    '''        p.setColor(isNightTheme() ? 0x1500E4D0 : 0x2200D7C2);
        c.drawCircle(w * .425f, h * .52f, h * .365f, p);
        p.setColor(isNightTheme() ? 0x0D3E8CFF : 0x1837678E);
        c.drawCircle(w * .88f, h * .15f, h * .34f, p);''',
    '''        // Compact, centered ambient glow so it does not overwhelm the compass.
        p.setColor(isNightTheme() ? 0x0D00E4D0 : 0x0A00D7C2);
        c.drawCircle(w * .50f, h * .53f, h * .285f, p);''',
    'balanced background glow')
view_path.write_text(view)

activity_path = Path("app/src/main/java/sa/h9/qibla/MainActivity.java")
activity = activity_path.read_text()
activity = replace_once(activity,
    'private static final String KEY_FONT_FAMILY = "font_family";',
    'private static final String KEY_FONT_FAMILY = "font_family";\n    private static final String KEY_SWAP_PANELS = "swap_panels";',
    'panel preference key')
activity = replace_once(activity,
    'qiblaView.setFontFamily(getSharedPreferences(UI_PREFS, MODE_PRIVATE).getString(KEY_FONT_FAMILY, "sans-serif"));',
    'qiblaView.setFontFamily(getSharedPreferences(UI_PREFS, MODE_PRIVATE).getString(KEY_FONT_FAMILY, "sans-serif"));\n        qiblaView.setSwapPanels(getSharedPreferences(UI_PREFS, MODE_PRIVATE).getBoolean(KEY_SWAP_PANELS, false));',
    'restore panel preference')
swap_button = '''        Button swapPanels = new Button(this);
        boolean panelsSwapped = qiblaView.getSwapPanels();
        swapPanels.setText("مواضع القوائم: " + (panelsSwapped ? "معكوسة" : "الافتراضي"));
        swapPanels.setTextSize(18f);
        swapPanels.setTextColor(Color.WHITE);
        swapPanels.setAllCaps(false);
        GradientDrawable swapBg = new GradientDrawable();
        swapBg.setColor(0xFF165B63);
        swapBg.setCornerRadius(dp(22));
        swapBg.setStroke(dp(1), 0xFF2FE0C7);
        swapPanels.setBackground(swapBg);
        swapPanels.setOnClickListener(v -> {
            boolean next = !qiblaView.getSwapPanels();
            qiblaView.setSwapPanels(next);
            getSharedPreferences(UI_PREFS, MODE_PRIVATE).edit().putBoolean(KEY_SWAP_PANELS, next).apply();
            swapPanels.setText("مواضع القوائم: " + (next ? "معكوسة" : "الافتراضي"));
        });
        LinearLayout.LayoutParams swapLp = new LinearLayout.LayoutParams(dp(300), dp(54));
        swapLp.bottomMargin = dp(12);
        card.addView(swapPanels, swapLp);

        Button guide = new Button(this);'''
if 'Button swapPanels = new Button(this);' not in activity:
    activity = replace_once(activity, '        Button guide = new Button(this);', swap_button, 'settings swap button')
activity = replace_once(activity,
    'float[] sizes = {0.85f, 1.0f, 1.15f, 1.30f, 1.45f};',
    'float[] sizes = {0.85f, 1.0f, 1.15f, 1.35f, 1.55f, 1.75f, 1.90f};',
    'font size options')
activity_path.write_text(activity)
print("Applied compass proportion fixes and persistent left/right panel swap setting.")
