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
