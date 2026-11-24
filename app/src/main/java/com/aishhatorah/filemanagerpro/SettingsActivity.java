package com.aishhatorah.filemanagerpro;

import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * מסך הגדרות מתקדם בעיצוב זכוכית מודרני עם אקורדיונים נפתחים, תתי-רשימות מוטמעות,
 * בחירת רקע וטשטוש, מחוות מפורטות, סידור קטגוריות מסך הבית ומיון ברירת מחדל.
 */
public class SettingsActivity extends AppCompatActivity {

    private static final int REQ_PICK_WALLPAPER = 4102;

    private static final String SEC_APPEARANCE = "appearance";
    private static final String SEC_MEDIA = "media";
    private static final String SEC_HOME_LAYOUT = "home_layout";
    private static final String SEC_DISPLAY_SORT = "display_sort";
    private static final String SEC_DEFAULTS = "defaults";
    private static final String SEC_STORAGE = "storage";
    private static final String SEC_UPDATES = "updates";

    private ScrollView activeScroll;
    private LinearLayout rootLayout;
    private LinearLayout bodyLayout;
    private int savedScrollY = 0;

    private final Set<String> expandedSections = new HashSet<>();
    private boolean nestedGesturesExpanded = false;
    private boolean nestedSortExpanded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        renderSettingsUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ThemeHelper.invalidateSystemPaletteCache();
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

    private void beginSmoothTransition() {
        if (bodyLayout != null && !PrefsManager.isReduceAnimations(this)) {
            AutoTransition t = new AutoTransition();
            t.setDuration(220L);
            TransitionManager.beginDelayedTransition(bodyLayout, t);
        }
    }

    private Drawable createSelectableRipple() {
        try {
            TypedArray ta = obtainStyledAttributes(new int[]{android.R.attr.selectableItemBackground});
            Drawable d = ta.getDrawable(0);
            ta.recycle();
            return d;
        } catch (Throwable ignored) {
            return null;
        }
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
        int dangerColor = dark ? Color.parseColor("#F87171") : Color.parseColor("#DC2626");

        LinearLayout root = new LinearLayout(this);
        rootLayout = root;
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        ThemeHelper.applyWallpaperBackdrop(this, root, theme);

        // כותרת עליונה מעוגלת כלפי מעלה
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(10), dp(8), dp(14), dp(10));
        ThemeHelper.applyCurvedHeaderStyle(this, topBar, theme);

