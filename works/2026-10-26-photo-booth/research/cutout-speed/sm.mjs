import fs from "node:fs";
for (const f of process.argv.slice(3)) for (const o of JSON.parse(fs.readFileSync(f))) {
  const fr = o.firstRun || {}; const ru = Object.entries(o.runs || {}).map(([k, v]) => { const a = Object.values(v).flatMap(x => x.ms.slice(1)).sort((x, y) => x - y); return k + ":" + a[a.length >> 1]; }).join(" ");
  console.log(process.argv[2], f, "| create", o.load?.createMs, "fetch", o.load?.fetchMs, "cRaf", o.createPhase?.rafGap, "| warm", JSON.stringify(o.warmTiny || o.warmReal || ""), "| first", fr.ms, "raf", fr.rafGap, "| med", ru, "|", o.error || "");
}
