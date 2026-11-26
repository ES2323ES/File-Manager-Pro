package com.aishhatorah.filemanagerpro;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.interfaces.IVLCVout;

import java.io.File;
import java.util.ArrayList;

/**
 * מעטפת מנוע LibVLC ייעודית להשמעת קובצי MTS / M2TS / MPEG-TS בצורה חלקה,
 * כולל פענוח חומרתי, הסרת השזירה (Deinterlacing) לווידאו 1080i ממצלמות, דילוג מיידי ותמיכה מלאה בשמע AC-3/MP2/LPCM/AAC.
 */
public final class VlcMtsPlayerHelper {

    public interface Callback {
        void onPrepared(int durationMs);
        void onVideoSizeChanged(int width, int height, float pixelRatio);
        void onFirstFrameRendered();
        void onPlayingChanged(boolean isPlaying);
        void onEndReached();
        void onError();
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Callback callback;
    private LibVLC libVLC;
    private MediaPlayer mediaPlayer;
    private File currentFile;

    private Surface attachedSurface;
    private int attachedWidth = 0;
    private int attachedHeight = 0;
    private boolean voutAttached = false;

    private boolean released = false;
    private boolean prepared = false;
    private boolean hasVout = false;
    private boolean firstFrameNotified = false;
    private boolean pendingPausedFirstFrame = false;
    private boolean endReached = false;
    private boolean muted = false;
    private boolean looping = false;
    private float playbackSpeed = 1.0f;
    private int cachedDurationMs = 0;
    private int lastTimeMs = 0;

    private final IVLCVout.OnNewVideoLayoutListener layoutListener;

    public VlcMtsPlayerHelper(Context context, Callback callback) {
        this.callback = callback;
        this.layoutListener = (vlcVout, width, height, visibleWidth, visibleHeight, sarNum, sarDen) -> {
            if (released || width <= 0 || height <= 0) return;
            final int effW = visibleWidth > 0 ? visibleWidth : width;
            final int effH = visibleHeight > 0 ? visibleHeight : height;
            final float sar = (sarNum > 0 && sarDen > 0) ? ((float) sarNum / (float) sarDen) : 1.0f;
            mainHandler.post(() -> {
                if (released) return;
                if (this.callback != null) {
                    this.callback.onVideoSizeChanged(effW, effH, sar);
                }
                if (hasVout) {
                    markFirstFrameRendered();
                }
            });
        };
        ArrayList<String> options = new ArrayList<>();
        options.add("--file-caching=500");
        options.add("--avcodec-hw=any");
        options.add("--deinterlace=1");
        options.add("--deinterlace-mode=yadif");
        options.add("--no-video-title-show");
        this.libVLC = new LibVLC(context.getApplicationContext(), options);
        this.mediaPlayer = new MediaPlayer(this.libVLC);
        this.mediaPlayer.setEventListener(this::handleVlcEvent);
    }

    public void attachSurface(Surface surface, int width, int height) {
        if (released || mediaPlayer == null) return;
        this.attachedSurface = surface;
        this.attachedWidth = width;
        this.attachedHeight = height;

        IVLCVout vout = mediaPlayer.getVLCVout();
        if (voutAttached) {
            try {
                vout.detachViews();
            } catch (Throwable ignored) {
            }
            voutAttached = false;
        }
        if (surface != null) {
            vout.setVideoSurface(surface, null);
            if (width > 0 && height > 0) {
                vout.setWindowSize(width, height);
            }
            vout.attachViews(layoutListener);
            voutAttached = true;
        }
    }

    public void updateWindowSize(int width, int height) {
        this.attachedWidth = width;
        this.attachedHeight = height;
        if (!released && mediaPlayer != null && voutAttached && width > 0 && height > 0) {
            try {
                mediaPlayer.getVLCVout().setWindowSize(width, height);
            } catch (Throwable ignored) {
            }
        }
    }

    public void detachSurface() {
        this.attachedSurface = null;
        if (mediaPlayer != null && voutAttached) {
            try {
                mediaPlayer.getVLCVout().detachViews();
            } catch (Throwable ignored) {
            }
            voutAttached = false;
        }
    }

    public void setLooping(boolean looping) {
        this.looping = looping;
    }

