package com.iniexe.rollcall;

import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class DashboardViewNavigationTest {
    @Test public void homeAgendaShowsClassAndExamAndFallsBackToEachAvailableItem() {
        try (ActivityScenario<DashboardTestActivity> scenario = ActivityScenario.launch(DashboardTestActivity.class)) {
            DashboardView.State state = fixture();
            state.upcomingExamTitle = "Algorithms midterm";
            state.upcomingExamType = "Theory";
            state.upcomingExamAt = System.currentTimeMillis() + 2 * 24 * 60 * 60_000L;
            state.examScheduleCheckedAt = System.currentTimeMillis();
            scenario.onActivity(activity -> {
                activity.dashboard.update(state);
                assertNotNull(findText(activity.dashboard, "Data Structures lab"));
                assertNotNull(findText(activity.dashboard, "Algorithms midterm"));
            });

            state.timetable.clear();
            scenario.onActivity(activity -> {
                activity.dashboard.update(state);
                assertNotNull(findText(activity.dashboard, "Algorithms midterm"));
                assertNull(findText(activity.dashboard, "Nothing scheduled"));
            });

            state.upcomingExamTitle = "";
            state.upcomingExamAt = 0;
            state.timetableLoaded = true;
            state.examScheduleCheckedAt = System.currentTimeMillis();
            scenario.onActivity(activity -> {
                activity.dashboard.update(state);
                assertNotNull(findText(activity.dashboard, "Nothing scheduled"));
            });
        }
    }

    @Test public void homeAgendaDistinguishesUnknownAndCachedScheduleAfterSyncFailure() {
        try (ActivityScenario<DashboardTestActivity> scenario = ActivityScenario.launch(DashboardTestActivity.class)) {
            DashboardView.State state = fixture();
            state.syncError = true;
            state.upcomingExamTitle = "";
            state.upcomingExamAt = 0;
            state.examScheduleCheckedAt = System.currentTimeMillis() - 3 * 60 * 60_000L;
            scenario.onActivity(activity -> {
                activity.dashboard.update(state);
                assertNotNull(findTextPrefix(activity.dashboard, "Saved timetable · updated"));
            });

            state.timetable.clear();
            state.timetableLoaded = false;
            state.timetableUpdatedAt = 0;
            state.examScheduleCheckedAt = 0;
            scenario.onActivity(activity -> {
                activity.dashboard.update(state);
                assertNotNull(findText(activity.dashboard, "Schedule refresh failed"));
                assertNotNull(findText(activity.dashboard,
                        "Rollcall couldn’t verify your latest class or exam schedule. Retry when your connection is ready."));
            });
        }
    }

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

    @Test public void emptyWeekExplainsThereAreNoClassesWithoutRefreshAction() {
        try (ActivityScenario<DashboardTestActivity> scenario = ActivityScenario.launch(DashboardTestActivity.class)) {
            DashboardView.State state = fixture();
            state.timetable.clear();
            scenario.onActivity(activity -> activity.dashboard.update(state));
            clickTab(scenario, "Week");
            scenario.onActivity(activity -> {
                assertNotNull(findText(activity.dashboard, "NO CLASSES\nSCHEDULED."));
                assertNotNull(findText(activity.dashboard, "There are no classes scheduled for this week."));
                assertNull(findText(activity.dashboard, "Refresh timetable  →"));
            });
        }
    }

    @Test public void webNavigationOrderAndMoreMenuRouteToWhatIfAndLeaveOd() {
        try (ActivityScenario<DashboardTestActivity> scenario = ActivityScenario.launch(DashboardTestActivity.class)) {
            DashboardView.State state = fixture();
            scenario.onActivity(activity -> activity.dashboard.update(state));
            clickTab(scenario, "More");
            scenario.onActivity(activity -> {
                assertNotNull(findDescription(activity.dashboard, "Marks and academics"));
                assertEquals(View.VISIBLE, activity.dashboard.findViewWithTag("rollcall-more-menu").getVisibility());
                findDescription(activity.dashboard, "More: What If?").performClick();
                assertEquals(View.GONE, activity.dashboard.findViewWithTag("rollcall-more-menu").getVisibility());
                assertTrue(findDescription(activity.dashboard, "More, selected").isSelected());
                findDescription(activity.dashboard, "More, selected").performClick();
                findDescription(activity.dashboard, "More: Leave & OD").performClick();
                assertTrue(findDescription(activity.dashboard, "More, selected").isSelected());
                findDescription(activity.dashboard, "Home").performClick();
                assertTrue(findDescription(activity.dashboard, "Home, selected").isSelected());
                assertTrue(activity.dashboard.isHomePageSelected());
                findDescription(activity.dashboard, "Courses").performClick();
                assertFalse(activity.dashboard.isHomePageSelected());
                assertFalse(activity.dashboard.closeOdDetailsForBack());
            });
        }
    }

    @Test public void announcementsLiveInMoreMenuAndShowUnreadCount() {
        try (ActivityScenario<DashboardTestActivity> scenario = ActivityScenario.launch(DashboardTestActivity.class)) {
            DashboardView.State state = fixture();
            state.cloudConfigured = true;
            state.announcementsLoaded = true;
            DashboardView.Announcement update = new DashboardView.Announcement();
            update.id = "12345678-1234-1234-1234-123456789012";
            update.title = "Schedule update";
            update.body = "The new timetable is ready.";
            state.announcements.add(update);
            scenario.onActivity(activity -> {
                activity.dashboard.update(state);
                assertNull(findText(activity.dashboard, "ROLLCALL UPDATES"));
                assertNotNull(findDescription(activity.dashboard, "More, updates available"));
                findDescription(activity.dashboard, "More").performClick();
                assertNotNull(findText(activity.dashboard, "Updates · 1 new"));
                findDescription(activity.dashboard, "More: Updates").performClick();
                assertNotNull(findText(activity.getWindow().getDecorView(), "ROLLCALL UPDATES"));
                assertNotNull(findText(activity.getWindow().getDecorView(), "Schedule update"));
            });
        }
    }

    @Test public void announcementPopupChannelIsReadFromTheInboxContract() throws Exception {
        JSONArray rows = new JSONArray().put(new JSONObject()
                .put("id", "12345678-1234-1234-1234-123456789012")
                .put("title", "Service update")
                .put("body", "The schedule sync is available.")
                .put("channels", new JSONArray().put("inbox").put("popup")));
        java.util.List<DashboardView.Announcement> items = CloudAccountClient.parseAnnouncements(
                new JSONObject().put("announcements", rows));
        assertEquals(1, items.size());
        assertTrue(items.get(0).showPopup);
    }

    @Test public void profilePhotoAppearsOnDashboardAvatarAndCanBeCleared() {
        try (ActivityScenario<DashboardTestActivity> scenario = ActivityScenario.launch(DashboardTestActivity.class)) {
            Bitmap photo = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
            scenario.onActivity(activity -> {
                activity.dashboard.setProfilePhoto(photo);
                View avatar = activity.dashboard.findViewWithTag("profile-photo-avatar");
                assertNotNull(avatar);
                assertEquals(View.VISIBLE, avatar.getVisibility());
                activity.dashboard.setProfilePhoto(null);
                assertEquals(View.GONE, avatar.getVisibility());
            });
            photo.recycle();
        }
    }

    @Test public void floatingNavigationOverlaysPagesAndHomeScrollHasClearance() {
        try (ActivityScenario<DashboardTestActivity> scenario = ActivityScenario.launch(DashboardTestActivity.class)) {
            scenario.onActivity(activity -> {
                View navigation = activity.dashboard.findViewWithTag("rollcall-floating-navigation");
                View homeScroll = activity.dashboard.findViewWithTag("rollcall-page-scroll-0");
                View homeHeader = activity.dashboard.findViewWithTag("rollcall-home-header");
                View selectedLabel = findDescription(activity.dashboard, "Overview, selected");
                assertNotNull(navigation);
                assertNull(activity.dashboard.findViewWithTag("rollcall-frosted-navigation-backdrop"));
                assertNotNull(homeScroll);
                assertNotNull(homeHeader);
                assertFalse(((ScrollView) homeScroll).isVerticalFadingEdgeEnabled());
                assertEquals(android.graphics.PixelFormat.OPAQUE, navigation.getBackground().getOpacity());
                assertEquals(homeHeader, ((ViewGroup) ((ScrollView) homeScroll).getChildAt(0)).getChildAt(0));
                ViewGroup homeBody = (ViewGroup) ((ScrollView) homeScroll).getChildAt(0);
                View bottomSpace = homeBody.getChildAt(homeBody.getChildCount() - 1);
                assertEquals("rollcall-scroll-bottom-space", bottomSpace.getTag());
                assertTrue(bottomSpace.getLayoutParams().height
                        >= activity.dashboard.getResources().getDisplayMetrics().density * 112f);
                assertTrue(selectedLabel instanceof TextView);
                assertEquals(AppPalette.get(0).buttonText, ((TextView) selectedLabel).getCurrentTextColor());
                View holder = (View) navigation.getParent();
                assertEquals(activity.dashboard, holder.getParent());
                assertTrue(navigation.getLayoutParams() instanceof FrameLayout.LayoutParams);
                assertTrue(holder instanceof FrameLayout);
                assertTrue(holder.getLayoutParams() instanceof FrameLayout.LayoutParams);
                assertEquals(android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL,
                        ((FrameLayout.LayoutParams) holder.getLayoutParams()).gravity);
                assertTrue(homeBody.getPaddingBottom()
                        >= activity.dashboard.getResources().getDisplayMetrics().density * 16f);
                assertTrue(homeHeader.getPaddingBottom()
                        >= activity.dashboard.getResources().getDisplayMetrics().density * 8f);
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
        state.timetableUpdatedAt = System.currentTimeMillis();
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

    private static TextView findTextPrefix(View view, String prefix) {
        if (view instanceof TextView && ((TextView) view).getText().toString().startsWith(prefix))
            return (TextView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findTextPrefix(group.getChildAt(i), prefix);
                if (found != null) return found;
            }
        }
        return null;
    }
}
