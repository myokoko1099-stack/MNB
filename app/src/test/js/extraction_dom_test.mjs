// DOM-level test of the injected JavaScript (ExtractionScript.kt) in a real Chromium.
// Not part of the Gradle/CI run. Usage:  node extraction_dom_test.mjs
// Requires: npm i playwright (and a Chromium install).
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT_PATH || 'playwright');

const here = dirname(fileURLToPath(import.meta.url));
const kt = readFileSync(join(here, '../../main/java/com/mnb/browser/extraction/ExtractionScript.kt'), 'utf8');
const SCRIPT = kt.split('"""')[1];

const NOVEL = `<!doctype html><html><head><title>Novel Site - Chapter 5</title></head><body>
<header><a href="/">Home</a> <a href="/list">Novels</a></header>
<nav class="main-menu"><a href="/a">Genres</a><a href="/b">Rankings</a></nav>
<div class="ads adsbygoogle">BUY NOW!!! Advertisement</div>
<div id="sidebar"><p>Popular novels sidebar text that is long enough to look like a real paragraph of text.</p></div>
<main><article>
  <h1>အခန်း ၅ - ခရီးစတင်ခြင်း</h1>
  <h2>Part One</h2>
  <p>မင်္ဂလာပါ။ ဒီနေ့ ကျွန်တော် ခရီးထွက်မယ်။ နေရောင်က လင်းလင်းထိန်ထိန် ရှိနေတယ်။</p>
  <p>   Second   paragraph with   odd   spacing and enough text to count as content here.  </p>
  <p style="display:none">Hidden paragraph that must never appear in the result at all.</p>
  <p hidden>Another hidden paragraph that must never appear in the result either way.</p>
  <p>Second paragraph with odd spacing and enough text to count as content here.</p>
  <p>Third paragraph, a unique one, containing different words to stay in the output.</p>
  <button>Next Chapter</button>
  <p>Third paragraph, a unique one, containing different words to stay in the output.</p>
  <p>“Yes.”</p><p>“Yes.”</p><p>Narration between.</p><p>“Yes.”</p>
  <div class="comments"><p>Great chapter!!! Thanks for the update, I really loved reading this one today.</p></div>
</article></main>
<footer><p>Copyright 2026 Novel Site. All rights reserved. Contact us for more information.</p></footer>
</body></html>`;

const BR_PAGE = `<!doctype html><html><head><title>BR page</title></head><body>
<div id="content">Line one of the story, long enough to count as a paragraph of text.<br><br>
Line two of the story, also long enough to be treated as its own paragraph.<br>
<span>Line three has <b>inline bold</b> and <i>italic</i> parts inside the same paragraph line.</span></div>
</body></html>`;

const FORM_PAGE = `<!doctype html><html><head><title>Form wrap</title></head><body><form id="f">
<div class="wrap has-sidebar"><div class="content">
<p>First paragraph inside an ASP.NET style form wrapper, which must still be extracted.</p>
<p>Second paragraph inside the form wrapper, also long enough to be considered content.</p>
</div></div><input type="submit" value="Go"></form></body></html>`;

const EMPTY_PAGE = `<!doctype html><html><head><title>Empty</title></head><body>
<nav><a href="/x">Menu</a></nav><header>Site</header><footer>Foot</footer><button>Click</button></body></html>`;


const BLOCK_SPANS = `<!doctype html><html><head><title>Book - WebNovel</title></head><body>
<div class="g_wrap"><header><h1>Book Name Here</h1></header>
<div class="cha-content"><h3 class="cha-tit"><span style="display:block">Chapter 1</span><span style="display:block">Chapter 1: Prologue</span></h3>
<p>This was definitely not a game. Of course, it wasn't a dream, either, was it?</p>
<p>But her voice definitely resounded within my head, loud and clear.</p></div></div></body></html>`;

const BOOK_H1 = `<!doctype html><html><head><title>Site</title></head><body>
<main><h1>Book Name As H1</h1><div class="chapter-title">Chapter 7: The Gate</div>
<p>First long paragraph of the story which is certainly long enough to count here.</p>
<p>Second long paragraph of the story which is certainly long enough to count too.</p></main></body></html>`;

const NO_TITLE = `<!doctype html><html><head><title>Site</title></head><body>
<div class="c"><p>Chapter 12 - Return</p><p>First long paragraph of the story which is certainly long enough to count here.</p></div></body></html>`;

const BARE_LABEL_A = `<!doctype html><html><head><title>Book</title></head><body><div class="c">
<div style="display:block">Chapter 1</div><div>Chapter 1: Prologue</div><div>Part 0: Prologue</div>
<p>This was definitely not a game. Of course, it wasn't a dream, either, was it?</p></div></body></html>`;
const BARE_LABEL_B = `<!doctype html><html><head><title>Book</title></head><body><div class="c">
<span style="display:block">Chapter 1</span><span style="display:block">Nightmare Begins</span>
<p>A frail-looking young man with pale skin and dark circles under his eyes sat there.</p></div></body></html>`;

