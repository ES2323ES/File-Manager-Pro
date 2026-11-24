package com.aishhatorah.filemanagerpro;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.PriorityQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class StorageAnalyzerActivity extends AppCompatActivity {

    private static final int TOP_FILES_LIMIT = 50;

    private static final class SizedFile {
        final File file;
        final long size;

        SizedFile(File file, long size) {
            this.file = file;
            this.size = size;
        }
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicInteger scanGen = new AtomicInteger(0);
    private final List<SizedFile> largestFiles = new ArrayList<>();

    private TextView tvStatusSummary;
    private ProgressBar scanSpinner;
    private HeavyAdapter adapter;
    private AudioPlayerManager.PlaybackListener miniAudioListener;

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int theme = PrefsManager.getThemeMode(this);
        ThemeHelper.applyWindowStatusBar(this, theme);

        int bgColor = ThemeHelper.getBgColor(this, theme);
        int cardColor = ThemeHelper.getCardColor(this, theme);
        int textPrimary = ThemeHelper.getPrimaryTextColor(this, theme);
        int textSecondary = ThemeHelper.getSecondaryTextColor(this, theme);
        int accent = ThemeHelper.getAccentColor(this, theme);
        boolean dark = ThemeHelper.isDarkSurface(this, theme);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        ThemeHelper.applyWallpaperBackdrop(this, root, theme);

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(10), dp(8), dp(12), dp(10));
        ThemeHelper.applyCurvedHeaderStyle(this, topBar, theme);

        ImageButton btnBack = ThemeHelper.createBorderlessSvgButton(
                this, R.drawable.ic_arrow_back, accent, "חזרה", 40, 8, v -> finish());
        topBar.addView(btnBack);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("ניתוח — הקבצים הגדולים ביותר");
        tvTitle.setTextSize(ThemeHelper.spScaled(this, 16.5f));
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setTextColor(textPrimary);
        LinearLayout.LayoutParams lpTitle = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lpTitle.setMarginStart(dp(6));
        topBar.addView(tvTitle, lpTitle);

        Button btnRescan = ThemedDialogHelper.createThemedButton(this, "סרוק שוב", theme, true, false);
        btnRescan.setOnClickListener(v -> runHeavyFilesScan());
        topBar.addView(btnRescan);

        root.addView(topBar);

        tvStatusSummary = new TextView(this);
        tvStatusSummary.setTextColor(ThemeHelper.hasWallpaper(this) ? textPrimary : textSecondary);
        tvStatusSummary.setTextSize(ThemeHelper.spScaled(this, 13f));
        tvStatusSummary.setPadding(dp(16), dp(10), dp(16), dp(8));
        root.addView(tvStatusSummary);

        scanSpinner = new ProgressBar(this);
        root.addView(scanSpinner);

        RecyclerView rv = new RecyclerView(this);
        rv.setClipToPadding(false);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setItemAnimator(null);
        if (ThemeHelper.hasWallpaper(this)) {
            rv.setBackgroundColor(ColorUtils.setAlphaComponent(bgColor, dark ? 136 : 156));
        }
        final int divColor = ThemeHelper.getDividerColor(this, theme);
        final float density = getResources().getDisplayMetrics().density;
        rv.addItemDecoration(new RecyclerView.ItemDecoration() {
            private final Paint paint = new Paint();

            @Override
            public void onDrawOver(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                paint.setColor(divColor);
                paint.setStrokeWidth(Math.max(1f, density));
                int count = parent.getChildCount();
                int left = parent.getPaddingLeft();
                int right = parent.getWidth() - parent.getPaddingRight();
                for (int i = 0; i < count; i++) {
                    View child = parent.getChildAt(i);
                    float y = child.getBottom();
                    c.drawLine(left, y, right, y, paint);
                }
            }
        });
        adapter = new HeavyAdapter(cardColor, textPrimary, textSecondary, accent);
        rv.setAdapter(adapter);

        FrameLayout overlayHost = new FrameLayout(this);
        overlayHost.addView(rv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        miniAudioListener = MiniAudioBarHelper.attachOverlay(this, overlayHost, rv);

        root.addView(overlayHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        runHeavyFilesScan();
    }

    private void runHeavyFilesScan() {
        final int gen = scanGen.incrementAndGet();
        scanSpinner.setVisibility(View.VISIBLE);
        tvStatusSummary.setText("סורק אחסון פנימי וכרטיס SD לאיתור הקבצים הכבדים ביותר...");

        executor.execute(() -> {
            PriorityQueue<SizedFile> minHeap = new PriorityQueue<>(TOP_FILES_LIMIT + 1,
                    (a, b) -> Long.compare(a.size, b.size));

            File internalRoot = Environment.getExternalStorageDirectory();
            if (internalRoot != null) {
                scanRecursive(internalRoot.toPath(), minHeap, 0, gen);
            }

            StorageUtils.StorageStat sd = StorageUtils.getExternalSdCardStat(this);
            if (sd.available && sd.root != null) {
                scanRecursive(sd.root.toPath(), minHeap, 0, gen);
            }
            if (scanGen.get() != gen) return;

            List<SizedFile> top = new ArrayList<>(minHeap);
            Collections.sort(top, (a, b) -> Long.compare(b.size, a.size));

            StorageUtils.StorageStat st = StorageUtils.getInternalStorageStat();
            String alertNote = StorageUtils.checkAndNotify96Threshold(this);

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || scanGen.get() != gen) return;
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

    private void scanRecursive(Path dir, PriorityQueue<SizedFile> minHeap, int depth, int gen) {
        if (dir == null || depth > 8 || scanGen.get() != gen) return;
        Path dirNamePath = dir.getFileName();
        String dirName = dirNamePath != null ? dirNamePath.toString() : "";
        if ("Android".equals(dirName) || ".FileManagerPro_Trash".equals(dirName)) return;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path child : stream) {
                if (scanGen.get() != gen) return;
                BasicFileAttributes attr;
                try {
                    attr = Files.readAttributes(child, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                } catch (Throwable ignored) {
                    continue;
                }
                if (attr.isSymbolicLink()) continue;
                if (attr.isDirectory()) {
                    scanRecursive(child, minHeap, depth + 1, gen);
                } else {
                    long len = attr.size();
                    if (len > 1024L * 100L) {
                        if (minHeap.size() < TOP_FILES_LIMIT) {
                            minHeap.offer(new SizedFile(child.toFile(), len));
                        } else if (!minHeap.isEmpty() && len > minHeap.peek().size) {
                            minHeap.poll();
                            minHeap.offer(new SizedFile(child.toFile(), len));
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void showHeavyFileActions(File f, View anchor) {
        List<String> options = Arrays.asList(
                "פתיחה בתוך האפליקציה",
                "העבר לסל המיחזור (" + StorageUtils.formatSize(f.length()) + ")",
                "מחק לצמיתות"
        );
        ThemedDialogHelper.showThemedMenu(
                this,
                anchor,
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
            SizedFile entry = largestFiles.get(position);
            File f = entry.file;
            h.rowCard.setCardBackgroundColor(Color.TRANSPARENT);
            h.tvName.setText("#" + (position + 1) + "  " + f.getName());
            h.tvName.setTextColor(textPrimary);
            h.tvMeta.setText(StorageUtils.formatSize(entry.size) + "  •  " + f.getParent());
            h.tvMeta.setTextColor(textSecondary);
            // סעיף ד + סעיף ח: האייקון האמיתי משמאל פותח בלחיצה את אפשרויות העריכה
            FileThumbnailLoader.bindFileIcon(StorageAnalyzerActivity.this, f, h.ivIcon);

            View.OnClickListener openMenu = v -> showHeavyFileActions(f, v);
            h.ivIcon.setOnClickListener(openMenu);
            h.rowCard.setOnClickListener(openMenu);
            h.rowCard.setOnLongClickListener(v -> {
                showHeavyFileActions(f, v);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return largestFiles.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final CardView rowCard;
            final ImageView ivIcon;
            final TextView tvName;
            final TextView tvMeta;
            final TextView tvHint;

            VH(@NonNull View itemView) {
                super(itemView);
                rowCard = itemView.findViewById(R.id.rowCard);
                ivIcon = itemView.findViewById(R.id.ivFileIcon);
                tvName = itemView.findViewById(R.id.tvFileName);
                tvMeta = itemView.findViewById(R.id.tvFileMeta);
                tvHint = itemView.findViewById(R.id.tvFileActionHint);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        scanGen.incrementAndGet();
        if (miniAudioListener != null) {
            AudioPlayerManager.getInstance().removeListener(miniAudioListener);
        }
        executor.shutdownNow();
    }
}
