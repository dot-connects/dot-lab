// 다듬기 (spec 79-92, mockup edit-ui): five steps that change a working copy of the book. [원본] puts one step back to
// how the book was when 다듬기 opened; the photo step keeps one undo for its last change.
import 'dart:convert';

import 'book.dart';
import 'models.dart';

/// A book font: [css] is the runtime's family (null = the theme's own), [preview] the app's cut-down copy for the card.
class BookFont {
  final String label;
  final String? css;
  final String preview;
  const BookFont(this.label, this.css, this.preview);
}

const _pre = BookFont('프리텐다드', null, 'Pretendard');
const _batang = BookFont('고운바탕', 'Gowun Batang', 'GowunBatang');
const _gaegu = BookFont('개구', 'Gaegu', 'Gaegu');
const _pen = BookFont('나눔펜', 'Nanum Pen Script', 'NanumPenScript');
const _dodum = BookFont('고운돋움', 'Gowun Dodum', 'GowunDodum');
const _hahmlet = BookFont('함렛', 'Hahmlet', 'Hahmlet');
const _dohyeon = BookFont('도현', 'Do Hyeon', 'DoHyeon');

/// Three fonts per theme: its own + the two the operator picked from rendered pages (11-27).
const themeFonts = <String, List<BookFont>>{
  'a': [_pre, _batang, _gaegu],
  'b': [_pre, _dodum, _gaegu],
  'c': [_pre, _batang, _pen],
  'diary': [BookFont('스포카 한 산스', null, 'Pretendard'), _pen, _gaegu],
  'mag': [_pre, _dodum, _hahmlet],
  'photo': [_pre, _dodum, _hahmlet],
  'album': [BookFont('수트', null, 'SUIT'), _dohyeon, _dodum],
};
List<BookFont> fontsOf(String theme) => themeFonts[theme] ?? const [_pre];

const textScale = {'s': 0.9, 'm': 1.0, 'l': 1.15};
const textSizeNames = {'s': '작게', 'm': '보통', 'l': '크게'};

/// Cover colours: null = the theme's own paper.
const coverSwatches = <(String?, String)>[
  (null, '기본'),
  ('#1E1E1E', '먹'),
  ('#1F3A6E', '남색'),
  ('#B4381C', '주홍'),
  ('#24543F', '초록'),
  ('#A8742A', '황토'),
  ('#E9DFC8', '모래'),
];

enum EditStep { cover, type, words, photos, check }

const stepNames = ['표지', '글자', '제목·한 줄', '사진', '확인'];

/// What [BookEdit.remove] did to the page.
enum RemoveResult { closedUp, filled, pageDropped, kept }

class BookEdit {
  final Book b; // the working copy
  final Book start; // as 다듬기 found it
  final List<LayoutDef> defs; // the theme's designs
  late final Map<String, LayoutDef> _def = {for (final d in defs) d.id: d};
  late List<int> origin = [for (var i = 0; i < b.pages.length; i++) i]; // current page -> start page
  int moved = 0, swapped = 0, removed = 0;
  final Set<int> typedPages = {};
  _Snap? _undo;

  BookEdit(Book book, this.defs)
      : b = _copy(book),
        start = _copy(book);

  // toJson hands out the book's own lists: a JSON round trip is the deep copy
  static Book copyBook(Book x) => _copy(x);
  static Book _copy(Book x) => Book.fromJson(jsonDecode(jsonEncode(x.toJson())) as Map<String, dynamic>);

  // ----- 표지 -----
  List<LayoutDef> get coverLayouts => [for (final d in defs) if (d.kind == 'cover') d];

  void setCoverLayout(String id) {
    final d = _def[id], p = b.pages.firstOrNull;
    if (d == null || p == null) return;
    final photo = p.ids.firstOrNull ?? b.cover ?? start.pages.first.ids.firstOrNull;
    final ids = [if (d.slots.isNotEmpty && photo != null) photo];
    _undo = null;
    b.pages[0] = PageSpec(id, ids, crop: [for (final _ in ids) p.crop.firstOrNull ?? '50% 50%'], text: {...p.text});
  }

  // ----- 제목·한 줄 -----
  void setText(int page, String key, String value) {
    if (page >= b.pages.length) return;
    _undo = null;
    b.pages[page].text[key] = value;
    if (page == 0 && key == 'title') b.title = value;
    typedPages.add(page);
  }

  /// Pages the words step walks: the cover and every page that had words or has them now (emptied ones stay).
  bool hasWords(int i) =>
      i == 0 || typedPages.contains(i) || [b.pages[i].text, start.pages[origin[i]].text].any((t) => t.values.any((v) => v.trim().isNotEmpty));

