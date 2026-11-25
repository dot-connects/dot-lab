// D4 1/2 사진을 고르고 있어요 (AI call 1): the count shrinks one line at a time.
import 'package:material_ui/material_ui.dart';

import '../tokens.dart';
import '../widgets.dart';

class PickingScreen extends StatelessWidget {
  final List<(String path, bool kept)> thumbs; // the 6x2 strip; dropped ones fade
  final int inRange, albums, albumPhotos;
  final ({int blurDark, int similar, int capture}) filtered;
  final int sent; // previews sent to the AI
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
  Widget build(BuildContext context) {
    final f = filtered;
    return NbScaffold(
      back: '✕',
      onBack: onClose,
      right: topLabel('1 / 2'),
      title: '사진을 고르고 있어요',
      body: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Padding(padding: const EdgeInsets.only(top: 2, bottom: 18), child: FadeGrid(thumbs)),
        Steps([
          StepRow('이 기간 사진', fmtInt(inRange)),
          StepRow('고른 앨범 $albums개', fmtInt(albumPhotos)),
          StepRow('폰에서 먼저 거르기', fmtInt(sent),
              small: '흐림·어두움 ${f.blurDark} · 비슷한 사진 ${f.similar} · 화면 캡처 ${f.capture}'),
          StepRow('잘 나온 사진 고르는 중', picked == null ? '…' : fmtInt(picked!),
              small: must > 0 ? '꼭 넣을 사진 $must장은 그대로 들어가요' : null, state: StepMark.now),
        ]),
        Priv('작게 줄인 미리보기 $sent장만 AI로 보내요. 원본은 폰에 그대로 있고 어디에도 저장하지 않아요.'),
      ]),
    );
  }
}

/// .work — a 6-column strip of square thumbs; dropped ones are faded and grey.
class FadeGrid extends StatelessWidget {
  final List<(String, bool)> items;
  const FadeGrid(this.items, {super.key});
  @override
  Widget build(BuildContext context) => GridView.count(
        crossAxisCount: 6,
        mainAxisSpacing: 4,
        crossAxisSpacing: 4,
        shrinkWrap: true,
        physics: const NeverScrollableScrollPhysics(),
        children: [
          for (final (p, kept) in items)
            kept
                ? Photo(p, r: 5)
                : Opacity(opacity: .22, child: ColorFiltered(colorFilter: const ColorFilter.matrix(grayMatrix), child: Photo(p, r: 5))),
        ],
      );
}

const grayMatrix = <double>[
  .2126, .7152, .0722, 0, 0, //
  .2126, .7152, .0722, 0, 0, //
  .2126, .7152, .0722, 0, 0, //
  0, 0, 0, 1, 0,
];
