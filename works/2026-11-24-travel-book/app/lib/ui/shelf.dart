// D2 shelf home (mockup §2 #shelf): face-out books on oak shelves, a year timeline, the empty shelf,
// and the long-press menu with an inline delete confirm.
import 'dart:math' as math;

import 'package:material_ui/material_ui.dart';

import '../core/library.dart';
import 'tokens.dart';

enum ShelfView { display, timeline }

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

  @override
  Widget build(BuildContext context) {
    final books = widget.books;
    final photos = books.fold(0, (s, b) => s + b.photos);
    return Scaffold(
      body: SafeArea(
        child: Stack(key: _stackKey, children: [
          Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 10, 20, 0),
              child: Row(crossAxisAlignment: CrossAxisAlignment.end, children: [
                Expanded(
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Text('내 책장', style: ui(24, w: FontWeight.w700).copyWith(letterSpacing: -.5)),
                    const SizedBox(height: 3),
                    Text(books.isEmpty ? '아직 비어 있어요' : '${books.length}권 · 사진 ${fmtInt(photos)}장', style: nm(11, color: C.ink2)),
                  ]),
                ),
                GestureDetector(
                  onTap: widget.onSettings,
                  child: const SizedBox(width: 30, height: 30, child: Icon(Icons.settings_outlined, size: 18, color: C.ink2)),
                ),
                const SizedBox(width: 8),
                _mode(),
              ]),
            ),
            Expanded(
              child: books.isEmpty
                  ? (widget.view == ShelfView.display ? _empty() : _emptyTimeline())
                  : widget.view == ShelfView.display
                      ? _display()
                      : _timeline(),
            ),
          ]),
          Positioned(right: 20, bottom: 24, child: _fab(books.isEmpty ? '첫 책 만들기' : '새 책')),
          if (_menu != null) ..._overlay(),
        ]),
      ),
    );
  }

  Widget _mode() => Container(
        padding: const EdgeInsets.all(3),
        decoration: BoxDecoration(color: C.tint, borderRadius: BorderRadius.circular(16)),
        child: Row(children: [
          for (final (v, l) in [(ShelfView.display, '진열'), (ShelfView.timeline, '연표')])
            GestureDetector(
              onTap: () => widget.onViewChanged(v),
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                decoration: widget.view == v
                    ? BoxDecoration(
                        color: C.paper,
                        borderRadius: BorderRadius.circular(13),
                        boxShadow: const [BoxShadow(color: Color(0x1A000000), blurRadius: 3, offset: Offset(0, 1))],
                      )
                    : null,
                child: Text(l, style: ui(11.5, w: FontWeight.w600, color: widget.view == v ? C.ink : C.ink2)),
              ),
            ),
        ]),
      );

  Widget _fab(String label) => GestureDetector(
        onTap: widget.onNew,
        child: Container(
          height: 52,
          padding: const EdgeInsets.fromLTRB(16, 0, 20, 0),
          decoration: BoxDecoration(
            color: C.ink,
            borderRadius: BorderRadius.circular(26),
            boxShadow: const [BoxShadow(color: Color(0x4D28190A), blurRadius: 18, offset: Offset(0, 8))],
          ),
          child: Row(mainAxisSize: MainAxisSize.min, children: [
            Text('+', style: ui(22, color: C.paper, h: 1)),
            const SizedBox(width: 6),
            Text(label, style: ui(14, w: FontWeight.w600, color: C.paper)),
          ]),
        ),
      );

  String _short(DateTime d) {
    final now = widget.now ?? DateTime.now();
    final md = '${two(d.month)}.${two(d.day)}';
    return d.year == now.year ? md : '${two(d.year % 100)}.$md';
  }

  Widget _book(ShelfBook b, {double w = 112, double h = 150}) => Builder(
        builder: (ctx) => GestureDetector(
          onTap: () => widget.onOpen(b),
          onLongPress: () => _longPress(b, ctx),
          child: _Cover(b, w: w, h: h),
        ),
      );

  // ----- 진열 -----
  Widget _wall(List<Widget> children) => DecoratedBox(
        decoration: const BoxDecoration(
          gradient: LinearGradient(begin: Alignment.topCenter, end: Alignment.bottomCenter, colors: [C.paper, C.wallBottom]),
        ),
        child: ListView(padding: const EdgeInsets.only(bottom: 110), children: children),
      );

  Widget _shelfRow(double height, List<Widget> books) => SizedBox(
        height: height,
        child: Stack(clipBehavior: Clip.none, children: [
          Positioned(
            left: 22,
            right: 0,
            bottom: 12,
            child: Row(crossAxisAlignment: CrossAxisAlignment.end, children: [
              for (final (i, b) in books.indexed) ...[if (i > 0) const SizedBox(width: 26), b],
            ]),
          ),
          const Positioned(left: 0, right: 0, bottom: 0, height: 12, child: _Board()),
        ]),
      );

  Widget _display() {
    final b = widget.books;
    return _wall([
      for (var i = 0; i < b.length; i += 2) ...[
        _shelfRow(i == 0 ? 214 : 198, [
          for (final x in b.skip(i).take(2)) _Edged(x, child: _book(x)),
        ]),
        Padding(
          padding: const EdgeInsets.fromLTRB(22, 8, 22, 0),
          child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
            for (final (j, x) in b.skip(i).take(2).indexed) ...[
              if (j > 0) const SizedBox(width: 26),
              SizedBox(
                width: 112,
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text(x.title, style: ui(12, w: FontWeight.w700), maxLines: 1, overflow: TextOverflow.ellipsis),
                  Text('${_short(x.start)} · ${x.pages}쪽', style: nm(11, color: C.ink2)),
                ]),
              ),
            ],
          ]),
        ),
      ],
    ]);
  }

  Widget _empty() => _wall([
        _shelfRow(214, [
          SizedBox(
            width: 112,
            height: 150,
            child: CustomPaint(
              painter: _Dashed(),
              child: Center(
                child: Text('첫 책이\n여기 놓여요', textAlign: TextAlign.center, style: ui(12, color: C.ink2, h: 1.5)),
              ),
            ),
          ),
        ]),
        _shelfRow(198, const []),
        Padding(
          padding: const EdgeInsets.fromLTRB(22, 22, 22, 0),
          child: Text('최근 여행 날짜를 골라 보세요.\n처음 2권은 무료예요.', style: ui(13, color: C.body, h: 1.6)),
        ),
      ]);

  // ----- 연표 -----
  /// The empty timeline: this year's heading and a dashed place on the line where the first trip will sit.
  Widget _emptyTimeline() => ListView(padding: const EdgeInsets.only(bottom: 110), children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(20, 12, 20, 0),
          child: Row(crossAxisAlignment: CrossAxisAlignment.baseline, textBaseline: TextBaseline.alphabetic, children: [
            Text('${(widget.now ?? DateTime.now()).year}', style: nm(40, w: FontWeight.w300, h: 1, ls: -2, color: C.ink2)),
            const SizedBox(width: 10),
            Text('0권', style: nm(11, color: C.ink2)),
          ]),
        ),
        Stack(children: [
          Positioned(left: 26, top: 0, bottom: 0, width: 1, child: Container(color: C.line)),
          Padding(
            padding: const EdgeInsets.fromLTRB(40, 10, 20, 0),
            child: Stack(clipBehavior: Clip.none, children: [
              Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                SizedBox(width: 76, height: 101, child: CustomPaint(painter: _Dashed())),
                const SizedBox(width: 12),
                Expanded(
                  child: Padding(
                    padding: const EdgeInsets.only(top: 4),
                    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      Text('여행 날짜 · 몇 박', style: nm(10.5, color: C.ink2)),
                      const SizedBox(height: 2),
                      Text('첫 여행이 여기 놓여요', style: ui(15, w: FontWeight.w700, color: C.ink2)),
                    ]),
                  ),
                ),
              ]),
              Positioned(
                left: -17,
                top: 10,
                child: Container(
                    width: 7, height: 7, decoration: BoxDecoration(shape: BoxShape.circle, border: Border.all(color: C.ink2, width: 1.2))),
              ),
            ]),
          ),
        ]),
        Padding(
          padding: const EdgeInsets.fromLTRB(22, 26, 22, 0),
          child: Text('다녀온 순서대로 해마다 쌓여요.\n처음 2권은 무료예요.', style: ui(13, color: C.body, h: 1.6)),
        ),
      ]);

  Widget _timeline() => ListView(padding: const EdgeInsets.only(top: 0, bottom: 110), children: [
        for (final (year, books) in groupByYear(widget.books)) ...[
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 12, 20, 0),
            child: Row(crossAxisAlignment: CrossAxisAlignment.baseline, textBaseline: TextBaseline.alphabetic, children: [
              Text('$year', style: nm(40, w: FontWeight.w300, h: 1, ls: -2)),
              const SizedBox(width: 10),
              Builder(builder: (_) {
                final t = yearTotals(books);
                return Text('${t.books}권 · ${t.places}곳 · ${t.days}일', style: nm(11, color: C.ink2));
              }),
            ]),
          ),
          Stack(children: [
            Positioned(left: 26, top: 0, bottom: 0, width: 1, child: Container(color: C.line)),
            Padding(
              padding: const EdgeInsets.fromLTRB(40, 10, 20, 0),
              child: Column(children: [for (final b in books) _trip(b)]),
            ),
          ]),
        ],
      ]);

  Widget _trip(ShelfBook b) {
    final d = b.days;
    final span = d == 1 ? '당일' : '${d - 1}박 $d일';
    Widget n(int v, String u) => Text.rich(TextSpan(children: [
          TextSpan(text: '$v', style: nm(10.5, w: FontWeight.w700)),
          TextSpan(text: u),
        ]), style: nm(10.5, color: C.ink2));
    // the whole row opens the book (operator 11-27: tapping the title did nothing); long press = the book menu
    return Builder(
      builder: (ctx) => GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: () => widget.onOpen(b),
        onLongPress: () => _longPress(b, ctx),
        child: Padding(
      padding: const EdgeInsets.only(bottom: 14),
      child: Stack(clipBehavior: Clip.none, children: [
        Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
          _book(b, w: 76, h: 101),
          const SizedBox(width: 12),
          Expanded(
            child: SizedBox(
              height: 101,
              child: Padding(
                padding: const EdgeInsets.only(top: 4, bottom: 2),
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
                  Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Text('${two(b.start.month)}.${two(b.start.day)} — ${two(b.end.month)}.${two(b.end.day)} · $span',
                        style: nm(10.5, color: C.ink2)),
                    const SizedBox(height: 2),
                    Text(b.title, style: ui(15, w: FontWeight.w700), maxLines: 2, overflow: TextOverflow.ellipsis),
                  ]),
                  Row(children: [
                    n(b.places, '곳'),
                    const SizedBox(width: 10),
                    n(b.photos, '장'),
                    if (b.km != null) ...[const SizedBox(width: 10), n(b.km!, 'km')],
                  ]),
                ]),
              ),
            ),
          ),
        ]),
        Positioned(
          left: -17,
          top: 10,
          child: Container(width: 7, height: 7, decoration: const BoxDecoration(color: C.ink, shape: BoxShape.circle)),
        ),
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
  final bool lifted;
  const _Cover(this.b, {required this.w, required this.h, this.lifted = false});
  @override
  Widget build(BuildContext context) => Container(
        width: w,
        height: h,
        decoration: BoxDecoration(
          color: C.gridBg,
          boxShadow: [
            lifted
                ? const BoxShadow(color: Color(0x8028190A), blurRadius: 26, offset: Offset(10, 16))
                : BoxShadow(color: const Color(0x47462D14), blurRadius: w > 100 ? 14 : 10, offset: w > 100 ? const Offset(6, 8) : const Offset(3, 4)),
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

class _Board extends StatelessWidget {
  const _Board();
  @override
  Widget build(BuildContext context) => Container(
        decoration: const BoxDecoration(
          gradient: LinearGradient(begin: Alignment.topCenter, end: Alignment.bottomCenter, colors: [C.oak1, C.oak2]),
          boxShadow: [BoxShadow(color: Color(0x40462810), blurRadius: 8, offset: Offset(0, 4))],
        ),
      );
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