  List<String> get titleIdeas => {for (final t in b.texts.values) ...t.titles}.toList();

  // ----- 사진 -----
  void setCrop(int page, int slot, String pos) {
    if (page >= b.pages.length) return;
    final p = b.pages[page];
    if (slot >= p.crop.length || p.crop[slot] == pos) return;
    _remember();
    p.crop[slot] = pos;
    moved++;
  }

  /// The photo in [slot] becomes [newId] (the caller copies its file in).
  void swap(int page, int slot, String newId) {
    final p = b.pages[page];
    if (slot >= p.ids.length) return;
    _remember();
    _replace(page, slot, newId);
    swapped++;
  }

  /// Take the photo in [slot] off the page: a design with the remaining slots closes the page up; else the next
  /// photo of the same day fills the slot ([alts], best first); else a one-photo page goes (never the cover).
  RemoveResult remove(int page, int slot, List<String> alts) {
    final p = b.pages[page];
    if (slot >= p.ids.length) return RemoveResult.kept;
    final d = _def[p.layout];
    final rest = d == null || slot >= d.slots.length ? const <String>[] : ([...d.slots]..removeAt(slot));
    // a page closing up never turns into a 네 컷 strip (that one is only for a pose series)
    final fit = rest.isEmpty
        ? null
        : defs.where((x) => x.kind == d!.kind && x.id != d.id && !x.id.endsWith('/fourcut') && _same(x.slots, rest)).firstOrNull;
    final old = p.ids[slot];
    if (fit != null) {
      _remember();
      b.pages[page] = PageSpec(fit.id, [...p.ids]..removeAt(slot), crop: [...p.crop]..removeAt(slot), text: _closeUp(p.text, slot, p.ids.length));
      _drop(old);
      removed++;
      return RemoveResult.closedUp;
    }
    final next = alts.where((a) => !_onPages(a)).firstOrNull;
    if (next != null) {
      _remember();
      _replace(page, slot, next);
      removed++;
      return RemoveResult.filled;
    }
    if (page > 0 && p.ids.length == 1) {
      _remember();
      b.pages.removeAt(page);
      origin.removeAt(page);
      _drop(old);
      removed++;
      return RemoveResult.pageDropped;
    }
    return RemoveResult.kept;
  }

  bool get canUndo => _undo != null;
  void forgetUndo() => _undo = null;
  void undo() {
    final u = _undo;
    if (u == null) return;
    u.restore(this);
    _undo = null;
  }

  // ----- 원본 -----
  void reset(EditStep s) {
    _undo = null;
    switch (s) {
      case EditStep.cover:
        b.coverColor = start.coverColor;
        setCoverLayout(start.pages.first.layout);
      case EditStep.type:
        b.font = start.font;
        b.textSize = start.textSize;
      case EditStep.words:
        b.title = start.title;
        for (final (i, o) in origin.indexed) {
          final was = start.pages[o].text;
          b.pages[i].text
            ..clear()
            ..addAll({for (final k in {...b.pages[i].text.keys, ...was.keys}) k: was[k] ?? ''});
        }
        typedPages.clear();
      case EditStep.photos:
        final text = {for (final (i, o) in origin.indexed) o: b.pages[i].text};
        final cover = b.pages.first;
        b.pages = [
          for (final (o, p) in start.pages.indexed)
            o == 0
                ? PageSpec(cover.layout, [...p.ids], crop: [...p.crop], text: text[0] ?? {...p.text})
                : PageSpec(p.layout, [...p.ids], crop: [...p.crop], text: text[o] ?? {...p.text}),
        ];
        if (cover.ids.isEmpty) b.pages[0] = PageSpec(cover.layout, [], text: b.pages[0].text);
        origin = [for (var i = 0; i < b.pages.length; i++) i];
        b.picks = [...start.picks];
        b.cover = start.cover;
        b.spare = [...start.spare];
        setCoverLayout(cover.layout); // the cover's slots follow its design, not the start cover's
        moved = swapped = removed = 0;
        _undo = null;
      case EditStep.check:
    }
  }

  bool changed(EditStep s) => switch (s) {
        EditStep.cover => b.coverColor != start.coverColor || b.pages.first.layout != start.pages.first.layout,
        EditStep.type => b.font != start.font || b.textSize != start.textSize,
        EditStep.words => b.title != start.title || typedPages.isNotEmpty,
        EditStep.photos => moved + swapped + removed > 0,
        EditStep.check => false,
      };
  bool get anyChange => EditStep.values.any(changed);

