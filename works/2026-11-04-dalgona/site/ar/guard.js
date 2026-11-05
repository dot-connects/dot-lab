// Procedural masked guard for the AR scene. Own character: orange jumpsuit, black mask with a white X.
// Origin = floor point between the feet, faces +Z, 1 unit = 1 m. No external assets.
import * as THREE from "https://cdn.jsdelivr.net/npm/three@0.160.0/build/three.module.min.js";

const V = THREE.Vector3;
const SUIT = 0xd2552a, TRIM = 0xa8441f;

// ---------- textures ----------
function fabricNormalMap() {
  const S = 256, h = new Float32Array(S * S);
  let seed = 7;
  const rnd = () => ((seed = (seed * 16807) % 2147483647) / 2147483647);
  // tileable value noise, near-isotropic so it reads as crumpled canvas, not grain
  const lat = (gx, gy) => { const L = new Float32Array(gx * gy); for (let i = 0; i < L.length; i++) L[i] = rnd() * 2 - 1; return [L, gx, gy]; };
  const noise = ([L, gx, gy], u, v) => {
    const x = u * gx, y = v * gy, x0 = Math.floor(x), y0 = Math.floor(y), fx = x - x0, fy = y - y0;
    const sx = fx * fx * (3 - 2 * fx), sy = fy * fy * (3 - 2 * fy);
    const at = (i, j) => L[((j % gy) + gy) % gy * gx + ((i % gx) + gx) % gx];
    return (at(x0, y0) * (1 - sx) + at(x0 + 1, y0) * sx) * (1 - sy) + (at(x0, y0 + 1) * (1 - sx) + at(x0 + 1, y0 + 1) * sx) * sy;
  };
  const oct = [lat(6, 5), lat(12, 10), lat(24, 20)], warp = lat(5, 5), warp2 = lat(4, 6);
  for (let y = 0; y < S; y++) for (let x = 0; x < S; x++) {
    const u0 = x / S, w0 = y / S, u = u0 + 0.07 * noise(warp, u0, w0), w = w0 + 0.07 * noise(warp2, u0, w0), du = 0;
    let v = 0;
    oct.forEach((o, i) => { const n = 1 - Math.abs(noise(o, u + du, w)); v += n * n * [1, 0.35, 0.12][i]; }); // ridged creases, big folds dominate
    v += 0.04 * (((x >> 1) + (y >> 1)) & 1); // canvas weave
    h[y * S + x] = v * 1.6;
  }
  const c = document.createElement("canvas");
  c.width = c.height = S;
  const ctx = c.getContext("2d"), img = ctx.createImageData(S, S);
  const at = (x, y) => h[((y + S) % S) * S + ((x + S) % S)];
  for (let y = 0; y < S; y++) for (let x = 0; x < S; x++) {
    const dx = (at(x + 1, y) - at(x - 1, y)) * 4, dy = (at(x, y + 1) - at(x, y - 1)) * 4;
    const n = new V(-dx, -dy, 1).normalize(), i = (y * S + x) * 4;
    img.data[i] = (n.x * 0.5 + 0.5) * 255; img.data[i + 1] = (n.y * 0.5 + 0.5) * 255;
    img.data[i + 2] = (n.z * 0.5 + 0.5) * 255; img.data[i + 3] = 255;
  }
  ctx.putImageData(img, 0, 0);
  const t = new THREE.CanvasTexture(c);
  t.wrapS = t.wrapT = THREE.RepeatWrapping;
  t.repeat.set(6, 4);
  return t;
}

