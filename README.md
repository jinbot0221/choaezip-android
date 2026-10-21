# 최애.zip Android — Firebase Spark

Kotlin·Jetpack Compose Android 앱입니다. 패키지는 **`com.portfolio.choaezip`**입니다. Google·이메일 로그인, 프로필, 일정 조회, 비공개 기록, 팬라운지 글·댓글·좋아요, 차단·신고를 Firebase Auth와 Cloud Firestore로 구현했습니다. Kakao·Apple 로그인은 포함하지 않습니다.

현재 앱은 **Cloud Functions를 호출하지 않습니다.** Spark 무료 할당량 내에서 사용할 수 있도록 전환했습니다. 사진 업로드와 일정·댓글·좋아요·회고의 서버 자동 알림은 주석 처리하고 화면에서 제외했습니다. **Firebase 콘솔 FCM 테스트는 유지**합니다.

## 실제 프로젝트 연결

1. Firebase 프로젝트 `choaezip`에 Android 앱 `com.portfolio.choaezip`를 등록합니다.
2. 제공받은 설정 파일을 **`app/google-services.json`**에 넣습니다. Git 제외 파일이므로 GitHub에서 새로 받은 폴더에도 별도로 넣어야 합니다. 서비스 계정 JSON을 넣지 마세요.
3. Authentication → 로그인 방법에서 **이메일/비밀번호와 Google**을 활성화합니다. Android Studio에서 `./gradlew signingReport`로 실제 APK 서명의 SHA-1/SHA-256을 확인하여 Firebase Android 앱에 등록합니다. 설정을 변경했다면 JSON을 다시 받습니다.
4. Firestore Database에서 **기본 `(default)` 데이터베이스**를 생성합니다. 미국에 만들어도 앱을 바꿀 필요가 없습니다. 테스트 모드 전체 공개 규칙 대신 이 저장소의 규칙을 사용하세요.
5. 최신 코드를 받은 프로젝트 폴더에서 Firestore 규칙·인덱스만 배포합니다.

```bash
npm ci
npx firebase login
npx firebase deploy --project choaezip --only firestore
```

**Functions·Storage 배포와 Blaze 업그레이드는 필요하지 않습니다.** 새 규칙을 배포하지 않으면 이전의 직접 접근 차단 규칙 때문에 로그인 프로필/글 저장이 실패합니다. 인덱스 생성은 완료될 때까지 기다려 주세요. 이 클라우드에서 사용자 계정으로 실제 프로젝트에 배포하지는 않았습니다.

