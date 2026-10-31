package com.aishhatorah.filemanagerpro;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.view.Window;

public class ThemeHelper {

    public static int getBgColor(int theme) {
        if (theme == PrefsManager.THEME_DARK) return Color.parseColor("#0F172A");
        if (theme == PrefsManager.THEME_NEON) return Color.parseColor("#050814");
        return Color.parseColor("#F8FAFC");
    }

    public static int getCardColor(int theme) {
        if (theme == PrefsManager.THEME_DARK) return Color.parseColor("#1E293B");
        if (theme == PrefsManager.THEME_NEON) return Color.parseColor("#0B132B");
        return Color.parseColor("#FFFFFF");
    }

    public static int getPrimaryTextColor(int theme) {
        if (theme == PrefsManager.THEME_DARK) return Color.parseColor("#F1F5F9");
        if (theme == PrefsManager.THEME_NEON) return Color.parseColor("#E0FBFC");
        return Color.parseColor("#0F172A");
    }

    public static int getSecondaryTextColor(int theme) {
        if (theme == PrefsManager.THEME_DARK) return Color.parseColor("#94A3B8");
        if (theme == PrefsManager.THEME_NEON) return Color.parseColor("#38BDF8");
        return Color.parseColor("#64748B");
    }

    public static int getAccentColor(int theme) {
        if (theme == PrefsManager.THEME_DARK) return Color.parseColor("#3B82F6");
        if (theme == PrefsManager.THEME_NEON) return Color.parseColor("#00F0FF");
        return Color.parseColor("#1D4ED8");
    }

    public static int getTopBarColor(int theme) {
        if (theme == PrefsManager.THEME_DARK) return Color.parseColor("#162033");
        if (theme == PrefsManager.THEME_NEON) return Color.parseColor("#070D24");
        return Color.parseColor("#FFFFFF");
    }

    public static void applyWindowStatusBar(Activity activity, int theme) {
        Window window = activity.getWindow();
        if (window == null) return;
        if (theme == PrefsManager.THEME_DARK) {
            window.setStatusBarColor(Color.parseColor("#0B1120"));
        } else if (theme == PrefsManager.THEME_NEON) {
            window.setStatusBarColor(Color.parseColor("#03050C"));
        } else {
            window.setStatusBarColor(Color.parseColor("#1D4ED8"));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.setNavigationBarColor(getBgColor(theme));
        }
    }

    public static String getThemeNameHe(int theme) {
        if (theme == PrefsManager.THEME_DARK) return "כהה";
        if (theme == PrefsManager.THEME_NEON) return "ניאון";
        return "בהיר";
    }
}
