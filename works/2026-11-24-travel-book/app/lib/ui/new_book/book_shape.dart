// D3 책 모양 고르기: theme, length and text amount up front; the other 8 options behind [더 보기].
import 'package:material_ui/material_ui.dart';

import '../../core/models.dart' show BookOptions;
import '../tokens.dart';
import '../widgets.dart';
import 'draft.dart';

const _themeNotes = {
  'a': '사진을 크고 넓게', // not in the mockup (a was the recommended one there)
  'b': '숫자와 동선',
  'c': '번호 지도 안내서',
  'diary': '사진과 두세 줄',
  'mag': '큰 제목, 기사처럼',
  'album': '인화한 사진첩',
  'photo': '글 없이 사진만',
};

const _toneExample = {
  'diary': '물이 생각보다 차갑다.',
  'kind': '물이 생각보다 차가웠어요.', // guessed: the mockup only shows the diary example
  'humor': '물이 차갑다. 발가락이 먼저 항복했다.', // guessed
};

class BookShapeScreen extends StatefulWidget {
  final NewBookDraft draft; // opts edited in place
  final String recommended; // theme id
  final String reason; // why the recommended theme, e.g. "풍경 사진이 많아서"
  final int photos, days, videos;
  final String placesHint; // "협재 · 우도 · 성산 …"
  final VoidCallback onBack, onMake;
  const BookShapeScreen({
    super.key,
    required this.draft,
    required this.recommended,
    required this.reason,
    required this.photos,
    required this.days,
    required this.videos,
    required this.placesHint,
    required this.onBack,
    required this.onMake,
  });
  @override
  State<BookShapeScreen> createState() => _BookShapeScreenState();
}

class _BookShapeScreenState extends State<BookShapeScreen> {
  bool _more = false;
  BookOptions get o => widget.draft.opts;
  String get _tone => o.tone == 'auto' ? 'diary' : o.tone;

  // How many of the [더 보기] options differ from the recommended defaults.
  int get _moreChanged {
    final d = BookOptions();
    return [
      _tone != 'diary',
      o.chapterBy != d.chapterBy,
      o.people != d.people,
      !(o.extras.containsAll(['map', 'numbers', 'video'])),
      o.format != d.format,
      o.quality != d.quality,
      o.toneMatch != d.toneMatch,
    ].where((x) => x).length;
  }

  bool get _changed {
    final d = BookOptions();
    return o.theme != null || o.length != d.length || o.textAmount != d.textAmount || _moreChanged > 0;
  }

  void _set(VoidCallback f) => setState(f);

  void _extra(String k, bool on) => _set(() => on ? o.extras.add(k) : o.extras.remove(k));

