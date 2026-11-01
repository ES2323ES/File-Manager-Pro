package com.aishhatorah.filemanagerpro;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfRenderer;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.FileProvider;

import com.google.android.material.button.MaterialButton;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BuiltInViewerActivity extends AppCompatActivity {

    public static final String EXTRA_FILE_PATH = "extra_file_path";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private File targetFile;
    private TextView tvMarqueeTitle;
    private MaterialButton btnTopRightVideoSettings;

    // מצב אודיו (מנוהל ע"י AudioPlayerManager כדי להמשיך ברקע ביציאה מהשיר)
    private boolean isAudioMode = false;
    private boolean audioTickerActive = false;
    private AudioPlayerManager.PlaybackListener audioListener;

    // מצב וידאו מובנה מתקדם
    private final List<File> videoPlaylist = new ArrayList<>();
    private int currentVideoIdx = 0;
    private MediaPlayer videoPlayer;
    private Surface activeVideoSurface;
    private TextureView videoTextureView;
    private boolean videoPrepared = false;
    private boolean videoTickerActive = false;
    private float videoSpeed = 1.0f;
    private int videoRepeatMode = AudioPlayerManager.REPEAT_OFF;
    private boolean videoMuted = false;
    private boolean videoAutoNext = true;
    private float videoScale = 1.0f;
    private float videoTransX = 0f;
    private float videoTransY = 0f;
    private boolean isLandscapeLocked = false;

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

        String lower = targetFile.getName().toLowerCase(Locale.ROOT);
        boolean isVideoFile = endsWithAny(lower, ".mp4", ".mkv", ".mov", ".avi", ".webm", ".3gp");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(bgColor);

        // סרגל עליון (RTL: צד ימין = סגור + מתחתיו הגדרות הוידאו בסרטון, מרכז = כותרת נעה תדיר, צד שמאל = שיתוף)
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(ThemeHelper.getTopBarColor(theme));
        topBar.setPadding(22, 16, 22, 16);

        // עמודה ימנית עליונה: לחצן סגור, ומתחתיו (בוידאו - סעיף יז) הגדרות הוידאו עם סמל גלגל שיניים
        LinearLayout rightActionsCol = new LinearLayout(this);
        rightActionsCol.setOrientation(LinearLayout.VERTICAL);
        rightActionsCol.setGravity(Gravity.CENTER_HORIZONTAL);

        Button btnClose = ThemedDialogHelper.createThemedButton(this, "סגור", theme, false, false);
        btnClose.setPadding(26, 14, 26, 14);
        btnClose.setOnClickListener(v -> finish());
        rightActionsCol.addView(btnClose);

        btnTopRightVideoSettings = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnTopRightVideoSettings.setText("הגדרות הוידאו");
        btnTopRightVideoSettings.setAllCaps(false);
        btnTopRightVideoSettings.setTextSize(11f);
        btnTopRightVideoSettings.setTypeface(Typeface.DEFAULT_BOLD);
        btnTopRightVideoSettings.setTextColor(accent);
        btnTopRightVideoSettings.setStrokeColor(ColorStateList.valueOf(accent));
        btnTopRightVideoSettings.setIconResource(R.drawable.ic_settings);
        btnTopRightVideoSettings.setIconTint(ColorStateList.valueOf(accent));
        btnTopRightVideoSettings.setIconSize(36);
        btnTopRightVideoSettings.setIconPadding(10);
        btnTopRightVideoSettings.setCornerRadius(20);
        btnTopRightVideoSettings.setInsetTop(0);
        btnTopRightVideoSettings.setInsetBottom(0);
        btnTopRightVideoSettings.setMinHeight(0);
        btnTopRightVideoSettings.setMinimumHeight(0);
        btnTopRightVideoSettings.setPadding(18, 10, 18, 10);
        LinearLayout.LayoutParams vsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vsLp.topMargin = 10;
        btnTopRightVideoSettings.setLayoutParams(vsLp);
        btnTopRightVideoSettings.setVisibility(isVideoFile ? View.VISIBLE : View.GONE);
        btnTopRightVideoSettings.setOnClickListener(v -> showVideoSettingsDialog(theme));
        rightActionsCol.addView(btnTopRightVideoSettings);

        topBar.addView(rightActionsCol);

        // סעיף יא: למעלה באמצע שם השיר / התמונה / הוידאו נע תדיר ללא הפסקה
        tvMarqueeTitle = new AppCompatTextView(this) {
            @Override
            public boolean isFocused() {
                return true;
            }
        };
        tvMarqueeTitle.setText("   " + targetFile.getName() + "   •   " + targetFile.getName() + "   ");
        tvMarqueeTitle.setTextSize(15.5f);
        tvMarqueeTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvMarqueeTitle.setTextColor(textPrimary);
        tvMarqueeTitle.setSingleLine(true);
        tvMarqueeTitle.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        tvMarqueeTitle.setMarqueeRepeatLimit(-1);
        tvMarqueeTitle.setHorizontallyScrolling(true);
        tvMarqueeTitle.setSelected(true);
        tvMarqueeTitle.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleLp.setMarginStart(18);
        titleLp.setMarginEnd(18);
        topBar.addView(tvMarqueeTitle, titleLp);

        Button btnShare = ThemedDialogHelper.createThemedButton(this, "שיתוף", theme, true, false);
        btnShare.setPadding(26, 14, 26, 14);
        btnShare.setOnClickListener(v -> shareCurrentFile());
        topBar.addView(btnShare);

        root.addView(topBar);

        LinearLayout contentArea = new LinearLayout(this);
        contentArea.setOrientation(LinearLayout.VERTICAL);
        contentArea.setGravity(Gravity.CENTER);
        contentArea.setPadding(22, 18, 22, 22);
        root.addView(contentArea, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);

        if (endsWithAny(lower, ".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp", ".heic")) {
            renderImageViewer(contentArea, cardColor, textSecondary, accent, theme);
        } else if (endsWithAny(lower, ".mp3", ".m4a", ".wav", ".aac", ".flac", ".ogg")) {
            isAudioMode = true;
            renderAudioPlayer(contentArea, cardColor, textPrimary, textSecondary, accent, theme);
        } else if (isVideoFile) {
            renderVideoPlayer(contentArea, cardColor, textPrimary, textSecondary, accent, theme);
        } else if (lower.endsWith(".pdf")) {
            renderPdfViewer(contentArea, cardColor, textSecondary, theme);
        } else if (endsWithAny(lower, ".zip", ".jar")) {
            renderArchiveExtractor(contentArea, cardColor, textPrimary, textSecondary, theme);
        } else if (endsWithAny(lower, ".docx", ".xlsx", ".pptx")) {
            renderOfficeDocumentViewer(contentArea, cardColor, textPrimary, textSecondary);
        } else {
            renderTextDocumentViewer(contentArea, cardColor, textPrimary, textSecondary);
        }
    }

    private void updateMarqueeHeaderName(String name) {
        if (tvMarqueeTitle != null && name != null) {
            tvMarqueeTitle.setText("   " + name + "   •   " + name + "   ");
            tvMarqueeTitle.setSelected(true);
        }
    }

    /**
     * סעיפים יד + טו: מציג תמונות עם הגדלה ביד (ללא כפתור הגדלה/התאמה למסך)
     * ולמטה לחצן "עורך תמונות מקצועי" עם סמל עיפרון.
     */
    private void renderImageViewer(LinearLayout host, int cardColor, int textSecondary, int accent, int theme) {
        LinearLayout card = createSurfaceCard(cardColor);
        card.setGravity(Gravity.CENTER);

        ZoomableImageView zoomImageView = new ZoomableImageView(this);

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
            zoomImageView.setImageBitmap(bmp);
        }

        LinearLayout.LayoutParams imgLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        card.addView(zoomImageView, imgLp);

        TextView tvMeta = new TextView(this);
        tvMeta.setText(String.format(Locale.US, "הגדלה והזזה ביד בשתי אצבעות  •  %dx%d  •  %s",
                Math.max(0, opts.outWidth), Math.max(0, opts.outHeight), StorageUtils.formatSize(targetFile.length())));
        tvMeta.setTextColor(textSecondary);
        tvMeta.setTextSize(12f);
        tvMeta.setGravity(Gravity.CENTER);
        tvMeta.setPadding(0, 14, 0, 14);
        card.addView(tvMeta);

        MaterialButton btnProImageEditor = createPencilEditorButton("עורך תמונות מקצועי", accent, theme, v -> {
            Intent i = new Intent(this, ProImageEditorActivity.class);
            i.putExtra(ProImageEditorActivity.EXTRA_IMAGE_PATH, targetFile.getAbsolutePath());
            startActivity(i);
        });
        card.addView(btnProImageEditor, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        host.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /**
     * סעיפים ח, ט, י, יב, יג, יט, כ, כא: נגן אודיו מתקדם עם:
     * - אייקון מוטמע של השיר במידה וקיים (ללא הפניה לחותך האודיו)
     * - לחיצה כפולה בימין (+10) ובשמאל (-10) עם חיווי מסודר
     * - תצוגת זמן בצד שמאל: הכי שמאלי הזמן שעבר ולידו הזמן הכולל של השיר
     * - סמלי ניגון למטה: השהה/נגן במרכז, ימינה ממנו שיר הבא, שמאלה ממנו שיר קודם, ו-10 שניות קדימה/אחורה
     * - באמצע למטה: בחירת מהירות השיר + סמל מצב חזרה (ללא / שיר נוכחי / כל התיקייה)
     */
    private void renderAudioPlayer(LinearLayout host, int cardColor, int textPrimary, int textSecondary, int accent, int theme) {
        AudioPlayerManager mgr = AudioPlayerManager.getInstance();
        mgr.playOrAttachFile(this, targetFile);

        LinearLayout card = createSurfaceCard(cardColor);
        card.setOrientation(LinearLayout.VERTICAL);

        // אזור מרכזי עליון ללחיצה כפולה (+10 בימין / -10 בשמאל) + עטיפת השיר
        FrameLayout stageFrame = new FrameLayout(this);

        LinearLayout artCenter = new LinearLayout(this);
        artCenter.setOrientation(LinearLayout.VERTICAL);
        artCenter.setGravity(Gravity.CENTER);

        final ImageView ivArt = new ImageView(this);
        ivArt.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams artLp = new LinearLayout.LayoutParams(340, 340);
        artLp.bottomMargin = 24;
        artCenter.addView(ivArt, artLp);

        final TextView tvTrackSubtitle = new TextView(this);
        tvTrackSubtitle.setTextColor(textSecondary);
        tvTrackSubtitle.setTextSize(12.5f);
        tvTrackSubtitle.setGravity(Gravity.CENTER);
        tvTrackSubtitle.setText("לחיצה כפולה בימין +10 ש׳ • בשמאל -10 ש׳");
        artCenter.addView(tvTrackSubtitle);

        stageFrame.addView(artCenter, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));

        final TextView tvDoubleTapBadge = createSeekOverlayBadge();
        stageFrame.addView(tvDoubleTapBadge);

        attachDoubleTapSeekGesture(stageFrame, tvDoubleTapBadge, deltaSec -> mgr.seekBy(deltaSec * 1000));

        card.addView(stageFrame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // סעיף כ: הזמן של השיר בצד שמאל — בצד הכי שמאלי מופיע כמה עבר מהשיר ולידו מופיע הזמן הכולל של השיר
        LinearLayout timeRowLtr = new LinearLayout(this);
        timeRowLtr.setOrientation(LinearLayout.HORIZONTAL);
        timeRowLtr.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        timeRowLtr.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        timeRowLtr.setPadding(6, 12, 6, 8);

        final TextView tvElapsedLeftmost = new TextView(this);
        tvElapsedLeftmost.setText("00:00");
        tvElapsedLeftmost.setTextColor(accent);
        tvElapsedLeftmost.setTextSize(14.5f);
        tvElapsedLeftmost.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        timeRowLtr.addView(tvElapsedLeftmost);

        final TextView tvTotalNextToIt = new TextView(this);
        tvTotalNextToIt.setText("  /  00:00");
        tvTotalNextToIt.setTextColor(textSecondary);
        tvTotalNextToIt.setTextSize(14f);
        tvTotalNextToIt.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        timeRowLtr.addView(tvTotalNextToIt);

        card.addView(timeRowLtr, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final SeekBar seekBar = new SeekBar(this);
        card.addView(seekBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // סעיפים ט + יט: סמלי ניגון למטה ב-LTR מפורש:
        // משמאל לימין: [10- אחורה] [שיר קודם (משמאל להשהה)] [השהה/נגן] [שיר הבא (מימין להשהה)] [+10 קדימה]
        LinearLayout transportRow = new LinearLayout(this);
        transportRow.setOrientation(LinearLayout.HORIZONTAL);
        transportRow.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        transportRow.setGravity(Gravity.CENTER);
        transportRow.setPadding(0, 22, 0, 14);

        ImageButton btnBack10 = createRoundIconButton(R.drawable.ic_replay_10, 112, false, accent, theme);
        ImageButton btnPrevSong = createRoundIconButton(R.drawable.ic_skip_prev, 116, false, accent, theme);
        final ImageButton btnPlayPause = createRoundIconButton(R.drawable.ic_pause, 142, true, accent, theme);
        ImageButton btnNextSong = createRoundIconButton(R.drawable.ic_skip_next, 116, false, accent, theme);
        ImageButton btnFwd10 = createRoundIconButton(R.drawable.ic_forward_10, 112, false, accent, theme);

        transportRow.addView(btnBack10);
        transportRow.addView(btnPrevSong);
        transportRow.addView(btnPlayPause);
        transportRow.addView(btnNextSong);
        transportRow.addView(btnFwd10);
        card.addView(transportRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // סעיפים ט + י: שורה תחתונה שבה באמצע למטה בדיוק אופציית בחירת מהירות השיר, ובצד סמל מצב החזרה
        FrameLayout bottomCenterBar = new FrameLayout(this);
        bottomCenterBar.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        bottomCenterBar.setPadding(4, 8, 4, 4);

        final ImageButton btnRepeatMode = createRoundIconButton(R.drawable.ic_repeat_off, 104, false, accent, theme);
        FrameLayout.LayoutParams repLp = new FrameLayout.LayoutParams(104, 104, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        bottomCenterBar.addView(btnRepeatMode, repLp);

        final MaterialButton btnSpeedCenter = createSpeedPillButton(mgr.getPlaybackSpeed(), accent, theme);
        FrameLayout.LayoutParams spdLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        bottomCenterBar.addView(btnSpeedCenter, spdLp);

        card.addView(bottomCenterBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        host.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // חיבור פעולות הכפתורים
        btnBack10.setOnClickListener(v -> mgr.seekBy(-10_000));
        btnFwd10.setOnClickListener(v -> mgr.seekBy(10_000));
        btnPrevSong.setOnClickListener(v -> mgr.playPrevious(this));
        btnNextSong.setOnClickListener(v -> mgr.playNext(this));
        btnPlayPause.setOnClickListener(v -> mgr.togglePlayPause(this));

        btnSpeedCenter.setOnClickListener(v -> showSpeedPickerDialog("בחירת מהירות השיר", mgr.getPlaybackSpeed(), chosen -> {
            mgr.setPlaybackSpeed(this, chosen);
            btnSpeedCenter.setText(String.format(Locale.US, "מהירות %.2gx", chosen).replace(".00", ".0"));
        }));

        btnRepeatMode.setOnClickListener(v -> showRepeatPickerDialog(
                "מצב חזרה באודיו",
                mgr.getRepeatMode(),
                true,
                chosenMode -> mgr.setRepeatMode(chosenMode)
        ));

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) mgr.seekTo(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        final Runnable syncAudioUi = () -> {
            File active = mgr.getCurrentFile();
            if (active != null) {
                targetFile = active;
                updateMarqueeHeaderName(active.getName());
            }
            Bitmap art = mgr.getCurrentArtwork();
            if (art != null && !art.isRecycled()) {
                ivArt.setImageBitmap(art);
            } else {
                ivArt.setImageResource(R.drawable.ic_audio);
            }
            btnPlayPause.setImageResource(mgr.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);
            btnSpeedCenter.setText(formatSpeedLabel(mgr.getPlaybackSpeed()));
            btnRepeatMode.setImageResource(resolveRepeatIcon(mgr.getRepeatMode()));
        };

        audioListener = syncAudioUi::run;
        mgr.addListener(audioListener);
        syncAudioUi.run();

        audioTickerActive = true;
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!audioTickerActive) return;
                int dur = mgr.getDurationMs();
                int pos = mgr.getCurrentPositionMs();
                seekBar.setMax(Math.max(1, dur));
                seekBar.setProgress(pos);
                tvElapsedLeftmost.setText(formatClock(pos));
                tvTotalNextToIt.setText("  /  " + formatClock(dur));
                uiHandler.postDelayed(this, 250);
            }
        });
    }

    /**
     * סעיפים טו, טז, יז, יח, כא: נגן וידאו מתקדם עם:
     * - הגדלה והקטנה ביד (Pinch-to-Zoom) + הזזה ביד
     * - סיבוב מסך אופקי/אנכי
     * - לחיצה כפולה בימין (+10) ובשמאל (-10) עם חיווי מסודר
     * - הגדרות הוידאו למעלה מימין מתחת ל"סגור"
     * - סמלי 10 שניות קדימה ואחורה למטה ביחד עם מהירות הסרטון, מצב חזרה והעברה לסרטון הבא/הקודם בתיקייה
     * - למטה לחצן "עורך וידאו מקצועי" עם סמל עיפרון (ללא הפניה לחותך הוידאו וללא "נגן / השהה" הישן)
     */
    private void renderVideoPlayer(LinearLayout host, int cardColor, int textPrimary, int textSecondary, int accent, int theme) {
        buildVideoFolderPlaylist(targetFile);

        LinearLayout card = createSurfaceCard(cardColor);
        card.setOrientation(LinearLayout.VERTICAL);

        final FrameLayout videoViewport = new FrameLayout(this);
        videoViewport.setBackgroundColor(Color.BLACK);
        videoViewport.setClipChildren(true);

        videoTextureView = new TextureView(this);
        videoViewport.addView(videoTextureView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));

        final TextView tvVideoDoubleTapBadge = createSeekOverlayBadge();
        videoViewport.addView(tvVideoDoubleTapBadge);

        setupVideoPinchZoomAndDoubleTap(videoViewport, tvVideoDoubleTapBadge);

        card.addView(videoViewport, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // שורת זמן + פס התקדמות
        LinearLayout timeBar = new LinearLayout(this);
        timeBar.setOrientation(LinearLayout.HORIZONTAL);
        timeBar.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        timeBar.setGravity(Gravity.CENTER_VERTICAL);
        timeBar.setPadding(4, 12, 4, 4);

        final TextView tvVidPos = new TextView(this);
        tvVidPos.setText("00:00");
        tvVidPos.setTextColor(accent);
        tvVidPos.setTextSize(12.5f);
        tvVidPos.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        timeBar.addView(tvVidPos);

        final SeekBar videoSeek = new SeekBar(this);
        timeBar.addView(videoSeek, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final TextView tvVidDur = new TextView(this);
        tvVidDur.setText("00:00");
        tvVidDur.setTextColor(textSecondary);
        tvVidDur.setTextSize(12.5f);
        tvVidDur.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        timeBar.addView(tvVidDur);

        card.addView(timeBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // סעיפים טו, טז, יח: סמלי ניגון, 10 שניות קדימה/אחורה, העברה לסרטון הבא/קודם, חזרה בצד, מהירות הסרטון וסיבוב מסך
        LinearLayout controlsRow = new LinearLayout(this);
        controlsRow.setOrientation(LinearLayout.HORIZONTAL);
        controlsRow.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        controlsRow.setGravity(Gravity.CENTER);
        controlsRow.setPadding(0, 10, 0, 10);

        final ImageButton btnVidRepeat = createRoundIconButton(R.drawable.ic_repeat_off, 96, false, accent, theme);
        ImageButton btnVidPrev = createRoundIconButton(R.drawable.ic_skip_prev, 98, false, accent, theme);
        ImageButton btnVidBack10 = createRoundIconButton(R.drawable.ic_replay_10, 104, false, accent, theme);
        final ImageButton btnVidPlayPause = createRoundIconButton(R.drawable.ic_pause, 124, true, accent, theme);
        ImageButton btnVidFwd10 = createRoundIconButton(R.drawable.ic_forward_10, 104, false, accent, theme);
        ImageButton btnVidNext = createRoundIconButton(R.drawable.ic_skip_next, 98, false, accent, theme);
        ImageButton btnVidRotate = createRoundIconButton(R.drawable.ic_screen_rotate, 96, false, accent, theme);

        controlsRow.addView(btnVidRepeat);
        controlsRow.addView(btnVidPrev);
        controlsRow.addView(btnVidBack10);
        controlsRow.addView(btnVidPlayPause);
        controlsRow.addView(btnVidFwd10);
        controlsRow.addView(btnVidNext);
        controlsRow.addView(btnVidRotate);
        card.addView(controlsRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // שורת מהירות הסרטון + עורך וידאו מקצועי עם סמל עיפרון למטה
        LinearLayout bottomRow = new LinearLayout(this);
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        bottomRow.setGravity(Gravity.CENTER_VERTICAL);
        bottomRow.setPadding(0, 6, 0, 0);

        MaterialButton btnProVideoEditor = createPencilEditorButton("עורך וידאו מקצועי", accent, theme, v -> {
            pauseVideoSafely();
            Intent i = new Intent(this, ProVideoEditorActivity.class);
            i.putExtra(ProVideoEditorActivity.EXTRA_VIDEO_PATH, targetFile.getAbsolutePath());
            startActivity(i);
        });
        LinearLayout.LayoutParams edLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        edLp.setMarginEnd(12);
        btnProVideoEditor.setLayoutParams(edLp);
        bottomRow.addView(btnProVideoEditor);

        final MaterialButton btnVidSpeed = createSpeedPillButton(videoSpeed, accent, theme);
        bottomRow.addView(btnVidSpeed);

        card.addView(bottomRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        host.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // לוגיקת נגן הוידאו
        final Runnable syncVidButtons = () -> {
            boolean playing = isVideoPlayingSafely();
            btnVidPlayPause.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
            btnVidRepeat.setImageResource(resolveRepeatIcon(videoRepeatMode));
            btnVidSpeed.setText(formatSpeedLabel(videoSpeed));
        };

        btnVidPlayPause.setOnClickListener(v -> {
            if (!videoPrepared || videoPlayer == null) return;
            if (isVideoPlayingSafely()) {
                videoPlayer.pause();
            } else {
                applyVideoSpeedInternal(true);
            }
            syncVidButtons.run();
        });

        btnVidBack10.setOnClickListener(v -> seekVideoBy(-10_000));
        btnVidFwd10.setOnClickListener(v -> seekVideoBy(10_000));

        btnVidPrev.setOnClickListener(v -> {
            if (videoPlaylist.isEmpty()) return;
            currentVideoIdx = (currentVideoIdx - 1 + videoPlaylist.size()) % videoPlaylist.size();
            loadVideoTrackAtIndex(currentVideoIdx, syncVidButtons);
        });

        btnVidNext.setOnClickListener(v -> {
            if (videoPlaylist.isEmpty()) return;
            currentVideoIdx = (currentVideoIdx + 1) % videoPlaylist.size();
            loadVideoTrackAtIndex(currentVideoIdx, syncVidButtons);
        });

        btnVidRepeat.setOnClickListener(v -> showRepeatPickerDialog(
                "מצב חזרה בוידאו",
                videoRepeatMode,
                false,
                chosen -> {
                    videoRepeatMode = chosen;
                    syncVidButtons.run();
                }
        ));

        btnVidRotate.setOnClickListener(v -> toggleScreenOrientation());

        btnVidSpeed.setOnClickListener(v -> showSpeedPickerDialog("בחירת מהירות הסרטון", videoSpeed, chosen -> {
            videoSpeed = chosen;
            applyVideoSpeedInternal(isVideoPlayingSafely());
            syncVidButtons.run();
        }));

        videoSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser && videoPrepared && videoPlayer != null) {
                    try { videoPlayer.seekTo(progress); } catch (Exception ignored) {}
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        videoTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
                activeVideoSurface = new Surface(surface);
                loadVideoTrackAtIndex(currentVideoIdx, syncVidButtons);
            }
            @Override public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {}
            @Override public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
                releaseVideoPlayer();
                activeVideoSurface = null;
                return true;
            }
            @Override public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {}
        });

        videoTickerActive = true;
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!videoTickerActive) return;
                if (videoPrepared && videoPlayer != null) {
                    try {
                        int dur = Math.max(1, videoPlayer.getDuration());
                        int pos = Math.max(0, videoPlayer.getCurrentPosition());
                        videoSeek.setMax(dur);
                        videoSeek.setProgress(pos);
                        tvVidPos.setText(formatClock(pos));
                        tvVidDur.setText(formatClock(dur));
                        syncVidButtons.run();
                    } catch (Exception ignored) {
                    }
                }
                uiHandler.postDelayed(this, 250);
            }
        });
    }

    private void buildVideoFolderPlaylist(File initialVideo) {
        videoPlaylist.clear();
        currentVideoIdx = 0;
        File parent = initialVideo.getParentFile();
        if (parent != null && parent.exists() && parent.isDirectory()) {
            File[] siblings = parent.listFiles();
            if (siblings != null) {
                for (File f : siblings) {
                    if (f.isFile() && endsWithAny(f.getName().toLowerCase(Locale.ROOT),
                            ".mp4", ".mkv", ".mov", ".avi", ".webm", ".3gp")) {
                        videoPlaylist.add(f);
                    }
                }
            }
        }
        if (videoPlaylist.isEmpty()) {
            videoPlaylist.add(initialVideo);
        } else {
            Collections.sort(videoPlaylist, (a, b) -> StorageUtils.compareHebrewFirst(a.getName(), b.getName()));
            for (int i = 0; i < videoPlaylist.size(); i++) {
                if (videoPlaylist.get(i).getAbsolutePath().equals(initialVideo.getAbsolutePath())) {
                    currentVideoIdx = i;
                    break;
                }
            }
        }
    }

    private void loadVideoTrackAtIndex(int idx, Runnable onReadyUi) {
        if (activeVideoSurface == null || videoPlaylist.isEmpty()) return;
        releaseVideoPlayer();
        File nextFile = videoPlaylist.get(Math.max(0, Math.min(videoPlaylist.size() - 1, idx)));
        targetFile = nextFile;
        updateMarqueeHeaderName(nextFile.getName());
        resetVideoZoom();

        try {
            videoPlayer = new MediaPlayer();
            videoPlayer.setDataSource(nextFile.getAbsolutePath());
            videoPlayer.setSurface(activeVideoSurface);
            float vol = videoMuted ? 0f : 1f;
            videoPlayer.setVolume(vol, vol);
            videoPlayer.setOnPreparedListener(mp -> {
                videoPrepared = true;
                applyVideoSpeedInternal(true);
                if (onReadyUi != null) onReadyUi.run();
            });
            videoPlayer.setOnCompletionListener(mp -> {
                if (videoRepeatMode == AudioPlayerManager.REPEAT_ONE) {
                    mp.seekTo(0);
                    applyVideoSpeedInternal(true);
                } else if (videoRepeatMode == AudioPlayerManager.REPEAT_FOLDER && !videoPlaylist.isEmpty()) {
                    currentVideoIdx = (currentVideoIdx + 1) % videoPlaylist.size();
                    loadVideoTrackAtIndex(currentVideoIdx, onReadyUi);
                } else if (videoAutoNext && currentVideoIdx + 1 < videoPlaylist.size()) {
                    currentVideoIdx++;
                    loadVideoTrackAtIndex(currentVideoIdx, onReadyUi);
                } else if (onReadyUi != null) {
                    onReadyUi.run();
                }
            });
            videoPlayer.prepareAsync();
        } catch (Exception e) {
            Toast.makeText(this, "שגיאה בטעינת הסרטון: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void setupVideoPinchZoomAndDoubleTap(FrameLayout viewport, TextView badge) {
        final PointF lastPan = new PointF();
        final ScaleGestureDetector scaleDetector = new ScaleGestureDetector(this,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        videoScale = Math.max(1.0f, Math.min(4.0f, videoScale * detector.getScaleFactor()));
                        if (videoTextureView != null) {
                            videoTextureView.setScaleX(videoScale);
                            videoTextureView.setScaleY(videoScale);
                            if (videoScale <= 1.02f) {
                                videoTransX = 0f;
                                videoTransY = 0f;
                                videoTextureView.setTranslationX(0f);
                                videoTextureView.setTranslationY(0f);
                            }
                        }
                        return true;
                    }
                });

        final GestureDetector tapDetector = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(MotionEvent e) {
                        return true;
                    }

                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        boolean rightHalf = e.getX() >= (viewport.getWidth() / 2f);
                        if (rightHalf) {
                            seekVideoBy(10_000);
                            flashSeekBadge(badge, true);
                        } else {
                            seekVideoBy(-10_000);
                            flashSeekBadge(badge, false);
                        }
                        return true;
                    }
                });

        viewport.setOnTouchListener((v, event) -> {
            if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true);
            scaleDetector.onTouchEvent(event);
            tapDetector.onTouchEvent(event);

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastPan.set(event.getX(), event.getY());
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (!scaleDetector.isInProgress() && videoScale > 1.04f && videoTextureView != null) {
                        float dx = event.getX() - lastPan.x;
                        float dy = event.getY() - lastPan.y;
                        float maxDx = (viewport.getWidth() * (videoScale - 1f)) * 0.5f;
                        float maxDy = (viewport.getHeight() * (videoScale - 1f)) * 0.5f;
                        videoTransX = Math.max(-maxDx, Math.min(maxDx, videoTransX + dx));
                        videoTransY = Math.max(-maxDy, Math.min(maxDy, videoTransY + dy));
                        videoTextureView.setTranslationX(videoTransX);
                        videoTextureView.setTranslationY(videoTransY);
                        lastPan.set(event.getX(), event.getY());
                    }
                    break;
            }
            return true;
        });
    }

    private void resetVideoZoom() {
        videoScale = 1.0f;
        videoTransX = 0f;
        videoTransY = 0f;
        if (videoTextureView != null) {
            videoTextureView.setScaleX(1.0f);
            videoTextureView.setScaleY(1.0f);
            videoTextureView.setTranslationX(0f);
            videoTextureView.setTranslationY(0f);
        }
    }

    private void toggleScreenOrientation() {
        isLandscapeLocked = !isLandscapeLocked;
        setRequestedOrientation(isLandscapeLocked
                ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    // סעיף יז: תפריט הגדרות הוידאו הנפתח מהכפתור העליון מימין מתחת לסגור
    private void showVideoSettingsDialog(int theme) {
        String muteStatus = videoMuted ? "מושתק (לחץ להפעלת קול)" : "פעיל (לחץ להשתקה)";
        String autoNextStatus = videoAutoNext ? "פעיל" : "כבוי";
        String repeatLabel = videoRepeatMode == AudioPlayerManager.REPEAT_ONE
                ? "חזרה על הסרטון עצמו"
                : (videoRepeatMode == AudioPlayerManager.REPEAT_FOLDER ? "חזרה על כל התיקייה" : "ללא חזרה");

        List<String> items = Arrays.asList(
                "מהירות הסרטון (כעת: " + formatSpeedLabel(videoSpeed) + ")",
                "מצב חזרה (כעת: " + repeatLabel + ")",
                "שמע הסרטון: " + muteStatus,
                "מעבר אוטומטי לסרטון הבא בתיקייה: " + autoNextStatus,
                "סיבוב מסך (אופקי / אנכי)",
                "איפוס הגדלת תצוגה ידנית (100%)",
                "פתיחה בעורך וידאו מקצועי"
        );

        ThemedDialogHelper.showThemedMenu(
                this,
                "הגדרות הוידאו",
                targetFile.getName(),
                items,
                idx -> {
                    switch (idx) {
                        case 0:
                            showSpeedPickerDialog("בחירת מהירות הסרטון", videoSpeed, chosen -> {
                                videoSpeed = chosen;
                                applyVideoSpeedInternal(isVideoPlayingSafely());
                            });
                            break;
                        case 1:
                            showRepeatPickerDialog("מצב חזרה בוידאו", videoRepeatMode, false, chosen -> videoRepeatMode = chosen);
                            break;
                        case 2:
                            videoMuted = !videoMuted;
                            if (videoPlayer != null) {
                                float vol = videoMuted ? 0f : 1f;
                                videoPlayer.setVolume(vol, vol);
                            }
                            Toast.makeText(this, videoMuted ? "שמע הסרטון הושתק" : "שמע הסרטון הופעל", Toast.LENGTH_SHORT).show();
                            break;
                        case 3:
                            videoAutoNext = !videoAutoNext;
                            Toast.makeText(this, videoAutoNext ? "מעבר אוטומטי הופעל" : "מעבר אוטומטי כובה", Toast.LENGTH_SHORT).show();
                            break;
                        case 4:
                            toggleScreenOrientation();
                            break;
                        case 5:
                            resetVideoZoom();
                            Toast.makeText(this, "הזום אופס ל-100%", Toast.LENGTH_SHORT).show();
                            break;
                        case 6:
                            pauseVideoSafely();
                            Intent ed = new Intent(this, ProVideoEditorActivity.class);
                            ed.putExtra(ProVideoEditorActivity.EXTRA_VIDEO_PATH, targetFile.getAbsolutePath());
                            startActivity(ed);
                            break;
                    }
                }
        );
    }

    private interface SpeedSelectedCallback {
        void onSpeedChosen(float speed);
    }

    private void showSpeedPickerDialog(String title, float current, SpeedSelectedCallback cb) {
        final float[] values = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f};
        List<String> labels = new ArrayList<>();
        for (float v : values) {
            String base = v == 1.0f ? "1.0x (מהירות רגילה)" : String.format(Locale.US, "%.2fx", v).replace("0x", "x");
            if (Math.abs(v - current) < 0.02f) {
                base = "✓  " + base;
            }
            labels.add(base);
        }
        ThemedDialogHelper.showThemedMenu(this, title, "בחר את מהירות ההפעלה הרצויה:", labels, idx -> cb.onSpeedChosen(values[idx]));
    }

    private interface RepeatSelectedCallback {
        void onRepeatChosen(int repeatMode);
    }

    private void showRepeatPickerDialog(String title, int currentMode, boolean isAudio, RepeatSelectedCallback cb) {
        String itemLabel = isAudio ? "חזרה על השיר עצמו" : "חזרה על הסרטון עצמו";
        String folderLabel = isAudio ? "חזרה על כל הקבצים בתיקייה" : "חזרה על כל הסרטונים בתיקייה";
        List<String> opts = Arrays.asList(
                (currentMode == AudioPlayerManager.REPEAT_OFF ? "✓  " : "") + "ללא חזרה",
                (currentMode == AudioPlayerManager.REPEAT_ONE ? "✓  " : "") + itemLabel,
                (currentMode == AudioPlayerManager.REPEAT_FOLDER ? "✓  " : "") + folderLabel
        );
        ThemedDialogHelper.showThemedMenu(this, title, "בחר אופן חזרה בסיום הניגון:", opts, cb::onRepeatChosen);
    }

    private interface DoubleTapSeekAction {
        void onSeekSeconds(int deltaSeconds);
    }

    private void attachDoubleTapSeekGesture(View target, TextView badge, DoubleTapSeekAction action) {
        final GestureDetector detector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                boolean rightSide = e.getX() >= (target.getWidth() / 2f);
                if (rightSide) {
                    action.onSeekSeconds(10);
                    flashSeekBadge(badge, true);
                } else {
                    action.onSeekSeconds(-10);
                    flashSeekBadge(badge, false);
                }
                return true;
            }
        });
        target.setOnTouchListener((v, ev) -> detector.onTouchEvent(ev));
    }

    // סעיף כא: חיווי מסודר של +10 בימין או -10 בשמאל בלחיצה כפולה
    private TextView createSeekOverlayBadge() {
        TextView badge = new TextView(this);
        badge.setTextColor(Color.WHITE);
        badge.setTextSize(18f);
        badge.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        badge.setTextDirection(View.TEXT_DIRECTION_LTR);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(38, 22, 38, 22);
        GradientDrawable pill = new GradientDrawable();
        pill.setCornerRadius(999f);
        pill.setColor(Color.argb(215, 29, 78, 216));
        pill.setStroke(3, Color.WHITE);
        badge.setBackground(pill);
        badge.setVisibility(View.GONE);
        return badge;
    }

    private void flashSeekBadge(TextView badge, boolean forwardRight) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                (forwardRight ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL
        );
        lp.leftMargin = 44;
        lp.rightMargin = 44;
        badge.setLayoutParams(lp);
        badge.setText(forwardRight ? "\u200E+10" : "\u200E-10");
        badge.animate().cancel();
        badge.setAlpha(1f);
        badge.setScaleX(0.85f);
        badge.setScaleY(0.85f);
        badge.setVisibility(View.VISIBLE);
        badge.animate()
                .scaleX(1.05f)
                .scaleY(1.05f)
                .setDuration(140)
                .withEndAction(() -> badge.animate()
                        .alpha(0f)
                        .setStartDelay(380)
                        .setDuration(200)
                        .withEndAction(() -> badge.setVisibility(View.GONE))
                        .start())
                .start();
    }

    private ImageButton createRoundIconButton(int drawableRes, int sizePx, boolean primaryFilled, int accent, int theme) {
        ImageButton btn = new ImageButton(this);
        btn.setImageResource(drawableRes);
        btn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int pad = primaryFilled ? 30 : 24;
        btn.setPadding(pad, pad, pad, pad);

        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        if (primaryFilled) {
            int fill = theme == PrefsManager.THEME_NEON ? Color.parseColor("#00B4D8") : accent;
            circle.setColor(fill);
            btn.setColorFilter(theme == PrefsManager.THEME_NEON ? Color.parseColor("#030712") : Color.WHITE);
        } else {
            circle.setColor(Color.argb(theme == PrefsManager.THEME_LIGHT ? 26 : 44,
                    Color.red(accent), Color.green(accent), Color.blue(accent)));
            circle.setStroke(2, accent);
            btn.setColorFilter(accent);
        }
        btn.setBackground(circle);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sizePx, sizePx);
        lp.setMargins(10, 0, 10, 0);
        btn.setLayoutParams(lp);
        return btn;
    }

    private MaterialButton createSpeedPillButton(float speed, int accent, int theme) {
        MaterialButton btn = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btn.setText(formatSpeedLabel(speed));
        btn.setAllCaps(false);
        btn.setTextSize(12.5f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setTextColor(accent);
        btn.setStrokeColor(ColorStateList.valueOf(accent));
        btn.setIconResource(R.drawable.ic_speed);
        btn.setIconTint(ColorStateList.valueOf(accent));
        btn.setIconSize(42);
        btn.setIconPadding(12);
        btn.setCornerRadius(999);
        btn.setPadding(30, 16, 30, 16);
        return btn;
    }

    private MaterialButton createPencilEditorButton(String label, int accent, int theme, View.OnClickListener onClick) {
        MaterialButton btn = new MaterialButton(this);
        btn.setText(label);
        btn.setAllCaps(false);
        btn.setTextSize(13.5f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        int bgFill = theme == PrefsManager.THEME_NEON ? Color.parseColor("#00B4D8") : accent;
        int fgColor = theme == PrefsManager.THEME_NEON ? Color.parseColor("#030712") : Color.WHITE;
        btn.setBackgroundTintList(ColorStateList.valueOf(bgFill));
        btn.setTextColor(fgColor);
        btn.setIconResource(R.drawable.ic_edit_pencil);
        btn.setIconTint(ColorStateList.valueOf(fgColor));
        btn.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        btn.setIconPadding(16);
        btn.setCornerRadius(24);
        btn.setPadding(28, 22, 28, 22);
        btn.setOnClickListener(onClick);
        return btn;
    }

    private int resolveRepeatIcon(int mode) {
        if (mode == AudioPlayerManager.REPEAT_ONE) return R.drawable.ic_repeat_one;
        if (mode == AudioPlayerManager.REPEAT_FOLDER) return R.drawable.ic_repeat_all;
        return R.drawable.ic_repeat_off;
    }

    private String formatSpeedLabel(float speed) {
        if (Math.abs(speed - Math.round(speed)) < 0.02f) {
            return String.format(Locale.US, "מהירות %.1fx", speed);
        }
        return String.format(Locale.US, "מהירות %.2fx", speed).replaceAll("0x$", "x");
    }

    private boolean isVideoPlayingSafely() {
        try {
            return videoPrepared && videoPlayer != null && videoPlayer.isPlaying();
        } catch (Exception e) {
            return false;
        }
    }

    private void pauseVideoSafely() {
        try {
            if (isVideoPlayingSafely()) videoPlayer.pause();
        } catch (Exception ignored) {
        }
    }

    private void seekVideoBy(int deltaMs) {
        if (!videoPrepared || videoPlayer == null) return;
        try {
            int dur = Math.max(1, videoPlayer.getDuration());
            int next = Math.max(0, Math.min(dur, videoPlayer.getCurrentPosition() + deltaMs));
            videoPlayer.seekTo(next);
        } catch (Exception ignored) {
        }
    }

    private void applyVideoSpeedInternal(boolean shouldPlay) {
        if (!videoPrepared || videoPlayer == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PlaybackParams p = videoPlayer.getPlaybackParams();
                if (p == null) p = new PlaybackParams();
                p.setSpeed(videoSpeed);
                videoPlayer.setPlaybackParams(p);
                if (!shouldPlay && videoPlayer.isPlaying()) {
                    videoPlayer.pause();
                } else if (shouldPlay && !videoPlayer.isPlaying()) {
                    videoPlayer.start();
                }
            } else if (shouldPlay) {
                videoPlayer.start();
            }
        } catch (Exception e) {
            try {
                if (shouldPlay && !videoPlayer.isPlaying()) videoPlayer.start();
            } catch (Exception ignored) {
            }
        }
    }

    private void releaseVideoPlayer() {
        videoPrepared = false;
        if (videoPlayer != null) {
            try { videoPlayer.stop(); } catch (Exception ignored) {}
            try { videoPlayer.release(); } catch (Exception ignored) {}
            videoPlayer = null;
        }
    }

    private void renderPdfViewer(LinearLayout host, int cardColor, int textSecondary, int theme) {
        LinearLayout card = createSurfaceCard(cardColor);
        card.setGravity(Gravity.CENTER);

        ZoomableImageView pageImage = new ZoomableImageView(this);
        pageImage.setBackgroundColor(Color.WHITE);
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
        card.setPadding(26, 24, 26, 24);
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
        // סעיף יב: ביציאה מהאודיו איננו עוצרים את AudioPlayerManager — השיר ממשיך לנגן ברקע עם השורה התחתונה!
        audioTickerActive = false;
        if (audioListener != null) {
            AudioPlayerManager.getInstance().removeListener(audioListener);
            audioListener = null;
        }
        videoTickerActive = false;
        releaseVideoPlayer();

        if (pdfRenderer != null) {
            try { pdfRenderer.close(); } catch (Exception ignored) {}
        }
        if (pdfDescriptor != null) {
            try { pdfDescriptor.close(); } catch (Exception ignored) {}
        }
        executor.shutdownNow();
    }
}
