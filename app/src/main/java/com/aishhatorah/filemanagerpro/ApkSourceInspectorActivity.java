package com.aishhatorah.filemanagerpro;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * מציג מקור וקוד מלא של APK:
 * נפתח כברירת מחדל ב"קבצים ומבנה", מציג אייקונים מותאמים לכל רכיב,
 * ומאפשר פתיחה ישירה של AndroidManifest, כל קובץ XML מקומפל, resources.arsc וקבצי DEX (מחלקות, מתודות, שדות ומחרוזות).
 */
public class ApkSourceInspectorActivity extends AppCompatActivity {

    public static final String EXTRA_APK_PATH = "extra_apk_path";
    private static final int SCREEN_STRUCTURE = 0;
    private static final int SCREEN_DEX = 1;
    private static final int SCREEN_ARSC = 2;

    private File apkFile;
    private LinearLayout contentList;
    private TextView tvTitle;
    private int theme;
    private int textPrimary;
    private int textSecondary;
    private int accent;
    private boolean dark;

    private List<ArchiveUtils.ArchiveEntryInfo> cachedEntries;
    private String currentApkPath = "";
    private int currentScreenMode = SCREEN_STRUCTURE;
    private String activeDexName = "classes.dex";
    private int dexSubTab = 0; // 0 = חבילות ומחלקות, 1 = מתודות, 2 = שדות, 3 = מחרוזות
    private String dexSearchQuery = "";
    private String arscSearchQuery = "";

