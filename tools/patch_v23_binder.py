from pathlib import Path
import re

main = Path('app/src/main/java/sa/h9/qibla/MainActivity.java')
text = main.read_text(encoding='utf-8')

# imports
text = text.replace('import android.content.Context;\n', 'import android.content.Context;\nimport android.content.ComponentName;\nimport android.content.ServiceConnection;\n')
text = text.replace('import android.content.pm.PackageManager;\n', 'import android.content.pm.PackageManager;\nimport android.content.pm.ResolveInfo;\n')
text = text.replace('import android.os.Looper;\n', 'import android.os.Looper;\nimport android.os.IBinder;\n')

# version strings in activity
text = text.replace('2.2 TEST', '2.3 TEST')

# fields
needle = '    private TextView middlewareDiagnosticText;\n    private TextView locationDiagnosticText;\n'
replacement = '''    private TextView middlewareDiagnosticText;\n    private TextView binderDiagnosticText;\n    private TextView locationDiagnosticText;\n    private boolean gwmBinderBound;\n\n    private final ServiceConnection gwmMapConnection = new ServiceConnection() {\n        @Override public void onServiceConnected(ComponentName name, IBinder service) {\n            gwmBinderBound = true;\n            String descriptor = "?";\n            try { descriptor = service.getInterfaceDescriptor(); } catch (Exception ignored) { }\n            updateBinderDiagnosticText("CONNECTED • " + name.flattenToShortString() + " • descriptor=" + descriptor + " • alive=" + service.isBinderAlive());\n        }\n\n        @Override public void onServiceDisconnected(ComponentName name) {\n            gwmBinderBound = false;\n            updateBinderDiagnosticText("DISCONNECTED • " + name.flattenToShortString());\n        }\n\n        @Override public void onBindingDied(ComponentName name) {\n            gwmBinderBound = false;\n            updateBinderDiagnosticText("BINDING DIED • " + name.flattenToShortString());\n        }\n\n        @Override public void onNullBinding(ComponentName name) {\n            gwmBinderBound = false;\n            updateBinderDiagnosticText("NULL BINDING • " + name.flattenToShortString());\n        }\n    };\n'''
if needle not in text:
    raise SystemExit('field insertion point not found')
text = text.replace(needle, replacement, 1)

# UI block: keep middleware but shrink, add binder row, move location down
old = '''        middlewareDiagnosticText = createDiagnosticText("2.3 TEST • GWM middleware: جاري الاختبار");\n        FrameLayout.LayoutParams middlewareLp = new FrameLayout.LayoutParams(dp(900), dp(125));\n        middlewareLp.gravity = Gravity.TOP | Gravity.LEFT;\n        middlewareLp.leftMargin = dp(24);\n        middlewareLp.topMargin = dp(142);\n        root.addView(middlewareDiagnosticText, middlewareLp);\n\n        locationDiagnosticText = createDiagnosticText("2.3 TEST • الموقع: جاري فحص fused/GPS");\n        FrameLayout.LayoutParams locationLp = new FrameLayout.LayoutParams(dp(700), dp(58));\n        locationLp.gravity = Gravity.TOP | Gravity.LEFT;\n        locationLp.leftMargin = dp(24);\n        locationLp.topMargin = dp(275);\n        root.addView(locationDiagnosticText, locationLp);\n'''
new = '''        middlewareDiagnosticText = createDiagnosticText("2.3 TEST • vehicleSignal: آخر فحص");\n        FrameLayout.LayoutParams middlewareLp = new FrameLayout.LayoutParams(dp(900), dp(92));\n        middlewareLp.gravity = Gravity.TOP | Gravity.LEFT;\n        middlewareLp.leftMargin = dp(24);\n        middlewareLp.topMargin = dp(132);\n        root.addView(middlewareDiagnosticText, middlewareLp);\n\n        binderDiagnosticText = createDiagnosticText("2.3 TEST • Binder: جاري البحث عن GWMMapService...");\n        FrameLayout.LayoutParams binderLp = new FrameLayout.LayoutParams(dp(900), dp(74));\n        binderLp.gravity = Gravity.TOP | Gravity.LEFT;\n        binderLp.leftMargin = dp(24);\n        binderLp.topMargin = dp(230);\n        root.addView(binderDiagnosticText, binderLp);\n\n        locationDiagnosticText = createDiagnosticText("2.3 TEST • الموقع: جاري فحص fused/GPS");\n        FrameLayout.LayoutParams locationLp = new FrameLayout.LayoutParams(dp(700), dp(58));\n        locationLp.gravity = Gravity.TOP | Gravity.LEFT;\n        locationLp.leftMargin = dp(24);\n        locationLp.topMargin = dp(310);\n        root.addView(locationDiagnosticText, locationLp);\n'''
if old not in text:
    raise SystemExit('UI block not found')
