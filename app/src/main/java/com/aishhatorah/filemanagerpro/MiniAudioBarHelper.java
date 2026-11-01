package com.aishhatorah.filemanagerpro;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.AppCompatTextView;

import java.io.File;

/**
 * סעיף יב: שורת מיני-נגן קבועה בתחתית האפליקציה ביציאה מהשיר:
 * - מציגה את האייקון של השיר (במידה וקיים) ואת שם השיר
 * - מאפשרת השהיה והפעלה ישירות מהשורה
 * - לחיצה על השורה מחזירה לנגן המלא ללא הפסקת השיר
 * - החלקה אופקית על השורה עוצרת ומפסיקה את השיר מיד!
 */
public class MiniAudioBarHelper {

    public static AudioPlayerManager.PlaybackListener attach(Activity activity, LinearLayout parentContainer) {
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
        ivArt.setScaleType(ImageView.ScaleType.FIT_CENTER);
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
        textCol.addView(tvTitle);

        final TextView tvHint = new TextView(activity);
        tvHint.setText("החלק על השורה לעצירת השיר • לחץ לפתיחת הנגן");
        tvHint.setTextSize(10.5f);
        tvHint.setSingleLine(true);
        tvHint.setGravity(Gravity.RIGHT);
        textCol.addView(tvHint);

        bar.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final ImageButton btnPlayPause = new ImageButton(activity);
        btnPlayPause.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        btnPlayPause.setPadding(22, 22, 22, 22);
        LinearLayout.LayoutParams playLp = new LinearLayout.LayoutParams(104, 104);
        playLp.leftMargin = 20;
        bar.addView(btnPlayPause, playLp);

        btnPlayPause.setOnClickListener(v -> AudioPlayerManager.getInstance().togglePlayPause(activity));

        // טיפול בלחיצה לפתיחת הנגן ובהחלקה אופקית על השורה להפסקת השיר
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

        final Runnable refreshUi = () -> {
            AudioPlayerManager mgr = AudioPlayerManager.getInstance();
            if (!mgr.hasActiveSession() || mgr.getCurrentFile() == null) {
                barWrapper.setVisibility(View.GONE);
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
            tvHint.setTextColor(textSecondary);

            Bitmap art = mgr.getCurrentArtwork();
            if (art != null && !art.isRecycled()) {
                ivArt.setImageBitmap(art);
            } else {
                ivArt.setImageResource(R.drawable.ic_audio);
            }

            GradientDrawable playCircle = new GradientDrawable();
            playCircle.setShape(GradientDrawable.OVAL);
            playCircle.setColor(theme == PrefsManager.THEME_NEON ? Color.parseColor("#00B4D8") : accent);
            btnPlayPause.setBackground(playCircle);
            btnPlayPause.setImageResource(mgr.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);
            btnPlayPause.setColorFilter(theme == PrefsManager.THEME_NEON ? Color.parseColor("#030712") : Color.WHITE);

            barWrapper.setVisibility(View.VISIBLE);
        };

        AudioPlayerManager.PlaybackListener listener = refreshUi::run;
        AudioPlayerManager.getInstance().addListener(listener);
        refreshUi.run();
        return listener;
    }
}
