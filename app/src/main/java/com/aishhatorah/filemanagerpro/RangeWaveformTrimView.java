package com.aishhatorah.filemanagerpro;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/**
 * סעיף 36: פס חיתוך משולב ואחיד (Start + End על אותו פס נוח ועבה)
 * המתאים הן לחותך האודיו והן לחותך הוידאו.
 */
public class RangeWaveformTrimView extends View {

    public interface OnRangeChangedListener {
        void onRangeChanged(long startMs, long endMs, boolean movingStart);
    }

    private long durationMs = 1000L;
    private long startMs = 0L;
    private long endMs = 1000L;
    private long minSpanMs = 500L;
    private int accentColor = Color.parseColor("#3B82F6");
    private int trackBgColor = Color.parseColor("#1E293B");
    private OnRangeChangedListener listener;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint activePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gripPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int activeThumb = 0; // 0 none, 1 start, 2 end

    public RangeWaveformTrimView(Context context) {
        super(context);
        setLayoutDirection(LAYOUT_DIRECTION_LTR);
        trackPaint.setColor(trackBgColor);
        wavePaint.setColor(Color.parseColor("#475569"));
        activePaint.setColor(Color.argb(95, 59, 130, 246));
        handlePaint.setColor(accentColor);
        gripPaint.setColor(Color.WHITE);
        gripPaint.setStrokeWidth(4f);
        gripPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setAccentColor(int color) {
        this.accentColor = color;
        activePaint.setColor(Color.argb(95, Color.red(color), Color.green(color), Color.blue(color)));
        handlePaint.setColor(color);
        invalidate();
    }

    public void setOnRangeChangedListener(OnRangeChangedListener listener) {
        this.listener = listener;
    }

    public void setDurationAndRange(long durationMs, long startMs, long endMs) {
        this.durationMs = Math.max(100L, durationMs);
        this.startMs = Math.max(0L, Math.min(this.durationMs - minSpanMs, startMs));
        this.endMs = Math.max(this.startMs + minSpanMs, Math.min(this.durationMs, endMs));
        invalidate();
    }

    public long getStartMs() {
        return startMs;
    }

    public long getEndMs() {
        return endMs;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredH = Math.round(64 * getResources().getDisplayMetrics().density);
        setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec),
                resolveSize(desiredH, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float padH = 22 * getResources().getDisplayMetrics().density;
        float w = getWidth() - padH * 2f;
        float h = getHeight();
        if (w <= 20f) return;

        float top = h * 0.16f;
        float bottom = h * 0.84f;
        RectF trackRect = new RectF(padH, top, padH + w, bottom);
        canvas.drawRoundRect(trackRect, 18f, 18f, trackPaint);

        // גל צורה אחיד ונעים לעין
        int bars = 46;
        float barStep = w / bars;
        float centerY = (top + bottom) * 0.5f;
        float maxAmp = (bottom - top) * 0.36f;
        for (int i = 0; i < bars; i++) {
            float norm = 0.25f + 0.75f * (float) Math.abs(Math.sin(i * 0.63f + 0.4f));
            float x = padH + i * barStep + barStep * 0.4f;
            canvas.drawLine(x, centerY - norm * maxAmp, x, centerY + norm * maxAmp, wavePaint);
        }

        float xStart = padH + (startMs * 1f / durationMs) * w;
        float xEnd = padH + (endMs * 1f / durationMs) * w;

        RectF activeRect = new RectF(xStart, top, xEnd, bottom);
        canvas.drawRoundRect(activeRect, 12f, 12f, activePaint);

        drawThumb(canvas, xStart, top, bottom);
        drawThumb(canvas, xEnd, top, bottom);
    }

    private void drawThumb(Canvas canvas, float cx, float top, float bottom) {
        float hw = 10 * getResources().getDisplayMetrics().density;
        RectF r = new RectF(cx - hw, top - 4f, cx + hw, bottom + 4f);
        canvas.drawRoundRect(r, 12f, 12f, handlePaint);
        float midY = (top + bottom) * 0.5f;
        canvas.drawLine(cx, midY - 12f, cx, midY + 12f, gripPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled() || durationMs <= 0) return false;
        float padH = 22 * getResources().getDisplayMetrics().density;
        float w = getWidth() - padH * 2f;
        if (w <= 20f) return false;

        float x = event.getX();
        float xStart = padH + (startMs * 1f / durationMs) * w;
        float xEnd = padH + (endMs * 1f / durationMs) * w;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                float distStart = Math.abs(x - xStart);
                float distEnd = Math.abs(x - xEnd);
                activeThumb = (distStart <= distEnd) ? 1 : 2;
                return true;
            case MotionEvent.ACTION_MOVE:
                float frac = Math.max(0f, Math.min(1f, (x - padH) / w));
                long targetMs = Math.round(frac * durationMs);
                if (activeThumb == 1) {
                    startMs = Math.max(0L, Math.min(endMs - minSpanMs, targetMs));
                } else if (activeThumb == 2) {
                    endMs = Math.min(durationMs, Math.max(startMs + minSpanMs, targetMs));
                }
                invalidate();
                if (listener != null) {
                    listener.onRangeChanged(startMs, endMs, activeThumb == 1);
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                activeThumb = 0;
                return true;
        }
        return super.onTouchEvent(event);
    }
}
