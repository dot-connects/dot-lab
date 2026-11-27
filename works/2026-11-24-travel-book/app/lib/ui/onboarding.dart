// D1 first run: three slides (mockup §1 #first). The permission request is the caller's job.
import 'package:material_ui/material_ui.dart';

import 'tokens.dart';
import 'widgets.dart';

const _p = 'assets/sample/';

class Onboarding extends StatefulWidget {
  final VoidCallback onAllow; // "사진 접근 허용하고 시작"
  const Onboarding({super.key, required this.onAllow});
  @override
  State<Onboarding> createState() => _OnboardingState();
}

class _OnboardingState extends State<Onboarding> {
  final _pc = PageController();
  int _i = 0;

  static const _slides = [
    ('날짜만 고르면\n그 여행이 한 권의 책이 돼요', '표지부터 마지막 쪽까지, 날짜·장소별로 엮어 넘겨 보는 책. 사진·PDF로도 남겨요.'),
    ('흐린 사진·연사·스크린샷은\n알아서 빼요', '잘 나온 것만 남기고, 둘 다 좋은 사진은 한 번만 물어봐요. 귀찮으면 [다 맡기기].'),
    ('사진은 폰을 떠나지 않아요', '고르고 글을 쓰기 위해 작게 줄인 미리보기만 잠깐 보내고, 어디에도 저장하지 않아요. 위치 정보를 쓰려면 사진 접근을 "모두 허용"으로 해 주세요.'),
  ];

  void _go(int i) => _pc.animateToPage(i, duration: const Duration(milliseconds: 280), curve: Curves.easeOut);

  @override
  void dispose() {
    _pc.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final last = _i == 2;
    return Scaffold(
      body: SafeArea(
        child: Stack(children: [
          PageView(
            controller: _pc,
            onPageChanged: (i) => setState(() => _i = i),
            children: [for (var i = 0; i < 3; i++) _slide(i)],
          ),
          if (!last)
            Positioned(
              right: 8,
              top: 0,
              child: GestureDetector(
                behavior: HitTestBehavior.opaque,
                onTap: () => _go(2), // skip to the permission slide: it is asked once, there
                child: Padding(padding: const EdgeInsets.fromLTRB(14, 8, 14, 8), child: Text('건너뛰기', style: ui(11, w: FontWeight.w600))),
              ),
            ),
          Positioned(
            left: 20,
            right: 20,
            bottom: 22,
            child: Btn(last ? '사진 접근 허용하고 시작' : '다음', onTap: last ? widget.onAllow : () => _go(_i + 1)),
          ),
        ]),
      ),
    );
  }

