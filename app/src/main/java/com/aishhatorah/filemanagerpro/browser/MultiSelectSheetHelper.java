package com.aishhatorah.filemanagerpro.browser;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import com.aishhatorah.filemanagerpro.PrefsManager;
import com.aishhatorah.filemanagerpro.R;
import com.aishhatorah.filemanagerpro.ThemeHelper;

/**
 * וילון פעולות תחתון לבחירה מרובה הניתן להרחבה וצמצום בגרירה אנכית חלקה או בהקשה על הידית.
 */
public final class MultiSelectSheetHelper {

    public interface ActionsListener {
        void onCopy();
        void onMove();
        void onShare();
        void onCompress();
        void onBatchRename();
        void onDelete();
    }

    private final Context context;
    private final int theme;
    private final int cardColor;
    private final int textPrimary;
    private final int textSecondary;
    private final int accent;
    private final ActionsListener actionsListener;

    private LinearLayout rootSheet;
    private ScrollView multiSelectScroll;
    private boolean expanded = false;
    private ValueAnimator heightAnimator;
    private ValueAnimator peekAnimator;

    public MultiSelectSheetHelper(
            Context context,
            int theme,
            int cardColor,
            int textPrimary,
            int textSecondary,
            int accent,
            ActionsListener actionsListener
    ) {
        this.context = context;
        this.theme = theme;
        this.cardColor = cardColor;
        this.textPrimary = textPrimary;
        this.textSecondary = textSecondary;
        this.accent = accent;
        this.actionsListener = actionsListener;
    }

    public LinearLayout createSheet() {
        boolean isDark = ThemeHelper.isDarkSurface(context, theme);
        final int collapsedHeight = dp(100);
        final int expandedHeight = dp(178);
        final int touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        final FrameLayout handleWrap = new FrameLayout(context);
        handleWrap.setPadding(0, dp(2), 0, dp(8));
        View dragPill = new View(context);
        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setCornerRadius(dp(3));
        pillBg.setColor(ColorUtils.setAlphaComponent(textSecondary, 110));
        dragPill.setBackground(pillBg);
        handleWrap.addView(dragPill, new FrameLayout.LayoutParams(dp(40), dp(4), Gravity.CENTER));

        LinearLayout sheet = new LinearLayout(context) {
            private float dragStartRawY;
            private float dragStartX;
            private int dragStartHeight;
            private boolean isDraggingSheet = false;
            private boolean startedOnHandle = false;

            private void initDown(MotionEvent ev) {
                dragStartRawY = ev.getRawY();
                dragStartX = ev.getRawX();
                if (multiSelectScroll != null && multiSelectScroll.getLayoutParams() != null) {
                    dragStartHeight = multiSelectScroll.getHeight() > 0
                            ? multiSelectScroll.getHeight()
                            : multiSelectScroll.getLayoutParams().height;
                } else {
                    dragStartHeight = collapsedHeight;
                }
                isDraggingSheet = false;
                startedOnHandle = ev.getY() <= handleWrap.getBottom() + dp(6);
            }

            private boolean shouldStartSheetDrag(float dx, float dy) {
                if (Math.abs(dy) <= touchSlop || Math.abs(dy) < Math.abs(dx) * 0.75f) {
                    return false;
                }
                if (startedOnHandle) return true;
                if (!expanded && dy < 0f) return true;
                return expanded && dy > 0f
                        && (multiSelectScroll == null || multiSelectScroll.getScrollY() <= 0);
            }

            private void beginSheetDrag(MotionEvent ev) {
                isDraggingSheet = true;
                cancelAnimators();
                if (multiSelectScroll != null && multiSelectScroll.getLayoutParams() != null) {
                    dragStartHeight = multiSelectScroll.getHeight() > 0
                            ? multiSelectScroll.getHeight()
                            : multiSelectScroll.getLayoutParams().height;
                }
                dragStartRawY = ev.getRawY();
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
            }

            private void updateSheetDrag(float currentRawY) {
                if (multiSelectScroll == null) return;
                ViewGroup.LayoutParams lp = multiSelectScroll.getLayoutParams();
                if (lp == null) return;
                float dy = currentRawY - dragStartRawY;
                int newH = Math.max(collapsedHeight, Math.min(expandedHeight, Math.round(dragStartHeight - dy)));
                if (lp.height != newH) {
                    lp.height = newH;
                    multiSelectScroll.setLayoutParams(lp);
                }
                if (multiSelectScroll.getScrollY() != 0) {
                    multiSelectScroll.scrollTo(0, 0);
                }
            }

            private void finishSheetDrag(float totalDy) {
                isDraggingSheet = false;
                if (multiSelectScroll == null || multiSelectScroll.getLayoutParams() == null) return;
                int curH = multiSelectScroll.getLayoutParams().height;
                boolean expand;
                if (totalDy < -dp(20)) {
                    expand = true;
                } else if (totalDy > dp(20)) {
                    expand = false;
                } else {
                    expand = curH >= (collapsedHeight + expandedHeight) / 2;
                }
                setExpanded(expand);
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                int action = ev.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    initDown(ev);
                    return false;
                }
                if (action == MotionEvent.ACTION_MOVE) {
                    if (!isDraggingSheet) {
                        float dx = ev.getRawX() - dragStartX;
                        float dy = ev.getRawY() - dragStartRawY;
                        if (shouldStartSheetDrag(dx, dy)) {
                            beginSheetDrag(ev);
                            return true;
                        }
                    }
                    return isDraggingSheet;
                }
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    if (isDraggingSheet) {
                        finishSheetDrag(ev.getRawY() - dragStartRawY);
                        return true;
                    }
                }
                return false;
            }

