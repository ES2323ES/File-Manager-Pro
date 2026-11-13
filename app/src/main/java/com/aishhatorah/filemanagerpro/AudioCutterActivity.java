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
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AudioCutterActivity extends AppCompatActivity {

    public static final String EXTRA_INITIAL_PATH = "extra_initial_path";
    private static final int REQ_PICK_AUDIO = 2001;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Uri selectedAudioUri = null;
    private File lastCutOutputFile = null;
    private long durationMs = 0;
    private long startMs = 0;
    private long endMs = 0;
    private MediaPlayer mediaPlayer = null;

    private TextView tvSelectedFile;
    private TextView tvRangeInfo;
    private RangeWaveformTrimView rangeTrimView;
    private Button btnPlayPreview;
    private Button btnCutSave;
    private Button btnShareCut;

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
        root.setBackgroundColor(bg);

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(ThemeHelper.getTopBarColor(this, theme));
        topBar.setPadding(24, 22, 24, 22);

        Button btnBack = ThemedDialogHelper.createThemedButton(this, "חזרה", theme, false, false);
        btnBack.setOnClickListener(v -> finish());
        topBar.addView(btnBack);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("חותך אודיו");
        tvTitle.setTextSize(17.5f);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setTextColor(textPrimary);
        LinearLayout.LayoutParams lpTitle = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lpTitle.setMarginStart(16);
        topBar.addView(tvTitle, lpTitle);
        root.addView(topBar);

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(32, 32, 32, 32);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(32, 32, 32, 32);
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(24f);
        shape.setColor(cardBg);
        card.setBackground(shape);

        tvSelectedFile = new TextView(this);
        tvSelectedFile.setText("לא נבחר קובץ אודיו עדיין");
        tvSelectedFile.setTextColor(textPrimary);
        tvSelectedFile.setTextSize(15f);
        tvSelectedFile.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(tvSelectedFile);

        Button btnPick = createActionButton("בחירת קובץ שמע לחיתוך (M4A / AAC / MP4)", accent, v -> {
            Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            pick.addCategory(Intent.CATEGORY_OPENABLE);
            pick.setType("audio/*");
            startActivityForResult(pick, REQ_PICK_AUDIO);
        });
        card.addView(btnPick);

        tvRangeInfo = new TextView(this);
        tvRangeInfo.setText("התחלה: 00:00.0  •  סיום: 00:00.0");
        tvRangeInfo.setTextColor(textSecondary);
        tvRangeInfo.setTextSize(13.5f);
        tvRangeInfo.setPadding(0, 26, 0, 12);
        card.addView(tvRangeInfo);

        // סעיף 36: פס חיתוך משולב ונוח (התחלה וסיום על גל השמע)
        rangeTrimView = new RangeWaveformTrimView(this);
        rangeTrimView.setAccentColor(accent);
        rangeTrimView.setEnabled(false);
        rangeTrimView.setOnRangeChangedListener((s, e, movingStart) -> {
            startMs = s;
            endMs = e;
            updateRangeLabel();
        });
        card.addView(rangeTrimView);

        btnPlayPreview = createActionButton("נגן תצוגה מקדימה מהקטע שנבחר", Color.parseColor("#0D9488"), v -> togglePreview());
        btnPlayPreview.setEnabled(false);
        card.addView(btnPlayPreview);

        btnCutSave = createActionButton("חתוך ושמור קובץ אודיו חדש", Color.parseColor("#6D28D9"), v -> performLosslessAudioCut());
        btnCutSave.setEnabled(false);
        card.addView(btnCutSave);

        btnShareCut = createActionButton("שיתוף הקובץ שנחתך", Color.parseColor("#0284C7"), v -> {
            if (lastCutOutputFile != null && lastCutOutputFile.exists()) {
                shareAudioFile(lastCutOutputFile);
            } else {
                Toast.makeText(this, "חתוך ושמור קובץ אודיו תחילה כדי לשתף אותו", Toast.LENGTH_SHORT).show();
            }
        });
        btnShareCut.setEnabled(false);
        btnShareCut.setAlpha(0.55f);
        card.addView(btnShareCut);

        body.addView(card);
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        String initialPath = getIntent().getStringExtra(EXTRA_INITIAL_PATH);
        if (initialPath != null && !initialPath.isEmpty()) {
            File initialFile = new File(initialPath);
            if (initialFile.exists()) {
                selectedAudioUri = Uri.fromFile(initialFile);
                loadAudioMetadata(selectedAudioUri, initialFile.getName());
            }
        }
    }

    private Button createActionButton(String text, int bgColor, View.OnClickListener listener) {
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
        lp.topMargin = 18;
        b.setLayoutParams(lp);
        b.setPadding(22, 22, 22, 22);
        b.setOnClickListener(listener);
        return b;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_AUDIO && resultCode == RESULT_OK && data != null && data.getData() != null) {
            selectedAudioUri = data.getData();
            loadAudioMetadata(selectedAudioUri, selectedAudioUri.getLastPathSegment());
        }
    }

    private void loadAudioMetadata(Uri uri, String displayName) {
        try {
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            mmr.setDataSource(this, uri);
            String durStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            durationMs = durStr != null ? Long.parseLong(durStr) : 0;
            mmr.release();

            startMs = 0;
            endMs = Math.max(500L, durationMs);
            rangeTrimView.setEnabled(true);
            rangeTrimView.setDurationAndRange(durationMs, startMs, endMs);
            btnPlayPreview.setEnabled(true);
            btnCutSave.setEnabled(true);

            tvSelectedFile.setText("קובץ נבחר: " + (displayName != null ? displayName : uri.toString()));
            updateRangeLabel();
        } catch (Exception e) {
            Toast.makeText(this, "שגיאה בטעינת קובץ האודיו: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void updateRangeLabel() {
        long clipDur = Math.max(0, endMs - startMs);
        tvRangeInfo.setText(String.format(Locale.US,
                "התחלה: %s  •  סיום: %s  (אורך קטע: %s)",
                formatTime(startMs), formatTime(endMs), formatTime(clipDur)));
    }

    private String formatTime(long ms) {
        long totalSec = ms / 1000;
        long min = totalSec / 60;
        long sec = totalSec % 60;
        long tenths = (ms % 1000) / 100;
        return String.format(Locale.US, "%02d:%02d.%d", min, sec, tenths);
    }

    private void togglePreview() {
        try {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
                mediaPlayer.release();
                mediaPlayer = null;
                btnPlayPreview.setText("נגן תצוגה מקדימה מהקטע שנבחר");
                return;
            }
            if (selectedAudioUri == null) return;
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(this, selectedAudioUri);
            mediaPlayer.prepare();
            mediaPlayer.seekTo((int) startMs);
            mediaPlayer.start();
            btnPlayPreview.setText("עצור השמעה");
            mediaPlayer.setOnCompletionListener(mp -> btnPlayPreview.setText("נגן תצוגה מקדימה מהקטע שנבחר"));
        } catch (Exception e) {
            Toast.makeText(this, "לא ניתן להשמיע קובץ זה: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void performLosslessAudioCut() {
        if (selectedAudioUri == null || endMs <= startMs) return;
        btnCutSave.setEnabled(false);
        Toast.makeText(this, "חותך ושומר קובץ שמע...", Toast.LENGTH_SHORT).show();

        final long startUs = startMs * 1000L;
        final long endUs = endMs * 1000L;

        executor.execute(() -> {
            MediaExtractor extractor = new MediaExtractor();
            MediaMuxer muxer = null;
            try {
                extractor.setDataSource(this, selectedAudioUri, null);
                int audioTrackIndex = -1;
                MediaFormat selectedFormat = null;

                for (int i = 0; i < extractor.getTrackCount(); i++) {
                    MediaFormat format = extractor.getTrackFormat(i);
                    String mime = format.getString(MediaFormat.KEY_MIME);
                    if (mime != null && mime.startsWith("audio/")) {
                        audioTrackIndex = i;
                        selectedFormat = format;
                        break;
                    }
                }

                if (audioTrackIndex < 0 || selectedFormat == null) {
                    throw new IllegalStateException("לא נמצא ערוץ אודיו תקני נתמך ל-MediaMuxer");
                }

                File musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
                if (!musicDir.exists()) musicDir.mkdirs();
                File outFile = new File(musicDir, "AudioCut_" + System.currentTimeMillis() + ".m4a");

                extractor.selectTrack(audioTrackIndex);
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC);

                muxer = new MediaMuxer(outFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                int dstTrack = muxer.addTrack(selectedFormat);
                muxer.start();

                ByteBuffer buffer = ByteBuffer.allocate(256 * 1024);
                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

                while (true) {
                    int sampleSize = extractor.readSampleData(buffer, 0);
                    if (sampleSize < 0) break;
                    long presentationTimeUs = extractor.getSampleTime();
                    if (presentationTimeUs > endUs) break;

                    if (presentationTimeUs >= startUs) {
                        bufferInfo.offset = 0;
                        bufferInfo.size = sampleSize;
                        bufferInfo.presentationTimeUs = presentationTimeUs - startUs;
                        int sampleFlags = extractor.getSampleFlags();
                        bufferInfo.flags = (sampleFlags & MediaExtractor.SAMPLE_FLAG_SYNC) != 0
                                ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
                        muxer.writeSampleData(dstTrack, buffer, bufferInfo);
                    }
                    extractor.advance();
                }

                muxer.stop();
                final File finalOut = outFile;
                runOnUiThread(() -> {
                    lastCutOutputFile = finalOut;
                    btnCutSave.setEnabled(true);
                    btnShareCut.setEnabled(true);
                    btnShareCut.setAlpha(1.0f);
                    ThemedDialogHelper.showCustomDialog(
                            this,
                            "קובץ השמע נחתך בהצלחה",
                            "הקובץ החדש נשמר בתיקיית המוזיקה:\n" + finalOut.getName(),
                            null,
                            "שיתוף הקובץ שנחתך",
                            () -> shareAudioFile(finalOut),
                            "סגור",
                            null,
                            false
                    );
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    btnCutSave.setEnabled(true);
                    Toast.makeText(this, "שגיאה בחיתוך אודיו: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            } finally {
                try { extractor.release(); } catch (Exception ignored) {}
                if (muxer != null) try { muxer.release(); } catch (Exception ignored) {}
            }
        });
    }

    private void shareAudioFile(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("audio/*");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "שיתוף קובץ האודיו שנחתך"));
        } catch (Exception e) {
            Toast.makeText(this, "שגיאה בשיתוף הקובץ: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            try { mediaPlayer.release(); } catch (Exception ignored) {}
        }
    }
}
