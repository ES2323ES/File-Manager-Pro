package com.aishhatorah.filemanagerpro;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.ColorUtils;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * מסך יומן שגיאות ודיווח ל-GitHub בעיצוב זכוכית מודרני:
 * - כותרת מעוגלת כלפי מעלה עם חץ חזרה (SVG), מחיקת לוג (SVG) וכפתור "דיווח"
 * - בועת לוגים מזכוכית ולשוניות סינון בצורת סימניות מתחת לבועה
 * - חלונית דיווח צפה מזכוכית מטושטשת עם סימניות נושא מתחת לבועת הכותרת והתוכן וכפתור "שלח"
 */
public class DiagnosticsActivity extends AppCompatActivity {

    private LinearLayout logListContainer;
    private String activeFilter = "ALL";
    private int theme;
    private int textPrimary;
    private int textSecondary;
    private int accent;
    private boolean dark;

    private final TextView[] filterTabs = new TextView[4];
    private final String[] filterLevels = {"ALL", AppLogger.LEVEL_ERROR, AppLogger.LEVEL_WARN, AppLogger.LEVEL_INFO};
    private int[] filterColors;

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        theme = PrefsManager.getThemeMode(this);
        ThemeHelper.applyWindowStatusBar(this, theme);
        textPrimary = ThemeHelper.getPrimaryTextColor(this, theme);
        textSecondary = ThemeHelper.getSecondaryTextColor(this, theme);
        accent = ThemeHelper.getAccentColor(this, theme);
        dark = ThemeHelper.isDarkSurface(this, theme);

        filterColors = new int[]{
                accent,
                dark ? Color.parseColor("#F87171") : Color.parseColor("#DC2626"),
                dark ? Color.parseColor("#FB7185") : Color.parseColor("#E11D48"),
                dark ? Color.parseColor("#60A5FA") : Color.parseColor("#2563EB")
        };

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        ThemeHelper.applyWallpaperBackdrop(this, root, theme);