// ---------- geometry helpers ----------
function tf(g, p = [0, 0, 0], r = [0, 0, 0], s = [1, 1, 1]) {
  const m = new THREE.Matrix4().compose(new V(...p), new THREE.Quaternion().setFromEuler(new THREE.Euler(...r)), new V(...s));
  return g.applyMatrix4(m);
}
// profile = [[radius, y], ...] bottom to top; zs flattens the cross-section front-to-back
function lathe(profile, segs = 20, zs = 1, phiStart = 0, phiLen = Math.PI * 2) {
  const g = new THREE.LatheGeometry(profile.map(([r, y]) => new THREE.Vector2(r, y)), segs, phiStart, phiLen);
  return zs === 1 ? g : g.scale(1, 1, zs);
}
// insert soft fold rings (radius bumps) into a lathe profile at the given heights
function folds(profile, ys, amp = 0.005) {
  const out = profile.slice();
  for (const y of ys) { const r = profR(profile, y); out.push([r, y - 0.011], [r + amp, y], [r, y + 0.011]); }
  return out.map((p, i) => [p, i]).sort((a, b) => a[0][1] - b[0][1] || a[1] - b[1]).map((p) => p[0]);
}
const ell = (rx, ry, rz, p, r, w = 16, hh = 12) => tf(new THREE.SphereGeometry(1, w, hh), p, r, [rx, ry, rz]);
const box = (w, h, d, p, r) => tf(new THREE.BoxGeometry(w, h, d), p, r);
const cyl = (r0, r1, len, p, r, seg = 12) => tf(new THREE.CylinderGeometry(r1, r0, len, seg), p, r);
function rbox(w, h, d, r, p, rot, n = 5) { // box with rounded edges (radius r), smooth normals
  const g = new THREE.BoxGeometry(w, h, d, n, n, n), P = g.attributes.position, N = g.attributes.normal;
  const hx = w / 2 - r, hy = h / 2 - r, hz = d / 2 - r, c = new V(), v = new V();
  for (let i = 0; i < P.count; i++) {
    v.fromBufferAttribute(P, i);
    c.set(THREE.MathUtils.clamp(v.x, -hx, hx), THREE.MathUtils.clamp(v.y, -hy, hy), THREE.MathUtils.clamp(v.z, -hz, hz));
    v.sub(c).normalize();
    N.setXYZ(i, v.x, v.y, v.z);
    P.setXYZ(i, c.x + v.x * r, c.y + v.y * r, c.z + v.z * r);
  }
  return tf(g, p, rot);
}
const cap = (r, len, p, rot) => tf(new THREE.CapsuleGeometry(r, len, 3, 8), p, rot);
function profR(profile, y) { // radius of a lathe profile at height y
  for (let i = 1; i < profile.length; i++) {
    const [r0, y0] = profile[i - 1], [r1, y1] = profile[i];
    if (y >= y0 && y <= y1) return r0 + ((r1 - r0) * (y - y0)) / (y1 - y0 || 1);
  }
  return 0;
}

// merge position/normal/uv of indexed or non-indexed geometries (no addons -> no import map needed)
function merge(geos) {
  let n = 0;
  for (const g of geos) n += g.attributes.position.count;
  const pos = new Float32Array(n * 3), nor = new Float32Array(n * 3), uv = new Float32Array(n * 2), col = new Float32Array(n * 3).fill(1), idx = [];
  let off = 0;
  for (const g of geos) {
    const c = g.attributes.position.count;
    pos.set(g.attributes.position.array, off * 3);
    nor.set(g.attributes.normal.array, off * 3);
    if (g.attributes.uv) uv.set(g.attributes.uv.array, off * 2);
    if (g.attributes.color) col.set(g.attributes.color.array, off * 3);
    if (g.index) for (const i of g.index.array) idx.push(i + off);
    else for (let i = 0; i < c; i++) idx.push(i + off);
    off += c;
  }
  const m = new THREE.BufferGeometry();
  m.setAttribute("position", new THREE.BufferAttribute(pos, 3));
  m.setAttribute("normal", new THREE.BufferAttribute(nor, 3));
  m.setAttribute("uv", new THREE.BufferAttribute(uv, 2));
  m.setAttribute("color", new THREE.BufferAttribute(col, 3));
  m.setIndex(idx);
  return m;
}
// fake ambient occlusion from the rest-pose position (metres, guard space)
function aoAt(x, y, z, lz, limb) {
  let f = 0.7 + 0.3 * THREE.MathUtils.smoothstep(y, 0, 0.3); // darker near the floor
  const dark = (d, r, amt) => { if (d < r) f *= 1 - amt * (1 - d / r); };
  dark(Math.hypot(x, (y - 0.86) * 1.3, z * 0.8), 0.15, 0.32); // crotch
  if (y > 0.96 && y < 1.035) f *= 1 - 0.25 * ((y - 0.96) / 0.075); // under the belt
  for (const s of [1, -1]) dark(Math.hypot(x - s * 0.15, y - 1.34, z), 0.1, 0.3); // armpits
  if (limb) f *= 1 - 0.25 * THREE.MathUtils.clamp(-lz / 0.045, 0, 1); // inner arm (faces away from the IK pole)
  return f;
}
// parts = [[geometry, material], ...] -> one mesh per material under parent.
// ao = {o: rest origin of parent in guard space, limb} bakes fake AO into vertex colours.
function seg(parent, parts, ao) {
  const byMat = new Map();
  for (const [g, m] of parts) {
    if (ao) {
      const P = g.attributes.position, c = new Float32Array(P.count * 3);
      for (let i = 0; i < P.count; i++) c.fill(aoAt(P.getX(i) + ao.o[0], P.getY(i) + ao.o[1], P.getZ(i) + ao.o[2], P.getZ(i), ao.limb), i * 3, i * 3 + 3);
      g.setAttribute("color", new THREE.BufferAttribute(c, 3));
    }
    (byMat.get(m) || byMat.set(m, []).get(m)).push(g);
  }
  for (const [m, gs] of byMat) {
    const mesh = new THREE.Mesh(merge(gs), m);
    mesh.castShadow = false; // a low-poly proxy casts instead
    mesh.receiveShadow = true;
    parent.add(mesh);
  }
  return parent;
}
const grp = (parent, p = [0, 0, 0]) => { const g = new THREE.Group(); g.position.set(...p); parent.add(g); return g; };

