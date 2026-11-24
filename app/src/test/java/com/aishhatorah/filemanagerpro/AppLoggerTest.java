package com.aishhatorah.filemanagerpro;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static org.junit.Assert.*;

public class AppLoggerTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void sanitizeForExportStripsPii() {
        String raw = "User user.name@example.com failed at /storage/emulated/0/Private/secret.txt "
                + "from 192.168.1.45 using ghp_SecretToken1234567890 phone 0501234567";
        String clean = AppLogger.sanitizeForExport(raw);
        assertFalse(clean.contains("example.com"));
        assertFalse(clean.contains("/storage/emulated/0"));
        assertFalse(clean.contains("192.168.1.45"));
        assertFalse(clean.contains("ghp_SecretToken1234567890"));
        assertFalse(clean.contains("0501234567"));
        assertTrue(clean.contains("[email]"));
        assertTrue(clean.contains("[storage-path]"));
        assertTrue(clean.contains("[ip]"));
        assertTrue(clean.contains("[token]"));
    }

    @Test
    public void cleanupOldLogsDeletesOnlyExpiredFiles() throws Exception {
        File logsDir = tmp.newFolder("logs");
        File oldLog = new File(logsDir, "log_2024-01-01.jsonl");
        File freshLog = new File(logsDir, "log_2026-06-01.jsonl");
        assertTrue(oldLog.createNewFile());
        assertTrue(freshLog.createNewFile());

        long now = System.currentTimeMillis();
        assertTrue(oldLog.setLastModified(now - (20L * 24L * 3600L * 1000L)));
        assertTrue(freshLog.setLastModified(now - (2L * 24L * 3600L * 1000L)));

        AppLogger.cleanupOldLogs(logsDir, now);
        assertFalse(oldLog.exists());
        assertTrue(freshLog.exists());
    }

    @Test
    public void formatThrowableCompactIncludesCauseAndRespectsLimit() {
        IllegalStateException root = new IllegalStateException("root failure reason");
        RuntimeException outer = new RuntimeException("outer wrapper", root);
        StackTraceElement[] deepOuter = new StackTraceElement[80];
        for (int i = 0; i < deepOuter.length; i++) {
            deepOuter[i] = new StackTraceElement(
                    "com.aishhatorah.filemanagerpro.DeepRunnerFrame" + i,
                    "executeStepWithVeryLongDiagnosticMethodName_" + i,
                    "DeepRunnerFrame.java",
                    100 + i
            );
        }
        outer.setStackTrace(deepOuter);

        String full = AppLogger.formatThrowableCompact(outer, 4096);
        assertTrue(full.contains("java.lang.RuntimeException: outer wrapper"));
        assertTrue(full.contains("Caused by: java.lang.IllegalStateException: root failure reason"));

        StringBuilder longMsg = new StringBuilder("huge header ");
        for (int i = 0; i < 40; i++) {
            longMsg.append("segment_").append(i).append("_");
        }
        RuntimeException oversized = new RuntimeException(longMsg.toString(), root);
        String truncated = AppLogger.formatThrowableCompact(oversized, 256);
        assertTrue(truncated.length() <= 256);
        assertTrue(truncated.endsWith("..."));
    }

    @Test
    public void appendToDirWritesEscapedJsonLineToDisk() throws Exception {
        File logsDir = tmp.newFolder("crash_logs");
        long now = 1770000000000L;
        AppLogger.appendToDir(logsDir, AppLogger.LEVEL_ERROR, "Crash", "Line1\n\"quoted\"\\slash", now);

        File[] files = logsDir.listFiles();
        assertNotNull(files);
        assertEquals(1, files.length);
        String content = new String(java.nio.file.Files.readAllBytes(files[0].toPath()), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(content.contains("\"level\":\"ERROR\""));
        assertTrue(content.contains("\"tag\":\"Crash\""));
        assertTrue(content.contains("Line1\\n\\\"quoted\\\"\\\\slash"));
    }
}