        ImageButton btnBack = ThemeHelper.createBorderlessSvgButton(
                this, R.drawable.ic_arrow_back, accent, "חזרה", 40, 8, v -> {
                    finish();
                    ThemeHelper.overrideCloseTransition(this);
                });
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
        scroll.setVerticalScrollBarEnabled(false);
        activeScroll = scroll;
        LinearLayout body = new LinearLayout(this);
        bodyLayout = body;
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(14), dp(14), dp(28));

        // 1. מראה וערכת נושא (כולל תמונת רקע וטשטוש)
        LinearLayout appearanceBox = createAccordionSection(
                body,
                SEC_APPEARANCE,
                "מראה וערכת נושא",
                "ערכה (" + ThemeHelper.getThemeNameHe(theme) + "), צבע דגש, גודל תצוגה, רקע וטשטוש",
                R.drawable.ic_wallpaper,
                theme, textPrimary, textSecondary, accent, divider, dark
        );
        populateAppearanceSection(appearanceBox, root, theme, textPrimary, textSecondary, accent, divider, dark, dangerColor);

        // 2. מדיה ומחוות (כולל תת-רשימה למחוות וניווט מבועת המוזיקה)
        LinearLayout mediaBox = createAccordionSection(
                body,
                SEC_MEDIA,
                "מדיה ומחוות",
                "נגנים מובנים, פירוט מחוות מגע וניווט מבועת המוזיקה",
                R.drawable.ic_play,
                theme, textPrimary, textSecondary, accent, divider, dark
        );
        populateMediaSection(mediaBox, theme, textPrimary, textSecondary, accent, divider, dark);

        // 3. סידור קטגוריות במסך הבית (תת-רשימה מוטמעת)
        LinearLayout homeOrderBox = createAccordionSection(
                body,
                SEC_HOME_LAYOUT,
                "סידור קטגוריות במסך הבית",
                "שינוי סדר האריחים והסתרת קטגוריות ממסך הבית",
                R.drawable.ic_drag_handle,
                theme, textPrimary, textSecondary, accent, divider, dark
        );
        populateHomeLayoutSection(homeOrderBox, theme, textPrimary, textSecondary, accent, divider, dark);

        // 4. שלוש ההגדרות המרכזיות נשמרות גלויות ישירות בכרטיסייה נקייה
        LinearLayout quickTogglesCard = createSectionCard(theme);
        addSwitchRow(quickTogglesCard,
                "שמות קטגוריות במסך הבית",
                "הצגת שמות הקטגוריות מתחת לאייקונים בדף הבית",
                PrefsManager.isShowHomeCategoryLabels(this),
                textPrimary, textSecondary,
                checked -> PrefsManager.setShowHomeCategoryLabels(this, checked));

        addSwitchRow(quickTogglesCard,
                "קיצור לתיקייה האחרונה",
                "שמירת כרטיס גישה מהירה לתיקייה האחרונה שנפתחה",
                PrefsManager.isQuickAccessEnabled(this),
                textPrimary, textSecondary,
                checked -> PrefsManager.setQuickAccessEnabled(this, checked));

        addSwitchRow(quickTogglesCard,
                "גיבוי .bak אוטומטי",
                "שמירת עותק גיבוי אוטומטי לפני שמירת שינויים בקבצים",
                PrefsManager.isCreateBackupOnSave(this),
                textPrimary, textSecondary,
                checked -> PrefsManager.setCreateBackupOnSave(this, checked));
        body.addView(quickTogglesCard);

        // 5. תצוגה ומיון (כולל תת-רשימה למיון ברירת מחדל)
        LinearLayout displayBox = createAccordionSection(
                body,
                SEC_DISPLAY_SORT,
                "תצוגה ומיון",
                "רשת או רשימה, קבצים מוסתרים, תמונות ממוזערות ומיון",
                R.drawable.ic_sort,
                theme, textPrimary, textSecondary, accent, divider, dark
        );
        populateDisplaySortSection(displayBox, theme, textPrimary, textSecondary, accent, divider, dark);

        // 6. אפליקציות ברירת מחדל
        Map<String, String> savedDefaults = PrefsManager.getAllDefaultOpenActions(this);
        String defSub = savedDefaults.isEmpty()
                ? "לא הוגדרו סיומות קבצים קבועות"
                : savedDefaults.size() + " סיומות מוגדרות לפתיחה קבועה";
        LinearLayout defaultsBox = createAccordionSection(
                body,
                SEC_DEFAULTS,
                "אפליקציות ברירת מחדל",
                defSub,
                R.drawable.ic_apps,
                theme, textPrimary, textSecondary, accent, divider, dark
        );
        populateDefaultsSection(defaultsBox, savedDefaults, theme, textPrimary, textSecondary, accent, divider, dark);

        // 7. אחסון וסל מיחזור
        LinearLayout storageBox = createAccordionSection(
                body,
                SEC_STORAGE,
                "אחסון וסל מיחזור",
                "ניתוח אחסון, התרעת תפוסה, סל מיחזור וניקוי מטמון",
                R.drawable.ic_analyzer,
                theme, textPrimary, textSecondary, accent, divider, dark
        );
        populateStorageSection(storageBox, theme, textPrimary, textSecondary, accent, divider, dangerColor);

        // הרשאות גישה (מוצג אוטומטית רק אם חסרה הרשאה)
        boolean allFilesGranted = StorageUtils.hasAllStoragePermissions(this);
        boolean notifGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;

        if (!allFilesGranted || !notifGranted) {
            LinearLayout permsCard = createSectionCard(theme);
            TextView tvPermHead = new TextView(this);
            tvPermHead.setText("הרשאות גישה נדרשות");
            tvPermHead.setTextColor(Color.parseColor("#D97706"));
            tvPermHead.setTextSize(ThemeHelper.spScaled(this, 13.5f));
            tvPermHead.setTypeface(Typeface.DEFAULT_BOLD);
            tvPermHead.setPadding(0, 0, 0, dp(6));
            permsCard.addView(tvPermHead);

            if (!allFilesGranted) {
                addActionListRow(permsCard,
                        "אישור גישה לכל הקבצים",
                        "נדרש לקריאה וניהול מלא של הקבצים במכשיר",
                        R.drawable.ic_lock,
                        accent, textPrimary, textSecondary, divider, true,
                        v -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                        Uri.parse("package:" + getPackageName())));
                            } else {
                                openAppSystemSettings();
                            }
                        });
            }
            if (!notifGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                addActionListRow(permsCard,
                        "אישור התראות",
                        "נדרש להצגת פקדי נגן המוזיקה והתרעות העתקה",
                        R.drawable.ic_info,
                        accent, textPrimary, textSecondary, divider, true,
                        v -> androidx.core.app.ActivityCompat.requestPermissions(
                                this,
                                new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                                2044
                        ));
            }
            body.addView(permsCard);
        }

        // 8. עדכונים ואבחון
        LinearLayout updatesBox = createAccordionSection(
                body,
                SEC_UPDATES,
                "עדכונים ואבחון",
                "עדכוני גרסה, יומן תקלות ואיפוס כל ההגדרות",
                R.drawable.ic_info,
                theme, textPrimary, textSecondary, accent, divider, dark
        );
        populateUpdatesSection(updatesBox, theme, textPrimary, textSecondary, accent, divider, dangerColor);

        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
        final int targetScroll = savedScrollY;
        if (targetScroll > 0) {
            scroll.post(() -> scroll.scrollTo(0, targetScroll));
        }
    }

    private LinearLayout createAccordionSection(
            LinearLayout parent,
            String sectionKey,
            String title,
            String summary,
            int iconRes,
            int theme,
            int textPrimary,
            int textSecondary,
            int accent,
            int divider,
            boolean dark
    ) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(ThemeHelper.createGlassCardBackground(this, theme, 18f, true, false));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = dp(12);
        card.setLayoutParams(cardLp);

        boolean expanded = expandedSections.contains(sectionKey);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(15), dp(16), dp(15));
        header.setForeground(createSelectableRipple());

        // אייקון מרובע מעוגל עם שקיפות עדינה
        LinearLayout iconBadge = new LinearLayout(this);
        iconBadge.setGravity(Gravity.CENTER);
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setCornerRadius(dp(11));
        badgeBg.setColor(ColorUtils.setAlphaComponent(accent, dark ? 38 : 22));
        badgeBg.setStroke(Math.max(1, dp(1)), ColorUtils.setAlphaComponent(accent, dark ? 55 : 32));
        iconBadge.setBackground(badgeBg);
        ImageView ivIcon = new ImageView(this);
        ivIcon.setImageResource(iconRes);
        ivIcon.setColorFilter(accent);
        iconBadge.addView(ivIcon, new LinearLayout.LayoutParams(dp(20), dp(20)));
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(dp(38), dp(38));
        badgeLp.setMarginEnd(dp(12));
        header.addView(iconBadge, badgeLp);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextColor(textPrimary);
        tvTitle.setTextSize(ThemeHelper.spScaled(this, 15.5f));
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        textCol.addView(tvTitle);

        TextView tvSub = new TextView(this);
        tvSub.setText(summary);
        tvSub.setTextColor(textSecondary);
        tvSub.setTextSize(ThemeHelper.spScaled(this, 12f));
        tvSub.setMaxLines(2);
        tvSub.setEllipsize(TextUtils.TruncateAt.END);
        tvSub.setLineSpacing(0f, 1.15f);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(3);
        textCol.addView(tvSub, subLp);

        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textLp.setMarginEnd(dp(10));
        header.addView(textCol, textLp);

        ImageView ivChevron = new ImageView(this);
        ivChevron.setImageResource(R.drawable.ic_chevron_left);
        ivChevron.setColorFilter(expanded ? accent : textSecondary);
        ivChevron.setRotation(expanded ? -90f : 0f);
        header.addView(ivChevron, new LinearLayout.LayoutParams(dp(22), dp(22)));

        card.addView(header);

        LinearLayout contentWrapper = new LinearLayout(this);
        contentWrapper.setOrientation(LinearLayout.VERTICAL);
        contentWrapper.setVisibility(expanded ? View.VISIBLE : View.GONE);

        View topDiv = new View(this);
        topDiv.setBackgroundColor(ColorUtils.setAlphaComponent(divider, 95));
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1)));
        divLp.setMargins(dp(16), 0, dp(16), dp(4));
        contentWrapper.addView(topDiv, divLp);

        LinearLayout innerBox = new LinearLayout(this);
        innerBox.setOrientation(LinearLayout.VERTICAL);
        innerBox.setPadding(dp(16), dp(6), dp(16), dp(14));
        contentWrapper.addView(innerBox);

        card.addView(contentWrapper);

        header.setOnClickListener(v -> {
            beginSmoothTransition();
            boolean nowExpanded;
            if (expandedSections.contains(sectionKey)) {
                expandedSections.remove(sectionKey);
                nowExpanded = false;
            } else {
                expandedSections.add(sectionKey);
                nowExpanded = true;
            }
            contentWrapper.setVisibility(nowExpanded ? View.VISIBLE : View.GONE);
            ivChevron.setColorFilter(nowExpanded ? accent : textSecondary);
            if (!PrefsManager.isReduceAnimations(this)) {
                ivChevron.animate().rotation(nowExpanded ? -90f : 0f).setDuration(200L).start();
            } else {
                ivChevron.setRotation(nowExpanded ? -90f : 0f);
            }
        });

        parent.addView(card);
        return innerBox;
    }

    private void populateAppearanceSection(
            LinearLayout box,
            LinearLayout rootBackdropTarget,
            int theme,
            int textPrimary,
            int textSecondary,
            int accent,
            int divider,
            boolean dark,
            int dangerColor
    ) {
        TextView tvThemeSub = new TextView(this);
        tvThemeSub.setText("ערכה פעילה: " + ThemeHelper.getThemeNameHe(theme));
        tvThemeSub.setTextColor(textSecondary);
        tvThemeSub.setTextSize(ThemeHelper.spScaled(this, 12.5f));
        tvThemeSub.setPadding(0, dp(4), 0, dp(10));
        box.addView(tvThemeSub);

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
                            PrefsManager.setCustomAccentColor(this, 0);
                            ThemeHelper.invalidateSystemPaletteCache();
                            renderSettingsUi();
                        });
                tRow.addView(cardItem);
            }
            LinearLayout.LayoutParams trLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (rowStart > 0) trLp.topMargin = dp(8);
            box.addView(tRow, trLp);
        }

        // צבע דגש
        TextView tvAccentLabel = new TextView(this);
        tvAccentLabel.setText("צבע דגש");
        tvAccentLabel.setTextColor(textPrimary);
        tvAccentLabel.setTextSize(ThemeHelper.spScaled(this, 13.5f));
        tvAccentLabel.setTypeface(Typeface.DEFAULT_BOLD);
        tvAccentLabel.setPadding(0, dp(16), 0, dp(10));
        box.addView(tvAccentLabel);

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
                gd.setColor(swColor == 0 ? ThemeHelper.getThemePreviewAccentColor(this, theme) : swColor);
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
            box.addView(swatchRow, srLp);
        }

        // גודל תצוגה
        addRowDivider(box, divider, 14, 4);
        float currentScale = PrefsManager.getUiScale(this);
        LinearLayout scaleHeaderRow = new LinearLayout(this);
        scaleHeaderRow.setOrientation(LinearLayout.HORIZONTAL);
        scaleHeaderRow.setGravity(Gravity.CENTER_VERTICAL);
        scaleHeaderRow.setPadding(0, dp(8), 0, dp(4));

        TextView tvScaleLabel = new TextView(this);
        tvScaleLabel.setText(String.format(Locale.US, "גודל תצוגה: %.0f%%", currentScale * 100f));
        tvScaleLabel.setTextColor(textPrimary);
        tvScaleLabel.setTextSize(ThemeHelper.spScaled(this, 13.5f));
        tvScaleLabel.setTypeface(Typeface.DEFAULT_BOLD);
        scaleHeaderRow.addView(tvScaleLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final SeekBar sbScale = new SeekBar(this);
        boolean isScaleDefault = Math.abs(currentScale - 1.0f) < 0.015f;
        final ImageButton btnResetScale = ThemeHelper.createBorderlessSvgButton(
                this, R.drawable.ic_reset, isScaleDefault ? textSecondary : accent,
                "איפוס לברירת המחדל", 32, 6, null);
        btnResetScale.setAlpha(isScaleDefault ? 0.5f : 1.0f);
        btnResetScale.setOnClickListener(v -> {
            PrefsManager.setUiScale(this, 1.0f);
            sbScale.setProgress(15);
            tvScaleLabel.setText("גודל תצוגה: 100%");
            btnResetScale.setColorFilter(textSecondary);
            btnResetScale.setAlpha(0.5f);
            Toast.makeText(this, "גודל התצוגה אופס ל-100%", Toast.LENGTH_SHORT).show();
        });
        scaleHeaderRow.addView(btnResetScale);
        box.addView(scaleHeaderRow);

        ThemeHelper.applySeekBarTint(sbScale, accent);
        sbScale.setMax(40);
        sbScale.setProgress(Math.max(0, Math.min(40, Math.round((currentScale - 0.85f) * 100f))));
        sbScale.setPadding(sbScale.getPaddingLeft(), dp(4), sbScale.getPaddingRight(), dp(8));
        sbScale.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float val = 0.85f + (progress / 100f);
                tvScaleLabel.setText(String.format(Locale.US, "גודל תצוגה: %.0f%%", val * 100f));
                boolean defNow = Math.abs(val - 1.0f) < 0.015f;
                btnResetScale.setColorFilter(defNow ? textSecondary : accent);
                btnResetScale.setAlpha(defNow ? 0.5f : 1.0f);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                float val = 0.85f + (seekBar.getProgress() / 100f);
                PrefsManager.setUiScale(SettingsActivity.this, val);
                Toast.makeText(SettingsActivity.this,
                        String.format(Locale.US, "גודל תצוגה נשמר: %.0f%%", val * 100f),
                        Toast.LENGTH_SHORT).show();
            }
        });
        box.addView(sbScale);

        addSwitchRow(box,
                "הפחתת אנימציות",
                "קיצור וביטול של מעברים ואנימציות בממשק",
                PrefsManager.isReduceAnimations(this),
                textPrimary, textSecondary,
                checked -> PrefsManager.setReduceAnimations(this, checked));

        // תמונת רקע וטשטוש (מוטמע כרשימת הגדרות מסודרת)
        addRowDivider(box, divider, 4, 6);
        TextView tvWallTitle = new TextView(this);
        tvWallTitle.setText("תמונת רקע וטשטוש");
        tvWallTitle.setTextColor(accent);
        tvWallTitle.setTextSize(ThemeHelper.spScaled(this, 12.5f));
        tvWallTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvWallTitle.setPadding(0, dp(6), 0, dp(4));
        box.addView(tvWallTitle);

        boolean hasWall = ThemeHelper.hasWallpaper(this);
        addActionListRow(box,
                hasWall ? "החלפת תמונת רקע" : "בחירת תמונת רקע",
                hasWall ? "תמונת רקע אישית פעילה ברקע האפליקציה" : "בחר תמונה מהגלריה לרקע שקוף ומטושטש",
                R.drawable.ic_wallpaper,
                accent, textPrimary, textSecondary, divider, true,
                v -> launchWallpaperPicker());

        if (hasWall) {
            addActionListRow(box,
                    "הסרת תמונת רקע",
                    "חזרה לרקע ערכת הנושא בלבד",
                    R.drawable.ic_delete,
                    dangerColor, dangerColor, textSecondary, divider, false,
                    v -> {
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
                    });
        }

        addRowDivider(box, divider, 4, 6);
        int curBlur = PrefsManager.getWallpaperBlurRadius(this);
        LinearLayout blurHeaderRow = new LinearLayout(this);
        blurHeaderRow.setOrientation(LinearLayout.HORIZONTAL);
        blurHeaderRow.setGravity(Gravity.CENTER_VERTICAL);
        blurHeaderRow.setPadding(0, dp(6), 0, dp(4));

        TextView tvBlurLabel = new TextView(this);
        tvBlurLabel.setText("עוצמת טשטוש רקע: " + (curBlur == 0 ? "0%" : Math.round((curBlur / 28f) * 100f) + "%"));
        tvBlurLabel.setTextColor(textPrimary);
        tvBlurLabel.setTextSize(ThemeHelper.spScaled(this, 13.5f));
        tvBlurLabel.setTypeface(Typeface.DEFAULT_BOLD);
        blurHeaderRow.addView(tvBlurLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        boolean isBlurDefault = (curBlur == PrefsManager.DEFAULT_WALLPAPER_BLUR);
        final SeekBar sbBlur = new SeekBar(this);
        final ImageButton btnResetBlur = ThemeHelper.createBorderlessSvgButton(
                this, R.drawable.ic_reset, isBlurDefault ? textSecondary : accent,
                "איפוס טשטוש", 32, 6, null);
        btnResetBlur.setAlpha(isBlurDefault ? 0.5f : 1.0f);
        btnResetBlur.setOnClickListener(v -> {
            PrefsManager.setWallpaperBlurRadius(this, PrefsManager.DEFAULT_WALLPAPER_BLUR);
            ThemeHelper.invalidateWallpaperCache();
            ThemeHelper.applyWallpaperBackdrop(this, rootBackdropTarget, theme);
            sbBlur.setProgress(PrefsManager.DEFAULT_WALLPAPER_BLUR);
            tvBlurLabel.setText("עוצמת טשטוש רקע: " + Math.round((PrefsManager.DEFAULT_WALLPAPER_BLUR / 28f) * 100f) + "%");
            btnResetBlur.setColorFilter(textSecondary);
            btnResetBlur.setAlpha(0.5f);
            Toast.makeText(this, "הטשטוש אופס לברירת המחדל", Toast.LENGTH_SHORT).show();
        });
        blurHeaderRow.addView(btnResetBlur);
        box.addView(blurHeaderRow);

        ThemeHelper.applySeekBarTint(sbBlur, accent);
        sbBlur.setMax(28);
        sbBlur.setProgress(curBlur);
        sbBlur.setPadding(sbBlur.getPaddingLeft(), dp(4), sbBlur.getPaddingRight(), dp(4));
        sbBlur.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvBlurLabel.setText("עוצמת טשטוש רקע: " + (progress == 0 ? "0%" : Math.round((progress / 28f) * 100f) + "%"));
                boolean defNow = (progress == PrefsManager.DEFAULT_WALLPAPER_BLUR);
                btnResetBlur.setColorFilter(defNow ? textSecondary : accent);
                btnResetBlur.setAlpha(defNow ? 0.5f : 1.0f);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                PrefsManager.setWallpaperBlurRadius(SettingsActivity.this, seekBar.getProgress());
                ThemeHelper.invalidateWallpaperCache();
                ThemeHelper.applyWallpaperBackdrop(SettingsActivity.this, rootBackdropTarget, theme);
            }
        });
        box.addView(sbBlur);
    }

    private void populateMediaSection(
            LinearLayout box,
            int theme,
            int textPrimary,
            int textSecondary,
            int accent,
            int divider,
            boolean dark
    ) {
        addSwitchRow(box,
                "נגן וידאו מובנה",
                "",
                PrefsManager.isUseInternalVideoPlayer(this),
                textPrimary, textSecondary,
                checked -> PrefsManager.setUseInternalVideoPlayer(this, checked));

        addSwitchRow(box,
                "מציג תמונות מובנה",
                "",
                PrefsManager.isUseInternalImageViewer(this),
                textPrimary, textSecondary,
                checked -> PrefsManager.setUseInternalImageViewer(this, checked));

        addSwitchRow(box,
                "נגן שמע מובנה",
                "",
                PrefsManager.isUseInternalAudioPlayer(this),
                textPrimary, textSecondary,
                checked -> PrefsManager.setUseInternalAudioPlayer(this, checked));

        // מצב מחוות: מתרחב לתת-רשימה עם פירוט כל המחווה בנפרד (ללא מתג ראשי)
        addRowDivider(box, divider, 2, 2);
        LinearLayout gestureHeaderRow = new LinearLayout(this);
        gestureHeaderRow.setOrientation(LinearLayout.HORIZONTAL);
        gestureHeaderRow.setGravity(Gravity.CENTER_VERTICAL);
        gestureHeaderRow.setPadding(dp(2), dp(13), dp(2), dp(13));
        gestureHeaderRow.setForeground(createSelectableRipple());

        LinearLayout gTextCol = new LinearLayout(this);
        gTextCol.setOrientation(LinearLayout.VERTICAL);
        TextView tvGestureTitle = new TextView(this);
        tvGestureTitle.setText("מצב מחוות");
        tvGestureTitle.setTextColor(textPrimary);
        tvGestureTitle.setTextSize(ThemeHelper.spScaled(this, 14f));
        tvGestureTitle.setTypeface(Typeface.DEFAULT_BOLD);
        gTextCol.addView(tvGestureTitle);

        TextView tvGestureCount = new TextView(this);
        tvGestureCount.setText(buildActiveGestureCountLabel());
        tvGestureCount.setTextColor(textSecondary);
        tvGestureCount.setTextSize(ThemeHelper.spScaled(this, 12f));
        gTextCol.addView(tvGestureCount);
        gestureHeaderRow.addView(gTextCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView ivGestureChevron = new ImageView(this);
        ivGestureChevron.setImageResource(R.drawable.ic_chevron_left);
        ivGestureChevron.setColorFilter(nestedGesturesExpanded ? accent : textSecondary);
        ivGestureChevron.setRotation(nestedGesturesExpanded ? -90f : 0f);
        gestureHeaderRow.addView(ivGestureChevron, new LinearLayout.LayoutParams(dp(20), dp(20)));
        box.addView(gestureHeaderRow);

        LinearLayout gestureSubBox = new LinearLayout(this);
        gestureSubBox.setOrientation(LinearLayout.VERTICAL);
        gestureSubBox.setVisibility(nestedGesturesExpanded ? View.VISIBLE : View.GONE);
        GradientDrawable subBg = new GradientDrawable();
        subBg.setCornerRadius(dp(14));
        subBg.setColor(ColorUtils.setAlphaComponent(accent, dark ? 22 : 12));
        subBg.setStroke(Math.max(1, dp(1)), ColorUtils.setAlphaComponent(divider, 95));
        gestureSubBox.setBackground(subBg);
        gestureSubBox.setPadding(dp(12), dp(6), dp(12), dp(6));
        LinearLayout.LayoutParams subBoxLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subBoxLp.bottomMargin = dp(8);
        gestureSubBox.setLayoutParams(subBoxLp);

        Runnable refreshGestureCount = () -> tvGestureCount.setText(buildActiveGestureCountLabel());

        addSwitchRow(gestureSubBox, "מעבר בין תמונות בלחיצה", "",
                PrefsManager.isGestureImageTapNav(this), textPrimary, textSecondary, c -> {
                    PrefsManager.setGestureImageTapNav(this, c);
                    refreshGestureCount.run();
                });
        addSwitchRow(gestureSubBox, "מעבר בין תמונות בהחלקה", "",
                PrefsManager.isGestureImageSwipeNav(this), textPrimary, textSecondary, c -> {
                    PrefsManager.setGestureImageSwipeNav(this, c);
                    refreshGestureCount.run();
                });
        addSwitchRow(gestureSubBox, "הגדלה בצביטה (תמונות ווידאו)", "",
                PrefsManager.isGesturePinchZoom(this), textPrimary, textSecondary, c -> {
                    PrefsManager.setGesturePinchZoom(this, c);
                    refreshGestureCount.run();
                });
        addSwitchRow(gestureSubBox, "הרצה וקפיצה בהחלקה בווידאו", "",
                PrefsManager.isGestureVideoSeekSwipe(this), textPrimary, textSecondary, c -> {
                    PrefsManager.setGestureVideoSeekSwipe(this, c);
                    refreshGestureCount.run();
                });
        addSwitchRow(gestureSubBox, "עוצמת שמע ובהירות בהחלקה", "",
                PrefsManager.isGestureVideoVolumeBrightness(this), textPrimary, textSecondary, c -> {
                    PrefsManager.setGestureVideoVolumeBrightness(this, c);
                    refreshGestureCount.run();
                });
        addSwitchRow(gestureSubBox, "האצה ל-2x בלחיצה ארוכה", "",
                PrefsManager.isGestureVideoHold2x(this), textPrimary, textSecondary, c -> {
                    PrefsManager.setGestureVideoHold2x(this, c);
                    refreshGestureCount.run();
                });
        addSwitchRow(gestureSubBox, "דפדוף בהחלקה ב-PDF", "",
                PrefsManager.isGesturePdfSwipe(this), textPrimary, textSecondary, c -> {
                    PrefsManager.setGesturePdfSwipe(this, c);
                    refreshGestureCount.run();
                });

        box.addView(gestureSubBox);

        gestureHeaderRow.setOnClickListener(v -> {
            beginSmoothTransition();
            nestedGesturesExpanded = !nestedGesturesExpanded;
            gestureSubBox.setVisibility(nestedGesturesExpanded ? View.VISIBLE : View.GONE);
            ivGestureChevron.setColorFilter(nestedGesturesExpanded ? accent : textSecondary);
            if (!PrefsManager.isReduceAnimations(this)) {
                ivGestureChevron.animate().rotation(nestedGesturesExpanded ? -90f : 0f).setDuration(190L).start();
            } else {
                ivGestureChevron.setRotation(nestedGesturesExpanded ? -90f : 0f);
            }
        });

        addSwitchRow(box,
                "חזרה מבועת המוזיקה לתיקיית השיר",
                "כאשר כבוי — יציאה מהנגן חוזרת תמיד למסך הקודם",
                PrefsManager.isMiniBarReturnToSongFolder(this),
                textPrimary, textSecondary,
                checked -> PrefsManager.setMiniBarReturnToSongFolder(this, checked));
    }

    private String buildActiveGestureCountLabel() {
        int active = 0;
        if (PrefsManager.isGestureImageTapNav(this)) active++;
        if (PrefsManager.isGestureImageSwipeNav(this)) active++;
        if (PrefsManager.isGesturePinchZoom(this)) active++;
        if (PrefsManager.isGestureVideoSeekSwipe(this)) active++;
        if (PrefsManager.isGestureVideoVolumeBrightness(this)) active++;
        if (PrefsManager.isGestureVideoHold2x(this)) active++;
        if (PrefsManager.isGesturePdfSwipe(this)) active++;
        return active + " מתוך 7 מחוות פעילות";
    }

    private void populateHomeLayoutSection(
            LinearLayout box,
            int theme,
            int textPrimary,
            int textSecondary,
            int accent,
            int divider,
            boolean dark
    ) {
        TextView tvHint = new TextView(this);
        tvHint.setText("הזז קטגוריות למעלה/למטה או הסתר אותן ממסך הבית.");
        tvHint.setTextColor(textSecondary);
        tvHint.setTextSize(ThemeHelper.spScaled(this, 12f));
        tvHint.setPadding(0, dp(4), 0, dp(8));
        box.addView(tvHint);

        LinearLayout organizerContainer = new LinearLayout(this);
        organizerContainer.setOrientation(LinearLayout.VERTICAL);
        box.addView(organizerContainer);

        rebuildInlineHomeOrganizer(organizerContainer, theme, textPrimary, textSecondary, accent, divider, dark);

        addRowDivider(box, divider, 4, 2);
        addActionListRow(box,
                "איפוס סדר ברירת מחדל",
                "החזרת כל הקטגוריות לסדר המקורי",
                R.drawable.ic_reset,
                accent, accent, textSecondary, divider, false,
                v -> {
                    PrefsManager.setHomeCategoryOrder(this, null);
                    PrefsManager.setHomeVisibleLimit(this, -1);
                    beginSmoothTransition();
                    rebuildInlineHomeOrganizer(organizerContainer, theme, textPrimary, textSecondary, accent, divider, dark);
                    Toast.makeText(this, "סדר הקטגוריות אופס לברירת המחדל", Toast.LENGTH_SHORT).show();
                });
    }

    private void rebuildInlineHomeOrganizer(
            LinearLayout container,
            int theme,
            int textPrimary,
            int textSecondary,
            int accent,
            int divider,
            boolean dark
    ) {
        container.removeAllViews();
        List<String> fullOrder = PrefsManager.getHomeCategoryOrder(this);
        int limit = PrefsManager.getHomeVisibleLimit(this);
        int split = (limit > 0 && limit < fullOrder.size()) ? limit : fullOrder.size();

        final List<String> visibleKeys = new ArrayList<>(fullOrder.subList(0, split));
        final List<String> hiddenKeys = new ArrayList<>(fullOrder.subList(split, fullOrder.size()));

        for (int i = 0; i < visibleKeys.size(); i++) {
            final int idx = i;
            final String key = visibleKeys.get(i);
            if (i > 0) {
                addRowDivider(container, divider, 0, 0);
            }
            addInlineCategoryRow(
                    container, key, true,
                    idx > 0, idx < visibleKeys.size() - 1,
                    textPrimary, textSecondary, accent, dark,
                    () -> {
                        Collections.swap(visibleKeys, idx, idx - 1);
                        persistInlineHomeOrganizer(visibleKeys, hiddenKeys);
                        beginSmoothTransition();
                        rebuildInlineHomeOrganizer(container, theme, textPrimary, textSecondary, accent, divider, dark);
                    },
                    () -> {
                        Collections.swap(visibleKeys, idx, idx + 1);
                        persistInlineHomeOrganizer(visibleKeys, hiddenKeys);
                        beginSmoothTransition();
                        rebuildInlineHomeOrganizer(container, theme, textPrimary, textSecondary, accent, divider, dark);
                    },
                    () -> {
                        if (visibleKeys.size() <= 1) {
                            Toast.makeText(this, "חייבת להישאר לפחות קטגוריה אחת גלויה", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        String moved = visibleKeys.remove(idx);
                        hiddenKeys.add(0, moved);
                        persistInlineHomeOrganizer(visibleKeys, hiddenKeys);
                        beginSmoothTransition();
                        rebuildInlineHomeOrganizer(container, theme, textPrimary, textSecondary, accent, divider, dark);
                    }
            );
        }

        if (!hiddenKeys.isEmpty()) {
            TextView tvHiddenSep = new TextView(this);
            tvHiddenSep.setText("מוסתר ממסך הבית (" + hiddenKeys.size() + ")");
            tvHiddenSep.setTextColor(dark ? Color.parseColor("#F87171") : Color.parseColor("#DC2626"));
            tvHiddenSep.setTextSize(ThemeHelper.spScaled(this, 12f));
            tvHiddenSep.setTypeface(Typeface.DEFAULT_BOLD);
            tvHiddenSep.setPadding(dp(4), dp(10), dp(4), dp(4));
            container.addView(tvHiddenSep);

            for (int i = 0; i < hiddenKeys.size(); i++) {
                final int idx = i;
                final String key = hiddenKeys.get(i);
                if (i > 0) {
                    addRowDivider(container, divider, 0, 0);
                }
                addInlineCategoryRow(
                        container, key, false,
                        idx > 0, idx < hiddenKeys.size() - 1,
                        textPrimary, textSecondary, accent, dark,
                        () -> {
                            Collections.swap(hiddenKeys, idx, idx - 1);
                            persistInlineHomeOrganizer(visibleKeys, hiddenKeys);
                            beginSmoothTransition();
                            rebuildInlineHomeOrganizer(container, theme, textPrimary, textSecondary, accent, divider, dark);
                        },
                        () -> {
                            Collections.swap(hiddenKeys, idx, idx + 1);
                            persistInlineHomeOrganizer(visibleKeys, hiddenKeys);
                            beginSmoothTransition();
                            rebuildInlineHomeOrganizer(container, theme, textPrimary, textSecondary, accent, divider, dark);
                        },
                        () -> {
                            String moved = hiddenKeys.remove(idx);
                            visibleKeys.add(moved);
                            persistInlineHomeOrganizer(visibleKeys, hiddenKeys);
                            beginSmoothTransition();
                            rebuildInlineHomeOrganizer(container, theme, textPrimary, textSecondary, accent, divider, dark);
                        }
                );
            }
        }
    }

    private void addInlineCategoryRow(
            LinearLayout parent,
            String key,
            boolean visible,
            boolean canMoveUp,
            boolean canMoveDown,
            int textPrimary,
            int textSecondary,
            int accent,
            boolean dark,
            Runnable onUp,
            Runnable onDown,
            Runnable onToggleVisibility
    ) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), dp(8), dp(2), dp(8));

        TextView tvName = new TextView(this);
        tvName.setText(categoryLabelForKey(key));
        tvName.setTextColor(visible ? textPrimary : textSecondary);
        tvName.setAlpha(visible ? 1f : 0.72f);
        tvName.setTextSize(ThemeHelper.spScaled(this, 13.5f));
        tvName.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tLp.setMarginEnd(dp(6));
        row.addView(tvName, tLp);

        ImageButton btnUp = ThemeHelper.createBorderlessSvgButton(
                this, R.drawable.ic_chevron_up, canMoveUp ? accent : textSecondary, "הזז למעלה", 32, 5,
                canMoveUp ? v -> onUp.run() : null);
        btnUp.setAlpha(canMoveUp ? 1f : 0.3f);
        btnUp.setEnabled(canMoveUp);
        row.addView(btnUp);

        ImageButton btnDown = ThemeHelper.createBorderlessSvgButton(
                this, R.drawable.ic_chevron_down, canMoveDown ? accent : textSecondary, "הזז למטה", 32, 5,
                canMoveDown ? v -> onDown.run() : null);
        btnDown.setAlpha(canMoveDown ? 1f : 0.3f);
        btnDown.setEnabled(canMoveDown);
        row.addView(btnDown);

        int visTint = visible ? accent : (dark ? Color.parseColor("#F87171") : Color.parseColor("#DC2626"));
        ImageButton btnVis = ThemeHelper.createBorderlessSvgButton(
                this,
                visible ? R.drawable.ic_visibility : R.drawable.ic_visibility_off,
                visTint,
                visible ? "הסתר ממסך הבית" : "הצג במסך הבית",
                34, 6,
                v -> onToggleVisibility.run());
        row.addView(btnVis);

        parent.addView(row);
    }

    private void persistInlineHomeOrganizer(List<String> visibleKeys, List<String> hiddenKeys) {
        List<String> combined = new ArrayList<>(visibleKeys.size() + hiddenKeys.size());
        combined.addAll(visibleKeys);
        combined.addAll(hiddenKeys);
        PrefsManager.setHomeCategoryOrder(this, combined);
        PrefsManager.setHomeVisibleLimit(this, hiddenKeys.isEmpty() ? -1 : visibleKeys.size());
    }

    private String categoryLabelForKey(String key) {
        switch (key) {
            case "internal": return getString(R.string.cat_internal);
            case "sd_card": return getString(R.string.cat_sd_card);
            case "downloads": return getString(R.string.cat_downloads);
            case "images": return getString(R.string.cat_images);
            case "audio": return getString(R.string.cat_audio);
            case "videos": return getString(R.string.cat_videos);
            case "documents": return getString(R.string.cat_documents);
            case "apps": return getString(R.string.cat_apps);
            case "recent": return getString(R.string.cat_recent);
            case "recycle_bin": return getString(R.string.cat_recycle_bin);
            case "audio_cutter": return getString(R.string.cat_audio_cutter);
            case "video_cutter": return getString(R.string.cat_video_cutter);
            default: return key;
        }
    }

    private void populateDisplaySortSection(
            LinearLayout box,
            int theme,
            int textPrimary,
            int textSecondary,
            int accent,
            int divider,
            boolean dark
    ) {
        // בורר תצוגת רשימה / רשת נקי בסגנון זכוכית
        boolean isGrid = PrefsManager.isGridLayout(this);
        LinearLayout segWrap = new LinearLayout(this);
        segWrap.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable segWrapBg = new GradientDrawable();
        segWrapBg.setCornerRadius(dp(12));
        segWrapBg.setColor(ColorUtils.setAlphaComponent(accent, dark ? 22 : 12));
        segWrapBg.setStroke(Math.max(1, dp(1)), ColorUtils.setAlphaComponent(divider, 90));
        segWrap.setBackground(segWrapBg);
        segWrap.setPadding(dp(4), dp(4), dp(4), dp(4));
        LinearLayout.LayoutParams segLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        segLp.topMargin = dp(6);
        segLp.bottomMargin = dp(8);
        segWrap.setLayoutParams(segLp);

        segWrap.addView(createSegmentedItem("תצוגת רשימה", !isGrid, accent, textPrimary, () -> {
            PrefsManager.setGeneralGridLayoutAndSyncNonMedia(this, false);
            renderSettingsUi();
        }));
        segWrap.addView(createSegmentedItem("תצוגת רשת", isGrid, accent, textPrimary, () -> {
            PrefsManager.setGeneralGridLayoutAndSyncNonMedia(this, true);
            renderSettingsUi();
        }));
        box.addView(segWrap);

        addSwitchRow(box,
                "קבצים מוסתרים",
                "",
                PrefsManager.isShowHidden(this),
                textPrimary, textSecondary,
                checked -> PrefsManager.setShowHidden(this, checked));

        addSwitchRow(box,
                "תצוגה מקדימה לקבצים",
                "",
                PrefsManager.isShowThumbnails(this),
                textPrimary, textSecondary,
                checked -> PrefsManager.setShowThumbnails(this, checked));

        addSwitchRow(box,
                "תמונה ממוזערת לתיקייה",
                "הצגת תמונה ממוזערת על תיקיות שמע, תמונות ווידאו",
                PrefsManager.isShowFolderThumbnails(this),
                textPrimary, textSecondary,
                checked -> PrefsManager.setShowFolderThumbnails(this, checked));

        addSwitchRow(box,
                "חישוב גודל תיקיות",
                "",
                PrefsManager.isShowFolderSizes(this),
                textPrimary, textSecondary,
                checked -> PrefsManager.setShowFolderSizes(this, checked));

        // מיון ברירת מחדל כתת-רשימה מוטמעת
        addRowDivider(box, divider, 2, 2);
        LinearLayout sortHeaderRow = new LinearLayout(this);
        sortHeaderRow.setOrientation(LinearLayout.HORIZONTAL);
        sortHeaderRow.setGravity(Gravity.CENTER_VERTICAL);
        sortHeaderRow.setPadding(dp(2), dp(13), dp(2), dp(13));
        sortHeaderRow.setForeground(createSelectableRipple());

        LinearLayout sTextCol = new LinearLayout(this);
        sTextCol.setOrientation(LinearLayout.VERTICAL);
        TextView tvSortTitle = new TextView(this);
        tvSortTitle.setText("מיון ברירת מחדל");
        tvSortTitle.setTextColor(textPrimary);
        tvSortTitle.setTextSize(ThemeHelper.spScaled(this, 14f));
        tvSortTitle.setTypeface(Typeface.DEFAULT_BOLD);
        sTextCol.addView(tvSortTitle);

        TextView tvSortCurrent = new TextView(this);
        tvSortCurrent.setText(friendlySortSummary());
        tvSortCurrent.setTextColor(textSecondary);
        tvSortCurrent.setTextSize(ThemeHelper.spScaled(this, 12f));
        sTextCol.addView(tvSortCurrent);
        sortHeaderRow.addView(sTextCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView ivSortChevron = new ImageView(this);
        ivSortChevron.setImageResource(R.drawable.ic_chevron_left);
        ivSortChevron.setColorFilter(nestedSortExpanded ? accent : textSecondary);
        ivSortChevron.setRotation(nestedSortExpanded ? -90f : 0f);
        sortHeaderRow.addView(ivSortChevron, new LinearLayout.LayoutParams(dp(20), dp(20)));
        box.addView(sortHeaderRow);

        LinearLayout sortSubBox = new LinearLayout(this);
        sortSubBox.setOrientation(LinearLayout.VERTICAL);
        sortSubBox.setVisibility(nestedSortExpanded ? View.VISIBLE : View.GONE);
        GradientDrawable sSubBg = new GradientDrawable();
        sSubBg.setCornerRadius(dp(14));
        sSubBg.setColor(ColorUtils.setAlphaComponent(accent, dark ? 22 : 12));
        sSubBg.setStroke(Math.max(1, dp(1)), ColorUtils.setAlphaComponent(divider, 95));
        sortSubBox.setBackground(sSubBg);
        sortSubBox.setPadding(dp(12), dp(6), dp(12), dp(6));

        rebuildInlineSortOptions(sortSubBox, tvSortCurrent, textPrimary, textSecondary, accent, divider);
        box.addView(sortSubBox);

        sortHeaderRow.setOnClickListener(v -> {
            beginSmoothTransition();
            nestedSortExpanded = !nestedSortExpanded;
            sortSubBox.setVisibility(nestedSortExpanded ? View.VISIBLE : View.GONE);
            ivSortChevron.setColorFilter(nestedSortExpanded ? accent : textSecondary);
            if (!PrefsManager.isReduceAnimations(this)) {
                ivSortChevron.animate().rotation(nestedSortExpanded ? -90f : 0f).setDuration(190L).start();
            } else {
                ivSortChevron.setRotation(nestedSortExpanded ? -90f : 0f);
            }
        });
    }

    private void rebuildInlineSortOptions(
            LinearLayout sortSubBox,
            TextView tvSortCurrent,
            int textPrimary,
            int textSecondary,
            int accent,
            int divider
    ) {
        sortSubBox.removeAllViews();
        int curMode = PrefsManager.getSortMode(this);
        final int[] modeValues = {
                PrefsManager.SORT_NAME,
                PrefsManager.SORT_CREATED,
                PrefsManager.SORT_DATE,
                PrefsManager.SORT_SIZE,
                PrefsManager.SORT_TYPE
        };
        final String[] modeLabels = {
                "לפי שם (מספרים ועברית תחילה)",
                "לפי תאריך יצירה",
                "לפי תאריך שינוי",
                "לפי משקל קובץ",
                "לפי סוג קובץ"
        };

        for (int i = 0; i < modeValues.length; i++) {
            final int modeVal = modeValues[i];
            final String label = modeLabels[i];
            if (i > 0) {
                addRowDivider(sortSubBox, divider, 0, 0);
            }
            boolean selected = (curMode == modeVal);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(4), dp(11), dp(4), dp(11));
            row.setForeground(createSelectableRipple());

            TextView tv = new TextView(this);
            tv.setText(label);
            tv.setTextColor(selected ? accent : textPrimary);
            tv.setTextSize(ThemeHelper.spScaled(this, 13f));
            tv.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            row.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            if (selected) {
                ImageView ivCheck = new ImageView(this);
                ivCheck.setImageResource(R.drawable.ic_check);
                ivCheck.setColorFilter(accent);
                row.addView(ivCheck, new LinearLayout.LayoutParams(dp(18), dp(18)));
            }

            row.setOnClickListener(v -> {
                PrefsManager.setSortMode(this, modeVal);
                tvSortCurrent.setText(friendlySortSummary());
                rebuildInlineSortOptions(sortSubBox, tvSortCurrent, textPrimary, textSecondary, accent, divider);
            });
            sortSubBox.addView(row);
        }

        addRowDivider(sortSubBox, divider, 4, 4);
        boolean asc = PrefsManager.isSortAscending(this);
        LinearLayout dirRow = new LinearLayout(this);
        dirRow.setOrientation(LinearLayout.HORIZONTAL);
        dirRow.setGravity(Gravity.CENTER_VERTICAL);
        dirRow.setPadding(dp(4), dp(11), dp(4), dp(11));
        dirRow.setForeground(createSelectableRipple());

        LinearLayout dTextCol = new LinearLayout(this);
        dTextCol.setOrientation(LinearLayout.VERTICAL);
        TextView tvDirTitle = new TextView(this);
        tvDirTitle.setText("סדר המיון");
        tvDirTitle.setTextColor(textPrimary);
        tvDirTitle.setTextSize(ThemeHelper.spScaled(this, 13f));
        tvDirTitle.setTypeface(Typeface.DEFAULT_BOLD);
        dTextCol.addView(tvDirTitle);

        TextView tvDirValue = new TextView(this);
        tvDirValue.setText(asc ? "עולה (א-ת / מהקטן לגדול)" : "יורד (ת-א / מהגדול והחדש)");
        tvDirValue.setTextColor(accent);
        tvDirValue.setTextSize(ThemeHelper.spScaled(this, 12f));
        dTextCol.addView(tvDirValue);
        dirRow.addView(dTextCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView ivDirIcon = new ImageView(this);
        ivDirIcon.setImageResource(asc ? R.drawable.ic_chevron_up : R.drawable.ic_chevron_down);
        ivDirIcon.setColorFilter(accent);
        dirRow.addView(ivDirIcon, new LinearLayout.LayoutParams(dp(20), dp(20)));

        dirRow.setOnClickListener(v -> {
            PrefsManager.setSortAscending(this, !asc);
            tvSortCurrent.setText(friendlySortSummary());
            rebuildInlineSortOptions(sortSubBox, tvSortCurrent, textPrimary, textSecondary, accent, divider);
        });
        sortSubBox.addView(dirRow);
    }

    private View createSegmentedItem(String label, boolean active, int accent, int textPrimary, Runnable onClick) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setGravity(Gravity.CENTER);
        tv.setTextSize(ThemeHelper.spScaled(this, 13f));
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(active ? ThemeHelper.getOnAccentTextColor(accent) : textPrimary);
        tv.setPadding(dp(8), dp(10), dp(8), dp(10));

        if (active) {
            GradientDrawable activeBg = new GradientDrawable();
            activeBg.setCornerRadius(dp(9));
            activeBg.setColor(accent);
            tv.setBackground(activeBg);
        } else {
            tv.setForeground(createSelectableRipple());
        }

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tv.setLayoutParams(lp);
        tv.setOnClickListener(v -> onClick.run());
        return tv;
    }

    private void populateDefaultsSection(
            LinearLayout box,
            Map<String, String> savedDefaults,
            int theme,
            int textPrimary,
            int textSecondary,
            int accent,
            int divider,
            boolean dark
    ) {
        if (savedDefaults.isEmpty()) {
            TextView tvDefInfo = new TextView(this);
            tvDefInfo.setText("לא הוגדרו סיומות קבצים קבועות.");
            tvDefInfo.setTextColor(textSecondary);
            tvDefInfo.setTextSize(ThemeHelper.spScaled(this, 13f));
            tvDefInfo.setPadding(dp(2), dp(8), dp(2), dp(4));
            box.addView(tvDefInfo);
            return;
        }

        List<String> sortedExts = new ArrayList<>(savedDefaults.keySet());
        Collections.sort(sortedExts);
        for (int idx = 0; idx < sortedExts.size(); idx++) {
            if (idx > 0) {
                addRowDivider(box, divider, 0, 0);
            }
            final String ext = sortedExts.get(idx);
            final String actName = friendlyActionName(savedDefaults.get(ext));
            LinearLayout defRow = new LinearLayout(this);
            defRow.setOrientation(LinearLayout.HORIZONTAL);
            defRow.setGravity(Gravity.CENTER_VERTICAL);
            defRow.setPadding(dp(2), dp(11), dp(2), dp(11));

            TextView tvItem = new TextView(this);
            tvItem.setText("\u2066" + ext + "\u2069  •  " + actName);
            tvItem.setTextColor(textPrimary);
            tvItem.setTextSize(ThemeHelper.spScaled(this, 13.5f));
            tvItem.setTypeface(Typeface.DEFAULT_BOLD);
            defRow.addView(tvItem, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            ImageButton btnRemoveDef = ThemeHelper.createBorderlessSvgButton(
                    this, R.drawable.ic_close, dangerOrMutedTint(dark), "הסר", 30, 6, v -> {
                        PrefsManager.setDefaultOpenAction(this, ext, null);
                        Toast.makeText(this, "הוסרה ברירת המחדל לסיומת " + ext, Toast.LENGTH_SHORT).show();
                        renderSettingsUi();
                    });
            defRow.addView(btnRemoveDef);
            box.addView(defRow);
        }

        if (sortedExts.size() > 1) {
            addRowDivider(box, divider, 2, 2);
            addActionListRow(box,
                    "איפוס כל ברירות המחדל",
                    "מחיקת כל ההעדפות השמורות לסיומות קבצים",
                    R.drawable.ic_reset,
                    accent, accent, textSecondary, divider, false,
                    v -> {
                        PrefsManager.clearAllDefaultOpenActions(this);
                        Toast.makeText(this, "כל ברירות המחדל אופסו", Toast.LENGTH_SHORT).show();
                        renderSettingsUi();
                    });
        }
    }

    private void populateStorageSection(
            LinearLayout box,
            int theme,
            int textPrimary,
            int textSecondary,
            int accent,
            int divider,
            int dangerColor
    ) {
        addActionListRow(box,
                "ניתוח אחסון",
                "סקירת נפח לפי קטגוריות, קבצים גדולים ותיקיות",
                R.drawable.ic_analyzer,
                accent, textPrimary, textSecondary, divider, true,
                v -> {
                    startActivity(new Intent(this, StorageAnalyzerActivity.class));
                    ThemeHelper.overrideOpenTransition(this);
                });

        addSwitchRow(box,
                "התרעת 96% תפוסה",
                "",
                PrefsManager.isAlert96Enabled(this),
                textPrimary, textSecondary,
                checked -> {
                    PrefsManager.setAlert96Enabled(this, checked);
                    if (checked) StorageUtils.checkAndNotify96Threshold(this);
                });

        addSwitchRow(box,
                "העברה לסל המיחזור",
                "",
                PrefsManager.isUseRecycleBin(this),
                textPrimary, textSecondary,
                checked -> PrefsManager.setUseRecycleBin(this, checked));

        addSwitchRow(box,
                "אישור לפני מחיקה",
                "",
                PrefsManager.isConfirmDelete(this),
                textPrimary, textSecondary,
                checked -> PrefsManager.setConfirmDelete(this, checked));

        int trashCount = RecycleBinManager.getTrashItemCount(this);
        String trashSize = StorageUtils.formatSize(RecycleBinManager.getTrashTotalBytes(this));
        addRowDivider(box, divider, 2, 2);
        addActionListRow(box,
                "ריקון סל המיחזור",
                trashCount + " פריטים  •  " + trashSize,
                R.drawable.ic_recycle_bin,
                dangerColor, dangerColor, textSecondary, divider, false,
                v -> confirmEmptyRecycleBinTwoStep());

        addRowDivider(box, divider, 2, 2);
        addActionListRow(box,
                "ניקוי מטמון זמני",
                "פינוי תמונות ממוזערות וקבצים זמניים",
                R.drawable.ic_delete,
                textSecondary, textPrimary, textSecondary, divider, true,
                v -> clearTemporaryCaches());
    }

    private void populateUpdatesSection(
            LinearLayout box,
            int theme,
            int textPrimary,
            int textSecondary,
            int accent,
            int divider,
            int dangerColor
    ) {
        addSwitchRow(box,
                "בדיקת עדכונים אוטומטית",
                "",
                PrefsManager.isAutoCheckUpdates(this),
                textPrimary, textSecondary,
                checked -> PrefsManager.setAutoCheckUpdates(this, checked));

        addRowDivider(box, divider, 2, 2);
        addActionListRow(box,
                "בדיקת עדכון כעת",
                "גרסה מותקנת: " + BuildConfig.VERSION_NAME,
                R.drawable.ic_info,
                accent, textPrimary, textSecondary, divider, true,
                v -> UpdateChecker.checkForUpdates(this, true));

        addRowDivider(box, divider, 2, 2);
        addActionListRow(box,
                "יומן אבחון ותקלות",
                "צפייה בלוגים ושיתוף דוח טכני",
                R.drawable.ic_bug_report,
                textSecondary, textPrimary, textSecondary, divider, true,
                v -> {
                    startActivity(new Intent(this, DiagnosticsActivity.class));
                    ThemeHelper.overrideOpenTransition(this);
                });

        addRowDivider(box, divider, 2, 2);
        addActionListRow(box,
                "איפוס הגדרות",
                "החזרת כל העדפות האפליקציה לברירת המחדל",
                R.drawable.ic_reset,
                dangerColor, dangerColor, textSecondary, divider, false,
                v -> confirmResetSettingsTwoStep());
    }

    private void addActionListRow(
            LinearLayout parent,
            String title,
            String subtitle,
            int iconRes,
            int iconTint,
            int titleColor,
            int subtitleColor,
            int divider,
            boolean showTrailingChevron,
            View.OnClickListener listener
    ) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), dp(12), dp(2), dp(12));
        row.setForeground(createSelectableRipple());

        ImageView ivLeading = new ImageView(this);
        ivLeading.setImageResource(iconRes);
        ivLeading.setColorFilter(iconTint);
        LinearLayout.LayoutParams leadLp = new LinearLayout.LayoutParams(dp(20), dp(20));
        leadLp.setMarginEnd(dp(12));
        row.addView(ivLeading, leadLp);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextColor(titleColor);
        tvTitle.setTextSize(ThemeHelper.spScaled(this, 13.5f));
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        textCol.addView(tvTitle);

        if (subtitle != null && !subtitle.isEmpty()) {
            TextView tvSub = new TextView(this);
            tvSub.setText(subtitle);
            tvSub.setTextColor(subtitleColor);
            tvSub.setTextSize(ThemeHelper.spScaled(this, 11.5f));
            LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            sLp.topMargin = dp(2);
            textCol.addView(tvSub, sLp);
        }
        row.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (showTrailingChevron) {
            ImageView ivTail = new ImageView(this);
            ivTail.setImageResource(R.drawable.ic_chevron_left);
            ivTail.setColorFilter(subtitleColor);
            row.addView(ivTail, new LinearLayout.LayoutParams(dp(18), dp(18)));
        }

        row.setOnClickListener(listener);
        parent.addView(row);
    }

    private int dangerOrMutedTint(boolean dark) {
        return dark ? Color.parseColor("#F87171") : Color.parseColor("#DC2626");
    }

    private String friendlySortSummary() {
        int mode = PrefsManager.getSortMode(this);
        String name;
        if (mode == PrefsManager.SORT_SIZE) name = "משקל";
        else if (mode == PrefsManager.SORT_DATE) name = "שינוי";
        else if (mode == PrefsManager.SORT_TYPE) name = "סוג";
        else if (mode == PrefsManager.SORT_CREATED) name = "יצירה";
        else name = "שם";
        return name + " • " + (PrefsManager.isSortAscending(this) ? "עולה" : "יורד");
    }

    private void clearTemporaryCaches() {
        FileThumbnailLoader.clearMemoryCache();
        ThemeHelper.invalidateWallpaperCache();
        FileScanCache.invalidate();
        new Thread(() -> {
            long freed = deleteDirContentsSafe(getCacheDir()) + deleteDirContentsSafe(getExternalCacheDir());
            runOnUiThread(() -> Toast.makeText(
                    this,
                    freed > 0
                            ? "נוקה מטמון זמני (" + StorageUtils.formatSize(freed) + ")"
                            : "המטמון הזמני כבר נקי",
                    Toast.LENGTH_SHORT
            ).show());
        }).start();
    }

    private static long deleteDirContentsSafe(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return 0L;
        long bytes = 0L;
        File[] children = dir.listFiles();
        if (children == null) return 0L;
        for (File f : children) {
            if (f.isDirectory()) {
                bytes += deleteDirContentsSafe(f);
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            } else {
                long len = f.length();
                if (f.delete()) bytes += len;
            }
        }
        return bytes;
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
                            expandedSections.clear();
                            nestedGesturesExpanded = false;
                            nestedSortExpanded = false;
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

    private LinearLayout createSectionCard(int theme) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(8), dp(16), dp(8));
        card.setBackground(ThemeHelper.createGlassCardBackground(this, theme, 18f, true, false));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
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
        int previewAccent = ThemeHelper.getThemePreviewAccentColor(this, themeId);
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

    private interface SwitchCallback {
        void onChanged(boolean checked);
    }

    private void addRowDivider(LinearLayout parent, int dividerColor, int topMarginDp, int bottomMarginDp) {
        View line = new View(this);
        line.setBackgroundColor(ColorUtils.setAlphaComponent(dividerColor, 85));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1)));
        lp.topMargin = dp(topMarginDp);
        lp.bottomMargin = dp(bottomMarginDp);
        parent.addView(line, lp);
    }

    private void addSwitchRow(LinearLayout parent, String title, String subtitle, boolean initial,
                              int textPrimary, int textSecondary, SwitchCallback cb) {
        int theme = PrefsManager.getThemeMode(this);
        if (parent.getChildCount() > 0) {
            addRowDivider(parent, ThemeHelper.getDividerColor(this, theme), 2, 2);
        }
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), dp(13), dp(2), dp(13));

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView tvT = new TextView(this);
        tvT.setText(title);
        tvT.setTextColor(textPrimary);
        tvT.setTextSize(ThemeHelper.spScaled(this, 13.5f));
        tvT.setTypeface(Typeface.DEFAULT_BOLD);
        textCol.addView(tvT);

        if (subtitle != null && !subtitle.isEmpty()) {
            TextView tvS = new TextView(this);
            tvS.setText(subtitle);
            tvS.setTextColor(textSecondary);
            tvS.setTextSize(ThemeHelper.spScaled(this, 11.5f));
            LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            sLp.topMargin = dp(2);
            textCol.addView(tvS, sLp);
        }

        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tLp.setMarginEnd(dp(12));
        row.addView(textCol, tLp);

        Switch sw = new Switch(this);
        sw.setChecked(initial);
        ThemeHelper.applySwitchTint(this, sw, theme);
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> cb.onChanged(isChecked));
        row.addView(sw);

        row.setOnClickListener(v -> sw.toggle());
        parent.addView(row);
    }

    private void openAppSystemSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()));
        startActivity(intent);
        ThemeHelper.overrideOpenTransition(this);
    }

    @Override
    public void finish() {
        super.finish();
        ThemeHelper.overrideCloseTransition(this);
    }
}
