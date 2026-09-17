from pathlib import Path
import re

main = Path('app/src/main/java/sa/h9/qibla/MainActivity.java')
text = main.read_text(encoding='utf-8')
text = text.replace('3.1 TEST', '3.2 TEST')
text = text.replace('            updateBinderDiagnosticText("CONNECTED • MapControl=" + descriptor + " • probing ServiceManagerFactory...");\n            probeServiceManagerFactoryDirect();',
'''            updateBinderDiagnosticText("CONNECTED • MapControl=" + descriptor + " • direct getLocationService()...");
            probeDirectGetLocationService();''')

marker = '    private void probeServiceManagerFactoryDirect() {'
if marker not in text:
    raise SystemExit('probeServiceManagerFactoryDirect marker not found')

probe = r'''    private void probeDirectGetLocationService() {
        synchronized (oemLocLog) {
            oemLocLog.setLength(0);
            oemLocLog.append("DIRECT getLocationService -> getDrLocInfo PROBE\n");
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

                java.lang.reflect.Method getLocationService = factory.getDeclaredMethod("getLocationService");
                getLocationService.setAccessible(true);
                appendOemLoc("method=getLocationService()->" + getLocationService.getReturnType().getName());

                Object service = getLocationService.invoke(null);
                appendOemLoc("getLocationService=" + (service == null ? "NULL" : service.getClass().getName()));
                if (service == null) {
                    appendOemLoc("RESULT: NULL service instance");
                    return;
                }

                appendInterestingMethods(service.getClass(), "service");

                try {
                    java.lang.reflect.Method drMethod = service.getClass().getMethod("getDrLocInfo");
                    drMethod.setAccessible(true);
                    Object dr = drMethod.invoke(service);
                    appendOemLoc("getDrLocInfo=" + String.valueOf(dr));
                    if (dr != null) {
                        appendOemLoc("DrLocInfo class=" + dr.getClass().getName());
                        Double heading = extractHeading(dr);
                        if (heading != null && !heading.isNaN()) {
                            final float h = normalize(heading.floatValue());
                            appendOemLoc("SUCCESS DR_HEADING=" + String.format(Locale.US, "%.2f°", h));
                            runOnUiThread(() -> qiblaView.setHeading(h, "اتجاه DR من نظام هافال"));
                        } else {
                            appendOemLoc("DR object found but heading field/getter not resolved");
                            for (java.lang.reflect.Method m : dr.getClass().getMethods()) {
                                String n = m.getName().toLowerCase(Locale.US);
                                if (n.contains("heading") || n.contains("bearing") || n.contains("direction") || n.contains("azimuth")) {
                                    appendOemLoc("drMethod=" + m.getName() + "()->" + m.getReturnType().getName());
                                }
                            }
                            for (java.lang.reflect.Field f : dr.getClass().getDeclaredFields()) {
                                String n = f.getName().toLowerCase(Locale.US);
                                if (n.contains("heading") || n.contains("bearing") || n.contains("direction") || n.contains("azimuth")) {
                                    try {
                                        f.setAccessible(true);
                                        appendOemLoc("drField=" + f.getName() + "=" + String.valueOf(f.get(dr)));
                                    } catch (Throwable ignored) { }
                                }
                            }
                        }
                    } else {
                        appendOemLoc("RESULT: getDrLocInfo returned NULL");
                    }
                } catch (Throwable e) {
                    appendOemLoc("getDrLocInfo ERROR=" + rootCauseName(e));
                    invokeDrInfo(service, "directService");
                }
            } catch (Throwable e) {
                appendOemLoc("DIRECT 3.2 ERROR=" + e.getClass().getSimpleName() + ":" + shortMessage(e));
            }
        }, "H9-Direct-getLocationService").start();
    }

'''
text = text.replace(marker, probe + marker)
text = text.replace('"3.1 TEST • BINDER: "', '"3.2 TEST • BINDER: "')
text = text.replace('"3.1 TEST • GWM: "', '"3.2 TEST • GWM: "')
text = text.replace('"3.1 TEST • LOC: "', '"3.2 TEST • LOC: "')
main.write_text(text, encoding='utf-8')

qv = Path('app/src/main/java/sa/h9/qibla/QiblaView.java')
q = qv.read_text(encoding='utf-8')
for old in ['3.1 TEST','3.0 TEST','2.9 TEST','2.8 TEST','2.7 TEST','2.6 TEST','1.9 TEST']:
    q = q.replace(old, '3.2 TEST')
qv.write_text(q, encoding='utf-8')

gradle = Path('app/build.gradle')
g = gradle.read_text(encoding='utf-8')
g = re.sub(r'versionCode\s+\d+', 'versionCode 32', g)
g = re.sub(r"versionName\s+'[^']+'", "versionName '3.2-test'", g)
gradle.write_text(g, encoding='utf-8')
print('H9 Qibla 3.2 direct getLocationService/getDrLocInfo probe applied')
