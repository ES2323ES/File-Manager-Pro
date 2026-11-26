package com.aishhatorah.filemanagerpro.viewer;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.FileProvider;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.aishhatorah.filemanagerpro.AudioPlayerManager;
import com.aishhatorah.filemanagerpro.PrefsManager;
import com.aishhatorah.filemanagerpro.R;
import com.aishhatorah.filemanagerpro.RecycleBinManager;
import com.aishhatorah.filemanagerpro.StorageUtils;
import com.aishhatorah.filemanagerpro.ThemeHelper;
import com.aishhatorah.filemanagerpro.GlassAnchoredPopoverHelper;
import com.google.android.material.button.MaterialButton;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

/**
 * כלי עזר משותפים למסך המדיה והצפייה: סרגלים צפים Edge-to-Edge, שקיפות מערכת מלאה ללא פס תחתון לבן,
 * פענוח תמונות באיכות גבוהה, חלונית טעינה שקופה עם ביטול ואייקונים נקיים.
 */
public final class ViewerUiHelper {

    public static class DecodedPhoto {
        public Bitmap bitmap;
        public int origWidth;
        public int origHeight;
    }

    public interface SpeedSelectedCallback {
        void onSpeedChosen(float speed);
    }

    private ViewerUiHelper() {
    }

    public static int dp(Context ctx, float dpVal) {
        return Math.round(dpVal * ctx.getResources().getDisplayMetrics().density);
    }

    public static int resolveStatusBarHeightFallback(Context ctx) {
        int resId = ctx.getResources().getIdentifier("status_bar_height", "dimen", "android");
        int px = resId > 0 ? ctx.getResources().getDimensionPixelSize(resId) : 0;
        return Math.max(px, dp(ctx, 28));
    }

