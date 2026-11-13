package com.aishhatorah.filemanagerpro;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.Layout;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

/**
 * סעיפים 2, 11, 12, 13, 23, 40, 41:
 * עורך טקסט וקוד מתקדם התומך ב-20 שפות תכנות, מספרי שורות מדויקים כולל בגלישת שורות,
 * ספירת שורות/מילים/תווים, Undo/Redo, חיפוש והחלפה, זום אצבעות, בחירת קידוד,
 * גיבוי .bak אוטומטי, עריכה ישירה מתוך קובץ ארכיון ZIP ותצוגת Markdown חיה.
 */
public class CodeEditorActivity extends AppCompatActivity {

    public static final String EXTRA_FILE_PATH = "extra_file_path";
    public static final String EXTRA_ARCHIVE_PATH = "extra_archive_path";
    public static final String EXTRA_ARCHIVE_ENTRY = "extra_archive_entry";
    public static final String EXTRA_ARCHIVE_PASSWORD = "extra_archive_password";
    public static final String EXTRA_READ_ONLY_TITLE = "extra_readonly_title";
    public static final String EXTRA_READ_ONLY_CONTENT = "extra_readonly_content";

    private File targetFile;
    private File archiveFile;
    private String archiveEntry;
    private String archivePassword;
    private boolean readOnlyMode = false;

    private CodeSyntaxHighlighter.Language currentLang = CodeSyntaxHighlighter.Language.PLAIN;
    private Charset currentCharset = StandardCharsets.UTF_8;
    private boolean wordWrap = true;
    private boolean previewMarkdown = false;
    private boolean dirty = false;
    private boolean suppressHistory = false;

    private final Deque<String> undoStack = new ArrayDeque<>();
    private final Deque<String> redoStack = new ArrayDeque<>();

    private EditText editorView;
    private LineNumberGutterView gutterView;
    private HorizontalScrollView editorHScroll;
    private ScrollView editorVScroll;
    private WebView markdownWebView;
    private TextView tvTitle;
    private TextView tvStatsBar;
    private TextView tvLangChip;
    private TextView tvEncChip;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int theme = PrefsManager.getThemeMode(this);
        ThemeHelper.applyWindowStatusBar(this, theme);
        int bg = ThemeHelper.getBgColor(this, theme);
        int topBg = ThemeHelper.getTopBarColor(this, theme);
        int cardBg = ThemeHelper.getCardColor(this, theme);
        int textPrimary = ThemeHelper.getPrimaryTextColor(this, theme);
        int textSecondary = ThemeHelper.getSecondaryTextColor(this, theme);
        int accent = ThemeHelper.getAccentColor(this, theme);
        boolean darkBg = ThemeHelper.isDarkSurface(this, theme);

        String path = getIntent().getStringExtra(EXTRA_FILE_PATH);
        String archPath = getIntent().getStringExtra(EXTRA_ARCHIVE_PATH);
        archiveEntry = getIntent().getStringExtra(EXTRA_ARCHIVE_ENTRY);
        archivePassword = getIntent().getStringExtra(EXTRA_ARCHIVE_PASSWORD);
        String roContent = getIntent().getStringExtra(EXTRA_READ_ONLY_CONTENT);
        String roTitle = getIntent().getStringExtra(EXTRA_READ_ONLY_TITLE);

        String displayTitle;
        if (roContent != null) {
            readOnlyMode = true;
            displayTitle = roTitle != null ? roTitle : "צפייה במקור";
            currentLang = CodeSyntaxHighlighter.detectLanguage(displayTitle);
        } else if (archPath != null && archiveEntry != null) {
            archiveFile = new File(archPath);
            displayTitle = new File(archiveEntry).getName() + " (ארכיון)";
            currentLang = CodeSyntaxHighlighter.detectLanguage(archiveEntry);
        } else if (path != null) {
            targetFile = new File(path);
            displayTitle = targetFile.getName();
            currentLang = CodeSyntaxHighlighter.detectLanguage(displayTitle);
        } else {
            finish();
            return;
        }

        wordWrap = PrefsManager.isTextWrapEnabled(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);

        // סרגל עליון
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(topBg);
        topBar.setPadding(16, 14, 16, 14);

        ImageButton btnBack = makeIconBtn(R.drawable.ic_arrow_back, textPrimary, v -> handleBackAttempt());
        topBar.addView(btnBack);

