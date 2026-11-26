package com.aishhatorah.filemanagerpro.viewer;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Typeface;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.util.Base64;
import android.util.LruCache;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.aishhatorah.filemanagerpro.CodeEditorActivity;
import com.aishhatorah.filemanagerpro.GifToolsHelper;
import com.aishhatorah.filemanagerpro.GlassSideDrawerHelper;
import com.aishhatorah.filemanagerpro.MediaFormatHelper;
import com.aishhatorah.filemanagerpro.PrefsManager;
import com.aishhatorah.filemanagerpro.ProImageEditorActivity;
import com.aishhatorah.filemanagerpro.R;
import com.aishhatorah.filemanagerpro.StorageUtils;
import com.aishhatorah.filemanagerpro.ThemeHelper;
import com.aishhatorah.filemanagerpro.WallpaperCropActivity;
import com.aishhatorah.filemanagerpro.ZoomableImageView;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * בקר צפייה מתקדם לתמונות, GIF מונפש ו-SVG:
 * - במה שחורה Edge-to-Edge מלאה ללא פס תחתון לבן
 * - סרגל עליון ותחתון צפים בזכוכית מעוגלת בכל 4 הפינות בדומה לנגן הווידאו
 * - זום בצביטה, דפדוף בלחיצה/החלקה, כפתורים צפים וכלי GIF/SVG/טפט
 */
public class ImageViewerController {

    private final AppCompatActivity activity;
    private final ViewerHost host;

    private final List<File> imagePlaylist = new ArrayList<>();
    private int currentImageIdx = 0;
    private int imageLoadGeneration = 0;
    private final LruCache<String, ViewerUiHelper.DecodedPhoto> photoCache =
            new LruCache<String, ViewerUiHelper.DecodedPhoto>(
                    (int) Math.max(4096, Math.min(32768, Runtime.getRuntime().maxMemory() / 1024L / 10L))) {
                @Override
                protected int sizeOf(String key, ViewerUiHelper.DecodedPhoto value) {
                    if (value == null || value.bitmap == null || value.bitmap.isRecycled()) return 1;
                    return Math.max(1, value.bitmap.getByteCount() / 1024);
                }
            };

    public ImageViewerController(ViewerHost host) {
        this.host = host;
        this.activity = host.getActivity();
    }

    public int getPlaylistSize() {
        return imagePlaylist.size();
    }

