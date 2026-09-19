from pathlib import Path
import re

main = Path('app/src/main/java/sa/h9/qibla/MainActivity.java')
text = main.read_text(encoding='utf-8')
text = text.replace('3.7 TEST', '3.8 TEST')

start = text.index('    private void probeGwmEngineerAdapter() {')
end = text.index('    private void probeServiceManagerInitAndLocation() {', start)

probe = r'''    private void probeGwmEngineerAdapter() {
        synchronized (oemLocLog) {
            oemLocLog.setLength(0);
            oemLocLog.append("GWM CarService • gwm_car_setting PROBE\n");
        }
        new Thread(() -> {
            try {
                Context engCtx = createPackageContext("com.gwm.naviengineer",
                        Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
                ClassLoader cl = engCtx.getClassLoader();

                Class<?> carClass = Class.forName("android.car.Car", false, cl);
                Class<?> mgrClass = Class.forName("android.car.hardware.setting.GwmCarSettingManager", false, cl);

                android.content.ServiceConnection safeConn = new android.content.ServiceConnection() {
                    @Override
                    public void onServiceConnected(android.content.ComponentName name, android.os.IBinder service) {
                        appendOemLoc("CONNECTED=" + String.valueOf(name));
                    }

                    @Override
                    public void onServiceDisconnected(android.content.ComponentName name) {
                        appendOemLoc("DISCONNECTED=" + String.valueOf(name));
                    }
                };

                Object car = null;
                for (java.lang.reflect.Method m : carClass.getMethods()) {
                    if (!"createCar".equals(m.getName()) || !java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
                    Class<?>[] ps = m.getParameterTypes();

                    boolean hasConn = false;
                    for (Class<?> p : ps) if (android.content.ServiceConnection.class.isAssignableFrom(p)) hasConn = true;
                    if (!hasConn) continue;

                    try {
                        Object[] args = new Object[ps.length];
                        for (int i=0;i<ps.length;i++) {
                            Class<?> p = ps[i];
                            if (Context.class.isAssignableFrom(p)) args[i] = this;
                            else if (android.content.ServiceConnection.class.isAssignableFrom(p)) args[i] = safeConn;
                            else if (android.os.Handler.class.isAssignableFrom(p)) args[i] = new android.os.Handler(android.os.Looper.getMainLooper());
                            else if (p == long.class || p == Long.class) args[i] = 0L;
                            else if (p == int.class || p == Integer.class) args[i] = 0;
                            else if (p == boolean.class || p == Boolean.class) args[i] = false;
                            else args[i] = null;
                        }
                        m.setAccessible(true);
                        Object v = m.invoke(null, args);
                        if (v != null) { car = v; break; }
                    } catch (Throwable ignored) { }
                }

                if (car == null) {
                    appendOemLoc("RESULT: Car=NULL");
                    return;
                }

                try {
                    java.lang.reflect.Method connect = carClass.getMethod("connect");
                    connect.invoke(car);
                } catch (Throwable e) {
                    appendOemLoc("connect ERROR=" + rootCauseName(e));
                }

                long deadline = System.currentTimeMillis() + 2500L;
                boolean connected = false;
                try {
                    java.lang.reflect.Method isConnected = carClass.getMethod("isConnected");
                    while (System.currentTimeMillis() < deadline) {
                        try {
                            Object v = isConnected.invoke(car);
                            connected = Boolean.TRUE.equals(v);
                            if (connected) break;
                        } catch (Throwable ignored) { }
                        try { Thread.sleep(100); } catch (InterruptedException ignored) { }
                    }
                } catch (Throwable ignored) { }

                appendOemLoc("isConnected=" + connected);

                java.lang.reflect.Method getMgr = carClass.getMethod("getCarManager", String.class);
                Object manager = null;
                try {
                    manager = getMgr.invoke(car, "gwm_car_setting");
                    appendOemLoc("getCarManager(gwm_car_setting)=" + (manager == null ? "NULL" : manager.getClass().getName()));
                } catch (Throwable e) {
                    appendOemLoc("getCarManager ERROR=" + rootCauseName(e));
                }

                if (manager == null) {
                    appendOemLoc("RESULT: manager=NULL");
                    return;
                }

                appendOemLoc("isGwmManager=" + mgrClass.isInstance(manager));

                int shown = 0;
                int useful = 0;
                for (java.lang.reflect.Method m : manager.getClass().getMethods()) {
                    String n = m.getName().toLowerCase(Locale.US);
                    if (!(n.contains("speed") || n.contains("gear") || n.contains("yaw") ||
                            n.contains("heading") || n.contains("azimuth") || n.contains("bearing") ||
                            n.contains("angle"))) continue;

                    Class<?>[] ps = m.getParameterTypes();
                    StringBuilder sig = new StringBuilder();
                    sig.append(m.getName()).append('(');
                    for (int i=0;i<ps.length;i++) {
                        if (i>0) sig.append(',');
                        sig.append(ps[i].getSimpleName());
                    }
                    sig.append(")->").append(m.getReturnType().getSimpleName());
                    appendOemLoc("M " + sig.toString());
                    shown++;

                    if (ps.length == 0) {
                        try {
                            m.setAccessible(true);
                            Object v = m.invoke(manager);
                            appendOemLoc("V " + m.getName() + "=" + String.valueOf(v));
                            if (v != null) useful++;
                        } catch (Throwable e) {
                            appendOemLoc("V " + m.getName() + " ERROR=" + rootCauseName(e));
                        }
                    }
                }

                appendOemLoc("RESULT methods=" + shown + " useful=" + useful);
            } catch (Throwable e) {
                appendOemLoc("3.8 ERROR=" + e.getClass().getSimpleName() + ":" + shortMessage(e));
            }
        }, "H9-GwmCarSetting38").start();
    }

'''
text = text[:start] + probe + text[end:]
text = text.replace('"3.7 TEST • BINDER: "', '"3.8 TEST • BINDER: "')
text = text.replace('"3.7 TEST • GWM: "', '"3.8 TEST • GWM: "')
text = text.replace('"3.7 TEST • LOC: "', '"3.8 TEST • LOC: "')
main.write_text(text, encoding='utf-8')

qv = Path('app/src/main/java/sa/h9/qibla/QiblaView.java')
q = qv.read_text(encoding='utf-8').replace('3.7 TEST', '3.8 TEST')
qv.write_text(q, encoding='utf-8')

gradle = Path('app/build.gradle')
g = gradle.read_text(encoding='utf-8')
g = re.sub(r'versionCode\s+\d+', 'versionCode 38', g)
g = re.sub(r"versionName\s+'[^']+'", "versionName '3.8-test'", g)
gradle.write_text(g, encoding='utf-8')
print('H9 Qibla 3.8 direct gwm_car_setting probe applied')
