// How the phone narrows a trip down before and between the AI looks (operator 11-27, Fable review
// docs/review-2026-11-27-fable-picking-and-making.md): the trip as moments (time gaps and moves), the number of shots
// per moment as "how much that place mattered", same-frame bursts merged but pose series kept, a wide shortlist for the
// first look (/scout) with more room for busy moments, and the finalists for /pick chosen by a recipe over the tags.
// The AI looks and names; this code counts and guarantees.
import 'dart:math' as math;

import 'models.dart';
import 'prefilter.dart' show hamming;

/// A new moment after this long without a photo, or after moving this far.
const momentGap = Duration(minutes: 20);
const momentMove = 300.0; // metres

double _metres(double la1, double lo1, double la2, double lo2) {
  const r = 6371000.0, d = math.pi / 180;
  final a = math.pow(math.sin((la2 - la1) * d / 2), 2) + math.cos(la1 * d) * math.cos(la2 * d) * math.pow(math.sin((lo2 - lo1) * d / 2), 2);
  return 2 * r * math.asin(math.sqrt(a));
}

/// [xs] split into moments, time order. A move counts only between two shots that both carry a position
/// (the last known position is carried across shots without one).
List<List<T>> momentsBy<T>(List<T> xs, DateTime Function(T) at, (double, double)? Function(T) pos) {
  final s = [...xs]..sort((a, b) => at(a).compareTo(at(b)));
  final out = <List<T>>[];
  (double, double)? last;
  for (final x in s) {
    final p = pos(x);
    final cur = out.isEmpty ? null : out.last;
    final split = cur == null ||
        at(x).difference(at(cur.last)) > momentGap ||
        (p != null && last != null && _metres(last.$1, last.$2, p.$1, p.$2) > momentMove);
    if (split) out.add([]);
    out.last.add(x);
    if (p != null) last = p;
  }
  return out;
}

(double, double)? posOf(Candidate c) => c.shot.lat != null && c.shot.lng != null ? (c.shot.lat!, c.shot.lng!) : null;

/// Frames <= 1.5 s apart that look the same (dHash distance <= 6) are one frame: the sharpest stays.
/// Then runs of 3+ frames, each <= 15 s after the one before with a similar picture (distance <= 18), are a pose
/// series: [series] maps each member's id to its series number (1, 2, …). Nothing of a series is dropped here.
/// A [must] photo is never merged into another frame (nor replaced by a sharper twin).
({List<Candidate> frames, Map<String, int> series}) collapse(List<Candidate> good, {int firstSeries = 1, Set<String> must = const {}}) {
  final s = [...good]..sort((a, b) => a.local.compareTo(b.local));
  final frames = <Candidate>[];
  for (final c in s) {
    final l = frames.isEmpty ? null : frames.last;
    if (l != null &&
        !must.contains(c.id) &&
        !must.contains(l.id) &&
        c.local.difference(l.local).inMilliseconds.abs() <= 1500 &&
        hamming(c.dhash, l.dhash) <= 6) {
      if (c.sharp > l.sharp) frames[frames.length - 1] = c;
      continue;
    }
    frames.add(c);
  }
  final series = <String, int>{};
  var n = firstSeries, i = 0;
  while (i < frames.length) {
    var j = i;
    while (j + 1 < frames.length &&
        frames[j + 1].local.difference(frames[j].local).inSeconds <= 15 &&
        hamming(frames[j + 1].dhash, frames[j].dhash) <= 18) {
      j++;
    }
    if (j - i + 1 >= 3) {
      for (var k = i; k <= j; k++) {
        series[frames[k].id] = n;
      }
      n++;
    }
    i = j + 1;
  }
  return (frames: frames, series: series);
}

/// Room per moment out of [cap]: in proportion to sqrt(its shots) (a busy place gets more, not all), at least 1,
/// never more than it has. [sizes] = shots per moment, [have] = frames left to choose from.
List<int> slots(List<int> sizes, List<int> have, int cap) {
  if (sizes.length > cap) {
    // more moments than room: one each for the busiest [cap] of them
    final order = [for (var i = 0; i < sizes.length; i++) i]..sort((a, b) => sizes[b].compareTo(sizes[a]));
    final top = order.take(cap).toSet();
    return [for (var i = 0; i < sizes.length; i++) top.contains(i) && have[i] > 0 ? 1 : 0];
  }
  final w = [for (final n in sizes) math.sqrt(math.max(1, n))];
  final sum = w.fold(0.0, (a, b) => a + b);
  final out = [for (var i = 0; i < sizes.length; i++) math.min(have[i], math.max(1, (cap * w[i] / sum).floor()))];
  // hand what is left to the moments with the most unused weight
  var left = cap - out.fold(0, (a, b) => a + b);
  while (left > 0) {
    var best = -1;
    var gap = 0.0;
    for (var i = 0; i < out.length; i++) {
      if (out[i] >= have[i]) continue;
      final g = cap * w[i] / sum - out[i];
      if (best < 0 || g > gap) {
        best = i;
        gap = g;
      }
    }
    if (best < 0) break;
    out[best]++;
    left--;
  }
  return out;
}

