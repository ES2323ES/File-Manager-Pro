package com.aishhatorah.filemanagerpro;

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
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * סעיף 37: מסך יומן שגיאות ודיווח ל-GitHub:
 * - חלוקת צבעים יומית (שגיאה=אדום, אזהרה=ורוד/אדום בהיר, מידע=כחול)
 * - ניקוי יומן וסינון לפי חומרה
 * - דיווח תקלה / הצעה / שאלה ישירות ל-GitHub Issues עם מחיקת כל מידע אישי (sanitizeForExport)
 */
public class DiagnosticsActivity extends AppCompatActivity {

    private LinearLayout logListContainer;
    private String activeFilter = "ALL";
    private int theme;
    private int textPrimary;
    private int textSecondary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        theme = PrefsManager.getThemeMode(this);
        ThemeHelper.applyWindowStatusBar(this, theme);
        int bg = ThemeHelper.getBgColor(this, theme);
        textPrimary = ThemeHelper.getPrimaryTextColor(this, theme);
        textSecondary = ThemeHelper.getSecondaryTextColor(this, theme);
        int accent = ThemeHelper.getAccentColor(this, theme);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(bg);

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(ThemeHelper.getTopBarColor(this, theme));
        topBar.setPadding(20, 16, 20, 16);

        Button btnBack = ThemedDialogHelper.createThemedButton(this, "חזרה", theme, false, false);
        btnBack.setOnClickListener(v -> finish());
        topBar.addView(btnBack);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("יומן שגיאות ודיווח");
        tvTitle.setTextColor(textPrimary);
        tvTitle.setTextSize(16.5f);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tLp.setMarginStart(12);
        topBar.addView(tvTitle, tLp);

        Button btnReport = ThemedDialogHelper.createThemedButton(this, "דווח ב-GitHub", theme, true, false);
        btnReport.setOnClickListener(v -> showGitHubIssueWizard());
        topBar.addView(btnReport);
        root.addView(topBar);

