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
        Attendance perfect = new Attendance("CS102", "Course", 10, 10);

        assertEquals(Integer.MAX_VALUE, partial.periodsToRecover(100));
        assertEquals("A 100% target cannot be recovered after missed periods", partial.guidance(100));
        assertEquals(0, perfect.safeAbsences(100));
        assertEquals(Integer.MAX_VALUE, perfect.periodsToRecover(100));
    }

    @Test public void zeroAttendanceAndDifferentTargetsKeepExistingCalculations() {
        Attendance none = new Attendance("CS101", "Course", 0, 5);
        Attendance nineOfTen = new Attendance("CS102", "Course", 9, 10);

        assertEquals(0, none.safeAbsences(75));
        assertEquals(15, none.periodsToRecover(75));
        assertEquals("Attend 15 periods in a row to reach 75%", none.guidance(75));
        assertEquals(8, nineOfTen.safeAbsences(50));
        assertEquals(0, nineOfTen.periodsToRecover(90));
        assertEquals(10, nineOfTen.periodsToRecover(95));
    }

    @Test public void existingRoundingAndInvalidInputBehaviorAreCharacterized() {
        Attendance rounded = new Attendance("CS101", "Course", 11, 13);
        assertEquals(1, rounded.safeAbsences(75));
        assertEquals(0, rounded.periodsToRecover(75));
        assertEquals(null, Attendance.parse(""));
        assertEquals(null, Attendance.parse("CS101 - Course: 90% (1/2)"));
        assertEquals(false, Attendance.parse("CS101 - Course: 50%").countsKnown);
    }

    @Test public void invalidConstructorCountsRemainRejected() {
        org.junit.Assert.assertThrows(IllegalArgumentException.class,
                () -> new Attendance("CS101", "Course", 2, 1));
    }
}
