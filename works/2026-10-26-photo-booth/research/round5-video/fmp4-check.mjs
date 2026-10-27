// Self-check of compose.js readFmp4 on untrusted input: real clips parse; truncated, bit-flipped and size-forged
// copies never throw or hang, and every sample they return lies inside the file. node fmp4-check.mjs clip.mp4 ...
import fs from "node:fs";
const src = fs.readFileSync(new URL("../../site/js/compose.js", import.meta.url), "utf8");
const code = src.slice(src.indexOf("const MAX_SAMPLES"), src.indexOf("// One clip decoded frame by frame"));
const readFmp4 = new Function(code.replace("export function readFmp4", "function readFmp4") + "; return readFmp4;")();
const ok = (r, len) => r === null || (r.samples.length <= 1200 && r.samples.every(s => s.data.byteOffset + s.data.length <= len && s.data.length > 0));
let rnd = 1; const R = () => (rnd = (rnd * 1103515245 + 12345) % 2 ** 31) / 2 ** 31;
for (const f of process.argv.slice(2)) {
  const b = fs.readFileSync(f), ab = () => b.buffer.slice(b.byteOffset, b.byteOffset + b.length);
  const r = readFmp4(ab());
  console.assert(r && r.samples.length > 0, "real clip parses", f);
  let n = 0, nul = 0;
  for (let k = 0; k < 400; k++) {
    const c = new Uint8Array(ab());
    if (k % 4 === 0) { const cut = Math.floor(R() * c.length); const t = c.slice(0, cut); const x = readFmp4(t.buffer); console.assert(ok(x, cut), "trunc"); nul += !x; n++; continue; }
    // flip bytes, biased to the box headers / tables at the front (moov, moof, trun)
    for (let j = 0; j < 4; j++) c[Math.floor(R() ** 3 * Math.min(c.length, 4000))] = Math.floor(R() * 256);
    if (k % 4 === 1) { const p = Math.floor(R() * Math.min(c.length - 4, 2000)); new DataView(c.buffer).setUint32(p, 0xffffffff * R()); }
    const x = readFmp4(c.buffer); console.assert(ok(x, c.length), "fuzz"); nul += !x; n++;
  }
  console.log(f.split(/[\\/]/).pop(), "samples", r.samples.length, r.config.codec, `${r.config.codedWidth}x${r.config.codedHeight}`, `fuzz ${n} ok, ${nul} -> null`);
}