        // שורת סינון וניקוי
        LinearLayout filterRow = new LinearLayout(this);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);
        filterRow.setPadding(20, 12, 20, 10);
        filterRow.addView(makeFilterBtn("הכל", "ALL", accent));
        filterRow.addView(makeFilterBtn("שגיאות", AppLogger.LEVEL_ERROR, Color.parseColor("#DC2626")));
        filterRow.addView(makeFilterBtn("אזהרות", AppLogger.LEVEL_WARN, Color.parseColor("#E11D48")));
        filterRow.addView(makeFilterBtn("מידע", AppLogger.LEVEL_INFO, Color.parseColor("#2563EB")));

        TextView btnClear = new TextView(this);
        btnClear.setText("נקה לוג");
        btnClear.setTextColor(Color.parseColor("#DC2626"));
        btnClear.setTypeface(Typeface.DEFAULT_BOLD);
        btnClear.setPadding(16, 8, 8, 8);
        btnClear.setOnClickListener(v -> {
            AppLogger.clearAllLogs(this);
            renderLogs();
            Toast.makeText(this, "היומן נוקה", Toast.LENGTH_SHORT).show();
        });
        filterRow.addView(btnClear);
        root.addView(filterRow);

        ScrollView scroll = new ScrollView(this);
        logListContainer = new LinearLayout(this);
        logListContainer.setOrientation(LinearLayout.VERTICAL);
        logListContainer.setPadding(20, 8, 20, 32);
        scroll.addView(logListContainer);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        renderLogs();
    }

    private TextView makeFilterBtn(String label, String level, int color) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(color);
        tv.setTextSize(12.5f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(18, 8, 18, 8);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tv.setGravity(Gravity.CENTER);
        tv.setLayoutParams(lp);
        tv.setOnClickListener(v -> {
            activeFilter = level;
            renderLogs();
        });
        return tv;
    }

    private void renderLogs() {
        logListContainer.removeAllViews();
        List<File> dayFiles = AppLogger.listLogFiles(this);
        if (dayFiles.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("אין רשומות שגיאה או אבחון כרגע.");
            empty.setTextColor(textSecondary);
            empty.setPadding(16, 28, 16, 28);
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
            dayHdr.setTypeface(Typeface.DEFAULT_BOLD);
            dayHdr.setPadding(8, 14, 8, 6);
            logListContainer.addView(dayHdr);

            for (AppLogger.LogEntry e : entries) {
                if (!"ALL".equals(activeFilter) && !activeFilter.equals(e.level)) continue;
                rendered++;
                addLogCard(e);
                if (rendered > 120) break;
            }
        }
    }

    private void addLogCard(AppLogger.LogEntry e) {
        boolean isDark = ThemeHelper.isDarkSurface(this, theme);
        int bgColor;
        int badgeColor;
        if (AppLogger.LEVEL_ERROR.equals(e.level)) {
            bgColor = isDark ? Color.parseColor("#3B1114") : Color.parseColor("#FEE2E2");
            badgeColor = Color.parseColor("#DC2626");
        } else if (AppLogger.LEVEL_WARN.equals(e.level)) {
            bgColor = isDark ? Color.parseColor("#331824") : Color.parseColor("#FFE4E6");
            badgeColor = Color.parseColor("#E11D48");
        } else {
            bgColor = isDark ? Color.parseColor("#112138") : Color.parseColor("#EFF6FF");
            badgeColor = Color.parseColor("#2563EB");
        }

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(20, 14, 20, 14);
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(14f);
        shape.setColor(bgColor);
        shape.setStroke(2, Color.argb(90, Color.red(badgeColor), Color.green(badgeColor), Color.blue(badgeColor)));
        card.setBackground(shape);

        TextView tvMeta = new TextView(this);
        tvMeta.setText(e.level + "  •  " + e.tag + "  •  " + e.timeDisplay);
        tvMeta.setTextColor(badgeColor);
        tvMeta.setTextSize(12f);
        tvMeta.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(tvMeta);

        TextView tvMsg = new TextView(this);
        tvMsg.setText(e.message);
        tvMsg.setTextColor(textPrimary);
        tvMsg.setTextSize(13f);
        tvMsg.setPadding(0, 4, 0, 0);
        card.addView(tvMsg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = 8;
        logListContainer.addView(card, lp);
    }

    private void showGitHubIssueWizard() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);

        RadioGroup rg = new RadioGroup(this);
        String[] types = {"דיווח תקלה (Bug)", "הצעת שיפור (Feature)", "שאלה (Question)"};
        for (int i = 0; i < types.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(500 + i);
            rb.setText(types[i]);
            rb.setTextColor(textPrimary);
            if (i == 0) rb.setChecked(true);
            rg.addView(rb);
        }
        panel.addView(rg);

        EditText etTitle = new EditText(this);
        etTitle.setHint("כותרת קצרה...");
        etTitle.setTextColor(textPrimary);
        panel.addView(etTitle);

        EditText etBody = new EditText(this);
        etBody.setHint("תאר את התקלה או ההצעה (נתיבים ומיילים יוסרו אוטומטית מהלוג)...");
        etBody.setMinLines(3);
        etBody.setTextColor(textPrimary);
        panel.addView(etBody);

        ThemedDialogHelper.showCustomDialog(
                this,
                "שליחת דיווח ל-GitHub",
                "פרטים רגישים (מיילים, נתיבים וטוקנים) מוסרים אוטומטית לשמירה על פרטיותך.",
                panel,
                "פתח בטופס GitHub",
                () -> {
                    int sel = rg.getCheckedRadioButtonId() - 500;
                    String prefix = sel == 1 ? "[Feature] " : (sel == 2 ? "[Question] " : "[Bug] ");
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

                    try {
                        String url = "https://github.com/" + PrefsManager.FIXED_GITHUB_REPO + "/issues/new?title="
                                + URLEncoder.encode(fullTitle, StandardCharsets.UTF_8.name())
                                + "&body=" + URLEncoder.encode(issueBody, StandardCharsets.UTF_8.name());
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    } catch (Exception ex) {
                        Toast.makeText(this, "לא ניתן לפתוח דפדפן: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                },
                "ביטול",
                null,
                true
        );
    }
}