// ---------- IK / pose helpers (world space) ----------
const _m = new THREE.Matrix4(), _q = new THREE.Quaternion(), _pq = new THREE.Quaternion();
const _A = new V(), _E = new V(), _W = new V(), _d = new V(), _p = new V(), _x = new V(), _y = new V(), _z = new V();
function setWorldQuat(obj, q) {
  obj.parent.getWorldQuaternion(_pq);
  obj.quaternion.copy(_pq.invert().multiply(q));
  obj.updateMatrixWorld(true);
}
function orientBone(bone, from, to, perp) { // bone's -Y runs from->to, +Z leans toward perp
  _y.subVectors(from, to).normalize();
  _x.crossVectors(_y, perp).normalize();
  _z.crossVectors(_x, _y);
  _m.makeBasis(_x, _y, _z);
  setWorldQuat(bone, _q.setFromRotationMatrix(_m));
}
function solveTwoBone(upper, lower, l1, l2, target, pole) {
  upper.updateWorldMatrix(true, false);
  _A.setFromMatrixPosition(upper.matrixWorld);
  _d.subVectors(target, _A);
  const len = THREE.MathUtils.clamp(_d.length(), 0.05, l1 + l2 - 1e-4);
  _d.normalize();
  const ca = (l1 * l1 + len * len - l2 * l2) / (2 * l1 * len), sa = Math.sqrt(Math.max(0, 1 - ca * ca));
  _p.subVectors(pole, _A).addScaledVector(_d, -_p.subVectors(pole, _A).dot(_d)).normalize();
  _E.copy(_A).addScaledVector(_d, l1 * ca).addScaledVector(_p, l1 * sa);
  _W.copy(_A).addScaledVector(_d, len);
  const perp = _p.clone();
  orientBone(upper, _A.clone(), _E.clone(), perp);
  orientBone(lower, _E.clone(), _W.clone(), perp);
}
function basisLook(dir, up, out) { // quaternion whose +Z = dir
  _z.copy(dir).normalize();
  _x.crossVectors(up, _z).normalize();
  _y.crossVectors(_z, _x);
  return out.setFromRotationMatrix(_m.makeBasis(_x, _y, _z));
}

