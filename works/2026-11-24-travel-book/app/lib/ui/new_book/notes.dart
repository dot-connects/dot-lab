// D3 일기·메모 넣기: paste / file / handwriting, then the list of what was added.
import 'package:material_ui/material_ui.dart';

import '../failures.dart';
import '../tokens.dart';
import '../widgets.dart';
import 'draft.dart';

class NotesScreen extends StatelessWidget {
  final List<NoteItem> items;
  final VoidCallback onBack, onPaste, onFile, onHandwriting, onDone;
  final ValueChanged<int> onTapItem; // view or remove
  const NotesScreen({
    super.key,
    required this.items,
    required this.onBack,
    required this.onPaste,
    required this.onFile,
    required this.onHandwriting,
    required this.onTapItem,
    required this.onDone,
  });

  @override
  Widget build(BuildContext context) {
    Widget way(IconData ic, String t, String s, VoidCallback f) => Expanded(
          child: GestureDetector(
            onTap: f,
            child: Container(
              padding: const EdgeInsets.fromLTRB(4, 12, 4, 10),
              decoration: cardDeco(color: C.card, r: 14),
              child: Column(children: [
                Icon(ic, size: 18, color: C.ink),
                const SizedBox(height: 6),
                Text(t, style: ui(12, w: FontWeight.w600)),
                const SizedBox(height: 6),
                Text(s, textAlign: TextAlign.center, style: ui(10, color: C.ink2, h: 1.3)),
              ]),
            ),
          ),
        );
    final n = items.where((i) => !i.bad).length;
    return NbScaffold(
      onBack: onBack,
      right: n > 0 ? topLabel('$n개 넣음', bold: true) : null,
      title: '일기·메모 넣기',
      lead: '넣으면 그날 일에 맞춰, 그 말투로 글을 써요.',
      body: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Row(children: [
          way(Icons.content_paste_outlined, '붙여 넣기', '글을 그대로', onPaste),
          const SizedBox(width: 6),
          way(Icons.description_outlined, '파일 고르기', 'txt · md · html · pdf', onFile),
          const SizedBox(width: 6),
          way(Icons.photo_camera_outlined, '손글씨', '찍거나 사진에서', onHandwriting),
        ]),
        if (items.isNotEmpty) ...[
          const OptLabel('넣은 것', note: '누르면 빼거나 볼 수 있어요', top: 18),
          for (final (i, it) in items.indexed) ...[
            if (i > 0) const SizedBox(height: 8),
            if (it.bad)
              BadRow('못 읽은 파일: ${it.title}', it.detail, onTap: () => onTapItem(i))
            else
            GestureDetector(
              onTap: () => onTapItem(i),
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 11),
                decoration: cardDeco(color: C.card, r: 14),
                child: Row(children: [
                  it.thumb != null
                      ? Photo(it.thumb!, w: 36, h: 36, r: 7)
                      : Container(
                          width: 36,
                          height: 36,
                          decoration: BoxDecoration(color: C.paper, borderRadius: BorderRadius.circular(10)),
                          child: const Icon(Icons.description_outlined, size: 18, color: C.ink),
                        ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      Text(it.title, style: ui(13.5, w: FontWeight.w700)),
                      Text(kw(it.detail), style: ui(11, color: C.ink2, h: 1.38)),
                    ]),
                  ),
                  const SizedBox(width: 12),
                  const Ck(CkState.on, size: 20),
                ]),
              ),
            ),
          ],
        ],
        if (items.any((i) => i.bad && i.title.toLowerCase().endsWith('.hwp')))
          Padding(
            padding: const EdgeInsets.only(top: 12),
            child: Text('한글 파일은 PDF로 저장해서 다시 넣어 보세요.', style: ui(11, color: C.ink2, h: 1.45)),
          ),
        Container(
          margin: const EdgeInsets.only(top: 14),
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 11),
          decoration: cardDeco(color: C.card, r: 14),
          child: Text(kw('넣은 글과 손글씨 사진은 글을 쓸 때만 AI로 보내요. 어디에도 저장하지 않아요.'), style: ui(11.5, color: C.body, h: 1.5)),
        ),
      ]),
      foot: [Btn(n > 0 ? '$n개 넣기' : '돌아가기', onTap: onDone)],
    );
  }
}
