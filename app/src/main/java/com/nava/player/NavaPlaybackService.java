package com.nava.player;

import android.media.AudioManager;
import android.media.audiofx.Equalizer;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

public final class NavaPlaybackService extends MediaSessionService {
    private ExoPlayer player;
    private MediaSession mediaSession;
    private static Equalizer equalizer;

    public static Equalizer getEqualizer() {
        return equalizer;
    }

    @Override
    @UnstableApi
    public void onCreate() {
        super.onCreate();
        player = new ExoPlayer.Builder(this).build();

        // A dedicated playback session lets Android's real Equalizer affect NAVA audio only.
        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audioManager != null) {
            int sessionId = audioManager.generateAudioSessionId();
            if (sessionId != AudioManager.ERROR) {
                player.setAudioSessionId(sessionId);
                try {
                    equalizer = new Equalizer(0, sessionId);
                    equalizer.setEnabled(true);
                } catch (RuntimeException | LinkageError ignored) {
                    equalizer = null;
                }
            }
        }

        mediaSession = new MediaSession.Builder(this, player).build();
    }

    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return mediaSession;
    }

    @Override
    public void onDestroy() {
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
        if (equalizer != null) {
            try { equalizer.release(); } catch (RuntimeException ignored) { }
            equalizer = null;
        }
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}
