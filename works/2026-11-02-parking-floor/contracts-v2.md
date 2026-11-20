# 어디댔어 v2 — 계약 (전송 형식 · Kotlin 공용 이름)

plan-v2.md의 모든 task가 이 이름과 형식을 쓴다. 바꾸려면 이 파일을 먼저 고친다.

## 1. 전송 형식 (JSON, Worker `/v2/*`)

```jsonc
// Car — 서버에 btAddress 없음(폰 로컬 CarLinkStore)
{ "id": "c_3f9a1b2c", "name": "흰 차", "color": -723724, "plate": "12가3456", "order": 0, "updated": 1795090000000 }
// Spot / Park — raw(인식 원문)는 서버로 보내지 않는다. "photo"는 서버가 정한 값(보내도 무시)
{ "id": 1795090123456, "carId": "c_3f9a1b2c", "atMs": 1795090123456,
  "floor": "B2",            // "B1".."B9" | "1F".."9F" | "ROOF" | null
  "zone": "C",              // ≤8자, 한 글자/한글/색 이름("파랑") | null
  "number": 37,             // 1..999 | null
  "pillar": null,           // ≤12자 "가-3" | null
  "memo": "엘리베이터 옆",   // ≤40자 | null
  "state": "PARKED",        // "PARKED" | "DRIVING"
  "photo": false }
// 서버가 붙여 돌려주는 필드: "by" (이름), "byId", "mine" (내가 남겼나), "drivingBy" (DRIVING일 때 운행한 사람 이름)
```

- id 생성: `Car.id = "c_" + 8자리 소문자 hex(SecureRandom)`, `Park.id = System.currentTimeMillis()`(같은 ms면 +1).
- Floor 문자열 ↔ Kotlin: `Under(n)` = "B$n", `Above(n)` = "${n}F", `Roof` = "ROOF". 화면 표기는 `Floor.label()` = "B2" / "2F" / "옥상".

