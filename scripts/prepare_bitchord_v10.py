from pathlib import Path
import os, runpy

ROOT = Path(__file__).resolve().parents[1]
BITCHORD = ROOT / "bitchord"

# Preserve the complete V9 behavior and only adjust album sizing + full settings Arabic.
runpy.run_path(str(ROOT / "scripts/prepare_bitchord_v9.py"), run_name="__main__")

old_cwd = Path.cwd()
os.chdir(BITCHORD)
try:
    # Version marker.
    p = Path("app/build.gradle.kts")
    s = p.read_text().replace('versionName = "1.4.1-H9-ar9"', 'versionName = "1.4.1-H9-ar10"')
    p.write_text(s)

    # Album/detail page: V8 used 42% of screen height, still too tall on 1920x1080 H9.
    # Keep a cinematic wide hero but cap the vertical footprint to ~31% / 340dp.
    p = Path("app/src/main/java/com/music/bitchord/ui/screens/DetailScreen.kt")
    s = p.read_text()
    s = s.replace(
        'detailConfig.screenHeightDp.dp * 0.42f',
        'minOf(detailConfig.screenHeightDp.dp * 0.31f, 340.dp)',
        1,
    )
    p.write_text(s)

    # Restore / complete Arabic settings strings. V9 public build used a reduced translation table.
    full_ar = {
        'Account & integrations':'الحساب والتكاملات',
        'Not signed in':'لم يتم تسجيل الدخول',
        'Signed in':'تم تسجيل الدخول',
        'Audio quality':'جودة الصوت',
        'Lossless / HQ Audio':'صوت عالي الجودة / بدون فقدان',
        'Lossless is not working — make sure the app is downloaded from the official source':'الصوت بدون فقدان غير متاح — تأكد من تثبيت التطبيق من المصدر الرسمي',
        'On Wi-Fi':'عبر Wi-Fi',
        'On mobile data':'عبر بيانات الجوال',
        'In use':'قيد الاستخدام',
        'High':'عالية',
        'Automatic':'تلقائي',
        'Playback':'التشغيل',
        'Crossfade':'تداخل الصوت',
        'Blends one track into the next':'يمزج نهاية الأغنية مع بداية الأغنية التالية',
        'Automix [BETA]':'المزج التلقائي [تجريبي]',
        'Times and blends transitions automatically, no slider needed. May not work as expected in low-mid range devices.':'يحدد توقيت الانتقالات ويمزجها تلقائيًا دون الحاجة إلى شريط تحكم. قد لا يعمل بالشكل المتوقع على الأجهزة المتوسطة أو الضعيفة.',
        'Blends every transition, timed automatically from each track. Turn off if facing overheating or lag.':'يمزج الانتقالات تلقائيًا وفق كل أغنية. عطّله عند وجود حرارة أو بطء.',
        'Skip silence':'تخطي الصمت',
        'Trim gaps longer than a second':'تخطي فترات الصمت الأطول من ثانية',
        'Spatial audio':'الصوت المكاني',
        'Needs a device with Dolby Atmos':'يتطلب جهازًا يدعم Dolby Atmos',
        'Turn on Dolby Atmos to use it':'فعّل Dolby Atmos لاستخدامه',
        'Equalizer':'معادل الصوت',
        "Your device's system panel":'لوحة النظام في جهازك',
        'Show stats for nerds':'إظهار معلومات الصوت التقنية',
        'Codec, bitrate and sample rate on the player':'إظهار الترميز ومعدل البت ومعدل العينة داخل المشغل',
        'Animated cover art':'غلاف متحرك',
        'Reduce animation':'تقليل الحركة',
        'Reduce dynamic blur':'تقليل التمويه الديناميكي',
        'Full-screen cover art':'غلاف بملء الشاشة',
        'Synced lyrics':'كلمات متزامنة',
        'Lights up the words on the player as they\'re sung':'يضيء كلمات الأغنية في المشغل بالتزامن مع الغناء',
        'Lyrics sources':'مصادر كلمات الأغاني',
        'None — no lyrics will be fetched':'لا شيء — لن يتم جلب كلمات الأغاني',
        'Storage':'التخزين',
        'Song cache limit':'حد ذاكرة الأغاني',
        'Downloaded audio kept on disk for instant seeking and replays':'يحتفظ بالصوت المحمّل لتسريع التقديم وإعادة التشغيل',
        'Clear image cache':'مسح ذاكرة الصور',
        'Clear song cache':'مسح ذاكرة الأغاني',
        'Frees space used by album artwork':'يفرغ المساحة المستخدمة لصور الألبومات',
        'Frees space used by downloaded audio':'يفرغ المساحة المستخدمة للصوت المحمّل',
        'Image cache cleared':'تم مسح ذاكرة الصور',
        'Song cache cleared':'تم مسح ذاكرة الأغاني',
        'Miscellaneous':'متفرقات',
        'Hide volume bar':'إخفاء شريط الصوت',
        'Removes the volume slider from the main player':'يخفي شريط مستوى الصوت من المشغل الرئيسي',
        'Play next on swipe':'تشغيل التالي عند السحب',
        'Swiping a song plays it next':'سحب الأغنية يجعلها التالية في التشغيل',
        'Swiping a song adds it to the end of the queue when disabled':'عند تعطيله، يؤدي سحب الأغنية إلى إضافتها لنهاية قائمة الانتظار',
        'Stop music on close from recents':'إيقاف الموسيقى عند إغلاق التطبيق من التطبيقات الأخيرة',
        'Stops playback when swiped away from recent apps':'يوقف التشغيل عند إزالة التطبيق من قائمة التطبيقات الأخيرة',
        'When enabled, closing the app from the recent apps screen will also stop music playback.':'عند التفعيل، سيؤدي إغلاق التطبيق من التطبيقات الأخيرة إلى إيقاف تشغيل الموسيقى أيضًا.',
        'YouTube Music account':'حساب YouTube Music',
        'Tap to sign in with Google':'اضغط لتسجيل الدخول باستخدام Google',
        'Developer':'المطور',
        'API Token':'رمز API',
        'Password':'كلمة المرور',
        'Signing in...':'جارٍ تسجيل الدخول...',
        'Login failed':'فشل تسجيل الدخول',
        'Last.fm Login':'تسجيل الدخول إلى Last.fm',
        'ListenBrainz Token':'رمز ListenBrainz',
        'No system equalizer on this device':'لا يوجد معادل صوت في نظام هذا الجهاز',
        'Play a track first, then open the equalizer':'شغّل أغنية أولًا ثم افتح معادل الصوت',
        'No Dolby Atmos panel on this device':'لا توجد لوحة Dolby Atmos على هذا الجهاز',
        "Couldn't open Dolby Atmos settings":'تعذر فتح إعدادات Dolby Atmos',
        'Turn on to experience lossless music quality. Restart Required!':'فعّل للاستماع بجودة صوت بدون فقدان. يلزم إعادة التشغيل!',
        'Turn off if its playing a different version of the song or another song. Restart Required!':'عطّله إذا تم تشغيل نسخة مختلفة من الأغنية أو أغنية أخرى. يلزم إعادة التشغيل!',
        'Widens stereo tracks for a more immersive feel':'يوسّع نطاق الستيريو لتجربة صوت أكثر إحاطة',
        'Swaps frosted glass for solid fills across the app':'يستبدل تأثير الزجاج الضبابي بخلفيات ثابتة في التطبيق',
        "Freezes the main player's gradient instead of drifting":'يثبت تدرج خلفية المشغل بدل تحركه',
        'Settings':'الإعدادات',
        'Account':'الحساب',
        'Appearance':'المظهر',
        'About':'حول التطبيق',
        'Version':'الإصدار',
        'Sign out':'تسجيل الخروج',
        'Cancel':'إلغاء',
        'Done':'تم',
        'Close':'إغلاق',
    }

    for f in Path('app/src/main/java').rglob('*.kt'):
        x = f.read_text()
        y = x
        for en, ar in sorted(full_ar.items(), key=lambda kv: -len(kv[0])):
            y = y.replace(f'"{en}"', f'"{ar}"')
        if y != x:
            f.write_text(y)

    # SettingsSheet has several long descriptions split across multiple Kotlin string literals.
    p = Path('app/src/main/java/com/music/bitchord/ui/screens/SettingsSheet.kt')
    s = p.read_text()
    split_replacements = {
        '"Each connection keeps its own ceiling, so Wi-Fi can stay on "':'"لكل اتصال حد جودة مستقل، لذلك يمكن إبقاء Wi-Fi على "',
        '"High while mobile data is capped. High costs about "':'"عالية بينما يتم تقييد بيانات الجوال. الجودة العالية تستهلك تقريبًا "',
        '" of data. The ceiling applies to every "':'" من البيانات. ينطبق حد الجودة على كل "',
        '"source, and outranks the lossless preference."':'"المصادر، وله أولوية على خيار الصوت بدون فقدان."',
        '"Up to ${formatCacheSize(cacheLimitMb)} of downloaded audio kept on "':'"يتم الاحتفاظ بما يصل إلى ${formatCacheSize(cacheLimitMb)} من الصوت المحمّل على "',
        '"disk — that\'s a real chunk of most phones\' free storage."':'"التخزين — وقد يستهلك ذلك جزءًا ملحوظًا من المساحة الحرة."',
    }
    for old, new in split_replacements.items():
        s = s.replace(old, new)
    p.write_text(s)
finally:
    os.chdir(old_cwd)

print('BitChord H9 V10 patch applied')
