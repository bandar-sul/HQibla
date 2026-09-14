from pathlib import Path
import re

main = Path('app/src/main/java/sa/h9/qibla/MainActivity.java')
text = main.read_text(encoding='utf-8')
text = text.replace('2.0 TEST', '2.1 TEST')

start = text.index('    private void testGwmMiddleware() {')
end = text.index('    private void updateMiddlewareDiagnosticText(String text) {', start)
new_method = r'''    private void testGwmMiddleware() {
        updateMiddlewareDiagnosticText("فحص URI الحقيقية من GWMNavi.apk...");
        new Thread(() -> {
            if (checkSelfPermission("com.gwm.navi.permission.READ_CONTENT") != PackageManager.PERMISSION_GRANTED) {
                runOnUiThread(() -> updateMiddlewareDiagnosticText("READ_CONTENT غير ممنوحة للتطبيق"));
                return;
            }

            StringBuilder report = new StringBuilder();
            probeMiddlewareUri(report,
                    "project",
                    new String[]{"project", "area", "wrapper", "navi_type", "drive_handle_type", "fuel_type", "map_widget_type"});
            probeMiddlewareUri(report,
                    "vehicleSignal",
                    new String[]{"ev_odometer_remain", "ev_battery_remain", "ev_battery_remain_percentage", "ev_battery_total", "hvac_state", "hvac_temp", "drive_type", "basic_outside_temp"});
            probeMiddlewareUri(report, "ability/data", null);

            String out = report.toString();
            if (out.length() > 260) out = out.substring(0, 260) + "…";
            final String finalOut = out.length() == 0 ? "لم ترجع URI الحقيقية بيانات" : out;
            runOnUiThread(() -> updateMiddlewareDiagnosticText(finalOut));
        }, "H9-GwmMiddleware-RealProbe").start();
    }

    private void probeMiddlewareUri(StringBuilder report, String path, String[] projection) {
        Cursor c = null;
        try {
            Uri uri = Uri.parse("content://com.gwm.navi.middleware/" + path);
            c = getContentResolver().query(uri, projection, null, null, null);
            if (report.length() > 0) report.append(" • ");
            if (c == null) {
                report.append(path).append("=null");
                return;
            }
            report.append(path).append(" OK rows=").append(c.getCount());
            String[] cols = c.getColumnNames();
            if (c.moveToFirst()) {
                report.append(" [");
                int shown = 0;
                for (int i = 0; i < cols.length && shown < 3; i++) {
                    String v;
                    try { v = c.getString(i); } catch (Exception e) { v = "?"; }
                    if (shown > 0) report.append(", ");
                    report.append(cols[i]).append('=').append(v);
                    shown++;
                }
                report.append(']');
            } else if (cols.length > 0) {
                report.append(" cols=");
                for (int i = 0; i < Math.min(cols.length, 4); i++) {
                    if (i > 0) report.append(',');
                    report.append(cols[i]);
                }
            }
        } catch (SecurityException e) {
            if (report.length() > 0) report.append(" • ");
            report.append(path).append(" SecurityException");
        } catch (Exception e) {
            if (report.length() > 0) report.append(" • ");
            report.append(path).append('=').append(e.getClass().getSimpleName());
        } finally {
            if (c != null) c.close();
        }
    }

'''
text = text[:start] + new_method + text[end:]
main.write_text(text, encoding='utf-8')

qv = Path('app/src/main/java/sa/h9/qibla/QiblaView.java')
q = qv.read_text(encoding='utf-8').replace('2.0 TEST', '2.1 TEST')
qv.write_text(q, encoding='utf-8')

gradle = Path('app/build.gradle')
g = gradle.read_text(encoding='utf-8')
g = re.sub(r'versionCode\s+\d+', 'versionCode 21', g)
g = re.sub(r"versionName\s+'[^']+'", "versionName '2.1-test'", g)
gradle.write_text(g, encoding='utf-8')

print('H9 Qibla 2.1 real middleware patch applied')
