# Pokémon GO encounter / catch loop — mechanics & game-feel numbers (research, 2026-11-10)

Purpose: learn the *genre mechanics* (input mapping, tolerances, odds, feedback timing) for our own original catch game.
We copy no art, characters, ball design, sounds or UI look.

Confidence tags: **[GM]** = datamined game master (PokeMiners, current) · **[B]** = Bulbapedia (community-verified) ·
**[P]** = client protocol field (public POGOProtos) · **[C]** = community guide / forum (weaker) · **[U]** = unverified,
from general play knowledge, measure from a video before relying on it.

---

## 1. Throw input (swipe → throw)

| Item | What GO does | Tag |
|---|---|---|
| Model | Real 3D physics ball (Unity rigidbody + collider). The swipe sets launch speed and direction; gravity makes the arc. Too weak → lands short on the ground; too strong → flies over the head. | [C][U] |
| Who judges the hit | **The client.** The request carries `hit_pokemon` (bool), `normalized_reticle_size` (1.0–2.0 = the 2−r throw factor), `spin_modifier` (0–1), `normalized_hit_position`. Server only rolls the catch. | [P] [proto](https://github.com/tejado/pgoapi/blob/master/pgoapi/protos/POGOProtos_src/Networking/Requests/Messages/CatchPokemonMessage.proto) |
| Hit = collision | Ball collider vs creature collider anywhere along the flight (body cylinder + head sphere), not "where the ball ends up". Pidgey: `collisionRadiusM 0.134`, `collisionHeightM 0.252`, `collisionHeadRadiusM 0.126`. | [GM] |
| Aim assist / homing | No visible homing in the real game is documented. Clones add it (see §8). | [U] |
| Missed ball | Lands/rolls behind; for ~1 s it can be grabbed back (well-known "recover ball" trick). | [C] [androidcentral](https://androidcentral.com/pokemon-go-tricks-recover-pokeballs-dont-hit-their-target) |
| Weak / sideways swipe | Ball drops a short distance in front; still consumed. | [U] |
| Typical miss rate | No primary data. A popular bot defaulted to `hit_rate 0.8` to look human (a guess by bot authors, not a measurement). | [U] [bot](https://github.com/PokemonGoF/PokemonGo-Bot/blob/master/pokemongo_bot/cell_workers/pokemon_catch_worker.py) |
| Deflection | Throwing while the creature attacks/taunts → ball bounces off (no catch roll). Advice: release in the last third of the attack anim. | [C] |

## 2. Curveball

| Item | Value | Tag |
|---|---|---|
| Gesture | Hold the ball and circle it; it starts spinning; sparkles appear once spin is enough → that is the "armed" signal. | [C] |
| Threshold | `spinBonusThreshold: 0.5` — `spin_modifier` ≥ 0.5 counts as curveball (bots send 0.5–1.0 for curve, <0.5 for not). | [GM] [P] |
| Bonus | ×1.7 catch multiplier (+ small XP) | [B] |
| Trajectory | Ball breaks sideways late in flight, in the spin's direction. Clockwise spin must hit the creature on the right half, counter-clockwise on the left half; crossing the midline is not needed. Players aim off to one side. | [C] [hub](https://pokemongohub.net/post/guide/throw-curveballs-correctly/) |

## 3. Ring

| Item | Value | Tag |
|---|---|---|
| Throw factor | `2 − r`, r = ring radius / max radius at throw time, only if the hit lands **inside** the coloured ring | [B] |
| Tiers (thresholds in factor) | Nice ≥ 1.0, Great ≥ 1.3, Excellent ≥ 1.7 → **Nice r ∈ (0.7,1], Great (0.3,0.7], Excellent ≤ 0.3** | [GM] `niceThrowThreshold 1.0 / great 1.3 / excellent 1.7` |
| Outside ring but on body | Caught attempt with factor 1 (no text) | [B] |
| Smallest ring | Ring shrinks to ~5 % of max (guide figure); hitting at the very smallest = guaranteed "critical catch" (1 shake) | [C] / [B] |
| Cycle | Shrinks max → min then snaps back (sawtooth feel), roughly 1.5–2 s per cycle (not in GM) | [U] |
| Static outer circle | White circle = max ring, coloured ring inside shrinks | [U] |
| Colour | Catch chance with ball+berry+medal (not throw/curve): green ≈ high → yellow → orange → red ≈ low (linear hue 120°→0° ≈ 100 %→0 %) | [B] |
| "Inside ring" judgement | Client-side, on the screen-projected hit point vs ring radius (exact rule not public) | [U] |

## 4. Catch formula and odds

`P = 1 − (1 − BCR / (2·CPM))^M`, `M = Ball × Berry × Throw × Curve × Medal × Encounter` [B]

| Factor | Values [B] |
|---|---|
| Ball | 1 / 1.5 / 2.0 |
| Berry | Razz 1.5, Silver Pinap 1.8, Golden Razz 2.5 |
| Throw | 1 (none), 1.0–1.3 Nice, 1.3–1.7 Great, 1.7–2.0 Excellent |
| Curve | 1.7 |
| Medal | 1.1 / 1.2 / 1.3 / 1.4 |
| Critical catch | flat 1 % per throw, or ring-at-minimum hit → 1 shake, caught |

Base capture / flee per species [B list]: commons (Pidgey, Rattata, Weedle, Caterpie) **50 % / 20 %**, Magikarp 70 %/15 %,
Pikachu 20 %/10 %, Dragonite 5 %/5 %, legendaries 2 %. Flee is rolled **only after a break-out**.

Real odds for a 50 % common with a plain ball, no bonus, by wild level (CPM [B]):

| Level | 1 | 5 | 10 | 15 | 20 | 30 |
|---|---|---|---|---|---|---|
| P | 100 % | 86 % | 59 % | 48 % | 42 % | 34 % |

Wild level is capped by trainer level (low for a new player) → **a beginner's commons are caught on almost every hit**;
the game gets "hard" only later. Lv20 common (42 % base): Great 56 %, curve 60 %, curve+Great 75 %, curve+Excellent 82 %.

Shakes: 3 wobbles then success; break-out after 1–3 wobbles. The protocol has no wobble count, so the client picks it [P][U].

## 5. Creature behaviour in the encounter (GM `pokemonSettings.encounter`)

| Field | Pidgey | Rattata | Zubat | Abra | Dragonite |
|---|---|---|---|---|---|
| movementType | JUMP | JUMP | ELECTRIC (zig-zag) | PSYCHIC (teleport) | FLYING |
| jumpTimeS | 1.4 | 0.9 | 1.0 | 1.0 | 1.0 |
| attackTimerS / attackProbability | 29 / 0.1 | 29 / 0.2 | 29 / 0.1 | 10 / 0.05 | 8 / 0.1 |
| dodgeProbability / dodgeDurationS / dodgeDistance | 0.15 / 1.0 / 1.0 | 0.3 / 1.0 / 1.0 | 0.2 / 0.7 / 0.125 | 0.2 / 2.4 / 0.6 | 0.15 / 1.0 / 1.0 |
| min/max action frequency (s) | 0.2 / 1.6 | same | same | same | same |
| cameraDistance (m) | 1.89 | 1.89 | 4.8 | 4.5 | 4.5 |
| collision radius / height (m) | 0.13 / 0.25 | 0.19 / 0.25 | 0.05 / 0.05 | 0.28 / 0.56 | 0.6 / 1.05 |

Read-out: small creatures sit **~1.9 m** away; the *collider is smaller than the model*. Most of the time the creature
idles; it acts every 0.2–1.6 s (idle variants), dodges with 15–30 % chance (ring hidden / ball misses for ~1 s), and
attacks rarely (deflects balls). `movementType` gives each family a readable rhythm. Movement types across all species:
Jump 581, Hovering 116, None 78, Flying 63, Psychic 19, Electric 14 [B list].

AR: non-AR = virtual ground with the creature standing on it + shadow; AR = camera feed, creature on the floor
(AR+ uses a detected floor plane, walking closer raises an "awareness" meter: `arCloseProximityThreshold 0.5`,
`arLowAwarenessThreshold 0.3` [GM]).

## 6. Feedback / juice sequence ([U] — timings eyeballed, verify on a screen recording)

```
release ──► flight ~0.6–0.9 s (ball spins, shrinks with depth, curve sparkles trail)
hit ──────► text pops at hit point: "Nice!/Great!/Excellent!" (+ "Curveball") rising & fading ~1 s
            ball bounces back up ~0.3 s, opens, creature flashes / turns to light, sucked in ~0.5 s
            ball snaps shut, drops to ground, 1 small bounce ~0.4 s
wobble ×1..3 ─► each: pause → tilt-left/right shake with button glow, ~0.8–1.0 s apart, haptic per wobble
caught ───► "click", star burst from the ball, "Gotcha!" banner ~1.5 s → result screen (XP/candy/dust lines count up)
break-out ─► ball bursts in light, creature pops out with a short "angry" anim, ring returns; next ball in hand
flee ─────► creature fades / puff of smoke, short message, back to map
miss ─────► ball lands behind, rolls; next ball rises into hand ~1 s later
```
Total normal catch ≈ 12–14 s from throw to result (players call it slow; a "quick catch" exploit cuts it to <2 s) [C].
Critical catch = only 1 wobble + special stars — the best throw is rewarded with *less waiting*.

## 7. Layout ([U], current UI from memory)

| Where | What |
|---|---|
| Bottom centre, ~12–15 % from bottom | ball, large (≈ 1/5 screen width) |
| Top left | run / exit |
| Top right | AR on/off toggle, camera (snapshot) |
| Bottom left / right | berry button / ball-type switcher |
| Above creature | name + level/CP; ring drawn on the creature's body centre |

## 8. Open-source GO-style throws (read code)

| Repo | File | Algorithm (concrete) |
|---|---|---|
| [kynesisgames/Pokemon-GO](https://github.com/kynesisgames/Pokemon-GO) (Unity, "replicating GO throwing") | `Assets/PokemonGO/Code/Thrower.cs`, `PokeBall.cs` | Throw if `dot(avgDelta.normalized, up) > 0.2` **and** `force = |avgPointerDelta| × 6 > 1`. **Only the swipe's x direction is used** (`pointerInfluence = (1,0)`); forward is fixed: `throwVec = normalize(sidewaysDir + forward) × force`. Path = quadratic Bezier start → mid(lifted `force × 0.5`, half if curve) → end, extrapolated ×2, followed at speed from an AnimationCurve; gravity only after the path/at collision (`Reflect` bounce ×2). **Aim help:** if `|end − creature| < helpRadius (2)`, lerp end toward creature per axis. Curve "charged" when ball angular speed > % of max (torque from dragging around the ball); curve shifts end sideways by 5 opposite the swipe. |
| [Harvard-Westlake/HWGo](https://github.com/Harvard-Westlake/HWGo) | `Assets/Catching Assets/PokeballThrow.cs` | Curve: each frame, cross product of (screen centre → last pointer) × (→ current pointer) adds ±`dt × 2` to `curveAmount`, clamp ±2.5, curve if `|curveAmount| > 1` → during flight a constant sideways force. Speed: `normalized = clamp01(|swipeVel| / dpi / 4000)` → `lerp(5, 40)` impulse along fixed 45° (forward+up). Direction from swipe is ignored. |
| [lucasvegi/PokemonGoCloneOffline](https://github.com/lucasvegi/PokemonGoCloneOffline) (Android) | `Controller/CapturaActivity.java` (`configuraTouchPokebola`) | 2D: velocity = total drag / total time; ball stepped `25 × v` per step with velocity decaying 4.5 %/step; **hit = ball rect intersects creature rect at any step** (swept test); even dragging the ball onto the creature counts. Off-screen → "try again", ball reset. |
| [indikman/AR-unity-pokemongo-sample](https://github.com/indikman/AR-unity-pokemongo-sample) | `Assets/Prepared/Scripts/PokeBall.cs` | `force = (end − start in world); force.z = |force|; force /= dt; AddForce(force × 10)` with gravity; trigger collider tag "monster". Minimal. |

Common pattern: **swipe speed is normalised and clamped**, direction is mostly *forward by default* (sideways from swipe x only),
and the hit is a **collision along the path**, not a single landing point. The one that feels most like GO adds a soft
aim-assist radius.

---

## Apply to our work (ranked)

Diagnosis first (from `geom.js` `release()` + `ui.js`): ball rests at y = 0.78 H; finger lifts after the drag around
y ≈ 0.55–0.7 H; creature centre sits at 0.35–0.5 H with a hit box ≈ 160 px tall on an 844 px phone (0.35 m × 0.84 ART at 1.2 m,
66° FOV). Landing = release + v × 260 ms, so the creature is hit only for **v ≈ 0.5–1.2 px/ms upward** — while a natural flick is
faster (≈ 2–4 px/ms, [U], measure with `G.lastThrow`). Fast flicks land at y < 0, get clamped to the top edge, and miss.
And only the *endpoint* is tested. That is why "throws don't land".

1. **Make distance forgiving: normalise speed, then sweep-test.** Replace `to = b + v × 260` with: direction = swipe angle
   (atan2 of the last-80 ms vector); reach = the distance to the creature × `k(speed)`, where
   `k = clamp(lerp(0.6, 1.6, (speed − 0.5) / 3.0), 0.6, 1.6)` → a wide middle band (≈1.2–2.8 px/ms) lands on the body, weak falls short,
   very hard flies over. Then **test the whole flight path against the box** (any sample u ∈ [0.6, 1] inside the box = hit; the
   creature is "at depth" late in the flight), not only u = 1. Over-the-head throws stay misses because the arc passes above the box.
   Target: ≥ 80 % of deliberate flicks hit on first try (measure 20 throws with `?seed`).
2. **Soft aim assist (lateral + depth).** If the predicted landing is within 1.0 × box height of the box edge, pull it toward the
   box centre by 50 % (kynesis `helpRadius` lerp). Keep it invisible; never pull into the ring (ring bonus stays skill).
3. **Curve must not ruin a straight aim.** Now the curve adds +15 % W at the end, so a curved ball aimed at the creature misses. Use a
   bow that returns: `bend = curve × 0.12 W × sin(π u)` (leaves and comes back), or GO-style: grant the curve bonus when the hit is on
   the spin's side of the creature. Also show **"armed" feedback while dragging** once |Σturn| ≥ 300° (ball glow + sparkles + 10 ms
   vibrate) — GO's sparkles are what make the gesture learnable.
4. **Raise catch odds to GO-beginner feel and use GO's shape.** Today hit = 50 % → half of all successful hits end in "도망쳤다".
   Use `p = 1 − (1 − 0.7)^M`, `M = (inRing ? 2 − r : 1) × (curve ? 1.7 : 1)`, cap 0.97 → hit 70 %, nice 75 %, great 84 %,
   excellent 89 %, curve-hit 87 %, curve-great 95 %. Tier cut-offs: keep ours (0.5 / 0.75) — more generous than GO's 0.3 / 0.7, fine for a
   one-visit web toy. Add **critical**: ring hit while r < 0.35 → 1 wobble, guaranteed (rewards skill with less waiting).
5. **Ring rhythm.** Our triangle 1.6 s is OK; GO's feel is shrink-then-snap. Try 1.8 s shrink (ease-in) + 0.2 s snap-back, min 0.25.
   Colour the ring by *catch chance* (green→yellow→red), not by tier — the tier is already shown by its size; for our odds it is
   mostly green, which itself says "easy".
6. **Juice sequence with timings** (all in `catchStep`/`SH`): hit text pops *at the hit point* and rises 0.8 s; ball bounces up
   0.25 s → creature flashes white then shrinks into the ball 0.45 s (we have shrink) → ball drops + 1 bounce 0.35 s → wobble 0.9 s each
   (tilt ±18°, pause between, 30 ms vibrate) → success: click + star burst + "잡았다!" 1.2 s; break-out: ball pops open with a flash, the
   creature springs back with a squash 0.3 s. Missed ball: roll off and fade 0.5 s (we have ghosts) and next ball rises from below
   0.3 s, never instantly.
7. **Creature life (cheap but alive).** Hop/idle variant every 0.2–1.6 s random (GM action frequency), contact shadow squashes on hop.
   Optional dodge: 10 % per throw, *telegraphed* (crouch 0.3 s, side-step 1.0 s, ring hidden) — add only after #1 measures ≥ 80 % hits.
   No attacks/deflection: they only add misses.
8. **Distance/size.** GO places small creatures at ~1.9 m with a collider smaller than the art. We use 1.2 m and the full art box
   (×0.84) — keep it (bigger target = easier), but put the creature lower (centre ~0.45–0.55 H, standing on the virtual floor) so the
   arc reads as "throw onto the floor in front of me".
9. **Own ball design (IP).** `drawBall` draws a red-top/white-bottom ball with a black band and centre button — that *is* GO's ball
   look, which we said we would not copy. Replace with our own shape/colour (e.g. D:OT colours, no black equator + button).
10. **Doc drift.** plan.md §3 says the ball shrinks 1 → 0.35; code `END_SCALE = 0.6`. Fix the plan when #1 lands.

Out of scope here but noted: GO's catch flow takes ~12–14 s and players dislike the wait; aim for 4–5 s total from hit to result.

## Sources
- [Bulbapedia — Catch rate (GO)](https://bulbapedia.bulbagarden.net/wiki/Catch_rate_(GO)) (formula, multipliers, ring colour, critical catch)
- [Bulbapedia — List of Pokémon by catch rate in GO](https://bulbapedia.bulbagarden.net/wiki/List_of_Pok%C3%A9mon_by_catch_rate_in_Pok%C3%A9mon_GO) (BCR, flee, movement type)
- [PokeMiners game master `latest.json`](https://github.com/PokeMiners/game_masters/blob/master/latest/latest.json) — `ENCOUNTER_SETTINGS`, `V0016_POKEMON_PIDGEY.encounter` etc. (fetched 2026-11-10)
- [POGOProtos CatchPokemonMessage](https://github.com/tejado/pgoapi/blob/master/pgoapi/protos/POGOProtos_src/Networking/Requests/Messages/CatchPokemonMessage.proto); bot usage: [PokemonGo-Bot catch worker](https://github.com/PokemonGoF/PokemonGo-Bot/blob/master/pokemongo_bot/cell_workers/pokemon_catch_worker.py), [Enhanced-Backend betterThrow.js](https://github.com/PokemonGo-Enhanced/Enhanced-Backend/blob/master/lib/mitm/handlers/betterThrow.js)
- [PokémonGoHub — curveballs](https://pokemongohub.net/post/guide/throw-curveballs-correctly/); [GameRevolution — quick catch (~14 s animation)](https://www.gamerevolution.com/guides/935096-pokemon-go-how-to-quick-catch-method-2023); [fone.tips — attack deflection](https://fone.tips/pokemon-go-great-throw/)
- Clones: links in §8.
- Not reachable: GamePress (DNS fail), Niantic help center (no catch-mechanics page found), no Niantic throw patent found
  (a 2024 ImagineAR v. Niantic suit cites US 8,777,746 "Gestures to encapsulate intent").
