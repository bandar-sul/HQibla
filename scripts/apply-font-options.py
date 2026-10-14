from pathlib import Path

view_path = Path("app/src/main/java/sa/h9/qibla/QiblaView.java")
view = view_path.read_text()
old = '    private float fontScale = 1.0f;'
new = '    private float fontScale = 1.15f;\n    private String fontFamily = "sans-serif";'
assert old in view, "fontScale field not found"
view = view.replace(old, new, 1)

old = """    public void setFontScale(float scale) {
        fontScale = Math.max(0.85f, Math.min(1.30f, scale));
        invalidate();
    }

    public float getFontScale() { return fontScale; }"""
new = """    public void setFontScale(float scale) {
        fontScale = Math.max(0.85f, Math.min(1.45f, scale));
        invalidate();
    }

    public float getFontScale() { return fontScale; }

    public void setFontFamily(String family) {
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
assert old in view, "fontScale methods not found"
view_path.write_text(view.replace(old, new, 1))

activity_path = Path("app/src/main/java/sa/h9/qibla/MainActivity.java")
activity = activity_path.read_text()
old = '    private static final String KEY_FONT_SCALE = "font_scale";'
assert old in activity, "font preference key not found"
activity = activity.replace(old, old + '\n    private static final String KEY_FONT_FAMILY = "font_family";', 1)

old = '        qiblaView.setFontScale(getSharedPreferences(UI_PREFS, MODE_PRIVATE).getFloat(KEY_FONT_SCALE, 1.0f));'
assert old in activity, "font initialization not found"
activity = activity.replace(old, old + '\n        qiblaView.setFontFamily(getSharedPreferences(UI_PREFS, MODE_PRIVATE).getString(KEY_FONT_FAMILY, "sans-serif"));', 1)

old = """            float current = qiblaView.getFontScale();
            float next = current < 0.95f ? 1.0f : (current < 1.10f ? 1.18f : 0.90f);
            qiblaView.setFontScale(next);
            getSharedPreferences(UI_PREFS, MODE_PRIVATE).edit().putFloat(KEY_FONT_SCALE, next).apply();
            fontSize.setText("حجم الخط: " + fontScaleLabel(next));"""
new = """            float[] sizes = {0.85f, 1.0f, 1.15f, 1.30f, 1.45f};
            float current = qiblaView.getFontScale();
            int currentIndex = 0;
            for (int i = 0; i < sizes.length; i++) {
                if (Math.abs(sizes[i] - current) < 0.04f) currentIndex = i;
            }
            float next = sizes[(currentIndex + 1) % sizes.length];
            qiblaView.setFontScale(next);
            getSharedPreferences(UI_PREFS, MODE_PRIVATE).edit().putFloat(KEY_FONT_SCALE, next).apply();
            fontSize.setText("حجم الخط: " + fontScaleLabel(next));"""
assert old in activity, "font size click block not found"
activity = activity.replace(old, new, 1)

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
assert anchor in activity, "helper insertion point not found"
activity_path.write_text(activity.replace(anchor, helper, 1))
print("Applied five typeface choices and five persistent size levels.")
