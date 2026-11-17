package com.aishhatorah.filemanagerpro;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * סידור והסתרת קטגוריות מסך הבית באמצעות גרירה ושחרור (Drag & Drop) כולל קו חסימה:
 * כל קטגוריה שתמוקם מתחת לקו החסימה תוסתר מהמסך הראשי.
 */
public class HomeLayoutActivity extends AppCompatActivity {

    private static final String KEY_DIVIDER = "__BLOCK_DIVIDER__";

    private final List<String> rows = new ArrayList<>();
    private LayoutAdapter adapter;
    private int theme;

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        theme = PrefsManager.getThemeMode(this);
        ThemeHelper.applyWindowStatusBar(this, theme);
        int textPrimary = ThemeHelper.getPrimaryTextColor(this, theme);
        int accent = ThemeHelper.getAccentColor(this, theme);
        boolean dark = ThemeHelper.isDarkSurface(this, theme);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                persistOrder();
                finish();
            }
        });

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        ThemeHelper.applyWallpaperBackdrop(this, root, theme);

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(10), dp(8), dp(14), dp(10));
        ThemeHelper.applyCurvedHeaderStyle(this, topBar, theme);

        ImageButton btnBack = ThemeHelper.createBorderlessSvgButton(
                this, R.drawable.ic_arrow_back, accent, "שמור וחזור", 40, 8, v -> {
                    persistOrder();
                    finish();
                });
        topBar.addView(btnBack);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("סידור מסך הבית");
        tvTitle.setTextColor(textPrimary);
        tvTitle.setTextSize(ThemeHelper.spScaled(this, 17f));
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tLp.setMarginStart(dp(6));
        topBar.addView(tvTitle, tLp);

        Button btnReset = new Button(this);
        btnReset.setText("איפוס");
        btnReset.setAllCaps(false);
        btnReset.setTextSize(ThemeHelper.spScaled(this, 13f));
        btnReset.setTypeface(Typeface.DEFAULT_BOLD);
        btnReset.setTextColor(accent);
        GradientDrawable resetBg = new GradientDrawable();
        resetBg.setCornerRadius(dp(12));
        resetBg.setColor(ColorUtils.setAlphaComponent(accent, dark ? 28 : 16));
        btnReset.setBackground(resetBg);
        btnReset.setStateListAnimator(null);
        btnReset.setMinHeight(0);
        btnReset.setMinimumHeight(0);
        btnReset.setPadding(dp(14), dp(7), dp(14), dp(7));
        btnReset.setOnClickListener(v -> {
            PrefsManager.setHomeCategoryOrder(this, null);
            PrefsManager.setHomeVisibleLimit(this, -1);
            loadRows();
            adapter.notifyDataSetChanged();
            Toast.makeText(this, "סדר הבית אופס לברירת המחדל", Toast.LENGTH_SHORT).show();
        });
        topBar.addView(btnReset);
        root.addView(topBar);

        TextView tvHint = new TextView(this);
        tvHint.setText("גרור למעלה/למטה לשינוי סדר הקטגוריות. פריטים מתחת ל\"קו החסימה\" לא יופיעו במסך הבית.");
        tvHint.setTextColor(ThemeHelper.getSecondaryTextColor(this, theme));
        tvHint.setTextSize(ThemeHelper.spScaled(this, 12.5f));
        tvHint.setPadding(dp(16), dp(10), dp(16), dp(8));
        root.addView(tvHint);

        loadRows();

        RecyclerView rv = new RecyclerView(this);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new LayoutAdapter();
        rv.setAdapter(adapter);

        ItemTouchHelper touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                int from = viewHolder.getAdapterPosition();
                int to = target.getAdapterPosition();
                if (from < 0 || to < 0) return false;
                Collections.swap(rows, from, to);
                adapter.notifyItemMoved(from, to);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }
        });
        touchHelper.attachToRecyclerView(rv);

        root.addView(rv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private void loadRows() {
        rows.clear();
        List<String> saved = PrefsManager.getHomeCategoryOrder(this);
        rows.addAll(saved);
        int limit = PrefsManager.getHomeVisibleLimit(this);
        int divIdx = (limit >= 0 && limit <= rows.size()) ? limit : rows.size();
        rows.add(divIdx, KEY_DIVIDER);
    }

    private void persistOrder() {
        int divIdx = rows.indexOf(KEY_DIVIDER);
        List<String> cleanOrder = new ArrayList<>();
        for (String r : rows) {
            if (!KEY_DIVIDER.equals(r)) cleanOrder.add(r);
        }
        PrefsManager.setHomeCategoryOrder(this, cleanOrder);
        PrefsManager.setHomeVisibleLimit(this, divIdx >= 0 ? divIdx : -1);
    }

    private String labelForKey(String key) {
        switch (key) {
            case "internal": return getString(R.string.cat_internal);
            case "sd_card": return getString(R.string.cat_sd_card);
            case "downloads": return getString(R.string.cat_downloads);
            case "images": return getString(R.string.cat_images);
            case "audio": return getString(R.string.cat_audio);
            case "videos": return getString(R.string.cat_videos);
            case "documents": return getString(R.string.cat_documents);
            case "apps": return getString(R.string.cat_apps);
            case "recent": return getString(R.string.cat_recent);
            case "recycle_bin": return getString(R.string.cat_recycle_bin);
            case "audio_cutter": return getString(R.string.cat_audio_cutter);
            case "video_cutter": return getString(R.string.cat_video_cutter);
            default: return key;
        }
    }

    private class LayoutAdapter extends RecyclerView.Adapter<LayoutAdapter.VH> {
        class VH extends RecyclerView.ViewHolder {
            final LinearLayout container;
            final TextView title;
            final ImageView handle;

            VH(LinearLayout c, TextView t, ImageView h) {
                super(c);
                this.container = c;
                this.title = t;
                this.handle = h;
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(parent.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(12), dp(14), dp(12));
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(dp(12), dp(4), dp(12), dp(4));
            row.setLayoutParams(lp);

            ImageView drag = new ImageView(parent.getContext());
            drag.setImageResource(R.drawable.ic_drag_handle);
            int iconSz = dp(24);
            row.addView(drag, new LinearLayout.LayoutParams(iconSz, iconSz));

            TextView tv = new TextView(parent.getContext());
            tv.setTextSize(ThemeHelper.spScaled(HomeLayoutActivity.this, 14f));
            tv.setTypeface(Typeface.DEFAULT_BOLD);
            LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tLp.setMarginStart(dp(10));
            row.addView(tv, tLp);
            return new VH(row, tv, drag);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            String key = rows.get(pos);
            if (KEY_DIVIDER.equals(key)) {
                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(dp(14));
                bg.setColor(ColorUtils.setAlphaComponent(Color.parseColor("#DC2626"), 220));
                h.container.setBackground(bg);
                h.title.setText("קו חסימה — מתחת לקו מוסתר מהבית");
                h.title.setTextColor(Color.WHITE);
                h.handle.setColorFilter(Color.WHITE);
            } else {
                h.container.setBackground(ThemeHelper.createGlassCardBackground(
                        HomeLayoutActivity.this, theme, 14f, true, false));
                h.title.setText(labelForKey(key));
                h.title.setTextColor(ThemeHelper.getPrimaryTextColor(HomeLayoutActivity.this, theme));
                h.handle.setColorFilter(ThemeHelper.getAccentColor(HomeLayoutActivity.this, theme));
            }
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }
    }
}
