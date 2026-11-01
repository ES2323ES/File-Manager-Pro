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
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * סעיף טו: עורך תמונות מקצועי מובנה באפליקציה
 * כולל סיבוב, היפוך, חיתוך יחס תצוגה, בהירות/ניגודיות/רוויה, פילטרים, ציור במכחול ושמירה/שיתוף.
 */
public class ProImageEditorActivity extends AppCompatActivity {

    public static final String EXTRA_IMAGE_PATH = "extra_image_path";

    private static class BrushStroke {
        final Path path;
        final int color;

        BrushStroke(Path path, int color) {
            this.path = path;
            this.color = color;
        }
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<BrushStroke> strokes = new ArrayList<>();

    private File sourceFile;
    private Bitmap originalBitmap;
    private Bitmap transformedBitmap;
    private EditorCanvasView canvasView;

    private int rotationDeg = 0;
    private boolean flipH = false;
    private boolean flipV = false;
    private int cropMode = 0; // 0 = מקורי, 1 = ריבועי 1:1, 2 = קולנועי 16:9
    private int presetFilter = 0; // 0 = מקורי, 1 = חי, 2 = חם, 3 = קריר, 4 = שחור-לבן

    private float brightnessOffset = 0f; // -80 .. +80
    private float contrastFactor = 1f;   // 0.5 .. 1.8
    private float saturationFactor = 1f; // 0.0 .. 2.0

    private boolean drawModeEnabled = false;
    private int activeBrushColor = Color.parseColor("#EF4444");
    private File lastSavedFile = null;
    private Button btnShareSaved;

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

        // סרגל עליון
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(ThemeHelper.getTopBarColor(theme));
        topBar.setPadding(24, 20, 24, 20);

        Button btnBack = ThemedDialogHelper.createThemedButton(this, "סגור", theme, false, false);
        btnBack.setOnClickListener(v -> finish());
        topBar.addView(btnBack);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("עורך תמונות מקצועי");
        tvTitle.setTextSize(16.5f);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setTextColor(textPrimary);
        tvTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tLp.setMarginStart(14);
        tLp.setMarginEnd(14);
        topBar.addView(tvTitle, tLp);

        Button btnSave = ThemedDialogHelper.createThemedButton(this, "שמור עותק", theme, true, false);
        btnSave.setOnClickListener(v -> saveEditedImage());
        topBar.addView(btnSave);

        root.addView(topBar);

        // משטח התצוגה של התמונה הנערכת
        FrameLayout previewHolder = new FrameLayout(this);
        previewHolder.setBackgroundColor(Color.parseColor("#0B1120"));
        canvasView = new EditorCanvasView();
        previewHolder.addView(canvasView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        root.addView(previewHolder, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // פאנל כלי העריכה התחתון
        ScrollView toolsScroll = new ScrollView(this);
        LinearLayout toolsBody = new LinearLayout(this);
        toolsBody.setOrientation(LinearLayout.VERTICAL);
        toolsBody.setPadding(28, 22, 28, 32);
        toolsBody.setBackgroundColor(cardColor);

        // שורת פעולות גיאומטריות וציור
        HorizontalScrollView actionsScroll = new HorizontalScrollView(this);
        actionsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);

        addToolChip(actionRow, "סיבוב 90°", theme, () -> {
            rotationDeg = (rotationDeg + 90) % 360;
            rebuildTransformedBitmap();
        });
        addToolChip(actionRow, "היפוך אופקי", theme, () -> {
            flipH = !flipH;
            rebuildTransformedBitmap();
        });
        addToolChip(actionRow, "היפוך אנכי", theme, () -> {
            flipV = !flipV;
            rebuildTransformedBitmap();
        });
        addToolChip(actionRow, "חיתוך יחס (מקור/1:1/16:9)", theme, () -> {
            cropMode = (cropMode + 1) % 3;
            rebuildTransformedBitmap();
            String label = cropMode == 0 ? "יחס מקורי" : (cropMode == 1 ? "חיתוך ריבועי 1:1" : "חיתוך קולנועי 16:9");
            Toast.makeText(this, label, Toast.LENGTH_SHORT).show();
        });
        addToolChip(actionRow, "מכחול ציור (הפעל/כבה)", theme, () -> {
            drawModeEnabled = !drawModeEnabled;
            Toast.makeText(this, drawModeEnabled ? "מצב ציור במכחול פעיל — צייר על התמונה" : "מצב ציור כבוי", Toast.LENGTH_SHORT).show();
        });
        addToolChip(actionRow, "בטל קו אחרון", theme, () -> {
            if (!strokes.isEmpty()) {
                strokes.remove(strokes.size() - 1);
                canvasView.invalidate();
            }
        });
        addToolChip(actionRow, "איפוס הכל", theme, () -> {
            rotationDeg = 0;
            flipH = false;
            flipV = false;
            cropMode = 0;
            presetFilter = 0;
            brightnessOffset = 0f;
            contrastFactor = 1f;
            saturationFactor = 1f;
            strokes.clear();
            rebuildTransformedBitmap();
        });

        actionsScroll.addView(actionRow);
        toolsBody.addView(actionsScroll);

        // שורת פילטרים וצבעי מכחול
        HorizontalScrollView filterScroll = new HorizontalScrollView(this);
        filterScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout filterRow = new LinearLayout(this);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);
        filterRow.setPadding(0, 16, 0, 10);

