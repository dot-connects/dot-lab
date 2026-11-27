// D4 1/2 사진을 고르고 있어요 — mockup r21 C "모자이크" (operator 11-27). Everything on it is real: the trip's own photos fill
// a mosaic as the phone looks at them (measured / toMeasure), the ones sent to the AI then gather into a grid under a
// light sweep while it chooses, and the picked ones stay, outlined. No timer steps ahead of the numbers.
import 'dart:async';
import 'dart:math' as math;

import 'package:material_ui/material_ui.dart';

import '../tokens.dart';
import '../widgets.dart';

class PickingScreen extends StatefulWidget {
  final List<String> sample; // photo paths spread over the trip, time order (the mosaic)
  final int inRange, albums, albumPhotos;
  final int measured, toMeasure; // the phone's own pass
  final int scoutTotal, scouted; // the AI's first, wide look
  final List<String> found; // what the picking found so far, newest last
  final ({int blurDark, int similar, int capture}) filtered;
  final List<String> sent; // paths sent to the AI, time order; empty until the phone has filtered
  final Set<String>? picked; // null while the AI has not answered
  final int must;
  final VoidCallback onClose;
  final VoidCallback? onLeave; // back to the shelf, the picking goes on
  const PickingScreen({
    this.onLeave,
    super.key,
    required this.sample,
    required this.inRange,
    required this.albums,
    required this.albumPhotos,
    this.measured = 0,
    this.toMeasure = 0,
    this.scoutTotal = 0,
    this.scouted = 0,
    this.found = const [],
    required this.filtered,
    this.sent = const [],
    this.picked,
    this.must = 0,
    required this.onClose,
  });
  @override
  State<PickingScreen> createState() => _PickingScreenState();
}

enum _Phase { looking, scouting, choosing, done }

class _PickingScreenState extends State<PickingScreen> with SingleTickerProviderStateMixin {
  late final _sweep = AnimationController(vsync: this, duration: const Duration(milliseconds: 1600));
  // the final look: a ring moving from finalist to finalist (where the AI's eye is, roughly), never standing still
  Timer? _hop;
  int _at = 0;
  final _rnd = math.Random(7);

  _Phase get _phase => widget.picked != null
      ? _Phase.done
      : widget.sent.isNotEmpty
          ? _Phase.choosing
          : (widget.scoutTotal > 0 ? _Phase.scouting : _Phase.looking);

  @override
  void didUpdateWidget(PickingScreen old) {
    super.didUpdateWidget(old);
    _syncSweep();
  }

  void _syncSweep() {
    final still = MediaQuery.disableAnimationsOf(context);
    final on = (_phase == _Phase.choosing || _phase == _Phase.scouting) && !still;
    if (on && !_sweep.isAnimating) _sweep.repeat();
    if (!on && _sweep.isAnimating) _sweep.stop();
    final hop = _phase == _Phase.choosing && !still;
    if (hop && _hop == null) {
      _hop = Timer.periodic(const Duration(milliseconds: 650), (_) {
        if (mounted && widget.sent.isNotEmpty) setState(() => _at = _rnd.nextInt(widget.sent.length));
      });
    } else if (!hop) {
      _hop?.cancel();
      _hop = null;
    }
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _syncSweep();
  }

  @override
  void dispose() {
    _hop?.cancel();
    _sweep.dispose();
    super.dispose();
  }

  int get _count => switch (_phase) {
        _Phase.looking => widget.measured, // counts up from 0: the number is the progress
        _Phase.scouting => widget.scouted,
        _Phase.choosing => widget.sent.length,
        _Phase.done => widget.picked!.length,
      };

  String get _line {
    final w = widget, f = w.filtered;
    return switch (_phase) {
      _Phase.looking when w.measured == 0 => '고른 앨범 ${w.albums}개 · ${fmtInt(w.albumPhotos)}장을 처음부터 살펴볼게요',
      _Phase.looking => '${fmtInt(w.toMeasure)}장 중 살펴본 사진',
      _Phase.scouting => '폰이 추린 ${fmtInt(w.scoutTotal)}장을 AI가 훑어보는 중\n흔들림·어두움 ${fmtInt(f.blurDark)} · 비슷한 사진 ${fmtInt(f.similar)} 뺐어요',
      _Phase.choosing => [
          if (f.blurDark > 0) '흔들림·어두움 ${fmtInt(f.blurDark)}',
          if (f.similar > 0) '비슷한 사진 ${fmtInt(f.similar)}',
          if (f.capture > 0) '캡처 ${fmtInt(f.capture)}',
        ].join(' · ').let((s) => s.isEmpty ? '후보 ${fmtInt(w.sent.length)}장에서 책에 넣을 사진 고르는 중' : '후보 ${fmtInt(w.sent.length)}장에서 책에 넣을 사진 고르는 중\n$s 뺐어요'),
      _Phase.done => '${fmtInt(w.picked!.length)}장 골랐어요${w.must > 0 ? ' · 꼭 넣을 사진 ${w.must}장 포함' : ''}',
    };
  }

