package com.aishhatorah.filemanagerpro;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * סעיפים 3, 6, 7, 8, 17, 18, 19, 33, 34, 38:
 * מסך הבית המינימליסטי והרספונסיבי של File Manager Pro 0.3.6:
 * - טעינה מיידית ממטמון סריקה (FileScanCache) ועדכון ברקע
 * - כרטיס גישה מהירה לתיקייה האחרונה
 * - זיהוי והצגת כונני USB OTG מחוברים בזמן אמת + לשונית הגדרות והוצאה בטוחה
 * - תמיכה בסידור והסתרת קטגוריות בהתאמה אישית (HomeLayoutActivity)
 */
public class MainActivity extends AppCompatActivity {

    private static final int REQ_MEDIA_PERMS = 1001;

    private final ExecutorService bgExecutor = Executors.newSingleThreadExecutor();
    private final Map<String, TextView> subtitleViewsById = new HashMap<>();
    private StorageUtils.CategoryBytesSummary cachedSizes = new StorageUtils.CategoryBytesSummary();
    private long lastBackPressedMs = 0L;
    private boolean forceShowAllCategories = false;
    private boolean sdSettingsTabExpanded = false;
    private String expandedUsbPath = null;

    private LinearLayout rootContainer;
    private LinearLayout topBar;
    private View topDivider;
    private ImageButton btnAbout;
    private ImageButton btnSettings;
    private TextView tvAppTitle;
    private SwipeRefreshLayout swipeRefreshHome;
    private CardView cardStorageAlert;
    private TextView tvAlertMessage;
    private CardView cardQuickAccess;
    private ImageView ivQuickAccessIcon;
    private TextView tvQuickAccessLabel;
    private TextView tvQuickAccessPath;
    private CardView cardStorageSummary;
    private TextView tvInternalSummaryTitle;
    private TextView tvInternalSummaryStats;
    private ProgressBar progressInternal;
    private LinearLayout sdSummaryBlock;
    private TextView tvSdSummaryTitle;
    private TextView tvSdSummaryStats;
    private ProgressBar progressSd;
    private LinearLayout usbOtgContainer;
    private LinearLayout gridRowsContainer;
    private Button btnShowAllCategories;
    private TextView tvPullHint;
    private AudioPlayerManager.PlaybackListener miniAudioListener;

    private static class HomeCategory {
        final String id;
        final String titleHe;
        final int iconRes;
        final String customDirPath;

        HomeCategory(String id, String titleHe, int iconRes) {
            this(id, titleHe, iconRes, null);
        }

        HomeCategory(String id, String titleHe, int iconRes, String customDirPath) {
            this.id = id;
            this.titleHe = titleHe;
            this.iconRes = iconRes;
            this.customDirPath = customDirPath;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        StorageUtils.CategoryBytesSummary prewarmed = FileScanCache.getCached();
        if (prewarmed != null) {
            cachedSizes = prewarmed;
        }

        bindViews();
        setupDoubleBackToExit();
        ensureStorageAndMediaPermissions();
        checkPostUpdateConfirmation();

        if (PrefsManager.isAutoCheckUpdates(this)) {
            UpdateChecker.checkForUpdates(this, false);
        }
    }

