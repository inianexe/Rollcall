package com.iniexe.rollcall;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.Collections;

public class HistorySeriesTest {
    private HistorySeries.Event e(String id, String status, long time) {
        return new HistorySeries.Event(id, "Synthetic class", status, time, false);
    }
    @Test public void partialHistoryNeverBuildsPercentageCurve() {
        HistorySeries h = new HistorySeries(Arrays.asList(e("1", "present", 1), e("2", "absent", 2)), 80, 100, true);
        assertFalse(h.complete); assertEquals(0, h.percentages.length);
    }
    @Test public void completeHistoryReconcilesDeduplicatesAndSorts() {
        HistorySeries h = new HistorySeries(Arrays.asList(e("2", "absent", 2), e("1", "present", 1), e("1", "present", 1)), 1, 2, true);
        assertTrue(h.complete); assertEquals(100, h.percentages[0], 0); assertEquals(50, h.percentages[1], 0);
    }
    @Test public void unknownTimeStatusAndUnreconciledDataStayCategorical() {
        for (HistorySeries.Event e : Arrays.asList(e("1", "unknown", 1), e("1", "present", 0), e("", "present", 1)))
            assertFalse(new HistorySeries(Collections.singletonList(e), 1, 1, true).complete);
        assertFalse(new HistorySeries(Collections.singletonList(e("1", "present", 1)), 1, 1, false).complete);
        assertFalse(new HistorySeries(Collections.emptyList(), 0, 0, true).complete);
    }
}
