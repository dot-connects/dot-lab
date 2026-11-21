import { test, after } from "node:test";
import assert from "node:assert/strict";
import { setup } from "./harness.js";

const h = await setup();
after(() => h.close());

test("a group written through D1Store reads back from local D1", async () => {
  await h.store.addGroup({ id: "g1", created: 5, name: "Home" });
  assert.deepEqual(await h.store.group("g1"), { id: "g1", name: "Home", created: 5 });
  await h.reset();
  assert.equal(await h.store.group("g1"), null);
});
