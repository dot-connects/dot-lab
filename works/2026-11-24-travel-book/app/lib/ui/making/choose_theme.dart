// 어떤 책으로 만들까요? (mockup r12 A1): after the photo review, always. A fan of three pages of one theme made from
// the picked photos; 7 theme chips, ‹ › and swipe to change it; length and text amount below.
import 'dart:async';
import 'dart:math' as math;

import 'package:material_ui/material_ui.dart';

import '../../core/models.dart' show BookOptions;
import '../new_book/draft.dart';
import '../tokens.dart';
import '../widgets.dart';

/// Page paper of each theme (the theme CSS).
const themePaper = {
  'a': Color(0xFFF4F3EF),
  'photo': Color(0xFFF4F2ED),
  'album': Color(0xFFEEE7DA),
  'b': Color(0xFFF4F2EC),
  'c': Color(0xFFF3EFE6),
  'diary': Color(0xFFF7F4EE),
  'mag': Color(0xFFF1EFEA),
};

/// What the recommendation looks at in the picked photos.
typedef PickStats = ({int photos, int portrait, int days, bool notes});

String recLength(int photos) => photos <= 20 ? 'short' : (photos <= 40 ? 'normal' : 'long');
String recText(String theme, bool notes) => theme == 'photo' ? 'photos' : (notes ? 'caption_essay' : 'caption');

/// One short line on why this theme fits these photos.
String themeReason(String t, PickStats s) {
  final tall = s.photos > 0 && s.portrait * 2 > s.photos, wide = s.photos > 0 && s.portrait * 3 < s.photos;
  return switch (t) {
    'a' => wide ? '가로 사진이 많아 크게 펼쳐요' : '사진을 크고 넓게',
    'photo' => '글 없이 사진으로만',
    'album' => s.photos >= 40 ? '사진이 많아 앨범처럼 촘촘히' : '인화해서 붙인 앨범처럼',
    'b' => s.days >= 3 ? '${dayWord(s.days)} 동안 들른 곳마다 한 장씩' : '들른 곳마다 한 장씩',
    'c' => '모눈 수첩에 적은 듯',
    'diary' => s.notes ? '메모를 넣어서 날마다 글이 주인공' : '날마다 쓴 글이 주인공',
    'mag' => tall ? '세로 사진이 많아 화보처럼' : '여행 기사 한 호',
    _ => '',
  };
}

const pageShadow = [BoxShadow(color: Color(0x40281A0A), blurRadius: 18, offset: Offset(0, 6))];

/// A light stand-in for a rendered page: the theme's paper with the person's photos.
/// [kind] 0 = cover (photo + big place title), 1 = one photo with margin, 2 = two photos.
class PagePreview extends StatelessWidget {
  final String theme, title;
  final int kind;
  final List<String> photos; // the first one or two are used
  final double w;
  const PagePreview({super.key, required this.theme, required this.title, required this.kind, required this.photos, required this.w});

  Widget _img(BuildContext c, String p) => ClipRRect(
        borderRadius: BorderRadius.circular(1.5),
        child: Image(image: sizedImage(c, p, w, w), fit: BoxFit.cover, width: double.infinity, height: double.infinity, gaplessPlayback: true),
      );

