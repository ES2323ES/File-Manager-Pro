package com.aishhatorah.filemanagerpro;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * סעיפים ח, ט, י, יב, יג, יט: מנהל ניגון אודיו מתקדם הפועל גם ברקע ביציאה מהנגן,
 * כולל תמיכה בתיקיית שירים (קדימה/אחורה), מצבי חזרה, שליטה במהירות השמעה,
 * חילוץ עטיפת שיר מוטמעת והצגת התראה פעילה בבר ההתראות העליון.
 */
public class AudioPlayerManager {

    public static final int REPEAT_OFF = 0;
    public static final int REPEAT_ONE = 1;
    public static final int REPEAT_FOLDER = 2;

    public interface PlaybackListener {
        void onPlaybackStateChanged();
    }

    private static final AudioPlayerManager INSTANCE = new AudioPlayerManager();

    public static AudioPlayerManager getInstance() {
        return INSTANCE;
    }

    private final Set<PlaybackListener> listeners = new CopyOnWriteArraySet<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<File> playlist = new ArrayList<>();

    private MediaPlayer mediaPlayer;
    private File currentFile;
    private int currentIndex = -1;
    private Bitmap currentArtwork;
    private float playbackSpeed = 1.0f;
    private int repeatMode = REPEAT_OFF;
    private boolean prepared = false;

    private AudioPlayerManager() {
    }

    public void addListener(PlaybackListener l) {
        if (l != null) listeners.add(l);
    }

    public void removeListener(PlaybackListener l) {
        if (l != null) listeners.remove(l);
    }

    private void notifyListeners() {
        mainHandler.post(() -> {
            for (PlaybackListener l : listeners) {
                try {
                    l.onPlaybackStateChanged();
                } catch (Exception ignored) {
                }
            }
        });
    }

    public boolean hasActiveSession() {
        return currentFile != null && mediaPlayer != null && prepared;
    }

    public boolean isPlaying() {
        try {
            return mediaPlayer != null && prepared && mediaPlayer.isPlaying();
        } catch (Exception e) {
            return false;
        }
    }

    public File getCurrentFile() {
        return currentFile;
    }

    public Bitmap getCurrentArtwork() {
        return currentArtwork;
    }

    public float getPlaybackSpeed() {
        return playbackSpeed;
    }

    public int getRepeatMode() {
        return repeatMode;
    }

    public int getDurationMs() {
        try {
            return (mediaPlayer != null && prepared) ? Math.max(1, mediaPlayer.getDuration()) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public int getCurrentPositionMs() {
        try {
            return (mediaPlayer != null && prepared) ? Math.max(0, mediaPlayer.getCurrentPosition()) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public void playOrAttachFile(Context context, File file) {
        if (file == null || !file.exists()) return;
        Context appCtx = context.getApplicationContext();

        // אם אותו שיר בדיוק כבר נטען ברקע, לא נתחיל אותו מחדש אלא נמשיך מאותה נקודה
        if (currentFile != null
                && currentFile.getAbsolutePath().equals(file.getAbsolutePath())
                && mediaPlayer != null
                && prepared) {
            if (!isPlaying()) {
                startInternal(appCtx);
            } else {
                syncForegroundService(appCtx);
                notifyListeners();
            }
            return;
        }

        buildFolderPlaylist(file);
        loadAndStartTrack(appCtx, file);
    }

    private void buildFolderPlaylist(File target) {
        playlist.clear();
        currentIndex = 0;
        File parent = target.getParentFile();
        if (parent != null && parent.exists() && parent.isDirectory()) {
            File[] siblings = parent.listFiles();
            if (siblings != null) {
                for (File f : siblings) {
                    if (f.isFile() && isAudioFileName(f.getName())) {
                        playlist.add(f);
                    }
                }
            }
        }
        if (playlist.isEmpty()) {
            playlist.add(target);
        } else {
            Collections.sort(playlist, (a, b) -> StorageUtils.compareHebrewFirst(a.getName(), b.getName()));
            for (int i = 0; i < playlist.size(); i++) {
                if (playlist.get(i).getAbsolutePath().equals(target.getAbsolutePath())) {
                    currentIndex = i;
                    break;
                }
            }
        }
    }

    private void loadAndStartTrack(Context appCtx, File file) {
        releaseMediaPlayerOnly();
        currentFile = file;
        Bitmap rawArt = FileThumbnailLoader.extractAudioEmbeddedArt(file, 320);
        currentArtwork = rawArt != null ? FileThumbnailLoader.createRoundedSquareBitmap(rawArt, 320, 42f) : null;

        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(file.getAbsolutePath());
            mediaPlayer.prepare();
            prepared = true;

            applySpeedInternal(true);

            mediaPlayer.setOnCompletionListener(mp -> handleTrackCompleted(appCtx));
            syncForegroundService(appCtx);
            notifyListeners();
        } catch (Exception e) {
            releaseMediaPlayerOnly();
            notifyListeners();
        }
    }

    private void handleTrackCompleted(Context appCtx) {
        if (repeatMode == REPEAT_ONE && currentFile != null) {
            try {
                if (mediaPlayer != null && prepared) {
                    mediaPlayer.seekTo(0);
                    applySpeedInternal(true);
                    syncForegroundService(appCtx);
                    notifyListeners();
                    return;
                }
            } catch (Exception ignored) {
            }
            loadAndStartTrack(appCtx, currentFile);
            return;
        }

        if (repeatMode == REPEAT_FOLDER && !playlist.isEmpty()) {
            currentIndex = (currentIndex + 1) % playlist.size();
            loadAndStartTrack(appCtx, playlist.get(currentIndex));
            return;
        }

        // ללא חזרה: אם יש שיר נוסף אחריו בתיקייה נעצור בסיום השיר הנוכחי ונאפס להתחלה
        syncForegroundService(appCtx);
        notifyListeners();
    }

    private void startInternal(Context appCtx) {
        if (mediaPlayer == null || !prepared) return;
        try {
            applySpeedInternal(true);
            syncForegroundService(appCtx);
            notifyListeners();
        } catch (Exception ignored) {
        }
    }

    private void applySpeedInternal(boolean shouldPlay) {
        if (mediaPlayer == null || !prepared) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PlaybackParams params = mediaPlayer.getPlaybackParams();
                if (params == null) params = new PlaybackParams();
                params.setSpeed(playbackSpeed);
                mediaPlayer.setPlaybackParams(params);
                if (!shouldPlay && mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();
                } else if (shouldPlay && !mediaPlayer.isPlaying()) {
                    mediaPlayer.start();
                }
            } else if (shouldPlay) {
                mediaPlayer.start();
            }
        } catch (Exception e) {
            try {
                if (shouldPlay && !mediaPlayer.isPlaying()) mediaPlayer.start();
            } catch (Exception ignored) {
            }
        }
    }

    public void setPlaybackSpeed(Context context, float speed) {
        this.playbackSpeed = Math.max(0.25f, Math.min(3.0f, speed));
        boolean wasPlaying = isPlaying();
        applySpeedInternal(wasPlaying);
        if (context != null) {
            syncForegroundService(context.getApplicationContext());
        }
        notifyListeners();
    }

    public void setRepeatMode(int mode) {
        this.repeatMode = mode;
        notifyListeners();
    }

    public int cycleRepeatMode() {
        repeatMode = (repeatMode + 1) % 3;
        notifyListeners();
        return repeatMode;
    }

    public void togglePlayPause(Context context) {
        if (mediaPlayer == null || !prepared) return;
        Context appCtx = context.getApplicationContext();
        try {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
            } else {
                applySpeedInternal(true);
            }
            syncForegroundService(appCtx);
            notifyListeners();
        } catch (Exception ignored) {
        }
    }

