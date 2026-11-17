package com.aishhatorah.filemanagerpro;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Switch;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.graphics.ColorUtils;

import java.io.File;

/**
 * מערכת ערכות נושא עם עיצוב זכוכית מודרני, תמונת רקע מטושטשת, כותרות מעוגלות ואייקוני SVG נקיים.
 */
public class ThemeHelper {

    private static volatile String cachedWallKey = "";
    private static volatile Bitmap cachedBlurredWall = null;

    public static void invalidateWallpaperCache() {
        cachedWallKey = "";
        cachedBlurredWall = null;
    }

    public static boolean hasWallpaper(Context context) {
        if (context == null) return false;
        String path = PrefsManager.getHomeWallpaperPath(context);
        if (path == null || path.isEmpty()) return false;
        File f = new File(path);
        return f.exists() && f.isFile() && f.length() > 0;
    }

    public static int resolveEffectiveTheme(Context context, int theme) {
        if (theme != PrefsManager.THEME_SYSTEM) {
            return theme;
        }
        if (context == null) return PrefsManager.THEME_LIGHT;
        int uiMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return uiMode == Configuration.UI_MODE_NIGHT_YES ? PrefsManager.THEME_DARK : PrefsManager.THEME_LIGHT;
    }

    public static int getBgColor(int theme) {
        return getBgColor(null, theme);
    }

