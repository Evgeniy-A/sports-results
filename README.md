# Sports Results

Веб-сервис для управления, проверки и публикации результатов спортивных мероприятий.

Проект объединяет рабочее пространство организатора и хронометража с публичным сайтом для участников. Результаты загружаются из файлов хронометража, проходят проверку и обработку, после чего могут быть опубликованы в Race-ориентированном протоколе.

> Статус проекта: **MVP / активная разработка**

## Демо

### Публичный сайт

**https://sports-results.pages.dev/**

Здесь доступны опубликованные мероприятия, информация для участников и публичные протоколы результатов.

### Административная панель

**https://sports-results.pages.dev/admin/events**

Тестовый доступ:

- **Логин:** `admin`
- **Пароль:** `admin`

> Демо-среда используется для разработки и тестирования и содержит тестовые данные. Учетные данные выше не должны использоваться для production-доступа или других сервисов.

## Что умеет система

- создание и настройка спортивных мероприятий;
- группировка мероприятий в серии;
- работа с отдельными стартами / дистанциями (`Race`);
- настройка правил награждения;
- поддержка `GUN_TIME` и `CHIP_TIME`;
- возрастные категории и правила их определения;
- стартовые кластеры;
- импорт результатов из CSV и XLSX;
- предварительный анализ и Preview перед применением импорта;
- отдельные режимы добавления, обновления и аварийной замены данных;
- ручная проверка и редактирование регистраций и результатов;
- независимая публикация информации о мероприятии и результатов;
- возврат результатов из `PUBLISHED` в `DRAFT`;
- публичные Race-ориентированные протоколы;
- фильтрация, сортировка, поиск и пагинация;
- промежуточные отсечки / splits;
- PDF-документы мероприятия;
- обращения участников по опубликованным результатам;
- вложения к обращениям и выгрузка журнала в XLSX;
- административный интерфейс;
- API-документация через OpenAPI / Swagger;
- интеграционные и нагрузочные тесты.

## Как проходит результат через систему

```text
Хронометраж
    ↓
CSV / XLSX
    ↓
Анализ и сопоставление колонок
    ↓
Preview и валидация
    ↓
Применение импорта
    ↓
PostgreSQL
    ↓
Черновик результатов
    ↓
Проверка организатором / хронометражем
    ↓
Публикация
    ↓
Публичный протокол
    ↓
Участник
```

При просмотре опубликованных результатов путь запроса выглядит примерно так:

```text
Browser
  ↓
React frontend
  ↓
HTTP API
  ↓
Controller
  ↓
Service
  ↓
Repository
  ↓
PostgreSQL
  ↓
Response
  ↓
Frontend
```

## Технологии

### Backend

- Java 21
- Spring Boot 4.1
- Spring Web
- Spring Data JPA
- Spring Security
- Bean Validation
- PostgreSQL
- Flyway
- Apache Commons CSV
- Apache POI
- OpenAPI / Swagger
- AWS SDK / S3-compatible object storage
- Maven

### Frontend

- React 19
- TypeScript
- Vite
- pnpm
- Oxlint

### Инфраструктура и тестирование

- Cloudflare Pages — frontend
- Render — backend API
- Docker Compose — локальный PostgreSQL
- embedded PostgreSQL — backend integration tests
- k6 — нагрузочное тестирование

## Основная модель данных

Упрощённо предметная модель строится вокруг цепочки:

```text
EventSeries
   ↓
Event
   ↓
Race
   ↓
Registration
   ↓
Result
```

- **EventSeries** — серия мероприятий.
- **Event** — конкретное мероприятие.
- **Race** — отдельный старт / дистанция / формат внутри мероприятия.
- **Registration** — снимок данных участника или команды для конкретного старта.
- **Result** — спортивный результат, включая финиш и промежуточные отсечки.

Публичные протоколы всегда относятся к конкретному `Race`; система не пересчитывает официальные места из-за пользовательской сортировки или фильтрации.

## Структура репозитория

```text
sports-results/
├── backend/       # Java / Spring Boot API
├── frontend/      # React / TypeScript интерфейс
├── load-tests/    # seed-данные и k6-сценарии
├── samples/       # тестовые файлы результатов
├── compose.yaml   # локальный PostgreSQL
└── AGENTS.md      # правила работы с кодовой базой
```

Подробная техническая документация:

- [Backend README](backend/README.md)
- [Frontend README](frontend/README.md)

## Локальный запуск

### 1. PostgreSQL

Из корня репозитория:

```bash
docker compose up -d postgres
```

Локальная база из `compose.yaml`:

```text
Database: sports_results
User:     sports_results
Password: sports_results
Port:     5432
```

Эти значения предназначены только для локальной разработки.

### 2. Backend

Требуется Java 21+.

Windows PowerShell:

```powershell
cd backend
$env:ADMIN_USERNAME = "admin"
$env:ADMIN_PASSWORD = "local-secret"
.\mvnw.cmd spring-boot:run
```

Backend будет доступен на:

```text
http://localhost:8080
```

Swagger UI:

```text
http://localhost:8080/swagger-ui.html
```

### 3. Frontend

Требуются Node.js и pnpm.

```bash
cd frontend
pnpm install
pnpm dev
```

Frontend разработки:

```text
http://127.0.0.1:5173
```

В dev-режиме Vite проксирует `/api` на backend по адресу `http://localhost:8080`.

## Основные переменные окружения

Backend использует переменные окружения для конфигурации. В репозиторий не должны попадать реальные production-секреты.

Примеры:

```text
ADMIN_USERNAME
ADMIN_PASSWORD

DB_URL
DB_USERNAME
DB_PASSWORD

CORS_ALLOWED_ORIGINS
PUBLIC_BASE_URL

DOCUMENT_STORAGE_ROOT
DOCUMENT_MAX_SIZE_BYTES

RESULT_ISSUE_ATTACHMENT_STORAGE_PROVIDER
RESULT_ISSUE_ATTACHMENT_S3_ENDPOINT
RESULT_ISSUE_ATTACHMENT_S3_REGION
RESULT_ISSUE_ATTACHMENT_S3_BUCKET
RESULT_ISSUE_ATTACHMENT_S3_ACCESS_KEY
RESULT_ISSUE_ATTACHMENT_S3_SECRET_KEY
```

Для frontend production-сборки используется, в частности:

```text
VITE_API_BASE_URL
```

Подробнее о конфигурации см. в [backend/README.md](backend/README.md) и [frontend/README.md](frontend/README.md).

## Проверка проекта

Backend:

```bash
cd backend
./mvnw test
./mvnw verify
```

Windows:

```powershell
cd backend
.\mvnw.cmd test
.\mvnw.cmd verify
```

Frontend:

```bash
cd frontend
pnpm test
pnpm lint
pnpm build
```

## Важные принципы

- информация о мероприятии и результаты публикуются независимо;
- официальные места не меняются из-за сортировки, поиска, фильтров или пагинации;
- `AwardPolicy.rankingBasis` определяет официальный способ ранжирования;
- `Race` — единственная область публичного протокола;
- импорт сначала разбирается и валидируется, затем применяется;
- реальные пароли, ключи и другие production-секреты не должны храниться в Git.

## Репозиторий

GitHub: **https://github.com/Evgeniy-A/sports-results**
