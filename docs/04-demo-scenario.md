# 04 — 데모 시나리오

라이브 고객 데모에서 실행할 정확한 흐름입니다. 각 단계는 호출, 기대 결과,
확인 포인트를 포함합니다. 이 시나리오는 `docs/curl-examples.sh`/`docs/demo.http`
(Phase 8)와 `docs/manual.html`의 데모 섹션의 기준이 됩니다.

**흐름**: 회원가입 → 로그인 → 채팅 생성 → 30분 이내 두 번째 채팅 →
스레드별 그룹핑 목록 → 피드백 생성 → 관리자 일일 활동 → 관리자 CSV 리포트.

> 전제: 앱이 `localhost:8080`에서 구동 중이고 서버의 AI provider 연동이 완료되어 있습니다.
> `AI_API_KEY`가 비어 있으면 외부 provider 대신 키 미설정을 명시하는 deterministic fallback 답변이 반환됩니다.

---

## 0. 준비 — 앱 기동 (사전 단계)

```bash
docker compose up -d            # PostgreSQL 15.8
./gradlew bootRun               # 앱 기동, Flyway가 스키마 적용
curl -s localhost:8080/api/v1/health    # {"status":"UP"} 기대
```

확인: 헬스가 `UP`, Swagger UI는 토큰 인증 후 `http://localhost:8080/swagger-ui.html`.

---

## 1. 회원가입 (멤버)

```bash
curl -s -XPOST localhost:8080/api/v1/auth/signup \
  -H 'Content-Type: application/json' \
  -d '{"email":"alice@example.com","password":"Pw123456!","name":"Alice"}'
```

기대 `201`:
```json
{ "id": 1, "email": "alice@example.com", "name": "Alice", "role": "MEMBER", "createdAt": "..." }
```

확인: `role`이 `MEMBER`. 같은 **이메일** 재시도 → `409`.

---

## 2. 로그인 → JWT 획득

```bash
TOKEN=$(curl -s -XPOST localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"alice@example.com","password":"Pw123456!"}' | jq -r .accessToken)
echo "$TOKEN"
```

기대 `200`: `accessToken`, `tokenType: "Bearer"`, `user`.

확인: 토큰 없이 보호 라우트 호출 시 `401`:
```bash
curl -s -o /dev/null -w '%{http_code}\n' localhost:8080/api/v1/chats   # 401
```

---

## 3. 첫 채팅 생성 → 새 스레드 생성

```bash
curl -s -XPOST localhost:8080/api/v1/chats -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"question":"벡터 데이터베이스가 뭐야?"}'
```

기대 `201`:
```json
{
  "chatId": 10, "threadId": 3,
  "question": "벡터 데이터베이스가 뭐야?",
  "answer": "...", "model": "...", "createdAt": "..."
}
```

확인: 이전 채팅이 없었으므로 새 스레드가 생성됩니다.
`threadId`를 기록해 둡니다.

---

## 4. 30분 이내 두 번째 채팅 → 같은 스레드 재사용

```bash
curl -s -XPOST localhost:8080/api/v1/chats -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"question":"예시를 들어줘."}'
```

기대 `201`:
```json
{ "chatId": 11, "threadId": 3, "...": "..." }
```

확인: **`threadId`가 3단계와 동일**합니다. 30분 규칙이 최신 채팅을
30분 이내로 보고 스레드를 재사용함을 증명합니다.

> 30분 초과 후 재사용 안 됨을 보이려면: 30분 규칙은 단위 테스트로 시간을
> 제어해 검증합니다(라이브에서 30분 대기 불필요).

---

## 5. 스레드별 그룹핑 채팅 목록

```bash
curl -s "localhost:8080/api/v1/chats?page=0&size=20&sort=createdAt,desc" \
  -H "Authorization: Bearer $TOKEN"
```

기대 `200`: 하나의 스레드(`threadId: 3`) 아래에 두 채팅(10, 11)이
그룹핑되어 반환.

