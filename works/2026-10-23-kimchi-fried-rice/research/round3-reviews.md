# Round 3 reviews (2026-10-24) — six role agents on the 2D build (commit b7060ba)

Verdict, all six: not sellable yet. Raw notes and captures: scratchpad `artdir/`, `anim/`, `phys/`, `gd/`, `prog/`.

## Carry into the 3D rebuild
- **Wok, not a pan.** Round bottom, walls flaring 30–40°, matte carbon steel: blue-grey heat centre with bronze/blue
  temper rings, seasoned black wall, thin bright lip; steel tube handle with a wooden end. (chef, art)
- **Rice reads as rice.** Grain ≈ 0.018–0.022 R (6 mm in a 36 cm wok), 1:2.2–2.5 ellipse, 1200+ grains, one oil
  highlight. No orange "bed" (reads as curry/soup) — at most a thin oil sheen. Kimchi = wilted cabbage strip:
  translucent pale rib #E8C48A/#F2D7A0 with red edge #B8261C, dark folded back #7E1A12. Scallion ring #3F8A2C / #DDEFC0.
- **Doneness colours:** raw #EFE6D4 with red stains → glossy red-orange #D65A2C → wok-hei brick #B24822 with brown
  specks → scorched bottom only #6B3A1E → burnt #2A1A12. Only the side touching the metal browns; the toss turns the
  brown side up — the reward is visible. (chef, art)
- **Toss shape:** a breaking wave — food climbs the far wall and curls back toward the cook. In world space the rice goes
  mostly straight up and down; the wok slides out under it (Ko & Hu 2020). Height 0.4–0.7 × wok diameter.
  Airtime ~0.4–0.45 s (G ≈ 26–30 R/s², real 54). No apex hang. Grains: ±25% vh jitter, stagger ~0.12 s, sideways
  ±0.25, landing spread ±60 ms. Wok leaves the flame by 5–8 cm at most; push 10–15 cm, lip snap 15–20°. (physics, motion, chef)
- **Wok curve:** no wind-up; push 0–90 ms easeOutCubic → lip snap 90–140 ms → 2-frame hold → return 140–320 ms →
  damped spring overshoot (ω≈18, ζ≈0.45). While the food is up the wok waits (pulled back, slightly low), rises 70 ms
  before the first landing and gives on contact. (motion)
- **Impact:** no shake on the flick; shake 2–3 px + hit-stop 33 ms on big landings; egg perfect 60–100 ms. (motion)
- **Fire:** blue columns under the wok wrapping up its sides; wok-hei flare over the FAR lip on the snap, yellow
  #FFC24A → orange #FF7A1A, 0.3–0.5 R high, 0.3–0.4 s. Steam (white, early), oil smoke (thin blue-grey, steady),
  burn smoke (grey-brown, after 2–3 s of neglect). (chef, art, motion)
- **Pile under tilt:** centre from the real pose, pc = clamp(-tan(roll), -tan(pitch))/0.7; piles up against the low
  wall; top layer follows fast (τ 0.15 s), bottom slow (0.35 s); sim runs before the first flick. Tilting far spills
  (operator 10-24). Landing is inelastic: stop within 80 ms. Shadows keep alpha ≥ 0.25 at any height. (physics)
- **Egg:** whites set from the edge (clear → white), puff and bubble in ~5 s, lacy brown edge by 15–20 s; yolk dome
  #F7A21B under a thin white film; flip only 5–10 cm high, 180° in ~0.3 s, edge-on near the apex; land with squash
  1.15/0.85 + two damped wobbles; the angle toast at the egg on the hit-stop frame. (chef, motion)
- **Game:** the cue must be visible every toss and readable ahead (rim fills 0.4 s, judge window 0.3 s); chef rhythm is
  2–3 tosses/s, so fast tossing costs only "raw", burning comes only from neglect; add a mid-round event (more rice /
  soy sauce / high heat) and ±10% tempo per round; egg rotation tied to visible strength, a real catch (follow the
  shadow by tilting), burst as a slope 1.2–1.4; mash says "아직!" at once; words land on the landing. (game)
- **Transition (1.4 s):** lift → plate slides in → rice pours as grains → plate squash + stamp → plate leaves → egg drops
  in with a squash and sizzle. (motion)
- **Engine:** fixed 1/60 step with an accumulator (game time was tied to frame rate: a 12 s round took 29 s);
  suspend/resume audio on visibilitychange and resume on every input; stop rendering behind the result slip; time
  limit must not cut an egg in the air; iOS motion denied → say so. Measure on a mid Android. (programmer)

## Keep
Result slip (receipt, stamp, count-up, flick to replay), loss lines with cause and number, 35° view with the handle at
the bottom, pivot on the rim opposite the toss, food riding the wok, tilt signs (verified by numbers), motion input
(calibration, refractory, armAfterRelease, orientation), per-grain sim, orders (반숙/완숙/불맛), combo flame.
