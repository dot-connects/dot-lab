// D5 책 보기: top bar [다듬기][내보내기] around the book runtime (a WebView, passed in as [page]),
// a one-time tip at the bottom or a failure banner at the top, and the page long-press sheet.
import 'package:material_ui/material_ui.dart';

import 'tokens.dart';

const bookBg = Color(0xFFE6DDCF);
const maxRewrites = 20; // [글 다시 쓰기] per book

class BookTip {
  final String text;
  final bool soft; // soft = paper card without a button
  final VoidCallback? onOk; // shows "확인"
  const BookTip(this.text, {this.soft = false, this.onOk});
}

class BookBanner {
  final String title, text, primary, secondary;
  final VoidCallback onPrimary, onSecondary;
  const BookBanner(this.title, this.text,
      {required this.primary, required this.secondary, required this.onPrimary, required this.onSecondary});
}

class BookView extends StatelessWidget {
  final String title;
  final Widget page; // the book runtime
  final VoidCallback onBack, onEdit, onExport;
  final BookTip? tip;
  final BookBanner? banner;
  final bool spread; // two pages per screen
  final VoidCallback? onSpread; // toggles one page / two pages
  final bool chrome; // the bars are showing (a tap in the middle of the book toggles them)
  final String? pageLabel; // "3 / 24", shown with the bars
  const BookView({
    this.chrome = true,
    this.pageLabel,
    this.spread = false,
    this.onSpread,
    super.key,
    required this.title,
    required this.page,
    required this.onBack,
    required this.onEdit,
    required this.onExport,
    this.tip,
    this.banner,
  });

  @override
  Widget build(BuildContext context) {
    Widget em(String t, bool f, VoidCallback onTap) => GestureDetector(
          onTap: onTap,
          child: Container(
            height: 30,
            padding: const EdgeInsets.symmetric(horizontal: 11),
            alignment: Alignment.center,
            decoration: BoxDecoration(
              color: f ? C.ink : null,
              borderRadius: BorderRadius.circular(15),
              border: f ? null : Border.all(color: C.ink, width: 1.5),
            ),
            child: Text(t, style: ui(12.5, w: FontWeight.w600, color: f ? C.paper : C.ink)),
          ),
        );
    final b = banner, t = tip;
    return Scaffold(
      backgroundColor: bookBg,
      body: SafeArea(
        child: Stack(children: [
          Positioned.fill(child: page),
          // the bar floats over the book (like an e-reader) and slides away while reading
          Positioned(
            left: 0,
            right: 0,
            top: 0,
            child: AnimatedSlide(
            offset: chrome ? Offset.zero : const Offset(0, -1.2),
            duration: const Duration(milliseconds: 220),
            curve: Curves.easeOutCubic,
            child: Container(
            height: 44,
            decoration: const BoxDecoration(color: bookBg, boxShadow: [BoxShadow(color: Color(0x1A3C2814), blurRadius: 8, offset: Offset(0, 2))]),
            child: Padding(
              padding: const EdgeInsets.only(left: 6, right: 12),
              child: Row(children: [
                GestureDetector(
                  onTap: onBack,
                  behavior: HitTestBehavior.opaque,
                  child: const SizedBox(width: 36, height: 44, child: Center(child: Icon(Icons.arrow_back_ios_new_rounded, size: 18, color: C.ink))),
                ),
                Expanded(child: Text(title, style: ui(14.5, w: FontWeight.w700), maxLines: 1, overflow: TextOverflow.ellipsis)),
                if (onSpread != null) ...[
                  GestureDetector(
                    onTap: onSpread,
                    behavior: HitTestBehavior.opaque,
                    child: SizedBox(
                      height: 44,
                      child: Row(children: [
                        Icon(spread ? Icons.auto_stories_outlined : Icons.crop_portrait, size: 18, color: C.ink),
                        const SizedBox(width: 3),
                        Text(spread ? '두 쪽' : '한 쪽', style: ui(12, w: FontWeight.w600)),
                      ]),
                    ),
                  ),
                  const SizedBox(width: 10),
                ],
                em('다듬기', false, onEdit),
                const SizedBox(width: 6),
                em('내보내기', true, onExport),
              ]),
            ),
          ),
          ),
          ),
          if (pageLabel != null)
            Positioned(
              left: 0,
              right: 0,
              bottom: 14,
              child: IgnorePointer(
                child: AnimatedOpacity(
                  opacity: chrome ? 1 : 0,
                  duration: const Duration(milliseconds: 220),
                  child: Center(
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
                      decoration: BoxDecoration(color: C.ink.withValues(alpha: .78), borderRadius: BorderRadius.circular(14)),
                      child: Text(pageLabel!, style: nm(12.5, color: C.paper, w: FontWeight.w600)),
                    ),
                  ),
                ),
              ),
            ),
              if (b != null)
                Positioned(
                  left: 14,
                  right: 14,
                  top: 52,
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 11),
                    decoration: BoxDecoration(color: C.ink, borderRadius: BorderRadius.circular(14)),
                    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      Text(b.title, style: ui(13, w: FontWeight.w700, color: C.paper)),
                      const SizedBox(height: 2),
                      Text(kw(b.text), style: ui(11.5, color: C.paper, h: 1.5)),
                      const SizedBox(height: 9),
                      Row(children: [
                        _pill(b.primary, b.onPrimary, ghost: false),
                        const SizedBox(width: 6),
                        _pill(b.secondary, b.onSecondary, ghost: true),
                      ]),
                    ]),
                  ),
                ),
              if (t != null)
                Positioned(
                  left: 18,
                  right: 18,
                  bottom: 20,
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 11),
                    decoration: BoxDecoration(
                      color: t.soft ? C.paper : C.ink,
                      borderRadius: BorderRadius.circular(14),
                      boxShadow: t.soft ? const [BoxShadow(color: Color(0x1F3C2814), blurRadius: 12, offset: Offset(0, 4))] : null,
                    ),
                    child: Row(children: [
                      Expanded(child: Text(kw(t.text), style: ui(12, h: 1.45, color: t.soft ? C.body : C.paper))),
                      if (t.onOk != null) ...[
                        const SizedBox(width: 10),
                        GestureDetector(onTap: t.onOk, child: Text('확인', style: ui(12, w: FontWeight.w600, color: C.oak1))),
                      ],
                    ]),
                  ),
                ),
        ]),
      ),
    );
  }

  Widget _pill(String t, VoidCallback f, {required bool ghost}) => GestureDetector(
        onTap: f,
        child: Container(
          height: 32,
          padding: const EdgeInsets.symmetric(horizontal: 14),
          alignment: Alignment.center,
          decoration: BoxDecoration(
            color: ghost ? null : C.paper,
            borderRadius: BorderRadius.circular(16),
            border: ghost ? Border.all(color: C.paper.withValues(alpha: .6), width: 1.5) : null,
          ),
          child: Text(t, style: ui(12, w: FontWeight.w600, color: ghost ? C.paper : C.ink)),
        ),
      );
}

