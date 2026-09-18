from pathlib import Path
import re

main = Path('app/src/main/java/sa/h9/qibla/MainActivity.java')
text = main.read_text(encoding='utf-8')
text = text.replace('3.2 TEST', '3.3 TEST')
text = text.replace(
'''            updateBinderDiagnosticText("CONNECTED • MapControl=" + descriptor + " • direct getLocationService()...");
            probeDirectGetLocationService();''',
'''            updateBinderDiagnosticText("CONNECTED • MapControl=" + descriptor + " • probing ServiceManagerFactory init/dependency...");
            probeServiceManagerInitAndLocation();''')

marker = '    private void probeDirectGetLocationService() {'
if marker not in text:
    raise SystemExit('probeDirectGetLocationService marker not found')

probe = r'''    private void probeServiceManagerInitAndLocation() {
        synchronized (oemLocLog) {
            oemLocLog.setLength(0);
            oemLocLog.append("ServiceManagerFactory INIT/DEPENDENCY PROBE\n");
        }
        new Thread(() -> {
            try {
                Context navCtx = createPackageContext("com.gwm.navi.app",
                        Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
                ClassLoader cl = navCtx.getClassLoader();
                Class<?> factory = Class.forName("com.gwm.navi.service.manager.ServiceManagerFactory", false, cl);
                Class<?> contract = Class.forName("com.gwm.navi.service.location.ILocationService", false, cl);
                appendOemLoc("factory=" + factory.getName());
                appendOemLoc("contract=" + contract.getName());

                // Show factory state and any init/register/set entry points.
                for (java.lang.reflect.Method m : factory.getDeclaredMethods()) {
                    String n = m.getName().toLowerCase(Locale.US);
                    if (n.contains("init") || n.contains("depend") || n.contains("register") ||
                            n.startsWith("set") || n.contains("location")) {
                        appendOemLoc("factoryMethod=" + java.lang.reflect.Modifier.toString(m.getModifiers()) + " " +
                                m.getName() + "(" + m.getParameterTypes().length + ")->" + m.getReturnType().getName());
                    }
                }
                for (java.lang.reflect.Field f : factory.getDeclaredFields()) {
                    if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    try {
                        f.setAccessible(true);
                        Object v = f.get(null);
                        appendOemLoc("factoryField=" + f.getName() + ":" + f.getType().getName() + "=" +
                                (v == null ? "null" : v.getClass().getName()));
                    } catch (Throwable e) {
                        appendOemLoc("factoryField " + f.getName() + " ERROR=" + rootCauseName(e));
                    }
                }

                Object dependency = null;
                try {
                    java.lang.reflect.Method m = factory.getDeclaredMethod("getDependencyService");
                    m.setAccessible(true);
                    dependency = m.invoke(null);
                    appendOemLoc("getDependencyService=" + (dependency == null ? "NULL" : dependency.getClass().getName()));
                } catch (Throwable e) {
                    appendOemLoc("getDependencyService ERROR=" + rootCauseName(e));
                }

                if (dependency != null) {
                    Class<?> dc = dependency.getClass();
                    int shown = 0;
                    for (java.lang.reflect.Method m : dc.getMethods()) {
                        String n = m.getName().toLowerCase(Locale.US);
                        String rt = m.getReturnType().getName().toLowerCase(Locale.US);
                        if (n.contains("init") || n.contains("register") || n.contains("service") ||
                                n.contains("location") || n.startsWith("get") || rt.contains("locationservice")) {
                            appendOemLoc("depMethod=" + m.getName() + "(" + m.getParameterTypes().length + ")->" +
                                    m.getReturnType().getName());
                            if (++shown >= 18) break;
                        }
                    }

                    // Read cached service fields from the dependency object.
                    for (java.lang.reflect.Field f : dc.getDeclaredFields()) {
                        String ft = f.getType().getName().toLowerCase(Locale.US);
                        String fn = f.getName().toLowerCase(Locale.US);
                        if (!(ft.contains("location") || ft.contains("service") || fn.contains("location") || fn.contains("service"))) continue;
                        try {
                            f.setAccessible(true);
                            Object v = f.get(dependency);
                            appendOemLoc("depField=" + f.getName() + ":" + f.getType().getName() + "=" +
                                    (v == null ? "null" : v.getClass().getName()));
                            if (v != null && invokeDrInfo(v, "depField/" + f.getName())) return;
                        } catch (Throwable e) {
                            appendOemLoc("depField " + f.getName() + " ERROR=" + rootCauseName(e));
                        }
                    }

                    // Only invoke no-arg accessors that look like getters. Avoid mutating init/register calls.
                    for (java.lang.reflect.Method m : dc.getMethods()) {
                        if (m.getParameterTypes().length != 0) continue;
                        String n = m.getName().toLowerCase(Locale.US);
                        String rt = m.getReturnType().getName().toLowerCase(Locale.US);
                        if (!(n.equals("getlocationservice") || n.equals("getlocservice") ||
                                rt.contains("ilocationservice") || rt.endsWith("locationservice"))) continue;
                        try {
                            m.setAccessible(true);
                            Object v = m.invoke(dependency);
                            appendOemLoc("depCall=" + m.getName() + " -> " +
                                    (v == null ? "NULL" : v.getClass().getName()));
                            if (v != null && invokeDrInfo(v, "dependency/" + m.getName())) return;
                        } catch (Throwable e) {
                            appendOemLoc("depCall " + m.getName() + " ERROR=" + rootCauseName(e));
                        }
                    }
                }

                // Retry the exact factory accessor after dependency inspection.
                try {
                    java.lang.reflect.Method getLocationService = factory.getDeclaredMethod("getLocationService");
                    getLocationService.setAccessible(true);
                    Object service = getLocationService.invoke(null);
                    appendOemLoc("RETRY getLocationService=" + (service == null ? "NULL" : service.getClass().getName()));
                    if (service != null) {
                        if (invokeDrInfo(service, "factoryRetry")) return;
                        appendInterestingMethods(service.getClass(), "factoryRetry");
                    }
                } catch (Throwable e) {
                    appendOemLoc("RETRY getLocationService ERROR=" + rootCauseName(e));
                }

                appendOemLoc("RESULT: factory/dependency not initialized in our process");
            } catch (Throwable e) {
                appendOemLoc("DIRECT 3.3 ERROR=" + e.getClass().getSimpleName() + ":" + shortMessage(e));
            }
        }, "H9-ServiceFactory-Init").start();
    }

'''
text = text.replace(marker, probe + marker)
text = text.replace('"3.2 TEST • BINDER: "', '"3.3 TEST • BINDER: "')
text = text.replace('"3.2 TEST • GWM: "', '"3.3 TEST • GWM: "')
text = text.replace('"3.2 TEST • LOC: "', '"3.3 TEST • LOC: "')
main.write_text(text, encoding='utf-8')

qv = Path('app/src/main/java/sa/h9/qibla/QiblaView.java')
q = qv.read_text(encoding='utf-8')
for old in ['3.2 TEST','3.1 TEST','3.0 TEST','2.9 TEST','2.8 TEST','2.7 TEST','2.6 TEST','1.9 TEST']:
    q = q.replace(old, '3.3 TEST')
qv.write_text(q, encoding='utf-8')

gradle = Path('app/build.gradle')
g = gradle.read_text(encoding='utf-8')
g = re.sub(r'versionCode\s+\d+', 'versionCode 33', g)
g = re.sub(r"versionName\s+'[^']+'", "versionName '3.3-test'", g)
gradle.write_text(g, encoding='utf-8')
print('H9 Qibla 3.3 ServiceManagerFactory init/dependency probe applied')
