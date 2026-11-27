// The AI's answers are untrusted input: keep only what refers to shots we sent, enforce the counts the schema
// cannot (structured outputs carry no length limits), and fall back to a sharpness-ranked book when an answer
// is missing or too thin. Two calls: /pick (which photos) and /write (text for the confirmed photos).
import 'dart:math' as math;
import 'models.dart';
import 'text_shape.dart';

const themes = ['a', 'b', 'c', 'diary', 'mag', 'album', 'photo'];

/// Photos and videos one book may carry into /write (its 48-image cap).
const maxPickPhotos = 44, maxPickVideos = 4, maxBookImages = 48;

String recommendTheme(List<Chapter> ch, {required bool hasDiaryNotes}) {
  if (hasDiaryNotes) return 'diary';
  if (ch.isEmpty) return 'a';
  final perDay = <String, Set<String>>{};
  for (final c in ch) {
    for (final x in c.items) {
      if (x.place != null) perDay.putIfAbsent(c.day, () => {}).add(x.place!);
    }
  }
  final avg = perDay.values.fold<int>(0, (s, v) => s + v.length) / ch.map((c) => c.day).toSet().length;
  return avg >= 3 ? 'b' : 'a';
}

/// Keep at most [maxPickPhotos] photos and [maxPickVideos] videos, dropping the lowest scores first;
/// must-include ids are never dropped.
List<Pick> capPicks(List<Pick> picks, Map<String, Candidate> byId, Set<String> must) {
  final ranked = [...picks]..sort((a, b) {
      final m = (must.contains(b.id) ? 1 : 0) - (must.contains(a.id) ? 1 : 0);
      return m != 0 ? m : b.score.compareTo(a.score);
    });
  var photos = 0, videos = 0;
  final keep = <String>{};
  for (final p in ranked) {
    final video = byId[p.id]?.shot.isVideo ?? false;
    if (video ? videos < maxPickVideos : photos < maxPickPhotos) {
      keep.add(p.id);
      video ? videos++ : photos++;
    }
  }
  return [for (final p in picks) if (keep.contains(p.id)) p];
}

/// [ranked]: [sent] is already best first (the phone's recipe order), so that order stays instead of sharpness;
/// [limit]: photos the book aims for (videos and must ids on top).
BookPlan fallbackPlan(List<Candidate> sent, BookOptions o, {String theme = 'a', Set<String> must = const {}, bool ranked = false, int? limit}) {
  final all = ranked ? sent : ([...sent]..sort((a, b) => b.sharp.compareTo(a.sharp)));
  final keep = {for (final c in all.where((c) => !c.shot.isVideo).take(limit ?? all.length)) c.id};
  final r = [for (final c in all) if (c.shot.isVideo || keep.contains(c.id) || must.contains(c.id)) c];
  final n = r.length;
  final picks = [for (var i = 0; i < n; i++) Pick(r[i].id, '', n == 1 ? 100 : 100 - (i * 100 ~/ (n - 1)))];
  return BookPlan(
    titles: const ['', '', ''],
    theme: o.theme ?? theme,
    picks: capPicks(picks, {for (final c in sent) c.id: c}, must),
    aiText: false,
  );
}

const kinds = {'풍경', '사람', '음식', '실내', '이동', '물건', '기타'};

