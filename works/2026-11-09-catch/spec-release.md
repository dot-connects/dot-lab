# 리얼뽑기 — Google Play 출시 준비 (spec)

2026-11-16. Operator picks: Play Store (personal account, testers via ACT Party), purchases verified on the
server, Play Integrity on every AI call. Operator steps live in `dot-lab/scripts/wizard-play-console.bat`.
Scope: `app/`, `worker/`, a privacy page on the dot-lab site, store listing files. Not in scope: iOS, ads, new features.

## Decisions (operator 11-16)

| # | Thing | Pick |
|---|---|---|
| 1 | Store | Google Play, personal account shared with duo-4cut (T-0188) and parking (T-0189) |
| 2 | Testers | ACT Party 1:1 exchange, 14–15 trades, after the closed-test release |
| 3 | Purchase check | Worker verifies each purchase token with Google before drawing |
| 4 | Abuse guard | Play Integrity: Worker accepts AI calls only from a Play-recognized copy of our app |
| 5 | Test period | pet sheets free up to 3 per device, the 4th on costs 500 won (real Play purchase). Switch off at production |
| 6 | Testers come from | the reel → lab site page → [안드로이드 먼저 해 보기] (group + opt-in); ACT Party only to top up |

## 1. Play rules that hit this app (checked 2026-11-16)

| Rule | Source | What we do |
|---|---|---|
| New apps target API 36 from 2026-08-31 | developer.android.com/google/play/requirements/target-sdk | `compileSdk`/`targetSdk` 35 → 36, AGP 8.7.3 → a version that supports 36 |
| AI image apps need an in-app report/flag for offensive output, without leaving the app | Play Console Help 13985936 | report button on every AI picture → Worker `/report` |
| Declare AI-generated store assets | Play Console Help 17262077 | answer in Console; store art is ours, not AI |
| 16 KB page size (native libs) | Play requirement for API 35+ targets | check every `.so` in the AAB (`tasks-vision`, ARCore, `libc++_shared`) and bump what fails |
| Personal account: 12 testers × 14 days | Play Console Help 14151465 | wizard menus 3–6 |
| Special-use foreground service | Play foreground-service declaration | Console declaration text + short screen video of the pet overlay (operator records) |

API 36 behaviour to re-check on the Fold: apps on large screens can no longer lock orientation or opt out of
resizing, and edge-to-edge is forced. The catch screen and menus must work in both orientations on the inner screen.

## 2. Purchases (Play Billing + server check)

Products: one consumable in-app product per sheet type, 500 won each.
`sheet_fluff` (털뭉치 pet sheet), `sheet_figure` (피규어 pet sheet). Group sheets (up to 3 caught together)
use the same products (one purchase = one sheet, whatever the group size), as today.

Flow:

```
app: BillingClient.launchBillingFlow(sheet_x)
  → onPurchasesUpdated(PURCHASED, purchaseToken)
  → POST worker /sheet  { ..., product, purchaseToken }  + header x-integrity
worker:
  1. Integrity verdict ok?                       no → 403 "integrity"
  2. D1 purchases[token]: done → 200 cached result (same sheet again)
                          drawing (< 3 min) → 409 "busy"
                          absent → Google purchases.products.get(pkg, product, token)
                                   purchaseState == 0 (purchased) and not consumed? else 402 "purchase"
  3. mark drawing → OpenAI → store the sheet in R2 under the token → mark done
  4. Google purchases.products.consume (also acknowledges) → 200 sheet
  on OpenAI failure: mark failed, do NOT consume → the app shows "돈은 나가지 않았어요" and may retry with the same token;
  an unconsumed purchase is refunded by Google after 3 days if never acknowledged.
app: on start, queryPurchasesAsync → any PURCHASED but unconsumed token is re-sent (recovers a crash mid-draw).
```

- Test-period free sheets: Worker var `FREE_SHEETS` (closed test = "3", production = "0").
  `/sheet` without a purchase token is accepted while D1 `free[device] < FREE_SHEETS` (integrity-verified device id;
  counted only after the sheet is stored, so a failed draw does not use one up). The app asks `GET /quota` → `{free_left}`
  and shows "테스트 기간 무료 · N장 남음" instead of the price until it reaches 0, then the 500-won button.
  A reinstall makes a new device id (another 3); the month cap bounds that. Free sheets use the same drawing path.
- Token state machine (`absent → drawing → done | failed`, failed → drawing on retry) is pure JS and TDD'd.
- One token draws one sheet. A `done` token returns the stored sheet (R2, 30 days) instead of drawing again.
- Price text comes from `ProductDetails.formattedPrice`, not the hard-coded `PRICE_WON`.
- Debug path stays for sideloaded dev builds: `x-purchase` = `DEBUG_PURCHASE` secret, accepted only when
  the build is debuggable AND integrity is skipped by the same secret. Release builds carry no token and hide
  the developer panel's token field.
- Google calls use one service account (JSON key in a Worker secret `GOOGLE_SA`, OAuth JWT signed with WebCrypto
  RS256). Never log request headers or the key (lesson: OpenAI key leaked via `wrangler tail`, 11-12).
- Library: Play Billing Library current major (check the minimum Play accepts at build time).

## 3. Play Integrity

- App: Standard integrity API (`IntegrityManagerFactory.createStandard`), prepare a token provider at start,
  request a token per AI call with `requestHash = sha256(route + device + body hash)`.
