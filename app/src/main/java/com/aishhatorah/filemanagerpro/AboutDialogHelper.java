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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * סעיפים י, כ, 7, 37:
 * - עיצוב מינימליסטי ונקי המותאם לערכת הנושא הפעילה.
 * - קרדיט שמור במדויק: "פיתוח והנדסה: @אש התורה" עם קישורים כחולים ל"במתמחים טופ" ול"בעוגן פליי".
 * - כפתורים לבדיקת עידכונים, יומן שינויים, מדריך למשתמש ויומן שגיאות ודיווח תקלות.
 */
public class AboutDialogHelper {

    public static void show(Activity activity) {
        int theme = PrefsManager.getThemeMode(activity);
        int textPrimary = ThemeHelper.getPrimaryTextColor(activity, theme);
        int textSecondary = ThemeHelper.getSecondaryTextColor(activity, theme);
        int accent = ThemeHelper.getAccentColor(activity, theme);
        int divider = ThemeHelper.getDividerColor(activity, theme);
        int subtleBg = ThemeHelper.getBgColor(activity, theme);
        boolean dark = ThemeHelper.isDarkSurface(activity, theme);

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        float d = activity.getResources().getDisplayMetrics().density;
        int hPad = Math.round(18 * d);
        int vPad = Math.round(16 * d);
        root.setPadding(hPad, vPad, hPad, Math.round(14 * d));
        root.setBackground(ThemedDialogHelper.createDialogBackground(activity, theme));

        TextView tvVersionBadge = new TextView(activity);
        tvVersionBadge.setText("File Manager Pro  •  גירסה " + UpdateChecker.formatOfficialVer(BuildConfig.VERSION_NAME));
        tvVersionBadge.setTextColor(accent);
        tvVersionBadge.setTextSize(ThemeHelper.spScaled(activity, 14.5f));
        tvVersionBadge.setTypeface(Typeface.DEFAULT_BOLD);
        tvVersionBadge.setGravity(Gravity.CENTER);
        tvVersionBadge.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        root.addView(tvVersionBadge, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        int logoPx = ThemeHelper.dpScaled(activity, 52);
        ImageView logo = new ImageView(activity);
        logo.setImageResource(R.drawable.ic_app_logo);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(logoPx, logoPx);
        logoLp.gravity = Gravity.CENTER_HORIZONTAL;
        logoLp.topMargin = Math.round(10 * d);
        logoLp.bottomMargin = Math.round(6 * d);
        root.addView(logo, logoLp);

        TextView tvTitle = new TextView(activity);
        tvTitle.setText("מנהל קבצים מתקדם ומהיר לאנדרואיד");
        tvTitle.setTextColor(textPrimary);
        tvTitle.setTextSize(ThemeHelper.spScaled(activity, 16.5f));
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        tvTitle.setPadding(0, 0, 0, Math.round(12 * d));
        root.addView(tvTitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // משבצת פיתוח והנדסה (סעיף י - שמורה בדיוק)
        LinearLayout creditCard = new LinearLayout(activity);
        creditCard.setOrientation(LinearLayout.VERTICAL);
        creditCard.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        creditCard.setGravity(Gravity.CENTER);
        int cPad = Math.round(14 * d);
        creditCard.setPadding(cPad, cPad, cPad, cPad);

        GradientDrawable creditBg = new GradientDrawable();
        creditBg.setCornerRadius(Math.round(14 * d));
        creditBg.setColor(subtleBg);
        creditBg.setStroke(2, divider);
        creditCard.setBackground(creditBg);

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

        root.addView(buildMinimalButton(activity, "בדיקת עידכונים והתקנה אוטומטית",
                accent, theme == PrefsManager.THEME_NEON ? Color.parseColor("#04101A") : Color.WHITE, 0,
                v -> UpdateChecker.checkForUpdates(activity, true)));

        root.addView(buildMinimalButton(activity, "יומן שינויים",
                subtleBg, textPrimary, divider,
                v -> ChangelogDialogHelper.show(activity)));

        root.addView(buildMinimalButton(activity, "מדריך למשתמש",
                subtleBg, textPrimary, divider,
                v -> activity.startActivity(new Intent(activity, UserGuideActivity.class))));

        root.addView(buildMinimalButton(activity, "יומן שגיאות ודיווח תקלות",
                subtleBg, textSecondary, divider,
                v -> activity.startActivity(new Intent(activity, DiagnosticsActivity.class))));

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(scroll)
                .create();

        Button btnClose = ThemedDialogHelper.createThemedButton(activity, "סגור", theme, false, false);
        btnClose.setGravity(Gravity.CENTER);
        btnClose.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        closeLp.topMargin = Math.round(4 * d);
        btnClose.setLayoutParams(closeLp);
        btnClose.setOnClickListener(v -> dialog.dismiss());
        root.addView(btnClose);

        scroll.addView(root);
        dialog.show();
        ThemedDialogHelper.applyResponsiveDialogWindow(activity, dialog);
    }

    private static Button buildMinimalButton(Activity activity, String text, int bg, int fg, int strokeColor, View.OnClickListener listener) {
        float d = activity.getResources().getDisplayMetrics().density;
        Button btn = new Button(activity);
        btn.setText(text);
        btn.setAllCaps(false);
        btn.setTextColor(fg);
        btn.setTextSize(ThemeHelper.spScaled(activity, 13.5f));
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);

        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(Math.round(11 * d));
        shape.setColor(bg);
        if (strokeColor != 0) {
            shape.setStroke(Math.max(2, Math.round(d)), strokeColor);
        }
        btn.setBackground(shape);
        btn.setMinHeight(0);
        btn.setMinimumHeight(0);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Math.round(8 * d);
        btn.setLayoutParams(lp);
        int padV = Math.round(11 * d);
        btn.setPadding(Math.round(14 * d), padV, Math.round(14 * d), padV);
        btn.setOnClickListener(listener);
        return btn;
    }
}
