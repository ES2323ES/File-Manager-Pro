package com.aishhatorah.filemanagerpro;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.io.File;
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

        // סרגל עליון של סייר הקבצים
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(ThemeHelper.getTopBarColor(theme));
        topBar.setPadding(24, 20, 24, 20);

        Button btnBack = new Button(this);
        btnBack.setText("חזרה");
        btnBack.setTextColor(accent);
        btnBack.setBackgroundColor(Color.TRANSPARENT);
        btnBack.setOnClickListener(v -> onBackPressed());
        topBar.addView(btnBack);

        tvHeaderTitle = new TextView(this);
        tvHeaderTitle.setText(title);
        tvHeaderTitle.setTextSize(17f);
        tvHeaderTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvHeaderTitle.setTextColor(textPrimary);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleLp.setMarginStart(16);
        topBar.addView(tvHeaderTitle, titleLp);

        if (isDirectoryMode()) {
            Button btnNewFolder = new Button(this);
            btnNewFolder.setText("+ תיקייה");
            btnNewFolder.setAllCaps(false);
            btnNewFolder.setTextColor(accent);
            btnNewFolder.setBackgroundColor(Color.TRANSPARENT);
            btnNewFolder.setOnClickListener(v -> promptCreateFolder());
            topBar.addView(btnNewFolder);
        } else if (MODE_RECYCLE_BIN.equals(currentMode)) {
            Button btnEmptyBin = new Button(this);
            btnEmptyBin.setText("רוקן סל");
            btnEmptyBin.setAllCaps(false);
            btnEmptyBin.setTextColor(Color.parseColor("#DC2626"));
            btnEmptyBin.setBackgroundColor(Color.TRANSPARENT);
            btnEmptyBin.setOnClickListener(v -> confirmEmptyRecycleBin());
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
        etSearch.setBackgroundColor(cardColor);
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
        swipeRefresh.setOnRefreshListener(() -> {
            loadFilesForCurrentMode();
            swipeRefresh.setRefreshing(false);
            Toast.makeText(this, "הרשימה רועננה", Toast.LENGTH_SHORT).show();
        });

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
        if (PrefsManager.isGridLayout(this)) {
            recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        } else {
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
        }
        adapter = new FileAdapter(cardColor, textPrimary, textSecondary);
        recyclerView.setAdapter(adapter);
        contentFrame.addView(recyclerView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        swipeRefresh.addView(contentFrame);
        root.addView(swipeRefresh, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);

        initInitialDirectory();
        updateClipboardBar();
        loadFilesForCurrentMode();
    }

    private boolean isDirectoryMode() {
        return MODE_INTERNAL.equals(currentMode)
                || MODE_SD_CARD.equals(currentMode)
                || MODE_DOWNLOADS.equals(currentMode);
    }

    private void initInitialDirectory() {
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
        if (MODE_SD_CARD.equals(currentMode) && currentDir == null) {
            tvPathBreadcrumb.setText("כרטיס SD חיצוני אינו מחובר למכשיר");
            allLoadedFiles.clear();
            filterFiles(etSearch.getText().toString());
            return;
        }

        if (currentDir != null) {
            tvPathBreadcrumb.setText(currentDir.getAbsolutePath());
        } else {
            tvPathBreadcrumb.setText("סורק אחסון עבור קטגוריה: " + tvHeaderTitle.getText());
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
            }

            sortFileList(result);

            runOnUiThread(() -> {
                allLoadedFiles.clear();
                allLoadedFiles.addAll(result);
                filterFiles(etSearch.getText().toString());
                updateClipboardBar();
            });
        });
    }

    private void collectMatchingCategoryFiles(File dir, List<File> out, boolean showHidden, int depth) {
        if (dir == null || !dir.exists() || depth > 7 || out.size() > 600) return;
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
                if (MODE_IMAGES.equals(currentMode) && matchesExt(name, ".jpg", ".jpeg", ".png", ".webp", ".gif", ".heic")) {
                    out.add(f);
                } else if (MODE_AUDIO.equals(currentMode) && matchesExt(name, ".mp3", ".m4a", ".wav", ".aac", ".flac", ".ogg")) {
                    out.add(f);
                } else if (MODE_VIDEOS.equals(currentMode) && matchesExt(name, ".mp4", ".mkv", ".mov", ".avi", ".webm", ".3gp")) {
                    out.add(f);
                } else if (MODE_DOCUMENTS.equals(currentMode) && matchesExt(name, ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".txt", ".rtf")) {
                    out.add(f);
                } else if (MODE_APPS.equals(currentMode) && matchesExt(name, ".apk", ".xapk", ".apks")) {
                    out.add(f);
                } else if (MODE_RECENT.equals(currentMode) && f.lastModified() >= recentCutoff) {
                    out.add(f);
                }
            }
        }
    }

    private boolean matchesExt(String name, String... exts) {
        for (String e : exts) {
            if (name.endsWith(e)) return true;
        }
        return false;
    }

    private void sortFileList(List<File> list) {
        final int sortMode = PrefsManager.getSortMode(this);
        final boolean asc = PrefsManager.isSortAscending(this);

        Collections.sort(list, (a, b) -> {
            if (a.isDirectory() != b.isDirectory()) {
                return a.isDirectory() ? -1 : 1;
            }
            int cmp;
            if (sortMode == PrefsManager.SORT_SIZE) {
                cmp = Long.compare(a.length(), b.length());
            } else if (sortMode == PrefsManager.SORT_DATE) {
                cmp = Long.compare(a.lastModified(), b.lastModified());
            } else if (sortMode == PrefsManager.SORT_TYPE) {
                cmp = getExtension(a.getName()).compareToIgnoreCase(getExtension(b.getName()));
            } else {
                cmp = a.getName().compareToIgnoreCase(b.getName());
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

    private void promptCreateFolder() {
        if (currentDir == null) return;
        EditText input = new EditText(this);
        input.setHint("שם התיקייה החדשה");
        new AlertDialog.Builder(this)
                .setTitle("יצירת תיקייה חדשה")
                .setView(input)
                .setPositiveButton("צור", (d, w) -> {
                    String folderName = input.getText().toString().trim();
                    if (!folderName.isEmpty()) {
                        File nf = new File(currentDir, folderName);
                        if (nf.mkdirs()) {
                            loadFilesForCurrentMode();
                            Toast.makeText(this, "התיקייה נוצרה", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "לא ניתן ליצור תיקייה במיקום זה", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("ביטול", null)
                .show();
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
                        Toast.makeText(this, isMove ? "הקובץ הועבר בהצלחה" : "הקובץ הועתק בהצלחה", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "הפעולה נכשלה — בדוק הרשאות אחסון", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "שגיאה: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void confirmEmptyRecycleBin() {
        new AlertDialog.Builder(this)
                .setTitle("ריקון סל המיחזור")
                .setMessage("האם למחוק לצמיתות את כל הפריטים בסל המיחזור?")
                .setPositiveButton("רוקן לצמיתות", (d, w) -> {
                    int count = RecycleBinManager.emptyTrash(this);
                    loadFilesForCurrentMode();
                    Toast.makeText(this, "נמחקו לצמיתות " + count + " פריטים", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("ביטול", null)
                .show();
    }

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
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            String ext = getExtension(file.getName()).toLowerCase(Locale.ROOT);
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            if (mime == null) mime = "*/*";

            Intent viewIntent = new Intent(Intent.ACTION_VIEW);
            viewIntent.setDataAndType(uri, mime);
            viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(viewIntent, "פתיחת קובץ באמצעות"));
        } catch (Exception e) {
            Toast.makeText(this, "לא נמצאה אפליקציה מתאימה לפתיחת קובץ זה", Toast.LENGTH_SHORT).show();
        }
    }

    private void showFileOperationsMenu(File file) {
        if (MODE_RECYCLE_BIN.equals(currentMode)) {
            String[] trashActions = {"שחזר למיקום המקורי", "מחק לצמיתות", "פרטי קובץ"};
            new AlertDialog.Builder(this)
                    .setTitle(file.getName())
                    .setItems(trashActions, (d, which) -> {
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
                    })
                    .show();
            return;
        }

        List<String> actions = new ArrayList<>(Arrays.asList(
                "העתקה",
                "העברה",
                "שינוי שם",
                "מחיקה",
                "שיתוף קובץ",
                "מאפיינים ופרטים"
        ));

        new AlertDialog.Builder(this)
                .setTitle(file.getName())
                .setItems(actions.toArray(new String[0]), (d, which) -> {
                    if (which == 0) {
                        clipboardFile = file;
                        clipboardIsMove = false;
                        updateClipboardBar();
                        Toast.makeText(this, "מוכן להעתקה — עבור לתיקיית היעד ולחץ 'הדבק כאן'", Toast.LENGTH_LONG).show();
                    } else if (which == 1) {
                        clipboardFile = file;
                        clipboardIsMove = true;
                        updateClipboardBar();
                        Toast.makeText(this, "מוכן להעברה — עבור לתיקיית היעד ולחץ 'הדבק כאן'", Toast.LENGTH_LONG).show();
                    } else if (which == 2) {
                        promptRenameFile(file);
                    } else if (which == 3) {
                        handleDeleteFile(file);
                    } else if (which == 4) {
                        shareFile(file);
                    } else if (which == 5) {
                        showPropertiesDialog(file);
                    }
                })
                .show();
    }

    private void promptRenameFile(File file) {
        EditText input = new EditText(this);
        input.setText(file.getName());
        new AlertDialog.Builder(this)
                .setTitle("שינוי שם")
                .setView(input)
                .setPositiveButton("שמור", (d, w) -> {
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
                })
                .setNegativeButton("ביטול", null)
                .show();
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
            new AlertDialog.Builder(this)
                    .setTitle(toTrash ? "העברה לסל המיחזור" : "מחיקה לצמיתות")
                    .setMessage("האם למחוק את '" + file.getName() + "'?")
                    .setPositiveButton("מחק", (d, w) -> performDelete.run())
                    .setNegativeButton("ביטול", null)
                    .show();
        } else {
            performDelete.run();
        }
    }

    private void shareFile(File file) {
        if (file.isDirectory()) {
            Toast.makeText(this, "שיתוף זמין לקבצים בודדים", Toast.LENGTH_SHORT).show();
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
        String msg = "שם: " + file.getName()
                + "\nנתיב: " + file.getAbsolutePath()
                + "\nסוג: " + (file.isDirectory() ? "תיקייה" : "קובץ")
                + "\nמשקל: " + (file.isDirectory() ? "-" : StorageUtils.formatSize(file.length()))
                + "\nעודכן לאחרונה: " + date;

        new AlertDialog.Builder(this)
                .setTitle("מאפייני פריט")
                .setMessage(msg)
                .setPositiveButton("סגור", null)
                .show();
    }

    @Override
    public void onBackPressed() {
        if (isDirectoryMode() && currentDir != null && rootBoundary != null
                && !currentDir.getAbsolutePath().equals(rootBoundary.getAbsolutePath())
                && currentDir.getParentFile() != null) {
            currentDir = currentDir.getParentFile();
            loadFilesForCurrentMode();
            return;
        }
        super.onBackPressed();
    }

    private class FileAdapter extends RecyclerView.Adapter<FileAdapter.VH> {
        private final int cardBg;
        private final int textPrimary;
        private final int textSecondary;
        private final SimpleDateFormat df = new SimpleDateFormat("dd/MM/yy HH:mm", Locale.US);

        FileAdapter(int cardBg, int textPrimary, int textSecondary) {
            this.cardBg = cardBg;
            this.textPrimary = textPrimary;
            this.textSecondary = textSecondary;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_file_row, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            File f = displayedFiles.get(position);
            h.rowCard.setCardBackgroundColor(cardBg);
            h.tvName.setText(f.getName());
            h.tvName.setTextColor(textPrimary);
            h.tvHint.setTextColor(textSecondary);

            if (f.isDirectory()) {
                h.ivIcon.setImageResource(R.drawable.ic_folder);
                File[] sub = f.listFiles();
                int count = sub != null ? sub.length : 0;
                h.tvMeta.setText(count + " פריטים • " + df.format(new Date(f.lastModified())));
            } else {
                h.ivIcon.setImageResource(resolveFileIcon(f.getName()));
                h.tvMeta.setText(StorageUtils.formatSize(f.length()) + " • " + df.format(new Date(f.lastModified())));
            }
            h.tvMeta.setTextColor(textSecondary);

            h.rowCard.setOnClickListener(v -> onFileClicked(f));
            h.rowCard.setOnLongClickListener(v -> {
                showFileOperationsMenu(f);
                return true;
            });
            h.tvHint.setOnClickListener(v -> showFileOperationsMenu(f));
        }

        private int resolveFileIcon(String name) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (matchesExt(lower, ".jpg", ".jpeg", ".png", ".webp", ".gif")) return R.drawable.ic_images;
            if (matchesExt(lower, ".mp3", ".m4a", ".wav", ".aac", ".flac")) return R.drawable.ic_audio;
            if (matchesExt(lower, ".mp4", ".mkv", ".mov", ".avi", ".webm")) return R.drawable.ic_video;
            if (matchesExt(lower, ".apk", ".xapk")) return R.drawable.ic_apps;
            if (matchesExt(lower, ".pdf", ".doc", ".docx", ".txt", ".xls", ".xlsx")) return R.drawable.ic_documents;
            return R.drawable.ic_file_generic;
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
}
