// CI DOM tests for DomReaderScript.kt. Playwright is installed only in the GitHub Actions job.
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const { chromium } = require('playwright');
const here = dirname(fileURLToPath(import.meta.url));
const kt = readFileSync(join(here, '../../main/java/com/mnb/browser/extraction/DomReaderScript.kt'), 'utf8');
const marker = '__MNB_DOM_READER_PAYLOAD__';
const start = kt.indexOf('private val SOURCE = """');
assert.ok(start >= 0, 'Kotlin DOM script source marker is present');
const from = kt.indexOf('\n', start) + 1;
const end = kt.indexOf('\n""".trimIndent()', from);
assert.ok(end > from, 'Kotlin DOM script has a closing delimiter');
const source = kt.slice(from, end).split('\n').map(line => line.replace(/^ {8}/, '')).join('\n');
assert.ok(source.includes(marker), 'DOM script has its payload placeholder');
const base = { sessionToken: 'test-session-1', documentToken: 'test-document-1', tabId: 'test-tab', navGeneration: 12 };
const invoke = (payload = {}) => source.replace(marker, JSON.stringify({ ...base, op: 'extract', ...payload }));

const fixtures = [
  ['Royal Road-like chapter', `<!doctype html><title>Book | Royal Road</title><body>
    <nav><p>Browse novels and ranking links belong to navigation only.</p></nav>
    <aside><p>This long sidebar recommendation is unrelated to the chapter content.</p></aside>
    <main><article id="chapter-content"><h1>Chapter 12: The Crossing</h1>
      <p>The first chapter paragraph has enough useful prose to be part of the story.</p>
      <p>The second chapter paragraph includes <em>inline emphasis</em> and remains one block.</p>
      <p style="display:none">Hidden story text must not be included in the result.</p>
      <div class="advertisement">Sponsored promotion should be filtered.</div>
    </article></main></body>`],
  ['Wattpad-like chapter', `<!doctype html><title>Story</title><body><div data-testid="story-content">
    <h2>Part One</h2><p>Wattpad-shaped story text is still found without a site selector.</p>
    <p>A second readable chapter paragraph continues the current scene.</p></div>
    <div class="sidebar"><p>Unrelated sidebar text is excluded from the extraction.</p></div></body>`],
  ['Wuxiaworld-like article', `<!doctype html><title>Chapter 7</title><body><main role="main"><article>
    <h1>第七章</h1><p>这是一个包含足够长度的中文段落，用来验证 CJK 文字仍然完整保留。</p>
    <p>Another long paragraph stays in the chapter body and not the site navigation.</p>
    </article></main><footer>Copyright and site links are not chapter text.</footer></body>`],
  ['Project Gutenberg-like book', `<!doctype html><title>Book</title><body><div id="content">
    <h2>CHAPTER I</h2><p>It was the best of times, it was the worst of times, and the prose continues here.</p>
    <p>Book text remains available from a generic document structure.</p></div></body>`],
  ['Standard Ebooks-like XHTML', `<!doctype html><title>Book</title><body><main><section>
    <h3>Chapter II</h3><p>The chapter begins with a sufficiently long paragraph of readable narrative prose.</p>
    <p>A second paragraph completes this standards-oriented ebook fixture.</p></section></main></body>`],
  ['NovelDex-like content wrapper', `<!doctype html><title>NovelDex page</title><body><div class="reader-container"><div class="chapter-content">
    <h1>အခန်း ၅</h1><p>မင်္ဂလာပါ။ ဒီနေ့ ကျွန်တော် ခရီးထွက်မယ်။ ဇာတ်လမ်းစာသားကို မူရင်းအတိုင်း ထိန်းသိမ်းပါ။</p>
    <p>Myanmar characters with zero width space: ကမ္​ဘာကြီး နှင့် မင်္ဂလာပါ။</p></div></div></body>`],
  ['generic br-separated chapter', `<!doctype html><title>Simple chapter</title><body><main><div id="chapter">
    First line of story text is long enough to be independently anchored here.<br>
    Second line of story text is also long enough to be independently anchored.
    </div></main></body>`],
];

