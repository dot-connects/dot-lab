# 어디댔어 v3 — 가족 → 그룹 (spec)

Design spec, not code. Current behaviour = the code (`worker/src/*.js`, `app-v2/.../data/*.kt`) + `contracts-v2.md`;
this file states what changes. Mockup: `mockups/v4-groups/index.html`. Where the mockup's "추천" and the operator's
decisions differ, the decisions win (★). Review round 1 (Codex, 13 findings) and the reasons behind each choice:
`archive/2026-11-21-spec-v3-review/`.

## 0. Decisions

| # | Operator decision (final) | Consequence |
|---|---|---|
| D1 | 가족 → 그룹 everywhere (app, KakaoTalk invite, "그룹에게 요청하기") | §5.8 |
| D2 | ≤ **3 groups per phone**; "＋ 그룹 추가" = create / join by code / join request | 409 `groups` |
| D3 | A car in several groups has **one** latest park; anyone in any containing group updates everyone | `car_groups` |
| D4 | All cars shared into my groups show on my home; **only the car's owner includes/excludes**, toggles **only in the car screen** ★ | §5.3, §5.6 |
| D5 | Home: group label only on others' cars ("○○님 차 · 회사 주차"); others' cars = recent 3 + "더 보기" | §5.5 |
| D6 | **One display name per person** across groups ★ | name on the device row |
| D7 | Alarms (차에서 내리면 물어보기 / 그룹에서 댔을 때 / 그룹에서 가져갔을 때) per car, car screen, this phone, **default ON for all cars incl. others'** ★ | §5.1 |
| D8 | A newly joined group does not include my cars; **a newly created group lists my cars all ON** on the create screen, switchable off before creating | §5.4 |
| D9 | No auto-created group on first run | `pendingGroup` removed |
| D10 | Migration: owner = member who last parked the car, fallback 방장 ★ | §3 |
| D11 | Leaving a group with my car → confirm (B-6); last group → solo (local only) | device deleted server-side |
| D12 | Car removed from a group while someone there is mid-park → save rejected + notice | 404 `car` + notice §4.4 |
| D13 | Parker not in a group I see the car through → show **"다른 그룹"** ("다른 그룹 17:15"); the API must not send that viewer the name or id | §4.1 masking |
| D14 | Group rename by the 방장: in scope | `PUT /v3/groups/{gid}` |
| D15 | `/v1/*` closed (410); no phone uses it | §2.4 |
| D16 | Third park state **LEFT** ("내림, 위치 미입력", 2026-11-21): the driving phone sends it with the time when the car's Bluetooth disappears while the park is DRIVING; everyone shows "HH:MM 내림 · 위치 미입력", the driver's phone "어디 댔는지 말해 주세요" + mic; a new park replaces it. Same name masking as DRIVING; v2 responses map LEFT → DRIVING | plan W4/W5/D3/D4/U5 |

Spec choices (reasons in the archive): one **device** identity + per-group **memberships** (`device.id = old
member.id`, so installed credentials stay valid); server keeps all my cars once I have a device; a car is only in
groups its owner belongs to; `/v3` + thin `/v2` layer, `/v1` → 410; photos `c/<car>/<park>.jpg` with legacy fallback;
widget lists my cars + the main car.

Terms: **차 주인** = `cars3.owner_id` (registering device). **방장** = `memberships.owner = 1`. Keep them apart in code and copy.

## 1. Data model (D1)

New tables beside the old ones (left as they are); `groups`, `invites`, `requests` reused + indexes. Rollback = D1 restore (§9).

