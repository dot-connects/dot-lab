// D4 2/2 책을 만들고 있어요 (operator 11-27 + Fable review: the old four steps were on a timer). Everything here is real:
// the cover the book will have (its cover photo, the place, the dates), the day lines /pick already wrote typed in one
// by one, the step the pipeline is actually on with the time it has taken, and the picked photos passing by.
import 'dart:async';

import 'package:flutter/foundation.dart' show ValueListenable;
import 'package:material_ui/material_ui.dart';

import '../../pipeline.dart' show WriteStage;
import '../tokens.dart';
import '../widgets.dart';
import 'choose_theme.dart';

class WritingScreen extends StatefulWidget {
  final List<String> photos; // picked photo paths, in time order
  final String? cover; // the cover photo path
  final String period; // "6.12 – 6.14"
  final List<(DateTime, String)> lines; // /pick's line per day
  final ValueListenable<(WriteStage, int, int)?> stage;
  final String theme; // id
  final String title; // the place on the cover page
  final String line; // "여백의 책 · 보통 24쪽 · 2~3분"
  final int picked;
  final bool words; // false = 사진만
  final VoidCallback onLeave; // back to the shelf; the book keeps being made
  const WritingScreen({
    super.key,
    required this.photos,
    this.cover,
    this.period = '',
    this.lines = const [],
    required this.stage,
    required this.theme,
    required this.title,
    required this.line,
    required this.picked,
    this.words = true,
    required this.onLeave,
  });
  @override
  State<WritingScreen> createState() => _WritingScreenState();
}

class _WritingScreenState extends State<WritingScreen> with SingleTickerProviderStateMixin {
  late final Timer _t;
  final _clock = Stopwatch()..start();
  int _shown = 0; // day lines typed so far
  late final _breath = AnimationController(vsync: this, duration: const Duration(milliseconds: 2600));

  @override
  void initState() {
    super.initState();
    _t = Timer.periodic(const Duration(seconds: 1), (_) {
      if (!mounted) return;
      setState(() {
        if (_clock.elapsed.inSeconds % 2 == 0 && _shown < widget.lines.length) _shown++;
      });
    });
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (MediaQuery.disableAnimationsOf(context)) {
      _breath.stop();
      _shown = widget.lines.length;
    } else if (!_breath.isAnimating) {
      _breath.repeat(reverse: true);
    }
  }

  @override
  void dispose() {
    _t.cancel();
    _breath.dispose();
    super.dispose();
  }

  String _status((WriteStage, int, int)? s) {
    final e = _clock.elapsed, mmss = '${e.inMinutes}:${two(e.inSeconds % 60)}';
    return switch (s) {
      null => '고른 사진 ${fmtInt(widget.picked)}장을 책으로 옮길 준비 중',
      (WriteStage.previews, final d, final t) => 'AI 편집자에게 보낼 사진 정리 중 · $d / $t',
      (WriteStage.editor, _, _) => '${widget.words ? 'AI 편집자가 쪽을 짜고 글을 쓰는 중' : 'AI 편집자가 쪽을 짜는 중'} · $mmss',
      (WriteStage.fixing, _, _) => '어색한 쪽을 한 번 더 다듬는 중 · $mmss',
      (WriteStage.files, _, final t) => '사진 $t장을 책에 붙이는 중',
    };
  }

