package com.aishhatorah.filemanagerpro;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.File;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * סעיף ד: טוען אסינכרוני מהיר עם זיכרון מטמון המציג לכל קובץ את האייקון האמיתי שלו:
 * - שירים ואודיו: עטיפת האלבום/האייקון המוטמע בתוך קובץ השמע (MediaMetadataRetriever)
 * - תמונות: תמונה ממוזערת אמיתית של התמונה עצמה
 * - סירטונים: פריים תצוגה מקדימה אמיתי מתוך הסרטון
 * - אפליקציות (APK): האייקון הרשמי של אותה אפליקציה מתוך PackageManager
 */
public class FileThumbnailLoader {

    private static final ExecutorService THUMB_EXECUTOR = Executors.newFixedThreadPool(4);
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private static final LruCache<String, Bitmap> BITMAP_CACHE = new LruCache<String, Bitmap>(
            (int) (Runtime.getRuntime().maxMemory() / 1024 / 8)) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return Math.max(1, value.getByteCount() / 1024);
        }
    };

    // מונע סריקה חוזרת לקבצים שאין בהם עטיפה פנימית (למשל שיר ללא תמונה מוטמעת)
    private static final Set<String> NO_EMBEDDED_ICON_KEYS = ConcurrentHashMap.newKeySet();

    public static void bindFileIcon(Context context, File file, ImageView targetView) {
        if (file == null || targetView == null) return;

        if (file.isDirectory()) {
            targetView.setTag(null);
            targetView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            targetView.setImageResource(R.drawable.ic_folder);
            return;
        }

        final String nameLower = file.getName().toLowerCase(Locale.ROOT);
        final int fallbackRes = resolveFallbackDrawable(nameLower);
        final String cacheKey = file.getAbsolutePath() + "@" + file.lastModified() + "@" + file.length();

        targetView.setTag(cacheKey);

        if (!PrefsManager.isShowThumbnails(context)) {
            targetView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            targetView.setImageResource(fallbackRes);
            return;
        }

        Bitmap cached = BITMAP_CACHE.get(cacheKey);
        if (cached != null && !cached.isRecycled()) {
            targetView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            targetView.setImageBitmap(cached);
            return;
        }

        targetView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        targetView.setImageResource(fallbackRes);

        if (NO_EMBEDDED_ICON_KEYS.contains(cacheKey)) {
            return;
        }

        if (!supportsRealIconExtraction(nameLower)) {
            return;
        }

        final Context appCtx = context.getApplicationContext();
        THUMB_EXECUTOR.execute(() -> {
            Bitmap extracted = extractRealFileBitmap(appCtx, file, nameLower, 144);
            if (extracted != null) {
                Bitmap rounded = createRoundedSquareBitmap(extracted, 144, 24f);
                BITMAP_CACHE.put(cacheKey, rounded);
                MAIN_HANDLER.post(() -> {
                    if (cacheKey.equals(targetView.getTag())) {
                        targetView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        targetView.setImageBitmap(rounded);
                    }
                });
            } else {
                NO_EMBEDDED_ICON_KEYS.add(cacheKey);
            }
        });
    }

    public static Bitmap extractAudioEmbeddedArt(File audioFile, int targetSizePx) {
        if (audioFile == null || !audioFile.exists()) return null;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(audioFile.getAbsolutePath());
            byte[] artBytes = retriever.getEmbeddedPicture();
            if (artBytes != null && artBytes.length > 0) {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(artBytes, 0, artBytes.length, bounds);
                int sample = 1;
                while (bounds.outWidth / sample > targetSizePx * 2 || bounds.outHeight / sample > targetSizePx * 2) {
                    sample *= 2;
                }
                BitmapFactory.Options decodeOpts = new BitmapFactory.Options();
                decodeOpts.inSampleSize = sample;
                return BitmapFactory.decodeByteArray(artBytes, 0, artBytes.length, decodeOpts);
            }
        } catch (Exception ignored) {
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static Bitmap extractRealFileBitmap(Context context, File file, String lower, int sizePx) {
        String path = file.getAbsolutePath();
        try {
            if (endsWithAny(lower, ".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp", ".heic")) {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(path, bounds);
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
                int sample = 1;
                while (bounds.outWidth / sample > sizePx * 2 || bounds.outHeight / sample > sizePx * 2) {
                    sample *= 2;
                }
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = sample;
                return BitmapFactory.decodeFile(path, opts);
            }

            if (endsWithAny(lower, ".mp3", ".m4a", ".wav", ".aac", ".flac", ".ogg")) {
                return extractAudioEmbeddedArt(file, sizePx);
            }

            if (endsWithAny(lower, ".mp4", ".mkv", ".mov", ".avi", ".webm", ".3gp")) {
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                try {
                    retriever.setDataSource(path);
                    Bitmap frame = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                    if (frame == null) {
                        frame = retriever.getFrameAtTime();
                    }
                    if (frame != null) {
                        return ThumbnailUtils.extractThumbnail(frame, sizePx, sizePx);
                    }
                } catch (Exception ignored) {
                } finally {
                    try {
                        retriever.release();
                    } catch (Exception ignored) {
                    }
                }
                return ThumbnailUtils.createVideoThumbnail(path, MediaStore.Images.Thumbnails.MINI_KIND);
            }

            if (lower.endsWith(".apk")) {
                PackageManager pm = context.getPackageManager();
                PackageInfo pkgInfo = pm.getPackageArchiveInfo(path, 0);
                if (pkgInfo != null && pkgInfo.applicationInfo != null) {
                    ApplicationInfo appInfo = pkgInfo.applicationInfo;
                    appInfo.sourceDir = path;
                    appInfo.publicSourceDir = path;
                    Drawable iconDrawable = appInfo.loadIcon(pm);
                    if (iconDrawable != null) {
                        return drawableToBitmap(iconDrawable, sizePx);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Bitmap drawableToBitmap(Drawable drawable, int sizePx) {
        if (drawable instanceof BitmapDrawable) {
            Bitmap b = ((BitmapDrawable) drawable).getBitmap();
            if (b != null && !b.isRecycled()) {
                return Bitmap.createScaledBitmap(b, sizePx, sizePx, true);
            }
        }
        int w = drawable.getIntrinsicWidth() > 0 ? drawable.getIntrinsicWidth() : sizePx;
        int h = drawable.getIntrinsicHeight() > 0 ? drawable.getIntrinsicHeight() : sizePx;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bmp;
    }

    public static Bitmap createRoundedSquareBitmap(Bitmap src, int sizePx, float radiusPx) {
        Bitmap scaled = ThumbnailUtils.extractThumbnail(src, sizePx, sizePx);
        if (scaled == null) return src;
        Bitmap output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        RectF rect = new RectF(0f, 0f, sizePx, sizePx);
        canvas.drawRoundRect(rect, radiusPx, radiusPx, paint);
        return output;
    }

    public static int resolveFallbackDrawable(String lower) {
        if (endsWithAny(lower, ".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp", ".heic")) return R.drawable.ic_images;
        if (endsWithAny(lower, ".mp3", ".m4a", ".wav", ".aac", ".flac", ".ogg")) return R.drawable.ic_audio;
        if (endsWithAny(lower, ".mp4", ".mkv", ".mov", ".avi", ".webm", ".3gp")) return R.drawable.ic_video;
        if (endsWithAny(lower, ".apk", ".xapk", ".apks")) return R.drawable.ic_apps;
        if (endsWithAny(lower, ".pdf", ".doc", ".docx", ".txt", ".xls", ".xlsx", ".ppt", ".pptx", ".csv", ".rtf", ".md")) {
            return R.drawable.ic_documents;
        }
        return R.drawable.ic_file_generic;
    }

    private static boolean supportsRealIconExtraction(String lower) {
        return endsWithAny(lower,
                ".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp", ".heic",
                ".mp3", ".m4a", ".wav", ".aac", ".flac", ".ogg",
                ".mp4", ".mkv", ".mov", ".avi", ".webm", ".3gp",
                ".apk");
    }

    private static boolean endsWithAny(String name, String... exts) {
        for (String e : exts) {
            if (name.endsWith(e)) return true;
        }
        return false;
    }
}
