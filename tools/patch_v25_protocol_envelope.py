from pathlib import Path

p = Path('app/src/main/java/sa/h9/qibla/MainActivity.java')
s = p.read_text()
start = s.index('    private void probeExactGwmLocationApi(IBinder service) {')
end = s.index('    private String shortMessage(Throwable t) {', start)
new = r'''    private void probeExactGwmLocationApi(IBinder service) {
        new Thread(() -> {
            final String iface = "com.gwm.map.sdk.MapControl";
            final String pkg = getPackageName();
            final String[] protocolIds = new String[]{"10022000", "10042000", "10062000", "10082000"};
            final String[] labels = new String[]{"CARD_STATE", "NAVI_MISC", "GUIDE_TBT", "NAVI_FOCUS"};
            StringBuilder out = new StringBuilder("V4 envelope requestSync\n");
            long reqId = System.currentTimeMillis();
            for (int i = 0; i < protocolIds.length; i++) {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    org.json.JSONObject req = new org.json.JSONObject();
                    req.put("pkgName", pkg);
                    req.put("protocolId", protocolIds[i]);
                    req.put("reqId", reqId + i);
                    req.put("verName", "1.0");
                    req.put("data", new org.json.JSONObject());
                    String json = req.toString();
                    data.writeInterfaceToken(iface);
                    data.writeString(json);
                    boolean sent = service.transact(4, data, reply, 0);
                    reply.readException();
                    String result = reply.readString();
                    out.append(labels[i]).append('(').append(protocolIds[i]).append(") sent=").append(sent).append("\n");
                    out.append(result == null ? "null" : result).append("\n");
                } catch (Throwable e) {
                    out.append(labels[i]).append(" ERR=").append(e.getClass().getSimpleName()).append(':').append(shortMessage(e)).append("\n");
                } finally {
                    data.recycle();
                    reply.recycle();
                }
            }
            String text = out.toString();
            if (text.length() > 1500) text = text.substring(0, 1500) + "…";
            final String finalText = text;
            runOnUiThread(() -> updateBinderDiagnosticText(finalText));
        }, "H9-GWM-V4-Envelope-Probe").start();
    }

'''
s = s[:start] + new + s[end:]
s = s.replace('2.5 TEST • BINDER:', '2.5 TEST • BINDER:')
p.write_text(s)

q = Path('app/src/main/java/sa/h9/qibla/QiblaView.java')
if q.exists():
    t = q.read_text().replace('H9 Qibla 2.4 TEST', 'H9 Qibla 2.5 TEST').replace('2.4 TEST', '2.5 TEST')
    q.write_text(t)

b = Path('app/build.gradle')
t = b.read_text()
t = t.replace("versionCode 24", "versionCode 25").replace("versionName '2.4-test'", "versionName '2.5-test'")
b.write_text(t)
