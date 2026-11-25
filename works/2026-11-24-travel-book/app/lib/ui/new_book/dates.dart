// D3 1/3 날짜: a month calendar, two taps for the range, a dot on days with photos, counts per day.
import 'package:material_ui/material_ui.dart';

import '../tokens.dart';
import '../widgets.dart';

DateTime _d(DateTime t) => DateTime(t.year, t.month, t.day);

/// "6월 12일 – 14일", "6월 30일 – 7월 2일", "6월 12일".
String rangeLabel(DateTime s, DateTime e) {
  if (_d(s) == _d(e)) return '${s.month}월 ${s.day}일';
  return s.month == e.month ? '${s.month}월 ${s.day}일 – ${e.day}일' : '${s.month}월 ${s.day}일 – ${e.month}월 ${e.day}일';
}

/// Compact form used in fine print: "6월 12–14일".
String rangeShort(DateTime s, DateTime e) {
  if (_d(s) == _d(e)) return '${s.month}월 ${s.day}일';
  return s.month == e.month ? '${s.month}월 ${s.day}–${e.day}일' : '${s.month}월 ${s.day}일–${e.month}월 ${e.day}일';
}

class DatesScreen extends StatefulWidget {
  final DateTime month;
  final Map<DateTime, int> photos, videos; // keyed by local date (midnight)
  final DateTime? start, end;
  final VoidCallback onClose;
  final void Function(DateTime start, DateTime end) onNext;
  final int? partial; // photos visible under "일부만 허용"; null = full access
  final VoidCallback? onOpenSettings;
  final ValueChanged<DateTime>? onMonth; // the shown month changed: load its counts into [photos]/[videos]
  const DatesScreen({
    super.key,
    required this.month,
    required this.photos,
    this.videos = const {},
    this.start,
    this.end,
    required this.onClose,
    required this.onNext,
    this.partial,
    this.onOpenSettings,
    this.onMonth,
  });
  @override
  State<DatesScreen> createState() => _DatesScreenState();
}

class _DatesScreenState extends State<DatesScreen> {
  late DateTime _m = DateTime(widget.month.year, widget.month.month);
  late DateTime? _s = widget.start, _e = widget.end;
  bool _goOn = false; // "N장으로 계속" under partial access

  void _tap(DateTime d) => setState(() {
        if (_s == null || _e != null || d.isBefore(_s!)) {
          _s = d;
          _e = null;
        } else {
          _e = d;
        }
      });

  List<DateTime> get _days {
    if (_s == null) return [];
    final e = _e ?? _s!;
    return [for (var d = _s!; !d.isAfter(e); d = DateTime(d.year, d.month, d.day + 1)) d];
  }

