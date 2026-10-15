from pathlib import Path

view_path = Path("app/src/main/java/sa/h9/qibla/QiblaView.java")
view = view_path.read_text()

replacements = [
    ('drawCompass(c, w * .427f, h * .53f, Math.min(h * .236f, w * .132f));',
     'drawCompass(c, w * .50f, h * .53f, Math.min(h * .236f, w * .132f));'),
    ('c.drawText("8.1 MANUAL", w * .114f, h * .116f, p);',
     '// Removed legacy build label as requested.'),
    ('float x = w * .018f, cardW = w * .178f, cardH = h * .116f;',
     'float cardW = w * .178f, cardH = h * .116f;\n        float x = w * (swapPanels ? .018f : .804f);'),
    ('float x=w*.715f, y=h*.175f, cw=w*.265f, ch=h*.405f;',
     'float x=w*(swapPanels ? .715f : .018f), y=h*.175f, cw=w*.265f, ch=h*.405f;'),
    ('fontScale = Math.max(0.85f, Math.min(1.45f, scale));',
     'fontScale = Math.max(0.85f, Math.min(1.90f, scale));'),
    ('private float fontScale = 1.15f;',
     'private float fontScale = 1.15f;\n    private boolean swapPanels = false;'),
    ('public float getFontScale() { return fontScale; }',
     'public float getFontScale() { return fontScale; }\n\n    public void setSwapPanels(boolean swap) { swapPanels = swap; invalidate(); }\n    public boolean getSwapPanels() { return swapPanels; }'),
    ('c.drawCircle(cx, cy, r*1.49f, p);',
     'c.drawCircle(cx, cy, r*1.28f, p);'),
    ('c.drawCircle(cx, cy, r*1.36f, stroke);',
     'c.drawCircle(cx, cy, r*1.18f, stroke);'),
    ('c.drawCircle(cx, cy, r*1.18f, stroke);',
     'c.drawCircle(cx, cy, r*1.08f, stroke);'),
]
for old, new in replacements:
    if old in view:
        view = view.replace(old, new, 1)
    elif new not in view:
        raise SystemExit("Expected QiblaView anchor missing: " + old)

view_path.write_text(view)

activity_path = Path("app/src/main/java/sa/h9/qibla/MainActivity.java")
activity = activity_path.read_text()

old = 'private static final String KEY_FONT_FAMILY = "font_family";'
new = old + '\n    private static final String KEY_SWAP_PANELS = "swap_panels";'
if old in activity:
    activity = activity.replace(old, new, 1)
elif new not in activity:
    raise SystemExit("Expected preference key anchor missing")
old = 'qiblaView.setFontFamily(getSharedPreferences(UI_PREFS, MODE_PRIVATE).getString(KEY_FONT_FAMILY, "sans-serif"));'
new = old + '\n        qiblaView.setSwapPanels(getSharedPreferences(UI_PREFS, MODE_PRIVATE).getBoolean(KEY_SWAP_PANELS, false));'
if old in activity:
    activity = activity.replace(old, new, 1)
elif new not in activity:
    raise SystemExit("Expected view initialization anchor missing")

old = '        Button guide = new Button(this);'
insert = '''        Button swapPanels = new Button(this);
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
if old in activity:
    activity = activity.replace(old, insert, 1)
elif 'Button swapPanels = new Button(this);' not in activity:
    raise SystemExit("Expected settings insertion anchor missing")

old = 'float[] sizes = {0.85f, 1.0f, 1.15f, 1.30f, 1.45f};'
new = 'float[] sizes = {0.85f, 1.0f, 1.15f, 1.35f, 1.55f, 1.75f, 1.90f};'
if old in activity:
    activity = activity.replace(old, new, 1)
elif new not in activity:
    raise SystemExit("Expected font-size list missing")

activity_path.write_text(activity)
print("Applied responsive compass rings, left/right panel toggle, and persisted panel preference.")
