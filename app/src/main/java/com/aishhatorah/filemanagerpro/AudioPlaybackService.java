package com.aishhatorah.filemanagerpro;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;

import java.io.File;

/**
 * סעיף 46: שירות התראת מדיה נקי ותקני של אנדרואיד (Notification.MediaStyle + MediaSession)
 * המחליף את הפריסה המותאמת הקודמת בחוויית נגן מערכתית אלגנטית הכוללת כפתורי מדיה,
 * עטיפת שיר, פס התקדמות מובנה אינטראקטיבי וסנכרון לוח הנעילה.
 */
public class AudioPlaybackService extends Service {

    public static final String CHANNEL_ID = "file_manager_pro_audio_channel";
    public static final int NOTIF_ID = 4402;

    public static final String ACTION_SYNC = "com.aishhatorah.filemanagerpro.AUDIO_SYNC";
    public static final String ACTION_TOGGLE = "com.aishhatorah.filemanagerpro.AUDIO_TOGGLE";
    public static final String ACTION_NEXT = "com.aishhatorah.filemanagerpro.AUDIO_NEXT";
    public static final String ACTION_PREV = "com.aishhatorah.filemanagerpro.AUDIO_PREV";
    public static final String ACTION_STOP_SERVICE = "com.aishhatorah.filemanagerpro.AUDIO_STOP";

    private MediaSession mediaSession;
    private final Handler tickerHandler = new Handler(Looper.getMainLooper());
    private final Runnable progressTicker = new Runnable() {
        @Override
        public void run() {
            AudioPlayerManager mgr = AudioPlayerManager.getInstance();
            if (!mgr.hasActiveSession() || mgr.getCurrentFile() == null) {
                tickerHandler.removeCallbacks(this);
                return;
            }
            updateMediaSessionState(mgr);
            if (mgr.isPlaying()) {
                tickerHandler.postDelayed(this, 1000L);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        ensureNotificationChannel();
        initMediaSession();
    }

    private void initMediaSession() {
        mediaSession = new MediaSession(this, "FileManagerProAudio");
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                AudioPlayerManager mgr = AudioPlayerManager.getInstance();
                if (!mgr.isPlaying()) mgr.togglePlayPause(AudioPlaybackService.this);
            }

            @Override
            public void onPause() {
                AudioPlayerManager mgr = AudioPlayerManager.getInstance();
                if (mgr.isPlaying()) mgr.togglePlayPause(AudioPlaybackService.this);
            }

            @Override
            public void onSkipToNext() {
                AudioPlayerManager.getInstance().playNext(AudioPlaybackService.this);
            }

            @Override
            public void onSkipToPrevious() {
                AudioPlayerManager.getInstance().playPrevious(AudioPlaybackService.this);
            }

            @Override
            public void onSeekTo(long pos) {
                AudioPlayerManager.getInstance().seekTo((int) pos);
            }

            @Override
            public void onStop() {
                AudioPlayerManager.getInstance().stopAndDismiss(AudioPlaybackService.this);
            }
        });
        mediaSession.setActive(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : ACTION_SYNC;
        AudioPlayerManager mgr = AudioPlayerManager.getInstance();

        if (ACTION_STOP_SERVICE.equals(action)) {
            tickerHandler.removeCallbacks(progressTicker);
            mgr.stopAndDismiss(this);
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_TOGGLE.equals(action)) {
            mgr.togglePlayPause(this);
        } else if (ACTION_NEXT.equals(action)) {
            mgr.playNext(this);
        } else if (ACTION_PREV.equals(action)) {
            mgr.playPrevious(this);
        }

        if (!mgr.hasActiveSession() || mgr.getCurrentFile() == null) {
            tickerHandler.removeCallbacks(progressTicker);
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        updateMediaSessionState(mgr);
        Notification notification = buildNativeMediaNotification(mgr);
        startForegroundSafely(notification);

        tickerHandler.removeCallbacks(progressTicker);
        if (mgr.isPlaying()) {
            tickerHandler.postDelayed(progressTicker, 1000L);
        } else {
            stopForeground(false);
        }

        return START_NOT_STICKY;
    }

    private void updateMediaSessionState(AudioPlayerManager mgr) {
        if (mediaSession == null) return;
        File song = mgr.getCurrentFile();
        String title = song != null ? song.getName() : "ניגון שמע";
        Bitmap art = mgr.getCurrentArtwork();
        long dur = Math.max(1L, mgr.getDurationMs());
        long pos = Math.max(0L, mgr.getCurrentPositionMs());

        MediaMetadata.Builder meta = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST,
                        "File Manager Pro  •  " + mgr.getCurrentTrackNumber() + "/" + mgr.getFolderTrackCount())
                .putLong(MediaMetadata.METADATA_KEY_DURATION, dur);
        if (art != null && !art.isRecycled()) {
            meta.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art);
        }
        mediaSession.setMetadata(meta.build());

