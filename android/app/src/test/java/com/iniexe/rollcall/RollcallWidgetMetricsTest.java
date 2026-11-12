package com.iniexe.rollcall;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public final class RollcallWidgetMetricsTest {
    @Test public void attendanceUsesWeightedCountsAndSortsCoursesNeedingAttention() {
        RollcallWidgetMetrics.AttendanceSummary value = RollcallWidgetMetrics.attendance(Arrays.asList(
                new Attendance("A", "A", 8, 10), new Attendance("B", "B", 9, 10),
                new Attendance("LOW", "Low", 2, 4)), 75);
        assertEquals(79, value.percentage);
        assertEquals(2, value.onTrack);
        assertEquals(1, value.belowTarget);
        assertEquals("LOW", value.attentionCourses.get(0).code);
    }

    @Test public void streakSkipsUnconfirmedClassesAndStopsAtConfirmedAbsence() {
        assertEquals(2, RollcallWidgetMetrics.currentStreak(Arrays.asList(
                mark(4, "present"), mark(3, "unable_to_confirm"), mark(2, "present"), mark(1, "absent"))));
        assertEquals(Arrays.asList("present", "present", "present"), RollcallWidgetMetrics.recentConfirmedStatuses(
                Arrays.asList(mark(4, "present"), mark(3, "unable_to_confirm"), mark(2, "present"), mark(1, "present")), 7));
    }

    @Test public void streakIsUnavailableUntilThereIsAConfirmedMark() {
        assertEquals(-1, RollcallWidgetMetrics.currentStreak(Collections.singletonList(mark(1, "not_taken"))));
        assertEquals(0, RollcallWidgetMetrics.currentStreak(Collections.singletonList(mark(1, "absent"))));
    }

    private static LiveSnapshotStore.ClassMark mark(long startsAt, String status) {
        return new LiveSnapshotStore.ClassMark("id" + startsAt, "COURSE", "Class", startsAt, status);
    }
}
