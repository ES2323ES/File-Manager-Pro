package com.aishhatorah.filemanagerpro;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * סעיף ז: התאמה אוטומטית מלאה של כלל הדיאלוגים והתפריטים לכל גודלי המסכים (מסכים קטנים, טלפונים רגילים, מתקפלים וטאבלטים)
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
            DisplayMetrics dm = activity.getResources().getDisplayMetrics();
            int maxWidth = dp(activity, 500);
            int targetWidth = Math.min(Math.round(dm.widthPixels * 0.94f), maxWidth);
            window.setLayout(targetWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    public static GradientDrawable createDialogBackground(int theme) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(36f);
        bg.setColor(ThemeHelper.getCardColor(theme));
        int accent = ThemeHelper.getAccentColor(theme);
        int strokeAlpha = theme == PrefsManager.THEME_NEON ? 150 : 65;
        bg.setStroke(2, Color.argb(strokeAlpha, Color.red(accent), Color.green(accent), Color.blue(accent)));
        return bg;
    }

    public static Button createThemedButton(Activity activity, String text, int theme, boolean filledPrimary, boolean destructive) {
        Button btn = new Button(activity);
        btn.setText(text);
        btn.setAllCaps(false);
        btn.setTextSize(13.5f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);

        int accent = ThemeHelper.getAccentColor(theme);
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(activity, 11));

        if (destructive) {
            int danger = Color.parseColor("#DC2626");
            if (filledPrimary) {
                shape.setColor(danger);
                btn.setTextColor(Color.WHITE);
            } else {
                shape.setColor(Color.argb(28, 220, 38, 38));
                shape.setStroke(Math.max(2, dp(activity, 1)), danger);
                btn.setTextColor(theme == PrefsManager.THEME_LIGHT ? danger : Color.parseColor("#F87171"));
            }
        } else if (filledPrimary) {
            if (theme == PrefsManager.THEME_NEON) {
                shape.setColor(Color.parseColor("#00B4D8"));
                shape.setStroke(Math.max(2, dp(activity, 1)), accent);
                btn.setTextColor(Color.parseColor("#030712"));
            } else {
                shape.setColor(accent);
                btn.setTextColor(Color.WHITE);
            }
        } else {
            shape.setColor(Color.argb(theme == PrefsManager.THEME_LIGHT ? 22 : 38,
                    Color.red(accent), Color.green(accent), Color.blue(accent)));
            shape.setStroke(Math.max(2, dp(activity, 1)), accent);
            btn.setTextColor(accent);
        }

        btn.setBackground(shape);
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
        int textPrimary = ThemeHelper.getPrimaryTextColor(theme);
        int textSecondary = ThemeHelper.getSecondaryTextColor(theme);
        int accent = ThemeHelper.getAccentColor(theme);

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);

        LinearLayout outer = new LinearLayout(activity);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        int hPad = dp(activity, 18);
        int vPad = dp(activity, 16);
        outer.setPadding(hPad, vPad, hPad, dp(activity, 14));
        outer.setBackground(createDialogBackground(theme));

        if (title != null && !title.isEmpty()) {
            TextView tvTitle = new TextView(activity);
            tvTitle.setText(title);
            tvTitle.setTextColor(theme == PrefsManager.THEME_NEON ? accent : textPrimary);
            tvTitle.setTextSize(16.5f);
            tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
            tvTitle.setPadding(0, 0, 0, dp(activity, 8));
            outer.addView(tvTitle);
        }

        if (message != null && !message.isEmpty()) {
            TextView tvMsg = new TextView(activity);
            tvMsg.setText(message);
            tvMsg.setTextColor(textSecondary);
            tvMsg.setTextSize(13.5f);
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
                negLp.setMarginEnd(dp(activity, 6));
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
        int theme = PrefsManager.getThemeMode(activity);
        int textPrimary = ThemeHelper.getPrimaryTextColor(theme);
        int textSecondary = ThemeHelper.getSecondaryTextColor(theme);
        int accent = ThemeHelper.getAccentColor(theme);

        ScrollView scroll = new ScrollView(activity);
        LinearLayout outer = new LinearLayout(activity);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        int hPad = dp(activity, 17);
        outer.setPadding(hPad, dp(activity, 16), hPad, dp(activity, 14));
        outer.setBackground(createDialogBackground(theme));

        TextView tvTitle = new TextView(activity);
        tvTitle.setText(title);
        tvTitle.setTextColor(theme == PrefsManager.THEME_NEON ? accent : textPrimary);
        tvTitle.setTextSize(16f);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        outer.addView(tvTitle);

        if (subtitle != null && !subtitle.isEmpty()) {
            TextView tvSub = new TextView(activity);
            tvSub.setText(subtitle);
            tvSub.setTextColor(textSecondary);
            tvSub.setTextSize(12f);
            tvSub.setPadding(0, dp(activity, 3), 0, dp(activity, 9));
            outer.addView(tvSub);
        } else {
            tvTitle.setPadding(0, 0, 0, dp(activity, 9));
        }

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(scroll)
                .create();

        for (int i = 0; i < options.size(); i++) {
            final int idx = i;
            String label = options.get(i);
            boolean isDanger = label.contains("מחיקה") || label.contains("מחק");

            Button itemBtn = new Button(activity);
            itemBtn.setText(label);
            itemBtn.setAllCaps(false);
            itemBtn.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            itemBtn.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
            itemBtn.setTextSize(13.5f);
            itemBtn.setTypeface(Typeface.DEFAULT_BOLD);
            itemBtn.setTextColor(isDanger
                    ? (theme == PrefsManager.THEME_LIGHT ? Color.parseColor("#DC2626") : Color.parseColor("#F87171"))
                    : textPrimary);

            GradientDrawable itemShape = new GradientDrawable();
            itemShape.setCornerRadius(dp(activity, 10));
            int rowBg = theme == PrefsManager.THEME_LIGHT
                    ? Color.parseColor("#F1F5F9")
                    : (theme == PrefsManager.THEME_NEON ? Color.parseColor("#111D42") : Color.parseColor("#0F172A"));
            itemShape.setColor(rowBg);
            itemShape.setStroke(Math.max(2, dp(activity, 1)), isDanger
                    ? Color.argb(90, 220, 38, 38)
                    : Color.argb(55, Color.red(accent), Color.green(accent), Color.blue(accent)));
            itemBtn.setBackground(itemShape);
            itemBtn.setMinHeight(0);
            itemBtn.setMinimumHeight(0);
            itemBtn.setPadding(dp(activity, 13), dp(activity, 10), dp(activity, 13), dp(activity, 10));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(activity, 6);
            itemBtn.setLayoutParams(lp);
            itemBtn.setOnClickListener(v -> {
                dialog.dismiss();
                listener.onSelected(idx);
            });
            outer.addView(itemBtn);
        }

        Button btnClose = createThemedButton(activity, "סגור", theme, false, false);
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        closeLp.topMargin = dp(activity, 4);
        btnClose.setLayoutParams(closeLp);
        btnClose.setOnClickListener(v -> dialog.dismiss());
        outer.addView(btnClose);

        scroll.addView(outer);
        dialog.show();
        applyResponsiveDialogWindow(activity, dialog);
    }
}
