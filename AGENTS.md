# Repository Guidelines

## Project Structure & Module Organization

- `backend/` — Java 21/Spring Boot: application code, Flyway migrations, and tests are under `src`.
- `frontend/` — React, TypeScript, and Vite code under `src`.
- `samples/` — immutable timing fixtures. Never edit or reorder columns.
- `load-tests/` — PostgreSQL seed and k6 scenarios.

## Architecture & Data Rules

Keep `Controller → Service → Repository → PostgreSQL`; expose DTOs. Core: `EventSeries → Event → SportFormat → Race → Registration → Result`. Public results require one Race; SportFormat never ranks. Effective visibility is `SportFormat.publicVisible && Race.publicVisible`; toggles never alter rankings or imported facts. Registration is the participant/team snapshot. Preserve nullable source fields, textual non-unique bibs, and imported places. Store durations as Java `Duration` and PostgreSQL `BIGINT` milliseconds. FINISH belongs to Result; Split is intermediate.

Event and Results publication are independent; Results `DRAFT` leaves Event public. Derive calendar/archive from Event dates. Use the Event IANA timezone and `EVENT_DATE` age. `AwardPolicy.rankingBasis` is authoritative; Race ranking fields are mirrors. GUN/CHIP places and prize flags never change with sorting, filters, search, or pagination. `NONE` creates no `RankingAchievement`; «Место» is the current query position. Prize counts control awarding, so zero prizes remain valid for GUN/CHIP. Hide public «Порядок протокола» sorting and mark the ranking time «Зачёт».

StartCluster is a nullable start grouping, not a ranking. Never infer `entryKind` or clusters from names/bibs. Keep `birthDate` and `sourceCategory` as source facts; `category_id` is effective. Resolve DOB first; use source mapping only without DOB.

Use `FileStorageService`; never expose storage paths. Never run destructive E2E against permanent imported data. Resolve CSV columns by header; separate parsing from snapshot application.

## Build, Test & Development Commands

```powershell
docker compose up -d postgres
cd backend
$env:ADMIN_PASSWORD = "local-secret"
.\mvnw.cmd spring-boot:run
.\mvnw.cmd test
.\mvnw.cmd verify

cd ..\frontend
pnpm install
pnpm dev
pnpm test
pnpm lint
pnpm build
```

Backend tests use embedded PostgreSQL; frontend proxies `/api` to port 8080.

## Coding Style & Testing

Use four-space Java indentation, constructor injection, record DTOs, and boundary validation. Use two-space TypeScript/CSS, PascalCase components, and camelCase helpers. Add Flyway migrations; never edit applied ones. Test domain/query changes against embedded PostgreSQL, CSV behavior with real fixtures, and UI changes with test, lint, build, and desktop/mobile checks.

## Commits & Pull Requests

Use imperative commits such as `Add event catalog filters`. PRs describe schema/API changes and verification. Include UI screenshots; never commit credentials or generated datasets.
