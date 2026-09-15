from pathlib import Path
import re

main = Path('app/src/main/java/sa/h9/qibla/MainActivity.java')
text = main.read_text(encoding='utf-8')
text = text.replace('2.5 TEST', '2.6 TEST')

if 'private android.os.Binder gwmMapCallbackBinder;' not in text:
    text = text.replace('    private IBinder gwmMapBinder;\n', '    private IBinder gwmMapBinder;\n    private android.os.Binder gwmMapCallbackBinder;\n')

text = text.replace('            updateBinderDiagnosticText("CONNECTED • descriptor=" + descriptor + " • OEM LOCATION/DR...");\n            probeExactGwmLocationApi(service);',
'''            updateBinderDiagnosticText("CONNECTED • descriptor=" + descriptor + " • registering callback + subscribe...");
            probeGwmSubscribe(service);''')

start_marker = '    private void probeExactGwmLocationApi(IBinder service) {'
end_marker = '    private String shortMessage(Throwable t) {'
if start_marker not in text:
    raise SystemExit('probeExactGwmLocationApi not found')
start = text.index(start_marker)
end = text.index(end_marker, start)

new_probe = r'''    private void probeGwmSubscribe(IBinder service) {
        new Thread(() -> {
            final String mapIface = "com.gwm.map.sdk.MapControl";
            final String cbIface = "com.gwm.map.sdk.MapCallback";
            final String pkg = getPackageName();
            final String[] protocolIds = new String[]{
                    "10011000", "10022000", "10031000", "10042000", "10051000",
                    "10062000", "10071000", "10082000", "10091000"
            };
            final StringBuilder log = new StringBuilder("V4 callback/subscribe probe\n");

            try {
                gwmMapCallbackBinder = new android.os.Binder() {
                    { attachInterface(null, cbIface); }

                    @Override
                    protected boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags)
                            throws android.os.RemoteException {
                        if (code == INTERFACE_TRANSACTION) {
                            if (reply != null) reply.writeString(cbIface);
                            return true;
                        }
                        try {
                            data.enforceInterface(cbIface);
                            if (code == 1) { // MapCallback.onResponse(String)
                                String response = data.readString();
                                final String msg = response == null ? "null" : response;
                                runOnUiThread(() -> updateBinderDiagnosticText("CALLBACK onResponse:\n" + trimDiag(msg)));
                                if (reply != null) reply.writeNoException();
                                return true;
                            }
                            if (code == 2) { // MapCallback.onReceiveDataFromMap(String, Bundle)
                                String key = data.readString();
                                android.os.Bundle bundle = null;
                                if (data.readInt() != 0) bundle = android.os.Bundle.CREATOR.createFromParcel(data);
                                String bundleText = bundle == null ? "null" : bundle.toString();
                                final String msg = "CALLBACK data key=" + key + "\n" + bundleText;
                                runOnUiThread(() -> updateBinderDiagnosticText(trimDiag(msg)));
                                if (reply != null) reply.writeNoException();
                                return true;
                            }
                        } catch (Throwable e) {
                            final String err = "CALLBACK tx=" + code + " ERROR=" + e.getClass().getSimpleName() + ":" + shortMessage(e);
                            runOnUiThread(() -> updateBinderDiagnosticText(err));
                        }
                        return super.onTransact(code, data, reply, flags);
                    }
                };

                android.os.Parcel d1 = android.os.Parcel.obtain();
                android.os.Parcel r1 = android.os.Parcel.obtain();
                try {
                    d1.writeInterfaceToken(mapIface);
                    d1.writeString(pkg);
                    d1.writeStrongBinder(gwmMapCallbackBinder);
                    boolean ok = service.transact(1, d1, r1, 0); // registerCallBack
                    r1.readException();
                    log.append("registerCallBack tx1=").append(ok).append('\n');
                } finally { d1.recycle(); r1.recycle(); }

                android.os.Parcel d2 = android.os.Parcel.obtain();
                android.os.Parcel r2 = android.os.Parcel.obtain();
                try {
                    d2.writeInterfaceToken(mapIface);
                    d2.writeString(pkg);
                    d2.writeStringArray(protocolIds);
                    boolean ok = service.transact(5, d2, r2, 0); // subscribe(String,String[])
                    r2.readException();
                    log.append("subscribe tx5=").append(ok).append(" ids=").append(java.util.Arrays.toString(protocolIds)).append('\n');
                } finally { d2.recycle(); r2.recycle(); }

                // Keep one validated sync request so we know the transport is still accepted.
                android.os.Parcel d3 = android.os.Parcel.obtain();
                android.os.Parcel r3 = android.os.Parcel.obtain();
                try {
                    org.json.JSONObject req = new org.json.JSONObject();
                    req.put("pkgName", pkg);
                    req.put("protocolId", "10022000");
                    req.put("reqId", String.valueOf(System.currentTimeMillis()));
                    req.put("verName", "1.0");
                    req.put("data", new org.json.JSONObject());
                    d3.writeInterfaceToken(mapIface);
                    d3.writeString(req.toString());
                    boolean ok = service.transact(4, d3, r3, 0);
                    r3.readException();
                    String response = r3.readString();
                    log.append("requestSync tx4=").append(ok).append(" -> ").append(response).append('\n');
                } finally { d3.recycle(); r3.recycle(); }

                log.append("waiting for live MapCallback…");
            } catch (Throwable e) {
                log.append("ERROR=").append(e.getClass().getSimpleName()).append(':').append(shortMessage(e));
            }
            final String out = trimDiag(log.toString());
            runOnUiThread(() -> updateBinderDiagnosticText(out));
        }, "H9-GWM-SubscribeProbe").start();
    }

    private String trimDiag(String value) {
        if (value == null) return "null";
        return value.length() > 1500 ? value.substring(0, 1500) + "…" : value;
    }

'''
text = text[:start] + new_probe + text[end:]
main.write_text(text, encoding='utf-8')

qv = Path('app/src/main/java/sa/h9/qibla/QiblaView.java')
q = qv.read_text(encoding='utf-8')
q = q.replace('2.5 TEST', '2.6 TEST').replace('2.4 TEST', '2.6 TEST').replace('2.3 TEST', '2.6 TEST').replace('1.9 TEST', '2.6 TEST')
qv.write_text(q, encoding='utf-8')

gradle = Path('app/build.gradle')
g = gradle.read_text(encoding='utf-8')
g = re.sub(r'versionCode\s+\d+', 'versionCode 26', g)
g = re.sub(r"versionName\s+'[^']+'", "versionName '2.6-test'", g)
gradle.write_text(g, encoding='utf-8')

print('H9 Qibla 2.6 MapControl subscribe + callback probe applied')
