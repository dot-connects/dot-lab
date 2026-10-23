# Input spec (input-principles lead, 2026-10-23). [src] = sourced, (est) = starting value to tune on a phone

## 1. Platform facts
- Device frame (W3C): x = screen right, y = screen top, z = out of screen. Held flat, handle (bottom edge) toward the
  user: +y = forward/away, +z = up. rotationRate deg/s: beta about x (pitch, lifting the far lip), gamma about y (roll),
  alpha about z (yaw). Device frame, so auto-rotation doesn't change them.
- iOS 13+: DeviceMotionEvent.requestPermission() from a tap, HTTPS, not in cross-origin iframes. iOS 13.4 bug: null
  values after grant.
- Android: no prompt, secure context. `acceleration` can be null (no gyro) → always derive from
  accelerationIncludingGravity (aIG). rotationRate can be null → detector must work on accel alone.
- In-app browsers (Instagram/Threads): no public report either way → probe at runtime.
- Rate ~60 Hz; Safari `interval` historically in seconds → use e.timeStamp for dt.
- Orientation lock impossible on iOS; phone held flat may auto-rotate the layout → hint to lock rotation.

## 2. Events
flick {strength 0..1(+), snap 0..1, dirX right+, dirY forward+, t}; tilt {x, y} per frame smoothed; source motion|touch.

## 3. Algorithm (per devicemotion)
```
dt = (e.timeStamp - last)/1000
a  = e.accelerationIncludingGravity
g  += (a - g) * dt/(TAU_G + dt)           // low-pass gravity, TAU_G = 0.20 s
lin = e.acceleration ?? (a - g)
m   = |lin|;  jerk = (m - mPrev)/dt
pitch = e.rotationRate?.beta ?? 0
gate = m > A_ON || |pitch| > W_ON
IDLE:  if gate && now - lastFlick > REFRACT: state=PULSE; t0=now; peak=0; jerkMax=0; imp=(0,0); tPeak=t0
PULSE: peak=max(peak,m) (tPeak); jerkMax=max(jerkMax,jerk); imp += lin.xy*dt; rot += (pitch,gamma)*dt
       if m < A_OFF or now-t0 > PULSE_MAX: emit; IDLE; lastFlick=now
emit: strength = clamp((peak - A_ON)/(A_MAX - A_ON))
      snap = clamp(1 - ((tPeak-t0) - RISE_FAST)/(RISE_SLOW - RISE_FAST))  (avg with clamp(jerkMax/JERK_MAX) if clean)
      dir = normalize(imp.x + K*rot.gamma, imp.y + K*rot.beta)
```
Constants (est): A_ON = max(6, μ+6σ) m/s², A_OFF 2, A_MAX 25 (≤19 on phones clipping at 2 g), W_ON 200 deg/s,
PULSE_MAX 250 ms, RISE_FAST 40 ms, RISE_SLOW 180 ms, JERK_MAX 600 m/s³, K .02, REFRACT 300 ms.
Optional emit-on-onset: `flickStart` at the gate so the pan reacts that frame; final strength at the peak (≤120 ms).
Rejection: ignore snap < .1 && strength < .2 (walking); tremor stays under the 6σ gate.
Calibration: 1 s idle level → g0, μ, σ. Optional "flick 3 times" → A_MAX = 1.2 × median peak.

## 4. Tilt
Low-passed gravity vs g0: tx = (g.x - g0.x)/9.81, ty = (g.y - g0.y)/9.81; dead zone .035 (~2°), full scale .35 (~20°),
rescale to -1..1, EMA τ 80 ms. Freeze tilt 250 ms after a flick onset. Verify signs on a device.

## 5. Onboarding
Start button tap → requestPermission() (if a function), audioCtx.resume(), audioSession. Denied/missing → touch.
Granted → wait 1000 ms for a devicemotion with non-null aIG; none → touch + hint "Safari/Chrome에서 열면 흔들어서 할 수
있어요"; got one → 1 s "팬을 평평하게 잡아요" calibration.
Touch mode: flick = release; strength = release speed / 2.5 px/ms; snap = 1 - (drag time - 60)/240 ms; dir = drag
vector (up = forward). Tilt = drag offset while holding.

## 6. Physical numbers
Wok toss (Ko & Hu): period 0.3 s, rotation 0.2 rad, ϕ ≈ 1.0 rad, radius .20 m, airtime ≈ .22 s, flight .17–.22 m, peak
decel ≈ 1.5 g; far-lip grains fastest (v = ωr), centre a dead zone.
Egg flip: v = √(2gh), airtime ≈ 2v/g; h .3 m → 2.4 m/s, .5 s. Half turn ω ≈ π/airtime ≈ 5–6 rad/s. Map strength → v,
snap → spin 3–8 rad/s (low snap = unflipped, too much = 1.5 turns).
Egg on oil (est): μk .05–.1, μs .15 → slides at ~8° tilt; linear damping ~1.5/s. Yolk wobble 3–5 Hz, ζ .2, squash 15%.

## 7. Haptics / audio
vibrate: Android only, after a gesture: 15 ms toss, 30 ms landing. iOS silent switch mutes Web Audio unless
navigator.audioSession.type='playback' (iOS 17+) — recommend respecting the switch + 🔇 hint (operator's call).
Latency iOS 20–50 ms, Android 50–150 ms → play sounds at flick onset.

## 8. Phone risks
1 motion events in IG/Threads in-app (iOS prompt?), 2 axis signs iOS vs Android, 3 event rate (30 Hz battery saver),
4 accel clipping at 2 g, 5 layout rotation + in-app swipe-back stealing drags, 6 forward vs sideways separable.
