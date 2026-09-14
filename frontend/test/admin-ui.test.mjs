import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import { localDateTimeToIso, toDateTimeLocal } from '../src/admin/time.ts'
import { filterTimeZones, suggestTimeZone, timeZoneLabel } from '../src/admin/timeZones.ts'
import { awardPolicyUpdate, withRankingBasis } from '../src/admin/awardPolicy.ts'
import { ApiError, readJsonBody } from '../src/api/client.ts'

const source = async (path) => readFile(new URL(path, import.meta.url), 'utf8')
const [app, adminApp, api, utils, events, eventPage, core, content, categories, results, imports, policy, issues, journal, timeZoneCombobox, css] = await Promise.all([
  source('../src/App.tsx'),
  source('../src/admin/AdminApp.tsx'),
  source('../src/admin/api.ts'),
  source('../src/admin/utils.ts'),
  source('../src/admin/pages/AdminEventsPage.tsx'),
  source('../src/admin/pages/AdminEventPage.tsx'),
  source('../src/admin/pages/EventCoreTabs.tsx'),
  source('../src/admin/pages/EventContentManager.tsx'),
  source('../src/admin/pages/EventCategoriesTab.tsx'),
  source('../src/admin/pages/EventResultsAdminTab.tsx'),
  source('../src/admin/pages/EventImportTab.tsx'),
  source('../src/admin/pages/EventPolicyTabs.tsx'),
  source('../src/admin/pages/EventIssuesTab.tsx'),
  source('../src/admin/pages/AdminJournalPage.tsx'),
  source('../src/admin/components/TimeZoneCombobox.tsx'),
  source('../src/admin/admin.css'),
])

test('admin route is isolated from the public application', () => {
  assert.match(app, /pathname === '\/admin'/)
  assert.match(app, /<AdminApp/)
  assert.match(adminApp, /\/admin\/events/)
  assert.match(adminApp, /\/admin\/support\/issues/)
})

test('Basic Auth credentials remain only in React runtime memory', () => {
  assert.match(adminApp, /useState<AdminCredentials \| null>/)
  assert.match(api, /Authorization.*basicAuthorization/s)
  assert.doesNotMatch(`${adminApp}\n${api}`, /localStorage|sessionStorage|JWT/i)
})

test('event list, create and edit use protected typed APIs', () => {
  assert.match(api, /events:.*\/admin\/events/s)
  assert.match(api, /createEvent:/)
  assert.match(api, /updateEvent:/)
  assert.match(events, /Создать мероприятие/)
  assert.match(core, /Настройка влияет на итоговый протокол/)
})

test('event content CRUD uses protected APIs and FileStorage-backed document downloads', () => {
  for (const method of ['infoBlocks', 'createInfoBlock', 'updateInfoBlock', 'deleteInfoBlock', 'schedule', 'createScheduleItem', 'updateScheduleItem', 'deleteScheduleItem', 'documents', 'uploadDocument', 'updateDocument', 'deleteDocument', 'documentContent']) {
    assert.match(api, new RegExp(`${method}:`))
  }
  assert.match(content, /Контент мероприятия/)
  assert.match(content, /application\/pdf/)
  assert.match(content, /ConfirmDialog/)
})

test('Event date inputs round-trip through the configured IANA time zone', () => {
  const instant = localDateTimeToIso('2028-04-12T09:30', 'Asia/Yekaterinburg')
  assert.equal(instant, '2028-04-12T04:30:00.000Z')
  assert.equal(toDateTimeLocal(instant, 'Asia/Yekaterinburg'), '2028-04-12T09:30')
})

test('Event timezone uses a fixed-value searchable combobox instead of an IANA text field', () => {
  assert.match(events, /<TimeZoneCombobox value={timeZone} location={location}/)
  assert.match(core, /<TimeZoneCombobox value={form\.timeZone} location={form\.location}/)
  assert.doesNotMatch(events, /IANA, например/)
  assert.doesNotMatch(core, /<input required value={form\.timeZone}/)
  assert.match(timeZoneCombobox, /role="combobox"/)
  assert.match(timeZoneCombobox, /role="listbox"/)
  assert.match(timeZoneCombobox, /type="search"/)
})

