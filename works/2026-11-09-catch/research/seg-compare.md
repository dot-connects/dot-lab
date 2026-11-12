# Segmenter comparison on the kitten clip (2026-11-12, plan Task 0)

Input: `research/claw-media/kitten.webm` (Wikimedia Commons, CC BY-SA 4.0), first 10 s at 12 fps = 120 frames, 352×640.
Run in headless Chrome with MediaPipe tasks-vision 0.10.14 (the same models the Android app ships), CPU delegate.
A frame "counts" when a cat/dog/person mask has > 40 px of 90×160, or the detector returns a box with score ≥ 0.3.

| setup | frames found (of 120) |
|---|---|
| DeepLab v3, threshold 0.5 (web game until now) | 72 |
| DeepLab v3, threshold 0.3 | 103 |
| EfficientDet-Lite0 (cat/dog/person, score ≥ 0.3) | 102 |
| DeepLab 0.3, or an EfficientDet box when the mask is empty | 117 |
| the same + hold the last mask up to 4 missed frames | **120** |

Detector labels: cat 83, dog 17 (the kitten mis-labelled as a dog), person 28 (the hand).
Still missing without the hold: frames 68, 69, 76 (motion blur during a jump).

**Choice for the app:** DeepLab 0.3 masks; when a mask is empty, an ellipse inside the EfficientDet box stands in;
`Tracker` holds the last mask for up to 4 missed frames. Kind comes from DeepLab (cat vs dog channel), not the detector.
