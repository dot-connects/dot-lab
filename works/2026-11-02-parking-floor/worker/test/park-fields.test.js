import { test } from "node:test";
import assert from "node:assert/strict";
import { cleanPark } from "../src/core.js";

// 1.1.1 (mockups/v5-listen): half and named floors, memo = the spoken sentence (<= 80).
const C = "c_00000001";
const park = (over = {}) => ({ id: 1000, carId: C, atMs: 1000, floor: "B2", state: "PARKED", ...over });
const ok = over => cleanPark(park(over), C, 2000) !== null;

test("floor wire: old and 1.1.1 floors pass, anything else is refused", () => {
  for (const f of ["B1", "B9", "1F", "9F", "ROOF", "B1.5", "2.5F", "M", "MZ2", "G", "L1", "L9", "P1", "P9", null]) assert.ok(ok({ floor: f }), f);
  for (const f of ["B0", "B10", "0F", "B1.6", "B1x5", "2.5", "MZ3", "M2", "L0", "P", "roof", "2층", ""]) assert.ok(!ok({ floor: f }), f);
});

test("memo up to 80 characters", () => {
  assert.ok(ok({ memo: "가".repeat(80) }));
  assert.ok(!ok({ memo: "가".repeat(81) }));
});
