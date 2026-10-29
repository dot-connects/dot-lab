# Making video brief (Round 8, 2026-10-29)

## The work
「따로 같이 네컷」 — a free web photo booth (D:OT lab, account 「그냥 시켜봄」). Person A shoots 6 shots on their own
phone, picks 4, sends a link. Friend B opens it anywhere, later, and shoots 6 shots standing *next to A's cut-out*
in each cell; picks 4. Both then decorate (frame, filter, stickers, retouch) and each saves on their own phone.
Promise: "멀리 있어도 한 칸에" — apart, yet in one frame. No sign-up, photos auto-deleted after 7 days.

## What we are designing
The second download, now named **"메이킹 영상"** (bonus), next to "움직이는 네컷" (the 4 cells as short loops).
Portrait 1080x1920 (story size), rendered on the phone in the browser (canvas + MediaRecorder / WebCodecs), about
6-15 s. It must:
1. make the person who downloads it **feel good / want to keep it** (a keepsake of this moment with this friend) —
   this comes FIRST (operator: "don't think only about story promotion");
2. also work as a post on Instagram story / SNS that makes viewers want to try it (promotion second).
The operator likes the app's cover animation: on a black film strip A's shots appear one by one with a flash
("딱 딱 딱 딱"), then B fills in behind A in the same frames, then the 4 picked frames snap into a strip ("착!").
Earlier mockup feedback: liked the handwritten hook "우리 이거 같이 안 찍음" and a split "따로 → 같이" idea; wants
the cover-animation feel in it.

## Real material available on the phone at render time (per side, 4 picked cells)
- raw still (JPG, the person's real room/background, 4:3 landscape)
- cut-out still (PNG with alpha)
- a 2-second clip per cell: the last 2 s of the countdown up to the shutter ("3, 2, 1" moment — people
  getting into pose, laughing, adjusting), raw and matte
- the finished decorated strip (the chosen frame, filter, stickers, caption, date), the moving strip
- metadata: date of each side's shoot (A's and B's dates/times can differ — hours or days apart), frame/theme
  name, caption text the user typed (e.g. "D+100"), shape (vertical strip or wide)
- NOT available: location, names, audio (clips are silent; we can add synthesized SFX or a short music bed)

## Constraints
- Korean UI copy, casual 반말 tone like the app ("찍었어", "착!").
- The app has two themes: "film" (black/white/lime #d6ff3d, bold) and "cute" (pink, Jua font, stickers).
- Render must be light enough for a mid-range Android phone in a few seconds; no server.
- A link sticker on Instagram is added by the user on top; leave a calm area in the lower ~15% (no text there).
- No names/locations we don't have. No fake data.
