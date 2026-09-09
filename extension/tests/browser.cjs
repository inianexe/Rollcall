const {chromium}=require('playwright');
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
(async()=>{
  const browser=await chromium.launch({headless:true,args:['--no-sandbox']});
  try{
    const page=await browser.newPage({viewport:{width:1300,height:850}}),errors=[];
    page.on('pageerror',error=>errors.push(error.message));
    await page.addInitScript({content:`const attach=Element.prototype.attachShadow;Element.prototype.attachShadow=function(options){return attach.call(this,{...options,mode:'open'})};\n`+fs.readFileSync(path.join(__dirname,'../core.js'),'utf8')+'\n'+fs.readFileSync(path.join(__dirname,'../ui.js'),'utf8')+'\n'+fs.readFileSync(path.join(__dirname,'../branding.js'),'utf8')+'\n'+fs.readFileSync(path.join(__dirname,'../timetable.js'),'utf8')+'\n'+fs.readFileSync(path.join(__dirname,'../companion.js'),'utf8')});
    await page.route('https://www.mycamu.co.in/**',route=>route.fulfill({contentType:'text/html',body:`<html><body><canvas width="700" height="300"></canvas><script>const canvas=document.querySelector('canvas'),ctx=canvas.getContext('2d');ctx.fillRect(20,40,500,30);ctx.fillRect(20,100,400,30);canvas.addEventListener('mousemove',event=>{const y=event.clientY-canvas.getBoundingClientRect().top;if(y>=40&&y<=70)ctx.fillText('FRESH01 - New subject : 88% - (23/26)',30,200);if(y>=100&&y<=130)ctx.fillText('FRESH01L - New lab : 73% - (19/26)',30,230);});</script></body></html>`}));
    await page.goto('https://www.mycamu.co.in/v2/attendance');
    await page.getByRole('button',{name:'Scan attendance',exact:true}).click();
    await page.getByText('2 courses found. Hover any missing bar once.',{exact:true}).waitFor({timeout:12000});
    assert.equal(await page.locator('article').count(),2);
    assert.match(await page.locator('#list').innerText(),/You can miss 4 more periods/);
    assert.match(await page.locator('#list').innerText(),/Attend the next 2 periods/);
    await page.getByRole('button',{name:'At risk',exact:true}).click();
    assert.equal(await page.locator('#list article').count(),1);
    await page.getByRole('button',{name:'At risk',exact:true}).click();
    await page.getByRole('button',{name:'Today',exact:true}).click();
    assert.equal(await page.locator('#today-view').isVisible(),true);
    await page.evaluate(()=>{history.pushState({},'', '/v2/timetable');const content=document.createElement('main');content.innerText='09 Sep 2026\\nNew subject (FRESH01)\\n09:00 AM - 10:00 AM';document.body.append(content);});
    await page.waitForTimeout(1400);
    await page.getByRole('button',{name:'Read day',exact:true}).click();
    assert.match(await page.locator('#day-list').innerText(),/88.46%/);
    fs.mkdirSync(path.join(__dirname,'../../docs/evidence'),{recursive:true});
    await page.screenshot({path:path.join(__dirname,'../../docs/evidence/synthetic-today.png')});
    assert.deepEqual(errors,[]);
    console.log('PASS: synthetic attendance, filtering and Today integration');
  }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
