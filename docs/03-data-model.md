# 03 — 데이터 모델

대상: PostgreSQL **15.8** (테스트는 H2 PostgreSQL 호환 모드).
스키마는 **Flyway**가 소유합니다(`V1__init.sql`). 앱은 `ddl-auto=validate`로
스키마와 엔티티 매핑이 일치하는지 검증합니다.

모든 타임스탬프는 `timestamptz`(PostgreSQL) / `TIMESTAMP WITH TIME ZONE`(H2)이며,
애플리케이션에서 `Instant`(UTC)로 매핑됩니다. 모든 기본키는 `BIGINT` identity.

---

## 1. 엔티티 관계 개요

```
users (1) ───< (N) chat_threads (1) ───< (N) chats (1) ───< (N) feedbacks
  │                                                              │
  ├──────────────< (N) feedbacks (user_id) ─────────────────────┘
  └──────────────< (N) activity_logs (user_id, nullable)
```

- 한 `users` 행은 여러 `chat_threads`를 가짐 (1:N). **신원은 `email`**.
- 한 `chat_threads` 행은 여러 `chats`를 가짐 (1:N).
- 한 `chats` 행은 최대 하나의 사용자별 `feedbacks`를 가짐 (사용자-채팅당 1).
- `feedbacks`는 `users`(작성자)와 `chats`를 모두 참조.
- `activity_logs`는 SIGNUP/LOGIN/CHAT_CREATED 이벤트를 기록(분석용).

---

## 2. 테이블

### 2.1 `users`
계정과 역할. **신원은 이메일**이며, `name`은 사용자 이름입니다.

| 컬럼 | 타입 | 제약 | 비고 |
|------|------|------|------|
| `id` | BIGINT | PK, identity | |
| `email` | VARCHAR(255) | NOT NULL, **UNIQUE** | 로그인 식별자(신원), 소문자 정규화 저장 |
| `password_hash` | VARCHAR(100) | NOT NULL | **BCrypt** 해시, 평문 금지 |
| `name` | VARCHAR(100) | NOT NULL | 사용자 이름 |
| `role` | VARCHAR(20) | NOT NULL, CHECK in (`MEMBER`,`ADMIN`) | 기본 `MEMBER` |
| `created_at` | TIMESTAMPTZ | NOT NULL, 기본 now() | |
| `deleted_at` | TIMESTAMPTZ | NULL 허용 | 사용자 소프트 삭제 시각(현재 삭제 API 없음) |

인덱스:
- `UNIQUE(email)`. 로그인은 `email` 조회로 수행.
- `idx_users_deleted_at` on (`deleted_at`) — 삭제 제외 조회.

### 2.2 `chat_threads`
사용자의 대화 단위. 30분 규칙이 행 재사용/생성을 결정.

| 컬럼 | 타입 | 제약 | 비고 |
|------|------|------|------|
| `id` | BIGINT | PK, identity | |
| `user_id` | BIGINT | NOT NULL, FK → `users(id)` | 소유자 |
| `title` | VARCHAR(200) | NULL 허용 | 첫 메시지에서 파생 가능 |
| `created_at` | TIMESTAMPTZ | NOT NULL, 기본 now() | |
| `updated_at` | TIMESTAMPTZ | NOT NULL, 기본 now() | 새 채팅 추가 시 갱신 |
| `deleted_at` | TIMESTAMPTZ | NULL 허용 | 스레드 소프트 삭제 시각 |

인덱스:
- `idx_threads_user_id` on (`user_id`)
- `idx_threads_deleted_at` on (`deleted_at`) — 삭제 제외 조회.

### 2.3 `chats`
스레드 내 단일 질문/답변 교환.

| 컬럼 | 타입 | 제약 | 비고 |
|------|------|------|------|
| `id` | BIGINT | PK, identity | |
| `thread_id` | BIGINT | NOT NULL, FK → `chat_threads(id)` | 소속 스레드 |
| `user_id` | BIGINT | NOT NULL, FK → `users(id)` | 작성자(비정규화, 조회 편의) |
| `question` | TEXT | NOT NULL | 사용자 질문 |
| `answer` | TEXT | NOT NULL | AI 답변 |
| `model` | VARCHAR(100) | NULL 허용 | 사용된 모델 |
| `created_at` | TIMESTAMPTZ | NOT NULL, 기본 now() | 30분 규칙·정렬·리포트 기준 |
| `deleted_at` | TIMESTAMPTZ | NULL 허용 | 채팅 소프트 삭제 시각 |

