from pathlib import Path
import re

main = Path('app/src/main/java/sa/h9/qibla/MainActivity.java')
text = main.read_text(encoding='utf-8')
text = text.replace('3.5 TEST', '3.6 TEST')

start = text.index('    private void probeGwmEngineerAdapter() {')
end = text.index('    private void probeServiceManagerInitAndLocation() {', start)

probe = r'''    private void probeGwmEngineerAdapter() {
        synchronized (oemLocLog) {
            oemLocLog.setLength(0);
            oemLocLog.append("GWMNaviEngineer • GwmCarSettingManager PROBE\n");
        }
        new Thread(() -> {
            try {
                Context engCtx = createPackageContext("com.gwm.naviengineer",
                        Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
                ClassLoader cl = engCtx.getClassLoader();

                Class<?> carClass = Class.forName("android.car.Car", false, cl);
                Class<?> mgrClass = Class.forName("android.car.hardware.setting.GwmCarSettingManager", false, cl);
                appendOemLoc("carClass=" + carClass.getName());
                appendOemLoc("mgrClass=" + mgrClass.getName());

                // Dump useful service/setting constants only.
                for (java.lang.reflect.Field f : mgrClass.getDeclaredFields()) {
                    String n = f.getName().toLowerCase(Locale.US);
                    if (!(n.contains("speed") || n.contains("gear") || n.contains("yaw") ||
                            n.contains("heading") || n.contains("azimuth") || n.contains("bearing") ||
                            n.contains("service"))) continue;
                    try {
                        if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                        f.setAccessible(true);
                        appendOemLoc("CONST " + f.getName() + "=" + String.valueOf(f.get(null)));
                    } catch (Throwable e) {
                        appendOemLoc("CONST " + f.getName() + " ERROR=" + rootCauseName(e));
                    }
                }

                // Dump only methods relevant to vehicle movement/orientation.
                for (java.lang.reflect.Method m : mgrClass.getMethods()) {
                    String n = m.getName().toLowerCase(Locale.US);
                    if (n.contains("speed") || n.contains("gear") || n.contains("yaw") ||
                            n.contains("heading") || n.contains("azimuth") || n.contains("bearing") ||
                            n.contains("vehicle") || n.contains("angle")) {
                        StringBuilder b = new StringBuilder("M ");
                        b.append(m.getName()).append('(');
                        Class<?>[] ps = m.getParameterTypes();
                        for (int i=0;i<ps.length;i++) { if(i>0)b.append(','); b.append(ps[i].getSimpleName()); }
                        b.append(")->").append(m.getReturnType().getSimpleName());
                        appendOemLoc(b.toString());
                    }
                }

                Object car = null;
                try {
                    java.lang.reflect.Method create = carClass.getMethod("createCar", Context.class);
                    car = create.invoke(null, this);
                    appendOemLoc("Car.createCar=" + (car == null ? "NULL" : car.getClass().getName()));
                } catch (Throwable e) {
                    appendOemLoc("createCar(Context) ERROR=" + rootCauseName(e));
                }

                if (car == null) {
                    for (java.lang.reflect.Method m : carClass.getMethods()) {
                        if (!"createCar".equals(m.getName()) || !java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
                        Class<?>[] ps = m.getParameterTypes();
                        if (ps.length < 1 || !Context.class.isAssignableFrom(ps[0])) continue;
                        try {
                            Object[] args = new Object[ps.length];
                            args[0] = this;
                            for (int i=1;i<ps.length;i++) {
                                Class<?> p = ps[i];
                                if (p == android.os.Handler.class) args[i] = new android.os.Handler(android.os.Looper.getMainLooper());
                                else if (p == long.class || p == Long.class) args[i] = 0L;
                                else if (p == int.class || p == Integer.class) args[i] = 0;
                                else args[i] = null;
                            }
                            m.setAccessible(true);
                            Object v = m.invoke(null, args);
                            appendOemLoc("createCar/" + ps.length + "=" + (v == null ? "NULL" : v.getClass().getName()));
                            if (v != null) { car = v; break; }
                        } catch (Throwable e) {
                            appendOemLoc("createCar/" + ps.length + " ERROR=" + rootCauseName(e));
                        }
                    }
                }

                if (car == null) {
                    appendOemLoc("RESULT: android.car.Car instance NULL");
                    return;
                }

                try {
                    java.lang.reflect.Method connect = carClass.getMethod("connect");
                    connect.invoke(car);
                    appendOemLoc("Car.connect=OK");
                } catch (Throwable e) {
                    appendOemLoc("Car.connect=" + rootCauseName(e));
                }

                java.lang.reflect.Method getMgr = carClass.getMethod("getCarManager", String.class);
                java.util.LinkedHashSet<String> serviceNames = new java.util.LinkedHashSet<>();
                serviceNames.add("setting");
                serviceNames.add("gwm_setting");
                serviceNames.add("car_setting");
                serviceNames.add("gwm_car_setting");

                for (java.lang.reflect.Field f : carClass.getDeclaredFields()) {
                    String n = f.getName().toLowerCase(Locale.US);
                    if (!(n.contains("setting") || n.contains("service"))) continue;
                    try {
                        if (!java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.getType() != String.class) continue;
                        f.setAccessible(true);
                        Object v = f.get(null);
                        if (v != null) {
                            String s = String.valueOf(v);
                            if (s.toLowerCase(Locale.US).contains("setting")) {
                                serviceNames.add(s);
                                appendOemLoc("CarConst " + f.getName() + "=" + s);
                            }
                        }
                    } catch (Throwable ignored) { }
                }

                Object manager = null;
                for (String svc : serviceNames) {
                    try {
                        Object v = getMgr.invoke(car, svc);
                        appendOemLoc("getCarManager(" + svc + ")=" + (v == null ? "NULL" : v.getClass().getName()));
                        if (v != null && mgrClass.isInstance(v)) { manager = v; break; }
                    } catch (Throwable e) {
                        appendOemLoc("mgr(" + svc + ") ERROR=" + rootCauseName(e));
                    }
                }

                if (manager == null) {
                    appendOemLoc("RESULT: GwmCarSettingManager not resolved");
                    return;
                }

                int useful = 0;
                for (java.lang.reflect.Method m : mgrClass.getMethods()) {
                    if (m.getParameterTypes().length != 0) continue;
                    String n = m.getName().toLowerCase(Locale.US);
                    if (!(n.contains("speed") || n.contains("gear") || n.contains("yaw") ||
                            n.contains("heading") || n.contains("azimuth") || n.contains("bearing") ||
                            n.contains("angle"))) continue;
                    try {
                        m.setAccessible(true);
                        Object v = m.invoke(manager);
                        appendOemLoc("CALL " + m.getName() + "=" + String.valueOf(v));
                        if (v != null) useful++;
                    } catch (Throwable e) {
                        appendOemLoc("CALL " + m.getName() + " ERROR=" + rootCauseName(e));
                    }
                }
                appendOemLoc("RESULT usefulCalls=" + useful);
            } catch (Throwable e) {
                appendOemLoc("3.6 CAR ERROR=" + e.getClass().getSimpleName() + ":" + shortMessage(e));
            }
        }, "H9-GwmCarSetting36").start();
    }

'''
text = text[:start] + probe + text[end:]
text = text.replace('"3.5 TEST • BINDER: "', '"3.6 TEST • BINDER: "')
text = text.replace('"3.5 TEST • GWM: "', '"3.6 TEST • GWM: "')
text = text.replace('"3.5 TEST • LOC: "', '"3.6 TEST • LOC: "')
main.write_text(text, encoding='utf-8')

qv = Path('app/src/main/java/sa/h9/qibla/QiblaView.java')
q = qv.read_text(encoding='utf-8').replace('3.5 TEST', '3.6 TEST')
qv.write_text(q, encoding='utf-8')

gradle = Path('app/build.gradle')
g = gradle.read_text(encoding='utf-8')
g = re.sub(r'versionCode\s+\d+', 'versionCode 36', g)
g = re.sub(r"versionName\s+'[^']+'", "versionName '3.6-test'", g)
gradle.write_text(g, encoding='utf-8')
print('H9 Qibla 3.6 GwmCarSettingManager probe applied')
