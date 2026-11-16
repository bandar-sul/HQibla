package sa.h9.qibla;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;

public final class SoundManager {
    private static final String TAG = "H9SoundManager";

    public static final int SOUND_MODE_COMBINED = 0;     // Default / Top: تكبيرات الأذان (تكبيرتين فقط - صوت الحرم المكي الطبيعي)
    public static final int SOUND_MODE_VOICE_ONLY = 1;    // نغمة دخول وقت الصلاة الهادئة + تكبيرتين
    public static final int SOUND_MODE_TAKBEER_ONLY = 2;  // تكبيرات الحرم المكي الكاملة (4 تكبيرات)
    public static final int SOUND_MODE_CHIME = 3;         // نغمة دخول وقت الصلاة الهادئة فقط
    public static final int SOUND_MODE_MUTED = 4;         // كتم الصوت (إشعار مرئي فقط)
    public static final int SOUND_MODE_CUSTOM = 5;        // أذان مخصص من ملف صوتي مرفوع من الجهاز

    private static final String UI_PREFS = "h9_qibla_ui";
    private static final String KEY_CUSTOM_ADHAN_NAME = "custom_adhan_filename";
    private static final String KEY_CUSTOM_ADHAN_DURATION_MS = "custom_adhan_duration_ms";
    private static final String CUSTOM_ADHAN_FILE = "custom_adhan_audio.bin";
    private static final long MAX_CUSTOM_AUDIO_BYTES = 30L * 1024L * 1024L; // 30 MB max

    private static MediaPlayer currentPlayer;
    private static int currentAudioRes = 0;
    private static AudioManager audioManager;
    private static AudioFocusRequest audioFocusRequest;
    private static AudioManager.OnAudioFocusChangeListener focusChangeListener;
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private SoundManager() { }

    public static File getCustomAudioFile(Context context) {
        if (context == null) return null;
        return new File(context.getApplicationContext().getFilesDir(), CUSTOM_ADHAN_FILE);
    }

    public static boolean hasCustomAudio(Context context) {
        File f = getCustomAudioFile(context);
        return f != null && f.exists() && f.isFile() && f.length() > 512L;
    }

