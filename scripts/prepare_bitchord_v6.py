from pathlib import Path
import os, runpy
ROOT=Path(__file__).resolve().parents[1]; BITCHORD=ROOT/'bitchord'
runpy.run_path(str(ROOT/'scripts/prepare_bitchord_v5.py'),run_name='__main__')
old_cwd=Path.cwd(); os.chdir(BITCHORD)
try:
 p=Path('app/build.gradle.kts'); s=p.read_text().replace('versionName = "1.4.1-H9-ar5"','versionName = "1.4.1-H9-ar6"'); p.write_text(s)
 p=Path('app/src/main/java/com/music/bitchord/ui/player/NowPlayingScreen.kt'); s=p.read_text().replace('private val ART_TITLE_GAP = 20.dp','private val ART_TITLE_GAP = 8.dp').replace('private val DISMISS_STRIP_HEIGHT = 44.dp','private val DISMISS_STRIP_HEIGHT = 64.dp').replace('private val ART_BOX_TOP_PAD = 14.dp','private val ART_BOX_TOP_PAD = 6.dp').replace('private val PLAYER_GUTTER = 18.dp','private val PLAYER_GUTTER = 16.dp')
 s=s.replace('    onClearQueue: () -> Unit,\n    onOpenMenu: () -> Unit,','    onClearQueue: () -> Unit,\n    onClosePlayback: () -> Unit,\n    onOpenMenu: () -> Unit,',1)
 old='''            Box(\n                modifier = Modifier\n                    .fillMaxWidth()\n                    .height(DISMISS_STRIP_HEIGHT),\n                contentAlignment = Alignment.Center,\n            ) {\n                Box(\n                    Modifier\n                        .width(38.dp)\n                        .height(5.dp)\n                        .clip(RoundedCornerShape(3.dp))\n                        .background(Color.White.copy(alpha = 0.32f)),\n                )\n            }'''
 new='''            Box(\n                modifier = Modifier\n                    .fillMaxWidth()\n                    .height(DISMISS_STRIP_HEIGHT),\n                contentAlignment = Alignment.Center,\n            ) {\n                Box(Modifier.width(46.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.42f)))\n                IconButton(onClick = onClosePlayback, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 18.dp).size(48.dp)) {\n                    Icon(Icons.Rounded.Close, contentDescription = "إيقاف وإغلاق الأغنية", tint = Color.White, modifier = Modifier.size(30.dp))\n                }\n            }'''
 if old not in s: raise RuntimeError('V6 top strip pattern not found')
 s=s.replace(old,new,1); p.write_text(s)
 p=Path('app/src/main/java/com/music/bitchord/ui/components/MiniPlayer.kt'); s=p.read_text()
 if 'import androidx.compose.foundation.layout.widthIn' not in s: s=s.replace('import androidx.compose.foundation.layout.width\n','import androidx.compose.foundation.layout.width\nimport androidx.compose.foundation.layout.widthIn\n')
 if 'import androidx.compose.material.icons.rounded.Close' not in s: s=s.replace('import androidx.compose.material.icons.rounded.Pause\n','import androidx.compose.material.icons.rounded.Pause\nimport androidx.compose.material.icons.rounded.Close\n')
 s=s.replace('    onNext: () -> Unit,\n    onExpand: () -> Unit,','    onNext: () -> Unit,\n    onClose: () -> Unit,\n    onExpand: () -> Unit,',1)
 s=s.replace('    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()\n    val shape = RoundedCornerShape(BAR_CORNER)','    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()\n    val miniConfiguration = androidx.compose.ui.platform.LocalConfiguration.current\n    val isH9Mini = miniConfiguration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE && miniConfiguration.screenWidthDp >= 900\n    val shape = RoundedCornerShape(BAR_CORNER)',1)
 s=s.replace('.size(40.dp)','.size(if (isH9Mini) 32.dp else 40.dp)',1).replace('Spacer(Modifier.width(10.dp))','Spacer(Modifier.width(if (isH9Mini) 6.dp else 10.dp))',1).replace('Column(Modifier.weight(1f)) {','Column(if (isH9Mini) Modifier.widthIn(max = 460.dp) else Modifier.weight(1f)) {',1)
 next_block='''            IconButton(onClick = onNext, modifier = Modifier.size(GLYPH_SLOT)) {\n                Icon(\n                    Icons.Rounded.SkipNext,\n                    contentDescription = "التالي",\n                    tint = MaterialTheme.colorScheme.onBackground,\n                    modifier = Modifier.size(28.dp),\n                )\n            }'''
 if next_block not in s: next_block=next_block.replace('contentDescription = "التالي"','contentDescription = "Next"')
 if next_block not in s: raise RuntimeError('V6 next button pattern not found')
 close_block=next_block+'''\n            IconButton(onClick = onClose, modifier = Modifier.size(GLYPH_SLOT)) {\n                Icon(Icons.Rounded.Close, contentDescription = "إيقاف وإغلاق الأغنية", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(24.dp))\n            }'''
 s=s.replace(next_block,close_block,1); p.write_text(s)
 p=Path('app/src/main/java/com/music/bitchord/MainActivity.kt'); s=p.read_text(); marker='''        Column(\n            modifier = Modifier\n                .align(Alignment.BottomCenter)'''; stop='''        val stopAndClosePlayback: () -> Unit = {\n            controller?.stop()\n            controller?.clearMediaItems()\n            showNowPlaying = false\n        }\n\n'''
 if marker not in s: raise RuntimeError('V6 bottom marker not found')
 s=s.replace(marker,stop+marker,1)
 s=s.replace('                    onNext = { controller?.seekToNextMediaItem() },\n                    onExpand = { showNowPlaying = true },','                    onNext = { controller?.seekToNextMediaItem() },\n                    onClose = stopAndClosePlayback,\n                    onExpand = { showNowPlaying = true },',1)
 s=s.replace('                    onOpenMenu = { songActions = song },','                    onClosePlayback = stopAndClosePlayback,\n                    onOpenMenu = { songActions = song },',1); p.write_text(s)
finally: os.chdir(old_cwd)
print('BitChord H9 V6 patch applied')
