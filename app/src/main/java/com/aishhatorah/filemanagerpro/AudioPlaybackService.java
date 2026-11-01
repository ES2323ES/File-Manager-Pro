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
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.RemoteViews;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.File;
import java.util.Locale;

/**
 * סעיף יח: שירות התראת מדיה מתקדם בבר ההתראות העליון בזמן ניגון אודיו:
 * - ללא מילים ("הקודם", "השהה", "הבא"), אלא סמלי מדיה מותאמים:
 *   בצד שמאל = שיר קודם, באמצע = השהה/נגן, בצד ימין = שיר הבא!
 * - כולל פס התקדמות של השיר משמאל לימין המתעדכן בזמן אמת!
 * - לחיצה על ההתראה פותחת את הנגן עם דגל ניווט חכם המחזיר בלחיצה על "חזור" אל התיקייה שממנה השיר מתנגן.
 */
public class AudioPlaybackService extends Service {

    public static final String CHANNEL_ID = "file_manager_pro_audio_channel";
    public static final int NOTIF_ID = 4402;

    public static final String ACTION_SYNC = "com.aishhatorah.filemanagerpro.AUDIO_SYNC";
    public static final String ACTION_TOGGLE = "com.aishhatorah.filemanagerpro.AUDIO_TOGGLE";
    public static final String ACTION_NEXT = "com.aishhatorah.filemanagerpro.AUDIO_NEXT";
    public static final String ACTION_PREV = "com.aishhatorah.filemanagerpro.AUDIO_PREV";
    public static final String ACTION_STOP_SERVICE = "com.aishhatorah.filemanagerpro.AUDIO_STOP";

    private final Handler tickerHandler = new Handler(Looper.getMainLooper());
    private final Runnable progressTicker = new Runnable() {
        @Override
        public void run() {
            AudioPlayerManager mgr = AudioPlayerManager.getInstance();
            if (!mgr.hasActiveSession() || mgr.getCurrentFile() == null) {
                tickerHandler.removeCallbacks(this);
                return;
            }
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                try {
                    nm.notify(NOTIF_ID, buildNotification(mgr));
                } catch (Exception ignored) {
                }
            }
            if (mgr.isPlaying()) {
                tickerHandler.postDelayed(this, 1000L);
            }
        }
    };

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

        tickerHandler.removeCallbacks(progressTicker);
        if (mgr.isPlaying()) {
            tickerHandler.postDelayed(progressTicker, 1000L);
        } else {
            stopForeground(false);
        }

        return START_NOT_STICKY;
    }

    private Notification buildNotification(AudioPlayerManager mgr) {
        File song = mgr.getCurrentFile();
        String songTitle = song != null ? song.getName() : "ניגון שמע";
        boolean playing = mgr.isPlaying();
        Bitmap artwork = mgr.getCurrentArtwork();

        int dur = mgr.getDurationMs();
        int pos = mgr.getCurrentPositionMs();
        int permille = dur > 0 ? Math.min(1000, (int) ((pos * 1000L) / dur)) : 0;

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

        RemoteViews collapsed = new RemoteViews(getPackageName(), R.layout.notif_audio_collapsed);
        collapsed.setTextViewText(R.id.tvNotifTitle, songTitle);
        collapsed.setProgressBar(R.id.pbNotifProgress, 1000, permille, false);
        collapsed.setImageViewResource(R.id.btnNotifPlayPause, playing ? R.drawable.ic_pause : R.drawable.ic_play);
        collapsed.setOnClickPendingIntent(R.id.btnNotifPrev, prevPi);
        collapsed.setOnClickPendingIntent(R.id.btnNotifPlayPause, togglePi);
        collapsed.setOnClickPendingIntent(R.id.btnNotifNext, nextPi);

        RemoteViews expanded = new RemoteViews(getPackageName(), R.layout.notif_audio_expanded);
        expanded.setTextViewText(R.id.tvNotifTitle, songTitle);
        expanded.setTextViewText(R.id.tvNotifSub, "File Manager Pro  •  " + mgr.getCurrentTrackNumber() + "/" + mgr.getFolderTrackCount());
        expanded.setTextViewText(R.id.tvNotifPos, formatClock(pos));
        expanded.setTextViewText(R.id.tvNotifDur, formatClock(dur));
        expanded.setProgressBar(R.id.pbNotifProgress, 1000, permille, false);
        expanded.setImageViewResource(R.id.btnNotifPlayPause, playing ? R.drawable.ic_pause : R.drawable.ic_play);
        expanded.setOnClickPendingIntent(R.id.btnNotifPrev, prevPi);
        expanded.setOnClickPendingIntent(R.id.btnNotifPlayPause, togglePi);
        expanded.setOnClickPendingIntent(R.id.btnNotifNext, nextPi);
        expanded.setOnClickPendingIntent(R.id.btnNotifClose, stopPi);

        if (artwork != null && !artwork.isRecycled()) {
            Bitmap notifThumb = FileThumbnailLoader.createRoundedSquareBitmap(artwork, 160, 24f);
            collapsed.setImageViewBitmap(R.id.ivNotifArt, notifThumb);
            expanded.setImageViewBitmap(R.id.ivNotifArt, notifThumb);
        } else {
            collapsed.setImageViewResource(R.id.ivNotifArt, R.drawable.ic_app_logo);
            expanded.setImageViewResource(R.id.ivNotifArt, R.drawable.ic_app_logo);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_app_logo)
                .setContentTitle(songTitle)
                .setContentText(formatClock(pos) + " / " + formatClock(dur))
                .setCustomContentView(collapsed)
                .setCustomBigContentView(expanded)
                .setContentIntent(contentPi)
                .setDeleteIntent(stopPi)
                .setOngoing(playing)
                .setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        return builder.build();
    }

    private String formatClock(int ms) {
        int sec = Math.max(0, ms / 1000);
        return String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60);
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
        ch.setDescription("מציג את סמלי המדיה ופס ההתקדמות של השיר בבר ההתראות בזמן ניגון");
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        tickerHandler.removeCallbacks(progressTicker);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
