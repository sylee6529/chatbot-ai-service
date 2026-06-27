# TASKS.md — 단계별 작업 계획

모든 요구사항은 **반드시** 구현됩니다. 단계는 **포함 범위가 아니라 순서**를 정합니다.
각 단계에는 산출물, 검증 명령, 필요한 문서 업데이트가 명시됩니다.

범례: `[ ]` 미시작 · `[~]` 진행 중 · `[x]` 완료

우선순위 매핑(과제 기준): **P0** = Phase 1–4 + 문서 · **P1** =
Phase 5–7 · **P2** = Phase 8.

---

## Phase 0 — 문서 & 계획  ✅ (현재)

**산출물**
- [x] `CLAUDE.md` (빌드 규칙, 단계, 완료 정의)
- [x] `TASKS.md` (본 파일)
- [x] `docs/00-assumptions.md`
- [x] `docs/01-requirements.md` (추적 매트릭스)
- [x] `docs/02-api-contract.md`
- [x] `docs/03-data-model.md`
- [x] `docs/04-demo-scenario.md`
- [x] `docs/manual.html` (인쇄용 CSS + 플레이스홀더 포함 스켈레톤)

**검증**
```bash
ls CLAUDE.md TASKS.md
ls docs/00-assumptions.md docs/01-requirements.md docs/02-api-contract.md \
   docs/03-data-model.md docs/04-demo-scenario.md docs/manual.html
# 브라우저로 매뉴얼 레이아웃 + 인쇄 미리보기 확인:
open docs/manual.html    # macOS
```

**필요 문서 업데이트**: 없음 (이 단계 자체가 문서).

---

## Phase 1 — 프로젝트 부트스트랩 & 데이터베이스  ✅ (P0)

**산출물**
- [x] `build.gradle`: `web`, `security`, `validation`, `jjwt`, `flyway`,
      `springdoc-openapi-starter-webmvc-ui`, `h2`(test), 선택적 Testcontainers 추가.
- [x] `application.properties` (+ `application-local`, `application-test`):
      환경변수 기반 데이터소스, JWT, AI 설정.
- [x] **PostgreSQL 15.8**용 `docker-compose.yml`.
- [x] Flyway 마이그레이션: `V1__init.sql` (users, chat_threads, chats, feedbacks,
      activity_logs) — PostgreSQL + H2 호환.
- [x] 헬스/레디니스 엔드포인트 (`/api/v1/health` 또는 actuator).

**검증**
```bash
docker compose config                 # compose 파일 유효성
docker compose up -d                  # PostgreSQL 15.8 기동
./gradlew clean compileKotlin         # 컴파일
./gradlew bootRun &                   # 앱 부팅, Flyway V1 적용
curl -s localhost:8080/api/v1/health  # UP 반환
```

**문서 업데이트**: `docs/03-data-model.md`(컬럼 변경 동기화),
`README.md`(실행/환경 섹션 — 여기서 스텁 형태로 생성).

---

## Phase 2 — 인증 & 사용자  ✅ (P0)

**산출물**
- [x] `User` 엔티티 필드: `email`, `passwordHash`, `name`, `role`, `createdAt` + repository.
- [x] BCrypt `PasswordEncoder`; 이메일 형식 + 비밀번호 기본 강도 검증.
- [x] `POST /api/v1/auth/signup`, `POST /api/v1/auth/login` → access token(JWT).
- [x] `JwtService`(발급/검증), `JwtAuthenticationFilter`, `SecurityConfig`.
- [x] signup, login 외 모든 라우트는 인증 필요.
- [x] `@AuthenticationPrincipal` 현재 사용자 주입.
- [x] 초기 관리자 시딩(`ADMIN_EMAIL`/`ADMIN_PASSWORD`/`ADMIN_NAME`). README에 문서화.

**검증**
```bash
./gradlew test --tests '*Auth*'
curl -s -XPOST localhost:8080/api/v1/auth/signup \
  -H 'Content-Type: application/json' \
  -d '{"email":"alice@example.com","password":"Pw123456!","name":"Alice"}'
curl -s -XPOST localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"alice@example.com","password":"Pw123456!"}'
# 토큰 없이 보호 라우트 → 401
curl -s -o /dev/null -w '%{http_code}\n' localhost:8080/api/v1/chats
```

**문서 업데이트**: `docs/02-api-contract.md`(인증 페이로드 확정),
`docs/manual.html`(인증 플레이스홀더를 실제 curl로 교체), README.

---

## Phase 3 — 챗봇 코어  ✅ (P0)

**산출물**
- [x] `AiClient` 인터페이스; OpenAI 호환 구현; 설정 기반 키/baseUrl/모델.
- [x] `Thread`/`Chat` 엔티티 + repository.
- [x] `ChatService`의 30분 스레드 생성/재사용 규칙.
- [x] `POST /api/v1/chats` — 질문 + 답변 영속화, 채팅 + 스레드 id 반환.
- [x] 이전 스레드 컨텍스트를 AI에 전달.
- [x] `model` 오버라이드; 키 없을 때 폴백 동작(명시적 + 문서화).

