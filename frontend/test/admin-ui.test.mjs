import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import { localDateTimeToIso, toDateTimeLocal } from '../src/admin/time.ts'
import { filterTimeZones, suggestTimeZone, timeZoneLabel } from '../src/admin/timeZones.ts'
import { awardPolicyChanged, awardPolicyUpdate, withRankingBasis } from '../src/admin/awardPolicy.ts'
import { ApiError, readJsonBody } from '../src/api/client.ts'
import { duplicateLocationIds, normalizedLocation } from '../src/admin/bulkEvents.ts'
import { authorizeAndOpenAttachment } from '../src/admin/attachmentOpening.ts'
import { confirmAdminNavigation, setAdminNavigationBlocker } from '../src/admin/navigationGuard.ts'
import {
  ADMIN_AUTH_SESSION_KEY,
  clearAdminCredentials,
  loadAdminCredentials,
  notifyUnauthorized,
  saveAdminCredentials,
} from '../src/admin/adminAuthSession.ts'

const source = async (path) => readFile(new URL(path, import.meta.url), 'utf8')
const [app, adminApp, api, adminTypes, utils, router, events, createEvent, creationLayout, templates, templateDetail, startComposer, categorySettings, awardPresentation, startDrafts, bulkEvents, inlineTemplate, eventPage, core, content, categories, results, resultEditor, imports, publication, policy, issues, issuePage, journal, timeZoneCombobox, css] = await Promise.all([
  source('../src/App.tsx'),
  source('../src/admin/AdminApp.tsx'),
  source('../src/admin/api.ts'),
  source('../src/admin/types.ts'),
  source('../src/admin/utils.ts'),
  source('../src/admin/router.tsx'),
  source('../src/admin/pages/AdminEventsPage.tsx'),
  source('../src/admin/pages/AdminCreateEventPage.tsx'),
  source('../src/admin/components/AdminCreationLayout.tsx'),
  source('../src/admin/pages/AdminTemplatesPage.tsx'),
  source('../src/admin/pages/AdminTemplatePage.tsx'),
  source('../src/admin/components/EventStartComposer.tsx'),
  source('../src/admin/components/CategorySettings.tsx'),
  source('../src/admin/awardPolicyPresentation.ts'),
  source('../src/admin/eventStartDrafts.ts'),
  source('../src/admin/pages/AdminBulkEventsPage.tsx'),
  source('../src/admin/components/InlineTemplateCreator.tsx'),
  source('../src/admin/pages/AdminEventPage.tsx'),
  source('../src/admin/pages/EventCoreTabs.tsx'),
  source('../src/admin/pages/EventContentManager.tsx'),
  source('../src/admin/pages/EventCategoriesTab.tsx'),
  source('../src/admin/pages/EventResultsAdminTab.tsx'),
  source('../src/admin/components/AdminResultEditor.tsx'),
  source('../src/admin/pages/EventImportTab.tsx'),
  source('../src/admin/pages/EventResultsPublicationTab.tsx'),
  source('../src/admin/pages/EventPolicyTabs.tsx'),
  source('../src/admin/pages/EventIssuesTab.tsx'),
  source('../src/admin/pages/AdminEventIssuePage.tsx'),
  source('../src/admin/pages/AdminJournalPage.tsx'),
  source('../src/admin/components/TimeZoneCombobox.tsx'),
  source('../src/admin/admin.css'),
])
const eventGeneralTab = core.split('export function StartsTab')[0]

test('admin route is isolated from the public application', () => {
  assert.match(app, /pathname === '\/admin'/)
  assert.match(app, /<AdminApp/)
  assert.match(adminApp, /\/admin\/events/)
  assert.match(adminApp, /\/admin\/support\/issues/)
  assert.match(adminApp, /\/admin\/templates/)
  assert.match(adminApp, /\/admin\/events\/bulk/)
})

