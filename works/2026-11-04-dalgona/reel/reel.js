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
#tray,.tools,#hint,#sub,#stat,#toast,#cursor,#ring,#again,#doom{display:none!important}
#rend .logo{width:150px;height:auto;opacity:.95}
#rend .ask{font-family:"Do Hyeon",sans-serif;font-size:62px;line-height:1.08;color:#fff;margin:6px 0 4px}
#rend .ask em{font-style:normal;color:#ffc23d}
#rh{display:none;position:absolute;left:0;top:0;pointer-events:none;transform-origin:0 0;will-change:transform;filter:drop-shadow(0 6px 10px rgba(0,0,0,.35))}
#rc{position:absolute;left:20px;right:20px;top:calc(env(safe-area-inset-top) + 124px);text-align:center;font-family:"Do Hyeon",sans-serif;
  font-size:44px;line-height:1.08;color:#fff;letter-spacing:-.01em;text-wrap:balance;pointer-events:none;
  text-shadow:0 3px 0 rgba(0,0,0,.35),0 0 24px rgba(0,0,0,.55);transition:none}
#rc em{font-style:normal;color:#ffc23d}
#rc small{display:block;font-size:28px;margin-top:8px;color:#f2efe8;letter-spacing:0}
#rend{position:absolute;inset:0;display:none;flex-direction:column;align-items:center;justify-content:center;gap:18px;
  background:radial-gradient(120% 80% at 50% 40%,rgba(30,22,16,.86),rgba(8,6,5,.96));pointer-events:none;text-align:center}
#rend b{font-family:"Do Hyeon",sans-serif;font-weight:400;font-size:76px;color:#fff;line-height:1}
#rend b em{font-style:normal;color:#e9a64a}
#rend span{font-family:"IBM Plex Sans KR",sans-serif;font-weight:600;font-size:23px;color:#f2ede4}
#rend i{font-style:normal;font-family:"Do Hyeon",sans-serif;font-size:27px;color:#2a1406;background:#ffc23d;border-radius:16px;padding:12px 26px;margin-top:10px}
#timer{bottom:150px!important}
/* Reels covers the bottom fifth with its own UI */
#rflash{position:absolute;inset:0;background:#fff;opacity:0;pointer-events:none}
#rchip{position:absolute;left:50%;bottom:calc(env(safe-area-inset-bottom) + 96px);transform:translateX(-50%);font-family:"Do Hyeon",sans-serif;font-size:30px;color:#fff;background:rgba(8,10,9,.72);border-radius:999px;padding:6px 22px;opacity:0;pointer-events:none;white-space:nowrap}
#rhold{position:absolute;inset:0;width:100%;height:100%;object-fit:cover;opacity:0;pointer-events:none}`;
document.head.appendChild(css);
const hud = document.querySelector(".hud");
// the hammer swings on its own while the pointer is held (the pinch), as in camera play
A.AR.autoSwing = true;
// the operator's own hand, cut from their phone test screenshot (11-06); (PX, PY) = where thumb and index meet
const PX = 145, PY = 212, NAT = 390 / 1248, ROT = 13.5;
const hand = Object.assign(document.createElement("img"), { id: "rh", src: here("assets/hand2_sam.png") });
const cap = Object.assign(document.createElement("div"), { id: "rc" });
const end = Object.assign(document.createElement("div"), { id: "rend" });
const flash = Object.assign(document.createElement("div"), { id: "rflash" });
// a still of the frame before a time skip, faded out over the new one: a dissolve instead of a jump
const hold = Object.assign(document.createElement("img"), { id: "rhold" });
const chip = Object.assign(document.createElement("div"), { id: "rchip" });
hud.append(hold, chip, cap, end, flash);
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
  hold(src, o) { if (src) hold.src = src; hold.style.opacity = o; },
  chip(text, o = 1) { if (text != null) chip.textContent = text; chip.style.opacity = o; },
  timer(o) { document.getElementById("timer").style.opacity = o; },
  zoom(k, ox, oy) { lens.style.transformOrigin = `${ox}px ${oy}px`; lens.style.transform = `scale(${k})`; },
  ready: () => hand.complete && tex.image != null,
};