  @override
  Widget build(BuildContext context) {
    final m = w * .09, p0 = photos.first, p1 = photos.length > 1 ? photos[1] : photos.first;
    final words = theme != 'photo';
    Widget bars() => Padding(
          padding: EdgeInsets.only(top: m * .7),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            for (final f in const [.72, .46])
              Container(width: (w - 2 * m) * f, height: math.max(1.5, w * .018), margin: EdgeInsets.only(bottom: w * .022), color: const Color(0x332B2621)),
          ]),
        );
    final child = switch (kind) {
      0 when theme == 'mag' || theme == 'photo' => Stack(fit: StackFit.expand, children: [
          _img(context, p0),
          const DecoratedBox(
              decoration: BoxDecoration(gradient: LinearGradient(begin: Alignment.topCenter, end: Alignment.bottomCenter, stops: [.5, 1], colors: [Color(0x00000000), Color(0x73000000)]))),
          Positioned(
            left: m,
            right: m,
            bottom: m,
            child: Text(title, maxLines: 2, overflow: TextOverflow.ellipsis, style: ui(w * .13, w: FontWeight.w800, color: Colors.white, h: 1.05)),
          ),
        ]),
      0 => Padding(
          padding: EdgeInsets.all(m),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Expanded(child: _img(context, p0)),
            SizedBox(height: m * .8),
            Text(title, maxLines: 1, overflow: TextOverflow.ellipsis, style: ui(w * .11, w: FontWeight.w800, h: 1.1)),
          ]),
        ),
      1 => Padding(
          padding: EdgeInsets.all(m * 1.3),
          child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [Expanded(child: _img(context, p0)), if (words) bars()]),
        ),
      _ => Padding(
          padding: EdgeInsets.all(m),
          child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            Expanded(child: _img(context, p0)),
            SizedBox(height: m * .5),
            Expanded(child: _img(context, p1)),
            if (words) bars(),
          ]),
        ),
    };
    return Container(
      width: w,
      height: w * 4 / 3,
      clipBehavior: Clip.antiAlias,
      decoration: BoxDecoration(color: themePaper[theme] ?? C.paper, borderRadius: BorderRadius.circular(3)),
      child: child,
    );
  }
}

/// The photos page [kind] of theme number [ti] shows (offset per theme so the fans differ).
List<String> previewPhotos(List<String> photos, String theme, int ti, int kind) {
  if (photos.isEmpty) return ['assets/covers/$theme.jpg'];
  final n = photos.length, at = ti * 3 + [0, n ~/ 3, n ~/ 2][kind % 3];
  return [photos[at % n], photos[(at + 1) % n]];
}

class ChooseThemeScreen extends StatefulWidget {
  final NewBookDraft draft; // opts edited in place; theme null = [recommended]
  final String recommended;
  final List<String> photos; // picked photo paths, in time order
  final String title; // the place on the cover page
  final PickStats stats;
  final String? price; // "1,500원" on a paid book
  final VoidCallback onBack, onMake;
  final Future<void> Function()? onMore; // the other options (tone, format, …) on the old book-shape screen
  const ChooseThemeScreen({
    super.key,
    required this.draft,
    required this.recommended,
    required this.photos,
    required this.title,
    required this.stats,
    this.price,
    required this.onBack,
    required this.onMake,
    this.onMore,
  });
  @override
  State<ChooseThemeScreen> createState() => _ChooseThemeScreenState();
}

class _ChooseThemeScreenState extends State<ChooseThemeScreen> {
  late final _order = [widget.recommended, ...themeNames.keys.where((t) => t != widget.recommended)];
  late final _chipKeys = [for (final _ in _order) GlobalKey()];
  late PageController _pc;
  late int _i;
  bool _hint = true, _touched = false; // touched: the person moved it already, no nudge
  final _timers = <Timer>[];
  BookOptions get o => widget.draft.opts;

  @override
  void initState() {
    super.initState();
    _preselect();
    _i = math.max(0, _order.indexOf(o.theme ?? widget.recommended));
    _pc = PageController(initialPage: _i);
    WidgetsBinding.instance.addPostFrameCallback((_) => _nudge());
  }

  /// First visit (or after "추천으로 되돌리기" on the more screen): everything at the recommendation.
  void _preselect() {
    if (o.theme != null) return;
    o
      ..theme = widget.recommended
      ..length = recLength(widget.stats.photos)
      ..textAmount = recText(widget.recommended, widget.stats.notes);
  }

  /// Once: slide 40 px toward the next theme and back, with a hint that fades after 3 s.
  void _nudge() {
    if (!mounted) return;
    _timers.add(Timer(const Duration(seconds: 3), () => mounted ? setState(() => _hint = false) : null));
    if (MediaQuery.disableAnimationsOf(context)) return;
    _timers.add(Timer(const Duration(milliseconds: 700), () async {
      if (!mounted || !_pc.hasClients || _touched) return;
      final x = _pc.offset;
      await _pc.animateTo(x + 40, duration: const Duration(milliseconds: 380), curve: Curves.easeOut);
      if (mounted && _pc.hasClients) await _pc.animateTo(x, duration: const Duration(milliseconds: 420), curve: Curves.easeInOut);
    }));
  }

