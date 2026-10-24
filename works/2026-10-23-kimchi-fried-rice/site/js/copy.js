/* copy.js — every word the player reads (decided with the copy / cue / juice agents, 2026-10-24) */
"use strict";
(function () {
  const K = (window.KFR = window.KFR || {});
  K.COPY = {
    shop: "00반점", shopSub: "영수증",
    hook: "12초 웍질, 몇 점 나올까?", how: "폰을 <b>위로 휙</b> → 밥이 뜬다<br>밥 가장자리에 <b>연기</b>가 오를 때 휙 → 불맛<br><small>폰을 바닥과 평평하게 들면 웍도 평평</small>", go: "불 켜!", watchBtn: "구경만 할래",
    tipMotion: "폰을 위로 휙! 튕겨서 볶기", tipTouch: "화면을 위로 휙! 쓸어 올려서 볶기",
    cueMotion: "폰을 위로 휙!", cueTouch: "화면을 위로 쓸어 올려!", cueSmall: "연기 오를 때마다 또 휙", cueProd: "불 식는다! 휙!",
    flip2Tip: "한 번 더 휙! 노른자를 위로", eggYolkUp: "노른자 위로!", watching: "구경 중…",
    perfect: "불맛 터짐!", perfectSub: ["어? 너 좀 하네?", "주방 들어와!", "+불맛"], good: "괜찮은데?", early: "아직 아니야", earlySub: "설익음", late: "눌었다…", lateSub: ["누룽지 서비스냐?", "손 놀지 마라"], tooHard: "너무 세!", goodChip: "고슬",
    fever: "불쇼!", feverSub: "4초간 불맛 x1.3",
    spill: "바닥에 밥!", spillSub: ["아이고 내 밥!", "그거 다 돈이야!", "바닥이 먹었다"], scorch: "불 끄라고!!", comboLost: "어허, 연속 끊김",
    combo: [[2, "불맛 UP"], [3, "웍질 장인"], [5, "불의 지배자"], [7, "웍신(神)"]],
    plated: "볶음밥 완성!",
    rimTip: "계속 휙휙! 바늘이 금색 칸에 올 때 휙 = 불맛",
    eggTip: (o) => o === "half" ? "반숙 · 오른쪽 막대가 초록 칸에 오면 휙! (2번 뒤집기)" : "완숙 · 오른쪽 막대가 초록 칸에 오면 휙! (2번 뒤집기)",
    flipCount: (n) => `뒤집기 ${n}/2`, eggGauge: "익힘",
    eggCatch: "기울여서 그림자 밑으로!", eggHop: "살짝 더 세게!",
    intro: [[0, "기름 두르고…"], [0.6, "밥 넣고…"], [1.4, "김치!"], [2.0, "볶아!"]], introSmall: "폰을 바닥과 평평하게",
    eggBurning: "계란 탄다!",
    eggPerfect: "완벽하게 뒤집었다", eggGood: "뒤집었다", eggWall: "벽에 걸렸다", eggFold: "반으로 접혔다", eggBurst: "노른자 터짐!", eggBurstSub: "계란 화면 강타!", eggOut: "계란이 바닥에…",
    tiers: [[92, "불맛 장인", "내일부터 주방 맡아"], [80, "동네 중국집 에이스", "다음 주부터 출근해"], [65, "자취 3년 차", "먹을 만은 하네"], [45, "알바 2주 차", "설거지부터 해"], [0, "사장님 나와 보세요", "우리 가게 오지 마"]],
    again: "한 번 더", share: "자랑하기",
  };
})();
