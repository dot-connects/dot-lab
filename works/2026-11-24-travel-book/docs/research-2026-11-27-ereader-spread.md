# E-reader / photo-book viewer research: spreads on a phone (2026-11-27)

Method: web search (standard mode) plus prior knowledge of the apps. Confidence tags: **[S]** = backed by a source below, **[K]** = general knowledge, not verified by a fetched source this session. Official docs for Kindle, Kobo, RIDI, Millie, Yes24, Issuu, Blurb, Mixbook and Snapfish were not found. Treat those rows as unverified.

## 1. Two-page (spread) mode on a phone

| App | Offered when | Toggle | Cover in spread | Toggle while on cover |
|---|---|---|---|---|
| Apple Books | Landscape, mainly iPad. Apple's iPad guide: turn to landscape to see two pages [S1]. iPhone not documented. PDFs do not spread [S2]. | Automatic from orientation. Orientation lock blocks it [S2]. Some users say iOS 14 removed single-page view [S3]. | [K] Alone | [K] No visible jump, cover is page 1 either way |
| Google Play Books | [K] Fixed-layout/comics get two pages on tablets/landscape, phones stay one page. Help: "not all books offer more than one page layout" [S4]. | [K] Display options | Unverified | Unverified |
| Kindle / Kobo | [K] Reflowable books get two columns on tablets in landscape. Kindle comics use Guided View (one panel at a time). | Unverified | Unverified | Unverified |
| RIDI / Yes24 / Millie | [K] Comic viewers (RIDI, Yes24) offer two-page view in landscape. Millie is reflow text, no spread. No official doc found. | Unverified | Unverified | Unverified |
| FlippingBook, Pocketmags, Publitas (same pattern as Issuu/Zinio/Readly) | Portrait = one page, landscape = two [S5][S6]. Publitas has an explicit "single page view" on mobile [S7]. | Automatic from orientation | [K] Flipbook convention: cover alone on the right, blank left | Unverified |
| Blurb / Mixbook / Snapfish | [K] Web preview shows spreads with arrows. On a phone it is a shrunk spread. | Unverified | [K] Closed-book cover first | Unverified |

Takeaway: no phone app found offers a spread in portrait. Spread means landscape, and orientation is the toggle. Print/EPUB convention: cover is page 1 on the **right** with a blank left, so pairs are (blank,1), (2,3), (4,5). Some apps center the cover alone instead. Either way the cover is never paired with page 2.

## 2. Position kept on toggle?

- Apple Books and Play Books keep a location marker, so the current page is retained [K].
- Fixed-layout viewers rotate to the spread that contains the current page [K].
- No source documents a jump to the cover. Unverified.
- Design rule: store the **page index**, not the spread index.

## 3. Page turn interaction

- Tap zones: left/right edges turn the page, centre tap toggles chrome. Google help: "tap the center of the page, then swipe left or right ... slider bar at the bottom" [S4]. Edge tap in Play Books [S8].
- Swipe: universal.
- Animation: Apple Books curl by default, with slide/none options [K]. Kindle and Kobo offer curl/slide/none [K]. Play Books plain slide [K]. Flipbook viewers flip or slide [K].
- Scrubber: bottom slider with page label, plus thumbnails in Apple Books and Play Books [S4][K]. It shows and hides with the chrome.
- Chrome: top and bottom bars toggle on a centre tap, and auto-hide after the first page turn [K].

## 4. Fixed-layout on a portrait phone

- Magazine apps (Pocketmags, Zinio, Readly) show one page fitted to width in portrait. Double-tap zooms, pinch gives fine control [S5][S6]. Spreads are split into single pages [K].
- Publitas: portrait snaps to one side of a spread, and pinching out shows the whole spread, effectively a spread overview [S7].
- Kindle comics: Guided View via double-tap. Kobo/RIDI comics: double-tap zoom with page-fit default [K].
- Photo-book previews show the spread small, with zoom or lightbox for one page [K]. Apple Photos memories is video, not a book viewer [K].

## 5. Opening from a library list

- Tap the cover (grid) or the row to open [K].
- Apple Books and Kindle expand the cover into the reader. Play Books fades. Pocketmags downloads first, then opens [S6][K].
- Cover and title are one hit target, and the last-read page is restored with no prompt [K].
- Long-press or "..." gives delete/details, not open [K].

## Sources

- [S1] https://support.apple.com/en-la/guide/ipad/ipadd3c9dc47/ios
- [S2] https://discussions.apple.com/thread/253125826
- [S3] https://discussions.apple.com/thread/251838419
- [S4] https://support.google.com/googleplay/answer/9755756
- [S5] https://flippingbook.com/help/online/getting-started/fbo-publications-on-mobile
- [S6] https://help.pocketmags.com/support/solutions/articles/5000890417-reading-an-edition
- [S7] https://www.publitas.com/blog/product-update-single-page-view/
- [S8] https://androidauthority.com/play-books-version-3-6-update-brings-tap-to-scroll-635111

## Recommendations for a photo travel-book viewer on a portrait phone

1. **Default to one page, fit-to-width, in portrait.** Spreads appear only in landscape in every app checked. A portrait spread should be an opt-in overview (Publitas pinch-out pattern).
2. **Spread mode: cover on the right, blank left.** Pairs are (blank,1), (2,3), and so on. Page 1 is the same image in both modes, so toggling on the cover causes no jump.
3. **Persist the page index, not the spread index.** On toggle, spread = floor((page + 1) / 2). Never reset to the cover.
4. **Edge taps (about 25 percent each side) turn the page, centre tap toggles chrome, plus horizontal swipe.** Plain slide by default, curl optional.
5. **Bottom scrubber with "n / N" label and thumbnail strip**, shown and hidden together with the top bar. Hide chrome after the first turn.
6. **Double-tap zooms to the photo (2x, again to fit), pinch is free zoom, pinch-out past 1x returns to the spread overview.** Disable page-turn swipe while zoomed.
7. **Library: tap cover or whole row to open**, with cover-to-reader expand animation and last-page restore, no confirmation.
8. **Do not rely on orientation lock.** Add an explicit one/two-page button in the chrome, since Apple users complained about lock and missing single-page view [S2][S3].