```bash
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. 실제 Firebase가 기본이며, 설정 파일이 없으면 빌드를 중단합니다. 출시 APK는 별도 릴리스 서명이 필요합니다.

## 데이터 저장 위치와 보안

| 경로 | 저장 내용 / 접근 |
| --- | --- |
| Firebase Authentication | Google·이메일 로그인 계정 |
| `users/{UID}` | 닉네임·소개·약관 동의·선호 아티스트. 본인만 읽기/수정 |
| `publicProfiles/{UID}` | 공개 닉네임·소개만 저장. 개인정보/권한 필드를 추가할 수 없음 |
| `posts/{글 ID}` | 공개 라운지 텍스트. 작성자만 수정·삭제 처리 |
| `posts/{글 ID}/comments/{댓글 ID}` | 댓글. 작성자만 삭제 |
| `posts/{글 ID}/likes/{UID}` | 계정별 좋아요. 하나만 생성 가능. 개수는 집계 조회 |
| `records/{기록 ID}` | 비공개 기록. 소유자만 읽기/쓰기 |
| `users/{UID}/blocks/{대상 UID}` | 차단. 목록은 본인만, 개별 관계는 당사자만 조회 |
| `reports/{신고 ID}` | 신고 내용. 작성자 조회 및 최초 생성만 허용; 처리 상태는 앱에서 변경 불가 |
| `artists/{아티스트 ID}`, `schedules/{일정 ID}` | 공개 조회. 일반 앱에서 쓰기 불가; 관리자가 콘솔에서 등록 |

가입 시 비공개/공개 프로필을 원자적으로 생성합니다. 서버 시각과 소유자/필드/길이/버전을 보안 규칙으로 검증합니다. 게시글은 서버 저장이 확인된 뒤에만 성공으로 표시하며 같은 저장 요청 재시도는 중복 글을 생성하지 않습니다. 로그아웃·앱 종료 후에도 글과 기록은 유지되며 일반 게시글에 자동 만료를 적용하지 않습니다. 디스크 Firestore 캐시는 사용하지 않고 조회는 서버에서 수행합니다.

차단한 계정의 글·댓글은 앱에서 걸러 표시하며 양방향 차단 관계의 새 댓글/좋아요는 규칙에서 거부합니다. 라운지는 원래 공개 공간이므로 차단이 공개 정보 자체를 비밀로 바꾸지는 않습니다. 신고는 콘솔에서 운영자가 검토합니다.

탈퇴는 본인 재인증 후 개인 기록·프로필·본인 댓글·좋아요·차단 목록을 정리한 뒤 Auth 계정을 삭제합니다. 본인 게시글은 본문을 지우고 `hidden=true`로 바꿔 공개 접근을 막습니다. 다른 사용자의 반응이 남아도 부모 글이 숨겨지면 공개 조회가 차단됩니다. Spark에는 서버 정리 작업이 없으므로 연결 오류 시 앱에서 다시 시도해야 합니다. 기존 Blaze 버전에서 사용한 Storage 파일이나 별도 컬렉션은 자동으로 정리하지 않습니다.

### 아티스트/일정 데이터

빈 데이터베이스에서도 글·기록 작성에서 **아티스트 미지정**을 선택할 수 있습니다. 지정 아티스트가 필요하면 Firestore 콘솔의 `artists`에 실제 데이터로 문서를 등록합니다.

```text
artists/{아티스트 ID}
  name: 표시 이름 (string)
  active: true (boolean)
  type: group 또는 member (string)