확인: 두 메시지가 **하나의 스레드 아래** 묶임. 정렬/페이지네이션 동작.
멤버는 본인 스레드만 봄.

---

## 6. 피드백 생성

```bash
curl -s -XPOST localhost:8080/api/v1/chats/10/feedbacks \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"isPositive":true}'
```

기대 `201`:
```json
{ "id": 5, "chatId": 10, "userId": 1, "isPositive": true,
  "status": "pending", "createdAt": "..." }
```

확인: 같은 채팅에 같은 사용자가 재시도 → **`409`**(사용자-채팅당 1피드백).
```bash
curl -s -o /dev/null -w '%{http_code}\n' -XPOST \
  localhost:8080/api/v1/chats/10/feedbacks -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"isPositive":false}'   # 409
```

---

## 7. 관리자 준비 — 관리자 로그인

초기 관리자는 환경변수로 시딩됨(`ADMIN_EMAIL`/`ADMIN_PASSWORD`,
`00-assumptions` B2).

```bash
ADMIN_TOKEN=$(curl -s -XPOST localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"'"$ADMIN_EMAIL"'","password":"'"$ADMIN_PASSWORD"'"}' | jq -r .accessToken)
```

확인: 멤버 토큰으로 관리자 엔드포인트 호출 시 `403`:
```bash
curl -s -o /dev/null -w '%{http_code}\n' \
  localhost:8080/api/v1/admin/activity -H "Authorization: Bearer $TOKEN"   # 403
```

---

## 8. 관리자 일일 활동 (최근 24시간)

```bash
curl -s localhost:8080/api/v1/admin/activity \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

기대 `200`:
```json
{ "windowStart": "...", "windowEnd": "...",
  "signups": 1, "logins": 2, "chatsCreated": 2 }
```

확인: 위 단계의 가입 1, 로그인(멤버+관리자), 채팅 2가 반영.

---

## 9. 관리자 CSV 리포트 (최근 24시간 채팅)

```bash
curl -s localhost:8080/api/v1/admin/reports/chats.csv \
  -H "Authorization: Bearer $ADMIN_TOKEN" -i | head -5
```

기대: `Content-Type: text/csv`, `Content-Disposition: attachment; ...`, 본문:
```
chat_id,thread_id,user_id,user_email,user_name,model,question,answer,chat_created_at
10,3,1,alice@example.com,Alice,...,"벡터 데이터베이스가 뭐야?","...",...
11,3,1,alice@example.com,Alice,...,"예시를 들어줘.","...",...
```

확인: 두 채팅이 생성 사용자(`alice@example.com`, `Alice`)와 함께 포함. 멤버 → `403`.

---

## 10. (선택) 스트리밍 채팅

```bash
curl -N -XPOST localhost:8080/api/v1/chats -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"question":"짧은 시 한 편 스트리밍해줘","isStreaming":true}'
```

기대: `text/event-stream` SSE — `event: chunk` 여러 개 후 `event: done`
(영속화된 `chatId` 포함).

확인: 스트리밍 답변도 정상 채팅으로 저장되어 목록/리포트에 나타남.

---

## 데모 체크리스트 (요약)

- [ ] 헬스 `UP`, Swagger 토큰 인증 후 접근 가능
- [ ] 회원가입 → `MEMBER`
- [ ] 로그인 → JWT, 토큰 없으면 `401`
- [ ] 첫 채팅 → 새 `threadId`
- [ ] 둘째 채팅(30분 이내) → 같은 `threadId`
- [ ] 목록 → 하나의 스레드에 두 채팅 그룹핑
- [ ] 피드백 생성 → `201`, 중복 → `409`
- [ ] 멤버의 관리자 엔드포인트 → `403`
- [ ] 관리자 활동 → 24시간 집계
- [ ] 관리자 CSV → `text/csv` + 두 채팅 + 사용자
- [ ] (선택) 스트리밍 → SSE 후 영속화
