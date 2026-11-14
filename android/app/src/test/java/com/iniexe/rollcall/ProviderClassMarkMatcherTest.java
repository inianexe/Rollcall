package com.iniexe.rollcall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class ProviderClassMarkMatcherTest {
    @Test public void matchesNormalizedCourseCodeWithinOneMinute() {
        LiveSnapshotStore.ClassSession session = session("CS-101", 1_000_000L);

        assertEquals(session, ProviderClassMarkMatcher.match("cs 101", 1_059_999L,
                Collections.singletonList(session)));
    }

    @Test public void doesNotMatchAtOrBeyondOneMinute() {
        LiveSnapshotStore.ClassSession session = session("CS101", 1_000_000L);

        assertNull(ProviderClassMarkMatcher.match("CS101", 1_060_000L, Collections.singletonList(session)));
        assertNull(ProviderClassMarkMatcher.match("CS101", 940_000L, Collections.singletonList(session)));
    }

    @Test public void rejectsEmptyAndDifferentCourseCodes() {
        LiveSnapshotStore.ClassSession session = session("CS101", 1_000_000L);

        assertNull(ProviderClassMarkMatcher.match("", 1_000_000L, Collections.singletonList(session)));
        assertNull(ProviderClassMarkMatcher.match("CS102", 1_000_000L, Collections.singletonList(session)));
    }

    @Test public void returnsFirstMatchingTimetableSession() {
        LiveSnapshotStore.ClassSession first = session("CS101", 1_000_000L);
        LiveSnapshotStore.ClassSession second = session("CS-101", 1_020_000L);

        assertEquals(first, ProviderClassMarkMatcher.match("CS101", 1_010_000L,
                Arrays.asList(first, second)));
    }

    private static LiveSnapshotStore.ClassSession session(String code, long startsAt) {
        return new LiveSnapshotStore.ClassSession("session", code, "Course", "", "", "class",
                startsAt, startsAt + 3_600_000L, false, false);
    }
}
