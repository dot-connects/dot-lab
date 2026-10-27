// Bench worker (research only). { id, load:{url, opts, ep, bytesMode}, } / { id, img, w, h, half } / { id, ping }
import * as ort from "https://cdn.jsdelivr.net/npm/onnxruntime-web@1.22.0/dist/ort.webgpu.min.mjs";
ort.env.wasm.wasmPaths = "https://cdn.jsdelivr.net/npm/onnxruntime-web@1.22.0/dist/";
ort.env.logLevel = "error";
let sess, inName, outName, isHalf = false;
const f32 = new Float32Array(1), u32 = new Uint32Array(f32.buffer);
function toHalf(v) { f32[0] = v; const x = u32[0], s = (x >>> 16) & 0x8000, e = ((x >>> 23) & 0xff) - 112, m = x & 0x7fffff;
  if (e <= 0) return s; if (e >= 31) return s | 0x7c00; return s | (e << 10) | ((m + 0x1000) >>> 13); }
function fromHalf(h) { const s = h & 0x8000 ? -1 : 1, e = (h >> 10) & 0x1f, m = h & 0x3ff; return e === 0 ? s * m * 2 ** -24 : s * (1 + m / 1024) * 2 ** (e - 15); }

async function load(m) {
  const t0 = performance.now();
  let buf = m.bytes; // optional pre-fetched bytes
  if (!buf) { const r = await fetch(m.url, { cache: m.cache || "default" }); buf = new Uint8Array(await r.arrayBuffer()); }
  const t1 = performance.now();
  if (m.power) ort.env.webgpu.powerPreference = m.power;
  const adapter = await navigator.gpu?.requestAdapter(m.power ? { powerPreference: m.power } : undefined);
  const info = adapter ? { f16: adapter.features.has("shader-f16"), vendor: adapter.info?.vendor, arch: adapter.info?.architecture, desc: adapter.info?.description } : null;
  const opts = { executionProviders: [m.ep === "wasm" ? "wasm" : (m.epOpts ? { name: "webgpu", ...m.epOpts } : "webgpu")], graphOptimizationLevel: "all", ...(m.opts || {}) };
  sess = await ort.InferenceSession.create(buf, opts);
  inName = sess.inputNames[0]; outName = sess.outputNames[0];
  const meta = sess.inputMetadata?.[0] || sess.inputMetadata?.[inName];
  return { fetchMs: Math.round(t1 - t0), createMs: Math.round(performance.now() - t1), bytes: buf.length, info, meta: JSON.stringify(sess.inputMetadata), outMeta: JSON.stringify(sess.outputMetadata) };
}
async function run(m) {
  const t0 = performance.now(), bmp = m.img, w = m.w, h = m.h;
  const c = new OffscreenCanvas(w, h), x = c.getContext("2d", { willReadFrequently: true });
  x.imageSmoothingQuality = "high"; x.drawImage(bmp, 0, 0, w, h); bmp.close();
  const px = x.getImageData(0, 0, w, h).data, n = w * h, t = new Float32Array(3 * n);
  for (let i = 0; i < n; i++) for (let k = 0; k < 3; k++) t[k * n + i] = (px[i * 4 + k] / 255 - 0.5) / 0.5;
  const half = m.half;
  const data = half ? Uint16Array.from(t, toHalf) : t;
  const t1 = performance.now();
  const feeds = { [inName]: new ort.Tensor(half ? "float16" : "float32", data, [1, 3, h, w]) };
  const o = (await sess.run(feeds))[outName];
  const t2 = performance.now();
  let d = await o.getData();
  const t3 = performance.now();
  if (o.type === "float16") d = Float32Array.from(d instanceof Uint16Array ? d : new Uint16Array(d.buffer), fromHalf);
  const [oh, ow] = o.dims.slice(-2), on = ow * oh, a = new Uint8Array(on);
  for (let i = 0; i < on; i++) a[i] = Math.max(0, Math.min(255, d[i] * 255 + 0.5));
  o.dispose?.();
  return [{ data: a, width: ow, height: oh, ms: Math.round(performance.now() - t0), prep: Math.round(t1 - t0), runMs: Math.round(t2 - t1), readMs: Math.round(t3 - t2) }, [a.buffer]];
}
onmessage = async ({ data: m }) => {
  try {
    if (m.ping) postMessage({ id: m.id, gpu: !!navigator.gpu, oc: typeof OffscreenCanvas });
    else if (m.load) postMessage({ id: m.id, ...(await load(m.load)) });
    else { const [r, tr] = await run(m); postMessage({ id: m.id, ...r }, tr); }
  } catch (e) { postMessage({ id: m.id, error: String(e?.message || e) }); }
};
