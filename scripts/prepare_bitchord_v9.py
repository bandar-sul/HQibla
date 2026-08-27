from pathlib import Path
import os, runpy

ROOT = Path(__file__).resolve().parents[1]
BITCHORD = ROOT / "bitchord"

runpy.run_path(str(ROOT / "scripts/prepare_bitchord_v8.py"), run_name="__main__")

old_cwd = Path.cwd()
os.chdir(BITCHORD)
try:
    p = Path("app/build.gradle.kts")
    s = p.read_text().replace('versionName = "1.4.1-H9-ar8"', 'versionName = "1.4.1-H9-ar9"')
    p.write_text(s)

    p = Path("app/src/main/java/com/music/bitchord/ui/player/NowPlayingScreen.kt")
    s = p.read_text()
    old = '.widthIn(max = PLAYER_MAX_WIDTH)\n                    .fillMaxWidth()'
    new = '.widthIn(max = if (isH9Player) 1420.dp else PLAYER_MAX_WIDTH)\n                    .fillMaxWidth()'
    if old not in s:
        raise RuntimeError("V9 player width cap pattern not found")
    s = s.replace(old, new, 1)
    s = s.replace('(if (isH9Player) Modifier.height(390.dp) else Modifier.weight(1f))','(if (isH9Player) Modifier.height(720.dp) else Modifier.weight(1f))',1)
    if 'val h9ArtWidth = if (isH9Player) minOf(maxWidth * 0.58f, 760.dp) else phoneFullArt' not in s:
        raise RuntimeError("V9 V8 h9ArtWidth pattern not found")
    s = s.replace('val h9ArtWidth = if (isH9Player) minOf(maxWidth * 0.58f, 760.dp) else phoneFullArt','val h9ArtWidth = if (isH9Player) minOf(maxWidth * 0.72f, 1180.dp) else phoneFullArt',1)
    strip_old = '''            Box(\n                modifier = Modifier\n                    .fillMaxWidth()\n                    .height(DISMISS_STRIP_HEIGHT),\n                contentAlignment = Alignment.Center,\n            ) {'''
    strip_new = '''            BoxWithConstraints(\n                modifier = Modifier\n                    .fillMaxWidth()\n                    .height(DISMISS_STRIP_HEIGHT),\n                contentAlignment = Alignment.Center,\n            ) {'''
    if strip_old not in s:
        raise RuntimeError("V9 top strip Box pattern not found")
    s = s.replace(strip_old, strip_new, 1)
    close_old = '''                    modifier = Modifier\n                        .align(Alignment.CenterEnd)\n                        .padding(end = 18.dp)\n                        .size(48.dp),'''
    close_new = '''                    modifier = Modifier\n                        .align(Alignment.Center)\n                        .absoluteOffset(x = -(maxWidth / 2) + 42.dp)\n                        .size(48.dp),'''
    if close_old not in s:
        raise RuntimeError("V9 full-player close alignment pattern not found")
    s = s.replace(close_old, close_new, 1)
    if 'import androidx.compose.foundation.layout.absoluteOffset' not in s:
        s = s.replace('import androidx.compose.foundation.layout.offset\n','import androidx.compose.foundation.layout.offset\nimport androidx.compose.foundation.layout.absoluteOffset\n',1)
    p.write_text(s)

    p = Path("app/src/main/java/com/music/bitchord/ui/components/MiniPlayer.kt")
    s = p.read_text()
    if 'import androidx.compose.foundation.layout.BoxWithConstraints' not in s:
        s = s.replace('import androidx.compose.foundation.layout.Box\n','import androidx.compose.foundation.layout.Box\nimport androidx.compose.foundation.layout.BoxWithConstraints\n',1)
    if 'import androidx.compose.foundation.layout.absoluteOffset' not in s:
        s = s.replace('import androidx.compose.foundation.layout.padding\n','import androidx.compose.foundation.layout.padding\nimport androidx.compose.foundation.layout.absoluteOffset\n',1)
    marker = '''    Box(\n        modifier = modifier\n            .padding(horizontal = PAGE_GUTTER)'''
    repl = '''    BoxWithConstraints(\n        modifier = modifier\n            .padding(horizontal = PAGE_GUTTER)'''
    if marker not in s:
        raise RuntimeError("V9 MiniPlayer outer Box pattern not found")
    s = s.replace(marker, repl, 1)
    inline_close = '''            IconButton(onClick = onClose, modifier = Modifier.size(GLYPH_SLOT)) {\n                Icon(\n                    Icons.Rounded.Close,\n                    contentDescription = "إيقاف وإغلاق الأغنية",\n                    tint = MaterialTheme.colorScheme.onBackground,\n                    modifier = Modifier.size(22.dp),\n                )\n            }'''
    if inline_close not in s:
        inline_close = inline_close.replace('modifier = Modifier.size(22.dp)','modifier = Modifier.size(24.dp)')
    if inline_close not in s:
        raise RuntimeError("V9 inline MiniPlayer close block not found")
    s = s.replace(inline_close,'',1)
    row_old = '''            modifier = Modifier\n                .fillMaxWidth()\n                .padding(ROW_PADDING),'''
    row_new = '''            modifier = Modifier\n                .fillMaxWidth()\n                .absolutePadding(left = if (isH9Mini) GLYPH_SLOT + 8.dp else ROW_PADDING, right = ROW_PADDING, top = ROW_PADDING, bottom = ROW_PADDING),'''
    if row_old not in s:
        raise RuntimeError("V9 MiniPlayer row padding pattern not found")
    s = s.replace(row_old,row_new,1)
    if 'import androidx.compose.foundation.layout.absolutePadding' not in s:
        s = s.replace('import androidx.compose.foundation.layout.padding\n','import androidx.compose.foundation.layout.padding\nimport androidx.compose.foundation.layout.absolutePadding\n',1)
    end_marker = '''        }\n    }\n}\n'''
    overlay = '''        }\n        IconButton(\n            onClick = onClose,\n            modifier = Modifier\n                .align(Alignment.Center)\n                .absoluteOffset(x = -(maxWidth / 2) + GLYPH_SLOT / 2 + 4.dp)\n                .size(GLYPH_SLOT),\n        ) {\n            Icon(\n                Icons.Rounded.Close,\n                contentDescription = "إيقاف وإغلاق الأغنية",\n                tint = MaterialTheme.colorScheme.onBackground,\n                modifier = Modifier.size(22.dp),\n            )\n        }\n    }\n}\n'''
    idx = s.rfind(end_marker)
    if idx < 0:
        raise RuntimeError("V9 MiniPlayer end marker not found")
    s = s[:idx] + overlay + s[idx + len(end_marker):]
    p.write_text(s)
finally:
    os.chdir(old_cwd)

print("BitChord H9 V9 patch applied")
