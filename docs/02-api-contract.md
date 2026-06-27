# 02 — API 계약

기본 URL: `http://localhost:8080`  ·  접두사: **`/api/v1`**

`POST /auth/signup`과 `POST /auth/login`을 **제외한** 모든 엔드포인트는
JWT가 필요합니다: `Authorization: Bearer <token>`.

> 이 계약은 Phase 1–7의 설계 목표입니다. *(초안)* 표시된 페이로드는 각 단계가
> 완료될 때 다듬어질 수 있으며, 변경 사항은 여기와 `docs/manual.html`에 반영됩니다.

---

## 공통 규약

### 인증 헤더
```
Authorization: Bearer eyJhbGciOiJIUzI1NiI...
```

### 정상 응답
정상 응답은 별도 공통 wrapper 없이 각 엔드포인트의 도메인 객체를 바로 반환합니다.
목록 응답만 아래 페이지네이션 봉투를 사용합니다. 생성 API는 `201`, 조회/수정 API는
`200`, 삭제 API는 `204 No Content`를 사용합니다.

### 에러 응답
모든 `4xx/5xx` 오류는 동일한 JSON 봉투를 사용합니다. `message`는 클라이언트가
화면에 표시하거나 로그에 남길 수 있는 단일 문장으로 작성합니다. 필드별 오류 배열은
두지 않습니다.

```json
{
  "timestamp": "2026-06-27T05:00:00Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Missing or invalid token",
  "path": "/api/v1/chats"
}
```

대표 예시:

`400 Bad Request` — 요청 본문/파라미터 검증 실패
```json
{
  "timestamp": "2026-06-27T05:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "question must not be blank",
  "path": "/api/v1/chats"
}
```

`401 Unauthorized` — JWT 누락/무효/만료
```json
{
  "timestamp": "2026-06-27T05:00:00Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Missing or invalid token",
  "path": "/api/v1/chats"
}
```

`403 Forbidden` — 인증은 되었지만 역할/소유권이 맞지 않음
```json
{
  "timestamp": "2026-06-27T05:00:00Z",
  "status": 403,
  "error": "Forbidden",
  "message": "You do not have permission to access this resource",
  "path": "/api/v1/threads/3"
}
```

`404 Not Found` — 대상 리소스 없음
```json
{
  "timestamp": "2026-06-27T05:00:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Thread not found",
  "path": "/api/v1/threads/999"
}
```

`409 Conflict` — 유니크 제약/중복 요청
```json
{
  "timestamp": "2026-06-27T05:00:00Z",
  "status": 409,
  "error": "Conflict",
  "message": "Feedback already exists for this chat",
  "path": "/api/v1/chats/10/feedbacks"
}
```

### 표준 상태 코드
| 코드 | 본 API에서의 의미 |
|------|-------------------|
| 200  | 성공 |
| 201  | 생성됨 |
| 400  | 검증 오류 (잘못된 본문/파라미터) |
| 401  | JWT 누락/무효/만료 |
| 403  | 인증됨이나 권한 없음 (소유권/역할) |
| 404  | 리소스 없음 |
| 409  | 충돌 (예: 이메일 중복, 피드백 중복) |
| 502  | AI provider 응답 오류 또는 모델 처리 실패 |
| 504  | AI provider 요청 시간 초과 |

### 페이지네이션 (목록 엔드포인트)
쿼리 파라미터: `page`(0-base, 기본 0), `size`(기본 20),
`sort`(예: `createdAt,desc`).
목록 응답 봉투:
```json
{
  "content": [ /* items */ ],
  "page": 0,
  "size": 20,
  "totalElements": 42,
  "totalPages": 3,
  "sort": "createdAt,desc"
}
```

검증 기준:
- `page`는 0 이상이어야 합니다.
- `size`는 1 이상 100 이하입니다.
- `sort`는 허용된 필드와 방향만 받습니다. 기본 정렬은 `createdAt,desc`입니다.
- 잘못된 페이지네이션/정렬 값은 `400 Bad Request`로 응답합니다.

### 역할
- `MEMBER` — 기본. 본인 리소스에만 행위 가능.
- `ADMIN` — 모든 리소스에 행위 가능; `/admin/**` 단독 접근.

소유권 위반은 `403 Forbidden`으로 응답합니다. 존재하지 않거나 소프트 삭제되어
일반 조회 대상에서 제외된 리소스는 `404 Not Found`로 응답합니다.

---

## 1. 인증 (Auth)

> **신원은 이메일**입니다. `email`(유일, 소문자 정규화)로 가입·로그인하며,
> `name`은 사용자 이름입니다. 로그인 성공 시 **access token JWT만** 발급합니다.

### 1.1 회원가입 — `POST /api/v1/auth/signup`  · 공개
`MEMBER` 계정을 생성합니다.