const browser = await chromium.launch({ headless: true });
const passed = [];
async function test(name, body) {
  try { await body(); passed.push(['PASS', name]); }
  catch (error) { passed.push(['FAIL', `${name}: ${error.message}`]); }
}
async function fresh(html) {
  const page = await browser.newPage();
  await page.setContent(html);
  return page;
}
async function evaluate(page, payload = {}) {
  const result = await page.evaluate(script => (0, eval)(script), invoke(payload));
  if (!payload.op && result?.status === 'error') throw new Error(`DOM extraction returned ${JSON.stringify(result)}`);
  return result;
}

for (const [name, html] of fixtures) {
  await test(`${name}: extracts paragraphs, headings, and stable hashes`, async () => {
    const page = await fresh(html);
    const result = await evaluate(page);
    assert.equal(result.status, 'success');
    assert.equal(result.sessionToken, base.sessionToken);
    assert.equal(result.documentToken, base.documentToken);
    assert.equal(result.tabId, base.tabId);
    assert.equal(result.navGeneration, base.navGeneration);
    assert.ok(result.paragraphs.length >= 2);
    assert.ok(result.paragraphs.every(p => p.index >= 0 && /^[0-9a-f]{8}$/.test(p.textHash)));
    assert.ok(result.chapterConfidence >= 0 && result.chapterConfidence <= 1);
    await page.close();
  });
}

await test('filters hidden, navigation, sidebar, ads, links and duplicate paragraphs', async () => {
  const page = await fresh(`<!doctype html><body><nav><a href="/x">Genres and directory links</a></nav>
    <aside><p>Popular novels not part of the chapter and should not be returned here.</p></aside>
    <main><article><h1>Chapter 1</h1><p>First unique paragraph with enough length for extraction.</p>
    <p>Second unique paragraph with enough length for extraction.</p>
    <p>Second unique paragraph with enough length for extraction.</p>
    <p>“Yes.”</p><p>He nodded and walked away from the door slowly.</p><p>“Yes.”</p>
    <p hidden>Hidden chapter paragraph must be omitted.</p><div class="adsbygoogle">BUY A BOOK NOW</div></article></main></body>`);
  const r = await evaluate(page); const text = r.paragraphs.map(p => p.originalText).join('\n');
  assert.equal(r.paragraphs.filter(p => p.originalText.startsWith('Second unique')).length, 1);
  assert.equal(r.paragraphs.filter(p => p.originalText === '“Yes.”').length, 2);
  for (const excluded of ['Genres', 'Popular novels', 'Hidden chapter', 'BUY A BOOK']) assert.ok(!text.includes(excluded));
  await page.close();
});

await test('paragraph indices stay stable when lazy content is appended and extracted again', async () => {
  const page = await fresh(`<!doctype html><body><main><article><p>Original paragraph has enough text for the chapter reader.</p>
    <p>Second original paragraph has enough text for the chapter reader.</p></article></main></body>`);
  const first = await evaluate(page); const old = first.paragraphs.map(p => p.index);
  await page.evaluate(() => { const p = document.createElement('p'); p.textContent = 'Lazy paragraph added later also contains enough useful chapter text.'; document.querySelector('article').appendChild(p); });
  await page.waitForTimeout(20);
  const next = await evaluate(page);
  assert.deepEqual(next.paragraphs.slice(0, 2).map(p => p.index), old);
  assert.ok(next.paragraphs.some(p => p.originalText.startsWith('Lazy paragraph')));
  assert.ok(next.observer.mutationCount > 0);
  await page.close();
});

await test('br-separated lines receive distinct text-node anchors', async () => {
  const page = await fresh(`<!doctype html><body><main><div id="lines">First line of text is long enough to form its own paragraph.<br>
    Second line of text is also long enough to form a separate paragraph.<br>
    Third line of text remains a separate readable paragraph.</div></main></body>`);
  const r = await evaluate(page);
  assert.equal(r.paragraphs.filter(p => p.originalText.includes('line of text')).length, 3);
  assert.equal(new Set(r.paragraphs.map(p => p.index)).size, r.paragraphs.length);
  await page.close();
});

