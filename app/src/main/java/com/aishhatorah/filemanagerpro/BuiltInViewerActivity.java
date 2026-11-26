package com.aishhatorah.filemanagerpro;

import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.MimeTypeMap;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.view.WindowCompat;

import com.aishhatorah.filemanagerpro.viewer.ArchiveViewerController;
import com.aishhatorah.filemanagerpro.viewer.AudioViewerController;
import com.aishhatorah.filemanagerpro.viewer.ImageViewerController;
import com.aishhatorah.filemanagerpro.viewer.OfficeViewerController;
import com.aishhatorah.filemanagerpro.viewer.PdfViewerController;
import com.aishhatorah.filemanagerpro.viewer.VideoViewerController;
import com.aishhatorah.filemanagerpro.viewer.ViewerHost;
import com.aishhatorah.filemanagerpro.viewer.ViewerUiHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * מציג ונגן המדיה המובנה של File Manager Pro:
 * נתב מרכזי רזה ומודולרי המנהל תמונות/GIF/SVG, וידאו Edge-to-Edge, שמע, PDF,
 * ארכיונים ומסמכי Office דרך בקרים ייעודיים בתיקיית viewer.
 */
public class BuiltInViewerActivity extends AppCompatActivity implements ViewerHost {

    public static final String EXTRA_FILE_PATH = "extra_file_path";
    public static final String EXTRA_FROM_NOTIFICATION = "extra_from_notification";
    public static final String EXTRA_FROM_MINI_AUDIO_BAR = "extra_from_mini_audio_bar";
    public static final String EXTRA_FORCE_TYPE = "extra_force_type";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private File targetFile;
    private File externalCopiedCacheFile = null;
    private boolean openedFromNotification = false;
    private boolean openedFromMiniAudioBar = false;
    private String forceType = null;

    private TextView tvMarqueeTitle;
    private TextView tvFolderTrackCounter;
    private String lastHeaderTitleName = null;

    private boolean isImageMode = false;
    private boolean isAudioMode = false;
    private boolean isVideoMode = false;
    private boolean isPdfMode = false;
    private boolean isArchiveMode = false;
    private boolean mediaControlsHidden = false;
    private boolean isVideoLocked = false;
    private boolean isInPipMode = false;
    private int lastVideoTopInsetPx = 0;

    private FrameLayout activeMediaStageRoot = null;
    private FrameLayout activeMediaViewport = null;
    private View activeMediaTopOverlay = null;
    private View activeMediaBottomPanel = null;
    private View activeImagePrevFloat = null;
    private View activeImageNextFloat = null;
    private View activeVideoCenterOverlay = null;
    private View activeVideoLockFloatingBtn = null;
    private ZoomableImageView activeZoomImageView = null;
    private Runnable archiveNavigateUpHandler = null;

