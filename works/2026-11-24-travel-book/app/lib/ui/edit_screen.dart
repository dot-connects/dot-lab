// 다듬기 (mockup edit-ui, operator 11-27 "원래 6단계 전부" minus 정보 줄): one decision card per step under the page
// itself. The page is the book runtime ([page] builder) so every change shows exactly as the book will look.
import 'dart:async';
import 'dart:math' as math;

import 'package:material_ui/material_ui.dart';

import '../core/edit.dart';
import '../core/models.dart';
import 'tokens.dart';
import 'widgets.dart';

enum EditMode { none, text, photo }

/// What the page view shows: page [page] of the working book, edited in [mode]; [version] bumps when it must redraw.
class EditPageArgs {
  final int page, slot, version;
  final EditMode mode;
  const EditPageArgs(this.page, this.mode, this.slot, this.version);
}

/// What the page view reports back.
class EditEvents {
  final void Function(int page, String key, String value) onText;
  final void Function(int page, int slot) onSlot;
  final void Function(int page, int slot, String pos) onCrop;
  final ValueChanged<bool> onCoverPaper; // false = the cover is all photo: its colour can't show
  const EditEvents(this.onText, this.onSlot, this.onCrop, this.onCoverPaper);
}

/// Same rule as the runtime's whiteOn: white words when they read better on [hex] than near-black ones.
bool whiteOn(String hex) {
  double lin(int v) {
    final c = v / 255;
    return c <= .03928 ? c / 12.92 : math.pow((c + .055) / 1.055, 2.4).toDouble();
  }

  double lum(int n) => .2126 * lin(n >> 16 & 255) + .7152 * lin(n >> 8 & 255) + .0722 * lin(n & 255);
  final l = lum(int.parse(hex.substring(1), radix: 16));
  return 1.05 / (l + .05) >= (l + .05) / (lum(0x161616) + .05);
}

String _hex(Color c) => '#${(c.toARGB32() & 0xFFFFFF).toRadixString(16).padLeft(6, '0').toUpperCase()}';
Color _color(String hex) => Color(0xFF000000 | int.parse(hex.substring(1), radix: 16));

class EditScreen extends StatefulWidget {
  final BookEdit edit;
  final Widget Function(EditPageArgs args, EditEvents events) page;
  final List<Candidate> Function(String id) alternatives; // same day, best first
  final Future<void> Function(Set<String> ids) copyMedia;
  final Future<void> Function() onSave;
  final VoidCallback onClose;
  const EditScreen(
      {super.key,
      required this.edit,
      required this.page,
      required this.alternatives,
      required this.copyMedia,
      required this.onSave,
      required this.onClose});
  @override
  State<EditScreen> createState() => _EditScreenState();
}

class _EditScreenState extends State<EditScreen> {
  BookEdit get e => widget.edit;
  EditStep _step = EditStep.cover;
  final Map<EditStep, int> _at = {}; // page shown per step
  int _slot = 0, _version = 0;
  bool _swapMode = false, _coverPaper = true, _custom = false, _saving = false;
  String? _snack;
  bool _undoable = false; // the snack's change is the one undo() takes back
  Timer? _snackOff;

  /// A short note over the bottom of the page (never pushing the page smaller); it goes by itself.
  void _note(String text, {bool undoable = false}) {
    _snack = text;
    _undoable = undoable;
    _snackOff?.cancel();
    _snackOff = Timer(const Duration(seconds: 4), () {
      if (mounted) setState(() => _snack = null);
    });
  }

  @override
  void dispose() {
    _snackOff?.cancel();
    super.dispose();
  }

  List<int> get _pages {
    final ps = e.b.pages;
    return switch (_step) {
      EditStep.cover || EditStep.check => [0],
      EditStep.type => [for (var i = 0; i < ps.length; i++) i],
      EditStep.words => [for (var i = 0; i < ps.length; i++) if (e.hasWords(i)) i],
      EditStep.photos => [for (var i = 0; i < ps.length; i++) if (ps[i].ids.isNotEmpty) i],
    };
  }