  @override
  Widget build(BuildContext context) {
    final still = MediaQuery.disableAnimationsOf(context);
    final w = widget;
    return NbScaffold(
      back: '✕',
      onBack: w.onLeave,
      right: topLabel('2 / 2'),
      title: '책을 만들고 있어요',
      lead: w.line,
      body: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Center(child: _coverCard(still)),
        const SizedBox(height: 22),
        if (w.lines.isNotEmpty) ...[
          const OptLabel('날마다 한 줄', top: 0),
          for (final (i, (day, text)) in w.lines.indexed)
            AnimatedOpacity(
              opacity: i < _shown ? 1 : 0,
              duration: still ? Duration.zero : const Duration(milliseconds: 500),
              child: Padding(
                padding: const EdgeInsets.only(bottom: 7),
                child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  SizedBox(width: 62, child: Text('${day.month}월 ${day.day}일', style: nm(12.5, color: C.ink2))),
                  Expanded(child: _Typed(text, on: i < _shown, still: still)),
                ]),
              ),
            ),
          const SizedBox(height: 14),
        ],
        ValueListenableBuilder<(WriteStage, int, int)?>(
          valueListenable: w.stage,
          builder: (_, s, _) {
            final known = s != null && s.$3 > 0;
            return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
              Text(_status(s), style: ui(13, w: FontWeight.w600)),
              const SizedBox(height: 8),
              ClipRRect(
                borderRadius: BorderRadius.circular(2),
                child: LinearProgressIndicator(
                  minHeight: 3,
                  color: C.ink,
                  backgroundColor: C.line,
                  value: still ? .5 : (known ? s.$2 / s.$3 : null),
                ),
              ),
            ]);
          },
        ),
        const SizedBox(height: 18),
        if (w.photos.isNotEmpty) _Film(photos: w.photos, still: still),
      ]),
      foot: [Btn('나가 있기 · 다 되면 알려 드릴게요', ghost: true, onTap: w.onLeave)],
    );
  }

  /// The cover as it will be: the cover photo, the place and the dates; it breathes a little while the book is made.
  Widget _coverCard(bool still) {
    final w = widget, photo = w.cover ?? (w.photos.isEmpty ? null : w.photos.first);
    const cw = 168.0, ch = 224.0;
    return AnimatedBuilder(
      animation: _breath,
      builder: (_, child) => Transform.scale(scale: 1 + .012 * _breath.value, child: child),
      child: Container(
        width: cw,
        height: ch,
        decoration: BoxDecoration(
          color: themePaper[w.theme] ?? C.paper,
          borderRadius: const BorderRadius.horizontal(left: Radius.circular(3), right: Radius.circular(6)),
          boxShadow: const [BoxShadow(color: Color(0x40281A0A), blurRadius: 22, offset: Offset(0, 10))],
        ),
        clipBehavior: Clip.antiAlias,
        child: Stack(fit: StackFit.expand, children: [
          if (photo != null) Photo(photo, w: cw, h: ch),
          const DecoratedBox(
            decoration: BoxDecoration(
              gradient: LinearGradient(begin: Alignment.topCenter, end: Alignment.bottomCenter, stops: [.45, 1], colors: [Color(0x00000000), Color(0x8C000000)]),
            ),
          ),
          // the spine's fold
          const Positioned(
            left: 0,
            top: 0,
            bottom: 0,
            width: 10,
            child: DecoratedBox(decoration: BoxDecoration(gradient: LinearGradient(colors: [Color(0x40000000), Color(0x00000000)]))),
          ),
          Positioned(
            left: 16,
            right: 12,
            bottom: 14,
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, mainAxisSize: MainAxisSize.min, children: [
              Text(w.title, maxLines: 2, overflow: TextOverflow.ellipsis, style: ui(19, w: FontWeight.w800, color: Colors.white, h: 1.15)),
              if (w.period.isNotEmpty) Text(w.period, style: nm(11, color: const Color(0xE6FFFFFF))),
            ]),
          ),
        ]),
      ),
    );
  }
}

/// A day line typing itself out once it is shown.
class _Typed extends StatelessWidget {
  final String text;
  final bool on, still;
  const _Typed(this.text, {required this.on, required this.still});
  @override
  Widget build(BuildContext context) => TweenAnimationBuilder<double>(
        tween: Tween(end: on ? 1 : 0),
        duration: still ? Duration.zero : Duration(milliseconds: 60 * text.length),
        builder: (_, v, _) => Text(text.substring(0, (text.length * v).round()), style: ui(13, color: C.ink, h: 1.4)),
      );
}

/// The picked photos passing slowly under the cover: what goes into the book.
class _Film extends StatefulWidget {
  final List<String> photos;
  final bool still;
  const _Film({required this.photos, required this.still});
  @override
  State<_Film> createState() => _FilmState();
}

class _FilmState extends State<_Film> with SingleTickerProviderStateMixin {
  late final _c = AnimationController(vsync: this, duration: Duration(seconds: 4 * widget.photos.length.clamp(6, 40)));
  @override
  void initState() {
    super.initState();
    if (!widget.still) _c.repeat();
  }

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    const s = 54.0, gap = 6.0;
    final n = widget.photos.length, run = n * (s + gap);
    return SizedBox(
      height: s,
      child: ClipRect(
        child: AnimatedBuilder(
          animation: _c,
          builder: (_, _) => Stack(clipBehavior: Clip.none, children: [
            for (var k = 0; k < 2; k++)
              for (final (i, p) in widget.photos.indexed)
                Positioned(left: k * run + i * (s + gap) - _c.value * run, top: 0, child: Photo(p, w: s, h: s, r: 4)),
          ]),
        ),
      ),
    );
  }
}
