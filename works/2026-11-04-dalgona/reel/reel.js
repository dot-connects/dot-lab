// Promo-video mode (?reel on the AR page): a real room around the candy, the operator's hand over the tools, and
// big captions. The page runs on Playwright's fake clock; render.py moves the pointer, the gyro and the captions
// frame by frame and screenshots each one. Nothing here ships to players.
const A = window.__ar, { THREE, scene } = A;
const here = p => new URL(p, import.meta.url).href;
const Q = new URLSearchParams(location.search);

// The room: a 360 photo on a sphere around the camera (Poly Haven, CC0: "Metro Vijzelgracht", "Lebombo"), so turning
// the phone pans it.
const tex = new THREE.TextureLoader().load(here(`assets/${Q.get("room") || "metro"}_4k.jpg`));
tex.colorSpace = THREE.SRGBColorSpace; tex.wrapS = THREE.RepeatWrapping; tex.repeat.x = -1;
const room = new THREE.Mesh(new THREE.SphereGeometry(9, 96, 48), new THREE.MeshBasicMaterial({ map: tex, side: THREE.BackSide, depthWrite: false, toneMapped: false }));
room.renderOrder = -10; room.rotation.y = +(Q.get("yaw") || 0) * Math.PI / 180;
scene.add(room);

const css = document.createElement("style");
css.textContent = `
#tray,.tools,#hint,#sub,#stat,#toast,#cursor,#ring,#again,#doom,#zbar{display:none!important}
#rend .logo{width:150px;height:auto;opacity:.95}
#rend .ask{font-family:"IBM Plex Sans KR",sans-serif;font-weight:600;font-size:40px;line-height:1.3;color:#fff;margin:10px 0 6px;letter-spacing:-.01em}
#rend .ask em{font-style:normal;color:#ff4d3d}
#rend i{white-space:nowrap}
#rh{display:none;position:absolute;left:0;top:0;pointer-events:none;transform-origin:0 0;will-change:transform;filter:drop-shadow(0 6px 10px rgba(0,0,0,.35))}
/* 11-08 operator: the playful captions read childish; now plain statements, a calm sans, one red for "탈락" */
#rc{position:absolute;left:24px;right:24px;top:calc(env(safe-area-inset-top) + 128px);text-align:center;font-family:"IBM Plex Sans KR",sans-serif;
  font-weight:600;font-size:31px;line-height:1.35;color:#fff;letter-spacing:-.01em;text-wrap:balance;pointer-events:none;
  text-shadow:0 2px 2px rgba(0,0,0,.45),0 0 22px rgba(0,0,0,.6);transition:none}
#rc em{font-style:normal;color:#ff4d3d}
#rc small{display:block;font-size:21px;font-weight:400;margin-top:8px;color:#ece8e2;letter-spacing:0}
#rc .out{display:inline-block;font-size:60px;color:#ff4d3d;letter-spacing:.32em;margin-right:-.32em}
#rclock{position:absolute;left:0;right:0;top:calc(env(safe-area-inset-top) + 178px);text-align:center;font-family:"IBM Plex Sans KR",sans-serif;font-weight:600;
  font-size:80px;line-height:1;color:#fff;font-variant-numeric:tabular-nums;letter-spacing:.02em;opacity:0;pointer-events:none;
  text-shadow:0 3px 3px rgba(0,0,0,.4),0 0 30px rgba(0,0,0,.6)}
#rclock.low{color:#ff4d3d}
/* the tries in between: a film strip running through a projector gate */
#rfilm{position:absolute;inset:0;background:#0c0b0a;opacity:0;pointer-events:none;overflow:hidden}
#rfilm .strip{position:absolute;left:50%;top:0;width:296px;margin-left:-148px;transform-origin:50% 0}
#rfilm .cell{position:absolute;left:0;right:0;height:300px;background:#17130f;box-sizing:border-box;padding:10px 40px}
#rfilm .cell::before,#rfilm .cell::after{content:"";position:absolute;top:0;bottom:0;width:22px;
  background:repeating-linear-gradient(#17130f 0 9px,#d8d2c6 9px 27px,#17130f 27px 37.5px);background-size:12px 37.5px;background-repeat:repeat-y;background-position:center}
#rfilm .cell::before{left:8px}#rfilm .cell::after{right:8px}
#rfilm .pic{position:relative;width:100%;height:100%;overflow:hidden;border-radius:3px;background:#2a241e}
#rfilm img{width:100%;height:100%;object-fit:cover;object-position:50% 50%;filter:sepia(.45) saturate(.55) contrast(1.08) brightness(.78)}
#rfilm .x{position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:4px;font-family:"IBM Plex Sans KR",sans-serif}
#rfilm .x b{font-weight:600;font-size:38px;color:#ff4d3d;letter-spacing:.3em;margin-right:-.3em;border:3px solid #ff4d3d;padding:2px 12px 4px 14px;transform:rotate(-7deg);background:rgba(12,10,8,.35)}
#rfilm .x span{font-size:17px;color:#f0e9de;text-shadow:0 1px 3px #000}
#rfilm .edge{position:absolute;left:9px;top:7px;font:600 15px/1 "IBM Plex Sans KR",sans-serif;color:#e8913a;letter-spacing:.12em}
#rfilm .cell.blank .pic{background:#fff}
#rfilm .gate{position:absolute;inset:0;background:radial-gradient(90% 60% at 50% 50%,transparent 55%,rgba(0,0,0,.75))}
#rend{position:absolute;inset:0;display:none;flex-direction:column;align-items:center;justify-content:center;gap:18px;
  background:radial-gradient(120% 80% at 50% 40%,rgba(30,22,16,.86),rgba(8,6,5,.96));pointer-events:none;text-align:center}
#rend span{font-family:"IBM Plex Sans KR",sans-serif;font-weight:400;font-size:21px;color:#d9d3ca}
#rend i{font-style:normal;font-family:"IBM Plex Sans KR",sans-serif;font-weight:600;font-size:21px;color:#fff;border:1.5px solid rgba(255,255,255,.7);border-radius:6px;padding:12px 24px;margin-top:14px}
#timer{bottom:150px!important}
#dead b{font-family:"IBM Plex Sans KR",sans-serif!important;font-weight:600!important;font-size:64px!important;letter-spacing:.3em!important;margin-right:-.3em}#dead span{font-size:21px!important;font-family:"IBM Plex Sans KR",sans-serif}
/* Reels covers the bottom fifth with its own UI */
#rflash{position:absolute;inset:0;background:#fff;opacity:0;pointer-events:none}
#rchip{position:absolute;left:50%;bottom:calc(env(safe-area-inset-bottom) + 96px);transform:translateX(-50%);font-family:"IBM Plex Sans KR",sans-serif;font-weight:600;font-size:22px;color:#fff;background:rgba(8,10,9,.72);border-radius:999px;padding:6px 22px;opacity:0;pointer-events:none;white-space:nowrap}
#rcount{position:absolute;left:20px;top:calc(env(safe-area-inset-top) + 40px);font-family:"IBM Plex Sans KR",sans-serif;font-weight:600;font-size:15px;color:#e9e4dc;background:rgba(10,9,8,.72);border-left:4px solid #ff4d3d;border-radius:4px;padding:6px 14px 6px 12px;opacity:0;pointer-events:none;letter-spacing:.06em;box-shadow:0 4px 14px rgba(0,0,0,.35)}
#rcount b{display:inline-block;font-size:34px;line-height:1;color:#fff;font-variant-numeric:tabular-nums;margin-left:8px;vertical-align:-5px;letter-spacing:.02em}
#rlogo{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;background:radial-gradient(90% 60% at 50% 50%,rgba(10,8,6,.72),rgba(10,8,6,.5));opacity:0;pointer-events:none}
#rlogo img{width:230px;height:auto}
#rhold{position:absolute;inset:0;width:100%;height:100%;object-fit:cover;opacity:0;pointer-events:none}`;
document.head.appendChild(css);
const hud = document.querySelector(".hud");
// the hammer swings on its own while the pointer is held (the pinch), as in camera play
A.AR.autoSwing = true;
// a flat white hand traced from the operator's own pinch photo (silhouette.py; 11-07 the photo hand read too real and
// stopped at the wrist); (PX, PY) = where thumb and index meet
const PX = 145, PY = 212, NAT = 390 / 1248, ROT = 13.5;
const hand = Object.assign(document.createElement("img"), { id: "rh", src: here("assets/hand_white.png") });
const cap = Object.assign(document.createElement("div"), { id: "rc" });
const end = Object.assign(document.createElement("div"), { id: "rend" });
const flash = Object.assign(document.createElement("div"), { id: "rflash" });
// a still of the frame before a time skip, faded out over the new one: a dissolve instead of a jump
const hold = Object.assign(document.createElement("img"), { id: "rhold" });
const chip = Object.assign(document.createElement("div"), { id: "rchip" });
// cut B: which try this is, top left all the way through
const count = Object.assign(document.createElement("div"), { id: "rcount" });
const clock = Object.assign(document.createElement("div"), { id: "rclock" });
// 11-08: the tries between the 2nd and the 13th run past as a film strip (stills from the tries already shot)
const film = Object.assign(document.createElement("div"), { id: "rfilm" });
film.innerHTML = '<div class="strip"></div><div class="gate"></div>';
const strip = film.querySelector(".strip"), CELL = 300;
let filmCells = [];
// 11-09 operator: the logo over the first second, then it fades off the opening shot
const logo = Object.assign(document.createElement("div"), { id: "rlogo" });
logo.innerHTML = `<img src="${here("../site/logo.svg")}" alt="달고나">`;
hud.append(hold, chip, cap, clock, end, flash, film, count, logo);   // the count stays on top of the white and the film
// The "lens": the 3D canvas and the hand share one wrapper, so a push-in zooms both alike while the captions and the
// clock stay put. The page still reads the pointer in unzoomed screen px, so the driver never has to think about it.
const gl = document.getElementById("gl"), lens = document.createElement("div");
lens.style.cssText = "position:absolute;inset:0;transform-origin:50% 50%;will-change:transform";
gl.parentNode.insertBefore(lens, gl); lens.append(gl, hand);

