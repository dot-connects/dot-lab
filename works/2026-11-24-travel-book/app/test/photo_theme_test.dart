import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:tripbook/core/models.dart';
import 'package:tripbook/core/paginate.dart';

import 'paginate_test.dart' show trip;

final photoDefs = (jsonDecode(File('test/fixtures/r8-photo-layouts.json').readAsStringSync()) as List)
    .map((j) => LayoutDef.fromJson(j as Map<String, dynamic>))
    .toList();

void main() {
  test('사진만: every pick lands on a page or in spare, and only the cover carries text', () {
    final (chs, plan) = trip(days: 3, perDay: 14);
    final r = paginate(plan, chs, BookOptions()..extras = {'video'}, photoDefs, cover: '', title: '제주');
    expect(r.pages.first.layout, 'photo/c1');
    final placed = {for (final pg in r.pages.skip(1)) ...pg.ids};
    final picked = {for (final p in plan.picks) p.id};
    expect(placed.union(r.spare.toSet()), picked);
    expect(r.spare.length, lessThanOrEqualTo(2)); // the theme has a layout for every orientation
    final textKeys = {for (final d in photoDefs.where((d) => d.kind != 'cover')) ...d.texts};
    expect(textKeys, isEmpty);
  });
}
