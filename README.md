# AIChatbot

Kotlin 1.9.25, Spring Boot 3.3.5, PostgreSQL 15.8 기반 AI 챗봇 백엔드입니다.

현재 구현 진행 기준은 `TASKS.md`입니다. Phase 1부터 Phase 7까지 완료되어
프로젝트 부트스트랩, DB 마이그레이션, 헬스체크, 회원가입, 로그인, JWT 인증,
채팅 생성, 30분 스레드 재사용, 스레드별 채팅 조회, 스레드 삭제, 피드백 관리,
관리자 활동 집계, CSV 리포트, SSE 채팅 스트리밍을 제공합니다.

## Requirements

- Java 17+
- Docker / Docker Compose
- PostgreSQL 15.8은 `docker-compose.yml`로 실행

## Run Locally

```bash
docker compose up -d
./gradlew bootRun
curl -s localhost:8080/api/v1/health
```

기대 응답:

```json
{"status":"UP"}
```

## Environment

| Name | Default | Description |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:15432/aichatbot` | JDBC URL |
| `DB_USERNAME` | `postgres` | DB 사용자 |
| `DB_PASSWORD` | `postgres` | DB 비밀번호 |
| `JWT_SECRET` | dev-only value | JWT 서명키. HMAC 서명을 위해 최소 32바이트 필요 |
| `JWT_ACCESS_TOKEN_TTL_SECONDS` | `3600` | access token TTL |
| `ADMIN_EMAIL` | empty | 값이 있으면 초기 관리자 계정 시딩 |
| `ADMIN_PASSWORD` | empty | 초기 관리자 비밀번호 |
| `ADMIN_NAME` | `Admin` | 초기 관리자 이름 |
| `AI_API_KEY` | empty | OpenAI-compatible API key |
| `AI_BASE_URL` | `https://api.openai.com/v1` | OpenAI-compatible base URL |
| `AI_DEFAULT_MODEL` | `gpt-4o-mini` | 기본 모델 |
| `AI_CONNECT_TIMEOUT_SECONDS` | `5` | AI provider 연결 타임아웃 |
| `AI_READ_TIMEOUT_SECONDS` | `30` | AI provider 응답 대기 타임아웃 |
| `CHAT_THREAD_WINDOW_MINUTES` | `30` | 스레드 재사용 시간 |

비밀 값은 커밋하지 않습니다. 로컬에서는 셸 환경변수나 별도 비커밋 파일로 주입합니다.
`AI_API_KEY`가 비어 있으면 외부 provider를 호출하지 않고 명시적인 deterministic fallback 답변을 반환합니다.
채팅 생성은 AI 호출을 DB 트랜잭션과 사용자 row lock 밖에서 수행하고, 저장 시점에 30분 스레드 규칙을 재검증합니다.
AI 호출 전 컨텍스트 스냅샷과 호출 후 최종 저장 스레드는 각각 최신 채팅 기준으로 계산합니다.
단일 사용자 저장은 row lock으로 직렬화되며, 스레드 내부 채팅 페이징은 향후 확장 항목입니다.

## Database

스키마는 Flyway가 관리합니다.

- 마이그레이션: `src/main/resources/db/migration/V1__init.sql`
- 운영/로컬: PostgreSQL
- 테스트: H2 PostgreSQL compatibility mode

## Useful Commands

```bash
docker compose config
./gradlew clean compileKotlin
./gradlew test
./docs/curl-examples.sh
```

## Auth Smoke Test

```bash
curl -s -XPOST localhost:8080/api/v1/auth/signup \
  -H 'Content-Type: application/json' \
  -d '{"email":"alice@example.com","password":"Pw123456!","name":"Alice"}'

TOKEN=$(curl -s -XPOST localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"alice@example.com","password":"Pw123456!"}' | jq -r .accessToken)

curl -s -o /dev/null -w '%{http_code}\n' localhost:8080/api/v1/chats
curl -s -o /dev/null -w '%{http_code}\n' localhost:8080/api/v1/chats \
  -H "Authorization: Bearer $TOKEN"
```

토큰이 없으면 `401`, 토큰이 있으면 채팅 API를 호출할 수 있습니다.

## Chat Smoke Test

```bash
curl -s -XPOST localhost:8080/api/v1/chats \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"question":"Hello"}'

curl -s "localhost:8080/api/v1/chats?page=0&size=10&sort=createdAt,desc" \
  -H "Authorization: Bearer $TOKEN"

curl -N -XPOST localhost:8080/api/v1/chats \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Accept: text/event-stream' \
  -H 'Content-Type: application/json' \
  -d '{"question":"Stream please","isStreaming":true}'
```

## Documentation

- `docs/00-assumptions.md`
- `docs/01-requirements.md`
- `docs/02-api-contract.md`
- `docs/03-data-model.md`
- `docs/04-demo-scenario.md`
- `docs/manual.html`

## Future Work

- RAG context retrieval
- Multiple AI providers
- Refresh token and email verification
- Rate limiting
- Audit log retention and export policy
- Thread-internal chat pagination
- Bulk soft-delete updates for large threads
