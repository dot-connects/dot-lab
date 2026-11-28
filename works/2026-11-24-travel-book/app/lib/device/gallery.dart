// Gallery access: the trip's photos and videos by DATE_TAKEN, grouped by album (Android: one photo = one folder).
// photo_manager 3.12 notes (plan-r2 review): createTimeCond filters DATE_ADDED, so a custom DATE_TAKEN filter is
// used; a custom WHERE drops the media-type condition, so media_type is added back; a filtered path list leaves
// out albums with no match, so the album list is read once without the filter.
import 'package:photo_manager/photo_manager.dart';

import '../core/chapters.dart';
import '../core/models.dart';

class Album {
  final String id, name;
  final int total; // whole album
  final List<Shot> inRange; // this trip's shots (after the local-day re-filter)
  const Album(this.id, this.name, this.total, this.inRange);
}

enum Access { full, limited, denied }

/// Photos + videos with GPS (ACCESS_MEDIA_LOCATION is a runtime permission).
Future<Access> requestAccess() async {
  final s = await PhotoManager.requestPermissionExtend(
    requestOption: const PermissionRequestOption(
      androidPermission: AndroidPermission(type: RequestType.common, mediaLocation: true),
    ),
  );
  if (s == PermissionState.authorized) return Access.full;
  if (s == PermissionState.limited) return Access.limited;
  return Access.denied;
}

Future<void> openSettings() => PhotoManager.openSetting();

const _pad = Duration(hours: 14); // trip dates are local; DATE_TAKEN is UTC, any zone is within 14 h

AdvancedCustomFilter _taken(DateTime from, DateTime to) => AdvancedCustomFilter()
    .addWhereCondition(ColumnWhereCondition(column: CustomColumns.base.mediaType, operator: 'in', value: '(1,3)'))
    .addWhereCondition(DateColumnWhereCondition(column: CustomColumns.android.dateTaken, operator: '>=', value: from.subtract(_pad)))
    .addWhereCondition(DateColumnWhereCondition(column: CustomColumns.android.dateTaken, operator: '<=', value: to.add(_pad)));

/// Photos saved from messengers often have no DATE_TAKEN: they are found by DATE_ADDED instead.
AdvancedCustomFilter _addedNoTaken(DateTime from, DateTime to) => AdvancedCustomFilter()
    .addWhereCondition(ColumnWhereCondition(column: CustomColumns.base.mediaType, operator: 'in', value: '(1,3)'))
    .addWhereCondition(WhereConditionGroup().andText('${CustomColumns.android.dateTaken} IS NULL'))
    .addWhereCondition(DateColumnWhereCondition(column: CustomColumns.base.createDate, operator: '>=', value: from.subtract(_pad)))
    .addWhereCondition(DateColumnWhereCondition(column: CustomColumns.base.createDate, operator: '<=', value: to.add(_pad)));

Shot _shot(AssetEntity a, {double? lat, double? lng}) => Shot(
      id: a.id,
      utc: a.createDateTime.toUtc(),
      isVideo: a.type == AssetType.video,
      durationMs: a.duration * 1000,
      lat: lat,
      lng: lng,
      relPath: a.relativePath ?? '',
      w: a.orientatedWidth,
      h: a.orientatedHeight,
    );

/// Every album, each with this trip's shots ([from]..[to] are local trip days, inclusive).
/// [exif] fills the camera wall clock / offset and GPS per photo in one file read (MediaChannel); without it GPS comes
/// from latlngAsync.
Future<List<Album>> scanTrip(DateTime from, DateTime to,
    {Future<({DateTime? wall, Duration? offset, double? lat, double? lng})> Function(AssetEntity a)? exif}) async {
  final end = DateTime(to.year, to.month, to.day).add(const Duration(days: 1, hours: 4)); // 04:00 day boundary
  final start = DateTime(from.year, from.month, from.day, 4);
  final all = await PhotoManager.getAssetPathList(hasAll: false, type: RequestType.common);
  // the filtered list holds only albums with a match, each already narrowed to the trip window
  final found = <String, List<AssetEntity>>{};
  for (final f in [_taken(start, end), _addedNoTaken(start, end)]) {
    for (final p in await PhotoManager.getAssetPathList(hasAll: false, type: RequestType.common, filterOption: f)) {
      final n = await p.assetCountAsync;
      if (n > 0) found.putIfAbsent(p.id, () => []).addAll(await p.getAssetListRange(start: 0, end: n));
    }
  }
  // position + camera clock per photo: 32 at a time (one by one took 12 s for 2,282 shots on the operator's phone)
  Future<Shot> read(AssetEntity a) async {
    if (a.type != AssetType.image) return _shot(a);
    if (exif == null) {
      final ll = await a.latlngAsync();
      final ok = ll != null && (ll.latitude != 0 || ll.longitude != 0);
      return _shot(a, lat: ok ? ll.latitude : null, lng: ok ? ll.longitude : null);
    }
    final e = await exif(a); // heat check 11-28: one open per photo, not two (latlngAsync + exif)
    final ok = e.lat != null && e.lng != null && (e.lat != 0 || e.lng != 0);
    final s = _shot(a, lat: ok ? e.lat : null, lng: ok ? e.lng : null);
    return Shot(id: s.id, utc: s.utc, wall: e.wall, exifOffset: e.offset, isVideo: s.isVideo, durationMs: s.durationMs,
        lat: s.lat, lng: s.lng, relPath: s.relPath, w: s.w, h: s.h);
  }

  final shotOf = <String, Shot>{};
  final pending = {for (final l in found.values) for (final a in l) a.id: a}.values.toList();
  for (var k = 0; k < pending.length; k += 32) {
    final part = pending.sublist(k, k + 32 > pending.length ? pending.length : k + 32);
    for (final s in await Future.wait(part.map(read))) {
      shotOf[s.id] = s;
    }
  }
  final out = <Album>[];
  for (final path in all) {
    final total = await path.assetCountAsync;
    final shots = [for (final a in found[path.id] ?? const <AssetEntity>[]) shotOf[a.id]!];
    final photos = shots.where((s) => !s.isVideo).toList();
    final inRange = [
      for (final s in shots)
        if (_inTrip(tripDay(localOf(s, photos)), from, to)) s,
    ];
    out.add(Album(path.id, path.name, total, inRange));
  }
  out.sort((a, b) => b.inRange.length.compareTo(a.inRange.length));
  return out;
}

bool _inTrip(String day, DateTime from, DateTime to) {
  final d = DateTime.parse(day);
  return !d.isBefore(DateTime(from.year, from.month, from.day)) && !d.isAfter(DateTime(to.year, to.month, to.day));
}

/// Thumbnail bytes with the long side = [long] (Glide fits the SHORT side, so pass an aspect-fit box).
Future<List<int>?> thumbnail(AssetEntity a, int long, {int quality = 80}) {
  final w = a.orientatedWidth, h = a.orientatedHeight;
  final size = w >= h
      ? ThumbnailSize(long, (long * h / (w == 0 ? 1 : w)).round().clamp(1, long))
      : ThumbnailSize((long * w / (h == 0 ? 1 : h)).round().clamp(1, long), long);
  return a.thumbnailDataWithSize(size, format: ThumbnailFormat.jpeg, quality: quality);
}
