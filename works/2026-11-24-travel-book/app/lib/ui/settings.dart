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
    // one line of a group: title, a small line under it, the value (or a chevron when it opens something)
    Widget row(String t, String s, String? v, {VoidCallback? onTap, bool first = false}) => GestureDetector(
          onTap: onTap,
          behavior: HitTestBehavior.opaque,
          child: Container(
            padding: const EdgeInsets.fromLTRB(14, 13, 12, 13),
            decoration: first ? null : const BoxDecoration(border: Border(top: BorderSide(color: C.cardEdge))),
            child: Row(children: [
              Expanded(
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text(t, style: ui(14, w: FontWeight.w600)),
                  const SizedBox(height: 3),
                  Text(kw(s), style: ui(11.5, color: C.ink2, h: 1.4)),
                ]),
              ),
              const SizedBox(width: 10),
              if (v != null) Text(v, style: nm(13, color: C.ink2)) else const Icon(Icons.chevron_right_rounded, size: 20, color: C.ink2),
            ]),
          ),
        );
    Widget group(List<Widget> rows) =>
        Container(decoration: cardDeco(), child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: rows));
    final used = freeBooks - freeLeft;
    return NbScaffold(
      onBack: onBack,
      title: '설정',
      body: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        const OptLabel('책장 기본 보기', top: 0),
        Sg([for (final v in ShelfView.values) SgItem(v, v.label)], view, onView),
        const OptLabel('화질 기본값', note: '책마다 바꿀 수 있어요'),
        Sg(const [SgItem('reduced', '조금 낮추기'), SgItem('original', '원본 그대로')], quality, onQuality),
        const OptLabel('내 책', top: 22),
        group([
          row('만든 책', freeLeft == 0 ? '무료 $freeBooks권 중 $used권 씀 · 다음 책부터 1권 1,500원' : '무료 $freeBooks권 중 $used권 씀', '$books권', first: true),
          row('사진 접근', access, null, onTap: onAccess),
        ]),
        const OptLabel('정보', top: 22),
        group([
          row('이용 약관 · 개인정보', '원본은 폰에만. 작은 미리보기만 AI로 보내고 저장하지 않아요', null, onTap: onTerms, first: true),
          if (onLab != null) row('실험용 기록 보내기', '마지막 사진 고르기의 미리보기·숫자 (시험판에만)', null, onTap: onLab),
          row('버전', '개인용 빌드 · 결제 없음', version),
        ]),
      ]),
    );
  }
}