        String[] filterNames = {"פילטר: מקורי", "חד וחי", "גוון חם", "גוון קריר", "שחור-לבן"};
        for (int i = 0; i < filterNames.length; i++) {
            final int fIdx = i;
            addToolChip(filterRow, filterNames[i], theme, () -> {
                presetFilter = fIdx;
                canvasView.updateColorFilter();
            });
        }

        int[] brushColors = {
                Color.parseColor("#EF4444"),
                Color.parseColor("#3B82F6"),
                Color.parseColor("#10B981"),
                Color.parseColor("#F59E0B"),
                Color.WHITE
        };
        for (int c : brushColors) {
            View dot = new View(this);
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(c);
            circle.setStroke(3, accent);
            dot.setBackground(circle);
            LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(72, 72);
            dLp.setMargins(10, 6, 10, 6);
            dot.setLayoutParams(dLp);
            dot.setOnClickListener(v -> {
                activeBrushColor = c;
                drawModeEnabled = true;
                Toast.makeText(this, "צבע מכחול נבחר", Toast.LENGTH_SHORT).show();
            });
            filterRow.addView(dot);
        }

        filterScroll.addView(filterRow);
        toolsBody.addView(filterScroll);

        // מכווני בהירות, ניגודיות ורוויה
        addAdjustmentSlider(toolsBody, "בהירות", 50, textPrimary, textSecondary, progress -> {
            brightnessOffset = (progress - 50) * 1.6f;
            canvasView.updateColorFilter();
        });
        addAdjustmentSlider(toolsBody, "ניגודיות", 50, textPrimary, textSecondary, progress -> {
            contrastFactor = 0.5f + (progress / 100f) * 1.3f;
            canvasView.updateColorFilter();
        });
        addAdjustmentSlider(toolsBody, "רוויית צבע", 50, textPrimary, textSecondary, progress -> {
            saturationFactor = (progress / 50f);
            canvasView.updateColorFilter();
        });

        btnShareSaved = ThemedDialogHelper.createThemedButton(this, "שיתוף התמונה הערוכה", theme, false, false);
        LinearLayout.LayoutParams shLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        shLp.topMargin = 14;
        btnShareSaved.setLayoutParams(shLp);
        btnShareSaved.setVisibility(View.GONE);
        btnShareSaved.setOnClickListener(v -> {
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
        });
        toolsBody.addView(btnShareSaved);

        toolsScroll.addView(toolsBody);
        root.addView(toolsScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 660));