  /// Copies the edit into [into] — a copy of it, so later edits never reach [into] (a failed save + 버리기).
  void applyTo(Book into) {
    final c = _copy(b);
    into
      ..title = c.title
      ..coverColor = c.coverColor
      ..font = c.font
      ..textSize = c.textSize
      ..pages = c.pages
      ..picks = c.picks
      ..cover = c.cover
      ..spare = c.spare
      ..texts = c.texts;
  }

  // ----- helpers -----
  bool _same(List<String> a, List<String> x) => a.length == x.length && [for (var i = 0; i < a.length; i++) a[i] == x[i]].every((e) => e);
  bool _onPages(String id) => b.pages.any((p) => p.ids.contains(id));

  void _replace(int page, int slot, String newId) {
    final p = b.pages[page], old = p.ids[slot];
    for (final k in _slotKeys(p.text, slot, p.ids.length)) {
      p.text[k] = ''; // the old photo's caption / time would sit under the new one
    }
    p.ids[slot] = newId;
    p.crop[slot] = '50% 50%';
    b.picks = [for (final x in b.picks) x.id == old ? Pick(newId, '', x.score) : x];
    if (b.cover == old) b.cover = newId;
    b.spare = [for (final s in b.spare) if (s != newId) s];
  }

  void _drop(String id) {
    if (_onPages(id)) return;
    b.picks = [for (final x in b.picks) if (x.id != id) x];
    if (b.cover == id) b.cover = b.pages.first.ids.firstOrNull;
  }

  /// Text keys that belong to photo [slot]: numbered families (cap, cap2, cap3 / cap1..cap4) by number; on a
  /// one-photo page every caption-like key. A single key shared by several photos belongs to the page.
  static List<String> _slotKeys(Map<String, String> text, int slot, int photos) {
    final re = RegExp(r'^(.*?)(\d*)$');
    final fam = <String, List<(String, int)>>{};
    for (final k in text.keys) {
      final m = re.firstMatch(k)!;
      fam.putIfAbsent(m.group(1)!, () => []).add((k, m.group(2)!.isEmpty ? 0 : int.parse(m.group(2)!)));
    }
    const pageWide = {'title', 'period', 'dayline', 'day', 'date', 'index', 'mast', 'stamp', 'colophon'};
    final out = <String>[];
    for (final e in fam.entries) {
      if (pageWide.contains(e.key)) continue;
      if (e.value.length == 1 && e.value.first.$2 == 0) {
        if (photos == 1) out.add(e.value.first.$1);
        continue;
      }
      final one = e.value.any((x) => x.$2 == 1); // cap1..capN vs cap, cap2..
      for (final (k, n) in e.value) {
        if ((one ? n - 1 : (n == 0 ? 0 : n - 1)) == slot) out.add(k);
      }
    }
    return out;
  }

  /// Photo [slot] left the page: its numbered words go and the later photos' words move up one.
  static Map<String, String> _closeUp(Map<String, String> text, int slot, int photos) {
    final out = {...text};
    final keyAt = <int, Map<String, String>>{}; // slot -> family -> key
    for (var i = 0; i < photos; i++) {
      for (final k in _slotKeys(text, i, photos)) {
        keyAt.putIfAbsent(i, () => {})[RegExp(r'^(.*?)\d*$').firstMatch(k)!.group(1)!] = k;
      }
    }
    for (var i = slot; i < photos; i++) {
      for (final e in (keyAt[i] ?? const <String, String>{}).entries) {
        final next = keyAt[i + 1]?[e.key];
        out[e.value] = next == null ? '' : text[next] ?? '';
      }
    }
    return out;
  }

  void _remember() => _undo = _Snap(this);
}

class _Snap {
  final List<Map<String, dynamic>> pages;
  final List<int> origin;
  final List<Pick> picks;
  final String? cover;
  final List<String> spare;
  final int moved, swapped, removed;
  _Snap(BookEdit e)
      : pages = [for (final p in e.b.pages) jsonDecode(jsonEncode(p.toJson())) as Map<String, dynamic>],
        origin = [...e.origin],
        picks = [...e.b.picks],
        cover = e.b.cover,
        spare = [...e.b.spare],
        moved = e.moved,
        swapped = e.swapped,
        removed = e.removed;
  void restore(BookEdit e) {
    e.b.pages = [for (final p in pages) PageSpec.fromJson(jsonDecode(jsonEncode(p)) as Map<String, dynamic>)];
    e.origin = [...origin];
    e.b
      ..picks = picks
      ..cover = cover
      ..spare = spare;
    e
      ..moved = moved
      ..swapped = swapped
      ..removed = removed;
  }
}
