// 어떻게 남길까요? (mockup exp B, operator 11-27): the pages in a strip (tap to leave one out), which pages, which format,
// one button. Pages arrive as they are rendered; a page still rendering shows paper.
import 'package:material_ui/material_ui.dart';

import 'tokens.dart';
import 'widgets.dart';

enum ExportKind { images, pdf }

enum PagePick { all, few, own }

/// Four pages that stand for the book: the cover, then three spread over the rest.
List<int> fewPages(int n) => n <= 4 ? [for (var i = 0; i < n; i++) i] : {0, n ~/ 3, 2 * n ~/ 3, n - 1}.toList();

class ExportScreen extends StatefulWidget {
  final String title;
  final List<String?> thumbs; // one per page; null = still rendering
  final VoidCallback onBack;
  /// Does the work for the chosen pages (in page order), calling [tick] with how many are done.
  final Future<void> Function(List<int> pages, ExportKind kind, ValueChanged<int> tick) onGo;
  final Future<void> Function(List<int> pages) onShare;
  const ExportScreen(
      {super.key, required this.title, required this.thumbs, required this.onBack, required this.onGo, required this.onShare});
  @override
  State<ExportScreen> createState() => _ExportScreenState();
}

class _ExportScreenState extends State<ExportScreen> {
  late final int n = widget.thumbs.length;
  late Set<int> _on = {for (var i = 0; i < n; i++) i};
  PagePick _pick = PagePick.all;
  ExportKind _kind = ExportKind.images;
  int? _done; // null = idle; else pages finished while running
  bool _finished = false;

  List<int> get _sel => _on.toList()..sort();

  void _changed(VoidCallback f) => setState(() {
        f();
        _finished = false;
      });

  void _setPick(PagePick p) => _changed(() {
        _pick = p;
        _on = switch (p) { PagePick.all => {for (var i = 0; i < n; i++) i}, PagePick.few => fewPages(n).toSet(), PagePick.own => {} };
      });

  void _toggle(int i) => _changed(() {
        _on.contains(i) ? _on.remove(i) : _on.add(i);
        _pick = _on.length == n ? PagePick.all : PagePick.own;
      });

  Future<void> _go() async {
    if (_done != null || _finished || _on.isEmpty) return;
    setState(() => _done = 0);
    try {
      await widget.onGo(_sel, _kind, (k) => mounted ? setState(() => _done = k) : null);
      if (mounted) setState(() => _finished = true);
    } catch (_) {
      // the host already told the person; the button is back to ready
    } finally {
      if (mounted) setState(() => _done = null);
    }
  }

  String get _label {
    final k = _on.length;
    if (_done != null) return _done == 0 ? '쪽 준비하는 중…' : '$_done / $k ${_kind == ExportKind.pdf ? '쪽 담는 중' : '저장 중'}';
    if (_finished) return _kind == ExportKind.pdf ? '✓ PDF를 만들었어요' : "✓ 갤러리 '여행 한 권'에 저장했어요";
    if (k == 0) return '남길 쪽을 골라 주세요';
    return _kind == ExportKind.pdf ? '$k쪽 PDF 만들기' : '$k장 갤러리에 저장';
  }

  String get _detail => _kind == ExportKind.pdf
      ? 'A5 너비(148mm)에 한 쪽씩 · 만든 뒤 저장할 곳을 골라요\n(파일, 드라이브, 카톡 등)'
      : "폰 화면 크기 JPG · 갤러리 '여행 한 권' 앨범\n영상 칸은 멈춘 한 장면으로 들어가요";

  Widget _thumb(int i) {
    final p = widget.thumbs[i], on = _on.contains(i);
    return GestureDetector(
      onTap: () => _toggle(i),
      child: SizedBox(
        width: 74,
        child: Stack(children: [
          AnimatedOpacity(
            duration: const Duration(milliseconds: 150),
            opacity: on ? 1 : .35,
            child: Container(
              width: 74,
              height: 99,
              decoration: const BoxDecoration(color: C.card, boxShadow: [BoxShadow(color: Color(0x29000000), blurRadius: 6, offset: Offset(0, 2))]),
              child: p == null
                  ? Center(child: Text('준비 중', style: ui(9.5, color: C.ink2)))
                  : Photo(p, w: 74, h: 99),
            ),
          ),
          Positioned(right: 4, top: 4, child: Ck(on ? CkState.on : CkState.off, size: 18, ring: C.ink2)),
          Positioned(left: 5, bottom: 3, child: Text('${i + 1}', style: nm(9.5, color: p == null ? C.ink2 : C.paper))),
        ]),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final busy = _done != null;
    return NbScaffold(
      onBack: widget.onBack,
      title: '어떻게 남길까요?',
      lead: '${widget.title} · $n쪽 · 누르면 빼거나 넣어요',
      body: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        SizedBox(
          height: 106,
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            clipBehavior: Clip.none,
            itemCount: n,
            separatorBuilder: (_, _) => const SizedBox(width: 7),
            itemBuilder: (_, i) => _thumb(i),
          ),
        ),
        OptLabel('고른 쪽', note: _on.length == n ? '$n쪽 모두' : '${_on.length}쪽'),
        Sg([const SgItem(PagePick.all, '모두'), const SgItem(PagePick.few, '대표 4쪽'), const SgItem(PagePick.own, '하나씩 고르기')], _pick,
            busy ? (_) {} : _setPick),
        const OptLabel('형식'),
        Sg([
          const SgItem(ExportKind.images, '이미지', sub: '갤러리'),
          const SgItem(ExportKind.pdf, 'PDF', sub: '인쇄용'),
        ], _kind, (k) {
          if (busy) return;
          _changed(() => _kind = k);
        }),
        Container(
          margin: const EdgeInsets.only(top: 10),
          padding: const EdgeInsets.fromLTRB(12, 10, 12, 10),
          decoration: BoxDecoration(color: C.card, borderRadius: BorderRadius.circular(12)),
          child: Text(_detail, style: ui(12, color: C.body, h: 1.55)),
        ),
      ]),
      foot: [
        Btn(_label, onTap: _on.isEmpty ? null : _go),
        Btn('공유하기', ghost: true, onTap: _on.isEmpty || busy ? null : () => widget.onShare(_sel)),
      ],
    );
  }
}
