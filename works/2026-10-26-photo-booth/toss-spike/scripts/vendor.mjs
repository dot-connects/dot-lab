// Copies the libraries' runtime files and the models into public/ so the bundle never loads code from a CDN
// (Apps in Toss forbids running externally delivered code).
import { cpSync, existsSync, mkdirSync, writeFileSync } from "node:fs";

const nm = "node_modules", out = "public/vendor";
// onnxruntime-web's wasm is emitted by Vite from the worker's import (dist/assets/), so it is not copied here
cpSync(`${nm}/@mediapipe/tasks-vision/wasm`, `${out}/mediapipe`, { recursive: true });
mkdirSync("public/models", { recursive: true });
cpSync("../site/models/modnet.onnx", "public/models/modnet.onnx");
const seg = "public/models/selfie_segmenter.tflite";
if (!existsSync(seg)) {
  const r = await fetch("https://storage.googleapis.com/mediapipe-models/image_segmenter/selfie_segmenter/float16/1/selfie_segmenter.tflite");
  if (!r.ok) throw new Error("selfie_segmenter http " + r.status);
  writeFileSync(seg, Buffer.from(await r.arrayBuffer()));
}
console.log("vendor ok");
