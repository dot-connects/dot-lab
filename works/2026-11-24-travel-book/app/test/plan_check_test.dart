import 'package:flutter_test/flutter_test.dart';
import 'package:tripbook/core/models.dart';
import 'package:tripbook/core/plan_check.dart';

Candidate c(String id, {int day = 12, bool video = false, int durMs = 0, double sharp = 500, String? place}) {
  final t = DateTime(2026, 6, day, 10, id.hashCode % 50);
  return Candidate(Shot(id: id, utc: t, isVideo: video, durationMs: durMs), sharp: sharp, luma: 120, dhash: 0, place: place, local: t);
}

Map<String, dynamic> pick({List<Map<String, dynamic>>? picks, String theme = 'a', List<Map<String, dynamic>> clips = const []}) => {
      'picks': picks ?? [for (var i = 0; i < 6; i++) {'id': 'p$i', 'score': 50 + i}],
      'ask': [{'a': 'p1', 'b': 'p2'}],
      'clips': clips,
      'chapters': [{'day': '2026-06-12', 'line': '서쪽'}],
      'theme': theme,
    };

void main() {
  final sent = [for (var i = 0; i < 8; i++) c('p$i', sharp: 100.0 + i)] + [c('v1', video: true, durMs: 9000)];
  final o = BookOptions();

  group('checkPick', () {
    test('ids never sent are dropped, duplicates kept once, picks carry no text yet', () {
      final p = checkPick(pick(picks: [
        for (var i = 0; i < 5; i++) {'id': 'p$i', 'score': 60},
        {'id': 'ghost', 'score': 99},
        {'id': 'p0', 'score': 10},
      ]), sent, o);
      expect(p.picks.map((x) => x.id), ['p0', 'p1', 'p2', 'p3', 'p4']);
      expect(p.picks.every((x) => x.caption.isEmpty), isTrue);
    });

    test('unknown theme falls back to the recommendation; ask capped at 5; user theme wins', () {
      final j = pick(theme: 'neon')..['ask'] = [for (var i = 0; i < 7; i++) {'a': 'p0', 'b': 'p${i + 1}'}];
      final p = checkPick(j, sent, o);
      expect(p.theme, 'a');
      expect(p.ask.length, 5);
      expect(checkPick(pick(theme: 'b'), sent, BookOptions()..theme = 'mag').theme, 'mag');
    });

    test('a clip outside the video becomes its first 4 seconds; clips on photos or unpicked videos are dropped', () {
      final p = checkPick(pick(picks: [for (var i = 0; i < 5; i++) {'id': 'p$i', 'score': 60}, {'id': 'v1', 'score': 70}], clips: [
        {'id': 'v1', 'startMs': 8000, 'endMs': 12000},
        {'id': 'p0', 'startMs': 0, 'endMs': 3000},
      ]), sent, o);
      expect(p.clips.map((x) => '${x.id} ${x.startMs}-${x.endMs}'), ['v1 0-4000']);
      expect(checkPick(pick(clips: [{'id': 'v1', 'startMs': 0, 'endMs': 3000}]), sent, o).clips, isEmpty);
    });

    test('fewer than 5 valid picks or no answer -> sharpness-ranked book without text', () {
      expect(checkPick(pick(picks: [{'id': 'p0', 'score': 1}]), sent, o).aiText, isFalse);
      final f = checkPick(null, sent, o);
      expect(f.picks.length, sent.length);
      expect(f.picks.first.score, greaterThanOrEqualTo(f.picks.last.score));
    });

    test('must-include ids the AI left out come back at score 50', () {
      final p = checkPick(pick(), sent, o, must: {'p7'});
      expect(p.picks.firstWhere((x) => x.id == 'p7').score, 50);
    });

    test('never more than 44 photos and 4 videos reach /write; must-includes survive the cut', () {
      final many = [for (var i = 0; i < 60; i++) c('x$i')] + [for (var i = 0; i < 6; i++) c('v$i', video: true, durMs: 5000)];
      final ai = {
        'picks': [for (var i = 0; i < 60; i++) {'id': 'x$i', 'score': i}] + [for (var i = 0; i < 6; i++) {'id': 'v$i', 'score': 90}],
        'ask': [], 'clips': [], 'chapters': [], 'theme': 'a',
      };
      final p = checkPick(ai, many, o, must: {'x0'}); // x0 has the lowest score
      final ids = p.picks.map((x) => x.id).toSet();
      expect(ids.where((i) => i.startsWith('x')).length, 44);
      expect(ids.where((i) => i.startsWith('v')).length, 4);
      expect(ids.contains('x0'), isTrue);
      expect(p.picks.length, lessThanOrEqualTo(maxBookImages));
      expect(fallbackPlan(many, o).picks.length, lessThanOrEqualTo(maxBookImages));
    });
  });

  group('checkWrite', () {
    final ids = ['p0', 'p1', 'p2'];
    Map<String, dynamic> w(List<Map<String, dynamic>> caps) =>
        {'titles': ['제주'], 'captions': caps, 'chapters': [{'day': '2026-06-12', 'line': '바다'}], 'essay': ' 좋았다 '};

    test('captions only for confirmed photos, first one wins, titles padded to 3', () {
      final r = checkWrite(w([
        {'id': 'p0', 'text': '짧다'},
        {'id': 'p0', 'text': '두 번째'},
        {'id': 'ghost', 'text': '없는 사진'},
      ]), ids, 'short')!;
      expect(r.captions, {'p0': '짧다'});
      expect(r.titles, ['제주', '', '']);
      expect(r.essay, '좋았다');
    });

    test('length by shape: a diary caption may be long, a short one may not', () {
      final long = '가' * 100;
      expect(checkWrite(w([{'id': 'p1', 'text': long}]), ids, 'short')!.captions['p1'], '');
      expect(checkWrite(w([{'id': 'p1', 'text': long}]), ids, 'diary')!.captions['p1'], long);
    });

    test('no answer -> null (the book stays picked, the app offers retry)', () {
      expect(checkWrite(null, ids, 'short'), isNull);
    });
  });

  test('resolveAsk: the chosen photo stays, the other goes; unanswered keeps the higher score', () {
    final p = checkPick(pick()..['ask'] = [{'a': 'p1', 'b': 'p2'}, {'a': 'p4', 'b': 'p3'}], sent, o);
    final r = resolveAsk(p, {'p1|p2': 'p2'});
    final ids = r.picks.map((x) => x.id).toSet();
    expect(ids.contains('p2') && !ids.contains('p1'), isTrue);
    expect(ids.contains('p4') && !ids.contains('p3'), isTrue);
    expect(r.ask, isEmpty);
  });

  test('recommendTheme: diary notes -> diary, many places a day -> b, else a', () {
    Chapter ch(int places) => Chapter('2026-06-12', null, [for (var i = 0; i < places; i++) c('x$i', place: 'p$i')]);
    expect(recommendTheme([ch(1)], hasDiaryNotes: true), 'diary');
    expect(recommendTheme([ch(4)], hasDiaryNotes: false), 'b');
    expect(recommendTheme([ch(1)], hasDiaryNotes: false), 'a');
  });
}
