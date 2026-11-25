// Small pieces shared by the app screens (mockup classes .btn .ck .sg .tg .nb .foot .fade).
import 'package:material_ui/material_ui.dart';

import 'tokens.dart';

/// .btn — 50 tall pill. [ghost] = outlined. Null [onTap] = dimmed.
class Btn extends StatelessWidget {
  final String label;
  final VoidCallback? onTap;
  final bool ghost;
  const Btn(this.label, {super.key, this.onTap, this.ghost = false});
  @override
  Widget build(BuildContext context) => Opacity(
        opacity: onTap == null ? .4 : 1,
        child: GestureDetector(
          onTap: onTap,
          child: Container(
            height: 50,
            alignment: Alignment.center,
            decoration: BoxDecoration(
              color: ghost ? Colors.transparent : C.ink,
              borderRadius: BorderRadius.circular(25),
              border: ghost ? Border.all(color: C.ink, width: 1.5) : null,
            ),
            child: Text(label, style: ui(15, w: FontWeight.w600, color: ghost ? C.ink : C.paper)),
          ),
        ),
      );
}

enum CkState { off, on, some }

/// .ck — 22px check circle: empty ring, filled tick, or filled dash (partial).
class Ck extends StatelessWidget {
  final CkState state;
  final double size;
  final Color ring;
  const Ck(this.state, {super.key, this.size = 22, this.ring = C.ink2});
  @override
  Widget build(BuildContext context) => Container(
        width: size,
        height: size,
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          color: state == CkState.off ? null : C.ink,
          border: state == CkState.off ? Border.all(color: ring, width: 1.5) : null,
        ),
        child: switch (state) {
          CkState.off => null,
          CkState.on => Icon(Icons.check_rounded, size: size * .62, color: C.paper),
          CkState.some => Center(child: Container(width: 9, height: 2, color: C.paper)),
        },
      );
}

/// .tg — 40x24 toggle.
class Tg extends StatelessWidget {
  final bool on;
  final ValueChanged<bool> onChanged;
  const Tg(this.on, this.onChanged, {super.key});
  @override
  Widget build(BuildContext context) => GestureDetector(
        onTap: () => onChanged(!on),
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 150),
          width: 40,
          height: 24,
          padding: const EdgeInsets.all(3),
          alignment: on ? Alignment.centerRight : Alignment.centerLeft,
          decoration: BoxDecoration(color: on ? C.ink : C.toggleOff, borderRadius: BorderRadius.circular(12)),
          child: Container(width: 18, height: 18, decoration: const BoxDecoration(color: C.paper, shape: BoxShape.circle)),
        ),
      );
}

/// One cell of a .sg segmented row.
class SgItem<T> {
  final T value;
  final String label;
  final String? sub;
  final bool rec; // shows the small "추천" mark
  const SgItem(this.value, this.label, {this.sub, this.rec = false});
}

/// .sg — equal-width choice cells; the chosen one is ink.
class Sg<T> extends StatelessWidget {
  final List<SgItem<T>> items;
  final T value;
  final ValueChanged<T> onChanged;
  const Sg(this.items, this.value, this.onChanged, {super.key});
  @override
  Widget build(BuildContext context) => Row(
        children: [
          for (final (i, it) in items.indexed) ...[
            if (i > 0) const SizedBox(width: 6),
            Expanded(
              child: GestureDetector(
                onTap: () => onChanged(it.value),
                child: Container(
                  padding: EdgeInsets.fromLTRB(4, it.sub == null ? 9 : 8, 4, 9),
                  decoration: BoxDecoration(color: it.value == value ? C.ink : C.card, borderRadius: BorderRadius.circular(12)),
                  child: Column(children: [
                    Text.rich(
                      TextSpan(text: it.label, children: [
                        if (it.rec)
                          TextSpan(text: ' 추천', style: ui(9.5, w: FontWeight.w700, color: it.value == value ? C.oak1 : C.oak2)),
                      ]),
                      textAlign: TextAlign.center,
                      style: ui(12.5, w: FontWeight.w600, color: it.value == value ? C.paper : C.ink, h: 1.25),
                    ),
                    if (it.sub != null)
                      Padding(
                        padding: const EdgeInsets.only(top: 2),
                        child: Text(it.sub!,
                            textAlign: TextAlign.center,
                            style: nm(10.5, color: it.value == value ? C.subOnInk : C.ink2, h: 1.3)),
                      ),
                  ]),
                ),
              ),
            ),
          ],
        ],
      );
}

