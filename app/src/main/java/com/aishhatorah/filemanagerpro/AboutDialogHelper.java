package com.aishhatorah.filemanagerpro;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class AboutDialogHelper {

    public static void show(Activity activity) {
        int theme = PrefsManager.getThemeMode(activity);
        int bgColor = ThemeHelper.getCardColor(theme);
        int textPrimary = ThemeHelper.getPrimaryTextColor(theme);
        int textSecondary = ThemeHelper.getSecondaryTextColor(theme);
        int accent = ThemeHelper.getAccentColor(theme);

        ScrollView scroll = new ScrollView(activity);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setPadding(52, 48, 52, 36);
        root.setBackgroundColor(bgColor);

        // גירסת האפליקציה למעלה (סעיף ג)
        TextView tvVersionBadge = new TextView(activity);
        tvVersionBadge.setText("File Manager Pro  •  גירסה " + BuildConfig.VERSION_NAME);
        tvVersionBadge.setTextColor(accent);
        tvVersionBadge.setTextSize(15f);
        tvVersionBadge.setTypeface(Typeface.DEFAULT_BOLD);
        tvVersionBadge.setGravity(Gravity.CENTER);
        root.addView(tvVersionBadge);

        ImageView logo = new ImageView(activity);
        logo.setImageResource(R.drawable.ic_app_logo);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(144, 144);
        logoLp.gravity = Gravity.CENTER_HORIZONTAL;
        logoLp.topMargin = 24;
        logoLp.bottomMargin = 16;
        root.addView(logo, logoLp);

        TextView tvTitle = new TextView(activity);
        tvTitle.setText("מנהל קבצים מתקדם לאנדרואיד");
        tvTitle.setTextColor(textPrimary);
        tvTitle.setTextSize(18f);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setGravity(Gravity.CENTER);
        root.addView(tvTitle);

        // קרדיט למפתח אש התורה (סעיף ב)
        TextView tvCredit = new TextView(activity);
        tvCredit.setText("פיתוח והנדסת תוכנה: אש התורה\nכתוב ב-Java נייטיב מלא • חתום במפתח קבוע");
        tvCredit.setTextColor(textSecondary);
        tvCredit.setTextSize(13.5f);
        tvCredit.setGravity(Gravity.CENTER);
        tvCredit.setPadding(0, 12, 0, 28);
        root.addView(tvCredit);

        // קישור לפרופיל של אש התורה במתמחים טופ
        root.addView(buildActionButton(
                activity,
                "פרופיל אש התורה במתמחים טופ",
                Color.parseColor("#1D4ED8"),
                Color.WHITE,
                v -> activity.startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse(activity.getString(R.string.mitmachim_url))))
        ));

        // קישור לפרופיל ב-GitHub
        root.addView(buildActionButton(
                activity,
                "הפרופיל שלי ב-GitHub (ES2323ES)",
                Color.parseColor("#0F172A"),
                Color.WHITE,
                v -> activity.startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse(activity.getString(R.string.github_profile_url))))
        ));

        // בדיקת עדכון ב-GitHub כולל הורדה ישירה והתקנה (סעיף ד)
        root.addView(buildActionButton(
                activity,
                "בדיקת עדכונים ב-GitHub והתקנה ישירה",
                Color.parseColor("#0284C7"),
                Color.WHITE,
                v -> UpdateChecker.checkForUpdates(activity, true)
        ));

        // מדריך למשתמש מפורט ומפואר (סעיף טז)
        root.addView(buildActionButton(
                activity,
                "מדריך למשתמש",
                Color.parseColor("#0D9488"),
                Color.WHITE,
                v -> UserGuideDialogHelper.show(activity)
        ));

        scroll.addView(root);

        new AlertDialog.Builder(activity)
                .setView(scroll)
                .setNegativeButton("סגור", null)
                .show();
    }

    private static Button buildActionButton(Activity activity, String text, int bg, int fg, View.OnClickListener listener) {
        Button btn = new Button(activity);
        btn.setText(text);
        btn.setAllCaps(false);
        btn.setTextColor(fg);
        btn.setTextSize(13.5f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);

        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(24f);
        shape.setColor(bg);
        btn.setBackground(shape);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.bottomMargin = 18;
        btn.setLayoutParams(lp);
        btn.setPadding(24, 24, 24, 24);
        btn.setOnClickListener(listener);
        return btn;
    }
}
