package com.iniexe.rollcall;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** A small animated map of the first successful Rollcall flow, without sample attendance data. */
final class OnboardingFlowView extends View {
    private final AppPalette palette;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ValueAnimator packet = ValueAnimator.ofFloat(0f, 1f);
    private float progress;

    OnboardingFlowView(Context context, AppPalette palette) {
        super(context);
        this.palette = palette;
        setContentDescription("Connect MyCamu, sync your academic data, and see it in Rollcall.");
        packet.setDuration(1800L);
        packet.setRepeatCount(ValueAnimator.INFINITE);
        packet.setInterpolator(new LinearInterpolator());
        packet.addUpdateListener(value -> {
            progress = (float) value.getAnimatedValue();
            invalidate();
        });
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        float width = getWidth();
        float cardWidth = Math.min(104f * density, width * .29f);
        float cardHeight = 48f * density;
        float cardTop = 15f * density;
        float centerY = cardTop + cardHeight * .5f;
        float[] centers = {width * .17f, width * .5f, width * .83f};
        String[] labels = {"MYCAMU", "ROLLCALL", "YOUR WEEK"};
        int[] accents = {palette.green, palette.highlight, palette.amber};

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f * density);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(palette.border);
        Path route = new Path();
        route.moveTo(centers[0], centerY);
        route.lineTo(centers[1], centerY);
        route.lineTo(centers[2], centerY);
        canvas.drawPath(route, paint);

        for (int i = 0; i < centers.length; i++) {
            float left = centers[i] - cardWidth * .5f;
            RectF card = new RectF(left, cardTop, left + cardWidth, cardTop + cardHeight);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(palette.surface);
            canvas.drawRoundRect(card, 12f * density, 12f * density, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1f * density);
            paint.setColor(palette.border);
            canvas.drawRoundRect(card, 12f * density, 12f * density, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.create("monospace", Typeface.BOLD));
            paint.setTextSize(9f * getResources().getDisplayMetrics().scaledDensity);
            paint.setColor(i == 1 ? palette.hero : palette.ink);
            canvas.drawText(labels[i], centers[i], centerY + (paint.getTextSize() * .34f), paint);
            paint.setColor(accents[i]);
            canvas.drawCircle(centers[i], card.bottom + 18f * density, 3f * density, paint);
        }

        float packetX = centers[0] + (centers[2] - centers[0]) * progress;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(palette.hero);
        paint.setShadowLayer(7f * density, 0f, 0f, palette.hero);
        canvas.drawCircle(packetX, centerY, 3.5f * density, paint);
        paint.clearShadowLayer();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!packet.isStarted()) packet.start();
    }

    @Override protected void onDetachedFromWindow() {
        packet.cancel();
        super.onDetachedFromWindow();
    }
}
