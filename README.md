# 최애.zip

화면정의서 CZ01~CZ23을 기반으로 만든 Kotlin / Jetpack Compose Android 앱입니다. Firebase Authentication, Cloud Functions, Firestore, Storage, FCM을 사용합니다. 카카오·Apple 로그인은 포함하지 않습니다.

- 앱 이름: **최애.zip**
- 패키지 / applicationId: **`com.portfolio.choaezip`**
- Android 8.0(API 26) 이상, compile/target SDK 36
- Kotlin 2.1.21, AGP 8.11.1, Gradle 8.13, JDK 21, Node 22
- 고객 화면은 한국어, 일정·예약 기준 시간대는 Asia/Seoul

## 실제 실행 화면

2026-10-21에 Android 10(AOSP) 에뮬레이터에서 디버그 APK를 실행해 캡처했습니다. 해상도는 780 × 1688px(390 × 844dp)입니다. Firebase Emulator에 연결했으며 LUMI, 닉네임, 기록과 글은 가상의 시연 데이터입니다. 상단의 개발 환경 표시는 debug 빌드에만 나타납니다.

| 홈 | 일정 | 비공개 기록 |
|---|---|---|
| <img src="docs/screenshots/home.png" width="240" alt="실행 화면: 홈" /> | <img src="docs/screenshots/calendar.png" width="240" alt="실행 화면: 월별 일정" /> | <img src="docs/screenshots/records.png" width="240" alt="실행 화면: 로그인한 계정의 비공개 기록" /> |

| 팬라운지 | 마이 · 로그인 상태 | 마이 · 게스트 |
|---|---|---|
| <img src="docs/screenshots/lounge.png" width="240" alt="실행 화면: 팬라운지" /> | <img src="docs/screenshots/my.png" width="240" alt="실행 화면: 마이" /> | <img src="docs/screenshots/my-guest.png" width="240" alt="실행 화면: 게스트 로그인 안내" /> |

일정과 목록은 스크롤해서 계속 볼 수 있습니다. 이미지 원본은 [docs/screenshots](docs/screenshots)에 있습니다.

## 개발 실행

Android Studio에서 프로젝트 루트를 열고 Android SDK 36 / Build Tools 35.0.0 및 JDK 21을 준비합니다.

```bash
npm ci
npm --prefix functions ci
npm run emulators
```

별도 터미널에서:

```bash
npm run seed
./gradlew assembleDebug
```

Android 에뮬레이터에 `app/build/outputs/apk/debug/app-debug.apk`를 설치합니다. 기본 debug 빌드는 개발용 `demo-choaezip` Firebase Emulator의 `10.0.2.2`에 연결합니다. **Firebase Emulator가 실행 중이어야 데이터와 이메일 가입이 동작합니다.** 가상 아티스트 LUMI와 예시 일정은 seed로만 등록합니다. 실제 데이터를 가장하는 오프라인 가짜 저장 동작은 없습니다.

실제 Android 기기를 USB로 연결하는 경우:

```bash
adb reverse tcp:9099 tcp:9099
adb reverse tcp:5001 tcp:5001
adb reverse tcp:9199 tcp:9199
./gradlew assembleDebug -PemulatorHost=127.0.0.1
```

## 실제 Firebase 연결

1. Firebase 프로젝트를 생성하고 Android 앱 **`com.portfolio.choaezip`**를 등록합니다.
2. `google-services.json`을 **`app/google-services.json`**에 넣습니다. 이 파일은 Git에서 제외됩니다. 서비스 계정 JSON이나 개인 키를 앱·저장소에 넣지 마세요.
3. Authentication에서 Email/Password 및 Google을 활성화합니다. Google에는 앱 서명 인증서의 SHA-1/SHA-256을 등록하고 갱신된 JSON을 내려받습니다. Credential Manager는 JSON의 `default_web_client_id`를 사용합니다.
4. Firestore와 Storage를 생성하고 Functions(서울 리전 `asia-northeast3`)와 Cloud Scheduler 사용을 위해 Blaze 요금제/필요한 API를 설정합니다. 비용이 발생할 수 있습니다.
5. 프로젝트 소유자의 인증된 개발 환경에서 명시적으로 배포합니다. `.firebaserc`의 기본 demo 프로젝트를 운영 프로젝트로 착각하지 마세요.

```bash
npx firebase login
npx firebase deploy --project YOUR_PROJECT_ID --only firestore,storage,functions
./gradlew assembleDebug -PuseEmulators=false
```

운영용 APK는 다음과 같이 만듭니다. 릴리스 서명은 배포자의 안전한 서명 설정이 필요하며 개인 키는 포함하지 않습니다.

```bash
./gradlew assembleRelease -PuseEmulators=false
```

Firebase 콘솔의 실제 아티스트/일정 데이터가 있어야 고객 목록이 표시됩니다. 개발용 seed는 demo Emulator만 대상으로 합니다. 실제 프로젝트에 seed를 자동으로 올리지 않습니다.

## 기능과 검증 범위

- 게스트 홈·최애 검색·월 달력·일정 상세·라운지 탐색
- 이메일 가입/로그인/비밀번호 재설정, Google Credential Manager 로그인/신규 가입 동의
- 비공개 기록 작성·수정·삭제·계정별 로컬 초안, 공개 게시글·댓글·좋아요
- Android Photo Picker, 최대 4장 / 장당 10MB / JPEG·PNG·WebP 검사
- 프로필·알림함·알림 설정·신고·양방향 차단·재인증 후 탈퇴
- Functions에서 소유권, 가입 완료, 제한 계정, 버전 경합, 중복 요청 검사
- Firestore 직접 접근 차단, Storage 업로드 소유권/형식/크기 검사, 미디어 접근 검사 후 5분 URL
- 일정 30분 전 알림, 일정 변경·취소, 댓글·좋아요·기록 회고·신고 결과 outbox/FCM 작업

자세한 화면 대응과 차이점은 [docs/IMPLEMENTATION.md](docs/IMPLEMENTATION.md), 배포·보안 설명은 [docs/FIREBASE.md](docs/FIREBASE.md)를 참고하세요.

```bash
./gradlew assembleDebug testDebugUnitTest lintDebug
node --test functions/test/policy.test.js
npm test  # Auth, Firestore, Functions, Storage 실제 Emulator 통합 테스트
```

`npm test`는 독립된 demo Emulator를 기동하므로 실행 중인 개발 Emulator와 포트가 겹치지 않게 종료 후 실행하세요. 테스트는 보안 규칙 우회, 타인 기록 접근, 이중 저장, 버전 경합, 차단, 알림 신청 경계, 사진 규칙, 탈퇴 등을 검사합니다. Google 공급자·실제 이메일 전달·실기기 FCM은 실제 Firebase 연결 후 검수해야 합니다.

## Codex cloud

각 작업은 이미 격리된 환경입니다. 기존 체크아웃을 사용하고 사용자가 요청하지 않으면 Git worktree를 만들지 않습니다.

```bash
bash scripts/setup-cloud.sh
source scripts/cloud-env.sh
npm run emulators
# 별도 셸에서 source scripts/cloud-env.sh 후 npm run seed
```

설치 스크립트는 `/workspace/toolchains`에 검증된 도구를 설치합니다. 시스템 CA와 프록시를 사용하며 TLS·체크섬 검증을 끄지 않습니다. Firebase Emulator 최초 다운로드에는 `storage.googleapis.com` 접근이 필요합니다. Android Emulator/실기기 실행과 실제 Firebase 배포는 별도 단계입니다.
