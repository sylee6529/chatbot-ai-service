# 01 — 요구사항 추적 매트릭스

과제의 모든 요구사항을 우선순위, 전달 단계, 명세 위치, 검증 방법과 함께
나열합니다. **제외되는 것은 없습니다.** 상태는 Phase 0(계획만) 기준이며,
구현 상태는 `TASKS.md`에서 추적합니다.

상태 범례: `계획됨`(설계 완료, 미구현) · `구현됨` · `검증됨`.

---

## P0 — 데모 핵심 경로

| ID | 요구사항 | 단계 | 명세 참조 | 검증 | 상태 |
|----|----------|------|-----------|------|------|
| P0-1 | Kotlin 1.9.x + Spring Boot 3.x + PostgreSQL 프로젝트 설정 | 1 | CLAUDE.md §2 | `./gradlew compileKotlin` | 계획됨 |
| P0-2 | PostgreSQL 15.8용 docker-compose | 1 | 03-data-model | `docker compose config` | 계획됨 |
| P0-3 | DB 마이그레이션 (Flyway) | 1 | 03-data-model | 앱 부팅 시 `V1` 적용; `flyway_schema_history` 채워짐 | 계획됨 |
| P0-4 | 회원가입(email/password/name) / 로그인(email/password) | 2 | 02-api-contract §Auth | `*Auth*` 테스트 + curl | 계획됨 |
| P0-5 | BCrypt 비밀번호 해싱 (+ 이메일 형식·비밀번호 복잡도 검증) | 2 | 00-assumptions A5 | 저장 해시 확인; 비밀번호 미로깅 | 계획됨 |
| P0-6 | JWT 발급 & 검증 | 2 | 00-assumptions A4 | 로그인 토큰 반환; 변조 토큰 → 401 | 계획됨 |
| P0-7 | member / admin 역할 | 2 | 00-assumptions B1 | JWT의 role; 관리자 전용 라우트가 멤버 거부 | 계획됨 |
| P0-8 | 보호된 엔드포인트(가입/로그인 제외) | 2 | 02-api-contract | 토큰 없는 요청 → 401 | 계획됨 |
| P0-9 | 채팅 생성 API | 3 | 02-api-contract §Chat | `POST /api/v1/chats`가 채팅 반환 | 계획됨 |
| P0-10 | OpenAI-compatible API 기반 AI 제공자 | 3 | 00-assumptions D1–D5 | `AiClient` 인터페이스; OpenAI-compatible 구현 | 계획됨 |
| P0-11 | 스레드 생성/재사용 — 30분 규칙 + 사용자별 동시성 제어 | 3 | 00-assumptions C1–C5 | 단위 테스트 `*Thread*`, 동시 요청 테스트 | 계획됨 |
| P0-12 | 채팅 영속화(질문 + 답변) | 3 | 03-data-model `chats` | 두 값 모두 저장된 행 | 계획됨 |
| P0-13 | 스레드별 그룹핑 채팅 목록, 멤버/관리자 인가 | 4 | 02-api-contract §Chat list | `*Retrieval*` / `*Authorization*` 테스트 | 계획됨 |
| P0-14 | 페이지네이션 + createdAt 정렬 | 4 | 00-assumptions I3 | `?page&size&sort` 쿼리 | 계획됨 |
| P0-15 | README.md | 8 (1에서 초안) | — | 체크리스트 포함 파일 존재 | 계획됨 |
| P0-16 | docs/manual.html | 0 | 본 저장소 | 파일 존재, 인쇄 CSS | **구현됨(스켈레톤)** |
| P0-17 | docs/manual.pdf 생성 경로 | 8 | scripts/export-manual-pdf.mjs | 스크립트 실행 → PDF | 계획됨 |

## P1 — 요구사항 완전성

| ID | 요구사항 | 단계 | 명세 참조 | 검증 | 상태 |
|----|----------|------|-----------|------|------|
| P1-1 | 인가 있는 스레드 삭제 | 5 | 02-api-contract §Thread | 소유자 삭제; 타인 → 403; `*ThreadDeletion*` | 구현됨 |
| P1-2 | 피드백 생성 | 5 | 02-api-contract §Feedback | `POST /chats/{id}/feedbacks` | 구현됨 |
| P1-3 | 사용자-채팅당 1피드백 | 5 | 00-assumptions E4, H5 | 중복 → 409; soft-deleted row 포함 유니크 제약 | 구현됨 |
| P1-4 | 인가/필터/정렬/페이징 피드백 목록 | 5 | 02-api-contract §Feedback list | `*Feedback*` 테스트 | 구현됨 |
| P1-5 | 관리자 피드백 상태 변경 | 5 | 00-assumptions E2 | `PATCH .../status`; 멤버 → 403 | 구현됨 |
| P1-6 | 최근 24시간 활동 집계 | 6 | 00-assumptions F1–F3 | `GET /admin/activity` 집계; 성공 로그인만 집계 | 구현됨 |
| P1-7 | 관리자 전용 CSV 리포트(최근 24시간) | 6 | 00-assumptions F4 | `text/csv`; 멤버 → 403 | 구현됨 |
| P1-8 | isStreaming=true 시 스트리밍 | 7 | 00-assumptions G1–G2 | SSE 응답; 비스트리밍 불변 | 구현됨 |

