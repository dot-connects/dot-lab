// D4 1/2 사진을 고르고 있어요 (AI call 1), mockup r12 B1: one big count eases down stage by stage
// (this period -> chosen albums -> blur/dark -> similar -> captures -> sent -> picked) while a 48-tile grid shows
// the same shares going: blurred, merged away, vanished, and an ink outline on the kept ones at the end.
import 'dart:async';
import 'dart:ui' show ImageFilter;

import 'package:material_ui/material_ui.dart';

import '../tokens.dart';
import '../widgets.dart';

class PickingScreen extends StatefulWidget {
  final List<(String path, bool kept)> thumbs; // a strip of the trip's photos (repeated to fill the grid)
  final int inRange, albums, albumPhotos;
  final ({int blurDark, int similar, int capture}) filtered;
  final int sent; // previews sent to the AI; 0 until the phone has filtered
  final int? picked; // null while the AI has not answered
  final int must;
  final VoidCallback onClose;
  const PickingScreen({
    super.key,
    required this.thumbs,
    required this.inRange,
    required this.albums,
    required this.albumPhotos,
    required this.filtered,
    required this.sent,
    this.picked,
    this.must = 0,
    required this.onClose,
  });
  @override
  State<PickingScreen> createState() => _PickingScreenState();
}

enum _T { live, blur, dup, cap, kept }

const _tiles = 48, _cols = 8;

class _PickingScreenState extends State<PickingScreen> {
  int _stage = 0;
  late final Timer _t;

  /// The last stage the numbers so far can show: 1 = albums, 5 = sent to the AI, 6 = picked.
  int get _ready => widget.picked != null ? 6 : (widget.sent > 0 ? 5 : 1);

  @override
  void initState() {
    super.initState();
    _t = Timer.periodic(const Duration(milliseconds: 1300), (_) {
      if (_stage < _ready) setState(() => _stage++);
    });
  }

  @override
  void didUpdateWidget(PickingScreen old) {
    super.didUpdateWidget(old);
    if (widget.picked != null && _stage == 5) _stage = 6; // the host shows the result only briefly: no wait for the tick
  }

  @override
  void dispose() {
    _t.cancel();
    super.dispose();
  }

  List<int> get _counts {
    final w = widget, f = w.filtered;
    final a = w.albumPhotos, x1 = a - f.blurDark, x2 = x1 - f.similar, x3 = x2 - f.capture;
    return [w.inRange, a, x1, x2, x3, w.sent, w.picked ?? w.sent].map((n) => n < 0 ? 0 : n).toList();
  }

  String _line(int s) {
    final w = widget;
    return switch (s) {
      0 => '이 기간에 찍은 사진 ${fmtInt(w.inRange)}장',
      1 => '고른 앨범 ${w.albums}개 · ${fmtInt(w.albumPhotos)}장',
      2 => '흔들리거나 어두운 사진 거르는 중',
      3 => '비슷한 사진은 한 장만',
      4 => '화면 캡처 빼기',
      5 => 'AI가 잘 나온 사진 고르는 중',
      _ => '${fmtInt(w.picked ?? 0)}장 골랐어요${w.must > 0 ? ' · 꼭 넣을 사진 ${w.must}장 포함' : ''}',
    };
  }

  /// Tile states at stage [s]: each filter takes its share of the 48 tiles, in a scattered order.
  List<_T> _states(int s) {
    final w = widget, f = w.filtered, base = w.albumPhotos > 0 ? w.albumPhotos : 1;
    int share(int n) => (_tiles * n / base).round().clamp(0, _tiles);
    final nb = share(f.blurDark), ns = share(f.similar), nc = share(f.capture);
    final live = (_tiles - nb - ns - nc).clamp(0, _tiles);
    final sent = share(w.sent).clamp(0, live), kept = share(w.picked ?? 0).clamp(0, sent);
    _T at(int i) {
      final q = (i * 29) % _tiles; // 29 is prime to 48: a scattered, stable order
      if (q < nb) return s >= 2 ? _T.blur : _T.live;
      if (q < nb + ns) return s >= 3 ? _T.dup : _T.live;
      if (q < nb + ns + nc) return s >= 4 ? _T.cap : _T.live;
      final r = q - nb - ns - nc; // the photos left for the AI
      if (s >= 5 && r >= sent) return _T.dup; // over the AI's image budget
      if (s >= 6) return r < kept ? _T.kept : _T.dup;
      return _T.live;
    }

    return [for (var i = 0; i < _tiles; i++) at(i)];
  }

