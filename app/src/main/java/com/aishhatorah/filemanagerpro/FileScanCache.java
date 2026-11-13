package com.aishhatorah.filemanagerpro;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * סעיף 18: מטמון מהיר של נפחי קטגוריות ורשימות הקבצים (מבוסס MediaStore SQLite)
 * לטעינה מיידית של מסך הבית וכל הקטגוריות (תמונות, שמע, סירטונים, מסמכים, אפליקציות, חדשים).
 */
public final class FileScanCache {

    private static final long CACHE_TTL_MS = 60_000L;
    private static final Object LOCK = new Object();

    private static volatile StorageUtils.CategoryIndexSnapshot cachedSnapshot = null;
    private static volatile boolean cachedIncludeHidden = false;
    private static volatile long lastUpdatedAt = 0L;
    private static volatile boolean dirty = true;

    private FileScanCache() {
    }

    public static void prewarm(Context context) {
        if (context == null || !StorageUtils.hasAllStoragePermissions(context)) return;
        getSnapshot(context, PrefsManager.isShowHidden(context), false);
    }

    public static StorageUtils.CategoryBytesSummary getCached() {
        StorageUtils.CategoryIndexSnapshot snap = cachedSnapshot;
        return snap != null ? snap.summary : null;
    }

    public static boolean isDirty() {
        return dirty || cachedSnapshot == null;
    }

    public static boolean isFresh(boolean showHidden) {
        return !dirty
                && cachedSnapshot != null
                && cachedIncludeHidden == showHidden
                && (System.currentTimeMillis() - lastUpdatedAt) < CACHE_TTL_MS;
    }

    public static StorageUtils.CategoryBytesSummary refreshNow(Context context) {
        if (context == null) return new StorageUtils.CategoryBytesSummary();
        return getSnapshot(context, PrefsManager.isShowHidden(context), true).summary;
    }

    public static StorageUtils.CategoryIndexSnapshot getSnapshot(Context context, boolean showHidden, boolean forceRefresh) {
        if (context == null) return new StorageUtils.CategoryIndexSnapshot();
        if (!forceRefresh && isFresh(showHidden)) {
            return cachedSnapshot;
        }
        synchronized (LOCK) {
            if (!forceRefresh && isFresh(showHidden)) {
                return cachedSnapshot;
            }
            StorageUtils.CategoryIndexSnapshot fresh = StorageUtils.buildCategoryIndexSnapshot(context, showHidden);
            cachedSnapshot = fresh;
            cachedIncludeHidden = showHidden;
            lastUpdatedAt = System.currentTimeMillis();
            dirty = false;
            return fresh;
        }
    }

    public static StorageUtils.CategoryIndexSnapshot peekFreshSnapshot(boolean showHidden) {
        return isFresh(showHidden) ? cachedSnapshot : null;
    }

    public static List<File> extractCategoryFiles(StorageUtils.CategoryIndexSnapshot snap, String mode) {
        if (snap == null || mode == null) return Collections.emptyList();
        if (FileBrowserActivity.MODE_IMAGES.equals(mode)) return new ArrayList<>(snap.images);
        if (FileBrowserActivity.MODE_AUDIO.equals(mode)) return new ArrayList<>(snap.audio);
        if (FileBrowserActivity.MODE_VIDEOS.equals(mode)) return new ArrayList<>(snap.videos);
        if (FileBrowserActivity.MODE_DOCUMENTS.equals(mode)) return new ArrayList<>(snap.documents);
        if (FileBrowserActivity.MODE_APPS.equals(mode)) return new ArrayList<>(snap.apps);
        if (FileBrowserActivity.MODE_RECENT.equals(mode)) return new ArrayList<>(snap.recent);
        return Collections.emptyList();
    }

    public static void invalidate() {
        dirty = true;
        lastUpdatedAt = 0L;
    }

    public static long getLastUpdatedAt() {
        return lastUpdatedAt;
    }
}
