// D3 3/3 더 넣을 것: optional side roads as rows (who/what kind of trip — r12 concept-input A, a row + sheet —
// must-include, notes, timeline) and the estimate before [책 만들기].
import 'package:material_ui/material_ui.dart';

import '../tokens.dart';
import '../widgets.dart';
import '../../core/models.dart' show BookOptions;
import 'draft.dart';

class ExtrasScreen extends StatelessWidget {
  final NewBookDraft draft;
  final int photos; // photos in the chosen albums and range
  final List<String> mustThumbs; // paths of the chosen must-include photos
  final int freeLeft;
  final VoidCallback onBack, onMust, onNotes, onTimeline, onMake;
  const ExtrasScreen({
    super.key,
    required this.draft,
    required this.photos,
    this.mustThumbs = const [],
    required this.freeLeft,
    required this.onBack,
    required this.onMust,
    required this.onNotes,
    required this.onTimeline,
    required this.onMake,
  });

  @override
  Widget build(BuildContext context) {
    final o = draft.opts;
    final must = draft.mustInclude.length;
    return NbScaffold(
      onBack: onBack,
      right: topLabel('3 / 3'),
      title: '더 넣을 게 있어요?',
      titleSmall: '(없어도 돼요)',
      body: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        StatefulBuilder(builder: (context, set) {
          final said = [if (o.who != null) o.who!, if (o.about.isNotEmpty) o.about];
          return _row(Icons.favorite_border_rounded, '누구랑 · 어떤 여행', said.isEmpty ? '넣으면 사진 고르기와 글에 써요' : said.join(' · '), said.isNotEmpty,
              () => showSheet<void>(context, (c) => _ConceptSheet(opts: o)).then((_) => set(() {})));
        }),
        const SizedBox(height: 8),
        _row(Icons.check_box_outlined, '꼭 넣을 사진', must > 0 ? '$must장 골랐어요' : '빠지면 안 되는 사진이 있으면 골라 두세요', must > 0, onMust,
            trail: mustThumbs.isEmpty ? null : _stack()),
        const SizedBox(height: 8),
        _row(Icons.edit_outlined, '일기·메모',
            draft.notes.isEmpty ? '글·파일·손글씨 사진. 넣으면 그 말투로 글을 써요' : '${draft.notes.length}개 넣었어요', draft.notes.isNotEmpty, onNotes),
        const SizedBox(height: 8),
        _row(Icons.route_outlined, '구글 타임라인',
            draft.timeline == null ? '다닌 길을 지도 쪽에. 내보내는 법은 그림으로 안내' : '넣었어요 · 다닌 길을 지도 쪽에', draft.timeline != null, onTimeline),
        Container(
          margin: const EdgeInsets.only(top: 12),
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(borderRadius: BorderRadius.circular(14), border: Border.all(color: C.line)),
          child: Text.rich(
            TextSpan(children: [
              TextSpan(text: kw('사진 ${fmtInt(photos)}장에서 잘 나온 것을 고르고, 그 사진으로 책 모양을 골라요. 2~3분쯤 걸려요.\n이번 책: ')),
              if (freeLeft > 0) ...[
                TextSpan(text: '무료', style: ui(12, w: FontWeight.w700)),
                const TextSpan(text: ' (처음 2권) · '),
                TextSpan(text: kw('그다음부터 1권 1,500원')),
              ] else ...[
                TextSpan(text: '1,500원', style: nm(12, w: FontWeight.w700)),
                const TextSpan(text: ' · 무료 2권을 다 썼어요'),
              ],
            ]),
            style: ui(12, color: C.body, h: 1.55),
          ),
        ),
      ]),
      foot: [Btn(freeLeft > 0 ? '사진 고르기 시작' : '사진 고르기 시작 · 1,500원', onTap: onMake)],
    );
  }

  Widget _stack() => SizedBox(
        width: 30 + 21.0 * (mustThumbs.take(3).length - 1),
        height: 30,
        child: Stack(children: [
          for (final (i, p) in mustThumbs.take(3).indexed)
            Positioned(
              left: 21.0 * i,
              child: Container(
                decoration: BoxDecoration(borderRadius: BorderRadius.circular(9), border: Border.all(color: C.cardOn, width: 2)),
                child: Photo(p, w: 30, h: 30, r: 7),
              ),
            ),
        ]),
      );

  Widget _row(IconData ic, String t, String s, bool fill, VoidCallback onTap, {Widget? trail}) => GestureDetector(
        onTap: onTap,
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 11),
          decoration: cardDeco(color: fill ? C.cardOn : C.card, r: 14),
          child: Row(children: [
            Container(
              width: 36,
              height: 36,
              decoration: BoxDecoration(color: C.paper, borderRadius: BorderRadius.circular(10)),
              child: Icon(ic, size: 18, color: C.ink),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(t, style: ui(13.5, w: FontWeight.w700)),
                const SizedBox(height: 1),
                Text(kw(s), style: ui(11, color: C.ink2, h: 1.38)),
              ]),
            ),
            if (trail != null) ...[const SizedBox(width: 12), trail],
            const SizedBox(width: 12),
            Text('›', style: ui(16, color: C.ink2)),
          ]),
        ),
      );
}

