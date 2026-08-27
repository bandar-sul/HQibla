from pathlib import Path
import os, runpy

ROOT = Path(__file__).resolve().parents[1]
BITCHORD = ROOT / "bitchord"

runpy.run_path(str(ROOT / "scripts/prepare_bitchord_v7.py"), run_name="__main__")

old_cwd = Path.cwd()
os.chdir(BITCHORD)
try:
    p = Path("app/build.gradle.kts")
    s = p.read_text().replace('versionName = "1.4.1-H9-ar7"', 'versionName = "1.4.1-H9-ar8"')
    p.write_text(s)

    p = Path("app/src/main/java/com/music/bitchord/ui/player/NowPlayingScreen.kt")
    s = p.read_text()
    s = s.replace('(if (isH9Player) Modifier.height(520.dp) else Modifier.weight(1f))','(if (isH9Player) Modifier.height(390.dp) else Modifier.weight(1f))',1)
    box_start = s.index('            BoxWithConstraints(')
    geom_start = s.index('                val fullArt', box_start)
    geom_end = s.index('                // How far down', geom_start)
    new_geometry = '''                val phoneFullArt = minOf(maxWidth, maxHeight - ART_TITLE_GAP - HEADER_HEIGHT)
                    .coerceAtLeast(THUMB_SIZE)
                val h9ArtWidth = if (isH9Player) minOf(maxWidth * 0.58f, 760.dp) else phoneFullArt
                val h9ArtHeight = if (isH9Player) (h9ArtWidth * 9f / 16f) else phoneFullArt
                val fullArt = if (isH9Player) h9ArtHeight else phoneFullArt
                val groupTop = if (isH9Player) 0.dp else
                    ((maxHeight - phoneFullArt - ART_TITLE_GAP - HEADER_HEIGHT) / 2).coerceAtLeast(0.dp)
                val artWidth = if (isH9Player) lerp(h9ArtWidth, THUMB_SIZE, p) else lerp(phoneFullArt, THUMB_SIZE, p)
                val artHeight = if (isH9Player) lerp(h9ArtHeight, THUMB_SIZE, p) else lerp(phoneFullArt, THUMB_SIZE, p)
                val artSize = artHeight
                val artTop = lerp(groupTop, 0.dp, p)
                val artStart = if (isH9Player)
                    lerp((maxWidth - h9ArtWidth) / 2, 0.dp, p)
                else
                    lerp((maxWidth - phoneFullArt) / 2, 0.dp, p)
                val titleTop = if (isH9Player)
                    lerp(groupTop + h9ArtHeight + 4.dp, 0.dp, p)
                else
                    lerp(groupTop + phoneFullArt + ART_TITLE_GAP, 0.dp, p)
                val titleStart = lerp(0.dp, THUMB_SIZE + 12.dp, p)
'''
    s = s[:geom_start] + new_geometry + s[geom_end:]
    if '.size(artSize)' not in s:
        raise RuntimeError("V8 artSize modifier pattern not found")
    s = s.replace('.size(artSize)', '.width(artWidth)\n                        .height(artHeight)',1)
    old_scale = '''                            val idle = artScale + (1f - artScale) * p
                            val h9WideScale = if (isH9Player) 1f + (0.45f * (1f - p)) else 1f
                            scaleX = idle * h9WideScale
                            scaleY = idle
                            alpha = if (isH9Player && p < 0.5f) 0.92f else 1f
                            translationX = swipeSettle * (1f - p)'''
    new_scale = '''                            val idle = artScale + (1f - artScale) * p
                            scaleX = idle
                            scaleY = idle
                            alpha = if (isH9Player && p < 0.5f) 0.86f else 1f
                            translationX = swipeSettle * (1f - p)'''
    if old_scale not in s:
        raise RuntimeError("V8 V7 scale hack pattern not found")
    s = s.replace(old_scale,new_scale,1)
    s = s.replace('private val ART_TITLE_GAP = 8.dp','private val ART_TITLE_GAP = 4.dp',1)
    s = s.replace('private val ART_BOX_TOP_PAD = 6.dp','private val ART_BOX_TOP_PAD = 2.dp',1)
    p.write_text(s)

    p = Path("app/src/main/java/com/music/bitchord/ui/screens/DetailScreen.kt")
    s = p.read_text()
    old = '''    val artHeight = LocalConfiguration.current.screenWidthDp.dp /
        if (isArtist) ARTIST_PHOTO_RATIO else SLEEVE_RATIO'''
    new = '''    val detailConfig = LocalConfiguration.current
    val isH9Detail = detailConfig.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE && detailConfig.screenWidthDp >= 900
    val artHeight = if (isH9Detail && !isArtist) {
        detailConfig.screenHeightDp.dp * 0.42f
    } else {
        detailConfig.screenWidthDp.dp / if (isArtist) ARTIST_PHOTO_RATIO else SLEEVE_RATIO
    }'''
    if old not in s:
        start = s.index('    val artHeight = LocalConfiguration.current.screenWidthDp.dp')
        end = s.index('\n\n', start)
        s = s[:start] + new + s[end:]
    else:
        s = s.replace(old,new,1)
    p.write_text(s)

    p = Path("app/src/main/java/com/music/bitchord/MainActivity.kt")
    s = p.read_text()
    marker = '        applyH9Immersive()\n        window.decorView.setOnSystemUiVisibilityChangeListener {'
    repl = '''        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN)
        applyH9Immersive()
        window.decorView.setOnSystemUiVisibilityChangeListener {'''
    if marker not in s:
        raise RuntimeError("V8 immersive marker not found")
    s = s.replace(marker,repl,1)
    if 'override fun onWindowFocusChanged(hasFocus: Boolean)' not in s:
        anchor = '    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {'
        focus_block = '''    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.decorView.post { applyH9Immersive() }
            window.decorView.postDelayed({ applyH9Immersive() }, 80L)
            window.decorView.postDelayed({ applyH9Immersive() }, 350L)
            window.decorView.postDelayed({ applyH9Immersive() }, 900L)
        }
    }

'''
        if anchor not in s:
            raise RuntimeError("V8 dispatchKeyEvent anchor not found")
        s = s.replace(anchor,focus_block + anchor,1)
    p.write_text(s)
finally:
    os.chdir(old_cwd)

print("BitChord H9 V8 patch applied")
