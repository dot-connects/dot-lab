// Test stills: 4:3 crop of each source -> 960x720 (landscape) or 720x960 (portrait). Licences: README in this folder.
export const IMGS = [
  { n: "person-a", src: "../cutout-speed/media/person-a-2s.jpg", c: [0, 0, 640, 480], pet: 0 },
  { n: "person-b", src: "../cutout-speed/media/person-b-2s.jpg", c: [0, 0, 640, 480], pet: 0 },
  { n: "hair1", src: "../cutout-speed/media/hair1.jpg", c: [0, 60, 1280, 960], pet: 0 },
  { n: "hair2", src: "../cutout-speed/media/hair2.jpg", c: [0, 100, 1280, 960], pet: 0 },
  { n: "hold-dog", src: "media/hold-dog.jpg", c: [0, 100, 1280, 1707], pet: 1 },
  { n: "lap-dog", src: "media/lap-dog.jpg", c: [0, 150, 1280, 1707], pet: 1 },
  { n: "cat-shoulder", src: "media/cat-shoulder.jpg", c: [0, 0, 1280, 1707], pet: 1 },
  { n: "hold-cat", src: "media/hold-cat.jpg", c: [70, 0, 1137, 853], pet: 1 },
  { n: "selfie-dog", src: "media/selfie-dog.jpg", c: [0, 0, 1280, 960], pet: 1 },
  { n: "car-dogs", src: "media/car-dogs.jpg", c: [0, 0, 1280, 960], pet: 1 },
  { n: "white-dog", src: "media/white-dog.jpg", c: [0, 200, 1206, 1608], pet: 1 },
  { n: "black-cat", src: "media/black-cat.jpg", c: [0, 0, 960, 720], pet: 1 },
  { n: "black-cat2", src: "media/black-cat2.jpg", c: [0, 0, 1280, 960], pet: 1 },
];
export const mk = (w, h) => Object.assign(document.createElement("canvas"), { width: w, height: h });
export async function loadStills(only) {
  const out = [];
  for (const im of IMGS) {
    if (only && !only.includes(im.n)) continue;
    const i = new Image(); i.src = im.src; await i.decode();
    const [W, H] = im.c[2] >= im.c[3] ? [960, 720] : [720, 960];
    const c = mk(W, H); c.getContext("2d").drawImage(i, ...im.c, 0, 0, W, H);
    out.push({ ...im, W, H, canvas: c });
  }
  return out;
}
// grayscale/RGB mask PNG -> { data: Uint8Array (channel ch), width, height }
export async function loadMask(url, ch = 0) {
  const i = new Image(); i.src = url; await i.decode();
  const c = mk(i.width, i.height), x = c.getContext("2d", { willReadFrequently: true }); x.drawImage(i, 0, 0);
  const p = x.getImageData(0, 0, i.width, i.height).data, d = new Uint8Array(i.width * i.height);
  for (let k = 0; k < d.length; k++) d[k] = p[k * 4 + ch];
  return { data: d, width: i.width, height: i.height };
}
// resample a mask to w x h (bilinear via canvas)
export function resample(m, w, h, ch = 0) {
  const a = mk(m.width, m.height), ax = a.getContext("2d"), id = ax.createImageData(m.width, m.height);
  for (let k = 0; k < m.data.length; k++) { const v = m.data[k]; id.data[k * 4] = id.data[k * 4 + 1] = id.data[k * 4 + 2] = v; id.data[k * 4 + 3] = 255; }
  ax.putImageData(id, 0, 0);
  const b = mk(w, h), bx = b.getContext("2d", { willReadFrequently: true }); bx.imageSmoothingQuality = "high"; bx.drawImage(a, 0, 0, w, h);
  const p = bx.getImageData(0, 0, w, h).data, d = new Uint8Array(w * h);
  for (let k = 0; k < d.length; k++) d[k] = p[k * 4 + ch];
  return { data: d, width: w, height: h };
}
// square min/max filter (separable), r in px; max = dilate, min = erode
export function morph(d, w, h, r, max) {
  const f = max ? Math.max : Math.min, t = new Uint8Array(d.length), o = new Uint8Array(d.length);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    let v = d[y * w + x]; for (let k = Math.max(0, x - r); k <= Math.min(w - 1, x + r); k++) v = f(v, d[y * w + k]); t[y * w + x] = v;
  }
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    let v = t[y * w + x]; for (let k = Math.max(0, y - r); k <= Math.min(h - 1, y + r); k++) v = f(v, t[k * w + x]); o[y * w + x] = v;
  }
  return o;
}