text = text.replace(old, new, 1)

# start binder after middleware probe
text = text.replace('        testGwmMiddleware();\n    }', '        testGwmMiddleware();\n        testGwmMapBinder();\n    }', 1)

# insert binder methods before updateMiddlewareDiagnosticText
marker = '    private void updateMiddlewareDiagnosticText(String text) {'
idx = text.find(marker)
if idx < 0:
    raise SystemExit('diagnostic method marker not found')
methods = r'''    private void testGwmMapBinder() {
        try {
            Intent discover = new Intent("com.gwmmapsdk.action.GWMMapService");
            discover.setPackage("com.gwm.navi.app");
            List<ResolveInfo> services = getPackageManager().queryIntentServices(discover, 0);
            String found = services == null ? "0" : String.valueOf(services.size());

            Intent explicit = new Intent();
            explicit.setComponent(new ComponentName(
                    "com.gwm.navi.app",
                    "com.gwm.navi.service.external.NaviExternalServiceV4"));

            boolean bound = bindService(explicit, gwmMapConnection, Context.BIND_AUTO_CREATE);
            updateBinderDiagnosticText("serviceMatches=" + found + " • bindService=" + bound + " • waiting callback...");
        } catch (SecurityException e) {
            updateBinderDiagnosticText("SecurityException • " + shortMessage(e));
        } catch (Exception e) {
            updateBinderDiagnosticText(e.getClass().getSimpleName() + " • " + shortMessage(e));
        }
    }

    private String shortMessage(Throwable t) {
        String s = t.getMessage();
        if (s == null) return "no message";
        return s.length() > 120 ? s.substring(0, 120) + "…" : s;
    }

    private void updateBinderDiagnosticText(String text) {
        if (binderDiagnosticText != null) binderDiagnosticText.setText("2.3 TEST • BINDER: " + text);
    }

'''
text = text[:idx] + methods + text[idx:]

# unbind safely on destroy
insert_before = '    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }'
if insert_before not in text:
    raise SystemExit('onAccuracyChanged marker not found')
unbind = '''    @Override\n    protected void onDestroy() {\n        if (gwmBinderBound) {\n            try { unbindService(gwmMapConnection); } catch (Exception ignored) { }\n            gwmBinderBound = false;\n        }\n        super.onDestroy();\n    }\n\n'''
text = text.replace(insert_before, unbind + insert_before, 1)

main.write_text(text, encoding='utf-8')

# update visible version everywhere in QiblaView
qv = Path('app/src/main/java/sa/h9/qibla/QiblaView.java')
q = qv.read_text(encoding='utf-8')
q = re.sub(r'\b(?:1\.9|2\.0|2\.1|2\.2) TEST\b', '2.3 TEST', q)
qv.write_text(q, encoding='utf-8')

# gradle version
gradle = Path('app/build.gradle')
g = gradle.read_text(encoding='utf-8')
g = re.sub(r'versionCode\s+\d+', 'versionCode 23', g)
g = re.sub(r"versionName\s+'[^']+'", "versionName '2.3-test'", g)
gradle.write_text(g, encoding='utf-8')

print('H9 Qibla 2.3 Binder probe patch applied')
