package com.aishhatorah.filemanagerpro;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ArchiveUtils {

    public static File compressToZip(File source, File targetZipFile) throws IOException {
        if (source == null || !source.exists()) {
            throw new IOException("קובץ המקור אינו קיים");
        }
        try (FileOutputStream fos = new FileOutputStream(targetZipFile);
             BufferedOutputStream bos = new BufferedOutputStream(fos);
             ZipOutputStream zos = new ZipOutputStream(bos)) {
            if (source.isDirectory()) {
                zipDirectoryRecursive(source, source.getName(), zos);
            } else {
                zipSingleFile(source, source.getName(), zos);
            }
        }
        return targetZipFile;
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
        byte[] buffer = new byte[16 * 1024];
        zos.putNextEntry(new ZipEntry(entryName));
        try (FileInputStream fis = new FileInputStream(file);
             BufferedInputStream bis = new BufferedInputStream(fis)) {
            int count;
            while ((count = bis.read(buffer)) != -1) {
                zos.write(buffer, 0, count);
            }
        }
        zos.closeEntry();
    }

    public static List<String> listZipEntries(File zipFile) throws IOException {
        List<String> entries = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(zipFile);
             BufferedInputStream bis = new BufferedInputStream(fis);
             ZipInputStream zis = new ZipInputStream(bis)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null && entries.size() < 300) {
                String label = entry.getName() + (entry.isDirectory() ? " (תיקייה)" :
                        (entry.getSize() >= 0 ? "  •  " + StorageUtils.formatSize(entry.getSize()) : ""));
                entries.add(label);
                zis.closeEntry();
            }
        }
        return entries;
    }

    public static int extractZip(File zipFile, File destinationDir) throws IOException {
        if (!destinationDir.exists() && !destinationDir.mkdirs()) {
            throw new IOException("לא ניתן ליצור תיקיית יעד לחילוץ");
        }
        String canonicalDestDir = destinationDir.getCanonicalPath();
        int extractedCount = 0;
        byte[] buffer = new byte[16 * 1024];

        try (FileInputStream fis = new FileInputStream(zipFile);
             BufferedInputStream bis = new BufferedInputStream(fis);
             ZipInputStream zis = new ZipInputStream(bis)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File outFile = new File(destinationDir, entry.getName());
                String canonicalOut = outFile.getCanonicalPath();
                if (!canonicalOut.startsWith(canonicalDestDir + File.separator) && !canonicalOut.equals(canonicalDestDir)) {
                    throw new SecurityException("זוהה נתיב קובץ לא חוקי בתוך ארכיון ה-ZIP");
                }

                if (entry.isDirectory()) {
                    outFile.mkdirs();
                } else {
                    File parent = outFile.getParentFile();
                    if (parent != null && !parent.exists()) parent.mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(outFile);
                         BufferedOutputStream bos = new BufferedOutputStream(fos)) {
                        int len;
                        while ((len = zis.read(buffer)) != -1) {
                            bos.write(buffer, 0, len);
                        }
                    }
                    extractedCount++;
                }
                zis.closeEntry();
            }
        }
        return extractedCount;
    }

    public static String extractOfficeDocumentText(File file) {
        String name = file.getName().toLowerCase();
        StringBuilder result = new StringBuilder();
        try (FileInputStream fis = new FileInputStream(file);
             ZipInputStream zis = new ZipInputStream(new BufferedInputStream(fis))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                boolean relevant = (name.endsWith(".docx") && "word/document.xml".equals(entryName))
                        || (name.endsWith(".xlsx") && ("xl/sharedStrings.xml".equals(entryName) || entryName.startsWith("xl/worksheets/sheet")))
                        || (name.endsWith(".pptx") && entryName.startsWith("ppt/slides/slide") && entryName.endsWith(".xml"));
                if (relevant) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int r;
                    while ((r = zis.read(buf)) != -1) baos.write(buf, 0, r);
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
                    if (t != null && !t.isEmpty()) {
                        sb.append(t);
                    }
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
