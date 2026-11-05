// Synthesized sound effects (Web Audio only, no assets). Works with AudioContext and OfflineAudioContext.
export function makeSfx(ac) {
  const out = ac.destination;
  const master = ac.createGain();
  const comp = ac.createDynamicsCompressor();
  comp.threshold.value = -14; comp.knee.value = 10; comp.ratio.value = 12;
  comp.attack.value = 0.002; comp.release.value = 0.25;
  const limit = ac.createGain(); // fixed trim after compressor to stay under 1.0
  limit.gain.value = 0.85;
  master.connect(comp); comp.connect(limit); limit.connect(out);
  master.gain.value = 0.9;

  // duck bus: everything except the ring goes through it (gain duck + "muffled world" lowpass)
  const duck = ac.createGain();
  const duckLP = ac.createBiquadFilter();
  duckLP.type = 'lowpass'; duckLP.frequency.value = ac.sampleRate / 2 - 100; duckLP.Q.value = 0.5;
  duck.connect(duckLP); duckLP.connect(master);
  const OPEN = duckLP.frequency.value;

  // shared noise buffer (2 s, white)
  const sr = ac.sampleRate;
  const noiseBuf = ac.createBuffer(1, sr * 2, sr);
  const nd = noiseBuf.getChannelData(0);
  for (let i = 0; i < nd.length; i++) nd[i] = Math.random() * 2 - 1;

  const rnd = (a, b) => a + Math.random() * (b - a);
  const now = () => ac.currentTime;

  function done(src, ...nodes) {
    src.onended = () => { try { src.disconnect(); } catch (e) {} nodes.forEach(n => { try { n.disconnect(); } catch (e) {} }); };
  }

  // noise burst through a filter and an exp envelope
  function noise(t, dur, { type = 'bandpass', freq = 1000, q = 1, gain = 1, attack = 0.002, to = duck, sweep = null }) {
    const s = ac.createBufferSource(); s.buffer = noiseBuf;
    const f = ac.createBiquadFilter(); f.type = type; f.frequency.setValueAtTime(freq, t); f.Q.value = q;
    if (sweep) f.frequency.exponentialRampToValueAtTime(Math.max(20, sweep), t + dur);
    const g = ac.createGain();
    g.gain.setValueAtTime(0.0001, t);
    g.gain.exponentialRampToValueAtTime(gain, t + attack);
    g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
    s.connect(f); f.connect(g); g.connect(to);
    s.start(t, rnd(0, 0.9), dur + 0.05); s.stop(t + dur + 0.05);
    done(s, f, g);
  }

  function tone(t, dur, { type = 'sine', freq = 440, to_f = null, gain = 0.3, attack = 0.005, to = duck, detune = 0 }) {
    const o = ac.createOscillator(); o.type = type; o.detune.value = detune;
    o.frequency.setValueAtTime(freq, t);
    if (to_f) o.frequency.exponentialRampToValueAtTime(to_f, t + dur);
    const g = ac.createGain();
    g.gain.setValueAtTime(0.0001, t);
    g.gain.exponentialRampToValueAtTime(gain, t + attack);
    g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
    o.connect(g); g.connect(to);
    o.start(t); o.stop(t + dur + 0.02);
    done(o, g);
  }

  function tick(urgent) {
    const t = now();
    const f = (urgent ? 1900 : 1100) * rnd(0.97, 1.03);
    tone(t, 0.05, { type: 'square', freq: f, gain: urgent ? 0.18 : 0.12, attack: 0.001 });
    noise(t, 0.03, { type: 'highpass', freq: urgent ? 4500 : 3000, gain: 0.15, attack: 0.001 });
  }

  function clack(t, lo) {
    noise(t, 0.06, { freq: rnd(2200, 3200), q: 2, gain: 0.5, attack: 0.001 });
    tone(t, 0.07, { type: 'triangle', freq: lo * rnd(0.95, 1.05), to_f: lo * 0.5, gain: 0.35, attack: 0.001 });
  }
  function cock() {
    const t = now();
    clack(t, 520);
    noise(t + 0.12, 0.12, { type: 'bandpass', freq: 900, q: 3, gain: 0.12, sweep: 1600 }); // slide
    clack(t + rnd(0.26, 0.3), 380);
  }
  function halfRack() { clack(now(), 520); }
  function creak() { // glove / fabric
    // rendered peaks were 0.04 (creak) and 0.02 (scuff): inaudible on a phone speaker, which also drops most of 300 Hz
    noise(now(), 0.15, { type: 'bandpass', freq: 1800 * rnd(0.95, 1.05), q: 6, gain: 1.0, attack: 0.04, sweep: 1500 });
  }
  function scuff() { // boot on floor: a low thud body plus the grit a phone speaker can actually play
    const t = now();
    noise(t, 0.14, { type: 'lowpass', freq: 700, q: 0.7, gain: 0.9, attack: 0.012 });
    noise(t + 0.01, 0.1, { type: 'bandpass', freq: 2400, q: 1.5, gain: 0.18, attack: 0.01 });
  }

  function gunshot() {
    const t = now();
    noise(t, 0.04, { type: 'highpass', freq: 2500, gain: 0.6, attack: 0.0005 }); // crack (kept lower: flat pop on phone speakers)
    noise(t, 0.25, { type: 'lowpass', freq: 3500, q: 0.7, gain: 0.9, attack: 0.001, sweep: 400 }); // blast body
    tone(t, 0.35, { type: 'sine', freq: rnd(120, 150), to_f: 35, gain: 0.9, attack: 0.001 }); // thump
    // room tail: slow filtered noise, delayed slightly
    noise(t + 0.03, 1.2, { type: 'lowpass', freq: 1800, q: 0.5, gain: 0.3, attack: 0.05, sweep: 200 });
    noise(t + 0.09, 0.9, { type: 'bandpass', freq: 700, q: 0.8, gain: 0.15, attack: 0.1, sweep: 300 });
  }

  function ring() {
    const t = now();
    // duck everything else, and muffle the world for ~3 s
    const g = duck.gain;
    g.cancelScheduledValues(t); g.setValueAtTime(g.value, t);
    g.linearRampToValueAtTime(0.15, t + 0.05);
    g.setValueAtTime(0.15, t + 1.8);
    g.linearRampToValueAtTime(1, t + 3.2);
    const lp = duckLP.frequency;
    lp.cancelScheduledValues(t); lp.setValueAtTime(700, t);
    lp.setValueAtTime(700, t + 3);
    lp.exponentialRampToValueAtTime(OPEN, t + 3.4);
    const f = rnd(5800, 6800);
    for (const [fr, v] of [[f, 0.08], [f * 1.01, 0.05]]) {
      const o = ac.createOscillator(); o.type = 'sine'; o.frequency.value = fr;
      const e = ac.createGain();
      e.gain.setValueAtTime(0.0001, t);
      e.gain.linearRampToValueAtTime(v, t + 0.08);
      e.gain.setValueAtTime(v, t + 0.6);
      e.gain.exponentialRampToValueAtTime(0.0001, t + 2.5);
      o.connect(e); e.connect(master);
      o.start(t); o.stop(t + 2.55);
      done(o, e);
    }
  }

  // ---- 3D voice (breathing) ----
  const v3 = a => Array.isArray(a) ? a : [a.x, a.y, a.z];
  function setPos(p, x, y, z) {
    if (p.positionX) { p.positionX.value = x; p.positionY.value = y; p.positionZ.value = z; }
    else p.setPosition(x, y, z);
  }
  let voice = [0, 1.6, -1.3];
  function setVoicePos(x, y, z) { voice = [x, y, z]; if (br) setPos(br.pan, x, y, z); }
  function setListener(pos, fwd, up) {
    const L = ac.listener, [px, py, pz] = v3(pos), [fx, fy, fz] = v3(fwd), [ux, uy, uz] = v3(up);
    if (L.positionX) {
      L.positionX.value = px; L.positionY.value = py; L.positionZ.value = pz;
      L.forwardX.value = fx; L.forwardY.value = fy; L.forwardZ.value = fz;
      L.upX.value = ux; L.upY.value = uy; L.upZ.value = uz;
    } else { L.setPosition(px, py, pz); L.setOrientation(fx, fy, fz, ux, uy, uz); }
  }

  // breathing through a mask: scheduled asymmetric envelope (inhale 0.9 rising, exhale 1.4 falling, 0.4 gap)
  const CYCLE = 0.9 + 1.4 + 0.4;
  const BREATH_PEAK = 0.92; // = 0.23 x4 makeup (bandpass noise is quiet); HRTF 1/1.3 at 1.3 m
  let br = null;
  function breath(on) {
    const t = now();
    if (on && !br) {
      const s = ac.createBufferSource(); s.buffer = noiseBuf; s.loop = true;
      const bp = ac.createBiquadFilter(); bp.type = 'bandpass'; bp.frequency.value = 1100; bp.Q.value = 3;
      const env = ac.createGain(); env.gain.value = 0.0001;
      const pan = ac.createPanner();
      pan.panningModel = 'HRTF'; pan.distanceModel = 'inverse'; pan.refDistance = 1; pan.rolloffFactor = 1;
      setPos(pan, ...voice);
      const fade = ac.createGain(); fade.gain.setValueAtTime(0.0001, t); fade.gain.linearRampToValueAtTime(1, t + 0.5);
      s.connect(bp); bp.connect(env); env.connect(pan); pan.connect(fade); fade.connect(duck);
      s.start(t, rnd(0, 1));
      const b = br = { s, env, pan, fade, bp, next: t, timer: 0 };
      const fill = () => {
        const until = now() + 10;
        while (b.next < until) {
          const t0 = b.next, pk = BREATH_PEAK * rnd(0.9, 1.1);
          env.gain.setValueAtTime(0.0001, t0);
          env.gain.linearRampToValueAtTime(pk, t0 + 0.9);
          env.gain.linearRampToValueAtTime(0.0001, t0 + 2.3);
          b.next += CYCLE;
        }
      };
      fill();
      b.timer = setInterval(fill, 2000);
    } else if (!on && br) {
      const b = br; br = null;
      clearInterval(b.timer);
      b.fade.gain.cancelScheduledValues(t); b.fade.gain.setValueAtTime(b.fade.gain.value, t);
      b.fade.gain.linearRampToValueAtTime(0.0001, t + 0.5);
      b.s.stop(t + 0.55);
      done(b.s, b.bp, b.env, b.pan, b.fade);
    }
  }

  // ---- room tone, heartbeat, drone ----
  const bedGain = ac.createGain(); bedGain.gain.value = 0; bedGain.connect(duck);
  const heartGain = ac.createGain(); heartGain.gain.value = 1; heartGain.connect(duck);
  const droneGain = ac.createGain(); droneGain.gain.value = 0; droneGain.connect(duck);
  let bedNodes = null, droneOn = false, tension = 0, hTimer = 0, silenced = false;

  function bed(on) {
    const t = now();
    if (on) {
      silenced = false;
      heartGain.gain.cancelScheduledValues(t); heartGain.gain.setValueAtTime(1, t);
      if (!bedNodes) {
        const s = ac.createBufferSource(); s.buffer = noiseBuf; s.loop = true;
        const f = ac.createBiquadFilter(); f.type = 'lowpass'; f.frequency.value = 180;
        const ng = ac.createGain(); ng.gain.value = 0.025;
        const o = ac.createOscillator(); o.type = 'sine'; o.frequency.value = 55;
        const og = ac.createGain(); og.gain.value = 0.02;
        s.connect(f); f.connect(ng); ng.connect(bedGain); o.connect(og); og.connect(bedGain);
        s.start(t, rnd(0, 1)); o.start(t);
        bedNodes = { s, o, all: [f, ng, og] };
      }
      bedGain.gain.cancelScheduledValues(t); bedGain.gain.setValueAtTime(bedGain.gain.value, t);
      bedGain.gain.linearRampToValueAtTime(1, t + 0.5);
    } else if (bedNodes) {
      const b = bedNodes; bedNodes = null;
      bedGain.gain.cancelScheduledValues(t); bedGain.gain.setValueAtTime(bedGain.gain.value, t);
      bedGain.gain.linearRampToValueAtTime(0, t + 0.3);
      b.s.stop(t + 0.35); b.o.stop(t + 0.35);
      done(b.s, ...b.all); done(b.o);
    }
  }

  function beat() {
    if (tension <= 0 || silenced) { hTimer = 0; return; }
    const t = now();
    for (const dt of [0, 0.08]) tone(t + dt, 0.14, { type: 'sine', freq: 60, to_f: 40, gain: 0.25, attack: 0.005, to: heartGain });
    hTimer = setTimeout(beat, 60000 / (60 + 60 * tension));
  }
  function ensureDrone() { // ponytail: oscillators run forever once created (silent at gain 0); stop them if CPU matters
    if (droneOn) return; droneOn = true;
    const lp = ac.createBiquadFilter(); lp.type = 'lowpass'; lp.frequency.value = 260; lp.connect(droneGain);
    for (const f of [82.4, 87.3]) { const o = ac.createOscillator(); o.type = 'sawtooth'; o.frequency.value = f; o.connect(lp); o.start(now()); }
  }
  function setTension(x) {
    if (silenced) return;
    tension = Math.max(0, Math.min(1, x));
    const t = now();
    droneGain.gain.cancelScheduledValues(t);
    if (tension > 0) {
      ensureDrone();
      droneGain.gain.setTargetAtTime(0.06 * tension, t, 0.3);
      if (!hTimer) beat();
    } else {
      droneGain.gain.setTargetAtTime(0, t, 0.3);
      clearTimeout(hTimer); hTimer = 0;
    }
  }
  function silence() {
    const t = now();
    silenced = true; clearTimeout(hTimer); hTimer = 0;
    for (const g of [bedGain, heartGain, droneGain]) {
      g.gain.cancelScheduledValues(t); g.gain.setValueAtTime(g.gain.value, t);
      g.gain.linearRampToValueAtTime(0, t + 0.03);
    }
  }

  function stinger() {
    const t = now();
    tone(t, 0.9, { type: 'sine', freq: 90, to_f: 40, gain: 0.8, attack: 0.003 });
    noise(t, 0.3, { type: 'lowpass', freq: 800, gain: 0.5, attack: 0.002, sweep: 150 });
    // dissonant swell: minor second + tritone cluster
    for (const fr of [220, 233, 311, 466]) {
      tone(t, 1.5, { type: 'sawtooth', freq: fr * rnd(0.99, 1.01), to_f: fr * 0.94, gain: 0.07, attack: 0.9, detune: rnd(-15, 15) });
    }
  }

  function win() {
    const t = now();
    [784, 988, 1319].forEach((fr, i) => {
      tone(t + i * 0.09, 0.9, { type: 'sine', freq: fr, gain: 0.2, attack: 0.01 });
      tone(t + i * 0.09, 0.5, { type: 'triangle', freq: fr * 2, gain: 0.05, attack: 0.01 });
    });
  }

  function setMaster(v) { master.gain.value = Math.max(0, Math.min(1, v)); }

  return { tick, cock, halfRack, creak, scuff, gunshot, ring, breath, setVoicePos, setListener,
    bed, setTension, silence, stinger, win, setMaster };
}