    private ApkSourceParser.DexInspectionResult cachedDexResult;
    private String cachedDexSourceName;
    private ApkSourceParser.ArscInspectionResult cachedArscResult;

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
                handleBackNavigation();
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
                this, R.drawable.ic_arrow_back, accent, "חזרה", 40, 8, v -> handleBackNavigation());
        topBar.addView(btnBack);

        tvTitle = new TextView(this);
        tvTitle.setText("מקור: " + apkFile.getName());
        tvTitle.setTextColor(textPrimary);
        tvTitle.setTextSize(ThemeHelper.spScaled(this, 15.5f));
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setSingleLine(true);
        tvTitle.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleLp.setMarginStart(dp(6));
        topBar.addView(tvTitle, titleLp);
        root.addView(topBar);

        ScrollView scroll = new ScrollView(this);
        contentList = new LinearLayout(this);
        contentList.setOrientation(LinearLayout.VERTICAL);
        contentList.setPadding(dp(14), dp(10), dp(14), dp(24));
        scroll.addView(contentList);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        loadStructureTab();
    }

    private void handleBackNavigation() {
        if (currentScreenMode != SCREEN_STRUCTURE) {
            currentScreenMode = SCREEN_STRUCTURE;
            if (tvTitle != null) {
                tvTitle.setText("מקור: " + apkFile.getName());
            }
            loadStructureTab();
            return;
        }
        if (!currentApkPath.isEmpty()) {
            navigateApkUp();
            return;
        }
        finish();
        ThemeHelper.overrideCloseTransition(this);
    }

    private Button makeTab(String label, Runnable r) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(ThemeHelper.spScaled(this, 12f));
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setSingleLine(true);
        b.setEllipsize(TextUtils.TruncateAt.END);
        b.setStateListAnimator(null);
        b.setElevation(0f);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(6), dp(9), dp(6), dp(9));
        b.setOnClickListener(v -> r.run());
        return b;
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
            hdr.setText("נתיב: " + pathDisplay + "  •  " + children.size() + " פריטים (סה״כ " + cachedEntries.size() + ")");
            hdr.setTextColor(accent);
            hdr.setTextSize(ThemeHelper.spScaled(this, 12.5f));
            hdr.setTypeface(Typeface.DEFAULT_BOLD);
            hdr.setPadding(dp(4), dp(2), dp(4), dp(10));
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
                    String lower = node.displayName.toLowerCase(Locale.ROOT);
                    String fullLower = node.fullPath.toLowerCase(Locale.ROOT);
                    int icon = resolveApkEntryIcon(lower);
                    int tint = resolveApkEntryTint(lower);
                    String sub = resolveApkEntrySubtitle(lower, node.size);

                    addInfoCard(node.displayName, sub, icon, tint, () -> handleApkEntryClick(node, lower, fullLower));
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "שגיאה בקריאת מבנה ה-APK: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private int resolveApkEntryIcon(String lower) {
        if ("androidmanifest.xml".equals(lower)) return R.drawable.ic_code;
        if ("resources.arsc".equals(lower)) return R.drawable.ic_analyzer;
        if (lower.endsWith(".dex")) return R.drawable.ic_code;
        if (lower.endsWith(".xml")) return R.drawable.ic_code;
        if (lower.endsWith(".so")) return R.drawable.ic_settings;
        if (lower.endsWith(".rsa") || lower.endsWith(".dsa") || lower.endsWith(".ec") || lower.endsWith(".sf") || lower.endsWith(".mf")) {
            return R.drawable.ic_lock;
        }
        return FileThumbnailLoader.resolveFallbackDrawable(lower);
    }

    private int resolveApkEntryTint(String lower) {
        if ("androidmanifest.xml".equals(lower)) return accent;
        if ("resources.arsc".equals(lower)) return Color.parseColor("#D97706");
        if (lower.endsWith(".dex")) return Color.parseColor("#6366F1");
        if (lower.endsWith(".xml")) return accent;
        if (lower.endsWith(".so")) return Color.parseColor("#0D9488");
        int fb = FileThumbnailLoader.resolveFallbackDrawable(lower);
        return fb == R.drawable.ic_file_generic ? textSecondary : 0;
    }

    private String resolveApkEntrySubtitle(String lower, long size) {
        String sz = StorageUtils.formatSize(size);
        if ("androidmanifest.xml".equals(lower)) return "מניפסט בינארי (AXML) • " + sz;
        if ("resources.arsc".equals(lower)) return "טבלת משאבים מקומפלת • " + sz;
        if (lower.endsWith(".dex")) return "קוד bytecode מקומפל (DEX) • " + sz;
        if (lower.endsWith(".xml")) return "XML מקומפל / טקסט • " + sz;
        if (lower.endsWith(".so")) return "ספריית קוד מקורית (ELF) • " + sz;
        return sz;
    }

    private void handleApkEntryClick(ArchiveUtils.ArchiveTreeNode node, String lower, String fullLower) {
        try {
            if ("androidmanifest.xml".equals(fullLower) || "androidmanifest.xml".equals(lower)) {
                byte[] raw = ArchiveUtils.readEntryBytes(apkFile, node.fullPath, null);
                String decoded = ApkSourceParser.decodeBinaryXml(raw);
                openReadOnlyViewer(node.fullPath, decoded);
                return;
            }
            if (lower.endsWith(".dex")) {
                activeDexName = node.fullPath;
                currentScreenMode = SCREEN_DEX;
                if (tvTitle != null) {
                    tvTitle.setText("מקור: " + node.displayName);
                }
                loadDexEntry(node.fullPath);
                return;
            }
            if ("resources.arsc".equals(lower)) {
                currentScreenMode = SCREEN_ARSC;
                if (tvTitle != null) {
                    tvTitle.setText("מקור: resources.arsc");
                }
                loadArscTab();
                return;
            }
            if (lower.endsWith(".xml")) {
                byte[] raw = ArchiveUtils.readEntryBytes(apkFile, node.fullPath, null);
                String decoded = ApkSourceParser.decodeBinaryXml(raw);
                openReadOnlyViewer(node.fullPath, decoded);
                return;
            }
            if (MediaFormatHelper.isTextFile(lower) || MediaFormatHelper.isCodeOrSyntaxFile(lower)
                    || lower.endsWith(".sf") || lower.endsWith(".mf") || lower.endsWith(".pro")
                    || "notice".equals(lower) || "license".equals(lower)) {
                byte[] raw = ArchiveUtils.readEntryBytes(apkFile, node.fullPath, null);
                openReadOnlyViewer(node.fullPath, new String(raw, StandardCharsets.UTF_8));
                return;
            }
            if (MediaFormatHelper.isImageFile(lower)) {
                File cacheDir = new File(getCacheDir(), "arch_preview");
                if (!cacheDir.exists()) cacheDir.mkdirs();
                File out = new File(cacheDir, System.currentTimeMillis() + "_" + node.displayName);
                ArchiveUtils.extractEntryToFile(apkFile, node.fullPath, null, out);
                FileOpenWithHelper.executeOpenAction(this, out, FileOpenWithHelper.ACTION_INTERNAL_IMAGE);
                return;
            }
            Toast.makeText(this, "קובץ בינארי (" + StorageUtils.formatSize(node.size) + ")", Toast.LENGTH_SHORT).show();
        } catch (Exception ex) {
            Toast.makeText(this, "לא ניתן לפתוח קובץ זה: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void loadDexEntry(String dexName) {
        contentList.removeAllViews();
        try {
            if (cachedDexResult == null || !dexName.equals(cachedDexSourceName)) {
                byte[] dex = ArchiveUtils.readEntryBytes(apkFile, dexName, null);
                cachedDexResult = ApkSourceParser.parseDexFull(dex);
                cachedDexSourceName = dexName;
            }
            ApkSourceParser.DexInspectionResult dex = cachedDexResult;

            // סטטיסטיקת DEX
            LinearLayout statsCard = new LinearLayout(this);
            statsCard.setOrientation(LinearLayout.VERTICAL);
            statsCard.setPadding(dp(12), dp(10), dp(12), dp(10));
            statsCard.setBackground(ThemeHelper.createGlassCardBackground(this, theme, 14f, true, false));
            TextView hdr = new TextView(this);
            hdr.setText(dexName + " (DEX " + dex.dexVersion + ")\n"
                    + dex.classCount + " מחלקות  •  " + dex.methodCount + " מתודות  •  "
                    + dex.fieldCount + " שדות  •  " + dex.stringCount + " מחרוזות");
            hdr.setTextColor(textPrimary);
            hdr.setTextSize(ThemeHelper.spScaled(this, 12.5f));
            hdr.setTypeface(Typeface.DEFAULT_BOLD);
            statsCard.addView(hdr);
            LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            stLp.bottomMargin = dp(8);
            contentList.addView(statsCard, stLp);

            // תתי-לשוניות בתוך DEX: חבילות | מתודות | שדות | מחרוזות
            LinearLayout subTabs = new LinearLayout(this);
            subTabs.setOrientation(LinearLayout.HORIZONTAL);
            String[] subLabels = {"חבילות ומחלקות", "מתודות", "שדות", "מחרוזות"};
            for (int i = 0; i < subLabels.length; i++) {
                final int idx = i;
                Button sb = makeTab(subLabels[i], () -> {
                    dexSubTab = idx;
                    loadDexEntry(dexName);
                });
                boolean act = (dexSubTab == i);
                GradientDrawable sgd = new GradientDrawable();
                sgd.setCornerRadius(dp(10));
                sgd.setColor(act ? ColorUtils.setAlphaComponent(accent, dark ? 95 : 42)
                        : ColorUtils.setAlphaComponent(ThemeHelper.getCardColor(this, theme), dark ? 150 : 190));
                sgd.setStroke(Math.max(1, dp(1)), act ? accent : ThemeHelper.getDividerColor(this, theme));
                sb.setBackground(sgd);
                sb.setTextColor(act ? accent : textPrimary);
                sb.setTextSize(ThemeHelper.spScaled(this, 11f));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                if (i < subLabels.length - 1) lp.setMarginEnd(dp(4));
                subTabs.addView(sb, lp);
            }
            LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            subLp.bottomMargin = dp(8);
            contentList.addView(subTabs, subLp);

            // שדה חיפוש מהיר
            LinearLayout resultsBox = new LinearLayout(this);
            resultsBox.setOrientation(LinearLayout.VERTICAL);

            EditText etFilter = createFilterEditText("סנן לפי שם חבילה / מחלקה / מתודה / מחרוזת...", dexSearchQuery);
            etFilter.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override
                public void afterTextChanged(Editable s) {
                    dexSearchQuery = s.toString();
                    renderDexFilteredItems(resultsBox, dex);
                }
            });
            LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            etLp.bottomMargin = dp(8);
            contentList.addView(etFilter, etLp);
            contentList.addView(resultsBox);

            renderDexFilteredItems(resultsBox, dex);
        } catch (Exception e) {
            TextView err = new TextView(this);
            err.setText("לא נמצא " + dexName + " ישירות בחבילה זו.");
            err.setTextColor(textSecondary);
            contentList.addView(err);
        }
    }

    private void renderDexFilteredItems(LinearLayout targetBox, ApkSourceParser.DexInspectionResult dex) {
        targetBox.removeAllViews();
        String q = dexSearchQuery == null ? "" : dexSearchQuery.trim().toLowerCase(Locale.ROOT);
        int shown = 0;

        if (dexSubTab == 0) {
            for (Map.Entry<String, List<ApkSourceParser.DexClassDetails>> entry : dex.packages.entrySet()) {
                String pkg = entry.getKey();
                List<ApkSourceParser.DexClassDetails> matchedClasses = new ArrayList<>();
                boolean pkgMatches = q.isEmpty() || pkg.toLowerCase(Locale.ROOT).contains(q);
                for (ApkSourceParser.DexClassDetails cd : entry.getValue()) {
                    if (pkgMatches || cd.className.toLowerCase(Locale.ROOT).contains(q)) {
                        matchedClasses.add(cd);
                    }
                }
                if (matchedClasses.isEmpty()) continue;
                if (shown++ >= 220) break;

                addInfoCardInto(targetBox, pkg, matchedClasses.size() + " מחלקות", R.drawable.ic_code, accent, () -> {
                    StringBuilder sb = new StringBuilder("// Package: ").append(pkg).append("\n\n");
                    for (ApkSourceParser.DexClassDetails cd : matchedClasses) {
                        sb.append(cd.formatFlags()).append(" ").append(cd.className);
                        if (!cd.superClassName.isEmpty() && !"java.lang.Object".equals(cd.superClassName)) {
                            sb.append(" extends ").append(cd.superClassName);
                        }
                        sb.append(" {\n");
                        for (String f : cd.fields) {
                            sb.append("    ").append(f).append(";\n");
                        }
                        if (!cd.fields.isEmpty() && !cd.methods.isEmpty()) sb.append("\n");
                        for (String m : cd.methods) {
                            sb.append("    ").append(m).append(";\n");
                        }
                        sb.append("}\n\n");
                    }
                    openReadOnlyViewer(pkg + ".java", sb.toString());
                });
            }
        } else if (dexSubTab == 1) {
            for (String m : dex.methods) {
                if (!q.isEmpty() && !m.toLowerCase(Locale.ROOT).contains(q)) continue;
                if (shown++ >= 250) break;
                addMonospaceLineCard(targetBox, m, Color.parseColor("#6366F1"));
            }
        } else if (dexSubTab == 2) {
            for (String f : dex.fields) {
                if (!q.isEmpty() && !f.toLowerCase(Locale.ROOT).contains(q)) continue;
                if (shown++ >= 250) break;
                addMonospaceLineCard(targetBox, f, Color.parseColor("#0D9488"));
            }
        } else {
            for (String s : dex.strings) {
                if (!q.isEmpty() && !s.toLowerCase(Locale.ROOT).contains(q)) continue;
                if (shown++ >= 250) break;
                addMonospaceLineCard(targetBox, "\"" + s + "\"", accent);
            }
        }

        if (shown == 0) {
            TextView empty = new TextView(this);
            empty.setText("אין תוצאות תואמות.");
            empty.setTextColor(textSecondary);
            empty.setPadding(dp(8), dp(12), dp(8), dp(12));
            targetBox.addView(empty);
        }
    }

    private void loadArscTab() {
        contentList.removeAllViews();
        try {
            if (cachedArscResult == null) {
                byte[] raw = ArchiveUtils.readEntryBytes(apkFile, "resources.arsc", null);
                cachedArscResult = ApkSourceParser.parseResourcesArsc(raw);
            }
            ApkSourceParser.ArscInspectionResult arsc = cachedArscResult;

            TextView hdr = new TextView(this);
            hdr.setText("resources.arsc"
                    + (arsc.packageName.isEmpty() ? "" : "  •  " + arsc.packageName)
                    + "  •  " + arsc.totalEntries + " משאבים ב-" + arsc.byType.size() + " קבוצות");
            hdr.setTextColor(accent);
            hdr.setTextSize(ThemeHelper.spScaled(this, 12.5f));
            hdr.setTypeface(Typeface.DEFAULT_BOLD);
            hdr.setPadding(dp(4), 0, dp(4), dp(8));
            contentList.addView(hdr);

            LinearLayout listBox = new LinearLayout(this);
            listBox.setOrientation(LinearLayout.VERTICAL);

            EditText etFilter = createFilterEditText("חפש משאב (string / color / layout / id)...", arscSearchQuery);
            etFilter.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override
                public void afterTextChanged(Editable s) {
                    arscSearchQuery = s.toString();
                    renderArscList(listBox, arsc);
                }
            });
            LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            etLp.bottomMargin = dp(8);
            contentList.addView(etFilter, etLp);
            contentList.addView(listBox);

            renderArscList(listBox, arsc);
        } catch (Exception e) {
            TextView err = new TextView(this);
            err.setText("לא ניתן לפענח resources.arsc בחבילה זו: " + e.getMessage());
            err.setTextColor(textSecondary);
            contentList.addView(err);
        }
    }

    private void renderArscList(LinearLayout listBox, ApkSourceParser.ArscInspectionResult arsc) {
        listBox.removeAllViews();
        String q = arscSearchQuery == null ? "" : arscSearchQuery.trim().toLowerCase(Locale.ROOT);
        int cards = 0;

        for (Map.Entry<String, List<ApkSourceParser.ArscResourceEntry>> entry : arsc.byType.entrySet()) {
            String typeName = entry.getKey();
            List<ApkSourceParser.ArscResourceEntry> matched = new ArrayList<>();
            boolean typeHit = q.isEmpty() || typeName.toLowerCase(Locale.ROOT).contains(q);
            for (ApkSourceParser.ArscResourceEntry item : entry.getValue()) {
                if (typeHit
                        || item.entryName.toLowerCase(Locale.ROOT).contains(q)
                        || item.valueDisplay.toLowerCase(Locale.ROOT).contains(q)) {
                    matched.add(item);
                }
            }
            if (matched.isEmpty()) continue;
            cards++;

            addInfoCardInto(listBox, "@" + typeName, matched.size() + " ערכים", R.drawable.ic_analyzer, Color.parseColor("#D97706"), () -> {
                StringBuilder sb = new StringBuilder("# resources.arsc — @" + typeName + " (" + matched.size() + ")\n\n");
                for (ApkSourceParser.ArscResourceEntry re : matched) {
                    sb.append(String.format(Locale.US, "0x%08x  %s  =  %s\n", re.resourceId, re.entryName, re.valueDisplay));
                }
                openReadOnlyViewer("resources_" + typeName + ".txt", sb.toString());
            });
        }

        if (cards == 0) {
            TextView empty = new TextView(this);
            empty.setText("לא נמצאו משאבים תואמים.");
            empty.setTextColor(textSecondary);
            empty.setPadding(dp(8), dp(12), dp(8), dp(12));
            listBox.addView(empty);
        }
    }

    private EditText createFilterEditText(String hint, String initial) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setText(initial);
        et.setSingleLine(true);
        et.setTextColor(textPrimary);
        et.setHintTextColor(textSecondary);
        et.setTextSize(ThemeHelper.spScaled(this, 13f));
        et.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        bg.setColor(ColorUtils.setAlphaComponent(ThemeHelper.getCardColor(this, theme), dark ? 190 : 225));
        bg.setStroke(Math.max(1, dp(1)), ThemeHelper.getDividerColor(this, theme));
        et.setBackground(bg);
        return et;
    }

    private void addMonospaceLineCard(LinearLayout target, String text, int badgeColor) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(9), dp(12), dp(9));
        card.setBackground(ThemeHelper.createGlassCardBackground(this, theme, 12f, true, false));

        View dot = new View(this);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(badgeColor);
        dot.setBackground(d);
        LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(dp(7), dp(7));
        dLp.setMarginEnd(dp(10));
        card.addView(dot, dLp);

        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(textPrimary);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextSize(11.5f);
        tv.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        tv.setTextIsSelectable(true);
        card.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(5);
        target.addView(card, lp);
    }

    private void openReadOnlyViewer(String title, String content) {
        Intent i = new Intent(this, CodeEditorActivity.class);
        i.putExtra(CodeEditorActivity.EXTRA_READ_ONLY_TITLE, title);
        i.putExtra(CodeEditorActivity.EXTRA_READ_ONLY_CONTENT, content);
        startActivity(i);
    }

    private void addInfoCard(String title, String sub, int iconRes, int iconTint, Runnable onClick) {
        addInfoCardInto(contentList, title, sub, iconRes, iconTint, onClick);
    }

    private void addInfoCardInto(LinearLayout target, String title, String sub, int iconRes, int iconTint, Runnable onClick) {
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
        target.addView(card, lp);
    }
}

