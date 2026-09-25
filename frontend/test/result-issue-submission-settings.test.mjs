import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import {
  calculateDraftResultInquiryDeadline,
  calculateResultInquiryDeadline,
  formatResultInquiryDeadline,
  MAX_RESULT_INQUIRY_WINDOW_DAYS,
  resultInquiryAvailabilityLabel,
  resultInquiryFieldVisibility,
  resultInquirySummary,
} from '../src/admin/resultInquirySettings.ts'

const source = async (path) => readFile(new URL(path, import.meta.url), 'utf8')
const [createPage, coreTabs, issuesTab, settingsCard, settingsFields, templatePage, adminStyles] = await Promise.all([
  source('../src/admin/pages/AdminEventsPage.tsx'),
  source('../src/admin/pages/EventCoreTabs.tsx'),
  source('../src/admin/pages/EventIssuesTab.tsx'),
  source('../src/admin/components/ResultInquirySettingsCard.tsx'),
  source('../src/admin/components/ResultInquirySettingsFields.tsx'),
  source('../src/admin/pages/AdminTemplatePage.tsx'),
  source('../src/admin/admin.css'),
])

test('event creation submits the Event-level result issue settings', () => {
  assert.match(createPage, /Обращения по результатам/)
  assert.match(settingsFields, /Принимать обращения по результатам/)
  assert.match(settingsFields, /AFTER_EVENT_DAYS/)
  assert.match(settingsFields, /FIXED_DATE/)
  assert.match(settingsFields, /type="date"/)
  assert.match(createPage, /resultInquiry: inquiry/)
  assert.match(createPage, /selectedTemplate\?\.resultInquiryDefaults/)
  assert.match(createPage, /Уже созданные обращения останутся доступны/)
})