            @Override
            public boolean onTouchEvent(MotionEvent ev) {
                int action = ev.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    initDown(ev);
                    return true;
                }
                if (action == MotionEvent.ACTION_MOVE) {
                    if (!isDraggingSheet) {
                        float dx = ev.getRawX() - dragStartX;
                        float dy = ev.getRawY() - dragStartRawY;
                        if (shouldStartSheetDrag(dx, dy)) {
                            beginSheetDrag(ev);
                        }
                    }
                    if (isDraggingSheet) {
                        updateSheetDrag(ev.getRawY());
                    }
                    return true;
                }
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    if (isDraggingSheet) {
                        finishSheetDrag(ev.getRawY() - dragStartRawY);
                    } else if (action == MotionEvent.ACTION_UP && startedOnHandle) {
                        toggle();
                    }
                    return true;
                }
                return super.onTouchEvent(ev);
            }
        };
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        float topCorner = dp(24);
        GradientDrawable sheetBg = new GradientDrawable();
        sheetBg.setCornerRadii(new float[]{topCorner, topCorner, topCorner, topCorner, 0f, 0f, 0f, 0f});
        int surfaceTint = ColorUtils.blendARGB(ThemeHelper.getTopBarColor(context, theme), accent, isDark ? 0.08f : 0.05f);
        sheetBg.setColor(ColorUtils.setAlphaComponent(surfaceTint, isDark ? 242 : 246));
        sheetBg.setStroke(Math.max(1, dp(1)), ColorUtils.setAlphaComponent(accent, 95));
        sheet.setBackground(sheetBg);
        sheet.setElevation(dp(12));
        sheet.setPadding(dp(12), dp(8), dp(12), dp(12));

        handleWrap.setOnClickListener(v -> toggle());
        sheet.addView(handleWrap, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        multiSelectScroll = new ScrollView(context);
        multiSelectScroll.setVerticalScrollBarEnabled(false);
        multiSelectScroll.setOverScrollMode(View.OVER_SCROLL_ALWAYS);

        LinearLayout rowsHolder = new LinearLayout(context);
        rowsHolder.setOrientation(LinearLayout.VERTICAL);
        rowsHolder.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout row1 = new LinearLayout(context);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setWeightSum(3f);
        row1.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row1.addView(createActionCard("העתק", R.drawable.ic_copy, accent, v -> actionsListener.onCopy()));
        row1.addView(createActionCard("העבר", R.drawable.ic_cut, accent, v -> actionsListener.onMove()));
        row1.addView(createActionCard("שתף", R.drawable.ic_share, accent, v -> actionsListener.onShare()));
        rowsHolder.addView(row1, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout row2 = new LinearLayout(context);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setWeightSum(3f);
        row2.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row2.addView(createActionCard("דחיסה", R.drawable.ic_compress_chip, accent, v -> actionsListener.onCompress()));
        row2.addView(createActionCard("שינוי שם", R.drawable.ic_edit_pencil, accent, v -> actionsListener.onBatchRename()));
        row2.addView(createActionCard("מחק", R.drawable.ic_delete, Color.parseColor("#EF4444"), v -> actionsListener.onDelete()));
        LinearLayout.LayoutParams r2Lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        r2Lp.topMargin = dp(8);
        rowsHolder.addView(row2, r2Lp);

        multiSelectScroll.addView(rowsHolder, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        sheet.addView(multiSelectScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, collapsedHeight));

        rootSheet = sheet;
        return sheet;
    }

    private LinearLayout createActionCard(String label, int iconRes, int iconTint, View.OnClickListener l) {
        boolean isDark = ThemeHelper.isDarkSurface(context, theme);
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(6), dp(12), dp(6), dp(12));

        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(dp(16));
        gd.setColor(ColorUtils.setAlphaComponent(cardColor, isDark ? 210 : 230));
        gd.setStroke(Math.max(1, dp(1)), ColorUtils.setAlphaComponent(iconTint, 70));
        card.setBackground(gd);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(82), 1f);
        lp.setMarginStart(dp(4));
        lp.setMarginEnd(dp(4));
        card.setLayoutParams(lp);

        FrameLayout iconBadge = new FrameLayout(context);
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setShape(GradientDrawable.OVAL);
        badgeBg.setColor(ColorUtils.setAlphaComponent(iconTint, isDark ? 36 : 26));
        iconBadge.setBackground(badgeBg);

        ImageView iv = new ImageView(context);
        iv.setImageResource(iconRes);
        iv.setColorFilter(iconTint);
        iconBadge.addView(iv, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        card.addView(iconBadge, new LinearLayout.LayoutParams(dp(36), dp(36)));

        TextView tv = new TextView(context);
        tv.setText(label);
        tv.setTextColor(textPrimary);
        tv.setTextSize(12f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setMaxLines(1);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tvLp.topMargin = dp(6);
        card.addView(tv, tvLp);

        card.setOnClickListener(l);
        return card;
    }

    public void resetCollapsedAndAnimateEntrance(final IsActiveCheck activeCheck) {
        cancelAnimators();
        expanded = false;
        if (multiSelectScroll != null) {
            ViewGroup.LayoutParams lp = multiSelectScroll.getLayoutParams();
            lp.height = dp(100);
            multiSelectScroll.setLayoutParams(lp);
            multiSelectScroll.scrollTo(0, 0);
        }
        if (rootSheet == null || multiSelectScroll == null) return;
        if (PrefsManager.isReduceAnimations(context)) {
            rootSheet.setTranslationY(0f);
            rootSheet.setAlpha(1f);
            multiSelectScroll.scrollTo(0, 0);
            return;
        }
        rootSheet.setTranslationY(dp(115));
        rootSheet.setAlpha(0f);
        rootSheet.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(230L)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    if (!activeCheck.isActive() || multiSelectScroll == null || expanded) return;
                    int peekTarget = dp(58);
                    ValueAnimator down = ValueAnimator.ofInt(0, peekTarget);
                    down.setDuration(220L);
                    down.setInterpolator(new DecelerateInterpolator());
                    down.addUpdateListener(a -> {
                        if (multiSelectScroll != null) {
                            multiSelectScroll.scrollTo(0, (int) a.getAnimatedValue());
                        }
                    });
                    down.addListener(new AnimatorListenerAdapter() {
                        private boolean canceled = false;

                        @Override
                        public void onAnimationCancel(Animator animation) {
                            canceled = true;
                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (canceled || multiSelectScroll == null || !activeCheck.isActive() || expanded) return;
                            ValueAnimator back = ValueAnimator.ofInt(peekTarget, 0);
                            back.setDuration(260L);
                            back.setStartDelay(90L);
                            back.setInterpolator(new DecelerateInterpolator());
                            back.addUpdateListener(a -> {
                                if (multiSelectScroll != null) {
                                    multiSelectScroll.scrollTo(0, (int) a.getAnimatedValue());
                                }
                            });
                            peekAnimator = back;
                            back.start();
                        }
                    });
                    peekAnimator = down;
                    down.start();
                })
                .start();
    }

    public void toggle() {
        setExpanded(!expanded);
    }

    public void setExpanded(boolean expand) {
        if (multiSelectScroll == null) return;
        cancelAnimators();
        expanded = expand;
        int targetH = dp(expanded ? 178 : 100);
        ViewGroup.LayoutParams curLp = multiSelectScroll.getLayoutParams();
        int startH = multiSelectScroll.getHeight() > 0
                ? multiSelectScroll.getHeight()
                : (curLp != null ? curLp.height : targetH);
        if (PrefsManager.isReduceAnimations(context) || startH == targetH) {
            if (curLp != null) {
                curLp.height = targetH;
                multiSelectScroll.setLayoutParams(curLp);
            }
            if (!expanded) multiSelectScroll.scrollTo(0, 0);
            return;
        }
        ValueAnimator va = ValueAnimator.ofInt(startH, targetH);
        va.setDuration(220L);
        va.setInterpolator(new DecelerateInterpolator());
        va.addUpdateListener(anim -> {
            if (multiSelectScroll == null) return;
            ViewGroup.LayoutParams lp = multiSelectScroll.getLayoutParams();
            if (lp != null) {
                lp.height = (int) anim.getAnimatedValue();
                multiSelectScroll.setLayoutParams(lp);
            }
        });
        heightAnimator = va;
        va.start();
        if (!expanded) {
            multiSelectScroll.smoothScrollTo(0, 0);
        }
    }

    public void cancelAnimators() {
        if (heightAnimator != null) {
            heightAnimator.cancel();
            heightAnimator = null;
        }
        if (peekAnimator != null) {
            peekAnimator.cancel();
            peekAnimator = null;
        }
        if (rootSheet != null) {
            rootSheet.animate().cancel();
        }
    }

    public interface IsActiveCheck {
        boolean isActive();
    }

    private int dp(float val) {
        return Math.round(val * context.getResources().getDisplayMetrics().density);
    }
}
