package com.iniexe.rollcall;

import android.os.Bundle;
import android.graphics.Color;
import android.view.View;
import android.widget.FrameLayout;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.activity.ComponentActivity;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.Locale;

/** Offline profiling fixtures included only in the local benchmark build. */
public final class CoursesBenchmarkActivity extends ComponentActivity {
    private DashboardView dashboard;
    private DashboardView.State state;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getIntent().getBooleanExtra("simple", false)) {
            ScrollView scroll = new ScrollView(this);
            LinearLayout list = new LinearLayout(this);
            list.setOrientation(LinearLayout.VERTICAL);
            for (int i = 0; i < 48; i++) {
                TextView row = new TextView(this);
                row.setText("Long Course Name " + i + " Applied Computing");
                row.setPadding(24, 48, 24, 48);
                list.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            scroll.addView(list);
            setContentView(scroll);
            return;
        }
        state = new DashboardView.State();
        state.attendanceLoaded = true;
        state.target = 75;
        state.rows = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            String code = String.format(java.util.Locale.ROOT, "P%03d", i);
            state.rows.add(course(code, "Long Course Name " + i + " Applied Computing", i % 3 == 0 ? 60 : 82, i % 3 == 0));
            state.rows.add(course(code + "L", "Long Course Name " + i + " Lab", i % 4 == 0 ? 55 : 80, i % 4 == 0));
        }
        state.courses = state.rows.size();
        for (DashboardView.Course row : state.rows) if (row.below) state.below++;
        boolean week = getIntent().getBooleanExtra("week", BuildConfig.BUILD_TYPE.equals("qa"));
        if (week) addWeekFixture(state);
        AppPalette palette = AppPalette.get(getIntent().getBooleanExtra("dark", false), 0);
        dashboard = new DashboardView(this, new DashboardView.Actions() {
            public void onSync() {}
            public void onPortal() {}
            public void onAcademicsPortal() {}
            public void onTarget() {}
            public void onTheme() {}
            public void onPalette() {}
            public void onAbout() {}
            public void onFilter(boolean belowOnly) {}
            public void onQuery(String value) {}
            public void onCourse(String code) {
                for (DashboardView.Course row : state.rows) if (row.code.equals(code)) row.expanded = !row.expanded;
                dashboard.update(state);
            }
        }, palette);
        if (BuildConfig.BUILD_TYPE.equals("qa")) setQaContentView(dashboard, palette);
        else setContentView(dashboard);
        dashboard.update(state);
        if (week) dashboard.openWidgetPage(2);
    }

    private void setQaContentView(View page, AppPalette palette) {
        FrameLayout root = new FrameLayout(this);
        FrameLayout content = new FrameLayout(this);
        root.setBackgroundColor(palette.canvas);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            content.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        content.addView(page, new FrameLayout.LayoutParams(-1, -1));
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(decor.getSystemUiVisibility()
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        setContentView(root);
        root.requestApplyInsets();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            int lightBars = palette.canvas > 0xFF808080
                    ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0;
            android.view.WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) controller.setSystemBarsAppearance(lightBars,
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        } else if (palette.canvas > 0xFF808080) {
            decor.setSystemUiVisibility(decor.getSystemUiVisibility()
                    | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
    }

    private static void addWeekFixture(DashboardView.State state) {
        long now = System.currentTimeMillis();
        SimpleDateFormat day = new SimpleDateFormat("EEEE, d MMM", Locale.getDefault());
        SimpleDateFormat time = new SimpleDateFormat("h:mm a", Locale.getDefault());
        Calendar date = Calendar.getInstance();
        date.set(Calendar.HOUR_OF_DAY, 0); date.set(Calendar.MINUTE, 0); date.set(Calendar.SECOND, 0); date.set(Calendar.MILLISECOND, 0);
        for (int dayIndex = 0; dayIndex < 7; dayIndex++) {
            date.setTimeInMillis(now);
            date.add(Calendar.DAY_OF_YEAR, dayIndex);
            String label = day.format(new Date(date.getTimeInMillis()));
            for (int period = 0; period < 6; period++) {
                DashboardView.ScheduleItem item = new DashboardView.ScheduleItem();
                item.date = label;
                item.startsAt = date.getTimeInMillis() + (8L + period) * 60 * 60 * 1000;
                item.endsAt = item.startsAt + 50 * 60 * 1000;
                item.time = time.format(new Date(item.startsAt)) + " – " + time.format(new Date(item.endsAt));
                item.name = "Long Applied Computing Course " + dayIndex + " Period " + period;
                item.code = String.format(Locale.ROOT, "WK%02d%02d", dayIndex, period);
                item.details = "Room " + (period + 1) + " · Faculty member";
                item.allDay = dayIndex == 6 && period == 0;
                item.cancelled = period == 5 && dayIndex % 3 == 0;
                state.timetable.add(item);
            }
        }
        state.timetableLoaded = true;
        state.timetableUpdatedAt = now;
        for (int i = 0; i < 60; i++) {
            DashboardView.ClassMarkItem item = new DashboardView.ClassMarkItem();
            item.day = new SimpleDateFormat("EEEE, d MMM yyyy", Locale.getDefault())
                    .format(new Date(now - i * 24L * 60 * 60 * 1000));
            item.time = "9:00 AM";
            item.code = String.format(Locale.ROOT, "WK%03d", i);
            item.name = "Week benchmark attendance record " + i;
            item.status = i % 4 == 0 ? "absent" : i % 4 == 1 ? "not_taken" : i % 4 == 2 ? "unable_to_confirm" : "present";
            item.color = "absent".equals(item.status) ? 0xffb5362a : "present".equals(item.status) ? 0xff24774f : 0xff73766e;
            item.startsAt = now - i * 60 * 60 * 1000L;
            state.classMarks.add(item);
        }
        state.classMarksUpdatedAt = now;
    }

    private static DashboardView.Course course(String code, String name, int percent, boolean below) {
        DashboardView.Course row = new DashboardView.Course();
        row.code = code;
        row.name = name;
        row.percent = percent;
        row.percentLabel = percent + "%";
        row.attended = 16;
        row.total = 20;
        row.countsKnown = true;
        row.status = below ? "Below target" : "Above target";
        row.guidance = below ? "Attend 12 classes to reach target" : "You can miss 1 more class";
        row.counts = "16 attended / 20 total";
        row.canMissText = below ? "0" : "1";
        row.mustAttendText = below ? "12" : "0";
        row.targetCount = below ? "Attend 12 classes" : "Can miss 1";
        row.color = below ? 0xffaa2921 : 0xff176146;
        row.below = below;
        return row;
    }
}
