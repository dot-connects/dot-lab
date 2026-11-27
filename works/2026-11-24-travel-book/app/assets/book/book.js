// Book runtime: renders book.json pages with the theme's templates (assets/book/themes/<t>/layouts.html).
// Flutter calls window.renderBook(book); JS talks back through flutter_inappwebview's callHandler.
//
// book = { id, theme, format: "phone"|"a5"|"square", pages: [PageSpec], media: { <shotId>: { src, frames?: [src], video? } } }
// PageSpec = { layout, ids, crop, text }   (lib/core/models.dart)
// book.style = { font?: family, size?: 0.9|1|1.15, cover?: "#rrggbb" }   (다듬기)
(function () {
  const RATIO = { phone: 4 / 3, a5: 1.4142, square: 1 };
  const bridge = (name, data) => window.flutter_inappwebview?.callHandler(name, data);
  let current = null;

  async function loadTheme(theme) {
    document.getElementById("theme").href = `themes/${theme}/theme.css`;
    // theme.css scopes every rule under .t-<theme>; without it the pages render unstyled (emulator run 2026-11-26)
    for (const c of [...document.body.classList]) if (c.startsWith("t-")) document.body.classList.remove(c);
    document.body.classList.add(`t-${theme}`);
    const html = await (await fetch(`themes/${theme}/layouts.html`)).text();
    document.getElementById("tpl").innerHTML = html;
  }

  /** A slot's crop "x% y%" or "x% y% s": where the photo sits in its frame, and how far it is zoomed (s >= 1). */
  function setCrop(el, c) {
    let [x = 50, y = 50, s = 1] = (c || "50% 50%").split(" ").map(parseFloat);
    s = Math.max(1, Math.min(4, s || 1));
    el.dataset.c = s > 1.005 ? `${x.toFixed(1)}% ${y.toFixed(1)}% ${s.toFixed(2)}` : `${x.toFixed(1)}% ${y.toFixed(1)}%`;
    el.style.objectPosition = `${x}% ${y}%`;
    // zoom = a smaller view box of the picture, placed by the same x/y; it stays inside its frame (no transform spill)
    const w = 100 / s, l = x / 100 * (100 - w), t = y / 100 * (100 - w);
    if (s > 1.005) el.style.setProperty("object-view-box", `inset(${t.toFixed(2)}% ${(100 - w - l).toFixed(2)}% ${(100 - w - t).toFixed(2)}% ${l.toFixed(2)}%)`);
    else el.style.removeProperty("object-view-box");
  }

  function fill(pg, spec, book, n) {
    pg.style.setProperty("--ratio", RATIO[book.format] || RATIO.phone);
    pg.dataset.page = n;
    for (const img of pg.querySelectorAll("img[data-slot]")) {
      const i = +img.dataset.slot, id = spec.ids[i], m = book.media[id] || {};
      const frame = img.dataset.frame;
      // only pages near the screen decode their photos: a 50-page book no longer decodes every 1600 px JPEG at open
      img.loading = "lazy";
      img.decoding = "async";
      img.src = frame != null && m.frames ? m.frames[+frame] || m.src : m.src;
      setCrop(img, (spec.crop && spec.crop[i]) || img.dataset.crop);
      if (m.video && frame === "0") {
        // the clip plays in place of its first frame, only while visible; export keeps the poster
        const v = document.createElement("video");
        Object.assign(v, { muted: true, loop: true, playsInline: true, poster: img.src, src: m.video });
        setCrop(v, img.dataset.c);
        v.dataset.clip = "1";
        v.dataset.slot = img.dataset.slot;
        img.replaceWith(v);
      }
    }
    for (const el of pg.querySelectorAll("[data-text]")) el.textContent = spec.text[el.dataset.text] || "";
    const ix = pg.querySelector("[data-index]");
    if (ix && spec.text.index) renderIndex(ix, JSON.parse(spec.text.index));
  }

  const NAMES = ["첫째", "둘째", "셋째", "넷째", "다섯째", "여섯째", "일곱째", "여덟째", "아홉째", "열째"];
  const WD = ["일", "월", "화", "수", "목", "금", "토"];
  function renderIndex(el, days) {
    // two columns, split by day count (mockup: days 1-2 left, 3 right)
    const cols = [document.createElement("div"), document.createElement("div")];
    days.forEach((d, i) => {
      const col = cols[i < Math.ceil(days.length / 2) ? 0 : 1];
      const dt = new Date(d.day + "T00:00:00");
      const head = document.createElement("span");
      head.className = "sm" + (col.childElementCount ? " g" : "");
      head.textContent = `${d.day.slice(5).replace("-", ".")} ${WD[dt.getDay()]} · ${NAMES[i] || i + 1 + "번째"} 날`;
      col.append(head);
      for (const s of d.stops) {
        const row = document.createElement("span");
        row.className = "cp";
        const t = document.createElement("span");
        t.className = "lb";
        t.textContent = s.time;
        row.append(t, s.place || "");
        col.append(row);
      }
    });
    el.replaceChildren(...cols);
  }

  // big display words (cover titles) shrink until they fit the page width
  function fitBig(pg) {
    for (const el of pg.querySelectorAll(".big")) {
      let fs = parseFloat(getComputedStyle(el).fontSize);
      const max = pg.clientWidth * 0.94;
      while ((el.scrollWidth > max || el.scrollWidth > el.clientWidth + 1) && fs > 10) { fs *= 0.92; el.style.fontSize = fs + "px"; }
      el.scrollLeft = 0;
    }
    // a one-line title the person typed longer than the AI's (or in a wider font) shrinks to end inside the page
    for (const el of pg.querySelectorAll('[data-text="title"]')) {
      if (!pg.clientWidth || el.classList.contains("big")) continue; // .big has its own fit above
      // start from the size before any fit (sizeText's, or the theme's), kept on the element
      if (el.dataset.fs0 === undefined) el.dataset.fs0 = el.style.fontSize;
      el.style.fontSize = el.dataset.fs0;
      let fs = parseFloat(getComputedStyle(el).fontSize);
      const pr = pg.getBoundingClientRect(), pad = pr.width * 0.03;
      // one-line titles must end inside the page; wrapping ones may take two lines at most
      // (contenteditable rewrites white-space to keep spaces; text-wrap-mode still says nowrap)
      const cs = getComputedStyle(el), nowrap = cs.whiteSpace === "nowrap" || cs.textWrapMode === "nowrap";
      const out = () => {
        const r = el.getBoundingClientRect();
        if (el.scrollWidth > el.clientWidth + 1) return true; // text wider than its own box (fixed-width grid cells)
        return nowrap ? r.left < pr.left + pad * 0.5 - 1 || r.right > pr.right - pad : r.height > fs * 2.7;
      };
      while (out() && fs > 8) { fs *= 0.94; el.style.fontSize = (fs / pg.clientWidth * 100) + "cqw"; }
      el.scrollLeft = 0;
      pg.scrollLeft = 0; // the caret scrolls an overflow:hidden page sideways
    }
  }

  // ----- 다듬기 style: font for the whole book, size of the small text, the cover's colour -----
  function applyFont(book) {
    const f = book.style && book.style.font, b = document.body.style;
    if (f) { b.setProperty("--f", `"${f}","Pretendard",sans-serif`); b.fontSynthesis = "none"; }
    else { b.removeProperty("--f"); b.fontSynthesis = ""; }
  }

  /** Captions and body text (not headings) times k, kept in cqw so a resize keeps the ratio. */
  function sizeText(pg, k) {
    const w = pg.clientWidth;
    if (!k || k === 1 || !w) return;
    for (const el of pg.querySelectorAll("[data-text]")) {
      const fs = parseFloat(getComputedStyle(el).fontSize);
      if (fs < w * 0.06) el.style.fontSize = (fs * k / w * 100).toFixed(3) + "cqw";
    }
  }

  const lum = h => {
    const n = parseInt(h.slice(1), 16);
    const c = [n >> 16, (n >> 8) & 255, n & 255].map(v => { v /= 255; return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4; });
    return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
  };
  /** White text when it reads better on the colour than near-black does (WCAG contrast). */
  window.whiteOn = h => 1.05 / (lum(h) + 0.05) >= (lum(h) + 0.05) / (lum("#161616") + 0.05);

  /** Whether a page shows its paper (photos cover less than 85% of it): a full-bleed cover has no colour to pick. */
  function paperShows(pg) {
    const pr = pg.getBoundingClientRect(), area = pr.width * pr.height;
    if (!area) return true;
    let ph = 0;
    for (const im of pg.querySelectorAll("[data-slot]")) { const r = im.getBoundingClientRect(); ph += r.width * r.height; }
    return ph / area < 0.85;
  }

  function coverColor(pg, c) {
    if (!c) return;
    const white = window.whiteOn(c), ink = white ? "#ffffff" : "#161616";
    const soft = white ? "rgba(255,255,255,.78)" : "rgba(22,22,22,.68)";
    for (const v of ["--paper", "--cloth", "--kraft", "--edge"]) pg.style.setProperty(v, c);
    for (const v of ["--ink", "--kink"]) pg.style.setProperty(v, ink);
    for (const v of ["--soft", "--dim"]) pg.style.setProperty(v, soft);
    pg.style.background = c;
    pg.style.color = ink;
  }

  const seen = new IntersectionObserver(es => {
    for (const e of es) for (const v of e.target.querySelectorAll("video[data-clip]")) e.isIntersecting && !document.hidden ? v.play().catch(() => {}) : v.pause();
  }, { threshold: 0.6 });
  // hidden (screen off, app in the background, another screen on top): no clip decodes; back: the visible one plays
  document.addEventListener("visibilitychange", () => {
    for (const v of document.querySelectorAll("video[data-clip]")) v.pause();
    if (!document.hidden) for (const pg of document.querySelectorAll(".pg")) { seen.unobserve(pg); seen.observe(pg); }
  });

  /** The page index at the left edge of what the reader shows now (0 = cover). */
  function shownPage(root) {
    const sheets = root.querySelectorAll(".sheet");
    if (!sheets.length || !root.clientWidth) return 0;
    const sh = sheets[Math.round(root.scrollLeft / root.clientWidth)] || sheets[0];
    const p = sh.querySelector(".pg[data-page]");
    return p ? +p.dataset.page : 0;
  }

  window.renderBook = async function (book, opts = {}) {
    // the old pages stop taking edits now: their events would land on a page that is about to change
    if (window.editMode) window.editMode(null);
    current = book;
    await loadTheme(book.theme);
    document.body.classList.toggle("export", !!opts.exportMode);
    document.body.classList.toggle("tone", !!book.toneMatch);
    applyFont(book);
    // a redraw builds the new pages under the old ones and swaps when they are ready: no blank flash on a font change
    const old = document.getElementById("book");
    const keep = opts.page != null ? opts.page : old.children.length ? shownPage(old) : 0;
    const root = old.cloneNode(false);
    root.style.cssText = "position:absolute;inset:0;visibility:hidden";
    old.after(root);
    // spread: an open book - the cover on the right with the inside cover blank, then 1|2, 3|4 (export: one page)
    const spread = !!opts.spread && !opts.exportMode;
    document.body.classList.toggle("spread", spread);
    let sheet = null;
    book.pages.forEach((spec, n) => {
      const t = document.getElementById(spec.layout);
      if (!t) return;
      if (!spread || n % 2 === 1 || n === 0) {
        sheet = document.createElement("div");
        sheet.className = spread ? "sheet two" : "sheet";
        root.append(sheet);
        if (spread && n === 0) { const blank = document.createElement("div"); blank.className = "pg blank left"; sheet.append(blank); }
      }
      const pg = t.content.firstElementChild.cloneNode(true);
      fill(pg, spec, book, n);
      if (spread) pg.classList.add(n % 2 === 1 ? "left" : "right");
      sheet.append(pg);
      seen.observe(pg);
      let timer = null;
      pg.addEventListener("pointerdown", () => { timer = setTimeout(() => bridge("longPress", { page: n }), 550); });
      for (const ev of ["pointerup", "pointercancel", "pointerleave"]) pg.addEventListener(ev, () => clearTimeout(timer));
    });
    if (spread) { const b0 = root.querySelector(".pg.blank"), p0 = root.querySelector(".pg[data-page]"); if (b0 && p0) b0.style.setProperty("--ratio", p0.style.getPropertyValue("--ratio")); }
    await document.fonts.ready;
    const st = book.style || {};
    const pgs = [...root.querySelectorAll(".pg[data-page]")];
    if (pgs[0]) coverColor(pgs[0], st.cover);
    pgs.slice(1).forEach(pg => sizeText(pg, st.size)); // before the fit: a larger size must still fit
    pgs.forEach(fitBig);
    // land on the same page as before; its photos decode before the swap
    const target = pgs.find(p => +p.dataset.page === keep) || pgs[0];
    const sh = target ? target.closest(".sheet") : null;
    if (opts.page != null) [...root.children].forEach(x => (x.style.display = x === sh ? "" : "none"));
    if (sh) {
      root.scrollLeft = [...root.children].indexOf(sh) * root.clientWidth;
      const imgs = [...sh.querySelectorAll("img")];
      imgs.forEach(i => (i.loading = "eager"));
      await Promise.race([Promise.all(imgs.map(i => i.decode().catch(() => {}))), new Promise(r => setTimeout(r, 700))]);
    }
    old.remove();
    root.style.cssText = "";
    if (sh) root.scrollLeft = [...root.children].indexOf(sh) * root.clientWidth;
    edit = null;
    bridge("rendered", { pages: book.pages.length, coverPaper: pgs[0] ? paperShows(pgs[0]) : false });
  };

  /** Export: show one page alone (Flutter screenshots it), video slots as posters. */
  window.showPage = async function (n) {
    const sheets = document.querySelectorAll(".sheet");
    sheets.forEach((s, i) => (s.style.display = i === n ? "" : "none"));
    for (const v of document.querySelectorAll("video[data-clip]")) v.pause();
    // lazy photos of the shown page must be decoded before Flutter takes the screenshot
    const imgs = sheets[n] ? [...sheets[n].querySelectorAll("img")] : [];
    for (const i of imgs) i.loading = "eager";
    await Promise.all(imgs.map(i => (i.complete ? i.decode() : new Promise(r => { i.onload = i.onerror = r; })).catch(() => {})));
    return new Promise(r => requestAnimationFrame(() => requestAnimationFrame(() => r(true))));
  };

  window.currentBook = () => current;

  // ----- 다듬기 editing on the shown page (Flutter: showPage(n) then editMode) -----
  // "text": the page's words are typed in place -> bridge("text", {page, key, value})
  // "photo": tap a photo to select it -> bridge("slot", {page, slot}); drag the selected one to move what shows
  //          -> bridge("crop", {page, slot, pos})
  let edit = null;

  /** Typing: the page grows around the words being typed so they stay readable above the keyboard. */
  function zoomTo(pg, el) {
    pg.style.transform = "";
    const pr = pg.getBoundingClientRect(), er = el.getBoundingClientRect();
    const vw = innerWidth, vh = innerHeight;
    const k = Math.min(2.6, (vw - 40) / Math.max(er.width, 60), (vh * 0.6) / Math.max(er.height, 20));
    if (k <= 1.05) return;
    const cx = er.left + er.width / 2 - pr.left, cy = er.top + er.height / 2 - pr.top;
    Object.assign(pg.style, { transformOrigin: "0 0", transition: "transform .22s ease", position: "relative", zIndex: 5,
      transform: `translate(${(vw / 2 - pr.left - cx * k).toFixed(1)}px,${(vh / 2 - pr.top - cy * k).toFixed(1)}px) scale(${k.toFixed(3)})` });
  }
  function unzoom(pg) { if (pg) pg.style.transform = ""; }
  // the keyboard opening (or closing) resizes the view: keep the typed words in the middle
  addEventListener("resize", () => {
    const a = document.activeElement;
    if (edit && edit.mode === "text" && a && a.classList && a.classList.contains("editing")) zoomTo(edit.pg, a);
  });

  window.editMode = function (mode, n, slot) {
    const pg = n == null ? null : document.querySelector(`.pg[data-page="${n}"]`);
    for (const el of document.querySelectorAll("[contenteditable]")) { el.removeAttribute("contenteditable"); el.classList.remove("editing"); }
    for (const el of document.querySelectorAll(".selslot")) el.classList.remove("selslot");
    if (edit) unzoom(edit.pg);
    edit = mode && pg ? { mode, n, slot: slot || 0, pg } : null;
    document.body.classList.toggle("photoedit", !!edit && mode === "photo");
    if (!edit) return;
    if (mode === "text") {
      for (const el of pg.querySelectorAll("[data-text]")) {
        if (!el.textContent.trim() || el.closest("[data-index]")) continue;
        el.contentEditable = "plaintext-only";
        el.spellcheck = false;
        el.classList.add("editing");
        el.onfocus = () => zoomTo(pg, el);
        el.onblur = () => unzoom(pg);
        el.oninput = () => {
          if (el.dataset.text === "title") fitBig(pg);
          bridge("text", { page: n, key: el.dataset.text, value: el.textContent.replace(/\s+/g, " ").trim() });
        };
      }
    } else if (mode === "photo") {
      // the photo's frame carries the mark (a ring drawn on the <img> itself sits under the picture)
      const sel = pg.querySelector(`[data-slot="${edit.slot}"]`);
      const box = sel && (sel.parentElement !== pg ? sel.parentElement : sel);
      if (box) {
        if (box !== sel && getComputedStyle(box).position === "static") box.style.position = "relative";
        box.classList.add("selslot");
      }
    }
  };

  // photo edit: one finger moves what shows, two fingers zoom and move; the result goes back once all lift
  const pts = new Map();
  let g = null;
  const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
  const mid = ps => ({ x: ps.reduce((t, p) => t + p.x, 0) / ps.length, y: ps.reduce((t, p) => t + p.y, 0) / ps.length });
  const dist = (a, b) => Math.hypot(a.x - b.x, a.y - b.y) || 1;
  function rebase() {
    const [x = 50, y = 50, s = 1] = (g.im.dataset.c || "50% 50%").split(" ").map(parseFloat);
    Object.assign(g, { x, y, s, start: [...pts.values()].map(p => ({ ...p })) });
  }
  document.addEventListener("pointerdown", e => {
    if (!edit || edit.mode !== "photo") return;
    const im = e.target.closest && e.target.closest("[data-slot]");
    if (!im || !edit.pg.contains(im) || (g && g.im !== im)) return;
    const slot = +im.dataset.slot;
    if (slot !== edit.slot) { edit.slot = slot; window.editMode("photo", edit.n, slot); bridge("slot", { page: edit.n, slot }); }
    e.preventDefault();
    im.setPointerCapture(e.pointerId);
    pts.set(e.pointerId, { x: e.clientX, y: e.clientY });
    if (!g) g = { im, slot, n: edit.n, r: im.getBoundingClientRect(), moved: false, c0: im.dataset.c };
    rebase();
  });
  document.addEventListener("pointermove", e => {
    if (!g || !pts.has(e.pointerId)) return;
    pts.set(e.pointerId, { x: e.clientX, y: e.clientY });
    const now = [...pts.values()], a = g.start, m0 = mid(a), m1 = mid(now);
    const s = now.length > 1 && a.length > 1 ? clamp(g.s * dist(now[0], now[1]) / dist(a[0], a[1]), 1, 4) : g.s;
    const k = 160 / s; // a zoomed photo moves less per finger pixel
    const x = clamp(g.x - (m1.x - m0.x) / g.r.width * k, 0, 100), y = clamp(g.y - (m1.y - m0.y) / g.r.height * k, 0, 100);
    setCrop(g.im, `${x}% ${y}% ${s}`);
    if (Math.abs(m1.x - m0.x) + Math.abs(m1.y - m0.y) > 3 || s !== g.s) g.moved = true;
  });
  function lift(e) {
    if (!g || !pts.has(e.pointerId)) return;
    pts.delete(e.pointerId);
    if (pts.size) return rebase();
    if (g.moved && g.im.dataset.c !== g.c0) bridge("crop", { page: g.n, slot: g.slot, pos: g.im.dataset.c });
    g = null;
  }
  document.addEventListener("pointerup", lift);
  document.addEventListener("pointercancel", lift);
})();
