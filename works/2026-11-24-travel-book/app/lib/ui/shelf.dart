// D2 shelf home (mockups/r24-home, picked 11-28): three views of the same books -- 진열 (face-out, two per shelf),
// 연도 (years on one line), 한 권씩 (one book on a stage) -- the empty shelf, and the long-press menu with an inline
// delete confirm.
import 'dart:math' as math;
import 'dart:ui' show ImageFilter;

import 'package:material_ui/material_ui.dart';

import '../core/library.dart';
import 'tokens.dart';

/// The mode button walks these in order: 진열 -> 연도 -> 한 권씩 -> 진열.
enum ShelfView {
  display('진열'),
  year('연도'),
  one('한 권씩');

  final String label;
  const ShelfView(this.label);
  ShelfView get next => values[(index + 1) % values.length];
}

const _wall2 = Color(0xFFE5DACA);
const _ease = Cubic(.2, .8, .2, 1);

class Shelf extends StatefulWidget {
  final List<ShelfBook> books;
  final ShelfView view;
  final ValueChanged<ShelfView> onViewChanged; // the caller remembers the choice (settings)
  final ValueChanged<ShelfBook> onOpen, onExport, onRename, onDelete;
  final VoidCallback onNew, onSettings;
  final DateTime? now;
  const Shelf({
    super.key,
    required this.books,
    required this.view,
    required this.onViewChanged,
    required this.onOpen,
    required this.onExport,
    required this.onRename,
    required this.onDelete,
    required this.onNew,
    required this.onSettings,
    this.now,
  });
  @override
  State<Shelf> createState() => _ShelfState();
}

class _ShelfState extends State<Shelf> {
  final _stackKey = GlobalKey();
  (ShelfBook, Rect)? _menu;
  bool _confirm = false;
  int _page = 0; // 한 권씩: the book in front (index into [_ordered])

  void _longPress(ShelfBook b, BuildContext bookCtx) {
    final box = bookCtx.findRenderObject() as RenderBox;
    final stack = _stackKey.currentContext!.findRenderObject() as RenderBox;
    final tl = stack.globalToLocal(box.localToGlobal(Offset.zero));
    setState(() {
      _menu = (b, tl & box.size);
      _confirm = false;
    });
  }

  void _close() => setState(() => _menu = null);

  /// Newest year first, newest trip first: the order of 연도 and 한 권씩.
  List<ShelfBook> get _ordered => [for (final (_, bs) in groupByYear(widget.books)) ...bs];

