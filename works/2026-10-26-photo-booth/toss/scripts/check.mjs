// Gate before `ait build` (PLAN-toss.md review items 4, 18, 33): the Toss seam is in, our own code runs nothing remote,
// only the test ad id, and the unpacked bundle stays under the 100 MB limit.
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";

const OUT = "dist", bad = [];
const walk = d => readdirSync(d).flatMap(f => { const p = join(d, f); return statSync(p).isDirectory() ? walk(p) : [p]; });
const files = walk(OUT);

const plat = readFileSync(join(OUT, "js/platform.js"), "utf8");
if (!plat.includes('"toss"')) bad.push("dist/js/platform.js is not the Toss build");
// a test build (-test appName) keeps the test ad; a release build must be given the real one (AIT_AD_ID)
const release = !/-test$/.test(process.env.AIT_APP_NAME ?? "ttarogachi-test");
if (release && !process.env.AIT_AD_ID) bad.push("release build needs AIT_AD_ID");
if (!plat.includes("ait-ad-test-rewarded-id")) bad.push("test ad id missing");

// our own files: no eval, no remote module/script/fetch except the Worker; fonts are bundled
const OK = /^https:\/\/(dot-lab-booth\.dotconnects\.workers\.dev)/;
for (const f of files.filter(f => /\.(js|html|css)$/.test(f) && !/[\\/]vendor[\\/]/.test(f) && !f.endsWith(join("js", "platform.js")))) {
  const s = readFileSync(f, "utf8");
  if (/\beval\(|new Function\(/.test(s)) bad.push(`${f}: eval / new Function`);
  for (const m of s.matchAll(/(?:import\(|importScripts\(|fetch\(|<script[^>]*src=|<link[^>]*href=|@import\s+(?:url\()?)\s*["'`](https?:\/\/[^"'`]+)/g))
    if (!OK.test(m[1])) bad.push(`${f}: remote ${m[1]}`);
  for (const m of s.matchAll(/["'`](https:\/\/(?:cdn\.jsdelivr\.net|storage\.googleapis\.com)[^"'`]*)/g)) bad.push(`${f}: CDN url ${m[1]}`);
}

const size = d => files.filter(f => f.startsWith(d)).reduce((n, f) => n + statSync(f).size, 0);
const MB = n => (n / 1048576).toFixed(1) + " MB";
for (const d of ["dist/vendor/ort", "dist/vendor/mediapipe", "dist/models", "dist/js", "dist"]) console.log(d.padEnd(24), MB(size(join(...d.split("/")))));
if (size(OUT) > 100 * 1048576) bad.push("bundle over 100 MB");

if (bad.length) { console.error("check FAILED\n " + bad.join("\n ")); process.exit(1); }
console.log("check ok");
