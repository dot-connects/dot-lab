# 어디댔어 v3 — 가족 → 그룹 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development or superpowers:executing-plans. Steps use `- [ ]`.

**Goal:** One-family model → devices with ≤3 groups and per-car sharing (Worker `/v3` + `/v2` compat, one-shot D1 migration, app v3), cut over in a maintenance window.

**Architecture:** One store class (`D1Store`) over new tables (`devices`, `memberships`, `cars3`, `car_groups`, `parks3`, `car_map`); `src/core.js` holds the domain ops (auth, authz, park CAS, masking, leave, fan-out, state) called by `src/v3.js` and a thin `src/v2.js`. A Python tool migrates families once. The app swaps `FamilyStore` for `GroupStore`, adds `Op.Share` and `State.parkId`, and moves sharing + alarms into the car screen.

**Tech Stack:** Workers, D1, R2, FCM; Node 24 `node --test` + `miniflare`; Python `sqlite3`/`unittest`; Kotlin/Compose, Robolectric, Roborazzi, WorkManager.

**Spec:** `spec-v3-groups.md` (§-refs point there) + `contracts-v2.md` + mockup `mockups/v4-groups/index.html`. Paths relative to `C:\DC\DD\dot-lab\works\2026-11-02-parking-floor\`; `$A` = `app-v2/app/src/main/java/kr/dot/wherepark`, `$T` = `app-v2/app/src/test/java/kr/dot/wherepark`.

## Global Constraints

- **[Codex]** tasks run via `codex-run.ps1` (background Bash, no network). Codex **never** runs `wrangler`, `npm install`, deploys or anything `--remote`; it edits files and runs `npm test`, `python -m unittest`, Gradle. **[Claude]** tasks need network, wrangler, Korean UI copy or judgement. Usage may move a task; network/wrangler steps stay with Claude.
- Done = the task's tests **and** that side's full suite pass. Delegated work: Claude reads `git diff`, runs `git show HEAD | grep -n '??'` (mojibake), checks the diff stays inside the task's Files.
- **Order:** Phase 1 Worker → 2 migration → 3 app data → 4 app UI → 6 smoke → 7 APK → 5 cutover (runbook written in 5, run last). Phases 1–2 may run beside phase 3 (worker/ vs app-v2/); inside a phase, sequential; never two writers on one file.
- Worker: `cd worker && npm test` (= `node --test test/`, every file). Migration: `cd worker && python -m unittest tools/test_migrate_v3.py tools/test_migrate_v2.py`.
- **Deviation from spec §8 (same coverage):** v2/v3 tests run on `D1Store` over local Miniflare D1 (`test/harness.js`), not `MemoryStore` (§2.6 guarantees live in SQL); `MemoryStore` is deleted in W5. The "v2 log line" becomes column `devices.v2_seen` (step 8 needs 7 days of evidence; Workers logs keep 3).
- Gradle: **one build at a time across sessions** — SendMessage `dot-dev` and `dot-dev2` "Gradle 칸 잡음 (wherepark)" before, "끝" after. No Gradle 06:00–06:40 / 23:10–23:40 KST (Git Bash `date` = KST). `export JAVA_HOME=C:/DC/tools/jdk-17`; `cd app-v2 && ./gradlew :app:testDebugUnitTest --max-workers=2 -Ptest.forks=1` (`--tests '<fqcn>'` for one class). Rules: `C:\DC\DD\docs\procedures\heavy-jobs.md`.
- Commits: Conventional Commits, English, one "why" line; explicit paths only (`git commit --only <paths>` from `C:\DC\DD\dot-lab`), then push. Never commit `firebase.properties`, `.env`, D1 exports, smoke keys, `~/.wherepark/*`.
- D1 exports hold personal data: scratchpad only, deleted after cutover step 8.
- Copy verbatim from spec §5/§5.8. "차 주인" (`ownerId`) ≠ "방장" (`memberships.owner`) in code and copy.
- Caps: 3 groups/device, 8 devices/group, 4 cars/owner, 5 live requests/group; name ≤12, group name 1–40.

---

## Phase 1 — Worker

### Task W0 [Claude]: Local D1 harness

**Files:** `worker/package.json`, `worker/.gitignore` (+`node_modules/`, `.wrangler/`, `migrate_v3.sql`); Create `worker/test/harness.js`, `worker/test/harness.test.js`.

**Produces:** `setup({ sql = ["schema.sql"] }) → { db, store, env, objects, call, reset, close }`: one `new Miniflare({ modules: true, script: "export default {}", d1Databases: ["DB"] })` per file, `db = await mf.getD1Database("DB")`, each SQL file applied statement by statement (split on `;`, comment lines dropped), `store = new D1Store(db)`, `env`/`objects`/`call` as in today's `v2.test.js` `setup()`, `reset()` deletes every row.

- [ ] `npm i -D miniflare@4` (network; exact pin + lockfile). Script `"test": "node --test test/"`.
- [ ] `harness.test.js`: `addGroup` → `group` round-trips; `npm test` green. Commit `test(wherepark-worker): miniflare D1 harness`.

### Task W1 [Codex]: Maintenance gate (= the maintenance build)

**Files:** `worker/src/index.js`; Test `worker/test/worker.test.js`.

**Contract:** in `handle()` before routing: `env.MAINTENANCE === "1"` and path starts with `/v1/` `/v2/` `/v3/` and not (`env.SMOKE_KEY` non-empty and header `x-smoke` equals it) → `503 {"error":"maintenance"}` + `Retry-After: 600`. `/j`, `/r`, assetlinks always served. `scheduled` returns at once while on.

- [ ] Tests first: gate → `/v2/state` 503 + header; `/j` 200; right `x-smoke` → 401 (normal path); wrong key → 503; no `SMOKE_KEY` → 503 whatever the header; gate off unchanged; cron with gate leaves an expired photo flag set. Green → commit `feat(wherepark-worker): maintenance gate with smoke bypass`; the sha = `MAINT_SHA` (P1).

### Task W2 [Codex]: v3 schema + store

**Files:** Create `worker/migrations/2026-11-22-v3.sql` (spec §1 SQL + `v2_seen INTEGER` on `devices`); append the same CREATEs to `worker/schema.sql`; `worker/src/store.js`; Test `worker/test/store.test.js`.

**Produces (D1Store, async; `me`, `x` = device ids):**
- `device(id) → {id, secretHash, name, fcmToken, created, lastSeen, v2Seen}|null`; `touchDevice(id, now, v2=false)`; `setToken(id, t)` = batch [clear `t` on other rows, set on `id`]; `clearToken(t)`.
- `memberships(me) → [{groupId, name, owner, joined}]` by joined; `groupMembers(gid) → [{id, name, owner, joined, lastSeen, created}]` by joined, id; `renameGroup(gid, name)`.
- `createGroup({me, newDevice?, groupId, groupName, now}) → "ok"|"groups"`; `join({me, newDevice?, codeHash, code, now}) → {result: "ok"|"code"|"member"|"full"|"groups", groupId?}` — one batch each, statements exactly spec §2.6 "Join / create group" (invite/request deletes guarded by `EXISTS(membership(me, gid) AND joined = ?now)`; new-device rollback `DELETE devices WHERE id=? AND NOT EXISTS(membership)`). Classify after: new membership → ok; invite gone/expired → code; already member → member; 8 members → full; else groups.
- `acceptRequest({reqHash, groupId, me, myName, code, codeHash, now}) → "ok"|"taken"|"many"`: batch [`INSERT INTO requests … SELECT … WHERE (live rows of gid) < 5 ON CONFLICT(req_hash) DO NOTHING`, `INSERT INTO invites SELECT … WHERE EXISTS(requests row with req_hash AND code)`]; re-read: my code → ok, other row → taken, none → many.
- `leave(gid, x, dropHashes) → {deviceGone}`: one batch = spec §2.5 steps 1–5 (step 4 statements guarded by `NOT EXISTS(memberships of gid)`, 방장 handoff `UPDATE … WHERE device_id = (oldest joined) AND NOT EXISTS(owner=1)`; step 5 guarded by `NOT EXISTS(memberships of x)`).
- Cars: `car(id) → {car, ownerId, ownerName, legacyGroup, legacyCarId}|null`; `visible(id, me)` (§2.2 SQL); `createCar(me, car, now) → "ok"|"cars"|"id"` (§2.6); `updateCar(me, car) → bool` (`WHERE owner_id = me`); `deleteCar(id, me) → bool` (batch parks3, car_groups, cars3); `include(id, gid, me, now)` / `exclude(id, gid, me) → "ok"|"car"|"group"` (§2.6 include; 0 rows → re-read); `carGroups(ids) → {carId: [gid]}`; `visibleCars(me)` (one query, mine ∪ via my groups, distinct).
- Parks: `park3(carId) → {park, prev, by, byId, drivingBy, drivingById, updated}|null`; `parks3(ids)`; `casPark3(carId, cur, next, me)`, `casRemovePark3(carId, cur, me) → bool` (today's CAS `AND <visible(me)>`; insert = `INSERT … SELECT … WHERE <visible> ON CONFLICT(car_id) DO NOTHING`); `knownParkers(me, ids) → {carId: Set}` (`car_groups ⋈ memberships m1 (me) ⋈ memberships m2`).
- `audience(ids, actor) → [token]` (DISTINCT tokens of owners ∪ members of the cars' groups, minus actor); `groupTokens(gid, actor)`; cron `expiredPhotos3(cutoff) → [{carId, parkId, legacyGroup, legacyCarId}]`, `expirePhotos3(cutoff)`.
- Invite/request methods stay as they are.

- [ ] Tests first, one per return value above, plus: invite kept on full/member; failed new-device join leaves no device; leave effects §2.5 1–5 (others' cars untouched); include twice → one row; CAS loses after visibility ends; audience dedupes and skips actor. Commit `feat(wherepark-worker): v3 tables and store`.

### Task W3 [Codex]: core.js + `/v3` group routes

**Files:** Create `worker/src/core.js`, `worker/src/v3.js`; `worker/src/index.js` (`/v3/` → `v3.handle`), `worker/src/fcm.js`; Test `worker/test/v3.test.js`.

**Produces (`core.js`):** `auth(req, store)` (Bearer `^[0-9a-f]{16}\.[0-9a-f]{64}$`; returns the row as read **before** `touchDevice`, 60 s throttle); `present(v, me, known)` (§4.1: `by`/`byId` null + `byOther: true` unless `byId === me.id || known.has(byId)`; `drivingBy`/`drivingById` → `drivingOther`; `prev` the same; `mine`); `notifyCars(env, store, ids, actor, data)`, `notifyGroup(env, store, gid, actor, data)` (clear unregistered tokens); `leaveGroup(env, store, gid, x) → {deviceGone}` (reads photo prefixes + audience first, `dropHashes` = sha256 of `memberRequests` codes, `store.leave`, R2 prefixes deleted when gone, push §2.5 step 6); `stateFor(store, me)` = §4.1 JSON from the fixed queries (groups by my `joined`; car `groups` = all for mine, ∩ mine for others'). `sendData` adds `android.collapse_key` (`park:<carId>`, `members:<groupId>`, `cars`).

**Routes:** every non-car row of §2.1 (errors §2.3, limits §2 header; `/v3/state` limit key `state:<deviceId>` → 429 `slow`; invites → `{code, expiresAt, link}`). Create/join without auth → `{deviceId, secret, token, groupId, state}`, with auth → `{groupId, state}`. Non-member on a group route → 404 `group`.

- [ ] Tests first: every route's 2xx shape and each error it lists in §2.1/§2.3 (with and without auth where optional); `member` keeps the invite usable; request-held invites survive a new invite; rename 41 chars/blank → 400; remove after 30-day idle; leave last → `gone`, token 401; `DELETE /v3/me` in 2 groups → 401; state limit via a fake `LIMIT`. Commit `feat(wherepark-worker): v3 groups and state`.

### Task W4 [Codex]: `/v3` cars, parks, masking, fan-out, cron

**Files:** `worker/src/core.js`, `worker/src/v3.js`, `worker/src/index.js` (`scheduled` on `parks3`, both photo keys); Test `worker/test/v3.test.js`.

**Contract:** §2.1 car rows, §2.2, §2.6 park CAS with recency re-judge (≤3 tries, else 409 `{error:"stale", park}`); `POST …/state {state, parkId}` with `parkId ≠ cur.park.id` → 409 `{error:"moved", park}`. New park id → `by/byId` = me, same id keeps them; DRIVING → `drivingBy/drivingById` = me. Photo key `c/<car>/<park>.jpg`, GET falls back to `g/<legacyGroup>/<legacyCarId>/<park>.jpg`, deletes remove both. Every response park goes through `present`. Pushes `park`/`cars` to `audience` (DELETE: read before).

- [ ] Tests first (owner O in X and Y, MX in X, MY in Y only, stranger S; car in X and Y):
  - every car route × {O, MX, MY, S}: S 404 `car`; MX/MY 403 `owner` on PUT/DELETE car and include/exclude, 200 on park/state/undo/photo. 5th own car 409 `cars`; foreign id 409 `id`, no car data.
  - O excludes X → MX park 404 `car`; MX also in Y → 200.
  - **D13:** MY parks then DRIVING → MX's `/v3/state`, park/undo bodies and a 409 `stale` body: `by`, `byId`, `drivingBy` null, `byOther`/`drivingOther` true; O sees the name; after MY leaves Y, O reads `byOther` too.
  - `casPark3` stubbed to lose once: older row meanwhile → incoming wins; newer → 409 `stale`. Stale `parkId` → 409 `moved`.
  - device in X and Y → one push; actor none; shared token once. Photo: legacy-only GET works; undo/replace/DELETE/cron delete both keys.
- [ ] Commit `feat(wherepark-worker): v3 cars, parks, masking, fan-out`.

### Task W5 [Codex]: `/v2` compat, `/v1` → 410, drop MemoryStore

**Files:** Rewrite `worker/src/v2.js` over `core.js`; `worker/src/index.js` (`/v1/*` → `410 {"error":"update"}`; delete v1 handlers, `newMember`, old `notify`); `worker/src/store.js` (delete `MemoryStore` + v2-table methods); port `worker/test/v2.test.js` and `worker/test/worker.test.js` to the harness (keep HTTP assertions, store pokes → v3 store calls, v1 tests → one 410 test).

**Contract (§2.4):** `/v2/groups`, `/v2/join` make a new device (v2 response shapes, `memberId` = device id). v2 group = the device's only membership; 0 or >1 → 410 `update` on every authed v2 route. Shapes = `contracts-v2.md` §1. `/v2/state` = group's cars + all mine, parks via `present`. Invisible → 404 `car` first on every v2 car route. `PUT /v2/cars/{id}`: new → `createCar` + `include` (409 `cars` → `full`); mine → update; visible not mine → 200 `{car: <server value>}`. `DELETE`: owner → delete, else 200 `{ok:true}`. `POST …/state` without `parkId`: only if `cur.updated <= me.lastSeen` (pre-request row), else 409 `moved` + park. Authed v2 calls `touchDevice(id, now, true)`.

- [ ] Tests first: ported suite; another group's car id PUT/DELETE → 404, no data; non-owner edit → 200 + server value, DB unchanged; non-owner delete → 200, car kept; legacy state after a later park → 409; D13 masking on `/v2/state`; 2 memberships → 410; `/v1/park` → 410; `v2_seen` set. `grep -rn MemoryStore worker/src worker/test` empty. Commit `feat(wherepark-worker): v2 compat on v3 tables, close v1`.

### Task W6 [Codex]: Concurrency on real D1

**Files:** Create `worker/test/d1.test.js` (`Promise.all` over `call`).

- [ ] 7 members + 3 concurrent joins → exactly 8, losers 409 `full`, their invites remain; a device in 2 groups creates 2 groups at once → one 200; parks atMs 100/200 in both orders → 200 kept; include racing the owner's leave → no `car_groups` row for a group the owner left; failed new-device join → no orphan device. Run `npm test` 5× in a row, all green. Commit `test(wherepark-worker): concurrent writes on local D1`.

## Phase 2 — Migration

### Task M1 [Codex]: `tools/migrate_v3.py`

**Files:** Create `worker/tools/migrate_v3.py`, `worker/tools/test_migrate_v3.py` (pattern of `migrate_v2.py`).

**Interfaces:** pure `plan(rows: dict[str, list[dict]], car_map: dict[tuple[str, str], str], new_id: Callable[[], str]) -> tuple[list[str], list[str], dict]` = (car_map INSERTs, copy INSERTs, report); `rows` keys `groups, members, cars, parks2`. CLI: `--remote|--local` (one), `--db dot-lab-parking`, `--dry-run`, `--out PATH` (default `./migrate_v3.sql`), `--persist-to PATH` (local only). Run: apply `migrations/2026-11-22-v3.sql` → read rows + `car_map` (`d1 execute --json`; dry-run: missing table = empty) → execute car_map INSERTs → **re-read** `car_map` → re-plan → execute copy INSERTs via `--file`. Dry-run executes nothing, writes both lists to `--out`, prints the report.

**Rules (§3, D10):** groups without members skipped and listed. `devices` ← members (same id, secret_hash, name, fcm_token, created, last_seen); `memberships` ← (group, id, owner, joined=created). Same `c_` id in two groups: newest park `atMs` (else car `updated`) keeps it, others `new_id()`; stored `car_map` always wins. Owner = `parks2.by_id` if still a member of that group → 방장 → oldest member (report column `last-parker|방장|oldest`). `cars3.json.id` = new id, `legacy_group/legacy_car_id` = old; `car_groups(car, group, now)`; `parks3` ← parks2 with `json.carId` rewritten and `driving_by_id` (and `prev.drivingById`) = the group member named `driving_by` (oldest if several) else NULL. Assert ≤4 cars per owner. Only `INSERT OR IGNORE`; no deletes, updates or R2.

- [ ] Tests first (sqlite3 in-memory; fixture = `schema.sql` + v3 CREATE; executor runs statements on sqlite3): owner rule ×3; collision mapping stable on re-run (`new_id` that raises on the 2nd plan); `legacy_car_id` set; re-run → `total_changes` 0; half the copy list then a full run = one clean run; dry-run → patched `subprocess.run` sees only SELECTs; `driving_by_id` match + null. Commit `feat(wherepark-worker): v3 migration tool`.

### Task M2 [Claude]: Rehearsal on a production copy

- [ ] `npx wrangler@4 d1 export dot-lab-parking --remote --output=$SCRATCH/d1-prod.sql`; `d1 execute dot-lab-parking --local --persist-to $SCRATCH/d1r --file <export>`.
- [ ] `migrate_v3.py --local --persist-to $SCRATCH/d1r --dry-run` → read owners/re-keys; run; re-run → 0 inserts.
- [ ] Local copy only: set one device's `secret_hash` = sha256(known secret); `wrangler dev --local --persist-to …` (W5 commit) → 200 on `/v2/state` (its cars) and `/v3/state`. No commit.

## Phase 3 — App data

### Task D1 [Codex]: Models, Wire v3, GroupStore

**Files:** `$A/core/Car.kt`, `$A/data/Prefs.kt`; rename `$A/data/FamilyStore.kt` → `GroupStore.kt` + call sites; Test `$T/data/WireTest.kt`.

```kotlin
data class Car(val id: String, val name: String, val color: Int, val plate: String?, val order: Int,
    val ownerId: String? = null, val ownerName: String? = null, val groups: List<String> = emptyList())
fun Car.isMine(meId: String?) = ownerId == null || ownerId == meId
// Park += val byOther: Boolean = false, val drivingOther: Boolean = false
data class Group(val id: String, val name: String, val owner: Boolean, val members: List<Member>)
data class ApiState(val me: Member?, val groups: List<Group>, val cars: List<Car>, val parks: Map<String, Park?>)
// Wire.state(j) parses §4.1; Wire.group/groupJson; carJson/car keep owner fields locally (server PUT body = v2 Car fields only)
object GroupStore { joined; auth; me(); groups(); group(id); save(ctx, deviceId, secret, me, groups); setState(ctx, me, groups);
    clear; removed; dismissRemoved; dropIfSame(ctx, auth); leave(ctx) }   // prefs "wp_family" memberId/secret kept
```
Delete `pendingGroup`, `carryOver`, `groupCreateError` and their uses: `SyncEngine.run`'s create block, carry-over dialogs (`MainActivity`, `JoinActivity`), the createError band, Onboarding's `setPendingGroup` (creation returns in D3/U4; nothing ships in between).

- [ ] Tests first: a §4.1 sample (own + other's car, `byOther`) parses exactly; car JSON keeps owner fields; an old `wp_family` pref still gives `auth()`. Suite green. Commit `refactor(wherepark): v3 models and GroupStore`.

### Task D2 [Codex]: FakeWorker

**Files:** Create `$T/data/FakeWorker.kt`, `$T/data/FakeWorkerTest.kt`.

**Produces:** `class FakeWorker : Transport` — in-memory devices/groups/cars/links/parks with W3–W5 rules for every route the app calls: 401, §2.2 authz, 409 `groups|full|cars|id|member`, recency 409 `stale`+park, `parkId` 409 `moved`, masking, photo bytes. Helpers `device(name): Pair<String /*auth*/, String /*id*/>`, `group(auth, name): String`, `addMember(gid, auth)`, `car(auth, id)`, `share(carId, gid)`, `exclude(carId, gid)`, `parkAs(auth, park)`, `var down: Int?` (answer every call with it), `calls`. `FakeNet` stays for request routes.

- [ ] `FakeWorkerTest` = the W4 authz table as data, passing. Commit `test(wherepark): FakeWorker enforces the v3 contract`.

### Task D3 [Codex]: Api v3, Share, State.parkId, settle

**Files:** `$A/data/Api.kt`, `$A/data/SyncQueue.kt`, `$A/data/SyncWorker.kt`; Tests `$T/data/SyncQueueTest.kt`, `$T/data/ContractFixTest.kt`, new `$T/data/SyncV3Test.kt`.

```kotlin
Api.state(ctx): Result<ApiState>                      // /v3/state; only 401 drops the credential
Api.createGroup(ctx, groupName: String, name: String?): Result<String>   // groupId
Api.join(ctx, code: String, name: String?): Result<String>
Api.invite(ctx, groupId: String, fresh: Boolean = false): Result<Invite>  // wp_invite cache keyed by groupId
Api.putRequest(ctx, id: String, groupId: String): Result<Unit>
Api.removeMember(ctx, groupId, id); Api.leaveGroup(ctx, groupId): Result<Boolean> /*deviceGone*/
Api.deleteMe(ctx); Api.renameGroup(ctx, groupId, name)   // peek, requestCode, cancelRequest, setFcmToken, photo: same signatures on /v3
Op.Share(carId: String, groupId: String, on: Boolean)     // key "share:$carId:$groupId", json t="share"
Op.State(carId, state, parkId: Long? = null, seq)         // null = queued by v2 → exec sends ParkStore.latest id
```
- create/join without device, under `synchronized(SyncQueue)`: `GroupStore.save`, `JoinRequest.cancel`, `PutCar` + latest `PutPark` per local car, `Merge.write(state)`; with device: `Merge.write`. Then `kick`. Remove `Discarded`, join backup/restore, old-auth DELETE.
- `SyncQueue.add`: no device → ignore; PutCar/DelCar of a non-mine car → ignore + log. Order car → share → undo → park → state → photo.
- `exec`: `/v3/cars/{id}…`; Share → `PUT|DELETE /v3/cars/{id}/groups/{gid}`; State body `{state, parkId}`.
- `settle` (§4.4): 403 → drop + log; 404 `car` on park/state/undo/photo of a non-mine car → drop all its ops, remove car + parks, §4.4 notice; 404 `group` on Share → drop; PutCar 409 `cars` → remove + §4.4 notice; PutCar 409 `id`/400/404/413 → localOnly + §4.4 notice; 0/5xx → retry, queue and credential kept. Korean literals: copy from the spec file, then grep the diff for `??`.

- [ ] Tests first (FakeWorker): order with Share; same-key replace; DelCar drops Share; park on a car excluded meanwhile → notice, car gone, others untouched; still visible via Y → saved; Share to a left group dropped; stale `parkId` → server park written; v2-era State sends latest id; `down = 503` run → queue + `auth()` unchanged; create without device queues PutCar+PutPark of 2 cars before any Share. Commit `feat(wherepark): v3 api, Share op, settle rules`.

### Task D4 [Claude]: Merge, solo, upgrade, push pulls, alarm defaults

**Files:** `$A/data/{Merge,GroupStore,CarStore,LocalData}.kt`, `$A/family/Push.kt`, `$A/App.kt`, `$A/core/Car.kt`; Tests `$T/data/MergeTest.kt`, `$T/family/PushTest.kt`, new `$T/data/UpgradeTest.kt`.

- `Merge.write`: my cars as today (queued PutCar wins, DelCar not revived, localOnly kept); others' cars + groups = server; queued Shares applied to `car.groups`; `GroupStore.setState`; a group gone without `Api.leaveGroup` (which drops it locally first) → notice `'{그룹}' 그룹에서 나가게 됐어요`.
- `GroupStore.toSolo(ctx)`: drop non-mine cars (+parks, unlink), strip owner fields from mine; used by `dropIfSame` (band), `leaveGroup` when gone, `deleteMe`. `CarStore.mine(ctx)` for the 4-car limit and "＋ 차 추가".
- `Upgrade.run(ctx)` in `App.onCreate` once (`wp_schema = 3`): drop `wp_pending`, `wp_carry`, `wp_create_error`; queued PutCar ids → `wp_legacy_share`; after the first pull with exactly one group → `Share(id, it, true)` for owned ids, clear.
- `Push.enabled` default **true** for every key; channel ids kept, names per §5.8. `Push.handle` → unique work `wp-pushpull`, KEEP, 3 s delay; `PushPullWorker`: snapshot parks + members → pull → notify per non-mine changed park (`drive:`/`park:`; today's texts, or `다른 그룹에서 {차}를 가져갔어요/댔어요` when masked) and new member (`join`, §5.8). `// ponytail: one pull's diff; a pull merged elsewhere skips its notice`.
- `CarResolver`: name tie → my car.

