package com.aishhatorah.filemanagerpro;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import java.util.List;

/**
 * וילון צד זכוכיתי מונפש הנפתח מתחת לכפתור ההמבורגר, עם הנפשה חלקה לאייקון ההמבורגר עצמו,
 * תמיכה בתת-תפריט נפתח במקום (לדוגמה: מיון לפי + החלף לסדר עולה/יורד),
 * וללא כפתור "סגור" — הקשה על הרקע הכהה סוגרת את הווילון בכל מצב.
 */
public final class GlassSideDrawerHelper {

    public static final class SubOption {
        public final String label;
        public final boolean selected;
        public final Runnable onClick;

        public SubOption(String label, boolean selected, Runnable onClick) {
            this.label = label;
            this.selected = selected;
            this.onClick = onClick;
        }
    }

    public static final class DrawerItem {
        public final String title;
        public final String subtitle;
        public final int iconRes;
        public final boolean destructive;
        public final Runnable onClick;
        public final List<SubOption> subOptions;
        public final String footerActionLabel;
        public final Runnable footerAction;

        private DrawerItem(
                String title,
                String subtitle,
                int iconRes,
                boolean destructive,
                Runnable onClick,
                List<SubOption> subOptions,
                String footerActionLabel,
                Runnable footerAction
        ) {
            this.title = title;
            this.subtitle = subtitle;
            this.iconRes = iconRes;
            this.destructive = destructive;
            this.onClick = onClick;
            this.subOptions = subOptions;
            this.footerActionLabel = footerActionLabel;
            this.footerAction = footerAction;
        }

        public static DrawerItem action(String title, int iconRes, Runnable onClick) {
            return new DrawerItem(title, null, iconRes, false, onClick, null, null, null);
        }

        public static DrawerItem action(String title, String subtitle, int iconRes, Runnable onClick) {
            return new DrawerItem(title, subtitle, iconRes, false, onClick, null, null, null);
        }

        public static DrawerItem destructive(String title, int iconRes, Runnable onClick) {
            return new DrawerItem(title, null, iconRes, true, onClick, null, null, null);
        }

        public static DrawerItem expandable(
                String title,
                String subtitle,
                int iconRes,
                List<SubOption> subOptions,
                String footerActionLabel,
                Runnable footerAction
        ) {
            return new DrawerItem(title, subtitle, iconRes, false, null, subOptions, footerActionLabel, footerAction);
        }
    }

    private GlassSideDrawerHelper() {
    }

