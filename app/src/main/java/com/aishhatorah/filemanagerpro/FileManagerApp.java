package com.aishhatorah.filemanagerpro;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentCallbacks2;
import android.os.Bundle;

import java.util.concurrent.Executors;

public class FileManagerApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        AppLogger.init(this);
        AppLogger.installUncaughtHandler(this);
        CacheCleanupManager.startHourlyScheduler(this);

        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity a, Bundle b) {}
            @Override public void onActivityStarted(Activity a) {
                CacheCleanupManager.maybePruneHourly(FileManagerApp.this);
            }
            @Override public void onActivityResumed(Activity a) {}
            @Override public void onActivityPaused(Activity a) {}
            @Override public void onActivityStopped(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(Activity a) {}
        });

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                CacheCleanupManager.pruneCaches(this);
                RecycleBinManager.migrateLegacyTrashIfNeeded(this);
                FileScanCache.prewarm(this);
                FileIndexDatabase.getInstance(this).clearAndRebuildSync(this);
            } catch (Throwable t) {
                AppLogger.warn(this, "Startup", "Background prewarm warning: " + t.getMessage());
            }
        });
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            FileThumbnailLoader.clearMemoryCache();
            ThemeHelper.invalidateWallpaperCache();
        }
    }
}
