package sa.h9.qibla;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

public final class SoundManager {
    private static final String TAG = "H9SoundManager";

    public static final int SOUND_MODE_COMBINED = 0;     // دخل الآن وقت الصلاة (بصوت رجل) + التكبيرات
    public static final int SOUND_MODE_VOICE_ONLY = 1;    // دخل الآن وقت الصلاة (بصوت رجل فقط)
    public static final int SOUND_MODE_TAKBEER_ONLY = 2;  // تكبيرات الحرم المكي فقط
    public static final int SOUND_MODE_CHIME = 3;         // نغمة إسلامية هادئة وقورة (للسيارة)
    public static final int SOUND_MODE_MUTED = 4;         // كتم الصوت (إشعار مرئي فقط)

    private static MediaPlayer currentPlayer;
    private static int currentAudioRes = 0;
    private static AudioManager audioManager;
    private static AudioFocusRequest audioFocusRequest;
    private static AudioManager.OnAudioFocusChangeListener focusChangeListener;
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private SoundManager() { }

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

        int resId;
        if (soundMode == SOUND_MODE_VOICE_ONLY) {
            resId = R.raw.prayer_announcement;
        } else if (soundMode == SOUND_MODE_TAKBEER_ONLY) {
            resId = R.raw.adhan_takbeerat;
        } else if (soundMode == SOUND_MODE_CHIME) {
            resId = R.raw.prayer_chime;
        } else {
            // SOUND_MODE_COMBINED (Default): Male voice "دخل الآن وقت الصلاة" + Takbeerat
            resId = R.raw.adhan;
        }

        playRawAudio(context, resId, onComplete);
    }

    public static synchronized void playAdhan(Context context, Runnable onComplete) {
        playRawAudio(context, R.raw.adhan, onComplete);
    }

    public static synchronized void playRawAudio(Context context, int rawResId, Runnable onComplete) {
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

            if (player == null) {
                Log.e(TAG, "Unable to instantiate MediaPlayer for resource " + rawResId);
                abandonFocus();
                if (onComplete != null) mainHandler.post(onComplete);
                return;
            }

            try {
                player.setWakeMode(appCtx, android.os.PowerManager.PARTIAL_WAKE_LOCK);
            } catch (Throwable ignored) { }

            player.setVolume(1.0f, 1.0f);

            currentPlayer = player;
            currentAudioRes = rawResId;

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
