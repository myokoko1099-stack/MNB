package com.mnb.browser.extraction

/**
 * R-03/R-04 DOM operations. The script is evaluated in an IIFE and stores state on the Document
 * using a non-enumerable Symbol (never on window and never as a tracking attribute). All page
 * values are serialized as JSON before they are inserted into the invocation.
 */
internal object DomReaderScript {
    private const val MARKER = "__MNB_DOM_READER_PAYLOAD__"

    fun invoke(payloadJson: String): String = SOURCE.replace(MARKER, payloadJson)

    private val SOURCE = """
(function (arg) {
  'use strict';
  var MAX_PARAGRAPHS = 400, MAX_LENGTH = 12000, MAX_TOTAL = 180000;
  var MAX_NODES = 6000, MAX_MUTATIONS = 200, LIFETIME_MS = 120000;
  var key = Symbol.for('mnb.dom.reader.registry.v3');
  function answer(value) { return value; }
  function hash(text) {
    var h = 2166136261;
    for (var i = 0; i < text.length; i++) { h ^= text.charCodeAt(i); h = Math.imul(h, 16777619); }
    return ('00000000' + (h >>> 0).toString(16)).slice(-8);
  }
  function normalize(s) {
    return String(s || '').replace(/[\u200b\u2060\ufeff\u00ad]/g, '')
      .replace(/[ \t\r\n\f\u00a0]+/g, ' ').trim();
  }
  function safeState() { try { return document[key] || null; } catch (_) { return null; } }
  function getNode(record) {
    try { return record.ref && typeof record.ref.deref === 'function' ? record.ref.deref() : record.ref; }
    catch (_) { return null; }
  }
  function validState(state) {
    return !!state && state.sessionToken === arg.sessionToken && state.documentToken === arg.documentToken &&
      state.tabId === arg.tabId && Number(state.navGeneration) === Number(arg.navGeneration) &&
      !state.expired && Date.now() - state.createdAt <= LIFETIME_MS;
  }
  function stateForExtraction() {
    var state = safeState();
    if (!validState(state)) {
      if (state && state.observer) { try { state.observer.disconnect(); } catch (_) {} }
      if (state && state.expiryTimer) { try { clearTimeout(state.expiryTimer); } catch (_) {} }
      state = { sessionToken: arg.sessionToken, documentToken: arg.documentToken, tabId: arg.tabId,
        navGeneration: arg.navGeneration, createdAt: Date.now(), expiryTimer: null,
        nextIndex: 0, records: new Map(), observer: null, mutations: 0, dirty: false, expired: false };
      try { Object.defineProperty(document, key, { value: state, writable: true, configurable: true, enumerable: false }); }
      catch (_) { return null; }
    }
    return state;
  }
  function hidden(el) {
    if (el.hidden || el.getAttribute('aria-hidden') === 'true') return true;
    try { var s = window.getComputedStyle(el); return s.display === 'none' || s.visibility === 'hidden' || s.visibility === 'collapse'; }
    catch (_) { return false; }
  }
  var noisy = /(^|[\\s_-])(nav|navbar|navigation|menu|sidebar|advert|advertisement|adsbygoogle|banner|sponsor|promo|comment|share|social|breadcrumb|footer|header|popup|modal|cookie|related|recommend|pagination|toolbar|widget)([\\s_-]|$)/i;
  var excluded = /^(SCRIPT|STYLE|NOSCRIPT|TEMPLATE|IFRAME|SVG|CANVAS|VIDEO|AUDIO|OBJECT|EMBED|NAV|HEADER|FOOTER|ASIDE|BUTTON|INPUT|SELECT|TEXTAREA|OPTION|MENU|DIALOG)$/;
  function skip(el) {
    if (!el || el.nodeType !== 1 || excluded.test(el.tagName) || hidden(el)) return true;
    var role = (el.getAttribute('role') || '').toLowerCase();
    if (/^(navigation|banner|contentinfo|complementary|button|menu|menubar|dialog|alertdialog)$/.test(role)) return true;
    return noisy.test(' ' + (typeof el.className === 'string' ? el.className : '') + ' ' + (el.id || '') + ' ');
  }
  function noisyAncestor(el, root) {
    for (var n = el; n && n !== root.parentElement; n = n.parentElement) if (skip(n)) return true;
    return false;
  }
  function linkRatio(el) {
    var text = normalize(el.textContent || ''); if (!text) return 1;
    var anchors; try { anchors = el.querySelectorAll('a'); } catch (_) { return 0; }
    var linked = 0;
    for (var i = 0; i < anchors.length; i++) linked += normalize(anchors[i].textContent || '').length;
    return Math.min(1, linked / text.length);
  }
  function candidateContainers() {
    var all = document.querySelectorAll('main, article, [role="main"], section, div');
    var best = document.body, bestScore = 0, bestDepth = -1;
    var limit = Math.min(all.length, 1000);
    for (var i = 0; i < limit; i++) {
      var el = all[i]; if (skip(el)) continue;
      var ps = el.querySelectorAll('p,h1,h2,h3,h4,h5,h6,blockquote,pre,li');
      var score = 0, count = 0;
      for (var j = 0; j < ps.length && j < 300; j++) {
        if (noisyAncestor(ps[j], el)) continue;
        var t = normalize(ps[j].textContent || '');
        if (t.length >= 25 && linkRatio(ps[j]) < 0.75) { score += Math.min(t.length, 2500); count++; }
      }
      if (!count) {
        var t0 = normalize(el.textContent || '');
        if (t0.length >= 120 && !noisyAncestor(el, document.body)) score = Math.min(t0.length, 5000);
      }
      var depth = 0; for (var p = el; p; p = p.parentElement) depth++;
      // Prefer a specific subtree that still holds most of the readable content.
      if (score > bestScore || (score >= bestScore * 0.88 && depth > bestDepth)) {
        best = el; bestScore = score; bestDepth = depth;
      }
    }
    return { element: best, score: bestScore };
  }
  function collect(root) {
    var selectors = 'p,h1,h2,h3,h4,h5,h6,blockquote,pre,li,div';
    var list = root.querySelectorAll(selectors), out = [], seenLong = new Set(), total = 0, limited = false, lastText = null;
    var limit = Math.min(list.length, MAX_NODES);
    function add(node, text, heading, kind, minLength) {
      text = normalize(text);
      if (!text) return;
      var linkRoot = node.nodeType === 1 ? node : node.parentElement;
      if (linkRoot && linkRatio(linkRoot) > (text.length < 160 ? 0.72 : 0.92)) return;
      if (text.length < minLength) return;
      if (text.length >= 20) { if (seenLong.has(text)) return; seenLong.add(text); }
      else if (text === lastText) return;
      if (text.length > MAX_LENGTH) { limited = true; return; }
      if (total + text.length > MAX_TOTAL || out.length >= MAX_PARAGRAPHS) { limited = true; return; }
      total += text.length;
      out.push({ node: node, text: text, headingLevel: heading, contentKind: kind, hash: hash(text) });
      lastText = text;
    }
    for (var i = 0; i < limit; i++) {
      var el = list[i];
      if (skip(el) || noisyAncestor(el, root)) continue;
      var tag = el.tagName.toLowerCase();
      var semantic = /^(p|h[1-6]|blockquote|pre|li)$/.test(tag);
      if (semantic) {
        // Avoid selecting nested semantic blocks twice.
        var parent = el.parentElement, nested = false;
        while (parent && parent !== root) {
          if (/^(p|h[1-6]|blockquote|pre|li)$/.test(parent.tagName.toLowerCase())) { nested = true; break; }
          parent = parent.parentElement;
        }
        if (nested) continue;
      } else {
        // Generic wrappers are anchors only when they contain no other block candidate.
        if (el.querySelector('p,h1,h2,h3,h4,h5,h6,blockquote,pre,li,div')) continue;
      }
      if (!semantic && tag === 'div') {
        var groups = [], group = [], hasDirectBreak = false;
        for (var child = el.firstChild; child; child = child.nextSibling) {
          if (child.nodeType === 1 && child.tagName === 'BR') { hasDirectBreak = true; groups.push(group); group = []; }
          else group.push(child);
        }
        if (hasDirectBreak) {
          groups.push(group);
          var segments = [], safeSegments = true;
          for (var gi = 0; gi < groups.length; gi++) {
            var nodes = groups[gi].filter(function (n) { return n.nodeType === 1 || (n.nodeType === 3 && normalize(n.nodeValue)); });
            if (!nodes.length) continue;
            if (nodes.length !== 1) { safeSegments = false; break; }
            var segmentNode = nodes[0], segmentText = normalize(segmentNode.textContent || segmentNode.nodeValue || '');
            if (segmentText) segments.push({ node: segmentNode, text: segmentText });
          }
          if (safeSegments && segments.length > 1) {
            for (var si = 0; si < segments.length; si++) add(segments[si].node, segments[si].text, 0, 'paragraph', 1);
            continue;
          }
        }
      }
      var text = normalize(el.textContent || '');
      var heading = /^h[1-6]$/.test(tag) ? parseInt(tag.charAt(1), 10) : 0;
      var kind = heading ? 'heading' : (tag === 'blockquote' ? 'quote' : 'paragraph');
      add(el, text, heading, kind, semantic ? 1 : 35);
    }
    if (list.length > MAX_NODES) limited = true;
    return { items: out, truncated: limited, totalChars: total, nodeCount: list.length };
  }
  function installObserver(state, root) {
    if (state.observer) { try { state.observer.disconnect(); } catch (_) {} state.observer = null; }
    if (state.expiryTimer) { try { clearTimeout(state.expiryTimer); } catch (_) {} state.expiryTimer = null; }
    if (state.mutations >= MAX_MUTATIONS || Date.now() - state.createdAt >= LIFETIME_MS) { state.expired = true; return; }
    if (typeof MutationObserver !== 'function') return;
    try {
      state.observer = new MutationObserver(function (changes) {
        if (!validState(state) || Date.now() - state.createdAt > LIFETIME_MS || state.mutations >= MAX_MUTATIONS) {
          state.expired = true; try { state.observer.disconnect(); } catch (_) {} state.observer = null;
          if (state.expiryTimer) { try { clearTimeout(state.expiryTimer); } catch (_) {} state.expiryTimer = null; }
          return;
        }
        state.mutations += changes.length || 1; state.dirty = true;
        if (state.mutations >= MAX_MUTATIONS) {
          try { state.observer.disconnect(); } catch (_) {} state.observer = null;
          if (state.expiryTimer) { try { clearTimeout(state.expiryTimer); } catch (_) {} state.expiryTimer = null; }
        }
      });
      state.observer.observe(root, { childList: true, subtree: true, characterData: true });
      state.expiryTimer = setTimeout(function () {
        if (state.observer && Date.now() - state.createdAt >= LIFETIME_MS) {
          try { state.observer.disconnect(); } catch (_) {} state.observer = null; state.expired = true; state.expiryTimer = null;
        }
      }, LIFETIME_MS + 20);
    } catch (_) { state.observer = null; }
  }
  function extract() {
    try {
      if (!arg.sessionToken || !arg.documentToken) return answer({ status: 'error', error: 'missing_token' });
      var selected = candidateContainers(), root = selected.element || document.body;
      var scanned = collect(root), state = stateForExtraction();
      if (!state) return answer({ status: 'error', error: 'registry_unavailable' });
      var oldRecords = Array.from(state.records.values()), unused = new Set(oldRecords.map(function (r) { return r.index; }));
      var out = [], nextRecords = new Map(), total = 0;
      for (var i = 0; i < scanned.items.length; i++) {
        var item = scanned.items[i], found = null;
        for (var j = 0; j < oldRecords.length; j++) {
          var old = oldRecords[j];
          if (unused.has(old.index) && getNode(old) === item.node && old.hash === item.hash) { found = old; break; }
        }
        if (!found) {
          for (var k = 0; k < oldRecords.length; k++) {
            var prior = oldRecords[k];
            if (unused.has(prior.index) && prior.hash === item.hash && prior.headingLevel === item.headingLevel && prior.state !== 'Translated') { found = prior; break; }
          }
        }
        var index = found ? found.index : state.nextIndex++;
        if (found) unused.delete(found.index);
        var exact = found && found.state === 'Translated' ? found.originalExact : String(item.node.textContent || '');
        var ref = typeof WeakRef === 'function' ? new WeakRef(item.node) : item.node;
        var record = { index: index, hash: item.hash, text: item.text, originalExact: exact,
          headingLevel: item.headingLevel, contentKind: item.contentKind, ref: ref,
          state: found && found.state === 'Translated' ? 'Translated' : 'Original', translatedText: found ? found.translatedText : null };
        nextRecords.set(index, record);
        out.push({ index: index, textHash: item.hash, originalText: item.text,
          headingLevel: item.headingLevel, contentKind: item.contentKind });
        total += item.text.length;
      }
      // Preserve old anchors for stable indices but do not let the bounded registry grow indefinitely.
      for (var rr = 0; rr < oldRecords.length && nextRecords.size < MAX_PARAGRAPHS; rr++) {
        if (unused.has(oldRecords[rr].index) && getNode(oldRecords[rr])) nextRecords.set(oldRecords[rr].index, oldRecords[rr]);
      }
      state.records = nextRecords; state.dirty = false;
      installObserver(state, root);
      var chapterConfidence = Math.max(0, Math.min(1,
        (Math.min(out.length, 8) / 8) * 0.45 + (Math.min(total, 6000) / 6000) * 0.35 +
        (out.some(function (p) { return p.headingLevel > 0; }) ? 0.15 : 0) +
        (/^(MAIN|ARTICLE)$/.test(root.tagName) ? 0.05 : 0)));
      var chapterTitle = '';
      var titleEls = document.querySelectorAll('[class*="chapter-title" i], [class*="chapter_title" i], [class*="chaptertitle" i], .entry-title, .post-title, [itemprop="headline"]');
      for (var ti = 0; ti < titleEls.length && !chapterTitle; ti++) if (!skip(titleEls[ti])) chapterTitle = normalize(titleEls[ti].textContent || '');
      if (!chapterTitle) { var h = out.find(function (p) { return p.headingLevel > 0; }); if (h) chapterTitle = h.originalText; }
      var rootSignature = (root.tagName + ' ' + (typeof root.className === 'string' ? root.className : '') + ' ' + (root.id || '')).toLowerCase();
      var chapterSignal = !!chapterTitle || /chapter|episode|novel|story|reader|content/.test(rootSignature);
      var articleSignal = root.tagName === 'ARTICLE' || /article|post|entry/.test(rootSignature);
      var contentType = chapterSignal ? 'chapter' : (articleSignal ? 'article' : 'content');
      return answer({ sessionToken: arg.sessionToken, documentToken: arg.documentToken, tabId: arg.tabId,
        navGeneration: arg.navGeneration, status: out.length ? 'success' : 'empty', contentType: contentType,
        chapterConfidence: chapterConfidence, pageTitle: normalize(document.title || ''), chapterTitle: chapterTitle,
        url: String(location.href || ''), paragraphs: out,
        truncation: { truncated: scanned.truncated, totalCharacters: total,
          maxParagraphs: MAX_PARAGRAPHS, maxParagraphLength: MAX_LENGTH, maxTotalCharacters: MAX_TOTAL },
        observer: { active: !!state.observer, mutationCount: state.mutations, dirty: state.dirty } });
    } catch (e) { return answer({ sessionToken: arg.sessionToken, documentToken: arg.documentToken,
      tabId: arg.tabId, navGeneration: arg.navGeneration, status: 'error', error: String(e && e.message || e) }); }
  }
  function withState() {
    var s = safeState();
    return validState(s) ? s : null;
  }
  function stateFailure() {
    var s = safeState();
    if (!s || s.documentToken !== arg.documentToken || s.tabId !== arg.tabId || Number(s.navGeneration) !== Number(arg.navGeneration)) return 'wrong_document';
    if (s.sessionToken !== arg.sessionToken) return 'stale_session';
    if (s.expired || Date.now() - s.createdAt > LIFETIME_MS) return 'stale_session';
    return null;
  }
  function resolve(record) {
    if (!record) return { status: 'index_not_found' };
    var node = getNode(record);
    if (!node || !node.isConnected) return { status: 'node_disconnected' };
    return { status: 'resolved', node: node };
  }
  function apply() {
    var failure = stateFailure(); if (failure) return answer({ status: failure });
    var state = withState();
    var results = [], list = Array.isArray(arg.paragraphs) ? arg.paragraphs : [];
    var scrollX = window.scrollX, scrollY = window.scrollY;
    for (var i = 0; i < list.length && i < MAX_PARAGRAPHS; i++) {
      var request = list[i], record = state.records.get(Number(request.index));
      if (!record) { results.push({ index: request.index, status: 'index_not_found' }); continue; }
      if (record.hash !== request.textHash) { results.push({ index: request.index, status: 'hash_mismatch' }); continue; }
      var target = resolve(record);
      if (target.status !== 'resolved') { results.push({ index: request.index, status: target.status }); continue; }
      var node = target.node, current = String(node.textContent || ''), currentNorm = normalize(current);
      if (record.state === 'Translated' && current === record.translatedText) {
        if (request.translatedText === record.translatedText) { results.push({ index: request.index, status: 'already_translated' }); continue; }
        results.push({ index: request.index, status: 'stale' }); continue;
      }
      if (currentNorm !== record.text || record.state === 'Stale') { record.state = 'Stale'; results.push({ index: request.index, status: 'text_changed' }); continue; }
      var translated = String(request.translatedText || '');
      if (!translated || translated.length > MAX_LENGTH) { record.state = 'Failed'; results.push({ index: request.index, status: 'invalid_translation' }); continue; }
      record.originalExact = current; record.translatedText = translated;
      try { node.textContent = translated; record.state = 'Translated'; results.push({ index: request.index, status: 'translated' }); }
      catch (_) { record.state = 'Failed'; results.push({ index: request.index, status: 'failed' }); }
    }
    try {
      window.scrollTo(scrollX, scrollY);
      requestAnimationFrame(function () { try { window.scrollTo(scrollX, scrollY); } catch (_) {} });
    } catch (_) {}
    return answer({ status: 'complete', sessionToken: arg.sessionToken, documentToken: arg.documentToken, results: results });
  }
  function restore() {
    var failure = stateFailure(); if (failure) return answer({ status: failure });
    var state = withState();
    var list = Array.isArray(arg.indices) ? arg.indices : Array.from(state.records.keys());
    var results = [], scrollX = window.scrollX, scrollY = window.scrollY;
    for (var i = 0; i < list.length && i < MAX_PARAGRAPHS; i++) {
      var index = Number(list[i]), record = state.records.get(index);
      if (!record) { results.push({ index: index, status: 'index_not_found' }); continue; }
      if (record.state !== 'Translated') { results.push({ index: index, status: (record.state === 'Original' || record.state === 'Restored') ? 'restored' : 'failed' }); continue; }
      var target = resolve(record);
      if (target.status !== 'resolved') { results.push({ index: index, status: target.status }); continue; }
      if (String(target.node.textContent || '') !== record.translatedText) { record.state = 'Stale'; results.push({ index: index, status: 'text_changed' }); continue; }
      try { target.node.textContent = record.originalExact; record.state = 'Restored'; record.translatedText = null; results.push({ index: index, status: 'restored' }); }
      catch (_) { record.state = 'Failed'; results.push({ index: index, status: 'failed' }); }
    }
    try { window.scrollTo(scrollX, scrollY); } catch (_) {}
    return answer({ status: 'complete', sessionToken: arg.sessionToken, documentToken: arg.documentToken, results: results });
  }
  function cleanup() {
    var failure = stateFailure(); if (failure) return answer({ status: failure });
    var state = withState();
    if (state.observer) { try { state.observer.disconnect(); } catch (_) {} state.observer = null; }
    if (state.expiryTimer) { try { clearTimeout(state.expiryTimer); } catch (_) {} state.expiryTimer = null; }
    return answer({ status: 'cleaned', mutationCount: state.mutations });
  }
  try {
    if (arg.op === 'extract') return extract();
    if (arg.op === 'replace') return apply();
    if (arg.op === 'restore') return restore();
    if (arg.op === 'cleanup') return cleanup();
    if (arg.op === 'resolve') {
      var failure = stateFailure(); if (failure) return answer({ status: failure });
      var s = withState();
      var rec = s.records.get(Number(arg.index));
      if (!rec) return answer({ status: 'index_not_found' });
      if (rec.hash !== arg.textHash) return answer({ status: 'hash_mismatch' });
      return answer({ status: resolve(rec).status, index: rec.index, textHash: rec.hash });
    }
    return answer({ status: 'error', error: 'unknown_operation' });
  } catch (e) { return answer({ status: 'error', error: String(e && e.message || e) }); }
})(__MNB_DOM_READER_PAYLOAD__)
""".trimIndent()
}
