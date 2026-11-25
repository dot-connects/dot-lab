// D5 실패할 때 (mockup §9). Two are screens here, two are book-view banners; the bad note file lives in
// notes.dart (NoteItem.bad) and "일부만 허용" in dates.dart (DatesScreen.partial).
import 'package:material_ui/material_ui.dart';

import 'book_view.dart';
import 'tokens.dart';
import 'widgets.dart';

/// Fewer than 5 photos in the range: offer the widened range, already counted.
class FewPhotosScreen extends StatelessWidget {
  final List<String> thumbs;
  final String wider; // "8월 14–16일"
  final int widerCount;
  final VoidCallback onClose, onWiden, onRedo;
  const FewPhotosScreen({
    super.key,
    required this.thumbs,
    required this.wider,
    required this.widerCount,
    required this.onClose,
    required this.onWiden,
    required this.onRedo,
  });

  @override
  Widget build(BuildContext context) => NbScaffold(
        back: '✕',
        onBack: onClose,
        right: topLabel('1 / 3'),
        title: '이 기간엔 사진이 ${thumbs.length}장뿐이에요',
        body: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          GridView.count(
            crossAxisCount: 5,
            mainAxisSpacing: 4,
            crossAxisSpacing: 4,
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            children: [for (final p in thumbs) Photo(p, r: 6)],
          ),
          const SizedBox(height: 14),
          Text(kw('책 한 권은 5장부터 만들어요. 기간을 넓혀 볼까요?'), style: ui(12, color: C.body, h: 1.5)),
          const SizedBox(height: 12),
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(borderRadius: BorderRadius.circular(14), border: Border.all(color: C.line)),
            child: Text.rich(
              TextSpan(children: [
                const TextSpan(text: '앞뒤로 하루씩 넓히면 '),
                TextSpan(text: '$wider · $widerCount장', style: nm(12, w: FontWeight.w700)),
                const TextSpan(text: '이에요.'),
              ]),
              style: ui(12, color: C.body, h: 1.55),
            ),
          ),
        ]),
        foot: [Btn('$wider로 넓히기', onTap: onWiden), Btn('날짜 다시 고르기', ghost: true, onTap: onRedo)],
      );
}

/// The month's AI budget ran out: the book was made without text and does not use a free book.
BookBanner quotaBanner({required VoidCallback onRetry, required VoidCallback onClose}) => BookBanner(
      '글 없이 만들었어요',
      '이번 달 AI 사용량이 다 찼어요. 다음 달 1일부터 [다시 시도]를 누르면 이 책에 글이 들어가요. 이 책은 무료 2권에 넣지 않았어요.',
      primary: '다시 시도',
      secondary: '닫기',
      onPrimary: onRetry,
      onSecondary: onClose,
    );

/// /write failed (offline): the picks are kept, only the text is retried.
BookBanner offlineBanner(int picked, {required VoidCallback onRetry, required VoidCallback onNoText}) => BookBanner(
      '글을 못 썼어요',
      '인터넷이 끊겼어요. 고른 $picked장은 그대로라서 글만 다시 쓰면 돼요.',
      primary: '다시 시도',
      secondary: '글 없이 두기',
      onPrimary: onRetry,
      onSecondary: onNoText,
    );

/// The timeline file could not be used; the map falls back to photo GPS.
class TimelineFailScreen extends StatelessWidget {
  final String file; // "Records.json"
  final String range; // "6월 12–14일"
  final int noGps; // photos without a location
  final VoidCallback onBack, onContinue, onOther;
  const TimelineFailScreen({
    super.key,
    required this.file,
    required this.range,
    required this.noGps,
    required this.onBack,
    required this.onContinue,
    required this.onOther,
  });

  @override
  Widget build(BuildContext context) => NbScaffold(
        onBack: onBack,
        title: '타임라인을 못 읽었어요',
        body: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          BadRow(file, '타임라인 파일이 아니거나 $range 기록이 없어요', icon: Icons.insert_drive_file_outlined),
          const SizedBox(height: 14),
          Text(
            kw(noGps > 0
                ? '괜찮아요. 사진에 든 위치만으로 지도를 그려요. 위치가 없는 사진 $noGps장은 날짜로만 넣어요.'
                : '괜찮아요. 사진에 든 위치만으로 지도를 그려요.'),
            style: ui(12, color: C.body, h: 1.5),
          ),
        ]),
        foot: [Btn('사진 위치로 계속', onTap: onContinue), Btn('다른 파일 고르기', ghost: true, onTap: onOther)],
      );
}

/// .add>div.bad — an item that could not be read.
class BadRow extends StatelessWidget {
  final String title, detail;
  final IconData icon;
  final VoidCallback? onTap;
  const BadRow(this.title, this.detail, {super.key, this.icon = Icons.description_outlined, this.onTap});
  @override
  Widget build(BuildContext context) => GestureDetector(
        onTap: onTap,
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 11),
          decoration: BoxDecoration(
            color: C.warn,
            borderRadius: BorderRadius.circular(14),
            border: Border.all(color: const Color(0xFFD3B48A)),
          ),
          child: Row(children: [
            Container(
              width: 36,
              height: 36,
              decoration: BoxDecoration(color: C.paper, borderRadius: BorderRadius.circular(10)),
              child: Icon(icon, size: 18, color: C.ink),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(title, style: ui(13.5, w: FontWeight.w700)),
                const SizedBox(height: 1),
                Text(kw(detail), style: ui(11, color: C.body, h: 1.38)),
              ]),
            ),
            const SizedBox(width: 12),
            const Icon(Icons.close, size: 16, color: C.ink2),
          ]),
        ),
      );
}
