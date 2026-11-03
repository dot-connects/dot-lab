# Parking floor app v1 — plan (2026-11-03)

A real, daily-use app for the operator's phone. Not published (sideload only). Builds on the measurement app in
`app/` (keeps its CSV mode as "시험 기록") and the round-6 widget, variant 2 + light panel with adjustable opacity
(https://claude.ai/artifact/2mmNEo5v9UqRYptzkMbRkt). Research: `docs/archive/2026-11-03-parking-design/`.

## Done when

1. The operator pairs the car once; after that, every park writes a record with no app open: floor estimate,
   second candidate, building name, time. The widget shows it within 10 s of the car Bluetooth dropping.
2. Widget 2x2 / 3x2 / 4x2 / 4x3 work on the operator's Samsung: estimate state [확정][직접 입력][2순위], confirmed
   state [기둥 사진][위치 입력][공유]; tags "추정 · 눌러서 확정" (yellow) / "확정" (green); light panel opacity
   0-100 % in settings, ink turns dark from 45 %. Minimum size 2x2.
3. Confirming or correcting a floor teaches that place: second visit to the same place uses the learned height.
4. Pillar photo reads the code (Korean + Latin) and stores only the text and one pillar colour; the photo is gone.
5. Share opens the Android share sheet with text for KakaoTalk ("차 위치: 스타필드 수원 B2 C-12 (16:32 주차)").
6. Idle cost: no process between drives; settings shows sensor minutes, CPU seconds, GPS seconds from
   `SystemHealthManager`.
7. Unit tests on the floor model and park detector pass; the operator runs it for 3 real parks.

## Architecture

```
CarSetup (once)      bonded devices list -> CompanionDeviceManager.associate(MAC, single device)
CarPresenceService   CompanionDeviceService: appeared -> start DriveService; disappeared -> stop it
DriveService         FGS type connectedDevice; barometer non-wakeup 1 Hz, maxReportLatency 5 min (FIFO);
                     passive GPS listener (no own fixes); keeps a 20-min ring buffer
on disappear         flush -> ParkDetector -> PlaceResolver -> FloorModel -> ParkStore -> widget + notification
ParkDetector         P_park = median of the last stable 10 s before disconnect;
                     P_ref = median of the last flat 20 s before the final descent/ascent began
                     (pressure slope ~0, then a sustained change); falls back to "no reference"
PlaceResolver        last GPS fix <= 10 min old -> Android Geocoder (no key) -> building/place name;
                     place key = rounded lat/lng cell (~50 m) so the same garage matches next time
FloorModel           per place: confirmed (delta_m, floor) pairs. 0 pairs -> default (first step 4.5 m,
                     then 3.3 m); 1 pair -> uniform height; 2+ -> line with a separate first step.
                     Output: floor, second candidate (the neighbour on the side the value leans to), confidence
ParkStore            JSON file in app storage: last parks per car, place models. No server
Widget               RemoteViews per size class (2x2, 3x2, 4x2, 4x3), numerals drawn as bitmap (PillarMark),
                     redrawn only on events
Activities           Home (my car + history), floor picker, pillar camera (CameraX + ML Kit Korean via Play
                     services, ~260 KB), location text input, settings (panel opacity, ink, battery numbers),
                     test recording (existing CSV mode)
```

Permissions: BLUETOOTH_CONNECT, companion device observe, FGS connectedDevice, POST_NOTIFICATIONS,
ACCESS_FINE + ACCESS_BACKGROUND_LOCATION (allowed: not on the store), CAMERA (only when taking a pillar photo).

## Decisions taken here

- Geocoder before Kakao: no key in the APK. If building names are poor on real parks, add Kakao later with the key
  injected from a gitignored file.
- 4x3 in v1 shows this car plus the previous park at the same place (no family server yet).
- Label/park value is read after pressure settles (11-03 data: taps during elevator motion were a floor off).
- The measurement CSV mode stays for research; the test grid moves to a "시험 기록" screen.

## Order

1. FloorModel + ParkDetector as pure Kotlin with tests (synthetic traces; no operator CSVs in the repo).
2. ParkStore + PlaceResolver.
3. Car setup + CompanionDeviceService + DriveService (real phone check: wake on connect, data on disconnect).
4. Widget v2 with sizes, panel opacity, actions.
5. Floor picker, location input, pillar camera + OCR, share.
6. Home, history, settings, battery numbers. Release 0.3.0.

## Risks to check on the phone

Samsung "sleeping apps" blocking CDM wake; barometer FIFO size; Geocoder returning only an address underground;
ML Kit model download on first use; widget bitmap memory at 4x3.
