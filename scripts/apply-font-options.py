from pathlib import Path


def replace_once(text, old, new, description):
    if new in text:
        return text
    if old in text:
        return text.replace(old, new, 1)
    raise AssertionError(description)


view_path = Path("app/src/main/java/sa/h9/qibla/QiblaView.java")
view = view_path.read_text()
old = '    private float fontScale = 1.0f;'
new = '    private float fontScale = 1.15f;\n    private String fontFamily = "sans-serif";'
view = replace_once(view, old, new, "fontScale field not found")

font_family_methods = """    public void setFontFamily(String family) {
        String[] allowed = {"sans-serif", "sans-serif-medium", "serif", "sans-serif-condensed", "casual"};
        for (String candidate : allowed) {
            if (candidate.equals(family)) {
                fontFamily = candidate;
                p.setTypeface(android.graphics.Typeface.create(candidate, android.graphics.Typeface.NORMAL));
                invalidate();
                return;
            }
        }
        fontFamily = "sans-serif";
        p.setTypeface(android.graphics.Typeface.create(fontFamily, android.graphics.Typeface.NORMAL));
        invalidate();
    }

    public String getFontFamily() { return fontFamily; }"""
if "public void setFontFamily(" not in view:
    anchor = "    public float getFontScale() { return fontScale; }"
    assert anchor in view, "fontScale methods not found"
    view = view.replace(anchor, anchor + "\n\n" + font_family_methods, 1)
view_path.write_text(view)

activity_path = Path("app/src/main/java/sa/h9/qibla/MainActivity.java")
activity = activity_path.read_text()
old = '    private static final String KEY_FONT_SCALE = "font_scale";'
new = old + '\n    private static final String KEY_FONT_FAMILY = "font_family";'
activity = replace_once(activity, old, new, "font preference key not found")

old = '        qiblaView.setFontScale(getSharedPreferences(UI_PREFS, MODE_PRIVATE).getFloat(KEY_FONT_SCALE, 1.0f));'
new = old + '\n        qiblaView.setFontFamily(getSharedPreferences(UI_PREFS, MODE_PRIVATE).getString(KEY_FONT_FAMILY, "sans-serif"));'
activity = replace_once(activity, old, new, "font initialization not found")

old = """            float current = qiblaView.getFontScale();
            float next = current < 0.95f ? 1.0f : (current < 1.10f ? 1.18f : 0.90f);
            qiblaView.setFontScale(next);
            getSharedPreferences(UI_PREFS, MODE_PRIVATE).edit().putFloat(KEY_FONT_SCALE, next).apply();
            fontSize.setText("حجم الخط: " + fontScaleLabel(next));"""
new = """            float[] sizes = {0.85f, 1.0f, 1.15f, 1.35f, 1.55f, 1.75f, 1.90f};
            float current = qiblaView.getFontScale();
            int currentIndex = 0;
            for (int i = 0; i < sizes.length; i++) {
                if (Math.abs(sizes[i] - current) < 0.04f) currentIndex = i;
            }
            float next = sizes[(currentIndex + 1) % sizes.length];
            qiblaView.setFontScale(next);
            getSharedPreferences(UI_PREFS, MODE_PRIVATE).edit().putFloat(KEY_FONT_SCALE, next).apply();
            fontSize.setText("حجم الخط: " + fontScaleLabel(next));"""
activity = replace_once(activity, old, new, "font size click block not found")

anchor = """        card.addView(fontSize, fontLp);

        Button guide = new Button(this);"""
insertion = """        card.addView(fontSize, fontLp);

        Button fontFamily = new Button(this);
        String[] fontNames = {"نسخ عربي", "نسخ متوسط", "نسخ كلاسيكي", "نسخ مضغوط", "نسخ حر"};
        String[] fontValues = {"sans-serif", "sans-serif-medium", "serif", "sans-serif-condensed", "casual"};
        fontFamily.setText("نوع الخط: " + fontName(qiblaView.getFontFamily(), fontValues, fontNames));
        fontFamily.setTextSize(18f);
        fontFamily.setTextColor(Color.WHITE);
        fontFamily.setAllCaps(false);
        GradientDrawable familyBg = new GradientDrawable();
        familyBg.setColor(0xFF22323A);
        familyBg.setCornerRadius(dp(22));
        familyBg.setStroke(dp(1), 0xFFE5B64A);
        fontFamily.setBackground(familyBg);
        fontFamily.setOnClickListener(v -> {
            int index = 0;
            for (int i = 0; i < fontValues.length; i++) {
                if (fontValues[i].equals(qiblaView.getFontFamily())) index = i;
            }
            String next = fontValues[(index + 1) % fontValues.length];
            qiblaView.setFontFamily(next);
            getSharedPreferences(UI_PREFS, MODE_PRIVATE).edit().putString(KEY_FONT_FAMILY, next).apply();
            fontFamily.setText("نوع الخط: " + fontName(next, fontValues, fontNames));
        });
        LinearLayout.LayoutParams familyLp = new LinearLayout.LayoutParams(dp(300), dp(54));
        familyLp.bottomMargin = dp(12);
        card.addView(fontFamily, familyLp);

        Button guide = new Button(this);"""
if "Button fontFamily = new Button(this);" not in activity:
    assert anchor in activity, "settings insertion point not found"
    activity = activity.replace(anchor, insertion, 1)

anchor = '    private void showAboutDetailsDialog() {'
helper = """    private String fontName(String value, String[] values, String[] labels) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(value)) return labels[i];
        }
        return labels[0];
    }

    private void showAboutDetailsDialog() {"""
if "private String fontName(" not in activity:
    assert anchor in activity, "helper insertion point not found"
    activity = activity.replace(anchor, helper, 1)
activity_path.write_text(activity)

# Apply requested dashboard layout: prayer/location panel left, compass centered, sensor cards right.
view_path = Path("app/src/main/java/sa/h9/qibla/QiblaView.java")
view = view_path.read_text()
replacements = [
    ('drawCompass(c, w * .427f, h * .53f, Math.min(h * .236f, w * .132f));',
     'drawCompass(c, w * .50f, h * .53f, Math.min(h * .236f, w * .132f));'),
    ('c.drawText("8.1 MANUAL", w * .114f, h * .116f, p);', '// Removed legacy build label as requested.'),
    ('float x = w * .018f, cardW = w * .178f, cardH = h * .116f;',
     'float x = w * .804f, cardW = w * .178f, cardH = h * .116f;'),
    ('float x=w*.715f, y=h*.175f, cw=w*.265f, ch=h*.405f;',
     'float x=w*.018f, y=h*.175f, cw=w*.265f, ch=h*.405f;'),
    ('fontScale = Math.max(0.85f, Math.min(1.45f, scale));',
     'fontScale = Math.max(0.85f, Math.min(1.90f, scale));'),
]
for old, new in replacements:
    view = replace_once(view, old, new, "Layout patch anchor not found: " + old)
view_path.write_text(view)

# Offer larger sizes suitable for the vehicle display, retaining saved selection.
activity_path = Path("app/src/main/java/sa/h9/qibla/MainActivity.java")
activity = activity_path.read_text()
old = 'float[] sizes = {0.85f, 1.0f, 1.15f, 1.30f, 1.45f};'
new = 'float[] sizes = {0.85f, 1.0f, 1.15f, 1.35f, 1.55f, 1.75f, 1.90f};'
activity = replace_once(activity, old, new, "font size list not found")
activity_path.write_text(activity)

print("Applied font choices, larger font sizes, removed legacy label, centered compass, and moved prayer/location panel left.")
