import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { serialize, parse, fileName } from "./petfile.js";

const V = JSON.parse(readFileSync(new URL("../test/petfile-vector.json", import.meta.url), "utf8"));

test("parse: the shared vector keeps what Kotlin must keep", () => {
  const r = parse(JSON.stringify(V.file));
  assert.deepEqual(r.creatures.map(c => ({ id: c.id, name: c.name, kind: c.kind, no: c.no, labels: c.poses.map(p => p.label) })), V.expect.creatures);
  assert.deepEqual(r.dropped, { poses: V.expect.dropped.poses, creatures: V.expect.dropped.creatures });
  // the kept picture is the first of its label
  assert.equal(r.creatures[0].poses[0].png, V.file.creatures[0].poses[0].png);
});

test("parse: refuses a wrong format, version, missing list or non-JSON", () => {
  for (const bad of V.reject) assert.throws(() => parse(JSON.stringify(bad)));
  assert.throws(() => parse("{nope"));
});

test("serialize -> parse round trip", () => {
  const png = V.file.creatures[0].poses[0].png;
  const creatures = [
    { id: "c_9", name: "멍멍이 1", kind: "dog", no: 4, pokeId: null, poses: [{ label: "front", png, w: 2, h: 2 }, { label: "lying", png, w: 2, h: 2 }] },
    { id: "p_1", name: "이상해씨", kind: "pokemon", no: 5, pokeId: 1, poses: [{ label: "front", png, w: 2, h: 2 }] },
  ];
  const text = serialize(creatures, new Date("2026-11-09T12:00:00Z"));
  const o = JSON.parse(text);
  assert.equal(o.format, "dotpet"); assert.equal(o.v, 1); assert.equal(o.exported, "2026-11-09T12:00:00.000Z");
  const r = parse(text);
  assert.deepEqual(r.creatures, creatures);
  assert.deepEqual(r.dropped, { poses: 0, creatures: 0 });
});

test("fileName: local date", () => {
  assert.equal(fileName(new Date(2026, 10, 9, 23, 59)), "우리집몬스터-20261109.dotpet");
  assert.equal(fileName(new Date(2027, 0, 2)), "우리집몬스터-20270102.dotpet");
});
