package com.iniexe.rollcall;

import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.activity.ComponentActivity;
import java.util.ArrayList;

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
        }, AppPalette.get(false, 0));
        setContentView(dashboard);
        dashboard.update(state);
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
