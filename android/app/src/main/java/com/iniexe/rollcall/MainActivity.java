package com.iniexe.rollcall;

import android.annotation.SuppressLint;
import android.animation.Animator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.android.gms.tasks.Tasks;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {
    private static final int REMINDER_PERMISSION_REQUEST = 7301;
    private static final int ONBOARDING_NOTIFICATION_REQUEST = 7302;
    private static final String CONNECTED_KEY = "mycamu_connected";
    private static final String MYCAMU_CONSENT_KEY = "mycamu_data_access_consent";
    private static final String DARK_KEY = "dark_theme";
    private static final String LIGHT_PALETTE_KEY = "light_palette";
    private static final String DARK_PALETTE_KEY = "dark_palette";
    private static final String COLOR_THEME_KEY = "color_theme";
    private static final String ONBOARDING_STARTED_KEY = "onboarding_started";
    private static final String NOTIFICATION_PERMISSION_DENIED_KEY = "notification_permission_denied";
    private static final String ACADEMIC_RECORDS_KEY = "kct_academic_records";
    private static final String ACADEMIC_UPDATED_KEY = "kct_academic_updated_at";
    private static final String UPCOMING_EXAM_KEY = "kct_upcoming_exam";
    static final String EXTRA_WIDGET_DESTINATION = "widget_destination";
    private int INK, MUTED, CANVAS, GREEN, AMBER, RED, SURFACE, BORDER, SECONDARY, TRACK, BUTTON, BUTTON_TEXT;
    private int HIGHLIGHT, HERO, HERO_TEXT, HERO_MUTED;
    private Typeface appTypeface;
    private static final String HOME = "https://www.mycamu.co.in/v2/attendance";
    private static final long AUTO_SYNC_INTERVAL_MS = 30_000L;
    private static final long ACADEMIC_SYNC_INTERVAL_MS = 15 * 60_000L;
    private static final long CLASS_LOG_SYNC_INTERVAL_MS = 60_000L;
    private static final long SIGN_IN_CHECK_INTERVAL_MS = 4_000L;
    private static final long WEB_PAGE_CHECK_INTERVAL_MS = 4_000L;
    private static final long RESULT_POLL_INTERVAL_MS = 400L;
    private static final long SYNC_TIMEOUT_MS = 120_000L;
    private static final int MAX_RESULT_POLLS = 300;
    private static final String START_SYNC = "(function(includeAcademic,includeHistory){"
            + "window.__rollcallAttendanceResult={state:'loading'};"
            + "try{"
            + "var programRaw=localStorage.getItem('ngStorage-progressionData');"
            + "var programs=programRaw?JSON.parse(programRaw):null;"
            + "if(!programs||!programs.length){window.__rollcallAttendanceResult={state:'profile'};return 'profile';}"
            + "var p=programs[0];"
            + "var body={InId:p.InId,PrID:p.PrID,CrID:p.CrID,DeptID:p.DeptID,SemID:p.SemID,AcYr:p.AcYr,SecID:p.SecID,CmProgID:p._id,StuID:p.StuID,isFE:!!p.isFE,isForWeb:true,isFrAbLg:true};"
            + "function reason(e){var m=e&&e.message||'';if(m==='auth')return 'sign-in expired';if(m==='shape')return 'unexpected response';if(/^http:\\d+$/.test(m))return 'HTTP '+m.slice(5);if(e&&e.name==='AbortError')return 'request timed out';return 'network error';}function postJson(path,payload){var controller=new AbortController(),timer=setTimeout(function(){controller.abort();},10000);return fetch(path,{method:'POST',credentials:'same-origin',redirect:'error',cache:'no-store',signal:controller.signal,headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)}).then(function(r){if(r.status===401||r.status===403)throw new Error('auth');if(path==='/api/Timetable/get'&&r.status===500)return {__rollcallNoSchedule:true};if(!r.ok)throw new Error('http:'+r.status);return r.json();}).finally(function(){clearTimeout(timer);});}"
            + "function text(v){if(typeof v==='string'||typeof v==='number')return String(v).slice(0,160);if(v&&typeof v==='object')return String(v.Name||v.Nm||v.name||'').slice(0,160);return '';}"
            + "var timetablePromise=Promise.resolve({state:'cached',rows:window.__rollcallTimetableRows||[]}),leavePromise=Promise.resolve({state:'skipped'});"
            + "if(includeAcademic){var today=new Date(),weekStart=new Date(today);weekStart.setHours(0,0,0,0);weekStart.setDate(today.getDate()-today.getDay());var weekEnd=new Date(weekStart);weekEnd.setDate(weekStart.getDate()+6);function ymd(d){return d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0');}"
            + "var timetableBody=Object.assign({},body,{start:ymd(weekStart),end:ymd(weekEnd),schdlTyp:'showCurWeek',isShowCancelledPeriod:true,isFromTt:true});"
            + "timetablePromise=postJson('/api/Timetable/get',timetableBody).then(function(r){if(r&&r.__rollcallNoSchedule){window.__rollcallTimetableRows=[];return {state:'ready',rows:[],empty:true};}var groups=r&&r.output&&r.output.data;if(!Array.isArray(groups))throw new Error('shape');var rows=[];function camuTime(v){if(v==null)return NaN;if(typeof v==='number')return v;var s=String(v).trim(),m=s.match(/^(\\d{4})-(\\d{2})-(\\d{2})(?:[T ](\\d{2}):(\\d{2})(?::(\\d{2})(?:\\.(\\d+))?)?(Z|([+-])(\\d{2}):?(\\d{2}))?)?$/i);if(m){var utcWall=!m[8]||m[8].toUpperCase()==='Z'||(m[9]&&Number(m[10])===0&&Number(m[11])===0);if(utcWall){var ms=m[7]?Number((m[7]+'00').slice(0,3)):0;return new Date(Number(m[1]),Number(m[2])-1,Number(m[3]),Number(m[4]||0),Number(m[5]||0),Number(m[6]||0),ms).getTime();}}return Date.parse(s);}function add(items,isEvent){if(!Array.isArray(items))return;items.forEach(function(e){if(!e||typeof e!=='object')return;var start=camuTime(e.start||e.eventDate||'');var end=camuTime(e.end||e.start||'');if(!Number.isFinite(start))return;var allDay=!!e.allDay||isEvent;var kind=text(e.type||e.eTy)||(isEvent?'event':'class');rows.push({id:text(e._id||e.id||e.PeriodId)||text(e.SubCd)+'_'+start,code:text(e.SubCd),name:text(e.SubNa||e.title)||'Class',staff:text(e.StaffNm),location:text(e.LocBtch),kind:kind,startsAt:start,endsAt:Number.isFinite(end)?end:start,allDay:allDay,cancelled:/critical|cancel/i.test(kind+' '+text(e.className))});});}add(groups[0],false);add(groups[1],false);add(groups[2],true);window.__rollcallTimetableRows=rows;return {state:'ready',rows:rows};}).catch(function(e){return {state:'error',reason:reason(e)};});"
            + "leavePromise=postJson('/api/Leave/getAllleave/',{StuID:p.StuID,isFrmReact:true}).then(function(r){var values=r&&r.output&&r.output.data;if(!Array.isArray(values))throw new Error('shape');"
            + "function periodId(v){if(typeof v==='string'||typeof v==='number')return String(v);if(v&&typeof v==='object')return String(v._id||v.id||v.periodId||v.PeriodId||v.PeriodID||v.PerID||'');return '';}function periodNo(v){if(v&&typeof v==='object'){var keys=['PeriodNo','periodNo','period_no','PeriodNumber','periodNumber','period_number','PerNo','perNo','per_no','Period','period','PNo','pNo','Sequence','sequence','SeqNo','seqNo'];for(var k=0;k<keys.length;k++){if(v[keys[k]]!=null){v=v[keys[k]];break;}}}var m=String(v==null?'':v).match(/^\\s*(?:period|per|p)?\\s*([1-9]\\d*)\\s*$/i);return m?Number(m[1]):0;}"
            + "var rows=values.map(function(e){var raw=e.periods,list=Array.isArray(raw)?raw:(raw==null||raw===''||Number(raw)===0?[]:[raw]),count=Array.isArray(raw)?raw.length:(typeof raw==='number'?raw:(raw==null||raw===''?0:list.length)),ids=[],numbers=[],semId=text(e.SemID||e.SemId||e.SemesterID||e.SemesterId||e.semId||e.semID||e.semesterId||e.sem_id),acYr=text(e.AcYr||e.AcademicYr||e.AcademicYear||e.acYr||e.academicYear||e.academic_year);list.forEach(function(v){var id=periodId(v),n=periodNo(v);if(id&&ids.indexOf(id)<0)ids.push(id);if(n>0&&n<=24&&numbers.indexOf(n)<0)numbers.push(n);});numbers.sort(function(a,b){return a-b;});return{id:text(e._id||e.refId),type:text(e.LvTy)||'Leave',status:text(e.Stus)||'Unknown',startsAt:Date.parse(e.start||''),endsAt:Date.parse(e.end||e.start||''),periods:count,periodIds:ids,periodNumbers:numbers,semId:semId,acYr:acYr,currentSemester:!!(semId||acYr)&&(!semId||String(semId)===String(p.SemID))&&(!acYr||String(acYr)===String(p.AcYr)),semesterKnown:!!(semId||acYr),fullDay:!ids.length&&!numbers.length};}).filter(function(e){return e.startsAt>0;});var dayKeys=[],dayContexts={};rows.forEach(function(item){if(!item.fullDay&&!item.periodIds.length&&!item.periodNumbers.length)return;var first=new Date(item.startsAt),last=new Date(item.endsAt>0?item.endsAt:item.startsAt);first.setHours(0,0,0,0);last.setHours(0,0,0,0);for(var day=new Date(first),count=0;day<=last&&count<31;day.setDate(day.getDate()+1),count++){var key=ymd(day);if(dayKeys.indexOf(key)<0)dayKeys.push(key);if(!dayContexts[key])dayContexts[key]={semId:item.semId,acYr:item.acYr};}});"
            + "return Promise.all(dayKeys.map(function(key){var context=dayContexts[key]||{},dayBody=Object.assign({},timetableBody,{start:key,end:key});if(context.semId)dayBody.SemID=context.semId;if(context.acYr)dayBody.AcYr=context.acYr;delete dayBody.schdlTyp;return postJson('/api/Timetable/get',dayBody).then(function(response){var groups=response&&response.output&&response.output.data;if(!Array.isArray(groups))return {date:key,slots:[]};var slots=[];[groups[0],groups[1]].forEach(function(part){if(!Array.isArray(part))return;part.forEach(function(e){if(!e||typeof e!=='object')return;var id=String(e.PeriodId||e.PeriodID||e.PerID||e.periodId||''),startsAt=Date.parse(e.start||e.eventDate||'');if(!Number.isFinite(startsAt))return;slots.push({periodId:id,startsAt:startsAt,endsAt:Date.parse(e.end||''),name:text(e.SubNa||e.title)||'Class',code:text(e.SubCd)});});});slots.sort(function(a,b){return a.startsAt-b.startsAt;});return {date:key,slots:slots};}).catch(function(){return {date:key,slots:[]};});})).then(function(days){var schedules={};days.forEach(function(day){schedules[day.date]=day.slots;});rows.forEach(function(item){var details=[],mapped=[];var first=new Date(item.startsAt),last=new Date(item.endsAt>0?item.endsAt:item.startsAt);first.setHours(0,0,0,0);last.setHours(0,0,0,0);for(var day=new Date(first),count=0;day<=last&&count<31;day.setDate(day.getDate()+1),count++){var key=ymd(day),slots=schedules[key]||[];slots.forEach(function(slot,index){var chosen=item.fullDay||(item.periodIds.length?item.periodIds.indexOf(slot.periodId)>=0:item.periodNumbers.indexOf(index+1)>=0);if(chosen){mapped.push(index+1);details.push({period:index+1,date:key,time:new Date(slot.startsAt).toLocaleTimeString([], {hour:'numeric',minute:'2-digit'}),endTime:slot.endsAt>slot.startsAt?new Date(slot.endsAt).toLocaleTimeString([], {hour:'numeric',minute:'2-digit'}):'',startsAt:slot.startsAt,name:slot.name,code:slot.code});}});}item.periodNumbers=mapped.filter(function(n,i){return mapped.indexOf(n)===i;}).sort(function(a,b){return a-b;});item.periodDetails=JSON.stringify(details);});return {state:'ready',rows:rows};});}).catch(function(e){return {state:'error',reason:reason(e)};});}"
            + "postJson('/api/Attendance/getDtaForStupage',body).then(function(response){var data=response&&response.output&&response.output.data;var subjects=data&&data.subjectList;"
            + "if(!Array.isArray(subjects)){window.__rollcallAttendanceResult={state:'error'};return;}"
            + "var rows=subjects.map(function(s){return{code:s.SubjCd,name:s.SubjNm,attended:Number(s.pnt),total:Number(s.all)};})"
            + ".filter(function(r){return r.code&&r.name&&Number.isInteger(r.attended)&&Number.isInteger(r.total)&&r.attended>=0&&r.total>=r.attended;});"
            + "var profileProgression=programs[0],login={},inst={};try{login=JSON.parse(localStorage.getItem('ngStorage-LoginDetails')||'{}');}catch(e){}try{inst=JSON.parse(localStorage.getItem('InId')||'{}');}catch(e){}"
            + "var roots=[p,login,login.Student,data];for(var i=0;i<localStorage.length;i++){var k=localStorage.key(i);if(/student|profile|user/i.test(k)){try{roots.push(JSON.parse(localStorage.getItem(k)));}catch(e){}}}"
            + "function find(keys){var found='';function walk(x,d){if(!x||typeof x!=='object'||d>5||found)return;Object.keys(x).some(function(k){var n=k.toLowerCase().replace(/[^a-z0-9]/g,'');var v=x[k];if(keys.indexOf(n)>=0&&(typeof v==='string'||typeof v==='number')&&String(v).trim()){found=String(v).trim();return true;}walk(v,d+1);return !!found;});}roots.forEach(function(x){walk(x,0);});return found;}"
            + "var fullName=[profileProgression.FNa,profileProgression.MNa,profileProgression.LNa].filter(function(x){return typeof x==='string'&&x.trim();}).join(' ').trim()||login.Name||find(['stuname','studentname','studentfullname','fullname']);"
            + "var roll=find(['rollno','rollnumber','sturollno','studentrollno','studentrollnumber','classrollno','regno','registrationno','registrationnumber','usn']);"
            + "var rollValue=roll||profileProgression.AdmNum||profileProgression.StuID||'';var rollLabel=roll?'Roll No.':(profileProgression.AdmNum?'Roll / Admission No.':(profileProgression.StuID?'Student ID':'Roll No.'));"
            + "var profile={name:fullName||'',institute:profileProgression.InName||inst.insNm||'',rollNo:rollValue,rollLabel:rollLabel};"
            + "var classLogPromise=Promise.resolve(window.__rollcallClassLogs||[]);if(includeHistory||!window.__rollcallClassLogs){var logBatches=[];for(var offset=0;offset<subjects.length;offset+=4){(function(part){logBatches.push(Promise.all(part.map(function(s){var meta={subjectId:String(s.SubjId),code:String(s.SubjCd||''),name:String(s.SubjNm||s.SubjCd||''),ok:false,rows:[]};return postJson('/api/getAttendanceByAttTypAndSubj',Object.assign({},body,{AttType:s.AttType,SubjId:s.SubjId})).then(function(r){var values=r&&r.output&&r.output.data;if(!Array.isArray(values))return meta;meta.ok=true;meta.rows=values.slice(0,1000).filter(function(r){var id=r.SubjId||r.SubID||r.SubId;var code=r.SubjCd||r.SubCd;return (id!=null&&String(id)===meta.subjectId)||(code!=null&&String(code).trim().toLowerCase()===meta.code.trim().toLowerCase());});if(values.length&&!meta.rows.length)meta.ok=false;return meta;}).catch(function(){return meta;});})));})(subjects.slice(offset,offset+4));}classLogPromise=Promise.all(logBatches).then(function(parts){return [].concat.apply([],parts);}).then(function(logs){window.__rollcallClassLogs=logs;return logs;});}"
            + "return Promise.all([timetablePromise,leavePromise,classLogPromise]).then(function(academic){var logs=Array.isArray(academic[2])?academic[2]:[],classes=academic[0]&&academic[0].rows||[],now=Date.now();function norm(v){return String(v||'').toLowerCase().replace(/[^a-z0-9]/g,'');}function markStatus(r){var v=r.present;if(v===true||String(v).toLowerCase()==='true'||String(v)==='1')return 'present';if(v===false||String(v).toLowerCase()==='false'||String(v)==='0')return 'absent';return 'unable_to_confirm';}function logTime(row){var d=String(row.ForDate||'').slice(0,10),t=String(row.FrTime||'').trim();if(!/^\\d{4}-\\d{2}-\\d{2}$/.test(d))return NaN;var parsed=Date.parse(d+'T'+t);if(!Number.isFinite(parsed))parsed=Date.parse(d+' '+t);return parsed;}var marks=[];classes.forEach(function(c){if(c.allDay||c.cancelled||c.endsAt<=0||c.endsAt>now||now-c.endsAt>43200000)return;var code=norm(c.code),batch=logs.find(function(x){var lc=norm(x.code);return x.ok&&code&&lc&&code===lc;});var matches=[];if(batch)matches=batch.rows.filter(function(r){var rt=logTime(r);return Number.isFinite(rt)&&Math.abs(rt-c.startsAt)<60000;});var status='unable_to_confirm';if(batch){var classDay=new Date(c.startsAt),expectedDay=classDay.getFullYear()+'-'+String(classDay.getMonth()+1).padStart(2,'0')+'-'+String(classDay.getDate()).padStart(2,'0');var sameDayLog=batch.rows.some(function(r){return String(r.ForDate||'').slice(0,10)===expectedDay;});if(!matches.length)status=sameDayLog?'unable_to_confirm':'not_taken';else{var states=matches.map(markStatus);status=states.every(function(x){return x==='present';})?'present':states.every(function(x){return x==='absent';})?'absent':'unable_to_confirm';}}marks.push({id:String(c.id||c.code)+'_'+c.startsAt,code:c.code,name:c.name,startsAt:c.startsAt,endsAt:c.endsAt,status:status});});var history=[];logs.forEach(function(batch){if(!batch.ok)return;batch.rows.forEach(function(r){var starts=logTime(r);if(!Number.isFinite(starts))return;history.push({id:batch.subjectId+'_'+String(r.ForDate||'')+'_'+String(r.FrTime||''),code:batch.code,name:batch.name,startsAt:starts,status:markStatus(r),leaveType:text(r.LvTy),leaveStatus:text(r.LeaveSt)});});});window.__rollcallAttendanceResult={state:'ready',rows:rows,profile:profile,accountContext:body,timetable:academic[0],leaves:academic[1],classAttendance:marks,history:history};});})"
            + ".catch(function(e){window.__rollcallAttendanceResult={state:e&&e.message==='auth'?'auth':'error',reason:reason(e)};});"
            + "return 'started';}catch(e){window.__rollcallAttendanceResult={state:'error'};return 'error';}})(__INCLUDE_ACADEMIC__,__INCLUDE_HISTORY__)";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView webStatus;
    private TextView kctPortalStatus;
    private View dashboard;
    private DashboardView dashboardView;
    private String dashboardMessage = "";
    private String syncErrorMessage = "";
    private String searchQuery = "";
    private LinearLayout webPage;
    private LinearLayout kctPortalPage;
    private FrameLayout onboardingPage;
    private FrameLayout myCamuConsentPage;
    private TextView myCamuConsentStatus;
    private Button myCamuConsentButton;
    private ScrollView aboutPage;
    private Switch reminderSwitch;
    private ImageView reminderBellIcon;
    private TextView reminderPermissionHint;
    private TextView settingsTargetValue;
    private TextView onboardingChoiceStatus;
    private Button notificationSettingsButton;
    private WebView webView;
    private WebView kctWebView;
    private String lastAcademicImportSignature = "";
    private boolean kctLoginReturnPending;
    private long kctSyncStartedAt;
    private StartupAnimationView startupAnimation;
    private View startupDestination;
    private Animator startupRevealAnimator;
    private Button webSyncButton;
    private Button dashboardWebButton;
    private boolean syncInProgress;
    private boolean sessionCheckInProgress;
    private boolean initialSyncAttempted;
    private boolean sessionExpired;
    private boolean reconnectInProgress;
    private boolean reconnectAttempted;
    private boolean lastProgramAvailable;
    private boolean showBelowOnly;
    private boolean darkTheme;
    private int colorThemeIndex;
    private AppPalette palette;
    private boolean connectedOnce;
    private boolean forceForegroundSync;
    private boolean onboardingStarted;
    private boolean onboardingChoiceTransitioning;
    private Runnable onboardingAdvance;
    private String onboardingAttendanceStyle = "";
    private boolean classRemindersEnabled;
    private boolean hasClassAttendanceSync;
    private String latestClassAttendanceId = "", latestClassAttendanceStatus = "", latestClassAttendanceName = "";
    private double attendanceTarget = 75;
    private String studentName = "";
    private String instituteName = "";
    private String rollNumber = "";
    private String rollNumberLabel = "Roll No.";
    private final Set<String> expandedCourses = new HashSet<>();
    private int pollCount;
    private long lastAutoSyncAt;
    private long lastAcademicSyncAttemptAt;
    private long lastClassLogSyncAt;

    private final Runnable routeMonitor = new Runnable() {
        @Override public void run() {
            long now = System.currentTimeMillis();
            String url = webView == null ? null : webView.getUrl();
            boolean portal = isCamuPortal(url);
            boolean pageReady = webView != null && webView.getProgress() >= 100;
            boolean attendanceRoute = url != null && url.toLowerCase(Locale.ROOT).contains("attendance");
            long nextCheck = connectedOnce ? AUTO_SYNC_INTERVAL_MS : SIGN_IN_CHECK_INTERVAL_MS;

            if (portal && pageReady && !syncInProgress && !sessionCheckInProgress)
                checkMyCamuSession(attendanceRoute);

            boolean webPageVisible = webPage != null && webPage.getVisibility() == View.VISIBLE;
            boolean webPageLoading = webView != null && webView.getProgress() < 100;
            if (!connectedOnce) nextCheck = SIGN_IN_CHECK_INTERVAL_MS;
            else if (webPageVisible || webPageLoading) nextCheck = WEB_PAGE_CHECK_INTERVAL_MS;
            else if (lastAutoSyncAt > 0) nextCheck = Math.max(1_000L,
                    Math.min(AUTO_SYNC_INTERVAL_MS, lastAutoSyncAt + AUTO_SYNC_INTERVAL_MS - now));
            handler.postDelayed(this, nextCheck);
        }
    };

    private final Runnable pollResult = new Runnable() {
        @Override public void run() {
            if (!syncInProgress) return;
            if (pollCount++ >= MAX_RESULT_POLLS) {
                finishSync("MyCamu did not return attendance in time. Tap Sync to try again.");
                return;
            }
            webView.evaluateJavascript("window.__rollcallAttendanceResult||{state:'pending'}", MainActivity.this::readResult);
        }
    };

    private final Runnable syncTimeout = () -> {
        if (syncInProgress) finishSync("MyCamu did not return attendance in time. Reopen MyCamu and try Sync again.");
    };

    private final Runnable scanKctAcademicPage = new Runnable() {
        @Override public void run() {
            if (kctWebView == null || kctPortalPage == null || kctPortalPage.getVisibility() != View.VISIBLE) return;
            Uri uri = Uri.parse(kctWebView.getUrl() == null ? "" : kctWebView.getUrl());
            if ("https".equalsIgnoreCase(uri.getScheme()) && "portal.kct.ac.in".equalsIgnoreCase(uri.getHost())) {
                kctWebView.evaluateJavascript(KCT_ACADEMIC_EXTRACTOR, MainActivity.this::readKctAcademicRecords);
                kctWebView.evaluateJavascript(KCT_EXAM_CARD_EXTRACTOR, MainActivity.this::readKctExamCards);
                if (kctLoginReturnPending) kctWebView.evaluateJavascript(KCT_AUTH_CHECK, MainActivity.this::onKctLoginChecked);
            }
            handler.postDelayed(this, 5000L);
        }
    };

    private static final String KCT_ACADEMIC_EXTRACTOR = "(function(){try{"
            + "if(location.protocol!=='https:'||location.hostname!=='portal.kct.ac.in')return {records:[]};"
            + "function clean(s){return String(s||'').replace(/\\u00a0/g,' ').replace(/[\\t ]+/g,' ').trim();}"
            + "function text(e){return clean(e&&e.innerText).slice(0,1200);}"
            + "var bodyText=clean(document.body&&document.body.innerText),lower=bodyText.toLowerCase();"
            + "var authenticated=!/(sign in|log in|verification code|enter code)/.test(lower)&&/(student profile|academics|exam scheduler|completed courses)/.test(lower);"
            + "if(!authenticated)return {authenticated:false,records:[],exams:[]};"
            + "function hasRecord(e){var s=text(e);return s.length<900&&/credit\\s*:/i.test(s)&&/grade\\s*:/i.test(s);}"
            + "var courseRoot=document.getElementById('Student_complete_course'),coursePane=courseRoot&&courseRoot.closest('.tab-pane'),courseVisible=!!courseRoot&&courseRoot.getClientRects().length>0&&getComputedStyle(courseRoot).display!=='none'&&(!coursePane||coursePane.classList.contains('active'));"
            + "var gcCards=courseVisible?Array.from(courseRoot.querySelectorAll('.gc-card')).filter(hasRecord):[],preferred=courseVisible?Array.from(courseRoot.querySelectorAll('.card,.course-card,.course-row,article,li,tr,[role=row]')).filter(hasRecord):[],cards=gcCards.length?gcCards:preferred.filter(function(e){return !preferred.some(function(c){return c!==e&&e.contains(c);});});if(courseVisible&&!cards.length){var generic=Array.from(courseRoot.querySelectorAll('div')).filter(hasRecord);cards=generic.filter(function(e){return !Array.from(e.children).some(hasRecord);});}var expectedCount=courseRoot?(String(courseRoot.textContent||'').match(/\\bgrade\\s*:/gi)||[]).length:0;"
            + "var semNodes=courseRoot?Array.from(courseRoot.querySelectorAll('*')).filter(function(e){var s=text(e);return s.length<180&&/\\bsemester\\s*[-:]?\\s*\\d{1,2}\\b/i.test(s);}).filter(function(e){return !Array.from(e.children).some(function(c){var s=text(c);return s.length<180&&/\\bsemester\\s*[-:]?\\s*\\d{1,2}\\b/i.test(s);});}):[];"
            + "var records=[];cards.forEach(function(card){var raw=text(card),lines=raw.split(/\\n+/).map(clean).filter(Boolean);"
            + "var cm=raw.match(/credit\\s*:\\s*(\\d+(?:\\.\\d+)?)/i),gm=raw.match(/grade\\s*:\\s*([A-Z][A-Z+]{0,2})/i),rm=raw.match(/result\\s*:\\s*([A-Z -]{2,24})/i);if(!cm||!gm)return;"
            + "var sem='';for(var i=0;i<semNodes.length;i++){if(semNodes[i].compareDocumentPosition(card)&Node.DOCUMENT_POSITION_FOLLOWING){var h=text(semNodes[i]),sm=h.match(/\\bsemester\\s*[-:]?\\s*(\\d{1,2})\\b/i),ym=h.match(/20\\d{2}(?:\\s*[-/]\\s*\\d{2,4})?/);if(sm)sem='Semester '+sm[1]+(ym?' · '+ym[0]:'');}}if(!sem)return;"
            + "var code='';for(var j=0;j<lines.length&&!code;j++){var tokens=lines[j].match(/[A-Z0-9][A-Z0-9/-]{3,21}/ig)||[];for(var q=0;q<tokens.length;q++)if(/[A-Z]/i.test(tokens[q])&&/\\d/.test(tokens[q])){code=tokens[q].toUpperCase();break;}}"
            + "var titleNode=card.querySelector('h1,h2,h3,h4,h5,[class*=course-name],[class*=subject-name],[class*=title]'),name=titleNode?text(titleNode):'';if(/faculty|staff|instructor|professor|^dr\\.?\\s/i.test(name))name='';"
            + "for(var k=0;!name&&k<lines.length;k++){var line=lines[k];if(/^(credit|grade|result|faculty|staff|instructor|handled by|course\\s*(code|id)|subject\\s*(code|id)|paper\\s*(code|id)|month\\s*&?\\s*year)\\s*:/i.test(line)||/\\b(faculty|instructor|professor)\\b/i.test(line)||/^dr\\.?\\s/i.test(line)||/semester\\s*[-:]?\\s*\\d+/i.test(line))continue;var candidate=code?line.replace(code,'').trim():line;if(candidate&&candidate.length>2&&candidate.length<120&&!/^20\\d{2}/.test(candidate)){name=candidate;break;}}"
            + "var grade=gm[1].toUpperCase();if(!/^(O|A\\+|A|B\\+|B|C|U|SA|WD|P|F|S)$/.test(grade))return;records.push({semester:sem,code:code,name:name||code,credits:Number(cm[1]),grade:grade,result:rm?clean(rm[1]).toUpperCase():''});});"
            + "function labelOf(e){return clean(e.getAttribute('aria-label')||e.getAttribute('title')||e.innerText);}var action='';"
            + "var stage=sessionStorage.getItem('rollcallKctStage')||'0',examPage=/exam/i.test(location.pathname)||Array.from(document.querySelectorAll('h1,h2,h3,[role=heading]')).some(function(e){return /exam scheduler|upcoming exam/i.test(text(e));});"
            + "var exams=[];if(examPage){function parseDate(ds,ts){var v=clean(ds),m=v.match(/^(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})$/),d;if(m)d=new Date(Number(m[1]),Number(m[2])-1,Number(m[3]));else{m=v.match(/^(\\d{1,2})[-/](\\d{1,2})[-/](\\d{4})$/);if(m)d=new Date(Number(m[3]),Number(m[2])-1,Number(m[1]));else{var parsed=Date.parse(v);if(!isFinite(parsed))return null;d=new Date(parsed);}}if(isNaN(d.getTime()))return null;var t=clean(ts).match(/\\b(\\d{1,2}):(\\d{2})\\s*(AM|PM)?\\b/i);if(!t)t=v.match(/\\b(\\d{1,2}):(\\d{2})\\s*(AM|PM)?\\b/i);var hasTime=!!t;if(t){var h=Number(t[1]);if(t[3]&&t[3].toUpperCase()==='PM'&&h<12)h+=12;if(t[3]&&t[3].toUpperCase()==='AM'&&h===12)h=0;d.setHours(h,Number(t[2]),0,0);}else d.setHours(0,0,0,0);return{at:d.getTime(),hasTime:hasTime};}Array.from(document.querySelectorAll('table')).forEach(function(table){var trs=Array.from(table.querySelectorAll('tr'));if(trs.length<2)return;var hi=trs.findIndex(function(r){var h=Array.from(r.querySelectorAll('th,td')).map(function(c){return text(c).toLowerCase();});return h.some(function(x){return /date/.test(x);})&&h.some(function(x){return /course|subject|paper|exam/.test(x);});});if(hi<0)return;var headers=Array.from(trs[hi].querySelectorAll('th,td')).map(function(c){return text(c).toLowerCase();});function cell(cells,re){var i=headers.findIndex(function(h){return re.test(h);});return i>=0&&cells[i]?text(cells[i]):'';}trs.slice(hi+1).forEach(function(r){var cells=Array.from(r.querySelectorAll('td,th'));if(!cells.length)return;var ds=cell(cells,/date/),course=cell(cells,/^(course|subject|paper)( name)?$/),type=cell(cells,/exam type|assessment|exam name|^exam$/),code=cell(cells,/course code|subject code|code/),venue=cell(cells,/venue|room|hall/),session=cell(cells,/session|an\\s*\\/\\s*fn/),status=cell(cells,/status/),time=cell(cells,/time/),date=parseDate(ds,time);if(!date||(!course&&!type&&!code))return;var title=(course||code||type).slice(0,120),sessionLabel=/\\b(a\\.?n\\.?|afternoon)\\b/i.test(session)?'AN':/\\b(f\\.?n\\.?|forenoon)\\b/i.test(session)?'FN':'',canonicalType=/embed/i.test(type)?'Embedded':/pract/i.test(type)?'Practical':/theor/i.test(type)?'Theory':type,details=[code,venue,status].filter(Boolean).join(' · ');exams.push({title:title,course:course,type:canonicalType,session:sessionLabel,code:code,details:details,startsAt:date.at,hasTime:date.hasTime});});});exams=exams.filter(function(e){return e.hasTime?e.startsAt>Date.now():new Date(e.startsAt).setHours(0,0,0,0)>=new Date().setHours(0,0,0,0);});exams.sort(function(a,b){return a.startsAt-b.startsAt;});}"
            + "if(courseVisible&&records.length>=2&&records.length===expectedCount&&stage!=='exam'){sessionStorage.setItem('rollcallKctStage','exam');sessionStorage.setItem('rollcallKctExamUrl',location.href);var examTab=Array.from(document.querySelectorAll('a,button,[role=tab],li,span,div')).find(function(e){return /^exam scheduler$/i.test(labelOf(e))&&e.getClientRects().length>0;});if(examTab)(examTab.closest('a,button,[role=tab],li')||examTab).click();}"
            + "else if(!records.length&&stage==='0'){var b=document.getElementById('academics-tab')||Array.from(document.querySelectorAll('a,button,[role=tab],li,span,div')).find(function(e){return /^academics$/i.test(labelOf(e));});if(b){(b.closest('a,button,[role=tab],li')||b).click();sessionStorage.setItem('rollcallKctStage','1');}}"
            + "else if(!records.length&&stage==='1'){var completed=document.getElementById('Course-tab');if(completed){completed.click();sessionStorage.setItem('rollcallKctStage','2');}}"
            + "var examRequested=sessionStorage.getItem('rollcallKctStage')==='exam',examVisited=examPage||(examRequested&&sessionStorage.getItem('rollcallKctExamUrl')!==location.href);"
            + "return {authenticated:true,records:records,complete:courseVisible&&records.length>=2&&records.length===expectedCount,expectedCount:expectedCount,candidateCount:cards.length,coursePanel:!courseRoot?'missing':courseVisible?'visible':'hidden',exams:exams,examPage:examVisited,stage:stage,action:action,source:courseVisible?'Student_complete_course':'none'};}catch(e){return {records:[]};}})()";
    private static final String KCT_EXAM_CARD_EXTRACTOR = "(function(){try{"
            + "var norm=function(s){return String(s||'').replace(/\\u00a0/g,' ').replace(/[\\t ]+/g,' ').trim();};"
            + "var body=norm(document.body&&document.body.innerText);if(!/exam scheduler/i.test(body))return {examPage:false,exams:[]};"
            + "var leaves=Array.from(document.querySelectorAll('body *')).filter(function(e){return e.children.length===0&&/\\b(start in \\d+ days|today|tomorrow)\\b/i.test(norm(e.innerText));});"
            + "var seen={},exams=[];leaves.forEach(function(leaf){var node=leaf,chosen=null;for(var i=0;i<10&&node;i++,node=node.parentElement){var raw=norm(node.innerText);if(raw.length>600)break;var lines=String(node.innerText||'').split(/\\n+/).map(norm).filter(Boolean),codeLine=lines.find(function(line){return /\\b(?:\\d{2,4}[A-Z]{2,5}\\d{2,6}|[A-Z]{2,5}\\d{2,6})\\b/i.test(line)&&!/(roll|register|student|contact|email)/i.test(line);}),date=raw.match(/\\b(\\d{1,2})[-/](\\d{1,2})[-/](\\d{4})\\b/);if(codeLine&&date){chosen={raw:raw,lines:lines,codeLine:codeLine,date:date};break;}}if(!chosen)return;var raw=chosen.raw,lines=chosen.lines,codeMatch=chosen.codeLine.match(/\\b(?:\\d{2,4}[A-Z]{2,5}\\d{2,6}|[A-Z]{2,5}\\d{2,6})\\b/i),date=chosen.date,code=codeMatch[0].toUpperCase(),key=code+'|'+date[0];if(seen[key])return;seen[key]=1;var at=new Date(Number(date[3]),Number(date[2])-1,Number(date[1])).getTime();if(!Number.isFinite(at)||at<Date.now()-86400000)return;var course=chosen.codeLine.replace(codeMatch[0],'').replace(/^[\\s:–—-]+|[\\s:–—-]+$/g,'').trim()||code,typeLine=lines.find(function(line){return /\\b(embedded|theory|practical)\\b/i.test(line);})||'',type=/embed/i.test(typeLine)?'Embedded':/pract/i.test(typeLine)?'Practical':/theor/i.test(typeLine)?'Theory':'',sessionLine=lines.find(function(line){return /\\b(a\\.?n\\.?|f\\.?n\\.?|forenoon|afternoon)\\b/i.test(line);})||'',session=/\\b(a\\.?n\\.?|afternoon)\\b/i.test(sessionLine)?'AN':/\\b(f\\.?n\\.?|forenoon)\\b/i.test(sessionLine)?'FN':'',details=lines.filter(function(line){return line!==chosen.codeLine&&line!==typeLine&&line!==sessionLine&&!/\\b\\d{1,2}[-/]\\d{1,2}[-/]\\d{4}\\b/.test(line)&&!/^(upcoming|start in \\d+ days|today|tomorrow)$/i.test(line)&&!/^(roll number|register number|email|mobile|address)/i.test(line);}).slice(0,5).join(' · ');exams.push({title:course.slice(0,120),course:course,type:type,session:session,code:code,details:details.slice(0,180),startsAt:at,hasTime:false});});exams.sort(function(a,b){return a.startsAt-b.startsAt;});return {examPage:true,exams:exams};}catch(_){return {examPage:false,exams:[]};}})()";
    private static final String KCT_AUTH_CHECK = "(function(){var t=(document.body&&document.body.innerText||'').toLowerCase();"
            + "return location.hostname==='portal.kct.ac.in'&&!/(sign in|log in|verification code|enter code)/.test(t)"
            + "&&(/student_profile/i.test(location.pathname)||/(student profile|student result|academics|completed courses)/.test(t));})()";

    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density); }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LiveSnapshotStore.initialize(this);
        ClassAttendanceNotifier.ensureChannels(this);
        android.content.SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        if (preferences.getInt("class_matching_version", 0) < 2) {
            LiveSnapshotStore.classMarks(this, new ArrayList<>());
            preferences.edit().putInt("class_matching_version", 2)
                    .putLong("last_class_log_sync_attempt", 0).apply();
        }
        if (preferences.getInt("leave_period_schema_version", 0) < 1) {
            preferences.edit().putInt("leave_period_schema_version", 1)
                    .putLong("last_academic_sync_attempt", 0).apply();
        }
        if (preferences.getInt("kct_academic_schema_version", 0) < 2) {
            preferences.edit().remove(ACADEMIC_RECORDS_KEY).remove(ACADEMIC_UPDATED_KEY)
                    .remove(UPCOMING_EXAM_KEY).remove("last_academic_sync_attempt")
                    .putInt("kct_academic_schema_version", 2).apply();
        }
        if (preferences.getInt("kct_academic_schema_version", 0) < 4) {
            preferences.edit().remove(ACADEMIC_RECORDS_KEY).remove(ACADEMIC_UPDATED_KEY)
                    .remove(UPCOMING_EXAM_KEY).putInt("kct_academic_schema_version", 4).apply();
        }
        if (preferences.getInt("kct_academic_schema_version", 0) < 5) {
            preferences.edit().remove(ACADEMIC_RECORDS_KEY).remove(ACADEMIC_UPDATED_KEY)
                    .putInt("kct_academic_schema_version", 5).apply();
        }
        if (preferences.getInt("kct_academic_schema_version", 0) < 6) {
            preferences.edit().remove(ACADEMIC_RECORDS_KEY).remove(ACADEMIC_UPDATED_KEY)
                    .remove("kct_auto_import_attempted").putInt("kct_academic_schema_version", 6).apply();
        }
        if (preferences.getInt("kct_auto_import_flow_version", 0) < 10) {
            preferences.edit().remove("kct_auto_import_attempted")
                    .putInt("kct_auto_import_flow_version", 10).apply();
        }
        boolean resumeKctImport = preferences.getBoolean("kct_portal_authenticated", false)
                && "[]".equals(preferences.getString(ACADEMIC_RECORDS_KEY, "[]"))
                && !preferences.getBoolean("kct_auto_import_attempted", false);
        if (resumeKctImport) preferences.edit().putBoolean("kct_auto_import_attempted", true).apply();
        if (preferences.getInt("figma_ui_version", 0) > 0) {
            preferences.edit().remove("figma_ui_version").putBoolean(DARK_KEY, true)
                    .putInt(COLOR_THEME_KEY, 0).putInt(DARK_PALETTE_KEY, 0).apply();
        }
        if (preferences.getInt("rollback_design_version", 0) < 1) {
            preferences.edit().putInt("rollback_design_version", 1).putInt(COLOR_THEME_KEY, 0)
                    .putInt(LIGHT_PALETTE_KEY, 0).putInt(DARK_PALETTE_KEY, 0).apply();
        }
        if (!preferences.getBoolean("torch_html_palette_applied", false)) {
            preferences.edit().putBoolean(DARK_KEY, true).putBoolean("torch_html_palette_applied", true).apply();
        }
        attendanceTarget = preferences.getInt("attendance_target", 75);
        darkTheme = preferences.getBoolean(DARK_KEY, true);
        colorThemeIndex = preferences.contains(COLOR_THEME_KEY) ? preferences.getInt(COLOR_THEME_KEY, 0)
                : preferences.getInt(darkTheme ? DARK_PALETTE_KEY : LIGHT_PALETTE_KEY, 0);
        if (colorThemeIndex != 5 && colorThemeIndex != 6) {
            colorThemeIndex = 0;
            preferences.edit().putInt(COLOR_THEME_KEY, 0).apply();
        }
        connectedOnce = preferences.getBoolean(CONNECTED_KEY, false) || !LiveSnapshotStore.get().attendance.isEmpty();
        onboardingStarted = false; // An unfinished connection always starts at the welcome screen.
        if (connectedOnce) preferences.edit().putBoolean(CONNECTED_KEY, true).apply();
        studentName = preferences.getString("student_name", "");
        instituteName = preferences.getString("institute_name", "");
        rollNumber = preferences.getString("roll_number", "");
        rollNumberLabel = preferences.getString("roll_number_label", "Roll No.");
        lastAcademicSyncAttemptAt = preferences.getLong("last_academic_sync_attempt", 0);
        lastClassLogSyncAt = preferences.getLong("last_class_log_sync_attempt", 0);
        classRemindersEnabled = ClassReminderScheduler.enabled(this);
        ClassReminderScheduler.schedule(this, LiveSnapshotStore.get().timetable);
        UpdatePushNotifications.subscribeIfAllowed(this);
        ReleaseUpdateNotifier.check(this);
        if (connectedOnce && preferences.getBoolean(MYCAMU_CONSENT_KEY, false)) scheduleBackgroundSync();
        else WorkManager.getInstance(getApplicationContext()).cancelUniqueWork("mycamu-attendance-sync");
        applyTheme();

        FrameLayout root = new FrameLayout(this);
        FrameLayout content = new FrameLayout(this);
        root.setBackgroundColor(CANVAS);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            content.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));
        dashboard = buildDashboardView();
        dashboard.setVisibility(connectedOnce ? View.VISIBLE : View.GONE);
        content.addView(dashboard, new FrameLayout.LayoutParams(-1, -1));
        webPage = buildWebPage();
        boolean showOnboarding = !connectedOnce && !onboardingStarted;
        webPage.setVisibility(connectedOnce || showOnboarding ? View.GONE : View.VISIBLE);
        content.addView(webPage, new FrameLayout.LayoutParams(-1, -1));
        onboardingPage = buildOnboardingPage();
        onboardingPage.setVisibility(showOnboarding ? View.VISIBLE : View.GONE);
        content.addView(onboardingPage, new FrameLayout.LayoutParams(-1, -1));
        aboutPage = buildAboutPage();
        aboutPage.setVisibility(View.GONE);
        content.addView(aboutPage, new FrameLayout.LayoutParams(-1, -1));
        kctPortalPage = buildKctPortalPage();
        kctPortalPage.setVisibility(View.GONE);
        content.addView(kctPortalPage, new FrameLayout.LayoutParams(-1, -1));
        myCamuConsentPage = buildMyCamuConsentPage();
        myCamuConsentPage.setVisibility(View.GONE);
        content.addView(myCamuConsentPage, new FrameLayout.LayoutParams(-1, -1));
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(decor.getSystemUiVisibility()
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        setContentView(root);
        applyTheme();
        if (saved != null && saved.getBoolean("restore_about_settings", false)) showAbout();
        else routeWidgetLaunch(getIntent());
        if (saved == null) showStartupAnimation(root);

        LiveSnapshotStore.setListener(this::render);
        webView.loadUrl(HOME);
        if (resumeKctImport) handler.postDelayed(this::showKctPortal, 3600L);
    }

    private void showStartupAnimation(FrameLayout root) {
        startupDestination = dashboard != null && dashboard.getVisibility() == View.VISIBLE ? dashboard
                : onboardingPage != null && onboardingPage.getVisibility() == View.VISIBLE ? onboardingPage
                : webPage != null && webPage.getVisibility() == View.VISIBLE ? webPage : aboutPage;
        if (startupDestination != null) startupDestination.setTranslationY(0f);
        startupAnimation = new StartupAnimationView(this, palette, new StartupAnimationView.TransitionListener() {
            @Override public void onStart() { beginStartupTransition(); }
            @Override public void onProgress(float progress) { updateStartupTransition(progress); }
        }, this::finishStartupAnimation);
        root.addView(startupAnimation, new FrameLayout.LayoutParams(-1, -1));
        root.requestApplyInsets();
        startupAnimation.start();
    }

    private void finishStartupAnimation() {
        if (startupAnimation == null) return;
        StartupAnimationView animation = startupAnimation;
        startupAnimation = null;
        animation.stop();
        if (startupDestination != null) startupDestination.setTranslationY(0f);
        if (startupRevealAnimator != null && startupRevealAnimator.isStarted()) startupRevealAnimator.end();
        startupRevealAnimator = null;
        startupDestination = null;
        ViewGroup parent = animation.getParent() instanceof ViewGroup ? (ViewGroup) animation.getParent() : null;
        if (parent != null) parent.removeView(animation);
    }

    private void beginStartupTransition() {
        if (startupDestination == null || startupDestination.getVisibility() != View.VISIBLE) {
            startupDestination = dashboard != null && dashboard.getVisibility() == View.VISIBLE ? dashboard
                    : onboardingPage != null && onboardingPage.getVisibility() == View.VISIBLE ? onboardingPage
                    : webPage != null && webPage.getVisibility() == View.VISIBLE ? webPage : aboutPage;
            if (startupDestination != null) startupDestination.setTranslationY(0f);
        }
        if (startupDestination == null || !startupDestination.isAttachedToWindow()
                || isFinishing() || isDestroyed()) return;
        int width = startupDestination.getWidth();
        int height = startupDestination.getHeight();
        if (width <= 0 || height <= 0) return;
        float centerX = width * .5f - startupDestination.getX();
        float centerY = height * .5f + dp(3f) - startupDestination.getY();
        float maxRadius = (float) Math.hypot(Math.max(centerX, width - centerX),
                Math.max(centerY, height - centerY));
        startupRevealAnimator = ViewAnimationUtils.createCircularReveal(startupDestination,
                Math.round(centerX), Math.round(centerY), 0f, maxRadius);
        startupRevealAnimator.setDuration(520L);
        startupRevealAnimator.setInterpolator(StartupAnimationView.TRANSITION_INTERPOLATOR);
        startupRevealAnimator.start();
    }

    private void updateStartupTransition(float progress) {
        if (progress >= 1f && startupRevealAnimator != null && startupRevealAnimator.isStarted())
            startupRevealAnimator.end();
    }

    private void applyTheme() {
        palette = AppPalette.get(darkTheme, colorThemeIndex);
        INK = palette.ink; MUTED = palette.muted; CANVAS = palette.canvas;
        GREEN = palette.green; AMBER = palette.amber; RED = palette.red;
        SURFACE = palette.surface; BORDER = palette.border; SECONDARY = palette.soft;
        TRACK = palette.track; BUTTON = palette.button; BUTTON_TEXT = palette.buttonText;
        HIGHLIGHT = palette.highlight; HERO = palette.hero; HERO_TEXT = palette.heroText; HERO_MUTED = palette.heroMuted;
        int systemBarColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ? Color.TRANSPARENT : CANVAS;
        getWindow().setStatusBarColor(systemBarColor);
        getWindow().setNavigationBarColor(systemBarColor);
        getWindow().getDecorView().setBackgroundColor(CANVAS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setStatusBarContrastEnforced(false);
            getWindow().setNavigationBarContrastEnforced(false);
        }
        int lightBars = darkTheme ? 0
                : android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
        int barMask = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.view.WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) controller.setSystemBarsAppearance(lightBars, barMask);
        } else {
            int systemUi = darkTheme ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26 && !darkTheme) systemUi |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            View decor = getWindow().getDecorView();
            int layoutFlags = decor.getSystemUiVisibility()
                    & (View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            decor.setSystemUiVisibility(systemUi | layoutFlags);
        }
    }

    private DashboardView buildDashboardView() {
        dashboardView = new DashboardView(this, new DashboardView.Actions() {
            @Override public void onSync() { syncAttendance(true); }
            @Override public void onPortal() { showMyCamu(); }
            @Override public void onAcademicsPortal() { showKctPortal(); }
            @Override public void onTarget() { chooseTarget(); }
            @Override public void onTheme() { toggleTheme(); }
            @Override public void onPalette() { choosePalette(); }
            @Override public void onAbout() { showAbout(); }
            @Override public void onFilter(boolean belowOnly) { showBelowOnly = belowOnly; }
            @Override public void onQuery(String value) { searchQuery = value; }
            @Override public void onCourse(String code) {
                if (!expandedCourses.add(code)) expandedCourses.remove(code);
                render(LiveSnapshotStore.get());
            }
            @Override public void onReminderToggle() {
                if (classRemindersEnabled) {
                    android.widget.Switch dummy = new android.widget.Switch(MainActivity.this);
                    onReminderSwitchChanged(dummy, false);
                } else {
                    setClassRemindersEnabled(true);
                    render(LiveSnapshotStore.get());
                }
            }
            @Override public void onNotifications() {
                Intent intent = new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                intent.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName());
                startActivity(intent);
            }
            @Override public void onCloudAccount() { showCloudAccountDialog(); }
            @Override public void onUpdates() {
                ReleaseUpdateNotifier.check(MainActivity.this);
                Toast.makeText(MainActivity.this, "Checking GitHub for Rollcall updates…", Toast.LENGTH_SHORT).show();
            }
            @Override public void onClearSavedData() { confirmClearSavedData(); }
            @Override public void onPrivacy() { openUrl("https://github.com/inianexe/Rollcall/blob/main/PRIVACY.md"); }
            @Override public void onTerms() { openUrl("https://github.com/inianexe/Rollcall/blob/main/TERMS.md"); }
            @Override public void onProject() { openUrl("https://github.com/inianexe/Rollcall"); }
            @Override public void onTargetChanged(int target) {
                attendanceTarget = target;
                getPreferences(MODE_PRIVATE).edit().putInt("attendance_target", target).apply();
                AttendanceEventNotifier.setTargetBaseline(MainActivity.this,
                        LiveSnapshotStore.get().attendance, target);
                expandedCourses.clear();
                render(LiveSnapshotStore.get());
            }
        }, palette);
        render(LiveSnapshotStore.get());
        return dashboardView;
    }

    private FrameLayout buildOnboardingPage() {
        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(CANVAS);
        page.addView(buildOnboardingStep(0), new FrameLayout.LayoutParams(-1, -1));
        return page;
    }

    private void showOnboardingStep(int step) {
        if (onboardingPage == null) return;
        if (onboardingAdvance != null) handler.removeCallbacks(onboardingAdvance);
        onboardingAdvance = null;
        onboardingChoiceTransitioning = false;
        onboardingChoiceStatus = null;
        View previous = onboardingPage.getChildCount() == 0 ? null : onboardingPage.getChildAt(onboardingPage.getChildCount() - 1);
        View next = buildOnboardingStep(step);
        onboardingPage.addView(next, new FrameLayout.LayoutParams(-1, -1));
        if (!animationsEnabled() || previous == null) {
            onboardingPage.removeAllViews();
            onboardingPage.addView(next, new FrameLayout.LayoutParams(-1, -1));
            return;
        }
        next.setTranslationY(dp(12));
        next.animate().translationY(0f).setDuration(220)
                .setInterpolator(new android.view.animation.PathInterpolator(.2f, 0f, 0f, 1f)).start();
        previous.animate().alpha(.9f).translationY(-dp(4)).setDuration(140)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).withEndAction(() -> {
                    if (previous.getParent() == onboardingPage) onboardingPage.removeView(previous);
                }).start();
    }

    private boolean animationsEnabled() {
        return Build.VERSION.SDK_INT < 26 || android.animation.ValueAnimator.areAnimatorsEnabled();
    }

    private void showAppScreen(View incoming) {
        if (incoming == null) return;
        View[] screens = {onboardingPage, webPage, dashboard, aboutPage, kctPortalPage, myCamuConsentPage};
        boolean entering = incoming.getVisibility() != View.VISIBLE;
        for (View screen : screens) {
            if (screen == null) continue;
            screen.animate().cancel();
            screen.setAlpha(1f);
            screen.setTranslationY(0f);
            screen.setVisibility(screen == incoming ? View.VISIBLE : View.GONE);
        }
        incoming.bringToFront();
        if (!entering || startupAnimation != null || !animationsEnabled()) return;
        if (incoming == dashboard && dashboardView != null) {
            dashboardView.animateCurrentPageEntry();
            return;
        }
        incoming.setTranslationY(dp(10));
        incoming.animate().translationY(0f).setDuration(200)
                .setInterpolator(new android.view.animation.PathInterpolator(.2f, 0f, 0f, 1f)).start();
    }

    /**
     * Torch Genesis onboarding: editorial scale, neutral ink and restrained acid-lime moments.
     * This is deliberately a Rollcall-owned native screen; MyCamu's website stays untouched.
     */
    private View buildOnboardingStep(int step) {
        final int paper = darkTheme ? 0xFF121212 : 0xFFF5F4EC;
        final int ink = darkTheme ? 0xFFF5F5EE : 0xFF182019;
        final int muted = darkTheme ? 0xFFA8AAA1 : 0xFF586057;
        final int panel = darkTheme ? 0xFF252525 : 0xFFFFFEFA;
        final int border = darkTheme ? 0xFF525252 : 0xFFC6C8BC;
        final int lime = 0xFFCCFF00, orange = 0xFFFF4400, lilac = 0xFFC8C8FF;
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(paper);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(23), dp(24), dp(23), dp(24));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));

        LinearLayout brand = new LinearLayout(this);
        brand.setGravity(android.view.Gravity.CENTER_VERTICAL);
        RollcallLogo brandMark = new RollcallLogo(this, palette);
        brand.addView(brandMark, new LinearLayout.LayoutParams(dp(36), dp(36)));
        TextView wordmark = text("rollcall", 23, ink, Typeface.BOLD);
        wordmark.setLetterSpacing(-.07f);
        wordmark.setPadding(dp(8), 0, 0, 0);
        brand.addView(wordmark);
        content.addView(brand);
        if (step > 0) {
            TextView back = text("←  BACK", 12, muted, Typeface.BOLD);
            back.setGravity(android.view.Gravity.CENTER_VERTICAL);
            back.setMinHeight(dp(48));
            back.setOnClickListener(v -> showOnboardingStep(step - 1));
            content.addView(back, topMargin(-1, dp(48), 12));
        } else {
            content.addView(new View(this), new LinearLayout.LayoutParams(1, dp(32)));
        }

        TextView index = text("ACADEMIC TRANSMISSION / 0" + (step + 1) + " OF 04", 10,
                darkTheme ? lime : 0xFF365000, Typeface.BOLD);
        index.setLetterSpacing(.07f);
        content.addView(index);
        if (step == 0) {
            content.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1));
            LinearLayout hero = new LinearLayout(this);
            hero.setOrientation(LinearLayout.VERTICAL);
            hero.setPadding(dp(21), dp(20), dp(21), dp(24));
            hero.setBackground(background(lime, 0, 18));
            TextView eyebrow = text("GET STARTED / 001", 11, 0xFF121212, Typeface.BOLD);
            hero.addView(eyebrow);
            TextView graphic = text("✱   ◯   ↗", 49, orange, Typeface.BOLD);
            graphic.setGravity(android.view.Gravity.CENTER);
            hero.addView(graphic, topMargin(-1, dp(140), 14));
            TextView headline = text("A LITTLE CLARITY.\nA BETTER WEEK.", 35, 0xFF121212, Typeface.BOLD);
            headline.setLetterSpacing(-.055f);
            headline.setLineSpacing(dp(1), 1f);
            hero.addView(headline, topMargin(-1, -2, 22));
            TextView description = text("Your classes, attendance and next moves. Together in one place.", 13, 0xFF343A26, Typeface.NORMAL);
            description.setLineSpacing(dp(4), 1f);
            hero.addView(description, topMargin(-1, -2, 10));
            content.addView(hero, topMargin(-1, -2, 20));
            content.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1));
            content.addView(onboardingButton("GET STARTED  ↗", lime, 0xFF121212,
                    v -> showOnboardingStep(1)), topMargin(-1, dp(56), 26));
        } else if (step == 1) {
            TextView title = text("REMINDERS\nTHAT MATTER.", 35, ink, Typeface.BOLD);
            title.setLetterSpacing(-.055f);
            content.addView(title, topMargin(-1, -2, 28));
            TextView caption = text("Be ready before the next class—not after it starts.", 14, muted, Typeface.NORMAL);
            caption.setLineSpacing(dp(4), 1f);
            content.addView(caption, topMargin(-1, -2, 12));

            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setPadding(dp(20), dp(18), dp(20), dp(20));
            info.setBackground(background(lilac, 0xFF121212, 16));
            info.addView(text("CLASS ALERT / 010", 10, 0xFF242424, Typeface.BOLD));
            TextView minutes = text("10 MINUTES\nBEFORE.", 29, 0xFF111111, Typeface.BOLD);
            minutes.setLetterSpacing(-.05f);
            info.addView(minutes, topMargin(-1, -2, 14));
            TextView detail = text("Attendance changes, OD decisions and timetable updates can also appear as notifications.", 12, 0xFF333341, Typeface.NORMAL);
            detail.setLineSpacing(dp(3), 1f);
            info.addView(detail, topMargin(-1, -2, 10));
            content.addView(info, topMargin(-1, -2, 24));
            TextView note = text("Rollcall checks for changes when Android permits background work. Class alerts use your saved timetable.", 11, muted, Typeface.NORMAL);
            note.setLineSpacing(dp(3), 1f);
            content.addView(note, topMargin(-1, -2, 16));
            content.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1));

            boolean allowed = Build.VERSION.SDK_INT < 33 || checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
            boolean settingsNeeded = !allowed &&
                    getPreferences(MODE_PRIVATE).getBoolean(NOTIFICATION_PERMISSION_DENIED_KEY, false) &&
                    !shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS);
            String action = allowed ? "CONTINUE  ↗"
                    : settingsNeeded ? "OPEN NOTIFICATION SETTINGS  ↗" : "ALLOW NOTIFICATIONS  ↗";
            content.addView(onboardingButton(action, lime, 0xFF121212,
                    v -> requestOnboardingNotifications()), topMargin(-1, dp(56), 22));
            TextView skip = text("NOT NOW", 13, muted, Typeface.BOLD);
            skip.setGravity(android.view.Gravity.CENTER);
            skip.setMinHeight(dp(48));
            skip.setOnClickListener(v -> showOnboardingStep(2));
            content.addView(skip, topMargin(-1, dp(48), 8));
        } else if (step == 2) {
            onboardingChoiceTransitioning = false;
            TextView title = text("WHAT'S YOUR\nFIRST MOVE?", 35, ink, Typeface.BOLD);
            title.setLetterSpacing(-.055f);
            content.addView(title, topMargin(-1, -2, 28));
            TextView caption = text("Choose what you want Rollcall to put first.", 13, muted, Typeface.NORMAL);
            content.addView(caption, topMargin(-1, -2, 10));
            String[] choices = {"KEEP ATTENDANCE ON TRACK", "FIND SAFE SKIPS", "SEE MY WEEK AT A GLANCE"};
            String[] captions = {"01 / YOUR TARGET", "02 / YOUR OPTIONS", "03 / YOUR SCHEDULE"};
            for (int i = 0; i < choices.length; i++) {
                final String choice = new String[]{"Keep attendance on track", "Find safe skips", "See my week at a glance"}[i];
                final LinearLayout tile = new LinearLayout(this);
                tile.setOrientation(LinearLayout.VERTICAL);
                tile.setPadding(dp(17), dp(16), dp(17), dp(16));
                final boolean selected = choice.equals(onboardingAttendanceStyle);
                tile.setBackground(background(selected ? lime : panel, selected ? lime : border, 15));
                tile.addView(text(captions[i], 10, selected ? 0xFF1B1B1B : muted, Typeface.BOLD));
                tile.addView(text(choices[i] + "  ↗", 14, selected ? 0xFF1B1B1B : ink, Typeface.BOLD),
                        topMargin(-1, -2, 10));
                tile.setMinHeight(dp(88));
                tile.setOnClickListener(v -> {
                    if (onboardingChoiceTransitioning) return;
                    onboardingAttendanceStyle = choice;
                    onboardingChoiceTransitioning = true;
                    tile.setBackground(background(lime, 0, 15));
                    for (int j = 0; j < tile.getChildCount(); j++)
                        if (tile.getChildAt(j) instanceof TextView)
                            ((TextView) tile.getChildAt(j)).setTextColor(0xFF121212);
                    if (animationsEnabled()) {
                        tile.animate().scaleX(1.02f).scaleY(1.02f).setDuration(95)
                                .withEndAction(() -> tile.animate().scaleX(1f).scaleY(1f)
                                        .setDuration(140).start()).start();
                    }
                    onboardingAdvance = () -> showOnboardingStep(3);
                    handler.postDelayed(onboardingAdvance, 300L);
                });
                content.addView(tile, topMargin(-1, -2, 10));
            }
            onboardingChoiceStatus = text("", 11, muted, Typeface.NORMAL);
            onboardingChoiceStatus.setVisibility(View.GONE);
            content.addView(onboardingChoiceStatus);
        } else {
            TextView title = text("MAKE IT\nYOUR SPACE.", 35, ink, Typeface.BOLD);
            title.setLetterSpacing(-.055f);
            content.addView(title, topMargin(-1, -2, 28));
            TextView caption = text("A Rollcall account is optional. Your MyCamu connection provides your actual academic data.", 13, muted, Typeface.NORMAL);
            caption.setLineSpacing(dp(4), 1f);
            content.addView(caption, topMargin(-1, -2, 12));

            LinearLayout account = new LinearLayout(this);
            account.setOrientation(LinearLayout.VERTICAL);
            account.setPadding(dp(17), dp(19), dp(17), dp(19));
            account.setBackground(background(lilac, 0, 16));
            account.addView(text("ROLLCALL / WEB ACCOUNT", 11, 0xFF111111, Typeface.BOLD));
            account.addView(text("YOUR MARKS.\nANYWHERE.", 28, 0xFF111111, Typeface.BOLD),
                    topMargin(-1, -2, 14));
            account.addView(text("Keep a read-only web mirror. Sign-in uses a password separate from MyCamu.", 12, 0xFF2A2A38, Typeface.NORMAL),
                    topMargin(-1, -2, 12));
            content.addView(account, topMargin(-1, -2, 20));

            content.addView(onboardingButton("SIGN IN TO ROLLCALL  ↗", lime, 0xFF111111,
                    v -> showCloudCredentials(false)), topMargin(-1, dp(54), 18));
            TextView create = text("NEW HERE? CREATE ACCOUNT  ↗", 12, ink, Typeface.BOLD);
            create.setMinHeight(dp(48));
            create.setGravity(android.view.Gravity.CENTER);
            create.setOnClickListener(v -> showCloudCredentials(true));
            content.addView(create, topMargin(-1, dp(48), 5));
            content.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1));
            content.addView(onboardingButton("CONTINUE TO MYCAMU  ↗", panel, ink,
                    v -> startMyCamuConnection()), topMargin(-1, dp(56), 26));
            TextView caveat = text("MYCAMU PROVIDES THE CLASSES. ROLLCALL MAKES THEM CLEAR.", 10, muted, Typeface.BOLD);
            caveat.setGravity(android.view.Gravity.CENTER);
            caveat.setLineSpacing(dp(3), 1f);
            content.addView(caveat, topMargin(-1, -2, 10));
        }
        return scroll;
    }

    private Button onboardingButton(String label, int fill, int textColor, View.OnClickListener action) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setTypeface(appTypeface(), Typeface.BOLD);
        button.setTextColor(textColor);
        button.setBackground(background(fill, 0, 8));
        button.setOnClickListener(action);
        return button;
    }

    private View onboardingArtifact(int kind) {
        FrameLayout art = new FrameLayout(this);
        art.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        View backing = new View(this);
        backing.setBackground(background(palette.focusSurface, palette.ink, 12));
        backing.setRotation(5f);
        FrameLayout.LayoutParams back = new FrameLayout.LayoutParams(dp(190), dp(94), android.view.Gravity.CENTER);
        back.leftMargin = dp(12);
        art.addView(backing, back);
        LinearLayout face = new LinearLayout(this);
        face.setGravity(android.view.Gravity.CENTER_VERTICAL);
        face.setPadding(dp(16), dp(12), dp(16), dp(12));
        face.setBackground(background(palette.surface, palette.ink, 12));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        String kicker = kind == 0 ? "THE WEEK / LIVE" : kind == 1 ? "CLASS ALERT" : "ROLLCALL ACCOUNT";
        String value = kind == 0 ? new SimpleDateFormat("EEE · d MMM", Locale.getDefault()).format(new Date()).toUpperCase(Locale.getDefault())
                : kind == 1 ? "10 MIN BEFORE" : "YOUR DATA, IN SYNC";
        copy.addView(text(kicker, 9, palette.highlight, Typeface.BOLD));
        TextView detail = text(value, 14, palette.ink, Typeface.BOLD);
        detail.setMaxLines(2);
        copy.addView(detail, topMargin(-1, -2, 6));
        face.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        if (kind == 1) {
            ImageView icon = new ImageView(this);
            icon.setImageResource(R.drawable.ic_notification_bell);
            icon.setColorFilter(palette.ink);
            face.addView(icon, new LinearLayout.LayoutParams(dp(30), dp(30)));
        } else if (kind == 2) {
            RollcallLogo mark = new RollcallLogo(this, palette);
            face.addView(mark, new LinearLayout.LayoutParams(dp(36), dp(36)));
        } else {
            TextView marker = text(new SimpleDateFormat("ww", Locale.getDefault()).format(new Date()), 34, palette.ink, Typeface.BOLD);
            marker.setIncludeFontPadding(false);
            face.addView(marker);
        }
        FrameLayout.LayoutParams front = new FrameLayout.LayoutParams(dp(216), dp(86), android.view.Gravity.CENTER);
        front.rightMargin = dp(12);
        face.setRotation(-2f);
        art.addView(face, front);
        return art;
    }

    private LinearLayout onboardingCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(background(palette.surface, palette.border, 16));
        return card;
    }

    private void addOnboardingChoiceGroup(LinearLayout parent, String question, String[] choices, int[] icons) {
        final int ink = palette.ink, paper = palette.buttonText;
        TextView heading = text(question, 14, ink, Typeface.BOLD);
        heading.setPadding(0, dp(6), 0, dp(8));
        parent.addView(heading);
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setClipChildren(false);
        group.setClipToPadding(false);
        parent.setClipChildren(false);
        parent.setClipToPadding(false);
        String selected = onboardingAttendanceStyle;
        TextView[] labels = new TextView[choices.length];
        ImageView[] marks = new ImageView[choices.length];
        TextView[] checks = new TextView[choices.length];
        for (int i = 0; i < choices.length; i++) {
            final int optionIndex = i;
            final String choice = choices[i];
            final int icon = icons[i];
            boolean chosen = choice.equals(selected);
            LinearLayout option = new LinearLayout(this);
            option.setGravity(android.view.Gravity.CENTER_VERTICAL);
            option.setPadding(dp(14), 0, dp(14), 0);
            option.setBackground(background(chosen ? palette.button : palette.surface,
                    chosen ? palette.button : palette.border, 14));
            ImageView mark = new ImageView(this);
            mark.setImageResource(icon);
            mark.setColorFilter(chosen ? paper : ink);
            option.addView(mark, new LinearLayout.LayoutParams(dp(20), dp(20)));
            TextView label = text(choice, 14, chosen ? paper : ink, Typeface.BOLD);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1f);
            labelParams.leftMargin = dp(12);
            option.addView(label, labelParams);
            TextView check = text("✓", 17, paper, Typeface.BOLD);
            check.setVisibility(chosen ? View.VISIBLE : View.INVISIBLE);
            option.addView(check);
            labels[i] = label;
            marks[i] = mark;
            checks[i] = check;
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(50));
            params.bottomMargin = dp(6);
            group.addView(option, params);
            option.setOnClickListener(v -> {
                if (onboardingChoiceTransitioning) return;
                onboardingAttendanceStyle = choice;
                onboardingChoiceTransitioning = true;
                for (int itemIndex = 0; itemIndex < group.getChildCount(); itemIndex++) {
                    boolean isChosen = choices[itemIndex].equals(choice);
                    View item = group.getChildAt(itemIndex);
                    item.setBackground(background(isChosen ? palette.button : palette.surface,
                            isChosen ? palette.button : palette.border, 14));
                    labels[itemIndex].setTextColor(isChosen ? paper : ink);
                    marks[itemIndex].setColorFilter(isChosen ? paper : ink);
                    checks[itemIndex].setVisibility(isChosen ? View.VISIBLE : View.INVISIBLE);
                    item.setEnabled(false);
                }
                View chosenView = v;
                chosenView.animate().scaleX(1.035f).scaleY(1.035f).setDuration(110)
                        .withEndAction(() -> chosenView.animate().scaleX(1f).scaleY(1f).setDuration(150).start()).start();
                checks[optionIndex].setScaleX(.4f);
                checks[optionIndex].setScaleY(.4f);
                checks[optionIndex].animate().scaleX(1f).scaleY(1f).setDuration(220).start();
                onboardingChoiceStatus.setText("Great choice. Let’s set up your sign-in.");
                onboardingChoiceStatus.setVisibility(View.VISIBLE);
                onboardingAdvance = () -> showOnboardingStep(3);
                handler.postDelayed(onboardingAdvance, 1200);
            });
        }
        parent.addView(group);
        onboardingChoiceStatus = text("", 13, palette.muted, Typeface.BOLD);
        onboardingChoiceStatus.setGravity(android.view.Gravity.CENTER);
        onboardingChoiceStatus.setPadding(0, dp(5), 0, dp(4));
        onboardingChoiceStatus.setVisibility(View.GONE);
        parent.addView(onboardingChoiceStatus, new LinearLayout.LayoutParams(-1, -2));
    }

    private void requestOnboardingNotifications() {
        if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            getPreferences(MODE_PRIVATE).edit().remove(NOTIFICATION_PERMISSION_DENIED_KEY).apply();
            UpdatePushNotifications.subscribeIfAllowed(this);
            showOnboardingStep(2);
        } else {
            boolean deniedBefore = getPreferences(MODE_PRIVATE).getBoolean(NOTIFICATION_PERMISSION_DENIED_KEY, false);
            if (deniedBefore && !shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS)) {
                Intent settings = new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                settings.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName());
                startActivity(settings);
                return;
            }
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, ONBOARDING_NOTIFICATION_REQUEST);
        }
    }

    private void startMyCamuConnection() {
        if (onboardingAdvance != null) handler.removeCallbacks(onboardingAdvance);
        onboardingAdvance = null;
        onboardingStarted = true;
        getPreferences(MODE_PRIVATE).edit().putBoolean(ONBOARDING_STARTED_KEY, true).apply();
        showAppScreen(webPage);
        if (webView != null) webView.requestFocus();
        handler.removeCallbacks(routeMonitor);
        handler.post(routeMonitor);
    }

    private FrameLayout buildMyCamuConsentPage() {
        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(CANVAS);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        page.addView(layout, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(8), dp(20), dp(8));
        TextView back = text("‹", 30, INK, Typeface.NORMAL);
        back.setGravity(android.view.Gravity.CENTER);
        back.setContentDescription("Not now. Return to MyCamu sign-in");
        back.setOnClickListener(v -> deferMyCamuConsent());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView headerTitle = text("MYCAMU / CONNECTION", 11, darkTheme ? HERO : INK, Typeface.BOLD);
        headerTitle.setLetterSpacing(.08f);
        LinearLayout.LayoutParams headerTitleParams = new LinearLayout.LayoutParams(0, -2, 1f);
        headerTitleParams.leftMargin = dp(8);
        header.addView(headerTitle, headerTitleParams);
        layout.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(12), dp(24), dp(20));
        scroll.addView(content);
        layout.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        TextView title = text("REVIEW ACCESS.", 32, INK, Typeface.BOLD);
        title.setLetterSpacing(-.035f);
        content.addView(title);
        TextView intro = text("Your MyCamu sign-in worked. Choose whether Rollcall can read the student details below before it opens your dashboard.",
                14, MUTED, Typeface.NORMAL);
        intro.setLineSpacing(dp(3), 1f);
        content.addView(intro, topMargin(-1, -2, 8));

        LinearLayout account = new LinearLayout(this);
        account.setGravity(android.view.Gravity.CENTER_VERTICAL);
        account.setPadding(dp(14), dp(13), dp(14), dp(13));
        account.setBackground(background(0xFFC8C8FF, 0xFF121212, 16));
        LinearLayout.LayoutParams accountParams = new LinearLayout.LayoutParams(-1, -2);
        accountParams.topMargin = dp(20);
        content.addView(account, accountParams);
        TextView accountMark = text("M", 17, 0xFF121212, Typeface.BOLD);
        accountMark.setGravity(android.view.Gravity.CENTER);
        accountMark.setBackground(background(0xFFCCFF00, 0, 13));
        account.addView(accountMark, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout accountCopy = new LinearLayout(this);
        accountCopy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams accountCopyParams = new LinearLayout.LayoutParams(0, -2, 1f);
        accountCopyParams.leftMargin = dp(12);
        account.addView(accountCopy, accountCopyParams);
        accountCopy.addView(text("MYCAMU / STUDENT", 14, 0xFF121212, Typeface.BOLD));
        accountCopy.addView(text("Signed in on this device", 12, 0xFF333341, Typeface.NORMAL), topMargin(-1, -2, 3));
        TextView connected = text("CONNECTED ✓", 10, 0xFF176146, Typeface.BOLD);
        account.addView(connected);

        TextView scopeHeading = text("ROLLCALL WILL READ", 16, INK, Typeface.BOLD);
        content.addView(scopeHeading, topMargin(-1, -2, 22));
        addConsentScope(content, "Student profile", "Name, institution, and roll number when available.");
        addConsentScope(content, "Course attendance", "Course names, attendance totals, and percentages.");
        addConsentScope(content, "Class schedule", "Timetable entries used for the Week view and reminders.");
        addConsentScope(content, "Class history", "Present and absent marks reported by MyCamu.");
        addConsentScope(content, "Leave and OD", "Approval status and mapped class hours.");

        LinearLayout privacy = new LinearLayout(this);
        privacy.setOrientation(LinearLayout.VERTICAL);
        privacy.setPadding(dp(14), dp(13), dp(14), dp(13));
        privacy.setBackground(background(SECONDARY, 0, 14));
        content.addView(privacy, topMargin(-1, -2, 14));
        privacy.addView(text("Your password stays with MyCamu", 13, INK, Typeface.BOLD));
        TextView privacyText = text("Rollcall does not read or store your password. The data above is saved on this device and can be removed from About & Settings.",
                12, MUTED, Typeface.NORMAL);
        privacyText.setLineSpacing(dp(2), 1f);
        privacy.addView(privacyText, topMargin(-1, -2, 4));

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setPadding(dp(24), dp(10), dp(24), dp(16));
        footer.setBackgroundColor(CANVAS);
        layout.addView(footer, new LinearLayout.LayoutParams(-1, -2));
        myCamuConsentStatus = text("", 12, GREEN, Typeface.BOLD);
        myCamuConsentStatus.setGravity(android.view.Gravity.CENTER);
        myCamuConsentStatus.setVisibility(View.GONE);
        myCamuConsentStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        footer.addView(myCamuConsentStatus, topMargin(-1, -2, 0));
        myCamuConsentButton = onboardingButton("ALLOW ACCESS  ↗", HERO, HERO_TEXT, v -> approveMyCamuAccess());
        LinearLayout.LayoutParams allowParams = new LinearLayout.LayoutParams(-1, dp(52));
        allowParams.topMargin = dp(8);
        footer.addView(myCamuConsentButton, allowParams);
        Button notNow = button("Not now");
        notNow.setAllCaps(false);
        notNow.setBackgroundTintList(ColorStateList.valueOf(SECONDARY));
        notNow.setTextColor(INK);
        notNow.setOnClickListener(v -> deferMyCamuConsent());
        LinearLayout.LayoutParams laterParams = new LinearLayout.LayoutParams(-1, dp(48));
        laterParams.topMargin = dp(6);
        footer.addView(notNow, laterParams);
        return page;
    }

    private void addConsentScope(LinearLayout parent, String title, String description) {
        LinearLayout item = new LinearLayout(this);
        item.setGravity(android.view.Gravity.CENTER_VERTICAL);
        item.setPadding(0, dp(8), 0, dp(8));
        TextView check = text("✓", 14, GREEN, Typeface.BOLD);
        check.setGravity(android.view.Gravity.CENTER);
        check.setBackground(background(SECONDARY, BORDER, 100));
        item.addView(check, new LinearLayout.LayoutParams(dp(30), dp(30)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, -2, 1f);
        copyParams.leftMargin = dp(11);
        item.addView(copy, copyParams);
        copy.addView(text(title, 13, INK, Typeface.BOLD));
        TextView detail = text(description, 11, MUTED, Typeface.NORMAL);
        detail.setLineSpacing(dp(1), 1f);
        copy.addView(detail, topMargin(-1, -2, 2));
        parent.addView(item, new LinearLayout.LayoutParams(-1, -2));
    }

    private void showMyCamuConsent() {
        if (myCamuConsentPage == null) return;
        if (myCamuConsentStatus != null) myCamuConsentStatus.setVisibility(View.GONE);
        if (myCamuConsentButton != null) {
            myCamuConsentButton.setEnabled(true);
            myCamuConsentButton.setText("Allow access");
        }
        showAppScreen(myCamuConsentPage);
    }

    private void approveMyCamuAccess() {
        getPreferences(MODE_PRIVATE).edit().putBoolean(MYCAMU_CONSENT_KEY, true).apply();
        if (connectedOnce) reconnectInProgress = true;
        myCamuConsentButton.setEnabled(false);
        myCamuConsentButton.setText("✓  Access approved");
        myCamuConsentStatus.setText("Access approved. Preparing your dashboard…");
        myCamuConsentStatus.setVisibility(View.VISIBLE);
        handler.postDelayed(() -> {
            if (isFinishing() || isDestroyed()) return;
            showAppScreen(webPage);
            syncAttendance(true);
        }, 600L);
    }

    private void deferMyCamuConsent() {
        initialSyncAttempted = true;
        reconnectAttempted = true;
        if (webStatus != null) webStatus.setText("Access was not approved. Rollcall has not read your MyCamu data. You can review access later.");
        showAppScreen(webPage);
    }

    private void chooseTarget() {
        final int[] selected = {(int) Math.round(attendanceTarget)};
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(20), dp(22), dp(8));
        content.setBackground(background(SURFACE, 0, 20));

        content.addView(text("Attendance target", 21, INK, Typeface.BOLD));
        TextView description = text("Choose the minimum you want to maintain.", 13, MUTED, Typeface.NORMAL);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(-1, -2);
        descriptionParams.topMargin = dp(5);
        content.addView(description, descriptionParams);

        TextView value = text(selected[0] + "%", 42, INK, Typeface.BOLD);
        value.setGravity(android.view.Gravity.CENTER);
        value.setPadding(0, dp(16), 0, dp(4));
        content.addView(value, new LinearLayout.LayoutParams(-1, -2));

        SeekBar slider = new SeekBar(this);
        slider.setMax(50);
        slider.setProgress(selected[0] - 50);
        slider.setProgressTintList(ColorStateList.valueOf(HERO));
        slider.setThumbTintList(ColorStateList.valueOf(HERO));
        content.addView(slider, new LinearLayout.LayoutParams(-1, dp(42)));

        LinearLayout scale = new LinearLayout(this);
        scale.setOrientation(LinearLayout.HORIZONTAL);
        TextView min = text("50%", 11, MUTED, Typeface.NORMAL);
        TextView mid = text("75%", 11, MUTED, Typeface.NORMAL);
        TextView max = text("100%", 11, MUTED, Typeface.NORMAL);
        scale.addView(min, new LinearLayout.LayoutParams(0, -2, 1));
        mid.setGravity(android.view.Gravity.CENTER);
        scale.addView(mid, new LinearLayout.LayoutParams(0, -2, 1));
        max.setGravity(android.view.Gravity.RIGHT);
        scale.addView(max, new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(scale);

        TextView quickLabel = text("Quick targets", 12, MUTED, Typeface.BOLD);
        LinearLayout.LayoutParams quickLabelParams = new LinearLayout.LayoutParams(-1, -2);
        quickLabelParams.topMargin = dp(18);
        content.addView(quickLabel, quickLabelParams);
        LinearLayout presets = new LinearLayout(this);
        presets.setOrientation(LinearLayout.HORIZONTAL);
        presets.setGravity(android.view.Gravity.CENTER_VERTICAL);
        presets.setPadding(0, dp(8), 0, dp(4));
        int[] presetValues = {60, 70, 75, 80, 90};
        TextView[] presetViews = new TextView[presetValues.length];
        for (int i = 0; i < presetValues.length; i++) {
            final int preset = presetValues[i];
            TextView chip = text(preset + "%", 12, INK, Typeface.BOLD);
            chip.setGravity(android.view.Gravity.CENTER);
            chip.setPadding(dp(5), dp(10), dp(5), dp(10));
            boolean active = preset == selected[0];
            chip.setTextColor(active ? HERO_TEXT : INK);
            chip.setBackground(background(active ? HERO : SECONDARY, 0, 12));
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(0, -2, 1);
            if (i > 0) chipParams.leftMargin = dp(5);
            presets.addView(chip, chipParams);
            presetViews[i] = chip;
            chip.setOnClickListener(v -> slider.setProgress(preset - 50));
        }
        content.addView(presets);
        TextView hint = text("Courses below this mark will be highlighted on your dashboard.", 12, MUTED, Typeface.NORMAL);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
        hintParams.topMargin = dp(12);
        content.addView(hint, hintParams);

        int dialogTheme = cloudDialogTheme();
        AlertDialog dialog = new AlertDialog.Builder(this, dialogTheme).setView(content).create();
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(-1, -2);
        actionsParams.topMargin = dp(20);
        content.addView(actions, actionsParams);

        Button cancel = new Button(this);
        cancel.setText("Cancel");
        cancel.setAllCaps(false);
        cancel.setTypeface(appTypeface(), Typeface.BOLD);
        cancel.setTextColor(INK);
        cancel.setMinWidth(0);
        cancel.setMinHeight(dp(48));
        cancel.setPadding(0, 0, 0, 0);
        cancel.setBackground(background(SECONDARY, 0, 12));
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        cancelParams.rightMargin = dp(6);
        actions.addView(cancel, cancelParams);
        cancel.setOnClickListener(v -> dialog.dismiss());

        Button save = new Button(this);
        save.setText("Save");
        save.setAllCaps(false);
        save.setTypeface(appTypeface(), Typeface.BOLD);
        save.setTextColor(HERO_TEXT);
        save.setMinWidth(0);
        save.setMinHeight(dp(48));
        save.setPadding(0, 0, 0, 0);
        save.setBackground(background(HERO, 0, 12));
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        saveParams.leftMargin = dp(6);
        actions.addView(save, saveParams);
        save.setOnClickListener(v -> {
            attendanceTarget = selected[0];
            getPreferences(MODE_PRIVATE).edit().putInt("attendance_target", (int) attendanceTarget).apply();
            if (settingsTargetValue != null) settingsTargetValue.setText((int) attendanceTarget + "%   EDIT →");
            AttendanceEventNotifier.setTargetBaseline(this, LiveSnapshotStore.get().attendance, (int) attendanceTarget);
            expandedCourses.clear();
            render(LiveSnapshotStore.get());
            dialog.dismiss();
        });

        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            private void update(int target) {
                selected[0] = target;
                value.setText(target + "%");
                for (int i = 0; i < presetViews.length; i++) {
                    boolean active = presetValues[i] == target;
                    presetViews[i].setTextColor(active ? HERO_TEXT : INK);
                    presetViews[i].setBackground(background(active ? HERO : SECONDARY, 0, 12));
                }
            }
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { update(progress + 50); }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        slider.setProgress(selected[0] - 50);
        dialog.show();
        dialog.getWindow().setBackgroundDrawable(background(SURFACE, BORDER, 20));
        dialog.getWindow().setLayout(getResources().getDisplayMetrics().widthPixels - dp(32),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void setClassRemindersEnabled(boolean enabled) {
        if (enabled && Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, REMINDER_PERMISSION_REQUEST);
            return;
        }
        classRemindersEnabled = enabled;
        ClassReminderScheduler.setEnabled(this, enabled);
        updateReminderSettings();
        if (enabled) animateReminderBell();
        render(LiveSnapshotStore.get());
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == ONBOARDING_NOTIFICATION_REQUEST) {
            boolean granted = grantResults.length > 0
                    && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED;
            getPreferences(MODE_PRIVATE).edit().putBoolean(NOTIFICATION_PERMISSION_DENIED_KEY, !granted).apply();
            if (granted)
                UpdatePushNotifications.subscribeIfAllowed(this);
            showOnboardingStep(2);
            return;
        }
        if (requestCode != REMINDER_PERMISSION_REQUEST) return;
        classRemindersEnabled = grantResults.length > 0
                && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED;
        ClassReminderScheduler.setEnabled(this, classRemindersEnabled);
        updateReminderSettings();
        if (classRemindersEnabled) animateReminderBell();
        ReleaseUpdateNotifier.check(this);
        render(LiveSnapshotStore.get());
    }

    private void toggleTheme() {
        if (!connectedOnce) return;
        darkTheme = !darkTheme;
        getPreferences(MODE_PRIVATE).edit().putBoolean(DARK_KEY, darkTheme).apply();
        recreate();
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean("restore_about_settings", aboutPage != null && aboutPage.getVisibility() == View.VISIBLE);
        super.onSaveInstanceState(outState);
    }

    private void choosePalette() {
        if (!connectedOnce) return;
        int selected = colorThemeIndex;
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(18), dp(20), dp(8));
        content.setBackground(background(SURFACE, 0, 20));
        content.addView(text(darkTheme ? "Night colors" : "Day colors", 20, INK, Typeface.BOLD));
        TextView hint = text("Pick a palette for " + (darkTheme ? "dark" : "light") + " mode.", 13, MUTED, Typeface.NORMAL);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
        hintParams.topMargin = dp(4);
        content.addView(hint, hintParams);

        for (int i : new int[]{0, 5, 6}) {
            if (i == 6) {
                TextView specialTitle = text("Special Themes", 13, MUTED, Typeface.BOLD);
                LinearLayout.LayoutParams specialParams = new LinearLayout.LayoutParams(-1, -2);
                specialParams.topMargin = dp(18);
                content.addView(specialTitle, specialParams);
            }
            final int index = i;
            AppPalette option = AppPalette.get(darkTheme, i);
            LinearLayout optionRow = new LinearLayout(this);
            optionRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
            optionRow.setPadding(dp(12), dp(10), dp(12), dp(10));
            optionRow.setBackground(background(i == selected ? SECONDARY : SURFACE, BORDER, 14));
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            rowParams.topMargin = dp(9);
            content.addView(optionRow, rowParams);

            LinearLayout labels = new LinearLayout(this);
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.addView(text(option.name, 14, INK, Typeface.BOLD));
            labels.addView(text(i >= 6 ? "Special theme · " + (darkTheme ? "Dark" : "Light")
                    : (darkTheme ? "Dark palette" : "Light palette"), 11, MUTED, Typeface.NORMAL));
            optionRow.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));

            LinearLayout swatches = new LinearLayout(this);
            swatches.setOrientation(LinearLayout.HORIZONTAL);
            int[] colors = {option.hero, option.green, option.highlight, option.soft};
            for (int color : colors) {
                View swatch = new View(this);
                swatch.setBackground(background(color, 0, 5));
                LinearLayout.LayoutParams swatchParams = new LinearLayout.LayoutParams(dp(13), dp(24));
                swatchParams.leftMargin = dp(3);
                swatches.addView(swatch, swatchParams);
            }
            LinearLayout.LayoutParams swatchParams = new LinearLayout.LayoutParams(-2, -2);
            swatchParams.rightMargin = dp(8);
            optionRow.addView(swatches, swatchParams);
            optionRow.addView(text(i == selected ? "✓" : "", 17, HIGHLIGHT, Typeface.BOLD));
            optionRow.setOnClickListener(v -> {
                colorThemeIndex = index;
                getPreferences(MODE_PRIVATE).edit().putInt(COLOR_THEME_KEY, index).apply();
                recreate();
            });
        }
        int dialogTheme = cloudDialogTheme();
        ScrollView paletteScroll = new ScrollView(this);
        paletteScroll.setFillViewport(true);
        paletteScroll.addView(content);
        AlertDialog dialog = new AlertDialog.Builder(this, dialogTheme)
                .setView(paletteScroll).setNegativeButton("Done", null).create();
        dialog.show();
        dialog.getWindow().setBackgroundDrawable(background(SURFACE, BORDER, 20));
    }

    private void saveProfile(JSONObject profile) {
        if (profile == null) return;
        studentName = profile.optString("name", "").trim();
        instituteName = profile.optString("institute", "").trim();
        rollNumber = profile.optString("rollNo", "").trim();
        rollNumberLabel = profile.optString("rollLabel", "Roll No.").trim();
        getPreferences(MODE_PRIVATE).edit()
                .putString("student_name", studentName)
                .putString("institute_name", instituteName)
                .putString("roll_number", rollNumber)
                .putString("roll_number_label", rollNumberLabel)
                .apply();
        render(LiveSnapshotStore.get());
    }

    private ScrollView buildAboutPage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(CANVAS);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(22), dp(20), dp(30));
        scroll.addView(page);

        Button back = button("‹  Dashboard");
        back.setBackgroundTintList(ColorStateList.valueOf(SECONDARY));
        back.setTextColor(INK);
        back.setOnClickListener(v -> showDashboard());
        page.addView(back, new LinearLayout.LayoutParams(-1, dp(48)));
        TextView eyebrow = text("06 / CONTROL ROOM", 10, HIGHLIGHT, Typeface.BOLD);
        eyebrow.setLetterSpacing(.12f);
        eyebrow.setPadding(0, dp(24), 0, dp(8));
        page.addView(eyebrow);
        TextView title = text("Settings.", 32, INK, Typeface.BOLD);
        title.setTypeface(appTypeface(), Typeface.BOLD);
        title.setPadding(0, 0, 0, dp(3));
        page.addView(title);
        TextView subtitle = text("Make this your space.", 14, MUTED, Typeface.NORMAL);
        subtitle.setPadding(0, 0, 0, dp(16));
        page.addView(subtitle);

        LinearLayout settings = cardContainer();
        settings.addView(text("APPEARANCE · NOTIFICATIONS · TARGET", 10, HIGHLIGHT, Typeface.BOLD));
        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        modeRow.setPadding(0, dp(8), 0, 0);
        LinearLayout modeCopy = new LinearLayout(this);
        modeCopy.setOrientation(LinearLayout.VERTICAL);
        modeCopy.addView(text("Appearance", 15, INK, Typeface.BOLD));
        TextView modeHint = text(darkTheme ? "Dark · Rollcall Signal" : "Light · Rollcall Signal", 12, MUTED, Typeface.NORMAL);
        LinearLayout.LayoutParams modeHintParams = new LinearLayout.LayoutParams(-1, -2);
        modeHintParams.topMargin = dp(4);
        modeCopy.addView(modeHint, modeHintParams);
        modeRow.addView(modeCopy, new LinearLayout.LayoutParams(0, -2, 1));
        Switch appearanceSwitch = new Switch(this);
        appearanceSwitch.setMinHeight(dp(48));
        appearanceSwitch.setSwitchMinWidth(dp(52));
        appearanceSwitch.setThumbTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{HERO, SURFACE}));
        appearanceSwitch.setTrackTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{HERO, BORDER}));
        appearanceSwitch.setChecked(darkTheme);
        appearanceSwitch.setContentDescription("Dark appearance");
        appearanceSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (checked == darkTheme) return;
            darkTheme = checked;
            getPreferences(MODE_PRIVATE).edit().putBoolean(DARK_KEY, darkTheme).apply();
            recreate();
        });
        modeRow.addView(appearanceSwitch, new LinearLayout.LayoutParams(-2, dp(48)));
        settings.addView(modeRow);
        LinearLayout targetRow = new LinearLayout(this);
        targetRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        targetRow.setPadding(0, dp(12), 0, dp(4));
        LinearLayout targetCopy = new LinearLayout(this);
        targetCopy.setOrientation(LinearLayout.VERTICAL);
        targetCopy.addView(text("Attendance target", 15, INK, Typeface.BOLD));
        targetCopy.addView(text("Your minimum attendance goal.", 12, MUTED, Typeface.NORMAL), topMargin(-1, -2, 4));
        targetRow.addView(targetCopy, new LinearLayout.LayoutParams(0, -2, 1));
        settingsTargetValue = text((int) attendanceTarget + "%   EDIT →", 12, HERO_TEXT, Typeface.BOLD);
        settingsTargetValue.setGravity(android.view.Gravity.CENTER);
        settingsTargetValue.setPadding(dp(12), dp(10), dp(12), dp(10));
        settingsTargetValue.setBackground(background(HERO, 0, 10));
        targetRow.addView(settingsTargetValue, new LinearLayout.LayoutParams(-2, dp(44)));
        targetRow.setOnClickListener(v -> chooseTarget());
        targetRow.setFocusable(true);
        targetRow.setContentDescription("Attendance target " + (int) attendanceTarget + " percent. Edit");
        settings.addView(targetRow);
        LinearLayout reminderRow = new LinearLayout(this);
        reminderRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        reminderRow.setPadding(0, dp(8), 0, 0);
        LinearLayout reminderCopy = new LinearLayout(this);
        reminderCopy.setOrientation(LinearLayout.VERTICAL);
        reminderCopy.addView(text("Class reminders", 15, INK, Typeface.BOLD));
        reminderPermissionHint = text("A quiet reminder 10 minutes before each class.", 12, MUTED, Typeface.NORMAL);
        LinearLayout.LayoutParams reminderHintParams = new LinearLayout.LayoutParams(-1, -2);
        reminderHintParams.topMargin = dp(4);
        reminderCopy.addView(reminderPermissionHint, reminderHintParams);
        LinearLayout.LayoutParams eventHintParams = new LinearLayout.LayoutParams(-1, -2);
        eventHintParams.topMargin = dp(4);
        reminderCopy.addView(text("Attendance, OD, schedule, and sync alerts follow Android notification settings.", 11, MUTED, Typeface.NORMAL), eventHintParams);
        reminderRow.addView(reminderCopy, new LinearLayout.LayoutParams(0, -2, 1));
        reminderBellIcon = new ImageView(this);
        reminderBellIcon.setImageResource(R.drawable.ic_notification_bell);
        reminderBellIcon.setColorFilter(classRemindersEnabled ? HERO : MUTED);
        reminderBellIcon.setContentDescription("Class reminder notifications");
        reminderRow.addView(reminderBellIcon, new LinearLayout.LayoutParams(dp(28), dp(28)));
        reminderSwitch = new Switch(this);
        reminderSwitch.setMinHeight(dp(48));
        reminderSwitch.setSwitchMinWidth(dp(52));
        reminderSwitch.setPadding(dp(8), 0, 0, 0);
        reminderSwitch.setThumbTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{HERO, SURFACE}));
        reminderSwitch.setTrackTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{HERO, BORDER}));
        reminderSwitch.setContentDescription("Class reminders");
        reminderRow.addView(reminderSwitch, new LinearLayout.LayoutParams(-2, dp(48)));
        settings.addView(reminderRow);
        notificationSettingsButton = button("Manage Android notifications");
        notificationSettingsButton.setOnClickListener(v -> {
            Intent intent = new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(intent);
        });
        LinearLayout.LayoutParams notificationSettingsParams = new LinearLayout.LayoutParams(-1, dp(48));
        notificationSettingsParams.topMargin = dp(8);
        settings.addView(notificationSettingsButton, notificationSettingsParams);
        page.addView(settings);
        updateReminderSettings();

        LinearLayout cloud = cardContainer();
        LinearLayout.LayoutParams cloudParams = new LinearLayout.LayoutParams(-1, -2);
        cloudParams.topMargin = dp(12);
        page.addView(cloud, cloudParams);
        cloud.addView(text("ROLLCALL WEB ACCOUNT", 12, MUTED, Typeface.BOLD));
        FirebaseUser cloudUser = FirebaseAuth.getInstance().getCurrentUser();
        boolean cloudPhoneLinked = cloudUser != null && CloudAccountClient.deviceId(this).equals(getSharedPreferences("rollcall_cloud_account", MODE_PRIVATE).getString("registered_device_id", ""));
        TextView cloudInfo = text(cloudUser == null ? "Optional. Create an account to securely view your synced Rollcall snapshot on the web." : cloudUser.getEmail() + (cloudUser.isEmailVerified() ? (cloudPhoneLinked ? " · phone linked" : " · connect this phone") : " · verify email to connect"), 13, MUTED, Typeface.NORMAL);
        cloudInfo.setPadding(0, dp(8), 0, dp(8));
        cloud.addView(cloudInfo);
        Button cloudButton = button(cloudUser == null ? "Sign in or create account" : "Manage web account");
        cloudButton.setOnClickListener(v -> showCloudAccountDialog());
        cloud.addView(cloudButton, new LinearLayout.LayoutParams(-1, dp(48)));
        TextView cloudPrivacy = text("Your MyCamu password is never sent to Rollcall. A verified email and this app are required. Attendance, schedule, leave/OD and KCT marks are encrypted in transit and stored on Rollcall’s cloud service for the web mirror. This is not end-to-end encryption.", 12, MUTED, Typeface.NORMAL);
        cloudPrivacy.setPadding(0, dp(8), 0, 0);
        cloud.addView(cloudPrivacy);

        String versionLabel = "Version";
        try { versionLabel += " " + getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (android.content.pm.PackageManager.NameNotFoundException ignored) { }
        TextView version = text(versionLabel, 12, MUTED, Typeface.NORMAL);
        version.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams versionParams = new LinearLayout.LayoutParams(-1, -2);
        versionParams.topMargin = dp(8);
        versionParams.bottomMargin = dp(8);
        page.addView(version, versionParams);

        LinearLayout project = cardContainer();
        project.addView(text("THE PROJECT", 12, MUTED, Typeface.BOLD));
        TextView description = text("Rollcall brings your academic activity into a clear, useful dashboard. It tracks progress toward your attendance target and keeps the latest snapshot on this device.", 15, INK, Typeface.NORMAL);
        description.setPadding(0, dp(8), 0, 0);
        project.addView(description);
        TextView privacy = text("Your connected academic account is used to read attendance data, which is stored locally. Rollcall is an independent project and is not affiliated with any institution or service provider.", 13, MUTED, Typeface.NORMAL);
        privacy.setPadding(0, dp(10), 0, 0);
        project.addView(privacy);
        page.addView(project);

        LinearLayout author = cardContainer();
        LinearLayout.LayoutParams authorParams = new LinearLayout.LayoutParams(-1, -2);
        authorParams.topMargin = dp(12);
        page.addView(author, authorParams);
        author.addView(text("BUILT BY", 12, MUTED, Typeface.BOLD));
        TextView githubProfile = linkText("GitHub · @inianexe");
        githubProfile.setPadding(0, dp(8), 0, dp(4));
        githubProfile.setOnClickListener(v -> openUrl("https://github.com/inianexe"));
        author.addView(githubProfile);
        TextView repository = linkText("View the Rollcall project");
        repository.setPadding(0, dp(4), 0, 0);
        repository.setOnClickListener(v -> openUrl("https://github.com/inianexe/Rollcall"));
        author.addView(repository);
        Button update = button("Check for updates");
        LinearLayout.LayoutParams updateParams = new LinearLayout.LayoutParams(-1, dp(48));
        updateParams.topMargin = dp(14);
        author.addView(update, updateParams);
        update.setOnClickListener(v -> {
            update.setEnabled(false);
            update.setText("Checking GitHub…");
            ReleaseUpdateNotifier.checkNow(this, (release, error) -> {
                update.setEnabled(true);
                update.setText("Check for updates");
                if (release != null) ReleaseUpdateNotifier.openUpdate(this, release);
                else Toast.makeText(this, error.isEmpty() ? "You’re up to date." : error, Toast.LENGTH_SHORT).show();
            });
        });

        LinearLayout legal = cardContainer();
        LinearLayout.LayoutParams legalParams = new LinearLayout.LayoutParams(-1, -2);
        legalParams.topMargin = dp(12);
        page.addView(legal, legalParams);
        legal.addView(text("PRIVACY & TERMS", 12, MUTED, Typeface.BOLD));
        TextView privacyPolicy = linkText("Privacy policy");
        privacyPolicy.setPadding(0, dp(8), 0, dp(4));
        privacyPolicy.setOnClickListener(v -> openUrl("https://github.com/inianexe/Rollcall/blob/main/PRIVACY.md"));
        legal.addView(privacyPolicy);
        TextView termsOfUse = linkText("Terms of use");
        termsOfUse.setPadding(0, dp(4), 0, 0);
        termsOfUse.setOnClickListener(v -> openUrl("https://github.com/inianexe/Rollcall/blob/main/TERMS.md"));
        legal.addView(termsOfUse);

        LinearLayout data = cardContainer();
        LinearLayout.LayoutParams dataParams = new LinearLayout.LayoutParams(-1, -2);
        dataParams.topMargin = dp(12);
        page.addView(data, dataParams);
        data.addView(text("SAVED MYCAMU DATA", 12, MUTED, Typeface.BOLD));
        TextView dataDescription = text("Disconnect this account and remove saved attendance, timetable, history, and profile details from this device.",
                13, INK, Typeface.NORMAL);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(-1, -2);
        descriptionParams.topMargin = dp(7);
        data.addView(dataDescription, descriptionParams);
        Button clear = button("Disconnect and clear saved data");
        clear.setTextColor(RED);
        clear.setBackgroundTintList(ColorStateList.valueOf(SECONDARY));
        clear.setOnClickListener(v -> confirmClearSavedData());
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(-1, dp(48));
        clearParams.topMargin = dp(10);
        data.addView(clear, clearParams);
        return scroll;
    }

    private int cloudDialogTheme() {
        return darkTheme ? R.style.TorchDialogDark : R.style.TorchDialogLight;
    }

    private void showCloudAccountDialog() {
        FirebaseUser existing = FirebaseAuth.getInstance().getCurrentUser();
        if (existing != null) {
            String[] actions = existing.isEmailVerified()
                    ? new String[]{"Connect this phone", "Replace recovery codes", "Delete Rollcall cloud account", "Sign out", "Close"}
                    : new String[]{"Resend verification", "Delete Rollcall account", "Sign out", "Close"};
            new AlertDialog.Builder(this, cloudDialogTheme()).setTitle("Rollcall web account")
                    .setItems(actions, (d, which) -> {
                        if (!existing.isEmailVerified()) {
                            if (which == 0) existing.sendEmailVerification().addOnCompleteListener(task -> Toast.makeText(this, task.isSuccessful() ? "Verification email sent." : "Could not send verification email.", Toast.LENGTH_LONG).show());
                            else if (which == 1) confirmCloudAccountDelete();
                            else if (which == 2) signOutCloudAccount();
                        } else if (which == 0) registerCloudDevice(null);
                        else if (which == 1) showRecoveryCodes();
                        else if (which == 2) confirmCloudAccountDelete();
                        else if (which == 3) signOutCloudAccount();
                    }).setNegativeButton("Cancel", null).show();
            return;
        }
        showCloudCredentials(false);
    }

    private void showCloudCredentials(boolean create) {
        if (FirebaseAuth.getInstance().getCurrentUser() != null) { showCloudAccountDialog(); return; }
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(dp(24), dp(12), dp(24), dp(4));
        EditText email = new EditText(this); email.setSingleLine(true); email.setInputType(33); email.setHint("Email address"); fields.addView(email);
        EditText password = new EditText(this); password.setSingleLine(true); password.setInputType(129); password.setHint("Password · 8 characters minimum"); fields.addView(password);
        new AlertDialog.Builder(this, cloudDialogTheme()).setTitle(create ? "Create Rollcall account" : "Sign in to Rollcall")
                .setMessage("This account brings your phone snapshot to the web. Use a separate password from MyCamu.")
                .setView(fields)
                .setPositiveButton(create ? "Create account" : "Sign in", (d, w) -> {
                    if (create) cloudCreateAccount(email.getText().toString().trim(), password.getText().toString());
                    else cloudSignIn(email.getText().toString().trim(), password.getText().toString());
                })
                .setNeutralButton(create ? "Have an account?" : "No account?", (d, w) -> showCloudCredentials(!create))
                .setNegativeButton("Not now", null).show();
    }

    private void cloudCreateAccount(String email, String password) {
        if (email.isEmpty() || password.length() < 8) { Toast.makeText(this, "Enter a valid email and a password with at least 8 characters.", Toast.LENGTH_LONG).show(); return; }
        FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password).addOnCompleteListener(this, task -> {
            if (!task.isSuccessful()) { Toast.makeText(this, task.getException() == null ? "Account creation failed." : task.getException().getMessage(), Toast.LENGTH_LONG).show(); return; }
            FirebaseUser user = task.getResult().getUser();
            if (user == null) return;
            user.sendEmailVerification().addOnCompleteListener(this, sent -> {
                getSharedPreferences("rollcall_cloud_account", MODE_PRIVATE).edit().putBoolean("registration_pending", true).apply();
                Toast.makeText(this, sent.isSuccessful() ? "Account created. Verify your email, then sign in here to connect this phone." : "Account created, but the verification email could not be sent. Use Resend verification.", Toast.LENGTH_LONG).show();
                if (connectedOnce) showAbout(); else showOnboardingStep(3);
            });
        });
    }

    private void cloudSignIn(String email, String password) {
        FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password).addOnCompleteListener(this, task -> {
            if (!task.isSuccessful()) { Toast.makeText(this, task.getException() == null ? "Sign-in failed." : task.getException().getMessage(), Toast.LENGTH_LONG).show(); return; }
            FirebaseUser user = task.getResult().getUser();
            if (user == null) return;
            if (!user.isEmailVerified()) {
                user.sendEmailVerification();
                Toast.makeText(this, "Verify your email before connecting the web dashboard.", Toast.LENGTH_LONG).show();
                if (connectedOnce) showAbout(); else showOnboardingStep(3);
            } else registerCloudDevice(null);
        });
    }

    private void registerCloudDevice(String recoveryCode) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || !user.isEmailVerified()) return;
        new Thread(() -> {
            try {
                String token = Tasks.await(user.getIdToken(true), 20, TimeUnit.SECONDS).getToken();
                String fcm = Tasks.await(FirebaseMessaging.getInstance().getToken(), 20, TimeUnit.SECONDS);
                JSONObject registered = CloudAccountClient.registerDevice(token, CloudAccountClient.deviceId(this), fcm, recoveryCode);
                getSharedPreferences("rollcall_cloud_account", MODE_PRIVATE).edit().putBoolean("registration_pending", false).putString("registered_device_id", CloudAccountClient.deviceId(this)).apply();
                CloudAccountClient.enqueueSnapshotSync(this);
                JSONArray recoveryCodes = registered.optJSONArray("recoveryCodes");
                runOnUiThread(() -> {
                    Toast.makeText(this, "This phone is linked to your Rollcall web account.", Toast.LENGTH_LONG).show();
                    if (connectedOnce) showAbout(); else showOnboardingStep(3);
                    if (recoveryCodes != null) new AlertDialog.Builder(this, cloudDialogTheme())
                            .setTitle("Save your recovery codes")
                            .setMessage("Each code can be used once if you replace or lose this phone. Store them somewhere private. They are shown only now.\n\n" + android.text.TextUtils.join("\n", jsonArrayStrings(recoveryCodes)))
                            .setPositiveButton("Done", null).show();
                });
            } catch (Exception error) {
                String message = error.getMessage() == null ? "Could not connect this phone." : error.getMessage();
                if (message.contains("recovery code")) runOnUiThread(this::promptCloudRecoveryCode);
                else runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
            }
        }, "rollcall-cloud-device").start();
    }

    private void promptCloudRecoveryCode() {
        EditText code = new EditText(this); code.setSingleLine(true); code.setHint("One-time recovery code");
        new AlertDialog.Builder(this, cloudDialogTheme()).setTitle("Replace trusted phone")
                .setMessage("Enter one unused backup code. This signs out web sessions on your old phone.")
                .setView(code).setPositiveButton("Connect phone", (d, w) -> registerCloudDevice(code.getText().toString().trim()))
                .setNegativeButton("Cancel", null).show();
    }

    private void showRecoveryCodes() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser(); if (user == null) return;
        new AlertDialog.Builder(this, cloudDialogTheme()).setTitle("Replace recovery codes?")
                .setMessage("This invalidates every previous recovery code. Save the new set somewhere private.")
                .setPositiveButton("Replace codes", (d, w) -> new Thread(() -> {
            try {
                String token = Tasks.await(user.getIdToken(false), 20, TimeUnit.SECONDS).getToken();
                JSONObject response = CloudAccountClient.generateRecoveryCodes(token);
                JSONArray codes = response.optJSONArray("codes");
                String value = codes == null ? "" : android.text.TextUtils.join("\n", jsonArrayStrings(codes));
                runOnUiThread(() -> new AlertDialog.Builder(this, cloudDialogTheme()).setTitle("Save your recovery codes")
                        .setMessage("Each code can be used once if you replace or lose this phone. Store them somewhere private. They are shown only now.\n\n" + value)
                        .setPositiveButton("Done", null).show());
            } catch (Exception error) { runOnUiThread(() -> Toast.makeText(this, "Could not create recovery codes.", Toast.LENGTH_LONG).show()); }
        }, "rollcall-recovery-codes").start()).setNegativeButton("Cancel", null).show();
    }

    private void signOutCloudAccount() {
        FirebaseAuth.getInstance().signOut();
        Toast.makeText(this, "Signed out of Rollcall web account. Your cloud snapshot remains available when you sign in again.", Toast.LENGTH_LONG).show();
        showAbout();
    }

    private void confirmCloudAccountDelete() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser(); if (user == null) return;
        EditText password = new EditText(this); password.setSingleLine(true); password.setInputType(129); password.setHint("Rollcall account password");
        new AlertDialog.Builder(this, cloudDialogTheme()).setTitle("Delete Rollcall cloud account")
                .setMessage("This deletes your Rollcall web account, synced cloud snapshot and recovery codes. MyCamu and your phone’s local snapshot remain unchanged. This cannot be undone.")
                .setView(password).setPositiveButton("Delete account", (d, w) -> {
                    if (user.getEmail() == null) return;
                    user.reauthenticate(EmailAuthProvider.getCredential(user.getEmail(), password.getText().toString())).addOnCompleteListener(this, reauth -> {
                        if (!reauth.isSuccessful()) { Toast.makeText(this, "Password check failed. Account was not deleted.", Toast.LENGTH_LONG).show(); return; }
                        new Thread(() -> {
                            try {
                                if (user.isEmailVerified()) {
                                    String token = Tasks.await(user.getIdToken(true), 20, TimeUnit.SECONDS).getToken();
                                    CloudAccountClient.deleteCloudAccount(token);
                                }
                                Tasks.await(user.delete(), 20, TimeUnit.SECONDS);
                                FirebaseAuth.getInstance().signOut();
                                runOnUiThread(() -> { Toast.makeText(this, "Rollcall cloud account deleted. Your phone data is unchanged.", Toast.LENGTH_LONG).show(); showAbout(); });
                            } catch (Exception error) { runOnUiThread(() -> Toast.makeText(this, "Could not delete the account. Try again while online.", Toast.LENGTH_LONG).show()); }
                        }, "rollcall-delete-account").start();
                    });
                }).setNegativeButton("Cancel", null).show();
    }

    private List<String> jsonArrayStrings(JSONArray values) {
        List<String> result = new ArrayList<>(); for (int i = 0; i < values.length(); i++) result.add(values.optString(i)); return result;
    }

    private void showPendingWebCode(String challengeId) {
        if (challengeId == null || challengeId.isEmpty()) return;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser(); if (user == null || !user.isEmailVerified()) { Toast.makeText(this, "Sign in to your Rollcall account to retrieve the web code.", Toast.LENGTH_LONG).show(); return; }
        new Thread(() -> {
            try {
                String token = Tasks.await(user.getIdToken(false), 20, TimeUnit.SECONDS).getToken();
                JSONObject result = CloudAccountClient.fetchPendingCode(token, CloudAccountClient.deviceId(this), challengeId);
                runOnUiThread(() -> {
                    TextView code = text(result.optString("code"), 32, HERO, Typeface.BOLD); code.setTextIsSelectable(true); code.setGravity(android.view.Gravity.CENTER); code.setPadding(dp(16), dp(10), dp(16), dp(10));
                    new AlertDialog.Builder(this, cloudDialogTheme()).setTitle("Web sign-in code")
                            .setMessage("Select and copy this six-digit code into your browser. It expires in 5 minutes.")
                            .setView(code).setPositiveButton("Done", null).show();
                });
            } catch (Exception error) { runOnUiThread(() -> Toast.makeText(this, "The web sign-in code expired or this is not the trusted phone.", Toast.LENGTH_LONG).show()); }
        }, "rollcall-web-code").start();
    }

    private View holdToConfirm(String labelText, int accent, Runnable confirmed) {
        final long holdMs = 900L;
        FrameLayout control = new FrameLayout(this);
        control.setBackground(background(SURFACE, BORDER, 16));
        control.setFocusable(true);
        control.setClickable(true);
        control.setContentDescription("Hold for one second to " + labelText.toLowerCase(Locale.ROOT)
                + ". Accessibility users can activate this control.");
        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        progress.setProgressTintList(ColorStateList.valueOf(accent));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(SECONDARY));
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(-1, dp(4), android.view.Gravity.BOTTOM);
        control.addView(progress, progressParams);
        TextView label = text(labelText, 14, accent, Typeface.BOLD);
        label.setGravity(android.view.Gravity.CENTER);
        control.addView(label, new FrameLayout.LayoutParams(-1, dp(52)));
        final long[] startedAt = {0L};
        final boolean[] complete = {false};
        Runnable[] tick = new Runnable[1];
        Runnable cancel = () -> {
            if (startedAt[0] == 0L || complete[0]) return;
            startedAt[0] = 0L;
            handler.removeCallbacks(tick[0]);
            progress.setProgress(0);
            label.setText(labelText);
            label.setTextColor(accent);
        };
        Runnable start = () -> {
            if (complete[0] || startedAt[0] != 0L) return;
            startedAt[0] = android.os.SystemClock.uptimeMillis();
            label.setText("Keep holding…");
            tick[0].run();
        };
        tick[0] = () -> {
            if (startedAt[0] == 0L || complete[0]) return;
            int value = (int) Math.min(1000, (android.os.SystemClock.uptimeMillis() - startedAt[0]) * 1000L / holdMs);
            progress.setProgress(value);
            if (value >= 1000) {
                complete[0] = true;
                startedAt[0] = 0L;
                label.setText("Confirmed");
                label.setTextColor(Color.WHITE);
                control.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                confirmed.run();
            } else handler.postDelayed(tick[0], 16L);
        };
        control.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) { start.run(); return true; }
            if (event.getActionMasked() == android.view.MotionEvent.ACTION_MOVE) {
                if (event.getX() < 0 || event.getX() > view.getWidth() || event.getY() < 0 || event.getY() > view.getHeight()) cancel.run();
                return true;
            }
            if (event.getActionMasked() == android.view.MotionEvent.ACTION_UP
                    || event.getActionMasked() == android.view.MotionEvent.ACTION_CANCEL) { cancel.run(); return true; }
            return true;
        });
        control.setOnKeyListener((view, keyCode, event) -> {
            if (keyCode != android.view.KeyEvent.KEYCODE_SPACE && keyCode != android.view.KeyEvent.KEYCODE_ENTER) return false;
            if (event.getAction() == android.view.KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) start.run();
            else if (event.getAction() == android.view.KeyEvent.ACTION_UP) cancel.run();
            return true;
        });
        control.setOnClickListener(view -> {
            if (complete[0]) return;
            complete[0] = true;
            handler.removeCallbacks(tick[0]);
            confirmed.run();
        });
        return control;
    }

    private void confirmClearSavedData() {
        int dialogTheme = cloudDialogTheme();
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(20), dp(20), dp(16));
        content.setBackground(background(SURFACE, 0, 20));

        content.addView(text("ACCOUNT & DATA", 11, MUTED, Typeface.BOLD));
        TextView title = text("Disconnect MyCamu?", 22, INK, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(6);
        content.addView(title, titleParams);
        TextView description = text("Your MyCamu session and Rollcall data saved on this phone will be removed. You can connect again later.",
                14, MUTED, Typeface.NORMAL);
        description.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(-1, -2);
        descriptionParams.topMargin = dp(8);
        content.addView(description, descriptionParams);

        LinearLayout removedData = new LinearLayout(this);
        removedData.setOrientation(LinearLayout.VERTICAL);
        removedData.setPadding(dp(12), dp(11), dp(12), dp(11));
        removedData.setBackground(background(SECONDARY, BORDER, 14));
        LinearLayout.LayoutParams removedDataParams = new LinearLayout.LayoutParams(-1, -2);
        removedDataParams.topMargin = dp(16);
        content.addView(removedData, removedDataParams);
        removedData.addView(text("Removed from this device", 13, INK, Typeface.BOLD));
        TextView removedDataDetails = text("Attendance, timetable, class history, profile details, and saved sign-in.",
                12, MUTED, Typeface.NORMAL);
        removedDataDetails.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams removedDataDetailsParams = new LinearLayout.LayoutParams(-1, -2);
        removedDataDetailsParams.topMargin = dp(4);
        removedData.addView(removedDataDetails, removedDataDetailsParams);

        Button keep = onboardingButton("Keep account", HERO, HERO_TEXT, v -> { });
        LinearLayout.LayoutParams keepParams = new LinearLayout.LayoutParams(-1, dp(52));
        keepParams.topMargin = dp(18);
        content.addView(keep, keepParams);
        final AlertDialog[] dialogRef = new AlertDialog[1];
        View disconnect = holdToConfirm("Hold to disconnect and clear data", RED, () -> {
            if (dialogRef[0] != null) dialogRef[0].dismiss();
            clearSavedData();
        });
        LinearLayout.LayoutParams disconnectParams = new LinearLayout.LayoutParams(-1, dp(52));
        disconnectParams.topMargin = dp(8);
        content.addView(disconnect, disconnectParams);

        AlertDialog dialog = new AlertDialog.Builder(this, dialogTheme).setView(content).create();
        dialogRef[0] = dialog;
        keep.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(background(SURFACE, BORDER, 20));
            dialog.getWindow().setLayout(getResources().getDisplayMetrics().widthPixels - dp(32),
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void clearSavedData() {
        WorkManager.getInstance(getApplicationContext()).cancelUniqueWork("mycamu-attendance-sync");
        ClassReminderScheduler.setEnabled(this, false);
        LiveSnapshotStore.clear(this);
        getSharedPreferences("mycamu_sync", MODE_PRIVATE).edit().clear().apply();
        getSharedPreferences("attendance_notice_state", MODE_PRIVATE).edit().clear().apply();
        getPreferences(MODE_PRIVATE).edit().remove(CONNECTED_KEY).remove("student_name").remove("institute_name")
                .remove("roll_number").remove("roll_number_label").remove("last_academic_sync_attempt")
                .remove("last_class_log_sync_attempt").remove(MYCAMU_CONSENT_KEY).apply();
        connectedOnce = false;
        applyTheme();
        sessionExpired = false;
        initialSyncAttempted = false;
        syncInProgress = false;
        handler.removeCallbacks(pollResult);
        hasClassAttendanceSync = false;
        latestClassAttendanceId = latestClassAttendanceName = latestClassAttendanceStatus = "";
        studentName = instituteName = rollNumber = "";
        rollNumberLabel = "Roll No.";
        syncErrorMessage = "";
        showAppScreen(onboardingPage);
        android.webkit.CookieManager.getInstance().removeAllCookies(null);
        android.webkit.CookieManager.getInstance().flush();
        android.webkit.WebStorage.getInstance().deleteAllData();
        if (webView != null) { webView.stopLoading(); webView.loadUrl(HOME); }
    }

    private TextView linkText(String value) {
        TextView link = text(value, 15, GREEN, Typeface.BOLD);
        link.setPaintFlags(link.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        link.setFocusable(true);
        return link;
    }

    private void openUrl(String url) {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    private void showAbout() {
        // The Torch HTML screen is the sole appearance for connected-app settings.
        if (connectedOnce && dashboardView != null) {
            showDashboard();
            dashboardView.openSettings();
            return;
        }
        if (aboutPage != null && aboutPage.getParent() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) aboutPage.getParent();
            int index = parent.indexOfChild(aboutPage);
            parent.removeView(aboutPage);
            aboutPage = buildAboutPage();
            aboutPage.setVisibility(View.GONE);
            parent.addView(aboutPage, index, new FrameLayout.LayoutParams(-1, -1));
        }
        showAppScreen(aboutPage);
    }

    private void routeWidgetLaunch(Intent intent) {
        String destination = intent == null ? null : intent.getStringExtra(EXTRA_WIDGET_DESTINATION);
        String extra = EXTRA_WIDGET_DESTINATION;
        if (destination == null) { destination = intent == null ? null : intent.getStringExtra("notification_destination"); extra = "notification_destination"; }
        if (destination == null) return;
        intent.removeExtra(extra);
        if (!connectedOnce) return;
        if ("settings".equals(destination)) {
            showAbout();
            return;
        }
        showDashboard();
        if (dashboardView != null) dashboardView.openNotificationDestination(destination);
    }

    private void updateReminderSettings() {
        if (reminderSwitch == null || reminderPermissionHint == null || notificationSettingsButton == null) return;
        reminderSwitch.setOnCheckedChangeListener(null);
        reminderSwitch.setChecked(classRemindersEnabled);
        reminderSwitch.setOnCheckedChangeListener(this::onReminderSwitchChanged);
        boolean allowed = ClassReminderScheduler.canNotify(this);
        reminderPermissionHint.setText(allowed
                ? classRemindersEnabled
                    ? "One reminder, normally 10 minutes before class, using your saved timetable."
                    : "Reminders are off. Turn them on for a 10-minute heads-up before class."
                : classRemindersEnabled
                    ? "Rollcall is set to remind you, but Android blocks notifications. Allow them below to receive reminders."
                    : "Reminders are off and Android blocks notifications. Turn them on and allow notifications to get class reminders.");
        if (reminderBellIcon != null) {
            reminderBellIcon.setColorFilter(classRemindersEnabled ? HERO : MUTED);
            reminderBellIcon.setContentDescription(classRemindersEnabled
                    ? "Class reminder notifications enabled" : "Class reminder notifications disabled");
        }
        notificationSettingsButton.setVisibility(View.VISIBLE);
    }

    private void animateReminderBell() {
        if (reminderBellIcon == null) return;
        reminderBellIcon.post(() -> {
            reminderBellIcon.setPivotX(reminderBellIcon.getWidth() / 2f);
            reminderBellIcon.setPivotY(dp(3));
            android.animation.ObjectAnimator ring = android.animation.ObjectAnimator.ofFloat(
                    reminderBellIcon, View.ROTATION, 0f, 16f, -13f, 8f, -4f, 0f);
            ring.setDuration(680L);
            ring.start();
        });
    }

    private void onReminderSwitchChanged(android.widget.CompoundButton button, boolean checked) {
        if (checked) {
            if (!classRemindersEnabled) setClassRemindersEnabled(true);
            return;
        }
        if (!classRemindersEnabled) return;
        button.setOnCheckedChangeListener(null);
        button.setChecked(true);
        button.setOnCheckedChangeListener(this::onReminderSwitchChanged);
        int dialogTheme = cloudDialogTheme();
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(20), dp(20), dp(16));
        content.setBackground(background(SURFACE, 0, 20));

        content.addView(text("CLASS REMINDERS", 11, MUTED, Typeface.BOLD));
        TextView title = text("Keep them on?", 22, INK, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(6);
        content.addView(title, titleParams);
        TextView description = text("Get a 10-minute heads-up before each class. We recommend keeping reminders on so you have time to get ready.",
                14, MUTED, Typeface.NORMAL);
        description.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(-1, -2);
        descriptionParams.topMargin = dp(8);
        content.addView(description, descriptionParams);

        LinearLayout benefit = new LinearLayout(this);
        benefit.setGravity(android.view.Gravity.CENTER_VERTICAL);
        benefit.setPadding(dp(12), dp(10), dp(12), dp(10));
        benefit.setBackground(background(SECONDARY, BORDER, 14));
        LinearLayout.LayoutParams benefitParams = new LinearLayout.LayoutParams(-1, -2);
        benefitParams.topMargin = dp(16);
        content.addView(benefit, benefitParams);
        TextView minutes = text("10m", 15, HERO_TEXT, Typeface.BOLD);
        minutes.setGravity(android.view.Gravity.CENTER);
        minutes.setBackground(background(HERO, 0, 12));
        benefit.addView(minutes, new LinearLayout.LayoutParams(dp(52), dp(44)));
        LinearLayout benefitCopy = new LinearLayout(this);
        benefitCopy.setOrientation(LinearLayout.VERTICAL);
        benefitCopy.setPadding(dp(12), 0, 0, 0);
        benefitCopy.addView(text("Before each class", 14, INK, Typeface.BOLD));
        TextView benefitHint = text("A quiet reminder to help you get ready.", 11, MUTED, Typeface.NORMAL);
        LinearLayout.LayoutParams benefitHintParams = new LinearLayout.LayoutParams(-1, -2);
        benefitHintParams.topMargin = dp(3);
        benefitCopy.addView(benefitHint, benefitHintParams);
        benefit.addView(benefitCopy, new LinearLayout.LayoutParams(0, -2, 1));

        Button keep = onboardingButton("Keep reminders on", HERO, HERO_TEXT, v -> { });
        LinearLayout.LayoutParams keepParams = new LinearLayout.LayoutParams(-1, dp(52));
        keepParams.topMargin = dp(18);
        content.addView(keep, keepParams);
        final AlertDialog[] reminderDialogRef = new AlertDialog[1];
        View turnOff = holdToConfirm("Hold to turn off reminders", RED, () -> {
            if (reminderDialogRef[0] != null) reminderDialogRef[0].dismiss();
            setClassRemindersEnabled(false);
        });
        LinearLayout.LayoutParams turnOffParams = new LinearLayout.LayoutParams(-1, dp(52));
        turnOffParams.topMargin = dp(8);
        content.addView(turnOff, turnOffParams);

        AlertDialog dialog = new AlertDialog.Builder(this, dialogTheme).setView(content).create();
        reminderDialogRef[0] = dialog;
        keep.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(background(SURFACE, BORDER, 20));
            dialog.getWindow().setLayout(getResources().getDisplayMetrics().widthPixels - dp(32),
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private LinearLayout buildWebPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(CANVAS);

        TextView kicker = text("MYCAMU / AUTHENTICATION", 10, darkTheme ? HERO : 0xFF365000, Typeface.BOLD);
        kicker.setLetterSpacing(.08f);
        kicker.setPadding(dp(23), dp(12), dp(23), dp(12));
        page.addView(kicker);
        TextView title = text("CONNECT YOUR\nACCOUNT.", 32, INK, Typeface.BOLD);
        title.setTypeface(appTypeface(), Typeface.BOLD);
        title.setLetterSpacing(-.055f);
        title.setPadding(dp(23), 0, dp(23), dp(10));
        page.addView(title);
        webStatus = text(connectedOnce ? "Attendance refreshes every minute while Rollcall is open."
                : "Sign in below. After sign-in, review what Rollcall can read before the first sync.", 13, MUTED, Typeface.NORMAL);
        webStatus.setPadding(dp(23), dp(4), dp(23), dp(15));
        page.addView(webStatus);

        LinearLayout actions = new LinearLayout(this);
        webSyncButton = button(connectedOnce ? "SYNC ATTENDANCE  ↗" : "CHECK SIGN-IN  ↗");
        webSyncButton.setAllCaps(false);
        webSyncButton.setTextColor(HERO_TEXT);
        webSyncButton.setBackgroundTintList(ColorStateList.valueOf(HERO));
        webSyncButton.setOnClickListener(v -> syncAttendance(true));
        actions.addView(webSyncButton, new LinearLayout.LayoutParams(0, dp(48), 1));
        dashboardWebButton = button("Dashboard");
        dashboardWebButton.setVisibility(connectedOnce ? View.VISIBLE : View.GONE);
        dashboardWebButton.setBackgroundTintList(ColorStateList.valueOf(SECONDARY));
        dashboardWebButton.setTextColor(INK);
        dashboardWebButton.setOnClickListener(v -> showDashboard());
        actions.addView(dashboardWebButton, new LinearLayout.LayoutParams(0, dp(48), 1));
        actions.setPadding(dp(23), 0, dp(23), dp(16));
        page.addView(actions);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.WHITE);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(false);
        webView.getSettings().setAllowContentAccess(false);
        webView.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (sessionExpired) webStatus.setText("Your MyCamu sign-in has expired. Sign in below; your saved attendance stays on this device.");
                else if (!syncInProgress) webStatus.setText(!connectedOnce
                        ? "Sign in with MyCamu below. After sign-in, review Rollcall's access request."
                        : isCamuPortal(url) ? "Attendance refreshes every minute while Rollcall is open."
                        : "MyCamu loaded. Open Attendance to refresh your courses.");
            }

            @Override public void onReceivedSslError(WebView view, SslErrorHandler error, android.net.http.SslError detail) {
                error.cancel();
                syncErrorMessage = "MyCamu's secure connection could not be verified.";
                showSyncMessage(syncErrorMessage);
            }

            @Override public void onReceivedError(WebView view, WebResourceRequest request, android.webkit.WebResourceError error) {
                if (request.isForMainFrame()) {
                    syncErrorMessage = "MyCamu could not load. Check your connection and retry.";
                    showSyncMessage(syncErrorMessage);
                }
            }

        });
        page.addView(webView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        return page;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private LinearLayout buildKctPortalPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(CANVAS);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(8), dp(16), dp(4));
        TextView back = text("‹", 32, INK, Typeface.NORMAL);
        back.setGravity(android.view.Gravity.CENTER);
        back.setContentDescription("Back to Marks and academics");
        back.setOnClickListener(v -> showDashboard());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = text("KCT portal", 19, INK, Typeface.BOLD);
        title.setPadding(dp(8), 0, 0, 0);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        page.addView(header);
        kctPortalStatus = text("Sign in on the KCT portal to view your academic record.", 12, MUTED, Typeface.NORMAL);
        kctPortalStatus.setPadding(dp(20), dp(2), dp(20), dp(8));
        page.addView(kctPortalStatus);
        kctWebView = new WebView(this);
        kctWebView.setBackgroundColor(Color.WHITE);
        kctWebView.getSettings().setJavaScriptEnabled(true);
        kctWebView.getSettings().setDomStorageEnabled(true);
        kctWebView.getSettings().setAllowFileAccess(false);
        kctWebView.getSettings().setAllowContentAccess(false);
        kctWebView.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager.getInstance().setAcceptCookie(true);
        kctWebView.setWebChromeClient(new WebChromeClient());
        kctWebView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (!"https".equalsIgnoreCase(uri.getScheme())) {
                    kctPortalStatus.setText("The KCT portal requested an insecure page, so Rollcall blocked it.");
                    return true;
                }
                return false;
            }

            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                kctPortalStatus.setText("Signed in? Rollcall is checking your results and next exam.");
                if ("https".equalsIgnoreCase(Uri.parse(url).getScheme())
                        && "portal.kct.ac.in".equalsIgnoreCase(Uri.parse(url).getHost())) {
                    view.evaluateJavascript(KCT_ACADEMIC_EXTRACTOR, MainActivity.this::readKctAcademicRecords);
                    if (kctLoginReturnPending) view.evaluateJavascript(KCT_AUTH_CHECK, MainActivity.this::onKctLoginChecked);
                }
            }

            @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, android.net.http.SslError error) {
                handler.cancel();
                kctPortalStatus.setText("Rollcall could not verify the KCT portal's secure connection.");
            }

            @Override public void onReceivedError(WebView view, WebResourceRequest request, android.webkit.WebResourceError error) {
                if (request.isForMainFrame()) kctPortalStatus.setText("KCT portal could not load. Check your connection and retry.");
            }
        });
        page.addView(kctWebView, new LinearLayout.LayoutParams(-1, 0, 1));
        return page;
    }

    private void showKctPortal() {
        if (kctWebView == null || kctPortalPage == null) return;
        kctLoginReturnPending = !getPreferences(MODE_PRIVATE).getBoolean("kct_portal_authenticated", false);
        kctSyncStartedAt = System.currentTimeMillis();
        kctWebView.evaluateJavascript("sessionStorage.removeItem('rollcallKctStage');sessionStorage.removeItem('rollcallKctExamUrl');true", null);
        String currentUrl = kctWebView.getUrl();
        if (currentUrl == null || "about:blank".equals(currentUrl)
                || !"portal.kct.ac.in".equalsIgnoreCase(Uri.parse(currentUrl).getHost()))
            kctWebView.loadUrl("https://portal.kct.ac.in/Student_profile");
        showAppScreen(kctPortalPage);
        handler.removeCallbacks(scanKctAcademicPage);
        handler.postDelayed(scanKctAcademicPage, 700L);
    }

    private void onKctLoginChecked(String value) {
        if (!kctLoginReturnPending || !"true".equals(value)) return;
        kctLoginReturnPending = false;
        kctSyncStartedAt = System.currentTimeMillis();
        getPreferences(MODE_PRIVATE).edit().putBoolean("kct_portal_authenticated", true).apply();
        if (kctPortalStatus != null)
            kctPortalStatus.setText("KCT sign-in complete. Rollcall is collecting your results and next exam.");
    }

    private void readKctAcademicRecords(String encoded) {
        if (encoded == null || encoded.equals("null")) return;
        try {
            Object decoded = new org.json.JSONTokener(encoded).nextValue();
            if (decoded instanceof String) decoded = new org.json.JSONTokener((String) decoded).nextValue();
            JSONObject response = decoded instanceof JSONObject ? (JSONObject) decoded : new JSONObject();
            JSONArray incoming = response.optJSONArray("records");
            JSONArray examRows = response.optJSONArray("exams");
            android.content.SharedPreferences prefs = getPreferences(MODE_PRIVATE);
            boolean completeCourseList = response.optBoolean("complete", false)
                    && "Student_complete_course".equals(response.optString("source", ""))
                    && incoming != null && incoming.length() >= 2;
            boolean savedCompleteCourseList = false;
            int uniqueCourseRows = 0;
            int distinctCodes = 0;
            int distinctNames = 0;
            if (completeCourseList) {
                java.util.LinkedHashMap<String, JSONObject> clean = new java.util.LinkedHashMap<>();
                java.util.HashSet<String> codes = new java.util.HashSet<>();
                java.util.HashSet<String> names = new java.util.HashSet<>();
                for (int i = 0; i < incoming.length(); i++) {
                    JSONObject item = incoming.optJSONObject(i);
                    if (item == null) continue;
                    String semester = item.optString("semester", "").trim();
                    String code = item.optString("code", "").trim();
                    String name = item.optString("name", "").trim();
                    String grade = item.optString("grade", "").trim().toUpperCase(Locale.ROOT);
                    double credits = item.optDouble("credits", -1);
                    if (semester.isEmpty() || (code.isEmpty() && name.isEmpty()) || grade.isEmpty()
                            || Double.isNaN(credits) || Double.isInfinite(credits) || credits < 0 || credits > 30) continue;
                    JSONObject safe = new JSONObject();
                    safe.put("semester", semester.substring(0, Math.min(semester.length(), 160)));
                    safe.put("code", code.substring(0, Math.min(code.length(), 32)));
                    safe.put("name", name.substring(0, Math.min(name.length(), 120)));
                    safe.put("grade", grade);
                    safe.put("result", item.optString("result", "").substring(0,
                            Math.min(item.optString("result", "").length(), 24)));
                    safe.put("credits", credits);
                    if (!code.isEmpty()) codes.add(code.toLowerCase(Locale.ROOT));
                    if (!name.isEmpty()) names.add(name.toLowerCase(Locale.ROOT));
                    clean.put(academicRecordKey(safe), safe);
                }
                JSONArray records = new JSONArray();
                for (JSONObject item : clean.values()) records.put(item);
                uniqueCourseRows = records.length();
                distinctCodes = codes.size();
                distinctNames = names.size();
                // A collapsed or repeated portal card must never replace a fuller saved result set.
                if (records.length() >= 2 && records.length() == incoming.length()) {
                    prefs.edit().putString(ACADEMIC_RECORDS_KEY, records.toString())
                            .putLong(ACADEMIC_UPDATED_KEY, System.currentTimeMillis()).apply();
                    CloudAccountClient.enqueueSnapshotSync(this);
                    savedCompleteCourseList = true;
                }
            }
            boolean authenticated = response.optBoolean("authenticated", false);
            boolean examPage = response.optBoolean("examPage", false);
            if (examPage) {
                JSONObject next = examRows == null || examRows.length() == 0 ? null : examRows.optJSONObject(0);
                android.content.SharedPreferences.Editor edit = prefs.edit();
                if (next == null) edit.remove(UPCOMING_EXAM_KEY);
                else {
                    String title = next.optString("title", "").trim();
                    long startsAt = next.optLong("startsAt", 0);
                    boolean hasTime = next.optBoolean("hasTime", false);
                    java.util.Calendar today = java.util.Calendar.getInstance();
                    today.set(java.util.Calendar.HOUR_OF_DAY, 0);
                    today.set(java.util.Calendar.MINUTE, 0);
                    today.set(java.util.Calendar.SECOND, 0);
                    today.set(java.util.Calendar.MILLISECOND, 0);
                    if (title.isEmpty() || (hasTime ? startsAt <= System.currentTimeMillis() : startsAt < today.getTimeInMillis()))
                        edit.remove(UPCOMING_EXAM_KEY);
                    else {
                        JSONObject safe = new JSONObject();
                        String course = next.optString("course", "").trim();
                        safe.put("title", title.substring(0, Math.min(title.length(), 120)));
                        safe.put("course", course.substring(0, Math.min(course.length(), 120)));
                        safe.put("type", next.optString("type", "").trim().substring(0,
                                Math.min(next.optString("type", "").trim().length(), 40)));
                        safe.put("session", next.optString("session", "").trim().toUpperCase(Locale.ROOT));
                        safe.put("code", next.optString("code", "").trim().substring(0,
                                Math.min(next.optString("code", "").trim().length(), 32)));
                        String details = next.optString("details", "").trim();
                        safe.put("details", details.substring(0, Math.min(details.length(), 180)));
                        safe.put("startsAt", startsAt);
                        safe.put("hasTime", hasTime);
                        edit.putString(UPCOMING_EXAM_KEY, safe.toString());
                    }
                }
                edit.putBoolean("kct_portal_authenticated", true).apply();
                CloudAccountClient.enqueueSnapshotSync(this);
            }
            boolean timedOut = authenticated && kctSyncStartedAt > 0
                    && System.currentTimeMillis() - kctSyncStartedAt > 45_000L;
            if (examPage || timedOut) {
                kctSyncStartedAt = 0;
                if (kctPortalStatus != null) {
                    if (savedCompleteCourseList) kctPortalStatus.setText("KCT results and exam details are up to date on this device.");
                    else if (incoming != null && incoming.length() > 0 && uniqueCourseRows > 0)
                        kctPortalStatus.setText("KCT showed " + incoming.length() + " result rows, but only "
                                + uniqueCourseRows + " matched distinct courses (" + distinctCodes + " codes, "
                                + distinctNames + " names). Nothing was saved; reopen Completed Courses to retry.");
                    else kctPortalStatus.setText(examPage
                            ? "Exam schedule checked. The completed-course panel is still open to collect results."
                            : "KCT is open. Navigate to Academics or Exam Scheduler while Rollcall checks the portal.");
                }
            } else if (kctPortalStatus != null) {
                String panel = response.optString("coursePanel", "missing");
                kctPortalStatus.setText("missing".equals(panel)
                        ? "Open KCT Academics → Completed Courses to import semester results."
                        : savedCompleteCourseList
                        ? "Imported " + incoming.length() + " completed-course results. They are available in Marks & academics."
                        : uniqueCourseRows > 0 && incoming != null && uniqueCourseRows < incoming.length()
                        ? "KCT showed " + incoming.length() + " grade rows, but only " + uniqueCourseRows
                        + " distinct course matches (" + distinctCodes + " codes, " + distinctNames + " names). No results were saved."
                        : "visible".equals(panel) && response.optInt("expectedCount") > 0
                        ? "KCT results: read " + (incoming == null ? 0 : incoming.length()) + " of "
                        + response.optInt("expectedCount") + " grade entries. No partial GPA was saved."
                        : "KCT completed courses are not open yet; Rollcall is checking the portal tabs.");
            }
        } catch (Exception ignored) {
            // Keep the last valid on-device academic snapshot when the portal changes its markup.
        }
    }

    private void readKctExamCards(String encoded) {
        if (encoded == null || encoded.equals("null")) return;
        try {
            Object decoded = new org.json.JSONTokener(encoded).nextValue();
            if (decoded instanceof String) decoded = new org.json.JSONTokener((String) decoded).nextValue();
            if (!(decoded instanceof JSONObject)) return;
            JSONObject response = (JSONObject) decoded;
            JSONArray exams = response.optJSONArray("exams");
            if (!response.optBoolean("examPage") || exams == null || exams.length() == 0) return;
            JSONObject next = exams.optJSONObject(0);
            String title = next == null ? "" : next.optString("title").trim();
            if (title.isEmpty() || title.matches("(?i).*(roll number|register number|student id|email|mobile|address).*")) {
                getPreferences(MODE_PRIVATE).edit().remove(UPCOMING_EXAM_KEY).apply();
                return;
            }
            JSONObject saved = new JSONObject();
            saved.put("title", title);
            saved.put("course", next.optString("course", title).trim());
            saved.put("type", next.optString("type", "").trim());
            saved.put("session", next.optString("session", "").trim().toUpperCase(Locale.ROOT));
            saved.put("code", next.optString("code", "").trim());
            saved.put("details", next.optString("details").trim());
            saved.put("startsAt", next.optLong("startsAt"));
            saved.put("hasTime", next.optBoolean("hasTime"));
            getPreferences(MODE_PRIVATE).edit().putString(UPCOMING_EXAM_KEY, saved.toString()).apply();
            CloudAccountClient.enqueueSnapshotSync(this);
            if (kctPortalStatus != null)
                kctPortalStatus.setText("Upcoming exam details are saved on this device.");
        } catch (Exception ignored) {
            // Keep the previous on-device exam if the portal changes its card markup.
        }
    }

    private String academicRecordKey(JSONObject item) {
        return item.optString("semester", "").toLowerCase(Locale.ROOT) + "|"
                + item.optString("code", "").toLowerCase(Locale.ROOT) + "|"
                + item.optString("name", "").toLowerCase(Locale.ROOT);
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setTypeface(appTypeface(), Typeface.BOLD);
        button.setTextColor(BUTTON_TEXT);
        button.setBackgroundTintList(ColorStateList.valueOf(BUTTON));
        return button;
    }

    private LinearLayout.LayoutParams topMargin(int width, int height, int marginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.topMargin = dp(marginDp);
        return params;
    }

    private LinearLayout cardContainer() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(background(SURFACE, BORDER, 16));
        return card;
    }

    private TextView text(String value, float size, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(appTypeface(), style);
        return view;
    }

    private Typeface appTypeface() {
        if (appTypeface == null) {
            try { appTypeface = Typeface.createFromAsset(getAssets(), "fonts/JetBrainsMono-Regular.ttf"); }
            catch (RuntimeException ignored) { appTypeface = Typeface.create("monospace", Typeface.NORMAL); }
        }
        return appTypeface;
    }

    private void render(LiveSnapshotStore.Snapshot snapshot) {
        // WorkManager can publish a snapshot off the UI thread. All rendering and
        // view mutations must be serialized on the activity's main thread.
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            handler.post(() -> render(LiveSnapshotStore.get()));
            return;
        }
        List<Attendance> rows = new ArrayList<>(snapshot.attendance);
        Collections.sort(rows, (a, b) -> {
            int empty = Boolean.compare(a.countsKnown && a.total == 0, b.countsKnown && b.total == 0);
            if (empty != 0) return empty;
            int percent = Double.compare(a.percentage(), b.percentage());
            return percent != 0 ? percent : a.code.compareTo(b.code);
        });
        int under = 0;
        int onTrack = 0, knownAttended = 0, knownTotal = 0;
        for (Attendance row : rows) {
            if (isBelowTarget(row)) under++;
            if (row.countsKnown && row.total > 0) {
                knownAttended += row.attended;
                knownTotal += row.total;
                if (!isBelowTarget(row)) onTrack++;
            }
        }
        DashboardView.State ui = new DashboardView.State();
        ui.dark = darkTheme;
        ui.remindersEnabled = classRemindersEnabled;
        ui.version = BuildConfig.VERSION_NAME;
        ui.name = studentName.isEmpty() ? "MyCamu student" : studentName;
        String[] nameParts = studentName.trim().split("\\s+");
        ui.initials = nameParts.length == 0 || nameParts[0].isEmpty() ? "R"
                : nameParts[0].substring(0, 1).toUpperCase(Locale.ROOT);
        if (nameParts.length > 1 && !nameParts[nameParts.length - 1].isEmpty())
            ui.initials += nameParts[nameParts.length - 1].substring(0, 1).toUpperCase(Locale.ROOT);
        ui.target = (int) Math.round(attendanceTarget);
        ui.attendanceLoaded = snapshot.attendanceAt > 0;
        ui.overallPercent = knownTotal == 0 ? -1 : (int) Math.round(100.0 * knownAttended / knownTotal);
        ui.heroMessage = knownTotal == 0 ? "— / —" : knownAttended + " / " + knownTotal;
        ui.overallCaption = "classes attended";
        ui.targetHint = knownTotal == 0 ? "Sync MyCamu to see your attendance"
                : ui.overallPercent < attendanceTarget
                ? Math.round(attendanceTarget - ui.overallPercent) + " pts below your " + ui.target + "% goal"
                : (ui.overallPercent - ui.target) + " pts above your " + ui.target + "% goal";
        ui.courses = rows.size();
        ui.below = under;
        ui.onTrack = onTrack;
        ui.query = searchQuery;
        ui.belowOnly = showBelowOnly;
        Map<String, String> courseCodeIndex = courseCodeIndex(rows);
        Map<String, ArrayList<LiveSnapshotStore.ClassMark>> marksByCourse = new HashMap<>();
        for (LiveSnapshotStore.ClassMark mark : snapshot.classMarks) {
            String code = courseCodeForSlot(mark.code, mark.name, courseCodeIndex);
            if (!code.isEmpty()) marksByCourse.computeIfAbsent(normalizeCourseCode(code), k -> new ArrayList<>()).add(mark);
        }
        Set<String> currentSemesterClassKeys = currentSemesterClassKeys(rows, marksByCourse, snapshot.timetable, courseCodeIndex);
        ui.currentSemesterClassKeys = currentSemesterClassKeys;
        Set<String> currentSemesterOdClasses = new HashSet<>();
        Set<String> approvedOdClasses = new HashSet<>();
        for (LiveSnapshotStore.LeaveRecord leave : snapshot.leaves) {
            if (!Attendance.isApprovedOd(leave.type, leave.status)) continue;
            JSONArray details;
            try { details = new JSONArray(leave.periodDetails); }
            catch (JSONException ignored) { continue; }
            for (int i = 0; i < details.length(); i++) {
                JSONObject slot = details.optJSONObject(i);
                if (slot == null) continue;
                long startsAt = slot.optLong("startsAt", 0);
                String code = courseCodeForSlot(slot.optString("code", ""), slot.optString("name", ""), courseCodeIndex);
                if (startsAt > 0 && !code.isEmpty()) {
                    String key = odClassKey(code, startsAt);
                    approvedOdClasses.add(key);
                    currentSemesterOdClasses.add(key);
                }
            }
        }
        for (LiveSnapshotStore.ClassMark mark : snapshot.classMarks) {
            String code = courseCodeForSlot(mark.code, mark.name, courseCodeIndex);
            String key = odClassKey(code, mark.startsAt);
            if (Attendance.isApprovedOd(mark.leaveType, mark.leaveStatus) && mark.startsAt > 0 && !code.isEmpty()) {
                approvedOdClasses.add(key);
            }
        }
        ui.approvedOdClasses = approvedOdClasses;
        ui.currentSemesterOdClasses = currentSemesterOdClasses;
        List<DashboardView.Course> courseRows = new ArrayList<>();
        for (Attendance row : rows) {
            DashboardView.Course item = new DashboardView.Course();
            item.code = row.code;
            item.name = row.name;
            item.percent = (float) row.percentage();
            item.attended = row.attended;
            item.total = row.total;
            item.countsKnown = row.countsKnown;
            int logged = 0, present = 0, approvedAbsent = 0;
            Set<String> courseOdClasses = new HashSet<>();
            Set<String> approvedAbsentKeys = new HashSet<>();
            for (String key : currentSemesterOdClasses)
                if (key.startsWith(normalizeCourseCode(row.code) + ":"))
                    courseOdClasses.add(key);
            boolean completeMarks = true;
            List<LiveSnapshotStore.ClassMark> courseMarks = marksByCourse.get(normalizeCourseCode(row.code));
            if (courseMarks == null) courseMarks = Collections.emptyList();
            for (LiveSnapshotStore.ClassMark mark : courseMarks) {
                String markCode = row.code;
                String key = odClassKey(markCode, mark.startsAt);
                if (!currentSemesterClassKeys.contains(key)) continue;
                logged++;
                if ("present".equals(mark.status)) present++;
                else if (!"absent".equals(mark.status)) completeMarks = false;
                if (mark.startsAt > 0) {
                    if ("absent".equals(mark.status) && courseOdClasses.contains(key)
                            && approvedAbsentKeys.add(key)) approvedAbsent++;
                }
            }
            item.odCount = courseOdClasses.size();
            item.odCredit = approvedAbsent;
            item.odReconciled = row.countsKnown && (logged > 0 || row.total == 0 && courseOdClasses.isEmpty())
                    && completeMarks && logged == row.total && present == row.attended;
            item.percentLabel = String.format(Locale.getDefault(), "%.0f%%", row.percentage());
            item.status = row.countsKnown && row.total == 0 ? "No classes yet"
                    : isBelowTarget(row) ? "Below target"
                    : row.percentage() == attendanceTarget ? "At target" : "Above target";
            item.counts = row.countsKnown ? row.attended + " attended / " + row.total + " total"
                    : "Attendance counts unavailable";
            item.guidance = row.guidance(attendanceTarget);
            if (row.countsKnown && row.total > 0) {
                if (row.percentage() < attendanceTarget) {
                    int needed = row.periodsToRecover(attendanceTarget);
                    item.targetCount = needed == Integer.MAX_VALUE ? "Target unreachable"
                            : "Attend " + needed + (needed == 1 ? " class" : " classes");
                } else {
                    int safeAbsences = row.safeAbsences(attendanceTarget);
                    item.targetCount = safeAbsences == 0 ? "Attend next class" : "Can miss " + safeAbsences;
                }
            }
            item.color = courseColor(row);
            item.below = isBelowTarget(row);
            item.expanded = expandedCourses.contains(row.code);
            courseRows.add(item);
        }
        ui.rows = courseRows;
        ui.timetableLoaded = snapshot.timetableAt > 0;
        ui.leavesLoaded = snapshot.leavesAt > 0;
        loadAcademicRecords(ui);
        ui.timetableUpdatedAt = snapshot.timetableAt;
        ui.classMarksUpdatedAt = snapshot.classMarksAt;
        ui.latestClassName = latestClassAttendanceId.isEmpty() ? "" : latestClassAttendanceName;
        ui.latestClassStatus = latestClassAttendanceId.isEmpty() ? "" : latestClassAttendanceStatus;
        if (ui.latestClassName.isEmpty()) {
            LiveSnapshotStore.ClassMark latestMark = null;
            long now = System.currentTimeMillis();
            for (LiveSnapshotStore.ClassMark mark : snapshot.classMarks) {
                if (mark.startsAt <= now && (latestMark == null || mark.startsAt > latestMark.startsAt)) latestMark = mark;
            }
            if (latestMark != null) {
                ui.latestClassName = latestMark.name;
                ui.latestClassStatus = latestMark.status;
            }
        }
        SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE, d MMM", Locale.getDefault());
        SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
        for (LiveSnapshotStore.ClassSession session : snapshot.timetable) {
            DashboardView.ScheduleItem item = new DashboardView.ScheduleItem();
            item.date = dayFormat.format(new Date(session.startsAt));
            item.startsAt = session.startsAt;
            item.endsAt = session.endsAt;
            item.time = timeFormat.format(new Date(session.startsAt));
            if (session.endsAt > session.startsAt && !session.allDay)
                item.time += " – " + timeFormat.format(new Date(session.endsAt));
            item.name = session.name;
            item.code = session.code;
            item.allDay = session.allDay;
            item.cancelled = session.cancelled;
            if (!isPlaceholder(session.location)) item.details = session.location.trim();
            if (!isPlaceholder(session.staff)) item.details += (item.details.isEmpty() ? "" : "  ·  ") + session.staff.trim();
            ui.timetable.add(item);
        }
        SimpleDateFormat leaveDate = new SimpleDateFormat("d MMM yyyy", Locale.getDefault());
        for (LiveSnapshotStore.LeaveRecord leave : snapshot.leaves) {
            DashboardView.LeaveItem item = new DashboardView.LeaveItem();
            item.id = leave.id;
            item.type = leave.type.isEmpty() ? "Leave" : leave.type;
            item.status = leave.status;
            item.startsAt = leave.startsAt;
            item.endsAt = leave.endsAt;
            item.periods = leave.periods;
            item.periodNumbers = leave.periodNumbers;
            item.periodDetails = leave.periodDetails;
            item.currentSemester = leave.currentSemester;
            item.semesterKnown = leave.semesterKnown;
            item.date = leaveDate.format(new Date(leave.startsAt));
            if (leave.endsAt > leave.startsAt) item.date += " – " + leaveDate.format(new Date(leave.endsAt));
            item.details = leave.periods > 0 ? leave.periods + (leave.periods == 1 ? " period" : " periods") : "";
            String status = leave.status.toLowerCase(Locale.ROOT);
            item.color = status.contains("approv") ? GREEN : status.contains("reject") ? RED
                    : status.contains("submit") || status.contains("pending") ? AMBER : MUTED;
            ui.leaves.add(item);
        }
        SimpleDateFormat historyDay = new SimpleDateFormat("EEEE, d MMM yyyy", Locale.getDefault());
        for (LiveSnapshotStore.ClassMark mark : snapshot.classMarks) {
            DashboardView.ClassMarkItem item = new DashboardView.ClassMarkItem();
            item.name = mark.name.isEmpty() ? mark.code : mark.name;
            item.code = mark.code;
            item.day = historyDay.format(new Date(mark.startsAt));
            item.time = timeFormat.format(new Date(mark.startsAt));
            item.startsAt = mark.startsAt;
            item.status = mark.status;
            item.leaveType = mark.leaveType;
            item.leaveStatus = mark.leaveStatus;
            item.color = "present".equals(mark.status) ? GREEN : "absent".equals(mark.status) ? RED
                    : "not_taken".equals(mark.status) ? AMBER : MUTED;
            ui.classMarks.add(item);
        }
        ui.syncing = syncInProgress;
        ui.sessionExpired = sessionExpired;
        ui.syncError = !snapshot.syncError.isEmpty();
        ui.syncSucceeded = snapshot.attendanceAt > 0 && snapshot.syncError.isEmpty() && !syncInProgress;
        ui.status = sessionExpired ? (snapshot.attendanceAt > 0
                    ? "Sign-in expired · saved attendance " + relativeAge(snapshot.attendanceAt)
                    : "MyCamu sign-in expired · tap refresh to reconnect")
                : syncInProgress ? "Syncing MyCamu…"
                : ui.syncError ? (snapshot.attendanceAt > 0
                    ? snapshot.syncError + " · saved attendance " + relativeAge(snapshot.attendanceAt)
                    : "Sync failed · tap refresh to retry")
                : snapshot.attendanceAt == 0 ? "Not synced · tap refresh to connect MyCamu"
                : "Attendance updated " + relativeAge(snapshot.attendanceAt);
        dashboardView.update(ui);
        RollcallTodayWidget.refresh(this);
    }

    private String relativeAge(long timestamp) {
        long minutes = Math.max(0, (System.currentTimeMillis() - timestamp) / 60_000L);
        if (minutes == 0) return "just now";
        if (minutes < 60) return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
        long hours = minutes / 60;
        if (hours < 24) return hours + (hours == 1 ? " hour ago" : " hours ago");
        long days = hours / 24;
        return days + (days == 1 ? " day ago" : " days ago");
    }

    private void loadAcademicRecords(DashboardView.State ui) {
        android.content.SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        ui.academicUpdatedAt = prefs.getLong(ACADEMIC_UPDATED_KEY, 0);
        ui.academicConnected = prefs.getBoolean("kct_portal_authenticated", false);
        ui.academicStatus = ui.academicUpdatedAt > 0 ? "Saved on this device. KCT remains the source of truth." : "";
        try {
            JSONObject exam = new JSONObject(prefs.getString(UPCOMING_EXAM_KEY, "{}"));
            String savedTitle = exam.optString("title", "").trim();
            ui.upcomingExamTitle = exam.optString("course", savedTitle).trim();
            ui.upcomingExamType = exam.optString("type", "").trim();
            ui.upcomingExamSession = exam.optString("session", "").trim().toUpperCase(Locale.ROOT);
            ui.upcomingExamCode = exam.optString("code", "").trim();
            if (ui.upcomingExamType.isEmpty() && savedTitle.contains(":")) {
                int separator = savedTitle.indexOf(':');
                ui.upcomingExamType = savedTitle.substring(0, separator).trim();
                ui.upcomingExamTitle = savedTitle.substring(separator + 1).trim();
            }
            if (ui.upcomingExamSession.isEmpty()) {
                java.util.regex.Matcher session = java.util.regex.Pattern.compile("\\b(AN|FN)\\b", java.util.regex.Pattern.CASE_INSENSITIVE)
                        .matcher(exam.optString("details", ""));
                if (session.find()) ui.upcomingExamSession = session.group(1).toUpperCase(Locale.ROOT);
            }
            String savedDetails = exam.optString("details", "").trim();
            if (ui.upcomingExamCode.isEmpty()) {
                for (String part : savedDetails.split(" · ")) {
                    if (part.matches("(?i)(?=.*[a-z])(?=.*\\d)[a-z0-9/-]{4,24}")) {
                        ui.upcomingExamCode = part;
                        break;
                    }
                }
            }
            java.util.ArrayList<String> visibleDetails = new java.util.ArrayList<>();
            for (String part : savedDetails.split(" · "))
                if (!part.equalsIgnoreCase(ui.upcomingExamCode) && !part.matches("(?i)\\s*(AN|FN)\\s*"))
                    visibleDetails.add(part);
            ui.upcomingExamDetails = android.text.TextUtils.join(" · ", visibleDetails);
            ui.upcomingExamAt = exam.optLong("startsAt", 0);
            ui.upcomingExamHasTime = exam.optBoolean("hasTime", false);
        } catch (JSONException ignored) {
            ui.upcomingExamTitle = "";
            ui.upcomingExamType = "";
            ui.upcomingExamSession = "";
            ui.upcomingExamCode = "";
            ui.upcomingExamDetails = "";
            ui.upcomingExamAt = 0;
            ui.upcomingExamHasTime = false;
        }
        try {
            JSONArray records = new JSONArray(prefs.getString(ACADEMIC_RECORDS_KEY, "[]"));
            for (int i = 0; i < records.length(); i++) {
                JSONObject raw = records.optJSONObject(i);
                if (raw == null) continue;
                DashboardView.AcademicCourse course = new DashboardView.AcademicCourse();
                course.semester = raw.optString("semester", "").trim();
                course.code = raw.optString("code", "").trim();
                course.name = raw.optString("name", "").trim();
                course.grade = raw.optString("grade", "").trim().toUpperCase(Locale.ROOT);
                course.result = raw.optString("result", "").trim();
                course.credits = raw.optDouble("credits", -1);
                if (course.semester.isEmpty() || (course.code.isEmpty() && course.name.isEmpty())
                        || course.grade.isEmpty() || Double.isNaN(course.credits) || Double.isInfinite(course.credits)
                        || course.credits < 0 || course.credits > 30) continue;
                ui.academicCourses.add(course);
            }
        } catch (JSONException ignored) {
            ui.academicStatus = "Saved academic data could not be read. Open KCT to refresh it.";
        }
    }

    private static String normalizeCourseCode(String code) {
        return code == null ? "" : code.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static Map<String, String> courseCodeIndex(List<Attendance> rows) {
        Map<String, String> index = new HashMap<>();
        for (Attendance row : rows) {
            index.put(normalizeCourseCode(row.code), row.code);
            index.putIfAbsent(normalizeCourseCode(row.name), row.code);
        }
        return index;
    }

    private static String courseCodeForSlot(String code, String name, Map<String, String> index) {
        String normalizedCode = normalizeCourseCode(code);
        String byCode = index.get(normalizedCode);
        if (byCode != null) return byCode;
        String byName = index.get(normalizeCourseCode(name));
        return byName == null ? (code == null ? "" : code) : byName;
    }

    private static Set<String> currentSemesterClassKeys(List<Attendance> courses,
            Map<String, ArrayList<LiveSnapshotStore.ClassMark>> marksByCourse,
            List<LiveSnapshotStore.ClassSession> timetable, Map<String, String> courseCodeIndex) {
        Set<String> keys = new HashSet<>();
        for (Attendance course : courses) {
            if (!course.countsKnown || course.total <= 0) continue;
            ArrayList<LiveSnapshotStore.ClassMark> source = marksByCourse.get(normalizeCourseCode(course.code));
            if (source == null) continue;
            ArrayList<LiveSnapshotStore.ClassMark> matching = new ArrayList<>();
            for (LiveSnapshotStore.ClassMark mark : source) {
                if (!"present".equals(mark.status) && !"absent".equals(mark.status)) continue;
                if (mark.startsAt > 0) matching.add(mark);
            }
            Collections.sort(matching, (a, b) -> Long.compare(b.startsAt, a.startsAt));
            for (int i = 0; i < Math.min(course.total, matching.size()); i++)
                keys.add(odClassKey(course.code, matching.get(i).startsAt));
        }
        for (LiveSnapshotStore.ClassSession session : timetable) {
            if (session.allDay || session.cancelled || session.startsAt <= 0) continue;
            String code = courseCodeForSlot(session.code, session.name, courseCodeIndex);
            for (Attendance course : courses)
                if (normalizeCourseCode(course.code).equals(normalizeCourseCode(code))) {
                    keys.add(odClassKey(course.code, session.startsAt));
                    break;
                }
        }
        return keys;
    }

    private static String odClassKey(String code, long startsAt) {
        return normalizeCourseCode(code) + ":" + (startsAt / 60_000L);
    }

    private static boolean isPlaceholder(String value) {
        return value == null || value.trim().isEmpty() || value.trim().matches("[-–—]+");
    }

    private int courseColor(Attendance row) {
        if (row.countsKnown && row.total == 0) return MUTED;
        if (row.percentage() < attendanceTarget) return RED;
        if (row.percentage() < Math.min(100, attendanceTarget + 10)) return AMBER;
        return GREEN;
    }

    private boolean isBelowTarget(Attendance row) {
        return !(row.countsKnown && row.total == 0) && row.percentage() < attendanceTarget;
    }

    private GradientDrawable background(int color, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private void showMyCamu() {
        showAppScreen(webPage);
        if (sessionExpired) {
            reconnectAttempted = false;
            if (webView != null) webView.loadUrl(HOME);
        }
        if (webStatus != null && sessionExpired)
            webStatus.setText("Your MyCamu sign-in has expired. Sign in below; your saved attendance stays on this device.");
        if (webSyncButton != null) webSyncButton.setText("Check sign-in");
        handler.removeCallbacks(routeMonitor);
        handler.post(routeMonitor);
    }

    private void showDashboard() {
        if (!connectedOnce) {
            showSyncMessage("Sign in to MyCamu to continue to your dashboard.");
            return;
        }
        showAppScreen(dashboard);
        render(LiveSnapshotStore.get());
    }

    private void syncAttendance() { syncAttendance(false); }

    private void syncAttendance(boolean forceAcademic) {
        if (webView == null || !isCamuPortal(webView.getUrl())) {
            syncErrorMessage = "MyCamu is still loading. Try Sync again in a moment.";
            showSyncMessage(syncErrorMessage);
            return;
        }
        if (syncInProgress) return;
        if (!getPreferences(MODE_PRIVATE).getBoolean(MYCAMU_CONSENT_KEY, false)) {
            webView.evaluateJavascript("!!localStorage.getItem('ngStorage-progressionData')", value -> {
                if ("true".equals(value)) {
                    initialSyncAttempted = true;
                    showMyCamuConsent();
                } else {
                    initialSyncAttempted = false;
                    showSyncMessage("Sign in to MyCamu first. Rollcall will ask you to review access after sign-in.");
                }
            });
            return;
        }
        if (!connectedOnce) initialSyncAttempted = true;
        if (connectedOnce && sessionExpired) {
            sessionExpired = false;
            reconnectInProgress = true;
        }
        syncErrorMessage = "";
        syncInProgress = true;
        long now = System.currentTimeMillis();
        lastAutoSyncAt = now;
        boolean includeAcademic = forceAcademic || !hasClassAttendanceSync || now - lastAcademicSyncAttemptAt >= ACADEMIC_SYNC_INTERVAL_MS;
        if (includeAcademic) {
            lastAcademicSyncAttemptAt = now;
            getPreferences(MODE_PRIVATE).edit().putLong("last_academic_sync_attempt", now).apply();
        }
        boolean includeHistory = forceAcademic || now - lastClassLogSyncAt >= CLASS_LOG_SYNC_INTERVAL_MS;
        if (includeHistory) {
            lastClassLogSyncAt = now;
            getPreferences(MODE_PRIVATE).edit().putLong("last_class_log_sync_attempt", now).apply();
        }
        pollCount = 0;
        showSyncMessage("Requesting attendance from MyCamu…");
        if (webSyncButton != null) {
            webSyncButton.setText("Refreshing…");
            webSyncButton.setEnabled(false);
        }
        handler.removeCallbacks(syncTimeout);
        handler.postDelayed(syncTimeout, SYNC_TIMEOUT_MS);
        webView.evaluateJavascript(START_SYNC.replace("__INCLUDE_ACADEMIC__", String.valueOf(includeAcademic))
                        .replace("__INCLUDE_HISTORY__", String.valueOf(includeHistory)),
                value -> handler.postDelayed(pollResult, RESULT_POLL_INTERVAL_MS));
    }

    private void checkMyCamuSession(boolean attendanceRoute) {
        sessionCheckInProgress = true;
        webView.evaluateJavascript("!!localStorage.getItem('ngStorage-progressionData')", value -> {
            sessionCheckInProgress = false;
            if (isFinishing() || webView == null || !isCamuPortal(webView.getUrl()) || syncInProgress) return;
            boolean signedIn = "true".equals(value);
            if (!connectedOnce) {
                if (signedIn && !initialSyncAttempted) {
                    initialSyncAttempted = true;
                    if (getPreferences(MODE_PRIVATE).getBoolean(MYCAMU_CONSENT_KEY, false)) syncAttendance(true);
                    else showMyCamuConsent();
                }
                return;
            }
            if (!signedIn) {
                lastProgramAvailable = false;
                if (!sessionExpired) {
                    sessionExpired = true;
                    syncErrorMessage = "MyCamu sign-in expired. Tap the refresh control to reconnect.";
                    LiveSnapshotStore.syncError(MainActivity.this, syncErrorMessage);
                    if (webStatus != null) webStatus.setText("Your MyCamu sign-in has expired. Sign in below; your saved attendance stays on this device.");
                    render(LiveSnapshotStore.get());
                }
                return;
            }
            if (!lastProgramAvailable) {
                lastProgramAvailable = true;
                reconnectAttempted = false;
            }
            if (sessionExpired) {
                if (webPage == null || webPage.getVisibility() != View.VISIBLE || reconnectAttempted) return;
                if (!getPreferences(MODE_PRIVATE).getBoolean(MYCAMU_CONSENT_KEY, false)) {
                    reconnectAttempted = true;
                    showMyCamuConsent();
                    return;
                }
                reconnectAttempted = true;
                sessionExpired = false;
                reconnectInProgress = true;
                syncErrorMessage = "";
                if (webStatus != null) webStatus.setText("MyCamu sign-in confirmed. Refreshing attendance…");
                syncAttendance(true);
            } else if (attendanceRoute && (forceForegroundSync || System.currentTimeMillis() - lastAutoSyncAt >= AUTO_SYNC_INTERVAL_MS)) {
                forceForegroundSync = false;
                syncAttendance(true);
            }
        });
    }

    private void readResult(String value) {
        if (!syncInProgress) return;
        try {
            JSONObject result = new JSONObject(value);
            String state = result.optString("state", "pending");
            if ("pending".equals(state) || "loading".equals(state)) {
                handler.postDelayed(pollResult, RESULT_POLL_INTERVAL_MS);
                return;
            }
            if ("profile".equals(state) || "auth".equals(state)) {
                if (connectedOnce) {
                    sessionExpired = true;
                    reconnectInProgress = false;
                    finishSync("MyCamu sign-in is needed. Sign in again to refresh attendance.");
                } else finishSync("Sign in to MyCamu, then tap Check sign-in to continue.");
                return;
            }
            if (!"ready".equals(state)) {
                String reason = result.optString("reason", "unexpected response");
                finishSync("MyCamu could not refresh attendance (" + reason + "). Tap Sync to retry.");
                return;
            }
            JSONArray source = result.optJSONArray("rows");
            if (source == null) {
                finishSync("MyCamu did not return a course list. Check your sign-in and try again.");
                return;
            }
            saveProfile(result.optJSONObject("profile"));
            JSONObject accountContext = result.optJSONObject("accountContext");
            if (accountContext != null) getSharedPreferences("mycamu_sync", MODE_PRIVATE).edit()
                    .putString("account_context", accountContext.toString()).apply();
            processClassAttendance(result.optJSONArray("classAttendance"));
            List<Attendance> rows = new ArrayList<>();
            for (int i = 0; i < source.length(); i++) {
                JSONObject row = source.optJSONObject(i);
                if (row == null) continue;
                try {
                    rows.add(new Attendance(row.optString("code"), row.optString("name"),
                            row.optInt("attended", -1), row.optInt("total", -1)));
                } catch (IllegalArgumentException ignored) { }
            }
            JSONObject timetableResult = result.optJSONObject("timetable");
            boolean timetableUpdated = timetableResult != null && "ready".equals(timetableResult.optString("state"));
            if (timetableUpdated) {
                LiveSnapshotStore.timetable(this, parseTimetable(timetableResult.optJSONArray("rows")));
                ClassReminderScheduler.schedule(this, LiveSnapshotStore.get().timetable);
            }
            JSONObject leaveResult = result.optJSONObject("leaves");
            boolean leavesUpdated = leaveResult != null && "ready".equals(leaveResult.optString("state"));
            if (leavesUpdated) LiveSnapshotStore.leaves(this, parseLeaves(leaveResult.optJSONArray("rows")));
            if (result.has("history")) LiveSnapshotStore.classMarks(this,
                    parseClassMarks(result.optJSONArray("history"), result.optJSONArray("classAttendance")));
            boolean firstConnection = !connectedOnce;
            connectedOnce = true;
            lastProgramAvailable = true;
            sessionExpired = false;
            getPreferences(MODE_PRIVATE).edit().putBoolean(CONNECTED_KEY, true).apply();
            scheduleBackgroundSync();
            CloudAccountClient.enqueueSnapshotSync(this);
            if (webSyncButton != null) webSyncButton.setText("Sync attendance");
            if (dashboardWebButton != null) dashboardWebButton.setVisibility(View.VISIBLE);
            if (!rows.isEmpty()) LiveSnapshotStore.attendance(this, rows);
            String message = rows.isEmpty() ? "MyCamu connected. No attendance records are available yet."
                    : "Synced " + rows.size() + " courses. Dashboard updated.";
            List<String> refreshErrors = new ArrayList<>();
            if (timetableResult != null && "error".equals(timetableResult.optString("state")))
                refreshErrors.add("timetable (" + timetableResult.optString("reason", "network error") + ")");
            if (leaveResult != null && "error".equals(leaveResult.optString("state")))
                refreshErrors.add("leave and OD (" + leaveResult.optString("reason", "network error") + ")");
            if (!refreshErrors.isEmpty()) message += " Partial sync: " + android.text.TextUtils.join(", ", refreshErrors)
                    + ". Previously saved data was kept.";
            finishSync(message);
            if (firstConnection) {
                applyTheme();
                ViewGroup parent = (ViewGroup) dashboard.getParent();
                int index = parent.indexOfChild(dashboard);
                parent.removeView(dashboard);
                dashboard = buildDashboardView();
                dashboard.setVisibility(View.GONE);
                parent.addView(dashboard, index, new FrameLayout.LayoutParams(-1, -1));
                if (aboutPage != null) {
                    int aboutIndex = parent.indexOfChild(aboutPage);
                    parent.removeView(aboutPage);
                    aboutPage = buildAboutPage();
                    aboutPage.setVisibility(View.GONE);
                    parent.addView(aboutPage, aboutIndex, new FrameLayout.LayoutParams(-1, -1));
                }
            }
            if (firstConnection || reconnectInProgress) {
                reconnectInProgress = false;
                showDashboard();
            }
            if (firstConnection && !getSharedPreferences("rollcall_cloud_account", MODE_PRIVATE).getBoolean("setup_prompt_shown", false)) {
                getSharedPreferences("rollcall_cloud_account", MODE_PRIVATE).edit().putBoolean("setup_prompt_shown", true).apply();
                handler.postDelayed(() -> new AlertDialog.Builder(this, cloudDialogTheme())
                        .setTitle("Use Rollcall on the web?")
                        .setMessage("Create an optional Rollcall account to view this phone’s synced attendance, timetable, leave/OD and KCT marks in a read-only web dashboard. Your MyCamu password is never used.")
                        .setPositiveButton("Set up", (dialog, which) -> showCloudAccountDialog())
                        .setNegativeButton("Not now", null).show(), 500L);
            }
        } catch (JSONException | RuntimeException e) {
            finishSync("Could not read the attendance response. Tap Sync to try again.");
        }
    }

    private void processClassAttendance(JSONArray marks) {
        if (marks == null) return;
        hasClassAttendanceSync = true;
        JSONObject latest = null;
        long now = System.currentTimeMillis();
        android.content.SharedPreferences sent = getSharedPreferences("attendance_notice_state", MODE_PRIVATE);
        for (int i = 0; i < marks.length(); i++) {
            JSONObject mark = marks.optJSONObject(i);
            if (mark == null) continue;
            long end = mark.optLong("endsAt", 0), start = mark.optLong("startsAt", 0);
            if (start <= 0) continue;
            if (end <= now && (latest == null || start > latest.optLong("startsAt", 0))) latest = mark;
            String id = mark.optString("id", "");
            String status = mark.optString("status", "not_taken");
            String key = "attendance_notice_" + Integer.toHexString(id.hashCode());
            String previous = sent.getString(key, "");
            boolean confirmedStatus = "present".equals(status) || "absent".equals(status);
            boolean confirmedMissing = end <= now && "not_taken".equals(status)
                    && end <= now - ClassAttendanceNotifier.MISSING_MARK_GRACE_MS;
            if (end >= now - 90 * 60_000L && (confirmedStatus || confirmedMissing) && !status.equals(previous)) {
                if (ClassAttendanceNotifier.post(this, id, mark.optString("name", "Class"), status))
                    sent.edit().putString(key, status).apply();
            }
        }
        if (latest != null) {
            latestClassAttendanceId = latest.optString("id", "");
            latestClassAttendanceName = latest.optString("name", "Class");
            latestClassAttendanceStatus = latest.optString("status", "not_taken");
        }
    }

    private List<LiveSnapshotStore.ClassSession> parseTimetable(JSONArray source) {
        List<LiveSnapshotStore.ClassSession> rows = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (source == null) return rows;
        for (int i = 0; i < Math.min(source.length(), 500); i++) {
            JSONObject row = source.optJSONObject(i);
            if (row == null) continue;
            long startsAt = row.optLong("startsAt", 0), endsAt = row.optLong("endsAt", startsAt);
            String id = row.optString("id", "");
            String code = row.optString("code", "");
            String name = row.optString("name", "");
            if (startsAt <= 0 || name.trim().isEmpty()) continue;
            if (id.isEmpty()) id = code + "_" + startsAt;
            String key = id + "_" + startsAt;
            if (!seen.add(key)) continue;
            rows.add(new LiveSnapshotStore.ClassSession(id, code, name,
                    row.optString("staff", ""), row.optString("location", ""), row.optString("kind", "class"),
                    startsAt, endsAt, row.optBoolean("allDay"), row.optBoolean("cancelled")));
        }
        Collections.sort(rows, (a, b) -> Long.compare(a.startsAt, b.startsAt));
        return rows;
    }

    private List<LiveSnapshotStore.LeaveRecord> parseLeaves(JSONArray source) {
        List<LiveSnapshotStore.LeaveRecord> rows = new ArrayList<>();
        if (source == null) return rows;
        for (int i = 0; i < Math.min(source.length(), 500); i++) {
            JSONObject row = source.optJSONObject(i);
            if (row == null) continue;
            long startsAt = row.optLong("startsAt", 0), endsAt = row.optLong("endsAt", startsAt);
            if (startsAt <= 0) continue;
            rows.add(new LiveSnapshotStore.LeaveRecord(row.optString("id", ""), row.optString("type", "Leave"),
                    row.optString("status", "Unknown"), startsAt, endsAt, row.optInt("periods", 0),
                    row.optString("periodNumbers", ""), row.optString("periodDetails", "[]"), row.optBoolean("currentSemester"), row.optBoolean("semesterKnown")));
        }
        Collections.sort(rows, (a, b) -> Long.compare(a.startsAt, b.startsAt));
        return rows;
    }

    private List<LiveSnapshotStore.ClassMark> parseClassMarks(JSONArray history, JSONArray recent) {
        Map<String, LiveSnapshotStore.ClassMark> marks = new java.util.LinkedHashMap<>();
        if (history != null) for (int i = 0; i < Math.min(history.length(), 2000); i++) {
            JSONObject row = history.optJSONObject(i);
            if (row == null) continue;
            long startsAt = row.optLong("startsAt", 0);
            if (startsAt <= 0) continue;
            String id = row.optString("id", "");
            String code = row.optString("code", "");
            for (LiveSnapshotStore.ClassSession session : LiveSnapshotStore.get().timetable) {
                String a = code.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
                String b = session.code.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
                if (!a.isEmpty() && !b.isEmpty() && a.equals(b)
                        && Math.abs(session.startsAt - startsAt) < 60_000L) {
                    id = session.id;
                    code = session.code;
                    break;
                }
            }
            if (id.isEmpty()) id = code + "_" + startsAt;
            marks.put(id, new LiveSnapshotStore.ClassMark(id, code, row.optString("name", code),
                    startsAt, row.optString("status", "unable_to_confirm"),
                    row.optString("leaveType"), row.optString("leaveStatus"), true));
        }
        if (recent != null) for (int i = 0; i < recent.length(); i++) {
            JSONObject row = recent.optJSONObject(i);
            if (row == null) continue;
            String id = row.optString("id", "");
            if (id.isEmpty()) continue;
            LiveSnapshotStore.ClassMark prior = marks.get(id);
            String status = row.optString("status", "unable_to_confirm");
            if (prior == null || !"unable_to_confirm".equals(status))
                marks.put(id, new LiveSnapshotStore.ClassMark(id, row.optString("code", ""),
                        row.optString("name", "Class"), row.optLong("startsAt", 0), status,
                        prior == null ? "" : prior.leaveType, prior == null ? "" : prior.leaveStatus,
                        prior != null && prior.leaveDetailsKnown));
        }
        List<LiveSnapshotStore.ClassMark> result = new ArrayList<>(marks.values());
        Collections.sort(result, (a, b) -> Long.compare(b.startsAt, a.startsAt));
        if (result.size() > 2000) return new ArrayList<>(result.subList(0, 2000));
        return result;
    }

    private void scheduleBackgroundSync() {
        if (!getPreferences(MODE_PRIVATE).getBoolean(MYCAMU_CONSENT_KEY, false)) return;
        if (getSharedPreferences("mycamu_sync", MODE_PRIVATE).getString("account_context", "").isEmpty()) return;
        Constraints constraints = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(AttendanceSyncWorker.class,
                15, java.util.concurrent.TimeUnit.MINUTES).setConstraints(constraints).build();
        WorkManager.getInstance(getApplicationContext()).enqueueUniquePeriodicWork(
                "mycamu-attendance-sync", ExistingPeriodicWorkPolicy.UPDATE, request);
    }

    private void finishSync(String message) {
        syncInProgress = false;
        lastAutoSyncAt = System.currentTimeMillis();
        boolean success = (message.startsWith("Synced ") || message.startsWith("MyCamu connected."))
                && !message.contains("Partial sync:");
        syncErrorMessage = success ? "" : message;
        LiveSnapshotStore.syncError(this, syncErrorMessage);
        handler.removeCallbacks(syncTimeout);
        handler.removeCallbacks(pollResult);
        if (webSyncButton != null) {
            webSyncButton.setText(sessionExpired || !connectedOnce ? "Check sign-in" : "Sync attendance");
            webSyncButton.setEnabled(true);
        }
        showSyncMessage(message);
    }

    private void showSyncMessage(String message) {
        if (webStatus != null) webStatus.setText(message);
        dashboardMessage = message;
        if (!syncInProgress && !syncErrorMessage.isEmpty()) LiveSnapshotStore.syncError(this, syncErrorMessage);
        if (dashboardView != null) render(LiveSnapshotStore.get());
    }

    private static boolean isCamuPortal(String url) {
        if (url == null) return false;
        android.net.Uri uri = android.net.Uri.parse(url);
        if (!"https".equalsIgnoreCase(uri.getScheme())) return false;
        String host = uri.getHost();
        if (host == null) return false;
        host = host.toLowerCase(Locale.ROOT);
        for (String domain : new String[]{"mycamu.co.in", "mycamu.com", "camu.in", "camuerp.com", "camuerp.in"})
            if (host.equals(domain) || host.endsWith("." + domain)) return true;
        return false;
    }

    @Override protected void onResume() {
        super.onResume();
        String launchChallenge = getIntent() == null ? null : getIntent().getStringExtra("rollcall_web_challenge_id");
        if (launchChallenge != null) {
            getIntent().removeExtra("rollcall_web_challenge_id");
            getSharedPreferences("rollcall_cloud_account", MODE_PRIVATE).edit().putString("pending_web_challenge_id", launchChallenge).apply();
        }
        FirebaseUser cloudUser = FirebaseAuth.getInstance().getCurrentUser();
        if (cloudUser != null && (getSharedPreferences("rollcall_cloud_account", MODE_PRIVATE).getBoolean("registration_pending", false)
                || !CloudAccountClient.deviceId(this).equals(getSharedPreferences("rollcall_cloud_account", MODE_PRIVATE).getString("registered_device_id", ""))))
            cloudUser.reload().addOnCompleteListener(task -> { if (task.isSuccessful() && cloudUser.isEmailVerified()) registerCloudDevice(null); });
        String challengeId = getSharedPreferences("rollcall_cloud_account", MODE_PRIVATE).getString("pending_web_challenge_id", "");
        if (!challengeId.isEmpty()) {
            getSharedPreferences("rollcall_cloud_account", MODE_PRIVATE).edit().remove("pending_web_challenge_id").apply();
            handler.postDelayed(() -> showPendingWebCode(challengeId), 2800L);
        }
        if (dashboardView != null && dashboard != null && dashboard.getVisibility() == View.VISIBLE)
            dashboardView.restoreSelectedPage();
        routeWidgetLaunch(getIntent());
        classRemindersEnabled = ClassReminderScheduler.enabled(this);
        ClassReminderScheduler.schedule(this, LiveSnapshotStore.get().timetable);
        updateReminderSettings();
        RollcallWidgets.refresh(this);
        forceForegroundSync = connectedOnce;
        handler.removeCallbacks(routeMonitor);
        handler.post(routeMonitor);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String challengeId = intent.getStringExtra("rollcall_web_challenge_id");
        intent.removeExtra("rollcall_web_challenge_id");
        getSharedPreferences("rollcall_cloud_account", MODE_PRIVATE).edit().remove("pending_web_challenge_id").apply();
        routeWidgetLaunch(intent);
        if (challengeId != null) handler.postDelayed(() -> showPendingWebCode(challengeId), 450L);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(routeMonitor);
        super.onPause();
    }

    @Override public void onBackPressed() {
        if (myCamuConsentPage != null && myCamuConsentPage.getVisibility() == View.VISIBLE) deferMyCamuConsent();
        else if (dashboardView != null && dashboardView.closeOdDetailsForBack()) { /* Returned to Week attendance. */ }
        else if (kctPortalPage != null && kctPortalPage.getVisibility() == View.VISIBLE) showDashboard();
        else if (aboutPage != null && aboutPage.getVisibility() == View.VISIBLE) showDashboard();
        else if (webPage != null && webPage.getVisibility() == View.VISIBLE) {
            if (connectedOnce) showDashboard();
            else if (webView != null && webView.canGoBack()) webView.goBack();
            else super.onBackPressed();
        }
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(routeMonitor);
        handler.removeCallbacks(pollResult);
        handler.removeCallbacks(syncTimeout);
        LiveSnapshotStore.setListener(null);
        if (webView != null) {
            if (webView.getParent() instanceof ViewGroup) ((ViewGroup) webView.getParent()).removeView(webView);
            webView.destroy();
            webView = null;
        }
        if (kctWebView != null) {
            if (kctWebView.getParent() instanceof ViewGroup) ((ViewGroup) kctWebView.getParent()).removeView(kctWebView);
            kctWebView.destroy();
            kctWebView = null;
        }
        super.onDestroy();
    }
}
