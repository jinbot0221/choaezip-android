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

## 2026-10-21 Firebase 실프로젝트 연결 및 인증 수정

- 사용자 제공 `choaezip` 프로젝트 설정으로 `assembleDebug` 성공. 패키지 `com.portfolio.choaezip`, 생성 BuildConfig `USE_EMULATORS=false` 확인. 설정 JSON은 Git 제외.
- `testDebugUnitTest lintDebug` 성공. 전체 7개 테스트 통과. 개발 배너 제거, FirebaseNetworkException의 한국어 안내, 계정 생성 실패 시 프로필 호출 방지, 계정 생성 후 프로필 저장 실패 분리, 취소 전파 검증 포함.
- Google 명시적 로그인 요청 → Google ID 토큰 → Firebase Auth 연결 구현. 사용자 취소는 오류 표시 없이 처리하며 로그아웃/탈퇴 시 Credential Manager 상태 초기화.
- 이메일 계정 생성 후 프로필 저장만 실패하면 가입 완료 화면으로 전환하고 닉네임/동의를 유지하여 재시도. 비밀번호는 저장하지 않음.
- 이 환경에서 실제 Firebase Auth 설정 조회는 네트워크 연결 실패(curl 56)로 검증하지 못함. 실제 Google 계정 로그인 및 실제 이메일 계정 생성은 미검증. 콘솔 공급자 활성화, APK 서명 SHA 등록, 실제 프로젝트의 Functions/Firestore/Storage 배포 여부는 별도 확인 필요.
- 기존 실행 스크린샷은 이번 수정 이전 버전이며 새 실행 화면 검증으로 간주하지 않음.

## 2026-10-21 FCM 콘솔 테스트 및 라운지 보관 확인

- 실제 Firebase 설정으로 `assembleDebug testDebugUnitTest lintDebug` 성공. Android 테스트 9개 통과. 알림 notification payload가 로그인/Functions 없이 전경에서 표시되고, 대상 없는 비공개 data 메시지는 표시되지 않는 것을 Robolectric에서 검증.
- 백그라운드 FCM SDK 표시용 기본 알림 채널/아이콘 지정, 앱 시작 시 채널 생성. 마이 → 알림 수신 확인에서 Android 13+ 권한 요청과 FCM 토큰 조회/복사 제공.
- `npm test` 성공: demo Firebase Auth/Firestore/Functions/Storage Emulator를 사용한 통합 테스트 및 도메인 테스트 전체 19개 통과. 라운지 생성 결과의 Firestore `posts/{id}` 문서와 작성자/본문을 확인하고 새 클라이언트 재로그인 후 글/목록 재조회까지 검증.
- 라운지의 기존 서버 영구 저장 로직 유지. 일반 게시글 자동 만료 없음. 사용자 삭제/탈퇴 및 운영 조치는 기존 정책대로 처리.
- 실제 FCM 네트워크 전송/콘솔 발송 및 실기기 전경·백그라운드 수신은 이번 환경에서 미검증. 실제 프로젝트 서버 배포 상태도 미확인. 콘솔 테스트 및 실제 데이터 저장 준비 방법은 README에 기재.

## 2026-10-21 로그인 정체 및 가입 입력 오류 안내 개선

- `assembleDebug testDebugUnitTest lintDebug` 성공. Android 테스트 12개 통과.
- 기존 가입 조건은 그대로 유지하면서 닉네임/이메일/비밀번호 길이/확인 불일치/필수 동의를 각각 표시. 일반 가입 및 Google 신규 계정의 가입 완료 화면 모두 항목별 안내 제공. 조건을 충족한 입력이 검증을 통과하는 단위 테스트와 실제 Compose 입력·동의 문구 클릭·가입 버튼 동작 UI 테스트 통과.
- Google 계정 선택은 앱의 busy Dialog를 열기 전에 실행. 선택 중 버튼 중복 실행 방지 및 취소/실패 안내. Firebase Auth 성공 후 앱 프로필 조회 실패를 별도로 표시하고 미배포 Functions 가능성을 안내.
- 로그인 공급자 비활성화, 중복 이메일, Firebase 비밀번호 정책, 잘못된 인증 정보 등 인증 오류를 구분. 실제 Google 로그인 성공 여부 및 사용자 프로젝트의 Functions 배포 상태는 미검증이며 이번 변경으로 해결되었다고 단정하지 않음.

## 2026-10-21 Firebase 단계별 진단 로그

- `assembleDebug testDebugUnitTest lintDebug` 성공. Android 테스트 14개 통과.
- `ChoaezipFirebase` 태그로 Firebase 초기화/프로젝트 구성, Google 계정 선택 및 Auth 교환, 이메일 생성/로그인, 프로필 조회/저장, 라운지/Storage, FCM 발급/갱신/서버 등록/전경 수신/알림 표시 요청을 분리하여 기록. request 번호, 경과 시간, 예외 종류와 SDK 코드 제공.
- debug 실프로젝트 앱 시작 시 Auth/Functions와 독립적으로 FCM 토큰 요청. 오류를 숨기던 기기 등록 실패도 로그 제공.
- 로그에 실제 토큰/이메일/비밀번호/SDK 예외 메시지와 반환 데이터를 기록하지 않는 회귀 테스트 통과. 릴리스 로그 비활성화.
- 이 작업은 원인 수집을 위한 계측이며 실제 사용자 기기의 Google 로그인/FCM 실패 원인이 해결되었다고 단정하지 않음. 실기기 재현 로그가 필요함. 백그라운드 콘솔 알림은 SDK 처리이므로 서비스 수신 로그가 없을 수 있음.
