package com.aishhatorah.filemanagerpro;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * חלונית התקדמות זכוכיתית מרכזית לכלל פעולות הקבצים והמדיה:
 * - מציגה התקדמות בזמן אמת עד לסיום הפעולה.
 * - כוללת כפתור "הסתר" להמשך שקט ברקע עם מעבר אוטומטי להתראת התקדמות.
 */
public final class OperationProgressHelper {

    private static final AtomicInteger NEXT_OP_ID = new AtomicInteger(100);

    private OperationProgressHelper() {
    }

    public static Handle show(Activity activity, String title, String initialDetail, boolean determinate) {
        return new Handle(activity, title, initialDetail, determinate);
    }

    public static final class Handle {
        private final Activity activity;
        private final Context appContext;
        private final int opId;
        private final String baseTitle;
        private final AlertDialog dialog;
        private final TextView tvDetail;
        private final ProgressBar progressBar;
        private final TextView tvLeftStat;
        private final TextView tvRightStat;

        private volatile boolean hiddenByUser = false;
        private volatile boolean finished = false;
        private long lastUiUpdateMs = 0L;

        private Handle(Activity activity, String title, String initialDetail, boolean determinate) {
            this.activity = activity;
            this.appContext = activity.getApplicationContext();
            this.opId = NEXT_OP_ID.incrementAndGet() % 8000 + 200;
            this.baseTitle = title != null ? title : "מבצע פעולה...";

            int theme = PrefsManager.getThemeMode(activity);
            int textPrimary = ThemeHelper.getPrimaryTextColor(activity, theme);
            int textSecondary = ThemeHelper.getSecondaryTextColor(activity, theme);
            int accent = ThemeHelper.getAccentColor(activity, theme);
            int divider = ThemeHelper.getDividerColor(activity, theme);

            LinearLayout box = new LinearLayout(activity);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            box.setPadding(dp(20), dp(18), dp(20), dp(16));
            box.setBackground(ThemedDialogHelper.createDialogBackground(activity, theme));

            TextView tvTitle = new TextView(activity);
            tvTitle.setText(this.baseTitle);
            tvTitle.setTextColor(textPrimary);
            tvTitle.setTextSize(ThemeHelper.spScaled(activity, 16f));
            tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
            box.addView(tvTitle);

            tvDetail = new TextView(activity);
            tvDetail.setText(initialDetail != null ? initialDetail : "מתחיל...");
            tvDetail.setTextColor(textSecondary);
            tvDetail.setTextSize(ThemeHelper.spScaled(activity, 13f));
            tvDetail.setSingleLine(true);
            tvDetail.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            tvDetail.setPadding(0, dp(8), 0, dp(10));
            box.addView(tvDetail);

            progressBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
            progressBar.setIndeterminate(!determinate);
            progressBar.setMax(1000);
            progressBar.setProgress(0);
            progressBar.setProgressTintList(ColorStateList.valueOf(accent));
            progressBar.setIndeterminateTintList(ColorStateList.valueOf(accent));
            progressBar.setProgressBackgroundTintList(ColorStateList.valueOf(divider));
            box.addView(progressBar, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout statsRow = new LinearLayout(activity);
            statsRow.setOrientation(LinearLayout.HORIZONTAL);
            statsRow.setPadding(0, dp(8), 0, dp(12));

            tvLeftStat = new TextView(activity);
            tvLeftStat.setText(determinate ? "0%" : "בתהליך...");
            tvLeftStat.setTextColor(textPrimary);
            tvLeftStat.setTextSize(ThemeHelper.spScaled(activity, 12.5f));
            statsRow.addView(tvLeftStat, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            tvRightStat = new TextView(activity);
            tvRightStat.setText("");
            tvRightStat.setTextColor(accent);
            tvRightStat.setTypeface(Typeface.DEFAULT_BOLD);
            tvRightStat.setTextSize(ThemeHelper.spScaled(activity, 12.5f));
            tvRightStat.setGravity(Gravity.END);
            statsRow.addView(tvRightStat);
            box.addView(statsRow);

            Button btnHide = ThemedDialogHelper.createThemedButton(activity, "הסתר", theme, false, false);
            LinearLayout.LayoutParams hideLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hideLp.gravity = Gravity.END;
            box.addView(btnHide, hideLp);

            AlertDialog dlg = new AlertDialog.Builder(activity)
                    .setView(box)
                    .setCancelable(true)
                    .create();
            dlg.setCanceledOnTouchOutside(true);

            Runnable moveToBackground = () -> {
                if (!hiddenByUser && !finished) {
                    hiddenByUser = true;
                    FileOperationNotificationHelper.forceShowNow(appContext, opId);
                    if (!activity.isFinishing()) {
                        Toast.makeText(activity, "הפעולה ממשיכה ברקע ומוצגת בהתראה", Toast.LENGTH_SHORT).show();
                    }
                }
            };

            btnHide.setOnClickListener(v -> {
                moveToBackground.run();
                safeDismiss();
            });
            dlg.setOnCancelListener(d -> moveToBackground.run());

            this.dialog = dlg;
            FileOperationNotificationHelper.begin(appContext, opId, this.baseTitle, initialDetail);

            if (!activity.isFinishing() && !activity.isDestroyed()) {
                dlg.show();
                ThemedDialogHelper.applyResponsiveDialogWindow(activity, dlg);
            }
        }

        private int dp(float v) {
            return Math.round(v * activity.getResources().getDisplayMetrics().density);
        }

        public void updatePercent(String detail, int percent, String rightStat) {
            int clamped = Math.max(0, Math.min(100, percent));
            int permille = clamped * 10;
            String pctText = clamped + "%";
            String notifText = (detail != null && !detail.isEmpty())
                    ? (detail + " • " + pctText)
                    : pctText;
            updateCustom(detail, pctText, rightStat != null ? rightStat : "", permille, notifText);
        }

        public void updateCounts(String detail, int done, int total, String rightStat) {
            int safeTotal = Math.max(1, total);
            int safeDone = Math.max(0, Math.min(done, safeTotal));
            int permille = (int) Math.round((safeDone * 1000.0) / safeTotal);
            int pct = (int) Math.round((safeDone * 100.0) / safeTotal);
            String left = safeDone + " / " + safeTotal + " (" + pct + "%)";
            String notif = (detail != null && !detail.isEmpty() ? detail + " • " : "") + left;
            updateCustom(detail, left, rightStat != null ? rightStat : "", permille, notif);
        }

        public void updateIndeterminate(String detail, String statusLabel) {
            if (finished) return;
            FileOperationNotificationHelper.updateProgress(appContext, opId, baseTitle,
                    detail != null ? detail : statusLabel, 0, 0);
            if (hiddenByUser || activity.isFinishing() || activity.isDestroyed()) return;
            activity.runOnUiThread(() -> {
                if (finished || hiddenByUser || activity.isFinishing() || activity.isDestroyed()) return;
                progressBar.setIndeterminate(true);
                if (detail != null) tvDetail.setText(detail);
                if (statusLabel != null) tvLeftStat.setText(statusLabel);
            });
        }

        public void updateCustom(String detail, String leftStat, String rightStat, int permille, String notifDetail) {
            if (finished) return;
            int clampedPermille = Math.max(0, Math.min(1000, permille));
            FileOperationNotificationHelper.updateProgress(
                    appContext, opId, baseTitle,
                    notifDetail != null ? notifDetail : (detail != null ? detail : ""),
                    clampedPermille, 1000);

            if (hiddenByUser || activity.isFinishing() || activity.isDestroyed()) return;
            long now = System.currentTimeMillis();
            if (now - lastUiUpdateMs < 55L && clampedPermille < 1000) return;
            lastUiUpdateMs = now;

            activity.runOnUiThread(() -> {
                if (finished || hiddenByUser || activity.isFinishing() || activity.isDestroyed()) return;
                if (progressBar.isIndeterminate()) {
                    progressBar.setIndeterminate(false);
                }
                progressBar.setProgress(clampedPermille);
                if (detail != null) tvDetail.setText(detail);
                if (leftStat != null) tvLeftStat.setText(leftStat);
                if (rightStat != null) tvRightStat.setText(rightStat);
            });
        }

        public void finish() {
            if (finished) return;
            finished = true;
            FileOperationNotificationHelper.finish(appContext, opId, baseTitle, "");
            if (!activity.isFinishing() && !activity.isDestroyed()) {
                activity.runOnUiThread(this::safeDismiss);
            }
        }

        private void safeDismiss() {
            try {
                if (dialog != null && dialog.isShowing() && !activity.isFinishing() && !activity.isDestroyed()) {
                    dialog.dismiss();
                }
            } catch (Throwable ignored) {
            }
        }
    }
}
