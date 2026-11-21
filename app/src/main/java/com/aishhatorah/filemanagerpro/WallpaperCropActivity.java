package com.aishhatorah.filemanagerpro;

import android.app.WallpaperManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.File;

/**
 * סעיף 42: חלון מקצועי לחיתוך, הגדלה ומיקום תמונה וקביעתה כטפט מסך בית / מסך נעילה / שניהם.
 */
public class WallpaperCropActivity extends AppCompatActivity {

    public static final String EXTRA_IMAGE_PATH = "extra_image_path";

    private Bitmap sourceBitmap;
    private ImageView previewImage;
    private FrameLayout cropViewport;
    private final Matrix matrix = new Matrix();
    private float lastX, lastY;

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String path = getIntent().getStringExtra(EXTRA_IMAGE_PATH);
        if (path == null) { finish(); return; }
        File f = new File(path);
        if (!f.exists()) { finish(); return; }

        sourceBitmap = decodeBoundedWallpaperBitmap(f, 2560);
        if (sourceBitmap == null) {
            Toast.makeText(this, "לא ניתן לטעון תמונה זו כטפט", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        int theme = PrefsManager.getThemeMode(this);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        Window w = getWindow();
        if (w != null) {
            w.setStatusBarColor(Color.TRANSPARENT);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                w.setNavigationBarColor(Color.TRANSPARENT);
            }
        }

        FrameLayout root = new FrameLayout(this);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(Color.BLACK);

        cropViewport = new FrameLayout(this);
        cropViewport.setBackgroundColor(Color.BLACK);
        previewImage = new ImageView(this);
        previewImage.setScaleType(ImageView.ScaleType.MATRIX);
        previewImage.setImageBitmap(sourceBitmap);
        cropViewport.addView(previewImage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ScaleGestureDetector scaleDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                matrix.postScale(d.getScaleFactor(), d.getScaleFactor(), d.getFocusX(), d.getFocusY());
                previewImage.setImageMatrix(matrix);
                return true;
            }
        });

        previewImage.setOnTouchListener((v, ev) -> {
            scaleDetector.onTouchEvent(ev);
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastX = ev.getX();
                    lastY = ev.getY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (!scaleDetector.isInProgress()) {
                        matrix.postTranslate(ev.getX() - lastX, ev.getY() - lastY);
                        previewImage.setImageMatrix(matrix);
                        lastX = ev.getX();
                        lastY = ev.getY();
                    }
                    break;
            }
            return true;
        });

        root.addView(cropViewport, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        float r = dp(22);

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setClickable(true);
        GradientDrawable topGlass = new GradientDrawable();
        topGlass.setCornerRadii(new float[]{0f, 0f, 0f, 0f, r, r, r, r});
        topGlass.setColor(Color.argb(182, 12, 18, 34));
        topGlass.setStroke(Math.max(1, dp(1)), Color.argb(42, 255, 255, 255));
        topBar.setBackground(topGlass);
        topBar.setPadding(dp(12), dp(14), dp(12), dp(12));

        ImageButton btnBack = ThemeHelper.createBorderlessSvgButton(
                this, R.drawable.ic_arrow_back, Color.WHITE, "חזרה", 40, 8, v -> finish());
        topBar.addView(btnBack);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("הגדרת טפט");
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(ThemeHelper.spScaled(this, 16f));
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleLp.setMarginStart(dp(6));
        topBar.addView(tvTitle, titleLp);

        ImageButton btnReset = ThemeHelper.createBorderlessSvgButton(
                this, R.drawable.ic_reset, Color.WHITE, "איפוס מסגור", 38, 8, v -> centerFitBitmap());
        topBar.addView(btnReset);

        root.addView(topBar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));

        LinearLayout bottomRow = new LinearLayout(this);
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        bottomRow.setClickable(true);
        bottomRow.setPadding(dp(12), dp(14), dp(12), dp(14));
        GradientDrawable bottomGlass = new GradientDrawable();
        bottomGlass.setCornerRadii(new float[]{r, r, r, r, 0f, 0f, 0f, 0f});
        bottomGlass.setColor(Color.argb(190, 12, 18, 34));
        bottomGlass.setStroke(Math.max(1, dp(1)), Color.argb(42, 255, 255, 255));
        bottomRow.setBackground(bottomGlass);

        bottomRow.addView(makeApplyBtn("מסך בית", WallpaperManager.FLAG_SYSTEM, theme, true));
        bottomRow.addView(makeApplyBtn("מסך נעילה", WallpaperManager.FLAG_LOCK, theme, true));
        bottomRow.addView(makeApplyBtn("שניהם", WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK, theme, false));
        root.addView(bottomRow, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            topBar.setPadding(dp(12) + sys.left, sys.top + dp(10), dp(12) + sys.right, dp(12));
            bottomRow.setPadding(dp(12) + sys.left, dp(14), dp(12) + sys.right, sys.bottom + dp(14));
            return insets;
        });

        setContentView(root);
        cropViewport.post(this::centerFitBitmap);
    }

    private void centerFitBitmap() {
        int vw = cropViewport.getWidth();
        int vh = cropViewport.getHeight();
        if (vw <= 0 || vh <= 0 || sourceBitmap == null) return;
        float scale = Math.max((float) vw / sourceBitmap.getWidth(), (float) vh / sourceBitmap.getHeight());
        float dx = (vw - sourceBitmap.getWidth() * scale) / 2f;
        float dy = (vh - sourceBitmap.getHeight() * scale) / 2f;
        matrix.reset();
        matrix.postScale(scale, scale);
        matrix.postTranslate(dx, dy);
        previewImage.setImageMatrix(matrix);
    }

    private Button makeApplyBtn(String label, int flags, int theme, boolean endMargin) {
        boolean primary = flags == (WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK);
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(ThemeHelper.spScaled(this, 13f));
        b.setTypeface(Typeface.DEFAULT_BOLD);
        int accent = ThemeHelper.getAccentColor(this, theme);
        b.setTextColor(primary ? ThemeHelper.getOnAccentTextColor(accent) : Color.WHITE);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(primary ? accent : Color.argb(46, 255, 255, 255));
        bg.setStroke(Math.max(1, dp(1)), primary ? accent : Color.argb(65, 255, 255, 255));
        b.setBackground(bg);
        b.setStateListAnimator(null);
        b.setElevation(0f);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(6), dp(11), dp(6), dp(11));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (endMargin) lp.setMarginEnd(dp(8));
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> applyWallpaper(flags));
        return b;
    }

    private void applyWallpaper(int flags) {
        try {
            DisplayMetrics dm = getResources().getDisplayMetrics();
            int vw = Math.max(1, cropViewport.getWidth());
            int vh = Math.max(1, cropViewport.getHeight());
            Bitmap out = Bitmap.createBitmap(vw, vh, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);
            c.drawColor(Color.BLACK);
            c.drawBitmap(sourceBitmap, matrix, null);

            WallpaperManager wm = WallpaperManager.getInstance(this);
            wm.setBitmap(out, null, true, flags);
            out.recycle();
            Toast.makeText(this, "הטפט עודכן בהצלחה!", Toast.LENGTH_SHORT).show();
            finish();
        } catch (Throwable t) {
            AppLogger.error(this, "Wallpaper", "Failed setting wallpaper", t);
            Toast.makeText(this, "שגיאה בקביעת טפט: " + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private Bitmap decodeBoundedWallpaperBitmap(File file, int maxDim) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            int sample = 1;
            int rawW = Math.max(1, bounds.outWidth);
            int rawH = Math.max(1, bounds.outHeight);
            while (rawW / sample > maxDim || rawH / sample > maxDim) {
                sample *= 2;
            }
            for (int attempt = 0; attempt < 3; attempt++) {
                try {
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inSampleSize = sample;
                    opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    return BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
                } catch (OutOfMemoryError oom) {
                    System.gc();
                    sample *= 2;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sourceBitmap != null && !sourceBitmap.isRecycled()) {
            sourceBitmap.recycle();
        }
    }
}
