const {test}=require('node:test');
const assert=require('node:assert/strict');
const T=require('../timetable');
test('daily entries retain exact codes, times and date',()=>{
 const r=T.parseDaily('09 Sep 2026\nCircuits (EC101)\n09:00 AM - 10:00 AM\nCircuits Lab (EC101L)\n12:00 PM – 01:30 PM');
 assert.equal(r.date,'2026-09-09');assert.equal(r.events.length,2);assert.equal(r.events[1].code,'EC101L');assert.equal(r.events[1].start,720);
});
test('ambiguous dates and invalid dates do not produce schedules',()=>{
 assert.equal(T.parseDaily('09 Sep 2026\n10 Sep 2026').date,null);assert.equal(T.dateKey('31 Feb 2026'),null);
});
test('empty timetable is not a holiday and unread times are flagged',()=>{
 assert.equal(T.parseDaily('09 Sep 2026').holiday,null);
 assert.match(T.parseDaily('09 Sep 2026\nCircuits (EC101)\nunknown').reason,/incomplete/);
});
test('explicit holiday conflicts with class data are rejected',()=>{
 assert.equal(T.parseDaily('09 Sep 2026\nToday: Holiday: Festival').holiday,'Festival');
 assert.equal(T.parseDaily('09 Sep 2026\nToday: Holiday: Festival\nCircuits (EC101)\n09:00 AM - 10:00 AM').events.length,0);
});
test('next class includes ongoing class and excludes ended classes',()=>{
 const events=[{start:540,end:600},{start:660,end:720}];
 assert.equal(T.nextEvent(events,570),events[0]);assert.equal(T.nextEvent(events,600),events[1]);assert.equal(T.nextEvent(events,720),null);
});
test('recording layout accepts spaced codes, room suffix and separate repeated periods',()=>{
 const r=T.parseDaily('11-Sep-2026\nOBJECT ORIENTED PROGRAMMING ( 24CSI025L ) ( ADM413 )\n01:40 PM - 02:40 PM (60 min) Example staff\nPractical\nOBJECT ORIENTED PROGRAMMING ( 24CSI025L ) ( ADM413 )\n02:40 PM - 03:40 PM (60 min) Example staff');
 assert.equal(r.events.length,2);assert.equal(r.events[0].code,'24CSI025L');assert.equal(r.events[0].name,'OBJECT ORIENTED PROGRAMMING');
});
const input=value=>({value,getClientRects:()=>[{}],closest:()=>null});
test('selected date is read from input value absent from innerText',()=>{
 const doc={querySelectorAll:()=>[input('11-Sep-2026')],body:{innerText:'Course ( CODE123 )\n09:30 AM - 10:30 AM'}};
 assert.equal(T.readPage(doc).date,'2026-09-11');assert.equal(T.readPage(doc).events.length,1);
});
test('ISO date input works; multiple dates are rejected',()=>{
 assert.equal(T.readPage({querySelectorAll:()=>[input('2026-09-11')],body:{innerText:''}}).date,'2026-09-11');
 assert.equal(T.readPage({querySelectorAll:()=>[input('2026-09-11'),input('2026-09-12')],body:{innerText:''}}).date,null);
});
