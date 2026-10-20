# Firebase 구조

## 접근 경계

Android → Firebase Auth → HTTPS callable `api` → Admin SDK → Firestore / Storage.

Firestore 클라이언트 읽기·쓰기는 모두 거부합니다. callable이 `req.auth`와 토큰 폐기/계정 정지 상태, 가입 완료, 소유권, 차단, 제한 계정을 검사합니다. Storage는 본인 계정의 신규 이미지 업로드/읽기/삭제만 허용합니다. 게시글 사진은 callable의 공개 여부 검사 후 단기 URL을 발급합니다. 서비스 계정 키는 앱에 필요하지 않습니다.

컬렉션:

- `artists`, `schedules`, `schedules/{id}/history`: 공개 데이터 / 운영자 변경 이력
- `users/{uid}`: 비공개 계정·설정. 공개 응답에는 닉네임·소개만 투영
- `users/{uid}/{blocks,reminders,notifications,devices}`: 계정 소유 데이터
- `records`: 작성자 전용, date·createdAt·ID 내림차순
- `posts/{id}/{comments,likes}`: 공개 글과 반응, hidden 및 상호 차단 필터
- `reports`: 신고자·대상·사유·처리 결과, 신고자/운영자 경계
- `requests`, `events`, `outbox`: 중복 방지와 DB 커밋에 연결된 알림
- `deletions`: 계정 차단 뒤 재시도 가능한 삭제 작업
- Storage `uploads/{uid}/{uuid}`: 이미지 및 `purpose=record|post` 메타데이터

## Callable 프로토콜

```javascript
const api = httpsCallable(getFunctions(app, 'asia-northeast3'), 'api');
await api({ action: 'saveRecord', artistId: 'lumi', title: '첫 공연',
  body: '잊지 못할 밤', date: '2026-10-20', category: '공연',
  media: [], requestKey: crypto.randomUUID() });
```

주요 actions: `artists`, `schedules(month,artistId,type)`, `schedule(id)`, `me`, `completeSignup`, `favorite`, `profile`, `records`, `record`, `saveRecord`, `deleteRecord`, `posts`, `post`, `savePost`, `deletePost`, `comments`, `comment`, `deleteComment`, `like`, `reminder`, `notifications`, `readNotification`, `preferences`, `device`, `blocks`, `block`, `report`, `media`, `logout`, `deleteAccount`.

목록은 `{items,cursor}`를 반환합니다. 기본 20 / 최대 50, 재조회 시 cursor를 전달합니다. 수정·삭제는 `expectedVersion`이 필요합니다. 충돌은 callable `aborted`, 권한은 `permission-denied`, 타인 기록과 숨김 글은 `not-found`로 응답합니다.

Firebase Auth 이메일 가입 직후 또는 첫 Google 로그인 직후 `completeSignup(nickname,terms=true,privacy=true)`를 호출합니다. 클라이언트가 전달한 admin·restricted 등의 권한 필드는 저장하지 않습니다.

관리자 action `adminSchedule`은 `id,artistId,title,body,location,type,status,startAt(epoch ms),sourceName,sourceUrl,expectedVersion`을 받습니다. `adminReviewReport(id,outcome)`은 `action_taken|no_violation`을 받습니다. 관리자 claim은 안전한 서버 운영 환경에서 Admin SDK로만 부여합니다.

## 알림

`dispatch`는 한국 시각 기준 매분 실행합니다. 일정 시작 30분 전·변경·취소, 댓글·최초 좋아요/한국 날짜당 1회, 연간 회고/윤년, 신고 처리 알림을 DB outbox와 연결합니다. 계정·대상·상호 차단·설정·권한·TTL을 전송 직전 검사하고 댓글·좋아요·회고는 22~08시 억제합니다. 알림함은 푸시 설정과 별개입니다. 재시도는 30초/2분/5분 예약 후 worker 실행 주기에 처리되며 정확히 초 단위 실행을 보장하지 않습니다.

FCM은 계정 ID·대상 ID·알림 ID와 일반 안내만 담으며 기록 본문·이메일·사진 URL을 넣지 않습니다. Android 알림 ID를 안정적으로 유지해 재전송 중복 표시를 줄입니다. 계정 제한 알림을 만드는 별도 운영자 제한 결정 API는 후속 범위이며, `restricted` 상태의 고객 기능 차단과 마이 안내는 구현했습니다.

## 배포 검수

`firestore.indexes.json`과 두 보안 규칙을 함께 배포하세요. rules의 Storage→Firestore 조회를 위한 Firebase 서비스 권한 안내가 배포 CLI에 표시되면 프로젝트 소유자가 설정해야 합니다. 프로덕션 Google 로그인에는 실제 서명 인증서와 OAuth 클라이언트, 미디어 서명 URL에는 Functions 서비스 계정의 서명 권한이 필요합니다.

실제 서비스에서 App Check 강제, 악용 방지/rate limit, 계정 존재 유추 방지 설정, 모니터링·알림·예산·리전·TTL·개인정보 보관 정책도 별도 운영 설정으로 검토하세요. 현재 callable은 App Check 강제를 켜지 않아 Emulator와 개발 클라이언트에서 테스트할 수 있습니다.
