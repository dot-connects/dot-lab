// D4 2/2 책을 만들고 있어요 (AI call 2), mockup r12 C1 + a little more: four steps on a time estimate (about 2 min,
// the last one stays until the book is done), six pages of the chosen theme dropping in one by one with a lift and
// a settle, a shimmer over the page being made, then a slow reshuffle so the screen never looks frozen.
import 'dart:async';
import 'dart:math' as math;

import 'package:material_ui/material_ui.dart';

import '../tokens.dart';
import '../widgets.dart';
import 'choose_theme.dart';

const _tick = Duration(milliseconds: 2500);
const _stepAt = [0, 20, 50, 85]; // seconds: when each step starts (of about 120)
const _kinds = [0, 1, 2, 1, 2, 1];

class WritingScreen extends StatefulWidget {
  final List<String> photos; // picked photo paths, in time order
  final String theme; // id
  final String title; // the place on the cover page
  final String line; // "여백의 책 · 보통 24쪽 · 2~3분"
  final int picked;
  final bool words; // false = 사진만: no caption step
  final VoidCallback onLeave; // back to the shelf; the book keeps being made
  const WritingScreen({
    super.key,
    required this.photos,
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

class _WritingScreenState extends State<WritingScreen> {
  late final Timer _t;
  final _rnd = math.Random();
  int _ticks = 0, _in = 0, _making = 0;
  final _slot = [0, 1, 2, 3, 4, 5]; // tile i sits at _slot[i]

  @override
  void initState() {
    super.initState();
    _in = 1;
    _t = Timer.periodic(_tick, (_) => setState(_step));
  }

  void _step() {
    _ticks++;
    if (_in < _kinds.length) {
      _making = _in++;
      return;
    }
    // all six are down: swap two of them, and make the moved one "the one being made"
    final a = _rnd.nextInt(6), b = (a + 1 + _rnd.nextInt(5)) % 6;
    final x = _slot[a];
    _slot[a] = _slot[b];
    _slot[b] = x;
    _making = a;
  }

  @override
  void dispose() {
    _t.cancel();
    super.dispose();
  }

  int get _now {
    final sec = _ticks * _tick.inMilliseconds ~/ 1000;
    return _stepAt.lastIndexWhere((s) => sec >= s);
  }

  @override
  Widget build(BuildContext context) {
    final still = MediaQuery.disableAnimationsOf(context);
    final mq = MediaQuery.of(context);
    final steps = [
      '사진 ${fmtInt(widget.picked)}장을 날짜와 장면으로 나누는 중',
      '장면마다 쪽 모양 고르는 중',
      widget.words ? '캡션과 짧은 글 쓰는 중' : '사진 크기와 순서 맞추는 중',
      '빠진 곳·겹친 곳 검사하는 중',
    ];
    return NbScaffold(
      back: '✕',
      onBack: widget.onLeave,
      right: topLabel('2 / 2'),
      title: '책을 만들고 있어요',
      lead: widget.line,
      body: LayoutBuilder(builder: (context, box) {
        const gap = 8.0;
        final avail = mq.size.height - mq.padding.vertical - 350; // bar, title, steps, foot
        final tw = math.min((box.maxWidth - 2 * gap) / 3, (avail - gap) / 2 * .75);
        final th = tw * 4 / 3, left0 = (box.maxWidth - 3 * tw - 2 * gap) / 2;
        return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          SizedBox(
            height: th * 2 + gap + 6,
            child: Stack(clipBehavior: Clip.none, children: [
              for (var i = 0; i < _kinds.length; i++)
                AnimatedPositioned(
                  key: ValueKey(i),
                  duration: still ? Duration.zero : const Duration(milliseconds: 1100),
                  curve: Curves.easeInOutCubic,
                  left: left0 + (_slot[i] % 3) * (tw + gap),
                  top: (_slot[i] ~/ 3) * (th + gap),
                  child: _Drop(
                    on: i < _in,
                    still: still,
                    tilt: (i.isEven ? -1 : 1) * .012,
                    child: Stack(children: [
                      PagePreview(
                          theme: widget.theme, title: widget.title, kind: _kinds[i], photos: previewPhotos(widget.photos, widget.theme, i, _kinds[i]), w: tw),
                      if (i == _making && !still) Positioned.fill(child: _Shimmer(key: ValueKey('s$_ticks'))),
                    ]),
                  ),
                ),
            ]),
          ),
          const SizedBox(height: 12),
          for (final (k, s) in steps.indexed) _stepRow(s, k < _now ? StepMark.done : (k == _now ? StepMark.now : StepMark.wait), still),
        ]);
      }),
      foot: [Btn('나가 있기 · 다 되면 알려 드릴게요', ghost: true, onTap: widget.onLeave)],
    );
  }

  Widget _stepRow(String s, StepMark m, bool still) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: Row(children: [
          SizedBox(
            width: 14,
            height: 14,
            child: switch (m) {
              StepMark.done => Container(
                  decoration: const BoxDecoration(color: C.ink, shape: BoxShape.circle),
                  child: const Icon(Icons.check_rounded, size: 10, color: C.paper),
                ),
              StepMark.now => CircularProgressIndicator(strokeWidth: 1.6, color: C.ink, value: still ? .75 : null),
              StepMark.wait => Container(decoration: BoxDecoration(shape: BoxShape.circle, border: Border.all(color: C.line, width: 1.5))),
            },
          ),
          const SizedBox(width: 8),
          Expanded(
            child: Text(s,
                style: ui(12.5, h: 1.35, color: m == StepMark.wait ? C.ink2 : C.ink, w: m == StepMark.now ? FontWeight.w700 : FontWeight.w400)),
          ),
        ]),
      );
}