    public static void show(
            Activity activity,
            View anchor,
            String headerSubtitle,
            List<DrawerItem> items,
            boolean forceDarkGlass
    ) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed() || items == null || items.isEmpty()) {
            return;
        }
        View decor = activity.getWindow() != null ? activity.getWindow().getDecorView() : anchor;
        if (decor == null) return;

        float d = activity.getResources().getDisplayMetrics().density;
        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        int screenW = dm.widthPixels;
        int screenH = dm.heightPixels;

        int theme = PrefsManager.getThemeMode(activity);
        boolean dark = forceDarkGlass || ThemeHelper.isDarkSurface(activity, theme);
        int accent = ThemeHelper.getAccentColor(activity, theme);
        int cardBase = forceDarkGlass ? Color.parseColor("#111B2E") : ThemeHelper.getCardColor(activity, theme);
        int textPrimary = forceDarkGlass ? Color.WHITE : ThemeHelper.getPrimaryTextColor(activity, theme);
        int textSecondary = forceDarkGlass ? Color.parseColor("#94A3B8") : ThemeHelper.getSecondaryTextColor(activity, theme);
        int dividerColor = forceDarkGlass
                ? Color.argb(46, 255, 255, 255)
                : ThemeHelper.getDividerColor(activity, theme);

        int[] loc = new int[2];
        boolean anchorOnLeftSide = true;
        int topY = Math.round(64 * d);
        if (anchor != null) {
            anchor.getLocationInWindow(loc);
            int centerX = loc[0] + anchor.getWidth() / 2;
            anchorOnLeftSide = centerX < (screenW / 2);
            topY = Math.max(Math.round(28 * d), loc[1] + anchor.getHeight() + Math.round(6 * d));
        }

        int drawerW = Math.min(Math.round(286 * d), Math.round(screenW * 0.78f));
        int maxH = Math.max(Math.round(180 * d), screenH - topY - Math.round(20 * d));

        FrameLayout overlay = new FrameLayout(activity);
        overlay.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        overlay.setFocusableInTouchMode(true);

        View backdrop = new View(activity);
        backdrop.setBackgroundColor(Color.argb(120, 4, 8, 18));
        backdrop.setAlpha(0f);
        overlay.addView(backdrop, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout drawerCard = new LinearLayout(activity);
        drawerCard.setOrientation(LinearLayout.VERTICAL);
        drawerCard.setClickable(true);
        drawerCard.setPadding(Math.round(10 * d), Math.round(10 * d), Math.round(10 * d), Math.round(10 * d));

        GradientDrawable glassBg = new GradientDrawable();
        glassBg.setCornerRadius(20f * d);
        glassBg.setColor(ColorUtils.setAlphaComponent(cardBase, dark ? 232 : 242));
        glassBg.setStroke(Math.max(2, Math.round(1.2f * d)),
                ColorUtils.setAlphaComponent(dark ? Color.WHITE : accent, dark ? 58 : 75));
        drawerCard.setBackground(glassBg);
        drawerCard.setElevation(14f * d);

        if (headerSubtitle != null && !headerSubtitle.trim().isEmpty()) {
            TextView tvSub = new TextView(activity);
            tvSub.setText(headerSubtitle.trim());
            tvSub.setTextColor(textSecondary);
            tvSub.setTextSize(ThemeHelper.spScaled(activity, 11.5f));
            tvSub.setTypeface(Typeface.DEFAULT_BOLD);
            tvSub.setMaxLines(1);
            tvSub.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            tvSub.setPadding(Math.round(10 * d), Math.round(2 * d), Math.round(10 * d), Math.round(8 * d));
            drawerCard.addView(tvSub);

            View topDiv = new View(activity);
            topDiv.setBackgroundColor(dividerColor);
            drawerCard.addView(topDiv, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Math.round(1 * d))));
        }

        ScrollView scroll = new ScrollView(activity);
        scroll.setVerticalScrollBarEnabled(false);
        final LinearLayout listContainer = new LinearLayout(activity);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listContainer);

        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        drawerCard.addView(scroll, scrollLp);

        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                drawerW, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | (anchorOnLeftSide ? Gravity.LEFT : Gravity.RIGHT));
        cardLp.topMargin = topY;
        cardLp.leftMargin = Math.round(10 * d);
        cardLp.rightMargin = Math.round(10 * d);
        cardLp.bottomMargin = Math.round(16 * d);
        overlay.addView(drawerCard, cardLp);

        drawerCard.post(() -> {
            if (drawerCard.getHeight() > maxH) {
                ViewGroup.LayoutParams lp = drawerCard.getLayoutParams();
                lp.height = maxH;
                drawerCard.setLayoutParams(lp);
            }
        });

        final PopupWindow popup = new PopupWindow(
                overlay,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                true
        );
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setClippingEnabled(false);
        popup.setOutsideTouchable(true);

        final boolean[] dismissing = new boolean[]{false};
        final boolean reduceAnim = PrefsManager.isReduceAnimations(activity);
        final float slideSign = anchorOnLeftSide ? -1f : 1f;

        final Runnable dismissWithAnim = () -> {
            if (dismissing[0]) return;
            dismissing[0] = true;
            if (reduceAnim) {
                if (anchor != null) {
                    anchor.animate().cancel();
                    anchor.setRotation(0f);
                    anchor.setScaleX(1f);
                    anchor.setScaleY(1f);
                }
                try {
                    if (popup.isShowing()) popup.dismiss();
                } catch (Throwable ignored) {
                }
                return;
            }
            if (anchor != null) {
                anchor.animate().rotation(0f).scaleX(1f).scaleY(1f)
                        .setDuration(190).setInterpolator(new DecelerateInterpolator()).start();
            }
            backdrop.animate().alpha(0f).setDuration(180).start();
            drawerCard.animate()
                    .translationX(slideSign * (drawerW + 24 * d))
                    .alpha(0f)
                    .setDuration(190)
                    .setInterpolator(new DecelerateInterpolator())
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            try {
                                if (popup.isShowing()) popup.dismiss();
                            } catch (Throwable ignored) {
                            }
                        }
                    })
                    .start();
        };

        backdrop.setOnClickListener(v -> dismissWithAnim.run());
        overlay.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                dismissWithAnim.run();
                return true;
            }
            return false;
        });

        popup.setOnDismissListener(() -> {
            if (anchor != null) {
                if (reduceAnim) {
                    anchor.animate().cancel();
                    anchor.setRotation(0f);
                    anchor.setScaleX(1f);
                    anchor.setScaleY(1f);
                } else {
                    anchor.animate().rotation(0f).scaleX(1f).scaleY(1f).setDuration(150).start();
                }
            }
        });

        for (int i = 0; i < items.size(); i++) {
            final DrawerItem item = items.get(i);
            final boolean isExpandable = item.subOptions != null && !item.subOptions.isEmpty();

            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Math.round(10 * d), Math.round(11 * d), Math.round(10 * d), Math.round(11 * d));

            GradientDrawable rowBg = new GradientDrawable();
            rowBg.setCornerRadius(12f * d);
            rowBg.setColor(Color.TRANSPARENT);
            row.setBackground(rowBg);

            if (item.iconRes != 0) {
                ImageView iv = new ImageView(activity);
                iv.setImageResource(item.iconRes);
                iv.setColorFilter(item.destructive ? Color.parseColor("#EF4444") : accent);
                LinearLayout.LayoutParams ivLp = new LinearLayout.LayoutParams(
                        Math.round(20 * d), Math.round(20 * d));
                ivLp.setMarginEnd(Math.round(10 * d));
                row.addView(iv, ivLp);
            }

            LinearLayout textCol = new LinearLayout(activity);
            textCol.setOrientation(LinearLayout.VERTICAL);

            TextView tvTitle = new TextView(activity);
            tvTitle.setText(item.title);
            tvTitle.setTextColor(item.destructive ? Color.parseColor("#EF4444") : textPrimary);
            tvTitle.setTextSize(ThemeHelper.spScaled(activity, 13.5f));
            tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
            textCol.addView(tvTitle);

            if (item.subtitle != null && !item.subtitle.isEmpty()) {
                TextView tvSub = new TextView(activity);
                tvSub.setText(item.subtitle);
                tvSub.setTextColor(textSecondary);
                tvSub.setTextSize(ThemeHelper.spScaled(activity, 11f));
                textCol.addView(tvSub);
            }

            row.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            if (isExpandable) {
                final ImageView ivArrow = new ImageView(activity);
                ivArrow.setImageResource(R.drawable.ic_chevron_down);
                ivArrow.setColorFilter(textSecondary);
                row.addView(ivArrow, new LinearLayout.LayoutParams(Math.round(18 * d), Math.round(18 * d)));

                final LinearLayout subContainer = new LinearLayout(activity);
                subContainer.setOrientation(LinearLayout.VERTICAL);
                subContainer.setVisibility(View.GONE);
                subContainer.setPadding(Math.round(8 * d), Math.round(4 * d), Math.round(16 * d), Math.round(6 * d));

                GradientDrawable subBg = new GradientDrawable();
                subBg.setCornerRadius(14f * d);
                subBg.setColor(ColorUtils.setAlphaComponent(accent, dark ? 22 : 16));
                subContainer.setBackground(subBg);

                for (SubOption subOpt : item.subOptions) {
                    LinearLayout sRow = new LinearLayout(activity);
                    sRow.setOrientation(LinearLayout.HORIZONTAL);
                    sRow.setGravity(Gravity.CENTER_VERTICAL);
                    sRow.setPadding(Math.round(10 * d), Math.round(9 * d), Math.round(10 * d), Math.round(9 * d));

                    View dot = new View(activity);
                    GradientDrawable dotBg = new GradientDrawable();
                    dotBg.setShape(GradientDrawable.OVAL);
                    dotBg.setColor(subOpt.selected ? accent : ColorUtils.setAlphaComponent(textSecondary, 90));
                    dot.setBackground(dotBg);
                    LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(
                            Math.round(7 * d), Math.round(7 * d));
                    dotLp.setMarginEnd(Math.round(8 * d));
                    sRow.addView(dot, dotLp);

                    TextView tvS = new TextView(activity);
                    tvS.setText(subOpt.label);
                    tvS.setTextColor(subOpt.selected ? accent : textPrimary);
                    tvS.setTypeface(subOpt.selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                    tvS.setTextSize(ThemeHelper.spScaled(activity, 13f));
                    sRow.addView(tvS, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                    sRow.setOnClickListener(v -> {
                        dismissWithAnim.run();
                        if (subOpt.onClick != null) subOpt.onClick.run();
                    });
                    subContainer.addView(sRow);
                }

                if (item.footerActionLabel != null && item.footerAction != null) {
                    View sep = new View(activity);
                    sep.setBackgroundColor(dividerColor);
                    LinearLayout.LayoutParams sepLp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Math.round(1 * d)));
                    sepLp.topMargin = Math.round(8 * d);
                    sepLp.bottomMargin = Math.round(6 * d);
                    subContainer.addView(sep, sepLp);

                    LinearLayout footRow = new LinearLayout(activity);
                    footRow.setOrientation(LinearLayout.HORIZONTAL);
                    footRow.setGravity(Gravity.CENTER_VERTICAL);
                    footRow.setPadding(Math.round(10 * d), Math.round(9 * d), Math.round(10 * d), Math.round(9 * d));
                    GradientDrawable footBg = new GradientDrawable();
                    footBg.setCornerRadius(10f * d);
                    footBg.setColor(ColorUtils.setAlphaComponent(accent, dark ? 44 : 26));
                    footRow.setBackground(footBg);

                    TextView tvFoot = new TextView(activity);
                    tvFoot.setText(item.footerActionLabel);
                    tvFoot.setTextColor(accent);
                    tvFoot.setTypeface(Typeface.DEFAULT_BOLD);
                    tvFoot.setTextSize(ThemeHelper.spScaled(activity, 12.8f));
                    tvFoot.setGravity(Gravity.CENTER);
                    footRow.addView(tvFoot, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    footRow.setOnClickListener(v -> {
                        dismissWithAnim.run();
                        item.footerAction.run();
                    });
                    subContainer.addView(footRow);
                }

                row.setOnClickListener(v -> {
                    boolean opening = subContainer.getVisibility() != View.VISIBLE;
                    if (!reduceAnim) {
                        AutoTransition transition = new AutoTransition();
                        transition.setDuration(180);
                        TransitionManager.beginDelayedTransition(drawerCard, transition);
                        subContainer.setVisibility(opening ? View.VISIBLE : View.GONE);
                        ivArrow.animate().rotation(opening ? 180f : 0f).setDuration(180).start();
                    } else {
                        subContainer.setVisibility(opening ? View.VISIBLE : View.GONE);
                        ivArrow.setRotation(opening ? 180f : 0f);
                    }
                });

                listContainer.addView(row);
                LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                subLp.bottomMargin = Math.round(4 * d);
                listContainer.addView(subContainer, subLp);
            } else {
                row.setOnClickListener(v -> {
                    dismissWithAnim.run();
                    if (item.onClick != null) item.onClick.run();
                });
                listContainer.addView(row);
            }
        }

        if (reduceAnim) {
            drawerCard.setTranslationX(0f);
            drawerCard.setAlpha(1f);
            backdrop.setAlpha(1f);
            popup.showAtLocation(decor, Gravity.NO_GRAVITY, 0, 0);
            if (anchor != null) {
                anchor.setRotation(90f);
            }
        } else {
            drawerCard.setTranslationX(slideSign * (drawerW + 24 * d));
            drawerCard.setAlpha(0f);

            popup.showAtLocation(decor, Gravity.NO_GRAVITY, 0, 0);

            if (anchor != null) {
                anchor.animate().rotation(90f).scaleX(0.92f).scaleY(0.92f)
                        .setDuration(220).setInterpolator(new DecelerateInterpolator()).start();
            }
            backdrop.animate().alpha(1f).setDuration(200).start();
            drawerCard.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(230)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
    }
}
