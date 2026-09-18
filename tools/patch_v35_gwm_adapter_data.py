from pathlib import Path
import re

main = Path('app/src/main/java/sa/h9/qibla/MainActivity.java')
text = main.read_text(encoding='utf-8')
text = text.replace('3.4 TEST', '3.5 TEST')

start = text.index('    private void probeGwmEngineerAdapter() {')
end = text.index('    private void probeServiceManagerInitAndLocation() {', start)

probe = r'''    private void probeGwmEngineerAdapter() {
        synchronized (oemLocLog) {
            oemLocLog.setLength(0);
            oemLocLog.append("GWMNaviEngineer • Adapter DATA PROBE\n");
        }
        new Thread(() -> {
            try {
                Context engCtx = createPackageContext("com.gwm.naviengineer",
                        Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
                ClassLoader cl = engCtx.getClassLoader();
                Class<?> clientClass = Class.forName("com.gwm.android.adapter.client.GwmAdapterClient", false, cl);

                java.lang.reflect.Method getInstance = clientClass.getDeclaredMethod("getInstance");
                getInstance.setAccessible(true);
                Object client = getInstance.invoke(null);
                appendOemLoc("instance=" + (client == null ? "NULL" : client.getClass().getName()));
                if (client == null) {
                    appendOemLoc("RESULT: getInstance() returned NULL");
                    return;
                }

                java.lang.reflect.Method getData = clientClass.getDeclaredMethod("getData", String.class);
                java.lang.reflect.Method getDataForcely = clientClass.getDeclaredMethod("getDataForcely", String.class);
                getData.setAccessible(true);
                getDataForcely.setAccessible(true);

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

                int useful = 0;
                for (String key : keys) {
                    Object normal = null;
                    Object forced = null;
                    String ne = null;
                    String fe = null;
                    try {
                        normal = getData.invoke(client, key);
                    } catch (Throwable e) {
                        ne = rootCauseName(e);
                    }
                    try {
                        forced = getDataForcely.invoke(client, key);
                    } catch (Throwable e) {
                        fe = rootCauseName(e);
                    }

                    String n = normal == null ? "null" : String.valueOf(normal);
                    String f = forced == null ? "null" : String.valueOf(forced);
                    if (!"null".equalsIgnoreCase(n) || !"null".equalsIgnoreCase(f)) useful++;

                    StringBuilder line = new StringBuilder();
                    line.append(key).append(" | get=").append(n);
                    if (ne != null) line.append("[").append(ne).append("]");
                    line.append(" | force=").append(f);
                    if (fe != null) line.append("[").append(fe).append("]");
                    appendOemLoc(line.toString());
                }

                appendOemLoc("RESULT useful=" + useful + "/" + keys.length);
            } catch (Throwable e) {
                appendOemLoc("3.5 ENGINEER ERROR=" + e.getClass().getSimpleName() + ":" + shortMessage(e));
            }
        }, "H9-GwmEngineerAdapter35").start();
    }

'''
text = text[:start] + probe + text[end:]
text = text.replace('"3.4 TEST • BINDER: "', '"3.5 TEST • BINDER: "')
text = text.replace('"3.4 TEST • GWM: "', '"3.5 TEST • GWM: "')
text = text.replace('"3.4 TEST • LOC: "', '"3.5 TEST • LOC: "')
main.write_text(text, encoding='utf-8')

qv = Path('app/src/main/java/sa/h9/qibla/QiblaView.java')
q = qv.read_text(encoding='utf-8').replace('3.4 TEST', '3.5 TEST')
qv.write_text(q, encoding='utf-8')

gradle = Path('app/build.gradle')
g = gradle.read_text(encoding='utf-8')
g = re.sub(r'versionCode\s+\d+', 'versionCode 35', g)
g = re.sub(r"versionName\s+'[^']+'", "versionName '3.5-test'", g)
gradle.write_text(g, encoding='utf-8')

print('H9 Qibla 3.5 focused GwmAdapterClient data probe applied')