    public void seekTo(int positionMs) {
        if (mediaPlayer == null || !prepared) return;
        try {
            int clamped = Math.max(0, Math.min(getDurationMs(), positionMs));
            mediaPlayer.seekTo(clamped);
            notifyListeners();
        } catch (Exception ignored) {
        }
    }

    public void seekBy(int deltaMs) {
        seekTo(getCurrentPositionMs() + deltaMs);
    }

    public void playNext(Context context) {
        if (playlist.isEmpty() || currentFile == null) return;
        currentIndex = (currentIndex + 1) % playlist.size();
        loadAndStartTrack(context.getApplicationContext(), playlist.get(currentIndex));
    }

    public void playPrevious(Context context) {
        if (playlist.isEmpty() || currentFile == null) return;
        if (getCurrentPositionMs() > 4000) {
            seekTo(0);
            return;
        }
        currentIndex = (currentIndex - 1 + playlist.size()) % playlist.size();
        loadAndStartTrack(context.getApplicationContext(), playlist.get(currentIndex));
    }

    public void stopAndDismiss(Context context) {
        releaseMediaPlayerOnly();
        currentFile = null;
        currentArtwork = null;
        if (context != null) {
            Intent stopIntent = new Intent(context.getApplicationContext(), AudioPlaybackService.class);
            stopIntent.setAction(AudioPlaybackService.ACTION_STOP_SERVICE);
            try {
                context.getApplicationContext().stopService(stopIntent);
            } catch (Exception ignored) {
            }
        }
        notifyListeners();
    }

    private void releaseMediaPlayerOnly() {
        prepared = false;
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
            } catch (Exception ignored) {
            }
            try {
                mediaPlayer.release();
            } catch (Exception ignored) {
            }
            mediaPlayer = null;
        }
    }

    private void syncForegroundService(Context appCtx) {
        try {
            Intent svc = new Intent(appCtx, AudioPlaybackService.class);
            svc.setAction(AudioPlaybackService.ACTION_SYNC);
            if (isPlaying()) {
                ContextCompat.startForegroundService(appCtx, svc);
            } else {
                appCtx.startService(svc);
            }
        } catch (Exception ignored) {
        }
    }

    public static boolean isAudioFileName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".mp3")
                || lower.endsWith(".m4a")
                || lower.endsWith(".wav")
                || lower.endsWith(".aac")
                || lower.endsWith(".flac")
                || lower.endsWith(".ogg");
    }
}
