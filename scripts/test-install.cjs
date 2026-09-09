const { chromium } = require('playwright');
const path = require('node:path');
const fs = require('node:fs');
const os = require('node:os');
(async () => {
  const extension = path.resolve(process.argv[2]);
  const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'rollcall-check-'));
  let context;
  try {
    context = await chromium.launchPersistentContext(profile, {
      channel: 'chromium', headless: true,
      args: [`--disable-extensions-except=${extension}`, `--load-extension=${extension}`]
    });
    await context.route('https://www.mycamu.co.in/**', route => route.fulfill({
      contentType: 'text/html', body: '<html><body><h1>Synthetic installation check</h1></body></html>'
    }));
    const page = await context.newPage();
    await page.goto('https://www.mycamu.co.in/v2/attendance');
    await page.locator('#rollcall-attendance-checker').waitFor({state:'visible',timeout:15000});
    console.log('PASS: Chromium loaded the extracted extension manifest and mounted Rollcall via its content scripts.');
  } finally {
    if (context) await context.close();
    fs.rmSync(profile,{recursive:true,force:true});
  }
})().catch(e => { console.error(e); process.exitCode = 1; });
