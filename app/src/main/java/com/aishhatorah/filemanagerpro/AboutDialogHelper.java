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
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

/**
 * מסך אודות בעיצוב זכוכית צפה עם רקע מטושטש, הילה עדינה לכרטיסיות הפנימיות
 * ואייקון סגירה נקי (SVG) בפינת הכרטיסייה ללא כפתור תחתון מיותר.
 */
public class AboutDialogHelper {

    public static void show(Activity activity) {
        int theme = PrefsManager.getThemeMode(activity);
        int textPrimary = ThemeHelper.getPrimaryTextColor(activity, theme);
        int textSecondary = ThemeHelper.getSecondaryTextColor(activity, theme);
        int accent = ThemeHelper.getAccentColor(activity, theme);
        boolean dark = ThemeHelper.isDarkSurface(activity, theme);

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        float d = activity.getResources().getDisplayMetrics().density;
        int hPad = Math.round(18 * d);
        int vPad = Math.round(14 * d);
        root.setPadding(hPad, vPad, hPad, Math.round(18 * d));
        root.setBackground(ThemedDialogHelper.createDialogBackground(activity, theme));

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(scroll)
                .create();

        // שורת כותרת עליונה: לוגו האפליקציה ממורכז למעלה וכפתור סגירה SVG בפינה
        FrameLayout headerOverlay = new FrameLayout(activity);
        headerOverlay.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        int logoPx = ThemeHelper.dpScaled(activity, 56);
        ImageView logo = new ImageView(activity);
        logo.setImageResource(R.drawable.ic_app_logo);
        FrameLayout.LayoutParams logoLp = new FrameLayout.LayoutParams(
                logoPx, logoPx, Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        logoLp.topMargin = Math.round(4 * d);
        logoLp.bottomMargin = Math.round(6 * d);
        headerOverlay.addView(logo, logoLp);

        ImageButton btnCloseIcon = ThemeHelper.createBorderlessSvgButton(
                activity, R.drawable.ic_close, textSecondary, "סגור", 36, 7, v -> dialog.dismiss());
        FrameLayout.LayoutParams closeLp = new FrameLayout.LayoutParams(
                ThemeHelper.dpScaled(activity, 36), ThemeHelper.dpScaled(activity, 36),
                Gravity.TOP | Gravity.END);
        headerOverlay.addView(btnCloseIcon, closeLp);
        root.addView(headerOverlay);

        TextView tvAppNameVersion = new TextView(activity);
        tvAppNameVersion.setText("\u200F\u2066" + BuildConfig.VERSION_NAME + "\u2069 רשמית  •  \u2066File Manager Pro\u2069");
        tvAppNameVersion.setTextDirection(View.TEXT_DIRECTION_RTL);
        tvAppNameVersion.setTextColor(accent);
        tvAppNameVersion.setTextSize(ThemeHelper.spScaled(activity, 14.5f));
        tvAppNameVersion.setTypeface(Typeface.DEFAULT_BOLD);
        tvAppNameVersion.setGravity(Gravity.CENTER);
        tvAppNameVersion.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        tvAppNameVersion.setPadding(0, Math.round(4 * d), 0, Math.round(4 * d));
        root.addView(tvAppNameVersion, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView tvTitle = new TextView(activity);
        tvTitle.setText("מנהל קבצים מתקדם ומהיר לאנדרואיד");
        tvTitle.setTextColor(textPrimary);
        tvTitle.setTextSize(ThemeHelper.spScaled(activity, 15.5f));
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        tvTitle.setPadding(0, 0, 0, Math.round(12 * d));
        root.addView(tvTitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // משבצת פיתוח והנדסה צפה מזכוכית עם הילה עדינה
        LinearLayout creditCard = new LinearLayout(activity);
        creditCard.setOrientation(LinearLayout.VERTICAL);
        creditCard.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        creditCard.setGravity(Gravity.CENTER);
        int cPad = Math.round(14 * d);
        creditCard.setPadding(cPad, cPad, cPad, cPad);
        creditCard.setBackground(ThemeHelper.createGlassCardBackground(activity, theme, 16f, false, true));
        creditCard.setElevation(4f * d);

        TextView tvDevLine = new TextView(activity);
        tvDevLine.setText("פיתוח והנדסה: @אש התורה");
        tvDevLine.setTextColor(textPrimary);
        tvDevLine.setTextSize(ThemeHelper.spScaled(activity, 15f));
        tvDevLine.setTypeface(Typeface.DEFAULT_BOLD);
        tvDevLine.setGravity(Gravity.CENTER);
        tvDevLine.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        creditCard.addView(tvDevLine, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout linksRow = new LinearLayout(activity);
        linksRow.setOrientation(LinearLayout.HORIZONTAL);
        linksRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        linksRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams linksLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        linksLp.topMargin = Math.round(8 * d);
        linksRow.setLayoutParams(linksLp);

        int linkBlue = dark ? Color.parseColor("#60A5FA") : Color.parseColor("#1D4ED8");

        TextView tvMitmachimLink = new TextView(activity);
        tvMitmachimLink.setText("במתמחים טופ");
        tvMitmachimLink.setTextColor(linkBlue);
        tvMitmachimLink.setTextSize(ThemeHelper.spScaled(activity, 14.5f));
        tvMitmachimLink.setTypeface(Typeface.DEFAULT_BOLD);
        tvMitmachimLink.setClickable(true);
        tvMitmachimLink.setFocusable(true);
        tvMitmachimLink.setPadding(8, 4, 8, 4);
        tvMitmachimLink.setOnClickListener(v -> activity.startActivity(new Intent(
                Intent.ACTION_VIEW,
                Uri.parse(activity.getString(R.string.mitmachim_url))
        )));
        linksRow.addView(tvMitmachimLink);

        TextView tvSpacer = new TextView(activity);
        tvSpacer.setText("   ");
        tvSpacer.setTextSize(14.5f);
        linksRow.addView(tvSpacer);

        TextView tvOgenPlayLink = new TextView(activity);
        tvOgenPlayLink.setText("בעוגן פליי");
        tvOgenPlayLink.setTextColor(linkBlue);
        tvOgenPlayLink.setTextSize(ThemeHelper.spScaled(activity, 14.5f));
        tvOgenPlayLink.setTypeface(Typeface.DEFAULT_BOLD);
        tvOgenPlayLink.setClickable(true);
        tvOgenPlayLink.setFocusable(true);
        tvOgenPlayLink.setPadding(8, 4, 8, 4);
        tvOgenPlayLink.setOnClickListener(v -> activity.startActivity(new Intent(
                Intent.ACTION_VIEW,
                Uri.parse(activity.getString(R.string.ogenplay_url))
        )));
        linksRow.addView(tvOgenPlayLink);

        creditCard.addView(linksRow);

        LinearLayout.LayoutParams creditLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        creditLp.bottomMargin = Math.round(14 * d);
        root.addView(creditCard, creditLp);

        root.addView(buildFloatingGlassButton(activity, theme, "בדיקת עידכונים והתקנה אוטומטית",
                true, accent, ThemeHelper.getOnAccentTextColor(accent),
                v -> UpdateChecker.checkForUpdates(activity, true)));

        root.addView(buildFloatingGlassButton(activity, theme, "יומן שינויים",
                false, accent, textPrimary,
                v -> ChangelogDialogHelper.show(activity)));

        root.addView(buildFloatingGlassButton(activity, theme, "מדריך למשתמש",
                false, accent, textPrimary,
                v -> {
                    activity.startActivity(new Intent(activity, UserGuideActivity.class));
                    ThemeHelper.overrideOpenTransition(activity);
                }));

        root.addView(buildFloatingGlassButton(activity, theme, "יומן שגיאות ודיווח תקלות",
                false, accent, textSecondary,
                v -> {
                    activity.startActivity(new Intent(activity, DiagnosticsActivity.class));
                    ThemeHelper.overrideOpenTransition(activity);
                }));

        scroll.addView(root);
        dialog.show();
        ThemedDialogHelper.applyResponsiveDialogWindow(activity, dialog);
    }

    private static Button buildFloatingGlassButton(
            Activity activity, int theme, String text, boolean primaryAccent, int accent, int fg, View.OnClickListener listener) {
        float d = activity.getResources().getDisplayMetrics().density;
        Button btn = new Button(activity);
        btn.setText(text);
        btn.setAllCaps(false);
        btn.setTextColor(fg);
        btn.setTextSize(ThemeHelper.spScaled(activity, 13.5f));
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);

        if (primaryAccent) {
            GradientDrawable shape = new GradientDrawable();
            shape.setCornerRadius(Math.round(14 * d));
            shape.setColor(accent);
            shape.setStroke(Math.max(1, Math.round(d)), ColorUtils.setAlphaComponent(Color.WHITE, 90));
            btn.setBackground(shape);
            btn.setElevation(4f * d);
        } else {
            btn.setBackground(ThemeHelper.createGlassCardBackground(activity, theme, 14f, false, true));
            btn.setElevation(2.5f * d);
        }
        btn.setStateListAnimator(null);
        btn.setMinHeight(0);
        btn.setMinimumHeight(0);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Math.round(9 * d);
        btn.setLayoutParams(lp);
        int padV = Math.round(11 * d);
        btn.setPadding(Math.round(14 * d), padV, Math.round(14 * d), padV);
        btn.setOnClickListener(listener);
        return btn;
    }
}
