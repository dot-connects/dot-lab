# 리얼뽑기 Play Release + Ad Reel — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** a signed 0.6.0 AAB ready for a Play closed test (Play Billing checked on the server, Play Integrity, 3 free sheets in the test, an AI-picture report), the store paperwork, a lab landing page + manifest, the operator wizard's last menu, and the ad reel.

**Architecture:** the Worker becomes the only place that decides "may this device get a picture": it checks a Play Integrity token, then either a free-sheet counter or a Google-verified purchase token held in a D1 ledger; sheets are kept in R2 so a token never draws twice. The app adds a thin Billing layer, an Integrity token provider and a report sheet; everything else is unchanged.

**Tech Stack:** Cloudflare Worker (JS, `node --test`, `node:sqlite` for D1 fakes), D1, R2, Google Play Developer API v3, Play Integrity API (standard), Android/Kotlin + Compose, Play Billing Library, Play Integrity library, ffmpeg + headless Chrome for the reel.

**Spec:** `works/2026-11-09-catch/spec-release.md`, `works/2026-11-09-catch/spec-reel.md`.

## Global Constraints

- Products: `sheet_fluff`, `sheet_figure`, consumable, 500 won each; one purchase token = one sheet (group or single).
- `FREE_SHEETS`: closed test `"3"`, production `"0"`; counted per device only after the sheet is stored.
- Integrity accept rule: `PLAY_RECOGNIZED`, package `kr.dot.catchpet`, cert digest = `CERT_SHA256`, `MEETS_DEVICE_INTEGRITY`, `requestHash` match, token age < 300 s.
- Debug bypass = the dev app `kr.dot.catchpet.dev` (done 11-16, 0e85d1d): header `x-purchase` equal to secret `DEBUG_PURCHASE` skips integrity, billing and day/entry counters, on `DEV_MONTH_CENTS`; release builds carry no token. Keep this path working in Task 4.
- Never log request headers, purchase tokens, integrity tokens or the service-account key. No `wrangler tail` on this Worker.
- Secrets piped from Windows must be BOM-free (`secret()` strips; wizard uses `New-Object System.Text.UTF8Encoding $false`).
- targetSdk/compileSdk 36. versionName 0.6.0, versionCode 600.
- No "AI" word in lab identity copy (page title, captions); the store description may name it after the first line.
- Gradle and headless Chrome never during 06:00–07:00, 08:30–09:10, 15:30–16:30, 22:00–23:50 KST (trading tick).
- Worker deploy goes out together with the 0.6.0 APK (0.5.5 previews stop working without a token).

---

## Part 1 — Worker (money path, TDD, independent review after Task 4)

### Task 1: Google auth + API calls (`worker/src/google.js`)

**Files:** Create `worker/src/google.js`, `worker/test/google.test.js`; modify `worker/package.json` (`"test": "node --test test/"`).

**Interfaces — Produces:**
- `googleToken(env, fetchFn = fetch, now = Date.now()) → Promise<string>` (cached in module scope until `exp - 60 s`)
- `getPurchase(env, product, token, fetchFn) → Promise<object>` (raw v3 `ProductPurchase` JSON; throws `{status}` on non-2xx)
- `consumePurchase(env, product, token, fetchFn) → Promise<boolean>`
- `decodeIntegrity(env, integrityToken, fetchFn) → Promise<object>` (the `tokenPayloadExternal` object)
- env: `GOOGLE_SA` (service-account JSON string), `PKG` (`kr.dot.catchpet`)

- [ ] **Step 1: failing test** — generate an RSA key with `crypto.subtle.generateKey({name:"RSASSA-PKCS1-v1_5", modulusLength:2048, publicExponent:new Uint8Array([1,0,1]), hash:"SHA-256"}, true, ["sign","verify"])`, export pkcs8 → PEM, build `GOOGLE_SA = JSON.stringify({client_email:"sa@x.iam.gserviceaccount.com", private_key: pem, token_uri:"https://oauth2.googleapis.com/token"})`. Fake `fetchFn` records calls and returns `{access_token:"at", expires_in:3600}` for the token URI.