/// Long-press on a page: [사진 바꾸기] [글 다시 쓰기]. Show with showSheet(context, (_) => PageSheet(...)).
class PageSheet extends StatelessWidget {
  final String heading; // "2쪽 · 협재 해변"
  final String when; // "6.12 금 13:20"
  final int samePlace; // photos to swap from
  final int rewrites; // used so far in this book
  final VoidCallback onSwapPhoto, onRewrite;
  const PageSheet({
    super.key,
    required this.heading,
    required this.when,
    required this.samePlace,
    required this.rewrites,
    required this.onSwapPhoto,
    required this.onRewrite,
  });

  @override
  Widget build(BuildContext context) {
    final spent = rewrites >= maxRewrites;
    Widget opt(IconData ic, String t, String s, VoidCallback? f) => Opacity(
          opacity: f == null ? .45 : 1,
          child: GestureDetector(
            onTap: f,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 11),
              decoration: cardDeco(color: C.card, r: 14),
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
              ]),
            ),
          ),
        );
    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, mainAxisSize: MainAxisSize.min, children: [
      Padding(
        padding: const EdgeInsets.only(bottom: 10),
        child: Row(crossAxisAlignment: CrossAxisAlignment.baseline, textBaseline: TextBaseline.alphabetic, children: [
          Expanded(child: Text(heading, style: ui(16, w: FontWeight.w700, h: 1.35))),
          Text(when, style: nm(11, color: C.ink2)),
        ]),
      ),
      opt(Icons.swap_horiz, '사진 바꾸기', '같은 곳·같은 날 사진 $samePlace장 중에서. 빼기도 여기서', onSwapPhoto),
      const SizedBox(height: 8),
      opt(Icons.edit_outlined, '글 다시 쓰기', spent ? '이 책은 다시 쓰기를 다 썼어요' : '이 쪽 글만 새로 받아요. 5초쯤 · 직접 고치려면 [다듬기]',
          spent ? null : onRewrite),
    ]);
  }
}
