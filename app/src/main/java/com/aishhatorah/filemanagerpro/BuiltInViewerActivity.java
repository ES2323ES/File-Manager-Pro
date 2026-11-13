package com.aishhatorah.filemanagerpro;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfRenderer;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.cardview.widget.CardView;
import androidx.core.content.FileProvider;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.button.MaterialButton;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;

/**
 * מציג ונגן המדיה המובנה של File Manager Pro v0.2.8:
 * - תצוגת Edge-to-Edge מלאה לתמונות ווידאו מאחורי סרגלים שקופים (סעיף 25)
 * - מצב מחוות אוטומטי מול מצב כפתורים קלאסי בווידאו ותמונות (סעיפים 29, 30, 44)
 * - לחיצה ארוכה בצד ימין 2x קדימה ובצד שמאל 2x אחורה בווידאו (סעיף 44)
 * - תמיכה מלאה ב-GIF מונפש + פירוק ל-PNG + המרה ל-MP4 + יצירת GIF (סעיף 9)
 * - קביעת טפט, הצגת מקור SVG, נגן שמע עם תור/טיימר שינה/אקולייזר (סעיפים 22, 41, 42)
 * - צפייה, חילוץ ועריכת קבצים בתוך ארכיוני ZIP/TAR/GZ כולל סיסמה (סעיף 2)
 */
public class BuiltInViewerActivity extends AppCompatActivity {

    public static final String EXTRA_FILE_PATH = "extra_file_path";
    public static final String EXTRA_FROM_NOTIFICATION = "extra_from_notification";
    public static final String EXTRA_FORCE_TYPE = "extra_force_type";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private File targetFile;
    private boolean openedFromNotification = false;
    private String forceType = null;

    private LinearLayout topBar;
    private TextView tvMarqueeTitle;
    private TextView tvFolderTrackCounter;
    private MaterialButton btnTopRightVideoSettings;

    private boolean isImageMode = false;
    private boolean mediaControlsHidden = false;
    private View activeMediaBottomPanel = null;
    private View activeImagePrevFloat = null;
    private View activeImageNextFloat = null;
    private View activeVideoCenterOverlay = null;
    private FrameLayout activeMediaStageRoot = null;
    private FrameLayout activeMediaViewport = null;

    private final List<File> imagePlaylist = new ArrayList<>();
    private int currentImageIdx = 0;
    private ZoomableImageView activeZoomImageView = null;
    private int imageLoadGeneration = 0;

    private boolean isAudioMode = false;
    private boolean audioTickerActive = false;
    private AudioPlayerManager.PlaybackListener audioListener;

    private boolean isVideoMode = false;
    private final List<File> videoPlaylist = new ArrayList<>();
    private int currentVideoIdx = 0;
    private MediaPlayer videoPlayer;
    private Surface activeVideoSurface;
    private TextureView videoTextureView;
    private boolean videoPrepared = false;
    private boolean videoTickerActive = false;
    private int currentVideoWidth = 0;
    private int currentVideoHeight = 0;
    private float videoSpeed = 1.0f;
    private int videoRepeatMode = AudioPlayerManager.REPEAT_OFF;
    private boolean videoMuted = false;
    private boolean videoAutoNext = true;
    private float videoScale = 1.0f;
    private float videoTransX = 0f;
    private float videoTransY = 0f;
    private boolean isLandscapeLocked = false;

    // סעיף 44: מצב האצה/החזרה זמנית בלחיצה ארוכה על קצוות הווידאו
    private boolean isHold2xForwardActive = false;
    private boolean isHold2xRewindActive = false;
    private float speedBeforeHold = 1.0f;
    private Runnable rewindPulseRunnable = null;

    private ParcelFileDescriptor pdfDescriptor;
    private PdfRenderer pdfRenderer;
    private int currentPdfPage = 0;

    private String archivePassword = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        openedFromNotification = getIntent().getBooleanExtra(EXTRA_FROM_NOTIFICATION, false);
        forceType = getIntent().getStringExtra(EXTRA_FORCE_TYPE);
        String path = getIntent().getStringExtra(EXTRA_FILE_PATH);
        if (path == null) {
            finish();
            return;
        }
        targetFile = new File(path);
        if (!targetFile.exists()) {
            Toast.makeText(this, "הקובץ אינו קיים", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // הפניה אוטומטית לעורך הקוד המתקדם עבור טקסט/קוד
        String lower = targetFile.getName().toLowerCase(Locale.ROOT);
        if (FileOpenWithHelper.ACTION_INTERNAL_CODE.equals(forceType)
                || (forceType == null && !MediaFormatHelper.isImageFile(lower)
                && !MediaFormatHelper.isAudioFile(lower)
                && !MediaFormatHelper.isVideoFile(lower)
                && !MediaFormatHelper.isPdfFile(lower)
                && !MediaFormatHelper.isZipOrArchiveFile(lower)
                && !MediaFormatHelper.isInstallableAppPackage(lower)
                && !endsWithAny(lower, ".docx", ".xlsx", ".pptx", ".odt", ".ods", ".odp")
                && (MediaFormatHelper.isTextFile(lower) || MediaFormatHelper.isCodeOrSyntaxFile(lower)))) {
            Intent codeIntent = new Intent(this, CodeEditorActivity.class);
            codeIntent.putExtra(CodeEditorActivity.EXTRA_FILE_PATH, targetFile.getAbsolutePath());
            startActivity(codeIntent);
            finish();
            return;
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                proceedCloseOrNavigateBack();
            }
        });

