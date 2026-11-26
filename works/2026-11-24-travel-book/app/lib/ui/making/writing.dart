// D4 2/2 글을 쓰고 있어요 (AI call 2): the line being written sits next to its photo, with the page count.
import 'package:material_ui/material_ui.dart';

import '../tokens.dart';
import '../widgets.dart';

enum WriteStage { lines, essay, binding }

class WritingScreen extends StatelessWidget {
  final String photo;
  final String meta; // "13:20 · 협재 해변"
  final String typing; // the line so far: "물이 생각보다 차"
  final int picked, page, pages;
  final String tone; // "담담한 일기체"
  final String theme; // "여백의 책"
  final WriteStage stage;
  final VoidCallback onClose;
  const WritingScreen({
    super.key,
    required this.photo,
    required this.meta,
    required this.typing,
    required this.picked,
    required this.page,
    required this.pages,
    required this.tone,
    required this.theme,
    this.stage = WriteStage.lines,
    required this.onClose,
  });

  @override
  Widget build(BuildContext context) {
    StepMark st(WriteStage s) => stage == s ? StepMark.now : (stage.index > s.index ? StepMark.done : StepMark.wait);
    String v(WriteStage s, String done) => switch (st(s)) { StepMark.wait => '기다리는 중', StepMark.now => '쓰는 중', StepMark.done => done };
    return NbScaffold(
      back: '✕',
      onBack: onClose,
      right: topLabel('2 / 2'),
      title: '글을 쓰고 있어요',
      body: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Container(
          margin: const EdgeInsets.only(bottom: 16),
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(14),
            boxShadow: const [BoxShadow(color: Color(0x1F3C2814), blurRadius: 16, offset: Offset(0, 6))],
          ),
          child: Row(crossAxisAlignment: CrossAxisAlignment.end, children: [
            Photo(photo, w: 92, h: 122),
            const SizedBox(width: 12),
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(meta, style: nm(10.5, color: C.ink2)),
                const SizedBox(height: 3),
                Text.rich(
                  TextSpan(text: typing, children: [
                    WidgetSpan(
                      alignment: PlaceholderAlignment.middle,
                      child: Container(width: 1.5, height: 13, margin: const EdgeInsets.only(left: 2), color: C.ink),
                    ),
                  ]),
                  style: ui(12, h: 1.5),
                ),
              ]),
            ),
          ]),
        ),
        Steps([
          StepRow('고른 사진', fmtInt(picked)),
          stage == WriteStage.lines
              ? StepRow('쪽마다 한 줄 쓰는 중', '$page', unit: ' / $pages쪽', small: '시각·장소를 붙이고 $tone로', state: StepMark.now)
              : StepRow('쪽마다 한 줄', '$pages', unit: '쪽'),
          StepRow('마지막 글', v(WriteStage.essay, '다 썼어요'), state: st(WriteStage.essay)),
          StepRow('책으로 엮기', v(WriteStage.binding, ''), small: '$theme · 폰에서', state: st(WriteStage.binding)),
        ]),
        Priv('고른 $picked장의 작은 미리보기와 시각·장소 이름만 AI로 보내요. 2분쯤 걸려요.'),
      ]),
    );
  }
}
