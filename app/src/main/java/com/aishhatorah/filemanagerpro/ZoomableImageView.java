package com.aishhatorah.filemanagerpro;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.appcompat.widget.AppCompatImageView;

/**
 * סעיף יד: מציג תמונות עם הגדלה והקטנה חלקה ביד (Pinch-to-Zoom + גרירה ביד)
 */
public class ZoomableImageView extends AppCompatImageView {

    private final Matrix matrix = new Matrix();
    private final float[] matrixValues = new float[9];
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;

    private float currentScale = 1f;
    private float minScale = 1f;
    private float maxScale = 6f;

    private float baseScale = 1f;
    private final PointF lastTouch = new PointF();
    private boolean isDragging = false;

    public ZoomableImageView(Context context) {
        super(context);
        setScaleType(ScaleType.MATRIX);

        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float factor = detector.getScaleFactor();
                float targetScale = currentScale * factor;
                if (targetScale > maxScale) {
                    factor = maxScale / currentScale;
                    currentScale = maxScale;
                } else if (targetScale < minScale) {
                    factor = minScale / currentScale;
                    currentScale = minScale;
                } else {
                    currentScale = targetScale;
                }
                matrix.postScale(factor, factor, detector.getFocusX(), detector.getFocusY());
                fixTranslationBounds();
                setImageMatrix(matrix);
                return true;
            }
        });

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (currentScale > minScale * 1.15f) {
                    fitImageToCenter();
                } else {
                    float boost = 2.5f;
                    currentScale = minScale * boost;
                    matrix.postScale(boost, boost, e.getX(), e.getY());
                    fixTranslationBounds();
                    setImageMatrix(matrix);
                }
                return true;
            }
        });
    }

    @Override
    public void setImageBitmap(Bitmap bm) {
        super.setImageBitmap(bm);
        post(this::fitImageToCenter);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        fitImageToCenter();
    }

    public void fitImageToCenter() {
        Drawable d = getDrawable();
        if (d == null || getWidth() <= 0 || getHeight() <= 0) return;
        int dw = d.getIntrinsicWidth();
        int dh = d.getIntrinsicHeight();
        if (dw <= 0 || dh <= 0) return;

        float scaleX = (float) getWidth() / (float) dw;
        float scaleY = (float) getHeight() / (float) dh;
        baseScale = Math.min(scaleX, scaleY);
        minScale = baseScale;
        maxScale = baseScale * 6f;
        currentScale = baseScale;

        float dx = (getWidth() - dw * baseScale) * 0.5f;
        float dy = (getHeight() - dh * baseScale) * 0.5f;

        matrix.reset();
        matrix.postScale(baseScale, baseScale);
        matrix.postTranslate(dx, dy);
        setImageMatrix(matrix);
    }

    private void fixTranslationBounds() {
        Drawable d = getDrawable();
        if (d == null) return;
        matrix.getValues(matrixValues);
        float transX = matrixValues[Matrix.MTRANS_X];
        float transY = matrixValues[Matrix.MTRANS_Y];
        float scaledW = d.getIntrinsicWidth() * matrixValues[Matrix.MSCALE_X];
        float scaledH = d.getIntrinsicHeight() * matrixValues[Matrix.MSCALE_Y];

        float fixX = getFixDragTrans(transX, getWidth(), scaledW);
        float fixY = getFixDragTrans(transY, getHeight(), scaledH);
        if (fixX != 0f || fixY != 0f) {
            matrix.postTranslate(fixX, fixY);
        }
    }

    private float getFixDragTrans(float trans, float viewSize, float contentSize) {
        float minTrans;
        float maxTrans;
        if (contentSize <= viewSize) {
            minTrans = (viewSize - contentSize) * 0.5f;
            maxTrans = minTrans;
        } else {
            minTrans = viewSize - contentSize;
            maxTrans = 0f;
        }
        if (trans < minTrans) return minTrans - trans;
        if (trans > maxTrans) return maxTrans - trans;
        return 0f;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastTouch.set(event.getX(), event.getY());
                isDragging = true;
                break;
            case MotionEvent.ACTION_MOVE:
                if (isDragging && !scaleDetector.isInProgress() && currentScale > minScale * 1.02f) {
                    float dx = event.getX() - lastTouch.x;
                    float dy = event.getY() - lastTouch.y;
                    matrix.postTranslate(dx, dy);
                    fixTranslationBounds();
                    setImageMatrix(matrix);
                    lastTouch.set(event.getX(), event.getY());
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_POINTER_UP:
                isDragging = false;
                break;
        }
        return true;
    }
}