export function makeGuard(opts = {}) {
  const nmap = fabricNormalMap();
  const cloth = (color) => new THREE.MeshPhysicalMaterial({
    color, roughness: 0.92, sheen: 1, sheenRoughness: 0.6, sheenColor: new THREE.Color(0xff9a6a),
    normalMap: nmap, normalScale: new THREE.Vector2(0.6, 0.6), vertexColors: true });
  const suit = cloth(SUIT), trim = cloth(TRIM);
  const hoodMat = cloth(SUIT); hoodMat.side = THREE.DoubleSide;
  const black = new THREE.MeshStandardMaterial({ color: 0x151517, roughness: 0.55 });
  const rubber = new THREE.MeshStandardMaterial({ color: 0x0b0b0c, roughness: 0.9 });
  const mask = new THREE.MeshPhysicalMaterial({ color: 0x050506, roughness: 0.2, metalness: 0.1, clearcoat: 1, clearcoatRoughness: 0.06 });
  const white = new THREE.MeshStandardMaterial({ color: 0xf4f4f2, roughness: 0.35 });
  const gun = new THREE.MeshStandardMaterial({ color: 0x1b1c1e, roughness: 0.45, metalness: 0.55 });

  const root = new THREE.Group();
  root.name = "guard";

  // ----- boots (static, in root) -----
  const bootParts = [], soleParts = [];
  for (const sx of [1, -1]) {
    const p = [sx * 0.13, 0, 0], r = [0, sx * 0.12, 0];
    bootParts.push([tf(lathe([[0, 0.05], [0.058, 0.05], [0.061, 0.12], [0.057, 0.2], [0.054, 0.215], [0, 0.216]], 16, 0.9), p, r), black]);
    bootParts.push([tf(ell(0.054, 0.058, 0.15, [0, 0.055, 0.045]), p, r), black]);
    bootParts.push([tf(ell(0.045, 0.02, 0.06, [0, 0.09, 0.11], [0.3, 0, 0]), p, r), black]); // laces/tongue ridge
    soleParts.push([tf(rbox(0.118, 0.034, 0.31, 0.012, [0, 0.017, 0.045]), p, r), rubber]);
    soleParts.push([tf(rbox(0.11, 0.024, 0.09, 0.008, [0, 0.04, -0.06]), p, r), rubber]); // heel block
  }
  seg(root, [...bootParts, ...soleParts], { o: [0, 0, 0] });

  // ----- hips (pelvis + belt + holster) -----
  const hips = grp(root, [0, 0.98, 0]);
  const pelvisProf = folds([[0, 0.855], [0.05, 0.86], [0.11, 0.88], [0.155, 0.92], [0.172, 0.99], [0.162, 1.06], [0.15, 1.12], [0, 1.13]], [0.995, 1.02]);
  seg(hips, [
    [tf(lathe(pelvisProf, 24, 0.68), [0, -0.98, 0]), suit],
    [tf(lathe([[0.168, 1.035], [0.176, 1.04], [0.176, 1.085], [0.168, 1.09]], 24, 0.7), [0, -0.98, 0]), black],
    [box(0.07, 0.05, 0.02, [0, 0.083, 0.122]), gun], // buckle
    [box(0.07, 0.15, 0.1, [-0.185, -0.02, 0.02], [0, 0, 0.12]), black], // holster
    [box(0.075, 0.04, 0.105, [-0.18, 0.06, 0.02], [0, 0, 0.12]), black], // holster flap
    [box(0.1, 0.09, 0.05, [0.12, 0.04, -0.12], [0, 0.5, 0]), black], // rear pouch
  ], { o: [0, 0.98, 0] });

  // ----- legs -----
  const legs = [];
  for (const sx of [1, -1]) {
    const thigh = grp(hips, [sx * 0.095, -0.05, 0]);
    seg(thigh, [[lathe(folds([[0, -0.47], [0.058, -0.47], [0.064, -0.43], [0.068, -0.38], [0.08, -0.22], [0.092, -0.08], [0.09, 0], [0, 0.06]], [-0.36, -0.395]), 18, 0.95), suit]], { o: [sx * 0.095, 0.93, 0] });
    const shin = grp(thigh, [0, -0.43, 0]);
    seg(shin, [
      [ell(0.06, 0.06, 0.062, [0, 0, 0.008]), suit], // knee
      [lathe(folds([[0, -0.34], [0.06, -0.34], [0.069, -0.31], [0.063, -0.24], [0.058, -0.15], [0.064, -0.05], [0.066, 0], [0, 0.02]], [-0.07, -0.1, -0.29]), 18), suit],
      [box(0.09, 0.11, 0.02, [sx * 0.05, 0.12, 0.02], [0, sx * 1.2, 0]), trim], // thigh cargo pocket (on shin's top = lower thigh)
    ], { o: [sx * 0.095, 0.5, 0] });
    legs.push({ thigh, shin, ankle: new V(sx * 0.13, 0.09, 0) });
  }

  // ----- spine / chest -----
  const spine = grp(hips, [0, 0.08, 0]); // world y 1.06
  const chestMesh = grp(spine);
  const chestProf = [[0, 1.0], [0.15, 1.0], [0.149, 1.08], [0.152, 1.12], [0.156, 1.16], [0.172, 1.26], [0.185, 1.34], [0.19, 1.4], [0.176, 1.45], [0.13, 1.49], [0.075, 1.52], [0.06, 1.55], [0, 1.56]];
  const zipPts = [];
  for (let y = 1.09; y <= 1.5; y += 0.03) zipPts.push(new V(0, y - 1.06, profR(chestProf, y) * 0.62 + 0.002));
  const chestParts = [
    [tf(lathe(folds(chestProf, [1.105, 1.135]), 28, 0.62), [0, -1.06, 0]), suit],
    [tf(new THREE.TubeGeometry(new THREE.CatmullRomCurve3(zipPts), 16, 0.007, 6), [0, 0, 0], [0, 0, 0], [1, 1, 0.45]), trim], // flat zipper placket
    [box(0.014, 0.03, 0.008, [0, 0.43, 0.06]), gun], // zipper pull
    [tf(lathe([[0.1, 1.41], [0.15, 1.44], [0.12, 1.5], [0.1, 1.56], [0.085, 1.6]], 20, 0.85, 0.75, Math.PI * 2 - 1.5), [0, -1.06, -0.01]), suit], // hood drape onto shoulders
    [tf(lathe([[0.072, 1.5], [0.078, 1.52], [0.074, 1.565], [0.068, 1.57]], 16, 0.95), [0, -1.06, 0.01]), trim], // collar
  ];
  for (const sx of [1, -1]) {
    chestParts.push([rbox(0.085, 0.1, 0.012, 0.005, [sx * 0.085, 0.27, 0.102], [0.08, sx * 0.38, 0]), trim]); // chest pocket
    chestParts.push([rbox(0.09, 0.03, 0.016, 0.006, [sx * 0.085, 0.315, 0.106], [0.08, sx * 0.38, 0]), trim]); // flap
  }
  seg(chestMesh, chestParts, { o: [0, 1.06, 0] });
  const chest = spine; // arms + head hang off the (unscaled) spine bone

  // ----- head -----
  const head = grp(chest, [0, 0.47, 0]); // neck, world y 1.53
  const hoodG = new THREE.SphereGeometry(1, 28, 20);
  { // cut the face opening and the neck hole out of the hood shell
    const p = hoodG.attributes.position, keep = [], ix = hoodG.index.array;
    for (let i = 0; i < ix.length; i += 3) {
      let cx = 0, cy = 0, cz = 0;
      for (let k = 0; k < 3; k++) { cx += p.getX(ix[i + k]) / 3; cy += p.getY(ix[i + k]) / 3; cz += p.getZ(ix[i + k]) / 3; }
      const face = cz > 0.2 && (cx / 0.66) ** 2 + ((cy + 0.08) / 0.78) ** 2 < 1;
      if (!face && cy > -0.93) keep.push(ix[i], ix[i + 1], ix[i + 2]);
    }
    hoodG.setIndex(keep);
    tf(hoodG, [0, 0.135, -0.012], [0, 0, 0], [0.122, 0.142, 0.132]);
  }
  const xParts = [];
  const maskC = [0, 0.125, 0.035], maskR = [0.082, 0.108, 0.075];
  for (const a of [Math.PI / 4, -Math.PI / 4]) { // white X, bent onto the mask surface at eye level
    const g = tf(new THREE.BoxGeometry(0.078, 0.013, 0.006, 10, 1, 1), [0, 0.012, 0], [0, 0, a]);
    const p = g.attributes.position;
    for (let i = 0; i < p.count; i++) {
      const x = p.getX(i), yy = p.getY(i); // relative to mask centre (X sits 12 mm above it)
      const s = maskR[2] * Math.sqrt(Math.max(0, 1 - (x / maskR[0]) ** 2 - (yy / maskR[1]) ** 2));
      p.setXYZ(i, x + maskC[0], yy + maskC[1], maskC[2] + s + (p.getZ(i) > 0 ? 0.0025 : -0.002));
    }
    g.computeVertexNormals();
    xParts.push([g, white]);
  }
  seg(head, [
    [hoodG, hoodMat],
    [tf(new THREE.TorusGeometry(1, 0.15, 8, 36), [0, 0.135 - 0.08 * 0.142, -0.012 + 0.69 * 0.132], [0, 0, 0], [0.66 * 0.122, 0.78 * 0.142, 0.11]), trim], // hood hem
    [ell(maskR[0], maskR[1], maskR[2], maskC, [0, 0, 0], 32, 24), mask],
    [ell(0.045, 0.05, 0.05, [0, 0.03, 0.0]), black], // neck under hood
    ...xParts,
  ], { o: [0, 1.53, 0] });

  // ----- rifle (bore axis = local +Z through origin, origin at the butt) -----
  const rifle = grp(root);
  rifle.name = "rifle";
  const MUZZLE = new V(0, 0, 1.0);
  seg(rifle, [
    [rbox(0.042, 0.11, 0.05, 0.01, [0, -0.04, 0.02]), gun], // butt pad
    [rbox(0.036, 0.06, 0.22, 0.01, [0, -0.025, 0.14], [0.08, 0, 0]), gun], // stock
    [cyl(0.014, 0.014, 0.2, [0, 0.0, 0.16], [Math.PI / 2, 0, 0], 8), gun], // buffer tube
    [rbox(0.05, 0.07, 0.3, 0.008, [0, -0.005, 0.38]), gun], // receiver
    [rbox(0.034, 0.105, 0.042, 0.01, [0, -0.085, 0.33], [0.3, 0, 0]), gun], // pistol grip
    [rbox(0.026, 0.16, 0.065, 0.008, [0, -0.115, 0.465], [-0.25, 0, 0]), gun], // magazine
    [rbox(0.012, 0.02, 0.07, 0.004, [0, -0.045, 0.34]), gun], // trigger guard
    [cyl(0.027, 0.027, 0.33, [0, 0, 0.645], [Math.PI / 2, 0, Math.PI / 8], 8), gun], // handguard
    [box(0.022, 0.012, 0.56, [0, 0.036, 0.52]), gun], // top rail
    [cyl(0.0095, 0.0095, 0.17, [0, 0, 0.9], [Math.PI / 2, 0, 0], 8), gun], // barrel
    [cyl(0.015, 0.015, 0.06, [0, 0, 0.97], [Math.PI / 2, 0, 0], 8), gun], // muzzle device
    [cyl(0.02, 0.02, 0.13, [0, 0.07, 0.4], [Math.PI / 2, 0, 0], 12), gun], // optic tube
    [box(0.024, 0.03, 0.06, [0, 0.05, 0.4]), gun], // optic mount
    [box(0.006, 0.04, 0.012, [0, 0.055, 0.79]), gun], // front sight post
    [tf(new THREE.CircleGeometry(0.0085, 14), [0, 0, 1.0006]), rubber], // dark bore
  ]);

  // gloved hands live on the rifle so they always wrap it; forearms are IK'd to these wrists
  const grip = new THREE.Group(); grip.position.set(0, -0.09, 0.33); grip.rotation.x = 0.3; rifle.add(grip);
  const knuckles = [];
  for (let k = 0; k < 4; k++) knuckles.push([ell(0.012, 0.011, 0.012, [-0.012, 0.03 - k * 0.02, 0.036]), black]);
  seg(grip, [
    [rbox(0.056, 0.088, 0.07, 0.02, [-0.008, -0.004, 0.006]), black], // fist closed round the grip
    [rbox(0.046, 0.06, 0.075, 0.018, [-0.026, -0.03, -0.05], [0.5, 0, 0]), black], // back of hand to wrist
    [cap(0.0125, 0.035, [0.022, 0.034, -0.004], [1.45, 0, 0]), black], // thumb across the left side
    ...knuckles,
  ]);
  const sup = new THREE.Group(); sup.position.set(0, 0, 0.62); rifle.add(sup);
  seg(sup, [
    [rbox(0.056, 0.028, 0.085, 0.012, [0.004, -0.04, 0]), black], // palm under the handguard
    [rbox(0.022, 0.056, 0.082, 0.01, [0.034, -0.014, 0.004], [0, 0, -0.15]), black], // fingers up the left side
    [rbox(0.046, 0.045, 0.06, 0.016, [0.016, -0.066, -0.05], [-0.5, 0, 0]), black], // heel of hand
    [cap(0.0115, 0.04, [-0.03, -0.02, 0.012], [Math.PI / 2, 0, 0]), black], // thumb along the right side
  ]);
  const wristR = new THREE.Object3D(), wristL = new THREE.Object3D();
  wristR.position.set(-0.028, -0.055, -0.085); grip.add(wristR);
  wristL.position.set(0.02, -0.085, -0.085); sup.add(wristL);

  // ----- arms -----
  const L1 = 0.31, L2 = 0.29;
  const arms = [];
  for (const sx of [1, -1]) {
    const upper = grp(chest, [sx * 0.18, 0.38, -0.01]);
    seg(upper, [
      [ell(0.062, 0.062, 0.06, [0, -0.01, 0]), suit], // deltoid
      [lathe(folds([[0, -0.335], [0.045, -0.335], [0.05, -0.3], [0.053, -0.25], [0.058, -0.12], [0.06, -0.04], [0.05, 0.02], [0, 0.03]], [-0.27, -0.295]), 16), suit],
    ], { o: [sx * 0.18, 1.44, 0], limb: true });
    const lower = grp(upper, [0, -L1, 0]);
    seg(lower, [
      [ell(0.046, 0.046, 0.046), suit], // elbow
      [lathe(folds([[0, -0.23], [0.043, -0.23], [0.046, -0.2], [0.05, -0.1], [0.049, -0.02], [0, 0.01]], [-0.045, -0.075]), 16), suit],
      [lathe([[0.046, -0.235], [0.05, -0.23], [0.05, -0.205], [0.046, -0.2]], 16), trim], // sleeve cuff
      [cyl(0.034, 0.04, 0.06, [0, -0.265, 0], [0, 0, 0], 12), black], // glove gauntlet
    ], { o: [sx * 0.18, 1.13, 0], limb: true });
    arms.push({ upper, lower, sx });
  }

  // ----- shadow proxy: one low-poly mesh, re-posed on the CPU each frame, invisible but casting -----
  const pc = (r, len, p, sc = [1, 1, 1]) => tf(new THREE.CapsuleGeometry(r, len, 2, 6), p, [0, 0, 0], sc);
  const proxyDefs = [
    [hips, pc(0.13, 0.1, [0, 0.0, 0], [1, 1, 0.7])],
    [spine, pc(0.15, 0.2, [0, 0.24, 0], [1, 1, 0.62])],
    [head, tf(new THREE.SphereGeometry(1, 8, 6), [0, 0.12, -0.01], [0, 0, 0], [0.11, 0.13, 0.115])],
    [rifle, tf(new THREE.BoxGeometry(0.04, 0.08, 1.0), [0, -0.02, 0.5])],
    [rifle, tf(new THREE.BoxGeometry(0.03, 0.14, 0.06), [0, -0.1, 0.44])],
    ...legs.flatMap((L) => [[L.thigh, pc(0.07, 0.32, [0, -0.22, 0])], [L.shin, pc(0.055, 0.32, [0, -0.2, 0])]]),
    ...arms.flatMap((a) => [[a.upper, pc(0.045, 0.24, [0, -0.15, 0])], [a.lower, pc(0.04, 0.22, [0, -0.15, 0])]]),
  ];
  for (const sx of [1, -1]) proxyDefs.push([root, tf(new THREE.BoxGeometry(0.1, 0.2, 0.28), [sx * 0.13, 0.1, 0.04])]);
  const proxyGeo = merge(proxyDefs.map(([, g]) => g));
  proxyGeo.deleteAttribute("color"); proxyGeo.deleteAttribute("uv");
  const proxyRest = proxyGeo.attributes.position.array.slice();
  const proxyRanges = []; // [bone, firstVertex, count]
  { let o = 0; for (const [b, g] of proxyDefs) { const c = g.attributes.position.count; proxyRanges.push([b, o, c]); o += c; } }
  const proxy = new THREE.Mesh(proxyGeo, new THREE.MeshBasicMaterial({ colorWrite: false, depthWrite: false }));
  proxy.castShadow = true; proxy.receiveShadow = false; proxy.frustumCulled = false; proxy.name = "shadowProxy";
  root.add(proxy);
  const _inv = new THREE.Matrix4(), _bm = new THREE.Matrix4(), _pv = new V();
  function updateProxy() {
    _inv.copy(root.matrixWorld).invert();
    const P = proxyGeo.attributes.position;
    for (const [b, o, c] of proxyRanges) {
      _bm.multiplyMatrices(_inv, b.matrixWorld);
      for (let i = o; i < o + c; i++) { _pv.fromArray(proxyRest, i * 3).applyMatrix4(_bm); P.setXYZ(i, _pv.x, _pv.y, _pv.z); }
    }
    P.needsUpdate = true;
  }

  // ----- state -----
  const target = new V(0, 1.5, 5);
  let targetSet = false, aimT = 0, aim = 0, kick = 0, yaw = 0, pitch = 0, held = false, tFreeze = null;
  const qLow = new THREE.Quaternion(), qAim = new THREE.Quaternion(), qR = new THREE.Quaternion(), qRoot = new THREE.Quaternion();
  const butt = new V(), buttAim = new V(), dir = new V(), up = new V(), tLocal = new V(), tmp = new V(), pole = new V();
  const swayE = new THREE.Euler();

  function update(dt = 1 / 60, t = 0) {
    if (held) t = tFreeze ??= t; else tFreeze = null; // hold: idle motion freezes at this instant
    const k = 1 - Math.exp(-dt * (held ? 25 : 6)); // hold: head locks onto the target
    aim += (aimT - aim) * (1 - Math.exp(-dt * 5));
    kick = Math.max(0, kick - dt / 0.25);
    const rc = kick * kick;

    root.updateMatrixWorld(true);
    tLocal.copy(targetSet ? target : root.localToWorld(tmp.set(0, 1.5, 5)));
    root.worldToLocal(tLocal);
    const dx = tLocal.x, dy = tLocal.y - 1.6, dz = tLocal.z;
    const yawT = THREE.MathUtils.clamp(Math.atan2(dx, dz), -1.3, 1.3);
    const pitchT = THREE.MathUtils.clamp(Math.atan2(dy, Math.hypot(dx, dz)), -0.7, 0.6);
    yaw += (yawT - yaw) * k;
    pitch += (pitchT - pitch) * k;

    // body: weight shift, breathing, blading into the aim
    const br = Math.sin((t * Math.PI * 2) / 4.2);
    hips.position.set(0.012 * Math.sin(t * 0.45), 0.98 - 0.003 * (1 + Math.sin(t * 0.9)), 0);
    hips.rotation.set(0, 0, 0.018 * Math.sin(t * 0.45));
    const spineYaw = yaw * (0.25 + 0.55 * aim) - 0.32 * aim;
    spine.rotation.set(0.07 * aim + 0.02 * br * 0 - 0.08 * rc, spineYaw, -hips.rotation.z * 0.8, "YXZ");
    chestMesh.scale.set(1 + 0.01 * br, 1, 1 + 0.018 * br);
    for (const a of arms) a.upper.position.y = 0.38 + 0.004 * br;
    head.rotation.set(
      -(pitch - 0.07 * aim) + 0.13 * aim - 0.05 * rc,
      THREE.MathUtils.clamp(yaw - spineYaw, -1.0, 1.0),
      0, "YXZ"); // head stays level
    root.updateMatrixWorld(true);

    // legs: feet planted, knees forward
    for (const L of legs) {
      root.localToWorld(_W.copy(L.ankle));
      L.thigh.getWorldPosition(pole);
      root.localToWorld(tmp.set(0, 0, 1)).sub(root.localToWorld(_A.set(0, 0, 0)));
      pole.addScaledVector(tmp, 1).y -= 0.3;
      solveTwoBone(L.thigh, L.shin, 0.43, 0.42, _W.clone(), pole.clone());
    }

    // rifle: blend low ready -> shouldered on target
    chest.getWorldQuaternion(qR);
    up.set(0, 1, 0).applyQuaternion(qR);
    chest.localToWorld(butt.set(-0.13, 0.33 + 0.003 * br, 0.13)); // low ready: butt in the shoulder pocket, muzzle down
    dir.set(0.3, -0.7, 0.65).applyQuaternion(qR);
    swayE.set(0.02 * Math.sin(t * 0.7), 0.025 * Math.sin(t * 0.53 + 1), 0);
    dir.applyEuler(swayE);
    basisLook(dir, up, qLow);
    chest.localToWorld(buttAim.set(-0.13, 0.43, 0.1)); // shoulder pocket
    const tw = root.localToWorld(tmp.copy(tLocal));
    dir.subVectors(tw, buttAim);
    basisLook(dir, up, qAim);
    butt.lerp(buttAim, aim);
    qR.slerpQuaternions(qLow, qAim, aim);
    if (rc > 0) {
      butt.addScaledVector(dir.set(0, 0, 1).applyQuaternion(qR), -0.05 * rc);
      qR.multiply(_q.setFromAxisAngle(_x.set(1, 0, 0), -0.14 * rc));
    }
    rifle.position.copy(root.worldToLocal(butt.clone()));
    root.getWorldQuaternion(qRoot);
    rifle.quaternion.copy(qRoot.invert().multiply(qR));
    sup.position.z = 0.53 + 0.04 * aim; // support hand slides along the handguard
    rifle.updateMatrixWorld(true);

    // arms reach the wrists on the rifle; elbows out and down
    chest.getWorldQuaternion(qR);
    for (const a of arms) {
      const w = (a.sx < 0 ? wristR : wristL).getWorldPosition(new V());
      a.upper.getWorldPosition(pole);
      pole.add(tmp.set(a.sx * (0.2 + 0.4 * aim), -0.8 + 0.45 * aim, -0.3 + 0.15 * aim).applyQuaternion(qR));
      solveTwoBone(a.upper, a.lower, L1, L2, w, pole.clone());
      root.userData.ikErr = Math.max(a.sx > 0 ? 0 : root.userData.ikErr, a.lower.localToWorld(tmp.set(0, -L2, 0)).distanceTo(w)); // debug: wrist gap
    }
    updateProxy();
  }

  return {
    root,
    update,
    lookAt(v) { target.copy(v); targetSet = true; },
    setAim(a) { aimT = THREE.MathUtils.clamp(a, 0, 1); },
    fire() { kick = 1; },
    hold(on) { held = !!on; },
    muzzleWorld(out = new V()) { rifle.updateMatrixWorld(true); return rifle.localToWorld(out.copy(MUZZLE)); },
  };
}
