package com.iniexe.rollcall;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.content.res.ColorStateList;
import android.text.InputType;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private int dp(float value) { return (int)(value * getResources().getDisplayMetrics().density); }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setPadding(dp(24), dp(36), dp(24), dp(24)); page.setBackgroundColor(0xFFFFF8EC);
        TextView title = new TextView(this); title.setText("Rollcall"); title.setTextSize(30); title.setTextColor(0xFF244B3E); title.setGravity(Gravity.CENTER_VERTICAL);
        TextView note = new TextView(this); note.setText("Attendance guidance over MyCamu. Your captured course data stays in memory on this device."); note.setTextSize(16); note.setTextColor(0xFF4E5B52); note.setPadding(0, dp(8), 0, dp(22));
        page.addView(title); page.addView(note);
        page.addView(button("Allow display over other apps", () -> { if (!Settings.canDrawOverlays(this)) startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))); }));
        page.addView(button("Enable MyCamu text reading", () -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
        page.addView(button("Start Rollcall overlay", () -> {
            if (Settings.canDrawOverlays(this)) {
                Intent service = new Intent(this, OverlayService.class);
                if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service);
            }
        }));
        page.addView(button("Open MyCamu", () -> {
            Intent launch = null;
            for (String id : new String[]{"com.octoze.mycamu_web_lite", "com.octoze.camu.mycamuapp"}) {
                launch = getPackageManager().getLaunchIntentForPackage(id);
                if (launch != null) break;
            }
            if (launch != null) startActivity(launch);
            else { TextView hint = new TextView(this); hint.setText("MyCamu app not found. Open it manually; the overlay can still be used."); page.addView(hint); }
        }));
        EditText course = input("Course name", false); EditText code = input("Course code", false); EditText attended = input("Periods attended", true); EditText total = input("Total periods", true);
        for (EditText field : new EditText[]{course, code, attended, total}) page.addView(field);
        TextView manualStatus = new TextView(this); page.addView(manualStatus);
        page.addView(button("Add attendance manually", () -> {
            try {
                Attendance row = new Attendance(code.getText().toString().trim(), course.getText().toString().trim(), Integer.parseInt(attended.getText().toString()), Integer.parseInt(total.getText().toString()));
                OverlayService.addManual(row);
                manualStatus.setText(row.code + " · " + row.name + " — " + String.format(java.util.Locale.getDefault(), "%.2f%%", row.percentage()) + " · " + row.guidance());
            } catch (RuntimeException e) { manualStatus.setText("Enter a course name, code, and valid attended / total counts."); }
        }));
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.addView(page); setContentView(scroll);
    }
    private EditText input(String hint, boolean numeric) { EditText field=new EditText(this); field.setSingleLine(true); field.setHint(hint); if(numeric)field.setInputType(InputType.TYPE_CLASS_NUMBER); return field; }
    private Button button(String label, Runnable action) { Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setBackgroundTintList(ColorStateList.valueOf(0xFF244B3E)); b.setTextColor(0xFFFFFFFF); b.setOnClickListener(v -> action.run()); return b; }
}
