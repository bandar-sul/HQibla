package sa.h9.qibla;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.util.Log;

public final class SoundManager {
    private static final String TAG = "H9SoundManager";
    private static MediaPlayer currentPlayer;
    private static int currentAudioRes = 0;
    private static AudioManager audioManager;
    private static AudioFocusRequest audioFocusRequest;
    private static AudioManager.OnAudioFocusChangeListener focusChangeListener;

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

    public static synchronized void playAdhan(Context context, Runnable onComplete) {
        playRawAudio(context, R.raw.adhan, onComplete);
    }

    public static synchronized void playRawAudio(Context context, int rawResId, Runnable onComplete) {
        if (context == null) return;
        if (isPhoneCallActive(context)) {
            Log.i(TAG, "Phone call in progress, skipping audio playback.");
            return;
        }

        stop();

        try {
            Context appCtx = context.getApplicationContext();
            audioManager = (AudioManager) appCtx.getSystemService(Context.AUDIO_SERVICE);

            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();

            // Request AudioFocus so vehicle DSP / Head Unit routes sound to car speakers even when radio is OFF
            focusChangeListener = focusChange -> {
                if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                    stop();
                }
            };

            if (audioManager != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                            .setAudioAttributes(attrs)
                            .setOnAudioFocusChangeListener(focusChangeListener)
                            .build();
                    audioManager.requestAudioFocus(audioFocusRequest);
                } else {
                    audioManager.requestAudioFocus(focusChangeListener, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
                }
            }

            MediaPlayer player = new MediaPlayer();
            player.setAudioAttributes(attrs);
            // Fallback for older vehicle HALs requiring explicit stream type
            try {
                player.setAudioStreamType(AudioManager.STREAM_ALARM);
            } catch (Throwable ignored) { }

            AssetFileDescriptor afd = appCtx.getResources().openRawResourceFd(rawResId);
            if (afd == null) {
                Log.w(TAG, "Failed to open raw resource: " + rawResId);
                abandonFocus();
                return;
            }
            player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();

            player.setVolume(1.0f, 1.0f);
            player.prepare();

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
                if (onComplete != null) onComplete.run();
            });

            player.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "MediaPlayer error: " + what + ", " + extra);
                stop();
                return true;
            });

            player.start();
        } catch (Throwable t) {
            Log.e(TAG, "Exception starting audio", t);
            stop();
        }
    }
}
