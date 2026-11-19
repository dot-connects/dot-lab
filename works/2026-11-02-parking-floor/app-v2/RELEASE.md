# 어디댔어 1.0.0 출시 메모

## 데이터 안전 양식 답

| 항목 | 답 |
|---|---|
| 수집·공유 데이터 | 앱 활동(주차 기록: 층·구역·번호·기둥·메모), 사진(선택), 이름(가족 표시 이름), 차 이름·색·번호판(선택) |
| 정확/대략 위치 | 수집 안 함 (위치 권한 없음) |
| 음성·오디오 | 수집 안 함. 기기 안 인식기만 쓰고 전송 안 함 |
| 전송 암호화 | 예 (HTTPS) |
| 삭제 요청 | 예. 앱 설정 "내 데이터 지우기", 가족 나가기 |
| 제3자 공유 | 없음 (같은 가족 구성원에게만 보임) |
| 개인정보처리방침 URL | Pages에 게시된 privacy.html (게시 후 URL 확인) |

## 권한

| 권한 | 이유 |
|---|---|
| RECORD_AUDIO | 위젯·앱에서 말로 주차 자리 입력 (기기 안 인식) |
| POST_NOTIFICATIONS | 주차 저장·차 연결 끊김 알림 |
| BLUETOOTH_CONNECT | 차 블루투스 연결 끊김 감지 |
| REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE | 차 블루투스가 가까이 있는지 백그라운드 감지 |
| INTERNET | 가족과 기록 동기화 |
| RECEIVE_BOOT_COMPLETED | 재부팅 뒤 차 감지·알림 복원 |
| 카메라 | 권한 없이 시스템 카메라 intent로만 사진 촬영 |
| 위치 | 쓰지 않음 |

## Play 출시 남은 일

1. AAB: `./gradlew :app:bundleRelease --max-workers=2` (업로드 키 서명, 키는 `~/.wherepark/`).
2. Play App Signing 지문을 Play Console에서 확인 -> Worker `ASSETLINKS_SHA256`에 쉼표로 이어 추가(업로드 키 지문 + Play 지문) -> Worker 배포 -> assetlinks 검증.
3. 비공개 테스트: 12명 x 14일, 다른 앱과 공용 테스터 그룹 사용.
4. LBS 간이 신고 판단: `C:\DC\DD\docs\archive\2026-11-03-parking-design\family-legal.md` 결론 참조 (신고 필요 가능성 높음, 사업 시작 1개월 안 간이 신고, 개인위치정보사업 등록은 해당 없음 가능성).
5. 푸시: Firebase 마법사 실행 후 `firebase.properties` 배치 (커밋 안 함).
6. privacy.html Pages 게시 URL을 Play Console에 입력.

## 실기기 확인

1. 위젯 마이크로 말하면 저장된다.
2. 비행기 모드에서도 인식된다.
3. 두 번째 폰이 합류하고 위젯이 갱신된다.
4. 차 블루투스가 끊기면 알림이 온다.
5. 카톡으로 받은 초대 링크가 앱에서 열린다.
6. 퀵 설정 타일, 바로가기, 위젯 고정(pin)이 동작한다.
7. 블루투스 기기 선택 화면에서 차를 고를 수 있다.
8. 기존 "주차층 0.4.1"은 지운다.
