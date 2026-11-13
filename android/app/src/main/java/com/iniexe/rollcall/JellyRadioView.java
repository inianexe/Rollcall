package com.iniexe.rollcall;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.drawable.GradientDrawable;

/** React Bits style selection with spring swell and neighbour displacement. */
final class JellyRadioView extends LinearLayout {
    interface OnSelection { void selected(int index); }
    private final TextView[] chips;
    private final int active, inactive, activeText, inactiveText;
    private ValueAnimator animator;
    private OnSelection listener;
    private int selected;

    JellyRadioView(Context context, String[] labels, int selected, int active, int inactive,
                   int activeText, int inactiveText, Typeface typeface) {
        super(context);
        setOrientation(HORIZONTAL);
        this.active = active; this.inactive = inactive;
        this.activeText = activeText; this.inactiveText = inactiveText;
        this.chips = new TextView[labels.length];
        this.selected = -1;
        setClipChildren(false);
        setClipToPadding(false);
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView chip = new TextView(context);
            chip.setText(labels[i]);
            chip.setTextSize(11);
            chip.setTypeface(typeface, Typeface.BOLD);
            chip.setGravity(Gravity.CENTER);
            chip.setMinHeight(dp(48));
            chip.setPadding(dp(6), dp(8), dp(6), dp(8));
            chip.setFocusable(true);
            chip.setOnClickListener(v -> select(index, true));
            LayoutParams lp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1);
            lp.leftMargin = i == 0 ? 0 : dp(4);
            lp.rightMargin = i == labels.length - 1 ? 0 : dp(4);
            addView(chip, lp);
            chips[i] = chip;
        }
        select(selected, false);
    }

    void onSelection(OnSelection next) { listener = next; }
    TextView chip(int index) { return chips[index]; }
    int selection() { return selected; }
    void setLabel(int index, String label) { chips[index].setText(label); updateA11y(); }

    void select(int index, boolean fromUser) {
        if (index < 0 || index >= chips.length) return;
        if (selected == index && !fromUser) return;
        boolean changed = selected != index;
        selected = index;
        if (animator != null) animator.cancel();
        for (int i = 0; i < chips.length; i++) {
            chips[i].setSelected(i == index);
            chips[i].setTextColor(i == index ? activeText : inactiveText);
            GradientDrawable shape = new GradientDrawable();
            shape.setColor(i == index ? active : inactive);
            shape.setCornerRadius(dp(10));
            chips[i].setBackground(shape);
        }
        updateA11y();
        if (fromUser && changed && listener != null) listener.selected(index);
        if (!fromUser || !changed || !NativeMotion.enabled(getContext()) || !isShown()) {
            position(1f);
            return;
        }
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(440);
        animator.setInterpolator(new OvershootInterpolator(.8f));
        animator.addUpdateListener(a -> position((float) a.getAnimatedValue()));
        animator.start();
    }

    private void position(float progress) {
        for (int i = 0; i < chips.length; i++) {
            float chosen = i == selected ? 1f : 0f;
            chips[i].setScaleX(1f + (chosen * .08f - (1f - chosen) * .03f) * progress);
            chips[i].setScaleY(1f + (chosen * .04f - (1f - chosen) * .02f) * progress);
            chips[i].setTranslationX(i == selected ? 0f : (i < selected ? -dp(5) : dp(5)) * progress);
        }
    }

    private void updateA11y() {
        for (int i = 0; i < chips.length; i++)
            chips[i].setContentDescription(chips[i].getText() + (i == selected ? ", selected" : ""));
    }

    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    @Override protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }
}