| 라우트 | 요청 | 응답 / 규칙 |
|---|---|---|
| `POST /v2/groups` | `{name, groupName}` | `{groupId, memberId, secret, groupName, members, cars:[]}` rate limit(IP) |
| `POST /v2/peek` | `{code}` | `{groupName, count}` 인증 없음, rate limit(`/v2/join`과 같은 키), 초대를 소모하지 않음 |
| `POST /v2/join` | `{name, code}` | `{groupId, memberId, secret, groupName, members, cars}` + 가족에게 push `{type:"members"}` |
| `POST /v2/invites` | — | `{code, expiresAt, link}` link = `env.LINK_BASE + "#" + code`. 이전 초대는 닫되 **살아 있는 요청에 준 초대는 남김**(v1 `/v1/invites`도 같음) |
| `PUT /v2/token` | `{fcmToken}` | v1 `/v1/token`과 같은 동작 |
| `GET /v2/state` | — | `{groupName, members:[{id,name,me,owner,lastSeen}], cars:[Car], parks:{carId: Park+by/byId/mine/drivingBy}}` |
| `PUT /v2/cars/{id}` | `{car}` | 가족당 ≤4(5번째 409 `full`), 키 = (group_id, id), push `{type:"cars"}` |
| `DELETE /v2/cars/{id}` | — | 그 차 parks2 행·R2 사진 삭제, push `cars` |
| `PUT /v2/cars/{id}/park` | `{park}` | 같은 id = 덮어씀(고치기), `by/byId` 유지. 받은 park의 state가 DRIVING이면 `driving_by = me.name`, PARKED면 null. 다른 id = `atMs`가 더 크면 받고 이전 json → `prev_json`, prev보다 오래된 그 차 사진 삭제. 아니면 **409** `{error:"stale", park:<서버 값>}`. push `{type:"park", carId}` |
| `POST /v2/cars/{id}/state` | `{state:"DRIVING"\|"PARKED"}` | id와 무관하게 **현재** 기록의 state만 바꿈, DRIVING이면 `driving_by = me.name`. 기록 없으면 404. push `park` |
| `POST /v2/cars/{id}/undo` | `{parkId}` | 현재 id == parkId일 때: prev 있으면 prev를 되살림, 없으면 그 차 기록을 비움(행 삭제). prev = null, 현재 사진 삭제, **push `park`**. 현재 id ≠ parkId면 409 `{error:"moved", park}` |
| `PUT /v2/cars/{id}/photo?park={parkId}` | `image/jpeg` 바이트 ≤400 KB | parkId가 그 차 **현재** 기록이 아니면 409 `{error:"moved"}`. 맞으면 R2 `g/{group}/{car}/{parkId}.jpg`, park.photo=true, push `park` |
| `GET /v2/cars/{id}/photo?park=` | — | jpeg 바이트 |
| `DELETE /v2/members/{id}` | — | 대상이 **같은 가족**일 때만. 본인 / 방장 / 대상 `coalesce(last_seen, created)` < now−30일이면 누구나. 방장이 빠지면 가장 오래된 사람이 방장(v1 규칙) |
| `PUT /v2/requests` | `{req}` | 가족에게 요청하기 수락(인증). `req` = 링크의 H = sha256(S) `^[0-9a-f]{64}$`(아니면 400 `req`). **서버가** 새 코드 C를 만들어 내 가족의 invites 행(+24h)을 넣음 — `dropInvites` 안 부름(가족의 다른 초대는 그대로). 행 = `requests(req_hash = H 그대로, group_id, code = C 정규화 12자, expires = now+24h)`. 살아 있는 행이 이미 있으면 **409** `taken`(먼저 받은 쪽이 이김, 만든 invite는 지움). `{ok:true}` |
| `POST /v2/requests/poll` | `{secret}` | 요청한 폰만 가진 S(`^[0-9a-f]{32}$`, 아니면 400)로 조회 — 서버가 sha256(S)로 행을 찾음. 링크(H)만 본 사람은 못 가져감. id를 URL에 넣는 GET 라우트는 없음. 인증 없음, rate limit(IP, 키 `/v2/requests`). 없거나 만료 404, 있으면 `{code:"XXXX-XXXX-XXXX"}`. 조회는 지우지 않음. 그 code로 `POST /v2/join`(v1 `/v1/join`도)이 성공하면 같은 code의 행 삭제. 가족 삭제(`dropGroup`) 때도 삭제 |
| `POST /v2/requests/cancel` | `{secret}` | 요청한 폰의 [취소]·[아니요]. S 형식 틀리면 400, rate limit은 poll과 같은 키. 그 요청 행과 그 행의 invite 삭제. 없어도 `{ok:true}`(멱등) |
| `DELETE /v2/me` | — | 내 member 삭제(방장이면 가장 오래된 사람에게 넘김). 마지막 1명이면 그룹의 invites·cars·parks2·R2 `g/{group}/` 전부 삭제. **v1 `dropGroup`도 같은 범위로 고친다** |

