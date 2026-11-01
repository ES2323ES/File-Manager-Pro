package com.aishhatorah.filemanagerpro;

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
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;

public class BuiltInViewerActivity extends AppCompatActivity {

    public static final String EXTRA_FILE_PATH = "extra_file_path";
    public static final String EXTRA_FROM_NOTIFICATION = "extra_from_notification";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private File targetFile;
    private boolean openedFromNotification = false;

    private LinearLayout topBar;
    private TextView tvMarqueeTitle;
    private TextView tvFolderTrackCounter;
    private MaterialButton btnTopRightVideoSettings;

    // סעיף א + ב: מצב תצוגת מדיה נקייה (הקשה בודדת בוידאו ובתמונות מסתירה/מציגה את כלל האופציות, ובסיבוב מסך וידאו במסך מלא!)
    private boolean isImageMode = false;
    private boolean mediaControlsHidden = false;
    private View activeMediaBottomPanel = null;
    private View activeImagePrevFloat = null;
    private View activeImageNextFloat = null;
    private FrameLayout activeMediaStageRoot = null;
    private FrameLayout activeMediaViewport = null;

    // סעיף ג: רשימת התמונות בתיקייה למעבר קדימה ואחורה בכל תמונה שנפתחת
    private final List<File> imagePlaylist = new ArrayList<>();
    private int currentImageIdx = 0;
    private ZoomableImageView activeZoomImageView = null;
    private int imageLoadGeneration = 0;

    // מצב אודיו (מנוהל ע"י AudioPlayerManager כדי להמשיך ברקע ביציאה מהשיר)
    private boolean isAudioMode = false;
    private boolean audioTickerActive = false;
    private AudioPlayerManager.PlaybackListener audioListener;

    // מצב וידאו מובנה מתקדם (צלול וללא מתיחה/מעיכה - סעיפים ה, ו)
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

    private ParcelFileDescriptor pdfDescriptor;
    private PdfRenderer pdfRenderer;
    private int currentPdfPage = 0;

    // סעיף ב (0.2.6): עריכת קובץ טקסט ישירות בנגיעה באותיות
    private EditText activeTextEditor = null;
    private String loadedOriginalText = "";
    private boolean textEditorDirty = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        openedFromNotification = getIntent().getBooleanExtra(EXTRA_FROM_NOTIFICATION, false);
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

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleViewerCloseOrBack();
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

    // סעיפים ב, ז: בסיבוב מסך וידאו מוצג בתצוגה מלאה בכל המסך ללא הפסקת הניגון, וכל המסכים מתאימים את עצמם!
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        boolean landscapeNow = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE;
        if (isVideoMode) {
            // סעיף ב: בסיבוב מסך לאופקי הוידאו מוצג מיד בתצוגה מלאה בכל המסך!
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

    // סעיף א: הקשה בודדת על תמונה או וידאו מציגה את התמונה/וידאו בלבד ללא שאר האופציות, ובהקשה שנייה האופציות מוצגות
    private void toggleMediaControlsVisibility() {
        mediaControlsHidden = !mediaControlsHidden;
        applyMediaImmersiveAndLayoutState();
    }

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
            if (isVideoMode && isLandscape) {
                topBar.setBackgroundColor(Color.argb(190, 8, 15, 30));
            } else {
                int theme = PrefsManager.getThemeMode(this);
                topBar.setBackgroundColor(ThemeHelper.getTopBarColor(theme));
            }
        }

        if (activeMediaBottomPanel != null) {
            activeMediaBottomPanel.setVisibility(mediaControlsHidden ? View.GONE : View.VISIBLE);
        }
        if (activeImagePrevFloat != null) {
            activeImagePrevFloat.setVisibility((!mediaControlsHidden && imagePlaylist.size() > 1) ? View.VISIBLE : View.GONE);
        }
        if (activeImageNextFloat != null) {
            activeImageNextFloat.setVisibility((!mediaControlsHidden && imagePlaylist.size() > 1) ? View.VISIBLE : View.GONE);
        }

        if (activeMediaStageRoot != null) {
            int theme = PrefsManager.getThemeMode(this);
            boolean pureBlackStage = mediaControlsHidden || (isVideoMode && isLandscape);
            activeMediaStageRoot.setBackgroundColor(pureBlackStage ? Color.BLACK : ThemeHelper.getBgColor(theme));
        }

