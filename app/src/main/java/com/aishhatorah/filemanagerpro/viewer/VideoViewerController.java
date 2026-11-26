package com.aishhatorah.filemanagerpro.viewer;

import android.app.NotificationManager;
import android.app.PictureInPictureParams;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Rational;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
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

import com.aishhatorah.filemanagerpro.AudioPlayerManager;
import com.aishhatorah.filemanagerpro.GifToolsHelper;
import com.aishhatorah.filemanagerpro.GlassSideDrawerHelper;
import com.aishhatorah.filemanagerpro.MediaFormatHelper;
import com.aishhatorah.filemanagerpro.MpegTsNormalizer;
import com.aishhatorah.filemanagerpro.NormalizedTsExoDataSource;
import com.aishhatorah.filemanagerpro.PrefsManager;
import com.aishhatorah.filemanagerpro.ProVideoEditorActivity;
import com.aishhatorah.filemanagerpro.R;
import com.aishhatorah.filemanagerpro.SeekableTsExtractor;
import com.aishhatorah.filemanagerpro.StorageUtils;
import com.aishhatorah.filemanagerpro.ThemeHelper;
import com.aishhatorah.filemanagerpro.ThemedDialogHelper;
import com.aishhatorah.filemanagerpro.TsToMp4Remuxer;
import com.aishhatorah.filemanagerpro.VideoCutterActivity;
import com.aishhatorah.filemanagerpro.WavySeekBar;

import java.io.Closeable;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * בקר נגן וידאו Edge-to-Edge מובנה עם תמיכה ב-ExoPlayer, MTS/TS Remux, מחוות זום/ווליום/בהירות/האצה,
 * ו-Picture-in-Picture, עם סרגלים צפים מעוגלים ב-4 הפינות.
 */
public class VideoViewerController {

    private final AppCompatActivity activity;
    private final ViewerHost host;

    private boolean awaitingInitialVideoPlay = true;
    private boolean suppressNextVideoAutoPlay = true;
    private TextView tvVideoCenterScrubTime = null;
    private ImageView ivVideoPreviewThumb = null;
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
    private int currentVideoRotationDegrees = 0;
    private int knownVideoDurationMs = 0;
    private boolean isUserSeekingVideo = false;
    private long lastLiveSeekUiMs = 0L;
    private long holdSeekUiUntilMs = 0L;
    private int pendingSeekTargetMs = -1;

    private boolean exoAudioDisabledRetry = false;
    private boolean exoForcedTsRetry = false;
    private boolean exoRemuxFallbackAttempted = false;
    private boolean isRemuxingMtsPlayback = false;
    private int mtsRemuxGeneration = 0;
    private int lastObservedVideoPosMs = 0;
    private int unexpectedPosResetCount = 0;
    private long firstPosResetObservedMs = 0L;
    private File activeRemuxedCacheFile = null;
    private boolean videoUsedFdFallback = false;
    private boolean speedModifiedFromDefault = false;
    private LinearLayout videoFallbackBar = null;
    private LinearLayout videoRemuxOverlay = null;
    private ImageButton btnCenterPlayPauseRef = null;

    private float videoSpeed = 1.0f;
    private int videoRepeatMode = AudioPlayerManager.REPEAT_OFF;
    private boolean videoShuffleMode = false;
    private boolean videoMuted = false;
    private boolean videoMutedByDeviceSilence = false;
    private BroadcastReceiver videoSilenceReceiver = null;
    private boolean videoAutoNext = true;
    private int videoScaleMode = 0; // 0=FIT, 1=FILL_CROP, 2=STRETCH
    private float videoScale = 1.0f;
    private float videoTransX = 0f;
    private float videoTransY = 0f;
    private final float[] lastAppliedMatrixValues = new float[9];
    private boolean hasAppliedVideoMatrix = false;
    private boolean isLandscapeLocked = false;
    private Runnable activeVideoSyncRunnable = null;

    private boolean isHold2xForwardActive = false;
    private boolean isHold2xRewindActive = false;
    private float speedBeforeHold = 1.0f;
    private Runnable rewindPulseRunnable = null;

    private final Runnable mtsBufferingWatchdog;

    public VideoViewerController(ViewerHost host) {
        this.host = host;
        this.activity = host.getActivity();
        this.mtsBufferingWatchdog = () -> {
            if (activity.isFinishing() || activity.isDestroyed() || exoPlayer == null) return;
            File tf = host.getTargetFile();
            if (!exoRemuxFallbackAttempted && !isRemuxingMtsPlayback && !awaitingInitialVideoPlay
                    && tf != null
                    && (MpegTsNormalizer.isLikelyMpegTsName(tf.getName())
                    || MpegTsNormalizer.detectTsLayoutCached(tf).packetStride > 0)
                    && exoPlayer.getPlaybackState() == Player.STATE_BUFFERING
                    && exoPlayer.getPlayWhenReady()) {
                startRemuxedMtsPlayback(tf, activeVideoSyncRunnable);
            }
        };
    }

    public boolean isAwaitingInitialVideoPlay() {
        return awaitingInitialVideoPlay;
    }

    public void render(int theme, LinearLayout topBar) {
        ViewerUiHelper.applyMediaTransparentSystemBars(activity);
        File targetFile = host.getTargetFile();
        if (targetFile == null) return;

        awaitingInitialVideoPlay = true;
        suppressNextVideoAutoPlay = true;
        hasAppliedVideoMatrix = false;
        host.setVideoLocked(false);
        host.setInPipMode(false);
        host.setMediaControlsHidden(true);

        int accent = ThemeHelper.getAccentColor(activity, theme);

        FrameLayout stageRoot = new FrameLayout(activity);
        stageRoot.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        stageRoot.setBackgroundColor(Color.BLACK);

        buildVideoFolderPlaylist(targetFile);

        final FrameLayout videoViewport = new FrameLayout(activity);
        videoViewport.setBackgroundColor(Color.BLACK);
        videoViewport.setClipChildren(true);

        videoTextureView = new TextureView(activity);
        videoTextureView.setOpaque(true);
        videoTextureView.setAlpha(0f);
        videoViewport.addView(videoTextureView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));

