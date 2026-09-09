(function(root){
'use strict';
const months=['jan','feb','mar','apr','may','jun','jul','aug','sep','oct','nov','dec'];
function dateKey(text){const m=String(text).match(/\b(\d{1,2})[- /](Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[- /](\d{4})\b/i);if(!m)return null;const year=+m[3],month=months.indexOf(m[2].toLowerCase()),day=+m[1],d=new Date(year,month,day);return d.getFullYear()===year&&d.getMonth()===month&&d.getDate()===day?`${year}-${String(month+1).padStart(2,'0')}-${String(day).padStart(2,'0')}`:null;}
function today(now=new Date()){return `${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,'0')}-${String(now.getDate()).padStart(2,'0')}`;}
function minutes(h,m,ap){h=+h;m=+m;if(m>59||h<1||h>12)return null;return h%12*60+m+(ap.toLowerCase()==='pm'?720:0);}
function timeRange(text){const m=text.match(/(\d{1,2}):(\d{2})\s*(AM|PM)\s*[-–—]\s*(\d{1,2}):(\d{2})\s*(AM|PM)/i);if(!m)return null;const start=minutes(m[1],m[2],m[3]),end=minutes(m[4],m[5],m[6]);return start!==null&&end!==null&&end>start?{start,end,label:m[0]}:null;}
function parseDaily(text, selectedDate=null){
 const dates=[...new Set((text.match(/\b\d{1,2}[- /](?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[- /]\d{4}\b/gi)||[]).map(dateKey).filter(Boolean))];
 if(selectedDate){dates.splice(0,dates.length,selectedDate);}
 if(dates.length!==1)return {date:null,events:[],holiday:null,reason:'Open the daily timetable with one selected date. Close Weekly Schedule first.'};
 const date=dates[0],holiday=text.match(/Today\s*:\s*Holiday\s*[:\-]\s*([^\n]+)/i);
 const lines=text.split(/\n/).map(x=>x.trim()).filter(Boolean),events=[];let pending=null,unread=0;
 const flush=()=>{if(pending){const t=timeRange(pending.lines.join(' '));if(t)events.push({code:pending.code,name:pending.name,...t,date});else unread++;}pending=null;};
 for(const line of lines){const m=line.match(/^(.+?)\s*\(\s*([A-Za-z0-9][A-Za-z0-9_.-]*\d[A-Za-z0-9_.-]*)\s*\)(?:\s*\([^()]*\))*\s*$/);if(m){flush();pending={name:m[1].trim(),code:m[2],lines:[]};}else if(pending)pending.lines.push(line);}
 flush();const seen=new Set();const unique=events.filter(e=>{const k=e.code+'|'+e.start+'|'+e.end;if(seen.has(k))return false;seen.add(k);return true;}).sort((a,b)=>a.start-b.start);
 if(holiday&&unique.length)return {date,events:[],holiday:null,reason:'The page contains both holiday and class entries. Check the selected date.'};
 return {date,events:unique,holiday:holiday?holiday[1].trim():null,reason:unread?`${unread} entries had unreadable times; captured list may be incomplete.`:unique.length?'Captured visible daily entries; verify against MyCamu.':holiday?'Holiday explicitly listed by MyCamu.':'No readable classes. This does not confirm a free day.'};
}
function readPage(doc){
 const dates=[];
 for(const input of doc.querySelectorAll('input')){
  if(!input.getClientRects().length||input.closest('#rollcall-attendance-checker'))continue;
  const value=String(input.value||'').trim();
  let date=dateKey(value);
  if(!date&&/^\d{4}-\d{2}-\d{2}$/.test(value)){
   const [y,m,d]=value.split('-').map(Number);
   if(m>=1&&m<=12)date=dateKey(`${d}-${months[m-1]}-${y}`);
  }
  if(date)dates.push(date);
 }
 const unique=[...new Set(dates)];
 if(unique.length>1)return {date:null,events:[],holiday:null,reason:'Several date controls are visible. Close the weekly schedule and select one day.'};
 return parseDaily(doc.body.innerText,unique[0]||null);
}
function nextEvent(events,nowMinutes){return events.find(e=>e.end>nowMinutes)||null;}
const api={dateKey,today,timeRange,parseDaily,readPage,nextEvent};if(typeof module!=='undefined'&&module.exports)module.exports=api;else root.RollcallTimetable=api;
})(globalThis);
