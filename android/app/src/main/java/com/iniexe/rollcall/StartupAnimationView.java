package com.iniexe.rollcall;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.os.Build;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;

/** Theme-aware Rollcall identity intro with a small animated attendance motif. */
final class StartupAnimationView extends View {
    private static final long INTRO_DURATION = 3000L;
    private static final long TRANSITION_DURATION = 520L;
    private static final float EXIT_START = INTRO_DURATION / (float) (INTRO_DURATION + TRANSITION_DURATION);
    private static final float LOGO_SIZE = 526f;
    private static final RectF LOGO_BOUNDS = new RectF(0, 0, LOGO_SIZE, LOGO_SIZE);
    static final PathInterpolator TRANSITION_INTERPOLATOR = new PathInterpolator(.22f, 0f, .2f, 1f);

    private static final class BrushStroke {
        final PathMeasure measure;
        final float start, end, radius, seed;
        BrushStroke(Path path, float start, float end, float radius, float seed) {
            measure = new PathMeasure(path, false);
            this.start = start; this.end = end; this.radius = radius; this.seed = seed;
        }
    }

    interface TransitionListener {
        void onStart();
        void onProgress(float progress);
    }

    private final AppPalette palette;
    private final RollcallLogo.Mark logoMark;
    private final Bitmap logoBitmap;
    private final Paint logoPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path revealMask = new Path();
    private final float[] brushPosition = new float[2];
    private final float[] brushTangent = new float[2];
    private final BrushStroke[] brushStrokes = createBrushStrokes();
    private final TransitionListener transitionListener;
    private final Runnable onFinished;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Typeface wordmarkTypeface;
    private final Typeface uiTypeface = Typeface.create("monospace", Typeface.NORMAL);
    private final ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
    private final boolean nightCity;
    private float progress;
    private boolean finished;
    private boolean exitStarted;

