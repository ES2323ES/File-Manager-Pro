package com.aishhatorah.filemanagerpro;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Matrix;
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
import android.widget.ImageButton;
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
    private boolean isMtsOrTsSource = false;
    private TextureView textureView;
    private Surface previewSurface;
    private VlcMtsPlayerHelper vlcPreviewPlayer;
    private MediaPlayer previewPlayer;
    private int durationMs = 1000;
    private int trimStartMs = 0;
    private int trimEndMs = 1000;
    private int rotationDegrees = 0;
    private int previewVideoWidth = 0;
    private int previewVideoHeight = 0;
    private float previewPixelRatio = 1.0f;
    private long lastPreviewSeekMs = 0L;
    private boolean muteAudio = false;
    private float previewSpeed = 1.0f;
    private VideoCompressor.QualityPreset selectedPreset = VideoCompressor.QualityPreset.ORIGINAL;

    private TextView tvRangeSummary;
    private TextView tvCompressionEstimate;
    private final Button[] presetButtons = new Button[4];
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
        isMtsOrTsSource = MpegTsNormalizer.isLikelyMpegTsName(sourceVideo.getName())
                || MpegTsNormalizer.detectTsLayoutCached(sourceVideo).packetStride > 0;

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
        ThemeHelper.applyCurvedHeaderStyle(this, topBar, theme);
        topBar.setPadding(ThemeHelper.dpScaled(this, 10), ThemeHelper.dpScaled(this, 8),
                ThemeHelper.dpScaled(this, 12), ThemeHelper.dpScaled(this, 10));

        ImageButton btnClose = ThemeHelper.createBorderlessSvgButton(
                this, R.drawable.ic_arrow_back, accent, "חזרה", 40, 8, v -> finish());
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
        textureView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if ((right - left) != (oldRight - oldLeft) || (bottom - top) != (oldBottom - oldTop)) {
                updatePreviewTransform();
            }
        });
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
            if (movingStart && (vlcPreviewPlayer != null || previewPlayer != null)) {
                long now = System.currentTimeMillis();
                if (now - lastPreviewSeekMs >= 160L) {
                    lastPreviewSeekMs = now;
                    try {
                        if (vlcPreviewPlayer != null) {
                            vlcPreviewPlayer.seekTo(trimStartMs);
                        } else if (previewPlayer != null) {
                            previewPlayer.seekTo(trimStartMs);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        });
        tools.addView(rangeTrimView);

        TextView tvCompressHeader = new TextView(this);
        tvCompressHeader.setText("הפחתת משקל ודחיסת איכות הווידאו:");
        tvCompressHeader.setTextColor(textPrimary);
        tvCompressHeader.setTextSize(13f);
        tvCompressHeader.setTypeface(Typeface.DEFAULT_BOLD);
        tvCompressHeader.setPadding(0, 14, 0, 8);
        tools.addView(tvCompressHeader);

        HorizontalScrollView presetScroll = new HorizontalScrollView(this);
        presetScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout presetRow = new LinearLayout(this);
        presetRow.setOrientation(LinearLayout.HORIZONTAL);
        VideoCompressor.QualityPreset[] presets = VideoCompressor.QualityPreset.values();
        for (int i = 0; i < presets.length; i++) {
            final VideoCompressor.QualityPreset preset = presets[i];
            Button chip = ThemedDialogHelper.createThemedButton(this, preset.labelHe, theme, preset == selectedPreset, false);
            chip.setTextSize(12f);
            chip.setPadding(22, 14, 22, 14);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(10);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> {
                selectedPreset = preset;
                refreshPresetChipStyles(theme);
                updateSummaryLabel();
            });
            presetButtons[i] = chip;
            presetRow.addView(chip);
        }
        presetScroll.addView(presetRow);
        tools.addView(presetScroll);

        tvCompressionEstimate = new TextView(this);
        tvCompressionEstimate.setTextColor(accent);
        tvCompressionEstimate.setTextSize(12.5f);
        tvCompressionEstimate.setTypeface(Typeface.DEFAULT_BOLD);
        tvCompressionEstimate.setPadding(0, 8, 0, 4);
        tools.addView(tvCompressionEstimate);

        HorizontalScrollView chipsScroll = new HorizontalScrollView(this);
        chipsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chipsRow = new LinearLayout(this);
        chipsRow.setOrientation(LinearLayout.HORIZONTAL);
        chipsRow.setPadding(0, 12, 0, 8);

        addChip(chipsRow, "נגן / השהה מקטע", theme, () -> {
            if (vlcPreviewPlayer != null) {
                if (vlcPreviewPlayer.isPlaying()) vlcPreviewPlayer.pause();
                else {
                    vlcPreviewPlayer.seekTo(trimStartMs);
                    vlcPreviewPlayer.play();
                }
                return;
            }
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
            updatePreviewTransform();
            updateSummaryLabel();
        });

        addChip(chipsRow, "ייצא כ-GIF מונפש", theme, this::exportAsGif);

        float[] speeds = {0.5f, 1.0f, 1.5f, 2.0f};
        for (float sp : speeds) {
            addChip(chipsRow, String.format(Locale.US, "מהירות %.1fx", sp), theme, () -> {
                previewSpeed = sp;
                if (vlcPreviewPlayer != null) {
                    vlcPreviewPlayer.setPlaybackSpeed(previewSpeed);
                } else if (previewPlayer != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
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
        muteRow.setPadding(0, 8, 0, 8);

        TextView tvMute = new TextView(this);
        tvMute.setText("הסר ערוץ שמע (ייצוא מושתק)");
        tvMute.setTextColor(textPrimary);
        tvMute.setTextSize(13f);
        tvMute.setTypeface(Typeface.DEFAULT_BOLD);
        muteRow.addView(tvMute, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch swMute = new Switch(this);
        swMute.setChecked(false);
        ThemeHelper.applySwitchTint(this, swMute, theme);
        swMute.setOnCheckedChangeListener((btn, checked) -> {
            muteAudio = checked;
            if (vlcPreviewPlayer != null) {
                vlcPreviewPlayer.setMuted(checked);
            } else if (previewPlayer != null) {
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
        shLp.topMargin = 12;
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
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.35f));

        setContentView(root);
        setupTexturePreview();
    }

    private void refreshPresetChipStyles(int theme) {
        VideoCompressor.QualityPreset[] presets = VideoCompressor.QualityPreset.values();
        for (int i = 0; i < presets.length; i++) {
            Button chip = presetButtons[i];
            if (chip == null) continue;
            Button styled = ThemedDialogHelper.createThemedButton(
                    this, presets[i].labelHe, theme, presets[i] == selectedPreset, false);
            chip.setBackground(styled.getBackground());
            chip.setTextColor(styled.getTextColors());
        }
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
                previewSurface = new Surface(surface);
                if (isMtsOrTsSource) {
                    startVlcPreview(width, height);
                    return;
                }
                startMediaPlayerPreview();
            }
            @Override public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
                if (vlcPreviewPlayer != null) {
                    vlcPreviewPlayer.updateWindowSize(width, height);
                }
                updatePreviewTransform();
            }
            @Override public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
                if (vlcPreviewPlayer != null) {
                    vlcPreviewPlayer.detachSurface();
                }
                releasePreview();
                previewSurface = null;
                return true;
            }
            @Override public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {}
        });
    }

    private void startVlcPreview(int width, int height) {
        releasePreview();
        try {
            vlcPreviewPlayer = new VlcMtsPlayerHelper(this, new VlcMtsPlayerHelper.Callback() {
                @Override
                public void onPrepared(int durMs) {
                    if (vlcPreviewPlayer == null || isFinishing() || isDestroyed()) return;
                    if (durMs > 1000 && (durationMs <= 1000 || Math.abs(durationMs - durMs) > 500)) {
                        boolean firstInit = durationMs <= 1000;
                        durationMs = durMs;
                        if (firstInit) {
                            trimStartMs = 0;
                            trimEndMs = durationMs;
                            rangeTrimView.setDurationAndRange(durationMs, trimStartMs, trimEndMs);
                        } else {
                            trimEndMs = Math.min(trimEndMs <= 1000 ? durationMs : trimEndMs, durationMs);
                            rangeTrimView.setDurationAndRange(durationMs, trimStartMs, trimEndMs);
                        }
                        updateSummaryLabel();
                    }
                }

                @Override
                public void onVideoSizeChanged(int w, int h, float sar) {
                    if (vlcPreviewPlayer == null || w <= 0 || h <= 0) return;
                    previewVideoWidth = w;
                    previewVideoHeight = h;
                    previewPixelRatio = sar > 0f ? sar : 1.0f;
                    updatePreviewTransform();
                }

                @Override
                public void onFirstFrameRendered() {
                }

                @Override
                public void onPlayingChanged(boolean isPlaying) {
                }

                @Override
                public void onEndReached() {
                }

                @Override
                public void onError() {
                    if (vlcPreviewPlayer != null && !isFinishing() && !isDestroyed()) {
                        startMediaPlayerPreview();
                    }
                }
            });
            vlcPreviewPlayer.setLooping(true);
            if (previewSurface != null) {
                vlcPreviewPlayer.attachSurface(previewSurface, width, height);
            }
            vlcPreviewPlayer.load(sourceVideo, true, muteAudio);

            executor.execute(() -> {
                long tsDur = MpegTsNormalizer.extractTsDurationMs(sourceVideo);
                if (tsDur > 1000L) {
                    final int finalDur = (int) Math.min(Integer.MAX_VALUE, tsDur);
                    runOnUiThread(() -> {
                        if (!isFinishing() && !isDestroyed() && durationMs <= 1000) {
                            durationMs = finalDur;
                            trimStartMs = 0;
                            trimEndMs = durationMs;
                            rangeTrimView.setDurationAndRange(durationMs, trimStartMs, trimEndMs);
                            updateSummaryLabel();
                        }
                    });
                }
            });
        } catch (Throwable ignored) {
            startMediaPlayerPreview();
        }
    }

    private void startMediaPlayerPreview() {
        releasePreview();
        if (previewSurface == null) return;
        try {
            previewPlayer = new MediaPlayer();
            previewPlayer.setDataSource(sourceVideo.getAbsolutePath());
            previewPlayer.setSurface(previewSurface);
            previewPlayer.setLooping(true);
            float vol = muteAudio ? 0f : 1f;
            previewPlayer.setVolume(vol, vol);
            previewPlayer.setOnVideoSizeChangedListener((mp, w, h) -> {
                if (w > 0 && h > 0) {
                    previewVideoWidth = w;
                    previewVideoHeight = h;
                    previewPixelRatio = 1.0f;
                    updatePreviewTransform();
                }
            });
            previewPlayer.prepare();
            previewVideoWidth = previewPlayer.getVideoWidth();
            previewVideoHeight = previewPlayer.getVideoHeight();
            previewPixelRatio = 1.0f;
            updatePreviewTransform();
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

    private void updatePreviewTransform() {
        if (textureView == null) return;
        int vw = textureView.getWidth();
        int vh = textureView.getHeight();
        if (vw <= 0 || vh <= 0 || previewVideoWidth <= 0 || previewVideoHeight <= 0) return;

        float cx = vw * 0.5f;
        float cy = vh * 0.5f;
        int rot = ((rotationDegrees % 360) + 360) % 360;
        float sar = previewPixelRatio > 0.1f ? previewPixelRatio : 1.0f;
        float rawW = (rot == 90 || rot == 270) ? (float) previewVideoHeight : ((float) previewVideoWidth * sar);
        float rawH = (rot == 90 || rot == 270) ? ((float) previewVideoWidth * sar) : (float) previewVideoHeight;
        float effW = (rot == 90 || rot == 270) ? rawH : rawW;
        float effH = (rot == 90 || rot == 270) ? rawW : rawH;
        float fitScale = Math.min((float) vw / effW, (float) vh / effH);

        Matrix m = new Matrix();
        m.setScale(rawW / (float) vw, rawH / (float) vh, cx, cy);
        if (rot != 0) {
            m.postRotate(rot, cx, cy);
        }
        m.postScale(fitScale, fitScale, cx, cy);
        textureView.setRotation(0f);
        textureView.setTransform(m);
    }

    private void updateSummaryLabel() {
        String soundTxt = muteAudio ? "ללא קול" : "עם שמע";
        tvRangeSummary.setText(String.format(Locale.US,
                "טווח: %s – %s  •  סיבוב: %d°  •  %s  •  %.1fx",
                formatClock(trimStartMs), formatClock(trimEndMs), rotationDegrees, soundTxt, previewSpeed));
        if (tvCompressionEstimate != null && sourceVideo != null) {
            long origBytes = sourceVideo.length();
            long clipMs = Math.max(500L, (long) trimEndMs - (long) trimStartMs);
            long estBytes = VideoCompressor.estimateCompressedBytes(
                    clipMs, selectedPreset, origBytes, durationMs, muteAudio, previewSpeed);
            int savedPct = origBytes > 0
                    ? Math.max(0, (int) Math.round(((origBytes - estBytes) * 100.0) / origBytes))
                    : 0;
            String origStr = StorageUtils.formatSize(origBytes);
            String estStr = StorageUtils.formatSize(estBytes);
            tvCompressionEstimate.setText(selectedPreset == VideoCompressor.QualityPreset.ORIGINAL
                    ? ("משקל מקורי: " + origStr + "  •  ייצוא מהיר ללא דחיסה (~" + estStr + ")")
                    : ("משקל מקורי: " + origStr + "  •  אחרי הפחתה: ~" + estStr + " (חיסכון " + savedPct + "%)"));
        }
    }

    private String formatClock(int ms) {
        int sec = Math.max(0, ms / 1000);
        return String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60);
    }

    private void exportAsGif() {
        final OperationProgressHelper.Handle progress = OperationProgressHelper.show(
                this, "ייצוא GIF מונפש", sourceVideo.getName(), false);
        executor.execute(() -> {
            try {
                File outDir = sourceVideo.getParentFile();
                if (outDir == null || !outDir.canWrite()) {
                    outDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                }
                String base = sourceVideo.getName().replaceAll("\\.[^.]+$", "");
                File outGif = StorageUtils.uniqueDestinationFile(outDir, base + ".gif");
                progress.updateIndeterminate(outGif.getName(), "מעבד פריימים ל-GIF...");
                GifToolsHelper.videoToGif(this, sourceVideo, outGif, 420, 10);
                progress.finish();
                runOnUiThread(() -> {
                    lastExportedFile = outGif;
                    btnShareOutput.setVisibility(View.VISIBLE);
                    Toast.makeText(this, "ה-GIF נשמר: " + outGif.getName(), Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                progress.finish();
                runOnUiThread(() -> Toast.makeText(this, "שגיאה ביצירת GIF: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void exportEditedVideo() {
        btnExport.setEnabled(false);
        final VideoCompressor.QualityPreset preset = selectedPreset;
        final boolean compress = preset != VideoCompressor.QualityPreset.ORIGINAL;
        final OperationProgressHelper.Handle progress = OperationProgressHelper.show(
                this,
                compress ? "דחיסת והפחתת משקל וידאו" : "ייצוא סרטון ערוך",
                sourceVideo.getName(),
                true);

        final long startUs = trimStartMs * 1000L;
        final long rawEndUs = Math.max(startUs + 500_000L, trimEndMs * 1000L);
        final long endUs = (durationMs > 0 && trimEndMs >= durationMs - 250) ? Long.MAX_VALUE : rawEndUs;
        final int rot = rotationDegrees;
        final boolean dropAudio = muteAudio;
        final float speedVal = previewSpeed;

        executor.execute(() -> {
            File outDir = sourceVideo.getParentFile();
            if (outDir == null || !outDir.canWrite()) {
                outDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
            }
            if (!outDir.exists()) outDir.mkdirs();

            String base = sourceVideo.getName().replaceAll("\\.[^.]+$", "");
            String suffix = compress ? "_דחוס.mp4" : "_ערוך.mp4";
            File outFile = StorageUtils.uniqueDestinationFile(outDir, base + suffix);

            if (compress) {
                try {
                    VideoCompressor.compressVideo(
                            sourceVideo,
                            outFile,
                            startUs,
                            endUs,
                            rot,
                            dropAudio,
                            speedVal,
                            preset,
                            pct -> {
                                progress.updatePercent(outFile.getName(), pct, preset.labelHe);
                                runOnUiThread(() -> btnExport.setText("דוחס " + pct + "%"));
                            }
                    );
                    progress.finish();
                    final File saved = outFile;
                    long origLen = Math.max(1L, sourceVideo.length());
                    long newLen = saved.length();
                    int savedPct = Math.max(0, (int) Math.round(((origLen - newLen) * 100.0) / origLen));
                    runOnUiThread(() -> {
                        btnExport.setText("ייצא ושמור");
                        btnExport.setEnabled(true);
                        lastExportedFile = saved;
                        btnShareOutput.setVisibility(View.VISIBLE);
                        Toast.makeText(this,
                                "נשמר: " + saved.getName() + " (" + StorageUtils.formatSize(newLen) + ", חיסכון " + savedPct + "%)",
                                Toast.LENGTH_LONG).show();
                    });
                } catch (Exception e) {
                    progress.finish();
                    if (outFile.exists()) outFile.delete();
                    runOnUiThread(() -> {
                        btnExport.setText("ייצא ושמור");
                        btnExport.setEnabled(true);
                        Toast.makeText(this, "שגיאה בדחיסה: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
                return;
            }

            if (isMtsOrTsSource && !dropAudio && rot == 0 && Math.abs(speedVal - 1.0f) < 0.02f) {
                try {
                    TsToMp4Remuxer.remuxToMp4(
                            sourceVideo,
                            outFile,
                            startUs,
                            endUs,
                            pct -> {
                                progress.updatePercent(outFile.getName(), pct, "המרת MTS");
                                runOnUiThread(() -> btnExport.setText("מייצא MTS " + pct + "%"));
                            }
                    );
                    progress.finish();
                    final File saved = outFile;
                    runOnUiThread(() -> {
                        btnExport.setText("ייצא ושמור");
                        btnExport.setEnabled(true);
                        lastExportedFile = saved;
                        btnShareOutput.setVisibility(View.VISIBLE);
                        Toast.makeText(this, "הסרטון הערוך נשמר: " + saved.getName(), Toast.LENGTH_LONG).show();
                    });
                } catch (Exception e) {
                    progress.finish();
                    if (outFile.exists()) outFile.delete();
                    runOnUiThread(() -> {
                        btnExport.setText("ייצא ושמור");
                        btnExport.setEnabled(true);
                        Toast.makeText(this, "שגיאה בייצוא MTS: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
                return;
            }

            MediaExtractor extractor = new MediaExtractor();
            MediaMuxer muxer = null;
            File tempMtsRemux = null;
            try {
                File mp4Input = sourceVideo;
                long extStartUs = startUs;
                long extEndUs = endUs;
                if (isMtsOrTsSource) {
                    tempMtsRemux = new File(getCacheDir(), "pro_edit_mts_" + System.currentTimeMillis() + ".mp4");
                    TsToMp4Remuxer.remuxToMp4(
                            sourceVideo,
                            tempMtsRemux,
                            startUs,
                            endUs,
                            pct -> progress.updatePercent(outFile.getName(), pct / 2, "הכנת MTS")
                    );
                    mp4Input = tempMtsRemux;
                    extStartUs = 0L;
                    extEndUs = Long.MAX_VALUE;
                }

                extractor.setDataSource(mp4Input.getAbsolutePath());
                int trackCount = extractor.getTrackCount();
                muxer = new MediaMuxer(outFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

                Map<Integer, Integer> indexMap = new HashMap<>();
                int maxBufSize = 1024 * 1024;
                int srcRot = 0;

                for (int i = 0; i < trackCount; i++) {
                    MediaFormat fmt = extractor.getTrackFormat(i);
                    String mime = fmt.getString(MediaFormat.KEY_MIME);
                    if (mime == null) continue;
                    if (mime.startsWith("video/") || (!dropAudio && mime.startsWith("audio/"))) {
                        extractor.selectTrack(i);
                        if (mime.startsWith("video/") && fmt.containsKey(MediaFormat.KEY_ROTATION)) {
                            srcRot = fmt.getInteger(MediaFormat.KEY_ROTATION);
                        }
                        int dstIdx = muxer.addTrack(fmt);
                        indexMap.put(i, dstIdx);
                        if (fmt.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                            maxBufSize = Math.max(maxBufSize, fmt.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE));
                        }
                    }
                }
                muxer.setOrientationHint(((srcRot + rot) % 360 + 360) % 360);

                extractor.seekTo(extStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                ByteBuffer buf = ByteBuffer.allocate(maxBufSize);
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                muxer.start();

                long spanBaseUs = extEndUs == Long.MAX_VALUE
                        ? Math.max(500_000L, (long) (trimEndMs - trimStartMs) * 1000L)
                        : Math.max(1L, extEndUs - extStartUs);
                int lastPct = -1;
                while (true) {
                    info.offset = 0;
                    info.size = extractor.readSampleData(buf, 0);
                    if (info.size < 0) break;
                    long pts = extractor.getSampleTime();
                    if (pts > extEndUs) break;

                    int srcTrack = extractor.getSampleTrackIndex();
                    Integer dstTrack = indexMap.get(srcTrack);
                    if (dstTrack != null && pts >= extStartUs) {
                        info.presentationTimeUs = Math.max(0L, (long) ((pts - extStartUs) / speedVal));
                        int sampleFlags = extractor.getSampleFlags();
                        info.flags = (sampleFlags & MediaExtractor.SAMPLE_FLAG_SYNC) != 0
                                ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
                        muxer.writeSampleData(dstTrack, buf, info);
                        int pct = (int) Math.min(99L, ((pts - extStartUs) * 100L) / spanBaseUs);
                        if (isMtsOrTsSource) pct = 50 + (pct / 2);
                        if (pct != lastPct) {
                            lastPct = pct;
                            progress.updatePercent(outFile.getName(), pct, "ייצוא מהיר");
                        }
                    }
                    extractor.advance();
                }

                progress.updatePercent(outFile.getName(), 100, "מסיים...");
                muxer.stop();
                progress.finish();
                final File saved = outFile;
                runOnUiThread(() -> {
                    btnExport.setText("ייצא ושמור");
                    btnExport.setEnabled(true);
                    lastExportedFile = saved;
                    btnShareOutput.setVisibility(View.VISIBLE);
                    Toast.makeText(this, "הסרטון הערוך נשמר: " + saved.getName(), Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                progress.finish();
                if (outFile.exists()) outFile.delete();
                runOnUiThread(() -> {
                    btnExport.setText("ייצא ושמור");
                    btnExport.setEnabled(true);
                    Toast.makeText(this, "שגיאה בייצוא הסרטון: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            } finally {
                try { extractor.release(); } catch (Exception ignored) {}
                if (muxer != null) try { muxer.release(); } catch (Exception ignored) {}
                if (tempMtsRemux != null && tempMtsRemux.exists()) {
                    try { tempMtsRemux.delete(); } catch (Throwable ignored) {}
                }
            }
        });
    }

    private void releasePreview() {
        if (vlcPreviewPlayer != null) {
            try { vlcPreviewPlayer.release(); } catch (Exception ignored) {}
            vlcPreviewPlayer = null;
        }
        if (previewPlayer != null) {
            try { previewPlayer.stop(); } catch (Exception ignored) {}
            try { previewPlayer.release(); } catch (Exception ignored) {}
            previewPlayer = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (vlcPreviewPlayer != null) {
            try { vlcPreviewPlayer.pause(); } catch (Throwable ignored) {}
        }
        if (previewPlayer != null) {
            try { if (previewPlayer.isPlaying()) previewPlayer.pause(); } catch (Throwable ignored) {}
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releasePreview();
        executor.shutdownNow();
    }
}
