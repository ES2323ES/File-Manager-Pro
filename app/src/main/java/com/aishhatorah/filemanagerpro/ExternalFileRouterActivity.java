package com.aishhatorah.filemanagerpro;

import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * נתב פתיחת קבצים מאפליקציות חיצוניות (ACTION_VIEW):
 * מזהה נתיב ישיר או מזרים תוכן מ-content:// עם חלונית התקדמות והתראה,
 * ומפנה למציג/עורך/מתקין המובנה של File Manager Pro.
 */
public class ExternalFileRouterActivity extends AppCompatActivity {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handleIncomingIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) {
            finish();
            return;
        }
        final Uri uri = intent.getData();
        if (uri == null) {
            finish();
            return;
        }
        final String mimeHint = intent.getType() != null
                ? intent.getType()
                : getContentResolver().getType(uri);

        // 1. file:// URI ישיר
        if ("file".equalsIgnoreCase(uri.getScheme()) && uri.getPath() != null) {
            File direct = new File(uri.getPath());
            if (direct.exists() && direct.isFile() && direct.canRead()) {
                routeResolvedFile(direct, mimeHint);
                return;
            }
        }

        // 2. נסיון לאתר נתיב ישיר ב-MediaStore (ללא העתקה מיותרת)
        File directMedia = tryResolveMediaStoreFile(uri);
        if (directMedia != null && directMedia.exists() && directMedia.isFile() && directMedia.canRead()) {
            routeResolvedFile(directMedia, mimeHint);
            return;
        }

        // 3. הזרמת content:// URI לתיקיית cache מבודדת עם התקדמות
        final OperationProgressHelper.Handle progress = OperationProgressHelper.show(
                this, "טוען קובץ לפתיחה", resolveDisplayName(uri, mimeHint), true);

        executor.execute(() -> {
            try {
                cleanupOldExternalCache();
                ContentResolver cr = getContentResolver();
                String rawName = resolveDisplayName(uri, mimeHint);
                long expectedSize = queryContentSize(cr, uri);

                File extRoot = new File(getCacheDir(), "external_open");
                File sessionDir = new File(extRoot, String.valueOf(System.currentTimeMillis()));
                if (!sessionDir.exists()) sessionDir.mkdirs();

                File tempOut = new File(sessionDir, sanitizeFileName(rawName));
                byte[] headerPeek = new byte[128];
                int headerRead = 0;

                try (InputStream rawIn = cr.openInputStream(uri)) {
                    if (rawIn == null) {
                        throw new IllegalStateException("לא ניתן לפתוח זרם קריאה מהמקור");
                    }
                    try (BufferedInputStream bis = new BufferedInputStream(rawIn, 128 * 1024);
                         BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(tempOut), 128 * 1024)) {
                        byte[] buf = new byte[128 * 1024];
                        long totalRead = 0L;
                        long lastProgMs = 0L;
                        int r;
                        while ((r = bis.read(buf)) != -1) {
                            if (headerRead == 0) {
                                int copyLen = Math.min(r, headerPeek.length);
                                System.arraycopy(buf, 0, headerPeek, 0, copyLen);
                                headerRead = copyLen;
                            }
                            bos.write(buf, 0, r);
                            totalRead += r;
                            long now = System.currentTimeMillis();
                            if (now - lastProgMs >= 80L) {
                                lastProgMs = now;
                                if (expectedSize > 0) {
                                    int pct = (int) Math.min(99L, (totalRead * 100L) / expectedSize);
                                    progress.updatePercent(rawName, pct,
                                            StorageUtils.formatSize(totalRead) + " / " + StorageUtils.formatSize(expectedSize));
                                } else {
                                    progress.updateIndeterminate(rawName, StorageUtils.formatSize(totalRead));
                                }
                            }
                        }
                    }
                }

                // השלמת סיומת חכמה לפי MIME או כותרת בינארית אם חסרה
                File finalFile = ensureFileExtension(tempOut, mimeHint, headerPeek, headerRead);
                progress.finish();

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    routeResolvedFile(finalFile, mimeHint);
                });
            } catch (Exception e) {
                progress.finish();
                AppLogger.error(this, "ExternalRouter", "Failed opening external URI: " + uri, e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "לא ניתן לפתוח קובץ זה: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        });
    }

    private void routeResolvedFile(File file, String mimeHint) {
        String name = file.getName();
        if (MediaFormatHelper.isInstallableAppPackage(name)
                || (mimeHint != null && mimeHint.toLowerCase(Locale.ROOT).contains("android.package-archive"))) {
            FileOpenWithHelper.openExternalFileDirect(this, file, mimeHint, this::finish);
            return;
        }
        FileOpenWithHelper.openExternalFileDirect(this, file, mimeHint, this::finish);
    }

    private File tryResolveMediaStoreFile(Uri uri) {
        if (!"content".equalsIgnoreCase(uri.getScheme())) return null;
        try (Cursor c = getContentResolver().query(uri, new String[]{MediaStore.MediaColumns.DATA}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(MediaStore.MediaColumns.DATA);
                if (idx >= 0) {
                    String p = c.getString(idx);
                    if (p != null && !p.isEmpty()) {
                        return new File(p);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private String resolveDisplayName(Uri uri, String mimeHint) {
        String name = null;
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) name = c.getString(idx);
            }
        } catch (Throwable ignored) {
        }
        if (name == null || name.trim().isEmpty()) {
            String last = uri.getLastPathSegment();
            if (last != null && !last.trim().isEmpty()) {
                int slash = last.lastIndexOf('/');
                name = slash >= 0 && slash < last.length() - 1 ? last.substring(slash + 1) : last;
            }
        }
        if (name == null || name.trim().isEmpty()) {
            name = "external_file_" + System.currentTimeMillis();
        }
        if (!name.contains(".") && mimeHint != null && !mimeHint.trim().isEmpty()) {
            String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeHint.toLowerCase(Locale.ROOT));
            if ("image/svg+xml".equalsIgnoreCase(mimeHint)) ext = "svg";
            if (ext != null && !ext.isEmpty()) {
                name = name + "." + ext;
            }
        }
        return name;
    }

    private long queryContentSize(ContentResolver cr, Uri uri) {
        try (Cursor c = cr.query(uri, new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.SIZE);
                if (idx >= 0 && !c.isNull(idx)) {
                    return c.getLong(idx);
                }
            }
        } catch (Throwable ignored) {
        }
        return -1L;
    }

    private File ensureFileExtension(File file, String mimeHint, byte[] head, int headLen) {
        String n = file.getName();
        if (n.contains(".")) return file;
        String guessedExt = null;
        if (mimeHint != null && !mimeHint.isEmpty()) {
            if ("image/svg+xml".equalsIgnoreCase(mimeHint)) guessedExt = "svg";
            else guessedExt = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeHint.toLowerCase(Locale.ROOT));
        }
        if (guessedExt == null && headLen >= 4) {
            String ascii = new String(head, 0, headLen, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
            if (ascii.startsWith("%pdf")) guessedExt = "pdf";
            else if (ascii.contains("<svg")) guessedExt = "svg";
            else if ((head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G') guessedExt = "png";
            else if ((head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8) guessedExt = "jpg";
            else if (ascii.startsWith("gif8")) guessedExt = "gif";
            else if (head[0] == 'P' && head[1] == 'K') guessedExt = "zip";
        }
        if (guessedExt != null && !guessedExt.isEmpty()) {
            File renamed = new File(file.getParentFile(), n + "." + guessedExt);
            if (file.renameTo(renamed)) {
                return renamed;
            }
        }
        return file;
    }

    private String sanitizeFileName(String name) {
        String clean = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return clean.isEmpty() ? ("file_" + System.currentTimeMillis()) : clean;
    }

    private void cleanupOldExternalCache() {
        CacheCleanupManager.cleanupOldExternalOpenSessions(this, 20L * 60L * 1000L);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