    private void setupDoubleBackToExit() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                long now = System.currentTimeMillis();
                if (now - lastBackPressedMs < 2000L) {
                    finish();
                } else {
                    lastBackPressedMs = now;
                    Toast.makeText(MainActivity.this, "לחץ שוב כדי לצאת", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void checkPostUpdateConfirmation() {
        String lastVer = PrefsManager.getLastKnownAppVersion(this);
        boolean pendingBanner = PrefsManager.isPendingUpdateSuccessBanner(this);
        boolean upgraded = !lastVer.isEmpty() && UpdateChecker.compareSemver(BuildConfig.VERSION_NAME, lastVer) > 0;

        if (upgraded || pendingBanner) {
            PrefsManager.setPendingUpdateSuccessBanner(this, false);
            PrefsManager.setLastKnownAppVersion(this, BuildConfig.VERSION_NAME);
            UpdateInstallReceiver.postSuccessNotification(this);
            ThemedDialogHelper.showCustomDialog(
                    this,
                    "\u2066File Manager Pro\u2069 עודכן בהצלחה",
                    "האפליקציה שודרגה בהצלחה לגירסה " + BuildConfig.VERSION_NAME + ".\n\n"
                            + "כלל הקבצים, ההרשאות וההגדרות האישיות שלך נשמרו במלואם.",
                    null,
                    "הבנתי",
                    null,
                    null,
                    null,
                    false
            );
        } else if (lastVer.isEmpty()) {
            PrefsManager.setLastKnownAppVersion(this, BuildConfig.VERSION_NAME);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyCurrentThemeAndRefresh(false);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyCurrentThemeAndRefresh(false);
    }

    private void bindViews() {
        rootContainer = findViewById(R.id.rootContainer);
        topBar = findViewById(R.id.topBar);
        topDivider = findViewById(R.id.topDivider);
        btnAbout = findViewById(R.id.btnAbout);
        btnSettings = findViewById(R.id.btnSettings);
        tvAppTitle = findViewById(R.id.tvAppTitle);
        swipeRefreshHome = findViewById(R.id.swipeRefreshHome);
        cardStorageAlert = findViewById(R.id.cardStorageAlert);
        tvAlertMessage = findViewById(R.id.tvAlertMessage);
        cardQuickAccess = findViewById(R.id.cardQuickAccess);
        ivQuickAccessIcon = findViewById(R.id.ivQuickAccessIcon);
        tvQuickAccessLabel = findViewById(R.id.tvQuickAccessLabel);
        tvQuickAccessPath = findViewById(R.id.tvQuickAccessPath);
        cardStorageSummary = findViewById(R.id.cardStorageSummary);
        tvInternalSummaryTitle = findViewById(R.id.tvInternalSummaryTitle);
        tvInternalSummaryStats = findViewById(R.id.tvInternalSummaryStats);
        progressInternal = findViewById(R.id.progressInternal);
        sdSummaryBlock = findViewById(R.id.sdSummaryBlock);
        tvSdSummaryTitle = findViewById(R.id.tvSdSummaryTitle);
        tvSdSummaryStats = findViewById(R.id.tvSdSummaryStats);
        progressSd = findViewById(R.id.progressSd);
        usbOtgContainer = findViewById(R.id.usbOtgContainer);
        gridRowsContainer = findViewById(R.id.gridRowsContainer);
        btnShowAllCategories = findViewById(R.id.btnShowAllCategories);
        tvPullHint = findViewById(R.id.tvPullHint);

        Button btnOpenAnalyzerFromAlert = findViewById(R.id.btnOpenAnalyzerFromAlert);

        btnAbout.setOnClickListener(v -> AboutDialogHelper.show(this));
        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        btnOpenAnalyzerFromAlert.setOnClickListener(v ->
                startActivity(new Intent(this, StorageAnalyzerActivity.class)));

        btnShowAllCategories.setOnClickListener(v -> {
            forceShowAllCategories = !forceShowAllCategories;
            applyCurrentThemeAndRefresh(false);
        });

        swipeRefreshHome.setColorSchemeColors(0xFF1D4ED8, 0xFF0284C7, 0xFF0D9488);
        swipeRefreshHome.setOnRefreshListener(() -> applyCurrentThemeAndRefresh(true));

        miniAudioListener = MiniAudioBarHelper.attach(this, rootContainer);
    }

    private void applyCurrentThemeAndRefresh(boolean fromUserPullToRefresh) {
        int theme = PrefsManager.getThemeMode(this);
        ThemeHelper.applyWindowStatusBar(this, theme);

        int bg = ThemeHelper.getBgColor(this, theme);
        int cardBg = ThemeHelper.getCardColor(this, theme);
        int topBg = ThemeHelper.getTopBarColor(this, theme);
        int textPrimary = ThemeHelper.getPrimaryTextColor(this, theme);
        int textSecondary = ThemeHelper.getSecondaryTextColor(this, theme);
        int accent = ThemeHelper.getAccentColor(this, theme);
        int divider = ThemeHelper.getDividerColor(this, theme);

        ThemeHelper.applyWallpaperBackdrop(this, rootContainer, theme);
        ThemeHelper.applyCurvedHeaderStyle(this, topBar, theme);
        topDivider.setVisibility(View.GONE);

        tvAppTitle.setText("File Manager Pro");
        tvAppTitle.setTextColor(textPrimary);
        tvAppTitle.setTextSize(ThemeHelper.spScaled(this, 15.5f));
        tvPullHint.setTextColor(ThemeHelper.hasWallpaper(this) ? textPrimary : textSecondary);
        tvPullHint.setTextSize(ThemeHelper.spScaled(this, 11.5f));

        ColorStateList accentState = ColorStateList.valueOf(accent);
        btnAbout.setColorFilter(accent);
        btnSettings.setColorFilter(accent);
        swipeRefreshHome.setColorSchemeColors(accent);

        ThemeHelper.styleGlassCardView(this, cardStorageSummary, theme, 18f, true);
        tvInternalSummaryTitle.setTextColor(textPrimary);
        tvInternalSummaryStats.setTextColor(textSecondary);
        tvSdSummaryTitle.setTextColor(textPrimary);
        tvSdSummaryStats.setTextColor(textSecondary);
        ThemeHelper.styleHorizontalProgressBar(progressInternal, accent);
        ThemeHelper.styleHorizontalProgressBar(progressSd, accent);

        refreshQuickAccessBanner(theme, textPrimary, textSecondary, accent);
        refreshStorageSummaryAnd96Alert(theme, textPrimary, textSecondary, accent);
        buildHomeGrid(theme, cardBg, textPrimary, textSecondary, accent);

        if (miniAudioListener != null) {
            miniAudioListener.onPlaybackStateChanged();
        }
        refreshCategoryVolumesAsync(fromUserPullToRefresh);
    }

    private void refreshQuickAccessBanner(int theme, int textPrimary, int textSecondary, int accent) {
        if (!PrefsManager.isQuickAccessEnabled(this)) {
            cardQuickAccess.setVisibility(View.GONE);
            return;
        }
        String lastPath = PrefsManager.getLastVisitedPath(this);
        String lastMode = PrefsManager.getLastVisitedMode(this);
        if (lastPath.isEmpty()) {
            cardQuickAccess.setVisibility(View.GONE);
            return;
        }
        File dir = new File(lastPath);
        if (!dir.exists() || !dir.isDirectory()) {
            cardQuickAccess.setVisibility(View.GONE);
            return;
        }
        cardQuickAccess.setVisibility(View.VISIBLE);
        ThemeHelper.styleGlassCardView(this, cardQuickAccess, theme, 16f, false);
        if (ivQuickAccessIcon != null) {
            ivQuickAccessIcon.setColorFilter(accent);
        }
        tvQuickAccessLabel.setTextColor(textPrimary);
        tvQuickAccessLabel.setTextSize(ThemeHelper.spScaled(this, 13f));
        tvQuickAccessLabel.setText("גישה מהירה");
        if (tvQuickAccessPath != null) {
            tvQuickAccessPath.setTextColor(textSecondary);
            tvQuickAccessPath.setTextSize(ThemeHelper.spScaled(this, 10.5f));
            tvQuickAccessPath.setText(dir.getAbsolutePath());
        }
        cardQuickAccess.setOnClickListener(v -> {
            Intent i = new Intent(this, FileBrowserActivity.class);
            i.putExtra(FileBrowserActivity.EXTRA_MODE, lastMode.isEmpty() ? FileBrowserActivity.MODE_INTERNAL : lastMode);
            i.putExtra(FileBrowserActivity.EXTRA_TITLE, dir.getName());
            i.putExtra(FileBrowserActivity.EXTRA_INITIAL_DIR, dir.getAbsolutePath());
            startActivity(i);
        });
    }

    private void refreshCategoryVolumesAsync(final boolean notifyWhenScanFinished) {
        bgExecutor.execute(() -> {
            StorageUtils.CategoryBytesSummary fresh = FileScanCache
                    .getSnapshot(this, PrefsManager.isShowHidden(this), notifyWhenScanFinished)
                    .summary;
            runOnUiThread(() -> {
                cachedSizes = fresh;
                boolean showLabelsNow = PrefsManager.isShowHomeCategoryLabels(this);
                for (Map.Entry<String, TextView> entry : subtitleViewsById.entrySet()) {
                    String catId = entry.getKey();
                    String sub = getCategorySubtitle(catId, null);
                    TextView tv = entry.getValue();
                    tv.setText(sub);
                    tv.setVisibility((showLabelsNow && !sub.isEmpty()) ? View.VISIBLE : View.GONE);
                }
                if (notifyWhenScanFinished) {
                    swipeRefreshHome.setRefreshing(false);
                    Toast.makeText(this, "כל הקבצים רועננו בהצלחה", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private String formatRtlStorageStats(long usedBytes, long totalBytes, double usedPercent) {
        String used = StorageUtils.formatSize(usedBytes);
        String total = StorageUtils.formatSize(totalBytes);
        String pct = String.format(Locale.US, "%.1f%%", usedPercent);
        return "\u2066" + used + "\u2069 מתוך \u2066" + total + "\u2069 (\u2066" + pct + "\u2069)";
    }

    private void refreshStorageSummaryAnd96Alert(int theme, int textPrimary, int textSecondary, int accent) {
        StorageUtils.StorageStat internal = StorageUtils.getInternalStorageStat();
        if (internal.available) {
            int pct = (int) Math.round(internal.usedPercent);
            progressInternal.setProgress(pct);
            tvInternalSummaryStats.setText(formatRtlStorageStats(internal.usedBytes, internal.totalBytes, internal.usedPercent));
        }

        StorageUtils.StorageStat sd = StorageUtils.getExternalSdCardStat(this);
        for (int i = sdSummaryBlock.getChildCount() - 1; i >= 0; i--) {
            View ch = sdSummaryBlock.getChildAt(i);
            if ("external_settings_tab".equals(ch.getTag())) {
                sdSummaryBlock.removeViewAt(i);
            }
        }
        if (sd.available) {
            sdSummaryBlock.setVisibility(View.VISIBLE);
            int sdPct = (int) Math.round(sd.usedPercent);
            progressSd.setProgress(sdPct);
            tvSdSummaryStats.setText(formatRtlStorageStats(sd.usedBytes, sd.totalBytes, sd.usedPercent));
            sdSummaryBlock.setClickable(true);
            sdSummaryBlock.setFocusable(true);
            final LinearLayout sdTab = buildExternalStorageSettingsTab(
                    theme, textPrimary, accent,
                    FileBrowserActivity.MODE_SD_CARD, "כרטיס SD", sd.root);
            sdTab.setVisibility(sdSettingsTabExpanded ? View.VISIBLE : View.GONE);
            sdSummaryBlock.addView(sdTab);
            sdSummaryBlock.setOnClickListener(v -> {
                sdSettingsTabExpanded = !sdSettingsTabExpanded;
                sdTab.setVisibility(sdSettingsTabExpanded ? View.VISIBLE : View.GONE);
            });
        } else {
            sdSettingsTabExpanded = false;
            sdSummaryBlock.setVisibility(View.GONE);
        }

        // כונני USB OTG מחוברים
        usbOtgContainer.removeAllViews();
        List<StorageUtils.StorageStat> otgVolumes = StorageUtils.getConnectedUsbOtgVolumes(this);
        if (otgVolumes.isEmpty()) {
            expandedUsbPath = null;
            usbOtgContainer.setVisibility(View.GONE);
        } else {
            usbOtgContainer.setVisibility(View.VISIBLE);
            float d = getResources().getDisplayMetrics().density;
            for (StorageUtils.StorageStat usb : otgVolumes) {
                final String usbPath = usb.root != null ? usb.root.getAbsolutePath() : usb.label;
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(0, Math.round(6 * d), 0, 0);

                LinearLayout top = new LinearLayout(this);
                top.setOrientation(LinearLayout.HORIZONTAL);
                top.setGravity(Gravity.CENTER_VERTICAL);

                TextView tvName = new TextView(this);
                tvName.setText(usb.label.isEmpty() ? "כונן USB חיצוני" : usb.label);
                tvName.setTextColor(accent);
                tvName.setTextSize(13f);
                tvName.setTypeface(Typeface.DEFAULT_BOLD);
                top.addView(tvName, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                TextView tvStat = new TextView(this);
                tvStat.setText("\u2066" + StorageUtils.formatSize(usb.freeBytes) + "\u2069 פנוי מתוך \u2066" + StorageUtils.formatSize(usb.totalBytes) + "\u2069");
                tvStat.setTextDirection(View.TEXT_DIRECTION_RTL);
                tvStat.setGravity(Gravity.END);
                tvStat.setTextColor(textSecondary);
                tvStat.setTextSize(11.5f);
                LinearLayout.LayoutParams statLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                statLp.setMarginStart(Math.round(8 * d));
                top.addView(tvStat, statLp);
                row.addView(top);

                ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
                pb.setMax(100);
                pb.setProgress((int) Math.round(usb.usedPercent));
                ThemeHelper.styleHorizontalProgressBar(pb, accent);
                LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, Math.round(6 * d));
                pbLp.topMargin = Math.round(5 * d);
                row.addView(pb, pbLp);

                final LinearLayout usbTab = buildExternalStorageSettingsTab(
                        theme, textPrimary, accent,
                        FileBrowserActivity.MODE_USB_OTG,
                        usb.label.isEmpty() ? "כונן USB" : usb.label,
                        usb.root);
                usbTab.setVisibility(usbPath.equals(expandedUsbPath) ? View.VISIBLE : View.GONE);
                row.addView(usbTab);

                row.setOnClickListener(v -> {
                    boolean nextShow = !usbPath.equals(expandedUsbPath);
                    expandedUsbPath = nextShow ? usbPath : null;
                    refreshStorageSummaryAnd96Alert(theme, textPrimary, textSecondary, accent);
                });
                usbOtgContainer.addView(row);
            }
        }

        String alertMsg = StorageUtils.checkAndNotify96Threshold(this);
        if (alertMsg != null) {
            cardStorageAlert.setVisibility(View.VISIBLE);
            tvAlertMessage.setText(alertMsg);
        } else {
            cardStorageAlert.setVisibility(View.GONE);
        }
    }

    private LinearLayout buildExternalStorageSettingsTab(
            int theme,
            int textPrimary,
            int accent,
            String mode,
            String title,
            File rootDir
    ) {
        float d = getResources().getDisplayMetrics().density;
        LinearLayout tab = new LinearLayout(this);
        tab.setTag("external_settings_tab");
        tab.setOrientation(LinearLayout.HORIZONTAL);
        tab.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        tab.setGravity(Gravity.CENTER_VERTICAL);
        tab.setPadding(Math.round(10 * d), Math.round(7 * d), Math.round(10 * d), Math.round(7 * d));
        LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tabLp.topMargin = Math.round(7 * d);
        tab.setLayoutParams(tabLp);

        GradientDrawable tabBg = new GradientDrawable();
        tabBg.setCornerRadius(Math.round(12 * d));
        tabBg.setColor(ThemeHelper.isDarkSurface(this, theme)
                ? Color.argb(42, 255, 255, 255)
                : Color.argb(26, Color.red(accent), Color.green(accent), Color.blue(accent)));
        tabBg.setStroke(Math.max(1, Math.round(d)), Color.argb(90, Color.red(accent), Color.green(accent), Color.blue(accent)));
        tab.setBackground(tabBg);

        LinearLayout btnSettingsAction = new LinearLayout(this);
        btnSettingsAction.setOrientation(LinearLayout.HORIZONTAL);
        btnSettingsAction.setGravity(Gravity.CENTER);
        btnSettingsAction.setPadding(Math.round(8 * d), Math.round(4 * d), Math.round(8 * d), Math.round(4 * d));
        ImageView ivSet = new ImageView(this);
        ivSet.setImageResource(R.drawable.ic_settings);
        ivSet.setColorFilter(accent);
        btnSettingsAction.addView(ivSet, new LinearLayout.LayoutParams(Math.round(16 * d), Math.round(16 * d)));
        TextView tvSet = new TextView(this);
        tvSet.setText("הגדרות / הוצאה בטוחה");
        tvSet.setTextColor(accent);
        tvSet.setTextSize(12f);
        tvSet.setTypeface(Typeface.DEFAULT_BOLD);
        tvSet.setPadding(Math.round(6 * d), 0, Math.round(6 * d), 0);
        btnSettingsAction.addView(tvSet);
        btnSettingsAction.setOnClickListener(v -> openSystemExternalStorageEjectSettings());
        tab.addView(btnSettingsAction, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.3f));

        View sep = new View(this);
        sep.setBackgroundColor(Color.argb(55, Color.red(textPrimary), Color.green(textPrimary), Color.blue(textPrimary)));
        LinearLayout.LayoutParams sepLp = new LinearLayout.LayoutParams(Math.max(1, Math.round(d)), Math.round(18 * d));
        sepLp.setMarginStart(Math.round(6 * d));
        sepLp.setMarginEnd(Math.round(6 * d));
        tab.addView(sep, sepLp);

        TextView tvOpen = new TextView(this);
        tvOpen.setText("פתח קבצים");
        tvOpen.setTextColor(textPrimary);
        tvOpen.setTextSize(12f);
        tvOpen.setTypeface(Typeface.DEFAULT_BOLD);
        tvOpen.setGravity(Gravity.CENTER);
        tvOpen.setPadding(Math.round(8 * d), Math.round(4 * d), Math.round(8 * d), Math.round(4 * d));
        tvOpen.setOnClickListener(v -> {
            Intent i = new Intent(this, FileBrowserActivity.class);
            i.putExtra(FileBrowserActivity.EXTRA_MODE, mode);
            i.putExtra(FileBrowserActivity.EXTRA_TITLE, title);
            if (rootDir != null) {
                i.putExtra(FileBrowserActivity.EXTRA_INITIAL_DIR, rootDir.getAbsolutePath());
            }
            startActivity(i);
        });
        tab.addView(tvOpen, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.9f));

        return tab;
    }

    private void openSystemExternalStorageEjectSettings() {
        Intent[] candidates = new Intent[]{
                new Intent(Settings.ACTION_MEMORY_CARD_SETTINGS),
                new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
                new Intent(Settings.ACTION_SETTINGS)
        };
        for (Intent intent : candidates) {
            try {
                if (intent.resolveActivity(getPackageManager()) != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    return;
                }
            } catch (Throwable ignored) {
            }
        }
        Toast.makeText(this, "פתח הגדרות המכשיר › אחסון להוצאה בטוחה", Toast.LENGTH_LONG).show();
    }

    private HomeCategory mapIdToHomeCategory(String key) {
        switch (key) {
            case "internal":
                return new HomeCategory(FileBrowserActivity.MODE_INTERNAL, "אחסון ראשי", R.drawable.ic_storage_internal);
            case "sd_card":
                return new HomeCategory(FileBrowserActivity.MODE_SD_CARD, "כרטיס SD", R.drawable.ic_sd_card);
            case "downloads":
                return new HomeCategory(FileBrowserActivity.MODE_DOWNLOADS, "הורדות", R.drawable.ic_downloads);
            case "images":
                return new HomeCategory(FileBrowserActivity.MODE_IMAGES, "תמונות", R.drawable.ic_images);
            case "audio":
                return new HomeCategory(FileBrowserActivity.MODE_AUDIO, "שמע", R.drawable.ic_audio);
            case "videos":
                return new HomeCategory(FileBrowserActivity.MODE_VIDEOS, "סירטונים", R.drawable.ic_video);
            case "documents":
                return new HomeCategory(FileBrowserActivity.MODE_DOCUMENTS, "מסמכים", R.drawable.ic_documents);
            case "apps":
                return new HomeCategory(FileBrowserActivity.MODE_APPS, "אפליקציות", R.drawable.ic_apps);
            case "recent":
                return new HomeCategory(FileBrowserActivity.MODE_RECENT, "קבצים חדשים", R.drawable.ic_recent);
            case "recycle_bin":
                return new HomeCategory(FileBrowserActivity.MODE_RECYCLE_BIN, "סל מיחזור", R.drawable.ic_recycle_bin);
            case "audio_cutter":
                return new HomeCategory("TOOL_AUDIO_CUTTER", "חותך אודיו", R.drawable.ic_audio_cutter);
            case "video_cutter":
                return new HomeCategory("TOOL_VIDEO_CUTTER", "חותך וידאו", R.drawable.ic_video_cutter);
            default:
                return null;
        }
    }

    private boolean isMultiColorCategoryIcon(int iconRes) {
        return iconRes == R.drawable.ic_apps
                || iconRes == R.drawable.ic_audio
                || iconRes == R.drawable.ic_video
                || iconRes == R.drawable.ic_images
                || iconRes == R.drawable.ic_documents
                || iconRes == R.drawable.ic_downloads
                || iconRes == R.drawable.ic_analyzer;
    }

    private int resolveSoftCategoryTint(String id, int accent) {
        if (FileBrowserActivity.MODE_RECENT.equals(id)) return 0xFFF59E0B;
        if (FileBrowserActivity.MODE_RECYCLE_BIN.equals(id)) return 0xFF10B981;
        if ("TOOL_AUDIO_CUTTER".equals(id)) return 0xFF8B5CF6;
        if ("TOOL_VIDEO_CUTTER".equals(id)) return 0xFFF43F5E;
        return accent;
    }

    private void buildHomeGrid(int theme, int cardBg, int textPrimary, int textSecondary, int accent) {
        gridRowsContainer.removeAllViews();
        subtitleViewsById.clear();
        LayoutInflater inflater = LayoutInflater.from(this);

        StorageUtils.StorageStat sd = StorageUtils.getExternalSdCardStat(this);
        List<StorageUtils.StorageStat> otgVolumes = StorageUtils.getConnectedUsbOtgVolumes(this);
        boolean hasRemovable = sd.available || !otgVolumes.isEmpty();

        List<HomeCategory> ordered = new ArrayList<>();
        for (String key : PrefsManager.getHomeCategoryOrder(this)) {
            if ("sd_card".equals(key)) {
                if (sd.available) {
                    ordered.add(mapIdToHomeCategory(key));
                } else if (!hasRemovable) {
                    ordered.add(new HomeCategory("TOOL_ANALYZER", "ניתוח", R.drawable.ic_analyzer));
                }
                continue;
            }
            HomeCategory hc = mapIdToHomeCategory(key);
            if (hc != null) ordered.add(hc);
        }
        // הוספת אריחי USB OTG אם מחוברים
        int otgInsertIdx = Math.min(2, ordered.size());
        for (StorageUtils.StorageStat usb : otgVolumes) {
            if (usb.root != null) {
                ordered.add(otgInsertIdx++, new HomeCategory(
                        FileBrowserActivity.MODE_USB_OTG,
                        usb.label.isEmpty() ? "כונן USB" : usb.label,
                        R.drawable.ic_usb_otg,
                        usb.root.getAbsolutePath()
                ));
            }
        }

        int visibleLimit = PrefsManager.getHomeVisibleLimit(this);
        if (visibleLimit > 0 && visibleLimit < ordered.size() && !forceShowAllCategories) {
            ordered = new ArrayList<>(ordered.subList(0, visibleLimit));
            btnShowAllCategories.setVisibility(View.VISIBLE);
            btnShowAllCategories.setText("הצג את כל הקטגוריות (" + PrefsManager.getHomeCategoryOrder(this).size() + ")");
            btnShowAllCategories.setTextColor(accent);
        } else if (visibleLimit > 0 && forceShowAllCategories) {
            btnShowAllCategories.setVisibility(View.VISIBLE);
            btnShowAllCategories.setText("צמצם קטגוריות משניות");
            btnShowAllCategories.setTextColor(accent);
        } else {
            btnShowAllCategories.setVisibility(View.GONE);
        }

        DisplayMetrics dm = getResources().getDisplayMetrics();
        float d = dm.density;
        float widthDp = dm.widthPixels / d;
        float heightDp = dm.heightPixels / d;
        boolean compact = widthDp < 330f || heightDp < 560f;
        boolean tablet = Math.min(widthDp, heightDp) >= 600f;
        int cols = widthDp >= 780f ? 5 : (widthDp >= 520f ? 4 : 3);
        boolean showLabels = PrefsManager.isShowHomeCategoryLabels(this);

        int tileHeightPx = ThemeHelper.dpScaled(this, compact ? 94 : (tablet ? 126 : 108));
        int iconSizePx = ThemeHelper.dpScaled(this, showLabels
                ? (compact ? 38 : (tablet ? 54 : 46))
                : (compact ? 46 : (tablet ? 62 : 54)));
        float titleSp = ThemeHelper.spScaled(this, compact ? 10.5f : (tablet ? 13f : 11.5f));
        float subSp = ThemeHelper.spScaled(this, compact ? 9f : (tablet ? 11f : 10f));
        int tileMarginPx = Math.round((compact ? 3.5f : 4.5f) * d);

        for (int i = 0; i < ordered.size(); i += cols) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            rowLayout.setWeightSum((float) cols);

            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rowLp.bottomMargin = Math.round(3f * d);
            rowLayout.setLayoutParams(rowLp);

            for (int c = 0; c < cols; c++) {
                int idx = i + c;
                if (idx >= ordered.size()) {
                    View spacer = new View(this);
                    LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(0, tileHeightPx, 1f);
                    sLp.setMargins(tileMarginPx, tileMarginPx, tileMarginPx, tileMarginPx);
                    rowLayout.addView(spacer, sLp);
                    continue;
                }
                HomeCategory item = ordered.get(idx);
                CardView tile = (CardView) inflater.inflate(R.layout.item_category_tile, rowLayout, false);
                ThemeHelper.styleGlassCardView(this, tile, theme, 18f, false);
                LinearLayout.LayoutParams tileLp = new LinearLayout.LayoutParams(0, tileHeightPx, 1f);
                tileLp.setMargins(tileMarginPx, tileMarginPx, tileMarginPx, tileMarginPx);
                tile.setLayoutParams(tileLp);

                ImageView iv = tile.findViewById(R.id.ivTileIcon);
                TextView tvTitle = tile.findViewById(R.id.tvTileTitle);
                TextView tvSub = tile.findViewById(R.id.tvTileSubtitle);

                ViewGroup.LayoutParams ivLp = iv.getLayoutParams();
                if (ivLp != null) {
                    ivLp.width = iconSizePx;
                    ivLp.height = iconSizePx;
                    iv.setLayoutParams(ivLp);
                }

                iv.setImageResource(item.iconRes);
                if (isMultiColorCategoryIcon(item.iconRes)) {
                    iv.clearColorFilter();
                } else {
                    iv.setColorFilter(resolveSoftCategoryTint(item.id, accent));
                }

                tvTitle.setText(item.titleHe);
                tvTitle.setTextSize(titleSp);
                tvTitle.setTextColor(textPrimary);
                tvTitle.setVisibility(showLabels ? View.VISIBLE : View.GONE);

                String sub = getCategorySubtitle(item.id, item.customDirPath);
                tvSub.setText(sub);
                tvSub.setTextSize(subSp);
                tvSub.setTextColor(textSecondary);
                tvSub.setTextDirection(View.TEXT_DIRECTION_LTR);
                tvSub.setVisibility((showLabels && !sub.isEmpty()) ? View.VISIBLE : View.GONE);
                if (!FileBrowserActivity.MODE_USB_OTG.equals(item.id)) {
                    subtitleViewsById.put(item.id, tvSub);
                }

                tile.setOnClickListener(v -> onCategoryClicked(item));
                rowLayout.addView(tile);
            }
            gridRowsContainer.addView(rowLayout);
        }
    }

    private String getCategorySubtitle(String id, String customPath) {
        if ("TOOL_ANALYZER".equals(id)) {
            return "קבצים כבדים";
        }
        if (FileBrowserActivity.MODE_INTERNAL.equals(id)) {
            StorageUtils.StorageStat st = StorageUtils.getInternalStorageStat();
            return st.available ? StorageUtils.formatSize(st.freeBytes) + " פנוי" : "פנימי";
        }
        if (FileBrowserActivity.MODE_SD_CARD.equals(id)) {
            StorageUtils.StorageStat sd = StorageUtils.getExternalSdCardStat(this);
            return sd.available ? StorageUtils.formatSize(sd.freeBytes) + " פנוי" : "לא מחובר";
        }
        if (FileBrowserActivity.MODE_USB_OTG.equals(id)) {
            if (customPath != null) {
                try {
                    android.os.StatFs stat = new android.os.StatFs(customPath);
                    return StorageUtils.formatSize(stat.getAvailableBytes()) + " פנוי";
                } catch (Throwable ignored) {
                }
            }
            return "USB מחובר";
        }
        if (FileBrowserActivity.MODE_DOWNLOADS.equals(id)) {
            return formatWeightWithCount(cachedSizes.downloadsBytes, cachedSizes.downloadsCount);
        }
        if (FileBrowserActivity.MODE_IMAGES.equals(id)) {
            return formatWeightWithCount(cachedSizes.imagesBytes, cachedSizes.imagesCount);
        }
        if (FileBrowserActivity.MODE_AUDIO.equals(id)) {
            return formatWeightWithCount(cachedSizes.audioBytes, cachedSizes.audioCount);
        }
        if (FileBrowserActivity.MODE_VIDEOS.equals(id)) {
            return formatWeightWithCount(cachedSizes.videosBytes, cachedSizes.videosCount);
        }
        if (FileBrowserActivity.MODE_DOCUMENTS.equals(id)) {
            return formatWeightWithCount(cachedSizes.documentsBytes, cachedSizes.documentsCount);
        }
        if (FileBrowserActivity.MODE_APPS.equals(id)) {
            return formatWeightWithCount(cachedSizes.appsBytes, cachedSizes.appsCount);
        }
        if (FileBrowserActivity.MODE_RECENT.equals(id)) {
            return formatWeightWithCount(cachedSizes.recentBytes, cachedSizes.recentCount);
        }
        if (FileBrowserActivity.MODE_RECYCLE_BIN.equals(id)) {
            return formatWeightWithCount(cachedSizes.recycleBinBytes, cachedSizes.recycleBinCount);
        }
        return "";
    }

    private String formatWeightWithCount(long bytes, int count) {
        return StorageUtils.formatSize(bytes) + " (" + count + ")";
    }

    private void onCategoryClicked(HomeCategory item) {
        if ("TOOL_ANALYZER".equals(item.id)) {
            startActivity(new Intent(this, StorageAnalyzerActivity.class));
            return;
        }
        if ("TOOL_AUDIO_CUTTER".equals(item.id)) {
            startActivity(new Intent(this, AudioCutterActivity.class));
            return;
        }
        if ("TOOL_VIDEO_CUTTER".equals(item.id)) {
            startActivity(new Intent(this, VideoCutterActivity.class));
            return;
        }
        Intent intent = new Intent(this, FileBrowserActivity.class);
        intent.putExtra(FileBrowserActivity.EXTRA_MODE, item.id);
        intent.putExtra(FileBrowserActivity.EXTRA_TITLE, item.titleHe);
        if (item.customDirPath != null) {
            intent.putExtra(FileBrowserActivity.EXTRA_INITIAL_DIR, item.customDirPath);
        }
        startActivity(intent);
    }

    private void ensureStorageAndMediaPermissions() {
        List<String> perms = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.READ_MEDIA_IMAGES);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.READ_MEDIA_VIDEO);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.READ_MEDIA_AUDIO);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }

        if (!perms.isEmpty()) {
            ActivityCompat.requestPermissions(this, perms.toArray(new String[0]), REQ_MEDIA_PERMS);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            ThemedDialogHelper.showCustomDialog(
                    this,
                    "גישה מלאה לעריכת כל הקבצים",
                    "כדי לאפשר פתיחת קבצים, חילוץ, העתקה, העברה ומחיקה באחסון הראשי ובכרטיס ה-SD, אנא אשר הרשאת גישה לכל הקבצים עבור File Manager Pro.",
                    null,
                    "אשר הרשאה",
                    () -> {
                        try {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    Uri.parse("package:" + getPackageName()));
                            startActivity(intent);
                        } catch (Exception e) {
                            startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                        }
                    },
                    "סגור",
                    null,
                    false
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        applyCurrentThemeAndRefresh(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (miniAudioListener != null) {
            AudioPlayerManager.getInstance().removeListener(miniAudioListener);
        }
        bgExecutor.shutdownNow();
    }
}