test('the same settings card is available in Event main and Results issues', () => {
  assert.match(coreTabs, /<ResultInquirySettingsCard api=\{api\} event=\{event\}/)
  assert.match(issuesTab, /<ResultInquirySettingsCard api=\{api\} event=\{event\} compact/)
  assert.match(settingsCard, /api\.resultInquiry\(event\.id\)/)
  assert.match(settingsCard, /api\.updateResultInquiry\(event\.id/)
  assert.match(settingsCard, /Закрыть приём/)
  assert.match(settingsCard, /Включить приём/)
})

test('deadline mode shows only its own input and switching does not reuse inactive values', () => {
  assert.deepEqual(resultInquiryFieldVisibility('AFTER_EVENT_DAYS'), {
    windowDays: true,
    fixedDate: false,
  })
  assert.deepEqual(resultInquiryFieldVisibility('FIXED_DATE'), {
    windowDays: false,
    fixedDate: true,
  })
  assert.match(settingsFields, /visibility\.windowDays &&/)
  assert.match(settingsFields, /visibility\.fixedDate &&/)
  assert.match(settingsFields, /admin-deadline-mode-option/)
  assert.match(settingsFields, /htmlFor=/)
  assert.match(settingsFields, /type="radio"/)
  assert.match(adminStyles, /\.admin-deadline-mode-option:focus-within/)
})

test('deadline preview adds calendar days in the Event timezone', () => {
  assert.equal(
    calculateDraftResultInquiryDeadline(
      '2027-08-15T09:00', '2027-08-15T23:59', 'AFTER_EVENT_DAYS', 3, null, 'Europe/Moscow',
    ),
    '2027-08-18T20:59:59.999Z',
  )
  assert.equal(
    calculateResultInquiryDeadline({
      startsAt: '2026-03-08T05:00:00Z',
      endsAt: '2026-03-08T06:30:00Z',
      timeZone: 'America/New_York',
    }, 'AFTER_EVENT_DAYS', 1, null),
    '2026-03-10T03:59:59.999Z',
  )
})

test('fixed-date preview uses the complete selected local calendar date', () => {
  assert.equal(calculateDraftResultInquiryDeadline(
    '', '', 'FIXED_DATE', null, '2027-08-20', 'Asia/Yekaterinburg',
  ), '2027-08-20T18:59:59.999Z')
  assert.equal(calculateDraftResultInquiryDeadline(
    '2027-08-15T18:00', '', 'FIXED_DATE', 999, '2027-08-20', 'Asia/Yekaterinburg',
  ), '2027-08-20T18:59:59.999Z')
})

test('deadline preview validates its bounds and formats an inclusive date', () => {
  assert.equal(calculateDraftResultInquiryDeadline(
    '', '', 'AFTER_EVENT_DAYS', 3, null, 'Europe/Moscow',
  ), null)
  assert.equal(calculateDraftResultInquiryDeadline(
    '2027-08-15T09:00', '', 'AFTER_EVENT_DAYS', 0, null, 'Europe/Moscow',
  ), null)
  assert.equal(calculateDraftResultInquiryDeadline(
    '2027-08-15T09:00', '', 'AFTER_EVENT_DAYS', MAX_RESULT_INQUIRY_WINDOW_DAYS + 1,
    null, 'Europe/Moscow',
  ), null)
  assert.match(
    formatResultInquiryDeadline('2027-08-18T20:59:59.999Z', 'Europe/Moscow'),
    /18.*авг.*2027.*включительно/i,
  )
})

test('saved AFTER_EVENT_DAYS summary uses the server deadline and full term', () => {
  assert.deepEqual(resultInquirySummary({
    enabled: true,
    deadlineMode: 'AFTER_EVENT_DAYS',
    windowDays: 7,
    fixedDate: '2030-01-01',
    email: 'timing@example.org',
    availability: 'OPEN',
    deadline: '2026-09-29T18:59:59.999Z',
  }, 'Asia/Yekaterinburg'), {
    status: 'Приём открыт',
    term: '7 дней после окончания мероприятия',
    deadline: '29 сентября 2026 г. включительно',
  })
})

test('saved FIXED_DATE summary does not expose a stale inactive deadline', () => {
  assert.deepEqual(resultInquirySummary({
    enabled: false,
    deadlineMode: 'FIXED_DATE',
    windowDays: 7,
    fixedDate: null,
    email: 'timing@example.org',
    availability: 'DISABLED',
    deadline: '2026-09-29T18:59:59.999Z',
  }, 'Asia/Yekaterinburg'), {
    status: 'Требуется настройка',
    term: 'Дата не задана',
    deadline: '—',
  })
})

test('saved FIXED_DATE summary uses only its active configured deadline', () => {
  assert.deepEqual(resultInquirySummary({
    enabled: true,
    deadlineMode: 'FIXED_DATE',
    windowDays: 999,
    fixedDate: '2027-08-18',
    email: 'timing@example.org',
    availability: 'OPEN',
    deadline: '2027-08-18T18:59:59.999Z',
  }, 'Asia/Yekaterinburg'), {
    status: 'Приём открыт',
    term: 'До выбранной даты',
    deadline: '18 августа 2027 г. включительно',
  })
})

test('event editor keeps saved summary separate from the unsaved deadline preview', () => {
  assert.match(settingsCard, /savedSettings/)
  assert.match(settingsCard, /draftSettings/)
  assert.match(settingsCard, /Верхняя сводка показывает действующие настройки/)
  assert.doesNotMatch(settingsCard, /calculatedDeadline \?\? settings\.deadline/)
  assert.match(settingsCard, /После сохранения обращения будут приниматься до:/)
})

test('template keeps editable inquiry defaults and documents copy-on-create', () => {
  assert.match(templatePage, /TemplateInquirySettingsCard/)
  assert.match(templatePage, /resultInquiryDefaults: settings/)
  assert.match(templatePage, /будут скопированы/i)
  assert.match(templatePage, /Уже созданные мероприятия не изменятся/)
})

test('admin availability labels distinguish manual disable and expiry', () => {
  assert.equal(resultInquiryAvailabilityLabel('OPEN'), 'Приём обращений открыт.')
  assert.equal(resultInquiryAvailabilityLabel('CLOSED'), 'Срок подачи обращений завершён.')
  assert.equal(resultInquiryAvailabilityLabel('DISABLED'), 'Приём обращений закрыт организатором.')
  assert.notEqual(resultInquiryAvailabilityLabel('CLOSED'), resultInquiryAvailabilityLabel('DISABLED'))
})
