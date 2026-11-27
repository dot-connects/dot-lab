// D4 고른 사진 보기: the picked photos by day. Tap = swap sheet; [+] per day adds more, locked at 48 in a book.
// [이대로 책 만들기] goes on to 어떤 책으로 만들까요? (choose_theme.dart), where theme and length are picked.
import 'package:material_ui/material_ui.dart';

import '../new_book/draft.dart';
import '../tokens.dart';
import '../widgets.dart';

const maxInBook = 48; // photos + videos, so /write never gets more

class PicksReviewScreen extends StatelessWidget {
  final List<(DateTime, List<PhotoRef>)> days;
  final Set<String> must;
  final String? coverId;
  final int from; // photos the picker chose from
  final int videos;
  final String? notice; // why the phone picked these on its own (the AI did not answer)
  final int pairs; // look-alike pairs the AI left to the person
  final VoidCallback? onPairs;
  final ValueChanged<PhotoRef> onTapPhoto;
  final ValueChanged<DateTime> onAdd;
  final VoidCallback onBack, onMake; // onMake: on to the book-shape screen
  const PicksReviewScreen({
    super.key,
    required this.days,
    required this.must,
    this.coverId,
    required this.from,
    this.videos = 0,
    this.notice,
    this.pairs = 0,
    this.onPairs,
    required this.onTapPhoto,
    required this.onAdd,
    required this.onBack,
    required this.onMake,
  });

  @override
  Widget build(BuildContext context) {
    final n = days.fold(0, (s, d) => s + d.$2.length);
    final full = n + videos >= maxInBook;
    void add(DateTime d) {
      if (full) {
        ScaffoldMessenger.of(context)
          ..hideCurrentSnackBar()
          ..showSnackBar(const SnackBar(content: Text('한 권에 48장까지')));
      } else {
        onAdd(d);
      }
    }

    return NbScaffold(
      onBack: onBack,
      right: topLabel(videos > 0 ? '사진 $n · 영상 $videos' : '사진 $n'),
      title: '이 $n장으로 만들어요',
      lead: '${fmtInt(from)}장에서 골랐어요. 누르면 빼거나 바꿀 수 있어요.',
      fade: true,
      body: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        if (notice != null)
          Container(
            margin: const EdgeInsets.only(bottom: 14),
            padding: const EdgeInsets.fromLTRB(14, 12, 14, 12),
            decoration: BoxDecoration(color: C.card, borderRadius: BorderRadius.circular(14)),
            child: Text(notice!, style: ui(12.5, color: C.ink, h: 1.5)),
          ),
        if (pairs > 0 && onPairs != null)
          GestureDetector(
            onTap: onPairs,
            child: Container(
              margin: const EdgeInsets.only(bottom: 14),
              padding: const EdgeInsets.fromLTRB(14, 12, 10, 12),
              decoration: BoxDecoration(border: Border.all(color: C.ink, width: 1.5), borderRadius: BorderRadius.circular(14)),
              child: Row(children: [
                Expanded(
                  child: Text('비슷하게 잘 나온 두 장이 $pairs쌍 있어요. 하나씩 골라 볼래요?\n안 고르면 점수가 높은 쪽으로 넣어요.', style: ui(12.5, color: C.ink, h: 1.5)),
                ),
                Text('고르기 ›', style: ui(13.5, w: FontWeight.w700)),
              ]),
            ),
          ),
        for (final (i, (day, ps)) in days.indexed) ...[
          Container(
            height: 22,
            margin: EdgeInsets.only(top: i == 0 ? 0 : 10),
            child: Row(crossAxisAlignment: CrossAxisAlignment.baseline, textBaseline: TextBaseline.alphabetic, children: [
              Text('${day.month}월 ${day.day}일 ${weekdays[day.weekday - 1]}', style: ui(12.5, w: FontWeight.w600)),
              const Spacer(),
              Text('${ps.length}장', style: nm(11, color: C.ink2)),
            ]),
          ),
          GridView.count(
            crossAxisCount: 5,
            mainAxisSpacing: 4,
            crossAxisSpacing: 4,
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            children: [
              for (final p in ps) _tile(p),
              GestureDetector(
                onTap: () => add(day),
                child: Opacity(
                  opacity: full ? .4 : 1,
                  child: Container(
                    alignment: Alignment.center,
                    decoration: BoxDecoration(borderRadius: BorderRadius.circular(6), border: Border.all(color: C.dash, width: 1.5)),
                    child: Text('+', style: nm(22, w: FontWeight.w300, color: C.ink2, h: 1)),
                  ),
                ),
              ),
            ],
          ),
        ],
      ]),
      foot: [
        Btn('이대로 책 만들기', onTap: onMake),
      ],
    );
  }

  Widget _tile(PhotoRef p) {
    Widget badge(String t, {required bool cover}) => Container(
          padding: cover ? const EdgeInsets.fromLTRB(5, 3, 5, 4) : null,
          width: cover ? null : 16,
          height: cover ? null : 16,
          alignment: Alignment.center,
          decoration: BoxDecoration(
            color: C.ink,
            borderRadius: cover ? const BorderRadius.only(topRight: Radius.circular(5)) : BorderRadius.circular(8),
            border: cover ? null : Border.all(color: C.paper, width: 1.5),
          ),
          child: Text(t, style: ui(9, w: cover ? FontWeight.w600 : FontWeight.w700, color: C.paper, h: 1)),
        );
    return GestureDetector(
      onTap: () => onTapPhoto(p),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(6),
        child: Stack(fit: StackFit.expand, children: [
          Container(color: C.gridBg, child: Photo(p.path)),
          if (must.contains(p.id)) Positioned(right: 3, top: 3, child: badge('꼭', cover: false)),
          if (p.id == coverId) Positioned(left: 0, bottom: 0, child: badge('표지', cover: true)),
        ]),
      ),
    );
  }
}
