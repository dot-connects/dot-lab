// The .dotpet export: the contract stage 2 (Android) reads. Pure, tested in petfile.test.mjs against
// ../test/petfile-vector.json, which the Kotlin parser tests too.
import { LABELS } from "./moves.js";

const PNG_SIG = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a];

/** creatures [{id, name, kind, no, pokeId, poses: [{label, png (base64), w, h}]}] -> file text */
export function serialize(creatures, now = new Date()) {
  return JSON.stringify({ format: "dotpet", v: 1, exported: now.toISOString(), creatures });
}

function isPng(b64) {
  if (typeof b64 !== "string" || !b64) return false;
  let bin;
  try { bin = atob(b64); } catch { return false; }
  return bin.length > 8 && PNG_SIG.every((c, i) => bin.charCodeAt(i) === c);
}

/**
 * File text -> {creatures, dropped: {poses, creatures}}. Throws on a wrong format/version or no creature list.
 * A pose with an unknown label, a broken or non-PNG picture, or a label already taken is dropped; a creature left
 * with no pose is dropped.
 */
export function parse(text) {
  const o = JSON.parse(text);
  if (!o || o.format !== "dotpet" || o.v !== 1 || !Array.isArray(o.creatures)) throw new Error("not a dotpet v1 file");
  const dropped = { poses: 0, creatures: 0 }, creatures = [];
  for (const c of o.creatures) {
    const seen = new Set(), poses = [];
    for (const p of Array.isArray(c.poses) ? c.poses : []) {
      if (!LABELS.includes(p.label) || seen.has(p.label) || !isPng(p.png)) { dropped.poses++; continue; }
      seen.add(p.label);
      poses.push({ label: p.label, png: p.png, w: p.w, h: p.h });
    }
    if (!poses.length) { dropped.creatures++; continue; }
    creatures.push({ id: c.id, name: c.name, kind: c.kind, no: c.no, pokeId: c.pokeId ?? null, poses });
  }
  return { creatures, dropped };
}

/** 우리집몬스터-YYYYMMDD.dotpet, local date */
export function fileName(d = new Date()) {
  const p = n => String(n).padStart(2, "0");
  return `우리집몬스터-${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}.dotpet`;
}
