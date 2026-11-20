package com.aishhatorah.filemanagerpro;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.FileProvider;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * עורך תמונות מקצועי מובנה במסך מלא (Edge-to-Edge):
 * - תצוגת תמונה בגודל מלא מאחורי סרגלי זכוכית שקופים
 * - היסטוריית ביטול / ביצוע מחדש מלאה (Undo / Redo) בסרגל העליון
 * - סרגל כלים תחתון נקי: סיבוב מהיר (90°), מראה (אופקי/אנכי), חיתוך יחס, מכחול + צבעים, פילטרים ובוררי בהירות/ניגודיות/רוויה
 */
public class ProImageEditorActivity extends AppCompatActivity {

    public static final String EXTRA_IMAGE_PATH = "extra_image_path";

    private static final int DRAWER_NONE = 0;
    private static final int DRAWER_MIRROR = 1;
    private static final int DRAWER_CROP = 2;
    private static final int DRAWER_BRUSH = 3;
    private static final int DRAWER_FILTERS = 4;
    private static final int DRAWER_ADJUST = 5;

    private static final String[] CROP_LABELS = {"מקורי", "1:1", "4:3", "16:9", "3:4", "9:16"};
    private static final String[] FILTER_NAMES = {"מקורי", "חד וחי", "חם", "קריר", "שחור-לבן", "ספיה", "וינטג'"};
    private static final int[] BRUSH_COLORS = {
            Color.parseColor("#EF4444"),
            Color.parseColor("#F97316"),
            Color.parseColor("#FACC15"),
            Color.parseColor("#10B981"),
            Color.parseColor("#06B6D4"),
            Color.parseColor("#3B82F6"),
            Color.parseColor("#8B5CF6"),
            Color.WHITE,
            Color.parseColor("#111827")
    };
    private static final float[] BRUSH_WIDTH_FRACTIONS = {0.006f, 0.013f, 0.024f};
    private static final String[] BRUSH_WIDTH_LABELS = {"דק", "בינוני", "עבה"};

    private static class BrushStroke {
        final Path path;
        final int color;
        final float widthFraction;

        BrushStroke(Path path, int color, float widthFraction) {
            this.path = path;
            this.color = color;
            this.widthFraction = widthFraction;
        }

        BrushStroke copy() {
            return new BrushStroke(new Path(path), color, widthFraction);
        }
    }

    private static class EditorState {
        final int rotationDeg;
        final boolean flipH;
        final boolean flipV;
        final int cropMode;
        final int presetFilter;
        final float brightnessOffset;
        final float contrastFactor;
        final float saturationFactor;
        final List<BrushStroke> strokes;

        EditorState(
                int rotationDeg,
                boolean flipH,
                boolean flipV,
                int cropMode,
                int presetFilter,
                float brightnessOffset,
                float contrastFactor,
                float saturationFactor,
                List<BrushStroke> srcStrokes
        ) {
            this.rotationDeg = rotationDeg;
            this.flipH = flipH;
            this.flipV = flipV;
            this.cropMode = cropMode;
            this.presetFilter = presetFilter;
            this.brightnessOffset = brightnessOffset;
            this.contrastFactor = contrastFactor;
            this.saturationFactor = saturationFactor;
            this.strokes = new ArrayList<>(srcStrokes.size());
            for (BrushStroke s : srcStrokes) {
                this.strokes.add(s.copy());
            }
        }
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<BrushStroke> strokes = new ArrayList<>();
    private final List<EditorState> undoStack = new ArrayList<>();
    private final List<EditorState> redoStack = new ArrayList<>();

    private File sourceFile;
    private Bitmap originalBitmap;
    private Bitmap transformedBitmap;
    private Bitmap filterThumbBitmap;
    private EditorCanvasView canvasView;

    private int rotationDeg = 0;
    private boolean flipH = false;
    private boolean flipV = false;
    private int cropMode = 0;
    private int presetFilter = 0;
    private float brightnessOffset = 0f;
    private float contrastFactor = 1f;
    private float saturationFactor = 1f;

    private int activeDrawer = DRAWER_NONE;
    private boolean drawModeEnabled = false;
    private int activeBrushColor = BRUSH_COLORS[0];
    private int activeBrushWidthIdx = 1;
    private File lastSavedFile = null;
    private boolean isSyncingSliders = false;
    private int accent;

    // UI references
    private ImageButton btnUndo;
    private ImageButton btnRedo;
    private ImageButton btnShareSaved;
    private FrameLayout subDrawerContainer;
    private View drawerDivider;
    private LinearLayout panelMirror;
    private HorizontalScrollView panelCrop;
    private LinearLayout panelBrush;
    private HorizontalScrollView panelFilters;
    private LinearLayout panelAdjust;

    private final List<ImageView> navTabIcons = new ArrayList<>();
    private final List<TextView> navTabLabels = new ArrayList<>();
    private final int[] navTabDrawers = {
            DRAWER_NONE, DRAWER_MIRROR, DRAWER_CROP, DRAWER_BRUSH, DRAWER_FILTERS, DRAWER_ADJUST
    };

    private LinearLayout mirrorHChip;
    private LinearLayout mirrorVChip;
    private final List<TextView> cropChips = new ArrayList<>();
    private final List<View> brushColorDots = new ArrayList<>();
    private final List<TextView> brushWidthChips = new ArrayList<>();
    private final List<ImageView> filterThumbViews = new ArrayList<>();
    private final List<TextView> filterLabels = new ArrayList<>();
    private final List<FrameLayout> filterFrames = new ArrayList<>();

