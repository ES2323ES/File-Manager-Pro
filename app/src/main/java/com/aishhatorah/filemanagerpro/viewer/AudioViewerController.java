package com.aishhatorah.filemanagerpro.viewer;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aishhatorah.filemanagerpro.AudioCutterActivity;
import com.aishhatorah.filemanagerpro.AudioPaletteHelper;
import com.aishhatorah.filemanagerpro.AudioPaletteHelper.AudioAdaptivePalette;
import com.aishhatorah.filemanagerpro.AudioPlayerManager;
import com.aishhatorah.filemanagerpro.GlassAnchoredPopoverHelper;
import com.aishhatorah.filemanagerpro.PrefsManager;
import com.aishhatorah.filemanagerpro.R;
import com.aishhatorah.filemanagerpro.ThemeHelper;
import com.aishhatorah.filemanagerpro.ThemedDialogHelper;
import com.aishhatorah.filemanagerpro.WavySeekBar;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * בקר נגן שמע מתקדם: פלטת צבעים דינמית מעטיפת השיר, מחוות החלקה אנכית/אופקית,
 * תור ניגון בגרירה, טיימר שינה, אקולייזר והדגשת בס, וקיצור לחותך אודיו.
 */
public class AudioViewerController {

    private final AppCompatActivity activity;
    private final ViewerHost host;

    private boolean audioTickerActive = false;
    private AudioPlayerManager.PlaybackListener audioListener;
    private LinearLayout audioRootLayout = null;
    private View audioStagePlayerView = null;
    private TextView tvAudioCenterScrubTime = null;

    public AudioViewerController(ViewerHost host) {
        this.host = host;
        this.activity = host.getActivity();
    }

