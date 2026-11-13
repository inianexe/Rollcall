package com.iniexe.rollcall;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AcademicRecordsTest {
    @Test public void mapsKctGradesToExistingTenPointScale() {
        assertEquals(10, AcademicRecords.gradePoint("o"));
        assertEquals(10, AcademicRecords.gradePoint("S"));
        assertEquals(9, AcademicRecords.gradePoint("A+"));
        assertEquals(8, AcademicRecords.gradePoint("A"));
        assertEquals(7, AcademicRecords.gradePoint("B+"));
        assertEquals(6, AcademicRecords.gradePoint("B"));
        assertEquals(5, AcademicRecords.gradePoint("C"));
        assertEquals(0, AcademicRecords.gradePoint("WD"));
        assertEquals(-1, AcademicRecords.gradePoint("I"));
        assertEquals(-1, AcademicRecords.gradePoint(null));
    }

    @Test public void parsesSemesterOrderingAndDisplayLabels() {
        assertEquals(20263, AcademicRecords.semesterNumber("2026 - Semester 3"));
        assertEquals(2, AcademicRecords.semesterNumber("Semester: 2"));
        assertEquals(0, AcademicRecords.semesterNumber("Summer Term"));
        assertEquals("Semester 3", AcademicRecords.semesterTitle("2026 - Semester   3 Results"));
        assertEquals("S3", AcademicRecords.semesterShortLabel("Semester 3 · 2026"));
        assertEquals("Sem", AcademicRecords.semesterShortLabel("Summer Term"));
    }
}
