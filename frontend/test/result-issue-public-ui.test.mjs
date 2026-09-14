import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import {
  decideResultIssueVerification,
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
const attachmentSource = await readFile(new URL('../src/components/ResultIssueAttachmentField.tsx', import.meta.url), 'utf8')
const clientSource = await readFile(new URL('../src/api/client.ts', import.meta.url), 'utf8')
const directUploadSource = await readFile(new URL('../src/api/directUpload.ts', import.meta.url), 'utf8')

test('MISSING_RESULT CTA opens the backend-backed issue flow', () => {
  assert.match(panelSource, /Уточнить результат/)
  assert.match(panelSource, /kind="MISSING_RESULT"/)
  assert.match(dialogSource, /createMissingResultIssue/)
})

test('MISSING_RESULT asks for DOB before showing the form', () => {
  assert.match(dialogSource, /dialogState.*'VERIFY'/s)
  assert.match(dialogSource, /parseRussianBirthDate\(birthDate\)/)
  assert.match(dialogSource, /verified\.lookupState === 'RESULT_NOT_PUBLIC'/)
  assert.match(dialogSource, /decideResultIssueVerification\(verifiedForFlow, verified\.activeIssue\)/)
})

test('successful DOB verification detects an active issue before rendering the form', () => {
  assert.deepEqual(decideResultIssueVerification(true, { issueId: 125 }), {
    state: 'DUPLICATE', issueId: 125,
  })
  assert.deepEqual(decideResultIssueVerification(true, null), { state: 'FORM' })
  assert.deepEqual(decideResultIssueVerification(false, { issueId: 125 }), {
    state: 'VERIFICATION_FAILED',
  })
  const verificationBlock = dialogSource.match(/const submitVerification[\s\S]*?const submitIssue/)?.[0] ?? ''
  assert.match(verificationBlock, /decision\.state === 'DUPLICATE'/)
  assert.match(verificationBlock, /setDuplicateIssueId\(decision\.issueId\)/)
  assert.match(verificationBlock, /setDialogState\('DUPLICATE'\)/)
  assert.match(dialogSource, /активное обращение №\{duplicateIssueId\}/)
})

test('a duplicate-bib verification can enter the same form without asking twice', () => {
  assert.match(eventResultsSource, /verifiedInquiryBirthDate/)
  assert.match(eventResultsSource, /verified\.lookupState === 'RESULT_NOT_PUBLIC' \? birthDate : null/)
  assert.match(panelSource, /initialVerifiedBirthDate=\{verifiedBirthDate\}/)
})

test('failed DOB prevents issue creation and presents organizer support guidance', () => {
  const verificationBlock = dialogSource.match(/const submitVerification[\s\S]*?const submitIssue/)?.[0] ?? ''
  assert.match(verificationBlock, /decision\.state === 'VERIFICATION_FAILED'/)
  assert.match(verificationBlock, /setVerificationFailed\(true\)/)
  assert.doesNotMatch(verificationBlock, /createMissingResultIssue|createResultCorrectionIssue/)
  assert.match(dialogSource, /Введённая дата рождения не совпадает/)
  assert.match(dialogSource, /supportUrl &&/)
  assert.doesNotMatch(dialogSource, /heroleague|if.*hero/i)
})

test('MISSING_RESULT form sends known context and only user-editable facts', () => {
  assert.match(dialogSource, /Мероприятие/)
  assert.match(dialogSource, /Формат \/ старт \/ дистанция/)
  assert.match(dialogSource, /Стартовый номер/)
  assert.match(dialogSource, /participantDisplayName/)
  assert.match(dialogSource, /estimatedStartAt/)
  assert.match(dialogSource, /estimatedFinishAt/)
  assert.match(dialogSource, /contactEmail/)
})

test('successful submission shows the public issue number and no internal registration id', () => {
  assert.match(dialogSource, /Обращение №\{createdIssue\.issueId\} принято/)
  assert.match(dialogSource, /Повторно отправлять обращение по этому результату не нужно/)
  assert.doesNotMatch(dialogSource, /registrationId/)
})

test('submit locking prevents two frontend requests from a double click', () => {
  assert.match(dialogSource, /if \(submitLockRef\.current \|\| submitting \|\| !verifiedBirthDate\) return/)
  assert.match(dialogSource, /submitLockRef\.current = true/)
  assert.match(dialogSource, /disabled=\{submitting\}/)
})

test('ACTIVE_RESULT_ISSUE_EXISTS is a normal duplicate state', () => {
  assert.match(dialogSource, /reason\.code === 'ACTIVE_RESULT_ISSUE_EXISTS'/)
  assert.match(dialogSource, /setDialogState\('DUPLICATE'\)/)
  assert.match(dialogSource, /Обращение уже отправлено/)
  assert.match(dialogSource, /Повторное обращение можно отправить после завершения его рассмотрения/)
})

test('duplicate state does not load or render attachment controls', () => {
  assert.match(dialogSource, /if \(dialogState !== 'FORM'\) return/)
  assert.match(dialogSource, /dialogState === 'FORM' && <form/)
  const duplicateBlock = dialogSource.match(/dialogState === 'DUPLICATE'[\s\S]*?<\/div>/)?.[0] ?? ''
  assert.match(duplicateBlock, /issue-outcome issue-duplicate/)
  assert.doesNotMatch(duplicateBlock, /form-error|role="alert"/)
  assert.doesNotMatch(duplicateBlock, /ResultIssueAttachmentField|type="file"|createResultIssueAttachment/)
})

test('public Result detail exposes a quiet correction action only when OPEN', () => {
  assert.match(detailsSource, /inquiry\?\.inquiryAvailability === 'OPEN'/)
  assert.match(detailsSource, /result-secondary-action/)
  assert.match(detailsSource, /Сообщить об ошибке/)
  assert.match(detailsSource, /kind="RESULT_CORRECTION"/)
})

test('RESULT_CORRECTION verifies DOB against the selected public result', () => {
  assert.match(dialogSource, /verified\.lookupState === 'RESULT_PUBLIC'/)
  assert.match(dialogSource, /verified\.publicResultId === expectedResultId/)
  assert.match(dialogSource, /props\.result\.resultId/)
})

test('public correction reasons exclude participant data changes', () => {
  assert.deepEqual(PUBLIC_CORRECTION_REASONS.map(({ value }) => value), [
    'OFFICIAL_TIME', 'CHIP_TIME', 'RESULT_STATUS', 'RACE_OR_FORMAT', 'OTHER',
  ])
  assert.equal(PUBLIC_CORRECTION_REASONS.some(({ value }) => value === 'PARTICIPANT_DATA'), false)
})

test('OFFICIAL_TIME and CHIP_TIME show one claimed-time field and map to the correct payload slot', () => {
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

test('RESULT_STATUS, RACE_OR_FORMAT and OTHER rely on the required comment', () => {
  assert.match(dialogSource, /<textarea maxLength=\{4000\}/)
  assert.match(dialogSource, /textarea[\s\S]*required/)
  assert.doesNotMatch(dialogSource, /correctionReason === 'RESULT_STATUS'[\s\S]{0,120}claimedTime/)
})

test('CLOSED and NOT_OPEN_YET use backend availability while DISABLED has no CTA', () => {
  assert.equal(resultIssueAvailabilityMessage('CLOSED'), 'Срок подачи обращений по результатам завершён.')
  assert.equal(resultIssueAvailabilityMessage('NOT_OPEN_YET'), 'Подача обращений по результатам ещё не началась.')
  assert.equal(resultIssueAvailabilityMessage('DISABLED'), null)
  assert.match(detailsSource, /inquiry\?\.inquiryAvailability === 'OPEN'/)
  assert.doesNotMatch(detailsSource, /new Date\(|deadline/)
})

test('DOB stays out of URL, browser storage, issue message and logs', () => {
  const combined = `${dialogSource}\n${clientSource}\n${eventResultsSource}`
  assert.doesNotMatch(combined, /localStorage|sessionStorage/)
  assert.doesNotMatch(clientSource, /result-inquiry\/verify\?/)
  assert.doesNotMatch(dialogSource, /setMessage\([^)]*birthDate|message:.*birthDate/)
  assert.doesNotMatch(combined, /console\.(?:log|info).*birth/i)
  assert.match(dialogSource, /setBirthDate\(''\)/)
  assert.match(dialogSource, /setVerifiedBirthDate\(null\)/)
})

test('public issue API methods keep DOB and issue data in POST JSON bodies', async () => {
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
      bib: 'A-1', birthDate: '1990-01-10', contactEmail: 'runner@example.test',
      message: 'Финишировал', estimatedStartAt: null, estimatedFinishAt: null,
    })
    await api.createResultCorrectionIssue(7, 44, {
      birthDate: '1990-01-10', correctionReason: 'OTHER', claimedGunTimeMs: null,
      claimedChipTimeMs: null, contactEmail: 'runner@example.test', message: 'Проверьте статус',
    })
  } finally {
    globalThis.fetch = originalFetch
  }
  assert.equal(calls[0].input, '/api/events/7/result-issue-requests/missing')
  assert.equal(calls[1].input, '/api/events/7/results/44/result-issue-requests')
  assert.equal(calls[0].init?.method, 'POST')
  assert.doesNotMatch(calls[0].input, /birthDate|1990/)
  assert.match(String(calls[0].init?.body), /"birthDate":"1990-01-10"/)
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
        bib: '1', birthDate: '1990-01-01', contactEmail: 'a@example.test', message: 'x',
        estimatedStartAt: null, estimatedFinishAt: null,
      }),
      (error) => error instanceof ApiError && error.code === 'ACTIVE_RESULT_ISSUE_EXISTS',
    )
  } finally {
    globalThis.fetch = originalFetch
  }
})

