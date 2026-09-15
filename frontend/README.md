# Sports Results Frontend

React/TypeScript interface for the public event catalog, Race-scoped protocols, and the organizer admin workspace.

## Commands

```powershell
pnpm install       # install locked dependencies
pnpm dev           # development server on http://127.0.0.1:5173
pnpm test          # public and admin contract/regression tests
pnpm lint          # Oxlint checks
pnpm build         # TypeScript check and production bundle
pnpm preview       # serve the built bundle locally
```

## API configuration

Without environment variables, the application calls same-origin `/api`; during `pnpm dev`, Vite proxies it to `http://localhost:8080`. Override the local proxy target with `VITE_API_PROXY_TARGET` when needed.

Cloudflare Pages must define the backend origin at build time:

```text
VITE_API_BASE_URL=https://sports-results-api.onrender.com
```

The shared URL builder appends `/api` and is used by public, admin, document, import, and XLSX requests. Direct attachment upload/download URLs are short-lived URLs returned by the backend or object storage and are not rewritten by the frontend.

Start PostgreSQL and the backend before browser testing. The public routes are:

- `/` — paginated event catalog with server-side year, series, city, and exact-date filters.
- `/events/{eventSlug}` — smart Event page: published results open the Race-scoped protocol first, while draft results open participant information first.
- `/events/{eventSlug}/results` — backward-compatible deep link to the same smart Event page.
- `/admin` and `/admin/events` — protected event administration.
- `/admin/events/{eventId}` — Event settings, formats/Race, categories/clusters, current registrations/results, imports, issues, ranking, and publication.
- `/admin/support/issues` — global Result Issue journal and filtered XLSX export.

## Admin UI

Start PostgreSQL and the backend with the required `app.admin` credentials, then run `pnpm dev` and open `http://127.0.0.1:5173/admin`. The login screen uses the backend's existing HTTP Basic security. Credentials remain in React runtime memory, are never written to browser storage, and must be entered again after a full refresh. Do not put passwords in this README or frontend environment files.

A local demo workflow is:

1. Create an EventSeries and Event, then add one or more Race starts. Event content supports ordered info blocks, schedule items, and PDF documents.
2. Configure AwardPolicy, Category, and optional StartCluster while Race results are `DRAFT`.
3. In «Импорт», choose an explicit mode (`ADD_NEW`, `UPDATE_EXISTING`, or `EMERGENCY_REPLACE`), select Race scope, upload CSV, inspect Preview, then Apply separately.
4. Review current Registration/Result data, apply pending category recalculation, and publish Event/results/Race explicitly.
5. Process Event-scoped issues or use the global journal to filter and export XLSX. Revoke the returned share batch when attachment links should stop working.

Admin mutations rely on backend validation and audit. The UI never auto-drafts a Race, auto-applies Preview, calculates ranking/categories, or silently chooses an import mode. Use only an isolated/demo PostgreSQL database for destructive manual smoke tests.

The Event decision uses only `resultsPublicationStatus`: result rows never publish a draft protocol. Published Events expose reusable «Результаты» and «О мероприятии» sections, and every protocol remains scoped to one Race. Name input is debounced; query composition and ordering are performed by PostgreSQL. For `GUN_TIME`/`CHIP_TIME`, the backend supplies stable official achievements plus a `prize` flag and the UI marks the ranking time as «Зачёт». Filtering and sorting never renumber these achievements. For `NONE`, the table instead shows query-relative «Место» from `displayPosition`, with no official badges. Category controls appear only when the Race policy enables category standing. Desktop uses a compact table, while narrow screens use cards. Keep API DTOs in `src/api/types.ts`, network calls in `src/api/client.ts`, and reusable presentation outside pages.

For visible changes, verify catalog navigation, empty/error states, result details, sorting/filter independence of achievement labels, and at least one 390 px mobile viewport. Do not add client-side award calculations or load complete result sets into the browser.
