// Module worker: runs one ONNX person-matting model (onnxruntime-web, WebGPU then WASM) on a still.
// Messages in : { id, load: spec, eps: ["webgpu", "wasm"] }  -> { id, ep, fetchMs, createMs, bytes }
//               { id, img: ImageBitmap }                      -> { id, data: Uint8Array alpha, width, height, ms }
// spec = { url, size: { short, div } | { w, h }, mean: [3], std: [3], out: "alpha"|"sigmoid"|"minmax"|"softmax2" }
// Errors come back as { id, error }.
import * as ort from "https://cdn.jsdelivr.net/npm/onnxruntime-web@1.22.0/dist/ort.webgpu.min.mjs";
ort.env.wasm.wasmPaths = "https://cdn.jsdelivr.net/npm/onnxruntime-web@1.22.0/dist/";
ort.env.logLevel = "error";

let sess = null, spec = null, inName = "", outName = "", f16 = false;

function inputSize(w, h) {
  const s = spec.size;
  if (s.w) return [s.w, s.h];
  const k = s.short / Math.min(w, h), r = v => Math.max(s.div, Math.round(v * k / s.div) * s.div);
  return [r(w), r(h)];
}

// float32 -> IEEE half bits (fp16 models whose graph input is float16)
const f32 = new Float32Array(1), u32 = new Uint32Array(f32.buffer);
function toHalf(v) {
  f32[0] = v; const x = u32[0], s = (x >>> 16) & 0x8000, e = ((x >>> 23) & 0xff) - 112, m = x & 0x7fffff;
  if (e <= 0) return s; if (e >= 31) return s | 0x7c00;
  return s | (e << 10) | ((m + 0x1000) >>> 13);
}
function fromHalf(h) {
  const s = h & 0x8000 ? -1 : 1, e = (h >> 10) & 0x1f, m = h & 0x3ff;
  return e === 0 ? s * m * 2 ** -24 : e === 31 ? s * Infinity : s * (1 + m / 1024) * 2 ** (e - 15);
}

async function load(m) {
  spec = m.load;
  const t0 = performance.now();
  const res = await fetch(spec.url, { cache: "force-cache" });
  if (!res.ok) throw new Error("model http " + res.status);
  const buf = new Uint8Array(await res.arrayBuffer());
  const t1 = performance.now();
  let err;
  for (const ep of m.eps) {
    if (ep === "webgpu" && !(self.navigator && navigator.gpu && await navigator.gpu.requestAdapter())) { err = "no webgpu adapter"; continue; }
    try {
      sess = await ort.InferenceSession.create(buf, { executionProviders: [ep], graphOptimizationLevel: "all" });
      inName = sess.inputNames[0]; outName = sess.outputNames[0];
      return { ep, fetchMs: Math.round(t1 - t0), createMs: Math.round(performance.now() - t1), bytes: buf.length };
    } catch (e) { err = ep + ": " + (e?.message || e); }
  }
  throw new Error(err || "no execution provider");
}

async function run(m) {
  const t0 = performance.now(), bmp = m.img, [w, h] = inputSize(bmp.width, bmp.height);
  const c = new OffscreenCanvas(w, h), x = c.getContext("2d", { willReadFrequently: true });
  x.drawImage(bmp, 0, 0, w, h); bmp.close();
  const px = x.getImageData(0, 0, w, h).data, n = w * h, t = new Float32Array(3 * n);
  const { mean, std } = spec;
  for (let i = 0; i < n; i++) for (let k = 0; k < 3; k++) t[k * n + i] = (px[i * 4 + k] / 255 - mean[k]) / std[k];
  const feed = async half => {
    const data = half ? Uint16Array.from(t, toHalf) : t;
    return (await sess.run({ [inName]: new ort.Tensor(half ? "float16" : "float32", data, [1, 3, h, w]) }))[outName];
  };
  let o;
  try { o = await feed(f16); } catch (e) { if (f16 || !/float16|type/i.test(String(e?.message || e))) throw e; f16 = true; o = await feed(true); }
  let d = await o.getData();
  if (o.type === "float16") d = Float32Array.from(d instanceof Uint16Array ? d : new Uint16Array(d.buffer), fromHalf);
  const [oh, ow] = o.dims.slice(-2), on = ow * oh, a = new Uint8Array(on);
  let lo = 0, hi = 1;
  if (spec.out === "minmax") { lo = Infinity; hi = -Infinity; for (let i = 0; i < on; i++) { lo = Math.min(lo, d[i]); hi = Math.max(hi, d[i]); } }
  for (let i = 0; i < on; i++) {
    let v = d[i];
    if (spec.out === "sigmoid") v = 1 / (1 + Math.exp(-v));
    else if (spec.out === "softmax2") v = d[on + i];          // channel 1 = person
    else if (spec.out === "minmax") v = (v - lo) / (hi - lo || 1);
    a[i] = Math.max(0, Math.min(255, v * 255 + 0.5));
  }
  o.dispose?.();
  return [{ data: a, width: ow, height: oh, ms: Math.round(performance.now() - t0) }, [a.buffer]];
}

onmessage = async ({ data: m }) => {
  try {
    if (m.load) postMessage({ id: m.id, ...(await load(m)) });
    else { const [r, tr] = await run(m); postMessage({ id: m.id, ...r }, tr); }
  } catch (e) { m.img?.close?.(); postMessage({ id: m.id, error: String(e?.message || e) }); }
};
