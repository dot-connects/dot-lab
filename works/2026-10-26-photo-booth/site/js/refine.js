// refine.js - WebGL2 mask refinement for a low-res person mask (e.g. MediaPipe selfie_segmenter 256x256).
//
//   const r = createRefiner();                     // or createRefiner(myCanvas)
//   const out = r.refine(video, { data, width, height }, opts);   // -> r.canvas, premultiplied RGBA person
//   ctx.drawImage(out, 0, 0, w, h);                 // over any background
//
// Steps per call (all on the GPU, ~8 passes at ~320 px wide + 1 full-res pass):
//   1. pack     : guide I = luma(frame), p = mask (EMA with last frame -> no flicker)
//   2. box blur : mean I, mean p, mean I*I, mean I*p      (He et al., "Guided Image Filtering", fast variant)
//   3. a, b     : a = cov(I,p) / (var(I) + eps),  b = mean p - a * mean I, then box-blurred
//   4. full res : alpha = a*I + b (a, b bilinear-upsampled, I at full res = hair-strand detail)
//                 alpha = smoothstep(lo, hi, alpha)   (lo up = erode, hi down = dilate/harden)
//                 rgb   = C - (1 - alpha) * B         (B = local background colour -> no halo/spill)
// opts: { radius=4 (work px), eps=1e-3, workWidth=320, lo=0.15, hi=0.85, temporal=0.5 (0 = off),
//         decontam=1 (0 = off), guide=1 (0 = keep the mask's own alpha: a matting model's hair strands, which the
//         guided step would smooth away; linear levels lo..hi + decontamination still run), width, height (output size) }
// mask.data: Float32Array 0..1 or Uint8Array 0..255, row 0 = top, value = person probability.

const VS = `#version 300 es
in vec2 p; out vec2 uv; uniform float flipY;
void main(){ uv = p * 0.5 + 0.5; if (flipY > 0.5) uv.y = 1.0 - uv.y; gl_Position = vec4(p, 0.0, 1.0); }`;

const HEAD = `#version 300 es
precision highp float; in vec2 uv;
float luma(vec3 c){ return dot(c, vec3(0.299, 0.587, 0.114)); }
`;

const PACK = HEAD + `
uniform sampler2D frame, mask, prev; uniform float k;
layout(location=0) out vec4 o0; layout(location=1) out vec4 o1;
void main(){
  vec3 c = texture(frame, uv).rgb; float I = luma(c);
  float p = texture(mask, uv).r;
  if (k < 1.0) {                                   // EMA, but follow fast changes (motion) at once
    float q = texture(prev, uv).g;
    p = mix(q, p, clamp(k + 2.0 * abs(p - q), 0.0, 1.0));
  }
  o0 = vec4(I, p, I * I, I * p);
  o1 = vec4(c * (1.0 - p), 1.0 - p);               // background-weighted colour, for decontamination
}`;

const BLUR = HEAD + `
uniform sampler2D src; uniform vec2 step; uniform int r; out vec4 o;
void main(){
  vec4 s = vec4(0.0);
  for (int i = -r; i <= r; i++) s += texture(src, uv + float(i) * step);
  o = s / float(2 * r + 1);
}`;

const AB = HEAD + `
uniform sampler2D m; uniform float eps; out vec4 o;
void main(){
  vec4 v = texture(m, uv);                          // mean I, mean p, mean I*I, mean I*p
  float a = (v.w - v.x * v.y) / (v.z - v.x * v.x + eps);
  o = vec4(a, v.y - a * v.x, 0.0, 1.0);
}`;

const FINAL = HEAD + `
uniform sampler2D frame, ab, bg, mask; uniform float lo, hi, decon, guide; out vec4 o;
void main(){
  vec3 c = texture(frame, uv).rgb;
  vec2 k = texture(ab, uv).xy;
  float a = guide > 0.5 ? smoothstep(lo, hi, k.x * luma(c) + k.y)
                        : clamp((texture(mask, uv).r - lo) / (hi - lo), 0.0, 1.0);   // linear: keeps faint strands
  vec4 g = texture(bg, uv);
  vec3 B = g.a > 0.01 ? g.rgb / g.a : c;
  vec3 f = clamp(c - (1.0 - a) * B, 0.0, a);       // premultiplied foreground, spill removed
  o = vec4(mix(c * a, f, decon), a);
}`;