```js
test("googleToken signs a JWT the public key verifies and caches it", async () => {
  const t1 = await googleToken(env, fake, 1_000_000);
  const t2 = await googleToken(env, fake, 1_000_000 + 60_000);
  assert.equal(t1, "at"); assert.equal(t2, "at");
  assert.equal(fake.calls.filter(c => c.url.includes("oauth2")).length, 1);
  const form = new URLSearchParams(fake.calls[0].body);
  assert.equal(form.get("grant_type"), "urn:ietf:params:oauth:grant-type:jwt-bearer");
  const [h, c, s] = form.get("assertion").split(".");
  const claims = JSON.parse(atob(c.replace(/-/g, "+").replace(/_/g, "/")));
  assert.equal(claims.iss, "sa@x.iam.gserviceaccount.com");
  assert.match(claims.scope, /androidpublisher/); assert.match(claims.scope, /playintegrity/);
  assert.ok(await crypto.subtle.verify("RSASSA-PKCS1-v1_5", pub, b64urlToBytes(s), new TextEncoder().encode(h + "." + c)));
});
test("getPurchase hits the v3 products URL with the bearer", async () => { /* URL ends /applications/kr.dot.catchpet/purchases/products/sheet_fluff/tokens/T1, header authorization "Bearer at" */ });
test("consumePurchase POSTs :consume and returns true on 204", async () => {});
test("decodeIntegrity POSTs {integrity_token} and returns tokenPayloadExternal", async () => {});
test("a 401 from Google throws {status:401} and never echoes the token", async () => {});
```

- [ ] **Step 2:** `cd worker && node --test test/google.test.js` → FAIL (module missing).
- [ ] **Step 3: implement.** JWT: header `{"alg":"RS256","typ":"JWT"}`, claims `{iss, scope:"https://www.googleapis.com/auth/androidpublisher https://www.googleapis.com/auth/playintegrity", aud: token_uri, iat, exp: iat+3600}`; PEM → strip header lines → base64 → `importKey("pkcs8", der, {name:"RSASSA-PKCS1-v1_5", hash:"SHA-256"}, false, ["sign"])`. URLs: `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${PKG}/purchases/products/${product}/tokens/${encodeURIComponent(token)}` (+ `:consume`), `https://playintegrity.googleapis.com/v1/${PKG}:decodeIntegrityToken`. Parse `GOOGLE_SA` through `secret()`.
- [ ] **Step 4:** tests PASS.
- [ ] **Step 5:** commit `feat(catch-worker): google service-account auth and play api calls`.

### Task 2: Verdicts (`worker/src/verdicts.js`)

**Interfaces — Produces:**
- `purchaseVerdict(p, product) → "ok" | "pending" | "bad"` — ok when `purchaseState === 0 && consumptionState === 0`; pending when `purchaseState === 2`; else bad. Product → style map `PRODUCT_STYLE = {sheet_fluff:"fluff", sheet_figure:"figure"}`.
- `integrityVerdict(v, {pkg, certHex, requestHash, now}) → "ok" | <reason string>`
- `certHex(s)` — base64 / base64url / hex digest → lowercase hex.
- `requestHash(path, device, bodyText) → Promise<string>` = hex sha256 of `${path}\n${device}\n${hex sha256(bodyText)}`.

- [ ] **Step 1: failing tests** with fixtures copied from Google's documented payload shape:

```js
const V = { requestDetails: { requestPackageName: "kr.dot.catchpet", requestHash: "H", timestampMillis: "1000000" },
  appIntegrity: { appRecognitionVerdict: "PLAY_RECOGNIZED", packageName: "kr.dot.catchpet", certificateSha256Digest: ["q83vEjRWeJA="] },
  deviceIntegrity: { deviceRecognitionVerdict: ["MEETS_DEVICE_INTEGRITY"] } };
const O = { pkg: "kr.dot.catchpet", certHex: "abcdef1234567890", requestHash: "H", now: 1_100_000 };
test("a good verdict passes", () => assert.equal(integrityVerdict(V, O), "ok"));
test("each failure has its own reason", () => {
  assert.equal(integrityVerdict({ ...V, appIntegrity: { ...V.appIntegrity, appRecognitionVerdict: "UNRECOGNIZED_VERSION" } }, O), "app");
  assert.equal(integrityVerdict({ ...V, deviceIntegrity: { deviceRecognitionVerdict: [] } }, O), "device");
  assert.equal(integrityVerdict(V, { ...O, requestHash: "X" }), "hash");
  assert.equal(integrityVerdict(V, { ...O, now: 1_000_000 + 301_000 }), "old");
  assert.equal(integrityVerdict(V, { ...O, certHex: "00" }), "cert");
  assert.equal(integrityVerdict(V, { ...O, pkg: "x.y" }), "pkg");
});
test("purchase states", () => {
  assert.equal(purchaseVerdict({ purchaseState: 0, consumptionState: 0 }), "ok");
  assert.equal(purchaseVerdict({ purchaseState: 2, consumptionState: 0 }), "pending");
  assert.equal(purchaseVerdict({ purchaseState: 1, consumptionState: 0 }), "bad");
  assert.equal(purchaseVerdict({ purchaseState: 0, consumptionState: 1 }), "bad");
});
test("requestHash matches the shared vector", async () =>   // the same vector is asserted in the app (Task 8)
  assert.equal(await requestHash("/sheet", "dev-aaaaaaaa", '{"a":1}'), SHARED_VECTOR));
```

Compute `SHARED_VECTOR` once with Node (`crypto.createHash`) while writing the test and paste the hex literal.

- [ ] **Step 2:** FAIL. **Step 3:** implement. **Step 4:** PASS. **Step 5:** commit `feat(catch-worker): purchase and integrity verdicts`.

### Task 3: Purchase ledger + free counter (`worker/src/ledger.js`, `worker/schema.sql`)

**Files:** modify `worker/schema.sql`; create `worker/src/ledger.js`, `worker/test/ledger.test.js` (D1 fake over `node:sqlite` like the existing `D1Counts` tests).

```sql
CREATE TABLE IF NOT EXISTS purchases (token TEXT PRIMARY KEY, product TEXT NOT NULL, device TEXT NOT NULL,
  state TEXT NOT NULL, sheet_key TEXT, layout TEXT, consumed INTEGER NOT NULL DEFAULT 0, at INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS free (device TEXT PRIMARY KEY, n INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS reports (at INTEGER NOT NULL, device TEXT, entry TEXT, kind TEXT, style TEXT, reason TEXT);
```

**Interfaces — Produces** (class `Ledger(db)`):
- `claim(token, product, device, now) → {go:"new"} | {go:"draw"} | {go:"cached", key, layout, consumed} | {go:"busy"}`
  - absent → `INSERT ... ON CONFLICT DO NOTHING RETURNING token` with state `drawing`: inserted → `new` (caller verifies with Google), lost race → `busy`
  - `done` → `cached`; `failed` → set `drawing`, `at=now` → `draw`; `drawing` with `now - at < 180000` → `busy`, else take over → `draw`
- `forget(token)` (delete a `new` row when Google says bad/pending), `finish(token, key, layout)`, `fail(token)`, `markConsumed(token)`
- `freeLeft(device, total) → number`, `useFree(device)` (increment)

