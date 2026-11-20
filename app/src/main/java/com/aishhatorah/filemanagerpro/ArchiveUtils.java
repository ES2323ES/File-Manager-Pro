package com.aishhatorah.filemanagerpro;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * סעיף 2: מנוע דחיסה, חילוץ, צפייה ועריכה פנימית מתקדם ב-Java טהורה:
 * - תמיכה ב-ZIP, TAR, TAR.GZ / TGZ, GZ
 * - תמיכה בפתיחה ויצירה של ארכיוני ZIP מוגנים בסיסמה (ZipCrypto תקני)
 * - הגנת Zip-Slip מחמירה מפני נתיבים זדוניים
 * - קריאה, עריכה, הוספה ומחיקה של קבצים ישירות בתוך הארכיון
 */
public class ArchiveUtils {

    public static class ArchiveEntryInfo {
        public final String name;
        public final long size;
        public final long compressedSize;
        public final boolean directory;
        public final boolean encrypted;

        public ArchiveEntryInfo(String name, long size, long compressedSize, boolean directory, boolean encrypted) {
            this.name = name == null ? "" : name;
            this.size = size;
            this.compressedSize = compressedSize;
            this.directory = directory;
            this.encrypted = encrypted;
        }
    }

    public static class ArchiveTreeNode {
        public final String displayName;
        public final String fullPath;
        public final boolean directory;
        public final long size;
        public final long compressedSize;
        public final boolean encrypted;
        public final ArchiveEntryInfo entryInfo;

        public ArchiveTreeNode(String displayName, String fullPath, boolean directory,
                               long size, long compressedSize, boolean encrypted, ArchiveEntryInfo entryInfo) {
            this.displayName = displayName;
            this.fullPath = fullPath;
            this.directory = directory;
            this.size = size;
            this.compressedSize = compressedSize;
            this.encrypted = encrypted;
            this.entryInfo = entryInfo;
        }
    }

    public static String normalizeTreeFolder(String folder) {
        if (folder == null) return "";
        String f = folder.replace('\\', '/').trim();
        while (f.startsWith("/")) f = f.substring(1);
        if (!f.isEmpty() && !f.endsWith("/")) f += "/";
        return f;
    }

    public static List<ArchiveTreeNode> listTreeChildren(List<ArchiveEntryInfo> entries, String currentFolder) {
        String prefix = normalizeTreeFolder(currentFolder);
        Map<String, ArchiveTreeNode> dirs = new LinkedHashMap<>();
        List<ArchiveTreeNode> files = new ArrayList<>();
        if (entries == null) return Collections.emptyList();

        for (ArchiveEntryInfo e : entries) {
            if (e == null || e.name == null) continue;
            String norm = e.name.replace('\\', '/');
            while (norm.startsWith("/")) norm = norm.substring(1);
            if (norm.isEmpty() || !norm.startsWith(prefix)) continue;

            String rest = norm.substring(prefix.length());
            if (rest.isEmpty() || "/".equals(rest)) continue;

            int slashIdx = rest.indexOf('/');
            if (slashIdx >= 0) {
                String dirName = rest.substring(0, slashIdx);
                if (dirName.isEmpty()) continue;
                String dirPath = prefix + dirName + "/";
                ArchiveTreeNode existing = dirs.get(dirPath);
                boolean isExactDirEntry = (slashIdx == rest.length() - 1) && e.directory;
                if (existing == null || isExactDirEntry) {
                    dirs.put(dirPath, new ArchiveTreeNode(
                            dirName,
                            dirPath,
                            true,
                            0L,
                            0L,
                            e.encrypted || (existing != null && existing.encrypted),
                            isExactDirEntry ? e : (existing != null ? existing.entryInfo : null)
                    ));
                }
            } else if (!e.directory) {
                files.add(new ArchiveTreeNode(rest, e.name, false, e.size, e.compressedSize, e.encrypted, e));
            }
        }

        List<ArchiveTreeNode> dirList = new ArrayList<>(dirs.values());
        Collections.sort(dirList, (a, b) -> StorageUtils.compareHebrewFirst(a.displayName, b.displayName));
        Collections.sort(files, (a, b) -> StorageUtils.compareHebrewFirst(a.displayName, b.displayName));
        List<ArchiveTreeNode> out = new ArrayList<>(dirList.size() + files.size());
        out.addAll(dirList);
        out.addAll(files);
        return out;
    }

    public static boolean isSafeEntryName(String name) {
        if (name == null || name.trim().isEmpty()) return false;
        String norm = name.replace('\\', '/');
        if (norm.startsWith("/")) return false;
        String[] parts = norm.split("/");
        for (String p : parts) {
            if ("..".equals(p)) return false;
        }
        return true;
    }

    public static File compressToZip(File source, File targetZipFile) throws IOException {
        List<File> list = new ArrayList<>();
        list.add(source);
        return compressFiles(list, targetZipFile, "zip", null);
    }

    public interface ArchiveProgressCallback {
        void onProgress(String itemName, int done, int total);
    }

    public static File compressFiles(List<File> sources, File targetFile, String format, String password) throws IOException {
        return compressFiles(sources, targetFile, format, password, null);
    }