- [ ] Tests first: merge keeps my queued car, replaces others', removes one that left (unlinked), shows pending Share; vanished group notice; 401 → others' cars gone, mine kept, band; upgrade from v2 prefs (credential, PutCar, State without parkId) → `joined()`, ops on `/v3`, one Share per owned car after first pull; alarms default on; 3 pushes → 1 pull; `byOther` texts. Commit `feat(wherepark): v3 merge, upgrade, coalesced push pulls`.

## Phase 4 — App UI [Claude]

Per task: screen + `$T/ui/AppScreensTest.kt` cases (texts per state) + `AppShots.kt` shots compared with the mockup frame; suite green; commit `feat(wherepark): <screen>`. Copy from the named §.

- **U1 설정 (A-1/B-5, §5.2)** `$A/ui/Settings.kt`: 내 차 = `CarStore.mine`; 그룹 rows → U3, add row (hidden at 3) → U4; 알림 = [알림 시험] + hint only; widget main car = any visible; 내 데이터 지우기 → `Api.deleteMe` (best effort) + `LocalData.wipe`.
- **U2 차 화면 (A-2/B-4, §5.6)** `$A/ui/CarScreens.kt` `CarEditScreen(draft, car, isNew, linked, a)`: mine = edit + "알림 · 이 폰에만" (`exit:`, `park:`, `drive:`; group rows only if `car.groups` non-empty) + "이 차가 보이는 그룹" (switch per my group → `CarStore.upsert` + `Op.Share`; ≥2 on → "두 그룹 모두…"; hidden without groups) + delete ("그룹 폰에서도 사라져요" only when shared). Others' = `{owner}님 차`, read-only + owner line, BT link allowed, alarms (exit only when linked), no delete/toggles; `saveDraft` never queues PutCar for them.
- **U3 그룹 상세 (B-1/B-6, §5.3)** `$A/family/FamilyActivity.kt` → `GroupActivity` (extra `groupId`, manifest): rename for 방장 (`Api.renameGroup`); members; my/others' car rows → car screen; invite → `InviteActivity(groupId)`; leave → B-6 lines from pure `LeaveText.of(group, cars, groups, meId): List<String>` (unit test: 4 combinations) → `Api.leaveGroup`.
- **U4 그룹 추가 (B-2, §5.4)** new `$A/family/AddGroup.kt`: sheet (3 rows + "…지금 N개"); create = group name (+ my name only without device) + my cars **all ON** → `Api.createGroup` → Shares; join → if I own cars, same list **all off** + [완료]; `member`/`groups`/`full`/offline copy.
- **U5 홈 (B-3, §5.5)** `$A/ui/Home.kt`: pure `HomeOrder.arrange(cars, parks, groups, meId): Pair<List<Car>, List<Pair<Car, String>>>` (mine by order; others by park `atMs` desc, parkless last; label `{owner}님 차 · {first shared group in my order}` / `… 외 N`) + unit test; 3 others then "N대 더 보기" in place; `WhoLine` → `다른 그룹` when `byOther`; 401 band copy + [그룹 추가].
- **U6 처음·초대·요청 (§5.7)** Onboarding Step4 4 buttons, nothing created; name asked on first create/join (`AppPrefs.setMyName`); `Request.kt` accept: 1 group / chooser (full greyed) / none → create; request hidden at 3 groups; JoinActivity request confirm.
- **U7 문구 (B-7, §5.8)** `Invite.kt`, `InviteActivity`, `JoinActivity`, `ListenModel`, `Home`, `Push`, `SyncWorker`, `worker/src/jpage.js` (title/heading 가족 → 그룹) + a worker test that `/j` and `/r` contain `그룹`. Done: `grep -rn 가족 $A worker/src/jpage.js` → only `Wire.NO_GROUP_NAME` and placeholders; both suites green.
- **U8 위젯 (§5.9)** `WidgetRenderer.mainCar(cars, mainId, meId)`: visible main → my lowest order → any; `carsFor` input = mine + main if not mine; config picker lists all visible with owner labels; `WidgetSpecsTest` cases.

