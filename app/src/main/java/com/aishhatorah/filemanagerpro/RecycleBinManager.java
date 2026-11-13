package com.aishhatorah.filemanagerpro;

import android.content.Context;
import android.os.Environment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * סעיף 39 וכללי ארכיטקטורה:
 * סל המיחזור עובר מתקיית השורש החיצונית (.FileManagerPro_Trash) לאחסון הפרטי של האפליקציה
 * (Android/data/com.aishhatorah.filemanagerpro/files/RecycleBin) עם העברה אוטומטית של קבצים קיימים.
 */
public class RecycleBinManager {

    private static final String LEGACY_TRASH_FOLDER_NAME = ".FileManagerPro_Trash";
    private static final String APP_TRASH_FOLDER_NAME = "RecycleBin";
    private static final String MANIFEST_FILE = "trash_manifest.json";
    private static volatile boolean migrated = false;

    public static class TrashEntry {
        public final String trashFileName;
        public final String originalPath;
        public final long deletedAt;

        public TrashEntry(String trashFileName, String originalPath, long deletedAt) {
            this.trashFileName = trashFileName;
            this.originalPath = originalPath;
            this.deletedAt = deletedAt;
        }
    }

    public static File getTrashDir(Context context) {
        File extFiles = context.getExternalFilesDir(null);
        File dir = extFiles != null
                ? new File(extFiles, APP_TRASH_FOLDER_NAME)
                : new File(context.getFilesDir(), APP_TRASH_FOLDER_NAME);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        ensureNoMedia(dir);
        return dir;
    }

    private static void ensureNoMedia(File dir) {
        try {
            File nm = new File(dir, ".nomedia");
            if (!nm.exists()) nm.createNewFile();
        } catch (Exception ignored) {
        }
    }

