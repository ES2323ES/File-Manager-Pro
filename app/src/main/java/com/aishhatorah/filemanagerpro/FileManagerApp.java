package com.aishhatorah.filemanagerpro;

import android.app.Application;
import android.content.ComponentCallbacks2;

import java.util.concurrent.Executors;

public class FileManagerApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        AppLogger.init(this);
        AppLogger.installUncaughtHandler(this);

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