  @override
  Widget build(BuildContext context) {
    final days = _days;
    final photos = days.fold(0, (s, d) => s + (widget.photos[d] ?? 0));
    final videos = days.fold(0, (s, d) => s + (widget.videos[d] ?? 0));
    return NbScaffold(
      back: '✕',
      onBack: widget.onClose,
      right: topLabel('1 / 3'),
      title: '언제 다녀왔어요?',
      body: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        if (widget.partial != null)
          Padding(
            padding: const EdgeInsets.only(bottom: 12),
            child: Warn('사진을 일부만 볼 수 있어요.',
                '허용한 ${widget.partial}장에서만 찾아요. 모두 허용해야 날짜만으로 여행 사진을 다 찾고, 사진에 든 위치도 읽어요.'),
          ),
        Row(crossAxisAlignment: CrossAxisAlignment.center, children: [
          Text('${_m.year}. ${_m.month}', style: nm(15, w: FontWeight.w600)),
          const Spacer(),
          for (final (g, dm) in [('‹', -1), ('›', 1)])
            GestureDetector(
              onTap: () {
                setState(() => _m = DateTime(_m.year, _m.month + dm));
                widget.onMonth?.call(_m);
              },
              child: SizedBox(width: 26, height: 26, child: Center(child: Text(g, style: ui(16, color: C.ink2)))),
            ),
        ]),
        const SizedBox(height: 6),
        _grid(),
        Container(
          margin: const EdgeInsets.only(top: 10),
          padding: const EdgeInsets.only(top: 10),
          decoration: const BoxDecoration(border: Border(top: BorderSide(color: C.line))),
          child: Row(crossAxisAlignment: CrossAxisAlignment.baseline, textBaseline: TextBaseline.alphabetic, children: [
            Text(_s == null ? '처음 날과 끝 날을 눌러요' : rangeLabel(_s!, _e ?? _s!), style: nm(15, w: FontWeight.w600)),
            const Spacer(),
            if (_s != null) Text('사진 ${fmtInt(photos)} · 영상 ${fmtInt(videos)}', style: nm(11.5, color: C.ink2)),
          ]),
        ),
        const SizedBox(height: 12),
        LayoutBuilder(
          builder: (_, c) => Wrap(spacing: 6, runSpacing: 6, children: [
            for (final d in days)
              Container(
                width: (c.maxWidth - 12) / 3,
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
                decoration: BoxDecoration(color: C.card, borderRadius: BorderRadius.circular(12)),
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text('${d.day} ${weekdays[d.weekday - 1]}', style: nm(11, color: C.ink2)),
                  const SizedBox(height: 2),
                  Text(fmtInt(widget.photos[d] ?? 0), style: nm(16, w: FontWeight.w600)),
                ]),
              ),
          ]),
        ),
      ]),
      fade: widget.partial != null && !_goOn,
      foot: widget.partial != null && !_goOn
          ? [
              Btn('설정에서 모두 허용', onTap: widget.onOpenSettings),
              Btn('${widget.partial}장으로 계속', ghost: true, onTap: () => setState(() => _goOn = true)),
            ]
          : [Btn('다음', onTap: _s == null ? null : () => widget.onNext(_s!, _e ?? _s!))],
    );
  }

  Widget _grid() {
    final first = DateTime(_m.year, _m.month);
    final offset = first.weekday % 7; // Sunday first
    final n = DateTime(_m.year, _m.month + 1, 0).day;
    final e = _e ?? _s;
    final cells = <Widget>[
      for (final w in const ['일', '월', '화', '수', '목', '금', '토'])
        Padding(padding: const EdgeInsets.only(bottom: 4), child: Center(child: Text(w, style: ui(10, color: C.ink2)))),
      for (var i = 0; i < offset; i++) const SizedBox(),
      for (var day = 1; day <= n; day++) _cell(DateTime(_m.year, _m.month, day), e),
    ];
    return LayoutBuilder(builder: (_, c) {
      final w = c.maxWidth / 7;
      return Wrap(runSpacing: 2, children: [
        for (final (i, x) in cells.indexed) SizedBox(width: w, height: i < 7 ? 18 : 32, child: x),
      ]);
    });
  }

  Widget _cell(DateTime d, DateTime? e) {
    final isS = _s != null && d == _s, isE = e != null && d == e && _e != null;
    final inR = _s != null && e != null && !d.isBefore(_s!) && !d.isAfter(e) && _e != null;
    final has = (widget.photos[d] ?? 0) > 0;
    final cap = isS || isE || (_s != null && _e == null && d == _s);
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTap: () => _tap(d),
      child: Container(
        decoration: inR
            ? BoxDecoration(
                color: C.tint,
                borderRadius: BorderRadius.horizontal(
                  left: Radius.circular(isS ? 16 : 0),
                  right: Radius.circular(isE ? 16 : 0),
                ),
              )
            : null,
        child: Stack(alignment: Alignment.center, children: [
          if (cap) Container(width: 32, height: 32, decoration: const BoxDecoration(color: C.ink, shape: BoxShape.circle)),
          Text('${d.day}', style: nm(12.5, color: cap ? C.paper : C.ink, w: cap ? FontWeight.w700 : FontWeight.w500)),
          if (has)
            Positioned(
              bottom: 3,
              child: Container(width: 4, height: 4, decoration: BoxDecoration(color: cap ? C.paper : C.oak2, shape: BoxShape.circle)),
            ),
        ]),
      ),
    );
  }
}