    public static void migrateLegacyTrashIfNeeded(Context context) {
        if (migrated || context == null) return;
        migrated = true;
        try {
            File legacy = new File(Environment.getExternalStorageDirectory(), LEGACY_TRASH_FOLDER_NAME);
            if (!legacy.exists() || !legacy.isDirectory()) return;
            File newDir = getTrashDir(context);
            File[] children = legacy.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (".nomedia".equals(child.getName())) {
                        child.delete();
                        continue;
                    }
                    if (MANIFEST_FILE.equals(child.getName())) {
                        mergeManifest(context, child, newDir);
                        child.delete();
                        continue;
                    }
                    File target = StorageUtils.uniqueDestinationFile(newDir, child.getName());
                    if (!child.renameTo(target)) {
                        try {
                            if (StorageUtils.copyFileOrDir(child, newDir)) {
                                StorageUtils.deleteRecursively(child);
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
            File[] rem = legacy.listFiles();
            if (rem == null || rem.length == 0) {
                legacy.delete();
            }
        } catch (Throwable ignored) {
        }
    }

    private static void mergeManifest(Context context, File legacyManifest, File newDir) {
        try {
            List<TrashEntry> existing = readEntries(context);
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(new FileInputStream(legacyManifest), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                JSONArray arr = new JSONArray(sb.toString());
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    existing.add(new TrashEntry(
                            o.optString("trashFileName"),
                            o.optString("originalPath"),
                            o.optLong("deletedAt")
                    ));
                }
            }
            saveEntries(newDir, existing);
        } catch (Exception ignored) {
        }
    }

    public static boolean moveToTrash(Context context, File target) {
        try {
            if (target == null || !target.exists()) return false;
            File trashDir = getTrashDir(context);
            String originalPath = target.getAbsolutePath();
            String safeName = System.currentTimeMillis() + "_" + target.getName();
            File dest = new File(trashDir, safeName);

            boolean moved = target.renameTo(dest);
            if (!moved) {
                if (StorageUtils.copyFileOrDir(target, trashDir)) {
                    File copied = new File(trashDir, target.getName());
                    if (copied.exists() && !copied.equals(dest)) {
                        copied.renameTo(dest);
                    }
                    StorageUtils.deleteRecursively(target);
                    moved = true;
                }
            }

            if (moved) {
                List<TrashEntry> entries = readEntries(context);
                entries.add(new TrashEntry(dest.getName(), originalPath, System.currentTimeMillis()));
                saveEntries(trashDir, entries);
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public static boolean restoreFromTrash(Context context, File trashFile) {
        try {
            List<TrashEntry> entries = readEntries(context);
            TrashEntry match = null;
            for (TrashEntry e : entries) {
                if (e.trashFileName.equals(trashFile.getName())) {
                    match = e;
                    break;
                }
            }

            File destFile;
            if (match != null) {
                File orig = new File(match.originalPath);
                File parent = orig.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                destFile = StorageUtils.uniqueDestinationFile(
                        parent != null ? parent : Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        orig.getName()
                );
            } else {
                String cleanName = trashFile.getName();
                int idx = cleanName.indexOf('_');
                if (idx > 0 && idx + 1 < cleanName.length()) {
                    cleanName = cleanName.substring(idx + 1);
                }
                File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                destFile = StorageUtils.uniqueDestinationFile(downloads, cleanName);
            }

            boolean ok = trashFile.renameTo(destFile);
            if (!ok && destFile.getParentFile() != null) {
                if (StorageUtils.copyFileOrDir(trashFile, destFile.getParentFile())) {
                    File copied = new File(destFile.getParentFile(), trashFile.getName());
                    if (copied.exists() && !copied.equals(destFile)) {
                        copied.renameTo(destFile);
                    }
                    StorageUtils.deleteRecursively(trashFile);
                    ok = true;
                }
            }

            if (ok && match != null) {
                entries.remove(match);
                saveEntries(getTrashDir(context), entries);
            }
            return ok;
        } catch (Exception e) {
            return false;
        }
    }

    public static int emptyTrash(Context context) {
        File trashDir = getTrashDir(context);
        File[] files = trashDir.listFiles();
        int count = 0;
        if (files != null) {
            for (File f : files) {
                if (MANIFEST_FILE.equals(f.getName()) || ".nomedia".equals(f.getName())) continue;
                if (StorageUtils.deleteRecursively(f)) count++;
            }
        }
        saveEntries(trashDir, new ArrayList<>());
        return count;
    }

    public static int getTrashItemCount(Context context) {
        File trashDir = getTrashDir(context);
        File[] files = trashDir.listFiles();
        if (files == null) return 0;
        int c = 0;
        for (File f : files) {
            String n = f.getName();
            if (!MANIFEST_FILE.equals(n) && !".nomedia".equals(n)) c++;
        }
        return c;
    }

    public static long getTrashTotalBytes(Context context) {
        File trashDir = getTrashDir(context);
        File[] files = trashDir.listFiles();
        if (files == null) return 0L;
        long total = 0L;
        for (File f : files) {
            String n = f.getName();
            if (MANIFEST_FILE.equals(n) || ".nomedia".equals(n)) continue;
            total += computeFileOrDirBytes(f);
        }
        return total;
    }

    private static long computeFileOrDirBytes(File f) {
        if (f == null || !f.exists()) return 0L;
        if (!f.isDirectory()) return f.length();
        long sum = 0L;
        File[] children = f.listFiles();
        if (children != null) {
            for (File c : children) sum += computeFileOrDirBytes(c);
        }
        return sum;
    }

    private static List<TrashEntry> readEntries(Context context) {
        List<TrashEntry> list = new ArrayList<>();
        File mf = new File(getTrashDir(context), MANIFEST_FILE);
        if (!mf.exists()) return list;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(mf), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            JSONArray arr = new JSONArray(sb.toString());
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                list.add(new TrashEntry(
                        o.optString("trashFileName"),
                        o.optString("originalPath"),
                        o.optLong("deletedAt")
                ));
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    private static void saveEntries(File trashDir, List<TrashEntry> list) {
        File mf = new File(trashDir, MANIFEST_FILE);
        try {
            JSONArray arr = new JSONArray();
            for (TrashEntry e : list) {
                JSONObject o = new JSONObject();
                o.put("trashFileName", e.trashFileName);
                o.put("originalPath", e.originalPath);
                o.put("deletedAt", e.deletedAt);
                arr.put(o);
            }
            try (FileOutputStream fos = new FileOutputStream(mf)) {
                fos.write(arr.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
        }
    }
}