    /**
     * החלת Edge-to-Edge שקוף לחלוטין על חלונות מדיה/PDF כולל ביטול Contrast Scrim של אנדרואיד 10+
     * למניעת פס לבן בתחתית המסך.
     */
    @SuppressWarnings("deprecation")
    public static void applyMediaTransparentSystemBars(Activity activity) {
        Window win = activity.getWindow();
        if (win == null) return;
        WindowCompat.setDecorFitsSystemWindows(win, false);
        win.setStatusBarColor(Color.TRANSPARENT);
        win.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            win.setNavigationBarContrastEnforced(false);
            win.setStatusBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = win.getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            win.setAttributes(lp);
        }
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(win, win.getDecorView());
        if (controller != null) {
            controller.setAppearanceLightStatusBars(false);
            controller.setAppearanceLightNavigationBars(false);
        }
        View decor = win.getDecorView();
        if (decor != null) {
            int vis = decor.getSystemUiVisibility();
            vis &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vis &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            decor.setSystemUiVisibility(vis);
        }
    }

    public static void setSystemBarsFullscreen(Activity activity, boolean fullscreen) {
        Window window = activity.getWindow();
        if (window == null) return;
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
        if (controller != null) {
            if (fullscreen) {
                controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                controller.hide(WindowInsetsCompat.Type.systemBars());
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars());
            }
        }
    }

    public static GradientDrawable createFloatingGlassBarBackground(Context ctx, int cornerDp, int bgColor, int strokeColor) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(bgColor);
        gd.setCornerRadius(dp(ctx, cornerDp));
        gd.setStroke(Math.max(1, dp(ctx, 1)), strokeColor);
        return gd;
    }

    public static ImageButton createCleanBorderlessIconButton(Context ctx, int drawableRes, int sizePx, int tintColor) {
        ImageButton btn = new ImageButton(ctx);
        btn.setImageResource(drawableRes);
        btn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        btn.setColorFilter(tintColor);
        int pad = Math.max(dp(ctx, 6), Math.round(sizePx * 0.22f));
        btn.setPadding(pad, pad, pad, pad);
        TypedValue outValue = new TypedValue();
        if (ctx.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
                && outValue.resourceId != 0) {
            btn.setBackgroundResource(outValue.resourceId);
        } else {
            btn.setBackgroundColor(Color.TRANSPARENT);
        }
        btn.setLayoutParams(new LinearLayout.LayoutParams(sizePx, sizePx));
        return btn;
    }

    public static ImageButton createResponsiveRoundIconButton(Context ctx, int drawableRes, int sizePx, int marginPx,
                                                              boolean primaryFilled, int accent) {
        ImageButton btn = new ImageButton(ctx);
        btn.setImageResource(drawableRes);
        btn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int pad = Math.max(dp(ctx, 7), Math.round(sizePx * (primaryFilled ? 0.24f : 0.23f)));
        btn.setPadding(pad, pad, pad, pad);

        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        if (primaryFilled) {
            circle.setColor(accent);
            btn.setColorFilter(Color.WHITE);
        } else {
            circle.setColor(Color.argb(46, Color.red(accent), Color.green(accent), Color.blue(accent)));
            circle.setStroke(Math.max(2, dp(ctx, 1.2f)), accent);
            btn.setColorFilter(accent);
        }
        btn.setBackground(circle);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sizePx, sizePx);
        lp.setMargins(marginPx, 0, marginPx, 0);
        btn.setLayoutParams(lp);
        return btn;
    }

    public static MaterialButton createPencilEditorButton(Context ctx, String label, int accent, View.OnClickListener onClick) {
        MaterialButton btn = new MaterialButton(ctx);
        btn.setText(label);
        btn.setAllCaps(false);
        btn.setTextSize(12.5f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setBackgroundTintList(ColorStateList.valueOf(accent));
        btn.setTextColor(Color.WHITE);
        btn.setIconResource(R.drawable.ic_edit_pencil);
        btn.setIconTint(ColorStateList.valueOf(Color.WHITE));
        btn.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        btn.setIconPadding(dp(ctx, 6));
        btn.setCornerRadius(dp(ctx, 10));
        btn.setMinHeight(0);
        btn.setMinimumHeight(0);
        btn.setPadding(dp(ctx, 12), dp(ctx, 8), dp(ctx, 12), dp(ctx, 8));
        btn.setOnClickListener(onClick);
        return btn;
    }

    public static TextView createMarqueeTitleView(Context ctx, String text, int textColor, float textSizeSp) {
        AppCompatTextView tv = new AppCompatTextView(ctx) {
            @Override
            public boolean isFocused() {
                return true;
            }
        };
        tv.setText(text == null ? "" : text);
        tv.setTextSize(textSizeSp);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(textColor);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        tv.setMarqueeRepeatLimit(-1);
        tv.setHorizontallyScrolling(true);
        tv.setSelected(true);
        tv.setGravity(Gravity.CENTER);
        return tv;
    }

    public static TextView createCleanCenterTimeOverlay(Context ctx) {
        TextView tv = new TextView(ctx);
        tv.setTextSize(30f);
        tv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tv.setTextColor(Color.WHITE);
        tv.setTextDirection(View.TEXT_DIRECTION_LTR);
        tv.setGravity(Gravity.CENTER);
        tv.setBackground(null);
        tv.setShadowLayer(8f, 0f, 2f, Color.argb(210, 0, 0, 0));
        tv.setVisibility(View.GONE);
        tv.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        return tv;
    }

    public static TextView createSeekOverlayBadge(Context ctx) {
        int theme = PrefsManager.getThemeMode(ctx);
        int accent = ThemeHelper.getAccentColor(ctx, theme);
        TextView badge = new TextView(ctx);
        badge.setTextColor(Color.WHITE);
        badge.setTextSize(15f);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(ctx, 16), dp(ctx, 9), dp(ctx, 16), dp(ctx, 9));
        GradientDrawable glassPill = new GradientDrawable();
        glassPill.setCornerRadius(dp(ctx, 16));
        glassPill.setColor(Color.argb(198, 12, 18, 30));
        glassPill.setStroke(Math.max(1, dp(ctx, 1.1f)),
                ColorUtils.setAlphaComponent(ColorUtils.blendARGB(Color.WHITE, accent, 0.45f), 115));
        badge.setBackground(glassPill);
        badge.setElevation(dp(ctx, 6));
        badge.setVisibility(View.GONE);
        return badge;
    }

    public static void flashSeekBadge(Context ctx, Handler uiHandler, TextView badge, boolean forwardRight) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                (forwardRight ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL
        );
        lp.leftMargin = dp(ctx, 20);
        lp.rightMargin = dp(ctx, 20);
        badge.setLayoutParams(lp);
        badge.setText(forwardRight ? "\u200E+10 ש׳" : "\u200E-10 ש׳");
        badge.animate().cancel();
        badge.setAlpha(1f);
        badge.setVisibility(View.VISIBLE);
        if (PrefsManager.isReduceAnimations(ctx)) {
            badge.setScaleX(1f);
            badge.setScaleY(1f);
            uiHandler.postDelayed(() -> badge.setVisibility(View.GONE), 500);
        } else {
            badge.setScaleX(0.85f);
            badge.setScaleY(0.85f);
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
    }

    public static FrameLayout createDocumentLoadingOverlay(Context ctx, int theme, int accent, int textPrimary, Runnable onCancel) {
        int bgColor = ThemeHelper.getBgColor(ctx, theme);
        int cardColor = ThemeHelper.getCardColor(ctx, theme);
        boolean dark = ThemeHelper.isDarkSurface(ctx, theme);

        FrameLayout overlay = new FrameLayout(ctx);
        overlay.setBackgroundColor(ColorUtils.setAlphaComponent(bgColor, 212));
        overlay.setClickable(true);

        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);

        ProgressBar spinner = new ProgressBar(ctx);
        spinner.setIndeterminate(true);
        spinner.setIndeterminateTintList(ColorStateList.valueOf(accent));
        col.addView(spinner, new LinearLayout.LayoutParams(dp(ctx, 44), dp(ctx, 44)));

        TextView btnCancel = new TextView(ctx);
        btnCancel.setText("ביטול");
        btnCancel.setTextColor(textPrimary);
        btnCancel.setTextSize(13.5f);
        btnCancel.setTypeface(Typeface.DEFAULT_BOLD);
        btnCancel.setGravity(Gravity.CENTER);
        btnCancel.setPadding(dp(ctx, 24), dp(ctx, 9), dp(ctx, 24), dp(ctx, 9));
        GradientDrawable cancelBg = new GradientDrawable();
        cancelBg.setCornerRadius(dp(ctx, 999));
        cancelBg.setColor(ColorUtils.setAlphaComponent(cardColor, dark ? 175 : 205));
        cancelBg.setStroke(Math.max(1, dp(ctx, 1)),
                ColorUtils.setAlphaComponent(dark ? Color.WHITE : accent, dark ? 56 : 86));
        btnCancel.setBackground(cancelBg);
        btnCancel.setElevation(dp(ctx, 4));
        btnCancel.setOnClickListener(v -> {
            if (onCancel != null) onCancel.run();
        });
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cLp.topMargin = dp(ctx, 18);
        col.addView(btnCancel, cLp);

        overlay.addView(col, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        return overlay;
    }

    public static LinearLayout createSurfaceCard(Context ctx, int cardColor) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(ctx, 14), dp(ctx, 14), dp(ctx, 14), dp(ctx, 14));
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(ctx, 18));
        shape.setColor(cardColor);
        card.setBackground(shape);
        return card;
    }

    public static void showSpeedPickerDialog(AppCompatActivity activity, View anchor, String title,
                                             float current, SpeedSelectedCallback cb) {
        final float[] values = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f};
        List<GlassAnchoredPopoverHelper.OptionItem> items = new ArrayList<>();
        for (float v : values) {
            boolean selected = Math.abs(v - current) < 0.02f;
            String label = v == 1.0f
                    ? "1.0x (רגיל)"
                    : String.format(Locale.US, "%.2fx", v).replaceAll("0x$", "x");
            items.add(GlassAnchoredPopoverHelper.OptionItem.selectable(
                    label,
                    R.drawable.ic_speed,
                    selected,
                    () -> cb.onSpeedChosen(v)
            ));
        }
        GlassAnchoredPopoverHelper.showMenu(activity, anchor, title, null, items, false);
    }

    public static DecodedPhoto decodeUltraClearPhoto(File file) {
        DecodedPhoto out = new DecodedPhoto();
        final int MAX_EDGE = 3072;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            out.origWidth = Math.max(0, bounds.outWidth);
            out.origHeight = Math.max(0, bounds.outHeight);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    ImageDecoder.Source src = ImageDecoder.createSource(file);
                    out.bitmap = ImageDecoder.decodeBitmap(src, (decoder, info, s) -> {
                        decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                        decoder.setMemorySizePolicy(ImageDecoder.MEMORY_POLICY_LOW_RAM);
                        int w = info.getSize().getWidth();
                        int h = info.getSize().getHeight();
                        out.origWidth = w;
                        out.origHeight = h;
                        if (w > MAX_EDGE || h > MAX_EDGE) {
                            float ratio = Math.min((float) MAX_EDGE / w, (float) MAX_EDGE / h);
                            decoder.setTargetSize(Math.max(1, Math.round(w * ratio)),
                                    Math.max(1, Math.round(h * ratio)));
                        }
                    });
                    if (out.bitmap != null) return out;
                } catch (OutOfMemoryError oom) {
                    System.gc();
                } catch (Exception ignored) {
                }
            }

            if (out.origWidth > 0 && out.origHeight > 0) {
                int sample = 1;
                while (out.origWidth / sample > MAX_EDGE || out.origHeight / sample > MAX_EDGE) {
                    sample *= 2;
                }
                for (int attempt = 0; attempt < 3 && out.bitmap == null; attempt++) {
                    try {
                        BitmapFactory.Options loadOpts = new BitmapFactory.Options();
                        loadOpts.inSampleSize = sample;
                        loadOpts.inPreferredConfig = Bitmap.Config.ARGB_8888;
                        loadOpts.inScaled = false;
                        loadOpts.inDither = false;
                        out.bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), loadOpts);
                    } catch (OutOfMemoryError oom) {
                        System.gc();
                        sample *= 2;
                    }
                }
            }
        } catch (OutOfMemoryError oom) {
            System.gc();
            try {
                BitmapFactory.Options fallback = new BitmapFactory.Options();
                fallback.inSampleSize = 4;
                fallback.inPreferredConfig = Bitmap.Config.RGB_565;
                out.bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), fallback);
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    public static byte[] readFileBytesForViewer(File file, boolean gunzip) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (InputStream raw = new FileInputStream(file);
             InputStream in = gunzip ? new GZIPInputStream(raw) : raw) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1 && bos.size() < 8 * 1024 * 1024) {
                bos.write(buf, 0, r);
            }
        }
        return bos.toByteArray();
    }

    public static String formatClock(int ms) {
        int sec = Math.max(0, ms / 1000);
        return String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60);
    }

    public static int resolveRepeatIcon(int mode) {
        if (mode == AudioPlayerManager.REPEAT_ONE) return R.drawable.ic_repeat_one;
        if (mode == AudioPlayerManager.REPEAT_FOLDER) return R.drawable.ic_repeat_all;
        return R.drawable.ic_repeat_off;
    }

    public static boolean endsWithAny(String name, String... suffixes) {
        for (String s : suffixes) {
            if (name.endsWith(s)) return true;
        }
        return false;
    }

    public static void shareFile(Context ctx, File targetFile) {
        if (targetFile == null) return;
        try {
            Uri uri = FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", targetFile);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("*/*");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            ctx.startActivity(Intent.createChooser(share, "שיתוף קובץ"));
        } catch (Exception e) {
            Toast.makeText(ctx, "שגיאה בשיתוף הקובץ: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    public static boolean deleteViewedFile(Context ctx, File targetFile) {
        return PrefsManager.isUseRecycleBin(ctx)
                ? RecycleBinManager.moveToTrash(ctx, targetFile)
                : StorageUtils.deleteRecursively(targetFile);
    }
}
