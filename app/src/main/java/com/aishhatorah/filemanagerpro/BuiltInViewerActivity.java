package com.aishhatorah.filemanagerpro;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfRenderer;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BuiltInViewerActivity extends AppCompatActivity {

    public static final String EXTRA_FILE_PATH = "extra_file_path";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private File targetFile;
    private MediaPlayer audioPlayer;
    private boolean audioUpdateActive = false;

    private ParcelFileDescriptor pdfDescriptor;
    private PdfRenderer pdfRenderer;
    private int currentPdfPage = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String path = getIntent().getStringExtra(EXTRA_FILE_PATH);
        if (path == null) {
            finish();
            return;
        }
        targetFile = new File(path);
        if (!targetFile.exists()) {
            Toast.makeText(this, "הקובץ אינו קיים", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

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

        // סרגל עליון מובנה של מציג הקבצים הפנימי
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(ThemeHelper.getTopBarColor(theme));
        topBar.setPadding(24, 20, 24, 20);

        Button btnClose = ThemedDialogHelper.createThemedButton(this, "סגור", theme, false, false);
        btnClose.setOnClickListener(v -> finish());
        topBar.addView(btnClose);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(targetFile.getName());
        tvTitle.setTextSize(15.5f);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setTextColor(textPrimary);
        tvTitle.setSingleLine(true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleLp.setMarginStart(16);
        titleLp.setMarginEnd(16);
        topBar.addView(tvTitle, titleLp);

        Button btnShare = ThemedDialogHelper.createThemedButton(this, "שיתוף", theme, true, false);
        btnShare.setOnClickListener(v -> shareCurrentFile());
        topBar.addView(btnShare);

        root.addView(topBar);

        LinearLayout contentArea = new LinearLayout(this);
        contentArea.setOrientation(LinearLayout.VERTICAL);
        contentArea.setGravity(Gravity.CENTER);
        contentArea.setPadding(28, 28, 28, 28);
        root.addView(contentArea, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);

        String lower = targetFile.getName().toLowerCase(Locale.ROOT);
        if (endsWithAny(lower, ".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp")) {
            renderImageViewer(contentArea, cardColor, textPrimary, textSecondary, theme);
        } else if (endsWithAny(lower, ".mp3", ".m4a", ".wav", ".aac", ".flac", ".ogg")) {
            renderAudioPlayer(contentArea, cardColor, textPrimary, textSecondary, accent, theme);
        } else if (endsWithAny(lower, ".mp4", ".mkv", ".mov", ".avi", ".webm", ".3gp")) {
            renderVideoPlayer(contentArea, cardColor, textPrimary, textSecondary, theme);
        } else if (lower.endsWith(".pdf")) {
            renderPdfViewer(contentArea, cardColor, textPrimary, textSecondary, theme);
        } else if (endsWithAny(lower, ".zip", ".jar")) {
            renderArchiveExtractor(contentArea, cardColor, textPrimary, textSecondary, theme);
        } else if (endsWithAny(lower, ".docx", ".xlsx", ".pptx")) {
            renderOfficeDocumentViewer(contentArea, cardColor, textPrimary, textSecondary);
        } else {
            renderTextDocumentViewer(contentArea, cardColor, textPrimary, textSecondary);
        }
    }

    private void renderImageViewer(LinearLayout host, int cardColor, int textPrimary, int textSecondary, int theme) {
        LinearLayout card = createSurfaceCard(cardColor);
        card.setGravity(Gravity.CENTER);

        ImageView imageView = new ImageView(this);
        imageView.setAdjustViewBounds(true);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(targetFile.getAbsolutePath(), opts);
        int sample = 1;
        while (opts.outWidth / sample > 2048 || opts.outHeight / sample > 2048) {
            sample *= 2;
        }
        BitmapFactory.Options loadOpts = new BitmapFactory.Options();
        loadOpts.inSampleSize = sample;
        Bitmap bmp = BitmapFactory.decodeFile(targetFile.getAbsolutePath(), loadOpts);
        if (bmp != null) {
            imageView.setImageBitmap(bmp);
        }

        LinearLayout.LayoutParams imgLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        card.addView(imageView, imgLp);

        TextView tvMeta = new TextView(this);
        tvMeta.setText(String.format(Locale.US, "רזולוציה: %dx%d  •  נפח: %s",
                Math.max(0, opts.outWidth), Math.max(0, opts.outHeight), StorageUtils.formatSize(targetFile.length())));
        tvMeta.setTextColor(textSecondary);
        tvMeta.setTextSize(12.5f);
        tvMeta.setGravity(Gravity.CENTER);
        tvMeta.setPadding(0, 18, 0, 0);
        card.addView(tvMeta);

        final boolean[] zoomed = {false};
        Button btnZoom = ThemedDialogHelper.createThemedButton(this, "הגדלה / התאמה למסך", theme, false, false);
        LinearLayout.LayoutParams zLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        zLp.topMargin = 16;
        btnZoom.setLayoutParams(zLp);
        btnZoom.setOnClickListener(v -> {
            zoomed[0] = !zoomed[0];
            imageView.setScaleType(zoomed[0] ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
        });
        card.addView(btnZoom);

        host.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void renderAudioPlayer(LinearLayout host, int cardColor, int textPrimary, int textSecondary, int accent, int theme) {
        LinearLayout card = createSurfaceCard(cardColor);
        card.setGravity(Gravity.CENTER);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_audio);
        LinearLayout.LayoutParams icLp = new LinearLayout.LayoutParams(160, 160);
        icLp.bottomMargin = 24;
        card.addView(icon, icLp);

        TextView tvName = new TextView(this);
        tvName.setText(targetFile.getName());
        tvName.setTextColor(textPrimary);
        tvName.setTextSize(17f);
        tvName.setTypeface(Typeface.DEFAULT_BOLD);
        tvName.setGravity(Gravity.CENTER);
        card.addView(tvName);

        TextView tvTime = new TextView(this);
        tvTime.setText("00:00 / 00:00");
        tvTime.setTextColor(accent);
        tvTime.setTextSize(14f);
        tvTime.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tvTime.setPadding(0, 20, 0, 16);
        card.addView(tvTime);

        SeekBar seekBar = new SeekBar(this);
        card.addView(seekBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(0, 28, 0, 12);

        Button btnBack10 = ThemedDialogHelper.createThemedButton(this, "-10 ש׳", theme, false, false);
        Button btnPlayPause = ThemedDialogHelper.createThemedButton(this, "השהה", theme, true, false);
        Button btnFwd10 = ThemedDialogHelper.createThemedButton(this, "+10 ש׳", theme, false, false);

        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        cLp.setMargins(8, 0, 8, 0);
        btnBack10.setLayoutParams(cLp);
        btnPlayPause.setLayoutParams(cLp);
        btnFwd10.setLayoutParams(cLp);

        controls.addView(btnBack10);
        controls.addView(btnPlayPause);
        controls.addView(btnFwd10);
        card.addView(controls, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button btnOpenCutter = ThemedDialogHelper.createThemedButton(this, "פתח בחותך האודיו המקצועי", theme, false, false);
        LinearLayout.LayoutParams cutLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cutLp.topMargin = 18;
        btnOpenCutter.setLayoutParams(cutLp);
        btnOpenCutter.setOnClickListener(v -> {
            Intent i = new Intent(this, AudioCutterActivity.class);
            i.putExtra(AudioCutterActivity.EXTRA_INITIAL_PATH, targetFile.getAbsolutePath());
            startActivity(i);
        });
        card.addView(btnOpenCutter);

        host.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        try {
            audioPlayer = new MediaPlayer();
            audioPlayer.setDataSource(targetFile.getAbsolutePath());
            audioPlayer.prepare();
            int total = audioPlayer.getDuration();
            seekBar.setMax(Math.max(1, total));
            audioPlayer.start();
            audioUpdateActive = true;

            Runnable ticker = new Runnable() {
                @Override
                public void run() {
                    if (!audioUpdateActive || audioPlayer == null) return;
                    try {
                        int pos = audioPlayer.getCurrentPosition();
                        seekBar.setProgress(pos);
                        tvTime.setText(formatClock(pos) + " / " + formatClock(total));
                    } catch (Exception ignored) {
                    }
                    uiHandler.postDelayed(this, 300);
                }
            };
            uiHandler.post(ticker);

            btnPlayPause.setOnClickListener(v -> {
                if (audioPlayer == null) return;
                if (audioPlayer.isPlaying()) {
                    audioPlayer.pause();
                    btnPlayPause.setText("נגן");
                } else {
                    audioPlayer.start();
                    btnPlayPause.setText("השהה");
                }
            });

            btnBack10.setOnClickListener(v -> {
                if (audioPlayer != null) {
                    audioPlayer.seekTo(Math.max(0, audioPlayer.getCurrentPosition() - 10000));
                }
            });

            btnFwd10.setOnClickListener(v -> {
                if (audioPlayer != null) {
                    audioPlayer.seekTo(Math.min(total, audioPlayer.getCurrentPosition() + 10000));
                }
            });

            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                    if (fromUser && audioPlayer != null) audioPlayer.seekTo(progress);
                }
                @Override public void onStartTrackingTouch(SeekBar sb) {}
                @Override public void onStopTrackingTouch(SeekBar sb) {}
            });

            audioPlayer.setOnCompletionListener(mp -> btnPlayPause.setText("נגן שוב"));
        } catch (Exception e) {
            Toast.makeText(this, "שגיאה בניגון קובץ השמע: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void renderVideoPlayer(LinearLayout host, int cardColor, int textPrimary, int textSecondary, int theme) {
        LinearLayout card = createSurfaceCard(cardColor);
        card.setGravity(Gravity.CENTER);

        VideoView videoView = new VideoView(this);
        LinearLayout.LayoutParams vLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        card.addView(videoView, vLp);

        MediaController controller = new MediaController(this);
        controller.setAnchorView(videoView);
        videoView.setMediaController(controller);
        videoView.setVideoPath(targetFile.getAbsolutePath());
        videoView.setOnPreparedListener(mp -> videoView.start());

        LinearLayout bottomRow = new LinearLayout(this);
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setPadding(0, 18, 0, 0);

        Button btnToggle = ThemedDialogHelper.createThemedButton(this, "נגן / השהה", theme, true, false);
        LinearLayout.LayoutParams b1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        b1.setMarginEnd(10);
        btnToggle.setLayoutParams(b1);
        btnToggle.setOnClickListener(v -> {
            if (videoView.isPlaying()) videoView.pause();
            else videoView.start();
        });

        Button btnCutter = ThemedDialogHelper.createThemedButton(this, "חתוך בחותך הוידאו", theme, false, false);
        LinearLayout.LayoutParams b2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        btnCutter.setLayoutParams(b2);
        btnCutter.setOnClickListener(v -> {
            videoView.pause();
            Intent i = new Intent(this, VideoCutterActivity.class);
            i.putExtra(VideoCutterActivity.EXTRA_INITIAL_PATH, targetFile.getAbsolutePath());
            startActivity(i);
        });

        bottomRow.addView(btnToggle);
        bottomRow.addView(btnCutter);
        card.addView(bottomRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        host.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void renderPdfViewer(LinearLayout host, int cardColor, int textPrimary, int textSecondary, int theme) {
        LinearLayout card = createSurfaceCard(cardColor);
        card.setGravity(Gravity.CENTER);

        ImageView pageImage = new ImageView(this);
        pageImage.setBackgroundColor(Color.WHITE);
        pageImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        card.addView(pageImage, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView tvPageIndicator = new TextView(this);
        tvPageIndicator.setTextColor(textSecondary);
        tvPageIndicator.setGravity(Gravity.CENTER);
        tvPageIndicator.setPadding(0, 14, 0, 14);
        card.addView(tvPageIndicator);

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);

        Button btnPrev = ThemedDialogHelper.createThemedButton(this, "עמוד קודם", theme, false, false);
        Button btnNext = ThemedDialogHelper.createThemedButton(this, "עמוד הבא", theme, true, false);
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp1.setMarginEnd(12);
        btnPrev.setLayoutParams(lp1);
        btnNext.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        nav.addView(btnPrev);
        nav.addView(btnNext);
        card.addView(nav, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        host.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        try {
            pdfDescriptor = ParcelFileDescriptor.open(targetFile, ParcelFileDescriptor.MODE_READ_ONLY);
            pdfRenderer = new PdfRenderer(pdfDescriptor);
            final int totalPages = pdfRenderer.getPageCount();

            Runnable renderPage = () -> {
                if (pdfRenderer == null || totalPages == 0) return;
                try (PdfRenderer.Page page = pdfRenderer.openPage(currentPdfPage)) {
                    int w = Math.max(800, page.getWidth() * 2);
                    int h = Math.max(1000, page.getHeight() * 2);
                    Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    bmp.eraseColor(Color.WHITE);
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    pageImage.setImageBitmap(bmp);
                    tvPageIndicator.setText("עמוד " + (currentPdfPage + 1) + " מתוך " + totalPages);
                    btnPrev.setEnabled(currentPdfPage > 0);
                    btnNext.setEnabled(currentPdfPage < totalPages - 1);
                }
            };
            renderPage.run();

            btnPrev.setOnClickListener(v -> {
                if (currentPdfPage > 0) {
                    currentPdfPage--;
                    renderPage.run();
                }
            });
            btnNext.setOnClickListener(v -> {
                if (currentPdfPage < totalPages - 1) {
                    currentPdfPage++;
                    renderPage.run();
                }
            });
        } catch (Exception e) {
            tvPageIndicator.setText("לא ניתן להציג קובץ PDF זה: " + e.getMessage());
        }
    }

    private void renderArchiveExtractor(LinearLayout host, int cardColor, int textPrimary, int textSecondary, int theme) {
        LinearLayout card = createSurfaceCard(cardColor);

        TextView tvHeader = new TextView(this);
        tvHeader.setText("תוכן ארכיון ZIP וחילוץ מובנה");
        tvHeader.setTextColor(textPrimary);
        tvHeader.setTextSize(16f);
        tvHeader.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(tvHeader);

        TextView tvSub = new TextView(this);
        tvSub.setText("סורק רשימת קבצים בתוך הארכיון...");
        tvSub.setTextColor(textSecondary);
        tvSub.setTextSize(12.5f);
        tvSub.setPadding(0, 6, 0, 16);
        card.addView(tvSub);

        ScrollView scroll = new ScrollView(this);
        TextView tvEntries = new TextView(this);
        tvEntries.setTextColor(textPrimary);
        tvEntries.setTextSize(13f);
        tvEntries.setLineSpacing(5f, 1.15f);
        scroll.addView(tvEntries);
        card.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Button btnExtract = ThemedDialogHelper.createThemedButton(this, "חלץ את כל הקבצים לתיקייה כאן", theme, true, false);
        LinearLayout.LayoutParams exLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        exLp.topMargin = 18;
        btnExtract.setLayoutParams(exLp);
        btnExtract.setOnClickListener(v -> {
            btnExtract.setEnabled(false);
            executor.execute(() -> {
                try {
                    String baseName = targetFile.getName().replaceAll("(?i)\\.(zip|jar)$", "");
                    File outDir = StorageUtils.uniqueDestinationFile(
                            targetFile.getParentFile() != null ? targetFile.getParentFile() : getFilesDir(),
                            baseName + "_מחולץ"
                    );
                    int count = ArchiveUtils.extractZip(targetFile, outDir);
                    uiHandler.post(() -> {
                        btnExtract.setEnabled(true);
                        ThemedDialogHelper.showCustomDialog(
                                this,
                                "החילוץ הושלם בהצלחה",
                                "חולצו " + count + " קבצים אל התיקייה:\n" + outDir.getAbsolutePath(),
                                null,
                                "הבנתי",
                                this::finish,
                                null,
                                null,
                                false
                        );
                    });
                } catch (Exception e) {
                    uiHandler.post(() -> {
                        btnExtract.setEnabled(true);
                        Toast.makeText(this, "שגיאה בחילוץ: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            });
        });
        card.addView(btnExtract);

        host.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        executor.execute(() -> {
            try {
                List<String> list = ArchiveUtils.listZipEntries(targetFile);
                StringBuilder sb = new StringBuilder();
                for (String item : list) sb.append("• ").append(item).append("\n");
                uiHandler.post(() -> {
                    tvSub.setText("נמצאו " + list.size() + " פריטים בארכיון (" + StorageUtils.formatSize(targetFile.length()) + ")");
                    tvEntries.setText(sb.length() > 0 ? sb.toString() : "הארכיון ריק");
                });
            } catch (Exception e) {
                uiHandler.post(() -> tvSub.setText("שגיאה בקריאת הארכיון: " + e.getMessage()));
            }
        });
    }

    private void renderOfficeDocumentViewer(LinearLayout host, int cardColor, int textPrimary, int textSecondary) {
        LinearLayout card = createSurfaceCard(cardColor);

        TextView tvSub = new TextView(this);
        tvSub.setText("קורא מסמכים מובנה • " + StorageUtils.formatSize(targetFile.length()));
        tvSub.setTextColor(textSecondary);
        tvSub.setTextSize(12f);
        tvSub.setPadding(0, 0, 0, 14);
        card.addView(tvSub);

        ScrollView scroll = new ScrollView(this);
        TextView tvDoc = new TextView(this);
        tvDoc.setText("טוען תוכן מסמך...");
        tvDoc.setTextColor(textPrimary);
        tvDoc.setTextSize(14f);
        tvDoc.setTextIsSelectable(true);
        tvDoc.setLineSpacing(6f, 1.2f);
        scroll.addView(tvDoc);
        card.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        host.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        executor.execute(() -> {
            String content = ArchiveUtils.extractOfficeDocumentText(targetFile);
            uiHandler.post(() -> tvDoc.setText(content));
        });
    }

    private void renderTextDocumentViewer(LinearLayout host, int cardColor, int textPrimary, int textSecondary) {
        LinearLayout card = createSurfaceCard(cardColor);

        TextView tvSub = new TextView(this);
        tvSub.setText("מציג מסמכים מובנה • " + StorageUtils.formatSize(targetFile.length()));
        tvSub.setTextColor(textSecondary);
        tvSub.setTextSize(12f);
        tvSub.setPadding(0, 0, 0, 14);
        card.addView(tvSub);

        ScrollView scroll = new ScrollView(this);
        TextView tvDoc = new TextView(this);
        tvDoc.setTextColor(textPrimary);
        tvDoc.setTextSize(13.5f);
        tvDoc.setTextIsSelectable(true);
        tvDoc.setLineSpacing(5f, 1.2f);
        scroll.addView(tvDoc);
        card.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        host.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        executor.execute(() -> {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(targetFile), StandardCharsets.UTF_8))) {
                char[] buf = new char[4096];
                int read;
                int totalChars = 0;
                while ((read = reader.read(buf)) != -1 && totalChars < 120_000) {
                    sb.append(buf, 0, read);
                    totalChars += read;
                }
            } catch (Exception e) {
                sb.append("לא ניתן להציג קובץ זה כטקסט: ").append(e.getMessage());
            }
            final String text = sb.toString();
            uiHandler.post(() -> tvDoc.setText(text.isEmpty() ? "(קובץ ריק)" : text));
        });
    }

    private LinearLayout createSurfaceCard(int cardColor) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(32, 32, 32, 32);
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(28f);
        shape.setColor(cardColor);
        card.setBackground(shape);
        return card;
    }

    private void shareCurrentFile() {
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", targetFile);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("*/*");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "שיתוף קובץ"));
        } catch (Exception e) {
            Toast.makeText(this, "שגיאה בשיתוף הקובץ: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String formatClock(int ms) {
        int sec = Math.max(0, ms / 1000);
        return String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60);
    }

    private boolean endsWithAny(String name, String... suffixes) {
        for (String s : suffixes) {
            if (name.endsWith(s)) return true;
        }
        return false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        audioUpdateActive = false;
        if (audioPlayer != null) {
            try { audioPlayer.stop(); } catch (Exception ignored) {}
            try { audioPlayer.release(); } catch (Exception ignored) {}
            audioPlayer = null;
        }
        if (pdfRenderer != null) {
            try { pdfRenderer.close(); } catch (Exception ignored) {}
        }
        if (pdfDescriptor != null) {
            try { pdfDescriptor.close(); } catch (Exception ignored) {}
        }
        executor.shutdownNow();
    }
}
