package com.aishhatorah.filemanagerpro;

import android.app.Application;

import java.util.concurrent.Executors;

public class FileManagerApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        AppLogger.init(this);
        AppLogger.installUncaughtHandler(this);
        AppLogger.info(this, "App", "Application started v" + BuildConfig.VERSION_NAME);

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                RecycleBinManager.migrateLegacyTrashIfNeeded(this);
                FileScanCache.prewarm(this);
            } catch (Throwable t) {
                AppLogger.warn(this, "Startup", "Background prewarm warning: " + t.getMessage());
            }
        });
    }
}
