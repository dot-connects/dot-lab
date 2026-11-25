// The AI's answer is untrusted input: keep only what refers to shots we sent, and fall back to a book
// without text when the answer is missing or too thin.
import 'models.dart';

const themes = ['a', 'b', 'c', 'diary', 'mag', 'album'];

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

BookPlan fallbackPlan(List<Candidate> sent, BookOptions o, {String theme = 'a'}) {
  final ranked = [...sent]..sort((a, b) => b.sharp.compareTo(a.sharp));
  final n = ranked.length;
  return BookPlan(
    titles: const ['', '', ''],
    theme: o.theme ?? theme,
    picks: [for (var i = 0; i < n; i++) Pick(ranked[i].id, '', n == 1 ? 100 : 100 - (i * 100 ~/ (n - 1)))],
    aiText: false,
  );
}

BookPlan checkPlan(Map<String, dynamic>? ai, List<Candidate> sent, BookOptions o, {String recommended = 'a'}) {
  if (ai == null) return fallbackPlan(sent, o, theme: recommended);
  final byId = {for (final c in sent) c.id: c};
  final seen = <String>{};
  final picks = <Pick>[];
  for (final p in (ai['picks'] as List? ?? const [])) {
    if (p is! Map) continue;
    final id = p['id'], cap = p['caption'], score = p['score'];
    if (id is! String || !byId.containsKey(id) || !seen.add(id)) continue;
    final text = cap is String && cap.trim().length <= 40 ? cap.trim() : '';
    picks.add(Pick(id, text, score is num ? score.round().clamp(0, 100) : 50));
  }
  if (picks.length < 5) return fallbackPlan(sent, o, theme: recommended);

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
    if (shot == null || !shot.isVideo) continue;
    final s = (c['startMs'] as num?)?.toInt() ?? 0, e = (c['endMs'] as num?)?.toInt() ?? 0;
    final ok = s >= 0 && e > s && e <= shot.durationMs && e - s <= 6000;
    clips.add(ok ? Clip(shot.id, s, e) : Clip(shot.id, 0, shot.durationMs < 4000 ? shot.durationMs : 4000));
  }

  final titles = [for (final t in (ai['titles'] as List? ?? const [])) if (t is String && t.trim().isNotEmpty) t.trim()].take(3).toList();
  while (titles.length < 3) {
    titles.add('');
  }
  final aiTheme = ai['theme'];
  return BookPlan(
    titles: titles,
    theme: o.theme ?? (themes.contains(aiTheme) ? aiTheme as String : recommended),
    picks: picks,
    ask: ask,
    chapterLines: {
      for (final c in (ai['chapters'] as List? ?? const []))
        if (c is Map && c['day'] is String && c['line'] is String) c['day'] as String: (c['line'] as String).trim(),
    },
    essay: ai['essay'] is String ? (ai['essay'] as String).trim() : '',
    clips: clips,
    aiText: true,
  );
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