    private SeekBar seekBrightness;
    private SeekBar seekContrast;
    private SeekBar seekSaturation;
    private TextView tvBrightnessVal;
    private TextView tvContrastVal;
    private TextView tvSaturationVal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String path = getIntent().getStringExtra(EXTRA_IMAGE_PATH);
        if (path == null) {
            finish();
            return;
        }
        sourceFile = new File(path);
        if (!sourceFile.exists()) {
            Toast.makeText(this, "קובץ התמונה אינו זמין", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        int theme = PrefsManager.getThemeMode(this);
        accent = ThemeHelper.getAccentColor(this, theme);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        Window w = getWindow();
        if (w != null) {
            w.setStatusBarColor(Color.TRANSPARENT);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                w.setNavigationBarColor(Color.TRANSPARENT);
            }
            View decor = w.getDecorView();
            if (decor != null) {
                int vis = decor.getSystemUiVisibility();
                vis &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vis &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
                decor.setSystemUiVisibility(vis);
            }
        }

        FrameLayout stageRoot = new FrameLayout(this);
        stageRoot.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        stageRoot.setBackgroundColor(Color.parseColor("#080B12"));

        // משטח התצוגה על כל המסך
        canvasView = new EditorCanvasView();
        stageRoot.addView(canvasView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // סרגל עליון שקוף למחצה: חזרה, כותרת, ביטול, ביצוע מחדש, שיתוף ושמירה
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setClickable(true);
        topBar.setBackgroundColor(Color.argb(176, 18, 26, 44));
        topBar.setPadding(dp(10), dp(24), dp(10), dp(8));

        ImageButton btnBack = ThemeHelper.createBorderlessSvgButton(
                this, R.drawable.ic_arrow_back, Color.WHITE, "חזרה", 40, 8, v -> finish());
        topBar.addView(btnBack);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("עורך תמונות");
        tvTitle.setTextSize(ThemeHelper.spScaled(this, 15.5f));
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setSingleLine(true);
        tvTitle.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tLp.setMarginStart(dp(6));
        tLp.setMarginEnd(dp(6));
        topBar.addView(tvTitle, tLp);

        btnUndo = ThemeHelper.createBorderlessSvgButton(
                this, R.drawable.ic_undo, Color.WHITE, "ביטול פעולה", 38, 8, v -> performUndo());
        topBar.addView(btnUndo);

        btnRedo = ThemeHelper.createBorderlessSvgButton(
                this, R.drawable.ic_redo, Color.WHITE, "ביצוע מחדש", 38, 8, v -> performRedo());
        topBar.addView(btnRedo);

        btnShareSaved = ThemeHelper.createBorderlessSvgButton(
                this, R.drawable.ic_share, Color.WHITE, "שיתוף תמונה ערוכה", 38, 8, v -> shareSavedFile());
        btnShareSaved.setVisibility(View.GONE);
        topBar.addView(btnShareSaved);

        Button btnSave = new Button(this);
        btnSave.setText("שמור");
        btnSave.setAllCaps(false);
        btnSave.setTextSize(ThemeHelper.spScaled(this, 12.5f));
        btnSave.setTypeface(Typeface.DEFAULT_BOLD);
        btnSave.setTextColor(Color.WHITE);
        GradientDrawable saveBg = new GradientDrawable();
        saveBg.setCornerRadius(dp(18));
        saveBg.setColor(accent);
        btnSave.setBackground(saveBg);
        btnSave.setMinHeight(0);
        btnSave.setMinimumHeight(0);
        btnSave.setMinWidth(0);
        btnSave.setMinimumWidth(0);
        btnSave.setPadding(dp(16), dp(7), dp(16), dp(7));
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        saveLp.setMarginStart(dp(4));
        btnSave.setLayoutParams(saveLp);
        btnSave.setOnClickListener(v -> saveEditedImage());
        topBar.addView(btnSave);

        stageRoot.addView(topBar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));

        // פאנל כלים תחתון שקוף למחצה וצמוד לתחתית
        LinearLayout bottomDock = new LinearLayout(this);
        bottomDock.setOrientation(LinearLayout.VERTICAL);
        bottomDock.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        bottomDock.setClickable(true);
        bottomDock.setBackgroundColor(Color.argb(188, 16, 23, 40));

        View topEdgeLine = new View(this);
        topEdgeLine.setBackgroundColor(Color.argb(40, 255, 255, 255));
        bottomDock.addView(topEdgeLine, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1))));

        // מגירת אפשרויות נפתחת מעל שורת האייקונים
        subDrawerContainer = new FrameLayout(this);
        subDrawerContainer.setVisibility(View.GONE);
        subDrawerContainer.setPadding(dp(10), dp(10), dp(10), dp(8));

        buildMirrorDrawer();
        buildCropDrawer();
        buildBrushDrawer();
        buildFiltersDrawer();
        buildAdjustDrawer();

        subDrawerContainer.addView(panelMirror);
        subDrawerContainer.addView(panelCrop);
        subDrawerContainer.addView(panelBrush);
        subDrawerContainer.addView(panelFilters);
        subDrawerContainer.addView(panelAdjust);
        bottomDock.addView(subDrawerContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        drawerDivider = new View(this);
        drawerDivider.setBackgroundColor(Color.argb(30, 255, 255, 255));
        drawerDivider.setVisibility(View.GONE);
        bottomDock.addView(drawerDivider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1))));

        // שורת האייקונים התחתונה הראשית
        LinearLayout iconRow = new LinearLayout(this);
        iconRow.setOrientation(LinearLayout.HORIZONTAL);
        iconRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        iconRow.setGravity(Gravity.CENTER_VERTICAL);
        iconRow.setPadding(dp(4), dp(6), dp(4), dp(6));

        addNavTab(iconRow, R.drawable.ic_rotate_right, "סיבוב", 0, () -> {
            if (activeDrawer == DRAWER_BRUSH) {
                setDrawerMode(DRAWER_NONE);
            }
            rotateExistingStrokes90();
            rotationDeg = (rotationDeg + 90) % 360;
            rebuildTransformedBitmap();
            pushHistoryState();
        });
        addNavTab(iconRow, R.drawable.ic_flip_horizontal, "מראה", 1, () -> toggleDrawer(DRAWER_MIRROR));
        addNavTab(iconRow, R.drawable.ic_crop, "חיתוך", 2, () -> toggleDrawer(DRAWER_CROP));
        addNavTab(iconRow, R.drawable.ic_edit_pencil, "מכחול", 3, () -> toggleDrawer(DRAWER_BRUSH));
        addNavTab(iconRow, R.drawable.ic_filters, "פילטרים", 4, () -> toggleDrawer(DRAWER_FILTERS));
        addNavTab(iconRow, R.drawable.ic_tune, "כוונון", 5, () -> toggleDrawer(DRAWER_ADJUST));

        bottomDock.addView(iconRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        stageRoot.addView(bottomDock, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));

        ViewCompat.setOnApplyWindowInsetsListener(stageRoot, (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            topBar.setPadding(dp(10) + sys.left, sys.top + dp(6), dp(10) + sys.right, dp(8));
            bottomDock.setPadding(sys.left, 0, sys.right, sys.bottom + dp(4));
            return insets;
        });

        setContentView(stageRoot);
        pushHistoryState();
        updateAllUiHighlights();
        loadInitialBitmap();
    }

    private void addNavTab(LinearLayout row, int iconRes, String label, int tabIndex, Runnable onTap) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setPadding(dp(2), dp(4), dp(2), dp(4));

        ImageView iv = new ImageView(this);
        iv.setImageResource(iconRes);
        iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        iv.setColorFilter(Color.WHITE);
        col.addView(iv, new LinearLayout.LayoutParams(dp(22), dp(22)));

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(9.5f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(Color.parseColor("#CBD5E1"));
        tv.setSingleLine(true);
        tv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tvLp.topMargin = dp(2);
        col.addView(tv, tvLp);

        navTabIcons.add(iv);
        navTabLabels.add(tv);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        col.setLayoutParams(lp);
        col.setOnClickListener(v -> onTap.run());
        row.addView(col);
    }

    private void toggleDrawer(int mode) {
        if (activeDrawer == mode) {
            setDrawerMode(DRAWER_NONE);
        } else {
            setDrawerMode(mode);
        }
    }

    private void setDrawerMode(int mode) {
        activeDrawer = mode;
        drawModeEnabled = (mode == DRAWER_BRUSH);

        boolean visible = (mode != DRAWER_NONE);
        subDrawerContainer.setVisibility(visible ? View.VISIBLE : View.GONE);
        drawerDivider.setVisibility(visible ? View.VISIBLE : View.GONE);

        panelMirror.setVisibility(mode == DRAWER_MIRROR ? View.VISIBLE : View.GONE);
        panelCrop.setVisibility(mode == DRAWER_CROP ? View.VISIBLE : View.GONE);
        panelBrush.setVisibility(mode == DRAWER_BRUSH ? View.VISIBLE : View.GONE);
        panelFilters.setVisibility(mode == DRAWER_FILTERS ? View.VISIBLE : View.GONE);
        panelAdjust.setVisibility(mode == DRAWER_ADJUST ? View.VISIBLE : View.GONE);

        updateNavHighlight();
    }

    private void updateNavHighlight() {
        for (int i = 0; i < navTabIcons.size(); i++) {
            boolean selected = (navTabDrawers[i] != DRAWER_NONE && navTabDrawers[i] == activeDrawer);
            int c = selected ? accent : Color.WHITE;
            int labelC = selected ? accent : Color.parseColor("#CBD5E1");
            navTabIcons.get(i).setColorFilter(c);
            navTabLabels.get(i).setTextColor(labelC);
        }
    }

    private void buildMirrorDrawer() {
        panelMirror = new LinearLayout(this);
        panelMirror.setOrientation(LinearLayout.HORIZONTAL);
        panelMirror.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        panelMirror.setGravity(Gravity.CENTER);
        panelMirror.setVisibility(View.GONE);

        mirrorHChip = createMirrorOptionChip(R.drawable.ic_flip_horizontal, "היפוך אופקי", v -> {
            flipExistingStrokes(true, false);
            flipH = !flipH;
            rebuildTransformedBitmap();
            pushHistoryState();
            updateMirrorChips();
        });
        mirrorVChip = createMirrorOptionChip(R.drawable.ic_flip_vertical, "היפוך אנכי", v -> {
            flipExistingStrokes(false, true);
            flipV = !flipV;
            rebuildTransformedBitmap();
            pushHistoryState();
            updateMirrorChips();
        });

        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp1.setMarginEnd(dp(12));
        panelMirror.addView(mirrorHChip, lp1);
        panelMirror.addView(mirrorVChip, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private LinearLayout createMirrorOptionChip(int iconRes, String title, View.OnClickListener l) {
        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding(dp(16), dp(8), dp(16), dp(8));

        ImageView iv = new ImageView(this);
        iv.setImageResource(iconRes);
        iv.setColorFilter(Color.WHITE);
        chip.addView(iv, new LinearLayout.LayoutParams(dp(20), dp(20)));

        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextSize(12.5f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tLp.setMarginStart(dp(8));
        chip.addView(tv, tLp);

        chip.setOnClickListener(l);
        return chip;
    }

    private void updateMirrorChips() {
        applySelectablePill(mirrorHChip, flipH);
        applySelectablePill(mirrorVChip, flipV);
    }

    private void buildCropDrawer() {
        panelCrop = new HorizontalScrollView(this);
        panelCrop.setHorizontalScrollBarEnabled(false);
        panelCrop.setVisibility(View.GONE);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        cropChips.clear();
        for (int i = 0; i < CROP_LABELS.length; i++) {
            final int idx = i;
            TextView chip = new TextView(this);
            chip.setText(CROP_LABELS[i]);
            chip.setTextSize(12.5f);
            chip.setTypeface(Typeface.DEFAULT_BOLD);
            chip.setTextColor(Color.WHITE);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(16), dp(8), dp(16), dp(8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(8));
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> {
                if (cropMode != idx) {
                    cropMode = idx;
                    rebuildTransformedBitmap();
                    pushHistoryState();
                    updateCropChips();
                }
            });
            cropChips.add(chip);
            row.addView(chip);
        }
        panelCrop.addView(row);
    }

    private void updateCropChips() {
        for (int i = 0; i < cropChips.size(); i++) {
            applySelectablePill(cropChips.get(i), cropMode == i);
        }
    }

    private void buildBrushDrawer() {
        panelBrush = new LinearLayout(this);
        panelBrush.setOrientation(LinearLayout.VERTICAL);
        panelBrush.setVisibility(View.GONE);

        HorizontalScrollView colorsScroll = new HorizontalScrollView(this);
        colorsScroll.setHorizontalScrollBarEnabled(false);

        LinearLayout colorRow = new LinearLayout(this);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        colorRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        colorRow.setGravity(Gravity.CENTER_VERTICAL);
        colorRow.setPadding(dp(2), dp(2), dp(2), dp(2));

        brushColorDots.clear();
        for (int colorVal : BRUSH_COLORS) {
            View dot = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(30), dp(30));
            lp.setMarginEnd(dp(10));
            dot.setLayoutParams(lp);
            dot.setOnClickListener(v -> {
                activeBrushColor = colorVal;
                drawModeEnabled = true;
                updateBrushUi();
            });
            brushColorDots.add(dot);
            colorRow.addView(dot);
        }

        View sep = new View(this);
        sep.setBackgroundColor(Color.argb(60, 255, 255, 255));
        LinearLayout.LayoutParams sepLp = new LinearLayout.LayoutParams(Math.max(1, dp(1)), dp(22));
        sepLp.setMarginStart(dp(4));
        sepLp.setMarginEnd(dp(10));
        colorRow.addView(sep, sepLp);

        brushWidthChips.clear();
        for (int i = 0; i < BRUSH_WIDTH_LABELS.length; i++) {
            final int idx = i;
            TextView wChip = new TextView(this);
            wChip.setText(BRUSH_WIDTH_LABELS[i]);
            wChip.setTextSize(11.5f);
            wChip.setTypeface(Typeface.DEFAULT_BOLD);
            wChip.setTextColor(Color.WHITE);
            wChip.setPadding(dp(11), dp(5), dp(11), dp(5));
            LinearLayout.LayoutParams wLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            wLp.setMarginEnd(dp(6));
            wChip.setLayoutParams(wLp);
            wChip.setOnClickListener(v -> {
                activeBrushWidthIdx = idx;
                updateBrushUi();
            });
            brushWidthChips.add(wChip);
            colorRow.addView(wChip);
        }

        colorsScroll.addView(colorRow);
        panelBrush.addView(colorsScroll);
    }

    private void updateBrushUi() {
        for (int i = 0; i < brushColorDots.size(); i++) {
            int c = BRUSH_COLORS[i];
            boolean sel = (c == activeBrushColor);
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(c);
            circle.setStroke(sel ? dp(3) : Math.max(1, dp(1)),
                    sel ? Color.WHITE : Color.argb(120, 255, 255, 255));
            brushColorDots.get(i).setBackground(circle);
            float scale = sel ? 1.15f : 0.94f;
            brushColorDots.get(i).setScaleX(scale);
            brushColorDots.get(i).setScaleY(scale);
        }
        for (int i = 0; i < brushWidthChips.size(); i++) {
            applySelectablePill(brushWidthChips.get(i), activeBrushWidthIdx == i);
        }
    }

    private void buildFiltersDrawer() {
        panelFilters = new HorizontalScrollView(this);
        panelFilters.setHorizontalScrollBarEnabled(false);
        panelFilters.setVisibility(View.GONE);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        filterThumbViews.clear();
        filterLabels.clear();
        filterFrames.clear();

        for (int i = 0; i < FILTER_NAMES.length; i++) {
            final int fIdx = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            itemLp.setMarginEnd(dp(10));
            item.setLayoutParams(itemLp);

            FrameLayout thumbFrame = new FrameLayout(this);
            thumbFrame.setPadding(dp(2), dp(2), dp(2), dp(2));
            ImageView thumb = new ImageView(this);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumb.setColorFilter(new ColorMatrixColorFilter(buildColorMatrix(fIdx, 0f, 1f, 1f)));
            thumbFrame.addView(thumb, new FrameLayout.LayoutParams(dp(46), dp(46)));
            item.addView(thumbFrame);

            TextView lbl = new TextView(this);
            lbl.setText(FILTER_NAMES[i]);
            lbl.setTextSize(10.5f);
            lbl.setTypeface(Typeface.DEFAULT_BOLD);
            lbl.setTextColor(Color.WHITE);
            lbl.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lLp.topMargin = dp(4);
            item.addView(lbl, lLp);

            item.setOnClickListener(v -> {
                if (presetFilter != fIdx) {
                    presetFilter = fIdx;
                    canvasView.updateColorFilter();
                    pushHistoryState();
                    updateFilterCards();
                }
            });

            filterThumbViews.add(thumb);
            filterLabels.add(lbl);
            filterFrames.add(thumbFrame);
            row.addView(item);
        }
        panelFilters.addView(row);
    }

    private void updateFilterCards() {
        for (int i = 0; i < filterFrames.size(); i++) {
            boolean sel = (presetFilter == i);
            GradientDrawable border = new GradientDrawable();
            border.setCornerRadius(dp(8));
            border.setColor(Color.argb(60, 255, 255, 255));
            border.setStroke(sel ? dp(2) : Math.max(1, dp(1)),
                    sel ? accent : Color.argb(70, 255, 255, 255));
            filterFrames.get(i).setBackground(border);
            filterLabels.get(i).setTextColor(sel ? accent : Color.parseColor("#E2E8F0"));
        }
    }

    private void populateFilterThumbnails() {
        if (filterThumbBitmap == null) return;
        for (ImageView iv : filterThumbViews) {
            iv.setImageBitmap(filterThumbBitmap);
        }
    }

    private void buildAdjustDrawer() {
        panelAdjust = new LinearLayout(this);
        panelAdjust.setOrientation(LinearLayout.VERTICAL);
        panelAdjust.setVisibility(View.GONE);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(2), 0, dp(2), dp(4));

        TextView tvTitle = new TextView(this);
        tvTitle.setText("כוונון תאורה וצבע");
        tvTitle.setTextSize(11.5f);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setTextColor(Color.parseColor("#CBD5E1"));
        header.addView(tvTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageButton btnResetAdj = ThemeHelper.createBorderlessSvgButton(
                this, R.drawable.ic_reset, accent, "איפוס כוונון", 28, 4, v -> {
                    brightnessOffset = 0f;
                    contrastFactor = 1f;
                    saturationFactor = 1f;
                    syncAdjustmentSliders();
                    canvasView.updateColorFilter();
                    pushHistoryState();
                });
        header.addView(btnResetAdj);
        panelAdjust.addView(header);

        tvBrightnessVal = new TextView(this);
        seekBrightness = addAdjustmentRow(panelAdjust, "בהירות", 50, tvBrightnessVal, p -> {
            brightnessOffset = (p - 50) * 1.6f;
            tvBrightnessVal.setText(formatSignedInt(p - 50));
            canvasView.updateColorFilter();
        });

        tvContrastVal = new TextView(this);
        seekContrast = addAdjustmentRow(panelAdjust, "ניגודיות", 50, tvContrastVal, p -> {
            contrastFactor = progressToContrast(p);
            tvContrastVal.setText(String.format(Locale.US, "%.1fx", contrastFactor));
            canvasView.updateColorFilter();
        });

        tvSaturationVal = new TextView(this);
        seekSaturation = addAdjustmentRow(panelAdjust, "רוויה", 50, tvSaturationVal, p -> {
            saturationFactor = p / 50f;
            tvSaturationVal.setText(formatSignedInt(p - 50));
            canvasView.updateColorFilter();
        });

        syncAdjustmentSliders();
    }

    private interface SliderCallback {
        void onChanged(int progress);
    }

    private SeekBar addAdjustmentRow(LinearLayout parent, String label, int initial, TextView valueView, SliderCallback cb) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(2), 0, dp(2));

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextColor(Color.WHITE);
        tvLabel.setTextSize(12f);
        tvLabel.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(tvLabel, new LinearLayout.LayoutParams(dp(58), ViewGroup.LayoutParams.WRAP_CONTENT));

        SeekBar sb = new SeekBar(this);
        sb.setMax(100);
        sb.setProgress(initial);
        ThemeHelper.applySeekBarTint(sb, accent);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (isSyncingSliders) return;
                if (fromUser) cb.onChanged(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (!isSyncingSliders) {
                    pushHistoryState();
                }
            }
        });
        row.addView(sb, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        valueView.setTextColor(Color.parseColor("#CBD5E1"));
        valueView.setTextSize(11f);
        valueView.setTypeface(Typeface.DEFAULT_BOLD);
        valueView.setGravity(Gravity.END);
        row.addView(valueView, new LinearLayout.LayoutParams(dp(40), ViewGroup.LayoutParams.WRAP_CONTENT));

        parent.addView(row);
        return sb;
    }

    private static float progressToContrast(int progress) {
        if (progress <= 50) {
            return 0.5f + (progress / 50f) * 0.5f;
        }
        return 1.0f + ((progress - 50) / 50f) * 0.8f;
    }

    private static int contrastToProgress(float contrast) {
        if (contrast <= 1.0f) {
            return Math.max(0, Math.min(50, Math.round(((contrast - 0.5f) / 0.5f) * 50f)));
        }
        return Math.max(50, Math.min(100, 50 + Math.round(((contrast - 1.0f) / 0.8f) * 50f)));
    }

    private static String formatSignedInt(int val) {
        return val > 0 ? ("+" + val) : String.valueOf(val);
    }

    private void syncAdjustmentSliders() {
        isSyncingSliders = true;
        int bProg = Math.max(0, Math.min(100, Math.round(50 + brightnessOffset / 1.6f)));
        int cProg = contrastToProgress(contrastFactor);
        int sProg = Math.max(0, Math.min(100, Math.round(saturationFactor * 50f)));

        seekBrightness.setProgress(bProg);
        seekContrast.setProgress(cProg);
        seekSaturation.setProgress(sProg);

        tvBrightnessVal.setText(formatSignedInt(bProg - 50));
        tvContrastVal.setText(String.format(Locale.US, "%.1fx", contrastFactor));
        tvSaturationVal.setText(formatSignedInt(sProg - 50));
        isSyncingSliders = false;
    }

    private void applySelectablePill(View v, boolean selected) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        bg.setColor(selected ? ColorUtils.setAlphaComponent(accent, 85) : Color.argb(45, 255, 255, 255));
        bg.setStroke(Math.max(1, dp(1)), selected ? accent : Color.argb(65, 255, 255, 255));
        v.setBackground(bg);
    }

    private void pushHistoryState() {
        EditorState snap = new EditorState(
                rotationDeg, flipH, flipV, cropMode, presetFilter,
                brightnessOffset, contrastFactor, saturationFactor, strokes
        );
        undoStack.add(snap);
        if (undoStack.size() > 35) {
            undoStack.remove(0);
        }
        redoStack.clear();
        updateUndoRedoButtons();
    }

    private void performUndo() {
        if (undoStack.size() <= 1) return;
        EditorState current = undoStack.remove(undoStack.size() - 1);
        redoStack.add(current);
        restoreState(undoStack.get(undoStack.size() - 1));
    }

    private void performRedo() {
        if (redoStack.isEmpty()) return;
        EditorState next = redoStack.remove(redoStack.size() - 1);
        undoStack.add(next);
        restoreState(next);
    }

    private void restoreState(EditorState state) {
        rotationDeg = state.rotationDeg;
        flipH = state.flipH;
        flipV = state.flipV;
        cropMode = state.cropMode;
        presetFilter = state.presetFilter;
        brightnessOffset = state.brightnessOffset;
        contrastFactor = state.contrastFactor;
        saturationFactor = state.saturationFactor;
        strokes.clear();
        for (BrushStroke s : state.strokes) {
            strokes.add(s.copy());
        }
        rebuildTransformedBitmap();
        updateAllUiHighlights();
    }

    private void updateAllUiHighlights() {
        updateUndoRedoButtons();
        updateMirrorChips();
        updateCropChips();
        updateBrushUi();
        updateFilterCards();
        syncAdjustmentSliders();
        updateNavHighlight();
    }

    private void updateUndoRedoButtons() {
        boolean canUndo = undoStack.size() > 1;
        boolean canRedo = !redoStack.isEmpty();
        if (btnUndo != null) {
            btnUndo.setEnabled(canUndo);
            btnUndo.setAlpha(canUndo ? 1f : 0.32f);
        }
        if (btnRedo != null) {
            btnRedo.setEnabled(canRedo);
            btnRedo.setAlpha(canRedo ? 1f : 0.32f);
        }
    }

    private void rotateExistingStrokes90() {
        if (transformedBitmap == null || strokes.isEmpty()) return;
        int w = transformedBitmap.getWidth();
        int h = transformedBitmap.getHeight();
        Matrix m = new Matrix();
        m.postTranslate(-w / 2f, -h / 2f);
        m.postRotate(90f);
        m.postTranslate(h / 2f, w / 2f);
        for (BrushStroke s : strokes) {
            s.path.transform(m);
        }
    }

    private void flipExistingStrokes(boolean horiz, boolean vert) {
        if (transformedBitmap == null || strokes.isEmpty()) return;
        float cx = transformedBitmap.getWidth() / 2f;
        float cy = transformedBitmap.getHeight() / 2f;
        Matrix m = new Matrix();
        m.postScale(horiz ? -1f : 1f, vert ? -1f : 1f, cx, cy);
        for (BrushStroke s : strokes) {
            s.path.transform(m);
        }
    }

    private void loadInitialBitmap() {
        executor.execute(() -> {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(sourceFile.getAbsolutePath(), bounds);
            int sample = 1;
            while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) {
                sample *= 2;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            originalBitmap = BitmapFactory.decodeFile(sourceFile.getAbsolutePath(), opts);
            if (originalBitmap != null) {
                int thumbSize = 128;
                int bw = originalBitmap.getWidth();
                int bh = originalBitmap.getHeight();
                float scale = Math.min((float) thumbSize / bw, (float) thumbSize / bh);
                int tw = Math.max(24, Math.round(bw * scale));
                int th = Math.max(24, Math.round(bh * scale));
                filterThumbBitmap = Bitmap.createScaledBitmap(originalBitmap, tw, th, true);
            }
            runOnUiThread(() -> {
                populateFilterThumbnails();
                rebuildTransformedBitmap();
            });
        });
    }

    private void rebuildTransformedBitmap() {
        if (originalBitmap == null || originalBitmap.isRecycled()) return;
        Bitmap oldTransformed = transformedBitmap;
        Bitmap src = originalBitmap;
        Bitmap cropTemp = null;

        if (cropMode != 0) {
            int w = src.getWidth();
            int h = src.getHeight();
            int targetW = w;
            int targetH = h;
            if (cropMode == 1) { // 1:1
                int side = Math.min(w, h);
                targetW = side;
                targetH = side;
            } else if (cropMode == 2) { // 4:3
                if (w * 3 > h * 4) targetW = (h * 4) / 3;
                else targetH = (w * 3) / 4;
            } else if (cropMode == 3) { // 16:9
                if (w * 9 > h * 16) targetW = (h * 16) / 9;
                else targetH = (w * 9) / 16;
            } else if (cropMode == 4) { // 3:4
                if (w * 4 > h * 3) targetW = (h * 3) / 4;
                else targetH = (w * 4) / 3;
            } else if (cropMode == 5) { // 9:16
                if (w * 16 > h * 9) targetW = (h * 9) / 16;
                else targetH = (w * 16) / 9;
            }
            int x = Math.max(0, (w - targetW) / 2);
            int y = Math.max(0, (h - targetH) / 2);
            cropTemp = Bitmap.createBitmap(src, x, y, Math.max(1, targetW), Math.max(1, targetH));
            src = cropTemp;
        }

        Matrix m = new Matrix();
        if (rotationDeg != 0) {
            m.postRotate(rotationDeg);
        }
        m.postScale(flipH ? -1f : 1f, flipV ? -1f : 1f);

        Bitmap nextTransformed = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
        transformedBitmap = nextTransformed;
        canvasView.setImageBitmap(transformedBitmap);
        canvasView.updateColorFilter();
        canvasView.invalidate();

        if (cropTemp != null && cropTemp != originalBitmap && cropTemp != nextTransformed && !cropTemp.isRecycled()) {
            cropTemp.recycle();
        }
        if (oldTransformed != null && oldTransformed != originalBitmap && oldTransformed != nextTransformed && !oldTransformed.isRecycled()) {
            oldTransformed.recycle();
        }
    }

    private static ColorMatrix buildColorMatrix(int preset, float bOffset, float cFactor, float sFactor) {
        ColorMatrix combined = new ColorMatrix();

        float effectiveSat = sFactor;
        if (preset == 1) effectiveSat = sFactor * 1.38f;
        else if (preset == 4) effectiveSat = 0f;
        else if (preset == 5) effectiveSat = sFactor * 0.25f;
        else if (preset == 6) effectiveSat = sFactor * 0.72f;

        ColorMatrix sat = new ColorMatrix();
        sat.setSaturation(effectiveSat);
        combined.postConcat(sat);

        if (preset == 5) { // ספיה
            ColorMatrix sepia = new ColorMatrix(new float[]{
                    0.393f, 0.769f, 0.189f, 0f, 0f,
                    0.349f, 0.686f, 0.168f, 0f, 0f,
                    0.272f, 0.534f, 0.131f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
            });
            combined.postConcat(sepia);
        }

        float c = cFactor;
        if (preset == 1) c = cFactor * 1.15f;
        else if (preset == 4) c = cFactor * 1.08f;
        else if (preset == 6) c = cFactor * 0.92f;

        float translate = (-0.5f * c + 0.5f) * 255f + bOffset;
        if (preset == 6) translate += 14f;

        float rBoost = preset == 2 ? 18f : (preset == 3 ? -12f : (preset == 6 ? 10f : 0f));
        float gBoost = preset == 2 ? 6f : (preset == 3 ? 2f : (preset == 6 ? 4f : 0f));
        float bBoost = preset == 3 ? 22f : (preset == 2 ? -14f : (preset == 6 ? -8f : 0f));

        ColorMatrix bc = new ColorMatrix(new float[]{
                c, 0, 0, 0, translate + rBoost,
                0, c, 0, 0, translate + gBoost,
                0, 0, c, 0, translate + bBoost,
                0, 0, 0, 1, 0
        });
        combined.postConcat(bc);
        return combined;
    }

    private void saveEditedImage() {
        if (transformedBitmap == null) return;
        final Bitmap base = transformedBitmap;
        final ColorMatrix cm = buildColorMatrix(presetFilter, brightnessOffset, contrastFactor, saturationFactor);
        final List<BrushStroke> strokesCopy = new ArrayList<>(strokes.size());
        for (BrushStroke s : strokes) strokesCopy.add(s.copy());

        executor.execute(() -> {
            try {
                int w = Math.max(1, base.getWidth());
                int h = Math.max(1, base.getHeight());
                Bitmap outBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(outBitmap);

                Paint imgPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
                imgPaint.setColorFilter(new ColorMatrixColorFilter(cm));
                c.drawBitmap(base, 0f, 0f, imgPaint);

                Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                strokePaint.setStyle(Paint.Style.STROKE);
                strokePaint.setStrokeCap(Paint.Cap.ROUND);
                strokePaint.setStrokeJoin(Paint.Join.ROUND);
                int minDim = Math.min(w, h);
                for (BrushStroke s : strokesCopy) {
                    strokePaint.setColor(s.color);
                    strokePaint.setStrokeWidth(Math.max(4f, minDim * s.widthFraction));
                    c.drawPath(s.path, strokePaint);
                }

                File outDir = sourceFile.getParentFile();
                if (outDir == null || !outDir.canWrite()) {
                    outDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                }
                if (!outDir.exists()) outDir.mkdirs();

                String baseName = sourceFile.getName().replaceAll("\\.[^.]+$", "");
                File outFile = StorageUtils.uniqueDestinationFile(outDir, baseName + "_ערוך.jpg");
                try (FileOutputStream fos = new FileOutputStream(outFile)) {
                    outBitmap.compress(Bitmap.CompressFormat.JPEG, 94, fos);
                }
                outBitmap.recycle();

                runOnUiThread(() -> {
                    lastSavedFile = outFile;
                    btnShareSaved.setVisibility(View.VISIBLE);
                    Toast.makeText(this, "נשמר בהצלחה: " + outFile.getName(), Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "שגיאה בשמירה: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void shareSavedFile() {
        if (lastSavedFile != null && lastSavedFile.exists()) {
            try {
                Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", lastSavedFile);
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("image/*");
                share.putExtra(Intent.EXTRA_STREAM, uri);
                share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(share, "שיתוף תמונה ערוכה"));
            } catch (Exception e) {
                Toast.makeText(this, "שגיאה בשיתוף: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private int dp(int v) {
        return ThemeHelper.dpScaled(this, v);
    }

    private class EditorCanvasView extends AppCompatImageView {
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix invMatrix = new Matrix();
        private final float[] ptBuf = new float[2];
        private Path activePath = null;

        EditorCanvasView() {
            super(ProImageEditorActivity.this);
            setScaleType(ScaleType.FIT_CENTER);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeCap(Paint.Cap.ROUND);
            strokePaint.setStrokeJoin(Paint.Join.ROUND);
        }

        void updateColorFilter() {
            setColorFilter(new ColorMatrixColorFilter(
                    buildColorMatrix(presetFilter, brightnessOffset, contrastFactor, saturationFactor)));
            invalidate();
        }

        private float[] mapViewToBitmap(float vx, float vy) {
            ptBuf[0] = vx;
            ptBuf[1] = vy;
            if (getImageMatrix().invert(invMatrix)) {
                invMatrix.mapPoints(ptBuf);
            }
            if (transformedBitmap != null) {
                ptBuf[0] = Math.max(0f, Math.min(transformedBitmap.getWidth(), ptBuf[0]));
                ptBuf[1] = Math.max(0f, Math.min(transformedBitmap.getHeight(), ptBuf[1]));
            }
            return ptBuf;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (transformedBitmap == null || strokes.isEmpty()) return;
            canvas.save();
            canvas.concat(getImageMatrix());
            int minDim = Math.min(transformedBitmap.getWidth(), transformedBitmap.getHeight());
            for (BrushStroke s : strokes) {
                strokePaint.setColor(s.color);
                strokePaint.setStrokeWidth(Math.max(4f, minDim * s.widthFraction));
                canvas.drawPath(s.path, strokePaint);
            }
            canvas.restore();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (!drawModeEnabled) {
                if (event.getActionMasked() == MotionEvent.ACTION_UP && activeDrawer != DRAWER_NONE) {
                    setDrawerMode(DRAWER_NONE);
                }
                return true;
            }
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            float[] imgPt = mapViewToBitmap(event.getX(), event.getY());
            float bx = imgPt[0];
            float by = imgPt[1];
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    activePath = new Path();
                    activePath.moveTo(bx, by);
                    activePath.lineTo(bx + 0.1f, by + 0.1f);
                    strokes.add(new BrushStroke(activePath, activeBrushColor, BRUSH_WIDTH_FRACTIONS[activeBrushWidthIdx]));
                    invalidate();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (activePath != null) {
                        activePath.lineTo(bx, by);
                        invalidate();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (activePath != null) {
                        activePath = null;
                        pushHistoryState();
                    }
                    return true;
            }
            return super.onTouchEvent(event);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
        if (canvasView != null) {
            canvasView.setImageDrawable(null);
        }
        if (transformedBitmap != null && transformedBitmap != originalBitmap && !transformedBitmap.isRecycled()) {
            transformedBitmap.recycle();
        }
        transformedBitmap = null;
        if (originalBitmap != null && !originalBitmap.isRecycled()) {
            originalBitmap.recycle();
        }
        originalBitmap = null;
        if (filterThumbBitmap != null && !filterThumbBitmap.isRecycled()) {
            filterThumbBitmap.recycle();
        }
        filterThumbBitmap = null;
    }
}