    public static int getBgColor(Context context, int theme) {
        if (theme == PrefsManager.THEME_SYSTEM && context != null) {
            boolean night = resolveEffectiveTheme(context, theme) == PrefsManager.THEME_DARK;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    return context.getColor(night ? android.R.color.system_neutral1_900 : android.R.color.system_neutral1_50);
                } catch (Throwable ignored) {
                }
            }
            return night ? Color.parseColor("#111418") : Color.parseColor("#F5F7FA");
        }
        if (theme == PrefsManager.THEME_DARK) return Color.parseColor("#111622");
        if (theme == PrefsManager.THEME_NEON) return Color.parseColor("#060B18");
        if (theme == PrefsManager.THEME_AMOLED) return Color.parseColor("#000000");
        if (theme == PrefsManager.THEME_OCEAN) return Color.parseColor("#0A192F");
        if (theme == PrefsManager.THEME_FOREST) return Color.parseColor("#0D1B17");
        if (theme == PrefsManager.THEME_ROSE) return Color.parseColor("#1F0E16");
        if (theme == PrefsManager.THEME_PURPLE) return Color.parseColor("#160F24");
        if (theme == PrefsManager.THEME_AMBER) return Color.parseColor("#1E160B");
        if (theme == PrefsManager.THEME_TEAL) return Color.parseColor("#091C1E");
        if (theme == PrefsManager.THEME_SLATE) return Color.parseColor("#EEF2F6");
        return Color.parseColor("#F5F7FA");
    }

    public static int getCardColor(int theme) {
        return getCardColor(null, theme);
    }

    public static int getCardColor(Context context, int theme) {
        if (theme == PrefsManager.THEME_SYSTEM && context != null) {
            boolean night = resolveEffectiveTheme(context, theme) == PrefsManager.THEME_DARK;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    return context.getColor(night ? android.R.color.system_neutral1_800 : android.R.color.system_neutral1_0);
                } catch (Throwable ignored) {
                }
            }
            return night ? Color.parseColor("#1C222B") : Color.parseColor("#FFFFFF");
        }
        if (theme == PrefsManager.THEME_DARK) return Color.parseColor("#1B2332");
        if (theme == PrefsManager.THEME_NEON) return Color.parseColor("#0D162B");
        if (theme == PrefsManager.THEME_AMOLED) return Color.parseColor("#0D0D10");
        if (theme == PrefsManager.THEME_OCEAN) return Color.parseColor("#112240");
        if (theme == PrefsManager.THEME_FOREST) return Color.parseColor("#142822");
        if (theme == PrefsManager.THEME_ROSE) return Color.parseColor("#2B1420");
        if (theme == PrefsManager.THEME_PURPLE) return Color.parseColor("#211735");
        if (theme == PrefsManager.THEME_AMBER) return Color.parseColor("#2A2012");
        if (theme == PrefsManager.THEME_TEAL) return Color.parseColor("#102A2D");
        if (theme == PrefsManager.THEME_SLATE) return Color.parseColor("#FFFFFF");
        return Color.parseColor("#FFFFFF");
    }

    public static int getDividerColor(Context context, int theme) {
        boolean isLight = !isDarkSurface(context, theme);
        return isLight ? Color.parseColor("#CBD5E1") : Color.parseColor("#243044");
    }

    public static int getPrimaryTextColor(int theme) {
        if (theme == PrefsManager.THEME_DARK) return Color.parseColor("#F1F5F9");
        if (theme == PrefsManager.THEME_NEON) return Color.parseColor("#E2F8FF");
        if (theme == PrefsManager.THEME_AMOLED) return Color.parseColor("#F4F4F5");
        if (theme == PrefsManager.THEME_OCEAN) return Color.parseColor("#E6F1FF");
        if (theme == PrefsManager.THEME_FOREST) return Color.parseColor("#ECFDF5");
        if (theme == PrefsManager.THEME_ROSE) return Color.parseColor("#FFF1F2");
        if (theme == PrefsManager.THEME_PURPLE) return Color.parseColor("#F5F3FF");
        if (theme == PrefsManager.THEME_AMBER) return Color.parseColor("#FFFBEB");
        if (theme == PrefsManager.THEME_TEAL) return Color.parseColor("#ECFEFF");
        if (theme == PrefsManager.THEME_SLATE) return Color.parseColor("#0F172A");
        return Color.parseColor("#0F172A");
    }

    public static int getPrimaryTextColor(Context context, int theme) {
        if (theme == PrefsManager.THEME_SYSTEM && context != null) {
            return resolveEffectiveTheme(context, theme) == PrefsManager.THEME_DARK
                    ? Color.parseColor("#F1F5F9")
                    : Color.parseColor("#0F172A");
        }
        return getPrimaryTextColor(theme);
    }

    public static int getSecondaryTextColor(int theme) {
        if (theme == PrefsManager.THEME_DARK) return Color.parseColor("#94A3B8");
        if (theme == PrefsManager.THEME_NEON) return Color.parseColor("#7DD3FC");
        if (theme == PrefsManager.THEME_AMOLED) return Color.parseColor("#A1A1AA");
        if (theme == PrefsManager.THEME_OCEAN) return Color.parseColor("#8892B0");
        if (theme == PrefsManager.THEME_FOREST) return Color.parseColor("#86EFAC");
        if (theme == PrefsManager.THEME_ROSE) return Color.parseColor("#FDA4AF");
        if (theme == PrefsManager.THEME_PURPLE) return Color.parseColor("#C4B5FD");
        if (theme == PrefsManager.THEME_AMBER) return Color.parseColor("#FCD34D");
        if (theme == PrefsManager.THEME_TEAL) return Color.parseColor("#67E8F9");
        if (theme == PrefsManager.THEME_SLATE) return Color.parseColor("#475569");
        return Color.parseColor("#64748B");
    }

    public static int getSecondaryTextColor(Context context, int theme) {
        if (theme == PrefsManager.THEME_SYSTEM && context != null) {
            return resolveEffectiveTheme(context, theme) == PrefsManager.THEME_DARK
                    ? Color.parseColor("#94A3B8")
                    : Color.parseColor("#64748B");
        }
        return getSecondaryTextColor(theme);
    }

    public static int getAccentColor(int theme) {
        if (theme == PrefsManager.THEME_DARK) return Color.parseColor("#3B82F6");
        if (theme == PrefsManager.THEME_NEON) return Color.parseColor("#22D3EE");
        if (theme == PrefsManager.THEME_AMOLED) return Color.parseColor("#60A5FA");
        if (theme == PrefsManager.THEME_OCEAN) return Color.parseColor("#38BDF8");
        if (theme == PrefsManager.THEME_FOREST) return Color.parseColor("#10B981");
        if (theme == PrefsManager.THEME_ROSE) return Color.parseColor("#F43F5E");
        if (theme == PrefsManager.THEME_PURPLE) return Color.parseColor("#A855F7");
        if (theme == PrefsManager.THEME_AMBER) return Color.parseColor("#F59E0B");
        if (theme == PrefsManager.THEME_TEAL) return Color.parseColor("#14B8A6");
        if (theme == PrefsManager.THEME_SLATE) return Color.parseColor("#334155");
        return Color.parseColor("#1E40AF");
    }

    public static int getAccentColor(Context context, int theme) {
        if (context != null) {
            int custom = PrefsManager.getCustomAccentColor(context);
            if (custom != 0) return custom;
            if (theme == PrefsManager.THEME_SYSTEM && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    return context.getColor(android.R.color.system_accent1_500);
                } catch (Throwable ignored) {
                }
            }
        }
        return getAccentColor(theme);
    }

    public static int getTopBarColor(int theme) {
        if (theme == PrefsManager.THEME_DARK) return Color.parseColor("#162033");
        if (theme == PrefsManager.THEME_NEON) return Color.parseColor("#091124");
        if (theme == PrefsManager.THEME_AMOLED) return Color.parseColor("#0A0A0C");
        if (theme == PrefsManager.THEME_OCEAN) return Color.parseColor("#0D213F");
        if (theme == PrefsManager.THEME_FOREST) return Color.parseColor("#112620");
        if (theme == PrefsManager.THEME_ROSE) return Color.parseColor("#29121D");
        if (theme == PrefsManager.THEME_PURPLE) return Color.parseColor("#1E1530");
        if (theme == PrefsManager.THEME_AMBER) return Color.parseColor("#261C0F");
        if (theme == PrefsManager.THEME_TEAL) return Color.parseColor("#0D2427");
        if (theme == PrefsManager.THEME_SLATE) return Color.parseColor("#FFFFFF");
        return Color.parseColor("#FFFFFF");
    }

    public static int getTopBarColor(Context context, int theme) {
        if (theme == PrefsManager.THEME_SYSTEM && context != null) {
            return getCardColor(context, theme);
        }
        return getTopBarColor(theme);
    }

    public static boolean isDarkSurface(Context context, int theme) {
        if (theme == PrefsManager.THEME_SYSTEM && context != null) {
            return resolveEffectiveTheme(context, theme) == PrefsManager.THEME_DARK;
        }
        return theme != PrefsManager.THEME_LIGHT && theme != PrefsManager.THEME_SLATE;
    }

    public static Drawable createCurvedTopBarBackground(Context context, int theme) {
        float d = context != null ? context.getResources().getDisplayMetrics().density : 2f;
        float r = 20f * d;
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(getTopBarColor(context, theme));
        gd.setCornerRadii(new float[]{0f, 0f, 0f, 0f, r, r, r, r});
        return gd;
    }

    public static void applyCurvedHeaderStyle(Context context, View topBar, int theme) {
        if (topBar == null) return;
        topBar.setBackground(createCurvedTopBarBackground(context, theme));
        if (context != null) {
            float d = context.getResources().getDisplayMetrics().density;
            topBar.setElevation(6f * d);
        }
    }

    public static int getGlassCardFillColor(Context context, int theme, boolean highOpacityForReading) {
        int baseCard = getCardColor(context, theme);
        boolean dark = isDarkSurface(context, theme);
        boolean wall = hasWallpaper(context);
        int alpha;
        if (highOpacityForReading) {
            alpha = wall ? (dark ? 222 : 230) : (dark ? 242 : 248);
        } else if (wall) {
            alpha = dark ? 156 : 168;
        } else {
            alpha = dark ? 216 : 230;
        }
        return ColorUtils.setAlphaComponent(baseCard, alpha);
    }

    public static GradientDrawable createGlassCardBackground(
            Context context, int theme, float cornerDp, boolean highOpacityForReading, boolean withGlow) {
        float d = context != null ? context.getResources().getDisplayMetrics().density : 2f;
        boolean dark = isDarkSurface(context, theme);
        int accent = getAccentColor(context, theme);
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(cornerDp * d);
        gd.setColor(getGlassCardFillColor(context, theme, highOpacityForReading));
        int rimBase = withGlow
                ? ColorUtils.blendARGB(dark ? Color.WHITE : accent, accent, 0.35f)
                : (dark ? Color.WHITE : Color.WHITE);
        int rimAlpha = withGlow ? (dark ? 82 : 120) : (dark ? 42 : 175);
        gd.setStroke(Math.max(1, Math.round(1.1f * d)), ColorUtils.setAlphaComponent(rimBase, rimAlpha));
        return gd;
    }

    public static void styleGlassCardView(Context context, CardView cardView, int theme, float cornerDp, boolean withGlow) {
        if (cardView == null) return;
        float d = context != null ? context.getResources().getDisplayMetrics().density : 2f;
        cardView.setRadius(cornerDp * d);
        cardView.setCardBackgroundColor(Color.TRANSPARENT);
        cardView.setCardElevation(0f);
        cardView.setBackground(createGlassCardBackground(context, theme, cornerDp, false, withGlow));
    }

    public static GradientDrawable createFloatingGlassDialogBackground(Context context, int theme) {
        float d = context != null ? context.getResources().getDisplayMetrics().density : 2f;
        boolean dark = isDarkSurface(context, theme);
        int baseCard = getCardColor(context, theme);
        int fill = ColorUtils.setAlphaComponent(baseCard, dark ? 230 : 236);
        int rim = dark ? Color.argb(66, 255, 255, 255) : Color.argb(190, 255, 255, 255);
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(24f * d);
        gd.setColor(fill);
        gd.setStroke(Math.max(2, Math.round(1.25f * d)), rim);
        return gd;
    }

    public static void applyWallpaperBackdrop(Context context, View targetView, int theme) {
        if (context == null || targetView == null) return;
        int bg = getBgColor(context, theme);
        String path = PrefsManager.getHomeWallpaperPath(context);
        if (path == null || path.isEmpty()) {
            targetView.setBackgroundColor(bg);
            return;
        }
        File f = new File(path);
        if (!f.exists() || !f.isFile()) {
            targetView.setBackgroundColor(bg);
            return;
        }
        int blurRadius = PrefsManager.getWallpaperBlurRadius(context);
        String key = path + ":" + f.lastModified() + ":b" + blurRadius;
        Bitmap blurred = cachedWallKey.equals(key) ? cachedBlurredWall : null;
        if (blurred == null) {
            blurred = decodeAndBlurWallpaper(path, blurRadius);
            if (blurred != null) {
                cachedWallKey = key;
                cachedBlurredWall = blurred;
            }
        }
        if (blurred == null) {
            targetView.setBackgroundColor(bg);
            return;
        }
        boolean dark = isDarkSurface(context, theme);
        int scrim = ColorUtils.setAlphaComponent(bg, dark ? 118 : 102);
        targetView.setBackground(new CenterCropBlurredDrawable(blurred, scrim));
    }

    private static Bitmap decodeAndBlurWallpaper(String path, int radius) {
        try {
            int maxOut = radius <= 4 ? 760 : 480;
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, bounds);
            int maxDim = Math.max(bounds.outWidth, bounds.outHeight);
            int sample = 1;
            while (maxDim / sample > maxOut) {
                sample *= 2;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap decoded = BitmapFactory.decodeFile(path, opts);
            if (decoded == null) return null;

            float scaleCap = radius <= 4 ? 640f : 340f;
            float scale = Math.min(1f, scaleCap / Math.max(1, Math.max(decoded.getWidth(), decoded.getHeight())));
            Bitmap scaled = scale < 0.99f
                    ? Bitmap.createScaledBitmap(decoded,
                            Math.max(1, Math.round(decoded.getWidth() * scale)),
                            Math.max(1, Math.round(decoded.getHeight() * scale)),
                            true)
                    : decoded.copy(Bitmap.Config.ARGB_8888, true);
            if (scaled != decoded) decoded.recycle();
            if (scaled == null) return null;
            if (radius < 1) return scaled;
            return fastStackBlur(scaled, radius);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Bitmap fastStackBlur(Bitmap src, int radius) {
        Bitmap bmp = src.isMutable() ? src : src.copy(Bitmap.Config.ARGB_8888, true);
        if (radius < 1) return bmp;
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        int[] pix = new int[w * h];
        bmp.getPixels(pix, 0, w, 0, 0, w, h);
        int wm = w - 1;
        int hm = h - 1;
        int wh = w * h;
        int div = radius + radius + 1;
        int[] r = new int[wh];
        int[] g = new int[wh];
        int[] b = new int[wh];
        int rsum, gsum, bsum, x, y, i, p, yp, yi, yw;
        int[] vmin = new int[Math.max(w, h)];
        int divsum = (div + 1) >> 1;
        divsum *= divsum;
        int[] dv = new int[256 * divsum];
        for (i = 0; i < 256 * divsum; i++) dv[i] = (i / divsum);
        yw = yi = 0;
        int[][] stack = new int[div][3];
        int stackpointer, stackstart, rbs, routsum, goutsum, boutsum, rinsum, ginsum, binsum;
        int r1 = radius + 1;

        for (y = 0; y < h; y++) {
            rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
            for (i = -radius; i <= radius; i++) {
                p = pix[yi + Math.min(wm, Math.max(i, 0))];
                int[] sir = stack[i + radius];
                sir[0] = (p & 0xff0000) >> 16;
                sir[1] = (p & 0x00ff00) >> 8;
                sir[2] = (p & 0x0000ff);
                rbs = r1 - Math.abs(i);
                rsum += sir[0] * rbs;
                gsum += sir[1] * rbs;
                bsum += sir[2] * rbs;
                if (i > 0) {
                    rinsum += sir[0];
                    ginsum += sir[1];
                    binsum += sir[2];
                } else {
                    routsum += sir[0];
                    goutsum += sir[1];
                    boutsum += sir[2];
                }
            }
            stackpointer = radius;
            for (x = 0; x < w; x++) {
                r[yi] = dv[rsum];
                g[yi] = dv[gsum];
                b[yi] = dv[bsum];
                rsum -= routsum;
                gsum -= goutsum;
                bsum -= boutsum;
                stackstart = stackpointer - radius + div;
                int[] sir = stack[stackstart % div];
                routsum -= sir[0];
                goutsum -= sir[1];
                boutsum -= sir[2];
                if (y == 0) vmin[x] = Math.min(x + radius + 1, wm);
                p = pix[yw + vmin[x]];
                sir[0] = (p & 0xff0000) >> 16;
                sir[1] = (p & 0x00ff00) >> 8;
                sir[2] = (p & 0x0000ff);
                rinsum += sir[0];
                ginsum += sir[1];
                binsum += sir[2];
                rsum += rinsum;
                gsum += ginsum;
                bsum += binsum;
                stackpointer = (stackpointer + 1) % div;
                sir = stack[stackpointer];
                routsum += sir[0];
                goutsum += sir[1];
                boutsum += sir[2];
                rinsum -= sir[0];
                ginsum -= sir[1];
                binsum -= sir[2];
                yi++;
            }
            yw += w;
        }
        for (x = 0; x < w; x++) {
            rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
            yp = -radius * w;
            for (i = -radius; i <= radius; i++) {
                yi = Math.max(0, yp) + x;
                int[] sir = stack[i + radius];
                sir[0] = r[yi];
                sir[1] = g[yi];
                sir[2] = b[yi];
                rbs = r1 - Math.abs(i);
                rsum += r[yi] * rbs;
                gsum += g[yi] * rbs;
                bsum += b[yi] * rbs;
                if (i > 0) {
                    rinsum += sir[0];
                    ginsum += sir[1];
                    binsum += sir[2];
                } else {
                    routsum += sir[0];
                    goutsum += sir[1];
                    boutsum += sir[2];
                }
                if (i < hm) yp += w;
            }
            yi = x;
            stackpointer = radius;
            for (y = 0; y < h; y++) {
                pix[yi] = (0xff000000) | (dv[rsum] << 16) | (dv[gsum] << 8) | dv[bsum];
                rsum -= routsum;
                gsum -= goutsum;
                bsum -= boutsum;
                stackstart = stackpointer - radius + div;
                int[] sir = stack[stackstart % div];
                routsum -= sir[0];
                goutsum -= sir[1];
                boutsum -= sir[2];
                if (x == 0) vmin[y] = Math.min(y + r1, hm) * w;
                p = x + vmin[y];
                sir[0] = r[p];
                sir[1] = g[p];
                sir[2] = b[p];
                rinsum += sir[0];
                ginsum += sir[1];
                binsum += sir[2];
                rsum += rinsum;
                gsum += ginsum;
                bsum += binsum;
                stackpointer = (stackpointer + 1) % div;
                sir = stack[stackpointer];
                routsum += sir[0];
                goutsum += sir[1];
                boutsum += sir[2];
                rinsum -= sir[0];
                ginsum -= sir[1];
                binsum -= sir[2];
                yi += w;
            }
        }
        bmp.setPixels(pix, 0, w, 0, 0, w, h);
        return bmp;
    }

    private static class CenterCropBlurredDrawable extends Drawable {
        private final Bitmap bitmap;
        private final int scrimColor;
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final Rect srcRect = new Rect();

        CenterCropBlurredDrawable(Bitmap bitmap, int scrimColor) {
            this.bitmap = bitmap;
            this.scrimColor = scrimColor;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect dst = getBounds();
            if (dst.width() <= 0 || dst.height() <= 0 || bitmap == null || bitmap.isRecycled()) return;
            float viewRatio = (float) dst.width() / (float) dst.height();
            float bmpRatio = (float) bitmap.getWidth() / (float) Math.max(1, bitmap.getHeight());
            int sx = 0, sy = 0, sw = bitmap.getWidth(), sh = bitmap.getHeight();
            if (bmpRatio > viewRatio) {
                sw = Math.max(1, Math.round(bitmap.getHeight() * viewRatio));
                sx = (bitmap.getWidth() - sw) / 2;
            } else if (bmpRatio < viewRatio) {
                sh = Math.max(1, Math.round(bitmap.getWidth() / viewRatio));
                sy = (bitmap.getHeight() - sh) / 2;
            }
            srcRect.set(sx, sy, sx + sw, sy + sh);
            canvas.drawBitmap(bitmap, srcRect, dst, paint);
            canvas.drawColor(scrimColor);
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(@Nullable ColorFilter colorFilter) { paint.setColorFilter(colorFilter); }
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }

    public static ImageButton createBorderlessSvgButton(
            Context context,
            int iconRes,
            int tintColor,
            String contentDescription,
            int sizeDp,
            int padDp,
            View.OnClickListener listener
    ) {
        ImageButton btn = new ImageButton(context);
        btn.setImageResource(iconRes);
        if (tintColor != 0) btn.setColorFilter(tintColor);
        btn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        btn.setContentDescription(contentDescription);
        try {
            TypedArray ta = context.obtainStyledAttributes(new int[]{android.R.attr.selectableItemBackgroundBorderless});
            Drawable ripple = ta.getDrawable(0);
            ta.recycle();
            btn.setBackground(ripple != null ? ripple : null);
        } catch (Throwable ignored) {
            btn.setBackgroundColor(Color.TRANSPARENT);
        }
        int padPx = dpScaled(context, padDp);
        btn.setPadding(padPx, padPx, padPx, padPx);
        int szPx = dpScaled(context, sizeDp);
        btn.setLayoutParams(new android.view.ViewGroup.LayoutParams(szPx, szPx));
        btn.setOnClickListener(listener);
        return btn;
    }

    public static void applySwitchTint(Context context, Switch sw, int theme) {
        if (sw == null) return;
        int accent = getAccentColor(context, theme);
        boolean dark = isDarkSurface(context, theme);
        int offThumb = dark ? Color.parseColor("#CBD5E1") : Color.parseColor("#F8FAFC");
        int offTrack = dark ? Color.parseColor("#475569") : Color.parseColor("#94A3B8");
        int onTrack = ColorUtils.setAlphaComponent(accent, 115);

        int[][] states = new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{}
        };
        ColorStateList thumbList = new ColorStateList(states, new int[]{accent, offThumb});
        ColorStateList trackList = new ColorStateList(states, new int[]{onTrack, offTrack});
        sw.setThumbTintList(thumbList);
        sw.setTrackTintList(trackList);
        sw.setThumbTintMode(PorterDuff.Mode.SRC_IN);
        sw.setTrackTintMode(PorterDuff.Mode.SRC_IN);
    }

    public static void applySeekBarTint(SeekBar sb, int accent) {
        if (sb == null) return;
        float d = sb.getResources().getDisplayMetrics().density;
        int trackH = Math.max(4, Math.round(8f * d));
        float cornerR = 999f;

        GradientDrawable trackBg = new GradientDrawable();
        trackBg.setCornerRadius(cornerR);
        trackBg.setColor(ColorUtils.setAlphaComponent(accent, 65));

        GradientDrawable trackFill = new GradientDrawable();
        trackFill.setCornerRadius(cornerR);
        trackFill.setColor(accent);
        ClipDrawable clipFill = new ClipDrawable(trackFill, Gravity.START, ClipDrawable.HORIZONTAL);

        LayerDrawable layers = new LayerDrawable(new Drawable[]{trackBg, clipFill});
        layers.setId(0, android.R.id.background);
        layers.setId(1, android.R.id.progress);
        layers.setLayerHeight(0, trackH);
        layers.setLayerHeight(1, trackH);
        layers.setLayerGravity(0, Gravity.CENTER_VERTICAL);
        layers.setLayerGravity(1, Gravity.CENTER_VERTICAL);

        sb.setProgressDrawable(layers);
        sb.setProgressTintList(null);
        sb.setProgressBackgroundTintList(null);
        sb.setSecondaryProgressTintList(null);

        GradientDrawable stickThumb = new GradientDrawable();
        stickThumb.setShape(GradientDrawable.RECTANGLE);
        stickThumb.setCornerRadius(999f);
        stickThumb.setColor(accent);
        int thumbW = Math.max(4, Math.round(5.5f * d));
        int thumbH = Math.max(18, Math.round(24f * d));
        stickThumb.setSize(thumbW, thumbH);

        sb.setThumb(stickThumb);
        sb.setThumbTintList(null);
        sb.setThumbOffset(thumbW / 2);
        sb.setSplitTrack(false);
        sb.setMinimumHeight(Math.round(34f * d));
        int hPad = Math.round(10f * d);
        sb.setPadding(hPad, Math.round(4f * d), hPad, Math.round(4f * d));
    }

    public static void styleHorizontalProgressBar(ProgressBar pb, int accent) {
        if (pb == null) return;
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(999f);
        bg.setColor(ColorUtils.setAlphaComponent(accent, 45));

        GradientDrawable fill = new GradientDrawable();
        fill.setCornerRadius(999f);
        fill.setColor(accent);
        ClipDrawable clip = new ClipDrawable(fill, Gravity.START, ClipDrawable.HORIZONTAL);

        LayerDrawable ld = new LayerDrawable(new Drawable[]{bg, clip});
        ld.setId(0, android.R.id.background);
        ld.setId(1, android.R.id.progress);
        pb.setProgressDrawable(ld);
        pb.setProgressTintList(null);
        pb.setProgressBackgroundTintList(null);
    }

    public static void applyWindowStatusBar(Activity activity, int theme) {
        Window window = activity.getWindow();
        if (window == null) return;
        int topColor = getTopBarColor(activity, theme);
        boolean dark = isDarkSurface(activity, theme);
        window.setStatusBarColor(topColor);
        View decor = window.getDecorView();
        if (decor != null) {
            int vis = decor.getSystemUiVisibility();
            if (!dark && ColorUtils.calculateLuminance(topColor) > 0.5) {
                vis |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            } else {
                vis &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            decor.setSystemUiVisibility(vis);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.setNavigationBarColor(getBgColor(activity, theme));
        }
    }

    public static float spScaled(Context context, float baseSp) {
        return baseSp * (context != null ? PrefsManager.getUiScale(context) : 1.0f);
    }

    public static int dpScaled(Context context, int baseDp) {
        if (context == null) return baseDp;
        float density = context.getResources().getDisplayMetrics().density;
        float scale = PrefsManager.getUiScale(context);
        return Math.round(baseDp * density * Math.max(0.85f, Math.min(1.25f, scale)));
    }

    public static String getThemeNameHe(int theme) {
        if (theme == PrefsManager.THEME_DARK) return "כהה";
        if (theme == PrefsManager.THEME_NEON) return "ניאון";
        if (theme == PrefsManager.THEME_AMOLED) return "שחור עמוק";
        if (theme == PrefsManager.THEME_OCEAN) return "אוקיינוס";
        if (theme == PrefsManager.THEME_FOREST) return "יער שקט";
        if (theme == PrefsManager.THEME_SYSTEM) return "מערכת";
        if (theme == PrefsManager.THEME_ROSE) return "ורד כהה";
        if (theme == PrefsManager.THEME_PURPLE) return "סגול מלכותי";
        if (theme == PrefsManager.THEME_AMBER) return "ענבר חם";
        if (theme == PrefsManager.THEME_TEAL) return "טורקיז עמוק";
        if (theme == PrefsManager.THEME_SLATE) return "צפחה בהיר";
        return "בהיר נקי";
    }
}
