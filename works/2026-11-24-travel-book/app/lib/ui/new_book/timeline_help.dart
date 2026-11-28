// D3 구글 타임라인 넣기: three pictured steps for exporting Timeline.json, then pick the file.
import 'package:material_ui/material_ui.dart';

import '../tokens.dart';
import '../widgets.dart';

class TimelineHelpScreen extends StatelessWidget {
  final String range; // "6월 12–14일"
  final VoidCallback onBack, onPickFile, onOpenSettings;
  const TimelineHelpScreen({super.key, required this.range, required this.onBack, required this.onPickFile, required this.onOpenSettings});

  @override
  Widget build(BuildContext context) {
    Widget menu(List<String> items, String hi) => Column(mainAxisAlignment: MainAxisAlignment.center, children: [
          for (final s in items)
            Container(
              height: 11,
              margin: const EdgeInsets.symmetric(vertical: 2),
              padding: const EdgeInsets.symmetric(horizontal: 4),
              decoration: BoxDecoration(color: s == hi ? C.ink : null, borderRadius: BorderRadius.circular(3)),
              child: Row(children: [
                Container(width: 5, height: 5, decoration: BoxDecoration(color: s == hi ? C.oak1 : const Color(0xFFCFC4B3), shape: BoxShape.circle)),
                const SizedBox(width: 4),
                Text(s, style: ui(8.5, w: FontWeight.w500, color: s == hi ? C.paper : C.ink2, h: 1)),
              ]),
            ),
        ]);
    Widget step(Widget il, int n, String t) => Container(
          margin: const EdgeInsets.only(bottom: 8),
          padding: const EdgeInsets.all(9),
          decoration: cardDeco(color: C.card, r: 14),
          child: Row(children: [
            Container(
              width: 92,
              height: 64,
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 7),
              decoration: BoxDecoration(color: C.paper, borderRadius: BorderRadius.circular(9)),
              child: il,
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text('$n', style: nm(20, w: FontWeight.w300, h: 1)),
                const SizedBox(height: 3),
                Text(kw(t), style: ui(12, h: 1.45)),
              ]),
            ),
          ]),
        );
    return NbScaffold(
      onBack: onBack,
      title: '구글 타임라인 넣기',
      lead: '다닌 길이 지도 쪽에 들어가요. 구글 지도에서 파일을 한 번 내보내야 해요.',
      body: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        step(menu(const ['소리', '위치', '배터리'], '위치'), 1, '폰 설정 → 위치 → 위치 서비스 → 타임라인'),
        step(menu(const ['자동 삭제', '내보내기', '백업'], '내보내기'), 2, '[타임라인 데이터 내보내기]를 누르고 저장할 곳을 골라요'),
        step(
          Column(mainAxisAlignment: MainAxisAlignment.center, children: [
            const Icon(Icons.insert_drive_file_outlined, size: 18, color: C.ink),
            const SizedBox(height: 5),
            Text('Timeline.json', style: ui(8.5, w: FontWeight.w500, h: 1)),
          ]),
          3,
          '여기로 돌아와 그 파일을 골라요',
        ),
        Container(
          margin: const EdgeInsets.only(top: 6),
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 11),
          decoration: cardDeco(color: C.card, r: 14),
          child: Text(kw('파일은 폰 안에서만 읽고, $range 기록만 써요. 없어도 사진에 든 위치로 지도를 그려요.'), style: ui(11.5, color: C.body, h: 1.5)),
        ),
      ]),
      foot: [Btn('파일 고르기', onTap: onPickFile), Btn('폰 설정 열기', ghost: true, onTap: onOpenSettings)],
    );
  }
}