  @override
  void dispose() {
    for (final t in _timers) {
      t.cancel();
    }
    _pc.dispose();
    super.dispose();
  }

  void _select(int i) {
    final was = o.theme;
    setState(() {
      _touched = true;
      _i = i;
      o.theme = _order[i];
      if (o.theme == 'photo') {
        o.textAmount = 'photos';
      } else if (was == 'photo') {
        o.textAmount = recText(o.theme!, widget.stats.notes);
      }
    });
    final c = _chipKeys[i].currentContext;
    if (c != null) Scrollable.ensureVisible(c, alignment: .5, duration: const Duration(milliseconds: 250));
  }

  void _go(int i) {
    if (i < 0 || i >= _order.length) return;
    _touched = true;
    MediaQuery.disableAnimationsOf(context)
        ? _pc.jumpToPage(i)
        : _pc.animateToPage(i, duration: const Duration(milliseconds: 380), curve: Curves.easeOutCubic);
  }

  Widget _chip(int i) {
    final t = _order[i], on = i == _i;
    return GestureDetector(
      key: _chipKeys[i],
      onTap: () => _go(i),
      child: Container(
        margin: const EdgeInsets.only(right: 6),
        padding: const EdgeInsets.symmetric(horizontal: 11),
        alignment: Alignment.center,
        decoration: BoxDecoration(
          color: on ? C.ink : const Color(0xFFFBF8F3),
          borderRadius: BorderRadius.circular(999),
          border: Border.all(color: on ? C.ink : C.line),
        ),
        child: Text.rich(
          TextSpan(text: themeNames[t], children: [
            if (t == widget.recommended) TextSpan(text: ' 추천', style: ui(10, w: FontWeight.w700, color: on ? C.oak1 : C.oak2)),
          ]),
          style: ui(12.5, w: FontWeight.w600, color: on ? C.paper : C.ink),
        ),
      ),
    );
  }

  Widget _fan(int ti, double h) {
    // pages .8 of the height from the top: the lowered, tilted side pages and their shadows stay inside the PageView
    final t = _order[ti], w = h * .8 * .75;
    Widget page(int kind) => Container(
          decoration: const BoxDecoration(boxShadow: pageShadow),
          child: PagePreview(theme: t, title: widget.title, kind: kind, photos: previewPhotos(widget.photos, t, ti, kind), w: w),
        );
    Widget side(int kind, double dir) =>
        Transform.translate(offset: Offset(dir * w * .5, h * .07), child: Transform.rotate(angle: dir * .105, child: page(kind)));
    return Padding(
      padding: EdgeInsets.only(top: h * .03),
      child: Stack(alignment: Alignment.topCenter, children: [side(1, -1), side(2, 1), page(0)]),
    );
  }

