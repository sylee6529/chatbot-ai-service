# AIChatbot

Kotlin 1.9.25, Spring Boot 3.3.5, PostgreSQL 15.8 기반 AI 챗봇 백엔드입니다.

사용자가 질문을 입력하면 AI provider를 통해 답변을 생성하고, 질문과 답변을 대화 이력으로
저장하는 API 서버입니다. 대화는 사용자별 스레드로 묶이며, 마지막 질문 후 30분 이내의
후속 질문은 같은 스레드에서 이어집니다. 사용자는 답변에 피드백을 남길 수 있고,
관리자는 전체 대화와 피드백, 최근 24시간 활동 현황을 확인할 수 있습니다.

회원가입, 로그인, JWT 인증, 채팅 생성, Demo Knowledge Base 기반 문서 참고 답변,
30분 스레드 재사용, 스레드별 채팅 조회, 스레드 삭제, 피드백 관리, 관리자 활동 집계,
CSV 리포트, SSE 채팅 스트리밍을 제공합니다.

## 기술 스택

- Kotlin 1.9.25
- Spring Boot 3.3.5
- JVM 런타임: Java 17+
- PostgreSQL 15.8 (`docker-compose.yml`로 실행)
- Docker / Docker Compose

## 실행 방법(Local)

```bash
docker compose up -d
./gradlew bootRun
curl -s localhost:8080/api/v1/health
```

기대 응답:

```json
{"status":"UP"}
```

## 설정

로컬 기본 DB는 `docker-compose.yml`의 PostgreSQL을 사용합니다.
AI 연동은 OpenAI-compatible 설정(`AI_API_KEY`, `AI_BASE_URL`, `AI_DEFAULT_MODEL`)을 사용하며,
`AI_API_KEY`가 비어 있으면 외부 provider를 호출하지 않고 fallback 답변을 반환합니다.
비밀 값은 커밋하지 않고 셸 환경변수나 별도 비커밋 파일로 주입합니다.

Demo Knowledge Base는 문서 등록 API 없이 서비스 설명 문서를 앱 기동 시 seed합니다.
이는 fine-tuning이 아니라, 관련 문서 chunk를 검색해 AI 요청 context에 포함하는 RAG 데모입니다.
embedding은 `AI_EMBEDDING_MODEL` 기준으로 생성하며, 모델이 바뀌면 seed 단계에서 재생성합니다.
질문 embedding, chunk 검색, AI 호출은 채팅 저장 트랜잭션과 사용자 row lock 밖에서 수행합니다.

## Database

- Flyway 마이그레이션: `src/main/resources/db/migration/V1__init.sql`
- 운영/로컬: PostgreSQL
- 테스트: H2 PostgreSQL compatibility mode

## 테스트

```bash
./gradlew test
```

## Documentation

먼저 확인할 문서는 아래와 같습니다.




- `docs/manual.html` — 고객사 제공용 API 사용 매뉴얼입니다. 인증, 질문 생성, 대화 이력, 피드백, 관리자 기능을 이해할 수 있도록 작성했습니다. 웹 화면으로 열 수 있고, PDF로 출력해도 문서처럼 읽히는 구성을 의도했습니다.

<img width="1070" height="907" alt="image" src="https://github.com/user-attachments/assets/639487e1-7758-4eda-9c2b-f3eeab0c6c7e" />


- `docs/chatbot-ui.html` — 고객사가 실제 제품 화면을 통해 API 사용 흐름을 이해할 수 있도록 만든 데모 챗봇 화면입니다. 프론트엔드 요구사항은 없지만, 회원가입/로그인, 질문 입력, 스레드별 대화 이력, 피드백, 관리자 기능이 어떻게 사용자 경험으로 이어지는지 확인하기 위해 작성했습니다. 백엔드 API가 준비된 기능은 실제 호출하고, 아직 준비되지 않은 기능은 화면 흐름을 확인할 수 있도록 동작합니다.

<img width="1096" height="549" alt="image" src="https://github.com/user-attachments/assets/8674a36c-982d-4fa3-a845-7c2697e5ea91" />


설계와 검증을 위한 참고 문서는 아래와 같습니다.

- `docs/00-assumptions.md` — 설계 판단과 범위 결정
- `docs/01-requirements.md` — 요구사항 추적과 검증 기준
- `docs/02-api-contract.md` — API 요청/응답, 권한, 오류 응답 규약
- `docs/03-data-model.md` — 테이블 구조, 관계, 인덱스, 삭제 정책
- `docs/04-demo-scenario.md` — 기능 흐름 확인용 호출 순서

## 추후 개선 가능 목록

- 문서 등록/관리 API, 파일 파싱, pgvector/vector DB, 문서별 ACL
- 다중 AI provider 지원
- refresh token 및 이메일 인증
- 요청 rate limiting
- 감사 로그 보존 및 내보내기 정책
- 스레드 내부 채팅 페이지네이션
- 대용량 스레드 삭제 처리 최적화
