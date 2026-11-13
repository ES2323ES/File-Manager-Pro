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
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
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

    public static File compressFiles(List<File> sources, File targetFile, String format, String password) throws IOException {
        if (sources == null || sources.isEmpty()) {
            throw new IOException("לא נבחרו קבצים לדחיסה");
        }
        String fmt = format == null ? "zip" : format.toLowerCase(Locale.ROOT);
        if ("tar".equals(fmt)) {
            compressToTar(sources, targetFile, false);
            return targetFile;
        }
        if ("tar.gz".equals(fmt) || "tgz".equals(fmt)) {
            compressToTar(sources, targetFile, true);
            return targetFile;
        }
        if ("gz".equals(fmt)) {
            File single = sources.get(0);
            if (single.isDirectory()) {
                compressToTar(sources, targetFile, true);
                return targetFile;
            }
            try (FileInputStream fis = new FileInputStream(single);
                 GZIPOutputStream gos = new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(targetFile)))) {
                copyStream(fis, gos);
            }
            return targetFile;
        }

        boolean hasPwd = password != null && !password.isEmpty();
        if (hasPwd) {
            writePasswordProtectedZip(sources, targetFile, password);
        } else {
            try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(targetFile)))) {
                for (File src : sources) {
                    if (src == null || !src.exists()) continue;
                    if (src.isDirectory()) {
                        zipDirectoryRecursive(src, src.getName(), zos);
                    } else {
                        zipSingleFile(src, src.getName(), zos);
                    }
                }
            }
        }
        return targetFile;
    }

    private static void zipDirectoryRecursive(File folder, String parentEntryPath, ZipOutputStream zos) throws IOException {
        File[] children = folder.listFiles();
        if (children == null || children.length == 0) {
            zos.putNextEntry(new ZipEntry(parentEntryPath + "/"));
            zos.closeEntry();
            return;
        }
        for (File child : children) {
            String entryName = parentEntryPath + "/" + child.getName();
            if (child.isDirectory()) {
                zipDirectoryRecursive(child, entryName, zos);
            } else {
                zipSingleFile(child, entryName, zos);
            }
        }
    }

    private static void zipSingleFile(File file, String entryName, ZipOutputStream zos) throws IOException {
        zos.putNextEntry(new ZipEntry(entryName));
        try (FileInputStream fis = new FileInputStream(file);
             BufferedInputStream bis = new BufferedInputStream(fis)) {
            copyStream(bis, zos);
        }
        zos.closeEntry();
    }

    public static List<String> listZipEntries(File archiveFile) throws IOException {
        List<ArchiveEntryInfo> detailed = listArchiveEntries(archiveFile);
        List<String> out = new ArrayList<>();
        for (ArchiveEntryInfo info : detailed) {
            String label = info.name + (info.directory ? " (תיקייה)" :
                    (info.size >= 0 ? "  •  " + StorageUtils.formatSize(info.size) : ""))
                    + (info.encrypted ? " 🔒" : "");
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
        if (!destinationDir.exists() && !destinationDir.mkdirs()) {
            throw new IOException("לא ניתן ליצור תיקיית יעד לחילוץ");
        }
        String canonicalDestDir = destinationDir.getCanonicalPath();
        String name = archiveFile.getName().toLowerCase(Locale.ROOT);

        if (MediaFormatHelper.isTarGzArchive(name)) {
            try (InputStream is = new GZIPInputStream(new BufferedInputStream(new FileInputStream(archiveFile)))) {
                return extractTarStream(is, destinationDir, canonicalDestDir);
            }
        }
        if (MediaFormatHelper.isTarArchive(name)) {
            try (InputStream is = new BufferedInputStream(new FileInputStream(archiveFile))) {
                return extractTarStream(is, destinationDir, canonicalDestDir);
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
            File outFile = safeResolve(destinationDir, canonicalDestDir, outName);
            try (GZIPInputStream gis = new GZIPInputStream(new BufferedInputStream(new FileInputStream(archiveFile)));
                 FileOutputStream fos = new FileOutputStream(outFile)) {
                copyStream(gis, fos);
            }
            return 1;
        }

        if ((password != null && !password.isEmpty()) || archiveRequiresPassword(archiveFile)) {
            return extractRawZipWithOptionalPassword(archiveFile, destinationDir, canonicalDestDir, password);
        }

        int extractedCount = 0;
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(archiveFile)))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File outFile = safeResolve(destinationDir, canonicalDestDir, entry.getName());
                if (entry.isDirectory()) {
                    outFile.mkdirs();
                } else {
                    File parent = outFile.getParentFile();
                    if (parent != null && !parent.exists()) parent.mkdirs();
                    try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outFile))) {
                        copyStream(zis, bos);
                    }
                    extractedCount++;
                }
                zis.closeEntry();
            }
        }
        return extractedCount;
    }

    public static byte[] readEntryBytes(File archiveFile, String targetEntryName, String password) throws IOException {
        String name = archiveFile.getName().toLowerCase(Locale.ROOT);
        if (MediaFormatHelper.isTarGzArchive(name)) {
            try (InputStream is = new GZIPInputStream(new BufferedInputStream(new FileInputStream(archiveFile)))) {
                return readTarEntryBytes(is, targetEntryName);
            }
        }
        if (MediaFormatHelper.isTarArchive(name)) {
            try (InputStream is = new BufferedInputStream(new FileInputStream(archiveFile))) {
                return readTarEntryBytes(is, targetEntryName);
            }
        }
        if (MediaFormatHelper.isGzArchive(name)) {
            try (GZIPInputStream gis = new GZIPInputStream(new BufferedInputStream(new FileInputStream(archiveFile)))) {
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

    private static List<ArchiveEntryInfo> listZipCentralDirectory(File zipFile) throws IOException {
        byte[] data = readAllBytes(new FileInputStream(zipFile));
        int eocdOffset = -1;
        for (int i = Math.max(0, data.length - 65557); i <= data.length - 22; i++) {
            if (data[i] == 0x50 && data[i + 1] == 0x4b && data[i + 2] == 0x05 && data[i + 3] == 0x06) {
                eocdOffset = i;
            }
        }
        if (eocdOffset < 0) {
            throw new IOException("קובץ ZIP אינו תקין");
        }
        ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int cdSize = bb.getInt(eocdOffset + 12);
        int cdOffset = bb.getInt(eocdOffset + 16);
        List<ArchiveEntryInfo> result = new ArrayList<>();
        int pos = cdOffset;
        int end = Math.min(data.length, cdOffset + cdSize);
        while (pos + 46 <= end) {
            if (bb.getInt(pos) != 0x02014b50) break;
            int flags = bb.getShort(pos + 8) & 0xFFFF;
            long compSize = bb.getInt(pos + 20) & 0xFFFFFFFFL;
            long uncompSize = bb.getInt(pos + 24) & 0xFFFFFFFFL;
            int nameLen = bb.getShort(pos + 28) & 0xFFFF;
            int extraLen = bb.getShort(pos + 30) & 0xFFFF;
            int commentLen = bb.getShort(pos + 32) & 0xFFFF;
            if (pos + 46 + nameLen > data.length) break;
            String name = new String(data, pos + 46, nameLen, StandardCharsets.UTF_8);
            boolean encrypted = (flags & 0x0001) != 0;
            boolean dir = name.endsWith("/");
            result.add(new ArchiveEntryInfo(name, uncompSize, compSize, dir, encrypted));
            pos += 46 + nameLen + extraLen + commentLen;
        }
        return result;
    }

    private static int extractRawZipWithOptionalPassword(File zipFile, File destDir, String canonicalDestDir, String password) throws IOException {
        byte[] fileBytes = readAllBytes(new FileInputStream(zipFile));
        int eocd = -1;
        for (int i = Math.max(0, fileBytes.length - 65557); i <= fileBytes.length - 22; i++) {
            if (fileBytes[i] == 0x50 && fileBytes[i + 1] == 0x4b && fileBytes[i + 2] == 0x05 && fileBytes[i + 3] == 0x06) {
                eocd = i;
            }
        }
        if (eocd < 0) throw new IOException("קובץ ZIP פגום");
        ByteBuffer bb = ByteBuffer.wrap(fileBytes).order(ByteOrder.LITTLE_ENDIAN);
        int cdSize = bb.getInt(eocd + 12);
        int cdOffset = bb.getInt(eocd + 16);
        int pos = cdOffset;
        int end = Math.min(fileBytes.length, cdOffset + cdSize);
        int extracted = 0;

        while (pos + 46 <= end) {
            if (bb.getInt(pos) != 0x02014b50) break;
            int flags = bb.getShort(pos + 8) & 0xFFFF;
            int method = bb.getShort(pos + 10) & 0xFFFF;
            long crc = bb.getInt(pos + 16) & 0xFFFFFFFFL;
            int compSize = bb.getInt(pos + 20);
            int nameLen = bb.getShort(pos + 28) & 0xFFFF;
            int extraLen = bb.getShort(pos + 30) & 0xFFFF;
            int commentLen = bb.getShort(pos + 32) & 0xFFFF;
            int localHeaderOffset = bb.getInt(pos + 42);
            String name = new String(fileBytes, pos + 46, nameLen, StandardCharsets.UTF_8);
            pos += 46 + nameLen + extraLen + commentLen;

            File outFile = safeResolve(destDir, canonicalDestDir, name);
            if (name.endsWith("/")) {
                outFile.mkdirs();
                continue;
            }
            byte[] plain = decodeLocalZipEntry(fileBytes, localHeaderOffset, flags, method, crc, compSize, password);
            File parent = outFile.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                fos.write(plain);
            }
            extracted++;
        }
        return extracted;
    }

    private static byte[] readRawZipEntryBytes(File zipFile, String targetName, String password) throws IOException {
        byte[] fileBytes = readAllBytes(new FileInputStream(zipFile));
        int eocd = -1;
        for (int i = Math.max(0, fileBytes.length - 65557); i <= fileBytes.length - 22; i++) {
            if (fileBytes[i] == 0x50 && fileBytes[i + 1] == 0x4b && fileBytes[i + 2] == 0x05 && fileBytes[i + 3] == 0x06) {
                eocd = i;
            }
        }
        if (eocd < 0) throw new IOException("קובץ ZIP פגום");
        ByteBuffer bb = ByteBuffer.wrap(fileBytes).order(ByteOrder.LITTLE_ENDIAN);
        int cdSize = bb.getInt(eocd + 12);
        int cdOffset = bb.getInt(eocd + 16);
        int pos = cdOffset;
        int end = Math.min(fileBytes.length, cdOffset + cdSize);

        while (pos + 46 <= end) {
            if (bb.getInt(pos) != 0x02014b50) break;
            int flags = bb.getShort(pos + 8) & 0xFFFF;
            int method = bb.getShort(pos + 10) & 0xFFFF;
            long crc = bb.getInt(pos + 16) & 0xFFFFFFFFL;
            int compSize = bb.getInt(pos + 20);
            int nameLen = bb.getShort(pos + 28) & 0xFFFF;
            int extraLen = bb.getShort(pos + 30) & 0xFFFF;
            int commentLen = bb.getShort(pos + 32) & 0xFFFF;
            int localHeaderOffset = bb.getInt(pos + 42);
            String name = new String(fileBytes, pos + 46, nameLen, StandardCharsets.UTF_8);
            pos += 46 + nameLen + extraLen + commentLen;
            if (name.equals(targetName)) {
                return decodeLocalZipEntry(fileBytes, localHeaderOffset, flags, method, crc, compSize, password);
            }
        }
        throw new IOException("הפריט לא נמצא: " + targetName);
    }

    private static byte[] decodeLocalZipEntry(byte[] data, int localOffset, int flags, int method,
                                              long expectedCrc, int compSize, String password) throws IOException {
        ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        if (bb.getInt(localOffset) != 0x04034b50) {
            throw new IOException("רשומת ZIP פגומה");
        }
        int lNameLen = bb.getShort(localOffset + 26) & 0xFFFF;
        int lExtraLen = bb.getShort(localOffset + 28) & 0xFFFF;
        int dataStart = localOffset + 30 + lNameLen + lExtraLen;
        boolean encrypted = (flags & 0x0001) != 0;
        byte[] payload = Arrays.copyOfRange(data, dataStart, dataStart + compSize);

        if (encrypted) {
            if (password == null || password.isEmpty()) {
                throw new IOException("הארכיון מוגן בסיסמה — נדרשת סיסמה");
            }
            if ((flags & 0x0008) == 0 && payload.length < 12) {
                throw new IOException("נתוני הצפנה לא תקינים");
            }
            ZipCryptoCipher cipher = new ZipCryptoCipher(password.getBytes(StandardCharsets.UTF_8));
            byte[] header = new byte[12];
            for (int i = 0; i < 12; i++) {
                header[i] = cipher.decryptByte(payload[i]);
            }
            int expectedCheck = (int) ((expectedCrc >>> 24) & 0xFF);
            if ((flags & 0x0008) == 0 && (header[11] & 0xFF) != expectedCheck) {
                throw new IOException("סיסמה שגויה");
            }
            byte[] decrypted = new byte[payload.length - 12];
            for (int i = 12; i < payload.length; i++) {
                decrypted[i - 12] = cipher.decryptByte(payload[i]);
            }
            payload = decrypted;
        }

        if (method == 0) {
            return payload;
        } else if (method == 8) {
            Inflater inflater = new Inflater(true);
            try (InflaterInputStream iis = new InflaterInputStream(new ByteArrayInputStream(payload), inflater)) {
                return readAllBytes(iis);
            } finally {
                inflater.end();
            }
        } else {
            throw new IOException("שיטת דחיסה לא נתמכת: " + method);
        }
    }

    private static void writePasswordProtectedZip(List<File> sources, File targetZip, String password) throws IOException {
        List<FileItem> items = new ArrayList<>();
        for (File f : sources) {
            collectItemsRecursive(f, "", items);
        }
        byte[] pwdBytes = password.getBytes(StandardCharsets.UTF_8);
        SecureRandom rng = new SecureRandom();
        ByteArrayOutputStream centralDir = new ByteArrayOutputStream();
        long offset = 0;
        int entryCount = 0;

        try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(targetZip))) {
            for (FileItem item : items) {
                byte[] nameBytes = item.entryName.getBytes(StandardCharsets.UTF_8);
                byte[] rawData = item.isDir ? new byte[0] : readAllBytes(new FileInputStream(item.file));
                CRC32 crc32 = new CRC32();
                crc32.update(rawData);
                long crc = crc32.getValue();

                byte[] compressed;
                int method;
                if (item.isDir || rawData.length == 0) {
                    compressed = rawData;
                    method = 0;
                } else {
                    ByteArrayOutputStream defOut = new ByteArrayOutputStream();
                    Deflater def = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
                    try (DeflaterOutputStream dos = new DeflaterOutputStream(defOut, def)) {
                        dos.write(rawData);
                    } finally {
                        def.end();
                    }
                    compressed = defOut.toByteArray();
                    method = 8;
                }

                byte[] encryptedPayload;
                int flags = 0x0800; // UTF-8
                if (!item.isDir) {
                    flags |= 0x0001; // Encrypted
                    ZipCryptoCipher cipher = new ZipCryptoCipher(pwdBytes);
                    encryptedPayload = new byte[12 + compressed.length];
                    byte[] hdr = new byte[12];
                    rng.nextBytes(hdr);
                    hdr[11] = (byte) ((crc >>> 24) & 0xFF);
                    for (int i = 0; i < 12; i++) encryptedPayload[i] = cipher.encryptByte(hdr[i]);
                    for (int i = 0; i < compressed.length; i++) encryptedPayload[12 + i] = cipher.encryptByte(compressed[i]);
                } else {
                    encryptedPayload = compressed;
                }

                int localOffset = (int) offset;
                ByteBuffer lh = ByteBuffer.allocate(30).order(ByteOrder.LITTLE_ENDIAN);
                lh.putInt(0x04034b50);
                lh.putShort((short) 20);
                lh.putShort((short) flags);
                lh.putShort((short) method);
                lh.putShort((short) 0);
                lh.putShort((short) 0x5421);
                lh.putInt((int) crc);
                lh.putInt(encryptedPayload.length);
                lh.putInt(rawData.length);
                lh.putShort((short) nameBytes.length);
                lh.putShort((short) 0);

                out.write(lh.array());
                out.write(nameBytes);
                out.write(encryptedPayload);
                offset += 30 + nameBytes.length + encryptedPayload.length;

                ByteBuffer cd = ByteBuffer.allocate(46).order(ByteOrder.LITTLE_ENDIAN);
                cd.putInt(0x02014b50);
                cd.putShort((short) 20);
                cd.putShort((short) 20);
                cd.putShort((short) flags);
                cd.putShort((short) method);
                cd.putShort((short) 0);
                cd.putShort((short) 0x5421);
                cd.putInt((int) crc);
                cd.putInt(encryptedPayload.length);
                cd.putInt(rawData.length);
                cd.putShort((short) nameBytes.length);
                cd.putShort((short) 0);
                cd.putShort((short) 0);
                cd.putShort((short) 0);
                cd.putShort((short) 0);
                cd.putInt(item.isDir ? 0x10 : 0);
                cd.putInt(localOffset);
                centralDir.write(cd.array());
                centralDir.write(nameBytes);
                entryCount++;
            }

            byte[] cdBytes = centralDir.toByteArray();
            out.write(cdBytes);

            ByteBuffer eocd = ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN);
            eocd.putInt(0x06054b50);
            eocd.putShort((short) 0);
            eocd.putShort((short) 0);
            eocd.putShort((short) entryCount);
            eocd.putShort((short) entryCount);
            eocd.putInt(cdBytes.length);
            eocd.putInt((int) offset);
            eocd.putShort((short) 0);
            out.write(eocd.array());
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

    private static void compressToTar(List<File> sources, File outFile, boolean gzip) throws IOException {
        try (OutputStream rawOut = new BufferedOutputStream(new FileOutputStream(outFile));
             OutputStream tarOut = gzip ? new GZIPOutputStream(rawOut) : rawOut) {
            for (File src : sources) {
                if (src != null && src.exists()) {
                    writeTarRecursive(src, src.getName(), tarOut);
                }
            }
            tarOut.write(new byte[1024]); // 2 empty 512B blocks EOF
        }
    }

    private static void writeTarRecursive(File file, String entryPath, OutputStream out) throws IOException {
        if (file.isDirectory()) {
            String dirEntry = entryPath.endsWith("/") ? entryPath : entryPath + "/";
            out.write(buildTarHeader(dirEntry, 0L, true, file.lastModified() / 1000L));
            File[] kids = file.listFiles();
            if (kids != null) {
                for (File k : kids) writeTarRecursive(k, entryPath + "/" + k.getName(), out);
            }
        } else {
            long len = file.length();
            out.write(buildTarHeader(entryPath, len, false, file.lastModified() / 1000L));
            try (FileInputStream fis = new FileInputStream(file)) {
                copyStream(fis, out);
            }
            int mod = (int) (len % 512L);
            if (mod > 0) {
                out.write(new byte[512 - mod]);
            }
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

    private static int extractTarStream(InputStream is, File destDir, String canonicalDestDir) throws IOException {
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
                File parent = outFile.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outFile))) {
                    copyFixed(is, bos, size);
                }
                int mod = (int) (size % 512L);
                if (mod > 0) skipFully(is, 512L - mod);
                count++;
            }
        }
        return count;
    }

    private static byte[] readTarEntryBytes(InputStream is, String targetName) throws IOException {
        byte[] hdr = new byte[512];
        while (readExact(is, hdr, 512) == 512) {
            if (isAllZero(hdr)) break;
            String name = readCString(hdr, 0, 100);
            String prefix = readCString(hdr, 345, 155);
            if (!prefix.isEmpty()) name = prefix + "/" + name;
            long size = parseOctal(hdr, 124, 12);
            boolean isDir = hdr[156] == '5' || name.endsWith("/");
            if (!isDir && name.equals(targetName)) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                copyFixed(is, baos, size);
                return baos.toByteArray();
            }
            long blocks = isDir ? 0L : (size + 511L) / 512L;
            skipFully(is, blocks * 512L);
        }
        throw new IOException("הפריט לא נמצא ב-TAR: " + targetName);
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
        byte[] dump = new byte[4096];
        while (rem > 0) {
            int r = is.read(dump, 0, (int) Math.min(dump.length, rem));
            if (r < 0) throw new EOFException();
            rem -= r;
        }
    }

    private static void copyFixed(InputStream is, OutputStream os, long len) throws IOException {
        byte[] buf = new byte[8192];
        long rem = len;
        while (rem > 0) {
            int r = is.read(buf, 0, (int) Math.min(buf.length, rem));
            if (r < 0) throw new EOFException();
            os.write(buf, 0, r);
            rem -= r;
        }
    }

    private static void copyStream(InputStream is, OutputStream os) throws IOException {
        byte[] buf = new byte[16 * 1024];
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
