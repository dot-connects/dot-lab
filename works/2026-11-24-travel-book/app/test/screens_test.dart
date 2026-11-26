// Pumps every D1-D3 screen at a 360x740 phone and checks the mockup copy is there with no overflow.
// SHOTS=<dir> flutter test test/screens_test.dart also writes a PNG per screen (real fonts, real images).

import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:tripbook/core/library.dart';
import 'package:tripbook/ui/new_book/albums.dart';
import 'package:tripbook/ui/new_book/book_shape.dart';
import 'package:tripbook/ui/new_book/dates.dart';
import 'package:tripbook/ui/new_book/draft.dart';
import 'package:tripbook/ui/new_book/extras.dart';
import 'package:tripbook/ui/new_book/must_pick.dart';
import 'package:tripbook/ui/new_book/notes.dart';
import 'package:tripbook/ui/new_book/timeline_help.dart';
import 'package:tripbook/ui/onboarding.dart';
import 'package:tripbook/ui/sample.dart';
import 'package:tripbook/ui/shelf.dart';
import 'package:tripbook/ui/tokens.dart';

import 'harness.dart';

void main() {
  setUpAll(loadFonts);
  void no() {}

  testWidgets('onboarding: 3 slides, skip jumps to the last, last button calls back', (t) async {
    var allowed = false;
    await pump(t, Onboarding(onAllow: () => allowed = true), 'd1-1');
    expect(find.text('날짜만 고르면\n그 여행이 한 권의 책이 돼요'), findsOneWidget);
    await t.tap(find.text('다음'));
    await t.pumpAndSettle();
    expect(find.text('412'), findsOneWidget);
    await shot(t, 'd1-2');
    await t.tap(find.text('건너뛰기'));
    await t.pumpAndSettle();
    expect(find.text('사진은 폰을 떠나지 않아요'), findsOneWidget);
    expect(find.text('건너뛰기'), findsNothing);
    await shot(t, 'd1-3');
    await t.tap(find.text('사진 접근 허용하고 시작'));
    expect(allowed, isTrue);
  });

  Widget shelf(List<ShelfBook> books, ShelfView v, {ValueChanged<ShelfBook>? onDelete}) => Shelf(
        books: books,
        view: v,
        onViewChanged: (_) {},
        onOpen: (_) {},
        onExport: (_) {},
        onRename: (_) {},
        onDelete: onDelete ?? (_) {},
        onNew: no,
        onSettings: no,
        now: DateTime(2026, 11, 25),
      );

  testWidgets('shelf display', (t) async {
    await pump(t, shelf(sampleBooks, ShelfView.display), 'd2-1-display');
    expect(find.text('내 책장'), findsOneWidget);
    expect(find.text('5권 · 사진 214장'), findsOneWidget);
    expect(find.text('06.12 · 24쪽'), findsOneWidget);
    expect(find.text('25.04.02 · 32쪽'), findsOneWidget);
    expect(find.text('새 책'), findsOneWidget);
  });

  testWidgets('shelf timeline', (t) async {
    await pump(t, shelf(sampleBooks, ShelfView.timeline), 'd2-2-timeline');
    expect(find.text('2026'), findsOneWidget);
    expect(find.text('2권 · 14곳 · 6일'), findsOneWidget);
    expect(find.text('06.12 — 06.14 · 2박 3일'), findsOneWidget);
  });

  testWidgets('shelf empty', (t) async {
    await pump(t, shelf(const [], ShelfView.display), 'd2-3-empty');
    expect(find.text('아직 비어 있어요'), findsOneWidget);
    expect(find.text('첫 책이\n여기 놓여요'), findsOneWidget);
    expect(find.text('첫 책 만들기'), findsOneWidget);
  });

  testWidgets('shelf long-press menu with inline delete confirm', (t) async {
    ShelfBook? deleted;
    await pump(t, shelf(sampleBooks, ShelfView.display, onDelete: (b) => deleted = b));
    await t.longPress(find.byType(Image).first); // the Jeju cover
    await t.pumpAndSettle();
    expect(find.text('펼치기'), findsOneWidget);
    await t.tap(find.text('지우기'));
    await t.pumpAndSettle();
    expect(find.text('이 책을 지울까요?'), findsOneWidget);
    await shot(t, 'd2-4-menu');
    await t.tap(find.text('지우기').last);
    await t.pumpAndSettle();
    expect(deleted, isNotNull);
  });

  testWidgets('dates', (t) async {
    await pump(
        t,
        DatesScreen(
            month: sampleMonth, photos: samplePhotosPerDay, videos: sampleVideosPerDay, start: DateTime(2026, 6, 12), end: DateTime(2026, 6, 14), onClose: no, onNext: (_, _) {}),
        'd3-1-dates');
    expect(find.text('언제 다녀왔어요?'), findsOneWidget);
    expect(find.text('6월 12일 – 14일'), findsOneWidget);
    expect(find.text('사진 412 · 영상 9'), findsOneWidget);
    expect(find.text('178'), findsOneWidget);
    // a new range: tap 2 then 6
    await t.tap(find.text('2'));
    await t.tap(find.text('6').first);
    await t.pump();
    expect(find.text('6월 2일 – 6일'), findsOneWidget);
  });

  testWidgets('albums: all on, then two off -> 186', (t) async {
    await pump(t, AlbumsScreen(albums: sampleAlbums, onBack: no, onNext: (_, _) {}), 'd3-2-albums');
    expect(find.text('412장으로 다음'), findsOneWidget);
    expect(find.text('이 기간 사진이 있는 앨범 4개'), findsOneWidget);
    expect(find.text('이 기간 164 · 전체 9,842'), findsOneWidget);
    await t.tap(find.text('KakaoTalk'));
    await t.tap(find.text('스크린샷'));
    await t.tap(find.text('다운로드')); // zero album: ignored
    await t.pump();
    expect(find.text('186장으로 다음'), findsOneWidget);
    expect(find.text('앨범 4개 중 2개 고름'), findsOneWidget);
  });

  testWidgets('extras', (t) async {
    final d = NewBookDraft()..mustInclude = {'12-5', '13-5', '13-8'};
    await pump(
        t,
        ExtrasScreen(
            draft: d,
            photos: 186,
            mustThumbs: [for (final id in d.mustInclude) samplePath(id)],
            freeLeft: 2,
            onBack: no,
            onMust: no,
            onNotes: no,
            onTimeline: no,
            onMake: no,
            onShape: no),
        'd3-3-extras');
    expect(find.text(kw('3장 골랐어요')), findsOneWidget);
    expect(find.text('책 만들기'), findsOneWidget);
    expect(find.text('테마·길이 먼저 고르기'), findsOneWidget);
    expect(find.textContaining('약 24쪽'), findsOneWidget);
    // 누구랑: a chip, own words, skip
    await t.tap(find.text('연인·부부'));
    await t.pump();
    expect(d.opts.who, '연인·부부');
    await t.tap(find.text('직접 쓰기'));
    await t.pump();
    await t.enterText(find.byType(TextField).first, '회사 동기 셋');
    expect(d.opts.who, '회사 동기 셋');
    await t.enterText(find.byType(TextField).last, '먹으러 간 여행');
    expect(d.opts.about, '먹으러 간 여행');
    await shot(t, 'd3-3-extras-who');
    await t.tap(find.text('건너뛰기'));
    await t.pump();
    expect((d.opts.who, d.opts.about), (null, ''));
    expect(find.text('누구랑 · 어떤 여행: 건너뛰었어요'), findsOneWidget);
  });

  testWidgets('must pick: cap at 10', (t) async {
    final d = NewBookDraft();
    for (var i = 0; i < 10; i++) {
      d.toggleMust('13-$i');
    }
    await pump(t, MustPickScreen(days: sampleMustDays, draft: d, onDone: no));
    await t.tap(find.byType(Image).first); // an 11th photo (day 12)
    await t.pump();
    expect(d.mustInclude.length, 10);
    expect(find.text('꼭 넣을 사진은 10장까지'), findsOneWidget);
  });

  testWidgets('must pick: look', (t) async {
    final d = NewBookDraft()..mustInclude = {'12-5', '13-5', '13-8'};
    await pump(t, MustPickScreen(days: sampleMustDays, draft: d, onDone: no), 'd3-4-must');
    expect(find.text('3장 고름'), findsOneWidget);
    expect(find.text('58장 · 1장 고름'), findsOneWidget);
    expect(find.text('3장 꼭 넣기'), findsOneWidget);
  });

  Widget shape(NewBookDraft d) => BookShapeScreen(
      draft: d,
      recommended: 'a',
      reason: '풍경 사진이 많아서',
      photos: 186,
      days: 3,
      videos: 9,
      placesHint: '협재 · 우도 · 성산 …',
      onBack: no,
      onMake: no);

  testWidgets('book shape + more', (t) async {
    final d = NewBookDraft();
    await pump(t, shape(d), 'd3-5-shape');
    expect(find.text('사진 186장 · 사흘'), findsOneWidget);
    expect(find.text('8가지'), findsOneWidget);
    await t.tap(find.text('더 보기'));
    await t.pumpAndSettle();
    await t.drag(find.byType(SingleChildScrollView), const Offset(0, -300));
    await t.pumpAndSettle();
    await t.tap(find.text('풍경 위주'));
    await t.pump();
    expect(d.opts.people, 'scenery');
    expect(find.text('바꾼 것 1 · 나머지는 추천값'), findsOneWidget);
    await t.tap(find.text('추천으로 되돌리기'));
    await t.pump();
    expect(d.opts.people, 'mix');
  });

  testWidgets('book shape: expanded look', (t) async {
    final d = NewBookDraft()..opts.people = 'scenery';
    await pump(t, shape(d));
    await t.tap(find.text('더 보기'));
    await t.pumpAndSettle();
    await t.drag(find.byType(SingleChildScrollView), const Offset(0, -560));
    await t.pumpAndSettle();
    await shot(t, 'd3-6-more');
    expect(find.text('담담한 일기체'), findsOneWidget);
  });

  testWidgets('notes', (t) async {
    await pump(t, NotesScreen(items: sampleNotes, onBack: no, onPaste: no, onFile: no, onHandwriting: no, onTapItem: (_) {}, onDone: no),
        'd3-7-notes');
    expect(find.text('2개 넣음'), findsOneWidget);
    expect(find.text('2개 넣기'), findsOneWidget);
    expect(find.text('제주 메모.txt'), findsOneWidget);
  });

  testWidgets('timeline help', (t) async {
    await pump(t, TimelineHelpScreen(range: rangeShort(DateTime(2026, 6, 12), DateTime(2026, 6, 14)), onBack: no, onPickFile: no, onOpenSettings: no),
        'd3-8-timeline');
    expect(find.text('구글 타임라인 넣기'), findsOneWidget);
    expect(find.text('파일 고르기'), findsOneWidget);
    expect(find.text('폰 설정 열기'), findsOneWidget);
  });
}