## Phase 6 — Live smoke

### Task S1 [Codex writes, Claude runs]: `worker/tools/smoke_v3.py`

Pattern of `smoke_v2.py`; every call sends `x-smoke` from env `WP_SMOKE_KEY`. `seed` (maintenance build, v2 routes): throwaway family + car + park → `~/.wherepark/smoke-v3-seed.json`. `check` (v3): the seed credential reads `/v2/state` and `/v3/state` with its car; devices A, B; G1 (A, B), G2 (A); A's car into G1 → B parks 200 → A excludes → B 404 `car` → B leaves G1 → B 401; `DELETE /v3/me` for A and the seed; PASS/FAIL lines, exit 1 on any FAIL. Task check: `python -m py_compile`.
[Claude] also: `$T/data/LiveSyncTest.kt` (`-Pwp.live=true`) → v3 two-device, two-group round trip, cleanup `DELETE /v3/me`; run once after cutover stage 6.

## Phase 7 — Release

### Task R1 [Claude]: v3 APK

- [ ] `versionCode = 2`, `versionName = "1.1.0"`; full unit + shot suite (Gradle slot); `./gradlew :app:assembleRelease --max-workers=2`; ≤30 MiB; `zipalign -c -P 16 -v 4`; same key (`~/.wherepark/`) so it installs over 1.0.0. `RELEASE.md`: 가족 → 그룹, note "한 비밀값이 최대 3개 그룹을 연다" (§6). Commit; the APK goes out at cutover stage 7.