**검증**
```bash
./gradlew test --tests '*Thread*' --tests '*Chat*'
TOKEN=...   # 로그인에서 획득
curl -s -XPOST localhost:8080/api/v1/chats -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"question":"Hello"}'
# 30분 이내 두 번째 호출은 같은 threadId 재사용
```

**문서 업데이트**: `docs/02-api-contract.md`(채팅 요청/응답),
`docs/04-demo-scenario.md`(검증 단계), `docs/manual.html`(챗봇 섹션).

---

## Phase 4 — 채팅 조회  ✅ (P0)

**산출물**
- [x] `GET /api/v1/chats` 스레드별 그룹핑; 멤버는 본인 것, 관리자는 전체.
- [x] `createdAt` 정렬 asc/desc; 페이지네이션(`page`, `size`).
- [x] 관리자용 선택적 `userId` 필터.

**검증**
```bash
./gradlew test --tests '*Retrieval*' --tests '*Authorization*'
curl -s "localhost:8080/api/v1/chats?page=0&size=10&sort=createdAt,desc" \
  -H "Authorization: Bearer $TOKEN"
```

**문서 업데이트**: `docs/02-api-contract.md`, `docs/manual.html`(스레드 섹션).

---

## Phase 5 — 스레드 삭제 & 피드백  ✅ (P1)

**산출물**
- [x] `DELETE /api/v1/threads/{id}` — 소유자 또는 관리자만; 하드/소프트 삭제 문서화.
- [x] `POST /api/v1/chats/{chatId}/feedbacks` — 본인 채팅(멤버)/모든 채팅(관리자).
- [x] `(user_id, chat_id)` 유일성 강제(DB 제약 + 서비스 검사 → `409`).
- [x] `GET /api/v1/feedbacks` — 역할별 범위, 감성 필터, 정렬, 페이징.
- [x] `PATCH /api/v1/feedbacks/{id}/status` — 관리자가 `PENDING`/`RESOLVED` 설정.

**검증**
```bash
./gradlew test --tests '*Feedback*' --tests '*ThreadDeletion*'
# 중복 피드백 → 409
curl -s -o /dev/null -w '%{http_code}\n' -XPOST \
  localhost:8080/api/v1/chats/1/feedbacks -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"isPositive":true}'
```

**문서 업데이트**: `docs/02-api-contract.md`, `docs/manual.html`(피드백 섹션).

---

## Phase 6 — 분석 & 리포팅  ✅ (P1)

**산출물**
- [x] `GET /api/v1/admin/activity` — 관리자 전용; 최근 **24시간** 가입·로그인·
      채팅 생성 수.
- [x] `GET /api/v1/admin/reports/chats.csv` — 관리자 전용; 최근 24시간 모든 채팅 +
      생성 사용자; `Content-Type: text/csv`.
- [x] 회원가입/로그인/채팅 생성 시 `activity_logs` 기록.

**검증**
```bash
./gradlew test --tests '*Report*' --tests '*Activity*'
curl -s localhost:8080/api/v1/admin/activity -H "Authorization: Bearer $ADMIN_TOKEN"
curl -s localhost:8080/api/v1/admin/reports/chats.csv \
  -H "Authorization: Bearer $ADMIN_TOKEN" -i | head -5   # text/csv 헤더 확인
# 멤버 토큰 → 403
```

**문서 업데이트**: `docs/02-api-contract.md`, `docs/manual.html`(관리자 리포트).

---

## Phase 7 — 스트리밍  (P1)

**산출물**
- [x] `isStreaming=true`인 `POST /api/v1/chats` → `SseEmitter` 통한 SSE
      (`text/event-stream`); 비스트리밍 경로 불변.
- [x] 최종 조립된 답변은 여전히 영속화.

**검증**
```bash
./gradlew test --tests '*Stream*'
curl -N -XPOST localhost:8080/api/v1/chats -H "Authorization: Bearer $TOKEN" \
  -H 'Accept: text/event-stream' -H 'Content-Type: application/json' \
  -d '{"question":"Stream please","isStreaming":true}'
```

**문서 업데이트**: `docs/02-api-contract.md`(스트리밍 섹션), `docs/manual.html`.

---

## Phase 8 — 테스트 & 전달 마무리  (P2)

**산출물**
- [x] 단위 테스트: 30분 규칙, 인증/인가, 피드백 유일성, 리포트 기간.
- [x] 테스트 프로파일에 가짜 `AiClient` 연결.
- [x] `docs/demo.http` 및/또는 `docs/curl-examples.sh` (전체 데모 흐름).
- [x] `.env.example`.
- [x] `README.md` 완성(개요, 실행, 환경, 테스트, 문서 링크, 체크리스트,
      가정, 향후 확장).
- [x] 향후 개선 목록: RAG, 다중 제공자, refresh token, 이메일 인증 링크, rate limit, audit logs.

**검증**
```bash
./gradlew clean test                 # 전체 스위트 통과
ls README.md .env.example docs/curl-examples.sh
```

**문서 업데이트**: 모든 문서 확정.

---

## 횡단 완료 정의

`CLAUDE.md` §8의 미러 — 기능은 테스트 통과, 명세대로의 인증 유무별 동작,
관련 문서 섹션(`02-api-contract`, `manual.html`) 업데이트가 모두 완료될 때까지
"완료"가 아닙니다.
