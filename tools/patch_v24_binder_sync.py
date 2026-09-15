from pathlib import Path
import re

main = Path('app/src/main/java/sa/h9/qibla/MainActivity.java')
text = main.read_text(encoding='utf-8')

text = text.replace('import android.os.Looper;\nimport android.os.IBinder;', 'import android.os.Looper;\nimport android.os.IBinder;\nimport android.os.Parcel;')
text = text.replace('2.3 TEST', '2.4 TEST')
text = text.replace('private boolean gwmBinderBound;\n', 'private boolean gwmBinderBound;\n    private IBinder gwmMapBinder;\n')

old = '''        @Override public void onServiceConnected(ComponentName name, IBinder service) {\n            gwmBinderBound = true;\n            String descriptor = "?";\n            try { descriptor = service.getInterfaceDescriptor(); } catch (Exception ignored) { }\n            updateBinderDiagnosticText("CONNECTED • " + name.flattenToShortString() + " • descriptor=" + descriptor + " • alive=" + service.isBinderAlive());\n        }'''
new = '''        @Override public void onServiceConnected(ComponentName name, IBinder service) {\n            gwmBinderBound = true;\n            gwmMapBinder = service;\n            String descriptor = "?";\n            try { descriptor = service.getInterfaceDescriptor(); } catch (Exception ignored) { }\n            updateBinderDiagnosticText("CONNECTED • descriptor=" + descriptor + " • بدء requestSync...");\n            probeMapControlSync(service);\n        }'''
if old not in text:
    raise SystemExit('onServiceConnected block not found')
text = text.replace(old, new)
text = text.replace('gwmBinderBound = false;\n            updateBinderDiagnosticText("DISCONNECTED', 'gwmBinderBound = false;\n            gwmMapBinder = null;\n            updateBinderDiagnosticText("DISCONNECTED')
text = text.replace('gwmBinderBound = false;\n            updateBinderDiagnosticText("BINDING DIED', 'gwmBinderBound = false;\n            gwmMapBinder = null;\n            updateBinderDiagnosticText("BINDING DIED')
text = text.replace('gwmBinderBound = false;\n            updateBinderDiagnosticText("NULL BINDING', 'gwmBinderBound = false;\n            gwmMapBinder = null;\n            updateBinderDiagnosticText("NULL BINDING')

marker = '    private String shortMessage(Throwable t) {'
probe = r'''    private void probeMapControlSync(IBinder service) {
        new Thread(() -> {
            String[] requests = new String[]{
                    "{}",
                    "{\"action\":\"getLocation\"}",
                    "{\"method\":\"getLocation\"}",
                    "{\"action\":\"getCurrentPosition\"}",
                    "{\"method\":\"getCurrentPosition\"}"
            };
            StringBuilder report = new StringBuilder("TX4 requestSync");
            for (String request : requests) {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken("com.gwm.map.sdk.MapControl");
                    data.writeString(request);
                    boolean handled = service.transact(4, data, reply, 0);
                    report.append("\n").append(request).append(" => transact=").append(handled);
                    if (handled) {
                        try {
                            reply.readException();
                            String result = reply.readString();
                            if (result == null) result = "null";
                            if (result.length() > 180) result = result.substring(0, 180) + "…";
                            report.append(" result=").append(result);
                        } catch (Exception e) {
                            report.append(" reply=").append(e.getClass().getSimpleName()).append(':').append(shortMessage(e));
                        }
                    }
                } catch (Exception e) {
                    report.append("\n").append(request).append(" => ").append(e.getClass().getSimpleName()).append(':').append(shortMessage(e));
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }
            String out = report.toString();
            if (out.length() > 1100) out = out.substring(0, 1100) + "…";
            final String finalOut = out;
            runOnUiThread(() -> updateBinderDiagnosticText(finalOut));
        }, "H9-MapControl-SyncProbe").start();
    }

'''
if marker not in text:
    raise SystemExit('shortMessage marker not found')
text = text.replace(marker, probe + marker)

# Make Binder box taller and move location box lower for multi-line requestSync results.
text = text.replace('new FrameLayout.LayoutParams(dp(900), dp(74))', 'new FrameLayout.LayoutParams(dp(900), dp(170))')
text = text.replace('binderLp.topMargin = dp(230);', 'binderLp.topMargin = dp(230);')
text = text.replace('locationLp.topMargin = dp(310);', 'locationLp.topMargin = dp(405);')

# Clear binder reference on destroy.
text = text.replace('            gwmBinderBound = false;\n        }\n        super.onDestroy();', '            gwmBinderBound = false;\n            gwmMapBinder = null;\n        }\n        super.onDestroy();')

main.write_text(text, encoding='utf-8')

qv = Path('app/src/main/java/sa/h9/qibla/QiblaView.java')
q = qv.read_text(encoding='utf-8')
q = re.sub(r'\b(?:1\.9|2\.[0-3]) TEST\b', '2.4 TEST', q)
qv.write_text(q, encoding='utf-8')

gradle = Path('app/build.gradle')
g = gradle.read_text(encoding='utf-8')
g = re.sub(r'versionCode\s+\d+', 'versionCode 24', g)
g = re.sub(r"versionName\s+'[^']+'", "versionName '2.4-test'", g)
gradle.write_text(g, encoding='utf-8')

print('H9 Qibla 2.4 Binder requestSync probe applied')
