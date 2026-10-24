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
        A.out.connect(comp); comp.connect(ac.destination);
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
      b.g.gain.setTargetAtTime(0.42 * Math.pow(l, 1.3), now(), tau);
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
        tone({ f0: 190, f1: 85, dur: 0.13, gain: 0.35 });
        burst({ type: "lowpass", f0: 1000, f1: 280, dur: 0.12, gain: 0.3 });
        burst({ buf: A.sizz, type: "highpass", f0: 3000, dur: 0.35, gain: 0.25, at: 0.03, attack: 0.03 });
      } else if (kind === "egg-splat") {
        burst({ type: "lowpass", f0: 2000, f1: 240, q: 2, dur: 0.24, gain: 0.55, attack: 0.002 });
        tone({ f0: 120, f1: 55, dur: 0.16, gain: 0.4 });
        for (let i = 0; i < 3; i++) tone({ f0: rnd(650, 950), f1: rnd(260, 340), dur: 0.05, gain: 0.1, at: 0.03 + i * rnd(0.03, 0.06) });
      } else if (kind === "spill") {
        for (let i = 0; i < 9; i++) burst({ f0: rnd(2800, 5200), q: 5, dur: 0.025, gain: rnd(0.12, 0.28), at: rnd(0, 0.45), attack: 0.001 });
      } else { // rice: a crackling burst of contact
        burst({ buf: A.crackle, type: "highpass", f0: 2200, f1: 3800, dur: 0.4, gain: 0.9, rate: rnd(1.1, 1.4), attack: 0.003 });
        burst({ buf: A.sizz, type: "highpass", f0: 3500, dur: 0.3, gain: 0.3, attack: 0.01 });
        tone({ f0: 150, f1: 75, dur: 0.07, gain: 0.15 });
      }
    }),
    flare: safe(() => { // gas whoomph
      burst({ type: "lowpass", f0: 160, f1: 1100, q: 1.5, dur: 0.5, gain: 0.6, attack: 0.05 });
      tone({ f0: 75, f1: 42, dur: 0.45, gain: 0.3, attack: 0.03 });
    }),
    ding: safe(() => { // kitchen-timer bell: inharmonic partials
      [[1, 0.28], [2, 0.1], [2.76, 0.08], [5.4, 0.03]].forEach(([m, g]) => tone({ f0: 1320 * m, dur: 1.4 / Math.sqrt(m), gain: g }));
    }),
    stamp: safe(() => {
      tone({ f0: 115, f1: 45, dur: 0.22, gain: 0.6 });
      burst({ type: "lowpass", f0: 1400, f1: 200, dur: 0.08, gain: 0.5, attack: 0.001 });
    }),
    pops: safe(() => { // dry crackle: the oil under the rice is hot again
      for (let i = 0; i < 5; i++) burst({ f0: rnd(2600, 4800), q: 6, dur: 0.02, gain: rnd(0.18, 0.32), at: i * rnd(0.04, 0.09), attack: 0.001 });
    }),
    burnWarn: safe((level) => {
      const l = clamp(+level || 0, 0, 1), b = A.warn;
      if (Math.abs(l - b.level) < 0.01) return;
      b.level = l;
      b.g.gain.setTargetAtTime(0.5 * l, now(), 0.25);
    }),
    mute: (m) => {
      A.muted = !!m;
      try { if (A.ctx) A.out.gain.setTargetAtTime(A.muted ? 0 : 0.9, now(), 0.03); } catch (e) {}
    },
  };
})();
