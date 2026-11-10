import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import {
  localDateTimeToInstant,
  parseClaimedTime,
  PUBLIC_CORRECTION_REASONS,
  resultIssueAvailabilityMessage,
} from '../src/utils/resultIssue.ts'
import { DirectUploadError, uploadFileDirectly } from '../src/api/directUpload.ts'

const panelSource = await readFile(new URL('../src/components/ResultInquiryPanel.tsx', import.meta.url), 'utf8')
const dialogSource = await readFile(new URL('../src/components/ResultIssueDialog.tsx', import.meta.url), 'utf8')
const detailsSource = await readFile(new URL('../src/components/ResultDetailsDialog.tsx', import.meta.url), 'utf8')
const eventResultsSource = await readFile(new URL('../src/components/EventResults.tsx', import.meta.url), 'utf8')
const appealSource = await readFile(new URL('../src/pages/AppealPage.tsx', import.meta.url), 'utf8')
const attachmentSource = await readFile(new URL('../src/components/ResultIssueAttachmentField.tsx', import.meta.url), 'utf8')
const clientSource = await readFile(new URL('../src/api/client.ts', import.meta.url), 'utf8')
const directUploadSource = await readFile(new URL('../src/api/directUpload.ts', import.meta.url), 'utf8')

test('MISSING_RESULT CTA opens the established backend-backed issue flow directly', () => {
  assert.match(panelSource, /Уточнить результат/)
  assert.match(panelSource, /kind="MISSING_RESULT"/)
  assert.match(dialogSource, /createMissingResultIssue/)
  assert.match(dialogSource, /useState<DialogState>\('FORM'\)/)
  assert.doesNotMatch(dialogSource, /inquiry\.activeIssue|initialActiveIssueId/)
  assert.doesNotMatch(dialogSource, /'VERIFY'|submitVerification|verifiedBirthDate/)
})

test('all public Result Issue entry points contain no birth-date verification UI', () => {
  const publicFlow = `${panelSource}\n${dialogSource}\n${detailsSource}\n${eventResultsSource}\n${appealSource}`
  assert.doesNotMatch(publicFlow, /birthDate|Дата рождения|ДД\.ММ\.ГГГГ|verifyResultInquiry/)
  assert.doesNotMatch(publicFlow, /ResultIssueSupportFallback/)
})

test('MISSING_RESULT form sends safe known context and the selected race', () => {
  assert.match(dialogSource, /Мероприятие/)
  assert.match(dialogSource, /<dt>Старт<\/dt>/)
  assert.match(dialogSource, /Стартовый номер/)
  assert.match(dialogSource, /participantDisplayName/)
  assert.match(dialogSource, /bib: inquiry\.bib/)
  assert.match(dialogSource, /raceId: inquiry\.raceId \?\? null/)
  assert.match(dialogSource, /estimatedStartAt/)
  assert.match(dialogSource, /estimatedFinishAt/)
  assert.doesNotMatch(dialogSource, /registrationId/)
})

test('successful submission shows only the public issue number', () => {
  assert.match(dialogSource, /Обращение №\{createdIssue\.issueId\} принято/)
  assert.match(dialogSource, /Повторно отправлять обращение по этому результату не нужно/)
  assert.doesNotMatch(dialogSource, /registrationId/)
})

test('submit locking prevents two frontend requests from a double click', () => {
  assert.match(dialogSource, /if \(submitLockRef\.current \|\| submitting\) return/)
  assert.match(dialogSource, /submitLockRef\.current = true/)
  assert.match(dialogSource, /disabled=\{submitting\}/)
})

test('active issue is disclosed only as a generic server-authoritative conflict', () => {
  assert.doesNotMatch(dialogSource, /inquiry\.activeIssue|duplicateIssueId/)
  assert.match(dialogSource, /reason\.code === 'ACTIVE_RESULT_ISSUE_EXISTS'/)
  assert.match(dialogSource, /setDialogState\('DUPLICATE'\)/)
  assert.match(dialogSource, /Обращение уже отправлено/)
  assert.match(dialogSource, /Повторное обращение можно отправить после завершения его рассмотрения/)
  assert.doesNotMatch(dialogSource, /активное обращение №/)
})