  @override
  Widget build(BuildContext context) {
    final books = widget.books;
    final photos = books.fold(0, (s, b) => s + b.photos);
    final view = widget.view;
    final ordered = _ordered;
    if (_page >= ordered.length) _page = math.max(0, ordered.length - 1);
    final ink2 = view == ShelfView.one && books.isNotEmpty ? const Color(0xFF48423A) : C.ink2; // grey on the tinted glow
    return Scaffold(
      body: Stack(children: [
        // the wall: light from above (진열, and under the 한 권씩 glow); 연도 sits on plain paper
        Positioned.fill(
          child: AnimatedOpacity(
            opacity: view == ShelfView.year ? 0 : 1,
            duration: const Duration(milliseconds: 400),
            // r28: a warm plaster wall -- light from above and a fine grain
            child: const DecoratedBox(
              decoration: BoxDecoration(
                gradient: LinearGradient(begin: Alignment.topCenter, end: Alignment.bottomCenter, colors: [Color(0xFFF1EADF), _wall2]),
              ),
              child: DecoratedBox(
                decoration: BoxDecoration(
                  gradient: RadialGradient(
                    center: Alignment.topCenter,
                    radius: 1.2,
                    colors: [Color(0xFFF7F2EA), Color(0x00F7F2EA)],
                    stops: [0, .7],
                  ),
                ),
                child: DecoratedBox(
                  decoration: BoxDecoration(
                    image: DecorationImage(image: AssetImage('assets/ui/plaster.png'), repeat: ImageRepeat.repeat, scale: 2),
                  ),
                ),
              ),
            ),
          ),
        ),
        Positioned.fill(
          child: AnimatedSwitcher(
            duration: const Duration(milliseconds: 500),
            child: view == ShelfView.one && ordered.isNotEmpty ? RepaintBoundary(child: _Glow(ordered[_page], key: const ValueKey('glow'))) : const SizedBox.expand(),
          ),
        ),
        SafeArea(
          child: Stack(key: _stackKey, children: [
            Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(24, 10, 14, 0),
                child: Row(children: [
                  Expanded(
                    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      Text('내 책장', style: ui(25, w: FontWeight.w800, h: 1.2).copyWith(letterSpacing: -.7)),
                      const SizedBox(height: 3),
                      Text(books.isEmpty ? '아직 비어 있어요' : '${books.length}권 · 사진 ${fmtInt(photos)}장',
                          style: nm(13, color: ink2, ls: -.1)),
                    ]),
                  ),
                  Semantics(
                    button: true,
                    label: '설정',
                    child: GestureDetector(
                      behavior: HitTestBehavior.opaque,
                      onTap: widget.onSettings,
                      child: SizedBox(width: 44, height: 44, child: Icon(Icons.settings_outlined, size: 21, color: ink2)),
                    ),
                  ),
                  const SizedBox(width: 2),
                  _mode(),
                ]),
              ),
              Expanded(
                child: AnimatedSwitcher(
                  duration: const Duration(milliseconds: 240),
                  switchInCurve: Curves.easeOut,
                  switchOutCurve: Curves.easeIn,
                  transitionBuilder: (child, a) => _roll(child, a, ValueKey(view), 6 / 700),
                  layoutBuilder: (cur, prev) => Stack(fit: StackFit.expand, children: [...prev, ?cur]), // fill: 한 권씩 starts at the top
                  child: KeyedSubtree(key: ValueKey(view), child: _body(view, ordered)),
                ),
              ),
            ]),
            Positioned(right: 20, bottom: 24, child: _fab(books.isEmpty ? '첫 책 만들기' : '새 책')),
            if (_menu != null) ..._overlay(),
          ]),
        ),
      ]),
    );
  }

  Widget _body(ShelfView v, List<ShelfBook> ordered) => switch (v) {
        _ when widget.books.isEmpty => v == ShelfView.year ? _emptyTimeline() : _empty(),
        ShelfView.display => _display(),
        ShelfView.year => _year(),
        ShelfView.one => _OneView(
            books: ordered,
            initial: _page,
            onPage: (i) => setState(() => _page = i),
            book: (b, w, h) => _book(b, w: w, h: h),
          ),
      };

  /// The incoming child rises in from below, the outgoing one leaves upwards (mockup: the view lifts 6px and fades;
  /// the mode glyph rolls up to the next one). [dy] is in child heights.
  static Widget _roll(Widget child, Animation<double> a, Key current, double dy) {
    final incoming = child.key == current;
    return FadeTransition(
      opacity: a,
      child: SlideTransition(
        position: Tween(begin: Offset(0, incoming ? dy : -dy), end: Offset.zero).animate(a),
        child: child,
      ),
    );
  }

  // operator 11-28: only the current view's icon, no words, no dots; a tap moves to the next view
  Widget _mode() {
    final v = widget.view;
    return Semantics(
      button: true,
      label: '보기: ${v.label}. 누르면 ${v.next.label}',
      excludeSemantics: true,
      child: GestureDetector(
        key: const ValueKey('shelf-mode'),
        behavior: HitTestBehavior.opaque,
        onTap: () => widget.onViewChanged(v.next),
        child: SizedBox(
          width: 44,
          height: 44,
          child: Center(
            child: Container(
              width: 36,
              height: 36,
              alignment: Alignment.center,
              decoration: const BoxDecoration(
                color: Color(0xC7FFFFFF),
                shape: BoxShape.circle,
                boxShadow: [
                  BoxShadow(color: Color(0x1A2B2621), spreadRadius: 1),
                  BoxShadow(color: Color(0x2E281A0C), blurRadius: 6, spreadRadius: -2, offset: Offset(0, 2)),
                ],
              ),
              child: ClipRect(
                child: SizedBox(
                  width: 18,
                  height: 18,
                  child: AnimatedSwitcher(
                    duration: const Duration(milliseconds: 340),
                    switchInCurve: _ease,
                    switchOutCurve: _ease,
                    transitionBuilder: (child, a) => _roll(child, a, ValueKey(v), 1.1),
                    child: CustomPaint(key: ValueKey(v), size: const Size(18, 18), painter: _ModeIcon(v)),
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _fab(String label) => GestureDetector(
        onTap: widget.onNew,
        child: Container(
          height: 54,
          padding: const EdgeInsets.fromLTRB(18, 0, 22, 0),
          decoration: BoxDecoration(
            color: C.ink,
            borderRadius: BorderRadius.circular(27),
            boxShadow: const [BoxShadow(color: Color(0x5928190A), blurRadius: 24, spreadRadius: -10, offset: Offset(0, 12))],
          ),
          child: Row(mainAxisSize: MainAxisSize.min, children: [
            Text('+', style: ui(22, color: C.paper, h: 1)),
            const SizedBox(width: 7),
            Text(label, style: ui(15, w: FontWeight.w700, color: C.paper).copyWith(letterSpacing: -.2)),
          ]),
        ),
      );

  String _short(DateTime d) {
    final now = widget.now ?? DateTime.now();
    final md = '${two(d.month)}.${two(d.day)}';
    return d.year == now.year ? md : '${two(d.year % 100)}.$md';
  }

  Widget _book(ShelfBook b, {double w = 112, double h = 150, bool shelf = false}) => Builder(
        builder: (ctx) => GestureDetector(
          onTap: () => widget.onOpen(b),
          onLongPress: () => _longPress(b, ctx),
          child: _Edged(b, child: _Cover(b, w: w, h: h, shelf: shelf)),
        ),
      );

  // ----- 진열 -----
  /// Two columns per shelf, as in the mockup grid (left 30, gap 30, right 36); the book fills most of its column.
  ({double col, double w, double h}) _cell(double width) { // the first Android frame can be 0 wide
    final col = math.max(0.0, (width - 30 - 30 - 36) / 2);
    final w = math.max(0.0, math.min(136.0, col - 10)).roundToDouble();
    return (col: col, w: w, h: (w * 4 / 3).roundToDouble());
  }

  /// One shelf (mockups/r28-shelf): the books stand on the board's top face, leaning back against the wall; the
  /// light under the board washes the wall below it (the labels and the next shelf's books).
  Widget _shelfRow(double h, List<Widget> books) => Padding(
        padding: const EdgeInsets.only(top: 34),
        child: SizedBox(
          height: h + 20,
          child: Stack(clipBehavior: Clip.none, children: [
            Positioned(
              left: 0,
              right: 0,
              top: h + 20,
              height: 300,
              child: const IgnorePointer(
                child: DecoratedBox(
                  decoration: BoxDecoration(
                    gradient: RadialGradient(
                      center: Alignment.topCenter,
                      radius: .95,
                      colors: [Color(0xCCFFEAC6), Color(0x00FFF0D7)],
                    ),
                  ),
                ),
              ),
            ),
            const Positioned(left: 12, right: 12, bottom: 0, height: 34, child: _Plank()),
            Positioned(
              left: 30,
              right: 36,
              bottom: 20,
              child: Row(crossAxisAlignment: CrossAxisAlignment.end, children: [
                for (final j in [0, 1]) ...[
                  if (j > 0) const SizedBox(width: 30),
                  Expanded(child: Align(alignment: Alignment.bottomLeft, child: j < books.length ? books[j] : null)),
                ],
              ]),
            ),
          ]),
        ),
      );

  /// A book leaning back against the wall: its shadow is tight and dark where the top touches the wall and widens,
  /// softer, down to the board; a dark line where it sits on the board.
  Widget _standing(ShelfBook b, double w, double h) => Stack(clipBehavior: Clip.none, children: [
        Positioned(
          left: -w * .03,
          right: -w * .15,
          top: 5,
          bottom: 4,
          child: const IgnorePointer(child: CustomPaint(painter: _LeanShadow())),
        ),
        Transform(
          alignment: Alignment.bottomCenter,
          transform: Matrix4.identity()
            ..setEntry(3, 2, 1 / 760)
            ..rotateX(-9 * math.pi / 180),
          child: _book(b, w: w, h: h, shelf: true),
        ),
        Positioned(
          left: w * .01,
          right: -w * .03,
          bottom: -3,
          height: 8,
          child: const IgnorePointer(child: CustomPaint(painter: _Contact())),
        ),
      ]);

  Widget _display() => LayoutBuilder(builder: (context, c) {
        final (:col, :w, :h) = _cell(c.maxWidth);
        final b = widget.books;
        return ListView(padding: const EdgeInsets.fromLTRB(0, 6, 0, 130), children: [
          for (var i = 0; i < b.length; i += 2) ...[
            _shelfRow(h, [for (final x in b.skip(i).take(2)) _standing(x, w, h)]),
            Padding(
              padding: const EdgeInsets.fromLTRB(30, 20, 36, 0),
              child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                for (final j in [0, 1]) ...[
                  if (j > 0) const SizedBox(width: 30),
                  SizedBox(
                    width: col,
                    child: i + j >= b.length
                        ? null
                        : Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                            Text(b[i + j].title,
                                style: ui(14.5, w: FontWeight.w700).copyWith(letterSpacing: -.3), maxLines: 1, overflow: TextOverflow.ellipsis),
                            const SizedBox(height: 3),
                            Text('${_short(b[i + j].start)} · ${b[i + j].pages}쪽', style: nm(12.5, color: C.ink2)),
                          ]),
                  ),
                ],
              ]),
            ),
          ],
        ]);
      });

  Widget _empty() => LayoutBuilder(builder: (context, c) {
        final (col: _, :w, :h) = _cell(c.maxWidth);
        return ListView(padding: const EdgeInsets.fromLTRB(0, 6, 0, 130), children: [
          _shelfRow(h, [
            SizedBox(
              width: w,
              height: h,
              child: CustomPaint(
                painter: _Dashed(),
                child: Center(
                  child: Text('첫 책이\n여기 놓여요', textAlign: TextAlign.center, style: ui(12.5, color: C.ink2, h: 1.5)),
                ),
              ),
            ),
          ]),
          _shelfRow(h, const []),
          Padding(
            padding: const EdgeInsets.fromLTRB(30, 26, 30, 0),
            child: Text('최근 여행 날짜를 골라 보세요.\n처음 2권은 무료예요.', style: ui(13.5, color: C.body, h: 1.6)),
          ),
        ]);
      });

  // ----- 연도 -----
  Widget _yearHead(int year, String sub, {Color color = C.ink}) => Container(
        padding: const EdgeInsets.fromLTRB(22, 20, 22, 12),
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [C.paper, C.paper, Color(0x00F4EFE7)],
            stops: [0, .78, 1],
          ),
        ),
        child: Row(crossAxisAlignment: CrossAxisAlignment.baseline, textBaseline: TextBaseline.alphabetic, children: [
          Expanded(child: Text('$year', style: nm(46, w: FontWeight.w300, h: 1, ls: -2.4, color: color))),
          Text(sub, style: nm(12.5, color: C.ink2)),
        ]),
      );

  /// The line down the left with a dot per trip; [children] sit 26px right of it.
  Widget _line(List<Widget> children) => Padding(
        padding: const EdgeInsets.symmetric(horizontal: 22),
        child: Stack(children: [
          Positioned(left: 4, top: 0, bottom: 0, width: 1, child: Container(color: C.line)),
          Padding(padding: const EdgeInsets.fromLTRB(26, 6, 0, 8), child: Column(children: children)),
        ]),
      );

  Widget _dot({bool hollow = false}) => Container(
        width: 9,
        height: 9,
        decoration: BoxDecoration(
          color: hollow ? C.paper : C.ink,
          shape: BoxShape.circle,
          border: hollow
              ? Border.all(color: C.ink2, width: 1.2)
              : Border.all(color: C.paper, width: 3, strokeAlign: BorderSide.strokeAlignOutside),
        ),
      );

  /// The empty 연도: this year's heading and a dashed place on the line where the first trip will sit.
  Widget _emptyTimeline() => ListView(padding: const EdgeInsets.only(bottom: 130), children: [
        _yearHead((widget.now ?? DateTime.now()).year, '0권', color: C.ink2),
        _line([
          Stack(clipBehavior: Clip.none, children: [
            Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
              SizedBox(width: 78, height: 104, child: CustomPaint(painter: _Dashed())),
              const SizedBox(width: 16),
              Expanded(
                child: Padding(
                  padding: const EdgeInsets.only(top: 2),
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Text('여행 날짜 · 몇 박', style: nm(12.5, color: C.ink2)),
                    const SizedBox(height: 4),
                    Text('첫 여행이 여기 놓여요', style: ui(17, w: FontWeight.w800, color: C.ink2)),
                  ]),
                ),
              ),
            ]),
            Positioned(left: -26, top: 7, child: _dot(hollow: true)),
          ]),
        ]),
        Padding(
          padding: const EdgeInsets.fromLTRB(22, 26, 22, 0),
          child: Text('다녀온 순서대로 해마다 쌓여요.\n처음 2권은 무료예요.', style: ui(13.5, color: C.body, h: 1.6)),
        ),
      ]);

  /// Each year's number sticks to the top while its trips scroll under it, then the next year pushes it away.
  Widget _year() => CustomScrollView(slivers: [
        for (final (year, books) in groupByYear(widget.books))
          SliverMainAxisGroup(slivers: [
            PinnedHeaderSliver(
              child: Builder(builder: (_) {
                final t = yearTotals(books);
                return _yearHead(year, '${t.books}권 · ${t.places}곳 · ${t.days}일');
              }),
            ),
            SliverToBoxAdapter(child: _line([for (final b in books) _trip(b)])),
          ]),
        const SliverToBoxAdapter(child: SizedBox(height: 130)),
      ]);

  Widget _trip(ShelfBook b) {
    Widget n(int v, String u) => Text.rich(TextSpan(children: [
          TextSpan(text: '$v', style: nm(12.5, w: FontWeight.w700)),
          TextSpan(text: u),
        ]), style: nm(12.5, color: C.ink2));
    // the whole row opens the book (operator 11-27: tapping the title did nothing); long press = the book menu
    return Builder(
      builder: (ctx) => GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: () => widget.onOpen(b),
        onLongPress: () => _longPress(b, ctx),
        child: Padding(
          padding: const EdgeInsets.only(bottom: 22),
          child: Stack(clipBehavior: Clip.none, children: [
            Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
              _book(b, w: 78, h: 104),
              const SizedBox(width: 16),
              Expanded(
                child: SizedBox(
                  height: 104,
                  child: Padding(
                    padding: const EdgeInsets.symmetric(vertical: 2),
                    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      Text(_dates(b), style: nm(12.5, color: C.ink2)),
                      const SizedBox(height: 4),
                      Text(b.title,
                          style: ui(17, w: FontWeight.w800, h: 1.3).copyWith(letterSpacing: -.4), maxLines: 2, overflow: TextOverflow.ellipsis),
                      const Spacer(),
                      Row(children: [
                        n(b.places, '곳'),
                        const SizedBox(width: 12),
                        n(b.photos, '장'),
                        if (b.km != null) ...[const SizedBox(width: 12), n(b.km!, 'km')],
                      ]),
                    ]),
                  ),
                ),
              ),
            ]),
            Positioned(left: -26, top: 7, child: _dot()),
          ]),
        ),
      ),
    );
  }

  // ----- long-press menu -----
  List<Widget> _overlay() {
    final (b, r) = _menu!;
    final size = (_stackKey.currentContext!.findRenderObject() as RenderBox).size;
    final small = r.width < 100; // timeline cover
    final left = math.min(r.left + (small ? 60 : 88), size.width - 168 - 12).clamp(12.0, size.width);
    final top = math.min(r.top + (small ? 30 : 70), size.height - 300).clamp(0.0, size.height);
    Widget row(IconData ic, String t, VoidCallback f) => GestureDetector(
          behavior: HitTestBehavior.opaque,
          onTap: f,
          child: SizedBox(
            height: 42,
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 10),
              child: Row(children: [Icon(ic, size: 18, color: C.ink), const SizedBox(width: 10), Text(t, style: ui(13.5, w: FontWeight.w600))]),
            ),
          ),
        );
    void act(ValueChanged<ShelfBook> f) {
      _close();
      f(b);
    }

    Widget pill(String t, bool go, VoidCallback f) => Expanded(
          child: GestureDetector(
            onTap: f,
            child: Container(
              height: 34,
              alignment: Alignment.center,
              decoration: BoxDecoration(
                color: go ? C.ink : null,
                borderRadius: BorderRadius.circular(17),
                border: go ? null : Border.all(color: C.ink, width: 1.5),
              ),
              child: Text(t, style: ui(12.5, w: FontWeight.w600, color: go ? C.paper : C.ink)),
            ),
          ),
        );

    return [
      Positioned.fill(child: GestureDetector(onTap: _close, child: Container(color: const Color(0x6B1E160E)))),
      Positioned.fromRect(
        rect: r,
        child: IgnorePointer(
          child: Transform.translate(
            offset: const Offset(0, -6),
            child: Transform.scale(scale: 1.04, child: _Edged(b, child: _Cover(b, w: r.width, h: r.height, lifted: true))),
          ),
        ),
      ),
      Positioned(
        left: left,
        top: top,
        width: 168,
        child: Container(
          padding: const EdgeInsets.all(6),
          decoration: BoxDecoration(
            color: C.paper,
            borderRadius: BorderRadius.circular(16),
            boxShadow: const [BoxShadow(color: Color(0x5928190A), blurRadius: 30, offset: Offset(0, 12))],
          ),
          child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            row(Icons.menu_book_outlined, '펼치기', () => act(widget.onOpen)),
            row(Icons.ios_share, '내보내기', () => act(widget.onExport)),
            row(Icons.edit_outlined, '이름 바꾸기', () => act(widget.onRename)),
            if (!_confirm)
              row(Icons.delete_outline, '지우기', () => setState(() => _confirm = true))
            else
              Container(
                margin: const EdgeInsets.only(top: 2),
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(color: C.warn, borderRadius: BorderRadius.circular(10)),
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text('이 책을 지울까요?', style: ui(13.5, w: FontWeight.w700)),
                  Padding(
                    padding: const EdgeInsets.only(top: 2, bottom: 9),
                    child: Text(kw('책만 지워요. 사진은 폰에 그대로 있어요. 되돌릴 수 없어요.'), style: ui(11, color: C.body, h: 1.4)),
                  ),
                  Row(children: [pill('취소', false, _close), const SizedBox(width: 6), pill('지우기', true, () => act(widget.onDelete))]),
                ]),
              ),
          ]),
        ),
      ),
    ];
  }
}


