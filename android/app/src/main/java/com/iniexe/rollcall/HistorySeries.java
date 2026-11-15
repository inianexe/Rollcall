package com.iniexe.rollcall;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

final class HistorySeries {
    static final class Event {
        final String id, label, status;
        final long time;
        final boolean approvedOd;
        Event(String id, String label, String status, long time, boolean approvedOd) {
            this.id = id; this.label = label; this.status = status; this.time = time; this.approvedOd = approvedOd;
        }
    }
    final List<Event> events;
    final boolean complete;
    final double[] percentages;
    HistorySeries(List<Event> source, int attended, int total, boolean reconciled) {
        ArrayList<Event> unique = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        boolean known = true;
        int present = 0;
        for (Event e : source) {
            if (!e.id.isEmpty() && !seen.add(e.id)) continue;
            unique.add(e);
            if ("present".equals(e.status)) present++;
            else if (!"absent".equals(e.status)) known = false;
            if (e.time <= 0 || e.id.isEmpty()) known = false;
        }
        Collections.sort(unique, Comparator.comparingLong(e -> e.time));
        events = Collections.unmodifiableList(unique);
        complete = reconciled && known && total > 0 && unique.size() == total && present == attended;
        percentages = complete ? new double[unique.size()] : new double[0];
        present = 0;
        for (int i = 0; i < percentages.length; i++) {
            if ("present".equals(unique.get(i).status)) present++;
            percentages[i] = 100.0 * present / (i + 1);
        }
    }
}