/// A page dropping onto the desk: falls in tilted, lifts its shadow on the way, overshoots a little and settles.
class _Drop extends StatelessWidget {
  final bool on, still;
  final double tilt; // resting angle (rad)
  final Widget child;
  const _Drop({required this.on, required this.still, required this.tilt, required this.child});
  @override
  Widget build(BuildContext context) => TweenAnimationBuilder<double>(
        tween: Tween(end: on ? 1 : 0),
        duration: still ? Duration.zero : const Duration(milliseconds: 900),
        builder: (_, t, c) {
          final back = 1 - Curves.easeOutBack.transform(t), lift = math.sin(math.pi * t.clamp(0, 1));
          return Opacity(
            opacity: (t * 2).clamp(0, 1),
            child: Transform.translate(
              offset: Offset(0, back * -26),
              child: Transform.rotate(
                angle: tilt + back * -.09,
                child: DecoratedBox(
                  decoration: BoxDecoration(boxShadow: [
                    BoxShadow(color: const Color(0x38281A0A), blurRadius: 6 + 16 * lift, offset: Offset(0, 2 + 8 * lift)),
                  ]),
                  child: c,
                ),
              ),
            ),
          );
        },
        child: child,
      );
}

/// A soft light sweeping across the page being made.
class _Shimmer extends StatefulWidget {
  const _Shimmer({super.key});
  @override
  State<_Shimmer> createState() => _ShimmerState();
}

class _ShimmerState extends State<_Shimmer> with SingleTickerProviderStateMixin {
  late final _c = AnimationController(vsync: this, duration: const Duration(milliseconds: 1400))..repeat();
  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => IgnorePointer(
        child: AnimatedBuilder(
          animation: _c,
          builder: (_, _) {
            final x = -2.2 + 4.4 * _c.value;
            return DecoratedBox(
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment(x - .7, -1),
                  end: Alignment(x + .7, 1),
                  colors: const [Color(0x00FFFFFF), Color(0x66FFFFFF), Color(0x00FFFFFF)],
                ),
              ),
            );
          },
        ),
      );
}