/// The cover face: the cached cover render, else a plain card with the title.
class _Cover extends StatelessWidget {
  final ShelfBook b;
  final double w, h;
  final bool lifted, shelf;
  const _Cover(this.b, {required this.w, required this.h, this.lifted = false, this.shelf = false});
  @override
  Widget build(BuildContext context) => Container(
        width: w,
        height: h,
        decoration: BoxDecoration(
          color: C.gridBg,
          boxShadow: [
            if (lifted)
              const BoxShadow(color: Color(0x8028190A), blurRadius: 26, offset: Offset(10, 16))
            else if (shelf) // the wall shadow is drawn behind the leaning book (_standing)
              const BoxShadow(color: Color(0x33281A0C), blurRadius: 1, offset: Offset(0, 1))
            else
              BoxShadow(color: const Color(0x47462D14), blurRadius: w > 100 ? 14 : 10, offset: w > 100 ? const Offset(6, 8) : const Offset(3, 4)),
          ],
        ),
        child: b.cover != null
            ? Image(image: sizedImage(context, b.cover!, w, h), fit: BoxFit.cover)
            : Padding(padding: const EdgeInsets.all(8), child: Text(b.title, style: ui(w > 100 ? 13 : 9, w: FontWeight.w700))),
      );
}

/// Page-edge strip on the right of a face-out book; thickness follows the page count (24 pages ~ 9px).
class _Edged extends StatelessWidget {
  final ShelfBook b;
  final Widget child;
  const _Edged(this.b, {required this.child});
  @override
  Widget build(BuildContext context) {
    final th = (b.pages * .36).roundToDouble().clamp(3.0, 16.0);
    return Stack(clipBehavior: Clip.none, children: [
      Positioned(
        right: -th,
        top: 2,
        bottom: 0,
        width: th,
        child: Transform(
          alignment: Alignment.topLeft,
          transform: Matrix4.skewY(-28 * math.pi / 180),
          child: const CustomPaint(painter: _Pages()),
        ),
      ),
      child,
    ]);
  }
}

