package com.aishhatorah.filemanagerpro.viewer;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfRenderer;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
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

import com.aishhatorah.filemanagerpro.AppLogger;
import com.aishhatorah.filemanagerpro.PrefsManager;
import com.aishhatorah.filemanagerpro.R;
import com.aishhatorah.filemanagerpro.ThemeHelper;
import com.aishhatorah.filemanagerpro.ZoomableImageView;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * בקר צפייה מתקדם לקבצי PDF:
 * - במה מלאה Edge-to-Edge ללא פס לבן בתחתית
 * - סרגל עליון ותחתון צפים בזכוכית כהה מעוגלת ב-4 פינות (בדומה לנגן הווידאו)
 * - הצגה/הסתרה בלחיצה על הדף, דפדוף אנכי ואופקי חלקים בהתאם להגדרות המחוות והאנימציה
 * - גלגל טעינה עם ביטול שקוף בפתיחה ראשונית בלבד (ללא הבהוב בין עמודים)
 */
public class PdfViewerController {

    private final AppCompatActivity activity;
    private final ViewerHost host;

    private ParcelFileDescriptor pdfDescriptor;
    private PdfRenderer pdfRenderer;
    private Bitmap activePdfBitmap;
    private int currentPdfPage = 0;
    private final Object pdfLock = new Object();
    private final AtomicBoolean pdfCancelled = new AtomicBoolean(false);
    private final AtomicInteger pdfRenderGen = new AtomicInteger(0);

    public PdfViewerController(ViewerHost host) {
        this.host = host;
        this.activity = host.getActivity();
    }