    StartupAnimationView(Context context, AppPalette palette, TransitionListener transitionListener, Runnable onFinished) {
        super(context);
        this.palette = palette;
        logoMark = RollcallLogo.themedMark(context, palette);
        logoBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.rollcall_animation_brush);
        int logoColor = "Black Monochrome".equals(palette.name) ? palette.ink
                : "Night City 2077".equals(palette.name) ? palette.green : palette.highlight;
        logoPaint.setColorFilter(new PorterDuffColorFilter(logoColor, PorterDuff.Mode.SRC_IN));
        nightCity = "Night City 2077".equals(palette.name);
        this.transitionListener = transitionListener;
        this.onFinished = onFinished;
        Typeface selectedTypeface = Typeface.create("monospace", Typeface.BOLD);
        try { selectedTypeface = Typeface.createFromAsset(context.getAssets(), "fonts/JetBrainsMono-Bold.ttf"); }
        catch (RuntimeException ignored) { }
        if ("Night City 2077".equals(palette.name)) {
            try {
                selectedTypeface = Typeface.createFromAsset(context.getAssets(), "fonts/Cyberpunk-Regular.ttf");
            } catch (RuntimeException ignored) {
                // Fall back to the system typeface if the theme font is unavailable.
            }
        }
        wordmarkTypeface = selectedTypeface;
        setBackgroundColor(palette.canvas);
        setContentDescription("Rollcall startup animation. Tap to continue.");
        setFocusable(true);
        animator.setDuration(INTRO_DURATION + TRANSITION_DURATION);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(value -> {
            progress = (float) value.getAnimatedValue();
            if (progress >= EXIT_START) startExit();
            if (exitStarted) {
                float transition = TRANSITION_INTERPOLATOR.getInterpolation(clamp((progress - EXIT_START) / (1f - EXIT_START)));
                float height = getHeight() > 0 ? getHeight() : getResources().getDisplayMetrics().heightPixels;
                setTranslationY(-height * transition);
                transitionListener.onProgress(transition);
            }
            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) { finish(); }
        });
    }

    void start() {
        if (Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled()) {
            finish();
            return;
        }
        if (!animator.isStarted()) animator.start();
    }

    void stop() {
        if (animator.isRunning()) animator.cancel();
        finished = true;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float sceneProgress = clamp(progress / EXIT_START);
        if (nightCity) { drawNightCity(canvas, sceneProgress); return; }
        float width = getWidth();
        float height = getHeight();
        float cx = width * .5f;
        float cy = height * .5f;
        canvas.drawColor(palette.canvas);
        drawLogoEntrance(canvas, cx, cy - dp(82), sceneProgress);
        drawAnimatedWordmark(canvas, "Rollcall", cx, cy + dp(13), 48,
                palette.ink, width - dp(48), sceneProgress);
        float caption = easeOut(clamp((sceneProgress - .30f) / .12f));
        drawCenteredText(canvas, "Make every class count.", cx, cy + dp(48),
                12, uiTypeface, palette.muted, caption, width - dp(48));
    }

    private void drawNightCity(Canvas canvas, float sceneProgress) {
        float w = getWidth(), h = getHeight(), cx = w / 2f, cy = h * .48f;
        canvas.drawColor(palette.canvas);
        paint.setStyle(Paint.Style.FILL);
        float reveal = easeOut(clamp(sceneProgress / .28f));
        // Low-contrast city architecture stays behind the animated name.
        paint.setColor(withAlpha(palette.green, .09f * reveal));
        float unit = w / 15f;
        for (int i = 0; i < 15; i++) {
            float tower = dp(25 + (i * 31 % 85)) * reveal;
            canvas.drawRect(i * unit, h * .79f - tower,
                    i * unit + unit * .65f, h * .79f, paint);
        }
        drawLogoEntrance(canvas, cx, cy - dp(82), sceneProgress);
        float rail = easeOut(clamp((sceneProgress - .04f) / .22f));
        paint.setColor(withAlpha(palette.green, .65f));
        canvas.drawRect(dp(24), cy - dp(135), dp(24) + (w - dp(48)) * rail,
                cy - dp(134), paint);
        float settle = easeOut(clamp((sceneProgress - .18f) / .38f));
        float split = dp(5) * (1f - settle);
        // Chromatic offsets converge once; no flashing or random frame changes.
        if (split > .2f) {
            canvas.save();
            canvas.clipRect(0, cy - dp(45), w, cy - dp(9));
            drawAnimatedWordmark(canvas, "ROLLCALL", cx - split, cy + dp(10),
                    46, palette.green, w - dp(48), sceneProgress);
            canvas.restore();
            canvas.save();
            canvas.clipRect(0, cy - dp(9), w, cy + dp(22));
            drawAnimatedWordmark(canvas, "ROLLCALL", cx + split, cy + dp(10),
                    46, palette.red, w - dp(48), sceneProgress);
            canvas.restore();
        }
        drawAnimatedWordmark(canvas, "ROLLCALL", cx, cy + dp(10),
                46, palette.ink, w - dp(48), sceneProgress);
        float caption = easeOut(clamp((sceneProgress - .36f) / .13f));
        drawCenteredText(canvas, "N I G H T   C I T Y   /   2 0 7 7", cx, cy + dp(48),
                10, uiTypeface, palette.green, caption, w - dp(48));
        float load = clamp((sceneProgress - .10f) / .43f);
        for (int i = 0; i < 20; i++) {
            paint.setColor(withAlpha(i < load * 20 ? palette.highlight : palette.border, .85f));
            float x = cx - dp(59) + dp(6) * i;
            canvas.drawRect(x, cy + dp(73), x + dp(3), cy + dp(76), paint);
        }
    }

    private void drawLogoEntrance(Canvas canvas, float cx, float centerY, float progress) {
        if (logoBitmap == null) {
            RollcallLogo.draw(logoMark, canvas, cx - dp(44), centerY - dp(44), dp(88), 1f);
            return;
        }
        float size = dp(88);
        float reveal = clamp((progress - .80f) / .10f);
        buildRevealMask(progress);
        canvas.save();
        canvas.translate(cx - size / 2f, centerY - size / 2f);
        canvas.scale(size / LOGO_SIZE, size / LOGO_SIZE);
        canvas.save();
        canvas.clipPath(revealMask);
        canvas.drawBitmap(logoBitmap, null, LOGO_BOUNDS, logoPaint);
        canvas.restore();
        if (reveal > 0f) {
            logoPaint.setAlpha(Math.round(255f * easeOut(reveal)));
            canvas.drawBitmap(logoBitmap, null, LOGO_BOUNDS, logoPaint);
            logoPaint.setAlpha(255);
        }
        canvas.restore();
    }

    private static BrushStroke[] createBrushStrokes() {
        Path leftStem = new Path(); leftStem.moveTo(105, 106); leftStem.lineTo(105, 424);
        Path leftTop = new Path(); leftTop.moveTo(105, 132); leftTop.lineTo(166, 132);
        Path leftBottom = new Path(); leftBottom.moveTo(104, 411); leftBottom.lineTo(151, 411);
        Path rStem = new Path(); rStem.moveTo(205, 151); rStem.lineTo(205, 355);
        Path rBowl = new Path();
        rBowl.moveTo(205, 152);
        rBowl.cubicTo(260, 137, 333, 145, 340, 205);
        rBowl.cubicTo(347, 255, 300, 280, 215, 272);
        Path rLeg = new Path(); rLeg.moveTo(233, 273); rLeg.lineTo(325, 388);
        Path rightTop = new Path(); rightTop.moveTo(383, 132); rightTop.lineTo(421, 132);
        Path rightStem = new Path(); rightStem.moveTo(422, 133); rightStem.lineTo(422, 410);
        Path rightBottom = new Path(); rightBottom.moveTo(418, 411); rightBottom.lineTo(385, 411);
        return new BrushStroke[] {
                new BrushStroke(leftStem, .067f, .200f, 27f, .3f),
                new BrushStroke(leftTop, .200f, .233f, 27f, 1.1f),
                new BrushStroke(leftBottom, .233f, .267f, 27f, 2.4f),
                new BrushStroke(rStem, .267f, .367f, 29f, 3.2f),
                new BrushStroke(rBowl, .367f, .500f, 30f, 4.1f),
                new BrushStroke(rLeg, .500f, .600f, 30f, 5.3f),
                new BrushStroke(rightTop, .600f, .650f, 27f, 6.2f),
                new BrushStroke(rightStem, .650f, .750f, 29f, 7.4f),
                new BrushStroke(rightBottom, .750f, .800f, 27f, 8.3f)
        };
    }

    private void buildRevealMask(float progress) {
        revealMask.rewind();
        for (BrushStroke stroke : brushStrokes) {
            float local = clamp((progress - stroke.start) / (stroke.end - stroke.start));
            if (local <= 0f) continue;
            local = local * local * (3f - 2f * local);
            float length = stroke.measure.getLength() * local;
            for (float distance = 0f; distance < length; distance += 5f) {
                if (!stroke.measure.getPosTan(distance, brushPosition, brushTangent)) break;
                float pressure = .91f + .09f * (float) Math.sin(distance * .08f + stroke.seed)
                        + .04f * (float) Math.sin(distance * .19f + stroke.seed * 1.7f);
                revealMask.addCircle(brushPosition[0], brushPosition[1], stroke.radius * pressure, Path.Direction.CW);
            }
            if (length > 0f && stroke.measure.getPosTan(length, brushPosition, brushTangent))
                revealMask.addCircle(brushPosition[0], brushPosition[1], stroke.radius, Path.Direction.CW);
        }
    }

    private void drawCenteredText(Canvas canvas, String text, float cx, float baseline, float size,
                                  Typeface face, int color, float alpha, float maxWidth) {
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(face);
        paint.setTextSize(dp(size));
        float measured = paint.measureText(text);
        if (measured > maxWidth) paint.setTextSize(paint.getTextSize() * maxWidth / measured);
        paint.setColor(withAlpha(color, alpha));
        canvas.drawText(text, cx, baseline, paint);
    }

    private void drawAnimatedWordmark(Canvas canvas, String word, float cx, float baseline,
                                      float size, int color, float maxWidth, float progress) {
        paint.setTypeface(wordmarkTypeface);
        paint.setTextSize(dp(size));
        paint.setTextAlign(Paint.Align.LEFT);
        float[] widths = new float[word.length()];
        paint.getTextWidths(word, widths);
        float gap = dp(.7f);
        float totalWidth = gap * (word.length() - 1);
        for (float width : widths) totalWidth += width;
        if (totalWidth > maxWidth) {
            float fit = maxWidth / totalWidth;
            paint.setTextSize(paint.getTextSize() * fit);
            for (int i = 0; i < widths.length; i++) widths[i] *= fit;
            gap *= fit;
            totalWidth = maxWidth;
        }

        float x = cx - totalWidth * .5f;
        for (int i = 0; i < word.length(); i++) {
            float amount = easeOut(clamp((progress - (.04f + i * .025f)) / .20f));
            float letterWidth = widths[i];
            if (amount > 0f) {
                float letterCenter = x + letterWidth * .5f;
                canvas.save();
                if (nightCity) {
                    // Alternate glyphs dock from opposite directions, then unskew.
                    float remaining = 1f - amount;
                    canvas.translate((i % 2 == 0 ? -1 : 1) * dp(15f) * remaining,
                            (i % 2 == 0 ? 1 : -1) * dp(20f) * remaining);
                    canvas.translate(letterCenter, baseline);
                    canvas.skew(-.28f * remaining, 0);
                    canvas.translate(-letterCenter, -baseline);
                } else canvas.translate(0f, dp(10f) * (1f - amount));
                float letterScale = .94f + .06f * amount;
                canvas.scale(letterScale, letterScale, letterCenter, baseline);
                paint.setColor(withAlpha(color, amount));
                canvas.drawText(word, i, i + 1, x, baseline, paint);
                canvas.restore();
            }
            x += letterWidth + gap;
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            performClick();
            finish();
            return true;
        }
        return true;
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override protected void onDetachedFromWindow() {
        // Detaching cancels the scene; it must not launch a reveal on the old window.
        finished = true;
        animator.removeAllListeners();
        if (animator.isRunning()) animator.cancel();
        super.onDetachedFromWindow();
    }

    private void finish() {
        if (finished) return;
        finished = true;
        startExit();
        setTranslationY(-getHeight());
        transitionListener.onProgress(1f);
        if (animator.isRunning()) animator.cancel();
        onFinished.run();
    }

    private void startExit() {
        if (exitStarted) return;
        exitStarted = true;
        transitionListener.onStart();
    }

    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
    private static float clamp(float value) { return Math.max(0f, Math.min(1f, value)); }
    private static float easeOut(float x) { float t = 1f - clamp(x); return 1f - t * t * t; }
    private static int withAlpha(int color, float alpha) {
        return (color & 0x00ffffff) | (Math.round(((color >>> 24) & 0xff) * alpha) << 24);
    }
}
