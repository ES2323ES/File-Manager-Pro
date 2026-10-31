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

public class RecycleBinManager {

    private static final String TRASH_FOLDER_NAME = ".FileManagerPro_Trash";
    private static final String MANIFEST_FILE = "trash_manifest.json";

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
        File ext = Environment.getExternalStorageDirectory();
        File dir = new File(ext, TRASH_FOLDER_NAME);
        if (!dir.exists() && !dir.mkdirs()) {
            dir = new File(context.getFilesDir(), TRASH_FOLDER_NAME);
            dir.mkdirs();
        }
        return dir;
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
                    if (copied.exists()) {
                        copied.renameTo(dest);
                    }
                    StorageUtils.deleteRecursively(target);
                    moved = true;
                }
            }

            if (moved) {
                List<TrashEntry> entries = readEntries(context);
                entries.add(new TrashEntry(dest.getName(), originalPath, System.currentTimeMillis()));
                saveEntries(context, entries);
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
                    StorageUtils.deleteRecursively(trashFile);
                    ok = true;
                }
            }

            if (ok && match != null) {
                entries.remove(match);
                saveEntries(context, entries);
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
                if (MANIFEST_FILE.equals(f.getName())) continue;
                if (StorageUtils.deleteRecursively(f)) count++;
            }
        }
        saveEntries(context, new ArrayList<>());
        return count;
    }

    public static int getTrashItemCount(Context context) {
        File trashDir = getTrashDir(context);
        File[] files = trashDir.listFiles();
        if (files == null) return 0;
        int c = 0;
        for (File f : files) {
            if (!MANIFEST_FILE.equals(f.getName())) c++;
        }
        return c;
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

    private static void saveEntries(Context context, List<TrashEntry> list) {
        File mf = new File(getTrashDir(context), MANIFEST_FILE);
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
