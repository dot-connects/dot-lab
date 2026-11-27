// The real Source: photo_manager (gallery.dart), the media channel and the OS geocoder.
import 'dart:typed_data';

import 'package:flutter/widgets.dart' show Locale;
import 'package:geocoding/geocoding.dart';
import 'package:photo_manager/photo_manager.dart';

import '../device/gallery.dart' as g;
import '../device/media_channel.dart' as mc;
import '../pipeline.dart';

class PhoneSource implements Source {
  final _geo = Geocoding(locale: const Locale('ko', 'KR'));
  final _assets = <String, AssetEntity?>{};

  Future<AssetEntity?> _asset(String id) async => _assets.containsKey(id) ? _assets[id] : _assets[id] = await AssetEntity.fromId(id);

  @override
  Future<Map<DateTime, (int, int)>> monthCounts(DateTime month) async {
    final from = DateTime(month.year, month.month), to = DateTime(month.year, month.month + 1);
    // ponytail: DATE_TAKEN only (the calendar dots); messenger saves without it show up after the trip scan
    final f = AdvancedCustomFilter()
        .addWhereCondition(ColumnWhereCondition(column: CustomColumns.base.mediaType, operator: 'in', value: '(1,3)'))
        .addWhereCondition(DateColumnWhereCondition(column: CustomColumns.android.dateTaken, operator: '>=', value: from))
        .addWhereCondition(DateColumnWhereCondition(column: CustomColumns.android.dateTaken, operator: '<', value: to));
    final paths = await PhotoManager.getAssetPathList(onlyAll: true, type: RequestType.common, filterOption: f);
    final out = <DateTime, (int, int)>{};
    for (final p in paths) {
      final n = await p.assetCountAsync;
      for (final a in await p.getAssetListRange(start: 0, end: n)) {
        final t = a.createDateTime.toLocal();
        final d = DateTime(t.year, t.month, t.day);
        final (ph, v) = out[d] ?? (0, 0);
        out[d] = a.type == AssetType.video ? (ph, v + 1) : (ph + 1, v);
      }
    }
    return out;
  }

  /// Current access without asking (a later start may have "일부만 허용").
  Future<bool> isLimited() async =>
      await PhotoManager.getPermissionState(
        requestOption: const PermissionRequestOption(
          androidPermission: AndroidPermission(type: RequestType.common, mediaLocation: true),
        ),
      ) ==
      PermissionState.limited;

  /// Photos + videos the app can see (under "일부만 허용" only the allowed ones).
  Future<int> visibleCount() => PhotoManager.getAssetCount(type: RequestType.common);

  @override
  Future<List<g.Album>> scan(DateTime from, DateTime to) => g.scanTrip(from, to, exif: mc.exifOf);

  @override
  Future<Uint8List?> jpeg(String id, int long, {int quality = 80}) async {
    final a = await _asset(id);
    if (a == null) return null;
    final b = await g.thumbnail(a, long, quality: quality);
    return b == null ? null : Uint8List.fromList(b);
  }

  @override
  Future<List<Uint8List>> videoFrames(String id) async {
    final a = await _asset(id);
    return a == null ? const [] : mc.videoFrames(a);
  }

  @override
  Future<String?> trim(String id, int startMs, int endMs, String out) async {
    final a = await _asset(id);
    return a == null ? null : mc.trimClip(a, startMs, endMs, out);
  }

  @override
  Future<String?> placeOf(double lat, double lng) async {
    try {
      // offline, the OS geocoder can wait long per call: a book without a place name beats a frozen screen
      final p = (await _geo.placemarkFromCoordinates(lat, lng).timeout(const Duration(seconds: 4))).firstOrNull;
      if (p == null) return null;
      final s = [p.locality, p.subAdministrativeArea, p.name].firstWhere((x) => x != null && x.isNotEmpty, orElse: () => null);
      return s;
    } catch (_) {
      return null; // offline or no geocoder: the book just has no place name there
    }
  }
}
