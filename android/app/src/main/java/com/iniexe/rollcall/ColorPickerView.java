package com.iniexe.rollcall;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

final class ColorPickerView extends LinearLayout {
    private final Spectrum spectrum;
    private final SeekBar hueSlider;
    private final EditText hexInput;
    private final TextView sample;
    private final float[] hsv = {84f, 1f, 1f};
    private boolean updating;
    private boolean validHex = true;

    ColorPickerView(Context context, int initialColor) {
        super(context);
        setOrientation(VERTICAL);
        int inset = dp(20);
        setPadding(inset, dp(14), inset, dp(8));

        spectrum = new Spectrum(context);
        addView(spectrum, new LayoutParams(-1, dp(214)));

        TextView hueLabel = label(context, "HUE", 10);
        LayoutParams hueLabelParams = new LayoutParams(-1, -2);
        hueLabelParams.topMargin = dp(16);
        addView(hueLabel, hueLabelParams);
        hueSlider = new SeekBar(context);
        hueSlider.setMax(359);
        hueSlider.setContentDescription("Color hue");
        hueSlider.setProgressTintList(ColorStateList.valueOf(0xFF9FA1A7));
        hueSlider.setThumbTintList(ColorStateList.valueOf(0xFFFFFFFF));
        hueSlider.setProgressBackgroundTintList(ColorStateList.valueOf(0xFF43454B));
        addView(hueSlider, new LayoutParams(-1, dp(38)));
        hueSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                hsv[0] = progress;
                changed();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        TextView colorLabel = label(context, "HEX COLOR", 10);
        LayoutParams colorLabelParams = new LayoutParams(-1, -2);
        colorLabelParams.topMargin = dp(9);
        addView(colorLabel, colorLabelParams);
        LinearLayout colorRow = new LinearLayout(context);
        colorRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        hexInput = new EditText(context);
        hexInput.setSingleLine(true);
        hexInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        hexInput.setTextColor(0xFFF5F5F7);
        hexInput.setHintTextColor(0xFF9B9BA1);
        hexInput.setHint("#RRGGBB");
        hexInput.setTextSize(14);
        hexInput.setPadding(dp(12), 0, dp(12), 0);
        hexInput.setContentDescription("Hex color value");
        GradientDrawable inputBackground = new GradientDrawable();
        inputBackground.setColor(0xFF202125);
        inputBackground.setCornerRadius(dp(10));
        inputBackground.setStroke(dp(1), 0xFF414248);
        hexInput.setBackground(inputBackground);
        colorRow.addView(hexInput, new LayoutParams(0, dp(48), 1f));
        sample = new TextView(context);
        sample.setGravity(android.view.Gravity.CENTER);
        sample.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        sample.setTextColor(0xFFFFFFFF);
        sample.setTextSize(10);
        sample.setContentDescription("Selected color preview");
        LayoutParams sampleParams = new LayoutParams(dp(70), dp(42));
        sampleParams.leftMargin = dp(10);
        colorRow.addView(sample, sampleParams);
        addView(colorRow);

        hexInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (updating) return;
                int parsed = parseHex(s.toString());
                validHex = parsed != 0;
                hexInput.setError(validHex ? null : "Enter a 6-digit hex color");
                if (validHex) setColor(parsed, false);
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        setColor(initialColor, true);
    }

    int getColor() { return Color.HSVToColor(hsv); }
    boolean hasValidColor() { return validHex; }

    void setColor(int color) { setColor(color, true); }

    private void setColor(int color, boolean writeHex) {
        Color.colorToHSV(0xFF000000 | (color & 0x00FFFFFF), hsv);
        updating = true;
        hueSlider.setProgress(Math.round(hsv[0]) % 360);
        if (writeHex) hexInput.setText(String.format(java.util.Locale.US, "#%06X", color & 0x00FFFFFF));
        sample.setText(String.format(java.util.Locale.US, "#%06X", color & 0x00FFFFFF));
        GradientDrawable chip = new GradientDrawable();
        chip.setColor(getColor());
        chip.setCornerRadius(dp(9));
        chip.setStroke(dp(1), 0xFF76777C);
        sample.setBackground(chip);
        sample.setTextColor(contrastText(getColor()));
        spectrum.setColor(getColor());
        spectrum.invalidate();
        updating = false;
    }