  @override
  Widget build(BuildContext context) {
    final theme = o.theme ?? widget.recommended;
    return NbScaffold(
      onBack: widget.onBack,
      right: GestureDetector(
        onTap: _changed ? () => _set(() => widget.draft.opts = BookOptions()) : null,
        child: topLabel('추천으로 되돌리기', bold: _changed),
      ),
      title: '책 모양 고르기',
      titleSmall: '(안 바꿔도 돼요)',
      fade: _more,
      body: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        const OptLabel('테마', note: '표지는 예시예요', top: 0),
        GridView.count(
          crossAxisCount: 3,
          crossAxisSpacing: 6,
          mainAxisSpacing: 7,
          childAspectRatio: .92,
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          children: [for (final t in themeNames.keys) _theme(t, t == theme)],
        ),
        OptLabel('길이', note: '사진 ${fmtInt(widget.photos)}장 · ${dayWord(widget.days)}', top: 10),
        Sg([
          for (final l in lengthNames.keys) SgItem(l, lengthNames[l]!, sub: '약 ${lengthPages[l]}쪽', rec: l == 'normal'),
        ], o.length, (v) => _set(() => o.length = v)),
        const OptLabel('글의 양', top: 10),
        Sg(const [SgItem('photos', '사진만'), SgItem('caption', '짧은 캡션'), SgItem('caption_essay', '캡션 + 에세이')], o.textAmount,
            (v) => _set(() => o.textAmount = v)),
        GestureDetector(
          onTap: () => _set(() => _more = !_more),
          child: Container(
            margin: const EdgeInsets.only(top: 11),
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            decoration: BoxDecoration(borderRadius: BorderRadius.circular(14), border: Border.all(color: C.line)),
            child: Row(children: [
              Expanded(
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text('더 보기', style: ui(12.5, w: FontWeight.w700)),
                  const SizedBox(height: 1),
                  Text(
                    _more
                        ? (_moreChanged == 0 ? '모두 추천값' : '바꾼 것 $_moreChanged · 나머지는 추천값')
                        : '말투 · 장 나누기 · 넣을 쪽 · 판형 · 화질 · 영상',
                    style: ui(10.5, color: C.ink2),
                  ),
                ]),
              ),
              const SizedBox(width: 8),
              Text(_more ? '접기' : '8가지', style: nm(11, color: C.ink2)),
              Icon(_more ? Icons.expand_less : Icons.expand_more, size: 16, color: C.ink2),
            ]),
          ),
        ),
        if (_more) ..._moreOptions(),
      ]),
      foot: [Btn('이대로 책 만들기', onTap: widget.onMake)],
    );
  }

  Widget _theme(String t, bool on) {
    final rec = t == widget.recommended;
    return GestureDetector(
      onTap: () => _set(() => o.theme = t == widget.recommended ? null : t),
      child: Column(children: [
        Stack(clipBehavior: Clip.none, children: [
          Container(
            width: 54,
            height: 72,
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(2),
              boxShadow: on
                  ? const [BoxShadow(color: C.ink, spreadRadius: 4), BoxShadow(color: C.paper, spreadRadius: 2)]
                  : const [BoxShadow(color: Color(0xFFD8CDBC), spreadRadius: 1), BoxShadow(color: Color(0x2E462D14), blurRadius: 8, offset: Offset(2, 3))],
            ),
            child: Photo('assets/covers/$t.jpg', w: 54, h: 72, r: 2),
          ),
          if (rec)
            Positioned(
              top: -5,
              left: 54 / 2 + 9,
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 4),
                decoration: BoxDecoration(color: C.ink, borderRadius: BorderRadius.circular(8), border: Border.all(color: C.paper, width: 1.5)),
                child: Text('추천', style: ui(9.5, w: FontWeight.w700, color: C.paper, h: 1)),
              ),
            ),
        ]),
        const SizedBox(height: 5),
        Text(themeNames[t]!, style: ui(11.5, w: FontWeight.w700, h: 1.2)),
        Text(rec ? widget.reason : _themeNotes[t]!,
            textAlign: TextAlign.center,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: ui(10, h: 1.3, color: on ? C.ink : C.ink2, w: on ? FontWeight.w600 : FontWeight.w400)),
      ]),
    );
  }

  Widget _toggleRow(String t, String s, bool on, ValueChanged<bool> f, {bool first = false}) => Container(
        padding: EdgeInsets.only(top: first ? 2 : 9, bottom: 9),
        decoration: first ? null : const BoxDecoration(border: Border(top: BorderSide(color: C.line))),
        child: Row(children: [
          Expanded(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(t, style: ui(12.5, w: FontWeight.w600)),
              const SizedBox(height: 1),
              Text(kw(s), style: ui(10.5, color: C.ink2, h: 1.38)),
            ]),
          ),
          const SizedBox(width: 10),
          Tg(on, f),
        ]),
      );

  Widget _eg(String s) => Padding(
        padding: const EdgeInsets.only(top: 7),
        child: Text.rich(TextSpan(children: [const TextSpan(text: '예: '), TextSpan(text: s, style: ui(11, w: FontWeight.w500))]),
            style: ui(11, color: C.ink2, h: 1.45)),
      );

  List<Widget> _moreOptions() => [
        const OptLabel('말투', note: '일기를 넣으면 그 말투로'),
        Sg(const [SgItem('diary', '담담한 일기체'), SgItem('kind', '다정한 존댓말'), SgItem('humor', '유머')], _tone,
            (v) => _set(() => o.tone = v == 'diary' ? 'auto' : v)),
        _eg('"${_toneExample[_tone]}"'),
        const OptLabel('장 나누기'),
        Sg([const SgItem('day', '날짜별', sub: '첫째 날 · 둘째 날 · 셋째 날'), SgItem('place', '장소별', sub: widget.placesHint)], o.chapterBy,
            (v) => _set(() => o.chapterBy = v)),
        const OptLabel('사람 사진'),
        Sg(const [SgItem('mix', '섞어서', rec: true), SgItem('scenery', '풍경 위주'), SgItem('people', '사람 위주')], o.people,
            (v) => _set(() => o.people = v)),
        const OptLabel('넣을 쪽', note: '마지막 글은 "글의 양"에서'),
        _toggleRow('동선 지도', '사진에 든 위치로 그려요. 타임라인을 넣으면 다닌 길까지', o.extras.contains('map'), (v) => _extra('map', v), first: true),
        _toggleRow('숫자 쪽', '며칠 · 몇 곳 · 몇 km', o.extras.contains('numbers'), (v) => _extra('numbers', v)),
        _toggleRow('영상 쪽', '영상 ${widget.videos}개 중 고른 것에서 3–5초씩', o.extras.contains('video'), (v) => _extra('video', v)),
        const OptLabel('판형', note: 'PDF는 고른 판형으로 다시 짜요'),
        Sg(const [SgItem('phone', '폰 세로', sub: '화면으로 볼 때'), SgItem('a5', 'A5 세로', sub: '인쇄'), SgItem('square', '정사각', sub: '20cm · 인쇄')],
            o.format, (v) => _set(() {
                  o.format = v;
                  if (v != 'phone') o.quality = 'original'; // print sizes want the original files
                })),
        const OptLabel('사진·영상 화질'),
        Sg(const [SgItem('reduced', '조금 낮추기', sub: 'PDF 약 22MB', rec: true), SgItem('original', '원본 그대로', sub: 'PDF 약 140MB')], o.quality,
            (v) => _set(() => o.quality = v)),
        Padding(
          padding: const EdgeInsets.only(top: 7),
          child: Text('인쇄 판형을 고르면 "원본 그대로"로 바꿔 둬요.', style: ui(11, color: C.ink2, h: 1.45)),
        ),
        const SizedBox(height: 8),
        _toggleRow('사진 톤 맞추기', '끄면 찍은 색 그대로. 켜면 한 권을 같은 느낌으로 (원본 파일은 안 바뀌어요)', o.toneMatch, (v) => _set(() => o.toneMatch = v),
            first: true),
      ];
}
