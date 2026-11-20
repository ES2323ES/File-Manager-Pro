package com.aishhatorah.filemanagerpro;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * מנהל ניקוי מטמון חכם ואוטומטי:
 * מונע הצטברות של ג'יגות בזיכרון הפנימי ע"י ניקוי מיידי בגמר השימוש (תצוגת ארכיון, פתיחה חיצונית, חיתוך וכו')
 * ואכיפת תקרת גודל קשיחה (64MB) ו-TTL קצר לקבצים זמניים.
 */
public final class CacheCleanupManager {

    public static final long DEFAULT_TEMP_TTL_MS = 15L * 60L * 1000L; // 15 דקות
    public static final long APK_CACHE_TTL_MS = 6L * 60L * 60L * 1000L; // 6 שעות
    public static final long MAX_TOTAL_CACHE_BYTES = 64L * 1024L * 1024L; // תקרת 64MB למטמון

    private static final String[] TEMP_DIR_NAMES = {
            "arch_preview",
            "external_open",
            "open_with"
    };

    private static final String[] TEMP_FILE_PREFIXES = {
            "mts_play_",
            "fmp_pkg_stage_",
            "ts_cut_src_",
            "cut_comp_src_",
            "mts_pre_comp_",
            "bundle_icon_",
            ".tmp_edit_",
            ".tmp_del_"
    };

    private CacheCleanupManager() {
    }

    public static void cleanupArchivePreviewDir(Context context) {
        if (context == null) return;
        try {
            File dir = new File(context.getCacheDir(), "arch_preview");
            deleteChildrenOnly(dir);
        } catch (Throwable ignored) {
        }
    }

