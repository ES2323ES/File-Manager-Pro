package com.aishhatorah.filemanagerpro;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
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
 * חלונית אפשרויות זכוכיתית קומפקטית הנפתחת מהאלמנט שלחצו עליו (מעל או מתחת בהתאם למקום הפנוי במסך)
 * בסגנון הווילון הצדדי המרחף — ללא כפתורי "סגור" מגושמים ועם סגירה בהקשה על הרקע.
 */
public final class GlassAnchoredPopoverHelper {

    public interface PopoverHandle {
        void dismiss();
    }

    public static final class OptionItem {
        public final String title;
        public final String subtitle;
        public final int iconRes;
        public final boolean selected;
        public final boolean destructive;
        public final boolean dividerBefore;
        public final Runnable onClick;

        public OptionItem(
                String title,
                String subtitle,
                int iconRes,
                boolean selected,
                boolean destructive,
                boolean dividerBefore,
                Runnable onClick
        ) {
            this.title = title;
            this.subtitle = subtitle;
            this.iconRes = iconRes;
            this.selected = selected;
            this.destructive = destructive;
            this.dividerBefore = dividerBefore;
            this.onClick = onClick;
        }

        public static OptionItem of(String title, int iconRes, Runnable onClick) {
            return new OptionItem(title, null, iconRes, false, false, false, onClick);
        }

        public static OptionItem selectable(String title, int iconRes, boolean selected, Runnable onClick) {
            return new OptionItem(title, null, iconRes, selected, false, false, onClick);
        }

        public static OptionItem destructive(String title, int iconRes, boolean dividerBefore, Runnable onClick) {
            return new OptionItem(title, null, iconRes, false, true, dividerBefore, onClick);
        }
    }

    private GlassAnchoredPopoverHelper() {
    }

