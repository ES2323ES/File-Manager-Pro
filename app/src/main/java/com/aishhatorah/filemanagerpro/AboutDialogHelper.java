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
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * סעיפים א, ב, ג:
 * - הוסר לחלוטין "פרופיל המפתח בגייטהאב"
 * - נוספה שורה ממורכזת ומסודרת: "פיתוח והנדסה: @ אש התורה" שבה רק "@ אש התורה" בכחול ומעביר לפרופיל
 * - תוקן הבאג שבו המילים קפצו לצד שמאל — כל הרכיבים והכפתורים ממורכזים לחלוטין באמצע!
 */
public class AboutDialogHelper {

    public static void show(Activity activity) {
        int theme = PrefsManager.getThemeMode(activity);
        int textPrimary = ThemeHelper.getPrimaryTextColor(theme);
        int accent = ThemeHelper.getAccentColor(theme);

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(50, 48, 50, 38);
        root.setBackground(ThemedDialogHelper.createDialogBackground(theme));

        // גירסת האפליקציה למעלה — ממורכזת תמיד
        TextView tvVersionBadge = new TextView(activity);
        tvVersionBadge.setText("File Manager Pro  •  גירסה " + BuildConfig.VERSION_NAME);
        tvVersionBadge.setTextColor(accent);
        tvVersionBadge.setTextSize(15f);
        tvVersionBadge.setTypeface(Typeface.DEFAULT_BOLD);
        tvVersionBadge.setGravity(Gravity.CENTER);
        tvVersionBadge.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        root.addView(tvVersionBadge, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ImageView logo = new ImageView(activity);
        logo.setImageResource(R.drawable.ic_app_logo);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(148, 148);
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
        tvTitle.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        tvTitle.setPadding(0, 0, 0, 18);
        root.addView(tvTitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // סעיף ב: בצד ימין "פיתוח והנדסה:" בטקסט רגיל ומסודר, ורק "@ אש התורה" בכחול ולחיץ לפרופיל במתמחים טופ
        LinearLayout creditCard = new LinearLayout(activity);
        creditCard.setOrientation(LinearLayout.HORIZONTAL);
        creditCard.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        creditCard.setGravity(Gravity.CENTER);
        creditCard.setPadding(28, 22, 28, 22);

        GradientDrawable creditBg = new GradientDrawable();
        creditBg.setCornerRadius(24f);
        int innerBg = theme == PrefsManager.THEME_LIGHT
                ? Color.parseColor("#F1F5F9")
                : (theme == PrefsManager.THEME_NEON ? Color.parseColor("#071029") : Color.parseColor("#0F172A"));
        creditBg.setColor(innerBg);
        creditBg.setStroke(2, Color.argb(65, Color.red(accent), Color.green(accent), Color.blue(accent)));
        creditCard.setBackground(creditBg);

        TextView tvDevLabel = new TextView(activity);
        tvDevLabel.setText("פיתוח והנדסה: ");
        tvDevLabel.setTextColor(textPrimary);
        tvDevLabel.setTextSize(15f);
        tvDevLabel.setTypeface(Typeface.DEFAULT_BOLD);
        creditCard.addView(tvDevLabel);

        int linkBlue = theme == PrefsManager.THEME_LIGHT
                ? Color.parseColor("#1D4ED8")
                : Color.parseColor("#3B82F6");

        TextView tvDevLink = new TextView(activity);
        tvDevLink.setText("@ אש התורה");
        tvDevLink.setTextColor(linkBlue);
        tvDevLink.setTextSize(15.5f);
        tvDevLink.setTypeface(Typeface.DEFAULT_BOLD);
        tvDevLink.setClickable(true);
        tvDevLink.setFocusable(true);
        tvDevLink.setOnClickListener(v -> activity.startActivity(new Intent(
                Intent.ACTION_VIEW,
                Uri.parse(activity.getString(R.string.mitmachim_url))
        )));
        creditCard.addView(tvDevLink);

        LinearLayout.LayoutParams creditLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        creditLp.bottomMargin = 22;
        root.addView(creditCard, creditLp);

        // בדיקת עידכונים והתקנה אוטומטית
        root.addView(buildCenteredActionButton(
                activity,
                "בדיקת עידכונים והתקנה אוטומטית",
                Color.parseColor("#0284C7"),
                Color.WHITE,
                v -> UpdateChecker.checkForUpdates(activity, true)
        ));

        // יומן שינויים מפואר
        root.addView(buildCenteredActionButton(
                activity,
                "יומן שינויים",
                Color.parseColor("#7C3AED"),
                Color.WHITE,
                v -> ChangelogDialogHelper.show(activity)
        ));

        // מדריך למשתמש
        root.addView(buildCenteredActionButton(
                activity,
                "מדריך למשתמש",
                Color.parseColor("#0D9488"),
                Color.WHITE,
                v -> UserGuideDialogHelper.show(activity)
        ));

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(scroll)
                .create();

        Button btnClose = ThemedDialogHelper.createThemedButton(activity, "סגור", theme, false, false);
        btnClose.setGravity(Gravity.CENTER);
        btnClose.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
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

    private static Button buildCenteredActionButton(Activity activity, String text, int bg, int fg, View.OnClickListener listener) {
        Button btn = new Button(activity);
        btn.setText(text);
        btn.setAllCaps(false);
        btn.setTextColor(fg);
        btn.setTextSize(13.5f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);

        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(24f);
        shape.setColor(bg);
        btn.setBackground(shape);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.bottomMargin = 16;
        btn.setLayoutParams(lp);
        btn.setPadding(24, 24, 24, 24);
        btn.setOnClickListener(listener);
        return btn;
    }
}
