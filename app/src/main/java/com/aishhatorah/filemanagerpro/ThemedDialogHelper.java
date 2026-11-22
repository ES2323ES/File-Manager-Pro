package com.aishhatorah.filemanagerpro;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import java.util.List;

/**
 * דיאלוגים צפים בסגנון זכוכית מודרני עם רקע מטושטש/מעומעם וכפתורים נקיים ללא מסגרות כבדות.
 */
public class ThemedDialogHelper {

    public interface MenuSelectionListener {
        void onSelected(int index);
    }

    private static int dp(Activity activity, float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    public static void applyResponsiveDialogWindow(Activity activity, AlertDialog dialog) {
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams wlp = window.getAttributes();
            if (wlp != null) {
                wlp.dimAmount = 0.44f;
                window.setAttributes(wlp);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                    window.setBackgroundBlurRadius(30);
                } catch (Throwable ignored) {
                }
            }
            DisplayMetrics dm = activity.getResources().getDisplayMetrics();
            int maxWidth = dp(activity, 500);
            int targetWidth = Math.min(Math.round(dm.widthPixels * 0.94f), maxWidth);
            window.setLayout(targetWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    public static GradientDrawable createDialogBackground(int theme) {
        return createDialogBackground(null, theme);
    }

    public static GradientDrawable createDialogBackground(Context context, int theme) {
        return ThemeHelper.createFloatingGlassDialogBackground(context, theme);
    }

    public static Button createThemedButton(Activity activity, String text, int theme, boolean filledPrimary, boolean destructive) {
        Button btn = new Button(activity);
        btn.setText(text);
        btn.setAllCaps(false);
        btn.setTextSize(ThemeHelper.spScaled(activity, 13.5f));
        btn.setTypeface(Typeface.DEFAULT_BOLD);

        int accent = ThemeHelper.getAccentColor(activity, theme);
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(activity, 14));

        if (destructive) {
            int danger = Color.parseColor("#DC2626");
            if (filledPrimary) {
                shape.setColor(danger);
                btn.setTextColor(Color.WHITE);
            } else {
                shape.setColor(Color.TRANSPARENT);
                btn.setTextColor(ThemeHelper.isDarkSurface(activity, theme) ? Color.parseColor("#F87171") : danger);
            }
        } else if (filledPrimary) {
            shape.setColor(accent);
            btn.setTextColor(theme == PrefsManager.THEME_NEON ? Color.parseColor("#04101A") : Color.WHITE);
        } else {
            shape.setColor(ColorUtils.setAlphaComponent(accent, ThemeHelper.isDarkSurface(activity, theme) ? 26 : 16));
            btn.setTextColor(accent);
        }

        btn.setBackground(shape);
        btn.setStateListAnimator(null);
        btn.setElevation(0f);
        btn.setMinHeight(0);
        btn.setMinimumHeight(0);
        btn.setPadding(dp(activity, 14), dp(activity, 10), dp(activity, 14), dp(activity, 10));
        return btn;
    }

    public static AlertDialog showCustomDialog(
            Activity activity,
            String title,
            String message,
            View customBody,
            String positiveLabel,
            Runnable onPositive,
            String negativeLabel,
            Runnable onNegative,
            boolean destructivePositive
    ) {
        int theme = PrefsManager.getThemeMode(activity);
        int textPrimary = ThemeHelper.getPrimaryTextColor(activity, theme);
        int textSecondary = ThemeHelper.getSecondaryTextColor(activity, theme);

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);

        LinearLayout outer = new LinearLayout(activity);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        int hPad = dp(activity, 18);
        int vPad = dp(activity, 16);
        outer.setPadding(hPad, vPad, hPad, dp(activity, 14));
        outer.setBackground(createDialogBackground(activity, theme));

        if (title != null && !title.isEmpty()) {
            TextView tvTitle = new TextView(activity);
            tvTitle.setText(title);
            tvTitle.setTextColor(textPrimary);
            tvTitle.setTextSize(ThemeHelper.spScaled(activity, 16f));
            tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
            tvTitle.setPadding(0, 0, 0, dp(activity, 8));
            outer.addView(tvTitle);
        }

        if (message != null && !message.isEmpty()) {
            TextView tvMsg = new TextView(activity);
            tvMsg.setText(message);
            tvMsg.setTextColor(textSecondary);
            tvMsg.setTextSize(ThemeHelper.spScaled(activity, 13.5f));
            tvMsg.setLineSpacing(dp(activity, 2), 1.15f);
            tvMsg.setPadding(0, 0, 0, dp(activity, 12));
            outer.addView(tvMsg);
        }

        if (customBody != null) {
            LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bodyLp.bottomMargin = dp(activity, 12);
            outer.addView(customBody, bodyLp);
        }

        LinearLayout btnRow = new LinearLayout(activity);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);