- park·state·undo·photo 라우트는 차가 **그 가족의 cars에 있는지** 먼저 확인, 없으면 404.
- **라우팅 순서:** `handle()`의 `BODY_MAX` 검사보다 **앞에서** `/v2/` 경로를 `v2.handle()`로 보낸다. v2는 JSON 4 KB, 사진 라우트만 400 KB(`content-length` 없거나 넘으면 413), 사진은 `req.arrayBuffer()`.
- 인증된 v2 요청마다 `last_seen`을 갱신하되 같은 값이 60초 안이면 쓰지 않는다. `newMember`(v1·v2 모두)가 `last_seen = created`로 넣는다.
- 30일 사진 삭제 = R2 lifecycle 규칙(`npx wrangler@4 r2 bucket lifecycle add dot-lab-parking-photos --expire-days 30`) + 크론 `scheduled`이 **`atMs`** 기준 30일 지난 parks2의 `photo`를 false로. `export default { fetch, scheduled }`.
- `wrangler.toml`: `[vars] LINK_BASE = "https://dot-lab-parking.dotconnects.workers.dev/j"`, `ASSETLINKS_SHA256 = "<서명 지문>"`, `[[r2_buckets]] binding = "PHOTOS"`, `bucket_name = "dot-lab-parking-photos"`, `[triggers] crons = ["0 18 * * *"]`.
- 같은 Worker가 서빙: `GET /j` (초대 페이지 HTML, UTF-8 `<meta charset>`: 코드 = `location.hash` 표시·[복사]·[앱으로 열기] = `intent://dot-lab-parking.dotconnects.workers.dev/j?c=<code>#Intent;scheme=https;package=kr.dot.wherepark;S.browser_fallback_url=<설치 안내 URL>;end` — 코드는 `?c=`로, JoinActivity는 `fragment` 또는 `c` 쿼리를 읽음), `GET /.well-known/assetlinks.json` (`ASSETLINKS_SHA256` 여러 개 콤마 → 배열).
- 같은 Worker가 서빙: `GET /r` (가족 요청 페이지, 링크 `/r#<H>.<URL 인코딩 이름>`, H = sha256(S) 64 hex). 앱이 있으면 App Links가 `RequestActivity`를 먼저 엶. 없으면 "받으려면 어디댔어가 있어야 해요" + [앱으로 열기] = `intent://…/r?q=<fragment 그대로>#Intent;scheme=https;package=kr.dot.wherepark;S.browser_fallback_url=<이 페이지>;end` (Play 안내 없음).
- 가족에게 요청하기 흐름: 새 폰이 비밀 S 32 hex(SecureRandom)를 만들어 로컬 `wp_request {secret,name,createdMs}`(24시간 지나면 버림)에만 두고 H = sha256(S)를 담은 `/r` 링크를 카톡으로 보냄 → 대기 화면에서 **10초마다**(분당 6회 < rate limit 10), 앱 시작·복귀 때 한 번 `POST /v2/requests/poll {secret:S}` → 가족이 [받기] = `PUT /v2/requests {req:H}` 하나 → 새 폰은 code를 받아도 **바로 들어가지 않음**: `POST /v2/peek` → "<가족 이름> · N명에 들어갈까요?" [들어가기] [아니요] (앱 시작 때 code가 와 있으면 JoinActivity로 이 확인 화면을 엶; 이미 가족이 있으면 기존 "지금 가족에서 나가고 들어갈까요?" 확인) → [들어가기] = 저장한 이름으로 기존 합류(§3 "합류"). [아니요]·[취소] = `POST /v2/requests/cancel`(실패 무시) + 로컬 삭제. 로컬 요청은 **모든** 성공한 합류·가족 만들기 때 지움. 400/404(코드 없음)일 때만 요청을 버리고, 429·409 full·네트워크 오류는 남겨 두고 "잠시 뒤 다시 확인할게요"/"가족이 꽉 찼어요"/"인터넷 연결을 확인해 주세요". 받는 화면: 링크 H가 내 폰의 요청이면 "내가 보낸 요청이에요…"(받기 없음), 가족이 없으면 "먼저 가족을 만들어야 받을 수 있어요" + [가족 만들기](→ 앱 홈/온보딩). 크론 `scheduled`이 만료된 requests 행을 지움(그 invite는 스스로 만료). 마이그레이션 `worker/migrations/2026-11-20-requests.sql`.
- D1 마이그레이션 `worker/migrations/2026-11-19-v2.sql` (추가만, 되돌릴 필요 없음):