```sql
-- migrations/2026-11-2x-v3.sql (CREATE IF NOT EXISTS only, re-runnable)
CREATE TABLE IF NOT EXISTS devices (id TEXT PRIMARY KEY, secret_hash TEXT NOT NULL, name TEXT NOT NULL,
  fcm_token TEXT, created INTEGER NOT NULL, last_seen INTEGER);            -- id = old members.id when migrated
CREATE INDEX IF NOT EXISTS devices_token ON devices (fcm_token);
CREATE TABLE IF NOT EXISTS memberships (group_id TEXT NOT NULL, device_id TEXT NOT NULL,
  owner INTEGER NOT NULL DEFAULT 0, joined INTEGER NOT NULL, PRIMARY KEY (group_id, device_id));  -- owner = 방장
CREATE INDEX IF NOT EXISTS memberships_device ON memberships (device_id);
CREATE TABLE IF NOT EXISTS cars3 (id TEXT PRIMARY KEY, owner_id TEXT NOT NULL, json TEXT NOT NULL,
  ord INTEGER NOT NULL, updated INTEGER NOT NULL,
  legacy_group TEXT, legacy_car_id TEXT);                                 -- legacy_*: photo fallback only
CREATE INDEX IF NOT EXISTS cars3_owner ON cars3 (owner_id);
CREATE TABLE IF NOT EXISTS car_groups (car_id TEXT NOT NULL, group_id TEXT NOT NULL, added INTEGER NOT NULL,
  PRIMARY KEY (car_id, group_id));
CREATE INDEX IF NOT EXISTS car_groups_group ON car_groups (group_id);
CREATE TABLE IF NOT EXISTS parks3 (car_id TEXT PRIMARY KEY, json TEXT NOT NULL, prev_json TEXT,
  by_name TEXT, by_id TEXT, driving_by TEXT, driving_by_id TEXT, updated INTEGER NOT NULL);  -- parks2 minus group_id, + driving_by_id (D13)
CREATE TABLE IF NOT EXISTS car_map (old_group TEXT NOT NULL, old_id TEXT NOT NULL, new_id TEXT NOT NULL,
  PRIMARY KEY (old_group, old_id));                                       -- migration, persistent (§3)
CREATE INDEX IF NOT EXISTS requests_group ON requests (group_id, by_id);
CREATE INDEX IF NOT EXISTS requests_code ON requests (code);
CREATE INDEX IF NOT EXISTS invites_group ON invites (group_id);
```