    public void load(File file, boolean autoPlay, boolean startMuted) {
        if (released || mediaPlayer == null || libVLC == null || file == null) return;
        this.currentFile = file;
        this.prepared = false;
        this.hasVout = false;
        this.firstFrameNotified = false;
        this.pendingPausedFirstFrame = !autoPlay;
        this.endReached = false;
        this.muted = startMuted;
        this.cachedDurationMs = 0;
        this.lastTimeMs = 0;

        Media media = new Media(libVLC, file.getAbsolutePath());
        media.setHWDecoderEnabled(true, false);
        media.addOption(":file-caching=500");
        media.addOption(":deinterlace=1");
        media.addOption(":deinterlace-mode=yadif");
        mediaPlayer.setMedia(media);
        media.release();

        try {
            mediaPlayer.setVolume((pendingPausedFirstFrame || muted) ? 0 : 100);
        } catch (Throwable ignored) {
        }
        mediaPlayer.play();
    }

    public void play() {
        if (released || mediaPlayer == null) return;
        if (endReached) {
            restartFromBeginning();
            return;
        }
        pendingPausedFirstFrame = false;
        applyVolumeInternal();
        try {
            if (!mediaPlayer.isPlaying()) {
                mediaPlayer.play();
            }
            if (Math.abs(playbackSpeed - 1.0f) >= 0.02f) {
                mediaPlayer.setRate(playbackSpeed);
            }
        } catch (Throwable ignored) {
        }
    }

    public void pause() {
        if (released || mediaPlayer == null) return;
        pendingPausedFirstFrame = false;
        try {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
            }
        } catch (Throwable ignored) {
        }
    }

    public void restartFromBeginning() {
        if (released || mediaPlayer == null || libVLC == null || currentFile == null) return;
        pendingPausedFirstFrame = false;
        endReached = false;
        applyVolumeInternal();
        try {
            mediaPlayer.stop();
            Media media = new Media(libVLC, currentFile.getAbsolutePath());
            media.setHWDecoderEnabled(true, false);
            media.addOption(":file-caching=500");
            media.addOption(":deinterlace=1");
            media.addOption(":deinterlace-mode=yadif");
            mediaPlayer.setMedia(media);
            media.release();
            mediaPlayer.play();
            if (Math.abs(playbackSpeed - 1.0f) >= 0.02f) {
                mediaPlayer.setRate(playbackSpeed);
            }
        } catch (Throwable ignored) {
        }
    }

    public void seekTo(long positionMs) {
        if (released || mediaPlayer == null) return;
        endReached = false;
        long target = Math.max(0L, positionMs);
        lastTimeMs = (int) Math.min(Integer.MAX_VALUE, target);
        try {
            mediaPlayer.setTime(target);
        } catch (Throwable ignored) {
        }
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
        applyVolumeInternal();
    }

    public void setPlaybackSpeed(float speed) {
        this.playbackSpeed = Math.max(0.25f, Math.min(4.0f, speed));
        if (!released && mediaPlayer != null) {
            try {
                mediaPlayer.setRate(this.playbackSpeed);
            } catch (Throwable ignored) {
            }
        }
    }