    public static String getCustomAudioDisplayName(Context context) {
        if (context == null) return "";
        return context.getApplicationContext()
                .getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)
                .getString(KEY_CUSTOM_ADHAN_NAME, "");
    }

    public static String getCustomAudioDurationFormatted(Context context) {
        if (context == null) return "";
        long ms = context.getApplicationContext()
                .getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_CUSTOM_ADHAN_DURATION_MS, 0L);
        if (ms <= 0L) return "";
        long totalSec = Math.max(1L, ms / 1000L);
        long mins = totalSec / 60L;
        long secs = totalSec % 60L;
        return String.format(Locale.US, "%02d:%02d", mins, secs);
    }

    public static synchronized boolean importCustomAudioFromUri(Context context, Uri uri) {
        if (context == null || uri == null) return false;
        Context appCtx = context.getApplicationContext();
        ContentResolver cr = appCtx.getContentResolver();
        if (cr == null) return false;

        String displayName = resolveDisplayName(cr, uri);
        File tmpFile = new File(appCtx.getFilesDir(), "custom_adhan_tmp.bin");
        File targetFile = getCustomAudioFile(appCtx);
        if (targetFile == null) return false;

        InputStream in = null;
        FileOutputStream out = null;
        try {
            in = cr.openInputStream(uri);
            if (in == null) return false;
            out = new FileOutputStream(tmpFile, false);
            byte[] buf = new byte[16384];
            long total = 0L;
            int read;
            while ((read = in.read(buf)) != -1) {
                total += read;
                if (total > MAX_CUSTOM_AUDIO_BYTES) {
                    throw new IllegalStateException("Audio file exceeds 30MB limit");
                }
                out.write(buf, 0, read);
            }
            out.flush();
        } catch (Throwable t) {
            Log.e(TAG, "Failed copying custom audio URI", t);
            try { if (tmpFile.exists()) tmpFile.delete(); } catch (Throwable ignored) { }
            return false;
        } finally {
            try { if (in != null) in.close(); } catch (Throwable ignored) { }
            try { if (out != null) out.close(); } catch (Throwable ignored) { }
        }

        if (!tmpFile.exists() || tmpFile.length() < 512L) {
            try { if (tmpFile.exists()) tmpFile.delete(); } catch (Throwable ignored) { }
            return false;
        }

        // Validate that Android MediaPlayer can actually decode this audio file and read its duration
        long durationMs = 0L;
        MediaPlayer probe = new MediaPlayer();
        try {
            probe.setDataSource(tmpFile.getAbsolutePath());
            probe.prepare();
            durationMs = Math.max(0, probe.getDuration());
        } catch (Throwable t) {
            Log.e(TAG, "Selected file is not a valid playable audio track", t);
            try { tmpFile.delete(); } catch (Throwable ignored) { }
            return false;
        } finally {
            try { probe.release(); } catch (Throwable ignored) { }
        }

        // Replace any existing custom file safely
        if (isPlaying() && currentAudioRes == SOUND_MODE_CUSTOM) {
            stop();
        }
        try {
            if (targetFile.exists()) {
                targetFile.delete();
            }
            if (!tmpFile.renameTo(targetFile)) {
                Log.e(TAG, "Failed renaming temp audio file");
                tmpFile.delete();
                return false;
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error finalizing custom audio file", t);
            try { tmpFile.delete(); } catch (Throwable ignored) { }
            return false;
        }

        appCtx.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_CUSTOM_ADHAN_NAME, displayName)
                .putLong(KEY_CUSTOM_ADHAN_DURATION_MS, durationMs)
                .putInt("adhan_sound_mode", SOUND_MODE_CUSTOM)
                .putBoolean("adhan_sound_enabled", true)
                .apply();
        return true;
    }

    public static synchronized void clearCustomAudio(Context context) {
        if (context == null) return;
        Context appCtx = context.getApplicationContext();
        if (isPlaying() && currentAudioRes == SOUND_MODE_CUSTOM) {
            stop();
        }
        try {
            File f = getCustomAudioFile(appCtx);
            if (f != null && f.exists()) f.delete();
        } catch (Throwable ignored) { }
        SharedPreferences prefs = appCtx.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor ed = prefs.edit()
                .remove(KEY_CUSTOM_ADHAN_NAME)
                .remove(KEY_CUSTOM_ADHAN_DURATION_MS);
        if (prefs.getInt("adhan_sound_mode", SOUND_MODE_COMBINED) == SOUND_MODE_CUSTOM) {
            ed.putInt("adhan_sound_mode", SOUND_MODE_COMBINED);
        }
        ed.apply();
    }

    private static String resolveDisplayName(ContentResolver cr, Uri uri) {
        String name = null;
        Cursor cursor = null;
        try {
            cursor = cr.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) name = cursor.getString(idx);
            }
        } catch (Throwable ignored) {
        } finally {
            try { if (cursor != null) cursor.close(); } catch (Throwable ignored) { }
        }
        if (name == null || name.trim().isEmpty()) {
            String seg = uri.getLastPathSegment();
            if (seg != null) {
                int slash = seg.lastIndexOf('/');
                if (slash >= 0 && slash + 1 < seg.length()) seg = seg.substring(slash + 1);
                int colon = seg.lastIndexOf(':');
                if (colon >= 0 && colon + 1 < seg.length()) seg = seg.substring(colon + 1);
                name = seg;
            }
        }
        if (name == null || name.trim().isEmpty()) {
            name = "custom_adhan.mp3";
        }
        return name.trim();
    }

    public static synchronized boolean isPlaying() {
        try {
            return currentPlayer != null && currentPlayer.isPlaying();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static synchronized int getCurrentAudioRes() {
        return isPlaying() ? currentAudioRes : 0;
    }

    public static synchronized void stop() {
        if (currentPlayer != null) {
            try {
                if (currentPlayer.isPlaying()) {
                    currentPlayer.stop();
                }
            } catch (Throwable ignored) { }
            try {
                currentPlayer.release();
            } catch (Throwable ignored) { }
            currentPlayer = null;
            currentAudioRes = 0;
        }
        abandonFocus();
    }

    private static void abandonFocus() {
        try {
            if (audioManager != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
                    audioManager.abandonAudioFocusRequest(audioFocusRequest);
                    audioFocusRequest = null;
                } else if (focusChangeListener != null) {
                    audioManager.abandonAudioFocus(focusChangeListener);
                }
            }
        } catch (Throwable ignored) { }
        focusChangeListener = null;
    }

    public static boolean isPhoneCallActive(Context context) {
        if (context == null) return false;
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                int mode = am.getMode();
                return mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION;
            }
        } catch (Throwable ignored) { }
        return false;
    }

    private static long lastPrayerAlertPlayedAt = 0L;

    public static synchronized void playPrayerAlert(Context context, int soundMode, Runnable onComplete) {
        playPrayerAlert(context, soundMode, false, onComplete);
    }

    public static synchronized void playPrayerAlert(Context context, int soundMode, boolean forceTest, Runnable onComplete) {
        if (soundMode == SOUND_MODE_MUTED) {
            if (onComplete != null) mainHandler.post(onComplete);
            return;
        }

        long now = System.currentTimeMillis();
        if (!forceTest && (isPlaying() || (now - lastPrayerAlertPlayedAt < 40000L))) {
            Log.w(TAG, "playPrayerAlert: Suppressing duplicate alert playback (last triggered " + (now - lastPrayerAlertPlayedAt) + "ms ago)");
            if (onComplete != null) mainHandler.post(onComplete);
            return;
        }
        lastPrayerAlertPlayedAt = now;

        if (soundMode == SOUND_MODE_CUSTOM && hasCustomAudio(context)) {
            File custom = getCustomAudioFile(context);
            if (custom != null) {
                playMedia(context, 0, custom.getAbsolutePath(), SOUND_MODE_CUSTOM, onComplete);
                return;
            }
        }

        int resId;
        if (soundMode == SOUND_MODE_VOICE_ONLY) {
            // Calm prayer entrance chime + 2 Makkah Takbeers
            resId = R.raw.adhan;
        } else if (soundMode == SOUND_MODE_TAKBEER_ONLY) {
            // Full Makkah Haram Takbeerat (4 Takbeers)
            resId = R.raw.adhan_takbeerat;
        } else if (soundMode == SOUND_MODE_CHIME) {
            // Calm prayer entrance chime only
            resId = R.raw.prayer_announcement;
        } else {
            // SOUND_MODE_COMBINED (Default & Top #1): Two Makkah Haram Takbeers only
            resId = R.raw.takbeerat_two;
        }

        playRawAudio(context, resId, onComplete);
    }

    public static synchronized void playAdhan(Context context, Runnable onComplete) {
        playRawAudio(context, R.raw.takbeerat_two, onComplete);
    }

    public static synchronized void playRawAudio(Context context, int rawResId, Runnable onComplete) {
        playMedia(context, rawResId, null, rawResId, onComplete);
    }

    private static synchronized void playMedia(Context context, int rawResId, String filePath, int trackingId, Runnable onComplete) {
        if (context == null) {
            if (onComplete != null) mainHandler.post(onComplete);
            return;
        }
        if (isPhoneCallActive(context)) {
            Log.i(TAG, "Phone call in progress, skipping audio playback.");
            if (onComplete != null) mainHandler.post(onComplete);
            return;
        }

        stop();

        try {
            Context appCtx = context.getApplicationContext();
            audioManager = (AudioManager) appCtx.getSystemService(Context.AUDIO_SERVICE);

            AudioAttributes attrs = null;
            try {
                attrs = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                        .build();
            } catch (Throwable t) {
                Log.w(TAG, "Failed creating media AudioAttributes", t);
            }

            focusChangeListener = focusChange -> {
                if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                    stop();
                }
            };

            if (audioManager != null) {
                try {
                    // Check and ensure volume is audible (not muted) on car speakers, bluetooth, and phone
                    int musicVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                    int musicMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                    if (musicVol <= 0 && musicMax > 0) {
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, Math.max(1, (int)(musicMax * 0.75f)), 0);
                    }
                    int notifVol = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION);
                    int notifMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION);
                    if (notifVol <= 0 && notifMax > 0) {
                        audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, Math.max(1, (int)(notifMax * 0.70f)), 0);
                    }
                } catch (Throwable ignored) { }

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && attrs != null) {
                        audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                                .setAudioAttributes(attrs)
                                .setAcceptsDelayedFocusGain(true)
                                .setWillPauseWhenDucked(false)
                                .setOnAudioFocusChangeListener(focusChangeListener)
                                .build();
                        audioManager.requestAudioFocus(audioFocusRequest);
                    } else {
                        audioManager.requestAudioFocus(focusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "Audio focus request warning (non-fatal): " + t.getMessage());
                }
            }

            MediaPlayer player = new MediaPlayer();
            if (attrs != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                player.setAudioAttributes(attrs);
            } else {
                player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            }

            if (filePath != null && !filePath.isEmpty()) {
                try {
                    player.setDataSource(filePath);
                    player.prepare();
                } catch (Throwable t) {
                    Log.w(TAG, "Custom audio file playback failed, falling back to takbeerat_two", t);
                    try { player.release(); } catch (Throwable ignored) { }
                    player = MediaPlayer.create(appCtx, R.raw.takbeerat_two);
                }
            } else {
                android.content.res.AssetFileDescriptor afd = null;
                try {
                    afd = appCtx.getResources().openRawResourceFd(rawResId);
                } catch (Throwable t) {
                    Log.w(TAG, "openRawResourceFd failed, using fallback", t);
                }

                if (afd != null) {
                    try {
                        player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                        afd.close();
                        player.prepare();
                    } catch (Throwable t) {
                        Log.w(TAG, "player.prepare with afd failed, falling back to MediaPlayer.create", t);
                        try { player.release(); } catch (Throwable ignored) { }
                        player = MediaPlayer.create(appCtx, rawResId);
                    }
                } else {
                    player = MediaPlayer.create(appCtx, rawResId);
                }
            }

            if (player == null) {
                Log.e(TAG, "Unable to instantiate MediaPlayer for trackingId " + trackingId);
                abandonFocus();
                if (onComplete != null) mainHandler.post(onComplete);
                return;
            }

            try {
                player.setWakeMode(appCtx, android.os.PowerManager.PARTIAL_WAKE_LOCK);
            } catch (Throwable ignored) { }

            player.setVolume(1.0f, 1.0f);

            currentPlayer = player;
            currentAudioRes = trackingId;

            player.setOnCompletionListener(mp -> {
                synchronized (SoundManager.class) {
                    if (currentPlayer == mp) {
                        try { mp.release(); } catch (Throwable ignored) { }
                        currentPlayer = null;
                        currentAudioRes = 0;
                    }
                }
                abandonFocus();
                if (onComplete != null) {
                    mainHandler.post(onComplete);
                }
            });

            player.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "MediaPlayer error: " + what + ", " + extra);
                stop();
                if (onComplete != null) {
                    mainHandler.post(onComplete);
                }
                return true;
            });

            player.start();
        } catch (Throwable t) {
            Log.e(TAG, "Exception starting audio", t);
            stop();
            if (onComplete != null) {
                mainHandler.post(onComplete);
            }
        }
    }
}
