package com.aishhatorah.filemanagerpro;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.util.SparseIntArray;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.SeekParameters;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.extractor.ExtractorsFactory;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VideoCutterActivity extends AppCompatActivity {

    public static final String EXTRA_INITIAL_PATH = "extra_initial_path";
    private static final int REQ_PICK_VIDEO = 3001;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Uri selectedVideoUri = null;
    private File resolvedSourceFile = null;
    private File tempCopiedCacheFile = null;
    private String resolvedDisplayName = "";
    private boolean isMtsOrTsSource = false;

    private File lastCutVideoFile = null;
    private long durationMs = 0;
    private long startMs = 0;
    private long endMs = 0;

    private TextureView previewTextureView;
    private Surface previewSurface;
    private ExoPlayer previewPlayer;
    private boolean previewAudioDisabled = false;
    private int previewVideoWidth = 0;
    private int previewVideoHeight = 0;
    private float previewPixelRatio = 1.0f;
    private long lastPreviewSeekMs = 0L;

    private TextView tvSelectedVideo;
    private TextView tvVideoRange;
    private RangeWaveformTrimView rangeTrimView;
    private Button btnCutVideo;
    private Button btnShareCutVideo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int theme = PrefsManager.getThemeMode(this);
        ThemeHelper.applyWindowStatusBar(this, theme);

        int bg = ThemeHelper.getBgColor(this, theme);
        int cardBg = ThemeHelper.getCardColor(this, theme);
        int textPrimary = ThemeHelper.getPrimaryTextColor(this, theme);
        int textSecondary = ThemeHelper.getSecondaryTextColor(this, theme);
        int accent = ThemeHelper.getAccentColor(this, theme);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        ThemeHelper.applyWallpaperBackdrop(this, root, theme);

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        ThemeHelper.applyCurvedHeaderStyle(this, topBar, theme);
        topBar.setPadding(ThemeHelper.dpScaled(this, 10), ThemeHelper.dpScaled(this, 8),
                ThemeHelper.dpScaled(this, 12), ThemeHelper.dpScaled(this, 10));

        ImageButton btnBack = ThemeHelper.createBorderlessSvgButton(
                this, R.drawable.ic_arrow_back, accent, "חזרה", 40, 8, v -> finish());
        topBar.addView(btnBack);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("חותך וידאו");
        tvTitle.setTextSize(ThemeHelper.spScaled(this, 17f));
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setTextColor(textPrimary);
        LinearLayout.LayoutParams lpTitle = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lpTitle.setMarginStart(ThemeHelper.dpScaled(this, 8));
        topBar.addView(tvTitle, lpTitle);
        root.addView(topBar);

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(28, 28, 28, 28);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(28, 28, 28, 28);
        card.setBackground(ThemeHelper.createGlassCardBackground(this, theme, 20f, false, false));

        tvSelectedVideo = new TextView(this);
        tvSelectedVideo.setText("בחר סרטון לחיתוך מהיר (MP4 / MTS / M2TS / TS)");
        tvSelectedVideo.setTextColor(textPrimary);
        tvSelectedVideo.setTextSize(14.5f);
        tvSelectedVideo.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(tvSelectedVideo);

        FrameLayout previewBox = new FrameLayout(this);
        previewBox.setBackgroundColor(Color.BLACK);
        previewTextureView = new TextureView(this);
        previewTextureView.setOpaque(true);
        previewBox.addView(previewTextureView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        previewTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
                previewSurface = new Surface(surface);
                if (previewPlayer != null) {
                    previewPlayer.setVideoSurface(previewSurface);
                } else if (selectedVideoUri != null) {
                    startPreviewExoPlayer(false);
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
                updatePreviewTransform();
            }

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
                if (previewPlayer != null) {
                    previewPlayer.setVideoSurface(null);
                }
                previewSurface = null;
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
            }
        });
        LinearLayout.LayoutParams vLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 460);
        vLp.topMargin = 16;
        card.addView(previewBox, vLp);

        Button btnPick = createButton("בחירת סרטון מקטגוריית הסרטונים", accent, v ->
                CategoryMediaPickerDialog.show(
                        this,
                        FileBrowserActivity.MODE_VIDEOS,
                        "בחירת סרטון מהמכשיר",
                        file -> {
                            selectedVideoUri = Uri.fromFile(file);
                            loadSelectedVideo(selectedVideoUri, file.getName(), file);
                        },
                        () -> {
                            Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                            pick.addCategory(Intent.CATEGORY_OPENABLE);
                            pick.setType("*/*");
                            pick.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                                    "video/*", "video/mp2ts", "video/mp2t", "application/octet-stream"
                            });
                            startActivityForResult(pick, REQ_PICK_VIDEO);
                        }
                ));
        card.addView(btnPick);

        tvVideoRange = new TextView(this);
        tvVideoRange.setText("התחלה: 00:00  •  סיום: 00:00");
        tvVideoRange.setTextColor(textSecondary);
        tvVideoRange.setPadding(0, 22, 0, 10);
        card.addView(tvVideoRange);

        rangeTrimView = new RangeWaveformTrimView(this);
        rangeTrimView.setAccentColor(accent);
        rangeTrimView.setEnabled(false);
        rangeTrimView.setOnRangeChangedListener((s, e, movingStart) -> {
            startMs = s;
            endMs = e;
            if (movingStart && previewPlayer != null) {
                long now = System.currentTimeMillis();
                if (now - lastPreviewSeekMs >= 180L) {
                    lastPreviewSeekMs = now;
                    try {
                        previewPlayer.seekTo(startMs);
                    } catch (Exception ignored) {
                    }
                }
            }
            tvVideoRange.setText(String.format(Locale.US, "התחלה: %02d:%02d  •  סיום: %02d:%02d",
                    (startMs / 1000) / 60, (startMs / 1000) % 60,
                    (endMs / 1000) / 60, (endMs / 1000) % 60));
        });
        card.addView(rangeTrimView);

        btnCutVideo = createButton("חתוך סרטון ושמור לתיקיית הסרטונים", Color.parseColor("#0284C7"), v -> executeVideoCut());
        btnCutVideo.setEnabled(false);
        card.addView(btnCutVideo);

        btnShareCutVideo = createButton("שיתוף הסרטון שנחתך", Color.parseColor("#0D9488"), v -> {
            if (lastCutVideoFile != null && lastCutVideoFile.exists()) {
                shareVideoFile(lastCutVideoFile);
            } else {
                Toast.makeText(this, "חתוך ושמור סרטון תחילה כדי לשתף אותו", Toast.LENGTH_SHORT).show();
            }
        });
        btnShareCutVideo.setEnabled(false);
        btnShareCutVideo.setAlpha(0.55f);
        card.addView(btnShareCutVideo);

        body.addView(card);
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        String initialPath = getIntent().getStringExtra(EXTRA_INITIAL_PATH);
        if (initialPath != null && !initialPath.isEmpty()) {
            File initialFile = new File(initialPath);
            if (initialFile.exists()) {
                selectedVideoUri = Uri.fromFile(initialFile);
                loadSelectedVideo(selectedVideoUri, initialFile.getName(), initialFile);
            }
        }
    }

    private Button createButton(String text, int bgColor, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(18f);
        shape.setColor(bgColor);
        b.setBackground(shape);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 16;
        b.setLayoutParams(lp);
        b.setPadding(22, 22, 22, 22);
        b.setOnClickListener(l);
        return b;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_VIDEO && resultCode == RESULT_OK && data != null && data.getData() != null) {
            selectedVideoUri = data.getData();
            String name = queryDisplayName(selectedVideoUri);
            File directFile = "file".equalsIgnoreCase(selectedVideoUri.getScheme()) && selectedVideoUri.getPath() != null
                    ? new File(selectedVideoUri.getPath()) : null;
            loadSelectedVideo(selectedVideoUri, name, directFile);
        }
    }

    private String queryDisplayName(Uri uri) {
        if (uri == null) return "";
        if ("file".equalsIgnoreCase(uri.getScheme()) && uri.getPath() != null) {
            return new File(uri.getPath()).getName();
        }
        try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String n = cursor.getString(idx);
                    if (n != null && !n.isEmpty()) return n;
                }
            }
        } catch (Throwable ignored) {
        }
        String last = uri.getLastPathSegment();
        return last != null ? last : "video";
    }

    private void loadSelectedVideo(Uri uri, String displayName, @Nullable File directFile) {
        releasePreviewPlayer();
        cleanupTempCacheCopy();
        resolvedDisplayName = displayName != null ? displayName : "";
        resolvedSourceFile = directFile;
        durationMs = 0L;
        startMs = 0L;
        endMs = 0L;
        previewAudioDisabled = false;
        isMtsOrTsSource = MpegTsNormalizer.isLikelyMpegTsName(resolvedDisplayName)
                || (directFile != null && MpegTsNormalizer.detectTsLayoutCached(directFile).packetStride > 0);

        tvSelectedVideo.setText("טוען סרטון: " + resolvedDisplayName);
        btnCutVideo.setEnabled(false);
        rangeTrimView.setEnabled(false);

        executor.execute(() -> {
            try {
                File usableFile = directFile;
                if (usableFile == null && "content".equalsIgnoreCase(uri.getScheme())) {
                    // אם מדובר ב-content:// של קובץ MTS/TS (או שהמערכת אינה חושפת נתיב ישיר), מעתיקים למטמון זמני לגישה ישירה
                    if (MpegTsNormalizer.isLikelyMpegTsName(resolvedDisplayName) || looksLikeMtsHeader(uri)) {
                        String ext = resolvedDisplayName.contains(".")
                                ? resolvedDisplayName.substring(resolvedDisplayName.lastIndexOf('.'))
                                : ".mts";
                        File cached = new File(getCacheDir(), "ts_cut_src_" + System.currentTimeMillis() + ext);
                        try (InputStream in = getContentResolver().openInputStream(uri);
                             FileOutputStream out = new FileOutputStream(cached)) {
                            if (in != null) {
                                byte[] buf = new byte[65536];
                                int r;
                                while ((r = in.read(buf)) > 0) {
                                    out.write(buf, 0, r);
                                }
                            }
                        }
                        if (cached.exists() && cached.length() > 0L) {
                            tempCopiedCacheFile = cached;
                            usableFile = cached;
                        }
                    }
                }

                resolvedSourceFile = usableFile;
                if (usableFile != null) {
                    isMtsOrTsSource = MpegTsNormalizer.isLikelyMpegTsName(resolvedDisplayName)
                            || MpegTsNormalizer.detectTsLayoutCached(usableFile).packetStride > 0;
                }

                long resolvedDur = 0L;
                if (usableFile != null) {
                    MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                    Closeable tsDs = null;
                    try {
                        tsDs = MpegTsNormalizer.setMediaMetadataRetrieverSource(mmr, usableFile);
                        String dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                        if (dur != null) resolvedDur = Long.parseLong(dur.trim());
                    } catch (Throwable ignored) {
                    } finally {
                        try { mmr.release(); } catch (Throwable ignored) {}
                        if (tsDs != null) try { tsDs.close(); } catch (Throwable ignored) {}
                    }
                    if (resolvedDur <= 0L && isMtsOrTsSource) {
                        resolvedDur = MpegTsNormalizer.extractTsDurationMs(usableFile);
                    }
                } else {
                    MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                    try {
                        mmr.setDataSource(this, uri);
                        String dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                        if (dur != null) resolvedDur = Long.parseLong(dur.trim());
                    } catch (Throwable ignored) {
                    } finally {
                        try { mmr.release(); } catch (Throwable ignored) {}
                    }
                }

                final long finalDur = resolvedDur;
                final File finalUsable = usableFile;
                runOnUiThread(() -> {
                    if (finalUsable != null) {
                        selectedVideoUri = Uri.fromFile(finalUsable);
                    }
                    applyLoadedDuration(finalDur);
                    startPreviewExoPlayer(false);
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "שגיאה בטעינת הסרטון: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private boolean looksLikeMtsHeader(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) return false;
            byte[] probe = new byte[16384];
            int total = 0;
            while (total < probe.length) {
                int r = in.read(probe, total, probe.length - total);
                if (r <= 0) break;
                total += r;
            }
            if (total < MpegTsNormalizer.TS_PACKET_SIZE * 3) return false;
            byte[] slice = total == probe.length ? probe : java.util.Arrays.copyOf(probe, total);
            return MpegTsNormalizer.detectTsLayoutFromBytes(slice, total).packetStride > 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void applyLoadedDuration(long durMs) {
        if (durMs > 0L) {
            durationMs = durMs;
            startMs = 0L;
            endMs = Math.max(500L, durationMs);
            rangeTrimView.setEnabled(true);
            rangeTrimView.setDurationAndRange(durationMs, startMs, endMs);
            btnCutVideo.setEnabled(true);
            tvVideoRange.setText(String.format(Locale.US, "התחלה: 00:00  •  סיום: %02d:%02d",
                    (endMs / 1000) / 60, (endMs / 1000) % 60));
        }
        tvSelectedVideo.setText("סרטון נבחר: " + resolvedDisplayName + (isMtsOrTsSource ? " (MTS/TS)" : ""));
    }

    @OptIn(markerClass = UnstableApi.class)
    private void startPreviewExoPlayer(boolean disableAudioTrack) {
        releasePreviewPlayer();
        if (selectedVideoUri == null) return;
        try {
            ExtractorsFactory extractorsFactory = SeekableTsExtractor.createExtractorsFactory(isMtsOrTsSource, () -> {
                if (durationMs > 0L) return durationMs;
                if (resolvedSourceFile != null) {
                    long tsDur = MpegTsNormalizer.extractTsDurationMs(resolvedSourceFile);
                    if (tsDur > 0L) return tsDur;
                }
                return 0L;
            });

            DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(
                    new NormalizedTsExoDataSource.Factory(this), extractorsFactory);

            DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(this)
                    .setEnableDecoderFallback(true);

            previewPlayer = new ExoPlayer.Builder(this, renderersFactory)
                    .setMediaSourceFactory(mediaSourceFactory)
                    .build();
            previewPlayer.setSeekParameters(SeekParameters.CLOSEST_SYNC);
            if (disableAudioTrack) {
                previewPlayer.setTrackSelectionParameters(
                        previewPlayer.getTrackSelectionParameters()
                                .buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                                .build());
            }
            if (previewSurface != null) {
                previewPlayer.setVideoSurface(previewSurface);
            }
            previewPlayer.addListener(new Player.Listener() {
                @Override
                public void onVideoSizeChanged(@NonNull VideoSize videoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        previewVideoWidth = videoSize.width;
                        previewVideoHeight = videoSize.height;
                        previewPixelRatio = videoSize.pixelWidthHeightRatio > 0f ? videoSize.pixelWidthHeightRatio : 1.0f;
                        updatePreviewTransform();
                    }
                }

                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    if (previewPlayer == null) return;
                    if (playbackState == Player.STATE_READY) {
                        long d = previewPlayer.getDuration();
                        if (d > 0L && d != C.TIME_UNSET && durationMs <= 0L) {
                            applyLoadedDuration(d);
                        }
                    }
                }

                @Override
                public void onPlayerError(@NonNull PlaybackException error) {
                    if (!previewAudioDisabled && isMtsOrTsSource) {
                        previewAudioDisabled = true;
                        startPreviewExoPlayer(true);
                    }
                }
            });

            MediaItem.Builder item = new MediaItem.Builder().setUri(selectedVideoUri);
            if (isMtsOrTsSource) {
                item.setMimeType(MimeTypes.VIDEO_MP2T);
            }
            previewPlayer.setMediaItem(item.build());
            previewPlayer.prepare();
            previewPlayer.play();
        } catch (Throwable ignored) {
        }
    }

    private void updatePreviewTransform() {
        if (previewTextureView == null) return;
        int vw = previewTextureView.getWidth();
        int vh = previewTextureView.getHeight();
        if (vw <= 0 || vh <= 0 || previewVideoWidth <= 0 || previewVideoHeight <= 0) return;
        float viewRatio = (float) vw / (float) vh;
        float videoRatio = (previewVideoWidth * previewPixelRatio) / (float) previewVideoHeight;
        float sx = videoRatio > viewRatio ? 1.0f : videoRatio / viewRatio;
        float sy = videoRatio > viewRatio ? viewRatio / videoRatio : 1.0f;
        Matrix m = new Matrix();
        m.setScale(sx, sy, vw * 0.5f, vh * 0.5f);
        previewTextureView.setTransform(m);
    }

    private void releasePreviewPlayer() {
        if (previewPlayer != null) {
            try {
                previewPlayer.release();
            } catch (Throwable ignored) {
            }
            previewPlayer = null;
        }
    }

    private void executeVideoCut() {
        if (selectedVideoUri == null || endMs <= startMs) return;
        btnCutVideo.setEnabled(false);
        Toast.makeText(this, isMtsOrTsSource ? "חותך וממיר קטע MTS ל-MP4..." : "חותך סרטון...", Toast.LENGTH_SHORT).show();

        final long startUs = startMs * 1000L;
        final long endUs = (durationMs > 0L && endMs >= durationMs - 250L) ? Long.MAX_VALUE : (endMs * 1000L);

        executor.execute(() -> {
            File moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
            if (!moviesDir.exists()) moviesDir.mkdirs();
            File outFile = new File(moviesDir, "VideoCut_" + System.currentTimeMillis() + ".mp4");

            if (isMtsOrTsSource && resolvedSourceFile != null && resolvedSourceFile.isFile()) {
                try {
                    TsToMp4Remuxer.RemuxResult res = TsToMp4Remuxer.remuxToMp4(
                            resolvedSourceFile,
                            outFile,
                            startUs,
                            endUs,
                            pct -> runOnUiThread(() -> btnCutVideo.setText("חותך סרטון MTS... " + pct + "%"))
                    );
                    runOnUiThread(() -> {
                        btnCutVideo.setText("חתוך סרטון ושמור לתיקיית הסרטונים");
                        lastCutVideoFile = outFile;
                        btnCutVideo.setEnabled(true);
                        btnShareCutVideo.setEnabled(true);
                        btnShareCutVideo.setAlpha(1.0f);
                        String extraNote = res.droppedUnsupportedAudio
                                ? "\n(הערה: ערוץ שמע AC-3 שלא נתמך במכשיר דולג כדי לשמור וידאו תקין)"
                                : "";
                        ThemedDialogHelper.showCustomDialog(
                                this,
                                "הסרטון נחתך ונשמר בהצלחה",
                                "הסרטון החתוך נשמר כ-MP4 בתיקיית הסרטונים:\n" + outFile.getName() + extraNote,
                                null,
                                "שיתוף הסרטון שנחתך",
                                () -> shareVideoFile(outFile),
                                "סגור",
                                null,
                                false
                        );
                    });
                } catch (Exception e) {
                    if (outFile.exists()) outFile.delete();
                    runOnUiThread(() -> {
                        btnCutVideo.setText("חתוך סרטון ושמור לתיקיית הסרטונים");
                        btnCutVideo.setEnabled(true);
                        Toast.makeText(this, "שגיאה בחיתוך MTS: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
                return;
            }

            MediaExtractor extractor = new MediaExtractor();
            MediaMuxer muxer = null;
            try {
                extractor.setDataSource(this, selectedVideoUri, null);
                int trackCount = extractor.getTrackCount();

                muxer = new MediaMuxer(outFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                SparseIntArray indexMap = new SparseIntArray(trackCount);
                int maxBufferSize = 1024 * 1024;

                for (int i = 0; i < trackCount; i++) {
                    MediaFormat format = extractor.getTrackFormat(i);
                    String mime = format.getString(MediaFormat.KEY_MIME);
                    if (mime != null && (mime.startsWith("video/") || mime.startsWith("audio/"))) {
                        try {
                            extractor.selectTrack(i);
                            int dstIndex = muxer.addTrack(format);
                            indexMap.put(i, dstIndex);
                            if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                                maxBufferSize = Math.max(maxBufferSize, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE));
                            }
                        } catch (Throwable ignored) {
                            extractor.unselectTrack(i);
                        }
                    }
                }

                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                muxer.start();

                ByteBuffer buffer = ByteBuffer.allocate(maxBufferSize);
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

                while (true) {
                    int sampleSize = extractor.readSampleData(buffer, 0);
                    if (sampleSize < 0) break;
                    long sampleTime = extractor.getSampleTime();
                    if (sampleTime > endUs) break;

                    int trackIndex = extractor.getSampleTrackIndex();
                    if (sampleTime >= startUs && indexMap.indexOfKey(trackIndex) >= 0) {
                        info.offset = 0;
                        info.size = sampleSize;
                        info.presentationTimeUs = sampleTime - startUs;
                        int flags = extractor.getSampleFlags();
                        info.flags = (flags & MediaExtractor.SAMPLE_FLAG_SYNC) != 0
                                ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
                        muxer.writeSampleData(indexMap.get(trackIndex), buffer, info);
                    }
                    extractor.advance();
                }

                muxer.stop();
                final File finalOut = outFile;
                runOnUiThread(() -> {
                    lastCutVideoFile = finalOut;
                    btnCutVideo.setEnabled(true);
                    btnShareCutVideo.setEnabled(true);
                    btnShareCutVideo.setAlpha(1.0f);
                    ThemedDialogHelper.showCustomDialog(
                            this,
                            "הסרטון נחתך ונשמר בהצלחה",
                            "הסרטון החתוך נשמר בתיקיית הסרטונים:\n" + finalOut.getName(),
                            null,
                            "שיתוף הסרטון שנחתך",
                            () -> shareVideoFile(finalOut),
                            "סגור",
                            null,
                            false
                    );
                });
            } catch (Exception e) {
                if (outFile.exists()) outFile.delete();
                runOnUiThread(() -> {
                    btnCutVideo.setEnabled(true);
                    Toast.makeText(this, "שגיאה בחיתוך וידאו: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            } finally {
                try { extractor.release(); } catch (Exception ignored) {}
                if (muxer != null) try { muxer.release(); } catch (Exception ignored) {}
            }
        });
    }

    private void shareVideoFile(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("video/mp4");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "שיתוף הסרטון שנחתך"));
        } catch (Exception e) {
            Toast.makeText(this, "שגיאה בשיתוף הסרטון: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void cleanupTempCacheCopy() {
        if (tempCopiedCacheFile != null && tempCopiedCacheFile.exists()) {
            try {
                tempCopiedCacheFile.delete();
            } catch (Throwable ignored) {
            }
            tempCopiedCacheFile = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (previewPlayer != null) {
            try { previewPlayer.pause(); } catch (Throwable ignored) {}
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releasePreviewPlayer();
        cleanupTempCacheCopy();
        executor.shutdownNow();
    }
}
