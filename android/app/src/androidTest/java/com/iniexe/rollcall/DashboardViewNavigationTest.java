package com.iniexe.rollcall;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class DashboardViewNavigationTest {
    @Test public void navigatesAndShowsFixtureScheduleAndAttendanceHistory() {
        try (ActivityScenario<DashboardTestActivity> scenario = ActivityScenario.launch(DashboardTestActivity.class)) {
            DashboardView.State state = fixture();
            scenario.onActivity(activity -> activity.dashboard.update(state));

            clickTab(scenario, "Courses");
            scenario.onActivity(activity -> {
                assertNotNull(findText(activity.dashboard, "Programming Fundamentals"));
                assertTrue(findDescription(activity.dashboard, "Courses, selected").isSelected());
            });

            clickTab(scenario, "Week");
            scenario.onActivity(activity -> {
                assertNotNull(findText(activity.dashboard, "Attendance history"));
                assertNotNull(findText(activity.dashboard, "Data Structures lecture"));
                assertNotNull(findDescription(activity.dashboard, "Data Structures lecture, Present"));
                assertTrue(findDescription(activity.dashboard, "Week, selected").isSelected());
                findText(activity.dashboard, "SCHEDULE").performClick();
                assertNotNull(findText(activity.dashboard, "Data Structures lab"));
                findText(activity.dashboard, "ATTENDANCE").performClick();
                assertNotNull(findDescription(activity.dashboard, "Data Structures lecture, Present"));
            });
        }
    }

    private static void clickTab(ActivityScenario<DashboardTestActivity> scenario, String label) {
        scenario.onActivity(activity -> findDescription(activity.dashboard, label).performClick());
    }

    private static DashboardView.State fixture() {
        DashboardView.State state = new DashboardView.State();
        state.attendanceLoaded = true;
        state.timetableLoaded = true;
        state.classMarksUpdatedAt = System.currentTimeMillis();

        DashboardView.Course course = new DashboardView.Course();
        course.code = "CS101";
        course.name = "Programming Fundamentals";
        course.percent = 80;
        course.percentLabel = "80%";
        course.attended = 16;
        course.total = 20;
        course.countsKnown = true;
        course.status = "Above target";
        course.guidance = "You can miss 1 more class";
        course.targetCount = "Can miss 1";
        course.color = 0xFFCCFF00;
        state.rows.add(course);
        state.courses = 1;
        state.onTrack = 1;

        Calendar future = Calendar.getInstance();
        future.add(Calendar.DAY_OF_YEAR, 1);
        future.set(Calendar.HOUR_OF_DAY, 9);
        future.set(Calendar.MINUTE, 0);
        future.set(Calendar.SECOND, 0);
        future.set(Calendar.MILLISECOND, 0);
        DashboardView.ScheduleItem schedule = new DashboardView.ScheduleItem();
        schedule.date = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(future.getTime());
        schedule.time = "9:00 AM – 10:00 AM";
        schedule.name = "Data Structures lab";
        schedule.code = "CS102L";
        schedule.startsAt = future.getTimeInMillis();
        schedule.endsAt = schedule.startsAt + 60 * 60_000L;
        state.timetable.add(schedule);

        DashboardView.ClassMarkItem history = new DashboardView.ClassMarkItem();
        history.day = "Yesterday";
        history.time = "9:00 AM";
        history.code = "CS102";
        history.name = "Data Structures lecture";
        history.status = "present";
        history.color = 0xFF9BE4A1;
        history.startsAt = System.currentTimeMillis() - 24 * 60 * 60_000L;
        state.classMarks.add(history);
        return state;
    }

    private static View findDescription(View view, String description) {
        if (description.equals(view.getContentDescription())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findDescription(group.getChildAt(i), description);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static TextView findText(View view, String text) {
        if (view instanceof TextView && text.equals(((TextView) view).getText().toString())) return (TextView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findText(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }
}
