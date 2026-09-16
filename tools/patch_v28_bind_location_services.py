from pathlib import Path
import re

main = Path('app/src/main/java/sa/h9/qibla/MainActivity.java')
text = main.read_text(encoding='utf-8')
text = text.replace('2.7 TEST', '2.8 TEST')

# Replace the 2.7 direct reflection call from the already-bound Map service.
text = text.replace('            updateBinderDiagnosticText("CONNECTED • descriptor=" + descriptor + " • probing direct GWM DR service...");\n            probeDirectGwmDr();',
'''            updateBinderDiagnosticText("CONNECTED • MapControl=" + descriptor + " • binding OEM location services...");
            probeOemLocationServices();''')

start_marker = '    private void probeDirectGwmDr() {'
end_marker = '    private String shortMessage(Throwable t) {'
if start_marker not in text:
    raise SystemExit('probeDirectGwmDr not found')
start = text.index(start_marker)
end = text.index(end_marker, start)

new_probe = r'''    private final java.util.concurrent.atomic.AtomicInteger oemLocReplies = new java.util.concurrent.atomic.AtomicInteger(0);
    private final StringBuilder oemLocLog = new StringBuilder();

    private void probeOemLocationServices() {
        synchronized (oemLocLog) {
            oemLocLog.setLength(0);
            oemLocLog.append("OEM LOCATION SERVICE BIND PROBE\n");
        }
        bindOneOemLocationService("SensService",
                "com.neusoft.nmasdk.location.service.SensService");
        bindOneOemLocationService("SystemService",
                "com.neusoft.nmasdk.system.service.SystemService");
    }

    private void bindOneOemLocationService(final String label, final String className) {
        try {
            Intent i = new Intent();
            i.setComponent(new ComponentName("com.gwm.navi.app", className));
            ServiceConnection c = new ServiceConnection() {
                @Override public void onServiceConnected(ComponentName name, IBinder service) {
                    inspectOemLocationBinder(label, name, service);
                }
                @Override public void onServiceDisconnected(ComponentName name) {
                    appendOemLoc(label + " disconnected");
                }
                @Override public void onBindingDied(ComponentName name) {
                    appendOemLoc(label + " bindingDied");
                }
                @Override public void onNullBinding(ComponentName name) {
                    appendOemLoc(label + " nullBinding");
                }
            };
            boolean ok = bindService(i, c, Context.BIND_AUTO_CREATE);
            appendOemLoc(label + " bindService=" + ok + " • " + className);
        } catch (Throwable e) {
            appendOemLoc(label + " bind ERROR=" + e.getClass().getSimpleName() + ":" + shortMessage(e));
        }
    }

    private void inspectOemLocationBinder(final String label, final ComponentName name, final IBinder binder) {
        new Thread(() -> {
            String descriptor = "?";
            try { descriptor = binder.getInterfaceDescriptor(); } catch (Throwable ignored) { }
            appendOemLoc(label + " CONNECTED • desc=" + descriptor);
            appendOemLoc(label + " component=" + name.flattenToShortString());
            try {
                Context navCtx = createPackageContext("com.gwm.navi.app",
                        Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
                ClassLoader cl = navCtx.getClassLoader();

                String[] ifaceCandidates = new String[]{
                        "com.gwm.navi.service.location.ILocationService",
                        "com.neusoft.nmasdk.location.service.ILocationService"
                };
                boolean contractFound = false;
                for (String ifaceName : ifaceCandidates) {
                    try {
                        Class<?> iface = Class.forName(ifaceName, false, cl);
                        contractFound = true;
                        appendOemLoc("contract=" + ifaceName);
                        appendInterestingMethods(iface, "iface");

                        // If this is AIDL, use its real Stub.asInterface(IBinder).
                        try {
                            Class<?> stub = Class.forName(ifaceName + "$Stub", false, cl);
                            java.lang.reflect.Method asInterface = stub.getDeclaredMethod("asInterface", IBinder.class);
                            asInterface.setAccessible(true);
                            Object proxy = asInterface.invoke(null, binder);
                            appendOemLoc("asInterface=" + (proxy == null ? "null" : proxy.getClass().getName()));
                            if (proxy != null && invokeDrInfo(proxy, label + "/AIDL")) return;
                        } catch (ClassNotFoundException e) {
                            appendOemLoc("Stub absent for " + ifaceName);
                        } catch (Throwable e) {
                            appendOemLoc("asInterface ERROR=" + e.getClass().getSimpleName() + ":" + shortMessage(e));
                        }
                    } catch (ClassNotFoundException ignored) { }
                }
                if (!contractFound) appendOemLoc("ILocationService class not found in candidate packages");

                // Inspect service binder itself for OEM methods exposed directly.
                appendInterestingMethods(binder.getClass(), label + "/binder");
                if (invokeDrInfo(binder, label + "/binder")) return;

                // Inspect local interface if available.
                if (descriptor != null && !"?".equals(descriptor)) {
                    try {
                        android.os.IInterface local = binder.queryLocalInterface(descriptor);
                        appendOemLoc("queryLocalInterface=" + (local == null ? "null" : local.getClass().getName()));
                        if (local != null) {
                            appendInterestingMethods(local.getClass(), label + "/local");
                            if (invokeDrInfo(local, label + "/local")) return;
                        }
                    } catch (Throwable e) {
                        appendOemLoc("localInterface ERROR=" + e.getClass().getSimpleName());
                    }
                }
            } catch (Throwable e) {
                appendOemLoc(label + " inspect ERROR=" + e.getClass().getName() + ":" + shortMessage(e));
            }
            oemLocReplies.incrementAndGet();
        }, "H9-OEM-Loc-" + label).start();
    }

    private void appendInterestingMethods(Class<?> c, String prefix) {
        try {
            StringBuilder b = new StringBuilder(prefix).append(" methods:");
            int count = 0;
            for (java.lang.reflect.Method m : c.getMethods()) {
                String n = m.getName().toLowerCase(Locale.US);
                if (n.contains("dr") || n.contains("loc") || n.contains("heading") || n.contains("bearing") || n.contains("position")) {
                    b.append(' ').append(m.getName()).append('(').append(m.getParameterTypes().length).append(')');
                    if (++count >= 18) break;
                }
            }
            appendOemLoc(b.toString());
        } catch (Throwable ignored) { }
    }

    private boolean invokeDrInfo(Object target, String source) {
        if (target == null) return false;
        String[] names = new String[]{"getDrLocInfo", "getLocInfo", "getLastLocation"};
        for (String n : names) {
            try {
                java.lang.reflect.Method m;
                try { m = target.getClass().getMethod(n); }
                catch (NoSuchMethodException e) { m = target.getClass().getDeclaredMethod(n); }
                m.setAccessible(true);
                Object value = m.invoke(target);
                appendOemLoc(source + " " + n + "=" + String.valueOf(value));
                if ("getDrLocInfo".equals(n) && value != null) {
                    Double heading = extractHeading(value);
                    if (heading != null && !heading.isNaN()) {
                        final float h = normalize(heading.floatValue());
                        appendOemLoc("SUCCESS DR_HEADING=" + String.format(Locale.US, "%.2f°", h));
                        runOnUiThread(() -> qiblaView.setHeading(h, "اتجاه DR من نظام هافال"));
                        return true;
                    }
                }
            } catch (NoSuchMethodException ignored) {
            } catch (Throwable e) {
                appendOemLoc(source + " " + n + " ERROR=" + e.getClass().getSimpleName() + ":" + shortMessage(e));
            }
        }
        return false;
    }

    private Double extractHeading(Object dr) {
        if (dr == null) return null;
        String[] getterNames = new String[]{"getHeading", "heading", "getDirection", "getBearing", "getAzimuth"};
        for (String name : getterNames) {
            try {
                java.lang.reflect.Method m;
                try { m = dr.getClass().getMethod(name); }
                catch (NoSuchMethodException e) { m = dr.getClass().getDeclaredMethod(name); }
                m.setAccessible(true);
                Object v = m.invoke(dr);
                if (v instanceof Number) return ((Number) v).doubleValue();
            } catch (Throwable ignored) { }
        }
        for (java.lang.reflect.Field f : dr.getClass().getDeclaredFields()) {
            String n = f.getName().toLowerCase(Locale.US);
            if (!(n.contains("heading") || n.contains("bearing") || n.contains("azimuth") || n.equals("direction"))) continue;
            try {
                f.setAccessible(true);
                Object v = f.get(dr);
                if (v instanceof Number) return ((Number) v).doubleValue();
            } catch (Throwable ignored) { }
        }
        return null;
    }

    private void appendOemLoc(String line) {
        final String out;
        synchronized (oemLocLog) {
            if (oemLocLog.length() > 0) oemLocLog.append('\n');
            oemLocLog.append(line);
            out = trimDiag(oemLocLog.toString());
        }
        runOnUiThread(() -> updateBinderDiagnosticText(out));
    }

    private String trimDiag(String value) {
        if (value == null) return "null";
        return value.length() > 1900 ? value.substring(0, 1900) + "…" : value;
    }

'''
text = text[:start] + new_probe + text[end:]
main.write_text(text, encoding='utf-8')

qv = Path('app/src/main/java/sa/h9/qibla/QiblaView.java')
q = qv.read_text(encoding='utf-8')
q = q.replace('2.7 TEST', '2.8 TEST').replace('2.6 TEST', '2.8 TEST').replace('2.5 TEST', '2.8 TEST').replace('1.9 TEST', '2.8 TEST')
qv.write_text(q, encoding='utf-8')

gradle = Path('app/build.gradle')
g = gradle.read_text(encoding='utf-8')
g = re.sub(r'versionCode\s+\d+', 'versionCode 28', g)
g = re.sub(r"versionName\s+'[^']+'", "versionName '2.8-test'", g)
gradle.write_text(g, encoding='utf-8')
print('H9 Qibla 2.8 OEM location service binding probe applied')
