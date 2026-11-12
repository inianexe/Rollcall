
(function(){
'use strict';
var state=null, page='home', courseFilter='all', weekMode='attendance', planMode='attend',
    odMode='recorded', odFilter='current', planCount=1, planCourse='', expanded={}, expandedTerms={};
var root=document.getElementById('app');
var byId=function(id){return document.getElementById(id)};
var html=function(s){return String(s==null?'':s).replace(/[&<>"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]})};
var val=function(x){return isFinite(Number(x))?Number(x):0};
var num=function(x){return Math.round(val(x))};
var pct=function(c){return c.countsKnown&&c.total?100*c.attended/c.total:val(c.percent)};
var fmt=function(x){return Number(val(x).toFixed(1)).toString()+'%'};
var date=function(ms,opts){try{return new Date(ms).toLocaleDateString('en-IN',opts)}catch(e){return '—'}};
var hour=function(ms){try{return new Date(ms).toLocaleTimeString('en-IN',{hour:'numeric',minute:'2-digit'})}catch(e){return ''}};
var tnow=function(){return Date.now()};
function bridge(method,arg){try{if(window.RollcallBridge&&typeof window.RollcallBridge[method]==='function')window.RollcallBridge[method](arg===undefined?'':String(arg))}catch(e){console.warn('Rollcall action unavailable',method)}}
function shellPage(next){
  page=next;
  document.querySelectorAll('.screen').forEach(function(node){node.classList.toggle('active',node.id==='page-'+page)});
  document.querySelectorAll('.nav-btn').forEach(function(btn){var yes=btn.dataset.page===page;btn.classList.toggle('active',yes);if(yes)btn.setAttribute('aria-current','page');else btn.removeAttribute('aria-current')});
  var nav=document.querySelector('.navi');if(nav)nav.style.display=page==='settings'||page==='leave'?'none':'flex';
  window.scrollTo({top:0,behavior:'instant'});
  if(page==='courses')renderCourses();
  if(page==='what')renderPlanner();
  if(page==='week')renderWeek();
  if(page==='marks')renderMarks();
  if(page==='leave')renderLeave();
}
var linkButton=function(label,method){return '<button class="button primary" type="button" data-action="'+method+'">'+html(label)+'</button>'};
function nativeCourses(){return state&&Array.isArray(state.rows)?state.rows:[]}
function goal(){return Math.min(100,Math.max(50,num(state?state.target:75)))}
function calc(c){
  if(!c.countsKnown||!c.total)return {miss:'—',must:'—'};
  var target=goal(),a=val(c.attended),t=val(c.total);
  var miss=Math.max(0,Math.floor((100*a-target*t)/target+1e-8));
  var must=100*a>=target*t-1e-8?0:target>=100?'∞':Math.max(0,Math.ceil((target*t-100*a)/(100-target)-1e-8));
  return {miss:miss,must:must};
}
function isBelow(c){return pct(c)<goal()-1e-6&&!!c.countsKnown&&c.total>0}
function courseMarkup(c,i){
  var bad=isBelow(c),r=calc(c),percent=pct(c),code=html(c.code),name=html(c.name);
  var countText=c.countsKnown?num(c.attended)+' / '+num(c.total)+' CLASSES':'CLASS COUNTS UNAVAILABLE';
  return '<article class="course '+(bad?'warn':'')+(expanded[c.code]?' open':'')+'" data-course-code="'+code+'">'
    +'<div class="course-head"><div><div class="eyebrow">'+code+'</div><h3>'+name+'</h3></div><span class="percent-pill">'+fmt(percent)+'</span></div>'
    +'<div class="course-metrics"><div class="metric '+(!bad&&c.countsKnown?'highlight':'')+'"><span class="eyebrow">CAN MISS</span><strong>'+r.miss+'</strong></div>'
    +'<div class="metric '+(bad?'highlight':'')+'"><span class="eyebrow">MUST ATTEND</span><strong>'+r.must+'</strong></div></div>'
    +'<div class="course-foot"><span class="meta">'+countText+'</span><div class="track '+(bad?'warn':'')+'"><i style="width:'+Math.max(0,Math.min(100,percent))+'%"></i></div></div>'
    +'<button type="button" class="detail-toggle" data-toggle-course="'+code+'" aria-expanded="'+!!expanded[c.code]+'">DETAILS '+(expanded[c.code]?'−':'+')+'</button>'
    +'<div class="course-detail">TARGET: '+goal()+'% · '+html(c.status||'')+'<br>'+html(c.guidance||'')+
        (c.odCount?' <br>OD: '+num(c.odCount)+' mapped; '+num(c.odCredit)+' absent credits.':'')
        +'<br><button class="section-action" type="button" data-plan="'+code+'">TRY IN WHAT IF? ↗</button></div></article>';
}
function renderCourses(){
  if(!state)return;
  var all=nativeCourses(),below=all.filter(isBelow),safe=all.filter(function(c){return !isBelow(c)});
  document.querySelectorAll('[data-filter]').forEach(function(btn){
    var type=btn.dataset.filter;
    btn.textContent=type==='all'?'ALL / '+all.length:type==='below'?'BELOW TARGET / '+below.length:'ON TRACK / '+safe.length;
    btn.classList.toggle('selected',type===courseFilter);
  });
  var list=courseFilter==='below'?below:courseFilter==='safe'?safe:all;
  byId('courseList').innerHTML=list.length?list.map(courseMarkup).join(''):
     '<div class="blank-panel"><div class="eyebrow">COURSES / '+(all.length?'CLEAR':'NOT CONNECTED')+'</div><strong>'+
     (all.length?'NOTHING HERE.<br>YOU’RE CLEAR.':'YOUR COURSES<br>LAND HERE.')+'</strong><p>'+
     (all.length?'No courses in this filter.':'Connect MyCamu to see your recorded attendance and course plans.')+'</p></div>';
  var footer=document.querySelector('#page-courses .footer-label');if(footer)footer.textContent='ATTENDANCE TARGET '+goal()+'% / COUNTS FROM MYCAMU';
}
function nextEvent(){
  var now=tnow(),times=(state.timetable||[]).filter(function(e){return e.startsAt>=now&&!e.cancelled&&!e.allDay}).sort(function(a,b){return a.startsAt-b.startsAt});
  var next=times.length?times[0]:null;
  var exam=state.upcomingExamAt>=now&&state.upcomingExamTitle?state.upcomingExamAt:0;
  if(exam&&(!next||exam<next.startsAt))return {when:exam,kind:'EXAM DATE',name:state.upcomingExamTitle,target:'marks'};
  return next?{when:next.startsAt,kind:'NEXT CLASS',name:next.name,target:'week'}:{when:now,kind:'TODAY',name:'Your academic week',target:'week'};
}
function renderHome(){
 if(!state)return;
 var event=nextEvent(),now=tnow(),g=goal(),name=(state.name||'MyCamu student').trim(),hourNow=new Date().getHours();
 var greeting=hourNow<12?'Good morning':hourNow<17?'Good afternoon':'Good evening';
 var hero=document.querySelector('#page-home .hero');
 hero.innerHTML='<div class="hero-top"><span class="eyebrow">ACADEMIC TRANSMISSION / 01</span><span class="hero-date">'+html(date(now,{weekday:'short',day:'2-digit',month:'short'}).toUpperCase())+'</span></div>'+
 '<div><p class="overline" style="color:#343a26;margin:0">'+greeting+',</p><h2>'+html(name.toUpperCase())+'<span style="color:var(--orange)">.</span><br>YOU GOT THIS.</h2><p class="hero-copy">One class at a time. Your academic week, decoded.</p></div>'+
 '<div class="hero-bottom"><div class="hero-foot"><span class="status-dot"></span><span>'+html(state.syncing?'SYNCING / LIVE':state.syncError?'SYNC ERROR / RETRY':state.status||'ROLLCALL / READY')+'</span></div><button class="section-action" style="color:#111" data-action="sync" type="button" aria-label="Refresh your attendance">↻ SYNC</button></div>'+
 '<div class="ticket-art" role="button" tabindex="0" aria-label="'+html(event.kind+', '+date(event.when,{dateStyle:'long'}))+'. Open '+event.target+'" data-page="'+event.target+'">'+
 '<div class="ticket-back"></div><div class="ticket-front"><span class="tk-tag">'+html(event.kind)+'</span><strong>'+date(event.when,{day:'2-digit'})+'</strong><span class="tk-bottom">'+html(date(event.when,{month:'short',year:'numeric'}).toUpperCase())+'</span></div><i class="ticket-ring"></i><i class="ticket-ring r2"></i></div>';
 var total=nativeCourses().length,below=nativeCourses().filter(isBelow).length;
 document.querySelector('#page-home .broadcast-row').innerHTML=
 '<div class="micro-card"><div class="eyebrow">STATUS / LIVE</div><strong style="color:var(--lime)">'+(total?below?'FOCUS / '+below:'ON TRACK ↗':'AWAITING SYNC')+'</strong><div class="meta">'+total+' COURSES · '+g+'% TARGET</div></div>'+
 '<div class="micro-card lilac"><div class="eyebrow">WHAT’S NEXT?</div><strong>'+html(event.kind==='TODAY'?'Make today<br>count.':event.kind==='EXAM DATE'?'An exam<br>is coming.':'Your next<br>class awaits.').replaceAll('&lt;br&gt;','<br>')+'</strong></div>';
 var examCard=document.querySelector('#page-home .exam-card');
 var exists=state.upcomingExamAt>0&&state.upcomingExamTitle&&state.upcomingExamAt>now-86400000;
 if(exists){
  var days=Math.max(0,Math.ceil((state.upcomingExamAt-now)/86400000));
  examCard.innerHTML='<div class="eyebrow small-orange">● UPCOMING EXAM</div><h3>'+html(state.upcomingExamTitle.toUpperCase())+'</h3>'+
  '<div class="badge-row">'+(state.upcomingExamType?'<span class="badge">'+html(state.upcomingExamType)+'</span>':'')+(state.upcomingExamSession?'<span class="badge orange">'+html(state.upcomingExamSession)+'</span>':'')+'</div>'+
  '<div class="exam-footer"><div><span class="countdown">'+(days===0?'TODAY':days===1?'TOMORROW':'IN '+days+' DAYS')+' ↗</span><div class="meta">'+html(date(state.upcomingExamAt,{weekday:'short',day:'numeric',month:'short'}))+' · '+html(state.upcomingExamCode||'')+'</div></div><div class="eyebrow" style="font-size:22px;color:var(--lime)">'+String(days).padStart(2,'0')+'<span style="font-size:9px">D</span></div></div>';
 }else{
  examCard.innerHTML='<div class="eyebrow small-orange">● UPCOMING EXAM</div><h3>NOTHING ON YOUR<br>RADAR YET.</h3><p class="meta">'+html(state.academicConnected?'No upcoming exam returned by KCT.':'Connect KCT to see upcoming exams.')+'</p>'+linkButton('REFRESH KCT ↗','kct');
 }
 var sunday=new Date();sunday.setHours(0,0,0,0);sunday.setDate(sunday.getDate()-sunday.getDay());
 var sched=state.timetable||[],weekCount=0;
 document.querySelector('#page-home .days').innerHTML=Array.from({length:7},function(_,i){
  var d=new Date(sunday);d.setDate(sunday.getDate()+i);
  var active=sched.some(function(t){return new Date(t.startsAt).toDateString()===d.toDateString()&&!t.cancelled});
  if(active)weekCount++;
  return '<span class="day '+(d.toDateString()===new Date().toDateString()?'today':'')+'"><i style="'+(active?'border-color:var(--lime);':'')+'"></i>'+['S','M','T','W','T','F','S'][i]+'</span>';
 }).join('');
 document.querySelector('#page-home .event-hint').textContent=state.timetableLoaded?
   (weekCount?weekCount+' DAYS WITH CLASSES // OPEN YOUR WEEK':'NO CLASSES SCHEDULED // FREE SLOT'):
   'SCHEDULE NOT SYNCED // REFRESH MYCAMU';
 var worst=nativeCourses().filter(isBelow).sort(function(a,b){return pct(a)-pct(b)})[0];
 var risk=document.querySelector('#page-home .risk-card');
 if(worst){
  var r=calc(worst);
  risk.innerHTML='<div class="risk-mark">!</div><div class="risk-main"><h4>'+html(worst.name)+'</h4><p>'+html(worst.counts||'')+'</p><div class="track warn"><i style="width:'+Math.max(0,Math.min(100,pct(worst)))+'%"></i></div></div><div class="risk-end"><strong>'+fmt(pct(worst))+'</strong><span>MUST ATTEND '+r.must+'</span></div>';
  risk.onclick=function(){courseFilter='below';shellPage('courses')};
 }else{
  risk.innerHTML='<div class="risk-mark" style="color:var(--green)">✓</div><div class="risk-main"><h4>'+ (total?'ALL COURSES ON TARGET':'YOUR ATTENDANCE WILL APPEAR HERE')+'</h4><p>'+ (total?'All '+total+' courses meet '+g+'%. Keep it up.':'Connect to MyCamu to begin tracking.')+'</p></div><div class="risk-end"><strong style="color:var(--lime)">'+(total?'OK':'—')+'</strong></div>';
  risk.onclick=function(){shellPage('courses')};
 }
}
function renderWeek(){
 if(!state)return;
 document.querySelectorAll('[data-week]').forEach(function(b){b.classList.toggle('selected',b.dataset.week===weekMode)});
 var attendance=byId('weekAttendance'),schedule=byId('weekSchedule');
 attendance.hidden=weekMode!=='attendance';schedule.hidden=weekMode!=='schedule';
 if(weekMode==='attendance'){
  var items=(state.classMarks||[]).slice().sort(function(a,b){return b.startsAt-a.startsAt});
  var last='',out='<div class="eyebrow" style="color:var(--lilac)">ATTENDANCE LOG / '+html(state.status||'LIVE')+'</div>';
  items.slice(0,100).forEach(function(item){
   var day=date(item.startsAt,{weekday:'long',day:'2-digit',month:'short',year:'numeric'}).toUpperCase();
   if(day!==last){last=day;out+='<div class="date-group">'+html(day)+'</div>'}
   var status=String(item.status||'').toLowerCase();
   var color=status==='present'?'var(--green)':status==='absent'?'#ff8870':'var(--muted)';
   var desc=status==='present'?'PRESENT ✓':status==='absent'?'ABSENT ×':status==='not_taken'?'NOT MARKED':'UNCONFIRMED';
   out+='<div class="history-item"><div><h4>'+html(item.name)+'</h4><p>'+html(item.code)+' · '+html(hour(item.startsAt))+'</p></div><span class="present" style="color:'+color+'">'+desc+'</span></div>';
  });
  if(!items.length)out+='<div class="blank-panel"><div class="eyebrow">YOUR RECORD</div><strong>HISTORY<br>GOES HERE.</strong><p>Sync attendance to load your class history.</p></div>';
  attendance.innerHTML=out+'<button type="button" class="section-action" data-page="leave">LEAVE & OD →</button>';
 } else {
  var rows=(state.timetable||[]).slice().sort(function(a,b){return a.startsAt-b.startsAt});
  var now=tnow(),lastDay='',out='';
  rows.forEach(function(item){
   var day=date(item.startsAt,{weekday:'long',day:'2-digit',month:'short',year:'numeric'}).toUpperCase();
   if(day!==lastDay){out+='<div class="date-group">'+html(day)+'</div>';lastDay=day;}
   out+='<div class="history-item"><div><h4>'+html(item.name)+'</h4><p>'+html(item.time||hour(item.startsAt))+' · '+html(item.code||'')+(item.details?' · '+html(item.details):'')+'</p></div><span class="badge '+(item.cancelled?'orange':'lime')+'">'+(item.cancelled?'CANCELLED':item.allDay?'EVENT':item.startsAt<now?'DONE':'UPCOMING')+'</span></div>';
  });
  if(!rows.length)out='<div class="blank-panel"><div class="eyebrow">THE WEEK / LIVE</div><strong>NO CLASSES<br>ON YOUR<br>SCHEDULE.</strong><p>'+html(state.timetableLoaded?'Your connected timetable has no classes this week.':'Connect MyCamu to see your week.')+'</p></div>';
  schedule.innerHTML=out+'<div class="notice"><b>REAL TIMETABLE.</b> Data from your connected MyCamu account.</div>';
 }
}
function semesterNum(label){var m=String(label).match(/semester\s*[-:]?\s*(\d+)/i);return m?Number(m[1]):0}
function gradePoint(g){var map={'O':10,'S':10,'A+':9,'A':8,'B+':7,'B':6,'C':5,'U':0,'SA':0,'WD':0};return Object.prototype.hasOwnProperty.call(map,String(g).toUpperCase())?map[String(g).toUpperCase()]:-1}
function renderMarks(){
 if(!state)return;
 var courses=state.academicCourses||[];
 var eligible=courses.filter(function(c){return gradePoint(c.grade)>=0&&val(c.credits)>0});
 var credits=eligible.reduce(function(n,c){return n+val(c.credits)},0);
 var weighted=eligible.reduce(function(n,c){return n+gradePoint(c.grade)*val(c.credits)},0);
 var earned=eligible.filter(function(c){return gradePoint(c.grade)>=5}).reduce(function(n,c){return n+val(c.credits)},0);
 var semesters=[...new Set(courses.map(function(c){return c.semester}).filter(Boolean))].sort(function(a,b){return semesterNum(b)-semesterNum(a)});
 var cgpa=credits?weighted/credits:null;
 var banner=document.querySelector('#page-marks .gpa-banner');
 banner.innerHTML='<div class="eyebrow">CUMULATIVE GPA / CGPA</div><div class="number">'+(cgpa===null?'—':cgpa.toFixed(2))+'<span style="font-size:19px;letter-spacing:-.05em"> / 10</span></div><p>'+credits.toFixed(1)+' CREDITS COUNTED · '+earned.toFixed(1)+' EARNED · '+semesters.length+' SEMESTERS</p>';
 var terms=semesters.map(function(semester){var all=courses.filter(function(c){return c.semester===semester}),valid=all.filter(function(c){return gradePoint(c.grade)>=0&&val(c.credits)>0});var total=valid.reduce(function(n,c){return n+val(c.credits)},0);var sum=valid.reduce(function(n,c){return n+gradePoint(c.grade)*val(c.credits)},0);return {semester:semester,courses:all,gpa:total?sum/total:null,credits:total}});
 var chart=document.querySelector('#page-marks .bars');
 chart.innerHTML=terms.length?terms.slice().reverse().map(function(t){
  return '<div class="bar-wrap"><span>'+ (t.gpa===null?'—':t.gpa.toFixed(2))+'</span><div class="bar" style="height:'+((t.gpa||0)*12)+'px"></div><span>S'+semesterNum(t.semester)+'</span></div>';
 }).join(''):'<div class="blank-panel"><div class="eyebrow">MARKS / NOT CONNECTED</div><strong>YOUR STORY,<br>IN NUMBERS.</strong><p>Sign into the KCT portal to import your semester results.</p></div>';
 var resultContainer=document.querySelector('#page-marks .section-head:nth-of-type(2)');
 // Keep the existing section headings and replace the prototype-only result rows.
 var resultItems=Array.from(document.querySelectorAll('#page-marks .result-item'));
 resultItems.forEach(function(el){el.remove()});
 var marker=document.querySelector('#page-marks .footer-label');
 if(marker){
  var out='';
  terms.forEach(function(t,i){
    out+='<div class="date-group" data-term="'+html(t.semester)+'" role="button" tabindex="0"><span>'+html(t.semester.toUpperCase())+' · SGPA '+(t.gpa===null?'—':t.gpa.toFixed(2))+'</span><span style="float:right"> '+(expandedTerms[t.semester]||i===0?'−':'+')+'</span></div>';
    if(expandedTerms[t.semester]||(!Object.prototype.hasOwnProperty.call(expandedTerms,t.semester)&&i===0))
    t.courses.forEach(function(c){
      out+='<div class="result-item"><div><strong>'+html(c.name||c.code)+'</strong><small>'+html(c.code)+' · '+val(c.credits).toFixed(1)+' CR · '+html(c.result||'')+'</small></div><div class="grade">'+html(c.grade||'—')+'</div></div>';
    });
  });
  var old=byId('nativeResults');if(!old){old=document.createElement('div');old.id='nativeResults';marker.parentNode.insertBefore(old,marker)}
  old.innerHTML=out;
  marker.textContent='CREDIT WEIGHTED / SYNCED WITH KCT';
 }
 var refresh=byId('refreshKct');if(!refresh){refresh=document.createElement('button');refresh.className='section-action';refresh.id='refreshKct';refresh.textContent='REFRESH FROM KCT PORTAL ↗';refresh.dataset.action='kct';banner.parentNode.insertBefore(refresh,banner.nextSibling)}
}
function renderPlanner(){
 if(!state)return;
 var all=nativeCourses(),sel=byId('courseSelect');
 if(!sel)return;
 if(!planCourse||!all.some(function(c){return c.code===planCourse}))planCourse=all.length?all[0].code:'';
 var options=all.map(function(c){return '<option value="'+html(c.code)+'">'+html(c.name)+' / '+html(c.code)+'</option>'}).join('');
 if(sel.innerHTML!==options)sel.innerHTML=options;
 sel.value=planCourse;
 if(!all.length){byId('simMessage').textContent='Connect MyCamu before simulating attendance.';return}
 var c=all.find(function(x){return x.code===planCourse})||all[0];
 var count=planCount;
 var a=val(c.attended),t=val(c.total);
 if(odMode!=='recorded'&&c.odReconciled&&c.odCredit>0){
  if(odMode==='present')a+=num(c.odCredit);
  if(odMode==='excluded')t=Math.max(a,t-num(c.odCredit));
 }
 var initial=t?100*a/t:0;
 var nextA=a+(planMode==='attend'?count:0),nextT=t+count,nextP=nextT?100*nextA/nextT:0;
 byId('currentPercent').textContent=c.countsKnown?fmt(initial):'—';
 byId('projectPercent').textContent=c.countsKnown?fmt(nextP):'—';
 byId('projectCaption').textContent=c.code+' · '+nextA+' attended / '+nextT+' total';
 byId('classSlider').max=50;
 byId('classSlider').value=planCount;
 byId('classCount').textContent=planCount;
 document.querySelectorAll('[data-action=attend],[data-action=miss]').forEach(function(b){b.classList.toggle('selected',b.dataset.action===planMode)});
 var after={attended:nextA,total:nextT,countsKnown:c.countsKnown},result=calc(after);
 byId('simMessage').innerHTML='<b>SIMULATION ONLY.</b> '+(c.countsKnown?
  (result.must==='∞'?'A 100% target cannot be recovered after a missed class.':result.must===0?'This plan meets your '+goal()+'% attendance target.':'After this plan, attend '+result.must+' consecutive classes to reach '+goal()+'%.'):
  'MyCamu has not supplied the attendance counts needed to make a projection.');
 var od=byId('torchODOptions');
 if(!od){od=document.createElement('div');od.id='torchODOptions';sel.parentNode.insertBefore(od,sel.nextSibling)}
 od.innerHTML=c.odReconciled&&c.odCredit>0?'<div class="eyebrow" style="color:var(--muted);margin-top:18px">APPROVED OD / SCENARIO</div><div class="filter-row" style="margin-top:8px">'+
  ['recorded','present','excluded'].map(function(x){return '<button class="chip '+(odMode===x?'selected':'')+'" data-od-mode="'+x+'" type="button">'+(x==='recorded'?'RECORDED':x==='present'?'OD PRESENT':'OD EXCLUDED')+'</button>'}).join('')+'</div>':'';
}
function renderLeave(){
 if(!state)return;
 var all=state.leaves||[],rows=odFilter==='all'?all:all.filter(function(x){return x.currentSemester});
 document.querySelectorAll('[data-od-filter]').forEach(function(b){b.classList.toggle('selected',b.dataset.odFilter===odFilter)});
 var out=rows.map(function(x){
  var status=x.status||'Unknown',tone=/approv/i.test(status)?'var(--green)':/reject/i.test(status)?'#ff8870':'#ffcb75';
  return '<div class="torch-list-row"><div class="course-head"><h4>'+html(x.type||'Leave')+'</h4><span class="badge" style="color:'+tone+'">'+html(status.toUpperCase())+'</span></div><p>'+html(x.date||date(x.startsAt,{dateStyle:'medium'}))+'</p><div class="eyebrow" style="color:var(--lime);margin-top:12px">'+num(x.periods)+' PERIODS</div><p>'+html(x.details||'')+'</p></div>';
 }).join('');
 if(!rows.length)out='<div class="blank-panel"><div class="eyebrow">LEAVE / OD</div><strong>NO RECORDS<br>FOUND.</strong><p>No '+(odFilter==='current'?'current semester ':'')+'leave or OD records were returned by MyCamu.</p></div>';
 byId('leaveRecords').innerHTML='<div class="eyebrow" style="color:var(--muted)">'+rows.length+' RECORD'+(rows.length===1?'':'S')+'</div>'+out;
}
function renderSettings(){
 if(!state)return;
 var screen=byId('page-settings');
 screen.innerHTML='<button type="button" class="section-action" data-page="home" style="margin-bottom:12px">← BACK TO DASHBOARD</button>'+
 '<div class="eyebrow" style="color:var(--lime)">06 / CONTROL ROOM</div><h1 class="page-title">Settings<span style="color:var(--orange)">.</span></h1><p class="page-sub">Make this your space.</p>'+
 '<div class="settings-block"><h3>APP PREFERENCES</h3>'+
 '<div class="settings-row"><div><strong>Dark appearance</strong><br><span>Switch between Ink and Paper.</span></div><button class="toggle '+(state.dark?'on':'')+'" aria-label="Toggle dark appearance" aria-pressed="'+!!state.dark+'" data-action="theme" type="button"></button></div>'+
 '<div class="settings-row"><div><strong>Class reminders</strong><br><span>10-minute heads-up before each class.</span></div><button class="toggle '+(state.remindersEnabled?'on':'')+'" aria-label="Toggle class reminders" aria-pressed="'+!!state.remindersEnabled+'" data-action="reminders" type="button"></button></div>'+
 '<div class="settings-row"><div><strong>Attendance target</strong><br><span>Current goal: '+goal()+'%</span></div><button class="button" type="button" data-action="target">EDIT ↗</button></div>'+
 '<div class="settings-row"><div><strong>Android notifications</strong><br><span>Manage permissions and channels.</span></div><button class="button" type="button" data-action="notifications">OPEN ↗</button></div></div>'+
 '<div class="settings-block"><h3>ACCOUNTS & UPDATES</h3><div class="settings-row"><div><strong>Rollcall web account</strong><br><span>Connect phone and cloud snapshot.</span></div><button class="button" type="button" data-action="cloud">MANAGE ↗</button></div>'+
 '<div class="settings-row"><div><strong>KCT academics</strong><br><span>Marks, GPA and upcoming exams.</span></div><button class="button" type="button" data-action="kct">CONNECT ↗</button></div>'+
 '<div class="settings-row"><div><strong>App updates</strong><br><span>Check the latest GitHub release.</span></div><button class="button" type="button" data-action="updates">CHECK ↗</button></div></div>'+
 '<div class="about-box"><div class="eyebrow">ABOUT / THE PROJECT</div><h3>GOOD DESIGN.<br>BETTER ATTENDANCE.</h3><p>Rollcall makes it easier to understand attendance, timetable, academic marks and progress toward your target.</p>'+
 '<p>Independent app · Not affiliated with an institution or service provider.</p>'+
 '<button class="section-action" style="color:#111" data-action="github">VIEW PROJECT ↗</button></div>'+
 '<div class="settings-block landing-extra"><h3>YOUR DATA & PRIVACY</h3><p>Your MyCamu password is not read or stored by Rollcall. Account-linked snapshots may be stored in Rollcall cloud services when you opt in.</p><div class="button-row"><button class="button" data-action="privacy" type="button">PRIVACY ↗</button><button class="button" data-action="terms" type="button">TERMS ↗</button></div><button class="button full" style="border-color:#ff8870;color:#ff8870;margin-top:16px" data-action="clear" type="button">DISCONNECT & CLEAR DATA</button></div>'+
 '<div class="divider"></div><div class="version">ROLLCALL / '+html(state.version||'ANDROID')+' · TORCH GENESIS</div>';
}
function render(snapshot){
 state=snapshot;
 document.body.classList.toggle('light-mode',!state.dark);
 document.documentElement.style.backgroundColor=state.dark?'#121212':'#f5f4ec';
 var meta=document.querySelector('meta[name=theme-color]');if(meta)meta.content=state.dark?'#121212':'#f5f4ec';
 document.querySelector('.app-header').innerHTML='<div class="logo"><b>[R]</b> rollcall</div><div class="header-actions"><button class="icon-button theme-switch" data-action="theme" type="button" aria-label="Toggle appearance">'+(state.dark?'☼':'◐')+'</button><button class="icon-button avatar" data-page="settings" type="button" aria-label="Open settings">'+html(state.initials||'R')+'</button></div>';
 renderHome();renderCourses();renderWeek();renderPlanner();renderMarks();renderLeave();renderSettings();
 document.body.classList.add('native-ready');
 shellPage(page);
}
document.addEventListener('click',function(e){
 var node=e.target.closest('button,a,[data-page],[data-term]');
 if(!node)return;
 if(node.dataset.page){e.preventDefault();shellPage(node.dataset.page);return}
 if(node.dataset.filter){courseFilter=node.dataset.filter;renderCourses();return}
 if(node.dataset.week){weekMode=node.dataset.week;renderWeek();return}
 if(node.dataset.odFilter){odFilter=node.dataset.odFilter;renderLeave();return}
 if(node.dataset.odMode){odMode=node.dataset.odMode;renderPlanner();return}
 if(node.dataset.toggleCourse){expanded[node.dataset.toggleCourse]=!expanded[node.dataset.toggleCourse];renderCourses();return}
 if(node.dataset.plan){planCourse=node.dataset.plan;shellPage('what');return}
 if(node.hasAttribute('data-term')){var k=node.dataset.term;expandedTerms[k]=!expandedTerms[k];renderMarks();return}
 if(node.id==='targetButton'||node.id==='settingsTargetButton'){showTarget();return}
 if(node.id==='cancelTarget'){hideTarget();return}
 if(node.id==='saveTarget'){bridge('setTarget',byId('targetSlider').value);hideTarget();return}
 if(node.id==='resetSimulation'){planCount=1;planMode='attend';odMode='recorded';renderPlanner();return}
 if(node.dataset.action){
  var act=node.dataset.action;
  if(act==='attend'||act==='miss'){planMode=act;renderPlanner();return}
  if(act==='target'){showTarget();return}
  bridge(({sync:'sync',theme:'toggleTheme',reminders:'toggleReminders',notifications:'notifications',cloud:'cloud',kct:'kct',updates:'updates',clear:'clear',privacy:'privacy',terms:'terms',github:'github'})[act]||act);
 }
});
document.addEventListener('change',function(e){if(e.target.id==='courseSelect'){planCourse=e.target.value;odMode='recorded';renderPlanner()}});
document.addEventListener('input',function(e){
 if(e.target.id==='classSlider'){planCount=Math.max(1,Math.min(50,Number(e.target.value)));renderPlanner()}
 if(e.target.id==='targetSlider')byId('targetValue').textContent=e.target.value+'%';
});
document.addEventListener('keydown',function(e){
 if(e.key==='Escape'){if(byId('targetOverlay').classList.contains('show'))hideTarget();else if(page==='leave'||page==='settings')shellPage('home')}
 if((e.key==='Enter'||e.key===' ')&&e.target.hasAttribute('data-page')){e.preventDefault();shellPage(e.target.dataset.page)}
 if((e.key==='Enter'||e.key===' ')&&e.target.hasAttribute('data-term')){e.preventDefault();var k=e.target.dataset.term;expandedTerms[k]=!expandedTerms[k];renderMarks()}
});
function showTarget(){byId('targetSlider').value=goal();byId('targetValue').textContent=goal()+'%';byId('targetOverlay').classList.add('show');byId('targetSlider').focus()}
function hideTarget(){byId('targetOverlay').classList.remove('show')}
byId('targetOverlay').addEventListener('click',function(e){if(e.target===byId('targetOverlay'))hideTarget()});
window.__rollcallRender=function(o){try{render(typeof o==='string'?JSON.parse(o):o)}catch(e){console.error('Rollcall UI rendering error',e)}};
window.__rollcallNavigate=function(to){shellPage(to||'home')};
window.__rollcallBack=function(){if(byId('targetOverlay').classList.contains('show')){hideTarget();return true}if(page!=='home'){shellPage('home');return true}return false};
window.__rollcallCurrentPage=function(){return page};
if(window.RollcallBridge)bridge('ready');
})();