test('timezone labels are human-readable while submitted values remain IANA identifiers', () => {
  assert.equal(timeZoneLabel('Europe/Moscow'), 'Москва — UTC+3')
  assert.equal(timeZoneLabel('Asia/Yekaterinburg'), 'Екатеринбург — UTC+5')
  assert.equal(timeZoneLabel('Asia/Kamchatka'), 'Камчатка — UTC+12')
  assert.equal(filterTimeZones('Омск')[0]?.value, 'Asia/Omsk')
})

test('an unambiguous Event location suggests a timezone without locking the selection', () => {
  assert.equal(suggestTimeZone('Челябинск, городской бор'), 'Asia/Yekaterinburg')
  assert.equal(suggestTimeZone('Владивосток'), 'Asia/Vladivostok')
  assert.equal(suggestTimeZone('Неизвестная площадка'), null)
  assert.match(timeZoneCombobox, /suggestedValue !== value/)
  assert.match(timeZoneCombobox, /select\(suggestedValue\)/)
})

test('event page exposes the complete operator tab model', () => {
  for (const label of ['Основное', 'Форматы и старты', 'Категории и кластеры', 'Участники', 'Результаты', 'Импорт', 'Обращения', 'Зачёт и награждение', 'Публикация']) {
    assert.match(eventPage, new RegExp(label))
  }
})

test('SportFormat and Race stay data-driven with explicit Draft and Publish', () => {
  assert.match(core, /createSportFormat/)
  assert.match(core, /createRace/)
  assert.match(core, /Вернуть в черновик/)
  assert.match(core, /Опубликовать/)
  assert.doesNotMatch(core, /auto-?draft/i)
})

test('category and StartCluster CRUD preserve their distinct meanings', () => {
  assert.match(categories, /Добавить категорию/)
  assert.match(categories, /CATEGORY_IN_USE|Категория используется|историческими данными/)
  assert.match(categories, /StartCluster/)
  assert.match(categories, /кластер не рассчитывается/)
  assert.match(categories, /младше 18 лет/)
})

test('participants are an Event-wide admin view without a fabricated Event place', () => {
  assert.match(results, /Текущие Registration по всем форматам и Race/)
  assert.match(results, /Общего места по Event не вычисляется/)
  assert.match(results, /SportFormat/)
  assert.match(results, /StartCluster/)
  assert.match(results, /sportFormatId: filters\.sportFormatId/)
})

test('registration and result correction round-trip through backend', () => {
  assert.match(results, /updateRegistration/)
  assert.match(results, /updateResult/)
  assert.match(results, /Frontend не пересчитывает ranking/)
  assert.match(results, /Race опубликован.*сразу изменит текущие публичные данные/s)
})

test('import exposes three explicit modes and never auto-syncs them', () => {
  assert.match(imports, /ADD_NEW/)
  assert.match(imports, /UPDATE_EXISTING/)
  assert.match(imports, /EMERGENCY_REPLACE/)
  assert.match(imports, /Добавить новые/)
  assert.match(imports, /Обновить существующие/)
  assert.match(imports, /Экстренно заменить данные/)
  assert.doesNotMatch(imports, /Синхронизировать всё/)
})

test('import Preview is separate from Apply and emergency replace needs typed confirmation', () => {
  assert.match(imports, /Построить Preview/)
  assert.match(imports, /blockingErrorsPresent/)
  assert.match(imports, /api\.importApply/)
  assert.match(imports, /confirmation === 'ЗАМЕНИТЬ'/)
  assert.match(utils, /IMPORT_PREVIEW_STALE.*Выполните Preview заново/s)
})

test('recalculation pending, Preview and Apply remain explicit', () => {
  assert.match(eventPage, /resultRecalculationRequired/)
  assert.match(eventPage, /Необходимо пересчитать категории/)
  assert.match(policy, /recalculationPreview/)
  assert.match(policy, /recalculationApply/)
  assert.match(policy, /Применить пересчёт/)
})

test('AwardPolicy stays backend-owned and published Race is never auto-drafted', () => {
  assert.match(policy, /AwardPolicy — единственный источник/)
  assert.match(policy, /GUN_TIME/)
  assert.match(policy, /CHIP_TIME/)
  assert.match(policy, /NONE/)
  assert.match(policy, /Admin UI не делает auto-Draft/)
})

test('admin result sorting sends only backend-supported contract values', () => {
  assert.match(results, /mode === 'results' \? 'place' : 'displayName'/)
  assert.match(results, /<option value="displayName">Имя<\/option>/)
  assert.doesNotMatch(results, /<option value="name">Имя<\/option>/)
  assert.doesNotMatch(results, /<option value="status">Статус<\/option>/)
})

