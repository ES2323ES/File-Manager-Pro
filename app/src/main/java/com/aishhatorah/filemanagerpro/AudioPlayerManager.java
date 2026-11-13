package com.aishhatorah.filemanagerpro;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * סעיפים 6, 22, 46: מנהל ניגון אודיו מתקדם הפועל ברקע,
 * כולל תור השמעה דינאמי (שינוי סדר, הוספה, הסרה), טיימר שינה, אקולייזר מובנה ובאס-בוסט.
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
    private Equalizer equalizer;
    private BassBoost bassBoost;
    private int selectedPresetIndex = -1;
    private short bassStrength = 0;
    private short[] customBandLevels = null;

    private File currentFile;
    private int currentIndex = -1;
    private Bitmap currentArtwork;
    private float playbackSpeed = 1.0f;
    private int repeatMode = REPEAT_OFF;
    private boolean prepared = false;

    private long sleepEndEpochMs = 0L;
    private final Runnable sleepCheckRunnable = new Runnable() {
        @Override
        public void run() {
            if (sleepEndEpochMs <= 0L) return;
            long rem = sleepEndEpochMs - System.currentTimeMillis();
            if (rem <= 0L) {
                sleepEndEpochMs = 0L;
                try {
                    if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                        mediaPlayer.pause();
                    }
                } catch (Throwable ignored) {
                }
                if (lastAppContext != null) {
                    syncForegroundService(lastAppContext);
                }
                notifyListeners();
            } else {
                mainHandler.postDelayed(this, Math.min(rem, 1000L));
            }
        }
    };
    private Context lastAppContext;

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

    public List<File> getPlaylistSnapshot() {
        return new ArrayList<>(playlist);
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public int getCurrentTrackNumber() {
        if (playlist.isEmpty() || currentIndex < 0) return 1;
        return Math.min(playlist.size(), currentIndex + 1);
    }

    public int getFolderTrackCount() {
        return Math.max(1, playlist.size());
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

    // ==================== תור ניגון דינאמי (סעיף 22) ====================

    public void playTrackAt(Context context, int idx) {
        if (idx < 0 || idx >= playlist.size()) return;
        currentIndex = idx;
        loadAndStartTrack(context.getApplicationContext(), playlist.get(idx));
    }

    public void addToQueue(Context context, File file) {
        if (file == null || !file.exists()) return;
        for (File f : playlist) {
            if (f.getAbsolutePath().equals(file.getAbsolutePath())) return;
        }
        playlist.add(file);
        notifyListeners();
    }

    public void removeFromQueue(int idx) {
        if (idx < 0 || idx >= playlist.size() || playlist.size() <= 1) return;
        if (idx == currentIndex) return;
        playlist.remove(idx);
        if (idx < currentIndex) currentIndex--;
        notifyListeners();
    }

    public void moveQueueItem(int fromIdx, int toIdx) {
        if (fromIdx < 0 || toIdx < 0 || fromIdx >= playlist.size() || toIdx >= playlist.size() || fromIdx == toIdx) return;
        File current = (currentIndex >= 0 && currentIndex < playlist.size()) ? playlist.get(currentIndex) : null;
        File item = playlist.remove(fromIdx);
        playlist.add(toIdx, item);
        if (current != null) {
            for (int i = 0; i < playlist.size(); i++) {
                if (playlist.get(i).getAbsolutePath().equals(current.getAbsolutePath())) {
                    currentIndex = i;
                    break;
                }
            }
        }
        notifyListeners();
    }

    // ==================== טיימר שינה (סעיף 22) ====================

    public void setSleepTimerMinutes(Context context, int minutes) {
        lastAppContext = context != null ? context.getApplicationContext() : lastAppContext;
        mainHandler.removeCallbacks(sleepCheckRunnable);
        if (minutes <= 0) {
            sleepEndEpochMs = 0L;
        } else {
            sleepEndEpochMs = System.currentTimeMillis() + (minutes * 60_000L);
            mainHandler.postDelayed(sleepCheckRunnable, 1000L);
        }
        notifyListeners();
    }

    public long getSleepTimerRemainingMs() {
        if (sleepEndEpochMs <= 0L) return 0L;
        return Math.max(0L, sleepEndEpochMs - System.currentTimeMillis());
    }

    // ==================== אקולייזר ובאס בוסט (סעיף 22) ====================

    private void attachAudioEffects() {
        releaseAudioEffects();
        if (mediaPlayer == null) return;
        try {
            int sessionId = mediaPlayer.getAudioSessionId();
            equalizer = new Equalizer(0, sessionId);
            equalizer.setEnabled(true);
            short bands = equalizer.getNumberOfBands();
            if (customBandLevels != null && customBandLevels.length == bands) {
                for (short i = 0; i < bands; i++) {
                    equalizer.setBandLevel(i, customBandLevels[i]);
                }
            } else if (selectedPresetIndex >= 0 && selectedPresetIndex < equalizer.getNumberOfPresets()) {
                equalizer.usePreset((short) selectedPresetIndex);
            }
            bassBoost = new BassBoost(0, sessionId);
            bassBoost.setEnabled(bassStrength > 0);
            if (bassBoost.getStrengthSupported()) {
                bassBoost.setStrength(bassStrength);
            }
        } catch (Throwable ignored) {
        }
    }

    private void releaseAudioEffects() {
        try { if (equalizer != null) equalizer.release(); } catch (Throwable ignored) {}
        try { if (bassBoost != null) bassBoost.release(); } catch (Throwable ignored) {}
        equalizer = null;
        bassBoost = null;
    }

    public int getEqBandCount() {
        try { return equalizer != null ? equalizer.getNumberOfBands() : 0; } catch (Throwable t) { return 0; }
    }

    public short getEqMinLevel() {
        try { return equalizer != null ? equalizer.getBandLevelRange()[0] : -1500; } catch (Throwable t) { return -1500; }
    }

    public short getEqMaxLevel() {
        try { return equalizer != null ? equalizer.getBandLevelRange()[1] : 1500; } catch (Throwable t) { return 1500; }
    }

    public int getEqCenterFreqHz(short band) {
        try { return equalizer != null ? equalizer.getCenterFreq(band) / 1000 : 1000; } catch (Throwable t) { return 1000; }
    }

    public short getEqBandLevel(short band) {
        try { return equalizer != null ? equalizer.getBandLevel(band) : 0; } catch (Throwable t) { return 0; }
    }

    public void setEqBandLevel(short band, short level) {
        try {
            if (equalizer != null) {
                equalizer.setBandLevel(band, level);
                short count = equalizer.getNumberOfBands();
                customBandLevels = new short[count];
                for (short i = 0; i < count; i++) customBandLevels[i] = equalizer.getBandLevel(i);
                selectedPresetIndex = -1;
            }
        } catch (Throwable ignored) {
        }
    }

    public List<String> getEqPresetNames() {
        List<String> list = new ArrayList<>();
        try {
            if (equalizer != null) {
                short n = equalizer.getNumberOfPresets();
                for (short i = 0; i < n; i++) list.add(equalizer.getPresetName(i));
            }
        } catch (Throwable ignored) {
        }
        return list;
    }

    public void applyEqPreset(int presetIdx) {
        try {
            if (equalizer != null && presetIdx >= 0 && presetIdx < equalizer.getNumberOfPresets()) {
                selectedPresetIndex = presetIdx;
                customBandLevels = null;
                equalizer.usePreset((short) presetIdx);
            }
        } catch (Throwable ignored) {
        }
    }

    public short getBassBoostStrength() {
        return bassStrength;
    }

    public void setBassBoostStrength(short strength) {
        this.bassStrength = (short) Math.max(0, Math.min(1000, strength));
        try {
            if (bassBoost != null) {
                bassBoost.setEnabled(this.bassStrength > 0);
                if (bassBoost.getStrengthSupported()) bassBoost.setStrength(this.bassStrength);
            }
        } catch (Throwable ignored) {
        }
    }

    // ==================== Playback Core ====================

    public void playOrAttachFile(Context context, File file) {
        if (file == null || !file.exists()) return;
        Context appCtx = context.getApplicationContext();
        lastAppContext = appCtx;

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
        currentArtwork = FileThumbnailLoader.extractAudioEmbeddedArt(file, 720);

        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(file.getAbsolutePath());
            mediaPlayer.prepare();
            prepared = true;
            attachAudioEffects();
            applySpeedInternal(true);
            mediaPlayer.setOnCompletionListener(mp -> handleTrackCompleted(appCtx));
            syncForegroundService(appCtx);
            notifyListeners();
        } catch (Exception e) {
            AppLogger.error(appCtx, "Audio", "Failed preparing track", e);
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
        if (context != null) syncForegroundService(context.getApplicationContext());
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
            if (lastAppContext != null) syncForegroundService(lastAppContext);
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
        mainHandler.removeCallbacks(sleepCheckRunnable);
        sleepEndEpochMs = 0L;
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
        releaseAudioEffects();
        if (mediaPlayer != null) {
            try { mediaPlayer.stop(); } catch (Exception ignored) {}
            try { mediaPlayer.release(); } catch (Exception ignored) {}
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
        return MediaFormatHelper.isAudioFile(name);
    }
}
