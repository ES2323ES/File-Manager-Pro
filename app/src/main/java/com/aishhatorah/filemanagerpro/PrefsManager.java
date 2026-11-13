package com.aishhatorah.filemanagerpro;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PrefsManager {
    private static final String PREFS_NAME = "file_manager_pro_prefs";
    public static final String FIXED_GITHUB_REPO = "ES2323ES/File-Manager-Pro";

    // סעיף 8: ערכות נושא מורחבות ומקצועיות
    public static final int THEME_LIGHT = 0;
    public static final int THEME_DARK = 1;
    public static final int THEME_NEON = 2;
    public static final int THEME_AMOLED = 3;
    public static final int THEME_OCEAN = 4;
    public static final int THEME_FOREST = 5;
    public static final int THEME_SYSTEM = 6;

    // סעיף 5: אפשרויות מיון כולל לפי יצירה
    public static final int SORT_NAME = 0;
    public static final int SORT_SIZE = 1;
    public static final int SORT_DATE = 2;
    public static final int SORT_TYPE = 3;
    public static final int SORT_CREATED = 4;

    public static final String[] DEFAULT_HOME_CATEGORIES = new String[]{
            "internal", "sd_card", "downloads", "images", "audio", "videos",
            "documents", "apps", "recent", "recycle_bin", "audio_cutter", "video_cutter"
    };

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static int getThemeMode(Context context) {
        return prefs(context).getInt("theme_mode", THEME_LIGHT);
    }

    public static void setThemeMode(Context context, int mode) {
        prefs(context).edit().putInt("theme_mode", mode).apply();
    }

    public static int getCustomAccentColor(Context context) {
        return prefs(context).getInt("custom_accent_color", 0);
    }

    public static void setCustomAccentColor(Context context, int argbColor) {
        prefs(context).edit().putInt("custom_accent_color", argbColor).apply();
    }

    // סעיף 6: גודל תצוגה באפליקציה
    public static float getUiScale(Context context) {
        float val = prefs(context).getFloat("ui_scale_factor", 1.0f);
        return Math.max(0.82f, Math.min(1.32f, val));
    }

    public static void setUiScale(Context context, float scale) {
        float clamped = Math.max(0.82f, Math.min(1.32f, scale));
        prefs(context).edit().putFloat("ui_scale_factor", clamped).apply();
    }

    // סעיף 6: בחירת מנוע ניגון/צפייה (אפליקציה מול מערכת)
    public static boolean isUseInternalVideoPlayer(Context context) {
        return prefs(context).getBoolean("engine_internal_video", true);
    }

    public static void setUseInternalVideoPlayer(Context context, boolean internal) {
        prefs(context).edit().putBoolean("engine_internal_video", internal).apply();
    }

    public static boolean isUseInternalImageViewer(Context context) {
        return prefs(context).getBoolean("engine_internal_image", true);
    }

    public static void setUseInternalImageViewer(Context context, boolean internal) {
        prefs(context).edit().putBoolean("engine_internal_image", internal).apply();
    }

    public static boolean isUseInternalAudioPlayer(Context context) {
        return prefs(context).getBoolean("engine_internal_audio", true);
    }

    public static void setUseInternalAudioPlayer(Context context, boolean internal) {
        prefs(context).edit().putBoolean("engine_internal_audio", internal).apply();
    }

    // סעיף 44: מצב מחוות אוטומטי בנגן (כמו סטטוס בוואטסאפ)
    public static boolean isAutoGestureMode(Context context) {
        return prefs(context).getBoolean("viewer_auto_gesture_mode", true);
    }

    public static void setAutoGestureMode(Context context, boolean enabled) {
        prefs(context).edit().putBoolean("viewer_auto_gesture_mode", enabled).apply();
    }

    // סעיף 40: יצירת קבצי גיבוי .bak אוטומטית לפני שמירת שינויים
    public static boolean isCreateBackupOnSave(Context context) {
        return prefs(context).getBoolean("create_bak_on_save", true);
    }

    public static void setCreateBackupOnSave(Context context, boolean enabled) {
        prefs(context).edit().putBoolean("create_bak_on_save", enabled).apply();
    }

    // סעיף 38: כניסה מהירה למסך הבית האחרון
    public static boolean isQuickAccessEnabled(Context context) {
        return prefs(context).getBoolean("quick_access_enabled", true);
    }

    public static void setQuickAccessEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean("quick_access_enabled", enabled).apply();
    }

    public static String getLastVisitedPath(Context context) {
        return prefs(context).getString("last_visited_path", "");
    }

    public static void setLastVisitedPath(Context context, String path) {
        prefs(context).edit().putString("last_visited_path", path == null ? "" : path).apply();
    }

    public static String getLastVisitedMode(Context context) {
        return prefs(context).getString("last_visited_mode", "");
    }

    public static void setLastVisitedMode(Context context, String mode) {
        prefs(context).edit().putString("last_visited_mode", mode == null ? "" : mode).apply();
    }

    // סעיף 31: חותמת זמן לניקוי "קבצים חדשים"
    public static long getRecentClearedTimestamp(Context context) {
        return prefs(context).getLong("recent_cleared_ts", 0L);
    }

    public static void setRecentClearedTimestamp(Context context, long ts) {
        prefs(context).edit().putLong("recent_cleared_ts", ts).apply();
    }

    // סעיף 32: קביעת אפליקציות ברירת מחדל לסוגי קבצים ("פתיחה באמצעות" -> תמיד)
    public static String getDefaultOpenAction(Context context, String ext) {
        if (ext == null) return "";
        String norm = ext.trim().toLowerCase(Locale.ROOT);
        if (!norm.startsWith(".")) norm = "." + norm;
        return prefs(context).getString("default_open_" + norm, "");
    }

    public static void setDefaultOpenAction(Context context, String ext, String targetAction) {
        if (ext == null) return;
        String norm = ext.trim().toLowerCase(Locale.ROOT);
        if (!norm.startsWith(".")) norm = "." + norm;
        SharedPreferences.Editor ed = prefs(context).edit();
        if (targetAction == null || targetAction.trim().isEmpty()) {
            ed.remove("default_open_" + norm);
        } else {
            ed.putString("default_open_" + norm, targetAction.trim());
        }
        ed.apply();
    }

    public static Map<String, String> getAllDefaultOpenActions(Context context) {
        Map<String, String> result = new HashMap<>();
        Map<String, ?> all = prefs(context).getAll();
        for (Map.Entry<String, ?> e : all.entrySet()) {
            if (e.getKey().startsWith("default_open_") && e.getValue() instanceof String) {
                result.put(e.getKey().substring("default_open_".length()), (String) e.getValue());
            }
        }
        return result;
    }

    public static void clearAllDefaultOpenActions(Context context) {
        SharedPreferences sp = prefs(context);
        SharedPreferences.Editor ed = sp.edit();
        for (String key : sp.getAll().keySet()) {
            if (key.startsWith("default_open_")) {
                ed.remove(key);
            }
        }
        ed.apply();
    }

    // סעיף 33: סידור והסתרת קטגוריות במסך הבית
    public static List<String> getHomeCategoryOrder(Context context) {
        String raw = prefs(context).getString("home_category_order", "");
        List<String> list = new ArrayList<>();
        if (!raw.isEmpty()) {
            for (String s : raw.split(",")) {
                String t = s.trim();
                if (!t.isEmpty() && !list.contains(t)) {
                    list.add(t);
                }
            }
        }
        for (String def : DEFAULT_HOME_CATEGORIES) {
            if (!list.contains(def)) {
                list.add(def);
            }
        }
        return list;
    }

    public static void setHomeCategoryOrder(Context context, List<String> order) {
        if (order == null || order.isEmpty()) {
            prefs(context).edit().remove("home_category_order").apply();
            return;
        }
        prefs(context).edit().putString("home_category_order", TextUtils.join(",", order)).apply();
    }

    public static int getHomeVisibleLimit(Context context) {
        return prefs(context).getInt("home_visible_limit", -1);
    }

    public static void setHomeVisibleLimit(Context context, int limit) {
        prefs(context).edit().putInt("home_visible_limit", limit).apply();
    }

    // סעיף 26: תצוגה ליניארית (כל הקבצים) או מחולקת לפי תיקיות
    public static boolean isCategoryLinearView(Context context, String mode) {
        String key = "linear_view_" + (mode == null ? "" : mode);
        return prefs(context).getBoolean(key, false);
    }

    public static void setCategoryLinearView(Context context, String mode, boolean linear) {
        String key = "linear_view_" + (mode == null ? "" : mode);
        prefs(context).edit().putBoolean(key, linear).apply();
    }

    // עורך טקסט וקוד (סעיפים 11, 23)
    public static boolean isTextWrapEnabled(Context context) {
        return prefs(context).getBoolean("text_editor_wrap", true);
    }

    public static void setTextWrapEnabled(Context context, boolean wrap) {
        prefs(context).edit().putBoolean("text_editor_wrap", wrap).apply();
    }

    public static float getTextFontSizeSp(Context context) {
        return prefs(context).getFloat("text_editor_font_sp", 14.5f);
    }

    public static void setTextFontSizeSp(Context context, float sp) {
        prefs(context).edit().putFloat("text_editor_font_sp", Math.max(9f, Math.min(32f, sp))).apply();
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

    private static String gridKeyForMode(String mode) {
        return "grid_layout_mode_" + (mode == null ? FileBrowserActivity.MODE_INTERNAL : mode);
    }

    public static boolean isGridLayoutForMode(Context context, String mode) {
        SharedPreferences sp = prefs(context);
        String key = gridKeyForMode(mode);
        if (sp.contains(key)) {
            return sp.getBoolean(key, false);
        }
        if (FileBrowserActivity.MODE_IMAGES.equals(mode) || FileBrowserActivity.MODE_VIDEOS.equals(mode)) {
            return true;
        }
        return isGridLayout(context);
    }

    public static void setGridLayoutForMode(Context context, String mode, boolean grid) {
        prefs(context).edit().putBoolean(gridKeyForMode(mode), grid).apply();
    }

    public static void setGeneralGridLayoutAndSyncNonMedia(Context context, boolean grid) {
        SharedPreferences.Editor ed = prefs(context).edit();
        ed.putBoolean("grid_layout", grid);
        String[] generalModes = {
                FileBrowserActivity.MODE_INTERNAL,
                FileBrowserActivity.MODE_SD_CARD,
                FileBrowserActivity.MODE_USB_OTG,
                FileBrowserActivity.MODE_DOWNLOADS,
                FileBrowserActivity.MODE_AUDIO,
                FileBrowserActivity.MODE_DOCUMENTS,
                FileBrowserActivity.MODE_APPS,
                FileBrowserActivity.MODE_RECENT,
                FileBrowserActivity.MODE_RECYCLE_BIN
        };
        for (String m : generalModes) {
            ed.putBoolean(gridKeyForMode(m), grid);
        }
        ed.apply();
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
        return FIXED_GITHUB_REPO;
    }

    public static String getLastKnownAppVersion(Context context) {
        return prefs(context).getString("last_known_app_version", "");
    }

    public static void setLastKnownAppVersion(Context context, String version) {
        prefs(context).edit().putString("last_known_app_version", version).apply();
    }

    public static boolean isPendingUpdateSuccessBanner(Context context) {
        return prefs(context).getBoolean("pending_update_success_banner", false);
    }

    public static void setPendingUpdateSuccessBanner(Context context, boolean pending) {
        prefs(context).edit().putBoolean("pending_update_success_banner", pending).apply();
    }

    public static void resetAll(Context context) {
        String currentKnown = getLastKnownAppVersion(context);
        prefs(context).edit().clear().putString("last_known_app_version", currentKnown).apply();
    }
}
