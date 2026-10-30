// node --test site/js/adgate.test.mjs : the Toss "광고 보는 동안 만들기" gate (PLAN-toss.md Task 8, review item 7)
import { test } from "node:test";
import assert from "node:assert/strict";
import { adOk, needsRetry, start, step } from "./adgate.js";

const run = (...evs) => evs.reduce(step, start());

test("nothing yet: no save, no retry line", () => { assert.equal(adOk(start()), false); assert.equal(needsRetry(start()), false); });
test("reward earned -> save allowed", () => assert.equal(adOk(run("earned")), true));
test("closed before the reward -> no save, retry line", () => {
  const s = run("dismissed"); assert.equal(adOk(s), false); assert.equal(needsRetry(s), true);
});
test("earned then closed (the normal end of an ad) stays earned", () => {
  const s = run("earned", "dismissed"); assert.equal(adOk(s), true); assert.equal(needsRetry(s), false);
});
test("no ad to show (load or show failed) never blocks saving", () => {
  assert.equal(adOk(run("failed")), true);
  assert.equal(adOk(run("dismissed", "retry", "failed")), true);
});
test("retry hides the line until the next ad ends", () => {
  const s = run("dismissed", "retry"); assert.equal(needsRetry(s), false); assert.equal(adOk(s), false);
  assert.equal(adOk(step(s, "earned")), true);
});
test("unknown events change nothing", () => assert.deepEqual(run("earned", "loaded"), run("earned")));
