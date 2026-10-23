/* game.js — flow, wok pose, HUD, scoring, result slip, attract demo (lead) */
"use strict";
(function () {
  const K = window.KFR, TAU = K.TAU, clamp = K.clamp;
  const RICE_T = 12, EGG_MAX = 9;
  const cv = document.getElementById("cv"), c = cv.getContext("2d");
  const app = document.getElementById("app"), intro = document.getElementById("intro"), resEl = document.getElementById("result");
  let W = 0, H = 0, vw = null;
  const fit = () => {
    const d = K.DPR(); W = app.clientWidth; H = app.clientHeight;
    cv.width = Math.round(W * d); cv.height = Math.round(H * d); c.setTransform(d, 0, 0, d, 0, 0);
    vw = K.view(W, H); K.Stage.reset();
  };
  new ResizeObserver(fit).observe(app); fit();

  let best = 0, bestAng = 0, taught = false;
  try { best = +localStorage.getItem("kfr-best") || 0; bestAng = +localStorage.getItem("kfr-best-angle") || 0; taught = !!localStorage.getItem("kfr-taught"); } catch (e) {}

  /* ---------- game state ---------- */
  let g = null;
  function newGame(demo) {
    return {
      demo, t: 0, phase: "rice", phaseT: 0, started: demo, rice: K.Rice.create({}), egg: null, fx: K.Fx.create(),
      pose: K.restPose(), tossAnim: null, flicks: 0, lastRiceFlick: 0, maxGap: 0, toast: null, rimFlash: 0, res: null,
      hitstop: 0, lastFlickT: -9, order: ORDERS[Math.floor(Math.random() * ORDERS.length)], words: [],
    };
  }

  /* ---------- the wok's own motion: a chef's toss (Ko & Hu: one cycle ~0.3 s; push + lift, far lip rises) ---------- */
  function startToss(f) {
    g.tossAnim = { t0: g.t, s: f.strength, snap: f.snap, du: f.dirU || 0 };
  }
  function updatePose() {
    const p = g.pose, a = g.tossAnim, tilt = K.Input.tilt;
    const eggAir = g.phase === "egg" && g.egg && g.egg.phase === "air";
    g.panU = K.lerp(g.panU || 0, eggAir ? tilt.u * 0.35 : g.phase === "egg" && g.egg && g.egg.phase !== "pan" ? (g.panU || 0) : 0, 0.2);
    let du = tilt.u * 0.03 + (g.panU || 0), dv = 0, lift = 0, lean = 0;
    if (a) {
      const dur = 0.36 - 0.08 * a.snap, k = (g.t - a.t0) / dur;
      if (k >= 1) g.tossAnim = null;
      else {
        // tiny pull back (anticipation), sharp push forward + up, settle back
        const back = k < 0.15 ? -Math.sin((k / 0.15) * Math.PI) * 0.03 : 0;
        const fwd = k >= 0.1 ? Math.sin(Math.PI * Math.min(1, (k - 0.1) / 0.9)) : 0;
        dv = (back + fwd * 0.13) * a.s;
        lift = Math.max(0, Math.sin(Math.PI * Math.min(1, k * 1.25))) * 0.06 * a.s;
        lean = Math.sin(Math.PI * k) * (0.6 + 0.4 * a.snap) * a.s;
        du += a.du * fwd * 0.06;
      }
    }
    p.du = du; p.dv = dv; p.lift = lift; p.lean = lean; p.t = g.t;
  }

  /* ---------- input ---------- */
  function onFlick(f) {
    if (!g || g.demo) return;
    if (g.phase === "result" || g.phase === "intro") return;
    flick(f);
  }
  function flick(f) {
    if (g.t - g.lastFlickT < 0.3) return;
    if (g.phase === "rice") {
      if (g.phaseT >= RICE_T && g.started) return;
      if (!g.started) { g.started = true; g.phaseT = 0; }
      g.lastFlickT = g.t;
      g.maxGap = Math.max(g.maxGap, g.phaseT - g.lastRiceFlick); g.lastRiceFlick = g.phaseT; g.flicks++;
      startToss(f);
      K.Rice.toss(g.rice, f, g.t);
      K.Fx.flare(g.fx, f.strength);
      if (f.strength >= 0.9 && f.snap > 0.4) K.Fx.oilCatch(g.fx, f.strength);
      K.Audio.toss(f.strength, f.snap);
      if (f.strength >= 0.9) K.Audio.flare();
    } else if (g.phase === "egg" && g.egg && K.Egg.inPan(g.egg)) {
      g.lastFlickT = g.t;
      startToss(f);
      K.Egg.toss(g.egg, Object.assign({}, f, { splatOK: K.Input.mode === "motion" }), g.t);
      K.Fx.flare(g.fx, f.strength * 0.6);
      K.Audio.toss(f.strength, f.snap);
    }
  }

  /* rice landing / spill events -> oil spatter, sound, a small kick */
  function drainRiceEvents() {
    const ev = g.rice.events; if (!ev || !ev.length) return;
    for (const e of ev) {
      if (e.type === "land" && e.n > 6) { K.Fx.spatter(g.fx, e.u, e.v, Math.min(10, ((e.n / 10) | 0) + 1)); K.Audio.land("rice"); g.rimFlash = Math.max(g.rimFlash, 0.5); }
      if (e.type === "spill") { K.Audio.land("spill"); }
    }
    ev.length = 0;
  }
  /* ---------- toasts: one short line after each toss, pops in ---------- */
  function toast(text, color) { g.toast = { text, color: color || "#1F2A33", t0: g.t }; }

  /* ---------- demo autopilot (attract mode behind the intro) ---------- */
  function demoDrive() {
    const t = g.phaseT;
    if (g.phase === "rice") {
      if ((t < 3.5 || t > 8.5) && t % 0.8 < 1 / 60) flick({ strength: K.rnd(0.8, 1.05), snap: K.rnd(0.4, 0.8), dirU: 0, dirV: 1 });
      K.Input.demoTilt(t >= 3.5 && t <= 8.5 ? Math.sin((t - 3.5) * 1.6) * 0.5 : 0, 0);
    } else if (g.phase === "egg" && g.egg) {
      const e = g.egg;
      if (K.Egg.inPan(e)) {
        K.Input.demoTilt(clamp(-e.u * 2.2 - (e.su || 0) * 0.8, -0.8, 0.8), clamp(-e.v * 2.2, -0.8, 0.8));
        if (e.cookBottom >= 1 && Math.abs(e.u) < 0.12) flick({ strength: K.rnd(0.85, 0.95), snap: 0.6, dirU: 0, dirV: 1 });
      } else K.Input.demoTilt(clamp((e.u || 0) / 0.35, -1, 1), 0);
    }
  }

  /* ---------- step ---------- */
  function step(dt) {
    g.t += dt;
    if (g.hitstop > 0) { g.hitstop -= dt; return; }
    g.phaseT += dt;
    if (g.demo) demoDrive();
    updatePose();
    const tilt = K.Input.tilt, ctx = { t: g.t, tilt, heat: K.heat, wokPose: g.pose };
    let contact = 0, steam = [];
    if (g.phase === "rice") {
      if (!g.started) { g.phaseT = 0; }
      else {
        K.Rice.update(g.rice, dt, ctx);
        const lt = g.rice.lastToss;
        if (lt && !lt.shown && g.t - lt.t > 0.35) { lt.shown = true; if (lt.label) toast(lt.label, lt.color); }
        drainRiceEvents();
      }
      contact = K.Rice.contactShare(g.rice);
      if (!taught && !g.demo && g.started && g.rice.golden && g.flicks >= 1) {
        taught = true; try { localStorage.setItem("kfr-taught", "1"); } catch (e) {}
        toast("지금!", "#E8481C"); g.rimFlash = 1;
      }
      steam = g.started ? K.Rice.hotspots(g.rice, 24) : [];
      if (g.started && g.phaseT >= RICE_T && (K.Rice.airborne(g.rice) === 0 || g.phaseT > RICE_T + 1)) {
        g.maxGap = Math.max(g.maxGap, RICE_T - g.lastRiceFlick);
        g.riceStats = K.Rice.stats(g.rice);
        g.phase = "egg"; g.phaseT = 0; g.egg = K.Egg.create(); K.Audio.ding();
      }
    } else if (g.phase === "egg") {
      K.Egg.update(g.egg, dt, ctx);
      contact = K.Egg.inPan(g.egg) ? 1 : 0.2;
      steam = K.Egg.hotspots(g.egg, 10);
      const o = K.Egg.outcome(g.egg);
      if (o.landedNow) {
        g.hitstop = o.burst ? 0.09 : o.caught ? 0.06 : 0; g.rimFlash = 1;
        if (o.caught) K.Fx.spatter(g.fx, o.u || 0, o.v || 0, 12);
        K.Audio.land(o.burst || o.torn || o.splat ? "egg-splat" : "egg-soft");
        try { navigator.vibrate && navigator.vibrate(30); } catch (e) {}
        if (o.caught && o.angleErr != null && !o.folded) toast(`${Math.round(180 - Math.abs(o.angleErr))}°!`, Math.abs(o.angleErr) <= 12 ? "#2BB673" : "#1F2A33");
        if (o.caught && o.flipped && Math.abs(o.angleErr || 99) <= 12 && !o.burst) K.Audio.ding();
        else if (o.caught && o.folded) toast("접혔다…", "#E8481C");
        else if (o.burst) toast("터졌다!", "#E8481C");
      }
      if ((o.done && g.phaseT > 0.3) || g.phaseT > EGG_MAX) {
        if (!o.done) K.Egg.forceStuck && K.Egg.forceStuck(g.egg);
        finish(); g.phase = "result"; g.phaseT = 0;
      }
    } else if (g.phase === "result") {
      if (g.demo && g.phaseT > 6) g = newGame(true);
    }
    const burning = steam.filter((s) => s.cook > 1.3).length / Math.max(1, steam.length);
    K.Fx.update(g.fx, dt, { flare: 0, burning, steam, wokPose: g.pose });
    K.Audio.sizzle(g.phase === "result" ? 0 : (g.started ? contact : 0.15));
    K.Audio.burnWarn(burning);
    g.contact = contact;
    g.rimFlash = Math.max(0, g.rimFlash - dt * 8);
  }

  /* ---------- scoring (DESIGN.md): rice 60 = doneness 40 + 불맛 20, egg 40 = flip 22 + yolk 12 + sear 6 ---------- */
  const GOOD_FLICKS = "12~16";
  const ORDERS = [
    { id: "half", label: "계란 반숙", win: [0.5, 0.9], fire: 1, done: 1 },
    { id: "full", label: "계란 완숙", win: [1.0, 1.4], fire: 1, done: 1 },
    { id: "fire", label: "불맛 많이", win: K.EGG_GOOD, fire: 1.5, done: 0.75 },
  ];
  const GRADES = [[95, "불맛 장인"], [85, "동네 중국집 에이스"], [70, "자취 3년 차"], [0, "엄마 부르기"]];
  function finish() {
    const st = g.riceStats || K.Rice.stats(g.rice), o = K.Egg.outcome(g.egg), ord = g.order;
    const doneness = Math.round(40 * st.good * ord.done);
    const fireCap = Math.round(20 * ord.fire), fireP = Math.min(fireCap, Math.round((g.rice.wokhei || 0) * ord.fire));
    const rice = Math.min(60, doneness + fireP);
    const lines = [];
    const lost = (v) => Math.round(v * 40 * ord.done);
    if (st.burnt > 0.02) lines.push([`탄 밥 ${Math.round(st.burnt * 100)}% · ` + (g.maxGap >= 1.5 ? `최대 ${g.maxGap.toFixed(1)}초 안 튕김` : "바닥에 붙은 밥"), -lost(st.burnt)]);
    if (st.raw > 0.02) lines.push([`덜 익은 밥 ${Math.round(st.raw * 100)}% · ` + (g.flicks > 22 ? `튕김 ${g.flicks}번, 너무 잦음 (맛있는 리듬 ${GOOD_FLICKS}번)` : "벽 쪽에 쏠림"), -lost(st.raw)]);
    if (st.spill > 0.02) lines.push([`쏟은 밥 ${Math.round(st.spill * 100)}% · 너무 세게 튕김`, -lost(st.spill)]);
    lines.sort((a, b) => a[1] - b[1]);
    if (fireP < fireCap) lines.push([`불맛 ${fireP}/${fireCap} · 최고 콤보 x${g.rice.maxCombo || 0} (노릇할 때 튕기기)`, fireP - fireCap]);
    let flip = 0, yolk = 0, sear = 0; const eggLines = [];
    const ang = o.angleErr != null ? Math.round(180 - Math.abs(o.angleErr)) : null;
    if (o.splat) eggLines.push(["계란이 화면에 붙음 · 너무 세게", -40]);
    else if (!o.caught && !o.stuck) eggLines.push(["계란 놓침 · 기울여서 웍을 밑으로", -40]);
    else if (o.stuck) { yolk = 12; eggLines.push(["계란을 안 던짐 · 바닥 탐", -28]); }
    else {
      const d = Math.abs(o.angleErr || 0);
      if (o.folded) { flip = 10; eggLines.push([`반으로 접힘 · ${ang}° 돎 (완벽 180°)`, -12]); }
      else if (o.flipped && d <= 12) flip = 22;
      else if (o.flipped) { flip = Math.round(22 - 0.25 * (d - 12)); eggLines.push([`뒤집기 ${ang}° (완벽 ±12°)`, flip - 22]); }
      else { flip = 6; eggLines.push([`안 뒤집힘 · ${o.rot > Math.PI ? "살살" : "조금 세게"}`, -16]); }
      if (o.burst) eggLines.push([`노른자 터짐 · 세기 ${(o.strength || 0).toFixed(1)} (기준 1.2)`, -12]);
      else if (o.torn) { yolk = 6; eggLines.push(["흰자 찢어짐 · 덜 익었을 때 던짐", -6]); }
      else yolk = 12;
      if (o.cookBottom >= ord.win[0] && o.cookBottom <= ord.win[1]) sear = 6;
      else eggLines.push([o.cookBottom < ord.win[0] ? `일찍 던짐 · ${ord.label}엔 덜 익음` : `늦게 던짐 · ${ord.label}엔 너무 익음`, -6]);
    }
    const egg = flip + yolk + sear, total = rice + egg;
    const perfectEgg = o.caught && o.flipped && Math.abs(o.angleErr || 99) <= 12 && !o.burst;
    let tag;
    if (o.splat) tag = "계란 화면 강타";
    else if (!o.caught && !o.stuck) tag = rice >= 55 ? "밥만 국가대표" : "계란은 바닥이 먹음";
    else if (o.stuck) tag = "계란 들러붙음";
    else if (o.burst) tag = "노른자 대참사";
    else if (total >= 100) tag = "사장님 나와 보세요";
    else if (perfectEgg && rice < 35) tag = "계란만 국가대표";
    else if (st.burnt > 0.25) tag = "화재경보기 울림";
    else if (st.spill > 0.2) tag = "주방 바닥 볶음밥";
    else if (st.raw > 0.3) tag = "김치밥 (볶음 아님)";
    else if ((g.rice.maxCombo || 0) >= 6 && total < 95) tag = "불쇼 하는 사람";
    else tag = GRADES.find(([s]) => total >= s)[1];
    const next = GRADES.slice().reverse().find(([s]) => s > total);
    const toPerfect = o.caught && !o.folded && o.angleErr != null ? Math.max(0, Math.round(Math.abs(o.angleErr) - 12)) : null;
    g.res = { rice, egg, total, tag, ang, toPerfect, next: next ? next[0] - total : 0, nextName: next ? next[1] : "",
      lines: lines.slice(0, 3).concat(eggLines.slice(0, 2)) };
  }

  /* ---------- HUD ---------- */
  function pill(x, y, w, h, fill) { c.fillStyle = fill; c.beginPath(); c.roundRect(x - w / 2, y - h / 2, w, h, h / 2); c.fill(); }
  function strokeText(t, x, y, size, ink, outline, font) {
    c.font = `${size}px ${font || '"Black Han Sans"'}`; c.textAlign = "center"; c.textBaseline = "middle";
    c.lineJoin = "round"; c.lineWidth = size * 0.22; c.strokeStyle = outline || "#fff"; c.strokeText(t, x, y);
    c.fillStyle = ink; c.fillText(t, x, y);
  }
  function hud() {
    const top = Math.max(18, H * 0.04);
    if (g.phase === "rice" && !g.demo) {
      const left = g.started ? Math.max(0, RICE_T - g.phaseT) : RICE_T, warn = left < 3 && g.started;
      const pulse = warn ? 1 + 0.12 * Math.max(0, Math.sin((left % 1) * Math.PI)) : 1;
      c.save(); c.translate(W / 2, top + 30); c.scale(pulse, pulse);
      c.shadowColor = "rgba(0,0,0,.18)"; c.shadowBlur = 10; c.shadowOffsetY = 3;
      pill(0, 0, 132, 56, warn ? "#E8481C" : "#fff"); c.shadowColor = "transparent";
      c.font = '36px "Black Han Sans"'; c.textAlign = "center"; c.textBaseline = "middle"; c.fillStyle = warn ? "#fff" : "#1F2A33";
      c.fillText(left.toFixed(1), 0, 2); c.restore();
      c.font = '16px "Jua"'; c.fillStyle = "#4b5761"; c.textAlign = "center"; c.fillText("1판 · 밥 볶기", W / 2, top + 72);
      if (!g.started) {
        // phone-flick loop until the first flick starts the clock
        const k = (g.t % 1.1) / 1.1, s = Math.sin(Math.min(1, k * 2.2) * Math.PI);
        c.save(); c.translate(W / 2, H * 0.3 - s * H * 0.05); c.rotate(-s * 0.35);
        c.fillStyle = "#1F2A33"; c.beginPath(); c.roundRect(-W * 0.06, -W * 0.11, W * 0.12, W * 0.22, W * 0.02); c.fill();
        c.fillStyle = "#E8481C"; c.beginPath(); c.roundRect(-W * 0.047, -W * 0.093, W * 0.094, W * 0.17, W * 0.012); c.fill();
        c.restore();
        strokeText(K.Input.mode === "motion" ? "손목으로 휙!" : "위로 휙 쓸어 올려!", W / 2, H * 0.205, 30, "#1F2A33");
      }
    } else if (g.phase === "egg" && !g.demo) {
      c.font = '16px "Jua"'; c.fillStyle = "#4b5761"; c.textAlign = "center"; c.fillText(`2판 · 계란 뒤집기 · 주문: ${g.order.label}`, W / 2, top + 10);
      const e = g.egg, o = K.Egg.outcome(e);
      let msg = "계란 익는 중…", col = "#1F2A33";
      if (K.Egg.inPan(e)) {
        if (e.cookBottom >= 0.6) { msg = e.cookBottom > 1.4 ? "바닥 탄다!" : Math.abs(e.su || 0) + Math.abs(e.sv || 0) > 0.25 ? "가운데로 모아!" : "지금 던져!"; col = e.cookBottom > 1.4 ? "#E8481C" : "#1F2A33"; }
      } else if (e.phase === "air" || e.phase === "splatFly") msg = (o.strength || 0) > 1.1 ? "어어…!" : "기울여서 받아!";
      else if (!o.done && e.phase !== "splat") msg = o.caught ? (o.flipped && !o.burst ? "좋아!" : o.folded ? "접혔다…" : "앗…") : "아…";
      else msg = o.caught ? (o.flipped && !o.burst ? "좋아!" : "앗…") : "아…";
      const k = clamp((g.t % 100) * 4, 0, 1);
      strokeText(msg, W / 2, top + 48, 38, col);
    }
    if (g.demo) { /* nothing: intro layer covers the top */ }
    // per-toss toast
    if (g.toast) {
      const k = (g.t - g.toast.t0) / 0.9;
      if (k >= 1) g.toast = null;
      else {
        const s = k < 0.28 ? K.easeOutBack(k / 0.28) : 1, a = k > 0.75 ? 1 - (k - 0.75) / 0.25 : 1;
        c.save(); c.globalAlpha = a; c.translate(W / 2, H * 0.24); c.rotate(-0.035); c.scale(s, s);
        strokeText(g.toast.text, 0, 0, 32, g.toast.color); c.restore();
      }
    }
    // motion events never arrived (in-app browser / desktop): say once how to get the real thing
    if (!g.demo && K.Input.mode === "touch" && K.Input.hint && g.phase === "rice" && !g.started) {
      c.font = '15px "Gowun Dodum"'; c.textAlign = "center"; c.fillStyle = "#4b5761"; c.fillText(K.Input.hint, W / 2, H * 0.97);
    }
    // spirit level (bottom-right): the bubble drifts to the high side
    if (g.phase !== "result" && !g.demo) {
      const lx = W * 0.86, ly = H * 0.9, lr = W * 0.07, t = K.Input.tilt;
      c.fillStyle = "rgba(31,42,51,.72)"; c.beginPath(); c.arc(lx, ly, lr, 0, TAU); c.fill();
      c.strokeStyle = "rgba(255,255,255,.35)"; c.lineWidth = 1; c.beginPath(); c.arc(lx, ly, lr * 0.35, 0, TAU); c.stroke();
      c.fillStyle = "#a3e635"; c.beginPath(); c.arc(lx - t.u * lr * 0.6, ly + t.v * lr * 0.6, lr * 0.28, 0, TAU); c.fill();
    }
  }

  /* ---------- render ---------- */
  function render() {
    const fx = g.fx, sh = fx.shake || { x: 0, y: 0 };
    c.save(); c.translate(sh.x, sh.y);
    K.Stage.drawCounter(c, vw);
    const fire = g.phase !== "result";
    if (fire) { K.Stage.drawUnderGlow(c, vw, fx.flareLevel || 0); K.Fx.drawBehind(c, fx, vw, g.pose); }
    if (g.phase !== "egg") K.Rice.drawSpilled && K.Rice.drawSpilled(c, g.rice, vw, g.pose);
    K.Stage.drawWok(c, vw, g.pose, g.contact || 0, fire);
    if (fire && K.Fx.drawRim) K.Fx.drawRim(c, fx, vw, g.pose);   // far-rim flare sits under the food
    if (g.phase === "rice" || g.phase === "result") K.Rice.draw(c, g.rice, vw, g.pose);
    if (g.phase === "egg") K.Egg.draw(c, g.egg, vw, g.pose);
    K.Stage.flashRim(c, vw, g.pose, g.rimFlash);
    K.Fx.drawFront(c, fx, vw, g.pose);
    if (fx.haze > 0.01) { c.fillStyle = `rgba(52,48,46,${Math.min(0.45, fx.haze)})`; c.fillRect(-10, -10, W + 20, H + 20); }
    c.restore();
    hud();
  }

  /* ---------- result slip ---------- */
  function showResult() {
    const r = g.res, record = r.total > best && best > 0;
    if (r.total > best) { best = r.total; try { localStorage.setItem("kfr-best", best); } catch (e) {} }
    const angRecord = r.ang != null && r.ang > bestAng && bestAng > 0;
    if (r.ang != null && r.ang > bestAng && r.ang <= 180) { bestAng = r.ang; try { localStorage.setItem("kfr-best-angle", bestAng); } catch (e) {} }
    const near = r.toPerfect ? `완벽 뒤집기까지 ${r.toPerfect}°` : r.next ? `「${r.nextName}」까지 ${r.next}점` : "";
    const lines = r.lines.map(([k, v]) => `<li><span>${k}</span><span>${v}</span></li>`).join("");
    resEl.innerHTML = `<div class="slip" id="slip"><div class="shop">D:OT 반점</div><div class="meta">주문 #002 · 김치볶음밥 1 · ${g.order.label}</div>${record ? '<div class="stamp rec">신기록</div>' : ""}<canvas id="bowl"></canvas>
      <div class="total"><span class="stamp" id="stamp" style="visibility:hidden">${r.tag}</span><span class="big"><span id="score">0</span><small>점</small></span></div>
      ${near ? `<div class="near">${near}</div>` : ""}
      <ul><li><span>밥 볶기 ${r.rice}/60 · 계란 ${r.egg}/40</span><span>최고 ${best}</span></li>
      ${r.ang != null ? `<li><span>뒤집기 ${r.ang}°${angRecord ? " · 신기록" : ""}</span><span>최고 ${bestAng}°</span></li>` : ""}${lines}
</ul>
      <div class="formula">밥 = 맛있게 익은 비율 × 40 + 불맛 20 (노릇할 때 튕긴 만큼) · 계란 = 뒤집기 22 + 노른자 12 + 굽기 6</div></div>
      <div class="row"><button class="btn" id="again" type="button">한 번 더</button><button class="btn ghost" id="share" type="button">자랑하기</button></div><div class="hint">폰을 휙 튕겨도 다시 시작</div>`;
    resEl.hidden = false;
    const bowl = document.getElementById("bowl"), bw = bowl.clientWidth || 300, bh = bw * 0.75;
    const [bc, bx] = [bowl, bowl.getContext("2d")], d = K.DPR(); bc.width = bw * d; bc.height = bh * d; bx.scale(d, d);
    drawBowl(bx, bw, bh);
    // count up the score, then slam the stamp
    const el = document.getElementById("score"), t0 = performance.now();
    const tick = (now) => {
      const k = Math.min(1, (now - t0) / 700); el.textContent = Math.round(r.total * K.smooth(k));
      if (k < 1) requestAnimationFrame(tick);
      else { const s = document.getElementById("stamp"); s.style.visibility = "visible"; s.classList.add("pop"); K.Audio.stamp(); }
    };
    requestAnimationFrame(tick);
    document.getElementById("again").onclick = (e) => { e.stopPropagation(); restart(); };
    document.getElementById("share").onclick = (e) => { e.stopPropagation(); share(); };
  }

  /* ---------- share: the order slip as a 1080x1350 picture ---------- */
  function slipImage() {
    const W2 = 1080, H2 = 1350, cv2 = document.createElement("canvas"); cv2.width = W2; cv2.height = H2;
    const x = cv2.getContext("2d"), r = g.res;
    const bg = x.createLinearGradient(0, 0, W2, H2); bg.addColorStop(0, "#eef1f3"); bg.addColorStop(1, "#c9d1d8");
    x.fillStyle = bg; x.fillRect(0, 0, W2, H2);
    x.save(); x.translate(W2 / 2, H2 / 2); x.rotate(-0.02);
    const sw = 860, sh = 1150; x.shadowColor = "rgba(30,40,50,.3)"; x.shadowBlur = 40; x.shadowOffsetY = 18;
    x.fillStyle = "#fbf6ea"; x.fillRect(-sw / 2, -sh / 2, sw, sh); x.shadowColor = "transparent";
    x.textAlign = "center"; x.fillStyle = "#2a1a14";
    x.font = '64px "Song Myung"'; x.fillText("D:OT 반점", 0, -sh / 2 + 100);
    x.font = '26px "Nanum Gothic Coding"'; x.fillStyle = "#6b5d4f"; x.fillText(`주문 #002 · 김치볶음밥 1 · ${g.order.label}`, 0, -sh / 2 + 148);
    const bw = 760, bh = 570, [bc] = [document.createElement("canvas")]; bc.width = bw; bc.height = bh;
    const bx = bc.getContext("2d"); drawBowl(bx, bw, bh); x.drawImage(bc, -bw / 2, -sh / 2 + 170);
    x.textAlign = "right"; x.fillStyle = "#2a1a14"; x.font = 'bold 150px "Nanum Gothic Coding"'; x.fillText(String(r.total), sw / 2 - 150, sh / 2 - 190);
    x.font = '48px "Nanum Gothic Coding"'; x.fillText("점", sw / 2 - 90, sh / 2 - 190);
    x.save(); x.translate(-sw / 2 + 230, sh / 2 - 240); x.rotate(-0.12);
    x.font = '58px "Song Myung"'; const tw = x.measureText(r.tag).width + 50;
    x.strokeStyle = "#c81e1e"; x.lineWidth = 8; x.strokeRect(-tw / 2, -50, tw, 90); x.fillStyle = "#c81e1e"; x.textAlign = "center"; x.fillText(r.tag, 0, 18); x.restore();
    x.textAlign = "center"; x.fillStyle = "#6b5d4f"; x.font = '30px "Nanum Gothic Coding"';
    x.fillText(`밥 ${r.rice}/60 · 계란 ${r.egg}/40` + (r.ang != null ? ` · 뒤집기 ${r.ang}°` : ""), 0, sh / 2 - 110);
    x.fillText("너도 볶아 봐 → D:OT lab", 0, sh / 2 - 60);
    x.restore();
    return cv2;
  }
  function share() {
    const cv2 = slipImage();
    cv2.toBlob(async (blob) => {
      try {
        const file = new File([blob], "kimchi-fried-rice.png", { type: "image/png" });
        if (navigator.canShare && navigator.canShare({ files: [file] })) {
          await navigator.share({ files: [file], text: `김치볶음밥 한 그릇 ${g.res.total}점 「${g.res.tag}」 ${location.href.split("?")[0]}` });
          return;
        }
      } catch (e) { if (e && e.name === "AbortError") return; }
      // in-app browsers: show the picture to long-press and save
      const url = URL.createObjectURL(blob), ov = document.createElement("div");
      ov.className = "layer"; ov.style.background = "rgba(15,18,21,.92)"; ov.style.zIndex = 5;
      ov.innerHTML = `<img alt="결과 주문서" style="max-width:100%;max-height:72%;border-radius:8px" src="${url}"><p style="color:#fff;margin:0;font-size:18px">이미지를 길게 눌러 저장하세요</p><button class="btn" type="button">닫기</button>`;
      ov.querySelector("button").onclick = (e) => { e.stopPropagation(); ov.remove(); URL.revokeObjectURL(url); };
      app.appendChild(ov);
    }, "image/png");
  }
  function drawBowl(c2, w, h) {
    const ct = Math.cos(K.THETA), cx = w / 2, cy = h * 0.55, r = w * 0.36;
    c2.fillStyle = "#e6e1d6"; c2.beginPath(); c2.moveTo(cx - r, cy); c2.bezierCurveTo(cx - r * 0.9, cy + r * 0.95, cx + r * 0.9, cy + r * 0.95, cx + r, cy); c2.closePath(); c2.fill();
    c2.strokeStyle = "#2563eb"; c2.lineWidth = 2.5; c2.beginPath(); c2.moveTo(cx - r * 0.93, cy + r * 0.18); c2.bezierCurveTo(cx - r * 0.7, cy + r * 0.62, cx + r * 0.7, cy + r * 0.62, cx + r * 0.93, cy + r * 0.18); c2.stroke();
    c2.fillStyle = "#fffdf8"; c2.beginPath(); c2.ellipse(cx, cy, r, r * ct, 0, 0, TAU); c2.fill();
    const top = K.Rice.drawBowl(c2, g.rice, cx, cy, r, ct);
    const o = K.Egg.outcome(g.egg);
    if (!o.caught && !o.stuck) {
      c2.font = '13px "Gowun Dodum"'; c2.textAlign = "center"; const t = "(계란 없음)", tw = c2.measureText(t).width + 16;
      c2.fillStyle = "rgba(251,246,234,.92)"; c2.beginPath(); c2.roundRect(cx - tw / 2, (top || cy - r * 0.4) - 16, tw, 24, 12); c2.fill();
      c2.fillStyle = "#6b5d4f"; c2.fillText(t, cx, (top || cy - r * 0.4) + 1);
    } else K.Egg.drawOnRice(c2, g.egg, cx, top || cy - r * 0.4, r * 0.42, ct);
  }

  function restart() {
    resEl.hidden = true; g = newGame(false); K.Input.armAfterRelease && K.Input.armAfterRelease();
  }

  /* ---------- boot ---------- */
  let last = performance.now(), shown = false;
  function frame(now) {
    const dt = Math.min(0.033, (now - last) / 1000); last = now;
    step(dt);
    if (g.phase === "result" && !shown && !g.demo) { shown = true; showResult(); }
    if (g.phase !== "result") shown = false;
    render();
    requestAnimationFrame(frame);
  }
  function begin(useMotion) {
    K.Audio.unlock();
    const go = () => { intro.hidden = true; g = newGame(false); };
    if (useMotion) K.Input.requestPermission().then(go, go); else { K.Input.useTouch(); go(); }
  }
  document.getElementById("go").onclick = () => begin(true);
  document.getElementById("touchMode").onclick = () => begin(false);
  resEl.addEventListener("click", (e) => { if (e.target === resEl) {} });
  K.Input.start(onFlick, app);
  g = newGame(true);
  // a flick on the result slip restarts
  K.Input.onResultFlick = () => { if (g && g.phase === "result" && !g.demo) restart(); };
  (document.fonts ? document.fonts.ready : Promise.resolve()).then(() => requestAnimationFrame(frame));
  K.Game = { get state() { return g; }, flick, newGame: (d) => (g = newGame(d)) };
})();
