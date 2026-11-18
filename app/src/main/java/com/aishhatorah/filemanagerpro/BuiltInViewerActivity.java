package com.aishhatorah.filemanagerpro;

import android.app.AlertDialog;
import android.app.PictureInPictureParams;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.graphics.PointF;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfRenderer;
import android.media.AudioAttributes;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Rational;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.webkit.MimeTypeMap;
import android.view.ScaleGestureDetector;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.cardview.widget.CardView;
import androidx.core.content.FileProvider;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.SeekParameters;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.extractor.ExtractorsFactory;

import com.google.android.material.button.MaterialButton;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
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
import java.util.concurrent.ThreadLocalRandom;
import java.util.zip.GZIPInputStream;

import com.aishhatorah.filemanagerpro.AudioPaletteHelper.AudioAdaptivePalette;

/**
 * מציג ונגן המדיה המובנה של File Manager Pro 0.3.6:
 * - תצוגת Edge-to-Edge מלאה לתמונות ווידאו מאחורי סרגלים שקופים (סעיף 25)
 * - מצב מחוות אוטומטי מול מצב כפתורים קלאסי בווידאו ותמונות (סעיפים 29, 30, 44)
 * - לחיצה ארוכה בצד ימין 2x קדימה ובצד שמאל 2x אחורה בווידאו (סעיף 44)
 * - תמונה בתוך תמונה (PiP) ופתיחה אוניברסלית דרך "פתח באמצעות" מכל אפליקציה
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
    private File externalCopiedCacheFile = null;
    private boolean openedFromNotification = false;
    private String forceType = null;

    private LinearLayout topBar;
    private TextView tvMarqueeTitle;
    private TextView tvFolderTrackCounter;
    private MaterialButton btnTopRightVideoSettings;
    private ImageButton btnTopRightVideoPlus;
    private ImageButton btnTopRightVideoMenu;

    private boolean isImageMode = false;
    private boolean mediaControlsHidden = false;
    private View activeImageTopOverlay = null;
    private View activeMediaBottomPanel = null;
    private View activeImagePrevFloat = null;
    private View activeImageNextFloat = null;
    private View activeVideoCenterOverlay = null;
    private View activeVideoLockFloatingBtn = null;
    private FrameLayout activeMediaStageRoot = null;
    private FrameLayout activeMediaViewport = null;

    private final List<File> imagePlaylist = new ArrayList<>();
    private int currentImageIdx = 0;
    private ZoomableImageView activeZoomImageView = null;
    private int imageLoadGeneration = 0;
    private final android.util.LruCache<String, DecodedPhoto> photoCache = new android.util.LruCache<>(5);
    private Runnable archiveNavigateUpHandler = null;

    private boolean isAudioMode = false;
    private boolean audioTickerActive = false;
    private AudioPlayerManager.PlaybackListener audioListener;
    private LinearLayout audioRootLayout = null;
    private View audioStagePlayerView = null;
    private TextView tvAudioCenterScrubTime = null;
    private ImageButton audioTopBackBtn = null;
    private ImageButton audioTopShareBtn = null;
    private String lastHeaderTitleName = null;

    private boolean isVideoMode = false;
    private boolean awaitingInitialVideoPlay = false;
    private boolean suppressNextVideoAutoPlay = false;
    private TextView tvVideoCenterScrubTime = null;
    private ImageView ivVideoPreviewThumb = null;
    private boolean isInPipMode = false;
    private final Runnable mtsBufferingWatchdog = () -> {
        if (isFinishing() || isDestroyed() || !isVideoMode || exoPlayer == null) return;
        if (!exoRemuxFallbackAttempted && !isRemuxingMtsPlayback && !awaitingInitialVideoPlay
                && targetFile != null && MpegTsNormalizer.isLikelyMpegTsName(targetFile.getName())
                && exoPlayer.getPlaybackState() == Player.STATE_BUFFERING
                && exoPlayer.getPlayWhenReady()) {
            startRemuxedMtsPlayback(targetFile, activeVideoSyncRunnable);
        }
    };
    private final List<File> videoPlaylist = new ArrayList<>();
    private int currentVideoIdx = 0;
    private ExoPlayer exoPlayer;
    private MediaPlayer videoPlayer;
    private Closeable legacyMediaSourceCloser;
    private Surface activeVideoSurface;
    private TextureView videoTextureView;
    private boolean videoPrepared = false;
    private boolean videoTickerActive = false;
    private int currentVideoWidth = 0;
    private int currentVideoHeight = 0;
    private float currentVideoPixelRatio = 1.0f;
    private int knownVideoDurationMs = 0;
    private boolean isUserSeekingVideo = false;
    private long lastLiveSeekUiMs = 0L;
    private long holdSeekUiUntilMs = 0L;
    private int pendingSeekTargetMs = -1;
    private boolean exoAudioDisabledRetry = false;
    private boolean exoForcedTsRetry = false;
    private boolean exoRemuxFallbackAttempted = false;
    private boolean isRemuxingMtsPlayback = false;
    private int lastObservedVideoPosMs = 0;
    private int unexpectedPosResetCount = 0;
    private long firstPosResetObservedMs = 0L;
    private int droppedVideoFrameBurst = 0;
    private long firstDroppedFrameBurstMs = 0L;
    private File activeRemuxedCacheFile = null;
    private boolean videoUsedFdFallback = false;
    private boolean speedModifiedFromDefault = false;
    private LinearLayout videoFallbackBar = null;
    private float videoSpeed = 1.0f;
    private int videoRepeatMode = AudioPlayerManager.REPEAT_OFF;
    private boolean videoShuffleMode = false;
    private boolean videoMuted = false;
    private boolean videoAutoNext = true;
    private boolean isVideoLocked = false;
    private int lastVideoTopInsetPx = 0;
    private int videoScaleMode = 0; // 0=FIT, 1=FILL_CROP, 2=STRETCH
    private float videoScale = 1.0f;
    private float videoTransX = 0f;
    private float videoTransY = 0f;
    private final float[] lastAppliedMatrixValues = new float[9];
    private boolean hasAppliedVideoMatrix = false;
    private boolean isLandscapeLocked = false;
    private Runnable activeVideoSyncRunnable = null;

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

    private void handleIncomingIntent(Intent intent, boolean isInitialCreate) {
        if (intent == null) {
            if (isInitialCreate) finish();
            return;
        }
        if (intent.getBooleanExtra(EXTRA_FROM_NOTIFICATION, false)) {
            openedFromNotification = true;
        }
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
                    Toast.makeText(this, "לא ניתן לפתוח קובץ זה", Toast.LENGTH_SHORT).show();
                    if (isInitialCreate) finish();
                }
            });
        });
    }

    private void openResolvedTargetFile(File file) {
        String lower = file.getName().toLowerCase(Locale.ROOT);
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
            codeIntent.putExtra(CodeEditorActivity.EXTRA_FILE_PATH, file.getAbsolutePath());
            startActivity(codeIntent);
            finish();
            return;
        }
        targetFile = file;
        detachAudioCallbacksOnly();
        releaseVideoPlayer();
        buildViewerUiForCurrentFile();
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
        if (isVideoMode) {
            if (landscapeNow || isVideoLocked) {
                mediaControlsHidden = true;
            }
            applyMediaImmersiveAndLayoutState();
            hasAppliedVideoMatrix = false;
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
        if (isVideoMode && isVideoLocked) {
            return;
        }
        mediaControlsHidden = !mediaControlsHidden;
        applyMediaImmersiveAndLayoutState();
    }

    // סעיף 25: במה פרוסה Edge-to-Edge מאחורי סרגלים צפים ושקופים ללא מרווחים חיצוניים
    private void applyMediaImmersiveAndLayoutState() {
        if (!isImageMode && !isVideoMode) {
            setSystemBarsFullscreen(false);
            return;
        }
    private void applyMediaImmersiveAndLayoutState() {
        if (!isImageMode && !isVideoMode) return;
        boolean isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        if (isVideoMode && (isVideoLocked || isInPipMode)) {
            mediaControlsHidden = true;
        }
        boolean wantFullscreenSystemBars = mediaControlsHidden || (isVideoMode && isLandscape);
        setSystemBarsFullscreen(wantFullscreenSystemBars);

        if (topBar != null) {
            topBar.setVisibility((mediaControlsHidden || isInPipMode) ? View.GONE : View.VISIBLE);
            topBar.setBackgroundColor(Color.argb(175, 10, 15, 28));
        }

        if (activeImageTopOverlay != null) {
            activeImageTopOverlay.setVisibility(mediaControlsHidden ? View.GONE : View.VISIBLE);
        }

        if (activeMediaBottomPanel != null) {
            activeMediaBottomPanel.setVisibility((mediaControlsHidden || isInPipMode) ? View.GONE : View.VISIBLE);
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
            boolean showCenter = !isInPipMode && !isVideoLocked && (!mediaControlsHidden || awaitingInitialVideoPlay);
            activeVideoCenterOverlay.setVisibility(showCenter ? View.VISIBLE : View.GONE);
        }
        if (activeVideoLockFloatingBtn != null) {
            activeVideoLockFloatingBtn.setVisibility((isVideoMode && isVideoLocked && !isInPipMode) ? View.VISIBLE : View.GONE);
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
        if (archiveNavigateUpHandler != null) {
            archiveNavigateUpHandler.run();
            return;
        }
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
        activeImageTopOverlay = null;
        activeMediaBottomPanel = null;
        activeImagePrevFloat = null;
        activeImageNextFloat = null;
        activeVideoCenterOverlay = null;
        activeVideoLockFloatingBtn = null;
        activeMediaStageRoot = null;
        activeMediaViewport = null;
        ivVideoPreviewThumb = null;
        btnTopRightVideoPlus = null;
        activeZoomImageView = null;
        archiveNavigateUpHandler = null;
        activeVideoSyncRunnable = null;
        audioRootLayout = null;
        audioStagePlayerView = null;
        tvAudioCenterScrubTime = null;
        tvVideoCenterScrubTime = null;
        audioTopBackBtn = null;
        audioTopShareBtn = null;
        lastHeaderTitleName = null;
        isVideoLocked = false;
        isInPipMode = false;
        lastVideoTopInsetPx = 0;
        awaitingInitialVideoPlay = false;
        suppressNextVideoAutoPlay = false;
        hasAppliedVideoMatrix = false;

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
        mediaControlsHidden = isVideoMode;
        awaitingInitialVideoPlay = isVideoMode;
        suppressNextVideoAutoPlay = isVideoMode;

        topBar = isImageMode ? null : buildTopBar(theme, textPrimary, textSecondary, accent, isLandscape);

        if (isImageMode || isVideoMode) {
            Window win = getWindow();
            if (win != null) {
                WindowCompat.setDecorFitsSystemWindows(win, false);
                win.setStatusBarColor(Color.TRANSPARENT);
                win.setNavigationBarColor(Color.TRANSPARENT);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    WindowManager.LayoutParams lp = win.getAttributes();
                    lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                    win.setAttributes(lp);
                }
                View decor = win.getDecorView();
                if (decor != null) {
                    int vis = decor.getSystemUiVisibility();
                    vis &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vis &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                    }
                    decor.setSystemUiVisibility(vis);
                }
            }

            FrameLayout stageRoot = new FrameLayout(this);
            stageRoot.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            stageRoot.setBackgroundColor(Color.BLACK);
            activeMediaStageRoot = stageRoot;

            if (isImageMode) {
                renderImageViewerOverlay(stageRoot, cardColor, textPrimary, textSecondary, accent, theme);
                ViewCompat.setOnApplyWindowInsetsListener(stageRoot, (v, insets) -> {
                    Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                    if (activeImageTopOverlay != null) {
                        activeImageTopOverlay.setPadding(dp(12) + sys.left, sys.top + dp(8), dp(12) + sys.right, dp(8));
                    }
                    if (activeMediaBottomPanel != null) {
                        activeMediaBottomPanel.setPadding(dp(12) + sys.left, dp(10), dp(12) + sys.right, sys.bottom + dp(10));
                    }
                    return insets;
                });
            } else {
                renderVideoPlayerOverlay(stageRoot, cardColor, textPrimary, textSecondary, accent, theme);
                if (topBar != null) {
                    FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
                    stageRoot.addView(topBar, topLp);
                }
                final int baseVPad = isLandscape ? dp(6) : dp(8);
                ViewCompat.setOnApplyWindowInsetsListener(stageRoot, (v, insets) -> {
                    Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                    lastVideoTopInsetPx = sys.top;
                    if (topBar != null) {
                        topBar.setPadding(dp(10) + sys.left, sys.top + baseVPad, dp(10) + sys.right, baseVPad);
                    }
                    if (activeMediaBottomPanel != null) {
                        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) activeMediaBottomPanel.getLayoutParams();
                        if (lp != null) {
                            lp.leftMargin = sys.left + dp(10);
                            lp.rightMargin = sys.right + dp(10);
                            lp.bottomMargin = sys.bottom + dp(10);
                            activeMediaBottomPanel.setLayoutParams(lp);
                        }
                    }
                    if (activeVideoLockFloatingBtn != null) {
                        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) activeVideoLockFloatingBtn.getLayoutParams();
                        if (lp != null) {
                            lp.bottomMargin = sys.bottom + dp(24);
                            activeVideoLockFloatingBtn.setLayoutParams(lp);
                        }
                    }
                    return insets;
                });
            }

            setContentView(stageRoot);
            applyMediaImmersiveAndLayoutState();
            return;
        }

        if (getWindow() != null) {
            WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        }
        setSystemBarsFullscreen(false);
        final AudioPlayerManager audioMgr = AudioPlayerManager.getInstance();
        final int touchSlopPx = ViewConfiguration.get(this).getScaledTouchSlop();

        LinearLayout root;
        if (isAudioMode) {
            final int[] audioSwipeMode = new int[]{0}; // 0=none, 1=horizontal seek, 2=vertical track
            final float[] swipeDownX = new float[]{0f};
            final float[] swipeDownY = new float[]{0f};
            final int[] swipeStartPosMs = new int[]{0};
            final int[] pendingSwipeTargetMs = new int[]{0};
            final long[] lastLiveSeekMs = new long[]{0L};

            root = new LinearLayout(this) {
                @Override
                public boolean onInterceptTouchEvent(MotionEvent ev) {
                    if (!isAudioMode) return super.onInterceptTouchEvent(ev);
                    switch (ev.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            audioSwipeMode[0] = 0;
                            swipeDownX[0] = ev.getRawX();
                            swipeDownY[0] = ev.getRawY();
                            swipeStartPosMs[0] = audioMgr.getCurrentPositionMs();
                            pendingSwipeTargetMs[0] = swipeStartPosMs[0];
                            break;
                        case MotionEvent.ACTION_MOVE: {
                            float dx = ev.getRawX() - swipeDownX[0];
                            float dy = ev.getRawY() - swipeDownY[0];
                            if (Math.abs(dx) > touchSlopPx * 1.4f && Math.abs(dx) > Math.abs(dy) * 1.3f) {
                                audioSwipeMode[0] = 1;
                                return true;
                            }
                            if (Math.abs(dy) > touchSlopPx * 1.6f && Math.abs(dy) > Math.abs(dx) * 1.3f) {
                                audioSwipeMode[0] = 2;
                                return true;
                            }
                            break;
                        }
                    }
                    return super.onInterceptTouchEvent(ev);
                }

                @Override
                public boolean onTouchEvent(MotionEvent ev) {
                    if (!isAudioMode) return super.onTouchEvent(ev);
                    switch (ev.getActionMasked()) {
                        case MotionEvent.ACTION_MOVE: {
                            float dx = ev.getRawX() - swipeDownX[0];
                            float dy = ev.getRawY() - swipeDownY[0];
                            if (audioSwipeMode[0] == 1) {
                                int dur = audioMgr.getDurationMs();
                                float width = Math.max(1f, getWidth());
                                int deltaMs = Math.round((dx / width) * 90_000f);
                                int target = dur > 0
                                        ? Math.max(0, Math.min(dur, swipeStartPosMs[0] + deltaMs))
                                        : Math.max(0, swipeStartPosMs[0] + deltaMs);
                                pendingSwipeTargetMs[0] = target;
                                if (tvAudioCenterScrubTime != null) {
                                    tvAudioCenterScrubTime.setText(formatClock(target));
                                    tvAudioCenterScrubTime.setVisibility(View.VISIBLE);
                                }
                                long now = System.currentTimeMillis();
                                if (now - lastLiveSeekMs[0] >= 180L) {
                                    lastLiveSeekMs[0] = now;
                                    audioMgr.seekTo(target);
                                }
                                return true;
                            } else if (audioSwipeMode[0] == 2) {
                                if (audioStagePlayerView != null) {
                                    audioStagePlayerView.setTranslationY(dy * 0.45f);
                                    float alpha = Math.max(0.45f, 1f - Math.abs(dy) / Math.max(1f, getHeight()));
                                    audioStagePlayerView.setAlpha(alpha);
                                }
                                return true;
                            }
                            break;
                        }
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL: {
                            float dy = ev.getRawY() - swipeDownY[0];
                            if (audioSwipeMode[0] == 1) {
                                audioMgr.seekTo(pendingSwipeTargetMs[0]);
                                if (tvAudioCenterScrubTime != null) {
                                    tvAudioCenterScrubTime.setVisibility(View.GONE);
                                }
                                audioSwipeMode[0] = 0;
                                return true;
                            } else if (audioSwipeMode[0] == 2) {
                                final View stage = audioStagePlayerView;
                                int slideDist = Math.max(dp(180), getHeight() / 2);
                                int triggerThreshold = dp(64);
                                if (stage != null && Math.abs(dy) > triggerThreshold) {
                                    final boolean goingPreviousDown = dy > 0;
                                    float exitY = goingPreviousDown ? slideDist : -slideDist;
                                    float enterFromY = goingPreviousDown ? -slideDist : slideDist;
                                    stage.animate()
                                            .translationY(exitY)
                                            .alpha(0f)
                                            .setDuration(150)
                                            .withEndAction(() -> {
                                                if (goingPreviousDown) {
                                                    audioMgr.playPreviousTrackImmediate(BuiltInViewerActivity.this);
                                                } else {
                                                    audioMgr.playNext(BuiltInViewerActivity.this);
                                                }
                                                stage.setTranslationY(enterFromY);
                                                stage.setAlpha(0f);
                                                stage.animate()
                                                        .translationY(0f)
                                                        .alpha(1f)
                                                        .setDuration(220)
                                                        .start();
                                            })
                                            .start();
                                } else if (stage != null) {
                                    stage.animate().translationY(0f).alpha(1f).setDuration(150).start();
                                }
                                audioSwipeMode[0] = 0;
                                return true;
                            }
                            break;
                        }
                    }
                    return super.onTouchEvent(ev);
                }
            };
        } else {
            root = new LinearLayout(this);
        }
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(bgColor);

        root.addView(topBar);

        LinearLayout contentArea = new LinearLayout(this);
        contentArea.setOrientation(LinearLayout.VERTICAL);
        contentArea.setGravity(Gravity.CENTER);
        boolean isPdfLayout = MediaFormatHelper.isPdfFile(lower);
        int areaPad = isPdfLayout ? 0 : (isAudioMode ? dp(isLandscape ? 8 : 14) : (isLandscape ? dp(8) : dp(12)));
        contentArea.setPadding(areaPad, areaPad, areaPad, areaPad);
        root.addView(contentArea, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (isAudioMode) {
            FrameLayout audioOverlayFrame = new FrameLayout(this);
            audioOverlayFrame.addView(root, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            tvAudioCenterScrubTime = createCleanCenterTimeOverlay();
            audioOverlayFrame.addView(tvAudioCenterScrubTime);
            setContentView(audioOverlayFrame);

            audioRootLayout = root;
            renderAudioPlayer(contentArea, bgColor, textPrimary, textSecondary, accent, theme, isLandscape);
        } else {
            setContentView(root);
            if (MediaFormatHelper.isPdfFile(lower)) {
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
    }

    private LinearLayout buildTopBar(int theme, int textPrimary, int textSecondary, int accent, boolean isLandscape) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(isAudioMode ? Color.TRANSPARENT : ThemeHelper.getTopBarColor(this, theme));
        int vPad = isLandscape ? dp(6) : dp(8);
        bar.setPadding(dp(10), vPad, dp(10), vPad);

        boolean translucentOverlay = isImageMode || isVideoMode;
        int effectiveText = translucentOverlay ? Color.WHITE : textPrimary;
        int effectiveSub = translucentOverlay ? Color.parseColor("#CBD5E1") : textSecondary;

        int backColor = translucentOverlay ? Color.WHITE : textPrimary;
        ImageButton btnBackIcon = createCleanBorderlessIconButton(R.drawable.ic_arrow_back, dp(42), backColor);
        btnBackIcon.setContentDescription("חזרה");
        btnBackIcon.setOnClickListener(v -> proceedCloseOrNavigateBack());
        bar.addView(btnBackIcon);
        if (isAudioMode) {
            audioTopBackBtn = btnBackIcon;
        }

        LinearLayout centerTitleCol = new LinearLayout(this);
        centerTitleCol.setOrientation(LinearLayout.VERTICAL);
        centerTitleCol.setGravity(Gravity.CENTER);

        if (isVideoMode || isAudioMode) {
            tvMarqueeTitle = new TextView(this);
            lastHeaderTitleName = targetFile.getName();
            tvMarqueeTitle.setText(lastHeaderTitleName);
            tvMarqueeTitle.setTextSize(ThemeHelper.spScaled(this, isLandscape ? 13.5f : 14.5f));
            tvMarqueeTitle.setTypeface(Typeface.DEFAULT_BOLD);
            tvMarqueeTitle.setTextColor(isVideoMode ? Color.WHITE : effectiveText);
            tvMarqueeTitle.setSingleLine(false);
            tvMarqueeTitle.setMaxLines(2);
            tvMarqueeTitle.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            tvMarqueeTitle.setTextDirection(View.TEXT_DIRECTION_LOCALE);
            tvMarqueeTitle.setGravity(Gravity.CENTER);
            if (isVideoMode) {
                tvMarqueeTitle.setShadowLayer(4f, 0f, 1.5f, Color.argb(200, 0, 0, 0));
            }
            centerTitleCol.addView(tvMarqueeTitle, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            tvMarqueeTitle = new AppCompatTextView(this) {
                @Override
                public boolean isFocused() {
                    return true;
                }
            };
            lastHeaderTitleName = targetFile.getName();
            tvMarqueeTitle.setText("   " + lastHeaderTitleName + "   •   " + lastHeaderTitleName + "   ");
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
        }

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

        if (isVideoMode) {
            btnTopRightVideoPlus = createCleanBorderlessIconButton(R.drawable.ic_add, dp(42), Color.WHITE);
            btnTopRightVideoPlus.setContentDescription("כלים ותמונה בתוך תמונה");
            btnTopRightVideoPlus.setOnClickListener(v -> showVideoPlusPopup(v, theme));
            bar.addView(btnTopRightVideoPlus);

            btnTopRightVideoMenu = createCleanBorderlessIconButton(R.drawable.ic_menu_hamburger, dp(42), Color.WHITE);
            btnTopRightVideoMenu.setContentDescription("אפשרויות");
            btnTopRightVideoMenu.setOnClickListener(v -> showVideoSettingsDialog(theme));
            bar.addView(btnTopRightVideoMenu);
            btnTopRightVideoSettings = null;
        } else {
            btnTopRightVideoPlus = null;
            btnTopRightVideoMenu = null;
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
            btnTopRightVideoSettings.setVisibility(isImageMode ? View.VISIBLE : View.GONE);
            btnTopRightVideoSettings.setOnClickListener(v -> {
                if (isImageMode) showImageOptionsDialog();
            });
            bar.addView(btnTopRightVideoSettings);
        }

        if (!isVideoMode && !isImageMode) {
            ImageButton btnShareIcon = createCleanBorderlessIconButton(R.drawable.ic_share, dp(42), effectiveText);
            btnShareIcon.setContentDescription("שיתוף");
            btnShareIcon.setOnClickListener(v -> shareCurrentFile());
            bar.addView(btnShareIcon);
            if (isAudioMode) {
                audioTopShareBtn = btnShareIcon;
            }
        }

        return bar;
    }

    private void updateMarqueeHeaderName(String name) {
        if (tvMarqueeTitle != null && name != null) {
            if (name.equals(lastHeaderTitleName)) return;
            lastHeaderTitleName = name;
            if (isVideoMode || isAudioMode) {
                tvMarqueeTitle.setText(name);
            } else {
                tvMarqueeTitle.setText("   " + name + "   •   " + name + "   ");
                tvMarqueeTitle.setSelected(true);
            }
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

        ImageButton btnFloatPrev = createCleanBorderlessIconButton(R.drawable.ic_skip_prev, dp(42), Color.WHITE);
        GradientDrawable prevScrim = new GradientDrawable();
        prevScrim.setShape(GradientDrawable.OVAL);
        prevScrim.setColor(Color.argb(125, 8, 12, 22));
        prevScrim.setStroke(Math.max(1, dp(1)), Color.argb(75, 255, 255, 255));
        btnFloatPrev.setBackground(prevScrim);
        btnFloatPrev.setPadding(dp(9), dp(9), dp(9), dp(9));
        FrameLayout.LayoutParams prevFlp = new FrameLayout.LayoutParams(dp(42), dp(42), Gravity.LEFT | Gravity.CENTER_VERTICAL);
        prevFlp.leftMargin = dp(10);
        stageRoot.addView(btnFloatPrev, prevFlp);
        activeImagePrevFloat = btnFloatPrev;

        ImageButton btnFloatNext = createCleanBorderlessIconButton(R.drawable.ic_skip_next, dp(42), Color.WHITE);
        GradientDrawable nextScrim = new GradientDrawable();
        nextScrim.setShape(GradientDrawable.OVAL);
        nextScrim.setColor(Color.argb(125, 8, 12, 22));
        nextScrim.setStroke(Math.max(1, dp(1)), Color.argb(75, 255, 255, 255));
        btnFloatNext.setBackground(nextScrim);
        btnFloatNext.setPadding(dp(9), dp(9), dp(9), dp(9));
        FrameLayout.LayoutParams nextFlp = new FrameLayout.LayoutParams(dp(42), dp(42), Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        nextFlp.rightMargin = dp(10);
        stageRoot.addView(btnFloatNext, nextFlp);
        activeImageNextFloat = btnFloatNext;

        FrameLayout topOverlay = new FrameLayout(this);
        topOverlay.setClickable(true);
        topOverlay.setBackgroundColor(Color.argb(172, 24, 34, 54));
        topOverlay.setPadding(dp(12), dp(26), dp(12), dp(8));

        ImageButton btnImgBack = createCleanBorderlessIconButton(R.drawable.ic_arrow_back, dp(44), Color.WHITE);
        btnImgBack.setContentDescription("חזרה");
        btnImgBack.setOnClickListener(v -> proceedCloseOrNavigateBack());
        topOverlay.addView(btnImgBack, new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.TOP | Gravity.LEFT));

        ImageButton btnImgHamburger = createCleanBorderlessIconButton(R.drawable.ic_menu_hamburger, dp(44), Color.WHITE);
        btnImgHamburger.setContentDescription("אפשרויות");
        btnImgHamburger.setOnClickListener(v -> showImageOptionsDialog());
        topOverlay.addView(btnImgHamburger, new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.TOP | Gravity.RIGHT));

        FrameLayout.LayoutParams topOverlayLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        stageRoot.addView(topOverlay, topOverlayLp);
        activeImageTopOverlay = topOverlay;

        LinearLayout bottomActionsRow = new LinearLayout(this);
        bottomActionsRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomActionsRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        bottomActionsRow.setGravity(Gravity.CENTER);
        bottomActionsRow.setClickable(true);
        bottomActionsRow.setBackgroundColor(Color.argb(172, 24, 34, 54));
        bottomActionsRow.setPadding(dp(12), dp(10), dp(12), dp(14));
        activeMediaBottomPanel = bottomActionsRow;

        final ImageButton btnProImageEditor = createCleanBorderlessIconButton(R.drawable.ic_edit_pencil, dp(46), Color.WHITE);
        btnProImageEditor.setContentDescription("עורך תמונות");
        btnProImageEditor.setOnClickListener(v -> {
            Intent i = new Intent(this, ProImageEditorActivity.class);
            i.putExtra(ProImageEditorActivity.EXTRA_IMAGE_PATH, targetFile.getAbsolutePath());
            startActivity(i);
        });
        LinearLayout.LayoutParams editIconLp = new LinearLayout.LayoutParams(dp(46), dp(46));
        editIconLp.setMarginStart(dp(14));
        editIconLp.setMarginEnd(dp(14));
        btnProImageEditor.setLayoutParams(editIconLp);

        final ImageButton btnShareBottom = createCleanBorderlessIconButton(R.drawable.ic_share, dp(46), Color.WHITE);
        btnShareBottom.setContentDescription("שיתוף");
        btnShareBottom.setOnClickListener(v -> shareCurrentFile());
        LinearLayout.LayoutParams shareIconLp = new LinearLayout.LayoutParams(dp(46), dp(46));
        shareIconLp.setMarginStart(dp(14));
        shareIconLp.setMarginEnd(dp(14));
        btnShareBottom.setLayoutParams(shareIconLp);

        final ImageButton btnWallpaper = createCleanBorderlessIconButton(R.drawable.ic_wallpaper, dp(46), Color.WHITE);
        btnWallpaper.setContentDescription("קבע כרקע");
        btnWallpaper.setOnClickListener(v -> {
            Intent wi = new Intent(this, WallpaperCropActivity.class);
            wi.putExtra(WallpaperCropActivity.EXTRA_IMAGE_PATH, targetFile.getAbsolutePath());
            startActivity(wi);
        });
        LinearLayout.LayoutParams wallIconLp = new LinearLayout.LayoutParams(dp(46), dp(46));
        wallIconLp.setMarginStart(dp(14));
        wallIconLp.setMarginEnd(dp(14));
        btnWallpaper.setLayoutParams(wallIconLp);

        bottomActionsRow.addView(btnProImageEditor);
        bottomActionsRow.addView(btnShareBottom);
        bottomActionsRow.addView(btnWallpaper);

        FrameLayout.LayoutParams bottomLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        stageRoot.addView(bottomActionsRow, bottomLp);

        final Runnable preloadNeighbors = () -> {
            if (imagePlaylist.size() <= 1) return;
            int[] offsets = new int[]{1, -1};
            for (int off : offsets) {
                int nIdx = (currentImageIdx + off + imagePlaylist.size()) % imagePlaylist.size();
                File neighbor = imagePlaylist.get(nIdx);
                String nLow = neighbor.getName().toLowerCase(Locale.ROOT);
                if (MediaFormatHelper.isSvgFile(nLow) || MediaFormatHelper.isGifFile(nLow)) continue;
                String cacheKey = neighbor.getAbsolutePath() + ":" + neighbor.lastModified();
                if (photoCache.get(cacheKey) != null) continue;
                executor.execute(() -> {
                    if (photoCache.get(cacheKey) != null) return;
                    DecodedPhoto decoded = decodeUltraClearPhoto(neighbor);
                    if (decoded != null && decoded.bitmap != null) {
                        photoCache.put(cacheKey, decoded);
                    }
                });
            }
        };

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

            if (fallbackSvgWebView.getVisibility() != View.GONE) {
                fallbackSvgWebView.setVisibility(View.GONE);
            }
            if (zoomView.getVisibility() != View.VISIBLE) {
                zoomView.setVisibility(View.VISIBLE);
            }
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
                            preloadNeighbors.run();
                        });
                        return;
                    } catch (Throwable ignored) {
                    }
                });
                return;
            }

            final String cacheKey = imgFile.getAbsolutePath() + ":" + imgFile.lastModified();
            DecodedPhoto cached = photoCache.get(cacheKey);
            if (cached != null && cached.bitmap != null && !cached.bitmap.isRecycled()) {
                zoomView.setImageBitmap(cached.bitmap);
                preloadNeighbors.run();
                return;
            }

            executor.execute(() -> {
                DecodedPhoto photo = decodeUltraClearPhoto(imgFile);
                if (photo.bitmap != null) {
                    photoCache.put(cacheKey, photo);
                }
                uiHandler.post(() -> {
                    if (gen != imageLoadGeneration || isFinishing()) return;
                    if (photo.bitmap != null) {
                        zoomView.setImageBitmap(photo.bitmap);
                        preloadNeighbors.run();
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

    private TextView createCleanCenterTimeOverlay() {
        TextView tv = new TextView(this);
        tv.setTextSize(30f);
        tv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tv.setTextColor(Color.WHITE);
        tv.setTextDirection(View.TEXT_DIRECTION_LTR);
        tv.setGravity(Gravity.CENTER);
        tv.setBackground(null);
        tv.setShadowLayer(8f, 0f, 2f, Color.argb(210, 0, 0, 0));
        tv.setVisibility(View.GONE);
        tv.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        return tv;
    }

    private void renderAudioPlayer(
            LinearLayout host,
            int defaultBgColor,
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

        int primaryBtnPx = dp(Math.round(54 * scale));
        int skipBtnPx = dp(Math.round(42 * scale));
        int seek10BtnPx = dp(Math.round(40 * scale));
        int secRowBtnPx = dp(Math.round(40 * scale));
        int btnMarginPx = dp(Math.round(6 * scale));

        // מסך נקי באחידות מלאה ללא מלבנים מקיפים
        LinearLayout playerRoot = new LinearLayout(this);
        playerRoot.setBackground(null);
        audioStagePlayerView = playerRoot;
        if (isLandscape) {
            playerRoot.setOrientation(LinearLayout.HORIZONTAL);
            playerRoot.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            playerRoot.setPadding(dp(8), dp(4), dp(8), dp(4));
        } else {
            playerRoot.setOrientation(LinearLayout.VERTICAL);
        }

        FrameLayout stageFrame = new FrameLayout(this);
        stageFrame.setBackground(null);

        final ImageView ivArt = new ImageView(this);
        ivArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        ivArt.setBackgroundColor(Color.TRANSPARENT);
        ivArt.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(20));
            }
        });

        FrameLayout.LayoutParams artLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER);
        stageFrame.addView(ivArt, artLp);

        final TextView tvDoubleTapBadge = createSeekOverlayBadge();
        stageFrame.addView(tvDoubleTapBadge);

        attachDoubleTapSeekGesture(stageFrame, tvDoubleTapBadge, deltaSec -> mgr.seekBy(deltaSec * 1000));

        LinearLayout controlsColumn = new LinearLayout(this);
        controlsColumn.setOrientation(LinearLayout.VERTICAL);
        controlsColumn.setGravity(Gravity.CENTER_VERTICAL);

        if (isLandscape) {
            LinearLayout.LayoutParams stageLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.44f);
            stageLp.rightMargin = dp(14);
            playerRoot.addView(stageFrame, stageLp);

            LinearLayout.LayoutParams ctrlLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.56f);
            playerRoot.addView(controlsColumn, ctrlLp);
        } else {
            LinearLayout.LayoutParams stageLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
            stageLp.bottomMargin = dp(10);
            playerRoot.addView(stageFrame, stageLp);

            playerRoot.addView(controlsColumn, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        LinearLayout timeRowLtr = new LinearLayout(this);
        timeRowLtr.setOrientation(LinearLayout.HORIZONTAL);
        timeRowLtr.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        timeRowLtr.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        timeRowLtr.setPadding(dp(6), dp(4), dp(6), dp(2));

        final TextView tvElapsedLeftmost = new TextView(this);
        tvElapsedLeftmost.setText("00:00");
        tvElapsedLeftmost.setTextColor(accent);
        tvElapsedLeftmost.setTextSize(14f);
        tvElapsedLeftmost.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        timeRowLtr.addView(tvElapsedLeftmost);

        final TextView tvTotalNextToIt = new TextView(this);
        tvTotalNextToIt.setText("  /  00:00");
        tvTotalNextToIt.setTextColor(accent);
        tvTotalNextToIt.setTextSize(13.5f);
        tvTotalNextToIt.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        timeRowLtr.addView(tvTotalNextToIt);

        controlsColumn.addView(timeRowLtr, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final WavySeekBar seekBar = new WavySeekBar(this);
        seekBar.setColors(accent, ColorUtils.setAlphaComponent(textPrimary, 56), accent);
        controlsColumn.addView(seekBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // שורה 1: 10 שניות אחורה | הקודם | השהה/נגן | הבא | 10 שניות קדימה (הסרת העיגולים מלבד כפתור הנגן הראשי)
        LinearLayout transportRow = new LinearLayout(this);
        transportRow.setOrientation(LinearLayout.HORIZONTAL);
        transportRow.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        transportRow.setGravity(Gravity.CENTER);
        transportRow.setPadding(0, dp(isLandscape ? 4 : 8), 0, dp(isLandscape ? 4 : 6));

        final ImageButton btnBack10 = createCleanBorderlessIconButton(R.drawable.ic_replay_10, seek10BtnPx, textPrimary);
        btnBack10.setContentDescription("10 שניות אחורה");
        final ImageButton btnPrevSong = createCleanBorderlessIconButton(R.drawable.ic_skip_prev, skipBtnPx, textPrimary);
        btnPrevSong.setContentDescription("רצועה קודמת");
        final ImageButton btnPlayPause = createResponsiveRoundIconButton(R.drawable.ic_pause, primaryBtnPx, btnMarginPx, true, accent, theme);
        btnPlayPause.setContentDescription("נגן / השהה");
        final ImageButton btnNextSong = createCleanBorderlessIconButton(R.drawable.ic_skip_next, skipBtnPx, textPrimary);
        btnNextSong.setContentDescription("רצועה הבאה");
        final ImageButton btnFwd10 = createCleanBorderlessIconButton(R.drawable.ic_forward_10, seek10BtnPx, textPrimary);
        btnFwd10.setContentDescription("10 שניות קדימה");

        LinearLayout.LayoutParams b10Lp = new LinearLayout.LayoutParams(seek10BtnPx, seek10BtnPx);
        b10Lp.setMargins(btnMarginPx, 0, btnMarginPx, 0);
        btnBack10.setLayoutParams(b10Lp);

        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(skipBtnPx, skipBtnPx);
        pLp.setMargins(btnMarginPx, 0, btnMarginPx, 0);
        btnPrevSong.setLayoutParams(pLp);

        LinearLayout.LayoutParams nLp = new LinearLayout.LayoutParams(skipBtnPx, skipBtnPx);
        nLp.setMargins(btnMarginPx, 0, btnMarginPx, 0);
        btnNextSong.setLayoutParams(nLp);

        LinearLayout.LayoutParams f10Lp = new LinearLayout.LayoutParams(seek10BtnPx, seek10BtnPx);
        f10Lp.setMargins(btnMarginPx, 0, btnMarginPx, 0);
        btnFwd10.setLayoutParams(f10Lp);

        transportRow.addView(btnBack10);
        transportRow.addView(btnPrevSong);
        transportRow.addView(btnPlayPause);
        transportRow.addView(btnNextSong);
        transportRow.addView(btnFwd10);
        controlsColumn.addView(transportRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // שורה 2 מתחת לנגן/השהה: ימין (+ תפריט כלים), אמצע (אייקון מהירות בלבד), שמאל (אייקון חזרה בלבד)
        FrameLayout secondRow = new FrameLayout(this);
        secondRow.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        secondRow.setPadding(dp(14), dp(2), dp(14), dp(2));

        final ImageButton btnMorePlus = createCleanBorderlessIconButton(R.drawable.ic_add, secRowBtnPx, textPrimary);
        btnMorePlus.setContentDescription("כלים נוספים");
        secondRow.addView(btnMorePlus, new FrameLayout.LayoutParams(
                secRowBtnPx, secRowBtnPx, Gravity.RIGHT | Gravity.CENTER_VERTICAL));

        final ImageButton btnSpeedCenter = createCleanBorderlessIconButton(R.drawable.ic_speed, secRowBtnPx, textPrimary);
        btnSpeedCenter.setContentDescription("מהירות השמעה");
        secondRow.addView(btnSpeedCenter, new FrameLayout.LayoutParams(
                secRowBtnPx, secRowBtnPx, Gravity.CENTER));

        final ImageButton btnRepeatMode = createCleanBorderlessIconButton(R.drawable.ic_repeat_off, secRowBtnPx, textPrimary);
        btnRepeatMode.setContentDescription("חזרה");
        secondRow.addView(btnRepeatMode, new FrameLayout.LayoutParams(
                secRowBtnPx, secRowBtnPx, Gravity.LEFT | Gravity.CENTER_VERTICAL));

        controlsColumn.addView(secondRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        host.addView(playerRoot, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final AudioAdaptivePalette[] activePalHolder = new AudioAdaptivePalette[]{
                AudioPaletteHelper.computePalette(this, null, theme, defaultBgColor, textPrimary, textSecondary, accent)
        };
        final Object[] lastArtworkKey = new Object[]{new Object()};
        final String[] lastTrackCounter = new String[]{null};
        final int[] lastPlayRes = new int[]{0};
        final int[] lastRepeatRes = new int[]{0};
        final String[] lastElapsedStr = new String[]{null};
        final String[] lastTotalStr = new String[]{null};

        final Runnable applyPaletteAndButtonTints = () -> {
            AudioAdaptivePalette pal = activePalHolder[0];
            if (audioRootLayout != null) {
                audioRootLayout.setBackgroundColor(pal.bg);
            }
            if (topBar != null) {
                topBar.setBackgroundColor(Color.TRANSPARENT);
            }
            Window window = getWindow();
            if (window != null) {
                window.setStatusBarColor(pal.bg);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    window.setNavigationBarColor(pal.bg);
                }
                WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
                if (controller != null) {
                    controller.setAppearanceLightStatusBars(!pal.darkSurface);
                    controller.setAppearanceLightNavigationBars(!pal.darkSurface);
                }
            }
            if (tvMarqueeTitle != null) {
                tvMarqueeTitle.setTextColor(pal.textPrimary);
            }
            if (tvFolderTrackCounter != null) {
                tvFolderTrackCounter.setTextColor(pal.textSecondary);
            }
            if (audioTopBackBtn != null) {
                audioTopBackBtn.setColorFilter(pal.textPrimary);
            }
            if (audioTopShareBtn != null) {
                audioTopShareBtn.setColorFilter(pal.textPrimary);
            }
            tvElapsedLeftmost.setTextColor(pal.accent);
            tvTotalNextToIt.setTextColor(pal.accent);
            seekBar.setColors(pal.accent, ColorUtils.setAlphaComponent(pal.textPrimary, 58), pal.accent);

            if (btnPlayPause.getBackground() instanceof GradientDrawable) {
                ((GradientDrawable) btnPlayPause.getBackground()).setColor(pal.accent);
            }
            btnPlayPause.setColorFilter(pal.playIconTint);

            if (tvAudioCenterScrubTime != null) {
                tvAudioCenterScrubTime.setTextColor(pal.textPrimary);
            }
            btnBack10.setColorFilter(pal.textPrimary);
            btnPrevSong.setColorFilter(pal.textPrimary);
            btnNextSong.setColorFilter(pal.textPrimary);
            btnFwd10.setColorFilter(pal.textPrimary);
            btnMorePlus.setColorFilter(pal.textPrimary);
            btnSpeedCenter.setColorFilter(Math.abs(mgr.getPlaybackSpeed() - 1.0f) >= 0.02f ? pal.accent : pal.textPrimary);
            btnRepeatMode.setColorFilter(mgr.getRepeatMode() != AudioPlayerManager.REPEAT_OFF ? pal.accent : pal.textPrimary);

            Bitmap curArt = mgr.getCurrentArtwork();
            if (curArt == null || curArt.isRecycled()) {
                ivArt.setColorFilter(pal.accent);
            } else {
                ivArt.clearColorFilter();
            }
        };

        btnBack10.setOnClickListener(v -> mgr.seekBy(-10_000));
        btnFwd10.setOnClickListener(v -> mgr.seekBy(10_000));
        btnPrevSong.setOnClickListener(v -> mgr.playPrevious(this));
        btnNextSong.setOnClickListener(v -> mgr.playNext(this));
        btnPlayPause.setOnClickListener(v -> mgr.togglePlayPause(this));

        btnSpeedCenter.setOnClickListener(v -> showSpeedPickerDialog("בחירת מהירות השיר", mgr.getPlaybackSpeed(), chosen -> {
            mgr.setPlaybackSpeed(this, chosen);
            btnSpeedCenter.setColorFilter(Math.abs(chosen - 1.0f) >= 0.02f ? activePalHolder[0].accent : activePalHolder[0].textPrimary);
        }));

        btnRepeatMode.setOnClickListener(v -> {
            int nextMode = mgr.cycleRepeatMode();
            lastRepeatRes[0] = resolveRepeatIcon(nextMode);
            btnRepeatMode.setImageResource(lastRepeatRes[0]);
            btnRepeatMode.setColorFilter(nextMode != AudioPlayerManager.REPEAT_OFF ? activePalHolder[0].accent : activePalHolder[0].textPrimary);
        });

        btnMorePlus.setOnClickListener(v -> {
            AudioAdaptivePalette pal = activePalHolder[0];
            LinearLayout iconStrip = new LinearLayout(this);
            iconStrip.setOrientation(LinearLayout.HORIZONTAL);
            iconStrip.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            iconStrip.setGravity(Gravity.CENTER_VERTICAL);
            iconStrip.setPadding(dp(10), dp(6), dp(10), dp(6));

            GradientDrawable stripBg = new GradientDrawable();
            stripBg.setCornerRadius(999f);
            stripBg.setColor(pal.darkSurface
                    ? ColorUtils.setAlphaComponent(Color.parseColor("#1E293B"), 242)
                    : ColorUtils.setAlphaComponent(Color.WHITE, 245));
            stripBg.setStroke(Math.max(1, dp(1)), ColorUtils.setAlphaComponent(pal.textSecondary, 60));
            iconStrip.setBackground(stripBg);
            iconStrip.setElevation(dp(8));

            final PopupWindow[] popupRef = new PopupWindow[1];

            int iconSize = dp(40);
            ImageButton bQueue = createCleanBorderlessIconButton(R.drawable.ic_queue, iconSize, pal.textPrimary);
            bQueue.setContentDescription("תור ניגון");
            bQueue.setOnClickListener(iv -> {
                if (popupRef[0] != null) popupRef[0].dismiss();
                showAudioQueueDialog();
            });

            int sleepTint = mgr.getSleepTimerRemainingMs() > 0 ? pal.accent : pal.textPrimary;
            ImageButton bSleep = createCleanBorderlessIconButton(R.drawable.ic_sleep_timer, iconSize, sleepTint);
            bSleep.setContentDescription("טיימר שינה");
            bSleep.setOnClickListener(iv -> {
                if (popupRef[0] != null) popupRef[0].dismiss();
                showSleepTimerDialog();
            });

            ImageButton bEq = createCleanBorderlessIconButton(R.drawable.ic_equalizer, iconSize, pal.textPrimary);
            bEq.setContentDescription("אקולייזר");
            bEq.setOnClickListener(iv -> {
                if (popupRef[0] != null) popupRef[0].dismiss();
                showEqualizerDialog();
            });

            ImageButton bCut = createCleanBorderlessIconButton(R.drawable.ic_audio_cutter, iconSize, pal.textPrimary);
            bCut.setContentDescription("חתוך אודיו");
            bCut.setOnClickListener(iv -> {
                if (popupRef[0] != null) popupRef[0].dismiss();
                File current = mgr.getCurrentFile() != null ? mgr.getCurrentFile() : targetFile;
                Intent ci = new Intent(this, AudioCutterActivity.class);
                ci.putExtra(AudioCutterActivity.EXTRA_INITIAL_PATH, current.getAbsolutePath());
                startActivity(ci);
            });

            iconStrip.addView(bQueue);
            iconStrip.addView(bSleep);
            iconStrip.addView(bEq);
            iconStrip.addView(bCut);

            iconStrip.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            int popW = iconStrip.getMeasuredWidth();
            int popH = iconStrip.getMeasuredHeight();
            PopupWindow popup = new PopupWindow(iconStrip,
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
            popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            popup.setOutsideTouchable(true);
            popupRef[0] = popup;

            int[] loc = new int[2];
            v.getLocationInWindow(loc);
            int screenW = getResources().getDisplayMetrics().widthPixels;
            int showX = Math.max(dp(8), Math.min(screenW - popW - dp(8), loc[0] + v.getWidth() / 2 - popW / 2));
            int showY = Math.max(dp(12), loc[1] - popH - dp(8));
            popup.showAtLocation(v, Gravity.NO_GRAVITY, showX, showY);
        });

        seekBar.setOnSeekBarChangeListener(new WavySeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(WavySeekBar sb, int progress, boolean fromUser) {
                if (fromUser) mgr.seekTo(progress);
            }
            @Override public void onStartTrackingTouch(WavySeekBar sb) {}
            @Override public void onStopTrackingTouch(WavySeekBar sb) {}
        });

        final Runnable syncAudioUi = () -> {
            File active = mgr.getCurrentFile();
            if (active != null) {
                targetFile = active;
                updateMarqueeHeaderName(active.getName());
            }
            if (tvFolderTrackCounter != null) {
                String cntStr = mgr.getCurrentTrackNumber() + "/" + mgr.getFolderTrackCount();
                if (!cntStr.equals(lastTrackCounter[0])) {
                    lastTrackCounter[0] = cntStr;
                    tvFolderTrackCounter.setVisibility(View.VISIBLE);
                    tvFolderTrackCounter.setText(cntStr);
                }
            }

            Bitmap art = mgr.getCurrentArtwork();
            if (lastArtworkKey[0] != art) {
                lastArtworkKey[0] = art;
                if (art != null && !art.isRecycled()) {
                    ivArt.setPadding(0, 0, 0, 0);
                    ivArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    ivArt.setClipToOutline(true);
                    ivArt.setImageBitmap(art);
                } else {
                    int fallbackPad = dp(isLandscape ? 28 : 54);
                    ivArt.setPadding(fallbackPad, fallbackPad, fallbackPad, fallbackPad);
                    ivArt.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    ivArt.setClipToOutline(false);
                    ivArt.setImageResource(R.drawable.ic_audio);
                }
                activePalHolder[0] = AudioPaletteHelper.computePalette(this, art, theme, defaultBgColor, textPrimary, textSecondary, accent);
                applyPaletteAndButtonTints.run();
            } else {
                btnSpeedCenter.setColorFilter(Math.abs(mgr.getPlaybackSpeed() - 1.0f) >= 0.02f
                        ? activePalHolder[0].accent : activePalHolder[0].textPrimary);
                btnRepeatMode.setColorFilter(mgr.getRepeatMode() != AudioPlayerManager.REPEAT_OFF
                        ? activePalHolder[0].accent : activePalHolder[0].textPrimary);
            }

            boolean playingNow = mgr.isPlaying();
            seekBar.setPlaying(playingNow);
            int playRes = playingNow ? R.drawable.ic_pause : R.drawable.ic_play;
            if (lastPlayRes[0] != playRes) {
                lastPlayRes[0] = playRes;
                btnPlayPause.setImageResource(playRes);
            }
            int repRes = resolveRepeatIcon(mgr.getRepeatMode());
            if (lastRepeatRes[0] != repRes) {
                lastRepeatRes[0] = repRes;
                btnRepeatMode.setImageResource(repRes);
            }
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
                seekBar.setPlaying(mgr.isPlaying());
                seekBar.setMax(Math.max(1, dur));
                seekBar.setProgress(pos);
                String posStr = formatClock(pos);
                if (!posStr.equals(lastElapsedStr[0])) {
                    lastElapsedStr[0] = posStr;
                    tvElapsedLeftmost.setText(posStr);
                }
                String totStr = "  /  " + formatClock(dur);
                if (!totStr.equals(lastTotalStr[0])) {
                    lastTotalStr[0] = totStr;
                    tvTotalNextToIt.setText(totStr);
                }
                uiHandler.postDelayed(this, 250);
            }
        });
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
                    ImageButton up = createCleanBorderlessIconButton(R.drawable.ic_chevron_up, dp(32), accent);
                    up.setContentDescription("הזז למעלה");
                    up.setOnClickListener(v -> {
                        mgr.moveQueueItem(idx, idx - 1);
                        rebuild[0].run();
                    });
                    row.addView(up);
                }
                if (i < queue.size() - 1) {
                    ImageButton dn = createCleanBorderlessIconButton(R.drawable.ic_chevron_down, dp(32), accent);
                    dn.setContentDescription("הזז למטה");
                    dn.setOnClickListener(v -> {
                        mgr.moveQueueItem(idx, idx + 1);
                        rebuild[0].run();
                    });
                    row.addView(dn);
                }
                if (i != curIdx && queue.size() > 1) {
                    ImageButton del = createCleanBorderlessIconButton(R.drawable.ic_close, dp(32), Color.parseColor("#DC2626"));
                    del.setContentDescription("הסר מהתור");
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
            ThemeHelper.applySeekBarTint(sb, accent);
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
        ThemeHelper.applySeekBarTint(sbBass, accent);
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
        videoTextureView.setAlpha(0f);
        videoViewport.addView(videoTextureView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));

        ivVideoPreviewThumb = new ImageView(this);
        ivVideoPreviewThumb.setScaleType(ImageView.ScaleType.FIT_CENTER);
        ivVideoPreviewThumb.setBackgroundColor(Color.BLACK);
        ivVideoPreviewThumb.setVisibility(View.VISIBLE);
        videoViewport.addView(ivVideoPreviewThumb, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));

        videoTextureView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if ((right - left) != (oldRight - oldLeft) || (bottom - top) != (oldBottom - oldTop)) {
                hasAppliedVideoMatrix = false;
                updateVideoAspectRatioMatrix();
            }
        });

        final TextView tvVideoDoubleTapBadge = createSeekOverlayBadge();
        videoViewport.addView(tvVideoDoubleTapBadge);

        tvVideoCenterScrubTime = createCleanCenterTimeOverlay();
        videoViewport.addView(tvVideoCenterScrubTime);

        setupVideoAdvancedTouchGestures(videoViewport, tvVideoDoubleTapBadge);

        stageRoot.addView(videoViewport, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // עיגול תכלת במרכז הווידאו בלבד ללא כפתורים צדדיים
        int skyBlueColor = Color.parseColor("#38BDF8");
        final ImageButton btnCenterPlayPause = createResponsiveRoundIconButton(
                R.drawable.ic_play, dp(62), 0, true, skyBlueColor, theme);
        btnCenterPlayPause.setColorFilter(Color.WHITE);
        btnCenterPlayPause.setContentDescription("נגן / השהה");
        activeVideoCenterOverlay = btnCenterPlayPause;

        FrameLayout.LayoutParams centerLp = new FrameLayout.LayoutParams(
                dp(62), dp(62), Gravity.CENTER);
        stageRoot.addView(btnCenterPlayPause, centerLp);

        // כפתור שחרור נעילה בתחתית המסך בעת נעילת וידאו
        final ImageButton btnFloatingUnlock = createCleanBorderlessIconButton(R.drawable.ic_lock, dp(48), Color.WHITE);
        btnFloatingUnlock.setContentDescription("שחרור נעילת מסך");
        GradientDrawable lockCircleBg = new GradientDrawable();
        lockCircleBg.setShape(GradientDrawable.OVAL);
        lockCircleBg.setColor(Color.argb(145, 10, 15, 28));
        btnFloatingUnlock.setBackground(lockCircleBg);
        btnFloatingUnlock.setVisibility(View.GONE);
        activeVideoLockFloatingBtn = btnFloatingUnlock;
        FrameLayout.LayoutParams unlockLp = new FrameLayout.LayoutParams(
                dp(48), dp(48), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        unlockLp.bottomMargin = dp(24);
        stageRoot.addView(btnFloatingUnlock, unlockLp);

        // סרגל חלופי לפתיחה בנגן חיצוני במקרה שקודק ספציפי אינו נתמך בחומרה
        videoFallbackBar = new LinearLayout(this);
        videoFallbackBar.setOrientation(LinearLayout.VERTICAL);
        videoFallbackBar.setGravity(Gravity.CENTER);
        videoFallbackBar.setClickable(true);
        videoFallbackBar.setPadding(dp(18), dp(14), dp(18), dp(14));
        GradientDrawable fbBg = new GradientDrawable();
        fbBg.setCornerRadius(dp(16));
        fbBg.setColor(Color.argb(225, 15, 23, 42));
        fbBg.setStroke(dp(1), Color.argb(90, 255, 255, 255));
        videoFallbackBar.setBackground(fbBg);
        videoFallbackBar.setVisibility(View.GONE);

        TextView tvFallbackMsg = new TextView(this);
        tvFallbackMsg.setText("פורמט וידאו זה דורש מפענח מערכת חיצוני");
        tvFallbackMsg.setTextColor(Color.WHITE);
        tvFallbackMsg.setTextSize(14f);
        tvFallbackMsg.setTypeface(Typeface.DEFAULT_BOLD);
        tvFallbackMsg.setGravity(Gravity.CENTER);
        tvFallbackMsg.setPadding(0, 0, 0, dp(10));
        videoFallbackBar.addView(tvFallbackMsg);

        Button btnOpenExternalVideo = ThemedDialogHelper.createThemedButton(this, "נגן בנגן מערכת / חיצוני", theme, true, false);
        btnOpenExternalVideo.setOnClickListener(v -> FileOpenWithHelper.openWithSystemChooser(this, targetFile));
        videoFallbackBar.addView(btnOpenExternalVideo);

        FrameLayout.LayoutParams fbLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        fbLp.setMargins(dp(24), 0, dp(24), 0);
        stageRoot.addView(videoFallbackBar, fbLp);

        // פאנל תחתון נקי ושקוף ללא ריבועים סביב האייקונים
        LinearLayout bottomCard = new LinearLayout(this);
        bottomCard.setOrientation(LinearLayout.VERTICAL);
        bottomCard.setClickable(true);
        bottomCard.setPadding(dp(10), dp(6), dp(10), dp(8));
        GradientDrawable transBottom = new GradientDrawable();
        transBottom.setCornerRadius(dp(16));
        transBottom.setColor(Color.argb(170, 10, 15, 28));
        bottomCard.setBackground(transBottom);
        activeMediaBottomPanel = bottomCard;

        // שורת אייקון נעילה מעל שורת ההתקדמות
        LinearLayout lockTopRow = new LinearLayout(this);
        lockTopRow.setOrientation(LinearLayout.HORIZONTAL);
        lockTopRow.setGravity(Gravity.CENTER);
        final ImageButton btnVidLock = createCleanBorderlessIconButton(R.drawable.ic_lock_open, dp(34), Color.WHITE);
        btnVidLock.setPadding(dp(6), dp(5), dp(6), dp(5));
        btnVidLock.setContentDescription("נעילת מסך הוידאו");
        lockTopRow.addView(btnVidLock, new LinearLayout.LayoutParams(dp(42), dp(32)));
        bottomCard.addView(lockTopRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // פס התקדמות מוגבה מעט מעל שתי שורות הפקדים
        LinearLayout timeBar = new LinearLayout(this);
        timeBar.setOrientation(LinearLayout.HORIZONTAL);
        timeBar.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        timeBar.setGravity(Gravity.CENTER_VERTICAL);
        timeBar.setPadding(dp(2), dp(2), dp(2), dp(8));

        final TextView tvVidPos = new TextView(this);
        tvVidPos.setText("00:00");
        tvVidPos.setTextColor(Color.WHITE);
        tvVidPos.setTextSize(12f);
        tvVidPos.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        timeBar.addView(tvVidPos);

        final WavySeekBar videoSeek = new WavySeekBar(this);
        videoSeek.setColors(accent, Color.argb(95, 255, 255, 255), Color.WHITE);
        timeBar.addView(videoSeek, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final TextView tvVidDur = new TextView(this);
        tvVidDur.setText("00:00");
        tvVidDur.setTextColor(Color.parseColor("#CBD5E1"));
        tvVidDur.setTextSize(12f);
        tvVidDur.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        timeBar.addView(tvVidDur);

        bottomCard.addView(timeBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // שורה 1 (RTL מימין לשמאל): סיבוב מסך | וידאו קדימה | השהה/נגן | וידאו אחורה | השמעה אקראית
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row1.setGravity(Gravity.CENTER_VERTICAL);

        final ImageButton btnVidRotate = createCleanBorderlessIconButton(R.drawable.ic_screen_rotate, dp(38), Color.WHITE);
        btnVidRotate.setContentDescription("סיבוב מסך");
        final ImageButton btnVidNext = createCleanBorderlessIconButton(R.drawable.ic_skip_next, dp(38), Color.WHITE);
        btnVidNext.setContentDescription("וידאו הבא");
        final ImageButton btnVidPlayPause = createCleanBorderlessIconButton(R.drawable.ic_pause, dp(40), Color.WHITE);
        btnVidPlayPause.setContentDescription("נגן / השהה");
        final ImageButton btnVidPrev = createCleanBorderlessIconButton(R.drawable.ic_skip_prev, dp(38), Color.WHITE);
        btnVidPrev.setContentDescription("וידאו קודם");
        final ImageButton btnVidShuffle = createCleanBorderlessIconButton(R.drawable.ic_shuffle, dp(38), Color.WHITE);
        btnVidShuffle.setContentDescription("השמעה אקראית");

        row1.addView(btnVidRotate, new LinearLayout.LayoutParams(0, dp(38), 1f));
        row1.addView(btnVidNext, new LinearLayout.LayoutParams(0, dp(38), 1f));
        row1.addView(btnVidPlayPause, new LinearLayout.LayoutParams(0, dp(40), 1f));
        row1.addView(btnVidPrev, new LinearLayout.LayoutParams(0, dp(38), 1f));
        row1.addView(btnVidShuffle, new LinearLayout.LayoutParams(0, dp(38), 1f));
        bottomCard.addView(row1, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // שורה 2 (RTL מימין לשמאל): מתיחה והתאמה למסך | 10 שניות קדימה | מהירות (אייקון בלבד) | 10 שניות אחורה | חזרה
        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row2.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams row2Lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        row2Lp.topMargin = dp(2);

        final ImageButton btnVidStretch = createCleanBorderlessIconButton(R.drawable.ic_aspect_fit, dp(36), Color.WHITE);
        btnVidStretch.setContentDescription("מתיחת הוידאו והתאמה למסך");
        final ImageButton btnVidFwd10 = createCleanBorderlessIconButton(R.drawable.ic_forward_10, dp(36), Color.WHITE);
        btnVidFwd10.setContentDescription("10 שניות קדימה");
        final ImageButton btnVidSpeed = createCleanBorderlessIconButton(R.drawable.ic_speed, dp(36), Color.WHITE);
        btnVidSpeed.setContentDescription("מהירות ניגון");
        final ImageButton btnVidBack10 = createCleanBorderlessIconButton(R.drawable.ic_replay_10, dp(36), Color.WHITE);
        btnVidBack10.setContentDescription("10 שניות אחורה");
        final ImageButton btnVidRepeat = createCleanBorderlessIconButton(R.drawable.ic_repeat_off, dp(36), Color.WHITE);
        btnVidRepeat.setContentDescription("חזרה על הוידאו");

        row2.addView(btnVidStretch, new LinearLayout.LayoutParams(0, dp(36), 1f));
        row2.addView(btnVidFwd10, new LinearLayout.LayoutParams(0, dp(36), 1f));
        row2.addView(btnVidSpeed, new LinearLayout.LayoutParams(0, dp(36), 1f));
        row2.addView(btnVidBack10, new LinearLayout.LayoutParams(0, dp(36), 1f));
        row2.addView(btnVidRepeat, new LinearLayout.LayoutParams(0, dp(36), 1f));
        bottomCard.addView(row2, row2Lp);

        FrameLayout.LayoutParams bottomLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        bottomLp.setMargins(dp(10), 0, dp(10), dp(10));
        stageRoot.addView(bottomCard, bottomLp);

        final boolean[] lastSyncedPlaying = new boolean[]{false};
        final int[] lastSyncedPlayRes = new int[]{0};
        final int[] lastSyncedRepeatRes = new int[]{0};
        final String[] lastCounterStr = new String[]{null};

        final Runnable syncVidButtons = () -> {
            boolean playing = isVideoPlayingSafely();
            lastSyncedPlaying[0] = playing;
            videoSeek.setPlaying(playing);
            int playRes = playing ? R.drawable.ic_pause : R.drawable.ic_play;
            if (lastSyncedPlayRes[0] != playRes) {
                lastSyncedPlayRes[0] = playRes;
                btnCenterPlayPause.setImageResource(playRes);
                btnVidPlayPause.setImageResource(playRes);
            }
            int repeatRes = resolveRepeatIcon(videoRepeatMode);
            if (lastSyncedRepeatRes[0] != repeatRes) {
                lastSyncedRepeatRes[0] = repeatRes;
                btnVidRepeat.setImageResource(repeatRes);
            }
            btnVidRepeat.setColorFilter(videoRepeatMode != AudioPlayerManager.REPEAT_OFF ? accent : Color.WHITE);
            btnVidShuffle.setColorFilter(videoShuffleMode ? accent : Color.WHITE);
            btnVidStretch.setColorFilter(videoScaleMode != 0 ? accent : Color.WHITE);
            btnVidSpeed.setColorFilter(Math.abs(videoSpeed - 1.0f) >= 0.02f ? accent : Color.WHITE);

            if (tvFolderTrackCounter != null && !videoPlaylist.isEmpty()) {
                String cntStr = (currentVideoIdx + 1) + "/" + videoPlaylist.size();
                if (!cntStr.equals(lastCounterStr[0])) {
                    lastCounterStr[0] = cntStr;
                    tvFolderTrackCounter.setVisibility(View.VISIBLE);
                    tvFolderTrackCounter.setText(cntStr);
                }
            }
        };
        activeVideoSyncRunnable = syncVidButtons;

        View.OnClickListener playPauseClick = v -> {
            boolean wasAwaiting = awaitingInitialVideoPlay;
            awaitingInitialVideoPlay = false;
            suppressNextVideoAutoPlay = false;
            if (exoPlayer == null && videoPlayer == null) {
                loadVideoTrackAtIndex(currentVideoIdx, syncVidButtons);
                applyMediaImmersiveAndLayoutState();
                return;
            }
            if (!videoPrepared) {
                applyMediaImmersiveAndLayoutState();
                return;
            }
            try {
                if (wasAwaiting || !isVideoPlayingSafely()) {
                    applyVideoSpeedInternal(true);
                } else {
                    pauseVideoSafely();
                }
            } catch (Exception ignored) {
            }
            syncVidButtons.run();
            if (wasAwaiting) {
                applyMediaImmersiveAndLayoutState();
            }
        };

        btnCenterPlayPause.setOnClickListener(playPauseClick);
        btnVidPlayPause.setOnClickListener(playPauseClick);
        btnVidBack10.setOnClickListener(v -> seekVideoBy(-10_000));
        btnVidFwd10.setOnClickListener(v -> seekVideoBy(10_000));
        btnVidPrev.setOnClickListener(v -> advanceVideoPlaylist(-1, syncVidButtons));
        btnVidNext.setOnClickListener(v -> advanceVideoPlaylist(1, syncVidButtons));

        btnVidShuffle.setOnClickListener(v -> {
            videoShuffleMode = !videoShuffleMode;
            syncVidButtons.run();
            Toast.makeText(this,
                    videoShuffleMode ? "השמעה אקראית הופעלה" : "השמעה אקראית כובתה",
                    Toast.LENGTH_SHORT).show();
        });

        btnVidStretch.setOnClickListener(v -> {
            videoScaleMode = (videoScaleMode + 1) % 3;
            resetVideoZoom();
            String modeMsg = videoScaleMode == 1 ? "מילוי מסך"
                    : (videoScaleMode == 2 ? "מתיחה למסך מלא" : "התאמה למסך");
            Toast.makeText(this, modeMsg, Toast.LENGTH_SHORT).show();
            syncVidButtons.run();
        });

        btnVidRepeat.setOnClickListener(v -> {
            videoRepeatMode = (videoRepeatMode + 1) % 3;
            syncVidButtons.run();
        });
        btnVidRotate.setOnClickListener(v -> toggleScreenOrientation());
        btnVidSpeed.setOnClickListener(v -> showSpeedPickerDialog("בחירת מהירות הסרטון", videoSpeed, chosen -> {
            videoSpeed = chosen;
            speedModifiedFromDefault = true;
            applyVideoSpeedInternal(isVideoPlayingSafely());
            syncVidButtons.run();
        }));

        btnVidLock.setOnClickListener(v -> {
            stopVideoHoldGestures(tvVideoDoubleTapBadge);
            isVideoLocked = true;
            mediaControlsHidden = true;
            applyMediaImmersiveAndLayoutState();
            Toast.makeText(this, "מסך הוידאו ננעל", Toast.LENGTH_SHORT).show();
        });

        btnFloatingUnlock.setOnClickListener(v -> {
            isVideoLocked = false;
            mediaControlsHidden = false;
            applyMediaImmersiveAndLayoutState();
        });

        videoSeek.setOnSeekBarChangeListener(new WavySeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(WavySeekBar sb, int progress, boolean fromUser) {
                if (fromUser && videoPrepared) {
                    int clamped = Math.max(0, progress);
                    tvVidPos.setText(formatClock(clamped));
                    long now = System.currentTimeMillis();
                    boolean isTs = targetFile != null && MpegTsNormalizer.isLikelyMpegTsName(targetFile.getName());
                    long minInterval = isTs ? 450L : 220L;
                    if (now - lastLiveSeekUiMs >= minInterval) {
                        lastLiveSeekUiMs = now;
                        seekVideoToMs(clamped);
                    }
                }
            }
            @Override
            public void onStartTrackingTouch(WavySeekBar sb) {
                isUserSeekingVideo = true;
            }
            @Override
            public void onStopTrackingTouch(WavySeekBar sb) {
                isUserSeekingVideo = false;
                if (videoPrepared) {
                    int target = Math.max(0, sb.getProgress());
                    pendingSeekTargetMs = target;
                    holdSeekUiUntilMs = System.currentTimeMillis() + 900L;
                    tvVidPos.setText(formatClock(target));
                    seekVideoToMs(target);
                }
            }
        });

        videoTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
                activeVideoSurface = new Surface(surface);
                hasAppliedVideoMatrix = false;
                if (exoPlayer != null) {
                    exoPlayer.setVideoSurface(activeVideoSurface);
                    updateVideoAspectRatioMatrix();
                } else if (videoPlayer != null) {
                    videoPlayer.setSurface(activeVideoSurface);
                    updateVideoAspectRatioMatrix();
                } else if (!isRemuxingMtsPlayback) {
                    loadVideoTrackAtIndex(currentVideoIdx, syncVidButtons);
                }
            }
            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
                hasAppliedVideoMatrix = false;
                updateVideoAspectRatioMatrix();
            }
            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
                if (exoPlayer != null) {
                    try { exoPlayer.setVideoSurface(null); } catch (Exception ignored) {}
                }
                if (videoPlayer != null) {
                    try { videoPlayer.setSurface(null); } catch (Exception ignored) {}
                }
                activeVideoSurface = null;
                return true;
            }
            @Override public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {}
        });

        final String[] lastTickerPos = new String[]{null};
        final String[] lastTickerDur = new String[]{null};
        final int[] lastTickerMax = new int[]{-1};
        final boolean[] lastTickerSeekEnabled = new boolean[]{true};

        videoTickerActive = true;
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!videoTickerActive) return;
                if (videoPrepared && (exoPlayer != null || videoPlayer != null)) {
                    try {
                        videoSeek.setPlaying(isVideoPlayingSafely());
                        int dur = getEffectiveVideoDurationMs();
                        int pos = getCurrentVideoPositionMs();
                        long now = System.currentTimeMillis();

                        // זיהוי אוטומטי אם זרם MTS גולמי חוזר להתחלה מעצמו בגלל איפוס PTS
                        if (!isUserSeekingVideo && now >= holdSeekUiUntilMs && isVideoPlayingSafely()) {
                            if (pos + 700 < lastObservedVideoPosMs
                                    && lastObservedVideoPosMs < (dur > 2500 ? dur - 1000 : Integer.MAX_VALUE)
                                    && videoRepeatMode == AudioPlayerManager.REPEAT_OFF
                                    && targetFile != null
                                    && MpegTsNormalizer.isLikelyMpegTsName(targetFile.getName())
                                    && !exoRemuxFallbackAttempted) {
                                if (firstPosResetObservedMs == 0L || now - firstPosResetObservedMs > 20_000L) {
                                    unexpectedPosResetCount = 1;
                                    firstPosResetObservedMs = now;
                                } else {
                                    unexpectedPosResetCount++;
                                }
                                if (unexpectedPosResetCount >= 2 || dur <= 0 || lastObservedVideoPosMs < 2500) {
                                    startRemuxedMtsPlayback(targetFile, syncVidButtons);
                                    uiHandler.postDelayed(this, 250);
                                    return;
                                }
                            } else if (firstPosResetObservedMs > 0L && now - firstPosResetObservedMs > 20_000L) {
                                unexpectedPosResetCount = 0;
                                firstPosResetObservedMs = 0L;
                            }
                            lastObservedVideoPosMs = pos;
                        }

                        if (dur > 0) {
                            if (!lastTickerSeekEnabled[0]) {
                                lastTickerSeekEnabled[0] = true;
                                videoSeek.setEnabled(true);
                            }
                            if (lastTickerMax[0] != dur) {
                                lastTickerMax[0] = dur;
                                videoSeek.setMax(dur);
                            }
                            boolean holdingSeek = now < holdSeekUiUntilMs && pendingSeekTargetMs >= 0;
                            if (holdingSeek && Math.abs(pos - pendingSeekTargetMs) <= 1500) {
                                holdingSeek = false;
                                holdSeekUiUntilMs = 0L;
                                pendingSeekTargetMs = -1;
                            }
                            if (!isUserSeekingVideo && !holdingSeek) {
                                videoSeek.setProgress(Math.min(dur, pos));
                                String posStr = formatClock(pos);
                                if (!posStr.equals(lastTickerPos[0])) {
                                    lastTickerPos[0] = posStr;
                                    tvVidPos.setText(posStr);
                                }
                            } else if (!isUserSeekingVideo && pendingSeekTargetMs >= 0) {
                                int clampedPending = Math.min(dur, Math.max(0, pendingSeekTargetMs));
                                videoSeek.setProgress(clampedPending);
                                String posStr = formatClock(clampedPending);
                                if (!posStr.equals(lastTickerPos[0])) {
                                    lastTickerPos[0] = posStr;
                                    tvVidPos.setText(posStr);
                                }
                            }
                            String durStr = formatClock(dur);
                            if (!durStr.equals(lastTickerDur[0])) {
                                lastTickerDur[0] = durStr;
                                tvVidDur.setText(durStr);
                            }
                        } else {
                            if (lastTickerSeekEnabled[0]) {
                                lastTickerSeekEnabled[0] = false;
                                videoSeek.setEnabled(false);
                            }
                            if (!isUserSeekingVideo) {
                                String posStr = formatClock(pos);
                                if (!posStr.equals(lastTickerPos[0])) {
                                    lastTickerPos[0] = posStr;
                                    tvVidPos.setText(posStr);
                                }
                            }
                            if (!"--:--".equals(lastTickerDur[0])) {
                                lastTickerDur[0] = "--:--";
                                tvVidDur.setText("--:--");
                            }
                        }

                        if (isVideoPlayingSafely() != lastSyncedPlaying[0]) {
                            syncVidButtons.run();
                        }
                    } catch (Exception ignored) {
                    }
                }
                uiHandler.postDelayed(this, 250);
            }
        });
    }

    private float[] computeBaseVideoScale(int viewW, int viewH) {
        if (videoScaleMode == 2) {
            return new float[]{1.0f, 1.0f};
        }
        float viewRatio = (float) viewW / (float) viewH;
        float effectiveWidth = (float) currentVideoWidth * Math.max(0.1f, currentVideoPixelRatio);
        float videoRatio = effectiveWidth / (float) currentVideoHeight;
        if (videoRatio <= 0f || viewRatio <= 0f) {
            return new float[]{1.0f, 1.0f};
        }
        float baseScaleX;
        float baseScaleY;
        if (videoScaleMode == 1) {
            if (videoRatio > viewRatio) {
                baseScaleX = videoRatio / viewRatio;
                baseScaleY = 1.0f;
            } else {
                baseScaleX = 1.0f;
                baseScaleY = viewRatio / videoRatio;
            }
        } else {
            if (videoRatio > viewRatio) {
                baseScaleX = 1.0f;
                baseScaleY = viewRatio / videoRatio;
            } else {
                baseScaleX = videoRatio / viewRatio;
                baseScaleY = 1.0f;
            }
        }
        return new float[]{baseScaleX, baseScaleY};
    }

    private void clampVideoTranslation() {
        if (videoTextureView == null) return;
        int viewW = videoTextureView.getWidth();
        int viewH = videoTextureView.getHeight();
        if (viewW <= 0 || viewH <= 0) return;

        float[] base = (currentVideoWidth > 0 && currentVideoHeight > 0)
                ? computeBaseVideoScale(viewW, viewH)
                : new float[]{1.0f, 1.0f};
        float contentW = viewW * base[0] * videoScale;
        float contentH = viewH * base[1] * videoScale;

        float maxDx = Math.max(0f, (contentW - viewW) * 0.5f);
        float maxDy = Math.max(0f, (contentH - viewH) * 0.5f);
        videoTransX = Math.max(-maxDx, Math.min(maxDx, videoTransX));
        videoTransY = Math.max(-maxDy, Math.min(maxDy, videoTransY));
    }

    private void updateVideoAspectRatioMatrix() {
        if (videoTextureView == null) return;
        int viewW = videoTextureView.getWidth();
        int viewH = videoTextureView.getHeight();
        if (viewW <= 0 || viewH <= 0 || currentVideoWidth <= 0 || currentVideoHeight <= 0) return;

        float[] base = computeBaseVideoScale(viewW, viewH);
        clampVideoTranslation();

        Matrix matrix = new Matrix();
        matrix.setScale(base[0] * videoScale, base[1] * videoScale, viewW * 0.5f, viewH * 0.5f);
        matrix.postTranslate(videoTransX, videoTransY);

        float[] nextValues = new float[9];
        matrix.getValues(nextValues);
        if (hasAppliedVideoMatrix) {
            boolean same = true;
            for (int i = 0; i < 9; i++) {
                if (Math.abs(nextValues[i] - lastAppliedMatrixValues[i]) > 0.0005f) {
                    same = false;
                    break;
                }
            }
            if (!same) {
                System.arraycopy(nextValues, 0, lastAppliedMatrixValues, 0, 9);
                videoTextureView.setTransform(matrix);
            }
        } else {
            System.arraycopy(nextValues, 0, lastAppliedMatrixValues, 0, 9);
            hasAppliedVideoMatrix = true;
            videoTextureView.setTransform(matrix);
        }
        revealVideoSurfaceIfReady();
    }

    private void revealVideoSurfaceIfReady() {
        if (videoTextureView == null) return;
        boolean hasDims = currentVideoWidth > 0 && currentVideoHeight > 0 && hasAppliedVideoMatrix;
        boolean showPreviewInstead = awaitingInitialVideoPlay
                && !isVideoPlayingSafely()
                && ivVideoPreviewThumb != null
                && ivVideoPreviewThumb.getDrawable() != null;
        if (hasDims && !showPreviewInstead) {
            videoTextureView.setAlpha(1f);
            if (ivVideoPreviewThumb != null) {
                ivVideoPreviewThumb.setVisibility(View.GONE);
            }
        }
    }

    private void advanceVideoPlaylist(int direction, Runnable onReadyUi) {
        awaitingInitialVideoPlay = false;
        suppressNextVideoAutoPlay = false;
        if (videoPlaylist.isEmpty()) return;
        if (videoPlaylist.size() == 1) {
            currentVideoIdx = 0;
            loadVideoTrackAtIndex(0, onReadyUi);
            return;
        }
        if (videoShuffleMode) {
            int nextIdx = currentVideoIdx;
            for (int attempt = 0; attempt < 6 && nextIdx == currentVideoIdx; attempt++) {
                nextIdx = ThreadLocalRandom.current().nextInt(videoPlaylist.size());
            }
            if (nextIdx == currentVideoIdx) {
                nextIdx = (currentVideoIdx + 1) % videoPlaylist.size();
            }
            currentVideoIdx = nextIdx;
        } else {
            int step = direction >= 0 ? 1 : -1;
            currentVideoIdx = (currentVideoIdx + step + videoPlaylist.size()) % videoPlaylist.size();
        }
        loadVideoTrackAtIndex(currentVideoIdx, onReadyUi);
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
        cleanupActiveRemuxedCache();
        File nextFile = videoPlaylist.get(Math.max(0, Math.min(videoPlaylist.size() - 1, idx)));
        targetFile = nextFile;
        updateMarqueeHeaderName(nextFile.getName());
        resetVideoZoom();
        knownVideoDurationMs = 0;
        pendingSeekTargetMs = -1;
        holdSeekUiUntilMs = 0L;
        lastObservedVideoPosMs = 0;
        unexpectedPosResetCount = 0;
        firstPosResetObservedMs = 0L;
        droppedVideoFrameBurst = 0;
        firstDroppedFrameBurstMs = 0L;
        exoAudioDisabledRetry = false;
        exoForcedTsRetry = false;
        exoRemuxFallbackAttempted = false;
        isRemuxingMtsPlayback = false;
        speedModifiedFromDefault = false;
        videoUsedFdFallback = false;
        if (videoFallbackBar != null) videoFallbackBar.setVisibility(View.GONE);
        if (videoTextureView != null) {
            videoTextureView.setAlpha(0f);
            videoTextureView.setKeepScreenOn(true);
        }
        if (ivVideoPreviewThumb != null) {
            ivVideoPreviewThumb.setImageDrawable(null);
            ivVideoPreviewThumb.setVisibility(awaitingInitialVideoPlay ? View.VISIBLE : View.GONE);
        }

        startVideoExoPlayer(nextFile, onReadyUi, false, false);

        // קריאת משך זמן, מימדים ותמונת תצוגה מקדימה ברקע (מונע תקיעות ממשק במיוחד בקבצי MTS)
        executor.execute(() -> {
            int resolvedDur = 0;
            int resolvedW = 0;
            int resolvedH = 0;
            Bitmap previewFrame = null;
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            Closeable tsDs = null;
            try {
                tsDs = MpegTsNormalizer.setMediaMetadataRetrieverSource(mmr, nextFile);
                String durStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                if (durStr != null) {
                    int parsed = Integer.parseInt(durStr.trim());
                    if (parsed > 0) resolvedDur = parsed;
                }
                String wStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
                String hStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
                String rotStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
                if (wStr != null && hStr != null) {
                    int pw = Integer.parseInt(wStr.trim());
                    int ph = Integer.parseInt(hStr.trim());
                    int rot = rotStr != null ? Integer.parseInt(rotStr.trim()) : 0;
                    if (rot == 90 || rot == 270) {
                        resolvedW = ph;
                        resolvedH = pw;
                    } else {
                        resolvedW = pw;
                        resolvedH = ph;
                    }
                }
                if (awaitingInitialVideoPlay) {
                    previewFrame = mmr.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                    if (previewFrame == null) {
                        previewFrame = mmr.getFrameAtTime(-1);
                    }
                }
            } catch (Throwable ignored) {
            } finally {
                try { mmr.release(); } catch (Throwable ignored) {}
                if (tsDs != null) {
                    try { tsDs.close(); } catch (Throwable ignored) {}
                }
            }
            if (resolvedDur <= 0 && MpegTsNormalizer.isLikelyMpegTsName(nextFile.getName())) {
                long tsMs = MpegTsNormalizer.extractTsDurationMs(nextFile);
                if (tsMs > 0L) {
                    resolvedDur = (int) Math.min(Integer.MAX_VALUE, tsMs);
                }
            }
            final int finalResolvedDur = resolvedDur;
            final int finalW = resolvedW;
            final int finalH = resolvedH;
            final Bitmap finalFrame = previewFrame;
            uiHandler.post(() -> {
                if (isFinishing() || isDestroyed() || targetFile == null || !targetFile.equals(nextFile)) {
                    if (finalFrame != null && !finalFrame.isRecycled()) finalFrame.recycle();
                    return;
                }
                if (finalResolvedDur > 0 && knownVideoDurationMs <= 0) {
                    knownVideoDurationMs = finalResolvedDur;
                }
                if (finalW > 0 && finalH > 0 && (currentVideoWidth <= 0 || currentVideoHeight <= 0)) {
                    currentVideoWidth = finalW;
                    currentVideoHeight = finalH;
                    hasAppliedVideoMatrix = false;
                    updateVideoAspectRatioMatrix();
                }
                if (finalFrame != null && ivVideoPreviewThumb != null && awaitingInitialVideoPlay && !isVideoPlayingSafely()) {
                    ivVideoPreviewThumb.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    ivVideoPreviewThumb.setImageBitmap(finalFrame);
                    ivVideoPreviewThumb.setVisibility(View.VISIBLE);
                    if (videoTextureView != null) videoTextureView.setAlpha(0f);
                } else {
                    if (finalFrame != null && !finalFrame.isRecycled()) finalFrame.recycle();
                    revealVideoSurfaceIfReady();
                }
            });
        });
    }

    private boolean isAudioOrCodecError(PlaybackException error) {
        if (error == null) return false;
        int code = error.errorCode;
        if (code == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
                || code == PlaybackException.ERROR_CODE_DECODING_FAILED
                || code == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
                || code == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED
                || code == PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED) {
            return true;
        }
        String msg = error.getMessage();
        if (msg != null) {
            String low = msg.toLowerCase(Locale.ROOT);
            if (low.contains("audio") || low.contains("ac3") || low.contains("eac3")
                    || low.contains("mpeg-l2") || low.contains("dts") || low.contains("decoder")) {
                return true;
            }
        }
        return false;
    }

    @OptIn(markerClass = UnstableApi.class)
    private void startVideoExoPlayer(File nextFile, Runnable onReadyUi, boolean disableAudioTrack, boolean forceTsExtractor) {
        releaseVideoPlayer();
        try {
            boolean isMpegTs = forceTsExtractor
                    || MpegTsNormalizer.isLikelyMpegTsName(nextFile.getName())
                    || MpegTsNormalizer.detectTsLayoutCached(nextFile).packetStride > 0;

            ExtractorsFactory extractorsFactory = SeekableTsExtractor.createExtractorsFactory(isMpegTs, () -> {
                if (knownVideoDurationMs > 0) return knownVideoDurationMs;
                long tsDur = MpegTsNormalizer.extractTsDurationMs(nextFile);
                if (tsDur > 0L) {
                    knownVideoDurationMs = (int) Math.min(Integer.MAX_VALUE, tsDur);
                }
                return tsDur;
            });

            DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(
                    new NormalizedTsExoDataSource.Factory(this), extractorsFactory);

            DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(this)
                    .setEnableDecoderFallback(true);

            DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                    .setBufferDurationsMs(15_000, 45_000, 250, 500)
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build();

            exoPlayer = new ExoPlayer.Builder(this, renderersFactory)
                    .setMediaSourceFactory(mediaSourceFactory)
                    .setLoadControl(loadControl)
                    .build();

            exoPlayer.setSeekParameters(SeekParameters.CLOSEST_SYNC);
            exoPlayer.setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT);

            if (disableAudioTrack) {
                exoPlayer.setTrackSelectionParameters(
                        exoPlayer.getTrackSelectionParameters()
                                .buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                                .build());
            }

            androidx.media3.common.AudioAttributes media3AudioAttrs = new androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build();
            exoPlayer.setAudioAttributes(media3AudioAttrs, true);
            exoPlayer.setVideoSurface(activeVideoSurface);
            exoPlayer.setVolume(videoMuted ? 0f : 1f);

            exoPlayer.addAnalyticsListener(new AnalyticsListener() {
                @Override
                public void onDroppedVideoFrames(@NonNull EventTime eventTime, int droppedFrames, long elapsedMs) {
                    if (!isMpegTs || exoRemuxFallbackAttempted || isRemuxingMtsPlayback) return;
                    long now = System.currentTimeMillis();
                    if (firstDroppedFrameBurstMs == 0L || now - firstDroppedFrameBurstMs > 3000L) {
                        firstDroppedFrameBurstMs = now;
                        droppedVideoFrameBurst = droppedFrames;
                    } else {
                        droppedVideoFrameBurst += droppedFrames;
                    }
                    if (droppedVideoFrameBurst >= 25) {
                        droppedVideoFrameBurst = 0;
                        firstDroppedFrameBurstMs = 0L;
                        uiHandler.post(() -> {
                            if (!isFinishing() && !isDestroyed()
                                    && targetFile != null && targetFile.equals(nextFile)
                                    && !exoRemuxFallbackAttempted) {
                                startRemuxedMtsPlayback(nextFile, onReadyUi);
                            }
                        });
                    }
                }
            });

            exoPlayer.addListener(new Player.Listener() {
                @Override
                public void onVideoSizeChanged(@NonNull VideoSize videoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        currentVideoWidth = videoSize.width;
                        currentVideoHeight = videoSize.height;
                        currentVideoPixelRatio = videoSize.pixelWidthHeightRatio > 0f
                                ? videoSize.pixelWidthHeightRatio : 1.0f;
                        hasAppliedVideoMatrix = false;
                        updateVideoAspectRatioMatrix();
                    }
                }

                @Override
                public void onIsPlayingChanged(boolean isPlaying) {
                    if (isPlaying) {
                        revealVideoSurfaceIfReady();
                    }
                    if (activeVideoSyncRunnable != null) activeVideoSyncRunnable.run();
                    else if (onReadyUi != null) onReadyUi.run();
                }

                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    if (exoPlayer == null) return;
                    if (playbackState == Player.STATE_BUFFERING) {
                        if (isMpegTs && !exoRemuxFallbackAttempted && !isRemuxingMtsPlayback && !awaitingInitialVideoPlay) {
                            uiHandler.removeCallbacks(mtsBufferingWatchdog);
                            uiHandler.postDelayed(mtsBufferingWatchdog, 4500L);
                        }
                    } else {
                        uiHandler.removeCallbacks(mtsBufferingWatchdog);
                    }
                    if (playbackState == Player.STATE_READY) {
                        videoPrepared = true;
                        revealVideoSurfaceIfReady();
                        long rawDur = exoPlayer.getDuration();
                        if (rawDur > 0L && rawDur != C.TIME_UNSET) {
                            boolean suspectTinyTs = isMpegTs && knownVideoDurationMs > 3000 && rawDur < knownVideoDurationMs / 2L;
                            if (!suspectTinyTs) {
                                knownVideoDurationMs = (int) Math.min(Integer.MAX_VALUE, rawDur);
                            }
                        }
                        if (Math.abs(videoSpeed - 1.0f) >= 0.02f) {
                            speedModifiedFromDefault = true;
                            applyVideoSpeedInternal(exoPlayer.getPlayWhenReady());
                        }
                        if (activeVideoSyncRunnable != null) activeVideoSyncRunnable.run();
                        else if (onReadyUi != null) onReadyUi.run();
                    } else if (playbackState == Player.STATE_ENDED) {
                        int effDur = getEffectiveVideoDurationMs();
                        int curPos = getCurrentVideoPositionMs();
                        if (isMpegTs && !exoRemuxFallbackAttempted && effDur > 6000 && curPos < effDur / 2) {
                            startRemuxedMtsPlayback(nextFile, onReadyUi);
                            return;
                        }
                        if (videoRepeatMode == AudioPlayerManager.REPEAT_ONE) {
                            exoPlayer.seekTo(0L);
                            exoPlayer.play();
                        } else if (videoRepeatMode == AudioPlayerManager.REPEAT_FOLDER && !videoPlaylist.isEmpty()) {
                            advanceVideoPlaylist(1, onReadyUi);
                        } else if (videoAutoNext && (videoShuffleMode || currentVideoIdx + 1 < videoPlaylist.size())) {
                            advanceVideoPlaylist(1, onReadyUi);
                        } else if (activeVideoSyncRunnable != null) {
                            activeVideoSyncRunnable.run();
                        } else if (onReadyUi != null) {
                            onReadyUi.run();
                        }
                    }
                }

                @Override
                public void onPlayerError(@NonNull PlaybackException error) {
                    if (!exoForcedTsRetry && !isMpegTs && MpegTsNormalizer.detectTsLayoutCached(nextFile).packetStride > 0) {
                        exoForcedTsRetry = true;
                        startVideoExoPlayer(nextFile, onReadyUi, disableAudioTrack, true);
                        return;
                    }
                    if (!exoAudioDisabledRetry && isAudioOrCodecError(error)) {
                        exoAudioDisabledRetry = true;
                        startVideoExoPlayer(nextFile, onReadyUi, true, isMpegTs);
                        return;
                    }
                    if (isMpegTs && !exoRemuxFallbackAttempted) {
                        startRemuxedMtsPlayback(nextFile, onReadyUi);
                        return;
                    }
                    startLegacyMediaPlayerFallback(nextFile, false, onReadyUi);
                }
            });

            MediaItem.Builder itemBuilder = new MediaItem.Builder().setUri(Uri.fromFile(nextFile));
            if (isMpegTs) {
                itemBuilder.setMimeType(MimeTypes.VIDEO_MP2T);
            }
            exoPlayer.setMediaItem(itemBuilder.build());
            boolean autoStart = !suppressNextVideoAutoPlay;
            exoPlayer.setPlayWhenReady(autoStart);
            exoPlayer.prepare();
            if (autoStart) {
                exoPlayer.play();
            }
        } catch (Throwable t) {
            startLegacyMediaPlayerFallback(nextFile, false, onReadyUi);
        }
    }

    private void startRemuxedMtsPlayback(File originalMtsFile, Runnable onReadyUi) {
        if (exoRemuxFallbackAttempted) {
            startLegacyMediaPlayerFallback(originalMtsFile, false, onReadyUi);
            return;
        }
        exoRemuxFallbackAttempted = true;
        isRemuxingMtsPlayback = true;
        releaseVideoPlayer();
        Toast.makeText(this, "מכין זרם MTS לצפייה חלקה...", Toast.LENGTH_SHORT).show();

        executor.execute(() -> {
            File cacheMp4 = new File(getCacheDir(), "mts_play_" + System.currentTimeMillis() + ".mp4");
            try {
                TsToMp4Remuxer.remuxToMp4(originalMtsFile, cacheMp4, 0L, Long.MAX_VALUE, null);
                uiHandler.post(() -> {
                    isRemuxingMtsPlayback = false;
                    if (isFinishing() || isDestroyed() || targetFile == null || !targetFile.equals(originalMtsFile)) {
                        if (cacheMp4.exists()) cacheMp4.delete();
                        return;
                    }
                    cleanupActiveRemuxedCache();
                    activeRemuxedCacheFile = cacheMp4;
                    startPlayableMp4InExo(cacheMp4, onReadyUi, originalMtsFile);
                });
            } catch (Throwable t) {
                if (cacheMp4.exists()) cacheMp4.delete();
                uiHandler.post(() -> {
                    isRemuxingMtsPlayback = false;
                    if (!isFinishing() && !isDestroyed() && targetFile != null && targetFile.equals(originalMtsFile)) {
                        startLegacyMediaPlayerFallback(originalMtsFile, false, onReadyUi);
                    }
                });
            }
        });
    }

    @OptIn(markerClass = UnstableApi.class)
    private void startPlayableMp4InExo(File mp4File, Runnable onReadyUi, File originalMtsFile) {
        releaseVideoPlayer();
        try {
            DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(this)
                    .setEnableDecoderFallback(true);
            DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                    .setBufferDurationsMs(15_000, 45_000, 250, 500)
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build();
            exoPlayer = new ExoPlayer.Builder(this, renderersFactory)
                    .setLoadControl(loadControl)
                    .build();
            exoPlayer.setSeekParameters(SeekParameters.CLOSEST_SYNC);
            exoPlayer.setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT);
            androidx.media3.common.AudioAttributes media3AudioAttrs = new androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build();
            exoPlayer.setAudioAttributes(media3AudioAttrs, true);
            if (activeVideoSurface != null) exoPlayer.setVideoSurface(activeVideoSurface);
            exoPlayer.setVolume(videoMuted ? 0f : 1f);
            exoPlayer.addListener(new Player.Listener() {
                @Override
                public void onVideoSizeChanged(@NonNull VideoSize videoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        currentVideoWidth = videoSize.width;
                        currentVideoHeight = videoSize.height;
                        currentVideoPixelRatio = videoSize.pixelWidthHeightRatio > 0f ? videoSize.pixelWidthHeightRatio : 1.0f;
                        hasAppliedVideoMatrix = false;
                        updateVideoAspectRatioMatrix();
                    }
                }

                @Override
                public void onIsPlayingChanged(boolean isPlaying) {
                    if (activeVideoSyncRunnable != null) activeVideoSyncRunnable.run();
                    else if (onReadyUi != null) onReadyUi.run();
                }

                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    if (exoPlayer == null) return;
                    if (playbackState == Player.STATE_READY) {
                        videoPrepared = true;
                        long rawDur = exoPlayer.getDuration();
                        if (rawDur > 0L && rawDur != C.TIME_UNSET) {
                            knownVideoDurationMs = (int) Math.min(Integer.MAX_VALUE, rawDur);
                        }
                        if (Math.abs(videoSpeed - 1.0f) >= 0.02f) {
                            speedModifiedFromDefault = true;
                            applyVideoSpeedInternal(exoPlayer.getPlayWhenReady());
                        }
                        if (activeVideoSyncRunnable != null) activeVideoSyncRunnable.run();
                        else if (onReadyUi != null) onReadyUi.run();
                    } else if (playbackState == Player.STATE_ENDED) {
                        if (videoRepeatMode == AudioPlayerManager.REPEAT_ONE) {
                            exoPlayer.seekTo(0L);
                            exoPlayer.play();
                        } else if (videoRepeatMode == AudioPlayerManager.REPEAT_FOLDER && !videoPlaylist.isEmpty()) {
                            advanceVideoPlaylist(1, onReadyUi);
                        } else if (videoAutoNext && (videoShuffleMode || currentVideoIdx + 1 < videoPlaylist.size())) {
                            advanceVideoPlaylist(1, onReadyUi);
                        } else if (activeVideoSyncRunnable != null) {
                            activeVideoSyncRunnable.run();
                        } else if (onReadyUi != null) {
                            onReadyUi.run();
                        }
                    }
                }

                @Override
                public void onPlayerError(@NonNull PlaybackException error) {
                    startLegacyMediaPlayerFallback(originalMtsFile, false, onReadyUi);
                }
            });
            exoPlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(mp4File)));
            boolean autoStart = !suppressNextVideoAutoPlay;
            exoPlayer.setPlayWhenReady(autoStart);
            exoPlayer.prepare();
            if (autoStart) {
                exoPlayer.play();
            }
        } catch (Throwable t) {
            startLegacyMediaPlayerFallback(originalMtsFile, false, onReadyUi);
        }
    }

    private void startLegacyMediaPlayerFallback(File nextFile, boolean useFileDescriptor, Runnable onReadyUi) {
        releaseVideoPlayer();
        videoUsedFdFallback = useFileDescriptor;
        try {
            videoPlayer = new MediaPlayer();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                videoPlayer.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .build());
            }
            legacyMediaSourceCloser = MpegTsNormalizer.setMediaPlayerSource(videoPlayer, nextFile, useFileDescriptor);
            videoPlayer.setSurface(activeVideoSurface);
            float vol = videoMuted ? 0f : 1f;
            videoPlayer.setVolume(vol, vol);
            videoPlayer.setOnVideoSizeChangedListener((mp, width, height) -> {
                if (width > 0 && height > 0) {
                    currentVideoWidth = width;
                    currentVideoHeight = height;
                    currentVideoPixelRatio = 1.0f;
                    hasAppliedVideoMatrix = false;
                    updateVideoAspectRatioMatrix();
                }
            });
            videoPlayer.setOnPreparedListener(mp -> {
                videoPrepared = true;
                int rawDur = 0;
                try { rawDur = mp.getDuration(); } catch (Exception ignored) {}
                if (rawDur > 0) knownVideoDurationMs = rawDur;
                currentVideoWidth = mp.getVideoWidth();
                currentVideoHeight = mp.getVideoHeight();
                currentVideoPixelRatio = 1.0f;
                hasAppliedVideoMatrix = false;
                updateVideoAspectRatioMatrix();
                boolean autoStart = !suppressNextVideoAutoPlay;
                try {
                    if (autoStart) {
                        mp.start();
                    } else {
                        mp.setVolume(0f, 0f);
                        mp.start();
                        mp.pause();
                        float restoreVol = videoMuted ? 0f : 1f;
                        mp.setVolume(restoreVol, restoreVol);
                        mp.seekTo(0);
                    }
                } catch (Exception ignored) {
                }
                if (Math.abs(videoSpeed - 1.0f) >= 0.02f) {
                    speedModifiedFromDefault = true;
                    applyVideoSpeedInternal(autoStart);
                }
                if (activeVideoSyncRunnable != null) activeVideoSyncRunnable.run();
                else if (onReadyUi != null) onReadyUi.run();
            });
            videoPlayer.setOnErrorListener((mp, what, extra) -> {
                if (!videoUsedFdFallback) {
                    startLegacyMediaPlayerFallback(nextFile, true, onReadyUi);
                    return true;
                }
                videoPrepared = false;
                if (videoFallbackBar != null) {
                    videoFallbackBar.setVisibility(View.VISIBLE);
                }
                if (activeVideoSyncRunnable != null) activeVideoSyncRunnable.run();
                else if (onReadyUi != null) onReadyUi.run();
                return true;
            });
            videoPlayer.setOnCompletionListener(mp -> {
                if (videoRepeatMode == AudioPlayerManager.REPEAT_ONE) {
                    try { mp.seekTo(0); } catch (Exception ignored) {}
                    applyVideoSpeedInternal(true);
                } else if (videoRepeatMode == AudioPlayerManager.REPEAT_FOLDER && !videoPlaylist.isEmpty()) {
                    advanceVideoPlaylist(1, onReadyUi);
                } else if (videoAutoNext && (videoShuffleMode || currentVideoIdx + 1 < videoPlaylist.size())) {
                    advanceVideoPlaylist(1, onReadyUi);
                } else if (activeVideoSyncRunnable != null) {
                    activeVideoSyncRunnable.run();
                } else if (onReadyUi != null) {
                    onReadyUi.run();
                }
            });
            videoPlayer.prepareAsync();
        } catch (Exception e) {
            if (!useFileDescriptor) {
                startLegacyMediaPlayerFallback(nextFile, true, onReadyUi);
                return;
            }
            videoPrepared = false;
            if (videoFallbackBar != null) videoFallbackBar.setVisibility(View.VISIBLE);
            if (activeVideoSyncRunnable != null) activeVideoSyncRunnable.run();
            else if (onReadyUi != null) onReadyUi.run();
        }
    }

    // מחוות וידאו מתקדמות: זום חד ועמוק מכל נקודה/פינה עד 20x באמצעות מטריצת GPU + גרירה חלקה + כיבוי בזמן נעילה
    private void setupVideoAdvancedTouchGestures(FrameLayout viewport, TextView badge) {
        final PointF lastPan = new PointF();
        final int[] activePanPointerId = new int[]{MotionEvent.INVALID_POINTER_ID};
        final float[] prevFocusX = new float[]{0f};
        final float[] prevFocusY = new float[]{0f};
        final boolean[] wasScalingSinceDown = new boolean[]{false};

        final ScaleGestureDetector scaleDetector = new ScaleGestureDetector(this,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScaleBegin(@NonNull ScaleGestureDetector detector) {
                        if (isVideoLocked) return false;
                        wasScalingSinceDown[0] = true;
                        stopVideoHoldGestures(badge);
                        prevFocusX[0] = detector.getFocusX();
                        prevFocusY[0] = detector.getFocusY();
                        return true;
                    }

                    @Override
                    public boolean onScale(@NonNull ScaleGestureDetector detector) {
                        if (isVideoLocked || videoTextureView == null) return false;
                        int viewW = videoTextureView.getWidth();
                        int viewH = videoTextureView.getHeight();
                        if (viewW <= 0 || viewH <= 0) return false;

                        float oldScale = videoScale;
                        float newScale = Math.max(1.0f, Math.min(20.0f, oldScale * detector.getScaleFactor()));
                        float actualFactor = (oldScale > 0f) ? (newScale / oldScale) : 1.0f;

                        float cx = viewW * 0.5f;
                        float cy = viewH * 0.5f;
                        float fx = detector.getFocusX();
                        float fy = detector.getFocusY();
                        float dfx = fx - prevFocusX[0];
                        float dfy = fy - prevFocusY[0];
                        prevFocusX[0] = fx;
                        prevFocusY[0] = fy;

                        videoScale = newScale;
                        if (videoScale <= 1.01f) {
                            videoScale = 1.0f;
                            videoTransX = 0f;
                            videoTransY = 0f;
                        } else {
                            videoTransX = (fx - cx) * (1f - actualFactor) + videoTransX * actualFactor + dfx;
                            videoTransY = (fy - cy) * (1f - actualFactor) + videoTransY * actualFactor + dfy;
                            clampVideoTranslation();
                        }
                        updateVideoAspectRatioMatrix();
                        return true;
                    }
                });

        final GestureDetector tapDetector = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(@NonNull MotionEvent e) {
                        return true;
                    }

                    @Override
                    public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                        if (isVideoLocked || wasScalingSinceDown[0]) return true;
                        toggleMediaControlsVisibility();
                        return true;
                    }

                    @Override
                    public boolean onDoubleTap(@NonNull MotionEvent e) {
                        if (isVideoLocked || wasScalingSinceDown[0]) return true;
                        float w = Math.max(1f, viewport.getWidth());
                        float normX = e.getX() / w;
                        if (normX > 0.34f && normX < 0.66f) {
                            if (videoPrepared && (exoPlayer != null || videoPlayer != null)) {
                                if (isVideoPlayingSafely()) pauseVideoSafely();
                                else applyVideoSpeedInternal(true);
                                flashStatusBadge(badge, isVideoPlayingSafely() ? "מנגן" : "מושהה");
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
                    public void onLongPress(@NonNull MotionEvent e) {
                        if (isVideoLocked || wasScalingSinceDown[0]) return;
                        if (!videoPrepared || (exoPlayer == null && videoPlayer == null)) return;
                        float w = Math.max(1f, viewport.getWidth());
                        float normX = e.getX() / w;
                        if (normX >= 0.62f) {
                            speedBeforeHold = videoSpeed;
                            isHold2xForwardActive = true;
                            videoSpeed = 2.0f;
                            applyVideoSpeedInternal(true);
                            showPersistentHoldBadge(badge, "הרצה קדימה");
                        } else if (normX <= 0.38f) {
                            isHold2xRewindActive = true;
                            showPersistentHoldBadge(badge, "הרצה אחורה");
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

        final int touchSlopPx = ViewConfiguration.get(this).getScaledTouchSlop();
        final float[] swipeDownRawX = new float[]{0f};
        final float[] swipeDownRawY = new float[]{0f};
        final boolean[] isHorizontalSwipeSeek = new boolean[]{false};
        final int[] horizontalSwipeStartPosMs = new int[]{0};
        final int[] horizontalSwipeTargetMs = new int[]{0};
        final long[] lastSwipeLiveSeekMs = new long[]{0L};

        viewport.setOnTouchListener((v, event) -> {
            if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true);
            if (isVideoLocked) {
                stopVideoHoldGestures(badge);
                if (tvVideoCenterScrubTime != null) tvVideoCenterScrubTime.setVisibility(View.GONE);
                isHorizontalSwipeSeek[0] = false;
                return true;
            }

            scaleDetector.onTouchEvent(event);
            tapDetector.onTouchEvent(event);

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    wasScalingSinceDown[0] = false;
                    isHorizontalSwipeSeek[0] = false;
                    activePanPointerId[0] = event.getPointerId(0);
                    lastPan.set(event.getX(), event.getY());
                    swipeDownRawX[0] = event.getRawX();
                    swipeDownRawY[0] = event.getRawY();
                    horizontalSwipeStartPosMs[0] = getCurrentVideoPositionMs();
                    horizontalSwipeTargetMs[0] = horizontalSwipeStartPosMs[0];
                    break;

                case MotionEvent.ACTION_POINTER_DOWN:
                    wasScalingSinceDown[0] = true;
                    isHorizontalSwipeSeek[0] = false;
                    if (tvVideoCenterScrubTime != null) tvVideoCenterScrubTime.setVisibility(View.GONE);
                    stopVideoHoldGestures(badge);
                    break;

                case MotionEvent.ACTION_MOVE:
                    if (!scaleDetector.isInProgress() && event.getPointerCount() == 1) {
                        if (videoScale > 1.01f && videoTextureView != null) {
                            int ptrIdx = event.findPointerIndex(activePanPointerId[0]);
                            if (ptrIdx < 0) ptrIdx = 0;
                            float x = event.getX(ptrIdx);
                            float y = event.getY(ptrIdx);
                            float dx = x - lastPan.x;
                            float dy = y - lastPan.y;
                            videoTransX += dx;
                            videoTransY += dy;
                            clampVideoTranslation();
                            updateVideoAspectRatioMatrix();
                            lastPan.set(x, y);
                        } else if (!wasScalingSinceDown[0] && !isHold2xForwardActive && !isHold2xRewindActive && videoPrepared) {
                            float dx = event.getRawX() - swipeDownRawX[0];
                            float dy = event.getRawY() - swipeDownRawY[0];
                            if (!isHorizontalSwipeSeek[0]
                                    && Math.abs(dx) > touchSlopPx * 1.5f
                                    && Math.abs(dx) > Math.abs(dy) * 1.3f) {
                                isHorizontalSwipeSeek[0] = true;
                            }
                            if (isHorizontalSwipeSeek[0]) {
                                int dur = getEffectiveVideoDurationMs();
                                float width = Math.max(1f, viewport.getWidth());
                                int deltaMs = Math.round((dx / width) * 90_000f);
                                int target = dur > 0
                                        ? Math.max(0, Math.min(dur, horizontalSwipeStartPosMs[0] + deltaMs))
                                        : Math.max(0, horizontalSwipeStartPosMs[0] + deltaMs);
                                horizontalSwipeTargetMs[0] = target;
                                pendingSeekTargetMs = target;
                                holdSeekUiUntilMs = System.currentTimeMillis() + 900L;
                                if (tvVideoCenterScrubTime != null) {
                                    tvVideoCenterScrubTime.setText(formatClock(target));
                                    tvVideoCenterScrubTime.setVisibility(View.VISIBLE);
                                }
                                long now = System.currentTimeMillis();
                                boolean isTs = targetFile != null && MpegTsNormalizer.isLikelyMpegTsName(targetFile.getName());
                                long minInterval = isTs ? 450L : 220L;
                                if (now - lastSwipeLiveSeekMs[0] >= minInterval) {
                                    lastSwipeLiveSeekMs[0] = now;
                                    seekVideoToMs(target);
                                }
                            }
                        }
                    }
                    break;

                case MotionEvent.ACTION_POINTER_UP: {
                    int upIndex = event.getActionIndex();
                    int upId = event.getPointerId(upIndex);
                    if (upId == activePanPointerId[0]) {
                        int newIndex = upIndex == 0 ? 1 : 0;
                        if (newIndex < event.getPointerCount()) {
                            activePanPointerId[0] = event.getPointerId(newIndex);
                            lastPan.set(event.getX(newIndex), event.getY(newIndex));
                        }
                    } else {
                        int idx = event.findPointerIndex(activePanPointerId[0]);
                        if (idx >= 0) {
                            lastPan.set(event.getX(idx), event.getY(idx));
                        }
                    }
                    break;
                }

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    activePanPointerId[0] = MotionEvent.INVALID_POINTER_ID;
                    if (isHorizontalSwipeSeek[0]) {
                        isHorizontalSwipeSeek[0] = false;
                        if (tvVideoCenterScrubTime != null) {
                            tvVideoCenterScrubTime.setVisibility(View.GONE);
                        }
                        if (videoPrepared) {
                            pendingSeekTargetMs = horizontalSwipeTargetMs[0];
                            holdSeekUiUntilMs = System.currentTimeMillis() + 900L;
                            seekVideoToMs(horizontalSwipeTargetMs[0]);
                        }
                    }
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
        lp.topMargin = Math.max(dp(86), lastVideoTopInsetPx + dp(62));
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
        hasAppliedVideoMatrix = false;
        if (videoTextureView != null) {
            videoTextureView.setScaleX(1.0f);
            videoTextureView.setScaleY(1.0f);
            videoTextureView.setTranslationX(0f);
            videoTextureView.setTranslationY(0f);
            updateVideoAspectRatioMatrix();
        }
    }

    private void toggleScreenOrientation() {
        boolean currentlyLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        isLandscapeLocked = !currentlyLandscape;
        setRequestedOrientation(isLandscapeLocked
                ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }

    private void showVideoPlusPopup(View anchor, int theme) {
        if (anchor == null || isFinishing() || isDestroyed()) return;
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(6), dp(8), dp(6));

        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setCornerRadius(dp(18));
        pillBg.setColor(Color.argb(232, 15, 23, 42));
        pillBg.setStroke(dp(1), Color.argb(95, 255, 255, 255));
        bar.setBackground(pillBg);

        final PopupWindow[] holder = new PopupWindow[1];

        ImageButton btnPip = createCleanBorderlessIconButton(R.drawable.ic_pip, dp(40), Color.WHITE);
        btnPip.setContentDescription("תמונה בתוך תמונה");
        btnPip.setOnClickListener(v -> {
            if (holder[0] != null) holder[0].dismiss();
            enterPictureInPictureSafe();
        });
        bar.addView(btnPip);

        ImageButton btnRot = createCleanBorderlessIconButton(R.drawable.ic_screen_rotate, dp(40), Color.WHITE);
        btnRot.setContentDescription("סיבוב מסך");
        btnRot.setOnClickListener(v -> {
            if (holder[0] != null) holder[0].dismiss();
            toggleScreenOrientation();
        });
        bar.addView(btnRot);

        ImageButton btnCut = createCleanBorderlessIconButton(R.drawable.ic_video_cutter, dp(40), Color.WHITE);
        btnCut.setContentDescription("חותך וידאו");
        btnCut.setOnClickListener(v -> {
            if (holder[0] != null) holder[0].dismiss();
            if (targetFile != null) {
                Intent ci = new Intent(this, VideoCutterActivity.class);
                ci.putExtra(VideoCutterActivity.EXTRA_INITIAL_PATH, targetFile.getAbsolutePath());
                startActivity(ci);
            }
        });
        bar.addView(btnCut);

        ImageButton btnShare = createCleanBorderlessIconButton(R.drawable.ic_share, dp(40), Color.WHITE);
        btnShare.setContentDescription("שיתוף");
        btnShare.setOnClickListener(v -> {
            if (holder[0] != null) holder[0].dismiss();
            shareCurrentFile();
        });
        bar.addView(btnShare);

        PopupWindow popup = new PopupWindow(
                bar,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );
        holder[0] = popup;
        popup.setOutsideTouchable(true);
        popup.setElevation(dp(10));
        popup.showAsDropDown(anchor, 0, dp(6), Gravity.END);
    }

    private void enterPictureInPictureSafe() {
        if (!isVideoMode || targetFile == null) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Toast.makeText(this, "תמונה בתוך תמונה זמינה מאנדרואיד 8 ומעלה", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            if (awaitingInitialVideoPlay) {
                awaitingInitialVideoPlay = false;
                suppressNextVideoAutoPlay = false;
                if (videoPrepared && !isVideoPlayingSafely()) {
                    applyVideoSpeedInternal(true);
                }
            }
            revealVideoSurfaceIfReady();
            isInPipMode = true;
            applyMediaImmersiveAndLayoutState();
            int w = currentVideoWidth > 0 ? currentVideoWidth : 16;
            int h = currentVideoHeight > 0 ? currentVideoHeight : 9;
            float ratio = (float) w / (float) h;
            if (ratio < 0.42f) ratio = 0.42f;
            if (ratio > 2.38f) ratio = 2.38f;
            Rational aspect = new Rational(Math.max(100, Math.round(ratio * 1000f)), 1000);
            PictureInPictureParams params = new PictureInPictureParams.Builder()
                    .setAspectRatio(aspect)
                    .build();
            enterPictureInPictureMode(params);
        } catch (Throwable t) {
            isInPipMode = false;
            applyMediaImmersiveAndLayoutState();
            Toast.makeText(this, "לא ניתן להפעיל תמונה בתוך תמונה במכשיר זה", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, @NonNull Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        isInPipMode = isInPictureInPictureMode;
        if (isVideoMode) {
            if (isInPictureInPictureMode) {
                mediaControlsHidden = true;
                resetVideoZoom();
            }
            applyMediaImmersiveAndLayoutState();
            hasAppliedVideoMatrix = false;
            if (videoTextureView != null) {
                videoTextureView.post(this::updateVideoAspectRatioMatrix);
            }
        }
    }

    private void showVideoSettingsDialog(int theme) {
        String muteStatus = videoMuted ? "מושתק (לחץ להפעלת קול)" : "פעיל (לחץ להשתקה)";
        String autoNextStatus = videoAutoNext ? "פעיל" : "כבוי";
        boolean autoGestures = PrefsManager.isAutoGestureMode(this);
        String repeatLabel = videoRepeatMode == AudioPlayerManager.REPEAT_ONE
                ? "חזרה על הסרטון עצמו"
                : (videoRepeatMode == AudioPlayerManager.REPEAT_FOLDER ? "חזרה על כל התיקייה" : "ללא חזרה");

        List<String> items = Arrays.asList(
                "תמונה בתוך תמונה (PiP)",
                "מהירות הסרטון (" + formatSpeedLabel(videoSpeed) + ")",
                "מצב חזרה (" + repeatLabel + ")",
                "שמע הסרטון: " + muteStatus,
                "מעבר אוטומטי לסרטון הבא: " + autoNextStatus,
                "מצב מחוות אוטומטי: " + (autoGestures ? "פועל (נקי)" : "כבוי (כפתורים)"),
                "סיבוב מסך מלא (אופקי / אנכי)",
                "איפוס זום תצוגה (100%)",
                "ייצוא קטע פתיחה ל-GIF מונפש",
                "עורך וידאו והפחתת משקל",
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
                            enterPictureInPictureSafe();
                            break;
                        case 1:
                            showSpeedPickerDialog("בחירת מהירות הסרטון", videoSpeed, chosen -> {
                                videoSpeed = chosen;
                                speedModifiedFromDefault = true;
                                applyVideoSpeedInternal(isVideoPlayingSafely());
                                if (activeVideoSyncRunnable != null) activeVideoSyncRunnable.run();
                            });
                            break;
                        case 2:
                            showRepeatPickerDialog("מצב חזרה בוידאו", videoRepeatMode, false, chosen -> {
                                videoRepeatMode = chosen;
                                if (activeVideoSyncRunnable != null) activeVideoSyncRunnable.run();
                            });
                            break;
                        case 3:
                            videoMuted = !videoMuted;
                            float vol = videoMuted ? 0f : 1f;
                            if (exoPlayer != null) {
                                exoPlayer.setVolume(vol);
                            }
                            if (videoPlayer != null) {
                                videoPlayer.setVolume(vol, vol);
                            }
                            Toast.makeText(this, videoMuted ? "שמע הסרטון הושתק" : "שמע הסרטון הופעל", Toast.LENGTH_SHORT).show();
                            break;
                        case 4:
                            videoAutoNext = !videoAutoNext;
                            Toast.makeText(this, videoAutoNext ? "מעבר אוטומטי הופעל" : "מעבר אוטומטי כובה", Toast.LENGTH_SHORT).show();
                            break;
                        case 5:
                            boolean nextMode = !autoGestures;
                            PrefsManager.setAutoGestureMode(this, nextMode);
                            Toast.makeText(this, nextMode ? "מצב מחוות אוטומטי הופעל" : "מצב כפתורים קלאסי הופעל", Toast.LENGTH_SHORT).show();
                            break;
                        case 6:
                            toggleScreenOrientation();
                            break;
                        case 7:
                            resetVideoZoom();
                            Toast.makeText(this, "הזום אופס ל-100%", Toast.LENGTH_SHORT).show();
                            break;
                        case 8:
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
                        case 9: {
                            Intent vei = new Intent(this, ProVideoEditorActivity.class);
                            vei.putExtra(ProVideoEditorActivity.EXTRA_VIDEO_PATH, targetFile.getAbsolutePath());
                            startActivity(vei);
                            break;
                        }
                        case 10:
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
        int theme = PrefsManager.getThemeMode(this);
        int bgColor = ThemeHelper.getCardColor(this, theme);
        int textPrimary = ThemeHelper.getPrimaryTextColor(this, theme);
        int accent = ThemeHelper.getAccentColor(this, theme);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER_HORIZONTAL);
        container.setMinimumWidth(0);
        container.setPadding(dp(10), dp(8), dp(10), dp(8));

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setCornerRadius(dp(16));
        cardBg.setColor(bgColor);
        container.setBackground(cardBg);

        AlertDialog dlg = new AlertDialog.Builder(this)
                .setView(container)
                .create();

        for (float v : values) {
            boolean selected = Math.abs(v - current) < 0.02f;
            String label = v == 1.0f
                    ? "1.0x"
                    : String.format(Locale.US, "%.2fx", v).replaceAll("0x$", "x");
            TextView tv = new TextView(this);
            tv.setText(label);
            tv.setTextSize(selected ? 18.5f : 16f);
            tv.setTypeface(Typeface.MONOSPACE, selected ? Typeface.BOLD : Typeface.NORMAL);
            tv.setTextColor(selected ? accent : textPrimary);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(dp(14), dp(7), dp(14), dp(7));
            tv.setBackgroundColor(Color.TRANSPARENT);
            tv.setOnClickListener(view -> {
                dlg.dismiss();
                cb.onSpeedChosen(v);
            });
            container.addView(tv, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        }
        dlg.show();
        if (dlg.getWindow() != null) {
            dlg.getWindow().setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            View decor = dlg.getWindow().getDecorView();
            if (decor != null) {
                decor.setMinimumWidth(0);
                decor.setPadding(0, 0, 0, 0);
            }
        }
    }

    private interface RepeatSelectedCallback {
        void onRepeatChosen(int repeatMode);
    }

    private void showRepeatPickerDialog(String title, int currentMode, boolean isAudio, RepeatSelectedCallback cb) {
        String itemLabel = isAudio ? "חזרה על השיר עצמו" : "חזרה על הסרטון עצמו";
        String folderLabel = isAudio ? "חזרה על כל הקבצים בתיקייה" : "חזרה על כל הסרטונים בתיקייה";
        List<String> opts = Arrays.asList(
                "ללא חזרה" + (currentMode == AudioPlayerManager.REPEAT_OFF ? "  (פעיל)" : ""),
                itemLabel + (currentMode == AudioPlayerManager.REPEAT_ONE ? "  (פעיל)" : ""),
                folderLabel + (currentMode == AudioPlayerManager.REPEAT_FOLDER ? "  (פעיל)" : "")
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
        int theme = PrefsManager.getThemeMode(this);
        int accent = ThemeHelper.getAccentColor(this, theme);
        TextView badge = new TextView(this);
        badge.setTextColor(Color.WHITE);
        badge.setTextSize(15f);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(16), dp(9), dp(16), dp(9));
        GradientDrawable glassPill = new GradientDrawable();
        glassPill.setCornerRadius(dp(16));
        glassPill.setColor(Color.argb(198, 12, 18, 30));
        glassPill.setStroke(Math.max(1, dp(1.1f)),
                ColorUtils.setAlphaComponent(ColorUtils.blendARGB(Color.WHITE, accent, 0.45f), 115));
        badge.setBackground(glassPill);
        badge.setElevation(dp(6));
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
        badge.setText(forwardRight ? "\u200E+10 ש׳" : "\u200E-10 ש׳");
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

    private ImageButton createCleanBorderlessIconButton(int drawableRes, int sizePx, int tintColor) {
        ImageButton btn = new ImageButton(this);
        btn.setImageResource(drawableRes);
        btn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        btn.setColorFilter(tintColor);
        int pad = Math.max(dp(6), Math.round(sizePx * 0.22f));
        btn.setPadding(pad, pad, pad, pad);
        TypedValue outValue = new TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
                && outValue.resourceId != 0) {
            btn.setBackgroundResource(outValue.resourceId);
        } else {
            btn.setBackgroundColor(Color.TRANSPARENT);
        }
        btn.setLayoutParams(new LinearLayout.LayoutParams(sizePx, sizePx));
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
            if (exoPlayer != null) {
                return videoPrepared && exoPlayer.isPlaying();
            }
            return videoPrepared && videoPlayer != null && videoPlayer.isPlaying();
        } catch (Exception e) {
            return false;
        }
    }

    private void pauseVideoSafely() {
        try {
            if (exoPlayer != null) {
                exoPlayer.pause();
                return;
            }
            if (isVideoPlayingSafely()) videoPlayer.pause();
        } catch (Exception ignored) {
        }
    }

    private int getEffectiveVideoDurationMs() {
        boolean isTs = targetFile != null && MpegTsNormalizer.isLikelyMpegTsName(targetFile.getName());
        if (exoPlayer != null) {
            try {
                long raw = exoPlayer.getDuration();
                if (raw > 0L && raw != C.TIME_UNSET) {
                    if (isTs && knownVideoDurationMs > 3000 && raw < knownVideoDurationMs / 2L) {
                        return knownVideoDurationMs;
                    }
                    knownVideoDurationMs = (int) Math.min(Integer.MAX_VALUE, raw);
                    return knownVideoDurationMs;
                }
            } catch (Exception ignored) {
            }
            return knownVideoDurationMs;
        }
        if (videoPlayer == null) return knownVideoDurationMs;
        try {
            int raw = videoPlayer.getDuration();
            if (raw > 0) {
                if (isTs && knownVideoDurationMs > 3000 && raw < knownVideoDurationMs / 2) {
                    return knownVideoDurationMs;
                }
                knownVideoDurationMs = raw;
                return raw;
            }
        } catch (Exception ignored) {
        }
        return knownVideoDurationMs;
    }

    private int getCurrentVideoPositionMs() {
        try {
            if (exoPlayer != null) {
                return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, exoPlayer.getCurrentPosition()));
            }
            if (videoPlayer != null) {
                return Math.max(0, videoPlayer.getCurrentPosition());
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private void seekVideoToMs(int targetMs) {
        int clamped = Math.max(0, targetMs);
        unexpectedPosResetCount = 0;
        firstPosResetObservedMs = 0L;
        lastObservedVideoPosMs = clamped;
        if (exoPlayer != null) {
            try {
                exoPlayer.seekTo(clamped);
            } catch (Exception ignored) {
            }
            return;
        }
        if (videoPlayer == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                videoPlayer.seekTo(clamped, MediaPlayer.SEEK_CLOSEST_SYNC);
            } else {
                videoPlayer.seekTo(clamped);
            }
        } catch (Exception ignored) {
            try { videoPlayer.seekTo(clamped); } catch (Exception ignored2) {}
        }
    }

    private void seekVideoBy(int deltaMs) {
        if (!videoPrepared || (exoPlayer == null && videoPlayer == null)) return;
        try {
            int dur = getEffectiveVideoDurationMs();
            long now = System.currentTimeMillis();
            int cur = (now < holdSeekUiUntilMs && pendingSeekTargetMs >= 0)
                    ? pendingSeekTargetMs
                    : getCurrentVideoPositionMs();
            int next = dur > 0 ? Math.max(0, Math.min(dur, cur + deltaMs)) : Math.max(0, cur + deltaMs);
            pendingSeekTargetMs = next;
            holdSeekUiUntilMs = now + 900L;
            seekVideoToMs(next);
        } catch (Exception ignored) {
        }
    }

    private void applyVideoSpeedInternal(boolean shouldPlay) {
        if (!videoPrepared) return;
        if (exoPlayer != null) {
            try {
                if (!shouldPlay && exoPlayer.isPlaying()) {
                    exoPlayer.pause();
                } else if (shouldPlay && !exoPlayer.isPlaying()) {
                    exoPlayer.play();
                }
                if (speedModifiedFromDefault || Math.abs(videoSpeed - 1.0f) >= 0.02f) {
                    exoPlayer.setPlaybackSpeed(videoSpeed);
                }
            } catch (Throwable speedEx) {
                videoSpeed = 1.0f;
                speedModifiedFromDefault = false;
                Toast.makeText(this, "שינוי מהירות אינו נתמך בפורמט וידאו זה", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (videoPlayer == null) return;
        try {
            if (!shouldPlay && videoPlayer.isPlaying()) {
                videoPlayer.pause();
            } else if (shouldPlay && !videoPlayer.isPlaying()) {
                videoPlayer.start();
            }

            boolean needSpeedParam = Math.abs(videoSpeed - 1.0f) >= 0.02f || speedModifiedFromDefault;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && needSpeedParam) {
                try {
                    PlaybackParams p = videoPlayer.getPlaybackParams();
                    if (p == null) p = new PlaybackParams();
                    p.setSpeed(videoSpeed);
                    videoPlayer.setPlaybackParams(p);
                    if (!shouldPlay && videoPlayer.isPlaying()) {
                        videoPlayer.pause();
                    }
                } catch (Throwable speedEx) {
                    videoSpeed = 1.0f;
                    speedModifiedFromDefault = false;
                    Toast.makeText(this, "שינוי מהירות אינו נתמך בפורמט וידאו זה", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            try {
                if (shouldPlay && !videoPlayer.isPlaying()) videoPlayer.start();
            } catch (Exception ignored) {
            }
        }
    }

    private void cleanupActiveRemuxedCache() {
        if (activeRemuxedCacheFile != null && activeRemuxedCacheFile.exists()) {
            try {
                activeRemuxedCacheFile.delete();
            } catch (Throwable ignored) {
            }
            activeRemuxedCacheFile = null;
        }
    }

    private void releaseVideoPlayer() {
        videoPrepared = false;
        currentVideoWidth = 0;
        currentVideoHeight = 0;
        currentVideoPixelRatio = 1.0f;
        lastObservedVideoPosMs = 0;
        unexpectedPosResetCount = 0;
        if (rewindPulseRunnable != null) uiHandler.removeCallbacks(rewindPulseRunnable);
        if (exoPlayer != null) {
            try { exoPlayer.stop(); } catch (Exception ignored) {}
            try { exoPlayer.release(); } catch (Exception ignored) {}
            exoPlayer = null;
        }
        if (videoPlayer != null) {
            try { videoPlayer.stop(); } catch (Exception ignored) {}
            try { videoPlayer.release(); } catch (Exception ignored) {}
            videoPlayer = null;
        }
        if (legacyMediaSourceCloser != null) {
            try { legacyMediaSourceCloser.close(); } catch (Exception ignored) {}
            legacyMediaSourceCloser = null;
        }
    }

    // ==================== מציג PDF ====================

    private void renderPdfViewer(LinearLayout host, int cardColor, int textSecondary, int theme) {
        int accent = ThemeHelper.getAccentColor(this, theme);
        int textPrimary = ThemeHelper.getPrimaryTextColor(this, theme);
        boolean dark = ThemeHelper.isDarkSurface(this, theme);

        FrameLayout pdfStage = new FrameLayout(this);
        pdfStage.setBackgroundColor(dark ? Color.parseColor("#0B0F19") : Color.parseColor("#E2E8F0"));

        ZoomableImageView pageImage = new ZoomableImageView(this);
        pageImage.setBackgroundColor(Color.WHITE);
        pdfStage.addView(pageImage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout navOverlay = new LinearLayout(this);
        navOverlay.setOrientation(LinearLayout.HORIZONTAL);
        navOverlay.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        navOverlay.setGravity(Gravity.CENTER_VERTICAL);
        navOverlay.setPadding(dp(10), dp(4), dp(10), dp(4));
        navOverlay.setBackground(ThemeHelper.createGlassCardBackground(this, theme, 999f, true, true));
        navOverlay.setElevation(dp(8));

        ImageButton btnPrev = createCleanBorderlessIconButton(R.drawable.ic_chevron_right, dp(42), textPrimary);
        btnPrev.setContentDescription("עמוד קודם");

        TextView tvPageIndicator = new TextView(this);
        tvPageIndicator.setTextColor(textPrimary);
        tvPageIndicator.setTextSize(13.5f);
        tvPageIndicator.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tvPageIndicator.setTextDirection(View.TEXT_DIRECTION_LTR);
        tvPageIndicator.setGravity(Gravity.CENTER);
        tvPageIndicator.setPadding(dp(14), dp(4), dp(14), dp(4));

        ImageButton btnNext = createCleanBorderlessIconButton(R.drawable.ic_chevron_left, dp(42), accent);
        btnNext.setContentDescription("עמוד הבא");

        navOverlay.addView(btnPrev);
        navOverlay.addView(tvPageIndicator);
        navOverlay.addView(btnNext);

        FrameLayout.LayoutParams navLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        navLp.bottomMargin = dp(14);
        pdfStage.addView(navOverlay, navLp);

        host.addView(pdfStage, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        try {
            pdfDescriptor = ParcelFileDescriptor.open(targetFile, ParcelFileDescriptor.MODE_READ_ONLY);
            pdfRenderer = new PdfRenderer(pdfDescriptor);
            final int totalPages = pdfRenderer.getPageCount();

            Runnable renderPage = () -> {
                if (pdfRenderer == null || totalPages == 0) return;
                try (PdfRenderer.Page page = pdfRenderer.openPage(currentPdfPage)) {
                    float scale = Math.max(2.5f, Math.min(4.0f, 2200f / Math.max(1, page.getWidth())));
                    int w = Math.max(1, Math.round(page.getWidth() * scale));
                    int h = Math.max(1, Math.round(page.getHeight() * scale));
                    Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    bmp.eraseColor(Color.WHITE);
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    pageImage.setImageBitmap(bmp);
                    tvPageIndicator.setText((currentPdfPage + 1) + " / " + totalPages);
                    boolean hasPrev = currentPdfPage > 0;
                    boolean hasNext = currentPdfPage < totalPages - 1;
                    btnPrev.setEnabled(hasPrev);
                    btnPrev.setAlpha(hasPrev ? 1f : 0.35f);
                    btnNext.setEnabled(hasNext);
                    btnNext.setAlpha(hasNext ? 1f : 0.35f);
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
            pageImage.setOnHorizontalSwipeListener(new ZoomableImageView.HorizontalSwipeListener() {
                @Override
                public void onSwipeToNext() {
                    if (currentPdfPage < totalPages - 1) {
                        currentPdfPage++;
                        renderPage.run();
                    }
                }

                @Override
                public void onSwipeToPrevious() {
                    if (currentPdfPage > 0) {
                        currentPdfPage--;
                        renderPage.run();
                    }
                }
            });
        } catch (Exception e) {
            tvPageIndicator.setText("לא ניתן להציג PDF: " + e.getMessage());
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

        final TextView tvBreadcrumb = new TextView(this);
        tvBreadcrumb.setTextColor(accent);
        tvBreadcrumb.setTextSize(12.5f);
        tvBreadcrumb.setTypeface(Typeface.DEFAULT_BOLD);
        tvBreadcrumb.setPadding(0, 0, 0, dp(8));
        tvBreadcrumb.setTextDirection(View.TEXT_DIRECTION_LTR);
        card.addView(tvBreadcrumb);

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

        card.addView(bottomActions);
        host.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final String[] currentArchiveFolder = new String[]{""};
        final List<ArchiveUtils.ArchiveEntryInfo> allEntries = new ArrayList<>();
        final boolean[] needsPwdState = new boolean[]{false};
        final boolean isZip = MediaFormatHelper.isZipContainer(targetFile.getName());
        final Runnable[] renderFolder = new Runnable[1];
        final Runnable[] reloadArchive = new Runnable[1];

        archiveNavigateUpHandler = () -> {
            if (!currentArchiveFolder[0].isEmpty()) {
                String trimmed = currentArchiveFolder[0].endsWith("/")
                        ? currentArchiveFolder[0].substring(0, currentArchiveFolder[0].length() - 1)
                        : currentArchiveFolder[0];
                int lastSlash = trimmed.lastIndexOf('/');
                currentArchiveFolder[0] = lastSlash >= 0 ? trimmed.substring(0, lastSlash + 1) : "";
                renderFolder[0].run();
            } else {
                archiveNavigateUpHandler = null;
                proceedCloseOrNavigateBack();
            }
        };

        renderFolder[0] = () -> {
            entriesList.removeAllViews();
            String folderPath = currentArchiveFolder[0];
            tvBreadcrumb.setText(folderPath.isEmpty() ? "/ (שורש הארכיון)" : ("/" + folderPath));
            List<ArchiveUtils.ArchiveTreeNode> nodes = ArchiveUtils.listTreeChildren(allEntries, folderPath);
            tvSub.setText("סה״כ " + allEntries.size() + " פריטים בארכיון • "
                    + nodes.size() + " בתיקייה זו • " + StorageUtils.formatSize(targetFile.length())
                    + (needsPwdState[0] ? " • מוגן בסיסמה" : ""));

            if (!folderPath.isEmpty()) {
                LinearLayout upRow = new LinearLayout(this);
                upRow.setOrientation(LinearLayout.HORIZONTAL);
                upRow.setGravity(Gravity.CENTER_VERTICAL);
                upRow.setPadding(dp(10), dp(9), dp(10), dp(9));
                GradientDrawable upBg = new GradientDrawable();
                upBg.setCornerRadius(dp(10));
                upBg.setColor(ThemeHelper.getBgColor(this, theme));
                upRow.setBackground(upBg);
                LinearLayout.LayoutParams upLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                upLp.bottomMargin = dp(6);
                upRow.setLayoutParams(upLp);

                ImageView ivUp = new ImageView(this);
                ivUp.setImageResource(R.drawable.ic_arrow_back);
                ivUp.setColorFilter(accent);
                upRow.addView(ivUp, new LinearLayout.LayoutParams(dp(20), dp(20)));

                TextView tvUp = new TextView(this);
                tvUp.setText(".. (חזרה לתיקייה הקודמת)");
                tvUp.setTextColor(accent);
                tvUp.setTextSize(13f);
                tvUp.setTypeface(Typeface.DEFAULT_BOLD);
                LinearLayout.LayoutParams tvUpLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                tvUpLp.setMarginStart(dp(8));
                upRow.addView(tvUp, tvUpLp);

                upRow.setOnClickListener(v -> archiveNavigateUpHandler.run());
                entriesList.addView(upRow);
            }

            for (ArchiveUtils.ArchiveTreeNode node : nodes) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(10), dp(9), dp(10), dp(9));

                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(dp(10));
                bg.setColor(ThemeHelper.getBgColor(this, theme));
                row.setBackground(bg);

                LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rLp.bottomMargin = dp(5);
                row.setLayoutParams(rLp);

                ImageView ivIcon = new ImageView(this);
                if (node.directory) {
                    ivIcon.setImageResource(R.drawable.ic_folder);
                    ivIcon.setColorFilter(accent);
                } else {
                    ivIcon.setImageResource(FileThumbnailLoader.resolveFallbackDrawable(node.displayName.toLowerCase(Locale.ROOT)));
                    ivIcon.clearColorFilter();
                }
                row.addView(ivIcon, new LinearLayout.LayoutParams(dp(22), dp(22)));

                TextView tvName = new TextView(this);
                tvName.setText(node.displayName);
                tvName.setTextColor(textPrimary);
                tvName.setTextSize(13.5f);
                tvName.setTypeface(node.directory ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                tvName.setMaxLines(1);
                tvName.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                LinearLayout.LayoutParams nLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                nLp.setMarginStart(dp(8));
                nLp.setMarginEnd(dp(8));
                row.addView(tvName, nLp);

                if (node.encrypted) {
                    ImageView ivLock = new ImageView(this);
                    ivLock.setImageResource(R.drawable.ic_lock);
                    ivLock.setColorFilter(textSecondary);
                    LinearLayout.LayoutParams lkLp = new LinearLayout.LayoutParams(dp(15), dp(15));
                    lkLp.setMarginEnd(dp(6));
                    row.addView(ivLock, lkLp);
                }

                if (node.directory) {
                    TextView tvDirHint = new TextView(this);
                    tvDirHint.setText("תיקייה");
                    tvDirHint.setTextColor(textSecondary);
                    tvDirHint.setTextSize(11f);
                    row.addView(tvDirHint);
                    row.setOnClickListener(v -> {
                        currentArchiveFolder[0] = node.fullPath;
                        scroll.scrollTo(0, 0);
                        renderFolder[0].run();
                    });
                } else {
                    if (node.size >= 0) {
                        TextView tvSize = new TextView(this);
                        tvSize.setText(StorageUtils.formatSize(node.size));
                        tvSize.setTextColor(textSecondary);
                        tvSize.setTextSize(11f);
                        row.addView(tvSize);
                    }
                    final ArchiveUtils.ArchiveEntryInfo entryRef = node.entryInfo != null
                            ? node.entryInfo
                            : new ArchiveUtils.ArchiveEntryInfo(node.fullPath, node.size, node.compressedSize, false, node.encrypted);
                    row.setOnClickListener(v -> openArchiveEntry(entryRef, () -> openArchiveEntry(entryRef, null)));
                    if (isZip && !node.encrypted) {
                        row.setOnLongClickListener(v -> {
                            ThemedDialogHelper.showCustomDialog(this, "מחיקה מהארכיון",
                                    "האם למחוק את \"" + node.displayName + "\" מתוך ה-ZIP?", null,
                                    "מחק", () -> executor.execute(() -> {
                                        try {
                                            ArchiveUtils.deleteZipEntry(targetFile, entryRef.name);
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
        };

        reloadArchive[0] = () -> executor.execute(() -> {
            try {
                List<ArchiveUtils.ArchiveEntryInfo> fetched = ArchiveUtils.listArchiveEntries(targetFile);
                boolean needsPwd = ArchiveUtils.archiveRequiresPassword(targetFile);
                uiHandler.post(() -> {
                    allEntries.clear();
                    allEntries.addAll(fetched);
                    needsPwdState[0] = needsPwd;
                    renderFolder[0].run();
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
                String msg = e.getMessage() != null ? e.getMessage() : "";
                uiHandler.post(() -> {
                    btnExtract.setEnabled(true);
                    if (msg.contains("סיסמ")) {
                        archivePassword = "";
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                        promptArchivePassword(() -> runFullExtraction(btnExtract));
                    } else {
                        Toast.makeText(this, "שגיאה בחילוץ: " + msg, Toast.LENGTH_LONG).show();
                    }
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
                String msg = e.getMessage() != null ? e.getMessage() : "";
                uiHandler.post(() -> {
                    if (msg.contains("סיסמ")) {
                        archivePassword = "";
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                        promptArchivePassword(() -> openArchiveEntry(item, retryAfterPwd));
                    } else {
                        Toast.makeText(this, "שגיאה בפתיחת פריט: " + msg, Toast.LENGTH_LONG).show();
                    }
                });
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
        uiHandler.removeCallbacks(mtsBufferingWatchdog);
        detachAudioCallbacksOnly();
        videoTickerActive = false;
        releaseVideoPlayer();
        cleanupActiveRemuxedCache();
        cleanupExternalCopiedCache();

        if (pdfRenderer != null) {
            try { pdfRenderer.close(); } catch (Exception ignored) {}
        }
        if (pdfDescriptor != null) {
            try { pdfDescriptor.close(); } catch (Exception ignored) {}
        }
        photoCache.evictAll();
        executor.shutdownNow();
    }
}
