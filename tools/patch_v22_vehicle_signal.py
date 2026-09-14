from pathlib import Path
import re

main = Path('app/src/main/java/sa/h9/qibla/MainActivity.java')
text = main.read_text(encoding='utf-8')
text = text.replace('2.1 TEST', '2.2 TEST')
text = text.replace('new FrameLayout.LayoutParams(dp(700), dp(58));\n        middlewareLp.gravity', 'new FrameLayout.LayoutParams(dp(900), dp(125));\n        middlewareLp.gravity')
text = text.replace('locationLp.topMargin = dp(207);', 'locationLp.topMargin = dp(275);')
text = text.replace('view.setTextSize(14f);', 'view.setTextSize(12f);')

start = text.index('    private void testGwmMiddleware() {')
end = text.index('    private void updateMiddlewareDiagnosticText(String text) {', start)
new_method = r'''    private void testGwmMiddleware() {
        updateMiddlewareDiagnosticText("vehicleSignal: قراءة جميع الأعمدة...");
        new Thread(() -> {
            if (checkSelfPermission("com.gwm.navi.permission.READ_CONTENT") != PackageManager.PERMISSION_GRANTED) {
                runOnUiThread(() -> updateMiddlewareDiagnosticText("READ_CONTENT غير ممنوحة للتطبيق"));
                return;
            }
            Cursor c = null;
            try {
                Uri uri = Uri.parse("content://com.gwm.navi.middleware/vehicleSignal");
                c = getContentResolver().query(uri, null, null, null, null);
                if (c == null) {
                    runOnUiThread(() -> updateMiddlewareDiagnosticText("vehicleSignal=null"));
                    return;
                }
                String[] cols = c.getColumnNames();
                StringBuilder matches = new StringBuilder();
                StringBuilder all = new StringBuilder();
                String[] keys = new String[]{"heading","bearing","yaw","angle","direction","speed","steering","gyro","course","azimuth","orientation","wheel"};
                if (c.moveToFirst()) {
                    for (int i = 0; i < cols.length; i++) {
                        String name = cols[i];
                        String lower = name == null ? "" : name.toLowerCase(Locale.US);
                        String value;
                        try { value = c.isNull(i) ? "null" : c.getString(i); }
                        catch (Exception e) { value = "?"; }
                        if (value != null && value.length() > 24) value = value.substring(0, 24) + "…";
                        if (all.length() > 0) all.append(" | ");
                        all.append(name).append('=').append(value);
                        for (String key : keys) {
                            if (lower.contains(key)) {
                                if (matches.length() > 0) matches.append(" • ");
                                matches.append(name).append('=').append(value);
                                break;
                            }
                        }
                    }
                }
                String out = matches.length() > 0
                        ? "MATCH: " + matches + "\nALL(" + cols.length + "): " + all
                        : "لا توجد أسماء heading/yaw/speed مباشرة • ALL(" + cols.length + "): " + all;
                if (out.length() > 900) out = out.substring(0, 900) + "…";
                final String finalOut = out;
                runOnUiThread(() -> updateMiddlewareDiagnosticText(finalOut));
            } catch (SecurityException e) {
                runOnUiThread(() -> updateMiddlewareDiagnosticText("vehicleSignal SecurityException"));
            } catch (Exception e) {
                final String err = e.getClass().getSimpleName() + ": " + e.getMessage();
                runOnUiThread(() -> updateMiddlewareDiagnosticText(err));
            } finally {
                if (c != null) c.close();
            }
        }, "H9-VehicleSignal-FullProbe").start();
    }

'''
text = text[:start] + new_method + text[end:]
main.write_text(text, encoding='utf-8')

qv = Path('app/src/main/java/sa/h9/qibla/QiblaView.java')
qv.write_text(qv.read_text(encoding='utf-8').replace('2.1 TEST', '2.2 TEST'), encoding='utf-8')

gradle = Path('app/build.gradle')
g = gradle.read_text(encoding='utf-8')
g = re.sub(r'versionCode\s+\d+', 'versionCode 22', g)
g = re.sub(r"versionName\s+'[^']+'", "versionName '2.2-test'", g)
gradle.write_text(g, encoding='utf-8')
print('H9 Qibla 2.2 full vehicleSignal probe applied')
