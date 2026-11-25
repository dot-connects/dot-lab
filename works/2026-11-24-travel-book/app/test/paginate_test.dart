import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:tripbook/core/models.dart';
import 'package:tripbook/core/paginate.dart';

final defs = (jsonDecode(File('assets/book/themes/a/layouts.json').readAsStringSync()) as List)
    .map((j) => LayoutDef.fromJson(j as Map<String, dynamic>))
    .toList();
final slotsOf = {for (final d in defs) d.id: d.slots};

Candidate c(String id, int day, int min, {bool portrait = false, bool video = false, String? place}) {
  final t = DateTime(2026, 6, day, 9, 0).add(Duration(minutes: min));
  return Candidate(
      Shot(id: id, utc: t, w: portrait ? 3 : 4, h: portrait ? 4 : 3, isVideo: video, durationMs: video ? 9000 : 0, lat: 33.4 + min / 1000, lng: 126.5),
      sharp: 500, luma: 120, dhash: 0, place: place ?? 'p$day', local: t);
}

(List<Chapter>, BookPlan) trip({int days = 3, int perDay = 12, bool withVideo = true}) {
  final chs = <Chapter>[];
  final picks = <Pick>[];
  for (var d = 0; d < days; d++) {
    final xs = [for (var i = 0; i < perDay; i++) c('d$d-$i', 12 + d, i * 20, portrait: i % 3 == 1)];
    if (withVideo && d == 1) xs.add(c('v$d', 12 + d, 500, video: true));
    chs.add(Chapter('2026-06-${12 + d}', 'p${12 + d}', xs));
    picks.addAll([for (final x in xs) Pick(x.id, '한 줄', 50)]);
  }
  return (chs, BookPlan(titles: ['제주', '', ''], theme: 'a', picks: picks, clips: withVideo ? [const Clip('v1', 0, 3000)] : const []));
}

Map<String, Candidate> index(List<Chapter> chs) => {for (final ch in chs) for (final x in ch.items) x.id: x};

void main() {
  test('portrait photos never land in L slots, landscape never in P slots', () {
    final (chs, plan) = trip();
    final byId = index(chs);
    final r = paginate(plan, chs, BookOptions(), defs, cover: 'a/c1', title: '제주');
    for (final pg in r.pages) {
      final slots = slotsOf[pg.layout]!;
      for (var i = 0; i < pg.ids.length && i < slots.length; i++) {
        final s = byId[pg.ids[i]]!.shot;
        if (slots[i] == 'L') expect(s.portrait, isFalse, reason: '${pg.layout} slot $i');
        if (slots[i] == 'P') expect(s.portrait, isTrue, reason: '${pg.layout} slot $i');
      }
    }
  });

  test('no layout twice in a row among photo pages', () {
    final (chs, plan) = trip(days: 2, perDay: 20);
    final r = paginate(plan, chs, BookOptions(), defs, cover: 'a/c1', title: '제주');
    for (var i = 1; i < r.pages.length; i++) {
      if (slotsOf[r.pages[i].layout]!.length > 0 && r.pages[i].layout != 'a/op') {
        expect(r.pages[i].layout == r.pages[i - 1].layout && r.pages[i].layout.startsWith('a/') && defs.firstWhere((d) => d.id == r.pages[i].layout).kind == 'photo', isFalse,
            reason: 'page $i ${r.pages[i].layout}');
      }
    }
  });

  test('every pick is on a page or in spare: nothing is lost', () {
    final (chs, plan) = trip(days: 3, perDay: 17);
    final r = paginate(plan, chs, BookOptions(), defs, cover: 'a/c1', title: '제주');
    final placed = {for (final pg in r.pages.skip(1)) ...pg.ids}..removeWhere((id) => r.pages.last.ids.contains(id) && r.pages.last.layout == 'a/en' && false);
    final all = plan.picks.map((p) => p.id).toSet();
    expect(all.difference({...placed, ...r.spare, ...r.pages.first.ids}), isEmpty);
  });

  test('turning numbers off removes the numbers page; a kind with no layout is skipped', () {
    final (chs, plan) = trip();
    final on = paginate(plan, chs, BookOptions(), defs, cover: 'a/c1', title: '제주');
    final off = paginate(plan, chs, BookOptions()..extras = {'video'}, defs, cover: 'a/c1', title: '제주');
    expect(on.pages.last.layout, 'a/en');
    expect(off.pages.any((p) => p.layout == 'a/en'), isFalse);
    // theme a has no essay or map layout: asking for them adds nothing and does not fail
    final extra = paginate(plan, chs, BookOptions()..extras = {'essay', 'map'}, defs, cover: 'a/c1', title: '제주');
    expect(extra.pages.where((p) => p.layout.contains('essay') || p.layout.contains('map')), isEmpty);
  });

  test('a chapter opens with its opener then a one-photo page', () {
    final (chs, plan) = trip(withVideo: false);
    final r = paginate(plan, chs, BookOptions(), defs, cover: 'a/c1', title: '제주');
    for (var i = 0; i < r.pages.length - 1; i++) {
      if (r.pages[i].layout == 'a/op') expect(slotsOf[r.pages[i + 1].layout]!.length, 1, reason: 'after page $i');
    }
    expect(r.pages.where((p) => p.layout == 'a/op').length, 3);
    expect(r.pages[1].text['day_name'], '첫째 날');
    expect(r.pages[1].text['date'], '06.12 금');
  });

  test('normal length lands near the recommendation; short <= normal <= long', () {
    final (chs, plan) = trip(days: 3, perDay: 14);
    int n(String len) => paginate(plan, chs, BookOptions()..length = len, defs, cover: 'a/c1', title: '제주').pages.length;
    final target = recommendPages(plan.picks.length, 3);
    expect(n('normal'), inInclusiveRange(target - 3, target + 3));
    // theme a has one landscape one-photo layout, so a long book cannot always reach x1.4: it only has to grow
    expect(n('short') <= n('normal') && n('normal') <= n('long'), isTrue);
  });

  test('cover texts: period, nights, running head', () {
    final (chs, plan) = trip();
    final r = paginate(plan, chs, BookOptions(), defs, cover: 'a/c1', title: '제주');
    expect(r.pages.first.layout, 'a/c1');
    expect(r.pages.first.text['period'], '2026.06.12 — 06.14');
    expect(r.pages.first.text['nights'], '2박 3일');
    expect(r.pages[1].text['running'], '제주 2박 3일');
    expect(r.pages[1].text['folio'], '1');
  });

  test('travelKm sums straight-line hops', () {
    final a = c('a', 12, 0), b = c('b', 12, 1000); // 1 degree of latitude apart
    expect(travelKm([a, b]), closeTo(111.2, 0.5));
  });
}