test('duplicate state does not load or render attachment controls', () => {
  assert.match(dialogSource, /if \(dialogState !== 'FORM'\) return/)
  assert.match(dialogSource, /dialogState === 'FORM' && <form/)
  const duplicateBlock = dialogSource.match(/dialogState === 'DUPLICATE'[\s\S]*?<\/div>/)?.[0] ?? ''
  assert.match(duplicateBlock, /issue-outcome issue-duplicate/)
  assert.doesNotMatch(duplicateBlock, /ResultIssueAttachmentField|type="file"|createResultIssueAttachment/)
})

test('public Result detail exposes correction only when intake is OPEN and uses race context', () => {
  assert.match(detailsSource, /inquiry\?\.inquiryAvailability === 'OPEN'/)
  assert.match(detailsSource, /result-secondary-action/)
  assert.match(detailsSource, /Сообщить об ошибке/)
  assert.match(detailsSource, /kind="RESULT_CORRECTION"/)
  assert.match(detailsSource, /loadedResult\.eventId, loadedResult\.bib, loadedResult\.raceId/)
})

test('public correction reasons exclude participant data changes', () => {
  assert.deepEqual(PUBLIC_CORRECTION_REASONS.map(({ value }) => value), [
    'OFFICIAL_TIME', 'CHIP_TIME', 'RESULT_STATUS', 'RACE_OR_FORMAT', 'OTHER',
  ])
  assert.equal(PUBLIC_CORRECTION_REASONS.some(({ value }) => value === 'PARTICIPANT_DATA'), false)
})

test('OFFICIAL_TIME and CHIP_TIME map one claimed time to the correct payload slot', () => {
  assert.match(dialogSource, /correctionReason === 'OFFICIAL_TIME' \|\| correctionReason === 'CHIP_TIME'/)
  assert.match(dialogSource, /claimedGunTimeMs: correctionReason === 'OFFICIAL_TIME' \? claimedTimeMs : null/)
  assert.match(dialogSource, /claimedChipTimeMs: correctionReason === 'CHIP_TIME' \? claimedTimeMs : null/)
})

test('claimed sporting time converts safely to milliseconds', () => {
  assert.equal(parseClaimedTime('03:51:02.490'), 13_862_490)
  assert.equal(parseClaimedTime('0:59:59'), 3_599_000)
  assert.equal(parseClaimedTime('01:60:00'), null)
  assert.equal(parseClaimedTime('not-a-time'), null)
})

test('non-time correction reasons rely on the required comment', () => {
  assert.match(dialogSource, /<textarea maxLength=\{4000\}/)
  assert.match(dialogSource, /textarea[\s\S]*required/)
  assert.doesNotMatch(dialogSource, /correctionReason === 'RESULT_STATUS'[\s\S]{0,120}claimedTime/)
})

test('closed, not-open-yet and disabled intake remain backend-driven', () => {
  assert.equal(resultIssueAvailabilityMessage('CLOSED'), 'Срок подачи обращений завершён.')
  assert.equal(resultIssueAvailabilityMessage('NOT_OPEN_YET'), 'Приём обращений ещё не открыт.')
  assert.equal(resultIssueAvailabilityMessage('DISABLED'), 'Приём обращений по результатам закрыт организатором.')
  assert.match(
    resultIssueAvailabilityMessage('CLOSED', '2026-09-27T21:00:00Z', 'Europe/Moscow') ?? '',
    /Обращения принимались до 28\.09\.2026 включительно/,
  )
  assert.match(detailsSource, /inquiry\?\.inquiryAvailability === 'OPEN'/)
  assert.match(dialogSource, /inquiry\.inquiryAvailability/)
  assert.match(panelSource, /availability\.canContact/)
})

