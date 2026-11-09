import test from "node:test";
import assert from "node:assert/strict";
import { LABELS, moves, count, suggest, poseOutcome } from "./moves.js";

test("moves: union of what each label unlocks", () => {
  assert.deepEqual(moves([]), []);
  assert.deepEqual(moves(["front"]), ["bounce", "flinch"]);
  assert.deepEqual(moves(["back"]), ["bounce", "flinch", "hide"]);
  assert.deepEqual(moves(["side"]), ["bounce", "flinch", "walk"]);
  assert.deepEqual(moves(["walk"]), ["bounce", "flinch"]);   // walk alone unlocks run only together with side
  assert.deepEqual(moves(["side", "walk"]), ["bounce", "flinch", "walk", "run"]);
  assert.deepEqual(moves(["lying"]), ["bounce", "flinch", "sleep"]);
  assert.deepEqual(moves(LABELS), ["bounce", "flinch", "walk", "run", "sleep", "hide"]);
  assert.deepEqual(moves(["front", "front", "nope"]), ["bounce", "flinch"]);
});

test("count: n/5 for real ones, n/1 for pokemon, unknown and repeated labels ignored", () => {
  assert.equal(count(["front", "side"], "dog"), "모습 2/5");
  assert.equal(count(["front", "front", "fly"], "cat"), "모습 1/5");
  assert.equal(count(["front"], "pokemon"), "모습 1/1");
});

test("suggest: from the box shape", () => {
  assert.equal(suggest(170, 100), "lying");
  assert.equal(suggest(161, 100), "lying");
  assert.equal(suggest(160, 100), "side");
  assert.equal(suggest(116, 100), "side");
  assert.equal(suggest(115, 100), "front");
  assert.equal(suggest(60, 100), "front");
});

test("poseOutcome: new pose, better grade replaces, same or worse keeps", () => {
  assert.equal(poseOutcome(undefined, "hit"), "new");
  assert.equal(poseOutcome("hit", "nice"), "better");
  assert.equal(poseOutcome("great", "excellent"), "better");
  assert.equal(poseOutcome("great", "great"), "have");
  assert.equal(poseOutcome("excellent", "nice"), "have");
});
