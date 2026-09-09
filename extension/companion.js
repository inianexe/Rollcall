(() => {
  'use strict';
  const C=globalThis.CamuAttendanceCore;
  if(!C||globalThis.__rollcallLoaded)return;
  globalThis.__rollcallLoaded=true;

  let rows=new Map(),epoch=0,scanning=false,host,root,timer,lastURL=location.href,query='',riskOnly=false;
  let view=/\/timetable\/?$/i.test(location.pathname)?'today':'attendance',capturedAt=null,timetable=null,tableAt=null;
  const T=globalThis.RollcallTimetable;
  const relevant=()=>/\/(attendance|timetable)\/?$/i.test(location.pathname);
  let status='Open Attendance → Subject-wise, then scan the chart.';
  const active=()=>/\/attendance\/?$/i.test(location.pathname);
  const visible=e=>!!e&&e.getClientRects().length>0;
  const el=(tag,text,cls)=>{const node=document.createElement(tag);if(text!==undefined)node.textContent=text;if(cls)node.className=cls;return node;};
  const schedule=()=>{clearTimeout(timer);timer=setTimeout(render,80);};

  function add(row,source){
    if(!active())return;
    const old=rows.get(row.code);
    if(old&&old.attended===row.attended&&old.total===row.total&&old.name===row.name)return;
    rows.set(row.code,{...row,source,capturedAt:Date.now()});capturedAt=Date.now();schedule();
  }
  function capture(text,source){const row=C.parse(text);if(row)add(row,source);}
  function clear(message='Cleared. Scan the chart when ready.'){
    epoch++;rows.clear();capturedAt=null;scanning=false;status=message;render();
  }

  for(const method of ['fillText','strokeText']){
    const original=CanvasRenderingContext2D.prototype[method];
    CanvasRenderingContext2D.prototype[method]=function(...args){
      const result=Reflect.apply(original,this,args);
      try{if(active()&&visible(this.canvas))capture(args[0],'Canvas tooltip');}catch{}
      return result;
    };
  }

  function inspect(value,token){if(token===epoch&&active())C.strings(value,row=>add(row,'Loaded chart text'));}
  const originalFetch=window.fetch;
  window.fetch=async function(...args){
    const token=epoch,response=await Reflect.apply(originalFetch,this,args);
    if(active()&&/json/i.test(response.headers.get('content-type')||'')){
      try{response.clone().text().then(text=>{if(text.length<2000000)inspect(JSON.parse(text),token);}).catch(()=>{});}catch{}
    }
    return response;
  };
  const originalSend=XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.send=function(...args){
    const token=epoch;
    this.addEventListener('load',()=>{try{
      if(!active())return;
      if(this.responseType==='json')inspect(this.response,token);
      else if((!this.responseType||this.responseType==='text')&&/json/i.test(this.getResponseHeader('content-type')||'')&&this.responseText.length<2000000)inspect(JSON.parse(this.responseText),token);
    }catch{}},{once:true});
    return Reflect.apply(originalSend,this,args);
  };

  function readVisibleTooltips(){
    if(!active()||!document.body)return;
    for(const node of document.querySelectorAll('[role="tooltip"],[class*="tooltip"],[class*="Tooltip"]'))if(visible(node))capture(node.textContent,'HTML tooltip');
    const walker=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT);let node,count=0;
    while((node=walker.nextNode())&&count++<20000)if(node.parentElement&&!/^(SCRIPT|STYLE|TEXTAREA)$/.test(node.parentElement.tagName)&&node.data.includes('%')&&node.data.includes('/')&&visible(node.parentElement))capture(node.data,'Page text');
  }
  function charts(){try{return Object.values(window.Chart?.instances||{}).filter(chart=>visible(chart.canvas||chart.chart?.canvas));}catch{return [];}}
  const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms));
  function hover(node,x,y){for(const type of ['pointermove','mousemove'])node.dispatchEvent(new MouseEvent(type,{bubbles:true,clientX:x,clientY:y,view:window}));}

  async function scan(){
    if(!active()){status='Open Attendance → Subject-wise before scanning.';render();return;}
    if(scanning){epoch++;scanning=false;status='Scan stopped. Results captured so far are shown.';render();return;}
    rows.clear();capturedAt=null;scanning=true;const token=++epoch;status='Scanning the attendance chart…';render();
    try{
      readVisibleTooltips();let precise=false;
      for(const chart of charts()){
        C.strings(chart.data,row=>add(row,'Chart data'));
        const canvas=chart.canvas||chart.chart?.canvas,rect=canvas.getBoundingClientRect();
        for(let index=0;index<(chart.data?.datasets?.length||0);index++)for(const bar of chart.getDatasetMeta?.(index)?.data||[]){
          if(token!==epoch||!active())return;
          const point=bar.getCenterPoint?.()||bar._model;if(!point||!Number.isFinite(point.x)||!Number.isFinite(point.y))continue;
          precise=true;hover(canvas,rect.left+point.x*rect.width/(chart.width||canvas.width),rect.top+point.y*rect.height/(chart.height||canvas.height));await delay(100);readVisibleTooltips();
        }
      }
      if(!precise)for(const canvas of document.querySelectorAll('canvas')){
        const box=canvas.getBoundingClientRect();if(!visible(canvas)||box.width<250||box.height<120)continue;
        for(const fraction of [.22,.5,.78])for(let y=8;y<box.height;y+=7){
          if(token!==epoch||!active())return;
          hover(canvas,box.left+box.width*fraction,box.top+y);await delay(24);readVisibleTooltips();
        }
        canvas.dispatchEvent(new MouseEvent('mouseout',{bubbles:true}));
      }
      for(const mark of [...document.querySelectorAll('svg rect,svg path')].slice(0,500)){
        if(token!==epoch||!active())return;
        const box=mark.getBoundingClientRect();if(box.width<80||box.height<5||box.height>80)continue;
        hover(mark,box.left+box.width/2,box.top+box.height/2);await delay(60);readVisibleTooltips();
      }
      status=rows.size?`${rows.size} courses found. Hover any missing bar once.`:'Nothing was captured. Hover one chart bar, then scan again.';
    }catch{status='The scan was interrupted. Try once more or hover the bars manually.';}
    finally{if(token===epoch){scanning=false;render();}}
  }

  function downloadDiagnostics(){
    const report={version:'0.9.3',page:active()?'attendance':'other',coursesRead:rows.size,canvasCount:document.querySelectorAll('canvas').length,chartInstances:charts().length,canvasHook:CanvasRenderingContext2D.prototype.fillText.toString().includes('capture'),sources:[...new Set([...rows.values()].map(row=>row.source))],status};
    const url=URL.createObjectURL(new Blob([JSON.stringify(report,null,2)],{type:'application/json'}));
    const link=document.createElement('a');link.href=url;link.download='rollcall-diagnostics.json';link.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
  }
  function urgency(row){const result=C.calculate(row.attended,row.total);return result.recover?result.recover-1000:result.margin;}
  function render(){
    if(!root)return;
    host.hidden=!relevant();if(host.hidden)return;
    root.querySelector('#attendance-view').hidden=view!=='attendance';root.querySelector('#today-view').hidden=view!=='today';
    for(const b of root.querySelectorAll('[data-view]'))b.setAttribute('aria-pressed',String(b.dataset.view===view));
    renderToday();
    root.querySelector('#freshness').textContent=capturedAt?'Latest attendance capture '+new Date(capturedAt).toLocaleTimeString([], {hour:'2-digit',minute:'2-digit'})+' · '+rows.size+' courses · visible data only':'Attendance not captured in this session.';
    root.querySelector('#scan').textContent=scanning?'Stop scan':'Scan attendance';
    root.querySelector('#status').textContent=status;
    const list=root.querySelector('#list');list.replaceChildren();
    if(!rows.size){
      const empty=el('div',undefined,'empty');empty.append(el('b','No attendance loaded'),el('p','Open Subject-wise and scan the chart.'));list.append(empty);return;
    }
    for(const row of [...rows.values()].filter(row=>(row.code+' '+row.name).toLowerCase().includes(query)&&(!riskOnly||(row.total>0&&4*row.attended-3*row.total<3))).sort((a,b)=>urgency(a)-urgency(b)||a.code.localeCompare(b.code))){
      const result=C.calculate(row.attended,row.total),percentage=result.percentage===null?'—':result.percentage.toFixed(2)+'%';
      const state=result.percentage===null?'neutral':result.recover?'danger':result.margin===0?'warn':'good';
      const message=result.percentage===null?'No classes recorded yet.':result.recover===Infinity?'100% can only be reached when no classes have been missed.':result.recover?`Attend the next ${result.recover} ${result.recover===1?'period':'periods'} to reach 75%.`:result.margin?`You can miss ${result.margin} more ${result.margin===1?'period':'periods'}; the next one drops you below 75%.`:'Missing the next period drops you below 75%.';
      const card=el('article',undefined,state),head=el('div',undefined,'course-head'),identity=el('div');identity.append(el('small',row.code),el('h3',row.name));head.append(identity,el('b',percentage,'percent'));
      card.append(head,el('p',`${row.attended} of ${row.total} periods attended`),el('strong',message));
      const progress=el('progress');progress.max=100;progress.value=result.percentage||0;progress.setAttribute('aria-label',row.name+' attendance');card.append(progress);
      const preview=el('details',undefined,'preview');preview.append(el('summary','What if I…'));
      for(const [label,a,t] of [['Attend next',1,1],['Miss next',0,1]]){
        const projected=C.calculate(row.attended+a,row.total+t);
        preview.append(el('p',label+' → '+projected.percentage.toFixed(2)+'%'+(projected.recover?' · below target':'')));
      }
      card.append(preview);list.append(card);
    }
    if(!list.children.length)list.append(el('p','No courses match this filter.','status'));
  }
  function readTimetable(){
    if(!/\/timetable\/?$/i.test(location.pathname)){timetable={date:null,events:[],reason:'Open MyCamu → Timetable, choose today, then Read day.'};tableAt=null;render();return;}
    timetable=T.readPage(document);tableAt=Date.now();render();
  }
  function renderToday(){
    const area=root.querySelector('#day-list');if(!area)return;area.replaceChildren();
    if(!timetable){area.append(el('p','Open MyCamu’s daily Timetable and select today, then Read day.','status'));return;}
    area.append(el('p',(timetable.date||'Date unavailable')+(tableAt?' · Read '+new Date(tableAt).toLocaleTimeString([], {hour:'2-digit',minute:'2-digit'}):''),'status'));
    area.append(el('p',timetable.reason,'status'));
    if(timetable.date&&timetable.date!==T.today())area.append(el('strong','This is not today’s schedule. Select today and read again.'));
    if(timetable.holiday)area.append(el('strong','Holiday · '+timetable.holiday));
    const now=new Date(),next=timetable.date===T.today()?T.nextEvent(timetable.events,now.getHours()*60+now.getMinutes()):null;
    for(const event of timetable.events){
      const row=rows.get(event.code),fresh=row&&Date.now()-row.capturedAt<15*60*1000,r=fresh?C.calculate(row.attended,row.total):null;
      const card=el('article',undefined,r?.recover?'danger':r?.margin===0?'warn':'neutral');
      card.append(el('small',event.label+(event===next?(event.start<=now.getHours()*60+now.getMinutes()?' · NOW':' · NEXT'):'')),el('h3',event.name),el('p',event.code));
      if(r&&r.percentage!==null)card.append(el('strong',r.percentage.toFixed(2)+'% · '+(r.recover?'Attend '+r.recover+' periods to reach 75%':r.margin===0?'Next missed period drops below 75%':r.margin+' additional absences available')));
      else card.append(el('p',row?'Attendance is stale or empty. Scan attendance again.':'Scan attendance to match this course by code.'));
      area.append(card);
    }
  }
  function enableDrag(handle){
    let drag=null;
    handle.addEventListener('pointerdown',e=>{if(e.button!==0)return;const rect=host.getBoundingClientRect();drag={x:e.clientX,y:e.clientY,left:rect.left,top:rect.top};handle.setPointerCapture(e.pointerId);});
    handle.addEventListener('pointermove',e=>{if(!drag)return;host.style.right='auto';host.style.bottom='auto';host.style.left=Math.max(0,Math.min(innerWidth-host.offsetWidth,drag.left+e.clientX-drag.x))+'px';host.style.top=Math.max(0,Math.min(innerHeight-host.offsetHeight,drag.top+e.clientY-drag.y))+'px';});
    for(const event of ['pointerup','pointercancel'])handle.addEventListener(event,()=>{drag=null;});
    const reset=()=>{host.style.left='';host.style.top='';host.style.right='';host.style.bottom='';};
    handle.ondblclick=reset;handle.onkeydown=e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();reset();}};
    window.addEventListener('resize',reset);
  }
  function mount(){
    host=el('div');host.id='rollcall-attendance-checker';document.body.append(host);root=host.attachShadow({mode:'closed'});
    const style=el('style');style.textContent=globalThis.RollcallStyles||'';root.append(style);
    const shell=el('details');shell.id='rollcall-shell';shell.open=true;
    const graphic=(data)=>{const n=document.createElementNS('http://www.w3.org/2000/svg',data.tag);for(const [key,value] of Object.entries(data.attrs))n.setAttribute(key,value);for(const child of data.children)n.append(graphic(child));return n;};
    const brand=el('summary',undefined,'brand'),mark=el('span',undefined,'brand-mark'),lockup=el('span',undefined,'brand-lockup'),name=el('span',undefined,'brand-name');
    if(globalThis.RollcallBrand){mark.append(graphic(globalThis.RollcallBrand.logo));name.append(graphic(globalThis.RollcallBrand.wordmark));}else{mark.textContent='R';name.textContent='Rollcall';}
    mark.setAttribute('aria-hidden','true');name.setAttribute('aria-label','Rollcall');name.setAttribute('role','img');
    const credit=el('span',undefined,'brand-credit');credit.append(el('span','by'),el('b','iniexe'));lockup.append(name,credit);brand.append(mark,lockup,el('span','','collapse-hint'));shell.append(brand);
    const art=el('div',undefined,'art-strip');art.setAttribute('aria-hidden','true');for(const cls of ['stripes','checks','orbit','fan'])art.append(el('i',undefined,cls));shell.append(art);
    const tabs=el('nav',undefined,'view-tabs');tabs.setAttribute('aria-label','Rollcall views');for(const [key,label] of [['attendance','Attendance'],['today','Today']]){const b=el('button',label);b.dataset.view=key;b.onclick=()=>{view=key;render();};tabs.append(b);}const drag=el('button','⠿ Move','drag-handle');drag.title='Drag to move. Double-click or press Enter to reset position.';tabs.append(drag);shell.append(tabs);enableDrag(drag);
    const body=el('section'),intro=el('div',undefined,'intro'),copy=el('div',undefined,'intro-copy'),scanButton=el('button','Scan attendance');copy.append(el('b','75% attendance checker'),el('p','See your safe absence limit at a glance.'));scanButton.id='scan';scanButton.onclick=scan;intro.append(copy,scanButton);body.append(intro);
    body.id='attendance-view';const fresh=el('p','','status');fresh.id='freshness';body.append(fresh);
    const statusNode=el('p',status,'status');statusNode.id='status';statusNode.setAttribute('role','status');body.append(statusNode);
    const controls=el('div',undefined,'controls'),search=el('input');search.type='search';search.placeholder='Find a course';search.setAttribute('aria-label','Find a course');search.oninput=()=>{query=search.value.toLowerCase().trim();render();};
    const filter=el('button','At risk');filter.setAttribute('aria-pressed','false');filter.onclick=()=>{riskOnly=!riskOnly;filter.setAttribute('aria-pressed',String(riskOnly));render();};controls.append(search,filter);body.append(controls);
    const list=el('div');list.id='list';body.append(list);
    const tools=el('details',undefined,'tools');tools.append(el('summary','Troubleshooting'));
    const reset=el('button','Clear results');reset.onclick=()=>{timetable=null;tableAt=null;clear();};const diagnostics=el('button','Download diagnostics');diagnostics.onclick=downloadDiagnostics;tools.append(reset,diagnostics);body.append(tools);
    const day=el('section');day.id='today-view';const read=el('button','Read day');read.onclick=readTimetable;day.append(read);const dayList=el('div');dayList.id='day-list';day.append(dayList);shell.append(body,day,el('footer','Rollcall 0.9.3 · by iniexe · Periods, not days.'));root.append(shell);render();
    // Keep memory only while moving directly between attendance and timetable.
    document.addEventListener('change',e=>{if(e.target?.matches?.('select')){timetable=null;tableAt=null;clear('Selection changed. Capture current attendance and timetable again.');}},true);
    document.addEventListener('click',e=>{if(/^(log\s*out|sign\s*out)$/i.test(e.target?.textContent?.trim()||'')){timetable=null;tableAt=null;clear('Session ended.');}},true);
    setInterval(()=>{if(location.href!==lastURL){const previous=new URL(lastURL);lastURL=location.href;epoch++;scanning=false;
      if(!relevant()||!/^\/(?:v2\/)?(?:attendance|timetable)\/?$/i.test(previous.pathname)||previous.search!==location.search){timetable=null;tableAt=null;clear('Context changed. Capture current data again.');}
      view=/\/timetable\/?$/i.test(location.pathname)?'today':'attendance';render();}
      readVisibleTooltips();if(view==='today')renderToday();},1200);

  }
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',mount,{once:true});else mount();
})();