인덱스:
- `idx_chats_thread_id` on (`thread_id`)
- `idx_chats_user_created` on (`user_id`, `created_at` DESC) — 사용자별 최신 채팅(30분 규칙) + 정렬.
- `idx_chats_created_at` on (`created_at`) — 24시간 리포트 범위 스캔.
- `idx_chats_deleted_at` on (`deleted_at`) — 삭제 제외 조회.

### 2.4 `feedbacks`
채팅에 대한 사용자 피드백. 사용자-채팅당 1개.

| 컬럼 | 타입 | 제약 | 비고 |
|------|------|------|------|
| `id` | BIGINT | PK, identity | |
| `chat_id` | BIGINT | NOT NULL, FK → `chats(id)` | 대상 채팅 |
| `user_id` | BIGINT | NOT NULL, FK → `users(id)` | 작성자 |
| `is_positive` | BOOLEAN | NOT NULL | 긍정(`true`) / 부정(`false`) |
| `status` | VARCHAR(10) | NOT NULL, CHECK in (`pending`,`resolved`) | 기본 `pending`, 관리자만 변경 |
| `created_at` | TIMESTAMPTZ | NOT NULL, 기본 now() | |
| `updated_at` | TIMESTAMPTZ | NOT NULL, 기본 now() | 상태 변경 시 갱신 |
| `deleted_at` | TIMESTAMPTZ | NULL 허용 | 피드백 소프트 삭제 시각 |

제약/인덱스:
- **`UNIQUE(user_id, chat_id)`** — 사용자-채팅당 1피드백(`409` 트리거). soft-deleted feedback이 있어도 같은 사용자/채팅 조합은 재사용하지 않음.
- `idx_feedbacks_user_id` on (`user_id`)
- `idx_feedbacks_is_positive` on (`is_positive`) — 긍정/부정 필터.
- `idx_feedbacks_status` on (`status`) — 상태 필터.
- `idx_feedbacks_deleted_at` on (`deleted_at`) — 삭제 제외 조회.

### 2.5 `activity_logs`
분석/리포트용 추가 전용 이벤트 로그.

| 컬럼 | 타입 | 제약 | 비고 |
|------|------|------|------|
| `id` | BIGINT | PK, identity | |
| `user_id` | BIGINT | NULL 허용, FK → `users(id)` | 이벤트 사용자. 사용자 소프트 삭제 후에도 값 보존 |
| `type` | VARCHAR(20) | NOT NULL, CHECK in (`SIGNUP`,`LOGIN`,`CHAT_CREATED`) | 이벤트 종류 |
| `created_at` | TIMESTAMPTZ | NOT NULL, 기본 now() | 24시간 윈도우 기준 |

인덱스:
- `idx_activity_type_created` on (`type`, `created_at`) — 유형별 24시간 집계.
- `idx_activity_created_at` on (`created_at`) — 윈도우 스캔.

> 세 유형(SIGNUP, LOGIN, CHAT_CREATED)을 하나의 테이블에서 관리하여 24시간 롤링
> 집계를 단일 쿼리로 처리합니다(`00-assumptions` F2). `LOGIN`은 access token 발급에
> 성공한 로그인만 기록합니다. 활동 로그 저장 실패는 핵심 작업을 롤백하지 않습니다.


---

## 3. 참조 무결성 & 삭제 정책

애플리케이션 레벨 삭제는 물리 삭제가 아니라 `deleted_at`을 채우는 **소프트 삭제**입니다.
FK는 참조 무결성만 보장하며, 애플리케이션은 일반 조회에서 `deleted_at IS NULL` 조건을
기본으로 적용합니다.