- [ ] **Step 1: failing tests:** fresh token → `new`; same token again while drawing → `busy`; after `finish` → `cached` with key; after `fail` → `draw`; drawing older than 180 s → `draw`; two concurrent claims on one fresh token → exactly one `new`; `forget` frees the token; `freeLeft("d",3)` 3 → `useFree` ×3 → 0.
- [ ] **Step 2–4:** FAIL → implement → PASS.
- [ ] **Step 5:** commit `feat(catch-worker): purchase ledger and free-sheet counter`.

### Task 4: Routes — integrity gate, free/paid `/sheet`, `/quota`, `/report`, R2

**Files:** modify `worker/src/index.js`, `worker/wrangler.toml`, `worker/test/worker.test.js`.

**Interfaces — Consumes:** Tasks 1–3. **Produces (HTTP):**
- `POST /sheet` body adds `product?`, `purchaseToken?`; header `x-integrity` (app) or `x-purchase` (debug). Replies `200 {mime,image,layout,free_left?}` · `402 {error:"purchase"|"pending"}` · `403 {error:"integrity"}` · `409 {error:"busy"}` · `429 {error:"free"}` (no token, free used up) · `502 model` · `503 off`.
- `GET /quota?device=` (x-app) → `{free_left, free_total}`.
- `POST /report {device, entry, kind, style, reason}` (x-app) → `200 {ok:true}`; reason ∈ `odd|person|other`.
- `POST /transform`: app path needs integrity (or debug token); a web Origin now answers `503 off` (the frozen web game falls back to its on-device picture).

Flow in `sheet()`: read raw text first (`await req.text()`, then `JSON.parse`) so `requestHash(path, device, text)` covers the exact bytes → debug token? skip to draw (old behaviour, `SHEET_PER_ENTRY` kept for debug only) → `GOOGLE_SA` missing → 503 off → `decodeIntegrity` + `integrityVerdict` ≠ ok → 403 → if `purchaseToken`: `PRODUCT_STYLE[product] === style` else 400; `ledger.claim`: `cached` → read R2 `SHEETS.get(key)`, consume if `consumed=0`, return it; `busy` → 409; `new` → `getPurchase` → verdict `pending` → `forget`, 402 pending; `bad` → `forget`, 402 purchase; then draw → `SHEETS.put("sheet/" + sha256hex(token) + ".webp", bytes)` → `finish` → `consumePurchase` (on success `markConsumed`) → 200. Model failure → `ledger.fail`, 502 (no consume). No token: `freeLeft(device, FREE_SHEETS) > 0` else 429 free → draw → `useFree` only after success → 200 with `free_left`.

wrangler.toml: `[[r2_buckets]] binding = "SHEETS"  bucket_name = "dot-lab-catch-sheets"`; vars `PKG = "kr.dot.catchpet"`, `FREE_SHEETS = "3"`, `CERT_SHA256 = ""` (filled after the first Play upload, App signing page), `CLOUD_PROJECT` not needed on the Worker.

- [ ] **Step 1: failing tests** (inject `env.google = {decodeIntegrity, getPurchase, consumePurchase}` fakes and a `Map`-backed `SHEETS`):
  - no integrity header and no debug token → 403, model not called
  - free path: 3 sheets OK (`free_left` 2,1,0), 4th → 429 free; a model failure does not use one up
  - paid path: good token → 200, R2 has the object, consume called once; same token again → 200 from R2, model called once in total
  - pending → 402 pending and the token can be claimed again later; consumed/cancelled → 402 purchase
  - product/style mismatch → 400
  - model fails → 502, not consumed, retry with same token draws
  - concurrent second request while drawing → 409
  - `/quota` → `{free_left:3, free_total:3}`; `/report` bad reason → 400, good → row in `reports`
  - web Origin `/transform` → 503 off; debug token still works on `/sheet` and `/transform` (0.5.x dev builds)
  - Update the 26 existing tests whose expectations changed (web-origin transform cases now 503).
