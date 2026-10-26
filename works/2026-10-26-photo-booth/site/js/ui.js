// Placeholders so the whole flow runs before (or without) the real capture/compose modules.
// ?mock=1 forces them (no MediaPipe); otherwise app.js uses them only when a module fails to import.
// ponytail: cut-out = an ellipse mask, strip = plain cells; real look comes from segment/compose/frames/filters.js
const sleep = ms => new Promise(r => setTimeout(r, ms));
const toBlob = (c, t, q) => new Promise(r => c.toBlob(r, t, q));
const canvas = (w, h) => Object.assign(document.createElement("canvas"), { width: w, height: h });

export function mockCapture(cam) {
  const W = 640, H = 480;
  const frame = (ctx, video, x, mirror) => {
    const r = cam.frameRect(video);
    ctx.save(); ctx.beginPath(); ctx.rect(x, 0, W, H); ctx.clip();
    if (mirror) { ctx.translate(x + W, 0); ctx.scale(-1, 1); } else ctx.translate(x, 0);
    ctx.drawImage(video, r.sx, r.sy, r.sw, r.sh, 0, 0, W, H); ctx.restore();
  };
  const ellipse = (ctx, x) => { ctx.beginPath(); ctx.ellipse(x + W / 2, H * 0.62, W * 0.24, H * 0.42, 0, 0, 7); };
  return {
    async shootOne(i, { video, onTick }) {
      const mirror = cam.camState.mirror;
      const c = canvas(2 * W, H), ctx = c.getContext("2d");
      onTick?.(3); await sleep(1000);
      onTick?.(2);
      let on = true;
      (function loop() {
        if (!on) return;
        frame(ctx, video, 0, mirror);
        ctx.fillStyle = "#000"; ctx.fillRect(W, 0, W, H); ctx.fillStyle = "#fff"; ellipse(ctx, W); ctx.fill();
        requestAnimationFrame(loop);
      })();
      const mime = ["video/mp4", "video/webm"].find(t => MediaRecorder.isTypeSupported(t));
      const rec = new MediaRecorder(c.captureStream(30), { mimeType: mime }), chunks = [];
      rec.ondataavailable = e => e.data.size && chunks.push(e.data);
      const stopped = new Promise(r => rec.onstop = r);
      rec.start(250);
      await sleep(1000); onTick?.(1); await sleep(1000);
      rec.stop(); on = false; await stopped;
      const s = canvas(W, H), sx = s.getContext("2d");
      frame(sx, video, 0, mirror);
      const raw = await toBlob(s, "image/jpeg", 0.9);
      sx.globalCompositeOperation = "destination-in"; ellipse(sx, 0); sx.fill();
      const cut = await toBlob(s, "image/png");
      const clipType = mime.split(";")[0];
      return { raw, cut, clip: new Blob(chunks, { type: clipType }), clipType };
    },
  };
}

const L = { W: 600, H: 1800, M: 24, GAP: 16, FOOT: 200, CW: 552, CH: 382 };
export const mockCompose = {
  LAYOUT: L,
  BACKGROUNDS: [
    { id: "a", label: "보낸 사람 배경" }, { id: "b", label: "받은 사람 배경" },
    { id: "booth-pink", label: "분홍 부스", draw: (c, w, h) => { c.fillStyle = "#ffc2d6"; c.fillRect(0, 0, w, h); } },
    { id: "white-wall", label: "흰 벽", draw: (c, w, h) => { c.fillStyle = "#f6f4f2"; c.fillRect(0, 0, w, h); } },
  ],
  async drawStrip({ a, b, bg, frame, date }) {
    const c = canvas(L.W, L.H), ctx = c.getContext("2d");
    ctx.fillStyle = frame?.color || "#fff"; ctx.fillRect(0, 0, L.W, L.H);
    const pre = this.BACKGROUNDS.find(x => x.id === bg);
    for (let i = 0; i < 4; i++) {
      const y = L.M + i * (L.CH + L.GAP);
      ctx.save(); ctx.beginPath(); ctx.rect(L.M, y, L.CW, L.CH); ctx.clip(); ctx.translate(L.M, y);
      const fit = img => { const s = L.CW / img.width, h = img.height * s; ctx.drawImage(img, 0, (L.CH - h) / 2, L.CW, h); };
      if (bg === "a") fit(a.cuts[i].raw); else if (bg === "b") fit(b.cuts[i].raw); else pre.draw(ctx, L.CW, L.CH);
      if (bg !== "a") fit(a.cuts[i].cut);
      if (bg !== "b") fit(b.cuts[i].cut);
      ctx.restore();
    }
    ctx.fillStyle = frame?.ink || "#2e2230"; ctx.textAlign = "center";
    ctx.font = '44px "Jua"'; ctx.fillText("따로 같이 네컷", L.W / 2, L.H - 110);
    ctx.font = '28px "Gowun Dodum"'; ctx.fillText(date, L.W / 2, L.H - 62);
    return toBlob(c, "image/jpeg", 0.92);
  },
  async recordMovingStrip() { throw new Error("mock: compose.js not loaded"); },
};
export const mockFrames = { FRAMES: [
  { id: "white", label: "화이트", color: "#fafafa", ink: "#2e2230" },
  { id: "black", label: "블랙", color: "#141216", ink: "#f4f0f5" },
  { id: "pink", label: "핑크", color: "#ffc9dc", ink: "#5b2335" },
] };
export const mockFilters = { FILTERS: [
  { id: "original", label: "원본" }, { id: "bbosasi", label: "뽀샤시" }, { id: "mono", label: "흑백" },
  { id: "warm", label: "웜" }, { id: "cool", label: "쿨" }, { id: "film", label: "필름" },
] };
