package com.aishhatorah.filemanagerpro;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
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

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_MEDIA_PERMS = 1001;

    private final ExecutorService bgExecutor = Executors.newSingleThreadExecutor();
    private final Map<String, TextView> subtitleViewsById = new HashMap<>();
    private StorageUtils.CategoryBytesSummary cachedSizes = new StorageUtils.CategoryBytesSummary();
    private long lastBackPressedMs = 0L;

    private LinearLayout rootContainer;
    private LinearLayout topBar;
    private MaterialButton btnAbout;
    private MaterialButton btnSettings;
    private TextView tvAppTitle;
    private SwipeRefreshLayout swipeRefreshHome;
    private CardView cardStorageAlert;
    private TextView tvAlertMessage;
    private CardView cardStorageSummary;
    private TextView tvInternalSummaryTitle;
    private TextView tvInternalSummaryStats;
    private ProgressBar progressInternal;
    private LinearLayout sdSummaryBlock;
    private TextView tvSdSummaryTitle;
    private TextView tvSdSummaryStats;
    private ProgressBar progressSd;
    private LinearLayout gridRowsContainer;
    private TextView tvPullHint;

    private static class HomeCategory {
        final String id;
        final String titleHe;
        final int iconRes;

        HomeCategory(String id, String titleHe, int iconRes) {
            this.id = id;
            this.titleHe = titleHe;
            this.iconRes = iconRes;
        }
    }

    // סידור מדויק של 4 שורות × 3 עמודות משמאל לימין
    private static final HomeCategory[][] HOME_ROWS_LTR = new HomeCategory[][]{
            // שורה ראשונה משמאל לימין: אחסון ראשי, כרטיס SD, הורדות
            {
                    new HomeCategory(FileBrowserActivity.MODE_INTERNAL, "אחסון ראשי", R.drawable.ic_storage_internal),
                    new HomeCategory(FileBrowserActivity.MODE_SD_CARD, "כרטיס SD", R.drawable.ic_sd_card),
                    new HomeCategory(FileBrowserActivity.MODE_DOWNLOADS, "הורדות", R.drawable.ic_downloads)
            },
            // שורה שנייה משמאל לימין: תמונות, שמע, סירטונים
            {
                    new HomeCategory(FileBrowserActivity.MODE_IMAGES, "תמונות", R.drawable.ic_images),
                    new HomeCategory(FileBrowserActivity.MODE_AUDIO, "שמע", R.drawable.ic_audio),
                    new HomeCategory(FileBrowserActivity.MODE_VIDEOS, "סירטונים", R.drawable.ic_video)
            },
            // שורה שלישית משמאל לימין: מסמכים, אפליקציות, קבצים חדשים
            {
                    new HomeCategory(FileBrowserActivity.MODE_DOCUMENTS, "מסמכים", R.drawable.ic_documents),
                    new HomeCategory(FileBrowserActivity.MODE_APPS, "אפליקציות", R.drawable.ic_apps),
                    new HomeCategory(FileBrowserActivity.MODE_RECENT, "קבצים חדשים", R.drawable.ic_recent)
            },
            // שורה רביעית משמאל לימין: סל מיחזור, חותך אודיו, חותך וידאו
            {
                    new HomeCategory(FileBrowserActivity.MODE_RECYCLE_BIN, "סל מיחזור", R.drawable.ic_recycle_bin),
                    new HomeCategory("TOOL_AUDIO_CUTTER", "חותך אודיו", R.drawable.ic_audio_cutter),
                    new HomeCategory("TOOL_VIDEO_CUTTER", "חותך וידאו", R.drawable.ic_video_cutter)
            }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();
        setupDoubleBackToExit();
        ensureStorageAndMediaPermissions();
        checkPostUpdateConfirmation();

        if (PrefsManager.isAutoCheckUpdates(this)) {
            UpdateChecker.checkForUpdates(this, false);
        }
    }

    // סעיף כ: ביציאה מהאפליקציה — "לחץ שוב כדי לצאת"
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

    // סעיף ו: הודעה מסודרת בסיום עדכון האפליקציה — "File Manager Pro עודכן בהצלחה"
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
                    "File Manager Pro עודכן בהצלחה",
                    "האפליקציה שודרגה בהצלחה לגירסה " + BuildConfig.VERSION_NAME + ".\n\n"
                            + "כלל הקבצים, ההרשאות וההגדרות האישיות שלך נשמרו במלואם וכעת כל היכולות החדשות זמינות לשימושך.",
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

    private void bindViews() {
        rootContainer = findViewById(R.id.rootContainer);
        topBar = findViewById(R.id.topBar);
        btnAbout = findViewById(R.id.btnAbout);
        btnSettings = findViewById(R.id.btnSettings);
        tvAppTitle = findViewById(R.id.tvAppTitle);
        swipeRefreshHome = findViewById(R.id.swipeRefreshHome);
        cardStorageAlert = findViewById(R.id.cardStorageAlert);
        tvAlertMessage = findViewById(R.id.tvAlertMessage);
        cardStorageSummary = findViewById(R.id.cardStorageSummary);
        tvInternalSummaryTitle = findViewById(R.id.tvInternalSummaryTitle);
        tvInternalSummaryStats = findViewById(R.id.tvInternalSummaryStats);
        progressInternal = findViewById(R.id.progressInternal);
        sdSummaryBlock = findViewById(R.id.sdSummaryBlock);
        tvSdSummaryTitle = findViewById(R.id.tvSdSummaryTitle);
        tvSdSummaryStats = findViewById(R.id.tvSdSummaryStats);
        progressSd = findViewById(R.id.progressSd);
        gridRowsContainer = findViewById(R.id.gridRowsContainer);
        tvPullHint = findViewById(R.id.tvPullHint);

        Button btnOpenAnalyzerFromAlert = findViewById(R.id.btnOpenAnalyzerFromAlert);

        // למעלה משמאל: אודות
        btnAbout.setOnClickListener(v -> AboutDialogHelper.show(this));

        // למעלה מימין: הגדרות
        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        btnOpenAnalyzerFromAlert.setOnClickListener(v ->
                startActivity(new Intent(this, StorageAnalyzerActivity.class)));

        // סעיף ז: החלקה מלמעלה למטה — העיגול מסתובב עד לסיום הסריקה האמיתית, ורק בסיומה מופיעה ההודעה "כל הקבצים רועננו בהצלחה"
        swipeRefreshHome.setColorSchemeColors(0xFF1D4ED8, 0xFF0284C7, 0xFF0D9488);
        swipeRefreshHome.setOnRefreshListener(() -> applyCurrentThemeAndRefresh(true));

        miniAudioListener = MiniAudioBarHelper.attach(this, rootContainer);
    }

    private AudioPlayerManager.PlaybackListener miniAudioListener;

    private void applyCurrentThemeAndRefresh(boolean fromUserPullToRefresh) {
        int theme = PrefsManager.getThemeMode(this);
        ThemeHelper.applyWindowStatusBar(this, theme);

        int bg = ThemeHelper.getBgColor(theme);
        int cardBg = ThemeHelper.getCardColor(theme);
        int topBg = ThemeHelper.getTopBarColor(theme);
        int textPrimary = ThemeHelper.getPrimaryTextColor(theme);
        int textSecondary = ThemeHelper.getSecondaryTextColor(theme);
        int accent = ThemeHelper.getAccentColor(theme);

        rootContainer.setBackgroundColor(bg);
        topBar.setBackgroundColor(topBg);
        tvAppTitle.setTextColor(textPrimary);
        tvPullHint.setTextColor(textSecondary);

        ColorStateList accentState = ColorStateList.valueOf(accent);
        btnAbout.setTextColor(accent);
        btnAbout.setIconTint(accentState);
        btnAbout.setStrokeColor(accentState);

        btnSettings.setTextColor(accent);
        btnSettings.setIconTint(accentState);
        btnSettings.setStrokeColor(accentState);

        cardStorageSummary.setCardBackgroundColor(cardBg);
        tvInternalSummaryTitle.setTextColor(textPrimary);
        tvInternalSummaryStats.setTextColor(textSecondary);
        tvSdSummaryTitle.setTextColor(textPrimary);
        tvSdSummaryStats.setTextColor(textSecondary);

        refreshStorageSummaryAnd96Alert();
        buildHomeGrid(cardBg, textPrimary, textSecondary);
        if (miniAudioListener != null) {
            miniAudioListener.onPlaybackStateChanged();
        }
        refreshCategoryVolumesAsync(fromUserPullToRefresh);
    }

    private void refreshCategoryVolumesAsync(final boolean notifyWhenScanFinished) {
        final boolean showHidden = PrefsManager.isShowHidden(this);
        bgExecutor.execute(() -> {
            StorageUtils.CategoryBytesSummary fresh = StorageUtils.computeCategorySizes(this, showHidden);
            runOnUiThread(() -> {
                cachedSizes = fresh;
                for (Map.Entry<String, TextView> entry : subtitleViewsById.entrySet()) {
                    String sub = getCategorySubtitle(entry.getKey());
                    TextView tv = entry.getValue();
                    tv.setText(sub);
                    tv.setVisibility(sub.isEmpty() ? View.GONE : View.VISIBLE);
                }
                if (notifyWhenScanFinished) {
                    swipeRefreshHome.setRefreshing(false);
                    Toast.makeText(this, "כל הקבצים רועננו בהצלחה", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void refreshStorageSummaryAnd96Alert() {
        StorageUtils.StorageStat internal = StorageUtils.getInternalStorageStat();
        if (internal.available) {
            int pct = (int) Math.round(internal.usedPercent);
            progressInternal.setProgress(pct);
            tvInternalSummaryStats.setText(String.format(Locale.US, "%s מתוך %s (%.1f%%)",
                    StorageUtils.formatSize(internal.usedBytes),
                    StorageUtils.formatSize(internal.totalBytes),
                    internal.usedPercent));
        }

        StorageUtils.StorageStat sd = StorageUtils.getExternalSdCardStat(this);
        if (sd.available) {
            sdSummaryBlock.setVisibility(View.VISIBLE);
            int sdPct = (int) Math.round(sd.usedPercent);
            progressSd.setProgress(sdPct);
            tvSdSummaryStats.setText(String.format(Locale.US, "%s מתוך %s (%.1f%%)",
                    StorageUtils.formatSize(sd.usedBytes),
                    StorageUtils.formatSize(sd.totalBytes),
                    sd.usedPercent));
        } else {
            sdSummaryBlock.setVisibility(View.VISIBLE);
            progressSd.setProgress(0);
            tvSdSummaryStats.setText("לא זוהה כרטיס SD חיצוני");
        }

        String alertMsg = StorageUtils.checkAndNotify96Threshold(this);
        if (alertMsg != null) {
            cardStorageAlert.setVisibility(View.VISIBLE);
            tvAlertMessage.setText(alertMsg);
        } else {
            cardStorageAlert.setVisibility(View.GONE);
        }
    }

    private void buildHomeGrid(int cardBg, int textPrimary, int textSecondary) {
        gridRowsContainer.removeAllViews();
        subtitleViewsById.clear();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (HomeCategory[] rowItems : HOME_ROWS_LTR) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            rowLayout.setWeightSum(3f);

            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            rowLp.bottomMargin = 4;
            rowLayout.setLayoutParams(rowLp);

            for (HomeCategory item : rowItems) {
                CardView tile = (CardView) inflater.inflate(R.layout.item_category_tile, rowLayout, false);
                tile.setCardBackgroundColor(cardBg);

                ImageView iv = tile.findViewById(R.id.ivTileIcon);
                TextView tvTitle = tile.findViewById(R.id.tvTileTitle);
                TextView tvSub = tile.findViewById(R.id.tvTileSubtitle);

                iv.setImageResource(item.iconRes);
                tvTitle.setText(item.titleHe);
                tvTitle.setTextColor(textPrimary);

                String sub = getCategorySubtitle(item.id);
                tvSub.setText(sub);
                tvSub.setTextColor(textSecondary);
                tvSub.setTextDirection(View.TEXT_DIRECTION_LTR);
                tvSub.setVisibility(sub.isEmpty() ? View.GONE : View.VISIBLE);
                subtitleViewsById.put(item.id, tvSub);

                tile.setOnClickListener(v -> onCategoryClicked(item));
                rowLayout.addView(tile);
            }

            gridRowsContainer.addView(rowLayout);
        }
    }

    // סעיפים ה + ו: משקל הקבצים + מספר הקבצים בסוגריים (למעט אחסון ראשי, כרטיס SD, חותך אודיו וחותך וידאו), והסרת המילה "מקצועי" באייקוני החותכים
    private String getCategorySubtitle(String id) {
        if (FileBrowserActivity.MODE_INTERNAL.equals(id)) {
            StorageUtils.StorageStat st = StorageUtils.getInternalStorageStat();
            return st.available ? StorageUtils.formatSize(st.freeBytes) + " פנוי" : "פנימי";
        }
        if (FileBrowserActivity.MODE_SD_CARD.equals(id)) {
            StorageUtils.StorageStat sd = StorageUtils.getExternalSdCardStat(this);
            return sd.available ? StorageUtils.formatSize(sd.freeBytes) + " פנוי" : "לא מחובר";
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
        // סעיף ו: ללא "חותך אודיו מקצועי" וללא "חותך וידאו מקצועי" — הכותרת בלבד מציגה "חותך אודיו" ו-"חותך וידאו"
        return "";
    }

    private String formatWeightWithCount(long bytes, int count) {
        return StorageUtils.formatSize(bytes) + " (" + count + ")";
    }

    private void onCategoryClicked(HomeCategory item) {
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
