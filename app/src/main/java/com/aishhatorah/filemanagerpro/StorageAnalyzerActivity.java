package com.aishhatorah.filemanagerpro;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StorageAnalyzerActivity extends AppCompatActivity {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<File> largestFiles = new ArrayList<>();

    private TextView tvStatusSummary;
    private ProgressBar scanSpinner;
    private HeavyAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int theme = PrefsManager.getThemeMode(this);
        ThemeHelper.applyWindowStatusBar(this, theme);

        int bgColor = ThemeHelper.getBgColor(theme);
        int cardColor = ThemeHelper.getCardColor(theme);
        int textPrimary = ThemeHelper.getPrimaryTextColor(theme);
        int textSecondary = ThemeHelper.getSecondaryTextColor(theme);
        int accent = ThemeHelper.getAccentColor(theme);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(bgColor);

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(ThemeHelper.getTopBarColor(theme));
        topBar.setPadding(24, 22, 24, 22);

        Button btnBack = ThemedDialogHelper.createThemedButton(this, "חזרה", theme, false, false);
        btnBack.setOnClickListener(v -> finish());
        topBar.addView(btnBack);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("ניתוח — הקבצים הגדולים ביותר");
        tvTitle.setTextSize(17f);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setTextColor(textPrimary);
        LinearLayout.LayoutParams lpTitle = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lpTitle.setMarginStart(14);
        topBar.addView(tvTitle, lpTitle);

        Button btnRescan = ThemedDialogHelper.createThemedButton(this, "סרוק שוב", theme, true, false);
        btnRescan.setOnClickListener(v -> runHeavyFilesScan());
        topBar.addView(btnRescan);

        root.addView(topBar);

        tvStatusSummary = new TextView(this);
        tvStatusSummary.setTextColor(textSecondary);
        tvStatusSummary.setTextSize(13f);
        tvStatusSummary.setPadding(32, 20, 32, 14);
        root.addView(tvStatusSummary);

        scanSpinner = new ProgressBar(this);
        root.addView(scanSpinner);

        RecyclerView rv = new RecyclerView(this);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HeavyAdapter(cardColor, textPrimary, textSecondary, accent);
        rv.setAdapter(adapter);

        root.addView(rv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
        runHeavyFilesScan();
    }

    private void runHeavyFilesScan() {
        scanSpinner.setVisibility(View.VISIBLE);
        tvStatusSummary.setText("סורק אחסון פנימי וכרטיס SD לאיתור הקבצים הכבדים ביותר...");

        executor.execute(() -> {
            List<File> collected = new ArrayList<>();
            File internalRoot = Environment.getExternalStorageDirectory();
            scanRecursive(internalRoot, collected, 0);

            StorageUtils.StorageStat sd = StorageUtils.getExternalSdCardStat(this);
            if (sd.available && sd.root != null) {
                scanRecursive(sd.root, collected, 0);
            }

            Collections.sort(collected, (a, b) -> Long.compare(b.length(), a.length()));
            List<File> top = collected.size() > 50 ? new ArrayList<>(collected.subList(0, 50)) : collected;

            StorageUtils.StorageStat st = StorageUtils.getInternalStorageStat();
            String alertNote = StorageUtils.checkAndNotify96Threshold(this);

            runOnUiThread(() -> {
                scanSpinner.setVisibility(View.GONE);
                largestFiles.clear();
                largestFiles.addAll(top);
                adapter.notifyDataSetChanged();

                String header = String.format(Locale.US,
                        "אחסון ראשי: %.1f%% תפוס (%s פנויים) • נמצאו %d קבצים מובילים במשקל",
                        st.usedPercent, StorageUtils.formatSize(st.freeBytes), top.size());
                if (alertNote != null) {
                    header = "אזהרה: התפוסה מעל 96%! מומלץ לפנות קבצים כבדים:\n" + header;
                    tvStatusSummary.setTextColor(Color.parseColor("#DC2626"));
                }
                tvStatusSummary.setText(header);
            });
        });
    }

    private void scanRecursive(File dir, List<File> out, int depth) {
        if (dir == null || !dir.exists() || depth > 8) return;
        if ("Android".equals(dir.getName())) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File f : children) {
            if (f.isDirectory()) {
                scanRecursive(f, out, depth + 1);
            } else if (f.length() > 1024 * 100) {
                out.add(f);
            }
        }
    }

    private void showHeavyFileActions(File f) {
        List<String> options = Arrays.asList(
                "פתיחה בתוך האפליקציה",
                "העבר לסל המיחזור (" + StorageUtils.formatSize(f.length()) + ")",
                "מחק לצמיתות"
        );
        ThemedDialogHelper.showThemedMenu(
                this,
                f.getName(),
                "משקל: " + StorageUtils.formatSize(f.length()) + "\n" + f.getAbsolutePath(),
                options,
                index -> {
                    if (index == 0) {
                        Intent viewer = new Intent(this, BuiltInViewerActivity.class);
                        viewer.putExtra(BuiltInViewerActivity.EXTRA_FILE_PATH, f.getAbsolutePath());
                        startActivity(viewer);
                    } else if (index == 1) {
                        if (RecycleBinManager.moveToTrash(this, f)) {
                            Toast.makeText(this, "הועבר לסל המיחזור", Toast.LENGTH_SHORT).show();
                            runHeavyFilesScan();
                        }
                    } else if (index == 2) {
                        ThemedDialogHelper.showCustomDialog(
                                this,
                                "מחיקה לצמיתות",
                                "האם למחוק לצמיתות את הקובץ \"" + f.getName() + "\" ולפנות " + StorageUtils.formatSize(f.length()) + "?",
                                null,
                                "מחק לצמיתות",
                                () -> {
                                    if (StorageUtils.deleteRecursively(f)) {
                                        Toast.makeText(this, "נמחק לצמיתות ופינה מקום", Toast.LENGTH_SHORT).show();
                                        runHeavyFilesScan();
                                    }
                                },
                                "סגור",
                                null,
                                true
                        );
                    }
                }
        );
    }

    private class HeavyAdapter extends RecyclerView.Adapter<HeavyAdapter.VH> {
        private final int cardBg;
        private final int textPrimary;
        private final int textSecondary;
        private final int accent;

        HeavyAdapter(int cardBg, int textPrimary, int textSecondary, int accent) {
            this.cardBg = cardBg;
            this.textPrimary = textPrimary;
            this.textSecondary = textSecondary;
            this.accent = accent;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_file_row, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            File f = largestFiles.get(position);
            h.rowCard.setCardBackgroundColor(cardBg);
            h.tvName.setText("#" + (position + 1) + "  " + f.getName());
            h.tvName.setTextColor(textPrimary);
            h.tvMeta.setText(StorageUtils.formatSize(f.length()) + "  •  " + f.getParent());
            h.tvMeta.setTextColor(textSecondary);
            h.tvHint.setText("⋮");
            h.tvHint.setTextColor(accent);

            View.OnClickListener openMenu = v -> showHeavyFileActions(f);
            h.rowCard.setOnClickListener(openMenu);
            h.tvHint.setOnClickListener(openMenu);
        }

        @Override
        public int getItemCount() {
            return largestFiles.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final CardView rowCard;
            final TextView tvName;
            final TextView tvMeta;
            final TextView tvHint;

            VH(@NonNull View itemView) {
                super(itemView);
                rowCard = itemView.findViewById(R.id.rowCard);
                tvName = itemView.findViewById(R.id.tvFileName);
                tvMeta = itemView.findViewById(R.id.tvFileMeta);
                tvHint = itemView.findViewById(R.id.tvFileActionHint);
                ImageView iv = itemView.findViewById(R.id.ivFileIcon);
                iv.setImageResource(R.drawable.ic_documents);
            }
        }
    }
}
