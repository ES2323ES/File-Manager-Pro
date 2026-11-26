package com.aishhatorah.filemanagerpro.browser;

import android.os.Handler;
import android.os.Looper;

import com.aishhatorah.filemanagerpro.StorageUtils;

import java.io.File;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * מטמון אסינכרוני לחישוב מספר הפריטים וגודל תיקיות ברקע עם ביטול דורות אוטומטי.
 */
public final class FolderSizeHelper {

    public static final class FolderStats {
        public final int itemCount;
        public final long totalBytes;

        public FolderStats(int itemCount, long totalBytes) {
            this.itemCount = itemCount;
            this.totalBytes = totalBytes;
        }
    }

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicInteger generation = new AtomicInteger(0);
    private final Map<String, FolderStats> statsCache = new ConcurrentHashMap<>();
    private final Set<String> pendingPaths = ConcurrentHashMap.newKeySet();

    public void clear() {
        generation.incrementAndGet();
        statsCache.clear();
        pendingPaths.clear();
    }

    public FolderStats getCached(String path, boolean requireBytes) {
        FolderStats st = path != null ? statsCache.get(path) : null;
        if (st == null) return null;
        if (requireBytes && st.totalBytes < 0L) return null;
        return st;
    }

    public void scheduleLoad(
            File dir,
            boolean includeHidden,
            boolean computeBytes,
            Runnable onReadyUi
    ) {
        if (dir == null || !dir.isDirectory()) return;
        final String path = dir.getAbsolutePath();
        FolderStats existing = statsCache.get(path);
        if (existing != null && (!computeBytes || existing.totalBytes >= 0L)) {
            return;
        }
        final String pendingKey = path + "|" + (computeBytes ? "1" : "0");
        if (!pendingPaths.add(pendingKey)) return;
        final int gen = generation.get();
        executor.execute(() -> {
            if (generation.get() != gen) return;
            int count = countDirectChildren(dir, includeHidden, gen);
            if (generation.get() != gen) return;
            long bytes = -1L;
            if (computeBytes) {
                long[] res = StorageUtils.computeDirectoryBytesAndCount(dir, includeHidden, 0);
                bytes = res[0];
            }
            if (generation.get() != gen) return;
            statsCache.put(path, new FolderStats(count, bytes));
            pendingPaths.remove(pendingKey);
            if (onReadyUi != null) {
                mainHandler.post(() -> {
                    if (generation.get() == gen) {
                        onReadyUi.run();
                    }
                });
            }
        });
    }

    private int countDirectChildren(File dir, boolean includeHidden, int expectedGen) {
        int count = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir.toPath())) {
            for (Path child : stream) {
                if (generation.get() != expectedGen) return count;
                Path namePath = child.getFileName();
                if (!includeHidden && namePath != null && namePath.toString().startsWith(".")) {
                    continue;
                }
                count++;
            }
        } catch (Throwable ignored) {
            File[] sub = dir.listFiles();
            if (sub != null) {
                for (File f : sub) {
                    if (!includeHidden && f.getName().startsWith(".")) continue;
                    count++;
                }
            }
        }
        return count;
    }

    public void shutdown() {
        generation.incrementAndGet();
        mainHandler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
    }
}
