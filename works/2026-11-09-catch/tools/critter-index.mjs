// Writes app/src/main/assets/critters/index.json from site/js/creatures.js SPECS without importing it
// (creatures.js imports three.js from a CDN, which node cannot load). The game's critters = groups fluff + fluff2.
import { readFileSync, writeFileSync } from "node:fs";
const src = readFileSync(new URL("../site/js/creatures.js", import.meta.url), "utf8");
const re = /\{\s*id:\s*"([^"]+)",\s*name:\s*"([^"]+)",\s*style:\s*"[^"]+",\s*group:\s*"(fluff2?)",\s*color:\s*"(#[0-9a-fA-F]{6})"/g;
const out = [...src.matchAll(re)].map(m => ({ id: m[1], name: m[2], type: m[3], color: m[4] }));
if (out.length !== 16) throw new Error(`expected 16 critters, got ${out.length}`);
writeFileSync(new URL("../app/app/src/main/assets/critters/index.json", import.meta.url), JSON.stringify(out, null, 1) + "\n");
console.log(out.length, out.map(c => c.name).join(" "));
