# dot-lab

그냥 시켜봄 — 이게 될까? 싶던 걸 일단 시켜 봅니다. (D:OT lab · Will this even work? We try it anyway.)

- Play: https://dot-connects.github.io/dot-lab/
- Blog (one post per work): [www.dotconnects.kr/search/label/실험](https://www.dotconnects.kr/search/label/%EC%8B%A4%ED%97%98)

One folder per work under `works/<yyyy-mm-dd>-<slug>/`:

- `brief.md` — the one-page plan
- `mockups/` — first-screen mockups shown to the operator
- `site/` — the work itself (static, served by GitHub Pages)
- `out/` — reel and share images
- `manifest.json` — what content-system ingests to post

Spec: `content-system/docs/superpowers/specs/2026-10-20-daily-lab-channel-design.md` (task T-0162).

## Before a work ships (learned on #001, 2026-10-21)

**Index tile (2026-10-24).** Add the work to `index.html` as the first `article.work`: a square
`thumbs/<NNN>.jpg` (600×600, no title text in the crop), the title on the image, one-line description, the
input verb, and "해설 →" to its blog post. Move the `NEW` badge to it and keep the `NEXT` placeholder last.
content-system holds a ready manifest whose work is not linked from `index.html` and retries each tick until it is (`lab_work.listed`).

**Back to the list (2026-10-26).** Every work links the root list (`href="../../../"`) twice: a small
"‹ 시켜본 것들" chip in the top-left of the start screen (hidden while playing, so a stray tap can't leave the game),
and "시켜본 것 더 보기 →" under the result's retry/share buttons. #001 and #002 show both.

**Touch input inside Threads / Instagram.** Most players open the link inside the app's own browser. With only
`touch-action: none` the drawing pad scrolled the page, and at scroll top Threads took every downward drag as
"close the browser" (up strokes drew, down strokes closed the window). Both fixes are needed on any work that
reads finger drags (see `works/2026-10-21-draw-korea/src.html`):

```js
// 1. the page keeps the gesture: cancel the touch events on the pad (buttons inside the pad stay native)
for (const t of ["touchstart", "touchmove"])
  pad.addEventListener(t, e => { if (!e.target.closest("button")) e.preventDefault(); }, { passive: false });
// 2. never sit at scroll top, or the app's pull-down/close gesture wins
document.body.style.minHeight = "calc(100% + 2px)";
const nudge = () => { if (scrollY < 1) scrollTo(0, 1); };
addEventListener("load", nudge); addEventListener("scroll", nudge, { passive: true });
pad.addEventListener("touchstart", nudge, { passive: true });
```
plus `html,body{overscroll-behavior:none}`. A "open in browser" banner was tried and dropped: not needed once
both fixes are in.

**Check it.** Headless Chrome at phone size with CDP `Input.dispatchTouchEvent` (synthetic pointer events pass while
real phones fail): a tall closed drag must leave `scrollY` unchanged and land every point; buttons must still tap.
Then the operator opens the Threads post on the phone.

**Pages cache.** GitHub Pages sends `max-age=600`: a phone can show the previous build for ~10 minutes after the
deploy. Wait that long (or close the in-app window) before judging a fix on the phone.

**Blog post (`blog_html`, T-0174 3b, 2026-10-26).** The post is the only part Google reads (it never reads inside
the iframe), so it must explain the work, not just link it. Reader = players; a short dev note goes last. Model posts:
[#001](https://www.dotconnects.kr/2026/10/001.html), [#002](https://www.dotconnects.kr/2026/10/2026-10-23-kimchi-fried-rice.html).
Required:
- Title = the search phrase first, the number last: `지도 안 보고 한반도 그리기 테스트, 몇 점 나올까? (실험 #001)`.
- Lead with a real result from the work (a measured score, a funny fail line), what you do, how long, which device.
- A big "바로 해 보기" link near the top; the iframe goes **last** ("여기서 바로 해 보기") — a drawing pad high in the
  post eats the page scroll on phones.
- Scoring in plain words + a small table; every number checked against the work's code, not the brief (#002's brief
  had outdated weights).
- "만든 이야기": what failed first and what changed, with a source link for any data or research used.
- Links: the other works' posts (at least one; add the new post to older posts' "다른 실험") + the list page, and the
  work folder on GitHub.
- Screenshots: list up to 8 png in `shots` (paths inside the work folder) and put `<p>[[shot-1]]</p>`… where they go.
Guidance (skip what doesn't fit the work): tips derived from the scoring, one "왜 내 점수가 낮지?" section,
1,800–2,500 Korean chars, `<h2>` sections. Never invent an experience; say who measured what.

**After a work (or tool review) is published, never edit its manifest.** content-system keys it by the manifest's
hash: a changed manifest of a posted one is now skipped (no second reel), so the edit does nothing. Fix the live blog
post instead.

**Manifest.** `status: "ready"` is what content-system ingests (one story per manifest content; editing a ready
manifest replaces the unpublished story). `narration` (optional) is voiced in the lab voice over the lab music;
the Instagram `caption` ends with "프로필 링크에서 바로 해 볼 수 있어." (captions are not clickable; the profile
links this repo's root list), `caption_threads` ends with the work URL. Add the work to `index.html`, newest on top.
