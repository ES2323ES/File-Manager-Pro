package com.aishhatorah.filemanagerpro;

import android.content.Context;
import android.os.Build;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * סעיף 37: מנגנון לוגים יומי מקצועי עם סינון רמות (אדום=שגיאה, ורוד=אזהרה, כחול=מידע),
 * ניקוי אוטומטי לאחר 14 יום, והסתרה קפדנית של מידע אישי (נתיבים, מיילים, מספרי טלפון, טוקנים).
 */
public final class AppLogger {

    public static final String LEVEL_INFO = "INFO";
    public static final String LEVEL_WARN = "WARN";
    public static final String LEVEL_ERROR = "ERROR";

    private static final int RETENTION_DAYS = 14;
    private static final Object FILE_LOCK = new Object();
    private static volatile boolean handlerInstalled = false;

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}");
    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("((?:ghp_|gho_|ghu_|ghs_|github_pat_)[A-Za-z0-9_\\-]+|Bearer\\s+[A-Za-z0-9._\\-]+)");
    private static final Pattern STORAGE_PATH_PATTERN =
            Pattern.compile("/(?:storage/emulated/\\d+|sdcard|mnt/[^\\s\"'\\]\\)]+)[^\\s\"'\\]\\)]*");
    private static final Pattern IPV4_PATTERN =
            Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");
    private static final Pattern LONG_DIGITS_PATTERN =
            Pattern.compile("\\b\\d{8,}\\b");

    public static class LogEntry {
        public final long timestamp;
        public final String timeDisplay;
        public final String level;
        public final String tag;
        public final String message;

        public LogEntry(long timestamp, String timeDisplay, String level, String tag, String message) {
            this.timestamp = timestamp;
            this.timeDisplay = timeDisplay;
            this.level = level;
            this.tag = tag;
            this.message = message;
        }
    }

    private AppLogger() {
    }

    public static void init(Context context) {
        if (context == null) return;
        cleanupOldLogs(getLogsDir(context), System.currentTimeMillis());
    }

    public static File getLogsDir(Context context) {
        File dir = new File(context.getFilesDir(), "logs");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static void installUncaughtHandler(Context context) {
        if (handlerInstalled || context == null) return;
        handlerInstalled = true;
        final Context appCtx = context.getApplicationContext();
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                StringWriter sw = new StringWriter();
                throwable.printStackTrace(new PrintWriter(sw));
                error(appCtx, "Crash", "Uncaught in " + thread.getName() + ": " + sw.toString());
            } catch (Throwable ignored) {
            }
            if (prev != null) {
                prev.uncaughtException(thread, throwable);
            }
        });
    }

    public static void info(Context context, String tag, String message) {
        append(context, LEVEL_INFO, tag, message);
    }

    public static void warn(Context context, String tag, String message) {
        append(context, LEVEL_WARN, tag, message);
    }

    public static void error(Context context, String tag, String message) {
        append(context, LEVEL_ERROR, tag, message);
    }

    public static void error(Context context, String tag, String message, Throwable err) {
        String detail = message == null ? "" : message;
        if (err != null) {
            detail = detail + (detail.isEmpty() ? "" : " | ") + err.getClass().getSimpleName() + ": " + err.getMessage();
        }
        append(context, LEVEL_ERROR, tag, detail);
    }

    private static void append(Context context, String level, String tag, String message) {
        if (context == null) return;
        appendToDir(getLogsDir(context), level, tag, message, System.currentTimeMillis());
    }

    public static void appendToDir(File logsDir, String level, String tag, String message, long nowMs) {
        if (logsDir == null) return;
        if (!logsDir.exists()) logsDir.mkdirs();
        String dayName = "log_" + new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(nowMs)) + ".jsonl";
        File target = new File(logsDir, dayName);
        String timeStr = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date(nowMs));
        synchronized (FILE_LOCK) {
            try {
                JSONObject obj = new JSONObject();
                obj.put("ts", nowMs);
                obj.put("time", timeStr);
                obj.put("level", level == null ? LEVEL_INFO : level);
                obj.put("tag", tag == null ? "App" : tag);
                obj.put("msg", message == null ? "" : message);
                String line = obj.toString() + "\n";
                try (FileOutputStream fos = new FileOutputStream(target, true)) {
                    fos.write(line.getBytes(StandardCharsets.UTF_8));
                }
            } catch (Throwable ignored) {
            }
        }
    }

    public static void cleanupOldLogs(File logsDir, long nowMs) {
        if (logsDir == null || !logsDir.exists()) return;
        File[] files = logsDir.listFiles();
        if (files == null) return;
        long cutoff = nowMs - (RETENTION_DAYS * 24L * 3600L * 1000L);
        for (File f : files) {
            if (f.isFile() && f.getName().startsWith("log_") && f.lastModified() > 0 && f.lastModified() < cutoff) {
                f.delete();
            }
        }
    }

    public static List<File> listLogFiles(Context context) {
        List<File> out = new ArrayList<>();
        File[] files = getLogsDir(context).listFiles();
        if (files == null) return out;
        Arrays.sort(files, (a, b) -> b.getName().compareTo(a.getName()));
        for (File f : files) {
            if (f.isFile() && f.getName().endsWith(".jsonl")) {
                out.add(f);
            }
        }
        return out;
    }

    public static List<LogEntry> readEntries(File logFile) {
        List<LogEntry> list = new ArrayList<>();
        if (logFile == null || !logFile.exists()) return list;
        synchronized (FILE_LOCK) {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(new FileInputStream(logFile), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    try {
                        JSONObject o = new JSONObject(line);
                        list.add(new LogEntry(
                                o.optLong("ts", 0L),
                                o.optString("time", ""),
                                o.optString("level", LEVEL_INFO),
                                o.optString("tag", "App"),
                                o.optString("msg", "")
                        ));
                    } catch (Throwable ignored) {
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        Collections.reverse(list);
        return list;
    }

    public static void clearAllLogs(Context context) {
        synchronized (FILE_LOCK) {
            File[] files = getLogsDir(context).listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile()) f.delete();
                }
            }
        }
    }

    /**
     * מסנן ומסווה כל מידע רגיש לפני ייצוא לדיווח ב-GitHub (סעיף 37).
     */
    public static String sanitizeForExport(String raw) {
        if (raw == null) return "";
        String s = raw;
        s = TOKEN_PATTERN.matcher(s).replaceAll("[token]");
        s = EMAIL_PATTERN.matcher(s).replaceAll("[email]");
        s = STORAGE_PATH_PATTERN.matcher(s).replaceAll("[storage-path]");
        s = IPV4_PATTERN.matcher(s).replaceAll("[ip]");
        s = LONG_DIGITS_PATTERN.matcher(s).replaceAll("[id]");
        return s;
    }

    public static String buildDeviceSummary(Context context) {
        return "App: File Manager Pro v" + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")\n"
                + "Android: " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")\n"
                + "Device: " + Build.MANUFACTURER + " " + Build.MODEL;
    }
}
