package com.aishhatorah.filemanagerpro;

import android.content.Intent;
import android.graphics.Color;
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
import android.util.SparseIntArray;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VideoCutterActivity extends AppCompatActivity {

    public static final String EXTRA_INITIAL_PATH = "extra_initial_path";
    private static final int REQ_PICK_VIDEO = 3001;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Uri selectedVideoUri = null;
    private File lastCutVideoFile = null;
    private long durationMs = 0;
    private long startMs = 0;
    private long endMs = 0;

    private VideoView videoView;
    private TextView tvSelectedVideo;
    private TextView tvVideoRange;
    private SeekBar seekStart;
    private SeekBar seekEnd;
    private Button btnCutVideo;
    private Button btnShareCutVideo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int theme = PrefsManager.getThemeMode(this);
        ThemeHelper.applyWindowStatusBar(this, theme);

        int bg = ThemeHelper.getBgColor(theme);
        int cardBg = ThemeHelper.getCardColor(theme);
        int textPrimary = ThemeHelper.getPrimaryTextColor(theme);
        int textSecondary = ThemeHelper.getSecondaryTextColor(theme);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(bg);

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(ThemeHelper.getTopBarColor(theme));
        topBar.setPadding(24, 22, 24, 22);

        Button btnBack = ThemedDialogHelper.createThemedButton(this, "חזרה", theme, false, false);
        btnBack.setOnClickListener(v -> finish());
        topBar.addView(btnBack);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("חותך וידאו מקצועי");
        tvTitle.setTextSize(18f);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setTextColor(textPrimary);
        LinearLayout.LayoutParams lpTitle = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lpTitle.setMarginStart(16);
        topBar.addView(tvTitle, lpTitle);
        root.addView(topBar);

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(32, 28, 32, 32);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(32, 32, 32, 32);
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(30f);
        shape.setColor(cardBg);
        card.setBackground(shape);

        tvSelectedVideo = new TextView(this);
        tvSelectedVideo.setText("בחר סרטון לחיתוך מהיר ללא קידוד מחדש");
        tvSelectedVideo.setTextColor(textPrimary);
        tvSelectedVideo.setTextSize(14.5f);
        tvSelectedVideo.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(tvSelectedVideo);

        videoView = new VideoView(this);
        LinearLayout.LayoutParams vLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 460);
        vLp.topMargin = 20;
        card.addView(videoView, vLp);

        Button btnPick = createButton("בחירת סרטון מהמכשיר (MP4)", Color.parseColor("#1D4ED8"), v -> {
            Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            pick.addCategory(Intent.CATEGORY_OPENABLE);
            pick.setType("video/*");
            startActivityForResult(pick, REQ_PICK_VIDEO);
        });
        card.addView(btnPick);

        tvVideoRange = new TextView(this);
        tvVideoRange.setText("התחלה: 00:00  •  סיום: 00:00");
        tvVideoRange.setTextColor(textSecondary);
        tvVideoRange.setPadding(0, 22, 0, 12);
        card.addView(tvVideoRange);

        seekStart = new SeekBar(this);
        seekStart.setMax(1000);
        seekStart.setEnabled(false);
        card.addView(seekStart);

        seekEnd = new SeekBar(this);
        seekEnd.setMax(1000);
        seekEnd.setProgress(1000);
        seekEnd.setEnabled(false);
        card.addView(seekEnd);

        SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                if (durationMs <= 0) return;
                startMs = (seekStart.getProgress() * durationMs) / 1000L;
                endMs = (seekEnd.getProgress() * durationMs) / 1000L;
                if (endMs <= startMs + 1000) endMs = Math.min(durationMs, startMs + 1000);
                if (fromUser && sb == seekStart) {
                    videoView.seekTo((int) startMs);
                }
                tvVideoRange.setText(String.format(Locale.US, "התחלה: %02d:%02d  •  סיום: %02d:%02d",
                        (startMs / 1000) / 60, (startMs / 1000) % 60,
                        (endMs / 1000) / 60, (endMs / 1000) % 60));
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        };
        seekStart.setOnSeekBarChangeListener(listener);
        seekEnd.setOnSeekBarChangeListener(listener);

        btnCutVideo = createButton("חתוך סרטון ושמור לתיקיית הסרטונים", Color.parseColor("#0284C7"), v -> executeVideoCut());
        btnCutVideo.setEnabled(false);
        card.addView(btnCutVideo);

        // סעיף כד: שיתוף הסרטון שנחתך ישירות מחותך הוידאו
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
                loadSelectedVideo(selectedVideoUri, initialFile.getName());
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
        shape.setCornerRadius(22f);
        shape.setColor(bgColor);
        b.setBackground(shape);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 18;
        b.setLayoutParams(lp);
        b.setPadding(24, 24, 24, 24);
        b.setOnClickListener(l);
        return b;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_VIDEO && resultCode == RESULT_OK && data != null && data.getData() != null) {
            selectedVideoUri = data.getData();
            loadSelectedVideo(selectedVideoUri, selectedVideoUri.getLastPathSegment());
        }
    }

    private void loadSelectedVideo(Uri uri, String displayName) {
        try {
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            mmr.setDataSource(this, uri);
            String dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            durationMs = dur != null ? Long.parseLong(dur) : 0;
            mmr.release();

            startMs = 0;
            endMs = durationMs;
            seekStart.setEnabled(true);
            seekEnd.setEnabled(true);
            seekStart.setProgress(0);
            seekEnd.setProgress(1000);
            btnCutVideo.setEnabled(true);

            tvSelectedVideo.setText("סרטון נבחר: " + (displayName != null ? displayName : uri.toString()));
            tvVideoRange.setText(String.format(Locale.US, "התחלה: 00:00  •  סיום: %02d:%02d",
                    (endMs / 1000) / 60, (endMs / 1000) % 60));
            videoView.setVideoURI(uri);
            videoView.start();
        } catch (Exception e) {
            Toast.makeText(this, "שגיאה בטעינת הסרטון: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void executeVideoCut() {
        if (selectedVideoUri == null || endMs <= startMs) return;
        btnCutVideo.setEnabled(false);
        Toast.makeText(this, "חותך סרטון...", Toast.LENGTH_SHORT).show();

        final long startUs = startMs * 1000L;
        final long endUs = endMs * 1000L;

        executor.execute(() -> {
            MediaExtractor extractor = new MediaExtractor();
            MediaMuxer muxer = null;
            try {
                extractor.setDataSource(this, selectedVideoUri, null);
                int trackCount = extractor.getTrackCount();

                File moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
                if (!moviesDir.exists()) moviesDir.mkdirs();
                File outFile = new File(moviesDir, "VideoCut_" + System.currentTimeMillis() + ".mp4");

                muxer = new MediaMuxer(outFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                SparseIntArray indexMap = new SparseIntArray(trackCount);
                int maxBufferSize = 1024 * 1024;

                for (int i = 0; i < trackCount; i++) {
                    MediaFormat format = extractor.getTrackFormat(i);
                    String mime = format.getString(MediaFormat.KEY_MIME);
                    if (mime != null && (mime.startsWith("video/") || mime.startsWith("audio/"))) {
                        extractor.selectTrack(i);
                        int dstIndex = muxer.addTrack(format);
                        indexMap.put(i, dstIndex);
                        if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                            maxBufferSize = Math.max(maxBufferSize, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE));
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
                            "הסרטון החתוך נשמר בתיקיית הסרטונים:\n" + finalOut.getName() +
                                    "\n\nתוכל לשתף את הסרטון שנחתך כעת:",
                            null,
                            "שיתוף הסרטון שנחתך",
                            () -> shareVideoFile(finalOut),
                            "סגור",
                            null,
                            false
                    );
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    btnCutVideo.setEnabled(true);
                    Toast.makeText(this, "שגיאה בחיתוך וידאו: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            } finally {
                try { extractor.release(); } catch (Exception ignored) {}
                if (muxer != null) {
                    try { muxer.release(); } catch (Exception ignored) {}
                }
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
}
