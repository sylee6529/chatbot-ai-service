# CLAUDE.md

이 파일은 본 저장소에서 작업하는 모든 AI/개발자를 위한 지침입니다.
**무엇을, 어떤 순서로 만들고, "완료"가 무엇을 의미하는지**에 대한 단일 기준입니다.

> 단계별 상세 작업 분해는 [`TASKS.md`](./TASKS.md)에 있습니다.
> 확정된 결정 사항과 가정은 [`docs/00-assumptions.md`](./docs/00-assumptions.md)에 있습니다.

---

## 1. 프로젝트 미션

실제로 구동되는 **Kotlin + Spring Boot** 기반 AI 챗봇 백엔드를 구축합니다.

이것은 장난감 프로토타입이 **아닙니다**. 긴급한 실제 고객 데모로 취급합니다.

- API는 실제로 동작해야 합니다.
- 챗봇 API는 처음부터 끝까지(end-to-end) 실제로 사용 가능해야 합니다.
- 저장소는 평가자가 이해할 수 있어야 합니다.
- 비전문가용 **고객 매뉴얼**(`docs/manual.html` → `docs/manual.pdf`)을 제공해야 합니다.
- 데모 이후에도 확장 가능해야 합니다(RAG, 다중 AI 제공자 등).

## 2. 고정 기술 제약

| 항목         | 결정                                                  |
| ------------ | ----------------------------------------------------- |
| 언어         | Kotlin **1.9.x** (현재 1.9.25)                        |
| 프레임워크   | Spring Boot **3.x.x** (현재 3.3.5)                    |
| JVM          | Java 17+ (Gradle toolchain 17 고정)                  |
| 데이터베이스 | PostgreSQL **15.8** (Docker Compose 사용)            |
| 마이그레이션 | Flyway (PostgreSQL + H2 호환 SQL)                    |
| 인증         | JWT (Bearer), BCrypt 비밀번호 해싱                   |
| 역할         | `MEMBER`, `ADMIN`                                     |
| API 문서     | springdoc-openapi (Swagger UI)                       |
| 테스트 DB    | H2 인메모리 (`./gradlew test`가 Docker 없이 동작)    |
| 빌드 DSL     | Groovy `build.gradle` (Kotlin DSL 아님)              |

`signup`과 `login`을 **제외한** 모든 엔드포인트는 유효한 JWT가 필요합니다.

## 3. 운영 원칙

코딩 전에 충분히 생각하되, 설계에서 멈추지 않습니다.

- 가정을 간결히 명시한 뒤 합리적인 백엔드 기본값으로 진행합니다.
- 요구사항을 **조용히 건너뛰지 않습니다**. 우선순위는 *포함 여부*가 아니라 *순서*를 정합니다.
- 어떤 요구사항도 "제외"로 표시하지 않습니다.
- 투기적 아키텍처보다 동작하는 end-to-end 흐름을 우선합니다.
- 도메인 규칙은 컨트롤러가 아니라 **서비스**에 둡니다.
- 변경된 모든 파일은 과제에 기여해야 합니다.
- 비밀 정보를 절대 커밋하지 않습니다. JWT 시크릿, AI 키/베이스 URL/모델은 환경변수에서 읽습니다.
- AI 키가 없으면 명시적으로 동작(스텁/에코)하고 **문서화**합니다 —
  실제 AI 호출이 성공한 것처럼 위장하지 않습니다.

## 4. 단계별(Phased) 전달 모델

작업을 단계로 나누어 전달합니다. 각 단계는 독립적으로 검증 가능해야 합니다.

- **Phase 0 — 문서 & 계획** *(현재 단계)*: 요구사항, 가정, API 계약,
  데이터 모델, 데모 시나리오, 고객 매뉴얼 스켈레톤, 작업 계획.
- **Phase 1 — 프로젝트 부트스트랩 & DB**: 의존성, 프로파일, docker-compose,
  Flyway 마이그레이션, 헬스 체크.
- **Phase 2 — 인증 & 사용자**: 회원가입, 로그인, JWT, 역할, 보호된 엔드포인트.
- **Phase 3 — 챗봇 코어**: AI 클라이언트 경계, 30분 스레드 규칙,
  채팅 영속화, 컨텍스트 포함, 모델 오버라이드.
- **Phase 4 — 조회**: 스레드별 그룹핑 채팅 목록, 소유자/관리자 범위, 정렬, 페이지네이션.
- **Phase 5 — 스레드 삭제 & 피드백**: 소유권 검사 삭제, 피드백 생명주기,
  사용자-채팅당 1피드백, 필터/정렬/페이징, 상태 변경.
- **Phase 6 — 분석 & 리포팅**: 최근 24시간 활동 집계, 관리자 전용 CSV 리포트.
- **Phase 7 — 스트리밍**: `isStreaming=true` 시 SSE(`SseEmitter`).
- **Phase 8 — 테스트 & 마무리**: 도메인/보안 테스트, 가짜 AI 제공자,
  데모 `.http`/curl, `.env.example`, README, manual.pdf 생성, 향후 확장 노트.

> 체크박스, 검증 명령, 단계별 문서 업데이트는 `TASKS.md`를 참고하세요.

## 5. 도메인 규칙 (기준)

