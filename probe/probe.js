const { chromium } = require('playwright');
const fs = require('fs');

const URL = process.env.PROBE_URL;
const kt = fs.readFileSync('app/src/main/java/com/mnb/browser/extraction/ExtractionScript.kt', 'utf8');
const from = kt.indexOf('"""', kt.indexOf('val SOURCE')) + 3;
const to = kt.indexOf('""".trimIndent()');
const SOURCE = kt.slice(from, to).trim().replace(/;\s*$/, '');

function summarize(r) {
  if (!r) return null;
  if (r.error) return { error: r.error };
  const ps = r.paragraphs || [];
  return {
    chapterTitle: r.chapterTitle,
    paragraphCount: ps.length,
    totalChars: ps.reduce((a, p) => a + p.t.length, 0),
    firstLengths: ps.slice(0, 8).map((p) => p.t.length),
    oddChars: (function () {
      const h = {};
      for (const p of ps) for (const ch of p.t) {
        const cp = ch.codePointAt(0);
        if (cp > 127 || cp < 32) { const k = 'U+' + cp.toString(16).toUpperCase(); h[k] = (h[k] || 0) + 1; }
      }
      return h;
    })(),
  };
}

const STRUCTURE = function () {
  function desc(e) {
    if (!e || !e.tagName) return '';
    var cls = typeof e.className === 'string' ? e.className.trim().split(/\s+/).slice(0, 4).join('.') : '';
    return e.tagName.toLowerCase() + (e.id ? '#' + e.id : '') + (cls ? '.' + cls : '');
  }
  function chain(e, n) {
    var a = [];
    for (var x = e; x && a.length < n; x = x.parentElement) a.push(desc(x));
    return a.join(' < ');
  }
  var info = {};
  var text = document.body.innerText || '';
  info.bodyTextLength = text.length;
  info.pCount = document.querySelectorAll('p').length;
  info.iframes = document.querySelectorAll('iframe').length;
  info.canvases = document.querySelectorAll('canvas').length;
  var all = document.querySelectorAll('*'), sh = 0;
  for (var i = 0; i < all.length; i++) if (all[i].shadowRoot) sh++;
  info.shadowRoots = sh;
  var low = text.toLowerCase();
  info.flags = {};
  ['log in', 'login', 'sign in', 'unlock', 'captcha', 'just a moment', 'verify you are human', 'premium', 'subscribe', 'locked'].forEach(function (k) {
    info.flags[k] = low.indexOf(k) >= 0;
  });
  var blocks = [];
  var els = document.body.querySelectorAll('*');
  for (var j = 0; j < els.length; j++) {
    var own = 0;
    for (var c = els[j].firstChild; c; c = c.nextSibling) if (c.nodeType === 3) own += c.nodeValue.trim().length;
    if (own > 60) blocks.push({ el: els[j], own: own });
  }
  info.textBlockCount = blocks.length;
  info.textBlocks = blocks.slice(0, 10).map(function (b) { return { own: b.own, chain: chain(b.el, 6) }; });
  if (blocks.length) {
    var par = blocks[0].el.parentElement;
    info.firstBlockParentChildCount = par.children.length;
    info.firstBlockParentChildren = Array.prototype.slice.call(par.children, 0, 12).map(function (k) {
      return { d: desc(k), textLen: (k.innerText || '').length, hidden: getComputedStyle(k).display === 'none' };
    });
  }
  return info;
};

(async () => {
  const result = { url: URL, startedAt: new Date().toISOString() };
  let browser;
  try {
    browser = await chromium.launch();
    const ctx = await browser.newContext({
      userAgent: 'Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36',
      viewport: { width: 412, height: 915 },
      isMobile: true,
      locale: 'en-US',
    });
    const page = await ctx.newPage();
    try {
      const resp = await page.goto(URL, { waitUntil: 'domcontentloaded', timeout: 45000 });
      result.status = resp ? resp.status() : null;
    } catch (e) {
      result.gotoError = String(e);
    }
    await page.waitForTimeout(8000);
    result.title = await page.title().catch(() => null);
    result.extractBeforeScroll = summarize(await page.evaluate(SOURCE));
    result.perScrollStep = [];
    for (let i = 0; i < 40; i++) {
      await page.evaluate(() => window.scrollBy(0, window.innerHeight));
      await page.waitForTimeout(400);
      if (i % 8 === 7) {
        const s = summarize(await page.evaluate(SOURCE));
        result.perScrollStep.push({ step: i + 1, paragraphCount: s && s.paragraphCount, error: s && s.error });
      }
    }
    result.extractAfterScroll = summarize(await page.evaluate(SOURCE));
    result.structure = await page.evaluate(STRUCTURE);
  } catch (e) {
    result.fatal = String(e);
  } finally {
    if (browser) await browser.close();
  }
  fs.mkdirSync('probe-results', { recursive: true });
  fs.writeFileSync('probe-results/result.json', JSON.stringify(result, null, 2));
  console.log(JSON.stringify(result, null, 2));
})();
