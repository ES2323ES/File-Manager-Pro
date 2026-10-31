package com.aishhatorah.filemanagerpro;

import android.content.Context;
import android.content.SharedPreferences;

public class PrefsManager {
    private static final String PREFS_NAME = "file_manager_pro_prefs";

    public static final int THEME_LIGHT = 0;
    public static final int THEME_DARK = 1;
    public static final int THEME_NEON = 2;

    public static final int SORT_NAME = 0;
    public static final int SORT_SIZE = 1;
    public static final int SORT_DATE = 2;
    public static final int SORT_TYPE = 3;

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static int getThemeMode(Context context) {
        return prefs(context).getInt("theme_mode", THEME_LIGHT);
    }

    public static void setThemeMode(Context context, int mode) {
        prefs(context).edit().putInt("theme_mode", mode).apply();
    }

    public static boolean isShowHidden(Context context) {
        return prefs(context).getBoolean("show_hidden", false);
    }

    public static void setShowHidden(Context context, boolean show) {
        prefs(context).edit().putBoolean("show_hidden", show).apply();
    }

    public static boolean isGridLayout(Context context) {
        return prefs(context).getBoolean("grid_layout", false);
    }

    public static void setGridLayout(Context context, boolean grid) {
        prefs(context).edit().putBoolean("grid_layout", grid).apply();
    }

    public static int getSortMode(Context context) {
        return prefs(context).getInt("sort_mode", SORT_NAME);
    }

    public static void setSortMode(Context context, int mode) {
        prefs(context).edit().putInt("sort_mode", mode).apply();
    }

    public static boolean isSortAscending(Context context) {
        return prefs(context).getBoolean("sort_asc", true);
    }

    public static void setSortAscending(Context context, boolean asc) {
        prefs(context).edit().putBoolean("sort_asc", asc).apply();
    }

    public static boolean isUseRecycleBin(Context context) {
        return prefs(context).getBoolean("use_recycle_bin", true);
    }

    public static void setUseRecycleBin(Context context, boolean use) {
        prefs(context).edit().putBoolean("use_recycle_bin", use).apply();
    }

    public static boolean isConfirmDelete(Context context) {
        return prefs(context).getBoolean("confirm_delete", true);
    }

    public static void setConfirmDelete(Context context, boolean confirm) {
        prefs(context).edit().putBoolean("confirm_delete", confirm).apply();
    }

    public static boolean isShowThumbnails(Context context) {
        return prefs(context).getBoolean("show_thumbnails", true);
    }

    public static void setShowThumbnails(Context context, boolean show) {
        prefs(context).edit().putBoolean("show_thumbnails", show).apply();
    }

    public static boolean isShowFolderSizes(Context context) {
        return prefs(context).getBoolean("show_folder_sizes", false);
    }

    public static void setShowFolderSizes(Context context, boolean show) {
        prefs(context).edit().putBoolean("show_folder_sizes", show).apply();
    }

    public static boolean isAlert96Enabled(Context context) {
        return prefs(context).getBoolean("alert_96_enabled", true);
    }

    public static void setAlert96Enabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean("alert_96_enabled", enabled).apply();
    }

    public static boolean isAutoCheckUpdates(Context context) {
        return prefs(context).getBoolean("auto_check_updates", true);
    }

    public static void setAutoCheckUpdates(Context context, boolean auto) {
        prefs(context).edit().putBoolean("auto_check_updates", auto).apply();
    }

    public static String getGithubRepo(Context context) {
        return prefs(context).getString("github_repo", "ES2323ES/File-Manager-Pro");
    }

    public static void setGithubRepo(Context context, String repo) {
        prefs(context).edit().putString("github_repo", repo).apply();
    }

    public static void resetAll(Context context) {
        prefs(context).edit().clear().apply();
    }
}
