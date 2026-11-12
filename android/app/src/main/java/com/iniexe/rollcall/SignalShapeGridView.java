package com.iniexe.rollcall;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Quiet shape grid behind the onboarding sign-in controls. */
final class SignalShapeGridView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ValueAnimator drift = ValueAnimator.ofFloat(0f, 1f);
    private final AppPalette palette;
    private float phase, pointerX = -10000f, pointerY = -10000f;

    SignalShapeGridView(Context context, AppPalette palette) {
        super(context);
        this.palette = palette;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        drift.setDuration(3600);
        drift.setRepeatCount(ValueAnimator.INFINITE);
        drift.addUpdateListener(a -> { phase = (float) a.getAnimatedValue(); invalidate(); });
    }

    void setPointer(float x, float y) { pointerX = x; pointerY = y; invalidate(); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float unit = getResources().getDisplayMetrics().density;
        float spacing = 38f * unit;
        for (float y = spacing * .5f; y < getHeight(); y += spacing) {
            for (float x = spacing * .5f; x < getWidth(); x += spacing) {
                float distance = (float) Math.hypot(x - pointerX, y - pointerY);
                float near = Math.max(0f, 1f - distance / (120f * unit));
                float size = (4f + 5f * near) * unit;
                float offset = (float) Math.sin(phase * Math.PI * 2 + x / (130f * unit)) * 2f * unit;
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(unit * (near > 0 ? 1.6f : 1f));
                int base = near > 0 ? palette.highlight : palette.ink;
                paint.setColor((Math.round((.16f + near * .65f) * 255) << 24) | (base & 0xFFFFFF));
                if (((int) (x / spacing) + (int) (y / spacing)) % 4 == 1)
                    canvas.drawCircle(x, y + offset, size * .48f, paint);
                else canvas.drawRect(x - size / 2, y + offset - size / 2,
                        x + size / 2, y + offset + size / 2, paint);
            }
        }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (android.os.Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled()) drift.start();
    }

    @Override protected void onDetachedFromWindow() { drift.cancel(); super.onDetachedFromWindow(); }
}
