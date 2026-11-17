package com.aishhatorah.filemanagerpro;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * בורר קבצים פנימי מתוך קטגוריות האפליקציה (שמע / וידאו) עבור חותך האודיו וחותך הוידאו.
 */
public final class CategoryMediaPickerDialog {

    public interface OnMediaSelected {
        void onSelected(File file);
    }

    private CategoryMediaPickerDialog() {
    }

    public static void show(
            Activity activity,
            String categoryMode,
            String title,
            OnMediaSelected listener,
            Runnable fallbackSystemPicker
    ) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        int theme = PrefsManager.getThemeMode(activity);
        int textPrimary = ThemeHelper.getPrimaryTextColor(activity, theme);
        int textSecondary = ThemeHelper.getSecondaryTextColor(activity, theme);
        int accent = ThemeHelper.getAccentColor(activity, theme);
        int divider = ThemeHelper.getDividerColor(activity, theme);
        boolean dark = ThemeHelper.isDarkSurface(activity, theme);
        float d = activity.getResources().getDisplayMetrics().density;

        LinearLayout outer = new LinearLayout(activity);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        int pad = Math.round(16 * d);
        outer.setPadding(pad, pad, pad, Math.round(12 * d));
        outer.setBackground(ThemedDialogHelper.createDialogBackground(activity, theme));

        LinearLayout headerRow = new LinearLayout(activity);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvTitle = new TextView(activity);
        tvTitle.setText(title);
        tvTitle.setTextColor(textPrimary);
        tvTitle.setTextSize(ThemeHelper.spScaled(activity, 16f));
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        headerRow.addView(tvTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageButton btnRefresh = ThemeHelper.createBorderlessSvgButton(
                activity, R.drawable.ic_reset, accent, "רענן רשימה", 34, 6, null);
        headerRow.addView(btnRefresh);
        outer.addView(headerRow);

        EditText etSearch = new EditText(activity);
        etSearch.setHint("חיפוש לפי שם קובץ או תיקייה...");
        etSearch.setHintTextColor(textSecondary);
        etSearch.setTextColor(textPrimary);
        etSearch.setTextSize(ThemeHelper.spScaled(activity, 13.5f));
        etSearch.setSingleLine(true);
        etSearch.setPadding(Math.round(12 * d), Math.round(9 * d), Math.round(12 * d), Math.round(9 * d));
        GradientDrawable searchBg = new GradientDrawable();
        searchBg.setCornerRadius(Math.round(12 * d));
        searchBg.setColor(ColorUtils.setAlphaComponent(accent, dark ? 24 : 12));
        searchBg.setStroke(Math.max(1, Math.round(1 * d)), ColorUtils.setAlphaComponent(divider, 180));
        etSearch.setBackground(searchBg);
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        searchLp.topMargin = Math.round(10 * d);
        searchLp.bottomMargin = Math.round(8 * d);
        outer.addView(etSearch, searchLp);

        TextView tvStatus = new TextView(activity);
        tvStatus.setText("טוען קבצים מהקטגוריה...");
        tvStatus.setTextColor(textSecondary);
        tvStatus.setTextSize(ThemeHelper.spScaled(activity, 12.5f));
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setPadding(0, Math.round(14 * d), 0, Math.round(14 * d));
        outer.addView(tvStatus);

        ListView listView = new ListView(activity);
        listView.setDivider(null);
        listView.setDividerHeight(Math.round(4 * d));
        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        int listH = Math.min(Math.round(dm.heightPixels * 0.48f), Math.round(380 * d));
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, listH);
        listView.setVisibility(View.GONE);
        outer.addView(listView, listLp);

        LinearLayout btnRow = new LinearLayout(activity);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams brLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        brLp.topMargin = Math.round(10 * d);
        btnRow.setLayoutParams(brLp);

        Button btnClose = ThemedDialogHelper.createThemedButton(activity, "סגור", theme, false, false);
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        btnRow.addView(btnClose, closeLp);

        Button btnExternal = null;
        if (fallbackSystemPicker != null) {
            btnExternal = ThemedDialogHelper.createThemedButton(activity, "ממקור אחר", theme, true, false);
            LinearLayout.LayoutParams extLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            extLp.setMarginStart(Math.round(8 * d));
            btnRow.addView(btnExternal, extLp);
        }
        outer.addView(btnRow);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(outer)
                .create();

        btnClose.setOnClickListener(v -> dialog.dismiss());
        if (btnExternal != null) {
            btnExternal.setOnClickListener(v -> {
                dialog.dismiss();
                fallbackSystemPicker.run();
            });
        }

        final List<File> allFiles = new ArrayList<>();
        final List<File> filtered = new ArrayList<>();
        final int iconRes = FileBrowserActivity.MODE_AUDIO.equals(categoryMode)
                ? R.drawable.ic_audio
                : R.drawable.ic_video;
        final int rowFill = ColorUtils.setAlphaComponent(accent, dark ? 22 : 10);

