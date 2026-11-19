package com.aishhatorah.filemanagerpro;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.Layout;
import android.text.Spannable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.ColorUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * סעיפים 2, 11, 12, 13, 23, 40, 41:
 * עורך טקסט וקוד מתקדם התומך ב-20 שפות תכנות, מספרי שורות מדויקים כולל בגלישת שורות,
 * ספירת שורות/מילים/תווים, Undo/Redo, חיפוש והחלפה מרובה בשורה מוטמעת, זום אצבעות, בחירת קידוד,
 * גיבוי .bak אוטומטי, עריכה ישירה מתוך קובץ ארכיון ZIP ותצוגת Markdown כברירת מחדל.
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
    private boolean isMarkdownFile = false;

    private CodeSyntaxHighlighter.Language currentLang = CodeSyntaxHighlighter.Language.PLAIN;
    private Charset currentCharset = StandardCharsets.UTF_8;
    private boolean wordWrap = true;
    private float currentFontSp = 14.5f;
    private boolean previewMarkdown = false;
    private boolean dirty = false;
    private boolean suppressHistory = false;
    private boolean suppressMatchClear = false;

    private static final int MAX_UNDO_ENTRIES = 40;
    private static final long MAX_UNDO_TOTAL_CHARS = 2_500_000L;

    private final Deque<String> undoStack = new ArrayDeque<>();
    private final Deque<String> redoStack = new ArrayDeque<>();
    private long undoTotalChars = 0L;
    private final List<int[]> searchMatches = new ArrayList<>();
    private int activeMatchIndex = -1;

    private LinearLayout rootLayout;
    private EditText editorView;
    private LineNumberGutterView gutterView;
    private FrameLayout editorSlot;
    private HorizontalScrollView editorHScroll;
    private ScrollView editorVScroll;
    private WebView markdownWebView;
    private LinearLayout metaContainer;
    private HorizontalScrollView metaScroll;
    private LinearLayout findReplaceBar;
    private EditText etFindInput;
    private EditText etReplaceInput;
    private TextView tvFindCounter;
    private TextView tvTitle;
    private TextView tvStatsBar;
    private TextView tvLangChip;
    private TextView tvEncChip;
    private ImageButton btnInfoToggle;
    private ImageButton btnMarkdownEye;
    private ImageButton btnWrapToggle;
    private int activeAccentColor;
    private int activeTextPrimaryColor;
    private boolean activeDarkBg;

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
        currentFontSp = PrefsManager.getTextFontSizeSp(this);
        isMarkdownFile = MediaFormatHelper.isMarkdownFile(displayTitle);
        activeAccentColor = accent;
        activeTextPrimaryColor = textPrimary;
        activeDarkBg = darkBg;

        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(bg);

        // סרגל עליון נקי: חזרה, שם הקובץ, ביטול וביצוע מחדש ושמירה בלבד
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        ThemeHelper.applyCurvedHeaderStyle(this, topBar, theme);
        topBar.setPadding(14, 12, 14, 12);

        ImageButton btnBack = makeIconBtn(R.drawable.ic_arrow_back, textPrimary, v -> handleBackAttempt());
        topBar.addView(btnBack);

        tvTitle = new TextView(this);
        tvTitle.setText(displayTitle);
        tvTitle.setTextColor(textPrimary);
        tvTitle.setTextSize(15f);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setSingleLine(true);
        tvTitle.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tLp.setMarginStart(10);
        tLp.setMarginEnd(6);
        topBar.addView(tvTitle, tLp);

        if (!readOnlyMode) {
            topBar.addView(makeIconBtn(R.drawable.ic_undo, textPrimary, v -> undo()));
            topBar.addView(makeIconBtn(R.drawable.ic_redo, textPrimary, v -> redo()));
        }

        if (!readOnlyMode) {
            TextView btnSave = new TextView(this);
            btnSave.setText("שמור");
            btnSave.setTextColor(ThemeHelper.getOnAccentTextColor(accent));
            btnSave.setTextSize(13f);
            btnSave.setTypeface(Typeface.DEFAULT_BOLD);
            btnSave.setPadding(22, 11, 22, 11);
            GradientDrawable saveBg = new GradientDrawable();
            saveBg.setCornerRadius(14f);
            saveBg.setColor(accent);
            btnSave.setBackground(saveBg);
            btnSave.setOnClickListener(v -> saveContent());
            topBar.addView(btnSave);
        }
        rootLayout.addView(topBar);

        int glassFill = ThemeHelper.getGlassCardFillColor(this, theme, true);
        int dividerColor = ThemeHelper.getDividerColor(this, theme);
        float density = getResources().getDisplayMetrics().density;

        // שורת כלים קבועה מתחת לסרגל העליון בסגנון האפליקציה: מידע, תחביר, קידוד, חיפוש, שבירת שורות, (ועין ב-Markdown)
        LinearLayout toolsBarContainer = new LinearLayout(this);
        toolsBarContainer.setOrientation(LinearLayout.VERTICAL);
        toolsBarContainer.setBackgroundColor(glassFill);

        HorizontalScrollView toolsScroll = new HorizontalScrollView(this);
        toolsScroll.setHorizontalScrollBarEnabled(false);
        toolsScroll.setFillViewport(true);

        LinearLayout toolsRow = new LinearLayout(this);
        toolsRow.setOrientation(LinearLayout.HORIZONTAL);
        toolsRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        toolsRow.setPadding(Math.round(12 * density), Math.round(5 * density), Math.round(12 * density), Math.round(5 * density));
        toolsRow.setGravity(Gravity.CENTER_VERTICAL);

        btnInfoToggle = makeIconBtn(R.drawable.ic_info, textSecondary, v -> toggleInfoBar(accent, textSecondary));
        toolsRow.addView(btnInfoToggle);

        tvLangChip = makeChip(currentLang.displayName, accent, v -> showLanguagePicker(darkBg));
        toolsRow.addView(tvLangChip);

        tvEncChip = makeChip("UTF-8", textSecondary, v -> showEncodingPicker());
        toolsRow.addView(tvEncChip);

        ImageButton btnSearchInBar = makeIconBtn(R.drawable.ic_search, textPrimary, v -> toggleFindReplaceBar());
        toolsRow.addView(btnSearchInBar);

        btnWrapToggle = makeIconBtn(R.drawable.ic_wrap, wordWrap ? accent : textSecondary,
                v -> toggleWordWrap(accent, textSecondary));
        toolsRow.addView(btnWrapToggle);

        if (isMarkdownFile) {
            previewMarkdown = true;
            btnMarkdownEye = makeIconBtn(R.drawable.ic_visibility_off, accent,
                    v -> toggleMarkdownPreview(darkBg, textPrimary, accent));
            toolsRow.addView(btnMarkdownEye);
        }

        toolsScroll.addView(toolsRow, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        toolsBarContainer.addView(toolsScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View toolsDivider = new View(this);
        toolsDivider.setBackgroundColor(dividerColor);
        toolsBarContainer.addView(toolsDivider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Math.round(density))));
        rootLayout.addView(toolsBarContainer);

        // שורת ספירה נפתחת בלחיצה על אייקון המידע (שורות / מילים / תווים בלבד)
        metaContainer = new LinearLayout(this);
        metaContainer.setOrientation(LinearLayout.VERTICAL);
        metaContainer.setBackgroundColor(glassFill);
        metaContainer.setVisibility(View.GONE);

        metaScroll = new HorizontalScrollView(this);
        metaScroll.setHorizontalScrollBarEnabled(false);
        metaScroll.setFillViewport(true);
        LinearLayout metaRow = new LinearLayout(this);
        metaRow.setOrientation(LinearLayout.HORIZONTAL);
        metaRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        metaRow.setPadding(Math.round(16 * density), Math.round(6 * density), Math.round(16 * density), Math.round(6 * density));
        metaRow.setGravity(Gravity.CENTER_VERTICAL);

        tvStatsBar = new TextView(this);
        tvStatsBar.setTextColor(textSecondary);
        tvStatsBar.setTextSize(12f);
        metaRow.addView(tvStatsBar);

        metaScroll.addView(metaRow, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        metaContainer.addView(metaScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View metaDivider = new View(this);
        metaDivider.setBackgroundColor(dividerColor);
        metaContainer.addView(metaDivider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Math.round(density))));
        rootLayout.addView(metaContainer);

        // שורת חיפוש והחלפה מוטמעת (כמו בחיפוש קבצים + החלפה והתאמות מרובות)
        findReplaceBar = buildInlineFindReplaceBar(theme, glassFill, textPrimary, textSecondary, accent, darkBg);
        findReplaceBar.setVisibility(View.GONE);
        rootLayout.addView(findReplaceBar);

        // גלאי זום צביטה ב-2 אצבעות ברמת המכולה כדי שה-ScrollView/EditText לא יחטפו את המחווה
        ScaleGestureDetector pinchDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float factor = detector.getScaleFactor();
                if (Math.abs(factor - 1f) < 0.002f) return true;
                currentFontSp = Math.max(9f, Math.min(32f, currentFontSp * factor));
                editorView.setTextSize(TypedValue.COMPLEX_UNIT_SP, currentFontSp);
                editorView.post(() -> gutterView.syncFromEditor(editorView));
                return true;
            }

            @Override
            public void onScaleEnd(ScaleGestureDetector detector) {
                PrefsManager.setTextFontSizeSp(CodeEditorActivity.this, currentFontSp);
            }
        });

        // גוף העורך (שמאל לימין לקוד נקי + מספרי שורות מסונכרנים)
        editorVScroll = new PinchAwareScrollView(this, pinchDetector);
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
        editorView.setTextSize(TypedValue.COMPLEX_UNIT_SP, currentFontSp);
        editorView.setGravity(Gravity.TOP | Gravity.START);
        editorView.setPadding(16, 16, 18, 80);
        if (readOnlyMode) {
            editorView.setKeyListener(null);
            editorView.setTextIsSelectable(true);
        }

        editorHScroll = new HorizontalScrollView(this);
        editorHScroll.setFillViewport(true);

        editorSlot = new FrameLayout(this);
        codeContainer.addView(editorSlot, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        applyWordWrapLayout();

        editorVScroll.addView(codeContainer, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        rootLayout.addView(editorVScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        markdownWebView = new WebView(this);
        markdownWebView.setVisibility(View.GONE);
        rootLayout.addView(markdownWebView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(rootLayout);

        // טעינת תוכן
        String initialContent = roContent != null ? roContent : loadInitialText();
        suppressHistory = true;
        editorView.setText(initialContent);
        CodeSyntaxHighlighter.highlight(editorView.getText(), currentLang, darkBg);
        suppressHistory = false;
        pushUndoSnapshot(initialContent);
        updateStats();
        editorView.post(() -> gutterView.syncFromEditor(editorView));
        if (previewMarkdown) {
            applyMarkdownPreviewState(darkBg, textPrimary, accent);
        }

        editorView.addTextChangedListener(new TextWatcher() {
            private String before;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                before = !suppressHistory ? s.toString() : null;
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int beforeLen, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!suppressHistory) {
                    if (before != null && !before.contentEquals(s)) {
                        pushUndoSnapshot(before);
                        redoStack.clear();
                    }
                    before = null;
                    dirty = true;
                    tvTitle.setText(displayTitle + " *");
                }
                CodeSyntaxHighlighter.highlight(s, currentLang, darkBg);
                if (!suppressMatchClear && findReplaceBar.getVisibility() == View.VISIBLE && etFindInput != null) {
                    refreshSearchHighlights(false);
                }
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

    private void pushUndoSnapshot(String snap) {
        if (snap == null) return;
        undoStack.push(snap);
        undoTotalChars += snap.length();
        while ((undoStack.size() > MAX_UNDO_ENTRIES || undoTotalChars > MAX_UNDO_TOTAL_CHARS)
                && undoStack.size() > 2) {
            String removed = undoStack.removeLast();
            undoTotalChars -= removed.length();
        }
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
        } catch (OutOfMemoryError oom) {
            System.gc();
            AppLogger.error(this, "CodeEditor", "OutOfMemory loading text", oom);
            Toast.makeText(this, "הקובץ כבד מדי לטעינה מלאה בזיכרון", Toast.LENGTH_LONG).show();
            return "";
        } catch (Throwable e) {
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
        undoTotalChars -= prev.length();
        redoStack.push(current);
        suppressHistory = true;
        editorView.setText(prev);
        editorView.setSelection(Math.min(prev.length(), editorView.getSelectionStart()));
        suppressHistory = false;
    }

    private void redo() {
        if (redoStack.isEmpty()) return;
        String next = redoStack.pop();
        pushUndoSnapshot(editorView.getText().toString());
        suppressHistory = true;
        editorView.setText(next);
        editorView.setSelection(next.length());
        suppressHistory = false;
    }

    private void applyWordWrapLayout() {
        int selStart = editorView.getSelectionStart();
        int selEnd = editorView.getSelectionEnd();
        if (editorView.getParent() instanceof ViewGroup) {
            ((ViewGroup) editorView.getParent()).removeView(editorView);
        }
        if (editorHScroll.getParent() instanceof ViewGroup) {
            ((ViewGroup) editorHScroll.getParent()).removeView(editorHScroll);
        }
        editorSlot.removeAllViews();

        if (wordWrap) {
            editorView.setHorizontallyScrolling(false);
            editorSlot.addView(editorView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            editorView.setHorizontallyScrolling(true);
            editorHScroll.addView(editorView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            editorSlot.addView(editorHScroll, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        if (selStart >= 0 && selEnd >= selStart && selEnd <= editorView.length()) {
            editorView.setSelection(selStart, selEnd);
        }
        editorView.requestLayout();
        editorView.post(() -> gutterView.syncFromEditor(editorView));
    }

    private void toggleInfoBar(int accent, int textSecondary) {
        if (!PrefsManager.isReduceAnimations(this)) {
            AutoTransition transition = new AutoTransition();
            transition.setDuration(170);
            TransitionManager.beginDelayedTransition(rootLayout, transition);
        }
        boolean open = metaContainer.getVisibility() != View.VISIBLE;
        metaContainer.setVisibility(open ? View.VISIBLE : View.GONE);
        if (btnInfoToggle != null) {
            btnInfoToggle.setColorFilter(open ? accent : textSecondary);
        }
    }

    private void toggleFindReplaceBar() {
        if (!PrefsManager.isReduceAnimations(this)) {
            AutoTransition transition = new AutoTransition();
            transition.setDuration(170);
            TransitionManager.beginDelayedTransition(rootLayout, transition);
        }
        boolean opening = findReplaceBar.getVisibility() != View.VISIBLE;
        findReplaceBar.setVisibility(opening ? View.VISIBLE : View.GONE);
        if (opening) {
            if (previewMarkdown) {
                previewMarkdown = false;
                applyMarkdownPreviewState(activeDarkBg, activeTextPrimaryColor, activeAccentColor);
            }
            etFindInput.requestFocus();
            refreshSearchHighlights(true);
        } else {
            clearSearchHighlights();
        }
    }

    private LinearLayout buildInlineFindReplaceBar(
            int theme, int cardBg, int textPrimary, int textSecondary, int accent, boolean darkBg) {
        float d = getResources().getDisplayMetrics().density;
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        container.setBackgroundColor(cardBg);
        container.setPadding(Math.round(12 * d), Math.round(8 * d), Math.round(12 * d), Math.round(8 * d));

        // שורת חיפוש + ניווט תוצאות מרובות
        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);

        etFindInput = new EditText(this);
        etFindInput.setHint("חפש בקוד / טקסט...");
        etFindInput.setHintTextColor(textSecondary);
        etFindInput.setTextColor(textPrimary);
        etFindInput.setTextSize(13.5f);
        etFindInput.setSingleLine(true);
        etFindInput.setPadding(Math.round(10 * d), Math.round(7 * d), Math.round(10 * d), Math.round(7 * d));
        GradientDrawable fieldBg = new GradientDrawable();
        fieldBg.setCornerRadius(10f * d);
        fieldBg.setColor(ColorUtils.setAlphaComponent(accent, darkBg ? 28 : 16));
        etFindInput.setBackground(fieldBg);
        searchRow.addView(etFindInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        tvFindCounter = new TextView(this);
        tvFindCounter.setText("0 תוצאות");
        tvFindCounter.setTextColor(textSecondary);
        tvFindCounter.setTextSize(11.5f);
        tvFindCounter.setPadding(Math.round(8 * d), 0, Math.round(8 * d), 0);
        searchRow.addView(tvFindCounter);

        ImageButton btnPrev = ThemeHelper.createBorderlessSvgButton(
                this, R.drawable.ic_chevron_up, accent, "תוצאה קודמת", 34, 6, v -> navigateSearchMatch(-1));
        ImageButton btnNext = ThemeHelper.createBorderlessSvgButton(
                this, R.drawable.ic_chevron_down, accent, "תוצאה הבאה", 34, 6, v -> navigateSearchMatch(1));
        ImageButton btnCloseSearch = ThemeHelper.createBorderlessSvgButton(
                this, R.drawable.ic_close, textSecondary, "סגור חיפוש", 34, 6, v -> {
                    findReplaceBar.setVisibility(View.GONE);
                    clearSearchHighlights();
                });
        searchRow.addView(btnPrev);
        searchRow.addView(btnNext);
        searchRow.addView(btnCloseSearch);
        container.addView(searchRow);

        etFindInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                refreshSearchHighlights(true);
            }
        });

        if (!readOnlyMode) {
            LinearLayout replaceRow = new LinearLayout(this);
            replaceRow.setOrientation(LinearLayout.HORIZONTAL);
            replaceRow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rLp.topMargin = Math.round(6 * d);
            replaceRow.setLayoutParams(rLp);

            etReplaceInput = new EditText(this);
            etReplaceInput.setHint("החלף ב...");
            etReplaceInput.setHintTextColor(textSecondary);
            etReplaceInput.setTextColor(textPrimary);
            etReplaceInput.setTextSize(13.5f);
            etReplaceInput.setSingleLine(true);
            etReplaceInput.setPadding(Math.round(10 * d), Math.round(7 * d), Math.round(10 * d), Math.round(7 * d));
            GradientDrawable repBg = new GradientDrawable();
            repBg.setCornerRadius(10f * d);
            repBg.setColor(ColorUtils.setAlphaComponent(accent, darkBg ? 28 : 16));
            etReplaceInput.setBackground(repBg);
            replaceRow.addView(etReplaceInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView btnRepOne = makeChip("החלף", accent, v -> replaceCurrentMatch());
            TextView btnRepAll = makeChip("החלף הכל", accent, v -> replaceAllMatches());
            replaceRow.addView(btnRepOne);
            replaceRow.addView(btnRepAll);
            container.addView(replaceRow);
        }

        return container;
    }

    private void clearSearchHighlights() {
        searchMatches.clear();
        activeMatchIndex = -1;
        Editable ed = editorView.getText();
        if (ed != null) {
            BackgroundColorSpan[] spans = ed.getSpans(0, ed.length(), BackgroundColorSpan.class);
            for (BackgroundColorSpan sp : spans) {
                ed.removeSpan(sp);
            }
        }
        if (tvFindCounter != null) {
            tvFindCounter.setText("0 תוצאות");
        }
    }

    private void refreshSearchHighlights(boolean jumpToFirst) {
        searchMatches.clear();
        Editable ed = editorView.getText();
        if (ed == null) return;
        BackgroundColorSpan[] oldSpans = ed.getSpans(0, ed.length(), BackgroundColorSpan.class);
        for (BackgroundColorSpan sp : oldSpans) {
            ed.removeSpan(sp);
        }

        String q = etFindInput != null ? etFindInput.getText().toString() : "";
        if (q.isEmpty()) {
            activeMatchIndex = -1;
            if (tvFindCounter != null) tvFindCounter.setText("0 תוצאות");
            return;
        }

        String text = ed.toString();
        String lowerText = text.toLowerCase(Locale.ROOT);
        String lowerQ = q.toLowerCase(Locale.ROOT);
        int idx = 0;
        while ((idx = lowerText.indexOf(lowerQ, idx)) >= 0) {
            searchMatches.add(new int[]{idx, idx + q.length()});
            idx += Math.max(1, q.length());
        }

        if (searchMatches.isEmpty()) {
            activeMatchIndex = -1;
            if (tvFindCounter != null) tvFindCounter.setText("0 תוצאות");
            return;
        }

        if (jumpToFirst || activeMatchIndex < 0 || activeMatchIndex >= searchMatches.size()) {
            activeMatchIndex = 0;
        }

        int otherColor = ColorUtils.setAlphaComponent(Color.parseColor("#F59E0B"), 85);
        int activeColor = ColorUtils.setAlphaComponent(activeAccentColor, 130);
        for (int i = 0; i < searchMatches.size(); i++) {
            int[] spanRange = searchMatches.get(i);
            ed.setSpan(new BackgroundColorSpan(i == activeMatchIndex ? activeColor : otherColor),
                    spanRange[0], spanRange[1], Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        focusActiveMatch();
    }

    private void navigateSearchMatch(int delta) {
        if (searchMatches.isEmpty()) {
            refreshSearchHighlights(true);
            return;
        }
        activeMatchIndex = (activeMatchIndex + delta + searchMatches.size()) % searchMatches.size();
        refreshSearchHighlights(false);
    }

    private void focusActiveMatch() {
        if (activeMatchIndex < 0 || activeMatchIndex >= searchMatches.size()) return;
        int[] range = searchMatches.get(activeMatchIndex);
        if (tvFindCounter != null) {
            tvFindCounter.setText((activeMatchIndex + 1) + "/" + searchMatches.size());
        }
        editorView.setSelection(range[0], range[1]);
        Layout layout = editorView.getLayout();
        if (layout != null && editorVScroll != null) {
            int line = layout.getLineForOffset(range[0]);
            int y = Math.max(0, layout.getLineTop(line) - editorVScroll.getHeight() / 3);
            editorVScroll.smoothScrollTo(0, y);
        }
    }

    private void replaceCurrentMatch() {
        if (readOnlyMode || searchMatches.isEmpty() || activeMatchIndex < 0) return;
        String rep = etReplaceInput != null ? etReplaceInput.getText().toString() : "";
        int[] range = searchMatches.get(activeMatchIndex);
        suppressMatchClear = true;
        editorView.getText().replace(range[0], range[1], rep);
        suppressMatchClear = false;
        refreshSearchHighlights(false);
    }

    private void replaceAllMatches() {
        if (readOnlyMode || searchMatches.isEmpty()) return;
        String q = etFindInput != null ? etFindInput.getText().toString() : "";
        String rep = etReplaceInput != null ? etReplaceInput.getText().toString() : "";
        if (q.isEmpty()) return;
        int count = searchMatches.size();
        Editable ed = editorView.getText();
        suppressMatchClear = true;
        for (int i = searchMatches.size() - 1; i >= 0; i--) {
            int[] r = searchMatches.get(i);
            ed.replace(r[0], r[1], rep);
        }
        suppressMatchClear = false;
        refreshSearchHighlights(true);
        Toast.makeText(this, "הוחלפו " + count + " התאמות", Toast.LENGTH_SHORT).show();
    }

    private void toggleWordWrap(int accent, int textSecondary) {
        wordWrap = !wordWrap;
        PrefsManager.setTextWrapEnabled(this, wordWrap);
        if (btnWrapToggle != null) {
            btnWrapToggle.setColorFilter(wordWrap ? accent : textSecondary);
        }
        applyWordWrapLayout();
        Toast.makeText(this, wordWrap ? "גלישת שורות מופעלת" : "גלישה אופקית ללא שבירה", Toast.LENGTH_SHORT).show();
    }

    private void toggleMarkdownPreview(boolean darkBg, int textPrimary, int accent) {
        previewMarkdown = !previewMarkdown;
        applyMarkdownPreviewState(darkBg, textPrimary, accent);
    }

    private void applyMarkdownPreviewState(boolean darkBg, int textPrimary, int accent) {
        if (previewMarkdown) {
            String textHex = String.format("#%06X", (0xFFFFFF & textPrimary));
            String accHex = String.format("#%06X", (0xFFFFFF & accent));
            String html = CodeSyntaxHighlighter.markdownToHtml(editorView.getText().toString(), darkBg, textHex, accHex);
            markdownWebView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
            editorVScroll.setVisibility(View.GONE);
            markdownWebView.setVisibility(View.VISIBLE);
            if (btnMarkdownEye != null) {
                btnMarkdownEye.setImageResource(R.drawable.ic_visibility_off);
                btnMarkdownEye.setContentDescription("חזור לעריכת קוד Markdown");
            }
        } else {
            markdownWebView.setVisibility(View.GONE);
            editorVScroll.setVisibility(View.VISIBLE);
            if (btnMarkdownEye != null) {
                btnMarkdownEye.setImageResource(R.drawable.ic_visibility);
                btnMarkdownEye.setContentDescription("תצוגת Markdown");
            }
        }
    }

    private void updateStats() {
        Editable ed = editorView.getText();
        int chars = ed != null ? ed.length() : 0;
        int lines = 1;
        int words = 0;
        boolean inWord = false;
        for (int i = 0; i < chars; i++) {
            char c = ed.charAt(i);
            if (c == '\n') {
                lines++;
            }
            if (Character.isWhitespace(c)) {
                inWord = false;
            } else if (!inWord) {
                words++;
                inWord = true;
            }
        }
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

    @Override
    public void finish() {
        super.finish();
        ThemeHelper.overrideCloseTransition(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing() && targetFile != null) {
            CacheCleanupManager.cleanupPreviewFileIfManagedCache(this, targetFile);
        }
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

    private static class PinchAwareScrollView extends ScrollView {
        private final ScaleGestureDetector pinchDetector;
        private boolean pinching = false;

        PinchAwareScrollView(CodeEditorActivity ctx, ScaleGestureDetector pinchDetector) {
            super(ctx);
            this.pinchDetector = pinchDetector;
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent ev) {
            if (pinchDetector != null) {
                pinchDetector.onTouchEvent(ev);
            }
            int action = ev.getActionMasked();
            boolean inProgress = pinchDetector != null && pinchDetector.isInProgress();
            if (ev.getPointerCount() >= 2 || inProgress) {
                if (!pinching) {
                    pinching = true;
                    MotionEvent cancel = MotionEvent.obtain(ev);
                    cancel.setAction(MotionEvent.ACTION_CANCEL);
                    super.dispatchTouchEvent(cancel);
                    cancel.recycle();
                }
                requestDisallowInterceptTouchEvent(true);
                return true;
            }
            if (pinching) {
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    pinching = false;
                }
                return true;
            }
            return super.dispatchTouchEvent(ev);
        }
    }
}
