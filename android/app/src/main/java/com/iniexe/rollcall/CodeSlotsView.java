package com.iniexe.rollcall;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Read-only six-digit presentation using React Bits Code Slots' staggered rise. */
final class CodeSlotsView extends LinearLayout {
    private final TextView[] slots = new TextView[6];
    private final int accent;
    private String code = "";

    CodeSlotsView(Context context, int surface, int ink, int accent) {
        super(context);
        this.accent = accent;
        setOrientation(HORIZONTAL);
        setPadding(dp(8), dp(8), dp(8), dp(8));
        setGravity(Gravity.CENTER);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        for (int i = 0; i < slots.length; i++) {
            TextView slot = new TextView(context);
            slot.setGravity(Gravity.CENTER);
            slot.setTextSize(24);
            slot.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            slot.setTextColor(ink);
            GradientDrawable background = new GradientDrawable();
            background.setColor(surface);
            background.setCornerRadius(dp(10));
            background.setStroke(dp(1), accent);
            slot.setBackground(background);
            slot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LayoutParams lp = new LayoutParams(0, dp(54), 1f);
            lp.leftMargin = dp(3);
            lp.rightMargin = dp(3);
            addView(slot, lp);
            slots[i] = slot;
        }
    }

    boolean setCode(String value) {
        if (value == null || !value.matches("[0-9]{6}")) return false;
        code = value;
        setContentDescription("Web sign-in code: " + value.replace("", " ").trim());
        for (int i = 0; i < slots.length; i++) {
            TextView slot = slots[i];
            slot.setText(String.valueOf(value.charAt(i)));
            if (NativeMotion.enabled(getContext()) && isAttachedToWindow()) {
                slot.animate().cancel();
                slot.setTranslationY(dp(8));
                slot.setScaleX(.85f); slot.setScaleY(.85f);
                slot.setAlpha(0f);
                slot.animate().translationY(0).scaleX(1).scaleY(1).alpha(1)
                        .setStartDelay(i * 20L).setDuration(300)
                        .setInterpolator(new OvershootInterpolator(.7f)).start();
            }
        }
        return true;
    }

    String code() { return code; }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override protected void onDetachedFromWindow() {
        for (TextView slot : slots) slot.animate().cancel();
        super.onDetachedFromWindow();
    }
}
