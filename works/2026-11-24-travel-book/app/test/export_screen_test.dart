// 어떻게 남길까요? (mockup exp B): page picks, format, progress, at three phone sizes.
import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:tripbook/ui/export_screen.dart';
import 'package:tripbook/ui/sample.dart';

import 'harness.dart';

final _photos = [for (final (_, ps) in samplePicks) for (final p in ps) p.path];

void main() {
  setUpAll(loadFonts);

  test('representative pages: the cover and three spread over the rest', () {
    expect(fewPages(20), [0, 6, 13, 19]);
    expect(fewPages(3), [0, 1, 2]);
  });

  Widget screen(List<String?> thumbs, {Future<void> Function(List<int>, ExportKind, ValueChanged<int>)? onGo}) =>
      ExportScreen(
        title: '간사이',
        thumbs: thumbs,
        onBack: () {},
        onGo: onGo ?? (_, _, _) async {},
        onShare: (_) async {},
      );

  for (final (size, tag) in [(const Size(360, 740), '360x740'), (const Size(412, 915), '412x915'), (const Size(360, 640), '360x640')]) {
    testWidgets('look $tag', (t) async {
      final thumbs = <String?>[for (var i = 0; i < 20; i++) i < 5 ? _photos[i % _photos.length] : null];
      await pump(t, screen(thumbs), 'exp-b-$tag', size);
      expect(find.text('어떻게 남길까요?'), findsOneWidget);
      expect(find.text('20장 갤러리에 저장'), findsOneWidget);
      expect(find.text('공유하기'), findsOneWidget);
    });
  }

  testWidgets('pick, format, progress, done', (t) async {
    final thumbs = <String?>[for (var i = 0; i < 20; i++) _photos[i % _photos.length]];
    List<int>? gotPages;
    ExportKind? gotKind;
    final step = Completer<void>();
    await pump(
        t,
        screen(thumbs, onGo: (p, k, tick) async {
          gotPages = p;
          gotKind = k;
          tick(2);
          await step.future;
        }));
    await t.tap(find.text('대표 4쪽'));
    await t.pump();
    expect(find.text('4장 갤러리에 저장'), findsOneWidget);
    await t.tap(find.text('PDF'));
    await t.pump();
    await t.tap(find.text('4쪽 PDF 만들기'));
    await t.pump();
    expect(find.text('2 / 4 쪽 담는 중'), findsOneWidget);
    expect(gotPages, [0, 6, 13, 19]);
    expect(gotKind, ExportKind.pdf);
    step.complete();
    await t.pumpAndSettle();
    expect(find.text('✓ PDF를 만들었어요'), findsOneWidget);
    await t.tap(find.text('이미지')); // a new choice makes the button ready again
    await t.pump();
    expect(find.text('4장 갤러리에 저장'), findsOneWidget);
  });

  testWidgets('a failed save leaves the button ready; no page = no button', (t) async {
    final thumbs = <String?>[for (var i = 0; i < 6; i++) _photos[i % _photos.length]];
    await pump(t, screen(thumbs, onGo: (_, _, _) async => throw StateError('x')));
    await t.tap(find.text('6장 갤러리에 저장'));
    await t.pumpAndSettle();
    expect(find.text('6장 갤러리에 저장'), findsOneWidget);
    await t.tap(find.text('하나씩 고르기'));
    await t.pump();
    expect(find.text('남길 쪽을 골라 주세요'), findsOneWidget);
    await t.tap(find.text('1'));
    await t.pump();
    expect(find.text('1장 갤러리에 저장'), findsOneWidget);
  });
}