- **Identity:** `member()` reads `devices`; a device lives while it has ≥1 membership (§2.5). No device = solo.
- `parks3.by_id/by_name` = device id + name snapshot (as today); old `by_id` values are member ids = device ids.
- `dropGroup(g)` deletes g's `invites`, `requests`, `car_groups`, `memberships`, `groups` row — never cars.
- **Caps:** 3 groups/device; 8 devices/group; 4 cars/**owner** (today per family); 5 live requests/group.
- **Photos:** `c/<car>/<park>.jpg`; GET falls back to `g/<legacy_group>/<legacy_car_id>/<park>.jpg`. Every delete path
  deletes both keys. Cron: the 30-day rule on `parks3`.

## 2. API v3

Body/photo limits, car id regex `^c_[0-9a-f]{8}$`: as v2. Unauthenticated routes use the `LIMIT` binding (10/min/IP)
with **keys shared with v2** (`groups`, `join`, `requests`). Authenticated: `GET /v3/state` and `/v2/state` limited per
device (key `state:<deviceId>`, 429 → app retries later); other authenticated routes unlimited (as today).

### 2.1 Routes

| Route | Auth | Rule |
|---|---|---|
| `POST /v3/groups` `{name?, groupName}` | optional | no auth: new device (name required) + group, me = 방장 → `{deviceId, secret, groupId, state}`; auth: membership only. 409 `groups`. §2.6 |
| `POST /v3/peek` `{code}` | none | `{groupName, count}` (unchanged) |
| `POST /v3/join` `{code, name?}` | optional | no auth → new device; auth → membership. 409 `member` (invite kept), `full`, `groups`. Atomic (§2.6). Push `members` |
| `POST /v3/groups/{gid}/invites` | member | as v2 (older invites closed except request-held) |
| `PUT /v3/requests` `{req, groupId}` | member of groupId | accept into the **chosen** group; else as v2 (`taken`, `many`) |
| `POST /v3/requests/poll`, `/cancel` | none | as v2; poll also returns `groupName` |
| `PUT /v3/token` `{fcmToken}` | device | also clears that token from other device rows |
| `GET /v3/state` | device | §4.1 |
| `PUT /v3/cars/{id}` `{car}` | device | absent → create, owner = me (409 `cars` at 4 owned; 409 `id` if the id belongs to someone else). Present → owner only (403 `owner`). Push `cars` |
| `DELETE /v3/cars/{id}` | owner | car + `car_groups` + `parks3` + photos; push `cars` to the audience taken **before** delete |
| `PUT` / `DELETE /v3/cars/{id}/groups/{gid}` | owner (+ member of gid for PUT) | include / exclude, idempotent, 404 `group`. Push `cars` to gid |
| `…/{id}/park`, `/undo`, `/photo?park=` | may_use | v2 semantics (prev, photo flag), keyed by car, writes per §2.6. Push `{type:"park", carId}` |
| `POST …/{id}/state` `{state, parkId}` | may_use | applies only if the current park id = `parkId`, else 409 `moved` + server park (§2.6) |
| `DELETE /v3/groups/{gid}/members/{id}` | member | 방장, or anyone if target idle >30 days; never the 방장 by a non-방장 (v2 rule). §2.5 |
| `DELETE /v3/groups/{gid}/me` | member | leave, §2.5 → `{ok, device:"kept"\|"gone"}` |
| `DELETE /v3/me` | device | 내 데이터 지우기: §2.5 for every group, then the device |
| `PUT /v3/groups/{gid}` `{name}` | 방장 of gid | rename, 1–40 chars trimmed (400 `name`), 403 `owner` for non-방장. Push `members` to gid |

No person rename route (one name per device, set on first create/join).

### 2.2 Authorization (live on every request)

```
visible(car, me) = car.owner_id = me OR EXISTS car_groups cg JOIN memberships m ON m.group_id = cg.group_id
                                          WHERE cg.car_id = car AND m.device_id = me
may_use  = visible      (state, photo GET, park, state, undo, photo PUT)
may_edit = owner        (PUT/DELETE car, include/exclude; include also needs me ∈ gid)
```

Invisible → **404 `car`** (same as nonexistent; no probing). Visible, not owner, owner-only route → 403 `owner`.
Group routes: not a member → 404 `group`.

### 2.3 Error codes

400 `name` `code` `car` `park` `state` `req` `photo` · 401 `auth` · 403 `owner` · 404 `route` `car` `group` `member`
`code` `req` `park` `photo` · 409 `full` (8 members) `groups` (3 groups) `cars` (4 owned) `member` `id` `stale` `moved`
`taken` · 413 `size` · 429 `slow` `many` · 410 `update` (v1; v2 with ≠1 membership).

### 2.4 v2 compatibility (installed app)

`/v2/*` keeps its shapes; group = the device's only membership (migrated devices have one; the old app's `/v2/join`
makes a new device). 0 or >1 memberships → 410 `update`.

- groups/join/peek/requests/invites/token → v3 forms. `GET /v2/state` cars = the group's cars **plus all I own** (old
  Merge deletes local cars missing from state).
- **Invisible → 404 `car` first on every v2 car route**; the rules below apply only to visible cars.
- `PUT /v2/cars/{id}`: new → owned by me **and included in my group**; mine → update; **visible, not mine → 200
  `{car: <server value>}`, edit ignored** (old app drops on 2xx, pull restores; no retry loop). `cars` → `full`.
- `DELETE /v2/cars/{id}`: owner → delete; else 200 no-op.
- `POST /v2/cars/{id}/state` (no `parkId`): applies only if the park row's `updated` ≤ the device's `last_seen` as read
  **before** this request updates it; else 409 `moved` + server park. A proxy for "the phone saw this park".
- park/undo/photo, `/v2/me`, `/v2/members/{id}` → v3 rules. First v2 call per device per day logs `v2 <deviceId>`.

### 2.5 Leave / remove (device X, group G; one `db.batch`)

1. Delete `car_groups` of G for cars owned by X (owner invariant).
2. Drop requests X accepted in G and their invites (`dropMemberRequests`).
3. Delete membership (G, X).
4. G empty → `dropGroup(G)`; else no 방장 → oldest `joined` becomes 방장.
5. X has no memberships → delete X's `cars3`, `car_groups`, `parks3`, device row; R2 prefixes after the batch.
6. Push `members` to G's remaining members; `cars` to the §4.2 audience of each car from steps 1 and 5.

X's access to cars it saw only through G ends at step 3 (authz is live). Copies on X's phone can't be revoked; the
next pull removes them.

### 2.6 Atomic writes (authz + cap + write in one statement or one `db.batch`)

- **Park/state/undo/photo flag:** CAS `UPDATE parks3 … WHERE car_id=? AND json id=? AND updated=? AND <visible(me)>`
  (insert form `INSERT … SELECT … WHERE <visible(me)> ON CONFLICT DO NOTHING`). 0 rows → re-read: invisible → 404
  `car`; else **re-judge recency**: incoming still wins (same id, or greater `atMs`) → recompute and retry, ≤3 tries;
  server park newer or equal → 409 `stale` + server park. The newest `atMs` wins whoever reaches the DB first.
- **Include:** `INSERT OR IGNORE INTO car_groups SELECT ?,?,? WHERE EXISTS(car owned by me) AND EXISTS(me ∈ gid)`;
  0 rows → re-read to answer 404 `car`/`group` or 200 (already in). A concurrent leave can't leave a dangling link.
- **Create car:** `INSERT INTO cars3 SELECT … WHERE (SELECT count(*) FROM cars3 WHERE owner_id=me) < 4 ON CONFLICT(id)
  DO NOTHING`; 0 rows → re-read: other owner → 409 `id`, else 409 `cars`.
- **Join / create group** (one batch): `INSERT devices` (new device only) → `INSERT memberships SELECT … WHERE
  members(g) < 8 AND groups(me) < 3 AND EXISTS(live invite)` (create: only the 3-group condition) → `DELETE invites
  WHERE code_hash=? AND EXISTS(that membership)` → same for `requests` with that code → `DELETE devices WHERE id=? AND
  NOT EXISTS(any membership)` (undo a new device on failure). One read afterwards classifies `full`/`groups`/`code`/
  `member`; the invite is consumed only when the membership exists.
- **Accept request:** the 5-per-group cap is a condition on the `requests` insert (fixes today's count-then-insert note).

## 3. Migration (one family → one group)

Runs **once, inside the maintenance window** (§9): nothing writes between reading the old tables and serving the new
ones. `worker/tools/migrate_v3.py` (`--remote|--local`, `--db`, `--dry-run`; pattern of `migrate_v2.py`):

1. Apply the CREATE file (idempotent).
2. Fill `car_map` first, one row per old `(group, id)`. Collisions (same `c_…` in two groups: a removed phone re-created
   its cars in a new family): the copy with the newest park (else car `updated`) keeps the id, others get a fresh random
   `c_` id. `INSERT OR IGNORE`, so a re-run reads the stored mapping instead of re-deciding.
3. Copy with `INSERT OR IGNORE`, every target car id taken from `car_map`:

| Source | Target | Rule |
|---|---|---|
| `members` | `devices` (same id, secret_hash, name, token, created, last_seen) + `memberships` (joined = created) | old credentials keep working |
| `cars (group, id)` | `cars3` (`legacy_group`, `legacy_car_id` = old id) + `car_groups` | owner = current `parks2.by_id` if still a member of that group; else 방장; else oldest member (D10) |
| `parks2` | `parks3` | `driving_by_id` = the group member whose name equals `driving_by`, else null (shown as "다른 그룹") |

- **Resumable:** only `CREATE IF NOT EXISTS` / `INSERT OR IGNORE` keyed by the mapping; a failed run is run again; a
  finished run re-run changes 0 rows. It never deletes or rewrites old rows or R2 objects.
- **Caps:** an owner gets ≤4 cars (a member was in one family of ≤4); asserted.
- **Dry-run** writes the SQL file, executes nothing, prints counts, per car `group · car · owner · last-parker|방장|
  oldest`, every re-keyed car, groups without members (skipped).
- **Rehearsal (required):** `d1 export --remote` → import `--local` → dry-run → run → re-run (0 rows) → `wrangler dev
  --local` + one migrated credential reads `/v2/state` and `/v3/state`.

## 4. Sync

### 4.1 Pull — one call

`GET /v3/state` →

```jsonc
{ "me": {"id", "name"},
  "groups": [{"id", "name", "owner" /*am I 방장*/, "members": [{"id","name","me","owner","lastSeen"}]}],
  "cars": [{ /*v2 Car*/, "ownerId", "ownerName", "mine", "groups": [/*mine: all; others': shared with me*/] }],
  "parks": {"<carId>": { /*Park + by, byId, mine, drivingBy, byOther, drivingOther*/ }} }
