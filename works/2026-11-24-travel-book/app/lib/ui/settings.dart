// D6 설정 (mockup §10): two defaults and a few lines to read.
import 'package:material_ui/material_ui.dart';

import '../core/library.dart';
import 'shelf.dart';
import 'tokens.dart';
import 'widgets.dart';

class SettingsScreen extends StatelessWidget {
  final ShelfView view;
  final String quality; // reduced|original
  final int books, freeLeft;
  final String access; // "모두 허용 · 위치 읽기 켜짐"
  final String version;
  final ValueChanged<ShelfView> onView;
  final ValueChanged<String> onQuality;
  final VoidCallback onBack, onAccess, onTerms;
  final VoidCallback? onLab; // test builds only: share the last pick's previews and numbers
  const SettingsScreen({
    super.key,
    required this.view,
    required this.quality,
    required this.books,
    required this.freeLeft,
    required this.access,
    required this.version,
    required this.onView,
    required this.onQuality,
    required this.onBack,
    required this.onAccess,
    required this.onTerms,
    this.onLab,
  });

  @override
  Widget build(BuildContext context) {
    Widget row(String t, String s, String v, {VoidCallback? onTap}) => GestureDetector(
          onTap: onTap,
          behavior: HitTestBehavior.opaque,
          child: Container(
            padding: const EdgeInsets.symmetric(vertical: 9),
            decoration: const BoxDecoration(border: Border(top: BorderSide(color: C.line))),
            child: Row(children: [
              Expanded(
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text(t, style: ui(12.5, w: FontWeight.w600)),
                  const SizedBox(height: 1),
                  Text(kw(s), style: ui(10.5, color: C.ink2, h: 1.38)),
                ]),
              ),
              const SizedBox(width: 10),
              Text(v, style: nm(11.5, color: C.ink2)),
            ]),
          ),
        );
    final used = freeBooks - freeLeft;
    return NbScaffold(
      onBack: onBack,
      title: '설정',
      body: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        const OptLabel('책장 기본 보기', top: 0),
        Sg(const [SgItem(ShelfView.display, '진열'), SgItem(ShelfView.timeline, '연표')], view, onView),
        const OptLabel('화질 기본값', note: '책마다 바꿀 수 있어요'),
        Sg(const [SgItem('reduced', '조금 낮추기'), SgItem('original', '원본 그대로')], quality, onQuality),
        const SizedBox(height: 16),
        row('만든 책', freeLeft == 0 ? '무료 $freeBooks권 중 $used권 씀 · 다음 책부터 1권 1,500원' : '무료 $freeBooks권 중 $used권 씀', '$books권'),
        row('사진 접근', access, '›', onTap: onAccess),
        row('이용 약관 · 개인정보', '원본은 폰에만. 작은 미리보기만 AI로 보내고 저장하지 않아요', '›', onTap: onTerms),
        if (onLab != null) row('실험용 기록 보내기', '마지막 사진 고르기의 미리보기·숫자 (시험판에만)', '›', onTap: onLab),
        row('버전', '개인용 빌드 · 결제 없음', version),
      ]),
    );
  }
}