- 사용자 필드(원문 명시): `email`(신원, 유일), `password`, `name`(이름), `created_at`, `role`.
- 한 사용자는 여러 스레드를 가질 수 있고, 한 스레드는 한 사용자에 속합니다.
- 한 스레드는 여러 채팅을 가지며, 한 채팅은 하나의 스레드와 하나의 사용자에 속합니다.
- **30분 규칙**: 새 채팅은 사용자의 가장 최근 스레드의 최신 채팅이
  **30분 이내**이면 그 스레드를 재사용하고, 그렇지 않으면 **새 스레드**를 생성합니다.
  (이전 채팅이 없으면 ⇒ 새 스레드.)
- AI에게 질문할 때 **같은 스레드**의 이전 채팅들을 컨텍스트로 포함합니다.
- 피드백 유일성 키 = `(user_id, chat_id)`.
- 피드백 상태 ∈ `{PENDING, RESOLVED}`; 감성 ∈ `{POSITIVE, NEGATIVE}`.
- timestamptz 친화 타입 사용. `Instant`(UTC)로 영속화.

## 6. 보안 요구사항

- **신원(identity)은 이메일**: `email`(유일)로 가입·로그인. 사용자 필드: email, password, name, 생성일시, role.
- BCrypt (Spring Security `PasswordEncoder`). 평문 저장 금지.
- 이메일 형식 검증 + 비밀번호 기본 강도 검증.
- **access token(JWT)만** 발급. refresh token은 향후 개선 목록.
- 비밀번호, JWT, 제공자 API 키를 절대 로깅하지 않음.
- JWT 클레임: subject(사용자 id), email, role.
- 모든 보호된 엔드포인트는 토큰 누락/무효 시 `401` 반환.
- 인가(소유권/역할)는 서비스에서 **서버 측**으로 강제; 위반 시 `403`.
- `400/401/403/404/409`에 대해 일관된 에러 봉투(envelope) 사용.

## 7. AI 연동 요구사항

- 제공자를 나중에 교체할 수 있도록 제공자 비종속 `AiClient` 경계.
- 기본 구현은 **OpenAI 호환** 채팅 컴플리션 API를 대상으로 함.
- 키, 베이스 URL, 기본 모델은 환경/설정에서 가져옴.
- 요청의 `model` 파라미터는 기본 모델을 오버라이드.
- 이전 스레드 메시지를 컨텍스트로 포함.
- **테스트** 프로파일에서는 **가짜(fake)** `AiClient` 사용(결정적, 네트워크 없음).

## 8. 완료 정의 (과제 기준)

아래 해당 항목들이 충족될 때에만 단계/기능이 완료됩니다.

- `./gradlew clean test` 통과 (또는 실행 불가 사유 문서화).
- `docker compose config` 유효; `docker compose up` 경로 문서화.
- 앱 로컬 기동; 마이그레이션 적용; Swagger UI 접근 가능.
- 회원가입 동작; 로그인 시 JWT 반환; 보호 API가 토큰 없음 거부.
- 설정된 AI **또는** 가짜 테스트 제공자로 채팅 생성 동작.
- 30분 규칙 테스트로 커버.
- 채팅 목록이 스레드별 그룹핑; 페이지네이션 + 정렬 검증.
- 피드백 유일성 강제; 관리자 전용 엔드포인트가 멤버 거부.
- CSV 리포트가 `text/csv` 반환.
- `README.md`, `docs/manual.html`, `docs/manual.pdf` 생성 명령, Swagger/OpenAPI 모두 존재.

**검증 없이 성공을 주장하지 않습니다.**

## 9. 저장소 맵 (목표)

```
.
├── CLAUDE.md                  # 본 지침
├── TASKS.md                   # 단계별 계획 + 검증
├── README.md                  # 평가자/개발자 문서 (Phase 8)
├── docker-compose.yml         # PostgreSQL 15.8 (Phase 1)
├── .env.example               # 샘플 환경변수 (Phase 8)
├── build.gradle / settings.gradle
├── docs/
│   ├── 00-assumptions.md      # 확정 결정
│   ├── 01-requirements.md     # 요구사항 추적 매트릭스
│   ├── 02-api-contract.md     # 엔드포인트, 요청, 응답, 인가
│   ├── 03-data-model.md       # 테이블, 관계, 제약, 인덱스
│   ├── 04-demo-scenario.md    # 실제 데모 흐름
│   ├── manual.html            # 고객용 매뉴얼 (→ PDF)
│   ├── manual.pdf             # 생성물 (Phase 8)
│   ├── demo.http / curl-examples.sh  # (Phase 8)
│   └── scripts/export-manual-pdf.mjs # Playwright 내보내기 (Phase 8)
└── src/
    └── main/kotlin/com/example/aichatbot/
        ├── config/    # security, jwt filter, openapi, data init
        ├── common/    # errors, ApiError, page DTOs, current-user
        ├── auth/      # signup, login, JWT 발급/검증
        ├── user/      # User 엔티티, Role, repository
        ├── chat/      # Thread/Chat 도메인, 30분 규칙, 스트리밍
        ├── ai/        # AiClient 경계 + OpenAI 호환 + Fake
        ├── feedback/  # 피드백 생명주기
        └── report/    # 활동 집계 + CSV
```