- [ ] **Step 2–4:** FAIL → implement → `npm test` all PASS.
- [ ] **Step 5:** commit `feat(catch-worker): server-checked purchases, integrity gate, free test sheets, reports`.
- [ ] **Step 6: independent review** of `git diff <base>..HEAD -- worker/` by the side that did not implement (Codex `review` or a fresh Claude), money focus: double draw, consume before store, refund path, token leakage. One round; fix High.
- [ ] **Step 7:** create R2 bucket + 30-day lifecycle: `npx wrangler@4 r2 bucket create dot-lab-catch-sheets`, `npx wrangler@4 r2 bucket lifecycle add dot-lab-catch-sheets expire-30 --expire-days 30` (check the flag name with `--help` first); `npx wrangler@4 d1 execute dot-lab-catch --remote --file schema.sql`. Do not deploy yet (Global Constraints: deploy with 0.6.0).

## Part 2 — App

### Task 5: API 36, AGP, 16 KB

**Files:** `app/build.gradle.kts`, `app/app/build.gradle.kts`, `app/gradle/wrapper/gradle-wrapper.properties`.

- [ ] Bump AGP to the newest stable that supports compileSdk 36 (check the AGP/API table on developer.android.com), Gradle wrapper to what that AGP requires, Kotlin/Compose plugin if the build asks; `compileSdk = 36`, `targetSdk = 36`.
- [ ] `JAVA_HOME=C:/DC/tools/jdk-17 ./gradlew assembleDebug testDebugUnitTest` → green (fix only what the bump breaks).
- [ ] 16 KB: unzip the APK, for every `lib/arm64-v8a/*.so` run `llvm-readelf -l` (NDK) and check every `LOAD` segment `Align` ≥ `0x4000`; or `zipalign -c -P 16 -v 4 app-debug.apk`. Any failing library (expect `tasks-vision` 0.10.14) → bump to the first version whose `.so` pass, re-run unit tests and the seg tests.
- [ ] API 36 large-screen behaviour: orientation lock and resizability opt-outs are ignored on the Fold's inner screen; list the screens that assume portrait (`CatchScreen`, menus) in the phone checklist (README) for the operator.
- [ ] Commit `build(catch): target API 36, AGP bump, 16 KB aligned native libs`.

### Task 6: Release signing, AAB, version, release-only hides

**Files:** `app/app/build.gradle.kts`, `ui/PetScreen.kt` (developer token field), `works/2026-11-09-catch/README.md` (build section).

- [ ] Claude runs `keytool -genkeypair -v -keystore %USERPROFILE%\.catchpet-upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=D:OT"` with a generated 24-char password stored as user env `CATCHPET_KS_PASS` (PowerShell `[Environment]::SetEnvironmentVariable`). Never print the password.
- [ ] `signingConfigs { create("release") { storeFile = file(System.getProperty("user.home") + "/.catchpet-upload.jks"); storePassword = System.getenv("CATCHPET_KS_PASS"); keyAlias = "upload"; keyPassword = System.getenv("CATCHPET_KS_PASS") } }`; `release { signingConfig = ...; isMinifyEnabled = false; buildConfigField("String", "DEBUG_PURCHASE", "\"\"") }`.
- [ ] versionName `0.6.0`, versionCode `600`.
- [ ] Developer token field and debug-purchase save only when `BuildConfig.DEBUG`.
- [ ] `./gradlew bundleRelease` → AAB at `%LOCALAPPDATA%\catchpet-build\app\outputs\bundle\release\app-release.aab`; `jarsigner -verify` passes.
- [ ] Commit `build(catch): release signing config and 0.6.0`.

### Task 7: Integrity client + request hash