/// .ol — option label row: bold label left, small note right.
class OptLabel extends StatelessWidget {
  final String label;
  final String? note;
  final double top;
  const OptLabel(this.label, {super.key, this.note, this.top = 14});
  @override
  Widget build(BuildContext context) => Padding(
        padding: EdgeInsets.only(top: top, bottom: 7),
        child: Row(crossAxisAlignment: CrossAxisAlignment.baseline, textBaseline: TextBaseline.alphabetic, children: [
          Text(label, style: ui(12.5, w: FontWeight.w700)),
          const SizedBox(width: 8),
          if (note != null) Expanded(child: Text(note!, textAlign: TextAlign.right, style: ui(11, color: C.ink2))),
        ]),
      );
}

/// Rounded cover-fit photo.
class Photo extends StatelessWidget {
  final String path;
  final double? w, h;
  final double r;
  const Photo(this.path, {super.key, this.w, this.h, this.r = 0});
  @override
  Widget build(BuildContext context) => ClipRRect(
        borderRadius: BorderRadius.circular(r),
        child: Image(image: imageOf(path), width: w, height: h, fit: BoxFit.cover, gaplessPlayback: true),
      );
}

/// .nb shell of the new-book screens: top line (back glyph left, label right), title, body, buttons pinned at the bottom.
class NbScaffold extends StatelessWidget {
  final String back; // '✕' or '‹'
  final VoidCallback onBack;
  final Widget? right;
  final String? title;
  final String? titleSmall;
  final String? lead;
  final Widget body;
  final List<Widget> foot;
  final bool fade;
  final bool scroll;
  const NbScaffold({
    super.key,
    this.back = '‹',
    required this.onBack,
    this.right,
    this.title,
    this.titleSmall,
    this.lead,
    required this.body,
    this.foot = const [],
    this.fade = false,
    this.scroll = true,
  });

  @override
  Widget build(BuildContext context) {
    final footH = foot.length * 60.0 + 22;
    final content = Padding(
      padding: const EdgeInsets.fromLTRB(20, 4, 20, 0),
      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, mainAxisSize: MainAxisSize.min, children: [
        if (title != null)
          Padding(
            padding: EdgeInsets.only(top: 10, bottom: lead == null ? 14 : 6),
            child: Text.rich(
              TextSpan(text: title, children: [
                if (titleSmall != null) TextSpan(text: ' $titleSmall', style: ui(13, w: FontWeight.w500, color: C.ink2)),
              ]),
              style: ui(21, w: FontWeight.w700, h: 1.25, color: C.ink).copyWith(letterSpacing: -.4),
            ),
          ),
        if (lead != null)
          Padding(padding: const EdgeInsets.only(bottom: 12), child: Text(kw(lead!), style: ui(12, color: C.body, h: 1.5))),
        body,
        SizedBox(height: footH + 16),
      ]),
    );
    return Scaffold(
      body: SafeArea(
        child: Stack(children: [
          Column(children: [
            SizedBox(
              height: 34,
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 8),
                child: Row(children: [
                  GestureDetector(
                    behavior: HitTestBehavior.opaque,
                    onTap: onBack,
                    child: SizedBox(
                      width: 44,
                      height: 34,
                      child: Center(
                        child: back == '✕' ? const Icon(Icons.close, size: 17, color: C.ink2) : Text(back, style: ui(20, color: C.ink2)),
                      ),
                    ),
                  ),
                  const Spacer(),
                  Padding(padding: const EdgeInsets.only(right: 12), child: right ?? const SizedBox()),
                ]),
              ),
            ),
            Expanded(child: scroll ? SingleChildScrollView(child: content) : content),
          ]),
          if (fade)
            Positioned(
              left: 0,
              right: 0,
              bottom: 0,
              height: footH + 40,
              child: const IgnorePointer(
                child: DecoratedBox(
                  decoration: BoxDecoration(
                    gradient: LinearGradient(begin: Alignment.topCenter, end: Alignment.bottomCenter, stops: [0, .46], colors: [Color(0x00F4EFE7), C.paper]),
                  ),
                ),
              ),
            ),
          Positioned(
            left: 20,
            right: 20,
            bottom: 22,
            child: Column(children: [
              for (final (i, f) in foot.indexed) ...[if (i > 0) const SizedBox(height: 10), f],
            ]),
          ),
        ]),
      ),
    );
  }
}

/// Top-right label: page counter ("1 / 3") or a bold state ("3장 고름").
Widget topLabel(String s, {bool bold = false}) =>
    Text(s, style: bold ? ui(12.5, w: FontWeight.w600) : nm(11, color: C.ink2));

/// Korean count words for days ("사흘"), as the mockup writes them.
String dayWord(int n) => const {1: '하루', 2: '이틀', 3: '사흘', 4: '나흘', 5: '닷새', 6: '엿새', 7: '이레', 8: '여드레', 9: '아흐레', 10: '열흘'}[n] ?? '$n일';