```

일정 문서는 `schedules/{일정 ID}`에 `artistId`, `title`, `body`, `location`, `type`(방송/컴백/공연/티켓), `status`(예정/취소/완료), `startAt`(epoch 밀리초 number), `updatedAt`(밀리초 number), `sourceName`, `sourceUrl`(HTTPS), `version`을 넣습니다. 앱은 서버에 실제 저장된 데이터만 보여줍니다. 아래 개발용 seed는 demo Emulator에만 가상 데이터를 만듭니다.

## FCM 콘솔 테스트

1. Google Play 서비스가 있는 기기에서 실제 Firebase 설정의 앱을 실행합니다.
2. **마이 → 알림 수신 확인 → 알림 권한 설정**에서 허용합니다.
3. **기기 토큰 가져오기 → 기기 토큰 복사**를 누릅니다. 로그인하지 않아도 됩니다.
4. Firebase 콘솔의 **같은 프로젝트 `choaezip` → Messaging → 알림 메시지 작성 → 테스트 메시지 전송**에 토큰을 넣습니다.
5. 먼저 앱을 보고 있는 상태에서 전송하여 확인한 뒤 홈 화면으로 나간 상태에서도 확인합니다. 강제 종료했다면 다시 앱을 실행하세요.

전경에서는 앱이, 백그라운드에서는 FCM SDK가 notification 메시지를 표시합니다. 기본 채널은 `공지와 테스트 알림`입니다. 자동 data 알림 수신과 서버 토큰 등록은 주석 처리되어 있습니다. 콘솔 알림은 앱 내부 알림함에 저장하지 않습니다. FCM 토큰은 공개 게시하지 마세요. 재설치/갱신 후 다시 복사합니다. 이 테스트에는 Functions·Storage·Blaze가 필요하지 않습니다.

## 로그와 오류 확인

Android Studio Logcat에서 **`tag:ChoaezipFirebase`**로 검색합니다. debug 빌드에서만 기록합니다.

- `firebase.config`: `backend=firestore-direct plan=Spark`, 프로젝트/패키지/Emulator 모드와 필요한 설정 존재 여부
- `auth.google.credential` → `auth.google.firebase`: 계정 선택 → Firebase 인증
- `auth.email.create`, `auth.email.login`: 이메일 가입·로그인
- `auth.profile.load`, `firestore.me`, `firestore.completeSignup`: Firestore 프로필 조회·생성
- `firestore.savePost`, `firestore.posts`: 라운지 저장·목록
- `auth.signup.validation`, `auth.profile.validation`: 입력에서 실패한 필드 이름
- `fcm.token`, `fcm.token.refresh`, `fcm.receive`, `fcm.display`: 발급·갱신·전경 수신·표시 요청

`START` 이후 같은 request 번호의 `OK`/`FAIL`, 예외 종류와 SDK 코드를 확인합니다. 초기화 OK만으로 실제 네트워크 연결 성공을 뜻하지 않습니다. `PERMISSION_DENIED`는 새 규칙 배포 여부, `FAILED_PRECONDITION`은 데이터베이스/인덱스를 확인합니다. 이메일 계정 생성 후 프로필 저장만 실패하면 계정을 다시 만들지 않고 가입 완료 화면에서 재시도합니다. 닉네임은 2~20자, 비밀번호는 8~64자이며 필수 동의가 필요합니다.

로그에는 이메일·비밀번호·UID·토큰·게시글·SDK 예외 메시지를 출력하지 않습니다. 실제 기기에서의 Google 로그인·FCM 전달은 해당 기기의 로그로 확인해야 합니다. 백그라운드 notification 메시지는 SDK가 처리하므로 앱 서비스의 수신 로그가 없을 수 있습니다.

## 개발과 검증

JDK 21, Android SDK 36 / Build Tools 35.0.0, Node.js 22 환경을 사용합니다. 이 클라우드에서는 매 셸에서 다음을 실행합니다.

```bash
source scripts/cloud-env.sh
npm ci
npm run emulators
```

다른 터미널:

```bash
./gradlew -PuseEmulators=true assembleDebug testDebugUnitTest lintDebug
```

로컬 개발 Emulator는 명시적 `-PuseEmulators=true`일 때만 연결합니다. Android 가상 기기 주소는 `10.0.2.2`, Auth 9099 / Firestore 8080입니다. 실제 기기는 `adb reverse tcp:9099 tcp:9099`, `adb reverse tcp:8080 tcp:8080` 후 `-PemulatorHost=127.0.0.1`로 빌드합니다. 운영 빌드와 demo 로그인 계정은 서로 공유되지 않습니다.

다른 Emulator를 종료한 상태에서:

```bash
npm test                      # Auth/Firestore Emulator: 가입·저장·재로그인·보안 규칙
npm run test:android-spark     # 실제 Kotlin SparkRepository를 같은 Emulator에 연결
```

Emulator 환경은 Blaze 계정이나 실제 프로젝트 배포가 필요하지 않습니다. 테스트는 demo 프로젝트만 사용합니다. `functions/`의 이전 서버 및 자동 알림 코드와 Android 사진/알림 UI는 주석으로 보존했으며 현재 배포 설정에서 제외했습니다. `firebase.blaze.disabled.json`은 이전 설정의 참고용 복사본이며 현재 명령에 사용하지 않습니다.

## 이전 실행 화면

`docs/screenshots/`의 PNG는 Spark 전환 이전 Android 10 Emulator 실행 캡처이며 가상 아티스트 LUMI 데이터가 포함됩니다. 현재 사진/자동 알림 UI와 저장 구조를 나타내는 최신 화면은 아닙니다. 검증 기록은 [docs/VALIDATION.md](docs/VALIDATION.md)를 참고하세요.
