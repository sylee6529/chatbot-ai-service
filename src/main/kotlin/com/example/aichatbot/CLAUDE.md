# CLAUDE.md

## Project Mission

Build a real, runnable Kotlin + Spring Boot backend for an AI chatbot service.

This is not only a coding exercise. Treat it as a real urgent customer demo:

* The API must actually run.
* The chatbot API must actually be usable.
* The repository must be understandable by a reviewer.
* A non-expert customer manual must be delivered.
* The codebase must remain extendable after the demo.

Tech constraints:

* Kotlin 1.9.x
* Spring Boot 3.x.x
* PostgreSQL 15.8
* JVM 17 or higher if required by Spring Boot 3
* Use Docker Compose for local PostgreSQL

## Operating Principles

Think before coding, but do not stop at design.

Before implementation:

* State assumptions briefly.
* Identify risky or ambiguous requirements.
* Choose the simplest working approach.
* Do not over-engineer speculative abstractions.
* Do not silently skip requirements.

During implementation:

* Work in small, verifiable steps.
* Prefer working end-to-end flow over isolated partial code.
* Every changed file should contribute to the assignment.
* Do not create decorative architecture that is not used.
* Keep package boundaries clear enough for later extension.

When uncertain:

* Prefer a reasonable backend-engineering default.
* Document the assumption in README or manual.
* Ask only when truly blocked.
* Do not wait for user confirmation for ordinary implementation choices.

## Priority Policy

Implement all requirements. Priorities exist to guide execution order, not to excuse missing features.

### P0: Demo-Critical Path

Finish these first and verify end-to-end:

1. Project bootstrapping

    * Kotlin + Spring Boot 3 project
    * PostgreSQL 15.8 via docker-compose
    * application profiles for local/test
    * Flyway or equivalent DB migration
    * health check or simple readiness endpoint

2. Authentication and users

    * signup
    * login
    * JWT access token
    * JWT authentication for all APIs except signup/login
    * roles: member, admin
    * password stored as hash, never plaintext
    * initial admin creation strategy documented

3. Chatbot core

    * create chat API
    * call actual AI provider when configured
    * store question and answer
    * create or reuse thread using the 30-minute rule
    * include previous chats in the same thread when requesting AI answer
    * model parameter support
    * safe behavior when AI provider key is missing, clearly documented

4. Chat retrieval

    * current member can view only own threads/chats
    * admin can view all threads/chats
    * response grouped by thread
    * sort by createdAt asc/desc
    * pagination

5. Manual deliverable

    * README.md for evaluator/developer
    * docs/manual.html for customer/demo usage
    * docs/manual.pdf generated from docs/manual.html
    * OpenAPI/Swagger UI enabled
    * curl examples or HTTP examples included

### P1: Requirement Completeness

After P0 works, complete these:

1. Thread deletion

    * member can delete only own thread
    * authorization failure handled clearly
    * deletion behavior documented: hard delete or soft delete

2. Feedback

    * create feedback for chat
    * member can create feedback only for own chat
    * admin can create feedback for any chat
    * one feedback per user per chat
    * list feedbacks
    * member sees own feedbacks only
    * admin sees all feedbacks
    * filter by positive/negative
    * sort and paginate
    * admin can update status: pending/resolved

3. Analysis and reporting

    * admin-only daily activity endpoint
    * count signups, logins, and chat creations within last 24 hours from request time
    * admin-only CSV report endpoint
    * CSV includes all chats from last 24 hours and the creating user

4. Streaming

    * support isStreaming=true
    * Prefer SSE/text-event-stream with Spring MVC SseEmitter if using Spring MVC
    * Do not migrate the entire project to WebFlux solely for streaming unless it is simpler
    * Non-streaming API must remain stable

### P2: Delivery Quality

Complete these after P0/P1 or in parallel when low-risk:

1. Tests

    * unit tests for thread 30-minute rule
    * auth/authorization tests
    * feedback uniqueness test
    * report date-range test
    * AI client mocked in tests
    * Testcontainers PostgreSQL if feasible

2. Developer experience

    * docker-compose up works
    * ./gradlew test passes
    * clear environment variable documentation
    * no secrets committed
    * sample .env.example

