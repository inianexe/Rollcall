package com.iniexe.rollcall;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Timetable {
    private static final Pattern DATE = Pattern.compile("\\b(\\d{1,2})[- /](Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[- /](\\d{4})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern COURSE = Pattern.compile("^(.+?)\\s*\\(\\s*([A-Za-z0-9][A-Za-z0-9_.-]*\\d[A-Za-z0-9_.-]*)\\s*\\)(?:\\s*\\([^()]*\\))*\\s*$");
    private static final Pattern TIME = Pattern.compile("(\\d{1,2}):(\\d{2})\\s*(AM|PM)\\s*[-–—]\\s*(\\d{1,2}):(\\d{2})\\s*(AM|PM)", Pattern.CASE_INSENSITIVE);

    static final class Event {
        final String name, code, label;
        final int start, end;
        Event(String name, String code, String label, int start, int end) { this.name=name; this.code=code; this.label=label; this.start=start; this.end=end; }
    }
    static final class Day {
        String date, holiday, reason;
        final List<Event> events = new ArrayList<>();
    }
    private static Integer toMinutes(String hour, String minute, String ampm) {
        int h=Integer.parseInt(hour), m=Integer.parseInt(minute);
        if (m>59 || h<1 || h>12) return null;
        return h%12*60+m+(ampm.equalsIgnoreCase("pm")?720:0);
    }
    static String today() { return LocalDate.now().toString(); }
    static Day parse(String text) {
        Day day=new Day(); Set<String> dates=new HashSet<>(); Matcher dm=DATE.matcher(text);
        while(dm.find()) try {
            LocalDate date=LocalDate.of(Integer.parseInt(dm.group(3)), month(dm.group(2)), Integer.parseInt(dm.group(1)));
            dates.add(date.toString());
        } catch (RuntimeException ignored) { }
        if (dates.size()!=1) { day.reason="Open the daily timetable with one selected date. Close Weekly Schedule first."; return day; }
        day.date=dates.iterator().next();
        Matcher holiday=Pattern.compile("Today\\s*:\\s*Holiday\\s*[:\\-]\\s*([^\\n]+)",Pattern.CASE_INSENSITIVE).matcher(text);
        if (holiday.find()) day.holiday=holiday.group(1).trim();
        String pendingName=null,pendingCode=null; StringBuilder times=new StringBuilder(); int unread=0;
        for (String raw:text.split("\\n")) {
            String line=raw.trim(); if(line.isEmpty())continue;
            Matcher course=COURSE.matcher(line);
            if(course.matches()) {
                if(pendingCode!=null) { if(!append(day,pendingName,pendingCode,times.toString()))unread++; }
                pendingName=course.group(1).trim(); pendingCode=course.group(2); times.setLength(0);
            } else if(pendingCode!=null) times.append(' ').append(line);
        }
        if(pendingCode!=null && !append(day,pendingName,pendingCode,times.toString())) unread++;
        day.events.sort(Comparator.comparingInt(event->event.start));
        if(day.holiday!=null&&!day.events.isEmpty()) { day.events.clear(); day.holiday=null; day.reason="The page contains both holiday and class entries. Check the selected date."; }
        else if(unread>0)day.reason=unread+" entries had unreadable times; captured list may be incomplete.";
        else if(!day.events.isEmpty())day.reason="Captured visible daily entries; verify against MyCamu.";
        else if(day.holiday!=null)day.reason="Holiday explicitly listed by MyCamu.";
        else day.reason="No readable classes. This does not confirm a free day.";
        return day;
    }
    private static boolean append(Day day,String name,String code,String text) {
        Matcher m=TIME.matcher(text); if(!m.find())return false;
        Integer start=toMinutes(m.group(1),m.group(2),m.group(3)),end=toMinutes(m.group(4),m.group(5),m.group(6));
        if(start==null||end==null||end<=start)return false;
        for(Event event:day.events)if(event.code.equals(code)&&event.start==start&&event.end==end)return true;
        day.events.add(new Event(name,code,m.group(),start,end)); return true;
    }
    private static int month(String name) {
        String[] months={"jan","feb","mar","apr","may","jun","jul","aug","sep","oct","nov","dec"};
        String key=name.substring(0,3).toLowerCase(Locale.ROOT);
        for(int i=0;i<months.length;i++)if(months[i].equals(key))return i+1;
        throw new IllegalArgumentException("Unknown month");
    }
    static Event next(List<Event> events,int now) { for(Event event:events)if(event.end>now)return event; return null; }
}
