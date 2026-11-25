# Plan review round 1 (2026-11-25, 4 lenses)

High (13) and what changed in plan.md:
- zoom-out: 6 themes before any real run -> milestones M1 (theme a end to end on the operator's phone) then M2.
- zoom-out/naive: mockup pages are fixed compositions -> rule "one .pg = one layout", slot orientation from photo-credits.json; b/c taken from r5 fragments (generators write to a wrong path).
- naive: undefined shared types, PageSpec.crop missing, no pipeline glue, WebView origin unclear -> shared types block, pipeline.dart task, WebViewAssetLoader one origin.
- failure: cost reservation smaller than max_tokens output; overspend not recorded -> reserve on max_tokens 12000, integer mills, settle with no limit in waitUntil.
- failure: 120 s timeout + SDK retries double-bill -> stream with keep-alive, maxRetries 0, 300 s.
- failure: ACCESS_MEDIA_LOCATION is runtime -> PermissionRequestOption mediaLocation, limited-access prompt.
- failure: pure-Dart decode of originals/HEIC -> platform thumbnails only.
- failure: export bitmaps in dp -> size / devicePixelRatio, video posters.
- rules: app token in code -> --dart-define-from-file, gitignored.
- rules: real-trip numbers only on emulator -> Task 12 operator phone run sets the cost constant.
- rules: public Pages repo -> no sample photos committed, font licences shipped.
Mediums folded: date query widened +-14 h, timezone from EXIF wall clock (drops 2 packages), 60-image budget split, video frames across length, text-block parsing, day cap + rate limit, CPU check, Media3 details, atomic book.json, gate() for spec failure rows, weather dropped (needs coordinates to a third party), Latin fonts bundled.
