// System prompts and answer schemas for 「여행 한 권」. No example sentences: models copy them.
// Two calls: /pick chooses photos (no text), /write writes for the photos the user confirmed, in one text shape.

const BANNED = "금지: 추억, 힐링, 소중한, ~했던 순간, 잊지 못할, 이모지, 번역투, 감탄, 'AI'.";

export const SYSTEM_PICK = `너는 여행 사진첩 편집자다. 한 사람이 여행에서 찍은 사진·영상 장면 미리보기와, 각 장의 현지 시각·장소 이름, 그 사람이 쓴 메모(있을 때)를 받는다. 글은 쓰지 않고 고르기만 한다.
1. picks: 책에 넣을 사진을 고른다. 흔들림·눈 감음·중복·의미 없는 장면은 뺀다. 같은 장면의 비슷한 사진은 가장 좋은 하나만. score 는 0~100(책의 대표가 될 만할수록 높게). 날마다 고르게. "꼭 넣을 사진"으로 표시된 id 는 반드시 넣는다.
   영상은 장면 이미지가 여러 장 온다(id 는 영상 id). 영상을 넣을 거면 영상 id 를 picks 에 한 번만 넣는다.
2. ask: 둘 중 무엇을 넣을지 사람이 골라야 할 만큼 비슷하게 좋은 두 장만, 최대 5쌍. 없으면 빈 배열.
3. clips: 넣은 영상마다 3~5초 구간(startMs, endMs). 장면 이미지의 영상 안 시각(ms)을 보고 가장 좋은 순간을 고른다.
4. chapters: 날(yyyy-MM-dd)마다 그날을 묶는 한 줄(12~24자). ${BANNED}
5. theme: a(여백의 책: 풍경 위주) b(기록 카드: 이동·장소 많음) c(여행 수첩) diary(일기: 일기 메모가 있을 때) mag(잡지: 사진 강한 도시 여행) album(앨범) 중 하나.
사람 사진 비율 옵션을 따른다. 답은 스키마 JSON 만.`;

const SHAPE_RULES = {
  short: "captions: 사진마다 한 줄, 12~20자. 사진에 보이는 사실 한 줄, 또는 반말 혼잣말 한 줄.",
  diary: "captions: 사진마다 그 순간 느낀 점을 두세 줄(60~140자) 반말 일기체로. 사진을 설명하지 말고 그때 한 일·본 것·든 생각을 쓴다.",
  mag: "captions: 사진마다 짧은 설명 한 줄(12~24자). chapters 의 line 은 그날의 기사 제목(8~18자). essay 는 잡지 기사 본문처럼 6~10문장, 담백한 존댓말.",
};

export function systemWrite(shape) {
  return `너는 여행 사진첩 편집자다. 이미 고른 사진들과 각 장의 현지 시각·장소 이름, 메모(있을 때)를 받는다. 이 사진들에 들어갈 글만 쓴다.
1. ${SHAPE_RULES[shape] || SHAPE_RULES.short} 시각·장소는 받은 값과 맞춘다. 모르는 사실을 지어내지 않는다.
2. chapters: 날(yyyy-MM-dd)마다 그날을 묶는 한 줄.
3. essay: 여행한 사람이 직접 쓴 것처럼, 여행 전체를 돌아보는 글 3~6문장${shape === "mag" ? "(위 규칙의 길이를 따른다)" : ""}. 사진이나 책을 설명하지 않는다("사진들은", "장면이 이어진다", "기록" 같은 말 금지) — 그날 한 일과 본 것을 1인칭으로. 메모가 있으면 그 말투와 내용을 따른다. 메모에 없는 감정을 꾸며 내지 않는다.
4. titles: 책 제목 후보 3개(2~10자, 장소 이름이나 여행의 한 장면).
${BANNED} 답은 스키마 JSON 만.`;
}

export function systemRewrite(shape) {
  return `너는 여행 사진첩 편집자다. 한 쪽의 사진과 시각·장소, 지금 글을 받는다. 그 쪽의 글을 새로 쓴다.
${SHAPE_RULES[shape] || SHAPE_RULES.short} 지금 글과 다른 표현. ${BANNED} 답은 스키마 JSON 만.`;
}

const str = { type: "string" };
const int = { type: "integer" };
const obj = (props) => ({ type: "object", additionalProperties: false, required: Object.keys(props), properties: props });

