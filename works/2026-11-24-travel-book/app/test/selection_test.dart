import 'package:flutter_test/flutter_test.dart';
import 'package:tripbook/core/models.dart';
import 'package:tripbook/core/selection.dart';

final t0 = DateTime(2026, 6, 12, 10);
Candidate c(String id, int sec, {int dhash = 0, double sharp = 50, double? lat, double? lng}) => Candidate(
    Shot(id: id, utc: t0.add(Duration(seconds: sec)).toUtc(), lat: lat, lng: lng),
    sharp: sharp, luma: 120, dhash: dhash, local: t0.add(Duration(seconds: sec)));

void main() {
  test('moments: a 20-minute gap or a 300 m move starts a new one', () {
    final xs = [
      c('a', 0, lat: 33.5, lng: 126.5), c('b', 600, lat: 33.5, lng: 126.5), // 10 min later, same spot
      c('c', 900, lat: 33.51, lng: 126.5), // ~1.1 km away
      c('d', 900 + 21 * 60), // 21 min later, no position
    ];
    final ms = momentsBy(xs, (x) => x.local, posOf);
    expect([for (final m in ms) [for (final x in m) x.id]], [['a', 'b'], ['c'], ['d']]);
  });

  test('collapse: a same-frame burst becomes its sharpest; a run of poses stays as a series', () {
    final xs = [
      c('b1', 0, dhash: 0, sharp: 10), c('b2', 1, dhash: 1, sharp: 30), // burst: one frame (b2)
      c('p1', 60, dhash: 0x00FF), c('p2', 66, dhash: 0x0FFF), c('p3', 72, dhash: 0x00F0), c('p4', 80, dhash: 0x0F0F), // poses
      c('x', 400, dhash: 0x7FFFFFFFFFFFFFFF),
    ];
    final r = collapse(xs);
    expect([for (final f in r.frames) f.id], ['b2', 'p1', 'p2', 'p3', 'p4', 'x']);
    expect(r.series, {'p1': 1, 'p2': 1, 'p3': 1, 'p4': 1});
  });

  test('slots: busy moments get more (by sqrt), every moment at least one, never more than it has', () {
    final s = slots([400, 100, 4, 1], [400, 100, 4, 1], 60);
    expect(s.reduce((a, b) => a + b), 60);
    expect(s[0], greaterThan(s[1]));
    expect(s[0], lessThan(4 * s[1])); // sqrt: 4x the shots is ~2x the room, not 4x
    expect(s[3], 1);
  });

  test('spread: the sharpest of each stretch, so a soft moment still gives its best', () {
    final xs = [for (var i = 0; i < 10; i++) c('s$i', i * 60, sharp: i == 2 ? 90 : (i == 7 ? 80 : 10))];
    expect([for (final x in spread(xs, 2)) x.id], ['s2', 's7']);
  });

  test('finalists: must, every moment, each kind its minimum, a pose series kept together, closed eyes out', () {
    final xs = <Candidate>[], tags = <String, Tag>{}, momentOf = <String, int>{}, series = <String, int>{};
    void add(String id, int m, String kind, int score, {int? s, bool eyes = false}) {
      final x = c(id, xs.length * 30);
      xs.add(x);
      tags[id] = Tag(kind, kind == '같이' ? 2 : (kind == '한명' ? 1 : 0), score, eyes);
      momentOf[id] = m;
      if (s != null) series[id] = s;
    }

    for (var i = 0; i < 40; i++) {
      add('v$i', 0, '풍경', 4); // a flood of good views in the busy moment
    }
    add('food1', 1, '음식', 3);
    add('food2', 1, '음식', 3);
    add('pair', 2, '같이', 3);
    add('closed', 2, '같이', 5, eyes: true);
    for (var i = 0; i < 4; i++) {
      add('pose$i', 3, '한명', 3, s: 7);
    }
    add('quiet', 4, '이동', 2);
    add('must', 4, '기타', 1);
    final out = finalists(xs, tags, momentOf, {0: 300, 1: 20, 2: 15, 3: 12, 4: 3}, series, 20, must: {'must'});
    final ids = {for (final x in out) x.id};
    expect(out.length, 20);
    expect(ids, containsAll(['must', 'food1', 'food2', 'pair', 'quiet', 'pose0', 'pose1', 'pose2', 'pose3']));
    expect(ids.contains('closed'), isFalse);
    expect(ids.where((i) => i.startsWith('v')).length, lessThanOrEqualTo(11)); // views capped near 35% (+ room left)
  });

  test('collapse never merges a must photo into its sharper twin', () {
    final r = collapse([c('m', 0, dhash: 0, sharp: 10), c('t', 1, dhash: 0, sharp: 90)], must: {'m'});
    expect([for (final f in r.frames) f.id], ['m', 't']);
  });

  test('slots: more moments than room - one each for the busiest, never above the cap', () {
    final s = slots([5, 50, 1, 9], [5, 50, 1, 9], 2);
    expect(s, [0, 1, 0, 1]);
  });

  test('shortlist: series and must on top of the slots are trimmed back to the cap, must kept', () {
    final m = [for (var i = 0; i < 12; i++) c('s$i', i * 5, dhash: i)];
    final series = {for (final x in m) x.id: 1};
    final out = shortlist([m], [12], series, 3, must: {'s11'});
    expect(out.length, 3);
    expect(out.map((x) => x.id), contains('s11'));
  });
}