        final BaseAdapter adapter = new BaseAdapter() {
            @Override public int getCount() { return filtered.size(); }
            @Override public Object getItem(int position) { return filtered.get(position); }
            @Override public long getItemId(int position) { return position; }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                LinearLayout row;
                ImageView iv;
                TextView tvName;
                TextView tvMeta;

                if (convertView instanceof LinearLayout) {
                    row = (LinearLayout) convertView;
                    iv = (ImageView) row.getChildAt(0);
                    LinearLayout col = (LinearLayout) row.getChildAt(1);
                    tvName = (TextView) col.getChildAt(0);
                    tvMeta = (TextView) col.getChildAt(1);
                } else {
                    row = new LinearLayout(activity);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(Math.round(10 * d), Math.round(9 * d), Math.round(10 * d), Math.round(9 * d));
                    GradientDrawable rBg = new GradientDrawable();
                    rBg.setCornerRadius(Math.round(12 * d));
                    rBg.setColor(rowFill);
                    row.setBackground(rBg);

                    iv = new ImageView(activity);
                    iv.setImageResource(iconRes);
                    LinearLayout.LayoutParams ivLp = new LinearLayout.LayoutParams(Math.round(30 * d), Math.round(30 * d));
                    ivLp.setMarginEnd(Math.round(10 * d));
                    row.addView(iv, ivLp);

                    LinearLayout col = new LinearLayout(activity);
                    col.setOrientation(LinearLayout.VERTICAL);

                    tvName = new TextView(activity);
                    tvName.setTextColor(textPrimary);
                    tvName.setTextSize(ThemeHelper.spScaled(activity, 13.5f));
                    tvName.setTypeface(Typeface.DEFAULT_BOLD);
                    tvName.setSingleLine(true);
                    tvName.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                    col.addView(tvName);

                    tvMeta = new TextView(activity);
                    tvMeta.setTextColor(textSecondary);
                    tvMeta.setTextSize(ThemeHelper.spScaled(activity, 11.5f));
                    tvMeta.setSingleLine(true);
                    tvMeta.setEllipsize(TextUtils.TruncateAt.END);
                    col.addView(tvMeta);

                    row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                }

                File f = filtered.get(position);
                tvName.setText(f.getName());
                File p = f.getParentFile();
                String folder = p != null ? p.getName() : "";
                tvMeta.setText(folder + "  •  " + StorageUtils.formatSize(f.length()));
                return row;
            }
        };
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < filtered.size()) {
                File picked = filtered.get(position);
                dialog.dismiss();
                if (listener != null) listener.onSelected(picked);
            }
        });

        final Runnable applyFilter = () -> {
            String q = etSearch.getText() != null ? etSearch.getText().toString().trim().toLowerCase(Locale.ROOT) : "";
            filtered.clear();
            for (File f : allFiles) {
                if (f == null || !f.exists()) continue;
                if (q.isEmpty()) {
                    filtered.add(f);
                } else {
                    String nameLow = f.getName().toLowerCase(Locale.ROOT);
                    String parentLow = f.getParentFile() != null ? f.getParentFile().getName().toLowerCase(Locale.ROOT) : "";
                    if (nameLow.contains(q) || parentLow.contains(q)) {
                        filtered.add(f);
                    }
                }
            }
            adapter.notifyDataSetChanged();
            if (filtered.isEmpty()) {
                listView.setVisibility(View.GONE);
                tvStatus.setVisibility(View.VISIBLE);
                tvStatus.setText(allFiles.isEmpty()
                        ? "לא נמצאו קבצים בקטגוריה זו במכשיר"
                        : "לא נמצאו קבצים התואמים לחיפוש");
            } else {
                tvStatus.setVisibility(View.GONE);
                listView.setVisibility(View.VISIBLE);
            }
        };

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { applyFilter.run(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        final ExecutorService loader = Executors.newSingleThreadExecutor();
        final Runnable loadCategory = () -> {
            tvStatus.setVisibility(View.VISIBLE);
            tvStatus.setText("טוען קבצים מהקטגוריה...");
            listView.setVisibility(View.GONE);
            loader.execute(() -> {
                boolean hidden = PrefsManager.isShowHidden(activity);
                StorageUtils.CategoryIndexSnapshot snap = FileScanCache.getSnapshot(activity, hidden, false);
                List<File> items = FileScanCache.extractCategoryFiles(snap, categoryMode);
                if (items.isEmpty()) {
                    snap = FileScanCache.refreshSingleCategory(activity, categoryMode, hidden);
                    items = FileScanCache.extractCategoryFiles(snap, categoryMode);
                }
                Collections.sort(items, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                final List<File> finalItems = items;
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    allFiles.clear();
                    allFiles.addAll(finalItems);
                    applyFilter.run();
                });
            });
        };

        btnRefresh.setOnClickListener(v -> {
            tvStatus.setVisibility(View.VISIBLE);
            tvStatus.setText("מרענן רשימת קבצים...");
            listView.setVisibility(View.GONE);
            loader.execute(() -> {
                boolean hidden = PrefsManager.isShowHidden(activity);
                StorageUtils.CategoryIndexSnapshot snap = FileScanCache.refreshSingleCategory(activity, categoryMode, hidden);
                List<File> items = FileScanCache.extractCategoryFiles(snap, categoryMode);
                Collections.sort(items, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                final List<File> finalItems = items;
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    allFiles.clear();
                    allFiles.addAll(finalItems);
                    applyFilter.run();
                });
            });
        });

        dialog.setOnDismissListener(dInterface -> loader.shutdownNow());
        dialog.show();
        ThemedDialogHelper.applyResponsiveDialogWindow(activity, dialog);
        loadCategory.run();
    }
}
