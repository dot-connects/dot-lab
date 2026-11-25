// D3 꼭 넣을 사진: the chosen albums' photos by day; up to 10 are kept no matter what the picker decides.
import 'package:material_ui/material_ui.dart';

import '../tokens.dart';
import '../widgets.dart';
import 'draft.dart';

class MustPickScreen extends StatefulWidget {
  final List<(DateTime, List<PhotoRef>)> days;
  final NewBookDraft draft; // mustInclude is edited in place
  final VoidCallback onDone;
  const MustPickScreen({super.key, required this.days, required this.draft, required this.onDone});
  @override
  State<MustPickScreen> createState() => _MustPickScreenState();
}

class _MustPickScreenState extends State<MustPickScreen> {
  late final _keys = [for (final _ in widget.days) GlobalKey()];
  int _cur = 0;

  Set<String> get _sel => widget.draft.mustInclude;
  int _picked(List<PhotoRef> ps) => ps.where((p) => _sel.contains(p.id)).length;

  void _toggle(String id) {
    if (!widget.draft.toggleMust(id)) {
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(const SnackBar(content: Text('꼭 넣을 사진은 10장까지')));
    }
    setState(() {});
  }

  void _jump(int i) {
    setState(() => _cur = i);
    Scrollable.ensureVisible(_keys[i].currentContext!, duration: const Duration(milliseconds: 250));
  }

  @override
  Widget build(BuildContext context) {
    final n = _sel.length;
    return NbScaffold(
      onBack: widget.onDone,
      right: n > 0 ? topLabel('$n장 고름', bold: true) : null,
      title: '꼭 넣을 사진',
      lead: '고른 사진은 빠지지 않아요. 나머지는 알아서 골라요.',
      fade: true,
      body: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        _chips(),
        const SizedBox(height: 12),
        for (final (i, (day, ps)) in widget.days.indexed) ...[
          Container(
            key: _keys[i],
            height: 22,
            margin: EdgeInsets.only(top: i == 0 ? 0 : 10),
            child: Row(crossAxisAlignment: CrossAxisAlignment.baseline, textBaseline: TextBaseline.alphabetic, children: [
              Text('${day.month}월 ${day.day}일 ${weekdays[day.weekday - 1]}', style: ui(12.5, w: FontWeight.w600)),
              const Spacer(),
              Text('${ps.length}장 · ${_picked(ps)}장 고름', style: nm(11, color: C.ink2)),
            ]),
          ),
          GridView.count(
            crossAxisCount: 4,
            mainAxisSpacing: 4,
            crossAxisSpacing: 4,
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            children: [for (final p in ps) _tile(p)],
          ),
        ],
      ]),
      foot: [Btn(n > 0 ? '$n장 꼭 넣기' : '돌아가기', onTap: widget.onDone)],
    );
  }

  Widget _chips() {
    Widget chip(int i) {
      final (day, ps) = widget.days[i];
      final on = i == _cur, dot = _picked(ps) > 0;
      return GestureDetector(
        onTap: () => _jump(i),
        child: Container(
          height: 30,
          padding: const EdgeInsets.symmetric(horizontal: 10),
          decoration: BoxDecoration(color: on ? C.ink : C.card, borderRadius: BorderRadius.circular(15)),
          child: Row(mainAxisAlignment: MainAxisAlignment.center, mainAxisSize: MainAxisSize.min, children: [
            Text('${day.day} ${weekdays[day.weekday - 1]} ', style: nm(12, w: FontWeight.w600, color: on ? C.paper : C.ink2)),
            Text('${ps.length}', style: nm(10.5, color: (on ? C.paper : C.ink2).withValues(alpha: .8))),
            if (dot) ...[
              const SizedBox(width: 5),
              Container(width: 5, height: 5, decoration: BoxDecoration(color: on ? C.oak1 : C.oak2, shape: BoxShape.circle)),
            ],
          ]),
        ),
      );
    }

    final n = widget.days.length;
    if (n <= 4) {
      return Row(children: [
        for (var i = 0; i < n; i++) ...[if (i > 0) const SizedBox(width: 6), Expanded(child: chip(i))],
      ]);
    }
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      child: Row(children: [for (var i = 0; i < n; i++) Padding(padding: const EdgeInsets.only(right: 6), child: chip(i))]),
    );
  }

  Widget _tile(PhotoRef p) {
    final on = _sel.contains(p.id);
    return GestureDetector(
      onTap: () => _toggle(p.id),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(6),
        child: Container(
          color: on ? C.pickedBg : C.gridBg,
          child: Stack(fit: StackFit.expand, children: [
            AnimatedScale(
              scale: on ? .86 : 1,
              duration: const Duration(milliseconds: 120),
              child: Photo(p.path, r: on ? 4 : 0),
            ),
            Positioned(
              right: 4,
              top: 4,
              child: Container(
                width: 20,
                height: 20,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: on ? C.ink : const Color(0x24000000),
                  border: Border.all(color: Colors.white, width: 1.5),
                ),
                child: on ? const Icon(Icons.check_rounded, size: 13, color: C.paper) : null,
              ),
            ),
          ]),
        ),
      ),
    );
  }
}
