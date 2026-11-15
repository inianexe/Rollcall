package com.iniexe.rollcall;

import org.junit.Test;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class InternalMarkTest {
    @Test public void validatesValuesSupportsWithheldAndReplacesDuplicateAssessmentIds() {
        Map<String, Object> first = mark("cia-1", "Networks", "CIA 1", 18, 20, "published");
        Map<String, Object> replacement = mark("cia-1", "Networks", "CIA 1", 19, 20, "published");
        Map<String, Object> invalid = mark("cia-2", "Networks", "CIA 2", 21, 20, "published");
        Map<String, Object> withheld = mark("cia-3", "Networks", "CIA 3", null, null, "withheld");
        assertEquals(2, InternalMark.fromValues(Arrays.asList(first, replacement, invalid, withheld)).size());
        assertEquals(19, InternalMark.fromValues(Arrays.asList(first, replacement)).get(0).score, 0);
        InternalMark hidden = InternalMark.fromValues(Arrays.asList(withheld)).get(0);
        assertFalse(hidden.scoreAvailable);
        assertEquals("withheld", hidden.status);
        assertEquals("Networks", hidden.course);
    }

    private static Map<String, Object> mark(String id, String course, String assessment,
                                             Number score, Number maximum, String status) {
        Map<String, Object> row = new HashMap<>();
        row.put("assessmentId", id); row.put("course", course); row.put("assessment", assessment);
        row.put("status", status); row.put("score", score); row.put("maximum", maximum);
        return row;
    }
}
