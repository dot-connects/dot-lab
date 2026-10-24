/* copy.js — every word the player reads (decided with the copy / cue / juice agents, 2026-10-24) */
"use strict";
(function () {
  const K = (window.KFR = window.KFR || {});
  K.COPY = {
    shop: "00반점", shopSub: "영수증",
    hook: "12초 웍질, 몇 점 나올까?", how: "폰을 <b>위로 휙</b> → 밥이 뜬다 · <b>옆으로 휙</b> → 옆으로<br>막대가 <b>금색 칸</b>에 올 때마다 휙 → 불맛<br><small>폰을 바닥과 평평하게 들면 웍도 평평</small>", go: "불 켜!", watchBtn: "구경만 할래",
    tipMotion: "폰을 위로 휙! 튕겨서 볶기", tipTouch: "화면을 위로 휙! 쓸어 올려서 볶기",
    cueMotion: "폰을 위로 휙!", cueTouch: "화면을 위로 쓸어 올려!", cueSmall: "위 막대 금색 칸마다 또 휙", cueProd: "불 식는다! 휙!",
    flip2Tip: "뒷면 익는 중… 초록 칸에서 한 번 더 휙", eggYolkUp: "노른자 위로!", watching: "구경 중…",
    perfect: "불맛 터짐!", perfectSub: ["어? 너 좀 하네?", "주방 들어와!", "+불맛"], good: "괜찮은데?", early: "아직 아니야", earlySub: "설익음", late: "눌었다…", lateSub: ["누룽지 서비스냐?", "손 놀지 마라"], tooHard: "너무 세!", goodChip: "고슬",
    finale: "불기둥 피날레!", finaleSub: "사장님이 박수 침", finaleWeak: "마지막이 약했다", finaleCue: "마지막 한 방!", finaleCueSub: "제일 높게 휙",
    sideTable: "옆 테이블 서비스!", sideTableSub: "손님: 이거 안 시켰는데요", memoSpill: "그 밥값 네 월급에서 깐다",
    url: "https://dot-connects.github.io/dot-lab/works/2026-10-23-kimchi-fried-rice/site/", urlShort: "dot-connects.github.io/dot-lab · 김치볶음밥 한 그릇", copied: "복사됨!",
    fever: "불쇼!", feverSub: "4초간 불맛 x1.3",
    spill: "바닥에 밥!", spillSub: ["아이고 내 밥!", "그거 다 돈이야!", "바닥이 먹었다"], scorch: "불 끄라고!!", comboLost: "어허, 연속 끊김",
    combo: [[2, "불맛 UP"], [3, "웍질 장인"], [5, "불의 지배자"], [7, "웍신(神)"]],
    plated: "볶음밥 완성!",
    rimTip: "계속 휙휙! 바늘이 금색 칸에 올 때 휙 = 불맛",
    eggTip: (o) => (o === "half" ? "반숙" : "완숙") + " · 막대가 초록 칸에 오면 휙 → 다시 차서 초록이면 또 휙",
    flipCount: (n) => `뒤집기 ${n}/2`, eggSide1: "앞면", eggSide2: "뒷면", eggSide2Late: "노른자 굳는다!",
    eggCatch: "기울여서 그림자 밑으로!", eggHop: "살짝 더 세게!",
    intro: [[0, "기름 두르고…"], [0.6, "밥 넣고…"], [1.4, "김치!"], [2.0, "볶아!"]], introSmall: "폰을 바닥과 평평하게",
    eggBurning: "계란 탄다!",
    eggPerfect: "완벽하게 뒤집었다", eggGood: "뒤집었다", eggWall: "벽에 걸렸다", eggFold: "반으로 접혔다", eggBurst: "노른자 터짐!", eggBurstSub: "계란 화면 강타!", eggOut: "계란이 바닥에…",
    tiers: [[92, "불맛 장인", "내일부터 주방 맡아"], [80, "동네 중국집 에이스", "다음 주부터 출근해"], [65, "자취 3년 차", "먹을 만은 하네"], [45, "알바 2주 차", "설거지부터 해"], [0, "사장님 나와 보세요", "우리 가게 오지 마"]],
    again: "한 번 더", share: "자랑하기",
  };
})();
