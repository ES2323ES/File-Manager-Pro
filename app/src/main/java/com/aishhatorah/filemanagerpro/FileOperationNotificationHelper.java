package com.aishhatorah.filemanagerpro;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

import androidx.core.app.NotificationCompat;

/**
 * סעיף 28: הצגת התראות התקדמות לפעולות קבצים ממושכות (העתקה, העברה, דחיסה, חילוץ, מחיקה).
 */
public final class FileOperationNotificationHelper {

    private static final String CHANNEL_ID = "file_ops_progress_channel";
    private static final int BASE_NOTIF_ID = 7700;

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

    public static void updateProgress(Context context, int opId, String title, String detail, int current, int total) {
        if (context == null) return;
        try {
            ensureChannel(context);
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            boolean indet = total <= 0;
            NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_folder)
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

    public static void finish(Context context, int opId, String title, String resultText) {
        if (context == null) return;
        try {
            ensureChannel(context);
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_folder)
                    .setContentTitle(title)
                    .setContentText(resultText)
                    .setAutoCancel(true)
                    .setOngoing(false)
                    .setProgress(0, 0, false)
                    .setPriority(NotificationCompat.PRIORITY_LOW);
            nm.notify(BASE_NOTIF_ID + opId, b.build());
        } catch (Throwable ignored) {
        }
    }
}