  Widget _slide(int i) {
    final (h, p) = _slides[i];
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 24),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Expanded(child: Center(child: [_spread, _flow, _lock][i]())),
        Row(children: [
          for (var d = 0; d < 3; d++)
            Container(
              margin: const EdgeInsets.only(right: 6),
              width: d == i ? 20 : 6,
              height: 6,
              decoration: BoxDecoration(color: C.ink.withValues(alpha: d == i ? 1 : .2), borderRadius: BorderRadius.circular(3)),
            ),
        ]),
        const SizedBox(height: 18),
        Text(h, style: ui(25, w: FontWeight.w700, h: 1.28).copyWith(letterSpacing: -.5)),
        const SizedBox(height: 10),
        Text(kw(p), style: ui(14, color: C.body, h: 1.6)),
        SizedBox(height: i == 2 ? 132 : 150),
      ]),
    );
  }

  // 1/3: the theme cover and one body page, opened like a book.
  Widget _spread() => Container(
        width: 248,
        height: 186,
        decoration: const BoxDecoration(boxShadow: [BoxShadow(color: Color(0x383C2814), blurRadius: 18, offset: Offset(0, 14))]),
        child: Stack(children: [
          Row(children: [
            Expanded(
              child: ClipRRect(
                borderRadius: const BorderRadius.horizontal(left: Radius.circular(3)),
                child: Stack(fit: StackFit.expand, children: [
                  Image.asset('${_p}udo.jpg', fit: BoxFit.cover),
                  const DecoratedBox(
                    decoration: BoxDecoration(
                      gradient: LinearGradient(begin: Alignment.bottomCenter, end: Alignment.topCenter, stops: [0, .55], colors: [Color(0x73000000), Color(0x00000000)]),
                    ),
                  ),
                  Positioned(left: 9, top: 11, child: Text('2026.06.12 — 06.14', style: nm(5.6, color: Colors.white))),
                  Positioned(left: 9, bottom: 9, child: Text('JEJU', style: nm(20.7, w: FontWeight.w800, color: Colors.white, h: .8, ls: -1))),
                ]),
              ),
            ),
            Expanded(
              child: ClipRRect(
                borderRadius: const BorderRadius.horizontal(right: Radius.circular(3)),
                child: Container(
                  color: Colors.white,
                  child: Column(children: [
                    Expanded(flex: 76, child: Image.asset('${_p}haenyeo.jpg', fit: BoxFit.cover, width: double.infinity)),
                    Expanded(
                      flex: 24,
                      child: Align(
                        alignment: Alignment.bottomLeft,
                        child: Padding(
                          padding: const EdgeInsets.fromLTRB(8, 0, 4, 7),
                          child: Text('14:10 · 하도 해녀 탈의장 앞', style: nm(8.5, color: const Color(0xFF222222)), maxLines: 1),
                        ),
                      ),
                    ),
                  ]),
                ),
              ),
            ),
          ]),
          const Positioned.fill(
            child: DecoratedBox(
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  stops: [.46, .5, .54],
                  colors: [Color(0x00000000), Color(0x2E000000), Color(0x00000000)],
                ),
              ),
            ),
          ),
        ]),
      );

  // 2/3: a grid where the weak shots fade, then 412 -> 38 -> 24.
  Widget _flow() {
    const drop = ['d3', 'd4', 'd5', 'd7', 'd10', 'd2', 'd6'];
    const order = ['hyeopjae', 'd3', 'road', 'd4', 'udo', 'd5', 'haenyeo', 'd7', 'doldam', 'd10', 'seongsan', 'd2', 'hallasan', 'd6', 'tangerine'];
    Widget st(String n, String u) => Column(children: [
          Text(n, style: nm(30, w: FontWeight.w300, ls: -1.2, h: 1.1)),
          const SizedBox(height: 6),
          Text(u, style: nm(12)),
        ]);
    final arrow = Padding(
      padding: const EdgeInsets.symmetric(horizontal: 8),
      child: Text('→', style: TextStyle(fontSize: 18, color: C.ink.withValues(alpha: .35))),
    );
    return Column(mainAxisSize: MainAxisSize.min, children: [
      SizedBox(
        width: 22 * 5 + 3 * 4,
        child: Wrap(spacing: 3, runSpacing: 3, children: [
          for (final k in order)
            Opacity(
              opacity: drop.contains(k) ? .25 : 1,
              child: drop.contains(k)
                  ? ColorFiltered(colorFilter: const ColorFilter.matrix(_gray), child: Photo('$_p$k.jpg', w: 22, h: 22, r: 2))
                  : Photo('$_p$k.jpg', w: 22, h: 22, r: 2),
            ),
        ]),
      ),
      const SizedBox(height: 18),
      Row(mainAxisSize: MainAxisSize.min, children: [st('412', '장'), arrow, st('38', '장'), arrow, st('24', '쪽')]),
    ]);
  }

  // 3/3: the photo stays on the phone.
  Widget _lock() => Column(mainAxisSize: MainAxisSize.min, children: [
        Container(
          width: 120,
          height: 160,
          alignment: Alignment.center,
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(16),
            boxShadow: const [BoxShadow(color: Color(0x263C2814), blurRadius: 24, offset: Offset(0, 10))],
          ),
          child: const Photo('${_p}tangerine.jpg', w: 96, h: 120, r: 6),
        ),
        const SizedBox(height: 10),
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
          decoration: BoxDecoration(color: C.tint, borderRadius: BorderRadius.circular(12)),
          child: Text('원본은 이 폰에만 · 작은 미리보기만 AI 로', style: nm(11)),
        ),
      ]);
}

const _gray = <double>[
  .2126, .7152, .0722, 0, 0, //
  .2126, .7152, .0722, 0, 0, //
  .2126, .7152, .0722, 0, 0, //
  0, 0, 0, 1, 0,
];
