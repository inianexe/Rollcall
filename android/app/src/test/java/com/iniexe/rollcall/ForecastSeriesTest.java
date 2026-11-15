package com.iniexe.rollcall;

import org.junit.Test;
import static org.junit.Assert.*;

public class ForecastSeriesTest {
    @Test public void forecastKeepsRecordedCountsAndUsesDiscretePoints() {
        Attendance a = new Attendance("OS", "Systems", 8, 10);
        ForecastSeries miss = new ForecastSeries(a, a, false, 4, 75);
        assertEquals(80, miss.recorded, .001);
        assertEquals(100.0 * 8 / 14, miss.points[4], .001);
        assertEquals(1, miss.crossing());
        assertEquals(10, a.total);
    }
    @Test public void zeroAndOneClassAreNotInventedPercentages() {
        Attendance a = new Attendance("OS", "Systems", 0, 0);
        ForecastSeries f = new ForecastSeries(a, a, true, 1, 100);
        assertTrue(Double.isNaN(f.recorded));
        assertTrue(Double.isNaN(f.points[0]));
        assertEquals(100, f.points[1], 0);
        assertEquals(-1, f.crossing());
    }
    @Test public void odPreviewKeepsRecordedAnchor() {
        Attendance a = new Attendance("OS", "Systems", 7, 10);
        ForecastSeries f = new ForecastSeries(a, a.withApprovedOd(1, false), true, 0, 75);
        assertEquals(70, f.recorded, 0);
        assertEquals(80, f.points[0], 0);
        assertTrue(f.odPreview);
    }
    @Test public void exactTargetAndMaximumScenario() {
        for (int attended : new int[]{0, 1, 3, 4}) {
            Attendance a = new Attendance("OS", "Systems", attended, 4);
            for (boolean attend : new boolean[]{true, false}) {
                ForecastSeries f = new ForecastSeries(a, a, attend, 20, 75);
                assertEquals(100.0 * (attended + (attend ? 20 : 0)) / 24, f.points[20], .0001);
            }
        }
    }
}
