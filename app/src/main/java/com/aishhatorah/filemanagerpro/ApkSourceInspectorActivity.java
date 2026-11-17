package com.aishhatorah.filemanagerpro;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.ColorUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * מציג קוד ורכיבים של APK עם עץ תיקיות היררכי אמיתי, פענוח AndroidManifest וסקירת DEX.
 */
public class ApkSourceInspectorActivity extends AppCompatActivity {

    public static final String EXTRA_APK_PATH = "extra_apk_path";
    private File apkFile;
    private LinearLayout contentList;
    private int theme;
    private int textPrimary;
    private int textSecondary;
    private int accent;
    private boolean dark;

    private List<ArchiveUtils.ArchiveEntryInfo> cachedEntries;
    private String currentApkPath = "";
    private int activeTab = 1; // 0 = מבנה, 1 = מניפסט, 2 = DEX
    private final Button[] tabButtons = new Button[3];

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String path = getIntent().getStringExtra(EXTRA_APK_PATH);
        if (path == null) { finish(); return; }
        apkFile = new File(path);
        if (!apkFile.exists()) { finish(); return; }

        theme = PrefsManager.getThemeMode(this);
        ThemeHelper.applyWindowStatusBar(this, theme);
        textPrimary = ThemeHelper.getPrimaryTextColor(this, theme);
        textSecondary = ThemeHelper.getSecondaryTextColor(this, theme);
        accent = ThemeHelper.getAccentColor(this, theme);
        dark = ThemeHelper.isDarkSurface(this, theme);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (activeTab == 0 && !currentApkPath.isEmpty()) {
                    navigateApkUp();
                    return;
                }
                finish();
            }
        });

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        ThemeHelper.applyWallpaperBackdrop(this, root, theme);

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(10), dp(8), dp(14), dp(10));
        ThemeHelper.applyCurvedHeaderStyle(this, topBar, theme);

        ImageButton btnBack = ThemeHelper.createBorderlessSvgButton(
                this, R.drawable.ic_arrow_back, accent, "חזרה", 40, 8, v -> {
                    if (activeTab == 0 && !currentApkPath.isEmpty()) {
                        navigateApkUp();
                    } else {
                        finish();
                    }
                });
        topBar.addView(btnBack);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("מקור ה-APK: " + apkFile.getName());
        tvTitle.setTextColor(textPrimary);
        tvTitle.setTextSize(ThemeHelper.spScaled(this, 15.5f));
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setSingleLine(true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleLp.setMarginStart(dp(6));
        topBar.addView(tvTitle, titleLp);
        root.addView(topBar);

        // לשוניות
        LinearLayout tabsRow = new LinearLayout(this);
        tabsRow.setOrientation(LinearLayout.HORIZONTAL);
        tabsRow.setPadding(dp(12), dp(10), dp(12), dp(6));

        tabButtons[0] = makeTab("קבצים ומבנה", 0, () -> {
            activeTab = 0;
            updateTabStyles();
            loadStructureTab();
        });
        tabButtons[1] = makeTab("AndroidManifest", 1, () -> {
            activeTab = 1;
            updateTabStyles();
            loadManifestTab();
        });
        tabButtons[2] = makeTab("מחלקות DEX", 2, () -> {
            activeTab = 2;
            updateTabStyles();
            loadDexTab();
        });

        for (int i = 0; i < 3; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i < 2) lp.setMarginEnd(dp(6));
            tabsRow.addView(tabButtons[i], lp);
        }
        root.addView(tabsRow);

        ScrollView scroll = new ScrollView(this);
        contentList = new LinearLayout(this);
        contentList.setOrientation(LinearLayout.VERTICAL);
        contentList.setPadding(dp(14), dp(8), dp(14), dp(24));
        scroll.addView(contentList);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        updateTabStyles();
        loadManifestTab();
    }

    private Button makeTab(String label, int index, Runnable r) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(ThemeHelper.spScaled(this, 12.5f));
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setStateListAnimator(null);
        b.setElevation(0f);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(8), dp(8), dp(8), dp(8));
        b.setOnClickListener(v -> r.run());
        return b;
    }

    private void updateTabStyles() {
        for (int i = 0; i < tabButtons.length; i++) {
            Button b = tabButtons[i];
            if (b == null) continue;
            boolean active = (i == activeTab);
            GradientDrawable gd = new GradientDrawable();
            gd.setCornerRadius(dp(12));
            gd.setColor(active
                    ? accent
                    : ColorUtils.setAlphaComponent(ThemeHelper.getCardColor(this, theme), dark ? 185 : 210));
            b.setBackground(gd);
            b.setTextColor(active
                    ? (theme == PrefsManager.THEME_NEON ? Color.parseColor("#04101A") : Color.WHITE)
                    : textPrimary);
        }
    }

    private void navigateApkUp() {
        if (currentApkPath.isEmpty()) return;
        String trimmed = currentApkPath.endsWith("/") ? currentApkPath.substring(0, currentApkPath.length() - 1) : currentApkPath;
        int slash = trimmed.lastIndexOf('/');
        currentApkPath = slash > 0 ? trimmed.substring(0, slash + 1) : "";
        loadStructureTab();
    }

    private void loadStructureTab() {
        contentList.removeAllViews();
        try {
            if (cachedEntries == null) {
                cachedEntries = ArchiveUtils.listArchiveEntries(apkFile);
            }
            List<ArchiveUtils.ArchiveTreeNode> children = ArchiveUtils.listTreeChildren(cachedEntries, currentApkPath);

            TextView hdr = new TextView(this);
            String cleanPath = currentApkPath.endsWith("/") ? currentApkPath.substring(0, currentApkPath.length() - 1) : currentApkPath;
            String pathDisplay = cleanPath.isEmpty() ? "/" : "/" + cleanPath;
            hdr.setText("נתיב בחבילה: " + pathDisplay + "  •  " + children.size() + " פריטים (מתוך " + cachedEntries.size() + ")");
            hdr.setTextColor(accent);
            hdr.setTextSize(ThemeHelper.spScaled(this, 12.5f));
            hdr.setTypeface(Typeface.DEFAULT_BOLD);
            hdr.setPadding(dp(4), 0, dp(4), dp(10));
            contentList.addView(hdr);

            if (!currentApkPath.isEmpty()) {
                addInfoCard("..", "חזרה לתיקיית האב", R.drawable.ic_arrow_back, accent, this::navigateApkUp);
            }

            for (ArchiveUtils.ArchiveTreeNode node : children) {
                if (node.directory) {
                    addInfoCard(node.displayName, "תיקייה", R.drawable.ic_folder, accent, () -> {
                        currentApkPath = node.fullPath;
                        loadStructureTab();
                    });
                } else {
                    int icon = FileThumbnailLoader.resolveFallbackDrawable(node.displayName);
                    int tint = icon == R.drawable.ic_file_generic ? textSecondary : 0;
                    addInfoCard(node.displayName, StorageUtils.formatSize(node.size), icon, tint, () -> {
                        if ("AndroidManifest.xml".equals(node.fullPath)) {
                            activeTab = 1;
                            updateTabStyles();
                            loadManifestTab();
                        } else if (node.fullPath.endsWith(".dex") && !node.fullPath.contains("/")) {
                            activeTab = 2;
                            updateTabStyles();
                            loadDexEntry(node.fullPath);
                        } else if (MediaFormatHelper.isTextFile(node.displayName) || node.displayName.endsWith(".xml")) {
                            try {
                                byte[] raw = ArchiveUtils.readEntryBytes(apkFile, node.fullPath, null);
                                String text = node.displayName.endsWith(".xml") ? ApkSourceParser.decodeBinaryXml(raw) : new String(raw, StandardCharsets.UTF_8);
                                openReadOnlyViewer(node.fullPath, text);
                            } catch (Exception ex) {
                                Toast.makeText(this, "קובץ בינארי פנימי", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(this, "קובץ בינארי (" + StorageUtils.formatSize(node.size) + ")", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "שגיאה בקריאת מבנה ה-APK: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void loadManifestTab() {
        contentList.removeAllViews();
        try {
            byte[] raw = ArchiveUtils.readEntryBytes(apkFile, "AndroidManifest.xml", null);
            String decoded = ApkSourceParser.decodeBinaryXml(raw);
            Button btnFull = ThemedDialogHelper.createThemedButton(this, "פתח בעורך קוד מלא", theme, true, false);
            btnFull.setOnClickListener(v -> openReadOnlyViewer("AndroidManifest.xml", decoded));
            contentList.addView(btnFull);

            LinearLayout codeCard = new LinearLayout(this);
            codeCard.setOrientation(LinearLayout.VERTICAL);
            codeCard.setPadding(dp(12), dp(10), dp(12), dp(10));
            codeCard.setBackground(ThemeHelper.createGlassCardBackground(this, theme, 16f, true, false));
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cardLp.topMargin = dp(10);

            TextView tv = new TextView(this);
            tv.setText(decoded);
            tv.setTextColor(textPrimary);
            tv.setTypeface(Typeface.MONOSPACE);
            tv.setTextSize(12f);
            tv.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            tv.setTextIsSelectable(true);
            codeCard.addView(tv);
            contentList.addView(codeCard, cardLp);
        } catch (Exception e) {
            TextView err = new TextView(this);
            err.setText("לא ניתן לפענח AndroidManifest.xml (ייתכן שזהו צרור חבילות XAPK/APKM): " + e.getMessage());
            err.setTextColor(textSecondary);
            contentList.addView(err);
        }
    }

    private void loadDexTab() {
        loadDexEntry("classes.dex");
    }

    private void loadDexEntry(String dexName) {
        contentList.removeAllViews();
        try {
            byte[] dex = ArchiveUtils.readEntryBytes(apkFile, dexName, null);
            Map<String, List<String>> pkgs = ApkSourceParser.parseDexPackagesAndClasses(dex);
            TextView hdr = new TextView(this);
            hdr.setText(dexName + "  •  " + pkgs.size() + " חבילות (Packages)");
            hdr.setTextColor(accent);
            hdr.setTypeface(Typeface.DEFAULT_BOLD);
            hdr.setPadding(dp(4), 0, dp(4), dp(10));
            contentList.addView(hdr);

            int shown = 0;
            for (Map.Entry<String, List<String>> entry : pkgs.entrySet()) {
                if (shown++ > 180) break;
                addInfoCard(entry.getKey(), entry.getValue().size() + " מחלקות", R.drawable.ic_code, accent, () -> {
                    StringBuilder sb = new StringBuilder("// Package: ").append(entry.getKey()).append("\n\n");
                    for (String cls : entry.getValue()) sb.append("class ").append(cls).append("\n");
                    openReadOnlyViewer(entry.getKey() + ".java", sb.toString());
                });
            }
        } catch (Exception e) {
            TextView err = new TextView(this);
            err.setText("לא נמצא " + dexName + " ישירות בחבילה זו.");
            err.setTextColor(textSecondary);
            contentList.addView(err);
        }
    }

    private void openReadOnlyViewer(String title, String content) {
        Intent i = new Intent(this, CodeEditorActivity.class);
        i.putExtra(CodeEditorActivity.EXTRA_READ_ONLY_TITLE, title);
        i.putExtra(CodeEditorActivity.EXTRA_READ_ONLY_CONTENT, content);
        startActivity(i);
    }

    private void addInfoCard(String title, String sub, int iconRes, int iconTint, Runnable onClick) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(10), dp(14), dp(10));
        card.setBackground(ThemeHelper.createGlassCardBackground(this, theme, 14f, true, false));

        ImageView iv = new ImageView(this);
        iv.setImageResource(iconRes);
        if (iconTint != 0) iv.setColorFilter(iconTint);
        else iv.clearColorFilter();
        int icSz = dp(24);
        LinearLayout.LayoutParams icLp = new LinearLayout.LayoutParams(icSz, icSz);
        icLp.setMarginEnd(dp(12));
        card.addView(iv, icLp);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView t1 = new TextView(this);
        t1.setText(title);
        t1.setTextColor(textPrimary);
        t1.setTextSize(ThemeHelper.spScaled(this, 13.5f));
        t1.setTypeface(Typeface.DEFAULT_BOLD);
        textCol.addView(t1);

        TextView t2 = new TextView(this);
        t2.setText(sub);
        t2.setTextColor(textSecondary);
        t2.setTextSize(ThemeHelper.spScaled(this, 11.5f));
        textCol.addView(t2);

        card.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(7);
        card.setOnClickListener(v -> onClick.run());
        contentList.addView(card, lp);
    }
}
