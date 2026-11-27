// The making host after the r12 flow change: review -> 어떤 책으로 만들까요? (always) -> making; [나가 있기] leaves the
// write running, reopening the book waits for the same write (no second /layout call), the shelf hears when it ends.
import 'dart:async';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:tripbook/book/phone_source.dart';
import 'package:tripbook/book/store.dart';
import 'package:tripbook/core/book.dart';
import 'package:tripbook/core/models.dart';
import 'package:tripbook/core/prefs.dart';
import 'package:tripbook/flow/app.dart';
import 'package:tripbook/flow/making_flow.dart';
import 'package:tripbook/pipeline.dart';
import 'package:tripbook/ui/tokens.dart';

import 'harness.dart';
import 'pipeline_test.dart' show FakeAi, FakeSource;

class _Store extends BookStore {
  final saved = <String>[];
  _Store() : super(Directory('unused'));
  @override
  Future<void> save(Book b) async => saved.add('${b.theme}/${b.opts.length}');
}

class _Src implements PhoneSource {
  @override
  dynamic noSuchMethod(Invocation i) => throw UnimplementedError('${i.memberName}'); // the making host never asks
}

class _Pipe extends Pipeline {
  final done = Completer<AiState>();
  var writes = 0;
  _Pipe(BookStore store) : super(src: FakeSource(), ai: FakeAi(), store: store, layoutsJson: (_) async => '{}');
  @override
  Future<AiState> write(Book b, {List<NoteInput> notes = const [], void Function(WriteStage, int, int)? onStage}) {
    writes++;
    return done.future;
  }
}

Book _book() {
  final cs = [
    for (var i = 0; i < 8; i++)
      Candidate(Shot(id: 'p$i', utc: DateTime.utc(2026, 6, 12 + i ~/ 4, 1, i), relPath: 'DCIM/', w: 4, h: 3),
          sharp: 500, luma: 120, dhash: i, place: '협재', local: DateTime(2026, 6, 12 + i ~/ 4, 10, i)),
  ];
  return Book(
      id: 'b1',
      from: DateTime(2026, 6, 12),
      to: DateTime(2026, 6, 13),
      opts: BookOptions(),
      theme: 'a',
      pool: cs,
      picks: [for (final c in cs) Pick(c.id, '', 90)]);
}

void main() {
  setUpAll(loadFonts);
  setUp(() {
    final jpg = File('assets/sample/d1.jpg').readAsBytesSync();
    galleryThumbs = (_, _) async => jpg;
  });
  tearDown(() => galleryThumbs = null);

  testWidgets('leaving the making screen keeps the write; reopening waits for the same one', (t) async {
    t.platformDispatcher.accessibilityFeaturesTestValue = const FakeAccessibilityFeatures(disableAnimations: true);
    addTearDown(t.platformDispatcher.clearAccessibilityFeaturesTestValue);
    final store = _Store(), pipe = _Pipe(store);
    final app = App(prefs: Prefs(File('unused')), store: store, pipe: pipe, src: _Src());
    var shelf = 0;
    app.shelfChanged.addListener(() => shelf++);
    final b = _book();
    final nav = GlobalKey<NavigatorState>();
    await t.pumpWidget(MaterialApp(navigatorKey: nav, theme: appTheme(), home: const Scaffold(body: Text('책장'))));
    unawaited(nav.currentState!.push(MaterialPageRoute<void>(builder: (_) => MakingFlow(app: app, book: b))));
    await t.pumpAndSettle();

    await t.tap(find.text('이대로 책 만들기'));
    await t.pumpAndSettle();
    expect(find.text('어떤 책으로 만들까요?'), findsOneWidget); // always, before the making
    expect(pipe.writes, 0);
    await t.tap(find.text('잡지'));
    await t.pumpAndSettle();
    await t.tap(find.text('길게')); // 8 photos: 짧게 is the recommendation
    await t.tap(find.text('이 모양으로 책 만들기'));
    await t.pumpAndSettle();
    expect(store.saved, ['mag/long']); // the choice is on the book before /layout
    expect(find.text('책을 만들고 있어요'), findsOneWidget);
    expect(pipe.writes, 1);

    await t.tap(find.text('나가 있기 · 다 되면 알려 드릴게요'));
    await t.pumpAndSettle();
    expect(find.text('책장'), findsOneWidget);
    expect(app.writes.keys, ['b1']); // still running

    unawaited(nav.currentState!.push(MaterialPageRoute<void>(builder: (_) => MakingFlow(app: app, book: b))));
    await t.pumpAndSettle();
    expect(find.text('책을 만들고 있어요'), findsOneWidget); // straight back to the making, not the review
    expect(pipe.writes, 1);
    await t.tap(find.text('나가 있기 · 다 되면 알려 드릴게요'));
    await t.pumpAndSettle();

    pipe.done.complete(AiState.ok);
    await t.pumpAndSettle();
    expect(app.writes, isEmpty);
    expect(shelf, 1);
    expect(pipe.writes, 1);
  });
}