```sql
-- 재실행 안전: 스크립트 `worker/tools/migrate_v2.py`가 PRAGMA table_info로 컬럼 유무를 보고 ALTER를 건너뜀
CREATE TABLE IF NOT EXISTS cars (group_id TEXT NOT NULL, id TEXT NOT NULL, json TEXT NOT NULL, ord INTEGER NOT NULL, updated INTEGER NOT NULL, PRIMARY KEY (group_id, id));
CREATE TABLE IF NOT EXISTS parks2 (group_id TEXT NOT NULL, car_id TEXT NOT NULL, json TEXT NOT NULL, prev_json TEXT, by_name TEXT, by_id TEXT,
  driving_by TEXT, updated INTEGER NOT NULL, PRIMARY KEY (group_id, car_id));
ALTER TABLE members ADD COLUMN last_seen INTEGER;   -- 없을 때만
ALTER TABLE groups ADD COLUMN name TEXT;           -- 없을 때만, 가족 이름(v1 그룹은 null → 화면 "우리 가족")
```

## 2. Kotlin 공용 이름 (package `kr.dot.wherepark.*`)

```kotlin
// core
sealed interface Floor { data class Under(val n: Int): Floor; data class Above(val n: Int): Floor; data object Roof: Floor }
fun Floor.label(): String; fun Floor.wire(): String; fun floorOfWire(s: String?): Floor?
data class Spot(val floor: Floor?, val zone: String?, val number: Int?, val pillar: String?, val memo: String?)
enum class Ambiguity { NONE, FLOOR_SIGN, ZONE_E_OR_2, P_FLOOR, JOINED }
data class ParseResult(val spot: Spot?, val ambiguity: Ambiguity, val options: List<Spot>, val carName: String?)
object KoNum { fun normalize(text: String): List<Tok> }          // 토큰: Num(v, korean: Boolean, digitsRead: Boolean) | Letter(c, fromSyllable: String?) | Word(s)
object SpotParser { fun parse(raw: String, carNames: List<String> = emptyList()): ParseResult }
data class Car(val id: String, val name: String, val color: Int, val plate: String?, val order: Int)
enum class ParkState { PARKED, DRIVING }
data class Park(val id: Long, val carId: String, val spot: Spot, val atMs: Long, val byId: String, val byName: String,
                val raw: String?, val photo: Boolean, val state: ParkState, val drivingBy: String? = null)
object CarResolver { fun resolve(explicitCarId: String?, btCarId: String?, spokenCarName: String?, cars: List<Car>, lastUsedCarId: String?): Car? }
object ParkRules {
  fun accept(cur: Park?, incoming: Park): Boolean                  // 같은 id = true, 다른 id = incoming.atMs > cur.atMs
  fun whenLabel(atMs: Long, nowMs: Long, zone: java.time.ZoneId = ZoneId.of("Asia/Seoul")): String
  //   같은 날 "14:32" · 달력상 어제 "어제 19:05" · 그 전 "11월 16일" (위젯·홈 공통)
}
enum class Shape { ROW, POSTER, PILLAR, DOT, TICKET }
data class Style(val bg: Int, val fg: Int, val mu: Int, val acc: Int, val onAcc: Int, val fl1: Int, val fl2: Int,
                 val radiusDp: Int, val family: String, val material: String, val font: String)
object Palette { fun default(shape: Shape): Style; fun shuffle(shape: Shape, prev: Style?, rnd: java.util.Random): Style } // 300번 안에 못 찾으면 default
object Contrast { fun ratio(a: Int, b: Int): Double; fun blend(top: Int, alpha: Double, under: Int): Int; fun readableOn(bg: Int): Int }

// data
data class Member(val id: String, val name: String, val me: Boolean, val owner: Boolean, val lastSeenMs: Long?)
data class Invite(val code: String, val expiresAt: Long, val link: String)
data class ApiState(val groupName: String, val members: List<Member>, val cars: List<Car>, val parks: Map<String, Park?>)
sealed interface Op { val key: String; val seq: Long                  // §3 규칙
  data class PutCar(val car: Car): Op; data class DelCar(val carId: String): Op; data class PutPark(val park: Park): Op
  data class Undo(val carId: String, val parkId: Long): Op; data class State(val carId: String, val state: ParkState): Op
  data class PutPhoto(val carId: String, val parkId: Long, val file: String): Op }
object SyncQueue { fun add(ctx, op: Op); fun ops(ctx): List<Op>; fun drop(ctx, key: String, seq: Long); fun kick(ctx); fun generation(ctx): Long }  // unique work "wp-sync", APPEND_OR_REPLACE
//   SyncWorker: §3 규칙. Api.exec는 (status, body) 반환
object ParkStore { fun latest(ctx, carId: String): Park?; fun prev(ctx, carId: String): Park?; fun history(ctx, carId: String): List<Park>
                   fun save(ctx, p: Park); fun undo(ctx, carId: String): Park?; fun lastUsedCarId(ctx): String?
                   fun markSendStarted(ctx, parkId: Long); fun sendStarted(ctx, parkId: Long): Boolean }
object CarStore { fun cars(ctx): List<Car>; fun upsert(ctx, c: Car); fun remove(ctx, id: String); fun replaceFromServer(ctx, cars: List<Car>) }
object CarLinkStore { fun address(ctx, carId: String): String?; fun carOf(ctx, address: String): String?; fun set(ctx, carId: String, address: String?) }
object FamilyStore { fun joined(ctx): Boolean; fun me(ctx): Member?; fun members(ctx): List<Member>; fun groupName(ctx): String?; fun clear(ctx) }
object Api { fun state(ctx): Result<ApiState>; fun peek(code: String): Result<Pair<String, Int>>; fun join(ctx, code: String, name: String): Result<ApiState>
             fun createGroup(ctx, name: String, groupName: String): Result<ApiState>; fun invite(ctx): Result<Invite>; fun setFcmToken(ctx, token: String?)
             fun exec(ctx, op: Op): Pair<Int, org.json.JSONObject?>; fun removeMember(ctx, id: String): Result<Unit>; fun deleteMe(ctx): Result<Unit> }

// widget
data class WidgetSpec(val appWidgetId: Int, val shape: Shape, val carId: String?, val strength: Int, val style: Style, val prevStyle: Style?)
data class Hit(val carId: String, val rectDp: android.graphics.RectF, val kind: String)   // kind = "MIC" | "ROW"
data class Rendered(val bitmap: android.graphics.Bitmap, val hits: List<Hit>)
object WidgetRenderer { fun render(ctx, spec: WidgetSpec, cars: List<Car>, parks: Map<String, Park?>, sizeDp: android.util.SizeF,
                                   density: Float, wallpaperColor: Int, now: Long, micReady: Boolean): Rendered }
```

