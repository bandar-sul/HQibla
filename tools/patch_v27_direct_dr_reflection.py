from pathlib import Path
import re

main = Path('app/src/main/java/sa/h9/qibla/MainActivity.java')
text = main.read_text(encoding='utf-8')
text = text.replace('2.6 TEST', '2.7 TEST')

text = text.replace('            updateBinderDiagnosticText("CONNECTED • descriptor=" + descriptor + " • registering callback + subscribe...");\n            probeGwmSubscribe(service);',
'''            updateBinderDiagnosticText("CONNECTED • descriptor=" + descriptor + " • probing direct GWM DR service...");
            probeDirectGwmDr();''')

start_marker = '    private void probeGwmSubscribe(IBinder service) {'
end_marker = '    private String shortMessage(Throwable t) {'
if start_marker not in text:
    raise SystemExit('probeGwmSubscribe not found')
start = text.index(start_marker)
end = text.index(end_marker, start)

new_probe = r'''    private void probeDirectGwmDr() {
        new Thread(() -> {
            StringBuilder log = new StringBuilder("DIRECT GWM DR REFLECTION\n");
            Object locationService = null;
            String providerClass = null;
            try {
                Context navCtx = createPackageContext("com.gwm.navi.app",
                        Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
                ClassLoader cl = navCtx.getClassLoader();
                android.content.pm.ApplicationInfo ai = getPackageManager().getApplicationInfo("com.gwm.navi.app", 0);
                log.append("apk=").append(ai.sourceDir).append('\n');

                // First inspect the exact OEM contracts we discovered statically.
                try {
                    Class<?> iface = Class.forName("com.gwm.navi.service.location.ILocationService", false, cl);
                    log.append("ILocationService methods:");
                    for (java.lang.reflect.Method m : iface.getDeclaredMethods()) {
                        String n = m.getName();
                        if (n.toLowerCase(Locale.US).contains("dr") || n.toLowerCase(Locale.US).contains("loc") ||
                                n.toLowerCase(Locale.US).contains("heading")) {
                            log.append(' ').append(n).append("()");
                        }
                    }
                    log.append('\n');
                } catch (Throwable e) {
                    log.append("ILocationService load=").append(e.getClass().getSimpleName()).append('\n');
                }

                // Enumerate GWMNavi classes and find a live provider with static getLocationService().
                dalvik.system.DexFile dex = new dalvik.system.DexFile(ai.sourceDir);
                java.util.Enumeration<String> entries = dex.entries();
                int inspected = 0;
                while (entries.hasMoreElements() && locationService == null) {
                    String cn = entries.nextElement();
                    if (!cn.startsWith("com.gwm.navi.")) continue;
                    String low = cn.toLowerCase(Locale.US);
                    if (!(low.contains("service") || low.contains("wrapper") || low.contains("location"))) continue;
                    if (++inspected > 2600) break;
                    try {
                        Class<?> c = Class.forName(cn, false, cl);
                        for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                            if (!"getLocationService".equals(m.getName())) continue;
                            if (m.getParameterTypes().length != 0) continue;
                            if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
                            try {
                                m.setAccessible(true);
                                Object v = m.invoke(null);
                                if (v != null) {
                                    locationService = v;
                                    providerClass = cn + ".getLocationService";
                                    break;
                                }
                            } catch (Throwable ignored) { }
                        }
                    } catch (Throwable ignored) { }
                }
                try { dex.close(); } catch (Throwable ignored) { }

                if (locationService == null) {
                    // Fallback: find singleton getInstance() then instance getLocationService().
                    dalvik.system.DexFile dex2 = new dalvik.system.DexFile(ai.sourceDir);
                    java.util.Enumeration<String> e2 = dex2.entries();
                    inspected = 0;
                    outer:
                    while (e2.hasMoreElements()) {
                        String cn = e2.nextElement();
                        if (!cn.startsWith("com.gwm.navi.")) continue;
                        String low = cn.toLowerCase(Locale.US);
                        if (!(low.contains("manager") || low.contains("service") || low.contains("wrapper"))) continue;
                        if (++inspected > 1800) break;
                        try {
                            Class<?> c = Class.forName(cn, false, cl);
                            java.lang.reflect.Method gi = null;
                            java.lang.reflect.Method gls = null;
                            for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                                if ("getInstance".equals(m.getName()) && m.getParameterTypes().length == 0 &&
                                        java.lang.reflect.Modifier.isStatic(m.getModifiers())) gi = m;
                                if ("getLocationService".equals(m.getName()) && m.getParameterTypes().length == 0 &&
                                        !java.lang.reflect.Modifier.isStatic(m.getModifiers())) gls = m;
                            }
                            if (gi != null && gls != null) {
                                gi.setAccessible(true); gls.setAccessible(true);
                                Object owner = gi.invoke(null);
                                if (owner != null) {
                                    Object v = gls.invoke(owner);
                                    if (v != null) {
                                        locationService = v;
                                        providerClass = cn + ".getInstance().getLocationService";
                                        break outer;
                                    }
                                }
                            }
                        } catch (Throwable ignored) { }
                    }
                    try { dex2.close(); } catch (Throwable ignored) { }
                }

                if (locationService == null) {
                    log.append("locationService=NULL • provider not resolved\n");
                } else {
                    log.append("provider=").append(providerClass).append('\n');
                    log.append("serviceClass=").append(locationService.getClass().getName()).append('\n');
                    Object dr = null;
                    java.lang.reflect.Method getDr = null;
                    for (java.lang.reflect.Method m : locationService.getClass().getMethods()) {
                        if ("getDrLocInfo".equals(m.getName()) && m.getParameterTypes().length == 0) {
                            getDr = m; break;
                        }
                    }
                    if (getDr == null) {
                        for (java.lang.reflect.Method m : locationService.getClass().getDeclaredMethods()) {
                            if ("getDrLocInfo".equals(m.getName()) && m.getParameterTypes().length == 0) {
                                getDr = m; break;
                            }
                        }
                    }
                    if (getDr != null) {
                        getDr.setAccessible(true);
                        dr = getDr.invoke(locationService);
                    }
                    if (dr == null) {
                        log.append("getDrLocInfo=NULL\n");
                    } else {
                        log.append("drClass=").append(dr.getClass().getName()).append('\n');
                        Double heading = extractHeading(dr);
                        log.append("DrLocInfo=").append(String.valueOf(dr)).append('\n');
                        if (heading != null && !heading.isNaN()) {
                            final float h = normalize(heading.floatValue());
                            log.append("DR_HEADING=").append(String.format(Locale.US, "%.2f°", h)).append('\n');
                            runOnUiThread(() -> {
                                qiblaView.setHeading(h, "اتجاه DR من نظام هافال");
                                updateBinderDiagnosticText(trimDiag("SUCCESS • OEM DR heading=" + String.format(Locale.US, "%.2f°", h) + "\n" + log));
                            });
                            return;
                        } else {
                            log.append("heading field/getter not resolved\n");
                        }
                    }
                }
            } catch (Throwable e) {
                log.append("ERROR=").append(e.getClass().getName()).append(':').append(shortMessage(e)).append('\n');
            }
            final String out = trimDiag(log.toString());
            runOnUiThread(() -> updateBinderDiagnosticText(out));
        }, "H9-Direct-DR-Probe").start();
    }

    private Double extractHeading(Object dr) {
        if (dr == null) return null;
        String[] getterNames = new String[]{"getHeading", "heading", "getDirection", "getBearing", "getAzimuth"};
        for (String name : getterNames) {
            try {
                java.lang.reflect.Method m;
                try { m = dr.getClass().getMethod(name); }
                catch (NoSuchMethodException e) { m = dr.getClass().getDeclaredMethod(name); }
                m.setAccessible(true);
                Object v = m.invoke(dr);
                if (v instanceof Number) return ((Number) v).doubleValue();
            } catch (Throwable ignored) { }
        }
        for (java.lang.reflect.Field f : dr.getClass().getDeclaredFields()) {
            String n = f.getName().toLowerCase(Locale.US);
            if (!(n.contains("heading") || n.contains("bearing") || n.contains("azimuth") || n.equals("direction"))) continue;
            try {
                f.setAccessible(true);
                Object v = f.get(dr);
                if (v instanceof Number) return ((Number) v).doubleValue();
            } catch (Throwable ignored) { }
        }
        return null;
    }

    private String trimDiag(String value) {
        if (value == null) return "null";
        return value.length() > 1700 ? value.substring(0, 1700) + "…" : value;
    }

'''
text = text[:start] + new_probe + text[end:]
main.write_text(text, encoding='utf-8')

qv = Path('app/src/main/java/sa/h9/qibla/QiblaView.java')
q = qv.read_text(encoding='utf-8')
q = q.replace('2.6 TEST', '2.7 TEST').replace('2.5 TEST', '2.7 TEST').replace('2.4 TEST', '2.7 TEST').replace('1.9 TEST', '2.7 TEST')
qv.write_text(q, encoding='utf-8')

gradle = Path('app/build.gradle')
g = gradle.read_text(encoding='utf-8')
g = re.sub(r'versionCode\s+\d+', 'versionCode 27', g)
g = re.sub(r"versionName\s+'[^']+'", "versionName '2.7-test'", g)
gradle.write_text(g, encoding='utf-8')

print('H9 Qibla 2.7 direct DR reflection probe applied')