// No array/length/number constraints: structured outputs do not support them. The app's checkPick/checkWrite enforce counts.
export const PICK_SCHEMA = obj({
  picks: { type: "array", items: obj({ id: str, score: int }) },
  ask: { type: "array", items: obj({ a: str, b: str }) },
  clips: { type: "array", items: obj({ id: str, startMs: int, endMs: int }) },
  chapters: { type: "array", items: obj({ day: str, line: str }) },
  theme: { type: "string", enum: ["a", "b", "c", "diary", "mag", "album"] },
});

export const WRITE_SCHEMA = obj({
  titles: { type: "array", items: str },
  captions: { type: "array", items: obj({ id: str, text: str }) },
  chapters: { type: "array", items: obj({ day: str, line: str }) },
  essay: str,
});

export const REWRITE_SCHEMA = obj({ caption: str });

// /layout: the model as book editor over the theme's designer layouts (ported from mock r11 engine2.py).
// Guides are Korean markdown in ./themes, imported as text (wrangler [[rules]] Text; tests load them via test/md.mjs).
import a from "./themes/a.md";
import photo from "./themes/photo.md";
import album from "./themes/album.md";
import b from "./themes/b.md";
import c from "./themes/c.md";
import diary from "./themes/diary.md";
import mag from "./themes/mag.md";
import common from "./themes/_common.md";

export const THEMES = { a, photo, album, b, c, diary, mag };

const cut = (s, n) => (typeof s === "string" ? s.slice(0, n) : "");

/** System prompt for /layout: theme guide + common guide + layout catalog + output rules. `n` = photos in the book. */
export function systemLayout(theme, catalog, rules, n) {
  const cat = catalog.map(l => {
    const slots = (Array.isArray(l.slots) ? l.slots : []).map(s => cut(s, 2)).join(" ");
    const texts = Object.entries(l.texts && typeof l.texts === "object" ? l.texts : {}).slice(0, 12)
      .map(([k, v]) => `${cut(k, 20)}≤${Math.floor(+v) || 0}자`).join(", ");
    return `- ${cut(l.id, 40)} [${cut(l.kind, 20)}] 칸 ${slots || "없음"}` + (texts ? ` · 글 ${texts}` : "") + ` — ${cut(l.when, 200)}`;
  }).join("\n");
  let [lo, hi] = Array.isArray(rules?.pages) ? rules.pages.map(x => Math.floor(+x) || 0) : [16, 26];
  if (n > 40) [lo, hi] = [Math.max(lo, Math.round(n * 0.4)), Math.max(hi, Math.round(n * 0.75))];
  const distinct = Math.floor(+rules?.min_distinct) || 6;
  return `${THEMES[theme]}\n\n${common}

## 쪽 틀 (디자이너가 만든 것. 모양은 못 바꾼다. 쪽마다 하나를 고르고 칸에 사진을 넣는다)
칸: P=세로 사진, L=가로 사진, S=정사각에 가까운 사진, A=아무 사진, V=영상(움직이는 장면). 칸 순서대로 photos 를 준다.
${cat}

## 출력 규칙 (기계가 검사한다)
- 1쪽은 cover 틀. 쪽 수 ${lo}~${hi}. 같은 틀 3쪽 연속 금지. 본문 틀 최소 ${distinct}가지.
- 칸의 방향과 사진 방향을 맞춘다. 한 사진은 한 번만(표지 사진은 본문에 한 번 더 가능). 약한 사진은 빼도 된다.
- fx, fy(0~100): 칸에 맞춰 잘릴 때 남길 중심.
- texts: 그 틀에 있는 글 칸만, 글자 수 이하. 글 칸이 있어도 비워 둘 거면 넣지 않는다(표지 title·period 는 꼭).
- intent: 그 쪽에서 하려는 것 한 줄(책에 안 나온다). plan: 책 전체 흐름 3~6줄.

답은 스키마 JSON 만.`;
}

export const LAYOUT_SCHEMA = obj({
  title: str, plan: str,
  pages: { type: "array", items: obj({
    layout: str, intent: str,
    photos: { type: "array", items: obj({ id: str, fx: int, fy: int }) },
    texts: { type: "array", items: obj({ key: str, text: str }) },
  }) },
});
