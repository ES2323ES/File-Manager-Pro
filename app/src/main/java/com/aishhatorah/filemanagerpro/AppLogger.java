package com.aishhatorah.filemanagerpro;

import android.content.Context;
import android.os.Build;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
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
    private static final int MAX_CRASH_TRACE_CHARS = 12_000;
    private static final Object FILE_LOCK = new Object();
    private static volatile File cachedLogsDir;

    private static final class CrashUncaughtHandler implements Thread.UncaughtExceptionHandler {
        private final Context appCtx;
        private final Thread.UncaughtExceptionHandler prev;

        CrashUncaughtHandler(Context appCtx, Thread.UncaughtExceptionHandler prev) {
            this.appCtx = appCtx;
            this.prev = prev;
        }

        @Override
        public void uncaughtException(Thread thread, Throwable throwable) {
            try {
                String threadName = thread != null ? thread.getName() : "unknown";
                String trace = formatThrowableCompact(throwable, MAX_CRASH_TRACE_CHARS);
                File dir = cachedLogsDir != null ? cachedLogsDir : getLogsDir(appCtx);
                appendToDir(dir, LEVEL_ERROR, "Crash", "Uncaught in " + threadName + ": " + trace, System.currentTimeMillis());
            } catch (Throwable ignored) {
            }
            if (prev != null && prev != this) {
                prev.uncaughtException(thread, throwable);
            }
        }
    }

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
        File dir = getLogsDir(context);
        cachedLogsDir = dir;
        cleanupOldLogs(dir, System.currentTimeMillis());
    }

    public static File getLogsDir(Context context) {
        File dir = new File(context.getFilesDir(), "logs");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        cachedLogsDir = dir;
        return dir;
    }

    public static void installUncaughtHandler(Context context) {
        if (context == null) return;
        Context appCtx = context.getApplicationContext();
        if (cachedLogsDir == null) {
            getLogsDir(appCtx);
        }
        Thread.UncaughtExceptionHandler current = Thread.getDefaultUncaughtExceptionHandler();
        if (current instanceof CrashUncaughtHandler) {
            return;
        }
        Thread.setDefaultUncaughtExceptionHandler(new CrashUncaughtHandler(appCtx, current));
    }

    public static String formatThrowableCompact(Throwable err, int maxChars) {
        if (err == null) return "Unknown error";
        int limit = Math.max(256, maxChars);
        StringBuilder sb = new StringBuilder(Math.min(limit, 2048));
        Throwable cur = err;
        int depth = 0;
        boolean cutByLimit = false;
        while (cur != null && depth < 6 && sb.length() < limit - 32) {
            if (depth > 0) {
                sb.append("\nCaused by: ");
            }
            sb.append(cur.getClass().getName());
            String msg = cur.getMessage();
            if (msg != null && !msg.isEmpty()) {
                sb.append(": ").append(msg);
            }
            StackTraceElement[] trace = cur.getStackTrace();
            boolean hasNextCause = cur.getCause() != null && depth < 5;
            int reserveForCause = hasNextCause ? Math.min(limit / 3, 1024) : 48;
            int frameStopAt = Math.max(sb.length(), limit - reserveForCause);
            if (trace != null && sb.length() < frameStopAt) {
                int maxFrames = Math.min(trace.length, depth == 0 ? 40 : 18);
                int shown = 0;
                for (int i = 0; i < maxFrames && sb.length() < frameStopAt; i++) {
                    sb.append("\n  at ").append(trace[i].toString());
                    shown++;
                }
                if (shown < trace.length) {
                    if (sb.length() < limit - 24) {
                        sb.append("\n  ... ").append(trace.length - shown).append(" more");
                    } else {
                        cutByLimit = true;
                    }
                }
            } else if (trace != null && trace.length > 0 && hasNextCause && sb.length() < limit - 16) {
                sb.append("\n  ... ").append(trace.length).append(" frames omitted");
                cutByLimit = true;
            }
            cur = cur.getCause();
            depth++;
        }
        if (cur != null) {
            cutByLimit = true;
        }
        if (sb.length() > limit) {
            return sb.substring(0, limit - 3) + "...";
        }
        if (cutByLimit && !sb.toString().endsWith("...")) {
            int keep = Math.min(sb.length(), limit - 3);
            return sb.substring(0, keep) + "...";
        }
        return sb.toString();
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
            detail = detail + (detail.isEmpty() ? "" : " | ") + formatThrowableCompact(err, MAX_CRASH_TRACE_CHARS);
        }
        append(context, LEVEL_ERROR, tag, detail);
    }

    private static void append(Context context, String level, String tag, String message) {
        if (context == null) return;
        installUncaughtHandler(context);
        appendToDir(getLogsDir(context), level, tag, message, System.currentTimeMillis());
    }

    public static void appendToDir(File logsDir, String level, String tag, String message, long nowMs) {
        if (logsDir == null) return;
        if (!logsDir.exists()) logsDir.mkdirs();
        String dayName = "log_" + new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(nowMs)) + ".jsonl";
        File target = new File(logsDir, dayName);
        String timeStr = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date(nowMs));
        String safeLevel = level == null ? LEVEL_INFO : level;
        String safeTag = tag == null ? "App" : tag;
        String safeMsg = message == null ? "" : message;
        synchronized (FILE_LOCK) {
            String line;
            try {
                JSONObject obj = new JSONObject();
                obj.put("ts", nowMs);
                obj.put("time", timeStr);
                obj.put("level", safeLevel);
                obj.put("tag", safeTag);
                obj.put("msg", safeMsg);
                String encoded = obj.toString();
                if (encoded == null || !encoded.startsWith("{")) {
                    throw new IllegalStateException("JSON serialization unavailable");
                }
                line = encoded + "\n";
            } catch (Throwable jsonErr) {
                line = "{\"ts\":" + nowMs
                        + ",\"time\":\"" + escapeJsonRaw(timeStr)
                        + "\",\"level\":\"" + escapeJsonRaw(safeLevel)
                        + "\",\"tag\":\"" + escapeJsonRaw(safeTag)
                        + "\",\"msg\":\"" + escapeJsonRaw(safeMsg) + "\"}\n";
            }
            try (FileOutputStream fos = new FileOutputStream(target, true)) {
                fos.write(line.getBytes(StandardCharsets.UTF_8));
                fos.flush();
                try {
                    fos.getFD().sync();
                } catch (Throwable ignored) {
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static String escapeJsonRaw(String s) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder out = new StringBuilder(s.length() + 16);
        int cap = Math.min(s.length(), MAX_CRASH_TRACE_CHARS);
        for (int i = 0; i < cap; i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        out.append(' ');
                    } else {
                        out.append(c);
                    }
            }
        }
        return out.toString();
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