window.__reel = {
  // pinch point at screen (x, y); k = size (1 = as photographed, smaller = further from the phone); r = extra turn, deg
  hand(x, y, k = 1, r = 0, on = true) {
    hand.style.display = on ? "block" : "none";
    const s = NAT * k;
    hand.style.transform = `translate(${x}px,${y}px) rotate(${ROT + r}deg) scale(${s}) translate(${-PX}px,${-PY}px)`;
  },
  cap(html = "", o = 1) { cap.innerHTML = html; cap.style.opacity = o; },
  end(html, o = 1) { end.style.display = html ? "flex" : "none"; end.innerHTML = html || ""; end.style.opacity = o; document.getElementById("timer").style.opacity = html ? 1 - o : 1; },
  flash(o) { flash.style.opacity = o; },
  logo(o) { logo.style.opacity = o; },
  hold(src, o) { if (src) hold.src = src; hold.style.opacity = o; },
  chip(text, o = 1) { if (text != null) chip.textContent = text; chip.style.opacity = o; },
  count(n, o = 1) { if (n != null) count.innerHTML = `시도<b>${String(n).padStart(2, "0")}</b>`; count.style.opacity = o; },
  timer(o) { document.getElementById("timer").style.opacity = o; },
  clock(text, o = 1, low = false) { if (text != null) clock.textContent = text; clock.style.opacity = o; clock.classList.toggle("low", low); },
  // cells: [{n, frames: [url], brk, why}] or {n, blank: true}; returns when the images are loaded (poll filmReady).
  // Each cell plays its own short clip as it crosses the gate; the 탈락 stamp lands on frame brk (the break).
  filmSetup(cells) {
    filmCells = cells;
    strip.innerHTML = cells.map(c => `<div class="cell${c.blank ? " blank" : ""}" data-n="${c.n}"><div class="pic">${c.blank ? "" :
      c.frames.map(s => `<img src="${s}" style="position:absolute;inset:0;visibility:hidden">`).join("") +
      `<div class="x"><b>탈락</b><span>${c.why}</span></div>`}<div class="edge">${String(c.n).padStart(2, "0")}</div></div></div>`).join("");
  },
  filmReady: () => [...strip.querySelectorAll("img")].every(i => i.complete && i.naturalWidth > 0),
  // y = which cell (index into cells, fractional) sits in the gate; k = zoom into the gate; b = brightness flicker
  film(o, y = 0, k = 1, b = 1, blur = 0) {
    film.style.opacity = o;
    const H = innerHeight;
    strip.querySelectorAll(".cell").forEach((c, i) => {
      c.style.top = `${H / 2 - CELL / 2 + (y - i) * CELL}px`;
      const fc = filmCells[i]; if (!fc || fc.blank) return;
      const u = Math.min(1, Math.max(0, (y - i + 0.45) / 0.75)), idx = Math.round(u * (fc.frames.length - 1));
      c.querySelectorAll("img").forEach((im, j) => { im.style.visibility = j === idx ? "visible" : "hidden"; });
      c.querySelector(".x").style.opacity = idx >= fc.brk ? 1 : 0;
    });
    strip.style.transformOrigin = `50% ${H / 2}px`;
    strip.style.transform = `scale(${k})`;
    strip.style.filter = `brightness(${b})${blur ? ` blur(${blur}px)` : ""}`;
  },
  zoom(k, ox, oy) { lens.style.transformOrigin = `${ox}px ${oy}px`; lens.style.transform = `scale(${k})`; },
  ready: () => hand.complete && tex.image != null,
};
