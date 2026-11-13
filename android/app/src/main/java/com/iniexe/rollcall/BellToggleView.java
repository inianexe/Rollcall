package com.iniexe.rollcall;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Bell swing and clipped pill reveal modelled on React Bits Bell Toggle. */
final class BellToggleView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path bell = new Path();
    private final RectF pill = new RectF();
    private final int offFill, onFill, offInk, onInk;
    private boolean checked;
    private float reveal, swing;
    private ValueAnimator revealAnimator, ringAnimator;
    private Runnable toggle;
    private final String offLabel = "Class reminders off";
    private final String onLabel = "Class reminders on";

    BellToggleView(Context context, int offFill, int onFill, int offInk, int onInk) {
        super(context);
        this.offFill = offFill; this.onFill = onFill;
        this.offInk = offInk; this.onInk = onInk;
        paint.setTextSize(getResources().getDisplayMetrics().scaledDensity * 14f);
        paint.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE,
                android.graphics.Typeface.BOLD));
        setClickable(true);
        setFocusable(true);
        setOnClickListener(v -> { if (toggle != null) toggle.run(); });
        describe();
    }

    void onToggle(Runnable action) { toggle = action; }
    void setChecked(boolean value) {
        if (checked == value) return;
        checked = value;
        if (revealAnimator != null) revealAnimator.cancel();
        if (ringAnimator != null) ringAnimator.cancel();
        if (NativeMotion.enabled(getContext()) && isAttachedToWindow()) {
            revealAnimator = ValueAnimator.ofFloat(reveal, value ? 1f : 0f);
            revealAnimator.setDuration(300);
            revealAnimator.setInterpolator(new DecelerateInterpolator(2f));
            revealAnimator.addUpdateListener(a -> { reveal = (float) a.getAnimatedValue(); invalidate(); });
            revealAnimator.start();
            if (value) {
                ringAnimator = ValueAnimator.ofFloat(0f, 1f);
                ringAnimator.setDuration(820);
                ringAnimator.addUpdateListener(a -> {
                    float t = (float) a.getAnimatedValue();
                    swing = 17f * (float) (Math.sin(t * Math.PI * 5) * Math.pow(1 - t, 1.2));
                    invalidate();
                });
                ringAnimator.start();
            }
        } else { reveal = value ? 1f : 0f; swing = 0f; invalidate(); }
        describe();
    }

    boolean isChecked() { return checked; }
    private void describe() { setContentDescription((checked ? onLabel : offLabel) + ". Double tap to toggle."); }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int desiredWidth = Math.round(Math.max(paint.measureText(offLabel), paint.measureText(onLabel))
                + dp(74));
        setMeasuredDimension(resolveSize(desiredWidth, widthSpec), resolveSize(dp(52), heightSpec));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float h = getHeight(), w = getWidth();
        float offWidth = Math.min(w, paint.measureText(offLabel) + dp(70));
        float currentWidth = offWidth + (w - offWidth) * reveal;
        pill.set(0, 0, currentWidth, h);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(NativeMotion.color(offFill, onFill, reveal));
        canvas.drawRoundRect(pill, h / 2f, h / 2f, paint);
        paint.setColor(NativeMotion.color(offInk, onInk, reveal));
        paint.setStrokeWidth(dp(2));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStyle(Paint.Style.STROKE);
        float cx = dp(27), cy = h / 2f;
        canvas.save();
        canvas.rotate(swing, cx, cy - dp(10));
        bell.reset();
        bell.moveTo(cx - dp(8), cy + dp(5));
        bell.quadTo(cx - dp(6), cy + dp(2), cx - dp(6), cy - dp(4));
        bell.cubicTo(cx - dp(6), cy - dp(13), cx + dp(6), cy - dp(13), cx + dp(6), cy - dp(4));
        bell.quadTo(cx + dp(6), cy + dp(2), cx + dp(8), cy + dp(5));
        bell.close();
        canvas.drawPath(bell, paint);
        canvas.drawCircle(cx, cy + dp(8), dp(1), paint);
        canvas.restore();
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.LEFT);
        float baseline = h / 2f - (paint.ascent() + paint.descent()) / 2f;
        int ink = NativeMotion.color(offInk, onInk, reveal);
        paint.setColor(ink);
        paint.setAlpha(Math.round(255 * (1f - reveal)));
        canvas.drawText(offLabel, dp(49), baseline, paint);
        paint.setAlpha(Math.round(255 * reveal));
        canvas.drawText(onLabel, dp(49), baseline, paint);
        paint.setAlpha(255);
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override protected void onDetachedFromWindow() {
        if (revealAnimator != null) revealAnimator.cancel();
        if (ringAnimator != null) ringAnimator.cancel();
        swing = 0f;
        super.onDetachedFromWindow();
    }
}