  @override
  Widget build(BuildContext context) {
    final still = MediaQuery.disableAnimationsOf(context);
    Duration d(int ms) => still ? Duration.zero : Duration(milliseconds: ms);
    final w = widget, p = _phase;
    final share = w.toMeasure == 0 ? 0.0 : (w.measured / w.toMeasure).clamp(0.0, 1.0);
    return NbScaffold(
      back: '✕',
      onBack: w.onClose,
      right: topLabel('1 / 2'),
      title: '사진을 고르고 있어요',
      foot: [
        if (w.onLeave != null && p != _Phase.done) Btn('나가 있기 · 다 고르면 알려 드릴게요', ghost: true, onTap: w.onLeave),
      ],
      body: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        TweenAnimationBuilder<double>(
          tween: Tween(end: _count.toDouble()),
          duration: d(p == _Phase.looking ? 450 : 900),
          curve: Curves.easeOutCubic,
          builder: (_, v, _) => Text(fmtInt(v.round()), style: nm(64, w: FontWeight.w300, h: 1, ls: -2.5)),
        ),
        const SizedBox(height: 6),
        SizedBox(
          height: 38, // two lines: the AI step names what the phone already took out
          child: AnimatedSwitcher(
            duration: d(300),
            layoutBuilder: (c, prev) => Stack(alignment: Alignment.topLeft, children: [...prev, ?c]),
            child: Text(_line,
                key: ValueKey(p == _Phase.looking && w.measured > 0 ? 'looking' : (p == _Phase.scouting ? 'scouting' : _line)),
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: ui(13, color: p == _Phase.done ? C.ink : C.body, w: p == _Phase.done ? FontWeight.w700 : FontWeight.w400)),
          ),
        ),
        const SizedBox(height: 10),
        ClipRRect(
          borderRadius: BorderRadius.circular(2),
          child: SizedBox(
            height: 3,
            child: AnimatedFractionallySizedBox(
              duration: d(400),
              alignment: Alignment.centerLeft,
              widthFactor: switch (p) {
                _Phase.looking => share * .5,
                _Phase.scouting => .5 + .3 * (w.scoutTotal == 0 ? 0 : w.scouted / w.scoutTotal),
                _Phase.choosing => .9,
                _Phase.done => 1,
              },
              child: const ColoredBox(color: C.ink),
            ),
          ),
        ),
        const SizedBox(height: 14),
        LayoutBuilder(builder: (c, box) => _mosaic(box.maxWidth, share, d)),
        // what the picking found, as it finds it (real numbers only)
        for (final (i, line) in w.found.indexed)
          TweenAnimationBuilder<double>(
            key: ValueKey(line),
            tween: Tween(begin: 0, end: 1),
            duration: d(500),
            builder: (_, v, child) => Opacity(opacity: v, child: Transform.translate(offset: Offset(0, 6 * (1 - v)), child: child)),
            child: Padding(
              padding: EdgeInsets.only(top: i == 0 ? 12 : 4),
              child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Padding(padding: const EdgeInsets.only(top: 6, right: 8), child: Container(width: 5, height: 5, decoration: const BoxDecoration(color: C.oak2, shape: BoxShape.circle))),
                Expanded(child: Text(line, style: ui(13, color: C.ink, w: FontWeight.w600, h: 1.45))),
              ]),
            ),
          ),
        Priv(w.sent.isNotEmpty || w.scoutTotal > 0
            ? '작게 줄인 미리보기 ${fmtInt(math.max(w.scoutTotal, w.sent.length))}장만 AI로 보내요. 원본은 폰에 그대로 있고 어디에도 저장하지 않아요.'
            : '작게 줄인 미리보기만 AI로 보내요. 원본은 폰에 그대로 있고 어디에도 저장하지 않아요.'),
      ]),
    );
  }

  /// One tile per photo path: the sample fills the mosaic as it is looked at; then the sent ones form a 9-wide grid
  /// (the rest fade); then the picked ones a 6-wide grid, outlined.
  Widget _mosaic(double width, double share, Duration Function(int) d) {
    final w = widget, p = _phase, sentAt = {for (final (i, s) in w.sent.indexed) s: i};
    final pickedList = [for (final s in w.sent) if (w.picked?.contains(s) ?? false) s];
    final pickedAt = {for (final (i, s) in pickedList.indexed) s: i};
    final all = [...w.sample, for (final s in w.sent) if (!w.sample.contains(s)) s];
    const mc = 16;
    // the finalists' and the picks' grids get denser as they grow, so a whole book still fits
    final sc = w.sent.length <= 54 ? 9 : 12, pc = pickedList.length <= 30 ? 6 : 8;
    final ms = width / mc, shown = (w.sample.length * share).ceil();
    final tiles = <Widget>[];
    for (final (i, path) in all.indexed) {
      final inSample = i < w.sample.length;
      double x = 0, y = 0, s = ms - 1, o = 0;
      var ring = false;
      if (p == _Phase.looking || p == _Phase.scouting) {
        if (!inSample) continue;
        x = (i % mc) * ms;
        y = (i ~/ mc) * ms;
        o = p == _Phase.scouting || i < shown ? 1 : 0;
      } else {
        final cols = p == _Phase.done && pickedAt.containsKey(path) ? pc : sc, k = p == _Phase.done ? pickedAt[path] : sentAt[path];
        if (k != null) {
          final g = width / cols;
          x = (k % cols) * g;
          y = (k ~/ cols) * g;
          s = g - 3;
          o = 1;
          ring = p == _Phase.done;
        } else if (sentAt.containsKey(path)) {
          final g = width / sc, j = sentAt[path]!; // sent but not picked: fades where it stood
          x = (j % sc) * g;
          y = (j ~/ sc) * g;
          s = g - 3;
        } else if (inSample) {
          x = (i % mc) * ms;
          y = (i ~/ mc) * ms;
        }
      }
      tiles.add(AnimatedPositioned(
        key: ValueKey(path),
        duration: d(900),
        curve: Curves.easeOutCubic,
        left: x,
        top: y,
        width: s,
        height: s,
        child: AnimatedOpacity(
          opacity: o,
          duration: d(p == _Phase.looking ? 250 : 600),
          child: Container(
            foregroundDecoration: ring ? BoxDecoration(border: Border.all(color: C.ink, width: 2), borderRadius: BorderRadius.circular(3)) : null,
            child: Photo(path, w: s, h: s, r: p == _Phase.looking ? 1.5 : 3),
          ),
        ),
      ));
    }
    final rows = math.max((w.sample.length / mc).ceil() * ms, p == _Phase.done ? (pickedList.length / pc).ceil() * width / pc : (w.sent.length / sc).ceil() * width / sc);
    return SizedBox(
      height: math.min(rows, width * 1.15),
      child: ClipRect(
        child: Stack(children: [
          ...tiles,
          if (p == _Phase.choosing && w.sent.isNotEmpty)
            AnimatedPositioned(
              duration: const Duration(milliseconds: 420),
              curve: Curves.easeInOutCubic,
              left: (_at % sc) * width / sc - 3,
              top: (_at ~/ sc) * width / sc - 3,
              width: width / sc + 3,
              height: width / sc + 3,
              child: IgnorePointer(
                child: Container(
                  decoration: BoxDecoration(
                    border: Border.all(color: C.ink, width: 2.5),
                    borderRadius: BorderRadius.circular(5),
                    boxShadow: const [BoxShadow(color: Color(0x40281A0A), blurRadius: 8)],
                  ),
                ),
              ),
            ),
          if (p == _Phase.choosing || p == _Phase.scouting)
            Positioned.fill(
              child: IgnorePointer(
                child: AnimatedBuilder(
                  animation: _sweep,
                  builder: (_, _) => DecoratedBox(
                    decoration: BoxDecoration(
                      gradient: LinearGradient(
                        begin: Alignment(-3 + 5 * _sweep.value, -.3),
                        end: Alignment(-1 + 5 * _sweep.value, .3),
                        colors: const [Color(0x00FFFAF0), Color(0xA6FFFAF0), Color(0x00FFFAF0)],
                      ),
                    ),
                  ),
                ),
              ),
            ),
        ]),
      ),
    );
  }
}

extension on String {
  T let<T>(T Function(String) f) => f(this);
}
