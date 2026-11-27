// 다듬기 core: remove rules, undo, [원본] per step, and that the opened book is never touched until applyTo.
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:tripbook/core/book.dart';
import 'package:tripbook/core/edit.dart';
import 'package:tripbook/core/models.dart';

final _defs = [
  for (final j in jsonDecode(File('assets/book/themes/a/layouts.json').readAsStringSync()) as List) LayoutDef.fromJson(j as Map<String, dynamic>),
];

Book _book() {
  final cs = [
    for (var i = 0; i < 9; i++)
      Candidate(Shot(id: 'p$i', utc: DateTime.utc(2026, 6, 12, 1, i), w: 4, h: 3), sharp: 500, luma: 120, dhash: i, local: DateTime(2026, 6, 12, 10, i)),
  ];
  return Book(id: 'b', from: DateTime(2026, 6, 12), to: DateTime(2026, 6, 12), opts: BookOptions(), theme: 'a', pool: cs,
      picks: [for (final c in cs.take(6)) Pick(c.id, '', 90)], title: '제주', cover: 'p0', pages: [
    PageSpec('a/c-bleed', ['p0'], text: {'title': '제주', 'period': '6.12'}),
    PageSpec('a/strip3', ['p1', 'p2', 'p3'], text: {'cap': '협재'}),
    PageSpec('a/bleed', ['p4']),
    PageSpec('a/plate-text', ['p5'], text: {'cap': '바다', 'body': '길'}),
  ]);
}

LayoutDef _d(String id) => _defs.firstWhere((d) => d.id == id);

void main() {
  test('the opened book is untouched until applyTo', () {
    final real = _book(), e = BookEdit(real, _defs);
    e.setText(0, 'title', '바람의 섬');
    e.setCrop(1, 0, '20% 50%');
    e.remove(2, 0, []);
    expect(real.title, '제주');
    expect(real.pages[1].crop[0], '50% 50%');
    expect(real.pages.length, 4);
    e.applyTo(real);
    expect(real.title, '바람의 섬');
    expect(real.pages[0].text['title'], '바람의 섬');
    expect(real.pages.length, 3);
  });

  test('remove: closes up when a design has the other slots, else fills from the same day, else drops a lone page', () {
    final e = BookEdit(_book(), _defs);
    final strip = _d('a/strip3');
    final fits = _defs.any((x) => x.kind == strip.kind && x.id != strip.id && x.slots.join() == ([...strip.slots]..removeAt(0)).join());
    final r = e.remove(1, 0, ['p7']);
    if (fits) {
      expect(r, RemoveResult.closedUp);
      expect(e.b.pages[1].ids, ['p2', 'p3']);
      expect(e.b.picks.map((p) => p.id), isNot(contains('p1')));
    } else {
      expect(r, RemoveResult.filled);
      expect(e.b.pages[1].ids, ['p7', 'p2', 'p3']);
    }
    // a photo already on another page never fills a slot
    expect(e.remove(2, 0, ['p5']), RemoveResult.pageDropped);
    expect(e.b.pages.map((p) => p.layout), ['a/c-bleed', e.b.pages[1].layout, 'a/plate-text']);
    expect(e.origin, [0, 1, 3]);
    // the cover is never dropped
    expect(e.remove(0, 0, []), RemoveResult.kept);
    expect(e.removed, 2);
  });

  test('undo takes back the last photo change only', () {
    final e = BookEdit(_book(), _defs);
    e.swap(3, 0, 'p8');
    e.setCrop(3, 0, '10% 10%');
    e.undo();
    expect(e.b.pages[3].ids, ['p8']);
    expect(e.b.pages[3].crop[0], '50% 50%');
    expect(e.moved, 0);
    expect(e.swapped, 1);
    expect(e.canUndo, isFalse);
  });

  test('swapping the cover photo moves the book cover and the pick', () {
    final e = BookEdit(_book(), _defs);
    e.swap(0, 0, 'p8');
    expect(e.b.cover, 'p8');
    expect(e.b.picks.first.id, 'p8');
  });

  test('원본 puts back one step and leaves the others', () {
    final e = BookEdit(_book(), _defs);
    e.b.coverColor = '#1F3A6E';
    e.setCoverLayout('a/c-paper');
    e.b.font = 'Gowun Batang';
    e.setText(0, 'title', '섬');
    e.setText(3, 'cap', '파도');
    e.remove(2, 0, []);
    e.swap(1, 1, 'p8');
    expect([for (final s in EditStep.values) e.changed(s)], [true, true, true, true, false]);

    e.reset(EditStep.photos);
    expect(e.b.pages.map((p) => p.ids), [['p0'], ['p1', 'p2', 'p3'], ['p4'], ['p5']]);
    expect(e.b.pages[0].layout, 'a/c-paper'); // the cover's design is the cover step's
    expect(e.b.pages[3].text['cap'], '파도'); // typed words stay
    expect(e.changed(EditStep.photos), isFalse);

    e.reset(EditStep.words);
    expect(e.b.title, '제주');
    expect(e.b.pages[0].text['title'], '제주');
    expect(e.b.pages[3].text['cap'], '바다');

    e.reset(EditStep.cover);
    expect(e.b.pages[0].layout, 'a/c-bleed');
    expect(e.b.coverColor, isNull);
    expect(e.b.font, 'Gowun Batang');
    expect(e.anyChange, isTrue);
    e.reset(EditStep.type);
    expect(e.anyChange, isFalse);
  });

  test('words 원본 after a dropped page maps each page to where it came from', () {
    final e = BookEdit(_book(), _defs);
    e.remove(2, 0, []);
    e.setText(2, 'cap', '파도');
    e.reset(EditStep.words);
    expect(e.b.pages[2].text['cap'], '바다');
  });

  test('undo is only for the last photo change: typing words or another step forgets it', () {
    final e = BookEdit(_book(), _defs);
    e.setCrop(1, 0, '10% 50%');
    e.setText(3, 'cap', '파도');
    expect(e.canUndo, isFalse);
    e.swap(3, 0, 'p8');
    e.setCoverLayout('a/c-paper');
    expect(e.canUndo, isFalse);
    expect(e.b.pages[3].text['cap'], ''); // the old photo's caption left with it
  });

  test("closing up moves the later photos' numbered words with them", () {
    final e = BookEdit(_book(), _defs);
    e.b.pages[1].text.addAll({'cap': 'a', 'cap2': 'b', 'cap3': 'c', 'day': '6.12'});
    e.remove(1, 0, []);
    expect(e.b.pages[1].text, {'cap': 'b', 'cap2': 'c', 'cap3': '', 'day': '6.12'});
  });

  test('applyTo hands over a copy: edits after a failed save never reach the book', () {
    final real = _book(), e = BookEdit(real, _defs);
    e.applyTo(real);
    e.setText(3, 'cap', '파도');
    e.setCrop(1, 0, '0% 0%');
    expect(real.pages[3].text['cap'], '바다');
    expect(real.pages[1].crop[0], '50% 50%');
  });

  test('a page whose words were all cleared stays in the words step', () {
    final e = BookEdit(_book(), _defs);
    e.setText(3, 'cap', '');
    e.setText(3, 'body', '');
    expect(e.hasWords(3), isTrue);
    expect(e.hasWords(2), isFalse);
  });

  test('every theme offers three fonts, its own first', () {
    for (final t in ['a', 'b', 'c', 'diary', 'mag', 'photo', 'album']) {
      expect(fontsOf(t).length, 3, reason: t);
      expect(fontsOf(t).first.css, isNull, reason: t);
    }
  });
}
