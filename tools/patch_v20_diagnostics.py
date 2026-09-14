from pathlib import Path

main = Path('app/src/main/java/sa/h9/qibla/MainActivity.java')
text = main.read_text(encoding='utf-8')

text = text.replace('private TextView diagnosticText;\n', 'private TextView diagnosticText;\n    private TextView locationDiagnosticText;\n')
text = text.replace('diagnosticText.setText("1.9 TEST • GWM middleware: جاري الاختبار");', 'diagnosticText.setText("2.0 TEST • GWM middleware: جاري الاختبار");')

needle = '''        root.addView(diagnosticText, diagnosticLp);\n\n        Button aboutButton = new Button(this);'''
insert = '''        root.addView(diagnosticText, diagnosticLp);\n\n        locationDiagnosticText = new TextView(this);\n        locationDiagnosticText.setText("2.0 TEST • Location: جاري الفحص");\n        locationDiagnosticText.setTextSize(14f);\n        locationDiagnosticText.setTextColor(0xFFF1F7FA);\n        locationDiagnosticText.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);\n        locationDiagnosticText.setPadding(dp(14), 0, dp(14), 0);\n        GradientDrawable locationDiagnosticBg = new GradientDrawable();\n        locationDiagnosticBg.setColor(0xCC07151C);\n        locationDiagnosticBg.setCornerRadius(dp(12));\n        locationDiagnosticBg.setStroke(dp(1), 0xFFE5B64A);\n        locationDiagnosticText.setBackground(locationDiagnosticBg);\n        FrameLayout.LayoutParams locationDiagnosticLp = new FrameLayout.LayoutParams(dp(560), dp(50));\n        locationDiagnosticLp.gravity = Gravity.TOP | Gravity.LEFT;\n        locationDiagnosticLp.leftMargin = dp(24);\n        locationDiagnosticLp.topMargin = dp(214);\n        root.addView(locationDiagnosticText, locationDiagnosticLp);\n\n        Button aboutButton = new Button(this);'''
if needle in text and 'locationDiagnosticText = new TextView' not in text:
    text = text.replace(needle, insert)

repls = {
    'else updateDiagnosticText("fused متاح • بانتظار أول موقع");': 'else updateLocationDiagnosticText("fused متاح • بانتظار أول موقع");',
    'updateDiagnosticText("fused غير متاح للنظام");': 'updateLocationDiagnosticText("fused غير متاح للنظام");',
    'updateDiagnosticText("لا توجد صلاحية موقع");': 'updateLocationDiagnosticText("لا توجد صلاحية موقع");',
    'updateDiagnosticText("fused رفض الطلب • GPS احتياط");': 'updateLocationDiagnosticText("fused رفض الطلب • GPS احتياط");',
    'updateDiagnosticText("Provider: fused   Bearing: " + bearingText + "   Speed: " + speedText + "   Accuracy: " + accuracyText);': 'updateLocationDiagnosticText("Provider: fused   Bearing: " + bearingText + "   Speed: " + speedText + "   Accuracy: " + accuracyText);',
    'if (diagnosticText != null) diagnosticText.setText("1.9 TEST • " + text);': 'if (diagnosticText != null) diagnosticText.setText("2.0 TEST • GWM: " + text);'
}
for a,b in repls.items():
    text = text.replace(a,b)

needle2 = '''    private void updateDiagnosticText(String text) {\n        if (diagnosticText != null) diagnosticText.setText("2.0 TEST • GWM: " + text);\n    }\n'''
if needle2 in text and 'private void updateLocationDiagnosticText' not in text:
    text = text.replace(needle2, needle2 + '''\n    private void updateLocationDiagnosticText(String text) {\n        if (locationDiagnosticText != null) locationDiagnosticText.setText("2.0 TEST • Location: " + text);\n    }\n''')

main.write_text(text, encoding='utf-8')

qv = Path('app/src/main/java/sa/h9/qibla/QiblaView.java')
qtext = qv.read_text(encoding='utf-8').replace('"1.9 TEST"', '"2.0 TEST"')
qv.write_text(qtext, encoding='utf-8')

bg = Path('app/build.gradle')
btext = bg.read_text(encoding='utf-8')
btext = btext.replace("versionCode 19", "versionCode 20").replace("versionName '1.9-test'", "versionName '2.0-test'")
bg.write_text(btext, encoding='utf-8')

wf = Path('.github/workflows/build-apk.yml')
wtext = wf.read_text(encoding='utf-8')
wtext = wtext.replace('Build H9 Qibla 1.9 Test APK', 'Build H9 Qibla 2.0 Test APK')
wtext = wtext.replace('H9-Qibla-1.9-Test-Haval-H9', 'H9-Qibla-2.0-Test-Haval-H9')
wf.write_text(wtext, encoding='utf-8')

print('H9 Qibla 2.0 diagnostics patch applied')