  Widget _arrow(bool next) {
    final on = next ? _i < _order.length - 1 : _i > 0;
    return GestureDetector(
      onTap: on ? () => _go(_i + (next ? 1 : -1)) : null,
      child: Opacity(
        opacity: on ? 1 : .3,
        child: Container(
          width: 36,
          height: 36,
          decoration: BoxDecoration(
            color: const Color(0xEEFBF8F3),
            shape: BoxShape.circle,
            boxShadow: const [BoxShadow(color: Color(0x2E281A0A), blurRadius: 8, offset: Offset(0, 2))],
          ),
          child: Icon(next ? Icons.chevron_right : Icons.chevron_left, size: 22, color: C.ink),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final s = widget.stats, t = _order[_i], rec = t == widget.recommended;
    final mq = MediaQuery.of(context);
    final fanH = (mq.size.height - mq.padding.vertical - 470).clamp(160.0, 360.0);
    final lenRec = recLength(s.photos), textRec = recText(t, s.notes), noWords = t == 'photo';
    return NbScaffold(
      onBack: widget.onBack,
      title: '어떤 책으로 만들까요?',
      lead: '고른 ${fmtInt(s.photos)}장으로 미리 만들어 봤어요',
      body: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        SizedBox(
          height: 32,
          // all 7 built (not a lazy list), so the chosen one can always be scrolled into view
          child: SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            clipBehavior: Clip.none,
            child: Row(crossAxisAlignment: CrossAxisAlignment.stretch, children: [for (var i = 0; i < _order.length; i++) _chip(i)]),
          ),
        ),
        const SizedBox(height: 6),
        SizedBox(
          height: fanH,
          child: Stack(clipBehavior: Clip.none, children: [
            Positioned.fill(
              left: -20,
              right: -20,
              child: PageView.builder(controller: _pc, itemCount: _order.length, onPageChanged: _select, itemBuilder: (_, i) => _fan(i, fanH)),
            ),
            Positioned(left: -6, top: 0, bottom: 0, child: Center(child: _arrow(false))),
            Positioned(right: -6, top: 0, bottom: 0, child: Center(child: _arrow(true))),
            Positioned(
              left: 0,
              right: 0,
              top: -4, // over the top edge of the pages, not the cover title
              child: IgnorePointer(
                child: AnimatedOpacity(
                  opacity: _hint ? 1 : 0,
                  duration: const Duration(milliseconds: 600),
                  child: Center(
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                      decoration: BoxDecoration(color: const Color(0xD92B2621), borderRadius: BorderRadius.circular(999)),
                      child: Text('옆으로 넘겨 다른 테마 보기', style: ui(11.5, w: FontWeight.w600, color: C.paper)),
                    ),
                  ),
                ),
              ),
            ),
          ]),
        ),
        const SizedBox(height: 8),
        Row(mainAxisAlignment: MainAxisAlignment.center, children: [
          Text(themeNames[t]!, style: ui(17, w: FontWeight.w700, h: 1.3)),
          if (rec)
            Container(
              margin: const EdgeInsets.only(left: 6),
              padding: const EdgeInsets.fromLTRB(6, 2, 6, 3),
              decoration: BoxDecoration(color: C.ink, borderRadius: BorderRadius.circular(6)),
              child: Text('추천', style: ui(10.5, w: FontWeight.w700, color: C.paper)),
            ),
        ]),
        Text(themeReason(t, s), textAlign: TextAlign.center, style: ui(12, color: C.ink2, h: 1.4)),
        OptLabel('길이', note: '사진 ${fmtInt(s.photos)}장 · ${dayWord(s.days)}', top: 10),
        Sg([for (final l in lengthNames.keys) SgItem(l, lengthNames[l]!, sub: '${lengthPages[l]}쪽', rec: l == lenRec)], o.length,
            (v) => setState(() => o.length = v)),
        OptLabel('글', note: noWords ? '사진만 테마는 글 없이 만들어요' : null, top: 10),
        IgnorePointer(
          ignoring: noWords,
          child: Opacity(
            opacity: noWords ? .45 : 1,
            child: Sg([
              for (final (k, l) in const [('photos', '사진만'), ('caption', '짧은 캡션'), ('caption_essay', '캡션 + 글')]) SgItem(k, l, rec: k == textRec),
            ], o.textAmount, (v) => setState(() => o.textAmount = v)),
          ),
        ),
        if (widget.onMore != null)
          Align(
            alignment: Alignment.centerRight,
            child: GestureDetector(
              behavior: HitTestBehavior.opaque,
              onTap: () async {
                await widget.onMore!();
                if (!mounted) return;
                setState(_preselect);
                final i = _order.indexOf(o.theme ?? widget.recommended);
                if (i >= 0 && i != _i) _pc.jumpToPage(i);
              },
              child: Padding(padding: const EdgeInsets.fromLTRB(8, 10, 2, 2), child: Text('말투·판형·영상 더 보기 ›', style: ui(12, w: FontWeight.w600, color: C.ink2))),
            ),
          ),
      ]),
      foot: [Btn(widget.price == null ? '이 모양으로 책 만들기' : '이 모양으로 책 만들기 · ${widget.price}', onTap: widget.onMake)],
    );
  }
}
