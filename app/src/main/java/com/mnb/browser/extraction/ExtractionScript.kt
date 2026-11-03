package com.mnb.browser.extraction

/**
 * JavaScript injected into the page via WebView.evaluateJavascript. It only reads the DOM that
 * the page has already rendered (it never fetches anything, so login/paywall content that is
 * not on screen is not reachable). It returns a plain object that WebView serialises to JSON:
 * { pageTitle, chapterTitle, url, paragraphs: [{ t: text, h: headingLevel }] }.
 * On failure it returns { error: message }.
 *
 * Do not use the dollar sign or triple quotes inside the script (Kotlin raw string).
 */
object ExtractionScript {
    val SOURCE: String = """
(function () {
  try {
    var REMOVE = {
      script: 1, style: 1, noscript: 1, template: 1, iframe: 1, svg: 1, canvas: 1, video: 1,
      audio: 1, object: 1, embed: 1, nav: 1, header: 1, footer: 1, aside: 1, button: 1,
      input: 1, select: 1, textarea: 1, option: 1, menu: 1, dialog: 1
    };
    var INLINE = {
      a: 1, span: 1, b: 1, i: 1, em: 1, strong: 1, u: 1, small: 1, sub: 1, sup: 1, mark: 1,
      font: 1, abbr: 1, cite: 1, code: 1, q: 1, s: 1, del: 1, ins: 1, big: 1, wbr: 1, ruby: 1,
      rt: 1, rp: 1, bdi: 1, bdo: 1, time: 1
    };
    var NOISE = /[\s_-](nav|navbar|navigation|menu|sidebar|advert|advertisement|ad|ads|adsbygoogle|banner|sponsor|sponsored|promo|comment|comments|share|sharing|social|breadcrumb|breadcrumbs|footer|header|popup|modal|cookie|cookies|related|recommend|recommended|pagination|toolbar|widget|btn|button|buttons)[\s_-]/i;
    var ROLE_NOISE = { navigation: 1, banner: 1, contentinfo: 1, complementary: 1, button: 1, menu: 1, menubar: 1, dialog: 1, alertdialog: 1 };

    function normalize(s) { return s.replace(/[ \t\r\n\f\u00a0]+/g, ' ').trim(); }

    function isHidden(el) {
      if (el.hidden || el.getAttribute('aria-hidden') === 'true') return true;
      var cs = window.getComputedStyle(el);
      return cs.display === 'none' || cs.visibility === 'hidden' || cs.visibility === 'collapse';
    }

    function isNoisy(el, tag) {
      if (REMOVE[tag]) return true;
      var role = el.getAttribute('role');
      if (role && ROLE_NOISE[role.toLowerCase()]) return true;
      var cls = typeof el.className === 'string' ? el.className : '';
      var sig = ' ' + cls + ' ' + (el.id || '') + ' ';
      return NOISE.test(sig);
    }

    function shouldSkip(el) {
      var tag = el.tagName.toLowerCase();
      return isNoisy(el, tag) || isHidden(el);
    }

    // Tag/role/hidden checks only (no class-name guessing); used for ancestors of a candidate.
    function hardSkip(el) {
      var tag = el.tagName.toLowerCase();
      if (REMOVE[tag] || isHidden(el)) return true;
      var role = el.getAttribute('role');
      return !!(role && ROLE_NOISE[role.toLowerCase()]);
    }

    function insideSkipped(el) {
      if (shouldSkip(el)) return true;
      for (var n = el.parentElement; n && n !== document.body && n !== document.documentElement; n = n.parentElement) {
        if (hardSkip(n)) return true;
      }
      return false;
    }

    // 1. Pick the smallest element that holds most of the page's paragraph text. Each text
    //    block adds its length to all of its ancestors, so sites that wrap every paragraph in
    //    its own box still resolve to the shared parent.
    var blocks = [];
    var total = 0;
    var cand = document.querySelectorAll('p, div, td, section, article, li');
    for (var i = 0; i < cand.length; i++) {
      var el = cand[i];
      var own = 0;
      if (el.tagName === 'P') {
        own = normalize(el.textContent || '').length;
      } else {
        for (var c = el.firstChild; c; c = c.nextSibling) {
          if (c.nodeType === 3) own += normalize(c.nodeValue).length;
        }
        if (own <= 40) own = 0;
      }
      if (own > 0 && !insideSkipped(el)) { blocks.push({ el: el, own: own }); total += own; }
    }
    var scores = new Map();
    for (var bi0 = 0; bi0 < blocks.length; bi0++) {
      for (var nd = blocks[bi0].el; nd && nd !== document.documentElement; nd = nd.parentElement) {
        scores.set(nd, (scores.get(nd) || 0) + blocks[bi0].own);
      }
    }
    var best = null, bestDepth = -1;
    scores.forEach(function (score, node) {
      if (node === document.body || score < total * 0.6) return;
      var depth = 0;
      for (var q = node.parentElement; q; q = q.parentElement) depth++;
      if (depth > bestDepth) { best = node; bestDepth = depth; }
    });
    var container = best || document.body;

    // 2. Walk the container in document order, collecting blocks of text.
    var out = [];
    var buf = '', linkChars = 0, inLink = 0, headingLevel = 0;

    function flush() {
      var text = normalize(buf);
      var links = linkChars;
      buf = ''; linkChars = 0;
      if (!text) return;
      if (!headingLevel && text.length < 120 && links / text.length > 0.8) return; // link-only line
      out.push({ t: text, h: headingLevel });
    }

    function walk(node) {
      if (node.nodeType === 3) {
        buf += node.nodeValue;
        if (inLink > 0) linkChars += normalize(node.nodeValue).length;
        return;
      }
      if (node.nodeType !== 1) return;
      if (shouldSkip(node)) return;
      var tag = node.tagName.toLowerCase();
      if (tag === 'br') { flush(); return; }
      var disp = window.getComputedStyle(node).display || '';
      var isBlock = disp ? (disp.indexOf('inline') !== 0 && disp.indexOf('ruby') !== 0 && disp !== 'contents') : !INLINE[tag];
      var level = (tag.length === 2 && tag.charAt(0) === 'h' && '123456'.indexOf(tag.charAt(1)) >= 0) ? parseInt(tag.charAt(1), 10) : 0;
      var prevLevel = headingLevel;
      if (isBlock) flush();
      if (level) headingLevel = level;
      if (tag === 'a') inLink++;
      for (var ch = node.firstChild; ch; ch = ch.nextSibling) walk(ch);
      if (tag === 'a') inLink--;
      if (isBlock) { flush(); headingLevel = prevLevel; }
    }

    for (var k = container.firstChild; k; k = k.nextSibling) walk(k);
    flush();

    // 3. Chapter title: a title-like element, else the first heading that was kept, else a
    //    leading "Chapter N" line. Never a bare h1, which is usually the book name.
    var chapterTitle = '';
    var cands = document.querySelectorAll('[class*="chapter-title" i], [class*="chapter_title" i], [class*="chaptertitle" i], [class*="cha-tit" i], .entry-title, .post-title, #chapter-heading, [itemprop=headline]');
    for (var ci = 0; ci < cands.length && !chapterTitle; ci++) {
      if (!insideSkipped(cands[ci]) || cands[ci].closest('header')) {
        if (!isHidden(cands[ci])) chapterTitle = normalize(cands[ci].innerText || cands[ci].textContent || '');
      }
    }
    if (!chapterTitle) {
      for (var hi = 0; hi < out.length; hi++) { if (out[hi].h > 0) { chapterTitle = out[hi].t; break; } }
    }
    if (!chapterTitle) {
      var CH = /^(chapter|ch\.?|episode|ep\.?|အခန်း)\s*[0-9၀-၉]/i;
      for (var oi = 0; oi < out.length && oi < 5; oi++) { if (CH.test(out[oi].t)) { chapterTitle = out[oi].t; break; } }
    }

    // A bare label such as "Chapter 1" is completed with the short line that follows it.
    var BARE = /^(chapter|ch\.?|episode|ep\.?|အခန်း)\s*[0-9၀-၉]+/i;
    if (BARE.test(chapterTitle) && chapterTitle.replace(BARE, '') === '') {
      for (var bi = 0; bi < out.length - 1 && bi < 5; bi++) {
        if (out[bi].t === chapterTitle) {
          var nt = out[bi + 1].t;
          if (nt.length < 100) chapterTitle = nt.indexOf(chapterTitle) === 0 ? nt : chapterTitle + ' ' + nt;
          break;
        }
      }
    }

    return {
      pageTitle: normalize(document.title || ''),
      chapterTitle: chapterTitle,
      url: location.href,
      paragraphs: out
    };
  } catch (e) {
    return { error: String(e && e.message ? e.message : e) };
  }
})();
""".trimIndent()
}
