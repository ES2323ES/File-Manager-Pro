package com.aishhatorah.filemanagerpro;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;

import androidx.annotation.NonNull;

/**
 * סרגל התקדמות עבה עם ידית בצורת מקל עומד, גלים הזורמים אחורה בעת ניגון,
 * ופס ישר ורציף בעת עצירה.
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
    private final RectF thumbRect = new RectF();

    private int max = 100;
    private int progress = 0;
    private boolean playing = true;
    private boolean userDragging = false;
    private float startX = 0f;
    private float endX = 0f;
    private float centerY = 0f;
    private float thumbHalfW;
    private float thumbHalfH;
    private float thumbActiveHalfW;
    private float thumbActiveHalfH;
    private float amplitudePx;
    private float wavelengthPx;
    private float desiredHeightPx;

    private float wavePhase = 0f;
    private long lastWaveTickMs = 0L;
    private boolean thumbOnlyTouch = false;

    private OnSeekBarChangeListener listener;

    public WavySeekBar(Context context) {
        super(context);
        setLayoutDirection(LAYOUT_DIRECTION_LTR);

        applyDimensions(false);

        wavePaint.setStyle(Paint.Style.STROKE);
        wavePaint.setStrokeCap(Paint.Cap.ROUND);
        wavePaint.setStrokeJoin(Paint.Join.ROUND);
        wavePaint.setColor(Color.parseColor("#3B82F6"));

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setColor(Color.argb(80, 203, 213, 225));

        thumbPaint.setStyle(Paint.Style.FILL);
        thumbPaint.setColor(Color.parseColor("#3B82F6"));
    }

    private float dp(float val) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, val, getResources().getDisplayMetrics());
    }

    private void applyDimensions(boolean compact) {
        if (compact) {
            thumbHalfW = dp(1.8f);
            thumbHalfH = dp(7.5f);
            thumbActiveHalfW = dp(2.4f);
            thumbActiveHalfH = dp(9.5f);
            amplitudePx = dp(1.8f);
            wavelengthPx = dp(18.0f);
            desiredHeightPx = dp(22f);
            wavePaint.setStrokeWidth(dp(3.4f));
            trackPaint.setStrokeWidth(dp(3.0f));
        } else {
            thumbHalfW = dp(2.6f);
            thumbHalfH = dp(11.5f);
            thumbActiveHalfW = dp(3.4f);
            thumbActiveHalfH = dp(14.0f);
            amplitudePx = dp(2.8f);
            wavelengthPx = dp(26.0f);
            desiredHeightPx = dp(38f);
            wavePaint.setStrokeWidth(dp(4.8f));
            trackPaint.setStrokeWidth(dp(4.4f));
        }
    }

    public void setCompactStyle(boolean compact) {
        applyDimensions(compact);
        recomputeBounds(getWidth(), getHeight());
        requestLayout();
        invalidate();
    }

    public void setPlaying(boolean isPlaying) {
        if (this.playing != isPlaying) {
            this.playing = isPlaying;
            lastWaveTickMs = SystemClock.uptimeMillis();
            invalidate();
        }
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
        int desiredH = Math.round(desiredHeightPx);
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
        recomputeBounds(w, h);
    }

    private void recomputeBounds(int w, int h) {
        if (w <= 0 || h <= 0) {
            startX = 0f;
            endX = 0f;
            return;
        }
        float padH = Math.max(thumbActiveHalfW + dp(4f), dp(8f));
        startX = padH;
        endX = Math.max(startX + 1f, w - padH);
        centerY = h / 2f;
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
            if (playing) {
                long now = SystemClock.uptimeMillis();
                if (lastWaveTickMs == 0L) {
                    lastWaveTickMs = now;
                } else {
                    long dt = Math.min(64L, Math.max(0L, now - lastWaveTickMs));
                    lastWaveTickMs = now;
                    // הגדלת המופע מעבירה את פסגות הגל שמאלה (אחורה ביחס להתקדמות הפס)
                    wavePhase = (wavePhase + (dt * 0.0044f)) % (float) (2.0 * Math.PI);
                }

                wavePath.reset();
                float stepPx = Math.max(2f, dp(2.0f));
                float twoPi = (float) (2.0 * Math.PI);
                boolean first = true;
                for (float x = startX; x < thumbX; x += stepPx) {
                    float y = centerY + amplitudePx * (float) Math.sin(twoPi * (x - startX) / wavelengthPx + wavePhase);
                    if (first) {
                        wavePath.moveTo(x, y);
                        first = false;
                    } else {
                        wavePath.lineTo(x, y);
                    }
                }
                float endY = centerY + amplitudePx * (float) Math.sin(twoPi * (thumbX - startX) / wavelengthPx + wavePhase);
                if (first) {
                    wavePath.moveTo(startX, endY);
                }
                wavePath.lineTo(thumbX, endY);
                canvas.drawPath(wavePath, wavePaint);

                if (isAttachedToWindow() && getVisibility() == VISIBLE) {
                    postInvalidateOnAnimation();
                }
            } else {
                lastWaveTickMs = 0L;
                canvas.drawLine(startX, centerY, thumbX, centerY, wavePaint);
            }
        }

        // נקודת הראש בצורת מקל עומד עם קצוות מעוגלים
        float hw = userDragging ? thumbActiveHalfW : thumbHalfW;
        float hh = userDragging ? thumbActiveHalfH : thumbHalfH;
        thumbRect.set(thumbX - hw, centerY - hh, thumbX + hw, centerY + hh);
        canvas.drawRoundRect(thumbRect, hw, hw, thumbPaint);
    }

    public void setThumbOnlyTouch(boolean thumbOnly) {
        this.thumbOnlyTouch = thumbOnly;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                if (thumbOnlyTouch) {
                    float sx = endX > startX ? startX : getPaddingLeft() + thumbActiveHalfW + dp(4f);
                    float ex = endX > startX ? endX : Math.max(sx + 1f, getWidth() - getPaddingRight() - thumbActiveHalfW - dp(4f));
                    float frac = max > 0 ? Math.max(0f, Math.min(1f, (float) progress / max)) : 0f;
                    float thumbX = sx + frac * (ex - sx);
                    if (Math.abs(event.getX() - thumbX) > dp(18f)) {
                        return false;
                    }
                }
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
