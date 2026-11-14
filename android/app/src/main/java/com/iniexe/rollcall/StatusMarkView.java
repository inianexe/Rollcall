package com.iniexe.rollcall;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** The React Bits idle ring, running arc, check and failure cross. */
final class StatusMarkView extends View {
    enum State { PENDING, RUNNING, DONE, FAILED }
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF circle = new RectF();
    private final Path mark = new Path();
    private final int ink, success, error;
    private State state;
    private ValueAnimator morph, spinner;
    private float arc = .3f, rotation;

    StatusMarkView(Context context, int ink, int success, int error) {
        super(context);
        this.ink = ink; this.success = success; this.error = error;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setState(State.PENDING);
    }

    void setState(State next) {
        if (next == state && (next != State.RUNNING || spinner != null)) return;
        state = next;
        if (morph != null) morph.cancel();
        if (spinner != null) { spinner.cancel(); spinner = null; }
        float target = next == State.RUNNING ? .68f : next == State.PENDING ? .3f : 1f;
        if (NativeMotion.enabled(getContext()) && isAttachedToWindow()) {
            morph = ValueAnimator.ofFloat(arc, target);
            morph.setDuration(300);
            morph.setInterpolator(new DecelerateInterpolator());
            morph.addUpdateListener(a -> { arc = (float) a.getAnimatedValue(); invalidate(); });
            morph.start();
        } else arc = target;
        if (next == State.RUNNING && isAttachedToWindow() && NativeMotion.enabled(getContext())) {
            spinner = ValueAnimator.ofFloat(0f, 360f);
            spinner.setDuration(1100);
            spinner.setRepeatCount(ValueAnimator.INFINITE);
            spinner.addUpdateListener(a -> { rotation = (float) a.getAnimatedValue(); invalidate(); });
            spinner.start();
        } else rotation = 0f;
        setContentDescription(next == State.RUNNING ? "Refresh in progress" : next == State.DONE
                ? "Refresh completed" : next == State.FAILED ? "Refresh failed" : "Refresh pending");
        invalidate();
    }

    State state() { return state; }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); if (state == State.RUNNING) setState(state); }
    @Override protected void onDetachedFromWindow() {
        if (morph != null) morph.cancel();
        if (spinner != null) { spinner.cancel(); spinner = null; }
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float d = Math.min(getWidth(), getHeight());
        float radius = d * .34f;
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, d * .07f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(ink);
        paint.setAlpha(55);
        circle.set(cx - radius, cy - radius, cx + radius, cy + radius);
        canvas.drawArc(circle, 0, 360, false, paint);
        paint.setColor(state == State.DONE ? success : state == State.FAILED ? error : ink);
        paint.setAlpha(255);
        if (state == State.PENDING) {
            for (int i = 0; i < 8; i++) canvas.drawArc(circle, i * 45f - 90f, 13f, false, paint);
        } else {
            canvas.drawArc(circle, rotation - 90f, 360f * arc, false, paint);
        }
        if (state == State.DONE || state == State.FAILED) {
            mark.reset();
            if (state == State.DONE) {
                mark.moveTo(cx - radius * .48f, cy);
                mark.lineTo(cx - radius * .1f, cy + radius * .38f);
                mark.lineTo(cx + radius * .55f, cy - radius * .42f);
            } else {
                mark.moveTo(cx - radius * .34f, cy - radius * .34f);
                mark.lineTo(cx + radius * .34f, cy + radius * .34f);
                mark.moveTo(cx + radius * .34f, cy - radius * .34f);
                mark.lineTo(cx - radius * .34f, cy + radius * .34f);
            }
            canvas.drawPath(mark, paint);
        }
    }
}
