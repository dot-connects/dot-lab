// Classic worker: DeepLab-v3 (MediaPipe tasks-vision 0.10.14, Apache-2.0, PASCAL VOC) finds real people / dogs / cats
// in camera frames and gives the mask for a cut-out.
// Copied from works/2026-10-26-photo-booth/site/js/pet-worker.js (D:OT lab, git c70de1f): the .cjs-as-blob loading and
// the iOS CPU delegate are kept as they were. Added: the person class (15), the find mode with 0..1 boxes (blob.js), and
// the cut mode (one class map, largest blob only). Nothing is imported from that folder.
// Classic, not a module worker: tasks-vision loads its wasm with importScripts, which module workers lack; the .cjs
// build needs `exports`.
// in : { id, img: ImageBitmap (transferred), mode: "find" }
// out: { id, person: {x, y, w, h} | null, animal: {x, y, w, h, kind: "dog" | "cat"} | null, ms }   (boxes 0..1)
// in : { id, img, mode: "cut", kind: "person" | "animal" }
// out: { id, data: Float32Array 0..1 (largest blob), width, height, ms }  | { id, error }
importScripts("blob.js");
const MP = "https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.14";
const DEEPLAB = "https://storage.googleapis.com/mediapipe-models/image_segmenter/deeplab_v3/float32/1/deeplab_v3.tflite";
const CAT = 8, DOG = 12, PERSON = 15;
const segP = (async () => {
  // the CDN serves .cjs with a non-JS MIME type, which importScripts refuses: load it as a JS blob instead
  const src = await (await fetch(MP + "/vision_bundle.cjs")).text();
  self.exports = {};
  importScripts(URL.createObjectURL(new Blob([src], { type: "text/javascript" })));
  const { FilesetResolver, ImageSegmenter } = self.exports;
  const files = await FilesetResolver.forVisionTasks(MP + "/wasm");
  const mk = delegate => ImageSegmenter.createFromOptions(files, { baseOptions: { modelAssetPath: DEEPLAB, delegate },
    runningMode: "IMAGE", outputConfidenceMasks: true, outputCategoryMask: false });
  // iOS: MediaPipe's GPU delegate scrambles multiclass categories (mediapipe #6142, photo-booth research/ios-audit.md #4)
  // ?cpu (page query, passed on by seg.js): CPU too; headless Chrome's software GL takes > 3 s a frame on the GPU path
  if (/iPhone|iPad|iPod/.test(navigator.userAgent) || new URL(self.location.href).searchParams.has("cpu")) return await mk("CPU");
  try { return await mk("GPU"); } catch { return await mk("CPU"); }
})();
const box = b => b && { x: b.x, y: b.y, w: b.w, h: b.h };
onmessage = async ({ data: { id, img, mode, kind } }) => {
  try {
    const seg = await segP, t0 = performance.now();
    let out = { id };
    seg.segment(img, r => {
      const M = r.confidenceMasks, w = M[CAT].width, h = M[CAT].height;
      const cat = M[CAT].getAsFloat32Array(), dog = M[DOG].getAsFloat32Array(), any = new Float32Array(cat.length);
      for (let i = 0; i < any.length; i++) any[i] = Math.min(1, cat[i] + dog[i]);
      if (mode === "cut") {
        const d = kind === "person" ? new Float32Array(M[PERSON].getAsFloat32Array()) : any;
        // nothing solid in the crop (the find saw it, the cut did not): no mask, the page falls back to the box crop
        out = blobBox(d, w, h, 0.5, 0.02) ? { id, data: keepLargest(d, w, h), width: w, height: h } : { id, data: null };
        return;
      }
      const p = blobBox(M[PERSON].getAsFloat32Array(), w, h), a = blobBox(any, w, h);
      out = { id, person: box(p), animal: a && { ...box(a), kind: kindOf(cat, dog, a.lab, a.id) } };
    });
    out.ms = Math.round(performance.now() - t0);
    postMessage(out, out.data ? [out.data.buffer] : []);
  } catch (e) { postMessage({ id, error: String(e?.message || e) }); }
  finally { img.close?.(); }
};
