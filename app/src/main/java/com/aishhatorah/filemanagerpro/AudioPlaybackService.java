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
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.File;

/**
 * סעיף יג: שירות ניגון שמע ברקע המציג בבר ההתראות העליון את סמל האפליקציה
 * ואת בקרי השיר הפעיל כל עוד השיר מנוגן.
 */
public class AudioPlaybackService extends Service {

    public static final String CHANNEL_ID = "file_manager_pro_audio_channel";
    public static final int NOTIF_ID = 4402;

    public static final String ACTION_SYNC = "com.aishhatorah.filemanagerpro.AUDIO_SYNC";
    public static final String ACTION_TOGGLE = "com.aishhatorah.filemanagerpro.AUDIO_TOGGLE";
    public static final String ACTION_NEXT = "com.aishhatorah.filemanagerpro.AUDIO_NEXT";
    public static final String ACTION_PREV = "com.aishhatorah.filemanagerpro.AUDIO_PREV";
    public static final String ACTION_STOP_SERVICE = "com.aishhatorah.filemanagerpro.AUDIO_STOP";

    @Override
    public void onCreate() {
        super.onCreate();
        ensureNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : ACTION_SYNC;
        AudioPlayerManager mgr = AudioPlayerManager.getInstance();

        if (ACTION_STOP_SERVICE.equals(action)) {
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
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        Notification notification = buildNotification(mgr);
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

        if (!mgr.isPlaying()) {
            stopForeground(false);
        }

        return START_NOT_STICKY;
    }

    private Notification buildNotification(AudioPlayerManager mgr) {
        File song = mgr.getCurrentFile();
        String songTitle = song != null ? song.getName() : "ניגון שמע";
        boolean playing = mgr.isPlaying();
        Bitmap artwork = mgr.getCurrentArtwork();

        Intent openViewer = new Intent(this, BuiltInViewerActivity.class);
        if (song != null) {
            openViewer.putExtra(BuiltInViewerActivity.EXTRA_FILE_PATH, song.getAbsolutePath());
        }
        openViewer.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
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

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_app_logo)
                .setContentTitle(songTitle)
                .setContentText(playing ? "File Manager Pro • מנגן כעת" : "File Manager Pro • מושהה")
                .setSubText("File Manager Pro")
                .setContentIntent(contentPi)
                .setDeleteIntent(stopPi)
                .setOngoing(playing)
                .setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(R.drawable.ic_skip_prev, "הקודם", prevPi)
                .addAction(playing ? R.drawable.ic_pause : R.drawable.ic_play, playing ? "השהה" : "נגן", togglePi)
                .addAction(R.drawable.ic_skip_next, "הבא", nextPi)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "עצור", stopPi);

        if (artwork != null && !artwork.isRecycled()) {
            builder.setLargeIcon(artwork);
        }

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
        ch.setDescription("מציג את סמל File Manager Pro ובקרי השיר בבר ההתראות העליון בזמן ניגון");
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
