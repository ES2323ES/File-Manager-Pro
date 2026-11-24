package com.aishhatorah.filemanagerpro;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.MediaStore;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class StorageUtils {

    private static final String ALERT_CHANNEL_ID = "storage_capacity_alert_96";
    public static final double CRITICAL_THRESHOLD_PERCENT = 96.0;

    public static class StorageStat {
        public final File root;
        public final String label;
        public final long totalBytes;
        public final long usedBytes;
        public final long freeBytes;
        public final double usedPercent;
        public final boolean available;

        public StorageStat(File root, long totalBytes, long usedBytes, long freeBytes, boolean available) {
            this(root, "", totalBytes, usedBytes, freeBytes, available);
        }

        public StorageStat(File root, String label, long totalBytes, long usedBytes, long freeBytes, boolean available) {
            this.root = root;
            this.label = label == null ? "" : label;
            this.totalBytes = totalBytes;
            this.usedBytes = usedBytes;
            this.freeBytes = freeBytes;
            this.available = available;
            this.usedPercent = totalBytes > 0 ? (usedBytes * 100.0) / totalBytes : 0.0;
        }
    }

    public static class CategoryBytesSummary {
        public long downloadsBytes = 0L;
        public int downloadsCount = 0;

        public long imagesBytes = 0L;
        public int imagesCount = 0;

        public long audioBytes = 0L;
        public int audioCount = 0;

        public long videosBytes = 0L;
        public int videosCount = 0;

        public long documentsBytes = 0L;
        public int documentsCount = 0;

        public long appsBytes = 0L;
        public int appsCount = 0;

        public long recentBytes = 0L;
        public int recentCount = 0;

        public long recycleBinBytes = 0L;
        public int recycleBinCount = 0;
    }

    public static class CategoryIndexSnapshot {
        public final CategoryBytesSummary summary = new CategoryBytesSummary();
        public final List<File> images = new ArrayList<>();
        public final List<File> audio = new ArrayList<>();
        public final List<File> videos = new ArrayList<>();
        public final List<File> documents = new ArrayList<>();
        public final List<File> apps = new ArrayList<>();
        public final List<File> recent = new ArrayList<>();
        public final Map<String, Long> sizeByPath = new HashMap<>();
        public final Map<String, Long> modifiedByPath = new HashMap<>();
    }

    private static final ThreadLocal<Collator> HE_COLLATOR = new ThreadLocal<Collator>() {
        @Override
        protected Collator initialValue() {
            return Collator.getInstance(new Locale("he", "IL"));
        }
    };

    public static boolean hasAllStoragePermissions(Context context) {
        if (context == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static long getFileCreatedTimeMillis(File file) {
        if (file == null || !file.exists()) return 0L;
        try {
            BasicFileAttributes attr = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
            long created = attr.creationTime().toMillis();
            if (created > 0) return created;
        } catch (Throwable ignored) {
        }
        return file.lastModified();
    }

    // סעיף 40: יצירת קובץ גיבוי .bak לפני שמירה אם מופעל בהגדרות
    public static File createBackupIfEnabled(Context context, File target) {
        if (context == null || target == null || !target.exists() || !target.isFile()) return null;
        if (!PrefsManager.isCreateBackupOnSave(context)) return null;
        try {
            File bak = new File(target.getParentFile(), target.getName() + ".bak");
            try (FileInputStream fis = new FileInputStream(target);
                 FileOutputStream fos = new FileOutputStream(bak);
                 FileChannel in = fis.getChannel();
                 FileChannel out = fos.getChannel()) {
                in.transferTo(0, in.size(), out);
            }
            return bak;
        } catch (Throwable ignored) {
            return null;
        }
    }

    // סעיף 34: זיהוי כונני USB OTG מחוברים בזמן אמת
    public static List<StorageStat> getConnectedUsbOtgVolumes(Context context) {
        List<StorageStat> out = new ArrayList<>();
        if (context == null) return out;
        try {
            File internal = Environment.getExternalStorageDirectory();
            StorageStat sd = getExternalSdCardStat(context);
            String internalPath = internal != null ? internal.getAbsolutePath() : "";
            String sdPath = (sd.available && sd.root != null) ? sd.root.getAbsolutePath() : "";

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                StorageManager sm = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
                if (sm != null) {
                    for (StorageVolume vol : sm.getStorageVolumes()) {
                        if (vol.isPrimary() || !vol.isRemovable()) continue;
                        File dir = vol.getDirectory();
                        if (dir == null || !dir.exists() || !dir.canRead()) continue;
                        String path = dir.getAbsolutePath();
                        if (path.equals(internalPath) || path.equals(sdPath)) continue;
                        try {
                            StatFs stat = new StatFs(path);
                            long total = stat.getTotalBytes();
                            if (total <= 0) continue;
                            long free = stat.getAvailableBytes();
                            long used = Math.max(0, total - free);
                            String desc = vol.getDescription(context);
                            out.add(new StorageStat(dir, desc != null ? desc : "USB OTG", total, used, free, true));
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }

            if (out.isEmpty()) {
                File storageDir = new File("/storage");
                File[] mounts = storageDir.listFiles();
                if (mounts != null) {
                    for (File m : mounts) {
                        if (!m.isDirectory() || !m.canRead()) continue;
                        String name = m.getName();
                        if ("emulated".equals(name) || "self".equals(name)) continue;
                        String path = m.getAbsolutePath();
                        if (path.equals(internalPath) || path.equals(sdPath)) continue;
                        try {
                            StatFs stat = new StatFs(path);
                            long total = stat.getTotalBytes();
                            if (total <= 0) continue;
                            long free = stat.getAvailableBytes();
                            long used = Math.max(0, total - free);
                            out.add(new StorageStat(m, "USB (" + name + ")", total, used, free, true));
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    public static CategoryBytesSummary computeCategorySizes(Context context, boolean includeHidden) {
        return buildCategoryIndexSnapshot(context, includeHidden).summary;
    }

    /**
     * אינדוקס מהיר מבוסס MediaStore SQLite (פי 50–100 מהר יותר מסריקת FUSE רקורסיבית),
     * עם השלמה מיידית של תיקיית ההורדות ונפילה מבוקרת לסריקת קבצים רק אם MediaStore ריק.
     */
    public static CategoryIndexSnapshot buildCategoryIndexSnapshot(Context context, boolean includeHidden) {
        CategoryIndexSnapshot snapshot = new CategoryIndexSnapshot();
        if (context == null) return snapshot;

        CategoryBytesSummary summary = snapshot.summary;
        summary.recycleBinBytes = RecycleBinManager.getTrashTotalBytes(context);
        summary.recycleBinCount = RecycleBinManager.getTrashItemCount(context);

        File dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        long[] dlStats = computeDirectoryBytesAndCount(dl, includeHidden, 0);
        summary.downloadsBytes = dlStats[0];
        summary.downloadsCount = (int) dlStats[1];

        long sevenDaysAgo = System.currentTimeMillis() - (7L * 24L * 3600L * 1000L);
        long recentCutoff = Math.max(sevenDaysAgo, PrefsManager.getRecentClearedTimestamp(context));
        Set<String> seenPaths = new HashSet<>(2048);

        int mediaStoreRows = queryMediaStoreSnapshot(context, snapshot, seenPaths, includeHidden, recentCutoff);

        // השלמה מהירה של תיקיית הורדות (קובצי APKX/APK+ חדשים שטרם נסרקו במערכת)
        if (dl != null && dl.exists() && dl.isDirectory()) {
            accumulateCategorySnapshotRecursive(dl, snapshot, seenPaths, includeHidden, recentCutoff, 0, 2);
        }

        // נפילה לסריקה מלאה רק אם MediaStore החזיר 0 שורות לחלוטין
        if (mediaStoreRows == 0) {
            File root = Environment.getExternalStorageDirectory();
            accumulateCategorySnapshotRecursive(root, snapshot, seenPaths, includeHidden, recentCutoff, 0, 7);
            StorageStat sd = getExternalSdCardStat(context);
            if (sd.available && sd.root != null) {
                accumulateCategorySnapshotRecursive(sd.root, snapshot, seenPaths, includeHidden, recentCutoff, 0, 7);
            }
        }
        return snapshot;
    }

    private static int queryMediaStoreSnapshot(
            Context context,
            CategoryIndexSnapshot snapshot,
            Set<String> seenPaths,
            boolean includeHidden,
            long recentCutoff
    ) {
        int rowCount = 0;
        ContentResolver cr = context.getContentResolver();
        if (cr == null) return 0;

        Uri uri = MediaStore.Files.getContentUri("external");
        String[] projection = new String[]{
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.DATE_MODIFIED
        };
        String selection = MediaStore.Files.FileColumns.DATA + " IS NOT NULL";
        String sortOrder = MediaStore.Files.FileColumns.DATE_MODIFIED + " DESC";

        try (Cursor c = cr.query(uri, projection, selection, null, sortOrder)) {
            if (c == null) return 0;
            int colPath = c.getColumnIndex(MediaStore.Files.FileColumns.DATA);
            int colSize = c.getColumnIndex(MediaStore.Files.FileColumns.SIZE);
            int colMod = c.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED);
            if (colPath < 0) return 0;

            while (c.moveToNext()) {
                rowCount++;
                String path = c.getString(colPath);
                if (path == null || path.isEmpty()) continue;
                if (path.contains("/.FileManagerPro_Trash/")) continue;
                if (isExcludedAndroidSystemPath(path)) continue;
                if (!includeHidden && containsHiddenSegment(path)) continue;

                int slash = path.lastIndexOf('/');
                String fileName = slash >= 0 ? path.substring(slash + 1) : path;
                if (fileName.isEmpty() || (!includeHidden && fileName.startsWith("."))) continue;
                if (!fileName.contains(".")) continue; // מדלג על תיקיות ללא סיומת לפני גישה לדיסק

                String lower = fileName.toLowerCase(Locale.ROOT);
                boolean isImg = MediaFormatHelper.isImageFile(lower);
                boolean isAud = !isImg && MediaFormatHelper.isAudioFile(lower);
                boolean isVid = !isImg && !isAud && MediaFormatHelper.isVideoFile(lower);
                boolean isDoc = !isImg && !isAud && !isVid && MediaFormatHelper.isDocumentFile(lower);
                boolean isApp = !isImg && !isAud && !isVid && !isDoc && MediaFormatHelper.isInstallableAppPackage(lower);

                long modSec = colMod >= 0 ? c.getLong(colMod) : 0L;
                long modMs = modSec > 0 ? modSec * 1000L : 0L;
                boolean isRecent = modMs >= recentCutoff && snapshot.recent.size() < 2500;

                if (!isImg && !isAud && !isVid && !isDoc && !isApp && !isRecent) continue;
                if (!seenPaths.add(path)) continue;

                File f = new File(path);
                long[] stat = readRegularFileSizeAndMtime(f);
                if (stat == null) continue;

                long len = stat[0];
                if (modMs <= 0L) modMs = stat[1];

                snapshot.sizeByPath.put(path, len);
                snapshot.modifiedByPath.put(path, modMs);

                if (isImg) {
                    snapshot.images.add(f);
                    snapshot.summary.imagesBytes += len;
                    snapshot.summary.imagesCount++;
                } else if (isAud) {
                    snapshot.audio.add(f);
                    snapshot.summary.audioBytes += len;
                    snapshot.summary.audioCount++;
                } else if (isVid) {
                    snapshot.videos.add(f);
                    snapshot.summary.videosBytes += len;
                    snapshot.summary.videosCount++;
                } else if (isDoc) {
                    snapshot.documents.add(f);
                    snapshot.summary.documentsBytes += len;
                    snapshot.summary.documentsCount++;
                } else if (isApp) {
                    snapshot.apps.add(f);
                    snapshot.summary.appsBytes += len;
                    snapshot.summary.appsCount++;
                }
                if (isRecent && snapshot.recent.size() < 2500) {
                    snapshot.recent.add(f);
                    snapshot.summary.recentBytes += len;
                    snapshot.summary.recentCount++;
                }
            }
        } catch (Throwable ignored) {
        }
        return rowCount;
    }

    /**
     * רענון ממוקד לקטגוריה בודדת בלבד (תמונות/שמע/וידאו/מסמכים/אפליקציות/חדשים)
     * מבלי לסרוק את כל המכשיר ושאר הקטגוריות.
     */
    public static void querySingleCategoryIntoSnapshot(
            Context context,
            CategoryIndexSnapshot snapshot,
            String mode,
            boolean includeHidden
    ) {
        if (context == null || snapshot == null || mode == null) return;
        ContentResolver cr = context.getContentResolver();
        if (cr == null) return;

        long sevenDaysAgo = System.currentTimeMillis() - (7L * 24L * 3600L * 1000L);
        long recentCutoff = Math.max(sevenDaysAgo, PrefsManager.getRecentClearedTimestamp(context));

        List<File> freshFiles = new ArrayList<>();
        long totalBytes = 0L;
        Set<String> seen = new HashSet<>();

        Uri queryUri;
        String selection = MediaStore.MediaColumns.DATA + " IS NOT NULL";
        String[] selectionArgs = null;

        if (FileBrowserActivity.MODE_IMAGES.equals(mode)) {
            queryUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        } else if (FileBrowserActivity.MODE_AUDIO.equals(mode)) {
            queryUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        } else if (FileBrowserActivity.MODE_VIDEOS.equals(mode)) {
            queryUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        } else if (FileBrowserActivity.MODE_APPS.equals(mode)) {
            queryUri = MediaStore.Files.getContentUri("external");
            selection = MediaStore.Files.FileColumns.DATA + " LIKE ?";
            selectionArgs = new String[]{"%.apk%"};
        } else if (FileBrowserActivity.MODE_RECENT.equals(mode)) {
            queryUri = MediaStore.Files.getContentUri("external");
            selection = MediaStore.Files.FileColumns.DATA + " IS NOT NULL AND "
                    + MediaStore.Files.FileColumns.DATE_MODIFIED + " >= ?";
            selectionArgs = new String[]{String.valueOf(recentCutoff / 1000L)};
        } else {
            queryUri = MediaStore.Files.getContentUri("external");
        }

        String[] projection = new String[]{
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.DATE_MODIFIED
        };
        String sortOrder = MediaStore.MediaColumns.DATE_MODIFIED + " DESC";

        try (Cursor c = cr.query(queryUri, projection, selection, selectionArgs, sortOrder)) {
            if (c != null) {
                int colPath = c.getColumnIndex(MediaStore.MediaColumns.DATA);
                int colMod = c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED);
                if (colPath >= 0) {
                    while (c.moveToNext()) {
                        String path = c.getString(colPath);
                        if (path == null || path.isEmpty()) continue;
                        if (path.contains("/.FileManagerPro_Trash/")) continue;
                        if (isExcludedAndroidSystemPath(path)) continue;
                        if (!includeHidden && containsHiddenSegment(path)) continue;

                        int slash = path.lastIndexOf('/');
                        String fileName = slash >= 0 ? path.substring(slash + 1) : path;
                        if (fileName.isEmpty() || (!includeHidden && fileName.startsWith("."))) continue;

                        String lower = fileName.toLowerCase(Locale.ROOT);
                        if (FileBrowserActivity.MODE_DOCUMENTS.equals(mode) && !MediaFormatHelper.isDocumentFile(lower)) {
                            continue;
                        }
                        if (FileBrowserActivity.MODE_APPS.equals(mode) && !MediaFormatHelper.isInstallableAppPackage(lower)) {
                            continue;
                        }
                        if (FileBrowserActivity.MODE_IMAGES.equals(mode) && !MediaFormatHelper.isImageFile(lower)) {
                            continue;
                        }
                        if (FileBrowserActivity.MODE_AUDIO.equals(mode) && !MediaFormatHelper.isAudioFile(lower)) {
                            continue;
                        }
                        if (FileBrowserActivity.MODE_VIDEOS.equals(mode) && !MediaFormatHelper.isVideoFile(lower)) {
                            continue;
                        }
                        if (FileBrowserActivity.MODE_RECENT.equals(mode) && freshFiles.size() >= 2500) {
                            break;
                        }
                        if (!seen.add(path)) continue;

                        File f = new File(path);
                        long[] stat = readRegularFileSizeAndMtime(f);
                        if (stat == null) continue;

                        long len = stat[0];
                        long modSec = colMod >= 0 ? c.getLong(colMod) : 0L;
                        long modMs = modSec > 0 ? modSec * 1000L : stat[1];
                        if (FileBrowserActivity.MODE_RECENT.equals(mode) && modMs < recentCutoff) {
                            continue;
                        }

                        snapshot.sizeByPath.put(path, len);
                        snapshot.modifiedByPath.put(path, modMs);
                        freshFiles.add(f);
                        totalBytes += len;
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        // השלמה מהירה של תיקיית הורדות עבור קטגוריית קבצים חדשים או חבילות התקנה
        if (FileBrowserActivity.MODE_APPS.equals(mode) || FileBrowserActivity.MODE_RECENT.equals(mode)
                || FileBrowserActivity.MODE_DOCUMENTS.equals(mode)) {
            File dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (dl != null && dl.exists() && dl.isDirectory()) {
                collectSingleCategoryInDir(dl, mode, freshFiles, seen, snapshot, includeHidden, recentCutoff, 0, 2);
                totalBytes = 0L;
                for (File f : freshFiles) {
                    Long sz = snapshot.sizeByPath.get(f.getAbsolutePath());
                    totalBytes += sz != null ? sz : f.length();
                }
            }
        }

        if (FileBrowserActivity.MODE_IMAGES.equals(mode)) {
            snapshot.images.clear();
            snapshot.images.addAll(freshFiles);
            snapshot.summary.imagesBytes = totalBytes;
            snapshot.summary.imagesCount = freshFiles.size();
        } else if (FileBrowserActivity.MODE_AUDIO.equals(mode)) {
            snapshot.audio.clear();
            snapshot.audio.addAll(freshFiles);
            snapshot.summary.audioBytes = totalBytes;
            snapshot.summary.audioCount = freshFiles.size();
        } else if (FileBrowserActivity.MODE_VIDEOS.equals(mode)) {
            snapshot.videos.clear();
            snapshot.videos.addAll(freshFiles);
            snapshot.summary.videosBytes = totalBytes;
            snapshot.summary.videosCount = freshFiles.size();
        } else if (FileBrowserActivity.MODE_DOCUMENTS.equals(mode)) {
            snapshot.documents.clear();
            snapshot.documents.addAll(freshFiles);
            snapshot.summary.documentsBytes = totalBytes;
            snapshot.summary.documentsCount = freshFiles.size();
        } else if (FileBrowserActivity.MODE_APPS.equals(mode)) {
            snapshot.apps.clear();
            snapshot.apps.addAll(freshFiles);
            snapshot.summary.appsBytes = totalBytes;
            snapshot.summary.appsCount = freshFiles.size();
        } else if (FileBrowserActivity.MODE_RECENT.equals(mode)) {
            snapshot.recent.clear();
            snapshot.recent.addAll(freshFiles);
            snapshot.summary.recentBytes = totalBytes;
            snapshot.summary.recentCount = freshFiles.size();
        }
    }

    private static void collectSingleCategoryInDir(
            File dir,
            String mode,
            List<File> out,
            Set<String> seen,
            CategoryIndexSnapshot snapshot,
            boolean includeHidden,
            long recentCutoff,
            int depth,
            int maxDepth
    ) {
        if (dir == null || !dir.exists() || depth > maxDepth) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File f : children) {
            if (!includeHidden && f.getName().startsWith(".")) continue;
            if (f.isDirectory()) {
                collectSingleCategoryInDir(f, mode, out, seen, snapshot, includeHidden, recentCutoff, depth + 1, maxDepth);
            } else {
                String path = f.getAbsolutePath();
                if (!seen.add(path)) continue;
                String lower = f.getName().toLowerCase(Locale.ROOT);
                boolean match = false;
                if (FileBrowserActivity.MODE_APPS.equals(mode)) {
                    match = MediaFormatHelper.isInstallableAppPackage(lower);
                } else if (FileBrowserActivity.MODE_DOCUMENTS.equals(mode)) {
                    match = MediaFormatHelper.isDocumentFile(lower);
                } else if (FileBrowserActivity.MODE_RECENT.equals(mode)) {
                    match = f.lastModified() >= recentCutoff && !lower.startsWith(".") && out.size() < 2500;
                }
                if (match) {
                    long len = f.length();
                    snapshot.sizeByPath.put(path, len);
                    snapshot.modifiedByPath.put(path, f.lastModified());
                    out.add(f);
                }
            }
        }
    }

    private static boolean isExcludedAndroidSystemPath(String path) {
        return path.contains("/Android/data/")
                || path.contains("/Android/obb/")
                || path.endsWith("/Android");
    }

    private static boolean containsHiddenSegment(String path) {
        return path.contains("/.");
    }

    private static long[] readRegularFileSizeAndMtime(File file) {
        try {
            BasicFileAttributes attr = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
            if (!attr.isRegularFile() || attr.isDirectory()) {
                return null;
            }
            return new long[]{Math.max(0L, attr.size()), Math.max(0L, attr.lastModifiedTime().toMillis())};
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static long computeDirectoryBytes(File dir, boolean includeHidden, int depth) {
        return computeDirectoryBytesAndCount(dir, includeHidden, depth)[0];
    }

    public static long[] computeDirectoryBytesAndCount(File dir, boolean includeHidden, int depth) {
        if (dir == null || depth > 5) return new long[]{0L, 0L};
        java.nio.file.Path dirPath = dir.toPath();
        try {
            BasicFileAttributes rootAttr = Files.readAttributes(dirPath, BasicFileAttributes.class,
                    java.nio.file.LinkOption.NOFOLLOW_LINKS);
            if (rootAttr.isSymbolicLink()) return new long[]{0L, 0L};
            if (!rootAttr.isDirectory()) return new long[]{Math.max(0L, rootAttr.size()), 1L};
        } catch (Throwable ignored) {
            return new long[]{0L, 0L};
        }
        long totalBytes = 0L;
        long totalCount = 0L;
        try (java.nio.file.DirectoryStream<java.nio.file.Path> stream = Files.newDirectoryStream(dirPath)) {
            for (java.nio.file.Path childPath : stream) {
                java.nio.file.Path namePath = childPath.getFileName();
                if (namePath == null) continue;
                String name = namePath.toString();
                if (!includeHidden && name.startsWith(".")) continue;
                try {
                    BasicFileAttributes attr = Files.readAttributes(childPath, BasicFileAttributes.class,
                            java.nio.file.LinkOption.NOFOLLOW_LINKS);
                    if (attr.isSymbolicLink()) continue;
                    if (attr.isDirectory()) {
                        long[] sub = computeDirectoryBytesAndCount(childPath.toFile(), includeHidden, depth + 1);
                        totalBytes += sub[0];
                        totalCount += sub[1];
                    } else {
                        totalBytes += Math.max(0L, attr.size());
                        totalCount += 1L;
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return new long[]{totalBytes, totalCount};
    }

    private static void accumulateCategorySnapshotRecursive(
            File dir,
            CategoryIndexSnapshot snapshot,
            Set<String> seenPaths,
            boolean includeHidden,
            long recentCutoff,
            int depth,
            int maxDepth
    ) {
        if (dir == null || !dir.exists() || depth > maxDepth) return;
        String dirName = dir.getName();
        if (".FileManagerPro_Trash".equals(dirName) || "Android".equals(dirName)) return;
        if (!includeHidden && dirName.startsWith(".")) return;

        File[] children = dir.listFiles();
        if (children == null) return;
        for (File f : children) {
            if (!includeHidden && f.getName().startsWith(".")) continue;
            if (f.isDirectory()) {
                accumulateCategorySnapshotRecursive(f, snapshot, seenPaths, includeHidden, recentCutoff, depth + 1, maxDepth);
            } else {
                String path = f.getAbsolutePath();
                if (!seenPaths.add(path)) continue;
                long len = f.length();
                long modMs = f.lastModified();
                String lower = f.getName().toLowerCase(Locale.ROOT);
                snapshot.sizeByPath.put(path, len);
                snapshot.modifiedByPath.put(path, modMs);

                if (MediaFormatHelper.isImageFile(lower)) {
                    snapshot.images.add(f);
                    snapshot.summary.imagesBytes += len;
                    snapshot.summary.imagesCount++;
                } else if (MediaFormatHelper.isAudioFile(lower)) {
                    snapshot.audio.add(f);
                    snapshot.summary.audioBytes += len;
                    snapshot.summary.audioCount++;
                } else if (MediaFormatHelper.isVideoFile(lower)) {
                    snapshot.videos.add(f);
                    snapshot.summary.videosBytes += len;
                    snapshot.summary.videosCount++;
                } else if (MediaFormatHelper.isDocumentFile(lower)) {
                    snapshot.documents.add(f);
                    snapshot.summary.documentsBytes += len;
                    snapshot.summary.documentsCount++;
                } else if (MediaFormatHelper.isInstallableAppPackage(lower)) {
                    snapshot.apps.add(f);
                    snapshot.summary.appsBytes += len;
                    snapshot.summary.appsCount++;
                }
                if (modMs >= recentCutoff && !lower.startsWith(".") && snapshot.recent.size() < 2500) {
                    snapshot.recent.add(f);
                    snapshot.summary.recentBytes += len;
                    snapshot.summary.recentCount++;
                }
            }
        }
    }

    // השוואת שמות כך שמספרים יופיעו ראשונים, אחריהם עברית, ואז אנגלית/אחר — עם השוואה טבעית של מספרים (2 לפני 10)
    public static int compareHebrewFirst(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        int rankA = nameLeadingScriptRank(a);
        int rankB = nameLeadingScriptRank(b);
        if (rankA != rankB) {
            return Integer.compare(rankA, rankB);
        }
        return compareNaturalText(a, b);
    }

    private static int nameLeadingScriptRank(String s) {
        if (s == null) return 2;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                return 0;
            }
            if (Character.isLetter(c)) {
                return (c >= '\u0590' && c <= '\u05FF') ? 1 : 2;
            }
        }
        return 2;
    }

    public static boolean startsWithDigit(String s) {
        return nameLeadingScriptRank(s) == 0;
    }

    public static boolean startsWithHebrew(String s) {
        return nameLeadingScriptRank(s) == 1;
    }

    private static int compareNaturalText(String a, String b) {
        int ia = 0;
        int ib = 0;
        int na = a.length();
        int nb = b.length();
        Collator heCollator = HE_COLLATOR.get();
        while (ia < na && ib < nb) {
            char ca = a.charAt(ia);
            char cb = b.charAt(ib);
            boolean da = isAsciiDigit(ca);
            boolean db = isAsciiDigit(cb);
            if (da && db) {
                int ea = ia;
                while (ea < na && isAsciiDigit(a.charAt(ea))) ea++;
                int eb = ib;
                while (eb < nb && isAsciiDigit(b.charAt(eb))) eb++;
                int numCmp = compareDigitRuns(a, ia, ea, b, ib, eb);
                if (numCmp != 0) return numCmp;
                ia = ea;
                ib = eb;
                continue;
            }
            if (da != db) {
                return da ? -1 : 1;
            }
            int ea = ia;
            while (ea < na && !isAsciiDigit(a.charAt(ea))) ea++;
            int eb = ib;
            while (eb < nb && !isAsciiDigit(b.charAt(eb))) eb++;
            String sa = a.substring(ia, ea);
            String sb = b.substring(ib, eb);
            int saRank = nameLeadingScriptRank(sa);
            int sbRank = nameLeadingScriptRank(sb);
            if (saRank != sbRank) {
                return Integer.compare(saRank, sbRank);
            }
            int textCmp = (heCollator != null)
                    ? heCollator.compare(sa, sb)
                    : sa.compareToIgnoreCase(sb);
            if (textCmp == 0) {
                textCmp = sa.compareToIgnoreCase(sb);
            }
            if (textCmp != 0) return textCmp;
            ia = ea;
            ib = eb;
        }
        return Integer.compare(na - ia, nb - ib);
    }

    private static boolean isAsciiDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static int compareDigitRuns(String a, int sa, int ea, String b, int sb, int eb) {
        int za = sa;
        while (za < ea && a.charAt(za) == '0') za++;
        int zb = sb;
        while (zb < eb && b.charAt(zb) == '0') zb++;
        int lenA = ea - za;
        int lenB = eb - zb;
        if (lenA != lenB) {
            return Integer.compare(lenA, lenB);
        }
        for (int i = 0; i < lenA; i++) {
            char ca = a.charAt(za + i);
            char cb = b.charAt(zb + i);
            if (ca != cb) {
                return Character.compare(ca, cb);
            }
        }
        // בשוויון ערך מספרי (למשל 01 לעומת 1), קצר יותר מופיע קודם
        return Integer.compare(ea - sa, eb - sb);
    }

    public static StorageStat getInternalStorageStat() {
        try {
            File path = Environment.getExternalStorageDirectory();
            StatFs stat = new StatFs(path.getAbsolutePath());
            long total = stat.getTotalBytes();
            long free = stat.getAvailableBytes();
            long used = Math.max(0, total - free);
            return new StorageStat(path, "אחסון ראשי", total, used, free, true);
        } catch (Exception e) {
            return new StorageStat(null, 0, 0, 0, false);
        }
    }

    public static StorageStat getExternalSdCardStat(Context context) {
        try {
            File[] dirs = context.getExternalFilesDirs(null);
            if (dirs != null && dirs.length > 1 && dirs[1] != null) {
                File sdAppDir = dirs[1];
                File sdRoot = sdAppDir;
                for (int i = 0; i < 4 && sdRoot.getParentFile() != null; i++) {
                    sdRoot = sdRoot.getParentFile();
                }
                if (sdRoot.exists()) {
                    StatFs stat = new StatFs(sdRoot.getAbsolutePath());
                    long total = stat.getTotalBytes();
                    long free = stat.getAvailableBytes();
                    long used = Math.max(0, total - free);
                    return new StorageStat(sdRoot, "כרטיס SD", total, used, free, total > 0);
                }
            }
        } catch (Exception ignored) {
        }
        return new StorageStat(null, 0, 0, 0, false);
    }

    public static String checkAndNotify96Threshold(Context context) {
        StorageStat internal = getInternalStorageStat();
        StorageStat sd = getExternalSdCardStat(context);

        boolean internalCritical = internal.available && internal.usedPercent >= CRITICAL_THRESHOLD_PERCENT;
        boolean sdCritical = sd.available && sd.usedPercent >= CRITICAL_THRESHOLD_PERCENT;

        if (!internalCritical && !sdCritical) {
            return null;
        }

        StringBuilder msg = new StringBuilder();
        if (internalCritical) {
            msg.append(String.format(Locale.US, "האחסון הראשי הגיע ל-%.1f%% תפוסה (%s פנויים בלבד).",
                    internal.usedPercent, formatSize(internal.freeBytes)));
        }
        if (sdCritical) {
            if (msg.length() > 0) msg.append("\n");
            msg.append(String.format(Locale.US, "כרטיס ה-SD החיצוני הגיע ל-%.1f%% תפוסה (%s פנויים בלבד).",
                    sd.usedPercent, formatSize(sd.freeBytes)));
        }

        if (PrefsManager.isAlert96Enabled(context)) {
            postSystemAlertNotification(context, msg.toString());
        }

        return msg.toString();
    }

    private static void postSystemAlertNotification(Context context, String contentText) {
        try {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;

            NotificationChannel channel = new NotificationChannel(
                    ALERT_CHANNEL_ID,
                    "התרעות תפוסת זיכרון (96%)",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("מתריע כאשר הזיכרון הפנימי או החיצוני מגיעים ל-96% תפוסה");
            nm.createNotificationChannel(channel);

            Intent analyzerIntent = new Intent(context, StorageAnalyzerActivity.class);
            PendingIntent pi = PendingIntent.getActivity(
                    context,
                    96,
                    analyzerIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_app_logo_notif)
                    .setContentTitle("אזהרת נפח אחסון קריטית (מעל 96%)")
                    .setContentText(contentText)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(contentText))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(pi)
                    .setAutoCancel(true);

            nm.notify(9601, builder.build());
        } catch (Exception ignored) {
        }
    }

    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.US, "%.2f MB", mb);
        double gb = mb / 1024.0;
        return String.format(Locale.US, "%.2f GB", gb);
    }

    public interface CopyProgressCallback {
        void onProgress(String currentFileName, long currentFileBytesDone, long currentFileTotalBytes, int completedFiles, int totalFiles);
    }

    public static int countTotalFileEntries(File root) {
        if (root == null || !root.exists()) return 0;
        if (!root.isDirectory()) return 1;
        File[] children = root.listFiles();
        if (children == null || children.length == 0) return 1;
        int sum = 0;
        for (File child : children) {
            sum += countTotalFileEntries(child);
        }
        return Math.max(1, sum);
    }

    public static boolean copyFileOrDir(File src, File dstDir) throws IOException {
        return copyFileOrDir(src, dstDir, null, null, 1);
    }

    public static boolean copyFileOrDir(File src, File dstDir, CopyProgressCallback cb, int[] completedCounter, int totalFiles) throws IOException {
        if (!src.exists()) return false;
        if (!dstDir.exists() && !dstDir.mkdirs()) return false;

        File target = new File(dstDir, src.getName());
        if (target.exists()) {
            target = uniqueDestinationFile(dstDir, src.getName());
        }

        if (src.isDirectory()) {
            if (!target.mkdirs()) return false;
            File[] children = src.listFiles();
            if (children != null && children.length > 0) {
                for (File child : children) {
                    copyFileOrDir(child, target, cb, completedCounter, totalFiles);
                }
            } else if (completedCounter != null) {
                completedCounter[0]++;
                if (cb != null) {
                    cb.onProgress(src.getName(), 0L, 0L, completedCounter[0], totalFiles);
                }
            }
            return true;
        } else {
            long totalBytes = Math.max(0L, src.length());
            int doneBefore = completedCounter != null ? completedCounter[0] : 0;
            if (cb != null) {
                cb.onProgress(src.getName(), 0L, totalBytes, doneBefore, totalFiles);
            }
            long copiedBytes = 0L;
            long lastReportMs = 0L;
            boolean usedFallbackStream = false;

            try (FileInputStream fis = new FileInputStream(src);
                 FileOutputStream fos = new FileOutputStream(target);
                 FileChannel inCh = fis.getChannel();
                 FileChannel outCh = fos.getChannel()) {

                final long chunk = 2L * 1024L * 1024L;
                while (copiedBytes < totalBytes) {
                    long count = Math.min(chunk, totalBytes - copiedBytes);
                    long transferred = inCh.transferTo(copiedBytes, count, outCh);
                    if (transferred <= 0L) {
                        usedFallbackStream = true;
                        break;
                    }
                    copiedBytes += transferred;
                    if (cb != null) {
                        long now = System.currentTimeMillis();
                        if (now - lastReportMs >= 80L || copiedBytes >= totalBytes) {
                            lastReportMs = now;
                            cb.onProgress(src.getName(), copiedBytes, totalBytes, doneBefore, totalFiles);
                        }
                    }
                }

                if (usedFallbackStream || copiedBytes < totalBytes) {
                    inCh.position(copiedBytes);
                    outCh.position(copiedBytes);
                    byte[] buf = new byte[512 * 1024];
                    int r;
                    while ((r = fis.read(buf)) != -1) {
                        fos.write(buf, 0, r);
                        copiedBytes += r;
                        if (cb != null) {
                            long now = System.currentTimeMillis();
                            if (now - lastReportMs >= 80L || copiedBytes >= totalBytes) {
                                lastReportMs = now;
                                cb.onProgress(src.getName(), copiedBytes, totalBytes, doneBefore, totalFiles);
                            }
                        }
                    }
                }
                fos.flush();
            }
            if (completedCounter != null) {
                completedCounter[0]++;
            }
            if (cb != null) {
                int doneAfter = completedCounter != null ? completedCounter[0] : (doneBefore + 1);
                cb.onProgress(src.getName(), totalBytes, totalBytes, doneAfter, totalFiles);
            }
            return true;
        }
    }

    public static boolean moveFileOrDir(File src, File dstDir) throws IOException {
        return moveFileOrDir(src, dstDir, null, null, 1);
    }

    public static boolean moveFileOrDir(File src, File dstDir, CopyProgressCallback cb, int[] completedCounter, int totalFiles) throws IOException {
        if (!src.exists()) return false;
        if (!dstDir.exists() && !dstDir.mkdirs()) return false;

        int entriesCount = countTotalFileEntries(src);
        long sizeBytes = src.isFile() ? Math.max(0L, src.length()) : 0L;
        if (cb != null) {
            cb.onProgress(src.getName(), 0L, sizeBytes, completedCounter != null ? completedCounter[0] : 0, totalFiles);
        }

        File target = uniqueDestinationFile(dstDir, src.getName());
        if (src.renameTo(target)) {
            if (completedCounter != null) {
                completedCounter[0] += entriesCount;
            }
            if (cb != null) {
                cb.onProgress(src.getName(), sizeBytes, sizeBytes, completedCounter != null ? completedCounter[0] : entriesCount, totalFiles);
            }
            return true;
        }
        if (copyFileOrDir(src, dstDir, cb, completedCounter, totalFiles)) {
            return deleteRecursively(src);
        }
        return false;
    }

    public static boolean deleteRecursively(File target) {
        if (target == null || !target.exists()) return false;
        if (target.isDirectory()) {
            File[] children = target.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        return target.delete();
    }

    public static File uniqueDestinationFile(File dir, String originalName) {
        File candidate = new File(dir, originalName);
        if (!candidate.exists()) return candidate;

        int dot = originalName.lastIndexOf('.');
        String base = dot > 0 ? originalName.substring(0, dot) : originalName;
        String ext = dot > 0 ? originalName.substring(dot) : "";
        int index = 1;
        while (candidate.exists()) {
            candidate = new File(dir, base + " (" + index + ")" + ext);
            index++;
        }
        return candidate;
    }

    public static boolean ejectRemovableStorageOrOpenSettings(Context context, String targetPath, boolean isSdCard) {
        if (context == null) return false;
        FileScanCache.invalidate();
        try {
            StorageManager sm = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
            if (sm != null) {
                String volumeId = resolveRemovableVolumeId(sm, targetPath);
                if (volumeId != null && !volumeId.isEmpty()) {
                    try {
                        java.lang.reflect.Method unmountMethod = StorageManager.class.getMethod("unmount", String.class);
                        unmountMethod.invoke(sm, volumeId);
                        return true;
                    } catch (Throwable ignored) {
                        // Normal on stock Android without privileged MOUNT_UNMOUNT_FILESYSTEMS
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        openSystemStorageSettings(context, isSdCard);
        return false;
    }

    private static String resolveRemovableVolumeId(StorageManager sm, String targetPath) {
        String normalizedTarget = targetPath == null ? "" : targetPath.replaceAll("/+$", "");
        try {
            List<StorageVolume> volumes = sm.getStorageVolumes();
            for (StorageVolume vol : volumes) {
                if (vol == null || vol.isPrimary() || !vol.isRemovable()) continue;
                String volPath = extractStorageVolumePath(vol);
                String normVol = volPath == null ? "" : volPath.replaceAll("/+$", "");
                boolean match = !normalizedTarget.isEmpty() && !normVol.isEmpty()
                        && (normalizedTarget.equals(normVol) || normalizedTarget.startsWith(normVol + "/"));
                if (match || normalizedTarget.isEmpty()) {
                    try {
                        java.lang.reflect.Method getId = StorageVolume.class.getMethod("getId");
                        Object idObj = getId.invoke(vol);
                        if (idObj instanceof String && !((String) idObj).isEmpty()) {
                            return (String) idObj;
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            java.lang.reflect.Method getVolumes = StorageManager.class.getMethod("getVolumes");
            Object listObj = getVolumes.invoke(sm);
            if (listObj instanceof List<?>) {
                for (Object vInfo : (List<?>) listObj) {
                    if (vInfo == null) continue;
                    String id = (String) vInfo.getClass().getMethod("getId").invoke(vInfo);
                    Object pathObj = null;
                    try {
                        pathObj = vInfo.getClass().getMethod("getPath").invoke(vInfo);
                    } catch (Throwable ignored) {
                    }
                    String volPath = pathObj instanceof File ? ((File) pathObj).getAbsolutePath() : "";
                    if (!normalizedTarget.isEmpty() && normalizedTarget.equals(volPath.replaceAll("/+$", "")) && id != null) {
                        return id;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String extractStorageVolumePath(StorageVolume vol) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            File dir = vol.getDirectory();
            if (dir != null) return dir.getAbsolutePath();
        }
        try {
            java.lang.reflect.Method m = StorageVolume.class.getMethod("getPath");
            Object res = m.invoke(vol);
            if (res instanceof String) return (String) res;
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static void openSystemStorageSettings(Context context, boolean isSdCard) {
        if (context == null) return;
        String[] actions = isSdCard
                ? new String[]{
                android.provider.Settings.ACTION_MEMORY_CARD_SETTINGS,
                android.provider.Settings.ACTION_INTERNAL_STORAGE_SETTINGS,
                android.provider.Settings.ACTION_SETTINGS
        }
                : new String[]{
                android.provider.Settings.ACTION_INTERNAL_STORAGE_SETTINGS,
                android.provider.Settings.ACTION_MEMORY_CARD_SETTINGS,
                android.provider.Settings.ACTION_SETTINGS
        };
        for (String action : actions) {
            try {
                Intent i = new Intent(action);
                if (!(context instanceof android.app.Activity)) {
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                }
                context.startActivity(i);
                return;
            } catch (Throwable ignored) {
            }
        }
    }
}
