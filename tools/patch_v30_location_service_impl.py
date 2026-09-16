from pathlib import Path
import re

main = Path('app/src/main/java/sa/h9/qibla/MainActivity.java')
text = main.read_text(encoding='utf-8')
text = text.replace('2.9 TEST', '3.0 TEST')
text = text.replace('            updateBinderDiagnosticText("CONNECTED • MapControl=" + descriptor + " • scanning internal ILocationService provider...");\n            probeInternalGwmLocationService();',
'''            updateBinderDiagnosticText("CONNECTED • MapControl=" + descriptor + " • probing LocationServiceImpl directly...");
            probeLocationServiceImplDirect();''')

marker = '    private void probeInternalGwmLocationService() {'
if marker not in text:
    raise SystemExit('probeInternalGwmLocationService marker not found')

probe = r'''    private void probeLocationServiceImplDirect() {
        synchronized (oemLocLog) {
            oemLocLog.setLength(0);
            oemLocLog.append("DIRECT LocationServiceImpl PROBE\n");
        }
        new Thread(() -> {
            try {
                Context navCtx = createPackageContext("com.gwm.navi.app",
                        Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
                ClassLoader cl = navCtx.getClassLoader();
                String targetName = "com.gwm.navi.wrapper.neusoft.location.LocationServiceImpl";
                Class<?> target = Class.forName(targetName, false, cl);
                appendOemLoc("target=" + target.getName());

                try {
                    Class<?> contract = Class.forName("com.gwm.navi.service.location.ILocationService", false, cl);
                    appendOemLoc("contract=" + contract.getName());
                } catch (Throwable e) {
                    appendOemLoc("contract ERROR=" + rootCauseName(e));
                }

                // Report useful methods first so we learn the real API surface on the car.
                int shown = 0;
                for (java.lang.reflect.Method m : target.getDeclaredMethods()) {
                    String n = m.getName().toLowerCase(Locale.US);
                    String rt = m.getReturnType().getName().toLowerCase(Locale.US);
                    if (n.contains("instance") || n.contains("location") || n.contains("service") || n.contains("dr") ||
                            rt.contains("location") || rt.contains("service")) {
                        appendOemLoc("method=" + java.lang.reflect.Modifier.toString(m.getModifiers()) + " " +
                                m.getName() + "(" + m.getParameterTypes().length + ")->" + m.getReturnType().getName());
                        if (++shown >= 14) break;
                    }
                }

                // 1) Static fields, especially singleton/service fields.
                for (java.lang.reflect.Field f : target.getDeclaredFields()) {
                    if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    try {
                        f.setAccessible(true);
                        Object v = f.get(null);
                        appendOemLoc("static=" + f.getName() + ":" + f.getType().getName() + " -> " +
                                (v == null ? "null" : v.getClass().getName()));
                        if (v != null && probeLocationCarrier(v, targetName + "." + f.getName())) return;
                    } catch (Throwable e) {
                        appendOemLoc("static " + f.getName() + " ERROR=" + rootCauseName(e));
                    }
                }

                // 2) All plausible static no-arg factories, not just exact getInstance names.
                for (java.lang.reflect.Method m : target.getDeclaredMethods()) {
                    if (!java.lang.reflect.Modifier.isStatic(m.getModifiers()) || m.getParameterTypes().length != 0) continue;
                    String n = m.getName().toLowerCase(Locale.US);
                    String rt = m.getReturnType().getName().toLowerCase(Locale.US);
                    if (!(n.contains("instance") || n.contains("service") || n.contains("location") || n.startsWith("get") ||
                            rt.contains("location") || rt.contains("service") || rt.contains("wrapper"))) continue;
                    try {
                        m.setAccessible(true);
                        Object v = m.invoke(null);
                        appendOemLoc("staticCall=" + m.getName() + " -> " + (v == null ? "null" : v.getClass().getName()));
                        if (v != null && probeLocationCarrier(v, targetName + "." + m.getName() + "()")) return;
                    } catch (Throwable e) {
                        appendOemLoc("staticCall " + m.getName() + " ERROR=" + rootCauseName(e));
                    }
                }

                // 3) Try a no-arg constructor if OEM left one available, then inspect the created carrier.
                boolean ctorTried = false;
                for (java.lang.reflect.Constructor<?> ctor : target.getDeclaredConstructors()) {
                    appendOemLoc("ctor params=" + ctor.getParameterTypes().length + " mods=" +
                            java.lang.reflect.Modifier.toString(ctor.getModifiers()));
                    if (ctor.getParameterTypes().length != 0 || ctorTried) continue;
                    ctorTried = true;
                    try {
                        ctor.setAccessible(true);
                        Object obj = ctor.newInstance();
                        appendOemLoc("ctorInstance=" + (obj == null ? "null" : obj.getClass().getName()));
                        if (obj != null && probeLocationCarrier(obj, targetName + ".<init>")) return;
                    } catch (Throwable e) {
                        appendOemLoc("ctor ERROR=" + rootCauseName(e));
                    }
                }

                // 4) Focused secondary candidates discovered by 2.9.
                String[] extra = new String[]{
                        "com.gwm.navi.service.manager.ServiceManagerFactory",
                        "com.gwm.navi.wrapper.neusoft.eVServiceImpl",
                        "com.gwm.navi.wrapper.neusoft.guide.GuideServiceImpl"
                };
                for (String cn : extra) {
                    try {
                        Class<?> c = Class.forName(cn, false, cl);
                        appendOemLoc("secondary=" + cn);
                        for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                            if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                            try {
                                f.setAccessible(true);
                                Object v = f.get(null);
                                if (v != null && probeLocationCarrier(v, cn + "." + f.getName())) return;
                            } catch (Throwable ignored) { }
                        }
                        for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                            if (!java.lang.reflect.Modifier.isStatic(m.getModifiers()) || m.getParameterTypes().length != 0) continue;
                            String n = m.getName().toLowerCase(Locale.US);
                            if (!(n.contains("instance") || n.contains("location") || n.contains("service") || n.startsWith("get"))) continue;
                            try {
                                m.setAccessible(true);
                                Object v = m.invoke(null);
                                if (v != null && probeLocationCarrier(v, cn + "." + m.getName() + "()")) return;
                            } catch (Throwable ignored) { }
                        }
                    } catch (Throwable e) {
                        appendOemLoc("secondary " + cn + " ERROR=" + rootCauseName(e));
                    }
                }

                appendOemLoc("NO DR INSTANCE • focused probe complete");
            } catch (Throwable e) {
                appendOemLoc("DIRECT PROBE ERROR=" + e.getClass().getSimpleName() + ":" + shortMessage(e));
            }
        }, "H9-LocationServiceImpl-Direct").start();
    }

'''
text = text.replace(marker, probe + marker)
text = text.replace('"2.9 TEST • BINDER: "', '"3.0 TEST • BINDER: "')
text = text.replace('"2.9 TEST • GWM: "', '"3.0 TEST • GWM: "')
text = text.replace('"2.9 TEST • LOC: "', '"3.0 TEST • LOC: "')
main.write_text(text, encoding='utf-8')

qv = Path('app/src/main/java/sa/h9/qibla/QiblaView.java')
q = qv.read_text(encoding='utf-8')
for old in ['2.9 TEST','2.8 TEST','2.7 TEST','2.6 TEST','1.9 TEST']:
    q = q.replace(old, '3.0 TEST')
qv.write_text(q, encoding='utf-8')

gradle = Path('app/build.gradle')
g = gradle.read_text(encoding='utf-8')
g = re.sub(r'versionCode\s+\d+', 'versionCode 30', g)
g = re.sub(r"versionName\s+'[^']+'", "versionName '3.0-test'", g)
gradle.write_text(g, encoding='utf-8')

print('H9 Qibla 3.0 focused LocationServiceImpl probe applied')