        tvTitle = new TextView(this);
        tvTitle.setText(displayTitle);
        tvTitle.setTextColor(textPrimary);
        tvTitle.setTextSize(15f);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setSingleLine(true);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tLp.setMarginStart(10);
        tLp.setMarginEnd(10);
        topBar.addView(tvTitle, tLp);

        if (!readOnlyMode) {
            topBar.addView(makeIconBtn(R.drawable.ic_undo, textPrimary, v -> undo()));
            topBar.addView(makeIconBtn(R.drawable.ic_redo, textPrimary, v -> redo()));
        }
        topBar.addView(makeIconBtn(R.drawable.ic_search, textPrimary, v -> showFindReplaceDialog()));
        topBar.addView(makeIconBtn(R.drawable.ic_wrap, accent, v -> toggleWordWrap()));

        if (!readOnlyMode) {
            TextView btnSave = new TextView(this);
            btnSave.setText("שמור");
            btnSave.setTextColor(Color.WHITE);
            btnSave.setTextSize(13f);
            btnSave.setTypeface(Typeface.DEFAULT_BOLD);
            btnSave.setPadding(24, 12, 24, 12);
            GradientDrawable saveBg = new GradientDrawable();
            saveBg.setCornerRadius(14f);
            saveBg.setColor(accent);
            btnSave.setBackground(saveBg);
            btnSave.setOnClickListener(v -> saveContent());
            topBar.addView(btnSave);
        }
        root.addView(topBar);

        // שורת מידע וכלים קומפקטית
        HorizontalScrollView metaScroll = new HorizontalScrollView(this);
        metaScroll.setHorizontalScrollBarEnabled(false);
        metaScroll.setBackgroundColor(cardBg);
        LinearLayout metaRow = new LinearLayout(this);
        metaRow.setOrientation(LinearLayout.HORIZONTAL);
        metaRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        metaRow.setPadding(20, 10, 20, 10);
        metaRow.setGravity(Gravity.CENTER_VERTICAL);

        tvStatsBar = new TextView(this);
        tvStatsBar.setTextColor(textSecondary);
        tvStatsBar.setTextSize(12f);
        metaRow.addView(tvStatsBar);

        tvLangChip = makeChip(currentLang.displayName, accent, v -> showLanguagePicker(darkBg));
        metaRow.addView(tvLangChip);

        tvEncChip = makeChip("UTF-8", textSecondary, v -> showEncodingPicker());
        metaRow.addView(tvEncChip);

        if (MediaFormatHelper.isMarkdownFile(displayTitle)) {
            TextView mdChip = makeChip("תצוגת Markdown", Color.parseColor("#0D9488"), v -> toggleMarkdownPreview(darkBg, textPrimary, accent));
            metaRow.addView(mdChip);
        }

        if (!readOnlyMode) {
            String[] quickChars = {"{ }", "( )", ";", "=", "->"};
            for (String qc : quickChars) {
                TextView q = makeChip(qc, textPrimary, v -> insertAtCursor(qc.equals("{ }") ? "{}" : (qc.equals("( )") ? "()" : qc)));
                metaRow.addView(q);
            }
        }
        metaScroll.addView(metaRow);
        root.addView(metaScroll);

        // גוף העורך (שמאל לימין לקוד נקי + מספרי שורות מסונכרנים)
        editorVScroll = new ScrollView(this);
        editorVScroll.setFillViewport(true);

        LinearLayout codeContainer = new LinearLayout(this);
        codeContainer.setOrientation(LinearLayout.HORIZONTAL);
        codeContainer.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

        gutterView = new LineNumberGutterView(this, textSecondary, ThemeHelper.getDividerColor(this, theme));
        codeContainer.addView(gutterView, new LinearLayout.LayoutParams(
                ThemeHelper.dpScaled(this, 44), ViewGroup.LayoutParams.MATCH_PARENT));

        editorView = new EditText(this);
        editorView.setBackgroundColor(Color.TRANSPARENT);
        editorView.setTextColor(textPrimary);
        editorView.setTypeface(Typeface.MONOSPACE);
        editorView.setTextSize(PrefsManager.getTextFontSizeSp(this));
        editorView.setGravity(Gravity.TOP | Gravity.START);
        editorView.setPadding(16, 16, 18, 80);
        editorView.setHorizontallyScrolling(!wordWrap);
        if (readOnlyMode) {
            editorView.setKeyListener(null);
            editorView.setTextIsSelectable(true);
        }

