// D5 가격 시트 (third book on). Payment is not built: the sheet says so and the main button just makes the book.
import 'package:material_ui/material_ui.dart';

import 'tokens.dart';
import 'widgets.dart';

const bookPrice = '1,500원';

/// Show with showSheet(context, (_) => PriceSheet(...)).
class PriceSheet extends StatelessWidget {
  final String line; // "경주 10월 3–4일 · 약 16쪽"
  final VoidCallback onLater, onMake;
  const PriceSheet({super.key, required this.line, required this.onLater, required this.onMake});

  @override
  Widget build(BuildContext context) => Column(crossAxisAlignment: CrossAxisAlignment.stretch, mainAxisSize: MainAxisSize.min, children: [
        Text('세 번째 책부터는\n1권에 $bookPrice이에요', style: ui(16, w: FontWeight.w700, h: 1.35)),
        Padding(
          padding: const EdgeInsets.only(top: 6, bottom: 12),
          child: Text(kw('무료 2권을 다 썼어요. 만든 책은 계속 볼 수 있어요.'), style: ui(12, color: C.body, h: 1.5)),
        ),
        for (final s in const ['사진 고르기와 글 쓰기', '다듬기·내보내기는 몇 번이든', '사진이 많아도, 쪽이 늘어도 같은 값'])
          Padding(
            padding: const EdgeInsets.only(bottom: 6),
            child: Row(children: [const Icon(Icons.check, size: 13, color: C.ink), const SizedBox(width: 8), Text(s, style: ui(12.5))]),
          ),
        Container(
          margin: const EdgeInsets.symmetric(vertical: 12),
          padding: const EdgeInsets.symmetric(vertical: 12),
          decoration: const BoxDecoration(border: Border.symmetric(horizontal: BorderSide(color: C.line))),
          child: Row(crossAxisAlignment: CrossAxisAlignment.baseline, textBaseline: TextBaseline.alphabetic, children: [
            Expanded(child: Text(line, style: ui(13))),
            Text(bookPrice, style: nm(20, w: FontWeight.w700)),
          ]),
        ),
        const Warn('결제는 아직 준비 중이에요.', '지금은 개인용 빌드라 돈이 나가지 않고 책은 그대로 만들어져요.'),
        const SizedBox(height: 14),
        Row(children: [
          Expanded(flex: 10, child: SizedBox(height: 46, child: Btn('다음에', ghost: true, onTap: onLater))),
          const SizedBox(width: 8),
          Expanded(flex: 16, child: SizedBox(height: 46, child: Btn('이번엔 그냥 만들기', onTap: onMake))),
        ]),
        Padding(
          padding: const EdgeInsets.only(top: 10),
          child: Text('책이 만들어지지 않으면 값을 받지 않아요.', textAlign: TextAlign.center, style: ui(11, color: C.ink2, h: 1.45)),
        ),
      ]);
}