    public static File compressFiles(List<File> sources, File targetFile, String format, String password, ArchiveProgressCallback cb) throws IOException {
        if (sources == null || sources.isEmpty()) {
            throw new IOException("לא נבחרו קבצים לדחיסה");
        }
        int totalFiles = 0;
        for (File s : sources) {
            totalFiles += StorageUtils.countTotalFileEntries(s);
        }
        totalFiles = Math.max(1, totalFiles);
        final int totalCount = totalFiles;
        final int[] doneCount = new int[]{0};

        String fmt = format == null ? "zip" : format.toLowerCase(Locale.ROOT);
        if ("tar".equals(fmt)) {
            compressToTar(sources, targetFile, false, cb, doneCount, totalCount);
            return targetFile;
        }
        if ("tar.gz".equals(fmt) || "tgz".equals(fmt)) {
            compressToTar(sources, targetFile, true, cb, doneCount, totalCount);
            return targetFile;
        }
        if ("gz".equals(fmt)) {
            File single = sources.get(0);
            if (single.isDirectory()) {
                compressToTar(sources, targetFile, true, cb, doneCount, totalCount);
                return targetFile;
            }
            if (cb != null) cb.onProgress(single.getName(), 0, 1);
            try (FileInputStream fis = new FileInputStream(single);
                 GZIPOutputStream gos = new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(targetFile)))) {
                copyStream(fis, gos);
            }
            if (cb != null) cb.onProgress(single.getName(), 1, 1);
            return targetFile;
        }

        boolean hasPwd = password != null && !password.isEmpty();
        if (hasPwd) {
            writePasswordProtectedZip(sources, targetFile, password, cb, totalCount);
        } else {
            try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(targetFile)))) {
                for (File src : sources) {
                    if (src == null || !src.exists()) continue;
                    if (src.isDirectory()) {
                        zipDirectoryRecursive(src, src.getName(), zos, cb, doneCount, totalCount);
                    } else {
                        zipSingleFile(src, src.getName(), zos, cb, doneCount, totalCount);
                    }
                }
            }
        }
        return targetFile;
    }

    private static void zipDirectoryRecursive(File folder, String parentEntryPath, ZipOutputStream zos,
                                              ArchiveProgressCallback cb, int[] doneCount, int totalCount) throws IOException {
        File[] children = folder.listFiles();
        if (children == null || children.length == 0) {
            zos.putNextEntry(new ZipEntry(parentEntryPath + "/"));
            zos.closeEntry();
            return;
        }
        for (File child : children) {
            String entryName = parentEntryPath + "/" + child.getName();
            if (child.isDirectory()) {
                zipDirectoryRecursive(child, entryName, zos, cb, doneCount, totalCount);
            } else {
                zipSingleFile(child, entryName, zos, cb, doneCount, totalCount);
            }
        }
    }

    private static void zipSingleFile(File file, String entryName, ZipOutputStream zos,
                                      ArchiveProgressCallback cb, int[] doneCount, int totalCount) throws IOException {
        if (cb != null) {
            cb.onProgress(file.getName(), doneCount[0], totalCount);
        }
        zos.putNextEntry(new ZipEntry(entryName));
        try (FileInputStream fis = new FileInputStream(file);
             BufferedInputStream bis = new BufferedInputStream(fis)) {
            copyStream(bis, zos);
        }
        zos.closeEntry();
        doneCount[0]++;
        if (cb != null) {
            cb.onProgress(file.getName(), doneCount[0], totalCount);
        }
    }

    public static List<String> listZipEntries(File archiveFile) throws IOException {
        List<ArchiveEntryInfo> detailed = listArchiveEntries(archiveFile);
        List<String> out = new ArrayList<>();
        for (ArchiveEntryInfo info : detailed) {
            String label = info.name + (info.directory ? " (תיקייה)" :
                    (info.size >= 0 ? "  •  " + StorageUtils.formatSize(info.size) : ""))
                    + (info.encrypted ? " (מוצפן)" : "");
            out.add(label);
        }
        return out;
    }

    public static List<ArchiveEntryInfo> listArchiveEntries(File archiveFile) throws IOException {
        if (archiveFile == null || !archiveFile.exists()) {
            throw new IOException("קובץ הארכיון לא נמצא");
        }
        String name = archiveFile.getName().toLowerCase(Locale.ROOT);
        if (MediaFormatHelper.isTarGzArchive(name)) {
            try (InputStream is = new GZIPInputStream(new BufferedInputStream(new FileInputStream(archiveFile)))) {
                return listTarEntries(is);
            }
        }
        if (MediaFormatHelper.isTarArchive(name)) {
            try (InputStream is = new BufferedInputStream(new FileInputStream(archiveFile))) {
                return listTarEntries(is);
            }
        }
        if (MediaFormatHelper.isGzArchive(name)) {
            List<ArchiveEntryInfo> single = new ArrayList<>();
            String inner = archiveFile.getName();
            if (inner.toLowerCase(Locale.ROOT).endsWith(".gz")) {
                inner = inner.substring(0, inner.length() - 3);
            }
            single.add(new ArchiveEntryInfo(inner, -1L, archiveFile.length(), false, false));
            return single;
        }
        return listZipCentralDirectory(archiveFile);
    }

    public static boolean archiveRequiresPassword(File archiveFile) {
        try {
            List<ArchiveEntryInfo> list = listArchiveEntries(archiveFile);
            for (ArchiveEntryInfo info : list) {
                if (info.encrypted && !info.directory) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public static int extractZip(File zipFile, File destinationDir) throws IOException {
        return extractArchive(zipFile, destinationDir, null);
    }

    public static int extractArchive(File archiveFile, File destinationDir, String password) throws IOException {
        return extractArchive(archiveFile, destinationDir, password, null);
    }

    public static int extractArchive(File archiveFile, File destinationDir, String password, ArchiveProgressCallback cb) throws IOException {
        if (!destinationDir.exists() && !destinationDir.mkdirs()) {
            throw new IOException("לא ניתן ליצור תיקיית יעד לחילוץ");
        }
        String canonicalDestDir = destinationDir.getCanonicalPath();
        String name = archiveFile.getName().toLowerCase(Locale.ROOT);

        int estTotal = 1;
        if (cb != null) {
            try {
                List<ArchiveEntryInfo> preview = listArchiveEntries(archiveFile);
                int fileEntries = 0;
                for (ArchiveEntryInfo info : preview) {
                    if (!info.directory) fileEntries++;
                }
                estTotal = Math.max(1, fileEntries);
            } catch (Throwable ignored) {
                estTotal = 1;
            }
        }

        if (MediaFormatHelper.isTarGzArchive(name)) {
            try (InputStream is = new GZIPInputStream(new BufferedInputStream(new FileInputStream(archiveFile)))) {
                return extractTarStream(is, destinationDir, canonicalDestDir, cb, estTotal);
            }
        }
        if (MediaFormatHelper.isTarArchive(name)) {
            try (InputStream is = new BufferedInputStream(new FileInputStream(archiveFile))) {
                return extractTarStream(is, destinationDir, canonicalDestDir, cb, estTotal);
            }
        }
        if (MediaFormatHelper.isGzArchive(name)) {
            String outName = archiveFile.getName();
            if (outName.toLowerCase(Locale.ROOT).endsWith(".gz")) {
                outName = outName.substring(0, outName.length() - 3);
            }
            if (!isSafeEntryName(outName)) {
                throw new SecurityException("שם קובץ לא חוקי בארכיון GZ");
            }
            if (cb != null) cb.onProgress(outName, 0, 1);
            File outFile = safeResolve(destinationDir, canonicalDestDir, outName);
            try (GZIPInputStream gis = new GZIPInputStream(new BufferedInputStream(new FileInputStream(archiveFile)));
                 FileOutputStream fos = new FileOutputStream(outFile)) {
                copyStream(gis, fos);
            }
            if (cb != null) cb.onProgress(outName, 1, 1);
            return 1;
        }

        if ((password != null && !password.isEmpty()) || archiveRequiresPassword(archiveFile)) {
            return extractRawZipWithOptionalPassword(archiveFile, destinationDir, canonicalDestDir, password, cb, estTotal);
        }

        try (ZipFile zf = new ZipFile(archiveFile)) {
            int zfTotal = Math.max(estTotal, Math.max(1, zf.size()));
            int extractedCount = 0;
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                File outFile = safeResolve(destinationDir, canonicalDestDir, entry.getName());
                if (entry.isDirectory()) {
                    outFile.mkdirs();
                } else {
                    if (cb != null) cb.onProgress(outFile.getName(), extractedCount, zfTotal);
                    File parent = outFile.getParentFile();
                    if (parent != null && !parent.exists()) parent.mkdirs();
                    try (InputStream is = zf.getInputStream(entry);
                         BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outFile), COPY_BUFFER_SIZE)) {
                        copyStream(is, bos);
                    }
                    extractedCount++;
                    if (cb != null) cb.onProgress(outFile.getName(), extractedCount, zfTotal);
                }
            }
            return extractedCount;
        } catch (SecurityException se) {
            throw se;
        } catch (IOException zfErr) {
            int extractedCount = 0;
            try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(archiveFile), COPY_BUFFER_SIZE))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    File outFile = safeResolve(destinationDir, canonicalDestDir, entry.getName());
                    if (entry.isDirectory()) {
                        outFile.mkdirs();
                    } else {
                        if (cb != null) cb.onProgress(outFile.getName(), extractedCount, estTotal);
                        File parent = outFile.getParentFile();
                        if (parent != null && !parent.exists()) parent.mkdirs();
                        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outFile), COPY_BUFFER_SIZE)) {
                            copyStream(zis, bos);
                        }
                        extractedCount++;
                        if (cb != null) cb.onProgress(outFile.getName(), extractedCount, estTotal);
                    }
                    zis.closeEntry();
                }
            }
            return extractedCount;
        }
    }

    public static void extractEntryToFile(File archiveFile, String targetEntryName, String password, File outFile) throws IOException {
        if (archiveFile == null || !archiveFile.exists()) {
            throw new IOException("קובץ הארכיון לא נמצא");
        }
        File parent = outFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        boolean success = false;
        try {
            String name = archiveFile.getName().toLowerCase(Locale.ROOT);
            if (MediaFormatHelper.isTarGzArchive(name)) {
                try (InputStream is = new GZIPInputStream(new BufferedInputStream(new FileInputStream(archiveFile), COPY_BUFFER_SIZE));
                     BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outFile), COPY_BUFFER_SIZE)) {
                    extractTarEntryToStream(is, targetEntryName, bos);
                    bos.flush();
                }
                success = true;
                return;
            }
            if (MediaFormatHelper.isTarArchive(name)) {
                try (InputStream is = new BufferedInputStream(new FileInputStream(archiveFile), COPY_BUFFER_SIZE);
                     BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outFile), COPY_BUFFER_SIZE)) {
                    extractTarEntryToStream(is, targetEntryName, bos);
                    bos.flush();
                }
                success = true;
                return;
            }
            if (MediaFormatHelper.isGzArchive(name)) {
                try (GZIPInputStream gis = new GZIPInputStream(new BufferedInputStream(new FileInputStream(archiveFile), COPY_BUFFER_SIZE));
                     BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outFile), COPY_BUFFER_SIZE)) {
                    copyStream(gis, bos);
                    bos.flush();
                }
                success = true;
                return;
            }
            if ((password != null && !password.isEmpty()) || archiveRequiresPassword(archiveFile)) {
                try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outFile), COPY_BUFFER_SIZE)) {
                    extractRawZipEntryToStream(archiveFile, targetEntryName, password, bos, -1L);
                    bos.flush();
                }
                success = true;
                return;
            }
            try (ZipFile zf = new ZipFile(archiveFile)) {
                ZipEntry ze = zf.getEntry(targetEntryName);
                if (ze == null) {
                    throw new IOException("הפריט לא נמצא בארכיון: " + targetEntryName);
                }
                try (InputStream is = zf.getInputStream(ze);
                     BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outFile), COPY_BUFFER_SIZE)) {
                    copyStream(is, bos);
                    bos.flush();
                }
                success = true;
            }
        } finally {
            if (!success && outFile.exists()) {
                outFile.delete();
            }
        }
    }

    public static byte[] readEntryBytes(File archiveFile, String targetEntryName, String password) throws IOException {
        String name = archiveFile.getName().toLowerCase(Locale.ROOT);
        if (MediaFormatHelper.isTarGzArchive(name)) {
            try (InputStream is = new GZIPInputStream(new BufferedInputStream(new FileInputStream(archiveFile), COPY_BUFFER_SIZE))) {
                return readTarEntryBytes(is, targetEntryName);
            }
        }
        if (MediaFormatHelper.isTarArchive(name)) {
            try (InputStream is = new BufferedInputStream(new FileInputStream(archiveFile), COPY_BUFFER_SIZE)) {
                return readTarEntryBytes(is, targetEntryName);
            }
        }
        if (MediaFormatHelper.isGzArchive(name)) {
            try (GZIPInputStream gis = new GZIPInputStream(new BufferedInputStream(new FileInputStream(archiveFile), COPY_BUFFER_SIZE))) {
                return readAllBytes(gis);
            }
        }
        if ((password != null && !password.isEmpty()) || archiveRequiresPassword(archiveFile)) {
            return readRawZipEntryBytes(archiveFile, targetEntryName, password);
        }
        try (ZipFile zf = new ZipFile(archiveFile)) {
            ZipEntry ze = zf.getEntry(targetEntryName);
            if (ze == null) {
                throw new IOException("הפריט לא נמצא בארכיון: " + targetEntryName);
            }
            try (InputStream is = zf.getInputStream(ze)) {
                return readAllBytes(is);
            }
        }
    }

    public static void updateOrAddZipEntry(File zipFile, String entryName, byte[] content) throws IOException {
        if (!isSafeEntryName(entryName)) throw new SecurityException("נתיב קובץ לא חוקי");
        File tmp = new File(zipFile.getParentFile(), ".tmp_edit_" + System.currentTimeMillis() + ".zip");
        boolean replaced = false;
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)));
             ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(tmp)))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String n = entry.getName();
                zos.putNextEntry(new ZipEntry(n));
                if (n.equals(entryName)) {
                    zos.write(content);
                    replaced = true;
                } else if (!entry.isDirectory()) {
                    copyStream(zis, zos);
                }
                zos.closeEntry();
                zis.closeEntry();
            }
            if (!replaced) {
                zos.putNextEntry(new ZipEntry(entryName));
                zos.write(content);
                zos.closeEntry();
            }
        }
        replaceFileAtomically(tmp, zipFile);
    }

    public static boolean deleteZipEntry(File zipFile, String entryName) throws IOException {
        File tmp = new File(zipFile.getParentFile(), ".tmp_del_" + System.currentTimeMillis() + ".zip");
        boolean removed = false;
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)));
             ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(tmp)))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String n = entry.getName();
                if (n.equals(entryName) || (entryName.endsWith("/") && n.startsWith(entryName))) {
                    removed = true;
                    zis.closeEntry();
                    continue;
                }
                zos.putNextEntry(new ZipEntry(n));
                if (!entry.isDirectory()) {
                    copyStream(zis, zos);
                }
                zos.closeEntry();
                zis.closeEntry();
            }
        }
        if (removed) {
            replaceFileAtomically(tmp, zipFile);
        } else {
            tmp.delete();
        }
        return removed;
    }

    private static void replaceFileAtomically(File srcTmp, File dst) throws IOException {
        if (dst.exists() && !dst.delete()) {
            throw new IOException("לא ניתן לעדכן את הארכיון");
        }
        if (!srcTmp.renameTo(dst)) {
            try (FileInputStream fis = new FileInputStream(srcTmp);
                 FileOutputStream fos = new FileOutputStream(dst)) {
                copyStream(fis, fos);
            }
            srcTmp.delete();
        }
    }

    // ==================== Zip Central Directory & ZipCrypto Engine ====================

    private static final int COPY_BUFFER_SIZE = 128 * 1024;
    private static final long MAX_IN_MEMORY_ENTRY_BYTES = 48L * 1024L * 1024L;
    private static final long ZIP32_SENTINEL = 0xFFFFFFFFL;

    private static byte[] readCentralDirectoryBytes(RandomAccessFile raf) throws IOException {
        long fileLen = raf.length();
        if (fileLen < 22L) {
            throw new IOException("קובץ ZIP אינו תקין");
        }
        int tailLen = (int) Math.min(fileLen, 65557L);
        byte[] tail = new byte[tailLen];
        raf.seek(fileLen - tailLen);
        raf.readFully(tail);

        int eocdOffset = -1;
        for (int i = tailLen - 22; i >= 0; i--) {
            if (tail[i] == 0x50 && tail[i + 1] == 0x4b && tail[i + 2] == 0x05 && tail[i + 3] == 0x06) {
                int commentLen = (tail[i + 20] & 0xFF) | ((tail[i + 21] & 0xFF) << 8);
                if (i + 22 + commentLen == tailLen) {
                    eocdOffset = i;
                    break;
                }
                if (eocdOffset < 0) {
                    eocdOffset = i;
                }
            }
        }
        if (eocdOffset < 0) {
            throw new IOException("קובץ ZIP אינו תקין");
        }
        ByteBuffer eocd = ByteBuffer.wrap(tail).order(ByteOrder.LITTLE_ENDIAN);
        int totalEntries16 = eocd.getShort(eocdOffset + 10) & 0xFFFF;
        long cdSize = eocd.getInt(eocdOffset + 12) & 0xFFFFFFFFL;
        long cdOffset = eocd.getInt(eocdOffset + 16) & 0xFFFFFFFFL;

        // בדיקת Zip64 EOCD Locator (20 בתים לפני ה-EOCD הרגיל)
        long eocdFilePos = (fileLen - tailLen) + eocdOffset;
        if ((cdSize == ZIP32_SENTINEL || cdOffset == ZIP32_SENTINEL || totalEntries16 == 0xFFFF)
                && eocdFilePos >= 20L) {
            byte[] locator = new byte[20];
            raf.seek(eocdFilePos - 20L);
            raf.readFully(locator);
            ByteBuffer locBb = ByteBuffer.wrap(locator).order(ByteOrder.LITTLE_ENDIAN);
            if (locBb.getInt(0) == 0x07064b50) {
                long zip64EocdOffset = locBb.getLong(8);
                if (zip64EocdOffset >= 0 && zip64EocdOffset + 56L <= fileLen) {
                    byte[] z64Eocd = new byte[56];
                    raf.seek(zip64EocdOffset);
                    raf.readFully(z64Eocd);
                    ByteBuffer z64Bb = ByteBuffer.wrap(z64Eocd).order(ByteOrder.LITTLE_ENDIAN);
                    if (z64Bb.getInt(0) == 0x06064b50) {
                        cdSize = z64Bb.getLong(40);
                        cdOffset = z64Bb.getLong(48);
                    }
                }
            }
        }

        if (cdSize < 0 || cdSize > 256L * 1024L * 1024L || cdOffset < 0 || cdOffset + cdSize > fileLen) {
            throw new IOException("מבנה אינדקס הארכיון חורג מגודל הקובץ");
        }
        byte[] cdBytes = new byte[(int) cdSize];
        raf.seek(cdOffset);
        raf.readFully(cdBytes);
        return cdBytes;
    }

    private static long[] resolveZip64CdSizes(ByteBuffer bb, int extraStart, int extraLen,
                                              long uncompSize, long compSize, long localHeaderOffset) {
        long[] vals = new long[]{uncompSize, compSize, localHeaderOffset};
        if (uncompSize != ZIP32_SENTINEL && compSize != ZIP32_SENTINEL && localHeaderOffset != ZIP32_SENTINEL) {
            return vals;
        }
        int ePos = extraStart;
        int eEnd = Math.min(bb.capacity(), extraStart + extraLen);
        while (ePos + 4 <= eEnd) {
            int headerId = bb.getShort(ePos) & 0xFFFF;
            int dataSize = bb.getShort(ePos + 2) & 0xFFFF;
            int fieldEnd = Math.min(eEnd, ePos + 4 + dataSize);
            if (headerId == 0x0001) {
                int cursor = ePos + 4;
                if (vals[0] == ZIP32_SENTINEL && cursor + 8 <= fieldEnd) {
                    vals[0] = bb.getLong(cursor);
                    cursor += 8;
                }
                if (vals[1] == ZIP32_SENTINEL && cursor + 8 <= fieldEnd) {
                    vals[1] = bb.getLong(cursor);
                    cursor += 8;
                }
                if (vals[2] == ZIP32_SENTINEL && cursor + 8 <= fieldEnd) {
                    vals[2] = bb.getLong(cursor);
                }
                break;
            }
            ePos += 4 + dataSize;
        }
        return vals;
    }

    private static List<ArchiveEntryInfo> listZipCentralDirectory(File zipFile) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(zipFile, "r")) {
            byte[] cdBytes = readCentralDirectoryBytes(raf);
            ByteBuffer bb = ByteBuffer.wrap(cdBytes).order(ByteOrder.LITTLE_ENDIAN);
            List<ArchiveEntryInfo> result = new ArrayList<>();
            int pos = 0;
            int end = cdBytes.length;
            while (pos + 46 <= end) {
                if (bb.getInt(pos) != 0x02014b50) break;
                int flags = bb.getShort(pos + 8) & 0xFFFF;
                long compSize = bb.getInt(pos + 20) & 0xFFFFFFFFL;
                long uncompSize = bb.getInt(pos + 24) & 0xFFFFFFFFL;
                int nameLen = bb.getShort(pos + 28) & 0xFFFF;
                int extraLen = bb.getShort(pos + 30) & 0xFFFF;
                int commentLen = bb.getShort(pos + 32) & 0xFFFF;
                long localHeaderOffset = bb.getInt(pos + 42) & 0xFFFFFFFFL;
                if (pos + 46 + nameLen > end) break;
                String name = new String(cdBytes, pos + 46, nameLen, StandardCharsets.UTF_8);
                long[] z64 = resolveZip64CdSizes(bb, pos + 46 + nameLen, extraLen, uncompSize, compSize, localHeaderOffset);
                boolean encrypted = (flags & 0x0001) != 0;
                boolean dir = name.endsWith("/");
                result.add(new ArchiveEntryInfo(name, z64[0], z64[1], dir, encrypted));
                pos += 46 + nameLen + extraLen + commentLen;
            }
            return result;
        }
    }

    private static int extractRawZipWithOptionalPassword(File zipFile, File destDir, String canonicalDestDir,
                                                         String password, ArchiveProgressCallback cb, int totalCount) throws IOException {
        int extracted = 0;
        try (RandomAccessFile raf = new RandomAccessFile(zipFile, "r")) {
            byte[] cdBytes = readCentralDirectoryBytes(raf);
            ByteBuffer bb = ByteBuffer.wrap(cdBytes).order(ByteOrder.LITTLE_ENDIAN);
            int pos = 0;
            int end = cdBytes.length;

            while (pos + 46 <= end) {
                if (bb.getInt(pos) != 0x02014b50) break;
                int flags = bb.getShort(pos + 8) & 0xFFFF;
                int method = bb.getShort(pos + 10) & 0xFFFF;
                long crc = bb.getInt(pos + 16) & 0xFFFFFFFFL;
                long compSize = bb.getInt(pos + 20) & 0xFFFFFFFFL;
                long uncompSize = bb.getInt(pos + 24) & 0xFFFFFFFFL;
                int nameLen = bb.getShort(pos + 28) & 0xFFFF;
                int extraLen = bb.getShort(pos + 30) & 0xFFFF;
                int commentLen = bb.getShort(pos + 32) & 0xFFFF;
                long localHeaderOffset = bb.getInt(pos + 42) & 0xFFFFFFFFL;
                if (pos + 46 + nameLen > end) break;
                String name = new String(cdBytes, pos + 46, nameLen, StandardCharsets.UTF_8);
                long[] z64 = resolveZip64CdSizes(bb, pos + 46 + nameLen, extraLen, uncompSize, compSize, localHeaderOffset);
                compSize = z64[1];
                localHeaderOffset = z64[2];
                pos += 46 + nameLen + extraLen + commentLen;

                File outFile = safeResolve(destDir, canonicalDestDir, name);
                if (name.endsWith("/")) {
                    outFile.mkdirs();
                    continue;
                }
                if (cb != null) cb.onProgress(outFile.getName(), extracted, Math.max(1, totalCount));
                File parent = outFile.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outFile), COPY_BUFFER_SIZE)) {
                    decodeLocalZipEntryToStream(raf, localHeaderOffset, flags, method, crc, compSize, password, bos, -1L);
                }
                extracted++;
                if (cb != null) cb.onProgress(outFile.getName(), extracted, Math.max(1, totalCount));
            }
        }
        return extracted;
    }

    private static void extractRawZipEntryToStream(File zipFile, String targetName, String password,
                                                   OutputStream out, long maxOutBytes) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(zipFile, "r")) {
            byte[] cdBytes = readCentralDirectoryBytes(raf);
            ByteBuffer bb = ByteBuffer.wrap(cdBytes).order(ByteOrder.LITTLE_ENDIAN);
            int pos = 0;
            int end = cdBytes.length;

            while (pos + 46 <= end) {
                if (bb.getInt(pos) != 0x02014b50) break;
                int flags = bb.getShort(pos + 8) & 0xFFFF;
                int method = bb.getShort(pos + 10) & 0xFFFF;
                long crc = bb.getInt(pos + 16) & 0xFFFFFFFFL;
                long compSize = bb.getInt(pos + 20) & 0xFFFFFFFFL;
                long uncompSize = bb.getInt(pos + 24) & 0xFFFFFFFFL;
                int nameLen = bb.getShort(pos + 28) & 0xFFFF;
                int extraLen = bb.getShort(pos + 30) & 0xFFFF;
                int commentLen = bb.getShort(pos + 32) & 0xFFFF;
                long localHeaderOffset = bb.getInt(pos + 42) & 0xFFFFFFFFL;
                if (pos + 46 + nameLen > end) break;
                String name = new String(cdBytes, pos + 46, nameLen, StandardCharsets.UTF_8);
                long[] z64 = resolveZip64CdSizes(bb, pos + 46 + nameLen, extraLen, uncompSize, compSize, localHeaderOffset);
                compSize = z64[1];
                localHeaderOffset = z64[2];
                pos += 46 + nameLen + extraLen + commentLen;
                if (name.equals(targetName)) {
                    decodeLocalZipEntryToStream(raf, localHeaderOffset, flags, method, crc, compSize, password, out, maxOutBytes);
                    return;
                }
            }
        }
        throw new IOException("הפריט לא נמצא: " + targetName);
    }

    private static byte[] readRawZipEntryBytes(File zipFile, String targetName, String password) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        extractRawZipEntryToStream(zipFile, targetName, password, baos, MAX_IN_MEMORY_ENTRY_BYTES);
        return baos.toByteArray();
    }

    private static void decodeLocalZipEntryToStream(RandomAccessFile raf, long localOffset, int flags, int method,
                                                    long expectedCrc, long compSize, String password,
                                                    OutputStream out, long maxOutBytes) throws IOException {
        byte[] lh = new byte[30];
        raf.seek(localOffset);
        raf.readFully(lh);
        ByteBuffer bb = ByteBuffer.wrap(lh).order(ByteOrder.LITTLE_ENDIAN);
        if (bb.getInt(0) != 0x04034b50) {
            throw new IOException("רשומת ZIP פגומה");
        }
        int lNameLen = bb.getShort(26) & 0xFFFF;
        int lExtraLen = bb.getShort(28) & 0xFFFF;
        long dataStart = localOffset + 30L + lNameLen + lExtraLen;
        if (dataStart + compSize > raf.length()) {
            throw new IOException("גודל רשומת ZIP חורג מגבולות הקובץ");
        }

        boolean encrypted = (flags & 0x0001) != 0;
        long payloadLen = compSize;
        ZipCryptoCipher cipher = null;

        raf.seek(dataStart);
        if (encrypted) {
            if (password == null || password.isEmpty()) {
                throw new IOException("הארכיון מוגן בסיסמה — נדרשת סיסמה");
            }
            if (payloadLen < 12) {
                throw new IOException("נתוני הצפנה לא תקינים");
            }
            cipher = new ZipCryptoCipher(password.getBytes(StandardCharsets.UTF_8));
            byte[] encHdr = new byte[12];
            raf.readFully(encHdr);
            byte[] decHdr = new byte[12];
            for (int i = 0; i < 12; i++) {
                decHdr[i] = cipher.decryptByte(encHdr[i]);
            }
            int expectedCheck = (int) ((expectedCrc >>> 24) & 0xFF);
            if ((flags & 0x0008) == 0 && (decHdr[11] & 0xFF) != expectedCheck) {
                throw new IOException("סיסמה שגויה");
            }
            payloadLen -= 12L;
        }

        InputStream rawBounded = new BufferedInputStream(new BoundedRafInputStream(raf, payloadLen), 64 * 1024);
        InputStream plainStream = cipher != null ? new ZipCryptoInputStream(rawBounded, cipher) : rawBounded;

        if (method == 0) {
            copyStreamBounded(plainStream, out, maxOutBytes);
        } else if (method == 8) {
            Inflater inflater = new Inflater(true);
            try (InflaterInputStream iis = new InflaterInputStream(plainStream, inflater, 64 * 1024)) {
                copyStreamBounded(iis, out, maxOutBytes);
            } finally {
                inflater.end();
            }
        } else {
            throw new IOException("שיטת דחיסה לא נתמכת: " + method);
        }
    }

    private static void copyStreamBounded(InputStream is, OutputStream os, long maxBytes) throws IOException {
        byte[] buf = new byte[COPY_BUFFER_SIZE];
        long total = 0L;
        int r;
        while ((r = is.read(buf)) != -1) {
            if (maxBytes > 0 && total + r > maxBytes) {
                throw new IOException("הקובץ בתוך הארכיון גדול מדי לתצוגה ישירה — יש לחלץ אותו תחילה");
            }
            os.write(buf, 0, r);
            total += r;
        }
    }

    private static class BoundedRafInputStream extends InputStream {
        private final RandomAccessFile raf;
        private long remaining;

        BoundedRafInputStream(RandomAccessFile raf, long length) {
            this.raf = raf;
            this.remaining = Math.max(0L, length);
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) return -1;
            int b = raf.read();
            if (b >= 0) remaining--;
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (remaining <= 0) return -1;
            int toRead = (int) Math.min(len, remaining);
            int r = raf.read(b, off, toRead);
            if (r > 0) remaining -= r;
            return r;
        }
    }

    private static class ZipCryptoInputStream extends InputStream {
        private final InputStream in;
        private final ZipCryptoCipher cipher;

        ZipCryptoInputStream(InputStream in, ZipCryptoCipher cipher) {
            this.in = in;
            this.cipher = cipher;
        }

        @Override
        public int read() throws IOException {
            int b = in.read();
            if (b < 0) return -1;
            return cipher.decryptByte((byte) b) & 0xFF;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int r = in.read(b, off, len);
            if (r > 0) {
                for (int i = 0; i < r; i++) {
                    b[off + i] = cipher.decryptByte(b[off + i]);
                }
            }
            return r;
        }
    }

    private static class ZipCryptoOutputStream extends OutputStream {
        private final OutputStream out;
        private final ZipCryptoCipher cipher;
        private final byte[] encBuf = new byte[64 * 1024];

        ZipCryptoOutputStream(OutputStream out, ZipCryptoCipher cipher) {
            this.out = out;
            this.cipher = cipher;
        }

        @Override
        public void write(int b) throws IOException {
            out.write(cipher.encryptByte((byte) b) & 0xFF);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            int pos = off;
            int rem = len;
            while (rem > 0) {
                int chunk = Math.min(rem, encBuf.length);
                for (int i = 0; i < chunk; i++) {
                    encBuf[i] = cipher.encryptByte(b[pos + i]);
                }
                out.write(encBuf, 0, chunk);
                pos += chunk;
                rem -= chunk;
            }
        }

        @Override
        public void flush() throws IOException {
            out.flush();
        }
    }

    private static class BufferedRafOutputStream extends OutputStream {
        private final RandomAccessFile raf;
        private final byte[] buf = new byte[COPY_BUFFER_SIZE];
        private int count = 0;

        BufferedRafOutputStream(RandomAccessFile raf) {
            this.raf = raf;
        }

        @Override
        public void write(int b) throws IOException {
            if (count >= buf.length) {
                flushBuffer();
            }
            buf[count++] = (byte) b;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if (len >= buf.length) {
                flushBuffer();
                raf.write(b, off, len);
                return;
            }
            if (len > buf.length - count) {
                flushBuffer();
            }
            System.arraycopy(b, off, buf, count, len);
            count += len;
        }

        private void flushBuffer() throws IOException {
            if (count > 0) {
                raf.write(buf, 0, count);
                count = 0;
            }
        }

        @Override
        public void flush() throws IOException {
            flushBuffer();
        }
    }

    private static void writePasswordProtectedZip(List<File> sources, File targetZip, String password,
                                                  ArchiveProgressCallback cb, int totalHint) throws IOException {
        List<FileItem> items = new ArrayList<>();
        for (File f : sources) {
            collectItemsRecursive(f, "", items);
        }
        int totalFiles = 0;
        for (FileItem fi : items) {
            if (!fi.isDir) totalFiles++;
        }
        totalFiles = Math.max(1, Math.max(totalHint, totalFiles));
        int doneFiles = 0;
        byte[] pwdBytes = password.getBytes(StandardCharsets.UTF_8);
        SecureRandom rng = new SecureRandom();
        ByteArrayOutputStream centralDir = new ByteArrayOutputStream();
        long offset = 0L;
        int entryCount = 0;
        boolean archiveUsedZip64 = false;
        byte[] streamBuf = new byte[COPY_BUFFER_SIZE];

        try (RandomAccessFile raf = new RandomAccessFile(targetZip, "rw")) {
            raf.setLength(0L);

            for (FileItem item : items) {
                if (!item.isDir && cb != null && item.file != null) {
                    cb.onProgress(item.file.getName(), doneFiles, totalFiles);
                }
                byte[] nameBytes = item.entryName.getBytes(StandardCharsets.UTF_8);
                long crc = 0L;
                long uncompSize = 0L;
                if (!item.isDir && item.file != null) {
                    CRC32 crc32 = new CRC32();
                    try (FileInputStream crcFis = new FileInputStream(item.file)) {
                        int r;
                        while ((r = crcFis.read(streamBuf)) != -1) {
                            crc32.update(streamBuf, 0, r);
                            uncompSize += r;
                        }
                    }
                    crc = crc32.getValue();
                }

                boolean needZip64LocalSizes = !item.isDir && uncompSize >= 0xFF000000L;
                int localExtraLen = needZip64LocalSizes ? 20 : 0;
                int method = (item.isDir || uncompSize == 0L) ? 0 : 8;
                int flags = 0x0800 | (item.isDir ? 0 : 0x0001); // UTF-8 (+ Encrypted for files)
                int verNeededLocal = needZip64LocalSizes ? 45 : 20;

                long localOffset = offset;
                ByteBuffer lh = ByteBuffer.allocate(30).order(ByteOrder.LITTLE_ENDIAN);
                lh.putInt(0x04034b50);
                lh.putShort((short) verNeededLocal);
                lh.putShort((short) flags);
                lh.putShort((short) method);
                lh.putShort((short) 0);
                lh.putShort((short) 0x5421);
                lh.putInt((int) crc);
                lh.putInt(needZip64LocalSizes ? (int) ZIP32_SENTINEL : 0);
                lh.putInt(needZip64LocalSizes ? (int) ZIP32_SENTINEL : (int) uncompSize);
                lh.putShort((short) nameBytes.length);
                lh.putShort((short) localExtraLen);

                raf.write(lh.array());
                raf.write(nameBytes);
                if (needZip64LocalSizes) {
                    ByteBuffer z64Local = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN);
                    z64Local.putShort((short) 0x0001);
                    z64Local.putShort((short) 16);
                    z64Local.putLong(uncompSize);
                    z64Local.putLong(0L);
                    raf.write(z64Local.array());
                }

                long dataStartOffset = raf.getFilePointer();
                long compSize = 0L;
                if (!item.isDir) {
                    BufferedRafOutputStream rafOut = new BufferedRafOutputStream(raf);
                    ZipCryptoCipher cipher = new ZipCryptoCipher(pwdBytes);
                    byte[] hdr = new byte[12];
                    rng.nextBytes(hdr);
                    hdr[11] = (byte) ((crc >>> 24) & 0xFF);
                    byte[] encHdr = new byte[12];
                    for (int i = 0; i < 12; i++) {
                        encHdr[i] = cipher.encryptByte(hdr[i]);
                    }
                    rafOut.write(encHdr, 0, 12);

                    if (uncompSize > 0L) {
                        ZipCryptoOutputStream zcos = new ZipCryptoOutputStream(rafOut, cipher);
                        Deflater def = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
                        try (FileInputStream srcFis = new FileInputStream(item.file)) {
                            DeflaterOutputStream dos = new DeflaterOutputStream(zcos, def, 64 * 1024);
                            int r;
                            while ((r = srcFis.read(streamBuf)) != -1) {
                                dos.write(streamBuf, 0, r);
                            }
                            dos.finish();
                        } finally {
                            def.end();
                        }
                    }
                    rafOut.flush();
                    long dataEndOffset = raf.getFilePointer();
                    compSize = dataEndOffset - dataStartOffset;

                    // תיקון compressedSize ב-Local Header בלי החזקת הקובץ בזיכרון
                    raf.seek(localOffset + 18L);
                    ByteBuffer patch32 = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
                    patch32.putInt(needZip64LocalSizes ? (int) ZIP32_SENTINEL : (int) compSize);
                    raf.write(patch32.array());
                    if (needZip64LocalSizes) {
                        raf.seek(localOffset + 30L + nameBytes.length + 12L);
                        ByteBuffer patch64 = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
                        patch64.putLong(compSize);
                        raf.write(patch64.array());
                    }
                    raf.seek(dataEndOffset);
                    offset = dataEndOffset;
                } else {
                    offset = dataStartOffset;
                }

                boolean needZ64Uncomp = uncompSize >= ZIP32_SENTINEL;
                boolean needZ64Comp = compSize >= ZIP32_SENTINEL;
                boolean needZ64Offset = localOffset >= ZIP32_SENTINEL;
                int z64FieldBytes = (needZ64Uncomp ? 8 : 0) + (needZ64Comp ? 8 : 0) + (needZ64Offset ? 8 : 0);
                int cdExtraLen = z64FieldBytes > 0 ? (4 + z64FieldBytes) : 0;
                if (cdExtraLen > 0) archiveUsedZip64 = true;

                ByteBuffer cd = ByteBuffer.allocate(46).order(ByteOrder.LITTLE_ENDIAN);
                cd.putInt(0x02014b50);
                cd.putShort((short) 45);
                cd.putShort((short) (cdExtraLen > 0 ? 45 : 20));
                cd.putShort((short) flags);
                cd.putShort((short) method);
                cd.putShort((short) 0);
                cd.putShort((short) 0x5421);
                cd.putInt((int) crc);
                cd.putInt(needZ64Comp ? (int) ZIP32_SENTINEL : (int) compSize);
                cd.putInt(needZ64Uncomp ? (int) ZIP32_SENTINEL : (int) uncompSize);
                cd.putShort((short) nameBytes.length);
                cd.putShort((short) cdExtraLen);
                cd.putShort((short) 0);
                cd.putShort((short) 0);
                cd.putShort((short) 0);
                cd.putInt(item.isDir ? 0x10 : 0);
                cd.putInt(needZ64Offset ? (int) ZIP32_SENTINEL : (int) localOffset);
                centralDir.write(cd.array());
                centralDir.write(nameBytes);
                if (cdExtraLen > 0) {
                    ByteBuffer cdZ64 = ByteBuffer.allocate(cdExtraLen).order(ByteOrder.LITTLE_ENDIAN);
                    cdZ64.putShort((short) 0x0001);
                    cdZ64.putShort((short) z64FieldBytes);
                    if (needZ64Uncomp) cdZ64.putLong(uncompSize);
                    if (needZ64Comp) cdZ64.putLong(compSize);
                    if (needZ64Offset) cdZ64.putLong(localOffset);
                    centralDir.write(cdZ64.array());
                }

                entryCount++;
                if (!item.isDir) {
                    doneFiles++;
                    if (cb != null && item.file != null) {
                        cb.onProgress(item.file.getName(), doneFiles, totalFiles);
                    }
                }
            }

            long cdOffset = offset;
            byte[] cdBytes = centralDir.toByteArray();
            long cdSize = cdBytes.length;
            raf.write(cdBytes);

            boolean writeZip64Eocd = archiveUsedZip64 || entryCount >= 0xFFFF || cdSize >= ZIP32_SENTINEL || cdOffset >= ZIP32_SENTINEL;
            if (writeZip64Eocd) {
                long z64EocdOffset = raf.getFilePointer();
                ByteBuffer z64Eocd = ByteBuffer.allocate(56).order(ByteOrder.LITTLE_ENDIAN);
                z64Eocd.putInt(0x06064b50);
                z64Eocd.putLong(44L);
                z64Eocd.putShort((short) 45);
                z64Eocd.putShort((short) 45);
                z64Eocd.putInt(0);
                z64Eocd.putInt(0);
                z64Eocd.putLong(entryCount);
                z64Eocd.putLong(entryCount);
                z64Eocd.putLong(cdSize);
                z64Eocd.putLong(cdOffset);
                raf.write(z64Eocd.array());

                ByteBuffer z64Loc = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN);
                z64Loc.putInt(0x07064b50);
                z64Loc.putInt(0);
                z64Loc.putLong(z64EocdOffset);
                z64Loc.putInt(1);
                raf.write(z64Loc.array());
            }

            ByteBuffer eocd = ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN);
            eocd.putInt(0x06054b50);
            eocd.putShort((short) 0);
            eocd.putShort((short) 0);
            eocd.putShort((short) Math.min(0xFFFF, entryCount));
            eocd.putShort((short) Math.min(0xFFFF, entryCount));
            eocd.putInt(cdSize >= ZIP32_SENTINEL ? (int) ZIP32_SENTINEL : (int) cdSize);
            eocd.putInt(cdOffset >= ZIP32_SENTINEL ? (int) ZIP32_SENTINEL : (int) cdOffset);
            eocd.putShort((short) 0);
            raf.write(eocd.array());
        }
    }

    private static class FileItem {
        final File file;
        final String entryName;
        final boolean isDir;

        FileItem(File file, String entryName, boolean isDir) {
            this.file = file;
            this.entryName = entryName;
            this.isDir = isDir;
        }
    }

    private static void collectItemsRecursive(File f, String prefix, List<FileItem> out) {
        if (f == null || !f.exists()) return;
        String name = prefix.isEmpty() ? f.getName() : prefix + "/" + f.getName();
        if (f.isDirectory()) {
            out.add(new FileItem(f, name + "/", true));
            File[] children = f.listFiles();
            if (children != null) {
                for (File c : children) collectItemsRecursive(c, name, out);
            }
        } else {
            out.add(new FileItem(f, name, false));
        }
    }

    public static final class ZipCryptoCipher {
        private static final int[] CRC_TABLE = new int[256];

        static {
            for (int n = 0; n < 256; n++) {
                int c = n;
                for (int k = 0; k < 8; k++) {
                    c = ((c & 1) != 0) ? (0xedb88320 ^ (c >>> 1)) : (c >>> 1);
                }
                CRC_TABLE[n] = c;
            }
        }

        private final int[] keys = {0x12345678, 0x23456789, 0x34567890};

        public ZipCryptoCipher(byte[] password) {
            if (password != null) {
                for (byte b : password) updateKeys(b);
            }
        }

        private static int crc32Byte(int crc, byte b) {
            return (crc >>> 8) ^ CRC_TABLE[(crc ^ (b & 0xFF)) & 0xFF];
        }

        private void updateKeys(byte c) {
            keys[0] = crc32Byte(keys[0], c);
            keys[1] += (keys[0] & 0xFF);
            keys[1] = keys[1] * 134775813 + 1;
            keys[2] = crc32Byte(keys[2], (byte) (keys[1] >>> 24));
        }

        private byte magicByte() {
            int t = (keys[2] & 0xFFFF) | 2;
            return (byte) ((t * (t ^ 1)) >>> 8);
        }

        public byte encryptByte(byte plain) {
            byte t = magicByte();
            updateKeys(plain);
            return (byte) (plain ^ t);
        }

        public byte decryptByte(byte cipher) {
            byte plain = (byte) (cipher ^ magicByte());
            updateKeys(plain);
            return plain;
        }
    }

    // ==================== USTAR TAR / TAR.GZ Engine ====================

    private static void compressToTar(List<File> sources, File outFile, boolean gzip,
                                      ArchiveProgressCallback cb, int[] doneCount, int totalCount) throws IOException {
        try (OutputStream rawOut = new BufferedOutputStream(new FileOutputStream(outFile));
             OutputStream tarOut = gzip ? new GZIPOutputStream(rawOut) : rawOut) {
            for (File src : sources) {
                if (src != null && src.exists()) {
                    writeTarRecursive(src, src.getName(), tarOut, cb, doneCount, totalCount);
                }
            }
            tarOut.write(new byte[1024]); // 2 empty 512B blocks EOF
        }
    }

    private static void writeTarRecursive(File file, String entryPath, OutputStream out,
                                          ArchiveProgressCallback cb, int[] doneCount, int totalCount) throws IOException {
        if (file.isDirectory()) {
            String dirEntry = entryPath.endsWith("/") ? entryPath : entryPath + "/";
            out.write(buildTarHeader(dirEntry, 0L, true, file.lastModified() / 1000L));
            File[] kids = file.listFiles();
            if (kids != null) {
                for (File k : kids) writeTarRecursive(k, entryPath + "/" + k.getName(), out, cb, doneCount, totalCount);
            }
        } else {
            if (cb != null) cb.onProgress(file.getName(), doneCount[0], totalCount);
            long len = file.length();
            out.write(buildTarHeader(entryPath, len, false, file.lastModified() / 1000L));
            try (FileInputStream fis = new FileInputStream(file)) {
                copyStream(fis, out);
            }
            int mod = (int) (len % 512L);
            if (mod > 0) {
                out.write(new byte[512 - mod]);
            }
            doneCount[0]++;
            if (cb != null) cb.onProgress(file.getName(), doneCount[0], totalCount);
        }
    }

    private static byte[] buildTarHeader(String name, long size, boolean isDir, long mtimeSec) {
        byte[] hdr = new byte[512];
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(nameBytes, 0, hdr, 0, Math.min(nameBytes.length, 100));
        writeOctal(isDir ? 0755 : 0644, hdr, 100, 8);
        writeOctal(0, hdr, 108, 8);
        writeOctal(0, hdr, 116, 8);
        writeOctal(size, hdr, 124, 12);
        writeOctal(Math.max(0, mtimeSec), hdr, 136, 12);
        Arrays.fill(hdr, 148, 156, (byte) ' ');
        hdr[156] = (byte) (isDir ? '5' : '0');
        byte[] ustar = "ustar\0".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(ustar, 0, hdr, 257, ustar.length);
        hdr[263] = '0';
        hdr[264] = '0';
        long sum = 0;
        for (byte b : hdr) sum += (b & 0xFF);
        writeOctal(sum, hdr, 148, 7);
        hdr[155] = ' ';
        return hdr;
    }

    private static void writeOctal(long val, byte[] buf, int offset, int length) {
        int idx = offset + length - 1;
        buf[idx--] = 0;
        long v = val;
        while (idx >= offset) {
            buf[idx--] = (byte) ('0' + (v & 7));
            v >>>= 3;
        }
    }

    private static List<ArchiveEntryInfo> listTarEntries(InputStream is) throws IOException {
        List<ArchiveEntryInfo> entries = new ArrayList<>();
        byte[] hdr = new byte[512];
        while (readExact(is, hdr, 512) == 512) {
            if (isAllZero(hdr)) break;
            String name = readCString(hdr, 0, 100);
            String prefix = readCString(hdr, 345, 155);
            if (!prefix.isEmpty()) name = prefix + "/" + name;
            long size = parseOctal(hdr, 124, 12);
            boolean isDir = hdr[156] == '5' || name.endsWith("/");
            entries.add(new ArchiveEntryInfo(name, size, size, isDir, false));
            long blocks = (size + 511L) / 512L;
            skipFully(is, blocks * 512L);
        }
        return entries;
    }

    private static int extractTarStream(InputStream is, File destDir, String canonicalDestDir,
                                        ArchiveProgressCallback cb, int totalCount) throws IOException {
        int count = 0;
        byte[] hdr = new byte[512];
        while (readExact(is, hdr, 512) == 512) {
            if (isAllZero(hdr)) break;
            String name = readCString(hdr, 0, 100);
            String prefix = readCString(hdr, 345, 155);
            if (!prefix.isEmpty()) name = prefix + "/" + name;
            long size = parseOctal(hdr, 124, 12);
            boolean isDir = hdr[156] == '5' || name.endsWith("/");
            File outFile = safeResolve(destDir, canonicalDestDir, name);
            if (isDir) {
                outFile.mkdirs();
            } else {
                if (cb != null) cb.onProgress(outFile.getName(), count, Math.max(1, totalCount));
                File parent = outFile.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outFile))) {
                    copyFixed(is, bos, size);
                }
                int mod = (int) (size % 512L);
                if (mod > 0) skipFully(is, 512L - mod);
                count++;
                if (cb != null) cb.onProgress(outFile.getName(), count, Math.max(1, totalCount));
            }
        }
        return count;
    }

    private static void extractTarEntryToStream(InputStream is, String targetName, OutputStream out) throws IOException {
        byte[] hdr = new byte[512];
        while (readExact(is, hdr, 512) == 512) {
            if (isAllZero(hdr)) break;
            String name = readCString(hdr, 0, 100);
            String prefix = readCString(hdr, 345, 155);
            if (!prefix.isEmpty()) name = prefix + "/" + name;
            long size = parseOctal(hdr, 124, 12);
            boolean isDir = hdr[156] == '5' || name.endsWith("/");
            if (!isDir && name.equals(targetName)) {
                copyFixed(is, out, size);
                return;
            }
            long blocks = isDir ? 0L : (size + 511L) / 512L;
            skipFully(is, blocks * 512L);
        }
        throw new IOException("הפריט לא נמצא ב-TAR: " + targetName);
    }

    private static byte[] readTarEntryBytes(InputStream is, String targetName) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        extractTarEntryToStream(is, targetName, baos);
        return baos.toByteArray();
    }

    private static File safeResolve(File destDir, String canonicalDestDir, String entryName) throws IOException {
        if (!isSafeEntryName(entryName)) {
            throw new SecurityException("זוהה נתיב קובץ לא חוקי בארכיון: " + entryName);
        }
        File outFile = new File(destDir, entryName);
        String canonicalOut = outFile.getCanonicalPath();
        if (!canonicalOut.startsWith(canonicalDestDir + File.separator) && !canonicalOut.equals(canonicalDestDir)) {
            throw new SecurityException("זוהה נתיב קובץ מחוץ לתיקיית היעד");
        }
        return outFile;
    }

    private static String readCString(byte[] buf, int off, int len) {
        int end = off;
        while (end < off + len && buf[end] != 0) end++;
        return new String(buf, off, end - off, StandardCharsets.UTF_8).trim();
    }

    private static long parseOctal(byte[] buf, int off, int len) {
        long res = 0;
        for (int i = off; i < off + len; i++) {
            byte b = buf[i];
            if (b == 0 || b == ' ') {
                if (res > 0) break;
                continue;
            }
            if (b >= '0' && b <= '7') {
                res = (res << 3) + (b - '0');
            }
        }
        return res;
    }

    private static boolean isAllZero(byte[] buf) {
        for (byte b : buf) if (b != 0) return false;
        return true;
    }

    private static int readExact(InputStream is, byte[] buf, int len) throws IOException {
        int total = 0;
        while (total < len) {
            int r = is.read(buf, total, len - total);
            if (r < 0) break;
            total += r;
        }
        return total;
    }

    private static void skipFully(InputStream is, long n) throws IOException {
        long rem = n;
        byte[] dump = new byte[32 * 1024];
        while (rem > 0) {
            int r = is.read(dump, 0, (int) Math.min(dump.length, rem));
            if (r < 0) throw new EOFException();
            rem -= r;
        }
    }

    private static void copyFixed(InputStream is, OutputStream os, long len) throws IOException {
        byte[] buf = new byte[COPY_BUFFER_SIZE];
        long rem = len;
        while (rem > 0) {
            int r = is.read(buf, 0, (int) Math.min(buf.length, rem));
            if (r < 0) throw new EOFException();
            os.write(buf, 0, r);
            rem -= r;
        }
    }

    private static void copyStream(InputStream is, OutputStream os) throws IOException {
        byte[] buf = new byte[COPY_BUFFER_SIZE];
        int r;
        while ((r = is.read(buf)) != -1) {
            os.write(buf, 0, r);
        }
    }

    private static byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (InputStream src = is) {
            copyStream(src, baos);
        }
        return baos.toByteArray();
    }

    public static String extractOfficeDocumentText(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder();
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                boolean relevant = (name.endsWith(".docx") && "word/document.xml".equals(entryName))
                        || (name.endsWith(".xlsx") && ("xl/sharedStrings.xml".equals(entryName) || entryName.startsWith("xl/worksheets/sheet")))
                        || (name.endsWith(".pptx") && entryName.startsWith("ppt/slides/slide") && entryName.endsWith(".xml"));
                if (relevant) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    copyStream(zis, baos);
                    String xml = new String(baos.toByteArray(), StandardCharsets.UTF_8);
                    result.append(parseXmlPlainText(xml)).append("\n");
                }
                zis.closeEntry();
            }
        } catch (Exception e) {
            return "לא ניתן לקרוא תוכן מסמך זה: " + e.getMessage();
        }
        String text = result.toString().trim();
        return text.isEmpty() ? "(המסמך ריק או מכיל אובייקטים גרפיים בלבד)" : text;
    }

    private static String parseXmlPlainText(String xml) {
        StringBuilder sb = new StringBuilder();
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(new StringReader(xml));
            int event = parser.getEventType();
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.TEXT) {
                    String t = parser.getText();
                    if (t != null && !t.isEmpty()) sb.append(t);
                } else if (event == XmlPullParser.END_TAG) {
                    String tag = parser.getName();
                    if ("p".equals(tag) || tag.endsWith(":p") || "row".equals(tag) || "si".equals(tag)) {
                        sb.append("\n");
                    } else if ("t".equals(tag) || "v".equals(tag)) {
                        sb.append(" ");
                    }
                }
                event = parser.next();
            }
        } catch (Exception ignored) {
        }
        return sb.toString();
    }
}