await test('resolves anchors and reports stale session, wrong document, changed and disconnected nodes', async () => {
  const page = await fresh(`<!doctype html><body><main><p id="one">A stable paragraph with enough readable content.</p>
    <p id="two">A second stable paragraph with enough readable content.</p></main></body>`);
  const extraction = await evaluate(page); const p = extraction.paragraphs[0];
  assert.equal((await evaluate(page, { op: 'resolve', index: p.index, textHash: p.textHash })).status, 'resolved');
  assert.equal((await evaluate(page, { op: 'resolve', index: p.index, textHash: 'deadbeef' })).status, 'hash_mismatch');
  assert.equal((await evaluate(page, { op: 'resolve', index: 9999, textHash: p.textHash })).status, 'index_not_found');
  assert.equal((await evaluate(page, { sessionToken: 'another-session', op: 'resolve', index: p.index, textHash: p.textHash })).status, 'stale_session');
  assert.equal((await evaluate(page, { documentToken: 'another-document', op: 'resolve', index: p.index, textHash: p.textHash })).status, 'wrong_document');
  assert.equal((await evaluate(page, { navGeneration: 13, op: 'resolve', index: p.index, textHash: p.textHash })).status, 'wrong_document');
  await page.evaluate(() => { document.querySelector('#one').textContent = 'Page changed this paragraph after extraction.'; });
  const changed = await evaluate(page, { op: 'replace', paragraphs: [{ index: p.index, textHash: p.textHash, translatedText: 'translated' }] });
  assert.equal(changed.results[0].status, 'text_changed');
  await page.evaluate(() => document.querySelector('#two').remove());
  const p2 = extraction.paragraphs[1];
  const removed = await evaluate(page, { op: 'replace', paragraphs: [{ index: p2.index, textHash: p2.textHash, translatedText: 'translated' }] });
  assert.equal(removed.results[0].status, 'node_disconnected');
  await page.close();
});

await test('replacement preserves exact source, is idempotent, and restore is exact', async () => {
  const original = '  Original paragraph has   odd spacing and enough readable text.  ';
  const page = await fresh(`<!doctype html><body><main><p id="target">${original}</p></main></body>`);
  const extraction = await evaluate(page); const p = extraction.paragraphs[0];
  const request = { op: 'replace', paragraphs: [{ index: p.index, textHash: p.textHash, translatedText: 'ဘာသာပြန်ထားသော စာသား' }] };
  const applied = await evaluate(page, request);
  assert.equal(applied.results[0].status, 'translated');
  assert.equal(await page.locator('#target').textContent(), 'ဘာသာပြန်ထားသော စာသား');
  assert.equal((await evaluate(page, request)).results[0].status, 'already_translated');
  const restored = await evaluate(page, { op: 'restore' });
  assert.equal(restored.results[0].status, 'restored');
  assert.equal(await page.locator('#target').textContent(), original);
  assert.equal((await evaluate(page, { op: 'restore' })).results[0].status, 'restored');
  await page.close();
});

await test('rejects a second replacement after source text is modified', async () => {
  const page = await fresh(`<!doctype html><body><main><p id="target">Original source paragraph remains long enough to be anchored safely.</p></main></body>`);
  const r = await evaluate(page); const p = r.paragraphs[0];
  await evaluate(page, { op: 'replace', paragraphs: [{ index: p.index, textHash: p.textHash, translatedText: 'translated' }] });
  await page.evaluate(() => { document.querySelector('#target').textContent = 'External page script changed the text.'; });
  assert.equal((await evaluate(page, { op: 'restore' })).results[0].status, 'text_changed');
  await page.close();
});

await test('observer cleanup is bounded and leaves the document available for restoration', async () => {
  const page = await fresh(`<!doctype html><body><main><p>A paragraph with sufficient content to initialize the observer.</p></main></body>`);
  const r = await evaluate(page); assert.equal(r.observer.active, true);
  assert.equal((await evaluate(page, { op: 'cleanup' })).status, 'cleaned');
  const again = await evaluate(page, { op: 'resolve', index: r.paragraphs[0].index, textHash: r.paragraphs[0].textHash });
  assert.equal(again.status, 'resolved');
  await page.close();
});