class _Pages extends CustomPainter {
  const _Pages();
  @override
  void paint(Canvas c, Size s) {
    c.drawRect(Offset.zero & s, Paint()..color = const Color(0xFFF3EEE4));
    final p = Paint()..color = const Color(0xFFDCD4C6);
    for (var x = 1.0; x < s.width; x += 2) {
      c.drawRect(Rect.fromLTWH(x, 0, 1, s.height), p);
    }
  }

  @override
  bool shouldRepaint(_Pages old) => false;
}

/// "08.02 – 08.05 · 3박 4일", or "10.19 · 당일".
String _dates(ShelfBook b) {
  String md(DateTime d) => '${two(d.month)}.${two(d.day)}';
  final d = b.days;
  return d == 1 ? '${md(b.start)} · 당일' : '${md(b.start)} – ${md(b.end)} · ${d - 1}박 $d일';
}

/// 한 권씩: one big book in front; a sideways swipe brings the next, the neighbours tilt back and recede.
class _OneView extends StatefulWidget {
  final List<ShelfBook> books; // newest first
  final int initial;
  final ValueChanged<int> onPage;
  final Widget Function(ShelfBook b, double w, double h) book; // the tappable cover (opens, long press = menu)
  const _OneView({required this.books, required this.initial, required this.onPage, required this.book});
  @override
  State<_OneView> createState() => _OneViewState();
}

