# Sports Results Backend

Spring Boot API for importing timing CSV snapshots, publishing event protocols, and calculating configurable award standings.

## Local startup

Requirements: Java 21+ and PostgreSQL 17+. The repository includes Maven Wrapper and Docker Compose for the database.

```powershell
cd ..
docker compose up -d postgres
$env:ADMIN_USERNAME = "admin"
$env:ADMIN_PASSWORD = "choose-a-local-password"
cd backend
.\mvnw.cmd spring-boot:run
```

Defaults from `compose.yaml` can be overridden with `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`. HikariCP defaults to 12 maximum and 2 idle connections; use `DB_POOL_MAX_SIZE`, `DB_POOL_MIN_IDLE`, and `DB_POOL_CONNECTION_TIMEOUT_MS` to tune measured deployments. `ADMIN_PASSWORD` is mandatory. Administrative routes use stateless HTTP Basic; public routes require no authentication.

Browser CORS is restricted to the comma-separated exact origins in `CORS_ALLOWED_ORIGINS`; the local default is `http://127.0.0.1:5173,http://localhost:5173`, and wildcard origins are rejected. A deployed frontend must add its stable Cloudflare Pages/custom-domain origin. This API policy is separate from the object-storage bucket CORS required for direct attachment uploads.

Flyway applies V1–V24 automatically. V4 enables PostgreSQL `pg_trgm`, so the migration user needs permission to create that extension. Hibernate validates rather than creates the schema.

- API documentation: <http://localhost:8080/swagger-ui.html>
- OpenAPI JSON: <http://localhost:8080/v3/api-docs>

## Public API

- `GET /api/events` — published calendar/archive; filters include `UPCOMING`, `ONGOING`, and `PAST` derived phases.
- `GET /api/events/filter-options` — currently available years, series, and cities for combined filters.
- `GET /api/events/slug/{slug}` — public Event detail with participant info, schedule, documents, and ordered Race rules, categories, and start clusters.
- `GET /api/events/{id}/races` and `/categories?raceId=...` — Race options and policy-controlled category standings. `categoryStandingEnabled` and category options are exposed only when `AwardPolicy.categoryEnabled` is true.
- `GET /api/events/{id}/results?raceId=...` — one Race-scoped public protocol with server-side filtering, sorting, pagination, and structured `rankingAchievements`. `raceId` is mandatory; public API never combines Race protocols. It returns 404 while Results are `DRAFT`.
- `GET /api/events/{id}/documents/{documentId}/content` — inline public PDF, available only for a published Event and public document.
- `GET /api/results/{id}` — public result details and intermediate splits; date of birth and raw source category are deliberately excluded.

Official achievements are calculated on read from each Race's `AwardPolicy`, before display filters and independently from sorting or pagination. `GUN_TIME` and `CHIP_TIME` select the sport order; `NONE` means no organizer-defined standing and returns only a query-relative `displayPosition`. Every eligible participant receives a place in each applicable full standing; `prizePlaces` only sets the derived `prize` flag and never truncates achievements. Zero prize places therefore remain a valid official standing. `NONE` requires primary/category standings and prizes to be disabled. With primary-winner exclusion enabled, primary prize winners are removed before the category standing is ranked. Categories imported from CSV remain source data but do not enable a standing. Equal times share a competition rank (`RANK`, for example `1, 1, 3`); database ids only stabilize result ordering.

Age-category resolution keeps three concepts separate: `birthDate` and `sourceCategory` are immutable import facts, while `Registration.category_id` is the normalized effective category used by official standings. Birth date has priority; exact `Category.sourceName` fallback is allowed only when birth date is absent. `EVENT_DATE` uses the Event's local date in its IANA timezone; `END_OF_EVENT_YEAR` uses 31 December of that local year. Category, policy, Event date/timezone, and admin registration changes trigger scoped recalculation without reimport.

`AwardPolicy.rankingBasis` controls official places and public presentation; `Race.publicRankingBasis` is retained as a synchronized compatibility mirror. Imported `place` fields are source values, not official achievements. Public pages default to 50 rows; nullable values sort last. Bib remains exact text, preserving `00123` and `A123`.

## Event lifecycle and content

An Event exists before Results and drives both the future calendar and past archive—there is no separate calendar entity. Event page publication and Results publication are independent. Results can move `PUBLISHED → DRAFT → PUBLISHED` without hiding participant information or rules. Race is the Event-scoped start/distance and the only public protocol scope. Race visibility changes do not recalculate results or rankings. `EventScheduleItem` and `EventInfoBlock` are optional. `StartCluster` belongs to a Race and may be assigned to a Registration, but never creates a standing or changes existing achievements.

