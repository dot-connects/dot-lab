// D4 둘 중 하나: only the pairs the picker could not settle. Tapping one moves to the next question.
import 'package:material_ui/material_ui.dart';

import '../new_book/draft.dart';
import '../tokens.dart';
import '../widgets.dart';

class PairAsk {
  final PhotoRef a, b;
  final String aTime, bTime; // "13:20"
  final String label; // "협재 해변 · 6월 12일"
  const PairAsk(this.a, this.b, {required this.aTime, required this.bTime, required this.label});
}

class PairsScreen extends StatefulWidget {
  final List<PairAsk> pairs;
  final VoidCallback onBack;
  final VoidCallback onLeaveToApp; // [다 맡기기]: the picker's own choice stands for the rest
  final ValueChanged<List<String>> onDone; // the kept id of every pair, in order
  const PairsScreen({super.key, required this.pairs, required this.onBack, required this.onLeaveToApp, required this.onDone});
  @override
  State<PairsScreen> createState() => _PairsScreenState();
}

class _PairsScreenState extends State<PairsScreen> {
  final _kept = <String>[];
  int get _i => _kept.length;

  void _choose(String id) {
    _kept.add(id);
    if (_kept.length == widget.pairs.length) {
      widget.onDone([..._kept]);
    } else {
      setState(() {});
    }
  }

  @override
  Widget build(BuildContext context) {
    final n = widget.pairs.length;
    final p = widget.pairs[_i.clamp(0, n - 1)];
    Widget side(PhotoRef r, String t) => GestureDetector(
          onTap: () => _choose(r.id),
          child: SizedBox(
            height: 172,
            child: Stack(fit: StackFit.expand, children: [
              Photo(r.path, r: 12),
              Positioned(
                left: 8,
                bottom: 8,
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 5),
                  decoration: BoxDecoration(color: const Color(0xB8181410), borderRadius: BorderRadius.circular(10)),
                  child: Text(t, style: nm(11, w: FontWeight.w600, color: Colors.white)),
                ),
              ),
            ]),
          ),
        );
    return NbScaffold(
      onBack: () => _i == 0 ? widget.onBack() : setState(_kept.removeLast),
      right: GestureDetector(onTap: widget.onLeaveToApp, child: topLabel('다 맡기기', bold: true)),
      title: '어느 쪽을 넣을까요?',
      body: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        side(p.a, p.aTime),
        const SizedBox(height: 8),
        side(p.b, p.bTime),
        const SizedBox(height: 12),
        Row(children: [
          Expanded(child: Text(p.label, style: ui(12, w: FontWeight.w600))),
          for (var d = 0; d < n; d++)
            Container(
              margin: const EdgeInsets.only(left: 5),
              width: d == _i ? 18 : 6,
              height: 6,
              decoration: BoxDecoration(color: C.ink.withValues(alpha: d == _i ? 1 : .2), borderRadius: BorderRadius.circular(3)),
            ),
        ]),
        const SizedBox(height: 14),
        Text(kw('누르면 다음 질문으로 넘어가요. $n개 중 ${_i + 1}번째. 안 고른 쪽은 다음 화면에서 다시 바꿀 수 있어요.'),
            style: ui(11.5, color: C.ink2, h: 1.5)),
      ]),
    );
  }
}
