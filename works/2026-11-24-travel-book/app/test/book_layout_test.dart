// checkLayout / repairLayout: the AI editor's plan is checked for one retry and always repaired into a usable book.
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:tripbook/core/book_layout.dart';
import 'package:tripbook/core/models.dart';

Candidate _c(String id, int day, int hour, {int w = 1200, int h = 800, bool video = false}) => Candidate(
      Shot(id: id, utc: DateTime.utc(2026, 6, day, hour), isVideo: video, w: w, h: h),
      sharp: 1, luma: 0.5, dhash: 0, local: DateTime(2026, 6, day, hour));

Map<String, dynamic> _ph(String id) => {'id': id, 'fx': 50, 'fy': 50};
Map<String, dynamic> _pg(String layout, List<String> ids, [Map<String, String> texts = const {}]) => {
      'layout': layout,
      'intent': '',
      'photos': [for (final i in ids) _ph(i)],
      'texts': [for (final e in texts.entries) {'key': e.key, 'text': e.value}],
    };

void main() {
  late List<LayoutDef> defs;
  late Map<String, Candidate> pool;
  setUpAll(() {
    final raw = File('assets/book/themes/a/layouts.json').readAsStringSync();
    defs = [for (final j in jsonDecode(raw) as List) LayoutDef.fromJson(j as Map<String, dynamic>)];
  });
  setUp(() {
    pool = {
      'l1': _c('l1', 12, 10),
      'l2': _c('l2', 12, 12),
      'p1': _c('p1', 13, 9, w: 800, h: 1200),
      'l3': _c('l3', 13, 15),
      'v1': _c('v1', 13, 16, video: true),
    };
  });

  test('designer layouts parse texts as limits', () {
    final plate = defs.firstWhere((d) => d.id == 'a/plate-text');
    expect(plate.limits, {'cap': 24, 'body': 110});
    expect(plate.texts, ['cap', 'body']);
    expect(plate.when, isNotEmpty);
    expect(layoutCatalog(defs).every((m) => !(m['id'] as String).contains('/ix')), isTrue);
  });

  test('slot fit: P/L/S/A/V', () {
    expect(slotFits('L', pool['l1']!.shot), isTrue);
    expect(slotFits('P', pool['l1']!.shot), isFalse);
    expect(slotFits('A', pool['p1']!.shot), isTrue);
    expect(slotFits('V', pool['v1']!.shot), isTrue);
    expect(slotFits('A', pool['v1']!.shot), isFalse);
    expect(slotFits('S', _c('s', 12, 1, w: 1000, h: 1000).shot), isTrue);
  });

  test('check: wrong slot, unknown layout, too long text, photo twice, cover not first', () {
    final body = {
      'pages': [
        _pg('a/drop', ['l1'], {'cap': '바다'}),
        _pg('a/plate', ['l2']), // P slot, landscape photo
        _pg('a/nope', ['l3']),
        _pg('a/drop', ['l3'], {'cap': '가' * 41}),
        _pg('a/drop', ['l3']),
      ],
    };
    final errs = checkLayout(body, defs, pool, layoutRules['a']!);
    expect(errs.join(' | '), contains('1쪽: 표지 틀은'));
    expect(errs.join(' | '), contains('l2(L) 는 P 칸'));
    expect(errs.join(' | '), contains('없는 틀 a/nope'));
    expect(errs.join(' | '), contains('41자 > 40자'));
    expect(errs.join(' | '), contains('l3: 2번'));
  });

  test('check: itinerary prose and too few prose pages are sent back', () {
    final body = {
      'pages': [
        _pg('a/c-paper', ['l1'], {'title': '제주'}),
        _pg('a/plate-text', ['l2'], {'body': '배를 탔고, 우도에 갔고, 카페에 앉았다.'}),
      ],
    };
    final errs = checkLayout(body, defs, pool, layoutRules['a']!);
    expect(errs.join(' | '), contains('일정을 나열'));
    expect(errs.join(' | '), contains('짧은 글(body)이 1번뿐')); // two days -> two
  });

  test('check (태안 book 11-27): clock-time captions, text-only pages, one layout everywhere', () {
    final many = {for (var i = 0; i < 12; i++) 'x$i': _c('x$i', 12, 8 + i)};
    final body = {
      'pages': [
        _pg('a/c-paper', ['x0'], {'title': '태안'}),
        for (var i = 1; i < 10; i++) _pg('a/drop', ['x$i'], {'cap': '서산 14:${10 + i}, 물 뒤로 산이 겹쳤다'}),
        _pg('a/text', [], {'body': '난로 불이 둥글게 흔들렸다.'}),
        _pg('a/text', [], {'body': '무릎 끝에 담요가 걸쳐져 있었다.'}),
      ],
    };
    final errs = checkLayout(body, defs, many, layoutRules['a']!).join(' | ');
    expect(errs, contains("'장소, 시각' 으로 시작"));
    expect(errs, contains('숫자(14:16)'));
    expect(errs, contains('글만 있는 쪽이 2쪽'));
    expect(errs, contains('같은 틀 a/drop'));
  });

  test('check: scenes go forward; a page of only food may gather the day', () {
    final order = {'l1': 0, 'l2': 1, 'p1': 2, 'l3': 3};
    final body = {
      'pages': [
        _pg('a/c-paper', ['l1'], {'title': '제주'}),
        _pg('a/drop', ['l3']),
        _pg('a/drop', ['l2']), // back to an earlier scene
      ],
    };
    expect(checkLayout(body, defs, pool, layoutRules['a']!, order: order).join(' | '), contains('장면 순서가 거꾸로 간 쪽: 3쪽'));
    expect(checkLayout(body, defs, pool, layoutRules['a']!, order: order, kinds: {'l2': '음식'}).join(' | '), isNot(contains('장면 순서')));
  });

  test('repair: the chosen cover photo replaces the cover photo the editor picked', () {
    final out = repairLayout({'pages': [_pg('a/c-paper', ['l1'], {'title': '제주', 'period': '6월'}), _pg('a/drop', ['l3'])]}, defs, pool,
        fallbackTitle: 't', coverPhoto: 'p1');
    expect(out.pages.first.ids, ['p1']);
    expect(defs.firstWhere((d) => d.id == out.pages.first.layout).slots.single, isIn(['P', 'A']));
    expect(out.pages.first.text['title'], '제주');
  });

  test('repair: drops broken pages, cuts text, puts the cover first, fills crops', () {
    final body = {
      'title': '제주',
      'pages': [
        _pg('a/drop', ['l2'], {'cap': '가' * 50, 'nope': 'x'}),
        _pg('a/c-paper', ['l1'], {'title': '제주'}),
        _pg('a/nope', ['l3']),
        _pg('a/drop', ['ghost']), // unknown photo -> the page would have a hole
        _pg('a/drop', ['l2']), // l2 again inside the book
        {'layout': 'a/drop', 'intent': '', 'photos': [{'id': 'l3', 'fx': 130, 'fy': 20}], 'texts': []},
      ],
    };
    final out = repairLayout(body, defs, pool, fallbackTitle: '여행');
    expect(out.title, '제주');
    expect([for (final p in out.pages) p.layout], ['a/c-paper', 'a/drop', 'a/drop']);
    expect(out.pages[1].text, {'cap': '가' * 40});
    expect(out.pages[2].crop, ['100% 20%']);
  });

  test('repair: no cover from the model -> one is made from the cover photo', () {
    final out = repairLayout({'pages': [_pg('a/drop', ['l2'])]}, defs, pool, fallbackTitle: '제주 2박 3일', coverPhoto: 'l1');
    expect(out.pages.first.ids, ['l1']);
    expect(out.pages.first.text['title'], '제주 2박 3일');
  });

  test('album stamps the first photo of each day, photo theme gets an index page', () {
    List<LayoutDef> load(String t) => [
          for (final j in jsonDecode(File('assets/book/themes/$t/layouts.json').readAsStringSync()) as List)
            LayoutDef.fromJson(j as Map<String, dynamic>)
        ];
    final album = load('album');
    final day = album.firstWhere((d) => d.kind == 'chapter' && d.texts.contains('stamp') && d.slots.length == 1);
    final cover = album.firstWhere((d) => d.kind == 'cover' && d.slots.length == 1);
    final out = repairLayout({'pages': [_pg(cover.id, ['l1']), _pg(day.id, ['l2'])]}, album, pool, fallbackTitle: 't');
    expect(out.pages[1].text['stamp'], 'JUN 12');

    final photo = load('photo');
    final pc = photo.firstWhere((d) => d.kind == 'cover' && d.slots.length == 1);
    final one = photo.firstWhere((d) => d.kind == 'photo' && d.slots.length == 1 && d.slots.first == 'A');
    final ids = List.generate(14, (i) => 'x$i');
    final many = {for (final id in ids) id: _c(id, 12, 1)};
    final pages = [_pg(pc.id, ['x0']), for (final id in ids.skip(1)) _pg(one.id, [id])];
    final o2 = repairLayout({'pages': pages}, photo, many, fallbackTitle: 't');
    expect(o2.pages.last.layout, contains('/ix'));
  });
}
