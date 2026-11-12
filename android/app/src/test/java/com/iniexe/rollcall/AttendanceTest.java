package com.iniexe.rollcall;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AttendanceTest {
    @Test public void preservesSafeAbsenceAndRecoveryResultsAtSeventyFivePercent() {
        Attendance safe = new Attendance("CS101", "Course", 18, 20);
        Attendance recovering = new Attendance("CS102", "Course", 12, 20);

        assertEquals(4, safe.safeAbsences(75));
        assertEquals(0, safe.periodsToRecover(75));
        assertEquals(0, recovering.safeAbsences(75));
        assertEquals(12, recovering.periodsToRecover(75));
    }

    @Test public void exactTargetAndNoClassesKeepExistingGuidanceBehavior() {
        Attendance exact = new Attendance("CS101", "Course", 3, 4);
        Attendance empty = new Attendance("CS102", "Course", 0, 0);

        assertEquals(0, exact.safeAbsences(75));
        assertEquals(0, exact.periodsToRecover(75));
        assertEquals("One missed period would put you below 75%", exact.guidance(75));
        assertEquals("No classes recorded yet", empty.guidance(75));
    }

    @Test public void oneHundredPercentTargetCannotBeRecoveredAfterAnAbsence() {
        Attendance partial = new Attendance("CS101", "Course", 9, 10);

        assertEquals(Integer.MAX_VALUE, partial.periodsToRecover(100));
        assertEquals("A 100% target cannot be recovered after missed periods", partial.guidance(100));
    }
}