        // התאמת מרווחי הבמה כך שבסיבוב מסך בוידאו (סעיף ב) או במצב נקי (סעיף א) הוידאו/תמונה יתפסו 100% משטח המסך!
        if (activeMediaViewport != null) {
            FrameLayout.LayoutParams vpLp = (FrameLayout.LayoutParams) activeMediaViewport.getLayoutParams();
            if (vpLp != null) {
                if (mediaControlsHidden || (isVideoMode && isLandscape)) {
                    vpLp.topMargin = 0;
                    vpLp.bottomMargin = 0;
                    vpLp.leftMargin = 0;
                    vpLp.rightMargin = 0;
                } else {
                    int topInset = isVideoMode ? dp(68) : dp(56);
                    int botInset = isVideoMode ? dp(148) : dp(104);
                    if (isLandscape && isImageMode) {
                        topInset = dp(48);
                        botInset = dp(74);
                    }
                    vpLp.topMargin = topInset;
                    vpLp.bottomMargin = botInset;
                    vpLp.leftMargin = dp(isVideoMode ? 6 : 4);
                    vpLp.rightMargin = dp(isVideoMode ? 6 : 4);
                }
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

    private void handleViewerCloseOrBack() {
        if (activeTextEditor != null && textEditorDirty) {
            ThemedDialogHelper.showCustomDialog(
                    this,
                    "שמירת שינויים בקובץ",
                    "בוצעו שינויים בטקסט של \"" + (targetFile != null ? targetFile.getName() : "") + "\". האם לשמור אותם לפני הסגירה?",
                    null,
                    "שמור וסגור",
                    () -> {
                        saveEditedTextFileNow(false);
                        textEditorDirty = false;
                        proceedCloseOrNavigateBack();
                    },
                    "צא ללא שמירה",
                    () -> {
                        textEditorDirty = false;
                        proceedCloseOrNavigateBack();
                    },
                    false
            );
            return;
        }
        proceedCloseOrNavigateBack();
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
        activeTextEditor = null;
        textEditorDirty = false;
        activeMediaBottomPanel = null;
        activeImagePrevFloat = null;
        activeImageNextFloat = null;
        activeMediaStageRoot = null;
        activeMediaViewport = null;
        activeZoomImageView = null;

        int theme = PrefsManager.getThemeMode(this);
        ThemeHelper.applyWindowStatusBar(this, theme);
        int bgColor = ThemeHelper.getBgColor(theme);
        int cardColor = ThemeHelper.getCardColor(theme);
        int textPrimary = ThemeHelper.getPrimaryTextColor(theme);
        int textSecondary = ThemeHelper.getSecondaryTextColor(theme);
        int accent = ThemeHelper.getAccentColor(theme);

        String lower = targetFile.getName().toLowerCase(Locale.ROOT);
        isImageMode = MediaFormatHelper.isImageFile(lower);
        isAudioMode = !isImageMode && MediaFormatHelper.isAudioFile(lower);
        isVideoMode = !isImageMode && !isAudioMode && MediaFormatHelper.isVideoFile(lower);

        boolean isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        mediaControlsHidden = isVideoMode && isLandscape;

        topBar = buildTopBar(theme, textPrimary, textSecondary, accent, isLandscape);

        // עבור תמונות ווידאו: במת FrameLayout מלאה המאפשרת מסך מלא ב-100% בהקשה בודדת (סעיף א) ובסיבוב מסך (סעיף ב)!
        if (isImageMode || isVideoMode) {
            FrameLayout stageRoot = new FrameLayout(this);
            stageRoot.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            stageRoot.setBackgroundColor(bgColor);
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
        } else if (lower.endsWith(".pdf")) {
            renderPdfViewer(contentArea, cardColor, textSecondary, theme);
        } else if (MediaFormatHelper.isZipOrArchiveFile(lower) || MediaFormatHelper.isInstallableAppPackage(lower)) {
            renderArchiveExtractor(contentArea, cardColor, textPrimary, textSecondary, theme);
        } else if (endsWithAny(lower, ".docx", ".xlsx", ".pptx", ".odt", ".ods", ".odp")) {
            renderOfficeDocumentViewer(contentArea, cardColor, textPrimary, textSecondary);
        } else {
            renderTextDocumentViewer(contentArea, cardColor, textPrimary, textSecondary, theme);
        }
    }

    private LinearLayout buildTopBar(int theme, int textPrimary, int textSecondary, int accent, boolean isLandscape) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(ThemeHelper.getTopBarColor(theme));
        int vPad = isLandscape ? dp(6) : dp(9);
        bar.setPadding(dp(10), vPad, dp(10), vPad);

        LinearLayout rightActionsCol = new LinearLayout(this);
        rightActionsCol.setOrientation(LinearLayout.VERTICAL);
        rightActionsCol.setGravity(Gravity.CENTER_HORIZONTAL);

        Button btnClose = ThemedDialogHelper.createThemedButton(this, "סגור", theme, false, false);
        btnClose.setPadding(dp(12), dp(5), dp(12), dp(5));
        btnClose.setOnClickListener(v -> handleViewerCloseOrBack());
        rightActionsCol.addView(btnClose);

        btnTopRightVideoSettings = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnTopRightVideoSettings.setText("הגדרות הוידאו");
        btnTopRightVideoSettings.setAllCaps(false);
        btnTopRightVideoSettings.setTextSize(10.5f);
        btnTopRightVideoSettings.setTypeface(Typeface.DEFAULT_BOLD);
        btnTopRightVideoSettings.setTextColor(accent);
        btnTopRightVideoSettings.setStrokeColor(ColorStateList.valueOf(accent));
        btnTopRightVideoSettings.setIconResource(R.drawable.ic_settings);
        btnTopRightVideoSettings.setIconTint(ColorStateList.valueOf(accent));
        btnTopRightVideoSettings.setIconSize(dp(14));
        btnTopRightVideoSettings.setIconPadding(dp(4));
        btnTopRightVideoSettings.setCornerRadius(dp(9));
        btnTopRightVideoSettings.setInsetTop(0);
        btnTopRightVideoSettings.setInsetBottom(0);
        btnTopRightVideoSettings.setMinHeight(0);
        btnTopRightVideoSettings.setMinimumHeight(0);
        btnTopRightVideoSettings.setPadding(dp(8), dp(3), dp(8), dp(3));
        LinearLayout.LayoutParams vsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vsLp.topMargin = dp(4);
        btnTopRightVideoSettings.setLayoutParams(vsLp);
        btnTopRightVideoSettings.setVisibility(isVideoMode ? View.VISIBLE : View.GONE);
        btnTopRightVideoSettings.setOnClickListener(v -> showVideoSettingsDialog(theme));
        rightActionsCol.addView(btnTopRightVideoSettings);

        bar.addView(rightActionsCol);

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
        tvMarqueeTitle.setTextSize(isLandscape ? 14f : 15f);
        tvMarqueeTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvMarqueeTitle.setTextColor(textPrimary);
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
        tvFolderTrackCounter.setTextColor(textSecondary);
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

        Button btnShare = ThemedDialogHelper.createThemedButton(this, "שיתוף", theme, true, false);
        btnShare.setPadding(dp(12), dp(5), dp(12), dp(5));
        btnShare.setOnClickListener(v -> shareCurrentFile());
        bar.addView(btnShare);

        return bar;
    }

    private void updateMarqueeHeaderName(String name) {
        if (tvMarqueeTitle != null && name != null) {
            tvMarqueeTitle.setText("   " + name + "   •   " + name + "   ");
            tvMarqueeTitle.setSelected(true);
        }
    }

    /**
     * סעיפים א, ג, ד, ה:
     * - א: הקשה בודדת על התמונה מסתירה את כל האופציות ומציגה את התמונה בלבד, והקשה שנייה מחזירה את האופציות!
     * - ג: העברה קדימה ואחורה לכל התמונות שבאותה תיקייה (גם בלחצנים וגם בהחלקת אצבע)!
     * - ד: הגדלה חזקה מאוד של התמונה (עד פי 25x)!
     * - ה: הצגה צלולה וחדה מאוד ברזולוציה גבוהה מלאה (עד 4096px ב-ARGB_8888 + כיבוד סיבוב EXIF)!
     */
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
        FrameLayout.LayoutParams vpLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        stageRoot.addView(imageViewport, vpLp);

        final ZoomableImageView zoomView = new ZoomableImageView(this);
        activeZoomImageView = zoomView;
        zoomView.setOnSingleTapListener(this::toggleMediaControlsVisibility);
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

        // חצי מעבר צפים בצדדים למעבר מהיר בין תמונות התיקייה (מוסתרים אוטומטית בהקשה בודדת!)
        ImageButton btnFloatPrev = createResponsiveRoundIconButton(R.drawable.ic_skip_prev, dp(40), 0, false, accent, theme);
        FrameLayout.LayoutParams prevFlp = new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.LEFT | Gravity.CENTER_VERTICAL);
        prevFlp.leftMargin = dp(10);
        stageRoot.addView(btnFloatPrev, prevFlp);
        activeImagePrevFloat = btnFloatPrev;

