/* copy.js — every word the player reads (decided with the copy / cue / juice agents, 2026-10-24) */
"use strict";
(function () {
  const K = (window.KFR = window.KFR || {});
  K.COPY = {
    shop: "00반점", shopSub: "영수증",
    hook: "12초 웍질, 몇 점 나올까?", how: "폰을 <b>위로 휙</b> → 밥이 뜬다<br>웍 테두리가 <b>금빛</b>일 때 휙 → 불맛", go: "불 켜!", watchBtn: "구경만 할래",
    tipMotion: "폰을 위로 휙! 튕겨서 볶기", tipTouch: "화면을 위로 휙! 쓸어 올려서 볶기",
    cueMotion: "폰을 위로 휙!", cueTouch: "화면을 위로 쓸어 올려!", cueSmall: "첫 휙에 12초 시작", cueProd: "불 식는다! 휙!",
    flip2Tip: "한 번 더 휙! 노른자를 위로", eggYolkUp: "노른자 위로!", watching: "구경 중…",
    perfect: "불맛 터짐!", perfectSub: "+불맛", good: "괜찮은데?", early: "아직 아니야", earlySub: "설익음", late: "눌었다…", lateSub: "바닥이 눌어붙음", tooHard: "너무 세!", goodChip: "고슬",
    spill: "바닥에 밥!", scorch: "탄다 탄다!", comboLost: "연속 끊김",
    combo: [[2, "불맛 UP"], [3, "웍질 장인"], [5, "불의 지배자"], [7, "웍신(神)"]],
    plated: "볶음밥 완성!",
    rimTip: "테두리가 금빛일 때 휙!",
    eggTip: (o, touch) => `계란 ${o === "half" ? "반숙" : "완숙"} · ${touch ? "손가락으로 좌우로 흔들어 보고" : "폰을 살짝 흔들어 보고"}, 스르륵 미끄러지면 휙!`,
    eggCatch: "기울여서 그림자 밑으로!",
    eggStuck: "아직 붙어 있어", eggSlides: "스르륵~", eggBurning: "계란 탄다!",
    eggPerfect: "완벽하게 뒤집었다", eggGood: "뒤집었다", eggWall: "벽에 걸렸다", eggFold: "반으로 접혔다", eggBurst: "노른자 터짐!", eggOut: "계란이 바닥에…",
    tiers: [[92, "불맛 장인"], [80, "동네 중국집 에이스"], [65, "자취 3년 차"], [45, "알바 2주 차"], [0, "엄마 불러와"]],
    again: "한 번 더", share: "자랑하기",
  };
})();
