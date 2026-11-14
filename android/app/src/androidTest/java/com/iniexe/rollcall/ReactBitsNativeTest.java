package com.iniexe.rollcall;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ReactBitsNativeTest {
    @Test public void dashboardEffectsUseRealStateAndActions() {
        try (ActivityScenario<DashboardTestActivity> scenario = ActivityScenario.launch(DashboardTestActivity.class)) {
            scenario.onActivity(activity -> activity.dashboard.update(fixture()));
            screenshot("home-dark");
            scenario.onActivity(activity -> {
                ShuffleTextView headline = find(activity.dashboard, ShuffleTextView.class);
                assertEquals("ALEX.\nYOU GOT THIS.", headline.settledText());
                assertNull(find(activity.dashboard, LetterGlitchView.class));
                assertNotNull(findText(activity.dashboard, "Updated just now"));
                assertEquals(StatusMarkView.State.DONE,
                        find(activity.dashboard, StatusMarkView.class).state());
            });

            tab(scenario, "Courses");
            screenshot("courses-dark");
            scenario.onActivity(activity -> {
                assertNotNull(findText(activity.dashboard, "Programming Fundamentals"));
                JellyRadioView radio = findRadio(activity.dashboard, "All courses");
                assertNotNull(radio);
                assertTrue(radio.chip(0).getText().toString().startsWith("All courses"));
                radio.chip(1).performClick();
                assertEquals(1, radio.selection());
                assertTrue(radio.chip(1).isSelected());
                assertFalse(hasVisibleText(activity.dashboard, "Programming Fundamentals"));
                assertTrue(hasVisibleText(activity.dashboard,
                        "Introduction to Computational Mathematics and Applied Logic"));
            });
            screenshot("courses-filtered");

            tab(scenario, "Week");
            scenario.onActivity(activity -> findText(activity.dashboard, "ATTENDANCE").performClick());
            screenshot("week-history");
            scenario.onActivity(activity -> {
                assertNotNull(find(activity.dashboard, BellToggleView.class));
                assertNull(find(activity.dashboard, FolderFloatView.class));
                findText(activity.dashboard, "Leave & OD records  →").performClick();
                assertNotNull(findText(activity.dashboard, "Leave & OD"));
                assertNotNull(findText(activity.dashboard, "Current sem · 0"));
                assertNotNull(findText(activity.dashboard, "All OD · 0"));
                activity.dashboard.closeOdDetailsForBack();
                findText(activity.dashboard, "SCHEDULE").performClick();
                assertNotNull(findText(activity.dashboard, "Data Structures lab"));
            });
            screenshot("week-schedule");

            tab(scenario, "What if?");
            screenshot("what-if");
            scenario.onActivity(activity -> {
                findText(activity.dashboard, "ATTEND").performClick();
                assertNotNull(findText(activity.dashboard, "YOUR ATTENDANCE / PROJECTED"));
            });

            tab(scenario, "Marks and academics");
            screenshot("marks-all");
            scenario.onActivity(activity -> {
                OptionWheelView wheel = find(activity.dashboard, OptionWheelView.class);
                assertNotNull(wheel);
                assertNotNull(findText(activity.dashboard, "Internal assessments"));
                assertNotNull(findText(activity.dashboard, "No internal marks synced"));
                wheel.select(1);
                assertNotNull(findText(activity.dashboard, "Semester results"));
            });
            screenshot("marks-selected");
        }
    }

    @Test public void lightModeAndCodePrivacyPresentation() {
        Intent intent = new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),
                DashboardTestActivity.class).putExtra("light", true);
        try (ActivityScenario<DashboardTestActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> activity.dashboard.update(fixture()));
            screenshot("home-light");
            tab(scenario, "Week");
            screenshot("week-light");
            scenario.onActivity(activity -> {
                CodeSlotsView code = new CodeSlotsView(activity, 0xFFE7E7EF, 0xFF111111, 0xFFFF4400);
                assertFalse(code.setCode("12345"));
                assertTrue(code.setCode("123456"));
                assertEquals("123456", code.code());
                assertTrue(code.getContentDescription().toString().contains("1 2 3 4 5 6"));
            });
        }
    }

    @Test public void emptyDataRapidNavigationAndRotation() {
        try (ActivityScenario<DashboardTestActivity> scenario = ActivityScenario.launch(DashboardTestActivity.class)) {
            DashboardView.State empty = new DashboardView.State();
            empty.name = "Alex";
            scenario.onActivity(activity -> activity.dashboard.update(empty));
            for (int repeat = 0; repeat < 3; repeat++) {
                tab(scenario, "Courses");
                tab(scenario, "Week");
                tab(scenario, "Marks and academics");
                tab(scenario, "Home");
            }
            scenario.recreate();
            scenario.onActivity(activity -> {
                activity.dashboard.update(empty);
                assertNotNull(find(activity.dashboard, ShuffleTextView.class));
                assertNotNull(find(activity.dashboard, StatusMarkView.class));
            });
        }
    }

    private static DashboardView.State fixture() {
        DashboardView.State state = new DashboardView.State();
        state.name = "Alex";
        state.status = "Updated just now";
        state.syncSucceeded = true;
        state.attendanceLoaded = true;
        state.timetableLoaded = true;
        state.leavesLoaded = true;
        state.target = 75;
        state.courses = 2;
        state.onTrack = 1;
        state.below = 1;
        state.heroMessage = "29 / 40";
        DashboardView.Course first = new DashboardView.Course();
        first.code = "CS101"; first.name = "Programming Fundamentals";
        first.attended = 18; first.total = 20; first.countsKnown = true;
        first.percent = 90; first.percentLabel = "90%";
        first.status = "On track"; first.guidance = "Can miss 4"; first.counts = "18 / 20";
        first.color = 0xFFCCFF00;
        state.rows.add(first);
        DashboardView.Course second = new DashboardView.Course();
        second.code = "CS102";
        second.name = "Introduction to Computational Mathematics and Applied Logic";
        second.attended = 11; second.total = 20; second.countsKnown = true;
        second.percent = 55; second.percentLabel = "55%"; second.below = true;
        second.status = "Below target"; second.guidance = "Must attend 16";
        second.counts = "11 / 20"; second.color = 0xFFFF4400;
        state.rows.add(second);
        Calendar tomorrow = Calendar.getInstance();
        tomorrow.add(Calendar.DAY_OF_YEAR, 1);
        tomorrow.set(Calendar.HOUR_OF_DAY, 9);
        DashboardView.ScheduleItem schedule = new DashboardView.ScheduleItem();
        schedule.date = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(tomorrow.getTime());
        schedule.time = "9:00 AM – 10:00 AM";
        schedule.name = "Data Structures lab"; schedule.code = "CS102L";
        schedule.startsAt = tomorrow.getTimeInMillis();
        schedule.endsAt = schedule.startsAt + 3600000;
        state.timetable.add(schedule);
        DashboardView.ClassMarkItem mark = new DashboardView.ClassMarkItem();
        mark.name = "Data Structures lecture"; mark.code = "CS102";
        mark.day = "Yesterday"; mark.time = "9:00 AM";
        mark.status = "present"; mark.startsAt = System.currentTimeMillis() - 86400000;
        state.classMarks.add(mark);
        for (int semester = 1; semester <= 2; semester++) {
            DashboardView.AcademicCourse course = new DashboardView.AcademicCourse();
            course.semester = String.valueOf(semester);
            course.code = "MA" + semester + "01";
            course.name = "Academic Mathematics " + semester;
            course.grade = semester == 1 ? "A" : "B";
            course.credits = 3;
            state.academicCourses.add(course);
        }
        return state;
    }

    private static void tab(ActivityScenario<DashboardTestActivity> scenario, String name) {
        scenario.onActivity(activity -> {
            View tab = findDescription(activity.dashboard, name);
            assertNotNull(name, tab);
            tab.performClick();
        });
    }

    private static void screenshot(String name) {
        SystemClock.sleep(650);
        Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        File folder = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getExternalFilesDir("react-bits-qa");
        assertNotNull(folder);
        File output = new File(folder, name + ".png");
        try (FileOutputStream stream = new FileOutputStream(output)) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
        } catch (Exception error) { throw new AssertionError(error); }
        bitmap.recycle();
    }

    private static View findDescription(View root, String label) {
        if (root.getContentDescription() != null && root.getContentDescription().toString().startsWith(label)) return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findDescription(group.getChildAt(i), label);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static TextView findText(View root, String label) {
        if (root instanceof TextView && label.equals(((TextView) root).getText().toString())) return (TextView) root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findText(group.getChildAt(i), label);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static boolean hasVisibleText(View root, String label) {
        if (root instanceof TextView && label.equals(((TextView) root).getText().toString()) && root.isShown())
            return true;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++)
                if (hasVisibleText(group.getChildAt(i), label)) return true;
        }
        return false;
    }

    private static <T extends View> T find(View root, Class<T> type) {
        if (type.isInstance(root)) return type.cast(root);
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                T found = find(group.getChildAt(i), type);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static JellyRadioView findRadio(View root, String firstLabel) {
        if (root instanceof JellyRadioView) {
            JellyRadioView radio = (JellyRadioView) root;
            if (radio.chip(0).getText().toString().startsWith(firstLabel)) return radio;
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                JellyRadioView found = findRadio(group.getChildAt(i), firstLabel);
                if (found != null) return found;
            }
        }
        return null;
    }
}
