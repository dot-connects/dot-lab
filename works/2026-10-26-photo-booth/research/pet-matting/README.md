# pet-matting bench (2026-10-27)

Run: `python -m http.server 8799 --bind 127.0.0.1 --directory C:/DC/DD/dot-lab`, then from `out/`:
`node ../drive.mjs ../profA <port> r.json "<base>/run.html?m=modnet"` (m = modnet, deeplab, dl256, selfie, u2netp, silueta,
isnet, biref512, vitq8, vit32[&short=256&tag=_256]) and `compose.html?rows=cur,A,Cvit32,...` for sheets.
Weights go in `models/` (gitignored); U2-Net/IS-Net need MaxPool `ceil_mode` 1->0 (`*-floor.onnx`, identical output) for WebGPU.

## Test photos (media/, gitignored; Wikimedia Commons 1280px thumbnails)

| file | source | licence |
|---|---|---|
| hold-dog.jpg | [Happy tattooed woman hugging her lovely black dog](https://commons.wikimedia.org/wiki/File:Happy_tattooed_woman_hugging_her_lovely_black_dog.jpg) | CC BY 2.0 |
| lap-dog.jpg | [Woman sits on floor of cozy room with dog in her lap](https://commons.wikimedia.org/wiki/File:Woman_sits_on_floor_of_cozy_room_with_dog_in_her_lap.jpg) | CC BY 2.0 |
| cat-shoulder.jpg | [Cat on Shoulder](https://commons.wikimedia.org/wiki/File:Cat_on_Shoulder.jpg) | CC BY-SA 4.0 |
| hold-cat.jpg | [Woman holding cat (Unsplash)](https://commons.wikimedia.org/wiki/File:Woman_holding_cat_(Unsplash).jpg) | CC0 |
| selfie-dog.jpg | [Dog selfie with Charlie](https://commons.wikimedia.org/wiki/File:Dog_selfie_with_Charlie_(51750239779).jpg) | CC BY-SA 2.0 |
| car-dogs.jpg | [Dog selfie with Charlie and Nora](https://commons.wikimedia.org/wiki/File:Dog_selfie_with_Charlie_and_Nora_(51750239689).jpg) | CC BY-SA 2.0 |
| white-dog.jpg | [Cute white fluffy dog close up portrait](https://commons.wikimedia.org/wiki/File:Cute_white_fluffy_dog_close_up_portrait.jpg) | CC BY 4.0 |
| black-cat.jpg | [Black Cat on Black Leather Couch](https://commons.wikimedia.org/wiki/File:Black_Cat_on_Black_Leather_Couch.jpg) | CC BY 2.0 |
| black-cat2.jpg | [Black cat curled up on a sofa in Newcastle upon Tyne, UK](https://commons.wikimedia.org/wiki/File:Black_cat_curled_up_on_a_sofa_in_Newcastle_upon_Tyne,_UK.jpg) | CC0 |

People stills: `../cutout-speed/media/` (see `../cutout.md`).
