package com.aishhatorah.filemanagerpro;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * סעיף 36 + 9: עורך וידאו מקצועי עם פס חיתוך משולב ואפשרות ייצוא ל-GIF.
 */
public class ProVideoEditorActivity extends AppCompatActivity {

    public static final String EXTRA_VIDEO_PATH = "extra_video_path";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private File sourceVideo;
    private TextureView textureView;
    private MediaPlayer previewPlayer;
    private int durationMs = 1000;
    private int trimStartMs = 0;
    private int trimEndMs = 1000;
    private int rotationDegrees = 0;
    private boolean muteAudio = false;
    private float previewSpeed = 1.0f;

    private TextView tvRangeSummary;
    private RangeWaveformTrimView rangeTrimView;
    private Button btnExport;
    private Button btnShareOutput;
    private File lastExportedFile = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String path = getIntent().getStringExtra(EXTRA_VIDEO_PATH);
        if (path == null) {
            finish();
            return;
        }
        sourceVideo = new File(path);
        if (!sourceVideo.exists()) {
            Toast.makeText(this, "קובץ הוידאו אינו זמין", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        int theme = PrefsManager.getThemeMode(this);
        ThemeHelper.applyWindowStatusBar(this, theme);
        int bgColor = ThemeHelper.getBgColor(this, theme);
        int cardColor = ThemeHelper.getCardColor(this, theme);
        int textPrimary = ThemeHelper.getPrimaryTextColor(this, theme);
        int accent = ThemeHelper.getAccentColor(this, theme);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(bgColor);

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(ThemeHelper.getTopBarColor(this, theme));
        topBar.setPadding(24, 20, 24, 20);

        Button btnClose = ThemedDialogHelper.createThemedButton(this, "סגור", theme, false, false);
        btnClose.setOnClickListener(v -> finish());
        topBar.addView(btnClose);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("עורך וידאו מקצועי");
        tvTitle.setTextSize(16.5f);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setTextColor(textPrimary);
        tvTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tLp.setMarginStart(14);
        tLp.setMarginEnd(14);
        topBar.addView(tvTitle, tLp);

        btnExport = ThemedDialogHelper.createThemedButton(this, "ייצא ושמור", theme, true, false);
        btnExport.setOnClickListener(v -> exportEditedVideo());
        topBar.addView(btnExport);

        root.addView(topBar);

        FrameLayout previewBox = new FrameLayout(this);
        previewBox.setBackgroundColor(Color.BLACK);
        textureView = new TextureView(this);
        previewBox.addView(textureView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        root.addView(previewBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        ScrollView toolsScroll = new ScrollView(this);
        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.VERTICAL);
        tools.setPadding(30, 24, 30, 34);
        tools.setBackgroundColor(cardColor);

        tvRangeSummary = new TextView(this);
        tvRangeSummary.setTextColor(textPrimary);
        tvRangeSummary.setTextSize(13.5f);
        tvRangeSummary.setTypeface(Typeface.DEFAULT_BOLD);
        tvRangeSummary.setPadding(0, 0, 0, 12);
        tools.addView(tvRangeSummary);

        rangeTrimView = new RangeWaveformTrimView(this);
        rangeTrimView.setAccentColor(accent);
        rangeTrimView.setOnRangeChangedListener((s, e, movingStart) -> {
            trimStartMs = (int) s;
            trimEndMs = (int) e;
            updateSummaryLabel();
            if (previewPlayer != null && movingStart) {
                try { previewPlayer.seekTo(trimStartMs); } catch (Exception ignored) {}
            }
        });
        tools.addView(rangeTrimView);

        HorizontalScrollView chipsScroll = new HorizontalScrollView(this);
        chipsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chipsRow = new LinearLayout(this);
        chipsRow.setOrientation(LinearLayout.HORIZONTAL);
        chipsRow.setPadding(0, 16, 0, 10);

        addChip(chipsRow, "נגן / השהה מקטע", theme, () -> {
            if (previewPlayer == null) return;
            try {
                if (previewPlayer.isPlaying()) previewPlayer.pause();
                else {
                    previewPlayer.seekTo(trimStartMs);
                    previewPlayer.start();
                }
            } catch (Exception ignored) {}
        });

        addChip(chipsRow, "סובב כיוון 90°", theme, () -> {
            rotationDegrees = (rotationDegrees + 90) % 360;
            textureView.setRotation(rotationDegrees);
            updateSummaryLabel();
        });

        addChip(chipsRow, "ייצא כ-GIF מונפש", theme, this::exportAsGif);

        float[] speeds = {0.5f, 1.0f, 1.5f, 2.0f};
        for (float sp : speeds) {
            addChip(chipsRow, String.format(Locale.US, "מהירות %.1fx", sp), theme, () -> {
                previewSpeed = sp;
                if (previewPlayer != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        PlaybackParams p = previewPlayer.getPlaybackParams();
                        if (p == null) p = new PlaybackParams();
                        p.setSpeed(previewSpeed);
                        previewPlayer.setPlaybackParams(p);
                    } catch (Exception ignored) {}
                }
                updateSummaryLabel();
            });
        }

        chipsScroll.addView(chipsRow);
        tools.addView(chipsScroll);

        LinearLayout muteRow = new LinearLayout(this);
        muteRow.setOrientation(LinearLayout.HORIZONTAL);
        muteRow.setGravity(Gravity.CENTER_VERTICAL);
        muteRow.setPadding(0, 10, 0, 10);

        TextView tvMute = new TextView(this);
        tvMute.setText("הסר ערוץ שמע (ייצוא מושתק)");
        tvMute.setTextColor(textPrimary);
        tvMute.setTextSize(13f);
        tvMute.setTypeface(Typeface.DEFAULT_BOLD);
        muteRow.addView(tvMute, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch swMute = new Switch(this);
        swMute.setChecked(false);
        swMute.setOnCheckedChangeListener((btn, checked) -> {
            muteAudio = checked;
            if (previewPlayer != null) {
                float vol = checked ? 0f : 1f;
                previewPlayer.setVolume(vol, vol);
            }
            updateSummaryLabel();
        });
        muteRow.addView(swMute);
        tools.addView(muteRow);

        btnShareOutput = ThemedDialogHelper.createThemedButton(this, "שיתוף הקובץ הערוך", theme, false, false);
        LinearLayout.LayoutParams shLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        shLp.topMargin = 14;
        btnShareOutput.setLayoutParams(shLp);
        btnShareOutput.setVisibility(View.GONE);
        btnShareOutput.setOnClickListener(v -> {
            if (lastExportedFile != null && lastExportedFile.exists()) {
                try {
                    Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", lastExportedFile);
                    Intent share = new Intent(Intent.ACTION_SEND);
                    share.setType(lastExportedFile.getName().endsWith(".gif") ? "image/gif" : "video/*");
                    share.putExtra(Intent.EXTRA_STREAM, uri);
                    share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(share, "שיתוף קובץ ערוך"));
                } catch (Exception e) {
                    Toast.makeText(this, "שגיאה בשיתוף: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });
        tools.addView(btnShareOutput);

        toolsScroll.addView(tools);
        root.addView(toolsScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 620));

        setContentView(root);
        setupTexturePreview();
    }

    private void addChip(LinearLayout row, String label, int theme, Runnable onClick) {
        Button b = ThemedDialogHelper.createThemedButton(this, label, theme, false, false);
        b.setTextSize(12f);
        b.setPadding(24, 16, 24, 16);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(12);
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> onClick.run());
        row.addView(b);
    }

    private void setupTexturePreview() {
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
                try {
                    previewPlayer = new MediaPlayer();
                    previewPlayer.setDataSource(sourceVideo.getAbsolutePath());
                    previewPlayer.setSurface(new Surface(surface));
                    previewPlayer.setLooping(true);
                    previewPlayer.prepare();
                    durationMs = Math.max(1000, previewPlayer.getDuration());
                    trimStartMs = 0;
                    trimEndMs = durationMs;
                    rangeTrimView.setDurationAndRange(durationMs, trimStartMs, trimEndMs);
                    updateSummaryLabel();
                    previewPlayer.start();
                } catch (Exception e) {
                    Toast.makeText(ProVideoEditorActivity.this, "שגיאה בטעינת תצוגה מקדימה: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
            @Override public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {}
            @Override public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
                releasePreview();
                return true;
            }
            @Override public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {}
        });
    }

    private void updateSummaryLabel() {
        String soundTxt = muteAudio ? "ללא קול" : "עם שמע";
        tvRangeSummary.setText(String.format(Locale.US,
                "טווח: %s – %s  •  סיבוב: %d°  •  %s  •  %.1fx",
                formatClock(trimStartMs), formatClock(trimEndMs), rotationDegrees, soundTxt, previewSpeed));
    }

    private String formatClock(int ms) {
        int sec = Math.max(0, ms / 1000);
        return String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60);
    }

    private void exportAsGif() {
        Toast.makeText(this, "מייצא GIF מונפש...", Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            try {
                File outDir = sourceVideo.getParentFile();
                if (outDir == null || !outDir.canWrite()) {
                    outDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                }
                String base = sourceVideo.getName().replaceAll("\\.[^.]+$", "");
                File outGif = StorageUtils.uniqueDestinationFile(outDir, base + ".gif");
                GifToolsHelper.videoToGif(this, sourceVideo, outGif, 420, 10);
                runOnUiThread(() -> {
                    lastExportedFile = outGif;
                    btnShareOutput.setVisibility(View.VISIBLE);
                    Toast.makeText(this, "ה-GIF נשמר: " + outGif.getName(), Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "שגיאה ביצירת GIF: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void exportEditedVideo() {
        btnExport.setEnabled(false);
        Toast.makeText(this, "מייצא סרטון ערוך באיכות מלאה...", Toast.LENGTH_SHORT).show();

        final long startUs = trimStartMs * 1000L;
        final long endUs = Math.max(startUs + 500_000L, trimEndMs * 1000L);
        final int rot = rotationDegrees;
        final boolean dropAudio = muteAudio;

        executor.execute(() -> {
            MediaExtractor extractor = new MediaExtractor();
            MediaMuxer muxer = null;
            try {
                File outDir = sourceVideo.getParentFile();
                if (outDir == null || !outDir.canWrite()) {
                    outDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
                }
                if (!outDir.exists()) outDir.mkdirs();

                String base = sourceVideo.getName().replaceAll("\\.[^.]+$", "");
                File outFile = StorageUtils.uniqueDestinationFile(outDir, base + "_ערוך.mp4");

                extractor.setDataSource(sourceVideo.getAbsolutePath());
                int trackCount = extractor.getTrackCount();
                muxer = new MediaMuxer(outFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                muxer.setOrientationHint(rot);

                Map<Integer, Integer> indexMap = new HashMap<>();
                int maxBufSize = 1024 * 1024;

                for (int i = 0; i < trackCount; i++) {
                    MediaFormat fmt = extractor.getTrackFormat(i);
                    String mime = fmt.getString(MediaFormat.KEY_MIME);
                    if (mime == null) continue;
                    if (mime.startsWith("video/") || (!dropAudio && mime.startsWith("audio/"))) {
                        extractor.selectTrack(i);
                        int dstIdx = muxer.addTrack(fmt);
                        indexMap.put(i, dstIdx);
                        if (fmt.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                            maxBufSize = Math.max(maxBufSize, fmt.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE));
                        }
                    }
                }

                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                ByteBuffer buf = ByteBuffer.allocate(maxBufSize);
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                muxer.start();

                while (true) {
                    info.offset = 0;
                    info.size = extractor.readSampleData(buf, 0);
                    if (info.size < 0) break;
                    long pts = extractor.getSampleTime();
                    if (pts > endUs) break;

                    int srcTrack = extractor.getSampleTrackIndex();
                    Integer dstTrack = indexMap.get(srcTrack);
                    if (dstTrack != null && pts >= startUs) {
                        info.presentationTimeUs = Math.max(0L, (long) ((pts - startUs) / previewSpeed));
                        int sampleFlags = extractor.getSampleFlags();
                        info.flags = (sampleFlags & MediaExtractor.SAMPLE_FLAG_SYNC) != 0
                                ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
                        muxer.writeSampleData(dstTrack, buf, info);
                    }
                    extractor.advance();
                }

                muxer.stop();
                final File saved = outFile;
                runOnUiThread(() -> {
                    btnExport.setEnabled(true);
                    lastExportedFile = saved;
                    btnShareOutput.setVisibility(View.VISIBLE);
                    Toast.makeText(this, "הסרטון הערוך נשמר: " + saved.getName(), Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    btnExport.setEnabled(true);
                    Toast.makeText(this, "שגיאה בייצוא הסרטון: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            } finally {
                try { extractor.release(); } catch (Exception ignored) {}
                if (muxer != null) try { muxer.release(); } catch (Exception ignored) {}
            }
        });
    }

    private void releasePreview() {
        if (previewPlayer != null) {
            try { previewPlayer.stop(); } catch (Exception ignored) {}
            try { previewPlayer.release(); } catch (Exception ignored) {}
            previewPlayer = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releasePreview();
        executor.shutdownNow();
    }
}