- Worker: `playintegrity.googleapis.com/v1/{pkg}:decodeIntegrityToken` with the same service account.
  Accept when `appIntegrity.appRecognitionVerdict == PLAY_RECOGNIZED`, package and cert digest match,
  `deviceIntegrity` contains `MEETS_DEVICE_INTEGRITY`, `requestHash` matches, token age < 5 min.
- Applies to `/transform` (free previews) and `/sheet`. The month cap (`MONTH_CENTS`) and per-device/IP day caps stay.
- If Google's API is down: previews answer 503 (try later); paid sheets keep the purchase unconsumed (no money lost).

## 4. AI picture report

- A small "신고" text button under every AI picture (detail screen, pet sheet preview).
- Tap → bottom sheet with 3 reasons (이상하거나 불쾌해요 / 사람이 잘못 그려졌어요 / 기타) → `POST /report`
  `{device, entry, kind, style, reason}` (no image upload) → D1 `reports` → "알려 줘서 고마워요".
  The reported picture is hidden on that phone at once (can be shown again from detail).
- Operator reads reports with one `wrangler d1 execute ... SELECT * FROM reports` command (in the README); counted weekly by hand for now.

## 5. Release build

- Upload key: Claude creates `%USERPROFILE%\.catchpet-upload.jks` with `keytool`; passwords in user env vars
  `CATCHPET_KS_PASS`; `signingConfigs.release` reads them; nothing under the repo. Play App Signing holds the app key,
  so a lost upload key can be reset through Play support.
- `bundleRelease` → AAB at `%LOCALAPPDATA%\catchpet-build\app\outputs\bundle\release\app-release.aab` (wizard default).
- `versionCode` from `versionName` (0.6.0 → 600), first Play build 0.6.0.
- Minify off for the first closed test (TFLite/ARCore reflection risk); revisit before production.
- Release hides: developer panel, AR switch stays behind the hidden long-press.
- Dev app (operator 11-16): the debug build is `kr.dot.catchpet.dev` "리얼뽑기 개발", installs beside the Play app,
  carries the operator's `DEBUG_PURCHASE` token: previews and pet sheets redraw without limits (tap a drawn picture,
  [다시] under a bought sheet). Worker: the token skips integrity, billing and day/entry counters and spends its own
  month ceiling `DEV_MONTH_CENTS` (2000); `MONTH_CENTS = "0"` still stops it.

## 6. Store paperwork (Claude drafts, operator pastes)

- Privacy policy page: `works/2026-11-09-catch/page/privacy.html` (Korean, plain): camera frames stay on the phone;
  a caught picture is sent to our server and OpenAI only when the user asks for an AI picture; OpenAI is
  called with storage off; purchase tokens and reports kept in Cloudflare D1; sheets in R2 for 30 days;
  no accounts, no ads, no analytics; contact = developer email.
- Data safety answers, content rating answers, target audience (13+, not for children), app access (no login),
  ads (none), AI-content declaration: `store/answers.md`.
- Listing: name 리얼뽑기, short line, description (no "AI" in the first line, per lab copy rule), icon 512,
  feature graphic 1024×500, phone screenshots ×4 (operator captures on the phone from a shot list; Claude frames them).
- Foreground-service declaration text + the shot list for the operator's 30 s video.

## 7. Operator steps added to the wizard

Menu 7 "결제·무결성 연결" (after the app exists in Console):
Google Cloud project → enable Android Publisher API + Play Integrity API → service account → JSON key →
`wrangler secret put GOOGLE_SA` (BOM-free pipe) → Play Console 사용자 및 권한: invite the service account
(view financial data, manage orders) → 앱 무결성: link the Cloud project → 설정 → 라이선스 테스트: add the operator's
Gmail (test purchases cost nothing) → in-app products `sheet_fluff`, `sheet_figure` at 500 won, active.

## 8. Tests and checks

- TDD: Worker token state machine, purchase verdict parsing, integrity verdict parsing (fixtures from Google's
  documented JSON), JWT claim building. App: price fallback, unconsumed-token recovery decision.
- Independent review (high risk: money): one Codex or fresh-Claude review of the Worker purchase path diff.
- Live: closed-test build on the operator's phone with a license tester account — buy fluff sheet, kill the app
  mid-draw, reopen → sheet arrives once; report a picture; airplane mode → "돈은 나가지 않았어요".
- `zipalign -c -P 16` / bundletool check for 16 KB on every `.so`.

## 9. Lab site page and manifest

- New landing page `works/2026-11-09-catch/page/index.html` (the frozen web game `site/` stays unlisted and untouched;
  earlier works untouched): the reel, three lines on what the app does, [안드로이드 먼저 해 보기] → a short two-step
  box (그룹 가입 → 참여 링크), "아이폰은 아직 안 돼요", later swapped for a Google Play badge (one constant).
- Added to the lab site list like the other works; `manifest.json` (kind work, reel, cover, caption, blog_html,
  link = the landing page, status ready only once the opt-in link works). content-system posts blog/Instagram/Threads.

## Open (not blocking the plan)

- Free sheets after production: `FREE_SHEETS = 0` by default; the operator may pick a launch freebie later.

- Group-sheet price stays 500 won although its OpenAI cost is higher — revisit with real numbers after release.
- Weekly report count could later go to the hub bot; not now.
