package com.aishhatorah.filemanagerpro;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.os.StatFs;

import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.text.Collator;
import java.util.Locale;

public class StorageUtils {

    private static final String ALERT_CHANNEL_ID = "storage_capacity_alert_96";
    public static final double CRITICAL_THRESHOLD_PERCENT = 96.0;

    public static class StorageStat {
        public final File root;
        public final long totalBytes;
        public final long usedBytes;
        public final long freeBytes;
        public final double usedPercent;
        public final boolean available;

        public StorageStat(File root, long totalBytes, long usedBytes, long freeBytes, boolean available) {
            this.root = root;
            this.totalBytes = totalBytes;
            this.usedBytes = usedBytes;
            this.freeBytes = freeBytes;
            this.available = available;
            this.usedPercent = totalBytes > 0 ? (usedBytes * 100.0) / totalBytes : 0.0;
        }
    }

    public static class CategoryBytesSummary {
        public long downloadsBytes = 0L;
        public long imagesBytes = 0L;
        public long audioBytes = 0L;
        public long videosBytes = 0L;
        public long documentsBytes = 0L;
        public long appsBytes = 0L;
        public long recentBytes = 0L;
        public long recycleBinBytes = 0L;
    }

    public static CategoryBytesSummary computeCategorySizes(Context context, boolean includeHidden) {
        CategoryBytesSummary summary = new CategoryBytesSummary();
        summary.recycleBinBytes = RecycleBinManager.getTrashTotalBytes(context);

        File dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        summary.downloadsBytes = computeDirectoryBytes(dl, includeHidden, 0);

        long recentCutoff = System.currentTimeMillis() - (7L * 24L * 3600L * 1000L);
        File root = Environment.getExternalStorageDirectory();
        accumulateCategoryBytes(root, summary, includeHidden, recentCutoff, 0);

        StorageStat sd = getExternalSdCardStat(context);
        if (sd.available && sd.root != null) {
            accumulateCategoryBytes(sd.root, summary, includeHidden, recentCutoff, 0);
        }
        return summary;
    }

    public static long computeDirectoryBytes(File dir, boolean includeHidden, int depth) {
        if (dir == null || !dir.exists() || depth > 6) return 0L;
        if (!dir.isDirectory()) return dir.length();
        File[] children = dir.listFiles();
        if (children == null) return 0L;
        long total = 0L;
        for (File f : children) {
            if (!includeHidden && f.getName().startsWith(".")) continue;
            if (f.isDirectory()) {
                total += computeDirectoryBytes(f, includeHidden, depth + 1);
            } else {
                total += f.length();
            }
        }
        return total;
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
                if (endsWithAny(lower, ".jpg", ".jpeg", ".png", ".webp", ".gif", ".heic", ".bmp")) {
                    out.imagesBytes += len;
                } else if (endsWithAny(lower, ".mp3", ".m4a", ".wav", ".aac", ".flac", ".ogg")) {
                    out.audioBytes += len;
                } else if (endsWithAny(lower, ".mp4", ".mkv", ".mov", ".avi", ".webm", ".3gp")) {
                    out.videosBytes += len;
                } else if (endsWithAny(lower, ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".txt", ".rtf", ".csv", ".md")) {
                    out.documentsBytes += len;
                } else if (endsWithAny(lower, ".apk", ".xapk", ".apks")) {
                    out.appsBytes += len;
                }
                if (f.lastModified() >= recentCutoff) {
                    out.recentBytes += len;
                }
            }
        }
    }

    private static boolean endsWithAny(String name, String... exts) {
        for (String e : exts) {
            if (name.endsWith(e)) return true;
        }
        return false;
    }

    // השוואת שמות כך שתיקיות ושמות בעברית יופיעו תמיד לפני שמות באנגלית (סעיף כא)
    public static int compareHebrewFirst(String a, String b) {
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
            return new StorageStat(path, total, used, free, true);
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
                    return new StorageStat(sdRoot, total, used, free, total > 0);
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
