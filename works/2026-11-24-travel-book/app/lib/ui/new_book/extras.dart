// D3 3/3 더 넣을 것: optional side roads (must-include, notes, timeline) and the estimate before [책 만들기].
import 'package:material_ui/material_ui.dart';

import '../tokens.dart';
import '../widgets.dart';
import 'draft.dart';

class ExtrasScreen extends StatelessWidget {
  final NewBookDraft draft;
  final int photos; // photos in the chosen albums and range
  final List<String> mustThumbs; // paths of the chosen must-include photos
  final int freeLeft;
  final VoidCallback onBack, onMust, onNotes, onTimeline, onMake, onShape;
  const ExtrasScreen({
    super.key,
    required this.draft,
    required this.photos,
    this.mustThumbs = const [],
    required this.freeLeft,
    required this.onBack,
    required this.onMust,
    required this.onNotes,
    required this.onTimeline,
    required this.onMake,
    required this.onShape,
  });

  @override
  Widget build(BuildContext context) {
    final o = draft.opts;
    final pages = lengthPages[o.length]!;
    final must = draft.mustInclude.length;
    final shapeLabel = draft.shapeTouched
        ? '${themeNames[o.theme ?? 'a']} · ${lengthNames[o.length]} $pages쪽 · 바꾸기'
        : '테마·길이 먼저 고르기';
    return NbScaffold(
      onBack: onBack,
      right: topLabel('3 / 3'),
      title: '더 넣을 게 있어요?',
      titleSmall: '(없어도 돼요)',
      body: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        _row(Icons.check_box_outlined, '꼭 넣을 사진', must > 0 ? '$must장 골랐어요' : '고른 사진은 빠지지 않아요', must > 0, onMust,
            trail: mustThumbs.isEmpty ? null : _stack()),
        const SizedBox(height: 8),
        _row(Icons.edit_outlined, '일기·메모',
            draft.notes.isEmpty ? '글·파일·손글씨 사진. 넣으면 그 말투로 글을 써요' : '${draft.notes.length}개 넣었어요', draft.notes.isNotEmpty, onNotes),
        const SizedBox(height: 8),
        _row(Icons.route_outlined, '구글 타임라인',
            draft.timeline == null ? '다닌 길을 지도 쪽에. 내보내는 법은 그림으로 안내' : '넣었어요 · 다닌 길을 지도 쪽에', draft.timeline != null, onTimeline),
        Container(
          margin: const EdgeInsets.only(top: 12),
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(borderRadius: BorderRadius.circular(14), border: Border.all(color: C.line)),
          child: Text.rich(
            TextSpan(children: [
              TextSpan(text: kw('사진 ${fmtInt(photos)}장에서 잘 나온 것을 골라 ')),
              TextSpan(text: '약 $pages쪽', style: nm(12, w: FontWeight.w700)),
              TextSpan(text: kw('으로 만들어요. 1분쯤 걸려요.\n이번 책: ')),
              if (freeLeft > 0) ...[
                TextSpan(text: '무료', style: ui(12, w: FontWeight.w700)),
                const TextSpan(text: ' (처음 2권) · '),
                TextSpan(text: kw('그다음부터 1권 1,500원')),
              ] else
                TextSpan(text: '1,500원', style: nm(12, w: FontWeight.w700)),
            ]),
            style: ui(12, color: C.body, h: 1.55),
          ),
        ),
      ]),
      foot: [Btn('책 만들기', onTap: onMake), Btn(shapeLabel, ghost: true, onTap: onShape)],
    );
  }

  Widget _stack() => SizedBox(
        width: 30 + 21.0 * (mustThumbs.take(3).length - 1),
        height: 30,
        child: Stack(children: [
          for (final (i, p) in mustThumbs.take(3).indexed)
            Positioned(
              left: 21.0 * i,
              child: Container(
                decoration: BoxDecoration(borderRadius: BorderRadius.circular(9), border: Border.all(color: C.cardOn, width: 2)),
                child: Photo(p, w: 30, h: 30, r: 7),
              ),
            ),
        ]),
      );

  Widget _row(IconData ic, String t, String s, bool fill, VoidCallback onTap, {Widget? trail}) => GestureDetector(
        onTap: onTap,
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 11),
          decoration: BoxDecoration(color: fill ? C.cardOn : C.card, borderRadius: BorderRadius.circular(14)),
          child: Row(children: [
            Container(
              width: 36,
              height: 36,
              decoration: BoxDecoration(color: C.paper, borderRadius: BorderRadius.circular(10)),
              child: Icon(ic, size: 18, color: C.ink),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(t, style: ui(13.5, w: FontWeight.w700)),
                const SizedBox(height: 1),
                Text(kw(s), style: ui(11, color: C.ink2, h: 1.38)),
              ]),
            ),
            if (trail != null) ...[const SizedBox(width: 12), trail],
            const SizedBox(width: 12),
            Text('›', style: ui(16, color: C.ink2)),
          ]),
        ),
      );
}