test('AwardPolicy update supports GUN_TIME, CHIP_TIME and a valid normalized NONE body', () => {
  const basePolicy = {
    id: 7,
    raceId: 3,
    rankingBasis: 'GUN_TIME',
    primaryStandingMode: 'BY_GENDER',
    absolutePrizePlaces: 3,
    categoryEnabled: true,
    ageCalculationMode: 'END_OF_EVENT_YEAR',
    categoryPrizePlaces: 5,
    excludeAbsoluteWinnersFromCategory: true,
  }
  assert.deepEqual(withRankingBasis(basePolicy, 'CHIP_TIME'), { ...basePolicy, rankingBasis: 'CHIP_TIME' })
  assert.deepEqual(withRankingBasis(basePolicy, 'GUN_TIME'), basePolicy)
  const none = withRankingBasis(basePolicy, 'NONE')
  assert.deepEqual(awardPolicyUpdate(none), {
    rankingBasis: 'NONE', primaryStandingMode: 'NONE', absolutePrizePlaces: 0,
    categoryEnabled: false, ageCalculationMode: 'END_OF_EVENT_YEAR',
    categoryPrizePlaces: 0, excludeAbsoluteWinnersFromCategory: false,
  })
  assert.doesNotMatch(JSON.stringify(awardPolicyUpdate(basePolicy)), /"id"|"raceId"/)
  assert.match(policy, /awardPolicyUpdate\(policy\)/)
})

test('shared JSON reader handles 204 and declared empty bodies without hiding malformed JSON', async () => {
  assert.equal(await readJsonBody(new Response(null, { status: 204 })), undefined)
  assert.equal(await readJsonBody(new Response(null, { status: 200 }), true), undefined)
  await assert.rejects(
    () => readJsonBody(new Response(null, { status: 200 })),
    (reason) => reason instanceof ApiError && reason.code === 'EMPTY_RESPONSE',
  )
  await assert.rejects(
    () => readJsonBody(new Response('{broken', { status: 200 })),
    SyntaxError,
  )
  assert.match(api, /readJsonBody<T>\(response, allowEmpty\)/)
  assert.doesNotMatch(api, /return response\.json\(\) as Promise<T>/)
})

test('Event issue queue separates snapshot, current state, archive and status', () => {
  assert.match(issues, /Рабочая очередь обращений/)
  assert.match(issues, /Состояние на момент обращения/)
  assert.match(issues, /Текущее состояние/)
  assert.match(issues, /archiveIssue/)
  assert.match(issues, /!detail\.archive\.queueArchivedAt/)
  assert.match(issues, /updateIssueStatus/)
  assert.match(issues, /authorizeAttachment/)
})

test('global journal keeps filters in URL and uses server-side pagination', () => {
  assert.match(journal, /filtersFromUrl/)
  assert.match(journal, /writeFiltersToUrl/)
  assert.match(journal, /AdminPagination/)
  assert.match(journal, /Historical snapshot/)
  assert.match(journal, /Current context/)
})

test('XLSX is downloaded as binary and share batch can be revoked', () => {
  assert.match(api, /response\.blob\(\)/)
  assert.match(api, /Content-Disposition/)
  assert.match(api, /X-Share-Batch-Id/)
  assert.match(journal, /URL\.createObjectURL/)
  assert.match(journal, /revokeShareBatch/)
  assert.match(journal, /Все ссылки на вложения.*перестанут работать/s)
})

test('central error mapping covers guards and stale operations', () => {
  for (const code of ['RACE_RESULTS_MUST_BE_DRAFT', 'RESULT_RECALCULATION_REQUIRED', 'RECALC_PREVIEW_STALE', 'RETIRED_REGISTRATION_NOT_MUTABLE', 'CATEGORY_IN_USE', 'DUPLICATE_BIB', 'AMBIGUOUS', 'CONFLICT']) {
    assert.match(utils, new RegExp(code))
  }
})

test('admin UI has loading, confirmations, table overflow and tablet behavior', () => {
  assert.match(css, /admin-spinner/)
  assert.match(css, /overflow-x: auto/)
  assert.match(css, /@media \(max-width: 780px\)/)
  assert.match(css, /admin-modal/)
})