const SHORT_TITLE = `<!doctype html><html><head><title>Book</title></head><body><div class="c">
<p>Chapter 5 Gate</p><p>Yes, he said quietly, and it was enough for them both to leave at once.</p></div></body></html>`;

const browser = await chromium.launch();
const results = [];
async function run(name, html, check) {
  const page = await browser.newPage();
  await page.setContent(html);
  const r = await page.evaluate(SCRIPT);
  await page.close();
  try { check(r); results.push(['PASS', name]); }
  catch (e) { results.push(['FAIL', name + ' -> ' + e.message]); console.error(JSON.stringify(r, null, 1)); }
}
const texts = r => r.paragraphs.map(p => p.t);

await run('novel: title/chapter/url fields', NOVEL, r => {
  assert.equal(r.pageTitle, 'Novel Site - Chapter 5');
  assert.equal(r.chapterTitle, 'အခန်း ၅ - ခရီးစတင်ခြင်း');
  assert.ok(r.url.length > 0);
});
await run('novel: removes nav/ads/header/footer/button/hidden/sidebar/comments', NOVEL, r => {
  const all = texts(r).join('\n');
  for (const bad of ['BUY NOW', 'Genres', 'Home', 'Novels', 'Next Chapter', 'Hidden paragraph', 'Another hidden', 'Copyright', 'Popular novels', 'Great chapter'])
    assert.ok(!all.includes(bad), 'leaked: ' + bad);
});
await run('novel: keeps order, headings and original Burmese text', NOVEL, r => {
  const t = texts(r);
  assert.deepEqual(r.paragraphs.slice(0, 2).map(p => [p.t, p.h]), [['အခန်း ၅ - ခရီးစတင်ခြင်း', 1], ['Part One', 2]]);
  assert.equal(t[2], 'မင်္ဂလာပါ။ ဒီနေ့ ကျွန်တော် ခရီးထွက်မယ်။ နေရောင်က လင်းလင်းထိန်ထိန် ရှိနေတယ်။');
  assert.ok(t.indexOf('Second paragraph with odd spacing and enough text to count as content here.') < t.indexOf('Third paragraph, a unique one, containing different words to stay in the output.'));
});
await run('novel: whitespace collapsed by script', NOVEL, r => {
  assert.ok(texts(r).includes('Second paragraph with odd spacing and enough text to count as content here.'));
});
await run('br-separated text becomes separate paragraphs, inline kept together', BR_PAGE, r => {
  const t = texts(r);
  assert.equal(t.length, 3, JSON.stringify(t));
  assert.ok(t[2].includes('inline bold') && t[2].includes('italic'));
});
await run('form wrapper + has-sidebar class still extracts', FORM_PAGE, r => {
  assert.equal(texts(r).length, 2, JSON.stringify(texts(r)));
});
await run('page with only chrome yields no paragraphs', EMPTY_PAGE, r => {
  assert.equal(r.paragraphs.length, 0, JSON.stringify(r.paragraphs));
});

await run('display:block spans are split into separate lines (no "Chapter 1Chapter 1")', BLOCK_SPANS, r => {
  const t = texts(r);
  assert.equal(t[0], 'Chapter 1', JSON.stringify(t));
  assert.equal(t[1], 'Chapter 1: Prologue');
  assert.ok(!t.some(x => x.includes('Chapter 1Chapter')));
});
await run('chapter title comes from the title element, not the book h1 in header', BLOCK_SPANS, r => {
  assert.equal(r.chapterTitle, 'Chapter 1 Chapter 1: Prologue');
  assert.ok(!r.chapterTitle.includes('Book Name'));
});
await run('chapter title prefers .chapter-title over a bare h1', BOOK_H1, r => {
  assert.equal(r.chapterTitle, 'Chapter 7: The Gate');
});
await run('chapter title falls back to a leading "Chapter N" line', NO_TITLE, r => {
  assert.equal(r.chapterTitle, 'Chapter 12 - Return');
});

await run('bare "Chapter 1" is completed by the following line that repeats it', BARE_LABEL_A, r => {
  assert.equal(r.chapterTitle, 'Chapter 1: Prologue');
});
await run('bare "Chapter 1" is joined with a following title line', BARE_LABEL_B, r => {
  assert.equal(r.chapterTitle, 'Chapter 1 Nightmare Begins');
});

await run('a full short title like "Chapter 5 Gate" is not extended', SHORT_TITLE, r => {
  assert.equal(r.chapterTitle, 'Chapter 5 Gate');
});

await browser.close();
for (const [s, n] of results) console.log(s, n);
const failed = results.filter(r => r[0] === 'FAIL').length;
console.log(`${results.length - failed}/${results.length} passed`);
process.exit(failed ? 1 : 0);
