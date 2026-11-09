// The dex on the phone: IndexedDB "dotcatch" v1 (creatures, poses by creatureId, meta.nextNo), with an in-memory
// fallback when the browser will not open it (the page then says the dex is not kept).
import { poseOutcome } from "./moves.js";

const req = r => new Promise((ok, no) => { r.onsuccess = () => ok(r.result); r.onerror = () => no(r.error); });

class Mem {
  constructor() { this.s = { creatures: new Map(), poses: new Map(), meta: new Map() }; }
  async get(st, k) { return this.s[st].get(k); }
  async put(st, v, k) { this.s[st].set(k ?? v.id, v); }
  async all(st) { return [...this.s[st].values()]; }
}

class Idb {
  constructor(db) { this.db = db; }
  tx(st, mode) { return this.db.transaction(st, mode).objectStore(st); }
  get(st, k) { return req(this.tx(st, "readonly").get(k)); }
  put(st, v, k) { return req(k === undefined ? this.tx(st, "readwrite").put(v) : this.tx(st, "readwrite").put(v, k)); }
  all(st) { return req(this.tx(st, "readonly").getAll()); }
}

function openDb() {
  return new Promise((ok, no) => {
    const r = indexedDB.open("dotcatch", 1);
    r.onupgradeneeded = () => {
      const db = r.result;
      db.createObjectStore("creatures", { keyPath: "id" });
      db.createObjectStore("poses", { keyPath: "id" }).createIndex("creatureId", "creatureId");
      db.createObjectStore("meta");
    };
    r.onsuccess = () => ok(r.result); r.onerror = () => no(r.error); r.onblocked = () => no(new Error("blocked"));
  });
}

let S = null;
export let saved = false;

/** Open the dex once. Returns true when it is kept on the phone, false when it lives in memory only. */
export async function open() {
  if (S) return saved;
  try { S = new Idb(await openDb()); saved = true; }
  catch { S = new Mem(); saved = false; }
  try { await navigator.storage?.persist?.(); } catch { /* best effort */ }
  return saved;
}

/** {creatures, poses} for the always-visible counter */
export async function counts() {
  const [c, p] = await Promise.all([S.all("creatures"), S.all("poses")]);
  return { creatures: c.length, poses: p.length };
}

async function nextNo() {
  const n = (await S.get("meta", "nextNo")) || 1;
  await S.put("meta", n + 1, "nextNo");
  return n;
}

/**
 * A pokemon caught: the same pokeId is the same creature (no new No., "또 잡았다 x n").
 * Returns {creature, outcome: "new" | "again", pose: "new" | "better" | "have"}.
 */
export async function catchVirtual({ pokeId, name, grade }) {
  const id = `p_${pokeId}`, now = Date.now();
  let c = await S.get("creatures", id), outcome = "again";
  if (!c) { c = { id, name, kind: "pokemon", no: await nextNo(), pokeId, times: 0, first: now }; outcome = "new"; }
  c.times++; c.last = now;
  await S.put("creatures", c);
  const pid = `${id}_front`, had = await S.get("poses", pid), pose = poseOutcome(had?.grade, grade);
  if (pose !== "have") await S.put("poses", { id: pid, creatureId: id, label: "front", grade, at: now });
  return { creature: c, outcome, pose };
}

const posesOf = async id => (await S.all("poses")).filter(p => p.creatureId === id);   // ponytail: full scan, fine for a home dex

/** Real creatures of one kind (dog | cat | person) for the "누구야?" chips, each with its pose labels, oldest first. */
export async function ofKind(kind) {
  const cs = (await S.all("creatures")).filter(c => c.kind === kind).sort((a, b) => a.no - b.no);
  return Promise.all(cs.map(async c => ({ ...c, labels: (await posesOf(c.id)).map(p => p.label) })));
}

/**
 * A real creature caught, picked on the "누구야?" screen: id = an existing one, or null for a new friend (name, kind).
 * The pose keeps the cut-out PNG (Blob); a label already held is replaced only by a better grade.
 * Returns {creature, outcome: "new" | "again", pose: "new" | "better" | "have", labels}.
 */
export async function catchReal({ id, name, kind, label, png, w, h, grade, blurry }) {
  const now = Date.now();
  let c = id ? await S.get("creatures", id) : null, outcome = "again";
  if (!c) {
    c = { id: "c_" + now.toString(36) + Math.random().toString(36).slice(2, 6), name, kind, no: await nextNo(), pokeId: null, times: 0, first: now };
    outcome = "new";
  }
  c.times++; c.last = now;
  await S.put("creatures", c);
  const pid = `${c.id}_${label}`, had = await S.get("poses", pid), pose = poseOutcome(had?.grade, grade);
  if (pose !== "have") await S.put("poses", { id: pid, creatureId: c.id, label, grade, at: now, png, w, h, blurry: !!blurry });
  return { creature: c, outcome, pose, labels: (await posesOf(c.id)).map(p => p.label) };
}
