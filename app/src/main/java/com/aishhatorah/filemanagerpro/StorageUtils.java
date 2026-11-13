package com.aishhatorah.filemanagerpro;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;

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
import java.util.List;
import java.util.Locale;

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
        CategoryBytesSummary summary = new CategoryBytesSummary();
        summary.recycleBinBytes = RecycleBinManager.getTrashTotalBytes(context);
        summary.recycleBinCount = RecycleBinManager.getTrashItemCount(context);

        File dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        long[] dlStats = computeDirectoryBytesAndCount(dl, includeHidden, 0);
        summary.downloadsBytes = dlStats[0];
        summary.downloadsCount = (int) dlStats[1];

        long sevenDaysAgo = System.currentTimeMillis() - (7L * 24L * 3600L * 1000L);
        long recentCutoff = Math.max(sevenDaysAgo, PrefsManager.getRecentClearedTimestamp(context));
        File root = Environment.getExternalStorageDirectory();
        accumulateCategoryBytes(root, summary, includeHidden, recentCutoff, 0);

        StorageStat sd = getExternalSdCardStat(context);
        if (sd.available && sd.root != null) {
            accumulateCategoryBytes(sd.root, summary, includeHidden, recentCutoff, 0);
        }
        return summary;
    }

    public static long computeDirectoryBytes(File dir, boolean includeHidden, int depth) {
        return computeDirectoryBytesAndCount(dir, includeHidden, depth)[0];
    }

    public static long[] computeDirectoryBytesAndCount(File dir, boolean includeHidden, int depth) {
        if (dir == null || !dir.exists() || depth > 6) return new long[]{0L, 0L};
        if (!dir.isDirectory()) return new long[]{dir.length(), 1L};
        File[] children = dir.listFiles();
        if (children == null) return new long[]{0L, 0L};
        long totalBytes = 0L;
        long totalCount = 0L;
        for (File f : children) {
            if (!includeHidden && f.getName().startsWith(".")) continue;
            if (f.isDirectory()) {
                long[] sub = computeDirectoryBytesAndCount(f, includeHidden, depth + 1);
                totalBytes += sub[0];
                totalCount += sub[1];
            } else {
                totalBytes += f.length();
                totalCount += 1L;
            }
        }
        return new long[]{totalBytes, totalCount};
    }

    private static void accumulateCategoryBytes(File dir, CategoryBytesSummary out, boolean includeHidden, long recentCutoff, int depth) {
        if (dir == null || !dir.exists() || depth > 7) return;
        String dirName = dir.getName();
        if (".FileManagerPro_Trash".equals(dirName) || "Android".equals(dirName)) return;
        if (!includeHidden && dirName.startsWith(".")) return;

        File[] children = dir.listFiles();
        if (children == null) return;
        for (File f : children) {
            if (!includeHidden && f.getName().startsWith(".")) continue;
            if (f.isDirectory()) {
                accumulateCategoryBytes(f, out, includeHidden, recentCutoff, depth + 1);
            } else {
                long len = f.length();
                String lower = f.getName().toLowerCase(Locale.ROOT);
                if (MediaFormatHelper.isImageFile(lower)) {
                    out.imagesBytes += len;
                    out.imagesCount++;
                } else if (MediaFormatHelper.isAudioFile(lower)) {
                    out.audioBytes += len;
                    out.audioCount++;
                } else if (MediaFormatHelper.isVideoFile(lower)) {
                    out.videosBytes += len;
                    out.videosCount++;
                } else if (MediaFormatHelper.isDocumentFile(lower)) {
                    out.documentsBytes += len;
                    out.documentsCount++;
                } else if (MediaFormatHelper.isInstallableAppPackage(lower)) {
                    out.appsBytes += len;
                    out.appsCount++;
                }
                if (f.lastModified() >= recentCutoff && !lower.startsWith(".")) {
                    out.recentBytes += len;
                    out.recentCount++;
                }
            }
        }
    }

    // השוואת שמות כך שתיקיות ושמות בעברית יופיעו תמיד לפני שמות באנגלית
    public static int compareHebrewFirst(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        boolean aHeb = startsWithHebrew(a);
        boolean bHeb = startsWithHebrew(b);
        if (aHeb != bHeb) {
            return aHeb ? -1 : 1;
        }
        if (aHeb) {
            Collator heCollator = Collator.getInstance(new Locale("he", "IL"));
            return heCollator.compare(a, b);
        }
        return a.compareToIgnoreCase(b);
    }

    public static boolean startsWithHebrew(String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                return c >= '\u0590' && c <= '\u05FF';
            }
        }
        return false;
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
                    .setSmallIcon(R.drawable.ic_storage_internal)
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
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb);
        double gb = mb / 1024.0;
        return String.format(Locale.US, "%.2f GB", gb);
    }

    public static boolean copyFileOrDir(File src, File dstDir) throws IOException {
        if (!src.exists()) return false;
        if (!dstDir.exists() && !dstDir.mkdirs()) return false;

        File target = new File(dstDir, src.getName());
        if (target.exists()) {
            target = uniqueDestinationFile(dstDir, src.getName());
        }

        if (src.isDirectory()) {
            if (!target.mkdirs()) return false;
            File[] children = src.listFiles();
            if (children != null) {
                for (File child : children) {
                    copyFileOrDir(child, target);
                }
            }
            return true;
        } else {
            try (FileInputStream fis = new FileInputStream(src);
                 FileOutputStream fos = new FileOutputStream(target);
                 FileChannel inChannel = fis.getChannel();
                 FileChannel outChannel = fos.getChannel()) {
                inChannel.transferTo(0, inChannel.size(), outChannel);
            }
            return true;
        }
    }

    public static boolean moveFileOrDir(File src, File dstDir) throws IOException {
        if (!src.exists()) return false;
        if (!dstDir.exists() && !dstDir.mkdirs()) return false;

        File target = uniqueDestinationFile(dstDir, src.getName());
        if (src.renameTo(target)) {
            return true;
        }
        if (copyFileOrDir(src, dstDir)) {
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
}
