package com.aishhatorah.filemanagerpro;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.Window;

/**
 * סעיפים 7, 8: מערכת ערכות נושא מינימליסטית ומקצועית עם שחור עמוק (AMOLED), אוקיינוס, יער,
 * ומערכת דינאמית (Android 12+ Material You / בהיר-כהה אוטומטי), כולל בחירת צבע מרכזי מותאם.
 */
public class ThemeHelper {

    public static int resolveEffectiveTheme(Context context, int theme) {
        if (theme != PrefsManager.THEME_SYSTEM) {
            return theme;
        }
        if (context == null) return PrefsManager.THEME_LIGHT;
        int uiMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return uiMode == Configuration.UI_MODE_NIGHT_YES ? PrefsManager.THEME_DARK : PrefsManager.THEME_LIGHT;
    }

    public static int getBgColor(int theme) {
        return getBgColor(null, theme);
    }

    public static int getBgColor(Context context, int theme) {
        if (theme == PrefsManager.THEME_SYSTEM && context != null) {
            boolean night = resolveEffectiveTheme(context, theme) == PrefsManager.THEME_DARK;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    return context.getColor(night ? android.R.color.system_neutral1_900 : android.R.color.system_neutral1_50);
                } catch (Throwable ignored) {
                }
            }
            return night ? Color.parseColor("#111418") : Color.parseColor("#F5F7FA");
        }
        if (theme == PrefsManager.THEME_DARK) return Color.parseColor("#111622");
        if (theme == PrefsManager.THEME_NEON) return Color.parseColor("#060B18");
        if (theme == PrefsManager.THEME_AMOLED) return Color.parseColor("#000000");
        if (theme == PrefsManager.THEME_OCEAN) return Color.parseColor("#0A192F");
        if (theme == PrefsManager.THEME_FOREST) return Color.parseColor("#0D1B17");
        return Color.parseColor("#F5F7FA");
    }

    public static int getCardColor(int theme) {
        return getCardColor(null, theme);
    }

    public static int getCardColor(Context context, int theme) {
        if (theme == PrefsManager.THEME_SYSTEM && context != null) {
            boolean night = resolveEffectiveTheme(context, theme) == PrefsManager.THEME_DARK;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    return context.getColor(night ? android.R.color.system_neutral1_800 : android.R.color.system_neutral1_0);
                } catch (Throwable ignored) {
                }
            }
            return night ? Color.parseColor("#1C222B") : Color.parseColor("#FFFFFF");
        }
        if (theme == PrefsManager.THEME_DARK) return Color.parseColor("#1B2332");
        if (theme == PrefsManager.THEME_NEON) return Color.parseColor("#0D162B");
        if (theme == PrefsManager.THEME_AMOLED) return Color.parseColor("#0D0D10");
        if (theme == PrefsManager.THEME_OCEAN) return Color.parseColor("#112240");
        if (theme == PrefsManager.THEME_FOREST) return Color.parseColor("#142822");
        return Color.parseColor("#FFFFFF");
    }

    public static int getDividerColor(Context context, int theme) {
        boolean isLight = resolveEffectiveTheme(context, theme) == PrefsManager.THEME_LIGHT;
        return isLight ? Color.parseColor("#E2E8F0") : Color.parseColor("#243044");
    }

    public static int getPrimaryTextColor(int theme) {
        if (theme == PrefsManager.THEME_DARK) return Color.parseColor("#F1F5F9");
        if (theme == PrefsManager.THEME_NEON) return Color.parseColor("#E2F8FF");
        if (theme == PrefsManager.THEME_AMOLED) return Color.parseColor("#F4F4F5");
        if (theme == PrefsManager.THEME_OCEAN) return Color.parseColor("#E6F1FF");
        if (theme == PrefsManager.THEME_FOREST) return Color.parseColor("#ECFDF5");
        return Color.parseColor("#0F172A");
    }

    public static int getPrimaryTextColor(Context context, int theme) {
        if (theme == PrefsManager.THEME_SYSTEM && context != null) {
            return resolveEffectiveTheme(context, theme) == PrefsManager.THEME_DARK
                    ? Color.parseColor("#F1F5F9")
                    : Color.parseColor("#0F172A");
        }
        return getPrimaryTextColor(theme);
    }

    public static int getSecondaryTextColor(int theme) {
        if (theme == PrefsManager.THEME_DARK) return Color.parseColor("#94A3B8");
        if (theme == PrefsManager.THEME_NEON) return Color.parseColor("#7DD3FC");
        if (theme == PrefsManager.THEME_AMOLED) return Color.parseColor("#A1A1AA");
        if (theme == PrefsManager.THEME_OCEAN) return Color.parseColor("#8892B0");
        if (theme == PrefsManager.THEME_FOREST) return Color.parseColor("#86EFAC");
        return Color.parseColor("#64748B");
    }

    public static int getSecondaryTextColor(Context context, int theme) {
        if (theme == PrefsManager.THEME_SYSTEM && context != null) {
            return resolveEffectiveTheme(context, theme) == PrefsManager.THEME_DARK
                    ? Color.parseColor("#94A3B8")
                    : Color.parseColor("#64748B");
        }
        return getSecondaryTextColor(theme);
    }

    public static int getAccentColor(int theme) {
        if (theme == PrefsManager.THEME_DARK) return Color.parseColor("#3B82F6");
        if (theme == PrefsManager.THEME_NEON) return Color.parseColor("#22D3EE");
        if (theme == PrefsManager.THEME_AMOLED) return Color.parseColor("#60A5FA");
        if (theme == PrefsManager.THEME_OCEAN) return Color.parseColor("#38BDF8");
        if (theme == PrefsManager.THEME_FOREST) return Color.parseColor("#10B981");
        return Color.parseColor("#1E40AF");
    }

    public static int getAccentColor(Context context, int theme) {
        if (context != null) {
            int custom = PrefsManager.getCustomAccentColor(context);
            if (custom != 0) return custom;
            if (theme == PrefsManager.THEME_SYSTEM && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    return context.getColor(android.R.color.system_accent1_500);
                } catch (Throwable ignored) {
                }
            }
        }
        return getAccentColor(theme);
    }

    public static int getTopBarColor(int theme) {
        if (theme == PrefsManager.THEME_DARK) return Color.parseColor("#162033");
        if (theme == PrefsManager.THEME_NEON) return Color.parseColor("#091124");
        if (theme == PrefsManager.THEME_AMOLED) return Color.parseColor("#0A0A0C");
        if (theme == PrefsManager.THEME_OCEAN) return Color.parseColor("#0D213F");
        if (theme == PrefsManager.THEME_FOREST) return Color.parseColor("#112620");
        return Color.parseColor("#FFFFFF");
    }

    public static int getTopBarColor(Context context, int theme) {
        if (theme == PrefsManager.THEME_SYSTEM && context != null) {
            return getCardColor(context, theme);
        }
        return getTopBarColor(theme);
    }

    public static boolean isDarkSurface(Context context, int theme) {
        if (theme == PrefsManager.THEME_SYSTEM && context != null) {
            return resolveEffectiveTheme(context, theme) == PrefsManager.THEME_DARK;
        }
        return theme != PrefsManager.THEME_LIGHT;
    }

    public static void applyWindowStatusBar(Activity activity, int theme) {
        Window window = activity.getWindow();
        if (window == null) return;
        int accent = getAccentColor(activity, theme);
        boolean dark = isDarkSurface(activity, theme);
        if (dark) {
            window.setStatusBarColor(getTopBarColor(activity, theme));
        } else {
            window.setStatusBarColor(accent);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.setNavigationBarColor(getBgColor(activity, theme));
        }
    }

    public static float spScaled(Context context, float baseSp) {
        return baseSp * (context != null ? PrefsManager.getUiScale(context) : 1.0f);
    }

    public static int dpScaled(Context context, int baseDp) {
        if (context == null) return baseDp;
        float density = context.getResources().getDisplayMetrics().density;
        float scale = PrefsManager.getUiScale(context);
        return Math.round(baseDp * density * Math.max(0.85f, Math.min(1.25f, scale)));
    }

    public static String getThemeNameHe(int theme) {
        if (theme == PrefsManager.THEME_DARK) return "כהה";
        if (theme == PrefsManager.THEME_NEON) return "ניאון";
        if (theme == PrefsManager.THEME_AMOLED) return "שחור עמוק (AMOLED)";
        if (theme == PrefsManager.THEME_OCEAN) return "אוקיינוס";
        if (theme == PrefsManager.THEME_FOREST) return "יער שקט";
        if (theme == PrefsManager.THEME_SYSTEM) return "לפי מערכת (דינאמי)";
        return "בהיר נקי";
    }
}