test('optional local estimated times are converted to ISO instants without custom persistence', () => {
  const instant = localDateTimeToInstant('2026-08-30T12:34')
  assert.ok(instant)
  assert.match(instant, /^2026-08-30T\d{2}:34:00\.000Z$/)
  assert.equal(localDateTimeToInstant(''), null)
  assert.equal(localDateTimeToInstant('bad-value'), null)
})

test('attachment UI is enabled only by backend direct-upload capability', () => {
  assert.match(dialogSource, /resultIssueAttachmentCapabilities/)
  assert.match(dialogSource, /enabled=\{attachmentCapabilities\?\.directUploadAvailable === true\}/)
  assert.match(attachmentSource, /if \(!enabled\) return null/)
  assert.match(attachmentSource, /Добавить файл/)
  assert.match(attachmentSource, /UPLOADING|UPLOADED|ERROR/)
  assert.match(attachmentSource, /progress|Повторить|errorMessage/)
  assert.match(attachmentSource, /Файл загружен\. Проверяется\./)
  assert.match(dialogSource, /createResultIssueAttachment/)
  assert.match(dialogSource, /uploadFileDirectly/)
  assert.match(dialogSource, /confirmResultIssueAttachment/)
  assert.match(dialogSource, /refreshResultIssueAttachmentUpload/)
  assert.doesNotMatch(`${clientSource}\n${directUploadSource}`, /MultipartFile|FormData/)
})

