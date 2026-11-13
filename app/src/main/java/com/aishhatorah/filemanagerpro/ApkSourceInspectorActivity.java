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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * סעיף 16: מציג קוד ורכיבים של APK (בסגנון MT Manager):
 * - צפייה במבנה הקבצים של ה-APK וחילוץ מהיר
 * - פענוח קריא של AndroidManifest.xml הבינארי (AXML)
 * - סקירת חבילות ומחלקות מתוך classes.dex
 */
public class ApkSourceInspectorActivity extends AppCompatActivity {

    public static final String EXTRA_APK_PATH = "extra_apk_path";
    private File apkFile;
    private LinearLayout contentList;
    private int theme;
    private int cardBg;
    private int textPrimary;
    private int textSecondary;
    private int accent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String path = getIntent().getStringExtra(EXTRA_APK_PATH);
        if (path == null) { finish(); return; }
        apkFile = new File(path);
        if (!apkFile.exists()) { finish(); return; }

        theme = PrefsManager.getThemeMode(this);
        ThemeHelper.applyWindowStatusBar(this, theme);
        int bg = ThemeHelper.getBgColor(this, theme);
        cardBg = ThemeHelper.getCardColor(this, theme);
        textPrimary = ThemeHelper.getPrimaryTextColor(this, theme);
        textSecondary = ThemeHelper.getSecondaryTextColor(this, theme);
        accent = ThemeHelper.getAccentColor(this, theme);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(bg);

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(ThemeHelper.getTopBarColor(this, theme));
        topBar.setPadding(18, 16, 18, 16);

        ImageButton btnBack = new ImageButton(this);
        btnBack.setImageResource(R.drawable.ic_arrow_back);
        btnBack.setColorFilter(textPrimary);
        btnBack.setBackgroundColor(Color.TRANSPARENT);
        btnBack.setOnClickListener(v -> finish());
        topBar.addView(btnBack);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("מקור ה-APK: " + apkFile.getName());
        tvTitle.setTextColor(textPrimary);
        tvTitle.setTextSize(15.5f);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setSingleLine(true);
        topBar.addView(tvTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(topBar);

        // לשוניות
        LinearLayout tabsRow = new LinearLayout(this);
        tabsRow.setOrientation(LinearLayout.HORIZONTAL);
        tabsRow.setPadding(16, 12, 16, 12);
        tabsRow.addView(makeTab("קבצים ומבנה", () -> loadStructureTab()), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        tabsRow.addView(makeTab("AndroidManifest", () -> loadManifestTab()), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        tabsRow.addView(makeTab("מחלקות DEX", () -> loadDexTab()), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(tabsRow);

        ScrollView scroll = new ScrollView(this);
        contentList = new LinearLayout(this);
        contentList.setOrientation(LinearLayout.VERTICAL);
        contentList.setPadding(24, 12, 24, 36);
        scroll.addView(contentList);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        loadManifestTab();
    }

    private Button makeTab(String label, Runnable r) {
        Button b = ThemedDialogHelper.createThemedButton(this, label, theme, false, false);
        b.setTextSize(12.5f);
        b.setPadding(8, 12, 8, 12);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginEnd(6);
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> r.run());
        return b;
    }

    private void loadStructureTab() {
        contentList.removeAllViews();
        try {
            List<ArchiveUtils.ArchiveEntryInfo> entries = ArchiveUtils.listArchiveEntries(apkFile);
            TextView hdr = new TextView(this);
            hdr.setText("סה\"כ רכיבים בחבילה: " + entries.size());
            hdr.setTextColor(accent);
            hdr.setTypeface(Typeface.DEFAULT_BOLD);
            hdr.setPadding(0, 0, 0, 12);
            contentList.addView(hdr);

            int shown = Math.min(entries.size(), 250);
            for (int i = 0; i < shown; i++) {
                ArchiveUtils.ArchiveEntryInfo e = entries.get(i);
                addInfoCard(e.name, e.directory ? "תיקייה" : StorageUtils.formatSize(e.size), () -> {
                    if ("AndroidManifest.xml".equals(e.name)) {
                        loadManifestTab();
                    } else if (e.name.endsWith(".dex")) {
                        loadDexEntry(e.name);
                    } else if (!e.directory && (MediaFormatHelper.isTextFile(e.name) || e.name.endsWith(".xml"))) {
                        try {
                            byte[] raw = ArchiveUtils.readEntryBytes(apkFile, e.name, null);
                            String text = e.name.endsWith(".xml") ? ApkSourceParser.decodeBinaryXml(raw) : new String(raw, StandardCharsets.UTF_8);
                            openReadOnlyViewer(e.name, text);
                        } catch (Exception ex) {
                            Toast.makeText(this, "קובץ בינארי פנימי", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
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

            TextView tv = new TextView(this);
            tv.setText(decoded);
            tv.setTextColor(textPrimary);
            tv.setTypeface(Typeface.MONOSPACE);
            tv.setTextSize(12f);
            tv.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            tv.setTextIsSelectable(true);
            tv.setPadding(0, 18, 0, 18);
            contentList.addView(tv);
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
            hdr.setPadding(0, 0, 0, 12);
            contentList.addView(hdr);

            int shown = 0;
            for (Map.Entry<String, List<String>> entry : pkgs.entrySet()) {
                if (shown++ > 180) break;
                addInfoCard(entry.getKey(), entry.getValue().size() + " מחלקות", () -> {
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

    private void addInfoCard(String title, String sub, Runnable onClick) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(22, 16, 22, 16);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(16f);
        bg.setColor(cardBg);
        card.setBackground(bg);

        TextView t1 = new TextView(this);
        t1.setText(title);
        t1.setTextColor(textPrimary);
        t1.setTextSize(13.5f);
        t1.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(t1);

        TextView t2 = new TextView(this);
        t2.setText(sub);
        t2.setTextColor(textSecondary);
        t2.setTextSize(12f);
        card.addView(t2);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = 10;
        card.setOnClickListener(v -> onClick.run());
        contentList.addView(card, lp);
    }
}
