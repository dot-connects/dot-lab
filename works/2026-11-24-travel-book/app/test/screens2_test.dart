// D4-D6 screens at a 360x740 phone: mockup copy present, no overflow, the caps and states behave.
import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';
import 'package:tripbook/ui/book_view.dart';
import 'package:tripbook/ui/failures.dart';
import 'package:tripbook/ui/making/pairs.dart';
import 'package:tripbook/ui/making/picking.dart';
import 'package:tripbook/ui/making/picks_review.dart';
import 'package:tripbook/ui/making/swap_sheet.dart';
import 'package:tripbook/ui/making/writing.dart';
import 'package:tripbook/ui/new_book/dates.dart';
import 'package:tripbook/ui/new_book/draft.dart';
import 'package:tripbook/ui/new_book/extras.dart';
import 'package:tripbook/ui/new_book/notes.dart';
import 'package:tripbook/ui/price_sheet.dart';
import 'package:tripbook/ui/sample.dart';
import 'package:tripbook/ui/settings.dart';
import 'package:tripbook/ui/shelf.dart';
import 'package:tripbook/ui/tokens.dart';
import 'package:tripbook/ui/widgets.dart';

import 'harness.dart';

void main() {
  setUpAll(loadFonts);
  void no() {}

  testWidgets('picking', (t) async {
    await pump(
        t,
        PickingScreen(
            thumbs: samplePickStrip,
            inRange: 412,
            albums: 2,
            albumPhotos: 186,
            filtered: (blurDark: 34, similar: 88, capture: 7),
            sent: 57,
            picked: 38,
            must: 3,
            onClose: no),
        'd4-1-picking');
    expect(find.text('사진을 고르고 있어요'), findsOneWidget);
    expect(find.text('57'), findsOneWidget);
    expect(find.text('38'), findsOneWidget);
    expect(find.text(kw('흐림·어두움 34 · 비슷한 사진 88 · 화면 캡처 7')), findsOneWidget);
  });

  testWidgets('pairs: each tap moves on, the last one reports every kept id', (t) async {
    List<String>? kept;
    await pump(t, PairsScreen(pairs: samplePairs, onBack: no, onLeaveToApp: no, onDone: (k) => kept = k), 'd4-2-pairs');
    expect(find.text('협재 해변 · 6월 12일'), findsOneWidget);
    expect(find.text('다 맡기기'), findsOneWidget);
    for (var i = 0; i < samplePairs.length; i++) {
      await t.tap(find.text(samplePairs[i].bTime));
      await t.pump();
    }
    expect(kept, [for (final p in samplePairs) p.b.id]);
  });

  Widget review({int extra = 0, void Function(DateTime)? onAdd}) => PicksReviewScreen(
        days: [
          for (final (i, (d, ps)) in samplePicks.indexed)
            (d, [...ps, if (i == 2) for (var k = 0; k < extra; k++) PhotoRef('e$k', ps[k % ps.length].path)]),
        ],
        must: sampleMustPicks,
        coverId: sampleCoverId,
        from: 186,
        videos: 2,
        shapeLine: '여백의 책 · 보통 · 캡션 + 에세이',
        pages: 24,
        onTapPhoto: (_) {},
        onAdd: onAdd ?? (_) {},
        onBack: no,
        onShape: no,
        onMake: no,
      );

  testWidgets('picks review: badges, counts, add', (t) async {
    DateTime? added;
    await pump(t, review(onAdd: (d) => added = d), 'd4-3-review');
    expect(find.text('이 38장으로 만들어요'), findsOneWidget);
    expect(find.text('사진 38 · 영상 2'), findsOneWidget);
    expect(find.text('꼭'), findsNWidgets(3));
    expect(find.text('표지'), findsOneWidget);
    expect(find.text('이대로 책 만들기 · 약 24쪽'), findsOneWidget);
    expect(find.text('모양 바꾸기 ›'), findsOneWidget);
    await t.tap(find.text('+').first);
    expect(added, DateTime(2026, 6, 12));
  });

  testWidgets('picks review: [+] locks at 48 (photos + videos)', (t) async {
    var adds = 0;
    await pump(t, review(extra: 8, onAdd: (_) => adds++)); // 46 photos + 2 videos
    await t.tap(find.text('+').first);
    await t.pump();
    expect(adds, 0);
    expect(find.text('한 권에 48장까지'), findsOneWidget);
  });

  testWidgets('swap sheet', (t) async {
    await pump(t, review());
    final ctx = t.element(find.byType(PicksReviewScreen));
    showSheet<void>(
        ctx,
        (_) => SwapSheet(
            photo: samplePicks[0].$2[1],
            place: '협재 해변',
            when: '6.12 금 13:20',
            alts: sampleAlts,
            samePlace: 14,
            onSwap: (_) {},
            onMore: no,
            onRemove: no,
            onCover: no));
    await t.pumpAndSettle();
    await shot(t, 'd4-4-swap');
    expect(find.text('이 사진 대신'), findsOneWidget);
    expect(find.text('+11'), findsOneWidget);
    expect(find.text('아까 둘 중'), findsOneWidget);
    expect(find.text('표지로 쓰기'), findsOneWidget);
  });

  testWidgets('writing', (t) async {
    await pump(
        t,
        const WritingScreen(
            photo: 'assets/sample/hyeopjae_2.jpg',
            meta: '13:20 · 협재 해변',
            typing: '물이 생각보다 차',
            picked: 38,
            page: 14,
            pages: 24,
            tone: '담담한 일기체',
            theme: '여백의 책',
            onClose: _no),
        'd4-5-writing');
    expect(find.text('글을 쓰고 있어요'), findsOneWidget);
    expect(find.text('기다리는 중'), findsNWidgets(2));
    expect(find.text(kw('시각·장소를 붙이고 담담한 일기체로')), findsOneWidget);
  });

  Widget book({BookTip? tip, BookBanner? banner}) => BookView(
        title: '제주 2박 3일',
        page: const ColoredBox(color: Color(0xFFDDDDDD)),
        onBack: no,
        onEdit: no,
        onExport: no,
        tip: tip,
        banner: banner,
      );

  testWidgets('book view + page sheet (and the spent rewrite state)', (t) async {
    var ok = false;
    await pump(t, book(tip: BookTip('책장에 넣었어요. 이번 책은 무료예요 (2권 중 첫 번째).', onOk: () => ok = true)), 'd5-1-book');
    expect(find.text('다듬기'), findsOneWidget);
    expect(find.text('내보내기'), findsOneWidget);
    await t.tap(find.text('확인'));
    expect(ok, isTrue);
    var rewrote = 0;
    Future<void> sheet(int used) async {
      showSheet<void>(
          t.element(find.byType(BookView)),
          (_) => PageSheet(
              heading: '2쪽 · 협재 해변', when: '6.12 금 13:20', samePlace: 14, rewrites: used, onSwapPhoto: no, onRewrite: () => rewrote++));
      await t.pumpAndSettle();
    }

    await sheet(3);
    await shot(t, 'd5-2-page-sheet');
    await t.tap(find.text('글 다시 쓰기'));
    expect(rewrote, 1);
    Navigator.of(t.element(find.byType(PageSheet))).pop();
    await t.pumpAndSettle();
    await sheet(maxRewrites);
    expect(find.text(kw('이 책은 다시 쓰기를 다 썼어요')), findsOneWidget);
    await t.tap(find.text('글 다시 쓰기'));
    expect(rewrote, 1);
  });

  testWidgets('price: extras on a paid book, then the honest sheet', (t) async {
    await pump(
        t,
        ExtrasScreen(
            draft: NewBookDraft(),
            photos: 96,
            freeLeft: 0,
            onBack: no,
            onMust: no,
            onNotes: no,
            onTimeline: no,
            onMake: no,
            onShape: no),
        'd5-3-price-extras');
    expect(find.text('책 만들기 · 1,500원'), findsOneWidget);
    expect(find.text(kw('빠지면 안 되는 사진이 있으면 골라 두세요')), findsOneWidget);
    var made = false;
    showSheet<void>(t.element(find.byType(ExtrasScreen)), (_) => PriceSheet(line: '경주 10월 3–4일 · 약 16쪽', onLater: no, onMake: () => made = true));
    await t.pumpAndSettle();
    await shot(t, 'd5-4-price-sheet');
    expect(find.text('세 번째 책부터는\n1권에 1,500원이에요'), findsOneWidget);
    expect(find.text('이번엔 그냥 만들기'), findsOneWidget);
    expect(find.text('다음에'), findsOneWidget);
    await t.tap(find.text('이번엔 그냥 만들기'));
    expect(made, isTrue);
  });

  testWidgets('fail: fewer than 5 photos', (t) async {
    await pump(
        t,
        FewPhotosScreen(
            thumbs: const ['assets/sample/d2.jpg', 'assets/sample/d7.jpg', 'assets/sample/d1.jpg'],
            wider: '8월 14–16일',
            widerCount: 27,
            onClose: no,
            onWiden: no,
            onRedo: no),
        'd5-5-few');
    expect(find.text('이 기간엔 사진이 3장뿐이에요'), findsOneWidget);
    expect(find.text('8월 14–16일로 넓히기'), findsOneWidget);
  });

  testWidgets('fail: quota and offline banners', (t) async {
    await pump(t, book(banner: quotaBanner(onRetry: no, onClose: no)), 'd5-6-quota');
    expect(find.text('글 없이 만들었어요'), findsOneWidget);
    expect(find.text('닫기'), findsOneWidget);
    await pump(t, book(banner: offlineBanner(38, onRetry: no, onNoText: no)), 'd5-7-offline');
    expect(find.text('글을 못 썼어요'), findsOneWidget);
    expect(find.text('글 없이 두기'), findsOneWidget);
  });

  testWidgets('fail: unreadable note file', (t) async {
    await pump(
        t,
        NotesScreen(
            items: [sampleNotes[0], const NoteItem('여행일기.hwp', '이 파일만 빼고 만들어요. txt · md · html · pdf · 사진은 읽을 수 있어요', bad: true)],
            onBack: no,
            onPaste: no,
            onFile: no,
            onHandwriting: no,
            onTapItem: (_) {},
            onDone: no),
        'd5-8-badnote');
    expect(find.text('1개 넣음'), findsOneWidget);
    expect(find.text('1개 넣기'), findsOneWidget);
    expect(find.text('못 읽은 파일: 여행일기.hwp'), findsOneWidget);
    expect(find.text('한글 파일은 PDF로 저장해서 다시 넣어 보세요.'), findsOneWidget);
  });

  testWidgets('fail: timeline unreadable', (t) async {
    await pump(t, TimelineFailScreen(file: 'Records.json', range: '6월 12–14일', noGps: 6, onBack: no, onContinue: no, onOther: no), 'd5-9-timeline');
    expect(find.text('타임라인을 못 읽었어요'), findsOneWidget);
    expect(find.text('사진 위치로 계속'), findsOneWidget);
  });

  testWidgets('fail: partial photo access', (t) async {
    await pump(
        t,
        DatesScreen(month: sampleMonth, photos: samplePhotosPerDay, partial: 42, onOpenSettings: no, onClose: no, onNext: (_, _) {}),
        'd5-10-partial');
    expect(find.text('설정에서 모두 허용'), findsOneWidget);
    await t.tap(find.text('42장으로 계속'));
    await t.pump();
    expect(find.text('다음'), findsOneWidget);
  });

  testWidgets('settings', (t) async {
    ShelfView? v;
    await pump(
        t,
        SettingsScreen(
            view: ShelfView.display,
            quality: 'reduced',
            books: 5,
            freeLeft: 0,
            access: '모두 허용 · 위치 읽기 켜짐',
            version: '0.1.0',
            onView: (x) => v = x,
            onQuality: (_) {},
            onBack: no,
            onAccess: no,
            onTerms: no),
        'd6-settings');
    expect(find.text('책장 기본 보기'), findsOneWidget);
    expect(find.text('5권'), findsOneWidget);
    expect(find.text(kw('무료 2권 중 2권 씀 · 다음 책부터 1권 1,500원')), findsOneWidget);
    await t.tap(find.text('연표'));
    expect(v, ShelfView.timeline);
  });
}

void _no() {}
