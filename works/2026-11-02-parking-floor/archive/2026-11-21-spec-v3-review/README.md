# spec-v3-groups — review round 1 (2026-11-21)

Conclusions live in `../../spec-v3-groups.md`. This folder keeps the arguments.

- `codex-adversarial-review.txt` — Codex adversarial review of the first draft: 8 High, 5 Medium.

## Why the spec chose what it chose

| Choice | Why |
|---|---|
| One device identity + per-group memberships | `Bearer id.secret` already names a phone; every member row today = one phone in one family, so `device.id = old member.id` keeps installed credentials valid. One-row-per-group = 3 credentials per phone, car ownership orphaned when "its" group is left, name stored 3×, push dedupe by token matching |
| Server keeps all my cars + parks once I have a device | re-including shows the current park at once; only the owner can read an unshared car |
| Owner ∈ every group of the car (invariant) | leave/remove excludes the owner's cars in the same batch; authz is one join |
| `/v3` + thin `/v2` layer, `/v1` → 410 | Worker ships first; installed app keeps working |
| Photos `c/<car>/<park>.jpg`, legacy key as fallback, no copy | photos expire after 30 days anyway |
| Widget lists my cars + main car | a company group's cars don't flood "외 N대" |

## Findings → resolution

| # | Finding (verified against code) | Resolution in spec |
|---|---|---|
| 1 | Re-run migration resurrects removed members / misses removals (`OR IGNORE` never deletes) | double-run dropped; one run inside a write-blocked maintenance window (§9) |
| 2 | Phone joining between runs gets 401 → app wipes credential + queue (`Api.state` → `dropIfSame`) | no gap: v3 deploys after the single run, while the window still answers 503; app keeps queue + credential on 503 (`settle` else-branch = retry, only 401 drops) |
| 3 | Collision winner can flip between runs | persistent `car_map` table written first; re-runs read it |
| 4 | Authz check and CAS are separate statements | authz inside the conditional write (`WHERE EXISTS visible`), include = conditional insert, both in the same D1 transaction as leave batches |
| 5 | v2 "not mine → 200 with server car" leaks other groups' cars | invisible → 404 first; the 200 path only for visible cars; test |
| 6 | Older park can win a CAS race; newer gets 409 `moved` and app overwrites with the older one | CAS loss → re-read and re-judge recency, retry ≤3; 409 only when the server park is really newer/equal |
| 7 | Queued `State` has no target park; stale 가져감 hits a newer park | `State` carries `parkId`; v2 legacy State judged by the device's previous `last_seen` (proxy — the server cannot know what the old app last saw) |
| 8 | Rollback is not "old tables untouched": `groups/invites/requests` are shared, photos deleted | rollback = D1 Time Travel restore + old Worker; lossless while writes are blocked; after lift, v3-era writes + R2 deletions are lost (stated) |
| 9 | v2 queued `PutCar` would become a private car | upgrade marks those ids; after the first v3 pull they get `Share` into the single migrated group |
| 10 | Re-keyed car's photo fallback uses the new id | `legacy_car_id` kept |
| 11 | Join cap/invite consume/insert not atomic; same for 3-group cap | one batch of conditional statements |
| 12 | Pull loops per car; fan-out unbounded; missing indexes; no auth rate limit | fixed-count queries, FCM collapse + app-side debounced pull, indexes, `state:<device>` limit |
| 13 | `npm test` runs only `worker.test.js`; tests use MemoryStore only | `node --test test/`; one local-D1 test file for conditional writes |

## Disagreements / partial

- #7 legacy v2 State: spec uses "park row changed after this device's previous contact → reject" as the proxy. Exact
  "what the old app last saw" is not knowable server-side; the old app is temporary.
- #12 debounce: done by FCM `collapse_key` + app-side coalesced pull (no server-side debounce state, which could drop
  the last event of a burst).
- Maintenance window blocks reads too (simpler than per-route rules; a failed read is harmless).
