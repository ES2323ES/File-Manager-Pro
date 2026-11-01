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
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileBrowserActivity extends AppCompatActivity {

    public static final String EXTRA_MODE = "extra_mode";
    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_INITIAL_DIR = "extra_initial_dir";

    public static final String MODE_INTERNAL = "INTERNAL";
    public static final String MODE_SD_CARD = "SD_CARD";
    public static final String MODE_DOWNLOADS = "DOWNLOADS";
    public static final String MODE_IMAGES = "IMAGES";
    public static final String MODE_AUDIO = "AUDIO";
    public static final String MODE_VIDEOS = "VIDEOS";
    public static final String MODE_DOCUMENTS = "DOCUMENTS";
    public static final String MODE_APPS = "APPS";
    public static final String MODE_RECENT = "RECENT";
    public static final String MODE_RECYCLE_BIN = "RECYCLE_BIN";

    // לוח עריכה פנימי להעברה והעתקה בין תיקיות
    private static File clipboardFile = null;
    private static boolean clipboardIsMove = false;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<File> allLoadedFiles = new ArrayList<>();
    private final List<File> displayedFiles = new ArrayList<>();

    private String currentMode = MODE_INTERNAL;
    private File currentDir = null;
    private File rootBoundary = null;

    private TextView tvHeaderTitle;
    private TextView tvPathBreadcrumb;
    private EditText etSearch;
    private LinearLayout clipboardBar;
    private TextView tvClipboardStatus;
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView recyclerView;
    private TextView tvEmptyState;
    private FileAdapter adapter;
    private AudioPlayerManager.PlaybackListener miniAudioListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        currentMode = getIntent().getStringExtra(EXTRA_MODE);
        if (currentMode == null) currentMode = MODE_INTERNAL;
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        if (title == null) title = "סייר קבצים";

        int theme = PrefsManager.getThemeMode(this);
        ThemeHelper.applyWindowStatusBar(this, theme);
        int bgColor = ThemeHelper.getBgColor(theme);
        int cardColor = ThemeHelper.getCardColor(theme);
        int textPrimary = ThemeHelper.getPrimaryTextColor(theme);
        int textSecondary = ThemeHelper.getSecondaryTextColor(theme);
        int accent = ThemeHelper.getAccentColor(theme);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(bgColor);

        // סרגל עליון של סייר הקבצים (RTL: ימין = חזרה + כותרת, שמאל = סימן + ליצירת תיקייה או קובץ חדש)
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(ThemeHelper.getTopBarColor(theme));
        topBar.setPadding(24, 20, 24, 20);

        Button btnBack = new Button(this);
        btnBack.setText("חזרה");
        btnBack.setTextColor(accent);
        btnBack.setBackgroundColor(Color.TRANSPARENT);
        btnBack.setOnClickListener(v -> navigateUpOrFinish());
        topBar.addView(btnBack);

        tvHeaderTitle = new TextView(this);
        tvHeaderTitle.setText(title);
        tvHeaderTitle.setTextSize(17f);
        tvHeaderTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvHeaderTitle.setTextColor(textPrimary);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleLp.setMarginStart(16);
        topBar.addView(tvHeaderTitle, titleLp);

        // סעיף יז: למעלה משמאל סימן של פלוס (+) הפותח בחירה בין "תיקייה חדשה" או "קובץ חדש"
        if (isDirectoryMode()) {
            Button btnPlus = new Button(this);
            btnPlus.setText("+");
            btnPlus.setTextSize(20f);
            btnPlus.setTypeface(Typeface.DEFAULT_BOLD);
            btnPlus.setTextColor(accent);
            GradientDrawable plusShape = new GradientDrawable();
            plusShape.setCornerRadius(22f);
            plusShape.setColor(Color.argb(26, Color.red(accent), Color.green(accent), Color.blue(accent)));
            plusShape.setStroke(2, accent);
            btnPlus.setBackground(plusShape);
            LinearLayout.LayoutParams plusLp = new LinearLayout.LayoutParams(112, 104);
            btnPlus.setLayoutParams(plusLp);
            btnPlus.setPadding(0, 0, 0, 0);
            btnPlus.setOnClickListener(v -> showCreatePlusMenu());
            topBar.addView(btnPlus);
        } else if (MODE_RECYCLE_BIN.equals(currentMode)) {
            Button btnEmptyBin = ThemedDialogHelper.createThemedButton(this, "רוקן סל", theme, true, true);
            btnEmptyBin.setOnClickListener(v -> confirmEmptyRecycleBinTwoStep());
            topBar.addView(btnEmptyBin);
        }

        root.addView(topBar);

        tvPathBreadcrumb = new TextView(this);
        tvPathBreadcrumb.setTextColor(textSecondary);
        tvPathBreadcrumb.setTextSize(12f);
        tvPathBreadcrumb.setPadding(32, 12, 32, 8);
        root.addView(tvPathBreadcrumb);

        // תיבת חיפוש מהירה
        etSearch = new EditText(this);
        etSearch.setHint("חיפוש קובץ או תיקייה...");
        etSearch.setTextColor(textPrimary);
        etSearch.setHintTextColor(textSecondary);
        GradientDrawable searchBg = new GradientDrawable();
        searchBg.setCornerRadius(22f);
        searchBg.setColor(cardColor);
        etSearch.setBackground(searchBg);
        etSearch.setPadding(32, 22, 32, 22);
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        searchLp.setMargins(28, 8, 28, 12);
        root.addView(etSearch, searchLp);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterFiles(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });

        // סרגל הדבקה בעת העתקה או העברה
        clipboardBar = new LinearLayout(this);
        clipboardBar.setOrientation(LinearLayout.HORIZONTAL);
        clipboardBar.setGravity(Gravity.CENTER_VERTICAL);
        clipboardBar.setBackgroundColor(Color.parseColor("#1D4ED8"));
        clipboardBar.setPadding(28, 16, 28, 16);
        clipboardBar.setVisibility(View.GONE);

        tvClipboardStatus = new TextView(this);
        tvClipboardStatus.setTextColor(Color.WHITE);
        tvClipboardStatus.setTextSize(13f);
        clipboardBar.addView(tvClipboardStatus, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button btnPasteHere = new Button(this);
        btnPasteHere.setText("הדבק כאן");
        btnPasteHere.setTextColor(Color.WHITE);
        btnPasteHere.setOnClickListener(v -> executePasteHere());
        clipboardBar.addView(btnPasteHere);

        Button btnCancelClip = new Button(this);
        btnCancelClip.setText("ביטול");
        btnCancelClip.setTextColor(Color.WHITE);
        btnCancelClip.setBackgroundColor(Color.TRANSPARENT);
        btnCancelClip.setOnClickListener(v -> {
            clipboardFile = null;
            updateClipboardBar();
        });
        clipboardBar.addView(btnCancelClip);

        root.addView(clipboardBar);

        swipeRefresh = new SwipeRefreshLayout(this);
        swipeRefresh.setColorSchemeColors(0xFF1D4ED8, 0xFF0284C7);
        // סעיף ז: העיגול מופיע עד לסיום הסריקה האמיתית, וההודעה מופיעה רק בסיום הסריקה עם הטקסט "כל הקבצים רועננו בהצלחה"
        swipeRefresh.setOnRefreshListener(() -> loadFilesForCurrentMode(true));

        LinearLayout contentFrame = new LinearLayout(this);
        contentFrame.setOrientation(LinearLayout.VERTICAL);

        tvEmptyState = new TextView(this);
        tvEmptyState.setText("לא נמצאו קבצים בתיקייה או בקטגוריה זו");
        tvEmptyState.setTextColor(textSecondary);
        tvEmptyState.setTextSize(14f);
        tvEmptyState.setGravity(Gravity.CENTER);
        tvEmptyState.setPadding(40, 90, 40, 40);
        tvEmptyState.setVisibility(View.GONE);
        contentFrame.addView(tvEmptyState);

        recyclerView = new RecyclerView(this);
        applyFolderLayoutMode();
        adapter = new FileAdapter(cardColor, textPrimary, textSecondary, accent);
        recyclerView.setAdapter(adapter);
        contentFrame.addView(recyclerView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        swipeRefresh.addView(contentFrame);
        root.addView(swipeRefresh, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // סעיף יב: שורת השיר התחתונה ביציאה מהנגן (עם השהיה והחלקה לעצירת השיר)
        miniAudioListener = MiniAudioBarHelper.attach(this, root);

        setContentView(root);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                navigateUpOrFinish();
            }
        });

        initInitialDirectory();
        updateClipboardBar();
        loadFilesForCurrentMode(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (miniAudioListener != null) {
            miniAudioListener.onPlaybackStateChanged();
        }
        if (recyclerView != null) {
            applyFolderLayoutMode();
            loadFilesForCurrentMode(false);
        }
    }

    private void applyFolderLayoutMode() {
        if (PrefsManager.isGridLayout(this)) {
            recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        } else {
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
        }
    }

    private boolean isDirectoryMode() {
        return MODE_INTERNAL.equals(currentMode)
                || MODE_SD_CARD.equals(currentMode)
                || MODE_DOWNLOADS.equals(currentMode);
    }

    private void initInitialDirectory() {
        String initialDirExtra = getIntent().getStringExtra(EXTRA_INITIAL_DIR);
        if (initialDirExtra != null && !initialDirExtra.isEmpty()) {
            File target = new File(initialDirExtra);
            if (target.exists() && target.isDirectory()) {
                StorageUtils.StorageStat sd = StorageUtils.getExternalSdCardStat(this);
                if (sd.available && sd.root != null
                        && target.getAbsolutePath().startsWith(sd.root.getAbsolutePath())) {
                    currentMode = MODE_SD_CARD;
                    rootBoundary = sd.root;
                } else {
                    currentMode = MODE_INTERNAL;
                    rootBoundary = Environment.getExternalStorageDirectory();
                }
                currentDir = target;
                tvHeaderTitle.setText(target.equals(rootBoundary)
                        ? (MODE_SD_CARD.equals(currentMode) ? "כרטיס SD" : "אחסון ראשי")
                        : target.getName());
                return;
            }
        }

        if (MODE_INTERNAL.equals(currentMode)) {
            rootBoundary = Environment.getExternalStorageDirectory();
            currentDir = rootBoundary;
        } else if (MODE_SD_CARD.equals(currentMode)) {
            StorageUtils.StorageStat sd = StorageUtils.getExternalSdCardStat(this);
            rootBoundary = sd.root;
            currentDir = sd.root;
        } else if (MODE_DOWNLOADS.equals(currentMode)) {
            rootBoundary = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            currentDir = rootBoundary;
        } else if (MODE_RECYCLE_BIN.equals(currentMode)) {
            rootBoundary = RecycleBinManager.getTrashDir(this);
            currentDir = rootBoundary;
        }
    }

    private void updateClipboardBar() {
        if (clipboardFile != null && isDirectoryMode() && currentDir != null) {
            clipboardBar.setVisibility(View.VISIBLE);
            String op = clipboardIsMove ? "העברה: " : "העתקה: ";
            tvClipboardStatus.setText(op + clipboardFile.getName());
        } else {
            clipboardBar.setVisibility(View.GONE);
        }
    }

    private void loadFilesForCurrentMode() {
        loadFilesForCurrentMode(false);
    }

    private void loadFilesForCurrentMode(final boolean fromUserPullToRefresh) {
        if (MODE_SD_CARD.equals(currentMode) && currentDir == null) {
            tvPathBreadcrumb.setText("כרטיס SD חיצוני אינו מחובר למכשיר");
            allLoadedFiles.clear();
            filterFiles(etSearch.getText().toString());
            if (fromUserPullToRefresh) {
                swipeRefresh.setRefreshing(false);
                Toast.makeText(this, "כל הקבצים רועננו בהצלחה", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        if (currentDir != null) {
            tvPathBreadcrumb.setText(currentDir.getAbsolutePath());
        } else {
            tvPathBreadcrumb.setText("קטגוריה: " + tvHeaderTitle.getText());
        }

        final boolean showHidden = PrefsManager.isShowHidden(this);

        executor.execute(() -> {
            List<File> result = new ArrayList<>();
            if (isDirectoryMode() || MODE_RECYCLE_BIN.equals(currentMode)) {
                if (currentDir != null && currentDir.exists()) {
                    File[] children = currentDir.listFiles();
                    if (children != null) {
                        for (File f : children) {
                            if (MODE_RECYCLE_BIN.equals(currentMode) && "trash_manifest.json".equals(f.getName())) {
                                continue;
                            }
                            if (!showHidden && f.getName().startsWith(".") && !MODE_RECYCLE_BIN.equals(currentMode)) {
                                continue;
                            }
                            result.add(f);
                        }
                    }
                }
            } else {
                File root = Environment.getExternalStorageDirectory();
                collectMatchingCategoryFiles(root, result, showHidden, 0);
                StorageUtils.StorageStat sd = StorageUtils.getExternalSdCardStat(this);
                if (sd.available && sd.root != null) {
                    collectMatchingCategoryFiles(sd.root, result, showHidden, 0);
                }
            }

            sortFileList(result);

            runOnUiThread(() -> {
                allLoadedFiles.clear();
                allLoadedFiles.addAll(result);
                filterFiles(etSearch.getText().toString());
                updateClipboardBar();
                if (fromUserPullToRefresh) {
                    swipeRefresh.setRefreshing(false);
                    Toast.makeText(this, "כל הקבצים רועננו בהצלחה", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void collectMatchingCategoryFiles(File dir, List<File> out, boolean showHidden, int depth) {
        if (dir == null || !dir.exists() || depth > 7 || out.size() > 800) return;
        if (dir.getName().equals(".FileManagerPro_Trash") || dir.getName().equals("Android")) return;
        if (!showHidden && dir.getName().startsWith(".")) return;

        File[] children = dir.listFiles();
        if (children == null) return;
        long recentCutoff = System.currentTimeMillis() - (7L * 24L * 3600L * 1000L);

        for (File f : children) {
            if (!showHidden && f.getName().startsWith(".")) continue;
            if (f.isDirectory()) {
                collectMatchingCategoryFiles(f, out, showHidden, depth + 1);
            } else {
                String name = f.getName().toLowerCase(Locale.ROOT);
                if (MODE_IMAGES.equals(currentMode) && MediaFormatHelper.isImageFile(name)) {
                    out.add(f);
                } else if (MODE_AUDIO.equals(currentMode) && MediaFormatHelper.isAudioFile(name)) {
                    out.add(f);
                } else if (MODE_VIDEOS.equals(currentMode) && MediaFormatHelper.isVideoFile(name)) {
                    out.add(f);
                } else if (MODE_DOCUMENTS.equals(currentMode) && MediaFormatHelper.isDocumentFile(name)) {
                    out.add(f);
                } else if (MODE_APPS.equals(currentMode) && MediaFormatHelper.isInstallableAppPackage(name)) {
                    out.add(f);
                } else if (MODE_RECENT.equals(currentMode) && f.lastModified() >= recentCutoff) {
                    out.add(f);
                }
            }
        }
    }

    // סעיף כא: תיקיות בעברית תמיד מופיעות לפני תיקיות באנגלית!
    private void sortFileList(List<File> list) {
        final int sortMode = PrefsManager.getSortMode(this);
        final boolean asc = PrefsManager.isSortAscending(this);

        Collections.sort(list, (a, b) -> {
            if (a.isDirectory() != b.isDirectory()) {
                return a.isDirectory() ? -1 : 1;
            }
            // עבור תיקיות: תמיד תיקיות בעברית לפני תיקיות באנגלית
            if (a.isDirectory() && b.isDirectory()) {
                boolean aHeb = StorageUtils.startsWithHebrew(a.getName());
                boolean bHeb = StorageUtils.startsWithHebrew(b.getName());
                if (aHeb != bHeb) {
                    return aHeb ? -1 : 1;
                }
            }
            int cmp;
            if (sortMode == PrefsManager.SORT_SIZE) {
                cmp = Long.compare(a.length(), b.length());
            } else if (sortMode == PrefsManager.SORT_DATE) {
                cmp = Long.compare(a.lastModified(), b.lastModified());
            } else if (sortMode == PrefsManager.SORT_TYPE) {
                cmp = getExtension(a.getName()).compareToIgnoreCase(getExtension(b.getName()));
            } else {
                cmp = StorageUtils.compareHebrewFirst(a.getName(), b.getName());
            }
            return asc ? cmp : -cmp;
        });
    }

    private String getExtension(String name) {
        int i = name.lastIndexOf('.');
        return i > 0 ? name.substring(i + 1) : "";
    }

    private void filterFiles(String query) {
        displayedFiles.clear();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        for (File f : allLoadedFiles) {
            if (q.isEmpty() || f.getName().toLowerCase(Locale.ROOT).contains(q)) {
                displayedFiles.add(f);
            }
        }
        tvEmptyState.setVisibility(displayedFiles.isEmpty() ? View.VISIBLE : View.GONE);
        adapter.notifyDataSetChanged();
    }

    // סעיף יז: לחיצה על + פותחת בחירה בין תיקייה חדשה או קובץ חדש
    private void showCreatePlusMenu() {
        ThemedDialogHelper.showThemedMenu(
                this,
                "יצירה בתיקייה הנוכחית",
                currentDir != null ? currentDir.getName() : "",
                Arrays.asList("תיקייה חדשה", "קובץ חדש"),
                which -> {
                    if (which == 0) {
                        promptCreateFolder();
                    } else {
                        promptCreateNewFile();
                    }
                }
        );
    }

    private EditText createStyledInput(String hint, String initialValue) {
        int theme = PrefsManager.getThemeMode(this);
        EditText input = new EditText(this);
        input.setHint(hint);
        if (initialValue != null) input.setText(initialValue);
        input.setTextColor(ThemeHelper.getPrimaryTextColor(theme));
        input.setHintTextColor(ThemeHelper.getSecondaryTextColor(theme));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(18f);
        bg.setColor(ThemeHelper.getBgColor(theme));
        bg.setStroke(2, ThemeHelper.getAccentColor(theme));
        input.setBackground(bg);
        input.setPadding(28, 22, 28, 22);
        return input;
    }

    private void promptCreateFolder() {
        if (currentDir == null) return;
        EditText input = createStyledInput("הזן שם לתיקייה החדשה...", null);
        ThemedDialogHelper.showCustomDialog(
                this,
                "תיקייה חדשה",
                "התיקייה תיווצר בתוך המיקום הנוכחי:",
                input,
                "צור תיקייה",
                () -> {
                    String folderName = input.getText().toString().trim();
                    if (!folderName.isEmpty()) {
                        File nf = new File(currentDir, folderName);
                        if (nf.mkdirs()) {
                            loadFilesForCurrentMode();
                            Toast.makeText(this, "התיקייה נוצרה בהצלחה", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "לא ניתן ליצור תיקייה במיקום זה", Toast.LENGTH_SHORT).show();
                        }
                    }
                },
                "סגור",
                null,
                false
        );
    }

    private void promptCreateNewFile() {
        if (currentDir == null) return;
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);

        EditText etFileName = createStyledInput("שם הקובץ (לדוגמה: רשימה.txt)", "מסמך_חדש.txt");
        EditText etContent = createStyledInput("תוכן הקובץ (אופציונלי)...", "");
        etContent.setMinLines(3);
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cLp.topMargin = 16;
        etContent.setLayoutParams(cLp);

        form.addView(etFileName);
        form.addView(etContent);

        ThemedDialogHelper.showCustomDialog(
                this,
                "קובץ חדש",
                "הזן את שם הקובץ ותוכנו ההתחלתי:",
                form,
                "צור קובץ",
                () -> {
                    String name = etFileName.getText().toString().trim();
                    if (name.isEmpty()) return;
                    if (!name.contains(".")) name = name + ".txt";
                    File target = StorageUtils.uniqueDestinationFile(currentDir, name);
                    try (FileOutputStream fos = new FileOutputStream(target)) {
                        fos.write(etContent.getText().toString().getBytes(StandardCharsets.UTF_8));
                        loadFilesForCurrentMode();
                        Toast.makeText(this, "הקובץ נוצר בהצלחה: " + target.getName(), Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(this, "יצירת הקובץ נכשלה: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                },
                "סגור",
                null,
                false
        );
    }

    private void executePasteHere() {
        if (clipboardFile == null || currentDir == null) return;
        final File src = clipboardFile;
        final boolean isMove = clipboardIsMove;
        executor.execute(() -> {
            try {
                boolean ok = isMove
                        ? StorageUtils.moveFileOrDir(src, currentDir)
                        : StorageUtils.copyFileOrDir(src, currentDir);
                runOnUiThread(() -> {
                    if (ok) {
                        clipboardFile = null;
                        updateClipboardBar();
                        loadFilesForCurrentMode();
                        Toast.makeText(this, isMove ? "הועבר בהצלחה" : "הועתק בהצלחה", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "הפעולה נכשלה — בדוק הרשאות אחסון", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "שגיאה: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    // סעיף טו: אישור + אישור נוסף לפני ריקון סל המיחזור
    private void confirmEmptyRecycleBinTwoStep() {
        ThemedDialogHelper.showCustomDialog(
                this,
                "ריקון סל המיחזור",
                "האם ברצונך לרוקן את כל הפריטים בסל המיחזור?",
                null,
                "המשך",
                () -> ThemedDialogHelper.showCustomDialog(
                        this,
                        "אישור נוסף למחיקה סופית",
                        "פעולה זו תמחק לצמיתות את כל התכולה של סל המיחזור ללא אפשרות שחזור. האם לאשר?",
                        null,
                        "כן, רוקן לצמיתות",
                        () -> {
                            int count = RecycleBinManager.emptyTrash(this);
                            loadFilesForCurrentMode();
                            Toast.makeText(this, "נמחקו לצמיתות " + count + " פריטים", Toast.LENGTH_SHORT).show();
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

    // סעיף א: פתיחה פנימית מלאה של תמונות, אודיו, וידאו, מסמכים וחילוץ ארכיונים בתוך האפליקציה עצמה ללא "פתח באמצעות"
    private void onFileClicked(File file) {
        if (MODE_RECYCLE_BIN.equals(currentMode)) {
            showFileOperationsMenu(file);
            return;
        }
        if (file.isDirectory()) {
            currentDir = file;
            loadFilesForCurrentMode();
            return;
        }

        String lower = file.getName().toLowerCase(Locale.ROOT);
        if (MediaFormatHelper.isInstallableAppPackage(lower)) {
            promptDirectApkInstallOrInspect(file);
            return;
        }

        Intent viewer = new Intent(this, BuiltInViewerActivity.class);
        viewer.putExtra(BuiltInViewerActivity.EXTRA_FILE_PATH, file.getAbsolutePath());
        startActivity(viewer);
    }

    // סעיף יב: תמיכה בהתקנת כל סוגי האפליקציות (.apk, .apks, .apkm, .xapk)
    private void promptDirectApkInstallOrInspect(File apkFile) {
        ThemedDialogHelper.showThemedMenu(
                this,
                apkFile.getName(),
                "חבילת התקנה לאנדרואיד • " + StorageUtils.formatSize(apkFile.length()),
                Arrays.asList("התקנת האפליקציה במכשיר", "צפייה בתוכן החבילה וחילוץ פנימי"),
                which -> {
                    if (which == 0) {
                        AppPackageInstaller.installAnyPackage(this, apkFile);
                    } else {
                        Intent viewer = new Intent(this, BuiltInViewerActivity.class);
                        viewer.putExtra(BuiltInViewerActivity.EXTRA_FILE_PATH, apkFile.getAbsolutePath());
                        startActivity(viewer);
                    }
                }
        );
    }

    // סעיפים יג, טז: תפריט תואם לגוון האפליקציה + אופציה מסודרת לדחיסה ל-קובץ ZIP וחילוץ ZIP
    private void showFileOperationsMenu(File file) {
        String subInfo = file.isDirectory()
                ? "תיקייה • " + file.getAbsolutePath()
                : StorageUtils.formatSize(file.length()) + " • " + file.getParent();

        if (MODE_RECYCLE_BIN.equals(currentMode)) {
            ThemedDialogHelper.showThemedMenu(
                    this,
                    file.getName(),
                    "פריט בסל המיחזור",
                    Arrays.asList("שחזר למיקום המקורי", "מחיקה לצמיתות", "פרטי פריט"),
                    which -> {
                        if (which == 0) {
                            if (RecycleBinManager.restoreFromTrash(this, file)) {
                                loadFilesForCurrentMode();
                                Toast.makeText(this, "הקובץ שוחזר בהצלחה", Toast.LENGTH_SHORT).show();
                            }
                        } else if (which == 1) {
                            StorageUtils.deleteRecursively(file);
                            loadFilesForCurrentMode();
                            Toast.makeText(this, "נמחק לצמיתות", Toast.LENGTH_SHORT).show();
                        } else {
                            showPropertiesDialog(file);
                        }
                    }
            );
            return;
        }

        final String lowerName = file.getName().toLowerCase(Locale.ROOT);
        final boolean isZip = MediaFormatHelper.isZipOrArchiveFile(lowerName);
        final boolean isAppPkg = !file.isDirectory() && MediaFormatHelper.isInstallableAppPackage(lowerName);
        List<String> actions = new ArrayList<>();
        if (isAppPkg) {
            actions.add("התקנת האפליקציה במכשיר");
        } else if (!file.isDirectory()) {
            actions.add(isZip ? "פתיחה וחילוץ קובץ ZIP" : "פתיחה בתוך האפליקציה");
        }
        actions.add("דחיסה לקובץ ZIP");
        actions.add("העתקה");
        actions.add("העברה");
        actions.add("שינוי שם");
        if (!file.isDirectory()) {
            actions.add("שיתוף קובץ");
        }
        actions.add("מאפיינים ופרטים");
        actions.add("מחיקה");

        ThemedDialogHelper.showThemedMenu(
                this,
                file.getName(),
                subInfo,
                actions,
                which -> {
                    String chosen = actions.get(which);
                    switch (chosen) {
                        case "התקנת האפליקציה במכשיר":
                            AppPackageInstaller.installAnyPackage(this, file);
                            break;
                        case "פתיחה וחילוץ קובץ ZIP":
                        case "פתיחה בתוך האפליקציה":
                            onFileClicked(file);
                            break;
                        case "דחיסה לקובץ ZIP":
                            promptCompressToZip(file);
                            break;
                        case "העתקה":
                            clipboardFile = file;
                            clipboardIsMove = false;
                            updateClipboardBar();
                            Toast.makeText(this, "מוכן להעתקה — עבור לתיקיית היעד ולחץ 'הדבק כאן'", Toast.LENGTH_LONG).show();
                            break;
                        case "העברה":
                            clipboardFile = file;
                            clipboardIsMove = true;
                            updateClipboardBar();
                            Toast.makeText(this, "מוכן להעברה — עבור לתיקיית היעד ולחץ 'הדבק כאן'", Toast.LENGTH_LONG).show();
                            break;
                        case "שינוי שם":
                            promptRenameFile(file);
                            break;
                        case "שיתוף קובץ":
                            shareFile(file);
                            break;
                        case "מאפיינים ופרטים":
                            showPropertiesDialog(file);
                            break;
                        case "מחיקה":
                            handleDeleteFile(file);
                            break;
                    }
                }
        );
    }

    // סעיף טז: יצירת קובץ ZIP עם טקסט מסודר וברור
    private void promptCompressToZip(File sourceFile) {
        String baseName = sourceFile.isDirectory()
                ? sourceFile.getName()
                : sourceFile.getName().replaceAll("\\.[^.]+$", "");
        EditText input = createStyledInput("שם ארכיון ה-ZIP...", baseName + ".zip");

        ThemedDialogHelper.showCustomDialog(
                this,
                "דחיסה לקובץ ZIP",
                "בחר את שם קובץ ה-ZIP שייווצר באותה תיקייה עבור \"" + sourceFile.getName() + "\":",
                input,
                "צור קובץ ZIP",
                () -> {
                    String zipName = input.getText().toString().trim();
                    if (zipName.isEmpty()) return;
                    if (!zipName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
                        zipName = zipName + ".zip";
                    }
                    File parent = sourceFile.getParentFile() != null ? sourceFile.getParentFile() : Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    final File targetZip = StorageUtils.uniqueDestinationFile(parent, zipName);
                    Toast.makeText(this, "דוחס לקובץ ZIP...", Toast.LENGTH_SHORT).show();
                    executor.execute(() -> {
                        try {
                            ArchiveUtils.compressToZip(sourceFile, targetZip);
                            runOnUiThread(() -> {
                                loadFilesForCurrentMode();
                                ThemedDialogHelper.showCustomDialog(
                                        this,
                                        "קובץ ה-ZIP נוצר בהצלחה",
                                        "שם הקובץ: " + targetZip.getName()
                                                + "\nנפח הארכיון: " + StorageUtils.formatSize(targetZip.length())
                                                + "\nמיקום שמירה: " + targetZip.getParent(),
                                        null,
                                        "הבנתי",
                                        null,
                                        null,
                                        null,
                                        false
                                );
                            });
                        } catch (Exception e) {
                            runOnUiThread(() -> Toast.makeText(this, "שגיאה ביצירת קובץ ZIP: " + e.getMessage(), Toast.LENGTH_LONG).show());
                        }
                    });
                },
                "סגור",
                null,
                false
        );
    }

    private void promptRenameFile(File file) {
        EditText input = createStyledInput("שם חדש...", file.getName());
        ThemedDialogHelper.showCustomDialog(
                this,
                "שינוי שם",
                "הזן שם מעודכן עבור הפריט:",
                input,
                "שמור",
                () -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.isEmpty() && file.getParentFile() != null) {
                        File dest = new File(file.getParentFile(), newName);
                        if (file.renameTo(dest)) {
                            loadFilesForCurrentMode();
                            Toast.makeText(this, "השם שונה בהצלחה", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "שינוי השם נכשל", Toast.LENGTH_SHORT).show();
                        }
                    }
                },
                "סגור",
                null,
                false
        );
    }

    private void handleDeleteFile(File file) {
        final boolean toTrash = PrefsManager.isUseRecycleBin(this);
        Runnable performDelete = () -> {
            boolean ok = toTrash ? RecycleBinManager.moveToTrash(this, file) : StorageUtils.deleteRecursively(file);
            if (ok) {
                loadFilesForCurrentMode();
                Toast.makeText(this, toTrash ? "הועבר לסל המיחזור" : "נמחק לצמיתות", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "המחיקה נכשלה — ודא הרשאת ניהול קבצים", Toast.LENGTH_SHORT).show();
            }
        };

        if (PrefsManager.isConfirmDelete(this)) {
            ThemedDialogHelper.showCustomDialog(
                    this,
                    toTrash ? "העברה לסל המיחזור" : "מחיקה לצמיתות",
                    "האם למחוק את \"" + file.getName() + "\"?",
                    null,
                    "מחק",
                    performDelete,
                    "סגור",
                    null,
                    true
            );
        } else {
            performDelete.run();
        }
    }

    private void shareFile(File file) {
        if (file.isDirectory()) {
            Toast.makeText(this, "שיתוף זמין לקבצים (ניתן לדחוס תיקייה לקובץ ZIP ולשתף)", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("*/*");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "שיתוף קובץ"));
        } catch (Exception e) {
            Toast.makeText(this, "שגיאה בשיתוף: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showPropertiesDialog(File file) {
        String date = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(new Date(file.lastModified()));
        String sizeStr = file.isDirectory()
                ? StorageUtils.formatSize(StorageUtils.computeDirectoryBytes(file, true, 0))
                : StorageUtils.formatSize(file.length());
        String msg = "שם הפריט: " + file.getName()
                + "\nסוג: " + (file.isDirectory() ? "תיקייה" : "קובץ")
                + "\nנפח: " + sizeStr
                + "\nעודכן לאחרונה: " + date
                + "\nנתיב מלא:\n" + file.getAbsolutePath();

        ThemedDialogHelper.showCustomDialog(
                this,
                "מאפייני פריט",
                msg,
                null,
                "סגור",
                null,
                null,
                null,
                false
        );
    }

    private void navigateUpOrFinish() {
        if (isDirectoryMode() && currentDir != null && rootBoundary != null
                && !currentDir.getAbsolutePath().equals(rootBoundary.getAbsolutePath())
                && currentDir.getParentFile() != null) {
            currentDir = currentDir.getParentFile();
            if (currentDir.getAbsolutePath().equals(rootBoundary.getAbsolutePath())) {
                if (MODE_SD_CARD.equals(currentMode)) {
                    tvHeaderTitle.setText("כרטיס SD");
                } else if (MODE_DOWNLOADS.equals(currentMode)) {
                    tvHeaderTitle.setText("הורדות");
                } else {
                    tvHeaderTitle.setText("אחסון ראשי");
                }
            } else {
                tvHeaderTitle.setText(currentDir.getName());
            }
            loadFilesForCurrentMode();
            return;
        }
        finish();
    }

    private class FileAdapter extends RecyclerView.Adapter<FileAdapter.VH> {
        private final int cardBg;
        private final int textPrimary;
        private final int textSecondary;
        private final int accent;
        private final SimpleDateFormat df = new SimpleDateFormat("dd/MM/yy HH:mm", Locale.US);

        FileAdapter(int cardBg, int textPrimary, int textSecondary, int accent) {
            this.cardBg = cardBg;
            this.textPrimary = textPrimary;
            this.textSecondary = textSecondary;
            this.accent = accent;
        }

        @Override
        public int getItemViewType(int position) {
            return PrefsManager.isGridLayout(FileBrowserActivity.this) ? 1 : 0;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            int layoutId = viewType == 1 ? R.layout.item_file_grid : R.layout.item_file_row;
            View v = LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            File f = displayedFiles.get(position);
            h.rowCard.setCardBackgroundColor(cardBg);
            h.tvName.setText(f.getName());
            h.tvName.setTextColor(textPrimary);
            h.tvHint.setTextColor(accent);

            // סעיף ד: הצגת האייקון האמיתי של אותו קובץ (שירים, תמונות, אפליקציות, סירטונים ותיקיות)
            FileThumbnailLoader.bindFileIcon(FileBrowserActivity.this, f, h.ivIcon);

            if (f.isDirectory()) {
                File[] sub = f.listFiles();
                int count = sub != null ? sub.length : 0;
                h.tvMeta.setText(count + " פריטים • " + df.format(new Date(f.lastModified())));
            } else {
                h.tvMeta.setText(StorageUtils.formatSize(f.length()) + " • " + df.format(new Date(f.lastModified())));
            }
            h.tvMeta.setTextColor(textSecondary);

            // סעיף ח: לחיצה על האייקון בצד שמאל של קובץ או תיקייה פותחת את אפשרויות העריכה (במקום שלוש הנקודות מימין)
            h.ivIcon.setOnClickListener(v -> showFileOperationsMenu(f));
            h.rowCard.setOnClickListener(v -> onFileClicked(f));
            h.rowCard.setOnLongClickListener(v -> {
                showFileOperationsMenu(f);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return displayedFiles.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final CardView rowCard;
            final ImageView ivIcon;
            final TextView tvName;
            final TextView tvMeta;
            final TextView tvHint;

            VH(@NonNull View itemView) {
                super(itemView);
                rowCard = itemView.findViewById(R.id.rowCard);
                ivIcon = itemView.findViewById(R.id.ivFileIcon);
                tvName = itemView.findViewById(R.id.tvFileName);
                tvMeta = itemView.findViewById(R.id.tvFileMeta);
                tvHint = itemView.findViewById(R.id.tvFileActionHint);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (miniAudioListener != null) {
            AudioPlayerManager.getInstance().removeListener(miniAudioListener);
        }
        executor.shutdownNow();
    }
}