  int get _page {
    // 글자 opens on a page with body text: the cover's title does not change with the text size
    final ps = _pages, p = _at[_step] ?? (_step == EditStep.type ? ps.firstWhere((i) => i > 0 && e.hasWords(i), orElse: () => ps.first) : ps.first);
    return ps.contains(p) ? p : ps.firstWhere((x) => x >= p, orElse: () => ps.last);
  }

  EditMode get _mode => switch (_step) { EditStep.words => EditMode.text, EditStep.photos => EditMode.photo, _ => EditMode.none };

  void _redraw(VoidCallback f) => setState(() {
        f();
        _version++;
      });

  void _go(EditStep s) => setState(() {
        if (_step == EditStep.photos && s != EditStep.photos) e.forgetUndo();
        _step = s;
        _slot = 0;
        _swapMode = false;
        _snack = null;
      });

  void _turn(int d) {
    final ps = _pages, k = ps.indexOf(_page) + d;
    if (k < 0 || k >= ps.length) return;
    setState(() {
      _at[_step] = ps[k];
      _slot = 0;
      _swapMode = false;
      _snack = null;
    });
  }

  Future<void> _save() async {
    if (_saving) return;
    setState(() => _saving = true);
    try {
      await widget.onSave();
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _close() async {
    if (!e.anyChange) return widget.onClose();
    final drop = await showDialog<bool>(
      context: context,
      builder: (c) => AlertDialog(
        backgroundColor: C.paper,
        content: Text('다듬은 걸 버리고 나갈까요?', style: ui(15, w: FontWeight.w600)),
        actions: [
          TextButton(onPressed: () => Navigator.of(c).pop(false), child: Text('계속 다듬기', style: ui(14, color: C.ink2))),
          TextButton(onPressed: () => Navigator.of(c).pop(true), child: Text('버리기', style: ui(14, w: FontWeight.w700))),
        ],
      ),
    );
    if (drop == true) widget.onClose();
  }

  // ----- photo step actions -----
  String _place(String id) {
    final c = e.b.pool.where((x) => x.id == id).firstOrNull;
    return c?.place ?? '사진';
  }

  /// Copies files in, then redraws whatever happened (a failed copy still leaves page and data in step).
  Future<void> _withMedia(Set<String> ids, VoidCallback after) async {
    try {
      await widget.copyMedia(ids);
    } finally {
      if (mounted) _redraw(after);
    }
  }

  Future<void> _swapTo(String id) async {
    final page = _page, slot = _slot; // the person may turn the page while the file copies
    await _withMedia({id}, () {
      e.swap(page, slot, id);
      _note('사진을 바꿨어요', undoable: true);
    });
  }

  Future<void> _remove() async {
    final page = _page, p = e.b.pages[page];
    if (_slot >= p.ids.length) return;
    final old = p.ids[_slot];
    final alts = [for (final c in widget.alternatives(old)) c.id];
    final before = p.ids.toSet();
    final r = e.remove(page, _slot, alts);
    if (r == RemoveResult.kept) {
      setState(() => _note('이 쪽에서는 뺄 수 없어요'));
      return;
    }
    final added = r == RemoveResult.filled ? e.b.pages[page].ids.toSet().difference(before) : <String>{};
    await _withMedia(added, () {
      _slot = 0;
      _swapMode = false;
      _note(
          switch (r) {
            RemoveResult.closedUp => '「${_place(old)}」 뺐어요 · ${p.ids.length}장 → ${p.ids.length - 1}장',
            RemoveResult.filled => '빼고 같은 날 사진으로 채웠어요',
            _ => '「${_place(old)}」 쪽을 뺐어요',
          },
          undoable: true);
    });
  }

  // ----- build -----
  @override
  Widget build(BuildContext context) {
    final s = _step.index, last = _step == EditStep.check, ps = _pages, k = ps.indexOf(_page);
    // typing: the page takes the whole screen above the keyboard (the runtime zooms to the words)
    final typing = MediaQuery.viewInsetsOf(context).bottom > 0;
    final events = EditEvents(
      (page, key, value) => setState(() => e.setText(page, key, value)),
      (page, slot) => setState(() {
        _slot = slot;
        _swapMode = false;
      }),
      // no note for a move or zoom (operator 11-27: it shrank the page); 원본으로 by the title takes it back
      (page, slot, pos) => setState(() => e.setCrop(page, slot, pos)),
      (paper) {
        if (paper != _coverPaper && mounted) setState(() => _coverPaper = paper);
      },
    );
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (did, _) {
        if (!did) _close();
      },
      child: Scaffold(
        backgroundColor: C.paper,
        body: SafeArea(
          child: Column(children: [
            if (!typing) ...[
            SizedBox(
              height: 48,
              child: Row(children: [
                IconButton(onPressed: _close, icon: const Icon(Icons.close, size: 20, color: C.ink2)),
                Expanded(child: Text(stepNames[s], style: ui(17, w: FontWeight.w700))),
                Text('${s + 1} / ${stepNames.length}', style: nm(13, color: C.ink2)),
                if (!last)
                  TextButton(onPressed: _saving ? null : _save, child: Text('바로 저장', style: ui(14, w: FontWeight.w700, color: C.oak2)))
                else
                  const SizedBox(width: 16),
              ]),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 0, 20, 6),
              child: Row(children: [
                for (var i = 0; i < stepNames.length; i++) ...[
                  if (i > 0) const SizedBox(width: 4),
                  Expanded(child: Container(height: 3, decoration: BoxDecoration(color: i <= s ? C.ink : C.line, borderRadius: BorderRadius.circular(2)))),
                ],
              ]),
            ),
            ],
            Expanded(
              child: Container(
                color: C.gridBg,
                child: Column(children: [
                  Expanded(
                    child: Stack(children: [
                      Positioned.fill(child: widget.page(EditPageArgs(_page, _mode, _slot, _version), events)),
                      if (_snack != null) Positioned(left: 0, right: 0, bottom: 0, child: _snackBar()),
                    ]),
                  ),
                  if (ps.length > 1 && !typing)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 8),
                      child: Row(mainAxisAlignment: MainAxisAlignment.center, children: [
                        _round(Icons.chevron_left, k > 0 ? () => _turn(-1) : null, '이전 쪽'),
                        Container(
                          margin: const EdgeInsets.symmetric(horizontal: 8),
                          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                          decoration: BoxDecoration(color: C.paper, borderRadius: BorderRadius.circular(16)),
                          child: Text(_page == 0 ? '표지' : '$_page쪽 / ${e.b.pages.length - 1}', style: nm(13)),
                        ),
                        _round(Icons.chevron_right, k < ps.length - 1 ? () => _turn(1) : null, '다음 쪽'),
                      ]),
                    ),
                ]),
              ),
            ),
            if (!typing) ...[
            ConstrainedBox(
              constraints: BoxConstraints(maxHeight: MediaQuery.sizeOf(context).height * .5),
              child: SingleChildScrollView(
                padding: const EdgeInsets.fromLTRB(20, 14, 20, 0),
                child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: _card()),
              ),
            ),
            Padding(padding: const EdgeInsets.fromLTRB(20, 12, 20, 14), child: _buttons(last)),
            ],
          ]),
        ),
      ),
    );
  }

  Widget _round(IconData i, VoidCallback? onTap, String label) => Semantics(
        label: label,
        button: true,
        child: GestureDetector(
          onTap: onTap,
          child: Opacity(
            opacity: onTap == null ? .35 : 1,
            child: Container(
                width: 40, height: 40, decoration: const BoxDecoration(color: C.paper, shape: BoxShape.circle), child: Icon(i, color: C.ink)),
          ),
        ),
      );

  Widget _snackBar() => Container(
        margin: const EdgeInsets.fromLTRB(12, 0, 12, 10),
        padding: const EdgeInsets.fromLTRB(14, 4, 4, 4),
        decoration: BoxDecoration(color: C.ink, borderRadius: BorderRadius.circular(12)),
        child: Row(children: [
          Expanded(child: Text(_snack!, style: ui(13, color: C.paper))),
          if (_undoable && e.canUndo && _step == EditStep.photos)
            TextButton(
              onPressed: () => _redraw(() {
                e.undo();
                _snack = null;
                _undoable = false;
                _slot = 0;
              }),
              child: Text('되돌리기', style: ui(13, w: FontWeight.w700, color: C.oak1)),
            )
          else
            const SizedBox(height: 40),
        ]),
      );

  Widget _buttons(bool last) {
    Widget out(String label, VoidCallback? onTap) => SizedBox(
        width: 84,
        child: Opacity(
          opacity: onTap == null ? .35 : 1,
          child: GestureDetector(
            onTap: onTap,
            child: Container(
              height: 50,
              alignment: Alignment.center,
              decoration: BoxDecoration(border: Border.all(color: C.ink, width: 1.5), borderRadius: BorderRadius.circular(25)),
              child: Text(label, style: ui(15, w: FontWeight.w600)),
            ),
          ),
        ));
    // one main button (다음 / 저장하기); 이전 only where there is a step before, 원본 is a small link by the step title
    final prev = [if (_step.index > 0) ...[out('이전', () => _go(EditStep.values[_step.index - 1])), const SizedBox(width: 8)]];
    if (last) return Row(children: [...prev, Expanded(child: Btn(_saving ? '저장하는 중…' : '저장하기', onTap: _saving ? null : _save))]);
    final next = Btn('다음', onTap: () => _go(EditStep.values[_step.index + 1]));
    return Row(children: [...prev, Expanded(child: next)]);
  }

  Widget _h(String title, String q) => Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          Expanded(child: Text(title, style: ui(19, w: FontWeight.w800))),
          if (_step != EditStep.check && e.changed(_step))
            GestureDetector(
              onTap: () {
                e.reset(_step);
                _withMedia(e.b.pages.first.ids.toSet(), () => _snack = null);
              },
              child: Padding(
                padding: const EdgeInsets.fromLTRB(12, 6, 0, 6),
                child: Row(mainAxisSize: MainAxisSize.min, children: [
                  const Icon(Icons.undo_rounded, size: 15, color: C.ink2),
                  const SizedBox(width: 3),
                  Text('원본으로', style: ui(12.5, w: FontWeight.w600, color: C.ink2)),
                ]),
              ),
            ),
        ]),
        Padding(padding: const EdgeInsets.only(top: 3, bottom: 12), child: Text(q, style: ui(13, color: C.body, h: 1.45))),
      ]);

  List<Widget> _card() => switch (_step) {
        EditStep.cover => _coverCard(),
        EditStep.type => _typeCard(),
        EditStep.words => _wordsCard(),
        EditStep.photos => _photoCard(),
        EditStep.check => _checkCard(),
      };

  List<Widget> _coverCard() {
    final covers = e.coverLayouts, now = e.b.pages.first.layout, c = e.b.coverColor;
    return [
      _h('표지', '모양과 색을 골라요. 글자색은 알아서 맞춰요.'),
      Sg([for (final (i, d) in covers.indexed) SgItem(d.id, '표지 ${i + 1}')], now, (id) {
        e.setCoverLayout(id);
        _withMedia(e.b.pages.first.ids.toSet(), () {});
      }),
      const OptLabel('표지 색'),
      Wrap(spacing: 8, runSpacing: 8, children: [
        for (final (hex, name) in coverSwatches) _swatch(hex, name, on: !_custom && c == hex),
        _rainbow(),
      ]),
      if (_custom) _hueSlider(),
      Padding(
        padding: const EdgeInsets.only(top: 8),
        child: Text(
          !_coverPaper
              ? '이 표지는 사진이 꽉 차서 색이 보이지 않아요. 다른 표지를 고르면 보여요.'
              : c == null
                  ? '테마의 종이 색 그대로예요.'
                  : '글자는 ${whiteOn(c) ? '흰색' : '먹색'}으로 맞췄어요.',
          style: ui(12.5, color: C.ink2, h: 1.4),
        ),
      ),
    ];
  }

  Widget _swatch(String? hex, String name, {required bool on}) => Semantics(
        label: '표지 색 $name',
        selected: on,
        button: true,
        child: GestureDetector(
          onTap: () => _redraw(() {
            _custom = false;
            e.b.coverColor = hex;
          }),
          child: Container(
            width: 40,
            height: 40,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: hex == null ? C.paper : _color(hex),
              border: Border.all(color: on ? C.ink : C.line, width: on ? 2.5 : 1),
            ),
            child: hex == null
                ? Center(child: Text('기본', style: ui(10, w: FontWeight.w600, color: C.ink2)))
                : on
                    ? Icon(Icons.check, size: 18, color: whiteOn(hex) ? Colors.white : C.ink)
                    : null,
          ),
        ),
      );

  Widget _rainbow() => Semantics(
        label: '표지 색 직접 고르기',
        button: true,
        selected: _custom,
        child: GestureDetector(
          onTap: () => setState(() => _custom = !_custom),
          child: Container(
            width: 40,
            height: 40,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              border: _custom ? Border.all(color: C.ink, width: 2.5) : null,
              gradient: const SweepGradient(colors: [
                Color(0xFFE5484D), Color(0xFFF5A524), Color(0xFFE7D33A), Color(0xFF46A758), Color(0xFF3E8EED), Color(0xFF8E4EC6), Color(0xFFE5484D),
              ]),
            ),
          ),
        ),
      );

  // hue around the wheel; light colours at the left end of the second slider, deep ones at the right
  double _hue = 210, _depth = .6;
  Widget _hueSlider() {
    void set() => _redraw(() => e.b.coverColor = _hex(HSLColor.fromAHSL(1, _hue, .45, .88 - _depth * .7).toColor()));
    return Column(children: [
      Row(children: [
        SizedBox(width: 40, child: Text('색', style: ui(12, color: C.ink2))),
        Expanded(child: Slider(value: _hue, max: 359, activeColor: HSLColor.fromAHSL(1, _hue, .6, .5).toColor(), onChanged: (v) => setState(() => _hue = v), onChangeEnd: (_) => set())),
      ]),
      Row(children: [
        SizedBox(width: 40, child: Text('진하기', style: ui(12, color: C.ink2))),
        Expanded(child: Slider(value: _depth, activeColor: C.ink, onChanged: (v) => setState(() => _depth = v), onChangeEnd: (_) => set())),
      ]),
    ]);
  }

  List<Widget> _typeCard() {
    final fonts = fontsOf(e.b.theme);
    return [
      _h('글자', '이 테마에 어울리는 글꼴 3개예요. 쪽 배치는 그대로예요.'),
      Row(children: [
        for (final (i, f) in fonts.indexed) ...[
          if (i > 0) const SizedBox(width: 8),
          Expanded(
            child: Semantics(
              button: true,
              selected: e.b.font == f.css,
              child: GestureDetector(
                onTap: () => _redraw(() => e.b.font = f.css),
                child: Container(
                  height: 88,
                  padding: const EdgeInsets.fromLTRB(10, 10, 10, 8),
                  decoration: BoxDecoration(
                    color: e.b.font == f.css ? C.cardOn : C.card,
                    borderRadius: BorderRadius.circular(14),
                    border: Border.all(color: e.b.font == f.css ? C.ink : Colors.transparent, width: 2),
                  ),
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Text('가나다', style: TextStyle(fontFamily: f.preview, fontSize: 24, color: C.ink, height: 1.1)),
                    Text('여행의 기록', style: TextStyle(fontFamily: f.preview, fontSize: 13, color: C.ink, height: 1.3)),
                    const Spacer(),
                    Text(f.label, style: ui(11.5, color: C.ink2)),
                  ]),
                ),
              ),
            ),
          ),
        ],
      ]),
      const OptLabel('글자 크기', note: '사진 설명·본문 글자만 바뀌어요'),
      Sg([for (final k in textSizeNames.keys) SgItem(k, textSizeNames[k]!)], e.b.textSize, (k) => _redraw(() => e.b.textSize = k)),
    ];
  }

  List<Widget> _wordsCard() {
    if (_page == 0) {
      final ideas = e.titleIdeas;
      return [
        _h('제목', '표지의 제목을 눌러 바로 고쳐요. 길면 표지에 맞게 줄여 줘요.'),
        if (ideas.isNotEmpty) ...[
          const OptLabel('AI가 지어 둔 후보', top: 0),
          Wrap(spacing: 8, runSpacing: 8, children: [
            for (final t in ideas)
              ChoiceChip(
                label: Text(t, style: ui(13.5, w: FontWeight.w600, color: e.b.title == t ? C.paper : C.ink)),
                selected: e.b.title == t,
                selectedColor: C.ink,
                backgroundColor: C.card,
                showCheckmark: false,
                side: BorderSide.none,
                onSelected: (_) => _redraw(() => e.setText(0, 'title', t)),
              ),
          ]),
        ],
        Padding(padding: const EdgeInsets.only(top: 10), child: Text('다음 쪽으로 넘기면 쪽마다 한 줄도 고칠 수 있어요.', style: ui(12.5, color: C.ink2))),
      ];
    }
    return [_h('쪽마다 한 줄', '쪽 위의 글자를 눌러 바로 고쳐요. 넘기면서 한 줄씩 봐요.')];
  }

  List<Widget> _photoCard() {
    final p = e.b.pages[_page], id = _slot < p.ids.length ? p.ids[_slot] : null;
    final alts = id == null || !_swapMode ? const <Candidate>[] : widget.alternatives(id).take(8).toList();
    Widget act(IconData i, String label, VoidCallback? onTap, {bool on = false, Color color = C.ink}) => Expanded(
          child: Semantics(
            button: true,
            selected: on,
            child: GestureDetector(
              onTap: onTap,
              child: Container(
                height: 58,
                decoration: BoxDecoration(color: on ? C.cardOn : C.card, borderRadius: BorderRadius.circular(14)),
                child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
                  Icon(i, size: 20, color: color),
                  const SizedBox(height: 3),
                  Text(label, style: ui(12.5, w: FontWeight.w600, color: color)),
                ]),
              ),
            ),
          ),
        );
    // operator 11-27: moving is what a finger on the photo always does (one finger moves, two zoom), so no 옮기기 button
    return [
      _h('쪽마다 사진', p.ids.length > 1 ? '사진을 눌러 고르고, 끌어서 옮기거나 두 손가락으로 키워요.' : '끌어서 옮기고, 두 손가락으로 키우거나 줄여요.'),
      Row(children: [
        act(Icons.swap_horiz, _swapMode ? '바꿀 사진 고르는 중' : '다른 사진으로 바꾸기', () => setState(() => _swapMode = !_swapMode), on: _swapMode),
        const SizedBox(width: 8),
        act(Icons.delete_outline, '이 사진 빼기', _remove, color: const Color(0xFFA3261B)),
      ]),
      if (_swapMode)
        alts.isEmpty
            ? Padding(padding: const EdgeInsets.only(top: 10), child: Text('같은 날 다른 사진이 없어요.', style: ui(12.5, color: C.ink2)))
            : SizedBox(
                height: 118,
                child: ListView.separated(
                  padding: const EdgeInsets.only(top: 10),
                  scrollDirection: Axis.horizontal,
                  itemCount: alts.length,
                  separatorBuilder: (_, _) => const SizedBox(width: 8),
                  itemBuilder: (_, i) {
                    final a = alts[i];
                    return GestureDetector(
                      onTap: () => _swapTo(a.id),
                      child: SizedBox(
                        width: 70,
                        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                          Photo('gallery:${a.id}', w: 70, h: 70, r: 10),
                          const SizedBox(height: 3),
                          Text('${two(a.local.hour)}:${two(a.local.minute)}', style: nm(11.5)),
                          Text(a.place ?? '같은 날', maxLines: 1, overflow: TextOverflow.ellipsis, style: ui(10.5, color: C.ink2)),
                        ]),
                      ),
                    );
                  },
                ),
              ),
    ];
  }

  // operator 11-27: no summary of the choices here, only the question and the buttons
  List<Widget> _checkCard() => [_h('이렇게 저장할까요?', '저장하면 책장의 책이 이대로 바뀌어요.')];

}
