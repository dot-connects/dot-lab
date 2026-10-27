// node sum.mjs r1.json [r2.json ...] -> one line per (tag, case): iou fp far fn edge ms, then the tag's mean
import fs from "node:fs";
for (const f of process.argv.slice(2)) for (const o of JSON.parse(fs.readFileSync(f))) {
  if (!o.rows) continue;
  const s = o.still || {};
  console.log(`== ${o.tag} ${o.error || ""} path=${s.path} delegate=${s.delegate} pet=${s.pet} petMs=${s.petMs} ${s.gpuError || ""} warm=${s.warmMs}`);
  for (const r of o.rows) console.log(`  ${r.n.padEnd(11)} iou ${r.iou.toFixed(3)}  fp ${String(r.fp).padStart(5)}  far ${String(r.far).padStart(5)}  fn ${String(r.fn).padStart(5)}  edge ${r.edge.toFixed(3)}  ${r.ms} ms`);
  const m = k => (o.rows.reduce((a, r) => a + r[k], 0) / o.rows.length);
  if (o.rows.length) console.log(`  ${"MEAN".padEnd(11)} iou ${m("iou").toFixed(3)}  fp ${m("fp").toFixed(2).padStart(5)}  far ${m("far").toFixed(2).padStart(5)}  fn ${m("fn").toFixed(2).padStart(5)}  edge ${m("edge").toFixed(3)}  ${Math.round(m("ms"))} ms`);
}
