package com.aishhatorah.filemanagerpro;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
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
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.AppCompatTextView;

import java.io.File;

/**
 * סעיף ז: שורת המיני-נגן החכמה למטה ביציאה מהשיר:
 * - הוסר הכיתוב "החלק על השורה לעצירת השיר • לחץ לפתיחת הנגן"
 * - במקומו מוצג פס התקדמות של השיר משמאל לימין המתעדכן בזמן אמת!
 * - החלקה אופקית לעצירת השיר ולחיצה לפתיחת הנגן נשמרות במלואן.
 */
public class MiniAudioBarHelper {

    public static AudioPlayerManager.PlaybackListener attach(Activity activity, LinearLayout parentContainer) {
        final Handler progressHandler = new Handler(Looper.getMainLooper());

        final LinearLayout barWrapper = new LinearLayout(activity);
        barWrapper.setOrientation(LinearLayout.VERTICAL);
        barWrapper.setPadding(20, 10, 20, 16);
        barWrapper.setVisibility(View.GONE);

        final LinearLayout bar = new LinearLayout(activity);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(24, 18, 24, 18);

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

        // סעיף ז: פס התקדמות של השיר משמאל לימין במקום כיתוב ההסבר
        final ProgressBar songProgressBar = new ProgressBar(
                activity, null, android.R.attr.progressBarStyleHorizontal);
        songProgressBar.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        songProgressBar.setMax(1000);
        songProgressBar.setProgress(0);
        LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(activity, 6));
        pbLp.topMargin = dpToPx(activity, 8);
        textCol.addView(songProgressBar, pbLp);

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
            private boolean swiping = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        swiping = false;
                        return true;

                    case MotionEvent.ACTION_MOVE: {
                        float dx = event.getRawX() - downRawX;
                        float dy = event.getRawY() - downRawY;
                        if (!swiping && Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) {
                            swiping = true;
                            if (v.getParent() != null) {
                                v.getParent().requestDisallowInterceptTouchEvent(true);
                            }
                        }
                        if (swiping) {
                            v.setTranslationX(dx);
                            float alpha = Math.max(0.2f, 1f - (Math.abs(dx) / Math.max(1f, v.getWidth())));
                            v.setAlpha(alpha);
                        }
                        return true;
                    }

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL: {
                        float dx = event.getRawX() - downRawX;
                        if (swiping) {
                            if (Math.abs(dx) > v.getWidth() * 0.24f) {
                                float exitX = dx > 0 ? v.getWidth() : -v.getWidth();
                                v.animate()
                                        .translationX(exitX)
                                        .alpha(0f)
                                        .setDuration(160)
                                        .withEndAction(() -> {
                                            v.setTranslationX(0f);
                                            v.setAlpha(1f);
                                            AudioPlayerManager.getInstance().stopAndDismiss(activity);
                                            Toast.makeText(activity, "ניגון השיר הופסק", Toast.LENGTH_SHORT).show();
                                        })
                                        .start();
                            } else {
                                v.animate().translationX(0f).alpha(1f).setDuration(150).start();
                            }
                        } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                            File activeSong = AudioPlayerManager.getInstance().getCurrentFile();
                            if (activeSong != null && activeSong.exists()) {
                                Intent i = new Intent(activity, BuiltInViewerActivity.class);
                                i.putExtra(BuiltInViewerActivity.EXTRA_FILE_PATH, activeSong.getAbsolutePath());
                                activity.startActivity(i);
                            }
                        }
                        swiping = false;
                        return true;
                    }
                }
                return false;
            }
        });

        barWrapper.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        parentContainer.addView(barWrapper, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final Runnable updateProgressOnly = new Runnable() {
            @Override
            public void run() {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                AudioPlayerManager mgr = AudioPlayerManager.getInstance();
                if (barWrapper.getVisibility() == View.VISIBLE && mgr.hasActiveSession()) {
                    int dur = mgr.getDurationMs();
                    int pos = mgr.getCurrentPositionMs();
                    int permille = dur > 0 ? Math.min(1000, (int) ((pos * 1000L) / dur)) : 0;
                    songProgressBar.setProgress(permille);
                    progressHandler.postDelayed(this, 250);
                }
            }
        };

        final Runnable refreshUi = () -> {
            AudioPlayerManager mgr = AudioPlayerManager.getInstance();
            if (!mgr.hasActiveSession() || mgr.getCurrentFile() == null) {
                barWrapper.setVisibility(View.GONE);
                progressHandler.removeCallbacks(updateProgressOnly);
                return;
            }

            int theme = PrefsManager.getThemeMode(activity);
            int cardColor = ThemeHelper.getCardColor(theme);
            int accent = ThemeHelper.getAccentColor(theme);
            int textPrimary = ThemeHelper.getPrimaryTextColor(theme);
            int textSecondary = ThemeHelper.getSecondaryTextColor(theme);

            barWrapper.setBackgroundColor(ThemeHelper.getBgColor(theme));

            GradientDrawable barBg = new GradientDrawable();
            barBg.setCornerRadius(28f);
            barBg.setColor(cardColor);
            barBg.setStroke(2, accent);
            bar.setBackground(barBg);
            bar.setTranslationX(0f);
            bar.setAlpha(1f);

            tvTitle.setText(mgr.getCurrentFile().getName());
            tvTitle.setTextColor(textPrimary);

            int trackTint = Color.argb(60, Color.red(textSecondary), Color.green(textSecondary), Color.blue(textSecondary));
            songProgressBar.setProgressTintList(ColorStateList.valueOf(accent));
            songProgressBar.setProgressBackgroundTintList(ColorStateList.valueOf(trackTint));
            int dur = mgr.getDurationMs();
            int pos = mgr.getCurrentPositionMs();
            songProgressBar.setProgress(dur > 0 ? Math.min(1000, (int) ((pos * 1000L) / dur)) : 0);

            Bitmap art = mgr.getCurrentArtwork();
            if (art != null && !art.isRecycled()) {
                Bitmap miniThumb = FileThumbnailLoader.createRoundedSquareBitmap(art, 128, 22f);
                ivArt.setScaleType(ImageView.ScaleType.FIT_CENTER);
                ivArt.setImageBitmap(miniThumb);
            } else {
                ivArt.setScaleType(ImageView.ScaleType.FIT_CENTER);
                ivArt.setImageResource(R.drawable.ic_audio);
            }

            GradientDrawable playCircle = new GradientDrawable();
            playCircle.setShape(GradientDrawable.OVAL);
            playCircle.setColor(theme == PrefsManager.THEME_NEON ? Color.parseColor("#00B4D8") : accent);
            btnPlayPause.setBackground(playCircle);
            btnPlayPause.setImageResource(mgr.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);
            btnPlayPause.setColorFilter(theme == PrefsManager.THEME_NEON ? Color.parseColor("#030712") : Color.WHITE);

            barWrapper.setVisibility(View.VISIBLE);
            progressHandler.removeCallbacks(updateProgressOnly);
            progressHandler.post(updateProgressOnly);
        };

        AudioPlayerManager.PlaybackListener listener = refreshUi::run;
        AudioPlayerManager.getInstance().addListener(listener);
        refreshUi.run();
        return listener;
    }

    private static int dpToPx(Activity activity, float dp) {
        return Math.round(dp * activity.getResources().getDisplayMetrics().density);
    }
}