        ImageButton btnFloatNext = createResponsiveRoundIconButton(R.drawable.ic_skip_next, dp(40), 0, false, accent, theme);
        FrameLayout.LayoutParams nextFlp = new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        nextFlp.rightMargin = dp(10);
        stageRoot.addView(btnFloatNext, nextFlp);
        activeImageNextFloat = btnFloatNext;

        // פאנל תחתון של תמונות (מידע + העברה לתמונה קודמת/הבאה + עורך תמונות מקצועי)
        LinearLayout bottomCard = createSurfaceCard(cardColor);
        bottomCard.setPadding(dp(12), dp(8), dp(12), dp(10));
        activeMediaBottomPanel = bottomCard;

        final TextView tvMeta = new TextView(this);
        tvMeta.setTextColor(textSecondary);
        tvMeta.setTextSize(11.5f);
        tvMeta.setGravity(Gravity.CENTER);
        tvMeta.setPadding(0, 0, 0, dp(6));
        bottomCard.addView(tvMeta);

        LinearLayout actionsRow = new LinearLayout(this);
        actionsRow.setOrientation(LinearLayout.HORIZONTAL);
        actionsRow.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        actionsRow.setGravity(Gravity.CENTER_VERTICAL);

        Button btnPrevImg = ThemedDialogHelper.createThemedButton(this, "◀ הקודמת", theme, false, false);
        btnPrevImg.setPadding(dp(10), dp(8), dp(10), dp(8));
        btnPrevImg.setTextSize(12f);