export function createRefiner(canvas = document.createElement("canvas")) {
  const gl = canvas.getContext("webgl2", { premultipliedAlpha: true, preserveDrawingBuffer: true, alpha: true });
  if (!gl) throw new Error("WebGL2 unavailable");
  // ponytail: RGBA16F work textures; var(I) loses precision below ~3e-4, so keep eps >= 1e-3
  if (!gl.getExtension("EXT_color_buffer_float") && !gl.getExtension("EXT_color_buffer_half_float"))
    throw new Error("float render targets unavailable");

  const prog = fs => {
    const p = gl.createProgram();
    for (const [type, src] of [[gl.VERTEX_SHADER, VS], [gl.FRAGMENT_SHADER, fs]]) {
      const s = gl.createShader(type); gl.shaderSource(s, src); gl.compileShader(s);
      if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(s));
      gl.attachShader(p, s);
    }
    gl.bindAttribLocation(p, 0, "p"); gl.linkProgram(p);
    if (!gl.getProgramParameter(p, gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(p));
    const u = {}; const n = gl.getProgramParameter(p, gl.ACTIVE_UNIFORMS);
    for (let i = 0; i < n; i++) { const nm = gl.getActiveUniform(p, i).name; u[nm] = gl.getUniformLocation(p, nm); }
    return { p, u };
  };
  const P = { pack: prog(PACK), blur: prog(BLUR), ab: prog(AB), fin: prog(FINAL) };
  gl.useProgram(P.fin.p); gl.uniform1f(P.fin.u.flipY, 1);   // only the canvas pass flips (row 0 = top)

  gl.bindBuffer(gl.ARRAY_BUFFER, gl.createBuffer());
  gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]), gl.STATIC_DRAW);
  gl.enableVertexAttribArray(0); gl.vertexAttribPointer(0, 2, gl.FLOAT, false, 0, 0);

  const tex = () => {
    const t = gl.createTexture(); gl.bindTexture(gl.TEXTURE_2D, t);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
    return t;
  };
  const frameT = tex(), maskT = tex();
  let W = 0, H = 0, T = null, hasPrev = false, flipPair = 0, maskBuf = null;

  function alloc(w, h) {   // work-res float targets
    const mk = () => {
      const t = tex(); gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA16F, w, h, 0, gl.RGBA, gl.HALF_FLOAT, null);
      return t;
    };
    const fb = (...ts) => {
      const f = gl.createFramebuffer(); gl.bindFramebuffer(gl.FRAMEBUFFER, f);
      ts.forEach((t, i) => gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0 + i, gl.TEXTURE_2D, t, 0));
      return f;
    };
    const A = [mk(), mk()], G = mk(), tmp = mk(), M = mk(), GB = mk(), D = mk(), E = mk();
    const packFb = [fb(A[0], G), fb(A[1], G)];
    for (const f of packFb) { gl.bindFramebuffer(gl.FRAMEBUFFER, f); gl.drawBuffers([gl.COLOR_ATTACHMENT0, gl.COLOR_ATTACHMENT1]); }
    T = { w, h, A, G, tmp, M, GB, D, E, packFb, tmpFb: fb(tmp), MFb: fb(M), GBFb: fb(GB), DFb: fb(D), EFb: fb(E) };
    gl.bindFramebuffer(gl.FRAMEBUFFER, null);
    hasPrev = false;
  }

  const bindTex = (unit, t, loc) => { gl.activeTexture(gl.TEXTURE0 + unit); gl.bindTexture(gl.TEXTURE_2D, t); gl.uniform1i(loc, unit); };
  const draw = (fb, w, h) => {
    gl.bindFramebuffer(gl.FRAMEBUFFER, fb); gl.viewport(0, 0, w, h);
    gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
  };
  function blur(src, dst, r) {     // separable box: src -> tmp (h) -> dst (v)
    gl.useProgram(P.blur.p); gl.uniform1i(P.blur.u.r, r);
    bindTex(0, src, P.blur.u.src); gl.uniform2f(P.blur.u.step, 1 / T.w, 0); draw(T.tmpFb, T.w, T.h);
    bindTex(0, T.tmp, P.blur.u.src); gl.uniform2f(P.blur.u.step, 0, 1 / T.h); draw(dst, T.w, T.h);
  }

  function refine(src, mask, o = {}) {
    const { radius = 4, eps = 1e-3, workWidth = 320, lo = 0.15, hi = 0.85, temporal = 0.5, decontam = 1, guide = 1 } = o;
    const sw = src.videoWidth || src.naturalWidth || src.width, sh = src.videoHeight || src.naturalHeight || src.height;
    const ow = o.width || sw, oh = o.height || sh;
    const ww = Math.min(sw, workWidth), wh = Math.round(ww * sh / sw);
    if (!T || T.w !== ww || T.h !== wh) alloc(ww, wh);
    if (ow !== W || oh !== H) { canvas.width = W = ow; canvas.height = H = oh; }

    gl.pixelStorei(gl.UNPACK_PREMULTIPLY_ALPHA_WEBGL, false);
    gl.bindTexture(gl.TEXTURE_2D, frameT);
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, src);
    let d = mask.data;
    if (!(d instanceof Uint8Array || d instanceof Uint8ClampedArray)) {
      if (!maskBuf || maskBuf.length !== d.length) maskBuf = new Uint8Array(d.length);
      for (let i = 0; i < d.length; i++) maskBuf[i] = d[i] * 255;
      d = maskBuf;
    }
    gl.bindTexture(gl.TEXTURE_2D, maskT);
    gl.pixelStorei(gl.UNPACK_ALIGNMENT, 1);
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.R8, mask.width, mask.height, 0, gl.RED, gl.UNSIGNED_BYTE, d);

    // 1. pack (+ EMA against the previous frame's p)
    const cur = flipPair, prev = 1 - flipPair; flipPair = prev;
    gl.useProgram(P.pack.p);
    bindTex(0, frameT, P.pack.u.frame); bindTex(1, maskT, P.pack.u.mask); bindTex(2, T.A[prev], P.pack.u.prev);
    gl.uniform1f(P.pack.u.k, hasPrev && temporal > 0 ? 1 - temporal : 1);
    draw(T.packFb[cur], ww, wh);
    hasPrev = true;
    // 2. means, background colour
    blur(T.A[cur], T.MFb, radius);
    blur(T.G, T.GBFb, radius * 2);
    // 3. a, b and their means
    gl.useProgram(P.ab.p); bindTex(0, T.M, P.ab.u.m); gl.uniform1f(P.ab.u.eps, eps); draw(T.DFb, ww, wh);
    blur(T.D, T.EFb, radius);
    // 4. full-res alpha + decontaminated colour, straight to the canvas
    gl.useProgram(P.fin.p);
    bindTex(0, frameT, P.fin.u.frame); bindTex(1, T.E, P.fin.u.ab); bindTex(2, T.GB, P.fin.u.bg); bindTex(3, maskT, P.fin.u.mask);
    gl.uniform1f(P.fin.u.guide, guide); gl.uniform1f(P.fin.u.lo, lo); gl.uniform1f(P.fin.u.hi, hi); gl.uniform1f(P.fin.u.decon, decontam);
    gl.bindFramebuffer(gl.FRAMEBUFFER, null); gl.viewport(0, 0, W, H);
    gl.clearColor(0, 0, 0, 0); gl.clear(gl.COLOR_BUFFER_BIT);
    gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
    return canvas;
  }

  return { canvas, refine, reset() { hasPrev = false; } };
}