        buildViewerUiForCurrentFile();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null) {
            if (intent.getBooleanExtra(EXTRA_FROM_NOTIFICATION, false)) {
                openedFromNotification = true;
            }
            forceType = intent.getStringExtra(EXTRA_FORCE_TYPE);
            String newPath = intent.getStringExtra(EXTRA_FILE_PATH);
            if (newPath != null) {
                File f = new File(newPath);
                if (f.exists() && (targetFile == null || !f.getAbsolutePath().equals(targetFile.getAbsolutePath()))) {
                    targetFile = f;
                    detachAudioCallbacksOnly();
                    releaseVideoPlayer();
                    buildViewerUiForCurrentFile();
                }
            }
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        boolean landscapeNow = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE;
        if (isVideoMode) {
            if (landscapeNow) {
                mediaControlsHidden = true;
            }
            applyMediaImmersiveAndLayoutState();
            if (videoTextureView != null) {
                videoTextureView.post(this::updateVideoAspectRatioMatrix);
            }
        } else if (isImageMode) {
            applyMediaImmersiveAndLayoutState();
            if (activeZoomImageView != null) {
                activeZoomImageView.post(activeZoomImageView::fitImageToCenter);
            }
        } else if (isAudioMode) {
            detachAudioCallbacksOnly();
            buildViewerUiForCurrentFile();
        }
    }

    private void setSystemBarsFullscreen(boolean fullscreen) {
        Window window = getWindow();
        if (window == null) return;
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
        if (controller != null) {
            if (fullscreen) {
                controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                controller.hide(WindowInsetsCompat.Type.systemBars());
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars());
            }
        }
    }

    private void toggleMediaControlsVisibility() {
        mediaControlsHidden = !mediaControlsHidden;
        applyMediaImmersiveAndLayoutState();
    }

    // סעיף 25: במה פרוסה Edge-to-Edge מאחורי סרגלים צפים ושקופים ללא מרווחים חיצוניים
    private void applyMediaImmersiveAndLayoutState() {
        if (!isImageMode && !isVideoMode) {
            setSystemBarsFullscreen(false);
            return;
        }
        boolean isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        boolean wantFullscreenSystemBars = mediaControlsHidden || (isVideoMode && isLandscape);
        setSystemBarsFullscreen(wantFullscreenSystemBars);

        if (topBar != null) {
            topBar.setVisibility(mediaControlsHidden ? View.GONE : View.VISIBLE);
            topBar.setBackgroundColor(Color.argb(175, 10, 15, 28));
        }

        if (activeMediaBottomPanel != null) {
            activeMediaBottomPanel.setVisibility(mediaControlsHidden ? View.GONE : View.VISIBLE);
        }

        boolean autoGesture = PrefsManager.isAutoGestureMode(this);
        if (activeImagePrevFloat != null) {
            activeImagePrevFloat.setVisibility(
                    (!mediaControlsHidden && !autoGesture && imagePlaylist.size() > 1) ? View.VISIBLE : View.GONE);
        }
        if (activeImageNextFloat != null) {
            activeImageNextFloat.setVisibility(
                    (!mediaControlsHidden && !autoGesture && imagePlaylist.size() > 1) ? View.VISIBLE : View.GONE);
        }
        if (activeVideoCenterOverlay != null) {
            activeVideoCenterOverlay.setVisibility(mediaControlsHidden ? View.GONE : View.VISIBLE);
        }

        if (activeMediaStageRoot != null) {
            activeMediaStageRoot.setBackgroundColor(Color.BLACK);
        }

        if (activeMediaViewport != null) {
            FrameLayout.LayoutParams vpLp = (FrameLayout.LayoutParams) activeMediaViewport.getLayoutParams();
            if (vpLp != null) {
                vpLp.setMargins(0, 0, 0, 0);
                activeMediaViewport.setLayoutParams(vpLp);
            }
        }

        if (isVideoMode && videoTextureView != null) {
            videoTextureView.post(this::updateVideoAspectRatioMatrix);
        } else if (isImageMode && activeZoomImageView != null) {
            activeZoomImageView.post(activeZoomImageView::fitImageToCenter);
        }
    }

    private void detachAudioCallbacksOnly() {
        audioTickerActive = false;
        if (audioListener != null) {
            AudioPlayerManager.getInstance().removeListener(audioListener);
            audioListener = null;
        }
    }

    private void proceedCloseOrNavigateBack() {
        setSystemBarsFullscreen(false);
        if (openedFromNotification && targetFile != null && targetFile.getParentFile() != null) {
            openedFromNotification = false;
            File songFolder = targetFile.getParentFile();
            Intent browser = new Intent(this, FileBrowserActivity.class);
            StorageUtils.StorageStat sd = StorageUtils.getExternalSdCardStat(this);
            boolean onSd = sd.available && sd.root != null
                    && songFolder.getAbsolutePath().startsWith(sd.root.getAbsolutePath());
            browser.putExtra(FileBrowserActivity.EXTRA_MODE,
                    onSd ? FileBrowserActivity.MODE_SD_CARD : FileBrowserActivity.MODE_INTERNAL);
            browser.putExtra(FileBrowserActivity.EXTRA_INITIAL_DIR, songFolder.getAbsolutePath());
            browser.putExtra(FileBrowserActivity.EXTRA_TITLE, songFolder.getName());
            browser.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(browser);
        }
        finish();
    }

    private void buildViewerUiForCurrentFile() {
        activeMediaBottomPanel = null;
        activeImagePrevFloat = null;
        activeImageNextFloat = null;
        activeVideoCenterOverlay = null;
        activeMediaStageRoot = null;
        activeMediaViewport = null;
        activeZoomImageView = null;

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
        } else if (FileOpenWithHelper.ACTION_INTERNAL_AUDIO.equals(forceType)) {
            isImageMode = false;
            isAudioMode = true;
            isVideoMode = false;
        } else if (FileOpenWithHelper.ACTION_INTERNAL_VIDEO.equals(forceType)) {
            isImageMode = false;
            isAudioMode = false;
            isVideoMode = true;
        } else if (FileOpenWithHelper.ACTION_INTERNAL_ARCHIVE.equals(forceType)) {
            isImageMode = false;
            isAudioMode = false;
            isVideoMode = false;
        } else {
            isImageMode = MediaFormatHelper.isImageFile(lower);
            isAudioMode = !isImageMode && MediaFormatHelper.isAudioFile(lower);
            isVideoMode = !isImageMode && !isAudioMode && MediaFormatHelper.isVideoFile(lower);
        }

        boolean isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        mediaControlsHidden = isVideoMode && isLandscape;

        topBar = buildTopBar(theme, textPrimary, textSecondary, accent, isLandscape);

        if (isImageMode || isVideoMode) {
            FrameLayout stageRoot = new FrameLayout(this);
            stageRoot.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            stageRoot.setBackgroundColor(Color.BLACK);
            activeMediaStageRoot = stageRoot;

            if (isImageMode) {
                renderImageViewerOverlay(stageRoot, cardColor, textPrimary, textSecondary, accent, theme);
            } else {
                renderVideoPlayerOverlay(stageRoot, cardColor, textPrimary, textSecondary, accent, theme);
            }

            FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
            stageRoot.addView(topBar, topLp);

            setContentView(stageRoot);
            applyMediaImmersiveAndLayoutState();
            return;
        }

        setSystemBarsFullscreen(false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(bgColor);

        root.addView(topBar);

        LinearLayout contentArea = new LinearLayout(this);
        contentArea.setOrientation(LinearLayout.VERTICAL);
        contentArea.setGravity(Gravity.CENTER);
        int areaPad = isLandscape ? dp(8) : dp(12);
        contentArea.setPadding(areaPad, areaPad, areaPad, areaPad);
        root.addView(contentArea, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);

        if (isAudioMode) {
            renderAudioPlayer(contentArea, cardColor, textPrimary, textSecondary, accent, theme, isLandscape);
        } else if (MediaFormatHelper.isPdfFile(lower)) {
            renderPdfViewer(contentArea, cardColor, textSecondary, theme);
        } else if (FileOpenWithHelper.ACTION_INTERNAL_ARCHIVE.equals(forceType)
                || MediaFormatHelper.isZipOrArchiveFile(lower)
                || MediaFormatHelper.isInstallableAppPackage(lower)) {
            renderArchiveExtractor(contentArea, cardColor, textPrimary, textSecondary, accent, theme);
        } else if (endsWithAny(lower, ".docx", ".xlsx", ".pptx", ".odt", ".ods", ".odp")) {
            renderOfficeDocumentViewer(contentArea, cardColor, textPrimary, textSecondary);
        } else {
            Intent codeIntent = new Intent(this, CodeEditorActivity.class);
            codeIntent.putExtra(CodeEditorActivity.EXTRA_FILE_PATH, targetFile.getAbsolutePath());
            startActivity(codeIntent);
            finish();
        }
    }

    private LinearLayout buildTopBar(int theme, int textPrimary, int textSecondary, int accent, boolean isLandscape) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(ThemeHelper.getTopBarColor(this, theme));
        int vPad = isLandscape ? dp(6) : dp(8);
        bar.setPadding(dp(10), vPad, dp(10), vPad);

        boolean translucentOverlay = isImageMode || isVideoMode;
        int effectiveText = translucentOverlay ? Color.WHITE : textPrimary;
        int effectiveSub = translucentOverlay ? Color.parseColor("#CBD5E1") : textSecondary;

        Button btnClose = ThemedDialogHelper.createThemedButton(this, "חזרה", theme, false, false);
        if (translucentOverlay) {
            btnClose.setTextColor(Color.WHITE);
        }
        btnClose.setPadding(dp(10), dp(5), dp(10), dp(5));
        btnClose.setOnClickListener(v -> proceedCloseOrNavigateBack());
        bar.addView(btnClose);

        LinearLayout centerTitleCol = new LinearLayout(this);
        centerTitleCol.setOrientation(LinearLayout.VERTICAL);
        centerTitleCol.setGravity(Gravity.CENTER);

        tvMarqueeTitle = new AppCompatTextView(this) {
            @Override
            public boolean isFocused() {
                return true;
            }
        };
        tvMarqueeTitle.setText("   " + targetFile.getName() + "   •   " + targetFile.getName() + "   ");
        tvMarqueeTitle.setTextSize(ThemeHelper.spScaled(this, isLandscape ? 13.5f : 14.5f));
        tvMarqueeTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvMarqueeTitle.setTextColor(effectiveText);
        tvMarqueeTitle.setSingleLine(true);
        tvMarqueeTitle.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        tvMarqueeTitle.setMarqueeRepeatLimit(-1);
        tvMarqueeTitle.setHorizontallyScrolling(true);
        tvMarqueeTitle.setSelected(true);
        tvMarqueeTitle.setGravity(Gravity.CENTER_VERTICAL);
        centerTitleCol.addView(tvMarqueeTitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        tvFolderTrackCounter = new TextView(this);
        tvFolderTrackCounter.setTextSize(11.5f);
        tvFolderTrackCounter.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tvFolderTrackCounter.setTextColor(effectiveSub);
        tvFolderTrackCounter.setTextDirection(View.TEXT_DIRECTION_LTR);
        tvFolderTrackCounter.setGravity(Gravity.CENTER);
        tvFolderTrackCounter.setVisibility((isAudioMode || isImageMode || isVideoMode) ? View.VISIBLE : View.GONE);
        LinearLayout.LayoutParams cntLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cntLp.topMargin = dp(2);
        centerTitleCol.addView(tvFolderTrackCounter, cntLp);

        LinearLayout.LayoutParams titleColLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleColLp.setMarginStart(dp(8));
        titleColLp.setMarginEnd(dp(8));
        bar.addView(centerTitleCol, titleColLp);

        btnTopRightVideoSettings = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnTopRightVideoSettings.setText("אפשרויות");
        btnTopRightVideoSettings.setAllCaps(false);
        btnTopRightVideoSettings.setTextSize(11f);
        btnTopRightVideoSettings.setTypeface(Typeface.DEFAULT_BOLD);
        btnTopRightVideoSettings.setTextColor(translucentOverlay ? Color.WHITE : accent);
        btnTopRightVideoSettings.setStrokeColor(ColorStateList.valueOf(translucentOverlay ? Color.argb(140, 255, 255, 255) : accent));
        btnTopRightVideoSettings.setIconResource(R.drawable.ic_menu_hamburger);
        btnTopRightVideoSettings.setIconTint(ColorStateList.valueOf(translucentOverlay ? Color.WHITE : accent));
        btnTopRightVideoSettings.setIconSize(dp(16));
        btnTopRightVideoSettings.setIconPadding(dp(4));
        btnTopRightVideoSettings.setCornerRadius(dp(9));
        btnTopRightVideoSettings.setInsetTop(0);
        btnTopRightVideoSettings.setInsetBottom(0);
        btnTopRightVideoSettings.setMinHeight(0);
        btnTopRightVideoSettings.setMinimumHeight(0);
        btnTopRightVideoSettings.setPadding(dp(8), dp(5), dp(8), dp(5));
        btnTopRightVideoSettings.setVisibility((isVideoMode || isImageMode) ? View.VISIBLE : View.GONE);
        btnTopRightVideoSettings.setOnClickListener(v -> {
            if (isVideoMode) showVideoSettingsDialog(theme);
            else if (isImageMode) showImageOptionsDialog();
        });
        bar.addView(btnTopRightVideoSettings);

        if (!isVideoMode && !isImageMode) {
            Button btnShare = ThemedDialogHelper.createThemedButton(this, "שיתוף", theme, true, false);
            btnShare.setPadding(dp(12), dp(5), dp(12), dp(5));
            btnShare.setOnClickListener(v -> shareCurrentFile());
            bar.addView(btnShare);
        }

        return bar;
    }

    private void updateMarqueeHeaderName(String name) {
        if (tvMarqueeTitle != null && name != null) {
            tvMarqueeTitle.setText("   " + name + "   •   " + name + "   ");
            tvMarqueeTitle.setSelected(true);
        }
    }

    // ==================== מציג תמונות + GIF + SVG + טפט ====================

    private void renderImageViewerOverlay(
            FrameLayout stageRoot,
            int cardColor,
            int textPrimary,
            int textSecondary,
            int accent,
            int theme
    ) {
        buildImageFolderPlaylist(targetFile);

        FrameLayout imageViewport = new FrameLayout(this);
        activeMediaViewport = imageViewport;
        stageRoot.addView(imageViewport, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final ZoomableImageView zoomView = new ZoomableImageView(this);
        activeZoomImageView = zoomView;
        imageViewport.addView(zoomView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final WebView fallbackSvgWebView = new WebView(this);
        fallbackSvgWebView.setBackgroundColor(Color.TRANSPARENT);
        fallbackSvgWebView.setVisibility(View.GONE);
        WebSettings ws = fallbackSvgWebView.getSettings();
        ws.setSupportZoom(true);
        ws.setBuiltInZoomControls(true);
        ws.setDisplayZoomControls(false);
        ws.setUseWideViewPort(true);
        ws.setLoadWithOverviewMode(true);
        ws.setAllowFileAccess(true);
        GestureDetector webSingleTap = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                toggleMediaControlsVisibility();
                return true;
            }
        });
        fallbackSvgWebView.setOnTouchListener((v, ev) -> {
            webSingleTap.onTouchEvent(ev);
            return false;
        });
        imageViewport.addView(fallbackSvgWebView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ImageButton btnFloatPrev = createResponsiveRoundIconButton(R.drawable.ic_skip_prev, dp(40), 0, false, Color.WHITE, theme);
        FrameLayout.LayoutParams prevFlp = new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.LEFT | Gravity.CENTER_VERTICAL);
        prevFlp.leftMargin = dp(10);
        stageRoot.addView(btnFloatPrev, prevFlp);
        activeImagePrevFloat = btnFloatPrev;

        ImageButton btnFloatNext = createResponsiveRoundIconButton(R.drawable.ic_skip_next, dp(40), 0, false, Color.WHITE, theme);
        FrameLayout.LayoutParams nextFlp = new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        nextFlp.rightMargin = dp(10);
        stageRoot.addView(btnFloatNext, nextFlp);
        activeImageNextFloat = btnFloatNext;

        LinearLayout bottomCard = new LinearLayout(this);
        bottomCard.setOrientation(LinearLayout.VERTICAL);
        bottomCard.setPadding(dp(12), dp(8), dp(12), dp(10));
        GradientDrawable transBg = new GradientDrawable();
        transBg.setCornerRadius(dp(16));
        transBg.setColor(Color.argb(185, 12, 18, 32));
        bottomCard.setBackground(transBg);
        activeMediaBottomPanel = bottomCard;

        final TextView tvMeta = new TextView(this);
        tvMeta.setTextColor(Color.parseColor("#CBD5E1"));
        tvMeta.setTextSize(11.5f);
        tvMeta.setGravity(Gravity.CENTER);
        tvMeta.setPadding(0, 0, 0, dp(6));
        bottomCard.addView(tvMeta);

        LinearLayout actionsRow = new LinearLayout(this);
        actionsRow.setOrientation(LinearLayout.HORIZONTAL);
        actionsRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        actionsRow.setGravity(Gravity.CENTER_VERTICAL);

        MaterialButton btnWallpaper = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnWallpaper.setText("קבע כטפט");
        btnWallpaper.setAllCaps(false);
        btnWallpaper.setTextSize(12f);
        btnWallpaper.setTypeface(Typeface.DEFAULT_BOLD);
        btnWallpaper.setTextColor(Color.WHITE);
        btnWallpaper.setStrokeColor(ColorStateList.valueOf(Color.argb(140, 255, 255, 255)));
        btnWallpaper.setIconResource(R.drawable.ic_wallpaper);
        btnWallpaper.setIconTint(ColorStateList.valueOf(Color.WHITE));
        btnWallpaper.setIconSize(dp(16));
        btnWallpaper.setCornerRadius(dp(10));
        btnWallpaper.setMinHeight(0);
        btnWallpaper.setMinimumHeight(0);
        btnWallpaper.setPadding(dp(10), dp(7), dp(10), dp(7));
        btnWallpaper.setOnClickListener(v -> {
            Intent wi = new Intent(this, WallpaperCropActivity.class);
            wi.putExtra(WallpaperCropActivity.EXTRA_IMAGE_PATH, targetFile.getAbsolutePath());
            startActivity(wi);
        });

        MaterialButton btnProImageEditor = createPencilEditorButton("עורך תמונות", accent, theme, v -> {
            Intent i = new Intent(this, ProImageEditorActivity.class);
            i.putExtra(ProImageEditorActivity.EXTRA_IMAGE_PATH, targetFile.getAbsolutePath());
            startActivity(i);
        });
        LinearLayout.LayoutParams editLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        editLp.setMarginStart(dp(6));
        editLp.setMarginEnd(dp(6));
        btnProImageEditor.setLayoutParams(editLp);

        Button btnShareBottom = ThemedDialogHelper.createThemedButton(this, "שיתוף", theme, false, false);
        btnShareBottom.setTextColor(Color.WHITE);
        btnShareBottom.setPadding(dp(10), dp(7), dp(10), dp(7));
        btnShareBottom.setOnClickListener(v -> shareCurrentFile());

        actionsRow.addView(btnWallpaper);
        actionsRow.addView(btnProImageEditor);
        actionsRow.addView(btnShareBottom);
        bottomCard.addView(actionsRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams bottomLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        bottomLp.setMargins(dp(10), 0, dp(10), dp(12));
        stageRoot.addView(bottomCard, bottomLp);

        final Runnable loadCurrentImageIntoStage = () -> {
            if (imagePlaylist.isEmpty()) return;
            final int gen = ++imageLoadGeneration;
            final File imgFile = imagePlaylist.get(currentImageIdx);
            targetFile = imgFile;
            updateMarqueeHeaderName(imgFile.getName());
            if (tvFolderTrackCounter != null) {
                tvFolderTrackCounter.setVisibility(View.VISIBLE);
                tvFolderTrackCounter.setText((currentImageIdx + 1) + "/" + imagePlaylist.size());
            }

            String nameLow = imgFile.getName().toLowerCase(Locale.ROOT);
            boolean isSvg = MediaFormatHelper.isSvgFile(nameLow);
            if (isSvg) {
                zoomView.setVisibility(View.GONE);
                fallbackSvgWebView.setVisibility(View.VISIBLE);
                btnProImageEditor.setVisibility(View.GONE);
                btnWallpaper.setVisibility(View.GONE);
                tvMeta.setText("קובץ SVG וקטורי • " + StorageUtils.formatSize(imgFile.length()));
                try {
                    byte[] rawBytes = readFileBytesForViewer(imgFile, nameLow.endsWith(".svgz"));
                    String b64 = Base64.encodeToString(rawBytes, Base64.NO_WRAP);
                    String html = "<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=20'/>"
                            + "<style>html,body{margin:0;padding:0;width:100%;height:100%;display:flex;align-items:center;justify-content:center;background:#000;}"
                            + "img{max-width:98%;max-height:98%;object-fit:contain;}</style></head>"
                            + "<body><img src='data:image/svg+xml;base64," + b64 + "'/></body></html>";
                    fallbackSvgWebView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
                } catch (Exception ignored) {
                }
                return;
            }

            fallbackSvgWebView.setVisibility(View.GONE);
            zoomView.setVisibility(View.VISIBLE);
            btnProImageEditor.setVisibility(View.VISIBLE);
            btnWallpaper.setVisibility(View.VISIBLE);

            // סעיף 9: ניגון GIF מונפש חלק באיכות מלאה דרך AnimatedImageDrawable (API 28+)
            if (MediaFormatHelper.isGifFile(nameLow) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                executor.execute(() -> {
                    try {
                        ImageDecoder.Source src = ImageDecoder.createSource(imgFile);
                        Drawable animatedDrawable = ImageDecoder.decodeDrawable(src);
                        uiHandler.post(() -> {
                            if (gen != imageLoadGeneration || isFinishing()) return;
                            zoomView.setImageDrawable(animatedDrawable);
                            if (animatedDrawable instanceof AnimatedImageDrawable) {
                                ((AnimatedImageDrawable) animatedDrawable).start();
                            }
                            tvMeta.setText("GIF מונפש • " + StorageUtils.formatSize(imgFile.length()));
                        });
                        return;
                    } catch (Throwable ignored) {
                    }
                });
                return;
            }

            executor.execute(() -> {
                DecodedPhoto photo = decodeUltraClearPhoto(imgFile);
                uiHandler.post(() -> {
                    if (gen != imageLoadGeneration || isFinishing()) return;
                    if (photo.bitmap != null) {
                        zoomView.setImageBitmap(photo.bitmap);
                        String dim = (photo.origWidth > 0 && photo.origHeight > 0)
                                ? (photo.origWidth + "x" + photo.origHeight + "  •  ") : "";
                        tvMeta.setText(dim + StorageUtils.formatSize(imgFile.length()));
                    } else {
                        zoomView.setVisibility(View.GONE);
                        fallbackSvgWebView.setVisibility(View.VISIBLE);
                        try {
                            byte[] rawBytes = readFileBytesForViewer(imgFile, false);
                            String b64 = Base64.encodeToString(rawBytes, Base64.NO_WRAP);
                            String html = "<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=20'/>"
                                    + "<style>html,body{margin:0;padding:0;width:100%;height:100%;display:flex;align-items:center;justify-content:center;background:#000;}"
                                    + "img{max-width:98%;max-height:98%;object-fit:contain;}</style></head>"
                                    + "<body><img src='data:image/*;base64," + b64 + "'/></body></html>";
                            fallbackSvgWebView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
                        } catch (Exception ignored) {
                        }
                    }
                });
            });
        };

        Runnable goPrev = () -> {
            if (imagePlaylist.size() <= 1) return;
            currentImageIdx = (currentImageIdx - 1 + imagePlaylist.size()) % imagePlaylist.size();
            loadCurrentImageIntoStage.run();
        };
        Runnable goNext = () -> {
            if (imagePlaylist.size() <= 1) return;
            currentImageIdx = (currentImageIdx + 1) % imagePlaylist.size();
            loadCurrentImageIntoStage.run();
        };

        btnFloatPrev.setOnClickListener(v -> goPrev.run());
        btnFloatNext.setOnClickListener(v -> goNext.run());

        // סעיף 44: במצב מחוות אוטומטי הקשה בצד ימין עוברת קדימה, בצד שמאל אחורה, ובמרכז מסתירה/מציגה פקדים
        zoomView.setOnTapPositionListener((normX, isZoomed) -> {
            if (!isZoomed && PrefsManager.isAutoGestureMode(this) && imagePlaylist.size() > 1) {
                if (normX >= 0.72f) {
                    goNext.run();
                    return;
                } else if (normX <= 0.28f) {
                    goPrev.run();
                    return;
                }
            }
            toggleMediaControlsVisibility();
        });

        zoomView.setOnHorizontalSwipeListener(new ZoomableImageView.HorizontalSwipeListener() {
            @Override
            public void onSwipeToNext() {
                goNext.run();
            }

            @Override
            public void onSwipeToPrevious() {
                goPrev.run();
            }
        });

        loadCurrentImageIntoStage.run();
    }

    private void showImageOptionsDialog() {
        List<String> opts = new ArrayList<>();
        String lower = targetFile.getName().toLowerCase(Locale.ROOT);
        boolean isSvg = MediaFormatHelper.isSvgFile(lower);
        boolean isGif = MediaFormatHelper.isGifFile(lower);

        opts.add("קבע כטפט (מסך בית / נעילה)");
        if (isSvg) {
            opts.add("הצג קוד מקור SVG");
        }
        if (isGif) {
            opts.add("פירוק GIF לתמונות PNG נפרדות");
            opts.add("המרת GIF לסרטון MP4");
        } else if (!isSvg) {
            opts.add("צור GIF מתמונות התיקייה");
        }
        boolean autoMode = PrefsManager.isAutoGestureMode(this);
        opts.add("מצב מחוות אוטומטי: " + (autoMode ? "מופעל (הקשה בצדדים)" : "כבוי (כפתורים צפים)"));
        opts.add("שיתוף קובץ");
        opts.add("מחיקה");

        ThemedDialogHelper.showThemedMenu(this, "אפשרויות תמונה", targetFile.getName(), opts, idx -> {
            String sel = opts.get(idx);
            if (sel.startsWith("קבע כטפט")) {
                Intent wi = new Intent(this, WallpaperCropActivity.class);
                wi.putExtra(WallpaperCropActivity.EXTRA_IMAGE_PATH, targetFile.getAbsolutePath());
                startActivity(wi);
            } else if (sel.equals("הצג קוד מקור SVG")) {
                Intent ci = new Intent(this, CodeEditorActivity.class);
                ci.putExtra(CodeEditorActivity.EXTRA_FILE_PATH, targetFile.getAbsolutePath());
                startActivity(ci);
            } else if (sel.startsWith("פירוק GIF")) {
                Toast.makeText(this, "מפרק פריימים...", Toast.LENGTH_SHORT).show();
                executor.execute(() -> {
                    try {
                        File parent = targetFile.getParentFile() != null ? targetFile.getParentFile() : getFilesDir();
                        File outDir = StorageUtils.uniqueDestinationFile(parent, targetFile.getName() + "_frames");
                        int count = GifToolsHelper.splitGifToPngs(targetFile, outDir);
                        uiHandler.post(() -> Toast.makeText(this,
                                "פורקו " + count + " תמונות אל " + outDir.getName(), Toast.LENGTH_LONG).show());
                    } catch (Exception e) {
                        uiHandler.post(() -> Toast.makeText(this, "שגיאה בפירוק GIF: " + e.getMessage(), Toast.LENGTH_LONG).show());
                    }
                });
            } else if (sel.startsWith("המרת GIF")) {
                Toast.makeText(this, "ממיר GIF ל-MP4...", Toast.LENGTH_SHORT).show();
                executor.execute(() -> {
                    try {
                        File parent = targetFile.getParentFile() != null ? targetFile.getParentFile() : getFilesDir();
                        File outMp4 = StorageUtils.uniqueDestinationFile(parent, targetFile.getName() + ".mp4");
                        GifToolsHelper.convertGifToMp4(targetFile, outMp4);
                        uiHandler.post(() -> Toast.makeText(this,
                                "נשמר סרטון: " + outMp4.getName(), Toast.LENGTH_LONG).show());
                    } catch (Exception e) {
                        uiHandler.post(() -> Toast.makeText(this, "שגיאה בהמרה: " + e.getMessage(), Toast.LENGTH_LONG).show());
                    }
                });
            } else if (sel.startsWith("צור GIF")) {
                if (imagePlaylist.size() < 2) {
                    Toast.makeText(this, "נדרשות לפחות 2 תמונות בתיקייה ליצירת GIF", Toast.LENGTH_SHORT).show();
                    return;
                }
                Toast.makeText(this, "יוצר אנימציית GIF מתמונות התיקייה...", Toast.LENGTH_SHORT).show();
                executor.execute(() -> {
                    try {
                        List<Bitmap> frames = new ArrayList<>();
                        int limit = Math.min(12, imagePlaylist.size());
                        for (int i = 0; i < limit; i++) {
                            BitmapFactory.Options o = new BitmapFactory.Options();
                            o.inSampleSize = 2;
                            Bitmap b = BitmapFactory.decodeFile(imagePlaylist.get(i).getAbsolutePath(), o);
                            if (b != null) frames.add(b);
                        }
                        File parent = targetFile.getParentFile() != null ? targetFile.getParentFile() : getFilesDir();
                        File outGif = StorageUtils.uniqueDestinationFile(parent, "animation.gif");
                        GifToolsHelper.imagesToGif(frames, 550, 640, outGif);
                        for (Bitmap b : frames) b.recycle();
                        uiHandler.post(() -> Toast.makeText(this, "נוצר GIF: " + outGif.getName(), Toast.LENGTH_LONG).show());
                    } catch (Exception e) {
                        uiHandler.post(() -> Toast.makeText(this, "שגיאה ביצירת GIF: " + e.getMessage(), Toast.LENGTH_LONG).show());
                    }
                });
            } else if (sel.startsWith("מצב מחוות אוטומטי")) {
                boolean next = !autoMode;
                PrefsManager.setAutoGestureMode(this, next);
                applyMediaImmersiveAndLayoutState();
                Toast.makeText(this, next ? "מצב מחוות אוטומטי הופעל" : "מצב מחוות אוטומטי כובה", Toast.LENGTH_SHORT).show();
            } else if (sel.equals("שיתוף קובץ")) {
                shareCurrentFile();
            } else if (sel.equals("מחיקה")) {
                confirmDeleteViewedFile();
            }
        });
    }

    private void confirmDeleteViewedFile() {
        ThemedDialogHelper.showCustomDialog(this, "מחיקת קובץ",
                "האם למחוק את \"" + targetFile.getName() + "\"?", null, "מחק", () -> {
                    boolean ok = PrefsManager.isUseRecycleBin(this)
                            ? RecycleBinManager.moveToTrash(this, targetFile)
                            : StorageUtils.deleteRecursively(targetFile);
                    Toast.makeText(this, ok ? "הקובץ נמחק" : "המחיקה נכשלה", Toast.LENGTH_SHORT).show();
                    if (ok) finish();
                }, "ביטול", null, true);
    }

    private static class DecodedPhoto {
        Bitmap bitmap;
        int origWidth;
        int origHeight;
    }

    private DecodedPhoto decodeUltraClearPhoto(File file) {
        DecodedPhoto out = new DecodedPhoto();
        final int MAX_EDGE = 4096;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            out.origWidth = Math.max(0, bounds.outWidth);
            out.origHeight = Math.max(0, bounds.outHeight);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    ImageDecoder.Source src = ImageDecoder.createSource(file);
                    out.bitmap = ImageDecoder.decodeBitmap(src, (decoder, info, s) -> {
                        decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                        decoder.setMemorySizePolicy(ImageDecoder.MEMORY_POLICY_DEFAULT);
                        int w = info.getSize().getWidth();
                        int h = info.getSize().getHeight();
                        out.origWidth = w;
                        out.origHeight = h;
                        if (w > MAX_EDGE || h > MAX_EDGE) {
                            float ratio = Math.min((float) MAX_EDGE / w, (float) MAX_EDGE / h);
                            decoder.setTargetSize(Math.max(1, Math.round(w * ratio)),
                                    Math.max(1, Math.round(h * ratio)));
                        }
                    });
                    if (out.bitmap != null) return out;
                } catch (Exception ignored) {
                }
            }

            if (out.origWidth > 0 && out.origHeight > 0) {
                int sample = 1;
                while (out.origWidth / sample > MAX_EDGE || out.origHeight / sample > MAX_EDGE) {
                    sample *= 2;
                }
                BitmapFactory.Options loadOpts = new BitmapFactory.Options();
                loadOpts.inSampleSize = sample;
                loadOpts.inPreferredConfig = Bitmap.Config.ARGB_8888;
                loadOpts.inScaled = false;
                loadOpts.inDither = false;
                out.bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), loadOpts);
            }
        } catch (OutOfMemoryError oom) {
            try {
                BitmapFactory.Options fallback = new BitmapFactory.Options();
                fallback.inSampleSize = 2;
                fallback.inPreferredConfig = Bitmap.Config.ARGB_8888;
                out.bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), fallback);
            } catch (Exception ignored) {
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private void buildImageFolderPlaylist(File initialImage) {
        imagePlaylist.clear();
        currentImageIdx = 0;
        File parent = initialImage.getParentFile();
        if (parent != null && parent.exists() && parent.isDirectory()) {
            File[] siblings = parent.listFiles();
            if (siblings != null) {
                for (File f : siblings) {
                    if (f.isFile() && MediaFormatHelper.isImageFile(f.getName())) {
                        imagePlaylist.add(f);
                    }
                }
            }
        }
        if (imagePlaylist.isEmpty()) {
            imagePlaylist.add(initialImage);
        } else {
            Collections.sort(imagePlaylist, (a, b) -> StorageUtils.compareHebrewFirst(a.getName(), b.getName()));
            for (int i = 0; i < imagePlaylist.size(); i++) {
                if (imagePlaylist.get(i).getAbsolutePath().equals(initialImage.getAbsolutePath())) {
                    currentImageIdx = i;
                    break;
                }
            }
        }
    }

    private byte[] readFileBytesForViewer(File file, boolean gunzip) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (InputStream raw = new FileInputStream(file);
             InputStream in = gunzip ? new GZIPInputStream(raw) : raw) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1 && bos.size() < 8 * 1024 * 1024) {
                bos.write(buf, 0, r);
            }
        }
        return bos.toByteArray();
    }

    // ==================== נגן שמע מתקדם (תור ניגון, טיימר שינה, אקולייזר) ====================

    private void renderAudioPlayer(
            LinearLayout host,
            int cardColor,
            int textPrimary,
            int textSecondary,
            int accent,
            int theme,
            boolean isLandscape
    ) {
        AudioPlayerManager mgr = AudioPlayerManager.getInstance();
        mgr.playOrAttachFile(this, targetFile);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        float smallestDp = Math.min(dm.widthPixels, dm.heightPixels) / dm.density;
        float scale = Math.max(0.82f, Math.min(1.06f, smallestDp / 380f));

        int primaryBtnPx = dp(Math.round(52 * scale));
        int skipBtnPx = dp(Math.round(42 * scale));
        int seek10BtnPx = dp(Math.round(40 * scale));
        int repeatBtnPx = dp(Math.round(38 * scale));
        int btnMarginPx = dp(Math.round(6 * scale));

        LinearLayout card = createSurfaceCard(cardColor);
        if (isLandscape) {
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            card.setPadding(dp(12), dp(10), dp(12), dp(10));
        } else {
            card.setOrientation(LinearLayout.VERTICAL);
        }

        FrameLayout stageFrame = new FrameLayout(this);

        CardView artCard = new CardView(this);
        artCard.setRadius(dp(20));
        artCard.setCardElevation(0f);
        int stageBg = !ThemeHelper.isDarkSurface(this, theme)
                ? Color.parseColor("#EFF6FF")
                : Color.parseColor("#0B1325");
        artCard.setCardBackgroundColor(stageBg);

        final ImageView ivArt = new ImageView(this);
        ivArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        artCard.addView(ivArt, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        stageFrame.addView(artCard, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final TextView tvDoubleTapBadge = createSeekOverlayBadge();
        stageFrame.addView(tvDoubleTapBadge);

        attachDoubleTapSeekGesture(artCard, tvDoubleTapBadge, deltaSec -> mgr.seekBy(deltaSec * 1000));

        LinearLayout controlsColumn = new LinearLayout(this);
        controlsColumn.setOrientation(LinearLayout.VERTICAL);
        controlsColumn.setGravity(Gravity.CENTER_VERTICAL);

        if (isLandscape) {
            LinearLayout.LayoutParams stageLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.44f);
            stageLp.rightMargin = dp(14);
            card.addView(stageFrame, stageLp);

            LinearLayout.LayoutParams ctrlLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.56f);
            card.addView(controlsColumn, ctrlLp);
        } else {
            LinearLayout.LayoutParams stageLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
            stageLp.bottomMargin = dp(8);
            card.addView(stageFrame, stageLp);

            card.addView(controlsColumn, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        LinearLayout timeRowLtr = new LinearLayout(this);
        timeRowLtr.setOrientation(LinearLayout.HORIZONTAL);
        timeRowLtr.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        timeRowLtr.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        timeRowLtr.setPadding(dp(4), dp(4), dp(4), dp(2));

        final TextView tvElapsedLeftmost = new TextView(this);
        tvElapsedLeftmost.setText("00:00");
        tvElapsedLeftmost.setTextColor(accent);
        tvElapsedLeftmost.setTextSize(14f);
        tvElapsedLeftmost.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        timeRowLtr.addView(tvElapsedLeftmost);

        final TextView tvTotalNextToIt = new TextView(this);
        tvTotalNextToIt.setText("  /  00:00");
        tvTotalNextToIt.setTextColor(textSecondary);
        tvTotalNextToIt.setTextSize(13.5f);
        tvTotalNextToIt.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        timeRowLtr.addView(tvTotalNextToIt);

        controlsColumn.addView(timeRowLtr, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final SeekBar seekBar = new SeekBar(this);
        seekBar.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        controlsColumn.addView(seekBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout transportRow = new LinearLayout(this);
        transportRow.setOrientation(LinearLayout.HORIZONTAL);
        transportRow.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        transportRow.setGravity(Gravity.CENTER);
        transportRow.setPadding(0, dp(isLandscape ? 6 : 10), 0, dp(isLandscape ? 6 : 8));

        ImageButton btnBack10 = createResponsiveRoundIconButton(R.drawable.ic_replay_10, seek10BtnPx, btnMarginPx, false, accent, theme);
        ImageButton btnPrevSong = createResponsiveRoundIconButton(R.drawable.ic_skip_prev, skipBtnPx, btnMarginPx, false, accent, theme);
        final ImageButton btnPlayPause = createResponsiveRoundIconButton(R.drawable.ic_pause, primaryBtnPx, btnMarginPx, true, accent, theme);
        ImageButton btnNextSong = createResponsiveRoundIconButton(R.drawable.ic_skip_next, skipBtnPx, btnMarginPx, false, accent, theme);
        ImageButton btnFwd10 = createResponsiveRoundIconButton(R.drawable.ic_forward_10, seek10BtnPx, btnMarginPx, false, accent, theme);

        transportRow.addView(btnBack10);
        transportRow.addView(btnPrevSong);
        transportRow.addView(btnPlayPause);
        transportRow.addView(btnNextSong);
        transportRow.addView(btnFwd10);
        controlsColumn.addView(transportRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout bottomCenterBar = new FrameLayout(this);
        bottomCenterBar.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        bottomCenterBar.setPadding(dp(2), dp(2), dp(2), dp(4));

        final ImageButton btnRepeatMode = createResponsiveRoundIconButton(R.drawable.ic_repeat_off, repeatBtnPx, 0, false, accent, theme);
        FrameLayout.LayoutParams repLp = new FrameLayout.LayoutParams(repeatBtnPx, repeatBtnPx, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        bottomCenterBar.addView(btnRepeatMode, repLp);

        final MaterialButton btnSpeedCenter = createSpeedPillButton(mgr.getPlaybackSpeed(), accent, theme);
        FrameLayout.LayoutParams spdLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        bottomCenterBar.addView(btnSpeedCenter, spdLp);

        controlsColumn.addView(bottomCenterBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // סעיף 22: סרגל כלים מתקדם לשמע (תור ניגון, טיימר שינה, אקולייזר, חיתוך אודיו)
        LinearLayout proAudioRow = new LinearLayout(this);
        proAudioRow.setOrientation(LinearLayout.HORIZONTAL);
        proAudioRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        proAudioRow.setGravity(Gravity.CENTER);
        proAudioRow.setPadding(0, dp(6), 0, 0);

        Button btnQueue = createCompactAudioAction("תור", R.drawable.ic_queue, accent, textPrimary);
        final Button btnSleep = createCompactAudioAction("שינה", R.drawable.ic_sleep_timer, accent, textPrimary);
        Button btnEq = createCompactAudioAction("אקולייזר", R.drawable.ic_equalizer, accent, textPrimary);
        Button btnCut = createCompactAudioAction("חתוך", R.drawable.ic_audio_cutter, accent, textPrimary);

        proAudioRow.addView(btnQueue);
        proAudioRow.addView(btnSleep);
        proAudioRow.addView(btnEq);
        proAudioRow.addView(btnCut);
        controlsColumn.addView(proAudioRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        host.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        btnBack10.setOnClickListener(v -> mgr.seekBy(-10_000));
        btnFwd10.setOnClickListener(v -> mgr.seekBy(10_000));
        btnPrevSong.setOnClickListener(v -> mgr.playPrevious(this));
        btnNextSong.setOnClickListener(v -> mgr.playNext(this));
        btnPlayPause.setOnClickListener(v -> mgr.togglePlayPause(this));

        btnSpeedCenter.setOnClickListener(v -> showSpeedPickerDialog("בחירת מהירות השיר", mgr.getPlaybackSpeed(), chosen -> {
            mgr.setPlaybackSpeed(this, chosen);
            btnSpeedCenter.setText(String.format(Locale.US, "מהירות %.2gx", chosen).replace(".00", ".0"));
        }));

        btnRepeatMode.setOnClickListener(v -> {
            int nextMode = mgr.cycleRepeatMode();
            btnRepeatMode.setImageResource(resolveRepeatIcon(nextMode));
            String toastText = nextMode == AudioPlayerManager.REPEAT_ONE
                    ? "חזרה על השיר עצמו"
                    : (nextMode == AudioPlayerManager.REPEAT_FOLDER ? "חזרה על כל התיקייה" : "ללא חזרה");
            Toast.makeText(this, toastText, Toast.LENGTH_SHORT).show();
        });

        btnQueue.setOnClickListener(v -> showAudioQueueDialog());
        btnSleep.setOnClickListener(v -> showSleepTimerDialog());
        btnEq.setOnClickListener(v -> showEqualizerDialog());
        btnCut.setOnClickListener(v -> {
            File current = mgr.getCurrentFile() != null ? mgr.getCurrentFile() : targetFile;
            Intent ci = new Intent(this, AudioCutterActivity.class);
            ci.putExtra(AudioCutterActivity.EXTRA_INITIAL_PATH, current.getAbsolutePath());
            startActivity(ci);
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) mgr.seekTo(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        final Runnable syncAudioUi = () -> {
            File active = mgr.getCurrentFile();
            if (active != null) {
                targetFile = active;
                updateMarqueeHeaderName(active.getName());
            }
            if (tvFolderTrackCounter != null) {
                tvFolderTrackCounter.setVisibility(View.VISIBLE);
                tvFolderTrackCounter.setText(mgr.getCurrentTrackNumber() + "/" + mgr.getFolderTrackCount());
            }

            Bitmap art = mgr.getCurrentArtwork();
            if (art != null && !art.isRecycled()) {
                ivArt.setPadding(0, 0, 0, 0);
                ivArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
                ivArt.setImageBitmap(art);
            } else {
                int fallbackPad = dp(isLandscape ? 28 : 44);
                ivArt.setPadding(fallbackPad, fallbackPad, fallbackPad, fallbackPad);
                ivArt.setScaleType(ImageView.ScaleType.FIT_CENTER);
                ivArt.setImageResource(R.drawable.ic_audio);
            }

            btnPlayPause.setImageResource(mgr.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);
            btnSpeedCenter.setText(formatSpeedLabel(mgr.getPlaybackSpeed()));
            btnRepeatMode.setImageResource(resolveRepeatIcon(mgr.getRepeatMode()));

            long remMs = mgr.getSleepTimerRemainingMs();
            btnSleep.setText(remMs > 0 ? ("שינה (" + Math.max(1, (int) Math.ceil(remMs / 60_000.0)) + "ד)") : "שינה");
        };

        audioListener = syncAudioUi::run;
        mgr.addListener(audioListener);
        syncAudioUi.run();

        audioTickerActive = true;
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!audioTickerActive) return;
                int dur = mgr.getDurationMs();
                int pos = mgr.getCurrentPositionMs();
                seekBar.setMax(Math.max(1, dur));
                seekBar.setProgress(pos);
                tvElapsedLeftmost.setText(formatClock(pos));
                tvTotalNextToIt.setText("  /  " + formatClock(dur));
                long remMs = mgr.getSleepTimerRemainingMs();
                if (remMs > 0) {
                    btnSleep.setText("שינה (" + formatClock((int) remMs) + ")");
                }
                uiHandler.postDelayed(this, 250);
            }
        });
    }

    private Button createCompactAudioAction(String label, int iconRes, int accent, int textPrimary) {
        MaterialButton btn = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btn.setText(label);
        btn.setAllCaps(false);
        btn.setTextSize(11f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setTextColor(textPrimary);
        btn.setStrokeColor(ColorStateList.valueOf(Color.argb(65, Color.red(accent), Color.green(accent), Color.blue(accent))));
        btn.setIconResource(iconRes);
        btn.setIconTint(ColorStateList.valueOf(accent));
        btn.setIconSize(dp(15));
        btn.setIconPadding(dp(4));
        btn.setCornerRadius(dp(10));
        btn.setMinHeight(0);
        btn.setMinimumHeight(0);
        btn.setMinWidth(0);
        btn.setMinimumWidth(0);
        btn.setInsetTop(0);
        btn.setInsetBottom(0);
        btn.setPadding(dp(6), dp(5), dp(6), dp(5));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(2), 0, dp(2), 0);
        btn.setLayoutParams(lp);
        return btn;
    }

    private void showAudioQueueDialog() {
        AudioPlayerManager mgr = AudioPlayerManager.getInstance();
        int theme = PrefsManager.getThemeMode(this);
        int textPrimary = ThemeHelper.getPrimaryTextColor(this, theme);
        int accent = ThemeHelper.getAccentColor(this, theme);

        ScrollView scroll = new ScrollView(this);
        LinearLayout listLayout = new LinearLayout(this);
        listLayout.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listLayout);

        final Runnable[] rebuild = new Runnable[1];
        rebuild[0] = () -> {
            listLayout.removeAllViews();
            List<File> queue = mgr.getPlaylistSnapshot();
            int curIdx = mgr.getCurrentIndex();
            for (int i = 0; i < queue.size(); i++) {
                final int idx = i;
                File track = queue.get(i);
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(8), dp(8), dp(8), dp(8));
                if (i == curIdx) {
                    GradientDrawable hl = new GradientDrawable();
                    hl.setCornerRadius(dp(8));
                    hl.setColor(Color.argb(36, Color.red(accent), Color.green(accent), Color.blue(accent)));
                    row.setBackground(hl);
                }

                TextView tvName = new TextView(this);
                tvName.setText((i + 1) + ". " + track.getName());
                tvName.setTextColor(i == curIdx ? accent : textPrimary);
                tvName.setTextSize(13f);
                tvName.setTypeface(i == curIdx ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                tvName.setMaxLines(1);
                tvName.setEllipsize(TextUtils.TruncateAt.END);
                tvName.setOnClickListener(v -> {
                    mgr.playTrackAt(this, idx);
                    rebuild[0].run();
                });
                row.addView(tvName, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                if (i > 0) {
                    TextView up = new TextView(this);
                    up.setText("▲");
                    up.setTextColor(accent);
                    up.setPadding(dp(8), dp(4), dp(8), dp(4));
                    up.setOnClickListener(v -> {
                        mgr.moveQueueItem(idx, idx - 1);
                        rebuild[0].run();
                    });
                    row.addView(up);
                }
                if (i < queue.size() - 1) {
                    TextView dn = new TextView(this);
                    dn.setText("▼");
                    dn.setTextColor(accent);
                    dn.setPadding(dp(8), dp(4), dp(8), dp(4));
                    dn.setOnClickListener(v -> {
                        mgr.moveQueueItem(idx, idx + 1);
                        rebuild[0].run();
                    });
                    row.addView(dn);
                }
                if (i != curIdx && queue.size() > 1) {
                    TextView del = new TextView(this);
                    del.setText("✕");
                    del.setTextColor(Color.parseColor("#DC2626"));
                    del.setPadding(dp(8), dp(4), dp(4), dp(4));
                    del.setOnClickListener(v -> {
                        mgr.removeFromQueue(idx);
                        rebuild[0].run();
                    });
                    row.addView(del);
                }
                listLayout.addView(row);
            }
        };
        rebuild[0].run();

        ThemedDialogHelper.showCustomDialog(this, "תור ניגון (סדר השמעה)",
                "הקש על שיר להשמעה מיידית או הזז למעלה/למטה:",
                scroll, "סגור", null, null, null, false);
    }

    private void showSleepTimerDialog() {
        List<String> opts = Arrays.asList("כבה טיימר שינה", "15 דקות", "30 דקות", "45 דקות", "60 דקות");
        int[] mins = {0, 15, 30, 45, 60};
        ThemedDialogHelper.showThemedMenu(this, "טיימר שינה",
                "האפליקציה תעצור את השמע אוטומטית בתום הזמן:", opts, idx -> {
                    AudioPlayerManager.getInstance().setSleepTimerMinutes(this, mins[idx]);
                    Toast.makeText(this, mins[idx] == 0 ? "טיימר שינה כובה" : ("השמע ייעצר בעוד " + mins[idx] + " דקות"),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void showEqualizerDialog() {
        AudioPlayerManager mgr = AudioPlayerManager.getInstance();
        int bandCount = mgr.getEqBandCount();
        if (bandCount <= 0) {
            Toast.makeText(this, "אקולייזר זמין רק בזמן ניגון פעיל במכשיר זה", Toast.LENGTH_SHORT).show();
            return;
        }
        int theme = PrefsManager.getThemeMode(this);
        int textPrimary = ThemeHelper.getPrimaryTextColor(this, theme);
        int accent = ThemeHelper.getAccentColor(this, theme);

        ScrollView scroll = new ScrollView(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(panel);

        List<String> presets = mgr.getEqPresetNames();
        if (!presets.isEmpty()) {
            Button btnPreset = ThemedDialogHelper.createThemedButton(this, "בחר פרופיל אקולייזר מובנה", theme, false, false);
            btnPreset.setOnClickListener(v -> ThemedDialogHelper.showThemedMenu(
                    this, "פרופילי אקולייזר", "בחר סגנון סאונד:", presets, idx -> {
                        mgr.applyEqPreset(idx);
                        Toast.makeText(this, "הופעל פרופיל: " + presets.get(idx), Toast.LENGTH_SHORT).show();
                    }));
            panel.addView(btnPreset);
        }

        short minLvl = mgr.getEqMinLevel();
        short maxLvl = mgr.getEqMaxLevel();
        int span = Math.max(1, maxLvl - minLvl);

        for (short b = 0; b < bandCount; b++) {
            final short bandIdx = b;
            TextView tvBand = new TextView(this);
            tvBand.setText(mgr.getEqCenterFreqHz(bandIdx) + " Hz");
            tvBand.setTextColor(textPrimary);
            tvBand.setTextSize(12.5f);
            tvBand.setPadding(0, dp(8), 0, dp(2));
            panel.addView(tvBand);

            SeekBar sb = new SeekBar(this);
            sb.setMax(span);
            sb.setProgress(mgr.getEqBandLevel(bandIdx) - minLvl);
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int p, boolean fromUser) {
                    if (fromUser) mgr.setEqBandLevel(bandIdx, (short) (minLvl + p));
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
            panel.addView(sb);
        }

        TextView tvBass = new TextView(this);
        tvBass.setText("הדגשת בס (Bass Boost)");
        tvBass.setTextColor(accent);
        tvBass.setTypeface(Typeface.DEFAULT_BOLD);
        tvBass.setPadding(0, dp(10), 0, dp(2));
        panel.addView(tvBass);

        SeekBar sbBass = new SeekBar(this);
        sbBass.setMax(1000);
        sbBass.setProgress(mgr.getBassBoostStrength());
        sbBass.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int p, boolean fromUser) {
                if (fromUser) mgr.setBassBoostStrength((short) p);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        panel.addView(sbBass);

        ThemedDialogHelper.showCustomDialog(this, "אקולייזר והדגשת בס", null, scroll, "סגור", null, null, null, false);
    }

    // ==================== נגן וידאו מתקדם (Edge-to-Edge, מחוות אוטומטיות / כפתורים, 2x בהחזקה) ====================

    private void renderVideoPlayerOverlay(
            FrameLayout stageRoot,
            int cardColor,
            int textPrimary,
            int textSecondary,
            int accent,
            int theme
    ) {
        buildVideoFolderPlaylist(targetFile);

        final FrameLayout videoViewport = new FrameLayout(this);
        videoViewport.setBackgroundColor(Color.BLACK);
        videoViewport.setClipChildren(true);
        activeMediaViewport = videoViewport;

        videoTextureView = new TextureView(this);
        videoTextureView.setOpaque(true);
        videoViewport.addView(videoTextureView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));

        videoTextureView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if ((right - left) != (oldRight - oldLeft) || (bottom - top) != (oldBottom - oldTop)) {
                updateVideoAspectRatioMatrix();
            }
        });

        final TextView tvVideoDoubleTapBadge = createSeekOverlayBadge();
        videoViewport.addView(tvVideoDoubleTapBadge);

        setupVideoAdvancedTouchGestures(videoViewport, tvVideoDoubleTapBadge);

        stageRoot.addView(videoViewport, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // סעיף 30 + 44: שכבת כפתורים צפים במרכז הווידאו (מצב כפתורים לעומת נקי במחוות אוטומטיות)
        LinearLayout centerCluster = new LinearLayout(this);
        centerCluster.setOrientation(LinearLayout.HORIZONTAL);
        centerCluster.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        centerCluster.setGravity(Gravity.CENTER);
        centerCluster.setPadding(dp(12), dp(8), dp(12), dp(8));
        GradientDrawable clusterBg = new GradientDrawable();
        clusterBg.setCornerRadius(999f);
        clusterBg.setColor(Color.argb(135, 10, 15, 28));
        centerCluster.setBackground(clusterBg);
        activeVideoCenterOverlay = centerCluster;

        final ImageButton btnCenterPrev = createResponsiveRoundIconButton(R.drawable.ic_skip_prev, dp(40), dp(4), false, Color.WHITE, theme);
        final ImageButton btnCenterBack10 = createResponsiveRoundIconButton(R.drawable.ic_replay_10, dp(40), dp(4), false, Color.WHITE, theme);
        final ImageButton btnCenterPlayPause = createResponsiveRoundIconButton(R.drawable.ic_pause, dp(52), dp(6), true, accent, theme);
        final ImageButton btnCenterFwd10 = createResponsiveRoundIconButton(R.drawable.ic_forward_10, dp(40), dp(4), false, Color.WHITE, theme);
        final ImageButton btnCenterNext = createResponsiveRoundIconButton(R.drawable.ic_skip_next, dp(40), dp(4), false, Color.WHITE, theme);

        centerCluster.addView(btnCenterPrev);
        centerCluster.addView(btnCenterBack10);
        centerCluster.addView(btnCenterPlayPause);
        centerCluster.addView(btnCenterFwd10);
        centerCluster.addView(btnCenterNext);

        FrameLayout.LayoutParams centerLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        stageRoot.addView(centerCluster, centerLp);

        // סעיף 25 + 44: פאנל תחתון שקוף הצף מעל קצה הווידאו
        LinearLayout bottomCard = new LinearLayout(this);
        bottomCard.setOrientation(LinearLayout.VERTICAL);
        bottomCard.setPadding(dp(12), dp(8), dp(12), dp(10));
        GradientDrawable transBottom = new GradientDrawable();
        transBottom.setCornerRadius(dp(16));
        transBottom.setColor(Color.argb(180, 10, 15, 28));
        bottomCard.setBackground(transBottom);
        activeMediaBottomPanel = bottomCard;

        LinearLayout timeBar = new LinearLayout(this);
        timeBar.setOrientation(LinearLayout.HORIZONTAL);
        timeBar.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        timeBar.setGravity(Gravity.CENTER_VERTICAL);
        timeBar.setPadding(dp(2), dp(2), dp(2), dp(4));

        final TextView tvVidPos = new TextView(this);
        tvVidPos.setText("00:00");
        tvVidPos.setTextColor(Color.WHITE);
        tvVidPos.setTextSize(12f);
        tvVidPos.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        timeBar.addView(tvVidPos);

        final SeekBar videoSeek = new SeekBar(this);
        videoSeek.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        timeBar.addView(videoSeek, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final TextView tvVidDur = new TextView(this);
        tvVidDur.setText("00:00");
        tvVidDur.setTextColor(Color.parseColor("#CBD5E1"));
        tvVidDur.setTextSize(12f);
        tvVidDur.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        timeBar.addView(tvVidDur);

        bottomCard.addView(timeBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout bottomRow = new LinearLayout(this);
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        bottomRow.setGravity(Gravity.CENTER_VERTICAL);

        ImageButton btnVidRotate = createResponsiveRoundIconButton(R.drawable.ic_screen_rotate, dp(36), dp(4), false, Color.WHITE, theme);
        final ImageButton btnVidRepeat = createResponsiveRoundIconButton(R.drawable.ic_repeat_off, dp(36), dp(4), false, Color.WHITE, theme);
        final MaterialButton btnVidSpeed = createSpeedPillButton(videoSpeed, Color.WHITE, theme);
        MaterialButton btnProVideoEditor = createPencilEditorButton("עורך וידאו", accent, theme, v -> {
            pauseVideoSafely();
            Intent i = new Intent(this, ProVideoEditorActivity.class);
            i.putExtra(ProVideoEditorActivity.EXTRA_VIDEO_PATH, targetFile.getAbsolutePath());
            startActivity(i);
        });
        LinearLayout.LayoutParams edLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        edLp.setMarginStart(dp(6));
        edLp.setMarginEnd(dp(6));
        btnProVideoEditor.setLayoutParams(edLp);

        bottomRow.addView(btnVidRotate);
        bottomRow.addView(btnVidRepeat);
        bottomRow.addView(btnProVideoEditor);
        bottomRow.addView(btnVidSpeed);
        bottomCard.addView(bottomRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams bottomLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        bottomLp.setMargins(dp(10), 0, dp(10), dp(10));
        stageRoot.addView(bottomCard, bottomLp);

        final Runnable syncVidButtons = () -> {
            boolean playing = isVideoPlayingSafely();
            btnCenterPlayPause.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
            btnVidRepeat.setImageResource(resolveRepeatIcon(videoRepeatMode));
            btnVidSpeed.setText(formatSpeedLabel(videoSpeed));

            boolean autoMode = PrefsManager.isAutoGestureMode(this);
            int extraVis = autoMode ? View.GONE : View.VISIBLE;
            btnCenterPrev.setVisibility(extraVis);
            btnCenterBack10.setVisibility(extraVis);
            btnCenterFwd10.setVisibility(extraVis);
            btnCenterNext.setVisibility(extraVis);

            if (tvFolderTrackCounter != null && !videoPlaylist.isEmpty()) {
                tvFolderTrackCounter.setVisibility(View.VISIBLE);
                tvFolderTrackCounter.setText((currentVideoIdx + 1) + "/" + videoPlaylist.size());
            }
        };

        btnCenterPlayPause.setOnClickListener(v -> {
            if (!videoPrepared || videoPlayer == null) return;
            if (isVideoPlayingSafely()) videoPlayer.pause();
            else applyVideoSpeedInternal(true);
            syncVidButtons.run();
        });
        btnCenterBack10.setOnClickListener(v -> seekVideoBy(-10_000));
        btnCenterFwd10.setOnClickListener(v -> seekVideoBy(10_000));
        btnCenterPrev.setOnClickListener(v -> {
            if (videoPlaylist.isEmpty()) return;
            currentVideoIdx = (currentVideoIdx - 1 + videoPlaylist.size()) % videoPlaylist.size();
            loadVideoTrackAtIndex(currentVideoIdx, syncVidButtons);
        });
        btnCenterNext.setOnClickListener(v -> {
            if (videoPlaylist.isEmpty()) return;
            currentVideoIdx = (currentVideoIdx + 1) % videoPlaylist.size();
            loadVideoTrackAtIndex(currentVideoIdx, syncVidButtons);
        });

        btnVidRepeat.setOnClickListener(v -> {
            videoRepeatMode = (videoRepeatMode + 1) % 3;
            syncVidButtons.run();
        });
        btnVidRotate.setOnClickListener(v -> toggleScreenOrientation());
        btnVidSpeed.setOnClickListener(v -> showSpeedPickerDialog("בחירת מהירות הסרטון", videoSpeed, chosen -> {
            videoSpeed = chosen;
            applyVideoSpeedInternal(isVideoPlayingSafely());
            syncVidButtons.run();
        }));

        videoSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser && videoPrepared && videoPlayer != null) {
                    try { videoPlayer.seekTo(progress); } catch (Exception ignored) {}
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        videoTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
                activeVideoSurface = new Surface(surface);
                loadVideoTrackAtIndex(currentVideoIdx, syncVidButtons);
            }
            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
                updateVideoAspectRatioMatrix();
            }
            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
                releaseVideoPlayer();
                activeVideoSurface = null;
                return true;
            }
            @Override public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {}
        });

        videoTickerActive = true;
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!videoTickerActive) return;
                if (videoPrepared && videoPlayer != null) {
                    try {
                        int dur = Math.max(1, videoPlayer.getDuration());
                        int pos = Math.max(0, videoPlayer.getCurrentPosition());
                        videoSeek.setMax(dur);
                        videoSeek.setProgress(pos);
                        tvVidPos.setText(formatClock(pos));
                        tvVidDur.setText(formatClock(dur));
                        syncVidButtons.run();
                    } catch (Exception ignored) {
                    }
                }
                uiHandler.postDelayed(this, 250);
            }
        });
    }

    private void updateVideoAspectRatioMatrix() {
        if (videoTextureView == null) return;
        int viewW = videoTextureView.getWidth();
        int viewH = videoTextureView.getHeight();
        if (viewW <= 0 || viewH <= 0 || currentVideoWidth <= 0 || currentVideoHeight <= 0) return;
        float viewRatio = (float) viewW / (float) viewH;
        float videoRatio = (float) currentVideoWidth / (float) currentVideoHeight;
        float scaleX = videoRatio > viewRatio ? 1.0f : videoRatio / viewRatio;
        float scaleY = videoRatio > viewRatio ? viewRatio / videoRatio : 1.0f;

        Matrix matrix = new Matrix();
        matrix.setScale(scaleX, scaleY, viewW * 0.5f, viewH * 0.5f);
        videoTextureView.setTransform(matrix);
        videoTextureView.invalidate();
    }

    private void buildVideoFolderPlaylist(File initialVideo) {
        videoPlaylist.clear();
        currentVideoIdx = 0;
        File parent = initialVideo.getParentFile();
        if (parent != null && parent.exists() && parent.isDirectory()) {
            File[] siblings = parent.listFiles();
            if (siblings != null) {
                for (File f : siblings) {
                    if (f.isFile() && MediaFormatHelper.isVideoFile(f.getName())) {
                        videoPlaylist.add(f);
                    }
                }
            }
        }
        if (videoPlaylist.isEmpty()) {
            videoPlaylist.add(initialVideo);
        } else {
            Collections.sort(videoPlaylist, (a, b) -> StorageUtils.compareHebrewFirst(a.getName(), b.getName()));
            for (int i = 0; i < videoPlaylist.size(); i++) {
                if (videoPlaylist.get(i).getAbsolutePath().equals(initialVideo.getAbsolutePath())) {
                    currentVideoIdx = i;
                    break;
                }
            }
        }
    }

    private void loadVideoTrackAtIndex(int idx, Runnable onReadyUi) {
        if (activeVideoSurface == null || videoPlaylist.isEmpty()) return;
        releaseVideoPlayer();
        File nextFile = videoPlaylist.get(Math.max(0, Math.min(videoPlaylist.size() - 1, idx)));
        targetFile = nextFile;
        updateMarqueeHeaderName(nextFile.getName());
        resetVideoZoom();

        try {
            videoPlayer = new MediaPlayer();
            videoPlayer.setDataSource(nextFile.getAbsolutePath());
            videoPlayer.setSurface(activeVideoSurface);
            float vol = videoMuted ? 0f : 1f;
            videoPlayer.setVolume(vol, vol);
            videoPlayer.setOnVideoSizeChangedListener((mp, width, height) -> {
                if (width > 0 && height > 0) {
                    currentVideoWidth = width;
                    currentVideoHeight = height;
                    updateVideoAspectRatioMatrix();
                }
            });
            videoPlayer.setOnPreparedListener(mp -> {
                videoPrepared = true;
                currentVideoWidth = mp.getVideoWidth();
                currentVideoHeight = mp.getVideoHeight();
                updateVideoAspectRatioMatrix();
                applyVideoSpeedInternal(true);
                if (onReadyUi != null) onReadyUi.run();
            });
            videoPlayer.setOnCompletionListener(mp -> {
                if (videoRepeatMode == AudioPlayerManager.REPEAT_ONE) {
                    mp.seekTo(0);
                    applyVideoSpeedInternal(true);
                } else if (videoRepeatMode == AudioPlayerManager.REPEAT_FOLDER && !videoPlaylist.isEmpty()) {
                    currentVideoIdx = (currentVideoIdx + 1) % videoPlaylist.size();
                    loadVideoTrackAtIndex(currentVideoIdx, onReadyUi);
                } else if (videoAutoNext && currentVideoIdx + 1 < videoPlaylist.size()) {
                    currentVideoIdx++;
                    loadVideoTrackAtIndex(currentVideoIdx, onReadyUi);
                } else if (onReadyUi != null) {
                    onReadyUi.run();
                }
            });
            videoPlayer.prepareAsync();
        } catch (Exception e) {
            Toast.makeText(this, "שגיאה בטעינת הסרטון: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // סעיף 29 + 44: מחוות וידאו מתקדמות (ללא החלקה להחלפת סרטון; לחיצה ארוכה בצד ימין 2x ובצד שמאל חזרה 2x)
    private void setupVideoAdvancedTouchGestures(FrameLayout viewport, TextView badge) {
        final PointF lastPan = new PointF();
        final ScaleGestureDetector scaleDetector = new ScaleGestureDetector(this,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        videoScale = Math.max(1.0f, Math.min(8.0f, videoScale * detector.getScaleFactor()));
                        if (videoTextureView != null) {
                            videoTextureView.setScaleX(videoScale);
                            videoTextureView.setScaleY(videoScale);
                            if (videoScale <= 1.02f) {
                                videoTransX = 0f;
                                videoTransY = 0f;
                                videoTextureView.setTranslationX(0f);
                                videoTextureView.setTranslationY(0f);
                            }
                        }
                        return true;
                    }
                });

        final GestureDetector tapDetector = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(MotionEvent e) {
                        return true;
                    }

                    @Override
                    public boolean onSingleTapConfirmed(MotionEvent e) {
                        toggleMediaControlsVisibility();
                        return true;
                    }

                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        float w = Math.max(1f, viewport.getWidth());
                        float normX = e.getX() / w;
                        if (normX > 0.34f && normX < 0.66f) {
                            if (videoPrepared && videoPlayer != null) {
                                if (isVideoPlayingSafely()) videoPlayer.pause();
                                else applyVideoSpeedInternal(true);
                                flashStatusBadge(badge, isVideoPlayingSafely() ? "▶ נגן" : "⏸ השהה");
                            }
                        } else if (normX >= 0.66f) {
                            seekVideoBy(10_000);
                            flashSeekBadge(badge, true);
                        } else {
                            seekVideoBy(-10_000);
                            flashSeekBadge(badge, false);
                        }
                        return true;
                    }

                    @Override
                    public void onLongPress(MotionEvent e) {
                        if (!videoPrepared || videoPlayer == null) return;
                        float w = Math.max(1f, viewport.getWidth());
                        float normX = e.getX() / w;
                        if (normX >= 0.62f) {
                            // לחיצה ארוכה בצד ימין: הרצה קדימה במהירות 2x
                            speedBeforeHold = videoSpeed;
                            isHold2xForwardActive = true;
                            videoSpeed = 2.0f;
                            applyVideoSpeedInternal(true);
                            showPersistentHoldBadge(badge, "⏩ מהירות 2.0x");
                        } else if (normX <= 0.38f) {
                            // לחיצה ארוכה בצד שמאל: הרצה אחורה במהירות 2x
                            isHold2xRewindActive = true;
                            showPersistentHoldBadge(badge, "⏪ אחורה 2.0x");
                            rewindPulseRunnable = new Runnable() {
                                @Override
                                public void run() {
                                    if (!isHold2xRewindActive) return;
                                    seekVideoBy(-900);
                                    uiHandler.postDelayed(this, 220L);
                                }
                            };
                            uiHandler.post(rewindPulseRunnable);
                        }
                    }
                });

        viewport.setOnTouchListener((v, event) -> {
            if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true);
            scaleDetector.onTouchEvent(event);
            tapDetector.onTouchEvent(event);

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastPan.set(event.getX(), event.getY());
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (!scaleDetector.isInProgress() && videoScale > 1.04f && videoTextureView != null) {
                        float dx = event.getX() - lastPan.x;
                        float dy = event.getY() - lastPan.y;
                        float maxDx = (viewport.getWidth() * (videoScale - 1f)) * 0.5f;
                        float maxDy = (viewport.getHeight() * (videoScale - 1f)) * 0.5f;
                        videoTransX = Math.max(-maxDx, Math.min(maxDx, videoTransX + dx));
                        videoTransY = Math.max(-maxDy, Math.min(maxDy, videoTransY + dy));
                        videoTextureView.setTranslationX(videoTransX);
                        videoTextureView.setTranslationY(videoTransY);
                        lastPan.set(event.getX(), event.getY());
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    stopVideoHoldGestures(badge);
                    break;
            }
            return true;
        });
    }

    private void stopVideoHoldGestures(TextView badge) {
        if (isHold2xForwardActive) {
            isHold2xForwardActive = false;
            videoSpeed = speedBeforeHold;
            applyVideoSpeedInternal(isVideoPlayingSafely());
            badge.setVisibility(View.GONE);
        }
        if (isHold2xRewindActive) {
            isHold2xRewindActive = false;
            if (rewindPulseRunnable != null) uiHandler.removeCallbacks(rewindPulseRunnable);
            badge.setVisibility(View.GONE);
        }
    }

    private void showPersistentHoldBadge(TextView badge, String text) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = dp(76);
        badge.setLayoutParams(lp);
        badge.animate().cancel();
        badge.setText(text);
        badge.setAlpha(1f);
        badge.setScaleX(1f);
        badge.setScaleY(1f);
        badge.setVisibility(View.VISIBLE);
    }

    private void flashStatusBadge(TextView badge, String text) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        badge.setLayoutParams(lp);
        badge.setText(text);
        badge.animate().cancel();
        badge.setAlpha(1f);
        badge.setVisibility(View.VISIBLE);
        badge.animate().alpha(0f).setStartDelay(400).setDuration(220)
                .withEndAction(() -> badge.setVisibility(View.GONE)).start();
    }

    private void resetVideoZoom() {
        videoScale = 1.0f;
        videoTransX = 0f;
        videoTransY = 0f;
        if (videoTextureView != null) {
            videoTextureView.setScaleX(1.0f);
            videoTextureView.setScaleY(1.0f);
            videoTextureView.setTranslationX(0f);
            videoTextureView.setTranslationY(0f);
        }
    }

    private void toggleScreenOrientation() {
        boolean currentlyLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        isLandscapeLocked = !currentlyLandscape;
        setRequestedOrientation(isLandscapeLocked
                ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }

    private void showVideoSettingsDialog(int theme) {
        String muteStatus = videoMuted ? "מושתק (לחץ להפעלת קול)" : "פעיל (לחץ להשתקה)";
        String autoNextStatus = videoAutoNext ? "פעיל" : "כבוי";
        boolean autoGestures = PrefsManager.isAutoGestureMode(this);
        String repeatLabel = videoRepeatMode == AudioPlayerManager.REPEAT_ONE
                ? "חזרה על הסרטון עצמו"
                : (videoRepeatMode == AudioPlayerManager.REPEAT_FOLDER ? "חזרה על כל התיקייה" : "ללא חזרה");

        List<String> items = Arrays.asList(
                "מהירות הסרטון (" + formatSpeedLabel(videoSpeed) + ")",
                "מצב חזרה (" + repeatLabel + ")",
                "שמע הסרטון: " + muteStatus,
                "מעבר אוטומטי לסרטון הבא: " + autoNextStatus,
                "מצב מחוות אוטומטי: " + (autoGestures ? "פועל (נקי)" : "כבוי (כפתורים)"),
                "סיבוב מסך מלא (אופקי / אנכי)",
                "איפוס זום תצוגה (100%)",
                "ייצוא קטע פתיחה ל-GIF מונפש",
                "פתיחה בעורך וידאו מקצועי",
                "שיתוף קובץ"
        );

        ThemedDialogHelper.showThemedMenu(
                this,
                "הגדרות ואפשרויות הוידאו",
                targetFile.getName(),
                items,
                idx -> {
                    switch (idx) {
                        case 0:
                            showSpeedPickerDialog("בחירת מהירות הסרטון", videoSpeed, chosen -> {
                                videoSpeed = chosen;
                                applyVideoSpeedInternal(isVideoPlayingSafely());
                            });
                            break;
                        case 1:
                            showRepeatPickerDialog("מצב חזרה בוידאו", videoRepeatMode, false, chosen -> videoRepeatMode = chosen);
                            break;
                        case 2:
                            videoMuted = !videoMuted;
                            if (videoPlayer != null) {
                                float vol = videoMuted ? 0f : 1f;
                                videoPlayer.setVolume(vol, vol);
                            }
                            Toast.makeText(this, videoMuted ? "שמע הסרטון הושתק" : "שמע הסרטון הופעל", Toast.LENGTH_SHORT).show();
                            break;
                        case 3:
                            videoAutoNext = !videoAutoNext;
                            Toast.makeText(this, videoAutoNext ? "מעבר אוטומטי הופעל" : "מעבר אוטומטי כובה", Toast.LENGTH_SHORT).show();
                            break;
                        case 4:
                            boolean nextMode = !autoGestures;
                            PrefsManager.setAutoGestureMode(this, nextMode);
                            Toast.makeText(this, nextMode ? "מצב מחוות אוטומטי הופעל" : "מצב כפתורים קלאסי הופעל", Toast.LENGTH_SHORT).show();
                            break;
                        case 5:
                            toggleScreenOrientation();
                            break;
                        case 6:
                            resetVideoZoom();
                            Toast.makeText(this, "הזום אופס ל-100%", Toast.LENGTH_SHORT).show();
                            break;
                        case 7:
                            Toast.makeText(this, "מייצר GIF מהסרטון...", Toast.LENGTH_SHORT).show();
                            executor.execute(() -> {
                                try {
                                    File parent = targetFile.getParentFile() != null ? targetFile.getParentFile() : getFilesDir();
                                    File outGif = StorageUtils.uniqueDestinationFile(parent, targetFile.getName() + ".gif");
                                    GifToolsHelper.videoToGif(this, targetFile, outGif, 480, 10);
                                    uiHandler.post(() -> Toast.makeText(this, "נוצר GIF: " + outGif.getName(), Toast.LENGTH_LONG).show());
                                } catch (Exception e) {
                                    uiHandler.post(() -> Toast.makeText(this, "שגיאה: " + e.getMessage(), Toast.LENGTH_LONG).show());
                                }
                            });
                            break;
                        case 8:
                            pauseVideoSafely();
                            Intent ed = new Intent(this, ProVideoEditorActivity.class);
                            ed.putExtra(ProVideoEditorActivity.EXTRA_VIDEO_PATH, targetFile.getAbsolutePath());
                            startActivity(ed);
                            break;
                        case 9:
                            shareCurrentFile();
                            break;
                    }
                }
        );
    }

    private interface SpeedSelectedCallback {
        void onSpeedChosen(float speed);
    }

    private void showSpeedPickerDialog(String title, float current, SpeedSelectedCallback cb) {
        final float[] values = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f};
        List<String> labels = new ArrayList<>();
        for (float v : values) {
            String base = v == 1.0f ? "1.0x (מהירות רגילה)" : String.format(Locale.US, "%.2fx", v).replace("0x", "x");
            if (Math.abs(v - current) < 0.02f) {
                base = "✓  " + base;
            }
            labels.add(base);
        }
        ThemedDialogHelper.showThemedMenu(this, title, "בחר את מהירות ההפעלה הרצויה:", labels, idx -> cb.onSpeedChosen(values[idx]));
    }

    private interface RepeatSelectedCallback {
        void onRepeatChosen(int repeatMode);
    }

    private void showRepeatPickerDialog(String title, int currentMode, boolean isAudio, RepeatSelectedCallback cb) {
        String itemLabel = isAudio ? "חזרה על השיר עצמו" : "חזרה על הסרטון עצמו";
        String folderLabel = isAudio ? "חזרה על כל הקבצים בתיקייה" : "חזרה על כל הסרטונים בתיקייה";
        List<String> opts = Arrays.asList(
                (currentMode == AudioPlayerManager.REPEAT_OFF ? "✓  " : "") + "ללא חזרה",
                (currentMode == AudioPlayerManager.REPEAT_ONE ? "✓  " : "") + itemLabel,
                (currentMode == AudioPlayerManager.REPEAT_FOLDER ? "✓  " : "") + folderLabel
        );
        ThemedDialogHelper.showThemedMenu(this, title, "בחר אופן חזרה בסיום הניגון:", opts, cb::onRepeatChosen);
    }

    private interface DoubleTapSeekAction {
        void onSeekSeconds(int deltaSeconds);
    }

    private void attachDoubleTapSeekGesture(View target, TextView badge, DoubleTapSeekAction action) {
        final GestureDetector detector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                boolean rightSide = e.getX() >= (target.getWidth() / 2f);
                if (rightSide) {
                    action.onSeekSeconds(10);
                    flashSeekBadge(badge, true);
                } else {
                    action.onSeekSeconds(-10);
                    flashSeekBadge(badge, false);
                }
                return true;
            }
        });
        target.setOnTouchListener((v, ev) -> detector.onTouchEvent(ev));
    }

    private TextView createSeekOverlayBadge() {
        TextView badge = new TextView(this);
        badge.setTextColor(Color.WHITE);
        badge.setTextSize(17f);
        badge.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        badge.setTextDirection(View.TEXT_DIRECTION_LTR);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(18), dp(10), dp(18), dp(10));
        GradientDrawable pill = new GradientDrawable();
        pill.setCornerRadius(999f);
        pill.setColor(Color.argb(215, 29, 78, 216));
        pill.setStroke(dp(1.5f), Color.WHITE);
        badge.setBackground(pill);
        badge.setVisibility(View.GONE);
        return badge;
    }

    private void flashSeekBadge(TextView badge, boolean forwardRight) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                (forwardRight ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL
        );
        lp.leftMargin = dp(20);
        lp.rightMargin = dp(20);
        badge.setLayoutParams(lp);
        badge.setText(forwardRight ? "\u200E+10" : "\u200E-10");
        badge.animate().cancel();
        badge.setAlpha(1f);
        badge.setScaleX(0.85f);
        badge.setScaleY(0.85f);
        badge.setVisibility(View.VISIBLE);
        badge.animate()
                .scaleX(1.05f)
                .scaleY(1.05f)
                .setDuration(140)
                .withEndAction(() -> badge.animate()
                        .alpha(0f)
                        .setStartDelay(380)
                        .setDuration(200)
                        .withEndAction(() -> badge.setVisibility(View.GONE))
                        .start())
                .start();
    }

    private ImageButton createResponsiveRoundIconButton(int drawableRes, int sizePx, int marginPx, boolean primaryFilled, int accent, int theme) {
        ImageButton btn = new ImageButton(this);
        btn.setImageResource(drawableRes);
        btn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int pad = Math.max(dp(7), Math.round(sizePx * (primaryFilled ? 0.24f : 0.23f)));
        btn.setPadding(pad, pad, pad, pad);

        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        if (primaryFilled) {
            circle.setColor(accent);
            btn.setColorFilter(Color.WHITE);
        } else {
            circle.setColor(Color.argb(46, Color.red(accent), Color.green(accent), Color.blue(accent)));
            circle.setStroke(Math.max(2, dp(1.2f)), accent);
            btn.setColorFilter(accent);
        }
        btn.setBackground(circle);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sizePx, sizePx);
        lp.setMargins(marginPx, 0, marginPx, 0);
        btn.setLayoutParams(lp);
        return btn;
    }

    private MaterialButton createSpeedPillButton(float speed, int accent, int theme) {
        MaterialButton btn = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btn.setText(formatSpeedLabel(speed));
        btn.setAllCaps(false);
        btn.setTextSize(12f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setTextColor(accent);
        btn.setStrokeColor(ColorStateList.valueOf(accent));
        btn.setIconResource(R.drawable.ic_speed);
        btn.setIconTint(ColorStateList.valueOf(accent));
        btn.setIconSize(dp(16));
        btn.setIconPadding(dp(5));
        btn.setCornerRadius(999);
        btn.setMinHeight(0);
        btn.setMinimumHeight(0);
        btn.setPadding(dp(10), dp(6), dp(10), dp(6));
        return btn;
    }

    private MaterialButton createPencilEditorButton(String label, int accent, int theme, View.OnClickListener onClick) {
        MaterialButton btn = new MaterialButton(this);
        btn.setText(label);
        btn.setAllCaps(false);
        btn.setTextSize(12.5f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setBackgroundTintList(ColorStateList.valueOf(accent));
        btn.setTextColor(Color.WHITE);
        btn.setIconResource(R.drawable.ic_edit_pencil);
        btn.setIconTint(ColorStateList.valueOf(Color.WHITE));
        btn.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        btn.setIconPadding(dp(6));
        btn.setCornerRadius(dp(10));
        btn.setMinHeight(0);
        btn.setMinimumHeight(0);
        btn.setPadding(dp(12), dp(8), dp(12), dp(8));
        btn.setOnClickListener(onClick);
        return btn;
    }

    private int resolveRepeatIcon(int mode) {
        if (mode == AudioPlayerManager.REPEAT_ONE) return R.drawable.ic_repeat_one;
        if (mode == AudioPlayerManager.REPEAT_FOLDER) return R.drawable.ic_repeat_all;
        return R.drawable.ic_repeat_off;
    }

    private String formatSpeedLabel(float speed) {
        if (Math.abs(speed - Math.round(speed)) < 0.02f) {
            return String.format(Locale.US, "מהירות %.1fx", speed);
        }
        return String.format(Locale.US, "מהירות %.2fx", speed).replaceAll("0x$", "x");
    }

    private boolean isVideoPlayingSafely() {
        try {
            return videoPrepared && videoPlayer != null && videoPlayer.isPlaying();
        } catch (Exception e) {
            return false;
        }
    }

    private void pauseVideoSafely() {
        try {
            if (isVideoPlayingSafely()) videoPlayer.pause();
        } catch (Exception ignored) {
        }
    }

    private void seekVideoBy(int deltaMs) {
        if (!videoPrepared || videoPlayer == null) return;
        try {
            int dur = Math.max(1, videoPlayer.getDuration());
            int next = Math.max(0, Math.min(dur, videoPlayer.getCurrentPosition() + deltaMs));
            videoPlayer.seekTo(next);
        } catch (Exception ignored) {
        }
    }

    private void applyVideoSpeedInternal(boolean shouldPlay) {
        if (!videoPrepared || videoPlayer == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PlaybackParams p = videoPlayer.getPlaybackParams();
                if (p == null) p = new PlaybackParams();
                p.setSpeed(videoSpeed);
                videoPlayer.setPlaybackParams(p);
                if (!shouldPlay && videoPlayer.isPlaying()) {
                    videoPlayer.pause();
                } else if (shouldPlay && !videoPlayer.isPlaying()) {
                    videoPlayer.start();
                }
            } else if (shouldPlay) {
                videoPlayer.start();
            }
        } catch (Exception e) {
            try {
                if (shouldPlay && !videoPlayer.isPlaying()) videoPlayer.start();
            } catch (Exception ignored) {
            }
        }
    }

    private void releaseVideoPlayer() {
        videoPrepared = false;
        currentVideoWidth = 0;
        currentVideoHeight = 0;
        if (rewindPulseRunnable != null) uiHandler.removeCallbacks(rewindPulseRunnable);
        if (videoPlayer != null) {
            try { videoPlayer.stop(); } catch (Exception ignored) {}
            try { videoPlayer.release(); } catch (Exception ignored) {}
            videoPlayer = null;
        }
    }

    // ==================== מציג PDF ====================

    private void renderPdfViewer(LinearLayout host, int cardColor, int textSecondary, int theme) {
        LinearLayout card = createSurfaceCard(cardColor);
        card.setGravity(Gravity.CENTER);

        ZoomableImageView pageImage = new ZoomableImageView(this);
        pageImage.setBackgroundColor(Color.WHITE);
        card.addView(pageImage, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView tvPageIndicator = new TextView(this);
        tvPageIndicator.setTextColor(textSecondary);
        tvPageIndicator.setGravity(Gravity.CENTER);
        tvPageIndicator.setPadding(0, dp(8), 0, dp(8));
        card.addView(tvPageIndicator);

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);

        Button btnPrev = ThemedDialogHelper.createThemedButton(this, "עמוד קודם", theme, false, false);
        Button btnNext = ThemedDialogHelper.createThemedButton(this, "עמוד הבא", theme, true, false);
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp1.setMarginEnd(dp(8));
        btnPrev.setLayoutParams(lp1);
        btnNext.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        nav.addView(btnPrev);
        nav.addView(btnNext);
        card.addView(nav, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        host.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        try {
            pdfDescriptor = ParcelFileDescriptor.open(targetFile, ParcelFileDescriptor.MODE_READ_ONLY);
            pdfRenderer = new PdfRenderer(pdfDescriptor);
            final int totalPages = pdfRenderer.getPageCount();

            Runnable renderPage = () -> {
                if (pdfRenderer == null || totalPages == 0) return;
                try (PdfRenderer.Page page = pdfRenderer.openPage(currentPdfPage)) {
                    int w = Math.min(3600, Math.max(1200, page.getWidth() * 3));
                    int h = Math.min(3600, Math.max(1500, page.getHeight() * 3));
                    Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    bmp.eraseColor(Color.WHITE);
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    pageImage.setImageBitmap(bmp);
                    tvPageIndicator.setText("עמוד " + (currentPdfPage + 1) + " מתוך " + totalPages);
                    btnPrev.setEnabled(currentPdfPage > 0);
                    btnNext.setEnabled(currentPdfPage < totalPages - 1);
                }
            };
            renderPage.run();

            btnPrev.setOnClickListener(v -> {
                if (currentPdfPage > 0) {
                    currentPdfPage--;
                    renderPage.run();
                }
            });
            btnNext.setOnClickListener(v -> {
                if (currentPdfPage < totalPages - 1) {
                    currentPdfPage++;
                    renderPage.run();
                }
            });
        } catch (Exception e) {
            tvPageIndicator.setText("לא ניתן להציג קובץ PDF זה: " + e.getMessage());
        }
    }

    // ==================== מציג ועורך ארכיונים אינטראקטיבי (ZIP/TAR/GZ + סיסמה) ====================

    private void renderArchiveExtractor(LinearLayout host, int cardColor, int textPrimary, int textSecondary, int accent, int theme) {
        LinearLayout card = createSurfaceCard(cardColor);

        TextView tvHeader = new TextView(this);
        tvHeader.setText("מנהל ארכיון ודחיסה מתקדם");
        tvHeader.setTextColor(textPrimary);
        tvHeader.setTextSize(16f);
        tvHeader.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(tvHeader);

        final TextView tvSub = new TextView(this);
        tvSub.setText("טוען תוכן ארכיון...");
        tvSub.setTextColor(textSecondary);
        tvSub.setTextSize(12f);
        tvSub.setPadding(0, dp(4), 0, dp(8));
        card.addView(tvSub);

        ScrollView scroll = new ScrollView(this);
        final LinearLayout entriesList = new LinearLayout(this);
        entriesList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(entriesList);
        card.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bottomActions = new LinearLayout(this);
        bottomActions.setOrientation(LinearLayout.HORIZONTAL);
        bottomActions.setPadding(0, dp(10), 0, 0);

        Button btnExtract = ThemedDialogHelper.createThemedButton(this, "חלץ הכל לכאן", theme, true, false);
        LinearLayout.LayoutParams exLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        btnExtract.setLayoutParams(exLp);
        bottomActions.addView(btnExtract);

        if (MediaFormatHelper.isZipContainer(targetFile.getName())) {
            Button btnPwd = ThemedDialogHelper.createThemedButton(this, "סיסמה", theme, false, false);
            LinearLayout.LayoutParams pwLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            pwLp.setMarginStart(dp(6));
            btnPwd.setLayoutParams(pwLp);
            btnPwd.setOnClickListener(v -> promptArchivePassword(null));
            bottomActions.addView(btnPwd);
        }

        card.addView(bottomActions);
        host.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final Runnable[] reloadArchive = new Runnable[1];
        reloadArchive[0] = () -> executor.execute(() -> {
            try {
                List<ArchiveUtils.ArchiveEntryInfo> entries = ArchiveUtils.listArchiveEntries(targetFile);
                boolean needsPwd = ArchiveUtils.archiveRequiresPassword(targetFile);
                boolean isZip = MediaFormatHelper.isZipContainer(targetFile.getName());

                uiHandler.post(() -> {
                    entriesList.removeAllViews();
                    tvSub.setText("נמצאו " + entries.size() + " פריטים • " + StorageUtils.formatSize(targetFile.length())
                            + (needsPwd ? " • מוגן בסיסמה 🔒" : "")
                            + (isZip ? "\nלחץ על קובץ לפתיחה/עריכה, לחיצה ארוכה למחיקה" : ""));

                    for (ArchiveUtils.ArchiveEntryInfo item : entries) {
                        LinearLayout row = new LinearLayout(this);
                        row.setOrientation(LinearLayout.HORIZONTAL);
                        row.setGravity(Gravity.CENTER_VERTICAL);
                        row.setPadding(dp(10), dp(8), dp(10), dp(8));

                        GradientDrawable bg = new GradientDrawable();
                        bg.setCornerRadius(dp(8));
                        bg.setColor(ThemeHelper.getBgColor(this, theme));
                        row.setBackground(bg);

                        LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        rLp.bottomMargin = dp(4);
                        row.setLayoutParams(rLp);

                        TextView tvName = new TextView(this);
                        tvName.setText((item.directory ? "📁 " : "📄 ") + item.name + (item.encrypted ? " 🔒" : ""));
                        tvName.setTextColor(textPrimary);
                        tvName.setTextSize(13f);
                        row.addView(tvName, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                        if (!item.directory && item.size >= 0) {
                            TextView tvSize = new TextView(this);
                            tvSize.setText(StorageUtils.formatSize(item.size));
                            tvSize.setTextColor(textSecondary);
                            tvSize.setTextSize(11f);
                            row.addView(tvSize);
                        }

                        if (!item.directory) {
                            row.setOnClickListener(v -> openArchiveEntry(item, () -> openArchiveEntry(item, null)));
                            if (isZip && !item.encrypted) {
                                row.setOnLongClickListener(v -> {
                                    ThemedDialogHelper.showCustomDialog(this, "מחיקה מהארכיון",
                                            "האם למחוק את \"" + item.name + "\" מתוך ה-ZIP?", null,
                                            "מחק", () -> executor.execute(() -> {
                                                try {
                                                    ArchiveUtils.deleteZipEntry(targetFile, item.name);
                                                    uiHandler.post(reloadArchive[0]);
                                                } catch (Exception e) {
                                                    uiHandler.post(() -> Toast.makeText(this, "שגיאה: " + e.getMessage(), Toast.LENGTH_LONG).show());
                                                }
                                            }), "ביטול", null, true);
                                    return true;
                                });
                            }
                        }
                        entriesList.addView(row);
                    }
                });
            } catch (Exception e) {
                uiHandler.post(() -> tvSub.setText("שגיאה בקריאת הארכיון: " + e.getMessage()));
            }
        });
        reloadArchive[0].run();

        btnExtract.setOnClickListener(v -> {
            if (ArchiveUtils.archiveRequiresPassword(targetFile) && archivePassword.isEmpty()) {
                promptArchivePassword(() -> runFullExtraction(btnExtract));
            } else {
                runFullExtraction(btnExtract);
            }
        });
    }

    private void promptArchivePassword(Runnable onEntered) {
        EditText et = new EditText(this);
        et.setHint("הזן סיסמת ארכיון...");
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        et.setText(archivePassword);
        ThemedDialogHelper.showCustomDialog(this, "ארכיון מוגן בסיסמה",
                "הזן את הסיסמה לפתיחה ועריכה:", et, "אישור", () -> {
                    archivePassword = et.getText() != null ? et.getText().toString() : "";
                    if (onEntered != null) onEntered.run();
                }, "ביטול", null, false);
    }

    private void runFullExtraction(Button btnExtract) {
        btnExtract.setEnabled(false);
        executor.execute(() -> {
            try {
                String baseName = targetFile.getName().replaceAll("(?i)\\.(tar\\.gz|tgz|zip|tar|gz|jar|cbz|apk|apkx|apk\\+|apks|apkm|xapk|apkp)$", "");
                File outDir = StorageUtils.uniqueDestinationFile(
                        targetFile.getParentFile() != null ? targetFile.getParentFile() : getFilesDir(),
                        baseName + "_מחולץ");
                int count = ArchiveUtils.extractArchive(targetFile, outDir, archivePassword);
                uiHandler.post(() -> {
                    btnExtract.setEnabled(true);
                    ThemedDialogHelper.showCustomDialog(this, "החילוץ הושלם בהצלחה",
                            "חולצו " + count + " קבצים אל:\n" + outDir.getAbsolutePath(),
                            null, "הבנתי", this::finish, null, null, false);
                });
            } catch (Exception e) {
                uiHandler.post(() -> {
                    btnExtract.setEnabled(true);
                    Toast.makeText(this, "שגיאה בחילוץ: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void openArchiveEntry(ArchiveUtils.ArchiveEntryInfo item, Runnable retryAfterPwd) {
        if (item.encrypted && archivePassword.isEmpty() && retryAfterPwd != null) {
            promptArchivePassword(retryAfterPwd);
            return;
        }
        String leaf = new File(item.name).getName().toLowerCase(Locale.ROOT);
        if (MediaFormatHelper.isZipContainer(targetFile.getName())
                && !item.encrypted
                && (MediaFormatHelper.isTextFile(leaf) || MediaFormatHelper.isCodeOrSyntaxFile(leaf))) {
            Intent ci = new Intent(this, CodeEditorActivity.class);
            ci.putExtra(CodeEditorActivity.EXTRA_ARCHIVE_PATH, targetFile.getAbsolutePath());
            ci.putExtra(CodeEditorActivity.EXTRA_ARCHIVE_ENTRY, item.name);
            ci.putExtra(CodeEditorActivity.EXTRA_ARCHIVE_PASSWORD, archivePassword);
            startActivity(ci);
            return;
        }

        executor.execute(() -> {
            try {
                byte[] bytes = ArchiveUtils.readEntryBytes(targetFile, item.name, archivePassword);
                File previewDir = new File(getCacheDir(), "arch_preview");
                previewDir.mkdirs();
                File tmp = new File(previewDir, new File(item.name).getName());
                try (FileOutputStream fos = new FileOutputStream(tmp)) {
                    fos.write(bytes);
                }
                uiHandler.post(() -> FileOpenWithHelper.tryOpenByDefaultOrBuiltin(this, tmp));
            } catch (Exception e) {
                uiHandler.post(() -> Toast.makeText(this, "שגיאה בפתיחת פריט: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void renderOfficeDocumentViewer(LinearLayout host, int cardColor, int textPrimary, int textSecondary) {
        LinearLayout card = createSurfaceCard(cardColor);

        TextView tvSub = new TextView(this);
        tvSub.setText("קורא מסמכים מובנה • " + StorageUtils.formatSize(targetFile.length()));
        tvSub.setTextColor(textSecondary);
        tvSub.setTextSize(12f);
        tvSub.setPadding(0, 0, 0, dp(10));
        card.addView(tvSub);

        ScrollView scroll = new ScrollView(this);
        TextView tvDoc = new TextView(this);
        tvDoc.setText("טוען תוכן מסמך...");
        tvDoc.setTextColor(textPrimary);
        tvDoc.setTextSize(ThemeHelper.spScaled(this, 14f));
        tvDoc.setTextIsSelectable(true);
        tvDoc.setLineSpacing(6f, 1.2f);
        scroll.addView(tvDoc);
        card.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        host.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        executor.execute(() -> {
            String content = ArchiveUtils.extractOfficeDocumentText(targetFile);
            uiHandler.post(() -> tvDoc.setText(content));
        });
    }

    private LinearLayout createSurfaceCard(int cardColor) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(18));
        shape.setColor(cardColor);
        card.setBackground(shape);
        return card;
    }

    private void shareCurrentFile() {
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", targetFile);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("*/*");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "שיתוף קובץ"));
        } catch (Exception e) {
            Toast.makeText(this, "שגיאה בשיתוף הקובץ: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String formatClock(int ms) {
        int sec = Math.max(0, ms / 1000);
        return String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60);
    }

    private boolean endsWithAny(String name, String... suffixes) {
        for (String s : suffixes) {
            if (name.endsWith(s)) return true;
        }
        return false;
    }

    private int dp(float dpVal) {
        return Math.round(dpVal * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        detachAudioCallbacksOnly();
        videoTickerActive = false;
        releaseVideoPlayer();

        if (pdfRenderer != null) {
            try { pdfRenderer.close(); } catch (Exception ignored) {}
        }
        if (pdfDescriptor != null) {
            try { pdfDescriptor.close(); } catch (Exception ignored) {}
        }
        executor.shutdownNow();
    }
}