        MaterialButton btnProImageEditor = createPencilEditorButton("עורך תמונות מקצועי", accent, theme, v -> {
            Intent i = new Intent(this, ProImageEditorActivity.class);
            i.putExtra(ProImageEditorActivity.EXTRA_IMAGE_PATH, targetFile.getAbsolutePath());
            startActivity(i);
        });
        btnProImageEditor.setPadding(dp(14), dp(8), dp(14), dp(8));
        LinearLayout.LayoutParams editLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        editLp.leftMargin = dp(6);
        editLp.rightMargin = dp(6);
        btnProImageEditor.setLayoutParams(editLp);

        Button btnNextImg = ThemedDialogHelper.createThemedButton(this, "הבאה ▶", theme, false, false);
        btnNextImg.setPadding(dp(10), dp(8), dp(10), dp(8));
        btnNextImg.setTextSize(12f);

        if (imagePlaylist.size() > 1) {
            actionsRow.addView(btnPrevImg);
            actionsRow.addView(btnProImageEditor);
            actionsRow.addView(btnNextImg);
        } else {
            btnProImageEditor.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            actionsRow.addView(btnProImageEditor);
        }

        bottomCard.addView(actionsRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams bottomLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        bottomLp.setMargins(dp(8), 0, dp(8), dp(8));
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
            boolean isSvg = nameLow.endsWith(".svg") || nameLow.endsWith(".svgz");
            if (isSvg) {
                zoomView.setVisibility(View.GONE);
                fallbackSvgWebView.setVisibility(View.VISIBLE);
                btnProImageEditor.setVisibility(View.GONE);
                tvMeta.setText(StorageUtils.formatSize(imgFile.length()));
                try {
                    byte[] rawBytes = readFileBytesForViewer(imgFile, nameLow.endsWith(".svgz"));
                    String b64 = Base64.encodeToString(rawBytes, Base64.NO_WRAP);
                    String html = "<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=20'/>"
                            + "<style>html,body{margin:0;padding:0;width:100%;height:100%;display:flex;align-items:center;justify-content:center;background:transparent;}"
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

            executor.execute(() -> {
                DecodedPhoto photo = decodeUltraClearPhoto(imgFile);
                uiHandler.post(() -> {
                    if (gen != imageLoadGeneration || isFinishing()) return;
                    if (photo.bitmap != null) {
                        zoomView.setImageBitmap(photo.bitmap);
                        String dim = (photo.origWidth > 0 && photo.origHeight > 0)
                                ? (photo.origWidth + "x" + photo.origHeight + "  •  ")
                                : "";
                        tvMeta.setText("הקש לתצוגה נקייה  •  " + dim + StorageUtils.formatSize(imgFile.length()));
                    } else {
                        // גיבוי WebView לתמונות מיוחדות
                        zoomView.setVisibility(View.GONE);
                        fallbackSvgWebView.setVisibility(View.VISIBLE);
                        try {
                            byte[] rawBytes = readFileBytesForViewer(imgFile, false);
                            String b64 = Base64.encodeToString(rawBytes, Base64.NO_WRAP);
                            String html = "<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=20'/>"
                                    + "<style>html,body{margin:0;padding:0;width:100%;height:100%;display:flex;align-items:center;justify-content:center;background:transparent;}"
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

        btnPrevImg.setOnClickListener(v -> goPrev.run());
        btnFloatPrev.setOnClickListener(v -> goPrev.run());
        btnNextImg.setOnClickListener(v -> goNext.run());
        btnFloatNext.setOnClickListener(v -> goNext.run());

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

    private static class DecodedPhoto {
        Bitmap bitmap;
        int origWidth;
        int origHeight;
    }

    // סעיף ה: פענוח תמונות באיכות חדה וצלולה במיוחד (עד 4096px ב-ARGB_8888 מלא + סיבוב EXIF אוטומטי!)
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
                            decoder.setTargetSize(
                                    Math.max(1, Math.round(w * ratio)),
                                    Math.max(1, Math.round(h * ratio))
                            );
                        }
                    });
                    if (out.bitmap != null) {
                        return out;
                    }
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
        int stageBg = theme == PrefsManager.THEME_LIGHT
                ? Color.parseColor("#EFF6FF")
                : (theme == PrefsManager.THEME_NEON ? Color.parseColor("#061024") : Color.parseColor("#0B1325"));
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
        transportRow.setPadding(0, dp(isLandscape ? 6 : 12), 0, dp(isLandscape ? 6 : 10));

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
        bottomCenterBar.setPadding(dp(2), dp(2), dp(2), dp(2));

        final ImageButton btnRepeatMode = createResponsiveRoundIconButton(R.drawable.ic_repeat_off, repeatBtnPx, 0, false, accent, theme);
        FrameLayout.LayoutParams repLp = new FrameLayout.LayoutParams(repeatBtnPx, repeatBtnPx, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        bottomCenterBar.addView(btnRepeatMode, repLp);

        final MaterialButton btnSpeedCenter = createSpeedPillButton(mgr.getPlaybackSpeed(), accent, theme);
        FrameLayout.LayoutParams spdLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        bottomCenterBar.addView(btnSpeedCenter, spdLp);

        controlsColumn.addView(bottomCenterBar, new LinearLayout.LayoutParams(
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
                uiHandler.postDelayed(this, 250);
            }
        });
    }

    /**
     * סעיפים א, ב, ה, ו:
     * - א: הקשה בודדת על הוידאו מציגה את הוידאו בלבד ללא שאר האופציות, ובהקשה שנייה האופציות מוצגות!
     * - ב: בסיבוב מסך הוידאו מוצג בתצוגה מלאה בכל המסך!
     * - ה + ו: הוידאו צלול וברור מאוד ואינו מעוך כלל (חישוב Matrix מדויק של יחס גובה-רוחב אמיתי של הסרטון)!
     */
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

        setupVideoPinchZoomSingleAndDoubleTap(videoViewport, tvVideoDoubleTapBadge);

        stageRoot.addView(videoViewport, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // פאנל בקרי הוידאו התחתון (מוסתר בהקשה בודדת ובמסך מלא אופקי, ומוצג בהקשה חוזרת)
        LinearLayout bottomCard = createSurfaceCard(cardColor);
        bottomCard.setOrientation(LinearLayout.VERTICAL);
        bottomCard.setPadding(dp(12), dp(8), dp(12), dp(10));
        activeMediaBottomPanel = bottomCard;

        LinearLayout timeBar = new LinearLayout(this);
        timeBar.setOrientation(LinearLayout.HORIZONTAL);
        timeBar.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        timeBar.setGravity(Gravity.CENTER_VERTICAL);
        timeBar.setPadding(dp(2), dp(2), dp(2), dp(2));

        final TextView tvVidPos = new TextView(this);
        tvVidPos.setText("00:00");
        tvVidPos.setTextColor(accent);
        tvVidPos.setTextSize(12f);
        tvVidPos.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        timeBar.addView(tvVidPos);

        final SeekBar videoSeek = new SeekBar(this);
        videoSeek.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        timeBar.addView(videoSeek, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final TextView tvVidDur = new TextView(this);
        tvVidDur.setText("00:00");
        tvVidDur.setTextColor(textSecondary);
        tvVidDur.setTextSize(12f);
        tvVidDur.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        timeBar.addView(tvVidDur);

        bottomCard.addView(timeBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout controlsRow = new LinearLayout(this);
        controlsRow.setOrientation(LinearLayout.HORIZONTAL);
        controlsRow.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        controlsRow.setGravity(Gravity.CENTER);
        controlsRow.setPadding(0, dp(4), 0, dp(4));

        DisplayMetrics dm = getResources().getDisplayMetrics();
        float smDp = Math.min(dm.widthPixels, dm.heightPixels) / dm.density;
        float btnScale = Math.max(0.84f, Math.min(1.04f, smDp / 375f));

        int vSmall = dp(Math.round(35 * btnScale));
        int vMed = dp(Math.round(37 * btnScale));
        int vBig = dp(Math.round(45 * btnScale));
        int vGap = dp(Math.round(3.5f * btnScale));

        final ImageButton btnVidRepeat = createResponsiveRoundIconButton(R.drawable.ic_repeat_off, vSmall, vGap, false, accent, theme);
        ImageButton btnVidPrev = createResponsiveRoundIconButton(R.drawable.ic_skip_prev, vMed, vGap, false, accent, theme);
        ImageButton btnVidBack10 = createResponsiveRoundIconButton(R.drawable.ic_replay_10, vMed, vGap, false, accent, theme);
        final ImageButton btnVidPlayPause = createResponsiveRoundIconButton(R.drawable.ic_pause, vBig, vGap, true, accent, theme);
        ImageButton btnVidFwd10 = createResponsiveRoundIconButton(R.drawable.ic_forward_10, vMed, vGap, false, accent, theme);
        ImageButton btnVidNext = createResponsiveRoundIconButton(R.drawable.ic_skip_next, vMed, vGap, false, accent, theme);
        ImageButton btnVidRotate = createResponsiveRoundIconButton(R.drawable.ic_screen_rotate, vSmall, vGap, false, accent, theme);

        controlsRow.addView(btnVidRepeat);
        controlsRow.addView(btnVidPrev);
        controlsRow.addView(btnVidBack10);
        controlsRow.addView(btnVidPlayPause);
        controlsRow.addView(btnVidFwd10);
        controlsRow.addView(btnVidNext);
        controlsRow.addView(btnVidRotate);
        bottomCard.addView(controlsRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout bottomRow = new LinearLayout(this);
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        bottomRow.setGravity(Gravity.CENTER_VERTICAL);
        bottomRow.setPadding(0, dp(2), 0, 0);

        MaterialButton btnProVideoEditor = createPencilEditorButton("עורך וידאו מקצועי", accent, theme, v -> {
            pauseVideoSafely();
            Intent i = new Intent(this, ProVideoEditorActivity.class);
            i.putExtra(ProVideoEditorActivity.EXTRA_VIDEO_PATH, targetFile.getAbsolutePath());
            startActivity(i);
        });
        btnProVideoEditor.setPadding(dp(14), dp(8), dp(14), dp(8));
        LinearLayout.LayoutParams edLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        edLp.setMarginEnd(dp(8));
        btnProVideoEditor.setLayoutParams(edLp);
        bottomRow.addView(btnProVideoEditor);

        final MaterialButton btnVidSpeed = createSpeedPillButton(videoSpeed, accent, theme);
        bottomRow.addView(btnVidSpeed);

        bottomCard.addView(bottomRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams bottomLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        bottomLp.setMargins(dp(8), 0, dp(8), dp(8));
        stageRoot.addView(bottomCard, bottomLp);

        final Runnable syncVidButtons = () -> {
            boolean playing = isVideoPlayingSafely();
            btnVidPlayPause.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
            btnVidRepeat.setImageResource(resolveRepeatIcon(videoRepeatMode));
            btnVidSpeed.setText(formatSpeedLabel(videoSpeed));
            if (tvFolderTrackCounter != null && !videoPlaylist.isEmpty()) {
                tvFolderTrackCounter.setVisibility(View.VISIBLE);
                tvFolderTrackCounter.setText((currentVideoIdx + 1) + "/" + videoPlaylist.size());
            }
        };

        btnVidPlayPause.setOnClickListener(v -> {
            if (!videoPrepared || videoPlayer == null) return;
            if (isVideoPlayingSafely()) {
                videoPlayer.pause();
            } else {
                applyVideoSpeedInternal(true);
            }
            syncVidButtons.run();
        });

        btnVidBack10.setOnClickListener(v -> seekVideoBy(-10_000));
        btnVidFwd10.setOnClickListener(v -> seekVideoBy(10_000));

        btnVidPrev.setOnClickListener(v -> {
            if (videoPlaylist.isEmpty()) return;
            currentVideoIdx = (currentVideoIdx - 1 + videoPlaylist.size()) % videoPlaylist.size();
            loadVideoTrackAtIndex(currentVideoIdx, syncVidButtons);
        });

        btnVidNext.setOnClickListener(v -> {
            if (videoPlaylist.isEmpty()) return;
            currentVideoIdx = (currentVideoIdx + 1) % videoPlaylist.size();
            loadVideoTrackAtIndex(currentVideoIdx, syncVidButtons);
        });

        btnVidRepeat.setOnClickListener(v -> showRepeatPickerDialog(
                "מצב חזרה בוידאו",
                videoRepeatMode,
                false,
                chosen -> {
                    videoRepeatMode = chosen;
                    syncVidButtons.run();
                }
        ));

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

    // סעיף ו + ה: חישוב פרופורציות מדויק (Fit-Center) כך שהוידאו יוצג בצורה יפה, צלולה ולעולם לא מעוך!
    private void updateVideoAspectRatioMatrix() {
        if (videoTextureView == null) return;
        int viewW = videoTextureView.getWidth();
        int viewH = videoTextureView.getHeight();
        if (viewW <= 0 || viewH <= 0 || currentVideoWidth <= 0 || currentVideoHeight <= 0) {
            return;
        }
        float viewRatio = (float) viewW / (float) viewH;
        float videoRatio = (float) currentVideoWidth / (float) currentVideoHeight;

        float scaleX;
        float scaleY;
        if (videoRatio > viewRatio) {
            scaleX = 1.0f;
            scaleY = viewRatio / videoRatio;
        } else {
            scaleX = videoRatio / viewRatio;
            scaleY = 1.0f;
        }

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

    private void setupVideoPinchZoomSingleAndDoubleTap(FrameLayout viewport, TextView badge) {
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
                        boolean rightSide = e.getX() >= (viewport.getWidth() / 2f);
                        if (rightSide) {
                            seekVideoBy(10_000);
                            flashSeekBadge(badge, true);
                        } else {
                            seekVideoBy(-10_000);
                            flashSeekBadge(badge, false);
                        }
                        return true;
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
            }
            return true;
        });
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
        String repeatLabel = videoRepeatMode == AudioPlayerManager.REPEAT_ONE
                ? "חזרה על הסרטון עצמו"
                : (videoRepeatMode == AudioPlayerManager.REPEAT_FOLDER ? "חזרה על כל התיקייה" : "ללא חזרה");

        List<String> items = Arrays.asList(
                "מהירות הסרטון (כעת: " + formatSpeedLabel(videoSpeed) + ")",
                "מצב חזרה (כעת: " + repeatLabel + ")",
                "שמע הסרטון: " + muteStatus,
                "מעבר אוטומטי לסרטון הבא בתיקייה: " + autoNextStatus,
                "סיבוב מסך מלא (אופקי / אנכי)",
                "איפוס הגדלת תצוגה ידנית (100%)",
                "פתיחה בעורך וידאו מקצועי"
        );

        ThemedDialogHelper.showThemedMenu(
                this,
                "הגדרות הוידאו",
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
                            toggleScreenOrientation();
                            break;
                        case 5:
                            resetVideoZoom();
                            Toast.makeText(this, "הזום אופס ל-100%", Toast.LENGTH_SHORT).show();
                            break;
                        case 6:
                            pauseVideoSafely();
                            Intent ed = new Intent(this, ProVideoEditorActivity.class);
                            ed.putExtra(ProVideoEditorActivity.EXTRA_VIDEO_PATH, targetFile.getAbsolutePath());
                            startActivity(ed);
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
            int fill = theme == PrefsManager.THEME_NEON ? Color.parseColor("#00B4D8") : accent;
            circle.setColor(fill);
            btn.setColorFilter(theme == PrefsManager.THEME_NEON ? Color.parseColor("#030712") : Color.WHITE);
        } else {
            circle.setColor(Color.argb(theme == PrefsManager.THEME_LIGHT ? 26 : 44,
                    Color.red(accent), Color.green(accent), Color.blue(accent)));
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
        btn.setTextSize(12.5f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setTextColor(accent);
        btn.setStrokeColor(ColorStateList.valueOf(accent));
        btn.setIconResource(R.drawable.ic_speed);
        btn.setIconTint(ColorStateList.valueOf(accent));
        btn.setIconSize(dp(18));
        btn.setIconPadding(dp(5));
        btn.setCornerRadius(999);
        btn.setMinHeight(0);
        btn.setMinimumHeight(0);
        btn.setPadding(dp(12), dp(7), dp(12), dp(7));
        return btn;
    }

    private MaterialButton createPencilEditorButton(String label, int accent, int theme, View.OnClickListener onClick) {
        MaterialButton btn = new MaterialButton(this);
        btn.setText(label);
        btn.setAllCaps(false);
        btn.setTextSize(13f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        int bgFill = theme == PrefsManager.THEME_NEON ? Color.parseColor("#00B4D8") : accent;
        int fgColor = theme == PrefsManager.THEME_NEON ? Color.parseColor("#030712") : Color.WHITE;
        btn.setBackgroundTintList(ColorStateList.valueOf(bgFill));
        btn.setTextColor(fgColor);
        btn.setIconResource(R.drawable.ic_edit_pencil);
        btn.setIconTint(ColorStateList.valueOf(fgColor));
        btn.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        btn.setIconPadding(dp(6));
        btn.setCornerRadius(dp(12));
        btn.setMinHeight(0);
        btn.setMinimumHeight(0);
        btn.setPadding(dp(12), dp(9), dp(12), dp(9));
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
        if (videoPlayer != null) {
            try { videoPlayer.stop(); } catch (Exception ignored) {}
            try { videoPlayer.release(); } catch (Exception ignored) {}
            videoPlayer = null;
        }
    }

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

    private void renderArchiveExtractor(LinearLayout host, int cardColor, int textPrimary, int textSecondary, int theme) {
        LinearLayout card = createSurfaceCard(cardColor);

        TextView tvHeader = new TextView(this);
        tvHeader.setText("תוכן ארכיון וחילוץ מובנה");
        tvHeader.setTextColor(textPrimary);
        tvHeader.setTextSize(16f);
        tvHeader.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(tvHeader);

        TextView tvSub = new TextView(this);
        tvSub.setText("סורק רשימת קבצים בתוך הארכיון...");
        tvSub.setTextColor(textSecondary);
        tvSub.setTextSize(12.5f);
        tvSub.setPadding(0, dp(4), 0, dp(10));
        card.addView(tvSub);

        ScrollView scroll = new ScrollView(this);
        TextView tvEntries = new TextView(this);
        tvEntries.setTextColor(textPrimary);
        tvEntries.setTextSize(13f);
        tvEntries.setLineSpacing(5f, 1.15f);
        scroll.addView(tvEntries);
        card.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button btnExtract = ThemedDialogHelper.createThemedButton(this, "חלץ את כל הקבצים לתיקייה כאן", theme, true, false);
        LinearLayout.LayoutParams exLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        exLp.topMargin = dp(12);
        btnExtract.setLayoutParams(exLp);
        btnExtract.setOnClickListener(v -> {
            btnExtract.setEnabled(false);
            executor.execute(() -> {
                try {
                    String baseName = targetFile.getName().replaceAll("(?i)\\.(zip|jar|cbz|apk|apkx|apk\\+|apks|apkm|xapk|apkp)$", "");
                    File outDir = StorageUtils.uniqueDestinationFile(
                            targetFile.getParentFile() != null ? targetFile.getParentFile() : getFilesDir(),
                            baseName + "_מחולץ"
                    );
                    int count = ArchiveUtils.extractZip(targetFile, outDir);
                    uiHandler.post(() -> {
                        btnExtract.setEnabled(true);
                        ThemedDialogHelper.showCustomDialog(
                                this,
                                "החילוץ הושלם בהצלחה",
                                "חולצו " + count + " קבצים אל התיקייה:\n" + outDir.getAbsolutePath(),
                                null,
                                "הבנתי",
                                this::finish,
                                null,
                                null,
                                false
                        );
                    });
                } catch (Exception e) {
                    uiHandler.post(() -> {
                        btnExtract.setEnabled(true);
                        Toast.makeText(this, "שגיאה בחילוץ: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            });
        });
        card.addView(btnExtract);

        host.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        executor.execute(() -> {
            try {
                List<String> list = ArchiveUtils.listZipEntries(targetFile);
                StringBuilder sb = new StringBuilder();
                for (String item : list) sb.append("• ").append(item).append("\n");
                uiHandler.post(() -> {
                    tvSub.setText("נמצאו " + list.size() + " פריטים בארכיון (" + StorageUtils.formatSize(targetFile.length()) + ")");
                    tvEntries.setText(sb.length() > 0 ? sb.toString() : "הארכיון ריק");
                });
            } catch (Exception e) {
                uiHandler.post(() -> tvSub.setText("שגיאה בקריאת הארכיון: " + e.getMessage()));
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
        tvDoc.setTextSize(14f);
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

    private void renderTextDocumentViewer(LinearLayout host, int cardColor, int textPrimary, int textSecondary, int theme) {
        LinearLayout card = createSurfaceCard(cardColor);

        final TextView tvSub = new TextView(this);
        tvSub.setText("עורך טקסט מובנה • גע באותיות לעריכה • " + StorageUtils.formatSize(targetFile.length()));
        tvSub.setTextColor(textSecondary);
        tvSub.setTextSize(12f);
        tvSub.setPadding(0, 0, 0, dp(10));
        card.addView(tvSub);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        final EditText etDoc = new EditText(this);
        activeTextEditor = etDoc;
        etDoc.setBackground(null);
        etDoc.setTextColor(textPrimary);
        etDoc.setHintTextColor(textSecondary);
        etDoc.setHint("הקובץ ריק — גע כאן כדי להוסיף מילים...");
        etDoc.setTextSize(14f);
        etDoc.setLineSpacing(5f, 1.2f);
        etDoc.setGravity(Gravity.TOP | Gravity.START);
        etDoc.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);
        etDoc.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        etDoc.setFocusable(true);
        etDoc.setFocusableInTouchMode(true);
        etDoc.setClickable(true);
        etDoc.setLongClickable(true);
        etDoc.setPadding(dp(4), dp(4), dp(4), dp(18));

        etDoc.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                etDoc.post(() -> {
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.showSoftInput(etDoc, InputMethodManager.SHOW_IMPLICIT);
                    }
                });
            }
        });

        etDoc.setOnClickListener(v -> {
            etDoc.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(etDoc, InputMethodManager.SHOW_IMPLICIT);
            }
        });

        scroll.addView(etDoc, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button btnSaveText = ThemedDialogHelper.createThemedButton(this, "שמור שינויים בקובץ", theme, true, false);
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        saveLp.topMargin = dp(10);
        btnSaveText.setLayoutParams(saveLp);
        btnSaveText.setOnClickListener(v -> {
            if (saveEditedTextFileNow(true)) {
                tvSub.setText("עורך טקסט מובנה • נשמר בהצלחה • " + StorageUtils.formatSize(targetFile.length()));
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(etDoc.getWindowToken(), 0);
                }
            }
        });
        card.addView(btnSaveText);

        host.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        executor.execute(() -> {
            StringBuilder sb = new StringBuilder();
            boolean readFailed = false;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(targetFile), StandardCharsets.UTF_8))) {
                char[] buf = new char[4096];
                int read;
                int totalChars = 0;
                while ((read = reader.read(buf)) != -1 && totalChars < 200_000) {
                    sb.append(buf, 0, read);
                    totalChars += read;
                }
            } catch (Exception e) {
                readFailed = true;
                sb.append("לא ניתן להציג קובץ זה כטקסט: ").append(e.getMessage());
            }
            final String text = sb.toString();
            final boolean isError = readFailed;
            uiHandler.post(() -> {
                loadedOriginalText = text;
                textEditorDirty = false;
                etDoc.setText(text);
                if (!isError) {
                    etDoc.addTextChangedListener(new TextWatcher() {
                        @Override
                        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                        }

                        @Override
                        public void onTextChanged(CharSequence s, int start, int before, int count) {
                        }

                        @Override
                        public void afterTextChanged(Editable s) {
                            String current = s != null ? s.toString() : "";
                            textEditorDirty = !current.equals(loadedOriginalText);
                        }
                    });
                }
            });
        });
    }

    private boolean saveEditedTextFileNow(boolean showSuccessToast) {
        if (activeTextEditor == null || targetFile == null) return false;
        String updatedContent = activeTextEditor.getText() != null ? activeTextEditor.getText().toString() : "";
        try (FileOutputStream fos = new FileOutputStream(targetFile, false)) {
            fos.write(updatedContent.getBytes(StandardCharsets.UTF_8));
            fos.flush();
            loadedOriginalText = updatedContent;
            textEditorDirty = false;
            if (showSuccessToast) {
                Toast.makeText(this, "השינויים בקובץ הטקסט נשמרו בהצלחה", Toast.LENGTH_SHORT).show();
            }
            return true;
        } catch (Exception e) {
            Toast.makeText(this, "שגיאה בשמירת הקובץ: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return false;
        }
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
