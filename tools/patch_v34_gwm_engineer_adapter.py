from pathlib import Path
import re

main = Path('app/src/main/java/sa/h9/qibla/MainActivity.java')
text = main.read_text(encoding='utf-8')
text = text.replace('3.3 TEST', '3.4 TEST')
text = text.replace(
'''            updateBinderDiagnosticText("CONNECTED • MapControl=" + descriptor + " • probing ServiceManagerFactory init/dependency...");
            probeServiceManagerInitAndLocation();''',
'''            updateBinderDiagnosticText("CONNECTED • MapControl=" + descriptor + " • probing GWMNaviEngineer Adapter...");
            probeGwmEngineerAdapter();''')

marker = '    private void probeServiceManagerInitAndLocation() {'
if marker not in text:
    raise SystemExit('3.3 probe marker not found')

probe = r'''    private void probeGwmEngineerAdapter() {
        synchronized (oemLocLog) {
            oemLocLog.setLength(0);
            oemLocLog.append("GWMNaviEngineer • GwmAdapterClient PROBE\n");
        }
        new Thread(() -> {
            try {
                Context engCtx = createPackageContext("com.gwm.naviengineer",
                        Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
                ClassLoader cl = engCtx.getClassLoader();
                Class<?> clientClass = Class.forName("com.gwm.android.adapter.client.GwmAdapterClient", false, cl);
                appendOemLoc("pkg=com.gwm.naviengineer");
                appendOemLoc("class=" + clientClass.getName());

                for (java.lang.reflect.Constructor<?> c : clientClass.getDeclaredConstructors()) {
                    StringBuilder b = new StringBuilder("ctor(");
                    Class<?>[] ps = c.getParameterTypes();
                    for (int i=0;i<ps.length;i++) { if(i>0)b.append(','); b.append(ps[i].getName()); }
                    b.append(')');
                    appendOemLoc(b.toString());
                }
                for (java.lang.reflect.Method m : clientClass.getDeclaredMethods()) {
                    String n = m.getName();
                    if (n.toLowerCase(Locale.US).contains("data") || n.toLowerCase(Locale.US).contains("instance")) {
                        StringBuilder b = new StringBuilder("method=");
                        b.append(java.lang.reflect.Modifier.toString(m.getModifiers())).append(' ')
                         .append(n).append('(');
                        Class<?>[] ps = m.getParameterTypes();
                        for (int i=0;i<ps.length;i++) { if(i>0)b.append(','); b.append(ps[i].getName()); }
                        b.append(")->").append(m.getReturnType().getName());
                        appendOemLoc(b.toString());
                    }
                }

                Object client = null;
                for (java.lang.reflect.Field f : clientClass.getDeclaredFields()) {
                    if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    try {
                        f.setAccessible(true);
                        Object v = f.get(null);
                        if (v != null && clientClass.isInstance(v)) {
                            client = v;
                            appendOemLoc("instance=static field " + f.getName());
                            break;
                        }
                    } catch (Throwable ignored) { }
                }

                if (client == null) {
                    for (java.lang.reflect.Method m : clientClass.getDeclaredMethods()) {
                        if (!java.lang.reflect.Modifier.isStatic(m.getModifiers()) || m.getParameterTypes().length != 0) continue;
                        if (!clientClass.isAssignableFrom(m.getReturnType())) continue;
                        try {
                            m.setAccessible(true);
                            Object v = m.invoke(null);
                            appendOemLoc("instanceCall=" + m.getName() + " -> " + (v == null ? "NULL" : v.getClass().getName()));
                            if (v != null) { client = v; break; }
                        } catch (Throwable e) {
                            appendOemLoc("instanceCall " + m.getName() + " ERROR=" + rootCauseName(e));
                        }
                    }
                }

                if (client == null) {
                    for (java.lang.reflect.Constructor<?> c : clientClass.getDeclaredConstructors()) {
                        Class<?>[] ps = c.getParameterTypes();
                        try {
                            c.setAccessible(true);
                            Object v = null;
                            if (ps.length == 0) v = c.newInstance();
                            else if (ps.length == 1 && Context.class.isAssignableFrom(ps[0])) v = c.newInstance(engCtx);
                            if (v != null) {
                                client = v;
                                appendOemLoc("instance=constructor(" + ps.length + ")");
                                break;
                            }
                        } catch (Throwable e) {
                            if (ps.length <= 1) appendOemLoc("ctor ERROR=" + rootCauseName(e));
                        }
                    }
                }

                java.lang.reflect.Method getData = null;
                for (java.lang.reflect.Method m : clientClass.getMethods()) {
                    if (!"getData".equals(m.getName())) continue;
                    Class<?>[] ps = m.getParameterTypes();
                    if (ps.length == 1 && ps[0] == String.class) { getData = m; break; }
                }
                if (getData == null) {
                    for (java.lang.reflect.Method m : clientClass.getDeclaredMethods()) {
                        if (!"getData".equals(m.getName())) continue;
                        Class<?>[] ps = m.getParameterTypes();
                        if (ps.length == 1 && ps[0] == String.class) { getData = m; break; }
                    }
                }
                if (getData == null) {
                    appendOemLoc("RESULT: getData(String) NOT FOUND");
                    return;
                }
                getData.setAccessible(true);
                boolean isStatic = java.lang.reflect.Modifier.isStatic(getData.getModifiers());
                appendOemLoc("getData(String) static=" + isStatic + " return=" + getData.getReturnType().getName());
                if (!isStatic && client == null) {
                    appendOemLoc("RESULT: client instance NULL");
                    return;
                }

                String[] keys = new String[]{
                        "car.basic.gear_status",
                        "car.basic.speed",
                        "car.basic.vehicle_speed",
                        "car.vehicle.speed",
                        "car.basic.yaw_rate",
                        "car.vehicle.yaw_rate",
                        "car.basic.heading",
                        "car.vehicle.heading",
                        "car.basic.azimuth",
                        "car.vehicle.azimuth",
                        "car.basic.bearing",
                        "car.vehicle.bearing",
                        "vehicle.speed",
                        "vehicle.yaw_rate",
                        "vehicle.heading",
                        "vehicle.azimuth"
                };
                Object target = isStatic ? null : client;
                int useful = 0;
                for (String key : keys) {
                    try {
                        Object v = getData.invoke(target, key);
                        String out = String.valueOf(v);
                        appendOemLoc(key + "=" + out);
                        if (v != null && !"null".equalsIgnoreCase(out) && !"".equals(out)) useful++;
                    } catch (Throwable e) {
                        appendOemLoc(key + " ERROR=" + rootCauseName(e));
                    }
                }
                appendOemLoc("RESULT: useful=" + useful + "/" + keys.length);
            } catch (Throwable e) {
                appendOemLoc("3.4 ENGINEER ERROR=" + e.getClass().getSimpleName() + ":" + shortMessage(e));
            }
        }, "H9-GwmEngineerAdapter").start();
    }

'''
text = text.replace(marker, probe + marker)
text = text.replace('"3.3 TEST • BINDER: "', '"3.4 TEST • BINDER: "')
text = text.replace('"3.3 TEST • GWM: "', '"3.4 TEST • GWM: "')
text = text.replace('"3.3 TEST • LOC: "', '"3.4 TEST • LOC: "')
main.write_text(text, encoding='utf-8')

qv = Path('app/src/main/java/sa/h9/qibla/QiblaView.java')
q = qv.read_text(encoding='utf-8')
for old in ['3.3 TEST','3.2 TEST','3.1 TEST','3.0 TEST','2.9 TEST','2.8 TEST','2.7 TEST','2.6 TEST','1.9 TEST']:
    q = q.replace(old, '3.4 TEST')
qv.write_text(q, encoding='utf-8')

gradle = Path('app/build.gradle')
g = gradle.read_text(encoding='utf-8')
g = re.sub(r'versionCode\s+\d+', 'versionCode 34', g)
g = re.sub(r"versionName\s+'[^']+'", "versionName '3.4-test'", g)
gradle.write_text(g, encoding='utf-8')
print('H9 Qibla 3.4 GWMNaviEngineer GwmAdapterClient probe applied')