        // כותרת מעוגלת כלפי מעלה כמו מסך הבית
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(10), dp(8), dp(12), dp(10));
        ThemeHelper.applyCurvedHeaderStyle(this, topBar, theme);

        ImageButton btnBack = ThemeHelper.createBorderlessSvgButton(
                this, R.drawable.ic_arrow_back, accent, "חזרה", 40, 8, v -> finish());
        topBar.addView(btnBack);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("יומן שגיאות");
        tvTitle.setTextColor(textPrimary);
        tvTitle.setTextSize(ThemeHelper.spScaled(this, 17f));
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tLp.setMarginStart(dp(6));
        topBar.addView(tvTitle, tLp);

        int clearTint = dark ? Color.parseColor("#F87171") : Color.parseColor("#DC2626");
        ImageButton btnClear = ThemeHelper.createBorderlessSvgButton(
                this, R.drawable.ic_delete, clearTint, "נקה יומן", 38, 8, v -> {
                    AppLogger.clearAllLogs(this);
                    renderLogs();
                    Toast.makeText(this, "היומן נוקה", Toast.LENGTH_SHORT).show();
                });
        topBar.addView(btnClear);

        Button btnReport = new Button(this);
        btnReport.setText("דיווח");
        btnReport.setAllCaps(false);
        btnReport.setTextSize(ThemeHelper.spScaled(this, 13f));
        btnReport.setTypeface(Typeface.DEFAULT_BOLD);
        btnReport.setTextColor(theme == PrefsManager.THEME_NEON ? Color.parseColor("#04101A") : Color.WHITE);
        GradientDrawable reportBg = new GradientDrawable();
        reportBg.setCornerRadius(dp(12));
        reportBg.setColor(accent);
        btnReport.setBackground(reportBg);
        btnReport.setStateListAnimator(null);
        btnReport.setMinHeight(0);
        btnReport.setMinimumHeight(0);
        btnReport.setPadding(dp(14), dp(7), dp(14), dp(7));
        LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rLp.setMarginStart(dp(4));
        btnReport.setOnClickListener(v -> showGitHubIssueWizard());
        topBar.addView(btnReport, rLp);

        root.addView(topBar);

        // בועת הלוגים המרכזית (זכוכית קריאה מעל הרקע)
        LinearLayout logsBubble = new LinearLayout(this);
        logsBubble.setOrientation(LinearLayout.VERTICAL);
        float topRad = dp(18);
        GradientDrawable bubbleBg = new GradientDrawable();
        bubbleBg.setCornerRadii(new float[]{topRad, topRad, topRad, topRad, 0f, 0f, 0f, 0f});
        bubbleBg.setColor(ThemeHelper.getGlassCardFillColor(this, theme, true));
        bubbleBg.setStroke(Math.max(1, dp(1)),
                ColorUtils.setAlphaComponent(dark ? Color.WHITE : accent, dark ? 44 : 70));
        logsBubble.setBackground(bubbleBg);
        logsBubble.setElevation(dp(3));

        ScrollView scroll = new ScrollView(this);
        logListContainer = new LinearLayout(this);
        logListContainer.setOrientation(LinearLayout.VERTICAL);
        logListContainer.setPadding(dp(12), dp(10), dp(12), dp(14));
        scroll.addView(logListContainer);
        logsBubble.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout.LayoutParams bubbleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        bubbleLp.setMargins(dp(12), dp(12), dp(12), 0);
        root.addView(logsBubble, bubbleLp);

        // לשוניות סינון בצורת סימניות מתחת לבועת הלוגים
        LinearLayout bookmarkRow = new LinearLayout(this);
        bookmarkRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams bmRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bmRowLp.setMargins(dp(12), 0, dp(12), dp(12));

        String[] labels = {"הכל", "שגיאות", "אזהרות", "מידע"};
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            TextView tab = new TextView(this);
            tab.setText(labels[i]);
            tab.setTextSize(ThemeHelper.spScaled(this, 12.5f));
            tab.setTypeface(Typeface.DEFAULT_BOLD);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(dp(6), dp(9), dp(6), dp(10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) lp.setMarginStart(dp(4));
            tab.setLayoutParams(lp);
            tab.setOnClickListener(v -> {
                activeFilter = filterLevels[idx];
                updateBookmarkTabs();
                renderLogs();
            });
            filterTabs[i] = tab;
            bookmarkRow.addView(tab);
        }
        root.addView(bookmarkRow, bmRowLp);

        setContentView(root);
        updateBookmarkTabs();
        renderLogs();
    }

    private void updateBookmarkTabs() {
        float bmRad = dp(14);
        int fillInactive = ColorUtils.setAlphaComponent(ThemeHelper.getCardColor(this, theme), dark ? 150 : 175);
        for (int i = 0; i < filterTabs.length; i++) {
            TextView tab = filterTabs[i];
            if (tab == null) continue;
            boolean active = filterLevels[i].equals(activeFilter);
            int c = filterColors[i];
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadii(new float[]{0f, 0f, 0f, 0f, bmRad, bmRad, bmRad, bmRad});
            bg.setColor(active ? c : fillInactive);
            bg.setStroke(Math.max(1, dp(1)), active ? c : ColorUtils.setAlphaComponent(c, 75));
            tab.setBackground(bg);
            tab.setTextColor(active
                    ? (theme == PrefsManager.THEME_NEON && i == 0 ? Color.parseColor("#04101A") : Color.WHITE)
                    : c);
            tab.setElevation(active ? dp(4) : dp(1));
        }
    }

    private void renderLogs() {
        logListContainer.removeAllViews();
        List<File> dayFiles = AppLogger.listLogFiles(this);
        if (dayFiles.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("אין רשומות שגיאה או אבחון כרגע.");
            empty.setTextColor(textSecondary);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(16), dp(36), dp(16), dp(36));
            logListContainer.addView(empty);
            return;
        }

        int rendered = 0;
        for (File dayFile : dayFiles) {
            if (rendered > 120) break;
            List<AppLogger.LogEntry> entries = AppLogger.readEntries(dayFile);
            if (entries.isEmpty()) continue;

            TextView dayHdr = new TextView(this);
            dayHdr.setText(dayFile.getName().replace("log_", "").replace(".jsonl", ""));
            dayHdr.setTextColor(textSecondary);
            dayHdr.setTextSize(ThemeHelper.spScaled(this, 12f));
            dayHdr.setTypeface(Typeface.DEFAULT_BOLD);
            dayHdr.setPadding(dp(6), dp(8), dp(6), dp(6));
            logListContainer.addView(dayHdr);

            for (AppLogger.LogEntry e : entries) {
                if (!"ALL".equals(activeFilter) && !activeFilter.equals(e.level)) continue;
                rendered++;
                addLogCard(e);
                if (rendered > 120) break;
            }
        }
        if (rendered == 0) {
            TextView empty = new TextView(this);
            empty.setText("אין רשומות התואמות לסינון שנבחר.");
            empty.setTextColor(textSecondary);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(16), dp(36), dp(16), dp(36));
            logListContainer.addView(empty);
        }
    }

    private void addLogCard(AppLogger.LogEntry e) {
        int badgeColor;
        int bgTint;
        if (AppLogger.LEVEL_ERROR.equals(e.level)) {
            badgeColor = dark ? Color.parseColor("#F87171") : Color.parseColor("#DC2626");
            bgTint = ColorUtils.setAlphaComponent(Color.parseColor("#DC2626"), dark ? 44 : 20);
        } else if (AppLogger.LEVEL_WARN.equals(e.level)) {
            badgeColor = dark ? Color.parseColor("#FB7185") : Color.parseColor("#E11D48");
            bgTint = ColorUtils.setAlphaComponent(Color.parseColor("#E11D48"), dark ? 40 : 18);
        } else {
            badgeColor = dark ? Color.parseColor("#60A5FA") : Color.parseColor("#2563EB");
            bgTint = ColorUtils.setAlphaComponent(Color.parseColor("#2563EB"), dark ? 38 : 16);
        }

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(12));
        shape.setColor(bgTint);
        card.setBackground(shape);

        TextView tvMeta = new TextView(this);
        tvMeta.setText(e.level + "  •  " + e.tag + "  •  " + e.timeDisplay);
        tvMeta.setTextColor(badgeColor);
        tvMeta.setTextSize(ThemeHelper.spScaled(this, 11.5f));
        tvMeta.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(tvMeta);

        TextView tvMsg = new TextView(this);
        tvMsg.setText(e.message);
        tvMsg.setTextColor(textPrimary);
        tvMsg.setTextSize(ThemeHelper.spScaled(this, 12.8f));
        tvMsg.setPadding(0, dp(3), 0, 0);
        card.addView(tvMsg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(6);
        logListContainer.addView(card, lp);
    }

    private void showGitHubIssueWizard() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        outer.setPadding(dp(18), dp(16), dp(18), dp(16));
        outer.setBackground(ThemedDialogHelper.createDialogBackground(this, theme));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(scroll)
                .create();

        TextView tvTitle = new TextView(this);
        tvTitle.setText("דיווח ל-GitHub");
        tvTitle.setTextColor(textPrimary);
        tvTitle.setTextSize(ThemeHelper.spScaled(this, 16.5f));
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        outer.addView(tvTitle);

        TextView tvSub = new TextView(this);
        tvSub.setText("פרטים אישיים (מיילים, נתיבים וטוקנים) מוסרים אוטומטית לשמירה על פרטיותך.");
        tvSub.setTextColor(textSecondary);
        tvSub.setTextSize(ThemeHelper.spScaled(this, 12f));
        tvSub.setPadding(0, dp(3), 0, dp(12));
        outer.addView(tvSub);

        // בועת קלט כותרת ותוכן (פינות עליונות מעוגלות, תחתונות שטוחות לסימניות מתחת)
        LinearLayout formBubble = new LinearLayout(this);
        formBubble.setOrientation(LinearLayout.VERTICAL);
        formBubble.setPadding(dp(14), dp(10), dp(14), dp(12));
        float bRad = dp(16);
        GradientDrawable bubbleBg = new GradientDrawable();
        bubbleBg.setCornerRadii(new float[]{bRad, bRad, bRad, bRad, 0f, 0f, 0f, 0f});
        bubbleBg.setColor(ThemeHelper.getGlassCardFillColor(this, theme, true));
        bubbleBg.setStroke(Math.max(1, dp(1)), ColorUtils.setAlphaComponent(accent, 90));
        formBubble.setBackground(bubbleBg);

        EditText etTitle = new EditText(this);
        etTitle.setHint("כותרת הדיווח...");
        etTitle.setHintTextColor(textSecondary);
        etTitle.setTextColor(textPrimary);
        etTitle.setTextSize(ThemeHelper.spScaled(this, 14f));
        etTitle.setBackgroundColor(Color.TRANSPARENT);
        etTitle.setPadding(0, dp(4), 0, dp(8));
        formBubble.addView(etTitle);

        View line = new View(this);
        line.setBackgroundColor(ThemeHelper.getDividerColor(this, theme));
        formBubble.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1))));

        EditText etBody = new EditText(this);
        etBody.setHint("תאר את התקלה או ההצעה בפירוט...");
        etBody.setHintTextColor(textSecondary);
        etBody.setTextColor(textPrimary);
        etBody.setTextSize(ThemeHelper.spScaled(this, 13.5f));
        etBody.setMinLines(4);
        etBody.setGravity(Gravity.TOP | Gravity.START);
        etBody.setBackgroundColor(Color.TRANSPARENT);
        etBody.setPadding(0, dp(10), 0, dp(4));
        formBubble.addView(etBody);

        outer.addView(formBubble);

        // סימניות בחירת נושא מתחת לבועת הקלט
        final String[] topicLabels = {"תקלה", "הצעת שיפור", "שאלה"};
        final String[] topicPrefixes = {"[Bug] ", "[Feature] ", "[Question] "};
        final int[] selectedTopic = {0};
        final TextView[] topicTabs = new TextView[3];

        LinearLayout topicBookmarkRow = new LinearLayout(this);
        topicBookmarkRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams tbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tbLp.bottomMargin = dp(14);

        Runnable refreshTopicTabs = () -> {
            float tRad = dp(13);
            int inactiveFill = ColorUtils.setAlphaComponent(ThemeHelper.getCardColor(this, theme), dark ? 155 : 185);
            for (int i = 0; i < topicTabs.length; i++) {
                TextView t = topicTabs[i];
                if (t == null) continue;
                boolean act = selectedTopic[0] == i;
                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadii(new float[]{0f, 0f, 0f, 0f, tRad, tRad, tRad, tRad});
                bg.setColor(act ? accent : inactiveFill);
                bg.setStroke(Math.max(1, dp(1)), ColorUtils.setAlphaComponent(accent, act ? 220 : 80));
                t.setBackground(bg);
                t.setTextColor(act
                        ? (theme == PrefsManager.THEME_NEON ? Color.parseColor("#04101A") : Color.WHITE)
                        : textPrimary);
            }
        };

        for (int i = 0; i < topicLabels.length; i++) {
            final int idx = i;
            TextView tab = new TextView(this);
            tab.setText(topicLabels[i]);
            tab.setTextSize(ThemeHelper.spScaled(this, 12.5f));
            tab.setTypeface(Typeface.DEFAULT_BOLD);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(dp(6), dp(8), dp(6), dp(9));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) lp.setMarginStart(dp(4));
            tab.setLayoutParams(lp);
            tab.setOnClickListener(v -> {
                selectedTopic[0] = idx;
                refreshTopicTabs.run();
            });
            topicTabs[i] = tab;
            topicBookmarkRow.addView(tab);
        }
        refreshTopicTabs.run();
        outer.addView(topicBookmarkRow, tbLp);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);

        Button btnCancel = ThemedDialogHelper.createThemedButton(this, "ביטול", theme, false, false);
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        cancelLp.setMarginEnd(dp(8));
        btnCancel.setLayoutParams(cancelLp);
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnRow.addView(btnCancel);

        Button btnSend = ThemedDialogHelper.createThemedButton(this, "שלח", theme, true, false);
        LinearLayout.LayoutParams sendLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        btnSend.setLayoutParams(sendLp);
        btnSend.setOnClickListener(v -> {
            String prefix = topicPrefixes[selectedTopic[0]];
            String userTitle = etTitle.getText().toString().trim();
            String fullTitle = prefix + (userTitle.isEmpty() ? "Feedback from v" + BuildConfig.VERSION_NAME : userTitle);

            StringBuilder safeLog = new StringBuilder();
            List<File> files = AppLogger.listLogFiles(this);
            if (!files.isEmpty()) {
                List<AppLogger.LogEntry> latest = AppLogger.readEntries(files.get(0));
                int count = 0;
                for (AppLogger.LogEntry e : latest) {
                    if (AppLogger.LEVEL_ERROR.equals(e.level) || AppLogger.LEVEL_WARN.equals(e.level)) {
                        safeLog.append(e.timeDisplay).append(" [").append(e.level).append("] ")
                                .append(e.tag).append(": ")
                                .append(AppLogger.sanitizeForExport(e.message)).append("\n");
                        if (++count >= 8) break;
                    }
                }
            }

            String issueBody = AppLogger.sanitizeForExport(etBody.getText().toString().trim())
                    + "\n\n### Device Info\n"
                    + AppLogger.buildDeviceSummary(this)
                    + (safeLog.length() > 0 ? "\n\n### Redacted Recent Logs\n```\n" + safeLog + "```" : "");

            ThemedDialogHelper.showCustomDialog(
                    this,
                    "שליחה דרך GitHub",
                    "לצורך שליחת הדיווח ב-GitHub ייפתח דף בדפדפן החיצוני במכשיר.",
                    null,
                    "המשך",
                    () -> {
                        dialog.dismiss();
                        try {
                            String url = "https://github.com/" + PrefsManager.FIXED_GITHUB_REPO + "/issues/new?title="
                                    + URLEncoder.encode(fullTitle, StandardCharsets.UTF_8.name())
                                    + "&body=" + URLEncoder.encode(issueBody, StandardCharsets.UTF_8.name());
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                        } catch (Exception ex) {
                            Toast.makeText(this, "לא ניתן לפתוח דפדפן: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    },
                    "חזור",
                    null,
                    false
            );
        });
        btnRow.addView(btnSend);
        outer.addView(btnRow);

        scroll.addView(outer);
        dialog.show();
        ThemedDialogHelper.applyResponsiveDialogWindow(this, dialog);
    }
}