/// [n] of [xs] (time order) spread over its time: [n] equal stretches, the sharpest of each - sharpness only
/// compared inside the stretch, so a soft-light moment still gives its best.
List<Candidate> spread(List<Candidate> xs, int n) {
  if (xs.length <= n) return [...xs];
  final out = <Candidate>[];
  for (var k = 0; k < n; k++) {
    final part = xs.sublist(k * xs.length ~/ n, (k + 1) * xs.length ~/ n);
    out.add(part.reduce((a, b) => b.sharp > a.sharp ? b : a));
  }
  return out;
}

/// The first look's shortlist: per moment its [slots] spread over time, plus up to 4 frames of each pose series and
/// every [must] id. [moments] hold the moment's frames (after [collapse]); [sizes] = shots taken there.
List<Candidate> shortlist(List<List<Candidate>> moments, List<int> sizes, Map<String, int> series, int cap, {Set<String> must = const {}}) {
  final room = slots(sizes, [for (final m in moments) m.length], cap);
  final out = <Candidate>[], seen = <String>{};
  void add(Candidate c) {
    if (seen.add(c.id)) out.add(c);
  }

  for (final (i, m) in moments.indexed) {
    for (final c in m) {
      if (must.contains(c.id)) add(c);
    }
    for (final c in spread(m, room[i])) {
      add(c);
    }
    final bySeries = <int, List<Candidate>>{};
    for (final c in m) {
      final s = series[c.id];
      if (s != null) bySeries.putIfAbsent(s, () => []).add(c);
    }
    for (final xs in bySeries.values) {
      for (final c in spread(xs, 4)) {
        add(c);
      }
    }
  }
  out.sort((a, b) => a.local.compareTo(b.local));
  if (out.length <= cap) return out;
  // series and must ids went on top of the slots: back to [cap], must ids kept, the rest evenly in time
  final keep = out.where((c) => must.contains(c.id)).toList(), rest = out.where((c) => !must.contains(c.id)).toList();
  final n = math.max(0, cap - keep.length);
  return [...keep, for (var k = 0; k < n && rest.isNotEmpty; k++) rest[k * rest.length ~/ n]]..sort((a, b) => a.local.compareTo(b.local));
}

/// What the first look says about one photo.
class Tag {
  final String kind; // 풍경 같이 한명 음식 명소 활동 동물 신기 디테일 이동 실내 기타
  final int people, score; // score 1..5
  final bool eyes; // eyes closed / face hidden
  final int hl; // this trip's main scene: 2 = what sets the trip apart, 1 = its run-up / surroundings, 0 = any trip
  const Tag(this.kind, this.people, this.score, this.eyes, [this.hl = 0]);
  Map<String, Object> toJson() => {'k': kind, 'p': people, 's': score, 'e': eyes, 'h': hl};
}

/// The main scenes' share of the finalists: 1.35x their natural share, between 15% and 30%; none when the look
/// marked none (Fable 11-28, docs/review-2026-11-28-fable-highlights.md). The three numbers are the knobs.
double highlightShare(int marked, int tagged) => marked == 0 || tagged == 0 ? 0 : (1.35 * marked / tagged).clamp(.15, .30);

const scoutKinds = {'풍경', '같이', '한명', '음식', '명소', '활동', '동물', '신기', '디테일', '이동', '실내', '기타'};

/// Photos a book of each length aims for (videos come on top, up to 4).
const lengthTarget = {'short': 20, 'normal': 32, 'long': 44};

