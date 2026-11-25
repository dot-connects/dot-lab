// D4 사진을 눌렀을 때: swap for a same-place photo (the pair's loser first), remove, or use as the cover.
import 'package:material_ui/material_ui.dart';

import '../new_book/draft.dart';
import '../tokens.dart';
import '../widgets.dart';

class SwapAlt {
  final PhotoRef photo;
  final String label; // "아까 둘 중" | "같은 곳"
  final String time; // "13:24"
  const SwapAlt(this.photo, this.label, this.time);
}

/// Sheet body; show it with showSheet(context, (_) => SwapSheet(...)).
class SwapSheet extends StatelessWidget {
  final PhotoRef photo;
  final String place, when; // "협재 해변", "6.12 금 13:20"
  final List<SwapAlt> alts; // the first 3 are shown
  final int samePlace; // "같은 곳 사진 14장"
  final bool isCover;
  final ValueChanged<SwapAlt> onSwap;
  final VoidCallback onMore, onRemove, onCover;
  const SwapSheet({
    super.key,
    required this.photo,
    required this.place,
    required this.when,
    required this.alts,
    required this.samePlace,
    this.isCover = false,
    required this.onSwap,
    required this.onMore,
    required this.onRemove,
    required this.onCover,
  });

  @override
  Widget build(BuildContext context) {
    final shown = alts.take(3).toList();
    final more = samePlace - shown.length;
    Widget cell(Widget pic, String b, String t, VoidCallback f) => Expanded(
          child: GestureDetector(
            onTap: f,
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              AspectRatio(aspectRatio: 1, child: pic),
              const SizedBox(height: 4),
              Text(b, style: ui(10.5, w: FontWeight.w600)),
              Text(t, style: nm(10.5, color: C.ink2)),
            ]),
          ),
        );
    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, mainAxisSize: MainAxisSize.min, children: [
      SizedBox(height: 150, child: Photo(photo.path, r: 12)),
      Padding(
        padding: const EdgeInsets.only(top: 10, bottom: 12),
        child: Row(crossAxisAlignment: CrossAxisAlignment.baseline, textBaseline: TextBaseline.alphabetic, children: [
          Text(place, style: ui(14.5, w: FontWeight.w700)),
          const Spacer(),
          Text(when, style: nm(11, color: C.ink2)),
        ]),
      ),
      if (samePlace > 0) ...[
        Padding(
          padding: const EdgeInsets.only(bottom: 7),
          child: Row(crossAxisAlignment: CrossAxisAlignment.baseline, textBaseline: TextBaseline.alphabetic, children: [
            Text('이 사진 대신', style: ui(12, w: FontWeight.w600)),
            const Spacer(),
            Text('같은 곳 사진 $samePlace장', style: ui(11, color: C.ink2)),
          ]),
        ),
        Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
          for (final (i, a) in shown.indexed) ...[
            if (i > 0) const SizedBox(width: 6),
            cell(Photo(a.photo.path, r: 8), a.label, a.time, () => onSwap(a)),
          ],
          for (var i = shown.length; i < 3; i++) ...[const SizedBox(width: 6), const Expanded(child: SizedBox())],
          const SizedBox(width: 6),
          more > 0
              ? cell(
                  Container(
                    alignment: Alignment.center,
                    decoration: BoxDecoration(borderRadius: BorderRadius.circular(8), border: Border.all(color: C.dash, width: 1.5)),
                    child: Text('+$more', style: nm(12, w: FontWeight.w600)),
                  ),
                  '',
                  '더 보기',
                  onMore)
              : const Expanded(child: SizedBox()),
        ]),
      ],
      const SizedBox(height: 14),
      Row(children: [
        Expanded(child: _btn('빼기', onRemove)),
        const SizedBox(width: 8),
        Expanded(child: _btn(isCover ? '표지예요' : '표지로 쓰기', isCover ? null : onCover)),
      ]),
    ]);
  }

  Widget _btn(String t, VoidCallback? f) => SizedBox(height: 46, child: Btn(t, ghost: true, onTap: f));
}
