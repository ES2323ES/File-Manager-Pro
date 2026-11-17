package com.aishhatorah.filemanagerpro;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

import androidx.core.app.NotificationCompat;

import java.util.concurrent.ConcurrentHashMap;

/**
 * התראות התקדמות לפעולות קבצים ממושכות: מוצגות רק כשהפעולה נמשכת מעל סף קצר, ומוסרות מיד בסיום.
 */
public final class FileOperationNotificationHelper {

    private static final String CHANNEL_ID = "file_ops_progress_channel";
    private static final int BASE_NOTIF_ID = 7700;
    private static final long SHOW_DELAY_MS = 350L;
    private static final ConcurrentHashMap<Integer, Long> OP_START_TIMES = new ConcurrentHashMap<>();

    private FileOperationNotificationHelper() {
    }

    private static void ensureChannel(Context context) {
        if (context == null) return;
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID,
                "התקדמות פעולות קבצים",
                NotificationManager.IMPORTANCE_LOW
        );
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
    }

    public static void begin(int opId) {
        OP_START_TIMES.put(opId, System.currentTimeMillis());
    }

    public static void updateProgress(Context context, int opId, String title, String detail, int current, int total) {
        if (context == null) return;
        try {
            long now = System.currentTimeMillis();
            Long started = OP_START_TIMES.putIfAbsent(opId, now);
            long startTs = started != null ? started : now;
            if ((now - startTs) < SHOW_DELAY_MS) {
                return;
            }
            ensureChannel(context);
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            boolean indet = total <= 0;
            NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_app_logo_notif)
                    .setContentTitle(title)
                    .setContentText(detail)
                    .setOnlyAlertOnce(true)
                    .setOngoing(true)
                    .setProgress(Math.max(0, total), Math.max(0, current), indet)
                    .setPriority(NotificationCompat.PRIORITY_LOW);
            nm.notify(BASE_NOTIF_ID + opId, b.build());
        } catch (Throwable ignored) {
        }
    }

    public static void cancel(Context context, int opId) {
        OP_START_TIMES.remove(opId);
        if (context == null) return;
        try {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.cancel(BASE_NOTIF_ID + opId);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void finish(Context context, int opId, String title, String resultText) {
        cancel(context, opId);
    }
}
