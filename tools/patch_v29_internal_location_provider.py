from pathlib import Path
import re

main = Path('app/src/main/java/sa/h9/qibla/MainActivity.java')
text = main.read_text(encoding='utf-8')
text = text.replace('2.8 TEST', '2.9 TEST')
text = text.replace('            updateBinderDiagnosticText("CONNECTED • MapControl=" + descriptor + " • binding OEM location services...");\n            probeOemLocationServices();',
'''            updateBinderDiagnosticText("CONNECTED • MapControl=" + descriptor + " • scanning internal ILocationService provider...");
            probeInternalGwmLocationService();''')

marker = '    private final java.util.concurrent.atomic.AtomicInteger oemLocReplies = new java.util.concurrent.atomic.AtomicInteger(0);\n'
if marker not in text:
    raise SystemExit('insert marker not found')

probe = r'''    private void probeInternalGwmLocationService() {
        synchronized (oemLocLog) {
            oemLocLog.setLength(0);
            oemLocLog.append("INTERNAL ILocationService PROVIDER PROBE\n");
        }
        new Thread(() -> {
            dalvik.system.DexFile dex = null;
            try {
                Context navCtx = createPackageContext("com.gwm.navi.app",
                        Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
                ClassLoader cl = navCtx.getClassLoader();
                android.content.pm.ApplicationInfo ai = getPackageManager().getApplicationInfo("com.gwm.navi.app", 0);
                appendOemLoc("apk=" + ai.sourceDir);

                Class<?> contract = null;
                try {
                    contract = Class.forName("com.gwm.navi.service.location.ILocationService", false, cl);
                    appendOemLoc("contract=" + contract.getName() + " • getDrLocInfo confirmed");
                } catch (Throwable e) {
                    appendOemLoc("contract load ERROR=" + e.getClass().getSimpleName());
                }

                dex = new dalvik.system.DexFile(ai.sourceDir);
                java.util.Enumeration<String> entries = dex.entries();
                int scanned = 0;
                int candidateClasses = 0;
                while (entries.hasMoreElements()) {
                    String cn = entries.nextElement();
                    if (!cn.startsWith("com.gwm.navi.")) continue;
                    String low = cn.toLowerCase(Locale.US);
                    if (!(low.contains("location") || low.contains("service") || low.contains("manager") ||
                            low.contains("wrapper") || low.contains("factory") || low.contains("sdk"))) continue;
                    if (++scanned > 4200) break;
                    try {
                        Class<?> c = Class.forName(cn, false, cl);
                        boolean interesting = false;
                        for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                            String n = m.getName().toLowerCase(Locale.US);
                            String rt = m.getReturnType().getName().toLowerCase(Locale.US);
                            if (n.contains("locationservice") || n.equals("getlocationservice") ||
                                    rt.contains("ilocationservice") || rt.endsWith("locationservice")) {
                                interesting = true;
                                break;
                            }
                        }
                        if (!interesting) {
                            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                                String ft = f.getType().getName().toLowerCase(Locale.US);
                                if (ft.contains("ilocationservice") || ft.endsWith("locationservice")) {
                                    interesting = true;
                                    break;
                                }
                            }
                        }
                        if (!interesting) continue;
                        candidateClasses++;
                        if (candidateClasses <= 18) appendOemLoc("candidate=" + cn);

                        // 1) Static fields that already hold ILocationService or a provider object.
                        for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                            if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                            String ft = f.getType().getName().toLowerCase(Locale.US);
                            if (!(ft.contains("locationservice") || ft.contains("locationmanager") ||
                                    ft.contains("locationprovider"))) continue;
                            try {
                                f.setAccessible(true);
                                Object v = f.get(null);
                                if (v != null) {
                                    appendOemLoc("staticField=" + cn + "." + f.getName() + " -> " + v.getClass().getName());
                                    if (probeLocationCarrier(v, cn + "." + f.getName())) return;
                                }
                            } catch (Throwable e) {
                                if (candidateClasses <= 8) appendOemLoc("field " + f.getName() + " ERROR=" + e.getClass().getSimpleName());
                            }
                        }

                        // 2) Static no-arg factories/singletons.
                        for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                            if (!java.lang.reflect.Modifier.isStatic(m.getModifiers()) || m.getParameterTypes().length != 0) continue;
                            String n = m.getName().toLowerCase(Locale.US);
                            String rt = m.getReturnType().getName().toLowerCase(Locale.US);
                            if (!(n.equals("getlocationservice") || n.equals("getinstance") || n.equals("getlocservice") ||
                                    rt.contains("ilocationservice") || rt.endsWith("locationservice"))) continue;
                            try {
                                m.setAccessible(true);
                                Object v = m.invoke(null);
                                if (v != null) {
                                    appendOemLoc("factory=" + cn + "." + m.getName() + " -> " + v.getClass().getName());
                                    if (probeLocationCarrier(v, cn + "." + m.getName() + "()")) return;
                                }
                            } catch (Throwable e) {
                                if (candidateClasses <= 8) appendOemLoc("factory " + m.getName() + " ERROR=" + rootCauseName(e));
                            }
                        }
                    } catch (Throwable ignored) { }
                }
                appendOemLoc("NO LIVE ILocationService • scanned=" + scanned + " candidates=" + candidateClasses);
            } catch (Throwable e) {
                appendOemLoc("SCAN ERROR=" + e.getClass().getSimpleName() + ":" + shortMessage(e));
            } finally {
                if (dex != null) try { dex.close(); } catch (Throwable ignored) { }
            }
        }, "H9-Internal-ILocationService").start();
    }

    private boolean probeLocationCarrier(Object carrier, String source) {
        if (carrier == null) return false;
        if (invokeDrInfo(carrier, source)) return true;

        // Look for an instance no-arg accessor returning the actual ILocationService.
        Class<?> c = carrier.getClass();
        for (java.lang.reflect.Method m : c.getMethods()) {
            if (m.getParameterTypes().length != 0 || java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
            String n = m.getName().toLowerCase(Locale.US);
            String rt = m.getReturnType().getName().toLowerCase(Locale.US);
            if (!(n.equals("getlocationservice") || n.equals("getlocservice") ||
                    rt.contains("ilocationservice") || rt.endsWith("locationservice"))) continue;
            try {
                m.setAccessible(true);
                Object v = m.invoke(carrier);
                if (v != null) {
                    appendOemLoc("accessor=" + source + "." + m.getName() + " -> " + v.getClass().getName());
                    if (invokeDrInfo(v, source + "." + m.getName())) return true;
                }
            } catch (Throwable e) {
                appendOemLoc("accessor " + m.getName() + " ERROR=" + rootCauseName(e));
            }
        }

        // Inspect one level of instance fields for a cached location-service proxy.
        for (java.lang.reflect.Field f : c.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
            String ft = f.getType().getName().toLowerCase(Locale.US);
            if (!(ft.contains("ilocationservice") || ft.endsWith("locationservice") || ft.contains("locationprovider"))) continue;
            try {
                f.setAccessible(true);
                Object v = f.get(carrier);
                if (v != null) {
                    appendOemLoc("instanceField=" + source + "." + f.getName() + " -> " + v.getClass().getName());
                    if (invokeDrInfo(v, source + "." + f.getName())) return true;
                }
            } catch (Throwable e) {
                appendOemLoc("instanceField " + f.getName() + " ERROR=" + rootCauseName(e));
            }
        }
        return false;
    }

    private String rootCauseName(Throwable e) {
        Throwable r = e;
        while (r.getCause() != null && r.getCause() != r) r = r.getCause();
        String msg = r.getMessage();
        if (msg == null) msg = "";
        if (msg.length() > 80) msg = msg.substring(0, 80) + "…";
        return r.getClass().getSimpleName() + (msg.isEmpty() ? "" : ":" + msg);
    }

'''
text = text.replace(marker, probe + marker)
text = text.replace('"2.8 TEST • BINDER: "', '"2.9 TEST • BINDER: "')
text = text.replace('"2.8 TEST • GWM: "', '"2.9 TEST • GWM: "')
text = text.replace('"2.8 TEST • LOC: "', '"2.9 TEST • LOC: "')
main.write_text(text, encoding='utf-8')

qv = Path('app/src/main/java/sa/h9/qibla/QiblaView.java')
q = qv.read_text(encoding='utf-8')
q = q.replace('2.8 TEST', '2.9 TEST').replace('2.7 TEST', '2.9 TEST').replace('2.6 TEST', '2.9 TEST').replace('1.9 TEST', '2.9 TEST')
qv.write_text(q, encoding='utf-8')

gradle = Path('app/build.gradle')
g = gradle.read_text(encoding='utf-8')
g = re.sub(r'versionCode\s+\d+', 'versionCode 29', g)
g = re.sub(r"versionName\s+'[^']+'", "versionName '2.9-test'", g)
gradle.write_text(g, encoding='utf-8')

print('H9 Qibla 2.9 internal ILocationService provider probe applied')
