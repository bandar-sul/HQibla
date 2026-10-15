from pathlib import Path

view_path = Path("app/src/main/java/sa/h9/qibla/QiblaView.java")
view = view_path.read_text()

# Apply layout changes only when the source still has the old values.
replacements = [
    ('drawCompass(c, w * .427f, h * .53f, Math.min(h * .236f, w * .132f));',
     'drawCompass(c, w * .50f, h * .53f, Math.min(h * .236f, w * .132f));'),
    ('c.drawText("8.1 MANUAL", w * .114f, h * .116f, p);',
     '// Removed legacy build label as requested.'),
    ('float x = w * .018f, cardW = w * .178f, cardH = h * .116f;',
     'float x = w * .804f, cardW = w * .178f, cardH = h * .116f;'),
    ('float x=w*.715f, y=h*.175f, cw=w*.265f, ch=h*.405f;',
     'float x=w*.018f, y=h*.175f, cw=w*.265f, ch=h*.405f;'),
    ('fontScale = Math.max(0.85f, Math.min(1.45f, scale));',
     'fontScale = Math.max(0.85f, Math.min(1.90f, scale));'),
]
for old, new in replacements:
    if old in view:
        view = view.replace(old, new, 1)
    elif new not in view:
        raise SystemExit("Expected source anchor missing: " + old)

view_path.write_text(view)

# Expand the existing font-size cycle without rewriting font preferences.
activity_path = Path("app/src/main/java/sa/h9/qibla/MainActivity.java")
activity = activity_path.read_text()
old = 'float[] sizes = {0.85f, 1.0f, 1.15f, 1.30f, 1.45f};'
new = 'float[] sizes = {0.85f, 1.0f, 1.15f, 1.35f, 1.55f, 1.75f, 1.90f};'
if old in activity:
    activity = activity.replace(old, new, 1)
elif new not in activity:
    raise SystemExit("Expected font-size list missing")
activity_path.write_text(activity)

print("Applied corrected dashboard layout and verified font options are present.")