test('direct upload sends the File to object storage with signed headers and progress', async () => {
  const originalXhr = globalThis.XMLHttpRequest
  const observed = { headers: {}, progress: [] }
  class FakeXmlHttpRequest {
    status = 204
    listeners = {}
    uploadListeners = {}
    upload = {
      addEventListener: (name, listener) => { this.uploadListeners[name] = listener },
    }
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
      method: 'PUT',
      requiredHeaders: { 'Content-Type': 'video/mp4' },
      file,
      onProgress: (percent) => observed.progress.push(percent),
    })
  } finally {
    globalThis.XMLHttpRequest = originalXhr
  }
  assert.equal(observed.method, 'PUT')
  assert.match(observed.url, /^https:\/\/objects\.example\.test\//)
  assert.equal(observed.headers['Content-Type'], 'video/mp4')
  assert.equal(observed.file, file)
  assert.deepEqual(observed.progress, [50, 100])
})

test('direct upload surfaces rejected signatures and network failures without a backend binary fallback', async () => {
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

test('ordinary race-scoped protocol behavior remains unchanged around the auxiliary flow', () => {
  assert.match(eventResultsSource, /raceId: Number\(raceId\)/)
  assert.match(eventResultsSource, /sort,/)
  assert.match(eventResultsSource, /direction,/)
  assert.match(eventResultsSource, /page,/)
  assert.match(eventResultsSource, /size: 50/)
  assert.match(eventResultsSource, /ResultDetailsDialog/)
})
