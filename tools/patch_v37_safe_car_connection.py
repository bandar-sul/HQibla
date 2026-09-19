from pathlib import Path
import re

main = Path('app/src/main/java/sa/h9/qibla/MainActivity.java')
text = main.read_text(encoding='utf-8')
text = text.replace('3.6 TEST', '3.7 TEST')

start = text.index('    private void probeGwmEngineerAdapter() {')
end = text.index('    private void probeServiceManagerInitAndLocation() {', start)

probe = r'''    private void probeGwmEngineerAdapter() {
        synchronized (oemLocLog) {
            oemLocLog.setLength(0);
            oemLocLog.append("GWMNaviEngineer • SAFE Car/GwmCarSetting PROBE\n");
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

                Object car = null;
                android.content.ServiceConnection safeConn = new android.content.ServiceConnection() {
                    @Override
                    public void onServiceConnected(android.content.ComponentName name, android.os.IBinder service) {
                        appendOemLoc("ServiceConnection CONNECTED " + String.valueOf(name));
                    }

                    @Override
                    public void onServiceDisconnected(android.content.ComponentName name) {
                        appendOemLoc("ServiceConnection DISCONNECTED " + String.valueOf(name));
                    }
                };

                for (java.lang.reflect.Method m : carClass.getMethods()) {
                    if (!"createCar".equals(m.getName()) || !java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
                    Class<?>[] ps = m.getParameterTypes();
                    StringBuilder sig = new StringBuilder("createCar(");
                    for (int i=0;i<ps.length;i++) { if(i>0)sig.append(','); sig.append(ps[i].getSimpleName()); }
                    sig.append(')');
                    appendOemLoc("FOUND " + sig.toString());

                    boolean hasServiceConnection = false;
                    for (Class<?> p : ps) {
                        if (android.content.ServiceConnection.class.isAssignableFrom(p)) {
                            hasServiceConnection = true;
                            break;
                        }
                    }
                    if (!hasServiceConnection) continue;

                    try {
                        Object[] args = new Object[ps.length];
                        boolean supported = true;
                        for (int i=0;i<ps.length;i++) {
                            Class<?> p = ps[i];
                            if (Context.class.isAssignableFrom(p)) args[i] = this;
                            else if (android.content.ServiceConnection.class.isAssignableFrom(p)) args[i] = safeConn;
                            else if (android.os.Handler.class.isAssignableFrom(p)) args[i] = new android.os.Handler(android.os.Looper.getMainLooper());
                            else if (p == long.class || p == Long.class) args[i] = 0L;
                            else if (p == int.class || p == Integer.class) args[i] = 0;
                            else if (p == boolean.class || p == Boolean.class) args[i] = false;
                            else {
                                args[i] = null;
                            }
                        }
                        m.setAccessible(true);
                        Object v = m.invoke(null, args);
                        appendOemLoc("CALL " + sig.toString() + "=" + (v == null ? "NULL" : v.getClass().getName()));
                        if (v != null) { car = v; break; }
                    } catch (Throwable e) {
                        appendOemLoc("CALL " + sig.toString() + " ERROR=" + rootCauseName(e));
                    }
                }

                if (car == null) {
                    appendOemLoc("RESULT: no safe createCar(ServiceConnection) succeeded");
                    return;
                }

                try {
                    java.lang.reflect.Method isConnected = carClass.getMethod("isConnected");
                    appendOemLoc("isConnected(before)=" + String.valueOf(isConnected.invoke(car)));
                } catch (Throwable ignored) { }

                try {
                    java.lang.reflect.Method connect = carClass.getMethod("connect");
                    connect.invoke(car);
                    appendOemLoc("Car.connect=OK");
                } catch (Throwable e) {
                    appendOemLoc("Car.connect ERROR=" + rootCauseName(e));
                }

                try { Thread.sleep(600); } catch (InterruptedException ignored) { }

                try {
                    java.lang.reflect.Method isConnected = carClass.getMethod("isConnected");
                    appendOemLoc("isConnected(after)=" + String.valueOf(isConnected.invoke(car)));
                } catch (Throwable ignored) { }

                java.lang.reflect.Method getMgr = carClass.getMethod("getCarManager", String.class);
                java.util.LinkedHashSet<String> serviceNames = new java.util.LinkedHashSet<>();

                for (java.lang.reflect.Field f : carClass.getDeclaredFields()) {
                    try {
                        if (!java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.getType() != String.class) continue;
                        f.setAccessible(true);
                        Object v = f.get(null);
                        if (v == null) continue;
                        String s = String.valueOf(v);
                        String both = (f.getName() + " " + s).toLowerCase(Locale.US);
                        if (both.contains("setting")) {
                            serviceNames.add(s);
                            appendOemLoc("CarConst " + f.getName() + "=" + s);
                        }
                    } catch (Throwable ignored) { }
                }

                serviceNames.add("setting");
                serviceNames.add("gwm_setting");
                serviceNames.add("car_setting");
                serviceNames.add("gwm_car_setting");

                Object manager = null;
                for (String svc : serviceNames) {
                    try {
                        Object v = getMgr.invoke(car, svc);
                        appendOemLoc("getCarManager(" + svc + ")=" + (v == null ? "NULL" : v.getClass().getName()));
                        if (v != null && mgrClass.isInstance(v)) {
                            manager = v;
                            break;
                        }
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
                    String n = m.getName().toLowerCase(Locale.US);
                    if (!(n.contains("speed") || n.contains("gear") || n.contains("yaw") ||
                            n.contains("heading") || n.contains("azimuth") || n.contains("bearing") ||
                            n.contains("angle"))) continue;
                    StringBuilder sig = new StringBuilder("M ");
                    sig.append(m.getName()).append('(');
                    Class<?>[] ps = m.getParameterTypes();
                    for (int i=0;i<ps.length;i++) { if(i>0)sig.append(','); sig.append(ps[i].getSimpleName()); }
                    sig.append(")->").append(m.getReturnType().getSimpleName());
                    appendOemLoc(sig.toString());

                    if (ps.length == 0) {
                        try {
                            m.setAccessible(true);
                            Object v = m.invoke(manager);
                            appendOemLoc("CALL " + m.getName() + "=" + String.valueOf(v));
                            if (v != null) useful++;
                        } catch (Throwable e) {
                            appendOemLoc("CALL " + m.getName() + " ERROR=" + rootCauseName(e));
                        }
                    }
                }
                appendOemLoc("RESULT usefulCalls=" + useful);
            } catch (Throwable e) {
                appendOemLoc("3.7 SAFE CAR ERROR=" + e.getClass().getSimpleName() + ":" + shortMessage(e));
            }
        }, "H9-GwmCarSetting37").start();
    }

'''
text = text[:start] + probe + text[end:]
text = text.replace('"3.6 TEST • BINDER: "', '"3.7 TEST • BINDER: "')
text = text.replace('"3.6 TEST • GWM: "', '"3.7 TEST • GWM: "')
text = text.replace('"3.6 TEST • LOC: "', '"3.7 TEST • LOC: "')
main.write_text(text, encoding='utf-8')

qv = Path('app/src/main/java/sa/h9/qibla/QiblaView.java')
q = qv.read_text(encoding='utf-8').replace('3.6 TEST', '3.7 TEST')
qv.write_text(q, encoding='utf-8')

gradle = Path('app/build.gradle')
g = gradle.read_text(encoding='utf-8')
g = re.sub(r'versionCode\s+\d+', 'versionCode 37', g)
g = re.sub(r"versionName\s+'[^']+'", "versionName '3.7-test'", g)
gradle.write_text(g, encoding='utf-8')
print('H9 Qibla 3.7 safe Car ServiceConnection probe applied')
