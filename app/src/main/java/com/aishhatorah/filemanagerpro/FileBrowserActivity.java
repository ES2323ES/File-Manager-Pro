package com.aishhatorah.filemanagerpro;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * סעיפים 2, 3, 4, 5, 7, 14, 15, 16, 18, 26, 28, 31, 32, 34, 38, 40, 43:
 * סייר קבצים מתקדם עם תפריט המבורגר מרכזי, Breadcrumbs אינטראקטיביים, בחירה מרובה,
 * דחיסה רב-פורמטית (+סיסמה), שינוי שם אצווה, שיתוף מרובה, ניהול אפליקציות מותקנות,
 * מצב קבצים חדשים עם ניקוי, פתיחה באמצעות, ושחזור גיבויי .bak.
 */
public class FileBrowserActivity extends AppCompatActivity {

    public static final String EXTRA_MODE = "extra_mode";
    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_INITIAL_DIR = "extra_initial_dir";

    public static final String MODE_INTERNAL = "INTERNAL";
    public static final String MODE_SD_CARD = "SD_CARD";
    public static final String MODE_USB_OTG = "USB_OTG";
    public static final String MODE_DOWNLOADS = "DOWNLOADS";
    public static final String MODE_IMAGES = "IMAGES";
    public static final String MODE_AUDIO = "AUDIO";
    public static final String MODE_VIDEOS = "VIDEOS";
    public static final String MODE_DOCUMENTS = "DOCUMENTS";
    public static final String MODE_APPS = "APPS";
    public static final String MODE_RECENT = "RECENT";
    public static final String MODE_RECYCLE_BIN = "RECYCLE_BIN";

    // לוח עריכה פנימי להעברה והעתקה בין תיקיות (תומך בקובץ בודד או מרובה)
    private static final List<File> clipboardFiles = new ArrayList<>();
    private static boolean clipboardIsMove = false;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<File> allLoadedFiles = new ArrayList<>();
    private final List<File> displayedFiles = new ArrayList<>();
    private final Set<String> selectedPaths = new HashSet<>();

    // סעיף 15: לשונית אפליקציות מותקנות
    private static class InstalledAppEntry {
        String appName;
        String packageName;
        String versionName;
        long sizeBytes;
        File sourceApk;
        boolean isSystem;
    }

    private final List<InstalledAppEntry> allInstalledApps = new ArrayList<>();
    private final List<InstalledAppEntry> displayedInstalledApps = new ArrayList<>();
    private boolean showingInstalledAppsTab = false;
    private boolean includeSystemApps = false;

    private String currentMode = MODE_INTERNAL;
    private String baseCategoryTitle = "סייר קבצים";
    private File currentDir = null;
    private File rootBoundary = null;
    private File selectedCategoryFolder = null;
    private final Map<String, Integer> categoryFolderCounts = new ConcurrentHashMap<>();
    private final Map<String, Integer> dirItemCountCache = new ConcurrentHashMap<>();
    private final Set<String> pendingDirCountPaths = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> cachedSizesByPath = new ConcurrentHashMap<>();
    private final Map<String, Long> cachedModifiedByPath = new ConcurrentHashMap<>();
    private Boolean appliedGridLayout = null;
    private int appliedSpanCount = -1;

    private boolean multiSelectMode = false;
    private boolean searchBarOpen = false;

    private TextView tvHeaderTitle;
    private HorizontalScrollView breadcrumbScroll;
    private LinearLayout breadcrumbContainer;
    private LinearLayout searchContainer;
    private EditText etSearch;
    private LinearLayout appsTabBar;
    private Button btnTabApkFiles;
    private Button btnTabInstalledApps;
    private Button btnToggleSystemApps;
    private LinearLayout clipboardBar;
    private TextView tvClipboardStatus;
    private LinearLayout multiSelectBar;
    private TextView tvMultiCount;
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView recyclerView;
    private ProgressBar loadingSpinner;
    private TextView tvEmptyState;
    private FileAdapter adapter;
    private InstalledAppsAdapter installedAdapter;
    private AudioPlayerManager.PlaybackListener miniAudioListener;

    private FrameLayout rightFastScrollContainer;
    private View rightFastScrollThumb;
    private boolean isDraggingFastScroll = false;

    private int theme;
    private int bgColor;
    private int cardColor;
    private int textPrimary;
    private int textSecondary;
    private int accent;
    private int divider;