요청:
```json
{ "email": "alice@example.com", "password": "Pw123456!", "name": "Alice" }
```
응답 `201`:
```json
{ "id": 1, "email": "alice@example.com", "name": "Alice", "role": "MEMBER", "createdAt": "2026-06-27T05:00:00Z" }
```
오류: `400`(이메일 형식 오류 / 약한 비밀번호), `409`(이메일 중복).

```bash
curl -s -XPOST localhost:8080/api/v1/auth/signup \
  -H 'Content-Type: application/json' \
  -d '{"email":"alice@example.com","password":"Pw123456!","name":"Alice"}'
```

### 1.2 로그인 — `POST /api/v1/auth/login`  · 공개
요청:
```json
{ "email": "alice@example.com", "password": "Pw123456!" }
```
응답 `200`:
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiI...",
  "tokenType": "Bearer",
  "user": { "id": 1, "email": "alice@example.com", "name": "Alice", "role": "MEMBER" }
}
```
오류: `401`(잘못된 자격 증명).

```bash
curl -s -XPOST localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"alice@example.com","password":"Pw123456!"}'
```


---

## 2. 채팅 (챗봇 코어)

### 2.1 채팅 생성 — `POST /api/v1/chats`  · 인증 필요
챗봇에 질문을 전송합니다. **스레드**는 OpenAI 요청에 함께 전달할 채팅 목록 단위이고,
**채팅**은 질문/답변 한 쌍입니다. 관계는 `User 1:N Thread`, `Thread 1:N Chat`입니다.

30분 스레드 규칙 적용: 호출자의 최신 채팅이 30분 이내면 그 채팅이 속한 스레드를
재사용하고, 아니면 새 스레드를 생성합니다. 같은 스레드의 이전 채팅들이 컨텍스트로
AI에 전달됩니다.

요청:
```json
{
  "question": "벡터 데이터베이스가 뭐야?",
  "model": "gpt-4o-mini",      // 선택; AI_DEFAULT_MODEL 오버라이드
  "isStreaming": false          // 선택; 기본 false (true는 §6 참조)
}
```
응답 `201` (비스트리밍):
```json
{
  "chatId": 10,
  "threadId": 3,
  "question": "벡터 데이터베이스가 뭐야?",
  "answer": "벡터 데이터베이스는 임베딩을 저장하는 ...",
  "model": "gpt-4o-mini",
  "createdAt": "2026-06-27T05:01:00Z"
}
```

오류: `400`(빈 질문 / model 빈 값·길이·문자 형식 오류 / 잘못된 isStreaming), `401`,
`502`(AI provider 응답 오류), `504`(AI provider 시간 초과).
`AI_API_KEY`가 설정되지 않은 로컬 환경에서는 외부 provider를 호출하지 않고,
키가 없음을 명시하는 deterministic fallback 답변을 반환합니다.

```bash
curl -s -XPOST localhost:8080/api/v1/chats -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"question":"벡터 데이터베이스가 뭐야?"}'
```

### 2.1.1 모델 지정
`model`은 현재 AI provider에 전달할 모델명입니다. 현재 구현은 OpenAI-compatible API
하나를 기본 provider로 사용하며, `model` 값만으로 provider를 자동 선택하지 않습니다.
Anthropic, Gemini 등 provider별 API 스펙 분기는 향후 `AiClient` 구현체 추가로 확장합니다.
애플리케이션은 `model`의 빈 값, 길이, 문자 형식만 검증합니다. 모델 존재 여부나 접근
가능 여부는 provider 응답으로 판단하며, provider가 모델 오류를 반환하면 `502`로 응답합니다.

### 2.2 스레드별 그룹핑 채팅 목록 — `GET /api/v1/chats`  · 인증 필요
채팅을 스레드 아래로 그룹핑하여 반환합니다. **MEMBER**는 본인 스레드만,
**ADMIN**은 전체(`userId`로 필터 가능).

쿼리: `page`, `size`, `sort=createdAt,desc|asc`, `userId` *(관리자 전용)*.

응답 `200` (스레드 단위 페이지네이션):
```json
{
  "content": [
    {
      "threadId": 3,
      "userId": 1,
      "createdAt": "2026-06-27T05:01:00Z",
      "updatedAt": "2026-06-27T05:10:00Z",
      "chats": [
        {
          "chatId": 10,
          "question": "벡터 데이터베이스가 뭐야?",
          "answer": "벡터 데이터베이스는 ...",
          "model": "gpt-4o-mini",
          "createdAt": "2026-06-27T05:01:00Z"
        },
        {
          "chatId": 11,
          "question": "예시를 들어줘.",
          "answer": "Pinecone, pgvector ...",
          "model": "gpt-4o-mini",
          "createdAt": "2026-06-27T05:10:00Z"
        }
      ]
    }
  ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1, "sort": "createdAt,desc"
}
```
페이지네이션은 **스레드 기준**입니다. `size=20`은 최대 20개의 스레드를 의미하며,
각 스레드 내부의 `chats`는 `createdAt` 오름차순으로 포함됩니다. `sort`는 스레드
정렬에 적용됩니다.

오류: `400`(잘못된 page/size/sort), `401`, `403`(멤버가 타인의 `userId` 전달).

```bash
curl -s "localhost:8080/api/v1/chats?page=0&size=20&sort=createdAt,desc" \
  -H "Authorization: Bearer $TOKEN"
```

---

## 3. 스레드 (Thread)

### 3.1 스레드 삭제 — `DELETE /api/v1/threads/{threadId}`  · 인증 필요
스레드와 그 하위 채팅/피드백을 소프트 삭제합니다. 삭제된 데이터는 일반 채팅 목록,
AI 컨텍스트, 피드백 목록, CSV 리포트에서 제외됩니다.
**MEMBER**는 본인 스레드만, **ADMIN**은 모든 스레드 삭제 가능.

응답 `204` (본문 없음).
이미 삭제된 스레드에 다시 삭제 요청이 오면 `404`로 응답합니다.
오류: `401`, `403`(소유자 아님, 관리자 아님), `404`(해당 스레드 없음 또는 이미 삭제됨).

```bash
curl -s -o /dev/null -w '%{http_code}\n' -XDELETE \
  localhost:8080/api/v1/threads/3 -H "Authorization: Bearer $TOKEN"
```

---

## 4. 피드백 (Feedback)

### 4.1 피드백 생성 — `POST /api/v1/chats/{chatId}/feedbacks`  · 인증 필요
`(user, chat)`당 1피드백. **MEMBER**는 본인 채팅만, **ADMIN**은 모든 채팅.

요청:
```json
{ "isPositive": true }
```
응답 `201`:
```json
{
  "id": 5, "chatId": 10, "userId": 1,
  "isPositive": true, "status": "pending",
  "createdAt": "2026-06-27T05:12:00Z"
}
```
삭제된 채팅 또는 삭제된 스레드에 속한 채팅은 피드백 생성 대상이 아니며 `404`로 응답합니다.
오류: `400`(잘못된 isPositive), `401`, `403`(본인 채팅 아님, 관리자 아님),
`404`(해당 채팅 없음 또는 삭제된 채팅), `409`(해당 사용자+채팅 피드백 이미 존재).

```bash
curl -s -XPOST localhost:8080/api/v1/chats/10/feedbacks \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"isPositive":true}'
```

### 4.2 피드백 목록 — `GET /api/v1/feedbacks`  · 인증 필요
**MEMBER**는 본인 피드백, **ADMIN**은 전체.

쿼리: `isPositive=true|false` *(선택)*, `status=pending|resolved`
*(선택)*, `page`, `size`, `sort=createdAt,desc|asc`.

응답 `200`:
```json
{
  "content": [
    {
      "id": 5, "chatId": 10, "userId": 1,
      "isPositive": true, "status": "pending",
      "createdAt": "2026-06-27T05:12:00Z"
    }
  ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1, "sort": "createdAt,desc"
}
```

```bash
curl -s "localhost:8080/api/v1/feedbacks?isPositive=true&page=0&size=20" \
  -H "Authorization: Bearer $TOKEN"
```

오류: `400`(잘못된 isPositive/status/page/size/sort), `401`.

### 4.3 피드백 상태 변경 — `PATCH /api/v1/feedbacks/{id}/status`  · ADMIN 전용
요청:
```json
{ "status": "resolved" }
```
응답 `200`:
```json
{ "id": 5, "status": "resolved", "updatedAt": "2026-06-27T05:20:00Z" }
```
오류: `400`(잘못된 status), `401`, `403`(멤버), `404`.

```bash
curl -s -XPATCH localhost:8080/api/v1/feedbacks/5/status \
  -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
  -d '{"status":"resolved"}'
```

---

## 5. 관리자 — 분석 & 리포팅

### 5.1 일일 활동 — `GET /api/v1/admin/activity`  · ADMIN 전용
**최근 24시간**(요청 시각 기준 롤링)의 활동 집계.

응답 `200`:
```json
{
  "windowStart": "2026-06-26T05:30:00Z",
  "windowEnd":   "2026-06-27T05:30:00Z",
  "signups": 4,
  "logins": 12,
  "chatsCreated": 37
}
```
`logins`는 성공하여 access token이 발급된 로그인만 집계합니다.
오류: `401`, `403`(멤버).

```bash
curl -s localhost:8080/api/v1/admin/activity \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

### 5.2 채팅 CSV 리포트 — `GET /api/v1/admin/reports/chats.csv`  · ADMIN 전용
최근 24시간 모든 채팅 + 생성 사용자.
응답 `200`, `Content-Type: text/csv`,
`Content-Disposition: attachment; filename="chats_last_24h.csv"`.

```
chat_id,thread_id,user_id,user_email,user_name,model,question,answer,chat_created_at
10,3,1,alice@example.com,Alice,gpt-4o-mini,"벡터 데이터베이스가 뭐야?","벡터 데이터베이스는 ...",2026-06-27T05:01:00Z
11,3,1,alice@example.com,Alice,gpt-4o-mini,"예시를 들어줘.","Pinecone, pgvector ...",2026-06-27T05:10:00Z
```
삭제된 채팅과 삭제된 스레드에 속한 채팅은 CSV에 포함하지 않습니다.
오류: `401`, `403`(멤버).

```bash
curl -s localhost:8080/api/v1/admin/reports/chats.csv \
  -H "Authorization: Bearer $ADMIN_TOKEN" -o chats_last_24h.csv
```

---

## 6. 스트리밍

### 6.1 스트리밍 채팅 — `isStreaming=true`인 `POST /api/v1/chats`  · 인증 필요
§2.1과 동일 엔드포인트. `isStreaming=true`와 `Accept: text/event-stream`을
함께 보낼 때 서버는 `SseEmitter`를 통해
**Server-Sent Events**(`Content-Type: text/event-stream`)로 응답합니다.
토큰/청크가 도착하는 대로 스트리밍되고, 마지막 이벤트가 영속화된 채팅 id를
담습니다. 전체 답변은 정상 채팅 행으로 저장됩니다.

스트림 시작 전 인증/검증 오류는 일반 JSON 에러 봉투로 응답합니다. 스트림이 시작된
뒤 AI provider 오류가 발생하면 일반 JSON 에러 봉투를 사용할 수 없으므로
`event: error`로 오류를 전달하고 스트림을 종료합니다. 이 경우 미완성 채팅은 저장하지 않습니다.

요청:
```json
{ "question": "짧은 시 한 편 스트리밍해줘", "isStreaming": true }
```
응답 `200` (SSE 스트림, 예시):
```
event: chunk
data: {"delta":"장미는 "}

event: chunk
data: {"delta":"붉고 "}

event: done
data: {"chatId":12,"threadId":3,"model":"gpt-4o-mini"}
```

스트림 중 오류 예시:
```
event: error
data: {"message":"AI provider request failed"}
```

```bash
curl -N -XPOST localhost:8080/api/v1/chats -H "Authorization: Bearer $TOKEN" \
  -H 'Accept: text/event-stream' \
  -H 'Content-Type: application/json' \
  -d '{"question":"짧은 시 한 편 스트리밍해줘","isStreaming":true}'
```

---

## 7. 개발 편의 엔드포인트

| 엔드포인트 | 인증 | 목적 |
|------------|------|------|
| `GET /api/v1/health` | 공개 | 레디니스 확인 (개발/운영 편의) |
| `GET /swagger-ui.html` | 인증 필요 | Swagger UI (평가자 확인 편의) |
| `GET /v3/api-docs` | 인증 필요 | OpenAPI JSON |

위 엔드포인트는 핵심 도메인 기능은 아니지만, 로컬 실행 상태 확인과 API 검증을
쉽게 하기 위해 제공합니다. `/api/v1/health`만 상태 확인을 위해 공개하고, Swagger와
OpenAPI 문서는 일반 보호 엔드포인트와 동일하게 JWT 인증을 요구합니다.

---

## 인가 요약

| 엔드포인트 | MEMBER | ADMIN | 공개 |
|------------|:------:|:-----:|:----:|
| `POST /auth/signup` | — | — | ✅ |
| `POST /auth/login` | — | — | ✅ |
| `POST /chats` | ✅ | ✅ | — |
| `GET /chats` | 본인 | 전체 (+`userId`) | — |
| `DELETE /threads/{id}` | 본인 | 모두 | — |
| `POST /chats/{id}/feedbacks` | 본인 채팅 | 모든 채팅 | — |
| `GET /feedbacks` | 본인 | 전체 | — |
| `PATCH /feedbacks/{id}/status` | ❌ 403 | ✅ | — |
| `GET /admin/activity` | ❌ 403 | ✅ | — |
| `GET /admin/reports/chats.csv` | ❌ 403 | ✅ | — |
| `GET /api/v1/health` | ✅ | ✅ | ✅ |
| `GET /swagger-ui.html`, `GET /v3/api-docs` | ✅ | ✅ | — |
