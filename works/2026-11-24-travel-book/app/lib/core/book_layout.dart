// The AI editor's page plan (/layout) -> checked -> repaired PageSpecs. Port of mockups/r11/engine/engine2.py.
// checkLayout lists what to send back for one retry; repairLayout always yields a usable book from whatever came back
// (unknown layouts and photos dropped, texts cut to the slot limit, cover forced first, index/stamps filled by code).
import 'dart:math' as math;

import 'models.dart';

class LayoutRules {
  final int lo, hi, minDistinct;
  final double proseePerDay, memoRatio;
  final int textPages; // pages of a 'text' layout (words only) at most: a book of them reads half empty
  const LayoutRules(this.lo, this.hi, this.minDistinct, {this.proseePerDay = 0, this.memoRatio = 0, this.textPages = 2});
  Map<String, Object> toJson(int shots) => {'pages': pageRange(shots), 'min_distinct': minDistinct};

  /// Long trips get more pages: 40+ photos -> 0.4..0.75 pages a photo.
  List<int> pageRange(int shots) => shots <= 40 ? [lo, hi] : [math.max(lo, (shots * 0.4).round()), math.max(hi, (shots * 0.75).round())];
}

// mockups/r11/engine/themes2/<theme>.json
const layoutRules = {
  'a': LayoutRules(16, 26, 5, proseePerDay: 1, textPages: 1),
  'photo': LayoutRules(16, 26, 6),
  'album': LayoutRules(14, 22, 6, memoRatio: 0.45),
  'b': LayoutRules(16, 26, 7, memoRatio: 0.6),
  'c': LayoutRules(16, 26, 7, memoRatio: 0.7),
  'diary': LayoutRules(16, 26, 7, textPages: 6),
  'mag': LayoutRules(16, 26, 7, textPages: 4),
};

bool isIndexLayout(LayoutDef d) => d.id.contains('/ix');

/// What the model chooses from (index pages are appended by code, never chosen).
List<Map<String, Object>> layoutCatalog(List<LayoutDef> defs) => [
      for (final d in defs)
        if (!isIndexLayout(d)) {'id': d.id, 'kind': d.kind, 'slots': d.slots, 'texts': d.limits, 'when': d.when},
    ];

String orientOf(Shot s) {
  if (s.w <= 0 || s.h <= 0) return 'L';
  final r = s.w / s.h;
  return r < 0.95 ? 'P' : (r > 1.05 ? 'L' : 'S');
}

bool slotFits(String slot, Shot s) {
  if (slot == 'V') return s.isVideo;
  if (s.isVideo) return false;
  final o = orientOf(s);
  final r = s.h == 0 ? 1.0 : s.w / s.h;
  return slot == 'A' || slot == o || (slot == 'S' && r >= 0.8 && r <= 1.25) || (o == 'S' && (slot == 'P' || slot == 'L'));
}

List<Map<String, dynamic>> _pages(Map<String, dynamic> body) =>
    [for (final p in (body['pages'] as List? ?? const [])) if (p is Map<String, dynamic>) p];
List<Map<String, dynamic>> _list(Object? x) => [for (final e in (x as List? ?? const [])) if (e is Map<String, dynamic>) e];
String _s(Object? x) => x is String ? x : '';

// "협재, 오후 한 시. …" and "서산 14:16, …": place + time first (operator's 태안 book 11-27: every caption read like that)
final _timed = RegExp(r'^[^,.\d]{1,14}[,.]?\s*(오전|오후|아침|저녁|낮|밤|새벽|해 질|해질|\d{1,2}\s*[:시])');
final _clock = RegExp(r'\d{1,2}:\d{2}');
/// "탔고, 갔고, 봤고" — a past-tense syllable (final consonant ㅆ) + 고: the "did X, did Y" itinerary chain.
int _pastAnd(String s) {
  var n = 0;
  for (var i = 0; i + 2 < s.length; i++) {
    final u = s.codeUnitAt(i);
    if (u >= 0xAC00 && u <= 0xD7A3 && (u - 0xAC00) % 28 == 20 && s[i + 1] == '고' && (s[i + 2] == ',' || s[i + 2] == ' ')) n++;
  }
  return n;
}

