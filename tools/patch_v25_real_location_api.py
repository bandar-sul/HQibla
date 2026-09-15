from pathlib import Path
import re

main = Path('app/src/main/java/sa/h9/qibla/MainActivity.java')
text = main.read_text(encoding='utf-8')
text = text.replace('2.4 TEST', '2.5 TEST')

old = '''        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            gwmBinderBound = true;
            gwmMapBinder = service;
            String descriptor = "?";
            try { descriptor = service.getInterfaceDescriptor(); } catch (Exception ignored) { }
            updateBinderDiagnosticText("CONNECTED • descriptor=" + descriptor + " • بدء requestSync...");
            probeMapControlSync(service);
        }
'''
new = '''        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            gwmBinderBound = true;
            gwmMapBinder = service;
            String descriptor = "?";
            try { descriptor = service.getInterfaceDescriptor(); } catch (Exception ignored) { }
            updateBinderDiagnosticText("CONNECTED • descriptor=" + descriptor + " • OEM LOCATION/DR...");
            probeExactGwmLocationApi(service);
        }
'''
if old not in text:
    raise SystemExit('current 2.4 onServiceConnected block not found')
text = text.replace(old, new)

# Replace the complete old exploratory method with the exact OEM read-only location probe.
start_marker = '    private void probeMapControlSync(IBinder service) {'
end_marker = '    private String shortMessage(Throwable t) {'
if start_marker not in text:
    raise SystemExit('probeMapControlSync not found')
start = text.index(start_marker)
end = text.index(end_marker, start)
new_probe = r'''    private void probeExactGwmLocationApi(IBinder service) {
        new Thread(() -> {
            final String iface = "com.gwm.map.sdk.MapControl";
            final String version = "v1.1.10-SNAPSHOT_t20250217";
            final int[] actions = new int[]{14001, 14002};
            final String[] labels = new String[]{"GET_LOCATION_INFO", "GET_DR_INFO"};
            StringBuilder out = new StringBuilder("OEM exact requestSync\n");
            for (int i = 0; i < actions.length; i++) {
                android.os.Parcel data = android.os.Parcel.obtain();
                android.os.Parcel reply = android.os.Parcel.obtain();
                try {
                    org.json.JSONObject req = new org.json.JSONObject();
                    req.put("actionType", actions[i]);
                    req.put("version", version);
                    String json = req.toString();
                    data.writeInterfaceToken(iface);
                    data.writeString(json);
                    boolean sent = service.transact(4, data, reply, 0);
                    reply.readException();
                    String result = reply.readString();
                    out.append(labels[i]).append('(').append(actions[i]).append(") sent=").append(sent).append("\n");
                    out.append(result == null ? "null" : result).append("\n");
                } catch (Throwable e) {
                    out.append(labels[i]).append(" ERROR=").append(e.getClass().getSimpleName()).append(':').append(shortMessage(e)).append("\n");
                } finally {
                    data.recycle();
                    reply.recycle();
                }
            }
            out.append("caller=").append(getPackageName()).append(" • GWM api_auth whitelist=com.gwm.voiceassistantoversea→vr");
            String s = out.toString();
            if (s.length() > 1200) s = s.substring(0, 1200) + "…";
            final String finalOut = s;
            runOnUiThread(() -> updateBinderDiagnosticText(finalOut));
        }, "H9-GWM-ExactLocationApi").start();
    }

'''
text = text[:start] + new_probe + text[end:]
main.write_text(text, encoding='utf-8')

qv = Path('app/src/main/java/sa/h9/qibla/QiblaView.java')
q = qv.read_text(encoding='utf-8')
q = q.replace('2.4 TEST', '2.5 TEST').replace('2.3 TEST', '2.5 TEST').replace('1.9 TEST', '2.5 TEST')
qv.write_text(q, encoding='utf-8')

gradle = Path('app/build.gradle')
g = gradle.read_text(encoding='utf-8')
g = re.sub(r'versionCode\s+\d+', 'versionCode 25', g)
g = re.sub(r"versionName\s+'[^']+'", "versionName '2.5-test'", g)
gradle.write_text(g, encoding='utf-8')

print('H9 Qibla 2.5 exact OEM location/DR requestSync probe applied')
