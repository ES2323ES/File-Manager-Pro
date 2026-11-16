package com.aishhatorah.filemanagerpro;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;

import androidx.annotation.NonNull;

/**
 * סרגל התקדמות בצורת גלים (Sinusoidal Wave SeekBar) לנגן האודיו והווידאו.
 * מתוכנן לציור מהיר ללא הקצאות בזמן גרירה ומניעת ריצודים (כיוון התקדמות תמיד משמאל לימין).
 */
public class WavySeekBar extends View {

    public interface OnSeekBarChangeListener {
        void onProgressChanged(WavySeekBar seekBar, int progress, boolean fromUser);
        void onStartTrackingTouch(WavySeekBar seekBar);
        void onStopTrackingTouch(WavySeekBar seekBar);
    }

    private final Paint wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path wavePath = new Path();

    private int max = 100;
    private int progress = 0;
    private boolean userDragging = false;
    private float startX = 0f;
    private float endX = 0f;
    private float centerY = 0f;
    private final float thumbRadius;
    private final float thumbActiveRadius;
    private final float amplitudePx;
    private final float wavelengthPx;

    private OnSeekBarChangeListener listener;

    public WavySeekBar(Context context) {
        super(context);
        setLayoutDirection(LAYOUT_DIRECTION_LTR);

        thumbRadius = dp(6.0f);
        thumbActiveRadius = dp(8.0f);
        amplitudePx = dp(3.4f);
        wavelengthPx = dp(20.0f);

        wavePaint.setStyle(Paint.Style.STROKE);
        wavePaint.setStrokeWidth(dp(2.8f));
        wavePaint.setStrokeCap(Paint.Cap.ROUND);
        wavePaint.setStrokeJoin(Paint.Join.ROUND);
        wavePaint.setColor(Color.parseColor("#3B82F6"));

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(dp(2.4f));
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setColor(Color.argb(80, 203, 213, 225));

        thumbPaint.setStyle(Paint.Style.FILL);
        thumbPaint.setColor(Color.parseColor("#3B82F6"));
    }

    private float dp(float val) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, val, getResources().getDisplayMetrics());
    }

    public void setColors(int waveColor, int trackColor, int thumbColor) {
        wavePaint.setColor(waveColor);
        trackPaint.setColor(trackColor);
        thumbPaint.setColor(thumbColor);
        invalidate();
    }

    public void setMax(int max) {
        int safeMax = Math.max(1, max);
        if (this.max != safeMax) {
            this.max = safeMax;
            if (this.progress > safeMax) {
                this.progress = safeMax;
            }
            invalidate();
        }
    }

    public int getMax() {
        return max;
    }

    public void setProgress(int progress) {
        if (userDragging) return;
        int clamped = Math.max(0, Math.min(max, progress));
        if (this.progress != clamped) {
            this.progress = clamped;
            invalidate();
        }
    }

    public int getProgress() {
        return progress;
    }

    public void setOnSeekBarChangeListener(OnSeekBarChangeListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredH = Math.round(dp(34f));
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int hMode = MeasureSpec.getMode(heightMeasureSpec);
        int hSize = MeasureSpec.getSize(heightMeasureSpec);
        int h;
        if (hMode == MeasureSpec.EXACTLY) {
            h = hSize;
        } else if (hMode == MeasureSpec.AT_MOST) {
            h = Math.min(desiredH, hSize);
        } else {
            h = desiredH;
        }
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rebuildWavePath(w, h);
    }

    private void rebuildWavePath(int w, int h) {
        wavePath.reset();
        float padH = Math.max(thumbActiveRadius + dp(2f), dp(10f));
        startX = padH;
        endX = Math.max(startX + 1f, w - padH);
        centerY = h / 2f;

        float stepPx = Math.max(2f, dp(1.5f));
        boolean first = true;
        float twoPi = (float) (2.0 * Math.PI);
        for (float x = startX; x <= endX; x += stepPx) {
            float y = centerY + amplitudePx * (float) Math.sin(twoPi * (x - startX) / wavelengthPx);
            if (first) {
                wavePath.moveTo(x, y);
                first = false;
            } else {
                wavePath.lineTo(x, y);
            }
        }
        float endY = centerY + amplitudePx * (float) Math.sin(twoPi * (endX - startX) / wavelengthPx);
        wavePath.lineTo(endX, endY);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (endX <= startX) return;

        float fraction = max > 0 ? Math.max(0f, Math.min(1f, (float) progress / (float) max)) : 0f;
        float thumbX = startX + fraction * (endX - startX);

        if (thumbX < endX) {
            canvas.drawLine(thumbX, centerY, endX, centerY, trackPaint);
        }

        if (thumbX > startX) {
            canvas.save();
            canvas.clipRect(0, 0, thumbX, getHeight());
            canvas.drawPath(wavePath, wavePaint);
            canvas.restore();
        }

        float r = userDragging ? thumbActiveRadius : thumbRadius;
        canvas.drawCircle(thumbX, centerY, r, thumbPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                ViewParent parent = getParent();
                if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
                userDragging = true;
                updateFromTouchX(event.getX());
                if (listener != null) {
                    listener.onStartTrackingTouch(this);
                    listener.onProgressChanged(this, progress, true);
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (userDragging) {
                    int prev = progress;
                    updateFromTouchX(event.getX());
                    if (progress != prev && listener != null) {
                        listener.onProgressChanged(this, progress, true);
                    }
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (userDragging) {
                    int prev = progress;
                    updateFromTouchX(event.getX());
                    userDragging = false;
                    if (progress != prev && listener != null) {
                        listener.onProgressChanged(this, progress, true);
                    }
                    if (listener != null) {
                        listener.onStopTrackingTouch(this);
                    }
                    invalidate();
                }
                return true;
            }
        }
        return super.onTouchEvent(event);
    }

    private void updateFromTouchX(float touchX) {
        float span = Math.max(1f, endX - startX);
        float frac = Math.max(0f, Math.min(1f, (touchX - startX) / span));
        progress = Math.round(frac * max);
    }
}
