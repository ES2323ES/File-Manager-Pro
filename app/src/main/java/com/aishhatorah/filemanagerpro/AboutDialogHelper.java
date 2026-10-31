package com.aishhatorah.filemanagerpro;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class AboutDialogHelper {

    public static void show(Activity activity) {
        int theme = PrefsManager.getThemeMode(activity);
        int textPrimary = ThemeHelper.getPrimaryTextColor(theme);
        int accent = ThemeHelper.getAccentColor(theme);

        ScrollView scroll = new ScrollView(activity);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setPadding(50, 48, 50, 38);
        root.setBackground(ThemedDialogHelper.createDialogBackground(theme));

        // גירסת האפליקציה למעלה
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
        tvTitle.setTextSize(17.5f);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setPadding(0, 0, 0, 22);
        root.addView(tvTitle);

        // סעיף ג: @ אש התורה עם קישור ישיר למתמחים טופ (סעיף ב: ללא המילים "כתוב ב-Java נייטיב מלא • חתום במפתח קבוע")
        root.addView(buildActionButton(
                activity,
                "@ אש התורה",
                Color.parseColor("#1D4ED8"),
                Color.WHITE,
                v -> activity.startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse(activity.getString(R.string.mitmachim_url))))
        ));

        // סעיף ד: פרופיל המפתח בגייטהאב
        root.addView(buildActionButton(
                activity,
                "פרופיל המפתח בגייטהאב",
                Color.parseColor("#0F172A"),
                Color.WHITE,
                v -> activity.startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse(activity.getString(R.string.github_profile_url))))
        ));

        // סעיף ח: בדיקת עידכונים והתקנה אוטומטית
        root.addView(buildActionButton(
                activity,
                "בדיקת עידכונים והתקנה אוטומטית",
                Color.parseColor("#0284C7"),
                Color.WHITE,
                v -> UpdateChecker.checkForUpdates(activity, true)
        ));

        // סעיף ז: יומן שינויים
        root.addView(buildActionButton(
                activity,
                "יומן שינויים",
                Color.parseColor("#7C3AED"),
                Color.WHITE,
                v -> ChangelogDialogHelper.show(activity)
        ));

        // מדריך למשתמש מפורט ומפואר
        root.addView(buildActionButton(
                activity,
                "מדריך למשתמש",
                Color.parseColor("#0D9488"),
                Color.WHITE,
                v -> UserGuideDialogHelper.show(activity)
        ));

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(scroll)
                .create();

        // סעיף יח: כפתור סגור בצבע ברור התואם לגוון האפליקציה
        Button btnClose = ThemedDialogHelper.createThemedButton(activity, "סגור", theme, false, false);
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        closeLp.topMargin = 10;
        btnClose.setLayoutParams(closeLp);
        btnClose.setOnClickListener(v -> dialog.dismiss());
        root.addView(btnClose);

        scroll.addView(root);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
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
        lp.bottomMargin = 16;
        btn.setLayoutParams(lp);
        btn.setPadding(24, 24, 24, 24);
        btn.setOnClickListener(listener);
        return btn;
    }
}
