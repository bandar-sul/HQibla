from pathlib import Path
import os, runpy, re

ROOT = Path(__file__).resolve().parents[1]
BITCHORD = ROOT / "bitchord"

# Build on the proven V10 patch chain.
runpy.run_path(str(ROOT / "scripts/prepare_bitchord_v10.py"), run_name="__main__")

old_cwd = Path.cwd()
os.chdir(BITCHORD)
try:
    # Version.
    p = Path("app/build.gradle.kts")
    s = p.read_text().replace('versionName = "1.4.1-H9-ar10"', 'versionName = "1.4.1-H9-ar11"')
    p.write_text(s)

    # H9 album/detail layout: shorter cinematic hero so metadata/actions and songs are visible immediately.
    p = Path("app/src/main/java/com/music/bitchord/ui/screens/DetailScreen.kt")
    s = p.read_text()
    s = s.replace('minOf(detailConfig.screenHeightDp.dp * 0.31f, 340.dp)', 'minOf(detailConfig.screenHeightDp.dp * 0.27f, 300.dp)', 1)
    s = s.replace('private const val SLEEVE_FRACTION = 0.80f', 'private const val SLEEVE_FRACTION = 0.72f', 1)
    s = s.replace('private val HEADER_DROP = 44.dp', 'private val HEADER_DROP = 24.dp', 1)
    p.write_text(s)

    # H9 Now Playing: landscape composition rather than an oversized phone canvas.
    p = Path("app/src/main/java/com/music/bitchord/ui/player/NowPlayingScreen.kt")
    s = p.read_text()
    s = s.replace('(if (isH9Player) Modifier.height(720.dp) else Modifier.weight(1f))', '(if (isH9Player) Modifier.height(560.dp) else Modifier.weight(1f))', 1)
    s = s.replace('minOf(maxWidth * 0.72f, 1180.dp)', 'minOf(maxWidth * 0.60f, 980.dp)', 1)
    s = s.replace('1420.dp else PLAYER_MAX_WIDTH', '1540.dp else PLAYER_MAX_WIDTH', 1)
    p.write_text(s)

    # Mini player: compact car strip aligned with the bottom navigation instead of spanning most of the screen.
    p = Path("app/src/main/java/com/music/bitchord/MainActivity.kt")
    s = p.read_text()
    s = s.replace('Modifier.fillMaxWidth(if (isH9Wide) 0.68f else 1f)', 'Modifier.fillMaxWidth(if (isH9Wide) 0.58f else 1f)', 1)
    s = s.replace('val h9SideInset = if (isH9Wide) 88.dp else 0.dp', 'val h9SideInset = if (isH9Wide) 96.dp else 0.dp', 1)
    p.write_text(s)

    p = Path("app/src/main/java/com/music/bitchord/ui/components/MiniPlayer.kt")
    s = p.read_text()
    s = s.replace('private val ROW_PADDING = 4.dp', 'private val ROW_PADDING = 3.dp', 1)
    s = s.replace('private val GLYPH_SLOT = 36.dp', 'private val GLYPH_SLOT = 34.dp', 1)
    p.write_text(s)

    # Faster first paint: Home first; secondary network/account/update work is deferred further.
    p = Path("app/src/main/java/com/music/bitchord/ui/MainViewModel.kt")
    s = p.read_text()
    s = s.replace('delay(1200)', 'delay(2200)', 1)
    s = s.replace('delay(600)', 'delay(900)', 1)
    s = s.replace('delay(5000); AppUpdateChecker.check()', 'delay(7000); AppUpdateChecker.check()', 1)
    p.write_text(s)

    # Persistent Arabic resource catalogue for H9. Current hard-coded Kotlin strings remain translated by V10,
    # while these resources provide a stable home for future migrations instead of losing translations per release.
    values_ar = Path('app/src/main/res/values-ar')
    values_ar.mkdir(parents=True, exist_ok=True)
    strings = '''<resources>
    <string name="app_name">BitChord H9</string>
    <string name="playback_channel_name">قيد التشغيل الآن</string>
    <string name="h9_settings">الإعدادات</string>
    <string name="h9_account_integrations">الحساب والتكاملات</string>
    <string name="h9_audio_quality">جودة الصوت</string>
    <string name="h9_lossless">صوت عالي الجودة / بدون فقدان</string>
    <string name="h9_wifi">عبر Wi-Fi</string>
    <string name="h9_mobile_data">عبر بيانات الجوال</string>
    <string name="h9_playback">التشغيل</string>
    <string name="h9_crossfade">تداخل الصوت</string>
    <string name="h9_automix">المزج التلقائي</string>
    <string name="h9_skip_silence">تخطي الصمت</string>
    <string name="h9_spatial_audio">الصوت المكاني</string>
    <string name="h9_equalizer">معادل الصوت</string>
    <string name="h9_storage">التخزين</string>
    <string name="h9_close_player">إيقاف وإغلاق الأغنية</string>
</resources>
'''
    (values_ar / 'strings.xml').write_text(strings)

    # A few remaining common labels seen in settings/detail pages.
    extra = {
        'Top songs':'أفضل الأغاني',
        'Suggested':'مقترحة',
        'Singles & EPs':'الأغاني المنفردة والأسطوانات القصيرة',
        'Albums':'الألبومات',
        'Play all':'تشغيل الكل',
        'Shuffle play':'تشغيل عشوائي',
        'Add to library':'إضافة إلى المكتبة',
        'Remove from library':'إزالة من المكتبة',
        'Downloads':'التنزيلات',
        'Miscellaneous':'متفرقات',
        'In use':'قيد الاستخدام',
        'Off':'إيقاف',
        'On':'تشغيل',
    }
    for f in Path('app/src/main/java').rglob('*.kt'):
        x=f.read_text(); y=x
        for en, ar in sorted(extra.items(), key=lambda kv:-len(kv[0])):
            y=y.replace(f'"{en}"', f'"{ar}"')
        if y != x:
            f.write_text(y)
finally:
    os.chdir(old_cwd)

print('BitChord H9 V11 patch applied')