        scroll.addView(outer);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(scroll)
                .create();

        if (negativeLabel != null && !negativeLabel.isEmpty()) {
            Button btnNeg = createThemedButton(activity, negativeLabel, theme, false, false);
            LinearLayout.LayoutParams negLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (positiveLabel != null && !positiveLabel.isEmpty()) {
                negLp.setMarginEnd(dp(activity, 8));
            }
            btnNeg.setLayoutParams(negLp);
            btnNeg.setOnClickListener(v -> {
                dialog.dismiss();
                if (onNegative != null) onNegative.run();
            });
            btnRow.addView(btnNeg);
        }

        if (positiveLabel != null && !positiveLabel.isEmpty()) {
            Button btnPos = createThemedButton(activity, positiveLabel, theme, true, destructivePositive);
            LinearLayout.LayoutParams posLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            btnPos.setLayoutParams(posLp);
            btnPos.setOnClickListener(v -> {
                dialog.dismiss();
                if (onPositive != null) onPositive.run();
            });
            btnRow.addView(btnPos);
        }

        outer.addView(btnRow);

        dialog.show();
        applyResponsiveDialogWindow(activity, dialog);
        return dialog;
    }

    public static void showThemedMenu(
            Activity activity,
            String title,
            String subtitle,
            List<String> options,
            MenuSelectionListener listener
    ) {
        showThemedMenu(activity, null, title, subtitle, options, listener);
    }

    public static void showThemedMenu(
            Activity activity,
            View anchor,
            String title,
            String subtitle,
            List<String> options,
            MenuSelectionListener listener
    ) {
        if (options == null || options.isEmpty() || listener == null) return;
        java.util.ArrayList<GlassAnchoredPopoverHelper.OptionItem> items = new java.util.ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            final int idx = i;
            String label = options.get(i);
            boolean isDanger = label.contains("מחיקה") || label.contains("מחק") || label.contains("הסרת");
            boolean isSelected = label.contains("(פעיל)");
            String cleanLabel = label.replace("  (פעיל)", "").trim();
            int iconRes = inferMenuIcon(cleanLabel, isDanger);
            boolean dividerBefore = isDanger && i > 0;
            items.add(new GlassAnchoredPopoverHelper.OptionItem(
                    cleanLabel,
                    null,
                    iconRes,
                    isSelected,
                    isDanger,
                    dividerBefore,
                    () -> listener.onSelected(idx)
            ));
        }
        GlassAnchoredPopoverHelper.showMenu(activity, anchor, title, subtitle, items, false);
    }

    private static int inferMenuIcon(String label, boolean isDanger) {
        if (isDanger) return R.drawable.ic_delete;
        if (label.contains("שחזר")) return R.drawable.ic_reset;
        if (label.contains("עורך וידאו")) return R.drawable.ic_video_cutter;
        if (label.contains("מקור ה-APK") || label.contains("התקן") || label.contains("אפליקציה")) return R.drawable.ic_apps;
        if (label.contains("תיקייה")) return R.drawable.ic_folder;
        if (label.contains("פתיחה באמצעות") || label.contains("מערכת")) return R.drawable.ic_settings;
        if (label.contains("פתיחה")) return R.drawable.ic_visibility;
        if (label.contains("בחירה מרובה")) return R.drawable.ic_multiselect;
        if (label.contains("חילוץ") || label.contains("דחיסה") || label.contains("ארכיון")) return R.drawable.ic_zip_archive;
        if (label.contains("העתקה") || label.contains("גיבוי")) return R.drawable.ic_copy;
        if (label.contains("העברה") || label.contains("סל המיחזור")) return R.drawable.ic_cut;
        if (label.contains("שינוי שם")) return R.drawable.ic_edit_pencil;
        if (label.contains("שיתוף")) return R.drawable.ic_share;
        if (label.contains("מאפיינים") || label.contains("פרטי")) return R.drawable.ic_info;
        if (label.contains("מיון") || label.contains("לפי ") || label.contains("סדר")) return R.drawable.ic_sort;
        if (label.contains("חזרה")) return R.drawable.ic_repeat_all;
        if (label.contains("טיימר") || label.contains("דקות")) return R.drawable.ic_sleep_timer;
        return R.drawable.ic_tune;
    }
}
