package com.iniexe.rollcall;

import com.iniexe.rollcall.ui.screens.courses.CourseMetricUiState;

final class CourseCardStateMapper {
    private CourseCardStateMapper() { }

    static CourseMetricUiState map(DashboardView.Course course, String kind, String accessibleName) {
        String detail = course.countsKnown
                ? course.percentLabel + " · " + course.attended + " / " + course.total + " attended"
                : "Attendance counts unavailable";
        return new CourseMetricUiState(kind, course.code, accessibleName, course.percent,
                course.percentLabel, detail, course.status, course.guidance,
                course.canMissText, course.mustAttendText, course.below, course.expanded, course.color);
    }
}