/// Problems worth one more call. Empty = good as is.
List<String> checkLayout(Map<String, dynamic> body, List<LayoutDef> defs, Map<String, Candidate> pool, LayoutRules rules) {
  final errs = <String>[];
  final by = {for (final d in defs) d.id: d};
  final pages = _pages(body);
  final range = rules.pageRange(pool.length);
  if (pages.length < range[0] || pages.length > range[1]) errs.add('쪽 수 ${pages.length}: ${range[0]}~${range[1]}');
  final used = <String, int>{};
  for (final (i, p) in pages.indexed) {
    final n = i + 1;
    final d = by[_s(p['layout'])];
    if (d == null || isIndexLayout(d)) {
      errs.add('$n쪽: 없는 틀 ${p['layout']}');
      continue;
    }
    if ((n == 1) != (d.kind == 'cover')) errs.add('$n쪽: 표지 틀은 1쪽에만, 1쪽은 표지 틀');
    final ids = [for (final x in _list(p['photos'])) _s(x['id'])];
    if (ids.length != d.slots.length) errs.add('$n쪽 ${d.id}: 사진 ${ids.length}장, 이 틀의 칸은 ${d.slots.length}개 (${d.slots.join()})');
    for (var k = 0; k < math.min(ids.length, d.slots.length); k++) {
      final c = pool[ids[k]];
      if (c == null) {
        errs.add('$n쪽: 없는 사진 ${ids[k]}');
        continue;
      }
      used[ids[k]] = (used[ids[k]] ?? 0) + 1;
      if (!slotFits(d.slots[k], c.shot)) errs.add('$n쪽 ${d.id}: ${ids[k]}(${c.shot.isVideo ? 'V' : orientOf(c.shot)}) 는 ${d.slots[k]} 칸에 맞지 않는다');
    }
    for (final t in _list(p['texts'])) {
      final k = _s(t['key']), v = _s(t['text']);
      final max = d.limits[k];
      if (max == null && !d.texts.contains(k)) {
        errs.add('$n쪽 ${d.id}: 글 칸 $k 없음 (있는 칸: ${d.texts.isEmpty ? '없음' : d.texts.join(', ')})');
      } else if (max != null && v.length > max) {
        errs.add('$n쪽 ${d.id}.$k: ${v.length}자 > $max자');
      }
    }
  }
  final cover = pages.isEmpty ? <String>{} : {for (final x in _list(pages.first['photos'])) _s(x['id'])};
  used.forEach((k, v) {
    if (v > 1 && !(v == 2 && cover.contains(k))) errs.add('$k: $v번 썼다 (표지 사진만 본문에 한 번 더)');
  });
  if (pages.length > 1 && _list(pages[1]['photos']).any((x) => cover.contains(_s(x['id'])))) {
    errs.add('2쪽: 표지 사진을 바로 다음 쪽에 다시 썼다 — 다른 사진으로');
  }
  final short = [
    for (final p in pages.skip(1))
      for (final t in _list(p['texts']))
        if (!const {'title', 'period', 'stamp', 'colophon'}.contains(_s(t['key'])) && _s(t['text']).length < 60) _s(t['text']),
  ];
  final timed = [for (final t in short) _timed.hasMatch(t)];
  final nTimed = timed.where((x) => x).length;
  if (short.isNotEmpty && nTimed > math.max(2, short.length * 0.3)) {
    errs.add("짧은 글 ${short.length}개 중 $nTimed개가 '장소, 시각' 으로 시작한다 — 3할 이하로, 나머지는 본 것·한 일·들은 말로 시작해라");
  }
  final clocked = short.where(_clock.hasMatch).length;
  if (clocked > 0) errs.add('짧은 글 $clocked개에 시각을 숫자(14:16)로 썼다 — 시각은 필요할 때만, "오후 두 시" "해 질 무렵"처럼 말로');
  final textOnly = [for (final p in pages) if (by[_s(p['layout'])]?.kind == 'text') p].length;
  if (textOnly > rules.textPages) {
    errs.add('글만 있는 쪽이 $textOnly쪽이다. 최대 ${rules.textPages}쪽 — 나머지 글은 사진과 함께 있는 틀(글 칸 있는 photo 틀)에 넣어라');
  }
  final bodies = [for (final p in pages) for (final t in _list(p['texts'])) if (_s(t['key']) == 'body') _s(t['text'])];
  final days = {for (final c in pool.values) ymd(c.local)}.length;
  if (rules.proseePerDay > 0) {
    final need = math.min((days * rules.proseePerDay).round(), 8);
    if (bodies.length < need) errs.add('짧은 글(body)이 ${bodies.length}번뿐이다. 최소 $need번 — 날마다 한 장면을 붙잡은 2~3문장을 사진과 함께 흩어 넣어라');
  }
  for (final b in bodies) {
    if (_pastAnd(b) >= 2 || '에서는 '.allMatches(b).length >= 2) {
      errs.add("글이 일정을 나열한다: 「${b.substring(0, math.min(40, b.length))}…」 — 한 장면만 붙잡아 다시 써라");
    }
  }
  if (rules.memoRatio > 0 && pages.length > 2) {
    final mid = pages.sublist(1, pages.length - 1);
    final withText = mid.where((p) => _list(p['texts']).any((t) => _s(t['key']) != 'stamp' && _s(t['text']).isNotEmpty)).length;
    if (withText < mid.length * rules.memoRatio) errs.add('글이 있는 쪽이 $withText/${mid.length}쪽뿐이다. 최소 ${(rules.memoRatio * 100).round()}%');
  }
  final seq = [for (final p in pages) _s(p['layout'])];
  for (var i = 2; i < seq.length; i++) {
    if (seq[i] == seq[i - 1] && seq[i] == seq[i - 2]) {
      errs.add('${i - 1}~${i + 1}쪽: 같은 틀 ${seq[i]} 3쪽 연속');
      break;
    }
  }
  final bodySeq = [for (final s in seq) if (by[s]?.kind == 'photo') s];
  final counts = <String, int>{};
  for (final s in bodySeq) {
    counts[s] = (counts[s] ?? 0) + 1;
  }
  final top = counts.entries.fold<MapEntry<String, int>?>(null, (a, e) => a == null || e.value > a.value ? e : a);
  if (top != null && bodySeq.length >= 8 && top.value > bodySeq.length * 0.3) {
    errs.add('본문 ${bodySeq.length}쪽 중 ${top.value}쪽이 같은 틀 ${top.key} 이다(3할 넘음) — 장면에 맞는 다른 틀을 섞어라');
  }
  final bodyLayouts = bodySeq.toSet();
  final photoDefs = defs.where((d) => d.kind == 'photo').length;
  if (bodyLayouts.isNotEmpty && bodyLayouts.length < math.min(rules.minDistinct, photoDefs)) {
    errs.add('본문 틀을 ${bodyLayouts.length}가지만 썼다. 최소 ${rules.minDistinct}가지 — 장면에 맞는 다른 틀을 골라라');
  }
  return errs;
}