## Phase 5 — Cutover (written now, run after S1 + R1)

### Task P1 [Claude]: `dot-lab/scripts/wizard-parking-v3-cutover.bat`

From `C:\DC\DD\.agents\skills\wizard\template.bat`; UTF-8 no BOM, **CRLF**, in `dot-lab/scripts/` (other folders deny bats), smoke-run via `printf "n\n" | cmd.exe //c <abs path>`. Stages gate on y/n, log to `scripts/logs/`, accept piped answers: **Claude runs it**, the operator answers gates in chat. Config: `MAINT_SHA` (W1), `V3_SHA`.

| Stage | Claude runs | Gate |
|---|---|---|
| 0 | `npx wrangler@4 whoami`; new `SMOKE_KEY` → `%USERPROFILE%\.wherepark\smoke-key` + `secret put SMOKE_KEY` | logged in |
| 2 | `git worktree add $SCRATCH/wp-maint MAINT_SHA`; `secret put MAINTENANCE` = 1; deploy from the worktree; `smoke_v3.py seed` | app 503, `/j` 200 |
| 3 | `d1 time-travel info dot-lab-parking` (bookmark → log) + `d1 export --remote` to scratch | bookmark logged |
| 4 | `migrate_v3.py --remote --dry-run` → operator reads owners/re-keys → `--remote` (re-run if it stops) | operator ok |
| 5 | deploy `V3_SHA` (gate still on) → `smoke_v3.py check` | all PASS |
| 6 | `secret put MAINTENANCE` = 0; operator opens the app on each phone | a park syncs |
| 7 | SendUserFile the R1 APK; operator installs over the old app | — |
| R | before 6 only: `d1 time-travel restore dot-lab-parking --bookmark=<3>` → deploy MAINT_SHA → MAINTENANCE 0 | prints §9 rollback notes |

- [ ] `--print` mode lists every command; compare with this table. Commit `chore(wherepark): v3 cutover wizard`.
- [ ] After 7: ROADMAP note for §9 step 8 — no `devices.v2_seen` in 7 days + 30 days since stage 6 → own plan; dropping old tables needs operator confirmation.
