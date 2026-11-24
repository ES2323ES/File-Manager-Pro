package com.aishhatorah.filemanagerpro;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.graphics.ColorUtils;

import java.io.File;
import java.util.Locale;

/**
 * השורה החכמה הצפה של האודיו:
 * - צפה בשקיפות מלאה על הרקע הכללי ועל רשימות המסך
 * - מתאימה את צבעי הבועה דינאמית לעטיפת האודיו (כאשר קיימת)
 * - פס התקדמות בצורת גלים עדינים בזמן ניגון, ופס רציף בעצירה
 * - הזזת השמע קדימה/אחורה מתבצעת אך ורק מלחיצה וגרירה של הידית העבה על פס ההתקדמות
 * - החלקה אופקית על הבועה לעצירה ולחיצה או משיכה כלפי מעלה לפתיחת מסך האודיו
 */
public class MiniAudioBarHelper {

    public static AudioPlayerManager.PlaybackListener attach(Activity activity, LinearLayout parentContainer) {
        return attachInternal(activity, parentContainer, null, false);
    }

    public static AudioPlayerManager.PlaybackListener attachOverlay(
            Activity activity, FrameLayout overlayHost, View scrollableContent) {
        return attachInternal(activity, overlayHost, scrollableContent, true);
    }

    private static AudioPlayerManager.PlaybackListener attachInternal(
            Activity activity, ViewGroup hostContainer, View scrollableContent, boolean asOverlay) {
        final Handler progressHandler = new Handler(Looper.getMainLooper());

        final int basePadLeft = scrollableContent != null ? scrollableContent.getPaddingLeft() : 0;
        final int basePadTop = scrollableContent != null ? scrollableContent.getPaddingTop() : 0;
        final int basePadRight = scrollableContent != null ? scrollableContent.getPaddingRight() : 0;
        final int basePadBottom = scrollableContent != null ? scrollableContent.getPaddingBottom() : 0;
        if (scrollableContent instanceof ViewGroup) {
            ((ViewGroup) scrollableContent).setClipToPadding(false);
        }

        final LinearLayout barWrapper = new LinearLayout(activity);
        barWrapper.setOrientation(LinearLayout.VERTICAL);
        barWrapper.setBackgroundColor(Color.TRANSPARENT);
        barWrapper.setClipChildren(false);
        barWrapper.setClipToPadding(false);
        barWrapper.setPadding(dpToPx(activity, 10), dpToPx(activity, 4), dpToPx(activity, 10), dpToPx(activity, 10));
        barWrapper.setVisibility(View.GONE);

        final LinearLayout bar = new LinearLayout(activity);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(24, 14, 24, 12);
        bar.setElevation(dpToPx(activity, 8));

        final ImageView ivArt = new ImageView(activity);
        ivArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams artLp = new LinearLayout.LayoutParams(96, 96);
        artLp.rightMargin = 20;
        bar.addView(ivArt, artLp);

        LinearLayout textCol = new LinearLayout(activity);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setGravity(Gravity.RIGHT);

        final TextView tvTitle = new AppCompatTextView(activity) {
            @Override
            public boolean isFocused() {
                return true;
            }
        };
        tvTitle.setSingleLine(true);
        tvTitle.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        tvTitle.setMarqueeRepeatLimit(-1);
        tvTitle.setSelected(true);
        tvTitle.setTextSize(13.5f);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        textCol.addView(tvTitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final WavySeekBar songWavyBar = new WavySeekBar(activity);
        songWavyBar.setCompactStyle(true);
        songWavyBar.setThumbOnlyTouch(true);
        songWavyBar.setShowTickDots(false);
        songWavyBar.setMax(1000);
        songWavyBar.setProgress(0);
        songWavyBar.setPlaying(false);
        LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(activity, 22));
        pbLp.topMargin = dpToPx(activity, 4);
        textCol.addView(songWavyBar, pbLp);

        final TextView tvTime = new TextView(activity);
        tvTime.setText("00:00 / 00:00");
        tvTime.setTextSize(10.5f);
        tvTime.setTypeface(Typeface.MONOSPACE);
        tvTime.setSingleLine(true);
        tvTime.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        tvTime.setTextDirection(View.TEXT_DIRECTION_LTR);
        tvTime.setGravity(Gravity.LEFT);
        LinearLayout.LayoutParams timeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        timeLp.topMargin = dpToPx(activity, 1);
        textCol.addView(tvTime, timeLp);

        final boolean[] userScrubbing = {false};
        songWavyBar.setOnSeekBarChangeListener(new WavySeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(WavySeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    AudioPlayerManager mgr = AudioPlayerManager.getInstance();
                    int dur = mgr.getDurationMs();
                    if (dur > 0) {
                        int newPos = (int) ((progress * (long) dur) / 1000L);
                        mgr.seekTo(newPos);
                        tvTime.setText(formatClock(newPos) + " / " + formatClock(dur));
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(WavySeekBar seekBar) {
                userScrubbing[0] = true;
            }

            @Override
            public void onStopTrackingTouch(WavySeekBar seekBar) {
                userScrubbing[0] = false;
            }
        });

        bar.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final ImageButton btnPlayPause = new ImageButton(activity);
        btnPlayPause.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        btnPlayPause.setPadding(22, 22, 22, 22);
        LinearLayout.LayoutParams playLp = new LinearLayout.LayoutParams(100, 100);
        playLp.leftMargin = 20;
        bar.addView(btnPlayPause, playLp);

        btnPlayPause.setOnClickListener(v -> AudioPlayerManager.getInstance().togglePlayPause(activity));

        final int touchSlop = ViewConfiguration.get(activity).getScaledTouchSlop();
        bar.setOnTouchListener(new View.OnTouchListener() {
            private float downRawX = 0f;
            private float downRawY = 0f;
            private int swipeAxis = 0; // 0=none, 1=horizontal stop, 2=vertical pull-up

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                boolean reduce = PrefsManager.isReduceAnimations(activity);
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        swipeAxis = 0;
                        return true;

                    case MotionEvent.ACTION_MOVE: {
                        float dx = event.getRawX() - downRawX;
                        float dy = event.getRawY() - downRawY;
                        if (swipeAxis == 0) {
                            if (Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy) * 1.15f) {
                                swipeAxis = 1;
                                if (v.getParent() != null) {
                                    v.getParent().requestDisallowInterceptTouchEvent(true);
                                }
                            } else if (dy < -touchSlop && Math.abs(dy) > Math.abs(dx) * 1.2f) {
                                swipeAxis = 2;
                                if (v.getParent() != null) {
                                    v.getParent().requestDisallowInterceptTouchEvent(true);
                                }
                            }
                        }
                        if (swipeAxis == 1) {
                            v.setTranslationX(dx);
                            float alpha = Math.max(0.2f, 1f - (Math.abs(dx) / Math.max(1f, v.getWidth())));
                            v.setAlpha(alpha);
                        } else if (swipeAxis == 2) {
                            float clampedUp = Math.max(-dpToPx(activity, 56), Math.min(0f, dy * 0.55f));
                            v.setTranslationY(clampedUp);
                            v.setAlpha(Math.max(0.75f, 1f - Math.abs(clampedUp) / dpToPx(activity, 180)));
                        }
                        return true;
                    }

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL: {
                        float dx = event.getRawX() - downRawX;
                        float dy = event.getRawY() - downRawY;
                        if (swipeAxis == 1) {
                            if (Math.abs(dx) > v.getWidth() * 0.24f) {
                                if (reduce) {
                                    v.setTranslationX(0f);
                                    v.setAlpha(1f);
                                    AudioPlayerManager.getInstance().stopAndDismiss(activity);
                                    Toast.makeText(activity, "ניגון השיר הופסק", Toast.LENGTH_SHORT).show();
                                } else {
                                    float exitX = dx > 0 ? v.getWidth() : -v.getWidth();
                                    v.animate()
                                            .translationX(exitX)
                                            .alpha(0f)
                                            .setDuration(170)
                                            .withEndAction(() -> {
                                                v.setTranslationX(0f);
                                                v.setAlpha(1f);
                                                AudioPlayerManager.getInstance().stopAndDismiss(activity);
                                                Toast.makeText(activity, "ניגון השיר הופסק", Toast.LENGTH_SHORT).show();
                                            })
                                            .start();
                                }
                            } else {
                                if (reduce) {
                                    v.setTranslationX(0f);
                                    v.setAlpha(1f);
                                } else {
                                    v.animate().translationX(0f).alpha(1f).setDuration(160).start();
                                }
                            }
                        } else if (swipeAxis == 2) {
                            if (reduce) {
                                v.setTranslationY(0f);
                                v.setAlpha(1f);
                            } else {
                                v.animate().translationY(0f).alpha(1f).setDuration(150).start();
                            }
                            if (event.getActionMasked() == MotionEvent.ACTION_UP && dy < -dpToPx(activity, 24)) {
                                openFullAudioViewer(activity, reduce);
                            }
                        } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                            openFullAudioViewer(activity, reduce);
                        }
                        swipeAxis = 0;
                        return true;
                    }
                }
                return false;
            }
        });

        barWrapper.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (asOverlay) {
            FrameLayout.LayoutParams overlayLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
            hostContainer.addView(barWrapper, overlayLp);
        } else {
            hostContainer.addView(barWrapper, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        final Runnable updateProgressOnly = new Runnable() {
            @Override
            public void run() {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                AudioPlayerManager mgr = AudioPlayerManager.getInstance();
                if (barWrapper.getVisibility() == View.VISIBLE && mgr.hasActiveSession()) {
                    songWavyBar.setAnimationsEnabled(!PrefsManager.isReduceAnimations(activity));
                    songWavyBar.setPlaying(mgr.isPlaying());
                    if (!userScrubbing[0]) {
                        int dur = mgr.getDurationMs();
                        int pos = mgr.getCurrentPositionMs();
                        int permille = dur > 0 ? Math.min(1000, (int) ((pos * 1000L) / dur)) : 0;
                        songWavyBar.setProgress(permille);
                        tvTime.setText(formatClock(pos) + " / " + formatClock(dur));
                    }
                    progressHandler.postDelayed(this, 220);
                }
            }
        };

        final Runnable updateScrollPadding = () -> {
            if (scrollableContent == null) return;
            if (barWrapper.getVisibility() == View.VISIBLE) {
                int h = barWrapper.getHeight();
                if (h <= 0) {
                    h = dpToPx(activity, 94);
                }
                scrollableContent.setPadding(basePadLeft, basePadTop, basePadRight, basePadBottom + h);
            } else {
                scrollableContent.setPadding(basePadLeft, basePadTop, basePadRight, basePadBottom);
            }
        };

        final Runnable refreshUi = () -> {
            AudioPlayerManager mgr = AudioPlayerManager.getInstance();
            if (!mgr.hasActiveSession() || mgr.getCurrentFile() == null) {
                barWrapper.setVisibility(View.GONE);
                progressHandler.removeCallbacks(updateProgressOnly);
                updateScrollPadding.run();
                return;
            }
            boolean wasHidden = barWrapper.getVisibility() != View.VISIBLE;
            boolean reduce = PrefsManager.isReduceAnimations(activity);

            int theme = PrefsManager.getThemeMode(activity);
            int cardColor = ThemeHelper.getCardColor(activity, theme);
            int defaultAccent = ThemeHelper.getAccentColor(activity, theme);
            int defaultTextPrimary = ThemeHelper.getPrimaryTextColor(activity, theme);
            int defaultTextSecondary = ThemeHelper.getSecondaryTextColor(activity, theme);

            Bitmap art = mgr.getCurrentArtwork();
            AudioPaletteHelper.AudioAdaptivePalette pal = AudioPaletteHelper.computePalette(
                    activity, art, theme, cardColor, defaultTextPrimary, defaultTextSecondary, defaultAccent);

            int barBgColor = (art != null && !art.isRecycled()) ? pal.bg : cardColor;
            int activeAccent = (art != null && !art.isRecycled()) ? pal.accent : defaultAccent;
            int activeTextPrimary = (art != null && !art.isRecycled()) ? pal.textPrimary : defaultTextPrimary;
            int activeTextSecondary = (art != null && !art.isRecycled()) ? pal.textSecondary : defaultTextSecondary;
            int playIconTint = (art != null && !art.isRecycled()) ? pal.playIconTint
                    : ThemeHelper.getOnAccentTextColor(defaultAccent);

            barWrapper.setBackgroundColor(Color.TRANSPARENT);

            GradientDrawable barBg = new GradientDrawable();
            barBg.setCornerRadius(28f);
            barBg.setColor(barBgColor);
            barBg.setStroke(2, activeAccent);
            bar.setBackground(barBg);
            bar.setTranslationX(0f);
            bar.setTranslationY(0f);
            bar.setAlpha(1f);

            tvTitle.setText(mgr.getCurrentFile().getName());
            tvTitle.setTextColor(activeTextPrimary);

            int trackTint = ColorUtils.setAlphaComponent(activeTextSecondary, 75);
            songWavyBar.setAnimationsEnabled(!reduce);
            songWavyBar.setColors(activeAccent, trackTint, activeAccent);
            songWavyBar.setPlaying(mgr.isPlaying());
            int dur = mgr.getDurationMs();
            int pos = mgr.getCurrentPositionMs();
            songWavyBar.setProgress(dur > 0 ? Math.min(1000, (int) ((pos * 1000L) / dur)) : 0);
            tvTime.setText(formatClock(pos) + " / " + formatClock(dur));
            tvTime.setTextColor(activeTextSecondary);

            ivArt.clearColorFilter();
            if (art != null && !art.isRecycled()) {
                Bitmap miniThumb = FileThumbnailLoader.createRoundedSquareBitmap(art, 128, 22f);
                ivArt.setScaleType(ImageView.ScaleType.FIT_CENTER);
                ivArt.setImageBitmap(miniThumb);
            } else {
                ivArt.setScaleType(ImageView.ScaleType.FIT_CENTER);
                ivArt.setImageResource(R.drawable.ic_audio);
                ivArt.setColorFilter(activeAccent);
            }

            GradientDrawable playCircle = new GradientDrawable();
            playCircle.setShape(GradientDrawable.OVAL);
            playCircle.setColor(activeAccent);
            btnPlayPause.setBackground(playCircle);
            btnPlayPause.setImageResource(mgr.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);
            btnPlayPause.setColorFilter(playIconTint);

            barWrapper.setVisibility(View.VISIBLE);
            if (wasHidden) {
                if (reduce) {
                    barWrapper.setTranslationY(0f);
                    barWrapper.setAlpha(1f);
                } else {
                    barWrapper.setTranslationY(dpToPx(activity, 28));
                    barWrapper.setAlpha(0f);
                    barWrapper.animate().translationY(0f).alpha(1f).setDuration(220).start();
                }
            }
            barWrapper.post(updateScrollPadding);
            progressHandler.removeCallbacks(updateProgressOnly);
            progressHandler.post(updateProgressOnly);
        };

        AudioPlayerManager.PlaybackListener listener = refreshUi::run;
        AudioPlayerManager.getInstance().addListener(listener);
        refreshUi.run();
        return listener;
    }

    private static void openFullAudioViewer(Activity activity, boolean reduceAnimations) {
        File activeSong = AudioPlayerManager.getInstance().getCurrentFile();
        if (activeSong != null && activeSong.exists()) {
            Intent i = new Intent(activity, BuiltInViewerActivity.class);
            i.putExtra(BuiltInViewerActivity.EXTRA_FILE_PATH, activeSong.getAbsolutePath());
            i.putExtra(BuiltInViewerActivity.EXTRA_FROM_MINI_AUDIO_BAR, true);
            activity.startActivity(i);
            if (reduceAnimations) {
                activity.overridePendingTransition(0, 0);
            } else {
                activity.overridePendingTransition(R.anim.slide_in_bottom, R.anim.hold_stay);
            }
        }
    }

    private static String formatClock(int ms) {
        int sec = Math.max(0, ms / 1000);
        return String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60);
    }

    private static int dpToPx(Activity activity, float dp) {
        return Math.round(dp * activity.getResources().getDisplayMetrics().density);
    }
}
