package com.iniexe.rollcall;

import org.junit.Test;
import static org.junit.Assert.*;

public class AttendanceEventNotifierTest {
    @Test public void attendanceTargetBoundaryCountsAsMeetingTarget() {
        assertFalse(AttendanceEventNotifier.targetSide(74.99, 75));
        assertTrue(AttendanceEventNotifier.targetSide(75.0, 75));
    }

    @Test public void onlyOdDecisionsAreAlertableStates() {
        assertTrue(AttendanceEventNotifier.isDecision("Approved"));
        assertTrue(AttendanceEventNotifier.isDecision("Rejected"));
        assertFalse(AttendanceEventNotifier.isDecision("Pending"));
        assertFalse(AttendanceEventNotifier.isDecision("Submitted"));
    }
}
