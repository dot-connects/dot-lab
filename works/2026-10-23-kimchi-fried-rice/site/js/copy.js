/* copy.js — every word the player reads (decided with the copy / cue / juice agents, 2026-10-24) */
"use strict";
(function () {
  const K = (window.KFR = window.KFR || {});
  K.COPY = {
    shop: "00반점", shopSub: "영수증",
    hook: "폰이 웍이에요.", how: "<b>위로 휙</b> 튕기면 볶아지고, <b>기울이면</b> 웍이 기울어요", go: "불 켜기", watchBtn: "먼저 구경하기",
    tipMotion: "폰을 위로 휙! 튕겨서 볶기", tipTouch: "화면을 위로 휙! 쓸어 올려서 볶기", watching: "구경 중…",
    perfect: "불맛!", perfectSub: "+불맛", good: "좋아", early: "너무 빨라", earlySub: "아직 덜 볶였어", late: "늦었어", lateSub: "바닥이 눌어", tooHard: "너무 세!",
    spill: "흘렸다!", scorch: "탄다 탄다!", comboLost: "연속 끊김",
    combo: [[3, "웍질 좀 하네"], [5, "불맛 장인 모드"], [8, "중국집 사장님 등장"]],
    plated: "볶음밥 완성!",
    eggTip: (o) => `계란 ${o === "half" ? "반숙" : "완숙"} · 흔들어서 스르륵 미끄러지면 휙!`,
    eggStuck: "아직 붙어 있어", eggSlides: "스르륵~", eggBurning: "계란 탄다!",
    eggPerfect: "완벽하게 뒤집었다", eggGood: "뒤집었다", eggWall: "벽에 걸렸다", eggFold: "반으로 접혔다", eggBurst: "노른자 터짐!", eggOut: "계란이 바닥에…",
    tiers: [[92, "불맛 장인"], [80, "동네 중국집 에이스"], [62, "자취 3년 차"], [0, "엄마 부르기"]],
    again: "한 번 더", share: "자랑하기",
  };
})();