/// /pick answer -> picks (no captions yet), ambiguous pairs, clips, chapter lines, theme.
/// [fallback]: the book to use when the answer is missing or too thin (the caller's recipe order and length).
BookPlan checkPick(Map<String, dynamic>? ai, List<Candidate> sent, BookOptions o,
    {String recommended = 'a', Set<String> must = const {}, BookPlan Function()? fallback}) {
  if (ai == null) return fallback?.call() ?? fallbackPlan(sent, o, theme: recommended, must: must);
  final byId = {for (final c in sent) c.id: c};
  final seen = <String>{};
  var picks = <Pick>[];
  for (final p in (ai['picks'] as List? ?? const [])) {
    if (p is! Map) continue;
    final id = p['id'], score = p['score'];
    if (id is! String || !byId.containsKey(id) || !seen.add(id)) continue;
    final kind = p['kind'], what = p['what'];
    picks.add(Pick(id, '', score is num ? score.round().clamp(0, 100) : 50,
        kind: kind is String && kinds.contains(kind) ? kind : '', what: what is String ? what.trim().substring(0, math.min(30, what.trim().length)) : ''));
  }
  if (picks.length < 5) return fallback?.call() ?? fallbackPlan(sent, o, theme: recommended, must: must);
  for (final m in must) {
    if (byId.containsKey(m) && seen.add(m)) picks.add(Pick(m, '', 50));
  }
  picks = capPicks(picks, byId, must);

  final picked = {for (final p in picks) p.id};
  final ask = <(String, String)>[];
  for (final a in (ai['ask'] as List? ?? const [])) {
    if (ask.length == 5) break;
    if (a is! Map) continue;
    final x = a['a'], y = a['b'];
    if (x is String && y is String && x != y && picked.contains(x) && picked.contains(y)) ask.add((x, y));
  }

  final clips = <Clip>[];
  for (final c in (ai['clips'] as List? ?? const [])) {
    if (c is! Map) continue;
    final shot = byId[c['id']]?.shot;
    if (shot == null || !shot.isVideo || !picked.contains(shot.id) || clips.any((x) => x.id == shot.id)) continue;
    final s = (c['startMs'] as num?)?.toInt() ?? 0, e = (c['endMs'] as num?)?.toInt() ?? 0;
    final ok = s >= 0 && e > s && e <= shot.durationMs && e - s <= 6000;
    clips.add(ok ? Clip(shot.id, s, e) : Clip(shot.id, 0, shot.durationMs < 4000 ? shot.durationMs : 4000));
  }

  final groups = <(String, List<String>)>[];
  for (final g in (ai['groups'] as List? ?? const [])) {
    if (g is! Map || groups.length == 4) continue;
    final k = g['kind'], ids = [for (final x in (g['ids'] as List? ?? const [])) if (x is String && picked.contains(x)) x];
    final fresh = ids.toSet().where((x) => groups.every((o) => !o.$2.contains(x))).toList();
    if ((k == '연속' || k == '음식') && fresh.length >= 2) groups.add((k as String, fresh.take(6).toList()));
  }
  final covers = [
    for (final x in (ai['covers'] as List? ?? const []))
      if (x is String && picked.contains(x) && !(byId[x]?.shot.isVideo ?? true)) x,
  ].take(3).toList();

  final aiTheme = ai['theme'];
  return BookPlan(
    titles: const ['', '', ''],
    theme: o.theme ?? (themes.contains(aiTheme) ? aiTheme as String : recommended),
    picks: picks,
    ask: ask,
    chapterLines: _lines(ai['chapters']),
    clips: clips,
    aiText: false,
    groups: groups,
    covers: covers,
  );
}

Map<String, String> _lines(Object? xs) => {
      for (final c in (xs as List? ?? const []))
        if (c is Map && c['day'] is String && c['line'] is String) c['day'] as String: (c['line'] as String).trim(),
    };

/// The text a /write call produced for one shape.
class WriteResult {
  final List<String> titles;
  final Map<String, String> captions; // pick id -> text
  final Map<String, String> chapterLines;
  final String essay;
  const WriteResult(this.titles, this.captions, this.chapterLines, this.essay);
  Map<String, dynamic> toJson() => {'titles': titles, 'captions': captions, 'chapterLines': chapterLines, 'essay': essay};
  factory WriteResult.fromJson(Map<String, dynamic> j) => WriteResult((j['titles'] as List).cast<String>(),
      (j['captions'] as Map).cast<String, String>(), (j['chapterLines'] as Map).cast<String, String>(), j['essay'] as String);
}

/// /write answer -> text only for the confirmed photos, each within its shape's length (too long = empty).
/// Null when the answer is missing (the book stays at "picked" and the app offers [다시 시도]).
WriteResult? checkWrite(Map<String, dynamic>? ai, List<String> pickedIds, String shape) {
  if (ai == null) return null;
  final ids = pickedIds.toSet();
  final caps = <String, String>{};
  for (final c in (ai['captions'] as List? ?? const [])) {
    if (c is! Map) continue;
    final id = c['id'], text = c['text'];
    if (id is String && ids.contains(id) && text is String && !caps.containsKey(id)) caps[id] = fitText(text, shape);
  }
  final titles = [for (final t in (ai['titles'] as List? ?? const [])) if (t is String && t.trim().isNotEmpty) t.trim()].take(3).toList();
  while (titles.length < 3) {
    titles.add('');
  }
  final lines = _lines(ai['chapters']);
  if (shape == 'mag') lines.updateAll((_, v) => fitText(v, 'mag'));
  return WriteResult(titles, caps, lines, ai['essay'] is String ? (ai['essay'] as String).trim() : '');
}

/// Apply the user's choices between ambiguous pairs. [chosen] is keyed "a|b" with the pair as the AI sent it.
/// An unanswered pair ([다 맡기기]) keeps the higher-scored photo.
BookPlan resolveAsk(BookPlan p, Map<String, String> chosen) {
  final score = {for (final x in p.picks) x.id: x.score};
  final drop = <String>{};
  for (final (a, b) in p.ask) {
    final keep = chosen['$a|$b'] ?? ((score[a] ?? 0) >= (score[b] ?? 0) ? a : b);
    drop.add(keep == a ? b : a);
  }
  return BookPlan(
    titles: p.titles,
    theme: p.theme,
    picks: [for (final x in p.picks) if (!drop.contains(x.id)) x],
    chapterLines: p.chapterLines,
    essay: p.essay,
    clips: [for (final c in p.clips) if (!drop.contains(c.id)) c],
    aiText: p.aiText,
  );
}
