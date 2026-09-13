from pathlib import Path

p=Path('app/src/main/java/sa/h9/qibla/MainActivity.java')
s=p.read_text(encoding='utf-8')
if 'import android.database.Cursor;' not in s:
    s=s.replace('import android.content.pm.PackageManager;\n','import android.content.pm.PackageManager;\nimport android.database.Cursor;\n')
s=s.replace('diagnosticText.setText("1.7 TEST • fused: بانتظار البيانات");','diagnosticText.setText("1.8 TEST • GWM middleware: جاري الاختبار");')
s=s.replace('if (diagnosticText != null) diagnosticText.setText("1.7 TEST • " + text);','if (diagnosticText != null) diagnosticText.setText("1.8 TEST • " + text);')
needle='        updateNetworkStatus();\n        showDeveloperNoticeOnce();\n        ensureLocationPermission();\n'
if needle in s and 'testGwmMiddleware();' not in s:
    s=s.replace(needle,needle+'        testGwmMiddleware();\n',1)
mark='    private void updateDiagnosticText(String text) {'
fun='''    private void testGwmMiddleware() {\n        updateDiagnosticText("GWM middleware: فحص الصلاحية...");\n        new Thread(() -> {\n            Cursor c = null;\n            try {\n                if (checkSelfPermission("com.gwm.navi.permission.READ_CONTENT") != PackageManager.PERMISSION_GRANTED) {\n                    runOnUiThread(() -> updateDiagnosticText("READ_CONTENT غير ممنوحة للتطبيق"));\n                    return;\n                }\n                Uri uri = Uri.parse("content://com.gwm.navi.middleware");\n                c = getContentResolver().query(uri, null, null, null, null);\n                if (c == null) {\n                    runOnUiThread(() -> updateDiagnosticText("middleware متاح • query=null"));\n                    return;\n                }\n                String[] cols = c.getColumnNames();\n                StringBuilder b = new StringBuilder("middleware OK • rows=").append(c.getCount()).append(" • cols=");\n                for (int i=0; i<Math.min(cols.length,8); i++) { if(i>0)b.append(','); b.append(cols[i]); }\n                if (c.moveToFirst()) {\n                    b.append(" • values=");\n                    for (int i=0; i<Math.min(cols.length,5); i++) {\n                        if(i>0)b.append(" | ");\n                        b.append(cols[i]).append('=');\n                        try { String v=c.getString(i); if(v!=null && v.length()>24)v=v.substring(0,24)+"…"; b.append(v); } catch(Exception e){ b.append('?'); }\n                    }\n                }\n                String out=b.toString();\n                runOnUiThread(() -> updateDiagnosticText(out));\n            } catch (SecurityException e) {\n                String x=e.getMessage();\n                runOnUiThread(() -> updateDiagnosticText("SecurityException • "+x));\n            } catch (IllegalArgumentException e) {\n                String x=e.getMessage();\n                runOnUiThread(() -> updateDiagnosticText("Provider وصلنا • URI غير معروف • "+x));\n            } catch (Exception e) {\n                String x=e.getClass().getSimpleName()+" • "+e.getMessage();\n                runOnUiThread(() -> updateDiagnosticText(x));\n            } finally { if(c!=null)c.close(); }\n        }, "H9-GwmMiddleware-Test").start();\n    }\n\n'''
if 'private void testGwmMiddleware()' not in s:
    assert mark in s
    s=s.replace(mark,fun+mark,1)
p.write_text(s,encoding='utf-8')

q=Path('app/src/main/java/sa/h9/qibla/QiblaView.java')
q.write_text(q.read_text(encoding='utf-8').replace('1.7 TEST','1.8 TEST'),encoding='utf-8')

g=Path('app/build.gradle')
g.write_text(g.read_text(encoding='utf-8').replace('versionCode 17','versionCode 18').replace("versionName '1.7-test'","versionName '1.8-test'"),encoding='utf-8')
