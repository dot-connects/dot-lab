# Round 8 — operator phone review 10-29 → decisions 10-30

Mockups: `mockups/r8/` (done-story.html, story2.html, making*.html). Debate log: three subagents (marketing, photo,
motion) two rounds + review round; conclusions folded into the picks below.

## Already shipped (6052de6, 515c147)
1 delete button gone · 3 camera-permission note gone · 5 pinch zoom grows the whole preview box · 6 save-both gone,
videos download when done (iOS keeps a tap) · 7/trust line "D:OT lab · 사진은 7일 동안만 다운 가능해요" · 9 decorate
theme tab gone · 10 file names to the second.

## Picked, to build
| # | What | Pick |
|---|---|---|
| A | Done-link thumbnail (B's "완성" link) | done-story.html #1 "빈자리가 채워짐": the send og.png with the dashed seat filled by the friend, "우리 네컷 나왔어!", pill "7일 동안만 · 따로 같이 네컷". Static image; the done link goes to its own small page with its own og tags, which forwards to the app |
| B | Story video → **메이킹 영상** | making5.html / making6.html: both people's cell i play the last 1 s of their pre-shutter clip at once, rows 1→4 (딱딱딱딱), rolls touch 0.2 s, rush in (ease-in), hard cut + flash + 2-frame shake, "착!" stamp beside the strip 1 s, the decorated strip hangs and sways (4°, ω 2.6, decay .55), save date under it, last 0.3 s fades to the background. Text = title "따로 같이 네컷" + date only. Clips get the user's filter + 보정 (same fx path as the moving strip). Per UI theme: 말랑부스 = pink dots (making4 #1); 필름레일 = concrete grey #cfd0cc, black film, lime gate corners, lime flash, lime stamp, lime tape + lime offset shadow, date lime on black |
| C | Save sheet | making.html C: two cards "움직이는 네컷" / "메이킹 영상" (보너스 ribbon) + black bubble below "SNS에 올려서 자랑해 줘 🙌 / 링크 복사했어! 링크도 붙여 주면 더 좋아"; the link is copied when the sheet opens |
| D | My own link from another browser | Worker keeps a hash of (IP + screen size + DPR + OS) from A's upload; status says `mine` when it matches → A's wait screen. "내가 보낸 링크면 여기" removed; the wait screen gets an underlined text button "혹시 친구가 보낸 링크야? 여기서 찍기 →" |

## Built (10-30)
| # | Commit | Note |
|---|---|---|
| D | 8a9611d, 52b5fec, 9b988cb | Worker `mine`; same fingerprint on A and B → null. Device rides as `?d=` (a header broke the live app: Pages deployed, Worker not) |
| C | 2686d3d, aadd628 | cards + bubble; making preview = my cut-out; in-app sheet leaves the clipboard alone |
| A | 2686d3d | `done.html` + `og-done.png` (mockups/r8/og-done.html) |
| B | 2686d3d, 5d5fba3, aadd628, 22edee8 | `js/making.js` draws, `compose.js recordMaking` encodes; rows = each person cut out over the chosen preset (a room pick → theme booth: cute `booth-pink`, film `white-wall`); start = 1 s before the last sample, hold at the end |

Verified: worker tests 18/18; e2e (scratchpad r8/e2e.mjs) wide·film and tall·cute pass except the old landB
contrast probe (cover animation mid-frame); `mine.mjs` 3 browsers; live create+status against the old Worker.
Reviews: Claude r8 (High 0; Medium 2 + Low 3 fixed), /done r1 High 1 (live CORS) · r2 High 0 (Medium 1 + Low 1 fixed).

## Open
- **Worker not deployed** (operator 10-30: try more first). Until then the live app cannot recognise my own link
  in another browser, and the "내가 보낸 링크면 여기" button is gone: A opening their own link in KakaoTalk lands on
  the friend's camera. Links made before the deploy never match (no `a.dev`).
- Fingerprint limits: iCloud Private Relay / CGNAT IP changes miss; same Wi-Fi + same iPhone model matches (escape link).
- WebM clips (old browsers) → making rows show stills. No WebCodecs → making = stills in real time.
- Not run on an iPhone.

## Phone test fixes (10-30)
| Operator report | Cause | Fix | Commit |
|---|---|---|---|
| Instagram (Android): 5-4-3-2-1 then 3-2-1 again, every shot | each shot's clip came out empty → shootOne's straight retry | after one empty clip the page records straight (MediaRecorder from "3", no pause); only shot 1 repeats. `stats.still.cuts[i].retried` = what failed (path, type, size) | 072e772 |
| Decorate zoom covered the top bar and menus | Round 6 #11 grew the box over the page (z-index 20) | `#s-done .previewwrap { overflow: clip }`: grows and pans inside the white area only | 072e772 |
| Chrome: second video "여러 파일 다운로드" prompt, then nothing downloads | the second auto-save ran with no touch since the first; refused = the tab blocks downloads | auto-save only with a pointerdown after the last download, else "버튼을 누르면 저장돼" | 072e772 |
| Pick (KakaoTalk A): two cells' cut-outs broken | not uploaded — the fast live cut-out of unpicked shots; the 4 uploaded HQ cuts were clean (checked from R2) | all backgrounds usable at once; HQ for all 6 even on WASM (was: picked 4 only); line "정밀 누끼 작업 중… n/6" under the buttons; cells swap as each lands. A locked-until-HQ version (e024fb9) was dropped: ~1 min on a WASM phone | e024fb9, c2c82c0 |

Verified headless: broken VideoEncoder stub → shot 1 `5432103210`, shots 2-3 `543210`; live link zoom 1×→5× stays in the
white area (https://claude.ai/artifact/KP7BokcujisDm66QPfe6fi); pick on `?scpu` (WASM) line 0/6→hidden, grid fixed;
B pick after A upload. Local runs: serve the site on 8799 (wrangler.toml ORIGINS), give B another screen size (else
the Worker's `mine` sends B to A's wait screen). The download fix was not run on a phone.
/done review: r1 High 0 (Medium 2, Low 5) · r2 High 0 (Low 3); fixed: recorder failure stops the frame loop,
steady line, WASM upload wait 60 s, one-line text (9a7ef07). Left: reshoot during an in-flight WASM cut shares CPU;
`straightOnly` stays for the page; B without ghost on "내 배경" shows no badge; clip cuts the preview shadow 1 px;
a failed straight shot still counts 3-2-1 once more.

