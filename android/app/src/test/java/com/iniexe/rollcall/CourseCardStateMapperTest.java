package com.iniexe.rollcall;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CourseCardStateMapperTest {
    @Test public void mapsExistingAttendanceOutputsWithoutRecalculatingThem() {
        DashboardView.Course course = new DashboardView.Course();
        course.code = "CS101L";
        course.name = "Programming Lab";
        course.percent = 60f;
        course.percentLabel = "60%";
        course.attended = 12;
        course.total = 20;
        course.countsKnown = true;
        course.status = "Below target";
        course.guidance = "Attend 12 classes in a row to reach 75%";
        course.canMissText = "0";
        course.mustAttendText = "12";
        course.below = true;
        course.expanded = true;
        course.color = 0xffaa2921;

        com.iniexe.rollcall.ui.screens.courses.CourseMetricUiState state =
                CourseCardStateMapper.map(course, "LAB", "Programming Lab");

        assertEquals("LAB", state.getKind());
        assertEquals("60% · 12 / 20 attended", state.getDetail());
        assertEquals("0", state.getCanMiss());
        assertEquals("12", state.getMustAttend());
        assertEquals("Attend 12 classes in a row to reach 75%", state.getGuidance());
        assertTrue(state.getBelowTarget());
        assertTrue(state.getExpanded());
    }

    @Test public void unavailableAttendanceRemainsUnavailable() {
        DashboardView.Course course = new DashboardView.Course();
        course.code = "CS101";
        course.canMissText = "—";
        course.mustAttendText = "—";
        com.iniexe.rollcall.ui.screens.courses.CourseMetricUiState state =
                CourseCardStateMapper.map(course, "THEORY", "Programming");

        assertEquals("Attendance counts unavailable", state.getDetail());
        assertEquals("—", state.getCanMiss());
        assertEquals("—", state.getMustAttend());
    }
}
