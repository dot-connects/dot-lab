// 다듬기 screen (mockup edit-ui): the five cards at three phone sizes, and the moves between them.
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:tripbook/core/book.dart';
import 'package:tripbook/core/edit.dart';
import 'package:tripbook/core/models.dart';
import 'package:tripbook/core/plan_check.dart';
import 'package:tripbook/ui/edit_screen.dart';
import 'package:tripbook/ui/tokens.dart';

import 'harness.dart';

final _defs = [
  for (final j in jsonDecode(File('assets/book/themes/b/layouts.json').readAsStringSync()) as List) LayoutDef.fromJson(j as Map<String, dynamic>),
];

Book _book() {
  final cs = [
    for (var i = 0; i < 8; i++)
      Candidate(Shot(id: 'p$i', utc: DateTime.utc(2026, 6, 12, 1, i), w: 4, h: 3),
          sharp: 500, luma: 120, dhash: i, place: '협재', local: DateTime(2026, 6, 12, 10, i)),
  ];
  return Book(id: 'b', from: DateTime(2026, 6, 12), to: DateTime(2026, 6, 13), opts: BookOptions(), theme: 'b', pool: cs, title: '제주',
      picks: [for (final c in cs.take(4)) Pick(c.id, '', 90)],
      texts: {'essay': const WriteResult(['제주', '제주 사흘', '바람의 섬'], {}, {}, '')},
      pages: [
        PageSpec('b/c-window', ['p0'], text: {'title': '제주', 'period': '6.12 – 6.13'}),
        PageSpec('b/e-wide', ['p1'], text: {'cap': '협재'}),
        PageSpec('b/e-two', ['p2', 'p3'], text: {'cap': '바다'}),
      ]);
}

void main() {
  setUpAll(loadFonts);

  Widget screen(BookEdit e, {List<EditPageArgs>? seen, VoidCallback? onClose, Future<void> Function()? onSave}) => EditScreen(
        edit: e,
        page: (a, _) {
          seen?.add(a);
          return Center(child: Container(width: 180, height: 240, color: C.paper, child: Center(child: Text('${a.page}쪽 ${a.mode.name}'))));
        },
        alternatives: (id) => [for (final c in e.b.pool) if (!e.b.pages.any((p) => p.ids.contains(c.id))) c],
        copyMedia: (_) async {},
        onSave: onSave ?? () async {},
        onClose: onClose ?? () {},
      );

  for (final (size, tag) in [(const Size(360, 740), '360x740'), (const Size(412, 915), '412x915'), (const Size(360, 640), '360x640')]) {
    testWidgets('look $tag', (t) async {
      final e = BookEdit(_book(), _defs);
      await pump(t, screen(e), 'edit-cover-$tag', size);
      expect(find.text('표지'), findsWidgets);
      expect(find.text('1 / 5'), findsOneWidget);
      for (final (i, name) in ['type', 'words', 'photos', 'check'].indexed) {
        await t.tap(find.text('다음'));
        await t.pumpAndSettle();
        await shot(t, 'edit-$name-$tag');
        expect(find.text('${i + 2} / 5'), findsOneWidget);
      }
      expect(find.text('저장하기'), findsOneWidget);
      expect(t.takeException(), isNull);
    });
  }

  testWidgets('choices change the book; 원본 puts the step back; the summary jumps and comes back', (t) async {
    final e = BookEdit(_book(), _defs);
    await pump(t, screen(e));
    await t.tap(find.bySemanticsLabel('표지 색 남색'));
    await t.pump();
    expect(e.b.coverColor, '#1F3A6E');
    expect(find.text('글자는 흰색으로 맞췄어요.'), findsOneWidget);
    await t.tap(find.text('원본으로'));
    await t.pump();
    expect(e.b.coverColor, isNull);
    expect(find.text('원본으로'), findsNothing); // nothing to put back: the link goes

    await t.tap(find.text('다음'));
    await t.pumpAndSettle();
    await t.tap(find.text('개구'));
    await t.tap(find.text('크게'));
    await t.pump();
    expect(e.b.font, 'Gaegu');
    expect(e.b.textSize, 'l');

    await t.tap(find.text('다음'));
    await t.pumpAndSettle();
    await t.tap(find.text('바람의 섬'));
    await t.pump();
    expect(e.b.title, '바람의 섬');
    expect(e.b.pages[0].text['title'], '바람의 섬');

    await t.tap(find.text('다음'));
    await t.pumpAndSettle();
    await t.tap(find.text('다음'));
    await t.pumpAndSettle();
    expect(find.text('「바람의 섬」'), findsOneWidget);
    expect(find.text('바뀜'), findsNWidgets(2));
    await t.tap(find.text('글자'));
    await t.pumpAndSettle();
    expect(find.text('요약으로'), findsOneWidget);
    await t.tap(find.text('요약으로'));
    await t.pumpAndSettle();
    expect(find.text('이렇게 저장할까요?'), findsOneWidget);
  });

  testWidgets('photo step: pages without photos are skipped, swap shows the same day, remove can be undone', (t) async {
    final e = BookEdit(_book(), _defs);
    final seen = <EditPageArgs>[];
    await pump(t, screen(e, seen: seen), null, const Size(412, 915));
    for (var i = 0; i < 3; i++) {
      await t.tap(find.text('다음'));
      await t.pumpAndSettle();
    }
    expect(seen.last.mode, EditMode.photo);
    await t.tap(find.byIcon(Icons.chevron_right));
    await t.pumpAndSettle();
    expect(seen.last.page, 1);
    await t.tap(find.text('바꾸기'));
    await t.pumpAndSettle();
    expect(find.text('10:04'), findsOneWidget); // p4: the first photo on no page
    await t.tap(find.text('10:04'));
    await t.pumpAndSettle();
    expect(e.b.pages[1].ids, ['p4']);
    expect(e.swapped, 1);
    await t.tap(find.text('되돌리기'));
    await t.pumpAndSettle();
    expect(e.b.pages[1].ids, ['p1']);
  });

  testWidgets('closing with changes asks first', (t) async {
    final e = BookEdit(_book(), _defs);
    var closed = 0;
    await pump(t, screen(e, onClose: () => closed++));
    await t.tap(find.byIcon(Icons.close));
    await t.pumpAndSettle();
    expect(closed, 1); // nothing changed: just closes
    await t.tap(find.bySemanticsLabel('표지 색 먹'));
    await t.pump();
    await t.tap(find.byIcon(Icons.close));
    await t.pumpAndSettle();
    expect(find.text('다듬은 걸 버리고 나갈까요?'), findsOneWidget);
    await t.tap(find.text('계속 다듬기'));
    await t.pumpAndSettle();
    expect(closed, 1);
  });
}
