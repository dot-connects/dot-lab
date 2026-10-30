// Classic worker: DeepLab-v3 (MediaPipe, Apache-2.0) cat + dog for the LIVE cut (segment.js segmentFrame), off the main
// thread: on the page it cost 74 ms a run (a 23 -> 18 fps preview with hitches, research/round5-engine/). Classic, not a
// module worker: tasks-vision loads its wasm with importScripts, which module workers lack; the .cjs build needs `exports`.
// Also the HQ moving cut's keyframes (clipmatte.js): its own wasm instance, so it never shares MediaPipe's GPU buffer pool
// with the face landmarker on the page (that pool aborted mid-decorate when DeepLab ran there on clip frames, 10-28).
// in : { id, img: ImageBitmap (transferred) }  ->  out: { id, data: Float32Array cat+dog, width, height, ms }
//      | { id, data: null } (no pet >= 0.3) | { id, error }
// bases come as query params (segment.js, from platform.js); the CDN defaults cover a cached older segment.js
const QP = new URL(self.location.href).searchParams;
const MP = QP.get("mp") || "https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.14";
const DEEPLAB = QP.get("deeplab") || "https://storage.googleapis.com/mediapipe-models/image_segmenter/deeplab_v3/float32/1/deeplab_v3.tflite";
const CAT = 8, DOG = 12;
const segP = (async () => {
  // the CDN serves .cjs with a non-JS MIME type, which importScripts refuses: load it as a JS blob instead
  const src = await (await fetch(MP + "/vision_bundle.cjs")).text();
  self.exports = {};
  importScripts(URL.createObjectURL(new Blob([src], { type: "text/javascript" })));
  const { FilesetResolver, ImageSegmenter } = self.exports;
  const files = await FilesetResolver.forVisionTasks(MP + "/wasm");
  const mk = delegate => ImageSegmenter.createFromOptions(files, { baseOptions: { modelAssetPath: DEEPLAB, delegate },
    runningMode: "IMAGE", outputConfidenceMasks: true, outputCategoryMask: false });
  // iOS: MediaPipe's GPU delegate scrambles multiclass categories (mediapipe #6142, research/ios-audit.md #4): CPU there
  if (/iPhone|iPad|iPod/.test(navigator.userAgent)) return await mk("CPU");
  try { return await mk("GPU"); } catch { return await mk("CPU"); }
})();
onmessage = async ({ data: { id, img } }) => {
  try {
    const seg = await segP, t0 = performance.now();
    let out = { id, data: null };
    seg.segment(img, r => {
      const M = r.confidenceMasks, a = M[CAT].getAsFloat32Array(), b = M[DOG].getAsFloat32Array(), d = new Float32Array(a.length);
      let hit = false;
      for (let i = 0; i < d.length; i++) { d[i] = Math.min(1, a[i] + b[i]); hit ||= d[i] >= 0.3; }
      if (hit) out = { id, data: d, width: M[CAT].width, height: M[CAT].height };
    });
    out.ms = Math.round(performance.now() - t0);
    postMessage(out, out.data ? [out.data.buffer] : []);
  } catch (e) { postMessage({ id, error: String(e?.message || e) }); }
  finally { img.close?.(); }
};
