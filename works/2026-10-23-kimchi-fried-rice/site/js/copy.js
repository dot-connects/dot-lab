/* copy.js — every word the player reads (decided with the copy / cue / juice agents, 2026-10-24) */
"use strict";
(function () {
  const K = (window.KFR = window.KFR || {});
  const KO = {
    shop: "00반점", shopSub: "영수증",
    hook: "12초 웍질. 근데 불이 좀…", how: "폰을 <b>위로 휙</b> 튕기면 밥이 뜬다<br>움직이는 바늘이 <b>금색 칸</b>에 들 때 휙 → 불맛", go: "불 켜!", watchBtn: "구경만 할래",
    tipMotion: "폰을 위로 휙! 튕겨서 볶기", tipTouch: "화면을 위로 휙! 쓸어 올려서 볶기",
    cueMotion: "폰을 위로 휙!", cueTouch: "화면을 위로 쓸어 올려!", cueSmall: "바늘이 금색 칸에 들 때 또 휙", cueProd: "불 식는다! 휙!",
    flip2Tip: "뒷면 익는 중… 초록 칸에서 한 번 더 휙", eggYolkUp: "노른자 위로!", watching: "구경 중…",
    perfect: "불맛 터짐!", perfectSub: ["어? 너 좀 하네?", "주방 들어와!", "+불맛"], good: "괜찮은데?", early: "아직 아니야", earlySub: "설익음", late: "눌었다…", lateSub: ["누룽지 서비스냐?", "손 놀지 마라"], tooHard: "너무 세!", goodChip: "고슬",
    finale: "불기둥 피날레!", finaleSub: "사장님이 박수 침", finaleWeak: "마지막이 약했다", finaleCue: "마지막 한 방!", finaleCueSub: "제일 높게 휙",
    sideTable: "옆 테이블 서비스!", sideTableSub: "손님: 이거 안 시켰는데요", memoSpill: "그 밥값 네 월급에서 깐다",
    url: "https://dot-connects.github.io/dot-lab/works/2026-10-23-kimchi-fried-rice/site/", urlShort: "dot-connects.github.io/dot-lab · 김치볶음밥 한 그릇", copied: "복사됨!",
    loading: "불 올리는 중…", openInBrowser: "Safari·Chrome에서 열면 폰을 흔들어서 할 수 있어요", credit: "00반점 · D:OT lab · three.js (MIT) · Google Fonts (OFL)",
    cardVer: "v1", copyLink: "링크 복사",
    shareText: (n, t) => `00반점 김치볶음밥 ${n}점 「${t}」 나왔다. 이겨 볼 사람?`,
    shareWin: (v, n) => `친구 ${v}점을 ${n}점으로 눌렀다. 다음 도전자?`, shareLose: (v, n) => `친구 ${v}점… 나는 ${n}점. 복수해 줄 사람?`,
    vsHook: (v) => `친구가 ${v}점 받았다! 이겨 봐`, vsWin: (v, n) => `친구 ${v} vs 나 ${n} — 이겼다!`, vsTie: (v) => `친구 ${v} vs 나 ${v} — 비겼다`, vsLose: (v, n) => `친구 ${v} vs 나 ${n} — 졌다…`,
    howTouch: "화면을 <b>위로 쓸어 올리면</b> 밥이 뜬다<br>움직이는 바늘이 <b>금색 칸</b>에 들 때 쓸기 → 불맛",
    mashed: "연타! 콤보 끊김", egg2Early: "뒤집긴 했는데 이르다", egg2Late: "뒤집긴 했는데 늦었다",
    blackWarn: "사장님: 에어컨 켠다~", blackout: "정전!", blackoutSub: "감으로 휙",
    finaleBack: "불 들어왔다 — 마지막 한 방!", blackPerfect: "감 살아있네!", lightsBack: "불 들어왔다!", lightsBackSub: (k, n) => `깜깜이 ${k}/${n} 불맛`,
    blackMemo: [[3, "눈 감고도 볶는다"], [2, "감은 있네"], [1, "전기세 내고 와"], [0, "불 켜고 다시 와"]],
    blackLine: (k, n) => `깜깜할 때 금색 ${k}번 (${n}번 중)`, blackShare: (k, n) => ` 정전 웍질 ${k}/${n}`,
    fever: "불쇼!", feverSub: "4초간 불맛 x1.3",
    spill: "바닥에 밥!", spillSub: ["아이고 내 밥!", "그거 다 돈이야!", "바닥이 먹었다"], scorch: "불 끄라고!!", comboLost: "어허, 연속 끊김",
    combo: [[2, "불맛 UP"], [3, "웍질 좀 하네"], [5, "불판 에이스"], [7, "주방장 각"]],
    plated: "볶음밥 완성!",
    rimTip: "계속 휙휙! 바늘이 금색 칸에 올 때 휙 = 불맛",
    eggTip: (o) => (o === "half" ? "반숙" : "완숙") + " · 막대가 초록 칸에 오면 휙, 다시 초록이면 또 휙",
    flipCount: (n) => `뒤집기 ${n}/2`, eggSide1: "앞면", eggSide2: "뒷면", eggSide2Late: "노른자 굳는다!",
    eggCatch: "기울여서 그림자 밑으로!", eggCatchTouch: "손가락 떼지 말고 끌어서 그림자 밑으로!", eggHop: "살짝 더 세게!",
    intro: [[0, "기름 두르고…"], [0.6, "김치 먼저!"], [1.4, "찬밥 투하!"], [2.0, "볶아!"]], introSmall: "폰을 바닥과 평평하게", flatBig: "폰을 바닥에 눕혀요", flatSmall: "노란 점을 가운데로", flatOk: "좋아요, 그대로!",
    eggBurning: "계란 탄다!",
    eggPerfect: "완벽하게 뒤집었다", eggGood: "뒤집었다", eggWall: "벽에 걸렸다", eggFold: "반으로 접혔다", eggBurst: "노른자 터짐!", eggBurstSub: ["괜찮아, 김볶은 어차피 비벼 먹어", "노른자 셀프 비빔"], eggLensSub: ["렌즈가 반숙 먹음", "카메라도 배고팠다"], eggBurstReceipt: "노른자 터짐 — 셀프 비빔 서비스", eggOut: "계란이 바닥에…",
    tiers: [[92, "주방장", "내일부터 불 맡아"], [80, "불판 에이스", "다음 주부터 웍 잡아"], [65, "칼판", "칼질은 되네, 불은 아직"], [45, "면판 막내", "양파부터 까"], [0, "설거지 담당", "내일부터 철가방 들어"]],
    again: "한 번 더", share: "자랑하기", tryIt: "직접 해보기", watchedBy: "구경한 판 · 점수는 기록 안 됨",
    comboN: (n) => `${n} 연속`, fireN: (n) => `불맛 ${n}`, spilledN: (n) => `흘린 밥 ${n}알`, title: "김치볶음밥 한 그릇", muteOn: "소리 켜기", muteOff: "소리 끄기",
    bestLine: (b, t) => `내 최고 ${b}점 · 「${t}」 · `, order: (o) => `오늘의 주문: 계란 ${o === "half" ? "반숙" : "완숙"}`,
    rItem: (o) => `김치볶음밥 1 · 계란 ${o === "half" ? "반숙" : "완숙"}`, rRec: "신기록", rDone: "밥 익힘", rFire: "불맛 (박자·높이)", rClean: "안 흘리기", rTiming: "계란 타이밍", rFlip: "뒤집기", rCatch: "받기", rYolk: "노른자",
    rFloor: (n, w) => `바닥 밥 변상 <b>${n}</b>알 × 10원 = <b>${w}</b>원`, pts: "점", boss: "사장님: ", toNext: (t, n) => `「${t}」까지 ${n}점`, myBest: (n) => `내 최고 ${n}점`, more: "시켜본 것 더 보기 →",
    whyBurnt: (n) => `탄 밥 ${n}%`, whyRaw: (n) => `덜 익은 밥 ${n}%`, whySpill: (n) => `흘린 밥 ${n}%`, whyGap: (s) => `최대 ${s}초 안 볶음`, whyFire: (n) => `불맛 ${n}%`, whyCombo: "최고 콤보",
    whyGold: (p, n) => `금색 칸에 휙 ${p}/${n}번`, whyLate: " (쉬다가 눌린 게 많음)", whyAngle: (a) => `계란 ${a}°`, whyFold: "반으로 접힘", whyTorn: "덜 익어서 찢어짐", whyWall: "벽에서 받음", whyMiss: "계란을 못 받음", whyLeft: "뒷면 방치", whyFlip2: "두 번째 뒤집기 실패",
    whyFrontEarly: "앞면을 너무 일찍 뒤집음", whyFrontLate: "앞면을 너무 늦게 뒤집음", whyBackEarly: "뒷면을 너무 일찍 뒤집음", whyBackLate: "뒷면을 너무 늦게 뒤집음",
  };

  const EN = { ...KO,
    shop: "00 Palace", shopSub: "Receipt",
    hook: "12 seconds of wok work. But the fire's a bit…", how: "Flick your phone <b>up</b> to toss the rice<br>Flick as the needle hits the <b>gold zone</b> → fire flavor", go: "Light it!", watchBtn: "Just watching",
    tipMotion: "Flick the phone up to toss!", tipTouch: "Swipe up to toss!",
    cueMotion: "Flick the phone up!", cueTouch: "Swipe up!", cueSmall: "Flick again when the needle hits gold", cueProd: "Fire's dying! Flick!",
    flip2Tip: "Other side cooking… one more flick on green", eggYolkUp: "Yolk up!", watching: "Watching…",
    perfect: "Flame on!", perfectSub: ["Oh? Not bad.", "Get in my kitchen!", "+Fire"], good: "Not bad!", early: "Not yet", earlySub: "Undercooked", late: "Burnt…", lateSub: ["Free crispy rice?", "No slacking"], tooHard: "Too hard!", goodChip: "Fluffy",
    finale: "Fire pillar!", finaleSub: "The boss is clapping", finaleWeak: "Weak finish", finaleCue: "Last toss!", finaleCueSub: "Your highest one",
    sideTable: "Next table!", sideTableSub: "Customer: I didn't order this", memoSpill: "That rice comes out of your pay",
    copied: "Copied!",
    loading: "Heating up…", openInBrowser: "Open in Safari or Chrome to play by shaking your phone", credit: "00 Palace · D:OT lab · three.js (MIT) · Google Fonts (OFL)",
    copyLink: "Copy link",
    shareText: (n, t) => `Scored ${n} on kimchi fried rice at 00 Palace: "${t}". Beat that?`,
    shareWin: (v, n) => `Crushed my friend's ${v} with ${n}. Who's next?`, shareLose: (v, n) => `Friend got ${v}… I got ${n}. Avenge me?`,
    vsHook: (v) => `Your friend got ${v}! Beat it`, vsWin: (v, n) => `Friend ${v} vs me ${n} — I won!`, vsTie: (v) => `Friend ${v} vs me ${v} — a tie`, vsLose: (v, n) => `Friend ${v} vs me ${n} — I lost…`,
    howTouch: "<b>Swipe up</b> to toss the rice<br>Swipe as the needle hits the <b>gold zone</b> → fire flavor",
    mashed: "Button mashing! Combo gone", egg2Early: "Flipped, but early", egg2Late: "Flipped, but late",
    blackWarn: "Boss: Turning on the AC~", blackout: "Blackout!", blackoutSub: "Flick by feel",
    finaleBack: "Lights on — last toss!", blackPerfect: "Still got it!", lightsBack: "Lights on!", lightsBackSub: (k, n) => `Blind fire ${k}/${n}`,
    blackMemo: [[3, "Cooks with eyes shut"], [2, "You've got a feel for it"], [1, "Go pay the power bill"], [0, "Come back with the lights on"]],
    blackLine: (k, n) => `Gold in the dark ${k} of ${n}`, blackShare: (k, n) => ` Blackout wok ${k}/${n}`,
    fever: "Fire show!", feverSub: "Fire x1.3 for 4 s",
    spill: "Rice on floor!", spillSub: ["Aigoo, my rice!", "That's money!", "The floor ate it"], scorch: "Kill the heat!!", comboLost: "Hey, streak gone",
    combo: [[2, "Fire up"], [3, "Nice wok work"], [5, "Wok Ace"], [7, "Head chef material"]],
    plated: "Rice is up!",
    rimTip: "Keep flicking! Needle in gold = fire",
    eggTip: (o) => (o === "half" ? "Runny" : "Well done") + " · flick when the bar hits green, flick again on the next green",
    flipCount: (n) => `Flip ${n}/2`, eggSide1: "Side 1", eggSide2: "Side 2", eggSide2Late: "Yolk's setting!",
    eggCatch: "Tilt under the shadow!", eggCatchTouch: "Hold and drag under the shadow!", eggHop: "A bit harder!",
    intro: [[0, "Oil first…"], [0.6, "Kimchi in!"], [1.4, "Cold rice in!"], [2.0, "Fry it!"]], introSmall: "Hold your phone flat", flatBig: "Lay your phone flat", flatSmall: "Yellow dot to the middle", flatOk: "Good, hold it!",
    eggBurning: "Egg's burning!",
    eggPerfect: "Perfect flip", eggGood: "Flipped", eggWall: "Hit the wall", eggFold: "Folded in half", eggBurst: "Yolk burst!", eggBurstSub: ["It's fine, you mix it anyway", "Self-serve yolk sauce"], eggLensSub: ["Yolk on the lens", "Even the camera was hungry"], eggBurstReceipt: "Yolk burst — self-mix service", eggOut: "Egg on the floor…",
    tiers: [[92, "Head Chef", "The fire's yours from tomorrow"], [80, "Wok Ace", "Next week you hold the wok"], [65, "Prep Cook", "Knife's fine. Fire, not yet"], [45, "Noodle Rookie", "Start peeling onions"], [0, "Dish Pit", "You're on deliveries tomorrow"]],
    again: "Again", share: "Brag", tryIt: "Try it yourself", watchedBy: "Watched game · score not saved",
    comboN: (n) => `${n} in a row`, fireN: (n) => `Fire ${n}`, spilledN: (n) => `Spilled ${n}`, title: "Kimchi Fried Rice", muteOn: "Sound on", muteOff: "Sound off",
    bestLine: (b, t) => `Best ${b} · "${t}" · `, order: (o) => `Today's order: ${o === "half" ? "runny" : "well-done"} egg`,
    rItem: (o) => `Kimchi fried rice x1 · egg ${o === "half" ? "runny" : "well done"}`, rRec: "New best", rDone: "Rice cooked", rFire: "Fire (beat · height)", rClean: "No spills", rTiming: "Egg timing", rFlip: "Flip", rCatch: "Catch", rYolk: "Yolk",
    rFloor: (n, w) => `Floor rice damages <b>${n}</b> grains × ₩10 = ₩<b>${w}</b>`, pts: "pts", boss: "Boss: ", toNext: (t, n) => `${n} pt${n === 1 ? "" : "s"} to "${t}"`, myBest: (n) => `Your best ${n} pts`, more: "More experiments →",
    whyBurnt: (n) => `Burnt rice ${n}%`, whyRaw: (n) => `Raw rice ${n}%`, whySpill: (n) => `Spilled rice ${n}%`, whyGap: (s) => `Idle for ${s} s at most`, whyFire: (n) => `Fire ${n}%`, whyCombo: "Best combo",
    whyGold: (p, n) => `Gold flicks ${p}/${n}`, whyLate: " (mostly burnt from idling)", whyAngle: (a) => `Egg ${a}°`, whyFold: "Folded in half", whyTorn: "Tore, too raw", whyWall: "Caught on the wall", whyMiss: "Dropped the egg", whyLeft: "Side 2 abandoned", whyFlip2: "Second flip failed",
    whyFrontEarly: "Side 1 flipped too early", whyFrontLate: "Side 1 flipped too late", whyBackEarly: "Side 2 flipped too early", whyBackLate: "Side 2 flipped too late",
  };

  // ?lang= wins, then the viewer's choice, then the browser (same rule and key as draw-korea)
  const q = new URLSearchParams(location.search).get("lang"); let saved = null; try { saved = localStorage.getItem("lab.lang"); } catch (e) {}
  const lang = q === "en" || q === "ko" ? q : saved === "en" || saved === "ko" ? saved : /^ko/i.test(navigator.language) ? "ko" : "en";
  K.COPY = lang === "en" ? EN : KO;

  // this file loads right after the markup, ahead of three.js, so the static words change before the first paint
  const $ = (id) => document.getElementById(id), label = (id, t) => $(id).setAttribute("aria-label", t);
  if (lang === "en") {
    document.documentElement.lang = "en"; document.title = "Kimchi Fried Rice · D:OT lab";
    label("cv", "Wok view"); label("gauge", "Fire"); $("gauge").dataset.label = EN.fireN(0); label("home", "Home"); label("mute", EN.muteOff);
    $("riceN").textContent = "Rice 100%"; $("cookN").textContent = "Cooked 0%"; document.querySelector("#intro h1").innerHTML = "Kimchi<br>Fried Rice"; $("back").textContent = "‹ All experiments";
    $("noGL").innerHTML = "<b>No 3D in this browser</b><span>Please open it in Safari or Chrome</span>";
    const ov = $("shareOv"); ov.querySelector("img").alt = "Result image"; ov.children[1].textContent = "Send this link and your score receipt shows up as the preview"; $("shareCopy").textContent = EN.copyLink; ov.querySelector(".close").textContent = "Tap anywhere to close";
  }
  const b = $("lang"); b.textContent = lang === "en" ? "KO" : "EN"; label("lang", lang === "en" ? "Korean" : "English");
  b.addEventListener("pointerdown", (e) => e.stopPropagation()); // not a flick
  b.onclick = (e) => { e.stopPropagation(); const next = lang === "en" ? "ko" : "en", u = new URL(location.href); u.searchParams.set("lang", next); try { localStorage.setItem("lab.lang", next); } catch (er) {} location.href = u.href; };
})();