## P1-Demo — 시연 설득력 확장

명시 요구사항은 아니지만, 과제 배경의 "향후 자사의 대외비 문서를 학습시키고 싶다"는
고객 관심사를 MVP1 데모에서 보여주기 위한 제한된 확장입니다. 문서 등록 API나
파일 업로드 기능은 만들지 않고, 서비스 설명 seed 문서를 기반으로 RAG 흐름만 시연합니다.

| ID | 요구사항 | 단계 | 명세 참조 | 검증 | 상태 |
|----|----------|------|-----------|------|------|
| PX-1 | 서비스 설명 문서를 demo knowledge로 seed | 8.5 | 00-assumptions J1–J2 | 앱 기동 후 demo document/chunk 존재 | 계획됨 |
| PX-2 | 문서 chunking 및 embedding 저장 | 8.5 | 00-assumptions J3–J5, 03-data-model §2.6–2.7 | chunking 단위 테스트, Flyway V2 검증 | 계획됨 |
| PX-3 | 질문 embedding 기반 topK chunk 검색 | 8.5 | 00-assumptions J4, 03-data-model §4 | retrieval 단위 테스트 | 계획됨 |
| PX-4 | 채팅 생성 시 `useKnowledgeBase` 옵션 지원, 기본 `true` | 8.5 | 02-api-contract §2.1 | 기존 chat 테스트 + fallback 테스트 | 계획됨 |
| PX-5 | RAG context를 AI prompt에 주입하고 응답에 `sources` 반환 | 8.5 | 02-api-contract §2.1.2 | prompt context injection 테스트, API 응답 확인 | 계획됨 |
| PX-6 | 데모 시나리오와 매뉴얼에 문서 기반 답변 캡처 흐름 추가 | 8.5 | 04-demo-scenario §3 | `docs/curl-examples.sh`, `manual.html` 업데이트 | 계획됨 |

## P2 — 전달 품질

| ID | 요구사항 | 단계 | 명세 참조 | 검증 | 상태 |
|----|----------|------|-----------|------|------|
| P2-1 | 핵심 도메인 + 보안 규칙 테스트 | 8 | TASKS Phase 8 | `./gradlew clean test` 통과 | 계획됨 |
| P2-2 | AI provider 설정 문서화 | 8 | 00-assumptions D3 | `AI_API_KEY`, `AI_BASE_URL`, `AI_DEFAULT_MODEL` 설명 | 계획됨 |
| P2-3 | 데모 HTTP/curl 예제 | 8 | docs/demo.http | 파일 존재, 실행 가능 | 계획됨 |
| P2-4 | 샘플 환경 파일 | 8 | .env.example | 파일 존재, 실제 비밀 없음 | 계획됨 |
| P2-5 | 향후 확장 노트(문서 등록/관리 API, 파일 파싱, pgvector/vector DB, 제공자 전환, 이메일 인증 링크/SMTP, rate limit, audit logs) | 8 | README §Future | 섹션 존재 | 계획됨 |
| P2-6 | JWT로 보호되는 Swagger / OpenAPI | 8 | springdoc | `/swagger-ui.html`, `/v3/api-docs` | 계획됨 |
| P2-7 | 헬스 체크 엔드포인트 | 8 | 02-api-contract §개발 편의 | `/api/v1/health` | 계획됨 |

## 문서 요구사항

| ID | 요구사항 | 단계 | 검증 | 상태 |
|----|----------|------|------|------|
| D-1 | 평가자/개발자용 README | 8 | 섹션 완성 | 계획됨 |
| D-2 | 비전문가 고객용 docs/manual.html | 0 | 섹션 + 인쇄 CSS | **구현됨(스켈레톤)** |
| D-3 | 재현 가능하게 생성되는 docs/manual.pdf (Playwright) | 8 | 스크립트 실행 → PDF | 계획됨 |
| D-4 | 구동 앱에서 JWT 인증 후 Swagger/OpenAPI 제공 | 8 | `/swagger-ui.html` | 계획됨 |
| D-5 | 매뉴얼 설명: 서비스 개요, 인증, 챗봇 호출, 스레드, 30분 규칙, 피드백, 관리자 API, 일반 오류, curl, JSON 응답 | 0/8 | 매뉴얼 섹션 | **구현됨(스켈레톤)** |
| D-6 | 데모 시나리오 문서화 | 0 | 04-demo-scenario | **구현됨** |

## 매뉴얼 내용 체크리스트 (D-5 세부)

- [x] 이 서비스가 하는 일 *(스켈레톤)*
- [x] 인증 방법 *(스켈레톤)*
- [x] 챗봇 호출 방법 *(스켈레톤)*
- [x] 스레드의 의미 *(스켈레톤)*
- [x] 30분 규칙 동작 방식 *(스켈레톤)*
- [x] 피드백 동작 방식 *(스켈레톤)*
- [x] 관리자 API 종류 *(스켈레톤)*
- [x] 일반 오류 *(스켈레톤)*
- [x] 예제 curl 명령 *(플레이스홀더)*
- [x] 예제 JSON 응답 *(플레이스홀더)*

> 스켈레톤 섹션은 각 단계가 완료될 때 확정된 페이로드로 채워집니다
> (`TASKS.md`의 단계별 "문서 업데이트" 참조).
