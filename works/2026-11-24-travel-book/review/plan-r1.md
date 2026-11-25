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

# Round 2 (2026-11-25, one Opus reviewer)
High 3, all fixed in plan.md: createTimeCond filters DATE_ADDED (use AdvancedCustomFilter on DATE_TAKEN); D1 cannot bind Infinity (settle with a plain UPDATE); structured output has no array/length constraints (plain arrays, checkPlan cuts).
Mediums folded: stream means status 200 -> errors in the body; Glide thumbnails fit the short side (pass aspect-fit sizes, originBytes for original); GPS via setRequireOriginal + ExifInterface; live schema call measures thinking tokens before deploy; webViewAssetLoader lives in InAppWebViewSettings + autoplay setting; tz packages removed; cents->mills; seed one mp4; Range fallback to poster.

# Round 3 (2026-11-25, one Opus reviewer)
High 1, fixed: ColumnWhereCondition on a date column silently empties WHERE -> DateColumnWhereCondition x2 + mediaType in (1,3).
Mediums fixed: body errors in the test list, upstream API errors refund, output_tokens (thinking included) drives max_tokens, orientated sizes. Lows fixed in code: capEvenly buckets by tripDay, Takeout *TimestampMs.

# Round 4 (2026-11-25, one Opus reviewer) — no High, loop stops.
Mediums folded: keep the reservation after message_start (code already does), keep the screen on during make(), original quality = 4000 px JPEG thumbnail (no EXIF GPS), DATE_TAKEN-null photos via DATE_ADDED, one release (R8) run before the operator APK, M1 theme a has no essay/map/back page (say so in the report).

# plan-r2 review (2026-11-25 night, one round: failure + implementer/rules)
High 7 (clips lost in the split, image caps past 60/48, /write output budget, must-include overflow, 0-photo albums missing from the filtered path list, M1 diary step impossible, 46 screens in one checkbox) — all closed in plan-r2 "리뷰 반영" 1–14.
