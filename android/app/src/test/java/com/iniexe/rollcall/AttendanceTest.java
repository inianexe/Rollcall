package com.iniexe.rollcall;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AttendanceTest {
    @Test public void parsesCountsAndPercentageRows() {
        Attendance row = Attendance.parse("CS101 - Algorithms: 75% - (3 / 4)");

        assertEquals("CS101", row.code);
        assertEquals("Algorithms", row.name);
        assertEquals(3, row.attended);
        assertEquals(4, row.total);
        assertEquals(75.0, row.percentage(), 0.0);
        assertTrue(row.countsKnown);
    }

    @Test public void acceptsPercentageOnlyTooltipRowsWithoutInventingCounts() {
        Attendance row = Attendance.parse("■ CS101 - Algorithms: 73.5%");

        assertEquals(73.5, row.percentage(), 0.0);
        assertFalse(row.countsKnown);
        assertEquals(0, row.total);
        assertTrue(row.guidance(75).contains("Safe absence guidance is unavailable"));
    }

    @Test public void attendanceGuidanceUsesTargetBoundary() {
        Attendance aboveTarget = new Attendance("CS101", "Algorithms", 8, 10);
        Attendance belowTarget = new Attendance("CS101", "Algorithms", 5, 10);

        assertEquals(0, aboveTarget.safeAbsences(75));
        assertEquals(0, aboveTarget.periodsToRecover(75));
        assertEquals(10, belowTarget.periodsToRecover(75));
        assertEquals(Integer.MAX_VALUE, belowTarget.periodsToRecover(100));
    }

    @Test public void approvedOdCanBeExcludedOrCountedAsAttendance() {
        Attendance row = new Attendance("CS101", "Algorithms", 5, 8);

        Attendance excluded = row.withApprovedOd(2, true);
        assertEquals(5, excluded.attended);
        assertEquals(6, excluded.total);
        assertEquals(83.3333333333, excluded.percentage(), 0.000000001);

        Attendance counted = row.withApprovedOd(2, false);
        assertEquals(7, counted.attended);
        assertEquals(8, counted.total);
        assertEquals(87.5, counted.percentage(), 0.0);
    }

    @Test public void rejectsInconsistentPortalRowsAndInvalidCounts() {
        assertNull(Attendance.parse("CS101 - Algorithms: 75% - (2 / 4)"));
        assertNull(Attendance.parse("CS101 - Algorithms: 125%"));

        try {
            new Attendance("CS101", "Algorithms", 5, 4);
            fail("Expected invalid counts to be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected validation at the model boundary.
        }
    }
}