## 3. 동기화 규칙 (서버 = 정본)

| 상황 | 규칙 |
|---|---|
| Op key | `car:{carId}`(PutCar·DelCar), `undo:{carId}:{parkId}`(Undo, 여러 개가 쌓임), `park:{carId}`(PutPark), `state:{carId}`(State), `photo:{carId}:{parkId}`. 각 op에 증가하는 `seq` |
| 처리 순서 | 종류 순 `car:` → `undo:` → `park:` → `state:` → `photo:`, 같은 종류 안에서는 seq 순. `DelCar`가 대기하면 그 차의 `undo:/park:/state:/photo:` op는 버림. **`PutPark`를 넣을 때 같은 차의 `state:` op는 버림**(새 기록이 운행 중 뒤에 오는 경우) |
| State 합치기 | `State` 추가 때 같은 차 `PutPark`가 대기 중이면 그 park의 state만 바꿔 PutPark에 합침(State op 안 만듦). 아니면 `state:` op |
| 큐 추가 | 같은 key가 있으면 새 op로 바꿈(seq 새로, 처리 순서는 위 종류 순이라 뒤집히지 않음). **모든 큐 읽기·쓰기는 `SyncQueue` 안의 한 lock**(`synchronized(SyncQueue)`) |
| 보낸 뒤 | **모든 drop과 응답의 로컬 쓰기는 그 key의 현재 op가 보낸 seq와 같을 때만**(아니면 새 op가 이미 들어온 것이므로 아무것도 안 함). 2xx → drop. 409 stale/moved → drop + 응답에 park가 **있을 때만** 로컬에 씀. 409 full(차) → drop + 로컬 차 삭제 + 알림 "차는 4대까지예요". 400/404/413 → drop + 로그. 401 → **요청에 쓴 인증이 지금 저장된 인증과 같을 때만** 큐 비움 + 가족 정보·pendingGroup 지움. 로컬 차·기록·CarLinkStore는 **남김**. 홈 맨 위에 "가족에서 나가게 됐어요" 띠 + [새 가족 만들기](→ pendingGroup 저장, 그룹 생성 뒤 로컬 차를 같은 id로 `PutCar`) [초대 코드로 들어가기]. 가족이 없는 동안 새 기록은 로컬에만 저장하고 큐에 넣지 않음. 그 외/네트워크 → retry |
| Worker 반복 | unique work `wp-sync` **`APPEND_OR_REPLACE`**. 보내는 op의 `(key, seq)`를 `inFlight`로 기록. 큐가 빌 때까지 반복, 성공 반환 직전 큐를 한 번 더 보고 남았으면 다시 enqueue. 끝나면 `Api.state`. 가족이 없고 `pendingGroup(name, groupName)`이 있으면 `createGroup`부터 — **그룹을 만드는 곳은 SyncWorker 하나뿐**(온보딩은 pendingGroup 저장 + kick만). 성공하면 pendingGroup 삭제. 401로 가족이 지워지면 pendingGroup도 지움(몰래 새 가족 안 만듦) |
| 병합 | GET 전에 `SyncQueue.generation()`을 떠 두고, 응답 받은 뒤 값이 바뀌었으면(그 사이 drop·add) 이 응답은 버림. `Merge.state`: 서버 cars·parks를 그대로 씀. 단 큐에 그 key(`car:`·`park:`) op가 남은 차는 로컬 값 유지. `DelCar` 대기 중인 차는 서버에 있어도 안 살림. `CarLinkStore`는 건드리지 않음. **`ParkRules.accept`는 같은 폰 안의 큐 정리에만 쓰고 병합엔 쓰지 않음** |
| 되돌리기 | 그 parkId가 **한 번도 전송을 시작하지 않았으면**(`ParkStore.sendStarted(parkId)==false`, 전송 시작 직전에 폰에 저장되는 표시) 로컬 prev 복원 + op 교체(prev가 있으면 `PutPark(prev)`, 없으면 drop). 그 외에는 로컬 prev 복원 + **같은 차 `park:` op를 버리고** `Undo(carId, parkId)` |
| 운행 중 | `State(carId, DRIVING)` op(기록 id 안 씀) |
| 합류 | ① 옛 차 목록(가족이 없고 pendingGroup만 있는 사람의 로컬 차 포함)·옛 인증·**pendingGroup**을 따로 떠 둠 ② 큐 비움 + pendingGroup 지움 ③ `join`(새 인증) 성공 → 새 인증 저장, 서버 state로 교체 ④ 옛 인증으로 `DELETE /v2/me`(실패 무시) ⑤ 옛 가족이 혼자였고 차가 있었으면 "차 n대를 새 가족으로 가져갈까요?" 예 → **같은 id로** `PutCar`(PK가 (group_id, id)라 충돌 없음, CarLinkStore 그대로 유지; 기록은 안 가져감). join 실패면 ①로 되돌림(큐·pendingGroup 복원). SyncWorker의 `createGroup` 결과는 lock 안에서 "가족 없음 + 같은 pendingGroup"일 때만 저장, 아니면 버림 |
| 사진 | `photo` 값은 **서버만** 정함(클라이언트 park의 photo는 무시). 클라이언트는 올리기 전 JPEG를 380 KB 밑이 될 때까지 품질 80→60→긴 변 1280→1024로 줄임, `setFixedLengthStreamingMode` |

ListenActivity intent extras: `car_id` (String?), `bt_car_id` (String?), `from` ∈ `widget|notif|tile|app|shortcut|practice`.
