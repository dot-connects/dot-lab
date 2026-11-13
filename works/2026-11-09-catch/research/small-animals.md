# Small animals: find + mask (spike, 2026-11-13)

28 Commons photos (hamster 6, rabbit 5, turtle/tortoise 6, aquarium fish 7, guinea pig 2, lizard 2) + 8 negatives
(rooms, people, a dog, plush toys). Frame long side 320.
tasks-vision 0.10.14, headless Chrome WASM CPU. Data: `small-animals/`. Score = sum of the group's labels;
"mask ok" = judged on the sheets (on the animal, mostly animal).

| method | found ≥0.25 (of 28) | kind right | negatives ≥0.25 | mask ok | ms (desktop WASM) |
|---|---|---|---|---|---|
| A. classifier, full frame (f32; int8 52 ms, same counts) | 18 | 16 | 0 (max 0.005) | — | 90 |
| B(i). 3×3 crops + 2×2 refine → magic touch | 25 | 22 | **1** (toy dolphin 0.59) | 17 + 1 partial | 920 + 330 |
| **B(ii). EfficientDet boxes (any class, ≥0.1, ≤5) → crop classify → magic touch at box centre** | **24** | **22** | 0 (max 0.06) | **19 + 2 partial** | 185 + 270 (int8 200: 22 found at ≥0.15) + 330 |
| DeepLab non-background (already runs) | — | — | — | 13 (all furry; turtles/fish 0) | 150 |
| magic touch at frame centre (no detection) | — | — | — | 17 | 330 |

Full frame: a group label was in top-5 for 23/28 (top-1 18/28).

## Recommendation
1. Candidates come free from the detector the app already runs: every box of any COCO class with score ≥ 0.1
   (small animals come out as cat/dog/bird/teddy bear/sheep…); skip confident cat/dog/person.
2. Crop each box +10 %, run **EfficientNet-Lite0** and sum the group labels:
   hamster {hamster} · guinea pig {guinea pig} · rabbit {wood rabbit, hare, Angora} · turtle {box turtle, mud turtle,
   terrapin, loggerhead, leatherback turtle} · fish {goldfish, tench, anemone fish, rock beauty, puffer, lionfish, eel,
   coho, sturgeon, gar, barracouta} · lizard {common iguana, American chameleon, whiptail, agama, frilled lizard,
   alligator lizard, Gila monster, green lizard, African chameleon, Komodo dragon, banded gecko}.
   Threshold **0.25 (f32)** or **0.15 (int8)**; negatives never passed 0.07. Treat hamster/guinea pig/rabbit as one
   "small furry" family if the kind matters (2 swaps seen).
3. Mask: if the DeepLab mask fills the box, use it (free, good for furry ones); otherwise one **magic touch** at the
   box centre, clipped to the box (clip untested; should cut the fish leaks).
4. Cadence on the 300 ms loop: classify ≤2 boxes every 3rd frame (~1 s); magic touch only once on a new find, then
   hold it like the cat path; re-touch at most every ~1 s.

Model files: efficientnet_lite0 f32 18.6 MB / int8 5.4 MB; magic_touch 6.2 MB (APK hand-off limit is 30 MB → int8).
The grid route costs 13 classifier calls and fired on a toy dolphin.

## Known failures
- Tiny in frame (hamster in a long tank, far tortoise): nothing finds them.
- Fish: small tetras/guppies in planted tanks — box lands on the fish but magic touch grabs bokeh/plants (2/7);
  betta with leaves labelled lizard; int8 loses small fish (0.62→0.02).
- Gecko next to a toy dinosaur: right label, box on the rock, mask wrong.
- ms are desktop WASM; expect the same ratios on the phone.
