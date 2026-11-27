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
      img.style.objectPosition = (spec.crop && spec.crop[i]) || img.dataset.crop || "50% 50%";
      if (m.video && frame === "0") {
        // the clip plays in place of its first frame, only while visible; export keeps the poster
        const v = document.createElement("video");
        Object.assign(v, { muted: true, loop: true, playsInline: true, poster: img.src, src: m.video });
        v.style.objectPosition = img.style.objectPosition;
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
      el.style.fontSize = "";
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
    for (const e of es) for (const v of e.target.querySelectorAll("video[data-clip]")) e.isIntersecting ? v.play().catch(() => {}) : v.pause();
  }, { threshold: 0.6 });

  window.renderBook = async function (book, opts = {}) {
    current = book;
    await loadTheme(book.theme);
    document.body.classList.toggle("export", !!opts.exportMode);
    document.body.classList.toggle("tone", !!book.toneMatch);
    applyFont(book);
    const root = document.getElementById("book");
    root.replaceChildren();
    // spread: the cover alone, then two pages side by side like an open book (export always shows one page)
    const spread = !!opts.spread && !opts.exportMode;
    document.body.classList.toggle("spread", spread);
    let sheet = null;
    book.pages.forEach((spec, n) => {
      const t = document.getElementById(spec.layout);
      if (!t) return;
      if (!spread || n === 0 || n % 2 === 1) {
        sheet = document.createElement("div");
        sheet.className = spread && n > 0 ? "sheet two" : "sheet";
        root.append(sheet);
      }
      const pg = t.content.firstElementChild.cloneNode(true);
      fill(pg, spec, book, n);
      if (spread && n > 0) pg.classList.add(n % 2 === 1 ? "left" : "right");
      sheet.append(pg);
      seen.observe(pg);
      let timer = null;
      pg.addEventListener("pointerdown", () => { timer = setTimeout(() => bridge("longPress", { page: n }), 550); });
      for (const ev of ["pointerup", "pointercancel", "pointerleave"]) pg.addEventListener(ev, () => clearTimeout(timer));
    });
    await document.fonts.ready;
    const st = book.style || {};
    const pgs = [...root.querySelectorAll(".pg")];
    if (pgs[0]) coverColor(pgs[0], st.cover);
    pgs.forEach(fitBig);
    pgs.slice(1).forEach(pg => sizeText(pg, st.size));
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
  window.editMode = function (mode, n, slot) {
    const pg = document.querySelectorAll(".pg")[n];
    for (const el of document.querySelectorAll("[contenteditable]")) { el.removeAttribute("contenteditable"); el.classList.remove("editing"); }
    for (const el of document.querySelectorAll(".selslot")) el.classList.remove("selslot");
    edit = mode && pg ? { mode, n, slot: slot || 0, pg } : null;
    if (!edit) return;
    if (mode === "text") {
      for (const el of pg.querySelectorAll("[data-text]")) {
        if (!el.textContent.trim() || el.closest("[data-index]")) continue;
        el.contentEditable = "plaintext-only";
        el.spellcheck = false;
        el.classList.add("editing");
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

  document.addEventListener("pointerdown", e => {
    if (!edit || edit.mode !== "photo") return;
    const im = e.target.closest && e.target.closest("[data-slot]");
    if (!im || !edit.pg.contains(im)) return;
    const slot = +im.dataset.slot;
    if (slot !== edit.slot) { edit.slot = slot; window.editMode("photo", edit.n, slot); bridge("slot", { page: edit.n, slot }); return; }
    e.preventDefault();
    im.setPointerCapture(e.pointerId);
    const [x0, y0] = (im.style.objectPosition || "50% 50%").split(" ").map(parseFloat);
    const sx = e.clientX, sy = e.clientY, r = im.getBoundingClientRect();
    const clamp = v => Math.max(0, Math.min(100, v));
    im.onpointermove = m => {
      im.style.objectPosition = `${clamp(x0 - (m.clientX - sx) / r.width * 160).toFixed(1)}% ${clamp(y0 - (m.clientY - sy) / r.height * 160).toFixed(1)}%`;
    };
    im.onpointerup = im.onpointercancel = m => {
      im.onpointermove = im.onpointerup = im.onpointercancel = null;
      if (Math.abs(m.clientX - sx) + Math.abs(m.clientY - sy) > 3) bridge("crop", { page: edit.n, slot, pos: im.style.objectPosition });
    };
  });
})();
