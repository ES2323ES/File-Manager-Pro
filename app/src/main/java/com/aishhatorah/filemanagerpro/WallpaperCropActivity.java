package com.aishhatorah.filemanagerpro;

import android.app.WallpaperManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String path = getIntent().getStringExtra(EXTRA_IMAGE_PATH);
        if (path == null) { finish(); return; }
        File f = new File(path);
        if (!f.exists()) { finish(); return; }

        sourceBitmap = BitmapFactory.decodeFile(f.getAbsolutePath());
        if (sourceBitmap == null) {
            Toast.makeText(this, "לא ניתן לטעון תמונה זו כטפט", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        int theme = PrefsManager.getThemeMode(this);
        ThemeHelper.applyWindowStatusBar(this, theme);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(Color.BLACK);

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(Color.argb(190, 15, 23, 42));
        topBar.setPadding(20, 16, 20, 16);

        Button btnCancel = ThemedDialogHelper.createThemedButton(this, "ביטול", theme, false, false);
        btnCancel.setOnClickListener(v -> finish());
        topBar.addView(btnCancel);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("חיתוך והגדרת טפט (גרור והגדל למסגור)");
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(14f);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setGravity(Gravity.CENTER);
        topBar.addView(tvTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(topBar);

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

        root.addView(cropViewport, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bottomRow = new LinearLayout(this);
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setPadding(16, 18, 16, 22);
        bottomRow.setBackgroundColor(Color.argb(200, 15, 23, 42));

        bottomRow.addView(makeApplyBtn("מסך בית", WallpaperManager.FLAG_SYSTEM, theme));
        bottomRow.addView(makeApplyBtn("מסך נעילה", WallpaperManager.FLAG_LOCK, theme));
        bottomRow.addView(makeApplyBtn("שניהם", WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK, theme));
        root.addView(bottomRow);

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

    private Button makeApplyBtn(String label, int flags, int theme) {
        Button b = ThemedDialogHelper.createThemedButton(this, label, theme, flags == (WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK), false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginEnd(8);
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sourceBitmap != null && !sourceBitmap.isRecycled()) {
            sourceBitmap.recycle();
        }
    }
}