class _OneViewState extends State<_OneView> {
  late final _pc = PageController(initialPage: widget.initial, viewportFraction: .6);
  late int _i = widget.initial;

  @override
  void dispose() {
    _pc.dispose();
    super.dispose();
  }

  void _go(int i) => _pc.animateToPage(i, duration: const Duration(milliseconds: 420), curve: _ease);

  @override
  Widget build(BuildContext context) => LayoutBuilder(builder: (context, c) {
        final books = widget.books;
        final w = math.min(210.0, (c.maxWidth * .54).roundToDouble()), h = (w * 4 / 3).roundToDouble();
        final b = books[_i];
        const ink2 = Color(0xFF48423A);
        return SingleChildScrollView(
          padding: const EdgeInsets.only(bottom: 110),
          child: Column(children: [
            SizedBox(
              height: h + 70,
              child: PageView.builder(
                controller: _pc,
                itemCount: books.length,
                onPageChanged: (i) {
                  setState(() => _i = i);
                  widget.onPage(i);
                },
                itemBuilder: (context, i) => AnimatedBuilder(
                  animation: _pc,
                  builder: (context, child) {
                    final p = _pc.position.hasContentDimensions ? _pc.page ?? _i.toDouble() : _i.toDouble();
                    final d = i - p, a = math.min(1.4, d.abs());
                    return Opacity(
                      opacity: 1 - .3 * a,
                      child: Transform(
                        alignment: Alignment.center,
                        transform: Matrix4.identity()
                          ..setEntry(3, 2, 1 / 900)
                          ..rotateY(-d * 24 * math.pi / 180),
                        child: Transform.scale(scale: 1 - .16 * a, child: child),
                      ),
                    );
                  },
                  child: Padding(
                    padding: const EdgeInsets.only(top: 30, bottom: 40),
                    child: Align(
                      alignment: Alignment.topCenter,
                      // the front book opens; a neighbour comes to the front first
                      child: i == _i
                          ? widget.book(books[i], w, h)
                          : GestureDetector(onTap: () => _go(i), child: AbsorbPointer(child: widget.book(books[i], w, h))),
                    ),
                  ),
                ),
              ),
            ),
            AnimatedSwitcher(
              duration: const Duration(milliseconds: 220),
              child: Padding(
                key: ValueKey(b.id),
                padding: const EdgeInsets.symmetric(horizontal: 24),
                child: Column(children: [
                  Text('${b.start.year}', style: nm(12.5, w: FontWeight.w600, color: ink2, ls: .75)),
                  const SizedBox(height: 4),
                  Text(b.title,
                      textAlign: TextAlign.center,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: ui(28, w: FontWeight.w800, h: 1.2).copyWith(letterSpacing: -.9)),
                  const SizedBox(height: 6),
                  Text(_dates(b), style: nm(13.5, color: ink2)),
                  const SizedBox(height: 18),
                  Row(mainAxisAlignment: MainAxisAlignment.center, children: [
                    for (final (j, (v, u)) in [(b.pages, '쪽'), (b.photos, '사진'), (b.places, '곳'), if (b.km != null) (b.km!, 'km')].indexed)
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 16),
                        decoration: j == 0 ? null : const BoxDecoration(border: Border(left: BorderSide(color: Color(0x242B2621)))),
                        child: Column(children: [
                          Text('$v', style: nm(20, w: FontWeight.w600, ls: -.5)),
                          Text(u, style: ui(12, color: ink2)),
                        ]),
                      ),
                  ]),
                ]),
              ),
            ),
            const SizedBox(height: 30),
            _index(c.maxWidth),
          ]),
        );
      });

  /// Mini covers to jump straight to a book; a small gap where the year changes.
  Widget _index(double width) {
    final books = widget.books;
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: ConstrainedBox(
        constraints: BoxConstraints(minWidth: width - 32),
        child: SizedBox(
          height: 50,
          child: Row(mainAxisAlignment: MainAxisAlignment.center, crossAxisAlignment: CrossAxisAlignment.end, children: [
            for (final (i, x) in books.indexed) ...[
              if (i > 0) SizedBox(width: books[i - 1].start.year != x.start.year ? 16 : 7),
              Semantics(
                button: true,
                label: x.title,
                child: GestureDetector(
                  onTap: () => _go(i),
                  child: AnimatedSlide(
                    offset: Offset(0, i == _i ? -8 / 33 : 0),
                    duration: const Duration(milliseconds: 250),
                    curve: _ease,
                    child: AnimatedScale(
                      scale: i == _i ? 1.14 : 1,
                      duration: const Duration(milliseconds: 250),
                      curve: _ease,
                      child: AnimatedOpacity(
                        opacity: i == _i ? 1 : .5,
                        duration: const Duration(milliseconds: 250),
                        child: Container(
                          width: 25,
                          height: 33,
                          clipBehavior: Clip.antiAlias,
                          decoration: const BoxDecoration(
                            color: C.gridBg,
                            borderRadius: BorderRadius.horizontal(left: Radius.circular(2), right: Radius.circular(3)),
                            boxShadow: [BoxShadow(color: Color(0x80281A0C), blurRadius: 6, spreadRadius: -3, offset: Offset(0, 3))],
                          ),
                          child: x.cover == null ? null : Image(image: sizedImage(context, x.cover!, 25, 33), fit: BoxFit.cover),
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ],
          ]),
        ),
      ),
    );
  }
}

/// 한 권씩 background: the front book's cover, blurred into a faint wash that fades to paper at the bottom.
class _Glow extends StatelessWidget {
  final ShelfBook b;
  const _Glow(this.b, {super.key});
  @override
  Widget build(BuildContext context) => Stack(fit: StackFit.expand, children: [
        ClipRect(
          child: AnimatedSwitcher(
            duration: const Duration(milliseconds: 450),
            child: b.cover == null
                ? const SizedBox.expand()
                : Opacity(
                    key: ValueKey(b.id),
                    opacity: .75,
                    child: Transform.scale(
                      scale: 1.4,
                      child: ImageFiltered(
                        imageFilter: ImageFilter.blur(sigmaX: 44, sigmaY: 44, tileMode: TileMode.decal),
                        child: Image(image: sizedImage(context, b.cover!, 48, 48), fit: BoxFit.cover, width: double.infinity, height: double.infinity),
                      ),
                    ),
                  ),
          ),
        ),
        const DecoratedBox(
          decoration: BoxDecoration(
            gradient: LinearGradient(
              begin: Alignment.topCenter,
              end: Alignment.bottomCenter,
              colors: [Color(0x4DF4EFE7), Color(0x8CF4EFE7), Color(0xF0F4EFE7)],
              stops: [0, .45, .74],
            ),
          ),
        ),
      ]);
}

/// The mode button's glyph, drawn from the mockup's 16x16 SVGs (ico.shelf / ico.year / ico.one).
class _ModeIcon extends CustomPainter {
  final ShelfView v;
  const _ModeIcon(this.v);
  @override
  void paint(Canvas c, Size s) {
    c.scale(s.width / 16);
    final p = Paint()
      ..color = C.ink
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.5
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round;
    final fill = Paint()..color = C.ink;
    switch (v) {
      case ShelfView.display:
        c.drawRRect(RRect.fromLTRBR(2.5, 3, 7, 11, const Radius.circular(.6)), p);
        c.drawRRect(RRect.fromLTRBR(9, 3, 13.5, 11, const Radius.circular(.6)), p);
        c.drawLine(const Offset(1, 13.5), const Offset(15, 13.5), p);
      case ShelfView.year:
        c.drawLine(const Offset(3.5, 2), const Offset(3.5, 14), p);
        c.drawCircle(const Offset(3.5, 4.5), 1.4, fill);
        c.drawCircle(const Offset(3.5, 11), 1.4, fill);
        c.drawLine(const Offset(7, 4.5), const Offset(14, 4.5), p);
        c.drawLine(const Offset(7, 11), const Offset(12, 11), p);
      case ShelfView.one:
        c.drawRRect(RRect.fromLTRBR(4.5, 1.8, 11.5, 11.8, const Radius.circular(.8)), p);
        c.drawLine(const Offset(2, 14.2), const Offset(14, 14.2), p);
    }
  }

  @override
  bool shouldRepaint(_ModeIcon old) => old.v != v;
}

/// A thick light-oak floating shelf (mockups/r28-shelf): the top face seen from above (darker where it meets the
/// wall), a lit rounded front edge, a grained front with darker ends, and a deep soft shadow on the wall below.
class _Plank extends StatelessWidget {
  const _Plank();
  @override
  Widget build(BuildContext context) => Container(
        decoration: const BoxDecoration(
          borderRadius: BorderRadius.vertical(top: Radius.circular(3), bottom: Radius.circular(4)),
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [
              Color(0xFFC9AE88), Color(0xFFDCC5A3), Color(0xFFEBDBC2), Color(0xFFFBF3E6), Color(0xFFF0DFC5), //
              Color(0xFFDDC39F), Color(0xFFD1B189), Color(0xFFBD9566), Color(0xFFA9814F),
            ],
            stops: [0, 4 / 34, 13 / 34, 15 / 34, 16.5 / 34, 19 / 34, .45, .8, 1],
          ),
          boxShadow: [
            BoxShadow(color: Color(0x59462810), blurRadius: 1, offset: Offset(0, 1)),
            BoxShadow(color: Color(0x59462810), blurRadius: 6, spreadRadius: -2, offset: Offset(0, 3)),
            BoxShadow(color: Color(0x99462810), blurRadius: 34, spreadRadius: -20, offset: Offset(0, 34)),
          ],
        ),
        foregroundDecoration: const BoxDecoration(
          borderRadius: BorderRadius.vertical(top: Radius.circular(3), bottom: Radius.circular(4)),
          gradient: LinearGradient(
            colors: [Color(0x2E462810), Color(0x00462810), Color(0x00462810), Color(0x33462810)],
            stops: [0, .07, .93, 1],
          ),
        ),
        child: const Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          SizedBox(height: 15),
          ColoredBox(color: Color(0xD9FFFFFF), child: SizedBox(height: 1)), // the lit rounded front edge
          Expanded(child: CustomPaint(painter: _Grain())),
        ]),
      );
}

/// Fine long wood grain on the board's front: thin wavy lines, fixed seed so it never shimmers between frames.
class _Grain extends CustomPainter {
  const _Grain();
  @override
  void paint(Canvas c, Size s) {
    final r = math.Random(7);
    final p = Paint()
      ..style = PaintingStyle.stroke
      ..blendMode = BlendMode.multiply;
    for (var i = 0; i < 16; i++) {
      final y0 = r.nextDouble() * s.height, amp = .4 + r.nextDouble() * 1.4, k = 2 + r.nextDouble() * 5, ph = r.nextDouble() * 6;
      p
        ..color = Color.fromARGB(10 + r.nextInt(18), 110, 70, 34)
        ..strokeWidth = .3 + r.nextDouble() * .5;
      final path = Path()..moveTo(0, y0);
      for (var x = 0.0; x <= s.width; x += 6) {
        path.lineTo(x, y0 + amp * math.sin(ph + x / s.width * k * math.pi));
      }
      c.drawPath(path, p);
    }
  }

  @override
  bool shouldRepaint(_Grain old) => false;
}

/// Where the book sits on the board: a thin dark soft line.
class _Contact extends CustomPainter {
  const _Contact();
  @override
  void paint(Canvas c, Size s) => c.drawOval(
        Offset.zero & s,
        Paint()
          ..color = const Color(0x80281608)
          ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 2.5),
      );

  @override
  bool shouldRepaint(_Contact old) => false;
}