    public void render(int theme) {
        ViewerUiHelper.applyMediaTransparentSystemBars(activity);
        pdfCancelled.set(false);
        currentPdfPage = 0;

        int accent = ThemeHelper.getAccentColor(activity, theme);
        int textPrimary = ThemeHelper.getPrimaryTextColor(activity, theme);

        FrameLayout stageRoot = new FrameLayout(activity);
        stageRoot.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        stageRoot.setBackgroundColor(Color.BLACK);

        FrameLayout pdfViewport = new FrameLayout(activity);
        pdfViewport.setBackgroundColor(Color.parseColor("#0B0F19"));
        stageRoot.addView(pdfViewport, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ZoomableImageView pageImage = new ZoomableImageView(activity);
        pageImage.setBackgroundColor(Color.TRANSPARENT);
        pageImage.setPinchZoomEnabled(PrefsManager.isGesturePinchZoom(activity));
        pageImage.setHorizontalSwipeEnabled(PrefsManager.isGesturePdfSwipe(activity));
        pageImage.setVerticalSwipeEnabled(PrefsManager.isGesturePdfSwipe(activity));
        pdfViewport.addView(pageImage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        int glassBgColor = ColorUtils.setAlphaComponent(
                ColorUtils.blendARGB(ThemeHelper.getTopBarColor(activity, theme), Color.parseColor("#0A0F1C"), 0.62f),
                196);
        int glassStrokeColor = Color.argb(65, 255, 255, 255);

        // סרגל עליון צף מעוגל בכל 4 הפינות כמו בווידאו
        LinearLayout topOverlay = new LinearLayout(activity);
        topOverlay.setOrientation(LinearLayout.HORIZONTAL);
        topOverlay.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        topOverlay.setGravity(Gravity.CENTER_VERTICAL);
        topOverlay.setClickable(true);
        topOverlay.setBackground(ViewerUiHelper.createFloatingGlassBarBackground(activity, 22, glassBgColor, glassStrokeColor));
        topOverlay.setElevation(dp(8));
        topOverlay.setPadding(dp(10), dp(6), dp(10), dp(6));

        ImageButton btnBack = ViewerUiHelper.createCleanBorderlessIconButton(activity, R.drawable.ic_arrow_back, dp(42), Color.WHITE);
        btnBack.setContentDescription("חזרה");
        btnBack.setOnClickListener(v -> host.proceedCloseOrNavigateBack());
        topOverlay.addView(btnBack);

        LinearLayout centerCol = new LinearLayout(activity);
        centerCol.setOrientation(LinearLayout.VERTICAL);
        centerCol.setGravity(Gravity.CENTER);

        File targetFile = host.getTargetFile();
        String fileName = targetFile != null ? targetFile.getName() : "";
        TextView tvTitle = ViewerUiHelper.createMarqueeTitleView(activity, fileName, Color.WHITE,
                ThemeHelper.spScaled(activity, 14f));
        centerCol.addView(tvTitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView tvPageSub = new TextView(activity);
        tvPageSub.setText("טוען מסמך...");
        tvPageSub.setTextSize(11.5f);
        tvPageSub.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tvPageSub.setTextColor(Color.parseColor("#CBD5E1"));
        tvPageSub.setTextDirection(View.TEXT_DIRECTION_LTR);
        tvPageSub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(2);
        centerCol.addView(tvPageSub, subLp);

        LinearLayout.LayoutParams centerLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        centerLp.setMarginStart(dp(6));
        centerLp.setMarginEnd(dp(6));
        topOverlay.addView(centerCol, centerLp);

        ImageButton btnShare = ViewerUiHelper.createCleanBorderlessIconButton(activity, R.drawable.ic_share, dp(42), Color.WHITE);
        btnShare.setContentDescription("שיתוף");
        btnShare.setOnClickListener(v -> host.shareCurrentFile());
        topOverlay.addView(btnShare);

        host.setHeaderViews(tvTitle, tvPageSub, fileName);

        final int statusFloor = ViewerUiHelper.resolveStatusBarHeightFallback(activity);
        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        topLp.setMargins(dp(10), statusFloor + dp(8), dp(10), 0);
        stageRoot.addView(topOverlay, topLp);

        // סרגל ניווט תחתון צף לחלוטין בזכוכית מעוגלת בכל 4 הפינות
        LinearLayout navOverlay = new LinearLayout(activity);
        navOverlay.setOrientation(LinearLayout.HORIZONTAL);
        navOverlay.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        navOverlay.setGravity(Gravity.CENTER);
        navOverlay.setPadding(dp(16), dp(8), dp(16), dp(8));
        navOverlay.setBackground(ViewerUiHelper.createFloatingGlassBarBackground(activity, 22, glassBgColor, glassStrokeColor));
        navOverlay.setElevation(dp(8));
        navOverlay.setClickable(true);

        ImageButton btnPrev = ViewerUiHelper.createCleanBorderlessIconButton(activity, R.drawable.ic_chevron_right, dp(42), Color.WHITE);
        btnPrev.setContentDescription("עמוד קודם");
        btnPrev.setEnabled(false);
        btnPrev.setAlpha(0.35f);

        TextView tvPageIndicator = new TextView(activity);
        tvPageIndicator.setText("...");
        tvPageIndicator.setTextColor(Color.WHITE);
        tvPageIndicator.setTextSize(14f);
        tvPageIndicator.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tvPageIndicator.setTextDirection(View.TEXT_DIRECTION_LTR);
        tvPageIndicator.setGravity(Gravity.CENTER);
        tvPageIndicator.setPadding(dp(22), dp(4), dp(22), dp(4));

        ImageButton btnNext = ViewerUiHelper.createCleanBorderlessIconButton(activity, R.drawable.ic_chevron_left, dp(42), Color.WHITE);
        btnNext.setContentDescription("עמוד הבא");
        btnNext.setEnabled(false);
        btnNext.setAlpha(0.35f);

        navOverlay.addView(btnPrev);
        navOverlay.addView(tvPageIndicator);
        navOverlay.addView(btnNext);

        FrameLayout.LayoutParams navLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        navLp.setMargins(dp(10), 0, dp(10), dp(10));
        stageRoot.addView(navOverlay, navLp);

        final FrameLayout pdfLoadingOverlay = ViewerUiHelper.createDocumentLoadingOverlay(
                activity, theme, accent, textPrimary, () -> {
                    pdfCancelled.set(true);
                    host.proceedCloseOrNavigateBack();
                });
        pdfLoadingOverlay.setVisibility(View.VISIBLE);
        stageRoot.addView(pdfLoadingOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

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
            ViewGroup.MarginLayoutParams bLp = (ViewGroup.MarginLayoutParams) navOverlay.getLayoutParams();
            if (bLp != null) {
                bLp.leftMargin = sys.left + dp(10);
                bLp.rightMargin = sys.right + dp(10);
                bLp.bottomMargin = sys.bottom + dp(10);
                navOverlay.setLayoutParams(bLp);
            }
            return insets;
        });

        host.setMediaControlsHidden(false);
        host.registerMediaChrome(false, false, true, stageRoot, pdfViewport, topOverlay, navOverlay,
                null, null, null, null, pageImage);

        activity.setContentView(stageRoot);
        ViewCompat.requestApplyInsets(stageRoot);
        host.applyMediaImmersiveAndLayoutState();

        // לחיצה בודדת במרכז מסתירה ומחזירה את הסרגלים הצפים
        pageImage.setOnTapPositionListener((normX, isZoomed) -> host.toggleMediaControlsVisibility());

        final int[] totalPagesHolder = new int[]{0};
        final boolean[] isRenderingPage = new boolean[]{false};
        final Handler uiHandler = host.getUiHandler();

        final java.util.function.BiConsumer<Integer, Integer> renderPageAsync = (requestedPage, slideDir) -> {
            final int myGen = pdfRenderGen.incrementAndGet();
            final boolean isInitialOpen;
            synchronized (pdfLock) {
                isInitialOpen = (pdfRenderer == null);
            }
            // מציגים מסך טעינה אך ורק בפתיחה הראשונית של הקובץ כדי למנוע הבהוב בין דפים
            if (isInitialOpen) {
                pdfLoadingOverlay.setVisibility(View.VISIBLE);
            }

            final File docFile = host.getTargetFile();
            host.getExecutor().execute(() -> {
                Bitmap renderedBmp = null;
                int totalPages = 0;
                int clampedPage = Math.max(0, requestedPage);
                String errorMsg = null;

                synchronized (pdfLock) {
                    if (pdfCancelled.get() || pdfRenderGen.get() != myGen) {
                        return;
                    }
                    try {
                        if (pdfRenderer == null && docFile != null) {
                            pdfDescriptor = ParcelFileDescriptor.open(docFile, ParcelFileDescriptor.MODE_READ_ONLY);
                            pdfRenderer = new PdfRenderer(pdfDescriptor);
                        }
                        if (pdfRenderer == null || pdfCancelled.get() || pdfRenderGen.get() != myGen) {
                            return;
                        }
                        totalPages = pdfRenderer.getPageCount();
                        if (totalPages > 0) {
                            clampedPage = Math.max(0, Math.min(totalPages - 1, requestedPage));
                            try (PdfRenderer.Page page = pdfRenderer.openPage(clampedPage)) {
                                int pw = Math.max(1, page.getWidth());
                                int ph = Math.max(1, page.getHeight());
                                float scale = Math.max(1.8f, Math.min(3.5f, 2200f / Math.max(pw, ph)));
                                final double maxPixels = 6_200_000.0;
                                double estPixels = (pw * (double) scale) * (ph * (double) scale);
                                if (estPixels > maxPixels) {
                                    scale *= (float) Math.sqrt(maxPixels / estPixels);
                                }
                                for (int attempt = 0; attempt < 3 && renderedBmp == null; attempt++) {
                                    int w = Math.max(1, Math.min(2400, Math.round(pw * scale)));
                                    int h = Math.max(1, Math.min(2400, Math.round(ph * scale)));
                                    try {
                                        renderedBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                                    } catch (OutOfMemoryError oom) {
                                        System.gc();
                                        scale *= 0.65f;
                                    }
                                }
                                if (renderedBmp != null && !pdfCancelled.get() && pdfRenderGen.get() == myGen) {
                                    renderedBmp.eraseColor(Color.WHITE);
                                    page.render(renderedBmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                                }
                            }
                        }
                    } catch (Throwable t) {
                        AppLogger.error(activity, "PdfViewer", "Failed rendering PDF page", t);
                        errorMsg = t.getMessage() != null ? t.getMessage() : "שגיאה בטעינת ה-PDF";
                    }
                }

                final Bitmap finalBmp = renderedBmp;
                final int finalTotal = totalPages;
                final int finalPage = clampedPage;
                final String finalErr = errorMsg;

                uiHandler.post(() -> {
                    pdfLoadingOverlay.setVisibility(View.GONE);
                    isRenderingPage[0] = false;

                    if (pdfCancelled.get() || pdfRenderGen.get() != myGen || activity.isFinishing() || activity.isDestroyed()) {
                        if (finalBmp != null && !finalBmp.isRecycled()) {
                            finalBmp.recycle();
                        }
                        return;
                    }
                    if (finalErr != null) {
                        tvPageIndicator.setText("שגיאה");
                        tvPageSub.setText("לא ניתן להציג PDF");
                        Toast.makeText(activity, "לא ניתן להציג PDF: " + finalErr, Toast.LENGTH_LONG).show();
                        return;
                    }
                    totalPagesHolder[0] = finalTotal;
                    currentPdfPage = finalPage;

                    if (finalBmp != null) {
                        Bitmap oldBmp = activePdfBitmap;
                        activePdfBitmap = finalBmp;
                        pageImage.animate().cancel();
                        pageImage.setImageBitmap(finalBmp);
                        if (slideDir != 0 && !PrefsManager.isReduceAnimations(activity)) {
                            pageImage.setAlpha(0.76f);
                            pageImage.setTranslationY(slideDir * dp(26));
                            pageImage.animate()
                                    .translationY(0f)
                                    .alpha(1f)
                                    .setDuration(180L)
                                    .setInterpolator(new DecelerateInterpolator())
                                    .start();
                        } else {
                            pageImage.setAlpha(1f);
                            pageImage.setTranslationY(0f);
                        }
                        if (oldBmp != null && oldBmp != finalBmp && !oldBmp.isRecycled()) {
                            oldBmp.recycle();
                        }
                    }

                    String indicator = finalTotal > 0 ? ((currentPdfPage + 1) + " / " + finalTotal) : "0 / 0";
                    tvPageIndicator.setText(indicator);
                    tvPageSub.setText(indicator);
                    boolean hasPrev = currentPdfPage > 0;
                    boolean hasNext = currentPdfPage < finalTotal - 1;
                    btnPrev.setEnabled(hasPrev);
                    btnPrev.setAlpha(hasPrev ? 1f : 0.35f);
                    btnNext.setEnabled(hasNext);
                    btnNext.setAlpha(hasNext ? 1f : 0.35f);
                });
            });
        };

        final java.util.function.IntConsumer goDeltaPage = delta -> {
            int total = totalPagesHolder[0];
            if (total <= 0 || isRenderingPage[0]) return;
            int target = currentPdfPage + delta;
            if (target < 0 || target >= total) return;
            isRenderingPage[0] = true;
            renderPageAsync.accept(target, delta > 0 ? 1 : -1);
        };

        btnPrev.setOnClickListener(v -> goDeltaPage.accept(-1));
        btnNext.setOnClickListener(v -> goDeltaPage.accept(1));

        pageImage.setOnHorizontalSwipeListener(new ZoomableImageView.HorizontalSwipeListener() {
            @Override
            public void onSwipeToNext() {
                if (PrefsManager.isGesturePdfSwipe(activity)) {
                    goDeltaPage.accept(1);
                }
            }

            @Override
            public void onSwipeToPrevious() {
                if (PrefsManager.isGesturePdfSwipe(activity)) {
                    goDeltaPage.accept(-1);
                }
            }
        });

        pageImage.setOnVerticalSwipeListener(new ZoomableImageView.VerticalSwipeListener() {
            @Override
            public void onSwipeUp() {
                if (PrefsManager.isGesturePdfSwipe(activity)) {
                    goDeltaPage.accept(1);
                }
            }

            @Override
            public void onSwipeDown() {
                if (PrefsManager.isGesturePdfSwipe(activity)) {
                    goDeltaPage.accept(-1);
                }
            }
        });

        renderPageAsync.accept(currentPdfPage, 0);
    }

    public void destroy() {
        pdfCancelled.set(true);
        synchronized (pdfLock) {
            if (pdfRenderer != null) {
                try {
                    pdfRenderer.close();
                } catch (Exception ignored) {
                }
                pdfRenderer = null;
            }
            if (pdfDescriptor != null) {
                try {
                    pdfDescriptor.close();
                } catch (Exception ignored) {
                }
                pdfDescriptor = null;
            }
        }
        if (activePdfBitmap != null && !activePdfBitmap.isRecycled()) {
            activePdfBitmap.recycle();
        }
        activePdfBitmap = null;
    }

    private int dp(float v) {
        return ViewerUiHelper.dp(activity, v);
    }
}
