package com.aishhatorah.filemanagerpro;

import java.util.Locale;

/**
 * סעיפים ט, יא, יב: זיהוי מרכזי ומלא של כלל סוגי קובצי האודיו, הוידאו, התמונות,
 * הארכיונים, המסמכים וחבילות ההתקנה לאנדרואיד (APK / APKS / APKM / XAPK).
 */
public final class MediaFormatHelper {

    private MediaFormatHelper() {
    }

    private static final String[] IMAGE_EXTS = {
            ".jpg", ".jpeg", ".jpe", ".jfif", ".png", ".webp", ".gif", ".bmp", ".dib",
            ".heic", ".heif", ".avif", ".ico", ".svg", ".svgz", ".tif", ".tiff",
            ".dng", ".cr2", ".nef", ".arw", ".raw", ".wbmp"
    };

    private static final String[] AUDIO_EXTS = {
            ".mp3", ".m4a", ".wav", ".aac", ".flac", ".ogg", ".oga", ".opus",
            ".amr", ".awb", ".wma", ".mid", ".midi", ".xmf", ".mxmf", ".imy",
            ".rtttl", ".rtx", ".ota", ".3ga", ".ac3", ".aiff", ".aif", ".ape",
            ".mka", ".mp2", ".mpga", ".ra", ".wv", ".dsf", ".m4b", ".m4r", ".caf"
    };

    private static final String[] VIDEO_EXTS = {
            ".mp4", ".mkv", ".mov", ".avi", ".webm", ".3gp", ".3g2", ".flv", ".f4v",
            ".m4v", ".mpeg", ".mpg", ".mts", ".m2ts", ".ts", ".vob", ".wmv",
            ".asf", ".ogv", ".divx", ".rmvb", ".rm", ".mp2v"
    };

    private static final String[] APP_PACKAGE_EXTS = {
            ".apk", ".apks", ".apkm", ".xapk"
    };

    private static final String[] ARCHIVE_EXTS = {
            ".zip", ".jar", ".cbz"
    };

    private static final String[] TEXT_EXTS = {
            ".txt", ".log", ".md", ".csv", ".tsv", ".json", ".xml", ".yaml", ".yml",
            ".ini", ".cfg", ".conf", ".properties", ".html", ".htm", ".js", ".css",
            ".java", ".kt", ".py", ".sh", ".sql", ".rtf"
    };

    private static final String[] DOCUMENT_EXTS = {
            ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".odt", ".ods", ".odp",
            ".txt", ".rtf", ".csv", ".tsv", ".md", ".log", ".json", ".xml", ".html", ".htm"
    };

    public static boolean isImageFile(String fileName) {
        return endsWithAny(fileName, IMAGE_EXTS);
    }

    public static boolean isAudioFile(String fileName) {
        return endsWithAny(fileName, AUDIO_EXTS);
    }

    public static boolean isVideoFile(String fileName) {
        return endsWithAny(fileName, VIDEO_EXTS);
    }

    public static boolean isInstallableAppPackage(String fileName) {
        return endsWithAny(fileName, APP_PACKAGE_EXTS);
    }

    public static boolean isSplitAppBundle(String fileName) {
        return endsWithAny(fileName, ".apks", ".apkm", ".xapk");
    }

    public static boolean isZipOrArchiveFile(String fileName) {
        return endsWithAny(fileName, ARCHIVE_EXTS);
    }

    public static boolean isTextFile(String fileName) {
        return endsWithAny(fileName, TEXT_EXTS);
    }

    public static boolean isDocumentFile(String fileName) {
        return endsWithAny(fileName, DOCUMENT_EXTS);
    }

    private static boolean endsWithAny(String fileName, String[] exts) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase(Locale.ROOT);
        for (String ext : exts) {
            if (lower.endsWith(ext)) return true;
        }
        return false;
    }
}