const whoChoices = ['혼자', '연인·부부', '친구', '가족', '부모님'];

class _ConceptSheet extends StatelessWidget {
  final BookOptions opts;
  const _ConceptSheet({required this.opts});
  @override
  Widget build(BuildContext context) => Padding(
        padding: EdgeInsets.only(bottom: MediaQuery.viewInsetsOf(context).bottom),
        child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          Text('누구랑, 어떤 여행이었어요?', style: ui(17, w: FontWeight.w700)),
          Padding(
            padding: const EdgeInsets.only(top: 2, bottom: 14),
            child: Text(kw('사진 고르기와 글에만 써요. 책에 이 말이 그대로 나오진 않아요'), style: ui(12, color: C.ink2, h: 1.45)),
          ),
          ConceptBox(opts: opts),
          const SizedBox(height: 18),
          Btn('넣기', onTap: () => Navigator.of(context).pop()),
        ]),
      );
}

/// 누구랑 + 어떤 여행: chips, "직접" for other company, one free line. Writes straight into the draft's BookOptions
/// (who / about); a chip tapped again or an emptied line clears it.
class ConceptBox extends StatefulWidget {
  final BookOptions opts;
  const ConceptBox({super.key, required this.opts});
  @override
  State<ConceptBox> createState() => _ConceptBoxState();
}

class _ConceptBoxState extends State<ConceptBox> {
  late final TextEditingController _who, _about;
  late bool _own = widget.opts.who != null && !whoChoices.contains(widget.opts.who);

  @override
  void initState() {
    super.initState();
    _who = TextEditingController(text: _own ? widget.opts.who : '');
    _about = TextEditingController(text: widget.opts.about);
  }

  @override
  void dispose() {
    _who.dispose();
    _about.dispose();
    super.dispose();
  }

  Widget _chip(String t, bool on, VoidCallback tap) => GestureDetector(
        onTap: tap,
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 7),
          decoration: BoxDecoration(
            color: on ? C.ink : const Color(0xFFFBF8F3),
            borderRadius: BorderRadius.circular(999),
            border: Border.all(color: on ? C.ink : C.line),
          ),
          child: Text(t, style: ui(13, color: on ? C.paper : C.ink)),
        ),
      );

  InputDecoration _box(String hint) => InputDecoration(
        hintText: hint,
        hintStyle: ui(13.5, color: const Color(0xFFADA395)),
        isDense: true,
        filled: true,
        fillColor: const Color(0xFFFBF8F3),
        contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 11),
        border: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: const BorderSide(color: C.line)),
        enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: const BorderSide(color: C.line)),
        focusedBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: const BorderSide(color: C.ink2)),
      );

  @override
  Widget build(BuildContext context) {
    final o = widget.opts;
    // Two questions, each with its own
    // label (operator 11-27: one label over chips + line read as "the line is for 누구랑" and got skipped)
    Widget label(String n, String t) => Row(children: [
          Container(
            width: 17,
            height: 17,
            alignment: Alignment.center,
            decoration: const BoxDecoration(color: C.ink, shape: BoxShape.circle),
            child: Text(n, style: nm(10, w: FontWeight.w700, color: C.paper, h: 1)),
          ),
          const SizedBox(width: 6),
          Text(t, style: ui(13, w: FontWeight.w700)),
        ]);
    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      label('1', '누구랑 갔어요?'),
      const SizedBox(height: 6),
      Wrap(spacing: 6, runSpacing: 6, children: [
        for (final w in whoChoices)
          _chip(w, !_own && o.who == w, () => setState(() {
                _own = false;
                o.who = o.who == w ? null : w;
              })),
        _chip('직접', _own, () => setState(() {
              _own = !_own;
              o.who = _own && _who.text.trim().isNotEmpty ? _who.text.trim() : null;
            })),
      ]),
      if (_own) ...[
        const SizedBox(height: 6),
        TextField(
          controller: _who,
          maxLength: 20,
          style: ui(13.5),
          decoration: _box('누구랑? 예: 회사 동기 셋, 강아지랑').copyWith(counterText: ''),
          onChanged: (v) => o.who = v.trim().isEmpty ? null : v.trim(),
        ),
      ],
      const SizedBox(height: 14),
      label('2', '어떤 여행이었어요?'),
      const SizedBox(height: 6),
      TextField(
        controller: _about,
        maxLength: 40,
        style: ui(13.5),
        decoration: _box('한 줄로. 예: 먹으러 간 여행 · 엄마 환갑').copyWith(counterText: ''),
        onChanged: (v) => o.about = v.trim(),
      ),
    ]);
  }
}