    public static void cleanupPreviewFileIfManagedCache(Context context, File file) {
        if (context == null || file == null) return;
        try {
            File cacheDir = context.getCacheDir();
            if (cacheDir == null) return;
            String canonicalCache = cacheDir.getCanonicalPath();
            String canonicalFile = file.getCanonicalPath();
            if (!canonicalFile.startsWith(canonicalCache + File.separator)) {
                return;
            }
            File archDir = new File(cacheDir, "arch_preview");
            String archPath = archDir.getCanonicalPath();
            if (canonicalFile.startsWith(archPath + File.separator)) {
                deleteRecursivelySafe(file);
                File parent = file.getParentFile();
                if (parent != null && !parent.getCanonicalPath().equals(archPath)) {
                    String[] rem = parent.list();
                    if (rem == null || rem.length == 0) {
                        parent.delete();
                    }
                }
                return;
            }
            File extDir = new File(cacheDir, "external_open");
            String extPath = extDir.getCanonicalPath();
            if (canonicalFile.startsWith(extPath + File.separator)) {
                File sessionDir = file.getParentFile();
                if (sessionDir != null && !sessionDir.getCanonicalPath().equals(extPath)) {
                    deleteRecursivelySafe(sessionDir);
                } else {
                    deleteRecursivelySafe(file);
                }
                return;
            }
            File openWithDir = new File(cacheDir, "open_with");
            String openWithPath = openWithDir.getCanonicalPath();
            if (canonicalFile.startsWith(openWithPath + File.separator)) {
                deleteRecursivelySafe(file);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void cleanupOldExternalOpenSessions(Context context, long maxAgeMs) {
        if (context == null) return;
        try {
            File extRoot = new File(context.getCacheDir(), "external_open");
            if (!extRoot.exists() || !extRoot.isDirectory()) return;
            long cutoff = System.currentTimeMillis() - Math.max(0L, maxAgeMs);
            File[] children = extRoot.listFiles();
            if (children == null) return;
            for (File child : children) {
                if (child.lastModified() <= cutoff) {
                    deleteRecursivelySafe(child);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public static void cleanupDownloadedApkUpdates(Context context, String currentVersionName) {
        if (context == null) return;
        cleanupApksInDir(context.getCacheDir(), currentVersionName);
        cleanupApksInDir(context.getExternalCacheDir(), currentVersionName);
    }

    private static void cleanupApksInDir(File dir, String currentVersionName) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        long now = System.currentTimeMillis();
        for (File f : files) {
            if (!f.isFile()) continue;
            String n = f.getName();
            if (n.startsWith("FileManagerPro-") && n.toLowerCase(Locale.ROOT).endsWith(".apk")) {
                String ver = n.substring("FileManagerPro-".length(), n.length() - 4);
                boolean obsolete = currentVersionName != null && !currentVersionName.isEmpty()
                        && UpdateChecker.compareSemver(ver, currentVersionName) <= 0;
                if (obsolete || (now - f.lastModified()) > APK_CACHE_TTL_MS) {
                    f.delete();
                }
            }
        }
    }

    public static void pruneCaches(Context context) {
        if (context == null) return;
        try {
            cleanupDownloadedApkUpdates(context, BuildConfig.VERSION_NAME);
            pruneCacheRoots(
                    context.getCacheDir(),
                    context.getExternalCacheDir(),
                    System.currentTimeMillis(),
                    MAX_TOTAL_CACHE_BYTES
            );
        } catch (Throwable ignored) {
        }
    }

    public static void pruneCacheRoots(File internalCache, File externalCache, long nowMs, long maxTotalBytes) {
        List<File> roots = new ArrayList<>();
        if (internalCache != null && internalCache.exists()) {
            roots.add(internalCache);
        }
        if (externalCache != null && externalCache.exists() && !samePath(internalCache, externalCache)) {
            roots.add(externalCache);
        }
        if (roots.isEmpty()) return;

        long tempCutoff = nowMs - DEFAULT_TEMP_TTL_MS;
        long apkCutoff = nowMs - APK_CACHE_TTL_MS;

        for (File root : roots) {
            for (String dirName : TEMP_DIR_NAMES) {
                File sub = new File(root, dirName);
                if (sub.exists() && sub.isDirectory()) {
                    deleteOlderThan(sub, tempCutoff);
                }
            }
            File[] rootChildren = root.listFiles();
            if (rootChildren != null) {
                for (File f : rootChildren) {
                    String name = f.getName();
                    if (isManagedTempFileName(name) && f.lastModified() <= tempCutoff) {
                        deleteRecursivelySafe(f);
                    } else if (f.isFile() && name.startsWith("FileManagerPro-")
                            && name.toLowerCase(Locale.ROOT).endsWith(".apk")
                            && f.lastModified() <= apkCutoff) {
                        f.delete();
                    }
                }
            }
        }

        if (maxTotalBytes < 0L) return;
        List<File> allFiles = new ArrayList<>();
        long totalBytes = 0L;
        for (File root : roots) {
            totalBytes += collectAllFiles(root, allFiles);
        }
        if (totalBytes <= maxTotalBytes) return;

        Collections.sort(allFiles, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return Long.compare(a.lastModified(), b.lastModified());
            }
        });

        for (File f : allFiles) {
            if (totalBytes <= maxTotalBytes) break;
            long len = f.length();
            if (f.delete()) {
                totalBytes -= len;
            }
        }
        for (File root : roots) {
            removeEmptySubdirs(root, false);
        }
    }

    private static boolean isManagedTempFileName(String name) {
        if (name == null) return false;
        for (String prefix : TEMP_FILE_PREFIXES) {
            if (name.startsWith(prefix)) return true;
        }
        return false;
    }

    private static void deleteOlderThan(File dir, long cutoffMs) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) {
                deleteOlderThan(child, cutoffMs);
                String[] rem = child.list();
                if (rem == null || rem.length == 0) {
                    child.delete();
                }
            } else if (child.lastModified() <= cutoffMs) {
                child.delete();
            }
        }
    }

    private static long collectAllFiles(File dir, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) return 0L;
        long sum = 0L;
        for (File c : children) {
            if (c.isDirectory()) {
                sum += collectAllFiles(c, out);
            } else {
                out.add(c);
                sum += Math.max(0L, c.length());
            }
        }
        return sum;
    }

    private static void removeEmptySubdirs(File dir, boolean deleteSelf) {
        File[] children = dir.listFiles();
        if (children != null) {
            for (File c : children) {
                if (c.isDirectory()) {
                    removeEmptySubdirs(c, true);
                }
            }
        }
        if (deleteSelf) {
            String[] after = dir.list();
            if (after == null || after.length == 0) {
                dir.delete();
            }
        }
    }

    private static void deleteChildrenOnly(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File c : children) {
            deleteRecursivelySafe(c);
        }
    }

    private static void deleteRecursivelySafe(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File c : children) {
                    deleteRecursivelySafe(c);
                }
            }
        }
        f.delete();
    }

    private static boolean samePath(File a, File b) {
        if (a == null || b == null) return false;
        try {
            return a.getCanonicalPath().equals(b.getCanonicalPath());
        } catch (Throwable ignored) {
            return a.getAbsolutePath().equals(b.getAbsolutePath());
        }
    }
}
