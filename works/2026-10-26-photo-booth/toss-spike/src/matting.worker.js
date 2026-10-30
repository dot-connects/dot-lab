// MODNet on one still, onnxruntime-web from the bundle (no CDN). In: {base, img: ImageBitmap} -> {ep, ms, loadMs, mean}.
import * as ort from "onnxruntime-web/webgpu";

let sess = null, ep = "";
async function load(base) {
  ort.env.logLevel = "error";
  const t0 = performance.now(), buf = new Uint8Array(await (await fetch(base + "models/modnet.onnx")).arrayBuffer());
  const gpu = !!(self.navigator?.gpu && await navigator.gpu.requestAdapter().catch(() => null));
  for (const e of gpu ? ["webgpu", "wasm"] : ["wasm"]) {
    try { sess = await ort.InferenceSession.create(buf, { executionProviders: [e] }); ep = e; break; }
    catch (err) { if (e === "wasm") throw err; }
  }
  return Math.round(performance.now() - t0);
}

self.onmessage = async ({ data }) => {
  try {
    const loadMs = sess ? 0 : await load(data.base);
    const [w, h] = ep === "webgpu" ? [512, 384] : [352, 256];
    const c = new OffscreenCanvas(w, h), g = c.getContext("2d");
    g.drawImage(data.img, 0, 0, w, h);
    const px = g.getImageData(0, 0, w, h).data, x = new Float32Array(3 * w * h);
    for (let i = 0; i < w * h; i++) for (let k = 0; k < 3; k++) x[k * w * h + i] = (px[i * 4 + k] / 255 - 0.5) / 0.5;
    const t0 = performance.now();
    const out = await sess.run({ [sess.inputNames[0]]: new ort.Tensor("float32", x, [1, 3, h, w]) });
    const a = out[sess.outputNames[0]].data;
    let s = 0; for (let i = 0; i < a.length; i++) s += a[i];
    self.postMessage({ ep, loadMs, ms: Math.round(performance.now() - t0), mean: +(s / a.length).toFixed(3), w, h });
  } catch (e) { self.postMessage({ error: String(e?.message || e) }); }
};