        setContentView(root);
        loadInitialBitmap();
    }

    private interface SliderListener {
        void onValue(int progress);
    }

    private void addAdjustmentSlider(LinearLayout parent, String label, int initial, int textPrimary, int textSecondary, SliderListener cb) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 8, 0, 8);

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(textPrimary);
        tv.setTextSize(12.5f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(tv, new LinearLayout.LayoutParams(190, ViewGroup.LayoutParams.WRAP_CONTENT));

        SeekBar sb = new SeekBar(this);
        sb.setMax(100);
        sb.setProgress(initial);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) cb.onValue(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        row.addView(sb, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        parent.addView(row);
    }

    private void addToolChip(LinearLayout row, String text, int theme, Runnable onClick) {
        Button b = ThemedDialogHelper.createThemedButton(this, text, theme, false, false);
        b.setTextSize(12f);
        b.setPadding(26, 16, 26, 16);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(12);
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> onClick.run());
        row.addView(b);
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
            runOnUiThread(this::rebuildTransformedBitmap);
        });
    }

    private void rebuildTransformedBitmap() {
        if (originalBitmap == null) return;
        Bitmap src = originalBitmap;

        if (cropMode != 0) {
            int w = src.getWidth();
            int h = src.getHeight();
            int targetW = w;
            int targetH = h;
            if (cropMode == 1) {
                int side = Math.min(w, h);
                targetW = side;
                targetH = side;
            } else if (cropMode == 2) {
                if (w * 9 > h * 16) {
                    targetW = (h * 16) / 9;
                } else {
                    targetH = (w * 9) / 16;
                }
            }
            int x = Math.max(0, (w - targetW) / 2);
            int y = Math.max(0, (h - targetH) / 2);
            src = Bitmap.createBitmap(src, x, y, Math.max(1, targetW), Math.max(1, targetH));
        }

        Matrix m = new Matrix();
        if (rotationDeg != 0) {
            m.postRotate(rotationDeg);
        }
        m.postScale(flipH ? -1f : 1f, flipV ? -1f : 1f);

        transformedBitmap = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
        canvasView.setImageBitmap(transformedBitmap);
        canvasView.updateColorFilter();
        canvasView.invalidate();
    }

    private ColorMatrix buildActiveColorMatrix() {
        ColorMatrix combined = new ColorMatrix();

        ColorMatrix sat = new ColorMatrix();
        float effectiveSat = presetFilter == 4 ? 0f : (presetFilter == 1 ? saturationFactor * 1.35f : saturationFactor);
        sat.setSaturation(effectiveSat);
        combined.postConcat(sat);

        float c = presetFilter == 1 ? contrastFactor * 1.15f : contrastFactor;
        float translate = (-0.5f * c + 0.5f) * 255f + brightnessOffset;
        float rBoost = presetFilter == 2 ? 18f : (presetFilter == 3 ? -10f : 0f);
        float bBoost = presetFilter == 3 ? 20f : (presetFilter == 2 ? -12f : 0f);

        ColorMatrix bc = new ColorMatrix(new float[]{
                c, 0, 0, 0, translate + rBoost,
                0, c, 0, 0, translate,
                0, 0, c, 0, translate + bBoost,
                0, 0, 0, 1, 0
        });
        combined.postConcat(bc);
        return combined;
    }

    private void saveEditedImage() {
        if (transformedBitmap == null) return;
        final Bitmap snapshot = Bitmap.createBitmap(
                Math.max(1, canvasView.getWidth()),
                Math.max(1, canvasView.getHeight()),
                Bitmap.Config.ARGB_8888
        );
        Canvas snapCanvas = new Canvas(snapshot);
        canvasView.draw(snapCanvas);

        executor.execute(() -> {
            try {
                File outDir = sourceFile.getParentFile();
                if (outDir == null || !outDir.canWrite()) {
                    outDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                }
                if (!outDir.exists()) outDir.mkdirs();

                String base = sourceFile.getName().replaceAll("\\.[^.]+$", "");
                File outFile = StorageUtils.uniqueDestinationFile(outDir, base + "_ערוך.jpg");
                try (FileOutputStream fos = new FileOutputStream(outFile)) {
                    snapshot.compress(Bitmap.CompressFormat.JPEG, 94, fos);
                }
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

    private class EditorCanvasView extends AppCompatImageView {
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Path activePath = null;

        EditorCanvasView() {
            super(ProImageEditorActivity.this);
            setScaleType(ScaleType.FIT_CENTER);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(10f);
            strokePaint.setStrokeCap(Paint.Cap.ROUND);
            strokePaint.setStrokeJoin(Paint.Join.ROUND);
        }

        void updateColorFilter() {
            setColorFilter(new ColorMatrixColorFilter(buildActiveColorMatrix()));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            for (BrushStroke s : strokes) {
                strokePaint.setColor(s.color);
                canvas.drawPath(s.path, strokePaint);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (!drawModeEnabled) return false;
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            float x = event.getX();
            float y = event.getY();
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    activePath = new Path();
                    activePath.moveTo(x, y);
                    strokes.add(new BrushStroke(activePath, activeBrushColor));
                    invalidate();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (activePath != null) {
                        activePath.lineTo(x, y);
                        invalidate();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    activePath = null;
                    return true;
            }
            return super.onTouchEvent(event);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