/// The finalists for /pick: about [count] photos from the tagged shortlist [xs] (time order), in this order:
/// must ids, the best of each moment, the recipe's minimum per kind, the two best pose series (up to 4 frames each),
/// then the rest by score (a busy moment counts a little more) under per-kind caps. [people] = the book option
/// (mix | scenery | people) moves the people share. Without tags (the look failed) the moments' spread decides.
List<Candidate> finalists(List<Candidate> xs, Map<String, Tag> tags, Map<String, int> momentOf, Map<int, int> momentSize, Map<String, int> series,
    int count, {Set<String> must = const {}, String people = 'mix'}) {
  if (xs.length <= count) return [...xs];
  Tag t(Candidate c) => tags[c.id] ?? const Tag('기타', 0, 3, false);
  double value(Candidate c) => t(c).score + .5 * t(c).hl + .35 * math.log(1 + (momentSize[momentOf[c.id]] ?? 1)) / math.ln10;
  final ok = [for (final c in xs) if (must.contains(c.id) || (t(c).score > 1 && !t(c).eyes)) c]..sort((a, b) => value(b).compareTo(value(a)));
  final out = <Candidate>[], seen = <String>{};
  final perKind = <String, int>{};
  // main scenes: their seats below, and never much past them later on (the final look is told "at most M")
  final mainCap = (count * highlightShare(xs.where((c) => t(c).hl == 2).length, xs.where((c) => tags.containsKey(c.id)).length) * 1.3).round();
  var mains = 0;
  bool add(Candidate c) {
    if (out.length >= count || seen.contains(c.id)) return false;
    if (t(c).hl == 2 && mains >= mainCap && !must.contains(c.id)) return false;
    seen.add(c.id);
    if (t(c).hl == 2) mains++;
    out.add(c);
    perKind[t(c).kind] = (perKind[t(c).kind] ?? 0) + 1;
    return true;
  }

  for (final c in xs) {
    if (must.contains(c.id)) add(c);
  }
  // the trip's main scenes first: their share of the room, spread over moments (sqrt) and time inside each
  final main = ok.where((c) => t(c).hl == 2).toList();
  final share = highlightShare(main.length, xs.where((c) => tags.containsKey(c.id)).length);
  if (share > 0) {
    final byMoment = <int, List<Candidate>>{};
    for (final c in main..sort((a, b) => a.local.compareTo(b.local))) {
      byMoment.putIfAbsent(momentOf[c.id] ?? -1, () => []).add(c);
    }
    final keys = byMoment.keys.toList();
    final room = slots([for (final k in keys) momentSize[k] ?? 1], [for (final k in keys) byMoment[k]!.length], (count * share).round());
    for (final (i, k) in keys.indexed) {
      for (final c in spread(byMoment[k]!, room[i])) {
        add(c);
      }
    }
  }
  // every moment once, the best-valued moments first (many moments of a trip hold 1-3 shots)
  final best = <int, Candidate>{};
  for (final c in ok) {
    best.putIfAbsent(momentOf[c.id] ?? -1, () => c);
  }
  final ms = best.keys.toList()..sort((a, b) => value(best[b]!).compareTo(value(best[a]!)));
  for (final m in ms.take((count * .45).round())) {
    add(best[m]!);
  }
  // the recipe's minimum per kind (a book of 32 photos is ~80 finalists: 같이 12, 한명 10, 음식 6, …)
  final pf = switch (people) { 'scenery' => .4, 'people' => 1.6, _ => 1.0 };
  final mins = {
    '같이': (count * .15 * pf).round(), '한명': (count * .12 * pf).round(), '음식': 6, '명소': 3, '활동': 3, '동물': 2, '신기': 2, '디테일': 3, '이동': 2,
  };
  for (final e in mins.entries) {
    for (final c in ok) {
      if ((perKind[e.key] ?? 0) >= e.value) break;
      if (t(c).kind == e.key) add(c);
    }
  }
  // the two best pose series, up to 4 frames each
  final groups = <int, List<Candidate>>{};
  for (final c in xs) {
    final s = series[c.id];
    if (s != null && t(c).score > 1) groups.putIfAbsent(s, () => []).add(c);
  }
  double avg(List<Candidate> g) => g.map((c) => t(c).score).reduce((x, y) => x + y) / g.length;
  final ranked = groups.entries.where((e) => e.value.length >= 3).toList()..sort((a, b) => avg(b.value).compareTo(avg(a.value)));
  for (final e in ranked.take(2)) {
    for (final c in spread(e.value, 4)) {
      add(c);
    }
  }
  // the rest by value, kinds capped so one kind never floods the book
  final caps = {'풍경': (count * .35).round(), '음식': 8, '이동': 4, '실내': 6, '디테일': 6, '기타': 4, '같이': (count * .3 * pf).round(), '한명': (count * .25 * pf).round()};
  for (final c in ok) {
    final k = t(c).kind;
    if ((perKind[k] ?? 0) >= (caps[k] ?? count)) continue;
    add(c);
  }
  for (final c in ok) {
    add(c); // room left: caps give way
  }
  return out..sort((a, b) => a.local.compareTo(b.local));
}

/// /scout's answer for one batch: tags by photo id ([ids] = the batch in the order sent; n = the index) and the batch's
/// phrase for its main scenes. Unknown kinds, out-of-range numbers and indexes outside the batch are dropped or
/// clamped (untrusted input).
({Map<String, Tag> tags, String top}) parseScout(Map<String, dynamic>? body, List<String> ids) {
  final out = <String, Tag>{};
  for (final t in (body?['tags'] as List? ?? const [])) {
    if (t is! Map) continue;
    final n = t['n'], k = t['k'];
    if (n is! int || n < 0 || n >= ids.length || k is! String || !scoutKinds.contains(k)) continue;
    final p = t['p'], s = t['s'];
    final h = t['h'];
    out[ids[n]] = Tag(k, p is num ? p.round().clamp(0, 9) : 0, s is num ? s.round().clamp(1, 5) : 3, t['e'] == true, h is num ? h.round().clamp(0, 2) : 0);
  }
  final top = body?['top'];
  return (tags: out, top: top is String ? top.trim().substring(0, math.min(24, top.trim().length)) : '');
}

/// 2295 -> "2,295"
String fmtCount(int n) => n.toString().replaceAllMapped(RegExp(r'\B(?=(\d{3})+(?!\d))'), (_) => ',');
