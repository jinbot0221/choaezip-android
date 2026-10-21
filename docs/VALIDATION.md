# 검증 결과 — 2026-10-20

현재 클라우드 머신에서 `bash scripts/setup-cloud.sh`를 실행한 결과입니다.

| 항목 | 실제 결과 |
|---|---|
| 도구 설치 | JDK 21.0.12.1 / Node 22.16.0 / SDK 36 / Build Tools 35.0.0 준비, TLS 및 공식 체크섬 검증 유지 |
| 의존성 | 루트 및 functions의 `npm ci` 성공, lockfile 유지 |
| `assembleDebug` | 성공, `app/build/outputs/apk/debug/app-debug.apk` 생성 |
| `testDebugUnitTest` | 3개 통과 / 0 실패 / 0 건너뜀 |
| Compose + Robolectric | 게스트 기록 탭→로그인, 마이→홈 탐색 2개 통과 |
| Kotlin 입력 검증 | 이메일·닉네임·비밀번호·본문 경계 테스트 1개 통과 |
| `lintDebug` | 오류 0 / 경고 10, 통과. 경고는 KTX 권고, 선택적 리소스 이름 조회, 의존성 새 버전 안내 등 |
| Node 정책 테스트 | 5개 통과 / 0 실패 / 0 건너뜀. 한국 날짜·30분 마감·야간 시간·윤년·미디어 경로 |
| Firebase 통합 테스트 | **14개 미실행**. Emulator 최초 다운로드가 `storage.googleapis.com` CONNECT 403으로 차단됨 |
| 실기기 / Android Emulator | 미실행. 이 머신에는 `/dev/kvm` 및 연결된 Android 기기가 없음 |
| 실제 Google·이메일 전달·FCM·Scheduler | 미검증. 실제 Firebase 프로젝트 설정·배포가 없음 |
| 재사용 설치 스크립트 | 현재 머신의 보존된 도구를 사용하는 전체 재실행 성공. 새 머신 복원은 미검증 |

Firebase 통합 테스트에는 비회원 쓰기 거부, 가입 완료 검사, 비공개 기록 소유권, 이중 저장, 버전 경합, Firestore 직접 접근 거부, 공개 글·댓글·좋아요, 신고와 차단, 일정 알림 경계, 알림 소유권, Storage 제약, 관리자 권한, 삭제 링크, 계정 탈퇴 검사가 포함되어 있습니다. 테스트 코드가 존재하는 것과 실제 통과한 것은 다릅니다.

네트워크 초안에 `storage.googleapis.com`과 `firebase-public.firebaseio.com`을 추가했습니다. 환경 설정에서 검토·저장해 런타임에 적용한 뒤 `source scripts/cloud-env.sh && npm test`로 통합 테스트를 진행해야 합니다. 설치/시작 지침도 환경 설정 초안에 저장했으며, 초안 저장은 실행·배포·환경 게시를 의미하지 않습니다.

Firebase 서비스 계정 키·릴리스 서명 키·실제 `google-services.json`은 저장소에 넣지 않았습니다. 디버그 APK는 Emulator 연결용으로, 운영 Firebase에 연결하려면 README의 실제 프로젝트 설정 절차가 필요합니다.

## 2026-10-21 실제 APK 실행 추가 검증

- 네트워크 설정이 적용된 현재 머신에서 Firebase Auth / Firestore / Functions / Storage Emulator 기동과 시연 seed 실행을 확인했습니다. 위 2026-10-20의 다운로드 차단 상태는 당시 결과입니다.
- 실제 실행 중 확인한 개발용 Firebase Options의 SDK 형식 검증 오류와 seed 스크립트의 패키지 exports 해석 오류를 수정했습니다. 키와 app ID는 demo 프로젝트 전용의 비운영 값입니다.
- 수정 후 `assembleDebug` 및 기존 Android 테스트 3개가 통과했습니다.
- 하드웨어 가속 없이 Android 10(AOSP) 가상 기기에서 APK 설치·실행, 공개 일정 조회, 이메일 Emulator 로그인, 본인 기록 조회, 공개 라운지 조회, 마이 프로필 조회를 직접 확인했습니다. 홈·일정·기록·라운지·로그인/게스트 마이 스크린샷을 README에 추가했습니다.
- 실제 Firebase 프로젝트, 실제 Google 로그인, 외부 메일 전달, FCM 전송은 여전히 미검증입니다. 서버 통합 테스트 14개 전체의 재실행 완료를 주장하지 않습니다.
