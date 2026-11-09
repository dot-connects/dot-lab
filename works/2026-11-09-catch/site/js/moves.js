// Poses -> what the floating pet can do (stage 2 reads the same rules). Pure, tested in moves.test.mjs.

export const LABELS = ["front", "side", "walk", "lying", "back"];
export const LABEL_KO = { front: "앞모습", side: "옆모습", walk: "걷는 중", lying: "누운 모습", back: "뒷모습" };
const ORDER = ["bounce", "flinch", "walk", "run", "sleep", "hide"];

/** Unlocked moves = union over the poses held; any pose gives bounce + flinch. */
export function moves(labels) {
  const has = new Set(labels.filter(l => LABELS.includes(l)));
  if (!has.size) return [];
  const m = new Set(["bounce", "flinch"]);
  if (has.has("side")) m.add("walk");
  if (has.has("side") && has.has("walk")) m.add("run");
  if (has.has("lying")) m.add("sleep");
  if (has.has("back")) m.add("hide");
  return ORDER.filter(x => m.has(x));
}

/** "모습 n/5" (a pokemon has one picture: n/1). */
export function count(labels, kind) {
  const n = new Set(labels.filter(l => LABELS.includes(l))).size;
  return `모습 ${n}/${kind === "pokemon" ? 1 : LABELS.length}`;
}

/** Suggested label from the box shape: wide = lying, a bit wide = side, else front (walk and back are never guessed). */
export const suggest = (w, h) => (w / h > 1.6 ? "lying" : w / h > 1.15 ? "side" : "front");

const RANK = { hit: 0, nice: 1, great: 2, excellent: 3 };
/** Catching a label already held: a better grade replaces the picture, same or worse keeps it. */
export const poseOutcome = (had, now) => (had === undefined || had === null ? "new" : RANK[now] > RANK[had] ? "better" : "have");