    public static void showMenu(
            Activity activity,
            View anchor,
            String title,
            String subtitle,
            List<OptionItem> items,
            boolean forceDarkGlass
    ) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed() || items == null || items.isEmpty()) {
            return;
        }
        int theme = PrefsManager.getThemeMode(activity);
        boolean dark = forceDarkGlass || ThemeHelper.isDarkSurface(activity, theme);
        int accent = ThemeHelper.getAccentColor(activity, theme);
        int textPrimary = forceDarkGlass ? Color.WHITE : ThemeHelper.getPrimaryTextColor(activity, theme);
        int textSecondary = forceDarkGlass ? Color.parseColor("#94A3B8") : ThemeHelper.getSecondaryTextColor(activity, theme);
        int dividerColor = forceDarkGlass
                ? Color.argb(42, 255, 255, 255)
                : ThemeHelper.getDividerColor(activity, theme);
        float d = activity.getResources().getDisplayMetrics().density;

        ScrollView scroll = new ScrollView(activity);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        scroll.addView(list);

        final PopoverHandle[] handleRef = new PopoverHandle[1];

        for (int i = 0; i < items.size(); i++) {
            final OptionItem item = items.get(i);
            if (item.dividerBefore && i > 0) {
                View sep = new View(activity);
                sep.setBackgroundColor(dividerColor);
                LinearLayout.LayoutParams sepLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Math.round(1 * d)));
                sepLp.topMargin = Math.round(4 * d);
                sepLp.bottomMargin = Math.round(4 * d);
                list.addView(sep, sepLp);
            }

            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Math.round(10 * d), Math.round(9.5f * d), Math.round(10 * d), Math.round(9.5f * d));

            GradientDrawable rowBg = new GradientDrawable();
            rowBg.setCornerRadius(12f * d);
            if (item.destructive) {
                rowBg.setColor(Color.argb(dark ? 28 : 16, 220, 38, 38));
            } else if (item.selected) {
                rowBg.setColor(ColorUtils.setAlphaComponent(accent, dark ? 44 : 24));
                rowBg.setStroke(Math.max(1, Math.round(1 * d)), ColorUtils.setAlphaComponent(accent, 110));
            } else {
                rowBg.setColor(Color.TRANSPARENT);
            }
            row.setBackground(rowBg);

            if (item.iconRes != 0) {
                ImageView iv = new ImageView(activity);
                iv.setImageResource(item.iconRes);
                int iconColor = item.destructive
                        ? (dark ? Color.parseColor("#F87171") : Color.parseColor("#DC2626"))
                        : (item.selected ? accent : (dark ? ColorUtils.setAlphaComponent(accent, 225) : accent));
                iv.setColorFilter(iconColor);
                LinearLayout.LayoutParams ivLp = new LinearLayout.LayoutParams(
                        Math.round(18.5f * d), Math.round(18.5f * d));
                ivLp.setMarginEnd(Math.round(10 * d));
                row.addView(iv, ivLp);
            }

            LinearLayout textCol = new LinearLayout(activity);
            textCol.setOrientation(LinearLayout.VERTICAL);

            TextView tvTitle = new TextView(activity);
            tvTitle.setText(item.title);
            tvTitle.setTextColor(item.destructive
                    ? (dark ? Color.parseColor("#FCA5A5") : Color.parseColor("#DC2626"))
                    : (item.selected ? accent : textPrimary));
            tvTitle.setTextSize(ThemeHelper.spScaled(activity, 13.2f));
            tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
            tvTitle.setMaxLines(2);
            tvTitle.setEllipsize(TextUtils.TruncateAt.END);
            textCol.addView(tvTitle);

            if (item.subtitle != null && !item.subtitle.isEmpty()) {
                TextView tvSub = new TextView(activity);
                tvSub.setText(item.subtitle);
                tvSub.setTextColor(textSecondary);
                tvSub.setTextSize(ThemeHelper.spScaled(activity, 11f));
                tvSub.setMaxLines(1);
                tvSub.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                textCol.addView(tvSub);
            }

            row.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            if (item.selected) {
                ImageView checkIv = new ImageView(activity);
                checkIv.setImageResource(R.drawable.ic_check);
                checkIv.setColorFilter(accent);
                LinearLayout.LayoutParams chkLp = new LinearLayout.LayoutParams(
                        Math.round(16 * d), Math.round(16 * d));
                chkLp.setMarginStart(Math.round(6 * d));
                row.addView(checkIv, chkLp);
            }

            LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rLp.bottomMargin = Math.round(2.5f * d);
            row.setLayoutParams(rLp);

            row.setOnClickListener(v -> {
                if (handleRef[0] != null) handleRef[0].dismiss();
                if (item.onClick != null) item.onClick.run();
            });
            list.addView(row);
        }

        handleRef[0] = showPanel(activity, anchor, title, subtitle, null, scroll, 296, forceDarkGlass, null);
    }

    public static PopoverHandle showPanel(
            Activity activity,
            View anchor,
            String title,
            String subtitle,
            View headerRightAction,
            View customContent,
            int preferredMaxWidthDp,
            boolean forceDarkGlass,
            Runnable onDismissCallback
    ) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed() || customContent == null) {
            return () -> { };
        }
        View decor = activity.getWindow() != null ? activity.getWindow().getDecorView() : anchor;
        if (decor == null) return () -> { };

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
                ? Color.argb(44, 255, 255, 255)
                : ThemeHelper.getDividerColor(activity, theme);

        final int panelW = Math.min(Math.round(preferredMaxWidthDp * d), Math.round(screenW * 0.88f));
        final int maxPanelH = Math.round(screenH * 0.62f);

        FrameLayout overlay = new FrameLayout(activity);
        overlay.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        overlay.setFocusableInTouchMode(true);

        View backdrop = new View(activity);
        backdrop.setBackgroundColor(Color.argb(112, 4, 8, 18));
        backdrop.setAlpha(0f);
        overlay.addView(backdrop, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        card.setClickable(true);
        card.setPadding(Math.round(12 * d), Math.round(11 * d), Math.round(12 * d), Math.round(11 * d));

        GradientDrawable glassBg = new GradientDrawable();
        glassBg.setCornerRadius(20f * d);
        glassBg.setColor(ColorUtils.setAlphaComponent(cardBase, dark ? 236 : 244));
        glassBg.setStroke(Math.max(2, Math.round(1.2f * d)),
                ColorUtils.setAlphaComponent(dark ? Color.WHITE : accent, dark ? 56 : 75));
        card.setBackground(glassBg);
        card.setElevation(16f * d);

        boolean hasHeader = (title != null && !title.trim().isEmpty())
                || (subtitle != null && !subtitle.trim().isEmpty())
                || headerRightAction != null;
        if (hasHeader) {
            LinearLayout headerRow = new LinearLayout(activity);
            headerRow.setOrientation(LinearLayout.HORIZONTAL);
            headerRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            headerRow.setGravity(Gravity.CENTER_VERTICAL);
            headerRow.setPadding(Math.round(4 * d), 0, Math.round(4 * d), Math.round(7 * d));

            LinearLayout titleCol = new LinearLayout(activity);
            titleCol.setOrientation(LinearLayout.VERTICAL);
            if (title != null && !title.trim().isEmpty()) {
                TextView tvT = new TextView(activity);
                tvT.setText(title.trim());
                tvT.setTextColor(textPrimary);
                tvT.setTextSize(ThemeHelper.spScaled(activity, 14.5f));
                tvT.setTypeface(Typeface.DEFAULT_BOLD);
                tvT.setMaxLines(1);
                tvT.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                titleCol.addView(tvT);
            }
            if (subtitle != null && !subtitle.trim().isEmpty()) {
                TextView tvS = new TextView(activity);
                tvS.setText(subtitle.trim());
                tvS.setTextColor(textSecondary);
                tvS.setTextSize(ThemeHelper.spScaled(activity, 11f));
                tvS.setMaxLines(2);
                tvS.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                titleCol.addView(tvS);
            }
            headerRow.addView(titleCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            if (headerRightAction != null) {
                LinearLayout.LayoutParams actLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                actLp.setMarginStart(Math.round(8 * d));
                headerRow.addView(headerRightAction, actLp);
            }

            card.addView(headerRow);

            View topDiv = new View(activity);
            topDiv.setBackgroundColor(dividerColor);
            LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Math.round(1 * d)));
            divLp.bottomMargin = Math.round(6 * d);
            card.addView(topDiv, divLp);
        }

        if (customContent.getParent() instanceof ViewGroup) {
            ((ViewGroup) customContent.getParent()).removeView(customContent);
        }
        card.addView(customContent, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        int wSpec = View.MeasureSpec.makeMeasureSpec(panelW, View.MeasureSpec.EXACTLY);
        int hSpec = View.MeasureSpec.makeMeasureSpec(maxPanelH, View.MeasureSpec.AT_MOST);
        card.measure(wSpec, hSpec);
        int measuredH = Math.min(maxPanelH, Math.max(Math.round(72 * d), card.getMeasuredHeight()));

        int sidePad = Math.round(10 * d);
        int safeTop = Math.round(28 * d);
        int safeBottom = Math.round(14 * d);
        int gap = Math.round(6 * d);

        int cardX;
        int cardY;
        float pivotX;
        float pivotY;
        boolean opensAbove = false;

        boolean validAnchor = anchor != null && anchor.isAttachedToWindow()
                && anchor.getWidth() > 0 && anchor.getHeight() > 0;
        if (validAnchor) {
            int[] aLoc = new int[2];
            int[] dLoc = new int[2];
            anchor.getLocationInWindow(aLoc);
            decor.getLocationInWindow(dLoc);
            int relLeft = aLoc[0] - dLoc[0];
            int relTop = aLoc[1] - dLoc[1];
            int anchorW = anchor.getWidth();
            int anchorH = anchor.getHeight();
            int anchorCenterX = relLeft + anchorW / 2;

            cardX = Math.max(sidePad, Math.min(screenW - panelW - sidePad, anchorCenterX - panelW / 2));

            int spaceBelow = (screenH - safeBottom) - (relTop + anchorH + gap);
            int spaceAbove = (relTop - gap) - safeTop;
            if (spaceBelow >= measuredH || spaceBelow >= spaceAbove) {
                int availH = Math.max(Math.round(120 * d), Math.min(maxPanelH, spaceBelow));
                measuredH = Math.min(measuredH, availH);
                cardY = Math.max(safeTop, Math.min(screenH - safeBottom - measuredH, relTop + anchorH + gap));
                opensAbove = false;
            } else {
                int availH = Math.max(Math.round(120 * d), Math.min(maxPanelH, spaceAbove));
                measuredH = Math.min(measuredH, availH);
                cardY = Math.max(safeTop, relTop - gap - measuredH);
                opensAbove = true;
            }
            pivotX = Math.max(Math.round(18 * d), Math.min(panelW - Math.round(18 * d), anchorCenterX - cardX));
            pivotY = opensAbove ? measuredH : 0f;
        } else {
            cardX = Math.max(sidePad, (screenW - panelW) / 2);
            cardY = Math.max(safeTop, (screenH - measuredH) / 2);
            pivotX = panelW * 0.5f;
            pivotY = measuredH * 0.5f;
        }

        card.setPivotX(pivotX);
        card.setPivotY(pivotY);

        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                panelW,
                card.getMeasuredHeight() > measuredH ? measuredH : ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.LEFT
        );
        cardLp.leftMargin = cardX;
        cardLp.topMargin = cardY;
        overlay.addView(card, cardLp);

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
        final float enterDy = validAnchor ? (opensAbove ? Math.round(10 * d) : -Math.round(10 * d)) : Math.round(8 * d);

        final Runnable dismissAction = () -> {
            if (dismissing[0]) return;
            dismissing[0] = true;
            if (reduceAnim) {
                try {
                    if (popup.isShowing()) popup.dismiss();
                } catch (Throwable ignored) {
                }
                return;
            }
            backdrop.animate().alpha(0f).setDuration(150).start();
            card.animate()
                    .scaleX(0.88f)
                    .scaleY(0.86f)
                    .translationY(enterDy * 0.7f)
                    .alpha(0f)
                    .setDuration(155)
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

        backdrop.setOnClickListener(v -> dismissAction.run());
        overlay.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                dismissAction.run();
                return true;
            }
            return false;
        });
        if (onDismissCallback != null) {
            popup.setOnDismissListener(onDismissCallback::run);
        }

        if (reduceAnim) {
            card.setScaleX(1f);
            card.setScaleY(1f);
            card.setTranslationY(0f);
            card.setAlpha(1f);
            backdrop.setAlpha(1f);
            popup.showAtLocation(decor, Gravity.NO_GRAVITY, 0, 0);
        } else {
            card.setScaleX(0.86f);
            card.setScaleY(0.82f);
            card.setTranslationY(enterDy);
            card.setAlpha(0f);
            popup.showAtLocation(decor, Gravity.NO_GRAVITY, 0, 0);

            backdrop.animate().alpha(1f).setDuration(175).start();
            card.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(205)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }

        return dismissAction::run;
    }
}
