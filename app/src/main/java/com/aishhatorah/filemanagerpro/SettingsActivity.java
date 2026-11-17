package com.aishhatorah.filemanagerpro;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.ColorUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * מסך הגדרות בעיצוב זכוכית מודרני עם בחירת רקע מטושטש לדף הבית והתיקיות,
 * שליטה בהצגת שמות קטגוריות, ערכות נושא, צבע מרכזי וסקאלת תצוגה.
 */
public class SettingsActivity extends AppCompatActivity {

    private static final int REQ_PICK_WALLPAPER = 4102;
    private ScrollView activeScroll;
    private int savedScrollY = 0;

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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_WALLPAPER && resultCode == RESULT_OK && data != null && data.getData() != null) {
            savePickedWallpaper(data.getData());
        }
    }

    private void savePickedWallpaper(Uri uri) {
        new Thread(() -> {
            try {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                try (InputStream is = getContentResolver().openInputStream(uri)) {
                    if (is == null) return;
                    BitmapFactory.decodeStream(is, null, bounds);
                }
                int maxDim = Math.max(bounds.outWidth, bounds.outHeight);
                int sample = 1;
                while (maxDim / sample > 1600) {
                    sample *= 2;
                }
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = sample;
                opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
                Bitmap bmp;
                try (InputStream is2 = getContentResolver().openInputStream(uri)) {
                    if (is2 == null) return;
                    bmp = BitmapFactory.decodeStream(is2, null, opts);
                }
                if (bmp == null) {
                    runOnUiThread(() -> Toast.makeText(this, "לא ניתן לקרוא את התמונה שנבחרה", Toast.LENGTH_SHORT).show());
                    return;
                }
                File dest = new File(getFilesDir(), "home_wallpaper.jpg");
                try (FileOutputStream fos = new FileOutputStream(dest, false)) {
                    bmp.compress(Bitmap.CompressFormat.JPEG, 88, fos);
                }
                bmp.recycle();
                ThemeHelper.invalidateWallpaperCache();
                PrefsManager.setHomeWallpaperPath(this, dest.getAbsolutePath());
                runOnUiThread(() -> {
                    Toast.makeText(this, "תמונת הרקע עודכנה לדף הבית ולתיקיות", Toast.LENGTH_SHORT).show();
                    renderSettingsUi();
                });
            } catch (Throwable t) {
                AppLogger.error(this, "SettingsWallpaper", "Failed to save wallpaper", t);
                runOnUiThread(() -> Toast.makeText(this, "שגיאה בשמירת תמונת הרקע", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void launchWallpaperPicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(intent, "בחר תמונת רקע"), REQ_PICK_WALLPAPER);
        } catch (Throwable t) {
            Toast.makeText(this, "לא נמצא בורר תמונות במכשיר", Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void renderSettingsUi() {
        if (activeScroll != null) {
            savedScrollY = activeScroll.getScrollY();
        }
        int theme = PrefsManager.getThemeMode(this);
        ThemeHelper.applyWindowStatusBar(this, theme);

        int textPrimary = ThemeHelper.getPrimaryTextColor(this, theme);
        int textSecondary = ThemeHelper.getSecondaryTextColor(this, theme);
        int accent = ThemeHelper.getAccentColor(this, theme);
        int divider = ThemeHelper.getDividerColor(this, theme);
        boolean dark = ThemeHelper.isDarkSurface(this, theme);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        ThemeHelper.applyWallpaperBackdrop(this, root, theme);

        // כותרת מעוגלת כלפי מעלה (ללא תמונת רקע בתוכה)
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(10), dp(8), dp(14), dp(10));
        ThemeHelper.applyCurvedHeaderStyle(this, topBar, theme);

        ImageButton btnBack = ThemeHelper.createBorderlessSvgButton(
                this, R.drawable.ic_arrow_back, accent, "חזרה", 40, 8, v -> finish());
        topBar.addView(btnBack);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("הגדרות");
        tvTitle.setTextSize(ThemeHelper.spScaled(this, 17.5f));
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setTextColor(textPrimary);
        LinearLayout.LayoutParams lpTitle = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lpTitle.setMarginStart(dp(6));
        topBar.addView(tvTitle, lpTitle);
        root.addView(topBar);

        ScrollView scroll = new ScrollView(this);
        activeScroll = scroll;
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(10), dp(14), dp(26));

        // 1. ערכות נושא וצבע מרכזי + גודל תצוגה
        addSectionTitle(body, "מראה, ערכת נושא וגודל תצוגה", accent);
        LinearLayout themeCard = createSectionCard(theme);

        TextView tvThemeSub = new TextView(this);
        tvThemeSub.setText("ערכת נושא פעילה: " + ThemeHelper.getThemeNameHe(theme));
        tvThemeSub.setTextColor(textSecondary);
        tvThemeSub.setTextSize(ThemeHelper.spScaled(this, 12.5f));
        tvThemeSub.setPadding(0, 0, 0, dp(10));
        themeCard.addView(tvThemeSub);

        int[] themeIds = {
                PrefsManager.THEME_LIGHT,
                PrefsManager.THEME_SLATE,
                PrefsManager.THEME_DARK,
                PrefsManager.THEME_AMOLED,
                PrefsManager.THEME_OCEAN,
                PrefsManager.THEME_FOREST,
                PrefsManager.THEME_NEON,
                PrefsManager.THEME_ROSE,
                PrefsManager.THEME_PURPLE,
                PrefsManager.THEME_AMBER,
                PrefsManager.THEME_TEAL,
                PrefsManager.THEME_SYSTEM
        };
        for (int rowStart = 0; rowStart < themeIds.length; rowStart += 3) {
            LinearLayout tRow = new LinearLayout(this);
            tRow.setOrientation(LinearLayout.HORIZONTAL);
            tRow.setWeightSum(3f);
            int end = Math.min(themeIds.length, rowStart + 3);
            for (int i = rowStart; i < end; i++) {
                final int tId = themeIds[i];
                View cardItem = createEqualThemeCard(
                        tId,
                        ThemeHelper.getThemeNameHe(tId),
                        theme == tId,
                        accent,
                        divider,
                        dark,
                        () -> {
                            PrefsManager.setThemeMode(this, tId);
                            renderSettingsUi();
                        });
                tRow.addView(cardItem);
            }
            LinearLayout.LayoutParams trLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (rowStart > 0) trLp.topMargin = dp(8);
            themeCard.addView(tRow, trLp);
        }

        // צבע מרכזי מותאם
        TextView tvAccentLabel = new TextView(this);
        tvAccentLabel.setText("צבע דגש מרכזי בערכת הנושא:");
        tvAccentLabel.setTextColor(textPrimary);
        tvAccentLabel.setTextSize(ThemeHelper.spScaled(this, 13f));
        tvAccentLabel.setTypeface(Typeface.DEFAULT_BOLD);
        tvAccentLabel.setPadding(0, dp(14), 0, dp(8));
        themeCard.addView(tvAccentLabel);

        int currentCustomAccent = PrefsManager.getCustomAccentColor(this);
        int[] swatches = {
                0,
                Color.parseColor("#1D4ED8"),
                Color.parseColor("#0284C7"),
                Color.parseColor("#0891B2"),
                Color.parseColor("#0D9488"),
                Color.parseColor("#059669"),
                Color.parseColor("#16A34A"),
                Color.parseColor("#65A30D"),
                Color.parseColor("#D97706"),
                Color.parseColor("#EA580C"),
                Color.parseColor("#DC2626"),
                Color.parseColor("#E11D48"),
                Color.parseColor("#DB2777"),
                Color.parseColor("#9333EA"),
                Color.parseColor("#7C3AED"),
                Color.parseColor("#4F46E5")
        };
        for (int rowStart = 0; rowStart < swatches.length; rowStart += 8) {
            LinearLayout swatchRow = new LinearLayout(this);
            swatchRow.setOrientation(LinearLayout.HORIZONTAL);
            swatchRow.setGravity(Gravity.CENTER_VERTICAL);
            int end = Math.min(swatches.length, rowStart + 8);
            for (int i = rowStart; i < end; i++) {
                final int swColor = swatches[i];
                View dot = new View(this);
                int sz = dp(26);
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
            LinearLayout.LayoutParams srLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (rowStart > 0) srLp.topMargin = dp(8);
            themeCard.addView(swatchRow, srLp);
        }

        // סקאלת גודל תצוגה
        float currentScale = PrefsManager.getUiScale(this);
        LinearLayout scaleHeaderRow = new LinearLayout(this);
        scaleHeaderRow.setOrientation(LinearLayout.HORIZONTAL);
        scaleHeaderRow.setGravity(Gravity.CENTER_VERTICAL);
        scaleHeaderRow.setPadding(0, dp(14), 0, dp(4));

        TextView tvScaleLabel = new TextView(this);
        tvScaleLabel.setText(String.format(Locale.US, "גודל תצוגה וטקסט באפליקציה: %.0f%%", currentScale * 100f));
        tvScaleLabel.setTextColor(textPrimary);
        tvScaleLabel.setTextSize(ThemeHelper.spScaled(this, 13f));
        tvScaleLabel.setTypeface(Typeface.DEFAULT_BOLD);
        scaleHeaderRow.addView(tvScaleLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        boolean isScaleDefault = Math.abs(currentScale - 1.0f) < 0.015f;
        ImageButton btnResetScale = ThemeHelper.createBorderlessSvgButton(
                this,
                R.drawable.ic_reset,
                isScaleDefault ? textSecondary : accent,
                "איפוס לגודל ברירת המחדל",
                32,
                6,
                v -> {
                    PrefsManager.setUiScale(this, 1.0f);
                    Toast.makeText(this, "גודל הטקסט והתצוגה אופס לברירת המחדל (100%)", Toast.LENGTH_SHORT).show();
                    renderSettingsUi();
                });
        btnResetScale.setAlpha(isScaleDefault ? 0.5f : 1.0f);
        scaleHeaderRow.addView(btnResetScale);
        themeCard.addView(scaleHeaderRow);

        SeekBar sbScale = new SeekBar(this);
        ThemeHelper.applySeekBarTint(sbScale, accent);
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

        // 2. מנועי צפייה, נגן מדיה ומחוות
        addSectionTitle(body, "מנועי מדיה ומחוות הפעלה", accent);
        LinearLayout mediaCard = createSectionCard(theme);

        addSwitchRow(mediaCard,
                "פתח סירטונים בנגן המובנה של האפליקציה",
                "כבה כדי לפתוח קבצי וידאו דרך נגן המערכת / אפליקציה חיצונית",
                PrefsManager.isUseInternalVideoPlayer(this),
                textPrimary, textSecondary,
                checked -> PrefsManager.setUseInternalVideoPlayer(this, checked));

        addSwitchRow(mediaCard,
                "פתח תמונות ו-GIF במציג המובנה",
                "מציג זום עמוק, תמיכה ב-GIF מונפש, קביעת טפט והגנת מסך מלא",
                PrefsManager.isUseInternalImageViewer(this),
                textPrimary, textSecondary,
                checked -> PrefsManager.setUseInternalImageViewer(this, checked));

        addSwitchRow(mediaCard,
                "פתח שמע (אודיו) בנגן המובנה",
                "נגן רקע מלא עם תור שירים, טיימר שינה ואקולייזר",
                PrefsManager.isUseInternalAudioPlayer(this),
                textPrimary, textSecondary,
                checked -> PrefsManager.setUseInternalAudioPlayer(this, checked));

        addSwitchRow(mediaCard,
                "מצב מחוות אוטומטי בנגן (כמו סטטוס)",
                "לחיצה קלה מציגה/מסתירה כפתורים; לחיצה ארוכה בצד ימין מריצה ×2",
                PrefsManager.isAutoGestureMode(this),
                textPrimary, textSecondary,
                checked -> PrefsManager.setAutoGestureMode(this, checked));
        body.addView(mediaCard);

        // 3. מסך בית, רקע זכוכית, ניווט וגיבויי .bak
        addSectionTitle(body, "מסך הבית, רקע וסייר קבצים", accent);
        LinearLayout homeCard = createSectionCard(theme);

        homeCard.addView(createSubtleButton("סידור והסתרת קטגוריות במסך הבית", accent, dark,
                v -> startActivity(new Intent(this, HomeLayoutActivity.class))));

        homeCard.addView(createSubtleButton(
                ThemeHelper.hasWallpaper(this)
                        ? "החלפת תמונת רקע לדף הבית ולתיקיות"
                        : "בחירת תמונת רקע לדף הבית ולתיקיות",
                accent, dark,
                v -> launchWallpaperPicker()));

        if (ThemeHelper.hasWallpaper(this)) {
            int danger = dark ? Color.parseColor("#F87171") : Color.parseColor("#DC2626");
            homeCard.addView(createSubtleButton("הסר תמונת רקע פעילה", danger, dark, v -> {
                String path = PrefsManager.getHomeWallpaperPath(this);
                if (path != null && !path.isEmpty()) {
                    try {
                        new File(path).delete();
                    } catch (Throwable ignored) {
                    }
                }
                PrefsManager.setHomeWallpaperPath(this, null);
                ThemeHelper.invalidateWallpaperCache();
                Toast.makeText(this, "תמונת הרקע הוסרה", Toast.LENGTH_SHORT).show();
                renderSettingsUi();
            }));
        }

        int curBlur = PrefsManager.getWallpaperBlurRadius(this);
        TextView tvBlurLabel = new TextView(this);
        tvBlurLabel.setText("עוצמת טשטוש תמונת הרקע: " + (curBlur == 0 ? "ללא טשטוש (0%)" : Math.round((curBlur / 28f) * 100f) + "%"));
        tvBlurLabel.setTextColor(textPrimary);
        tvBlurLabel.setTextSize(ThemeHelper.spScaled(this, 13f));
        tvBlurLabel.setTypeface(Typeface.DEFAULT_BOLD);
        tvBlurLabel.setPadding(0, dp(12), 0, dp(4));
        homeCard.addView(tvBlurLabel);

        SeekBar sbBlur = new SeekBar(this);
        ThemeHelper.applySeekBarTint(sbBlur, accent);
        sbBlur.setMax(28);
        sbBlur.setProgress(curBlur);
        sbBlur.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvBlurLabel.setText("עוצמת טשטוש תמונת הרקע: " + (progress == 0 ? "ללא טשטוש (0%)" : Math.round((progress / 28f) * 100f) + "%"));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                PrefsManager.setWallpaperBlurRadius(SettingsActivity.this, seekBar.getProgress());
                ThemeHelper.invalidateWallpaperCache();
                ThemeHelper.applyWallpaperBackdrop(SettingsActivity.this, root, theme);
            }
        });
        homeCard.addView(sbBlur);

        addSwitchRow(homeCard,
                "הצגת שמות קטגוריות במסך הבית",
                "כבה כדי להציג אייקונים גדולים בלבד ללא כיתוב מתחתם",
                PrefsManager.isShowHomeCategoryLabels(this),
                textPrimary, textSecondary,
                checked -> PrefsManager.setShowHomeCategoryLabels(this, checked));

        addSwitchRow(homeCard,
                "גישה מהירה לתיקייה האחרונה במסך הבית",
                "הצג כרטיס חזרה מהירה למיקום האחרון בו ביקרת בסייר",
                PrefsManager.isQuickAccessEnabled(this),
                textPrimary, textSecondary,
                checked -> PrefsManager.setQuickAccessEnabled(this, checked));

        addSwitchRow(homeCard,
                "יצירת קובץ גיבוי .bak לפני שמירה בעורך",
                "שומר עותק של הקובץ המקורי עם סיומת .bak לפני כל שמירת עריכה",
                PrefsManager.isCreateBackupOnSave(this),
                textPrimary, textSecondary,
                checked -> PrefsManager.setCreateBackupOnSave(this, checked));
        body.addView(homeCard);

        // 4. תצוגה בתיקיות ומיון
        addSectionTitle(body, "תצוגת תיקיות, קטגוריות ומיון", accent);
        LinearLayout displayCard = createSectionCard(theme);

        boolean isGrid = PrefsManager.isGridLayout(this);
        LinearLayout viewModeRow = new LinearLayout(this);
        viewModeRow.setOrientation(LinearLayout.HORIZONTAL);
        viewModeRow.addView(createOptionButton("תצוגת רשימה", !isGrid, accent, dark, () -> {
            PrefsManager.setGeneralGridLayoutAndSyncNonMedia(this, false);
            renderSettingsUi();
        }));
        viewModeRow.addView(createOptionButton("תצוגת רשת", isGrid, accent, dark, () -> {
            PrefsManager.setGeneralGridLayoutAndSyncNonMedia(this, true);
            renderSettingsUi();
        }));
        displayCard.addView(viewModeRow);

        displayCard.addView(createSubtleButton("תצוגת רשת / רשימה לכל קטגוריה בנפרד", accent, dark,
                v -> showPerCategoryLayoutDialog()));

        addSwitchRow(displayCard,
                "הצגת קבצים מוסתרים",
                "הצג קבצים ותיקיות שמתחילים בנקודה (.)",
                PrefsManager.isShowHidden(this),
                textPrimary, textSecondary,
                checked -> PrefsManager.setShowHidden(this, checked));

        addSwitchRow(displayCard,
                "תצוגה מקדימה לקבצי מדיה ו-APK",
                "טען עטיפות אלבום, פריימים מוידאו וסמלי חבילות APK",
                PrefsManager.isShowThumbnails(this),
                textPrimary, textSecondary,
                checked -> PrefsManager.setShowThumbnails(this, checked));

        addSwitchRow(displayCard,
                "חישוב נפח תיקיות משנה",
                "הצג משקל מצטבר לתיקיות (עלול להאט בתיקיות ענקיות)",
                PrefsManager.isShowFolderSizes(this),
                textPrimary, textSecondary,
                checked -> PrefsManager.setShowFolderSizes(this, checked));

        addSwitchRow(displayCard,
                "סדר מיון עולה (א-ת / מהקטן לגדול)",
                "כבה למיון בסדר יורד (הגדול או החדש ביותר ראשון)",
                PrefsManager.isSortAscending(this),
                textPrimary, textSecondary,
                checked -> PrefsManager.setSortAscending(this, checked));

        displayCard.addView(createSubtleButton("שינוי שדה מיון ברירת מחדל", accent, dark,
                v -> showSortModeDialog()));
        body.addView(displayCard);

        // 5. אפליקציות ברירת מחדל
        addSectionTitle(body, "אפליקציות ברירת מחדל לסוגי קבצים", accent);
        LinearLayout defaultsCard = createSectionCard(theme);
        Map<String, String> savedDefaults = PrefsManager.getAllDefaultOpenActions(this);
        TextView tvDefInfo = new TextView(this);
        tvDefInfo.setText(savedDefaults.isEmpty()
                ? "לא הוגדרו סיומות קבצים קבועות. ניתן לקבוע ברירת מחדל מתפריט 'פתיחה באמצעות'."
                : "מוגדרות " + savedDefaults.size() + " סיומות קבצים עם פתיחה קבועה.");
        tvDefInfo.setTextColor(textSecondary);
        tvDefInfo.setTextSize(ThemeHelper.spScaled(this, 12.5f));
        defaultsCard.addView(tvDefInfo);
        if (!savedDefaults.isEmpty()) {
            defaultsCard.addView(createSubtleButton("ניהול ואיפוס ברירות מחדל לסיומות", accent, dark,
                    v -> showDefaultOpenManagerDialog(savedDefaults)));
        }
        body.addView(defaultsCard);

        // 6. אחסון, ניתוח וסל מיחזור
        addSectionTitle(body, "ניתוח נפחים, התרעת 96% וסל מיחזור", accent);
        LinearLayout analyzerCard = createSectionCard(theme);

        analyzerCard.addView(createSubtleButton("ניתוח — איתור הקבצים הכבדים ביותר", accent, dark,
                v -> startActivity(new Intent(this, StorageAnalyzerActivity.class))));

        addSwitchRow(analyzerCard,
                "התרעה אוטומטית ב-96% תפוסה",
                "מתריע כאשר הזיכרון הפנימי או הכרטיס מתקרבים למילוי מלא",
                PrefsManager.isAlert96Enabled(this),
                textPrimary, textSecondary,
                checked -> {
                    PrefsManager.setAlert96Enabled(this, checked);
                    if (checked) StorageUtils.checkAndNotify96Threshold(this);
                });

        addSwitchRow(analyzerCard,
                "העברה לסל המיחזור בעת מחיקה",
                "שמור קבצים שנמחקו לצורך שחזור עתידי",
                PrefsManager.isUseRecycleBin(this),
                textPrimary, textSecondary,
                checked -> PrefsManager.setUseRecycleBin(this, checked));

        addSwitchRow(analyzerCard,
                "בקשת אישור לפני מחיקה",
                "אישור בטיחותי לפני העברה לסל או מחיקה",
                PrefsManager.isConfirmDelete(this),
                textPrimary, textSecondary,
                checked -> PrefsManager.setConfirmDelete(this, checked));

        int trashCount = RecycleBinManager.getTrashItemCount(this);
        String trashSize = StorageUtils.formatSize(RecycleBinManager.getTrashTotalBytes(this));
        int dangerColor = dark ? Color.parseColor("#F87171") : Color.parseColor("#DC2626");
        analyzerCard.addView(createSubtleButton(
                "ריקון סל המיחזור (" + trashCount + " פריטים • " + trashSize + ")",
                dangerColor, dark,
                v -> confirmEmptyRecycleBinTwoStep()));
        body.addView(analyzerCard);

        // 7. הסתרת סעיף הרשאות כאשר ההרשאות מאושרות ופעילות
        boolean allFilesGranted = StorageUtils.hasAllStoragePermissions(this);
        boolean notifGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;

        if (!allFilesGranted || !notifGranted) {
            addSectionTitle(body, "הרשאות גישה נדרשות", Color.parseColor("#D97706"));
            LinearLayout permsCard = createSectionCard(theme);
            if (!allFilesGranted) {
                permsCard.addView(createSubtleButton("אשר הרשאת גישה לכל הקבצים והאחסון", accent, dark, v -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:" + getPackageName())));
                    } else {
                        openAppSystemSettings();
                    }
                }));
            }
            if (!notifGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permsCard.addView(createSubtleButton("אשר הרשאת התראות (נגן ופעולות רקע)", accent, dark, v ->
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
        LinearLayout updateCard = createSectionCard(theme);

        addSwitchRow(updateCard,
                "בדיקת עדכונים אוטומטית בהפעלה",
                "כולל תמיכה מאובטחת בתעודות רשת מסוננת (נטפרי) עבור GitHub בלבד",
                PrefsManager.isAutoCheckUpdates(this),
                textPrimary, textSecondary,
                checked -> PrefsManager.setAutoCheckUpdates(this, checked));

        updateCard.addView(createSubtleButton(
                "בדיקת עדכון כעת",
                accent, dark,
                v -> UpdateChecker.checkForUpdates(this, true)));

        updateCard.addView(createSubtleButton(
                "יומן שגיאות ואבחון תקלות",
                textPrimary, dark,
                v -> startActivity(new Intent(this, DiagnosticsActivity.class))));

        updateCard.addView(createSubtleButton(
                "איפוס כל ההגדרות לברירת מחדל",
                dangerColor, dark,
                v -> confirmResetSettingsTwoStep()));

        body.addView(updateCard);

        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
        final int targetScroll = savedScrollY;
        if (targetScroll > 0) {
            scroll.post(() -> scroll.scrollTo(0, targetScroll));
        }
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
                        "חזור",
                        null,
                        true
                ),
                "חזור",
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
                            ThemeHelper.invalidateWallpaperCache();
                            Toast.makeText(this, "כל ההגדרות אופסו לברירת המחדל", Toast.LENGTH_SHORT).show();
                            renderSettingsUi();
                        },
                        "חזור",
                        null,
                        true
                ),
                "חזור",
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

    private LinearLayout createSectionCard(int theme) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(ThemeHelper.createGlassCardBackground(this, theme, 16f, true, false));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(6);
        card.setLayoutParams(lp);
        return card;
    }

    private View createEqualThemeCard(
            int themeId,
            String label,
            boolean active,
            int activeAccent,
            int divider,
            boolean dark,
            Runnable onClick
    ) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(6), dp(8), dp(6), dp(8));

        int previewBg = ThemeHelper.getBgColor(this, themeId);
        int previewAccent = ThemeHelper.getAccentColor(themeId);
        boolean previewDark = ThemeHelper.isDarkSurface(this, themeId);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setCornerRadius(dp(14));
        cardBg.setColor(active
                ? ColorUtils.setAlphaComponent(activeAccent, dark ? 52 : 28)
                : ColorUtils.setAlphaComponent(previewBg, 220));
        cardBg.setStroke(dp(active ? 2 : 1), active ? activeAccent : ColorUtils.setAlphaComponent(divider, 180));
        card.setBackground(cardBg);

        LinearLayout swatchRow = new LinearLayout(this);
        swatchRow.setOrientation(LinearLayout.HORIZONTAL);
        swatchRow.setGravity(Gravity.CENTER);

        View dotPrimary = new View(this);
        GradientDrawable d1 = new GradientDrawable();
        d1.setShape(GradientDrawable.OVAL);
        d1.setColor(previewBg);
        d1.setStroke(dp(1), previewDark ? Color.argb(120, 255, 255, 255) : Color.argb(90, 15, 23, 42));
        dotPrimary.setBackground(d1);
        LinearLayout.LayoutParams lpD1 = new LinearLayout.LayoutParams(dp(16), dp(16));
        lpD1.setMarginEnd(dp(4));
        swatchRow.addView(dotPrimary, lpD1);

        View dotAccent = new View(this);
        GradientDrawable d2 = new GradientDrawable();
        d2.setShape(GradientDrawable.OVAL);
        d2.setColor(previewAccent);
        dotAccent.setBackground(d2);
        swatchRow.addView(dotAccent, new LinearLayout.LayoutParams(dp(16), dp(16)));

        card.addView(swatchRow);

        TextView tvName = new TextView(this);
        tvName.setText(label);
        tvName.setTextSize(ThemeHelper.spScaled(this, 11.5f));
        tvName.setTypeface(Typeface.DEFAULT_BOLD);
        tvName.setSingleLine(true);
        tvName.setEllipsize(TextUtils.TruncateAt.END);
        tvName.setGravity(Gravity.CENTER);
        tvName.setTextColor(active ? ThemeHelper.getPrimaryTextColor(this, PrefsManager.getThemeMode(this))
                : ThemeHelper.getPrimaryTextColor(this, themeId));
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameLp.topMargin = dp(6);
        card.addView(tvName, nameLp);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(0, dp(68), 1f);
        cardLp.setMargins(dp(4), 0, dp(4), 0);
        card.setLayoutParams(cardLp);
        card.setOnClickListener(v -> onClick.run());
        return card;
    }

    private Button createOptionButton(String label, boolean active, int accent, boolean dark, Runnable onClick) {
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
        bg.setCornerRadius(dp(12));
        bg.setColor(active
                ? accent
                : ColorUtils.setAlphaComponent(accent, dark ? 24 : 14));
        b.setBackground(bg);
        b.setStateListAnimator(null);
        b.setElevation(0f);
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
                              int textPrimary, int textSecondary, SwitchCallback cb) {
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
        ThemeHelper.applySwitchTint(this, sw, PrefsManager.getThemeMode(this));
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> cb.onChanged(isChecked));
        row.addView(sw);

        parent.addView(row);
    }

    private Button createSubtleButton(String label, int textColor, boolean dark, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(textColor);
        b.setTextSize(ThemeHelper.spScaled(this, 13f));
        b.setTypeface(Typeface.DEFAULT_BOLD);

        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(12));
        shape.setColor(ColorUtils.setAlphaComponent(textColor, dark ? 28 : 16));
        b.setBackground(shape);
        b.setStateListAnimator(null);
        b.setElevation(0f);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(12), dp(10), dp(12), dp(10));

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
