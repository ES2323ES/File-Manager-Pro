package com.aishhatorah.filemanagerpro;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.core.app.NotificationCompat;

import java.util.concurrent.ConcurrentHashMap;

/**
 * התראות התקדמות לפעולות ממושכות:
 * - פעולות קצרות (< 450ms) אינן מציפות את שורת ההתראות.
 * - אם פעולה נמשכת מעל 450ms (או שהמשתמש לחץ על "הסתר"), מוצגת התראת התקדמות חיה עד לסיום.
 */
public final class FileOperationNotificationHelper {

    private static final String CHANNEL_ID = "file_ops_progress_channel";
    private static final int BASE_NOTIF_ID = 7700;
    private static final long SHOW_DELAY_MS = 450L;
    private static final long MIN_NOTIFY_INTERVAL_MS = 180L;

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final ConcurrentHashMap<Integer, PendingOp> ACTIVE_OPS = new ConcurrentHashMap<>();

    private static final class PendingOp {
        final Context appContext;
        final long startMs;
        volatile String title;
        volatile String detail;
        volatile int current;
        volatile int total;
        volatile boolean shown;
        volatile boolean finished;
        volatile long lastNotifyMs;
        Runnable delayedShowRunnable;

        PendingOp(Context appContext, String title, String detail) {
            this.appContext = appContext;
            this.startMs = System.currentTimeMillis();
            this.title = title == null ? "מעבד קבצים..." : title;
            this.detail = detail == null ? "" : detail;
        }
    }

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
        begin(null, opId, "מעבד קבצים...", "");
    }

    public static void begin(Context context, int opId, String title, String detail) {
        Context appCtx = context != null ? context.getApplicationContext() : null;
        PendingOp prev = ACTIVE_OPS.remove(opId);
        if (prev != null && prev.delayedShowRunnable != null) {
            MAIN_HANDLER.removeCallbacks(prev.delayedShowRunnable);
        }

        PendingOp op = new PendingOp(appCtx, title, detail);
        op.delayedShowRunnable = () -> {
            PendingOp cur = ACTIVE_OPS.get(opId);
            if (cur == op && !op.finished && !op.shown && op.appContext != null) {
                op.shown = true;
                postNotification(op.appContext, opId, op.title, op.detail, op.current, op.total);
            }
        };
        ACTIVE_OPS.put(opId, op);
        MAIN_HANDLER.postDelayed(op.delayedShowRunnable, SHOW_DELAY_MS);
    }

    public static void forceShowNow(Context context, int opId) {
        Context appCtx = context != null ? context.getApplicationContext() : null;
        PendingOp op = ACTIVE_OPS.get(opId);
        if (op == null) {
            if (appCtx == null) return;
            op = new PendingOp(appCtx, "פעולת קבצים ברקע", "");
            ACTIVE_OPS.put(opId, op);
        }
        if (op.finished) return;
        if (op.delayedShowRunnable != null) {
            MAIN_HANDLER.removeCallbacks(op.delayedShowRunnable);
        }
        op.shown = true;
        Context ctx = appCtx != null ? appCtx : op.appContext;
        if (ctx != null) {
            postNotification(ctx, opId, op.title, op.detail, op.current, op.total);
        }
    }

    public static void updateProgress(Context context, int opId, String title, String detail, int current, int total) {
        if (context == null) return;
        try {
            Context appCtx = context.getApplicationContext();
            PendingOp op = ACTIVE_OPS.get(opId);
            if (op == null) {
                begin(appCtx, opId, title, detail);
                op = ACTIVE_OPS.get(opId);
            }
            if (op == null || op.finished) return;

            if (title != null && !title.isEmpty()) op.title = title;
            if (detail != null) op.detail = detail;
            op.current = current;
            op.total = total;

            long now = System.currentTimeMillis();
            boolean thresholdReached = (now - op.startMs) >= SHOW_DELAY_MS;
            if (!op.shown && !thresholdReached) {
                return;
            }
            boolean firstShow = !op.shown;
            op.shown = true;
            if (firstShow || (now - op.lastNotifyMs) >= MIN_NOTIFY_INTERVAL_MS || (total > 0 && current >= total)) {
                op.lastNotifyMs = now;
                postNotification(appCtx, opId, op.title, op.detail, current, total);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void postNotification(Context context, int opId, String title, String detail, int current, int total) {
        if (context == null) return;
        try {
            ensureChannel(context);
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            boolean indet = total <= 0;
            NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_app_logo_notif)
                    .setContentTitle(title != null ? title : "פעולת קבצים")
                    .setContentText(detail != null ? detail : "")
                    .setOnlyAlertOnce(true)
                    .setOngoing(true)
                    .setSilent(true)
                    .setProgress(Math.max(0, total), Math.max(0, current), indet)
                    .setPriority(NotificationCompat.PRIORITY_LOW);
            nm.notify(BASE_NOTIF_ID + opId, b.build());
        } catch (Throwable ignored) {
        }
    }

    public static void cancel(Context context, int opId) {
        PendingOp op = ACTIVE_OPS.remove(opId);
        if (op != null) {
            op.finished = true;
            if (op.delayedShowRunnable != null) {
                MAIN_HANDLER.removeCallbacks(op.delayedShowRunnable);
            }
        }
        Context ctx = context != null ? context : (op != null ? op.appContext : null);
        if (ctx == null) return;
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
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