test('public issue APIs send no DOB in URL or JSON bodies', async () => {
  const calls = []
  const originalFetch = globalThis.fetch
  globalThis.fetch = async (input, init) => {
    calls.push({ input: String(input), init })
    return new Response(JSON.stringify({
      issueId: 91, type: 'MISSING_RESULT', status: 'NEW', createdAt: '2026-08-30T10:00:00Z',
      attachmentUploadToken: 'token', attachmentUploadTokenExpiresAt: '2026-08-30T11:00:00Z',
    }), { status: 201, headers: { 'Content-Type': 'application/json' } })
  }
  try {
    const { api } = await import(`../src/api/client.ts?issue-public=${Date.now()}`)
    await api.createMissingResultIssue(7, {
      bib: 'A-1', raceId: 12, contactEmail: 'runner@example.test',
      message: 'Финишировал', estimatedStartAt: null, estimatedFinishAt: null,
    })
    await api.createResultCorrectionIssue(7, 44, {
      correctionReason: 'OTHER', claimedGunTimeMs: null, claimedChipTimeMs: null,
      contactEmail: 'runner@example.test', message: 'Проверьте статус',
    })
  } finally {
    globalThis.fetch = originalFetch
  }
  assert.equal(calls[0].input, '/api/events/7/result-issue-requests/missing')
  assert.equal(calls[1].input, '/api/events/7/results/44/result-issue-requests')
  assert.equal(calls[0].init?.method, 'POST')
  assert.match(String(calls[0].init?.body), /"raceId":12/)
  assert.doesNotMatch(`${calls[0].input}${calls[0].init?.body}${calls[1].init?.body}`, /birthDate|1990/)
})

test('machine-readable backend error code is preserved without exposing server details', async () => {
  const originalFetch = globalThis.fetch
  globalThis.fetch = async () => new Response(JSON.stringify({
    status: 409, code: 'ACTIVE_RESULT_ISSUE_EXISTS', message: 'internal text', violations: [],
  }), { status: 409, headers: { 'Content-Type': 'application/json' } })
  try {
    const { api, ApiError } = await import(`../src/api/client.ts?issue-error=${Date.now()}`)
    await assert.rejects(
      api.createMissingResultIssue(1, {
        bib: '1', raceId: null, contactEmail: 'a@example.test', message: 'x',
        estimatedStartAt: null, estimatedFinishAt: null,
      }),
      (error) => error instanceof ApiError && error.code === 'ACTIVE_RESULT_ISSUE_EXISTS',
    )
  } finally {
    globalThis.fetch = originalFetch
  }
})

test('optional local estimated times are converted to ISO instants', () => {
  const instant = localDateTimeToInstant('2026-08-30T12:34')
  assert.ok(instant)
  assert.match(instant, /^2026-08-30T\d{2}:34:00\.000Z$/)
  assert.equal(localDateTimeToInstant(''), null)
  assert.equal(localDateTimeToInstant('bad-value'), null)
})

test('attachment UI remains enabled only by backend direct-upload capability', () => {
  assert.match(dialogSource, /resultIssueAttachmentCapabilities/)
  assert.match(dialogSource, /enabled=\{attachmentCapabilities\?\.directUploadAvailable === true\}/)
  assert.match(attachmentSource, /if \(!enabled\) return null/)
  assert.match(attachmentSource, /Добавить файл/)
  assert.match(attachmentSource, /UPLOADING|UPLOADED|ERROR/)
  assert.match(dialogSource, /createResultIssueAttachment/)
  assert.match(dialogSource, /uploadFileDirectly/)
  assert.match(dialogSource, /confirmResultIssueAttachment/)
  assert.doesNotMatch(`${clientSource}\n${directUploadSource}`, /MultipartFile|FormData/)
})