  @override
  Widget build(BuildContext context) {
    final still = MediaQuery.disableAnimationsOf(context);
    final s = still ? _ready : _stage;
    Duration d(int ms) => still ? Duration.zero : Duration(milliseconds: ms);
    final states = _states(s), th = widget.thumbs;
    return NbScaffold(
      back: '✕',
      onBack: widget.onClose,
      right: topLabel('1 / 2'),
      title: '사진을 고르고 있어요',
      body: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        TweenAnimationBuilder<double>(
          tween: Tween(end: _counts[s].toDouble()),
          duration: d(1000),
          curve: Curves.easeOutCubic,
          builder: (_, v, _) => Text(fmtInt(v.round()), style: nm(64, w: FontWeight.w300, h: 1, ls: -2.5)),
        ),
        const SizedBox(height: 6),
        SizedBox(
          height: 20,
          child: AnimatedSwitcher(
            duration: d(300),
            layoutBuilder: (c, prev) => Stack(alignment: Alignment.centerLeft, children: [...prev, ?c]),
            child: Text(_line(s),
                key: ValueKey(s), style: ui(13, color: s == 6 ? C.ink : C.body, w: s == 6 ? FontWeight.w700 : FontWeight.w400)),
          ),
        ),
        const SizedBox(height: 14),
        GridView.count(
          crossAxisCount: _cols,
          mainAxisSpacing: 3,
          crossAxisSpacing: 3,
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          children: [for (var i = 0; i < _tiles; i++) _tile(th.isEmpty ? null : th[i % th.length].$1, states[i], d(600))],
        ),
        Priv(widget.sent > 0
            ? '작게 줄인 미리보기 ${widget.sent}장만 AI로 보내요. 원본은 폰에 그대로 있고 어디에도 저장하지 않아요.'
            : '작게 줄인 미리보기만 AI로 보내요. 원본은 폰에 그대로 있고 어디에도 저장하지 않아요.'),
      ]),
    );
  }

  Widget _tile(String? path, _T st, Duration d) {
    Widget img = path == null ? const ColoredBox(color: C.gridBg) : Photo(path, w: 40, h: 40, r: 2);
    if (st == _T.blur) {
      img = ImageFiltered(
        imageFilter: ImageFilter.blur(sigmaX: 1.6, sigmaY: 1.6),
        child: ColorFiltered(colorFilter: const ColorFilter.matrix(_dim), child: img),
      );
    }
    return AnimatedOpacity(
      opacity: switch (st) { _T.blur => .35, _T.dup => .22, _T.cap => 0, _ => 1 },
      duration: d,
      child: AnimatedSlide(
        offset: st == _T.dup ? const Offset(-.6, 0) : Offset.zero, // slides into its neighbour
        duration: d,
        curve: Curves.easeInOut,
        child: AnimatedScale(
          scale: switch (st) { _T.blur => .8, _T.dup => .6, _T.cap => .4, _ => 1 },
          duration: d,
          curve: Curves.easeInOut,
          child: AnimatedContainer(
            duration: d,
            foregroundDecoration: BoxDecoration(
              borderRadius: BorderRadius.circular(2),
              border: Border.all(color: st == _T.kept ? C.ink : const Color(0x002B2621), width: 2),
            ),
            child: ClipRRect(borderRadius: BorderRadius.circular(2), child: SizedBox.expand(child: img)),
          ),
        ),
      ),
    );
  }
}

const _dim = <double>[
  .55, 0, 0, 0, 0, //
  0, .55, 0, 0, 0, //
  0, 0, .55, 0, 0, //
  0, 0, 0, 1, 0,
];