**Files:** create `ai/Integrity.kt`, `ai/RequestHash.kt`, test `ai/RequestHashTest.kt`; modify `ai/AiClient.kt`, `MainActivity.kt`, gradle (`com.google.android.play:integrity`, latest stable), BuildConfig `CLOUD_PROJECT` (number, filled after the wizard's Cloud step; `0` = skip and rely on debug token).

**Interfaces — Produces:** `RequestHash.of(path: String, device: String, body: String): String` (same formula and `SHARED_VECTOR` as Task 2); `Integrity.prepare(ctx)` (once, on app start); `suspend fun Integrity.token(hash: String): String?`; `AiClient` sends `x-integrity` when a token exists, `x-purchase` only in debug builds.

- [ ] **Step 1:** `RequestHashTest`: `assertEquals(SHARED_VECTOR, RequestHash.of("/sheet", "dev-aaaaaaaa", "{\"a\":1}"))` → FAIL → implement with `MessageDigest.getInstance("SHA-256")` → PASS.
- [ ] **Step 2:** `Integrity`: `IntegrityManagerFactory.createStandard(ctx).prepareIntegrityToken(PrepareIntegrityTokenRequest.builder().setCloudProjectNumber(BuildConfig.CLOUD_PROJECT).build())` → keep the provider; `token(hash)` = `provider.request(StandardIntegrityTokenRequest.builder().setRequestHash(hash).build()).await().token()`; on `StandardIntegrityException` re-prepare once, then return null (the Worker answers 403 → message "이 기기에서는 그림을 만들 수 없어요").
- [ ] **Step 3:** `AiClient.post` builds the JSON once, computes the hash over that exact string, attaches the header. Add the 403 `integrity` and 429 `free` texts to `Errors.kt` + `ErrorsTest`.
- [ ] Commit `feat(catch): play integrity token on every AI call`.

### Task 8: Billing, free quota, recovery

**Files:** create `billing/Billing.kt`, `billing/Pending.kt`, test `billing/PendingTest.kt`; rewrite `billing/Purchase.kt` (keep `PRICE_WON` as the fallback text only); modify `ai/AiClient.kt` (`sheet(..., product, purchaseToken)`, `quota()`), `ai/AiWork.kt` (`buy`), `ui/DetailScreen.kt` (button text), gradle (`com.android.billingclient:billing-ktx`, latest stable — read its release notes for the current `queryProductDetails` / `enablePendingPurchases` signatures via context7 before coding).

**Interfaces — Produces:**
- `Billing.connect(ctx)`, `Billing.price(product): String?` (`formattedPrice`), `Billing.launch(activity, product, entryId, style)` with `setObfuscatedAccountId(deviceId)` and `setObfuscatedProfileId("$style:$entryId".take(64))`, a `StateFlow<List<Purchase>>` of purchased-not-consumed.
- `Pending.decide(purchase states, saved map, profileId) → List<Resend(token, product, entryId, style)>` — pure; a PURCHASED token with an entry (from prefs `pending|<token>` or the profile id) is resent, PENDING waits, unknown entry → skipped and logged.
- `AiClient.quota(): Result<Int>` (`free_left`).

- [ ] **Step 1:** `PendingTest`: purchased + prefs map → resend; purchased + only profile id → resend with parsed entry/style; pending → none; purchased with neither → none. FAIL → implement → PASS.
- [ ] **Step 2:** `AiWork.buy(ctx, e, style)`: quota > 0 → `sheet` without token (free); else `Billing.launch`; on PURCHASED save `pending|token`, call `sheet(..., product, token)`; on 200 save the sheet as today, then `consumeAsync(token)` (second safety after the Worker's consume) and drop `pending|token`; on 402 pending show "결제 확인 중이에요"; on 502 keep the pending entry ("돈은 나가지 않았어요, 다시 눌러 주세요").
- [ ] **Step 3:** app start (`MainActivity.onResume`): `queryPurchasesAsync(INAPP)` → `Pending.decide` → resend each.
- [ ] **Step 4:** Detail button: free left > 0 → "테스트 기간 무료 · N장 남음"; else `Billing.price(product) ?: "${PRICE_WON}원"`; debug builds keep "개발판 무료".
- [ ] Unit tests green; commit `feat(catch): play billing with server check, free test sheets, purchase recovery`.

### Task 9: AI picture report

**Files:** create `ui/ReportSheet.kt`; modify `ui/DetailScreen.kt` (under each AI picture), `ai/AiClient.kt` (`report(entry, kind, style, reason)`), `dex/Dex.kt` (`hidden` set per entry+style).

- [ ] Small "신고" text under each AI picture → bottom sheet: 이상하거나 불쾌해요 (`odd`) / 사람이 잘못 그려졌어요 (`person`) / 기타 (`other`) → POST → "알려 줘서 고마워요"; the picture is hidden at once with a "다시 보기" link.
- [ ] Roborazzi shot of the sheet (existing shot-test pattern, `ButtonShotTest`) at 430 dp width; commit `feat(catch): in-app report for AI pictures`.

## Part 3 — Paperwork, page, wizard

### Task 10: Privacy page + store answers + listing

**Files:** create `works/2026-11-09-catch/page/privacy.html`, `works/2026-11-09-catch/store/answers.md`, `store/listing.md`, `store/shots.md`, `store/icon-512.png`, `store/feature-1024x500.png`.

- [ ] privacy.html (plain Korean, the lab site look): what goes where exactly as spec-release §6; contact = the developer email from the wizard state (operator fills if empty).
- [ ] answers.md: Data safety (Photos: collected, shared with OpenAI for processing, not stored by us except R2 sheets 30 days, not linked to identity, encrypted in transit, deletion = app data clear / 30-day expiry; Device ID: app-made random id; Purchase history: Google Play), content rating questionnaire answers, target audience 13+, app access (no login), ads none, AI-content declaration, foreground-service (special use) text, `PACKAGE_USAGE_STATS` purpose line.
- [ ] listing.md: name 리얼뽑기, short line ≤ 80 chars, description; shots.md: 4 phone screenshots the operator captures (catch, capsule, dex, pet on home) + a 30 s overlay video for the FGS declaration. Icon/feature graphic from the app's own art (no "AI").
- [ ] Commit `docs(catch): privacy policy and store paperwork`.

### Task 11: Landing page + lab list + manifest (mockups first)

- [ ] 3 phone-portrait mockups of the landing page as one Artifact (reel slot, three lines, [안드로이드 먼저 해 보기] → two-step box with the group link and the opt-in link, "아이폰은 아직 안 돼요", colour-shuffle button). Operator picks; fix the picked elements.
- [ ] Build `works/2026-11-09-catch/page/index.html` (links as two constants at the top; one more constant `PLAY_URL = ""` swaps the button to a Play badge when set). Headless 430 px check: first paint < 2 s, contrast measured, CDP tap on the button opens the steps.
- [ ] Add the card to the root `index.html` list (pattern of the 004 dalgona card: thumb, number 005, title, verb, 해설 link once the blog post exists).
- [ ] `works/2026-11-09-catch/manifest.json` (fields as `works/2026-11-04-dalgona/manifest.json`; `link` = landing page; `status: "draft"` until the opt-in link works, then `"ready"`).
- [ ] Commit + push (push = live site: check the page live after the Pages build).

### Task 12: Wizard menu 7 and tester order

**Files:** `dot-lab/scripts/wizard-play-console.bat` (Edit tool keeps CRLF; re-check `git ls-files --eol` and smoke-run with `DOT_PLAY_STATE` pointing at a scratch file).

- [ ] Menu 7 "결제·무결성 연결": Cloud project → enable "Google Play Android Developer API" + "Play Integrity API" → service account → JSON key download → piped into `npx wrangler@4 secret put GOOGLE_SA` from `worker/` with `$OutputEncoding = New-Object System.Text.UTF8Encoding $false`, then the JSON file deleted after a y/n → Play Console 사용자 및 권한: invite the service-account email (재무 데이터 보기, 주문 관리) → 앱 무결성: link the Cloud project, record the project number (state `CLOUD_PROJECT`, Claude puts it in BuildConfig) → App signing page: copy the SHA-256 (state `CERT_SHA256`, Claude puts it in wrangler.toml) → 설정 → 라이선스 테스트: add the owner Gmail → 수익 창출 → 인앱 상품: `sheet_fluff`, `sheet_figure`, 500원, active.
- [ ] Menu text: menu 4 (ACT Party) labelled "보충용 — 소개 페이지로 12명이 안 모일 때"; menu 3 last step tells the operator to send the opt-in link to Claude (landing page + manifest ready).
- [ ] Commit `feat(lab): wizard menu for billing and integrity wiring`.

### Task 13: Ship the closed-test build

- [ ] Before the closed test opens (operator 11-18): ask the operator whether the OpenAI API credit was topped up
  (testers' free previews 10 + sheets 5 ≈ $0.9 each; MONTH_CENTS = $20). Claude cannot read the balance; after the
  first tester calls, check `npx wrangler@4 tail` for OpenAI 429 `insufficient_quota`.
- [ ] After wizard menu 7: fill `CLOUD_PROJECT`, `CERT_SHA256`; `npm test`; `npx wrangler@4 deploy`; `bundleRelease`; hand the AAB path to the operator (wizard menu 3).
- [ ] Live check with the operator's license-tester phone: free sheet ×3 → 4th shows the price → test purchase → sheet arrives once; kill the app mid-draw → reopen → arrives once, not charged twice; airplane mode during draw → "돈은 나가지 않았어요"; report a picture → row in `reports` (`npx wrangler@4 d1 execute dot-lab-catch --remote --command "SELECT * FROM reports ORDER BY at DESC LIMIT 5"`).
- [ ] `/done` review, wrap-up (ROADMAP, T-0162, CONTEXT).

## Part 4 — Reel (spec-reel.md; can run in parallel with Parts 1–3)

### Task 14: Footage
- [ ] Search Pexels / Pixabay (API or site) for: sleeping cat on a sofa in warm light; a cat pawing at a phone/screen; hand raising a green-screen phone in a living room. 6–10 candidates each → contact sheets (`ffmpeg -vf fps=2,scale=320:-1,tile=6x2`) → pick, preferring one cat across shots 1 and 5.
- [ ] `works/2026-11-09-catch/reel/SOURCES.md` (URL, author, licence line); clips under `reel/stock/` (git-ignored like photo-booth `reel/`).

### Task 15: Storyboard mockups
- [ ] One Artifact, 3 variants (grade, type, pacing), real frames from the picked clips, phone portrait; operator picks.

### Task 16: Pet from the stock cat
- [ ] Cut-out of the cat from a frame with the app's own seg path (or `research/` tooling), then Worker `/sheet` fluff 12-cell with the debug token; save cells for the reel.

### Task 17: Render
- [ ] Scenes as HTML/canvas with the fake clock at 30 fps (`references/techniques.md` "fake-clock 녹화"), claw/capsule/dex/pet/talk cloud from app assets; stock clips graded with one curve (`ffmpeg eq/curves`), green screen replaced (`chromakey` + overlay of the rendered screen), motion blur on fast moves (render 120 fps → `tmix`/`minterpolate` to 30); captions in Jua/고운돋움, heavy and wide.
- [ ] Checks: `mpdecimate` unique frames, scene jumps (`select='gt(scene,0.06)'`), caption contrast on the busiest frame, first 2 s frame sheet.

### Task 18: Voice, mix, review, out
- [ ] 2–3 voice samples on the video (Gemini TTS, bright young female; budget the free quota), operator picks; mix with the app's `Sfx` sounds and lab music (photo-booth `mix8.py` pattern).
- [ ] One 6-persona review on 0.5 s frame sheets; fix the weakest shot.
- [ ] `out/reel.mp4`, `out/cover.png`; manifest media fields; status stays `draft` until the opt-in link works.