    private int dp(float val) {
        return Math.round(val * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        currentMode = getIntent().getStringExtra(EXTRA_MODE);
        if (currentMode == null) currentMode = MODE_INTERNAL;
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        if (title == null) title = "סייר קבצים";
        baseCategoryTitle = title;

        theme = PrefsManager.getThemeMode(this);
        ThemeHelper.applyWindowStatusBar(this, theme);
        bgColor = ThemeHelper.getBgColor(this, theme);
        cardColor = ThemeHelper.getCardColor(this, theme);
        textPrimary = ThemeHelper.getPrimaryTextColor(this, theme);
        textSecondary = ThemeHelper.getSecondaryTextColor(this, theme);
        accent = ThemeHelper.getAccentColor(this, theme);
        divider = ThemeHelper.getDividerColor(this, theme);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(bgColor);

        // סרגל כותרת עליון מינימליסטי עם המבורגר (סעיף 4)
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(ThemeHelper.getTopBarColor(this, theme));
        topBar.setPadding(dp(8), dp(8), dp(8), dp(8));

        ImageButton btnBack = new ImageButton(this);
        btnBack.setImageResource(R.drawable.ic_arrow_back);
        btnBack.setColorFilter(accent);
        btnBack.setBackgroundColor(Color.TRANSPARENT);
        btnBack.setContentDescription("חזרה");
        btnBack.setPadding(dp(8), dp(8), dp(8), dp(8));
        btnBack.setOnClickListener(v -> navigateUpOrFinish());
        topBar.addView(btnBack, new LinearLayout.LayoutParams(dp(40), dp(40)));

        tvHeaderTitle = new TextView(this);
        tvHeaderTitle.setText(title);
        tvHeaderTitle.setTextSize(ThemeHelper.spScaled(this, 16.5f));
        tvHeaderTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvHeaderTitle.setTextColor(textPrimary);
        tvHeaderTitle.setMaxLines(1);
        tvHeaderTitle.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleLp.setMarginStart(dp(4));
        titleLp.setMarginEnd(dp(4));
        topBar.addView(tvHeaderTitle, titleLp);

        ImageButton btnQuickSearch = new ImageButton(this);
        btnQuickSearch.setImageResource(R.drawable.ic_search);
        btnQuickSearch.setColorFilter(textSecondary);
        btnQuickSearch.setBackgroundColor(Color.TRANSPARENT);
        btnQuickSearch.setContentDescription("חיפוש");
        btnQuickSearch.setPadding(dp(8), dp(8), dp(8), dp(8));
        btnQuickSearch.setOnClickListener(v -> toggleSearchBar(!searchBarOpen));
        topBar.addView(btnQuickSearch, new LinearLayout.LayoutParams(dp(38), dp(38)));

        ImageButton btnHamburger = new ImageButton(this);
        btnHamburger.setImageResource(R.drawable.ic_menu_hamburger);
        btnHamburger.setColorFilter(accent);
        btnHamburger.setBackgroundColor(Color.TRANSPARENT);
        btnHamburger.setContentDescription("תפריט");
        btnHamburger.setPadding(dp(8), dp(8), dp(8), dp(8));
        btnHamburger.setOnClickListener(v -> showHamburgerMenu());
        topBar.addView(btnHamburger, new LinearLayout.LayoutParams(dp(40), dp(40)));

        root.addView(topBar);

        View topDiv = new View(this);
        topDiv.setBackgroundColor(divider);
        root.addView(topDiv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1))));

        // סרגל Breadcrumbs אינטראקטיבי לחיץ (סעיף 38)
        breadcrumbScroll = new HorizontalScrollView(this);
        breadcrumbScroll.setHorizontalScrollBarEnabled(false);
        breadcrumbScroll.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        breadcrumbScroll.setPadding(dp(12), dp(5), dp(12), dp(5));
        breadcrumbContainer = new LinearLayout(this);
        breadcrumbContainer.setOrientation(LinearLayout.HORIZONTAL);
        breadcrumbContainer.setGravity(Gravity.CENTER_VERTICAL);
        breadcrumbScroll.addView(breadcrumbContainer);
        root.addView(breadcrumbScroll);

        // תיבת חיפוש נפתחת לפי בקשה מהמבורגר או מאייקון חיפוש (סעיף 4, 7)
        searchContainer = new LinearLayout(this);
        searchContainer.setOrientation(LinearLayout.HORIZONTAL);
        searchContainer.setGravity(Gravity.CENTER_VERTICAL);
        searchContainer.setPadding(dp(10), dp(4), dp(10), dp(6));
        searchContainer.setVisibility(View.GONE);

        etSearch = new EditText(this);
        etSearch.setHint("חפש קובץ, תיקייה או אפליקציה...");
        etSearch.setTextColor(textPrimary);
        etSearch.setHintTextColor(textSecondary);
        etSearch.setTextSize(ThemeHelper.spScaled(this, 13.5f));
        GradientDrawable searchBg = new GradientDrawable();
        searchBg.setCornerRadius(dp(10));
        searchBg.setColor(cardColor);
        searchBg.setStroke(Math.max(1, dp(1)), divider);
        etSearch.setBackground(searchBg);
        etSearch.setPadding(dp(12), dp(8), dp(12), dp(8));
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterCurrentList(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        searchContainer.addView(etSearch, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button btnCloseSearch = new Button(this);
        btnCloseSearch.setText("✕");
        btnCloseSearch.setTextColor(textSecondary);
        btnCloseSearch.setBackgroundColor(Color.TRANSPARENT);
        btnCloseSearch.setMinWidth(0);
        btnCloseSearch.setMinimumWidth(0);
        btnCloseSearch.setOnClickListener(v -> {
            etSearch.setText("");
            toggleSearchBar(false);
        });
        searchContainer.addView(btnCloseSearch);
        root.addView(searchContainer);

        // לשוניות אפליקציות (סעיף 15) — מוצג רק ב-MODE_APPS
        appsTabBar = new LinearLayout(this);
        appsTabBar.setOrientation(LinearLayout.HORIZONTAL);
        appsTabBar.setGravity(Gravity.CENTER_VERTICAL);
        appsTabBar.setPadding(dp(10), dp(4), dp(10), dp(6));
        appsTabBar.setVisibility(MODE_APPS.equals(currentMode) ? View.VISIBLE : View.GONE);

        btnTabApkFiles = createTabChip("קובצי APK במכשיר", true);
        btnTabApkFiles.setOnClickListener(v -> {
            showingInstalledAppsTab = false;
            refreshAppsTabUi();
            loadFilesForCurrentMode(false);
        });
        appsTabBar.addView(btnTabApkFiles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        btnTabInstalledApps = createTabChip("אפליקציות מותקנות", false);
        btnTabInstalledApps.setOnClickListener(v -> {
            showingInstalledAppsTab = true;
            refreshAppsTabUi();
            loadInstalledAppsAsync();
        });
        LinearLayout.LayoutParams itLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        itLp.setMarginStart(dp(6));
        appsTabBar.addView(btnTabInstalledApps, itLp);

        btnToggleSystemApps = createTabChip("מערכת", false);
        btnToggleSystemApps.setVisibility(View.GONE);
        btnToggleSystemApps.setOnClickListener(v -> {
            includeSystemApps = !includeSystemApps;
            refreshAppsTabUi();
            filterCurrentList(etSearch.getText().toString());
        });
        LinearLayout.LayoutParams sysLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sysLp.setMarginStart(dp(6));
        appsTabBar.addView(btnToggleSystemApps, sysLp);
        root.addView(appsTabBar);

        // סרגל הדבקה
        clipboardBar = new LinearLayout(this);
        clipboardBar.setOrientation(LinearLayout.HORIZONTAL);
        clipboardBar.setGravity(Gravity.CENTER_VERTICAL);
        clipboardBar.setBackgroundColor(accent);
        clipboardBar.setPadding(dp(12), dp(6), dp(12), dp(6));
        clipboardBar.setVisibility(View.GONE);

        tvClipboardStatus = new TextView(this);
        tvClipboardStatus.setTextColor(Color.WHITE);
        tvClipboardStatus.setTextSize(12.5f);
        clipboardBar.addView(tvClipboardStatus, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button btnPasteHere = new Button(this);
        btnPasteHere.setText("הדבק כאן");
        btnPasteHere.setAllCaps(false);
        btnPasteHere.setTextColor(Color.WHITE);
        btnPasteHere.setTypeface(Typeface.DEFAULT_BOLD);
        btnPasteHere.setBackgroundColor(Color.TRANSPARENT);
        btnPasteHere.setOnClickListener(v -> executePasteHere());
        clipboardBar.addView(btnPasteHere);

        Button btnCancelClip = new Button(this);
        btnCancelClip.setText("ביטול");
        btnCancelClip.setAllCaps(false);
        btnCancelClip.setTextColor(Color.WHITE);
        btnCancelClip.setBackgroundColor(Color.TRANSPARENT);
        btnCancelClip.setOnClickListener(v -> {
            clipboardFiles.clear();
            updateClipboardBar();
        });
        clipboardBar.addView(btnCancelClip);
        root.addView(clipboardBar);

        // מסגרת רשימה + פס הרצה מהיר בצד ימין + חיווי טעינה (סעיף 18)
        FrameLayout listStageFrame = new FrameLayout(this);
        listStageFrame.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

        swipeRefresh = new SwipeRefreshLayout(this);
        swipeRefresh.setColorSchemeColors(accent, Color.parseColor("#0284C7"));
        swipeRefresh.setOnRefreshListener(() -> {
            if (MODE_APPS.equals(currentMode) && showingInstalledAppsTab) {
                loadInstalledAppsAsync();
            } else {
                loadFilesForCurrentMode(true);
            }
        });

        FrameLayout innerSwipeContent = new FrameLayout(this);

        tvEmptyState = new TextView(this);
        tvEmptyState.setText("לא נמצאו פריטים להצגה");
        tvEmptyState.setTextColor(textSecondary);
        tvEmptyState.setTextSize(ThemeHelper.spScaled(this, 14f));
        tvEmptyState.setGravity(Gravity.CENTER);
        tvEmptyState.setPadding(dp(20), dp(44), dp(20), dp(20));
        tvEmptyState.setVisibility(View.GONE);
        innerSwipeContent.addView(tvEmptyState, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL));

        loadingSpinner = new ProgressBar(this);
        loadingSpinner.setIndeterminateTintList(ColorStateList.valueOf(accent));
        loadingSpinner.setVisibility(View.GONE);
        innerSwipeContent.addView(loadingSpinner, new FrameLayout.LayoutParams(
                dp(36), dp(36), Gravity.CENTER));

        recyclerView = new RecyclerView(this);
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemViewCacheSize(24);
        recyclerView.setClipToPadding(false);
        recyclerView.setPadding(dp(5), dp(3), dp(20), dp(12));
        applyFolderLayoutMode();
        adapter = new FileAdapter();
        installedAdapter = new InstalledAppsAdapter();
        recyclerView.setAdapter(adapter);
        innerSwipeContent.addView(recyclerView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        swipeRefresh.setOnChildScrollUpCallback((parent, child) ->
                recyclerView != null && recyclerView.canScrollVertically(-1));
        swipeRefresh.addView(innerSwipeContent, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        listStageFrame.addView(swipeRefresh, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        buildRightFastScrollerOverlay(listStageFrame);

        root.addView(listStageFrame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // סרגל בחירה מרובה תחתון (סעיף 5, 43)
        multiSelectBar = buildMultiSelectBar();
        multiSelectBar.setVisibility(View.GONE);
        root.addView(multiSelectBar);

        miniAudioListener = MiniAudioBarHelper.attach(this, root);

        setContentView(root);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (multiSelectMode) {
                    exitMultiSelectMode();
                    return;
                }
                navigateUpOrFinish();
            }
        });

        initInitialDirectory();
        updateClipboardBar();
        loadFilesForCurrentMode(false);
    }

    private Button createTabChip(String text, boolean active) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(ThemeHelper.spScaled(this, 12f));
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(8), dp(7), dp(8), dp(7));
        styleTabChip(b, active);
        return b;
    }

    private void styleTabChip(Button b, boolean active) {
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(dp(10));
        gd.setColor(active ? accent : cardColor);
        gd.setStroke(Math.max(1, dp(1)), active ? accent : divider);
        b.setBackground(gd);
        b.setTextColor(active ? (theme == PrefsManager.THEME_NEON ? Color.parseColor("#04101A") : Color.WHITE) : textPrimary);
    }

    private void refreshAppsTabUi() {
        styleTabChip(btnTabApkFiles, !showingInstalledAppsTab);
        styleTabChip(btnTabInstalledApps, showingInstalledAppsTab);
        btnToggleSystemApps.setVisibility(showingInstalledAppsTab ? View.VISIBLE : View.GONE);
        styleTabChip(btnToggleSystemApps, includeSystemApps);
        recyclerView.setAdapter(showingInstalledAppsTab ? installedAdapter : adapter);
        if (showingInstalledAppsTab) {
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
        } else {
            applyFolderLayoutMode();
        }
    }

    private void toggleSearchBar(boolean open) {
        searchBarOpen = open;
        searchContainer.setVisibility(open ? View.VISIBLE : View.GONE);
        if (open) {
            etSearch.requestFocus();
        }
    }

    private LinearLayout buildMultiSelectBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setBackgroundColor(ThemeHelper.getTopBarColor(this, theme));
        bar.setPadding(dp(10), dp(8), dp(10), dp(8));

        LinearLayout topInfo = new LinearLayout(this);
        topInfo.setOrientation(LinearLayout.HORIZONTAL);
        topInfo.setGravity(Gravity.CENTER_VERTICAL);

        tvMultiCount = new TextView(this);
        tvMultiCount.setTextColor(textPrimary);
        tvMultiCount.setTypeface(Typeface.DEFAULT_BOLD);
        tvMultiCount.setTextSize(13f);
        topInfo.addView(tvMultiCount, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button btnSelectAll = new Button(this);
        btnSelectAll.setText("בחר הכל");
        btnSelectAll.setTextColor(accent);
        btnSelectAll.setBackgroundColor(Color.TRANSPARENT);
        btnSelectAll.setMinHeight(0);
        btnSelectAll.setMinimumHeight(0);
        btnSelectAll.setOnClickListener(v -> {
            if (selectedPaths.size() == displayedFiles.size()) {
                selectedPaths.clear();
            } else {
                for (File f : displayedFiles) selectedPaths.add(f.getAbsolutePath());
            }
            updateMultiSelectUi();
        });
        topInfo.addView(btnSelectAll);

        Button btnCancel = new Button(this);
        btnCancel.setText("ביטול");
        btnCancel.setTextColor(textSecondary);
        btnCancel.setBackgroundColor(Color.TRANSPARENT);
        btnCancel.setMinHeight(0);
        btnCancel.setMinimumHeight(0);
        btnCancel.setOnClickListener(v -> exitMultiSelectMode());
        topInfo.addView(btnCancel);
        bar.addView(topInfo);

        HorizontalScrollView actionsScroll = new HorizontalScrollView(this);
        actionsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout actionsRow = new LinearLayout(this);
        actionsRow.setOrientation(LinearLayout.HORIZONTAL);

        actionsRow.addView(createMultiActionChip("העתק", R.drawable.ic_copy, v -> multiCopy(false)));
        actionsRow.addView(createMultiActionChip("העבר", R.drawable.ic_cut, v -> multiCopy(true)));
        actionsRow.addView(createMultiActionChip("שתף", R.drawable.ic_share, v -> multiShare()));
        actionsRow.addView(createMultiActionChip("דחיסה", R.drawable.ic_zip_archive, v -> promptMultiFormatCompress(getSelectedFilesList())));
        actionsRow.addView(createMultiActionChip("שינוי שם אצווה", R.drawable.ic_edit_pencil, v -> promptBatchRename(getSelectedFilesList())));
        actionsRow.addView(createMultiActionChip("מחק", R.drawable.ic_delete, v -> multiDelete()));

        actionsScroll.addView(actionsRow);
        bar.addView(actionsScroll);
        return bar;
    }

    private Button createMultiActionChip(String label, int iconRes, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(11.5f);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(textPrimary);
        b.setCompoundDrawablesRelativeWithIntrinsicBounds(iconRes, 0, 0, 0);
        b.setCompoundDrawablePadding(dp(4));
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(dp(9));
        gd.setColor(cardColor);
        gd.setStroke(Math.max(1, dp(1)), divider);
        b.setBackground(gd);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(10), dp(6), dp(10), dp(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dp(6));
        b.setLayoutParams(lp);
        b.setOnClickListener(l);
        return b;
    }

    private List<File> getSelectedFilesList() {
        List<File> out = new ArrayList<>();
        for (File f : displayedFiles) {
            if (selectedPaths.contains(f.getAbsolutePath())) out.add(f);
        }
        return out;
    }

    private void enterMultiSelectMode(File initial) {
        multiSelectMode = true;
        selectedPaths.clear();
        if (initial != null) selectedPaths.add(initial.getAbsolutePath());
        updateMultiSelectUi();
    }

    private void exitMultiSelectMode() {
        multiSelectMode = false;
        selectedPaths.clear();
        multiSelectBar.setVisibility(View.GONE);
        adapter.notifyDataSetChanged();
    }

    private void updateMultiSelectUi() {
        if (!multiSelectMode) {
            multiSelectBar.setVisibility(View.GONE);
            return;
        }
        multiSelectBar.setVisibility(View.VISIBLE);
        tvMultiCount.setText("נבחרו " + selectedPaths.size() + " פריטים");
        adapter.notifyDataSetChanged();
    }

    // ==================== תפריט המבורגר מרכזי (סעיפים 4, 5, 26, 31) ====================
    private void showHamburgerMenu() {
        if (showingInstalledAppsTab) {
            ThemedDialogHelper.showThemedMenu(
                    this,
                    "אפשרויות אפליקציות מותקנות",
                    null,
                    Arrays.asList(
                            "חיפוש אפליקציה",
                            includeSystemApps ? "הסתר אפליקציות מערכת" : "הצג גם אפליקציות מערכת",
                            "רענן רשימה"
                    ),
                    idx -> {
                        if (idx == 0) toggleSearchBar(true);
                        else if (idx == 1) {
                            includeSystemApps = !includeSystemApps;
                            refreshAppsTabUi();
                            filterCurrentList(etSearch.getText().toString());
                        } else {
                            loadInstalledAppsAsync();
                        }
                    }
            );
            return;
        }

        final List<String> opts = new ArrayList<>();
        opts.add("חיפוש קבצים");
        opts.add("מיון לפי (" + currentSortNameHe() + ")");
        boolean grid = PrefsManager.isGridLayoutForMode(this, currentMode);
        opts.add(grid ? "מעבר לתצוגת רשימה" : "מעבר לתצוגת רשת");

        if (isSupportsLinearOrFolderToggle()) {
            boolean linear = isEffectiveLinearCategoryView();
            opts.add(linear ? "תצוגה מקובצת לפי תיקיות" : "תצוגה ליניארית (כל הקבצים)");
        }
        opts.add(multiSelectMode ? "סיום בחירה מרובה" : "בחירה מרובה (העתקה / שיתוף / דחיסה / מחיקה)");

        if (isDirectoryMode()) {
            opts.add("תיקייה חדשה");
            opts.add("קובץ חדש (עורך קוד וטקסט)");
            if (!clipboardFiles.isEmpty()) {
                opts.add("הדבק לכאן (" + clipboardFiles.size() + " פריטים)");
            }
        }
        if (MODE_RECENT.equals(currentMode)) {
            opts.add("ניקוי רשימת קבצים חדשים");
        }
        if (MODE_RECYCLE_BIN.equals(currentMode)) {
            opts.add("רוקן את כל סל המיחזור");
        }

        ThemedDialogHelper.showThemedMenu(
                this,
                "אפשרויות תיקייה",
                currentDir != null ? currentDir.getName() : baseCategoryTitle,
                opts,
                which -> {
                    String chosen = opts.get(which);
                    if (chosen.startsWith("חיפוש")) {
                        toggleSearchBar(true);
                    } else if (chosen.startsWith("מיון")) {
                        showQuickSortMenu();
                    } else if (chosen.startsWith("מעבר לתצוגת")) {
                        PrefsManager.setGridLayoutForMode(this, currentMode, !grid);
                        applyFolderLayoutMode();
                        adapter.notifyDataSetChanged();
                        recyclerView.post(this::syncRightFastScrollerFromRecycler);
                    } else if (chosen.startsWith("תצוגה מקובצת") || chosen.startsWith("תצוגה ליניארית")) {
                        boolean nextLinear = !isEffectiveLinearCategoryView();
                        PrefsManager.setCategoryLinearView(this, currentMode, nextLinear);
                        selectedCategoryFolder = null;
                        loadFilesForCurrentMode(false);
                    } else if (chosen.startsWith("בחירה מרובה")) {
                        enterMultiSelectMode(null);
                    } else if (chosen.startsWith("סיום בחירה")) {
                        exitMultiSelectMode();
                    } else if (chosen.equals("תיקייה חדשה")) {
                        promptCreateFolder();
                    } else if (chosen.startsWith("קובץ חדש")) {
                        promptCreateNewFile();
                    } else if (chosen.startsWith("הדבק לכאן")) {
                        executePasteHere();
                    } else if (chosen.startsWith("ניקוי רשימת")) {
                        PrefsManager.setRecentClearedTimestamp(this, System.currentTimeMillis());
                        FileScanCache.invalidate();
                        loadFilesForCurrentMode(true);
                        Toast.makeText(this, "הרשימה נוקתה", Toast.LENGTH_SHORT).show();
                    } else if (chosen.startsWith("רוקן את כל")) {
                        confirmEmptyRecycleBinTwoStep();
                    }
                }
        );
    }

    private String currentSortNameHe() {
        int s = PrefsManager.getSortMode(this);
        if (s == PrefsManager.SORT_SIZE) return "גודל";
        if (s == PrefsManager.SORT_DATE) return "שינוי";
        if (s == PrefsManager.SORT_CREATED) return "יצירה";
        if (s == PrefsManager.SORT_TYPE) return "סוג";
        return "שם";
    }

    private void showQuickSortMenu() {
        final String[] labels = {
                "לפי שם (עברית תחילה)",
                "לפי תאריך יצירה",
                "לפי תאריך שינוי",
                "לפי גודל",
                "לפי סוג קובץ",
                PrefsManager.isSortAscending(this) ? "החלף לסדר יורד" : "החלף לסדר עולה"
        };
        final int[] vals = {
                PrefsManager.SORT_NAME,
                PrefsManager.SORT_CREATED,
                PrefsManager.SORT_DATE,
                PrefsManager.SORT_SIZE,
                PrefsManager.SORT_TYPE
        };
        ThemedDialogHelper.showThemedMenu(
                this,
                "מיון פריטים",
                "בחר שדה מיון או היפוך סדר:",
                Arrays.asList(labels),
                which -> {
                    if (which == labels.length - 1) {
                        PrefsManager.setSortAscending(this, !PrefsManager.isSortAscending(this));
                    } else {
                        PrefsManager.setSortMode(this, vals[which]);
                    }
                    loadFilesForCurrentMode(false);
                }
        );
    }

    // ==================== פס הרצה בצד ימין ====================
    private void buildRightFastScrollerOverlay(FrameLayout hostFrame) {
        rightFastScrollContainer = new FrameLayout(this);
        rightFastScrollContainer.setPadding(dp(4), dp(8), dp(3), dp(8));
        rightFastScrollContainer.setVisibility(View.GONE);

        View trackLine = new View(this);
        GradientDrawable trackBg = new GradientDrawable();
        trackBg.setCornerRadius(dp(3));
        trackBg.setColor(Color.argb(40, Color.red(accent), Color.green(accent), Color.blue(accent)));
        trackLine.setBackground(trackBg);
        rightFastScrollContainer.addView(trackLine,
                new FrameLayout.LayoutParams(dp(4), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER_HORIZONTAL));

        rightFastScrollThumb = new View(this);
        GradientDrawable thumbBg = new GradientDrawable();
        thumbBg.setCornerRadius(dp(6));
        thumbBg.setColor(accent);
        rightFastScrollThumb.setBackground(thumbBg);
        rightFastScrollContainer.addView(rightFastScrollThumb,
                new FrameLayout.LayoutParams(dp(8), dp(48), Gravity.TOP | Gravity.CENTER_HORIZONTAL));

        hostFrame.addView(rightFastScrollContainer,
                new FrameLayout.LayoutParams(dp(22), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.RIGHT));

        rightFastScrollContainer.setOnTouchListener((v, event) -> {
            int count = showingInstalledAppsTab ? displayedInstalledApps.size() : displayedFiles.size();
            if (count == 0 || recyclerView == null) return false;
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                isDraggingFastScroll = true;
                if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true);
                rightFastScrollThumb.setScaleX(1.25f);
                int topPad = rightFastScrollContainer.getPaddingTop();
                int botPad = rightFastScrollContainer.getPaddingBottom();
                int thumbH = rightFastScrollThumb.getHeight() > 0 ? rightFastScrollThumb.getHeight() : dp(48);
                int trackH = Math.max(1, rightFastScrollContainer.getHeight() - topPad - botPad - thumbH);
                float clampedY = Math.max(0f, Math.min(trackH, event.getY() - topPad - (thumbH * 0.5f)));
                float fraction = clampedY / (float) trackH;
                rightFastScrollThumb.setTranslationY(clampedY);

                int targetPos = Math.max(0, Math.min(count - 1, Math.round(fraction * (count - 1))));
                RecyclerView.LayoutManager lm = recyclerView.getLayoutManager();
                if (lm instanceof LinearLayoutManager) {
                    ((LinearLayoutManager) lm).scrollToPositionWithOffset(targetPos, 0);
                } else {
                    recyclerView.scrollToPosition(targetPos);
                }
                return true;
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                isDraggingFastScroll = false;
                rightFastScrollThumb.setScaleX(1.0f);
                syncRightFastScrollerFromRecycler();
                return true;
            }
            return false;
        });

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (!isDraggingFastScroll) syncRightFastScrollerFromRecycler();
            }
        });
    }

    private void syncRightFastScrollerFromRecycler() {
        if (rightFastScrollContainer == null || rightFastScrollThumb == null || recyclerView == null) return;
        int count = showingInstalledAppsTab ? displayedInstalledApps.size() : displayedFiles.size();
        if (count == 0) {
            rightFastScrollContainer.setVisibility(View.GONE);
            return;
        }
        int range = recyclerView.computeVerticalScrollRange();
        int extent = recyclerView.computeVerticalScrollExtent();
        int offset = recyclerView.computeVerticalScrollOffset();
        if (range <= extent + dp(10) && count <= 6) {
            rightFastScrollContainer.setVisibility(View.GONE);
            return;
        }
        rightFastScrollContainer.setVisibility(View.VISIBLE);
        int topPad = rightFastScrollContainer.getPaddingTop();
        int botPad = rightFastScrollContainer.getPaddingBottom();
        int thumbH = rightFastScrollThumb.getHeight() > 0 ? rightFastScrollThumb.getHeight() : dp(48);
        int trackH = Math.max(1, rightFastScrollContainer.getHeight() - topPad - botPad - thumbH);
        int maxScroll = Math.max(1, range - extent);
        float fraction = Math.max(0f, Math.min(1f, (float) offset / (float) maxScroll));
        rightFastScrollThumb.setTranslationY(fraction * trackH);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (recyclerView != null && !showingInstalledAppsTab) {
            applyFolderLayoutMode();
            adapter.notifyDataSetChanged();
            recyclerView.post(this::syncRightFastScrollerFromRecycler);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null) {
            setIntent(intent);
            String newMode = intent.getStringExtra(EXTRA_MODE);
            if (newMode != null) currentMode = newMode;
            String newTitle = intent.getStringExtra(EXTRA_TITLE);
            if (newTitle != null) {
                baseCategoryTitle = newTitle;
                tvHeaderTitle.setText(newTitle);
            }
            selectedCategoryFolder = null;
            showingInstalledAppsTab = false;
            appsTabBar.setVisibility(MODE_APPS.equals(currentMode) ? View.VISIBLE : View.GONE);
            initInitialDirectory();
            updateClipboardBar();
            applyFolderLayoutMode();
            loadFilesForCurrentMode(false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (miniAudioListener != null) miniAudioListener.onPlaybackStateChanged();
        if (recyclerView != null && !showingInstalledAppsTab) {
            applyFolderLayoutMode();
            if (allLoadedFiles.isEmpty() || FileScanCache.isDirty()) {
                loadFilesForCurrentMode(false);
            }
        }
    }

    private int computeResponsiveGridColumns() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float widthDp = dm.widthPixels / dm.density;
        if (widthDp < 280f) return 2;
        if (widthDp >= 780f) return 5;
        if (widthDp >= 530f) return 4;
        return 3;
    }

    private void applyFolderLayoutMode() {
        boolean wantGrid = PrefsManager.isGridLayoutForMode(this, currentMode);
        int wantSpan = wantGrid ? computeResponsiveGridColumns() : 1;
        if (appliedGridLayout != null && appliedGridLayout == wantGrid && appliedSpanCount == wantSpan
                && recyclerView.getLayoutManager() != null) {
            return;
        }
        appliedGridLayout = wantGrid;
        appliedSpanCount = wantSpan;
        if (wantGrid) {
            recyclerView.setLayoutManager(new GridLayoutManager(this, wantSpan));
        } else {
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
        }
    }

    private boolean isDirectoryMode() {
        return MODE_INTERNAL.equals(currentMode)
                || MODE_SD_CARD.equals(currentMode)
                || MODE_USB_OTG.equals(currentMode)
                || MODE_DOWNLOADS.equals(currentMode);
    }

    private boolean isSupportsLinearOrFolderToggle() {
        return MODE_IMAGES.equals(currentMode)
                || MODE_VIDEOS.equals(currentMode)
                || MODE_AUDIO.equals(currentMode)
                || MODE_DOCUMENTS.equals(currentMode);
    }

    private boolean isEffectiveLinearCategoryView() {
        if (MODE_IMAGES.equals(currentMode) || MODE_VIDEOS.equals(currentMode)) {
            return PrefsManager.isCategoryLinearView(this, currentMode);
        }
        return !PrefsManager.isCategoryLinearView(this, currentMode);
    }

    private boolean isGroupedMediaCategoryMode() {
        return (MODE_IMAGES.equals(currentMode) || MODE_VIDEOS.equals(currentMode))
                && !isEffectiveLinearCategoryView();
    }

    private void initInitialDirectory() {
        String initialDirExtra = getIntent().getStringExtra(EXTRA_INITIAL_DIR);
        if (initialDirExtra != null && !initialDirExtra.isEmpty()) {
            File target = new File(initialDirExtra);
            if (target.exists() && target.isDirectory()) {
                StorageUtils.StorageStat sd = StorageUtils.getExternalSdCardStat(this);
                List<StorageUtils.StorageStat> otgs = StorageUtils.getConnectedUsbOtgVolumes(this);
                boolean matchedOtg = false;
                for (StorageUtils.StorageStat otg : otgs) {
                    if (otg.root != null && target.getAbsolutePath().startsWith(otg.root.getAbsolutePath())) {
                        currentMode = MODE_USB_OTG;
                        rootBoundary = otg.root;
                        matchedOtg = true;
                        break;
                    }
                }
                if (!matchedOtg) {
                    if (sd.available && sd.root != null && target.getAbsolutePath().startsWith(sd.root.getAbsolutePath())) {
                        currentMode = MODE_SD_CARD;
                        rootBoundary = sd.root;
                    } else {
                        currentMode = MODE_INTERNAL;
                        rootBoundary = Environment.getExternalStorageDirectory();
                    }
                }
                currentDir = target;
                tvHeaderTitle.setText(target.equals(rootBoundary) ? baseCategoryTitle : target.getName());
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
        } else if (MODE_USB_OTG.equals(currentMode)) {
            List<StorageUtils.StorageStat> otgs = StorageUtils.getConnectedUsbOtgVolumes(this);
            rootBoundary = otgs.isEmpty() ? null : otgs.get(0).root;
            currentDir = rootBoundary;
        } else if (MODE_DOWNLOADS.equals(currentMode)) {
            rootBoundary = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            currentDir = rootBoundary;
        } else if (MODE_RECYCLE_BIN.equals(currentMode)) {
            rootBoundary = RecycleBinManager.getTrashDir(this);
            currentDir = rootBoundary;
        }
    }

    private void renderInteractiveBreadcrumbs() {
        breadcrumbContainer.removeAllViews();
        File active = null;
        if (isDirectoryMode() && currentDir != null) {
            active = currentDir;
        } else if (isGroupedMediaCategoryMode() && selectedCategoryFolder != null) {
            active = selectedCategoryFolder;
        }
        if (active == null) {
            breadcrumbScroll.setVisibility(View.GONE);
            return;
        }
        breadcrumbScroll.setVisibility(View.VISIBLE);

        // שמירה לגישה מהירה במסך הבית (סעיף 38)
        if (isDirectoryMode()) {
            PrefsManager.setLastVisitedPath(this, active.getAbsolutePath());
            PrefsManager.setLastVisitedMode(this, currentMode);
        }

        List<File> chain = new ArrayList<>();
        File cursor = active;
        String boundaryPath = rootBoundary != null ? rootBoundary.getAbsolutePath() : "/";
        while (cursor != null) {
            chain.add(0, cursor);
            if (cursor.getAbsolutePath().equals(boundaryPath)) break;
            cursor = cursor.getParentFile();
        }

        for (int i = 0; i < chain.size(); i++) {
            final File seg = chain.get(i);
            final boolean isLast = (i == chain.size() - 1);
            TextView tv = new TextView(this);
            String label = (i == 0 && rootBoundary != null && seg.equals(rootBoundary))
                    ? baseCategoryTitle : seg.getName();
            tv.setText(label);
            tv.setTextSize(12f);
            tv.setTypeface(isLast ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            tv.setTextColor(isLast ? accent : textSecondary);
            tv.setPadding(dp(4), dp(2), dp(4), dp(2));
            if (!isLast && isDirectoryMode()) {
                tv.setOnClickListener(v -> {
                    currentDir = seg;
                    tvHeaderTitle.setText(seg.equals(rootBoundary) ? baseCategoryTitle : seg.getName());
                    loadFilesForCurrentMode(false);
                });
            }
            breadcrumbContainer.addView(tv);
            if (!isLast) {
                TextView sep = new TextView(this);
                sep.setText(" › ");
                sep.setTextColor(textSecondary);
                sep.setTextSize(12f);
                breadcrumbContainer.addView(sep);
            }
        }
        breadcrumbScroll.post(() -> breadcrumbScroll.fullScroll(View.FOCUS_RIGHT));
    }

    private void updateClipboardBar() {
        if (!clipboardFiles.isEmpty() && isDirectoryMode() && currentDir != null) {
            clipboardBar.setVisibility(View.VISIBLE);
            String op = clipboardIsMove ? "העברה: " : "העתקה: ";
            String name = clipboardFiles.size() == 1
                    ? clipboardFiles.get(0).getName()
                    : (clipboardFiles.size() + " פריטים");
            tvClipboardStatus.setText(op + name);
        } else {
            clipboardBar.setVisibility(View.GONE);
        }
    }

    private void loadFilesForCurrentMode() {
        loadFilesForCurrentMode(false);
    }

    private void loadFilesForCurrentMode(final boolean fromUserPullToRefresh) {
        if (showingInstalledAppsTab) {
            loadInstalledAppsAsync();
            return;
        }
        if ((MODE_SD_CARD.equals(currentMode) || MODE_USB_OTG.equals(currentMode)) && currentDir == null) {
            breadcrumbScroll.setVisibility(View.GONE);
            allLoadedFiles.clear();
            filterCurrentList(etSearch.getText().toString());
            tvEmptyState.setText(MODE_USB_OTG.equals(currentMode) ? "לא זוהה כונן USB OTG מחובר" : "כרטיס SD חיצוני אינו מחובר");
            if (fromUserPullToRefresh) swipeRefresh.setRefreshing(false);
            return;
        }

        renderInteractiveBreadcrumbs();

        final boolean showHidden = PrefsManager.isShowHidden(this);
        final File activeMediaFolder = selectedCategoryFolder;
        final boolean isIndexedCat = isIndexedCategoryMode();
        final StorageUtils.CategoryIndexSnapshot quickSnap =
                (!fromUserPullToRefresh && isIndexedCat)
                        ? FileScanCache.peekFreshSnapshot(showHidden)
                        : null;

        loadingSpinner.setVisibility((allLoadedFiles.isEmpty() && quickSnap == null) ? View.VISIBLE : View.GONE);

        executor.execute(() -> {
            List<File> result = new ArrayList<>();
            if (isDirectoryMode() || MODE_RECYCLE_BIN.equals(currentMode)) {
                dirItemCountCache.clear();
                pendingDirCountPaths.clear();
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
            } else if (isGroupedMediaCategoryMode()) {
                if (activeMediaFolder != null && activeMediaFolder.exists() && activeMediaFolder.isDirectory()) {
                    File[] children = activeMediaFolder.listFiles();
                    if (children != null) {
                        for (File f : children) {
                            if (f.isDirectory()) continue;
                            if (!showHidden && f.getName().startsWith(".")) continue;
                            String lower = f.getName().toLowerCase(Locale.ROOT);
                            if (MODE_IMAGES.equals(currentMode) && MediaFormatHelper.isImageFile(lower)) {
                                result.add(f);
                            } else if (MODE_VIDEOS.equals(currentMode) && MediaFormatHelper.isVideoFile(lower)) {
                                result.add(f);
                            }
                        }
                    }
                } else {
                    StorageUtils.CategoryIndexSnapshot snap = quickSnap != null
                            ? quickSnap
                            : FileScanCache.getSnapshot(this, showHidden, fromUserPullToRefresh);
                    cachedSizesByPath.putAll(snap.sizeByPath);
                    cachedModifiedByPath.putAll(snap.modifiedByPath);
                    List<File> allMediaFiles = FileScanCache.extractCategoryFiles(snap, currentMode);
                    Map<String, File> foldersByPath = new LinkedHashMap<>();
                    Map<String, Integer> countsByPath = new LinkedHashMap<>();
                    for (File mf : allMediaFiles) {
                        File parent = mf.getParentFile();
                        if (parent == null) continue;
                        String key = parent.getAbsolutePath();
                        if (!foldersByPath.containsKey(key)) foldersByPath.put(key, parent);
                        Integer prev = countsByPath.get(key);
                        countsByPath.put(key, (prev == null ? 0 : prev) + 1);
                    }
                    categoryFolderCounts.clear();
                    categoryFolderCounts.putAll(countsByPath);
                    result.addAll(foldersByPath.values());
                }
            } else if (isIndexedCat) {
                StorageUtils.CategoryIndexSnapshot snap = quickSnap != null
                        ? quickSnap
                        : FileScanCache.getSnapshot(this, showHidden, fromUserPullToRefresh);
                cachedSizesByPath.putAll(snap.sizeByPath);
                cachedModifiedByPath.putAll(snap.modifiedByPath);
                result.addAll(FileScanCache.extractCategoryFiles(snap, currentMode));
            } else {
                long sevenDaysAgo = System.currentTimeMillis() - (7L * 24L * 3600L * 1000L);
                long recentCutoff = Math.max(sevenDaysAgo, PrefsManager.getRecentClearedTimestamp(this));
                collectMatchingCategoryFiles(Environment.getExternalStorageDirectory(), result, showHidden, recentCutoff, 0);
                StorageUtils.StorageStat sd = StorageUtils.getExternalSdCardStat(this);
                if (sd.available && sd.root != null) {
                    collectMatchingCategoryFiles(sd.root, result, showHidden, recentCutoff, 0);
                }
            }

            sortFileList(result);

            runOnUiThread(() -> {
                loadingSpinner.setVisibility(View.GONE);
                allLoadedFiles.clear();
                allLoadedFiles.addAll(result);
                filterCurrentList(etSearch.getText().toString());
                updateClipboardBar();
                if (fromUserPullToRefresh) {
                    swipeRefresh.setRefreshing(false);
                    Toast.makeText(this, "כל הקבצים רועננו בהצלחה", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private boolean isIndexedCategoryMode() {
        return MODE_IMAGES.equals(currentMode)
                || MODE_AUDIO.equals(currentMode)
                || MODE_VIDEOS.equals(currentMode)
                || MODE_DOCUMENTS.equals(currentMode)
                || MODE_APPS.equals(currentMode)
                || MODE_RECENT.equals(currentMode);
    }

    private void loadInstalledAppsAsync() {
        loadingSpinner.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            List<InstalledAppEntry> entries = new ArrayList<>();
            try {
                PackageManager pm = getPackageManager();
                List<PackageInfo> pkgs = pm.getInstalledPackages(0);
                for (PackageInfo pi : pkgs) {
                    if (pi == null || pi.applicationInfo == null) continue;
                    InstalledAppEntry e = new InstalledAppEntry();
                    e.packageName = pi.packageName;
                    e.versionName = pi.versionName != null ? pi.versionName : "1.0";
                    e.isSystem = (pi.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                    CharSequence lbl = pi.applicationInfo.loadLabel(pm);
                    e.appName = lbl != null ? lbl.toString() : pi.packageName;
                    e.sourceApk = pi.applicationInfo.publicSourceDir != null
                            ? new File(pi.applicationInfo.publicSourceDir) : null;
                    e.sizeBytes = (e.sourceApk != null && e.sourceApk.exists()) ? e.sourceApk.length() : 0L;
                    entries.add(e);
                }
                Collections.sort(entries, (a, b) -> StorageUtils.compareHebrewFirst(a.appName, b.appName));
            } catch (Throwable ignored) {
            }
            runOnUiThread(() -> {
                loadingSpinner.setVisibility(View.GONE);
                allInstalledApps.clear();
                allInstalledApps.addAll(entries);
                filterCurrentList(etSearch.getText().toString());
                swipeRefresh.setRefreshing(false);
            });
        });
    }

    private void collectMatchingCategoryFiles(File dir, List<File> out, boolean showHidden, long recentCutoff, int depth) {
        if (dir == null || !dir.exists() || depth > 7 || out.size() > 5000) return;
        if (".FileManagerPro_Trash".equals(dir.getName()) || "Android".equals(dir.getName())) return;
        if (!showHidden && dir.getName().startsWith(".")) return;

        File[] children = dir.listFiles();
        if (children == null) return;

        for (File f : children) {
            if (!showHidden && f.getName().startsWith(".")) continue;
            if (f.isDirectory()) {
                collectMatchingCategoryFiles(f, out, showHidden, recentCutoff, depth + 1);
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
                } else if (MODE_RECENT.equals(currentMode) && f.lastModified() >= recentCutoff && !name.startsWith(".")) {
                    out.add(f);
                }
            }
        }
    }

    private void sortFileList(List<File> list) {
        if (list.size() <= 1) return;
        final int sortMode = PrefsManager.getSortMode(this);
        final boolean asc = PrefsManager.isSortAscending(this);

        // חישוב מטא-דאטה פעם אחת (O(N)) לפני המיון במקום אלפי קריאות מערכת בתוך ה-Comparator
        final Map<File, Boolean> isDirMap = new HashMap<>(list.size());
        final Map<File, Boolean> hebMap = new HashMap<>(list.size());
        final Map<File, Long> metricMap = (sortMode == PrefsManager.SORT_SIZE
                || sortMode == PrefsManager.SORT_DATE
                || sortMode == PrefsManager.SORT_CREATED)
                ? new HashMap<>(list.size()) : null;

        final boolean onlyFileCategories = isIndexedCategoryMode() && !(isGroupedMediaCategoryMode() && selectedCategoryFolder == null);
        for (File f : list) {
            boolean dir = !onlyFileCategories && f.isDirectory();
            isDirMap.put(f, dir);
            hebMap.put(f, StorageUtils.startsWithHebrew(f.getName()));
            if (metricMap != null) {
                String p = f.getAbsolutePath();
                if (sortMode == PrefsManager.SORT_SIZE) {
                    Long cached = cachedSizesByPath.get(p);
                    metricMap.put(f, cached != null ? cached : f.length());
                } else if (sortMode == PrefsManager.SORT_DATE) {
                    Long cached = cachedModifiedByPath.get(p);
                    metricMap.put(f, cached != null ? cached : f.lastModified());
                } else if (sortMode == PrefsManager.SORT_CREATED) {
                    metricMap.put(f, StorageUtils.getFileCreatedTimeMillis(f));
                }
            }
        }

        Collections.sort(list, (a, b) -> {
            boolean aDir = Boolean.TRUE.equals(isDirMap.get(a));
            boolean bDir = Boolean.TRUE.equals(isDirMap.get(b));
            if (aDir != bDir) {
                return aDir ? -1 : 1;
            }
            if (aDir && bDir) {
                boolean aHeb = Boolean.TRUE.equals(hebMap.get(a));
                boolean bHeb = Boolean.TRUE.equals(hebMap.get(b));
                if (aHeb != bHeb) return aHeb ? -1 : 1;
            }
            int cmp;
            if (metricMap != null) {
                Long va = metricMap.get(a);
                Long vb = metricMap.get(b);
                cmp = Long.compare(va != null ? va : 0L, vb != null ? vb : 0L);
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

    private void filterCurrentList(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (showingInstalledAppsTab) {
            displayedInstalledApps.clear();
            for (InstalledAppEntry e : allInstalledApps) {
                if (!includeSystemApps && e.isSystem) continue;
                if (q.isEmpty() || e.appName.toLowerCase(Locale.ROOT).contains(q)
                        || e.packageName.toLowerCase(Locale.ROOT).contains(q)) {
                    displayedInstalledApps.add(e);
                }
            }
            tvEmptyState.setVisibility(displayedInstalledApps.isEmpty() ? View.VISIBLE : View.GONE);
            installedAdapter.notifyDataSetChanged();
        } else {
            displayedFiles.clear();
            for (File f : allLoadedFiles) {
                if (q.isEmpty() || f.getName().toLowerCase(Locale.ROOT).contains(q)) {
                    displayedFiles.add(f);
                }
            }
            tvEmptyState.setVisibility(displayedFiles.isEmpty() ? View.VISIBLE : View.GONE);
            adapter.notifyDataSetChanged();
        }
        if (recyclerView != null) recyclerView.post(this::syncRightFastScrollerFromRecycler);
    }

    private EditText createStyledInput(String hint, String initialValue) {
        EditText input = new EditText(this);
        input.setHint(hint);
        if (initialValue != null) input.setText(initialValue);
        input.setTextColor(textPrimary);
        input.setHintTextColor(textSecondary);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(9));
        bg.setColor(bgColor);
        bg.setStroke(Math.max(1, dp(1)), accent);
        input.setBackground(bg);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        return input;
    }

    private void promptCreateFolder() {
        if (currentDir == null) return;
        EditText input = createStyledInput("שם התיקייה החדשה...", null);
        ThemedDialogHelper.showCustomDialog(
                this,
                "תיקייה חדשה",
                "התיקייה תיווצר ב-" + currentDir.getName() + ":",
                input,
                "צור תיקייה",
                () -> {
                    String folderName = input.getText().toString().trim();
                    if (!folderName.isEmpty()) {
                        File nf = new File(currentDir, folderName);
                        if (nf.mkdirs()) {
                            FileScanCache.invalidate();
                            loadFilesForCurrentMode();
                            Toast.makeText(this, "התיקייה נוצרה", Toast.LENGTH_SHORT).show();
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
        EditText etFileName = createStyledInput("שם הקובץ (לדוגמה: notes.txt או script.py)", "מסמך_חדש.txt");
        ThemedDialogHelper.showCustomDialog(
                this,
                "קובץ חדש",
                "הקובץ ייפתח מיד בעורך הקוד והטקסט המובנה:",
                etFileName,
                "צור ופתח",
                () -> {
                    String name = etFileName.getText().toString().trim();
                    if (name.isEmpty()) return;
                    if (!name.contains(".")) name = name + ".txt";
                    File target = StorageUtils.uniqueDestinationFile(currentDir, name);
                    try (FileOutputStream fos = new FileOutputStream(target)) {
                        fos.write("".getBytes(StandardCharsets.UTF_8));
                        FileScanCache.invalidate();
                        loadFilesForCurrentMode();
                        Intent ed = new Intent(this, CodeEditorActivity.class);
                        ed.putExtra(CodeEditorActivity.EXTRA_FILE_PATH, target.getAbsolutePath());
                        startActivity(ed);
                    } catch (Exception e) {
                        Toast.makeText(this, "יצירת הקובץ נכשלה: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                },
                "סגור",
                null,
                false
        );
    }

    // ==================== פעולות לוח ופעולות מרובות + התראות התקדמות (סעיף 28) ====================
    private void multiCopy(boolean isMove) {
        List<File> sel = getSelectedFilesList();
        if (sel.isEmpty()) {
            Toast.makeText(this, "בחר לפחות פריט אחד", Toast.LENGTH_SHORT).show();
            return;
        }
        clipboardFiles.clear();
        clipboardFiles.addAll(sel);
        clipboardIsMove = isMove;
        exitMultiSelectMode();
        updateClipboardBar();
        Toast.makeText(this, "נשמר ללוח (" + sel.size() + " פריטים) — נווט ליעד והדבק", Toast.LENGTH_LONG).show();
    }

    private void multiShare() {
        List<File> sel = getSelectedFilesList();
        ArrayList<Uri> uris = new ArrayList<>();
        for (File f : sel) {
            if (f.isFile()) {
                try {
                    uris.add(FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f));
                } catch (Throwable ignored) {
                }
            }
        }
        if (uris.isEmpty()) {
            Toast.makeText(this, "שיתוף זמין לקבצים (דחוס תיקיות תחילה)", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent share = new Intent(uris.size() == 1 ? Intent.ACTION_SEND : Intent.ACTION_SEND_MULTIPLE);
        share.setType("*/*");
        if (uris.size() == 1) {
            share.putExtra(Intent.EXTRA_STREAM, uris.get(0));
        } else {
            share.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        }
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(share, "שיתוף " + uris.size() + " קבצים"));
        exitMultiSelectMode();
    }

    private void multiDelete() {
        final List<File> sel = getSelectedFilesList();
        if (sel.isEmpty()) return;
        final boolean toTrash = PrefsManager.isUseRecycleBin(this);
        Runnable run = () -> {
            exitMultiSelectMode();
            final int opId = (int) (System.currentTimeMillis() % 10000);
            Toast.makeText(this, "מוחק " + sel.size() + " פריטים...", Toast.LENGTH_SHORT).show();
            executor.execute(() -> {
                int done = 0;
                int total = sel.size();
                for (File f : sel) {
                    FileOperationNotificationHelper.updateProgress(this, opId,
                            "מחיקת קבצים", f.getName(), done, total);
                    if (toTrash) RecycleBinManager.moveToTrash(this, f);
                    else StorageUtils.deleteRecursively(f);
                    done++;
                }
                FileOperationNotificationHelper.finish(this, opId,
                        "המחיקה הושלמה", "טופלו " + done + " פריטים");
                FileScanCache.invalidate();
                runOnUiThread(() -> loadFilesForCurrentMode(false));
            });
        };
        if (PrefsManager.isConfirmDelete(this)) {
            ThemedDialogHelper.showCustomDialog(
                    this,
                    toTrash ? "העברה לסל המיחזור" : "מחיקה לצמיתות",
                    "למחוק " + sel.size() + " פריטים נבחרים?",
                    null, "מחק הכל", run, "ביטול", null, true
            );
        } else {
            run.run();
        }
    }

    private void executePasteHere() {
        if (clipboardFiles.isEmpty() || currentDir == null) return;
        final List<File> items = new ArrayList<>(clipboardFiles);
        final boolean isMove = clipboardIsMove;
        final File dst = currentDir;
        final int opId = (int) (System.currentTimeMillis() % 10000);

        Toast.makeText(this, (isMove ? "מעביר " : "מעתיק ") + items.size() + " פריטים...", Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            int okCount = 0;
            int total = items.size();
            try {
                for (int i = 0; i < total; i++) {
                    File src = items.get(i);
                    FileOperationNotificationHelper.updateProgress(this, opId,
                            isMove ? "העברת קבצים" : "העתקת קבצים", src.getName(), i, total);
                    boolean ok = isMove
                            ? StorageUtils.moveFileOrDir(src, dst)
                            : StorageUtils.copyFileOrDir(src, dst);
                    if (ok) okCount++;
                }
                FileOperationNotificationHelper.finish(this, opId,
                        isMove ? "ההעברה הושלמה" : "ההעתקה הושלמה",
                        "הושלמו " + okCount + " מתוך " + total + " פריטים");
                final int fOk = okCount;
                FileScanCache.invalidate();
                runOnUiThread(() -> {
                    clipboardFiles.clear();
                    updateClipboardBar();
                    loadFilesForCurrentMode();
                    Toast.makeText(this, (isMove ? "הועברו " : "הועתקו ") + fOk + " פריטים", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                FileOperationNotificationHelper.finish(this, opId, "שגיאה בפעולת קבצים", e.getMessage());
                AppLogger.error(this, "FileOps", "Paste failed", e);
                runOnUiThread(() -> Toast.makeText(this, "שגיאה: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    // ==================== שינוי שם אצווה (סעיף 43) ====================
    private void promptBatchRename(List<File> items) {
        if (items.isEmpty()) {
            Toast.makeText(this, "בחר פריטים לשינוי שם אצווה", Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);

        EditText etPrefix = createStyledInput("תבנית שם (לדוגמה: תמונה_טיול_)", "קובץ_");
        EditText etStartNum = createStyledInput("מספר התחלתי (ברירת מחדל 1)", "1");
        etStartNum.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        EditText etExt = createStyledInput("סיומת אחידה (אופציונלי, ריק = שמור מקורית)", "");

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        form.addView(etPrefix);
        form.addView(etStartNum, lp);
        form.addView(etExt, lp);

        ThemedDialogHelper.showCustomDialog(
                this,
                "שינוי שם אצווה (" + items.size() + " פריטים)",
                "לדוגמה: תמונה_01, תמונה_02...",
                form,
                "בצע שינוי שם",
                () -> {
                    String prefix = etPrefix.getText().toString().trim();
                    if (prefix.isEmpty()) prefix = "file_";
                    int start = 1;
                    try {
                        start = Integer.parseInt(etStartNum.getText().toString().trim());
                    } catch (Throwable ignored) {
                    }
                    String forcedExt = etExt.getText().toString().trim();
                    if (!forcedExt.isEmpty() && !forcedExt.startsWith(".")) forcedExt = "." + forcedExt;

                    int renamed = 0;
                    for (int i = 0; i < items.size(); i++) {
                        File f = items.get(i);
                        File parent = f.getParentFile();
                        if (parent == null) continue;
                        String ext = "";
                        if (!f.isDirectory()) {
                            if (!forcedExt.isEmpty()) ext = forcedExt;
                            else {
                                int dot = f.getName().lastIndexOf('.');
                                if (dot > 0) ext = f.getName().substring(dot);
                            }
                        }
                        String num = String.format(Locale.US, "%02d", start + i);
                        File target = StorageUtils.uniqueDestinationFile(parent, prefix + num + ext);
                        if (f.renameTo(target)) renamed++;
                    }
                    exitMultiSelectMode();
                    if (renamed > 0) FileScanCache.invalidate();
                    loadFilesForCurrentMode(false);
                    Toast.makeText(this, "שונו שמות ל-" + renamed + " פריטים", Toast.LENGTH_SHORT).show();
                },
                "ביטול",
                null,
                false
        );
    }

    // ==================== דחיסה רב-פורמטית + סיסמה (סעיף 2) ====================
    private void promptMultiFormatCompress(List<File> sources) {
        if (sources == null || sources.isEmpty()) return;
        File first = sources.get(0);
        String baseName = sources.size() == 1
                ? (first.isDirectory() ? first.getName() : first.getName().replaceAll("\\.[^.]+$", ""))
                : ("Archive_" + sources.size() + "_items");

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);

        EditText etName = createStyledInput("שם הארכיון (ללא סיומת)", baseName);
        form.addView(etName);

        RadioGroup rgFmt = new RadioGroup(this);
        rgFmt.setOrientation(RadioGroup.VERTICAL);
        String[] fmts = {"zip", "tar", "tar.gz", "gz"};
        String[] fmtLabels = {"ZIP (סטנדרטי + תמיכה בסיסמה)", "TAR (לא דחוס)", "TAR.GZ (דחיסת GZip גבוהה)", "GZ (קובץ בודד)"};
        for (int i = 0; i < fmts.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(2000 + i);
            rb.setText(fmtLabels[i]);
            rb.setTextColor(textPrimary);
            if (i == 0) rb.setChecked(true);
            rgFmt.addView(rb);
        }
        LinearLayout.LayoutParams rgLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rgLp.topMargin = dp(8);
        form.addView(rgFmt, rgLp);

        EditText etPwd = createStyledInput("סיסמה לארכיון ZIP (אופציונלי)...", "");
        etPwd.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        form.addView(etPwd, rgLp);

        ThemedDialogHelper.showCustomDialog(
                this,
                "דחיסת קבצים לארכיון",
                "דוחס " + sources.size() + " פריטים נבחרים:",
                form,
                "צור ארכיון",
                () -> {
                    int idx = rgFmt.getCheckedRadioButtonId() - 2000;
                    if (idx < 0 || idx >= fmts.length) idx = 0;
                    String fmt = fmts[idx];
                    String rawName = etName.getText().toString().trim();
                    if (rawName.isEmpty()) rawName = baseName;
                    String fullFileName = rawName.toLowerCase(Locale.ROOT).endsWith("." + fmt)
                            ? rawName : (rawName + "." + fmt);
                    String pwd = etPwd.getText().toString();
                    File parent = first.getParentFile() != null
                            ? first.getParentFile()
                            : Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    final File targetArchive = StorageUtils.uniqueDestinationFile(parent, fullFileName);
                    final int opId = (int) (System.currentTimeMillis() % 10000);

                    exitMultiSelectMode();
                    Toast.makeText(this, "דוחס קבצים...", Toast.LENGTH_SHORT).show();
                    executor.execute(() -> {
                        try {
                            FileOperationNotificationHelper.updateProgress(this, opId,
                                    "דחיסת ארכיון", targetArchive.getName(), 0, 0);
                            ArchiveUtils.compressFiles(sources, targetArchive, fmt, pwd);
                            FileOperationNotificationHelper.finish(this, opId,
                                    "הארכיון נוצר בהצלחה", targetArchive.getName());
                            FileScanCache.invalidate();
                            runOnUiThread(() -> {
                                loadFilesForCurrentMode();
                                Toast.makeText(this, "נוצר בהצלחה: " + targetArchive.getName(), Toast.LENGTH_LONG).show();
                            });
                        } catch (Exception e) {
                            FileOperationNotificationHelper.finish(this, opId, "שגיאה בדחיסה", e.getMessage());
                            AppLogger.error(this, "Archive", "Compress failed", e);
                            runOnUiThread(() -> Toast.makeText(this, "שגיאה: " + e.getMessage(), Toast.LENGTH_LONG).show());
                        }
                    });
                },
                "ביטול",
                null,
                false
        );
    }

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
                            FileScanCache.invalidate();
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

    // ==================== פתיחת קבצים והצעות .bak (סעיפים 6, 14, 32, 40) ====================
    private void onFileClicked(File file) {
        if (multiSelectMode) {
            String p = file.getAbsolutePath();
            if (selectedPaths.contains(p)) selectedPaths.remove(p);
            else selectedPaths.add(p);
            updateMultiSelectUi();
            return;
        }
        if (MODE_RECYCLE_BIN.equals(currentMode)) {
            showFileOperationsMenu(file);
            return;
        }
        if (file.isDirectory()) {
            if (isGroupedMediaCategoryMode()) {
                selectedCategoryFolder = file;
                tvHeaderTitle.setText(file.getName());
                if (etSearch.getText().length() > 0) etSearch.setText("");
                loadFilesForCurrentMode(false);
                return;
            }
            currentDir = file;
            tvHeaderTitle.setText(file.getName());
            if (etSearch.getText().length() > 0) etSearch.setText("");
            loadFilesForCurrentMode();
            return;
        }

        // סעיף 40: שחזור קובץ גיבוי .bak
        if (MediaFormatHelper.isBackupFile(file.getName())) {
            promptBackupFileAction(file);
            return;
        }

        FileOpenWithHelper.tryOpenByDefaultOrBuiltin(this, file);
    }

    private void promptBackupFileAction(File bakFile) {
        String origName = bakFile.getName().substring(0, bakFile.getName().length() - 4);
        ThemedDialogHelper.showCustomDialog(
                this,
                "קובץ גיבוי (.bak)",
                "האם לשחזר את הגיבוי לקובץ המקורי \"" + origName + "\" או לצפות בתוכן כטקסט?",
                null,
                "שחזר קובץ מקור",
                () -> {
                    File dest = new File(bakFile.getParentFile(), origName);
                    try (FileInputStream fis = new FileInputStream(bakFile);
                         FileOutputStream fos = new FileOutputStream(dest)) {
                        byte[] buf = new byte[16 * 1024];
                        int r;
                        while ((r = fis.read(buf)) != -1) fos.write(buf, 0, r);
                        FileScanCache.invalidate();
                        loadFilesForCurrentMode(false);
                        Toast.makeText(this, "הקובץ שוחזר: " + dest.getName(), Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Toast.makeText(this, "שגיאה בשחזור: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                },
                "פתח בעורך",
                () -> {
                    Intent i = new Intent(this, CodeEditorActivity.class);
                    i.putExtra(CodeEditorActivity.EXTRA_FILE_PATH, bakFile.getAbsolutePath());
                    startActivity(i);
                },
                false
        );
    }

    // ==================== תפריט אפשרויות קובץ (שמאל האייקון) ====================
    private void showFileOperationsMenu(File file) {
        String subInfo = file.isDirectory() ? "תיקייה" : StorageUtils.formatSize(file.length());

        if (MODE_RECYCLE_BIN.equals(currentMode)) {
            ThemedDialogHelper.showThemedMenu(
                    this, file.getName(), "פריט בסל המיחזור",
                    Arrays.asList("שחזר למיקום המקורי", "מחיקה לצמיתות", "פרטי פריט"),
                    which -> {
                        if (which == 0) {
                            if (RecycleBinManager.restoreFromTrash(this, file)) {
                                FileScanCache.invalidate();
                                loadFilesForCurrentMode();
                                Toast.makeText(this, "הקובץ שוחזר בהצלחה", Toast.LENGTH_SHORT).show();
                            }
                        } else if (which == 1) {
                            StorageUtils.deleteRecursively(file);
                            FileScanCache.invalidate();
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
            actions.add("התקן / בדוק חבילת אפליקציה");
            actions.add("הצג מקור ה-APK (Manifest / DEX)");
        } else if (!file.isDirectory()) {
            actions.add(isZip ? "פתיחה וניהול תוכן ארכיון" : "פתיחה");
            actions.add("פתיחה באמצעות...");
        }
        if (!isDirectoryMode() && file.getParentFile() != null) {
            actions.add("פתח בתיקייה המקורית");
        }
        actions.add("בחירה מרובה");
        actions.add("דחיסה (ZIP / TAR / GZ)");
        actions.add("העתקה");
        actions.add("העברה");
        actions.add("שינוי שם");
        if (!file.isDirectory()) actions.add("שיתוף קובץ");
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
                        case "התקן / בדוק חבילת אפליקציה":
                            AppPackageInstaller.inspectAndInstall(this, file);
                            break;
                        case "הצג מקור ה-APK (Manifest / DEX)": {
                            Intent i = new Intent(this, ApkSourceInspectorActivity.class);
                            i.putExtra(ApkSourceInspectorActivity.EXTRA_APK_PATH, file.getAbsolutePath());
                            startActivity(i);
                            break;
                        }
                        case "פתיחה וניהול תוכן ארכיון":
                        case "פתיחה":
                            onFileClicked(file);
                            break;
                        case "פתיחה באמצעות...":
                            FileOpenWithHelper.showOpenAsDialog(this, file);
                            break;
                        case "פתח בתיקייה המקורית": {
                            Intent jump = new Intent(this, FileBrowserActivity.class);
                            jump.putExtra(EXTRA_MODE, MODE_INTERNAL);
                            jump.putExtra(EXTRA_INITIAL_DIR, file.getParentFile().getAbsolutePath());
                            jump.putExtra(EXTRA_TITLE, file.getParentFile().getName());
                            startActivity(jump);
                            break;
                        }
                        case "בחירה מרובה":
                            enterMultiSelectMode(file);
                            break;
                        case "דחיסה (ZIP / TAR / GZ)":
                            promptMultiFormatCompress(Collections.singletonList(file));
                            break;
                        case "העתקה":
                            clipboardFiles.clear();
                            clipboardFiles.add(file);
                            clipboardIsMove = false;
                            updateClipboardBar();
                            Toast.makeText(this, "מוכן להעתקה", Toast.LENGTH_SHORT).show();
                            break;
                        case "העברה":
                            clipboardFiles.clear();
                            clipboardFiles.add(file);
                            clipboardIsMove = true;
                            updateClipboardBar();
                            Toast.makeText(this, "מוכן להעברה", Toast.LENGTH_SHORT).show();
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

    private void showInstalledAppMenu(InstalledAppEntry app) {
        List<String> opts = new ArrayList<>();
        if (app.sourceApk != null && app.sourceApk.exists()) {
            opts.add("גיבוי APK לתיקיית ההורדות");
            opts.add("שיתוף חבילת APK");
            opts.add("הצג מקור ה-APK (Manifest / DEX)");
        }
        opts.add("פרטי אפליקציה בהגדרות");
        opts.add("הסרת התקנה");

        ThemedDialogHelper.showThemedMenu(
                this,
                app.appName,
                app.packageName + "  •  " + app.versionName,
                opts,
                idx -> {
                    String c = opts.get(idx);
                    if (c.startsWith("גיבוי APK") && app.sourceApk != null) {
                        File dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                        File out = StorageUtils.uniqueDestinationFile(dl, app.appName + "_" + app.versionName + ".apk");
                        executor.execute(() -> {
                            try (FileInputStream fis = new FileInputStream(app.sourceApk);
                                 FileOutputStream fos = new FileOutputStream(out)) {
                                byte[] buf = new byte[16 * 1024];
                                int r;
                                while ((r = fis.read(buf)) != -1) fos.write(buf, 0, r);
                                runOnUiThread(() -> Toast.makeText(this, "גובה ל: " + out.getName(), Toast.LENGTH_LONG).show());
                            } catch (Exception e) {
                                runOnUiThread(() -> Toast.makeText(this, "הגיבוי נכשל: " + e.getMessage(), Toast.LENGTH_LONG).show());
                            }
                        });
                    } else if (c.startsWith("שיתוף") && app.sourceApk != null) {
                        shareFile(app.sourceApk);
                    } else if (c.startsWith("הצג מקור") && app.sourceApk != null) {
                        Intent i = new Intent(this, ApkSourceInspectorActivity.class);
                        i.putExtra(ApkSourceInspectorActivity.EXTRA_APK_PATH, app.sourceApk.getAbsolutePath());
                        startActivity(i);
                    } else if (c.startsWith("פרטי")) {
                        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + app.packageName));
                        startActivity(i);
                    } else if (c.startsWith("הסרת")) {
                        Intent del = new Intent(Intent.ACTION_DELETE,
                                Uri.parse("package:" + app.packageName));
                        startActivity(del);
                    }
                }
        );
    }

    private void promptRenameFile(File file) {
        EditText input = createStyledInput("שם חדש...", file.getName());
        ThemedDialogHelper.showCustomDialog(
                this,
                "שינוי שם",
                "הזן שם מעודכן לפריט:",
                input,
                "שמור",
                () -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.isEmpty() && file.getParentFile() != null) {
                        File dest = new File(file.getParentFile(), newName);
                        if (file.renameTo(dest)) {
                            FileScanCache.invalidate();
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
                FileScanCache.invalidate();
                loadFilesForCurrentMode();
                Toast.makeText(this, toTrash ? "הועבר לסל המיחזור" : "נמחק לצמיתות", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "המחיקה נכשלה", Toast.LENGTH_SHORT).show();
            }
        };

        if (PrefsManager.isConfirmDelete(this)) {
            ThemedDialogHelper.showCustomDialog(
                    this,
                    toTrash ? "העברה לסל המיחזור" : "מחיקה לצמיתות",
                    "האם למחוק את \"" + file.getName() + "\"?",
                    null, "מחק", performDelete, "סגור", null, true
            );
        } else {
            performDelete.run();
        }
    }

    private void shareFile(File file) {
        if (file.isDirectory()) {
            Toast.makeText(this, "שיתוף זמין לקבצים (ניתן לדחוס תיקייה ולשתף)", Toast.LENGTH_LONG).show();
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
        SimpleDateFormat fmt = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US);
        String modified = fmt.format(new Date(file.lastModified()));
        String created = fmt.format(new Date(StorageUtils.getFileCreatedTimeMillis(file)));
        String sizeStr = file.isDirectory()
                ? StorageUtils.formatSize(StorageUtils.computeDirectoryBytes(file, true, 0))
                : StorageUtils.formatSize(file.length());
        String msg = "שם הפריט: " + file.getName()
                + "\nסוג: " + (file.isDirectory() ? "תיקייה" : "קובץ")
                + "\nנפח: " + sizeStr
                + "\nנוצר: " + created
                + "\nעודכן לאחרונה: " + modified
                + "\nנתיב מלא:\n" + file.getAbsolutePath();

        ThemedDialogHelper.showCustomDialog(
                this, "מאפייני פריט", msg, null, "סגור", null, null, null, false
        );
    }

    private void navigateUpOrFinish() {
        if (isGroupedMediaCategoryMode() && selectedCategoryFolder != null) {
            selectedCategoryFolder = null;
            tvHeaderTitle.setText(baseCategoryTitle);
            if (etSearch.getText().length() > 0) etSearch.setText("");
            loadFilesForCurrentMode(false);
            return;
        }
        if (isDirectoryMode() && currentDir != null && rootBoundary != null
                && !currentDir.getAbsolutePath().equals(rootBoundary.getAbsolutePath())
                && currentDir.getParentFile() != null) {
            currentDir = currentDir.getParentFile();
            tvHeaderTitle.setText(currentDir.equals(rootBoundary) ? baseCategoryTitle : currentDir.getName());
            loadFilesForCurrentMode();
            return;
        }
        finish();
    }

    private class FileAdapter extends RecyclerView.Adapter<FileAdapter.VH> {
        private final SimpleDateFormat df = new SimpleDateFormat("dd/MM/yy HH:mm", Locale.US);

        @Override
        public int getItemViewType(int position) {
            return PrefsManager.isGridLayoutForMode(FileBrowserActivity.this, currentMode) ? 1 : 0;
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
            boolean isGrid = getItemViewType(position) == 1;
            boolean isSelected = multiSelectMode && selectedPaths.contains(f.getAbsolutePath());
            h.rowCard.setCardBackgroundColor(isSelected
                    ? Color.argb(46, Color.red(accent), Color.green(accent), Color.blue(accent))
                    : cardColor);

            h.tvName.setText(f.getName());
            h.tvName.setTextSize(ThemeHelper.spScaled(FileBrowserActivity.this, isGrid ? 12f : 13.5f));
            h.tvName.setTextColor(textPrimary);
            h.tvHint.setTextColor(accent);

            FileThumbnailLoader.bindFileIcon(FileBrowserActivity.this, f, h.ivIcon);

            if (f.isDirectory()) {
                String path = f.getAbsolutePath();
                if (isGroupedMediaCategoryMode() && selectedCategoryFolder == null) {
                    Integer matched = categoryFolderCounts.get(path);
                    int count = matched != null ? matched : 0;
                    String unit = MODE_IMAGES.equals(currentMode) ? "תמונות" : "סירטונים";
                    h.tvMeta.setText(isGrid
                            ? (count + " " + unit)
                            : (count + " " + unit + " • " + df.format(new Date(f.lastModified()))));
                } else {
                    Integer cachedDirCount = dirItemCountCache.get(path);
                    if (cachedDirCount != null) {
                        h.tvMeta.setText(isGrid
                                ? (cachedDirCount + " פריטים")
                                : (cachedDirCount + " פריטים • " + df.format(new Date(f.lastModified()))));
                    } else {
                        h.tvMeta.setText("תיקייה");
                        scheduleDirChildCount(f, position);
                    }
                }
            } else {
                String p = f.getAbsolutePath();
                Long sz = cachedSizesByPath.get(p);
                long fileLen = sz != null ? sz : f.length();
                Long md = cachedModifiedByPath.get(p);
                long modMs = md != null ? md : f.lastModified();
                h.tvMeta.setText(isGrid
                        ? StorageUtils.formatSize(fileLen)
                        : (StorageUtils.formatSize(fileLen) + " • " + df.format(new Date(modMs))));
            }
            h.tvMeta.setTextColor(textSecondary);

            // סעיף ח: לחיצה על האייקון משמאל פותחת אפשרויות עריכה; לחיצה ארוכה נכנסת לבחירה מרובה (סעיף 5)
            h.ivIcon.setOnClickListener(v -> {
                if (multiSelectMode) onFileClicked(f);
                else showFileOperationsMenu(f);
            });
            h.rowCard.setOnClickListener(v -> onFileClicked(f));
            h.rowCard.setOnLongClickListener(v -> {
                if (!multiSelectMode) enterMultiSelectMode(f);
                else onFileClicked(f);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return displayedFiles.size();
        }

        private void scheduleDirChildCount(File dir, int adapterPos) {
            final String path = dir.getAbsolutePath();
            if (!pendingDirCountPaths.add(path)) return;
            executor.execute(() -> {
                File[] sub = dir.listFiles();
                int count = sub != null ? sub.length : 0;
                dirItemCountCache.put(path, count);
                runOnUiThread(() -> {
                    if (adapterPos >= 0 && adapterPos < displayedFiles.size()
                            && displayedFiles.get(adapterPos).getAbsolutePath().equals(path)) {
                        notifyItemChanged(adapterPos);
                    }
                });
            });
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

    private class InstalledAppsAdapter extends RecyclerView.Adapter<InstalledAppsAdapter.VH> {
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_file_row, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            InstalledAppEntry app = displayedInstalledApps.get(position);
            h.rowCard.setCardBackgroundColor(cardColor);
            h.tvName.setText(app.appName);
            h.tvName.setTextColor(textPrimary);
            h.tvMeta.setText(app.packageName + " • " + app.versionName + " • " + StorageUtils.formatSize(app.sizeBytes));
            h.tvMeta.setTextColor(textSecondary);
            try {
                h.ivIcon.setImageDrawable(getPackageManager().getApplicationIcon(app.packageName));
            } catch (Throwable t) {
                h.ivIcon.setImageResource(R.drawable.ic_apps);
            }
            h.ivIcon.setOnClickListener(v -> showInstalledAppMenu(app));
            h.rowCard.setOnClickListener(v -> showInstalledAppMenu(app));
        }

        @Override
        public int getItemCount() {
            return displayedInstalledApps.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final CardView rowCard;
            final ImageView ivIcon;
            final TextView tvName;
            final TextView tvMeta;

            VH(@NonNull View itemView) {
                super(itemView);
                rowCard = itemView.findViewById(R.id.rowCard);
                ivIcon = itemView.findViewById(R.id.ivFileIcon);
                tvName = itemView.findViewById(R.id.tvFileName);
                tvMeta = itemView.findViewById(R.id.tvFileMeta);
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
