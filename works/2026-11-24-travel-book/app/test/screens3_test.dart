// r12 flow screens: 어떤 책으로 만들까요? (A1) and 책을 만들고 있어요 (C1), at three phone sizes.
import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:tripbook/ui/making/choose_theme.dart';
import 'package:tripbook/ui/making/writing.dart';
import 'package:tripbook/ui/new_book/draft.dart';
import 'package:tripbook/ui/sample.dart';
import 'package:tripbook/ui/tokens.dart' show appTheme, kw;

import 'harness.dart';

final _photos = [for (final (_, ps) in samplePicks) for (final p in ps) p.path];
const _sizes = [(Size(360, 740), '360x740'), (Size(412, 915), '412x915'), (Size(360, 640), '360x640')];

void still(WidgetTester t) {
  t.platformDispatcher.accessibilityFeaturesTestValue = const FakeAccessibilityFeatures(disableAnimations: true);
  addTearDown(t.platformDispatcher.clearAccessibilityFeaturesTestValue);
}

void main() {
  setUpAll(loadFonts);
  void no() {}

  Widget choose(NewBookDraft d, {VoidCallback? onMake, bool notes = false}) => ChooseThemeScreen(
        draft: d,
        recommended: 'a',
        photos: _photos,
        title: '협재 해변',
        stats: (photos: 38, portrait: 6, days: 3, notes: notes),
        onBack: no,
        onMake: onMake ?? no,
      );

  testWidgets('choose theme: recommendation preselected, chips / arrows / swipe change it, 사진만 forces no text', (t) async {
    final d = NewBookDraft();
    var made = 0;
    await pump(t, choose(d, onMake: () => made++));
    expect(find.text('어떤 책으로 만들까요?'), findsOneWidget);
    expect(find.text(kw('고른 38장으로 미리 만들어 봤어요')), findsOneWidget);
    expect((d.opts.theme, d.opts.length, d.opts.textAmount), ('a', 'normal', 'caption')); // 38 photos, no notes
    expect(find.text('추천'), findsOneWidget); // the badge by the name
    expect(find.text('가로 사진이 많아 크게 펼쳐요'), findsOneWidget);
    expect(find.text('옆으로 넘겨 다른 테마 보기'), findsOneWidget);

    await t.tap(find.byIcon(Icons.chevron_right));
    await t.pumpAndSettle();
    expect(d.opts.theme, 'b');
    await t.ensureVisible(find.text('잡지'));
    await t.pumpAndSettle();
    // ignore: avoid_print
    await t.tap(find.text('잡지'));
    await t.pumpAndSettle();
    expect(d.opts.theme, 'mag');
    await t.drag(find.byType(PageView), const Offset(300, 0)); // swipe back one
    await t.pumpAndSettle();
    expect(d.opts.theme, 'diary');

    final photoChip = find.descendant(of: find.byType(Row), matching: find.text('사진만')).first;
    await t.ensureVisible(photoChip);
    await t.pumpAndSettle();
    await t.tap(photoChip);
    await t.pumpAndSettle();
    expect((d.opts.theme, d.opts.textAmount), ('photo', 'photos'));
    expect(find.text('사진만 테마는 글 없이 만들어요'), findsOneWidget);
    await t.ensureVisible(find.text('여백의 책 추천'));
    await t.pumpAndSettle();
    await t.tap(find.text('여백의 책 추천'));
    await t.pumpAndSettle();
    expect((d.opts.theme, d.opts.textAmount), ('a', 'caption')); // back to the recommendation, not stuck on 사진만

    await t.tap(find.text('길게'));
    await t.pump();
    expect(d.opts.length, 'long');
    await t.tap(find.text('이 모양으로 책 만들기'));
    expect(made, 1);
  });

  testWidgets('choose theme: a second visit keeps what was chosen', (t) async {
    final d = NewBookDraft()
      ..opts.theme = 'mag'
      ..opts.length = 'short';
    await pump(t, choose(d, notes: true));
    expect((d.opts.theme, d.opts.length), ('mag', 'short'));
    expect(find.text('세로 사진이 많아 화보처럼').evaluate().isEmpty, isTrue); // 6 of 38 portrait: not "tall"
    expect(find.text('여행 기사 한 호'), findsOneWidget);
  });

  testWidgets('choose theme: the nudge slides the fan and comes back, the hint fades', (t) async {
    final d = NewBookDraft();
    await pump(t, choose(d));
    final pv = t.widget<PageView>(find.byType(PageView)).controller!;
    await t.pump(const Duration(milliseconds: 700));
    await t.pump(const Duration(milliseconds: 300));
    expect(pv.offset, greaterThan(10));
    await shot(t, 'r12-a1-nudge', settle: false);
    await t.pump(const Duration(seconds: 3));
    await t.pumpAndSettle();
    expect(pv.offset, 0);
    expect(d.opts.theme, 'a');
    final hint = t.widget<AnimatedOpacity>(find.ancestor(of: find.text('옆으로 넘겨 다른 테마 보기'), matching: find.byType(AnimatedOpacity)));
    expect(hint.opacity, 0);
  });

  for (final (sz, n) in _sizes) {
    testWidgets('choose theme at $n', (t) async {
      still(t);
      await pump(t, choose(NewBookDraft()), 'r12-a1-$n', sz);
      expect(find.text('이 모양으로 책 만들기'), findsOneWidget);
    });
  }

  Widget writing({VoidCallback? onLeave, bool words = true}) => WritingScreen(
        photos: _photos,
        theme: 'a',
        title: '협재 해변',
        line: '여백의 책 · 보통 24쪽 · 2~3분',
        picked: 38,
        words: words,
        onLeave: onLeave ?? no,
      );

  testWidgets('writing: steps follow the clock, the last one never claims done; leaving calls back', (t) async {
    still(t);
    var left = 0;
    await pump(t, writing(onLeave: () => left++));
    expect(find.text('책을 만들고 있어요'), findsOneWidget);
    expect(find.text('사진 38장을 날짜와 장면으로 나누는 중'), findsOneWidget);
    expect(find.byType(CircularProgressIndicator), findsOneWidget);
    expect(find.byIcon(Icons.check_rounded), findsNothing);
    await t.pump(const Duration(seconds: 30));
    expect(find.byIcon(Icons.check_rounded), findsOneWidget); // step 1 done, step 2 now
    await t.pump(const Duration(minutes: 10));
    expect(find.byIcon(Icons.check_rounded), findsNWidgets(3));
    expect(find.byType(CircularProgressIndicator), findsOneWidget); // still on the check step
    await t.tap(find.text('나가 있기 · 다 되면 알려 드릴게요'));
    expect(left, 1);
  });

  testWidgets('writing: 사진만 has no caption step', (t) async {
    still(t);
    await pump(t, writing(words: false));
    expect(find.text('캡션과 짧은 글 쓰는 중'), findsNothing);
    expect(find.text('사진 크기와 순서 맞추는 중'), findsOneWidget);
  });

  testWidgets('writing: pages drop in, then keep moving', (t) async {
    t.view.physicalSize = const Size(360, 740) * 2;
    t.view.devicePixelRatio = 2;
    addTearDown(t.view.reset);
    await t.pumpWidget(RepaintBoundary(key: shotKey, child: MaterialApp(debugShowCheckedModeBanner: false, theme: appTheme(), home: writing())));
    (double?, double?) at(int i) {
      final p = t.widget<AnimatedPositioned>(find.byType(AnimatedPositioned).at(i));
      return (p.left, p.top);
    }

    for (var i = 0; i < 4; i++) {
      await t.pump(const Duration(milliseconds: 2500));
    }
    await t.pump(const Duration(milliseconds: 350)); // the 5th page mid-drop, shimmer on it
    await shot(t, 'r12-c1-drop', settle: false);
    for (var i = 0; i < 3; i++) {
      await t.pump(const Duration(milliseconds: 2500));
    }
    await t.pump(const Duration(seconds: 2));
    final before = [for (var i = 0; i < 6; i++) at(i)];
    await t.pump(const Duration(milliseconds: 2500));
    await t.pump(const Duration(seconds: 2));
    final after = [for (var i = 0; i < 6; i++) at(i)];
    expect(after.toSet(), before.toSet()); // same six places
    expect(after, isNot(before)); // but two pages swapped
  });

  for (final (sz, n) in _sizes) {
    testWidgets('writing at $n', (t) async {
      still(t);
      await pump(t, writing(), null, sz);
      await t.pump(const Duration(seconds: 40));
      await shot(t, 'r12-c1-$n');
      expect(find.text('나가 있기 · 다 되면 알려 드릴게요'), findsOneWidget);
    });
  }
}