test('direct upload sends the File to object storage with signed headers and progress', async () => {
  const originalXhr = globalThis.XMLHttpRequest
  const observed = { headers: {}, progress: [] }
  class FakeXmlHttpRequest {
    status = 204
    listeners = {}
    uploadListeners = {}
    upload = { addEventListener: (name, listener) => { this.uploadListeners[name] = listener } }
    open(method, url) { observed.method = method; observed.url = url }
    setRequestHeader(name, value) { observed.headers[name] = value }
    addEventListener(name, listener) { this.listeners[name] = listener }
    send(file) {
      observed.file = file
      this.uploadListeners.progress?.({ lengthComputable: true, loaded: 5, total: 10 })
      this.listeners.load?.()
    }
  }
  globalThis.XMLHttpRequest = FakeXmlHttpRequest
  const file = { name: 'finish.mp4', size: 10 }
  try {
    await uploadFileDirectly({
      url: 'https://objects.example.test/private/upload?X-Amz-Signature=signed',
      method: 'PUT', requiredHeaders: { 'Content-Type': 'video/mp4' }, file,
      onProgress: (percent) => observed.progress.push(percent),
    })
  } finally {
    globalThis.XMLHttpRequest = originalXhr
  }
  assert.equal(observed.method, 'PUT')
  assert.equal(observed.headers['Content-Type'], 'video/mp4')
  assert.equal(observed.file, file)
  assert.deepEqual(observed.progress, [50, 100])
})

test('direct upload surfaces rejected signatures and network failures', async () => {
  const originalXhr = globalThis.XMLHttpRequest
  class FailedXmlHttpRequest {
    static failure = 'load'
    status = 403
    listeners = {}
    upload = { addEventListener: () => undefined }
    open() {}
    setRequestHeader() {}
    addEventListener(name, listener) { this.listeners[name] = listener }
    send() { this.listeners[FailedXmlHttpRequest.failure]?.() }
  }
  globalThis.XMLHttpRequest = FailedXmlHttpRequest
  const request = {
    url: 'https://objects.example.test/rejected', method: 'PUT', requiredHeaders: {},
    file: { name: 'proof.txt', size: 1 }, onProgress: () => undefined,
  }
  try {
    await assert.rejects(
      uploadFileDirectly(request),
      (error) => error instanceof DirectUploadError && error.status === 403,
    )
    FailedXmlHttpRequest.failure = 'error'
    await assert.rejects(
      uploadFileDirectly(request),
      (error) => error instanceof DirectUploadError && error.status === null,
    )
  } finally {
    globalThis.XMLHttpRequest = originalXhr
  }
  assert.match(dialogSource, /reason\.status === 403/)
  assert.match(dialogSource, /refreshResultIssueAttachmentUpload/)
  assert.doesNotMatch(`${clientSource}\n${dialogSource}`, /FormData|\/attachments\/upload-binary/)
})

test('issue modal has accessible labels, focus trap, ESC and backdrop handling', () => {
  assert.match(dialogSource, /role="dialog"/)
  assert.match(dialogSource, /aria-modal="true"/)
  assert.match(dialogSource, /aria-labelledby="result-issue-title"/)
  assert.match(dialogSource, /event\.key === 'Escape'/)
  assert.match(dialogSource, /event\.key !== 'Tab'/)
  assert.match(dialogSource, /onMouseDown=\{closeDialog\}/)
  assert.match(dialogSource, /role="alert"/)
})

test('ordinary race-scoped protocol behavior remains unchanged', () => {
  assert.match(eventResultsSource, /raceId: Number\(raceId\)/)
  assert.match(eventResultsSource, /sort,/)
  assert.match(eventResultsSource, /direction,/)
  assert.match(eventResultsSource, /page,/)
  assert.match(eventResultsSource, /size: 50/)
  assert.match(eventResultsSource, /ResultDetailsDialog/)
})