    private void changed() {
        validHex = true;
        hexInput.setError(null);
        setColor(getColor(), true);
    }

    private TextView label(Context context, String value, int size) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextColor(0xFFB9BAC0);
        view.setTextSize(size);
        view.setLetterSpacing(.08f);
        view.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        return view;
    }

    private int parseHex(String value) {
        String digits = value == null ? "" : value.trim();
        if (digits.startsWith("#")) digits = digits.substring(1);
        if (digits.length() != 6) return 0;
        try { return 0xFF000000 | Integer.parseInt(digits, 16); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private static int contrastText(int color) {
        double r = linear(Color.red(color) / 255.0), g = linear(Color.green(color) / 255.0),
                b = linear(Color.blue(color) / 255.0);
        double luminance = .2126 * r + .7152 * g + .0722 * b;
        return luminance > .179 ? 0xFF111214 : 0xFFFFFFFF;
    }

    private static double linear(double channel) {
        return channel <= .04045 ? channel / 12.92 : Math.pow((channel + .055) / 1.055, 2.4);
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private final class Spectrum extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF bounds = new RectF();
        private final Path clipPath = new Path();
        private float saturation = 1f, value = 1f;

        Spectrum(Context context) {
            super(context);
            setFocusable(true);
            setClickable(true);
            setContentDescription("Saturation and brightness color selector");
            setColor(Color.HSVToColor(hsv));
        }

        void setColor(int color) {
            float[] current = new float[3];
            Color.colorToHSV(color, current);
            saturation = current[1];
            value = current[2];
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            bounds.set(0, 0, getWidth(), getHeight());
            float radius = dp(13);
            clipPath.reset();
            clipPath.addRoundRect(bounds, radius, radius, Path.Direction.CW);
            int save = canvas.save();
            canvas.clipPath(clipPath);
            int pureHue = Color.HSVToColor(new float[]{hsv[0], 1f, 1f});
            paint.setShader(null);
            paint.setColor(pureHue);
            canvas.drawRect(bounds, paint);
            paint.setShader(new LinearGradient(0, 0, getWidth(), 0,
                    0xFFFFFFFF, pureHue, Shader.TileMode.CLAMP));
            canvas.drawRect(bounds, paint);
            paint.setShader(new LinearGradient(0, 0, 0, getHeight(),
                    0x00000000, 0xFF000000, Shader.TileMode.CLAMP));
            canvas.drawRect(bounds, paint);
            paint.setShader(null);
            float inset = dp(11);
            float x = inset + saturation * Math.max(0, getWidth() - inset * 2);
            float y = inset + (1f - value) * Math.max(0, getHeight() - inset * 2);
            paint.setColor(contrastText(getColor()));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            canvas.drawCircle(x, y, dp(9), paint);
            paint.setColor(getColor());
            paint.setStrokeWidth(dp(1));
            canvas.drawCircle(x, y, dp(6), paint);
            paint.setStyle(Paint.Style.FILL);
            canvas.restoreToCount(save);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN
                    || event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                float inset = dp(11);
                hsv[1] = Math.max(0f, Math.min(1f,
                        (event.getX() - inset) / Math.max(1, getWidth() - inset * 2)));
                hsv[2] = 1f - Math.max(0f, Math.min(1f,
                        (event.getY() - inset) / Math.max(1, getHeight() - inset * 2)));
                changed();
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                performClick();
                return true;
            }
            return true;
        }

        @Override public boolean performClick() {
            super.performClick();
            sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED);
            return true;
        }
    }
}
