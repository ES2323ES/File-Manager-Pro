package com.aishhatorah.filemanagerpro;

import java.util.Locale;

/**
 * סעיפים 2, 9, 12, 13, 14, 40, 41:
 * זיהוי מלא של כלל פורמטי תמונה (כולל GIF ו-SVG), שמע, וידאו, קוד ותכנות, ארכיונים (ZIP/TAR/GZ/TAR.GZ),
 * קבצי גיבוי .bak, מסמכים וחבילות התקנה לאנדרואיד.
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
            ".apk", ".apkx", ".apk+", ".apks", ".apkm", ".xapk", ".apkp", ".apk1", ".apks+"
    };

    private static final String[] ARCHIVE_EXTS = {
            ".tar.gz", ".tgz", ".zip", ".tar", ".gz", ".jar", ".cbz"
    };

    private static final String[] CODE_EXTS = {
            ".ts", ".tsx", ".js", ".mjs", ".cjs", ".jsx", ".py", ".sh", ".bash", ".zsh",
            ".java", ".kt", ".kts", ".c", ".h", ".cpp", ".hpp", ".cc", ".cs",
            ".html", ".htm", ".css", ".scss", ".rs", ".bat", ".cmd", ".ps1",
            ".properties", ".prop", ".smali", ".php", ".yaml", ".yml", ".json",
            ".xml", ".svg", ".sql", ".md", ".markdown", ".gradle", ".ini", ".cfg", ".conf", ".toml"
    };

    private static final String[] TEXT_EXTS = {
            ".txt", ".log", ".md", ".markdown", ".csv", ".tsv", ".json", ".xml", ".yaml", ".yml",
            ".ini", ".cfg", ".conf", ".properties", ".prop", ".html", ".htm", ".js", ".mjs", ".jsx",
            ".ts", ".tsx", ".css", ".scss", ".java", ".kt", ".kts", ".c", ".h", ".cpp", ".hpp", ".cc",
            ".py", ".sh", ".bash", ".rs", ".bat", ".cmd", ".smali", ".php", ".sql", ".rtf", ".gradle",
            ".toml", ".bak"
    };

    private static final String[] DOCUMENT_EXTS = {
            ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".odt", ".ods", ".odp",
            ".txt", ".rtf", ".csv", ".tsv", ".md", ".markdown", ".log", ".json", ".xml", ".html", ".htm",
            ".yaml", ".yml", ".sql", ".java", ".kt", ".py", ".js", ".ts", ".css", ".php", ".smali", ".c", ".cpp"
    };

    public static boolean isImageFile(String fileName) {
        return endsWithAny(fileName, IMAGE_EXTS);
    }

    public static boolean isGifFile(String fileName) {
        return fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".gif");
    }

    public static boolean isSvgFile(String fileName) {
        if (fileName == null) return false;
        String l = fileName.toLowerCase(Locale.ROOT);
        return l.endsWith(".svg") || l.endsWith(".svgz");
    }

    public static boolean isMarkdownFile(String fileName) {
        if (fileName == null) return false;
        String l = fileName.toLowerCase(Locale.ROOT);
        return l.endsWith(".md") || l.endsWith(".markdown");
    }

    public static boolean isBackupFile(String fileName) {
        return fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".bak");
    }

    public static boolean isPdfFile(String fileName) {
        return fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    public static boolean isAudioFile(String fileName) {
        return endsWithAny(fileName, AUDIO_EXTS);
    }

    public static boolean isVideoFile(String fileName) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase(Locale.ROOT);
        // קובץ .ts במדיה לרוב וידאו, אך בקוד TypeScript קטן נזהה אותו בקוד רק כשנדרש או נאפשר פתיחה בשניהם
        return endsWithAny(lower, VIDEO_EXTS);
    }

    public static boolean isInstallableAppPackage(String fileName) {
        if (fileName == null) return false;
        String trimmed = fileName.trim().toLowerCase(Locale.ROOT);
        if (endsWithAny(trimmed, APP_PACKAGE_EXTS)) return true;
        return trimmed.matches(".*\\.apk[x+0-9_]+$");
    }

    public static boolean isSplitAppBundle(String fileName) {
        if (fileName == null) return false;
        String trimmed = fileName.trim().toLowerCase(Locale.ROOT);
        return endsWithAny(trimmed, ".apkx", ".apk+", ".apks", ".apkm", ".xapk", ".apkp", ".apks+")
                || trimmed.matches(".*\\.apk[x+]+$");
    }

    public static boolean isZipOrArchiveFile(String fileName) {
        return endsWithAny(fileName, ARCHIVE_EXTS);
    }

    public static boolean isZipContainer(String fileName) {
        if (fileName == null) return false;
        String l = fileName.toLowerCase(Locale.ROOT);
        return l.endsWith(".zip") || l.endsWith(".jar") || l.endsWith(".cbz");
    }

    public static boolean isTarGzArchive(String fileName) {
        if (fileName == null) return false;
        String l = fileName.toLowerCase(Locale.ROOT);
        return l.endsWith(".tar.gz") || l.endsWith(".tgz");
    }

    public static boolean isTarArchive(String fileName) {
        return fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".tar");
    }

    public static boolean isGzArchive(String fileName) {
        if (fileName == null) return false;
        String l = fileName.toLowerCase(Locale.ROOT);
        return l.endsWith(".gz") && !l.endsWith(".tar.gz");
    }

    public static boolean isCodeOrSyntaxFile(String fileName) {
        return endsWithAny(fileName, CODE_EXTS);
    }

    public static boolean isTextFile(String fileName) {
        return endsWithAny(fileName, TEXT_EXTS);
    }

    public static boolean isDocumentFile(String fileName) {
        return endsWithAny(fileName, DOCUMENT_EXTS);
    }

    public static String getExtensionNormalized(String fileName) {
        if (fileName == null) return "";
        String lower = fileName.trim().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".tar.gz")) return ".tar.gz";
        int dot = lower.lastIndexOf('.');
        if (dot >= 0 && dot < lower.length() - 1) {
            return lower.substring(dot);
        }
        return "";
    }

    public static boolean isSupportedBuiltInFormat(String fileName) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase(Locale.ROOT);
        return isImageFile(lower)
                || isAudioFile(lower)
                || isVideoFile(lower)
                || isInstallableAppPackage(lower)
                || isZipOrArchiveFile(lower)
                || isTextFile(lower)
                || isPdfFile(lower)
                || lower.endsWith(".docx")
                || lower.endsWith(".xlsx")
                || lower.endsWith(".pptx");
    }

    private static boolean endsWithAny(String fileName, String... exts) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase(Locale.ROOT);
        for (String ext : exts) {
            if (lower.endsWith(ext)) return true;
        }
        return false;
    }
}
