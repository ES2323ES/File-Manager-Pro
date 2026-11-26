package com.aishhatorah.filemanagerpro.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aishhatorah.filemanagerpro.PrefsManager;
import com.aishhatorah.filemanagerpro.R;
import com.aishhatorah.filemanagerpro.ThemeHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * מתאם גרירה ושחרור (Drag & Drop) לסידור והסתרת קטגוריות מסך הבית,
 * משותף הן לתת-רשימה המוטמעת במסך ההגדרות והן למסך סידור הבית העצמאי.
 */
public class HomeOrderDragAdapter extends RecyclerView.Adapter<HomeOrderDragAdapter.VH> {

    public static final String KEY_DIVIDER = "__BLOCK_DIVIDER__";

    public interface OnOrderChangedListener {
        void onOrderPersisted(int visibleCount, int totalCategories);
    }

    private final Context context;
    private final int theme;
    private final boolean compactMode;
    private final List<String> rows = new ArrayList<>();
    private final OnOrderChangedListener listener;
    private ItemTouchHelper touchHelper;
    private RecyclerView attachedRecyclerView;

    public HomeOrderDragAdapter(Context context, int theme, boolean compactMode, OnOrderChangedListener listener) {
        this.context = context;
        this.theme = theme;
        this.compactMode = compactMode;
        this.listener = listener;
        reloadFromPrefs();
    }

    public static HomeOrderDragAdapter attachToRecyclerView(
            RecyclerView rv,
            Context context,
            int theme,
            boolean compactMode,
            OnOrderChangedListener listener
    ) {
        rv.setLayoutManager(new LinearLayoutManager(context));
        rv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        HomeOrderDragAdapter adapter = new HomeOrderDragAdapter(context, theme, compactMode, listener);
        rv.setAdapter(adapter);
        adapter.attachHelper(rv);
        return adapter;
    }

    public void attachHelper(RecyclerView rv) {
        this.attachedRecyclerView = rv;
        ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean isLongPressDragEnabled() {
                return true;
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                int from = viewHolder.getAdapterPosition();
                int to = target.getAdapterPosition();
                if (from < 0 || to < 0 || from >= rows.size() || to >= rows.size()) return false;
                Collections.swap(rows, from, to);
                notifyItemMoved(from, to);
                return true;
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                if (recyclerView.getParent() != null) {
                    recyclerView.getParent().requestDisallowInterceptTouchEvent(false);
                }
                persistOrder();
                notifyDataSetChanged();
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }
        });
        this.touchHelper = helper;
        helper.attachToRecyclerView(rv);
    }

    public void reloadFromPrefs() {
        rows.clear();
        List<String> saved = PrefsManager.getHomeCategoryOrder(context);
        rows.addAll(saved);
        int limit = PrefsManager.getHomeVisibleLimit(context);
        int divIdx = (limit >= 0 && limit <= rows.size()) ? limit : rows.size();
        rows.add(divIdx, KEY_DIVIDER);
        notifyDataSetChanged();
    }

    public void resetToDefault() {
        PrefsManager.setHomeCategoryOrder(context, null);
        PrefsManager.setHomeVisibleLimit(context, -1);
        reloadFromPrefs();
        if (listener != null) {
            listener.onOrderPersisted(rows.size() - 1, rows.size() - 1);
        }
    }

    public void persistOrder() {
        int divIdx = rows.indexOf(KEY_DIVIDER);
        List<String> cleanOrder = new ArrayList<>(rows.size());
        for (String r : rows) {
            if (!KEY_DIVIDER.equals(r)) {
                cleanOrder.add(r);
            }
        }
        int limit = (divIdx >= 0 && divIdx < cleanOrder.size()) ? divIdx : -1;
        PrefsManager.setHomeCategoryOrder(context, cleanOrder);
        PrefsManager.setHomeVisibleLimit(context, limit);
        if (listener != null) {
            int vis = limit >= 0 ? limit : cleanOrder.size();
            listener.onOrderPersisted(vis, cleanOrder.size());
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LinearLayout row = new LinearLayout(parent.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int hPad = dp(compactMode ? 12 : 14);
        int vPad = dp(compactMode ? 10 : 12);
        row.setPadding(hPad, vPad, hPad, vPad);

        RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(compactMode ? 0 : 12), dp(compactMode ? 3 : 4), dp(compactMode ? 0 : 12), dp(compactMode ? 3 : 4));
        row.setLayoutParams(lp);

        ImageView drag = new ImageView(parent.getContext());
        drag.setImageResource(R.drawable.ic_drag_handle);
        int iconSz = dp(compactMode ? 21 : 24);
        row.addView(drag, new LinearLayout.LayoutParams(iconSz, iconSz));

        TextView tv = new TextView(parent.getContext());
        tv.setTextSize(ThemeHelper.spScaled(context, compactMode ? 13.5f : 14f));
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tLp.setMarginStart(dp(10));
        row.addView(tv, tLp);
        return new VH(row, tv, drag);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        String key = rows.get(pos);
        int divIdx = rows.indexOf(KEY_DIVIDER);
        boolean belowDivider = (divIdx >= 0 && pos > divIdx);

        if (KEY_DIVIDER.equals(key)) {
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(12));
            bg.setColor(ColorUtils.setAlphaComponent(Color.parseColor("#DC2626"), 220));
            h.container.setBackground(bg);
            h.container.setAlpha(1f);
            h.title.setText("קו חסימה — מתחת לקו מוסתר מהבית");
            h.title.setTextColor(Color.WHITE);
            h.handle.setColorFilter(Color.WHITE);
        } else {
            h.container.setBackground(ThemeHelper.createGlassCardBackground(
                    context, theme, compactMode ? 12f : 14f, true, false));
            h.container.setAlpha(belowDivider ? 0.56f : 1f);
            h.title.setText(labelForKey(key));
            h.title.setTextColor(belowDivider
                    ? ThemeHelper.getSecondaryTextColor(context, theme)
                    : ThemeHelper.getPrimaryTextColor(context, theme));
            h.handle.setColorFilter(ThemeHelper.getAccentColor(context, theme));
        }

        h.handle.setOnTouchListener((v, ev) -> {
            if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
                if (attachedRecyclerView != null && attachedRecyclerView.getParent() != null) {
                    attachedRecyclerView.getParent().requestDisallowInterceptTouchEvent(true);
                }
                if (touchHelper != null) {
                    touchHelper.startDrag(h);
                }
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    private String labelForKey(String key) {
        switch (key) {
            case "internal": return context.getString(R.string.cat_internal);
            case "sd_card": return context.getString(R.string.cat_sd_card);
            case "downloads": return context.getString(R.string.cat_downloads);
            case "images": return context.getString(R.string.cat_images);
            case "audio": return context.getString(R.string.cat_audio);
            case "videos": return context.getString(R.string.cat_videos);
            case "documents": return context.getString(R.string.cat_documents);
            case "apps": return context.getString(R.string.cat_apps);
            case "recent": return context.getString(R.string.cat_recent);
            case "recycle_bin": return context.getString(R.string.cat_recycle_bin);
            case "audio_cutter": return context.getString(R.string.cat_audio_cutter);
            case "video_cutter": return context.getString(R.string.cat_video_cutter);
            default: return key;
        }
    }

    private int dp(int v) {
        return Math.round(v * context.getResources().getDisplayMetrics().density);
    }

    public static class VH extends RecyclerView.ViewHolder {
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
}
