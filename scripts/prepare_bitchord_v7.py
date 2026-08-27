from pathlib import Path
import os, runpy
ROOT=Path(__file__).resolve().parents[1]; BITCHORD=ROOT/'bitchord'
runpy.run_path(str(ROOT/'scripts/prepare_bitchord_v6.py'),run_name='__main__')
old_cwd=Path.cwd(); os.chdir(BITCHORD)
try:
 p=Path('app/build.gradle.kts'); s=p.read_text().replace('versionName = "1.4.1-H9-ar6"','versionName = "1.4.1-H9-ar7"'); p.write_text(s)
 p=Path('app/src/main/java/com/music/bitchord/ui/player/NowPlayingScreen.kt'); s=p.read_text()
 if 'import androidx.compose.material3.IconButton' not in s: s=s.replace('import androidx.compose.material3.Icon\n','import androidx.compose.material3.Icon\nimport androidx.compose.material3.IconButton\n',1)
 old='''                modifier = Modifier\n                    .weight(1f)\n                    .widthIn(max = PLAYER_MAX_WIDTH)\n                    .fillMaxWidth()\n                    .padding(top = ART_BOX_TOP_PAD, bottom = 18.dp),'''
 new='''                modifier = (if (isH9Player) Modifier.height(520.dp) else Modifier.weight(1f))\n                    .widthIn(max = PLAYER_MAX_WIDTH)\n                    .fillMaxWidth()\n                    .padding(top = ART_BOX_TOP_PAD, bottom = if (isH9Player) 4.dp else 18.dp),'''
 if old not in s: raise RuntimeError('V7 artwork region pattern not found')
 s=s.replace(old,new,1)
 old='''                            val idle = artScale + (1f - artScale) * p\n                            scaleX = idle\n                            scaleY = idle\n                            translationX = swipeSettle * (1f - p)'''
 new='''                            val idle = artScale + (1f - artScale) * p\n                            val h9WideScale = if (isH9Player) 1f + (0.45f * (1f - p)) else 1f\n                            scaleX = idle * h9WideScale\n                            scaleY = idle\n                            alpha = if (isH9Player && p < 0.5f) 0.92f else 1f\n                            translationX = swipeSettle * (1f - p)'''
 if old not in s: raise RuntimeError('V7 artwork graphics pattern not found')
 s=s.replace(old,new,1)
 down_old='''                    Icon(\n                        Icons.AutoMirrored.Rounded.VolumeDown,\n                        contentDescription = null,\n                        tint = Color.White.copy(alpha = 0.5f),\n                        modifier = Modifier.size(20.dp),\n                    )'''
 down_new='''                    IconButton(\n                        onClick = { audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_PLAY_SOUND); systemVolume = (audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0).toFloat() / maxVolume },\n                        modifier = Modifier.size(44.dp),\n                    ) { Icon(Icons.AutoMirrored.Rounded.VolumeDown, contentDescription = "خفض الصوت", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(24.dp)) }'''
 up_old='''                    Icon(\n                        Icons.AutoMirrored.Rounded.VolumeUp,\n                        contentDescription = null,\n                        tint = Color.White.copy(alpha = 0.5f),\n                        modifier = Modifier.size(20.dp),\n                    )'''
 up_new='''                    IconButton(\n                        onClick = { audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_PLAY_SOUND); systemVolume = (audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0).toFloat() / maxVolume },\n                        modifier = Modifier.size(44.dp),\n                    ) { Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = "رفع الصوت", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(24.dp)) }'''
 if down_old not in s or up_old not in s: raise RuntimeError('V7 volume patterns not found')
 s=s.replace(down_old,down_new,1).replace(up_old,up_new,1); p.write_text(s)
 p=Path('app/src/main/java/com/music/bitchord/MainActivity.kt'); s=p.read_text()
 old='''                    onClose = stopAndClosePlayback,\n                    onExpand = { showNowPlaying = true },\n                    modifier = Modifier.fillMaxWidth(),'''; new='''                    onClose = stopAndClosePlayback,\n                    onExpand = { showNowPlaying = true },\n                    modifier = Modifier.fillMaxWidth(if (isH9Wide) 0.68f else 1f),'''
 if old not in s: raise RuntimeError('V7 MiniPlayer call pattern not found')
 s=s.replace(old,new,1)
 old='''        applyH9Immersive()\n        enableEdgeToEdge()'''; new='''        applyH9Immersive()\n        window.decorView.setOnSystemUiVisibilityChangeListener { if (resources.configuration.screenWidthDp >= 900) window.decorView.postDelayed({ applyH9Immersive() }, 120L) }\n        enableEdgeToEdge()'''
 if old not in s: raise RuntimeError('V7 immersive pattern not found')
 s=s.replace(old,new,1)
 resume='''    override fun onResume() {\n        super.onResume()\n        applyH9Immersive()\n    }\n'''
 dispatch='''    override fun onResume() {\n        super.onResume()\n        applyH9Immersive()\n    }\n\n    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {\n        val code = event.keyCode\n        if (code == android.view.KeyEvent.KEYCODE_VOLUME_UP || code == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) {\n            if (event.action == android.view.KeyEvent.ACTION_DOWN) {\n                val audio = getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager\n                audio.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, if (code == android.view.KeyEvent.KEYCODE_VOLUME_UP) android.media.AudioManager.ADJUST_RAISE else android.media.AudioManager.ADJUST_LOWER, android.media.AudioManager.FLAG_SHOW_UI or android.media.AudioManager.FLAG_PLAY_SOUND)\n                applyH9Immersive()\n            }\n            return true\n        }\n        return super.dispatchKeyEvent(event)\n    }\n'''
 if resume not in s: raise RuntimeError('V7 onResume pattern not found')
 s=s.replace(resume,dispatch,1); p.write_text(s)
 p=Path('app/src/main/java/com/music/bitchord/ui/components/MiniPlayer.kt'); s=p.read_text().replace('private val ROW_PADDING = 5.dp','private val ROW_PADDING = 4.dp').replace('private val GLYPH_SLOT = 40.dp','private val GLYPH_SLOT = 36.dp'); p.write_text(s)
 p=Path('app/src/main/java/com/music/bitchord/ui/MainViewModel.kt'); s=p.read_text().replace('delay(450)','delay(1200)',1).replace('delay(250)','delay(600)',1).replace('delay(1800); AppUpdateChecker.check()','delay(5000); AppUpdateChecker.check()',1); p.write_text(s)
finally: os.chdir(old_cwd)
print('BitChord H9 V7 patch applied')
