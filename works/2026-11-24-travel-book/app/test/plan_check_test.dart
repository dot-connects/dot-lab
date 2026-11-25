import 'package:flutter_test/flutter_test.dart';
import 'package:tripbook/core/models.dart';
import 'package:tripbook/core/plan_check.dart';

Candidate c(String id, {int day = 12, bool video = false, int durMs = 0, double sharp = 500, String? place}) {
  final t = DateTime(2026, 6, day, 10, id.hashCode % 50);
  return Candidate(Shot(id: id, utc: t, isVideo: video, durationMs: durMs), sharp: sharp, luma: 120, dhash: 0, place: place, local: t);
}

Map<String, dynamic> ai({List<Map<String, dynamic>>? picks, String theme = 'a', List<Map<String, dynamic>> clips = const []}) => {
      'titles': ['제주', '바람', '귤'],
      'theme': theme,
      'picks': picks ?? [for (var i = 0; i < 6; i++) {'id': 'p$i', 'caption': '사진 $i', 'score': 50 + i}],
      'ask': [{'a': 'p1', 'b': 'p2'}],
      'chapters': [{'day': '2026-06-12', 'line': '서쪽'}],
      'essay': '좋았다',
      'clips': clips,
    };

void main() {
  final sent = [for (var i = 0; i < 8; i++) c('p$i', sharp: 100.0 + i)] + [c('v1', video: true, durMs: 9000)];
  final o = BookOptions();

  test('ids never sent are dropped, duplicates kept once', () {
    final p = checkPlan(ai(picks: [
      for (var i = 0; i < 5; i++) {'id': 'p$i', 'caption': '', 'score': 60},
      {'id': 'ghost', 'caption': 'x', 'score': 99},
      {'id': 'p0', 'caption': 'again', 'score': 10},
    ]), sent, o);
    expect(p.picks.map((x) => x.id), ['p0', 'p1', 'p2', 'p3', 'p4']);
    expect(p.aiText, isTrue);
  });

  test('a caption over 40 characters becomes empty, not cut', () {
    final p = checkPlan(ai(picks: [for (var i = 0; i < 5; i++) {'id': 'p$i', 'caption': i == 0 ? '가' * 41 : '짧다', 'score': 60}]), sent, o);
    expect(p.picks.first.caption, '');
    expect(p.picks[1].caption, '짧다');
  });

  test('unknown theme falls back to the recommendation; titles padded to 3, ask capped at 5', () {
    final j = ai(theme: 'neon')..['titles'] = ['하나']..['ask'] = [for (var i = 0; i < 7; i++) {'a': 'p0', 'b': 'p${i + 1}'}];
    final p = checkPlan(j, sent, o);
    expect(p.theme, 'a');
    expect(p.titles.length, 3);
    expect(p.ask.length, 5);
  });

  test('the user-chosen theme wins over the AI', () {
    final p = checkPlan(ai(theme: 'b'), sent, BookOptions()..theme = 'mag');
    expect(p.theme, 'mag');
  });

  test('a clip outside the video length becomes its first 4 seconds; clips on photos are dropped', () {
    final p = checkPlan(ai(clips: [
      {'id': 'v1', 'startMs': 8000, 'endMs': 12000},
      {'id': 'p0', 'startMs': 0, 'endMs': 3000},
    ]), sent, o);
    expect(p.clips.map((x) => '${x.id} ${x.startMs}-${x.endMs}'), ['v1 0-4000']);
  });

  test('fewer than 5 valid picks or no answer at all -> book without text', () {
    expect(checkPlan(ai(picks: [{'id': 'p0', 'caption': 'a', 'score': 1}]), sent, o).aiText, isFalse);
    final f = checkPlan(null, sent, o);
    expect(f.aiText, isFalse);
    expect(f.picks.length, sent.length);
    expect(f.picks.every((p) => p.caption.isEmpty), isTrue);
    expect(f.picks.first.score, greaterThanOrEqualTo(f.picks.last.score)); // sharper ranks higher
  });

  test('resolveAsk: the chosen photo stays, the other goes; unanswered keeps the higher score', () {
    final p = checkPlan(ai()..['ask'] = [{'a': 'p1', 'b': 'p2'}, {'a': 'p4', 'b': 'p3'}], sent, o);
    final r = resolveAsk(p, {'p1|p2': 'p2'});
    final ids = r.picks.map((x) => x.id).toSet();
    expect(ids.contains('p2') && !ids.contains('p1'), isTrue);
    expect(ids.contains('p4') && !ids.contains('p3'), isTrue); // p4 score 54 > p3 53
    expect(r.ask, isEmpty);
  });

  test('recommendTheme: diary notes -> diary, many places a day -> b, else a', () {
    Chapter ch(int places) => Chapter('2026-06-12', null, [for (var i = 0; i < places; i++) c('x$i', place: 'p$i')]);
    expect(recommendTheme([ch(1)], hasDiaryNotes: true), 'diary');
    expect(recommendTheme([ch(4)], hasDiaryNotes: false), 'b');
    expect(recommendTheme([ch(1)], hasDiaryNotes: false), 'a');
  });
}