    public boolean isPlaying() {
        if (released || mediaPlayer == null || pendingPausedFirstFrame) return false;
        try {
            return mediaPlayer.isPlaying();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public boolean isPrepared() {
        return prepared;
    }

    public int getDurationMs() {
        if (!released && mediaPlayer != null) {
            try {
                long len = mediaPlayer.getLength();
                if (len > 0L) {
                    cachedDurationMs = (int) Math.min(Integer.MAX_VALUE, len);
                }
            } catch (Throwable ignored) {
            }
        }
        return cachedDurationMs;
    }

    public int getCurrentPositionMs() {
        if (released || mediaPlayer == null) return 0;
        if (pendingPausedFirstFrame) return 0;
        try {
            long t = mediaPlayer.getTime();
            if (t >= 0L) {
                lastTimeMs = (int) Math.min(Integer.MAX_VALUE, t);
            }
        } catch (Throwable ignored) {
        }
        return lastTimeMs;
    }

    public void release() {
        if (released) return;
        released = true;
        mainHandler.removeCallbacksAndMessages(null);
        MediaPlayer mp = mediaPlayer;
        LibVLC vlc = libVLC;
        mediaPlayer = null;
        libVLC = null;
        if (mp != null) {
            try {
                mp.setEventListener(null);
            } catch (Throwable ignored) {
            }
            if (voutAttached) {
                try {
                    mp.getVLCVout().detachViews();
                } catch (Throwable ignored) {
                }
                voutAttached = false;
            }
            try {
                mp.stop();
            } catch (Throwable ignored) {
            }
            try {
                mp.release();
            } catch (Throwable ignored) {
            }
        }
        if (vlc != null) {
            try {
                vlc.release();
            } catch (Throwable ignored) {
            }
        }
    }

    private void applyVolumeInternal() {
        if (released || mediaPlayer == null) return;
        try {
            mediaPlayer.setVolume((pendingPausedFirstFrame || muted) ? 0 : 100);
        } catch (Throwable ignored) {
        }
    }

    private void handleVlcEvent(MediaPlayer.Event event) {
        if (event == null || released) return;
        final int type = event.type;
        final long lenChanged = (type == MediaPlayer.Event.LengthChanged) ? event.getLengthChanged() : -1L;
        final long timeChanged = (type == MediaPlayer.Event.TimeChanged) ? event.getTimeChanged() : -1L;
        final int voutCount = (type == MediaPlayer.Event.Vout) ? event.getVoutCount() : 0;

        mainHandler.post(() -> {
            if (released || mediaPlayer == null) return;
            switch (type) {
                case MediaPlayer.Event.LengthChanged:
                    if (lenChanged > 0L) {
                        cachedDurationMs = (int) Math.min(Integer.MAX_VALUE, lenChanged);
                    }
                    ensurePreparedNotified();
                    break;

                case MediaPlayer.Event.Vout:
                    if (voutCount > 0) {
                        hasVout = true;
                        updateDurationFromPlayer();
                        ensurePreparedNotified();
                        markFirstFrameRendered();
                        if (pendingPausedFirstFrame) {
                            finishPausedFirstFrame();
                        }
                    }
                    break;

                case MediaPlayer.Event.Playing:
                    endReached = false;
                    updateDurationFromPlayer();
                    ensurePreparedNotified();
                    if (pendingPausedFirstFrame) {
                        try {
                            mediaPlayer.setVolume(0);
                        } catch (Throwable ignored) {
                        }
                        if (hasVout) {
                            finishPausedFirstFrame();
                        }
                    } else {
                        applyVolumeInternal();
                        if (Math.abs(playbackSpeed - 1.0f) >= 0.02f) {
                            try {
                                mediaPlayer.setRate(playbackSpeed);
                            } catch (Throwable ignored) {
                            }
                        }
                        if (callback != null) callback.onPlayingChanged(true);
                    }
                    break;

                case MediaPlayer.Event.Paused:
                    if (callback != null) callback.onPlayingChanged(false);
                    break;

                case MediaPlayer.Event.TimeChanged:
                    if (timeChanged >= 0L && !pendingPausedFirstFrame) {
                        lastTimeMs = (int) Math.min(Integer.MAX_VALUE, timeChanged);
                    }
                    ensurePreparedNotified();
                    break;

                case MediaPlayer.Event.EndReached:
                    if (looping) {
                        restartFromBeginning();
                    } else {
                        endReached = true;
                        if (callback != null) {
                            callback.onPlayingChanged(false);
                            callback.onEndReached();
                        }
                    }
                    break;

                case MediaPlayer.Event.EncounteredError:
                    if (callback != null) {
                        callback.onError();
                    }
                    break;
            }
        });
    }

    private void updateDurationFromPlayer() {
        if (released || mediaPlayer == null) return;
        try {
            long len = mediaPlayer.getLength();
            if (len > 0L) {
                cachedDurationMs = (int) Math.min(Integer.MAX_VALUE, len);
            }
        } catch (Throwable ignored) {
        }
    }

    private void ensurePreparedNotified() {
        if (!prepared && !released) {
            updateDurationFromPlayer();
            prepared = true;
            if (callback != null) {
                callback.onPrepared(cachedDurationMs);
            }
        }
    }

    private void markFirstFrameRendered() {
        if (!firstFrameNotified && !released) {
            firstFrameNotified = true;
            if (callback != null) {
                callback.onFirstFrameRendered();
            }
        }
    }

    private void finishPausedFirstFrame() {
        if (!pendingPausedFirstFrame || released || mediaPlayer == null) return;
        pendingPausedFirstFrame = false;
        try {
            mediaPlayer.pause();
            mediaPlayer.setTime(0L);
            lastTimeMs = 0;
        } catch (Throwable ignored) {
        }
        applyVolumeInternal();
        if (callback != null) {
            callback.onPlayingChanged(false);
        }
    }
}