    public void render(int theme) {
        ViewerUiHelper.applyMediaTransparentSystemBars(activity);
        File initialFile = host.getTargetFile();
        buildImageFolderPlaylist(initialFile);

        FrameLayout stageRoot = new FrameLayout(activity);
        stageRoot.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        stageRoot.setBackgroundColor(Color.BLACK);

        FrameLayout imageViewport = new FrameLayout(activity);
        imageViewport.setBackgroundColor(Color.BLACK);
        stageRoot.addView(imageViewport, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final ZoomableImageView zoomView = new ZoomableImageView(activity);
        imageViewport.addView(zoomView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final WebView fallbackSvgWebView = new WebView(activity);
        fallbackSvgWebView.setBackgroundColor(Color.parseColor("#E2E8F0"));
        fallbackSvgWebView.setVisibility(View.GONE);
        WebSettings ws = fallbackSvgWebView.getSettings();
        ws.setSupportZoom(true);
        ws.setBuiltInZoomControls(true);
        ws.setDisplayZoomControls(false);
        ws.setUseWideViewPort(true);
        ws.setLoadWithOverviewMode(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        ws.setJavaScriptEnabled(false);
        GestureDetector webSingleTap = new GestureDetector(activity, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                host.toggleMediaControlsVisibility();
                return true;
            }
        });
        fallbackSvgWebView.setOnTouchListener((v, ev) -> {
            webSingleTap.onTouchEvent(ev);
            return false;
        });
        imageViewport.addView(fallbackSvgWebView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ImageButton btnFloatPrev = ViewerUiHelper.createCleanBorderlessIconButton(activity, R.drawable.ic_skip_prev, dp(42), Color.WHITE);
        GradientDrawable prevScrim = new GradientDrawable();
        prevScrim.setShape(GradientDrawable.OVAL);
        prevScrim.setColor(Color.argb(125, 8, 12, 22));
        prevScrim.setStroke(Math.max(1, dp(1)), Color.argb(75, 255, 255, 255));
        btnFloatPrev.setBackground(prevScrim);
        btnFloatPrev.setPadding(dp(9), dp(9), dp(9), dp(9));
        FrameLayout.LayoutParams prevFlp = new FrameLayout.LayoutParams(dp(42), dp(42), Gravity.LEFT | Gravity.CENTER_VERTICAL);
        prevFlp.leftMargin = dp(10);
        stageRoot.addView(btnFloatPrev, prevFlp);

        ImageButton btnFloatNext = ViewerUiHelper.createCleanBorderlessIconButton(activity, R.drawable.ic_skip_next, dp(42), Color.WHITE);
        GradientDrawable nextScrim = new GradientDrawable();
        nextScrim.setShape(GradientDrawable.OVAL);
        nextScrim.setColor(Color.argb(125, 8, 12, 22));
        nextScrim.setStroke(Math.max(1, dp(1)), Color.argb(75, 255, 255, 255));
        btnFloatNext.setBackground(nextScrim);
        btnFloatNext.setPadding(dp(9), dp(9), dp(9), dp(9));
        FrameLayout.LayoutParams nextFlp = new FrameLayout.LayoutParams(dp(42), dp(42), Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        nextFlp.rightMargin = dp(10);
        stageRoot.addView(btnFloatNext, nextFlp);

        int glassBgColor = ColorUtils.setAlphaComponent(
                ColorUtils.blendARGB(ThemeHelper.getTopBarColor(activity, theme), Color.parseColor("#0A0F1C"), 0.58f),
                194);
        int glassStroke = Color.argb(65, 255, 255, 255);

        // סרגל עליון צף ומעוגל ב-4 פינות (זהה לווידאו) עם שם קובץ ומונה תמונות
        LinearLayout topOverlay = new LinearLayout(activity);
        topOverlay.setOrientation(LinearLayout.HORIZONTAL);
        topOverlay.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        topOverlay.setGravity(Gravity.CENTER_VERTICAL);
        topOverlay.setClickable(true);
        topOverlay.setBackground(ViewerUiHelper.createFloatingGlassBarBackground(activity, 22, glassBgColor, glassStroke));
        topOverlay.setElevation(dp(8));
        topOverlay.setPadding(dp(10), dp(6), dp(10), dp(6));

        ImageButton btnImgBack = ViewerUiHelper.createCleanBorderlessIconButton(activity, R.drawable.ic_arrow_back, dp(42), Color.WHITE);
        btnImgBack.setContentDescription("חזרה");
        btnImgBack.setOnClickListener(v -> host.proceedCloseOrNavigateBack());
        topOverlay.addView(btnImgBack);

        LinearLayout centerTitleCol = new LinearLayout(activity);
        centerTitleCol.setOrientation(LinearLayout.VERTICAL);
        centerTitleCol.setGravity(Gravity.CENTER);

        String initialName = initialFile != null ? initialFile.getName() : "";
        TextView tvTitle = ViewerUiHelper.createMarqueeTitleView(activity, initialName, Color.WHITE,
                ThemeHelper.spScaled(activity, 14f));
        centerTitleCol.addView(tvTitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView tvCounter = new TextView(activity);
        tvCounter.setTextSize(11.5f);
        tvCounter.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tvCounter.setTextColor(Color.parseColor("#CBD5E1"));
        tvCounter.setTextDirection(View.TEXT_DIRECTION_LTR);
        tvCounter.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams cntLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cntLp.topMargin = dp(2);
        centerTitleCol.addView(tvCounter, cntLp);

        LinearLayout.LayoutParams titleColLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleColLp.setMarginStart(dp(8));
        titleColLp.setMarginEnd(dp(8));
        topOverlay.addView(centerTitleCol, titleColLp);

        ImageButton btnImgHamburger = ViewerUiHelper.createCleanBorderlessIconButton(activity, R.drawable.ic_menu_hamburger, dp(42), Color.WHITE);
        btnImgHamburger.setContentDescription("אפשרויות");
        btnImgHamburger.setOnClickListener(this::showImageOptionsDialog);
        topOverlay.addView(btnImgHamburger);

        host.setHeaderViews(tvTitle, tvCounter, initialName);

        final int statusFloor = ViewerUiHelper.resolveStatusBarHeightFallback(activity);
        FrameLayout.LayoutParams topOverlayLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        topOverlayLp.setMargins(dp(10), statusFloor + dp(8), dp(10), 0);
        stageRoot.addView(topOverlay, topOverlayLp);

        // סרגל פעולות תחתון צף לחלוטין בזכוכית מעוגלת בכל 4 הפינות
        LinearLayout bottomActionsRow = new LinearLayout(activity);
        bottomActionsRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomActionsRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        bottomActionsRow.setGravity(Gravity.CENTER);
        bottomActionsRow.setClickable(true);
        bottomActionsRow.setBackground(ViewerUiHelper.createFloatingGlassBarBackground(activity, 22, glassBgColor, glassStroke));
        bottomActionsRow.setElevation(dp(8));
        bottomActionsRow.setPadding(dp(14), dp(8), dp(14), dp(8));

        final ImageButton btnProImageEditor = ViewerUiHelper.createCleanBorderlessIconButton(activity, R.drawable.ic_edit_pencil, dp(44), Color.WHITE);
        btnProImageEditor.setContentDescription("עורך תמונות");
        btnProImageEditor.setOnClickListener(v -> {
            File tf = host.getTargetFile();
            if (tf == null) return;
            Intent i = new Intent(activity, ProImageEditorActivity.class);
            i.putExtra(ProImageEditorActivity.EXTRA_IMAGE_PATH, tf.getAbsolutePath());
            activity.startActivity(i);
        });
        LinearLayout.LayoutParams editIconLp = new LinearLayout.LayoutParams(dp(44), dp(44));
        editIconLp.setMarginStart(dp(14));
        editIconLp.setMarginEnd(dp(14));
        btnProImageEditor.setLayoutParams(editIconLp);

        final ImageButton btnShareBottom = ViewerUiHelper.createCleanBorderlessIconButton(activity, R.drawable.ic_share, dp(44), Color.WHITE);
        btnShareBottom.setContentDescription("שיתוף");
        btnShareBottom.setOnClickListener(v -> host.shareCurrentFile());
        LinearLayout.LayoutParams shareIconLp = new LinearLayout.LayoutParams(dp(44), dp(44));
        shareIconLp.setMarginStart(dp(14));
        shareIconLp.setMarginEnd(dp(14));
        btnShareBottom.setLayoutParams(shareIconLp);

        final ImageButton btnWallpaper = ViewerUiHelper.createCleanBorderlessIconButton(activity, R.drawable.ic_wallpaper, dp(44), Color.WHITE);
        btnWallpaper.setContentDescription("קבע כרקע");
        btnWallpaper.setOnClickListener(v -> {
            File tf = host.getTargetFile();
            if (tf == null) return;
            Intent wi = new Intent(activity, WallpaperCropActivity.class);
            wi.putExtra(WallpaperCropActivity.EXTRA_IMAGE_PATH, tf.getAbsolutePath());
            activity.startActivity(wi);
        });
        LinearLayout.LayoutParams wallIconLp = new LinearLayout.LayoutParams(dp(44), dp(44));
        wallIconLp.setMarginStart(dp(14));
        wallIconLp.setMarginEnd(dp(14));
        btnWallpaper.setLayoutParams(wallIconLp);

        bottomActionsRow.addView(btnProImageEditor);
        bottomActionsRow.addView(btnShareBottom);
        bottomActionsRow.addView(btnWallpaper);

        FrameLayout.LayoutParams bottomLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        bottomLp.setMargins(dp(10), 0, dp(10), dp(10));
        stageRoot.addView(bottomActionsRow, bottomLp);

        ViewCompat.setOnApplyWindowInsetsListener(stageRoot, (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            int effectiveTop = Math.max(sys.top, statusFloor);
            ViewGroup.MarginLayoutParams tLp = (ViewGroup.MarginLayoutParams) topOverlay.getLayoutParams();
            if (tLp != null) {
                tLp.leftMargin = sys.left + dp(10);
                tLp.rightMargin = sys.right + dp(10);
                tLp.topMargin = effectiveTop + dp(8);
                topOverlay.setLayoutParams(tLp);
            }
            ViewGroup.MarginLayoutParams bLp = (ViewGroup.MarginLayoutParams) bottomActionsRow.getLayoutParams();
            if (bLp != null) {
                bLp.leftMargin = sys.left + dp(10);
                bLp.rightMargin = sys.right + dp(10);
                bLp.bottomMargin = sys.bottom + dp(10);
                bottomActionsRow.setLayoutParams(bLp);
            }
            return insets;
        });

        host.setMediaControlsHidden(false);
        host.registerMediaChrome(true, false, false, stageRoot, imageViewport, topOverlay, bottomActionsRow,
                btnFloatPrev, btnFloatNext, null, null, zoomView);

        activity.setContentView(stageRoot);
        ViewCompat.requestApplyInsets(stageRoot);
        host.applyMediaImmersiveAndLayoutState();

        final Handler uiHandler = host.getUiHandler();

        final Runnable preloadNeighbors = () -> {
            if (imagePlaylist.size() <= 1) return;
            int[] offsets = new int[]{1, -1};
            for (int off : offsets) {
                int nIdx = (currentImageIdx + off + imagePlaylist.size()) % imagePlaylist.size();
                File neighbor = imagePlaylist.get(nIdx);
                if (neighbor.length() > 16L * 1024L * 1024L) continue;
                String nLow = neighbor.getName().toLowerCase(Locale.ROOT);
                if (MediaFormatHelper.isSvgFile(nLow) || MediaFormatHelper.isGifFile(nLow)) continue;
                String cacheKey = neighbor.getAbsolutePath() + ":" + neighbor.lastModified();
                if (photoCache.get(cacheKey) != null) continue;
                host.getExecutor().execute(() -> {
                    if (photoCache.get(cacheKey) != null) return;
                    ViewerUiHelper.DecodedPhoto decoded = ViewerUiHelper.decodeUltraClearPhoto(neighbor);
                    if (decoded != null && decoded.bitmap != null) {
                        photoCache.put(cacheKey, decoded);
                    }
                });
            }
        };

        final Runnable loadCurrentImageIntoStage = () -> {
            if (imagePlaylist.isEmpty()) return;
            final int gen = ++imageLoadGeneration;
            final File imgFile = imagePlaylist.get(currentImageIdx);
            host.setTargetFile(imgFile);
            host.updateMarqueeHeaderName(imgFile.getName());
            TextView counterView = host.getFolderTrackCounterView();
            if (counterView != null) {
                counterView.setVisibility(View.VISIBLE);
                counterView.setText((currentImageIdx + 1) + "/" + imagePlaylist.size());
            }

            String nameLow = imgFile.getName().toLowerCase(Locale.ROOT);
            boolean isSvg = MediaFormatHelper.isSvgFile(nameLow);
            if (isSvg) {
                zoomView.setVisibility(View.GONE);
                fallbackSvgWebView.setBackgroundColor(Color.parseColor("#E2E8F0"));
                fallbackSvgWebView.setVisibility(View.VISIBLE);
                fallbackSvgWebView.onResume();
                btnProImageEditor.setVisibility(View.GONE);
                btnWallpaper.setVisibility(View.GONE);
                final boolean compressedSvg = nameLow.endsWith(".svgz");
                host.getExecutor().execute(() -> {
                    try {
                        byte[] rawBytes = ViewerUiHelper.readFileBytesForViewer(imgFile, compressedSvg);
                        final String html = buildSvgViewerHtml(rawBytes);
                        uiHandler.post(() -> {
                            if (gen != imageLoadGeneration || activity.isFinishing()) return;
                            fallbackSvgWebView.loadDataWithBaseURL("https://svg.local/", html, "text/html", "UTF-8", null);
                            preloadNeighbors.run();
                        });
                    } catch (Exception e) {
                        uiHandler.post(() -> {
                            if (gen != imageLoadGeneration || activity.isFinishing()) return;
                            Toast.makeText(activity, "שגיאה בטעינת SVG: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
                    }
                });
                return;
            }

            if (fallbackSvgWebView.getVisibility() != View.GONE) {
                fallbackSvgWebView.setVisibility(View.GONE);
            }
            if (zoomView.getVisibility() != View.VISIBLE) {
                zoomView.setVisibility(View.VISIBLE);
            }
            btnProImageEditor.setVisibility(View.VISIBLE);
            btnWallpaper.setVisibility(View.VISIBLE);

            if (MediaFormatHelper.isGifFile(nameLow) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                host.getExecutor().execute(() -> {
                    try {
                        ImageDecoder.Source src = ImageDecoder.createSource(imgFile);
                        Drawable animatedDrawable = ImageDecoder.decodeDrawable(src);
                        uiHandler.post(() -> {
                            if (gen != imageLoadGeneration || activity.isFinishing()) return;
                            zoomView.setImageDrawable(animatedDrawable);
                            if (animatedDrawable instanceof AnimatedImageDrawable) {
                                ((AnimatedImageDrawable) animatedDrawable).start();
                            }
                            preloadNeighbors.run();
                        });
                    } catch (Throwable ignored) {
                    }
                });
                return;
            }

            final String cacheKey = imgFile.getAbsolutePath() + ":" + imgFile.lastModified();
            ViewerUiHelper.DecodedPhoto cached = photoCache.get(cacheKey);
            if (cached != null && cached.bitmap != null && !cached.bitmap.isRecycled()) {
                zoomView.setImageBitmap(cached.bitmap);
                preloadNeighbors.run();
                return;
            }

            host.getExecutor().execute(() -> {
                ViewerUiHelper.DecodedPhoto photo = ViewerUiHelper.decodeUltraClearPhoto(imgFile);
                if (photo.bitmap != null) {
                    photoCache.put(cacheKey, photo);
                }
                uiHandler.post(() -> {
                    if (gen != imageLoadGeneration || activity.isFinishing()) return;
                    if (photo.bitmap != null) {
                        zoomView.setImageBitmap(photo.bitmap);
                        preloadNeighbors.run();
                    } else {
                        zoomView.setVisibility(View.GONE);
                        fallbackSvgWebView.setBackgroundColor(Color.BLACK);
                        fallbackSvgWebView.setVisibility(View.VISIBLE);
                        fallbackSvgWebView.onResume();
                        try {
                            byte[] rawBytes = ViewerUiHelper.readFileBytesForViewer(imgFile, false);
                            String b64 = Base64.encodeToString(rawBytes, Base64.NO_WRAP);
                            String html = "<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=20'/>"
                                    + "<style>html,body{margin:0;padding:0;width:100%;height:100%;display:flex;align-items:center;justify-content:center;background:#000;}"
                                    + "img{max-width:96vw;max-height:84vh;object-fit:contain;}</style></head>"
                                    + "<body><img src='data:image/*;base64," + b64 + "'/></body></html>";
                            fallbackSvgWebView.loadDataWithBaseURL("https://img.local/", html, "text/html", "UTF-8", null);
                        } catch (Exception ignored) {
                        }
                    }
                });
            });
        };

        Runnable goPrev = () -> {
            if (imagePlaylist.size() <= 1) return;
            currentImageIdx = (currentImageIdx - 1 + imagePlaylist.size()) % imagePlaylist.size();
            loadCurrentImageIntoStage.run();
        };
        Runnable goNext = () -> {
            if (imagePlaylist.size() <= 1) return;
            currentImageIdx = (currentImageIdx + 1) % imagePlaylist.size();
            loadCurrentImageIntoStage.run();
        };

        btnFloatPrev.setOnClickListener(v -> goPrev.run());
        btnFloatNext.setOnClickListener(v -> goNext.run());

        zoomView.setPinchZoomEnabled(PrefsManager.isGesturePinchZoom(activity));
        zoomView.setHorizontalSwipeEnabled(PrefsManager.isGestureImageSwipeNav(activity));

        zoomView.setOnTapPositionListener((normX, isZoomed) -> {
            if (!isZoomed && PrefsManager.isGestureImageTapNav(activity) && imagePlaylist.size() > 1) {
                if (normX >= 0.72f) {
                    goNext.run();
                    return;
                } else if (normX <= 0.28f) {
                    goPrev.run();
                    return;
                }
            }
            host.toggleMediaControlsVisibility();
        });

        zoomView.setOnHorizontalSwipeListener(new ZoomableImageView.HorizontalSwipeListener() {
            @Override
            public void onSwipeToNext() {
                if (PrefsManager.isGestureImageSwipeNav(activity)) {
                    goNext.run();
                }
            }

            @Override
            public void onSwipeToPrevious() {
                if (PrefsManager.isGestureImageSwipeNav(activity)) {
                    goPrev.run();
                }
            }
        });

        loadCurrentImageIntoStage.run();
    }

    private void showImageOptionsDialog(View anchor) {
        File targetFile = host.getTargetFile();
        if (targetFile == null) return;
        String lower = targetFile.getName().toLowerCase(Locale.ROOT);
        boolean isSvg = MediaFormatHelper.isSvgFile(lower);
        boolean isGif = MediaFormatHelper.isGifFile(lower);
        boolean autoMode = PrefsManager.isAutoGestureMode(activity);

        List<GlassSideDrawerHelper.DrawerItem> drawerItems = new ArrayList<>();
        drawerItems.add(GlassSideDrawerHelper.DrawerItem.action(
                "קבע כטפט (מסך בית / נעילה)", R.drawable.ic_wallpaper, () -> {
                    Intent wi = new Intent(activity, WallpaperCropActivity.class);
                    wi.putExtra(WallpaperCropActivity.EXTRA_IMAGE_PATH, targetFile.getAbsolutePath());
                    activity.startActivity(wi);
                }));
        if (isSvg) {
            drawerItems.add(GlassSideDrawerHelper.DrawerItem.action(
                    "הצג קוד מקור SVG", R.drawable.ic_code, () -> {
                        Intent ci = new Intent(activity, CodeEditorActivity.class);
                        ci.putExtra(CodeEditorActivity.EXTRA_FILE_PATH, targetFile.getAbsolutePath());
                        activity.startActivity(ci);
                    }));
        }
        if (isGif) {
            drawerItems.add(GlassSideDrawerHelper.DrawerItem.action(
                    "פירוק GIF לתמונות PNG נפרדות", R.drawable.ic_images, () -> {
                        Toast.makeText(activity, "מפרק פריימים...", Toast.LENGTH_SHORT).show();
                        host.getExecutor().execute(() -> {
                            try {
                                File parent = targetFile.getParentFile() != null ? targetFile.getParentFile() : activity.getFilesDir();
                                File outDir = StorageUtils.uniqueDestinationFile(parent, targetFile.getName() + "_frames");
                                int count = GifToolsHelper.splitGifToPngs(targetFile, outDir);
                                host.getUiHandler().post(() -> Toast.makeText(activity,
                                        "פורקו " + count + " תמונות אל " + outDir.getName(), Toast.LENGTH_LONG).show());
                            } catch (Exception e) {
                                host.getUiHandler().post(() -> Toast.makeText(activity,
                                        "שגיאה בפירוק GIF: " + e.getMessage(), Toast.LENGTH_LONG).show());
                            }
                        });
                    }));
            drawerItems.add(GlassSideDrawerHelper.DrawerItem.action(
                    "המרת GIF לסרטון MP4", R.drawable.ic_video_cutter, () -> {
                        Toast.makeText(activity, "ממיר GIF ל-MP4...", Toast.LENGTH_SHORT).show();
                        host.getExecutor().execute(() -> {
                            try {
                                File parent = targetFile.getParentFile() != null ? targetFile.getParentFile() : activity.getFilesDir();
                                File outMp4 = StorageUtils.uniqueDestinationFile(parent, targetFile.getName() + ".mp4");
                                GifToolsHelper.convertGifToMp4(targetFile, outMp4);
                                host.getUiHandler().post(() -> Toast.makeText(activity,
                                        "נשמר סרטון: " + outMp4.getName(), Toast.LENGTH_LONG).show());
                            } catch (Exception e) {
                                host.getUiHandler().post(() -> Toast.makeText(activity,
                                        "שגיאה בהמרה: " + e.getMessage(), Toast.LENGTH_LONG).show());
                            }
                        });
                    }));
        } else if (!isSvg) {
            drawerItems.add(GlassSideDrawerHelper.DrawerItem.action(
                    "צור GIF מתמונות התיקייה", R.drawable.ic_images, () -> {
                        if (imagePlaylist.size() < 2) {
                            Toast.makeText(activity, "נדרשות לפחות 2 תמונות בתיקייה ליצירת GIF", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        Toast.makeText(activity, "יוצר אנימציית GIF מתמונות התיקייה...", Toast.LENGTH_SHORT).show();
                        host.getExecutor().execute(() -> {
                            try {
                                List<Bitmap> frames = new ArrayList<>();
                                int limit = Math.min(12, imagePlaylist.size());
                                for (int i = 0; i < limit; i++) {
                                    BitmapFactory.Options o = new BitmapFactory.Options();
                                    o.inSampleSize = 2;
                                    Bitmap b = BitmapFactory.decodeFile(imagePlaylist.get(i).getAbsolutePath(), o);
                                    if (b != null) frames.add(b);
                                }
                                File parent = targetFile.getParentFile() != null ? targetFile.getParentFile() : activity.getFilesDir();
                                File outGif = StorageUtils.uniqueDestinationFile(parent, "animation.gif");
                                GifToolsHelper.imagesToGif(frames, 550, 640, outGif);
                                for (Bitmap b : frames) b.recycle();
                                host.getUiHandler().post(() -> Toast.makeText(activity, "נוצר GIF: " + outGif.getName(), Toast.LENGTH_LONG).show());
                            } catch (Exception e) {
                                host.getUiHandler().post(() -> Toast.makeText(activity, "שגיאה ביצירת GIF: " + e.getMessage(), Toast.LENGTH_LONG).show());
                            }
                        });
                    }));
        }

        drawerItems.add(GlassSideDrawerHelper.DrawerItem.action(
                autoMode ? "כבה מצב מחוות" : "הפעל מצב מחוות",
                R.drawable.ic_settings, () -> {
                    boolean next = !autoMode;
                    PrefsManager.setAutoGestureMode(activity, next);
                    host.applyMediaImmersiveAndLayoutState();
                    Toast.makeText(activity, next ? "מצב מחוות הופעל" : "מצב מחוות כובה", Toast.LENGTH_SHORT).show();
                }));

        drawerItems.add(GlassSideDrawerHelper.DrawerItem.destructive(
                "מחיקה", R.drawable.ic_delete, host::confirmDeleteViewedFile));

        GlassSideDrawerHelper.show(activity, anchor, targetFile.getName(), drawerItems, true);
    }

    private void buildImageFolderPlaylist(File initialImage) {
        imagePlaylist.clear();
        currentImageIdx = 0;
        if (initialImage == null) return;
        File parent = initialImage.getParentFile();
        if (parent != null && parent.exists() && parent.isDirectory()) {
            File[] siblings = parent.listFiles();
            if (siblings != null) {
                for (File f : siblings) {
                    if (f.isFile() && MediaFormatHelper.isImageFile(f.getName())) {
                        imagePlaylist.add(f);
                    }
                }
            }
        }
        if (imagePlaylist.isEmpty()) {
            imagePlaylist.add(initialImage);
        } else {
            Collections.sort(imagePlaylist, (a, b) -> StorageUtils.compareHebrewFirst(a.getName(), b.getName()));
            for (int i = 0; i < imagePlaylist.size(); i++) {
                if (imagePlaylist.get(i).getAbsolutePath().equals(initialImage.getAbsolutePath())) {
                    currentImageIdx = i;
                    break;
                }
            }
        }
    }

    private String buildSvgViewerHtml(byte[] rawBytes) {
        String svgText = new String(rawBytes, StandardCharsets.UTF_8);
        if (!svgText.isEmpty() && svgText.charAt(0) == '\uFEFF') {
            svgText = svgText.substring(1);
        }
        String cleaned = svgText.trim()
                .replaceAll("(?is)<\\?xml[^>]*\\?>", "")
                .replaceAll("(?is)<!DOCTYPE[^>]*>", "")
                .replaceAll("(?is)<script\\b[^>]*>.*?</script>", "")
                .trim();

        int svgStart = cleaned.toLowerCase(Locale.ROOT).indexOf("<svg");
        if (svgStart >= 0) {
            cleaned = cleaned.substring(svgStart);
            int openEnd = cleaned.indexOf('>');
            if (openEnd > 4) {
                String openTag = cleaned.substring(0, openEnd + 1);
                String rest = cleaned.substring(openEnd + 1);
                String lowerTag = openTag.toLowerCase(Locale.ROOT);
                StringBuilder patchedTag = new StringBuilder(openTag.substring(0, openEnd));
                if (!lowerTag.contains("xmlns=")) {
                    patchedTag.append(" xmlns=\"http://www.w3.org/2000/svg\"");
                }
                if (!lowerTag.contains("viewbox=")) {
                    Double w = extractSvgNumericAttr(openTag, "width");
                    Double h = extractSvgNumericAttr(openTag, "height");
                    if (w != null && h != null && w > 0 && h > 0) {
                        patchedTag.append(" viewBox=\"0 0 ")
                                .append(w.doubleValue() == Math.rint(w) ? String.valueOf(w.longValue()) : String.valueOf(w))
                                .append(' ')
                                .append(h.doubleValue() == Math.rint(h) ? String.valueOf(h.longValue()) : String.valueOf(h))
                                .append('"');
                    }
                }
                if (!lowerTag.contains("preserveaspectratio=")) {
                    patchedTag.append(" preserveAspectRatio=\"xMidYMid meet\"");
                }
                patchedTag.append('>');
                cleaned = patchedTag.toString() + rest;
            }
            return "<!DOCTYPE html><html><head>"
                    + "<meta charset='utf-8'/>"
                    + "<meta name='viewport' content='width=device-width,initial-scale=1.0,maximum-scale=20.0,user-scalable=yes'/>"
                    + "<style>"
                    + "html,body{margin:0;padding:0;width:100%;height:100%;overflow:hidden;"
                    + "background-color:#E2E8F0;"
                    + "background-image:linear-gradient(45deg,#CBD5E1 25%,transparent 25%),"
                    + "linear-gradient(-45deg,#CBD5E1 25%,transparent 25%),"
                    + "linear-gradient(45deg,transparent 75%,#CBD5E1 75%),"
                    + "linear-gradient(-45deg,transparent 75%,#CBD5E1 75%);"
                    + "background-size:24px 24px;"
                    + "background-position:0 0,0 12px,12px -12px,-12px 0;}"
                    + ".stage{box-sizing:border-box;width:100vw;height:100vh;padding:72px 16px 84px 16px;"
                    + "display:flex;align-items:center;justify-content:center;}"
                    + ".stage>svg{display:block;width:100% !important;height:100% !important;max-width:92vw;max-height:78vh;}"
                    + "</style></head><body><div class='stage'>" + cleaned + "</div></body></html>";
        }

        String b64 = Base64.encodeToString(rawBytes, Base64.NO_WRAP);
        return "<!DOCTYPE html><html><head>"
                + "<meta charset='utf-8'/>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1.0,maximum-scale=20.0,user-scalable=yes'/>"
                + "<style>html,body{margin:0;padding:0;width:100%;height:100%;display:flex;align-items:center;justify-content:center;background:#E2E8F0;}"
                + "img{width:90vw;height:78vh;object-fit:contain;}</style></head>"
                + "<body><img src='data:image/svg+xml;base64," + b64 + "'/></body></html>";
    }

    private Double extractSvgNumericAttr(String openTag, String attrName) {
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("\\b" + attrName + "\\s*=\\s*[\"']\\s*([0-9]+(?:\\.[0-9]+)?)(?:px|pt)?\\s*[\"']",
                            java.util.regex.Pattern.CASE_INSENSITIVE)
                    .matcher(openTag);
            if (m.find()) {
                return Double.parseDouble(m.group(1));
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public void destroy() {
        photoCache.evictAll();
    }

    private int dp(float v) {
        return ViewerUiHelper.dp(activity, v);
    }
}
