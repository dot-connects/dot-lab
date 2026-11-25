// Book runtime: renders book.json pages with the theme's templates (assets/book/themes/<t>/layouts.html).
// Flutter calls window.renderBook(book); JS talks back through flutter_inappwebview's callHandler.
//
// book = { id, theme, format: "phone"|"a5"|"square", pages: [PageSpec], media: { <shotId>: { src, frames?: [src], video? } } }
// PageSpec = { layout, ids, crop, text }   (lib/core/models.dart)
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
      img.src = frame != null && m.frames ? m.frames[+frame] || m.src : m.src;
      img.style.objectPosition = (spec.crop && spec.crop[i]) || img.dataset.crop || "50% 50%";
      if (m.video && frame === "0") {
        // the clip plays in place of its first frame, only while visible; export keeps the poster
        const v = document.createElement("video");
        Object.assign(v, { muted: true, loop: true, playsInline: true, poster: img.src, src: m.video });
        v.style.objectPosition = img.style.objectPosition;
        v.dataset.clip = "1";
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
      while (el.scrollWidth > max && fs > 10) { fs *= 0.92; el.style.fontSize = fs + "px"; }
    }
  }

  const seen = new IntersectionObserver(es => {
    for (const e of es) for (const v of e.target.querySelectorAll("video[data-clip]")) e.isIntersecting ? v.play().catch(() => {}) : v.pause();
  }, { threshold: 0.6 });

  window.renderBook = async function (book, opts = {}) {
    current = book;
    await loadTheme(book.theme);
    document.body.classList.toggle("export", !!opts.exportMode);
    document.body.classList.toggle("tone", !!book.toneMatch);
    const root = document.getElementById("book");
    root.replaceChildren();
    book.pages.forEach((spec, n) => {
      const t = document.getElementById(spec.layout);
      if (!t) return;
      const sheet = document.createElement("div");
      sheet.className = "sheet";
      const pg = t.content.firstElementChild.cloneNode(true);
      fill(pg, spec, book, n);
      sheet.append(pg);
      root.append(sheet);
      seen.observe(pg);
      let timer = null;
      pg.addEventListener("pointerdown", () => { timer = setTimeout(() => bridge("longPress", { page: n }), 550); });
      for (const ev of ["pointerup", "pointercancel", "pointerleave"]) pg.addEventListener(ev, () => clearTimeout(timer));
    });
    await document.fonts.ready;
    root.querySelectorAll(".pg").forEach(fitBig);
    bridge("rendered", { pages: book.pages.length });
  };

  /** Export: show one page alone (Flutter screenshots it), video slots as posters. */
  window.showPage = function (n) {
    const sheets = document.querySelectorAll(".sheet");
    sheets.forEach((s, i) => (s.style.display = i === n ? "" : "none"));
    for (const v of document.querySelectorAll("video[data-clip]")) v.pause();
    return new Promise(r => requestAnimationFrame(() => requestAnimationFrame(() => r(true))));
  };

  window.currentBook = () => current;
})();