    private ImageViewerController imageController = null;
    private PdfViewerController pdfController = null;
    private AudioViewerController audioController = null;
    private VideoViewerController videoController = null;
    private ArchiveViewerController archiveController = null;
    private OfficeViewerController officeController = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getWindow() != null) {
            getWindow().setFormat(PixelFormat.RGBA_8888);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                proceedCloseOrNavigateBack();
            }
        });

        handleIncomingIntent(getIntent(), true);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent, false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isImageMode || isVideoMode || isPdfMode) {
            applyMediaImmersiveAndLayoutState();
        }
    }

    private void handleIncomingIntent(Intent intent, boolean isInitialCreate) {
        if (intent == null) {
            if (isInitialCreate) finish();
            return;
        }
        openedFromNotification = intent.getBooleanExtra(EXTRA_FROM_NOTIFICATION, false);
        openedFromMiniAudioBar = intent.getBooleanExtra(EXTRA_FROM_MINI_AUDIO_BAR, false);
        forceType = intent.getStringExtra(EXTRA_FORCE_TYPE);
        String path = intent.getStringExtra(EXTRA_FILE_PATH);
        if (path != null && !path.isEmpty()) {
            File f = new File(path);
            if (!f.exists()) {
                if (isInitialCreate) {
                    Toast.makeText(this, "הקובץ אינו קיים", Toast.LENGTH_SHORT).show();
                    finish();
                }
                return;
            }
            if (isInitialCreate || targetFile == null || !f.getAbsolutePath().equals(targetFile.getAbsolutePath())) {
                openResolvedTargetFile(f);
            }
            return;
        }

        Uri dataUri = intent.getData();
        if (dataUri == null && Intent.ACTION_SEND.equals(intent.getAction())) {
            dataUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        }
        if (dataUri == null) {
            if (isInitialCreate) finish();
            return;
        }

        File direct = resolveDirectFileFromUri(dataUri);
        if (direct != null && direct.exists() && direct.canRead()) {
            openResolvedTargetFile(direct);
            return;
        }

        if (isInitialCreate) {
            LinearLayout loadingRoot = new LinearLayout(this);
            loadingRoot.setGravity(Gravity.CENTER);
            loadingRoot.setBackgroundColor(Color.BLACK);
            TextView tvWait = new TextView(this);
            tvWait.setText("פותח קובץ...");
            tvWait.setTextColor(Color.WHITE);
            tvWait.setTextSize(15f);
            loadingRoot.addView(tvWait);
            setContentView(loadingRoot);
        }

        final Uri copyUri = dataUri;
        executor.execute(() -> {
            File copied = copyContentUriToCache(copyUri);
            uiHandler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (copied != null && copied.exists()) {
                    cleanupExternalCopiedCache();
                    externalCopiedCacheFile = copied;
                    openResolvedTargetFile(copied);
                } else {
                    Toast.makeText(this, "לא ניתן לפתוח קובץ חיצוני זה", Toast.LENGTH_SHORT).show();
                    if (isInitialCreate) finish();
                }
            });
        });
    }

    private void openResolvedTargetFile(File file) {
        if (file == null || !file.exists()) {
            finish();
            return;
        }
        String lower = file.getName().toLowerCase(Locale.ROOT);
        if (FileOpenWithHelper.ACTION_INTERNAL_CODE.equals(forceType)
                || (forceType == null && !MediaFormatHelper.isImageFile(lower)
                && !MediaFormatHelper.isAudioFile(lower)
                && !MediaFormatHelper.isVideoFile(lower)
                && !MediaFormatHelper.isPdfFile(lower)
                && !MediaFormatHelper.isZipOrArchiveFile(lower)
                && !MediaFormatHelper.isInstallableAppPackage(lower)
                && !ViewerUiHelper.endsWithAny(lower, ".docx", ".xlsx", ".pptx", ".odt", ".ods", ".odp")
                && (MediaFormatHelper.isTextFile(lower) || MediaFormatHelper.isCodeOrSyntaxFile(lower)))) {
            Intent codeIntent = new Intent(this, CodeEditorActivity.class);
            codeIntent.putExtra(CodeEditorActivity.EXTRA_FILE_PATH, file.getAbsolutePath());
            startActivity(codeIntent);
            finish();
            return;
        }
        targetFile = file;
        teardownActiveControllers();
        buildViewerUiForCurrentFile();
    }

    private void teardownActiveControllers() {
        if (audioController != null) {
            audioController.destroy();
            audioController = null;
        }
        if (videoController != null) {
            videoController.destroy();
            videoController = null;
        }
        if (pdfController != null) {
            pdfController.destroy();
            pdfController = null;
        }
        if (imageController != null) {
            imageController.destroy();
            imageController = null;
        }
        archiveController = null;
        officeController = null;
    }

    private File resolveDirectFileFromUri(Uri uri) {
        if (uri == null) return null;
        if ("file".equalsIgnoreCase(uri.getScheme()) && uri.getPath() != null) {
            return new File(uri.getPath());
        }
        try {
            String path = uri.getPath();
            if (path != null) {
                int idx = path.indexOf("/storage/");
                if (idx >= 0) {
                    File candidate = new File(path.substring(idx));
                    if (candidate.exists() && candidate.canRead()) return candidate;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private File copyContentUriToCache(Uri uri) {
        try {
            String displayName = null;
            try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    displayName = c.getString(0);
                }
            } catch (Throwable ignored) {
            }
            if (displayName == null || displayName.trim().isEmpty()) {
                displayName = uri.getLastPathSegment();
                if (displayName != null && displayName.contains("/")) {
                    displayName = displayName.substring(displayName.lastIndexOf('/') + 1);
                }
            }
            if (displayName == null || displayName.trim().isEmpty()) {
                displayName = "opened_file";
            }
            displayName = displayName.replaceAll("[\\\\/:*?\"<>|]", "_");
            if (!displayName.contains(".")) {
                String mime = null;
                try { mime = getContentResolver().getType(uri); } catch (Throwable ignored) {}
                String ext = mime != null ? MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) : null;
                if (ext != null && !ext.isEmpty()) {
                    displayName = displayName + "." + ext;
                }
            }
            File dir = new File(getCacheDir(), "open_with");
            if (!dir.exists()) dir.mkdirs();
            File dest = new File(dir, System.currentTimeMillis() + "_" + displayName);
            try (InputStream in = getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(dest)) {
                if (in == null) return null;
                byte[] buf = new byte[16 * 1024];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
                out.flush();
            }
            return dest;
        } catch (Throwable t) {
            return null;
        }
    }

    private void cleanupExternalCopiedCache() {
        if (externalCopiedCacheFile != null && externalCopiedCacheFile.exists()) {
            try { externalCopiedCacheFile.delete(); } catch (Throwable ignored) {}
        }
        externalCopiedCacheFile = null;
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        boolean landscapeNow = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE;
        if (isVideoMode && videoController != null) {
            videoController.onConfigurationChanged(landscapeNow);
        } else if (isImageMode) {
            applyMediaImmersiveAndLayoutState();
            if (activeZoomImageView != null) {
                activeZoomImageView.post(activeZoomImageView::fitImageToCenter);
            }
        } else if (isPdfMode) {
            applyMediaImmersiveAndLayoutState();
            if (activeZoomImageView != null) {
                activeZoomImageView.post(activeZoomImageView::fitImageToCenter);
            }
        } else if (isAudioMode) {
            if (audioController != null) {
                audioController.detachCallbacksOnly();
            }
            buildViewerUiForCurrentFile();
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, @NonNull Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (isVideoMode && videoController != null) {
            videoController.onPictureInPictureModeChanged(isInPictureInPictureMode);
        }
    }

    private void buildViewerUiForCurrentFile() {
        activeMediaStageRoot = null;
        activeMediaViewport = null;
        activeMediaTopOverlay = null;
        activeMediaBottomPanel = null;
        activeImagePrevFloat = null;
        activeImageNextFloat = null;
        activeVideoCenterOverlay = null;
        activeVideoLockFloatingBtn = null;
        activeZoomImageView = null;
        archiveNavigateUpHandler = null;
        tvMarqueeTitle = null;
        tvFolderTrackCounter = null;
        lastHeaderTitleName = null;
        isVideoLocked = false;
        isInPipMode = false;
        lastVideoTopInsetPx = 0;

        int theme = PrefsManager.getThemeMode(this);
        ThemeHelper.applyWindowStatusBar(this, theme);
        int bgColor = ThemeHelper.getBgColor(this, theme);
        int cardColor = ThemeHelper.getCardColor(this, theme);
        int textPrimary = ThemeHelper.getPrimaryTextColor(this, theme);
        int textSecondary = ThemeHelper.getSecondaryTextColor(this, theme);
        int accent = ThemeHelper.getAccentColor(this, theme);

        String lower = targetFile.getName().toLowerCase(Locale.ROOT);
        if (FileOpenWithHelper.ACTION_INTERNAL_IMAGE.equals(forceType)) {
            isImageMode = true;
            isAudioMode = false;
            isVideoMode = false;
            isPdfMode = false;
        } else if (FileOpenWithHelper.ACTION_INTERNAL_AUDIO.equals(forceType)) {
            isImageMode = false;
            isAudioMode = true;
            isVideoMode = false;
            isPdfMode = false;
        } else if (FileOpenWithHelper.ACTION_INTERNAL_VIDEO.equals(forceType)) {
            isImageMode = false;
            isAudioMode = false;
            isVideoMode = true;
            isPdfMode = false;
        } else if (FileOpenWithHelper.ACTION_INTERNAL_ARCHIVE.equals(forceType)) {
            isImageMode = false;
            isAudioMode = false;
            isVideoMode = false;
            isPdfMode = false;
        } else {
            isImageMode = MediaFormatHelper.isImageFile(lower);
            isAudioMode = !isImageMode && MediaFormatHelper.isAudioFile(lower);
            isVideoMode = !isImageMode && !isAudioMode && MediaFormatHelper.isVideoFile(lower);
            isPdfMode = !isImageMode && !isAudioMode && !isVideoMode && MediaFormatHelper.isPdfFile(lower);
        }

        if (isImageMode) {
            imageController = new ImageViewerController(this);
            imageController.render(theme);
            return;
        }

        if (isPdfMode) {
            pdfController = new PdfViewerController(this);
            pdfController.render(theme);
            return;
        }

        boolean isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        if (isVideoMode) {
            videoController = new VideoViewerController(this);
            LinearLayout videoTopBar = buildTopBar(theme, textPrimary, textSecondary, isLandscape, true);
            videoController.render(theme, videoTopBar);
            return;
        }

        if (isAudioMode) {
            audioController = new AudioViewerController(this);
            ImageButton[] audioTopRefs = new ImageButton[2];
            LinearLayout audioTopBar = buildAudioOrDocTopBar(theme, textPrimary, textSecondary, isLandscape, true, audioTopRefs);
            audioController.render(audioTopBar, audioTopRefs[0], audioTopRefs[1],
                    bgColor, textPrimary, textSecondary, accent, theme, isLandscape);
            return;
        }

        if (getWindow() != null) {
            WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        }
        ViewerUiHelper.setSystemBarsFullscreen(this, false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(bgColor);

        LinearLayout docTopBar = buildAudioOrDocTopBar(theme, textPrimary, textSecondary, isLandscape, false, null);
        root.addView(docTopBar);

        LinearLayout contentArea = new LinearLayout(this);
        contentArea.setOrientation(LinearLayout.VERTICAL);
        contentArea.setGravity(Gravity.CENTER);
        int areaPad = ViewerUiHelper.dp(this, isLandscape ? 8 : 12);
        contentArea.setPadding(areaPad, areaPad, areaPad, areaPad);
        root.addView(contentArea, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
        if (FileOpenWithHelper.ACTION_INTERNAL_ARCHIVE.equals(forceType)
                || MediaFormatHelper.isZipOrArchiveFile(lower)
                || MediaFormatHelper.isInstallableAppPackage(lower)) {
            archiveController = new ArchiveViewerController(this);
            archiveController.render(contentArea, cardColor, textPrimary, textSecondary, accent, theme);
        } else if (ViewerUiHelper.endsWithAny(lower, ".docx", ".xlsx", ".pptx", ".odt", ".ods", ".odp")) {
            officeController = new OfficeViewerController(this);
            officeController.render(contentArea, cardColor, textPrimary, textSecondary, theme);
        } else {
            Intent codeIntent = new Intent(this, CodeEditorActivity.class);
            codeIntent.putExtra(CodeEditorActivity.EXTRA_FILE_PATH, targetFile.getAbsolutePath());
            startActivity(codeIntent);
            finish();
        }
    }

    private LinearLayout buildTopBar(int theme, int textPrimary, int textSecondary, boolean isLandscape, boolean forVideo) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setClickable(true);
        int vPad = ViewerUiHelper.dp(this, isLandscape ? 6 : 8);
        bar.setPadding(ViewerUiHelper.dp(this, 10), vPad, ViewerUiHelper.dp(this, 10), vPad);

        int effectiveText = forVideo ? Color.WHITE : textPrimary;
        int effectiveSub = forVideo ? Color.parseColor("#CBD5E1") : textSecondary;

        ImageButton btnBackIcon = ViewerUiHelper.createCleanBorderlessIconButton(
                this, R.drawable.ic_arrow_back, ViewerUiHelper.dp(this, 42), effectiveText);
        btnBackIcon.setContentDescription("חזרה");
        btnBackIcon.setOnClickListener(v -> proceedCloseOrNavigateBack());
        bar.addView(btnBackIcon);

        LinearLayout centerTitleCol = new LinearLayout(this);
        centerTitleCol.setOrientation(LinearLayout.VERTICAL);
        centerTitleCol.setGravity(Gravity.CENTER);

        tvMarqueeTitle = new TextView(this);
        lastHeaderTitleName = targetFile.getName();
        tvMarqueeTitle.setText(lastHeaderTitleName);
        tvMarqueeTitle.setTextSize(ThemeHelper.spScaled(this, isLandscape ? 13.5f : 14.5f));
        tvMarqueeTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvMarqueeTitle.setTextColor(effectiveText);
        tvMarqueeTitle.setSingleLine(false);
        tvMarqueeTitle.setMaxLines(2);
        tvMarqueeTitle.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        tvMarqueeTitle.setTextDirection(View.TEXT_DIRECTION_LOCALE);
        tvMarqueeTitle.setGravity(Gravity.CENTER);
        if (forVideo) {
            tvMarqueeTitle.setShadowLayer(4f, 0f, 1.5f, Color.argb(200, 0, 0, 0));
        }
        centerTitleCol.addView(tvMarqueeTitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        tvFolderTrackCounter = new TextView(this);
        tvFolderTrackCounter.setTextSize(11.5f);
        tvFolderTrackCounter.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tvFolderTrackCounter.setTextColor(effectiveSub);
        tvFolderTrackCounter.setTextDirection(View.TEXT_DIRECTION_LTR);
        tvFolderTrackCounter.setGravity(Gravity.CENTER);
        tvFolderTrackCounter.setVisibility(View.VISIBLE);
        LinearLayout.LayoutParams cntLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cntLp.topMargin = ViewerUiHelper.dp(this, 2);
        centerTitleCol.addView(tvFolderTrackCounter, cntLp);

        LinearLayout.LayoutParams titleColLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleColLp.setMarginStart(ViewerUiHelper.dp(this, 8));
        titleColLp.setMarginEnd(ViewerUiHelper.dp(this, 8));
        bar.addView(centerTitleCol, titleColLp);

        if (forVideo) {
            ImageButton btnPlus = ViewerUiHelper.createCleanBorderlessIconButton(
                    this, R.drawable.ic_add, ViewerUiHelper.dp(this, 42), Color.WHITE);
            btnPlus.setContentDescription("כלים ותמונה בתוך תמונה");
            btnPlus.setOnClickListener(v -> {
                if (videoController != null) videoController.showVideoPlusPopup(v, theme);
            });
            bar.addView(btnPlus);

            ImageButton btnMenu = ViewerUiHelper.createCleanBorderlessIconButton(
                    this, R.drawable.ic_menu_hamburger, ViewerUiHelper.dp(this, 42), Color.WHITE);
            btnMenu.setContentDescription("אפשרויות");
            btnMenu.setOnClickListener(v -> {
                if (videoController != null) videoController.showVideoSettingsDialog(v, theme);
            });
            bar.addView(btnMenu);
        }

        return bar;
    }

    private LinearLayout buildAudioOrDocTopBar(int theme, int textPrimary, int textSecondary,
                                               boolean isLandscape, boolean forAudio, ImageButton[] outAudioRefs) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        if (forAudio) {
            bar.setBackgroundColor(Color.TRANSPARENT);
        } else {
            ThemeHelper.applyCurvedHeaderStyle(this, bar, theme);
        }
        int vPad = ViewerUiHelper.dp(this, isLandscape ? 6 : 8);
        bar.setPadding(ViewerUiHelper.dp(this, 12), vPad, ViewerUiHelper.dp(this, 12), vPad);

        ImageButton btnBackIcon = ViewerUiHelper.createCleanBorderlessIconButton(
                this, R.drawable.ic_arrow_back, ViewerUiHelper.dp(this, 42), textPrimary);
        btnBackIcon.setContentDescription("חזרה");
        btnBackIcon.setOnClickListener(v -> proceedCloseOrNavigateBack());
        bar.addView(btnBackIcon);
        if (forAudio && outAudioRefs != null) {
            outAudioRefs[0] = btnBackIcon;
        }

        LinearLayout centerTitleCol = new LinearLayout(this);
        centerTitleCol.setOrientation(LinearLayout.VERTICAL);
        centerTitleCol.setGravity(Gravity.CENTER);

        lastHeaderTitleName = targetFile.getName();
        if (forAudio) {
            tvMarqueeTitle = new TextView(this);
            tvMarqueeTitle.setText(lastHeaderTitleName);
            tvMarqueeTitle.setTextSize(ThemeHelper.spScaled(this, isLandscape ? 13.5f : 14.5f));
            tvMarqueeTitle.setTypeface(Typeface.DEFAULT_BOLD);
            tvMarqueeTitle.setTextColor(textPrimary);
            tvMarqueeTitle.setSingleLine(false);
            tvMarqueeTitle.setMaxLines(2);
            tvMarqueeTitle.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            tvMarqueeTitle.setTextDirection(View.TEXT_DIRECTION_LOCALE);
            tvMarqueeTitle.setGravity(Gravity.CENTER);
        } else {
            tvMarqueeTitle = new AppCompatTextView(this) {
                @Override
                public boolean isFocused() {
                    return true;
                }
            };
            tvMarqueeTitle.setText(lastHeaderTitleName);
            tvMarqueeTitle.setTextSize(ThemeHelper.spScaled(this, isLandscape ? 13.5f : 14.5f));
            tvMarqueeTitle.setTypeface(Typeface.DEFAULT_BOLD);
            tvMarqueeTitle.setTextColor(textPrimary);
            tvMarqueeTitle.setSingleLine(true);
            tvMarqueeTitle.setEllipsize(TextUtils.TruncateAt.MARQUEE);
            tvMarqueeTitle.setMarqueeRepeatLimit(-1);
            tvMarqueeTitle.setHorizontallyScrolling(true);
            tvMarqueeTitle.setSelected(true);
            tvMarqueeTitle.setGravity(Gravity.CENTER_VERTICAL);
        }
        centerTitleCol.addView(tvMarqueeTitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        tvFolderTrackCounter = new TextView(this);
        tvFolderTrackCounter.setTextSize(11.5f);
        tvFolderTrackCounter.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tvFolderTrackCounter.setTextColor(textSecondary);
        tvFolderTrackCounter.setTextDirection(View.TEXT_DIRECTION_LTR);
        tvFolderTrackCounter.setGravity(Gravity.CENTER);
        tvFolderTrackCounter.setVisibility(forAudio ? View.VISIBLE : View.GONE);
        LinearLayout.LayoutParams cntLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cntLp.topMargin = ViewerUiHelper.dp(this, 2);
        centerTitleCol.addView(tvFolderTrackCounter, cntLp);

        LinearLayout.LayoutParams titleColLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleColLp.setMarginStart(ViewerUiHelper.dp(this, 8));
        titleColLp.setMarginEnd(ViewerUiHelper.dp(this, 8));
        bar.addView(centerTitleCol, titleColLp);

        ImageButton btnShareIcon = ViewerUiHelper.createCleanBorderlessIconButton(
                this, R.drawable.ic_share, ViewerUiHelper.dp(this, 42), textPrimary);
        btnShareIcon.setContentDescription("שיתוף");
        btnShareIcon.setOnClickListener(v -> shareCurrentFile());
        bar.addView(btnShareIcon);
        if (forAudio && outAudioRefs != null) {
            outAudioRefs[1] = btnShareIcon;
        }

        return bar;
    }

    // ==================== מימוש ViewerHost ====================

    @Override
    public AppCompatActivity getActivity() {
        return this;
    }

    @Override
    public ExecutorService getExecutor() {
        return executor;
    }

    @Override
    public Handler getUiHandler() {
        return uiHandler;
    }

    @Override
    public File getTargetFile() {
        return targetFile;
    }

    @Override
    public void setTargetFile(File file) {
        this.targetFile = file;
    }

    @Override
    public TextView getMarqueeTitleView() {
        return tvMarqueeTitle;
    }

    @Override
    public TextView getFolderTrackCounterView() {
        return tvFolderTrackCounter;
    }

    @Override
    public void setHeaderViews(TextView marqueeTitle, TextView trackCounter, String initialName) {
        this.tvMarqueeTitle = marqueeTitle;
        this.tvFolderTrackCounter = trackCounter;
        this.lastHeaderTitleName = initialName;
    }

    @Override
    public void updateMarqueeHeaderName(String name) {
        if (tvMarqueeTitle != null && name != null) {
            if (name.equals(lastHeaderTitleName)) return;
            lastHeaderTitleName = name;
            tvMarqueeTitle.setText(name);
            if (!isVideoMode && !isAudioMode) {
                tvMarqueeTitle.setSelected(true);
            }
        }
    }

    @Override
    public void setArchiveNavigateUpHandler(Runnable handler) {
        this.archiveNavigateUpHandler = handler;
    }

    @Override
    public void setArchiveMode(boolean active) {
        this.isArchiveMode = active;
    }

    @Override
    public void registerMediaChrome(
            boolean imageMode,
            boolean videoMode,
            boolean pdfMode,
            FrameLayout stageRoot,
            FrameLayout viewport,
            View topOverlay,
            View bottomPanel,
            View prevFloat,
            View nextFloat,
            View centerOverlay,
            View lockFloatingBtn,
            ZoomableImageView zoomView
    ) {
        this.isImageMode = imageMode;
        this.isVideoMode = videoMode;
        this.isPdfMode = pdfMode;
        this.activeMediaStageRoot = stageRoot;
        this.activeMediaViewport = viewport;
        this.activeMediaTopOverlay = topOverlay;
        this.activeMediaBottomPanel = bottomPanel;
        this.activeImagePrevFloat = prevFloat;
        this.activeImageNextFloat = nextFloat;
        this.activeVideoCenterOverlay = centerOverlay;
        this.activeVideoLockFloatingBtn = lockFloatingBtn;
        this.activeZoomImageView = zoomView;
    }

    @Override
    public boolean isMediaControlsHidden() {
        return mediaControlsHidden;
    }

    @Override
    public void setMediaControlsHidden(boolean hidden) {
        this.mediaControlsHidden = hidden;
    }

    @Override
    public void toggleMediaControlsVisibility() {
        if (isVideoMode && isVideoLocked) {
            return;
        }
        mediaControlsHidden = !mediaControlsHidden;
        applyMediaImmersiveAndLayoutState();
    }

    @Override
    public void applyMediaImmersiveAndLayoutState() {
        if (!isImageMode && !isVideoMode && !isPdfMode) {
            ViewerUiHelper.setSystemBarsFullscreen(this, false);
            return;
        }
        boolean isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        if (isVideoMode && (isVideoLocked || isInPipMode)) {
            mediaControlsHidden = true;
        }
        boolean wantFullscreenSystemBars = mediaControlsHidden || (isVideoMode && isLandscape);
        ViewerUiHelper.setSystemBarsFullscreen(this, wantFullscreenSystemBars);

        if (activeMediaTopOverlay != null) {
            activeMediaTopOverlay.setVisibility((mediaControlsHidden || isInPipMode) ? View.GONE : View.VISIBLE);
        }
        if (activeMediaBottomPanel != null) {
            activeMediaBottomPanel.setVisibility((mediaControlsHidden || isInPipMode) ? View.GONE : View.VISIBLE);
        }

        if (isImageMode) {
            int count = imageController != null ? imageController.getPlaylistSize() : 0;
            boolean imageNavGestures = PrefsManager.isGestureImageTapNav(this) || PrefsManager.isGestureImageSwipeNav(this);
            if (activeImagePrevFloat != null) {
                activeImagePrevFloat.setVisibility((!mediaControlsHidden && !imageNavGestures && count > 1) ? View.VISIBLE : View.GONE);
            }
            if (activeImageNextFloat != null) {
                activeImageNextFloat.setVisibility((!mediaControlsHidden && !imageNavGestures && count > 1) ? View.VISIBLE : View.GONE);
            }
        }

        if (isVideoMode) {
            boolean awaiting = videoController != null && videoController.isAwaitingInitialVideoPlay();
            if (activeVideoCenterOverlay != null) {
                boolean showCenter = !isInPipMode && !isVideoLocked && (!mediaControlsHidden || awaiting);
                activeVideoCenterOverlay.setVisibility(showCenter ? View.VISIBLE : View.GONE);
            }
            if (activeVideoLockFloatingBtn != null) {
                activeVideoLockFloatingBtn.setVisibility((isVideoLocked && !isInPipMode) ? View.VISIBLE : View.GONE);
            }
        }

        if (activeMediaStageRoot != null) {
            activeMediaStageRoot.setBackgroundColor(Color.BLACK);
        }

        if (activeMediaViewport != null) {
            FrameLayout.LayoutParams vpLp = (FrameLayout.LayoutParams) activeMediaViewport.getLayoutParams();
            if (vpLp != null && (vpLp.leftMargin != 0 || vpLp.topMargin != 0 || vpLp.rightMargin != 0 || vpLp.bottomMargin != 0)) {
                vpLp.setMargins(0, 0, 0, 0);
                activeMediaViewport.setLayoutParams(vpLp);
            }
        }

        if (isImageMode && activeZoomImageView != null) {
            activeZoomImageView.setPinchZoomEnabled(PrefsManager.isGesturePinchZoom(this));
            activeZoomImageView.setHorizontalSwipeEnabled(PrefsManager.isGestureImageSwipeNav(this));
            activeZoomImageView.post(activeZoomImageView::fitImageToCenter);
        } else if (isPdfMode && activeZoomImageView != null) {
            activeZoomImageView.setPinchZoomEnabled(PrefsManager.isGesturePinchZoom(this));
            activeZoomImageView.setHorizontalSwipeEnabled(PrefsManager.isGesturePdfSwipe(this));
            activeZoomImageView.setVerticalSwipeEnabled(PrefsManager.isGesturePdfSwipe(this));
        }
    }

    @Override
    public boolean isVideoLocked() {
        return isVideoLocked;
    }

    @Override
    public void setVideoLocked(boolean locked) {
        this.isVideoLocked = locked;
    }

    @Override
    public boolean isInPipMode() {
        return isInPipMode;
    }

    @Override
    public void setInPipMode(boolean inPip) {
        this.isInPipMode = inPip;
    }

    @Override
    public int getLastVideoTopInsetPx() {
        return lastVideoTopInsetPx;
    }

    @Override
    public void setLastVideoTopInsetPx(int px) {
        this.lastVideoTopInsetPx = px;
    }

    @Override
    public void proceedCloseOrNavigateBack() {
        if (archiveNavigateUpHandler != null) {
            archiveNavigateUpHandler.run();
            return;
        }
        ViewerUiHelper.setSystemBarsFullscreen(this, false);
        File activeFileForFolder = (isAudioMode && AudioPlayerManager.getInstance().getCurrentFile() != null)
                ? AudioPlayerManager.getInstance().getCurrentFile()
                : targetFile;
        if ((openedFromNotification || (openedFromMiniAudioBar && PrefsManager.isMiniBarReturnToSongFolder(this)))
                && activeFileForFolder != null && activeFileForFolder.getParentFile() != null) {
            openedFromNotification = false;
            openedFromMiniAudioBar = false;
            File songFolder = activeFileForFolder.getParentFile();
            String folderPath = songFolder.getAbsolutePath();
            String resolvedMode = FileBrowserActivity.MODE_INTERNAL;
            StorageUtils.StorageStat sd = StorageUtils.getExternalSdCardStat(this);
            List<StorageUtils.StorageStat> otgs = StorageUtils.getConnectedUsbOtgVolumes(this);
            boolean matchedOtg = false;
            for (StorageUtils.StorageStat otg : otgs) {
                if (otg.root != null && folderPath.startsWith(otg.root.getAbsolutePath())) {
                    resolvedMode = FileBrowserActivity.MODE_USB_OTG;
                    matchedOtg = true;
                    break;
                }
            }
            if (!matchedOtg && sd.available && sd.root != null && folderPath.startsWith(sd.root.getAbsolutePath())) {
                resolvedMode = FileBrowserActivity.MODE_SD_CARD;
            }
            Intent browser = new Intent(this, FileBrowserActivity.class);
            browser.putExtra(FileBrowserActivity.EXTRA_MODE, resolvedMode);
            browser.putExtra(FileBrowserActivity.EXTRA_INITIAL_DIR, folderPath);
            browser.putExtra(FileBrowserActivity.EXTRA_TITLE, songFolder.getName());
            browser.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(browser);
            finish();
            if (isAudioMode) {
                try {
                    if (PrefsManager.isReduceAnimations(this)) {
                        overridePendingTransition(0, 0);
                    } else {
                        overridePendingTransition(0, R.anim.slide_out_bottom);
                    }
                } catch (Throwable ignored) {
                }
            } else {
                ThemeHelper.overrideCloseTransition(this);
            }
            return;
        }
        if (isArchiveMode) {
            isArchiveMode = false;
            CacheCleanupManager.cleanupArchivePreviewDir(this);
        }
        finish();
        if (isAudioMode) {
            try {
                if (PrefsManager.isReduceAnimations(this)) {
                    overridePendingTransition(0, 0);
                } else {
                    overridePendingTransition(0, R.anim.slide_out_bottom);
                }
            } catch (Throwable ignored) {
            }
        } else {
            ThemeHelper.overrideCloseTransition(this);
        }
    }

    @Override
    public void shareCurrentFile() {
        ViewerUiHelper.shareFile(this, targetFile);
    }

    @Override
    public void confirmDeleteViewedFile() {
        if (targetFile == null) return;
        ThemedDialogHelper.showCustomDialog(this, "מחיקת קובץ",
                "האם למחוק את \"" + targetFile.getName() + "\"?", null, "מחק", () -> {
                    boolean ok = ViewerUiHelper.deleteViewedFile(this, targetFile);
                    Toast.makeText(this, ok ? "הקובץ נמחק" : "המחיקה נכשלה", Toast.LENGTH_SHORT).show();
                    if (ok) finish();
                }, "ביטול", null, true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        teardownActiveControllers();
        cleanupExternalCopiedCache();

        if (isFinishing()) {
            CacheCleanupManager.cleanupArchivePreviewDir(this);
            CacheCleanupManager.cleanupPreviewFileIfManagedCache(this, targetFile);
        }

        executor.shutdownNow();
    }
}