3. Documentation polish

    * API scenario walkthrough in manual
    * error response examples
    * authentication flow examples
    * admin flow examples
    * PDF export script documented
    * demo script: signup → login → chat → list chats → feedback → admin report

4. Future extension notes

    * RAG/document-learning extension plan
    * multi-provider AI extension plan
    * refresh token/rate limit/audit log notes
    * do not implement large speculative features unless core requirements are complete

## Backend Design Guidance

Do not treat this section as a fixed architecture. Use it as guidance.

Recommended boundaries:

* auth: signup, login, JWT, current user
* user: user entity, role
* chat: thread and chat domain logic
* ai: provider abstraction and OpenAI-compatible implementation
* feedback: feedback lifecycle
* report: activity and CSV generation
* common: errors, security helpers, response models

Keep domain rules in services, not controllers.

Important domain rules:

* A user can have many threads.
* A thread belongs to one user.
* A thread has many chats.
* A chat belongs to one thread and one user.
* A new thread is created when the user has no previous chat or the latest chat is older than 30 minutes.
* A chat within 30 minutes goes to the existing latest thread.
* Feedback uniqueness is user_id + chat_id.
* Feedback status is pending or resolved.
* Use timestamptz-compatible types. Prefer Instant for persistence.

Security requirements:

* Store password as BCrypt hash or another Spring Security PasswordEncoder hash.
* Never log passwords, JWTs, or provider API keys.
* JWT must include subject/user id and role or authorities.
* All protected endpoints must reject missing/invalid tokens.
* Authorization must be enforced server-side.
* Use consistent 401/403 responses.

AI integration requirements:

* Create an AI client boundary so provider can be replaced later.
* Current implementation may use OpenAI-compatible chat completion API.
* Read API key, base URL, and default model from environment/config.
* The model request parameter can override the default model.
* Include previous thread messages as context.
* Keep request/response DTOs small and explicit.
* In test profile, use a fake AI client.

Do not fake the demo by default. The real provider path should work when configured.

## Manual and Documentation Requirements

The customer has shallow API-spec knowledge. Therefore, do not rely only on Swagger.

Create these deliverables:

1. README.md
   Audience: evaluator/developer.
   Must include:

    * project overview
    * tech stack
    * run instructions
    * environment variables
    * database setup
    * test instructions
    * API documentation locations
    * implemented requirement checklist
    * assumptions and tradeoffs
    * future extension plan

2. docs/manual.html
   Audience: customer/non-expert demo participant.
   Must be a polished standalone HTML manual.
   Must include:

    * what this service does
    * quick start
    * demo scenario
    * how to sign up
    * how to log in
    * how to call chatbot API
    * how threads work in plain language
    * how feedback works
    * how admin reports work
    * sample curl commands
    * sample responses
    * common errors and fixes
    * environment/API key notes
    * print-friendly CSS

3. docs/manual.pdf
   Generate from docs/manual.html.
   Add a script such as scripts/export-manual-pdf.mjs.
   Prefer Playwright or another simple open-source tool.
   The PDF should be reproducible, not manually exported only.

4. OpenAPI/Swagger
   Add springdoc-openapi.
   Swagger UI should be available when the app runs.
   OpenAPI JSON/YAML should be available.
   JWT Bearer authentication should be documented.

5. Optional API client examples
   Add docs/demo.http or docs/curl-examples.sh.
   Include full demo flow.

## Verification Checklist

Before final response, run or provide the nearest possible result for:

* ./gradlew clean test
* docker compose config
* docker compose up path documented
* application starts locally
* migrations apply
* signup works
* login returns JWT
* protected API rejects no token
* chat creation works with configured AI or fake test provider
* thread 30-minute rule tested
* chat list groups by thread
* feedback uniqueness enforced
* admin-only endpoints reject member
* CSV report returns text/csv
* manual.html exists
* manual.pdf generation command exists
* README has requirement checklist

If any command cannot run in the environment, document why and what was checked instead.

## Final Reporting Format

At the end of the task, report:

1. What was implemented
2. How to run
3. How to run tests
4. Where the manual is
5. Where Swagger/OpenAPI is
6. Environment variables required
7. Any assumptions
8. Remaining risks or follow-up improvements

Do not claim success without verification.