await test('replacement preserves scroll position while paragraph height changes', async () => {
  const text = 'A long paragraph used to create vertical space on the page. '.repeat(20);
  const html = `<!doctype html><body><main>${Array.from({ length: 24 }, (_, i) => `<p id="p${i}">${text} ${i}</p>`).join('')}</main></body>`;
  const page = await fresh(html);
  const extraction = await evaluate(page);
  await page.evaluate(() => window.scrollTo(0, 650));
  const before = await page.evaluate(() => window.scrollY);
  const target = extraction.paragraphs[0];
  await evaluate(page, { op: 'replace', paragraphs: [{ index: target.index, textHash: target.textHash, translatedText: 'Short translation.' }] });
  await page.waitForTimeout(40);
  const after = await page.evaluate(() => window.scrollY);
  assert.ok(Math.abs(after - before) <= 2, `scroll moved from ${before} to ${after}`);
  await page.close();
});

await test('paragraph and total-output resource limits report truncation', async () => {
  const paragraphs = Array.from({ length: 430 }, (_, i) => `<p>Paragraph ${i} carries enough unique text for the bounded extraction registry.</p>`).join('');
  const page = await fresh(`<!doctype html><body><main><article>${paragraphs}</article></main></body>`);
  const r = await evaluate(page);
  assert.equal(r.paragraphs.length, 400);
  assert.equal(r.truncation.truncated, true);
  await page.close();
});


const ORIG_A = 'Original paragraph A has enough readable text to be extracted.';
const ORIG_B = 'Original paragraph B also has enough readable text to be extracted.';
const ARTICLE = `<!doctype html><body><main><article><p id="a">${ORIG_A}</p><p id="b">${ORIG_B}</p></article></main></body>`;
const tr1 = (r, text = 'translated A') => ({ op: 'replace', paragraphs: [{ index: r.paragraphs[0].index, textHash: r.paragraphs[0].textHash, translatedText: text }] });

await test('regression 1: observer lifetime expires, original restore still works', async () => {
  const page = await browser.newPage();
  await page.clock.install();
  await page.setContent(ARTICLE);
  const r = await evaluate(page);
  assert.equal((await evaluate(page, tr1(r))).results[0].status, 'translated');
  await page.clock.fastForward(121000);
  const st = await evaluate(page, { op: 'status' });
  assert.equal(st.observerActive, false); assert.equal(st.observerExpired, true);
  const out = await evaluate(page, { op: 'restore' });
  assert.equal(out.results.find(x => x.index === r.paragraphs[0].index).status, 'restored');
  assert.equal(await page.locator('#a').textContent(), ORIG_A);
  await page.close();
});

await test('regression 2+4: translate, extract again, original and registry are kept', async () => {
  const page = await fresh(ARTICLE);
  const first = await evaluate(page, { sessionToken: 'op-1' });
  await evaluate(page, { sessionToken: 'op-1', ...tr1(first) });
  const second = await evaluate(page, { sessionToken: 'op-2' });
  const view = r => r.paragraphs.map(p => [p.index, p.textHash, p.originalText]);
  assert.deepEqual(view(second), view(first));
  assert.equal((await evaluate(page, { op: 'status', sessionToken: 'op-2' })).records, first.paragraphs.length);
  const out = await evaluate(page, { op: 'restore', sessionToken: 'op-2' });
  assert.ok(out.results.every(x => x.status === 'restored'));
  assert.equal(await page.locator('#a').textContent(), ORIG_A);
  await page.close();
});

