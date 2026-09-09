from pathlib import Path

p = Path('app/src/main/java/sa/h9/qibla/MainActivity.java')
s = p.read_text(encoding='utf-8')
old = 'تم تطوير البرنامج بواسطة هافال H9 صدقة جارية\\n\\nللتواصل عبر الواتساب\\n0539100194\\n\\nمتجر H9'
new = 'تم تطوير البرنامج بواسطة بندر بن سليمان حساب هافال H9 صدقة جارية عني وعنك وعن كل مسلم\\n\\nللتواصل عبر الواتساب\\n0539100194\\n\\nمتجر H9'
if old not in s:
    raise SystemExit('About text not found')
s = s.replace(old, new, 1)
p.write_text(s, encoding='utf-8')

q = Path('app/src/main/java/sa/h9/qibla/QiblaView.java')
t = q.read_text(encoding='utf-8')
t = t.replace('c.drawText("1.5", w * .114f, h * .116f, p);', 'c.drawText("1.6", w * .114f, h * .116f, p);')
t = t.replace('H 9   Q i b l a   1.5', 'H 9   Q i b l a   1.6')
q.write_text(t, encoding='utf-8')

g = Path('app/build.gradle')
u = g.read_text(encoding='utf-8').replace('versionCode 15', 'versionCode 16').replace("versionName '1.5'", "versionName '1.6'")
g.write_text(u, encoding='utf-8')
