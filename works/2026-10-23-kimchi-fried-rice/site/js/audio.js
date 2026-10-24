/* audio.js — procedural Web Audio (sizzle bed, toss, landings, flare, ding, stamp); never breaks the game */
"use strict";
(function () {
  const K = (window.KFR = window.KFR || {});
  const clamp = (v, a, b) => (v < a ? a : v > b ? b : v), rnd = (a, b) => a + Math.random() * (b - a);
  /* ================= KFR.Audio: Web Audio, all procedural ================= */
  const A = { ctx: null, out: null, muted: false, noise: null, crackle: null, siz: null, warn: null };

  function makeBuffers(ac) {
    const sr = ac.sampleRate, mk = (sec) => ac.createBuffer(1, Math.floor(sr * sec), sr);
    A.noise = mk(2); const n = A.noise.getChannelData(0);
    for (let i = 0; i < n.length; i++) n[i] = Math.random() * 2 - 1;
    // sizzle bed: hiss with a slow random swell plus dense tiny pops
    A.sizz = mk(3); const z = A.sizz.getChannelData(0);
    let env = 0.5, tgt = 0.5, pop = 0, popA = 0;
    for (let i = 0; i < z.length; i++) {
      if (i % 600 === 0) tgt = rnd(0.25, 0.8);
      env += (tgt - env) * 0.002;
      if (Math.random() < 0.0016) { pop = 1; popA = rnd(0.4, 1); }
      pop *= 0.93;
      z[i] = (Math.random() * 2 - 1) * (env * 0.55 + pop * popA * 1.4);
    }
    // crackle: sparse sharp clicks only (burn warning, rice landing)
    A.crackle = mk(2); const k = A.crackle.getChannelData(0);
    let cl = 0, clA = 0;
    for (let i = 0; i < k.length; i++) {
      if (Math.random() < 0.0009) { cl = 1; clA = rnd(0.3, 1); }
      cl *= 0.9;
      k[i] = (Math.random() * 2 - 1) * cl * clA;
    }
  }

  /* a looping bed: src -> highpass -> lowpass -> gain(0) -> out */
  function bed(buf, hp, lp, rate) {
    const ac = A.ctx, src = ac.createBufferSource(), h = ac.createBiquadFilter(), l = ac.createBiquadFilter(), g = ac.createGain();
    src.buffer = buf; src.loop = true; src.playbackRate.value = rate;
    h.type = "highpass"; h.frequency.value = hp; l.type = "lowpass"; l.frequency.value = lp; g.gain.value = 0;
    src.connect(h); h.connect(l); l.connect(g); g.connect(A.out); src.start();
    return { g, l, level: 0 };
  }

  function unlock() {
    try {
      try { if (navigator.audioSession) navigator.audioSession.type = "playback"; } catch (e) {}
      if (!A.ctx) {
        const AC = window.AudioContext || window.webkitAudioContext;
        if (!AC) return;
        const ac = (A.ctx = new AC());
        const comp = ac.createDynamicsCompressor();
        comp.threshold.value = -14; comp.ratio.value = 4;
        A.out = ac.createGain(); A.out.gain.value = A.muted ? 0 : 0.9;
        A.lp = ac.createBiquadFilter(); A.lp.type = "lowpass"; A.lp.frequency.value = 20000; // the slow-mo muffles everything
        A.out.connect(A.lp); A.lp.connect(comp); comp.connect(ac.destination);
        makeBuffers(ac);
        A.siz = bed(A.sizz, 1500, 6000, 1);
        A.warn = bed(A.crackle, 900, 4500, 0.8);
        // iOS: play something inside the gesture
        const s = ac.createBufferSource(); s.buffer = ac.createBuffer(1, 1, ac.sampleRate); s.connect(ac.destination); s.start();
      }
      if (A.ctx.state !== "running") A.ctx.resume();
    } catch (e) {}
  }

  /* everything below no-ops before unlock and swallows errors: sound must never break the game */
  const safe = (fn) => function () { if (!A.ctx) return; try { fn.apply(null, arguments); } catch (e) {} };
  const now = () => A.ctx.currentTime;

  /* filtered noise burst with a frequency sweep and an attack/decay envelope */
  function burst(o) {
    const ac = A.ctx, t = now() + (o.at || 0), d = o.dur;
    const src = ac.createBufferSource(), f = ac.createBiquadFilter(), g = ac.createGain();
    src.buffer = o.buf || A.noise; src.playbackRate.value = o.rate || 1;
    f.type = o.type || "bandpass"; f.Q.value = o.q || 1;
    f.frequency.setValueAtTime(o.f0, t); f.frequency.exponentialRampToValueAtTime(o.f1 || o.f0, t + d);
    const at = o.attack || 0.004;
    g.gain.setValueAtTime(0.0001, t); g.gain.exponentialRampToValueAtTime(o.gain, t + at); g.gain.exponentialRampToValueAtTime(0.0001, t + d);
    src.connect(f); f.connect(g); g.connect(A.out);
    src.start(t, Math.random() * (src.buffer.duration - d - 0.1)); src.stop(t + d + 0.05);
  }
  function tone(o) {
    const ac = A.ctx, t = now() + (o.at || 0), d = o.dur, osc = ac.createOscillator(), g = ac.createGain();
    osc.type = o.type || "sine";
    osc.frequency.setValueAtTime(o.f0, t); osc.frequency.exponentialRampToValueAtTime(o.f1 || o.f0, t + d);
    g.gain.setValueAtTime(0.0001, t); g.gain.exponentialRampToValueAtTime(o.gain, t + (o.attack || 0.003)); g.gain.exponentialRampToValueAtTime(0.0001, t + d);
    osc.connect(g); g.connect(A.out); osc.start(t); osc.stop(t + d + 0.05);
  }

  K.Audio = {
    unlock,
    suspend: () => { try { if (A.ctx && A.ctx.state === "running") A.ctx.suspend(); } catch (e) {} },
    resume: () => { try { if (A.ctx && A.ctx.state !== "running") A.ctx.resume(); } catch (e) {} },
    /* main heat cue: louder and brighter with food on the metal; drops fast when it all goes airborne */
    sizzle: safe((level) => {
      const l = clamp(+level || 0, 0, 1), b = A.siz;
      if (Math.abs(l - b.level) < 0.01) return;
      const tau = l < b.level ? 0.035 : 0.08;
      b.level = l;
      b.g.gain.setTargetAtTime(0.42 * Math.pow(l, 1.3) * (A.dark ? 0.75 : 1), now(), tau); // dark: the room goes a little quieter
      b.l.frequency.setTargetAtTime(2200 + 7500 * l, now(), tau);
    }),
    toss: safe((strength, snap) => {
      const s = clamp((strength || 1) / 1.6, 0.15, 1), p = clamp(snap || 0, 0, 1);
      burst({ f0: 320, f1: 1500 + 900 * s, q: 1.1, dur: 0.2 + 0.12 * s, gain: 0.4 * s, attack: 0.07 }); // whoosh
      burst({ f0: 2400, f1: 3900, q: 9, dur: 0.08 + 0.07 * p, gain: 0.16 + 0.22 * p, at: 0.01 }); // scrape on the burner ring
      tone({ f0: 1870, dur: 0.3, gain: 0.03 + 0.05 * p, type: "triangle", at: 0.01 }); // steel ring
      tone({ f0: 2950, dur: 0.18, gain: 0.02 + 0.03 * p, type: "sine", at: 0.01 });
    }),
    land: safe((kind) => {
      if (kind === "egg-soft") {
        tone({ f0: 420, f1: 180, dur: 0.13, gain: 0.3, type: "triangle" }); // phone speakers drop below ~300 Hz
        burst({ type: "lowpass", f0: 1000, f1: 280, dur: 0.12, gain: 0.3 });
        burst({ buf: A.sizz, type: "highpass", f0: 3000, dur: 0.35, gain: 0.25, at: 0.03, attack: 0.03 });
      } else if (kind === "egg-splat") {
        burst({ type: "lowpass", f0: 2000, f1: 240, q: 2, dur: 0.24, gain: 0.55, attack: 0.002 });
        tone({ f0: 280, f1: 120, dur: 0.16, gain: 0.4, type: "triangle" });
        for (let i = 0; i < 3; i++) tone({ f0: rnd(650, 950), f1: rnd(260, 340), dur: 0.05, gain: 0.1, at: 0.03 + i * rnd(0.03, 0.06) });
      } else if (kind === "spill") {
        for (let i = 0; i < 9; i++) burst({ f0: rnd(2800, 5200), q: 5, dur: 0.025, gain: rnd(0.12, 0.28), at: rnd(0, 0.45), attack: 0.001 });
        burst({ type: "lowpass", f0: 1200, f1: 300, dur: 0.1, gain: 0.45, attack: 0.002 }); tone({ f0: 380, f1: 170, dur: 0.07, gain: 0.2, type: "triangle" }); // 투둑: it has weight
      } else { // rice: a crackling burst of contact
        burst({ buf: A.crackle, type: "highpass", f0: 2200, f1: 3800, dur: 0.4, gain: 0.9, rate: rnd(1.1, 1.4), attack: 0.003 });
        burst({ buf: A.sizz, type: "highpass", f0: 3500, dur: 0.3, gain: 0.3, attack: 0.01 });
        tone({ f0: 320, f1: 150, dur: 0.08, gain: 0.25, type: "triangle" });
      }
    }),
    flare: safe(() => { // gas whoomph
      burst({ type: "bandpass", f0: 300, f1: 2500, q: 0.7, dur: 0.6, gain: 0.6, attack: 0.05 });
    }),
    ding: safe(() => { // kitchen-timer bell: inharmonic partials
      [[1, 0.28], [2, 0.1], [2.76, 0.08], [5.4, 0.03]].forEach(([m, g]) => tone({ f0: 1320 * m, dur: 1.4 / Math.sqrt(m), gain: g }));
    }),
    stamp: safe(() => {
      tone({ f0: 260, f1: 110, dur: 0.22, gain: 0.5, type: "triangle" }); burst({ f0: 2500, f1: 300, dur: 0.1, gain: 0.3, attack: 0.001 });
      burst({ type: "lowpass", f0: 1400, f1: 200, dur: 0.08, gain: 0.5, attack: 0.001 });
    }),
    // the beat: one dry 탁 when the gold band opens; it ducks the sizzle so it cuts through (next sizzle() restores it)
    tick: safe(() => { burst({ f0: 1100, q: 4, dur: 0.035, gain: 0.7, attack: 0.001 }); burst({ f0: 3200, q: 6, dur: 0.02, gain: 0.35, at: 0.012, attack: 0.001 }); A.siz.g.gain.setTargetAtTime(0.1, now(), 0.01); A.siz.level = -1; }),
    perfect: safe((combo) => tone({ f0: 660 * Math.pow(2, (Math.min(combo || 1, 8) * 2) / 12), dur: 0.12, gain: 0.14, type: "triangle" })), // one step up per combo
    comboBreak: safe(() => tone({ f0: 330, f1: 220, dur: 0.15, gain: 0.16, type: "triangle" })),
    fever: safe(() => { burst({ type: "bandpass", f0: 250, f1: 2800, q: 0.7, dur: 0.9, gain: 0.75, attack: 0.08 }); burst({ type: "lowpass", f0: 700, dur: 3.5, gain: 0.18, attack: 0.2 }); }),
    slow: safe((on) => {
      A.lp.frequency.setTargetAtTime(on ? 900 : 20000, now(), on ? 0.05 : 0.06);
      if (on) burst({ f0: 200, f1: 900, q: 0.9, dur: 0.9, gain: 0.35, attack: 0.25, at: 0.1 }); // a long slow whoosh
      else { tone({ f0: 220, f1: 90, dur: 0.3, gain: 0.5, type: "triangle" }); }
    }),
    darkFlag: (d) => { A.dark = !!d; if (A.siz) A.siz.level = -1; },
    buzz: safe(() => { tone({ f0: 120, dur: 0.35, gain: 0.05, type: "sawtooth" }); burst({ f0: 3200, q: 8, dur: 0.015, gain: 0.25, at: 0.05 }); burst({ f0: 3200, q: 8, dur: 0.015, gain: 0.2, at: 0.17 }); }),
    blackout: safe((on) => {
      A.dark = !!on; if (A.siz) A.siz.level = -1;
      if (on) { tone({ f0: 300, f1: 110, dur: 0.14, gain: 0.6, type: "triangle" }); burst({ type: "lowpass", f0: 1800, f1: 200, dur: 0.06, gain: 0.6, attack: 0.001 }); tone({ f0: 240, f1: 150, dur: 0.6, gain: 0.06, type: "sawtooth", at: 0.05 }); }
      else { [0, 0.07, 0.16].forEach((x) => burst({ f0: 3200, q: 8, dur: 0.015, gain: 0.3, at: x })); tone({ f0: 150, f1: 240, dur: 0.4, gain: 0.05, type: "sawtooth", at: 0.2 }); }
    }),
    pops: safe(() => { // dry crackle: the oil under the rice is hot again
      for (let i = 0; i < 6; i++) burst({ f0: rnd(2200, 4200), q: 5, dur: 0.03, gain: rnd(0.4, 0.6), at: i * rnd(0.05, 0.1), attack: 0.001 });
    }),
    burnWarn: safe((level) => {
      const l = clamp(+level || 0, 0, 1), b = A.warn;
      if (Math.abs(l - b.level) < 0.01) return;
      b.level = l;
      b.g.gain.setTargetAtTime(0.5 * l, now(), 0.25);
    }),
    isMuted: () => !!A.muted,
    mute: (m) => {
      A.muted = !!m;
      try { if (A.ctx) A.out.gain.setTargetAtTime(A.muted ? 0 : 0.9, now(), 0.03); } catch (e) {}
    },
  };
})();
