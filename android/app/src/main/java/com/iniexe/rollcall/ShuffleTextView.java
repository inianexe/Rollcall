package com.iniexe.rollcall;

import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import java.util.Random;

/** A bounded character reveal inspired by React Bits Shuffle. */
final class ShuffleTextView extends TextView {
    private static final char[] GLYPHS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    private final Random random = new Random();
    private ValueAnimator animator;
    private CharSequence finalText = "";

    ShuffleTextView(Context context) { super(context); }
    ShuffleTextView(Context context, AttributeSet attrs) { super(context, attrs); }

    void setShuffledText(CharSequence value) {
        CharSequence target = value == null ? "" : value;
        if (target.toString().equals(finalText.toString())) return;
        finalText = target;
        if (animator != null) animator.cancel();
        if (!NativeMotion.enabled(getContext()) || !isAttachedToWindow() || !isShown()) {
            setText(target);
            return;
        }
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(520);
        animator.setInterpolator(new DecelerateInterpolator(2f));
        animator.addUpdateListener(a -> {
            float progress = (float) a.getAnimatedValue();
            if (progress >= .995f) { setText(finalText); return; }
            char[] letters = finalText.toString().toCharArray();
            for (int i = 0; i < letters.length; i++) {
                if (!Character.isLetterOrDigit(letters[i])) continue;
                float threshold = .25f + .7f * i / Math.max(1, letters.length);
                if (progress < threshold) letters[i] = GLYPHS[random.nextInt(GLYPHS.length)];
            }
            setText(new String(letters));
        });
        animator.start();
    }

    String settledText() { return finalText.toString(); }

    @Override protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        setText(finalText);
        super.onDetachedFromWindow();
    }
}
