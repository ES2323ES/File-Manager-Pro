package com.aishhatorah.filemanagerpro;

import android.content.Context;

/**
 * סעיף 18: מטמון רקע מהיר לחישוב נפחי קטגוריות וזמינות מיידית בפתיחת מסך הבית והתיקיות.
 */
public final class FileScanCache {

    private static volatile StorageUtils.CategoryBytesSummary cachedSummary = null;
    private static volatile long lastUpdatedAt = 0L;

    private FileScanCache() {
    }

    public static void prewarm(Context context) {
        if (context == null || !StorageUtils.hasAllStoragePermissions(context)) return;
        refreshNow(context);
    }

    public static StorageUtils.CategoryBytesSummary getCached() {
        return cachedSummary;
    }

    public static StorageUtils.CategoryBytesSummary refreshNow(Context context) {
        if (context == null) return new StorageUtils.CategoryBytesSummary();
        StorageUtils.CategoryBytesSummary fresh = StorageUtils.computeCategorySizes(context, PrefsManager.isShowHidden(context));
        cachedSummary = fresh;
        lastUpdatedAt = System.currentTimeMillis();
        return fresh;
    }

    public static void invalidate() {
        lastUpdatedAt = 0L;
    }

    public static long getLastUpdatedAt() {
        return lastUpdatedAt;
    }
}
