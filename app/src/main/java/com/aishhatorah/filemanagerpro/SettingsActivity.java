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
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * סעיפים 6, 7, 8, 27, 32, 33, 37, 38, 40, 44:
 * מסך הגדרות מינימליסטי ומקצועי עם ערכות נושא מורחבות, בחירת צבע מרכזי,
 * גודל תצוגה, בחירת מנוע פנימי/מערכת, מחוות אוטומטיות, סידור מסך בית, איפוס אפליקציות ברירת מחדל,
 * הסתרת סעיף הרשאות כאשר מאושרות וקישור מהיר לאבחון שגיאות.
 */
public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        renderSettingsUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderSettingsUi();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void renderSettingsUi() {
        int theme = PrefsManager.getThemeMode(this);
        ThemeHelper.applyWindowStatusBar(this, theme);

        int bgColor = ThemeHelper.getBgColor(this, theme);
        int cardColor = ThemeHelper.getCardColor(this, theme);
        int textPrimary = ThemeHelper.getPrimaryTextColor(this, theme);
        int textSecondary = ThemeHelper.getSecondaryTextColor(this, theme);
        int accent = ThemeHelper.getAccentColor(this, theme);
        int divider = ThemeHelper.getDividerColor(this, theme);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(bgColor);

        // סרגל כותרת עליון מינימליסטי
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(ThemeHelper.getTopBarColor(this, theme));
        topBar.setPadding(dp(12), dp(10), dp(12), dp(10));

        Button btnBack = new Button(this);
        btnBack.setText("חזרה");
        btnBack.setAllCaps(false);
        btnBack.setTextColor(accent);
        btnBack.setTextSize(ThemeHelper.spScaled(this, 14f));
        btnBack.setTypeface(Typeface.DEFAULT_BOLD);
        btnBack.setBackgroundColor(Color.TRANSPARENT);
        btnBack.setMinHeight(0);
        btnBack.setMinimumHeight(0);
        btnBack.setOnClickListener(v -> finish());
        topBar.addView(btnBack);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("הגדרות");
        tvTitle.setTextSize(ThemeHelper.spScaled(this, 17.5f));
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setTextColor(textPrimary);
        LinearLayout.LayoutParams lpTitle = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lpTitle.setMarginStart(dp(8));
        topBar.addView(tvTitle, lpTitle);
        root.addView(topBar);

        View topDiv = new View(this);
        topDiv.setBackgroundColor(divider);
        root.addView(topDiv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1))));

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(12), dp(14), dp(26));

        // 1. ערכות נושא וצבע מרכזי + גודל תצוגה (סעיפים 6, 7, 8)
        addSectionTitle(body, "מראה, ערכת נושא וגודל תצוגה", accent);
        LinearLayout themeCard = createSectionCard(cardColor, divider);

        TextView tvThemeSub = new TextView(this);
        tvThemeSub.setText("ערכת נושא פעילה: " + ThemeHelper.getThemeNameHe(theme));
        tvThemeSub.setTextColor(textSecondary);
        tvThemeSub.setTextSize(ThemeHelper.spScaled(this, 12.5f));
        tvThemeSub.setPadding(0, 0, 0, dp(10));
        themeCard.addView(tvThemeSub);

        int[] themeIds = {
                PrefsManager.THEME_LIGHT,
                PrefsManager.THEME_DARK,
                PrefsManager.THEME_AMOLED,
                PrefsManager.THEME_OCEAN,
                PrefsManager.THEME_FOREST,
                PrefsManager.THEME_NEON,
                PrefsManager.THEME_SYSTEM
        };
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < themeIds.length; i++) {
            final int tId = themeIds[i];
            Button b = createOptionButton(ThemeHelper.getThemeNameHe(tId).replace(" (AMOLED)", "").replace(" (דינאמי)", ""),
                    theme == tId, accent, divider, () -> {
                        PrefsManager.setThemeMode(this, tId);
                        renderSettingsUi();
                    });
            if (i < 4) row1.addView(b);
            else row2.addView(b);
        }
        themeCard.addView(row1);
        LinearLayout.LayoutParams r2Lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        r2Lp.topMargin = dp(6);
        themeCard.addView(row2, r2Lp);

        // צבע מרכזי מותאם
        TextView tvAccentLabel = new TextView(this);
        tvAccentLabel.setText("צבע דגש מרכזי בערכת הנושא:");
        tvAccentLabel.setTextColor(textPrimary);
        tvAccentLabel.setTextSize(ThemeHelper.spScaled(this, 13f));
        tvAccentLabel.setTypeface(Typeface.DEFAULT_BOLD);
        tvAccentLabel.setPadding(0, dp(14), 0, dp(8));
        themeCard.addView(tvAccentLabel);

        LinearLayout swatchRow = new LinearLayout(this);
        swatchRow.setOrientation(LinearLayout.HORIZONTAL);
        swatchRow.setGravity(Gravity.CENTER_VERTICAL);
        int currentCustomAccent = PrefsManager.getCustomAccentColor(this);
        int[] swatches = {
                0,
                Color.parseColor("#1D4ED8"),
                Color.parseColor("#0284C7"),
                Color.parseColor("#059669"),
                Color.parseColor("#7C3AED"),
                Color.parseColor("#E11D48"),
                Color.parseColor("#D97706")
        };
        for (int swColor : swatches) {
            View dot = new View(this);
            int sz = dp(28);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(sz, sz);
            dlp.setMarginEnd(dp(8));
            dot.setLayoutParams(dlp);
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            gd.setColor(swColor == 0 ? ThemeHelper.getAccentColor(theme) : swColor);
            boolean sel = currentCustomAccent == swColor;
            gd.setStroke(sel ? dp(3) : dp(1), sel ? textPrimary : divider);
            dot.setBackground(gd);
            dot.setOnClickListener(v -> {
                PrefsManager.setCustomAccentColor(this, swColor);
                Toast.makeText(this, swColor == 0 ? "חזר לצבע ברירת המחדל של הערכה" : "צבע הדגש עודכן", Toast.LENGTH_SHORT).show();
                renderSettingsUi();
            });
            swatchRow.addView(dot);
        }
        themeCard.addView(swatchRow);

        // סקאלת גודל תצוגה (סעיף 6)
        float currentScale = PrefsManager.getUiScale(this);
        TextView tvScaleLabel = new TextView(this);
        tvScaleLabel.setText(String.format(Locale.US, "גודל תצוגה וטקסט באפליקציה: %.0f%%", currentScale * 100f));
        tvScaleLabel.setTextColor(textPrimary);
        tvScaleLabel.setTextSize(ThemeHelper.spScaled(this, 13f));
        tvScaleLabel.setTypeface(Typeface.DEFAULT_BOLD);
        tvScaleLabel.setPadding(0, dp(14), 0, dp(4));
        themeCard.addView(tvScaleLabel);

        SeekBar sbScale = new SeekBar(this);
        sbScale.setMax(40); // 0.85 .. 1.25
        sbScale.setProgress(Math.max(0, Math.min(40, Math.round((currentScale - 0.85f) * 100f))));
        sbScale.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float val = 0.85f + (progress / 100f);
                tvScaleLabel.setText(String.format(Locale.US, "גודל תצוגה וטקסט באפליקציה: %.0f%%", val * 100f));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                float val = 0.85f + (seekBar.getProgress() / 100f);
                PrefsManager.setUiScale(SettingsActivity.this, val);
                renderSettingsUi();
            }
        });
        themeCard.addView(sbScale);
        body.addView(themeCard);

        // 2. מנועי צפייה, נגן מדיה ומחוות (סעיפים 6, 44)
        addSectionTitle(body, "מנועי מדיה ומחוות הפעלה", accent);
        LinearLayout mediaCard = createSectionCard(cardColor, divider);

        addSwitchRow(mediaCard,
                "פתח סירטונים בנגן המובנה של האפליקציה",
                "כבה כדי לפתוח קבצי וידאו דרך נגן המערכת / אפליקציה חיצונית",
                PrefsManager.isUseInternalVideoPlayer(this),
                textPrimary, textSecondary, divider,
                checked -> PrefsManager.setUseInternalVideoPlayer(this, checked));

        addSwitchRow(mediaCard,
                "פתח תמונות ו-GIF במציג המובנה",
                "מציג זום עמוק, תמיכה ב-GIF מונפש, קביעת טפט והגנת מסך מלא",
                PrefsManager.isUseInternalImageViewer(this),
                textPrimary, textSecondary, divider,
                checked -> PrefsManager.setUseInternalImageViewer(this, checked));

        addSwitchRow(mediaCard,
                "פתח שמע (אודיו) בנגן המובנה",
                "נגן רקע מלא עם תור שירים, טיימר שינה ואקולייזר",
                PrefsManager.isUseInternalAudioPlayer(this),
                textPrimary, textSecondary, divider,
                checked -> PrefsManager.setUseInternalAudioPlayer(this, checked));

        addSwitchRow(mediaCard,
                "מצב מחוות אוטומטי בנגן (כמו סטטוס)",
                "לחיצה קלה מציגה/מסתירה כפתורים; לחיצה ארוכה בצד ימין מריצה ×2",
                PrefsManager.isAutoGestureMode(this),
                textPrimary, textSecondary, divider,
                checked -> PrefsManager.setAutoGestureMode(this, checked));
        body.addView(mediaCard);

        // 3. מסך בית, ניווט וגיבויי .bak (סעיפים 33, 38, 40)
        addSectionTitle(body, "מסך הבית, סייר ועורך קבצים", accent);
        LinearLayout homeCard = createSectionCard(cardColor, divider);

        homeCard.addView(createSubtleButton("סידור והסתרת קטגוריות במסך הבית", cardColor, accent,
                v -> startActivity(new Intent(this, HomeLayoutActivity.class))));

        addSwitchRow(homeCard,
                "גישה מהירה לתיקייה האחרונה במסך הבית",
                "הצג כרטיס חזרה מהירה למיקום האחרון בו ביקרת בסייר",
                PrefsManager.isQuickAccessEnabled(this),
                textPrimary, textSecondary, divider,
                checked -> PrefsManager.setQuickAccessEnabled(this, checked));

        addSwitchRow(homeCard,
                "יצירת קובץ גיבוי .bak לפני שמירה בעורך",
                "שומר עותק של הקובץ המקורי עם סיומת .bak לפני כל שמירת עריכה",
                PrefsManager.isCreateBackupOnSave(this),
                textPrimary, textSecondary, divider,
                checked -> PrefsManager.setCreateBackupOnSave(this, checked));
        body.addView(homeCard);

        // 4. תצוגה בתיקיות ומיון (סעיפים ג, 5)
        addSectionTitle(body, "תצוגת תיקיות, קטגוריות ומיון", accent);
        LinearLayout displayCard = createSectionCard(cardColor, divider);

        boolean isGrid = PrefsManager.isGridLayout(this);
        LinearLayout viewModeRow = new LinearLayout(this);
        viewModeRow.setOrientation(LinearLayout.HORIZONTAL);
        viewModeRow.addView(createOptionButton("תצוגת רשימה", !isGrid, accent, divider, () -> {
            PrefsManager.setGeneralGridLayoutAndSyncNonMedia(this, false);
            renderSettingsUi();
        }));
        viewModeRow.addView(createOptionButton("תצוגת רשת", isGrid, accent, divider, () -> {
            PrefsManager.setGeneralGridLayoutAndSyncNonMedia(this, true);
            renderSettingsUi();
        }));
        displayCard.addView(viewModeRow);

        displayCard.addView(createSubtleButton("תצוגת רשת / רשימה לכל קטגוריה בנפרד", cardColor, accent,
                v -> showPerCategoryLayoutDialog()));

        addSwitchRow(displayCard,
                "הצגת קבצים מוסתרים",
                "הצג קבצים ותיקיות שמתחילים בנקודה (.)",
                PrefsManager.isShowHidden(this),
                textPrimary, textSecondary, divider,
                checked -> PrefsManager.setShowHidden(this, checked));

        addSwitchRow(displayCard,
                "תצוגה מקדימה לקבצי מדיה ו-APK",
                "טען עטיפות אלבום, פריימים מוידאו וסמלי חבילות APK",
                PrefsManager.isShowThumbnails(this),
                textPrimary, textSecondary, divider,
                checked -> PrefsManager.setShowThumbnails(this, checked));

        addSwitchRow(displayCard,
                "חישוב נפח תיקיות משנה",
                "הצג משקל מצטבר לתיקיות (עלול להאט בתיקיות ענקיות)",
                PrefsManager.isShowFolderSizes(this),
                textPrimary, textSecondary, divider,
                checked -> PrefsManager.setShowFolderSizes(this, checked));

        addSwitchRow(displayCard,
                "סדר מיון עולה (א-ת / מהקטן לגדול)",
                "כבה למיון בסדר יורד (הגדול או החדש ביותר ראשון)",
                PrefsManager.isSortAscending(this),
                textPrimary, textSecondary, divider,
                checked -> PrefsManager.setSortAscending(this, checked));

        displayCard.addView(createSubtleButton("שינוי שדה מיון ברירת מחדל", cardColor, accent,
                v -> showSortModeDialog()));
        body.addView(displayCard);

        // 5. אפליקציות ברירת מחדל ("פתיחה באמצעות" -> תמיד, סעיף 32)
        addSectionTitle(body, "אפליקציות ברירת מחדל לסוגי קבצים", accent);
        LinearLayout defaultsCard = createSectionCard(cardColor, divider);
        Map<String, String> savedDefaults = PrefsManager.getAllDefaultOpenActions(this);
        TextView tvDefInfo = new TextView(this);
        tvDefInfo.setText(savedDefaults.isEmpty()
                ? "לא הוגדרו סיומות קבצים קבועות. ניתן לקבוע ברירת מחדל מתפריט 'פתיחה באמצעות'."
                : "מוגדרות " + savedDefaults.size() + " סיומות קבצים עם פתיחה קבועה.");
        tvDefInfo.setTextColor(textSecondary);
        tvDefInfo.setTextSize(ThemeHelper.spScaled(this, 12.5f));
        defaultsCard.addView(tvDefInfo);
        if (!savedDefaults.isEmpty()) {
            defaultsCard.addView(createSubtleButton("ניהול ואיפוס ברירות מחדל לסיומות", cardColor, accent,
                    v -> showDefaultOpenManagerDialog(savedDefaults)));
        }
        body.addView(defaultsCard);

        // 6. אחסון, ניתוח וייצור סל מיחזור
        addSectionTitle(body, "ניתוח נפחים, התרעת 96% וסל מיחזור", accent);
        LinearLayout analyzerCard = createSectionCard(cardColor, divider);

        analyzerCard.addView(createSubtleButton("ניתוח — איתור הקבצים הכבדים ביותר", cardColor, accent,
                v -> startActivity(new Intent(this, StorageAnalyzerActivity.class))));

        addSwitchRow(analyzerCard,
                "התרעה אוטומטית ב-96% תפוסה",
                "מתריע כאשר הזיכרון הפנימי או הכרטיס מתקרבים למילוי מלא",
                PrefsManager.isAlert96Enabled(this),
                textPrimary, textSecondary, divider,
                checked -> {
                    PrefsManager.setAlert96Enabled(this, checked);
                    if (checked) StorageUtils.checkAndNotify96Threshold(this);
                });

        addSwitchRow(analyzerCard,
                "העברה לסל המיחזור בעת מחיקה",
                "שמור קבצים שנמחקו לצורך שחזור עתידי",
                PrefsManager.isUseRecycleBin(this),
                textPrimary, textSecondary, divider,
                checked -> PrefsManager.setUseRecycleBin(this, checked));

        addSwitchRow(analyzerCard,
                "בקשת אישור לפני מחיקה",
                "אישור בטיחותי לפני העברה לסל או מחיקה",
                PrefsManager.isConfirmDelete(this),
                textPrimary, textSecondary, divider,
                checked -> PrefsManager.setConfirmDelete(this, checked));

        int trashCount = RecycleBinManager.getTrashItemCount(this);
        String trashSize = StorageUtils.formatSize(RecycleBinManager.getTrashTotalBytes(this));
        analyzerCard.addView(createSubtleButton(
                "ריקון סל המיחזור (" + trashCount + " פריטים • " + trashSize + ")",
                cardColor, Color.parseColor("#DC2626"),
                v -> confirmEmptyRecycleBinTwoStep()));
        body.addView(analyzerCard);

        // 7. סעיף 27: הסתרת סעיף הרשאות כאשר ההרשאות מאושרות ופעילות
        boolean allFilesGranted = StorageUtils.hasAllStoragePermissions(this);
        boolean notifGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;

        if (!allFilesGranted || !notifGranted) {
            addSectionTitle(body, "הרשאות גישה נדרשות", Color.parseColor("#D97706"));
            LinearLayout permsCard = createSectionCard(cardColor, divider);
            if (!allFilesGranted) {
                permsCard.addView(createSubtleButton("אשר הרשאת גישה לכל הקבצים והאחסון", cardColor, accent, v -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:" + getPackageName())));
                    } else {
                        openAppSystemSettings();
                    }
                }));
            }
            if (!notifGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permsCard.addView(createSubtleButton("אשר הרשאת התראות (נגן ופעולות רקע)", cardColor, accent, v ->
                        androidx.core.app.ActivityCompat.requestPermissions(
                                this,
                                new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                                2044
                        )));
            }
            body.addView(permsCard);
        }

        // 8. עידכונים, יומן שגיאות ואיפוס
        addSectionTitle(body, "עידכונים, יומן אבחון ואיפוס", accent);
        LinearLayout updateCard = createSectionCard(cardColor, divider);

        addSwitchRow(updateCard,
                "בדיקת עדכונים אוטומטית בהפעלה",
                "כולל תמיכה מאובטחת בתעודות רשת מסוננת (נטפרי) עבור GitHub בלבד",
                PrefsManager.isAutoCheckUpdates(this),
                textPrimary, textSecondary, divider,
                checked -> PrefsManager.setAutoCheckUpdates(this, checked));

        updateCard.addView(createSubtleButton(
                "בדיקת עדכון כעת",
                cardColor, accent,
                v -> UpdateChecker.checkForUpdates(this, true)));

        updateCard.addView(createSubtleButton(
                "יומן שגיאות ואבחון תקלות",
                cardColor, textPrimary,
                v -> startActivity(new Intent(this, DiagnosticsActivity.class))));

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

    private void showDefaultOpenManagerDialog(Map<String, String> savedDefaults) {
        final List<String> exts = new ArrayList<>(savedDefaults.keySet());
        List<String> rows = new ArrayList<>();
        for (String ext : exts) {
            rows.add(ext + "  ->  " + friendlyActionName(savedDefaults.get(ext)));
        }
        rows.add("איפוס כל ברירות המחדל");
        ThemedDialogHelper.showThemedMenu(
                this,
                "אפליקציות ברירת מחדל לפי סיומת",
                "הקש על סיומת כדי לאפס את הבחירה הקבועה שלה:",
                rows,
                idx -> {
                    if (idx == rows.size() - 1) {
                        PrefsManager.clearAllDefaultOpenActions(this);
                        Toast.makeText(this, "כל ברירות המחדל אופסו", Toast.LENGTH_SHORT).show();
                    } else {
                        String ext = exts.get(idx);
                        PrefsManager.setDefaultOpenAction(this, ext, null);
                        Toast.makeText(this, "הוסרה ברירת המחדל לסיומת " + ext, Toast.LENGTH_SHORT).show();
                    }
                    renderSettingsUi();
                }
        );
    }

    private String friendlyActionName(String action) {
        if (FileOpenWithHelper.ACTION_INTERNAL_CODE.equals(action)) return "עורך טקסט וקוד מובנה";
        if (FileOpenWithHelper.ACTION_INTERNAL_IMAGE.equals(action)) return "מציג תמונות מובנה";
        if (FileOpenWithHelper.ACTION_INTERNAL_AUDIO.equals(action)) return "נגן שמע מובנה";
        if (FileOpenWithHelper.ACTION_INTERNAL_VIDEO.equals(action)) return "נגן וידאו מובנה";
        if (FileOpenWithHelper.ACTION_INTERNAL_ARCHIVE.equals(action)) return "מנהל ארכיונים מובנה";
        if (FileOpenWithHelper.ACTION_SYSTEM_APP.equals(action)) return "אפליקציית מערכת חיצונית";
        return action == null ? "" : action;
    }

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
                        "כל ההעדפות האישיות (ערכת נושא, תצוגה ומיון) יוחזרו לברירת המחדל. לאשר איפוס?",
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
        tv.setTextSize(ThemeHelper.spScaled(this, 13.5f));
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(dp(4), dp(14), dp(4), dp(6));
        parent.addView(tv);
    }

    private LinearLayout createSectionCard(int cardColor, int divider) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(14));
        shape.setColor(cardColor);
        shape.setStroke(Math.max(1, dp(1)), divider);
        card.setBackground(shape);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(6);
        card.setLayoutParams(lp);
        return card;
    }

    private Button createOptionButton(String label, boolean active, int accent, int divider, Runnable onClick) {
        int theme = PrefsManager.getThemeMode(this);
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(ThemeHelper.spScaled(this, 12f));
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(active
                ? (theme == PrefsManager.THEME_NEON ? Color.parseColor("#04101A") : Color.WHITE)
                : ThemeHelper.getPrimaryTextColor(this, theme));

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(10));
        bg.setColor(active ? accent : Color.TRANSPARENT);
        bg.setStroke(Math.max(1, dp(1)), active ? accent : divider);
        b.setBackground(bg);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(6), dp(8), dp(6), dp(8));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(3), 0, dp(3), 0);
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> onClick.run());
        return b;
    }

    private interface SwitchCallback {
        void onChanged(boolean checked);
    }

    private void addSwitchRow(LinearLayout parent, String title, String subtitle, boolean initial,
                              int textPrimary, int textSecondary, int divider, SwitchCallback cb) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView tvT = new TextView(this);
        tvT.setText(title);
        tvT.setTextColor(textPrimary);
        tvT.setTextSize(ThemeHelper.spScaled(this, 13.5f));
        tvT.setTypeface(Typeface.DEFAULT_BOLD);
        textCol.addView(tvT);

        TextView tvS = new TextView(this);
        tvS.setText(subtitle);
        tvS.setTextColor(textSecondary);
        tvS.setTextSize(ThemeHelper.spScaled(this, 11.5f));
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
        b.setTextSize(ThemeHelper.spScaled(this, 13f));
        b.setTypeface(Typeface.DEFAULT_BOLD);

        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(10));
        shape.setColor(cardColor);
        shape.setStroke(Math.max(1, dp(1)), textColor);
        b.setBackground(shape);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(12), dp(9), dp(12), dp(9));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
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
            labels.add(cat[1] + "  •  " + (grid ? "רשת" : "רשימה"));
        }

        ThemedDialogHelper.showThemedMenu(
                this,
                "תצוגת רשת / רשימה לפי קטגוריה",
                "בחר קטגוריה כדי לשנות את תצוגתה:",
                labels,
                which -> {
                    final String modeKey = categories[which][0];
                    final String modeName = categories[which][1];
                    ThemedDialogHelper.showThemedMenu(
                            this,
                            "אופן תצוגה: " + modeName,
                            "בחר את התצוגה הרצויה עבור " + modeName + ":",
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
        final String[] modes = {
                "לפי שם (עברית תחילה)",
                "לפי משקל קובץ",
                "לפי תאריך שינוי",
                "לפי סוג קובץ",
                "לפי תאריך יצירה"
        };
        final int[] modeValues = {
                PrefsManager.SORT_NAME,
                PrefsManager.SORT_SIZE,
                PrefsManager.SORT_DATE,
                PrefsManager.SORT_TYPE,
                PrefsManager.SORT_CREATED
        };
        ThemedDialogHelper.showThemedMenu(
                this,
                "מיון ברירת מחדל",
                "בחר כיצד למיין קבצים ותיקיות:",
                Arrays.asList(modes),
                which -> {
                    PrefsManager.setSortMode(this, modeValues[which]);
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