await test('regression 3: reader close, reopen, extract, restore', async () => {
  const page = await fresh(ARTICLE);
  const first = await evaluate(page, { sessionToken: 'reader-1' });
  await evaluate(page, { sessionToken: 'reader-1', ...tr1(first) });
  assert.equal((await evaluate(page, { op: 'cleanup', sessionToken: 'reader-1' })).status, 'cleaned');
  const reopened = await evaluate(page, { sessionToken: 'reader-2' });
  assert.equal(reopened.paragraphs[0].originalText, ORIG_A);
  assert.equal((await evaluate(page, { op: 'restore', sessionToken: 'reader-2' })).status, 'complete');
  assert.equal(await page.locator('#a').textContent(), ORIG_A);
  await page.close();
});

await test('regression 5: navigation change rejects the old document restore', async () => {
  const page = await fresh(ARTICLE);
  const r = await evaluate(page);
  await evaluate(page, tr1(r));
  assert.equal((await evaluate(page, { op: 'restore', navGeneration: 13 })).status, 'wrong_document');
  assert.equal(await page.locator('#a').textContent(), 'translated A');
  assert.equal((await evaluate(page, { op: 'restore' })).status, 'complete');
  await page.close();
});

await test('regression 6: tabs keep separate registries', async () => {
  const a = await fresh(ARTICLE), b = await fresh(ARTICLE);
  const ia = { tabId: 'tab-a' }, ib = { tabId: 'tab-b', documentToken: 'document-b' };
  const ra = await evaluate(a, ia), rb = await evaluate(b, ib);
  await evaluate(a, { ...ia, ...tr1(ra) }); await evaluate(b, { ...ib, ...tr1(rb) });
  assert.equal((await evaluate(b, { op: 'restore', tabId: 'tab-a' })).status, 'wrong_document');
  assert.equal(await b.locator('#a').textContent(), 'translated A');
  assert.equal((await evaluate(a, { op: 'restore', ...ia })).status, 'complete');
  assert.equal(await a.locator('#a').textContent(), ORIG_A);
  assert.equal(await b.locator('#a').textContent(), 'translated A');
  assert.equal((await evaluate(b, { op: 'restore', ...ib })).status, 'complete');
  await a.close(); await b.close();
});

await test('regression 7: after observer cleanup, replace and restore stay valid', async () => {
  const page = await fresh(ARTICLE);
  const r = await evaluate(page);
  assert.equal((await evaluate(page, { op: 'cleanup' })).status, 'cleaned');
  assert.equal((await evaluate(page, tr1(r))).results[0].status, 'translated');
  assert.equal((await evaluate(page, { op: 'restore' })).status, 'complete');
  assert.equal(await page.locator('#a').textContent(), ORIG_A);
  await page.close();
});

await test('regression 8: real document close clears registry (bfcache keeps it)', async () => {
  const page = await fresh(ARTICLE);
  const r = await evaluate(page);
  await evaluate(page, tr1(r));
  await page.evaluate(() => window.dispatchEvent(new PageTransitionEvent('pagehide', { persisted: true })));
  assert.equal((await evaluate(page, { op: 'status' })).status, 'ok');
  await page.evaluate(() => window.dispatchEvent(new PageTransitionEvent('pagehide', { persisted: false })));
  assert.equal((await evaluate(page, { op: 'status' })).status, 'wrong_document');
  assert.equal((await evaluate(page, { op: 'restore' })).status, 'wrong_document');
  await page.close();
});

await test('mutation limit expires observer only; restore still works', async () => {
  const page = await fresh(ARTICLE);
  const r = await evaluate(page);
  await evaluate(page, tr1(r));
  await page.evaluate(() => { const art = document.querySelector('article'); for (let i = 0; i < 250; i++) art.appendChild(document.createElement('span')); });
  await page.waitForTimeout(50);
  assert.equal((await evaluate(page, { op: 'status' })).observerExpired, true);
  assert.equal((await evaluate(page, { op: 'restore' })).status, 'complete');
  assert.equal(await page.locator('#a').textContent(), ORIG_A);
  await page.close();
});

await browser.close();
for (const [status, name] of passed) console.log(`${status} ${name}`);
const failed = passed.filter(([status]) => status === 'FAIL').length;
console.log(`${passed.length - failed}/${passed.length} passed`);
process.exit(failed ? 1 : 0);