    public void render(
            LinearLayout topBar,
            ImageButton audioTopBackBtn,
            ImageButton audioTopShareBtn,
            int defaultBgColor,
            int textPrimary,
            int textSecondary,
            int accent,
            int theme,
            boolean isLandscape
    ) {
        if (activity.getWindow() != null) {
            WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), true);
        }
        ViewerUiHelper.setSystemBarsFullscreen(activity, false);

        final AudioPlayerManager audioMgr = AudioPlayerManager.getInstance();
        final int touchSlopPx = ViewConfiguration.get(activity).getScaledTouchSlop();

        final int[] audioSwipeMode = new int[]{0}; // 0=none, 1=horizontal seek, 2=vertical track
        final float[] swipeDownX = new float[]{0f};
        final float[] swipeDownY = new float[]{0f};
        final int[] swipeStartPosMs = new int[]{0};
        final int[] pendingSwipeTargetMs = new int[]{0};
        final long[] lastLiveSeekMs = new long[]{0L};

        LinearLayout root = new LinearLayout(activity) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
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
                                tvAudioCenterScrubTime.setText(ViewerUiHelper.formatClock(target));
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
                            boolean reduce = PrefsManager.isReduceAnimations(activity);
                            if (stage != null && Math.abs(dy) > triggerThreshold) {
                                final boolean goingPreviousDown = dy > 0;
                                if (reduce) {
                                    stage.setTranslationY(0f);
                                    stage.setAlpha(1f);
                                    if (goingPreviousDown) {
                                        audioMgr.playPreviousTrackImmediate(activity);
                                    } else {
                                        audioMgr.playNext(activity);
                                    }
                                } else {
                                    float exitY = goingPreviousDown ? slideDist : -slideDist;
                                    float enterFromY = goingPreviousDown ? -slideDist : slideDist;
                                    stage.animate()
                                            .translationY(exitY)
                                            .alpha(0f)
                                            .setDuration(150)
                                            .withEndAction(() -> {
                                                if (goingPreviousDown) {
                                                    audioMgr.playPreviousTrackImmediate(activity);
                                                } else {
                                                    audioMgr.playNext(activity);
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
                                }
                            } else if (stage != null) {
                                if (reduce) {
                                    stage.setTranslationY(0f);
                                    stage.setAlpha(1f);
                                } else {
                                    stage.animate().translationY(0f).alpha(1f).setDuration(160).start();
                                }
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

        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(defaultBgColor);
        if (topBar != null) {
            root.addView(topBar);
        }

        LinearLayout contentArea = new LinearLayout(activity);
        contentArea.setOrientation(LinearLayout.VERTICAL);
        contentArea.setGravity(Gravity.CENTER);
        int areaPad = dp(isLandscape ? 8 : 14);
        contentArea.setPadding(areaPad, areaPad, areaPad, areaPad);
        root.addView(contentArea, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        FrameLayout audioOverlayFrame = new FrameLayout(activity);
        audioOverlayFrame.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        tvAudioCenterScrubTime = ViewerUiHelper.createCleanCenterTimeOverlay(activity);
        audioOverlayFrame.addView(tvAudioCenterScrubTime);
        activity.setContentView(audioOverlayFrame);

        audioRootLayout = root;
        renderAudioPlayerContent(
                contentArea, topBar, audioTopBackBtn, audioTopShareBtn,
                defaultBgColor, textPrimary, textSecondary, accent, theme, isLandscape
        );
    }

    private void renderAudioPlayerContent(
            LinearLayout contentArea,
            LinearLayout topBar,
            ImageButton audioTopBackBtn,
            ImageButton audioTopShareBtn,
            int defaultBgColor,
            int textPrimary,
            int textSecondary,
            int accent,
            int theme,
            boolean isLandscape
    ) {
        AudioPlayerManager mgr = AudioPlayerManager.getInstance();
        mgr.playOrAttachFile(activity, host.getTargetFile());

        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        float smallestDp = Math.min(dm.widthPixels, dm.heightPixels) / dm.density;
        float scale = Math.max(0.82f, Math.min(1.06f, smallestDp / 380f));

        int primaryBtnPx = dp(Math.round(54 * scale));
        int skipBtnPx = dp(Math.round(42 * scale));
        int seek10BtnPx = dp(Math.round(40 * scale));
        int secRowBtnPx = dp(Math.round(40 * scale));
        int btnMarginPx = dp(Math.round(6 * scale));

        LinearLayout playerRoot = new LinearLayout(activity);
        playerRoot.setBackground(null);
        audioStagePlayerView = playerRoot;
        if (isLandscape) {
            playerRoot.setOrientation(LinearLayout.HORIZONTAL);
            playerRoot.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            playerRoot.setPadding(dp(8), dp(4), dp(8), dp(4));
        } else {
            playerRoot.setOrientation(LinearLayout.VERTICAL);
        }

        FrameLayout stageFrame = new FrameLayout(activity);
        stageFrame.setBackground(null);

        final ImageView ivArt = new ImageView(activity);
        ivArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        ivArt.setBackgroundColor(Color.TRANSPARENT);
        ivArt.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(20));
            }
        });

        stageFrame.addView(ivArt, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));

        final TextView tvDoubleTapBadge = ViewerUiHelper.createSeekOverlayBadge(activity);
        stageFrame.addView(tvDoubleTapBadge);

        final GestureDetector dblTapDetector = new GestureDetector(activity, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(@NonNull MotionEvent e) {
                return true;
            }

            @Override
            public boolean onDoubleTap(@NonNull MotionEvent e) {
                boolean rightSide = e.getX() >= (stageFrame.getWidth() / 2f);
                if (rightSide) {
                    mgr.seekBy(10_000);
                    ViewerUiHelper.flashSeekBadge(activity, host.getUiHandler(), tvDoubleTapBadge, true);
                } else {
                    mgr.seekBy(-10_000);
                    ViewerUiHelper.flashSeekBadge(activity, host.getUiHandler(), tvDoubleTapBadge, false);
                }
                return true;
            }
        });
        stageFrame.setOnTouchListener((v, ev) -> dblTapDetector.onTouchEvent(ev));

        LinearLayout controlsColumn = new LinearLayout(activity);
        controlsColumn.setOrientation(LinearLayout.VERTICAL);
        controlsColumn.setGravity(Gravity.CENTER_VERTICAL);

        if (isLandscape) {
            LinearLayout.LayoutParams stageLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.44f);
            stageLp.rightMargin = dp(14);
            playerRoot.addView(stageFrame, stageLp);

            playerRoot.addView(controlsColumn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.56f));
        } else {
            LinearLayout.LayoutParams stageLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
            stageLp.bottomMargin = dp(10);
            playerRoot.addView(stageFrame, stageLp);

            playerRoot.addView(controlsColumn, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        LinearLayout timeRowLtr = new LinearLayout(activity);
        timeRowLtr.setOrientation(LinearLayout.HORIZONTAL);
        timeRowLtr.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        timeRowLtr.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        timeRowLtr.setPadding(dp(6), dp(4), dp(6), dp(2));

        final TextView tvElapsedLeftmost = new TextView(activity);
        tvElapsedLeftmost.setText("00:00");
        tvElapsedLeftmost.setTextColor(accent);
        tvElapsedLeftmost.setTextSize(14f);
        tvElapsedLeftmost.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        timeRowLtr.addView(tvElapsedLeftmost);

        final TextView tvTotalNextToIt = new TextView(activity);
        tvTotalNextToIt.setText("  /  00:00");
        tvTotalNextToIt.setTextColor(accent);
        tvTotalNextToIt.setTextSize(13.5f);
        tvTotalNextToIt.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        timeRowLtr.addView(tvTotalNextToIt);

        controlsColumn.addView(timeRowLtr, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final WavySeekBar seekBar = new WavySeekBar(activity);
        seekBar.setShowTickDots(false);
        seekBar.setAnimationsEnabled(!PrefsManager.isReduceAnimations(activity));
        seekBar.setColors(accent, ColorUtils.setAlphaComponent(textPrimary, 56), accent);
        controlsColumn.addView(seekBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout transportRow = new LinearLayout(activity);
        transportRow.setOrientation(LinearLayout.HORIZONTAL);
        transportRow.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        transportRow.setGravity(Gravity.CENTER);
        transportRow.setPadding(0, dp(isLandscape ? 4 : 8), 0, dp(isLandscape ? 4 : 6));

        final ImageButton btnBack10 = ViewerUiHelper.createCleanBorderlessIconButton(activity, R.drawable.ic_replay_10, seek10BtnPx, textPrimary);
        btnBack10.setContentDescription("10 שניות אחורה");
        final ImageButton btnPrevSong = ViewerUiHelper.createCleanBorderlessIconButton(activity, R.drawable.ic_skip_prev, skipBtnPx, textPrimary);
        btnPrevSong.setContentDescription("רצועה קודמת");
        final ImageButton btnPlayPause = ViewerUiHelper.createResponsiveRoundIconButton(activity, R.drawable.ic_pause, primaryBtnPx, btnMarginPx, true, accent);
        btnPlayPause.setContentDescription("נגן / השהה");
        final ImageButton btnNextSong = ViewerUiHelper.createCleanBorderlessIconButton(activity, R.drawable.ic_skip_next, skipBtnPx, textPrimary);
        btnNextSong.setContentDescription("רצועה הבאה");
        final ImageButton btnFwd10 = ViewerUiHelper.createCleanBorderlessIconButton(activity, R.drawable.ic_forward_10, seek10BtnPx, textPrimary);
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

        FrameLayout secondRow = new FrameLayout(activity);
        secondRow.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        secondRow.setPadding(dp(14), dp(2), dp(14), dp(2));

        final ImageButton btnMorePlus = ViewerUiHelper.createCleanBorderlessIconButton(activity, R.drawable.ic_more_dots, secRowBtnPx, textPrimary);
        btnMorePlus.setContentDescription("כלים נוספים");
        secondRow.addView(btnMorePlus, new FrameLayout.LayoutParams(
                secRowBtnPx, secRowBtnPx, Gravity.RIGHT | Gravity.CENTER_VERTICAL));

        final ImageButton btnSpeedCenter = ViewerUiHelper.createCleanBorderlessIconButton(activity, R.drawable.ic_speed, secRowBtnPx, textPrimary);
        btnSpeedCenter.setContentDescription("מהירות השמעה");
        secondRow.addView(btnSpeedCenter, new FrameLayout.LayoutParams(
                secRowBtnPx, secRowBtnPx, Gravity.CENTER));

        final ImageButton btnRepeatMode = ViewerUiHelper.createCleanBorderlessIconButton(activity, R.drawable.ic_repeat_off, secRowBtnPx, textPrimary);
        btnRepeatMode.setContentDescription("חזרה");
        secondRow.addView(btnRepeatMode, new FrameLayout.LayoutParams(
                secRowBtnPx, secRowBtnPx, Gravity.LEFT | Gravity.CENTER_VERTICAL));

        controlsColumn.addView(secondRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        contentArea.addView(playerRoot, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final AudioAdaptivePalette[] activePalHolder = new AudioAdaptivePalette[]{
                AudioPaletteHelper.computePalette(activity, null, theme, defaultBgColor, textPrimary, textSecondary, accent)
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
            Window window = activity.getWindow();
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
            if (host.getMarqueeTitleView() != null) {
                host.getMarqueeTitleView().setTextColor(pal.textPrimary);
            }
            if (host.getFolderTrackCounterView() != null) {
                host.getFolderTrackCounterView().setTextColor(pal.textSecondary);
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
        btnPrevSong.setOnClickListener(v -> mgr.playPrevious(activity));
        btnNextSong.setOnClickListener(v -> mgr.playNext(activity));
        btnPlayPause.setOnClickListener(v -> mgr.togglePlayPause(activity));

        btnSpeedCenter.setOnClickListener(v -> ViewerUiHelper.showSpeedPickerDialog(
                activity, v, "בחירת מהירות השיר", mgr.getPlaybackSpeed(), chosen -> {
                    mgr.setPlaybackSpeed(activity, chosen);
                    btnSpeedCenter.setColorFilter(Math.abs(chosen - 1.0f) >= 0.02f
                            ? activePalHolder[0].accent : activePalHolder[0].textPrimary);
                }));

        btnRepeatMode.setOnClickListener(v -> {
            int nextMode = mgr.cycleRepeatMode();
            lastRepeatRes[0] = ViewerUiHelper.resolveRepeatIcon(nextMode);
            btnRepeatMode.setImageResource(lastRepeatRes[0]);
            btnRepeatMode.setColorFilter(nextMode != AudioPlayerManager.REPEAT_OFF
                    ? activePalHolder[0].accent : activePalHolder[0].textPrimary);
        });

        btnMorePlus.setOnClickListener(v -> {
            final View anchorBtn = v;
            AudioAdaptivePalette pal = activePalHolder[0];
            LinearLayout iconStrip = new LinearLayout(activity);
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
            ImageButton bQueue = ViewerUiHelper.createCleanBorderlessIconButton(activity, R.drawable.ic_queue, iconSize, pal.textPrimary);
            bQueue.setContentDescription("תור ניגון");
            bQueue.setOnClickListener(iv -> {
                if (popupRef[0] != null) popupRef[0].dismiss();
                showAudioQueueDialog(anchorBtn);
            });

            int sleepTint = mgr.getSleepTimerRemainingMs() > 0 ? pal.accent : pal.textPrimary;
            ImageButton bSleep = ViewerUiHelper.createCleanBorderlessIconButton(activity, R.drawable.ic_sleep_timer, iconSize, sleepTint);
            bSleep.setContentDescription("טיימר שינה");
            bSleep.setOnClickListener(iv -> {
                if (popupRef[0] != null) popupRef[0].dismiss();
                showSleepTimerDialog(anchorBtn);
            });

            ImageButton bEq = ViewerUiHelper.createCleanBorderlessIconButton(activity, R.drawable.ic_equalizer, iconSize, pal.textPrimary);
            bEq.setContentDescription("אקולייזר");
            bEq.setOnClickListener(iv -> {
                if (popupRef[0] != null) popupRef[0].dismiss();
                showEqualizerDialog(anchorBtn);
            });

            ImageButton bCut = ViewerUiHelper.createCleanBorderlessIconButton(activity, R.drawable.ic_audio_cutter, iconSize, pal.textPrimary);
            bCut.setContentDescription("חתוך אודיו");
            bCut.setOnClickListener(iv -> {
                if (popupRef[0] != null) popupRef[0].dismiss();
                File current = mgr.getCurrentFile() != null ? mgr.getCurrentFile() : host.getTargetFile();
                if (current != null) {
                    Intent ci = new Intent(activity, AudioCutterActivity.class);
                    ci.putExtra(AudioCutterActivity.EXTRA_INITIAL_PATH, current.getAbsolutePath());
                    activity.startActivity(ci);
                }
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
            int screenW = activity.getResources().getDisplayMetrics().widthPixels;
            int showX = Math.max(dp(8), Math.min(screenW - popW - dp(8), loc[0] + v.getWidth() / 2 - popW / 2));
            int showY = Math.max(dp(12), loc[1] - popH - dp(8));
            popup.showAtLocation(v, Gravity.NO_GRAVITY, showX, showY);
        });

        seekBar.setOnSeekBarChangeListener(new WavySeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(WavySeekBar sb, int progress, boolean fromUser) {
                if (fromUser) mgr.seekTo(progress);
            }

            @Override
            public void onStartTrackingTouch(WavySeekBar sb) {
            }

            @Override
            public void onStopTrackingTouch(WavySeekBar sb) {
            }
        });

        final Runnable syncAudioUi = () -> {
            File active = mgr.getCurrentFile();
            if (active != null) {
                host.setTargetFile(active);
                host.updateMarqueeHeaderName(active.getName());
            }
            TextView tvCounter = host.getFolderTrackCounterView();
            if (tvCounter != null) {
                String cntStr = mgr.getCurrentTrackNumber() + "/" + mgr.getFolderTrackCount();
                if (!cntStr.equals(lastTrackCounter[0])) {
                    lastTrackCounter[0] = cntStr;
                    tvCounter.setVisibility(View.VISIBLE);
                    tvCounter.setText(cntStr);
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
                activePalHolder[0] = AudioPaletteHelper.computePalette(activity, art, theme, defaultBgColor, textPrimary, textSecondary, accent);
                applyPaletteAndButtonTints.run();
            } else {
                btnSpeedCenter.setColorFilter(Math.abs(mgr.getPlaybackSpeed() - 1.0f) >= 0.02f
                        ? activePalHolder[0].accent : activePalHolder[0].textPrimary);
                btnRepeatMode.setColorFilter(mgr.getRepeatMode() != AudioPlayerManager.REPEAT_OFF
                        ? activePalHolder[0].accent : activePalHolder[0].textPrimary);
            }

            boolean playingNow = mgr.isPlaying();
            seekBar.setAnimationsEnabled(!PrefsManager.isReduceAnimations(activity));
            seekBar.setPlaying(playingNow);
            int playRes = playingNow ? R.drawable.ic_pause : R.drawable.ic_play;
            if (lastPlayRes[0] != playRes) {
                lastPlayRes[0] = playRes;
                btnPlayPause.setImageResource(playRes);
            }
            int repRes = ViewerUiHelper.resolveRepeatIcon(mgr.getRepeatMode());
            if (lastRepeatRes[0] != repRes) {
                lastRepeatRes[0] = repRes;
                btnRepeatMode.setImageResource(repRes);
            }
        };

        audioListener = syncAudioUi::run;
        mgr.addListener(audioListener);
        syncAudioUi.run();

        audioTickerActive = true;
        host.getUiHandler().post(new Runnable() {
            @Override
            public void run() {
                if (!audioTickerActive) return;
                int dur = mgr.getDurationMs();
                int pos = mgr.getCurrentPositionMs();
                seekBar.setAnimationsEnabled(!PrefsManager.isReduceAnimations(activity));
                seekBar.setPlaying(mgr.isPlaying());
                seekBar.setMax(Math.max(1, dur));
                seekBar.setProgress(pos);
                String posStr = ViewerUiHelper.formatClock(pos);
                if (!posStr.equals(lastElapsedStr[0])) {
                    lastElapsedStr[0] = posStr;
                    tvElapsedLeftmost.setText(posStr);
                }
                String totStr = "  /  " + ViewerUiHelper.formatClock(dur);
                if (!totStr.equals(lastTotalStr[0])) {
                    lastTotalStr[0] = totStr;
                    tvTotalNextToIt.setText(totStr);
                }
                host.getUiHandler().postDelayed(this, 250);
            }
        });
    }

    private void showAudioQueueDialog(View anchor) {
        final AudioPlayerManager mgr = AudioPlayerManager.getInstance();
        final int theme = PrefsManager.getThemeMode(activity);
        final int textPrimary = ThemeHelper.getPrimaryTextColor(activity, theme);
        final int accent = ThemeHelper.getAccentColor(activity, theme);
        final boolean dark = ThemeHelper.isDarkSurface(activity, theme);

        final List<File> queue = new ArrayList<>(mgr.getPlaylistSnapshot());
        final RecyclerView rv = new RecyclerView(activity);
        rv.setLayoutManager(new LinearLayoutManager(activity));
        rv.setOverScrollMode(View.OVER_SCROLL_NEVER);

        final ItemTouchHelper[] helperRef = new ItemTouchHelper[1];

        RecyclerView.Adapter<RecyclerView.ViewHolder> adapter = new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            class QueueVH extends RecyclerView.ViewHolder {
                final LinearLayout row;
                final ImageView handle;
                final TextView title;
                final ImageButton del;

                QueueVH(LinearLayout r, ImageView h, TextView t, ImageButton d) {
                    super(r);
                    this.row = r;
                    this.handle = h;
                    this.title = t;
                    this.del = d;
                }
            }

            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                LinearLayout row = new LinearLayout(parent.getContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(10), dp(8), dp(10), dp(8));
                RecyclerView.LayoutParams rLp = new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rLp.setMargins(0, dp(2), 0, dp(2));
                row.setLayoutParams(rLp);

                ImageView drag = new ImageView(parent.getContext());
                drag.setImageResource(R.drawable.ic_drag_handle);
                row.addView(drag, new LinearLayout.LayoutParams(dp(22), dp(22)));

                TextView tvName = new TextView(parent.getContext());
                tvName.setTextSize(ThemeHelper.spScaled(activity, 13f));
                tvName.setMaxLines(1);
                tvName.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                tLp.setMarginStart(dp(8));
                tLp.setMarginEnd(dp(6));
                row.addView(tvName, tLp);

                ImageButton del = ViewerUiHelper.createCleanBorderlessIconButton(activity, R.drawable.ic_close, dp(28), Color.parseColor("#DC2626"));
                del.setContentDescription("הסר מהתור");
                row.addView(del);
                return new QueueVH(row, drag, tvName, del);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                QueueVH h = (QueueVH) holder;
                File track = queue.get(position);
                int curIdx = mgr.getCurrentIndex();
                boolean isCurrent = position == curIdx;

                GradientDrawable rowBg = new GradientDrawable();
                rowBg.setCornerRadius(dp(11));
                if (isCurrent) {
                    rowBg.setColor(ColorUtils.setAlphaComponent(accent, dark ? 48 : 26));
                    rowBg.setStroke(Math.max(1, dp(1)), ColorUtils.setAlphaComponent(accent, 130));
                } else {
                    rowBg.setColor(ColorUtils.setAlphaComponent(ThemeHelper.getBgColor(activity, theme), dark ? 140 : 170));
                }
                h.row.setBackground(rowBg);
                h.handle.setColorFilter(accent);
                h.title.setText((position + 1) + ". " + track.getName());
                h.title.setTextColor(isCurrent ? accent : textPrimary);
                h.title.setTypeface(isCurrent ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);

                h.handle.setOnTouchListener((v, ev) -> {
                    if (ev.getActionMasked() == MotionEvent.ACTION_DOWN && helperRef[0] != null) {
                        helperRef[0].startDrag(h);
                    }
                    return false;
                });
                h.row.setOnClickListener(v -> {
                    int pos = h.getAdapterPosition();
                    if (pos >= 0 && pos < queue.size()) {
                        mgr.playTrackAt(activity, pos);
                        notifyDataSetChanged();
                    }
                });
                h.del.setVisibility((!isCurrent && queue.size() > 1) ? View.VISIBLE : View.GONE);
                h.del.setOnClickListener(v -> {
                    int pos = h.getAdapterPosition();
                    if (pos >= 0 && pos < queue.size() && pos != mgr.getCurrentIndex() && queue.size() > 1) {
                        mgr.removeFromQueue(pos);
                        queue.remove(pos);
                        notifyItemRemoved(pos);
                        notifyItemRangeChanged(Math.min(pos, queue.size()), queue.size() - Math.min(pos, queue.size()));
                    }
                });
            }

            @Override
            public int getItemCount() {
                return queue.size();
            }
        };
        rv.setAdapter(adapter);

        ItemTouchHelper touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                int from = viewHolder.getAdapterPosition();
                int to = target.getAdapterPosition();
                if (from < 0 || to < 0 || from >= queue.size() || to >= queue.size()) return false;
                Collections.swap(queue, from, to);
                mgr.moveQueueItem(from, to);
                adapter.notifyItemMoved(from, to);
                return true;
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }
        });
        helperRef[0] = touchHelper;
        touchHelper.attachToRecyclerView(rv);

        int maxRvHeight = Math.round(activity.getResources().getDisplayMetrics().heightPixels * 0.44f);
        int estimatedH = queue.size() * dp(48);
        LinearLayout rvHost = new LinearLayout(activity);
        rvHost.setOrientation(LinearLayout.VERTICAL);
        rvHost.addView(rv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                estimatedH > maxRvHeight ? maxRvHeight : ViewGroup.LayoutParams.WRAP_CONTENT));

        GlassAnchoredPopoverHelper.showPanel(
                activity,
                anchor,
                "תור ניגון (" + queue.size() + ")",
                "גרור לשינוי סדר או הקש להשמעה מיידית",
                null,
                rvHost,
                316,
                false,
                null
        );
    }

    private void showSleepTimerDialog(View anchor) {
        List<String> opts = Arrays.asList("כבה טיימר שינה", "15 דקות", "30 דקות", "45 דקות", "60 דקות");
        int[] mins = {0, 15, 30, 45, 60};
        ThemedDialogHelper.showThemedMenu(activity, anchor, "טיימר שינה",
                "האפליקציה תעצור את השמע בתום הזמן:", opts, idx -> {
                    AudioPlayerManager.getInstance().setSleepTimerMinutes(activity, mins[idx]);
                    Toast.makeText(activity, mins[idx] == 0
                                    ? "טיימר שינה כובה"
                                    : ("השמע ייעצר בעוד " + mins[idx] + " דקות"),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private static String formatEqDb(short millibels) {
        if (Math.abs(millibels) < 50) return "0 dB";
        float db = millibels / 100f;
        return (db > 0f ? "+" : "") + String.format(Locale.US, "%.1f dB", db);
    }

    private void showEqualizerDialog(View anchor) {
        final AudioPlayerManager mgr = AudioPlayerManager.getInstance();
        final int bandCount = mgr.getEqBandCount();
        if (bandCount <= 0) {
            Toast.makeText(activity, "אקולייזר זמין רק בזמן ניגון פעיל במכשיר זה", Toast.LENGTH_SHORT).show();
            return;
        }
        final int theme = PrefsManager.getThemeMode(activity);
        final int textPrimary = ThemeHelper.getPrimaryTextColor(activity, theme);
        final int textSecondary = ThemeHelper.getSecondaryTextColor(activity, theme);
        final int accent = ThemeHelper.getAccentColor(activity, theme);
        final boolean dark = ThemeHelper.isDarkSurface(activity, theme);

        ScrollView scroll = new ScrollView(activity);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        scroll.addView(panel);

        final List<String> presets = mgr.getEqPresetNames();
        final TextView[] presetChipRef = new TextView[1];

        final short minLvl = mgr.getEqMinLevel();
        final short maxLvl = mgr.getEqMaxLevel();
        final int span = Math.max(1, maxLvl - minLvl);

        final SeekBar[] bandSeekBars = new SeekBar[bandCount];
        final TextView[] bandDbViews = new TextView[bandCount];
        final SeekBar[] bassSeekRef = new SeekBar[1];
        final TextView[] bassValRef = new TextView[1];

        final Runnable syncEqViews = () -> {
            for (short b = 0; b < bandCount; b++) {
                short lvl = mgr.getEqBandLevel(b);
                if (bandSeekBars[b] != null) {
                    bandSeekBars[b].setProgress(Math.max(0, Math.min(span, lvl - minLvl)));
                }
                if (bandDbViews[b] != null) {
                    bandDbViews[b].setText(formatEqDb(lvl));
                }
            }
            short bass = mgr.getBassBoostStrength();
            if (bassSeekRef[0] != null) {
                bassSeekRef[0].setProgress(bass);
            }
            if (bassValRef[0] != null) {
                bassValRef[0].setText(Math.round(bass / 10f) + "%");
            }
            if (presetChipRef[0] != null) {
                int selPreset = mgr.getSelectedEqPresetIndex();
                String presetLabel = (selPreset >= 0 && selPreset < presets.size())
                        ? presets.get(selPreset)
                        : "ברירת מחדל / מותאם";
                presetChipRef[0].setText("פרופיל אקולייזר: " + presetLabel);
            }
        };

        if (!presets.isEmpty()) {
            LinearLayout presetBtn = new LinearLayout(activity);
            presetBtn.setOrientation(LinearLayout.HORIZONTAL);
            presetBtn.setGravity(Gravity.CENTER_VERTICAL);
            presetBtn.setPadding(dp(10), dp(8), dp(10), dp(8));
            GradientDrawable pBg = new GradientDrawable();
            pBg.setCornerRadius(dp(11));
            pBg.setColor(ColorUtils.setAlphaComponent(accent, dark ? 30 : 18));
            pBg.setStroke(Math.max(1, dp(1)), ColorUtils.setAlphaComponent(accent, 95));
            presetBtn.setBackground(pBg);

            ImageView ivTune = new ImageView(activity);
            ivTune.setImageResource(R.drawable.ic_equalizer);
            ivTune.setColorFilter(accent);
            LinearLayout.LayoutParams itLp = new LinearLayout.LayoutParams(dp(17), dp(17));
            itLp.setMarginEnd(dp(8));
            presetBtn.addView(ivTune, itLp);

            TextView tvPreset = new TextView(activity);
            tvPreset.setTextColor(textPrimary);
            tvPreset.setTextSize(ThemeHelper.spScaled(activity, 12.5f));
            tvPreset.setTypeface(Typeface.DEFAULT_BOLD);
            tvPreset.setMaxLines(1);
            tvPreset.setEllipsize(TextUtils.TruncateAt.END);
            presetBtn.addView(tvPreset, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            presetChipRef[0] = tvPreset;

            ImageView ivChev = new ImageView(activity);
            ivChev.setImageResource(R.drawable.ic_chevron_down);
            ivChev.setColorFilter(textSecondary);
            presetBtn.addView(ivChev, new LinearLayout.LayoutParams(dp(16), dp(16)));

            LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            pbLp.bottomMargin = dp(8);
            panel.addView(presetBtn, pbLp);

            presetBtn.setOnClickListener(pv -> {
                List<GlassAnchoredPopoverHelper.OptionItem> pItems = new ArrayList<>();
                int activePreset = mgr.getSelectedEqPresetIndex();
                for (int i = 0; i < presets.size(); i++) {
                    final int idx = i;
                    pItems.add(GlassAnchoredPopoverHelper.OptionItem.selectable(
                            presets.get(i),
                            R.drawable.ic_equalizer,
                            i == activePreset,
                            () -> {
                                mgr.applyEqPreset(idx);
                                syncEqViews.run();
                                Toast.makeText(activity, "הופעל פרופיל: " + presets.get(idx), Toast.LENGTH_SHORT).show();
                            }
                    ));
                }
                GlassAnchoredPopoverHelper.showMenu(activity, pv, "פרופילי אקולייזר", null, pItems, false);
            });
        }

        for (short b = 0; b < bandCount; b++) {
            final short bandIdx = b;
            LinearLayout rowHdr = new LinearLayout(activity);
            rowHdr.setOrientation(LinearLayout.HORIZONTAL);
            rowHdr.setGravity(Gravity.CENTER_VERTICAL);
            rowHdr.setPadding(dp(2), dp(6), dp(2), dp(1));

            TextView tvBand = new TextView(activity);
            tvBand.setText(mgr.getEqCenterFreqHz(bandIdx) + " Hz");
            tvBand.setTextColor(textPrimary);
            tvBand.setTextSize(ThemeHelper.spScaled(activity, 12f));
            tvBand.setTypeface(Typeface.DEFAULT_BOLD);
            rowHdr.addView(tvBand, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView tvDb = new TextView(activity);
            tvDb.setTextColor(textSecondary);
            tvDb.setTextSize(ThemeHelper.spScaled(activity, 11.5f));
            tvDb.setTypeface(Typeface.MONOSPACE);
            rowHdr.addView(tvDb);
            bandDbViews[b] = tvDb;
            panel.addView(rowHdr);

            SeekBar sb = new SeekBar(activity);
            sb.setMax(span);
            ThemeHelper.applySeekBarTint(sb, accent);
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int p, boolean fromUser) {
                    if (fromUser) {
                        short level = (short) (minLvl + p);
                        mgr.setEqBandLevel(bandIdx, level);
                        tvDb.setText(formatEqDb(level));
                        if (presetChipRef[0] != null) {
                            presetChipRef[0].setText("פרופיל אקולייזר: מותאם אישית");
                        }
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
            bandSeekBars[b] = sb;
            panel.addView(sb);
        }

        LinearLayout bassHdr = new LinearLayout(activity);
        bassHdr.setOrientation(LinearLayout.HORIZONTAL);
        bassHdr.setGravity(Gravity.CENTER_VERTICAL);
        bassHdr.setPadding(dp(2), dp(9), dp(2), dp(2));

        TextView tvBass = new TextView(activity);
        tvBass.setText("הדגשת בס (Bass Boost)");
        tvBass.setTextColor(accent);
        tvBass.setTextSize(ThemeHelper.spScaled(activity, 12.5f));
        tvBass.setTypeface(Typeface.DEFAULT_BOLD);
        bassHdr.addView(tvBass, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvBassVal = new TextView(activity);
        tvBassVal.setTextColor(accent);
        tvBassVal.setTextSize(ThemeHelper.spScaled(activity, 11.5f));
        tvBassVal.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        bassHdr.addView(tvBassVal);
        bassValRef[0] = tvBassVal;
        panel.addView(bassHdr);

        SeekBar sbBass = new SeekBar(activity);
        sbBass.setMax(1000);
        ThemeHelper.applySeekBarTint(sbBass, accent);
        sbBass.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int p, boolean fromUser) {
                if (fromUser) {
                    mgr.setBassBoostStrength((short) p);
                    tvBassVal.setText(Math.round(p / 10f) + "%");
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        bassSeekRef[0] = sbBass;
        panel.addView(sbBass);

        syncEqViews.run();

        LinearLayout resetChip = new LinearLayout(activity);
        resetChip.setOrientation(LinearLayout.HORIZONTAL);
        resetChip.setGravity(Gravity.CENTER_VERTICAL);
        resetChip.setPadding(dp(9), dp(5), dp(9), dp(5));
        GradientDrawable resetBg = new GradientDrawable();
        resetBg.setCornerRadius(dp(10));
        resetBg.setColor(ColorUtils.setAlphaComponent(accent, dark ? 34 : 20));
        resetBg.setStroke(Math.max(1, dp(1)), ColorUtils.setAlphaComponent(accent, 90));
        resetChip.setBackground(resetBg);

        ImageView ivReset = new ImageView(activity);
        ivReset.setImageResource(R.drawable.ic_reset);
        ivReset.setColorFilter(accent);
        LinearLayout.LayoutParams irLp = new LinearLayout.LayoutParams(dp(14), dp(14));
        irLp.setMarginEnd(dp(5));
        resetChip.addView(ivReset, irLp);

        TextView tvReset = new TextView(activity);
        tvReset.setText("איפוס לברירת מחדל");
        tvReset.setTextColor(accent);
        tvReset.setTextSize(ThemeHelper.spScaled(activity, 11.2f));
        tvReset.setTypeface(Typeface.DEFAULT_BOLD);
        resetChip.addView(tvReset);

        resetChip.setOnClickListener(rvClick -> {
            mgr.resetEqualizerToDefault();
            syncEqViews.run();
            Toast.makeText(activity, "האקולייזר אופס לברירת מחדל", Toast.LENGTH_SHORT).show();
        });

        GlassAnchoredPopoverHelper.showPanel(
                activity,
                anchor,
                "אקולייזר והדגשת בס",
                null,
                resetChip,
                scroll,
                320,
                false,
                null
        );
    }

    public void detachCallbacksOnly() {
        audioTickerActive = false;
        if (audioListener != null) {
            AudioPlayerManager.getInstance().removeListener(audioListener);
            audioListener = null;
        }
    }

    public void destroy() {
        detachCallbacksOnly();
        audioRootLayout = null;
        audioStagePlayerView = null;
        tvAudioCenterScrubTime = null;
    }

    private int dp(float v) {
        return ViewerUiHelper.dp(activity, v);
    }
}
