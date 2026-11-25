// D3 2/3 앨범: every album starts on; albums with no photo in the range are dimmed and can't be tapped.
import 'package:material_ui/material_ui.dart';

import '../tokens.dart';
import '../widgets.dart';
import 'draft.dart';

class AlbumsScreen extends StatefulWidget {
  final List<Album> albums;
  final Set<String>? selected; // null = all albums that have photos in the range
  final VoidCallback onBack;
  final void Function(Set<String> ids, int photos) onNext;
  const AlbumsScreen({super.key, required this.albums, this.selected, required this.onBack, required this.onNext});
  @override
  State<AlbumsScreen> createState() => _AlbumsScreenState();
}

class _AlbumsScreenState extends State<AlbumsScreen> {
  late final _usable = [for (final a in widget.albums) if (a.inRange > 0) a.id];
  late final Set<String> _sel = {...(widget.selected ?? _usable)};

  @override
  Widget build(BuildContext context) {
    // Android keeps each photo in exactly one album (bucket), so the sum has no double counting.
    final n = widget.albums.where((a) => _sel.contains(a.id)).fold(0, (s, a) => s + a.inRange);
    final all = _sel.length == _usable.length;
    final state = _sel.isEmpty ? CkState.off : (all ? CkState.on : CkState.some);
    return NbScaffold(
      onBack: widget.onBack,
      right: topLabel('2 / 3'),
      title: '어느 앨범에서 가져올까요?',
      body: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        GestureDetector(
          behavior: HitTestBehavior.opaque,
          onTap: () => setState(() => all ? _sel.clear() : _sel.addAll(_usable)),
          child: Padding(
            padding: const EdgeInsets.fromLTRB(8, 2, 8, 12),
            child: Row(children: [
              Expanded(
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text('전체 선택', style: ui(13.5, w: FontWeight.w700)),
                  Text(all ? '이 기간 사진이 있는 앨범 ${_usable.length}개' : '앨범 ${_usable.length}개 중 ${_sel.length}개 고름',
                      style: ui(11, color: C.ink2)),
                ]),
              ),
              Ck(state),
            ]),
          ),
        ),
        for (final a in widget.albums) ...[_row(a), const SizedBox(height: 8)],
      ]),
      foot: [Btn('${fmtInt(n)}장으로 다음', onTap: n == 0 ? null : () => widget.onNext({..._sel}, n))],
    );
  }

  Widget _row(Album a) {
    final zero = a.inRange == 0, on = _sel.contains(a.id);
    return Opacity(
      opacity: zero ? .42 : 1,
      child: GestureDetector(
        onTap: zero ? null : () => setState(() => on ? _sel.remove(a.id) : _sel.add(a.id)),
        child: Container(
          padding: const EdgeInsets.all(8),
          decoration: BoxDecoration(color: on ? C.cardOn : C.card, borderRadius: BorderRadius.circular(14)),
          child: Row(children: [
            a.thumb == null
                ? Container(width: 46, height: 46, decoration: BoxDecoration(color: C.gridBg, borderRadius: BorderRadius.circular(8)))
                : Photo(a.thumb!, w: 46, h: 46, r: 8),
            const SizedBox(width: 10),
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(a.name, style: ui(13.5, w: FontWeight.w700), maxLines: 1, overflow: TextOverflow.ellipsis),
                Text(zero ? '이 기간 0' : '이 기간 ${fmtInt(a.inRange)} · 전체 ${fmtInt(a.total)}', style: nm(11, color: C.ink2)),
              ]),
            ),
            const SizedBox(width: 10),
            Ck(on ? CkState.on : CkState.off, ring: zero ? C.zeroRing : C.ink2),
          ]),
        ),
      ),
    );
  }
}