        long actions = PlaybackState.ACTION_PLAY
                | PlaybackState.ACTION_PAUSE
                | PlaybackState.ACTION_PLAY_PAUSE
                | PlaybackState.ACTION_SKIP_TO_NEXT
                | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                | PlaybackState.ACTION_SEEK_TO
                | PlaybackState.ACTION_STOP;
        int stateCode = mgr.isPlaying() ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED;
        PlaybackState state = new PlaybackState.Builder()
                .setActions(actions)
                .setState(stateCode, pos, mgr.isPlaying() ? mgr.getPlaybackSpeed() : 0f)
                .build();
        mediaSession.setPlaybackState(state);
    }

    private void startForegroundSafely(Notification notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIF_ID, notification);
            }
        } catch (Exception e) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                try {
                    nm.notify(NOTIF_ID, notification);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private Notification buildNativeMediaNotification(AudioPlayerManager mgr) {
        File song = mgr.getCurrentFile();
        String songTitle = song != null ? song.getName() : "ניגון שמע";
        boolean playing = mgr.isPlaying();
        Bitmap artwork = mgr.getCurrentArtwork();

        Intent openViewer = new Intent(this, BuiltInViewerActivity.class);
        if (song != null) {
            openViewer.putExtra(BuiltInViewerActivity.EXTRA_FILE_PATH, song.getAbsolutePath());
        }
        openViewer.putExtra(BuiltInViewerActivity.EXTRA_FROM_NOTIFICATION, true);
        openViewer.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentPi = PendingIntent.getActivity(
                this,
                401,
                openViewer,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        PendingIntent prevPi = serviceActionPi(ACTION_PREV, 402);
        PendingIntent togglePi = serviceActionPi(ACTION_TOGGLE, 403);
        PendingIntent nextPi = serviceActionPi(ACTION_NEXT, 404);
        PendingIntent stopPi = serviceActionPi(ACTION_STOP_SERVICE, 405);

        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_audio)
                .setContentTitle(songTitle)
                .setContentText("File Manager Pro  •  " + mgr.getCurrentTrackNumber() + "/" + mgr.getFolderTrackCount())
                .setContentIntent(contentPi)
                .setDeleteIntent(stopPi)
                .setOngoing(playing)
                .setOnlyAlertOnce(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(this, R.drawable.ic_skip_prev), "הקודם", prevPi).build())
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(this, playing ? R.drawable.ic_pause : R.drawable.ic_play),
                        playing ? "השהה" : "נגן", togglePi).build())
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(this, R.drawable.ic_skip_next), "הבא", nextPi).build())
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(this, R.drawable.ic_delete), "סגור", stopPi).build());

        if (artwork != null && !artwork.isRecycled()) {
            builder.setLargeIcon(artwork);
        }

        Notification.MediaStyle style = new Notification.MediaStyle()
                .setMediaSession(mediaSession.getSessionToken())
                .setShowActionsInCompactView(0, 1, 2);
        builder.setStyle(style);

        return builder.build();
    }

    private PendingIntent serviceActionPi(String action, int reqCode) {
        Intent i = new Intent(this, AudioPlaybackService.class);
        i.setAction(action);
        return PendingIntent.getService(
                this,
                reqCode,
                i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private void ensureNotificationChannel() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID,
                "נגן שמע ברקע — File Manager Pro",
                NotificationManager.IMPORTANCE_LOW
        );
        ch.setDescription("פקדי מדיה מקוריים ופס התקדמות בלוח ההתראות");
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        tickerHandler.removeCallbacks(progressTicker);
        if (mediaSession != null) {
            try {
                mediaSession.setActive(false);
                mediaSession.release();
            } catch (Throwable ignored) {
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