/// A leaning book's shadow on the wall: narrow at the top where the book touches, wider at the board; darkest at
/// the top edge, a little darker again near the board.
class _LeanShadow extends CustomPainter {
  const _LeanShadow();
  @override
  void paint(Canvas c, Size s) {
    final path = Path()
      ..moveTo(s.width * .07, 0)
      ..lineTo(s.width * .93, 0)
      ..lineTo(s.width, s.height)
      ..lineTo(0, s.height)
      ..close();
    c.drawPath(
      path,
      Paint()
        ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 5)
        ..shader = const LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [Color(0xA6341E0C), Color(0x73341E0C), Color(0x55341E0C), Color(0x73341E0C)],
          stops: [0, .07, .6, 1],
        ).createShader(Offset.zero & s),
    );
  }

  @override
  bool shouldRepaint(_LeanShadow old) => false;
}


class _Dashed extends CustomPainter {
  @override
  void paint(Canvas c, Size s) {
    final p = Paint()
      ..color = C.dash
      ..strokeWidth = 1.5;
    void line(Offset a, Offset b) {
      final len = (b - a).distance;
      final dir = (b - a) / len;
      for (var t = 0.0; t < len; t += 7) {
        c.drawLine(a + dir * t, a + dir * math.min(t + 4, len), p);
      }
    }

    line(Offset.zero, Offset(s.width, 0));
    line(Offset(s.width, 0), Offset(s.width, s.height));
    line(Offset(s.width, s.height), Offset(0, s.height));
    line(Offset(0, s.height), Offset.zero);
  }

  @override
  bool shouldRepaint(_Dashed old) => false;
}
