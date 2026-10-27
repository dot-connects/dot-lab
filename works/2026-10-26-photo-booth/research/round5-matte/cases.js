// Round 5 cut-out test set: every case is a 960x720 still (the booth's STILL_MAX) + its ground-truth alpha.
// real: one photo, crop [sx, sy, sw, sh] -> 960x720; GT = BiRefNet-lite 1024 (MIT) on that frame, checked by eye (gt.html).
// comp: synthetic composite over a background photo; each layer = a photo cut with its own BiRefNet alpha, drawn
//       src rect -> dest rect. GT = union of the layers with keep: true (exact by construction).
// Sources + licences: README.md.
export const W = 960, H = 720;
export const CASES = [
  { n: "person-a", tag: "plain wall, braids", src: "person-a-2s.jpg", crop: [0, 0, 640, 480] },
  { n: "hair1", tag: "loose red hair, dark bg", src: "hair1.jpg", crop: [0, 60, 1280, 960] },
  { n: "hair2", tag: "loose black hair", src: "hair2.jpg", crop: [0, 100, 1280, 960] },
  { n: "hold-dog", tag: "pet held up", src: "hold-dog.jpg", crop: [0, 300, 1280, 960], pet: 1 },
  { n: "hold-cat", tag: "pet in arms", src: "hold-cat.jpg", crop: [70, 0, 1137, 853], pet: 1 },
  { n: "arms-books", tag: "arms raised, hands at top edge, cluttered bg", bg: ["books.jpg", [0, 0, 1123, 842]],
    layers: [{ src: "arms-up.jpg", s: [0, 330, 1280, 960], d: [0, 0, 960, 720], keep: true }] },
  { n: "far-person", tag: "street crowd + second person far behind", bg: ["city.jpg", [0, 0, 1128, 846]],
    layers: [{ src: "person-a-2s.jpg", s: [0, 0, 640, 480], d: [600, 170, 288, 216], keep: false },
             { src: "person-b-2s.jpg", s: [0, 0, 640, 480], d: [-60, 0, 960, 720], keep: true }] },
  { n: "dog-beside", tag: "dog sitting beside, cluttered bg", bg: ["books.jpg", [0, 0, 1123, 842]], pet: 1,
    layers: [{ src: "person-a-2s.jpg", s: [0, 0, 640, 480], d: [-200, 0, 960, 720], keep: true },
             { src: "white-dog.jpg", s: [0, 0, 1206, 2115], d: [630, 190, 302, 530], keep: true }] },
];
// every image that needs its own BiRefNet alpha: real frames by case name, layer sources by file name
export const GT_KEYS = [...CASES.filter(c => c.src).map(c => c.n), ...new Set(CASES.flatMap(c => (c.layers || []).map(l => l.src)))];

export const mk = (w = W, h = H) => Object.assign(document.createElement("canvas"), { width: w, height: h });
const img = async u => { const i = new Image(); i.src = u; await i.decode(); return i; };
// gray PNG -> canvas whose alpha = the gray value (white RGB)
async function alphaCanvas(u, w, h) {
  const i = await img(u), c = mk(w, h), x = c.getContext("2d", { willReadFrequently: true });
  x.drawImage(i, 0, 0, w, h);
  const d = x.getImageData(0, 0, w, h);
  for (let k = 0; k < d.data.length; k += 4) { d.data[k + 3] = d.data[k]; d.data[k] = d.data[k + 1] = d.data[k + 2] = 255; }
  x.putImageData(d, 0, 0); return c;
}
// frame (960x720 canvas) and gt (Float32Array 0..1) of one case. base = folder holding media/ and gt/.
export async function loadCase(c, base = ".", withGt = true) {
  const frame = mk(), fx = frame.getContext("2d"), g = mk(), gx = g.getContext("2d", { willReadFrequently: true });
  if (c.src) {
    fx.drawImage(await img(`${base}/media/${c.src}`), ...c.crop, 0, 0, W, H);
    if (withGt) gx.drawImage(await alphaCanvas(`${base}/gt/${c.n}.png`, W, H), 0, 0);
  } else {
    fx.drawImage(await img(`${base}/media/${c.bg[0]}`), ...c.bg[1], 0, 0, W, H);
    for (const l of c.layers) {
      const im = await img(`${base}/media/${l.src}`), a = await alphaCanvas(`${base}/gt/${l.src}.png`, im.naturalWidth, im.naturalHeight);
      const cut = mk(im.naturalWidth, im.naturalHeight), cx = cut.getContext("2d");
      cx.drawImage(im, 0, 0); cx.globalCompositeOperation = "destination-in"; cx.drawImage(a, 0, 0);
      fx.drawImage(cut, ...l.s, ...l.d);
      gx.globalCompositeOperation = l.keep ? "source-over" : "destination-out";
      gx.drawImage(a, ...l.s, ...l.d);
    }
  }
  const p = gx.getImageData(0, 0, W, H).data, gt = new Float32Array(W * H);
  for (let k = 0; k < gt.length; k++) gt[k] = p[k * 4 + 3] / 255;
  return { frame, gt };
}
