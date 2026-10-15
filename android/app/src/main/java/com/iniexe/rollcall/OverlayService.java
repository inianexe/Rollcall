package com.iniexe.rollcall;

import android.app.Service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.content.pm.ServiceInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

public class OverlayService extends Service {
    private static final List<Attendance> manualRows = new ArrayList<>();
    private WindowManager window;
    private LinearLayout panel;
    private TextView status, results;
    private Button scanButton;
    private final List<Attendance> rows = new ArrayList<>();
    private WindowManager.LayoutParams params;
    private boolean todayView;
    private long scannedAt;
    private Timetable.Day timetable;
    private int dp(float value) { return (int)(value * getResources().getDisplayMetrics().density); }

    @Override public void onCreate() {
        super.onCreate();
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return; }
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(1, notification());
        window = (WindowManager)getSystemService(WINDOW_SERVICE);
        panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(dp(12), dp(10), dp(12), dp(10)); panel.setBackgroundColor(0xFFFFF8EC); panel.setElevation(dp(8));
        TextView header = new TextView(this); header.setText("◉  Rollcall  ·  drag to move"); header.setTextColor(0xFF244B3E); header.setTextSize(16); header.setPadding(dp(4), dp(5), dp(4), dp(8)); panel.addView(header);
        status = new TextView(this); status.setText("Open MyCamu Attendance → Subject-wise, then scan."); status.setTextColor(0xFF52645A); panel.addView(status);
        LinearLayout actions = new LinearLayout(this);
        Button attendance = button("Attendance"); attendance.setOnClickListener(v -> { todayView=false; render(); }); actions.addView(attendance,new LinearLayout.LayoutParams(0,dp(44),1));
        Button today = button("Today"); today.setOnClickListener(v -> { todayView=true; render(); }); actions.addView(today,new LinearLayout.LayoutParams(0,dp(44),1));
        scanButton = button("Scan"); scanButton.setOnClickListener(v -> { if(todayView)readDay();else scan(); }); actions.addView(scanButton,new LinearLayout.LayoutParams(0,dp(44),1));
        Button close = button("Hide"); close.setOnClickListener(v -> stopSelf()); actions.addView(close,new LinearLayout.LayoutParams(0,dp(44),1)); panel.addView(actions);
        ScrollView scroll = new ScrollView(this); results = new TextView(this); results.setTextColor(0xFF263C33); results.setTextSize(14); scroll.addView(results); panel.addView(scroll);
        header.setOnTouchListener(new View.OnTouchListener() {
            float x,y; int left,top;
            @Override public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction()==MotionEvent.ACTION_DOWN) { x=e.getRawX(); y=e.getRawY(); left=params.x; top=params.y; return true; }
                if (e.getAction()==MotionEvent.ACTION_MOVE) { params.x=left+(int)(x-e.getRawX()); params.y=top+(int)(e.getRawY()-y); window.updateViewLayout(panel,params); return true; }
                return true;
            }
        });
        params = new WindowManager.LayoutParams(dp(300), dp(340), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.END; params.x=dp(12); params.y=dp(100);
        window.addView(panel, params);
    }
    private Notification notification() {
        String channel="rollcall_overlay";
        if (Build.VERSION.SDK_INT >= 26) ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel(channel,"Rollcall overlay",NotificationManager.IMPORTANCE_LOW));
        Intent open=new Intent(this,MainActivity.class);
        PendingIntent pending=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder builder=Build.VERSION.SDK_INT >= 26?new Notification.Builder(this,channel):new Notification.Builder(this);
        return builder.setSmallIcon(android.R.drawable.ic_menu_info_details).setContentTitle("Rollcall overlay is on").setContentText("Attendance guidance is available over MyCamu.").setOngoing(true).setContentIntent(pending).build();
    }
    private Button button(String label) { Button b=new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(12); b.setMinWidth(0); b.setPadding(dp(2),0,dp(2),0); b.setBackgroundTintList(ColorStateList.valueOf(0xFF244B3E)); b.setTextColor(0xFFFFFFFF); return b; }
    private void scan() {
        List<Attendance> found = MyCamuAccessibilityService.readAttendance();
        synchronized (manualRows) { found.addAll(manualRows); }
        java.util.LinkedHashMap<String,Attendance> unique=new java.util.LinkedHashMap<>(); for (Attendance row:found) unique.put(row.code,row); found=new ArrayList<>(unique.values());
        if (found.isEmpty()) { status.setText("No readable attendance found. Open Subject-wise; if MyCamu hides chart text, use manual entry in the Rollcall app."); return; }
        rows.clear(); rows.addAll(found); scannedAt=System.currentTimeMillis(); status.setText(rows.size()+" courses read from the visible screen."); render();
    }
    private void readDay() {
        timetable=Timetable.parse(MyCamuAccessibilityService.readVisibleText());
        if(timetable.date==null)status.setText("Select one MyCamu timetable day and enable Rollcall text reading.");
        else status.setText(timetable.date+" · "+timetable.reason);
        render();
    }
    private void render() {
        scanButton.setText(todayView?"Read day":"Scan");
        if(todayView){ renderDay(); return; }
        if(rows.isEmpty()){ if(results!=null)results.setText("Scan Attendance → Subject-wise to read courses."); return; }
        StringBuilder out=new StringBuilder();
        for (Attendance row: rows) out.append(row.code).append(" · ").append(row.name).append("\n").append(String.format(java.util.Locale.getDefault(), "%.2f%%  (%d/%d)", row.percentage(), row.attended, row.total)).append("\n").append(row.guidance()).append("\n\n");
        results.setText(out.toString());
    }
    private void renderDay() {
        if(timetable==null){results.setText("Open MyCamu’s daily Timetable, select one day, then tap Read day.");return;}
        if(timetable.date==null){results.setText(timetable.reason);return;}
        StringBuilder out=new StringBuilder(timetable.date).append("\n").append(timetable.reason).append("\n\n");
        if(timetable.holiday!=null)out.append("Holiday · ").append(timetable.holiday).append('\n');
        int now=java.time.LocalTime.now().getHour()*60+java.time.LocalTime.now().getMinute();
        Timetable.Event next=Timetable.today().equals(timetable.date)?Timetable.next(timetable.events,now):null;
        for(Timetable.Event event:timetable.events){
            out.append(event.label).append(event==next?(event.start<=now?" · NOW":" · NEXT"):"").append("\n").append(event.name).append("\n").append(event.code).append('\n');
            Attendance row=null;for(Attendance item:rows)if(item.code.equals(event.code)){row=item;break;}
            if(row!=null&&scannedAt>0&&System.currentTimeMillis()-scannedAt<15*60*1000)out.append(String.format(java.util.Locale.getDefault(),"%.2f%% · ",row.percentage())).append(row.guidance()).append('\n');
            else out.append(row==null?"Scan attendance to match this course by code.":"Attendance is stale. Scan attendance again.").append('\n');
            out.append('\n');
        }
        results.setText(out.toString());
    }
    static void addManual(Attendance row) { synchronized (manualRows) { manualRows.removeIf(old -> old.code.equals(row.code)); manualRows.add(row); } }
    @Override public int onStartCommand(Intent intent,int flags,int startId) { return START_NOT_STICKY; }
    @Override public void onDestroy() { if (panel!=null && window!=null) window.removeView(panel); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