        // זום אצבעות לגודל הגופן (סעיף 11)
        ScaleGestureDetector pinchDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float cur = PrefsManager.getTextFontSizeSp(CodeEditorActivity.this);
                float next = Math.max(9f, Math.min(30f, cur * detector.getScaleFactor()));
                PrefsManager.setTextFontSizeSp(CodeEditorActivity.this, next);
                editorView.setTextSize(next);
                gutterView.syncFromEditor(editorView);
                return true;
            }
        });
        editorView.setOnTouchListener((v, ev) -> {
            pinchDetector.onTouchEvent(ev);
            return false;
        });

        editorHScroll = new HorizontalScrollView(this);
        editorHScroll.setFillViewport(true);
        editorHScroll.addView(editorView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        codeContainer.addView(editorHScroll, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        editorVScroll.addView(codeContainer, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(editorVScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        markdownWebView = new WebView(this);
        markdownWebView.setVisibility(View.GONE);
        root.addView(markdownWebView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);

        // טעינת תוכן
        String initialContent = roContent != null ? roContent : loadInitialText();
        suppressHistory = true;
        editorView.setText(initialContent);
        CodeSyntaxHighlighter.highlight(editorView.getText(), currentLang, darkBg);
        suppressHistory = false;
        undoStack.push(initialContent);
        updateStats();
        editorView.post(() -> gutterView.syncFromEditor(editorView));

        editorView.addTextChangedListener(new TextWatcher() {
            private String before;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                before = s.toString();
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int beforeLen, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!suppressHistory) {
                    if (before != null && !before.equals(s.toString())) {
                        undoStack.push(before);
                        if (undoStack.size() > 50) undoStack.removeLast();
                        redoStack.clear();
                    }
                    dirty = true;
                    tvTitle.setText(displayTitle + " *");
                }
                CodeSyntaxHighlighter.highlight(s, currentLang, darkBg);
                updateStats();
                editorView.post(() -> gutterView.syncFromEditor(editorView));
            }
        });
    }

    private ImageButton makeIconBtn(int iconRes, int tint, View.OnClickListener l) {
        ImageButton btn = new ImageButton(this);
        btn.setImageResource(iconRes);
        btn.setColorFilter(tint);
        btn.setBackgroundColor(Color.TRANSPARENT);
        btn.setPadding(14, 14, 14, 14);
        btn.setOnClickListener(l);
        return btn;
    }

    private TextView makeChip(String text, int color, View.OnClickListener l) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(12f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(18, 8, 18, 8);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(12f);
        bg.setStroke(2, Color.argb(90, Color.red(color), Color.green(color), Color.blue(color)));
        tv.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginStart(10);
        tv.setLayoutParams(lp);
        tv.setOnClickListener(l);
        return tv;
    }

    private String loadInitialText() {
        try {
            byte[] raw;
            if (archiveFile != null && archiveEntry != null) {
                raw = ArchiveUtils.readEntryBytes(archiveFile, archiveEntry, archivePassword);
            } else if (targetFile != null && targetFile.exists()) {
                long cap = Math.min(targetFile.length(), 1_500_000L);
                raw = new byte[(int) cap];
                try (FileInputStream fis = new FileInputStream(targetFile)) {
                    int off = 0;
                    while (off < raw.length) {
                        int r = fis.read(raw, off, raw.length - off);
                        if (r < 0) break;
                        off += r;
                    }
                }
            } else {
                return "";
            }
            return new String(raw, currentCharset);
        } catch (Exception e) {
            AppLogger.error(this, "CodeEditor", "Load failed", e);
            Toast.makeText(this, "שגיאה בקריאת הקובץ: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return "";
        }
    }

    private void saveContent() {
        if (readOnlyMode) return;
        String text = editorView.getText().toString();
        byte[] bytes = text.getBytes(currentCharset);
        try {
            if (archiveFile != null && archiveEntry != null) {
                StorageUtils.createBackupIfEnabled(this, archiveFile);
                ArchiveUtils.updateOrAddZipEntry(archiveFile, archiveEntry, bytes);
                Toast.makeText(this, "נשמר ישירות לתוך הארכיון בהצלחה", Toast.LENGTH_SHORT).show();
            } else if (targetFile != null) {
                StorageUtils.createBackupIfEnabled(this, targetFile);
                try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                    fos.write(bytes);
                }
                Toast.makeText(this, "הקובץ נשמר בהצלחה", Toast.LENGTH_SHORT).show();
            }
            dirty = false;
            String cur = tvTitle.getText().toString();
            if (cur.endsWith(" *")) tvTitle.setText(cur.substring(0, cur.length() - 2));
        } catch (Exception e) {
            AppLogger.error(this, "CodeEditor", "Save failed", e);
            Toast.makeText(this, "שגיאה בשמירה: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void undo() {
        if (undoStack.size() <= 1) return;
        String current = editorView.getText().toString();
        String prev = undoStack.pop();
        redoStack.push(current);
        suppressHistory = true;
        editorView.setText(prev);
        editorView.setSelection(Math.min(prev.length(), editorView.getSelectionStart()));
        suppressHistory = false;
    }

    private void redo() {
        if (redoStack.isEmpty()) return;
        String next = redoStack.pop();
        undoStack.push(editorView.getText().toString());
        suppressHistory = true;
        editorView.setText(next);
        editorView.setSelection(next.length());
        suppressHistory = false;
    }

    private void toggleWordWrap() {
        wordWrap = !wordWrap;
        PrefsManager.setTextWrapEnabled(this, wordWrap);
        editorView.setHorizontallyScrolling(!wordWrap);
        editorView.requestLayout();
        editorView.post(() -> gutterView.syncFromEditor(editorView));
        Toast.makeText(this, wordWrap ? "גלישת שורות מופעלת" : "גלישה אופקית ללא שבירה", Toast.LENGTH_SHORT).show();
    }

    private void toggleMarkdownPreview(boolean darkBg, int textPrimary, int accent) {
        previewMarkdown = !previewMarkdown;
        if (previewMarkdown) {
            String textHex = String.format("#%06X", (0xFFFFFF & textPrimary));
            String accHex = String.format("#%06X", (0xFFFFFF & accent));
            String html = CodeSyntaxHighlighter.markdownToHtml(editorView.getText().toString(), darkBg, textHex, accHex);
            markdownWebView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
            editorVScroll.setVisibility(View.GONE);
            markdownWebView.setVisibility(View.VISIBLE);
        } else {
            markdownWebView.setVisibility(View.GONE);
            editorVScroll.setVisibility(View.VISIBLE);
        }
    }

    private void insertAtCursor(String token) {
        int start = Math.max(0, editorView.getSelectionStart());
        editorView.getText().insert(start, token);
        if ("{}".equals(token) || "()".equals(token)) {
            editorView.setSelection(start + 1);
        }
    }

    private void updateStats() {
        String txt = editorView.getText().toString();
        int chars = txt.length();
        int lines = txt.isEmpty() ? 1 : txt.split("\n", -1).length;
        String trimmed = txt.trim();
        int words = trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;
        tvStatsBar.setText(String.format(Locale.US, "%d שורות  •  %d מילים  •  %d תווים", lines, words, chars));
    }

    private void showLanguagePicker(boolean darkBg) {
        CodeSyntaxHighlighter.Language[] langs = CodeSyntaxHighlighter.Language.values();
        String[] names = new String[langs.length];
        for (int i = 0; i < langs.length; i++) names[i] = langs[i].displayName;
        new android.app.AlertDialog.Builder(this)
                .setTitle("בחר שפת תחביר")
                .setItems(names, (d, idx) -> {
                    currentLang = langs[idx];
                    tvLangChip.setText(currentLang.displayName);
                    CodeSyntaxHighlighter.highlight(editorView.getText(), currentLang, darkBg);
                }).show();
    }

    private void showEncodingPicker() {
        String[] labels = {"UTF-8", "ISO-8859-8 (עברית)", "Windows-1255 (עברית)", "UTF-16"};
        new android.app.AlertDialog.Builder(this)
                .setTitle("בחר קידוד טקסט")
                .setItems(labels, (d, idx) -> {
                    String cs = idx == 1 ? "ISO-8859-8" : (idx == 2 ? "windows-1255" : (idx == 3 ? "UTF-16" : "UTF-8"));
                    currentCharset = Charset.forName(cs);
                    tvEncChip.setText(cs);
                    if (!dirty && !readOnlyMode) {
                        suppressHistory = true;
                        editorView.setText(loadInitialText());
                        suppressHistory = false;
                    }
                }).show();
    }

    private void showFindReplaceDialog() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        EditText etFind = new EditText(this);
        etFind.setHint("חפש טקסט...");
        panel.addView(etFind);
        EditText etReplace = new EditText(this);
        etReplace.setHint("החלף ב... (אופציונלי)");
        panel.addView(etReplace);

        ThemedDialogHelper.showCustomDialog(
                this,
                "חיפוש והחלפה",
                null,
                panel,
                readOnlyMode ? "חפש" : "החלף הכל",
                () -> {
                    String q = etFind.getText().toString();
                    if (q.isEmpty()) return;
                    String content = editorView.getText().toString();
                    String rep = etReplace.getText().toString();
                    if (!readOnlyMode && !rep.isEmpty()) {
                        editorView.setText(content.replace(q, rep));
                    } else {
                        int idx = content.indexOf(q, Math.max(0, editorView.getSelectionEnd()));
                        if (idx < 0) idx = content.indexOf(q);
                        if (idx >= 0) {
                            editorView.requestFocus();
                            editorView.setSelection(idx, idx + q.length());
                        } else {
                            Toast.makeText(this, "לא נמצא", Toast.LENGTH_SHORT).show();
                        }
                    }
                },
                "ביטול",
                null,
                true
        );
    }

    private void handleBackAttempt() {
        if (!dirty || readOnlyMode) {
            finish();
            return;
        }
        ThemedDialogHelper.showCustomDialog(
                this,
                "שינויים שלא נשמרו",
                "לשמור את השינויים שביצעת לפני היציאה?",
                null,
                "שמור וצא",
                () -> {
                    saveContent();
                    finish();
                },
                "צא ללא שמירה",
                this::finish,
                true
        );
    }

    @Override
    public void onBackPressed() {
        handleBackAttempt();
    }

    /**
     * סעיף 23: שוליים חכמים המציירים את מספרי השורות בהתאמה מדויקת לכל שורה (כולל שורות עטופות ארוכות).
     */
    private static class LineNumberGutterView extends View {
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint();
        private final java.util.List<LinePos> positions = new java.util.ArrayList<>();

        private static class LinePos {
            final int num;
            final float y;
            LinePos(int num, float y) { this.num = num; this.y = y; }
        }

        LineNumberGutterView(CodeEditorActivity ctx, int numColor, int dividerColor) {
            super(ctx);
            textPaint.setColor(numColor);
            textPaint.setTypeface(Typeface.MONOSPACE);
            textPaint.setTextSize(11 * getResources().getDisplayMetrics().scaledDensity);
            textPaint.setTextAlign(Paint.Align.RIGHT);
            borderPaint.setColor(dividerColor);
            borderPaint.setStrokeWidth(2f);
        }

        void syncFromEditor(EditText ed) {
            positions.clear();
            Layout layout = ed.getLayout();
            if (layout == null) {
                invalidate();
                return;
            }
            textPaint.setTextSize(ed.getTextSize() * 0.78f);
            String txt = ed.getText().toString();
            int padTop = ed.getPaddingTop();
            int logicalLine = 1;
            int lastLogicalStart = -1;
            int lineCount = layout.getLineCount();

            for (int visual = 0; visual < lineCount; visual++) {
                int startOff = layout.getLineStart(visual);
                boolean isNewLogical = (visual == 0) || (startOff > 0 && startOff - 1 < txt.length() && txt.charAt(startOff - 1) == '\n');
                if (isNewLogical && startOff != lastLogicalStart) {
                    lastLogicalStart = startOff;
                    positions.add(new LinePos(logicalLine++, padTop + layout.getLineBaseline(visual)));
                }
            }
            setMinimumHeight(Math.max(ed.getHeight(), 200));
            requestLayout();
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float rightX = getWidth() - 10f;
            canvas.drawLine(getWidth() - 2f, 0, getWidth() - 2f, getHeight(), borderPaint);
            for (LinePos lp : positions) {
                canvas.drawText(String.valueOf(lp.num), rightX, lp.y, textPaint);
            }
        }
    }
}