        ivVideoPreviewThumb = new ImageView(activity);
        ivVideoPreviewThumb.setScaleType(ImageView.ScaleType.MATRIX);
        ivVideoPreviewThumb.setBackgroundColor(Color.BLACK);
        ivVideoPreviewThumb.setVisibility(View.VISIBLE);
        ivVideoPreviewThumb.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if ((right - left) != (oldRight - oldLeft) || (bottom - top) != (oldBottom - oldTop)) {
                updatePreviewThumbTransform();
            }
        });
        videoViewport.addView(ivVideoPreviewThumb, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));

        videoTextureView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if ((right - left) != (oldRight - oldLeft) || (bottom - top) != (oldBottom - oldTop)) {
                hasAppliedVideoMatrix = false;
                updateVideoAspectRatioMatrix();
            }
        });

        final TextView tvVideoDoubleTapBadge = ViewerUiHelper.createSeekOverlayBadge(activity);
        videoViewport.addView(tvVideoDoubleTapBadge);

        tvVideoCenterScrubTime = ViewerUiHelper.createCleanCenterTimeOverlay(activity);
        videoViewport.addView(tvVideoCenterScrubTime);

        setupVideoAdvancedTouchGestures(videoViewport, tvVideoDoubleTapBadge, accent);

        stageRoot.addView(videoViewport, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final ImageButton btnCenterPlayPause = ViewerUiHelper.createResponsiveRoundIconButton(
                activity, R.drawable.ic_play, dp(62), 0, true, accent);
        btnCenterPlayPause.setColorFilter(ThemeHelper.getOnAccentTextColor(accent));
        btnCenterPlayPause.setContentDescription("נגן / השהה");
        btnCenterPlayPauseRef = btnCenterPlayPause;

        FrameLayout.LayoutParams centerLp = new FrameLayout.LayoutParams(
                dp(62), dp(62), Gravity.CENTER);
        stageRoot.addView(btnCenterPlayPause, centerLp);

        final ImageButton btnFloatingUnlock = ViewerUiHelper.createCleanBorderlessIconButton(activity, R.drawable.ic_lock, dp(48), Color.WHITE);
        btnFloatingUnlock.setContentDescription("שחרור נעילת מסך");
        GradientDrawable lockCircleBg = new GradientDrawable();
        lockCircleBg.setShape(GradientDrawable.OVAL);
        lockCircleBg.setColor(Color.argb(145, 10, 15, 28));
        btnFloatingUnlock.setBackground(lockCircleBg);
        btnFloatingUnlock.setVisibility(View.GONE);
        FrameLayout.LayoutParams unlockLp = new FrameLayout.LayoutParams(
                dp(48), dp(48), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        unlockLp.bottomMargin = dp(24);
        stageRoot.addView(btnFloatingUnlock, unlockLp);

        videoFallbackBar = new LinearLayout(activity);
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

        TextView tvFallbackMsg = new TextView(activity);
        tvFallbackMsg.setText("פורמט וידאו זה דורש מפענח מערכת חיצוני");
        tvFallbackMsg.setTextColor(Color.WHITE);
        tvFallbackMsg.setTextSize(14f);
        tvFallbackMsg.setTypeface(Typeface.DEFAULT_BOLD);
        tvFallbackMsg.setGravity(Gravity.CENTER);
        tvFallbackMsg.setPadding(0, 0, 0, dp(10));
        videoFallbackBar.addView(tvFallbackMsg);

        Button btnOpenExternalVideo = ThemedDialogHelper.createThemedButton(activity, "נגן בנגן מערכת / חיצוני", theme, true, false);
        btnOpenExternalVideo.setOnClickListener(v -> {
            File tf = host.getTargetFile();
            if (tf != null) com.aishhatorah.filemanagerpro.FileOpenWithHelper.openWithSystemChooser(activity, tf);
        });
        videoFallbackBar.addView(btnOpenExternalVideo);

        FrameLayout.LayoutParams fbLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        fbLp.setMargins(dp(24), 0, dp(24), 0);
        stageRoot.addView(videoFallbackBar, fbLp);

        // חלונית טעינה גלויה בזמן הכנת זרם MTS חלק (מונע לחיצות כפולות ואיפוסים)
        videoRemuxOverlay = new LinearLayout(activity);
        videoRemuxOverlay.setOrientation(LinearLayout.VERTICAL);
        videoRemuxOverlay.setGravity(Gravity.CENTER);
        videoRemuxOverlay.setClickable(true);
        videoRemuxOverlay.setPadding(dp(20), dp(16), dp(20), dp(16));
        GradientDrawable remuxBg = new GradientDrawable();
        remuxBg.setCornerRadius(dp(18));
        remuxBg.setColor(Color.argb(215, 15, 23, 42));
        remuxBg.setStroke(dp(1), Color.argb(80, 255, 255, 255));
        videoRemuxOverlay.setBackground(remuxBg);
        videoRemuxOverlay.setVisibility(View.GONE);

        ProgressBar remuxProgress = new ProgressBar(activity);
        remuxProgress.setIndeterminate(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            remuxProgress.setIndeterminateTintList(ColorStateList.valueOf(accent));
        }
        LinearLayout.LayoutParams rpLp = new LinearLayout.LayoutParams(dp(36), dp(36));
        rpLp.bottomMargin = dp(10);
        videoRemuxOverlay.addView(remuxProgress, rpLp);

        TextView tvRemuxMsg = new TextView(activity);
        tvRemuxMsg.setText("מכין זרם MTS לצפייה חלקה...");
        tvRemuxMsg.setTextColor(Color.WHITE);
        tvRemuxMsg.setTextSize(14f);
        tvRemuxMsg.setTypeface(Typeface.DEFAULT_BOLD);
        tvRemuxMsg.setGravity(Gravity.CENTER);
        videoRemuxOverlay.addView(tvRemuxMsg);

        FrameLayout.LayoutParams remuxLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        remuxLp.setMargins(dp(24), 0, dp(24), 0);
        stageRoot.addView(videoRemuxOverlay, remuxLp);

        LinearLayout bottomCard = new LinearLayout(activity);
        bottomCard.setOrientation(LinearLayout.VERTICAL);
        bottomCard.setClickable(true);
        bottomCard.setPadding(dp(14), dp(8), dp(14), dp(10));
        bottomCard.setBackground(ViewerUiHelper.createFloatingGlassBarBackground(
                activity, 22, Color.argb(182, 10, 15, 28), Color.argb(60, 255, 255, 255)));

        registerVideoSilenceMonitor();
        evaluateVideoDeviceSilence(false);

        LinearLayout lockTopRow = new LinearLayout(activity);
        lockTopRow.setOrientation(LinearLayout.HORIZONTAL);
        lockTopRow.setGravity(Gravity.CENTER);
        final ImageButton btnVidLock = ViewerUiHelper.createCleanBorderlessIconButton(activity, R.drawable.ic_lock_open, dp(34), Color.WHITE);
        btnVidLock.setPadding(dp(6), dp(5), dp(6), dp(5));
        btnVidLock.setContentDescription("נעילת מסך הוידאו");
        lockTopRow.addView(btnVidLock, new LinearLayout.LayoutParams(dp(42), dp(32)));
        bottomCard.addView(lockTopRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout timeBar = new LinearLayout(activity);
        timeBar.setOrientation(LinearLayout.HORIZONTAL);
        timeBar.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        timeBar.setGravity(Gravity.CENTER_VERTICAL);
        timeBar.setPadding(dp(2), dp(2), dp(2), dp(8));

        final TextView tvVidPos = new TextView(activity);
        tvVidPos.setText("00:00");
        tvVidPos.setTextColor(Color.WHITE);
        tvVidPos.setTextSize(12f);
        tvVidPos.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        timeBar.addView(tvVidPos);

        final WavySeekBar videoSeek = new WavySeekBar(activity);
        videoSeek.setShowTickDots(false);
        videoSeek.setColors(accent, Color.argb(95, 255, 255, 255), Color.WHITE);
        timeBar.addView(videoSeek, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final TextView tvVidDur = new TextView(activity);
        tvVidDur.setText("00:00");
        tvVidDur.setTextColor(Color.parseColor("#CBD5E1"));
        tvVidDur.setTextSize(12f);
        tvVidDur.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        timeBar.addView(tvVidDur);

        bottomCard.addView(timeBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout row1 = new LinearLayout(activity);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row1.setGravity(Gravity.CENTER_VERTICAL);

        final ImageButton btnVidRotate = ViewerUiHelper.createCleanBorderlessIconButton(activity, R.drawable.ic_screen_rotate, dp(38), Color.WHITE);
        btnVidRotate.setContentDescription("סיבוב מסך");
        final ImageButton btnVidNext = ViewerUiHelper.createCleanBorderlessIconButton(activity, R.drawable.ic_skip_next, dp(38), Color.WHITE);
        btnVidNext.setContentDescription("וידאו הבא");
        final ImageButton btnVidPlayPause = ViewerUiHelper.createCleanBorderlessIconButton(activity, R.drawable.ic_pause, dp(40), Color.WHITE);
        btnVidPlayPause.setContentDescription("נגן / השהה");
        final ImageButton btnVidPrev = ViewerUiHelper.createCleanBorderlessIconButton(activity, R.drawable.ic_skip_prev, dp(38), Color.WHITE);
        btnVidPrev.setContentDescription("וידאו קודם");
        final ImageButton btnVidShuffle = ViewerUiHelper.createCleanBorderlessIconButton(activity, R.drawable.ic_shuffle, dp(38), Color.WHITE);
        btnVidShuffle.setContentDescription("השמעה אקראית");

        row1.addView(btnVidRotate, new LinearLayout.LayoutParams(0, dp(38), 1f));
        row1.addView(btnVidNext, new LinearLayout.LayoutParams(0, dp(38), 1f));
        row1.addView(btnVidPlayPause, new LinearLayout.LayoutParams(0, dp(40), 1f));
        row1.addView(btnVidPrev, new LinearLayout.LayoutParams(0, dp(38), 1f));
        row1.addView(btnVidShuffle, new LinearLayout.LayoutParams(0, dp(38), 1f));
        bottomCard.addView(row1, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout row2 = new LinearLayout(activity);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row2.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams row2Lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        row2Lp.topMargin = dp(2);

        final ImageButton btnVidStretch = ViewerUiHelper.createCleanBorderlessIconButton(activity, R.drawable.ic_aspect_fit, dp(36), Color.WHITE);
        btnVidStretch.setContentDescription("מתיחת הוידאו והתאמה למסך");
        final ImageButton btnVidFwd10 = ViewerUiHelper.createCleanBorderlessIconButton(activity, R.drawable.ic_forward_10, dp(36), Color.WHITE);
        btnVidFwd10.setContentDescription("10 שניות קדימה");
        final ImageButton btnVidSpeed = ViewerUiHelper.createCleanBorderlessIconButton(activity, R.drawable.ic_speed, dp(36), Color.WHITE);
        btnVidSpeed.setContentDescription("מהירות ניגון");
        final ImageButton btnVidBack10 = ViewerUiHelper.createCleanBorderlessIconButton(activity, R.drawable.ic_replay_10, dp(36), Color.WHITE);
        btnVidBack10.setContentDescription("10 שניות אחורה");
        final ImageButton btnVidRepeat = ViewerUiHelper.createCleanBorderlessIconButton(activity, R.drawable.ic_repeat_off, dp(36), Color.WHITE);
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

        final int statusFloor = ViewerUiHelper.resolveStatusBarHeightFallback(activity);
        host.setLastVideoTopInsetPx(statusFloor);
        if (topBar != null) {
            topBar.setBackground(ViewerUiHelper.createFloatingGlassBarBackground(
                    activity, 22, Color.argb(178, 10, 15, 28), Color.argb(55, 255, 255, 255)));
            topBar.setElevation(dp(8));
            FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
            topLp.setMargins(dp(10), statusFloor + dp(8), dp(10), 0);
            stageRoot.addView(topBar, topLp);
        }

        ViewCompat.setOnApplyWindowInsetsListener(stageRoot, (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            int effectiveTop = Math.max(sys.top, statusFloor);
            host.setLastVideoTopInsetPx(effectiveTop);
            if (topBar != null) {
                ViewGroup.MarginLayoutParams tLp = (ViewGroup.MarginLayoutParams) topBar.getLayoutParams();
                if (tLp != null) {
                    tLp.leftMargin = sys.left + dp(10);
                    tLp.rightMargin = sys.right + dp(10);
                    tLp.topMargin = effectiveTop + dp(8);
                    topBar.setLayoutParams(tLp);
                }
            }
            ViewGroup.MarginLayoutParams bLp = (ViewGroup.MarginLayoutParams) bottomCard.getLayoutParams();
            if (bLp != null) {
                bLp.leftMargin = sys.left + dp(10);
                bLp.rightMargin = sys.right + dp(10);
                bLp.bottomMargin = sys.bottom + dp(10);
                bottomCard.setLayoutParams(bLp);
            }
            ViewGroup.MarginLayoutParams uLp = (ViewGroup.MarginLayoutParams) btnFloatingUnlock.getLayoutParams();
            if (uLp != null) {
                uLp.bottomMargin = sys.bottom + dp(24);
                btnFloatingUnlock.setLayoutParams(uLp);
            }
            return insets;
        });

        host.registerMediaChrome(false, true, false, stageRoot, videoViewport, topBar, bottomCard,
                null, null, btnCenterPlayPause, btnFloatingUnlock, null);

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
            int repeatRes = ViewerUiHelper.resolveRepeatIcon(videoRepeatMode);
            if (lastSyncedRepeatRes[0] != repeatRes) {
                lastSyncedRepeatRes[0] = repeatRes;
                btnVidRepeat.setImageResource(repeatRes);
            }
            btnVidRepeat.setColorFilter(videoRepeatMode != AudioPlayerManager.REPEAT_OFF ? accent : Color.WHITE);
            btnVidShuffle.setColorFilter(videoShuffleMode ? accent : Color.WHITE);
            btnVidStretch.setColorFilter(videoScaleMode != 0 ? accent : Color.WHITE);
            btnVidSpeed.setColorFilter(Math.abs(videoSpeed - 1.0f) >= 0.02f ? accent : Color.WHITE);

            TextView tvCounter = host.getFolderTrackCounterView();
            if (tvCounter != null && !videoPlaylist.isEmpty()) {
                String cntStr = (currentVideoIdx + 1) + "/" + videoPlaylist.size();
                if (!cntStr.equals(lastCounterStr[0])) {
                    lastCounterStr[0] = cntStr;
                    tvCounter.setVisibility(View.VISIBLE);
                    tvCounter.setText(cntStr);
                }
            }
        };
        activeVideoSyncRunnable = syncVidButtons;

        View.OnClickListener playPauseClick = v -> {
            if (isRemuxingMtsPlayback) return;
            boolean wasAwaiting = awaitingInitialVideoPlay;
            awaitingInitialVideoPlay = false;
            suppressNextVideoAutoPlay = false;
            if (exoPlayer == null && videoPlayer == null) {
                loadVideoTrackAtIndex(currentVideoIdx, syncVidButtons);
                host.applyMediaImmersiveAndLayoutState();
                return;
            }
            if (!videoPrepared) {
                if (exoPlayer != null) {
                    exoPlayer.setPlayWhenReady(true);
                    exoPlayer.play();
                    File curTf = host.getTargetFile();
                    if (curTf != null
                            && (MpegTsNormalizer.isLikelyMpegTsName(curTf.getName())
                            || MpegTsNormalizer.detectTsLayoutCached(curTf).packetStride > 0)
                            && !exoRemuxFallbackAttempted
                            && !isRemuxingMtsPlayback
                            && exoPlayer.getPlaybackState() == Player.STATE_BUFFERING) {
                        host.getUiHandler().removeCallbacks(mtsBufferingWatchdog);
                        host.getUiHandler().postDelayed(mtsBufferingWatchdog, 9500L);
                    }
                }
                syncVidButtons.run();
                if (wasAwaiting) {
                    host.applyMediaImmersiveAndLayoutState();
                }
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
                host.applyMediaImmersiveAndLayoutState();
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
            Toast.makeText(activity,
                    videoShuffleMode ? "השמעה אקראית הופעלה" : "השמעה אקראית כובתה",
                    Toast.LENGTH_SHORT).show();
        });

        btnVidStretch.setOnClickListener(v -> {
            videoScaleMode = (videoScaleMode + 1) % 3;
            resetVideoZoom();
            String modeMsg = videoScaleMode == 1 ? "מילוי מסך"
                    : (videoScaleMode == 2 ? "מתיחה למסך מלא" : "התאמה למסך");
            Toast.makeText(activity, modeMsg, Toast.LENGTH_SHORT).show();
            syncVidButtons.run();
        });

        btnVidRepeat.setOnClickListener(v -> {
            videoRepeatMode = (videoRepeatMode + 1) % 3;
            syncVidButtons.run();
        });
        btnVidRotate.setOnClickListener(v -> toggleScreenOrientation());
        btnVidSpeed.setOnClickListener(v -> ViewerUiHelper.showSpeedPickerDialog(
                activity, v, "בחירת מהירות הסרטון", videoSpeed, chosen -> {
                    videoSpeed = chosen;
                    speedModifiedFromDefault = true;
                    applyVideoSpeedInternal(isVideoPlayingSafely());
                    syncVidButtons.run();
                }));

        btnVidLock.setOnClickListener(v -> {
            stopVideoHoldGestures(tvVideoDoubleTapBadge);
            host.setVideoLocked(true);
            host.setMediaControlsHidden(true);
            host.applyMediaImmersiveAndLayoutState();
            Toast.makeText(activity, "מסך הוידאו ננעל", Toast.LENGTH_SHORT).show();
        });

        btnFloatingUnlock.setOnClickListener(v -> {
            host.setVideoLocked(false);
            host.setMediaControlsHidden(false);
            host.applyMediaImmersiveAndLayoutState();
        });

        videoSeek.setOnSeekBarChangeListener(new WavySeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(WavySeekBar sb, int progress, boolean fromUser) {
                if (fromUser && videoPrepared) {
                    int clamped = Math.max(0, progress);
                    tvVidPos.setText(ViewerUiHelper.formatClock(clamped));
                    long now = System.currentTimeMillis();
                    File tf = host.getTargetFile();
                    boolean isTs = tf != null && MpegTsNormalizer.isLikelyMpegTsName(tf.getName());
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
                    File curTf = host.getTargetFile();
                    boolean isTs = curTf != null && (MpegTsNormalizer.isLikelyMpegTsName(curTf.getName())
                            || MpegTsNormalizer.detectTsLayoutCached(curTf).packetStride > 0);
                    pendingSeekTargetMs = target;
                    holdSeekUiUntilMs = System.currentTimeMillis() + (isTs ? 2000L : 900L);
                    tvVidPos.setText(ViewerUiHelper.formatClock(target));
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

            @Override
            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
            }
        });

        final String[] lastTickerPos = new String[]{null};
        final String[] lastTickerDur = new String[]{null};
        final int[] lastTickerMax = new int[]{-1};
        final boolean[] lastTickerSeekEnabled = new boolean[]{true};

        videoTickerActive = true;
        host.getUiHandler().post(new Runnable() {
            @Override
            public void run() {
                if (!videoTickerActive) return;
                if (videoPrepared && (exoPlayer != null || videoPlayer != null)) {
                    try {
                        videoSeek.setPlaying(isVideoPlayingSafely());
                        int dur = getEffectiveVideoDurationMs();
                        int pos = getCurrentVideoPositionMs();
                        long now = System.currentTimeMillis();
                        File curTf = host.getTargetFile();

                        if (!isUserSeekingVideo && now >= holdSeekUiUntilMs && isVideoPlayingSafely()) {
                            if (pos + 700 < lastObservedVideoPosMs
                                    && lastObservedVideoPosMs >= 3500
                                    && lastObservedVideoPosMs < (dur > 4500 ? dur - 1500 : Integer.MAX_VALUE)
                                    && videoRepeatMode == AudioPlayerManager.REPEAT_OFF
                                    && curTf != null
                                    && (MpegTsNormalizer.isLikelyMpegTsName(curTf.getName())
                                    || MpegTsNormalizer.detectTsLayoutCached(curTf).packetStride > 0)
                                    && !exoRemuxFallbackAttempted
                                    && !isRemuxingMtsPlayback) {
                                if (firstPosResetObservedMs == 0L || now - firstPosResetObservedMs > 20_000L) {
                                    unexpectedPosResetCount = 1;
                                    firstPosResetObservedMs = now;
                                } else {
                                    unexpectedPosResetCount++;
                                }
                                if (unexpectedPosResetCount >= 3) {
                                    startRemuxedMtsPlayback(curTf, syncVidButtons);
                                    host.getUiHandler().postDelayed(this, 250);
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
                                String posStr = ViewerUiHelper.formatClock(pos);
                                if (!posStr.equals(lastTickerPos[0])) {
                                    lastTickerPos[0] = posStr;
                                    tvVidPos.setText(posStr);
                                }
                            } else if (!isUserSeekingVideo && pendingSeekTargetMs >= 0) {
                                int clampedPending = Math.min(dur, Math.max(0, pendingSeekTargetMs));
                                videoSeek.setProgress(clampedPending);
                                String posStr = ViewerUiHelper.formatClock(clampedPending);
                                if (!posStr.equals(lastTickerPos[0])) {
                                    lastTickerPos[0] = posStr;
                                    tvVidPos.setText(posStr);
                                }
                            }
                            String durStr = ViewerUiHelper.formatClock(dur);
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
                                String posStr = ViewerUiHelper.formatClock(pos);
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
                host.getUiHandler().postDelayed(this, 250);
            }
        });

        activity.setContentView(stageRoot);
        ViewCompat.requestApplyInsets(stageRoot);
        host.applyMediaImmersiveAndLayoutState();
    }

    public void onConfigurationChanged(boolean landscapeNow) {
        if (landscapeNow || host.isVideoLocked()) {
            host.setMediaControlsHidden(true);
        }
        host.applyMediaImmersiveAndLayoutState();
        hasAppliedVideoMatrix = false;
        if (videoTextureView != null) {
            videoTextureView.post(this::updateVideoAspectRatioMatrix);
        }
    }

    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        host.setInPipMode(isInPictureInPictureMode);
        if (isInPictureInPictureMode) {
            host.setMediaControlsHidden(true);
            resetVideoZoom();
        }
        host.applyMediaImmersiveAndLayoutState();
        hasAppliedVideoMatrix = false;
        if (videoTextureView != null) {
            videoTextureView.post(this::updateVideoAspectRatioMatrix);
        }
    }

    public void showVideoPlusPopup(View anchor, int theme) {
        if (anchor == null || activity.isFinishing() || activity.isDestroyed()) return;
        LinearLayout bar = new LinearLayout(activity);
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

        ImageButton btnPip = ViewerUiHelper.createCleanBorderlessIconButton(activity, R.drawable.ic_pip, dp(40), Color.WHITE);
        btnPip.setContentDescription("תמונה בתוך תמונה");
        btnPip.setOnClickListener(v -> {
            if (holder[0] != null) holder[0].dismiss();
            enterPictureInPictureSafe();
        });
        bar.addView(btnPip);

        ImageButton btnRot = ViewerUiHelper.createCleanBorderlessIconButton(activity, R.drawable.ic_screen_rotate, dp(40), Color.WHITE);
        btnRot.setContentDescription("סיבוב מסך");
        btnRot.setOnClickListener(v -> {
            if (holder[0] != null) holder[0].dismiss();
            toggleScreenOrientation();
        });
        bar.addView(btnRot);

        ImageButton btnCut = ViewerUiHelper.createCleanBorderlessIconButton(activity, R.drawable.ic_video_cutter, dp(40), Color.WHITE);
        btnCut.setContentDescription("חותך וידאו");
        btnCut.setOnClickListener(v -> {
            if (holder[0] != null) holder[0].dismiss();
            File tf = host.getTargetFile();
            if (tf != null) {
                Intent ci = new Intent(activity, VideoCutterActivity.class);
                ci.putExtra(VideoCutterActivity.EXTRA_INITIAL_PATH, tf.getAbsolutePath());
                activity.startActivity(ci);
            }
        });
        bar.addView(btnCut);

        ImageButton btnShare = ViewerUiHelper.createCleanBorderlessIconButton(activity, R.drawable.ic_share, dp(40), Color.WHITE);
        btnShare.setContentDescription("שיתוף");
        btnShare.setOnClickListener(v -> {
            if (holder[0] != null) holder[0].dismiss();
            host.shareCurrentFile();
        });
        bar.addView(btnShare);

        PopupWindow popup = new PopupWindow(
                bar, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        holder[0] = popup;
        popup.setOutsideTouchable(true);
        popup.setElevation(dp(10));
        popup.showAsDropDown(anchor, 0, dp(6), Gravity.END);
    }

    public void showVideoSettingsDialog(View anchor, int theme) {
        File tf = host.getTargetFile();
        if (tf == null) return;
        boolean autoGestures = PrefsManager.isAutoGestureMode(activity);
        List<GlassSideDrawerHelper.DrawerItem> items = new ArrayList<>();
        items.add(GlassSideDrawerHelper.DrawerItem.action(
                "תמונה בתוך תמונה (PiP)", R.drawable.ic_pip, this::enterPictureInPictureSafe));
        items.add(GlassSideDrawerHelper.DrawerItem.action(
                "שמע הסרטון: " + (videoMuted ? "מושתק (לחץ להפעלה)" : (videoMutedByDeviceSilence ? "מושקט (מצב שקט)" : "פעיל (לחץ להשתקה)")),
                R.drawable.ic_audio, () -> {
                    videoMuted = !videoMuted;
                    applyCurrentVideoVolume();
                    Toast.makeText(activity, isEffectiveVideoMuted() ? "שמע הסרטון הושתק" : "שמע הסרטון הופעל", Toast.LENGTH_SHORT).show();
                }));
        items.add(GlassSideDrawerHelper.DrawerItem.action(
                "מעבר אוטומטי לסרטון הבא: " + (videoAutoNext ? "פעיל" : "כבוי"),
                R.drawable.ic_skip_next, () -> {
                    videoAutoNext = !videoAutoNext;
                    Toast.makeText(activity, videoAutoNext ? "מעבר אוטומטי הופעל" : "מעבר אוטומטי כובה", Toast.LENGTH_SHORT).show();
                }));
        items.add(GlassSideDrawerHelper.DrawerItem.action(
                autoGestures ? "כבה מצב מחוות" : "הפעל מצב מחוות",
                R.drawable.ic_settings, () -> {
                    boolean nextMode = !autoGestures;
                    PrefsManager.setAutoGestureMode(activity, nextMode);
                    host.applyMediaImmersiveAndLayoutState();
                    Toast.makeText(activity, nextMode ? "מצב מחוות הופעל" : "מצב מחוות כובה", Toast.LENGTH_SHORT).show();
                }));
        items.add(GlassSideDrawerHelper.DrawerItem.action(
                "ייצוא קטע פתיחה ל-GIF", R.drawable.ic_images, () -> {
                    Toast.makeText(activity, "מייצר GIF מהסרטון...", Toast.LENGTH_SHORT).show();
                    host.getExecutor().execute(() -> {
                        try {
                            File parent = tf.getParentFile() != null ? tf.getParentFile() : activity.getFilesDir();
                            File outGif = StorageUtils.uniqueDestinationFile(parent, tf.getName() + ".gif");
                            GifToolsHelper.videoToGif(activity, tf, outGif, 480, 10);
                            host.getUiHandler().post(() -> Toast.makeText(activity, "נוצר GIF: " + outGif.getName(), Toast.LENGTH_LONG).show());
                        } catch (Exception e) {
                            host.getUiHandler().post(() -> Toast.makeText(activity, "שגיאה: " + e.getMessage(), Toast.LENGTH_LONG).show());
                        }
                    });
                }));
        items.add(GlassSideDrawerHelper.DrawerItem.action(
                "עורך וידאו והפחתת משקל", R.drawable.ic_video_cutter, () -> {
                    Intent vei = new Intent(activity, ProVideoEditorActivity.class);
                    vei.putExtra(ProVideoEditorActivity.EXTRA_VIDEO_PATH, tf.getAbsolutePath());
                    activity.startActivity(vei);
                }));
        items.add(GlassSideDrawerHelper.DrawerItem.action(
                "שיתוף קובץ", R.drawable.ic_share, host::shareCurrentFile));

        GlassSideDrawerHelper.show(activity, anchor, tf.getName(), items, true);
    }

    public void enterPictureInPictureSafe() {
        File tf = host.getTargetFile();
        if (tf == null) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Toast.makeText(activity, "תמונה בתוך תמונה זמינה מאנדרואיד 8 ומעלה", Toast.LENGTH_SHORT).show();
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
            host.setInPipMode(true);
            host.applyMediaImmersiveAndLayoutState();
            int w = currentVideoWidth > 0 ? currentVideoWidth : 16;
            int h = currentVideoHeight > 0 ? currentVideoHeight : 9;
            float ratio = (float) w / (float) h;
            if (ratio < 0.42f) ratio = 0.42f;
            if (ratio > 2.38f) ratio = 2.38f;
            Rational aspect = new Rational(Math.max(100, Math.round(ratio * 1000f)), 1000);
            PictureInPictureParams params = new PictureInPictureParams.Builder()
                    .setAspectRatio(aspect)
                    .build();
            activity.enterPictureInPictureMode(params);
        } catch (Throwable t) {
            host.setInPipMode(false);
            host.applyMediaImmersiveAndLayoutState();
            Toast.makeText(activity, "לא ניתן להפעיל תמונה בתוך תמונה במכשיר זה", Toast.LENGTH_SHORT).show();
        }
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

    private void updatePreviewThumbTransform() {
        if (ivVideoPreviewThumb == null) return;
        Drawable d = ivVideoPreviewThumb.getDrawable();
        if (d == null) return;
        int viewW = ivVideoPreviewThumb.getWidth();
        int viewH = ivVideoPreviewThumb.getHeight();
        int bmpW = d.getIntrinsicWidth();
        int bmpH = d.getIntrinsicHeight();
        if (viewW <= 0 || viewH <= 0 || bmpW <= 0 || bmpH <= 0) return;

        float sar = currentVideoPixelRatio > 0.1f ? currentVideoPixelRatio : 1.0f;
        float dispW = bmpW * sar;
        float dispH = bmpH;

        float scaleX;
        float scaleY;
        if (videoScaleMode == 2) {
            scaleX = (float) viewW / (float) bmpW;
            scaleY = (float) viewH / (float) bmpH;
        } else if (videoScaleMode == 1) {
            float cover = Math.max((float) viewW / dispW, (float) viewH / dispH);
            scaleX = sar * cover;
            scaleY = cover;
        } else {
            float fit = Math.min((float) viewW / dispW, (float) viewH / dispH);
            scaleX = sar * fit;
            scaleY = fit;
        }

        Matrix m = new Matrix();
        m.setScale(scaleX, scaleY);
        float scaledW = bmpW * scaleX;
        float scaledH = bmpH * scaleY;
        m.postTranslate((viewW - scaledW) * 0.5f, (viewH - scaledH) * 0.5f);
        ivVideoPreviewThumb.setScaleType(ImageView.ScaleType.MATRIX);
        ivVideoPreviewThumb.setImageMatrix(m);
    }

    private void updateVideoAspectRatioMatrix() {
        updatePreviewThumbTransform();
        if (videoTextureView == null) return;
        int viewW = videoTextureView.getWidth();
        int viewH = videoTextureView.getHeight();
        if (viewW <= 0 || viewH <= 0 || currentVideoWidth <= 0 || currentVideoHeight <= 0) return;

        Matrix matrix = new Matrix();
        if (currentVideoRotationDegrees == 0) {
            float[] base = computeBaseVideoScale(viewW, viewH);
            clampVideoTranslation();
            matrix.setScale(base[0] * videoScale, base[1] * videoScale, viewW * 0.5f, viewH * 0.5f);
            matrix.postTranslate(videoTransX, videoTransY);
        } else {
            float cx = viewW * 0.5f;
            float cy = viewH * 0.5f;
            boolean swap = currentVideoRotationDegrees == 90 || currentVideoRotationDegrees == 270;
            float rawW = (swap ? currentVideoHeight : currentVideoWidth) * Math.max(0.1f, currentVideoPixelRatio);
            float rawH = swap ? currentVideoWidth : currentVideoHeight;
            float dispW = swap ? rawH : rawW;
            float dispH = swap ? rawW : rawH;
            matrix.setScale(rawW / (float) viewW, rawH / (float) viewH, cx, cy);
            matrix.postRotate(currentVideoRotationDegrees, cx, cy);
            float sx;
            float sy;
            if (videoScaleMode == 2) {
                sx = (float) viewW / dispW;
                sy = (float) viewH / dispH;
            } else if (videoScaleMode == 1) {
                float cover = Math.max((float) viewW / dispW, (float) viewH / dispH);
                sx = cover;
                sy = cover;
            } else {
                float fit = Math.min((float) viewW / dispW, (float) viewH / dispH);
                sx = fit;
                sy = fit;
            }
            clampVideoTranslation();
            matrix.postScale(sx * videoScale, sy * videoScale, cx, cy);
            matrix.postTranslate(videoTransX, videoTransY);
        }

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
        releasePlayer();
        cleanupActiveRemuxedCache();
        File nextFile = videoPlaylist.get(Math.max(0, Math.min(videoPlaylist.size() - 1, idx)));
        host.setTargetFile(nextFile);
        host.updateMarqueeHeaderName(nextFile.getName());
        resetVideoZoom();
        knownVideoDurationMs = 0;
        pendingSeekTargetMs = -1;
        holdSeekUiUntilMs = 0L;
        lastObservedVideoPosMs = 0;
        unexpectedPosResetCount = 0;
        firstPosResetObservedMs = 0L;
        exoAudioDisabledRetry = false;
        exoForcedTsRetry = false;
        exoRemuxFallbackAttempted = false;
        isRemuxingMtsPlayback = false;
        mtsRemuxGeneration++;
        speedModifiedFromDefault = false;
        videoUsedFdFallback = false;
        if (videoFallbackBar != null) videoFallbackBar.setVisibility(View.GONE);
        if (videoRemuxOverlay != null) videoRemuxOverlay.setVisibility(View.GONE);
        if (videoTextureView != null) {
            videoTextureView.setAlpha(0f);
            videoTextureView.setKeepScreenOn(true);
        }
        if (ivVideoPreviewThumb != null) {
            ivVideoPreviewThumb.setImageDrawable(null);
            ivVideoPreviewThumb.setVisibility(awaitingInitialVideoPlay ? View.VISIBLE : View.GONE);
        }

        final boolean isMpegTsFile = MpegTsNormalizer.isLikelyMpegTsName(nextFile.getName())
                || MpegTsNormalizer.detectTsLayoutCached(nextFile).packetStride > 0;

        startVideoExoPlayer(nextFile, onReadyUi, false, false);

        host.getExecutor().execute(() -> {
            int resolvedDur = 0;
            int resolvedW = 0;
            int resolvedH = 0;
            Bitmap previewFrame = null;
            if (isMpegTsFile) {
                long tsMs = MpegTsNormalizer.extractTsDurationMs(nextFile);
                if (tsMs > 0L) {
                    resolvedDur = (int) Math.min(Integer.MAX_VALUE, tsMs);
                }
            } else {
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
            }
            final int finalResolvedDur = resolvedDur;
            final int finalW = resolvedW;
            final int finalH = resolvedH;
            final Bitmap finalFrame = previewFrame;
            host.getUiHandler().post(() -> {
                File curTarget = host.getTargetFile();
                if (activity.isFinishing() || activity.isDestroyed() || curTarget == null || !curTarget.equals(nextFile)) {
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
                    ivVideoPreviewThumb.setScaleType(ImageView.ScaleType.MATRIX);
                    ivVideoPreviewThumb.setImageBitmap(finalFrame);
                    ivVideoPreviewThumb.setVisibility(View.VISIBLE);
                    updatePreviewThumbTransform();
                    ivVideoPreviewThumb.post(this::updatePreviewThumbTransform);
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
        releasePlayer();
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
                    new NormalizedTsExoDataSource.Factory(activity), extractorsFactory);

            DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(activity)
                    .setEnableDecoderFallback(true);

            DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                    .setBufferDurationsMs(15_000, 45_000, 250, 500)
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build();

            exoPlayer = new ExoPlayer.Builder(activity, renderersFactory)
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
            evaluateVideoDeviceSilence(false);
            exoPlayer.setVolume(isEffectiveVideoMuted() ? 0f : 1f);

            exoPlayer.addListener(new Player.Listener() {
                @Override
                public void onVideoSizeChanged(@NonNull VideoSize videoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        int rot = ((videoSize.unappliedRotationDegrees % 360) + 360) % 360;
                        currentVideoRotationDegrees = rot;
                        if (rot == 90 || rot == 270) {
                            currentVideoWidth = videoSize.height;
                            currentVideoHeight = videoSize.width;
                        } else {
                            currentVideoWidth = videoSize.width;
                            currentVideoHeight = videoSize.height;
                        }
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
                public void onRenderedFirstFrame() {
                    if (!awaitingInitialVideoPlay || isVideoPlayingSafely()) {
                        if (videoTextureView != null) videoTextureView.setAlpha(1f);
                        if (ivVideoPreviewThumb != null) ivVideoPreviewThumb.setVisibility(View.GONE);
                    }
                }

                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    if (exoPlayer == null) return;
                    if (playbackState == Player.STATE_BUFFERING) {
                        if (isMpegTs && !exoRemuxFallbackAttempted && !isRemuxingMtsPlayback && !awaitingInitialVideoPlay) {
                            host.getUiHandler().removeCallbacks(mtsBufferingWatchdog);
                            host.getUiHandler().postDelayed(mtsBufferingWatchdog, 9500L);
                        }
                    } else {
                        host.getUiHandler().removeCallbacks(mtsBufferingWatchdog);
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
        if (isRemuxingMtsPlayback) return;
        if (exoRemuxFallbackAttempted) {
            if (videoFallbackBar != null) videoFallbackBar.setVisibility(View.VISIBLE);
            return;
        }
        exoRemuxFallbackAttempted = true;
        isRemuxingMtsPlayback = true;
        final int remuxGen = ++mtsRemuxGeneration;
        host.getUiHandler().removeCallbacks(mtsBufferingWatchdog);
        releasePlayer();
        if (btnCenterPlayPauseRef != null) btnCenterPlayPauseRef.setVisibility(View.GONE);
        if (videoRemuxOverlay != null) videoRemuxOverlay.setVisibility(View.VISIBLE);

        host.getExecutor().execute(() -> {
            File cacheMp4 = new File(activity.getCacheDir(), "mts_play_" + System.currentTimeMillis() + ".mp4");
            try {
                TsToMp4Remuxer.remuxToMp4(originalMtsFile, cacheMp4, 0L, Long.MAX_VALUE, null);
                host.getUiHandler().post(() -> {
                    File curTf = host.getTargetFile();
                    if (remuxGen != mtsRemuxGeneration || activity.isFinishing() || activity.isDestroyed()
                            || curTf == null || !curTf.equals(originalMtsFile)) {
                        if (cacheMp4.exists()) cacheMp4.delete();
                        return;
                    }
                    isRemuxingMtsPlayback = false;
                    if (videoRemuxOverlay != null) videoRemuxOverlay.setVisibility(View.GONE);
                    cleanupActiveRemuxedCache();
                    activeRemuxedCacheFile = cacheMp4;
                    startPlayableMp4InExo(cacheMp4, onReadyUi, originalMtsFile);
                });
            } catch (Throwable t) {
                if (cacheMp4.exists()) cacheMp4.delete();
                host.getUiHandler().post(() -> {
                    if (remuxGen != mtsRemuxGeneration) return;
                    isRemuxingMtsPlayback = false;
                    if (videoRemuxOverlay != null) videoRemuxOverlay.setVisibility(View.GONE);
                    File curTf = host.getTargetFile();
                    if (!activity.isFinishing() && !activity.isDestroyed() && curTf != null && curTf.equals(originalMtsFile)) {
                        videoPrepared = false;
                        if (videoFallbackBar != null) videoFallbackBar.setVisibility(View.VISIBLE);
                        if (activeVideoSyncRunnable != null) activeVideoSyncRunnable.run();
                        else if (onReadyUi != null) onReadyUi.run();
                    }
                });
            }
        });
    }

    @OptIn(markerClass = UnstableApi.class)
    private void startPlayableMp4InExo(File mp4File, Runnable onReadyUi, File originalMtsFile) {
        releasePlayer();
        try {
            DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(activity)
                    .setEnableDecoderFallback(true);
            DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                    .setBufferDurationsMs(15_000, 45_000, 250, 500)
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build();
            exoPlayer = new ExoPlayer.Builder(activity, renderersFactory)
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
            exoPlayer.setVolume(isEffectiveVideoMuted() ? 0f : 1f);
            exoPlayer.addListener(new Player.Listener() {
                @Override
                public void onVideoSizeChanged(@NonNull VideoSize videoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        int rot = ((videoSize.unappliedRotationDegrees % 360) + 360) % 360;
                        currentVideoRotationDegrees = rot;
                        if (rot == 90 || rot == 270) {
                            currentVideoWidth = videoSize.height;
                            currentVideoHeight = videoSize.width;
                        } else {
                            currentVideoWidth = videoSize.width;
                            currentVideoHeight = videoSize.height;
                        }
                        currentVideoPixelRatio = videoSize.pixelWidthHeightRatio > 0f ? videoSize.pixelWidthHeightRatio : 1.0f;
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
                public void onRenderedFirstFrame() {
                    if (!awaitingInitialVideoPlay || isVideoPlayingSafely()) {
                        if (videoTextureView != null) videoTextureView.setAlpha(1f);
                        if (ivVideoPreviewThumb != null) ivVideoPreviewThumb.setVisibility(View.GONE);
                    }
                }

                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    if (exoPlayer == null) return;
                    if (playbackState == Player.STATE_READY) {
                        videoPrepared = true;
                        revealVideoSurfaceIfReady();
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
                    videoPrepared = false;
                    if (videoFallbackBar != null) videoFallbackBar.setVisibility(View.VISIBLE);
                    if (activeVideoSyncRunnable != null) activeVideoSyncRunnable.run();
                    else if (onReadyUi != null) onReadyUi.run();
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
            videoPrepared = false;
            if (videoFallbackBar != null) videoFallbackBar.setVisibility(View.VISIBLE);
            if (activeVideoSyncRunnable != null) activeVideoSyncRunnable.run();
            else if (onReadyUi != null) onReadyUi.run();
        }
    }

    private void startLegacyMediaPlayerFallback(File nextFile, boolean useFileDescriptor, Runnable onReadyUi) {
        releasePlayer();
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
            float vol = isEffectiveVideoMuted() ? 0f : 1f;
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
                revealVideoSurfaceIfReady();
                boolean autoStart = !suppressNextVideoAutoPlay;
                try {
                    if (autoStart) {
                        mp.start();
                    } else {
                        mp.setVolume(0f, 0f);
                        mp.start();
                        mp.pause();
                        float restoreVol = isEffectiveVideoMuted() ? 0f : 1f;
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

    private void setupVideoAdvancedTouchGestures(FrameLayout viewport, TextView badge, int accentColor) {
        final LinearLayout gestureHud = new LinearLayout(activity);
        gestureHud.setOrientation(LinearLayout.HORIZONTAL);
        gestureHud.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        gestureHud.setGravity(Gravity.CENTER_VERTICAL);
        gestureHud.setPadding(dp(14), dp(9), dp(14), dp(9));
        GradientDrawable hudBg = new GradientDrawable();
        hudBg.setCornerRadius(dp(18));
        hudBg.setColor(Color.argb(198, 12, 18, 32));
        hudBg.setStroke(Math.max(1, dp(1)), Color.argb(72, 255, 255, 255));
        gestureHud.setBackground(hudBg);
        gestureHud.setElevation(dp(8));
        gestureHud.setVisibility(View.GONE);

        final ImageView ivHudIcon = new ImageView(activity);
        ivHudIcon.setColorFilter(Color.WHITE);
        LinearLayout.LayoutParams hiLp = new LinearLayout.LayoutParams(dp(20), dp(20));
        hiLp.rightMargin = dp(10);
        gestureHud.addView(ivHudIcon, hiLp);

        final int barTrackW = dp(92);
        final int barTrackH = dp(6);
        FrameLayout hudTrack = new FrameLayout(activity);
        GradientDrawable trackBg = new GradientDrawable();
        trackBg.setCornerRadius(dp(3));
        trackBg.setColor(Color.argb(75, 255, 255, 255));
        hudTrack.setBackground(trackBg);

        final View hudFill = new View(activity);
        GradientDrawable fillBg = new GradientDrawable();
        fillBg.setCornerRadius(dp(3));
        fillBg.setColor(accentColor);
        hudFill.setBackground(fillBg);
        hudTrack.addView(hudFill, new FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.LEFT));
        gestureHud.addView(hudTrack, new LinearLayout.LayoutParams(barTrackW, barTrackH));

        final TextView tvHudPct = new TextView(activity);
        tvHudPct.setTextColor(Color.WHITE);
        tvHudPct.setTextSize(12.5f);
        tvHudPct.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tvHudPct.setMinWidth(dp(42));
        tvHudPct.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams hpLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hpLp.leftMargin = dp(10);
        gestureHud.addView(tvHudPct, hpLp);

        FrameLayout.LayoutParams hudLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        hudLp.topMargin = Math.max(dp(92), host.getLastVideoTopInsetPx() + dp(66));
        viewport.addView(gestureHud, hudLp);

        final Runnable hideHudRunnable = () -> {
            if (gestureHud.getVisibility() != View.VISIBLE) return;
            if (PrefsManager.isReduceAnimations(activity)) {
                gestureHud.setVisibility(View.GONE);
            } else {
                gestureHud.animate().alpha(0f).setDuration(220)
                        .withEndAction(() -> gestureHud.setVisibility(View.GONE)).start();
            }
        };

        final AudioManager audioMgr = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);

        final PointF lastPan = new PointF();
        final int[] activePanPointerId = new int[]{MotionEvent.INVALID_POINTER_ID};
        final float[] prevFocusX = new float[]{0f};
        final float[] prevFocusY = new float[]{0f};
        final boolean[] wasScalingSinceDown = new boolean[]{false};

        final int touchSlopPx = ViewConfiguration.get(activity).getScaledTouchSlop();
        final float[] swipeDownRawX = new float[]{0f};
        final float[] swipeDownRawY = new float[]{0f};
        final float[] swipeDownLocalX = new float[]{0f};
        final boolean[] gestureMovedBeyondSlop = new boolean[]{false};
        final int[] swipeMode = new int[]{0};
        final int[] horizontalSwipeStartPosMs = new int[]{0};
        final int[] horizontalSwipeTargetMs = new int[]{0};
        final long[] lastSwipeLiveSeekMs = new long[]{0L};
        final float[] verticalStartFraction = new float[]{0.5f};
        final int[] lastAppliedVolStep = new int[]{-1};

        final ScaleGestureDetector scaleDetector = new ScaleGestureDetector(activity,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScaleBegin(@NonNull ScaleGestureDetector detector) {
                        if (host.isVideoLocked() || !PrefsManager.isGesturePinchZoom(activity)) return false;
                        wasScalingSinceDown[0] = true;
                        swipeMode[0] = 0;
                        stopVideoHoldGestures(badge);
                        prevFocusX[0] = detector.getFocusX();
                        prevFocusY[0] = detector.getFocusY();
                        return true;
                    }

                    @Override
                    public boolean onScale(@NonNull ScaleGestureDetector detector) {
                        if (host.isVideoLocked() || videoTextureView == null
                                || !PrefsManager.isGesturePinchZoom(activity)) return false;
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

        final GestureDetector tapDetector = new GestureDetector(activity,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(@NonNull MotionEvent e) {
                        return true;
                    }

                    @Override
                    public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                        if (host.isVideoLocked() || wasScalingSinceDown[0] || gestureMovedBeyondSlop[0] || swipeMode[0] != 0) {
                            return true;
                        }
                        host.toggleMediaControlsVisibility();
                        return true;
                    }

                    @Override
                    public boolean onDoubleTap(@NonNull MotionEvent e) {
                        if (host.isVideoLocked() || wasScalingSinceDown[0]
                                || !PrefsManager.isGestureVideoSeekSwipe(activity)) {
                            return true;
                        }
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
                            ViewerUiHelper.flashSeekBadge(activity, host.getUiHandler(), badge, true);
                        } else {
                            seekVideoBy(-10_000);
                            ViewerUiHelper.flashSeekBadge(activity, host.getUiHandler(), badge, false);
                        }
                        return true;
                    }

                    @Override
                    public void onLongPress(@NonNull MotionEvent e) {
                        if (host.isVideoLocked() || wasScalingSinceDown[0] || gestureMovedBeyondSlop[0]
                                || swipeMode[0] != 0 || !PrefsManager.isGestureVideoHold2x(activity)) {
                            return;
                        }
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
                                    host.getUiHandler().postDelayed(this, 220L);
                                }
                            };
                            host.getUiHandler().post(rewindPulseRunnable);
                        }
                    }
                });

        viewport.setOnTouchListener((v, event) -> {
            if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true);
            if (host.isVideoLocked()) {
                stopVideoHoldGestures(badge);
                if (tvVideoCenterScrubTime != null) tvVideoCenterScrubTime.setVisibility(View.GONE);
                gestureHud.setVisibility(View.GONE);
                swipeMode[0] = 0;
                return true;
            }

            scaleDetector.onTouchEvent(event);
            tapDetector.onTouchEvent(event);

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    wasScalingSinceDown[0] = false;
                    gestureMovedBeyondSlop[0] = false;
                    swipeMode[0] = 0;
                    lastAppliedVolStep[0] = -1;
                    activePanPointerId[0] = event.getPointerId(0);
                    lastPan.set(event.getX(), event.getY());
                    swipeDownRawX[0] = event.getRawX();
                    swipeDownRawY[0] = event.getRawY();
                    swipeDownLocalX[0] = event.getX();
                    horizontalSwipeStartPosMs[0] = getCurrentVideoPositionMs();
                    horizontalSwipeTargetMs[0] = horizontalSwipeStartPosMs[0];
                    break;

                case MotionEvent.ACTION_POINTER_DOWN:
                    wasScalingSinceDown[0] = true;
                    swipeMode[0] = 0;
                    if (tvVideoCenterScrubTime != null) tvVideoCenterScrubTime.setVisibility(View.GONE);
                    host.getUiHandler().removeCallbacks(hideHudRunnable);
                    gestureHud.setVisibility(View.GONE);
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
                            if (Math.hypot(dx, dy) > 2f) {
                                gestureMovedBeyondSlop[0] = true;
                            }
                            videoTransX += dx;
                            videoTransY += dy;
                            clampVideoTranslation();
                            updateVideoAspectRatioMatrix();
                            lastPan.set(x, y);
                        } else if (!wasScalingSinceDown[0] && !isHold2xForwardActive && !isHold2xRewindActive) {
                            float dx = event.getRawX() - swipeDownRawX[0];
                            float dy = event.getRawY() - swipeDownRawY[0];
                            if (!gestureMovedBeyondSlop[0] && Math.hypot(dx, dy) > touchSlopPx) {
                                gestureMovedBeyondSlop[0] = true;
                            }
                            if (swipeMode[0] == 0) {
                                if (videoPrepared && PrefsManager.isGestureVideoSeekSwipe(activity)
                                        && Math.abs(dx) > touchSlopPx * 1.5f
                                        && Math.abs(dx) > Math.abs(dy) * 1.3f) {
                                    swipeMode[0] = 1;
                                } else if (PrefsManager.isGestureVideoVolumeBrightness(activity)
                                        && Math.abs(dy) > touchSlopPx * 1.25f
                                        && Math.abs(dy) > Math.abs(dx) * 1.25f) {
                                    boolean rightHalf = swipeDownLocalX[0] >= (Math.max(1f, viewport.getWidth()) * 0.5f);
                                    if (rightHalf && audioMgr != null) {
                                        swipeMode[0] = 2;
                                        int maxV = Math.max(1, audioMgr.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
                                        int curV = audioMgr.getStreamVolume(AudioManager.STREAM_MUSIC);
                                        verticalStartFraction[0] = curV / (float) maxV;
                                        lastAppliedVolStep[0] = curV;
                                    } else {
                                        swipeMode[0] = 3;
                                        WindowManager.LayoutParams lp = activity.getWindow().getAttributes();
                                        float curB = lp != null ? lp.screenBrightness : -1f;
                                        if (curB < 0f) {
                                            try {
                                                int sysB = Settings.System.getInt(activity.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 128);
                                                curB = Math.max(0.01f, Math.min(1.0f, sysB / 255f));
                                            } catch (Throwable t) {
                                                curB = 0.5f;
                                            }
                                        }
                                        verticalStartFraction[0] = Math.max(0.01f, Math.min(1.0f, curB));
                                    }
                                }
                            }

                            if (swipeMode[0] == 1) {
                                int dur = getEffectiveVideoDurationMs();
                                float width = Math.max(1f, viewport.getWidth());
                                int deltaMs = Math.round((dx / width) * 90_000f);
                                int target = dur > 0
                                        ? Math.max(0, Math.min(dur, horizontalSwipeStartPosMs[0] + deltaMs))
                                        : Math.max(0, horizontalSwipeStartPosMs[0] + deltaMs);
                                horizontalSwipeTargetMs[0] = target;
                                File curTf = host.getTargetFile();
                                boolean isTs = curTf != null && (MpegTsNormalizer.isLikelyMpegTsName(curTf.getName())
                                        || MpegTsNormalizer.detectTsLayoutCached(curTf).packetStride > 0);
                                pendingSeekTargetMs = target;
                                holdSeekUiUntilMs = System.currentTimeMillis() + (isTs ? 2000L : 900L);
                                if (tvVideoCenterScrubTime != null) {
                                    tvVideoCenterScrubTime.setText(ViewerUiHelper.formatClock(target));
                                    tvVideoCenterScrubTime.setVisibility(View.VISIBLE);
                                }
                                long now = System.currentTimeMillis();
                                long minInterval = isTs ? 450L : 220L;
                                if (now - lastSwipeLiveSeekMs[0] >= minInterval) {
                                    lastSwipeLiveSeekMs[0] = now;
                                    seekVideoToMs(target);
                                }
                            } else if (swipeMode[0] == 2 && audioMgr != null) {
                                float rangePx = Math.max(dp(150), viewport.getHeight() * 0.55f);
                                float frac = Math.max(0f, Math.min(1f, verticalStartFraction[0] + (-dy / rangePx)));
                                int maxV = Math.max(1, audioMgr.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
                                int targetStep = Math.max(0, Math.min(maxV, Math.round(frac * maxV)));
                                if (targetStep != lastAppliedVolStep[0]) {
                                    lastAppliedVolStep[0] = targetStep;
                                    try {
                                        audioMgr.setStreamVolume(AudioManager.STREAM_MUSIC, targetStep, 0);
                                    } catch (Throwable ignored) {
                                    }
                                }
                                int pct = Math.round(frac * 100f);
                                host.getUiHandler().removeCallbacks(hideHudRunnable);
                                FrameLayout.LayoutParams gLp = (FrameLayout.LayoutParams) gestureHud.getLayoutParams();
                                gLp.topMargin = Math.max(dp(92), host.getLastVideoTopInsetPx() + dp(66));
                                gestureHud.setLayoutParams(gLp);
                                gestureHud.animate().cancel();
                                gestureHud.setAlpha(1f);
                                gestureHud.setVisibility(View.VISIBLE);
                                ivHudIcon.setImageResource(R.drawable.ic_audio);
                                tvHudPct.setText(pct + "%");
                                ViewGroup.LayoutParams fillLp = hudFill.getLayoutParams();
                                fillLp.width = Math.max(0, Math.round(barTrackW * frac));
                                hudFill.setLayoutParams(fillLp);
                            } else if (swipeMode[0] == 3) {
                                float rangePx = Math.max(dp(150), viewport.getHeight() * 0.55f);
                                float frac = Math.max(0.01f, Math.min(1f, verticalStartFraction[0] + (-dy / rangePx)));
                                WindowManager.LayoutParams lp = activity.getWindow().getAttributes();
                                if (lp != null) {
                                    lp.screenBrightness = frac;
                                    activity.getWindow().setAttributes(lp);
                                }
                                int pct = Math.max(1, Math.round(frac * 100f));
                                host.getUiHandler().removeCallbacks(hideHudRunnable);
                                FrameLayout.LayoutParams gLp = (FrameLayout.LayoutParams) gestureHud.getLayoutParams();
                                gLp.topMargin = Math.max(dp(92), host.getLastVideoTopInsetPx() + dp(66));
                                gestureHud.setLayoutParams(gLp);
                                gestureHud.animate().cancel();
                                gestureHud.setAlpha(1f);
                                gestureHud.setVisibility(View.VISIBLE);
                                ivHudIcon.setImageResource(R.drawable.ic_brightness);
                                tvHudPct.setText(pct + "%");
                                ViewGroup.LayoutParams fillLp = hudFill.getLayoutParams();
                                fillLp.width = Math.max(0, Math.round(barTrackW * frac));
                                hudFill.setLayoutParams(fillLp);
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
                    if (swipeMode[0] == 1) {
                        if (tvVideoCenterScrubTime != null) {
                            tvVideoCenterScrubTime.setVisibility(View.GONE);
                        }
                        if (videoPrepared) {
                            File curTf = host.getTargetFile();
                            boolean isTs = curTf != null && (MpegTsNormalizer.isLikelyMpegTsName(curTf.getName())
                                    || MpegTsNormalizer.detectTsLayoutCached(curTf).packetStride > 0);
                            pendingSeekTargetMs = horizontalSwipeTargetMs[0];
                            holdSeekUiUntilMs = System.currentTimeMillis() + (isTs ? 2000L : 900L);
                            seekVideoToMs(horizontalSwipeTargetMs[0]);
                        }
                    } else if (swipeMode[0] == 2 || swipeMode[0] == 3) {
                        host.getUiHandler().removeCallbacks(hideHudRunnable);
                        host.getUiHandler().postDelayed(hideHudRunnable, 520L);
                    }
                    swipeMode[0] = 0;
                    gestureMovedBeyondSlop[0] = false;
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
            if (rewindPulseRunnable != null) host.getUiHandler().removeCallbacks(rewindPulseRunnable);
            badge.setVisibility(View.GONE);
        }
    }

    private void showPersistentHoldBadge(TextView badge, String text) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = Math.max(dp(86), host.getLastVideoTopInsetPx() + dp(62));
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
        if (PrefsManager.isReduceAnimations(activity)) {
            host.getUiHandler().postDelayed(() -> badge.setVisibility(View.GONE), 480);
        } else {
            badge.animate().alpha(0f).setStartDelay(400).setDuration(220)
                    .withEndAction(() -> badge.setVisibility(View.GONE)).start();
        }
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
        } else {
            updatePreviewThumbTransform();
        }
    }

    private void toggleScreenOrientation() {
        boolean currentlyLandscape = activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        isLandscapeLocked = !currentlyLandscape;
        activity.setRequestedOrientation(isLandscapeLocked
                ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
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
        File tf = host.getTargetFile();
        boolean isTs = tf != null && MpegTsNormalizer.isLikelyMpegTsName(tf.getName());
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
            File curTf = host.getTargetFile();
            boolean isTs = curTf != null && (MpegTsNormalizer.isLikelyMpegTsName(curTf.getName())
                    || MpegTsNormalizer.detectTsLayoutCached(curTf).packetStride > 0);
            pendingSeekTargetMs = next;
            holdSeekUiUntilMs = now + (isTs ? 2000L : 900L);
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
                Toast.makeText(activity, "שינוי מהירות אינו נתמך בפורמט וידאו זה", Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(activity, "שינוי מהירות אינו נתמך בפורמט וידאו זה", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            try {
                if (shouldPlay && !videoPlayer.isPlaying()) videoPlayer.start();
            } catch (Exception ignored) {
            }
        }
    }

    private boolean isEffectiveVideoMuted() {
        return videoMuted || videoMutedByDeviceSilence;
    }

    private void applyCurrentVideoVolume() {
        float vol = isEffectiveVideoMuted() ? 0f : 1f;
        try {
            if (exoPlayer != null) exoPlayer.setVolume(vol);
            if (videoPlayer != null) videoPlayer.setVolume(vol, vol);
        } catch (Exception ignored) {
        }
    }

    private void evaluateVideoDeviceSilence(boolean notifyUser) {
        if (videoMutedByDeviceSilence) {
            videoMutedByDeviceSilence = false;
            applyCurrentVideoVolume();
        }
    }

    private void registerVideoSilenceMonitor() {
        if (videoSilenceReceiver != null) return;
        videoSilenceReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                evaluateVideoDeviceSilence(true);
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.media.VOLUME_CHANGED_ACTION");
        filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        filter.addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activity.registerReceiver(videoSilenceReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                activity.registerReceiver(videoSilenceReceiver, filter);
            }
        } catch (Throwable ignored) {
        }
    }

    private void unregisterVideoSilenceMonitor() {
        if (videoSilenceReceiver != null) {
            try {
                activity.unregisterReceiver(videoSilenceReceiver);
            } catch (Throwable ignored) {
            }
            videoSilenceReceiver = null;
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

    public void releasePlayer() {
        videoPrepared = false;
        currentVideoWidth = 0;
        currentVideoHeight = 0;
        currentVideoPixelRatio = 1.0f;
        currentVideoRotationDegrees = 0;
        lastObservedVideoPosMs = 0;
        unexpectedPosResetCount = 0;
        if (rewindPulseRunnable != null) host.getUiHandler().removeCallbacks(rewindPulseRunnable);
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

    public void destroy() {
        unregisterVideoSilenceMonitor();
        host.getUiHandler().removeCallbacks(mtsBufferingWatchdog);
        videoTickerActive = false;
        releasePlayer();
        cleanupActiveRemuxedCache();
        activeVideoSyncRunnable = null;
    }

    private int dp(float v) {
        return ViewerUiHelper.dp(activity, v);
    }
}
