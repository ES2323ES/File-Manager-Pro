package com.aishhatorah.filemanagerpro;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.os.Environment;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * אינדקס קבצים ותיקיות מקומי מבוסס SQLite (WAL) לחיפוש מיידי בכל האחסון:
 * כולל קבצים ותיקיות באחסון הראשי, כרטיס SD, כונני USB OTG ותיקיית ההורדות.
 */
public final class FileIndexDatabase extends SQLiteOpenHelper {

    private static final String DB_NAME = "file_index.db";
    private static final int DB_VERSION = 1;
    private static final int MAX_CRAWL_DEPTH = 24;
    private static final int BATCH_SIZE = 400;

    private static volatile FileIndexDatabase instance;
    private volatile boolean rebuildInProgress = false;

    private FileIndexDatabase(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
        setWriteAheadLoggingEnabled(true);
    }

    public static FileIndexDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (FileIndexDatabase.class) {
                if (instance == null) {
                    instance = new FileIndexDatabase(context);
                }
            }
        }
        return instance;
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        try {
            db.enableWriteAheadLogging();
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS entries ("
                + "path TEXT PRIMARY KEY,"
                + "parent_path TEXT,"
                + "name TEXT NOT NULL,"
                + "name_norm TEXT NOT NULL,"
                + "is_dir INTEGER NOT NULL,"
                + "is_hidden INTEGER NOT NULL DEFAULT 0,"
                + "size_bytes INTEGER NOT NULL DEFAULT 0,"
                + "modified_ms INTEGER NOT NULL DEFAULT 0,"
                + "volume_root TEXT"
                + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_entries_name_norm ON entries(name_norm)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_entries_parent ON entries(parent_path)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_entries_dir ON entries(is_dir)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS entries");
        onCreate(db);
    }

    public static String normalize(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("[\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]", "")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    public static String[] tokenize(String normalizedQuery) {
        if (normalizedQuery == null) return new String[0];
        String trimmed = normalize(normalizedQuery);
        if (trimmed.isEmpty()) return new String[0];
        String[] parts = trimmed.split("\\s+");
        List<String> clean = new ArrayList<>(parts.length);
        for (String p : parts) {
            if (!p.isEmpty()) clean.add(p);
        }
        return clean.toArray(new String[0]);
    }

    public static boolean matchesAllTokens(String candidateNameNorm, String[] tokens) {
        if (candidateNameNorm == null || tokens == null || tokens.length == 0) return false;
        for (String tok : tokens) {
            if (tok != null && !tok.isEmpty() && !candidateNameNorm.contains(tok)) {
                return false;
            }
        }
        return true;
    }

    public boolean isRebuildInProgress() {
        return rebuildInProgress;
    }

    public boolean hasIndexedEntries() {
        try {
            SQLiteDatabase db = getReadableDatabase();
            try (Cursor c = db.rawQuery("SELECT 1 FROM entries LIMIT 1", null)) {
                return c.moveToFirst();
            }
        } catch (Throwable ignored) {
            return false;
        }
    }

    public void upsertFile(File file) {
        if (file == null || !file.exists()) return;
        upsertFiles(Collections.singletonList(file));
    }

    public void upsertFiles(List<File> files) {
        if (files == null || files.isEmpty()) return;
        SQLiteDatabase db = null;
        try {
            db = getWritableDatabase();
            db.beginTransaction();
            try (SQLiteStatement stmt = db.compileStatement(
                    "INSERT OR REPLACE INTO entries "
                            + "(path, parent_path, name, name_norm, is_dir, is_hidden, size_bytes, modified_ms, volume_root) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                for (File f : files) {
                    if (f == null || !f.exists()) continue;
                    String path = f.getAbsolutePath();
                    if (path.contains(".FileManagerPro_Trash")) continue;
                    bindStatementRow(stmt, f, path, false);
                    stmt.executeInsert();
                }
            }
            db.setTransactionSuccessful();
        } catch (Throwable ignored) {
        } finally {
            if (db != null) {
                try {
                    if (db.inTransaction()) db.endTransaction();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    public void upsertTree(File root) {
        if (root == null || !root.exists()) return;
        if (!root.isDirectory()) {
            upsertFile(root);
            return;
        }
        List<File> collected = new ArrayList<>();
        collectTreeForUpsert(root, 0, collected);
        if (!collected.isEmpty()) {
            upsertFiles(collected);
        }
    }

    private static void collectTreeForUpsert(File node, int depth, List<File> out) {
        if (node == null || depth > MAX_CRAWL_DEPTH || !node.exists()) return;
        String name = node.getName();
        if (".FileManagerPro_Trash".equals(name)) return;
        out.add(node);
        if (node.isDirectory()) {
            File[] children = node.listFiles();
            if (children != null) {
                for (File child : children) {
                    collectTreeForUpsert(child, depth + 1, out);
                }
            }
        }
    }

    public void removePath(String pathPrefix) {
        if (pathPrefix == null || pathPrefix.isEmpty()) return;
        try {
            SQLiteDatabase db = getWritableDatabase();
            String prefixSlash = pathPrefix.endsWith("/") ? pathPrefix : (pathPrefix + "/");
            db.delete("entries", "path = ? OR path LIKE ? ESCAPE '\\'",
                    new String[]{pathPrefix, escapeLike(prefixSlash) + "%"});
        } catch (Throwable ignored) {
        }
    }

    public List<File> search(String query, File scopeRoot, boolean showHidden, int limit) {
        String nq = normalize(query);
        String[] tokens = tokenize(nq);
        if (tokens.length == 0) return Collections.emptyList();

        List<File> out = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT path, is_dir FROM entries WHERE 1=1");
        List<String> args = new ArrayList<>();

        if (!showHidden) {
            sql.append(" AND is_hidden = 0");
        }
        if (scopeRoot != null) {
            String rootPath = scopeRoot.getAbsolutePath();
            String rootSlash = rootPath.endsWith("/") ? rootPath : (rootPath + "/");
            sql.append(" AND (path LIKE ? ESCAPE '\\' OR path = ?)");
            args.add(escapeLike(rootSlash) + "%");
            args.add(rootPath);
        }
        for (String tok : tokens) {
            sql.append(" AND name_norm LIKE ? ESCAPE '\\'");
            args.add("%" + escapeLike(tok) + "%");
        }
        sql.append(" ORDER BY is_dir DESC, name_norm ASC LIMIT ?");
        args.add(String.valueOf(Math.max(50, limit)));

        try {
            SQLiteDatabase db = getReadableDatabase();
            try (Cursor c = db.rawQuery(sql.toString(), args.toArray(new String[0]))) {
                while (c.moveToNext()) {
                    String p = c.getString(0);
                    if (p == null) continue;
                    File f = new File(p);
                    if (f.exists()) {
                        out.add(f);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    public synchronized void clearAndRebuildSync(Context context) {
        if (context == null || !StorageUtils.hasAllStoragePermissions(context)) return;
        rebuildInProgress = true;
        SQLiteDatabase db = null;
        try {
            List<File> roots = collectStorageRoots(context);
            if (roots.isEmpty()) return;

            db = getWritableDatabase();
            db.beginTransaction();
            db.delete("entries", null, null);
            db.setTransactionSuccessful();
            db.endTransaction();

            Set<String> visitedCanonicalDirs = new HashSet<>();
            List<PendingEntry> batch = new ArrayList<>(BATCH_SIZE);

            for (File root : roots) {
                crawlDirectory(root, root.getAbsolutePath(), false, 0, visitedCanonicalDirs, batch, db);
            }
            flushBatch(db, batch);
        } catch (Throwable ignored) {
        } finally {
            rebuildInProgress = false;
            if (db != null) {
                try {
                    if (db.inTransaction()) db.endTransaction();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private List<File> collectStorageRoots(Context context) {
        Set<File> roots = new LinkedHashSet<>();
        File internal = Environment.getExternalStorageDirectory();
        if (internal != null && internal.exists() && internal.canRead()) {
            roots.add(internal);
        }
        StorageUtils.StorageStat sd = StorageUtils.getExternalSdCardStat(context);
        if (sd.available && sd.root != null && sd.root.exists() && sd.root.canRead()) {
            roots.add(sd.root);
        }
        for (StorageUtils.StorageStat otg : StorageUtils.getConnectedUsbOtgVolumes(context)) {
            if (otg.root != null && otg.root.exists() && otg.root.canRead()) {
                roots.add(otg.root);
            }
        }
        File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (downloads != null && downloads.exists() && downloads.canRead()) {
            boolean coveredByInternal = internal != null
                    && downloads.getAbsolutePath().startsWith(internal.getAbsolutePath());
            if (!coveredByInternal) {
                roots.add(downloads);
            }
        }
        return new ArrayList<>(roots);
    }

    private void crawlDirectory(
            File dir,
            String volumeRoot,
            boolean ancestorHidden,
            int depth,
            Set<String> visitedCanonicalDirs,
            List<PendingEntry> batch,
            SQLiteDatabase db
    ) {
        if (dir == null || depth > MAX_CRAWL_DEPTH || !dir.exists()) return;
        String dirName = dir.getName();
        if (".FileManagerPro_Trash".equals(dirName)) return;

        String absPath = dir.getAbsolutePath();
        // דילוג מבוקר רק על Android/data ו-Android/obb החסומות מ-API 30; Android/media נסרקת במלואה
        if (absPath.endsWith("/Android/data") || absPath.endsWith("/Android/obb")) return;

        String canonical;
        try {
            canonical = dir.getCanonicalPath();
        } catch (Throwable t) {
            canonical = absPath;
        }
        if (!visitedCanonicalDirs.add(canonical)) return;

        File[] children = dir.listFiles();
        if (children == null) return;

        for (File child : children) {
            String name = child.getName();
            if (".FileManagerPro_Trash".equals(name)) continue;
            boolean isHidden = ancestorHidden || name.startsWith(".");
            boolean isDir = child.isDirectory();
            batch.add(new PendingEntry(child, volumeRoot, isHidden, isDir));
            if (batch.size() >= BATCH_SIZE) {
                flushBatch(db, batch);
            }
            if (isDir) {
                crawlDirectory(child, volumeRoot, isHidden, depth + 1, visitedCanonicalDirs, batch, db);
            }
        }
    }

    private void flushBatch(SQLiteDatabase db, List<PendingEntry> batch) {
        if (batch.isEmpty()) return;
        try {
            db.beginTransaction();
            try (SQLiteStatement stmt = db.compileStatement(
                    "INSERT OR REPLACE INTO entries "
                            + "(path, parent_path, name, name_norm, is_dir, is_hidden, size_bytes, modified_ms, volume_root) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                for (PendingEntry item : batch) {
                    bindStatementRow(stmt, item.file, item.volumeRoot, item.isHidden, item.isDir);
                    stmt.executeInsert();
                }
            }
            db.setTransactionSuccessful();
        } catch (Throwable ignored) {
        } finally {
            batch.clear();
            try {
                if (db.inTransaction()) db.endTransaction();
            } catch (Throwable ignored) {
            }
        }
    }

    private static void bindStatementRow(SQLiteStatement stmt, File f, String path, boolean ancestorHidden) {
        bindStatementRow(stmt, f, "", ancestorHidden || f.getName().startsWith("."), f.isDirectory());
    }

    private static void bindStatementRow(
            SQLiteStatement stmt,
            File f,
            String volumeRoot,
            boolean isHidden,
            boolean isDir
    ) {
        String path = f.getAbsolutePath();
        String parent = f.getParent();
        String name = f.getName();
        stmt.clearBindings();
        stmt.bindString(1, path);
        stmt.bindString(2, parent != null ? parent : "");
        stmt.bindString(3, name);
        stmt.bindString(4, normalize(name));
        stmt.bindLong(5, isDir ? 1L : 0L);
        stmt.bindLong(6, isHidden ? 1L : 0L);
        stmt.bindLong(7, isDir ? 0L : Math.max(0L, f.length()));
        stmt.bindLong(8, Math.max(0L, f.lastModified()));
        stmt.bindString(9, volumeRoot != null ? volumeRoot : "");
    }

    private static String escapeLike(String raw) {
        return raw.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private static final class PendingEntry {
        final File file;
        final String volumeRoot;
        final boolean isHidden;
        final boolean isDir;

        PendingEntry(File file, String volumeRoot, boolean isHidden, boolean isDir) {
            this.file = file;
            this.volumeRoot = volumeRoot;
            this.isHidden = isHidden;
            this.isDir = isDir;
        }
    }
}