test('Basic Auth credentials persist only for the current tab session and logout clears them', () => {
  const values = new Map()
  const storage = {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
    removeItem: (key) => values.delete(key),
  }
  assert.equal(loadAdminCredentials(storage), null)
  saveAdminCredentials({ username: 'admin', password: 'secret' }, storage)
  assert.deepEqual(loadAdminCredentials(storage), { username: 'admin', password: 'secret' })
  assert.ok(values.has(ADMIN_AUTH_SESSION_KEY))
  clearAdminCredentials(storage)
  assert.equal(loadAdminCredentials(storage), null)
  assert.match(adminApp, /useState<AdminCredentials \| null>\(\(\) => loadAdminCredentials\(\)\)/)
  assert.match(adminApp, /clearAdminCredentials\(\)/)
  assert.match(api, /Authorization.*basicAuthorization/s)
  assert.doesNotMatch(`${adminApp}\n${api}`, /localStorage|JWT/i)
  assert.doesNotMatch(api, /queryString\([^)]*password|params\.set\(['"]password/i)
})

test('a real backend 401 clears the admin session through the common API client', () => {
  let unauthorized = 0
  notifyUnauthorized(403, () => { unauthorized += 1 })
  assert.equal(unauthorized, 0)
  notifyUnauthorized(401, () => { unauthorized += 1 })
  assert.equal(unauthorized, 1)
  assert.equal((api.match(/notifyUnauthorized\(response\.status, onUnauthorized\)/g) ?? []).length, 4)
})

test('event list, create and edit use protected typed APIs', () => {
  assert.match(api, /events:.*\/admin\/events/s)
  assert.match(api, /createEvent:/)
  assert.match(api, /updateEvent:/)
  for (const action of ['Создать мероприятие', 'Создать несколько']) assert.match(events, new RegExp(action))
  assert.doesNotMatch(events, /href="\/admin\/templates"|>Шаблоны<\/AdminLink>/)
  assert.match(core, /Настройка влияет на итоговый протокол/)
})

test('Events workspace groups creation routes and leaves Templates in the canonical sidebar only', () => {
  assert.match(events, /href="\/admin\/events\/new">\+ Создать мероприятие/)
  assert.match(events, /href="\/admin\/events\/bulk">Создать несколько/)
  assert.doesNotMatch(events, /href="\/admin\/templates"/)
  assert.match(adminApp, /href="\/admin\/templates"/)
  assert.match(adminApp, />Шаблоны<\/AdminLink>/)
  assert.match(events, /admin-events-create-actions/)
  assert.match(events, /admin-events-filter/)
})

test('single Event creation has a direct full-page route and does not use a Drawer', () => {
  assert.match(adminApp, /location\.pathname === '\/admin\/events\/new'/)
  assert.match(adminApp, /<AdminCreateEventPage api=\{api\}/)
  assert.match(createEvent, /export function AdminCreateEventPage/)
  assert.match(createEvent, /<AdminCreationHeader/)
  assert.match(createEvent, /<AdminCreationSection number="01"/)
  assert.match(createEvent, /<AdminCreationSection number="04"/)
  assert.doesNotMatch(createEvent, /<Drawer|CreateEventDrawer/)
  assert.match(creationLayout, /<AdminLink href="\/admin\/events">Мероприятия<\/AdminLink>/)
})

test('single and bulk creation share guided sections and preserve their destinations', () => {
  assert.match(createEvent, /navigateAdmin\(`\/admin\/events\/\$\{created\.event\.id\}`\)/)
  assert.match(bulkEvents, /navigateAdmin\('\/admin\/events'\)/)
  for (const sourceText of [createEvent, bulkEvents]) {
    assert.match(sourceText, /<AdminCreationHeader/)
    assert.match(sourceText, /<AdminCreationSection number="01"/)
    assert.match(sourceText, /<AdminCreationSection number="04"/)
  }
  assert.match(createEvent, /resultInquiry: inquiry/)
  assert.match(createEvent, /starts: startCommands\(starts\)/)
  assert.match(bulkEvents, /api\.previewBulkEvents\(command\(\)\)/)
  assert.match(bulkEvents, /api\.createBulkEvents\(command\(\)\)/)
})

test('single creation protects meaningful drafts on router navigation and reload', () => {
  assert.match(createEvent, /setAdminNavigationBlocker\(\(\) => allowNavigation\.current \|\| window\.confirm\(LEAVE_EVENT_CREATION_MESSAGE\)\)/)
  assert.match(createEvent, /window\.addEventListener\('beforeunload', warn\)/)
  assert.match(createEvent, /window\.removeEventListener\('beforeunload', warn\)/)
  assert.match(createEvent, /allowNavigation\.current = true[\s\S]*navigateAdmin/)
})

test('event creation keeps template selection and inline creation in one business flow', () => {
  assert.match(createEvent, /label="Шаблон"/)
  assert.match(inlineTemplate, /\+ Создать новый шаблон/)
  assert.match(inlineTemplate, /label="Название шаблона"/)
  assert.match(inlineTemplate, /api\.createEventSeries/)
  assert.match(createEvent, /setTemplates\(\(current\) => \[\.\.\.current, created\]/)
  assert.match(createEvent, /setTemplateId\(String\(created\.id\)\)/)
  assert.doesNotMatch(`${events}\n${createEvent}\n${core}\n${templates}\n${bulkEvents}\n${inlineTemplate}`, /Серия|серия/)
})

test('ordinary Event and series forms do not ask for a URL or submit a slug', () => {
  assert.doesNotMatch(createEvent, /Адрес в URL|name, slug|setSlug/)
  assert.match(createEvent, /eventSeriesId: Number\(templateId\), name,/)
  assert.doesNotMatch(eventGeneralTab, /Адрес в URL|slug: form\.slug|set\('slug'/)
  assert.match(eventGeneralTab, /label="Шаблон"/)
})

test('event table and empty state use template terminology and useful creation actions', () => {
  assert.match(events, /<th>Шаблон<\/th>/)
  assert.match(events, /Мероприятий пока нет/)
  assert.match(events, /несколько городов сразу из шаблона/)
  assert.doesNotMatch(events, /<th>Серия<\/th>|<th>Event<\/th>/)
})

test('event table fits desktop content and uses one keyboard-accessible row action', () => {
  assert.match(events, /className="admin-event-row"/)
  assert.match(events, /role="link"/)
  assert.match(events, /tabIndex=\{0\}/)
  assert.match(events, /keyEvent\.key !== 'Enter'.*keyEvent\.key !== ' '/s)
  assert.doesNotMatch(events, /admin-row-link[^>]*href=\{`\/admin\/events/)
  assert.match(css, /\.admin-events-table-wrap[^}]*overflow-x:\s*clip/)
  assert.match(css, /\.admin-events-table[^}]*table-layout:\s*fixed/)
})

test('admin navigation is a closed-by-default overlay with complete keyboard dismissal', () => {
  assert.match(adminApp, /useState\(false\)/)
  assert.match(adminApp, /aria-expanded=\{sidebarOpen\}/)
  assert.match(adminApp, /aria-controls="admin-navigation"/)
  assert.match(adminApp, /admin-sidebar-scrim/)
  assert.match(adminApp, /event\.key !== 'Escape'/)
  assert.match(adminApp, /querySelector<HTMLAnchorElement>\('a\[href\]'\)\?\.focus\(\)/)
  assert.match(adminApp, /onClick=\{\(\) => closeSidebar\(\)\}/)
  assert.match(css, /\.admin-content[^}]*padding:[^}]*;/)
  assert.doesNotMatch(css, /\.admin-content[^}]*margin-left:\s*244px/)
  assert.match(css, /\.admin-sidebar-scrim[^}]*position:\s*fixed/)
})

test('templates screen supports list, empty state, create and stable-slug edit API', () => {
  assert.match(templates, /<h1>Шаблоны<\/h1>/)
  assert.match(templates, /Шаблонов пока нет/)
  assert.match(templates, /\+ Новый шаблон/)
  assert.match(templates, /api\.createEventSeries/)
  assert.match(templates, /api\.updateEventSeries/)
  assert.match(templates, /template\.eventCount/)
  assert.match(templates, /template\.startCount/)
  assert.doesNotMatch(templates, /slug|Удалить шаблон/)
})

test('template details provide start CRUD with ordered reusable definitions', () => {
  assert.match(adminApp, /templateMatch/)
  assert.match(templateDetail, /Старты шаблона/)
  assert.match(templateDetail, /\+ Добавить старт/)
  assert.match(templateDetail, /api\.createTemplateStart/)
  assert.match(templateDetail, /api\.updateTemplateStart/)
  assert.match(templateDetail, /api\.deleteTemplateStart/)
  assert.match(templateDetail, /Это не изменит уже созданные мероприятия/)
  for (const endpoint of ['templateStarts:', 'createTemplateStart:', 'updateTemplateStart:', 'deleteTemplateStart:']) {
    assert.match(api, new RegExp(endpoint))
  }
  assert.doesNotMatch(templateDetail, /Код импорта|sourceCode/)
  assert.match(startDrafts, /sourceCode: null/)
})

test('template start ordering uses drag drop and touch arrows without a numeric order field', () => {
  assert.match(templateDetail, /draggable={!busy}/)
  assert.match(templateDetail, /onDrop=\{\(\) => dropBefore/)
  assert.match(templateDetail, /api\.reorderTemplateStarts/)
  assert.match(templateDetail, /Поднять старт/)
  assert.match(templateDetail, /Опустить старт/)
  assert.doesNotMatch(`${templateDetail}\n${startComposer}`, /Field label="(?:Порядок|displayOrder)"/)
  assert.doesNotMatch(`${templateDetail}\n${startComposer}`, /type="number"[^>]*(?:displayOrder|Порядок)/)
})

test('award editor separates absolute and category awards without NONE dropdown options', () => {
  for (const status of ['Награждение: Абсолют', 'Награждение: Категории', 'Награждение: Абсолют + категории', 'Награждение не настроено']) {
    assert.ok(`${templateDetail}\n${startComposer}\n${awardPresentation}`.includes(status))
  }
  assert.match(templateDetail, /updateTemplateAwardPolicy/)
  assert.match(templateDetail, /deleteTemplateAwardPolicy/)
  for (const value of ['GUN_TIME', 'CHIP_TIME', 'BY_GENDER', 'EVENT_DATE']) {
    assert.match(startComposer, new RegExp(value))
  }
  assert.match(startComposer, /Награждение — абсолют/)
  assert.match(startComposer, /checked=\{value\.primaryStandingMode !== 'NONE'\}/)
  assert.match(startComposer, /<option value="ALL">Общее/)
  assert.match(startComposer, /<option value="BY_GENDER">По полу/)
  assert.doesNotMatch(startComposer, /<option value="NONE">/)
  assert.doesNotMatch(startComposer, /Без официального зачёта|Без награждения/)
  assert.match(startComposer, /Награждение по категориям/)
  assert.match(startComposer, /Призовых мест в каждой категории/)
  assert.match(startComposer, /Не награждать в возрастных категориях призёров абсолютного награждения/)
  assert.match(startComposer, /rankingBasis === 'NONE'/)
  assert.ok(startComposer.indexOf('Возраст считать') > startComposer.indexOf('value.categoryEnabled &&'))
})

test('template and Race award flows reuse category editing without navigation', () => {
  assert.match(templateDetail, /<AwardEditor[\s\S]*categorySettings=\{[\s\S]*<CategorySettings[\s\S]*defaultRankingBasis=/)
  assert.match(policy, /<AwardEditor[\s\S]*categorySettings=\{[\s\S]*<CategorySettings[\s\S]*defaultRankingBasis=/)
  for (const action of ['createTemplateCategory', 'updateTemplateCategory', 'deleteTemplateCategory', 'templateCategories']) {
    assert.match(`${api}\n${templateDetail}`, new RegExp(action))
  }
  assert.match(categorySettings, /Возрастные категории/)
  assert.match(categorySettings, /\+ Добавить категорию/)
  assert.match(categorySettings, />Изменить</)
  assert.match(categorySettings, />Удалить</)
  assert.match(categorySettings, />Отключить</)
  assert.match(categorySettings, /category\.inUse/)
  assert.match(categorySettings, /старая категория и её исторические связи сохранятся/i)
  assert.match(categorySettings, /Название в файле/)
  assert.match(categorySettings, /minAge/)
  assert.match(categorySettings, /maxAge/)
  assert.match(categorySettings, /gender/)
  assert.match(categorySettings, /displayOrder/)
  assert.match(categorySettings, /enabled/)
})

test('single Event creation copies selected starts and keeps the preview editable on back', () => {
  assert.match(createEvent, /api\.templateStarts/)
  assert.match(createEvent, /draftsFromTemplate/)
  assert.match(startDrafts, /included: true/)
  assert.match(startComposer, /checked=\{draft\.included\}/)
  assert.match(createEvent, /<EventStartComposer/)
  assert.match(createEvent, /Продолжить/)
  assert.match(createEvent, /title="Проверка"/)
  assert.match(createEvent, /setPreview\(false\)/)
  assert.match(createEvent, /createEventWithStarts/)
  assert.match(api, /\/admin\/events\/with-starts/)
})

test('event composer allows event-only edits, extra starts and visual reordering', () => {
  assert.match(startComposer, /origin: 'extra'/)
  assert.match(startComposer, /\+ Добавить старт/)
  assert.match(startComposer, /Удалить дополнительный старт/)
  assert.match(startComposer, /Изменения применятся только к создаваемому мероприятию/)
  assert.match(startComposer, /onDrop=\{\(\) => dropBefore/)
  assert.doesNotMatch(`${startComposer}\n${createEvent}`, /Код импорта|· Код:/)
  assert.match(startComposer, /Дистанция, м/)
})

test('bulk creation uses one common start set and previews total materialization', () => {
  assert.match(bulkEvents, /api\.templateStarts/)
  assert.match(bulkEvents, /<EventStartComposer/)
  assert.match(bulkEvents, /starts: startCommands\(starts\)/)
  assert.match(bulkEvents, /preview\.totalStartCount/)
  assert.match(bulkEvents, /preview\.starts\.map/)
  assert.doesNotMatch(bulkEvents, /per-city|для каждого города/i)
})

test('template UX exposes user terminology rather than persistence entity names', () => {
  const visibleCopy = `${templates}\n${templateDetail}\n${events}\n${createEvent}\n${bulkEvents}`
  for (const term of ['EventSeries', 'TemplateStart', 'AwardPolicy', 'Race']) {
    assert.doesNotMatch(visibleCopy, new RegExp(`>${term}<`))
  }
})

test('bulk screen manages cities, explicit timezones and two-step Preview without losing draft state', () => {
  assert.match(bulkEvents, /title="Создать несколько мероприятий"/)
  assert.match(bulkEvents, /\+ Добавить город/)
  assert.match(bulkEvents, /LOCATION_SUGGESTIONS/)
  assert.match(bulkEvents, /suggestTimeZone\(location\) \?\? ''/)
  assert.match(bulkEvents, /rows\.every\(\(row\) => row\.location\.trim\(\) && row\.timeZone\)/)
  assert.match(bulkEvents, /api\.previewBulkEvents/)
  assert.match(bulkEvents, /api\.createBulkEvents/)
  assert.match(bulkEvents, /onBack=\{\(\) => setPreview\(null\)\}/)
  assert.match(bulkEvents, /title="Проверка"/)
  assert.match(bulkEvents, /Часовой пояс:/)
  assert.doesNotMatch(bulkEvents, /Показать Preview|>Preview<|Timezone:|проверьте Preview/)
})

test('existing Event starts support guarded deletion without affecting templates', () => {
  assert.match(api, /deleteRace:/)
  assert.match(api, /\/admin\/events\/\$\{eventId\}\/races\/\$\{raceId\}/)
  assert.match(core, /Удалить старт/)
  assert.match(core, /`Удалить старт «\$\{confirmedRace\?\.name/)
  assert.match(core, /Старт будет удалён только из этого мероприятия\. Шаблон и другие мероприятия не изменятся\./)
  assert.match(core, /resultsPublicationStatus === 'PUBLISHED'/)
  for (const code of ['RACE_DELETE_PUBLISHED', 'RACE_DELETE_HAS_RESULTS', 'RACE_DELETE_HAS_REGISTRATIONS', 'RACE_DELETE_HAS_DEPENDENCIES']) {
    assert.match(utils, new RegExp(code))
  }
})

test('bulk duplicate detection normalizes case, whitespace and yo', () => {
  assert.equal(normalizedLocation('  Орёл   Центр '), 'орел центр')
  const duplicates = duplicateLocationIds([
    { id: 1, location: 'Москва', timeZone: 'Europe/Moscow' },
    { id: 2, location: '  москва ', timeZone: 'Europe/Moscow' },
    { id: 3, location: 'Казань', timeZone: 'Europe/Moscow' },
  ])
  assert.deepEqual([...duplicates].sort(), [1, 2])
  assert.match(bulkEvents, /disabled=\{busy \|\| !ready\}/)
})

test('admin heading actions wrap and become full-width without mobile overflow', () => {
  assert.match(css, /\.admin-heading-actions[^}]*flex-wrap:\s*wrap/)
  assert.match(css, /@media \(max-width: 520px\).*\.admin-heading-actions/s)
  assert.match(css, /\.admin-heading-actions > \*[^}]*width:\s*100%/)
})

test('event content CRUD uses protected APIs and FileStorage-backed document downloads', () => {
  for (const method of ['infoBlocks', 'createInfoBlock', 'updateInfoBlock', 'deleteInfoBlock', 'schedule', 'createScheduleItem', 'updateScheduleItem', 'deleteScheduleItem', 'documents', 'uploadDocument', 'updateDocument', 'deleteDocument', 'documentContent']) {
    assert.match(api, new RegExp(`${method}:`))
  }
  assert.match(content, /Контент мероприятия/)
  assert.match(content, /application\/pdf/)
  assert.match(content, /ConfirmDialog/)
})

test('fresh participant information uses a nullable-id JSON DTO and remains editable through PUT', () => {
  assert.match(adminTypes, /interface ParticipantInfo[\s\S]*id: number \| null/)
  assert.match(api, /participantInfo:.*\/participant-info/s)
  assert.match(api, /updateParticipantInfo:.*method: 'PUT'/s)
  assert.match(core, /api\.participantInfo\(event\.id\)[\s\S]*setInfo\(infoValue\)/)
  assert.match(core, /\{info && <section className="admin-card"/)
  assert.match(core, /setInfo\(\(current\) => current \? \{ \.\.\.current, \.\.\.patch \} : current\)/)
  assert.match(core, /updateInfo\(\{ shortDescription:/)
  assert.match(core, /updateInfo\(\{ venueName:/)
  assert.match(core, /api\.updateParticipantInfo\(event\.id/)
  assert.match(core, /shortDescription: info\.shortDescription[\s\S]*additionalInfo: info\.additionalInfo/)
  assert.match(core, /setInfo\(await api\.updateParticipantInfo/)
})

test('Event date inputs round-trip through the configured IANA time zone', () => {
  const instant = localDateTimeToIso('2028-04-12T09:30', 'Asia/Yekaterinburg')
  assert.equal(instant, '2028-04-12T04:30:00.000Z')
  assert.equal(toDateTimeLocal(instant, 'Asia/Yekaterinburg'), '2028-04-12T09:30')
})

test('Event timezone uses a fixed-value searchable combobox instead of an IANA text field', () => {
  assert.match(createEvent, /<TimeZoneCombobox value={timeZone} location={location}/)
  assert.match(core, /<TimeZoneCombobox value={form\.timeZone} location={form\.location}/)
  assert.doesNotMatch(createEvent, /IANA, например/)
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

test('event page separates Event configuration from result operations', () => {
  for (const label of ['Мероприятие', 'Результаты', 'Основное', 'Старты', 'Награждение и категории', 'Информация участникам', 'Загрузка', 'Участники', 'Публикация результатов', 'Обращения']) {
    assert.match(eventPage, new RegExp(label))
  }
  assert.match(eventPage, /EVENT_TABS/)
  assert.match(eventPage, /RESULT_TABS/)
  assert.doesNotMatch(eventPage, /Категории и стартовые волны|Доступ к результатам/)
  assert.match(eventPage, /tab === 'publication'/)
  assert.match(core, /Публикация мероприятия/)
  assert.match(core, /Результаты публикуются отдельно для каждого старта/)
  assert.match(eventPage, /publishedRaceCount/)
})

test('Start UI uses the final Race-only contract while preserving explicit publication', () => {
  assert.match(core, /createRace/)
  assert.match(core, /Добавить старт/)
  assert.match(core, /Создать старт/)
  assert.doesNotMatch(core, /Добавить формат|Добавить Race|Спортивный формат|Тип участия|entryMode/)
  assert.doesNotMatch(core, /Адрес в URL|slug: form\.slug|sportFormatId:/)
  assert.match(core, /Вернуть в черновик/)
  assert.match(core, /Опубликовать/)
  assert.match(core, /admin-button-primary admin-button-compact[\s\S]*?>Открыть/)
  assert.match(core, /admin-button-secondary admin-button-compact[\s\S]*?>Редактировать/)
  assert.match(core, /admin-button-subtle admin-button-compact/)
  assert.match(core, /admin-button-danger-outline admin-button-compact[\s\S]*?>Удалить/)
  assert.doesNotMatch(core, /Код импорта|sourceCode/)
  assert.doesNotMatch(core, /auto-?draft/i)
  assert.doesNotMatch(api, /sportFormats:|createSportFormat:|sportFormatId|entryMode/)
})

test('result publication tab reuses Race publication state and APIs', () => {
  assert.match(publication, /api\.results\(event\.id/)
  assert.match(publication, /raceId: race\.id, page: 0, size: 1/)
  assert.match(publication, /totalElements/)
  assert.match(publication, /api\.publishRace/)
  assert.match(publication, /api\.draftRace/)
  assert.match(publication, /Публикация самого мероприятия не изменяется/)
  assert.match(publication, /Статус мероприятия не изменится/)
  assert.doesNotMatch(publication, /updateEventPublication|new publication|resultsPublished\s*=/i)
})

test('event publication form keeps the legacy results field compatibility-only', () => {
  assert.match(eventGeneralTab, /api\.updateEventPublication\(event\.id/)
  assert.match(eventGeneralTab, /resultsPublicationStatus: event\.resultsPublicationStatus/)
  assert.match(eventGeneralTab, /Статусы результатов стартов не изменены/)
  assert.match(eventGeneralTab, /const publishedRace = races\.some\(\(race\) => race\.resultsPublicationStatus === 'PUBLISHED'\)/)
  assert.doesNotMatch(eventGeneralTab, /api\.(?:publishRace|draftRace)\(/)
})

test('categories remain in award workflow while start waves are not ordinary Event settings', () => {
  assert.match(policy, /CategorySettings/)
  assert.match(categorySettings, /Добавить категорию/)
  assert.doesNotMatch(eventPage, /EventCategoriesTab|CategoriesClustersTab|Категории и стартовые волны/)
  assert.match(categories, /стартовая волна не рассчитывается/)
})

test('participants are an Event-wide admin view without a fabricated Event place', () => {
  assert.match(results, /Текущие участники по всем стартам/)
  assert.match(results, /Общее место по мероприятию не вычисляется/)
  assert.match(results, /Field label="Старт"/)
  assert.match(results, /race\.name/)
  assert.doesNotMatch(results, /sportFormatId: filters\.sportFormatId|Field label="SportFormat"/)
})

test('registration and result correction round-trip through backend', () => {
  assert.match(results, /AdminResultEditor/)
  assert.match(resultEditor, /updateRegistration/)
  assert.match(resultEditor, /updateResult/)
  assert.match(resultEditor, /Официальные места система рассчитывает/)
  assert.match(resultEditor, /Старт опубликован.*сразу изменит текущие публичные данные/s)
})

test('Admin Results requests per-Race official ranking and shows the same achievements in list and detail', () => {
  assert.doesNotMatch(results, /rankingBasis: 'CHIP_TIME'/)
  assert.match(results, /<td>\{row\.place \?\? '—'\}<\/td>/)
  assert.match(resultEditor, /<h3>Официальный зачёт<\/h3>/)
  assert.match(resultEditor, /detail\.rankingAchievements\.map/)
  assert.match(resultEditor, /achievement\.label/)
  assert.match(resultEditor, /Официального места нет\./)
})

test('import exposes three explicit modes and never auto-syncs them', () => {
  assert.match(imports, /ADD_NEW/)
  assert.match(imports, /UPDATE_EXISTING/)
  assert.match(imports, /EMERGENCY_REPLACE/)
  assert.match(imports, /Добавить новые/)
  assert.match(imports, /Обновить существующие/)
  assert.match(imports, /Экстренно заменить данные/)
  assert.match(imports, /Загрузка результатов/)
  assert.match(imports, /race\.name/)
  assert.doesNotMatch(imports, /race\.sportFormatName/)
  assert.doesNotMatch(imports, /Синхронизировать всё/)
})

test('update import exposes authoritative per-field manual conflict resolution', () => {
  assert.match(imports, /manualConflicts/)
  assert.match(imports, /ordinaryBlockingErrorsPresent/)
  assert.match(imports, /Оставить все ручные/)
  assert.match(imports, /Принять всё из файла/)
  assert.match(imports, /Оставить ручное/)
  assert.match(imports, /Принять из файла/)
  assert.match(imports, /manualConflictResolutionPayload/)
  assert.match(api, /manualConflictResolutions/)
  assert.match(api, /JSON\.stringify\(manualConflictResolutions\)/)
})

test('import preview explains manual result conflicts and invalid statuses in Russian', () => {
  assert.match(imports, /MANUAL_RESULT_OVERRIDE_CONFLICT: 'Значение из файла конфликтует с ручной корректировкой результата'/)
  assert.match(imports, /MANUAL_RESULT_OVERRIDE_WOULD_BE_RETIRED: 'Экстренная замена затронет результат с ручными корректировками'/)
  assert.match(imports, /INVALID_RESULT_STATUS: 'Неизвестный статус результата\. Выберите допустимый статус\.'/)
  assert.match(imports, /Стартовый номер: \$\{details\[1\]\}/)
  assert.match(imports, /details\[2\]\.split\(', '\)\.map\(importFieldLabel\)/)
})

test('flexible import keeps data verification separate from Apply and emergency replace needs typed confirmation', () => {
  assert.match(imports, /Рекомендуемый формат/)
  assert.match(imports, /Другой файл хронометражиста/)
  assert.match(imports, /Скачать шаблон Excel/)
  assert.match(imports, /Сопоставление колонок/)
  assert.match(imports, /Сопоставление стартов/)
  assert.match(imports, /Название формата файла/)
  assert.match(imports, /Найти старты в файле/)
  assert.match(imports, /Запомнить сопоставление стартов/)
  assert.match(imports, /Сопоставлено стартов:/)
  assert.match(imports, /Проверить данные/)
  assert.match(imports, /4\. Проверка данных/)
  assert.doesNotMatch(imports, /Построить Preview/)
  assert.match(api, /imports\/template\.xlsx/)
  assert.match(api, /imports\/analyze/)
  assert.match(api, /import-mapping-profiles/)
  assert.match(api, /form\.append\('options', JSON\.stringify\(options\)\)/)
  assert.match(imports, /Файл не является актуальным шаблоном Sports Results/)
  assert.match(imports, /ordinaryBlockingErrorsPresent/)
  assert.match(imports, /api\.importApply/)
  assert.match(imports, /confirmation === 'ЗАМЕНИТЬ'/)
  assert.match(utils, /IMPORT_PREVIEW_STALE.*Повторите проверку данных/s)
  assert.match(utils, /STALE_EVENT_TEMPLATE.*Структура мероприятия изменилась/s)
  assert.doesNotMatch(imports, /Race\.sourceCode|ImportBatch|backend plan/)
})

test('no-op import is informational and cannot be applied from the preview', () => {
  assert.match(imports, /const noChangesToApply = preview !== null/)
  assert.match(imports, /actionableCount === 0/)
  assert.match(imports, /disabled=\{busy !== null \|\| noChangesToApply\}/)
  assert.match(imports, /AdminNotice tone="info">Изменений для применения нет\./)
  assert.match(imports, /if \(!preview \|\| !file \|\| applyBlocked \|\| noChangesToApply\) return/)
})

test('import presentation translates technical decisions and category or cluster changes', () => {
  for (const label of ['Готово', 'Требуется проверка', 'Готово к применению', 'Есть блокирующие ошибки', 'Без изменений']) {
    assert.match(utils, new RegExp(label))
  }
  assert.match(imports, /NEW_REGISTRATION: 'Участник будет добавлен'/)
  assert.match(imports, /categoryDefinition: 'Категория'/)
  assert.match(imports, /Будет создана:/)
  assert.match(imports, /clusterDefinition: 'Стартовая волна'/)
  assert.match(imports, /Проверка данных обновлена/)
})

test('recalculation pending, Preview and Apply remain explicit', () => {
  assert.match(eventPage, /resultRecalculationRequired/)
  assert.match(eventPage, /Необходимо пересчитать категории/)
  assert.match(policy, /recalculationPreview/)
  assert.match(policy, /recalculationApply/)
  assert.match(policy, /Применить пересчёт/)
})

test('AwardPolicy stays backend-owned and published Start is never auto-drafted', () => {
  assert.match(policy, /единственный источник правил официального протокола/)
  assert.match(policy, /GUN_TIME/)
  assert.match(policy, /CHIP_TIME/)
  assert.match(`${policy}\n${startComposer}`, /NONE/)
  assert.match(policy, /Сохранение настроек не меняет статус публикации автоматически/)
  assert.match(policy, /Field label="Старт"/)
  assert.match(policy, /item\.name/)
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
    categoryRankingBasis: 'CHIP_TIME',
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
    rankingBasis: 'NONE', categoryRankingBasis: 'NONE', primaryStandingMode: 'NONE', absolutePrizePlaces: 0,
    categoryEnabled: false, ageCalculationMode: 'END_OF_EVENT_YEAR',
    categoryPrizePlaces: 0, excludeAbsoluteWinnersFromCategory: false,
  })
  assert.doesNotMatch(JSON.stringify(awardPolicyUpdate(basePolicy)), /"id"|"raceId"/)
  assert.match(policy, /awardPolicyUpdate\(policy\)/)
})

test('primary and category ranking bases have independent Russian controls and atomic bulk actions', () => {
  assert.match(startComposer, /Field label="Основной зачёт"/)
  assert.match(startComposer, /Field label="Возрастные категории" hint="Используется для категорий без индивидуальной настройки\."/)
  assert.match(startComposer, /Официальное время \(Gun Time\)/)
  assert.match(startComposer, /Чистое время \(Chip Time\)/)
  assert.match(categorySettings, /Время зачёта:/)
  assert.match(categorySettings, /По умолчанию ·/)
  assert.match(categorySettings, /rankingBasisOverride/)
  assert.match(policy, /Все по официальному времени/)
  assert.match(policy, /Все по чистому времени/)
  assert.match(policy, /Сбросить индивидуальные настройки/)
  assert.match(policy, /api\.bulkCategoryRankingBasis\(raceId, bulkAction\)/)
  assert.match(api, /bulkCategoryRankingBasis:[\s\S]*method: 'PUT'/)
  assert.match(api, /categories\/ranking-basis/)
  assert.doesNotMatch(policy, /Promise\.all\([^)]*updateCategory/)
  assert.match(policy, /race\?\.resultsPublicationStatus === 'PUBLISHED'/)
})

test('unsaved AwardPolicy changes block category bulk actions without losing the draft', () => {
  const persisted = {
    id: 7,
    raceId: 3,
    rankingBasis: 'GUN_TIME',
    categoryRankingBasis: 'CHIP_TIME',
    primaryStandingMode: 'BY_GENDER',
    absolutePrizePlaces: 3,
    categoryEnabled: true,
    ageCalculationMode: 'EVENT_DATE',
    categoryPrizePlaces: 3,
    excludeAbsoluteWinnersFromCategory: false,
  }
  assert.equal(awardPolicyChanged(persisted, { ...persisted, id: 8, raceId: 4 }), false)
  for (const [field, value] of [
    ['rankingBasis', 'CHIP_TIME'],
    ['categoryRankingBasis', 'GUN_TIME'],
    ['primaryStandingMode', 'ALL'],
    ['absolutePrizePlaces', 5],
    ['categoryEnabled', false],
    ['ageCalculationMode', 'END_OF_EVENT_YEAR'],
    ['categoryPrizePlaces', 5],
    ['excludeAbsoluteWinnersFromCategory', true],
  ]) {
    assert.equal(awardPolicyChanged({ ...persisted, [field]: value }, persisted), true, field)
  }
  assert.match(policy, /policyDirty && <AdminNotice tone="warning">\{UNSAVED_AWARD_POLICY_MESSAGE\}<\/AdminNotice>/)
  assert.match(policy, /disabled=\{busy !== null \|\| policyDirty \|\| race\?\.resultsPublicationStatus === 'PUBLISHED'\}/)
  assert.match(policy, /if \(policyDirty\) \{ setBulkAction\(null\); setError\(UNSAVED_AWARD_POLICY_MESSAGE\); return \}[\s\S]*api\.bulkCategoryRankingBasis/)
  assert.match(policy, /CategorySettings[\s\S]*disabled=\{policyDirty \|\| race\?\.resultsPublicationStatus === 'PUBLISHED'\}/)
  assert.match(policy, /disabled=\{!recalcRaceIds\.length \|\| busy !== null \|\| policyDirty\}/)
  assert.match(policy, /disabled=\{preview\.blockingCount > 0 \|\| busy !== null \|\| policyDirty\}/)
  assert.match(policy, /onChange=\{\(next\) => \{ if \(next\) setPolicy\(\{ \.\.\.policy, \.\.\.next \}\) \}\}/)
})

test('saved AwardPolicy response becomes the clean baseline and race switch requires explicit discard', () => {
  assert.match(policy, /const savedPolicy = await api\.updateAwardPolicy/)
  assert.match(policy, /setPolicy\(savedPolicy\); setPersistedPolicy\(savedPolicy\)/)
  assert.match(policy, /setPolicy\(loadedPolicy\); setPersistedPolicy\(loadedPolicy\); setCategories\(loadedCategories\)/)
  assert.match(policy, /const requestId = \+\+policyLoadRequest\.current/)
  assert.match(policy, /if \(requestId !== policyLoadRequest\.current\) return/)
  assert.match(policy, /if \(policyDirty && !window\.confirm\(DISCARD_AWARD_POLICY_MESSAGE\)\) return/)
  assert.match(policy, /onChange=\{\(change\) => selectRace\(Number\(change\.target\.value\)\)\}/)
})

test('dirty AwardPolicy protects admin navigation, browser history and reload without duplicating the Race guard', () => {
  assert.match(policy, /const policyDirty = awardPolicyChanged\(policy, persistedPolicy\)/)
  assert.match(policy, /if \(!policyDirty\) return\s+return setAdminNavigationBlocker\(\(\) => window\.confirm\(LEAVE_AWARD_POLICY_MESSAGE\)\)/)
  assert.match(policy, /Есть несохранённые изменения настроек зачёта\. Покинуть страницу без сохранения\?/)
  assert.match(policy, /if \(!policyDirty\) return\s+const warn = \(navigation: BeforeUnloadEvent\) => \{ navigation\.preventDefault\(\) \}/)
  assert.match(policy, /window\.addEventListener\('beforeunload', warn\)/)
  assert.match(policy, /window\.removeEventListener\('beforeunload', warn\)/)
  assert.match(policy, /if \(policyDirty && !window\.confirm\(DISCARD_AWARD_POLICY_MESSAGE\)\) return/)
  assert.match(router, /if \(!confirmAdminNavigation\(\)\) return false/)
  assert.match(router, /window\.addEventListener\('popstate', handleHistoryNavigation\)/)
  assert.match(router, /if \(!confirmAdminNavigation\(\)\)[\s\S]*restoring = true[\s\S]*window\.history\.go\(delta\)/)
})

test('AwardPolicy navigation guard clears only after successful save', () => {
  const saveFlow = policy.match(/const save = async \(\) => \{[\s\S]*?\n  \}/)?.[0] ?? ''
  assert.match(saveFlow, /const savedPolicy = await api\.updateAwardPolicy/)
  assert.match(saveFlow, /setPolicy\(savedPolicy\); setPersistedPolicy\(savedPolicy\)/)
  assert.match(saveFlow, /catch \(reason\) \{ setError\(adminErrorMessage\(reason\)\) \}/)
  assert.doesNotMatch(saveFlow.match(/catch \(reason\)[\s\S]*$/)?.[0] ?? '', /setPersistedPolicy/)
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

test('Event issue queue opens a full-page Event-scoped workspace and preserves filters', () => {
  assert.match(issues, /Рабочая очередь обращений/)
  assert.match(issues, /admin-clickable-row/)
  assert.match(issues, /role="link"/)
  assert.match(issues, /closest\('a, button, input, select, textarea'\)/)
  assert.match(issues, /results\/issues\/\$\{issueId\}/)
  assert.match(issues, /returnTo=/)
  assert.match(issues, /queueUrl/)
  assert.match(issues, /queueScope/)
  assert.match(issues, /issuePage/)
  assert.doesNotMatch(issues, />Открыть<|EventIssueDrawer|<Drawer/)
  assert.match(adminApp, /eventIssueMatch/)
  assert.match(adminApp, /AdminEventIssuePage/)
})

test('Event issue workspace shows immutable context and reuses the current Result editor', () => {
  assert.match(issuePage, /Обращение участника/)
  assert.match(issuePage, /Состояние на момент обращения/)
  assert.match(issuePage, /Неизменяемый снимок/)
  assert.match(issuePage, /AdminResultEditor/)
  assert.match(issuePage, /hideBirthDate/)
  assert.match(issuePage, /issueWorkspace/)
  assert.doesNotMatch(issuePage, /Дата рождения|birthDate/)
  assert.match(resultEditor, /api\.result\(resultId\)/)
  assert.match(resultEditor, /api\.updateResult\(detail\.resultId/)
  assert.match(resultEditor, /await load\(false\)/)
  assert.match(resultEditor, /Статус обращения не изменён/)
  assert.doesNotMatch(resultEditor, /updateIssueStatus/)
})

test('Result editor uses race-scoped structured participant fields and hides issue-only noise', () => {
  assert.match(resultEditor, /api\.categories\(value\.raceId\)/)
  assert.match(resultEditor, /Field label="Категория"[\s\S]*<select/)
  assert.match(resultEditor, /Без категории/)
  assert.match(resultEditor, /category\.enabled \|\| String\(category\.id\) === registration\.categoryId/)
  assert.match(resultEditor, /visibleCategories\.map/)
  assert.match(resultEditor, /categoryId: registration\.categoryId \? Number/)
  assert.match(resultEditor, /<option value="male">Мужчина<\/option>/)
  assert.match(resultEditor, /<option value="female">Женщина<\/option>/)
  assert.match(resultEditor, /<option value="">Не указан<\/option>/)
  assert.match(resultEditor, /!issueWorkspace && <Field label="Тип участника"/)
  assert.match(resultEditor, /issueWorkspace \? <p className="admin-muted">Исходная категория из файла/)
})

test('derived ranking fields are not editable in the shared Result editor', () => {
  assert.doesNotMatch(resultEditor, /admin-place-grid/)
  for (const field of ['overallPlace', 'genderPlace', 'categoryPlace', 'netOverallPlace', 'netGenderPlace', 'netCategoryPlace']) {
    assert.doesNotMatch(resultEditor, new RegExp(`label=\\{?${field}`))
  }
  assert.match(resultEditor, /overallPlace: detail\.overallPlace/)
})

test('Result issue workspace presents reasons and workflow in Russian', () => {
  assert.match(utils, /OFFICIAL_TIME: 'Официальное время'/)
  assert.match(utils, /CHIP_TIME: 'Чистое время'/)
  assert.match(issuePage, /Статус обращения/)
  assert.doesNotMatch(issuePage, />Workflow|Workflow [^=]/)
  assert.doesNotMatch(issuePage, />Bib|Bib \/ участник/)
})

test('claimed Result Issue values remain local until the shared editor is submitted', () => {
  assert.match(resultEditor, /focusedGun.*OFFICIAL_TIME/s)
  assert.match(resultEditor, /focusedChip.*CHIP_TIME/s)
  assert.match(resultEditor, /Подставить заявленное/)
  assert.match(resultEditor, /type="button"[^>]*onClick=.*claimedGunTimeMs/s)
  assert.match(resultEditor, /type="button"[^>]*onClick=.*claimedChipTimeMs/s)
  assert.match(resultEditor, /onSubmit=\{saveResult\}/)
})

test('issue workflow is explicit and attachments keep secure authorization', () => {
  assert.match(issuePage, /Взять в работу/)
  assert.match(issuePage, /Решить обращение/)
  assert.match(issuePage, /Отклонить/)
  assert.match(issuePage, /updateIssueStatus\(eventId, issueId, detail\.status, next, comment\)/)
  assert.match(issuePage, /Комментарий специалиста/)
  assert.match(issuePage, /Вложения: нет/)
  assert.match(issuePage, /authorizeAttachment/)
  assert.match(issuePage, /authorizeAndOpenAttachment/)
  assert.match(issuePage, /История/)
  assert.match(api, /comment: comment\.trim\(\) \|\| null/)
})

test('attachment opening uses the exact fresh authorization URL and ignores stale attachment URLs', async () => {
  const freshUrl = 'http://127.0.0.1:9000/test-presigned-url'
  const staleAttachment = { downloadUrl: 'https://object-storage.invalid/stale-presigned-url' }
  const opened = []

  await authorizeAndOpenAttachment(
    async () => ({ downloadUrl: freshUrl }),
    (...args) => opened.push(args),
  )

  assert.deepEqual(opened, [[freshUrl, '_blank', 'noopener,noreferrer']])
  assert.notEqual(opened[0][0], staleAttachment.downloadUrl)
  assert.match(api, /authorizeAttachment:[\s\S]*method: 'POST', cache: 'no-store'/)
})

test('attachment scan states stay Russian and opening is limited to uploaded clean files', () => {
  assert.match(utils, /PENDING: 'Ожидает проверки'/)
  assert.match(utils, /CLEAN: 'Проверено'/)
  assert.match(issuePage, /attachment\.originalFileName/)
  assert.match(issuePage, /attachment\.uploadStatus === 'UPLOADED' && attachment\.scanStatus === 'CLEAN'/)
  assert.match(issuePage, />Открыть<\/button>/)
  assert.match(issuePage, /authorizeAttachment\(eventId, issueId, attachmentId\)/)
  assert.doesNotMatch(issuePage, /localhost:9000|result-issue-files/)
})

test('global journal keeps filters in URL and uses server-side pagination', () => {
  assert.match(journal, /filtersFromUrl/)
  assert.match(journal, /writeFiltersToUrl/)
  assert.match(journal, /AdminPagination/)
  assert.match(journal, /Состояние на момент обращения/)
  assert.match(journal, /Текущее состояние/)
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
  assert.match(utils, /PREVIEW_STALE: 'Данные или настройки изменились\. Повторите проверку данных\.'/)
  for (const code of ['RACE_RESULTS_MUST_BE_DRAFT', 'RESULT_RECALCULATION_REQUIRED', 'RECALC_PREVIEW_STALE', 'RETIRED_REGISTRATION_NOT_MUTABLE', 'CATEGORY_IN_USE', 'DUPLICATE_BIB', 'AMBIGUOUS', 'CONFLICT']) {
    assert.match(utils, new RegExp(code))
  }
})

test('invalid Event dates use the stable backend code and a specific Russian message', () => {
  assert.match(utils, /INVALID_EVENT_DATES: 'Окончание мероприятия не может быть раньше начала\.'/)
  assert.match(api, /throw new ApiError\(response\.status, message, code, violations\)/)
  assert.match(eventGeneralTab, /setDirty\(false\).*Изменения сохранены/s)
  assert.doesNotMatch(eventGeneralTab.match(/catch \(reason\).*?finally/s)?.[0] ?? '', /setDirty\(false\)/)
})

test('admin navigation proceeds without confirmation when no dirty blocker is registered', () => {
  assert.equal(confirmAdminNavigation(), true)
  assert.match(router, /if \(!confirmAdminNavigation\(\)\) return false/)
})

test('dirty admin navigation can stay with its draft or leave through the same blocker', () => {
  let shouldLeave = false
  let draft = 'changed location'
  let url = '/admin/events/1'
  const release = setAdminNavigationBlocker(() => shouldLeave)
  try {
    if (confirmAdminNavigation()) url = '/admin/events/1?tab=results'
    assert.equal(url, '/admin/events/1')
    assert.equal(draft, 'changed location')

    shouldLeave = true
    if (confirmAdminNavigation()) {
      url = '/admin/events/1?tab=results'
      draft = 'server location'
    }
    assert.equal(url, '/admin/events/1?tab=results')
    assert.equal(draft, 'server location')
  } finally {
    release()
  }
})

test('Event general dirty state protects AdminLink, tabs and browser history while retaining beforeunload', () => {
  assert.match(eventGeneralTab, /setAdminNavigationBlocker\(\(\) => window\.confirm\(UNSAVED_CHANGES_MESSAGE\)\)/)
  assert.match(eventGeneralTab, /window\.addEventListener\('beforeunload', warn\)/)
  assert.match(eventGeneralTab, /window\.removeEventListener\('beforeunload', warn\)/)
  assert.match(router, /navigateAdmin\(props\.href\)/)
  assert.match(router, /window\.addEventListener\('popstate', handleHistoryNavigation\)/)
  assert.match(router, /window\.history\.go\(delta\)/)
  assert.match(router, /restoring = true/)
  assert.match(eventPage, /selectTab.*navigateAdmin/s)
  assert.match(eventPage, /AdminLink href="\/admin\/events"/)
})

test('admin UI has loading, confirmations, table overflow and tablet behavior', () => {
  assert.match(css, /admin-spinner/)
  assert.match(css, /overflow-x: auto/)
  assert.match(css, /@media \(max-width: 780px\)/)
  assert.match(css, /admin-modal/)
})
