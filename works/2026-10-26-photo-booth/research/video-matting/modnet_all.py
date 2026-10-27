# Runs MODNet (ORT CPU) on every frame of media/a, media/b at 512x384 and 384x288 -> out/modnet_<size>_<clip>.npy (uint8 alpha at 640x480)
import sys, glob, time, numpy as np, cv2, onnxruntime as ort
s = ort.InferenceSession("models/modnet.onnx", providers=["CPUExecutionProvider"])
inp = s.get_inputs()[0].name
for (w, h) in [(512, 384), (384, 288)]:
    for clip in ["a", "b"]:
        fs = sorted(glob.glob(f"media/{clip}/*.jpg")); out = np.zeros((len(fs), 480, 640), np.uint8); t = time.time()
        for i, f in enumerate(fs):
            im = cv2.cvtColor(cv2.imread(f), cv2.COLOR_BGR2RGB)
            x = cv2.resize(im, (w, h), interpolation=cv2.INTER_AREA).astype(np.float32) / 255
            x = ((x - 0.5) / 0.5).transpose(2, 0, 1)[None]
            a = s.run(None, {inp: x})[0][0, 0]
            out[i] = np.clip(cv2.resize(a, (640, 480), interpolation=cv2.INTER_LINEAR) * 255 + 0.5, 0, 255)
        np.save(f"out/modnet_{w}_{clip}.npy", out); print(w, clip, len(fs), round(time.time() - t, 1), "s", flush=True)
