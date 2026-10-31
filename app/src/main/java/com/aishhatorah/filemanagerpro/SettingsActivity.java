package com.aishhatorah.filemanagerpro;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        renderSettingsUi();
    }

    private void renderSettingsUi() {
        int theme = PrefsManager.getThemeMode(this);
        ThemeHelper.applyWindowStatusBar(this, theme);

        int bgColor = ThemeHelper.getBgColor(theme);
        int cardColor = ThemeHelper.getCardColor(theme);
        int textPrimary = ThemeHelper.getPrimaryTextColor(theme);
        int textSecondary = ThemeHelper.getSecondaryTextColor(theme);
        int accent = ThemeHelper.getAccentColor(theme);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(bgColor);

        // סרגל כותרת עליון
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(ThemeHelper.getTopBarColor(theme));
        topBar.setPadding(24, 22, 24, 22);

        Button btnBack = new Button(this);
        btnBack.setText("חזרה");
        btnBack.setTextColor(accent);
        btnBack.setBackgroundColor(Color.TRANSPARENT);
        btnBack.setOnClickListener(v -> finish());
        topBar.addView(btnBack);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("הגדרות האפליקציה");
        tvTitle.setTextSize(18f);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setTextColor(textPrimary);
        LinearLayout.LayoutParams lpTitle = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lpTitle.setMarginStart(16);
        topBar.addView(tvTitle, lpTitle);

        root.addView(topBar);

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(32, 28, 32, 48);

        // 1. גוון התאורה של האפליקציה: בהיר, כהה, ניאון (סעיף ו)
        addSectionTitle(body, "גוון התאורה וערכת נושא", accent);
        LinearLayout themeCard = createSectionCard(cardColor);
        TextView tvThemeSub = new TextView(this);
        tvThemeSub.setText("בחר את גוון התאורה של File Manager Pro (מצב נוכחי: " + ThemeHelper.getThemeNameHe(theme) + ")");
        tvThemeSub.setTextColor(textSecondary);
        tvThemeSub.setTextSize(13f);
        tvThemeSub.setPadding(0, 0, 0, 20);
        themeCard.addView(tvThemeSub);

        LinearLayout themeButtonsRow = new LinearLayout(this);
        themeButtonsRow.setOrientation(LinearLayout.HORIZONTAL);
        themeButtonsRow.setWeightSum(3f);

        themeButtonsRow.addView(createThemeButton("בהיר", PrefsManager.THEME_LIGHT, theme == PrefsManager.THEME_LIGHT, accent));
        themeButtonsRow.addView(createThemeButton("כהה", PrefsManager.THEME_DARK, theme == PrefsManager.THEME_DARK, accent));
        themeButtonsRow.addView(createThemeButton("ניאון", PrefsManager.THEME_NEON, theme == PrefsManager.THEME_NEON, accent));

        themeCard.addView(themeButtonsRow);
        body.addView(themeCard);

        // 2. ניתוח ובדיקת הקבצים הגדולים ביותר + התרעת 96% (סעיף טו)
        addSectionTitle(body, "ניתוח נפח אחסון והתרעות זיכרון", accent);
        LinearLayout analyzerCard = createSectionCard(cardColor);

        Button btnAnalyzer = new Button(this);
        btnAnalyzer.setText("ניתוח — איתור הקבצים בעלי המשקל הגדול ביותר");
        btnAnalyzer.setAllCaps(false);
        btnAnalyzer.setTextColor(Color.WHITE);
        btnAnalyzer.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable analyzerShape = new GradientDrawable();
        analyzerShape.setCornerRadius(24f);
        analyzerShape.setColor(Color.parseColor("#1D4ED8"));
        btnAnalyzer.setBackground(analyzerShape);
        btnAnalyzer.setPadding(24, 26, 24, 26);
        btnAnalyzer.setOnClickListener(v -> startActivity(new Intent(this, StorageAnalyzerActivity.class)));
        analyzerCard.addView(btnAnalyzer);

        addSwitchRow(analyzerCard,
                "התרעה אוטומטית ב-96% תפוסה",
                "הצג אזהרה והתראה כאשר הזיכרון הפנימי או החיצוני מגיעים ל-96% מהקיבולת",
                PrefsManager.isAlert96Enabled(this),
                textPrimary, textSecondary,
                checked -> {
                    PrefsManager.setAlert96Enabled(this, checked);
                    if (checked) StorageUtils.checkAndNotify96Threshold(this);
                });

        body.addView(analyzerCard);

        // 3. תצוגת קבצים ומיון
        addSectionTitle(body, "תצוגת קבצים, תיקיות ומיון", accent);
        LinearLayout displayCard = createSectionCard(cardColor);

        addSwitchRow(displayCard,
                "הצגת קבצים מוסתרים",
                "הצג קבצי ותיקיות מערכת המתחילים בנקודה (.)",
                PrefsManager.isShowHidden(this),
                textPrimary, textSecondary,
                checked -> PrefsManager.setShowHidden(this, checked));

        addSwitchRow(displayCard,
                "תצוגת רשת (Grid) במקום רשימה",
                "הצג קבצים בשתי עמודות בסייר הקבצים",
                PrefsManager.isGridLayout(this),
                textPrimary, textSecondary,
                checked -> PrefsManager.setGridLayout(this, checked));

        addSwitchRow(displayCard,
                "הצגת תמונות ממוזערות למדיה",
                "טען תצוגה מקדימה מהירה לתמונות וסרטונים",
                PrefsManager.isShowThumbnails(this),
                textPrimary, textSecondary,
                checked -> PrefsManager.setShowThumbnails(this, checked));

        addSwitchRow(displayCard,
                "חישוב גודל תיקיות",
                "חשב נפח מצטבר עבור תיקיות משנה",
                PrefsManager.isShowFolderSizes(this),
                textPrimary, textSecondary,
                checked -> PrefsManager.setShowFolderSizes(this, checked));

        addSwitchRow(displayCard,
                "סדר מיון עולה (א-ת / מהקטן לגדול)",
                "כבה למיון בסדר יורד (מהכבד או החדש ביותר לראשון)",
                PrefsManager.isSortAscending(this),
                textPrimary, textSecondary,
                checked -> PrefsManager.setSortAscending(this, checked));

        Button btnSortField = createSubtleButton("שינוי שדה מיון ברירת מחדל", cardColor, accent, v -> showSortModeDialog());
        displayCard.addView(btnSortField);
        body.addView(displayCard);

        // 4. סל מיחזור ואבטחת מחיקה
        addSectionTitle(body, "סל מיחזור ומחיקת קבצים", accent);
        LinearLayout trashCard = createSectionCard(cardColor);

        addSwitchRow(trashCard,
                "העברה לסל המיחזור בעת מחיקה",
                "שמור קבצים שנמחקו בסל המיחזור לצורך שחזור עתידי",
                PrefsManager.isUseRecycleBin(this),
                textPrimary, textSecondary,
                checked -> PrefsManager.setUseRecycleBin(this, checked));

        addSwitchRow(trashCard,
                "בקשת אישור לפני מחיקה",
                "הצג חלון אימות לפני מחיקת קובץ או תיקייה",
                PrefsManager.isConfirmDelete(this),
                textPrimary, textSecondary,
                checked -> PrefsManager.setConfirmDelete(this, checked));

        Button btnEmptyTrashNow = createSubtleButton(
                "ריקון סל המיחזור כעת (" + RecycleBinManager.getTrashItemCount(this) + " פריטים)",
                cardColor, Color.parseColor("#DC2626"),
                v -> {
                    int deleted = RecycleBinManager.emptyTrash(this);
                    Toast.makeText(this, "רוקנו " + deleted + " פריטים מסל המיחזור", Toast.LENGTH_SHORT).show();
                    renderSettingsUi();
                });
        trashCard.addView(btnEmptyTrashNow);
        body.addView(trashCard);

        // 5. הרשאות מערכת, מדיה והתקנת עדכונים (סעיף ז)
        addSectionTitle(body, "הרשאות גישה למדיה, אחסון ועריכה", accent);
        LinearLayout permsCard = createSectionCard(cardColor);

        boolean allFilesGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager();
        TextView tvPermStatus = new TextView(this);
        tvPermStatus.setText("סטטוס הרשאת עריכת כל הקבצים: " + (allFilesGranted ? "מאושרת ופעילה" : "ממתינה לאישור"));
        tvPermStatus.setTextColor(allFilesGranted ? Color.parseColor("#16A34A") : Color.parseColor("#D97706"));
        tvPermStatus.setTypeface(Typeface.DEFAULT_BOLD);
        tvPermStatus.setPadding(0, 0, 0, 14);
        permsCard.addView(tvPermStatus);

        permsCard.addView(createSubtleButton("ניהול הרשאת גישה לכל הקבצים והאחסון", cardColor, accent, v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } else {
                openAppSystemSettings();
            }
        }));

        permsCard.addView(createSubtleButton("פתיחת הרשאות מערכת (אודיו, וידאו, תמונות והתראות)", cardColor, accent,
                v -> openAppSystemSettings()));
        body.addView(permsCard);

        // 6. עדכוני GitHub ואיפוס
        addSectionTitle(body, "עדכונים מ-GitHub ואיפוס הגדרות", accent);
        LinearLayout updateCard = createSectionCard(cardColor);

        addSwitchRow(updateCard,
                "בדיקת עדכונים אוטומטית בהפעלה",
                "בדוק בגייטהאב אם שוחררה גירסה חדשה (V2 / V3) בעת פתיחת האפליקציה",
                PrefsManager.isAutoCheckUpdates(this),
                textPrimary, textSecondary,
                checked -> PrefsManager.setAutoCheckUpdates(this, checked));

        updateCard.addView(createSubtleButton(
                "הגדרת מאגר GitHub לעדכונים (" + PrefsManager.getGithubRepo(this) + ")",
                cardColor, accent, v -> promptEditGithubRepo()));

        updateCard.addView(createSubtleButton(
                "איפוס כל ההגדרות לברירת מחדל",
                cardColor, Color.parseColor("#DC2626"), v -> {
                    PrefsManager.resetAll(this);
                    Toast.makeText(this, "כל ההגדרות אופסו לברירת המחדל", Toast.LENGTH_SHORT).show();
                    renderSettingsUi();
                }));

        body.addView(updateCard);

        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
    }

    private void addSectionTitle(LinearLayout parent, String title, int color) {
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextColor(color);
        tv.setTextSize(14f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(8, 22, 8, 12);
        parent.addView(tv);
    }

    private LinearLayout createSectionCard(int cardColor) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(32, 28, 32, 28);
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(28f);
        shape.setColor(cardColor);
        card.setBackground(shape);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = 14;
        card.setLayoutParams(lp);
        return card;
    }

    private Button createThemeButton(String label, int mode, boolean active, int accent) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(active ? Color.WHITE : accent);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(20f);
        bg.setColor(active ? Color.parseColor("#1D4ED8") : Color.TRANSPARENT);
        bg.setStroke(2, accent);
        b.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(6, 0, 6, 0);
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> {
            PrefsManager.setThemeMode(this, mode);
            renderSettingsUi();
        });
        return b;
    }

    private interface SwitchCallback {
        void onChanged(boolean checked);
    }

    private void addSwitchRow(LinearLayout parent, String title, String subtitle, boolean initial,
                              int textPrimary, int textSecondary, SwitchCallback cb) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 18, 0, 18);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView tvT = new TextView(this);
        tvT.setText(title);
        tvT.setTextColor(textPrimary);
        tvT.setTextSize(14f);
        tvT.setTypeface(Typeface.DEFAULT_BOLD);
        textCol.addView(tvT);

        TextView tvS = new TextView(this);
        tvS.setText(subtitle);
        tvS.setTextColor(textSecondary);
        tvS.setTextSize(12f);
        textCol.addView(tvS);

        row.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch sw = new Switch(this);
        sw.setChecked(initial);
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> cb.onChanged(isChecked));
        row.addView(sw);

        parent.addView(row);
    }

    private Button createSubtleButton(String label, int cardColor, int textColor, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(textColor);
        b.setTypeface(Typeface.DEFAULT_BOLD);

        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(20f);
        shape.setColor(cardColor);
        shape.setStroke(2, textColor);
        b.setBackground(shape);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 16;
        b.setLayoutParams(lp);
        b.setOnClickListener(listener);
        return b;
    }

    private void showSortModeDialog() {
        String[] modes = {"לפי שם (א-ת)", "לפי משקל קובץ", "לפי תאריך שינוי", "לפי סוג קובץ"};
        int current = PrefsManager.getSortMode(this);
        new AlertDialog.Builder(this)
                .setTitle("מיון ברירת מחדל")
                .setSingleChoiceItems(modes, current, (d, which) -> {
                    PrefsManager.setSortMode(this, which);
                    d.dismiss();
                    Toast.makeText(this, "המיון עודכן: " + modes[which], Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void promptEditGithubRepo() {
        EditText input = new EditText(this);
        input.setText(PrefsManager.getGithubRepo(this));
        new AlertDialog.Builder(this)
                .setTitle("מאגר GitHub לבדיקת עדכונים")
                .setMessage("הזן בפורמט owner/repo:")
                .setView(input)
                .setPositiveButton("שמור", (d, w) -> {
                    String slug = input.getText().toString().trim();
                    if (slug.contains("/")) {
                        PrefsManager.setGithubRepo(this, slug);
                        renderSettingsUi();
                    }
                })
                .setNegativeButton("ביטול", null)
                .show();
    }

    private void openAppSystemSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }
}