const _months = ['JAN', 'FEB', 'MAR', 'APR', 'MAY', 'JUN', 'JUL', 'AUG', 'SEP', 'OCT', 'NOV', 'DEC'];

/// Always a book: what the model got wrong is dropped or cut, never shown broken.
({String title, List<PageSpec> pages}) repairLayout(Map<String, dynamic> body, List<LayoutDef> defs, Map<String, Candidate> pool,
    {required String fallbackTitle, String? coverPhoto}) {
  final by = {for (final d in defs) d.id: d};
  final pages = <PageSpec>[];
  final usedBody = <String>{};
  for (final p in _pages(body)) {
    final d = by[_s(p['layout'])];
    if (d == null || isIndexLayout(d)) continue;
    final photos = [for (final x in _list(p['photos'])) if (pool.containsKey(_s(x['id']))) x];
    if (photos.length < d.slots.length) continue; // an empty slot would show a hole
    final ids = <String>[], crop = <String>[];
    for (final x in photos.take(d.slots.length)) {
      ids.add(_s(x['id']));
      final fx = ((x['fx'] as num?) ?? 50).clamp(0, 100), fy = ((x['fy'] as num?) ?? 50).clamp(0, 100);
      crop.add('${fx.round()}% ${fy.round()}%');
    }
    if (d.kind != 'cover' && ids.any(usedBody.contains)) continue; // a photo twice inside the book
    if (d.kind != 'cover') usedBody.addAll(ids);
    final text = <String, String>{};
    for (final t in _list(p['texts'])) {
      final k = _s(t['key']);
      if (!d.texts.contains(k)) continue;
      final max = d.limits[k];
      final v = _s(t['text']).trim();
      text[k] = max != null && v.length > max ? v.substring(0, max) : v;
    }
    pages.add(PageSpec(d.id, ids, crop: crop, text: text));
  }
  // cover first, exactly once
  final covers = pages.where((p) => by[p.layout]?.kind == 'cover').toList();
  pages.removeWhere((p) => by[p.layout]?.kind == 'cover');
  final coverDef = defs.where((d) => d.kind == 'cover' && d.slots.length == 1).firstOrNull;
  final title = _s(body['title']).trim().isNotEmpty ? _s(body['title']).trim() : fallbackTitle;
  if (covers.isNotEmpty) {
    pages.insert(0, covers.first);
  } else if (coverDef != null) {
    final pic = coverPhoto ?? pool.values.where((c) => !c.shot.isVideo).firstOrNull?.id;
    if (pic != null) pages.insert(0, PageSpec(coverDef.id, [pic], text: {'title': title}));
  }
  _stamps(pages, by, pool);
  final ix = defs.where(isIndexLayout).toList()..sort((a, b) => a.slots.length.compareTo(b.slots.length));
  if (ix.isNotEmpty) {
    final order = [for (final p in pages.skip(1)) ...p.ids].where((id) => !(pool[id]?.shot.isVideo ?? true)).toSet().toList();
    // the largest index the book can fill; evenly sampled when it has more photos (an empty cell would show a hole)
    final d = ix.lastWhere((d) => d.slots.length <= order.length, orElse: () => ix.first);
    final n = d.slots.length;
    if (order.length >= n) pages.add(PageSpec(d.id, [for (var i = 0; i < n; i++) order[(i * (order.length - 1) / math.max(n - 1, 1)).round()]]));
  }
  return (title: title, pages: pages);
}

/// A layout with a 'stamp' slot gets the developer's date ("JUN 13") of the first photo of that day in the book.
void _stamps(List<PageSpec> pages, Map<String, LayoutDef> by, Map<String, Candidate> pool) {
  final seen = <String>{};
  for (final p in pages.skip(1)) {
    for (final id in p.ids) {
      final c = pool[id];
      if (c == null) continue;
      final day = ymd(c.local);
      if (seen.add(day) && (by[p.layout]?.texts.contains('stamp') ?? false) && (p.text['stamp'] ?? '').isEmpty) {
        p.text['stamp'] = '${_months[c.local.month - 1]} ${c.local.day}';
      }
    }
  }
}