| 삭제 대상 | 처리 |
|-----------|------|
| 스레드 삭제 API | `chat_threads.deleted_at`, 해당 스레드의 `chats.deleted_at`, 해당 채팅들의 `feedbacks.deleted_at`을 같은 시각으로 갱신 |
| 사용자 삭제(향후 확장) | `users.deleted_at` 및 소유 스레드/채팅/피드백 `deleted_at` 갱신 |
| 활동 로그 | 삭제하지 않음. 사용자가 소프트 삭제되어도 집계 이력 보존 |

삭제된 데이터는 일반 조회, AI 컨텍스트, 피드백 목록, CSV 리포트에서 제외합니다.

유니크 제약은 `deleted_at`을 조건으로 한 partial unique index를 사용하지 않습니다.
PostgreSQL과 H2 테스트 환경의 차이를 줄이기 위해 soft-deleted row의 unique key도
재사용하지 않습니다. 예를 들어 삭제된 사용자와 같은 이메일로 재가입하거나, 삭제된
피드백과 같은 `(user_id, chat_id)` 조합으로 다시 생성하는 흐름은 지원하지 않습니다.

채팅 생성의 30분 규칙은 사용자 단위 동시성을 제어해야 합니다. 구현은 트랜잭션 안에서
대상 `users` 행을 `SELECT ... FOR UPDATE`로 잠근 뒤 최신 채팅 조회, 스레드 생성/재사용
결정, 채팅 저장을 순서대로 수행합니다.
재사용 대상 스레드는 별도 "최신 스레드" 조회가 아니라 최신 채팅의 `thread_id`로 도출합니다.

---

## 4. 핵심 쿼리 패턴 (인덱스 근거)

| 패턴 | 사용처 | 지원 인덱스 |
|------|--------|-------------|
| 사용자 row lock 후 가장 최근 채팅 1건 (삭제 제외) | 30분 규칙 | `idx_chats_user_created`, `idx_chats_deleted_at` |
| 사용자/전체 스레드 목록 (createdAt 정렬, 페이징, 삭제 제외) | 채팅 조회 | `idx_threads_user_id`, `idx_threads_deleted_at`, `idx_chats_thread_id`, `idx_chats_deleted_at` |
| 최근 24시간 채팅 (삭제 제외) | CSV 리포트 | `idx_chats_created_at`, `idx_chats_deleted_at` |
| 유형별 최근 24시간 이벤트 수 | 활동 집계 | `idx_activity_type_created` |
| 긍정/부정 및 상태별 피드백 필터 (삭제 제외) | 피드백 목록 | `idx_feedbacks_is_positive`, `idx_feedbacks_status`, `idx_feedbacks_deleted_at` |
| 피드백 중복 방지 | 피드백 생성 | `UNIQUE(user_id, chat_id)` |

---

## 5. 마이그레이션 노트

- `V1__init.sql` — 위 5개 테이블(users, chat_threads, chats, feedbacks,
  activity_logs) + `deleted_at` 컬럼 + 인덱스 + 제약 생성. PostgreSQL/H2 공통 SQL
  사용(벤더 종속 구문 회피; identity는 `BIGINT GENERATED BY DEFAULT AS IDENTITY` 또는
  H2 호환 형태로 작성).
- 향후 변경은 새 버전 파일로(`V2__...`, `V3__...`) — 기존 마이그레이션 수정 금지.
- 초기 관리자는 마이그레이션이 아니라 애플리케이션 시딩으로 생성
  (`00-assumptions` B2) — 비밀번호 해시를 SQL에 커밋하지 않기 위함.

---

## 6. 엔티티 매핑 요약 (구현 시 목표)

| 테이블 | JPA 엔티티 | 패키지 |
|--------|-----------|--------|
| `users` | `User` (+ `Role` enum) | `user` |
| `chat_threads` | `ChatThread` | `chat` |
| `chats` | `Chat` | `chat` |
| `feedbacks` | `Feedback` (+ `FeedbackStatus` enum) | `feedback` |
| `activity_logs` | `ActivityLog` (+ `ActivityType` enum) | `report` |

`updated_at`을 가진 엔티티는 `@PreUpdate`/감사 콜백으로 갱신합니다.