PDF metadata is stored in PostgreSQL while binary content goes through `FileStorageService`. Development uses `LocalFileStorageService`; configure `DOCUMENT_STORAGE_ROOT` and `DOCUMENT_MAX_SIZE_BYTES`. Storage keys are generated, paths stay private, and a future S3-compatible implementation can replace local storage without changing Event domain or API.

## Result issue attachment storage

The default `memory` adapter supports backend lifecycle tests but deliberately does not advertise browser uploads. Production uses the generic S3-compatible adapter and a private bucket:

```powershell
$env:RESULT_ISSUE_ATTACHMENT_STORAGE_PROVIDER = "s3"
$env:RESULT_ISSUE_ATTACHMENT_S3_ENDPOINT = "https://objects.example.net"
$env:RESULT_ISSUE_ATTACHMENT_S3_REGION = "region-1"
$env:RESULT_ISSUE_ATTACHMENT_S3_BUCKET = "private-result-issues"
$env:RESULT_ISSUE_ATTACHMENT_S3_ACCESS_KEY = "..."
$env:RESULT_ISSUE_ATTACHMENT_S3_SECRET_KEY = "..."
$env:RESULT_ISSUE_ATTACHMENT_S3_PATH_STYLE_ACCESS = "true"
```

Credentials have no repository defaults and must come from the deployment environment. Set path-style access according to the provider. The browser receives only short-lived presigned PUT/GET URLs; PostgreSQL stores the generated object key, never a public URL or credential. Configure bucket CORS for the exact public-site origin, `PUT`, and the signed request headers (normally `Content-Type`). Keep public bucket access disabled. Upload uses an ordinary single PUT; multipart upload remains a future adapter-level extension.

For a complete Windows setup with private local MinIO, exact CORS, direct browser PUT/GET, and the
development-only fake scanner, see `../local-dev/result-issue-attachments/README.md`. Docker is not required.

## Global Result Issue export

`POST /api/admin/result-issue-requests/export/xlsx` exports every row matching the Stage G journal filters, not only the current page. The request also accepts `shareLifetimeDays` (1–365, default 30) or explicit `noExpiry: true`. Configure the externally reachable application origin; it is never derived from the request `Host` header:

```powershell
$env:PUBLIC_BASE_URL = "https://results.example.net"
curl.exe -u "admin:$env:ADMIN_PASSWORD" -H "Content-Type: application/json" `
  -d '{"location":"Kazan","statuses":["NEW","IN_PROGRESS"],"shareLifetimeDays":30}' `
  -o result-issue-journal.xlsx `
  http://localhost:8080/api/admin/result-issue-requests/export/xlsx
```

The workbook contains application links such as `/share/attachments/{token}` only for `UPLOADED` + `CLEAN` attachments. The public endpoint validates the hashed, revocable grant and current attachment state, then redirects to a fresh five-minute S3/MinIO GET URL; the bucket remains private and Spring does not proxy file bytes. Revoke a complete export through `POST /api/admin/result-issue-share-batches/{batchId}/revoke`, or one link through `POST /api/admin/result-issue-attachment-share-grants/{grantId}/revoke`.

## Administrative workflow

Create an EventSeries first, then an Event that references it. The legacy import endpoint replaces the complete Event snapshot only after full parsing and validation succeeds.

```powershell
curl.exe -u "admin:$env:ADMIN_PASSWORD" -H "Content-Type: application/json" `
  -d '{"name":"M52","slug":"m52","description":null,"active":true}' `
  http://localhost:8080/api/admin/event-series

curl.exe -u "admin:$env:ADMIN_PASSWORD" -F "file=@../samples/results_m52_2025.csv" `
  http://localhost:8080/api/admin/events/1/imports
```

The Stage A preview endpoint is separate and read-only for Registration/Result data. It requires an explicit mode and one or more Race ids, stores only bounded operation metadata, and returns classifications, field diffs, totals, and blocking diagnostics. It does not apply the proposed changes.

```powershell
curl.exe -u "admin:$env:ADMIN_PASSWORD" `
  -F "file=@../samples/results_m52_2025.csv" `
  -F "mode=ADD_NEW" -F "raceIds=1" -F "rowLimit=100" `
  http://localhost:8080/api/admin/events/1/imports/preview
```

EventSeries and Event updates, Registration/Result edits, and AwardPolicy changes are available under `/api/admin/**`. Event-scoped endpoints manage participant info, schedule, info blocks, Race, clusters, documents, and the two publication statuses. The admin results query may search across all of an Event's races, but does not calculate a combined place. Existing Category CRUD remains Race-scoped. Changes are validated and audited. The operator UI is served by the frontend at `/admin`; it uses the existing HTTP Basic configuration and does not introduce JWT or RBAC.

## Verification

```powershell
.\mvnw.cmd test
.\mvnw.cmd verify
```

Integration tests launch an isolated real PostgreSQL instance. Performance fixtures and k6 instructions are in `../load-tests/`; do not point the 100k seed at a database containing valuable data.
