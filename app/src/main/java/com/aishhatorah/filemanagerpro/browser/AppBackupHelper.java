package com.aishhatorah.filemanagerpro.browser;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.aishhatorah.filemanagerpro.StorageUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * ניהול חבילות אפליקציות מותקנות: טעינת base.apk + split APKs,
 * חישוב נפח אמיתי, גיבוי תקין של אפליקציות רגילות (.apk) ומפוצלות (.apks),
 * והכנת חבילות לשיתוף.
 */
public final class AppBackupHelper {

    public static class InstalledAppEntry {
        public String appName;
        public String packageName;
        public String versionName;
        public long sizeBytes;
        public File sourceApk;
        public final List<File> splitApks = new ArrayList<>();
        public boolean isSystem;

        public boolean isSplitBundle() {
            return !splitApks.isEmpty();
        }
    }

    private AppBackupHelper() {
    }

    public static List<InstalledAppEntry> loadInstalledApps(PackageManager pm) {
        List<InstalledAppEntry> entries = new ArrayList<>();
        if (pm == null) return entries;
        try {
            List<PackageInfo> pkgs = pm.getInstalledPackages(0);
            for (PackageInfo pi : pkgs) {
                if (pi == null || pi.applicationInfo == null) continue;
                ApplicationInfo ai = pi.applicationInfo;
                InstalledAppEntry e = new InstalledAppEntry();
                e.packageName = pi.packageName != null ? pi.packageName : "";
                e.versionName = (pi.versionName != null && !pi.versionName.trim().isEmpty())
                        ? pi.versionName.trim() : "1.0";
                e.isSystem = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                CharSequence lbl = ai.loadLabel(pm);
                e.appName = (lbl != null && lbl.length() > 0) ? lbl.toString() : e.packageName;

                String basePath = ai.publicSourceDir != null ? ai.publicSourceDir : ai.sourceDir;
                e.sourceApk = (basePath != null && !basePath.isEmpty()) ? new File(basePath) : null;

                long totalBytes = (e.sourceApk != null && e.sourceApk.exists()) ? e.sourceApk.length() : 0L;
                Set<String> seenPaths = new HashSet<>();
                if (e.sourceApk != null) {
                    seenPaths.add(e.sourceApk.getAbsolutePath());
                }

                String[] splits = (ai.splitPublicSourceDirs != null && ai.splitPublicSourceDirs.length > 0)
                        ? ai.splitPublicSourceDirs
                        : ai.splitSourceDirs;
                if (splits != null) {
                    for (String sp : splits) {
                        if (sp == null || sp.isEmpty()) continue;
                        File sf = new File(sp);
                        if (sf.exists() && sf.isFile() && seenPaths.add(sf.getAbsolutePath())) {
                            e.splitApks.add(sf);
                            totalBytes += sf.length();
                        }
                    }
                }
                e.sizeBytes = totalBytes;
                entries.add(e);
            }
            Collections.sort(entries, (a, b) -> StorageUtils.compareHebrewFirst(a.appName, b.appName));
        } catch (Throwable ignored) {
        }
        return entries;
    }

