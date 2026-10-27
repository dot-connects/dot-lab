// Research worker: { load:{url, ep} } -> session; { imgs:[ImageBitmap...], w, h, names:[input names], norm:"modnet"|"unit", outs:[names] }
import * as ort from "https://cdn.jsdelivr.net/npm/onnxruntime-web@1.22.0/dist/ort.webgpu.min.mjs";
ort.env.wasm.wasmPaths = "https://cdn.jsdelivr.net/npm/onnxruntime-web@1.22.0/dist/";
ort.env.logLevel = "error";
let sess;
function tensor(bmp, w, h, norm) {
  const c = new OffscreenCanvas(w, h), x = c.getContext("2d", { willReadFrequently: true });
  x.drawImage(bmp, 0, 0, w, h); bmp.close();
  const px = x.getImageData(0, 0, w, h).data, n = w * h, t = new Float32Array(3 * n);
  for (let i = 0; i < n; i++) for (let k = 0; k < 3; k++) t[k * n + i] = norm === "modnet" ? (px[i * 4 + k] / 255 - 0.5) / 0.5 : px[i * 4 + k] / 255;
  return new ort.Tensor("float32", t, [1, 3, h, w]);
}
onmessage = async ({ data: m }) => {
  try {
    if (m.load) {
      const t0 = performance.now(), buf = new Uint8Array(await (await fetch(m.load.url)).arrayBuffer());
      sess = await ort.InferenceSession.create(buf, { executionProviders: [m.load.ep || "webgpu"], graphOptimizationLevel: "all" });
      postMessage({ id: m.id, ms: Math.round(performance.now() - t0), inputs: sess.inputNames, outputs: sess.outputNames });
    } else {
      const t0 = performance.now(), feeds = {};
      m.names.forEach((n, i) => feeds[n] = tensor(m.imgs[i], m.w, m.h, m.norm));
      const t1 = performance.now();
      const r = await sess.run(feeds, m.outs);
      const o = r[m.outs[0]], d = await o.getData(), t2 = performance.now();
      const a = new Uint8Array(d.length); for (let i = 0; i < d.length; i++) a[i] = Math.max(0, Math.min(255, d[i] * 255 + 0.5));
      for (const k in r) r[k].dispose?.();
      postMessage({ id: m.id, data: a, dims: o.dims, prep: Math.round(t1 - t0), run: Math.round(t2 - t1), ms: Math.round(performance.now() - t0) }, [a.buffer]);
    }
  } catch (e) { postMessage({ id: m.id, error: String(e?.message || e) }); }
};
