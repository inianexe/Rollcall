package com.iniexe.rollcall;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.view.View;

final class ForecastPlotView extends View {
    private final SignalDrawing draw = new SignalDrawing(this);
    private final Path path = new Path();
    private final int recordedColor, projectedColor, muted;
    private ForecastSeries data;
    private double[] previous = new double[0];
    private float progress = 1;
    private ValueAnimator animator;

    ForecastPlotView(Context context, int recordedColor, int projectedColor, int muted) {
        super(context);
        this.recordedColor = recordedColor;
        this.projectedColor = projectedColor;
        this.muted = muted;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setSeries(ForecastSeries next) {
        double[] visible = data == null ? new double[0] : new double[data.points.length];
        for (int i = 0; i < visible.length; i++) visible[i] = value(i);
        stop();
        previous = visible;
        data = next;
        progress = 1;
        if (next != null && previous.length > 0 && isShown() && NativeMotion.enabled(getContext())) {
            progress = 0;
            animator = ValueAnimator.ofFloat(0, 1);
            animator.setDuration(SignalDrawing.CHART);
            animator.addUpdateListener(a -> { progress = (float) a.getAnimatedValue(); invalidate(); });
            animator.start();
        }
        invalidate();
    }

    private double value(int i) {
        double target = data.points[i];
        return i < previous.length && !Double.isNaN(previous[i]) && !Double.isNaN(target)
                ? previous[i] + (target - previous[i]) * progress : target;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float left = draw.dp(12), right = getWidth() - draw.dp(12);
        float top = draw.dp(12), bottom = getHeight() - draw.dp(12), height = bottom - top;
        draw.line.setColor(muted); draw.line.setPathEffect(null);
        canvas.drawLine(left, top, left, bottom, draw.line);
        canvas.drawLine(left, bottom, right, bottom, draw.line);
        if (data == null) return;
        float targetY = bottom - height * data.target / 100;
        draw.line.setStrokeWidth(draw.dp(1));
        canvas.drawLine(left, targetY, right, targetY, draw.line);
        draw.line.setStrokeWidth(draw.dp(2));
        path.rewind();
        boolean started = false;
        for (int i = 0; i < data.points.length; i++) {
            double val = value(i);
            if (Double.isNaN(val)) continue;
            float x = left + (right - left) * i / Math.max(1, data.count);
            float y = bottom - height * (float) val / 100;
            if (!started) { path.moveTo(x, y); started = true; } else path.lineTo(x, y);
        }
        draw.line.setColor(projectedColor); draw.line.setPathEffect(draw.projection);
        canvas.drawPath(path, draw.line);
        draw.line.setPathEffect(null);
        draw.marker.setColor(recordedColor);
        if (!Double.isNaN(data.recorded)) canvas.drawCircle(left, bottom - height * (float) data.recorded / 100, draw.dp(5), draw.marker);
        for (int i = 0; i < data.points.length; i++) {
            double val = value(i);
            if (Double.isNaN(val)) continue;
            float x = left + (right - left) * i / Math.max(1, data.count);
            float y = bottom - height * (float) val / 100;
            canvas.drawCircle(x, y, draw.dp(i == data.count ? 5 : 2), draw.line);
        }
    }

    private void stop() { if (animator != null) { animator.cancel(); animator = null; } }
    @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }
    @Override protected void onVisibilityChanged(View changed, int visibility) {
        super.onVisibilityChanged(changed, visibility);
        if (!isShown()) { stop(); progress = 1; }
    }
    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility != VISIBLE) { stop(); progress = 1; }
    }
}
