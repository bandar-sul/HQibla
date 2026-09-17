from pathlib import Path
import re

main = Path('app/src/main/java/sa/h9/qibla/MainActivity.java')
text = main.read_text(encoding='utf-8')
text = text.replace('3.0 TEST', '3.1 TEST')
text = text.replace('            updateBinderDiagnosticText("CONNECTED • MapControl=" + descriptor + " • probing LocationServiceImpl directly...");\n            probeLocationServiceImplDirect();',
'''            updateBinderDiagnosticText("CONNECTED • MapControl=" + descriptor + " • probing ServiceManagerFactory...");
            probeServiceManagerFactoryDirect();''')

marker = '    private void probeLocationServiceImplDirect() {'
if marker not in text:
    raise SystemExit('probeLocationServiceImplDirect marker not found')

probe = r'''    private void probeServiceManagerFactoryDirect() {
        synchronized (oemLocLog) {
            oemLocLog.setLength(0);
            oemLocLog.append("DIRECT ServiceManagerFactory PROBE\n");
        }
        new Thread(() -> {
            try {
                Context navCtx = createPackageContext("com.gwm.navi.app",
                        Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
                ClassLoader cl = navCtx.getClassLoader();
                Class<?> contract = Class.forName("com.gwm.navi.service.location.ILocationService", false, cl);
                String targetName = "com.gwm.navi.service.manager.ServiceManagerFactory";
                Class<?> target = Class.forName(targetName, false, cl);
                appendOemLoc("target=" + target.getName());
                appendOemLoc("contract=" + contract.getName());

                int shown = 0;
                for (java.lang.reflect.Method m : target.getDeclaredMethods()) {
                    StringBuilder sig = new StringBuilder();
                    sig.append("method=").append(java.lang.reflect.Modifier.toString(m.getModifiers())).append(' ')
                            .append(m.getName()).append('(');
                    Class<?>[] ps = m.getParameterTypes();
                    for (int i = 0; i < ps.length; i++) {
                        if (i > 0) sig.append(',');
                        sig.append(ps[i].getName());
                    }
                    sig.append(")->").append(m.getReturnType().getName());
                    appendOemLoc(sig.toString());
                    if (++shown >= 22) break;
                }

                // Static cached objects/singletons.
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

                // Invoke safe factory forms. Support no-arg, Class, and enum selectors.
                for (java.lang.reflect.Method m : target.getDeclaredMethods()) {
                    if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
                    Class<?>[] ps = m.getParameterTypes();
                    Object[] args = null;
                    String selector = "";
                    if (ps.length == 0) {
                        args = new Object[0];
                    } else if (ps.length == 1 && ps[0] == Class.class) {
                        args = new Object[]{contract};
                        selector = "[ILocationService.class]";
                    } else if (ps.length == 1 && ps[0].isEnum()) {
                        Object[] constants = ps[0].getEnumConstants();
                        if (constants != null) {
                            for (Object c : constants) {
                                String n = String.valueOf(c).toLowerCase(Locale.US);
                                if (n.contains("loc") || n.contains("position") || n.contains("navi")) {
                                    try {
                                        m.setAccessible(true);
                                        Object v = m.invoke(null, c);
                                        appendOemLoc("call=" + m.getName() + "[" + c + "] -> " +
                                                (v == null ? "null" : v.getClass().getName()));
                                        if (v != null && probeLocationCarrier(v, targetName + "." + m.getName() + "[" + c + "]")) return;
                                    } catch (Throwable e) {
                                        appendOemLoc("call " + m.getName() + "[" + c + "] ERROR=" + rootCauseName(e));
                                    }
                                }
                            }
                        }
                        continue;
                    } else {
                        continue;
                    }
                    try {
                        m.setAccessible(true);
                        Object v = m.invoke(null, args);
                        appendOemLoc("call=" + m.getName() + selector + " -> " +
                                (v == null ? "null" : v.getClass().getName()));
                        if (v != null && probeLocationCarrier(v, targetName + "." + m.getName() + selector)) return;
                    } catch (Throwable e) {
                        appendOemLoc("call " + m.getName() + selector + " ERROR=" + rootCauseName(e));
                    }
                }

                // Constructors are diagnostic only; instantiate a no-arg one if present.
                for (java.lang.reflect.Constructor<?> ctor : target.getDeclaredConstructors()) {
                    appendOemLoc("ctor params=" + ctor.getParameterTypes().length + " mods=" +
                            java.lang.reflect.Modifier.toString(ctor.getModifiers()));
                    if (ctor.getParameterTypes().length == 0) {
                        try {
                            ctor.setAccessible(true);
                            Object v = ctor.newInstance();
                            appendOemLoc("ctorInstance=" + (v == null ? "null" : v.getClass().getName()));
                            if (v != null && probeLocationCarrier(v, targetName + ".<init>")) return;
                        } catch (Throwable e) {
                            appendOemLoc("ctor ERROR=" + rootCauseName(e));
                        }
                    }
                }

                appendOemLoc("NO ILocationService • ServiceManagerFactory probe complete");
            } catch (Throwable e) {
                appendOemLoc("FACTORY PROBE ERROR=" + e.getClass().getSimpleName() + ":" + shortMessage(e));
            }
        }, "H9-ServiceManagerFactory").start();
    }

'''
text = text.replace(marker, probe + marker)
text = text.replace('"3.0 TEST • BINDER: "', '"3.1 TEST • BINDER: "')
text = text.replace('"3.0 TEST • GWM: "', '"3.1 TEST • GWM: "')
text = text.replace('"3.0 TEST • LOC: "', '"3.1 TEST • LOC: "')
main.write_text(text, encoding='utf-8')

qv = Path('app/src/main/java/sa/h9/qibla/QiblaView.java')
q = qv.read_text(encoding='utf-8')
for old in ['3.0 TEST','2.9 TEST','2.8 TEST','2.7 TEST','2.6 TEST','1.9 TEST']:
    q = q.replace(old, '3.1 TEST')
qv.write_text(q, encoding='utf-8')

gradle = Path('app/build.gradle')
g = gradle.read_text(encoding='utf-8')
g = re.sub(r'versionCode\s+\d+', 'versionCode 31', g)
g = re.sub(r"versionName\s+'[^']+'", "versionName '3.1-test'", g)
gradle.write_text(g, encoding='utf-8')

print('H9 Qibla 3.1 ServiceManagerFactory probe applied')
