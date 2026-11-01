package com.aishhatorah.filemanagerpro;

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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

        // סרגל כותרת עליון — סעיף י: "הגדרות" במקום "הגדרות האפליקציה"
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
        tvTitle.setText("הגדרות");
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

        // 1. גוון התאורה של האפליקציה: בהיר, כהה, ניאון
        addSectionTitle(body, "גוון התאורה וערכת נושא", accent);
        LinearLayout themeCard = createSectionCard(cardColor);
        TextView tvThemeSub = new TextView(this);
        tvThemeSub.setText("בחר את גוון התאורה של האפליקציה (מצב נוכחי: " + ThemeHelper.getThemeNameHe(theme) + ")");
        tvThemeSub.setTextColor(textSecondary);
        tvThemeSub.setTextSize(13f);
        tvThemeSub.setPadding(0, 0, 0, 20);
        themeCard.addView(tvThemeSub);

        LinearLayout themeButtonsRow = new LinearLayout(this);
        themeButtonsRow.setOrientation(LinearLayout.HORIZONTAL);
        themeButtonsRow.setWeightSum(3f);

        themeButtonsRow.addView(createOptionButton("בהיר", theme == PrefsManager.THEME_LIGHT, accent, () -> {
            PrefsManager.setThemeMode(this, PrefsManager.THEME_LIGHT);
            renderSettingsUi();
        }));
        themeButtonsRow.addView(createOptionButton("כהה", theme == PrefsManager.THEME_DARK, accent, () -> {
            PrefsManager.setThemeMode(this, PrefsManager.THEME_DARK);
            renderSettingsUi();
        }));
        themeButtonsRow.addView(createOptionButton("ניאון", theme == PrefsManager.THEME_NEON, accent, () -> {
            PrefsManager.setThemeMode(this, PrefsManager.THEME_NEON);
            renderSettingsUi();
        }));

        themeCard.addView(themeButtonsRow);
        body.addView(themeCard);

        // 2. ניתוח ובדיקת הקבצים הגדולים ביותר + התרעת 96%
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

        // 3. סעיף ג: בחירת תצוגת רשת או תצוגת רשימה בכל הקטגוריות (כולל תמונות וסרטונים שברירת המחדל שלהן רשת) + מיון
        addSectionTitle(body, "תצוגת התוכן בתיקיות, בקטגוריות ומיון", accent);
        LinearLayout displayCard = createSectionCard(cardColor);

        TextView tvLayoutLabel = new TextView(this);
        tvLayoutLabel.setText("תצוגה בתיקיות ובשאר הקטגוריות:");
        tvLayoutLabel.setTextColor(textPrimary);
        tvLayoutLabel.setTextSize(14f);
        tvLayoutLabel.setTypeface(Typeface.DEFAULT_BOLD);
        tvLayoutLabel.setPadding(0, 0, 0, 12);
        displayCard.addView(tvLayoutLabel);

        boolean isGrid = PrefsManager.isGridLayout(this);
        LinearLayout viewModeRow = new LinearLayout(this);
        viewModeRow.setOrientation(LinearLayout.HORIZONTAL);
        viewModeRow.setWeightSum(2f);

        viewModeRow.addView(createOptionButton("תצוגת רשימה", !isGrid, accent, () -> {
            PrefsManager.setGeneralGridLayoutAndSyncNonMedia(this, false);
            renderSettingsUi();
        }));
        viewModeRow.addView(createOptionButton("תצוגת רשת", isGrid, accent, () -> {
            PrefsManager.setGeneralGridLayoutAndSyncNonMedia(this, true);
            renderSettingsUi();
        }));
        LinearLayout.LayoutParams vmLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vmLp.bottomMargin = 18;
        viewModeRow.setLayoutParams(vmLp);
        displayCard.addView(viewModeRow);

        TextView tvImagesLayoutLabel = new TextView(this);
        tvImagesLayoutLabel.setText("תצוגה בקטגוריית תמונות (תיקיות ותוכן התיקיות):");
        tvImagesLayoutLabel.setTextColor(textPrimary);
        tvImagesLayoutLabel.setTextSize(14f);
        tvImagesLayoutLabel.setTypeface(Typeface.DEFAULT_BOLD);
        tvImagesLayoutLabel.setPadding(0, 4, 0, 12);
        displayCard.addView(tvImagesLayoutLabel);

        boolean isImagesGrid = PrefsManager.isGridLayoutForMode(this, FileBrowserActivity.MODE_IMAGES);
        LinearLayout imagesModeRow = new LinearLayout(this);
        imagesModeRow.setOrientation(LinearLayout.HORIZONTAL);
        imagesModeRow.setWeightSum(2f);
        imagesModeRow.addView(createOptionButton("תצוגת רשימה", !isImagesGrid, accent, () -> {
            PrefsManager.setGridLayoutForMode(this, FileBrowserActivity.MODE_IMAGES, false);
            renderSettingsUi();
        }));
        imagesModeRow.addView(createOptionButton("תצוגת רשת", isImagesGrid, accent, () -> {
            PrefsManager.setGridLayoutForMode(this, FileBrowserActivity.MODE_IMAGES, true);
            renderSettingsUi();
        }));
        LinearLayout.LayoutParams imLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        imLp.bottomMargin = 18;
        imagesModeRow.setLayoutParams(imLp);
        displayCard.addView(imagesModeRow);

        TextView tvVideosLayoutLabel = new TextView(this);
        tvVideosLayoutLabel.setText("תצוגה בקטגוריית סירטונים (תיקיות ותוכן התיקיות):");
        tvVideosLayoutLabel.setTextColor(textPrimary);
        tvVideosLayoutLabel.setTextSize(14f);
        tvVideosLayoutLabel.setTypeface(Typeface.DEFAULT_BOLD);
        tvVideosLayoutLabel.setPadding(0, 4, 0, 12);
        displayCard.addView(tvVideosLayoutLabel);

        boolean isVideosGrid = PrefsManager.isGridLayoutForMode(this, FileBrowserActivity.MODE_VIDEOS);
        LinearLayout videosModeRow = new LinearLayout(this);
        videosModeRow.setOrientation(LinearLayout.HORIZONTAL);
        videosModeRow.setWeightSum(2f);
        videosModeRow.addView(createOptionButton("תצוגת רשימה", !isVideosGrid, accent, () -> {
            PrefsManager.setGridLayoutForMode(this, FileBrowserActivity.MODE_VIDEOS, false);
            renderSettingsUi();
        }));
        videosModeRow.addView(createOptionButton("תצוגת רשת", isVideosGrid, accent, () -> {
            PrefsManager.setGridLayoutForMode(this, FileBrowserActivity.MODE_VIDEOS, true);
            renderSettingsUi();
        }));
        LinearLayout.LayoutParams vdLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vdLp.bottomMargin = 10;
        videosModeRow.setLayoutParams(vdLp);
        displayCard.addView(videosModeRow);

        Button btnPerCategoryLayout = createSubtleButton(
                "בחירת תצוגת רשת / רשימה לכל הקטגוריות בנפרד",
                cardColor,
                accent,
                v -> showPerCategoryLayoutDialog()
        );
        LinearLayout.LayoutParams pclLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pclLp.topMargin = 10;
        pclLp.bottomMargin = 12;
        btnPerCategoryLayout.setLayoutParams(pclLp);
        displayCard.addView(btnPerCategoryLayout);

        addSwitchRow(displayCard,
                "הצגת קבצים מוסתרים",
                "הצג קבצי ותיקיות מערכת המתחילים בנקודה (.)",
                PrefsManager.isShowHidden(this),
                textPrimary, textSecondary,
                checked -> PrefsManager.setShowHidden(this, checked));

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

        // 4. סל מיחזור ואבטחת מחיקה (סעיף טו: בקשת אישור + אישור נוסף בריקון סל המיחזור)
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

        int trashCount = RecycleBinManager.getTrashItemCount(this);
        String trashSize = StorageUtils.formatSize(RecycleBinManager.getTrashTotalBytes(this));
        Button btnEmptyTrashNow = createSubtleButton(
                "ריקון סל המיחזור (" + trashCount + " פריטים • " + trashSize + ")",
                cardColor, Color.parseColor("#DC2626"),
                v -> confirmEmptyRecycleBinTwoStep());
        trashCard.addView(btnEmptyTrashNow);
        body.addView(trashCard);

        // 5. הרשאות מערכת, מדיה והתקנת עדכונים
        addSectionTitle(body, "הרשאות גישה למדיה, אחסון ועריכה", accent);
        LinearLayout permsCard = createSectionCard(cardColor);

        boolean allFilesGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager();
        TextView tvPermStatus = new TextView(this);
        tvPermStatus.setText("סטטוס הרשאת עריכת כל הקבצים: " + (allFilesGranted ? "מאושרת ופעילה" : "ממתינה לאישור"));
        tvPermStatus.setTextColor(allFilesGranted ? Color.parseColor("#16A34A") : Color.parseColor("#D97706"));
        tvPermStatus.setTypeface(Typeface.DEFAULT_BOLD);
        tvPermStatus.setPadding(0, 0, 0, 14);
        permsCard.addView(tvPermStatus);

        boolean notifGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;

        TextView tvNotifStatus = new TextView(this);
        tvNotifStatus.setText("הרשאת סמל האפליקציה בבר ההתראות (בזמן ניגון שיר): " + (notifGranted ? "מאושרת ופעילה" : "ממתינה לאישור"));
        tvNotifStatus.setTextColor(notifGranted ? Color.parseColor("#16A34A") : Color.parseColor("#D97706"));
        tvNotifStatus.setTypeface(Typeface.DEFAULT_BOLD);
        tvNotifStatus.setPadding(0, 6, 0, 14);
        permsCard.addView(tvNotifStatus);

        permsCard.addView(createSubtleButton("ניהול הרשאת גישה לכל הקבצים והאחסון", cardColor, accent, v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } else {
                openAppSystemSettings();
            }
        }));

        permsCard.addView(createSubtleButton("אישור הרשאת התראות (להצגת סמל האפליקציה בזמן ניגון שיר)", cardColor, accent, v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notifGranted) {
                androidx.core.app.ActivityCompat.requestPermissions(
                        this,
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                        2044
                );
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent notifIntent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                startActivity(notifIntent);
            } else {
                openAppSystemSettings();
            }
        }));

        permsCard.addView(createSubtleButton("פתיחת הרשאות מערכת (אודיו, וידאו, תמונות והתקנת עדכונים)", cardColor, accent,
                v -> openAppSystemSettings()));
        body.addView(permsCard);

        // 6. סעיפים ט, יא, טו: "עידכונים ואיפוס הגדרות" + "בדיקת עדכונים אוטומטית בהפעלה" (ללא הגדרת מאגר, ועם אישור נוסף באיפוס)
        addSectionTitle(body, "עידכונים ואיפוס הגדרות", accent);
        LinearLayout updateCard = createSectionCard(cardColor);

        addSwitchRow(updateCard,
                "בדיקת עדכונים אוטומטית בהפעלה",
                "בדוק אוטומטית בעת הכניסה לאפליקציה אם זמינה גירסה חדשה והצע עדכון רק כשקיים עדכון",
                PrefsManager.isAutoCheckUpdates(this),
                textPrimary, textSecondary,
                checked -> PrefsManager.setAutoCheckUpdates(this, checked));

        updateCard.addView(createSubtleButton(
                "איפוס כל ההגדרות לברירת מחדל",
                cardColor, Color.parseColor("#DC2626"),
                v -> confirmResetSettingsTwoStep()));

        body.addView(updateCard);

        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
    }

    // סעיף טו: בקשת אישור נוסף אחרי לחיצה על ריקון סל המיחזור
    private void confirmEmptyRecycleBinTwoStep() {
        ThemedDialogHelper.showCustomDialog(
                this,
                "ריקון סל המיחזור",
                "האם ברצונך לרוקן את סל המיחזור?",
                null,
                "המשך",
                () -> ThemedDialogHelper.showCustomDialog(
                        this,
                        "אישור נוסף למחיקה לצמיתות",
                        "שים לב: כל הקבצים בסל המיחזור יימחקו לצמיתות ולא ניתן יהיה לשחזרם. לאשר ריקון סופי?",
                        null,
                        "כן, רוקן לצמיתות",
                        () -> {
                            int deleted = RecycleBinManager.emptyTrash(this);
                            Toast.makeText(this, "רוקנו לצמיתות " + deleted + " פריטים מסל המיחזור", Toast.LENGTH_SHORT).show();
                            renderSettingsUi();
                        },
                        "סגור",
                        null,
                        true
                ),
                "סגור",
                null,
                false
        );
    }

    // סעיף טו: בקשת אישור נוסף אחרי לחיצה על איפוס ההגדרות לברירת מחדל
    private void confirmResetSettingsTwoStep() {
        ThemedDialogHelper.showCustomDialog(
                this,
                "איפוס הגדרות לברירת מחדל",
                "האם ברצונך לאפס את הגדרות האפליקציה?",
                null,
                "המשך",
                () -> ThemedDialogHelper.showCustomDialog(
                        this,
                        "אישור נוסף לאיפוס הגדרות",
                        "כל ההעדפות האישיות (גוון תאורה, תצוגה ומיון) יוחזרו לברירת המחדל. לאשר איפוס?",
                        null,
                        "כן, אפס הגדרות",
                        () -> {
                            PrefsManager.resetAll(this);
                            Toast.makeText(this, "כל ההגדרות אופסו לברירת המחדל", Toast.LENGTH_SHORT).show();
                            renderSettingsUi();
                        },
                        "סגור",
                        null,
                        true
                ),
                "סגור",
                null,
                false
        );
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

    private Button createOptionButton(String label, boolean active, int accent, Runnable onClick) {
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
        b.setOnClickListener(v -> onClick.run());
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

    private void showPerCategoryLayoutDialog() {
        final String[][] categories = new String[][]{
                {FileBrowserActivity.MODE_INTERNAL, "אחסון ראשי"},
                {FileBrowserActivity.MODE_SD_CARD, "כרטיס SD"},
                {FileBrowserActivity.MODE_DOWNLOADS, "הורדות"},
                {FileBrowserActivity.MODE_IMAGES, "תמונות"},
                {FileBrowserActivity.MODE_AUDIO, "שמע"},
                {FileBrowserActivity.MODE_VIDEOS, "סירטונים"},
                {FileBrowserActivity.MODE_DOCUMENTS, "מסמכים"},
                {FileBrowserActivity.MODE_APPS, "אפליקציות"},
                {FileBrowserActivity.MODE_RECENT, "קבצים חדשים"},
                {FileBrowserActivity.MODE_RECYCLE_BIN, "סל מיחזור"}
        };

        List<String> labels = new ArrayList<>();
        for (String[] cat : categories) {
            boolean grid = PrefsManager.isGridLayoutForMode(this, cat[0]);
            labels.add(cat[1] + "  •  " + (grid ? "תצוגת רשת" : "תצוגת רשימה"));
        }

        ThemedDialogHelper.showThemedMenu(
                this,
                "תצוגת רשת / רשימה לפי קטגוריה",
                "בחר קטגוריה כדי לשנות בין תצוגת רשת לתצוגת רשימה:",
                labels,
                which -> {
                    final String modeKey = categories[which][0];
                    final String modeName = categories[which][1];
                    ThemedDialogHelper.showThemedMenu(
                            this,
                            "אופן תצוגה: " + modeName,
                            "בחר את התצוגה הרצויה עבור קטגוריית " + modeName + ":",
                            Arrays.asList("תצוגת רשת", "תצוגת רשימה"),
                            choice -> {
                                boolean chooseGrid = (choice == 0);
                                PrefsManager.setGridLayoutForMode(this, modeKey, chooseGrid);
                                renderSettingsUi();
                                Toast.makeText(this,
                                        modeName + ": עודכן ל" + (chooseGrid ? "תצוגת רשת" : "תצוגת רשימה"),
                                        Toast.LENGTH_SHORT).show();
                            }
                    );
                }
        );
    }

    private void showSortModeDialog() {
        String[] modes = {"לפי שם (עברית תחילה)", "לפי משקל קובץ", "לפי תאריך שינוי", "לפי סוג קובץ"};
        ThemedDialogHelper.showThemedMenu(
                this,
                "מיון ברירת מחדל",
                "בחר כיצד למיין את תוכן התיקיות והקטגוריות:",
                Arrays.asList(modes),
                which -> {
                    PrefsManager.setSortMode(this, which);
                    Toast.makeText(this, "המיון עודכן: " + modes[which], Toast.LENGTH_SHORT).show();
                }
        );
    }

    private void openAppSystemSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }
}