    public static String sanitizeFileName(String raw) {
        if (raw == null) return "app";
        String cleaned = raw.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]+", "_").trim();
        while (cleaned.startsWith(".")) {
            cleaned = cleaned.substring(1).trim();
        }
        return cleaned.isEmpty() ? "app" : cleaned;
    }

    public static String buildBackupFileName(InstalledAppEntry app) {
        String namePart = sanitizeFileName(app != null ? app.appName : "app");
        String verPart = sanitizeFileName(app != null ? app.versionName : "1.0");
        String ext = (app != null && app.isSplitBundle()) ? ".apks" : ".apk";
        return namePart + "_" + verPart + ext;
    }

    public static File backupAppToDownloads(InstalledAppEntry app, File downloadsDir) throws IOException {
        if (app == null || app.sourceApk == null || !app.sourceApk.exists()) {
            throw new IOException("קובץ ההתקנה של האפליקציה אינו זמין");
        }
        if (downloadsDir == null) {
            throw new IOException("תיקיית היעד אינה זמינה");
        }
        if (!downloadsDir.exists() && !downloadsDir.mkdirs()) {
            throw new IOException("לא ניתן ליצור את תיקיית היעד");
        }

        File out = StorageUtils.uniqueDestinationFile(downloadsDir, buildBackupFileName(app));
        if (app.isSplitBundle()) {
            createSplitApksArchive(app.sourceApk, app.splitApks, out);
        } else {
            copyFile(app.sourceApk, out);
        }
        return out;
    }

    public static File prepareShareablePackage(Context context, InstalledAppEntry app) throws IOException {
        if (app == null || app.sourceApk == null || !app.sourceApk.exists()) {
            throw new IOException("קובץ ההתקנה אינו זמין");
        }
        if (!app.isSplitBundle()) {
            return app.sourceApk;
        }
        File shareDir = new File(context.getCacheDir(), "shared_apks");
        if (!shareDir.exists() && !shareDir.mkdirs()) {
            throw new IOException("לא ניתן ליצור תיקיית שיתוף זמנית");
        }
        File out = new File(shareDir, buildBackupFileName(app));
        createSplitApksArchive(app.sourceApk, app.splitApks, out);
        return out;
    }

    public static void createSplitApksArchive(File baseApk, List<File> splitApks, File destZip) throws IOException {
        if (baseApk == null || !baseApk.exists() || !baseApk.isFile()) {
            throw new IOException("קובץ base.apk חסר");
        }
        if (destZip.getParentFile() != null && !destZip.getParentFile().exists()) {
            //noinspection ResultOfMethodCallIgnored
            destZip.getParentFile().mkdirs();
        }
        Set<String> usedNames = new HashSet<>();
        boolean success = false;
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(destZip)))) {
            zos.setLevel(Deflater.BEST_SPEED);
            String baseEntryName = ensureUniqueApkEntryName(usedNames, "base.apk");
            writeZipApkEntry(zos, baseApk, baseEntryName);

            if (splitApks != null) {
                int idx = 1;
                for (File split : splitApks) {
                    if (split == null || !split.exists() || !split.isFile()) continue;
                    String rawName = split.getName();
                    if (rawName.isEmpty() || !rawName.toLowerCase(java.util.Locale.ROOT).endsWith(".apk")) {
                        rawName = "split_" + idx + ".apk";
                    }
                    String entryName = ensureUniqueApkEntryName(usedNames, rawName);
                    writeZipApkEntry(zos, split, entryName);
                    idx++;
                }
            }
            zos.finish();
            success = true;
        } finally {
            if (!success && destZip.exists()) {
                //noinspection ResultOfMethodCallIgnored
                destZip.delete();
            }
        }
    }

    private static String ensureUniqueApkEntryName(Set<String> usedNames, String candidate) {
        String clean = sanitizeFileName(candidate);
        if (!clean.toLowerCase(java.util.Locale.ROOT).endsWith(".apk")) {
            clean = clean + ".apk";
        }
        if (usedNames.add(clean.toLowerCase(java.util.Locale.ROOT))) {
            return clean;
        }
        String stem = clean.substring(0, clean.length() - 4);
        int i = 1;
        while (true) {
            String next = stem + "_" + i + ".apk";
            if (usedNames.add(next.toLowerCase(java.util.Locale.ROOT))) {
                return next;
            }
            i++;
        }
    }

    private static void writeZipApkEntry(ZipOutputStream zos, File src, String entryName) throws IOException {
        ZipEntry ze = new ZipEntry(entryName);
        ze.setTime(src.lastModified());
        zos.putNextEntry(ze);
        byte[] buf = new byte[32 * 1024];
        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(src))) {
            int r;
            while ((r = bis.read(buf)) != -1) {
                zos.write(buf, 0, r);
            }
        }
        zos.closeEntry();
    }

    public static void copyFile(File src, File dst) throws IOException {
        if (src == null || !src.exists()) {
            throw new IOException("קובץ המקור אינו קיים");
        }
        if (dst.getParentFile() != null && !dst.getParentFile().exists()) {
            //noinspection ResultOfMethodCallIgnored
            dst.getParentFile().mkdirs();
        }
        byte[] buf = new byte[32 * 1024];
        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(src));
             BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(dst))) {
            int r;
            while ((r = bis.read(buf)) != -1) {
                bos.write(buf, 0, r);
            }
            bos.flush();
        }
    }
}