```

**Parker masking (D13).** Parker P is *known* to viewer V for car C iff P = V or P is a member of a group in
`car_groups(C)` ∩ V's groups (the owner is in all of C's groups, so sees every parker). Unknown → `by`, `byId` null,
`byOther: true`; same for the DRIVING person (`driving_by_id`) and `prev`. One `present(v, me, known)` serves every
response carrying a park (v3 state, park/state/undo/photo, 409 bodies, `/v2/state`). Live: a parker who leaves the
shared group becomes "다른 그룹". App: "다른 그룹 17:15", notification "다른 그룹에서 회색 차를 댔어요/가져갔어요".

Cars = mine ∪ cars of my groups, deduped. **Fixed query count, no per-car loop** (today's `parks2()` loops): groups +
members; visible cars ⋈ owner; `parks3` for those ids; parkers' memberships in my groups (masking). ≤ ~96 cars.

### 4.2 Push fan-out

Audience(car) = devices with a membership in any group of `car_groups(car)` ∪ owner, minus the actor, `DISTINCT` by
device then by `fcm_token`. One push per phone per event even through two groups. `members` events → that group only.
Payload unchanged (`{type, carId?, groupId?}`); the app pulls full state on receipt (as today), so the old app works.
Bursts: FCM `collapse_key` = `park:<carId>` / `members:<groupId>`, and the v3 app coalesces pulls (unique work, 3 s
delay, KEEP): a burst costs each phone one pull. Bound per event: ≤ 23 phones × 4 queries.

### 4.3 Conflicts

Per car: newest `atMs` wins across park ids, same id overwrites, 409 carries the server park; race-proof now (§2.6
re-judges recency after a lost CAS). `State` only touches the park it was meant for (`parkId`).

### 4.4 Offline queue

- `State` gains `parkId` (the park shown when 가져감 / BT reconnect happened). Old queued `State` ops without it
  (upgrade) send the local latest park id. 409 `moved` → drop + write the server park (as today).
- New op `Share(carId, groupId, on)`, key `share:{carId}:{groupId}`, replaces same key; order `car:` → `share:` →
  `undo:` → `park:` → `state:` → `photo:`; dropped by a queued `DelCar`. Ops queue only while a device exists.
- Settle additions: 403 → drop + log. **404 `car` on a park/state/undo/photo op for a car not mine** (D12) → drop
  its ops, remove the car + local parks, notice **"이 차는 이제 그룹에 없어요. 저장하지 못했어요"**. 404 `group` on `Share` →
  drop. 409 `cars` → "차는 4대까지예요". 409 `id` → like a refused PutCar ("그룹과 나누지 못한 차가 있어요 (흰 차). 이 폰에만
  남아 있어요"). 401 → today's `dropIfSame` (band §5.5).
- Merge: my cars as today; others' cars and groups = server canon; a car leaving visibility → today's removal.
- Create / join / leave / kick / accept are online calls (offline → "인터넷 연결을 확인해 주세요"). Deleted:
  `pendingGroup`, `carryOver`, `Api.join`'s queue backup/restore and old-auth `DELETE /v2/me`.
- Creating my device queues `PutCar` + latest `PutPark` per local car (today's `onSaved`), then the picker's `Share`s.
- **Upgrade from v2:** queued `PutCar` ids → `legacyShare`; after the first `/v3/state`, with exactly one group (the
  migrated family), queue `Share(car, it, true)` for those I own, then clear. Queued edits of a car now owned by
  someone else (D10) → 403 → dropped.
- **Maintenance 503:** `settle` retries every non-4xx; only 401 clears credentials (`SyncWorker.kt`, `Api.state`,
  `FamilyStore.dropIfSame`) — queue and login survive.

## 5. App

### 5.1 Data layer

- `FamilyStore` → `GroupStore`: credential (same `wp_family` `memberId`/`secret` keys, so upgrades stay logged in), `me`,
  `groups`, removed flag. First v3 start: `pendingGroup` without credential discarded (D9); `carryOver` deleted.
- `Car` + `ownerId: String? = null`, `ownerName: String? = null`, `groups: List<String> = emptyList()`;
  `mine = ownerId == null || ownerId == me.id`. One `CarStore`; "＋ 차 추가" and the 4-car limit count mine only.
- `Push.enabled` default **true for every key** (`exit:`, `park:`, `drive:`, `join`). Channel ids unchanged (user
  channel settings survive), names change.
- `CarResolver` (voice): on a name tie prefer my car.

### 5.2 설정 (A-1 / B-5)

- 내 차: mine only, group names small under each.
- 그룹: "회사 주차 · 5명" / "내 차 1대 · 다른 사람 차 2대" → 상세. "＋ 그룹 추가" (hidden at 3). No groups: "아직 혼자 쓰고
  있어요" / "그룹을 만들면 가족·동료 폰에서도 이 차 자리가 보여요. 내 위치는 공유되지 않아요." + "＋ 그룹 추가".
- 알림: per-car rows gone; "알림 시험" with "차마다 알림은 위 '내 차'에서 차를 눌러 바꿔요".
- 위젯 메인 차량: any visible car ("김대리님 차" labels).
- 내 데이터 지우기: "…모든 그룹에서 나갈까요? 되돌릴 수 없어요." → `DELETE /v3/me` (best effort) + `LocalData.wipe`.

### 5.3 그룹 상세 (B-1 without toggles)

- Title with a pencil for the 방장 only → "그룹 이름 바꾸기" dialog (`PUT /v3/groups/{gid}`).
- Members: 3 + "N명 더 보기"; 방장 / 30-day rules unchanged ("그룹에서 내보내기").
- "이 그룹에 보이는 내 차": read-only rows ("우리 가족에도 보여요"), tap → car screen; footer "내 차를 넣고 빼는 건 차 화면에서 해요".
- "다른 사람 차": "검은 차 · 김대리님 차" → car screen; footer "다른 사람 차는 차 주인만 넣고 뺄 수 있어요."
- [그룹 초대하기] [그룹에서 나가기] → B-6 dialog built from local `car.groups` (lines only when they apply): "회사 주차에서
  나갈까요?" / "회색 차가 이 그룹에서 빠지고, 검은 차·파란 차는 내 홈에서 사라져요." / "회색 차와 기록은 이 폰에 남고, 우리
  가족에서는 계속 보여요."; last group: "마지막 그룹이에요. 나가면 혼자 쓰는 상태가 돼요." `device:"gone"` → solo, no band.

### 5.4 그룹 추가 sheet (B-2) + include picker

- 새 그룹 만들기 / 초대 코드로 들어가기 / 그룹에게 요청하기 + "그룹은 3개까지 만들거나 들어갈 수 있어요 · 지금 N개".
- Create: group name (+ my name only if no device yet).
- Create screen (D8): group name + "이 그룹에 넣을 내 차", a switch per car, **all ON**, switchable off, then [만들기].
  After success the ON cars are queued as `Share` (after the device's `PutCar`s).
- Join: after success, if I own cars, the same list **all off** + [완료].
- Removed: "지금 가족에서 나가고 들어갈까요?", "차 n대를 새 가족으로 가져갈까요?". 409 `member` → "이미 들어가 있는 그룹이에요",
  `groups` → "그룹은 3개까지예요", `full` → "그룹이 꽉 찼어요".

### 5.5 홈 (B-3)

- My cars (by `order`), then others' by latest park `atMs` desc (no park → car `updated`); one card per car.
- Others' label "김대리님 차 · 회사 주차" (first shared group in my group order; 2+ → "회사 주차 외 1"); first 3, then
  "N대 더 보기" expanding in place.
- 401 band: "그룹에서 나가게 됐어요" / "내 차와 기록은 이 폰에 남아 있어요" + [그룹 추가]. The "가족을 만들지 못했어요" band goes.

### 5.6 차 화면 (A-2 / B-4)

- Mine: existing edit + "알림 · 이 폰에만" (3 switches) + "이 차가 보이는 그룹" (switch per my group; "두 그룹 모두 같은 자리를
  봐요. 누가 대든 마지막 자리 하나로 맞춰져요." when ≥2 on; hidden with no groups) + 이 차 지우기 ("그룹 폰에서도 사라져요"
  only when shared).
- Others': "김대리님 차", "회사 주차 그룹에서 같이 봐요. 이름·색·번호는 차 주인만 바꿀 수 있어요.", read-only, no delete or
  group switches. **블루투스 link allowed** (migrated family cars become someone else's; the driver's link must keep
  working) → 3 alarm switches (내리면 물어보기 when linked).
- Group alarm rows only when the car is in ≥1 group.

### 5.7 Onboarding, invite, request

- Onboarding page 4 "같이 볼까요?": [그룹 만들기] [초대 코드로 들어가기] [그룹에게 요청하기] [나중에]; nothing is created
  otherwise. Name asked on the first create/join only ("그룹에 보일 내 이름"), kept locally for the 401 band.
- Invite: per group; cached code keyed by group id.
- Accepting a request: 1 group → "○○를 '우리 가족' 그룹에 받을까요?"; 2+ → "○○를 어느 그룹에 받을까요?" + my groups (full
  ones greyed "꽉 찼어요"). No group → "먼저 그룹을 만들어야 받을 수 있어요" [그룹 만들기].
- Requesting: hidden at 3 groups. On code: "<byName>님이 받아 줬어요" + "'<그룹>' · N명에 들어갈까요?" [들어가기] [아니요].

### 5.8 Copy

| Where | v3 copy (가족 → 그룹) |
|---|---|
| KakaoTalk | 어디댔어 '{그룹}' 그룹에 초대해요. 링크를 누르면 돼요. |
| Invite screen | 그룹 초대 · '{그룹}'에 부를 사람에게 보내요 |
| Join screen | 그룹 초대를 받았어요 · 그룹에 보일 내 이름 |
| Request | 그룹에게 요청하기 · 그룹 사람이 [받기]를 누르면 바로 들어가요 |
| Accept | 이미 다른 그룹이 받은 요청이에요 |
| Alarms / channels | 그룹에서 댔을 때 · 그룹에서 가져갔을 때 · 그룹 참여 |
| Join notice | {이름}가 '{그룹}' 그룹에 들어왔어요 |
| Home pending | 신호가 잡히면 그룹에 보내요 |
| Car delete | 그룹 폰에서도 사라져요 |
| Full | 그룹이 꽉 찼어요 |

Done check: `grep -rn 가족` over app main + `jpage.js` finds only `Wire.NO_GROUP_NAME` and placeholders.

### 5.9 Widget

- Main car = any visible car; gone → my lowest-order car → any car. Same for single-car widgets.
- `carsFor` input = my cars + the main car if it isn't mine. Config picker lists all visible cars with owner labels.

## 6. Security notes

- **Join request S/H** unchanged (link = sha256(S), poll/cancel need S, first accept wins across all groups). Someone
  in another group holding the link can accept first; the requester sees "<byName>님이 받아 줬어요 · '<그룹>' · N명" and
  can decline. Accepting needs membership of the chosen group.
- **One secret opens up to 3 groups:** bigger blast radius, no new exposure; note for the Play data-safety form.
- **Removed member / car owner / 방장:** §2.5 — access ends at once (live authz, writes re-check it, §2.6); the owner's
  cars leave that group; last group → device, cars, parks, photos deleted; 방장 passes to the oldest; groups never
  delete cars.
- **Probing:** invisible car = 404 `car` on v3 and v2 alike; taken id = 409 `id` with no data.
- **D13:** viewers outside the parker's shared groups never get the name or id (leak test, §8).
- **Rename:** 방장 only; ≤40 chars (it reaches peek and invite copy).

## 7. Edge cases (mockup 가장자리 표 + decisions)

| Case | Behaviour |
|---|---|
| Owner excludes or deletes a car mid-park | §4.4 404 notice, card removed; owner's park unchanged. Still visible via another group → save succeeds |
| 가져감 queued offline, someone parks meanwhile | `State.parkId` mismatch → 409 `moved`, new park kept |
| Last group left / removed by others | solo B-5, local data stays / 401 band |
| Removed from one of several groups | pull shows it gone → "'{그룹}' 그룹에서 나가게 됐어요" |
| 3 groups | add row + request button hidden; 409 `groups` backstop |
| Concurrent edits from two groups | newest park wins (§2.6); name or "다른 그룹" (D13) |
| Parker later leaves the shared group | the same record now reads "다른 그룹" (masking is live) |
| `Share` queued, group left | 404 `group` → dropped |

## 8. Test plan

`package.json` test script becomes `node --test test/` (today it runs only `worker.test.js`).

- **Worker `test/v3.test.js`** (MemoryStore): authz matrix (owner / member of a containing group / of another group /
  stranger × every car route); caps; §2.5 effects incl. last membership → 401; mid-park exclusion (404; 200 via a second
  group); fan-out one push per device, actor excluded, duplicate token once; D13 leak check (Y-only viewer gets null
  `by`/`byId`/`drivingBy` in state, park/undo/409 bodies and `/v2/state`); `State` with a stale `parkId` → 409; rename
  (방장 200, others 403, 41 chars 400); photo key + legacy fallback via `legacy_car_id`.
- **v2 compat** (`v2.test.js` kept): invisible car → **404 before** the "not mine → 200" path (PUT/DELETE with another
  group's car id returns no data); non-owner edit/delete = 200 no-op; legacy `State` rejected when the park changed after
  the device's last contact; `/v1/*` → 410.
- **Real local D1 `test/d1.test.js`** (D1Store on a Miniflare D1 via `wrangler`'s `getPlatformProxy`, schema applied):
  concurrent joins into a 7-member group → exactly 8; 4th group in parallel → one wins; concurrent older/newer parks →
  newer wins whichever lands first; include racing the owner's leave → no dangling `car_groups` row; failed join leaves
  the invite and no orphan device.
- **Migration `tools/test_migrate_v3.py`** (sqlite3, same SQL): owner rule; collision via `car_map` stable on re-run,
  `legacy_car_id` set; re-run 0 rows; interrupted run resumes; dry-run executes nothing.
- **Live `tools/smoke_v3.py`** (operator, bypass header): two devices, two groups, include → park → exclude → 404 →
  leave → 401; a migrated v2 credential reads `/v2/state` with its cars.
- **App (Robolectric)**: **FakeNet enforces the v3 contract** from the same case table as the Worker authz test (403,
  404 car/group, 409s, one park per car, masking). Settle (404 on others' car); `Share` order; `State.parkId`; upgrade
  from v2 prefs (same credential, queued ops on v3, `legacyShare`); 503 keeps queue + credential. `LiveSyncTest` +1
  two-group round trip.

## 9. Rollout (maintenance window, single migration)

The installed base is a handful of family phones, so writes are simply stopped for a few minutes.

| Step | Who | What |
|---|---|---|
| 1 | Claude | v3 Worker (+ v2 layer + gate), migration tool, tests, app v3, rehearsal (§3); a **maintenance build** = today's Worker + the gate |
| 2 | operator (wizard `.bat`) | deploy the maintenance build with secret `MAINTENANCE=1`: every `/v1` `/v2` `/v3` route → 503 + `Retry-After: 600` (reads too — simpler, harmless); `/j`, `/r`, assetlinks still served; cron skips while on. Requests with header `x-smoke: <SMOKE_KEY secret>` pass |
| 3 | operator | backup: `wrangler d1 time-travel info dot-lab-parking` (note the bookmark) + `d1 export --remote` |
| 4 | operator | `migrate_v3.py --remote --dry-run` (read the owner / re-key list) → `migrate_v3.py --remote` (re-run if it stops midway) |
| 5 | operator | deploy v3 (still `MAINTENANCE=1`) → `smoke_v3.py` through the bypass header |
| 6 | operator | lift: `MAINTENANCE=0` (secret put). Open the app on each phone (else WorkManager backoff waits); a park syncs |
| 7 | operator | install the v3 APK **over** the old one on each phone (same key: data and login stay) |
| 8 | Claude, later | 7 days without a `v2` log line + 30 days since step 6: remove v2 layer and photo fallback; dropping old tables = separate, confirmed step |

**Rollback before step 6** (migration or smoke fails): `wrangler d1 time-travel restore dot-lab-parking --bookmark=<step 3>`
+ deploy the maintenance build, then `MAINTENANCE=0`. **Loses nothing**: no write was accepted after the bookmark, and
the migration never touches R2. **After step 6** the same restore loses v3-era writes, and R2 photos deleted by v3
stay deleted (their parks then show no photo). After step 7 a rollback also needs the old APK.

Operator: the wizard (steps 2–6) + reinstall over the old app; no re-joining or re-adding cars.

## 10. Open questions

None — the four earlier questions were settled by the operator (D8 create default, D13, D14, D15).
