package com.iniexe.rollcall;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Date;

final class TimetableTransitionView extends SignalPanel {
    static final class Event {
        final String id, title, detail, time;
        final long start, end;
        final boolean allDay, cancelled, od;
        Event(String id, String title, String detail, String time, long start, long end, boolean allDay, boolean cancelled, boolean od) {
            this.id=id; this.title=title; this.detail=detail; this.time=time; this.start=start; this.end=end;
            this.allDay=allDay; this.cancelled=cancelled; this.od=od;
        }
    }
    private List<Event> events = Collections.emptyList();
    private final LinearLayout content;
    private final TextView dayButton, weekButton, title;
    private long selected = dayStart(System.currentTimeMillis());
    private boolean week = true, available, stale;

    TimetableTransitionView(Context context, AppPalette palette, Typeface font) {
        super(context, palette, font);
        LinearLayout modes = new LinearLayout(context);
        dayButton = action("Day", () -> switchMode(false));
        weekButton = action("Week", () -> switchMode(true));
        modes.addView(dayButton, new LayoutParams(0, -2, 1)); modes.addView(weekButton, new LayoutParams(0, -2, 1));
        addView(modes);
        LinearLayout dates = new LinearLayout(context);
        TextView previous = action("‹", () -> move(-1)); previous.setContentDescription("Previous " + (week ? "week" : "day"));
        TextView next = action("›", () -> move(1)); next.setContentDescription("Next day or week");
        title = copy("", 14, palette.ink); title.setGravity(android.view.Gravity.CENTER);
        dates.addView(previous, new LayoutParams(dp(48), -2));
        dates.addView(title, new LayoutParams(0, -1, 1)); dates.addView(next, new LayoutParams(dp(48), -2));
        addSpace(dates, 8);
        content = new LinearLayout(context); content.setOrientation(VERTICAL); addSpace(content, 8);
    }
    void update(List<Event> events, boolean available, boolean stale) {
        this.events = Collections.unmodifiableList(new ArrayList<>(events)); this.available=available; this.stale=stale;
        render(false);
    }
    static long dayStart(long time) {
        Calendar c = Calendar.getInstance(); c.setTimeInMillis(time);
        c.set(Calendar.HOUR_OF_DAY,0); c.set(Calendar.MINUTE,0); c.set(Calendar.SECOND,0); c.set(Calendar.MILLISECOND,0);
        return c.getTimeInMillis();
    }
    static boolean active(Event event, long now) {
        return !event.cancelled && !event.allDay && event.start > 0 && event.end > event.start && event.start <= now && now < event.end;
    }
    private void move(int n) {
        Calendar c=Calendar.getInstance(); c.setTimeInMillis(selected); c.add(Calendar.DATE, n*(week?7:1)); selected=c.getTimeInMillis(); render(true);
    }
    private void switchMode(boolean value) { if (week != value) { week=value; render(true); } }
    private void render(boolean animate) {
        content.animate().cancel(); content.setAlpha(1); content.setTranslationY(0); content.removeAllViews();
        dayButton.setSelected(!week); weekButton.setSelected(week);
        dayButton.setBackground(surface(!week ? palette.soft : palette.surface));
        weekButton.setBackground(surface(week ? palette.soft : palette.surface));
        dayButton.setContentDescription("Day view" + (!week ? ", selected" : ""));
        weekButton.setContentDescription("Week view" + (week ? ", selected" : ""));
        Calendar date = Calendar.getInstance(); date.setTimeInMillis(selected);
        if (week) { int offset=(date.get(Calendar.DAY_OF_WEEK)+5)%7; date.add(Calendar.DATE,-offset); }
        SimpleDateFormat label = new SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault());
        title.setText((week ? "Week of " : "") + new SimpleDateFormat("d MMM", Locale.getDefault()).format(date.getTime()));
        long now=System.currentTimeMillis();
        for (int d=0; d<(week?7:1); d++,date.add(Calendar.DATE,1)) {
            long day=date.getTimeInMillis(); Calendar after=(Calendar)date.clone(); after.add(Calendar.DATE,1);
            ArrayList<Event> matches=new ArrayList<>();
            for (Event e:events) if(e.start>=day && e.start<after.getTimeInMillis()) matches.add(e);
            TextView heading=action(label.format(new Date(day)) + (day==selected ? " · Selected" : ""), () -> { selected=day; week=false; render(true); });
            LayoutParams hp=new LayoutParams(-1,-2); hp.topMargin=dp(d==0?0:12); content.addView(heading,hp);
            if (matches.isEmpty()) {
                TextView empty=copy(!available ? "Schedule unavailable · Sync MyCamu" : stale ? "No classes in saved timetable · May be out of date" : "No classes scheduled",12,palette.muted);
                empty.setPadding(dp(12),dp(8),dp(12),dp(8)); content.addView(empty);
            }
            for(Event e:matches) {
                String status=e.cancelled ? "Cancelled · " : !stale && active(e,now) ? "In progress · " : "";
                TextView row=copy(status+e.title+"\n"+(e.allDay?"All day":e.time.isEmpty()?"Time unavailable":e.time)
                        +(e.detail.isEmpty()?"":" · "+e.detail)+(e.od?"\nApproved OD":"")
                        +(stale?"\nSaved schedule · Current timing not confirmed":""),14,palette.ink);
                row.setTag(e.id); row.setPadding(dp(12),dp(10),dp(12),dp(10)); content.addView(row);
            }
        }
        if(animate && isShown() && NativeMotion.enabled(getContext())) {
            content.setAlpha(.3f); content.setTranslationY(dp(6));
            content.animate().alpha(1).translationY(0).setDuration(SignalDrawing.FOLD).start();
        }
    }
    private void stop() { if(content!=null) { content.animate().cancel(); content.setAlpha(1); content.setTranslationY(0); } }
    @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }
    @Override protected void onVisibilityChanged(View changed,int visibility) { super.onVisibilityChanged(changed,visibility); if(!isShown()) stop(); }
    @Override protected void onWindowVisibilityChanged(int visibility) { super.onWindowVisibilityChanged(visibility); if(visibility!=VISIBLE) stop(); }
}
